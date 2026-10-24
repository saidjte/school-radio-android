package com.saidjte.schoolradio.data.backup

import android.content.Context
import android.net.Uri
import com.saidjte.schoolradio.data.local.AppDatabase
import com.saidjte.schoolradio.data.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * النسخ الاحتياطي والاستعادة — عبر Storage Access Framework (مكان يختاره المستخدم).
 *
 * ماذا تتضمن النسخة؟
 *  - التصنيفات والمقاطع (URI + بياناتها) — لكن *ليس* الملفات الصوتية الأصلية نفسها.
 *  - قوائم التشغيل وعناصرها وترتيبها.
 *  - الجداول والأحداث وسجل التنفيذ.
 *  - الإعدادات (اسم المدرسة، المظهر، الخفض…).
 *
 * بعد الاستعادة تُفحص صلاحية URI لكل مقطع، وما لا يُتاح يُعلَّم غير متاح ليعاد ربطه.
 */
class BackupRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsStore
) {
    companion object {
        const val FORMAT_VERSION = 3   // v3 (v0.5.0): recordingId + noteText + imagePath للفقرات + recId للتسجيلات
        const val MIN_SUPPORTED_VERSION = 1
        const val BACKUP_MIME = "application/json"
        const val BACKUP_EXT = ".schoolradio.json"
    }

    /** تصدير كل البيانات إلى URI اختاره المستخدم (ACTION_CREATE_DOCUMENT) */
    suspend fun exportTo(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val root = JSONObject()
            root.put("format_version", FORMAT_VERSION)
            root.put("app_version", "0.5.0")
            root.put("exported_at", System.currentTimeMillis())

            val categories = JSONArray()
            db.categoryDao().observeAll().first().forEach { c ->
                categories.put(JSONObject().put("id", c.id).put("name", c.name)
                    .put("sortOrder", c.sortOrder).put("isBuiltIn", c.isBuiltIn))
            }
            root.put("categories", categories)

            val tracks = JSONArray()
            db.audioTrackDao().observeAll().first().forEach { t ->
                tracks.put(JSONObject()
                    .put("id", t.id).put("title", t.title).put("uri", t.uri)
                    .put("categoryId", t.categoryId ?: JSONObject.NULL)
                    .put("durationMs", t.durationMs).put("sizeBytes", t.sizeBytes)
                    .put("isAvailable", t.isAvailable))
            }
            root.put("tracks", tracks)

            val playlists = JSONArray()
            db.playlistDao().observeAll().first().forEach { p ->
                val po = JSONObject().put("id", p.id).put("name", p.name).put("repeat", p.repeat)
                val items = JSONArray()
                db.playlistDao().items(p.id).forEach { it ->
                    items.put(JSONObject().put("trackId", it.trackId).put("position", it.position))
                }
                po.put("items", items)
                playlists.put(po)
            }
            root.put("playlists", playlists)

            val schedules = JSONArray()
            db.scheduleDao().observeAll().first().forEach { s ->
                schedules.put(JSONObject().put("id", s.id).put("name", s.name).put("enabled", s.enabled))
            }
            root.put("schedules", schedules)

            val events = JSONArray()
            db.scheduleDao().observeEvents().first().forEach { e ->
                events.put(JSONObject()
                    .put("id", e.id).put("scheduleId", e.scheduleId)
                    .put("startMinutes", e.startMinutes).put("weekDaysMask", e.weekDaysMask)
                    .put("title", e.title)
                    .put("playlistId", e.playlistId ?: JSONObject.NULL)
                    .put("singleTrackId", e.singleTrackId ?: JSONObject.NULL)
                    // v3: تشغيل التسجيلات ضمن الفقرات + نص/صورة شاشة العرض
                    .put("recordingId", e.recordingId ?: JSONObject.NULL)
                    .put("noteText", e.noteText ?: JSONObject.NULL)
                    .put("imagePath", e.imagePath ?: JSONObject.NULL))
            }
            root.put("events", events)

            // ===== v2: المستخدمون + أرشيف التسجيلات =====
            val users = JSONArray()
            db.userDao().observeAll().first().forEach { u ->
                users.put(JSONObject().put("id", u.id).put("name", u.name).put("role", u.role))
            }
            root.put("users", users)

            val recordings = JSONArray()
            db.recordingDao().observeAll().first().forEach { r ->
                recordings.put(JSONObject()
                    .put("recId", r.id) // v3: لإعادة ربط recordingId في الفقرات بعد الاستعادة
                    .put("title", r.title).put("filePath", r.filePath)
                    .put("durationMs", r.durationMs).put("sizeBytes", r.sizeBytes)
                    .put("source", r.source)
                    .put("recordedByName", r.recordedByName ?: JSONObject.NULL)
                    .put("recordedByRole", r.recordedByRole ?: JSONObject.NULL)
                    .put("createdAt", r.createdAt))
            }
            root.put("recordings", recordings)

            root.put("settings", JSONObject().apply {
                val s = settings.settings.first()
                put("schoolName", s.schoolName)
                put("themeMode", s.themeMode)
                put("defaultVolume", s.defaultVolume.toDouble())
                put("duckVolume", s.duckVolume.toDouble())
                put("duckEnabled", s.duckEnabled)
                put("notificationsEnabled", s.notificationsEnabled)
                put("currentUserId", s.currentUserId ?: JSONObject.NULL)
            })

            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(root.toString(2).toByteArray(Charsets.UTF_8))
            } ?: throw IllegalStateException("تعذر فتح مقار النسخ")
            root.getJSONArray("tracks").length()
        }
    }

    /**
     * استعادة نسخة سابقة بعد التحقق من سلامتها.
     * الخطوات: تحقق بنية JSON → نسخة أمان من الحالي → تفريغ واستيراد → فحص URI.
     */
    suspend fun restoreFrom(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            } ?: throw IllegalStateException("تعذر قراءة ملف النسخة")

            val root = JSONObject(text)
            // ---- التحقق من السلامة قبل أي تعديل (نقبل v1 و v2) ----
            val version = root.optInt("format_version", -1)
            if (version < MIN_SUPPORTED_VERSION || version > FORMAT_VERSION) {
                throw IllegalArgumentException("إصدار النسخة غير مدعوم (format_version=$version)")
            }
            for (key in listOf("categories", "tracks", "playlists", "schedules", "events", "settings")) {
                if (!root.has(key)) throw IllegalArgumentException("النسخة ناقصة: $key")
            }

            // ---- نسخة أمان من الحالة الحالية قبل الاستبدال ----
            val safetyUri = backupToUserVisibleFile("نسخة-أمان-قبل-الاستعادة")
            exportTo(safetyUri)

            // ---- تفريغ الجداول (الترتيب يحترم المفاتيح الخارجية) ----
            db.clearAllTables()

            // ---- استيراد التصنيفات ----
            val catIdMap = HashMap<Long, Long>()
            root.getJSONArray("categories").let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val newId = db.categoryDao().insert(
                        com.saidjte.schoolradio.data.local.entity.CategoryEntity(
                            name = o.getString("name"),
                            sortOrder = o.getInt("sortOrder"),
                            isBuiltIn = o.getBoolean("isBuiltIn")
                        )
                    )
                    if (newId != -1L) catIdMap[o.getLong("id")] = newId
                }
            }

            // ---- استيراد المقاطع + فحص صلاحية الوصول ----
            var unavailableCount = 0
            val trackIdMap = HashMap<Long, Long>()
            root.getJSONArray("tracks").let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val tUri = Uri.parse(o.getString("uri"))
                    val available = isReadable(tUri)
                    if (!available) unavailableCount++
                    val newId = db.audioTrackDao().insert(
                        com.saidjte.schoolradio.data.local.entity.AudioTrackEntity(
                            title = o.getString("title"),
                            uri = o.getString("uri"),
                            categoryId = if (o.isNull("categoryId")) null
                            else catIdMap[o.getLong("categoryId")],
                            durationMs = o.getLong("durationMs"),
                            sizeBytes = o.getLong("sizeBytes"),
                            isAvailable = available
                        )
                    )
                    if (newId != -1L) trackIdMap[o.getLong("id")] = newId
                }
            }

            // ---- استيراد القوائم وعناصرها ----
            root.getJSONArray("playlists").let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val pid = db.playlistDao().insert(
                        com.saidjte.schoolradio.data.local.entity.PlaylistEntity(
                            name = o.getString("name"), repeat = o.getBoolean("repeat")
                        )
                    )
                    o.getJSONArray("items").let { items ->
                        for (j in 0 until items.length()) {
                            val io = items.getJSONObject(j)
                            trackIdMap[io.getLong("trackId")]?.let { tid ->
                                db.playlistDao().insertItem(
                                    com.saidjte.schoolradio.data.local.entity.PlaylistItemEntity(
                                        playlistId = pid, trackId = tid, position = io.getInt("position")
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // ---- استيراد الجداول والأحداث ----
            val schedIdMap = HashMap<Long, Long>()
            root.getJSONArray("schedules").let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val sid = db.scheduleDao().insert(
                        com.saidjte.schoolradio.data.local.entity.RadioScheduleEntity(
                            name = o.getString("name"), enabled = o.getBoolean("enabled")
                        )
                    )
                    schedIdMap[o.getLong("id")] = sid
                }
            }
            // ---- v3: استيراد التسجيلات *قبل* الفقرات (FK recording_id + إعادة الربط) ----
            var missingRecordingFiles = 0
            val recIdMap = HashMap<Long, Long>()
            if (root.has("recordings")) {
                root.getJSONArray("recordings").let { arr ->
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val path = o.getString("filePath")
                        if (!java.io.File(path).exists()) missingRecordingFiles++
                        val newRecId = db.recordingDao().insert(
                            com.saidjte.schoolradio.data.local.entity.RecordingEntity(
                                title = o.getString("title"),
                                filePath = path,
                                durationMs = o.getLong("durationMs"),
                                sizeBytes = o.getLong("sizeBytes"),
                                source = o.optString("source", "PTT"),
                                recordedByName = if (o.isNull("recordedByName")) null
                                else o.getString("recordedByName"),
                                recordedByRole = if (o.isNull("recordedByRole")) null
                                else o.getString("recordedByRole"),
                                createdAt = o.getLong("createdAt")
                            )
                        )
                        if (newRecId != -1L && o.has("recId")) {
                            recIdMap[o.getLong("recId")] = newRecId
                        }
                    }
                }
            }

            root.getJSONArray("events").let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val sid = schedIdMap[o.getLong("scheduleId")] ?: continue
                    db.scheduleDao().insertEvent(
                        com.saidjte.schoolradio.data.local.entity.ScheduledEventEntity(
                            scheduleId = sid,
                            startMinutes = o.getInt("startMinutes"),
                            weekDaysMask = o.getInt("weekDaysMask"),
                            title = o.getString("title"),
                            playlistId = if (o.isNull("playlistId")) null else o.getLong("playlistId"),
                            singleTrackId = if (o.isNull("singleTrackId")) null
                            else trackIdMap[o.getLong("singleTrackId")],
                            // v3: إعادة ربط التسجيل بالمعرف الجديد (نسخ ≤v2 بلا حقل تسجيل)
                            recordingId = if (o.isNull("recordingId")) null
                            else recIdMap[o.getLong("recordingId")],
                            noteText = if (o.isNull("noteText")) null else o.optString("noteText"),
                            imagePath = if (o.isNull("imagePath")) null else o.optString("imagePath")
                        )
                    )
                }
            }

            // ---- v2: استيراد المستخدمين والأدوار (نسخ v1 تجاهلها بأمان) ----
            val userIdMap = HashMap<Long, Long>()
            if (root.has("users")) {
                root.getJSONArray("users").let { arr ->
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val newId = db.userDao().insert(
                            com.saidjte.schoolradio.data.local.entity.UserEntity(
                                name = o.getString("name"),
                                role = o.getString("role")
                            )
                        )
                        if (newId != -1L) userIdMap[o.getLong("id")] = newId
                    }
                }
            }
            if (userIdMap.isEmpty()) {
                // نسخة قديمة أو قائمة فارغة: أعد زرع المشرف الافتراضي
                if (db.userDao().count() == 0) {
                    val id = db.userDao().insert(
                        com.saidjte.schoolradio.data.local.entity.UserEntity(
                            name = com.saidjte.schoolradio.data.repository.SessionRepository.DEFAULT_ADMIN_NAME,
                            role = com.saidjte.schoolradio.data.local.entity.UserRole.ADMIN.name
                        )
                    )
                    if (id != -1L) userIdMap[-1L] = id
                    settings.setCurrentUserId(id)
                }
            }

            // ---- استيراد الإعدادات ----
            root.getJSONObject("settings").let { so ->
                settings.setSchoolName(so.getString("schoolName"))
                settings.setThemeMode(so.getInt("themeMode"))
                settings.setDefaultVolume(so.getDouble("defaultVolume").toFloat())
                settings.setDuckVolume(so.getDouble("duckVolume").toFloat())
                settings.setDuckEnabled(so.getBoolean("duckEnabled"))
                settings.setNotificationsEnabled(so.getBoolean("notificationsEnabled"))
            }

            // المستخدم الحالي: اربط المعرف القديم بالجديد إن وُجد
            val savedUserId = if (root.getJSONObject("settings").isNull("currentUserId")) null
            else root.getJSONObject("settings").optLong("currentUserId", -1L)
            settings.setCurrentUserId(
                savedUserId?.let { userIdMap[it] }
                    ?: userIdMap.values.firstOrNull()
            )

            val msg = buildString {
                append("استُعيدت ${trackIdMap.size} مقطعاً")
                if (unavailableCount > 0) {
                    append("، منها $unavailableCount غير متاح حالياً (أعد ربطه من المكتبة)")
                }
                if (missingRecordingFiles > 0) {
                    append(". تنبيه: $missingRecordingFiles تسجيلاً بلا ملفاتها على هذا الجهاز")
                }
                append(". وجرى حفظ نسخة أمان من بياناتك السابقة قبل الاستبدال.")
            }
            msg
        }
    }

    /** إنشاء ملف نسخة جديد في مكان يختاره النظام (Downloads) — بسيط ومباشر */
    suspend fun backupToUserVisibleFile(label: String): Uri = withContext(Dispatchers.IO) {
        val name = "إذاعة-المدرسية-${label}-${System.currentTimeMillis()}$BACKUP_EXT"
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name)
            put(android.provider.MediaStore.Downloads.MIME_TYPE, BACKUP_MIME)
        }
        val uri = context.contentResolver.insert(
            android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
        ) ?: throw IllegalStateException("تعذر إنشاء ملف النسخ الاحتياطي")
        uri
    }

    private fun isReadable(uri: Uri): Boolean = try {
        context.contentResolver.openInputStream(uri)?.use { true } ?: false
    } catch (_: Exception) { false }
}

package com.saidjte.schoolradio.data.repository

import android.content.Context
import android.net.Uri
import android.media.MediaMetadataRetriever
import android.provider.OpenableColumns
import com.saidjte.schoolradio.audio.SilenceAnalyzer
import com.saidjte.schoolradio.data.bundled.BundledJingles
import com.saidjte.schoolradio.data.local.AppDatabase
import com.saidjte.schoolradio.data.local.entity.AudioTrackEntity
import com.saidjte.schoolradio.data.local.entity.CategoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * مستودع المكتبة الصوتية: الاستيراد عبر SAF، التصنيف، البحث، واكتشاف الملفات غير المتاحة.
 *
 * لماذا SAF وليس صلاحيات التخزين الشامل؟
 * - لا يحتاج أي صلاحية READ/WRITE_EXTERNAL_STORAGE.
 * - المستخدم يختار الملفات نفسها (أو مجلداً كاملاً بـ ACTION_OPEN_DOCUMENT_TREE).
 * - takePersistableUriPermission يحفظ الوصول بعد إعادة التشغيل.
 */
class LibraryRepository(
    private val context: Context,
    private val db: AppDatabase
) {
    private val trackDao = db.audioTrackDao()
    private val categoryDao = db.categoryDao()

    val tracks: Flow<List<AudioTrackEntity>> = trackDao.observeAll()
    val categories: Flow<List<CategoryEntity>> = categoryDao.observeAll()

    fun search(query: String): Flow<List<AudioTrackEntity>> =
        if (query.isBlank()) trackDao.observeAll() else trackDao.search(query.trim())

    /** زرع التصنيفات الافتراضية السبعة عند أول تشغيل فقط */
    suspend fun ensureDefaultCategories() {
        if (categoryDao.count() == 0) {
            DEFAULT_CATEGORIES.forEachIndexed { i, name ->
                categoryDao.insert(CategoryEntity(name = name, sortOrder = i, isBuiltIn = true))
            }
        }
    }

    // ================== v0.10.0: إدارة كاملة للتصنيفات ==================
    // سابقاً كانت التصنيفات الافتراضية السبعة تُعرض للقراءة فقط — لا إضافة
    // ولا تسمية ولا حذف، فتكدّست المقاطع في «فقرات متنوعة» عن قهر.

    val categoriesWithCounts: Flow<List<com.saidjte.schoolradio.data.local.dao.CategoryWithCount>> =
        categoryDao.observeAllWithCounts()

    /**
     * إضافة تصنيف جديد مخصص.
     * @return null عند النجاح، أو رسالة عربية (اسم فارغ/مكرر) لعرضها للمستخدم
     */
    suspend fun addCategory(name: String): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return "اكتب اسم التصنيف أولاً"
        if (categoryDao.insert(CategoryEntity(name = trimmed, sortOrder = 999, isBuiltIn = false)) == -1L) {
            return "تصنيف بهذا الاسم موجود بالفعل"
        }
        return null
    }

    /** إعادة تسمية أي تصنيف (افتراضي أو مخصص) — @return رسالة خطأ أو null */
    suspend fun renameCategory(category: CategoryEntity, newName: String): String? {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return "الاسم لا يمكن أن يكون فارغاً"
        if (trimmed == category.name) return null
        // فحص التكرار يدوياً (القيد UNIQUE يُتجاهل بصمت مع IGNORE فقط عند الإدراج)
        if (categories.first().any { it.id != category.id && it.name == trimmed }) {
            return "تصنيف بهذا الاسم موجود بالفعل"
        }
        categoryDao.update(category.copy(name = trimmed))
        return null
    }

    /**
     * حذف تصنيف مخصص — المقاطع التابعة له تعود إلى «بدون تصنيف» تلقائياً
     * (قيد FK onDelete = SET_NULL يتكفل بذلك على مستوى قاعدة البيانات).
     * التصنيفات الافتراضية (isBuiltIn) لا تُحذف حفاظاً على بنية البرنامج المدرسي.
     * @return رسالة خطأ أو null عند النجاح
     */
    suspend fun deleteCategory(category: CategoryEntity): String? {
        if (category.isBuiltIn) return "التصنيفات الافتراضية لا تُحذف — يمكنك إعادة تسميتها فقط"
        categoryDao.deleteIfCustom(category.id)
        return null
    }

    /**
     * استيراد عدة ملفات في عملية واحدة.
     * تستخرج الاسم والمدة والحجم، وتطلب صلاحية وصول دائمة لكل مستند.
     * v0.18.0: يُقرر تحليل الفراغ الأول للملفات المستوردة بعد الإدراج مباشرة
     * (خلفياً — لا يعطل الاستيراد ولا رسالة التأكيد).
     * @return قائمة العناوين المستوردة (لرسالة التأكيد)
     */
    suspend fun importUris(uris: List<Uri>, categoryId: Long?): Int =
        withContext(Dispatchers.IO) {
            val cr = context.contentResolver
            var imported = 0
            val importedIds = mutableListOf<Long>()
            for (uri in uris) {
                try {
                    // صلاحية دائمة حتى يبقى الملف قابلاً للتشغيل بعد إعادة التشغيل
                    cr.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                    // بعض الموفرات لا تدعم الدوام — يعمل الملف داخل الجلسة وقد يُطلب إعادة ربطه لاحقاً
                }
                val name = queryDisplayName(cr, uri) ?: "مقطع ${imported + 1}"
                val duration = queryDuration(cr, uri)
                val size = querySize(cr, uri)
                val newId = trackDao.insert(
                    AudioTrackEntity(
                        title = name.substringBeforeLast('.').ifBlank { name },
                        uri = uri.toString(),
                        documentId = try { uri.lastPathSegment } catch (_: Exception) { null },
                        categoryId = categoryId,
                        durationMs = duration,
                        sizeBytes = size,
                        isAvailable = true
                    )
                )
                if (newId > 0) importedIds += newId
                imported++
            }
            // v0.18.0: تحليل فوري للجديد (خيط IO — عادة أجزاء من الثانية لكل ملف)
            for (id in importedIds) analyzeSilenceById(id)
            imported
        }

    // ================== v0.18.0: قص الفراغ الأول ==================

    /**
     * تحليل الفراغ الأول لمقطع واحد وبدء تخزين النتيجة.
     * آمن تماماً: فشل التحليل يعني «لا قص» — لا يرفع استثناء أبداً.
     * @return المدة المقيسة بالميلي ثانية (0 = لا فراغ يُذكر)
     */
    suspend fun analyzeSilenceById(trackId: Long): Long = withContext(Dispatchers.IO) {
        val track = trackDao.byId(trackId) ?: return@withContext 0L
        if (!track.isAvailable) return@withContext 0L
        val trim = SilenceAnalyzer.leadingSilenceMs(context, track.uri)
        trackDao.setSilenceTrim(trackId, trim)
        trim
    }

    /**
     * فحص خلفي تدريجي: يحلّل دفعة من المقاطع التي لم تُحلَّل بعد.
     * يُستدعى من إقلاع التطبيق وبعد الاستيراد — بحد أقصى للدفعة حتى لا يلمس الإقلاع.
     * @return عدد المقاطع المحلَّلة في هذه الدفعة
     */
    suspend fun scanPendingSilence(limit: Int = 12): Int = withContext(Dispatchers.IO) {
        val pending = trackDao.pendingSilenceScan(limit)
        for (t in pending) {
            val trim = SilenceAnalyzer.leadingSilenceMs(context, t.uri)
            trackDao.setSilenceTrim(t.id, trim)
        }
        pending.size
    }

    suspend fun rename(track: AudioTrackEntity, newTitle: String) {
        trackDao.update(track.copy(title = newTitle))
    }

    suspend fun moveToCategory(track: AudioTrackEntity, categoryId: Long?) {
        trackDao.update(track.copy(categoryId = categoryId))
    }

    /** حذف المقطع من المكتبة فقط — لا يمس الملف الأصلي على الإطلاق */
    suspend fun removeFromLibrary(track: AudioTrackEntity) = trackDao.delete(track)

    /** فحص توفر الملفات (يُستدعى عند الإقلاع أو يدوياً): من يفشل وصوله يُعلَّم غير متاح */
    suspend fun refreshAvailability(): List<AudioTrackEntity> = withContext(Dispatchers.IO) {
        val cr = context.contentResolver
        val all = trackDao.unavailable().ifEmpty { return@withContext emptyList() }
        val relinked = mutableListOf<AudioTrackEntity>()
        for (t in all) {
            val ok = try {
                cr.openInputStream(Uri.parse(t.uri))?.use { it.read() >= 0 } ?: false
            } catch (_: Exception) { false }
            if (ok) { trackDao.setAvailability(t.id, true); relinked += t.copy(isAvailable = true) }
        }
        relinked
    }

    /** إعادة ربط مقطع فُقدت صلاحيته بملف جديد يختاره المستخدم */
    suspend fun relink(track: AudioTrackEntity, newUri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                newUri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) { /* كما سبق */ }
        trackDao.update(track.copy(uri = newUri.toString(), isAvailable = true))
        // v0.18.0: ملف جديد = فراغ أول جديد — أعِد فتح التحليل ليغطيه الفحص الخلفي
        trackDao.resetSilenceAnalysis(track.id)
    }

    suspend fun trackById(id: Long): AudioTrackEntity? = trackDao.byId(id)
    suspend fun tracksByIds(ids: List<Long>): List<AudioTrackEntity> = trackDao.byIds(ids)
    suspend fun categoryById(id: Long): CategoryEntity? = categoryDao.byId(id)

    // ================== v0.14.0: الجينلات المدمجة للفواصل ==================

    /**
     * زرع الجينلات المدمجة في المكتبة عند أول تشغيل (ورقة ضمان: يُستدعى من
     * SchoolRadioApp بعد زرع التصنيفات). لكل جينل:
     *  - يُنسخ من assets إلى filesDir/jingles (مرة واحدة — إن وُجد ملف بنفس
     *    الحجم يُترك لتسريع الإقلاعات التالية).
     *  - يُسجَّل في قاعدة المكتبة بتصنيف «فواصل وجينلات» وبمعرف مستند
     *    `bundled:jingles/…` يمنع ازدواج الزرع ويجعله قابلاً للانتقاء فوراً
     *    في محرر الفواصل (Breaks) والحائط الصوتي.
     *
     * يبقى كل شيء قابلاً للتعديل/الحذف كأي مقطع مستورد — لا خصائص خاصة.
     */
    suspend fun ensureBundledJingles() {
        val dir = java.io.File(context.filesDir, "jingles").apply { mkdirs() }

        // التصنيف: موجود؟ وإلا أنشئه (INSERT IGNORE قد يعيد -1 عند سباق نادر — نعيد القراءة)
        val categoryId = categories.first().firstOrNull { it.name == BundledJingles.CATEGORY }?.id
            ?: run {
                categoryDao.insert(
                    CategoryEntity(name = BundledJingles.CATEGORY, sortOrder = 98, isBuiltIn = false)
                )
                categories.first().firstOrNull { it.name == BundledJingles.CATEGORY }?.id
                    ?: return // تعذّر الحصول على تصنيف — أجّل الزرع لإقلاع قادم
            }

        for (j in BundledJingles.ALL) {
            val docId = BundledJingles.docId(j.file)
            if (trackDao.countByDocumentId(docId) > 0) continue // زُرع سابقاً

            val outFile = java.io.File(dir, j.file)
            if (!outFile.exists() || outFile.length() == 0L) {
                try {
                    context.assets.open("jingles/${j.file}").use { input ->
                        outFile.outputStream().use { input.copyTo(it) }
                    }
                } catch (_: Exception) {
                    continue // أصل مفقود؟ لا نكسر الإقلاع — الجينلات الباقية تُزرع
                }
            }

            val durationMs = queryDurationForFile(outFile)
            val newId = trackDao.insert(
                AudioTrackEntity(
                    title = j.title,
                    uri = Uri.fromFile(outFile).toString(),
                    documentId = docId,
                    categoryId = categoryId,
                    durationMs = durationMs,
                    sizeBytes = outFile.length()
                )
            )
            // v0.18.0: الجينلات المدمجة صغيرة — نحلل فراغها الأول فوراً (غير مانع للإقلاع)
            if (newId > 0) {
                runCatching { analyzeSilenceById(newId) }
            }
        }
    }

    /** مدة ملف محلي عبر MediaMetadataRetriever — 0 عند الفشل (المدة تُصحَّح عند التشغيل) */
    private fun queryDurationForFile(file: java.io.File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
        } catch (_: Exception) {
            0
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun queryDisplayName(cr: android.content.ContentResolver, uri: Uri): String? =
        cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    private fun querySize(cr: android.content.ContentResolver, uri: Uri): Long =
        cr.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getLong(0) else 0
        } ?: 0

    private fun queryDuration(cr: android.content.ContentResolver, uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
        } catch (_: Exception) { 0 } finally {
            runCatching { retriever.release() }
        }
    }

    companion object {
        val DEFAULT_CATEGORIES = listOf(
            "القرآن الكريم",
            "الحديث النبوي",
            "النشيد الوطني",
            "الأناشيد المدرسية",
            "المؤثرات الصوتية",
            "الفقرات الثقافية",
            "فقرات متنوعة"
        )
    }
}

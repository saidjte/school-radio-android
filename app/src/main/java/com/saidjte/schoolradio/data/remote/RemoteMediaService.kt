package com.saidjte.schoolradio.data.remote

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.saidjte.schoolradio.core.util.RemoteLinks
import com.saidjte.schoolradio.data.local.AppDatabase
import com.saidjte.schoolradio.data.local.entity.AudioTrackEntity
import com.saidjte.schoolradio.data.repository.LibraryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * v0.23.0 — خدمة «استيراد من الإنترنت» الموحدة: المعاينة، الحفظ كمقطع بث،
 * التنزيل المحلي (offline للأبد)، وتحديد URI القابل للتشغيل لحظة التشغيل.
 *
 * نموذج البيانات: المقطع البعيد صف مكتبة عادي عموداه الجديدان:
 *  - [AudioTrackEntity.isRemote] يبث من رابط بدل ملف محلي.
 *  - [AudioTrackEntity.sourceUrl] الرابط الأصلي (يوتوب/ساوندكلاود) لإعادة
 *    استخراج تدفق طازج — فروابط يوتوب/ساوندكلاود تنتهي صلاحيتها بعد ساعات،
 *    بينما روابط Drive/المباشرة ثابتة فتُستعمل كما هي.
 *
 * المعادل الصوتي والمؤقتات وكل شيء يعمل كما مع الملفات المحلية لأن التشغيل
 * يمر بنفس المشغل والجلسة الصوتية — لا مسار موازٍ إطلاقاً.
 */
class RemoteMediaService(
    private val context: Context,
    private val db: AppDatabase,
    private val library: LibraryRepository
) {
    private val trackDao = db.audioTrackDao()

    // ==================== v0.25.0: تقدم التنزيل الحي ====================

    enum class DownloadPhase { RUNNING, DONE, FAILED }

    /** حالة تنزيل واحد — تظهر في حوار الاستيراد وفي صف المقطع في المكتبة */
    data class DownloadProgress(
        val trackId: Long,
        val title: String,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val phase: DownloadPhase,
        val message: String? = null
    ) {
        /** نسبة 0..1 أو null إن كان الحجم مجهولاً — يُعرض مؤشر غير محدد */
        val fraction: Float?
            get() = DownloadLogic.percent(downloadedBytes, totalBytes)
        val isRunning: Boolean get() = phase == DownloadPhase.RUNNING
    }

    private val _downloads = MutableStateFlow<Map<Long, DownloadProgress>>(emptyMap())

    /** تنزيلات جارية/منتهية حديثاً بالمعرّف — تُنظّف ذاتياً بعد انقضاء فترة العرض */
    val downloads: StateFlow<Map<Long, DownloadProgress>> = _downloads.asStateFlow()

    /** نطاق داخلي لتنظيف حالات التنزيل المنتهية بعد مهلة عرض قصيرة */
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun reportProgress(
        trackId: Long, title: String, downloaded: Long, total: Long,
        phase: DownloadPhase, message: String? = null
    ) {
        _downloads.update { cur ->
            cur + (trackId to DownloadProgress(trackId, title, downloaded, total, phase, message))
        }
    }

    private fun cleanupLater(trackId: Long, afterMs: Long) {
        cleanupScope.launch {
            delay(afterMs)
            _downloads.update { it - trackId }
        }
    }

    /** نتيجة معاينة رابط — كل ما يلزم الحوار قبل قرار الحفظ/التشغيل */
    data class LinkPreview(
        val parsed: RemoteLinks.Parsed,
        val title: String,
        val uploader: String?,
        val durationMs: Long,
        val streamUrl: String,
        val mimeType: String?
    ) {
        val sourceLabel: String get() = parsed.source.label
    }

    /**
     * معاينة الرابط: تحليل + استخراج (شبكة لليوتوب/ساوندكلاود، فحص لِDrive).
     * @throws RemoteImportException برسالة عربية عند رابط غير مدعوم أو فشل الاستخراج
     */
    suspend fun preview(rawUrl: String): LinkPreview {
        val parsed = RemoteLinks.parse(rawUrl)
            ?: throw RemoteImportException(
                "الرابط غير مدعوم — ألصق رابط مقطع من يوتوب أو ساوندكلاود أو Google Drive أو رابط صوتي مباشر"
            )
        parsed.blockedReason?.let { throw RemoteImportException(it) }
        val res = StreamResolver.resolveAny(parsed)
        return LinkPreview(
            parsed = parsed,
            title = res.title,
            uploader = res.uploader,
            durationMs = res.durationMs,
            streamUrl = res.streamUrl,
            mimeType = res.mimeType
        )
    }

    /**
     * حفظ المقطع البعيد في المكتبة كمقطع «بث» — يظهر فوراً بمعرّفه الخاص
     * ويقبل التصنيف والقوائم والفقرات المجدولة كأي مقطع.
     */
    suspend fun saveRemoteTrack(preview: LinkPreview, categoryId: Long?): AudioTrackEntity {
        val id = trackDao.insert(
            AudioTrackEntity(
                title = preview.title,
                uri = preview.streamUrl,
                documentId = "remote:${preview.parsed.source.name}:${preview.parsed.mediaId ?: preview.streamUrl.hashCode()}",
                categoryId = categoryId,
                durationMs = preview.durationMs,
                isRemote = true,
                sourceUrl = preview.parsed.url,
                isAvailable = true
            )
        )
        return trackDao.byId(id) ?: throw RemoteImportException("تعذر حفظ المقطع في المكتبة")
    }

    /**
     * تنزيل مقطع بعيد إلى ملف محلي — يحوله إلى مقطع مكتبة كامل يعمل دون إنترنت،
     * ويحلل فراغه الأول مثل أي استيراد محلي (توحيد سلوك v0.18.0).
     *
     * v0.25.0:
     *  - يُبلّغ عن بدايته ونهايته عبر [downloads] فترى الواجهة التقدم حياً
     *    (شريط نسبة في الحوار وفي صف المقطع) بدل صمت طويل بلا مؤشر.
     *  - الروابط التي تحتاج تجديداً (يوتوب/ساوندكلاود) **تُجدد إجبارياً**:
     *    الرابط القديم المحفوظ يرفضه الخادم بـ403 دوماً، فالفشل الصامت في
     *    التجديد يخرج الآن برسالة إرشادية بدل تنزيل مضمون الفشل.
     *
     * @return رسالة النجاح العربية
     */
    suspend fun downloadToLibrary(track: AudioTrackEntity, knownFreshUrl: String? = null): String =
        withContext(Dispatchers.IO) {
            reportProgress(track.id, track.title, 0L, -1L, DownloadPhase.RUNNING)
            try {
                // يوتوب/ساوندكلاود: التجديد إجباري — الرابط المخزن منتهٍ حتماً
                val url = knownFreshUrl ?: freshDownloadUrlFor(track)
                if (url.isBlank() || !url.startsWith("http")) {
                    throw RemoteImportException("لا يوجد رابط صالح للتنزيل — أعد استيراد المقطع من مصدره")
                }
                val ext = RemoteLinks.fileExtensionFor(
                    RemoteLinks.parse(track.sourceUrl ?: "")?.source ?: RemoteLinks.Source.DIRECT,
                    url, null
                )
                val dir = File(context.filesDir, "remote_imports").apply { mkdirs() }
                val safe = track.title.replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), "_")
                    .take(60).ifBlank { "remote_track" }
                var out = File(dir, "$safe.$ext")
                var n = 1
                while (out.exists()) {
                    out = File(dir, "$safe (${n++}).$ext")
                }
                StreamResolver.downloadToFile(url, out) { downloaded, total ->
                    reportProgress(track.id, track.title, downloaded, total, DownloadPhase.RUNNING)
                }

                val duration = queryDurationForFile(out)
                trackDao.update(
                    track.copy(
                        uri = Uri.fromFile(out).toString(),
                        isRemote = false,
                        durationMs = if (duration > 0) duration else track.durationMs,
                        sizeBytes = out.length()
                    )
                )
                // تحليل الفراغ الأول — الملف محلي الآن فالتحليل آمن ومفيد
                runCatching { library.analyzeSilenceById(track.id) }
                val msg = "نُزّل «${track.title}» إلى المكتبة — يعمل الآن دون إنترنت"
                reportProgress(track.id, track.title, out.length(), out.length(), DownloadPhase.DONE, msg)
                cleanupLater(track.id, 6_000L)
                msg
            } catch (e: Exception) {
                val msg = (e as? RemoteImportException)?.message
                    ?: "فشل التنزيل — تحقق من الاتصال وحاول مجدداً"
                reportProgress(track.id, track.title, 0L, -1L, DownloadPhase.FAILED, msg)
                cleanupLater(track.id, 10_000L)
                throw e
            }
        }

    /**
     * رابط تنزيل طازج لمقطع بعيد:
     *  - يوتوب/ساوندكلاوند: تجديد إجباري — وفشل التجديد **استثناء واضح** لا سقوط
     *    صامت على رابط منتهٍ يرفضه googlevideo بـ403 (سبب «الزر لا يعمل» القديم).
     *  - Drive/مباشر: الرابط المخزن ثابت فيُستعمل كما هو.
     */
    private suspend fun freshDownloadUrlFor(track: AudioTrackEntity): String {
        val parsed = RemoteLinks.parse(track.sourceUrl ?: "")
        if (parsed?.source?.needsFreshResolve == true && parsed.playable) {
            try {
                val res = StreamResolver.resolveAny(parsed)
                if (res.streamUrl != track.uri ||
                    (res.durationMs > 0 && res.durationMs != track.durationMs)
                ) {
                    trackDao.update(
                        track.copy(
                            uri = res.streamUrl,
                            durationMs = if (res.durationMs > 0) res.durationMs else track.durationMs
                        )
                    )
                }
                return res.streamUrl
            } catch (e: Exception) {
                throw RemoteImportException(
                    "تعذر تجديد رابط التدفق من ${parsed.source.label} — تحقق من الاتصال ثم أعد التنزيل"
                )
            }
        }
        return track.uri
    }

    /**
     * URI قابل للتشغيل **الآن** لمقطع ما:
     *  - محلي → مساره كما هو.
     *  - بعيد Drive/مباشر → الرابط المخزن ثابت فيُستعمل مباشرة.
     *  - بعيد يوتوب/ساوندكلاود → استخراج تدفق طازج (ثوانٍ قليلة على الشبكة)
     *    مع تحديث المخزن والمدة؛ وعند فشل التجديد يُعاد المخزن القديم
     *    ليعطي المشغل خطأه الواضح بدل تجميد الواجهة.
     *
     * @param knownFreshUrl تدفق عُرّف للتو في نفس الجلسة — يوفر إعادة الاستخراج
     */
    suspend fun playableUriFor(track: AudioTrackEntity, knownFreshUrl: String? = null): String {
        if (!track.isRemote) return track.uri
        if (knownFreshUrl != null) {
            if (knownFreshUrl != track.uri) {
                trackDao.update(track.copy(uri = knownFreshUrl))
            }
            return knownFreshUrl
        }
        val parsed = RemoteLinks.parse(track.sourceUrl ?: "")
        if (parsed?.source?.needsFreshResolve == true && parsed.playable) {
            try {
                val res = StreamResolver.resolveAny(parsed)
                if (res.streamUrl != track.uri || (res.durationMs > 0 && res.durationMs != track.durationMs)) {
                    trackDao.update(
                        track.copy(
                            uri = res.streamUrl,
                            durationMs = if (res.durationMs > 0) res.durationMs else track.durationMs
                        )
                    )
                }
                return res.streamUrl
            } catch (_: Exception) {
                return track.uri // التدفق القديم — المشغل سيعرض رسالة واضحة إن انتهى فعلاً
            }
        }
        return track.uri
    }

    /** مدة ملف محلي عبر MediaMetadataRetriever — 0 عند الفشل (تُصحَّح عند التشغيل) */
    private fun queryDurationForFile(file: File): Long {
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
}

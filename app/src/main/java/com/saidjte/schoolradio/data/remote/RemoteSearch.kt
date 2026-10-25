package com.saidjte.schoolradio.data.remote

import com.saidjte.schoolradio.core.util.RemoteLinks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.soundcloud.linkHandler.SoundcloudSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException

/**
 * v0.25.0 — البحث عن المقاطع في يوتوب وساوندكلاوث مباشرة عبر **NewPipeExtractor**
 * (نفس محرك الاستيراد v0.23.0 — فلا اعتماد جديد ولا وسيط يموت).
 *
 * البحث يُرشَّح للفيديوهات (يوتوب) والمقاطع (ساوندكلاود) فقط — القنوات والقوائم
 * والبثوث الحية طويلة الأمد ليست مقاطع إذائية فلا تُعرض أصلاً.
 *
 * نتيجة البحث ليست مقطعاً جاهزاً — عند اختيار نتيجة تُحوَّل رابطها إلى نفس مسار
 * المعاينة/التشغيل المباشر/التنزيل القائم، فيبقى سلوك التطبيق مساراً واحداً.
 */
object RemoteSearch {

    /** نتيجة بحث واحدة — رابطها يدخل مسار [RemoteMediaService.preview] مباشرة */
    data class RemoteSearchResult(
        val url: String,
        val title: String,
        val uploader: String?,
        val durationMs: Long,
        val source: RemoteLinks.Source
    ) {
        val sourceLabel: String get() = source.label
    }

    /**
     * بحث فعلي على المنصة — الصفحة الأولى (≈20 نتيجة) مرشَّحة للمقاطع الصوتية.
     * @throws RemoteImportException برسالة عربية عند فشل الشبكة أو المنصة
     */
    suspend fun search(
        source: RemoteLinks.Source,
        query: String,
        limit: Int = 15
    ): List<RemoteSearchResult> = withContext(Dispatchers.IO) {
        val q = RemoteSearchLogic.normalizeQuery(query)
        if (!RemoteSearchLogic.isSearchable(q)) return@withContext emptyList()
        StreamResolver.ensureInit()
        withTimeout(30_000L) {
            try {
                val (service, contentFilter) = when (source) {
                    RemoteLinks.Source.YOUTUBE ->
                        ServiceList.YouTube to listOf(YoutubeSearchQueryHandlerFactory.VIDEOS)
                    else ->
                        ServiceList.SoundCloud to listOf(SoundcloudSearchQueryHandlerFactory.TRACKS)
                }
                val handler = service.searchQHFactory.fromQuery(q, contentFilter, "")
                val info = SearchInfo.getInfo(service, handler)
                toResults(info.relatedItems, source, limit)
            } catch (e: RemoteImportException) {
                throw e
            } catch (e: IOException) {
                throw RemoteImportException("تعذر الاتصال بـ${source.label} — تحقق من الإنترنت وحاول مجدداً")
            } catch (e: Exception) {
                throw RemoteImportException("فشل البحث في ${source.label} — أعد المحاولة بعد قليل")
            }
        }
    }

    /** تحويل عناصر الاستخراج إلى نتائج — مفصولة للاختبار بعناصر مبنية يدوياً */
    internal fun toResults(
        items: List<InfoItem>,
        source: RemoteLinks.Source,
        limit: Int
    ): List<RemoteSearchResult> = RemoteSearchLogic.toResults(items, source, limit)
}

/**
 * المنطق الصافي لتحويل نتائج الاستخراج — بلا شبكة، يُختبر بعناصر مُصنّعة.
 */
object RemoteSearchLogic {

    /** تنظيف استعلام البحث: تقليم وطي المسافات المتعددة */
    fun normalizeQuery(query: String): String =
        query.trim().replace(Regex("\\s+"), " ")

    /** استعلام قابل للبحث: حرفان على الأقل بعد التنظيف الداخلي */
    fun isSearchable(query: String): Boolean = normalizeQuery(query).length >= 2

    /**
     * فلترة عناصر نتيجة البحث:
     *  - مقاطع فقط (InfoType.STREAM) — القنوات والقوائم والبلاي-ليست تُستبعد.
     *  - بلا بثوث حية (LIVE/AUDIO_LIVE) لأن مدتها مفتوحة لا تناسب الجدولة الإذاعية.
     *  - رابط وعنوان صحيحان.
     * ثم التقييد بـ [limit] مع تحويل المدة من ثوانٍ إلى ميلي ثانية.
     */
    fun toResults(
        items: List<InfoItem>,
        source: RemoteLinks.Source,
        limit: Int
    ): List<RemoteSearch.RemoteSearchResult> {
        if (limit <= 0) return emptyList()
        return items.asSequence()
            .filterIsInstance<StreamInfoItem>()
            .filter {
                it.streamType != org.schabi.newpipe.extractor.stream.StreamType.LIVE_STREAM &&
                    it.streamType != org.schabi.newpipe.extractor.stream.StreamType.AUDIO_LIVE_STREAM
            }
            .mapNotNull { item ->
                val url = item.url?.takeIf { it.startsWith("http") } ?: return@mapNotNull null
                val title = item.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                RemoteSearch.RemoteSearchResult(
                    url = url,
                    title = title,
                    uploader = item.uploaderName?.takeIf { it.isNotBlank() },
                    durationMs = (item.duration.coerceAtLeast(0)) * 1000L,
                    source = source
                )
            }
            .take(limit)
            .toList()
    }
}

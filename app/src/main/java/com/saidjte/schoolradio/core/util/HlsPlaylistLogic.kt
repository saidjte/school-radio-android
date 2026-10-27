package com.saidjte.schoolradio.core.util



/**
 * v0.46.0 — تحليل قوائم بث HLS لأجل **التنزيل المحلي** (منطق صافٍ بلا أي
 * تبعية أندرويد — قابل للاختبار وحدوياً بعينات حقيقية الشكل).
 *
 * لماذا وُجد؟ الإثبات التجريبي (دورة v0.46.0): ساوندكلاود أسقط التدفقات
 * التقدمية عن محتواها — كل transcodings المقطع المبلغ عنه HLS حصراً
 * (aac_160k/aac_96k/abr_sq). التشغيل عبر وحدة media3-exoplayer-hls يُعالج
 * القائمة أصلاً، أما التنزيل فقائمة .m3u8 نص كيلوبايتي لا ملف صوتي —
 * تنزيلها بمحارف Range يعطي ملفاً تالفاً. الحل: تحليل القائمة وتجميع
 * مقاطعها الفعلية بالترتيب.
 *
 * الشكل الفعلي لقوائم ساوندكلاود (مُثبَّت تجريبياً):
 *  - قائمة وسائط مباشرة (VOD): `#EXT-X-VERSION:7` + `#EXT-X-MAP:URI="init.mp4"`
 *    + مقاطع `dataNNN.m4s` (fMP4) — **بلا تشفير** لأي مقطع عام.
 *  - التجميع: init.mp4 + المقاطع بالترتيب = ملف fMP4 صالح (m4a) يفكه
 *    كل مشغل — لا إعادة ترميز ولا مكتبات خارجية.
 *
 * ما لا ندعمه نسمّيه صادقاً بدل فشل غامض: قائمة الجودات (Master)،
 * المقاطع المشفرة (EXT-X-KEY بخلاف NONE)، وليس-m3u8 أصلاً.
 */
object HlsPlaylistLogic {

    /** قائمة وسائط جاهزة للتنزيل: مقطع تهيئة (اختياري) + مقاطع بترتيبها المقدس */
    data class MediaPlaylist(
        /** مقطع التهيئة init.mp4 (fMP4) — null إن لم تعلنه القائمة */
        val initUrl: String?,
        /** روابط المقاطع بترتيب التشغيل الصارم — ترتيب الصوت مقدس */
        val segments: List<String>,
        /** مجموع مدد EXTINF بالثواني — تقدير صدق لمدة الناتج */
        val totalDurationSec: Double
    )

    /** نتيجة التحليل: ما يُنزَّل وما يُرفض مع تسمية صادقة لكل رفض */
    sealed class Result {
        data class Media(val playlist: MediaPlaylist) : Result()
        /** قائمة جودات متعددة (#EXT-X-STREAM-INF) — لا مقاطع مباشرة فيها */
        object Master : Result()
        /** مقاطع مشفرة (EXT-X-KEY بطريقة غير NONE) — التنزيل غير متاح لها */
        object Encrypted : Result()
        /** ليس ملف m3u8 أصلاً (صفحة HTML أو جسد فارغ أو تالف) */
        object Invalid : Result()
    }

    /**
     * تحليل نص قائمة HLS بالنسبة لعنوانها (لحل الروابط النسبية إن وُجدت —
     * قوائم ساوندكلاود روابطها مطلقة لكن النداءات الأخرى قد تختلف).
     */
    fun parse(playlistUrl: String, body: String): Result {
        val text = body.trimStart()
        if (!text.startsWith("#EXTM3U")) return Result.Invalid
        if (Regex("(?m)^#EXT-X-STREAM-INF").containsMatchIn(text)) return Result.Master

        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }

        // التشفير: أي مفتاح بخلاف METHOD=NONE يجعل التجميع ناتجاً عشوائياً — ارفض صادقاً
        for (line in lines) {
            if (line.startsWith("#EXT-X-KEY:") && !line.contains("METHOD=NONE")) {
                return Result.Encrypted
            }
        }

        // مقطع التهيئة من EXT-X-MAP (قد يسبقه EXT-X-MAP:BYTERANGE على نفس السطر)
        var initUrl: String? = null
        var totalDuration = 0.0
        val segments = ArrayList<String>(32)
        for (line in lines) {
            when {
                line.startsWith("#EXT-X-MAP:") ->
                    if (initUrl == null) {
                        val uri = Regex("URI=\"([^\"]+)\"").find(line)?.groupValues?.get(1)
                        initUrl = uri?.let { resolve(playlistUrl, it) }
                    }
                line.startsWith("#EXTINF:") -> {
                    totalDuration += line.removePrefix("#EXTINF:")
                        .substringBefore(',').trim().toDoubleOrNull() ?: 0.0
                }
                !line.startsWith("#") -> segments += resolve(playlistUrl, line)
            }
        }
        if (segments.isEmpty()) return Result.Invalid
        return Result.Media(MediaPlaylist(initUrl, segments, totalDuration))
    }

    /**
     * حل رابط نسبي بالنسبة لقائمة البث — المطلق يعود كما هو.
     * java.net.URI تحل وفق RFC 3986 (المسار النسبي يُدمج مع مجلد القائمة
     * ولا يُورّث query الأساس) — java.net.URL سلوكها غير صارم هنا.
     */
    internal fun resolve(baseUrl: String, link: String): String = try {
        if (link.startsWith("http://") || link.startsWith("https://")) link
        else java.net.URI(baseUrl).resolve(link).toString()
    } catch (_: Exception) {
        link
    }
}

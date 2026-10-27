package com.saidjte.schoolradio.core.util

/**
 * v0.23.0 — التعرّف على روابط المقاطع من المنصات المدعومة (منطق صافٍ بلا شبكة ولا أندرويد).
 *
 * يخدم ميزة «استيراد مقاطع من الإنترنت»:
 *  - يوتوب (watch / youtu.be / shorts / live / embed / music)
 *  - SoundCloud (مقطع مفرد — القوائم الموسومية Sets مرفوضة بتعليل واضح)
 *  - Google Drive (ملف مشترك عام — المجلدات مرفوضة)
 *  - رابط صوتي مباشر ينتهي بامتداد معروف (mp3/m4a/wav/…)
 *
 * التحويل هنا تحليل نصي فقط — الاستخراج الفعلي للتدفق يقع على StreamResolver.
 * التحليل متسامح مع الروابط غير المرمزة (عربية/مسافات): تُقرأ يدوياً دون java.net.URI
 * الذي يرمي استثناءات على أحرف غير قياسية.
 */
object RemoteLinks {

    enum class Source(val label: String) {
        YOUTUBE("يوتوب"),
        SOUNDCLOUD("ساوندكلاود"),
        GOOGLE_DRIVE("Google Drive"),
        DIRECT("رابط صوتي مباشر");

        /** يوتوب وساوندكلاود يحتاجان استخراج تدفق طازجاً قبل كل تشغيل (روابطهم تنتهي صلاحيتها) */
        val needsFreshResolve: Boolean get() = this == YOUTUBE || this == SOUNDCLOUD
    }

    /**
     * نتيجة التحليل. [blockedReason] ليست فارغة = رابط مُفهوم لكن غير مدعوم
     * (مجلد Drive / قائمة SoundCloud / قناة بلا مقطع) — رسالة عربية جاهزة للعرض.
     */
    data class Parsed(
        val source: Source,
        val mediaId: String?,
        val url: String,
        val blockedReason: String? = null
    ) {
        val playable: Boolean get() = blockedReason == null
    }

    // ================== المنصة الرئيسية ==================

    fun parse(raw: String): Parsed? {
        val url = raw.trim()
        if (url.isEmpty() || !url.contains('.')) return null
        val host = hostOf(url)
        return when {
            host.endsWith("youtu.be") ->
                // يوتوب المختصر: المعرّف هو أول مقطع في المسار
                youtubeParsed(pathSegments(url).firstOrNull(), url)
            host.endsWith("youtube.com") || host.endsWith("youtube-nocookie.com") -> parseYouTubeCom(url)
            host.endsWith("soundcloud.com") -> parseSoundCloud(url)
            (host == "drive.google.com" || host == "docs.google.com") -> parseDrive(url)
            isDirectAudio(url) -> Parsed(Source.DIRECT, null, url)
            else -> null
        }
    }

    // ================== يوتوب ==================

    private fun parseYouTubeCom(url: String): Parsed? {
        // watch?v=ID أو ?v=ID في أي موضع من الاستعلام
        url.extractQueryValue("v")?.let { return youtubeParsed(it, url) }
        // /shorts/ID و /live/ID و /embed/ID و /v/ID
        val segments = pathSegments(url)
        for (marker in listOf("shorts", "live", "embed", "v")) {
            val i = segments.indexOf(marker)
            if (i >= 0 && i + 1 < segments.size) return youtubeParsed(segments[i + 1], url)
        }
        return Parsed(Source.YOUTUBE, null, url, "رابط يوتوب بلا معرف مقطع — ألصق رابط «مشاهدة» كاملاً")
    }

    private fun youtubeParsed(id: String?, url: String): Parsed? =
        if (isValidYouTubeId(id)) Parsed(Source.YOUTUBE, id, url)
        else Parsed(Source.YOUTUBE, null, url, "معرف المقطع في رابط يوتوب غير صالح")

    /** معرف يوتوب الصحيح: 11 حرفاً/رقماً من [A-Za-z0-9_-] */
    fun isValidYouTubeId(id: String?): Boolean =
        id != null && id.length == 11 && id.all { it.isLetterOrDigit() || it == '_' || it == '-' }

    // ================== SoundCloud ==================

    private fun parseSoundCloud(url: String): Parsed? {
        val host = hostOf(url)
        val segments = pathSegments(url)
        // v0.44.0: الرابط المختصر من زر المشاركة (on.soundcloud.com/<رمز>) هو صيغة
        // المشاركة القياسية في تطبيق ساوندكلاود — المستخرج يحلّ تحويله بنفسه
        // (SoundcloudStreamLinkHandlerFactory يقبل هذا النمط صراحة)، فالرفض هنا
        // كان يمنع تشغيل روابط المشاركة رغم أنها مقاطع صالحة
        if (host == "on.soundcloud.com") {
            return if (segments.isEmpty())
                Parsed(Source.SOUNDCLOUD, null, url, "رابط ساوندكلاود المختصر ناقص — أعد نسخه من زر المشاركة")
            else Parsed(Source.SOUNDCLOUD, null, url)
        }
        // رابط القناة أو الصفحات العامة ليس مقطعاً
        if (segments.size < 2) {
            return Parsed(Source.SOUNDCLOUD, null, url, "ألصق رابط مقطع مفرد (artist/title) لا رابط حساب أو صفحة")
        }
        if (segments[1] == "sets") {
            return Parsed(Source.SOUNDCLOUD, null, url, "قوائم SoundCloud (Sets) غير مدعومة بعد — ألصق رابط مقطع مفرد")
        }
        return Parsed(Source.SOUNDCLOUD, segments.take(2).joinToString("/"), url)
    }

    // ================== Google Drive ==================

    private fun parseDrive(url: String): Parsed? {
        val segments = pathSegments(url)
        if (segments.contains("folders")) {
            return Parsed(Source.GOOGLE_DRIVE, null, url, "مجلدات Drive غير مدعومة — ألصق رابط ملف صوتي واحد")
        }
        // /file/d/{ID}/… أو /drive/u/0/file/d/{ID}/…
        val dIdx = segments.indexOf("d")
        if (segments.firstOrNull() == "file" && dIdx >= 1 && dIdx + 1 < segments.size) {
            return Parsed(Source.GOOGLE_DRIVE, segments[dIdx + 1], url)
        }
        // open?id= أو uc?id=
        val id = url.extractQueryValue("id")
        if (!id.isNullOrBlank()) return Parsed(Source.GOOGLE_DRIVE, id, url)
        return Parsed(Source.GOOGLE_DRIVE, null, url, "تعذر قراءة معرف الملف من رابط Drive — استخدم «نسخ الرابط» من قائمة الملف")
    }

    // ================== روابط مباشرة ==================

    private val AUDIO_EXTENSIONS = setOf(
        "mp3", "m4a", "aac", "wav", "ogg", "oga", "opus", "flac", "wma", "amr", "3gp", "webm", "mp4"
    )

    fun isDirectAudio(url: String): Boolean {
        val path = url.substringBefore('?').substringBefore('#')
        val ext = path.substringAfterLast('.', "").lowercase()
        return ext in AUDIO_EXTENSIONS
    }

    /** امتداد ملف مقترح من رابط (للتنزيل المحلي) — افتراضياً m4a للتدفقات المستخرجة */
    fun fileExtensionFor(source: Source, url: String, mimeType: String?): String {
        if (source == Source.DIRECT) {
            val path = url.substringBefore('?').substringBefore('#')
            val ext = path.substringAfterLast('.', "").lowercase()
            if (ext.length in 2..4) return ext
        }
        val mime = mimeType?.lowercase() ?: ""
        return when {
            "mp4" in mime || "m4a" in mime || "aac" in mime -> "m4a"
            "webm" in mime || "opus" in mime -> "webm"
            "mpeg" in mime || "mp3" in mime -> "mp3"
            "ogg" in mime -> "ogg"
            "wav" in mime -> "wav"
            "flac" in mime -> "flac"
            else -> "m4a"
        }
    }

    // ================== أدوات تحليل لطيفة ==================

    /** استخراج المضيف من أي رابط بلا استثناءات — يتقبل العربية والمسافات والبروتوكول المفقود */
    internal fun hostOf(url: String): String {
        val s = url.trim()
            .removePrefix("https://").removePrefix("HTTPS://")
            .removePrefix("http://").removePrefix("HTTP://")
            .removePrefix("//")
        return s.substringBefore('/').substringBefore(':').substringBefore('?').lowercase()
    }

    /**
     * مقاطع المسار **بعد** المضيف — بلا بروتوكول ولا استعلام ولا fragment:
     * “https://soundcloud.com/artist/track” ← [artist, track]
     */
    internal fun pathSegments(url: String): List<String> {
        val trimmed = url.trim()
        val noProto = trimmed.substringAfter("//", trimmed)
        val path = noProto.substringBefore('?').substringBefore('#')
            .substringAfter('/', "")
        return path.split('/').filter { it.isNotBlank() }
    }

    /** قراءة قيمة معلمة استعلام بتحويل قليل — تتعامل مع ?a=b&c=d و #fragment */
    private fun String.extractQueryValue(key: String): String? {
        val query = substringAfter('?', "").substringBefore('#')
        for (pair in query.split('&')) {
            val k = pair.substringBefore('=')
            if (k == key) {
                val v = pair.substringAfter('=', "")
                return if (v.isBlank()) null else v.substringBefore('&').trim()
            }
        }
        return null
    }
}

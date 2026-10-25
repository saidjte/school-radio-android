package com.saidjte.schoolradio.data.remote

import com.saidjte.schoolradio.core.util.RemoteLinks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * v0.23.0 — استخراج تدفقات المقاطع من يوتوب وساوندكلاود عبر **NewPipeExtractor**
 * (المحرك المفتوح الرسمي لتطبيق NewPipe) — يتحدث مع المنصتين مباشرة دون وسيط،
 * وهذا هو السبب في اختياره: واجهات Piped/Invidious العامة تموت باستمرار بينما
 * يحيا هذا المحرك بتحديثات مجتمعه المستمرة.
 *
 * Google Drive والروابط المباشرة لا يحتاجان استخراجاً — تحويل رابط وفحص توفر فقط.
 *
 * كل الدوال تُعلّق (suspend) وتنفذ على Dispatchers.IO وتحترم مهلة صريحة،
 * وكل فشل يخرج برسالة عربية مفهومة عبر [RemoteImportException].
 */

/** استثناء برسالة عربية جاهزة لعرضها للمستخدم مباشرة */
class RemoteImportException(message: String) : RuntimeException(message)

/** مستخدم الوكيل القياسي — بعض المنصات ترفض الطلبات بلا User-Agent متصفح */
internal const val BROWSER_UA: String =
    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

/** تنفيذ طلبات NewPipeExtractor عبر HttpURLConnection — بلا اعتماد إضافي (لا OkHttp) */
internal class UrlConnectionDownloader : Downloader() {
    override fun execute(request: Request): Response {
        val conn = URL(request.url()).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 12_000
            conn.readTimeout = 20_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", BROWSER_UA)
            conn.setRequestProperty("Accept-Language", "ar,en;q=0.8")
            request.headers().forEach { (k, v) -> conn.setRequestProperty(k, v.joinToString(", ")) }
            val data = request.dataToSend()
            if (data != null) {
                conn.requestMethod = request.httpMethod()
                conn.doOutput = true
                conn.outputStream.use { it.write(data) }
            }
            val code = conn.responseCode
            // استجابة NewPipeExtractor نصية (HTML/JSON) — التدفقات نفسها تعود روابط نصية
            val body = (if (code in 200..399) conn.inputStream else conn.errorStream)
                ?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            @Suppress("UNCHECKED_CAST")
            val headers = conn.headerFields as? Map<String, List<String>> ?: emptyMap()
            return Response(code, conn.responseMessage ?: "", headers, body, request.url())
        } finally {
            conn.disconnect()
        }
    }
}

object StreamResolver {

    @Volatile private var initialized = false

    /** تهيئة واحدة قبل أي استخراج — بترجمة عربية مغربية للبيانات الوصفية */
    fun ensureInit() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            NewPipe.init(UrlConnectionDownloader(), Localization("ar", "MA"))
            initialized = true
        }
    }

    data class Resolved(
        val title: String,
        val uploader: String?,
        val durationMs: Long,
        val streamUrl: String,
        val mimeType: String?
    )

    /**
     * المسار الموحد: يحل أي مصدر معروف إلى تدفق قابل للتشغيل الآن.
     * يوتوب/ساوندكلاود عبر المستخرج، وDrive/المباشر عبر فحص الروابط العامة.
     */
    suspend fun resolveAny(parsed: RemoteLinks.Parsed): Resolved = when (parsed.source) {
        RemoteLinks.Source.YOUTUBE, RemoteLinks.Source.SOUNDCLOUD -> resolveExtractor(parsed)
        RemoteLinks.Source.GOOGLE_DRIVE -> resolveDrive(parsed)
        RemoteLinks.Source.DIRECT -> resolveDirect(parsed)
    }

    // ================== يوتوب + ساوندكلاود ==================

    private suspend fun resolveExtractor(parsed: RemoteLinks.Parsed): Resolved =
        withContext(Dispatchers.IO) {
            ensureInit()
            withTimeout(30_000L) {
                try {
                    val service = when (parsed.source) {
                        RemoteLinks.Source.YOUTUBE -> ServiceList.YouTube
                        else -> ServiceList.SoundCloud
                    }
                    val info = StreamInfo.getInfo(service, parsed.url)
                    val best = pickAudio(info.audioStreams)
                        ?: throw RemoteImportException(
                            "لا يوجد تدفق صوتي متاح لهذا المقطع — قد يكون خاصاً أو مقيداً بالعمر أو مدفوعاً"
                        )
                    val streamUrl = best.url?.takeIf { it.isNotBlank() }
                        ?: throw RemoteImportException("التدفق المستخرج غير مكتمل — أعد المحاولة بعد قليل")
                    Resolved(
                        title = info.name?.takeIf { it.isNotBlank() } ?: "مقطع ${parsed.source.label}",
                        uploader = info.uploaderName?.takeIf { it.isNotBlank() },
                        durationMs = info.duration.coerceAtLeast(0) * 1000L,
                        streamUrl = streamUrl,
                        mimeType = best.format?.mimeType
                    )
                } catch (e: RemoteImportException) {
                    throw e
                } catch (e: IOException) {
                    throw RemoteImportException("تعذر الاتصال بالمنصة — تحقق من الإنترنت وحاول مجدداً")
                } catch (e: Exception) {
                    throw RemoteImportException(
                        "تعذر استخراج التدفق — تأكد أن الرابط صحيح والمقطع عام ومتاح علناً"
                    )
                }
            }
        }

    /**
     * اختيار أفضل تدفق صوتي: تفضيل M4A/AAC (توافق عالٍ مع كل أجهزة الأندرويد
     * ومع دائرة المكتبة) بأعلى معدل بت، وإلا أفضل ما هو متاح (Opus/WebM يشتغل أيضاً).
     * الروابط https فقط — بعض التدفقات تُقدم بـ http فيرفضها المشغل.
     */
    internal fun pickAudio(streams: List<org.schabi.newpipe.extractor.stream.AudioStream>) =
        streams.filter { it.isUrl && it.url?.startsWith("https") == true }
            .maxWithOrNull(
                compareBy(
                    { it.format == org.schabi.newpipe.extractor.MediaFormat.M4A },
                    { it.averageBitrate }
                )
            )

    // ================== Google Drive ==================

    /**
     * رابط Drive لا يقدم التدفق مباشرة — نمر عبر نقطتي تنزيل عامتين ونتفادى
     * صفحة «الفحص الفيروسي» بقيمة confirm=t، ونرفض صفحات HTML برسالة إرشاد
     * لتغيير مشاركة الملف إلى «أي شخص لديه الرابط».
     */
    private suspend fun resolveDrive(parsed: RemoteLinks.Parsed): Resolved {
        val id = parsed.mediaId
            ?: throw RemoteImportException("تعذر قراءة معرف ملف Drive — استخدم «نسخ الرابط» من قائمة الملف")
        val candidates = listOf(
            "https://drive.usercontent.google.com/download?id=$id&export=download&confirm=t",
            "https://drive.google.com/uc?export=download&id=$id"
        )
        val error = RemoteImportException(
            "الملف في Drive غير متاح للتشغيل — تأكد من مشاركته «لأي شخص لديه الرابط» وأنه ملف صوتي"
        )
        var lastError: Exception = error
        for (url in candidates) {
            try {
                return withContext(Dispatchers.IO) { withTimeout(20_000L) { probe(url, parsed) } }
            } catch (e: RemoteImportException) {
                lastError = e
            } catch (e: Exception) {
                lastError = error
            }
        }
        throw lastError
    }

    /** فحص رابط تنزيل: نصف استجابة فقط (نطلب البايت الأول) — نتأكد أنه ملف لا صفحة */
    private fun probe(url: String, parsed: RemoteLinks.Parsed): Resolved {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", BROWSER_UA)
            conn.setRequestProperty("Range", "bytes=0-0")
            val code = conn.responseCode
            if (code !in 200..399) {
                throw RemoteImportException(
                    "الملف في Drive غير مشترك علناً — غيّر المشاركة إلى «أي شخص لديه الرابط» ثم أعد المحاولة"
                )
            }
            val contentType = (conn.getHeaderField("Content-Type") ?: "").lowercase()
            if ("text/html" in contentType || "text/plain" in contentType) {
                throw RemoteImportException(
                    "حصلنا صفحة بدل الملف — الملف كبير أو غير عام؛ شاركه «لأي شخص لديه الرابط» وأعد المحاولة"
                )
            }
            val disposition = conn.getHeaderField("Content-Disposition") ?: ""
            val filename = disposition.extractFilename()
                ?: "مقطع Drive ${parsed.mediaId?.takeLast(6) ?: ""}".trim()
            return Resolved(
                title = filename.substringBeforeLast('.').ifBlank { filename },
                uploader = null,
                durationMs = 0L,
                streamUrl = url,
                mimeType = contentType.ifBlank { "audio/mp4" }
            )
        } finally {
            conn.disconnect()
        }
    }

    // ================== الروابط الصوتية المباشرة ==================

    private suspend fun resolveDirect(parsed: RemoteLinks.Parsed): Resolved {
        // فحص توفر خفيف (مهلة قصيرة) — الفشل لا يمنع: بعض الخوادم ترفض Range
        val filename = parsed.url.substringBefore('?').substringAfterLast('/')
            .ifBlank { "مقطع مباشر" }
        return try {
            withContext(Dispatchers.IO) {
                withTimeout(10_000L) {
                    val conn = URL(parsed.url).openConnection() as HttpURLConnection
                    try {
                        conn.connectTimeout = 8_000
                        conn.readTimeout = 8_000
                        conn.setRequestMethod("HEAD")
                        conn.setRequestProperty("User-Agent", BROWSER_UA)
                        val code = conn.responseCode
                        if (code !in 200..399) {
                            throw RemoteImportException("الرابط المباشر لا يستجيب (رمز $code) — تأكد من صحته")
                        }
                        val ct = (conn.getHeaderField("Content-Type") ?: "").lowercase()
                        if ("text/html" in ct) {
                            throw RemoteImportException("الرابط يشير لصفحة لا ملف صوتي")
                        }
                        val name = (conn.getHeaderField("Content-Disposition") ?: "").extractFilename()
                            ?: java.net.URLDecoder.decode(filename, "UTF-8")
                        Resolved(
                            title = name.substringBeforeLast('.').ifBlank { name },
                            uploader = null,
                            durationMs = 0L,
                            streamUrl = parsed.url,
                            mimeType = ct.ifBlank { null }
                        )
                    } finally {
                        conn.disconnect()
                    }
                }
            }
        } catch (e: RemoteImportException) {
            throw e
        } catch (e: Exception) {
            // الخادم لا يحب HEAD — نجري ببدون معاينة غنية
            Resolved(
                title = java.net.URLDecoder.decode(filename, "UTF-8").substringBeforeLast('.'),
                uploader = null,
                durationMs = 0L,
                streamUrl = parsed.url,
                mimeType = null
            )
        }
    }

    // ================== التنزيل المحلي ==================

    /**
     * تنزيل التدفق إلى ملف محلي — بعد التحميل يصبح المقطع ملكاً للمكتبة
     * يعمل دون إنترنت للأبد (فلسفة التطبيق offline-first).
     * @return حجم الملف بالبايت
     */
    suspend fun downloadToFile(url: String, outFile: File): Long = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", BROWSER_UA)
            val code = conn.responseCode
            if (code !in 200..399) {
                throw RemoteImportException("فشل التنزيل (رمز $code) — حاول مجدداً بعد قليل")
            }
            val ct = (conn.getHeaderField("Content-Type") ?: "").lowercase()
            if ("text/html" in ct) {
                throw RemoteImportException(
                    "الخادم أعاد صفحة بدل الملف — غالباً انتهت صلاحية التدفق؛ أعد الاستيراد من الرابط الأصلي"
                )
            }
            val tmp = File(outFile.parentFile, outFile.name + ".part")
            conn.inputStream.use { input ->
                tmp.outputStream().use { out -> input.copyTo(out, 64 * 1024) }
            }
            if (tmp.length() == 0L) {
                tmp.delete()
                throw RemoteImportException("التنزيل جاء فارغاً — تحقق من الاتصال وحاول مجدداً")
            }
            if (!tmp.renameTo(outFile)) {
                tmp.copyTo(outFile, overwrite = true)
                tmp.delete()
            }
            outFile.length()
        } catch (e: RemoteImportException) {
            throw e
        } catch (e: IOException) {
            throw RemoteImportException("انقطع الاتصال أثناء التنزيل — تحقق من الشبكة وحاول مجدداً")
        } finally {
            conn.disconnect()
        }
    }

    // ================== أدوات ==================

    /** قراءة اسم الملف من ترويسة Content-Disposition (تدعم filename* المشفرة) */
    private fun String.extractFilename(): String? {
        if (isBlank()) return null
        val star = Regex("filename\\*=UTF-8''([^;\\r\\n]+)").find(this)?.groupValues?.get(1)
        if (star != null) {
            val decoded = runCatching { java.net.URLDecoder.decode(star, "UTF-8") }.getOrNull() ?: star
            return decoded.trim('"', ' ', ';').takeIf { it.isNotBlank() }
        }
        val plain = Regex("filename=\"?([^\";\\r\\n]+)\"?").find(this)?.groupValues?.get(1)
        return plain?.trim()?.takeIf { it.isNotBlank() }
    }
}

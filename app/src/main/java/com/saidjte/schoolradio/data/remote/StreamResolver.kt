package com.saidjte.schoolradio.data.remote

import com.saidjte.schoolradio.core.util.RemoteLinks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
     * اختيار أفضل تدفق صوتي — v0.42.0 تصحيح جذري: **تدفقات تقدمية حصراً**
     * (PROGRESSIVE_HTTP: ملف صوتي واحد يُنزَّل ويُبثّ بمحرف Range).
     *
     * القاعدة القديمة كانت تختار أعلى معدل بت وهي تكتشف أن قوائم HLS/DASH
     * تُسجّل بصيغة M4A وبمعدلات بت أعلى من التقدمية — فيفوز ملف .m3u8/.mpd
     * نصي بحجم كيلوبايات لا الملف الصوتي: تنزيله يعطي ملفاً تالفاً، وتشغيله
     * يفشل أصلاً (وحدات media3-hls/dash ليست في الاعتماديات) — وربما كان
     * هذا بطة «بطء التنزيل» الثانية: تمرير كيلوبايات يُحتسب تنزيلاً سريعاً
     * زائفاً ثم فشل.
     *
     * ضمن التقدمية: M4A/AAC أولاً (توافق عالٍ مع كل الأجهزة) بأعلى معدل بت.
     * الروابط https فقط — بعض التدفقات تُقدم بـ http فيرفضها المشغل.
     */
    internal fun pickAudio(streams: List<org.schabi.newpipe.extractor.stream.AudioStream>) =
        streams.filter { it.isUrl && it.url?.startsWith("https") == true }
            .filter {
                it.deliveryMethod == org.schabi.newpipe.extractor.stream.DeliveryMethod.PROGRESSIVE_HTTP
            }
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
     *
     * v0.42.0: تنزيل **مجزأ متوازٍ** وفق خطة [ParallelDownloadLogic] —
     * الاستطلاع أولًا (GET بشريحة ضئيلة يقرأ الترويسات): إن أعلن الخادم
     * دعم Range والحجم فلملف يُقسم ست شرائح تُنزَّل اتصالات متوازية تكتب
     * كل واحدة في موضعها بمحرف Range (يفك خنق الاتصال الواحد الذي يفرضه
     * googlevideo — سبب بطء التنزيل المبلغ عنه) — وفشل التفرع لأي سبب
     * غير رفض الخادم الصادق يهبط مرة واحدة للتدفق الواحد (سلوك v0.25.0).
     *
     * معايرة التقدم الحي عبر [onProgress] (البايتات المنزلة، الكلي إن عُرف)
     * + رسالة 403 إرشادية تفسر سبب الرفض (انتهاء صلاحية رابط التدفق) بدل رمز غامض.
     *
     * @return حجم الملف بالبايت
     */
    suspend fun downloadToFile(
        url: String,
        outFile: File,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> }
    ): Long = withContext(Dispatchers.IO) {
        val probe = runCatching { probeRange(url) }
            .getOrDefault(RangeProbe(total = -1L, acceptRanges = false))
        val plan = ParallelDownloadLogic.plan(probe.total, probe.acceptRanges)
        // v0.43.0: ملف .part فريد لكل عملية — كان الاسم الثابت «name.part»
        // يجعل تنزيلين متنافسين لنفس العنوان يكتبان الملف الوسيط نفسه فيفسده
        // أو ينهار أحدهما باختفائه تحت قدميه — الفريدة تلغي التصادم بنيوياً
        val part = File(outFile.parentFile, outFile.name + ".part-" + java.util.UUID.randomUUID())
        if (plan != null) {
            try {
                return@withContext downloadSegmented(url, outFile, part, plan, probe.total, onProgress)
            } catch (e: RemoteImportException) {
                part.delete()
                throw e // رفض خادم صادق (403/410/صفحة HTML) — لا يحجبه سقوط للتدفق الواحد
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // شبكة متقطعة أو خادم تجاهل Range وسط التفرع — هبوط واحد موثوق
                part.delete()
            }
        }
        downloadSequential(url, outFile, part, onProgress)
    }

    /** نتيجة استطلاع التدفق: الحجم المعلن وهل يعلن الخادم دعم محارف Range */
    private class RangeProbe(val total: Long, val acceptRanges: Boolean)

    /**
     * استطلاع خفيف: GET بشريحة «bytes=0-0» — يقرأ الترويسات فقط ولا يُنزّل الجسد.
     * نستخدم GET لا HEAD لأن بعض خوادم المنصات ترفض HEAD، والاستجابة 206
     * للشريحة الضئيلة هي الدليل العملي على دعم Range (أصدق من ترويسة معلنة).
     */
    private fun probeRange(url: String): RangeProbe {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 12_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", BROWSER_UA)
            conn.setRequestProperty("Range", "bytes=0-0")
            val code = conn.responseCode
            checkDownloadResponse(code, conn)
            val acceptRanges = code == 206
            val total = when {
                code == 206 -> conn.getHeaderField("Content-Range")
                    ?.substringAfter('/')?.toLongOrNull() ?: -1L
                else -> conn.contentLengthLong.takeIf { it > 0 } ?: -1L
            }
            return RangeProbe(total, acceptRanges)
        } finally {
            conn.disconnect()
        }
    }

    /** رسائل رفض الخادم الصادقة — واحدة لكل مسارات التنزيل (استطلاع/شرائح/تدفق واحد) */
    private fun checkDownloadResponse(code: Int, conn: HttpURLConnection) {
        if (code in 200..399) return
        throw RemoteImportException(
            if (code == 403 || code == 410)
                "الخادم رفض التدفق ($code) — انتهت صلاحية الرابط غالباً؛ أعد المحاولة فتُجدد آلياً أو أعد الاستيراد"
            else "فشل التنزيل (رمز $code) — حاول مجدداً بعد قليل"
        )
    }

    private fun isHtmlResponse(conn: HttpURLConnection): Boolean =
        "text/html" in (conn.getHeaderField("Content-Type") ?: "").lowercase()

    /**
     * التنزيل المجزأ المتوازي: ملف .part يحجز حجمه كاملاً مسبقاً ثم تكتب كل
     * شريحة في موضعها (كتابات موضعية على قناة واحدة — آمنة خيطياً عكسياً
     * لأن كل شريحة تملك نطاق محارف لا يلمسه غيرها)، والتقدم يجمع من كل
     * الشرائح، والنهاية تتحقق من اكتمال المجموع قبل إعادة التسمية.
     */
    private suspend fun downloadSegmented(
        url: String,
        outFile: File,
        part: File,
        plan: List<ParallelDownloadLogic.Segment>,
        total: Long,
        onProgress: (Long, Long) -> Unit
    ): Long = withContext(Dispatchers.IO) {
        val raf = java.io.RandomAccessFile(part, "rw")
        try {
            raf.setLength(total)
            val channel = raf.channel
            val downloaded = java.util.concurrent.atomic.AtomicLongArray(plan.size)
            val lastReported = java.util.concurrent.atomic.AtomicLong(0L)

            fun reportProgress() {
                val cur = ParallelDownloadLogic.aggregate(
                    LongArray(downloaded.length()) { downloaded.get(it) }
                )
                if (DownloadLogic.shouldReport(lastReported.get(), cur)) {
                    lastReported.set(cur)
                    onProgress(cur, total)
                }
            }

            coroutineScope {
                plan.map { seg ->
                    async {
                        val conn = URL(url).openConnection() as HttpURLConnection
                        try {
                            conn.connectTimeout = 12_000
                            conn.readTimeout = 30_000
                            conn.instanceFollowRedirects = true
                            conn.setRequestProperty("User-Agent", BROWSER_UA)
                            conn.setRequestProperty("Range", ParallelDownloadLogic.rangeHeader(seg))
                            val code = conn.responseCode
                            checkDownloadResponse(code, conn)
                            // خادم تجاهل Range وأعاد 200 كاملاً — الكتابة الموضعية هنا
                            // ستلطخ الشرائح الأخرى؛ هبوط للتدفق الواحد واجب لا خيار
                            if (code != 206) {
                                throw IllegalStateException("الخادم تجاهل محارف Range (رمز $code)")
                            }
                            if (isHtmlResponse(conn)) {
                                throw RemoteImportException(
                                    "الخادم أعاد صفحة بدل الملف — غالباً انتهت صلاحية التدفق؛ أعد الاستيراد من الرابط الأصلي"
                                )
                            }
                            val buf = ByteArray(ParallelDownloadLogic.READ_BUFFER_BYTES)
                            conn.inputStream.use { input ->
                                var pos = seg.start
                                while (true) {
                                    val n = input.read(buf)
                                    if (n < 0) break
                                    channel.write(java.nio.ByteBuffer.wrap(buf, 0, n), pos)
                                    pos += n
                                    downloaded.addAndGet(seg.index, n.toLong())
                                    reportProgress()
                                }
                                // قُطعت الشريحة مبكراً وسكتت — نقصٌ لا يُكتشف غيره إلا هنا
                                if (pos - seg.start != seg.length) {
                                    throw IllegalStateException("انقطعت شريحة ${seg.index} قبل اكتمالها")
                                }
                            }
                        } finally {
                            conn.disconnect()
                        }
                    }
                }.awaitAll()
            }

            val downloadedTotal = ParallelDownloadLogic.aggregate(
                LongArray(downloaded.length()) { downloaded.get(it) }
            )
            if (!ParallelDownloadLogic.isComplete(plan, downloadedTotal)) {
                throw IllegalStateException("مجموع الشرائح لا يطابق الحجم المعلن")
            }
            onProgress(downloadedTotal, total)
            channel.force(true)
            raf.close()
            if (!part.renameTo(outFile)) {
                part.copyTo(outFile, overwrite = true)
                part.delete()
            }
            outFile.length()
        } finally {
            runCatching { raf.close() }
        }
    }

    /** التدفق الواحد — سلوك v0.25.0 الحرفي: للاحجام الصغيرة والخوادم بلا Range */
    private suspend fun downloadSequential(
        url: String,
        outFile: File,
        tmp: File,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): Long = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", BROWSER_UA)
            val code = conn.responseCode
            checkDownloadResponse(code, conn)
            if (isHtmlResponse(conn)) {
                throw RemoteImportException(
                    "الخادم أعاد صفحة بدل الملف — غالباً انتهت صلاحية التدفق؛ أعد الاستيراد من الرابط الأصلي"
                )
            }
            // الحجم الكلي إن أعلنه الخادم — يُغذي شريط التقدم بالنسبة
            val total = conn.contentLengthLong
            onProgress(0L, total.toLong())
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(ParallelDownloadLogic.READ_BUFFER_BYTES)
                    var downloaded = 0L
                    var lastReported = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        downloaded += n
                        // خنق الإبلاغ: كل 256KB — بلا إغراق لإعادة رسم الواجهة
                        if (DownloadLogic.shouldReport(lastReported, downloaded)) {
                            lastReported = downloaded
                            onProgress(downloaded, total.toLong())
                        }
                    }
                    onProgress(downloaded, total.toLong())
                }
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

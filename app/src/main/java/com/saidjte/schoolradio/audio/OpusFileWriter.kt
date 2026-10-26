package com.saidjte.schoolradio.audio

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
import com.saidjte.schoolradio.core.util.OggOpusWriter
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * كاتب ملفات Opus HD: يستقبل دفعات PCM 16-bit ويكتبها ملفاً ‎.ogg
 * (Opus داخل حاوية OGG) — أعلى جودة متاحة لتسجيلات الميكروفون في التطبيق.
 *
 * ─── v0.38.0 — الإصلاح الجذري لعطل «التسجيل لا يُشغَّل» ───
 * حتى v0.37.0 كانت الحاوية تُكتب عبر MediaMuxer(MUXER_OUTPUT_OGG) وهو منفذ
 * هشّ على أجهزة كثيرة: يفشل أحياناً عند البدء (فيسقط التسجيل إلى AAC) وعند
 * «نجاحه» ينتج حاويات برؤوس/granule تالفة يرفضها ExoPlayer. الآن الحاوية
 * تُكتب بأنفسنا بايتاً بايتاً عبر [OggOpusWriter] (RFC 7845) — نتيجة حتمية
 * متطابقة على كل جهاز، ومُتحقق منها وحدوياً وخارجياً (ffprobe).
 * المشفر نفسه MediaCodec audio/opus البرمجي (c2.android.opus.encoder) —
 * متوفر على كل أجهزة أندرويد 10+ ويعمل بلا انحراف، والفحص المسبق له عبر
 * [encoderAvailable] يبقى بوابة قرار الترميز في RecordingCodecLogic.
 *
 * ─── قرارات فنية ───
 * - الميكروفون يعمل أصلاً بـ48kHz (MicrophoneController.SAMPLE_RATE) وهي المعدل
 *   الأصلي الوحيد لـOpus: لا إعادة أخذ عينات، فالمدخل يُرمَّز كما يُلتقط.
 * - csd-0 (OpusHead من المشفر) لا يُكتب حزمة بيانات — منه نقرأ pre-skip فقط،
 *   ورأس الدفق نكتبه بأنفسنا.
 * - granule يُحسب من TOC كل حزمة مُصدَرة فعلاً — مدة الملف تطابق محتواه حتى
 *   مع سقوط دفعات إدخال عند انشغال المشفر مؤقتاً.
 * - عند الإغلاق: تحقق بنيوي (OggS + OpusHead + حجم معقول) — ملف غير سليم
 *   يُحذف ولا يعود للإدارة إطلاقاً، فلا ملفات مكسورة تتسلل إلى الأرشيف.
 * - feed() غير محظورة (dequeueInputBuffer بزمن 0) وfinish() ترسل EOS وتفرّغ
 *   حتى نهاية الدفق — نفس عقد AacFileWriter تماماً.
 */
class OpusFileWriter(
    filePath: String,
    private val sampleRate: Int = MicrophoneController.SAMPLE_RATE, // 48000 = أصلي لـOpus
    private val channelCount: Int = 1,
    private val bitRate: Int = BIT_RATE
) : PcmAudioWriter {
    private val outFile = File(filePath)
    private var codec: MediaCodec? = null
    private var sink: OutputStream? = null
    private var ogg: OggOpusWriter? = null
    private var samplesWritten = 0L
    private var preSkip = OggOpusWriter.DEFAULT_PRE_SKIP
    private val bufferInfo = MediaCodec.BufferInfo()

    @Volatile
    override var failed: String? = null
        private set

    override val totalSamples: Long get() = samplesWritten

    override val filePath: String get() = outFile.absolutePath

    /** تهيئة المُشفّر وفتح الملف — يعيد null عند النجاح أو رسالة خطأ */
    override fun start(): String? {
        if (Build.VERSION.SDK_INT < MIN_API) {
            failed = "Opus يتطلب أندرويد 10 أو أحدث"
            return failed
        }
        return try {
            val format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_OPUS, sampleRate, channelCount
            ).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                // دفعة إدخال ما (16-bit مونو) — سقف سخي يمنع رفض المخازن
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 65_536)
            }
            val c = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
            c.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            c.start()
            codec = c
            sink = BufferedOutputStream(FileOutputStream(outFile), 1 shl 16)
            null
        } catch (e: Exception) {
            releaseQuietly()
            outFile.delete() // فشل البدء = لا ملف فارغ يتيم في مجلد الأرشيف
            failed = e.message ?: "تعذر تهيئة مشفر Opus"
            failed
        }
    }

    /** تغذية دفعة PCM — من خيط الصوت فقط، بلا استثناءات أبداً */
    override fun feed(pcm: ShortArray, n: Int) {
        val c = codec ?: return
        if (failed != null || n <= 0) return
        try {
            val inIndex = c.dequeueInputBuffer(0) // زمن صفر: لا نعطل خيط البث أبداً
            if (inIndex >= 0) {
                val input: java.nio.ByteBuffer = c.getInputBuffer(inIndex) ?: return
                input.clear()
                for (i in 0 until n) input.putShort(pcm[i])
                val ptsUs = samplesWritten * 1_000_000L / sampleRate
                c.queueInputBuffer(inIndex, 0, n * 2, ptsUs, 0)
                samplesWritten += n
            }
            drain(endOfStream = false)
        } catch (e: Exception) {
            failed = e.message ?: "خطأ أثناء التغذية"
        }
    }

    /** إغلاق الملف: EOS + تفريغ كامل + صفحة EOS — يعيد (المدة_ms، الحجم_بايت) أو null */
    @Synchronized
    override fun finish(): Pair<Long, Long>? {
        val c = codec
        if (c != null && failed == null) {
            try {
                var queued = false
                var attempts = 0
                while (!queued && attempts < 50) {
                    val inIndex = c.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val ptsUs = samplesWritten * 1_000_000L / sampleRate
                        c.queueInputBuffer(
                            inIndex, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        queued = true
                    }
                    attempts++
                }
                drain(endOfStream = queued)
            } catch (e: Exception) {
                failed = e.message ?: "خطأ عند إغلاق الملف"
            }
        }
        var finalGranule = 0L
        if (failed == null) {
            try {
                val o = ogg ?: throw IllegalStateException("لم يُنتج المشفر أي بيانات")
                finalGranule = o.finalizeStream()
                sink?.flush()
            } catch (e: Exception) {
                failed = e.message ?: "خطأ عند إغلاق الحاوية"
            }
        }
        val size = outFile.length()
        val ok = failed == null && finalGranule > preSkip && hasValidContainerSignature()
        if (!ok) outFile.delete() // لا ملف مكسور يتيمة في الأرشيف — قرار الكاتب لا الإدارة
        releaseQuietly()
        return if (ok) ((finalGranule - preSkip) * 1000L / sampleRate) to size else null
    }

    /** تفريغ مخازن الإخراج إلى كاتب الحاوية حتى نفادها أو بلوغ EOS */
    private fun drain(endOfStream: Boolean) {
        val c = codec ?: return
        var spins = 0
        while (true) {
            val outIndex = c.dequeueOutputBuffer(bufferInfo, if (endOfStream) 10_000 else 0)
            when {
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER ->
                    if (!endOfStream) return
                    else if (++spins > 100) return // حاجز أمان ضد الحلقة اللانهائية

                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED ->
                    if (ogg == null) startContainer(c.outputFormat)

                outIndex >= 0 -> {
                    val flags = bufferInfo.flags
                    // csd-0 ليس حزمة صوت — رأس الدفق نكتبه بأنفسنا في startContainer
                    if (bufferInfo.size > 0 && flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        if (ogg == null) startContainer(c.outputFormat)
                        val o = ogg
                        val enc = c.getOutputBuffer(outIndex)
                        if (o != null && enc != null) {
                            enc.position(bufferInfo.offset)
                            enc.limit(bufferInfo.offset + bufferInfo.size)
                            val packet = ByteArray(bufferInfo.size)
                            enc.get(packet)
                            o.writePacket(packet)
                        }
                    }
                    c.releaseOutputBuffer(outIndex, false)
                    if (flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    /** بدء الحاوية: pre-skip من csd-0 ثم صفحتا الرأس — تُنادى مرة واحدة */
    private fun startContainer(format: MediaFormat) {
        val csd = format.getByteBuffer("csd-0")
        preSkip = OggOpusWriter.preSkipFromCsd(csd?.let { b ->
            b.position(0)
            ByteArray(b.remaining()).also { b.get(it) }
        }) ?: OggOpusWriter.DEFAULT_PRE_SKIP
        val s = sink ?: throw IllegalStateException("الملف غير مفتوح")
        val o = OggOpusWriter(s, channelCount, sampleRate, preSkip)
        o.writeHeaderPages()
        ogg = o
    }

    /** تحقق بنيوي سريع: يبدأ بـOggS ويحمل OpusHead عند بداية الحمولة (البايت 28) */
    private fun hasValidContainerSignature(): Boolean {
        if (outFile.length() < 200) return false
        return try {
            outFile.inputStream().use { ins ->
                val head = ByteArray(36)
                var read = 0
                while (read < head.size) {
                    val r = ins.read(head, read, head.size - read)
                    if (r < 0) break
                    read += r
                }
                read == head.size &&
                    head[0] == 'O'.code.toByte() && head[1] == 'g'.code.toByte() &&
                    head[2] == 'g'.code.toByte() && head[3] == 'S'.code.toByte() &&
                    head[28] == 'O'.code.toByte() && head[29] == 'p'.code.toByte() &&
                    head[30] == 'u'.code.toByte() && head[31] == 's'.code.toByte() &&
                    head[32] == 'H'.code.toByte() && head[33] == 'e'.code.toByte() &&
                    head[34] == 'a'.code.toByte() && head[35] == 'd'.code.toByte()
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun releaseQuietly() {
        try { codec?.stop() } catch (_: Exception) {}
        try { codec?.release() } catch (_: Exception) {}
        codec = null
        try { sink?.close() } catch (_: Exception) {}
        sink = null
        ogg = null
    }

    companion object {
        /** أول واجهة أندرويد تتيح مشفر Opus البرمجي الرسمي في MediaCodecList */
        const val MIN_API = 29

        /** 128kbps مونو: الشفافية الكاملة لكلام المذيع مع Opus */
        const val BIT_RATE = com.saidjte.schoolradio.core.util.RecordingCodecLogic.OPUS_BIT_RATE

        /**
         * هل يوجد مشفر Opus على هذا الجهاز؟ — يُفحص مرة واحدة عند بدء كل تسجيل
         * عبر MediaCodecList الرسمي (لا قوائم يدوية): الأجهزة بلا مشفر عتادي
         * كثيراً ما تتيح المشفر البرمجي c2.android.opus.encoder، والفحص يلتقطه.
         */
        fun encoderAvailable(): Boolean = runCatching {
            android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
                info.isEncoder && info.supportedTypes.contains(MediaFormat.MIMETYPE_AUDIO_OPUS)
            }
        }.getOrDefault(false)
    }
}

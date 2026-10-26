package com.saidjte.schoolradio.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * كاتب ملفات AAC: يستقبل دفعات PCM 16-bit ويكتبها ملفاً ‎.m4a (AAC-LC داخل MPEG-4).
 *
 * ─── لماذا هذه الفئة أصلاً؟ ───
 * قيد أندرويد الحاسم: الميكروفون مورد وحيد — لا يمكن تشغيل MediaRecorder وAudioRecord
 * معاً على نفس الجهاز (سيُفشل أحدهما). حل «خط الأنابيب الواحد» المختار:
 *   AudioRecord واحد في MicrophoneController يمسك الميكروفون ويمرر الصوت للمكبرات،
 *   وكل دفعة PCM تُمرَّر منه إلى هنا فتُشفَّر AAC فوراً عبر MediaCodec وتُغلَّف
 *   في حاوية MP4 عبر MediaMuxer.
 * النتيجة: بث مباشر + تسجيل متزامن بلا تنازع، وبنفس كفاءة التشفير العتادي إن توفر.
 *
 * ─── قرارات فنية ───
 * - AAC-LC عند 48kHz مونو 128kbps: جودة كلام ممتازة (~57MB/ساعة) — نصف الحجم تقريباً
 *   مقابل جودة لا تكاد يسمعها الأذن عند 256kbps ستيريو.
 * - feed() غير محظورة عملياً (dequeueInputBuffer بزمن 0): إن لم يتوفر مخزن إدخال
 *   تُترك الدفعة (نادر جداً عند دفعات 10ms) حتى لا يتعطل خيط البث الحي أبداً.
 * - العد sample-level: المدة تُحسب من العينات المشفَّرة فعلاً فتطابق طول الملف تماماً.
 */
class AacFileWriter(
    filePath: String,
    private val sampleRate: Int = MicrophoneController.SAMPLE_RATE,
    private val channelCount: Int = 1,
    private val bitRate: Int = BIT_RATE
) {
    private val outFile = File(filePath)
    private var codec: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var trackIndex = -1
    private var muxerStarted = false
    private var samplesWritten = 0L
    private val bufferInfo = MediaCodec.BufferInfo()

    /** رسالة فشل إن حدث — تفحصها الإدارة قبل الاعتماد على الناتج */
    @Volatile
    var failed: String? = null
        private set

    /** مجموع العينات المشفَّرة فعلاً — أساس حساب المدة */
    val totalSamples: Long get() = samplesWritten

    /** المسار النهائي للملف (يُستخدم عند إدراج سجل الأرشيف) */
    val filePath: String get() = outFile.absolutePath

    /** تهيئة المُشفّر والحاوية — يعيد null عند النجاح أو رسالة خطأ */
    fun start(): String? {
        return try {
            val format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount
            ).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                // دفعة إدخال ما (16-bit مونو) — سقف سخي يمنع رفض المخازن
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 65_536)
            }
            val c = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            c.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            c.start()
            codec = c
            muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            null
        } catch (e: Exception) {
            releaseQuietly()
            failed = e.message ?: "تعذر تهيئة مشفر AAC"
            failed
        }
    }

    /**
     * تغذية دفعة PCM — تُستدعى من خيط الصوت فقط (خيط البث أو خيط الفقرة).
     * لا تُلقي استثناءات أبداً: أي خطأ يُسجَّل في failed ويستمر البث الحي كما هو.
     */
    fun feed(pcm: ShortArray, n: Int) {
        val c = codec ?: return
        if (failed != null || n <= 0) return
        try {
            val inIndex = c.dequeueInputBuffer(0) // زمن صفر: لا نعطل خيط البث أبداً
            if (inIndex >= 0) {
                val input: ByteBuffer = c.getInputBuffer(inIndex) ?: return
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

    /**
     * إغلاق الملف: إشارة نهاية الدفق ثم تفريغ كامل حتى إطار EOS.
     * يعيد (المدة_ms، الحجم_بايت) عند النجاح أو null عند فشل سابق/حالي.
     */
    @Synchronized
    fun finish(): Pair<Long, Long>? {
        val c = codec ?: return null
        if (failed == null) {
            try {
                // أرسل EOS مع إعادة محاولة قصيرة حتى يتوفر مخزن إدخال
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
        val durationMs = samplesWritten * 1000L / sampleRate
        val ok = failed == null && muxerStarted
        releaseQuietly()
        return if (ok) durationMs to outFile.length() else null
    }

    /** تفريغ مخازن الإخراج إلى المكسر حتى نفادها أو بلوغ EOS */
    private fun drain(endOfStream: Boolean) {
        val c = codec ?: return
        val m = muxer ?: return
        var spins = 0
        while (true) {
            val outIndex = c.dequeueOutputBuffer(bufferInfo, if (endOfStream) 10_000 else 0)
            when {
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER ->
                    if (!endOfStream) return
                    else if (++spins > 100) return // حاجز أمان ضد الحلقة اللانهائية

                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    // صيغة الإخراج النهائية (تشمل csd-0) تُسجَّل كمسار في الحاوية
                    trackIndex = m.addTrack(c.outputFormat)
                    m.start()
                    muxerStarted = true
                }

                outIndex >= 0 -> {
                    val encoded: ByteBuffer? = c.getOutputBuffer(outIndex)
                    if (encoded != null && bufferInfo.size > 0 && muxerStarted) {
                        encoded.position(bufferInfo.offset)
                        encoded.limit(bufferInfo.offset + bufferInfo.size)
                        m.writeSampleData(trackIndex, encoded, bufferInfo)
                    }
                    c.releaseOutputBuffer(outIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    private fun releaseQuietly() {
        try { codec?.stop() } catch (_: Exception) {}
        try { codec?.release() } catch (_: Exception) {}
        codec = null
        try {
            if (muxerStarted) muxer?.stop()
        } catch (_: Exception) {}
        try { muxer?.release() } catch (_: Exception) {}
        muxer = null
        muxerStarted = false
    }

    companion object {
        /** 128kbps: نقطة الجذى لكلام الميكروفون المونو */
        const val BIT_RATE = 128_000
    }
}

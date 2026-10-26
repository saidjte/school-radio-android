package com.saidjte.schoolradio.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import java.io.File
import java.nio.ByteBuffer

/**
 * v0.29.0 — كاتب ملفات Opus HD: يستقبل دفعات PCM 16-bit ويكتبها ملفاً ‎.ogg
 * (Opus داخل حاوية OGG) — أعلى جودة متاحة لتسجيلات الميكروفون في التطبيق.
 *
 * ─── لماذا Opus HD؟ ───
 * - جودة كلام شفافة تماماً عند 128kbps مونو، وأفضل تعامل مع موسيقى الجينلات
 *   (بلا صفير الترددات العالية الذي يظهر أحياناً في AAC) — والحجم نفسه تقريباً.
 * - الميكروفون في التطبيق يعمل أصلاً بـ48kHz (MicrophoneController.SAMPLE_RATE)
 *   وهي المعدل الأصلي الوحيد لـOpus: لا إعادة أخذ عينات، فالمدخل يُرمَّز كما يُلتقط.
 * - الحاوية OGG كإخراج MediaMuxer متاحة من Android 10 (API 29) — الفئة نفسها
 *   محمية بفحص إصدار، والإدارة ترجع تلقائياً إلى AacFileWriter على الأقدم.
 * - التشغيل: فك ترميز Opus مدعوم في أندرويد منذ API 21، فالأرشيف المختلط يعمل
 *   في MediaPlayer/WaveformExtractor على كل أجهزة المدارس دون شرط.
 *
 * ─── نفس عقد AacFileWriter تماماً ───
 * feed() غير محظورة (dequeueInputBuffer بزمن 0) وfinish() ترسل EOS وتفرّغ حتى
 * نهاية الدفق، والمدة تُحسب من العينات المشفَّرة فعلاً — أخذت البنية المرجعية
 * نفسها مع اختلافات الترميز فقط (MIMETYPE_AUDIO_OPUS + MUXER_OUTPUT_OGG).
 */
class OpusFileWriter(
    filePath: String,
    private val sampleRate: Int = MicrophoneController.SAMPLE_RATE, // 48000 = أصلي لـOpus
    private val channelCount: Int = 1,
    private val bitRate: Int = BIT_RATE
) : PcmAudioWriter {
    private val outFile = File(filePath)
    private var codec: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var trackIndex = -1
    private var muxerStarted = false
    private var samplesWritten = 0L
    private val bufferInfo = MediaCodec.BufferInfo()

    @Volatile
    override var failed: String? = null
        private set

    override val totalSamples: Long get() = samplesWritten

    override val filePath: String get() = outFile.absolutePath

    /** تهيئة المُشفّر والحاوية — يعيد null عند النجاح أو رسالة خطأ */
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
            muxer = MediaMuxer(
                outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG
            )
            null
        } catch (e: Exception) {
            releaseQuietly()
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

    /** إغلاق الملف: EOS + تفريغ كامل — يعيد (المدة_ms، الحجم_بايت) أو null */
    @Synchronized
    override fun finish(): Pair<Long, Long>? {
        val c = codec ?: return null
        if (failed == null) {
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
                    // صيغة الإخراج النهائية (تشمل csd-0/PreSkip الخاصة بـOpus) تُسجَّل كمسار
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
        /** أول واجهة أندرويد تتيح مشفر Opus + حاوية OGG في MediaMuxer */
        const val MIN_API = 29

        /** 128kbps مونو: الشفافية الكاملة لكلام المذيع مع Opus */
        const val BIT_RATE = com.saidjte.schoolradio.core.util.RecordingCodecLogic.OPUS_BIT_RATE

        /**
         * هل يوجد مشفر Opus على هذا الجهاز؟ — يُفحص مرة واحدة عند بدء كل تسجيل
         * عبر MediaCodecList الرسمي (لا قوائم يدوية): الأجهزة بلا مشفر عتادي
         * كثيراً ما تتيح المشفر البرمجي c2.android.opus.encoder، والفحص يلتقطه.
         */
        fun encoderAvailable(): Boolean = runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
                info.isEncoder && info.supportedTypes.contains(MediaFormat.MIMETYPE_AUDIO_OPUS)
            }
        }.getOrDefault(false)
    }
}

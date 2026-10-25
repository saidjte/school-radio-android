package com.saidjte.schoolradio.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.saidjte.schoolradio.core.util.SilenceLogic

/**
 * v0.20.0 — مستخرج الشكل الموجي لمحرر القص اليدوي.
 *
 * يفك تشفير الملف كاملاً (MediaExtractor + MediaCodec → PCM) ويقيس قمة السعة
 * لكل نافذة 20ms — نفس تقنية [SilenceAnalyzer] التي أثبتت موثوقيتها في v0.18.0،
 * لكن عبر المدة كلها بدل أول ثوانٍ، فتُبنى صورة بصرية صادقة لكل الملف:
 * المذيع يرى بالعين أين تبدأ النغمة وأين تسكت قبل أن يحرك مقابض القص.
 *
 * حدود الأمان (نفس فلسفة v0.18.0 — التحليل تحسين وليس شرطاً):
 *  - سقف فك التشفير [MAX_DECODE_MS]: تسجيلات ساعة كاملة لا تجمد الواجهة —
 *    ما فُكّ يُرسم وعلامة [Waveform.complete] تخبر الواجهة أن الباقي لم يُقَس.
 *  - أي استثناء (ملف تالف/صيغة عجيبة) يعيد null — المحرر يكمل بمنزلقاته
 *    والشريط التقريبي كما في v0.19.0، لا انهيار ولا حلقة انتظار أبدية.
 */
object WaveformExtractor {

    /** سقف فك التشفير بالميلي ثانية — 15 دقيقة تكفي أطول فقرة مدرسية عملية */
    const val MAX_DECODE_MS = 15 * 60_000L

    /**
     * نتيجة الاستخراج.
     * @param windowPeaks قمة سعة كل نافذة (0..32767) بمعدل نافذة كل [windowMs]
     * @param windowMs عرض النافذة الزمنية بالميلي ثانية (20ms — نفس نافذة كاشف الصمت)
     * @param decodedMs الزمن المكوَّس فعلياً (قد يقل عن مدة الملف عند بلوغ السقف)
     * @param complete هل فُكّ الملف حتى نهايته؟
     */
    data class Waveform(
        val windowPeaks: IntArray,
        val windowMs: Long,
        val decodedMs: Long,
        val complete: Boolean
    )

    /** كم محاولة انتظار متتالية (10ms لكل واحدة) قبل الاستسلام بسلام */
    private const val MAX_TRY_AGAIN_STREAK = 200

    /**
     * استخراج الشكل الموجي لملف صوتي.
     * @param uriStr مسار content:// أو file:// أو مسار نظام مباشر
     * @return الشكل الموجي، أو null عند أي فشل (المحرر يكمل بلا موجة)
     */
    fun extract(context: Context, uriStr: String): Waveform? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            val uri = android.net.Uri.parse(uriStr)
            when {
                uriStr.startsWith("content://") -> extractor.setDataSource(context, uri, null)
                else -> extractor.setDataSource(uriStr)
            }

            // اختيار مسار الصوت
            var audioTrack = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    audioTrack = i; format = f; break
                }
            }
            if (audioTrack < 0 || format == null) return null
            extractor.selectTrack(audioTrack)
            extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            val mime = format.getString(MediaFormat.KEY_MIME)!!
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val samplesPerWindow =
                (sampleRate * SilenceLogic.WINDOW_MS / 1000L).toInt().coerceAtLeast(1)
            val maxWindows = (MAX_DECODE_MS / SilenceLogic.WINDOW_MS).toInt()

            val peaks = ArrayList<Int>(maxWindows.coerceAtMost(90_000))
            val info = MediaCodec.BufferInfo()
            var windowPeak = 0
            var samplesInWindow = 0
            var sawInputEos = false
            var sawOutputEos = false
            var tryAgainStreak = 0
            var hitCap = false

            loop@ while (!sawOutputEos && !hitCap) {
                // تغذية المدخل من الملف
                if (!sawInputEos) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)!!
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(
                                inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            sawInputEos = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                // حصاد المخرجات (PCM) وقياس قمة كل نافذة
                val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIdx >= 0 -> {
                        tryAgainStreak = 0
                        val out = codec.getOutputBuffer(outIdx)
                        if (out != null && info.size > 0) {
                            out.position(info.offset)
                            out.limit(info.offset + info.size)
                            val encoding = runCatching {
                                codec.outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                            }.getOrDefault(AudioFormat.ENCODING_PCM_16BIT)
                            val isFloat = encoding == AudioFormat.ENCODING_PCM_FLOAT
                            val bytesPerSample = if (isFloat) 4 else 2
                            val samples = info.size / bytesPerSample
                            for (s in 0 until samples) {
                                val v = if (isFloat) {
                                    (out.getFloat() * Short.MAX_VALUE).toInt()
                                } else {
                                    out.short.toInt()
                                }
                                val a = if (v < 0) -v else v
                                if (a > windowPeak) windowPeak = a
                                samplesInWindow++
                                if (samplesInWindow >= samplesPerWindow) {
                                    peaks.add(windowPeak)
                                    windowPeak = 0
                                    samplesInWindow = 0
                                    if (peaks.size >= maxWindows) {
                                        hitCap = true
                                        break@loop
                                    }
                                }
                            }
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0)
                            sawOutputEos = true
                    }
                    outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (++tryAgainStreak >= MAX_TRY_AGAIN_STREAK) break@loop
                    }
                    else -> { /* INFO_OUTPUT_FORMAT_CHANGED — نتابع */ }
                }
            }
            if (samplesInWindow > 0 && peaks.size < maxWindows) peaks.add(windowPeak)

            val decodedMs = peaks.size * SilenceLogic.WINDOW_MS
            Waveform(
                windowPeaks = peaks.toIntArray(),
                windowMs = SilenceLogic.WINDOW_MS,
                decodedMs = decodedMs,
                complete = sawOutputEos && !hitCap
            )
        } catch (_: Exception) {
            null // أي فشل = لا موجة — المحرر يكمل بمنزلقاته كما في v0.19.0
        } catch (_: OutOfMemoryError) {
            null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }
}

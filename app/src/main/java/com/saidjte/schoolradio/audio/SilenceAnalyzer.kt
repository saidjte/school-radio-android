package com.saidjte.schoolradio.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.saidjte.schoolradio.core.util.SilenceLogic

/**
 * v0.18.0 — محلّل الفراغ الأول: يفك تشفير بداية ملف صوتي ويقيس كم ثانية صمت
 * تسبق أول صوت حقيقي. النتيجة تُخزَّن في قاعدة المكتبة (trim_start_ms) ويطبقها
 * مشغل الوسائط عبر ClippingConfiguration — فيبدأ المقطع من أول نغمة فوراً.
 *
 * لماذا MediaCodec لا MediaMetadataRetriever؟
 * الـRetriever لا يعطي معلومة الصمت إطلاقاً — لا بديل عن فك التشفير الفعلي
 * للعينات (PCM) وقياس سعتها. الفك تشفير يتوقف عند أول صوت مسموع، لذا هو
 * سريع جداً عملياً (جزء من الثانية لأغلب الجينلات والمقاطع).
 *
 * السلامة أولاً: أي استثناء (ملف مشفر/تالف/صيغة عجيبة) يعيد 0 — التحليل
 * تحسين وليس شرطاً؛ المقطع يُشغَّل عادياً من بدايته عند فشل القياس.
 */
object SilenceAnalyzer {

    /** كم محاولة انتظار متتالية (10ms لكل واحدة) قبل الاستسلام بسلام */
    private const val MAX_TRY_AGAIN_STREAK = 200

    /**
     * قياس الفراغ الأول لملف صوتي.
     * @param uriStr مسار content:// أو file:// للمقطع
     * @return عدد الميلي ثانية المرشحة للقص (0 = لا فراغ أو فشل التحليل)
     */
    fun leadingSilenceMs(context: Context, uriStr: String): Long {
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
            if (audioTrack < 0 || format == null) return 0L
            extractor.selectTrack(audioTrack)
            extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            val mime = format.getString(MediaFormat.KEY_MIME)!!
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val samplesPerWindow =
                (sampleRate * SilenceLogic.WINDOW_MS / 1000L).toInt().coerceAtLeast(1)
            val maxWindows = (SilenceLogic.MAX_PROBE_MS / SilenceLogic.WINDOW_MS).toInt()

            val peaks = ArrayList<Int>(maxWindows)
            val info = MediaCodec.BufferInfo()
            var windowPeak = 0
            var samplesInWindow = 0
            var sawInputEos = false
            var sawOutputEos = false
            var tryAgainStreak = 0

            loop@ while (!sawOutputEos && peaks.size < maxWindows) {
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
                                    if (peaks.size >= maxWindows) break@loop
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
            SilenceLogic.leadingSilenceMs(peaks)
        } catch (_: Exception) {
            0L // أي فشل = لا قص — التشغيل العادي من البداية
        } catch (_: OutOfMemoryError) {
            0L
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }
}

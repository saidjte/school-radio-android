package com.saidjte.schoolradio.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.Process
import com.saidjte.schoolradio.core.util.BroadcastCaptureLogic
import com.saidjte.schoolradio.core.util.MicFrameRing

/**
 * مصدر التقاط صوت التطبيق لتسجيل البث الكامل (v0.49.0) — أندرويد 10+ فقط.
 *
 * **لماذا هذا هو الجذر الصحيح وليس رقعة؟** المطلوب تسجيل «ما يسمعه الجمهور» —
 * وهذا يتكوّن في نظام الصوت نفسه بعد كل الفيدرات والخفض التلقائي وجينل المايك
 * ومعالجة المذيع. أي محاولة لجمع المصادر يدوياً في التطبيق (فيدرات مكرّرة
 * في مسجل ثانٍ) تصنع حالة منسوخة حتمياً تنحرف عن الصوت المسموع.
 * AudioPlaybackCapture يعيد المزيج النهائي الفعلي لمخرجات التطبيق.
 *
 * **حالة المايك**: التقاط الإخراج لا يلمس مسار المايك عندما يعمل بـ
 * USAGE_VOICE_COMMUNICATION (مكبر الجهاز) — فيُدمج تاب PCM المايك الموجود
 * أصلاً (onPcm بعد الكسب والتلاشي) حسابياً. عندما يعمل المايك بـ USAGE_MEDIA
 * (سماعة/بلوتوث/تلقائي) فهو داخل الالتقاط أصلاً فلا دمج — وإلا تضاعف.
 *
 * دفعات 10ms مونو 48kHz تخرج من [start] إلى الكاتب مباشرة — نفس عقد
 * PcmAudioWriter الحالي بلا أي تعديل عليه.
 */
class ProjectionCaptureSource(private val projection: MediaProjection) {

    /**
     * هل يُدمج تاب المايك في المزيج؟ يقرره RecordingManager عند البدء حسب
     * usage مسار المايك الفعلي (VOICE_COMMUNICATION = دمج، MEDIA = لا).
     */
    @Volatile var micTapEnabled: Boolean = false

    private val micRing = MicFrameRing(MicrophoneController.SAMPLE_RATE / 10) // سقف 100ms
    private var record: AudioRecord? = null
    private var thread: Thread? = null
    @Volatile private var running = false

    val isActive: Boolean get() = running

    /**
     * بدء خيط الالتقاط — الدفعات المدموجة تخرج عبر [onPcm].
     * @return null عند النجاح أو رسالة خطأ عربية جاهزة للعرض
     */
    fun start(onPcm: (ShortArray, Int) -> Unit): String? {
        val captureConfig = AudioPlaybackCaptureConfiguration.Builder(projection)
            // ما يشغّله التطبيق كله مسارات وسائط — الأنماط الثلاثة تغطيها حصراً
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()

        val minBuf = AudioRecord.getMinBufferSize(
            MicrophoneController.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) return "معاملات التقاط الصوت غير مدعومة على هذا الجهاز"

        val rec = try {
            AudioRecord.Builder()
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(MicrophoneController.SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                        .build()
                )
                // أرضية 100ms — نفس قاعدة v0.45.0 في المايك: فيض الالتقاط لا يُعوَّض
                .setBufferSizeInBytes(
                    maxOf(minBuf * 4, MicrophoneController.SAMPLE_RATE * 2 * 2 / 10)
                )
                .setAudioPlaybackCaptureConfig(captureConfig)
                .build()
        } catch (e: Exception) {
            return "تعذر بدء التقاط صوت التطبيق: ${e.message ?: "خطأ غير معروف"}"
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return "تعذر تهيئة التقاط الصوت على هذا الجهاز"
        }
        record = rec
        micRing.clear()
        running = true

        val stereoChunk = ShortArray(MicrophoneController.SAMPLE_RATE / 100 * 2) // 480 إطاراً × 2
        val micChunk = ShortArray(MicrophoneController.SAMPLE_RATE / 100)
        val monoChunk = ShortArray(MicrophoneController.SAMPLE_RATE / 100)

        thread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            try {
                rec.startRecording()
            } catch (e: Exception) {
                running = false
            }
            while (running) {
                val n = try {
                    rec.read(stereoChunk, 0, stereoChunk.size)
                } catch (_: Exception) {
                    break
                }
                if (n <= 0) continue
                val frames = n / 2
                if (frames <= 0 || frames > monoChunk.size) continue
                BroadcastCaptureLogic.downmixInto(stereoChunk, monoChunk, frames)

                // دمج المايك فقط حين يكون خارج الالتقاط — وإلا تضاعف صوته
                if (micTapEnabled) {
                    val got = micRing.pull(micChunk, frames)
                    if (got > 0) {
                        for (i in 0 until got) {
                            monoChunk[i] = BroadcastCaptureLogic.combineToShort(monoChunk[i], micChunk[i])
                        }
                    }
                }
                onPcm(monoChunk, frames)
            }
            try { rec.stop() } catch (_: Exception) {}
            rec.release()
        }, "broadcast-capture").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
        return null
    }

    /**
     * نقطة تغذية تاب المايك — يستدعيها RecordingManager من onPcm الحالي
     * (خيط البث). الخزان ينسخ داخلياً فلا سباق على مخزن حلقة المايك المعاد استخدامه.
     */
    fun pushMicPcm(pcm: ShortArray, n: Int) {
        if (!micTapEnabled || !running) return
        micRing.push(pcm, n)
    }

    /** إيقاف الالتقاط وتحرير المسار — آمن للاستدعاء المتكرر ومن أي خيط */
    fun stop() {
        running = false
        thread?.join(800)
        thread = null
        record = null
        micRing.clear()
    }
}

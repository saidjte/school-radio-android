package com.saidjte.schoolradio.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioDeviceCallback
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlin.math.sqrt

/**
 * متحكم البث المباشر (اضغط للتحدث): ميكروفون ← مكبرات بأقل تأخير عملي بلغة Kotlin.
 *
 * البنية: AudioRecord (التقاط) → حلقة خيط مخصص → AudioTrack (إخراج MODE_STREAM).
 * - معدل العينة 48kHz (النطاق الأصلي لأغلب الأجهزة — يمنع إعادة المعاينة وكلفة تأخيرها).
 * - مخزن = 2× الحد الأدنى: توازن بين التأخير (~40-80ms عملياً) والاستقرار.
 * - مصدر VOICE_COMMUNICATION يفعّل معالجة الصدى (AEC) ضد صفير التغذية الراجعة
 *   عندما يكون السماعة والميكروفون في نفس الجهاز.
 *
 * الخفض التلقائي (Auto-ducking): عند بدء البث يُخفَّض مستوى المشغل عبر رد onDuck
 * (يتصل PlaybackConnection.setVolume) ويُعاد إلى قيمته السابقة عند الإنهاء.
 *
 * فصل معماري: هذا المتحكم مستقل كلياً عن PlaybackService — لا يتشاركان حالة.
 */
class MicrophoneController(
    private val context: Context,
    private val routing: AudioRoutingManager,
    private val onDuck: (duck: Boolean) -> Unit,
    private val onLevel: (level: Float) -> Unit = {},
    private val onStateChanged: (isLive: Boolean, error: String?) -> Unit = { _, _ -> }
) {
    companion object {
        const val SAMPLE_RATE = 48_000
        const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }

    @Volatile private var running = false
    private var loopThread: Thread? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var deviceLossCallback: AudioDeviceCallback? = null
    private var lastRoute: AudioRoutingManager.Route = AudioRoutingManager.Route.SPEAKER

    val isLive: Boolean get() = running

    /** هل الصلاحية ممنوحة؟ */
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * بدء البث المباشر.
     * @param route مسار الإخراج (يُقترح تلقائياً عبر routing.suggestRoute())
     * @return null عند النجاح، أو رسالة خطأ عربية واضحة
     */
    fun start(route: AudioRoutingManager.Route): String? {
        if (running) return "البث جارٍ بالفعل"
        if (!hasPermission()) return "صلاحية الميكروفون غير ممنوحة — اسمح بها أولاً"

        // فحص توفر الميكروفون (قد يحتجزه تطبيق آخر مثل مكالمة أو مسجل)
        if (!routing.isMicrophoneFree(SAMPLE_RATE, ENCODING, CHANNEL_IN)) {
            return "الميكروفون مشغول بتطبيق آخر أو غير متاح على هذا الجهاز"
        }

        val usage = routing.applyRoute(route)
        lastRoute = route

        val minRecordBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING)
        if (minRecordBuffer <= 0) {
            routing.restoreNormal()
            return "معاملات الصوت غير مدعومة على هذا الجهاز"
        }
        val bufferSize = minRecordBuffer * 2

        audioRecord = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION, // AEC ضد الصفير
                SAMPLE_RATE, CHANNEL_IN, ENCODING, bufferSize
            )
        } catch (e: Exception) {
            routing.restoreNormal()
            return "تعذر فتح الميكروفون: ${e.message ?: "خطأ غير معروف"}"
        }.also { rec ->
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                rec.release()
                audioRecord = null
                routing.restoreNormal()
                return "تعذر تهيئة الميكروفون على هذا الجهاز"
            }
        }

        audioTrack = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(usage)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(ENCODING)
                        .setChannelMask(CHANNEL_OUT)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(bufferSize)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
        } catch (e: Exception) {
            releaseAll()
            routing.restoreNormal()
            return "تعذر فتح مسار الإخراج: ${e.message ?: "خطأ غير معروف"}"
        }.also { tr ->
            if (tr.state != AudioTrack.STATE_INITIALIZED) {
                releaseAll()
                routing.restoreNormal()
                return "تعذر تهيئة مسار الإخراج على هذا الجهاز"
            }
        }

        running = true
        onDuck(true) // خفض الموسيقى فور بدء الإعلان
        audioTrack?.play()
        audioRecord?.startRecording()

        // مراقبة انقطاع جهاز الإخراج أثناء البث (فك السماعة مثلاً)
        deviceLossCallback = routing.registerDeviceLossListener {
            if (running) stop("انقطع جهاز الإخراج فتوقف البث")
        }

        loopThread = Thread({ passthroughLoop() }, "ptt-passthrough").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
        onStateChanged(true, null)
        return null
    }

    /** حلقة التمرير: قراءة ← كتابة فورية ← حساب مستوى RMS للمؤشر البصري */
    private fun passthroughLoop() {
        // دفعة 10ms = 480 عينة عند 48kHz — أصغر دفعة عملية لتأخير منخفض
        val chunk = ShortArray(SAMPLE_RATE / 100)
        while (running) {
            val n = try {
                audioRecord?.read(chunk, 0, chunk.size) ?: break
            } catch (_: Exception) { break }
            if (n > 0) {
                audioTrack?.write(chunk, 0, n)
                onLevel(rms(chunk, n))
            }
        }
    }

    private fun rms(x: ShortArray, n: Int): Float {
        var acc = 0L
        for (i in 0 until n) acc += x[i].toLong() * x[i]
        return (sqrt(acc.toDouble() / n) / Short.MAX_VALUE).toFloat()
    }

    /**
     * إنهاء البث وإعادة كل شيء إلى وضعه الطبيعي.
     * آمن للاستدعاء من أي خيط ولأي عدد من المرات.
     */
    fun stop(reason: String? = null) {
        if (!running && audioRecord == null) return
        running = false
        loopThread?.join(400)
        loopThread = null
        releaseAll()
        routing.restoreNormal()
        deviceLossCallback?.let { try { routing.unregister(it) } catch (_: Exception) {} }
        deviceLossCallback = null
        onDuck(false) // إعادة مستوى الموسيقى إلى قيمته السابقة
        onStateChanged(false, reason)
    }

    private fun releaseAll() {
        audioRecord?.run {
            try { stop() } catch (_: Exception) {}
            release()
        }
        audioRecord = null
        audioTrack?.run {
            try { stop() } catch (_: Exception) {}
            release()
        }
        audioTrack = null
    }
}

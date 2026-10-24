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
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors
import kotlin.math.sqrt

/**
 * مراحل البث المباشر — آلة حالات صريحة بدل منطقية true/false:
 * IDLE (خامل) ← STARTING (يجري فتح الميكروفون والإخراج) ← LIVE (يبثّ)
 * ← STOPPING (يجري التنعيم والإغلاق) ← IDLE.
 * الواجهة تستخدمها لعرض مؤشر انتقال ناعم بدل تجميد الزر ثم انطلاق مفاجئ.
 */
enum class MicPhase { IDLE, STARTING, LIVE, STOPPING }

/**
 * متحكم البث المباشر (اضغط للتحدث): ميكروفون ← مكبرات بأقل تأخير عملي بلغة Kotlin.
 *
 * v0.8.0 — سلاسة كاملة:
 *  - كل الأعمال الثقيلة (فتح AudioRecord/AudioTrack، فحص المخازن، الإغلاق والانتظار)
 *    تُنفَّذ على خيط تحكم مخصص مُتسلسل — صفر حجب للخيط الرئيسي مهما كان الجهاز بطيئاً.
 *  - آلة حالات [MicPhase] مكشوفة كـ StateFlow: الزر يعرض «جارٍ البدء…» لحظة الضغط
 *    بدل الصمت المريب، و«جارٍ الإيقاف…» لحظة التحرير.
 *  - تلاشي صوتي (fade-in 60ms / fade-out 80ms) على دفعات PCM — لا نقرات ولا
 *    فرقعة عند فتح أو قطع الميكروفون، والتسجيل المتزامن يأخذ الصوت المتلاشي نفسه.
 *  - ضغطة خاطفة سريعة (إيقاف طُلب أثناء بدء البدء) تُعالج بأمان: البدء يُكمل
 *    تهيئته ثم يغلق فوراً بلا تسريب موارد.
 *
 * البنية: AudioRecord (التقاط) → حلقة خيط مخصص → AudioTrack (إخراج MODE_STREAM).
 * - معدل العينة 48kHz (النطاق الأصلي لأغلب الأجهزة — يمنع إعادة المعاينة وكلفة تأخيرها).
 * - مصدر VOICE_COMMUNICATION يفعّل معالجة الصدى (AEC) ضد صفير التغذية الراجعة.
 *
 * الخفض التلقائي (Auto-ducking): عند بدء البث يُخفَّض مستوى المشغل عبر رد onDuck
 * (يتصل PlaybackConnection.setVolume) ويُعاد إلى قيمته السابقة عند الإنهاء.
 */
class MicrophoneController(
    private val context: Context,
    private val routing: AudioRoutingManager,
    private val onDuck: (duck: Boolean) -> Unit,
    private val onLevel: (level: Float) -> Unit = {},
    /** ★ v0.3.0: نقطة تفريغ دفعات PCM — بوابتها يعمل التسجيل المتزامن مع البث (خط أنابيب واحد) */
    private val onPcm: (pcm: ShortArray, count: Int) -> Unit = { _, _ -> },
    private val onStateChanged: (isLive: Boolean, error: String?) -> Unit = { _, _ -> }
) {
    companion object {
        const val SAMPLE_RATE = 48_000
        const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

        // مدة التلاشي بالدفعات (كل دفعة 10ms): 6 دفعات = 60ms دخول، 8 = 80ms خروج
        private const val FADE_IN_CHUNKS = 6
        private const val FADE_OUT_CHUNKS = 8
    }

    // ===== آلة الحالات المشتركة مع الواجهة =====
    private val _phase = MutableStateFlow(MicPhase.IDLE)
    val phase: StateFlow<MicPhase> = _phase.asStateFlow()

    /** هل البث يعمل فعلاً (إخراج صوت جارٍ)؟ — نفس الدلالة السابقة للتوافق */
    val isLive: Boolean get() = _phase.value == MicPhase.LIVE

    @Volatile private var running = false
    @Volatile private var stopRequested = false
    private var loopThread: Thread? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var deviceLossCallback: AudioDeviceCallback? = null
    private var lastRoute: AudioRoutingManager.Route = AudioRoutingManager.Route.SPEAKER

    /** خيط تحكم مخصص ومتسلسل: عمليات البدء/الإيقاف لا تتقاطع أبداً (FIFO) */
    private val controlExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "mic-control").apply { priority = Thread.MAX_PRIORITY }
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun notifyState(isLiveNow: Boolean, error: String?) {
        // ★ يجب أن يصل رد الحالة إلى الخيط الرئيسي: المستمعون يمسّون ExoPlayer
        // (stopPreview في الحَكَم) وهو يشترط الوصول من خيط الإنشاء نفسه
        mainHandler.post { onStateChanged(isLiveNow, error) }
    }

    private fun notifyResult(result: String?) {
        // رد الواجهة (snackbar) يجب أن يصل إلى الخيط الرئيسي
        mainHandler.post { onResultListener?.invoke(result) }
    }

    /** مستمع نتيجة البدء — يضبطه المستدعي قبل start() */
    @Volatile private var onResultListener: ((String?) -> Unit)? = null

    /** هل الصلاحية ممنوحة؟ */
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * بدء البث المباشر — غير متزامن: يعود فوراً وتُدار النتيجة عبر [onResult]
     * (null = نجاح، وإلا رسالة عربية) وعبر [phase] لعرض الانتقال في الواجهة.
     */
    fun start(route: AudioRoutingManager.Route, onResult: (String?) -> Unit = {}) {
        onResultListener = onResult
        if (_phase.value == MicPhase.LIVE || _phase.value == MicPhase.STARTING) {
            notifyResult("البث جارٍ بالفعل")
            return
        }
        stopRequested = false
        _phase.value = MicPhase.STARTING // استجابة فورية للواجهة قبل أي عمل ثقيل
        controlExecutor.execute { startNow(route) }
    }

    /** جسم البدء الفعلي — يعمل على خيط التحكم المخصص فقط */
    private fun startNow(route: AudioRoutingManager.Route) {
        try {
            if (!hasPermission()) {
                failStart("صلاحية الميكروفون غير ممنوحة — اسمح بها أولاً")
                return
            }
            // فحص توفر الميكروفون (قد يحتجزه تطبيق آخر مثل مكالمة أو مسجل)
            if (!routing.isMicrophoneFree(SAMPLE_RATE, ENCODING, CHANNEL_IN)) {
                failStart("الميكروفون مشغول بتطبيق آخر أو غير متاح على هذا الجهاز")
                return
            }

            val usage = routing.applyRoute(route)
            lastRoute = route

            val minRecordBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING)
            if (minRecordBuffer <= 0) {
                routing.restoreNormal()
                failStart("معاملات الصوت غير مدعومة على هذا الجهاز")
                return
            }
            val bufferSize = minRecordBuffer * 2

            val rec = try {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION, // AEC ضد الصفير
                    SAMPLE_RATE, CHANNEL_IN, ENCODING, bufferSize
                )
            } catch (e: Exception) {
                routing.restoreNormal()
                failStart("تعذر فتح الميكروفون: ${e.message ?: "خطأ غير معروف"}")
                return
            }
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                rec.release()
                routing.restoreNormal()
                failStart("تعذر تهيئة الميكروفون على هذا الجهاز")
                return
            }
            audioRecord = rec

            val track = try {
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
                failStart("تعذر فتح مسار الإخراج: ${e.message ?: "خطأ غير معروف"}")
                return
            }
            if (track.state != AudioTrack.STATE_INITIALIZED) {
                releaseAll()
                routing.restoreNormal()
                failStart("تعذر تهيئة مسار الإخراج على هذا الجهاز")
                return
            }
            audioTrack = track

            // ضغطة خاطفة: طُلب الإيقاف أثناء فتح الأجهزة؟ أغلق فوراً بلا دخول للهواء
            if (stopRequested) {
                releaseAll()
                routing.restoreNormal()
                finishStop("أُلغي البدء")
                return
            }

            running = true
            onDuck(true) // خفض الموسيقى فور بدء الإعلان
            track.play()
            rec.startRecording()

            // مراقبة انقطاع جهاز الإخراج أثناء البث (فك السماعة مثلاً)
            deviceLossCallback = routing.registerDeviceLossListener {
                if (running) stop("انقطع جهاز الإخراج فتوقف البث")
            }

            loopThread = Thread({ passthroughLoop() }, "ptt-passthrough").apply {
                priority = Thread.MAX_PRIORITY
                start()
            }

            _phase.value = MicPhase.LIVE
            notifyState(true, null)
            notifyResult(null)
        } catch (e: Exception) {
            // أي مفاجأة غير متوقعة أثناء التهيئة: تنظيف كامل ورسالة واضحة
            try { releaseAll(); routing.restoreNormal() } catch (_: Exception) {}
            failStart("تعذر بدء البث: ${e.message ?: "خطأ غير معروف"}")
        }
    }

    /** فشل البدء: إعادة الحالة إلى الخمول ونشر رسالة الخطأ */
    private fun failStart(message: String) {
        _phase.value = MicPhase.IDLE
        notifyState(false, message)
        notifyResult(message)
    }

    /** حلقة التمرير: قراءة ← تلاشي ← كتابة فورية ← تفريغ للتسجيل ← حساب مستوى RMS */
    private fun passthroughLoop() {
        // دفعة 10ms = 480 عينة عند 48kHz — أصغر دفعة عملية لتأخير منخفض
        val chunk = ShortArray(SAMPLE_RATE / 100)
        val fadeInStep = 1f / FADE_IN_CHUNKS
        val fadeOutStep = 1f / FADE_OUT_CHUNKS
        var gain = 0f          // يبدأ من الصفر: دخول ناعم بلا فرقعة
        var fadingOut = false
        while (running) {
            val n = try {
                audioRecord?.read(chunk, 0, chunk.size) ?: break
            } catch (_: Exception) { break }
            if (n > 0) {
                // طُلب الإيقاف؟ ننحّل الصوت تدريجياً ثم نخرج — لا قطع مفاجئ
                if (stopRequested) fadingOut = true
                if (gain < 1f) gain = (gain + fadeInStep).coerceAtMost(1f)
                if (fadingOut) gain = (gain - fadeOutStep).coerceAtLeast(0f)

                // تطبيق عامل التلاشي على الدفعة (نفس الدفعة تُكتب وتُسجَّل — اتساق تام)
                for (i in 0 until n) chunk[i] = (chunk[i] * gain).toInt().toShort()

                if (n > 0 && gain > 0f) {
                    audioTrack?.write(chunk, 0, n)
                    // نفس الدفعة تذهب للتسجيل المتزامن (كلفة استدعاء واحد — لا تأخير عملي)
                    onPcm(chunk, n)
                    onLevel(rms(chunk, n))
                }
                if (fadingOut && gain <= 0f) break
            }
        }
    }

    private fun rms(x: ShortArray, n: Int): Float {
        var acc = 0L
        for (i in 0 until n) acc += x[i].toLong() * x[i]
        return (sqrt(acc.toDouble() / n) / Short.MAX_VALUE).toFloat()
    }

    /**
     * إنهاء البث — غير متزامن: يعود فوراً، ينحل الصوت تدريجياً (fade-out)
     * على خيط التحكم ثم يحرر الموارد. آمن للاستدعاء من أي خيط ولأي عدد من المرات.
     */
    fun stop(reason: String? = null) {
        if (_phase.value == MicPhase.IDLE || _phase.value == MicPhase.STOPPING) return
        stopRequested = true
        _phase.value = MicPhase.STOPPING // الواجهة تعرض «جارٍ الإيقاف…» فوراً
        controlExecutor.execute { stopNow(reason) }
    }

    /** جسم الإيقاف الفعلي — يعمل على خيط التحكم بعد انحلال حلقة التمرير */
    private fun stopNow(reason: String?) {
        try {
            running = false
            // حلقة التمرير تنحل الصوت وتخرج بنفسها عند رؤية stopRequested؛
            // ننتظرها بوقت سخي ثم نجبر إن علقت (قراءة معطوبة مثلاً)
            loopThread?.join(1200)
            if (loopThread?.isAlive == true) loopThread?.interrupt()
            loopThread = null
            releaseAll()
            routing.restoreNormal()
            deviceLossCallback?.let { try { routing.unregister(it) } catch (_: Exception) {} }
            deviceLossCallback = null
            stopRequested = false
            onDuck(false) // إعادة مستوى الموسيقى إلى قيمته السابقة
            _phase.value = MicPhase.IDLE
            notifyState(false, reason)
        } catch (e: Exception) {
            try { releaseAll() } catch (_: Exception) {}
            _phase.value = MicPhase.IDLE
            notifyState(false, reason ?: "خطأ أثناء الإيقاف: ${e.message ?: ""}")
        }
    }

    /** نهاية إيقاف ناجحة (مسار إلغاء البدء) — بلا رسالة خطأ */
    private fun finishStop(reason: String?) {
        onDuck(false)
        _phase.value = MicPhase.IDLE
        notifyState(false, reason)
        notifyResult(null)
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

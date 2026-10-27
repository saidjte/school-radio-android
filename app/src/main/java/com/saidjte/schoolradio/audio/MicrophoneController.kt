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
import android.os.Process
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
 * v0.13.0 — قضاء على التقطيع: خيطا التقاط/إخراج منفصلان على PcmRingBuffer (200ms)،
 * مخزن إخراج ~120ms، أولوية URGENT_AUDIO، ومستوى واجهة مخنّق 25Hz.
 *
 * البنية: AudioRecord (التقاط) ← PcmRingBuffer ← AudioTrack (إخراج MODE_STREAM).
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
    private val onStateChanged: (isLive: Boolean, error: String?) -> Unit = { _, _ -> },
    /** ★ v0.11.0: مصدر تكوين معالجة المذيع الحية (AGC/حجز الضجيج/إلغاء الصدى) —
     *  يُقرأ لحظة بدء البث فتُطبق التغييرات من الاستوديو على البث التالي مباشرة */
    private val processingProvider: () -> MicProcessing = { MicProcessing() }
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

    /**
     * v0.9.0 — كسب الميكروفون (فيدر قناة الميك في الميكسر):
     * 0.0 = كتم، 1.0 = طبيعي، حتى 2.0 = تضخيم لصوت خافت.
     * يُطبّق على دفعات PCM في حلقة التمرير مع منع التشبع (لا لفّ موجة).
     */
    private val _micGain = MutableStateFlow(1f)
    val micGain: StateFlow<Float> = _micGain.asStateFlow()

    @Volatile private var gainFactor = 1f

    fun setMicGain(value: Float) {
        val v = value.coerceIn(0f, 2f)
        gainFactor = v
        _micGain.value = v
    }

    /** هل البث يعمل فعلاً (إخراج صوت جارٍ)؟ — نفس الدلالة السابقة للتوافق */
    val isLive: Boolean get() = _phase.value == MicPhase.LIVE

    @Volatile private var running = false
    @Volatile private var stopRequested = false
    private var captureThread: Thread? = null
    private var playbackThread: Thread? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var deviceLossCallback: AudioDeviceCallback? = null
    private var lastRoute: AudioRoutingManager.Route = AudioRoutingManager.Route.SPEAKER

    /** v0.13.0: المخزن الحلقي بين خيطي الالتقاط والإخراج — يُنشأ عند كل بث */
    private var ring: PcmRingBuffer? = null

    /** v0.11.0: سلسلة معالجة صوت المذيع — دورة حياة عُقدة البث نفسها */
    private val processingChain = MicProcessingChain()

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
            val captureBufferSize = minRecordBuffer * 2
            // v0.13.0: مخزن إخراج ~120ms — الحصن ضد underrun (كان عند الحد الأدنى فأسمع التقطيع)
            val trackBufferSize = maxOf(captureBufferSize, (SAMPLE_RATE * 2 * 0.12f).toInt())
            ring = PcmRingBuffer(SAMPLE_RATE / 5) // 200ms = عشرون دفعة 10ms

            // v0.44.0: المايك الخارجي عبر USB-C — أندرويد يوجّه الالتقاط افتراضياً
            // إلى المدمج وحده فلا صوت من الخارجي إطلاقاً (البلاغ). عند وجود مدخل
            // USB نلتقط منه: مصدر MIC الخام (أصدق التزاماً بالاختيار الصريح عبر
            // الأجهزة) + تفضيل الجهاز setPreferredDevice. بلا USB: السلوك القائم
            // (VOICE_COMMUNICATION بمعالجة الصدى ضد الصفير) لا يُمس.
            val usbMic = usbInputDevice(
                context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            )
            val rec = try {
                AudioRecord(
                    if (usbMic != null) MediaRecorder.AudioSource.MIC
                    else MediaRecorder.AudioSource.VOICE_COMMUNICATION, // AEC ضد الصفير
                    SAMPLE_RATE, CHANNEL_IN, ENCODING, captureBufferSize
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
            usbMic?.let { rec.setPreferredDevice(it) }
            audioRecord = rec

            // v0.11.0: ربط سلسلة معالجة المذيع بجلسة التسجيل قبل بدء الالتقاط —
            // AGC/حجز الضجيج/إلغاء الصدى تُطبق على الصوت قبل التلاشي والإخراج والتسجيل
            processingChain.attach(rec.audioSessionId, processingProvider())

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
                    .setBufferSizeInBytes(trackBufferSize)
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

            // v0.13.0: خيطا الالتقاط والإخراج منفصلان — يلتقيان على المخزن الحلقي،
            // وأي اختناق لحظي يمتصه المخزن بدل أن يصل أذن المستمع تقطيعاً
            captureThread = Thread({ captureLoop() }, "mic-capture").apply {
                priority = Thread.MAX_PRIORITY
                start()
            }
            playbackThread = Thread({ playbackLoop() }, "mic-playback").apply {
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

    /**
     * خيط الالتقاط: قراءة من الميكروفون فقط ثم كتابة في المخزن الحلقي.
     * أقل كود ممكن هنا = أقل فرص توقف وأدق التقاط لأزمنة الميكروفون.
     *
     * v0.15.0 — مناعة كاملة ضد الانهيار (إصلاح إنهاء البث):
     * أي استثناء يهرب من هذا الخيط كان يقتل العملية كلها (معالج الاستثناء الافتراضي
     * للخيوط). الآن لا يهرب شيء — الخيط يخرج بهدوء وآلية الإيقاف تكمل عملها.
     */
    private fun captureLoop() {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val chunk = ShortArray(SAMPLE_RATE / 100) // دفعة 10ms
            while (running) {
                val n = try {
                    audioRecord?.read(chunk, 0, chunk.size) ?: break
                } catch (_: Exception) { break }
                if (n > 0) {
                    ring?.write(chunk, 0, n)
                } else if (n < 0) {
                    // خطأ قراءة عطل (ERROR_INVALID_OPERATION…): أوقف البث بأمان
                    stop("تعذر الاستمرار في قراءة الميكروفون")
                    break
                }
            }
        } catch (t: Throwable) {
            // v0.15.0: لا يجوز أن يموت خيط صوتي باستثناء غير معالج — النظام يغلق التطبيق
            if (running) stop("توقف غير متوقع في خيط الالتقاط")
        }
    }

    /**
     * خيط الإخراج: استهلاك المخزن الحلقي ← تلاشٍ وكسب ← AudioTrack
     * ← تفريغ للتسجيل المتزامن ← حساب المستوى (مخنّق 25Hz للواجهة).
     * يعمل حتى بعد رفع running لإتمام نحول الإيقاف (fade-out) ثم يخرج.
     */
    private fun playbackLoop() {
        // v0.15.0: كل جسم الخيط داخل درع try/catch — أي استثناء يهرب من خيط صوتي
        // كان يقتل العملية كلها (سبب انهيار التطبيق عند إنهاء البث)
        try {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val chunk = ShortArray(SAMPLE_RATE / 100)
        val fadeInStep = 1f / FADE_IN_CHUNKS
        val fadeOutStep = 1f / FADE_OUT_CHUNKS
        var gain = 0f          // يبدأ من الصفر: دخول ناعم بلا فرقعة
        var fadingOut = false
        var lastLevelAt = 0L
        while (running || fadingOut) {
            // انتظار حتى 100ms — يمكّن فحص running بين الدفعات إن خلا المخزن
            val n = ring?.read(chunk, 0, chunk.size, 100) ?: 0
            if (n <= 0) continue
            // طُلب الإيقاف؟ ننحّل الصوت تدريجياً ثم نخرج — لا قطع مفاجئ
            if (stopRequested) fadingOut = true
            if (gain < 1f) gain = (gain + fadeInStep).coerceAtMost(1f)
            if (fadingOut) gain = (gain - fadeOutStep).coerceAtLeast(0f)

            // كسب الميكروفون (فيدر الميكسر) × تلاشي الدخول/الخروج
            // مع منع التشبع: قيمة كسب > 1 بدون تحديد النطاق تلفّ الموجة فتصدر تشويه رقمي
            val g = gain * gainFactor
            for (i in 0 until n) {
                val s = chunk[i] * g
                chunk[i] = when {
                    s > Short.MAX_VALUE -> Short.MAX_VALUE
                    s < Short.MIN_VALUE -> Short.MIN_VALUE
                    else -> s.toInt().toShort()
                }
            }

            if (g > 0f) {
                try {
                    audioTrack?.write(chunk, 0, n)
                    // نفس الدفعة تذهب للتسجيل المتزامن (كلفة استدعاء واحد — لا تأخير عملي)
                    onPcm(chunk, n)
                } catch (t: Throwable) {
                    // المسار الإخراجي تلف (حُرر AudioTrack مثلاً) — نوقف البث بلا انهيار
                    stop("توقف الإخراج الصوتي بشكل غير متوقع")
                    break
                }
            }
            // المستوى للواجهة (الموجة الحية): 25Hz كافية بصرياً وتريح الخيط الرئيسي
            val now = System.currentTimeMillis()
            if (now - lastLevelAt >= 40) {
                lastLevelAt = now
                onLevel(rms(chunk, n))
            }
            if (fadingOut && gain <= 0f) break
        }
        } catch (t: Throwable) {
            // v0.15.0: حصن أخير — لا خيط صوتي يموت باستثناء حيّ مهما كانت الظروف
            if (running) stop("توقف غير متوقع في خيط الإخراج")
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
        // v0.15.0: استيقِظ الخيوط المنتظرة فوراً — يختصر زمن الإيقاف وينهي
        // الحلقتين طبيعياً دون اللجوء إلى المقاطعة القاسية في الغالبية الساحقة
        ring?.wakeUp()
        controlExecutor.execute { stopNow(reason) }
    }

    /** جسم الإيقاف الفعلي — يعمل على خيط التحكم بعد انحلال حلقة التمرير */
    private fun stopNow(reason: String?) {
        try {
            running = false
            ring?.wakeUp()
            // خيط الإخراج ينحل الصوت ويخرج بنفسه عند رؤية stopRequested (أقل من 100ms)؛
            // خيط الالتقاط يخرج فور رفع running. ننتظر بوقت سخي ثم نجبر إن علقت.
            playbackThread?.join(1200)
            if (playbackThread?.isAlive == true) {
                // v0.15.0: المقاطعة الآن آمنة — المخزن يلتقط InterruptedException
                // وخيوط الصوت كلها داخل درع try/catch فلا انهيار مهما حدث
                playbackThread?.interrupt()
                playbackThread?.join(400)
            }
            playbackThread = null
            captureThread?.join(1200)
            if (captureThread?.isAlive == true) {
                captureThread?.interrupt()
                captureThread?.join(400)
            }
            captureThread = null
            ring?.clear()
            ring = null
            releaseAll()
            routing.restoreNormal()
            deviceLossCallback?.let { try { routing.unregister(it) } catch (_: Exception) {} }
            deviceLossCallback = null
            stopRequested = false
            onDuck(false) // إعادة مستوى الموسيقى إلى قيمته السابقة
            _phase.value = MicPhase.IDLE
            notifyState(false, reason)
        } catch (t: Throwable) {
            // v0.15.0: الإيقاف مسؤولية مقدسة — لا يجوز أن يفشل بانهيار أبداً
            // (كان يكفي استثناء واحد غير معالج ليغلق التطبيق عند ضغطة الإنهاء)
            try { releaseAll() } catch (_: Throwable) {}
            try { routing.restoreNormal() } catch (_: Throwable) {}
            _phase.value = MicPhase.IDLE
            notifyState(false, reason ?: "خطأ أثناء الإيقاف: ${t.message ?: ""}")
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
        processingChain.release()
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

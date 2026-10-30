package com.saidjte.schoolradio.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import com.saidjte.schoolradio.core.util.CrashGuard
import com.saidjte.schoolradio.core.util.RecordingCodecLogic
import com.saidjte.schoolradio.core.util.RecordingLogic
import com.saidjte.schoolradio.core.util.WaveformLogic
import com.saidjte.schoolradio.data.local.entity.RecordingEntity
import com.saidjte.schoolradio.data.local.entity.RecordingSource
import com.saidjte.schoolradio.data.local.entity.UserEntity
import com.saidjte.schoolradio.data.repository.RecordingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * مدير التسجيلات (v0.57.0) — وضع واحد: **الفقرة التحضيرية (MEMO)**.
 *
 * تسجيل تحضيري مستقل بلا إخراج للمكبرات — يفتح AudioRecord خاصاً به (مصدر
 * MIC الخام بجودة أنقى، بلا AEC/AGC للمكالمات) لتحضير الفقرات مسبقاً قبل
 * يوم الإذاعة. لا يعمل أثناء البث المباشر: قاعدة **مسجّل واحد أبداً** —
 * الميكروفون ملك لحالة واحدة في كل لحظة، فلا تنافس على عتاد الالتقاط ولا
 * افتتاح مسجّل ثانٍ فوق بث حي (استقرار البث المباشر هو الأولوية — قرار
 * الأستاذ v0.57.0: «لا داعي لتسجيل البث المباشر»).
 *
 * سجلات الأرشيف القديمة بمصادري PTT/MIX (قبل v0.57.0) تبقى صالحة وتُعرض
 * بعناوينها الأصلية عبر [RecordingSource] — الإزالة شملت مسار *البدء* حصراً.
 *
 * الحفظ النهائي: finish() في الكاتب (AacFileWriter أو OpusFileWriter — v0.29.0)
 * ثم إدراج سجل في Room مع لقطة (اسم + دور) المستخدم الحالي عند لحظة التسجيل.
 */
class RecordingManager(
    private val context: Context,
    private val repository: RecordingRepository,
    /** مزوّد المستخدم الحالي — يُقرأ عند الحفظ فقط (لا حالة مكررة) */
    private val currentUserProvider: suspend () -> UserEntity?,
    /** حالة البث الحي — قاعدة المسجّل الواحد: لا فقرة تحضيرية أثناء البث */
    private val isLiveProvider: () -> Boolean = { false },
    /** v0.29.0: تفضيل «عالي الدقة Opus HD» من الإعدادات — لقطة حية يحدّثها جامع الإعدادات */
    private val hdPreferredProvider: () -> Boolean = { true }
) {
    // v0.15.0: أي استثناء في كوروتين الحفظ (فشل قرص مثلاً) يُسجّل في CrashGuard
    // بدل أن يتصاعد إلى القاتل الافتراضي ويغلق التطبيق أثناء إنهاء البث
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO +
            com.saidjte.schoolradio.core.util.CrashGuard.coroutineHandler
    )

    // ===== الحالة المشتركة مع الواجهة =====
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _mode = MutableStateFlow<RecordingSource?>(null)
    val mode: StateFlow<RecordingSource?> = _mode.asStateFlow()

    /** مؤقّت التسجيل — يُحدَّث من خيط الصوت (StateFlow يكتم التكرار) */
    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()

    /**
     * مستوى الموجة الحية: RMS لدفعات PCM **المرسلة فعلاً إلى الكاتب** —
     * فالموجة تعرض ما يُسجل بالضبط، لا ما يدخل الميكروفون فقط.
     */
    private val _recordLevel = MutableStateFlow(0f)
    val recordLevel: StateFlow<Float> = _recordLevel.asStateFlow()

    // ===== الحالة الداخلية =====
    private var writer: PcmAudioWriter? = null

    /** v0.29.0: هل التسجيل الجاري بترميز Opus HD؟ — يقرر نوع MIME في سجل الأرشيف */
    private var writerUsesOpus = false

    // وضع الفقرة المستقلة (AudioRecord خاص + خيط قراءة خاص)
    private var memoRecord: AudioRecord? = null
    private var memoThread: Thread? = null
    @Volatile private var memoRunning = false

    /** مجلد الأرشيف: مسار التطبيق الخاص — بلا صلاحيات تخزين ويُحذف مع التطبيق */
    private fun recordingsDir(): File {
        val base = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)
            ?: context.filesDir // نادراً: ذاكرة خارجية غير متاحة
        return File(base, "recordings").apply { mkdirs() }
    }

    /**
     * بدء تسجيل فقرة تحضيرية مستقلة — قاعدة المسجّل الواحد: أثناء البث
     * المباشر يرفض بأمانة (ميكروفون البث مفتوح ولا ينافسه مسجّل ثانٍ).
     * @return null عند النجاح أو رسالة خطأ عربية جاهزة للعرض
     */
    fun startMemoRecording(): String? {
        if (_isRecording.value) return "التسجيل جارٍ بالفعل"
        if (isLiveProvider()) {
            return "التسجيل أثناء البث المباشر غير متاح — أوقف البث أولاً ثم سجّل فقرة تحضيرية"
        }
        return begin(RecordingSource.MEMO)
    }

    /** المسار المشترك: اختيار الترميز ثم تهيئة الكاتب ثم فتح الميكروفون */
    private fun begin(newSource: RecordingSource): String? {
        val startedAt = System.currentTimeMillis()
        val dir = recordingsDir()

        // v0.29.0: قرار Opus HD — تفضيل المستخدم + أندرويد 10+ + وجود المشفر فعلاً
        val wantOpus = RecordingCodecLogic.preferOpus(
            android.os.Build.VERSION.SDK_INT,
            OpusFileWriter.encoderAvailable(),
            hdPreferredProvider()
        )
        var opusError: String? = null
        if (wantOpus) {
            val f = File(dir, RecordingLogic.newFileName(newSource, startedAt, RecordingCodecLogic.extension(true)))
            val w = OpusFileWriter(f.absolutePath)
            val err = w.start()
            if (err == null) {
                return startCommon(w, usesOpus = true)
            }
            // فشل تهيئة Opus (جهاز شاذ) — رجوع شفاف إلى AAC بدل إفشال التسجيل كله
            opusError = err
            CrashGuard.record("OpusFileWriter.start", IllegalStateException(err))
        }
        val f2 = File(dir, RecordingLogic.newFileName(newSource, startedAt, RecordingCodecLogic.extension(false)))
        val w2 = AacFileWriter(f2.absolutePath)
        val err2 = w2.start()
        if (err2 != null) {
            return if (opusError != null) "تعذر بدء التسجيل: $opusError — وAAC أيضاً: $err2"
            else "تعذر بدء التسجيل: $err2"
        }
        return startCommon(w2, usesOpus = false)
    }

    /** التجهيز المشترك بعد نجاح تهيئة الكاتب (v0.29.0: استُخرج لدعم الترميزين) */
    private fun startCommon(w: PcmAudioWriter, usesOpus: Boolean): String? {
        writer = w
        writerUsesOpus = usesOpus
        _mode.value = RecordingSource.MEMO
        _elapsedMs.value = 0L
        _recordLevel.value = 0f // الموجة الجديدة تبدأ من الصمت

        return startMemoCapture(w) // نفتح الميكروفون بأنفسنا
    }

    /** إيقاف التسجيل وحفظ السجل في الأرشيف — آمن من أي خيط */
    fun stopRecording() {
        if (!_isRecording.value && memoRecord == null) return
        _isRecording.value = false
        _recordLevel.value = 0f // الموجة تعود إلى الصمت فوراً
        // v0.15.0: إيقاف خيط الفقرة قد ينتظر حتى 400ms — يُنفّذ خارج الخيط
        // المستدعي (قد يكون الخيط الرئيسي) وبدرع runCatching
        com.saidjte.schoolradio.core.util.CrashGuard.runOnIo {
            runCatching { stopMemoCapture() }
                .onFailure { CrashGuard.record("stopMemoCapture", it) }
            finishRecording()
        }
    }

    /** الإغلاق الفعلي والحفظ — على خيط IO دائماً (v0.15.0) */
    private fun finishRecording() {
        val w = writer
        writer = null
        if (w == null) return

        // الإغلاق الكامل (EOS + تفريغ) على خيط IO — قد يأخذ حتى بضع مئات ms
        scope.launch {
            val result = w.finish()
            if (result == null || w.failed != null) {
                // v0.38.0: ملف فاشل لا يُترك يتماً في مجلد الأرشيف أبداً —
                // الكاتب نفسه يحذف ناتجه المكسور، وهذا يحذف أي بقايا قديمة
                runCatching { File(w.filePath).delete() }
                _mode.value = null
                return@launch
            }
            val (durationMs, sizeBytes) = result
            if (durationMs < 300) {
                // أقل من ثلث ثانية = ضغطة خاطئة — لا نلوث الأرشيف ولا نترك ملفاً يتماً
                runCatching { File(w.filePath).delete() }
                _mode.value = null
                return@launch
            }
            val user = runCatching { currentUserProvider() }.getOrNull()
            repository.insert(
                RecordingEntity(
                    title = RecordingLogic.defaultTitle(RecordingSource.MEMO, System.currentTimeMillis()),
                    // المسار الحقيقي الذي كتبه الكاتب (وليس مجلد جديد) — الملف نفسه في الأرشيف
                    filePath = w.filePath,
                    durationMs = durationMs,
                    sizeBytes = sizeBytes,
                    source = RecordingSource.MEMO.name,
                    // v0.29.0: نوع MIME حسب الترميز الفعلي — يقود التشغيل/المشاركة/التصدير/الشارة
                    mimeType = RecordingCodecLogic.mimeType(writerUsesOpus),
                    recordedByName = user?.name,
                    recordedByRole = user?.role
                )
            )
            _mode.value = null
        }
    }

    /** فتح الميكروفون لوضع الفقرة: AudioRecord مصدر MIC (بلا معالجة مكالمات) */
    private fun startMemoCapture(w: PcmAudioWriter): String? {
        val minBuf = AudioRecord.getMinBufferSize(
            MicrophoneController.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) {
            w.finish()
            return "معاملات الصوت غير مدعومة على هذا الجهاز"
        }
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC, // خام: جودة تسجيل أنقى من مصدر المكالمات
                MicrophoneController.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 2
            )
        } catch (e: Exception) {
            w.finish()
            return "تعذر فتح الميكروفون: ${e.message ?: "خطأ غير معروف"}"
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            w.finish()
            return "تعذر تهيئة الميكروفون على هذا الجهاز"
        }
        // v0.44.0: نفس عقد البث المباشر — المايك الخارجي عبر USB-C يُلتقط منه
        // صراحة إن وُجد، فالافتراضي يبقى على المدمج وحده رغم وجود الخارجي
        usbInputDevice(
            context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        )?.let { record.setPreferredDevice(it) }

        memoRecord = record
        memoRunning = true
        _isRecording.value = true

        // دفعات 10ms مثل خط البث تماماً — نفس نمط الحساب للمؤقّت
        val chunk = ShortArray(MicrophoneController.SAMPLE_RATE / 100)
        memoThread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            record.startRecording()
            while (memoRunning && writer === w) {
                val n = try {
                    record.read(chunk, 0, chunk.size)
                } catch (_: Exception) {
                    break
                }
                if (n > 0) {
                    w.feed(chunk, n)
                    _elapsedMs.value = w.totalSamples * 1000L / MicrophoneController.SAMPLE_RATE
                    // الموجة الحية للفقرة التمهيدية — من دفعاتها نفسها
                    _recordLevel.value = WaveformLogic.normalize(rmsOf(chunk, n))
                }
            }
            try { record.stop() } catch (_: Exception) {}
            record.release()
        }, "memo-recorder").apply { start() }
        return null
    }

    /** إيقاف خيط الفقرة — يُترك إطلاق AudioRecord للخيط نفسه بعد آخر قراءة */
    private fun stopMemoCapture() {
        memoRunning = false
        memoThread?.join(400)
        memoThread = null
        memoRecord = null
    }

    /** RMS دفعة PCM كنسبة من المدى الكامل — خفيفة عمداً (حلقة واحدة) */
    private fun rmsOf(x: ShortArray, n: Int): Float {
        if (n <= 0) return 0f
        var acc = 0L
        for (i in 0 until n) acc += x[i].toLong() * x[i]
        return (kotlin.math.sqrt(acc.toDouble() / n) / Short.MAX_VALUE).toFloat()
    }
}

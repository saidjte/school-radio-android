package com.saidjte.schoolradio.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

/**
 * مدير التسجيلات — يدخل التسجيل إلى الأرشيف في وضعين:
 *
 *  1) وضع البث (PTT): يسجّل البث الحي الجاري. دفعات PCM تأتي من حلقة
 *     MicrophoneController عبر feedLivePcm() — نفس خط الأنابيب، صفر تنازع.
 *     إذا انتهى البث بينما التسجيل يعمل يُوقف ويُحفظ تلقائياً (لا ملفات تالفة).
 *
 *  2) وضع الفقرة (MEMO): تسجيل تحضيري مستقل بلا إخراج للمكبرات — يفتح
 *     AudioRecord خاصاً به (مصدر MIC الخام بجودة أنقى، بلا AEC/AGC للمكالمات)
 *     لتحضير الفقرات مسبقاً قبل يوم الإذاعة.
 *
 * الحفظ النهائي: finish() في AacFileWriter ثم إدراج سجل في Room مع لقطة
 * (اسم + دور) المستخدم الحالي عند لحظة التسجيل.
 */
class RecordingManager(
    private val context: Context,
    private val repository: RecordingRepository,
    /** مزوّد المستخدم الحالي — يُقرأ عند الحفظ فقط (لا حالة مكررة) */
    private val currentUserProvider: suspend () -> UserEntity?,
    /** مزوّد حالة البث الحي — لمنع بدء تسجيل بث بلا بث فعلي */
    private val isLiveProvider: () -> Boolean = { false }
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ===== الحالة المشتركة مع الواجهة =====
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _mode = MutableStateFlow<RecordingSource?>(null)
    val mode: StateFlow<RecordingSource?> = _mode.asStateFlow()

    /** مؤقّت التسجيل الحي — يُحدَّث من خيط الصوت (StateFlow يكتم التكرار) */
    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()

    /**
     * v0.12.0 — مستوى الموجة الحية: RMS لدفعات PCM **المرسلة فعلاً إلى الكاتب**
     * (بعد التلاشي والكسب وسلسلة تنظيف المذيع) — فالموجة تعرض ما يُسجل بالضبط،
     * لا ما يدخل الميكروفون فقط. يُغذّى من المسارين: حلقة البث (PTT) وخيط الفقرة (MEMO).
     */
    private val _recordLevel = MutableStateFlow(0f)
    val recordLevel: StateFlow<Float> = _recordLevel.asStateFlow()

    // ===== الحالة الداخلية =====
    private var writer: AacFileWriter? = null
    private var source: RecordingSource = RecordingSource.PTT

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
     * بدء تسجيل البث الحي الجاري (الميكروفون مفتوح بالفعل في MicrophoneController).
     * @return null عند النجاح أو رسالة خطأ عربية جاهزة للعرض
     */
    fun startLiveRecording(): String? {
        if (_isRecording.value) return "التسجيل جارٍ بالفعل"
        if (!isLiveProvider()) return "ابدأ البث المباشر أولاً ثم سجّله"
        return begin(RecordingSource.PTT)
    }

    /** بدء تسجيل فقرة تحضيرية مستقلة (المشغل والبث متوقفان عادةً) */
    fun startMemoRecording(): String? {
        if (_isRecording.value) return "التسجيل جارٍ بالفعل"
        return begin(RecordingSource.MEMO)
    }

    /** المسار المشترك للوضعين: تهيئة الكاتب ثم تجهيز حسب الوضع */
    private fun begin(newSource: RecordingSource): String? {
        val startedAt = System.currentTimeMillis()
        val dir = recordingsDir()
        val file = File(dir, RecordingLogic.newFileName(newSource, startedAt))
        val w = AacFileWriter(file.absolutePath)
        val err = w.start()
        if (err != null) return "تعذر بدء التسجيل: $err"

        writer = w
        source = newSource
        _mode.value = newSource
        _elapsedMs.value = 0L
        _recordLevel.value = 0f // v0.12.0: موجة جديدة تبدأ من الصمت

        return if (newSource == RecordingSource.MEMO) {
            startMemoCapture(w) // في وضع الفقرة نفتح الميكروفون بأنفسنا
        } else {
            _isRecording.value = true // في وضع البث ستتدفق الدفعات من حلقة PTT
            null
        }
    }

    /**
     * نقطة الربط من حلقة البث الحي — تُستدعى من خيط PTT كل ~10ms.
     * خفيفة عمداً: تمرير واحد + تحديث مؤقّت (StateFlow يكتم التحديثات المتتالية).
     */
    fun feedLivePcm(pcm: ShortArray, n: Int) {
        val w = writer ?: return
        if (!_isRecording.value || source != RecordingSource.PTT) return
        w.feed(pcm, n)
        _elapsedMs.value = w.totalSamples * 1000L / MicrophoneController.SAMPLE_RATE
        // v0.12.0: نفس الدفعة تكفي لحساب مستوى الموجة الحية (كلفة 480 عملية/10ms)
        _recordLevel.value = WaveformLogic.normalize(rmsOf(pcm, n))
    }

    /** يُنادى تلقائياً عند توقف البث الحي — ينقذ الملف قبل فتح الميكروفون */
    fun onLiveBroadcastStopped() {
        if (_isRecording.value && source == RecordingSource.PTT) {
            stopRecording()
        }
    }

    /** إيقاف التسجيل وحفظ السجل في الأرشيف — آمن من أي خيط */
    fun stopRecording() {
        if (!_isRecording.value && memoRecord == null) return
        _isRecording.value = false
        _recordLevel.value = 0f // v0.12.0: الموجة تعود إلى الصمت فوراً
        stopMemoCapture()

        val w = writer
        writer = null
        val finishedSource = source
        if (w == null) return

        // الإغلاق الكامل (EOS + تفريغ) على خيط IO — قد يأخذ حتى بضع مئات ms
        scope.launch {
            val result = w.finish()
            if (result == null || w.failed != null) {
                _mode.value = null
                return@launch
            }
            val (durationMs, sizeBytes) = result
            if (durationMs < 300) {
                // أقل من ثلث ثانية = ضغطة خاطئة — لا نلوث الأرشيف
                _mode.value = null
                return@launch
            }
            val user = runCatching { currentUserProvider() }.getOrNull()
            repository.insert(
                RecordingEntity(
                    title = RecordingLogic.defaultTitle(finishedSource, System.currentTimeMillis()),
                    // المسار الحقيقي الذي كتبه الكاتب (وليس مجلد جديد) — الملف نفسه في الأرشيف
                    filePath = w.filePath,
                    durationMs = durationMs,
                    sizeBytes = sizeBytes,
                    source = finishedSource.name,
                    recordedByName = user?.name,
                    recordedByRole = user?.role
                )
            )
            _mode.value = null
        }
    }

    /** فتح الميكروفون لوضع الفقرة: AudioRecord مصدر MIC (بلا معالجة مكالمات) */
    private fun startMemoCapture(w: AacFileWriter): String? {
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
                    // v0.12.0: الموجة الحية للفقرة التمهيدية — من دفعاتها نفسها
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

    /** v0.12.0: RMS دفعة PCM كنسبة من المدى الكامل — خفيفة عمداً (حلقة واحدة) */
    private fun rmsOf(x: ShortArray, n: Int): Float {
        if (n <= 0) return 0f
        var acc = 0L
        for (i in 0 until n) acc += x[i].toLong() * x[i]
        return (kotlin.math.sqrt(acc.toDouble() / n) / Short.MAX_VALUE).toFloat()
    }
}

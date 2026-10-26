package com.saidjte.schoolradio.audio

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.saidjte.schoolradio.core.util.CrashGuard
import com.saidjte.schoolradio.core.util.PlaybackGuardLogic
import com.saidjte.schoolradio.core.util.RecordingIntegrityLogic
import com.saidjte.schoolradio.data.local.entity.RecordingEntity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * مشغّل أرشيف التسجيلات — ExoPlayer مستقل عن مشغّل المكتبة الخلفي عمداً:
 * لا يمس جلسة الوسائط ولا إشعار النظام (استماع مراجعة سريع من الشاشة).
 *
 * v0.8.0: العزلة صارت مُدارة مركزياً عبر PlaybackCoordinator —
 * كل بدء/إيقاف يمر بالحَكَم (لا تداخل صوتي مع القائمة الرئيسية أو البث المباشر)،
 * وانتهاء المقطع طبيعياً يُبلّغ عبر [onEnded] ليستأنف الحَكَم ما كان يعمل قبله.
 *
 * التحديث الدوري للموضع: Handler بفاصل 500ms فقط أثناء التشغيل —
 * لا حلقات في الخلفية ولا استهلاك طاقة عند الخمول.
 */
class RecordingsPlayer(context: Context) {

    data class State(
        val recordingId: Long? = null,   // التسجيل المختار حالياً
        val isPlaying: Boolean = false,
        val positionMs: Long = 0,
        val durationMs: Long = 0,
        /** v0.9.0: سرعة التشغيل الحالية (0.5..2.0) — تبقى بين المقاطع حتى تغييرها */
        val speed: Float = 1f,
        /**
         * v0.20.0: إزاحة بداية القص المطبّقة على التسجيل الحالي (0 = بلا قص).
         * مع Clipping تكون المواضع نسبية للجزء المختار — هذا الحقل يوثّقها
         * للواجهة وللتشخيص دون تغيير أي منطق عرض (0:00 = بداية الجزء المختار).
         */
        val trimStartMs: Long = 0
    )

    private val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext).build()
    private val handler = Handler(Looper.getMainLooper())

    /** v0.9.0: معرف الجلسة الصوتية — يوصلها ServiceLocator بمحرك المؤثرات المشترك */
    val audioSessionId: Int get() = player.audioSessionId

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val poller = object : Runnable {
        override fun run() {
            try {
                val p = player
                _state.value = _state.value.copy(
                    positionMs = p.currentPosition.coerceAtLeast(0),
                    durationMs = if (p.duration > 0) p.duration else _state.value.durationMs,
                    isPlaying = p.isPlaying
                )
                if (p.isPlaying) handler.postDelayed(this, 250)
            } catch (t: Throwable) {
                // v0.37.0: حلقة التحديث لا تقتل العملية أبداً — توثيق وهدوء
                CrashGuard.record("recordings.poller", t)
            }
        }
    }

    /** رد نداء نهاية المقطع طبيعياً — يوصله ServiceLocator بالحَكَم للاستئناف التلقائي */
    var onEnded: (() -> Unit)? = null

    /**
     * v0.36.0: أحداث الأخطاء غير المتزامنة (صيغة غير مدعومة/ملف تالف/فك ترميز)
     * برسائل عربية جاهزة — تجمعها شاشة الأرشيف في snackbar بدل صمت مريب.
     */
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    init {
        // عند انتهاء المقطع طبيعياً: حدّث الحالة وأبلغ الحَكَم (استئناف القائمة الرئيسية)
        // v0.37.0: أي استثناء في ردود النداء يهرب عبر الخيط الداخلي للمشغّل ويقتل العملية
        // (كانت الحماية في toggle تُغطي البدء فقط) — لذلك كل رد نداء محاط بدرع مستقل.
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                runCatching {
                    if (playbackState == Player.STATE_ENDED) {
                        _state.value = _state.value.copy(isPlaying = false)
                        onEnded?.invoke()
                    }
                }.onFailure { CrashGuard.record("recordings.onState", it) }
            }

            // v0.10.0: لحظة جاهزية المقطع — ثبّت المدة الفعلية (قد تختلف عن القيمة المسجلة)
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                runCatching {
                    _state.value = _state.value.copy(positionMs = 0)
                }.onFailure { CrashGuard.record("recordings.onTransition", it) }
            }

            // v0.36.0: فشل التشغيل غير المتزامن — توثيق + رسالة عربية بدل صمت مريب
            override fun onPlayerError(error: PlaybackException) {
                runCatching {
                    CrashGuard.record("recordings.playback", error)
                    handler.removeCallbacks(poller)
                    _state.value = _state.value.copy(isPlaying = false)
                    _errors.tryEmit(PlaybackGuardLogic.decodeFailureMessage())
                }.onFailure { CrashGuard.record("recordings.onError", it) }
            }
        })
    }

    /**
     * تشغيل/إيقاف مؤقت/تبديل — استدعِ من الخيط الرئيسي (Compose).
     * نفس التسجيل = تبديل تشغيل/إيقاف، تسجيل آخر = استبدال فوري.
     *
     * v0.10.0 — إصلاح خلل حرج: recordingId لم يكن يُضبط في الحالة أبداً عند
     * بدء تسجيل جديد، فكان المشغل المثبّت في الأرشيف لا يظهر إطلاقاً ولا
     * يتزامن أي سطر مع الصوت (فُقد التحكم الكامل بالتسجيلات).
     * كما أصبحت الدالة تُرجع رسالة عربية عند ملف مفقود بدل الصمت المريب،
     * وتعيد المقطع المنتهي من بدايته عند الضغط الثانية بعد انتهائه طبيعياً.
     *
     * @return null عند النجاح، أو رسالة خطأ جاهزة للعرض (ملف مفقود مثلاً)
     */
    fun toggle(recording: RecordingEntity): String? {
        val currentId = _state.value.recordingId
        if (currentId == recording.id) {
            if (player.playbackState == Player.STATE_ENDED) {
                // انتهى سابقاً طبيعياً — إعادة تشغيل من البداية بدل ضغطة ميتة
                player.seekTo(0)
                player.play()
            } else if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
            startPolling()
            return null
        }
        val file = java.io.File(recording.filePath)
        if (!file.exists()) {
            // ملف مفقود (استعادة نسخة على جهاز آخر مثلاً) — رسالة واضحة بدل انهيار أو صمت
            _state.value = State() // صفّر الاختيار
            return "ملف هذا التسجيل غير موجود على الجهاز — ربما حُذف أو نُقل"
        }
        // v0.39.0: الصدق البنيوي — ملف تالف من إصدار قديم (كاتب الحاويات قبل إصلاح
        // v0.38.0) لا يُسلَّم لمشغل فك الترميز أصلاً: رسالة السبب الحقيقي بدل «فشل
        // فك الترميز» العامة التي جعلت الأستاذ يظن أن كل التسجيلات معطلة.
        val state = RecordingIntegrityLogic.classify(
            exists = true,
            fileSize = file.length(),
            head = RecordingIntegrityLogic.readFileHead(file),
            mimeType = recording.mimeType
        )
        if (state == RecordingIntegrityLogic.State.CORRUPT) {
            _state.value = State(speed = _state.value.speed)
            return RecordingIntegrityLogic.corruptMessage()
        }
        // v0.20.0 + v0.36.0: القص اليدوي (محرر الموجة) يطبّق هنا عبر الحارس —
        // قيم تالفة في التخزين لا تصل إلى المشغل أبداً (PlaybackGuardLogic محصّن ومختبر).
        val guard = PlaybackGuardLogic.sanitizeClip(
            recording.trimStartMs, recording.trimEndMs, recording.durationMs
        )
        val clip = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(guard.startMs)
            .setEndPositionMs(guard.endMs ?: androidx.media3.common.C.TIME_UNSET)
            .build()
        try {
            player.setMediaItem(
                MediaItem.Builder()
                    .setUri(android.net.Uri.fromFile(file))
                    .setClippingConfiguration(clip)
                    .build()
            )
            player.prepare()
            player.play()
        } catch (t: Throwable) {
            // v0.36.0: أي فشل بدء متزامن = رسالة عربية + توثيق، لا انهيار أبداً
            CrashGuard.record("recordings.toggle", t)
            _state.value = State(speed = _state.value.speed)
            return PlaybackGuardLogic.startFailureMessage()
        }
        // ★ الإصلاح الجوهري: ربط الحالة بالتسجيل الجديد فوراً —
        // من دون هذا يبقى recordingId صفراً/قديماً فلا يظهر المشغل ولا يتوهّج السطر النشط
        _state.value = _state.value.copy(
            recordingId = recording.id,
            positionMs = 0,
            // القيمة المسجلة — تُصححها المدة الفعلية عند الجاهزية (مع القص: مدة الجزء المختار)
            durationMs = guard.endMs?.let { it - guard.startMs } ?: recording.durationMs,
            trimStartMs = guard.startMs,
            isPlaying = true
        )
        startPolling()
        return null
    }

    /** تقديم/ترجيع إلى موضع محدد بالمللي ثانية — لشريط التقدم التفاعلي (v0.8.0) */
    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0))
        // دفعة تحديث فورية حتى لا ينتظر شريط التقدم الدورة القادمة
        _state.value = _state.value.copy(positionMs = player.currentPosition.coerceAtLeast(0))
        if (player.isPlaying) startPolling()
    }

    /** v0.9.0: تقديم/ترجيع نسبي (±15 ثانية مثلاً) — أزرار مشغل الاحتراف */
    fun skipBy(deltaMs: Long) {
        seekTo(player.currentPosition + deltaMs)
    }

    /**
     * v0.9.0: سرعة التشغيل (0.5..2.0) — لمراجعة النصوص بسرعة أو تفحّم التفاصيل.
     * سرعة ExoPlayer تبقى معينة عبر تغيير المقطع حتى تعديلها صراحة.
     */
    fun setSpeed(speed: Float) {
        val s = speed.coerceIn(0.5f, 2.0f)
        player.setPlaybackSpeed(s)
        _state.value = _state.value.copy(speed = s)
    }

    /**
     * v0.15.0 — فيدَر قناة المعاينة في الميكسر الاحترافي:
     * مستوى مستقل لمعاينة الأرشيف (يُضرب بالماستر في الميكسر) —
     * يُطبق فوراً ويبقى عبر التسجيلات حتى تغييره.
     */
    fun setVolume(v: Float) {
        player.volume = v.coerceIn(0f, 1f)
    }

    /** إيقاف كامل — يُستدعى من الحَكَم فقط (بدء قائمة رئيسية أو بث مباشر) */
    fun stopAll() {
        player.stop()
        _state.value = State(speed = _state.value.speed)
    }

    /** تحرير الموارد — مع دورة حياة العملية (singleton في ServiceLocator) */
    fun release() {
        handler.removeCallbacksAndMessages(null)
        com.saidjte.schoolradio.di.ServiceLocator.detachAudioEffects(player.audioSessionId)
        player.release()
    }

    private fun startPolling() {
        handler.removeCallbacks(poller)
        handler.post(poller)
    }
}

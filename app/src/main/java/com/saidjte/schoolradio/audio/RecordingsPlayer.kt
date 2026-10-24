package com.saidjte.schoolradio.audio

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.saidjte.schoolradio.data.local.entity.RecordingEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
        val speed: Float = 1f
    )

    private val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext).build()
    private val handler = Handler(Looper.getMainLooper())

    /** v0.9.0: معرف الجلسة الصوتية — يوصلها ServiceLocator بمحرك المؤثرات المشترك */
    val audioSessionId: Int get() = player.audioSessionId

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val poller = object : Runnable {
        override fun run() {
            val p = player
            _state.value = _state.value.copy(
                positionMs = p.currentPosition.coerceAtLeast(0),
                durationMs = if (p.duration > 0) p.duration else _state.value.durationMs,
                isPlaying = p.isPlaying
            )
            if (p.isPlaying) handler.postDelayed(this, 250)
        }
    }

    /** رد نداء نهاية المقطع طبيعياً — يوصله ServiceLocator بالحَكَم للاستئناف التلقائي */
    var onEnded: (() -> Unit)? = null

    init {
        // عند انتهاء المقطع طبيعياً: حدّث الحالة وأبلغ الحَكَم (استئناف القائمة الرئيسية)
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    _state.value = _state.value.copy(isPlaying = false)
                    onEnded?.invoke()
                }
            }
        })
    }

    /**
     * تشغيل/إيقاف مؤقت/تبديل — استدعِ من الخيط الرئيسي (Compose).
     * نفس التسجيل = تبديل تشغيل/إيقاف، تسجيل آخر = استبدال فوري.
     */
    fun toggle(recording: RecordingEntity) {
        val currentId = _state.value.recordingId
        if (currentId == recording.id) {
            if (player.isPlaying) player.pause() else player.play()
            startPolling()
            return
        }
        val file = java.io.File(recording.filePath)
        if (!file.exists()) {
            // ملف مفقود (استعادة نسخة على جهاز آخر مثلاً) — رسالة عبر الحالة بدل انهيار
            _state.value = State() // صفّر الاختيار
            return
        }
        player.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(file)))
        player.prepare()
        player.play()
        startPolling()
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

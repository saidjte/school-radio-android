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
 * لا يمس جلسة الوسائط ولا إشعار النظام (استماع مراجعة سريع من الشاشة)،
 * ويتوقف تلقائياً إذا بدأ البث المباشر (الميكروفون أولوية في الاستوديو).
 *
 * التحديث الدوري للموضع: Handler بفاصل 500ms فقط أثناء التشغيل —
 * لا حلقات في الخلفية ولا استهلاك طاقة عند الخمول.
 */
class RecordingsPlayer(context: Context) {

    data class State(
        val recordingId: Long? = null,   // التسجيل المختار حالياً
        val isPlaying: Boolean = false,
        val positionMs: Long = 0,
        val durationMs: Long = 0
    )

    private val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext).build()
    private val handler = Handler(Looper.getMainLooper())

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
            if (p.isPlaying) handler.postDelayed(this, 500)
        }
    }

    init {
        // عند انتهاء المقطع طبيعياً: حدّث الحالة (تُعاد الأيقونة إلى «تشغيل»)
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    _state.value = _state.value.copy(isPlaying = false)
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

    /** إيقاف كامل — يُستدعى عند بدء البث المباشر (أولوية الميكروفون) */
    fun stopAll() {
        player.stop()
        _state.value = State()
    }

    /** تحرير الموارد — مع دورة حياة العملية (singleton في ServiceLocator) */
    fun release() {
        handler.removeCallbacksAndMessages(null)
        player.release()
    }

    private fun startPolling() {
        handler.removeCallbacks(poller)
        handler.post(poller)
    }
}

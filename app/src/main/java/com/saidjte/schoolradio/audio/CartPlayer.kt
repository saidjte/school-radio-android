package com.saidjte.schoolradio.audio

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * محرك حائط الجينغل (Cart Player) — v0.11.0.
 *
 * نمط «الرقائق» المعتمد في كل استوديوهات الإذاعة (PlayIt Cartwall وما شابه):
 * الجينغل يُطلَق بلمسة واحدة فوق كل شيء — فوق موسيقى القائمة وفوق البث المباشر
 * (النشيد/الجنديل خلف صوت المذيع هو السلوك الإذاعي الطبيعي) — لذلك لا يمر
 * هذا المشغل عبر PlaybackCoordinator عمداً: طبقة مزج مستقلة بفيدَر مستوى خاص.
 *
 * دلالة SOLO: مشغل واحد فقط — إطلاق رقاقة جديدة يوقف التي قبلهها بتلاشٍ سريع
 * (كما في أجهزة الاستوديو)، والتكرار (Loop) لكل رقاقة على حدة للموسيقى الخلفية.
 *
 * التحديث الدوري: 250ms فقط أثناء وجود رقاقة نشطة — لا حلقات خلفية عند الخمول.
 *
 * ★ الخيطية: كل الدوال العامة يجب استدعاؤها من الخيط الرئيسي (متطلب ExoPlayer) —
 *   الواجهة Compose تستدعيها من Compose مباشرة فهذا محقق بطبيعة الاستخدام.
 */
class CartPlayer(context: Context) {

    data class State(
        /** مفتاح الرقاقة النشطة "صفحة:موضع" — null = لا شيء يعمل */
        val activeKey: String? = null,
        /** عنوان المقطع المعروض على الرقاقة */
        val title: String? = null,
        val isPlaying: Boolean = false,
        val positionMs: Long = 0,
        val durationMs: Long = 0,
        /** هل الرقاقة النشطة في وضع التكرار؟ */
        val loop: Boolean = false,
        /** فيدَر قناة الجينغل 0..1 — يبقى بين الرقائق */
        val volume: Float = 0.85f
    )

    private val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext).build()
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** معرف الجلسة الصوتية — يوصلها ServiceLocator بمحرك المؤثرات المشترك */
    val audioSessionId: Int get() = player.audioSessionId

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var fadeJob: Job? = null

    private val poller = object : Runnable {
        override fun run() {
            val s = _state.value
            if (s.activeKey == null) return
            _state.value = s.copy(
                positionMs = player.currentPosition.coerceAtLeast(0),
                durationMs = if (player.duration > 0) player.duration else s.durationMs,
                isPlaying = player.isPlaying
            )
            handler.postDelayed(this, 250)
        }
    }

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    // انتهت الرقاقة طبيعياً (بلا تكرار) — صفر الحالة وجاهز لإطلاق جديد
                    _state.value = _state.value.copy(
                        activeKey = null, isPlaying = false, title = null,
                        positionMs = 0, durationMs = 0, loop = false
                    )
                    handler.removeCallbacks(poller)
                }
            }
        })
    }

    /**
     * إطلاق رقاقة — دلالة SOLO: أي رقاقة تعمل تتوقف بتلاشٍ سريع أولاً.
     * الضغط الثانية على نفس الرقاقة العاملة = إيقافها (سلوك أجهزة الاستوديو).
     * @return null عند النجاح أو رسالة عربية للعرض (ملف مفقود مثلاً)
     */
    fun fire(padKey: String, uri: String, title: String, loop: Boolean, volume: Float): String? {
        val javaFile = java.io.File(uri)
        val mediaUri = if (uri.startsWith("content://") || uri.startsWith("file://")) {
            android.net.Uri.parse(uri)
        } else if (javaFile.exists()) {
            android.net.Uri.fromFile(javaFile)
        } else {
            return "ملف هذا الجينغل غير متاح على الجهاز — أعد ربط الرقاقة بمقطع من المكتبة"
        }

        val samePad = _state.value.activeKey == padKey
        if (samePad) {
            // نفس الرقاقة: ضغطة ثانية = إيقاف (ومع التكرار أيضاً — قرار المذيع الصريح)
            stop()
            return null
        }

        fadeJob?.cancel()
        player.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        player.volume = _state.value.volume // نبدأ من الفيدَر الحالي
        player.setMediaItem(MediaItem.fromUri(mediaUri))
        player.prepare()
        player.play()
        _state.value = _state.value.copy(
            activeKey = padKey, title = title, isPlaying = true,
            positionMs = 0, durationMs = 0, loop = loop,
            volume = volume
        )
        player.volume = volume
        // دخول ناعم قصير (60ms) — بلا فرقعة عند إطلاق الرقاقة
        animateVolume(from = 0f, to = volume, durationMs = 60)
        handler.removeCallbacks(poller)
        handler.post(poller)
        return null
    }

    /** إيقاف الرقاقة النشطة بتلاشٍ سريع — لا قطع مفاجئ على مكبرات القاعة */
    fun stop() {
        if (_state.value.activeKey == null) return
        fadeJob?.cancel()
        val remaining = max(player.duration - player.currentPosition, 0L)
        val fadeMs = 80L
        if (player.duration > 0 && remaining < fadeMs + 50) {
            // قرب النهاية أصلاً — أوقف فوراً بلا تلاشٍ يعيد التشغيل من حافة المقطع
            hardStop()
        } else {
            fadeJob = scope.launch {
                animateVolume(from = player.volume, to = 0f, durationMs = fadeMs)
                hardStop()
            }
        }
    }

    /** فيدَر قناة الجينغل — يُطبق فورياً على الرقاقة النشطة ويُحفظ لما بعدها */
    fun setVolume(v: Float) {
        val nv = v.coerceIn(0f, 1f)
        _state.value = _state.value.copy(volume = nv)
        // أثناء تلاشٍ جارٍ لا نتدخل — التلاشي نفسه سيصل إلى المستوى الجديد
        if (fadeJob?.isActive != true) player.volume = nv
    }

    /** تحرير الموارد — دورة حياة العملية (singleton في ServiceLocator) */
    fun release() {
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        com.saidjte.schoolradio.di.ServiceLocator.detachAudioEffects(player.audioSessionId)
        player.release()
    }

    private fun hardStop() {
        player.stop()
        handler.removeCallbacks(poller)
        _state.value = _state.value.copy(
            activeKey = null, isPlaying = false, title = null,
            positionMs = 0, durationMs = 0, loop = false
        )
        fadeJob = null
    }

    private fun animateVolume(from: Float, to: Float, durationMs: Long) {
        fadeJob?.cancel()
        if (durationMs <= 0) {
            player.volume = to
            return
        }
        val steps = 6
        val stepMs = durationMs / steps
        fadeJob = scope.launch {
            var i = 1
            while (i <= steps) {
                player.volume = from + (to - from) * (i.toFloat() / steps)
                if (i < steps) delay(stepMs)
                i++
            }
        }
    }
}

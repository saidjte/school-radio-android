package com.saidjte.schoolradio.audio

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.saidjte.schoolradio.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * خدمة الوسائط الأمامية — تشغيل الصوت في الخلفية مع إشعار بعناصر تحكم.
 *
 * لماذا MediaSessionService؟
 * - تدير دورة حياة الخدمة الأمامية (foregroundServiceType="mediaPlayback") والإشعار تلقائياً.
 * - تتيح التحكم من أي واجهة عبر MediaController دون مؤشرات عامة.
 * - handleAudioFocus=true يجعل ExoPlayer يخفض/يتوقف تلقائياً عند المكالمات.
 *
 * فصل معماري صريح: هذه الخدمة تخدم *المشغل* فقط؛ بث الميكروفون المباشر
 * (MicrophoneController) منفصل تماماً حتى لا يتنافسا على الميكروفون/المخازن.
 *
 * v0.15.0 — محرك حدود الفواصل (Breaks) بمهلة انتقال قابلة للضبط:
 *  عندما تكون قائمة «الفواصل المجدولة» نشطة (وسم breaks:) يُفعَّل
 *  pauseAtEndOfMediaItems فيتوقف المشغل تلقائياً عند نهاية كل مقطع،
 *  ويتولى هذا الصنف قرار العبور:
 *   • العنصر التالي **جينل** → انتظار [ServiceLocator.breakTransitionMs]
 *     (مهلة انتقال الجدول قابلة للضبط من محرر الفواصل) ثم انطلاق الجينل.
 *   • العنصر التالي **فقرة** (بعد جينل أو تخطي فاصل) → عبور فوري بلا مهلة.
 *  القوائم العادية (مكتبة/قوائم/أرشيف) لا تتأثر إطلاقاً — الآلية موصولة
 *  بتدفق [ServiceLocator.breaksModeActive] الذي تديره PlaybackConnection.
 *
 *  الإيقاف اليدوي للمستخدم لا يخطئه المحرك: نتفاعر فقط مع سبب
 *  PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM (حافة مقطع) —
 *  أما ضغطة المستخدم فسببها USER_REQUEST فنتركه بعنايته.
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var serviceScope: CoroutineScope? = null
    private var pendingTransitionJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        // v0.51.0: مشغل المكتبة يُبنى بمصنع يغرس تاب تسجيل البث الكامل —
        // نسخ PCM المفكوك إلى mixBus.mediaTap (جذر قلب معمارية v0.49.0:
        // المنصة لا تعيد صوت التطبيق نفسه من AudioPlaybackCapture فالتطبيق
        // يأخذ نسخته من آخر نقطة يملكها قبل مكبرات الجهاز)
        val player = ExoPlayer.Builder(
            this,
            renderersFactoryWithTap(this, ServiceLocator.mixBus.mediaTap)
        )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true) // إيقاف مؤقت عند فك السماعة تلقائياً
            .build()

        // v0.9.0: ربط سلسلة المؤثرات (معادل الصوت/الباص/المحيطي/مقياس VU)
        // بجلسة هذا المشغل — الاستوديو يتحكم بها مركزياً عبر AudioEffects
        ServiceLocator.attachAudioEffects(player.audioSessionId)

        // ===== v0.15.0: محرك حدود الفواصل =====
        serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        player.addListener(boundaryListener)
        // مزامنة وضع الفواصل مع المشغل — أي تغيير من PlaybackConnection يُطبق فوراً
        serviceScope!!.launch {
            ServiceLocator.breaksModeActive.collect { enabled ->
                player.pauseAtEndOfMediaItems = enabled
                if (!enabled) pendingTransitionJob?.cancel()
            }
        }

        mediaSession = MediaSession.Builder(this, player).build()
    }

    private val boundaryListener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            val player = mediaSession?.player ?: return
            if (playWhenReady) return // انطلاق/استئناف عادي — لا حافة هنا
            if (reason != Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) return
            if (!ServiceLocator.breaksModeActive.value) return

            // المشغل توقف عند حافة مقطع (نهاية فقرة) — قرر العبور
            val curIndex = player.currentMediaItemIndex
            val nextIndex = curIndex + 1
            if (nextIndex >= player.mediaItemCount) return // نهاية القائمة — لا عبور

            val nextIsBreak = player.getMediaItemAt(nextIndex)
                .mediaMetadata.extras?.getBoolean(
                    PlaybackConnection.QueueItem.Companion.KEY_IS_BREAK, false
                ) == true

            pendingTransitionJob?.cancel()
            if (!nextIsBreak) {
                // حافة لا يليها جينل (فقرة بعد جينل) — عبور فوري بسلاسة
                player.seekToNextMediaItem()
                player.play()
                return
            }

            val delayMs = ServiceLocator.breakTransitionMs.value
            if (delayMs <= 0L) {
                // مهلة صفر: الجينل ينطلق فور نهاية الفقرة (السلوك الإذاعي الكلاسيكي)
                player.seekToNextMediaItem()
                player.play()
            } else {
                // مهلة انتقال قابلة للضبط: صمت مقصود ثم الجينل —
                // نتحقق قبل العبور أن المستخدم لم يتقدم يدوياً خلال الانتظار
                pendingTransitionJob = serviceScope!!.launch {
                    delay(delayMs)
                    val p = mediaSession?.player ?: return@launch
                    if (p.currentMediaItemIndex == curIndex &&
                        !p.playWhenReady &&
                        ServiceLocator.breaksModeActive.value
                    ) {
                        p.seekToNextMediaItem()
                        p.play()
                    }
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        // إن لم يكن هناك تشغيل فعلي فلا داعي لبقاء الخدمة حية
        val player = mediaSession?.player
        if (player == null || (!player.playWhenReady && player.playbackState == Player.STATE_IDLE)) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        serviceScope?.cancel()
        serviceScope = null
        pendingTransitionJob = null
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}

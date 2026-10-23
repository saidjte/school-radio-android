package com.saidjte.schoolradio.audio

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

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
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true) // إيقاف مؤقت عند فك السماعة تلقائياً
            .build()

        mediaSession = MediaSession.Builder(this, player).build()
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
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}

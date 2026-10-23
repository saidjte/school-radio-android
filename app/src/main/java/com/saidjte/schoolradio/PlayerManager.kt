package com.saidjte.schoolradio

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * مدير التشغيل المركزي (Singleton) — ExoPlayer واحد لكل التطبيق
 * يدعم: تسجيلات مدمجة (asset:///) وروابط شبكة (http/https) وبثاً مباشراً
 */
object PlayerManager {

    private var player: ExoPlayer? = null
    private val listeners = mutableListOf<Player.Listener>()
    private var currentSegment: Segment? = null

    val isPlaying: Boolean get() = player?.isPlaying == true
    val currentPosition: Long get() = player?.currentPosition ?: 0L
    val duration: Long get() = player?.duration?.takeIf { it != C.TIME_UNSET } ?: 0L
    val nowPlaying: Segment? get() = currentSegment

    fun ensure(context: Context): ExoPlayer {
        if (player == null) {
            player = ExoPlayer.Builder(context.applicationContext).build().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                        .build(),
                    true // إدارة تركيز الصوت تلقائياً (إيقاف عند المكالمة)
                )
            }
        }
        return player!!
    }

    fun play(context: Context, segment: Segment) {
        val p = ensure(context)
        currentSegment = segment
        val metadata = MediaMetadata.Builder()
            .setTitle(segment.title)
            .setArtist(segment.category)
            .build()
        val item = MediaItem.Builder()
            .setUri(segment.audioUrl)
            .setMediaId(segment.id)
            .setMediaMetadata(metadata)
            .build()
        p.setMediaItem(item)
        p.prepare()
        p.playWhenReady = true
    }

    fun playLive(context: Context, live: LiveInfo) {
        if (!live.enabled || live.url.isBlank()) return
        val p = ensure(context)
        currentSegment = Segment(
            id = "live",
            title = live.title,
            description = "بث مباشر",
            category = "مباشر",
            durationSec = 0,
            audioUrl = live.url,
            order = 0
        )
        val metadata = MediaMetadata.Builder()
            .setTitle(live.title)
            .setArtist("مباشر")
            .build()
        val item = MediaItem.Builder()
            .setUri(live.url)
            .setMediaId("live")
            .setMediaMetadata(metadata)
            .build()
        p.setMediaItem(item)
        p.prepare()
        p.playWhenReady = true
    }

    fun togglePlayPause() {
        player?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun stop() {
        player?.stop()
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
    }

    fun setSpeed(speed: Float) {
        player?.playbackParameters = PlaybackParameters(speed)
    }

    fun release() {
        player?.release()
        player = null
    }
}

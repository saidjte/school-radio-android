package com.saidjte.schoolradio.audio

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** حالة المشغل كما تراها الواجهة */
data class PlaybackUiState(
    val hasQueue: Boolean = false,
    val isPlaying: Boolean = false,
    val currentTitle: String? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val queueSize: Int = 0,
    val queueIndex: Int = -1,
    /** عناوين عناصر قائمة الانتظار (لحد 50) للعرض في القائمة */
    val queueTitles: List<String> = emptyList(),
    val volume: Float = 1f,
    val repeatQueue: Boolean = false,
    val errorMessage: String? = null
)

/**
 * جسر التحكم بالمشغل: يبني MediaController متصلاً بـ PlaybackService (جلسة Media3)
 * ويعرض حالة التشغيل كـ StateFlow للواجهة.
 *
 * ضمانات معمارية:
 * - مشغل واحد فقط في التطبيق (الخدمة) — كل الشاشات تتحكم عبر هذه القناة.
 * - التشغيل يستمر عند التنقل بين الشاشات أو إطفاء الشاشة (خدمة أمامية + إشعار).
 */
class PlaybackConnection(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var positionPollJob: kotlinx.coroutines.Job? = null

    /** يبني المتحكم — استدعِه مرة من Application/أول شاشة */
    fun connect() {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            try {
                val c = future.get()
                controller = c
                c.addListener(listener)
                startPolling()
                pushState()
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    errorMessage = "تعذر الاتصال بخدمة المشغل: ${e.message ?: "خطأ غير معروف"}"
                )
            }
        }, MoreExecutors.directExecutor())
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = pushState()
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = pushState()
        override fun onPlaybackStateChanged(playbackState: Int) = pushState()
        override fun onVolumeChanged(volume: Float) = pushState()
        override fun onRepeatModeChanged(repeatMode: Int) = pushState()

        override fun onPlayerError(error: PlaybackException) {
            // رسائل عربية واضحة حسب رمز الخطأ
            val msg = when (error.errorCode) {
                PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
                PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
                PlaybackException.ERROR_CODE_IO_UNSPECIFIED ->
                    "الملف غير متاح — أعد ربطه من المكتبة أو تحقق من صلاحية الوصول"
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ->
                    "صيغة الملف غير مدعومة أو تالف"
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ->
                    "فشل تهيئة وحدة فك الترميز على هذا الجهاز"
                else -> "خطأ في التشغيل: ${error.errorCodeName}"
            }
            _state.value = _state.value.copy(errorMessage = msg)
            // نتجاوز المقطع المعطوب تلقائياً ليكمل الباقي
            if ((controller?.mediaItemCount ?: 0) > 1) controller?.seekToNextMediaItem()
        }
    }

    private fun startPolling() {
        positionPollJob?.cancel()
        positionPollJob = scope.launch {
            while (isActive) {
                pushState()
                delay(500)
            }
        }
    }

    private fun pushState() {
        val c = controller ?: return
        _state.value = PlaybackUiState(
            hasQueue = c.mediaItemCount > 0,
            isPlaying = c.isPlaying,
            currentTitle = c.currentMediaItem?.mediaMetadata?.title?.toString(),
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = if (c.duration > 0) c.duration else 0,
            queueSize = c.mediaItemCount,
            queueIndex = c.currentMediaItemIndex,
            queueTitles = (0 until c.mediaItemCount.coerceAtMost(50)).map {
                c.getMediaItemAt(it).mediaMetadata.title?.toString() ?: "بدون عنوان"
            },
            volume = c.volume,
            repeatQueue = c.repeatMode == Player.REPEAT_MODE_ALL,
            errorMessage = _state.value.errorMessage
        )
    }

    // ================== أوامر التشغيل ==================

    /** تشغيل قائمة مقاطع بالترتيب (يستبدل قائمة الانتظار الحالية) */
    fun playQueue(items: List<QueueItem>, startIndex: Int = 0, repeatAll: Boolean = false) {
        val c = controller ?: return
        if (items.isEmpty()) return
        // تشغيل يدوي من الواجهة — ليس فقرة مجدولة، نمسح معلومات العرض على الهواء
        com.saidjte.schoolradio.di.ServiceLocator.setOnAir(null)
        _state.value = _state.value.copy(errorMessage = null)
        c.setMediaItems(items.map { it.toMediaItem() }, startIndex, 0)
        c.repeatMode = if (repeatAll) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
        c.prepare()
        c.play()
    }

    fun playPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE && c.mediaItemCount > 0) c.prepare()
            c.play()
        }
    }

    fun stopAll() {
        controller?.stop()
        controller?.clearMediaItems()
        com.saidjte.schoolradio.di.ServiceLocator.setOnAir(null)
        _state.value = PlaybackUiState()
    }

    fun next() { controller?.seekToNextMediaItem() }
    fun previous() { controller?.seekToPreviousMediaItem() }
    fun skipTo(index: Int) { controller?.seekTo(index, 0) }
    fun seekTo(positionMs: Long) { controller?.seekTo(positionMs) }

    /** ضبط مستوى الصوت — تستخدمه أيضاً آلية الخفض التلقائي أثناء الإعلان */
    fun setVolume(volume: Float) {
        controller?.volume = volume.coerceIn(0f, 1f)
    }

    fun setRepeatAll(enabled: Boolean) {
        controller?.repeatMode = if (enabled) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    /** عنصر قائمة انتظار مبسط للمشغل */
    data class QueueItem(val uri: String, val title: String, val trackId: Long) {
        fun toMediaItem(): MediaItem = MediaItem.Builder()
            .setUri(uri)
            .setMediaId(trackId.toString())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .build()
            )
            .build()
    }
}

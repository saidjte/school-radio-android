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
    /** v0.8.0: خلط الترتيب مفعّل؟ */
    val shuffleEnabled: Boolean = false,
    /** v0.8.0: وسم مصدر القائمة الحالية — مثل "library" أو "playlist:3" لتمييز ما يشغّله المستخدم */
    val queueTag: String? = null,
    /** v0.8.0: معرف المقطع من المكتبة قيد التشغيل (mediaId) — لتمييز السطر النشط */
    val currentTrackId: Long? = null,
    /**
     * v0.23.0: هل العنصر الحالي فاصلاً (جينل)؟ — شاشة العرض تستعمله لعرض
     * لوحة عدّ الفاصل حتى مع جينلات القوائم الموسومة (breaks:) دون معلومات على الهواء.
     */
    val currentIsBreak: Boolean = false,
    /** v0.14.0: متبقي المقطع الحالي (مدة − موضع) للمؤقت التنازلي على شاشة العرض */
    val remainingMs: Long = 0,
    /**
     * v0.14.0: متبقي قائمة الفقرة كاملة (الحالي + مدد القادمة) —
     * ‎-1 إذا مدد العناصر القادمة غير معروفة بعد (لم تُحضّر) فلا يُعرض.
     */
    val queueRemainingMs: Long = -1L,
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

    /** v0.9.0: مستوى صوت مرغوب قبل جاهزية المتحكم — يُطبق فور الاتصال */
    @Volatile private var pendingVolume: Float? = null

    /** ضبط مستوى صوت البداية المحفوظ — يعمل ولو لم تكن خدمة الوسائط جاهزة بعد */
    fun applyStartupVolume(v: Float) {
        val c = controller
        if (c != null) c.volume = v.coerceIn(0f, 1f) else pendingVolume = v
    }

    /** يبني المتحكم — استدعِه مرة من Application/أول شاشة */
    fun connect() {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            try {
                val c = future.get()
                controller = c
                // v0.9.0: طبّق مستوى الصوت المحفوظ فور الجاهزية (قبل أي تشغيل)
                pendingVolume?.let { c.volume = it.coerceIn(0f, 1f) }
                pendingVolume = null
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
        override fun onShuffleModeEnabledChanged(enabled: Boolean) = pushState()

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
        // v0.14.0: حساب المؤقت التنازلي — متبقي المقطع + متبقي القائمة إن كانت المدد معروفة
        val pos = c.currentPosition.coerceAtLeast(0)
        val dur = if (c.duration > 0) c.duration else 0
        val remaining = (dur - pos).coerceAtLeast(0)
        var queueRemaining = remaining.toLong()
        var queueKnown = true
        // مدد العناصر القادمة من Timeline (النوافذ غير المحضّرة تعيد TIME_UNSET)
        val timeline = c.currentTimeline
        val window = androidx.media3.common.Timeline.Window()
        for (i in (c.currentMediaItemIndex + 1) until c.mediaItemCount) {
            timeline.getWindow(i, window)
            val d = window.durationMs
            if (d > 0 && d != androidx.media3.common.C.TIME_UNSET) queueRemaining += d
            else { queueKnown = false; break }
        }
        _state.value = PlaybackUiState(
            hasQueue = c.mediaItemCount > 0,
            isPlaying = c.isPlaying,
            currentTitle = c.currentMediaItem?.mediaMetadata?.title?.toString(),
            positionMs = pos,
            durationMs = dur,
            remainingMs = remaining,
            queueRemainingMs = if (queueKnown) queueRemaining else -1L,
            queueSize = c.mediaItemCount,
            queueIndex = c.currentMediaItemIndex,
            queueTitles = (0 until c.mediaItemCount.coerceAtMost(50)).map {
                c.getMediaItemAt(it).mediaMetadata.title?.toString() ?: "بدون عنوان"
            },
            volume = c.volume,
            repeatQueue = c.repeatMode == Player.REPEAT_MODE_ALL,
            shuffleEnabled = c.shuffleModeEnabled,
            queueTag = _state.value.queueTag,
            currentTrackId = c.currentMediaItem?.mediaId?.toLongOrNull(),
            // v0.23.0: قراءة وسم الفاصل من عنصر الوسائط الحالي (جينلات القوائم/الفاصل المجدول)
            currentIsBreak = c.currentMediaItem?.mediaMetadata?.extras
                ?.getBoolean(QueueItem.KEY_IS_BREAK, false) == true,
            errorMessage = _state.value.errorMessage
        )
    }

    // ================== أوامر التشغيل ==================

    /**
     * تشغيل قائمة مقاطع بالترتيب (يستبدل قائمة الانتظار الحالية).
     * v0.8.0: [queueTag] يعرّف مصدر الطلب (مثل "playlist:2") لتمييز القائمة النشطة في الواجهة.
     * v0.15.0: [transitionDelayMs] مهلة الانتقال قبل الجينل في قوائم الفواصل —
     * عند بدء قائمة وسمها "breaks:" تُفعّل آلية الحدود في PlaybackService تلقائياً
     * (توقف حافة كل مقطع + مهلة انتقال قابلة للضبط قبل انطلاق الجينل).
     */
    fun playQueue(
        items: List<QueueItem>,
        startIndex: Int = 0,
        repeatAll: Boolean = false,
        queueTag: String? = null,
        transitionDelayMs: Long = 0L
    ) {
        val c = controller ?: return
        if (items.isEmpty()) return
        // تشغيل يدوي من الواجهة — ليس فقرة مجدولة، نمسح معلومات العرض على الهواء
        com.saidjte.schoolradio.di.ServiceLocator.setOnAir(null)
        // v0.15.0: مزامنة وضع الفواصل مع خدمة المشغل — أي قائمة بلا وسم فواصل
        // تعطّل آلية الحدود فوراً (القوائم العادية تتقدم تلقائياً كما كانت)
        val breaksMode = queueTag?.startsWith("breaks:") == true
        com.saidjte.schoolradio.di.ServiceLocator.breaksModeActive.value = breaksMode
        com.saidjte.schoolradio.di.ServiceLocator.breakTransitionMs.value =
            if (breaksMode) transitionDelayMs.coerceAtLeast(0L) else 0L
        _state.value = _state.value.copy(errorMessage = null, queueTag = queueTag)
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

    /** v0.8.0: إيقاف مؤقت صريح — تستخدمه آلية العزلة عند معاينة التسجيلات */
    fun pause() { controller?.pause() }

    /** v0.8.0: استئناف صريح — تستخدمه آلية العزلة بعد انتهاء المعاينة */
    fun play() {
        val c = controller ?: return
        if (c.playbackState == Player.STATE_IDLE && c.mediaItemCount > 0) c.prepare()
        c.play()
    }

    fun stopAll() {
        controller?.stop()
        controller?.clearMediaItems()
        com.saidjte.schoolradio.di.ServiceLocator.setOnAir(null)
        // v0.15.0: إعادة وضع الفواصل إلى الخمول عند تفريغ القائمة
        com.saidjte.schoolradio.di.ServiceLocator.breaksModeActive.value = false
        com.saidjte.schoolradio.di.ServiceLocator.breakTransitionMs.value = 0L
        _state.value = PlaybackUiState()
    }

    /** v0.8.0: تفعيل/تعطيل خلط ترتيب قائمة الانتظار */
    fun setShuffle(enabled: Boolean) {
        controller?.shuffleModeEnabled = enabled
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

    /**
     * عنصر قائمة انتظار مبسط للمشغل.
     * v0.15.0: [isBreak] يميز عناصر الجينل (الفواصل) — تُحمل داخل Metadata.extras
     * ليتعرف عليها محرك الحدود في PlaybackService ويطبق مهلة الانتقال قبلها.
     * v0.18.0: [trimStartMs] قص الفراغ الأول — يتحول إلى ClippingConfiguration
     * عند بناء MediaItem إذا كان إعداد «قص الفراغ الأول» مفعّلاً (تجاهل = تشغيل كما هو).
     * v0.19.0: [trimEndMs] نقطة نهاية القص اليدوي + [manualTrim] وسم السلطة:
     * القص اليدوي قرار صريح فيُطبّق دائماً بغضّ النظر عن المفتاح العام.
     */
    data class QueueItem(
        val uri: String,
        val title: String,
        val trackId: Long,
        val isBreak: Boolean = false,
        val trimStartMs: Long = 0,
        val trimEndMs: Long = 0,
        val manualTrim: Boolean = false
    ) {
        fun toMediaItem(): MediaItem {
            val metadata = MediaMetadata.Builder()
                .setTitle(title)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            if (isBreak) {
                metadata.setExtras(
                    android.os.Bundle().apply { putBoolean(KEY_IS_BREAK, true) }
                )
            }
            val builder = MediaItem.Builder()
                .setUri(uri)
                .setMediaId(trackId.toString())
                .setMediaMetadata(metadata.build())
            // القص: البداية (آلي عند تفعيل الإعداد، أو يدوي دائماً) + النهاية (يدوي فقط)
            val applyStart = trimStartMs > 0 &&
                (manualTrim || com.saidjte.schoolradio.di.ServiceLocator.trimSilenceEnabled.value)
            val applyEnd = manualTrim && trimEndMs > trimStartMs
            if (applyStart || applyEnd) {
                val clip = MediaItem.ClippingConfiguration.Builder()
                if (applyStart) clip.setStartPositionMs(trimStartMs)
                if (applyEnd) clip.setEndPositionMs(trimEndMs)
                builder.setClippingConfiguration(clip.build())
            }
            return builder.build()
        }

        companion object {
            /** وسم عناصر الفواصل داخل MediaMetadata.extras — يقرأه PlaybackService */
            const val KEY_IS_BREAK = "sr_is_break"

            /** قراءة وسم الفاصل من عنصر وسائط (خدمة المشغل) */
            fun MediaItem.isBreakItem(): Boolean =
                mediaMetadata.extras?.getBoolean(KEY_IS_BREAK, false) == true

            /**
             * بناء عنصر من كيان مقطع المكتبة — يورّث قص الفراغ الأول المقيس تلقائياً
             * والقص اليدوي (بداية + نهاية) منذ v0.19.0.
             * البديل الأنظف عن تمرير الحقول يدوياً في كل نداء.
             */
            fun of(
                track: com.saidjte.schoolradio.data.local.entity.AudioTrackEntity,
                isBreak: Boolean = false
            ): QueueItem =
                QueueItem(
                    track.uri, track.title, track.id, isBreak,
                    track.trimStartMs, track.trimEndMs, track.trimManual
                )
        }
    }
}

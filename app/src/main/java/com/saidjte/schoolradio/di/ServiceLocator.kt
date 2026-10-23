package com.saidjte.schoolradio.di

import android.content.Context
import com.saidjte.schoolradio.audio.AudioRoutingManager
import com.saidjte.schoolradio.audio.MicrophoneController
import com.saidjte.schoolradio.audio.PlaybackConnection
import com.saidjte.schoolradio.audio.RecordingManager
import com.saidjte.schoolradio.audio.RecordingsPlayer
import com.saidjte.schoolradio.data.backup.BackupRepository
import com.saidjte.schoolradio.data.local.AppDatabase
import com.saidjte.schoolradio.data.repository.LibraryRepository
import com.saidjte.schoolradio.data.repository.PlaylistRepository
import com.saidjte.schoolradio.data.repository.RecordingRepository
import com.saidjte.schoolradio.data.repository.ScheduleRepository
import com.saidjte.schoolradio.data.repository.SessionRepository
import com.saidjte.schoolradio.data.settings.SettingsStore
import com.saidjte.schoolradio.scheduler.SchedulerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * حاقن اعتمادي يدوي بسيط (ServiceLocator).
 *
 * لماذا لا Hilt في هذه النسخة؟ حجم المشروع المتوسط + بنية أحادية الوحدة،
 * والحاقن اليدوي يقلل تعقيد البناء (لا kapt إضافي) مع الحفاظ على فصل الطبقات.
 * الانتقال إلى Hilt مستقبلاً يستبدل هذا الملف دون مساس بالطبقات.
 */
object ServiceLocator {

    @Volatile private var initialized = false

    private lateinit var appContext: Context
    lateinit var appScope: CoroutineScope
        private set
    lateinit var db: AppDatabase
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var library: LibraryRepository
        private set
    lateinit var playlists: PlaylistRepository
        private set
    lateinit var schedules: ScheduleRepository
        private set
    lateinit var backup: BackupRepository
        private set
    lateinit var recordingsRepo: RecordingRepository
        private set
    lateinit var session: SessionRepository
        private set
    lateinit var routing: AudioRoutingManager
        private set
    lateinit var playback: PlaybackConnection
        private set
    lateinit var scheduler: SchedulerManager
        private set
    lateinit var recordings: RecordingManager
        private set

    // مشغل تسجيلات الأرشيف (ExoPlayer مستقل عن مشغل المكتبة الخلفي)
    val recordingsPlayer: RecordingsPlayer by lazy { RecordingsPlayer(appContext) }

    // ===== حالة البث المباشر المشتركة مع الواجهة =====
    private val _micLevel = MutableStateFlow(0f)
    val micLevel: StateFlow<Float> = _micLevel.asStateFlow()

    private val _micLive = MutableStateFlow(false)
    val micLive: StateFlow<Boolean> = _micLive.asStateFlow()

    private val _micMessage = MutableStateFlow<String?>(null)
    val micMessage: StateFlow<String?> = _micMessage.asStateFlow()

    private var volumeBeforeDuck: Float? = null

    /** متحكم البث المباشر — الخفض التلقائي موصول هنا مركزياً، ودفعة PCM موصولة بالتسجيل */
    val mic: MicrophoneController by lazy {
        MicrophoneController(
            context = appContext,
            routing = routing,
            onDuck = { duck ->
                appScope.launch(Dispatchers.Main) {
                    val s = settings.settings.first()
                    if (s.duckEnabled) {
                        if (duck) {
                            // احفظ المستوى الحالي ثم اخفض إلى مستوى الإعلان
                            volumeBeforeDuck = playback.state.value.volume
                                .takeIf { it > 0f } ?: s.defaultVolume
                            playback.setVolume(s.duckVolume)
                        } else {
                            // أعد المستوى السابق بعد انتهاء الإعلان
                            playback.setVolume(volumeBeforeDuck ?: s.defaultVolume)
                            volumeBeforeDuck = null
                        }
                    }
                }
            },
            onLevel = { level -> _micLevel.value = level },
            // ★ نقطة الربط v0.3.0: كل دفعة PCM من حلقة البث تُغذّي التسجيل (خط أنابيب واحد)
            onPcm = { pcm, n -> recordings.feedLivePcm(pcm, n) },
            onStateChanged = { isLive, message ->
                _micLive.value = isLive
                _micMessage.value = message
                if (!isLive) {
                    _micLevel.value = 0f
                    // انتهى البث والتسجيل يعمل؟ أوقف واحفظ فوراً (لا ملفات تالفة)
                    recordings.onLiveBroadcastStopped()
                }
            }
        )
    }

    /** إعادة ضبط منبهات كل الأحداث المفعلة — تُستدعى بعد أي تعديل في الجدولة */
    suspend fun rearmScheduleAlarms() {
        val enabledIds = schedules.enabledSchedules().map { it.id }.toSet()
        val events = schedules.allEvents().filter { it.scheduleId in enabledIds }
        scheduler.rescheduleAll(events)
    }

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            appContext = context.applicationContext
            appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            db = AppDatabase.get(appContext)
            settings = SettingsStore(appContext)
            library = LibraryRepository(appContext, db)
            playlists = PlaylistRepository(db)
            schedules = ScheduleRepository(db)
            backup = BackupRepository(appContext, db, settings)
            recordingsRepo = RecordingRepository(db.recordingDao())
            session = SessionRepository(settings, db.userDao())
            routing = AudioRoutingManager(appContext)
            playback = PlaybackConnection(appContext)
            scheduler = SchedulerManager(appContext)
            // التسجيل يقرأ المستخدم الحالي عند الحفظ، وحالة البث لمنع التسجيل بلا بث
            recordings = RecordingManager(
                appContext,
                recordingsRepo,
                currentUserProvider = { session.currentUser.first() },
                isLiveProvider = { _micLive.value }
            )
            initialized = true
        }
    }
}

package com.saidjte.schoolradio.di

import android.content.Context
import com.saidjte.schoolradio.audio.AudioEffects
import com.saidjte.schoolradio.audio.AudioRoutingManager
import com.saidjte.schoolradio.audio.CartPlayer
import com.saidjte.schoolradio.audio.MicProcessing
import com.saidjte.schoolradio.audio.MicrophoneController
import com.saidjte.schoolradio.audio.PlaybackConnection
import com.saidjte.schoolradio.audio.PlaybackCoordinator
import com.saidjte.schoolradio.audio.RecordingManager
import com.saidjte.schoolradio.audio.RecordingsPlayer
import com.saidjte.schoolradio.core.util.BreaksLogic
import com.saidjte.schoolradio.data.backup.BackupRepository
import com.saidjte.schoolradio.data.local.AppDatabase
import com.saidjte.schoolradio.data.local.entity.RadioScheduleEntity
import com.saidjte.schoolradio.data.local.entity.ScheduledEventEntity
import com.saidjte.schoolradio.data.repository.LibraryRepository
import com.saidjte.schoolradio.data.repository.PlaylistRepository
import com.saidjte.schoolradio.data.repository.RecordingRepository
import com.saidjte.schoolradio.data.repository.ScheduleRepository
import com.saidjte.schoolradio.data.repository.SessionRepository
import com.saidjte.schoolradio.data.scripts.ScriptRepository
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
 * معلومات الفقرة المعروضة حالياً على شاشة «على الهواء» (v0.5.0).
 * يضبطها ScheduledPlaybackLauncher لحظة إطلاق فقرة مجدولة، ويعرضها OnAirScreen بالصورة والنص.
 *
 * v0.13.0 — ربط السكربت بالفقرة: [scriptId] يفتح لوحة قراءة السكربت (Teleprompter)
 * على شاشة العرض لحظة انطلاق الفقرة، و[scriptTitle] عنوانه إن وُجد (يُتحقق من
 * الوجود لحظة العرض — حذف السكربت لا يكسر شيئاً).
 */
data class OnAirInfo(
    val eventId: Long,
    val eventTitle: String,
    val noteText: String? = null,
    val imagePath: String? = null,
    val scriptId: Long? = null,
    val scriptTitle: String? = null
)

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

    // v0.9.0: مستودع السكربتات الإذاعية (الإعلانات والنصوص)
    lateinit var scripts: ScriptRepository
        private set

    // مشغل تسجيلات الأرشيف (ExoPlayer مستقل عن مشغل المكتبة الخلفي)
    val recordingsPlayer: RecordingsPlayer by lazy {
        RecordingsPlayer(appContext).also { p ->
            // v0.9.0: معاينة التسجيلات تدخل سلسلة المؤثرات المشتركة (معادل الصوت يعمل عليها أيضاً)
            attachAudioEffects(p.audioSessionId)
            // v0.8.0: نهاية المعاينة طبيعياً → الحَكَم يستأنف ما كان يعمل قبله
            p.onEnded = { coordinator.previewFinished() }
        }
    }

    /**
     * v0.11.0: مشغل حائط الجينغل — طبقة مزج مستقلة عمداً:
     * الرقائق تُطلَق فوق القائمة وفوق البث المباشر (النشيد خلف صوت المذيع
     * سلوك إذاعي طبيعي) — لذلك لا يمر عبر PlaybackCoordinator.
     * ★ يجب أول وصول له من الخيط الرئيسي (إنشاء ExoPlayer) — الواجهة Compose
     *   تصل إليه من Compose مباشرة فهذا محقق.
     */
    val cartPlayer: CartPlayer by lazy {
        CartPlayer(appContext).also { p ->
            // الجينغل يدخل سلسلة المؤثرات المشتركة أيضاً — المعادل والباص يعملان عليه
            attachAudioEffects(p.audioSessionId)
        }
    }

    /**
     * v0.8.0 — حَكَم التشغيل الموحّد: مصدر واحد للحقيقة عند تعارض المصادر الصوتية
     * (قائمة المكتبة/القوائم، معاينة التسجيلات، البث المباشر).
     * المنفذ موصول كسولاً لتفادي أي حلقة تهيئة.
     */
    val coordinator: PlaybackCoordinator by lazy {
        PlaybackCoordinator(object : PlaybackCoordinator.PlayerPort {
            override fun isMainPlaying(): Boolean = playback.state.value.isPlaying
            override fun pauseMain() = playback.pause()
            override fun resumeMain() = playback.play()
            override fun stopPreview() = recordingsPlayer.stopAll()
            override fun isMicLive(): Boolean = _micLive.value
        })
    }

    // ===== حالة البث المباشر المشتركة مع الواجهة =====
    private val _micLevel = MutableStateFlow(0f)
    val micLevel: StateFlow<Float> = _micLevel.asStateFlow()

    // ===== v0.9.0: مستوى الموسيقى الحي (VU من Visualizer عبر AudioEffects) =====
    private val _mediaLevel = MutableStateFlow(0f)
    val mediaLevel: StateFlow<Float> = _mediaLevel.asStateFlow()

    // ===== v0.9.0: مؤثرات الصوت — استرجاع الإعدادات المحفوظة مرة واحدة فقط =====
    private var effectsRestored = false
    private var pendingRestore: AudioEffects.RestoreData? = null
    @Volatile private var pendingMicGain = 1f

    /**
     * v0.9.0: ربط جلسة صوتية جديدة بمحرك المؤثرات المشترك —
     * يُستدعى من PlaybackService ومن RecordingsPlayer فور إنشاء المشغلين.
     * أول استدعاء ناجح يطبّق الإعدادات المحفوظة (معادل/باص/محيطي).
     */
    fun attachAudioEffects(sessionId: Int) {
        AudioEffects.attach(sessionId, pendingRestore)
        if (AudioEffects.state.value.available && !effectsRestored) {
            effectsRestored = true
            appScope.launch {
                val s = settings.settings.first()
                val restore = AudioEffects.RestoreData(
                    enabled = s.eqEnabled,
                    presetId = s.eqPresetId,
                    gains = s.eqGains,
                    bass = s.bassStrength,
                    virt = s.virtStrength
                )
                pendingRestore = restore
                AudioEffects.restore(restore)
            }
        }
    }

    /** فك جلسة انتهى مشغلها (تحرير مؤثرات نظيف) */
    fun detachAudioEffects(sessionId: Int) {
        AudioEffects.detach(sessionId)
    }

    // ===== شاشة العرض على الهواء (v0.5.0) =====
    private val _onAir = MutableStateFlow<OnAirInfo?>(null)
    val onAir: StateFlow<OnAirInfo?> = _onAir.asStateFlow()

    /** يُستدعى من AlarmReceiver لحظة إطلاق فقرة مجدولة، ومن الإيقاف اليدوي للمسح */
    fun setOnAir(info: OnAirInfo?) { _onAir.value = info }

    // ===== مسودة فقرة جديدة قادمة من شاشة التسجيلات (زر «جدولة» السريع) =====
    private val _pendingScheduleDraft = MutableStateFlow<ScheduledEventEntity?>(null)
    val pendingScheduleDraft: StateFlow<ScheduledEventEntity?> = _pendingScheduleDraft.asStateFlow()

    fun setPendingScheduleDraft(draft: ScheduledEventEntity?) { _pendingScheduleDraft.value = draft }

    // ===== v0.9.0: السكربت المفتوح حالياً في قارئ Teleprompter =====
    // (الكائنات لا تُمرر عبر مسارات التنقل — تُحمل هنا ثم يُفتح المسار)
    private val _teleprompterScript =
        MutableStateFlow<com.saidjte.schoolradio.data.scripts.ScriptItem?>(null)
    val teleprompterScript: StateFlow<com.saidjte.schoolradio.data.scripts.ScriptItem?> =
        _teleprompterScript.asStateFlow()

    fun setTeleprompterScript(script: com.saidjte.schoolradio.data.scripts.ScriptItem?) {
        _teleprompterScript.value = script
    }

    // ===== v0.10.0: مسار إخراج البث المختار — حالة مشتركة بين الرئيسية وقارئ السكربت =====
    // كان مسار الإخراج حالة محلية في شاشة الرئيسية، فلم يستطع زر الاختصار العائم
    // في قارئ السكربت (أو أي شاشة أخرى) معرفة المسار المختار — صار الآن مركزياً.
    private val _broadcastRoute = MutableStateFlow(AudioRoutingManager.Route.SPEAKER)
    val broadcastRoute: StateFlow<AudioRoutingManager.Route> = _broadcastRoute.asStateFlow()

    fun setBroadcastRoute(route: AudioRoutingManager.Route) { _broadcastRoute.value = route }

    /**
     * v0.10.0: بدء البث المباشر من أي شاشة بالمسار المشترك — الواجهة المشتركة
     * (زر الاختصار العائم وغيره) تستدعي هذا بدل تكرار منطق الصلاحية والمسار.
     * @return false إذا كانت الصلاحية غير ممنوحة (تتولى الشاشة المستدعية طلبها)
     */
    fun startMicBroadcast(onResult: (String?) -> Unit): Boolean {
        if (!mic.hasPermission()) return false
        mic.start(_broadcastRoute.value, onResult)
        return true
    }

    private val _micLive = MutableStateFlow(false)
    val micLive: StateFlow<Boolean> = _micLive.asStateFlow()

    private val _micMessage = MutableStateFlow<String?>(null)
    val micMessage: StateFlow<String?> = _micMessage.asStateFlow()

    private var volumeBeforeDuck: Float? = null

    /**
     * v0.11.0: آخر لقطة لإعدادات معالجة المذيع — يُحدّثها جامع مستمر من DataStore
     * ويقرأها البث عند البدء فتُطبق تغييرات الاستوديو على البث التالي مباشرة.
     */
    @Volatile private var cachedMicProcessing = MicProcessing()

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
                if (isLive) {
                    // v0.8.0: البث المباشر أعلى أولوية — الحَكَم يوقف المعاينة فوراً
                    coordinator.micStarted()
                } else {
                    _micLevel.value = 0f
                    coordinator.micStopped()
                    // انتهى البث والتسجيل يعمل؟ أوقف واحفظ فوراً (لا ملفات تالفة)
                    recordings.onLiveBroadcastStopped()
                }
            },
            // v0.11.0: سلسلة تنظيف صوت المذيع (AGC/حجز الضجيج/إلغاء الصدى)
            processingProvider = { cachedMicProcessing }
        ).also { it.setMicGain(pendingMicGain) }
    }

    /** إعادة ضبط منبهات كل الأحداث المفعلة — تُستدعى بعد أي تعديل في الجدولة */
    suspend fun rearmScheduleAlarms() {
        val enabledIds = schedules.enabledSchedules().map { it.id }.toSet()
        val events = schedules.allEvents().filter { it.scheduleId in enabledIds }
        scheduler.rescheduleAll(events)
    }

    // ================== v0.8.0: بوابات التشغيل الموحّدة ==================

    /**
     * بوابة معاينة التسجيلات — كل الشاشات تستدعي هذه بدل recordingsPlayer مباشرة.
     * تمر عبر الحَكَم (توقف مؤقت للقائمة الرئيسية إن كانت تعمل) وتُرجع رسالة
     * عربية جاهزة للعرض إذا منع البدء (البث المباشر يعمل أو الملف مفقود)،
     * أو null عند النجاح.
     */
    fun previewToggle(recording: com.saidjte.schoolradio.data.local.entity.RecordingEntity): String? {
        val currentId = recordingsPlayer.state.value.recordingId
        val sameRecording = currentId == recording.id
        if (sameRecording) {
            // تبديل تشغيل/إيقاف لنفس التسجيل — لا قرار عزلة جديد
            // v0.10.0: تمرير رسالة فشل (مثل ملف مفقود) إلى الواجهة بدل الصمت
            return recordingsPlayer.toggle(recording)
        }
        return when (coordinator.requestPreviewStart()) {
            PlaybackCoordinator.PreviewDecision.OK -> recordingsPlayer.toggle(recording)
            PlaybackCoordinator.PreviewDecision.BLOCKED_LIVE ->
                "أوقف البث المباشر أولاً للاستماع إلى التسجيل — لا يجوز خلطهما"
        }
    }

    /**
     * بوابة القائمة الرئيسية (مكتبة/قوائم/تشغيل يدوي) — توقف أي معاينة أولاً عبر الحَكَم.
     * الفقرات المجدولة تستدعي [onScheduledPlaybackStarting] مباشرة.
     */
    fun playMainQueue(
        items: List<PlaybackConnection.QueueItem>,
        startIndex: Int = 0,
        repeatAll: Boolean = false,
        queueTag: String? = null
    ) {
        if (items.isEmpty()) return
        coordinator.requestMainQueueStart()
        playback.playQueue(items, startIndex, repeatAll, queueTag)
    }

    /**
     * بوابة الفقرة المجدولة — تُستدعى من AlarmReceiver قبل إطلاق القائمة
     * (على خيط رئيسي): توقف معاينة التسجيلات إن كانت تعمل.
     */
    fun onScheduledPlaybackStarting() {
        coordinator.requestMainQueueStart()
    }

    /**
     * v0.12.0 — التشغيل المتواصل لجدول كامل مع الفواصل الآلية بين فقراته.
     *
     * يحقق حرفياً «إدراج جينغل آلي بين الفقرات»: تُجمع فقرات الجدول بالترتيب
     * الزمني، وتُبنى قائمة واحدة (فقرة ← جينغل ← فقرة …) عبر BreaksLogic،
     * ثم تنطلق عبر البوابة الموحدة (توقّف المعاينات وضمانات الحَكَم كاملة).
     *
     * @return رسالة عربية جاهزة للعرض — نجاح أو تعليل واضح لعدم الانطلاق.
     */
    suspend fun playScheduleWithBreaks(schedule: RadioScheduleEntity): String {
        val events = schedules.allEvents()
            .filter { it.scheduleId == schedule.id }
            .sortedBy { it.startMinutes }
        if (events.isEmpty()) return "لا فقرات في «${schedule.name}» — أضف فقراته أولاً"

        // 1) تجميع محتوى كل فقرة (قائمة/مقطع/تسجيل) — الفقرة المعطوبة تُتخطى بلا عطل
        var skipped = 0
        val segments = events.map { e -> resolveEventContent(e) ?: run { skipped++; emptyList() } }
        if (segments.all { it.isEmpty() })
            return "محتوى فقرات «${schedule.name}» غير متاح — أعد ربط المقاطع من محرر الفقرات"

        // 2) تجميع تجمع الجينغلات وفق إعداد الجدول
        val pool: List<BreaksLogic.PlaySpec> = when {
            schedule.breakTrackId != null ->
                library.trackById(schedule.breakTrackId)
                    ?.takeIf { it.isAvailable }
                    ?.let { listOf(BreaksLogic.PlaySpec(it.uri, it.title, it.id)) }
                    ?: emptyList()
            schedule.breakCategoryId != null ->
                library.tracks.first()
                    .filter { it.categoryId == schedule.breakCategoryId && it.isAvailable }
                    .map { BreaksLogic.PlaySpec(it.uri, it.title, it.id) }
            else -> emptyList()
        }

        // 3) بناء القائمة المتداخلة (فواصل بين الفقرات فقط، بلا تكرار مباشر)
        val queue = BreaksLogic.buildQueue(
            segments = segments,
            config = BreaksLogic.BreakConfig(
                enabled = schedule.breakEnabled,
                trackId = schedule.breakTrackId,
                categoryId = schedule.breakCategoryId
            ),
            breakPool = pool
        )
        if (queue.isEmpty()) return "لا يوجد محتوى قابل للتشغيل في «${schedule.name}»"

        // 4) الإطلاق عبر البوابة الموحدة + شاشة العرض تعرض البرنامج كاملاً
        playMainQueue(
            queue.map { PlaybackConnection.QueueItem(it.uri, it.title, it.trackId) },
            queueTag = "breaks:${schedule.id}"
        )
        setOnAir(
            OnAirInfo(
                eventId = events.first().id,
                eventTitle = "برنامج: ${schedule.name}",
                noteText = if (schedule.breakEnabled && pool.isNotEmpty())
                    "تشغيل متواصل مع فواصل آلية بين الفقرات" else "تشغيل متواصل للفقرات",
                imagePath = null
            )
        )
        val breakNote = when {
            !schedule.breakEnabled -> "بلا فواصل (معطّلة في إعداد الجدول)"
            pool.isEmpty() -> "الفواصل مفعّلة لكن لا جينغل متاح — فحص إعداد الفواصل"
            else -> "مع فواصل آلية بين الفقرات"
        }
        val skippedNote = if (skipped > 0) " • تخطيت $skipped فقرة بلا محتوى" else ""
        return "انطلق «${schedule.name}» (${queue.size} مقطع) — $breakNote$skippedNote"
    }

    /** v0.12.0: تحليل محتوى فقرة إلى عناصر تشغيل — null إذا كان غير متاح */
    private suspend fun resolveEventContent(e: ScheduledEventEntity): List<BreaksLogic.PlaySpec>? = when {
        e.playlistId != null ->
            playlists.resolvedTracks(e.playlistId)
                .map { BreaksLogic.PlaySpec(it.uri, it.title, it.id) }
                .takeIf { it.isNotEmpty() }
        e.recordingId != null ->
            recordingsRepo.byId(e.recordingId)
                ?.takeIf { java.io.File(it.filePath).exists() }
                ?.let { listOf(BreaksLogic.PlaySpec(it.filePath, it.title, it.id)) }
        e.singleTrackId != null ->
            library.trackById(e.singleTrackId)
                ?.takeIf { it.isAvailable }
                ?.let { listOf(BreaksLogic.PlaySpec(it.uri, it.title, it.id)) }
        else -> null
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
            // v0.9.0: السكربتات الإذاعية — تخزين JSON محلي في مسار التطبيق
            scripts = ScriptRepository(appContext.filesDir, appScope)
            // v0.9.0: مضخّة مستوى الموسيقى من محرك المؤثرات إلى حالة الواجهة
            AudioEffects.onMediaLevel = { level -> _mediaLevel.value = level }
            // التسجيل يقرأ المستخدم الحالي عند الحفظ، وحالة البث لمنع التسجيل بلا بث
            recordings = RecordingManager(
                appContext,
                recordingsRepo,
                currentUserProvider = { session.currentUser.first() },
                isLiveProvider = { _micLive.value }
            )
            // v0.9.0: تحميل إعدادات الاستوديو المحفوظة مسبقاً (كسب الميك + مستوى الموسيقى)
            // + استرجاع المؤثرات فور توفر أول جلسة صوتية
            appScope.launch {
                runCatching {
                    val s = settings.settings.first()
                    pendingMicGain = s.micGain
                    mic.setMicGain(s.micGain)
                    playback.applyStartupVolume(s.defaultVolume)
                    val restore = AudioEffects.RestoreData(
                        enabled = s.eqEnabled,
                        presetId = s.eqPresetId,
                        gains = s.eqGains,
                        bass = s.bassStrength,
                        virt = s.virtStrength
                    )
                    pendingRestore = restore
                    if (AudioEffects.state.value.available && !effectsRestored) {
                        effectsRestored = true
                        AudioEffects.restore(restore)
                    }
                }
            }
            // v0.11.0: جامع دائم لإعدادات معالجة المذيع — البث يقرأ اللقطة الأحدث لحظة البدء
            appScope.launch {
                settings.settings.collect { s ->
                    cachedMicProcessing = MicProcessing(
                        agc = s.micAgc,
                        noiseGate = s.micNoiseGate,
                        echoCancel = s.micEchoCancel
                    )
                }
            }
            initialized = true
        }
    }
}

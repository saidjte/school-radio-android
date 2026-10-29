package com.saidjte.schoolradio.di

import android.content.Context
import com.saidjte.schoolradio.audio.AudioEffects
import com.saidjte.schoolradio.audio.AudioRoutingManager
import com.saidjte.schoolradio.audio.CartPlayer
import com.saidjte.schoolradio.audio.MicProcessing
import com.saidjte.schoolradio.audio.MicrophoneController
import com.saidjte.schoolradio.audio.PlaybackConnection
import com.saidjte.schoolradio.audio.RecordingManager
import com.saidjte.schoolradio.core.util.BreaksLogic
import com.saidjte.schoolradio.core.util.CrashGuard
import com.saidjte.schoolradio.core.util.RecordingIntegrityLogic
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
    val scriptTitle: String? = null,
    /** v0.23.0: هذه فقرة فاصل بين الفقرات — شاشة العرض تُظهر لوحة عدّ الفاصل */
    val isBreak: Boolean = false,
    /** v0.23.0: أثناء مهلة الانتقال — لحظة (epoch) انطلاق الجينل لعدّ حي على الشاشة */
    val breakStartsAtMs: Long? = null,
    /** v0.23.0: اسم الجينل — يظهر في لوحة الفاصل أثناء مهلة الانتقال والتشغيل */
    val breakTitle: String? = null
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

    // ===== v0.15.0: حالة محرك حدود الفواصل (تقرأها PlaybackService) =====
    /** هل قائمة الفواصل المجدولة نشطة؟ (تُفعّل توقف الحواف في خدمة المشغل) */
    val breaksModeActive = MutableStateFlow(false)
    /** مهلة الانتقال بالميلي ثانية قبل انطلاق الجينل بعد كل فقرة (0 = فوراً) */
    val breakTransitionMs = MutableStateFlow(0L)

    // v0.18.0: إعداد «قص الفراغ الأول» كلقطة حية — يقرأه QueueItem.toMediaItem
    // متزامناً عند بناء عناصر الوسائط (بلا تعليق) ويحدّثه جامع الإعدادات الدائم
    val trimSilenceEnabled = MutableStateFlow(true)

    private lateinit var appContext: Context
    val appScope: CoroutineScope
        get() = _appScope

    // v0.15.0: أي استثناء خلفي يُسجّل في CrashGuard بدل أن يقتل العملية
    private val _appScope by lazy {
        CoroutineScope(SupervisorJob() + Dispatchers.Default +
            com.saidjte.schoolradio.core.util.CrashGuard.coroutineHandler)
    }
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

    // v0.23.0: خدمة الاستيراد من الإنترنت (يوتوب/ساوندكلاود/Drive/روابط مباشرة)
    lateinit var remote: com.saidjte.schoolradio.data.remote.RemoteMediaService
        private set

    // v0.16.0: الميكسر — تطبيق القنوات موحّد هنا. المستويات الممرّرة «فعّالة»
    // (فيدَر × ماستر × حالة الكتم/الخفض) محسوبة في الواجهة؛ وبدون قيم
    // يُعاد الحساب من الإعدادات المحفوظة (المشاهد، استرجاع المشغلات، انتهاء الخفض).
    // v0.41.0: قناة المعاينة أُزيلت — التسجيلات عبر قناة الموسيقى نفسها.
    fun applyMixerVolumes(
        musicVolume: Float? = null,
        cartVolume: Float? = null
    ) {
        // v0.47.0: عقد media3 — المشغلات تُلمس من الخيط الرئيسي حصراً. هذا الإطلاق
        // كان على Dispatchers.Default فيرمي MediaController/ExoPlayer
        // «Player is accessed on the wrong thread» ويُلتقط في CrashGuard فيبدو
        // سحب الفيدَرات ميتاً بلا أثر — نفس النمط المثبت في onDuck أدناه منذ v0.16.0.
        appScope.launch(Dispatchers.Main) {
            val s = settings.settings.first()
            val m = s.masterVolume.coerceIn(0f, 1f)
            // أثناء إعلان حيّ: المسموع = فيدَر × ماستر × نسبة الخفض — سحب الفيدَر أثناء الإعلان يستجيب
            val ducking = _micLive.value && s.duckEnabled
            val musicEff = musicVolume ?: run {
                val base = s.defaultVolume.coerceIn(0f, 1f) * m
                if (ducking) base * s.duckVolume.coerceIn(0f, 1f) else base
            }
            playback.setVolume(musicEff.coerceIn(0f, 1f))
            // v0.41.0: قناة «معاينة الأرشيف» أُزيلت — التسجيلات تشتغل عبر مشغل
            // المكتبة نفسه (قناة الموسيقى) فلا مشغل معاينة ثانٍ يُضبط من هنا
            if (_cartPlayerInstantiated)
                cartPlayer.setVolume((cartVolume ?: (s.cartVolume * m)).coerceIn(0f, 1f))
        }
    }

    /** v0.16.0: ضبط قناة الجينل إن كان مشغلها قائماً فقط (بلا إنشاء كسول من الميكسر) */
    fun setCartVolumeIfReady(v: Float) {
        if (_cartPlayerInstantiated) cartPlayer.setVolume(v.coerceIn(0f, 1f))
    }

    private val _cartPlayerInstantiated: Boolean
        get() = cartPlayerRef != null
    @Volatile private var cartPlayerRef: CartPlayer? = null

    /**
     * v0.16.0: مشغل حائط الجينغل — طبقة مزج مستقلة عمداً:
     * الرقائق تُطلَق فوق القائمة وفوق البث المباشر (النشيد خلف صوت المذيع
     * سلوك إذاعي طبيعي) — لذلك لا يمر عبر بوابة القائمة الرئيسية.
     * ★ يجب أول وصول له من الخيط الرئيسي (إنشاء ExoPlayer) — الواجهة Compose
     *   تصل إليه من Compose مباشرة فهذا محقق.
     */
    val cartPlayer: CartPlayer by lazy {
        CartPlayer(appContext, tap = mixBus.cartTap).also { p ->
            cartPlayerRef = p
            // v0.15.0: استرجاع فيدَر قناة الجينل × الماستر من الإعدادات المحفوظة
            // v0.47.0: على Main — المشغل أُنشئ على الخيط الرئيسي فيُلمس منه حصراً
            appScope.launch(Dispatchers.Main) {
                val s = settings.settings.first()
                p.setVolume(s.cartVolume * s.masterVolume)
            }
            // الجينغل يدخل سلسلة المؤثرات المشتركة أيضاً — المعادل والباص يعملان عليه
            attachAudioEffects(p.audioSessionId)
        }
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
     * يُستدعى من PlaybackService ومن CartPlayer فور إنشاء المشغلين
     * (v0.41.0: مشغل المعاينة المنفصل أُزيل فبقي مشغلا الخدمة والجينل).
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

    /**
     * v0.8.0 — حَكَم التشغيل الموّد كان يدير عزلة مشغل المعاينة المنفصل —
     * v0.41.0: أُلغي بوجوده الكلي؛ لم يعد هناك مشغل ثانٍ يعزله
     * (التسجيلات تشتغل عبر مشغل المكتبة نفسه فالعزلة بُنيت بلا حاجة إليها).
     */
    private val _micLive = MutableStateFlow(false)
    val micLive: StateFlow<Boolean> = _micLive.asStateFlow()

    // v0.36.0: تفضيل «عالي الدقة Opus HD» كلقطة حية — يقرأه RecordingManager لحظة بدء كل تسجيل
    val recordingHdPreferred = MutableStateFlow(true)

    private val _micMessage = MutableStateFlow<String?>(null)
    val micMessage: StateFlow<String?> = _micMessage.asStateFlow()

    // ===== v0.49.0: إعلان البث + تسجيل البث الكامل =====

    /**
     * v0.51.0: ناقل مزج التابات — جذر معماري واحد لكل أصوات البث الكامل:
     * المشغل والكارتس والنغمة والمايك يملك كل منهم تاباً هنا، والتسجيل يجمعها
     * بمستوياتها الفعلية (المنصة لا تعيد صوت التطبيق نفسه من AudioPlaybackCapture
     * فالتطبيق لا يطلب صوته من أحد — صوته يمر بأيديه).
     */
    val mixBus: com.saidjte.schoolradio.audio.BroadcastMixBus by lazy {
        com.saidjte.schoolradio.audio.BroadcastMixBus()
    }

    /** نغمة «إعلان بدء البث» — عبر مخرج الوسائط وتبُعث نسختها إلى تاب النغمة */
    val announcer: com.saidjte.schoolradio.audio.BroadcastAnnouncer by lazy {
        com.saidjte.schoolradio.audio.BroadcastAnnouncer(toneTap = mixBus.toneTap)
    }

    // ===== v0.50.0: جلسة البث الحي — الإعلان جلسة كاملة لا نغمة فحسب =====

    /**
     * البث على الهواء — من لحظة ضغطة «إعلان بدء البث» حتى «إنهاء جلسة البث».
     * الصفحة الرئيسية تعرض شارة «مباشر» نابضة ما دام صائباً (مستقلة عن المايك:
     * الجلسة تبدأ قبل فتح الهواء وقد تستمر بعده)، والزر نفسه يتحول إلى «إنهاء».
     * حالة جلسة حية بالذاكرة — جلسة بث لا تعيش بعد موت العملية وهذا هو حكمها.
     */
    private val _broadcastOnAir = MutableStateFlow(false)
    val broadcastOnAir: StateFlow<Boolean> = _broadcastOnAir.asStateFlow()

    /** رسائل جلسة البث (نشيد الافتتاح مفقود/غير متوفر…) — نمط micMessage نفسه */
    private val _broadcastMessage = MutableStateFlow<String?>(null)
    val broadcastMessage: StateFlow<String?> = _broadcastMessage.asStateFlow()
    fun setBroadcastMessage(msg: String?) { _broadcastMessage.value = msg }

    /**
     * v0.50.0: إعلان بدء البث — ضغطة واحدة تنفّذ شعورة الافتتاح كاملة:
     *  1. شارة «مباشر» في الصفحة الرئيسية فوراً (الإعلان بدأ فعلاً)
     *  2. النغمة الافتتاحية عبر مكبرات الاستوديو (وإلى تسجيل البث الكامل)
     *  3. بعد انقضاء النغمة فعلاً: نشيد الافتتاح المختار من المكتبة آلياً
     *     إن وُجد (قرار OpeningAnthemLogic الحتمي)
     */
    fun announceBroadcastStart() {
        if (_broadcastOnAir.value) return
        _broadcastOnAir.value = true
        announcer.announceStart {
            // رد النغمة يصل من خيط الصوت — قاعدة v0.47.0 المقدسة: المشغل من
            // الخيط الرئيسي حصراً (نمط onDuck المثبت منذ v0.16.0)
            appScope.launch(Dispatchers.Main) { startOpeningAnthem() }
        }
    }

    /**
     * v0.50.0: إنهاء جلسة البث — الشارة تختفي فوراً، وإن كان النشيد
     * الافتتاحي ما زال قيد التشغيل فهو بنت جلسة الافتتاح فيُوقف مؤقتاً
     * معها (تمييز بالوسم — أي قائمة أخرى يعملها المذيع لاحقاً لا تُمس).
     */
    fun endBroadcastSession() {
        _broadcastOnAir.value = false
        if (playback.state.value.queueTag == "anthem") {
            appScope.launch(Dispatchers.Main) { playback.pause() }
        }
    }

    /** ما بعد النغمة: النشيد المحدد قائمةً بعنصر واحد — يتوقف عند نهايته تلقائياً */
    private suspend fun startOpeningAnthem() {
        val configured = settings.settings.first().openingAnthemId ?: return
        val track = library.trackById(configured)
        when (val d = com.saidjte.schoolradio.core.util.OpeningAnthemLogic.decide(
            configuredId = configured,
            trackExists = track != null,
            isAvailable = track?.isAvailable == true
        )) {
            is com.saidjte.schoolradio.core.util.OpeningAnthemLogic.Decision.Play -> {
                val t = track!!
                // نفس مسار المكتبة الحقيقي — حلّ فوري للمحلي وتجديد خلفي للبث البعيد
                val plan = remote.buildQueuePlan(listOf(t), 0)
                playMainQueue(
                    listOf(
                        PlaybackConnection.QueueItem(
                            plan.uris.first(), t.title, t.id, false,
                            t.trimStartMs, t.trimEndMs, t.trimManual
                        )
                    ),
                    queueTag = "anthem"
                )
                remote.refreshPendingRemoteAsync(plan.pendingTracks)
            }
            is com.saidjte.schoolradio.core.util.OpeningAnthemLogic.Decision.Hold ->
                d.reason?.let { _broadcastMessage.value = it }
        }
    }

    /**
     * v0.51.0: بدء تسجيل البث الكامل (المايك + كل ما يشغله التطبيق بمستوياتها
     * الفعلية على الهواء) عبر ناقل التابات مباشرة — بلا MediaProjection ولا
     * حوار نظام (جذر بلاغَي v0.50.0: المنصة تستثني صوت التطبيق نفسه من
     * AudioPlaybackCapture، والتطبيق لا يحتاج إذن أحد ليأخذ نسخته من صوته).
     */
    fun startBroadcastRecording(onError: (String) -> Unit) {
        val err = recordings.startMixRecording(mixBus)
        if (err != null) onError(err)
    }

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
                        // v0.16.0: الخفض نسبة بقاء تُضرب في فيدَر الموسيقى × الماستر —
                        // سحب الفيدَر أثناء الإعلان يستجيب فوراً، والاستعادة تقرأ الإعدادات
                        // دائماً (لا لقطة قديمة تُمسك قبل سحب المستخدم)
                        val base = s.defaultVolume.coerceIn(0f, 1f) * s.masterVolume.coerceIn(0f, 1f)
                        playback.setVolume(if (duck) base * s.duckVolume.coerceIn(0f, 1f) else base)
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
                    // v0.15.0: على خيط خلفي — كان يستدعى من الخيط الرئيسي وينتظر
                    // خيط التسجيل حتى 400ms، وأي استثناء فيه كان يغلق التطبيق
                    com.saidjte.schoolradio.core.util.CrashGuard.runOnIo {
                        recordings.onLiveBroadcastStopped()
                    }
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

    // ================== بوابات التشغيل الموحّدة ==================

    /**
     * v0.41.0 — التوحيد الجذري الذي طلبه الأستاذ حرفياً: «لماذا لا تستعمل
     * المشغل الذي يعمل في المكتبة» — بعد أن صدّر تسجيلاً واستورده في المكتبة
     * فاشتغل، بينما أرشيف التسجيلات أظهر «تعذر بدء التشغيل»، ثبت أن العطل
     * في المشغل المعزول الثاني وبوابة الحَكَم لا في الملفات.
     *
     * فصار تشغيل التسجيلات هنا: عبر مشغل المكتبة نفسه (خدمة أمامية بإشعار
     * وسائط) — المحرك المجرَّب ذاته وبناء MediaItem ذاته المستعمل للفقرات
     * المجدولة من التسجيلات (وهي تعمل عبره أصلاً منذ v0.20.0).
     *
     * بقي من البوابة القديمة الصدق فقط: ملف مفقود أو تالف = رسالة السبب
     * الحقيقي قبل الإطلاق (منطق v0.39 كما هو)، ونفس التسجيل = تبديل تشغيل/إيقاف.
     *
     * @return null عند النجاح أو رسالة عربية جاهزة للعرض.
     */
    fun playRecording(recording: com.saidjte.schoolradio.data.local.entity.RecordingEntity): String? {
        val file = java.io.File(recording.filePath)
        if (!file.exists()) {
            // ملف مفقود (استعادة نسخة على جهاز آخر مثلاً) — رسالة واضحة بدل صمت مريب
            return "ملف هذا التسجيل غير موجود على الجهاز — ربما حُذف أو نُقل"
        }
        // v0.39.0: الصدق البنيوي — ملف تالف من إصدار قديم (كاتب الحاويات قبل
        // إصلاح v0.38.0) لا يُسلَّم لمشغل فك الترميز أصلاً: رسالة السبب الحقيقي.
        val integrity = RecordingIntegrityLogic.classify(
            exists = true,
            fileSize = file.length(),
            head = RecordingIntegrityLogic.readFileHead(file),
            mimeType = recording.mimeType
        )
        if (integrity == RecordingIntegrityLogic.State.CORRUPT) {
            return RecordingIntegrityLogic.corruptMessage()
        }
        // نفس التسجيل قيد التشغيل/المتوقف مؤقتاً؟ تبديل تشغيل/إيقاف كالعادة
        val pb = playback.state.value
        if (pb.currentIsRecording && pb.currentTrackId == recording.id) {
            playback.playPause()
            return null
        }
        playMainQueue(
            listOf(
                PlaybackConnection.QueueItem(
                    recording.filePath, recording.title, recording.id,
                    false, recording.trimStartMs, recording.trimEndMs,
                    recording.trimManual, isRecording = true
                )
            ),
            queueTag = "recording"
        )
        return null
    }

    /**
     * بوابة القائمة الرئيسية (مكتبة/قوائم/تسجيلات/فقرات مجدولة/تشغيل يدوي) —
     * v0.41.0: مصدر صوت واحد في التطبيق كله فلا حاجة لأي حَكَم عزل قبلها.
     */
    fun playMainQueue(
        items: List<PlaybackConnection.QueueItem>,
        startIndex: Int = 0,
        repeatAll: Boolean = false,
        queueTag: String? = null,
        transitionDelayMs: Long = 0L
    ) {
        if (items.isEmpty()) return
        playback.playQueue(items, startIndex, repeatAll, queueTag, transitionDelayMs)
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
                    ?.let {
                        listOf(
                            BreaksLogic.PlaySpec(
                                // v0.35.0: URI المخزن فوراً — التجديد الخلفي يتكفل بالبعد
                                it.uri, it.title, it.id,
                                it.trimStartMs, it.trimEndMs, it.trimManual
                            )
                        )
                    }
                    ?: emptyList()
            schedule.breakCategoryId != null ->
                library.tracks.first()
                    .filter { it.categoryId == schedule.breakCategoryId && it.isAvailable }
                    .map {
                        BreaksLogic.PlaySpec(
                            // v0.35.0: URI المخزن فوراً — التجديد الخلفي يتكفل بالبعد
                            it.uri, it.title, it.id,
                            it.trimStartMs, it.trimEndMs, it.trimManual
                        )
                    }
            else -> emptyList()
        }

        // v0.35.0: سرعة الانطلاق — حلّ شبكي فوري للمقطع البعيد الأول فقط (صحة أول صوت)،
        // وبقية المقاطع البعيدة تُجدَّد في الخلفية وتحل أماكنها في القائمة الحية أثناء الاستماع
        val firstRemoteSpec = segments.flatten()
            .firstOrNull { library.trackById(it.trackId)?.isRemote == true }
        var launchSegments = segments
        var resolvedFirstId = -1L
        if (firstRemoteSpec != null) {
            val ent = library.trackById(firstRemoteSpec.trackId)
            if (ent != null) {
                val fresh = try { remote.playableUriFor(ent) } catch (_: Exception) { ent.uri }
                launchSegments = segments.map { list ->
                    list.map { if (it.trackId == firstRemoteSpec.trackId) it.copy(uri = fresh) else it }
                }
                resolvedFirstId = ent.id
            }
        }
        val pendingTracks = (launchSegments.flatten() + pool).map { it.trackId }
            .distinct()
            .filter { it != resolvedFirstId }
            .mapNotNull { library.trackById(it) }
            .filter { it.isRemote }
        remote.refreshPendingRemoteAsync(pendingTracks)

        // 3) بناء القائمة المتداخلة الموسومة (الواصلة تعرف محرك الحدود مواضع الجينل)
        val tagged = BreaksLogic.buildQueueTagged(
            segments = launchSegments,
            config = BreaksLogic.BreakConfig(
                enabled = schedule.breakEnabled,
                trackId = schedule.breakTrackId,
                categoryId = schedule.breakCategoryId,
                transitionSec = schedule.breakTransitionSec,
                everyTrack = schedule.breakEveryTrack
            ),
            breakPool = pool
        )
        val queue = tagged.map { it.spec }
        if (queue.isEmpty()) return "لا يوجد محتوى قابل للتشغيل في «${schedule.name}»"

        // 4) الإطلاق عبر البوابة الموحدة + شاشة العرض تعرض البرنامج كاملاً
        // v0.15.0: العناصر الموسومة isBreak + مهلة الانتقال تُمرر لمحرك الحدود
        playMainQueue(
            tagged.map {
                PlaybackConnection.QueueItem(
                    it.spec.uri, it.spec.title, it.spec.trackId,
                    it.isBreak, it.spec.trimStartMs,
                    it.spec.trimEndMs, it.spec.manualTrim
                )
            },
            queueTag = "breaks:${schedule.id}",
            transitionDelayMs = schedule.breakTransitionSec * 1000L
        )
        setOnAir(
            OnAirInfo(
                eventId = events.first().id,
                eventTitle = "برنامج: ${schedule.name}",
                noteText = if (schedule.breakEnabled && pool.isNotEmpty())
                    if (schedule.breakEveryTrack) "تشغيل متواصل — جينل بعد كل مقطع" else "تشغيل متواصل مع فواصل آلية بين الفقرات"
                    else "تشغيل متواصل للفقرات",
                imagePath = null
            )
        )
        val breakNote = when {
            !schedule.breakEnabled -> "بلا فواصل (معطّلة في إعداد الجدول)"
            pool.isEmpty() -> "الفواصل مفعّلة لكن لا جينغل متاح — فحص إعداد الفواصل"
            schedule.breakEveryTrack -> "جينل بعد كل مقطع"
            else -> "مع فواصل آلية بين الفقرات"
        }
        val transitionNote = if (schedule.breakEnabled && pool.isNotEmpty() && schedule.breakTransitionSec > 0)
            " • مهلة انتقال ${schedule.breakTransitionSec}ث" else ""
        val skippedNote = if (skipped > 0) " • تخطيت $skipped فقرة بلا محتوى" else ""
        return "انطلق «${schedule.name}» (${queue.size} مقطع) — $breakNote$transitionNote$skippedNote"
    }

    /** v0.12.0: تحليل محتوى فقرة إلى عناصر تشغيل — null إذا كان غير متاح */
    private suspend fun resolveEventContent(e: ScheduledEventEntity): List<BreaksLogic.PlaySpec>? = when {
        e.playlistId != null ->
            playlists.resolvedTracks(e.playlistId)
                .map {
                    BreaksLogic.PlaySpec(
                        // v0.35.0: URI المخزن فوراً — التجديد الخلفي يتكفل بالبعد
                        it.uri, it.title, it.id,
                        it.trimStartMs, it.trimEndMs, it.trimManual
                    )
                }
                .takeIf { it.isNotEmpty() }
        e.recordingId != null ->
            recordingsRepo.byId(e.recordingId)
                ?.takeIf { java.io.File(it.filePath).exists() }
                ?.let { listOf(BreaksLogic.PlaySpec(it.filePath, it.title, it.id)) }
        e.singleTrackId != null ->
            library.trackById(e.singleTrackId)
                ?.takeIf { it.isAvailable }
                ?.let {
                    listOf(
                        BreaksLogic.PlaySpec(
                            // v0.35.0: URI المخزن فوراً — التجديد الخلفي يتكفل بالبعد
                            it.uri, it.title, it.id,
                            it.trimStartMs, it.trimEndMs, it.trimManual
                        )
                    )
                }
        else -> null
    }

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            appContext = context.applicationContext
            db = AppDatabase.get(appContext)
            settings = SettingsStore(appContext)
            library = LibraryRepository(appContext, db)
            // v0.23.0: خدمة الاستيراد من الإنترنت — تحتاج المكتبة لتحليل الفراغ بعد التنزيل
            remote = com.saidjte.schoolradio.data.remote.RemoteMediaService(appContext, db, library)
            playlists = PlaylistRepository(db)
            schedules = ScheduleRepository(db)
            backup = BackupRepository(appContext, db, settings)
            recordingsRepo = RecordingRepository(db.recordingDao())
            session = SessionRepository(settings, db.userDao())
            routing = AudioRoutingManager(appContext)
            playback = PlaybackConnection(appContext)
            // v0.35.0: جسر الإحلال الحي — التجديد الخلفي للروابط البعيدة يستبدل عناصر القائمة الحية
            remote.onFreshUri = { trackId, freshUri -> playback.replaceItemUri(trackId, freshUri) }
            // v0.42.0: بعد اكتمال التنزيل يتبنى المشغل الملف المحلي فوراً —
            // التشغيل يصير من الملف لا من المصدر (العنصر الجاري بنفس الموضع)
            remote.onLocalFileAdopted = { trackId, localUri -> playback.adoptLocalFile(trackId, localUri) }
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
                isLiveProvider = { _micLive.value },
                hdPreferredProvider = { recordingHdPreferred.value }
            )
            // v0.9.0: تحميل إعدادات الاستوديو المحفوظة مسبقاً (كسب الميك + مستوى الموسيقى)
            // + استرجاع المؤثرات فور توفر أول جلسة صوتية
            // v0.47.0: على Main — applyStartupVolume قد تلمس MediaController المتصل
            // مباشرة (c.volume) فكانت سباقاً مع جاهزية الخدمة يرمي «wrong thread»
            appScope.launch(Dispatchers.Main) {
                runCatching {
                    val s = settings.settings.first()
                    pendingMicGain = s.micGain
                    mic.setMicGain(s.micGain)
                    // v0.15.0: مستوى الموسيقى الفعّال = الفيدَر × الماستر
                    playback.applyStartupVolume(s.defaultVolume * s.masterVolume)
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
                    // v0.18.0: لقطة حية لإعداد قص الفراغ الأول
                    trimSilenceEnabled.value = s.trimSilence
                    // v0.36.0: لقطة حية لتفضيل Opus HD
                    recordingHdPreferred.value = s.recordingHd
                }
            }
            initialized = true
        }
    }
}

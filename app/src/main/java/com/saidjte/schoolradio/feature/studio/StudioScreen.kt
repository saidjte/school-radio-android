package com.saidjte.schoolradio.feature.studio

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saidjte.schoolradio.audio.AudioEffects
import com.saidjte.schoolradio.audio.AudioRoutingManager
import com.saidjte.schoolradio.audio.MicPhase
import com.saidjte.schoolradio.core.theme.status
import com.saidjte.schoolradio.core.ui.AppIcons
import com.saidjte.schoolradio.core.ui.GradientButton
import com.saidjte.schoolradio.core.ui.ScreenHeader
import com.saidjte.schoolradio.core.ui.StatusBadge
import com.saidjte.schoolradio.core.ui.driftingGradient
import com.saidjte.schoolradio.core.util.MixerLogic
import com.saidjte.schoolradio.data.settings.AppSettings
import com.saidjte.schoolradio.di.ServiceLocator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/**
 * شاشة الاستوديو (v0.9.0) — وحدة تحكم إذاعية احترافية:
 *
 * 1. تحكم البث المباشر: بدء/إنهاء الميكروفون مع اختيار مسار الإخراج.
 * 2. وحدة المزج (Mixer): فيدَرَان عموديان (ميكروفون 0..200٪ / موسيقى 0..100٪)
 *    بمؤشري مستوى حيّين (VU) بقمم ممتدة — مع الخفض التلقائي (Ducking).
 * 3. معادل الصوت (Equalizer): شرائح تردد الجهاز + إعدادات مسبقة مترجمة
 *    + تعزيز الباص والمؤثر المحيطي — يُطبَّق على كل مشغلي التطبيق.
 *
 * كل الإعدادات تُحفظ محلياً وتُسترجع عند تشغيل التطبيق.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudioScreen(
    snackbar: androidx.compose.material3.SnackbarHostState,
    onOpenCartwall: () -> Unit = {}
) {
    val locator = ServiceLocator
    val scope = rememberCoroutineScope()
    val mic = locator.mic

    val micPhase by mic.phase.collectAsStateWithLifecycle()
    val micGain by mic.micGain.collectAsStateWithLifecycle()
    val micLevelRaw by locator.micLevel.collectAsStateWithLifecycle()
    val mediaLevelRaw by locator.mediaLevel.collectAsStateWithLifecycle()
    val playbackState by locator.playback.state.collectAsStateWithLifecycle()
    val fx by AudioEffects.state.collectAsStateWithLifecycle()
    val settings by locator.settings.settings
        .collectAsStateWithLifecycle(initialValue = AppSettings())

    var route by remember { mutableStateOf(AudioRoutingManager.Route.SPEAKER) }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        scope.launch {
            if (granted) {
                mic.start(route) { err -> err?.let { m -> scope.launch { snackbar.showSnackbar(m) } } }
            } else {
                snackbar.showSnackbar("بدون صلاحية الميكروفون لا يمكن البث المباشر")
            }
        }
    }

    fun startStopMic() {
        when (micPhase) {
            MicPhase.LIVE, MicPhase.STARTING -> mic.stop()
            MicPhase.IDLE, MicPhase.STOPPING -> {
                if (mic.hasPermission()) {
                    mic.start(route) { err -> err?.let { m -> scope.launch { snackbar.showSnackbar(m) } } }
                } else {
                    micPermission.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
    }

    /** حفظ تكوين المعادل الحالي (الإعداد المسبق + كسب الشرائح) */
    fun persistEq() {
        val s = fx
        val gains = s.bands.joinToString("|") { String.format(Locale.US, "%.2f", it.gainDb) }
        scope.launch {
            locator.settings.setEqConfig(s.selectedPreset?.toInt() ?: -1, gains)
            locator.settings.setEqEnabled(s.enabled)
            locator.settings.setBassStrength(s.bassStrength)
            locator.settings.setVirtStrength(s.virtStrength)
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenHeader(
                title = "الاستوديو",
                subtitle = "مزج الميكروفون والموسيقى وضبط الصوت — أسلوب الإذاعات الاحترافية"
            )
        }

        // ===== تحكم البث المباشر =====
        item {
            BroadcastCard(
                phase = micPhase,
                route = route,
                onRouteChange = { route = it },
                onStartStop = ::startStopMic
            )
        }

        // ===== v0.15.0: وحدة المزج الاحترافية (4 قنوات + ماستر + مشاهد) =====
        item {
            MixerConsole(
                micFader = micGain,
                micLevel = micLevelRaw,
                musicFader = settings.defaultVolume,
                mediaLevel = mediaLevelRaw,
                breaksFader = settings.cartVolume,
                previewFader = settings.previewVolume,
                masterFader = settings.masterVolume,
                duckEnabled = settings.duckEnabled,
                duckVolume = settings.duckVolume,
                onMicFader = { mic.setMicGain(it) },
                onMicFaderFinished = { v -> scope.launch { locator.settings.setMicGain(v) } },
                onMusicFader = { locator.playback.setVolume(it) },
                onMusicFaderFinished = { v -> scope.launch { locator.settings.setDefaultVolume(v) } },
                onBreaksFader = { locator.cartPlayer.setVolume(it) },
                onBreaksFaderFinished = { v -> scope.launch { locator.settings.setCartVolume(v) } },
                onPreviewFader = { locator.recordingsPlayer.setVolume(it) },
                onPreviewFaderFinished = { v -> scope.launch { locator.settings.setPreviewVolume(v) } },
                onMasterFader = { v ->
                    scope.launch { locator.settings.setMasterVolume(v) }
                    locator.applyMixerVolumes(master = v)
                },
                onMasterFaderFinished = { locator.applyMixerVolumes() },
                onDuckEnabled = { scope.launch { locator.settings.setDuckEnabled(it) } },
                onDuckVolume = { scope.launch { locator.settings.setDuckVolume(it) } },
                onScene = { scene ->
                    mic.setMicGain(scene.set.mic)
                    locator.applyMixerVolumes(
                        musicFader = scene.set.music,
                        master = scene.set.master,
                        previewFader = scene.set.preview,
                        cartFader = scene.set.breaks
                    )
                    scope.launch {
                        locator.settings.setMicGain(scene.set.mic)
                        locator.settings.setDefaultVolume(scene.set.music)
                        locator.settings.setCartVolume(scene.set.breaks)
                        locator.settings.setPreviewVolume(scene.set.preview)
                        locator.settings.setMasterVolume(scene.set.master)
                        locator.settings.setDuckEnabled(scene.set.duckEnabled)
                        locator.settings.setDuckVolume(scene.set.duckVolume)
                    }
                }
            )
        }

        // ===== v0.11.0: معالجة صوت المذيع =====
        item {
            MicProcessingCard(
                agc = settings.micAgc,
                noiseGate = settings.micNoiseGate,
                echoCancel = settings.micEchoCancel,
                onAgc = { scope.launch { locator.settings.setMicAgc(it) } },
                onNoiseGate = { scope.launch { locator.settings.setMicNoiseGate(it) } },
                onEchoCancel = { scope.launch { locator.settings.setMicEchoCancel(it) } }
            )
        }

        // ===== معادل الصوت =====
        item {
            EqualizerCard(
                fx = fx,
                onEnabled = { AudioEffects.setEnabled(it); persistEq() },
                onPreset = { AudioEffects.setPreset(it); persistEq() },
                onBand = { index, db -> AudioEffects.setBandGainDb(index, db) },
                onBandFinished = { persistEq() },
                onBass = { AudioEffects.setBassStrength(it) },
                onBassFinished = { persistEq() },
                onVirt = { AudioEffects.setVirtualizerStrength(it) },
                onVirtFinished = { persistEq() },
                onReset = {
                    fx.bands.forEach { b -> AudioEffects.setBandGainDb(b.index, 0f) }
                    AudioEffects.setBassStrength(0f)
                    AudioEffects.setVirtualizerStrength(0f)
                    persistEq()
                }
            )
        }

        item {
            Text(
                "ملاحظة احترافية: البث المباشر له الأولوية دائماً — عند بدء الإعلان " +
                    "تتوقف المعاينات ويُخفَض مستوى الموسيقى تلقائياً ثم تُعاد بعد الإنهاء. " +
                    "أما حائط الجينغل فيعمل فوق كل شيء عمداً — النشيد خلف صوت المذيع سلوك إذاعي طبيعي.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start
            )
        }

        // ===== v0.11.0: وصول سريع لحائط الجينغل =====
        item {
            GradientButton(
                label = "افتح حائط الجينغل",
                icon = AppIcons.MusicNote,
                onClick = onOpenCartwall
            )
        }

        item { Spacer(Modifier.height(70.dp)) }
    }
}

// =====================================================================
// بطاقة تحكم البث المباشر
// =====================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BroadcastCard(
    phase: MicPhase,
    route: AudioRoutingManager.Route,
    onRouteChange: (AudioRoutingManager.Route) -> Unit,
    onStartStop: () -> Unit
) {
    val st = MaterialTheme.status
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    AppIcons.Mic, null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text("البث المباشر", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "اضغط للتحدث — زر المذيع",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusBadge(
                    label = when (phase) {
                        MicPhase.IDLE -> "جاهز"
                        MicPhase.STARTING -> "جارٍ البدء…"
                        MicPhase.LIVE -> "مباشر الآن"
                        MicPhase.STOPPING -> "جارٍ الإيقاف…"
                    },
                    color = when (phase) {
                        MicPhase.LIVE -> st.live
                        MicPhase.IDLE -> st.running
                        else -> st.warning
                    }
                )
            }

            // مسارات الإخراج — نفس رقائق الرئيسية بأيقوناتها
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val routes = listOf(
                    AudioRoutingManager.Route.SPEAKER to AppIcons.VolumeUp,
                    AudioRoutingManager.Route.WIRED to AppIcons.Headphones,
                    AudioRoutingManager.Route.BLUETOOTH to AppIcons.Bluetooth,
                    AudioRoutingManager.Route.MEDIA_DEFAULT to AppIcons.Autorenew
                )
                routes.forEach { (r, icon) ->
                    FilterChip(
                        selected = route == r,
                        onClick = { onRouteChange(r) },
                        label = { Text(r.labelAr) },
                        leadingIcon = { Icon(icon, null, Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }

            val busy = phase == MicPhase.STARTING || phase == MicPhase.STOPPING
            if (phase == MicPhase.LIVE || phase == MicPhase.STARTING) {
                // زر إنهاء — بلون خطأ دلالي
                androidx.compose.material3.Surface(
                    onClick = { if (!busy) onStartStop() },
                    enabled = !busy,
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (busy) "جارٍ الإيقاف…" else "إنهاء البث",
                        Modifier.padding(vertical = 13.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            } else {
                GradientButton(
                    label = if (busy) "جارٍ البدء…" else "ابدأ البث المباشر",
                    icon = AppIcons.Mic,
                    enabled = !busy,
                    onClick = onStartStop
                )
            }
        }
    }
}

// =====================================================================
// v0.11.0: بطاقة معالجة صوت المذيع — سلسلة التنظيف القياسية للإذاعات
// =====================================================================

@Composable
private fun MicProcessingCard(
    agc: Boolean,
    noiseGate: Boolean,
    echoCancel: Boolean,
    onAgc: (Boolean) -> Unit,
    onNoiseGate: (Boolean) -> Unit,
    onEchoCancel: (Boolean) -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.GraphicEq, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.padding(horizontal = 10.dp)) {
                    Text("معالجة صوت المذيع", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "سلسلة التنظيف القياسية في الإذاعات — تُطبّق عند بدء البث التالي",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            ProcessingSwitch(
                title = "توحيد الكسب تلقائياً (AGC)",
                subtitle = "يرفع الصوت الخافت ويخفض الصوت العالي — قناة ميك متوازنة مهما تغيّر بُعد المذيع",
                checked = agc,
                onChange = onAgc
            )
            ProcessingSwitch(
                title = "حجز الضجيج الخلفي",
                subtitle = "يكتم هسهسة القاعة وصوت الحركة أثناء الإعلان",
                checked = noiseGate,
                onChange = onNoiseGate
            )
            ProcessingSwitch(
                title = "إلغاء الصدى (AEC)",
                subtitle = "يمنع صفير التغذية الراجعة عندما تكون مكبرات القاعة قريبة من الهاتف",
                checked = echoCancel,
                onChange = onEchoCancel
            )
        }
    }
}

@Composable
private fun ProcessingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// =====================================================================
// v0.15.0 — وحدة المزج الاحترافية: 4 قنوات + ماستر + مشاهد جاهزة
// =====================================================================

/**
 * لوحة المزج بأسلوب الاستوديوهات الإذاعية:
 *  - قناة الميكروفون (كسب 0..200٪ + VU حي + كتم).
 *  - قناة الموسيقى (فيدَر + VU حي + كتم) — تمثل مشغل المكتبة/القوائم/الفقرات.
 *  - قناة الجينل (فيدَر + كتم) — حائط الرقائق والفواصل.
 *  - قناة المعاينة (فيدَر + كتم) — استماع أرشيف التسجيلات.
 *  - الماستر: يضرب القنوات الموسيقية الثلاث (لا يمس الميك — خط مباشر).
 *  - الخفض التلقائي أثناء الإعلان (Ducking) كما كان.
 *  - مشاهد جاهزة: ضغطة واحدة تنقل كل الفيدرات لوضع إذاعي مُجرَّب.
 *
 * الكتم (M) مؤقت كأجهزة الاستوديو: يحفظ الفيدَر تحته ويعود لحظة رفعه،
 * ولا يُكتب في الإعدادات — الإعدادات تحفظ الفيدرات لا حالة الكتم.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MixerConsole(
    micFader: Float,
    micLevel: Float,
    musicFader: Float,
    mediaLevel: Float,
    breaksFader: Float,
    previewFader: Float,
    masterFader: Float,
    duckEnabled: Boolean,
    duckVolume: Float,
    onMicFader: (Float) -> Unit,
    onMicFaderFinished: (Float) -> Unit,
    onMusicFader: (Float) -> Unit,
    onMusicFaderFinished: (Float) -> Unit,
    onBreaksFader: (Float) -> Unit,
    onBreaksFaderFinished: (Float) -> Unit,
    onPreviewFader: (Float) -> Unit,
    onPreviewFaderFinished: (Float) -> Unit,
    onMasterFader: (Float) -> Unit,
    onMasterFaderFinished: () -> Unit,
    onDuckEnabled: (Boolean) -> Unit,
    onDuckVolume: (Float) -> Unit,
    onScene: (MixerLogic.Scene) -> Unit
) {
    // الكتم جلسي — لا يُحفظ (سلوك لوحات المزج الحقيقية)
    var micMuted by remember { mutableStateOf(false) }
    var musicMuted by remember { mutableStateOf(false) }
    var breaksMuted by remember { mutableStateOf(false) }
    var previewMuted by remember { mutableStateOf(false) }

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Tune, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text("وحدة المزج", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "أربع قنوات + ماستر — كما في لوحات الاستوديوهات الإذاعية",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ===== المشاهد الجاهزة =====
            Text(
                "مشاهد جاهزة",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MixerLogic.Scene.entries.forEach { scene ->
                    FilterChip(
                        selected = false,
                        onClick = { onScene(scene) },
                        label = { Text(scene.labelAr) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
            Text(
                "المستوى المسموع = فيدَر القناة × الماستر • الكتم مؤقت ولا يمس الفيدَر",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ===== صف القنوات: ميك + موسيقى + جينل + معاينة + ماستر =====
            Row(
                Modifier.fillMaxWidth().height(250.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ---- قناة الميكروفون ----
                ChannelStrip(
                    modifier = Modifier.weight(1.2f),
                    channel = MixerLogic.Channel.MIC,
                    fader = micFader,
                    faderFraction = micFader / 2f,
                    muted = micMuted,
                    hasVu = true,
                    vuLevel = micLevel,
                    obeysMaster = false,
                    onFader = { raw ->
                        val v = MixerLogic.clampFader(MixerLogic.Channel.MIC, raw)
                        onMicFader(if (micMuted) 0f else v)
                    },
                    onFaderFinished = { onMicFaderFinished(micFader) },
                    onToggleMute = {
                        micMuted = !micMuted
                        onMicFader(if (micMuted) 0f else micFader)
                    }
                )

                StripDivider()

                // ---- قناة الموسيقى ----
                ChannelStrip(
                    modifier = Modifier.weight(1.2f),
                    channel = MixerLogic.Channel.MUSIC,
                    fader = musicFader,
                    faderFraction = musicFader,
                    muted = musicMuted,
                    hasVu = true,
                    vuLevel = mediaLevel,
                    obeysMaster = true,
                    onFader = { raw ->
                        val v = MixerLogic.clampFader(MixerLogic.Channel.MUSIC, raw)
                        onMusicFader(if (musicMuted) 0f else v * masterFader)
                    },
                    onFaderFinished = { onMusicFaderFinished(musicFader) },
                    onToggleMute = {
                        musicMuted = !musicMuted
                        onMusicFader(if (musicMuted) 0f else musicFader * masterFader)
                    }
                )

                StripDivider()

                // ---- قناة الجينل ----
                ChannelStrip(
                    modifier = Modifier.weight(1.2f),
                    channel = MixerLogic.Channel.BREAKS,
                    fader = breaksFader,
                    faderFraction = breaksFader,
                    muted = breaksMuted,
                    hasVu = false,
                    vuLevel = 0f,
                    obeysMaster = true,
                    onFader = { raw ->
                        val v = MixerLogic.clampFader(MixerLogic.Channel.BREAKS, raw)
                        onBreaksFader(if (breaksMuted) 0f else v * masterFader)
                    },
                    onFaderFinished = { onBreaksFaderFinished(breaksFader) },
                    onToggleMute = {
                        breaksMuted = !breaksMuted
                        onBreaksFader(if (breaksMuted) 0f else breaksFader * masterFader)
                    }
                )

                StripDivider()

                // ---- قناة المعاينة ----
                ChannelStrip(
                    modifier = Modifier.weight(1.2f),
                    channel = MixerLogic.Channel.PREVIEW,
                    fader = previewFader,
                    faderFraction = previewFader,
                    muted = previewMuted,
                    hasVu = false,
                    vuLevel = 0f,
                    obeysMaster = true,
                    onFader = { raw ->
                        val v = MixerLogic.clampFader(MixerLogic.Channel.PREVIEW, raw)
                        onPreviewFader(if (previewMuted) 0f else v * masterFader)
                    },
                    onFaderFinished = { onPreviewFaderFinished(previewFader) },
                    onToggleMute = {
                        previewMuted = !previewMuted
                        onPreviewFader(if (previewMuted) 0f else previewFader * masterFader)
                    }
                )

                StripDivider()

                // ---- الماستر ----
                Column(
                    Modifier.weight(1.15f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    VerticalFader(
                        value = masterFader,
                        onValueChange = { onMasterFader(it) },
                        onFinished = onMasterFaderFinished,
                        label = "الماستر",
                        valueText = "${(masterFader * 100).roundToInt()}٪",
                        modifier = Modifier.weight(1f),
                        accent = Color(0xFFE8B33C),
                        showValueInside = true
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // ===== الخفض التلقائي (Ducking) =====
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("خفض الموسيقى تلقائياً أثناء الإعلان", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "عند بدء البث ينخفض مستوى الموسيقى ثم يعود بعد الإنهاء",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = duckEnabled, onCheckedChange = onDuckEnabled)
            }
            if (duckEnabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "مستوى الخفض: ${(duckVolume * 100).roundToInt()}٪",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.width(120.dp)
                    )
                    Slider(
                        value = duckVolume,
                        onValueChange = { onDuckVolume(it) },
                        valueRange = 0f..0.6f,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/** فاصل عمودي رقيق بين قنوات اللوحة */
@Composable
private fun StripDivider() {
    Box(
        Modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    )
}

/**
 * قناة واحدة في اللوحة: (VU اختياري) + فيدَر + زر كتم M.
 * [onFader] يستقبل القيمة الخام 0..1 من الإيماءة ويحوّلها المستدعي لدلالة قناته.
 */
@Composable
private fun ChannelStrip(
    channel: MixerLogic.Channel,
    fader: Float,
    faderFraction: Float,
    muted: Boolean,
    hasVu: Boolean,
    vuLevel: Float,
    obeysMaster: Boolean,
    onFader: (Float) -> Unit,
    onFaderFinished: () -> Unit,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier
) {
    val st = MaterialTheme.status
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (hasVu) {
                VuMeter(
                    level = if (muted) 0f else vuLevel,
                    label = "",
                    modifier = Modifier.weight(1f)
                )
            }
            VerticalFader(
                value = faderFraction,
                onValueChange = onFader,
                onFinished = onFaderFinished,
                label = "",
                valueText = MixerLogic.faderText(channel, if (muted) 0f else fader),
                modifier = if (hasVu) Modifier.weight(1.1f) else Modifier.fillMaxWidth(),
                accent = if (muted) MaterialTheme.colorScheme.outlineVariant
                else MaterialTheme.colorScheme.primary,
                showValueInside = true
            )
        }
        // زر الكتم M — أحمر عند الكتم كما في اللوحات الحقيقية
        Surface(
            onClick = onToggleMute,
            shape = MaterialTheme.shapes.small,
            color = if (muted) st.live.copy(alpha = 0.85f)
            else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        ) {
            Text(
                "M",
                Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                color = if (muted) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            channel.labelAr,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

// =====================================================================
// معادل الصوت
// =====================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EqualizerCard(
    fx: AudioEffects.AudioEffectsState,
    onEnabled: (Boolean) -> Unit,
    onPreset: (Short) -> Unit,
    onBand: (Short, Float) -> Unit,
    onBandFinished: () -> Unit,
    onBass: (Float) -> Unit,
    onBassFinished: () -> Unit,
    onVirt: (Float) -> Unit,
    onVirtFinished: () -> Unit,
    onReset: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.GraphicEq, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text("معادل الصوت (Equalizer)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (fx.available) "يعمل على كل مشغلي التطبيق — مكتبة، تسجيلات، فقرات"
                        else "مؤثرات الصوت غير متاحة على هذا الجهاز",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = fx.enabled,
                    onCheckedChange = onEnabled,
                    enabled = fx.available
                )
            }

            if (fx.available) {
                // الإعدادات المسبقة
                if (fx.presets.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        fx.presets.forEach { p ->
                            FilterChip(
                                selected = fx.selectedPreset == p.id,
                                onClick = { onPreset(p.id) },
                                label = { Text(p.name) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }
                    }
                }

                // شرائح الترددات — عمودية كأجهزة الستيريو
                Row(
                    Modifier.fillMaxWidth().height(185.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    fx.bands.forEach { band ->
                        val range = (band.maxDb - band.minDb).coerceAtLeast(0.01f)
                        Column(
                            Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                dbText(band.gainDb),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            VerticalFader(
                                value = (band.gainDb - band.minDb) / range,
                                onValueChange = { f -> onBand(band.index, band.minDb + f * range) },
                                onFinished = onBandFinished,
                                label = band.freqLabel,
                                valueText = band.freqLabel,
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                accent = MaterialTheme.colorScheme.primary,
                                showValueInside = false
                            )
                        }
                    }
                }

                // تعزيز الباص والمؤثر المحيطي (إن دعمهما الجهاز)
                if (fx.bassSupported) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "تعزيز الباص: ${(fx.bassStrength * 100).roundToInt()}٪",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.width(130.dp)
                        )
                        Slider(
                            value = fx.bassStrength,
                            onValueChange = onBass,
                            onValueChangeFinished = onBassFinished,
                            valueRange = 0f..1f,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                if (fx.virtSupported) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "مؤثر محيطي: ${(fx.virtStrength * 100).roundToInt()}٪",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.width(130.dp)
                        )
                        Slider(
                            value = fx.virtStrength,
                            onValueChange = onVirt,
                            onValueChangeFinished = onVirtFinished,
                            valueRange = 0f..1f,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                TextButton(onClick = onReset) {
                    Text("تصفير الإعدادات")
                }
            }
        }
    }
}

/** نص الديسيبل بإشارة صريحة وأرقام مغربية */
private fun dbText(db: Float): String = when {
    db > 0.05f -> "+" + String.format(Locale.US, "%.1f", db)
    db < -0.05f -> String.format(Locale.US, "%.1f", db)
    else -> "0.0"
}

// =====================================================================
// مكوّنات الاستوديو: فيدَر عمودي + مؤشر مستوى VU
// =====================================================================

/**
 * فيدَر عمودي بلمسة استوديو — مسار بتدرج + مقبض بخط مركزي.
 * السحب عمودياً والنقر ينقل القيمة مباشرة (القمة = 1.0).
 * [onFinished] يُستدعى عند نهاية السحب لحفظ القيمة.
 */
@Composable
private fun VerticalFader(
    value: Float,
    onValueChange: (Float) -> Unit,
    onFinished: (() -> Unit)?,
    label: String,
    valueText: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    showValueInside: Boolean = true
) {
    val currentValue by rememberUpdatedState(value)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val thumbColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val outline = MaterialTheme.colorScheme.outlineVariant

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (showValueInside) {
            Text(
                valueText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        onValueChange((1f - offset.y / size.height.toFloat()).coerceIn(0f, 1f))
                        onFinished?.invoke()
                    }
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = { onFinished?.invoke() },
                        onDragCancel = { onFinished?.invoke() },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            val h = size.height.toFloat()
                            onValueChange((currentValue - dragAmount / h).coerceIn(0f, 1f))
                        }
                    )
                }
        ) {
            drawFader(
                fraction = value.coerceIn(0f, 1f),
                trackColor = trackColor,
                fillColor = accent,
                thumbColor = thumbColor,
                outline = outline
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            minLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

/** رسم الفيدَر داخل Canvas — أفقي مركزي بمسار 10dp ومقبض عريض */
private fun DrawScope.drawFader(
    fraction: Float,
    trackColor: Color,
    fillColor: Color,
    thumbColor: Color,
    outline: Color
) {
    val w = size.width
    val h = size.height
    val trackW = 10.dp.toPx()
    val pad = 6.dp.toPx()
    val cx = w / 2f
    val trackTop = pad
    val trackH = h - pad * 2

    // المسار
    drawRoundRect(
        color = trackColor,
        topLeft = Offset(cx - trackW / 2, trackTop),
        size = Size(trackW, trackH),
        cornerRadius = CornerRadius(trackW / 2)
    )
    // التعبئة من الأسفل
    val fillH = trackH * fraction
    if (fillH > 1f) {
        drawRoundRect(
            color = fillColor,
            topLeft = Offset(cx - trackW / 2, trackTop + trackH - fillH),
            size = Size(trackW, fillH),
            cornerRadius = CornerRadius(trackW / 2)
        )
    }
    // المقبض
    val thumbW = (w * 0.82f).coerceAtMost(40.dp.toPx())
    val thumbH = 16.dp.toPx()
    val thumbY = trackTop + trackH - fillH
    drawRoundRect(
        color = thumbColor,
        topLeft = Offset(cx - thumbW / 2, thumbY - thumbH / 2),
        size = Size(thumbW, thumbH),
        cornerRadius = CornerRadius(8.dp.toPx())
    )
    drawRoundRect(
        color = outline,
        topLeft = Offset(cx - thumbW / 2, thumbY - thumbH / 2),
        size = Size(thumbW, thumbH),
        cornerRadius = CornerRadius(8.dp.toPx()),
        style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx())
    )
    // خط المركز
    drawLine(
        color = fillColor,
        start = Offset(cx - thumbW * 0.28f, thumbY),
        end = Offset(cx + thumbW * 0.28f, thumbY),
        strokeWidth = 2.5.dp.toPx(),
        cap = StrokeCap.Round
    )
}

/**
 * مؤشر مستوى VU عمودي — تعبئة بتدرج (أخضر ← كهرماني ← أحمر)
 * مع قمة ممتدة تتناقص ببطء كما في مؤشرات الاستوديوهات الحقيقية.
 */
@Composable
private fun VuMeter(
    level: Float,
    label: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary
) {
    val green = Color(0xFF2E9E6B)
    val amber = Color(0xFFE8B33C)
    val red = Color(0xFFD9534F)
    val track = MaterialTheme.colorScheme.surfaceVariant
    val smooth by animateFloatAsState(
        targetValue = level.coerceIn(0f, 1f),
        animationSpec = tween(70),
        label = "vu-level"
    )
    // قمة ممتدة تتسارع صعوداً وتتناقص ببطء
    var peak by remember { mutableStateOf(0f) }
    val smoothState by rememberUpdatedState(smooth)
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            delay(60)
            if (smoothState >= peak) peak = smoothState
            else peak = (peak - 0.012f).coerceAtLeast(smoothState)
        }
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val w = size.width
            val h = size.height
            val barW = (w * 0.55f).coerceAtMost(16.dp.toPx())
            val cx = w / 2f
            val pad = 6.dp.toPx()
            val barH = h - pad * 2
            val top = pad

            // المسار الخامل
            drawRoundRect(
                color = track,
                topLeft = Offset(cx - barW / 2, top),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(barW / 2)
            )
            // التعبئة بتدرج ألوان الحرارة
            val lvl = smooth.coerceIn(0f, 1f)
            if (lvl > 0.005f) {
                val fillH = barH * lvl
                drawRoundRect(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(red, amber, green),
                        startY = top,
                        endY = top + barH
                    ),
                    topLeft = Offset(cx - barW / 2, top + barH - fillH),
                    size = Size(barW, fillH),
                    cornerRadius = CornerRadius(barW / 2)
                )
            }
            // خط القمة
            if (peak > 0.01f) {
                val py = top + barH * (1f - peak.coerceIn(0f, 1f))
                drawLine(
                    color = Color.White.copy(alpha = 0.85f),
                    start = Offset(cx - barW / 2, py),
                    end = Offset(cx + barW / 2, py),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

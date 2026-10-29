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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saidjte.schoolradio.audio.AudioEffects
import com.saidjte.schoolradio.audio.AudioRoutingManager
import com.saidjte.schoolradio.audio.MicPhase
import com.saidjte.schoolradio.core.theme.status
import com.saidjte.schoolradio.core.ui.AppIcons
import com.saidjte.schoolradio.core.ui.EqualizerCard
import com.saidjte.schoolradio.core.ui.GradientButton
import com.saidjte.schoolradio.core.ui.ScreenHeader
import com.saidjte.schoolradio.core.ui.StatusBadge
import com.saidjte.schoolradio.core.ui.driftingGradient
import com.saidjte.schoolradio.core.util.MixerLogic
import com.saidjte.schoolradio.core.util.WaveformLogic
import kotlinx.coroutines.delay
import com.saidjte.schoolradio.data.settings.AppSettings
import com.saidjte.schoolradio.di.ServiceLocator
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/**
 * شاشة الاستوديو (v0.9.0) — وحدة تحكم إذاعية احترافية:
 *
 * 1. تحكم البث المباشر: بدء/إنهاء الميكروفون مع اختيار مسار الإخراج.
 * 2. وحدة المزج (v0.16.0): صفوف أفقية كبيرة سهلة اللمس — سلايدر لكل قناة
 *    (ميكروفون/موسيقى/جينل/معاينة) + أزرار دقة −/+ + كتم مؤقت + مشاهد جاهزة،
 *    والماستر يُضرب لحظياً في القنوات الموسيقية.
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
    // v0.35.0: المستويات الحية لم تعد تُجمَع في رأس الشاشة — مزوّدات تُقرأ وقت العينة
    // داخل أشرطة VU نفسها، وإعادة تركيب شاشة الاستوديو عند كل انبعاث صارت صفراً
    val playbackState by locator.playback.state.collectAsStateWithLifecycle()
    val fx by AudioEffects.state.collectAsStateWithLifecycle()
    val settings by locator.settings.settings
        .collectAsStateWithLifecycle(initialValue = AppSettings())

    var route by remember { mutableStateOf(AudioRoutingManager.Route.SPEAKER) }
    // v0.16.0: عدّاد تطبيق المشاهد — يُبادل وحدة المزج برفع الكتم الجلسي عند كل مشهد
    var sceneTick by remember { mutableIntStateOf(0) }
    // v0.50.0: جلسة البث الحي — زر الإعلان نفسه يتحول إلى إنهاء ما دامت جارية
    val onAir by locator.broadcastOnAir.collectAsStateWithLifecycle()

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

    /** حفظ تكوين المعادل من اللقطة الفعلية (v0.22.0 — بلا تسرب حالة الكومبوزيشن) */
    fun persistEq() {
        val d = AudioEffects.snapshot()
        scope.launch {
            locator.settings.setEqConfig(d.presetId, d.gains)
            locator.settings.setEqEnabled(d.enabled)
            locator.settings.setBassStrength(d.bass)
            locator.settings.setVirtStrength(d.virt)
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
                onStartStop = ::startStopMic,
                // v0.50.0: الإعلان جلسة كاملة — نغمة + شارة في الرئيسية + نشيد آلي
                onAnnounce = {
                    if (locator.broadcastOnAir.value) locator.endBroadcastSession()
                    else locator.announceBroadcastStart()
                },
                onAir = onAir
            )
        }

        // ===== v0.16.0: وحدة المزج — صفوف أفقية سهلة + كتم لا يفسد الفيدَرات =====
        item {
            MixerConsole(
                micFader = micGain,
                micLevelProvider = { locator.micLevel.value },
                musicFader = settings.defaultVolume,
                mediaLevelProvider = { locator.mediaLevel.value },
                breaksFader = settings.cartVolume,
                masterFader = settings.masterVolume,
                duckEnabled = settings.duckEnabled,
                duckVolume = settings.duckVolume,
                duckingActive = micPhase == MicPhase.LIVE && settings.duckEnabled,
                sceneTick = sceneTick,
                onMicVolume = { mic.setMicGain(it) },
                onMusicVolume = { locator.playback.setVolume(it) },
                onCartVolume = { locator.setCartVolumeIfReady(it) },
                onMicFaderFinished = { v -> scope.launch { locator.settings.setMicGain(v) } },
                onMusicFaderFinished = { v -> scope.launch { locator.settings.setDefaultVolume(v) } },
                onBreaksFaderFinished = { v -> scope.launch { locator.settings.setCartVolume(v) } },
                onMasterFaderFinished = { v ->
                    scope.launch { locator.settings.setMasterVolume(v) }
                    locator.applyMixerVolumes()
                },
                onDuckEnabled = { scope.launch { locator.settings.setDuckEnabled(it) } },
                onDuckVolume = { scope.launch { locator.settings.setDuckVolume(it) } },
                onScene = { scene ->
                    sceneTick++
                    mic.setMicGain(scene.set.mic)
                    scope.launch {
                        locator.settings.setMicGain(scene.set.mic)
                        locator.settings.setDefaultVolume(scene.set.music)
                        locator.settings.setCartVolume(scene.set.breaks)
                        locator.settings.setMasterVolume(scene.set.master)
                        locator.settings.setDuckEnabled(scene.set.duckEnabled)
                        locator.settings.setDuckVolume(scene.set.duckVolume)
                        // بعد حفظ الإعدادات: تطبيق فوري للمستويات الفعّالة على المشغلات القائمة
                        locator.applyMixerVolumes(
                            musicVolume = scene.set.music * scene.set.master,
                            cartVolume = scene.set.breaks * scene.set.master
                        )
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
                onRadioPreset = { AudioEffects.applyRadioPreset(it); persistEq() },
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
                    AudioEffects.setPresetClear()
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
    onStartStop: () -> Unit,
    /** v0.50.0: الإعلان جلسة كاملة — والزر نفسه ينهي الجلسة */
    onAnnounce: () -> Unit,
    /** v0.50.0: جلسة البث على الهواء */
    onAir: Boolean
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
            // v0.50.0: إعلان بدء البث يتحول إلى إنهاء الجلسة ما دامت جارية —
            // نفس الحالة المركزية فالاستوديو والرئيسية يرزنان دائماً زرّاً واحداً
            Surface(
                onClick = onAnnounce,
                shape = MaterialTheme.shapes.large,
                color = if (onAir) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        if (onAir) AppIcons.Stop else AppIcons.Campaign, null,
                        tint = if (onAir) MaterialTheme.colorScheme.onErrorContainer
                               else MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        if (onAir) "إنهاء جلسة البث" else "إعلان بدء البث",
                        Modifier.padding(start = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (onAir) MaterialTheme.colorScheme.onErrorContainer
                                else MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
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
// v0.16.0 — وحدة المزج المبسّطة «سهلة التحكم»
// =====================================================================

/**
 * وحدة مزج أعيد تصميمها بالكامل بعد ملاحظة «يصعب التحكم بها»:
 *
 * لماذا كانت الصعوبة؟ (تشخيص v0.16.0)
 *  1) فيضرات عمودية ضيقة (5 قنوات في صف 250dp) — سحب رأسي دقيق بالأصعب على الهاتف.
 *  2) مؤشر الفيدَر لا يتحرك أثناء السحب لأن الحالة كانت تأتي من الإعدادات
 *     ولا تُحدَّث محلياً، والحفظ عند نهاية السحب كان يكتب القيمة القديمة!
 *  3) الكتم كان يكتب صفراً داخل الفيدَر المحفوظ فيتلف بعد كتم/إلغاء كتم،
 *     والماستر كان يتضاعف داخل قيم القنوات فتتلوث القيم المحفوظة.
 *
 * التصميم الجديد:
 *  - صف أفقي كبير لكل قناة: سلايدر أندرويد قياسي (سحب أو نقر مباشر)
 *    + زرا دقة (− / +) بنقرة 2٪ (5٪ للميكروفون) — بلا حاجة لأصابع جرّاح.
 *  - مؤشر مستوى رفيع تحت سلايدر الميكروفون والموسيقى.
 *  - زر كتم M جلسي: يصمّت مؤقتاً ولا يكتب شيئاً في الإعدادات إطلاقاً —
 *    إلغاء الكتم يعيد المستوى نفسه فوراً (سلوك لوحات الاستوديو الحقيقية).
 *  - الفيدَرات محلية الحركة (تتحرك مع الإصبع لحظياً) وتُحفظ قيمها الخام
 *    عند ترك السلايدر فقط — والماستر يُضرب لحظياً عند التطبيق لا عند الحفظ.
 *  - مشاهد جاهزة كبطاقات كبيرة: ضغطة واحدة تنقل كل القنوات وترفع الكتم.
 *
 * الدلالة الصوتية: الفعّال = فيدَر القناة × الماستر (الميك خط مباشر)،
 * وأثناء إعلان حيّ: فيدَر الموسيقى × الماستر × نسبة الخفض.
 */
@Composable
private fun MixerConsole(
    micFader: Float,
    /** v0.35.0: مزوّد مستوى الميكروفون — يُقرأ وقت العينة داخل شريط VU */
    micLevelProvider: () -> Float,
    musicFader: Float,
    /** v0.35.0: مزوّد مستوى الموسيقى — يُقرأ وقت العينة داخل شريط VU */
    mediaLevelProvider: () -> Float,
    breaksFader: Float,
    masterFader: Float,
    duckEnabled: Boolean,
    duckVolume: Float,
    duckingActive: Boolean,
    sceneTick: Int,
    onMicVolume: (Float) -> Unit,
    onMusicVolume: (Float) -> Unit,
    onCartVolume: (Float) -> Unit,
    onMicFaderFinished: (Float) -> Unit,
    onMusicFaderFinished: (Float) -> Unit,
    onBreaksFaderFinished: (Float) -> Unit,
    onMasterFaderFinished: (Float) -> Unit,
    onDuckEnabled: (Boolean) -> Unit,
    onDuckVolume: (Float) -> Unit,
    onScene: (MixerLogic.Scene) -> Unit
) {
    val st = MaterialTheme.status

    // ===== الفيدَرات المحلية: تتحرك فوراً مع الإصبع =====
    var micLocal by remember { mutableFloatStateOf(micFader) }
    var musicLocal by remember { mutableFloatStateOf(musicFader) }
    var breaksLocal by remember { mutableFloatStateOf(breaksFader) }
    var masterLocal by remember { mutableFloatStateOf(masterFader) }

    // مزامنة مع المصدر عند تغيّر خارجي (استرجاع من الإعدادات، تطبيق مشهد)
    LaunchedEffect(micFader) { micLocal = micFader }
    LaunchedEffect(musicFader) { musicLocal = musicFader }
    LaunchedEffect(breaksFader) { breaksLocal = breaksFader }
    LaunchedEffect(masterFader) { masterLocal = masterFader }

    // ===== الكتم جلسي فقط — لا يكتب في الإعدادات ولا يمس الفيدَر =====
    var micMuted by remember { mutableStateOf(false) }
    var musicMuted by remember { mutableStateOf(false) }
    var breaksMuted by remember { mutableStateOf(false) }
    // كل مشهد يُطبَّق = حالة نظيفة بلا كتم معلّق
    LaunchedEffect(sceneTick) {
        if (sceneTick > 0) {
            micMuted = false
            musicMuted = false
            breaksMuted = false
        }
    }

    /** الفعّال لقناة الموسيقى: فيدَر × ماستر (× نسبة الخفض أثناء إعلان حيّ) */
    fun musicEffective(fader: Float, muted: Boolean): Float = when {
        muted -> 0f
        duckingActive -> fader * masterLocal * duckVolume
        else -> fader * masterLocal
    }

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Tune, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text("وحدة المزج", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "اسحب السلايدر أو انقر عليه — واستعمل − / + للضبط الدقيق",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ===== المشاهد الجاهزة — بطاقات كبيرة بضغطة واحدة =====
            MixerLogic.Scene.entries.chunked(2).forEach { rowScenes ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowScenes.forEach { scene ->
                        SceneCard(
                            scene = scene,
                            onClick = { onScene(scene) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowScenes.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // ===== قناة الميكروفون — خط مباشر 0..200٪ (لا يخضع للماستر) =====
            MixerChannelRow(
                icon = AppIcons.Mic,
                name = "الميكروفون",
                value = micLocal,
                range = 0f..2f,
                step = 0.05f,
                muted = micMuted,
                accent = st.live,
                vu = null,
                vuProvider = { if (micMuted) 0f else micLevelProvider() },
                onValue = { v ->
                    micLocal = MixerLogic.clampFader(MixerLogic.Channel.MIC, v)
                    onMicVolume(if (micMuted) 0f else micLocal)
                },
                onFinished = { onMicFaderFinished(micLocal) },
                onStep = { v ->
                    micLocal = MixerLogic.clampFader(MixerLogic.Channel.MIC, v)
                    onMicVolume(if (micMuted) 0f else micLocal)
                    onMicFaderFinished(micLocal)
                },
                onToggleMute = {
                    micMuted = !micMuted
                    onMicVolume(if (micMuted) 0f else micLocal)
                }
            )

            // ===== قناة الموسيقى — مشغل المكتبة/القوائم/الفقرات =====
            MixerChannelRow(
                icon = AppIcons.MusicNote,
                name = "الموسيقى",
                value = musicLocal,
                range = 0f..1f,
                step = 0.02f,
                muted = musicMuted,
                accent = MaterialTheme.colorScheme.primary,
                vu = null,
                vuProvider = { if (musicMuted) 0f else mediaLevelProvider() },
                onValue = { v ->
                    musicLocal = MixerLogic.clampFader(MixerLogic.Channel.MUSIC, v)
                    onMusicVolume(musicEffective(musicLocal, musicMuted))
                },
                onFinished = { onMusicFaderFinished(musicLocal) },
                onStep = { v ->
                    musicLocal = MixerLogic.clampFader(MixerLogic.Channel.MUSIC, v)
                    onMusicVolume(musicEffective(musicLocal, musicMuted))
                    onMusicFaderFinished(musicLocal)
                },
                onToggleMute = {
                    musicMuted = !musicMuted
                    onMusicVolume(musicEffective(musicLocal, musicMuted))
                }
            )

            // ===== قناة الجينل — حائط الرقائق والفواصل =====
            MixerChannelRow(
                icon = AppIcons.QueueMusic,
                name = "الجينل والفواصل",
                value = breaksLocal,
                range = 0f..1f,
                step = 0.02f,
                muted = breaksMuted,
                accent = Color(0xFFE8B33C),
                vu = null,
                onValue = { v ->
                    breaksLocal = MixerLogic.clampFader(MixerLogic.Channel.BREAKS, v)
                    onCartVolume(if (breaksMuted) 0f else breaksLocal * masterLocal)
                },
                onFinished = { onBreaksFaderFinished(breaksLocal) },
                onStep = { v ->
                    breaksLocal = MixerLogic.clampFader(MixerLogic.Channel.BREAKS, v)
                    onCartVolume(if (breaksMuted) 0f else breaksLocal * masterLocal)
                    onBreaksFaderFinished(breaksLocal)
                },
                onToggleMute = {
                    breaksMuted = !breaksMuted
                    onCartVolume(if (breaksMuted) 0f else breaksLocal * masterLocal)
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // ===== الماستر — يُضرب لحظياً في كل القنوات الموسيقية أثناء السحب =====
            MixerChannelRow(
                icon = AppIcons.VolumeUp,
                name = "الماستر — مستوى القنوات الموسيقية كلها",
                value = masterLocal,
                range = 0f..1f,
                step = 0.02f,
                muted = false,
                accent = Color(0xFFE8B33C),
                vu = null,
                onValue = { v ->
                    masterLocal = MixerLogic.clampFader(MixerLogic.Channel.MASTER, v)
                    onMusicVolume(musicEffective(musicLocal, musicMuted))
                    onCartVolume(if (breaksMuted) 0f else breaksLocal * masterLocal)
                },
                onFinished = { onMasterFaderFinished(masterLocal) },
                onStep = { v ->
                    masterLocal = MixerLogic.clampFader(MixerLogic.Channel.MASTER, v)
                    onMusicVolume(musicEffective(musicLocal, musicMuted))
                    onCartVolume(if (breaksMuted) 0f else breaksLocal * masterLocal)
                    onMasterFaderFinished(masterLocal)
                },
                onToggleMute = {},
                isMaster = true
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // ===== الخفض التلقائي (Ducking) =====
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("خفض الموسيقى تلقائياً أثناء الإعلان", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (duckEnabled) {
                            "يبقى من الموسيقى أثناء الحديث: ${(duckVolume * 100).roundToInt()}٪" +
                                if (duckingActive) " — الخفض فعّال الآن" else ""
                        } else {
                            "معطّل — الموسيقى بمستواها الكامل أثناء الحديث"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (duckingActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = duckEnabled, onCheckedChange = onDuckEnabled)
            }
            if (duckEnabled) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "نسبة البقاء",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = duckVolume,
                        onValueChange = onDuckVolume,
                        valueRange = 0f..0.6f,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${(duckVolume * 100).roundToInt()}٪",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(46.dp)
                    )
                }
            }

            Text(
                "القيم تُحفظ تلقائياً عند ترك السلايدر • زر M كتم مؤقت يعود لحظة إلغائه • " +
                    "الميكروفون خط مباشر لا يخضع للماستر",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** بطاقة مشهد جاهز — هدف لمس كبير بعنوان وشرح مختصر */
@Composable
private fun SceneCard(
    scene: MixerLogic.Scene,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        modifier = modifier
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                scene.labelAr,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                scene.hintAr,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
                maxLines = 2
            )
        }
    }
}

/**
 * صف قناة واحد — الوحدة الأساسية «السهلة»:
 * اسم + قيمة + كتم M، ثم صف (− سلايدر +)، ثم شريط مستوى رفيع (اختياري).
 */
@Composable
private fun MixerChannelRow(
    icon: ImageVector,
    name: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    muted: Boolean,
    accent: Color,
    vu: Float?,
    /** v0.35.0: مصدر مستوى حي يُقرأ وقت العينة — يلغي إعادة تركيب الصف عند كل انبعاث */
    vuProvider: (() -> Float)? = null,
    onValue: (Float) -> Unit,
    onFinished: () -> Unit,
    onStep: (Float) -> Unit,
    onToggleMute: () -> Unit,
    isMaster: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // الصف الأول: الاسم + القيمة الحية + زر الكتم
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon, null,
                tint = if (muted) MaterialTheme.colorScheme.outlineVariant else accent,
                modifier = Modifier.size(20.dp)
            )
            Text(
                name,
                Modifier.weight(1f).padding(horizontal = 8.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface
            )
            Text(
                if (muted) "مكتوم" else "${(value * 100).roundToInt()}٪",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (muted) MaterialTheme.colorScheme.error else accent
            )
            if (!isMaster) {
                MuteButton(
                    muted = muted,
                    onClick = onToggleMute,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }
        }
        // الصف الثاني: أزرار الدقة حول السلايدر
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StepButton(
                label = "−",
                enabled = value > range.start + 0.001f
            ) { onStep((value - step).coerceIn(range.start, range.endInclusive)) }
            Slider(
                value = value.coerceIn(range.start, range.endInclusive),
                onValueChange = onValue,
                onValueChangeFinished = onFinished,
                valueRange = range,
                colors = SliderDefaults.colors(
                    thumbColor = if (muted) MaterialTheme.colorScheme.outlineVariant else accent,
                    activeTrackColor = if (muted) MaterialTheme.colorScheme.outlineVariant else accent,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.weight(1f)
            )
            StepButton(
                label = "+",
                enabled = value < range.endInclusive - 0.001f
            ) { onStep((value + step).coerceIn(range.start, range.endInclusive)) }
        }
        // شريط مستوى رفيع (الميكروفون والموسيقى فقط)
        if (vu != null || vuProvider != null) {
            LinearVu(
                level = vu ?: 0f,
                levelProvider = vuProvider,
                modifier = Modifier.fillMaxWidth().height(5.dp)
            )
        }
    }
}

/** زر كتم M — أحمر عند الكتم، لا يكتب في الإعدادات إطلاقاً */
@Composable
private fun MuteButton(
    muted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = if (muted) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Text(
            "M",
            Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            color = if (muted) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

/** زر دقة دائري − / + — ضغطة واحدة تحرّك الفيدَر بخطوة صغيرة ثابتة */
@Composable
private fun StepButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.size(32.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}

/**
 * شريط مستوى أفقي رفيع — تعبئة بتدرج حراري (أخضر ← كهرماني ← أحمر)
 * تنمو من جهة البداية (يمين الشاشة في العربية) مع تنعيم بسيط.
 */
@Composable
private fun LinearVu(
    level: Float,
    modifier: Modifier = Modifier,
    /** v0.35.0: مصدر المستوى المباشر — يُقرأ وقت العينة لا في التركيب */
    levelProvider: (() -> Float)? = null
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val currentLevel by rememberUpdatedState(level)
    val providerRef by rememberUpdatedState(levelProvider)
    val smooth = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var prev = 0f
        while (true) {
            val target = (providerRef?.invoke() ?: currentLevel).coerceIn(0f, 1f)
            prev = WaveformLogic.smooth(prev, target)
            smooth.floatValue = prev
            delay(45)
        }
    }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(color = track, cornerRadius = CornerRadius(h / 2))
        val lvl = smooth.floatValue
        if (lvl > 0.005f) {
            val green = Color(0xFF2E9E6B)
            val amber = Color(0xFFE8B33C)
            val red = Color(0xFFD9534F)
            val fillW = w * lvl
            val startX = if (rtl) w - fillW else 0f
            val gradStart = if (rtl) w else 0f
            val gradEnd = if (rtl) 0f else w
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = if (rtl) listOf(red, amber, green) else listOf(green, amber, red),
                    startX = gradStart,
                    endX = gradEnd
                ),
                topLeft = Offset(startX, 0f),
                size = Size(fillW, h),
                cornerRadius = CornerRadius(h / 2)
            )
        }
    }
}

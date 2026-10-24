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
fun StudioScreen(snackbar: androidx.compose.material3.SnackbarHostState) {
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

        // ===== وحدة المزج =====
        item {
            MixerCard(
                micGain = micGain,
                micLevel = micLevelRaw,
                mediaVolume = playbackState.volume,
                mediaLevel = mediaLevelRaw,
                duckEnabled = settings.duckEnabled,
                duckVolume = settings.duckVolume,
                onMicGain = { mic.setMicGain(it) },
                onMicGainFinished = { v -> scope.launch { locator.settings.setMicGain(v) } },
                onMediaVolume = { locator.playback.setVolume(it) },
                onMediaVolumeFinished = { v -> scope.launch { locator.settings.setDefaultVolume(v) } },
                onDuckEnabled = { scope.launch { locator.settings.setDuckEnabled(it) } },
                onDuckVolume = { scope.launch { locator.settings.setDuckVolume(it) } }
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
                    "تتوقف المعاينات ويُخفَض مستوى الموسيقى تلقائياً ثم تُعاد بعد الإنهاء.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start
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
// وحدة المزج: فيدَرَان + مؤشرا مستوى + خفض تلقائي
// =====================================================================

@Composable
private fun MixerCard(
    micGain: Float,
    micLevel: Float,
    mediaVolume: Float,
    mediaLevel: Float,
    duckEnabled: Boolean,
    duckVolume: Float,
    onMicGain: (Float) -> Unit,
    onMicGainFinished: (Float) -> Unit,
    onMediaVolume: (Float) -> Unit,
    onMediaVolumeFinished: (Float) -> Unit,
    onDuckEnabled: (Boolean) -> Unit,
    onDuckVolume: (Float) -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Tune, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.padding(horizontal = 10.dp)) {
                    Text("وحدة المزج", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "اضبط قناة الميكروفون وقناة الموسيقى كما في الاستوديوهات",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth().height(230.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // قناة الميكروفون: مؤشر مستوى + فيدَر
                VuMeter(
                    level = micLevel,
                    label = "الميك",
                    modifier = Modifier.weight(1f)
                )
                VerticalFader(
                    value = micGain / 2f,
                    onValueChange = { onMicGain(it * 2f) },
                    onFinished = { onMicGainFinished(micGain) },
                    label = "كسب الميكروفون",
                    valueText = if (micGain <= 0.01f) "مكتوم"
                    else "${(micGain * 100).roundToInt()}٪",
                    modifier = Modifier.weight(1.2f),
                    accent = MaterialTheme.colorScheme.primary
                )

                Box(
                    Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                )

                // قناة الموسيقى: فيدَر + مؤشر مستوى
                VerticalFader(
                    value = mediaVolume,
                    onValueChange = { onMediaVolume(it) },
                    onFinished = { onMediaVolumeFinished(mediaVolume) },
                    label = "مستوى الموسيقى",
                    valueText = "${(mediaVolume * 100).roundToInt()}٪",
                    modifier = Modifier.weight(1.2f),
                    accent = MaterialTheme.colorScheme.secondary
                )
                VuMeter(
                    level = mediaLevel,
                    label = "الموسيقى",
                    modifier = Modifier.weight(1f),
                    accent = MaterialTheme.colorScheme.secondary
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // الخفض التلقائي (Ducking)
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

package com.saidjte.schoolradio.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.saidjte.schoolradio.audio.AudioEffects
import com.saidjte.schoolradio.core.audio.RadioPresets
import java.util.Locale
import kotlin.math.roundToInt

/**
 * v0.22.0: مكوّنات المعادل الصوتي المشتركة — كانت خاصة بالاستوديو منذ v0.9.0
 * وأصبحت مشتركة بين الاستوديو وشاشة الإعدادات (نفس مصدر الحالة AudioEffects
 * يجعل الموضعين متزامنين دائماً).
 */

/**
 * بطاقة المعادل الصوتي الكاملة: مفتاح تشغيل + أنماط إذاعية مخصصة (v0.22.0)
 * + إعدادات الجهاز المترجمة + شرائح تردد عمودية + باص/محيطي + تصفير.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EqualizerCard(
    fx: AudioEffects.AudioEffectsState,
    onEnabled: (Boolean) -> Unit,
    onPreset: (Short) -> Unit,
    onRadioPreset: (RadioPresets.RadioPreset) -> Unit = {},
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
                // ===== v0.22.0: الأنماط الإذاعية المخصصة — مستقلة عن دعم الجهاز =====
                if (RadioPresets.ALL.isNotEmpty()) {
                    Text(
                        "أنماط الإذاعة",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        RadioPresets.ALL.forEach { p ->
                            FilterChip(
                                selected = fx.selectedPreset?.toInt() == p.id,
                                onClick = { onRadioPreset(p) },
                                label = { Text(p.name) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }
                    }
                    // وصف النمط المختار إن وُجد
                    RadioPresets.ALL.firstOrNull { it.id == fx.selectedPreset?.toInt() }
                        ?.let { chosen ->
                            Text(
                                chosen.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                }

                // إعدادات الجهاز المسبقة (قد تكون فارغة على بعض الأجهزة)
                if (fx.presets.isNotEmpty()) {
                    Text(
                        "أنماط الجهاز",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
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
fun dbText(db: Float): String = when {
    db > 0.05f -> "+" + String.format(Locale.US, "%.1f", db)
    db < -0.05f -> String.format(Locale.US, "%.1f", db)
    else -> "0.0"
}

/**
 * فيدَر عمودي بلمسة استوديو — مسار بتدرج + مقبض بخط مركزي.
 * السحب عمودياً والنقر ينقل القيمة مباشرة (القمة = 1.0).
 * [onFinished] يُستدعى عند نهاية السحب لحفظ القيمة.
 */
@Composable
fun VerticalFader(
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

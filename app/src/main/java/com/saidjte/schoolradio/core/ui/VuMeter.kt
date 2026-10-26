package com.saidjte.schoolradio.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.saidjte.schoolradio.core.util.AirCountdown
import com.saidjte.schoolradio.core.util.OnAirDisplayLogic
import com.saidjte.schoolradio.core.util.WaveformLogic
import kotlinx.coroutines.delay

/**
 * v0.24.0 — مؤشر VU الكبير لشاشة العرض.
 *
 * عمودان متقطعان (20 شريحة) بأسلوب عدّادات الاستوديو الكلاسيكية:
 *  - عمود «الموسيقى» يتبع مستوى الوسائط الحي، وعمود «الميكروفون» يتبع الميكروفون الحي.
 *  - مناطق ألوان VU المعروفة: أخضر سفلي، كهرماني علوي، وشريحتان حمراوان للقمة
 *    — نفس لغة ألوان العد التنازلي على الشاشة فلا يلتبس المذيع.
 *  - قمة ثابتة (Peak Hold) تنحدر ببطء — يلتقط المذيع أعلى لحظة حتى لو علا طويلاً.
 *
 * الأداء: نفس نمط LiveWaveform — معيّنة 45ms تسحب من تدفق مكتوم ~10ms،
 * والرسم Canvas صافٍ بلا إعادة تركيب.
 */
@Composable
fun BigVuMeter(
    /** مستوى الوسائط 0..1 (RMS من محرك التشغيل) */
    mediaLevel: Float,
    /** مستوى الميكروفون 0..1 (RMS من محرك البث) */
    micLevel: Float,
    /** تشغيل الوسائط جارٍ — يوقّع عمود الموسيقى ويصفّره عند التوقف */
    mediaActive: Boolean,
    /** الميكروفون حي — يوقّع عمود الميكروفون ويصفّره عند الإغلاق */
    micActive: Boolean,
    modifier: Modifier = Modifier,
    onDark: Color = Color.White,
    /** لون المنطقة السفلية (الخضراء) — زمردي شاشة العرض افتراضياً */
    lowColor: Color = Color(0xFF5CDAC4)
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        VuBarColumn(
            label = "موسيقى",
            level = mediaLevel,
            active = mediaActive,
            lowColor = lowColor,
            onDark = onDark,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
        Spacer(Modifier.width(10.dp))
        VuBarColumn(
            label = "ميكروفون",
            level = micLevel,
            active = micActive,
            lowColor = lowColor,
            onDark = onDark,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
    }
}

/** عمود VU واحد بعلامة القمة الثابتة وعنوان أسفلها */
@Composable
private fun VuBarColumn(
    label: String,
    level: Float,
    active: Boolean,
    lowColor: Color,
    onDark: Color,
    modifier: Modifier = Modifier
) {
    val segs = OnAirDisplayLogic.VU_SEGMENTS
    val currentLevel by rememberUpdatedState(level)
    val smooth = remember { mutableFloatStateOf(0f) }
    val peak = remember { mutableFloatStateOf(0f) }

    // معيّنة 45ms — تنعيم أسي مثل الموجة + تحديث القمة الثابتة بالمنطق الصافي
    LaunchedEffect(active) {
        if (!active) {
            smooth.floatValue = 0f
            peak.floatValue = 0f
            return@LaunchedEffect
        }
        var prev = 0f
        while (true) {
            val target = WaveformLogic.normalize(currentLevel)
            val smoothed = WaveformLogic.smooth(prev, target)
            prev = smoothed
            smooth.floatValue = smoothed
            peak.floatValue = OnAirDisplayLogic.peakHold(peak.floatValue, smoothed)
            delay(45)
        }
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val gap = 2.5f.dp.toPx()
            val segH = (size.height + gap) / segs - gap
            val lit = OnAirDisplayLogic.vuSegments(smooth.floatValue, segs)
            val barW = size.width
            // الشرائح من الأسفل (0) إلى الأعلى — أولاً الخامل ثم المضاء فوقه
            for (i in 0 until segs) {
                val y = size.height - (i + 1) * segH - i * gap
                val zone = OnAirDisplayLogic.vuZoneColor(i, segs)
                val litColor = when (zone) {
                    AirCountdown.Urgency.FINAL -> Color(0xFFFF5A5F)
                    AirCountdown.Urgency.SOON -> Color(0xFFFFB74D)
                    else -> lowColor
                }
                drawRoundRect(
                    color = if (i < lit) litColor else onDark.copy(alpha = 0.10f),
                    topLeft = Offset(0f, y),
                    size = Size(barW, segH),
                    cornerRadius = CornerRadius(segH / 3f, segH / 3f)
                )
            }
            // علامة القمة الثابتة — خط فاتح عند أعلى مستوى التقطه العمود
            val p = peak.floatValue
            if (active && p > 0.02f) {
                val py = size.height - p * size.height
                drawRoundRect(
                    color = onDark.copy(alpha = 0.85f),
                    topLeft = Offset(0f, (py - 1.5f.dp.toPx()).coerceAtLeast(0f)),
                    size = Size(barW, 1.5f.dp.toPx()),
                    cornerRadius = CornerRadius(2f, 2f)
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = onDark.copy(alpha = 0.6f),
            maxLines = 1
        )
    }
}

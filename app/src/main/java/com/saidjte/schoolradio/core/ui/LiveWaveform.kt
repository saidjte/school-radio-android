package com.saidjte.schoolradio.core.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.saidjte.schoolradio.core.util.WaveformLogic
import kotlinx.coroutines.delay

/**
 * v0.12.0 — الموجة الحية (Live Waveform): عمود مستوى الصوت اللحظي كما في
 * مسجلات الاستوديو الاحترافية — أعمدة متماثلة حول خط الوسط تنساب مع الزمن.
 *
 * أين تُستعمل؟
 *  - بطاقة البث المباشر في الرئيسية: أثناء التسجيل تعرض «ما يُكتب في الملف فعلاً»
 *    (مستوى دفعات PCM المرسلة للكاتب — بث حي PTT أو فقرة تحضيرية MEMO على السواء)،
 *    وأثناء البث بلا تسجيل تعرض مستوى الميكروفون الحي.
 *  - شاشة «على الهواء» المسرحية: نبض الميكروفون أثناء الإعلان ونبض الموسيقى
 *    أثناء الفقرات والفواصل — العرض يبدو حياً لا ساكناً.
 *
 * البنية والأداء:
 *  - المصدر StateFlow مكتوم (conflated) يُحدَّث كل ~10ms من خيط الصوت؛
 *    المُعيّنة هنا تسحب منه بإيقاع ثابت 45ms (≈22 إطاراً/ثانية) فتظهر حركة
 *    سلسة بلا إغراق Compose بتحديثات بلا معنى بصري.
 *  - النافذة المنزلقة 64 عموداً (≈2.9 ثانية) في SnapshotStateList —
 *    أي تغيير يعيد الرسم فقط، بلا إعادة تركيب لمحتوى الشاشة.
 *  - التنعيم عبر WaveformLogic.smooth (هبوط أسيّ) يمنع ارتجاف الأعمدة.
 *  - الاتجاه: الأحدث يساراً والتاريخ ينساب يميناً — قراءة طبيعية للواجهة العربية.
 */
@Composable
fun LiveWaveform(
    /** المستوى الخام الحالي 0..1 (RMS كما يأتي من محرك الصوت) */
    level: Float,
    /** القيادة: false تُصفّر النافذة فوراً (موجة ساكنة مستقيمة) */
    active: Boolean,
    modifier: Modifier = Modifier,
    /** لون الأعمدة */
    color: Color,
    /** لون خط الوسط الخامل */
    trackColor: Color = Color.White.copy(alpha = 0.14f),
    /** يُخفي الأعمدة ويكفي بالخط الأوسط — للاستعمال في المساحات الضيقة */
    showBars: Boolean = true
) {
    // النافذة المنزلقة — أحدث قيمة في النهاية
    val history = remember { mutableStateListOf<Float>() }
    // القيمة المنعمة الحالية — تُعرض ولو توقف أخذ العينات
    val display = remember { mutableFloatStateOf(0f) }
    val currentLevel by rememberUpdatedState(level)
    val bars = WaveformLogic.HISTORY_BARS

    // مُعيّنة بإيقاع ثابت — مستقلة عن وتيرة التحديثات الواردة (وميض 10ms مكتوم)
    LaunchedEffect(active) {
        if (!active) {
            history.clear()
            display.floatValue = 0f
            return@LaunchedEffect
        }
        var previous = 0f
        while (true) {
            val target = WaveformLogic.normalize(currentLevel)
            val smoothed = WaveformLogic.smooth(previous, target)
            previous = smoothed
            display.floatValue = smoothed
            WaveformLogic.pushWindow(history, smoothed, bars)
            delay(45)
        }
    }

    Canvas(modifier) {
        val cy = size.height / 2f
        // خط الوسط — يعطي مرجعاً بصرياً حتى في الصمت التام
        drawLine(
            color = trackColor,
            start = Offset(0f, cy),
            end = Offset(size.width, cy),
            strokeWidth = 1.dp.toPx()
        )
        if (!showBars || history.isEmpty()) return@Canvas

        val gap = 1.5f.dp.toPx()
        val step = (size.width + gap) / bars
        val barWidth = (step - gap).coerceAtLeast(1.5f)
        val maxHalf = size.height / 2f - 1.dp.toPx() / 2f

        // الرسم من الأحدث (يسار) إلى الأقدم (يمين) مع تلاشٍ تدريجي نحو التاريخ
        for (i in history.indices) {
            val value = history[i].coerceIn(0.015f, 1f)
            val halfHeight = value * maxHalf
            // من اليمين إلى اليسار: العنصر الأخير (الأحدث) يقع عند الحافة اليسرى
            val x = (history.size - 1 - i) * step
            val fade = 0.3f + 0.7f * (i + 1) / history.size
            drawWaveBar(
                color = color.copy(alpha = fade),
                centerX = x + barWidth / 2f,
                centerY = cy,
                halfHeight = halfHeight.coerceAtLeast(barWidth / 2f),
                width = barWidth
            )
        }
    }
}

/** عمود متماثل حول خط الوسط بأطراف دائرية — شكل شريط المقاييس الكلاسيكي */
private fun DrawScope.drawWaveBar(
    color: Color,
    centerX: Float,
    centerY: Float,
    halfHeight: Float,
    width: Float
) {
    drawRoundRect(
        color = color,
        topLeft = Offset(centerX - width / 2f, centerY - halfHeight),
        size = Size(width, halfHeight * 2f),
        cornerRadius = CornerRadius(width / 2f, width / 2f)
    )
}

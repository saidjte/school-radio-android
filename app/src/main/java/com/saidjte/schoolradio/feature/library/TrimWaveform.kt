package com.saidjte.schoolradio.feature.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.saidjte.schoolradio.core.util.WaveformLogic

/**
 * v0.20.0 — معرض شكل موجي تفاعلي داخل محرر القص اليدوي.
 *
 * المذيع يرى الصوت بالعين قبل أن يسمعه: أعمدة السعة عبر المدة كاملة،
 * المنطقة المختارة مضاءة وما حولها خافت، ومقبضان قابلان للسحب عند حافتي
 * الاختيار (أو نقرة تنقل أقرب مقبض إلى موضع اللمس مباشرة).
 *
 * المحور الزمني يسار → يمين دائماً (عرف عالمي في كل محررات الصوت — Audacity
 * ومشابهوها لا يعكسونه في العربية) — الرسم على Canvas لا يتأثر بتخطيط RTL.
 *
 * الأداء: السحب يحدّث حالتي القص في الأب فتُعاد قراءة الرسم — عمليات Canvas
 * بسيطة (بضع مئة مستطيل) فلا حاجة لتخزين مؤقت أو رسم في طبقة منفصلة.
 *
 * @param envelope المغلف المعروض (0..1 لكل عمود) — null أثناء الاستخراج/عند الفشل
 * @param startMs/endMs نقطتا القص الحاليتان (تُرسمان من الأب فتتحركان حياً مع السحب)
 * @param durationMs مدة الملف — صفر يعطل التفاعل كلياً
 * @param onHandleEngaged بدأ المستخدم لمس مقبض (يوقف المعاينة الصوتية في الأب)
 * @param onStartChange/onEndChange قيم جديدة أثناء السحب (الأب يفرض الفجوة الدنيا)
 * @param onDragFinished انتهى اللمس — الأب يقرّب القيمة لأقرب 100ms
 */
@Composable
fun TrimWaveformView(
    envelope: FloatArray?,
    startMs: Long,
    endMs: Long,
    durationMs: Long,
    onStartChange: (Long) -> Unit,
    onEndChange: (Long) -> Unit,
    onHandleEngaged: () -> Unit,
    onDragFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val insideColor = MaterialTheme.colorScheme.primary
    val outsideColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.30f)
    val handleColor = MaterialTheme.colorScheme.primary
    val selectionColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    val emptyColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)

    // المقبض النشط أثناء السحب: 0 = البداية، 1 = النهاية، -1 = لا شيء
    var activeHandle by remember { mutableIntStateOf(-1) }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(WAVE_HEIGHT_DP.dp)
            .pointerInput(durationMs, envelope) {
                detectTapGestures { off ->
                    if (durationMs <= 0 || envelope == null || envelope.isEmpty()) return@detectTapGestures
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    val startFrac = startMs.toFloat() / durationMs
                    val endFrac = endMs.toFloat() / durationMs
                    val tapMs = WaveformLogic.msAtFraction(off.x / w, durationMs)
                    // النقرة تنقل أقرب مقبض إلى موضعها — أسرع طريقة لتقريب دقيق
                    val toStart = kotlin.math.abs(tapMs - startMs)
                    val toEnd = kotlin.math.abs(endMs - tapMs)
                    onHandleEngaged()
                    if (toStart <= toEnd) onStartChange(tapMs) else onEndChange(tapMs)
                    onDragFinished()
                }
            }
            .pointerInput(durationMs, envelope) {
                detectHorizontalDragGestures(
                    onDragStart = { off ->
                        if (durationMs <= 0 || envelope == null || envelope.isEmpty()) return@detectHorizontalDragGestures
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val startX = startMs.toFloat() / durationMs * w
                        val endX = endMs.toFloat() / durationMs * w
                        activeHandle = if (kotlin.math.abs(off.x - startX) <= kotlin.math.abs(off.x - endX)) 0 else 1
                        onHandleEngaged()
                    },
                    onDragEnd = {
                        if (activeHandle >= 0) {
                            onDragFinished()
                            activeHandle = -1
                        }
                    },
                    onDragCancel = {
                        if (activeHandle >= 0) {
                            onDragFinished()
                            activeHandle = -1
                        }
                    }
                ) { change, _ ->
                    change.consume()
                    if (activeHandle < 0 || durationMs <= 0) return@detectHorizontalDragGestures
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    val frac = (change.position.x / w).coerceIn(0f, 1f)
                    val ms = WaveformLogic.msAtFraction(frac, durationMs)
                    if (activeHandle == 0) onStartChange(ms) else onEndChange(ms)
                }
            }
    ) {
        val w = size.width
        val h = size.height
        val midY = h / 2f

        // ===== الخلفية المحايدة + منطقة الاختيار مضاءة =====
        if (durationMs > 0) {
            val startFrac = (startMs.toFloat() / durationMs).coerceIn(0f, 1f)
            val endFrac = (endMs.toFloat() / durationMs).coerceIn(0f, 1f)
            drawRect(
                color = selectionColor,
                topLeft = Offset(w * startFrac, 0f),
                size = Size(w * (endFrac - startFrac).coerceAtLeast(0.005f), h)
            )
        }

        // ===== أعمدة الموجة =====
        if (envelope == null || envelope.isEmpty()) {
            // سطر هادئ يتوسط الحاوية — انتظار الاستخراج أو ملف بلا صوت
            drawLine(
                color = emptyColor,
                start = Offset(0f, midY),
                end = Offset(w, midY),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        } else {
            val n = envelope.size
            val barW = w / n
            val barSlot = barW * 0.72f // فجوة صغيرة بين الأعمدة — قراءة أوضح من الصلب
            val usable = h * 0.86f
            val startFrac = if (durationMs > 0) (startMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
            val endFrac = if (durationMs > 0) (endMs.toFloat() / durationMs).coerceIn(0f, 1f) else 1f
            val corner = CornerRadius(barSlot / 2f)
            for (i in 0 until n) {
                val frac = (i + 0.5f) / n // منتصف العمود يحدد انتماءه للمنطقة
                val amplitude = envelope[i].coerceIn(0f, 1f)
                val barH = (amplitude * usable).coerceAtLeast(2.dp.toPx()) // أرضية بصرية بسيطة
                val color = if (frac in startFrac..endFrac) insideColor else outsideColor
                drawRoundRect(
                    color = color,
                    topLeft = Offset(i * barW + (barW - barSlot) / 2f, midY - barH / 2f),
                    size = Size(barSlot, barH),
                    cornerRadius = corner
                )
            }

            // ===== مقبضا القص: خط عمودي + دائرة مركز =====
            if (durationMs > 0) {
                val lineW = 2.5.dp.toPx()
                val handleR = 7.dp.toPx()
                val startX = w * startFrac
                val endX = w * endFrac
                for (hx in floatArrayOf(startX, endX)) {
                    drawLine(
                        color = handleColor,
                        start = Offset(hx, 2.dp.toPx()),
                        end = Offset(hx, h - 2.dp.toPx()),
                        strokeWidth = lineW,
                        cap = StrokeCap.Round
                    )
                    drawCircle(color = handleColor, radius = handleR, center = Offset(hx, midY))
                    drawCircle(
                        color = Color.White.copy(alpha = 0.9f),
                        radius = handleR * 0.38f,
                        center = Offset(hx, midY)
                    )
                }
            }
        }
    }
}

/** ارتفاع حاوية الموجة داخل الحوار */
private const val WAVE_HEIGHT_DP = 84

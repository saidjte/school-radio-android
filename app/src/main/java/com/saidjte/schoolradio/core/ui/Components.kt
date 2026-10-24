package com.saidjte.schoolradio.core.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.saidjte.schoolradio.core.theme.gradients

/**
 * مكوّنات واجهة مشتركة (v0.7.0 — جيل جديد عصري):
 *  - ScreenHeader: عنوان بخط مميز وشريط تدرج ذهبي + سطر وصف + عنصر ختامي اختياري
 *  - EmptyStateCard: حالة فارغة بقرص متدرج ناعم وظل خفيف وزر متدرج
 *  - StatusBadge: شارة حالة بشكل حبة (Pill) مع نقطة إشارة
 *  - GradientButton: زر رئيسي متدرج بظل ناعم
 *  - Modifier.driftingGradient: خلفية تدرج تنساب ببطء (حياة بلا تكلفة أداء)
 *  - RoundedProgressBar: شريط تقدم بأطراف دائرية
 *  - MirroredIcon: أيقونة تُقلب أفقيًا في الاتجاه العربي RTL
 */

/** شريط تمييز متدرج صغير أعلى عناوين الأقسام */
@Composable
private fun AccentBar(modifier: Modifier = Modifier) {
    Box(
        modifier
            .width(44.dp)
            .height(5.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.gradients.gold.diagonalBrush())
    )
}

/** ترويسة موحّدة لشاشات التطبيق: شريط تمييز + عنوان كبير + سطر وصف */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            AccentBar()
            Text(
                title,
                Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.titleLarge
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) trailing()
    }
}

/** حالة فارغة موحّدة: قرص متدرج ناعم + عنوان + شرح + زر متدرج اختياري */
@Composable
fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    ctaLabel: String? = null,
    onCta: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        shadowElevation = 1.dp
    ) {
        Column(
            Modifier.padding(30.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.Center) {
                // هالة ناعمة خلف القرص
                Box(
                    Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.gradients.heroSoft.diagonalBrush())
                )
                Surface(
                    shape = CircleShape,
                    shadowElevation = 4.dp,
                    color = Color.Transparent
                ) {
                    Box(
                        Modifier
                            .size(58.dp)
                            .background(MaterialTheme.gradients.hero.diagonalBrush(), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            modifier = Modifier.size(26.dp),
                            tint = Color.White
                        )
                    }
                }
            }
            Text(
                title,
                Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                body,
                Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (ctaLabel != null && onCta != null) {
                GradientButton(
                    label = ctaLabel,
                    onClick = onCta,
                    modifier = Modifier.padding(top = 18.dp)
                )
            }
        }
    }
}

/**
 * زر رئيسي متدرج — الوجه الجديد للأزرار الأولية:
 * تدرج زمردي بظل ملون ناعم وشكل حبة، نص أبيض عريض.
 */
@Composable
fun GradientButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    Box(modifier) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(50),
            shadowElevation = if (enabled) 5.dp else 0.dp,
            color = Color.Transparent,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                Modifier
                    .background(
                        if (enabled) MaterialTheme.gradients.hero.diagonalBrush()
                        else Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    )
                    .padding(vertical = 13.dp, horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        label,
                        color = if (enabled) Color.White
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

/** شارة حالة بشكل حبة: نقطة إشارة + نص بلون الحالة */
@Composable
fun StatusBadge(
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(50)
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                label,
                color = color,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

/** تحويل قائمة ألوان إلى تدرج قطري موحّد */
fun List<Color>.diagonalBrush(): Brush = Brush.linearGradient(this)

/**
 * خلفية تدرج تنساب ببطء — تمنح البطاقات البطلة إحساس حياة هادئ.
 * تُزيح نقطة بداية/نهاية التدرج ذهاباً وإياباً كل ~4 ثوانٍ.
 */
fun Modifier.driftingGradient(
    colors: List<Color>,
    animate: Boolean = true
): Modifier = composed {
    if (!animate || colors.size < 2) {
        return@composed this.background(Brush.linearGradient(colors))
    }
    val transition = rememberInfiniteTransition(label = "gradient-drift")
    val phase by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gradient-phase"
    )
    drawBehind {
        val w = size.width
        val h = size.height
        val startX = w * (0.35f * phase)
        val startY = h * (0.25f + 0.2f * phase)
        drawRect(
            brush = Brush.linearGradient(
                colors,
                start = Offset(startX, startY),
                end = Offset(w - startX, h - startY * 0.5f)
            )
        )
    }
}

/** شريط تقدم بأطراف دائرية — بديل أنعم لـ LinearProgressIndicator */
@Composable
fun RoundedProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    strokeWidth: Int = 7
) {
    val cap = (strokeWidth * 2.4f).dp
    Box(modifier) {
        androidx.compose.foundation.Canvas(
            Modifier.fillMaxWidth().height(cap)
        ) {
            val h = size.height
            val w = size.width
            val mid = h / 2f
            val capPx = strokeWidth.dp.toPx().coerceAtMost(h)
            // المسار
            drawLine(
                color = trackColor,
                start = Offset(capPx / 2f, mid),
                end = Offset(w - capPx / 2f, mid),
                strokeWidth = capPx,
                cap = StrokeCap.Round
            )
            // التقدم
            val p = progress.coerceIn(0f, 1f)
            if (p > 0f) {
                drawLine(
                    color = color,
                    start = Offset(capPx / 2f, mid),
                    end = Offset(capPx / 2f + (w - capPx) * p, mid),
                    strokeWidth = capPx,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

/** حلقة تقدم دائرية — مرّر مقاساً صريحاً عبر modifier (مثال: Modifier.size(56.dp)) */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    strokeWidth: Int = 5,
    content: @Composable () -> Unit
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
            val stroke = strokeWidth.dp.toPx()
            val inset = stroke / 2f
            drawArc(
                color = trackColor,
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                style = Stroke(stroke, cap = StrokeCap.Round),
                topLeft = Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
            )
            drawArc(
                color = color,
                startAngle = -90f, sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                style = Stroke(stroke, cap = StrokeCap.Round),
                topLeft = Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
            )
        }
        content()
    }
}

/**
 * أيقونة مُعكوسة أفقيًا في وضع RTL — لأسهم التقدم/التراجع في مشغلات الوسائط
 * (في القراءة العربية يتقدّم «التالي» نحو اليسار كما في يوتيوب العربي).
 */
@Composable
fun MirroredIcon(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Icon(
        icon,
        contentDescription = contentDescription,
        modifier = if (rtl) modifier.scale(scaleX = -1f, scaleY = 1f) else modifier,
        tint = tint
    )
}

/** زر نصي موحّد بألوان الثيم — يُستخدم داخل الترويسات والحالات الفارغة */
@Composable
fun TextAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Text(label)
    }
}

package com.saidjte.schoolradio.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.saidjte.schoolradio.core.theme.glass
import com.saidjte.schoolradio.core.theme.gradients

/**
 * v0.28.0 — طبقة الكريستال: لغة واجهة زجاجية أنيقة فوق خلفية أورورا احترافية.
 *
 *  - [CrystalBackground]: غسلة رأسية هادئة + ثلاث هالات شعاعية ضخمة
 *    (زمردي/سماوي/ذهبي) تمنح العمق الذي تطفو فوقه البطاقات الزجاجية —
 *    بلا تكلفة ضباب حقيقي (radial gradients فقط، خفيفة على كل الأجهزة).
 *  - [GlassCard]: بطاقة نصف شفافة بحد رقيق ولمعة علوية، بقراءة أو بنقرة.
 *  - [GlassButton]: زر زجاجي (أو متدرج زمردي عند accent) بنفس لغة الحواف.
 *  - [GlassBadge]: شارة حبة زجاجية صغيرة للنصوص المميزة.
 *
 * كل المكونات تتكيّف مع الوضعين الفاتح والداكن عبر MaterialTheme.glass.
 */

/** خلفية الأورورا الكريستالية — تُلف حول كامل التطبيق في MainActivity */
@Composable
fun CrystalBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val g = MaterialTheme.gradients
    Box(modifier.fillMaxSize()) {
        // الأساس: غسلة رأسية هادئة من ثلاث درجات
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(g.bgWash))
        )
        // ثلاث هالات شعاعية شفافة — العمق الكريستالي بلا ضباب حقيقي
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    auroraBlob(g.aurora[0], cx = 0.88f, cy = 0.06f, radiusFactor = 0.85f)
                    auroraBlob(g.aurora[1], cx = 0.06f, cy = 0.44f, radiusFactor = 0.80f)
                    auroraBlob(g.aurora[2], cx = 0.72f, cy = 1.02f, radiusFactor = 0.95f)
                }
        )
        content()
    }
}

/** هالة شعاعية واحدة بمركز نسبي (0..1) وشعاع نسبي من أكبر بُعد */
private fun DrawScope.auroraBlob(color: Color, cx: Float, cy: Float, radiusFactor: Float) {
    val w = size.width
    val h = size.height
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(color, Color.Transparent),
            center = Offset(cx * w, cy * h),
            radius = radiusFactor * maxOf(w, h)
        )
    )
}

/**
 * ملء زجاجي داخلي: تدرج ملء مائل + لمعة علوية تذوب في الثلث الأول + صبغة اختيارية
 * (لتمييز حالة دلالية مثل «قيد التشغيل») — داخل حدّ الشكل نفسه عبر drawOutline
 * فلا يتجاوز الزوايا أبداً.
 */
fun Modifier.glassFill(
    fill: Brush,
    sheen: Color,
    shape: Shape,
    tint: Color? = null
): Modifier =
    this.drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        drawOutline(outline, fill)
        tint?.let { drawOutline(outline, it) }
        drawOutline(
            outline,
            Brush.verticalGradient(
                listOf(sheen, Color.Transparent),
                startY = 0f,
                endY = size.height * 0.38f
            )
        )
    }

/**
 * البطاقة الزجاجية الموحّدة — البديل الكريستالي لبطاقات Surface المصمتة.
 * onClick = null → بطاقة قراءة؛ غير null → بطاقة قابلة للنقر بنفس الشكل.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    onClick: (() -> Unit)? = null,
    borderColor: Color? = null,
    shadowElevation: Dp = 2.dp,
    /** صبغة دلالية اختيارية فوق الزجاج (مثل أزرق التشغيل الحي) */
    tint: Color? = null,
    content: @Composable () -> Unit
) {
    val glass = MaterialTheme.glass
    val fillBrush = Brush.linearGradient(glass.fill)
    val border = BorderStroke(1.dp, borderColor ?: glass.border)
    val fillModifier = Modifier.glassFill(fillBrush, glass.sheen, shape, tint)
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = border,
            shadowElevation = shadowElevation
        ) {
            Box(fillModifier) { content() }
        }
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = border,
            shadowElevation = shadowElevation
        ) {
            Box(fillModifier) { content() }
        }
    }
}

/**
 * الزر الزجاجي — الوجه الجديد للأزرار الثانوية:
 * ملء زجاجي وحد رقيق ونص بلون الثيم. accent = true يعيده زرّاً رئيسياً
 * متدرجاً زمردياً (نفس لغة GradientButton) — زرّان بلغة واحدة متدرجة.
 */
@Composable
fun GlassButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Boolean = false,
    enabled: Boolean = true
) {
    val glass = MaterialTheme.glass
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = Color.Transparent,
        border = if (accent) null else BorderStroke(1.dp, glass.border),
        shadowElevation = if (enabled) 4.dp else 0.dp,
        modifier = modifier
    ) {
        val inner: Modifier = if (accent) {
            Modifier.background(
                if (enabled) MaterialTheme.gradients.hero.diagonalBrush()
                else Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.surfaceVariant
                    )
                ),
                shape
            )
        } else {
            Modifier.glassFill(
                if (enabled) Brush.linearGradient(glass.fill)
                else Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                ),
                glass.sheen,
                shape
            )
        }
        Box(
            inner.padding(vertical = 12.dp, horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(
                        icon, null,
                        tint = if (accent && enabled) Color.White
                        else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    label,
                    color = when {
                        accent && enabled -> Color.White
                        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.primary
                    },
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** شارة حبة زجاجية صغيرة — للنصوص المميزة فوق الزجاج (وقت/حالة/عدّاد) */
@Composable
fun GlassBadge(
    text: String,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.primary
) {
    val glass = MaterialTheme.glass
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = glass.chip,
        border = BorderStroke(0.5.dp, glass.border)
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor
        )
    }
}

/** قرص أيقونة أنيق: هالة ناعمة بأيقونة مركزية — للرؤوس والبطاقات */
@Composable
fun IconDisc(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    tint: Color = MaterialTheme.colorScheme.primary,
    container: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
) {
    Box(
        modifier
            .size(size)
            .background(container, RoundedCornerShape(50)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.52f))
    }
}

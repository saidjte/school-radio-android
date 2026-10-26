package com.saidjte.schoolradio.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * v0.32.0 — الأسطح النظيفة: وداعاً لعصر الكريستال.
 *
 *  - [AppBackground]: خلفية مصمتة من لون الثيم — بلا غسلات ولا هالات شعاعية.
 *  - [AppCard]: بطاقة M3 مصمتة بظل خفيف، بصبغة دلالية اختيارية وحد رقيق.
 *  - [IconDisc]: قرص أيقونة ناعم للرؤوس والبطاقات.
 *
 * لغة واحدة بسيطة على كل الأجهزة: رسم أقل، وضوح أعلى، دفء من الألوان
 * المصمتة بدل الشفافيات — وبطارية أطول.
 */

/** خلفية التطبيق المصمتة — تُلف حول كامل التطبيق في MainActivity */
@Composable
fun AppBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        content()
    }
}

/**
 * بطاقة التطبيق الموحّدة — سطح M3 مصمت بظل خفيف، البديل النظيف للبطاقة الزجاجية.
 *  - [tint]: صبغة دلالية تُدمج بلطف مع سطح البطاقة (مثل أزرق «قيد التشغيل»).
 *  - [borderColor]: حد رقيق اختياري للتمييز (مثل زمرد «الفقرة القادمة»).
 *  - onClick غير null → بطاقة قابلة للنقر بنفس الشكل.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    onClick: (() -> Unit)? = null,
    borderColor: Color? = null,
    shadowElevation: Dp = 2.dp,
    tint: Color? = null,
    content: @Composable () -> Unit
) {
    val base = MaterialTheme.colorScheme.surfaceContainer
    val color = if (tint != null) tint.copy(alpha = 0.12f).compositeOver(base) else base
    val border = borderColor?.let { BorderStroke(1.dp, it) }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = color,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = border,
            shadowElevation = shadowElevation
        ) { content() }
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = color,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = border,
            shadowElevation = shadowElevation
        ) { content() }
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

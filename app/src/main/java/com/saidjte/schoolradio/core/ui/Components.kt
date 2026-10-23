package com.saidjte.schoolradio.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * مكوّنات واجهة مشتركة (v0.4.0):
 *  - ScreenHeader: عنوان موحّد لكل الشاشات (عنوان + سطر وصف اختياري)
 *  - EmptyStateCard: حالة فارغة موحّدة بأيقونة داخل قرص ملوّن + زر إجراء اختياري
 *  - StatusBadge: شارة حالة (قرص بلون دلالي شفاف + نص بلونه)
 *  - MirroredIcon: أيقونة تُقلب أفقيًا في الاتجاه العربي RTL
 *    (أزرار التالي/السابق يجب أن تشير باتجاه القراءة العربي)
 */

/** ترويسة موحّدة لشاشات التطبيق: عنوان كبير + سطر وصف */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** حالة فارغة موحّدة: أيقونة في قرص container + عنوان + شرح + إجراء اختياري */
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
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            Modifier.padding(28.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.padding(18.dp).size(34.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                title,
                Modifier.padding(top = 14.dp),
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
                Button(onClick = onCta, Modifier.padding(top = 16.dp)) {
                    Text(ctaLabel)
                }
            }
        }
    }
}

/** شارة حالة: خلفية اللون الدلالي بشفافية + نص بلونه الكامل */
@Composable
fun StatusBadge(
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.13f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            label,
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = color,
            style = MaterialTheme.typography.labelSmall
        )
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

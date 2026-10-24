package com.saidjte.schoolradio.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import java.io.File

/**
 * صورة فقرة محمّلة من مسار ملف محلي (v0.7.0 — عبر Coil).
 *
 * Coil (io.coil-kt) أفضل مكتبة صور لأندرويد:
 *  - تحميل غير متزامن خارج الخيط الرئيسي مع كاش ذاكرة تلقائي
 *  - ظهور متلاشٍ ناعم (crossfade) بدل الظهور المفاجئ
 *  - placeholder شفاف بلون السطح أثناء فك الترميز
 * عند null أو فشل القراءة لا يُرسم شيء (يتقلص التخطيط بأمان) — المنادي يقرر البديل.
 * يعمل Offline بالكامل: المصدر ملفات محلية فقط.
 */
@Composable
fun SegmentImage(
    path: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    cornerRadius: androidx.compose.ui.unit.Dp? = null
) {
    if (path.isNullOrBlank()) return
    val shape = cornerRadius?.let { androidx.compose.foundation.shape.RoundedCornerShape(it) }

    val context = LocalContext.current
    val request = remember(path) {
        ImageRequest.Builder(context)
            .data(File(path))
            .crossfade(180)
            .build()
    }

    val clipped = if (shape != null) modifier.clip(shape) else modifier
    SubcomposeAsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = clipped,
        contentScale = contentScale,
        loading = { Box(Modifier.matchParentSize()) }
    )
}

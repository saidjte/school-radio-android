package com.saidjte.schoolradio.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saidjte.schoolradio.core.ui.AppIcons
import com.saidjte.schoolradio.core.util.ScheduleLogic
import com.saidjte.schoolradio.data.remote.RemoteMediaService
import kotlinx.coroutines.launch

/**
 * v0.23.0 — حوار «استيراد من الإنترنت»: ألصق رابط مقطع من يوتوب أو ساوندكلاود
 * أو Google Drive أو رابط صوتي مباشر، تعرّف عليه المكتبة وتجلب معلوماته،
 * ثم تختار: تشغيل مباشر (بث الآن عبر الإنترنت) أو تنزيل إلى المكتبة (ملف دائم
 * يعمل دون إنترنت للأبد — فلسفة التطبيق offline-first محفوظة).
 *
 * كل فشل يصل من [RemoteMediaService] برسالة عربية جاهزة (RemoteImportException)
 * فلا حاجة لترجمة استثناءات هنا.
 */
@Composable
fun ImportLinkDialog(
    defaultCategoryId: Long?,
    onDismiss: () -> Unit,
    onPlayNow: (RemoteMediaService.LinkPreview, Long?) -> Unit,
    onDownload: (RemoteMediaService.LinkPreview, Long?) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<RemoteMediaService.LinkPreview?>(null) }
    val scope = rememberCoroutineScope()

    // كشف المصدر أثناء الكتابة (فوري بلا شبكة) — يطمئن المستخدم أن الرابط مفهوم
    val detected = remember(url) {
        com.saidjte.schoolradio.core.util.RemoteLinks.parse(url)
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("استيراد من الإنترنت") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "ألصق رابط مقطع من يوتوب أو ساوندكلاود أو Google Drive — أو رابط صوتي مباشر",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        error = null
                        preview = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("رابط المقطع") },
                    placeholder = { Text("https://…") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = {
                        if (url.isNotEmpty() && !busy) {
                            IconButton(onClick = { url = ""; preview = null; error = null }) {
                                Icon(Icons.Filled.Close, "مسح الرابط")
                            }
                        }
                    },
                    singleLine = true,
                    isError = error != null,
                    supportingText = { error?.let { Text(it, color = MaterialTheme.colorScheme.error) } }
                )

                // شارة المصدر المكتشف (تحليل محلي فوري)
                if (detected != null && preview == null) {
                    Text(
                        if (detected.playable) "المصدر: ${detected.source.label}"
                        else detected.blockedReason ?: "رابط غير مدعوم",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (detected.playable) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                }

                // معاينة المقطع بعد الجلب
                preview?.let { p ->
                    Surface(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                p.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    p.sourceLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (!p.uploader.isNullOrBlank()) {
                                    Text(
                                        "  •  ${p.uploader}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (p.durationMs > 0) {
                                Text(
                                    "المدة: ${ScheduleLogic.formatDuration(p.durationMs)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (preview == null) "يجلب معلومات المقطع…"
                            else "ينزّل الملف… قد يستغرق لحظات حسب الحجم",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row {
                if (preview != null && !busy) {
                    // تشغيل مباشر — بث عبر الإنترنت الآن
                    TextButton(onClick = { onPlayNow(preview!!, defaultCategoryId) }) {
                        Icon(AppIcons.GraphicEq, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("تشغيل مباشر")
                    }
                    Spacer(Modifier.width(4.dp))
                    // تنزيل إلى المكتبة — ملف دائم يعمل دون إنترنت
                    TextButton(onClick = { onDownload(preview!!, defaultCategoryId) }) {
                        Icon(AppIcons.Download, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("تنزيل")
                    }
                } else {
                    TextButton(
                        enabled = url.isNotBlank() && !busy,
                        onClick = {
                            scope.launch {
                                busy = true
                                error = null
                                try {
                                    preview = com.saidjte.schoolradio.di.ServiceLocator
                                        .remote.preview(url.trim())
                                } catch (e: Exception) {
                                    error = (e as? com.saidjte.schoolradio.data.remote.RemoteImportException)
                                        ?.message
                                        ?: "تعذّر جلب المقطع — تحقق من الرابط والاتصال"
                                }
                                busy = false
                            }
                        }
                    ) {
                        Text(if (preview == null) "جلب المعلومات" else "إعادة الجلب")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

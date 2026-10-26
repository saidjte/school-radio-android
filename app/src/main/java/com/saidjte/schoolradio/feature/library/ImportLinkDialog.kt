package com.saidjte.schoolradio.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import com.saidjte.schoolradio.core.ui.RoundedProgressBar
import com.saidjte.schoolradio.core.util.RemoteLinks
import com.saidjte.schoolradio.core.util.ScheduleLogic
import com.saidjte.schoolradio.data.remote.DownloadLogic
import com.saidjte.schoolradio.data.remote.RemoteImportException
import com.saidjte.schoolradio.data.remote.RemoteMediaService
import com.saidjte.schoolradio.data.remote.RemoteSearch
import kotlinx.coroutines.launch

/**
 * v0.23.0 + v0.25.0 — حوار «استيراد من الإنترنت» بتبويبين:
 *  - **رابط**: ألصق رابط مقطع من يوتوب أو ساوندكلاود أو Google Drive أو رابط صوتي مباشر.
 *  - **بحث**: اكتب كلمة وابحث مباشرة في يوتوب أو ساوندكلاود، واختر نتيجة لتدخل
 *    نفس مسار المعاينة (تشغيل مباشر أو تنزيل دائم) — لا مسار موازٍ.
 *
 * v0.25.0: مؤشر تنزيل حي — شريط تقدم بالنسبة إن أعلن الخادم الحجم، وخط حالة
 * بالبايتات، وعند إغلاق الحوار يكمل التنزيل بالخلفية ويظهر تقدمه في صف المقطع.
 *
 * كل فشل يصل من الطبقة البعيدة برسالة عربية جاهزة (RemoteImportException).
 */
@Composable
fun ImportLinkDialog(
    defaultCategoryId: Long?,
    activeDownload: RemoteMediaService.DownloadProgress? = null,
    onDismiss: () -> Unit,
    onPlayNow: (RemoteMediaService.LinkPreview, Long?) -> Unit,
    onDownload: (RemoteMediaService.LinkPreview, Long?) -> Unit
) {
    var tab by remember { mutableStateOf(0) } // 0 = رابط، 1 = بحث
    var url by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<RemoteMediaService.LinkPreview?>(null) }
    val scope = rememberCoroutineScope()

    // ===== حالة البحث (تبويب 1) =====
    var query by remember { mutableStateOf("") }
    var searchSource by remember { mutableStateOf(RemoteLinks.Source.YOUTUBE) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<RemoteSearch.RemoteSearchResult>>(emptyList()) }

    // v0.25.0: تنزيل جارٍ من هذا الحوار — عرض تقدمه هنا وإتاحة الإغلاق
    val downloading = activeDownload != null && activeDownload.isRunning

    // كشف المصدر أثناء الكتابة (فوري بلا شبكة) — يطمئن المستخدم أن الرابط مفهوم
    val detected = remember(url) { RemoteLinks.parse(url) }

    fun fetchPreview(link: String) {
        scope.launch {
            busy = true
            error = null
            try {
                preview = com.saidjte.schoolradio.di.ServiceLocator.remote.preview(link.trim())
            } catch (e: Exception) {
                error = (e as? RemoteImportException)?.message
                    ?: "تعذّر جلب المقطع — تحقق من الرابط والاتصال"
            }
            busy = false
        }
    }

    fun doSearch() {
        scope.launch {
            searching = true
            searchError = null
            try {
                results = RemoteSearch.search(searchSource, query)
                if (results.isEmpty()) searchError = "لا نتائج — جرّب كلمات بحث أخرى"
            } catch (e: Exception) {
                searchError = (e as? RemoteImportException)?.message
                    ?: "فشل البحث — تحقق من الاتصال وحاول مجدداً"
            }
            searching = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("استيراد من الإنترنت") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // ===== تبويبا الدخول =====
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        label = { Text("رابط") }
                    )
                    FilterChip(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        label = { Text("بحث") }
                    )
                }

                if (tab == 0) {
                    // ================== تبويب الرابط ==================
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
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = {
                            if (url.isNotEmpty() && !busy) {
                                IconButton(onClick = { url = ""; preview = null; error = null }) {
                                    Icon(Icons.Rounded.Close, "مسح الرابط")
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
                } else {
                    // ================== تبويب البحث (v0.25.0) ==================
                    Text(
                        "ابحث عن المقطع مباشرة في المنصة — النتيجة تدخل نفس مسار المعاينة والتنزيل",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = searchSource == RemoteLinks.Source.YOUTUBE,
                            onClick = { searchSource = RemoteLinks.Source.YOUTUBE; results = emptyList(); searchError = null },
                            label = { Text("يوتوب") }
                        )
                        FilterChip(
                            selected = searchSource == RemoteLinks.Source.SOUNDCLOUD,
                            onClick = { searchSource = RemoteLinks.Source.SOUNDCLOUD; results = emptyList(); searchError = null },
                            label = { Text("ساوندكلاود") }
                        )
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it; searchError = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("كلمة البحث") },
                        placeholder = { Text("مثال: نشيد العلم") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = {
                            if (query.isNotBlank() && !searching) {
                                IconButton(onClick = { doSearch() }) {
                                    Icon(Icons.Rounded.Search, "ابحث")
                                }
                            }
                        },
                        singleLine = true,
                        isError = searchError != null,
                        supportingText = { searchError?.let { Text(it, color = MaterialTheme.colorScheme.error) } }
                    )

                    if (searching) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "يبحث في ${if (searchSource == RemoteLinks.Source.YOUTUBE) "يوتوب" else "ساوندكلاود"}…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (results.isNotEmpty()) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 330.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            results.forEach { r ->
                                Surface(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            url = r.url
                                            tab = 0
                                            fetchPreview(r.url)
                                        },
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                ) {
                                    Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                                        Text(
                                            r.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.size(2.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                r.sourceLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            if (!r.uploader.isNullOrBlank()) {
                                                Text(
                                                    "  •  ${r.uploader}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            if (r.durationMs > 0) {
                                                Text(
                                                    "  •  ${ScheduleLogic.formatDuration(r.durationMs)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Text(
                            "اضغط نتيجة لجلب معلوماتها ثم اختر: تشغيل مباشر أو تنزيل",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                // معاينة المقطع بعد الجلب (مشتركة بين التبويبين)
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

                // ===== v0.25.0: تقدم التنزيل الحي — شريط نسبة + بايتات =====
                if (downloading && activeDownload != null) {
                    Surface(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    ) {
                        Column(
                            Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Text(
                                "جارٍ تنزيل «${activeDownload.title}»…",
                                style = MaterialTheme.typography.titleSmall
                            )
                            val frac = activeDownload.fraction
                            if (frac != null) {
                                RoundedProgressBar(
                                    progress = frac,
                                    modifier = Modifier.fillMaxWidth(),
                                    strokeWidth = 6
                                )
                            } else {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                            }
                            Text(
                                DownloadLogic.statusLine(
                                    activeDownload.downloadedBytes,
                                    activeDownload.totalBytes
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "يمكنك إغلاق الحوار — التنزيل يكمل في الخلفية وسترى تقدمه في صف المقطع",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                if (downloading) {
                    // أثناء التنزيل: الأزرار مغلقة — لا تنزيل مزدوج لنفس المعاينة
                    TextButton(enabled = false, onClick = { }) { Text("جارٍ التنزيل…") }
                } else if (tab == 1) {
                    TextButton(
                        enabled = query.isNotBlank() && !searching,
                        onClick = { doSearch() }
                    ) {
                        Icon(Icons.Rounded.Search, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (results.isEmpty()) "ابحث" else "بحث جديد")
                    }
                } else if (preview != null && !busy) {
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
                        onClick = { fetchPreview(url) }
                    ) {
                        Text(if (preview == null) "جلب المعلومات" else "إعادة الجلب")
                    }
                }
            }
        },
        dismissButton = {
            // أثناء التنزيل: الإغلاق «إخفاء» — التنزيل يكمل بالخلفية
            TextButton(enabled = !busy, onClick = onDismiss) {
                Text(if (downloading) "إخفاء" else "إلغاء")
            }
        }
    )
}

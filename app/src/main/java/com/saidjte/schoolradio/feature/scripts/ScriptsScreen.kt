package com.saidjte.schoolradio.feature.scripts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saidjte.schoolradio.core.ui.AppIcons
import com.saidjte.schoolradio.core.ui.EmptyStateCard
import com.saidjte.schoolradio.core.ui.ScreenHeader
import com.saidjte.schoolradio.core.util.AppFormat
import com.saidjte.schoolradio.data.scripts.ScriptItem
import com.saidjte.schoolradio.di.ServiceLocator

/**
 * شاشة السكربتات الإذاعية (v0.9.0) — نصوص الإعلانات والتقديمات:
 *  - قائمة سكربتات محلية 100% (JSON داخل مسار التطبيق) بعنوان ونص وتاريخ
 *  - إنشاء/تعديل/حذف حر لأي مستخدم (المذيع هو المستخدم الأساسي هنا)
 *  - زر «قراءة» يفتح قارئ Teleprompter بخط ضخم وتمرير تلقائي —
 *    ليقرأ المذيع النص من شاشة الهاتف مباشرة أثناء الإذاعة
 */
@Composable
fun ScriptsScreen(
    snackbar: androidx.compose.material3.SnackbarHostState,
    onRead: (ScriptItem) -> Unit
) {
    val locator = ServiceLocator
    val scripts by locator.scripts.scripts.collectAsStateWithLifecycle()

    var editorTarget by remember { mutableStateOf<ScriptItem?>(null) }
    var showNewEditor by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ScriptItem?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
            ScreenHeader(
                title = "السكربتات",
                subtitle = if (scripts.isEmpty()) "نصوص الإعلانات والتقديمات"
                else "${scripts.size} سكربتاً — اقرأها من شاشة الهاتف مباشرة"
            )

            Spacer(Modifier.height(10.dp))

            if (scripts.isEmpty()) {
                EmptyStateCard(
                    icon = AppIcons.Article,
                    title = "لا سكربتات بعد",
                    body = "اكتب نص الإعلان أو التقديم هنا ليقرأه المذيع من شاشة الهاتف بخط كبير وتمرير تلقائي أثناء الإذاعة",
                    ctaLabel = "أضف سكربتاً جديداً",
                    onCta = { showNewEditor = true },
                    animAsset = "mic_waves"
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(scripts, key = { it.id }) { script ->
                        ScriptRow(
                            script = script,
                            onRead = { onRead(script) },
                            onEdit = { editorTarget = script },
                            onDelete = { deleteTarget = script }
                        )
                    }
                    item { Spacer(Modifier.height(90.dp)) }
                }
            }
        }

        // زر إضافة سكربت
        ExtendedFloatingActionButton(
            onClick = { showNewEditor = true },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(18.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Icon(Icons.Rounded.Add, null)
            Text("  سكربت جديد")
        }
    }

    // ===== محرر سكربت جديد =====
    if (showNewEditor) {
        ScriptEditorDialog(
            initial = null,
            onDismiss = { showNewEditor = false },
            onConfirm = { title, body ->
                locator.scripts.add(title, body)
                showNewEditor = false
            }
        )
    }

    // ===== محرر تعديل =====
    editorTarget?.let { target ->
        ScriptEditorDialog(
            initial = target,
            onDismiss = { editorTarget = null },
            onConfirm = { title, body ->
                locator.scripts.update(target.copy(title = title, body = body))
                editorTarget = null
            }
        )
    }

    // ===== تأكيد الحذف =====
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("حذف السكربت؟") },
            text = { Text("سيُحذف «${target.title}» نهائياً.") },
            confirmButton = {
                TextButton(onClick = {
                    locator.scripts.delete(target.id)
                    deleteTarget = null
                }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("إلغاء") }
            }
        )
    }
}

/** سطر سكربت: عنوان + معاينة نص + تاريخ وعدد كلمات + أزرار قراءة/تعديل/حذف */
@Composable
private fun ScriptRow(
    script: ScriptItem,
    onRead: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        script.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${AppFormat.dateShort(script.updatedAt)} • ${script.wordCount} كلمة",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onRead) {
                    Icon(
                        AppIcons.Display, "قراءة على شاشة العرض",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Rounded.Edit, "تعديل")
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Rounded.Delete, "حذف",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            if (script.body.isNotBlank()) {
                Text(
                    script.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

/** حوار إنشاء/تعديل سكربت — عنوان + نص كامل */
@Composable
private fun ScriptEditorDialog(
    initial: ScriptItem?,
    onDismiss: () -> Unit,
    onConfirm: (title: String, body: String) -> Unit
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var body by remember { mutableStateOf(initial?.body ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "سكربت جديد" else "تعديل السكربت") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("العنوان") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("نص السكربت") },
                    placeholder = { Text("اكتب هنا نص الإعلان أو التقديم كاملاً…") },
                    minLines = 6,
                    modifier = Modifier.fillMaxWidth().height(230.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title, body) },
                enabled = title.isNotBlank() && body.isNotBlank()
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

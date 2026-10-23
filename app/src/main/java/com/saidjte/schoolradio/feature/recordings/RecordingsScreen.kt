package com.saidjte.schoolradio.feature.recordings

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saidjte.schoolradio.audio.RecordingsPlayer
import com.saidjte.schoolradio.core.ui.AppIcons
import com.saidjte.schoolradio.core.util.RecordingLogic
import com.saidjte.schoolradio.data.local.entity.RecordingEntity
import com.saidjte.schoolradio.data.local.entity.UserRole
import com.saidjte.schoolradio.di.ServiceLocator
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * شاشة أرشيف التسجيلات (v0.3.0):
 *  - قائمة كل التسجيلات (بث حي / فقرة تحضيرية) مع بياناتها وتوقيع مسجّلها
 *  - استماع فوري من الشاشة (ExoPlayer مستقل بلا إشعار نظام)
 *  - تسمية وحذف محميان بالأدوار: معلم أو مشرف فقط
 */
@Composable
fun RecordingsScreen(snackbar: androidx.compose.material3.SnackbarHostState) {
    val locator = ServiceLocator
    val scope = rememberCoroutineScope()

    val recordings by locator.recordingsRepo.observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val playback by locator.recordingsPlayer.state.collectAsStateWithLifecycle()
    val canManage by locator.session.canManage
        .collectAsStateWithLifecycle(initialValue = true)

    var renameTarget by remember { mutableStateOf<RecordingEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<RecordingEntity?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("أرشيف التسجيلات", style = MaterialTheme.typography.titleLarge)
        Text(
            "تسجيلات البث الحي والفقرات التحضيرية — ${recordings.size} تسجيلاً",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        if (recordings.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("لا توجد تسجيلات بعد", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "اضغط زر التسجيل في الصفحة الرئيسية أثناء البث المباشر، أو سجّل فقرة تحضيرية",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(recordings, key = { it.id }) { rec ->
                    RecordingRow(
                        recording = rec,
                        playing = playback,
                        canManage = canManage,
                        onToggle = { locator.recordingsPlayer.toggle(rec) },
                        onRename = { renameTarget = rec },
                        onDelete = { deleteTarget = rec }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    // ===== حوار التسمية =====
    renameTarget?.let { target ->
        RenameDialog(
            initial = target.title,
            onDismiss = { renameTarget = null },
            onConfirm = { newTitle ->
                scope.launch {
                    locator.recordingsRepo.rename(target.id, newTitle)
                    snackbar.showSnackbar("عُدّل عنوان التسجيل")
                }
                renameTarget = null
            }
        )
    }

    // ===== حوار تأكيد الحذف (الملف من القرص أيضاً) =====
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("حذف التسجيل؟") },
            text = { Text("سيُحذف «${target.title}» وملفه من الجهاز نهائياً.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        locator.recordingsRepo.delete(target)
                        snackbar.showSnackbar("حُذف التسجيل")
                    }
                    deleteTarget = null
                }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("إلغاء") }
            }
        )
    }
}

/** سطر تسجيل واحد: تشغيل + البيانات + شارة المصدر + أدوات محمية بالدور */
@Composable
private fun RecordingRow(
    recording: RecordingEntity,
    playing: RecordingsPlayer.State,
    canManage: Boolean,
    onToggle: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val isCurrent = playing.recordingId == recording.id
    val dateText = SimpleDateFormat("EEEE d/M/yyyy — HH:mm", Locale("ar"))
        .format(Date(recording.createdAt))

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggle) {
                    Icon(
                        if (isCurrent && playing.isPlaying) AppIcons.Pause
                        else Icons.Filled.PlayArrow,
                        contentDescription = "تشغيل/إيقاف",
                        tint = if (isCurrent) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(recording.title, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "$dateText • ${RecordingLogic.formatElapsed(recording.durationMs)} • " +
                            RecordingLogic.formatBytes(recording.sizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SourceBadge(recording)
                        recording.recordedByName?.let { name ->
                            val role = recording.recordedByRole?.let { UserRole.from(it) }
                            Text(
                                "سجّلها: $name${role?.let { " (${it.labelAr})" } ?: ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (canManage) {
                    IconButton(onClick = onRename) {
                        Icon(Icons.Filled.Edit, "تسمية التسجيل")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Filled.Delete, "حذف التسجيل",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            if (isCurrent) {
                val progress = if (playing.durationMs > 0)
                    playing.positionMs.toFloat() / playing.durationMs else 0f
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                )
                Text(
                    "${RecordingLogic.formatElapsed(playing.positionMs)} / " +
                        RecordingLogic.formatElapsed(playing.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** شارة المصدر: بث مباشر (أحمر) أو فقرة تحضيرية (أزرق) */
@Composable
private fun SourceBadge(recording: RecordingEntity) {
    val isLive = recording.sourceEnum().name == "PTT"
    val color = if (isLive) Color(0xFFC62828) else Color(0xFF1565C0)
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            if (isLive) "بث مباشر" else "فقرة تحضيرية",
            Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            color = color,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun RenameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تسمية التسجيل") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("العنوان") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value.trim()) },
                enabled = value.isNotBlank()
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

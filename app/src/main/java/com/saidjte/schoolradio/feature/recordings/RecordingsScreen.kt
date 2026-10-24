package com.saidjte.schoolradio.feature.recordings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saidjte.schoolradio.audio.RecordingsPlayer
import com.saidjte.schoolradio.core.theme.status
import com.saidjte.schoolradio.core.ui.AppIcons
import com.saidjte.schoolradio.core.ui.EmptyStateCard
import com.saidjte.schoolradio.core.ui.ScreenHeader
import com.saidjte.schoolradio.core.ui.StatusBadge
import com.saidjte.schoolradio.core.util.AppFormat
import com.saidjte.schoolradio.core.util.RecordingLogic
import com.saidjte.schoolradio.data.local.entity.RecordingEntity
import com.saidjte.schoolradio.data.local.entity.UserRole
import com.saidjte.schoolradio.di.ServiceLocator
import kotlinx.coroutines.launch

/**
 * شاشة أرشيف التسجيلات (v0.4.0):
 *  - بحث بالعنوان + مرشّح المصدر (الكل / بث مباشر / فقرة تحضيرية)
 *  - ملخص علوي: العدد والحجم الكلي للأرشيف
 *  - استماع فوري من الشاشة (ExoPlayer مستقل بلا إشعار نظام) بأزرار دائرية ملوّنة
 *  - تسمية وحذف محميان بالأدوار: معلم أو مشرف فقط
 */
enum class RecFilter(val labelAr: String) {
    ALL("الكل"), LIVE("بث مباشر"), MEMO("تحضيرية")
}

@Composable
fun RecordingsScreen(
    snackbar: androidx.compose.material3.SnackbarHostState,
    onScheduleClick: () -> Unit = {}
) {
    val locator = ServiceLocator
    val scope = rememberCoroutineScope()

    val recordings by locator.recordingsRepo.observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val playback by locator.recordingsPlayer.state.collectAsStateWithLifecycle()
    val canManage by locator.session.canManage
        .collectAsStateWithLifecycle(initialValue = true)

    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(RecFilter.ALL) }
    var renameTarget by remember { mutableStateOf<RecordingEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<RecordingEntity?>(null) }

    val visible = recordings
        .filter { rec ->
            when (filter) {
                RecFilter.ALL -> true
                RecFilter.LIVE -> rec.sourceEnum().name == "PTT"
                RecFilter.MEMO -> rec.sourceEnum().name != "PTT"
            }
        }
        .filter { rec ->
            search.isBlank() || rec.title.contains(search.trim(), ignoreCase = true)
        }
    val totalBytes = recordings.sumOf { it.sizeBytes }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        ScreenHeader(
            title = "أرشيف التسجيلات",
            subtitle = if (recordings.isEmpty()) "لا تسجيلات بعد"
            else "${recordings.size} تسجيلاً • ${RecordingLogic.formatBytes(totalBytes)}"
        )

        if (recordings.isNotEmpty()) {
            // ===== البحث =====
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                placeholder = { Text("ابحث في التسجيلات…") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (search.isNotEmpty()) {
                        IconButton(onClick = { search = "" }) {
                            Icon(Icons.Filled.Close, "مسح البحث")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )
            // ===== مرشّح المصدر =====
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RecFilter.entries.forEach { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { filter = f },
                        label = { Text(f.labelAr) }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (recordings.isEmpty()) {
            EmptyStateCard(
                icon = AppIcons.LibraryMusic,
                title = "لا توجد تسجيلات بعد",
                body = "اضغط زر التسجيل في الصفحة الرئيسية أثناء البث المباشر، أو سجّل فقرة تحضيرية قبل وقت الإذاعة"
            )
        } else if (visible.isEmpty()) {
            EmptyStateCard(
                icon = AppIcons.LibraryMusic,
                title = "لا نتائج مطابقة",
                body = "جرّب كلمة بحث مختلفة أو غيّر المرشّح"
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(visible, key = { it.id }) { rec ->
                    RecordingRow(
                        recording = rec,
                        playing = playback,
                        canManage = canManage,
                        onToggle = { locator.recordingsPlayer.toggle(rec) },
                        onRename = { renameTarget = rec },
                        onDelete = { deleteTarget = rec },
                        // v0.5.0: جدولة سريعة — مسودة فقرة بهذا التسجيل ثم الانتقال للجدولة
                        onSchedule = {
                            ServiceLocator.setPendingScheduleDraft(
                                com.saidjte.schoolradio.data.local.entity.ScheduledEventEntity(
                                    scheduleId = -1L,
                                    startMinutes = 480, // 08:00 — يعدّلها المستخدم من المحرر
                                    weekDaysMask = 0b0111110,
                                    title = rec.title,
                                    recordingId = rec.id
                                )
                            )
                            onScheduleClick()
                        }
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

/** سطر تسجيل واحد: زر تشغيل دائري + البيانات + شارة المصدر + أدوات محمية بالدور + جدولة سريعة */
@Composable
private fun RecordingRow(
    recording: RecordingEntity,
    playing: RecordingsPlayer.State,
    canManage: Boolean,
    onToggle: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onSchedule: () -> Unit = {}
) {
    val st = MaterialTheme.status
    val isCurrent = playing.recordingId == recording.id
    val isLiveSrc = recording.sourceEnum().name == "PTT"
    // v0.6.0: أرقام مغربية عبر المنسّق المركزي
    val dateText = AppFormat.dateShort(recording.createdAt)

    Card(Modifier.fillMaxWidth().animateContentSize()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(
                    onClick = onToggle,
                    modifier = Modifier.size(46.dp),
                    colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (isCurrent) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (isCurrent) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        if (isCurrent && playing.isPlaying) AppIcons.Pause
                        else Icons.Filled.PlayArrow,
                        contentDescription = "تشغيل/إيقاف"
                    )
                }
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(
                        recording.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "$dateText • ${RecordingLogic.formatElapsed(recording.durationMs)} • " +
                            RecordingLogic.formatBytes(recording.sizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusBadgeSource(isLiveSrc)
                        recording.recordedByName?.let { name ->
                            val role = recording.recordedByRole?.let { UserRole.from(it) }
                            Text(
                                "سجّلها: $name${role?.let { " (${it.labelAr})" } ?: ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                IconButton(onClick = onSchedule) {
                    Icon(AppIcons.Event, "جدولة هذا التسجيل")
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
            AnimatedVisibility(
                visible = isCurrent,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    val progress = if (playing.durationMs > 0)
                        playing.positionMs.toFloat() / playing.durationMs else 0f
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
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
}

/** شارة المصدر: بث مباشر (أحمر) أو فقرة تحضيرية (أزرق) — بألوان دلالية متوافقة مع الداكن */
@Composable
private fun StatusBadgeSource(isLive: Boolean) {
    val st = MaterialTheme.status
    StatusBadge(if (isLive) "بث مباشر" else "فقرة تحضيرية", if (isLive) st.live else st.running)
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

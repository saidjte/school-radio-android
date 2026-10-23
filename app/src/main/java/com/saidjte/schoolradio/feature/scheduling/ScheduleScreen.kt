package com.saidjte.schoolradio.feature.scheduling

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saidjte.schoolradio.core.theme.status
import com.saidjte.schoolradio.core.ui.AppIcons
import com.saidjte.schoolradio.core.ui.EmptyStateCard
import com.saidjte.schoolradio.core.ui.ScreenHeader
import com.saidjte.schoolradio.core.util.ScheduleLogic
import com.saidjte.schoolradio.data.local.entity.ExecutionStatus
import com.saidjte.schoolradio.data.local.entity.RadioScheduleEntity
import com.saidjte.schoolradio.data.local.entity.ScheduledEventEntity
import com.saidjte.schoolradio.di.ServiceLocator
import kotlinx.coroutines.launch

/**
 * الجدولة: جداول متعددة (طابور الصباح/الاستراحة…)، أحداث بأيام أسبوعية
 * وربط بقوائم تشغيل، حالة التنفيذ لكل فقرة، وطلب صلاحية المنبهات الدقيقة.
 */
@Composable
fun ScheduleScreen(snackbar: androidx.compose.material3.SnackbarHostState) {
    val locator = ServiceLocator
    val scope = rememberCoroutineScope()

    val schedules by locator.schedules.schedules.collectAsStateWithLifecycle(initialValue = emptyList())
    val events by locator.schedules.events.collectAsStateWithLifecycle(initialValue = emptyList())
    val executions by locator.schedules.recentExecutions.collectAsStateWithLifecycle(initialValue = emptyList())
    var creatingSchedule by remember { mutableStateOf(false) }
    var editingEvent by remember { mutableStateOf<EventEditorState?>(null) }
    var renamingSchedule by remember { mutableStateOf<RadioScheduleEntity?>(null) }

    // بوابة الأدوار (v0.3.0): تعديل الجدولة = معلم أو مشرف فقط
    val canManage by locator.session.canManage.collectAsStateWithLifecycle(initialValue = true)
    val requireManage: (() -> Unit) -> Unit = { action ->
        if (canManage) action()
        else scope.launch { snackbar.showSnackbar("يلزم دور معلم أو مشرف لتعديل الجدولة") }
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    requireManage {
                        if (schedules.isEmpty()) {
                            scope.launch { snackbar.showSnackbar("أنشئ جدولاً أولاً ثم أضف إليه فقرات") }
                        } else {
                            editingEvent = EventEditorState(scheduleId = schedules.first().id)
                        }
                    }
                }
            ) {
                Icon(Icons.Filled.Add, null)
                Text("  فقرة مجدولة")
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ===== تنبيه المنبهات الدقيقة =====
            item {
                val exactOk = remember { locator.scheduler.canScheduleExact() }
                if (!exactOk) {
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                AppIcons.Alarm, null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                Text(
                                    "صلاحية المنبهات الدقيقة غير ممنوحة — ستعمل الفقرات بتسامح حتى 10 دقائق",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            TextButton(onClick = { locator.scheduler.openExactAlarmSettings() }) {
                                Text("منح")
                            }
                        }
                    }
                }
            }

            // ===== الجداول =====
            item {
                ScreenHeader(
                    title = "الجداول",
                    subtitle = "مجموعات فقرات تعمل معاً (طابور الصباح، الاستراحة…)"
                )
            }
            items(schedules, key = { it.id }) { s ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(s.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${events.count { it.scheduleId == s.id }} فقرة",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = s.enabled,
                            onCheckedChange = { on ->
                                scope.launch {
                                    locator.schedules.setEnabled(s, on)
                                    locator.rearmScheduleAlarms()
                                    snackbar.showSnackbar(
                                        if (on) "فُعّل الجدول وأُعيدت ضبط منبهاته"
                                        else "عُطّل الجدول وأُلغيت منبهاته"
                                    )
                                }
                            }
                        )
                        IconButton(onClick = { requireManage { renamingSchedule = s } }) {
                            Icon(Icons.Filled.Edit, "تعديل الاسم")
                        }
                        IconButton(onClick = {
                            requireManage {
                                scope.launch {
                                    locator.schedules.deleteSchedule(s.id)
                                    locator.rearmScheduleAlarms()
                                }
                            }
                        }) { Icon(Icons.Filled.Delete, "حذف الجدول") }
                    }
                }
            }

            // ===== الفقرات المجدولة =====
            item {
                ScreenHeader(
                    title = "الفقرات المجدولة",
                    subtitle = "وقت البدء + الأيام + المحتوى — تنطلق تلقائياً عبر المنبهات الدقيقة"
                )
            }
            if (events.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = AppIcons.Event,
                        title = "لا فقرات بعد",
                        body = "أضف فقرة وحدد وقتها وأيامها وقائمة تشغيلها لتنطلق تلقائياً"
                    )
                }
            }
            items(events, key = { it.id }) { e ->
                val scheduleName = schedules.firstOrNull { it.id == e.scheduleId }?.name ?: "—"
                val lastExecution = executions.firstOrNull { it.eventId == e.id }
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${ScheduleLogic.formatMinutes(e.startMinutes)} — ${e.title}",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                "جدول: $scheduleName • الأيام: ${dayMaskLabelAr(e.weekDaysMask)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            lastExecution?.let {
                                val st = MaterialTheme.status
                                Text(
                                    "آخر تنفيذ: ${ExecutionStatus.labelAr(it.status)}" +
                                        (it.note?.let { n -> " • $n" } ?: ""),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when (it.status) {
                                        ExecutionStatus.COMPLETED -> st.success
                                        ExecutionStatus.FAILED -> st.failed
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                        IconButton(onClick = { requireManage { editingEvent = EventEditorState.from(e) } }) {
                            Icon(Icons.Filled.Edit, "تعديل الفقرة")
                        }
                        IconButton(onClick = {
                            requireManage {
                                scope.launch {
                                    locator.scheduler.cancelAlarm(e.id)
                                    locator.schedules.deleteEvent(e.id)
                                    locator.rearmScheduleAlarms()
                                }
                            }
                        }) { Icon(Icons.Filled.Delete, "حذف الفقرة") }
                    }
                }
            }
            item { Spacer(Modifier.padding(60.dp)) }
        }
    }

    // ===== حوار إنشاء/تعديل فقرة =====
    editingEvent?.let { state ->
        EventEditorDialog(
            initial = state,
            schedules = schedules,
            events = events,
            onDismiss = { editingEvent = null },
            onSave = { event ->
                scope.launch {
                    if (state.originalId == null) locator.schedules.addEvent(event)
                    else locator.schedules.updateEvent(event)
                    locator.rearmScheduleAlarms()
                    snackbar.showSnackbar("حُفظت الفقرة وأُعيد ضبط المنبهات")
                }
                editingEvent = null
            }
        )
    }

    // ===== حوار إعادة تسمية جدول =====
    renamingSchedule?.let { s ->
        TextDialog(
            title = "تعديل اسم الجدول", label = "الاسم",
            initial = s.name,
            onDismiss = { renamingSchedule = null }
        ) { name ->
            scope.launch {
                locator.schedules.renameSchedule(s, name)
            }
            renamingSchedule = null
        }
    }

    // حوار إنشاء جدول
    if (creatingSchedule) {
        TextDialog(
            title = "جدول جديد", label = "اسم الجدول (مثل: طابور الصباح)",
            onDismiss = { creatingSchedule = false }
        ) { name ->
            scope.launch {
                locator.schedules.createSchedule(name)
                snackbar.showSnackbar("أُنشئ الجدول «$name»")
            }
            creatingSchedule = false
        }
    }
}

/** حالة محرر الفقرة (جديد أو موجود) */
data class EventEditorState(
    val originalId: Long? = null,
    val scheduleId: Long,
    val title: String = "",
    val timeText: String = "09:45",
    val weekDaysMask: Int = 0b0111110, // الاثنين..الجمعة افتراضياً
    val playlistId: Long? = null,
    val singleTrackId: Long? = null
) {
    companion object {
        fun from(e: ScheduledEventEntity) = EventEditorState(
            originalId = e.id,
            scheduleId = e.scheduleId,
            title = e.title,
            timeText = ScheduleLogic.formatMinutes(e.startMinutes),
            weekDaysMask = e.weekDaysMask,
            playlistId = e.playlistId,
            singleTrackId = e.singleTrackId
        )
    }
}

private val ARABIC_DAYS = listOf("الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت", "الأحد")

/** تسمية عربية لقناع الأيام */
fun dayMaskLabelAr(mask: Int): String =
    (1..7).filter { (mask shr (it - 1)) and 1 == 1 }
        .joinToString("، ") { ARABIC_DAYS[it - 1] }
        .ifBlank { "لا أيام" }

@Composable
private fun EventEditorDialog(
    initial: EventEditorState,
    schedules: List<RadioScheduleEntity>,
    events: List<ScheduledEventEntity>,
    onDismiss: () -> Unit,
    onSave: (ScheduledEventEntity) -> Unit
) {
    val locator = ServiceLocator
    var scheduleId by remember { mutableStateOf(initial.scheduleId) }
    var title by remember { mutableStateOf(initial.title) }
    var timeText by remember { mutableStateOf(initial.timeText) }
    var mask by remember { mutableStateOf(initial.weekDaysMask) }
    var playlistId by remember { mutableStateOf(initial.playlistId) }
    var singleTrackId by remember { mutableStateOf(initial.singleTrackId) }
    var conflictError by remember { mutableStateOf<String?>(null) }

    val playlists by locator.playlists.playlists.collectAsStateWithLifecycle(initialValue = emptyList())
    val tracks by locator.library.tracks.collectAsStateWithLifecycle(initialValue = emptyList())
    val playlistsFlat = playlists
    val tracksFlat = tracks

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.originalId == null) "فقرة مجدولة جديدة" else "تعديل الفقرة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("عنوان الفقرة") }, singleLine = true
                )
                OutlinedTextField(
                    value = timeText, onValueChange = { timeText = it },
                    label = { Text("وقت البدء (HH:mm)") },
                    supportingText = {
                        val parsed = ScheduleLogic.parseHhMm(timeText)
                        Text(
                            if (parsed == null) "صيغة غير صحيحة — مثال: 09:45"
                            else "ستُطلق في ${ScheduleLogic.formatMinutes(parsed)}"
                        )
                    },
                    singleLine = true
                )
                Text("أيام التشغيل", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ARABIC_DAYS.take(4).forEachIndexed { i, day ->
                        FilterChip(
                            selected = (mask shr i) and 1 == 1,
                            onClick = { mask = mask xor (1 shl i) },
                            label = { Text(day, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ARABIC_DAYS.drop(4).forEachIndexed { idx, day ->
                        val i = idx + 4
                        FilterChip(
                            selected = (mask shr i) and 1 == 1,
                            onClick = { mask = mask xor (1 shl i) },
                            label = { Text(day, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Text("المحتوى", style = MaterialTheme.typography.labelLarge)
                Text(
                    "قائمة تشغيل أو مقطع واحد (اختر واحداً)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SimpleDropdownRow(
                    label = "قائمة التشغيل",
                    selected = playlistsFlat.firstOrNull { it.id == playlistId }?.name ?: "بلا",
                    options = playlistsFlat.map { it.id to it.name },
                    onPick = {
                        playlistId = it; singleTrackId = null
                    },
                    onClear = { playlistId = null }
                )
                SimpleDropdownRow(
                    label = "مقطع واحد",
                    selected = tracksFlat.firstOrNull { it.id == singleTrackId }?.title ?: "بلا",
                    options = tracksFlat.map { it.id to it.title },
                    onPick = {
                        singleTrackId = it; playlistId = null
                    },
                    onClear = { singleTrackId = null }
                )
                conflictError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
                // اختيار الجدول
                SimpleDropdownRow(
                    label = "الجدول التابع له",
                    selected = schedules.firstOrNull { it.id == scheduleId }?.name ?: "—",
                    options = schedules.map { it.id to it.name },
                    onPick = { scheduleId = it },
                    onClear = { }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val minutes = ScheduleLogic.parseHhMm(timeText)
                    if (title.isNotBlank() && minutes != null && mask != 0) {
                        // منع تعارض المواعيد مع حدث آخر في نفس الوقت والأيام
                        val conflict = events.any {
                            it.id != initial.originalId &&
                                it.scheduleId == scheduleId &&
                                it.startMinutes == minutes &&
                                (it.weekDaysMask and mask) != 0
                        }
                        if (conflict) {
                            conflictError = "توجد فقرة أخرى في نفس الوقت والأيام المحددة — عدّل الوقت أو الأيام"
                        } else {
                            onSave(
                                ScheduledEventEntity(
                                    id = initial.originalId ?: 0,
                                    scheduleId = scheduleId,
                                    startMinutes = minutes,
                                    weekDaysMask = mask,
                                    title = title.trim(),
                                    playlistId = playlistId,
                                    singleTrackId = singleTrackId
                                )
                            )
                        }
                    }
                }
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun TextDialog(
    title: String,
    label: String,
    initial: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                label = { Text(label) }, singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text.trim()) }
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}


/** صف قائمة منسدلة بسيط (اختيار واحد مع إمكانية الإفراغ) */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SimpleDropdownRow(
    label: String,
    selected: String,
    options: List<Pair<Long, String>>,
    onPick: (Long) -> Unit,
    onClear: () -> Unit
) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = open,
        onExpandedChange = { open = it }
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = open,
            onDismissRequest = { open = false }
        ) {
            DropdownMenuItem(
                text = { Text("بلا") },
                onClick = { onClear(); open = false }
            )
            options.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name, maxLines = 1) },
                    onClick = { onPick(id); open = false }
                )
            }
        }
    }
}

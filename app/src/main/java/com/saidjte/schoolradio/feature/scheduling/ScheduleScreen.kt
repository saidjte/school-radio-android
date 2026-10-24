package com.saidjte.schoolradio.feature.scheduling

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saidjte.schoolradio.core.theme.gradients
import com.saidjte.schoolradio.core.theme.status
import com.saidjte.schoolradio.core.ui.AppIcons
import com.saidjte.schoolradio.core.ui.EmptyStateCard
import com.saidjte.schoolradio.core.ui.ScreenHeader
import com.saidjte.schoolradio.core.ui.SegmentImage
import com.saidjte.schoolradio.core.ui.diagonalBrush
import com.saidjte.schoolradio.core.util.EventContentAr
import com.saidjte.schoolradio.core.util.ScheduleLogic
import com.saidjte.schoolradio.core.util.SegmentImageStore
import com.saidjte.schoolradio.data.local.entity.AudioTrackEntity
import com.saidjte.schoolradio.data.local.entity.CategoryEntity
import com.saidjte.schoolradio.data.local.entity.ExecutionStatus
import com.saidjte.schoolradio.data.local.entity.RecordingEntity
import com.saidjte.schoolradio.data.local.entity.RadioScheduleEntity
import com.saidjte.schoolradio.data.local.entity.ScheduledEventEntity
import com.saidjte.schoolradio.di.ServiceLocator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * الجدولة: جداول متعددة (طابور الصباح/الاستراحة…)، أحداث بأيام أسبوعية
 * وربط بقائمة تشغيل أو مقطع أو **تسجيل من الأرشيف** (v0.5.0)،
 * نص/صورة للفقرة تُعرض على شاشة «على الهواء»، حالة التنفيذ لكل فقرة،
 * وطلب صلاحية المنبهات الدقيقة.
 */
/**
 * شاشة الجدولة (محدّثة v0.8.0):
 *  - ملخص علوي: جداول مفعلة / فقرات إجمالاً / الفقرة القادمة — بلمحة واحدة
 *  - الفقرات مرتبة زمنياً، الفقرة القادمة بشارة وقت متدرجة، الأيام كرقائق مصغرة
 *  - تنبيه المنبهات الدقيقة + بوابة الأدوار كما هي
 */
@Composable
fun ScheduleScreen(snackbar: androidx.compose.material3.SnackbarHostState) {
    val locator = ServiceLocator
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val schedules by locator.schedules.schedules.collectAsStateWithLifecycle(initialValue = emptyList())
    val events by locator.schedules.events.collectAsStateWithLifecycle(initialValue = emptyList())
    val executions by locator.schedules.recentExecutions.collectAsStateWithLifecycle(initialValue = emptyList())
    val playlists by locator.playlists.playlists.collectAsStateWithLifecycle(initialValue = emptyList())
    val tracks by locator.library.tracks.collectAsStateWithLifecycle(initialValue = emptyList())
    // v0.12.0: التصنيفات — لأسماء مصادر جينغلات الفواصل
    val categories by locator.library.categories.collectAsStateWithLifecycle(initialValue = emptyList())
    val recordings by locator.recordingsRepo.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    // v0.13.0: السكربتات — لربط سكربت المذيع بالفقرة المجدولة
    val scripts by locator.scripts.scripts.collectAsStateWithLifecycle(initialValue = emptyList())

    var creatingSchedule by remember { mutableStateOf(false) }
    var editingEvent by remember { mutableStateOf<EventEditorState?>(null) }
    var renamingSchedule by remember { mutableStateOf<RadioScheduleEntity?>(null) }
    // v0.12.0: حوار إعداد الفواصل المجدولة لجدول ما
    var editingBreaks by remember { mutableStateOf<RadioScheduleEntity?>(null) }

    // بوابة الأدوار (v0.3.0): تعديل الجدولة = معلم أو مشرف فقط
    val canManage by locator.session.canManage.collectAsStateWithLifecycle(initialValue = true)
    val requireManage: (() -> Unit) -> Unit = { action ->
        if (canManage) action()
        else scope.launch { snackbar.showSnackbar("يلزم دور معلم أو مشرف لتعديل الجدولة") }
    }

    // ===== التقاط مسودة «جدولة تسجيل» القادمة من شاشة التسجيلات (v0.5.0) =====
    LaunchedEffect(Unit) {
        val draft = locator.pendingScheduleDraft.value ?: return@LaunchedEffect
        locator.setPendingScheduleDraft(null)
        val current = locator.schedules.schedules.first()
        val scheduleId = if (current.isEmpty()) {
            // لا جداول بعد؟ نُنشئ الجدول الصباحي تلقائياً حتى لا يعلق المستخدم
            locator.schedules.createSchedule("البرنامج الصباحي")
        } else current.first().id
        editingEvent = EventEditorState(
            scheduleId = scheduleId,
            title = draft.title,
            weekDaysMask = draft.weekDaysMask,
            recordingId = draft.recordingId
        )
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    requireManage {
                        // إصلاح v0.5.0: لا طريق مسدود — بلا جداول نفتح حوار إنشاء جدول
                        // مباشرة، وبعد حفظه يُفتح محرر الفقرة فوراً
                        if (schedules.isEmpty()) creatingSchedule = true
                        else editingEvent = EventEditorState(scheduleId = schedules.first().id)
                    }
                }
            ) {
                Icon(Icons.Filled.Add, null)
                Text("  فقرة مجدولة")
            }
        }
    ) { padding ->
        // v0.8.0: الفقرات مرتبة زمنياً + حساب الفقرة القادمة للتمييز البصري
        val sortedEvents = events.sortedBy { it.startMinutes }
        val enabledIds = schedules.filter { it.enabled }.map { it.id }.toSet()
        val minutesNow = remember(events) { ScheduleLogic.minutesOfDay(System.currentTimeMillis()) }
        val nextEventId = sortedEvents
            .firstOrNull { it.scheduleId in enabledIds && it.startMinutes > minutesNow }?.id

        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ===== ملخص الجدولة (v0.8.0) =====
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ScheduleStat(
                        "${schedules.count { it.enabled }}", "جدول مفعّل",
                        Modifier.weight(1f), MaterialTheme.colorScheme.primary
                    )
                    ScheduleStat(
                        "${events.size}", "فقرة مجدولة",
                        Modifier.weight(1f), MaterialTheme.colorScheme.secondary
                    )
                    ScheduleStat(
                        "${sortedEvents.count { it.scheduleId in enabledIds && it.startMinutes > minutesNow }}",
                        "متبقية اليوم",
                        Modifier.weight(1f), MaterialTheme.status.success
                    )
                }
            }
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
            // ===== v0.13.0: بطاقة حماية الأتمتة (تحسينات البطارية) =====
            item {
                val batteryOk = remember { locator.scheduler.isIgnoringBatteryOptimizations() }
                if (!batteryOk) {
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                AppIcons.Bolt, null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                Text(
                                    "اعفِ التطبيق من تحسينات البطارية لتعمل الفقرات الأوتوماتيكية موثوقاً على كل الأجهزة",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            TextButton(onClick = { locator.scheduler.requestIgnoreBatteryOptimizations() }) {
                                Text("إعفاء")
                            }
                        }
                    }
                }
            }

            // ===== الجداول =====
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ScreenHeader(
                        title = "الجداول",
                        subtitle = "مجموعات فقرات تعمل معاً (طابور الصباح، الاستراحة…)",
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { requireManage { creatingSchedule = true } }) {
                        Icon(Icons.Filled.Add, null)
                        Text(" جدول جديد")
                    }
                }
            }
            // إصلاح انهيار v0.5.0: مفاتيح LazyColumn يجب أن تكون فريدة في العمود كله
            // لا داخل كل كتلة — جدولان منفصلان يبدآن الترقيم من 1 فتتصادم المفاتيح
            // (Key was already used) بمجرد إضافة أول فقرة. بادئة نصية تفصل النطاقين.
            items(schedules, key = { "schedule-${it.id}" }) { s ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
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
                                        // تنظيف صور فقرات الجدول قبل حذفها (لا صور يتيمة)
                                        events.filter { it.scheduleId == s.id }
                                            .forEach { SegmentImageStore.delete(it.imagePath) }
                                        locator.schedules.deleteSchedule(s.id)
                                        locator.rearmScheduleAlarms()
                                    }
                                }
                            }) { Icon(Icons.Filled.Delete, "حذف الجدول") }
                        }
                        // ===== v0.12.0: الفواصل المجدولة — إعداد الجينغل الآلي + التشغيل المتواصل =====
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                AppIcons.MusicNote, null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                breaksSourceLabel(s, tracks, categories),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { requireManage { editingBreaks = s } }) {
                                Text("الفواصل")
                            }
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        snackbar.showSnackbar(locator.playScheduleWithBreaks(s))
                                    }
                                },
                                enabled = events.any { it.scheduleId == s.id }
                            ) {
                                Icon(Icons.Filled.PlayArrow, null, Modifier.size(16.dp))
                                Text(" تشغيل")
                            }
                        }
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
                        title = if (schedules.isEmpty()) "ابدأ بجدول ثم أضف فقراته" else "لا فقرات بعد",
                        body = "اضغط «فقرة مجدولة» أدناه — إذا لم يوجد جدول سيُطلب منك اسمه أولاً،" +
                            " ثم حدّد وقت الفقرة وأيامها ومحتواها (قائمة/مقطع/تسجيل) لتنطلق تلقائياً"
                    )
                }
            }
            items(sortedEvents, key = { "event-${it.id}" }) { e ->
                val lastExecution = executions.firstOrNull { it.eventId == e.id }
                val isNextUp = e.id == nextEventId
                val contentLabel = EventContentAr.label(
                    playlistName = e.playlistId?.let { pid -> playlists.firstOrNull { it.id == pid }?.name },
                    trackTitle = e.singleTrackId?.let { tid -> tracks.firstOrNull { it.id == tid }?.title },
                    recordingTitle = e.recordingId?.let { rid -> recordings.firstOrNull { it.id == rid }?.title }
                )
                Card(
                    Modifier.fillMaxWidth().animateContentSize(),
                    colors = if (isNextUp) CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                    ) else CardDefaults.cardColors(),
                    border = if (isNextUp) BorderStroke(
                        1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                    ) else null
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (e.imagePath != null) {
                            SegmentImage(
                                e.imagePath,
                                Modifier.size(52.dp),
                                contentDescription = "صورة الفقرة"
                            )
                            Spacer(Modifier.padding(start = 10.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // شارة الوقت — متدرجة للفقرة القادمة (لغة بطاقة «الفقرة التالية» في الرئيسية)
                                if (isNextUp) {
                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        shadowElevation = 3.dp,
                                        color = Color.Transparent
                                    ) {
                                        Box(
                                            Modifier.background(
                                                MaterialTheme.gradients.hero.diagonalBrush(),
                                                MaterialTheme.shapes.small
                                            )
                                        ) {
                                            Text(
                                                ScheduleLogic.formatMinutes(e.startMinutes),
                                                Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontFeatureSettings = "tnum", fontWeight = FontWeight.Bold
                                                ),
                                                color = Color.White
                                            )
                                        }
                                    }
                                } else {
                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                    ) {
                                        Text(
                                            ScheduleLogic.formatMinutes(e.startMinutes),
                                            Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontFeatureSettings = "tnum", fontWeight = FontWeight.Bold
                                            ),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    e.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (isNextUp) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            // v0.8.0: الأيام كرقائق مصغرة — أسرع قراءة من النص الطويل
                            WeekDayPills(e.weekDaysMask)
                            Text(
                                contentLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (EventContentAr.hasContent(e.playlistId, e.singleTrackId, e.recordingId))
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!e.noteText.isNullOrBlank()) {
                                Text(
                                    e.noteText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
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
                                    SegmentImageStore.delete(e.imagePath)
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
            recordings = recordings,
            scripts = scripts,
            onDismiss = { editingEvent = null },
            onSave = { event ->
                scope.launch {
                    if (state.originalId == null) locator.schedules.addEvent(event)
                    else locator.schedules.updateEvent(event)
                    locator.rearmScheduleAlarms()
                    // v0.13.0: أول فقرة تُحفظ → أقم خدمة الأتمتة الحارسة إن لم تكن حية
                    com.saidjte.schoolradio.scheduler.AutomationService.start(context)
                    snackbar.showSnackbar("حُفظت الفقرة وأُعيد ضبط المنبهات")
                }
                editingEvent = null
            }
        )
    }

    // ===== حوار إعداد الفواصل المجدولة (v0.12.0) =====
    editingBreaks?.let { s ->
        BreaksEditorDialog(
            schedule = s,
            tracks = tracks,
            categories = categories,
            onDismiss = { editingBreaks = null },
            onSave = { enabled, trackId, categoryId ->
                scope.launch {
                    // نقرأ الجدول حديثاً — نسخة s قد تكون قديمة لو تغيرت حالة الجدول
                    val fresh = locator.schedules.scheduleById(s.id) ?: s
                    locator.schedules.setBreakConfig(fresh, enabled, trackId, categoryId)
                    snackbar.showSnackbar(
                        if (enabled) "فُعّلت الفواصل الآلية بين فقرات «${fresh.name}»"
                        else "عُطّلت الفواصل في «${fresh.name}»"
                    )
                }
                editingBreaks = null
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

    // حوار إنشاء جدول — بعد الحفظ يُفتح محرر الفقرة مباشرة (إصلاح v0.5.0)
    if (creatingSchedule) {
        TextDialog(
            title = "جدول جديد", label = "اسم الجدول (مثل: طابور الصباح)",
            onDismiss = { creatingSchedule = false }
        ) { name ->
            scope.launch {
                val id = locator.schedules.createSchedule(name)
                snackbar.showSnackbar("أُنشئ الجدول «$name» — أضف الآن فقراته")
                editingEvent = EventEditorState(scheduleId = id)
            }
            creatingSchedule = false
        }
    }
}

/**
 * v0.12.0 — وصف مصدر جينغلات الفواصل لبطاقة الجدول (سطر صغير تحت الاسم).
 * يظهر اسم المقطع/التصنيف الفعلي — أو تعليلاً واضحاً إذا حُذف المصدر.
 */
internal fun breaksSourceLabel(
    schedule: RadioScheduleEntity,
    tracks: List<AudioTrackEntity>,
    categories: List<CategoryEntity>
): String = when {
    !schedule.breakEnabled -> "الفواصل بين الفقرات: معطّلة"
    schedule.breakTrackId != null ->
        "الفواصل: جينغل «${tracks.firstOrNull { it.id == schedule.breakTrackId }?.title ?: "مقطع محذوف"}»"
    schedule.breakCategoryId != null ->
        "الفواصل: عشوائي من «${
            categories.firstOrNull { it.id == schedule.breakCategoryId }?.name ?: "تصنيف محذوف"
        }»"
    else -> "الفواصل: مفعّلة بلا جينغل — اختر المصدر"
}

/**
 * v0.12.0 — حوار إعداد الفواصل المجدولة لجدول:
 *  - مفتاح تفعيل الفواصل الآلية بين فقرات هذا الجدول.
 *  - نمطان لمصدر الجينغل: مقطع محدد من المكتبة (يناسب هوية البرنامج الثابتة)
 *    أو اختيار عشوائي من تصنيف كامل (يناسب تجمعات جينغلات الانتقال) —
 *    الاختيار العشوائي يستبعد التكرار المباشر تلقائياً.
 */
@Composable
private fun BreaksEditorDialog(
    schedule: RadioScheduleEntity,
    tracks: List<AudioTrackEntity>,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (enabled: Boolean, trackId: Long?, categoryId: Long?) -> Unit
) {
    var enabled by remember { mutableStateOf(schedule.breakEnabled) }
    // نمط المصدر — يُستنتج من الإعداد الحالي (افتراضي المقطع المحدد)
    var modeIsTrack by remember { mutableStateOf(schedule.breakCategoryId == null) }
    var selectedTrackId by remember { mutableStateOf(schedule.breakTrackId) }
    var selectedCategoryId by remember { mutableStateOf(schedule.breakCategoryId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("الفواصل المجدولة") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "جينغة انتقال تنطلق آلياً بين فقرات «${schedule.name}»: " +
                        "في زر «تشغيل» المتواصل، وبعد كل فقرة مجدولة إن وُجدت فقرة تالية اليوم.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "فواصل آلية بين الفقرات",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { enabled = !enabled }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = modeIsTrack,
                        onClick = { modeIsTrack = true },
                        label = { Text("جينغل محدد") }
                    )
                    FilterChip(
                        selected = !modeIsTrack,
                        onClick = { modeIsTrack = false },
                        label = { Text("عشوائي من تصنيف") }
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (modeIsTrack) {
                    if (tracks.isEmpty()) {
                        Text(
                            "المكتبة فارغة — استورد جينغلات الانتقال من المكتبة أولاً",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Column(Modifier.heightIn(max = 260.dp)) {
                            tracks.forEach { t ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedTrackId = t.id }
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selectedTrackId == t.id,
                                        onClick = { selectedTrackId = t.id }
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            t.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (!t.isAvailable) {
                                            Text(
                                                "غير متاح حالياً على الجهاز",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    if (categories.isEmpty()) {
                        Text(
                            "لا تصنيفات بعد — أنشئ تصنيف «جينغلات» من المكتبة أولاً",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Column(Modifier.heightIn(max = 260.dp)) {
                            categories.forEach { c ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedCategoryId = c.id }
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selectedCategoryId == c.id,
                                        onClick = { selectedCategoryId = c.id }
                                    )
                                    Text(
                                        c.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
                if (enabled && modeIsTrack && selectedTrackId == null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "اختر مقطعاً لتكون الفواصل فعّالة عند الحفظ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // الحفظ يفعّل الفواصل فقط إذا كان المصدر محدداً فعلاً — لا إعداد نصف مكتمل
                    val sourceReady =
                        if (modeIsTrack) selectedTrackId != null else selectedCategoryId != null
                    onSave(
                        enabled && sourceReady,
                        if (modeIsTrack) selectedTrackId else null,
                        if (modeIsTrack) null else selectedCategoryId
                    )
                }
            ) { Text("حفظ") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

/** حالة محرر الفقرة (جديد أو موجود) */
data class EventEditorState(
    val originalId: Long? = null,
    val scheduleId: Long,
    val title: String = "",
    val timeText: String = "09:45",
    val weekDaysMask: Int = 0b0111110, // الاثنين..الجمعة افتراضياً
    val playlistId: Long? = null,
    val singleTrackId: Long? = null,
    val recordingId: Long? = null,
    val noteText: String? = null,
    val imagePath: String? = null,
    val scriptId: Long? = null
) {
    companion object {
        fun from(e: ScheduledEventEntity) = EventEditorState(
            originalId = e.id.takeIf { it > 0 },
            scheduleId = e.scheduleId,
            title = e.title,
            timeText = ScheduleLogic.formatMinutes(e.startMinutes),
            weekDaysMask = e.weekDaysMask,
            playlistId = e.playlistId,
            singleTrackId = e.singleTrackId,
            recordingId = e.recordingId,
            noteText = e.noteText,
            imagePath = e.imagePath,
            scriptId = e.scriptId
        )
    }
}

private val ARABIC_DAYS = listOf("الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت", "الأحد")

/** تسمية عربية لقناع الأيام */
fun dayMaskLabelAr(mask: Int): String =
    (1..7).filter { (mask shr (it - 1)) and 1 == 1 }
        .joinToString("، ") { ARABIC_DAYS[it - 1] }
        .ifBlank { "لا أيام" }

/**
 * رقائق أيام الأسبوع المصغرة (v0.8.0): حرف واحد لكل يوم —
 * الايام المفعّلة بلون الثيم والمطفأة باهتة، أسرع قراءة من النص الطويل.
 * ترتيب ISO: الاثنين ← الأحد (نفس ترتيب بتات weekDaysMask).
 */
@Composable
fun WeekDayPills(mask: Int, modifier: Modifier = Modifier) {
    val dayLetters = listOf("ن", "ث", "ر", "خ", "ج", "س", "ح")
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        (1..7).forEach { dayIso ->
            val active = ((mask shr (dayIso - 1)) and 1) == 1
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Text(
                    dayLetters[dayIso - 1],
                    Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    color = if (active) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

/**
 * بطاقة إحصاء صغيرة لملخص الجدولة (v0.8.0):
 * قيمة بارزة بلون دلالي + تسمية — نفس لغة StatChip في الرئيسية لكن أفقية مختصرة.
 */
@Composable
private fun ScheduleStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.primary
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 1.dp
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                color = valueColor,
                fontWeight = FontWeight.Bold
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EventEditorDialog(
    initial: EventEditorState,
    schedules: List<RadioScheduleEntity>,
    events: List<ScheduledEventEntity>,
    recordings: List<RecordingEntity>,
    scripts: List<com.saidjte.schoolradio.data.scripts.ScriptItem>,
    onDismiss: () -> Unit,
    onSave: (ScheduledEventEntity) -> Unit
) {
    val locator = ServiceLocator
    val context = LocalContext.current
    val imageScope = rememberCoroutineScope()
    var scheduleId by remember { mutableStateOf(initial.scheduleId) }
    var title by remember { mutableStateOf(initial.title) }
    var timeText by remember { mutableStateOf(initial.timeText) }
    var mask by remember { mutableStateOf(initial.weekDaysMask) }
    var playlistId by remember { mutableStateOf(initial.playlistId) }
    var singleTrackId by remember { mutableStateOf(initial.singleTrackId) }
    var recordingId by remember { mutableStateOf(initial.recordingId) }
    var noteText by remember { mutableStateOf(initial.noteText ?: "") }
    var imagePath by remember { mutableStateOf(initial.imagePath) }
    // v0.13.0: سكربت المذيع المرتبط بالفقرة — يفتح على شاشة العرض لحظة الإطلاق
    var scriptId by remember { mutableStateOf(initial.scriptId) }
    var conflictError by remember { mutableStateOf<String?>(null) }

    val playlists by locator.playlists.playlists.collectAsStateWithLifecycle(initialValue = emptyList())
    val tracks by locator.library.tracks.collectAsStateWithLifecycle(initialValue = emptyList())

    // اختيار صورة الفقرة عبر Photo Picker (بلا صلاحيات تخزين) + نسخة مضغوطة داخل التطبيق
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            imageScope.launch {
                val saved = SegmentImageStore.saveFrom(context, uri)
                if (saved != null) {
                    SegmentImageStore.delete(imagePath)
                    imagePath = saved
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.originalId == null) "فقرة مجدولة جديدة" else "تعديل الفقرة") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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

                Text("المحتوى الصوتي", style = MaterialTheme.typography.labelLarge)
                Text(
                    "اختر واحداً من الثلاثة: قائمة تشغيل، أو مقطع من المكتبة، أو تسجيلاً من الأرشيف",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SimpleDropdownRow(
                    label = "قائمة تشغيل",
                    selected = playlists.firstOrNull { it.id == playlistId }?.name ?: "بلا",
                    options = playlists.map { it.id to it.name },
                    onPick = {
                        playlistId = it; singleTrackId = null; recordingId = null
                    },
                    onClear = { playlistId = null }
                )
                SimpleDropdownRow(
                    label = "مقطع من المكتبة",
                    selected = tracks.firstOrNull { it.id == singleTrackId }?.title ?: "بلا",
                    options = tracks.map { it.id to it.title },
                    onPick = {
                        singleTrackId = it; playlistId = null; recordingId = null
                    },
                    onClear = { singleTrackId = null }
                )
                SimpleDropdownRow(
                    label = "تسجيل من الأرشيف",
                    selected = recordings.firstOrNull { it.id == recordingId }?.title ?: "بلا",
                    options = recordings.map { it.id to it.title },
                    onPick = {
                        recordingId = it; playlistId = null; singleTrackId = null
                    },
                    onClear = { recordingId = null }
                )

                // ===== v0.13.0: سكربت المذيع المرتبط بالفقرة =====
                Text(
                    "سكربت المذيع (Teleprompter) — يظهر جاهزاً للقراءة على شاشة العرض لحظة انطلاق الفقرة",
                    style = MaterialTheme.typography.labelLarge
                )
                SimpleDropdownRow(
                    label = "سكربت الفقرة",
                    selected = scripts.firstOrNull { it.id == scriptId }?.title ?: "بلا",
                    options = scripts.map { it.id to it.title },
                    onPick = { scriptId = it },
                    onClear = { scriptId = null }
                )

                // ===== نص الفقرة (يظهر على شاشة العرض على الهواء) =====
                OutlinedTextField(
                    value = noteText, onValueChange = { noteText = it },
                    label = { Text("نص الفقرة (يظهر على شاشة العرض)") },
                    placeholder = { Text("مثال: استمعوا إلى كلمة اليوم مع زميلنا…") },
                    minLines = 2,
                    maxLines = 4
                )

                // ===== صورة الفقرة =====
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (imagePath != null) {
                        SegmentImage(imagePath, Modifier.size(64.dp), contentDescription = "صورة الفقرة")
                    }
                    Column {
                        TextButton(onClick = {
                            imagePicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }) {
                            Icon(Icons.Filled.Add, null)
                            Text(if (imagePath == null) " إضافة صورة" else " تغيير الصورة")
                        }
                        if (imagePath != null) {
                            TextButton(onClick = {
                                SegmentImageStore.delete(imagePath)
                                imagePath = null
                            }) { Text("إزالة الصورة") }
                        }
                    }
                }

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
                            // استُبدلت صورة قديمة؟ نحذف ملفها من القرص (لا ملفات يتيمة)
                            if (initial.imagePath != null && initial.imagePath != imagePath) {
                                SegmentImageStore.delete(initial.imagePath)
                            }
                            onSave(
                                ScheduledEventEntity(
                                    id = initial.originalId ?: 0,
                                    scheduleId = scheduleId,
                                    startMinutes = minutes,
                                    weekDaysMask = mask,
                                    title = title.trim(),
                                    playlistId = playlistId,
                                    singleTrackId = singleTrackId,
                                    recordingId = recordingId,
                                    noteText = noteText.trim().takeIf { it.isNotBlank() },
                                    imagePath = imagePath,
                                    scriptId = scriptId
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

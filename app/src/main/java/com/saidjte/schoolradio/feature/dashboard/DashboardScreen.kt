package com.saidjte.schoolradio.feature.dashboard

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saidjte.schoolradio.audio.AudioRoutingManager
import com.saidjte.schoolradio.core.theme.status
import com.saidjte.schoolradio.core.ui.AppIcons
import com.saidjte.schoolradio.core.ui.EmptyStateCard
import com.saidjte.schoolradio.core.ui.StatusBadge
import com.saidjte.schoolradio.core.util.ScheduleLogic
import com.saidjte.schoolradio.data.local.entity.ExecutionStatus
import com.saidjte.schoolradio.data.local.entity.ScheduledEventEntity
import com.saidjte.schoolradio.data.settings.AppSettings
import com.saidjte.schoolradio.di.ServiceLocator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * لوحة التحكم الرئيسية (v0.4.0 — إعادة تصميم):
 *  - تحية زمنية باسم المستخدم الحالي ودوره
 *  - بطاقة بطل متدرجة: اسم المدرسة + ساعة حية بأرقام جدولية
 *  - شريط إحصاءات اليوم: الفقرات / المكتملة / المتبقية
 *  - بطاقة «الفقرة التالية» مع عدّاد تنازلي حي بالثانية
 *  - بطاقة بث مباشر محسّنة: نبضات لمس، حلقة مستوى انسيابية، شارة «مباشر» متحركة
 *  - حالات فارغة بإجراءات انتقال مباشر (الجدولة/المكتبة)
 */

/** التحية العربية حسب ساعة اليوم */
private fun greetingFor(hour: Int): String = when (hour) {
    in 5..11 -> "صباح الخير"
    in 12..16 -> "نهارك سعيد"
    in 17..21 -> "مساء الخير"
    else -> "ليلة موفقة"
}

@Composable
fun DashboardScreen(
    snackbar: androidx.compose.material3.SnackbarHostState,
    onNavigateToSchedule: () -> Unit = {},
    onNavigateToLibrary: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val locator = ServiceLocator

    val settings by locator.settings.settings
        .collectAsStateWithLifecycle(initialValue = AppSettings())
    val playback by locator.playback.state.collectAsStateWithLifecycle()
    val currentUser by locator.session.currentUser
        .collectAsStateWithLifecycle(initialValue = null)

    // نبضة زمنية كل ثانية — تغذي الساعة والعدّاد التنازلي معًا
    val nowTick by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }
    val nowText = remember(nowTick) {
        SimpleDateFormat("EEEE d MMMM yyyy — HH:mm:ss", Locale("ar")).format(Date(nowTick))
    }

    // أحداث اليوم + سجل التنفيذ لتلوين الحالات
    val todayEvents by remember { locator.schedules.todayEventsFlow() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val executions by locator.schedules.recentExecutions
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val todayKey = ScheduleLogic.dateKey(nowTick)
    val minutesNow = ScheduleLogic.minutesOfDay(nowTick)

    // حالة الميكروفون والمستوى الحي — من مركز الحالة المشترك (ServiceLocator)
    val mic = locator.mic
    val isLive by locator.micLive.collectAsStateWithLifecycle()
    val micLevelRaw by locator.micLevel.collectAsStateWithLifecycle()
    val micError by locator.micMessage.collectAsStateWithLifecycle()
    val micLevel by animateFloatAsState(
        targetValue = micLevelRaw.coerceIn(0f, 1f),
        animationSpec = tween(90),
        label = "mic-level-smooth"
    )
    var route by remember { mutableStateOf(AudioRoutingManager.Route.SPEAKER) }

    // حالة التسجيل — مؤقّت + وضع + نشاط
    val recActive by locator.recordings.isRecording.collectAsStateWithLifecycle()
    val recElapsed by locator.recordings.elapsedMs.collectAsStateWithLifecycle()
    val recMode by locator.recordings.mode.collectAsStateWithLifecycle()

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        scope.launch {
            if (granted) {
                mic.start(route)?.let { snackbar.showSnackbar(it) }
            } else {
                snackbar.showSnackbar("بدون صلاحية الميكروفون لا يمكن البث المباشر")
            }
        }
    }

    /** بدء التسجيل (بث حي إن كان البث نشطاً وإلا فقرة تحضيرية) بصلاحية الميكروفون */
    fun startRecordingOrMemo() {
        if (!mic.hasPermission()) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        scope.launch {
            val err = if (isLive) locator.recordings.startLiveRecording()
            else locator.recordings.startMemoRecording()
            err?.let { snackbar.showSnackbar(it) }
        }
    }

    // إحصاءات اليوم
    val completedToday = executions.count {
        it.dateKey == todayKey && it.status == ExecutionStatus.COMPLETED
    }
    val remainingToday = todayEvents.count { it.startMinutes > minutesNow }
    val nextEvent = todayEvents.filter { it.startMinutes > minutesNow }.minByOrNull { it.startMinutes }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ===== التحية الزمنية =====
        item {
            Column {
                Text(
                    "${greetingFor(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))}" +
                        (currentUser?.let { "، ${it.name}" } ?: ""),
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    if (currentUser != null)
                        "${currentUser!!.roleEnum().labelAr} — جاهز للإذاعة"
                    else "جاهز للإذاعة",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ===== بطاقة البطل: المدرسة + الساعة الحية =====
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 4.dp, shape = MaterialTheme.shapes.large)
                    .background(
                        brush = Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
                                MaterialTheme.colorScheme.tertiary
                            )
                        ),
                        shape = MaterialTheme.shapes.large
                    )
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        settings.schoolName,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Text(
                        "الإذاعة المدرسية — نظام التحكم المحلي",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            AppIcons.ScheduleClock, null,
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            nowText,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 17.sp,
                                fontFeatureSettings = "tnum"
                            ),
                            color = Color.White
                        )
                    }
                }
            }
        }

        // ===== شريط إحصاءات اليوم =====
        if (todayEvents.isNotEmpty()) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatChip("فقرات اليوم", "${todayEvents.size}", Modifier.weight(1f))
                    StatChip("أُنجزت", "$completedToday", Modifier.weight(1f), MaterialTheme.status.success)
                    StatChip("متبقية", "$remainingToday", Modifier.weight(1f), MaterialTheme.colorScheme.primary)
                }
            }
        }

        // ===== بطاقة «الفقرة التالية» مع العدّاد =====
        nextEvent?.let { next ->
            item { NextUpCard(next, nowTick, todayKey, executions) }
        }

        // ===== المشغل الحالي =====
        if (playback.hasQueue) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("قيد التشغيل الآن", style = MaterialTheme.typography.titleSmall)
                        Text(
                            playback.currentTitle ?: "لا شيء يعمل حالياً",
                            style = MaterialTheme.typography.titleMedium
                        )
                        LinearProgressIndicator(
                            progress = {
                                if (playback.durationMs > 0)
                                    playback.positionMs.toFloat() / playback.durationMs else 0f
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${ScheduleLogic.formatDuration(playback.positionMs)} / " +
                                    ScheduleLogic.formatDuration(playback.durationMs),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { locator.playback.previous() }) {
                                Icon(AppIcons.SkipPrevious, "السابق")
                            }
                            IconButton(onClick = { locator.playback.playPause() }) {
                                Icon(
                                    if (playback.isPlaying) AppIcons.Pause
                                    else Icons.Filled.PlayArrow,
                                    "تشغيل/إيقاف"
                                )
                            }
                            IconButton(onClick = { locator.playback.next() }) {
                                Icon(AppIcons.SkipNext, "التالي")
                            }
                        }
                    }
                }
            }
        }

        // ===== زر البث المباشر الضخم + زر التسجيل =====
        item {
            LiveBroadcastCard(
                isLive = isLive,
                level = micLevel,
                route = route,
                error = micError,
                recActive = recActive,
                recElapsed = recElapsed,
                recMode = recMode,
                onRouteChange = { route = it },
                onTalkStart = {
                    if (mic.hasPermission()) {
                        scope.launch {
                            mic.start(route)?.let { snackbar.showSnackbar(it) }
                        }
                    } else {
                        micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onTalkEnd = { mic.stop() },
                onRecordToggle = {
                    if (recActive) {
                        locator.recordings.stopRecording()
                        scope.launch { snackbar.showSnackbar("حُفظ التسجيل في أرشيف التسجيلات") }
                    } else {
                        startRecordingOrMemo()
                    }
                }
            )
        }

        // ===== جدول اليوم =====
        item { Text("فقرات اليوم", style = MaterialTheme.typography.titleMedium) }
        if (todayEvents.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = AppIcons.Event,
                    title = "لا توجد فقرات مجدولة لليوم",
                    body = "أنشئ فقرات الصباح من تبويب الجدولة ليعمل النظام تلقائياً في مواعيدها",
                    ctaLabel = "الذهاب إلى الجدولة",
                    onCta = onNavigateToSchedule
                )
            }
        } else {
            items(todayEvents, key = { it.id }) { event ->
                EventRow(event, executions, todayKey, minutesNow, todayEvents)
            }
        }

        // ===== اقتراح المكتبة عندما تكون فقرات اليوم قليلة =====
        if (todayEvents.isNotEmpty() && remainingToday == 0) {
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            AppIcons.MusicNote, null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text(
                                "انتهت فقرات اليوم",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                "حضّر فقرات الغد من المكتبة والقوائم",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        androidx.compose.material3.TextButton(onClick = onNavigateToLibrary) {
                            Text("المكتبة")
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

/** رقاقة إحصاء صغيرة: تسمية + قيمة كبيرة بلون اختياري */
@Composable
private fun StatChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                color = valueColor
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * بطاقة «الفقرة التالية»: عنوانها ووقتها وعدّاد تنازلي حي.
 * إذا كانت الفقرة قيد التشغيل فعلاً (سجل RUNNING) تتحول إلى شارة «قيد التشغيل».
 */
@Composable
private fun NextUpCard(
    next: ScheduledEventEntity,
    nowMs: Long,
    todayKey: String,
    executions: List<com.saidjte.schoolradio.data.local.entity.ScheduleExecutionEntity>
) {
    val running = executions.any {
        it.eventId == next.id && it.dateKey == todayKey && it.status == ExecutionStatus.RUNNING
    }
    val remainingMs = next.startMinutes * 60_000L - ScheduleLogic.minutesOfDay(nowMs) * 60_000L -
        (nowMs % 60_000L)
    val countdown = if (running) "الآن" else "تبدأ بعد ${ScheduleLogic.formatDuration(remainingMs.coerceAtLeast(0))}"

    Surface(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                Box(Modifier.padding(10.dp), contentAlignment = Alignment.Center) {
                    Text(
                        ScheduleLogic.formatMinutes(next.startMinutes),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("الفقرة التالية", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(next.title, style = MaterialTheme.typography.titleSmall)
            }
            Text(
                countdown,
                style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** سطر فقرة اليوم مع شريحة الحالة الدلالية وتمييز الفقرة قيد التشغيل */
@Composable
private fun EventRow(
    event: ScheduledEventEntity,
    executions: List<com.saidjte.schoolradio.data.local.entity.ScheduleExecutionEntity>,
    todayKey: String,
    minutesNow: Int,
    all: List<ScheduledEventEntity>
) {
    val st = MaterialTheme.status
    val execution = executions.firstOrNull {
        it.eventId == event.id && it.dateKey == todayKey
    }
    val isRunning = execution?.status == ExecutionStatus.RUNNING
    val statusLabel = when {
        execution?.status == ExecutionStatus.COMPLETED -> "مكتملة" to st.success
        isRunning -> "قيد التشغيل" to st.running
        execution?.status == ExecutionStatus.FAILED -> "فشلت" to st.failed
        execution?.status == ExecutionStatus.MISSED -> "فائتة" to st.warning
        event.startMinutes < minutesNow -> "لم تعمل" to st.warning
        else -> "مجدولة" to MaterialTheme.colorScheme.primary
    }
    val next = all.filter { it.startMinutes > event.startMinutes }
        .minByOrNull { it.startMinutes }

    Card(
        Modifier.fillMaxWidth().animateContentSize(),
        border = if (isRunning) BorderStroke(2.dp, st.running.copy(alpha = 0.5f)) else null,
        colors = if (isRunning) CardDefaults.cardColors(
            containerColor = st.running.copy(alpha = 0.08f)
        ) else CardDefaults.cardColors()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            ) {
                Text(
                    ScheduleLogic.formatMinutes(event.startMinutes),
                    Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFeatureSettings = "tnum", fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(event.title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    next?.let {
                        "الفقرة التالية: ${it.title} في ${ScheduleLogic.formatMinutes(it.startMinutes)}"
                    } ?: "آخر فقرة في جدول اليوم",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StatusBadge(statusLabel.first, statusLabel.second)
        }
    }
}

/**
 * بطاقة البث المباشر: زر ضخم «اضغط للتحدث» بنظام الضغط والاستمرار مع نبضات لمس،
 * حلقة مستوى صوت حية انسيابية، شارة «مباشر» نابضة، اختيار مسار الإخراج،
 * وزر تسجيل: يسجّل البث الجاري أو فقرة تحضيرية مستقلة.
 */
@Composable
private fun LiveBroadcastCard(
    isLive: Boolean,
    level: Float,
    route: AudioRoutingManager.Route,
    error: String?,
    recActive: Boolean,
    recElapsed: Long,
    recMode: com.saidjte.schoolradio.data.local.entity.RecordingSource?,
    onRouteChange: (AudioRoutingManager.Route) -> Unit,
    onTalkStart: () -> Unit,
    onTalkEnd: () -> Unit,
    onRecordToggle: () -> Unit
) {
    val st = MaterialTheme.status
    val haptics = LocalHapticFeedback.current

    val pulse = rememberInfiniteTransition(label = "live-pulse")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.15f, targetValue = 0.55f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "live-alpha"
    )

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isLive) st.liveContainer
            else MaterialTheme.colorScheme.surface
        ),
        border = if (isLive) BorderStroke(2.dp, st.live.copy(alpha = 0.5f)) else null
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("البث المباشر عبر الميكروفون", style = MaterialTheme.typography.titleMedium)
                AnimatedVisibility(
                    visible = isLive,
                    enter = fadeIn(tween(150)) + expandVertically(tween(150)),
                    exit = fadeOut(tween(150)) + shrinkVertically(tween(150))
                ) {
                    val livePulse = rememberInfiniteTransition(label = "badge-pulse")
                    val badgeAlpha by livePulse.animateFloat(
                        initialValue = 1f, targetValue = 0.35f,
                        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
                        label = "badge-alpha"
                    )
                    StatusBadge(
                        "● مباشر", st.live,
                        modifier = Modifier.padding(start = 8.dp)
                            .alpha(badgeAlpha)
                    )
                }
            }
            Text(
                "اضغط مع الاستمرار للتحدث — حرّر للإنهاء (تُخفَّض الموسيقى تلقائياً)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Box(Modifier.size(160.dp).padding(16.dp), contentAlignment = Alignment.Center) {
                val baseRing = MaterialTheme.colorScheme.primary
                Canvas(Modifier.fillMaxSize()) {
                    val ringColor = if (isLive) st.live else baseRing
                    val alpha = if (isLive) 0.25f + level * 0.75f else pulseAlpha
                    drawCircle(
                        color = ringColor.copy(alpha = alpha),
                        radius = size.minDimension / 2f,
                        style = Stroke(width = 14f),
                        center = Offset(size.width / 2f, size.height / 2f)
                    )
                }
                Surface(
                    modifier = Modifier
                        .size(96.dp)
                        .pointerInput(isLive) {
                            detectTapGestures(
                                onPress = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (!isLive) onTalkStart()
                                    try { awaitRelease() } finally {
                                        if (isLive) {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onTalkEnd()
                                        }
                                    }
                                }
                            )
                        },
                    shape = CircleShape,
                    color = if (isLive) st.live else MaterialTheme.colorScheme.primary,
                    shadowElevation = 6.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                AppIcons.Mic, null,
                                tint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.size(26.dp)
                            )
                            Text(
                                if (isLive) "تُبثّ الآن" else "اضغط\nللتحدث",
                                color = Color.White,
                                style = MaterialTheme.typography.titleSmall,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // ===== زر التسجيل =====
            // أثناء البث: يسجّل البث الحي | بدون بث: يسجّل فقرة تحضيرية مستقلة
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    // هالة نابضة حمراء أثناء التسجيل — مؤشر بصري لا يُخطئه أحد
                    if (recActive) {
                        val recPulse = rememberInfiniteTransition(label = "rec-pulse")
                        val recAlpha by recPulse.animateFloat(
                            initialValue = 0.2f, targetValue = 0.7f,
                            animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
                            label = "rec-alpha"
                        )
                        Canvas(Modifier.size(64.dp)) {
                            drawCircle(
                                color = st.live.copy(alpha = recAlpha),
                                radius = size.minDimension / 2f,
                                style = Stroke(width = 10f)
                            )
                        }
                    }
                    Surface(
                        modifier = Modifier
                            .size(56.dp)
                            .pointerInput(recActive) {
                                detectTapGestures {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onRecordToggle()
                                }
                            },
                        shape = CircleShape,
                        color = if (recActive) st.live else MaterialTheme.colorScheme.secondary,
                        shadowElevation = 4.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (recActive) AppIcons.Stop else AppIcons.Record,
                                contentDescription = if (recActive) "إيقاف التسجيل" else "بدء التسجيل",
                                tint = Color.White
                            )
                        }
                    }
                }
                Column {
                    Text(
                        when {
                            recActive && recMode?.name == "PTT" -> "تُسجَّل الفقرة الآن"
                            recActive -> "تسجيل فقرة تحضيرية…"
                            isLive -> "سجّل البث الحي الآن"
                            else -> "تسجيل فقرة تحضيرية"
                        },
                        style = MaterialTheme.typography.titleSmall
                    )
                    if (recActive) {
                        Text(
                            "${ScheduleLogic.formatDuration(recElapsed)} — سيُحفظ تلقائياً في الأرشيف",
                            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            if (isLive) "AAC ‏128kbps — حفظ فوري عند الإيقاف"
                            else "بلا بث للمكبرات — تحضير قبل الإذاعة",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // اختيار مسار الإخراج (يُقفل أثناء البث)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    AudioRoutingManager.Route.SPEAKER,
                    AudioRoutingManager.Route.WIRED,
                    AudioRoutingManager.Route.BLUETOOTH,
                    AudioRoutingManager.Route.MEDIA_DEFAULT
                ).forEach { r ->
                    AssistChip(
                        onClick = { if (!isLive) onRouteChange(r) },
                        label = { Text(r.labelAr) },
                        enabled = !isLive
                    )
                }
            }
            error?.let {
                Text(it, color = st.failed, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

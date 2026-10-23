package com.saidjte.schoolradio.feature.dashboard

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saidjte.schoolradio.audio.AudioRoutingManager
import com.saidjte.schoolradio.core.ui.AppIcons
import com.saidjte.schoolradio.core.util.ScheduleLogic
import com.saidjte.schoolradio.data.local.entity.ExecutionStatus
import com.saidjte.schoolradio.data.local.entity.ScheduledEventEntity
import com.saidjte.schoolradio.data.settings.AppSettings
import com.saidjte.schoolradio.di.ServiceLocator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * لوحة التحكم الرئيسية: هوية المدرسة، الساعة الحية، حالة المشغل،
 * الفقرة التالية، جدول اليوم مع حالة كل فقرة، وزر البث المباشر الضخم.
 */
@Composable
fun DashboardScreen(snackbar: androidx.compose.material3.SnackbarHostState) {
    val scope = rememberCoroutineScope()
    val locator = ServiceLocator

    val settings by locator.settings.settings
        .collectAsStateWithLifecycle(initialValue = AppSettings())
    val playback by locator.playback.state.collectAsStateWithLifecycle()

    // ساعة حية تتحدث كل ثانية
    val nowText by produceState("…") {
        while (true) {
            value = SimpleDateFormat("EEEE d MMMM yyyy — HH:mm:ss", Locale("ar")).format(Date())
            delay(1000)
        }
    }

    // أحداث اليوم + سجل التنفيذ لتلوين الحالات
    val todayEvents by remember { locator.schedules.todayEventsFlow() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val executions by locator.schedules.recentExecutions
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val todayKey = ScheduleLogic.dateKey(System.currentTimeMillis())
    val minutesNow = ScheduleLogic.minutesOfDay(System.currentTimeMillis())

    // حالة الميكروفون والمستوى الحي — من مركز الحالة المشترك (ServiceLocator)
    val mic = locator.mic
    val isLive by locator.micLive.collectAsStateWithLifecycle()
    val micLevel by locator.micLevel.collectAsStateWithLifecycle()
    val micError by locator.micMessage.collectAsStateWithLifecycle()
    var route by remember { mutableStateOf(AudioRoutingManager.Route.SPEAKER) }

    // حالة التسجيل (v0.3.0) — مؤقّت + وضع + نشاط
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

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ===== الهوية والساعة =====
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        settings.schoolName,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "الإذاعة المدرسية — نظام التحكم المحلي",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.DateRange, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text("  $nowText", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // ===== المشغل الحالي =====
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("قيد التشغيل الآن", style = MaterialTheme.typography.titleSmall)
                    Text(
                        playback.currentTitle ?: "لا شيء يعمل حالياً",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (playback.hasQueue) {
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
                                    else Icons.Filled.PlayArrow, "تشغيل/إيقاف"
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
        item {
            Text("فقرات اليوم", style = MaterialTheme.typography.titleMedium)
        }
        if (todayEvents.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        "لا توجد فقرات مجدولة لليوم — أنشئها من تبويب الجدولة",
                        Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(todayEvents, key = { it.id }) { event ->
                EventRow(event, executions, todayKey, minutesNow, todayEvents)
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

/** سطر فقرة اليوم مع شريحة الحالة (مجدولة/قيد التشغيل/مكتملة/فائتة/فشلت) */
@Composable
private fun EventRow(
    event: ScheduledEventEntity,
    executions: List<com.saidjte.schoolradio.data.local.entity.ScheduleExecutionEntity>,
    todayKey: String,
    minutesNow: Int,
    all: List<ScheduledEventEntity>
) {
    val execution = executions.firstOrNull {
        it.eventId == event.id && it.dateKey == todayKey
    }
    val statusLabel = when {
        execution?.status == ExecutionStatus.COMPLETED -> "مكتملة" to Color(0xFF2E7D32)
        execution?.status == ExecutionStatus.RUNNING -> "قيد التشغيل" to Color(0xFF1565C0)
        execution?.status == ExecutionStatus.FAILED -> "فشلت" to Color(0xFFC62828)
        execution?.status == ExecutionStatus.MISSED -> "فائتة" to Color(0xFF9E9D24)
        event.startMinutes < minutesNow -> "لم تعمل" to Color(0xFF9E9D24)
        else -> "مجدولة" to Color(0xFF00695C)
    }
    val next = all.filter { it.startMinutes > event.startMinutes }
        .minByOrNull { it.startMinutes }

    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                ScheduleLogic.formatMinutes(event.startMinutes),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
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
            Surface(
                color = statusLabel.second.copy(alpha = 0.12f),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    statusLabel.first,
                    Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = statusLabel.second,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

/**
 * بطاقة البث المباشر: زر ضخم «اضغط للتحدث» بنظام الضغط والاستمرار،
 * حلقة مستوى صوت حية، اختيار مسار الإخراج، مؤشر تفعيل واضح للميكروفون،
 * وزر تسجيل (v0.3.0): يسجّل البث الجاري أو فقرة تحضيرية مستقلة.
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
    val pulse = rememberInfiniteTransition(label = "live-pulse")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.15f, targetValue = 0.55f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "live-alpha"
    )

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isLive) Color(0xFFC62828).copy(alpha = 0.08f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("البث المباشر عبر الميكروفون", style = MaterialTheme.typography.titleMedium)
            Text(
                "اضغط مع الاستمرار للتحدث — حرّر للإنهاء (تُخفَّض الموسيقى تلقائياً)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Box(Modifier.size(160.dp).padding(16.dp), contentAlignment = Alignment.Center) {
                val baseRing = MaterialTheme.colorScheme.primary
                Canvas(Modifier.fillMaxSize()) {
                    val ringColor = if (isLive) Color(0xFFC62828) else baseRing
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
                                    if (!isLive) onTalkStart()
                                    try { awaitRelease() } finally { if (isLive) onTalkEnd() }
                                }
                            )
                        },
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = if (isLive) Color(0xFFC62828) else MaterialTheme.colorScheme.primary,
                    shadowElevation = 6.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            if (isLive) "تُبثّ الآن" else "اضغط\nللتحدث",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // ===== زر التسجيل (v0.3.0) =====
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
                                color = Color(0xFFC62828).copy(alpha = recAlpha),
                                radius = size.minDimension / 2f,
                                style = Stroke(width = 10f)
                            )
                        }
                    }
                    Surface(
                        modifier = Modifier
                            .size(56.dp)
                            .pointerInput(recActive) {
                                detectTapGestures { onRecordToggle() }
                            },
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = if (recActive) Color(0xFFC62828)
                        else MaterialTheme.colorScheme.secondary,
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
                            style = MaterialTheme.typography.bodySmall,
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
                Text(it, color = Color(0xFFC62828), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

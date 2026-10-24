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
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saidjte.schoolradio.audio.AudioRoutingManager
import com.saidjte.schoolradio.audio.MicPhase
import com.saidjte.schoolradio.core.theme.gradients
import com.saidjte.schoolradio.core.theme.status
import com.saidjte.schoolradio.core.ui.AppIcons
import com.saidjte.schoolradio.core.ui.EmptyStateCard
import com.saidjte.schoolradio.core.ui.LiveWaveform
import com.saidjte.schoolradio.core.ui.StatusBadge
import com.saidjte.schoolradio.core.ui.diagonalBrush
import com.saidjte.schoolradio.core.ui.driftingGradient
import com.saidjte.schoolradio.core.util.AppFormat
import com.saidjte.schoolradio.core.util.ScheduleLogic
import com.saidjte.schoolradio.data.local.entity.ExecutionStatus
import com.saidjte.schoolradio.data.local.entity.ScheduledEventEntity
import com.saidjte.schoolradio.data.settings.AppSettings
import com.saidjte.schoolradio.di.ServiceLocator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

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
    onNavigateToLibrary: () -> Unit = {},
    onOpenOnAir: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenStudio: () -> Unit = {},
    onOpenScripts: () -> Unit = {},
    onOpenCartwall: () -> Unit = {}
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
        // v0.6.0: أرقام مغربية (1234) مع أسماء عربية — عبر المنسّق المركزي
        AppFormat.dateLong(nowTick)
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
    // v0.8.0: مرحلة البث (جارٍ البدء/مباشر/جارٍ الإيقاف) لاستجابة زر فورية وسلسة
    val micPhase by mic.phase.collectAsStateWithLifecycle()
    val micLevelRaw by locator.micLevel.collectAsStateWithLifecycle()
    val micError by locator.micMessage.collectAsStateWithLifecycle()
    val micLevel by animateFloatAsState(
        targetValue = micLevelRaw.coerceIn(0f, 1f),
        animationSpec = tween(90),
        label = "mic-level-smooth"
    )
    // v0.10.0: المسار المشترك مركزياً — الرئيسية وقارئ السكربت يتقاسمان نفس الاختيار
    val route by locator.broadcastRoute.collectAsStateWithLifecycle()
    // v0.10.0: نمط البث (استمرار / ضغطة) محفوظ في الإعدادات
    val settingsState by locator.settings.settings
        .collectAsStateWithLifecycle(initialValue = com.saidjte.schoolradio.data.settings.AppSettings())
    val micMode = remember(settingsState.micMode) {
        com.saidjte.schoolradio.audio.MicMode.from(settingsState.micMode)
    }

    // حالة التسجيل — مؤقّت + وضع + نشاط
    val recActive by locator.recordings.isRecording.collectAsStateWithLifecycle()
    val recElapsed by locator.recordings.elapsedMs.collectAsStateWithLifecycle()
    val recMode by locator.recordings.mode.collectAsStateWithLifecycle()
    // v0.12.0: مستوى الموجة الحية للتسجيل — ما يُكتب في الملف فعلاً (بث PTT أو فقرة MEMO)
    val recLevelRaw by locator.recordings.recordLevel.collectAsStateWithLifecycle()

    // v0.12.0: مصدر الموجة الموحد — أثناء التسجيل مستوى الملف، وإلا مستوى البث الحي
    val waveActive = isLive || recActive
    val waveSource = if (recActive) recLevelRaw else micLevelRaw
    val waveLevel by animateFloatAsState(
        targetValue = waveSource.coerceIn(0f, 1f),
        animationSpec = tween(80),
        label = "wave-source-smooth"
    )

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        scope.launch {
            if (granted) {
                // v0.8.0: البدء غير متزامن — النتيجة/الأخطاء عبر رد النداء وآلة الحالات
                mic.start(route) { err -> err?.let { m -> scope.launch { snackbar.showSnackbar(m) } } }
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
        // ===== بطاقة البطل: تحية + المدرسة + الساعة الحية (v0.7.0 — تدرج حي) =====
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 10.dp, shape = MaterialTheme.shapes.extraLarge,
                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                    )
                    .clip(MaterialTheme.shapes.extraLarge)
                    .driftingGradient(MaterialTheme.gradients.hero)
            ) {
                // حلقات زخرفية شفافة تمنح عمقاً بلا تكلفة
                Canvas(Modifier.matchParentSize()) {
                    drawCircle(
                        Color.White.copy(alpha = 0.08f),
                        radius = size.height * 0.8f,
                        center = Offset(size.width * 0.94f, -size.height * 0.15f)
                    )
                    drawCircle(
                        Color.White.copy(alpha = 0.06f),
                        radius = size.height * 0.55f,
                        center = Offset(size.width * 0.04f, size.height * 1.02f)
                    )
                }
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    // شريحة التحية الزمنية باسم المستخدم
                    Surface(
                        color = Color.White.copy(alpha = 0.17f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            "${greetingFor(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))}" +
                                (currentUser?.let { "، ${it.name}" } ?: ""),
                            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        settings.schoolName,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White
                    )
                    Text(
                        "الإذاعة المدرسية — نظام التحكم المحلي",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    Spacer(Modifier.height(14.dp))
                    // حبة الساعة الحية بأرقام جدولية
                    Surface(
                        color = Color.White.copy(alpha = 0.17f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                AppIcons.ScheduleClock, null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(
                                nowText,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontSize = 15.sp, fontFeatureSettings = "tnum"
                                ),
                                color = Color.White
                            )
                        }
                    }
                }
                // v0.9.0: زر الإعدادات في ركن بطاقة البطل — الإعدادات خرجت من شريط التنقل
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                ) {
                    Icon(
                        Icons.Filled.Settings, "الإعدادات",
                        tint = Color.White
                    )
                }
            }
        }

        // ===== شريط إحصاءات اليوم =====
        if (todayEvents.isNotEmpty()) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatChip(
                        "فقرات اليوم", "${todayEvents.size}", Modifier.weight(1f),
                        icon = AppIcons.Event
                    )
                    StatChip(
                        "أُنجزت", "$completedToday", Modifier.weight(1f),
                        valueColor = MaterialTheme.status.success,
                        icon = AppIcons.CheckCircle
                    )
                    StatChip(
                        "متبقية", "$remainingToday", Modifier.weight(1f),
                        valueColor = MaterialTheme.colorScheme.primary,
                        icon = AppIcons.ScheduleClock
                    )
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
                            IconButton(onClick = onOpenOnAir) {
                                Icon(AppIcons.Display, "شاشة العرض على الهواء")
                            }
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
                phase = micPhase,
                level = micLevel,
                route = route,
                error = micError,
                recActive = recActive,
                recElapsed = recElapsed,
                recMode = recMode,
                micMode = micMode,
                waveActive = waveActive,
                waveLevel = waveLevel,
                onRouteChange = { locator.setBroadcastRoute(it) },
                onMicModeChange = { mode ->
                    scope.launch {
                        locator.settings.setMicMode(mode.name)
                    }
                },
                onTalkStart = {
                    if (mic.hasPermission()) {
                        mic.start(route) { err -> err?.let { m -> scope.launch { snackbar.showSnackbar(m) } } }
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

        // ===== v0.9.0: إجراءات سريعة — الاستوديو والسكربتات =====
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickActionCard(
                    icon = AppIcons.Tune,
                    title = "الاستوديو",
                    subtitle = "ميكسر + معادل صوت",
                    modifier = Modifier.weight(1f),
                    onClick = onOpenStudio
                )
                QuickActionCard(
                    icon = AppIcons.Article,
                    title = "السكربتات",
                    subtitle = "نصوص المذيع",
                    modifier = Modifier.weight(1f),
                    onClick = onOpenScripts
                )
            }
        }

        // ===== v0.11.0: حائط الجينغل — بوابة سريعة من الرئيسية =====
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickActionCard(
                    icon = AppIcons.MusicNote,
                    title = "حائط الجينغل",
                    subtitle = "نشيد • فواصل • مؤثرات",
                    modifier = Modifier.weight(1f),
                    onClick = onOpenCartwall
                )
                QuickActionCard(
                    icon = AppIcons.Event,
                    title = "الجدولة",
                    subtitle = "فقرات آلية",
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToSchedule
                )
            }
        }

        // ===== جدول اليوم =====
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "فقرات اليوم",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                androidx.compose.material3.TextButton(onClick = onOpenOnAir) {
                    Icon(AppIcons.Display, null)
                    Text(" شاشة العرض")
                }
            }
        }
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

/** بطاقة إحصاء (v0.7.0): أيقونة في قرص ملون + قيمة كبيرة + تسمية */
@Composable
private fun StatChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    icon: ImageVector? = null
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 1.dp
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (icon != null) {
                Surface(shape = CircleShape, color = valueColor.copy(alpha = 0.13f)) {
                    Icon(
                        icon, null,
                        tint = valueColor,
                        modifier = Modifier.padding(7.dp).size(17.dp)
                    )
                }
                Spacer(Modifier.height(7.dp))
            }
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
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        shadowElevation = 2.dp
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // قرص الوقت — تدرج زمردي (v0.7.0)
            Surface(shape = CircleShape, shadowElevation = 4.dp, color = Color.Transparent) {
                Box(
                    Modifier
                        .size(52.dp)
                        .background(MaterialTheme.gradients.hero.diagonalBrush(), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        ScheduleLogic.formatMinutes(next.startMinutes),
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (next.imagePath != null) {
                Spacer(Modifier.padding(horizontal = 5.dp))
                com.saidjte.schoolradio.core.ui.SegmentImage(
                    next.imagePath,
                    Modifier.size(44.dp),
                    contentDescription = "صورة الفقرة"
                )
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("الفقرة التالية", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(next.title, style = MaterialTheme.typography.titleSmall)
                if (!next.noteText.isNullOrBlank()) {
                    Text(
                        next.noteText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
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
            // شارة الوقت — تدرج أزرق حيّ أثناء التشغيل (v0.7.0)
            if (isRunning) {
                Surface(shape = MaterialTheme.shapes.small, shadowElevation = 3.dp, color = Color.Transparent) {
                    Box(
                        Modifier.background(
                            Brush.linearGradient(
                                listOf(st.running, st.running.copy(alpha = 0.7f))
                            ),
                            MaterialTheme.shapes.small
                        )
                    ) {
                        Text(
                            ScheduleLogic.formatMinutes(event.startMinutes),
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
                        ScheduleLogic.formatMinutes(event.startMinutes),
                        Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFeatureSettings = "tnum", fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(event.title, style = MaterialTheme.typography.bodyLarge)
                if (!event.noteText.isNullOrBlank()) {
                    Text(
                        event.noteText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                Text(
                    next?.let {
                        "الفقرة التالية: ${it.title} في ${ScheduleLogic.formatMinutes(it.startMinutes)}"
                    } ?: "آخر فقرة في جدول اليوم",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (event.imagePath != null) {
                com.saidjte.schoolradio.core.ui.SegmentImage(
                    event.imagePath,
                    Modifier.size(44.dp),
                    contentDescription = "صورة الفقرة"
                )
                Spacer(Modifier.padding(start = 6.dp))
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
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
/**
 * بطاقة البث المباشر (v0.10.0 — دعم نمطي التشغيل):
 *  - HOLD «استمرار للبث»: الضغط بالاستمرار يفتح الهواء وتحريره يغلقه (نمط PTT الاستوديو)
 *  - TOGGLE «ضغطة للبدء»: ضغطة تفتح وضغطة تالية تغلق — الأنسب أثناء قراءة السكربت
 * النمط يُختار من الشريحة العلوية للبطاقة ويُحفظ في الإعدادات، والزر يتكيّف معه
 * بالتلميح والسلوك معاً — سلاسة البدء/الإيقاف نفسها (تلاشي صوتي + آلة حالات).
 */
private fun LiveBroadcastCard(
    isLive: Boolean,
    phase: MicPhase,
    level: Float,
    route: AudioRoutingManager.Route,
    error: String?,
    recActive: Boolean,
    recElapsed: Long,
    recMode: com.saidjte.schoolradio.data.local.entity.RecordingSource?,
    micMode: com.saidjte.schoolradio.audio.MicMode,
    waveActive: Boolean,
    waveLevel: Float,
    onRouteChange: (AudioRoutingManager.Route) -> Unit,
    onMicModeChange: (com.saidjte.schoolradio.audio.MicMode) -> Unit,
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

            // ===== v0.10.0: منتقي نمط البث — استمرار (PTT) أو ضغطة (Toggle) =====
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 6.dp)
            ) {
                com.saidjte.schoolradio.audio.MicMode.entries.forEach { mode ->
                    FilterChip(
                        selected = micMode == mode,
                        onClick = { onMicModeChange(mode) },
                        label = { Text(mode.labelAr, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }
            Text(
                micMode.hintAr + " (تُخفَّض الموسيقى تلقائياً)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Box(Modifier.size(160.dp).padding(16.dp), contentAlignment = Alignment.Center) {
                val baseRing = MaterialTheme.colorScheme.primary
                val startingRing = MaterialTheme.colorScheme.tertiary
                Canvas(Modifier.fillMaxSize()) {
                    val transitioning = phase == MicPhase.STARTING || phase == MicPhase.STOPPING
                    val ringColor = when {
                        isLive -> st.live
                        phase == MicPhase.STARTING -> startingRing
                        else -> baseRing
                    }
                    val alpha = when {
                        isLive -> 0.25f + level * 0.75f
                        transitioning -> 0.45f
                        else -> pulseAlpha
                    }
                    drawCircle(
                        color = ringColor.copy(alpha = alpha),
                        radius = size.minDimension / 2f,
                        style = Stroke(width = 14f),
                        center = Offset(size.width / 2f, size.height / 2f)
                    )
                }
                // v0.8.0: إيماءة مستقرة — pointerInput مفهّح بـ Unit مع قراءة الحالة
                // الأحدث عبر rememberUpdatedState، فلا تُقطع الإيماءة عند تغير الحالة
                // (النمط القديم كان يعيد تشغيل pointerInput عند انقلاب isLive).
                val talkStart by rememberUpdatedState(onTalkStart)
                val talkEnd by rememberUpdatedState(onTalkEnd)
                val phaseRef by rememberUpdatedState(phase)
                val modeRef by rememberUpdatedState(micMode)
                Surface(
                    modifier = Modifier
                        .size(96.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    when (modeRef) {
                                        // نمط الاستوديو: بثّ ما دام الإصبع مضغوطاً
                                        com.saidjte.schoolradio.audio.MicMode.HOLD -> {
                                            if (phaseRef == MicPhase.IDLE) talkStart()
                                            try { awaitRelease() } finally {
                                                // حرر الإصبع أثناء البث أو حتى أثناء البدء؟ أوقف بأمان —
                                                // آلة الحالات تعالج «الإيقاف أثناء البدء» بلا تسريب
                                                val p = phaseRef
                                                if (p == MicPhase.LIVE || p == MicPhase.STARTING) {
                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    talkEnd()
                                                }
                                            }
                                        }
                                        // نمط القراءة الطويلة: ضغطة تبدأ وضغطة تالية تنهي
                                        com.saidjte.schoolradio.audio.MicMode.TOGGLE -> {
                                            try { awaitRelease() } finally {
                                                // نفّذ عند رفع الإصبع حتى لا يُحتسب ضغطاً مزدوجاً
                                                val p = phaseRef
                                                if (p == MicPhase.IDLE) {
                                                    talkStart()
                                                } else if (p == MicPhase.LIVE) {
                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    talkEnd()
                                                }
                                                // STARTING/STOPPING: تجاهل — آلة الحالات تُكمل ثم نقرر لاحقاً
                                            }
                                        }
                                    }
                                }
                            )
                        },
                    shape = CircleShape,
                    color = when {
                        isLive -> st.live
                        phase == MicPhase.STARTING -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    },
                    shadowElevation = 6.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (phase == MicPhase.STARTING || phase == MicPhase.STOPPING) {
                                // مؤشر انتقال صغير — استجابة محسوسة خلال ~100-300ms فتح الأجهزة
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.5.dp,
                                    color = Color.White
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    if (phase == MicPhase.STARTING) "جارٍ البدء…" else "جارٍ الإيقاف…",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    textAlign = TextAlign.Center
                                )
                            } else {
                                Icon(
                                    AppIcons.Mic, null,
                                    tint = Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier.size(26.dp)
                                )
                                Text(
                                    if (isLive) "تُبثّ الآن — اضغط للإنهاء"
                                    else if (micMode == com.saidjte.schoolradio.audio.MicMode.HOLD) "اضغط\nواستمر"
                                    else "اضغط للبدء",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleSmall,
                                    textAlign = TextAlign.Center
                                )
                            }
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

            // ===== v0.12.0: الموجة الحية — نبض ما يُبثّ/يُسجَّل الآن =====
            // أثناء التسجيل تعرض مستوى دفعات PCM المرسلة للكاتب (الملف نفسه)،
            // وأثناء البث بلا تسجيل تعرض صوت الميكروفون على الهواء.
            AnimatedVisibility(visible = waveActive) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LiveWaveform(
                        level = waveLevel,
                        active = waveActive,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                (if (recActive) st.live else MaterialTheme.colorScheme.primary)
                                    .copy(alpha = 0.06f)
                            ),
                        color = if (recActive) st.live else MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (recActive) "موجة التسجيل الحية — ما يُكتب في الملف الآن"
                        else "موجة البث الحية — صوت الميكروفون على الهواء",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // اختيار مسار الإخراج (يُقفل أثناء البث) — v0.6.0:
            // FlowRow ملتفّ بدل Row يفيض، رقائق اختيار بأيقونات وحالة مختارة واضحة،
            // فلم تعد رقاقة «تلقائي» تُقص خارج الشاشة فارغةً مشوّهة.
            val routeIcons = mapOf(
                AudioRoutingManager.Route.SPEAKER to AppIcons.VolumeUp,
                AudioRoutingManager.Route.WIRED to AppIcons.Headphones,
                AudioRoutingManager.Route.BLUETOOTH to AppIcons.Bluetooth,
                AudioRoutingManager.Route.MEDIA_DEFAULT to AppIcons.Autorenew
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                listOf(
                    AudioRoutingManager.Route.SPEAKER,
                    AudioRoutingManager.Route.WIRED,
                    AudioRoutingManager.Route.BLUETOOTH,
                    AudioRoutingManager.Route.MEDIA_DEFAULT
                ).forEach { r ->
                    FilterChip(
                        selected = route == r,
                        onClick = { if (phase == MicPhase.IDLE) onRouteChange(r) },
                        enabled = phase == MicPhase.IDLE,
                        label = { Text(r.labelAr) },
                        leadingIcon = {
                            Icon(
                                routeIcons[r]!!, null,
                                modifier = Modifier.size(17.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
            error?.let {
                Text(it, color = st.failed, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/**
 * v0.9.0: بطاقة إجراء سريع مضغوطة — بوابة بطاقة إلى الاستوديو/السكربتات
 * من الصفحة الرئيسية مباشرة.
 */
@Composable
private fun QuickActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(MaterialTheme.gradients.heroSoft.diagonalBrush()),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Icon(
                AppIcons.SkipNext, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

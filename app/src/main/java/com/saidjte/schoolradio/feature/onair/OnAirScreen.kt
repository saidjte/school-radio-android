package com.saidjte.schoolradio.feature.onair

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saidjte.schoolradio.core.ui.AppIcons
import com.saidjte.schoolradio.core.ui.BigVuMeter
import com.saidjte.schoolradio.core.ui.LiveWaveform
import com.saidjte.schoolradio.core.ui.LocalPerformanceMode
import com.saidjte.schoolradio.core.ui.MicQuickToggle
import com.saidjte.schoolradio.core.ui.RoundedProgressBar
import com.saidjte.schoolradio.core.ui.SegmentImage
import com.saidjte.schoolradio.core.util.AppFormat
import com.saidjte.schoolradio.core.util.AirCountdown
import com.saidjte.schoolradio.core.util.BreakCountdown
import com.saidjte.schoolradio.core.util.OnAirDisplayLogic
import com.saidjte.schoolradio.core.util.ScheduleLogic
import com.saidjte.schoolradio.di.ServiceLocator
import kotlinx.coroutines.delay

/**
 * شاشة العرض «على الهواء» (v0.7.0 — إعادة تصميم مسرحية).
 *
 * مكانها في الاستوديو: يُربط الجهاز بشاشة العرض/البروجكتور وتُعرض هذه الشاشة؛
 * تعرض صورة الفقرة الجارية ونصها مع التقدم، وتتكفل بالعناصر التالية تلقائياً:
 *  - إبقاء الشاشة مضاءة طوال العرض (keepScreenOn).
 *  - نبضة زمنية كل ثانية (ساعة كبيرة + تقدم).
 *  - بلا تشغيل حالياً: ساعة فخمة داخل هالات نابضة + التاريخ — مناسبة للعرض المستمر.
 *  - خلفية متدرجة زمردية داكنة بدل الأسود الصافي + حلقات زخرفية.
 */

/** خلفية العرض: تدرج عمودي زمردي داكن مع حلقات زخرفية هادئة */
private val AirBg = listOf(Color(0xFF071412), Color(0xFF0C2320), Color(0xFF092024))

@Composable
fun OnAirScreen(onExit: () -> Unit) {
    val locator = ServiceLocator
    val playback by locator.playback.state.collectAsStateWithLifecycle()
    val onAir by locator.onAir.collectAsStateWithLifecycle()
    val view = LocalView.current

    // v0.12.0: الموجات الحية — نبض الميكروفون أثناء الإعلان ونبض الموسيقى أثناء الفقرات
    // v0.31.0: المستويات لم تعد تُجمَع في رأس شاشة العرض — مزوّدات تُقرأ وقت العينة
    // داخل الموجات وأعمدة VU نفسها، فإعادة تركيب الشاشة عند كل انبعاث صارت صفراً
    val micLive by locator.micLive.collectAsStateWithLifecycle()
    // v0.13.0: السكربتات — لحل السكربت المرتبط بالفقرة الجارية على شاشة العرض
    val scripts by locator.scripts.scripts.collectAsStateWithLifecycle(initialValue = emptyList())
    val onAirScript = onAir?.scriptId?.let { sid -> scripts.firstOrNull { it.id == sid } }
    val airTeal = Color(0xFF5CDAC4)

    // ===== v0.24.0: مؤقت ترتيل المذيع — لحظة فتح الميكروفون تُحفظ لبدء العد =====
    // إن فُتحت الشاشة والمايك حي أصلاً يبدأ العد من اللحظة الحالية (سقف معقول).
    var micStartedAt by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(micLive) {
        micStartedAt = if (micLive) System.currentTimeMillis() else null
    }

    // إبقاء الشاشة مضاءة طوال بقاء شاشة العرض مفتوحة
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // نبضة زمنية: ساعة + تحديث التقدم
    val nowTick by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }
    val clockText = remember(nowTick) { AppFormat.clock(nowTick) }
    val dateText = remember(nowTick) { AppFormat.dateDay(nowTick) }

    // ===== v0.14.0: المؤقت التنازلي — الفقرة القادمة عند الخمول =====
    // نفس منطق الرئيسية: فقرات اليوم (الجداول المفعّلة) والقادمة زمنياً
    val todayEvents by remember {
        locator.schedules.todayEventsFlow()
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    // ===== v0.24.0: الفقرات المجدولة القادمة — أولها بعدّ حي والبقية بمواعيدها =====
    val upcomingEvents = remember(todayEvents, nowTick) {
        OnAirDisplayLogic.upcomingEvents(
            todayEvents.map { OnAirDisplayLogic.UpcomingEvent(it.title, it.startMinutes) },
            ScheduleLogic.minutesOfDay(nowTick)
        )
    }

    // ===== v0.23.0: الفاصل بين الفقرات — لوحة العد التنازلي الحية على شاشة العرض =====
    // مرحلة الفاصل: انتظار (مهلة قبل الجينل) / تشغيل (الجينل يعمل) / لا شيء
    val breakPhase = BreakCountdown.phase(
        onAirIsBreak = onAir?.isBreak == true,
        currentIsBreak = playback.currentIsBreak,
        breakStartsAtMs = onAir?.breakStartsAtMs,
        nowMs = nowTick
    )
    // نبضة سريعة (250ms) أثناء مهلة الانتقال فقط — عدّ انسيابي على الشاشة الكبيرة
    val breakTick by produceState(System.currentTimeMillis(),
        breakPhase == BreakCountdown.Phase.WAITING
    ) {
        while (true) {
            value = System.currentTimeMillis()
            delay(250)
        }
    }

    // ===== v0.24.0: عناوين الفقرات القادمة من قائمة التشغيل الجارية =====
    val upcomingTitles = remember(playback.queueTitles, playback.queueIndex) {
        OnAirDisplayLogic.upcomingQueueTitles(playback.queueTitles, playback.queueIndex)
    }

    val onDark = Color.White

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(AirBg))
    ) {
        // حلقات زخرفية في الزوايا — عمق مسرحي بلا تكلفة
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                Color.White.copy(alpha = 0.03f),
                radius = size.height * 0.45f,
                center = Offset(size.width * 0.95f, size.height * 0.08f)
            )
            drawCircle(
                Color(0xFF5CDAC4).copy(alpha = 0.05f),
                radius = size.height * 0.35f,
                center = Offset(size.width * 0.05f, size.height * 0.95f)
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ===== الترويسة: اسم الفقرة + شارة البث + زر الخروج =====
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        onAir?.eventTitle ?: playback.currentTitle ?: "الإذاعة المدرسية",
                        style = MaterialTheme.typography.titleLarge,
                        color = onDark,
                        maxLines = 2
                    )
                    Text(
                        if (playback.hasQueue)
                            "الفقرة ${playback.queueIndex + 1} من ${playback.queueSize}"
                        else "في انتظار الفقرة القادمة",
                        style = MaterialTheme.typography.labelMedium,
                        color = onDark.copy(alpha = 0.65f)
                    )
                }
                // شارة «على الهواء» بنقطة نابضة عند وجود تشغيل
                if (playback.hasQueue) {
                    val pulse = rememberInfiniteTransition(label = "air-pulse")
                    val dotAlpha by pulse.animateFloat(
                        initialValue = 1f, targetValue = 0.25f,
                        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
                        label = "air-dot"
                    )
                    Surface(
                        color = Color(0xFFD32F2F).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(9.dp)
                                    .alpha(dotAlpha)
                                    .background(Color(0xFFFF5A5F), CircleShape)
                            )
                            Spacer(Modifier.size(7.dp))
                            Text(
                                "على الهواء",
                                color = Color(0xFFFF8A85),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
                IconButton(onClick = onExit) {
                    Icon(Icons.Rounded.Close, "إغلاق شاشة العرض", tint = onDark)
                }
            }

            Spacer(Modifier.height(18.dp))

            // ===== v0.12.0: شريط «الميكروفون مباشر» بموجة حية — يظهر أثناء الإعلان الحي مهما كانت الحالة =====
            if (micLive) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color(0xFFD32F2F).copy(alpha = 0.22f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            "● الميكروفون مباشر",
                            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            color = Color(0xFFFF8A85),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    LiveWaveform(
                        level = 0f,
                        levelProvider = { locator.micLevel.value.coerceIn(0f, 1f) },
                        active = true,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        color = Color(0xFFFF8A85)
                    )
                }
                Spacer(Modifier.height(14.dp))
            }

            // ===== v0.24.0: مؤقت ترتيل المذيع — عدّ زمن الحديث المباشر بلون دلالي =====
            if (micLive) {
                val elapsedSec = OnAirDisplayLogic.pacingElapsedSec(micStartedAt, nowTick)
                val paceUrgency = OnAirDisplayLogic.pacingUrgency(elapsedSec)
                val paceColor = when (paceUrgency) {
                    AirCountdown.Urgency.NORMAL -> airTeal
                    AirCountdown.Urgency.SOON -> Color(0xFFFFB74D)
                    AirCountdown.Urgency.FINAL -> Color(0xFFFF5A5F)
                }
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = paceColor.copy(alpha = 0.12f)
                ) {
                    Row(
                        Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(AppIcons.Mic, null, tint = paceColor, modifier = Modifier.size(30.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "مؤقت الترتيل — زمن حديثك المباشر",
                                style = MaterialTheme.typography.labelMedium,
                                color = onDark.copy(alpha = 0.65f)
                            )
                            Text(
                                when (paceUrgency) {
                                    AirCountdown.Urgency.NORMAL -> "توقيتك مريح — واصل"
                                    AirCountdown.Urgency.SOON -> "اقتربت من زمن الترتيل — اختم قريباً"
                                    AirCountdown.Urgency.FINAL -> "تجاوزت زمن الترتيل — اختم الآن"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = paceColor
                            )
                        }
                        Text(
                            AirCountdown.mmss(elapsedSec),
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontSize = 40.sp,
                                fontWeight = FontWeight.Bold,
                                fontFeatureSettings = "tnum"
                            ),
                            color = paceColor
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            if (playback.hasQueue) {
                // ===== v0.23.0: لوحة مهلة الانتقال — «انطلاق الفاصل بعد …» بعدّ حي =====
                if (breakPhase == BreakCountdown.Phase.WAITING) {
                    val waitSec = BreakCountdown.waitSecondsLeft(
                        onAir?.breakStartsAtMs ?: breakTick, breakTick
                    )
                    Surface(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0xFFFFB74D).copy(alpha = 0.10f)
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 34.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color(0xFFFFB74D).copy(alpha = 0.16f)
                            ) {
                                Text(
                                    "فاصل بين الفقرات",
                                    Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                    color = Color(0xFFFFD180),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                            Spacer(Modifier.height(18.dp))
                            Text(
                                "انطلاق الفاصل بعد",
                                style = MaterialTheme.typography.titleLarge,
                                color = onDark.copy(alpha = 0.85f)
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                AirCountdown.mmss(waitSec),
                                style = MaterialTheme.typography.displayMedium.copy(
                                    fontSize = 84.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFeatureSettings = "tnum"
                                ),
                                color = Color(0xFFFFB74D)
                            )
                            if (!onAir?.breakTitle.isNullOrBlank()) {
                                Spacer(Modifier.height(14.dp))
                                Text(
                                    "الجينل: ${onAir?.breakTitle}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = airTeal
                                )
                            }
                        }
                    }
                } else {
                // ===== صورة الفقرة (إن وُجدت) — منطقة العرض الأكبر بإطار أنيق =====
                val img = onAir?.imagePath
                if (img != null) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .padding(6.dp)
                    ) {
                        SegmentImage(
                            img,
                            Modifier
                                .fillMaxWidth()
                                .height(330.dp)
                                .clip(RoundedCornerShape(18.dp)),
                            contentDescription = "صورة الفقرة",
                            contentScale = ContentScale.Fit
                        )
                    }
                } else {
                    Surface(
                        Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = onDark.copy(alpha = 0.07f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                AppIcons.GraphicEq, null,
                                tint = onDark.copy(alpha = 0.4f),
                                modifier = Modifier.size(72.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(22.dp))

                // ===== نص الفقرة بخط كبير للعرض من بعيد =====
                Text(
                    onAir?.noteText?.takeIf { it.isNotBlank() }
                        ?: onAir?.eventTitle
                        ?: playback.currentTitle
                        ?: "",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 30.sp,
                        lineHeight = 42.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = onDark,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(24.dp))

                // ===== v0.13.0: لوحة سكربت المذيع — ربط السكربت بالفقرة المجدولة =====
                // عند انطلاق الفقرة المرتبطة بسكربت تظهر لوحة القراءة جاهزة على شاشة
                // العرض نفسها: نص بخط كبير + زر هواء عائم لفتح/إغلاق الميكروفون أثناء القراءة.
                if (onAirScript != null) {
                    var scriptOpen by remember(onAirScript.id) {
                        androidx.compose.runtime.mutableStateOf(true)
                    }
                    Surface(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = onDark.copy(alpha = 0.07f)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    AppIcons.Article, null,
                                    tint = airTeal,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "سكربت الفقرة: ${onAirScript.title}",
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = onDark
                                )
                                TextButton(onClick = { scriptOpen = !scriptOpen }) {
                                    Text(
                                        if (scriptOpen) "طَوِ" else "اقرأ",
                                        color = airTeal
                                    )
                                }
                            }
                            if (scriptOpen) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    onAirScript.body.ifBlank { "السكربت فارغ — أضف نصه من شاشة السكربتات" },
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 300.dp)
                                        .verticalScroll(rememberScrollState()),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = 24.sp,
                                        lineHeight = 40.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = onDark,
                                    textAlign = TextAlign.Start
                                )
                                Spacer(Modifier.height(10.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    MicQuickToggle(
                                        size = 56.dp,
                                        onResult = { }
                                    )
                                    Spacer(Modifier.width(14.dp))
                                    Text(
                                        "اضغط الزر لفتح أو إغلاق الميكروفون أثناء القراءة",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = onDark.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                }
                } // نهاية فرع «ليس انتظار فاصل» — لوحة الانتقال تُغني عن صورة ونص الفقرة

                // ===== v0.23.0: بطاقة الفاصل الجاري — «ينتهي الفاصل بعد …» =====
                if (breakPhase == BreakCountdown.Phase.PLAYING) {
                    // لون دلالي خاص بالفواصل (قصيرة): أحمر في آخر 5 ثوانٍ، كهرماني قبلها
                    val breakUrgency = BreakCountdown.urgency(playback.remainingMs, playback.isPlaying)
                    val breakColor = when (breakUrgency) {
                        AirCountdown.Urgency.FINAL -> Color(0xFFFF5A5F)
                        else -> Color(0xFFFFB74D)
                    }
                    Surface(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = breakColor.copy(alpha = 0.12f)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                AppIcons.Alarm, null,
                                tint = breakColor,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "ينتهي الفاصل بعد",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = onDark.copy(alpha = 0.6f)
                                )
                                Text(
                                    onAir?.breakTitle ?: playback.currentTitle ?: "فاصل بين الفقرات",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = airTeal,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                AirCountdown.mmss(playback.remainingMs / 1000),
                                style = MaterialTheme.typography.displaySmall.copy(
                                    fontSize = 44.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFeatureSettings = "tnum"
                                ),
                                color = breakColor
                            )
                        }
                    }
                } else {
                // ===== v0.14.0: المؤقت التنازلي للفقرة — واضح من آخر القاعة =====
                // لون دلالي: زمردي عادي / كهرماني في آخر دقيقة / أحمر في آخر 15 ثانية
                val urgency = AirCountdown.urgency(playback.remainingMs, playback.isPlaying)
                val countdownColor = when (urgency) {
                    AirCountdown.Urgency.NORMAL -> Color(0xFF5CDAC4)
                    AirCountdown.Urgency.SOON -> Color(0xFFFFB74D)
                    AirCountdown.Urgency.FINAL -> Color(0xFFFF5A5F)
                }
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = countdownColor.copy(alpha = 0.12f)
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            AppIcons.Alarm, null,
                            tint = countdownColor,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "المقطع الحالي ينتهي بعد",
                                style = MaterialTheme.typography.labelMedium,
                                color = onDark.copy(alpha = 0.6f)
                            )
                            if (playback.queueRemainingMs >= 0 && playback.queueSize > 1) {
                                Text(
                                    "نهاية الفقرة كاملة بعد " +
                                        AirCountdown.mmss(playback.queueRemainingMs / 1000),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = onDark.copy(alpha = 0.45f)
                                )
                            }
                        }
                        Text(
                            AirCountdown.mmss(playback.remainingMs / 1000),
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontSize = 44.sp,
                                fontWeight = FontWeight.Bold,
                                fontFeatureSettings = "tnum"
                            ),
                            color = countdownColor
                        )
                    }
                }
                } // نهاية فرع بطاقة الفقرة العادية

                // ===== v0.24.0: الفقرات القادمة — يعرف الجميع ما بعدها من آخر القاعة =====
                if (upcomingTitles.isNotEmpty() && breakPhase != BreakCountdown.Phase.WAITING) {
                    Spacer(Modifier.height(14.dp))
                    Surface(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = onDark.copy(alpha = 0.07f)
                    ) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(
                                "الفقرات القادمة",
                                style = MaterialTheme.typography.labelMedium,
                                color = onDark.copy(alpha = 0.6f)
                            )
                            Spacer(Modifier.height(8.dp))
                            upcomingTitles.forEachIndexed { i, title ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (i == 0) airTeal else onDark.copy(alpha = 0.14f)
                                    ) {
                                        Text(
                                            "${i + 1}",
                                            Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                            color = if (i == 0) Color(0xFF062A25) else onDark,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        if (i == 0) "التالي: $title" else title,
                                        style = if (i == 0) MaterialTheme.typography.titleMedium
                                        else MaterialTheme.typography.bodyMedium,
                                        color = if (i == 0) onDark else onDark.copy(alpha = 0.75f),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // ===== شريط التقدم بأطراف دائرية + الزمن =====
                RoundedProgressBar(
                    progress = if (playback.durationMs > 0)
                        (playback.positionMs.toFloat() / playback.durationMs).coerceIn(0f, 1f)
                    else 0f,
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF5CDAC4),
                    trackColor = onDark.copy(alpha = 0.18f),
                    strokeWidth = 6
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        ScheduleLogic.formatDuration(playback.positionMs),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFeatureSettings = "tnum"
                        ),
                        color = onDark.copy(alpha = 0.8f)
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        ScheduleLogic.formatDuration(playback.durationMs),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFeatureSettings = "tnum"
                        ),
                        color = onDark.copy(alpha = 0.8f)
                    )
                }

                // ===== v0.24.0: مؤشر VU الكبير — عمودا موسيقى/ميكروفون بمؤشر قمة =====
                // + موجة أوسع (132dp بدل 46dp) تُقرأ من آخر القاعة بوضوح
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(132.dp)
                ) {
                    BigVuMeter(
                        mediaLevel = 0f,
                        micLevel = 0f,
                        mediaActive = playback.isPlaying,
                        micActive = micLive,
                        mediaLevelProvider = { locator.mediaLevel.value.coerceIn(0f, 1f) },
                        micLevelProvider = { locator.micLevel.value.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .width(118.dp)
                            .fillMaxHeight(),
                        onDark = onDark
                    )
                    Spacer(Modifier.width(14.dp))
                    LiveWaveform(
                        level = 0f,
                        levelProvider = { locator.mediaLevel.value.coerceIn(0f, 1f) },
                        active = playback.isPlaying,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        color = airTeal,
                        trackColor = onDark.copy(alpha = 0.14f)
                    )
                }

                // ===== تحكم سريع: أزرار دائرية زجاجية + تشغيل بارز =====
                Row(
                    Modifier.padding(top = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = onDark.copy(alpha = 0.1f),
                        shape = CircleShape
                    ) {
                        IconButton(onClick = { locator.playback.previous() }) {
                            Icon(AppIcons.SkipPrevious, "السابق", tint = onDark)
                        }
                    }
                    Surface(
                        color = Color(0xFF5CDAC4),
                        shape = CircleShape,
                        shadowElevation = 8.dp
                    ) {
                        IconButton(onClick = { locator.playback.playPause() }) {
                            Icon(
                                if (playback.isPlaying) AppIcons.Pause else Icons.Rounded.PlayArrow,
                                "تشغيل/إيقاف مؤقت",
                                tint = Color(0xFF062A25),
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }
                    Surface(
                        color = onDark.copy(alpha = 0.1f),
                        shape = CircleShape
                    ) {
                        IconButton(onClick = { locator.playback.next() }) {
                            Icon(AppIcons.SkipNext, "التالي", tint = onDark)
                        }
                    }
                }
            } else {
                // ===== بلا تشغيل: ساعة فخمة داخل هالات نابضة + التاريخ =====
                Spacer(Modifier.weight(1f))
                Box(contentAlignment = Alignment.Center) {
                    // v0.31.0: النبض حركة 60fps دائمة على شاشة تُترك مفتوحة ساعات —
                    // وضع الأداء يجعل الهالات ثابتة جميلة بلا أي كلفة حركة
                    if (LocalPerformanceMode.current) {
                        repeat(3) { ring ->
                            Box(
                                Modifier
                                    .size((240 - ring * 46).dp)
                                    .alpha(0.14f + ring * 0.04f)
                                    .background(Color(0xFF5CDAC4).copy(alpha = 0.12f), CircleShape)
                            )
                        }
                    } else {
                        // ثلاث هالات بنبض متفاوت — عمق مسرحي
                        val halo = rememberInfiniteTransition(label = "halo")
                        val haloAlpha by halo.animateFloat(
                            initialValue = 0.22f, targetValue = 0.05f,
                            animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
                            label = "halo-alpha"
                        )
                        repeat(3) { ring ->
                            Box(
                                Modifier
                                    .size((240 - ring * 46).dp)
                                    .alpha(haloAlpha + ring * 0.04f)
                                    .background(Color(0xFF5CDAC4).copy(alpha = 0.12f), CircleShape)
                            )
                        }
                    }
                    Icon(
                        AppIcons.ScheduleClock, null,
                        tint = Color(0xFF5CDAC4).copy(alpha = 0.75f),
                        modifier = Modifier.size(110.dp)
                    )
                }
                Spacer(Modifier.height(28.dp))
                Text(
                    clockText,
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum"
                    ),
                    color = onDark
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    dateText,
                    style = MaterialTheme.typography.titleMedium,
                    color = onDark.copy(alpha = 0.65f)
                )
                Spacer(Modifier.height(20.dp))
                // ===== v0.14.0 + v0.24.0: القادم من جدول اليوم — الفقرة التالية بعدّ حي =====
                // ثم بقية قائمة القادم بمواعيدها (حتى ثلاث فقرات) لترى القاعة كامل البرنامج
                if (upcomingEvents.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFF5CDAC4).copy(alpha = 0.10f)
                    ) {
                        Column(
                            Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val first = upcomingEvents.first()
                            Text(
                                "الفقرة القادمة: ${first.title}",
                                style = MaterialTheme.typography.titleMedium,
                                color = onDark.copy(alpha = 0.85f),
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "تبدأ بعد " +
                                    AirCountdown.mmss(AirCountdown.secondsUntil(first.startMinutes, nowTick)),
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFeatureSettings = "tnum"
                                ),
                                color = Color(0xFF5CDAC4)
                            )
                            if (upcomingEvents.size > 1) {
                                Spacer(Modifier.height(10.dp))
                                upcomingEvents.drop(1).forEach { ev ->
                                    Text(
                                        "${ScheduleLogic.formatMinutes(ev.startMinutes)} — ${ev.title}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = onDark.copy(alpha = 0.6f),
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(Modifier.height(3.dp))
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        "ستبدأ الفقرات المجدولة تلقائياً في مواعيدها",
                        style = MaterialTheme.typography.bodyLarge,
                        color = onDark.copy(alpha = 0.55f),
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

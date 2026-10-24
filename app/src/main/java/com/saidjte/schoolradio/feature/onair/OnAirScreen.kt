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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import com.saidjte.schoolradio.core.ui.LiveWaveform
import com.saidjte.schoolradio.core.ui.RoundedProgressBar
import com.saidjte.schoolradio.core.ui.SegmentImage
import com.saidjte.schoolradio.core.util.AppFormat
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
    val micLive by locator.micLive.collectAsStateWithLifecycle()
    val micLevelRaw by locator.micLevel.collectAsStateWithLifecycle()
    val mediaLevelRaw by locator.mediaLevel.collectAsStateWithLifecycle()
    val onAirMicLevel by animateFloatAsState(
        targetValue = micLevelRaw.coerceIn(0f, 1f),
        animationSpec = tween(80),
        label = "onair-mic-wave"
    )
    val onAirMediaLevel by animateFloatAsState(
        targetValue = mediaLevelRaw.coerceIn(0f, 1f),
        animationSpec = tween(80),
        label = "onair-media-wave"
    )
    val airTeal = Color(0xFF5CDAC4)

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
                    Icon(Icons.Filled.Close, "إغلاق شاشة العرض", tint = onDark)
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
                        level = onAirMicLevel,
                        active = true,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        color = Color(0xFFFF8A85)
                    )
                }
                Spacer(Modifier.height(14.dp))
            }

            if (playback.hasQueue) {
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

                // ===== v0.12.0: نبض المقطع الحي — موجة الموسيقى/الجينغل كما تُسمع الآن =====
                // تجعل الفواصل المجدولة (Breaks) مرئية على شاشة العرض لا مسموعة فقط
                Spacer(Modifier.height(14.dp))
                LiveWaveform(
                    level = onAirMediaLevel,
                    active = playback.isPlaying,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    color = airTeal,
                    trackColor = onDark.copy(alpha = 0.14f)
                )

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
                                if (playback.isPlaying) AppIcons.Pause else Icons.Filled.PlayArrow,
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
                Text(
                    "ستبدأ الفقرات المجدولة تلقائياً في مواعيدها",
                    style = MaterialTheme.typography.bodyLarge,
                    color = onDark.copy(alpha = 0.55f),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

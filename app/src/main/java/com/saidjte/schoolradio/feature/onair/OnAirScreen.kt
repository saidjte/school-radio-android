package com.saidjte.schoolradio.feature.onair

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saidjte.schoolradio.core.ui.AppIcons
import com.saidjte.schoolradio.core.ui.SegmentImage
import com.saidjte.schoolradio.core.util.ScheduleLogic
import com.saidjte.schoolradio.di.ServiceLocator
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * شاشة العرض «على الهواء» (v0.5.0).
 *
 * مكانها في الاستوديو: يُربط الجهاز بشاشة العرض/البروجكتور وتُعرض هذه الشاشة؛
 * تعرض صورة الفقرة الجارية ونصها مع التقدم، وتتكفل بالعناصر التالية تلقائياً:
 *  - إبقاء الشاشة مضاءة طوال العرض (keepScreenOn).
 *  - نبضة زمنية كل ثانية (ساعة كبيرة + تقدم).
 *  - بلا تشغيل حالياً: رسالة هادئة + الساعة — تبقى مناسبة للعرض المستمر.
 */
@Composable
fun OnAirScreen(onExit: () -> Unit) {
    val locator = ServiceLocator
    val playback by locator.playback.state.collectAsStateWithLifecycle()
    val onAir by locator.onAir.collectAsStateWithLifecycle()
    val view = LocalView.current

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
    val clockText = remember(nowTick) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(nowTick))
    }

    val onDark = Color.White

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ===== الترويسة: اسم الفقرة + زر الخروج =====
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
                IconButton(onClick = onExit) {
                    Icon(Icons.Filled.Close, "إغلاق شاشة العرض", tint = onDark)
                }
            }

            Spacer(Modifier.height(16.dp))

            if (playback.hasQueue) {
                // ===== صورة الفقرة (إن وُجدت) — منطقة العرض الأكبر =====
                val img = onAir?.imagePath
                if (img != null) {
                    SegmentImage(
                        img,
                        Modifier
                            .fillMaxWidth()
                            .height(340.dp),
                        contentDescription = "صورة الفقرة",
                        contentScale = ContentScale.Fit,
                        cornerRadius = 18.dp
                    )
                } else {
                    Surface(
                        Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        shape = MaterialTheme.shapes.large,
                        color = onDark.copy(alpha = 0.08f)
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

                Spacer(Modifier.height(20.dp))

                // ===== نص الفقرة بخط كبير للعرض من بعيد =====
                Text(
                    onAir?.noteText?.takeIf { it.isNotBlank() }
                        ?: onAir?.eventTitle
                        ?: playback.currentTitle
                        ?: "",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 28.sp,
                        lineHeight = 38.sp
                    ),
                    color = onDark,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(24.dp))

                // ===== شريط التقدم + الزمن =====
                LinearProgressIndicator(
                    progress = {
                        if (playback.durationMs > 0)
                            (playback.positionMs.toFloat() / playback.durationMs).coerceIn(0f, 1f)
                        else 0f
                    },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = onDark.copy(alpha = 0.2f)
                )
                Spacer(Modifier.height(6.dp))
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

                // ===== تحكم سريع: سابق/تشغيل-إيقاف/تالي =====
                Row(
                    Modifier.padding(top = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { locator.playback.previous() }) {
                        Icon(AppIcons.SkipPrevious, "السابق", tint = onDark)
                    }
                    IconButton(onClick = { locator.playback.playPause() }) {
                        Icon(
                            if (playback.isPlaying) AppIcons.Pause else Icons.Filled.PlayArrow,
                            "تشغيل/إيقاف مؤقت",
                            tint = onDark,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                    IconButton(onClick = { locator.playback.next() }) {
                        Icon(AppIcons.SkipNext, "التالي", tint = onDark)
                    }
                }
            } else {
                // ===== بلا تشغيل: شاشة انتظار هادئة مناسبة للعرض المستمر =====
                Spacer(Modifier.weight(1f))
                Icon(
                    AppIcons.ScheduleClock, null,
                    tint = onDark.copy(alpha = 0.35f),
                    modifier = Modifier.size(96.dp)
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    clockText,
                    style = MaterialTheme.typography.displayMedium,
                    color = onDark.copy(alpha = 0.9f),
                    fontSize = 64.sp
                )
                Text(
                    "ستبدأ الفقرات المجدولة تلقائياً في مواعيدها",
                    style = MaterialTheme.typography.bodyLarge,
                    color = onDark.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

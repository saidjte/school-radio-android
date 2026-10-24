package com.saidjte.schoolradio.feature.scripts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saidjte.schoolradio.core.ui.AppIcons
import com.saidjte.schoolradio.core.ui.RoundedProgressBar
import com.saidjte.schoolradio.core.util.RecordingLogic
import com.saidjte.schoolradio.data.scripts.ScriptItem
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * قارئ السكربت (Teleprompter) (v0.9.0) — أداة المذيع الاحترافية:
 *
 *  - نص بخط ضخم قابل للتكبير (18..64sp) على خلفية داكنة واضحة
 *  - تمرير تلقائي بسرعة قابلة للضبط — المذيع يقرأ والنص يتقدم بهدوء
 *  - تشغيل/إيقاف التمرير بلمسة، وزمن قراءة حي
 *  - وضع الانعكاس الأفقي (Mirror) — لتركيب الهاتف أمام مرآة استوديو حقيقية
 *  - الشاشة تبقى مضاء تلقائياً أثناء القراءة (keepScreenOn)
 *
 * لمسة على أي مكان في الشاشة تُظهر/تُخفي عناصر التحكم.
 */
@Composable
fun TeleprompterScreen(
    script: ScriptItem,
    onExit: () -> Unit
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val view = LocalView.current

    var autoScroll by remember { mutableStateOf(false) }
    var speedDpS by remember { mutableStateOf(55f) }      // سرعة التمرير dp/ثانية
    var fontSp by remember { mutableStateOf(30f) }        // حجم الخط
    var mirror by remember { mutableStateOf(false) }      // انعكاس للمرآة
    var controlsVisible by remember { mutableStateOf(true) }
    var readSeconds by remember { mutableStateOf(0) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // الشاشة تبقى مضاء أثناء القراءة — أساسي للمذيع أمام الجمهور
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // عدّاد زمن القراءة — يعمل أثناء التمرير التلقائي فقط
    LaunchedEffect(autoScroll) {
        while (autoScroll) {
            kotlinx.coroutines.delay(1000)
            readSeconds += 1
        }
    }

    // حلقة التمرير التلقائي: دفعات ناعمة متساوية السرعة (Linear)
    LaunchedEffect(autoScroll, speedDpS) {
        if (!autoScroll) return@LaunchedEffect
        val pxPerTick = with(density) { speedDpS.dp.toPx() / 10f }
        while (autoScroll) {
            val before = scrollState.value
            scrollState.animateScrollBy(
                value = pxPerTick,
                animationSpec = tween(durationMillis = 100, easing = LinearEasing)
            )
            // وصلنا النهاية؟ أوقف التمرير تلقائياً
            if (scrollState.value >= scrollState.maxValue) {
                autoScroll = false
                break
            }
            // وقف المستخدم السحب اليدوي؟ استمر من الموضع الجديد
            if (scrollState.value == before) break
        }
    }

    val scrollRatio = if (scrollState.maxValue > 0)
        scrollState.value.toFloat() / scrollState.maxValue else 0f

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0B100E))
            .pointerInput(Unit) {
                detectTapGestures { controlsVisible = !controlsVisible }
            }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer { scaleX = if (mirror) -1f else 1f }
        ) {
            // النص القابل للتمرير — وزن كامل
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 22.dp, vertical = 18.dp)
            ) {
                Text(
                    script.title,
                    color = Color(0xFF9BE3C6),
                    fontSize = (fontSp * 0.72f).sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = (fontSp * 0.72f * 1.4f).sp
                )
                Spacer(Modifier.size(18.dp))
                Text(
                    script.body,
                    color = Color(0xFFEAEFEA),
                    fontSize = fontSp.sp,
                    lineHeight = (fontSp * 1.85f).sp,
                    textAlign = TextAlign.Start
                )
                Spacer(Modifier.size(140.dp)) // مساحة نهاية مريحة للقراءة
            }
        }

        // ===== الشريط العلوي: رجوع + العنوان + زمن القراءة =====
        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it }
        ) {
            Surface(
                color = Color(0xCC141C19),
                contentColor = Color.White,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.statusBarsPadding().padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع")
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            script.title,
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Text(
                            "زمن القراءة: ${RecordingLogic.formatElapsed(readSeconds * 1000L)}" +
                                " • ${script.wordCount} كلمة",
                            color = Color(0xFF9BE3C6),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }

        // ===== شريط التحكم السفلي =====
        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it }
        ) {
            Surface(
                color = Color(0xCC141C19),
                contentColor = Color.White,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    // شريط تقدم القراءة
                    RoundedProgressBar(
                        progress = scrollRatio,
                        color = Color(0xFF5CDAC4),
                        trackColor = Color(0xFF22302B),
                        strokeWidth = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // تشغيل/إيقاف التمرير التلقائي
                        IconButton(
                            onClick = {
                                if (scrollState.value >= scrollState.maxValue) {
                                    scope.launch { scrollState.scrollTo(0) }
                                }
                                autoScroll = !autoScroll
                            }
                        ) {
                            Icon(
                                if (autoScroll) AppIcons.Pause else Icons.Filled.PlayArrow,
                                "تشغيل/إيقاف التمرير",
                                tint = Color(0xFF5CDAC4)
                            )
                        }
                        Text(
                            "سرعة التمرير: ${speedDpS.roundToInt()}",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.width(110.dp)
                        )
                        Slider(
                            value = speedDpS,
                            onValueChange = { speedDpS = it },
                            valueRange = 15f..150f,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // حجم الخط − / +
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { fontSp = (fontSp - 4f).coerceAtLeast(18f) }) {
                                Text("أ-", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            IconButton(onClick = { fontSp = (fontSp + 4f).coerceAtMost(64f) }) {
                                Text("أ+", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                        // وضع انعكاس المرآة
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "وضع المرآة",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium
                            )
                            androidx.compose.material3.Switch(
                                checked = mirror,
                                onCheckedChange = { mirror = it }
                            )
                        }
                    }
                }
            }
        }
    }
}

package com.saidjte.schoolradio.feature.scripts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.saidjte.schoolradio.core.ui.MicQuickToggle
import com.saidjte.schoolradio.core.ui.RoundedProgressBar
import com.saidjte.schoolradio.core.util.RecordingLogic
import com.saidjte.schoolradio.data.scripts.ScriptItem
import com.saidjte.schoolradio.di.ServiceLocator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * قارئ السكربت (Teleprompter) (v0.9.0، محدّث v0.10.0، ترقية احترافية v0.11.0):
 *
 *  - نص بخط ضخم قابل للتكبير (18..64sp) على خلفية داكنة واضحة
 *  - تمرير تلقائي بسرعة قابلة للضبط — وتغيير السرعة أثناء التمرير يعمل فوراً
 *  - ★ v0.11.0: عدّ تنازلي 3-2-1 قبل انطلاق القراءة من البداية — المذيع يتحضر
 *  - ★ v0.11.0: زمن متبقٍ تقديري محسوب من سرعة التمرير الحية + زمن القراءة المنقضي
 *  - ★ v0.11.0: خط إرشادي عند موضع القراءة (32٪ من الشاشة) قابل للإخفاء
 *  - ★ v0.11.0: تفضيلات المذيع (السرعة/الخط/المرآة/الإرشاد) تُحفظ وتُسترجع —
 *    أجهزة الاستوديو الحقيقية تحفظ إعدادات كل مذيع
 *  - وضع الانعكاس الأفقي (Mirror) لتركيب الهاتف أمام مرآة استوديو حقيقية
 *  - زر اختصار عائم دائم لفتح/إغلاق الهواء — بث بلمسة دون مغادرة النص
 *  - الشاشة تبقى مضاء تلقائياً أثناء القراءة (keepScreenOn)
 *
 * لمسة على أي مكان في الشاشة تُظهر/تُخفي عناصر التحكم (الزر العائم يبقى دائماً).
 */
@Composable
fun TeleprompterScreen(
    script: ScriptItem,
    onExit: () -> Unit,
    snackbar: androidx.compose.material3.SnackbarHostState? = null
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val view = LocalView.current
    val settings = ServiceLocator.settings

    var autoScroll by remember { mutableStateOf(false) }
    var speedDpS by remember { mutableStateOf(55f) }      // سرعة التمرير dp/ثانية
    var fontSp by remember { mutableStateOf(30f) }        // حجم الخط
    var mirror by remember { mutableStateOf(false) }      // انعكاس للمرآة
    var guideLine by remember { mutableStateOf(true) }    // الخط الإرشادي
    var controlsVisible by remember { mutableStateOf(true) }
    var readSeconds by remember { mutableStateOf(0) }
    /** v0.11.0: عدّ تنازلي قبل انطلاق التمرير (3→2→1 ثم انطلاق) — null = لا عدّ */
    var countdown by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()

    // ===== v0.11.0: استرجاع تفضيلات المذيع المحفوظة (مرة واحدة عند فتح القارئ) =====
    LaunchedEffect(Unit) {
        runCatching {
            val s = settings.settings.first()
            speedDpS = s.prompterSpeed
            fontSp = s.prompterFont
            mirror = s.prompterMirror
            guideLine = s.prompterGuide
        }
    }

    /** رسائل زر الهواء العائم — عبر snackbar الشاشة المضيفة إن توفر */
    val quickMicMessage: (String) -> Unit = { msg ->
        snackbar?.let { s -> scope.launch { s.showSnackbar(msg) } }
    }

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

    // ===== v0.11.0: الزمن المتبقي التقديري — من سرعة التمرير الحالية (يُحدّث لحظياً) =====
    val remainingSeconds: Long = with(density) {
        if (scrollState.maxValue <= 0) 0L else {
            val pxPerSec = (speedDpS.dp.toPx()).coerceAtLeast(1f)
            ((scrollState.maxValue - scrollState.value) / pxPerSec).toLong()
        }
    }

    /** بدء/إيقاف القراءة — مع عدّ تنازلي عند البدء من بداية النص فقط (v0.11.0) */
    fun togglePlay() {
        if (countdown != null) {
            // المستخدم غيّر رأيه أثناء العدّ — ألغِ
            countdown = null
            return
        }
        if (autoScroll) {
            autoScroll = false
            return
        }
        if (scrollState.value >= scrollState.maxValue) {
            scope.launch { scrollState.scrollTo(0) }
        }
        if (scrollState.value < scrollState.maxValue * 0.05f) {
            // انطلاقة جديدة من بداية النص — المذيع يتحضر (3-2-1)
            scope.launch {
                for (n in 3 downTo 1) {
                    countdown = n
                    kotlinx.coroutines.delay(650)
                }
                countdown = null
                autoScroll = true
            }
        } else {
            autoScroll = true
        }
    }

    // v0.10.0: يرتفع زر الهواء فوق شريط الأدوات عند ظهوره وينزل عند إخفائه — حركة ناعمة
    val micFloatBottom by animateDpAsState(
        targetValue = if (controlsVisible) 185.dp else 22.dp,
        label = "mic-float-bottom"
    )

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

        // ===== v0.11.0: الخط الإرشادي عند موضع القراءة (32٪ من الشاشة) —
        // خارج طبقة الانعكاس حتى لا يقلب مع النص =====
        if (guideLine) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val guideY = maxHeight * 0.32f
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .offset(y = guideY)
                        .height(2.dp)
                        .background(Color(0xFF5CDAC4).copy(alpha = 0.55f))
                )
            }
        }

        // ===== v0.11.0: عدّ تنازلي 3-2-1 قبل الانطلاق =====
        countdown?.let { n ->
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x660B100E)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$n",
                    color = Color(0xFF5CDAC4),
                    fontSize = 120.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    "استعد للقراءة…",
                    color = Color(0xFF9BE3C6),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = 96.dp)
                )
            }
        }

        // ===== الشريط العلوي: رجوع + العنوان + زمن القراءة + المتبقي =====
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
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "رجوع")
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
                        // v0.11.0: الزمن المتبقي التقديري — يتحدث مع تغيير السرعة أو التمرير
                        if (scrollState.maxValue > 0 && remainingSeconds > 0) {
                            Text(
                                "المتبقي التقديري: ${RecordingLogic.formatElapsed(remainingSeconds * 1000L)}",
                                color = Color(0xFFB7D9CC),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }

        // ===== v0.10.0: زر الهواء العائم الدائم — الهدية للمذيع =====
        // يبقى ظاهراً حتى مع إخفاء بقية الأدوات: المذيع يفتح ويغلق البث بلمسة
        // واحدة دون أن يغادر النص أو يعود إلى الرئيسية.
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = micFloatBottom)
        ) {
            MicQuickToggle(onResult = quickMicMessage)
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
                        // تشغيل/إيقاف التمرير التلقائي — مع العد التنازلي الذكي
                        IconButton(onClick = ::togglePlay) {
                            Icon(
                                if (autoScroll || countdown != null) AppIcons.Pause
                                else Icons.Rounded.PlayArrow,
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
                            onValueChangeFinished = {
                                scope.launch { settings.setPrompterSpeed(speedDpS) }
                            },
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
                            IconButton(onClick = {
                                fontSp = (fontSp - 4f).coerceAtLeast(18f)
                                scope.launch { settings.setPrompterFont(fontSp) }
                            }) {
                                Text("أ-", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            IconButton(onClick = {
                                fontSp = (fontSp + 4f).coerceAtMost(64f)
                                scope.launch { settings.setPrompterFont(fontSp) }
                            }) {
                                Text("أ+", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                        // v0.11.0: الخط الإرشادي
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "الإرشاد",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium
                            )
                            Switch(
                                checked = guideLine,
                                onCheckedChange = {
                                    guideLine = it
                                    scope.launch { settings.setPrompterGuide(it) }
                                }
                            )
                        }
                        // وضع انعكاس المرآة
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "المرآة",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium
                            )
                            Switch(
                                checked = mirror,
                                onCheckedChange = {
                                    mirror = it
                                    scope.launch { settings.setPrompterMirror(it) }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

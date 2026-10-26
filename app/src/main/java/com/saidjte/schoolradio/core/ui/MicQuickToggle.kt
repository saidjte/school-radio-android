package com.saidjte.schoolradio.core.ui

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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saidjte.schoolradio.audio.MicPhase
import com.saidjte.schoolradio.di.ServiceLocator

/**
 * v0.10.0 — زر اختصار الميكروفون العائم للمذيع (مكوّن مشترك):
 *
 * جوهر الفائدة: أثناء قراءة السكربت في قارئ Teleprompter لا يستطيع المذيع
 * العودة إلى الرئيسية كل مرة ليفتح أو يغلق الهواء — هذا الزر يبقى عائماً
 * فوق النص ويبدّل البث بلمسة واحدة، بحجم كبير وألوان «على الهواء» التي
 * لا تُخطئها العين حتى مع إخفاء بقية الأدوات.
 *
 * الخصائص الاحترافية:
 *  - نفس حالة البث المركزية (ServiceLocator): يعمل مع أي نمط ويعكس الحالة الحقيقية.
 *  - حلقة مستوى صوت حيّة حول الزر أثناء البث (VU من الميكروفون).
 *  - نبض تحذيري عند العمل + اهتزاز لمسي عند كل تبديل.
 *  - يطلب صلاحية الميكروفون ذاتياً عند أول استخدام.
 *  - يعمل في النمطين: ضغطة للبدء/الإنهاء (سلوكه الافتراضي كزر مستقل).
 *
 * @param size قطر الزر (افتراضي 68dp — كبير بما يكفي لإصبع متوتر أثناء الإذاعة)
 * @param onResult رسائل البدء/الخطأ تخرج هنا (snackbar الشاشة المضيفة)
 */
@Composable
fun MicQuickToggle(
    modifier: Modifier = Modifier,
    size: Dp = 68.dp,
    onResult: (String) -> Unit = {}
) {
    val locator = ServiceLocator
    val haptics = LocalHapticFeedback.current
    val isLive by locator.micLive.collectAsStateWithLifecycle()
    val phase by locator.mic.phase.collectAsStateWithLifecycle()
    val levelRaw by locator.micLevel.collectAsStateWithLifecycle()

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            locator.startMicBroadcast { err -> err?.let(onResult) }
        } else {
            onResult("بدون صلاحية الميكروفون لا يمكن البث المباشر")
        }
    }

    val liveColor = MaterialTheme.colorScheme.error
    val idleColor = Color(0xFF5CDAC4)

    // نبض تحذيري عند البث — لا أحد ينسى الميكروفون مفتوحاً
    val pulse = rememberInfiniteTransition(label = "quick-mic-pulse")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 1f, targetValue = 0.45f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "quick-mic-alpha"
    )

    val stateRef by rememberUpdatedState(phase)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // حلقة المستوى الصوتي — أكبر قليلاً من الزر
        Canvas(Modifier.size(size + 14.dp)) {
            val lv = if (isLive) levelRaw.coerceIn(0f, 1f) else 0f
            if (lv > 0.01f) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.25f + lv * 0.75f),
                    radius = size.toPx() / 2f + 6.dp.toPx() * (0.5f + lv),
                    center = Offset(this.size.width / 2f, this.size.height / 2f),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
        Surface(
            modifier = Modifier
                .size(size)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            try { awaitRelease() } finally {
                                when (stateRef) {
                                    MicPhase.IDLE -> {
                                        if (!locator.startMicBroadcast { err -> err?.let(onResult) }) {
                                            micPermission.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                    MicPhase.LIVE -> locator.mic.stop()
                                    else -> Unit // STARTING/STOPPING — آلة الحالات تتكفل
                                }
                            }
                        }
                    )
                },
            shape = CircleShape,
            color = if (isLive || phase == MicPhase.STARTING) liveColor else idleColor,
            shadowElevation = 8.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        AppIcons.Mic, null,
                        tint = Color.White.copy(alpha = 0.95f),
                        modifier = Modifier.size(size * 0.38f)
                    )
                    Text(
                        if (isLive) "مباشر"
                        else if (phase == MicPhase.STARTING || phase == MicPhase.STOPPING) "لحظة…"
                        else "افتح\nالهواء",
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .alpha(if (isLive) pulseAlpha else 1f)
                    )
                }
            }
        }
    }
}

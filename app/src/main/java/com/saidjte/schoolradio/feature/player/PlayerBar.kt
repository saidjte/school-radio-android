package com.saidjte.schoolradio.feature.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saidjte.schoolradio.core.theme.gradients
import com.saidjte.schoolradio.core.ui.AppIcons
import com.saidjte.schoolradio.core.ui.MirroredIcon
import com.saidjte.schoolradio.core.ui.RoundedProgressBar
import com.saidjte.schoolradio.core.ui.diagonalBrush
import com.saidjte.schoolradio.core.util.ScheduleLogic
import com.saidjte.schoolradio.di.ServiceLocator

/**
 * شريط المشغل المصغر (v0.4.0 — تصميم عائم):
 * كارت بزوايا دائرية وارتفاع نغمي فوق شريط التنقل، عنوان مقصوص بسطر واحد،
 * أيقونات تقدّم/تراجع معكوسة للاتجاه العربي، وانتقال متحرك بين أيقونتي التشغيل.
 * النقر يفتح المشغل الموسع (شريط تقدم تفاعلي + قائمة الانتظار).
 * v0.5.0: زر شاشة العرض «على الهواء».
 */
@Composable
fun PlayerBar(
    snackbar: androidx.compose.material3.SnackbarHostState,
    onOpenOnAir: (() -> Unit)? = null
) {
    val playback = ServiceLocator.playback
    val state by playback.state.collectAsStateWithLifecycle()

    state.errorMessage?.let { error ->
        AlertDialog(
            onDismissRequest = { playback.clearError() },
            title = { Text("مشكلة في التشغيل") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { playback.clearError() }) { Text("حسناً") }
            }
        )
    }

    if (!state.hasQueue) return

    var expanded by remember { mutableStateOf(false) }
    if (expanded) {
        FullPlayerDialog(onDismiss = { expanded = false })
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .clickable { expanded = true },
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 1.dp,
        shadowElevation = 8.dp
    ) {
        Column {
            // شريط تقدم بأطراف دائرية (v0.7.0)
            RoundedProgressBar(
                progress = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                strokeWidth = 3
            )
            Row(
                Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(
                        state.currentTitle ?: "—",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${ScheduleLogic.formatDuration(state.positionMs)} / " +
                            ScheduleLogic.formatDuration(state.durationMs) +
                            "  •  ${state.queueIndex + 1}/${state.queueSize}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (onOpenOnAir != null) {
                    IconButton(onClick = onOpenOnAir) {
                        Icon(AppIcons.Display, "شاشة العرض على الهواء")
                    }
                }
                IconButton(onClick = { playback.previous() }) {
                    MirroredIcon(AppIcons.SkipPrevious, "السابق")
                }
                AnimatedContent(
                    targetState = state.isPlaying,
                    transitionSpec = {
                        (fadeIn(tween(150)) + scaleIn(initialScale = 0.6f, animationSpec = tween(150)))
                            .togetherWith(fadeOut(tween(100)) + scaleOut(targetScale = 0.6f, animationSpec = tween(100)))
                    },
                    label = "play-pause"
                ) { playing ->
                    // زر تشغيل في قرص متدرج — لمسة المشغلات العصرية (v0.7.0)
                    Surface(
                        onClick = { playback.playPause() },
                        shape = CircleShape,
                        shadowElevation = 4.dp,
                        color = Color.Transparent
                    ) {
                        Box(
                            Modifier
                                .size(42.dp)
                                .background(MaterialTheme.gradients.hero.diagonalBrush(), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (playing) AppIcons.Pause else Icons.Rounded.PlayArrow,
                                "تشغيل/إيقاف مؤقت",
                                modifier = Modifier.size(22.dp),
                                tint = Color.White
                            )
                        }
                    }
                }
                IconButton(onClick = { playback.next() }) {
                    MirroredIcon(AppIcons.SkipNext, "التالي")
                }
                IconButton(onClick = { playback.stopAll() }) {
                    Icon(
                        Icons.Rounded.Close, "إيقاف كامل",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/** مشغل موسع مع شريط تقدم تفاعلي وقائمة الانتظار وأزرار الخلط/التكرار (v0.8.0) */
@Composable
private fun FullPlayerDialog(onDismiss: () -> Unit) {
    val playback = ServiceLocator.playback
    val state by playback.state.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } },
        title = { Text("قائمة التشغيل الحالية") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    state.currentTitle ?: "—",
                    style = MaterialTheme.typography.titleMedium
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // v0.8.0: خلط الترتيب — أداة الإذاعة الحية لتنويع المقاطع
                    IconButton(onClick = { playback.setShuffle(!state.shuffleEnabled) }) {
                        Icon(
                            AppIcons.Shuffle, "خلط الترتيب",
                            tint = if (state.shuffleEnabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // v0.8.0: تكرار القائمة — موجود محركياً منذ v0.4.0 وصار له زر الآن
                    IconButton(onClick = { playback.setRepeatAll(!state.repeatQueue) }) {
                        Icon(
                            AppIcons.Repeat, "تكرار القائمة",
                            tint = if (state.repeatQueue) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "${ScheduleLogic.formatDuration(state.positionMs)} / " +
                            ScheduleLogic.formatDuration(state.durationMs),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                    )
                }
                androidx.compose.material3.Slider(
                    value = if (state.durationMs > 0)
                        state.positionMs.toFloat() / state.durationMs else 0f,
                    onValueChange = { ratio ->
                        playback.seekTo((ratio * state.durationMs).toLong())
                    }
                )
                if (state.errorMessage != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.error)
                        Text(
                            " ${state.errorMessage}",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                // قائمة الانتظار بعناوينها الحقيقية
                Column {
                    state.queueTitles.forEachIndexed { i, title ->
                        val isCurrent = i == state.queueIndex
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { playback.skipTo(i) }
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                "${i + 1}. ",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                title,
                                style = if (isCurrent) MaterialTheme.typography.titleSmall
                                else MaterialTheme.typography.bodyMedium,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    )
}

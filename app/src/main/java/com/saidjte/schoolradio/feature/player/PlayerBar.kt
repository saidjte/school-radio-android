package com.saidjte.schoolradio.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saidjte.schoolradio.core.ui.AppIcons
import com.saidjte.schoolradio.core.util.ScheduleLogic
import com.saidjte.schoolradio.di.ServiceLocator

/**
 * شريط المشغل المصغر — يظهر فوق شريط التنقل عند وجود قائمة تشغيل.
 * يعرض: العنوان، تشغيل/إيقاف، التالي/السابق، التقدم، وزر إيقاف كامل.
 * الأخطاء (ملف مفقود/صيغة غير مدعومة) تُعرض في حوار واضح.
 */
@Composable
fun PlayerBar(snackbar: androidx.compose.material3.SnackbarHostState) {
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

    Column(
        Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        LinearProgressIndicator(
            progress = {
                if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f
            },
            modifier = Modifier.fillMaxWidth().height(3.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    state.currentTitle ?: "—",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1
                )
                Text(
                    "${ScheduleLogic.formatDuration(state.positionMs)} / " +
                        ScheduleLogic.formatDuration(state.durationMs) +
                        "  •  ${state.queueIndex + 1}/${state.queueSize}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { playback.previous() }) {
                Icon(AppIcons.SkipPrevious, "السابق")
            }
            IconButton(onClick = { playback.playPause() }) {
                Icon(
                    if (state.isPlaying) AppIcons.Pause else Icons.Filled.PlayArrow,
                    "تشغيل/إيقاف مؤقت",
                    modifier = Modifier.size(32.dp)
                )
            }
            IconButton(onClick = { playback.next() }) {
                Icon(AppIcons.SkipNext, "التالي")
            }
            IconButton(onClick = { playback.stopAll() }) {
                Icon(Icons.Filled.Close, "إيقاف كامل", tint = Color(0xFFC62828))
            }
        }
    }
}

/** مشغل موسع مع شريط تقدم تفاعلي وقائمة الانتظار */
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
                androidx.compose.material3.Slider(
                    value = if (state.durationMs > 0)
                        state.positionMs.toFloat() / state.durationMs else 0f,
                    onValueChange = { ratio ->
                        playback.seekTo((ratio * state.durationMs).toLong())
                    }
                )
                Text(
                    "${ScheduleLogic.formatDuration(state.positionMs)} / " +
                        ScheduleLogic.formatDuration(state.durationMs),
                    style = MaterialTheme.typography.bodySmall
                )
                if (state.errorMessage != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, null, tint = Color(0xFFC62828))
                        Text(" ${state.errorMessage}", color = Color(0xFFC62828))
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

package com.saidjte.schoolradio.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.saidjte.schoolradio.audio.SilenceAnalyzer
import com.saidjte.schoolradio.core.ui.AppIcons
import com.saidjte.schoolradio.core.util.ScheduleLogic
import com.saidjte.schoolradio.core.util.TrimLogic
import com.saidjte.schoolradio.data.local.entity.AudioTrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * v0.19.0 — محرر القص اليدوي للمقاطع.
 *
 * المذيع يسمع النشيد ويعرف أين يبدأ وأين ينتهي بلا مقدمات — هذا المحرر يمنحه
 * التحكم الكامل: منزلق بداية ومنزلق نهاية مع شريط مرئي للجزء المشغَّل،
 * معاينة فورية للجزء المختار، واقتراح آلي للفراغ الأول (قياس v0.18.0 نفسه)
 * كنقطة انطلاق سريعة. الحفظ يوسم المقطع بوضع «السلطة اليدوية»: قصه يُطبَّق
 * في كل مسارات التشغيل ولا يمسّه التحليل الآلي بعدها أبداً.
 *
 * لماذا مشغل معاينة محلي؟ المعاينة داخل الحوار يجب ألا تعكّر قائمة الهواء
 * الحالية (المشغل الرئيسي) — ExoPlayer مؤقت يُحرَّر مع إغلاق الحوار.
 */
@Composable
fun TrimEditorDialog(
    track: AudioTrackEntity,
    onDismiss: () -> Unit,
    /** حفظ القص (بداية، نهاية) — التحقق النهائي في المستودع */
    onSave: (startMs: Long, endMs: Long) -> Unit,
    /** إزالة كل القص — يظهر فقط للمقاطع المقصوصة سابقاً */
    onClear: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // المدة: من قاعدة البيانات أو تُستكشف من الملف نفسه عند أول فتح
    var duration by remember { mutableStateOf(track.durationMs) }
    var probing by remember { mutableStateOf(false) }

    // نقطتا القص الحاليتان — تعملان محلياً حتى الحفظ
    var startMs by remember { mutableStateOf(track.trimStartMs.coerceAtLeast(0L)) }
    var endMs by remember {
        mutableStateOf(
            if (track.trimEndMs > 0) track.trimEndMs else track.durationMs
        )
    }
    var hint by remember { mutableStateOf<String?>(null) }
    var previewing by remember { mutableStateOf(false) }

    // مدة مجهولة؟ استكشفها من الملف مرة واحدة عند فتح المحرر
    LaunchedEffect(track.id) {
        if (duration <= 0) {
            probing = true
            val d = withContext(Dispatchers.IO) {
                val retriever = android.media.MediaMetadataRetriever()
                try {
                    if (track.uri.startsWith("content://"))
                        retriever.setDataSource(context, android.net.Uri.parse(track.uri))
                    else retriever.setDataSource(track.uri)
                    retriever.extractMetadata(
                        android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
                    )?.toLongOrNull() ?: 0L
                } catch (_: Exception) {
                    0L
                } finally {
                    runCatching { retriever.release() }
                }
            }
            duration = d
            if (endMs <= 0 && d > 0) endMs = d
            if (d <= 0) hint = "المدة غير معروفة لهذا الملف — شغّله مرة ثم أعد فتح القص اليدوي"
            probing = false
        }
    }

    // مشغل المعاينة المحلي — يُحرَّر مع الحوار
    var player by remember { mutableStateOf<androidx.media3.exoplayer.ExoPlayer?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            player?.release()
            player = null
        }
    }

    fun stopPreview() {
        player?.stop()
        previewing = false
    }

    /** معاينة الجزء المختار فقط — Clipping بنفس آلية التشغيل الفعلي */
    fun togglePreview() {
        val p = player ?: androidx.media3.exoplayer.ExoPlayer.Builder(context).build().also {
            it.addListener(object : androidx.media3.common.Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == androidx.media3.common.Player.STATE_ENDED) previewing = false
                }
            })
            player = it
        }
        if (previewing) {
            stopPreview()
            return
        }
        val clip = androidx.media3.common.MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(startMs)
            .setEndPositionMs(if (endMs > startMs) endMs else androidx.media3.common.C.TIME_UNSET)
            .build()
        p.setMediaItem(
            androidx.media3.common.MediaItem.Builder()
                .setUri(track.uri)
                .setClippingConfiguration(clip)
                .build()
        )
        p.prepare()
        p.play()
        previewing = true
    }

    /** اقتراح آلي: قياس الفراغ الأول (نفس محرك v0.18.0) — اقتراح فقط دون حفظ */
    fun suggestSilence() {
        if (probing) return
        probing = true
        scope.launch {
            val trim = withContext(Dispatchers.IO) {
                runCatching { SilenceAnalyzer.leadingSilenceMs(context, track.uri) }.getOrDefault(0L)
            }
            if (trim > 0) {
                val (s, e) = TrimLogic.normalize(trim, endMs, duration)
                startMs = s; endMs = e
                hint = "وُجد فراغ ${ScheduleLogic.formatDuration(trim)} في البداية — راجع النقطتين واحفظ"
            } else {
                hint = "لا فراغ يُذكر في بداية هذا المقطع — حرّك المنزلقات يدوياً"
            }
            probing = false
        }
    }

    val validationError = if (duration > 0) TrimLogic.validate(startMs, endMs, duration) else "المدة غير معروفة"
    val hasTrim = TrimLogic.hasAnyTrim(track.trimStartMs, track.trimEndMs, track.trimManual)

    AlertDialog(
        onDismissRequest = { stopPreview(); onDismiss() },
        title = {
            Column {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(AppIcons.Tune, null, Modifier.padding(end = 6.dp))
                    Text("قص يدوي", fontWeight = FontWeight.SemiBold)
                }
                Text(
                    track.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // ===== الشريط المرئي: الأصل كاملاً والجزء المشغَّل مظلل =====
                BoxWithConstraints(
                    Modifier.fillMaxWidth().height(26.dp)
                ) {
                    val w = maxWidth
                    Box(
                        Modifier.fillMaxWidth().height(26.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    )
                    if (duration > 0) {
                        val startFrac = (startMs.toFloat() / duration).coerceIn(0f, 1f)
                        val endFrac = (endMs.toFloat() / duration).coerceIn(0f, 1f)
                        Box(
                            Modifier
                                .offset(x = w * startFrac)
                                .width(w * (endFrac - startFrac).coerceAtLeast(0.01f))
                                .height(26.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                        )
                    }
                }

                Text(
                    if (duration > 0) TrimLogic.summary(startMs, endMs)
                    else "بانتظار مدة الملف…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // ===== منزلق البداية =====
                Text("البداية: ${ScheduleLogic.formatDuration(startMs)}")
                Slider(
                    value = startMs.toFloat(),
                    onValueChange = { v ->
                        stopPreview()
                        val nv = v.toLong().coerceAtLeast(0L)
                        // فرض الفجوة الدنيا: البداية لا تتجاوز النهاية
                        startMs = if (endMs - nv >= TrimLogic.MIN_SEGMENT_MS) nv
                        else (endMs - TrimLogic.MIN_SEGMENT_MS).coerceAtLeast(0L)
                    },
                    onValueChangeFinished = { startMs = TrimLogic.snap(startMs) },
                    valueRange = 0f..(if (duration > 0) duration.toFloat() else 1f),
                    enabled = duration > 0 && !probing
                )

                // ===== منزلق النهاية =====
                Text("النهاية: ${ScheduleLogic.formatDuration(endMs)}")
                Slider(
                    value = endMs.toFloat(),
                    onValueChange = { v ->
                        stopPreview()
                        val nv = v.toLong()
                        // فرض الفجوة الدنيا: النهاية لا تنزل تحت البداية
                        endMs = if (nv - startMs >= TrimLogic.MIN_SEGMENT_MS) nv
                        else startMs + TrimLogic.MIN_SEGMENT_MS
                    },
                    onValueChangeFinished = {
                        endMs = TrimLogic.snap(endMs)
                        if (duration > 0) endMs = endMs.coerceAtMost(duration)
                    },
                    valueRange = 0f..(if (duration > 0) duration.toFloat() else 1f),
                    enabled = duration > 0 && !probing
                )

                // ===== أدوات مساعدة =====
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { suggestSilence() },
                        enabled = duration > 0 && !probing
                    ) {
                        Icon(AppIcons.Cut, null, Modifier.padding(end = 4.dp))
                        Text("اقتراح الفراغ الأول")
                    }
                    OutlinedButton(
                        onClick = { togglePreview() },
                        enabled = duration > 0 && validationError == null
                    ) {
                        Icon(
                            if (previewing) AppIcons.Stop else Icons.Filled.PlayArrow,
                            null, Modifier.padding(end = 4.dp)
                        )
                        Text(if (previewing) "إيقاف المعاينة" else "معاينة القص")
                    }
                }

                hint?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                if (hasTrim) {
                    Text(
                        "هذا المقطع مقصوص حالياً" +
                            if (track.trimManual) " يدوياً" else " آلياً (الفراغ الأول)" +
                            " — الحفظ يستبدل القص السابق، و«إزالة القص» يعيده كاملاً",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { stopPreview(); onSave(startMs, endMs) },
                enabled = duration > 0 && validationError == null && !probing
            ) { Text("حفظ القص") }
        },
        dismissButton = {
            Row {
                if (hasTrim) {
                    TextButton(onClick = { stopPreview(); onClear() }) {
                        Text("إزالة القص", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = { stopPreview(); onDismiss() }) { Text("إلغاء") }
            }
        }
    )
}

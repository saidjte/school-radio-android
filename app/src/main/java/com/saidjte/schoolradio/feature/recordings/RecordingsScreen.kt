package com.saidjte.schoolradio.feature.recordings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saidjte.schoolradio.audio.RecordingsPlayer
import com.saidjte.schoolradio.core.theme.status
import com.saidjte.schoolradio.core.ui.AppIcons
import com.saidjte.schoolradio.core.ui.EmptyStateCard
import com.saidjte.schoolradio.core.ui.PlayingEqualizer
import com.saidjte.schoolradio.core.ui.ScreenHeader
import com.saidjte.schoolradio.core.ui.StatusBadge
import com.saidjte.schoolradio.core.util.AppFormat
import com.saidjte.schoolradio.core.util.RecordingCodecLogic
import com.saidjte.schoolradio.core.util.RecordingLogic
import com.saidjte.schoolradio.core.util.TrimLogic
import com.saidjte.schoolradio.data.local.entity.RecordingEntity
import com.saidjte.schoolradio.data.local.entity.UserRole
import com.saidjte.schoolradio.di.ServiceLocator
import com.saidjte.schoolradio.feature.library.TrimEditorDialog
import com.saidjte.schoolradio.feature.library.TrimTarget
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * شاشة أرشيف التسجيلات (v0.4.0، محدّثة v0.8.0):
 *  - بحث بالعنوان + مرشّح المصدر (الكل / بث مباشر / فقرة تحضيرية) + فرز (الأحدث/الأقدم/الأطول)
 *  - ملخص علوي: العدد والحجم الكلي للأرشيف
 *  - استماع فوري عبر الحَكَم الموحّد ( PlaybackCoordinator ) — لا تداخل مع مشغل القائمة أو البث،
 *    والتوقّف المؤقت للقائمة الرئيسية يُستأنف تلقائياً بعد انتهاء المعاينة
 *  - مشغل سطر كامل: شريط تقديم تفاعلي + زر إيقاف + مؤشر تشغيل متحرك
 *  - تسمية وحذف محميان بالأدوار: معلم أو مشرف فقط
 */
enum class RecFilter(val labelAr: String) {
    ALL("الكل"), LIVE("بث مباشر"), MEMO("تحضيرية")
}

enum class RecSort(val labelAr: String) {
    NEWEST("الأحدث"), OLDEST("الأقدم"), LONGEST("الأطول")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecordingsScreen(
    snackbar: androidx.compose.material3.SnackbarHostState,
    onScheduleClick: () -> Unit = {}
) {
    val locator = ServiceLocator
    val scope = rememberCoroutineScope()

    val recordings by locator.recordingsRepo.observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val playback by locator.recordingsPlayer.state.collectAsStateWithLifecycle()
    val canManage by locator.session.canManage
        .collectAsStateWithLifecycle(initialValue = true)

    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(RecFilter.ALL) }
    var sort by rememberSaveable { mutableStateOf(RecSort.NEWEST) }
    var renameTarget by remember { mutableStateOf<RecordingEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<RecordingEntity?>(null) }
    // v0.14.0: تسجيل بانتظار صلاحية التصدير (أندرويد 9- فقط)
    var pendingExport by remember { mutableStateOf<RecordingEntity?>(null) }
    // v0.20.0: تسجيل تحت القص اليدوي (محرر الموجة)
    var trimming by remember { mutableStateOf<RecordingEntity?>(null) }

    val context = androidx.compose.ui.platform.LocalContext.current

    /** v0.14.0: تنفيذ التصدير خارج الخيط الرئيسي مع ردّ فوري بالـ snackbar */
    fun doExport(rec: RecordingEntity) {
        scope.launch(Dispatchers.IO) {
            val result = RecordingShareHelper.export(context, rec)
            val msg = when (result) {
                is RecordingShareHelper.ExportResult.Done ->
                    com.saidjte.schoolradio.core.util.RecordingExportLogic.successMessage(result.fileName)
                RecordingShareHelper.ExportResult.SourceMissing ->
                    "ملف التسجيل غير متاح — ربما حُذف من الذاكرة"
                is RecordingShareHelper.ExportResult.Failed ->
                    "فشل التصدير: ${result.reason ?: "خطأ غير معروف"}"
            }
            snackbar.showSnackbar(msg)
        }
    }

    /** v0.14.0: مشاركة عبر لوحة النظام (بلا صلاحيات — FileProvider) */
    fun doShare(rec: RecordingEntity) {
        RecordingShareHelper.share(context, rec)?.let { msg ->
            scope.launch { snackbar.showSnackbar(msg) }
        }
    }

    // v0.14.0: طلب صلاحية الكتابة للتصدير على أندرويد 9- ثم إنجاز التصدير المعلّق
    val exportPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        pendingExport?.let { rec ->
            pendingExport = null
            if (granted) doExport(rec)
            else scope.launch {
                snackbar.showSnackbar("بدون صلاحية التخزين لا يمكن التصدير على هذا الإصدار — استعمل زر المشاركة")
            }
        }
    }

    /** v0.14.0: تصدير — على أندرويد 9- تُطلب صلاحية الكتابة أولاً */
    fun exportOrRequest(rec: RecordingEntity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            doExport(rec)
        } else {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) doExport(rec)
            else {
                pendingExport = rec
                exportPermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    val visible = recordings
        .filter { rec ->
            when (filter) {
                RecFilter.ALL -> true
                RecFilter.LIVE -> rec.sourceEnum().name == "PTT"
                RecFilter.MEMO -> rec.sourceEnum().name != "PTT"
            }
        }
        .filter { rec ->
            search.isBlank() || rec.title.contains(search.trim(), ignoreCase = true)
        }
        .let { list ->
            when (sort) {
                RecSort.NEWEST -> list.sortedByDescending { it.createdAt }
                RecSort.OLDEST -> list.sortedBy { it.createdAt }
                RecSort.LONGEST -> list.sortedByDescending { it.durationMs }
            }
        }
    val totalBytes = recordings.sumOf { it.sizeBytes }

    /** فتح/إغلاق معاينة عبر الحَكَم الموحّد — الرسائل المنعِقة (بث يعمل/ملف مفقود) تظهر snackbar */
    fun togglePreview(rec: RecordingEntity) {
        locator.previewToggle(rec)?.let { msg ->
            scope.launch { snackbar.showSnackbar(msg) }
        }
    }

    // v0.36.0: أخطاء التشغيل غير المتزامنة (صيغة غير مدعومة/ملف تالف) — snackbar بدل الصمت
    LaunchedEffect(Unit) {
        locator.recordingsPlayer.errors.collect { msg -> snackbar.showSnackbar(msg) }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        ScreenHeader(
            title = "أرشيف التسجيلات",
            subtitle = if (recordings.isEmpty()) "لا تسجيلات بعد"
            else "${recordings.size} تسجيلاً • ${RecordingLogic.formatBytes(totalBytes)}"
        )

        if (recordings.isNotEmpty()) {
            // ===== البحث =====
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                placeholder = { Text("ابحث في التسجيلات…") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (search.isNotEmpty()) {
                        IconButton(onClick = { search = "" }) {
                            Icon(Icons.Filled.Close, "مسح البحث")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )
            // ===== مرشّح المصدر + الفرز في صف قابل للالتفاف — توفير عمودي وترتيب أجمل =====
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                RecFilter.entries.forEach { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { filter = f },
                        label = { Text(f.labelAr) }
                    )
                }
                Text(
                    "الفرز:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp)
                )
                RecSort.entries.forEach { s ->
                    FilterChip(
                        selected = sort == s,
                        onClick = { sort = s },
                        label = { Text(s.labelAr) }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (recordings.isEmpty()) {
            EmptyStateCard(
                icon = AppIcons.LibraryMusic,
                title = "لا توجد تسجيلات بعد",
                body = "اضغط زر التسجيل في الصفحة الرئيسية أثناء البث المباشر، أو سجّل فقرة تحضيرية قبل وقت الإذاعة"
            )
        } else if (visible.isEmpty()) {
            EmptyStateCard(
                icon = AppIcons.LibraryMusic,
                title = "لا نتائج مطابقة",
                body = "جرّب كلمة بحث مختلفة أو غيّر المرشّح"
            )
        } else {
            // v0.9.0: المشغل السفلي المثبّت — شريط قائمة بكامل الارتفاع المتبقي
            val currentRec = remember(
                playback.recordingId, recordings
            ) { recordings.firstOrNull { it.id == playback.recordingId } }
            // v0.10.0: موقع التسجيل الحالي داخل القائمة المرئية — للسابق/التالي والعدّاد
            val visibleIndex = currentRec?.let { visible.indexOfFirst { r -> r.id == it.id } } ?: -1

            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(visible, key = { it.id }) { rec ->
                    RecordingRow(
                        recording = rec,
                        playing = playback,
                        canManage = canManage,
                        onToggle = { togglePreview(rec) },
                        onSeek = { ms -> locator.recordingsPlayer.seekTo(ms) },
                        onStop = {
                            locator.recordingsPlayer.stopAll()
                            // الإيقاف الصريح يعني انتهاء المعاينة — أبلغ الحَكَم
                            locator.coordinator.previewFinished()
                        },
                        onRename = { renameTarget = rec },
                        onDelete = { deleteTarget = rec },
                        // v0.20.0: القص اليدوي فوق الشكل الموجي — متاح للجميع مثل المكتبة
                        onTrim = { trimming = rec },
                        // v0.14.0: مشاركة/تصدير — متاحان للجميع (ليسا تدميراً)
                        onShare = { doShare(rec) },
                        onExport = { exportOrRequest(rec) },
                        // v0.5.0: جدولة سريعة — مسودة فقرة بهذا التسجيل ثم الانتقال للجدولة
                        onSchedule = {
                            ServiceLocator.setPendingScheduleDraft(
                                com.saidjte.schoolradio.data.local.entity.ScheduledEventEntity(
                                    scheduleId = -1L,
                                    startMinutes = 480, // 08:00 — يعدّلها المستخدم من المحرر
                                    weekDaysMask = 0b0111110,
                                    title = rec.title,
                                    recordingId = rec.id
                                )
                            )
                            onScheduleClick()
                        }
                    )
                }
                item { Spacer(Modifier.height(12.dp)) }
            }

            // ===== v0.10.0: مشغل التسجيلات المثبّت — سابق/تالي + ±15ث + سرعة + تقدم كامل =====
            if (currentRec != null) {
                RecordingsMiniPlayer(
                    state = playback,
                    title = currentRec.title,
                    positionText = if (visibleIndex >= 0 && visible.isNotEmpty())
                        "${visibleIndex + 1} من ${visible.size}" else null,
                    canGoPrevious = visibleIndex > 0,
                    canGoNext = visibleIndex in 0 until visible.size - 1,
                    onToggle = { togglePreview(currentRec) },
                    onStop = {
                        locator.recordingsPlayer.stopAll()
                        locator.coordinator.previewFinished()
                    },
                    onSeek = { ms -> locator.recordingsPlayer.seekTo(ms) },
                    onSkip = { delta -> locator.recordingsPlayer.skipBy(delta) },
                    onSpeed = { speed -> locator.recordingsPlayer.setSpeed(speed) },
                    // v0.10.0: التنقل بين التسجيلات من المشغل نفسه — بلا عودة للقائمة
                    onPrevious = {
                        if (visibleIndex > 0) togglePreview(visible[visibleIndex - 1])
                    },
                    onNext = {
                        if (visibleIndex in 0 until visible.size - 1)
                            togglePreview(visible[visibleIndex + 1])
                    }
                )
            }
        }
    }

    // ===== حوار التسمية =====
    renameTarget?.let { target ->
        RenameDialog(
            initial = target.title,
            onDismiss = { renameTarget = null },
            onConfirm = { newTitle ->
                scope.launch {
                    locator.recordingsRepo.rename(target.id, newTitle)
                    snackbar.showSnackbar("عُدّل عنوان التسجيل")
                }
                renameTarget = null
            }
        )
    }

    // ===== v0.20.0: محرر القص اليدوي للتسجيلات — نفس محرر المكتبة + الشكل الموجي =====
    trimming?.let { rec ->
        TrimEditorDialog(
            target = TrimTarget(
                title = rec.title,
                uri = rec.filePath,
                durationMs = rec.durationMs,
                trimStartMs = rec.trimStartMs,
                trimEndMs = rec.trimEndMs,
                trimManual = rec.trimManual
            ),
            onDismiss = { trimming = null },
            onSave = { s, e ->
                scope.launch {
                    val err = locator.recordingsRepo.applyManualTrim(rec, s, e)
                    snackbar.showSnackbar(
                        err ?: TrimLogic.message(s, e, rec.title)
                    )
                }
                trimming = null
            },
            onClear = {
                scope.launch {
                    locator.recordingsRepo.clearManualTrim(rec.id)
                    snackbar.showSnackbar(TrimLogic.CLEAR_MESSAGE)
                }
                trimming = null
            }
        )
    }

    // ===== حوار تأكيد الحذف (الملف من القرص أيضاً) =====
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("حذف التسجيل؟") },
            text = { Text("سيُحذف «${target.title}» وملفه من الجهاز نهائياً.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        locator.recordingsRepo.delete(target)
                        snackbar.showSnackbar("حُذف التسجيل")
                    }
                    deleteTarget = null
                }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("إلغاء") }
            }
        )
    }
}

/**
 * v0.10.0: مشغل التسجيلات المثبّت أسفل الأرشيف — أداة احترافية كاملة:
 * تشغيل/إيقاف، شريط تقدم تفاعلي، قفزات ±15 ثانية، سرعات تشغيل (0.75× حتى 2×)،
 * وتنقّل سابق/تالي بين تسجيلات القائمة المرئية مع عدّاد الموضع (3 من 12).
 * يظهر فقط عند وجود تسجيل مختار ويبقى ثابتاً أثناء تمرير القائمة.
 */
@Composable
private fun RecordingsMiniPlayer(
    state: RecordingsPlayer.State,
    title: String,
    positionText: String? = null,
    canGoPrevious: Boolean = false,
    canGoNext: Boolean = false,
    onToggle: () -> Unit,
    onStop: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkip: (Long) -> Unit,
    onSpeed: (Float) -> Unit,
    onPrevious: () -> Unit = {},
    onNext: () -> Unit = {}
) {
    val isPlaying = state.isPlaying
    Surface(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = 8.dp
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(
                    onClick = onToggle,
                    modifier = Modifier.size(44.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        if (isPlaying) AppIcons.Pause else Icons.Filled.PlayArrow,
                        "تشغيل/إيقاف مؤقت"
                    )
                }
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${RecordingLogic.formatElapsed(state.positionMs)} / " +
                            RecordingLogic.formatElapsed(state.durationMs) +
                            (positionText?.let { "  •  $it" } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onStop) {
                    Icon(
                        AppIcons.Stop, "إيقاف كامل",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Slider(
                value = if (state.durationMs > 0)
                    (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f,
                onValueChange = { ratio -> onSeek((ratio * state.durationMs).toLong()) },
                enabled = state.durationMs > 0,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // v0.10.0: التسجيل السابق في القائمة المرئية
                FilledTonalIconButton(
                    onClick = onPrevious,
                    enabled = canGoPrevious,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(AppIcons.SkipPrevious, "التسجيل السابق", Modifier.size(20.dp))
                }
                // قفزة للخلف 15 ثانية
                FilledTonalIconButton(
                    onClick = { onSkip(-15_000L) },
                    modifier = Modifier.size(38.dp)
                ) {
                    Text("-15ث", fontSize = 11.sp)
                }
                // قفزة للأمام 15 ثانية
                FilledTonalIconButton(
                    onClick = { onSkip(15_000L) },
                    modifier = Modifier.size(38.dp)
                ) {
                    Text("+15ث", fontSize = 11.sp)
                }
                // v0.10.0: التسجيل التالي في القائمة المرئية
                FilledTonalIconButton(
                    onClick = onNext,
                    enabled = canGoNext,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(AppIcons.SkipNext, "التسجيل التالي", Modifier.size(20.dp))
                }
            }

            // سرعات التشغيل — صف مستقل كي لا يتكدس السطر أعلاه على الشاشات الضيقة
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    "السرعة:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                listOf(0.75f, 1f, 1.5f, 2f).forEach { s ->
                    FilterChip(
                        selected = kotlin.math.abs(state.speed - s) < 0.02f,
                        onClick = { onSpeed(s) },
                        label = {
                            Text("${if (s == 1f) "1" else String.format(java.util.Locale.US, "%.2f", s).trimEnd('0', '.')}×",
                                fontSize = 11.sp)
                        }
                    )
                }
            }
        }
    }
}

/**
 * سطر تسجيل واحد — v0.17.0 إعادة بناء كاملة لعلاج تشوّه القائمة:
 * كان السطر يحشر حتى 7 عناصر (تشغيل + نص + 5 أيقونات أدوات) فُيُسحق العنوان على الشاشات الضيقة.
 * البنية الجديدة: صف هادئ (تشغيل + العنوان والبيانات + قائمة أدوات ⋮ واحدة)
 * والقائمة المنسدلة تجمع الجدولة/المشاركة/التصدير/التسمية/الحذف بأيقونات واضحة.
 */
@Composable
private fun RecordingRow(
    recording: RecordingEntity,
    playing: RecordingsPlayer.State,
    canManage: Boolean,
    onToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onStop: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onSchedule: () -> Unit = {},
    onShare: () -> Unit = {},
    onExport: () -> Unit = {},
    onTrim: () -> Unit = {}
) {
    val isCurrent = playing.recordingId == recording.id
    val isPlayingNow = isCurrent && playing.isPlaying
    val isLiveSrc = recording.sourceEnum().name == "PTT"
    // v0.6.0: أرقام مغربية عبر المنسّق المركزي
    val dateText = AppFormat.dateShort(recording.createdAt)
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        Modifier.fillMaxWidth().animateContentSize(),
        colors = if (isCurrent) CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        ) else CardDefaults.cardColors(),
        border = if (isCurrent) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)) else null
    ) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(
                    onClick = onToggle,
                    modifier = Modifier.size(46.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (isCurrent) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (isCurrent) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        if (isPlayingNow) AppIcons.Pause
                        else Icons.Filled.PlayArrow,
                        contentDescription = "تشغيل/إيقاف"
                    )
                }
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            recording.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isPlayingNow) {
                            Spacer(Modifier.width(8.dp))
                            PlayingEqualizer(
                                modifier = Modifier.size(width = 15.dp, height = 14.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        "$dateText • ${RecordingLogic.formatElapsed(recording.durationMs)} • " +
                            RecordingLogic.formatBytes(recording.sizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusBadgeSource(isLiveSrc)
                        // v0.36.0: شارة الترميز — Opus HD للتسجيلات الجديدة عالية الدقة
                        if (RecordingCodecLogic.isOpusRecord(recording.mimeType)) {
                            val st = MaterialTheme.status
                            StatusBadge(RecordingCodecLogic.codecLabel(true), st.success)
                        }
                        recording.recordedByName?.let { name ->
                            val role = recording.recordedByRole?.let { UserRole.from(it) }
                            Text(
                                "سجّلها: $name${role?.let { " (${it.labelAr})" } ?: ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                // v0.17.0: قائمة أدوات واحدة بدل 5 أيقونات — لا ازدحام مهما كانت الصلاحيات
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(AppIcons.MoreVert, "خيارات التسجيل")
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("جدولة كفقرة") },
                            leadingIcon = { Icon(AppIcons.Event, null, Modifier.size(20.dp)) },
                            onClick = { menuOpen = false; onSchedule() }
                        )
                        DropdownMenuItem(
                            text = { Text("مشاركة") },
                            leadingIcon = { Icon(AppIcons.Share, null, Modifier.size(20.dp)) },
                            onClick = { menuOpen = false; onShare() }
                        )
                        DropdownMenuItem(
                            text = { Text("تصدير إلى Downloads") },
                            leadingIcon = { Icon(AppIcons.Download, null, Modifier.size(20.dp)) },
                            onClick = { menuOpen = false; onExport() }
                        )
                        // v0.20.0: القص اليدوي فوق الشكل الموجي — متاح للجميع مثل المكتبة
                        DropdownMenuItem(
                            text = { Text("قص يدوي…") },
                            leadingIcon = { Icon(AppIcons.Tune, null, Modifier.size(20.dp)) },
                            onClick = { menuOpen = false; onTrim() }
                        )
                        if (canManage) {
                            HorizontalDivider(Modifier.padding(vertical = 4.dp))
                            DropdownMenuItem(
                                text = { Text("تسمية") },
                                leadingIcon = { Icon(Icons.Filled.Edit, null, Modifier.size(20.dp)) },
                                onClick = { menuOpen = false; onRename() }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "حذف",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Delete, null, Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = { menuOpen = false; onDelete() }
                            )
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = isCurrent,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    val progress = if (playing.durationMs > 0)
                        playing.positionMs.toFloat() / playing.durationMs else 0f
                    // v0.8.0: شريط تقديم تفاعلي — سحب لنقل موضع الاستماع مباشرة
                    Slider(
                        value = progress.coerceIn(0f, 1f),
                        onValueChange = { ratio ->
                            onSeek((ratio * playing.durationMs).toLong())
                        },
                        modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 10.dp),
                        enabled = playing.durationMs > 0
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 6.dp)
                    ) {
                        Text(
                            "${RecordingLogic.formatElapsed(playing.positionMs)} / " +
                                RecordingLogic.formatElapsed(playing.durationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onStop) {
                            Icon(AppIcons.Stop, null, Modifier.size(16.dp))
                            Text(" إيقاف")
                        }
                    }
                }
            }
        }
    }
}

/** شارة المصدر: بث مباشر (أحمر) أو فقرة تحضيرية (أزرق) — بألوان دلالية متوافقة مع الداكن */
@Composable
private fun StatusBadgeSource(isLive: Boolean) {
    val st = MaterialTheme.status
    StatusBadge(if (isLive) "بث مباشر" else "فقرة تحضيرية", if (isLive) st.live else st.running)
}

@Composable
private fun RenameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تسمية التسجيل") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("العنوان") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value.trim()) },
                enabled = value.isNotBlank()
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

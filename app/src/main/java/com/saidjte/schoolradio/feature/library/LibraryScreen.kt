package com.saidjte.schoolradio.feature.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saidjte.schoolradio.core.ui.AppIcons
import com.saidjte.schoolradio.core.ui.EmptyStateCard
import com.saidjte.schoolradio.core.ui.PlayingEqualizer
import com.saidjte.schoolradio.core.ui.RoundedProgressBar
import com.saidjte.schoolradio.core.ui.ScreenHeader
import com.saidjte.schoolradio.core.util.ScheduleLogic
import com.saidjte.schoolradio.data.local.entity.AudioTrackEntity
import com.saidjte.schoolradio.di.ServiceLocator
import kotlinx.coroutines.launch

/**
 * المكتبة الصوتية: استيراد عبر SAF (ملفات متعددة في عملية واحدة)، تصنيفات
 * افتراضية ومخصصة، بحث، تعديل، حذف من المكتبة فقط، واكتشاف/إعادة ربط الملفات المفقودة.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(snackbar: androidx.compose.material3.SnackbarHostState) {
    val locator = ServiceLocator
    val scope = rememberCoroutineScope()

    val tracks by locator.library.tracks.collectAsStateWithLifecycle(initialValue = emptyList())
    val categories by locator.library.categories.collectAsStateWithLifecycle(initialValue = emptyList())
    // v0.8.0: حالة مشغل القائمة الرئيسية لتمييز المقطع قيد التشغيل وتحديث أزراره
    val playback by locator.playback.state.collectAsStateWithLifecycle()
    var search by rememberSaveable { mutableStateOf("") }
    val filtered = if (search.isBlank()) tracks
    else tracks.filter { it.title.contains(search.trim(), ignoreCase = true) }

    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    var editing by remember { mutableStateOf<AudioTrackEntity?>(null) }
    var relinking by remember { mutableStateOf<AudioTrackEntity?>(null) }
    var categoryMenuOpen by remember { mutableStateOf(false) }

    // استيراد ملفات متعددة من المستندات (بلا صلاحيات تخزين شامل)
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val n = locator.library.importUris(uris, selectedCategoryId)
                snackbar.showSnackbar("استُورد $n مقطعاً إلى المكتبة")
            }
        }
    }

    // إعادة ربط مقطع فُقدت صلاحيته بملف جديد
    val relinkLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val track = relinking
        if (uri != null && track != null) {
            scope.launch {
                locator.library.relink(track, uri)
                snackbar.showSnackbar("أُعيد ربط «${track.title}» بنجاح")
            }
        }
        relinking = null
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = {
                importLauncher.launch(arrayOf("audio/*"))
            }) {
                Icon(Icons.Filled.Add, "استيراد ملفات صوتية")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {

            ScreenHeader(
                title = "المكتبة الصوتية",
                subtitle = if (tracks.isEmpty()) "لا مقاطع بعد"
                else "${tracks.size} مقطعاً جاهزاً للبرامج"
            )

            // ===== البحث =====
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                placeholder = { Text("ابحث باسم المقطع…") },
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

            // ===== شريط التصنيفات =====
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    FilterChip(
                        selected = selectedCategoryId == null,
                        onClick = { selectedCategoryId = null },
                        label = { Text("الكل") }
                    )
                }
                items(categories, key = { it.id }) { c ->
                    FilterChip(
                        selected = selectedCategoryId == c.id,
                        onClick = {
                            selectedCategoryId = if (selectedCategoryId == c.id) null else c.id
                        },
                        label = { Text(c.name) }
                    )
                }
            }

            val visible = filtered.filter {
                selectedCategoryId == null || it.categoryId == selectedCategoryId
            }

            /** v0.8.0: التشغيل الموحّد عبر الحَكَم — يقاطع المقطع المطلوب ضمن قائمة المكتبة المرئية
             *  فتعمل أزرار التالي/السابق عبر كل مقاطع القائمة، وتتوقف أي معاينة تسجيل فوراً */
            fun playFromLibrary(track: AudioTrackEntity) {
                val queue = visible.filter { it.isAvailable }.map {
                    com.saidjte.schoolradio.audio.PlaybackConnection.QueueItem(it.uri, it.title, it.id)
                }
                val startIndex = queue.indexOfFirst { it.trackId == track.id }
                locator.playMainQueue(
                    items = queue,
                    startIndex = if (startIndex >= 0) startIndex else 0,
                    queueTag = "library"
                )
            }

            if (visible.isEmpty()) {
                EmptyStateCard(
                    icon = AppIcons.MusicNote,
                    title = if (tracks.isEmpty()) "المكتبة فارغة" else "لا نتائج مطابقة",
                    body = if (tracks.isEmpty())
                        "استورد ملفات MP3/WAV من الجهاز لتجهيز فقرات الإذاعة"
                    else "جرّب كلمة بحث مختلفة أو اختر تصنيفاً آخر",
                    ctaLabel = if (tracks.isEmpty()) "استيراد مقاطع" else null,
                    onCta = if (tracks.isEmpty()) {
                        { importLauncher.launch(arrayOf("audio/*")) }
                    } else null
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        // v0.8.0: تشغيل كل القائمة المرئية — زر متدرج واضح أعلى القائمة
                        com.saidjte.schoolradio.core.ui.GradientButton(
                            label = "تشغيل الكل (${visible.size} مقطعاً)",
                            icon = Icons.Filled.PlayArrow,
                            onClick = {
                                val first = visible.firstOrNull { it.isAvailable } ?: return@GradientButton
                                playFromLibrary(first)
                            }
                        )
                    }
                    items(visible, key = { it.id }) { track ->
                        TrackRow(
                            track = track,
                            categoryName = categories.firstOrNull { it.id == track.categoryId }?.name,
                            isCurrent = playback.currentTrackId == track.id,
                            isPlaying = playback.isPlaying,
                            progress = if (playback.currentTrackId == track.id && playback.durationMs > 0)
                                playback.positionMs.toFloat() / playback.durationMs else 0f,
                            onPlay = { playFromLibrary(track) },
                            onEdit = { editing = track },
                            onDelete = {
                                scope.launch {
                                    locator.library.removeFromLibrary(track)
                                    snackbar.showSnackbar("حُذف «${track.title}» من المكتبة (بقي الملف الأصلي كما هو)")
                                }
                            },
                            onRelink = { relinking = track }
                        )
                    }
                    item { androidx.compose.foundation.layout.Spacer(Modifier.padding(40.dp)) }
                }
            }
        }
    }

    // ===== حوار التعديل =====
    editing?.let { track ->
        EditTrackDialog(
            track = track,
            categories = categories,
            onDismiss = { editing = null },
            onSave = { newTitle, newCategory ->
                scope.launch {
                    locator.library.rename(track, newTitle)
                    locator.library.moveToCategory(track, newCategory)
                    snackbar.showSnackbar("حُدّث المقطع")
                }
                editing = null
            }
        )
    }

    // ===== فحص إعادة الربط — منتقي واحد يفتح مرة لكل طلب =====
    androidx.compose.runtime.LaunchedEffect(relinking?.id) {
        if (relinking != null) relinkLauncher.launch(arrayOf("audio/*"))
    }
}

@Composable
private fun TrackRow(
    track: AudioTrackEntity,
    categoryName: String?,
    isCurrent: Boolean,
    isPlaying: Boolean,
    progress: Float,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRelink: () -> Unit
) {
    // v0.8.0: المقطع قيد التشغيل يُميّز بلون وتحريك — وأزراره تعكس الحالة الفعلية
    val isPlayingNow = isCurrent && isPlaying
    Card(
        Modifier.fillMaxWidth(),
        colors = if (isCurrent) CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ) else CardDefaults.cardColors(),
        border = if (isCurrent)
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)) else null
    ) {
        Column {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = onPlay,
                    modifier = Modifier.size(44.dp),
                    enabled = track.isAvailable,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (isCurrent) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (isCurrent) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        if (isPlayingNow) AppIcons.Pause else Icons.Filled.PlayArrow,
                        "تشغيل/إيقاف مؤقت"
                    )
                }
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            track.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isPlayingNow) {
                            androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                            PlayingEqualizer(
                                modifier = Modifier.size(width = 15.dp, height = 14.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (categoryName != null) {
                            Text(
                                categoryName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text("  •  ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            if (track.durationMs > 0)
                                ScheduleLogic.formatDuration(track.durationMs) else "مدة غير معروفة",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (!track.isAvailable) {
                    IconButton(onClick = onRelink) {
                        Icon(
                            Icons.Filled.Warning, "أعد ربط الملف",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "تعديل") }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, "حذف من المكتبة",
                        tint = MaterialTheme.colorScheme.secondary)
                }
            }
            // شريط تقدم رفيع أسفل السطر قيد التشغيل
            if (isCurrent && progress > 0f) {
                RoundedProgressBar(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                    strokeWidth = 3
                )
                androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun EditTrackDialog(
    track: AudioTrackEntity,
    categories: List<com.saidjte.schoolradio.data.local.entity.CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (String, Long?) -> Unit
) {
    var title by remember { mutableStateOf(track.title) }
    var category by remember { mutableStateOf(categories.firstOrNull { it.id == track.categoryId }) }
    var menuOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل المقطع") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("العنوان") }, singleLine = true
                )
                ExposedDropdownMenuBox(
                    expanded = menuOpen,
                    onExpandedChange = { menuOpen = it }
                ) {
                    OutlinedTextField(
                        value = category?.name ?: "بدون تصنيف",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("التصنيف") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(menuOpen) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("بدون تصنيف") },
                            onClick = { category = null; menuOpen = false }
                        )
                        categories.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c.name) },
                                onClick = { category = c; menuOpen = false }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (title.isNotBlank()) onSave(title.trim(), category?.id) }
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

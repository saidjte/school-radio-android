package com.saidjte.schoolradio.feature.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

            // ===== البحث =====
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                placeholder = { Text("ابحث باسم المقطع…") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true
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

            if (visible.isEmpty()) {
                Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(
                        "لا توجد مقاطع بعد — اضغط زر (+) لاستيراد ملفات MP3/WAV من الجهاز",
                        Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(visible, key = { it.id }) { track ->
                        TrackRow(
                            track = track,
                            categoryName = categories.firstOrNull { it.id == track.categoryId }?.name,
                            onPlay = {
                                locator.playback.playQueue(
                                    listOf(
                                        com.saidjte.schoolradio.audio.PlaybackConnection.QueueItem(
                                            track.uri, track.title, track.id
                                        )
                                    )
                                )
                            },
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
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRelink: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(track.title, style = MaterialTheme.typography.bodyLarge)
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
            } else {
                IconButton(onClick = onPlay) {
                    Icon(Icons.Filled.PlayArrow, "تشغيل")
                }
            }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "تعديل") }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, "حذف من المكتبة",
                    tint = MaterialTheme.colorScheme.secondary)
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

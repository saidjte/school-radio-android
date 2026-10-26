package com.saidjte.schoolradio.feature.playlists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saidjte.schoolradio.audio.PlaybackConnection
import com.saidjte.schoolradio.core.ui.AppIcons
import com.saidjte.schoolradio.core.ui.EmptyStateCard
import com.saidjte.schoolradio.core.ui.PlayingEqualizer
import com.saidjte.schoolradio.core.ui.ScreenHeader
import com.saidjte.schoolradio.data.local.entity.PlaylistEntity
import com.saidjte.schoolradio.di.ServiceLocator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * قوائم التشغيل: إنشاء وتسمية، إضافة/حذف مقاطع، إعادة ترتيب، تشغيل كامل، تكرار.
 */
@Composable
fun PlaylistsScreen(snackbar: androidx.compose.material3.SnackbarHostState) {
    val locator = ServiceLocator
    val scope = rememberCoroutineScope()

    val playlists by locator.playlists.playlists.collectAsStateWithLifecycle(initialValue = emptyList())
    // v0.8.0: حالة المشغل لتمييز القائمة قيد التشغيل وتحديث زرها (تشغيل/إيقاف مؤقت)
    val playback by locator.playback.state.collectAsStateWithLifecycle()
    var openPlaylistId by remember { mutableStateOf<Long?>(null) }
    var creating by remember { mutableStateOf(false) }
    var renamingId by remember { mutableStateOf<Long?>(null) }

    // اسم القائمة قيد إعادة التسمية (يُقرأ من التدفق)
    val renamingName by produceState("", renamingId) {
        value = renamingId?.let { id ->
            locator.playlists.playlists.first()
                .firstOrNull { it.id == id }?.name ?: ""
        } ?: ""
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) {
                Icon(Icons.Rounded.Add, "قائمة جديدة")
            }
        }
    ) { padding ->
        val openId = openPlaylistId
        if (openId != null) {
            PlaylistItemsScreen(playlistId = openId, snackbar = snackbar,
                onBack = { openPlaylistId = null })
        } else {
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                ScreenHeader(
                    title = "قوائم التشغيل",
                    subtitle = if (playlists.isEmpty()) "لا قوائم بعد"
                    else "${playlists.size} قائمة جاهزة للجدولة"
                )
                if (playlists.isEmpty()) {
                    EmptyStateCard(
                        icon = AppIcons.QueueMusic,
                        title = "لا توجد قوائم بعد",
                        body = "أنشئ قائمة (طابور الصباح، نشيد الأسبوع…) ثم أضف إليها مقاطع من المكتبة",
                        animAsset = "radio_waves"
                    )
                }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(playlists, key = { it.id }) { p ->
                        // v0.8.0: هل هذه هي القائمة قيد التشغيل حالياً؟ (مطابقة وسم المصدر)
                        val isThisPlaying = playback.queueTag == "playlist:${p.id}" && playback.hasQueue
                        Card(
                            Modifier.fillMaxWidth(),
                            colors = if (isThisPlaying) CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            ) else CardDefaults.cardColors(),
                            border = if (isThisPlaying) BorderStroke(
                                1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            ) else null
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.material3.Surface(
                                    shape = androidx.compose.foundation.shape.CircleShape,
                                    color = if (isThisPlaying) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                ) {
                                    Icon(
                                        AppIcons.QueueMusic, null,
                                        Modifier.padding(10.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            p.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (isThisPlaying) {
                                            androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                                            PlayingEqualizer(
                                                modifier = Modifier.size(width = 15.dp, height = 14.dp),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    Text(
                                        if (p.repeat) "التكرار مفعّل" else "بدون تكرار",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = {
                                    scope.launch {
                                        val resolved = locator.playlists.resolvedTracks(p.id)
                                        if (resolved.isEmpty()) {
                                            snackbar.showSnackbar("القائمة فارغة أو كل مقاطعها غير متاحة")
                                        } else if (isThisPlaying && playback.isPlaying) {
                                            // نفس القائمة تعمل: زر التشغيل صار إيقافاً مؤقتاً
                                            locator.playback.pause()
                                        } else if (isThisPlaying) {
                                            locator.playback.play()
                                        } else {
                                            locator.playMainQueue(
                                                resolved.map {
                                                    // v0.23.0: مقاطع البث البعيدة تجلب تدفقاً طازجاً
                                                    PlaybackConnection.QueueItem(
                                                        locator.remote.playableUriFor(it),
                                                        it.title, it.id, false,
                                                        it.trimStartMs, it.trimEndMs, it.trimManual
                                                    )
                                                },
                                                repeatAll = p.repeat,
                                                queueTag = "playlist:${p.id}"
                                            )
                                            snackbar.showSnackbar("تشغيل «${p.name}» (${resolved.size} مقطعاً)")
                                        }
                                    }
                                }) {
                                    Icon(
                                        if (isThisPlaying && playback.isPlaying) AppIcons.Pause
                                        else Icons.Rounded.PlayArrow,
                                        "تشغيل/إيقاف القائمة"
                                    )
                                }
                                // v0.17.0: قائمة أدوات واحدة بدل ثلاث أيقونات — اتساق مع الأرشيف والمكتبة
                                Box {
                                    var menuOpen by remember { mutableStateOf(false) }
                                    IconButton(onClick = { menuOpen = true }) {
                                        Icon(AppIcons.MoreVert, "خيارات القائمة")
                                    }
                                    DropdownMenu(
                                        expanded = menuOpen,
                                        onDismissRequest = { menuOpen = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("إدارة العناصر") },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.AutoMirrored.Rounded.List,
                                                    null,
                                                    Modifier.size(20.dp)
                                                )
                                            },
                                            onClick = {
                                                menuOpen = false
                                                openPlaylistId = p.id
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("تعديل الاسم") },
                                            leadingIcon = {
                                                Icon(Icons.Rounded.Edit, null, Modifier.size(20.dp))
                                            },
                                            onClick = {
                                                menuOpen = false
                                                renamingId = p.id
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    "حذف القائمة",
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Rounded.Delete, null, Modifier.size(20.dp),
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            },
                                            onClick = {
                                                menuOpen = false
                                                scope.launch {
                                                    locator.playlists.delete(p.id)
                                                    snackbar.showSnackbar("حُذفت القائمة")
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        TextDialog(
            title = "قائمة تشغيل جديدة", label = "اسم القائمة",
            onDismiss = { creating = false }
        ) { name ->
            scope.launch {
                locator.playlists.create(name)
                snackbar.showSnackbar("أُنشئت القائمة «$name»")
            }
            creating = false
        }
    }
    renamingId?.let { id ->
        TextDialog(
            title = "تعديل اسم القائمة", label = "الاسم الجديد",
            initial = renamingName,
            onDismiss = { renamingId = null }
        ) { name ->
            scope.launch {
                locator.playlists.playlistById(id)?.let { locator.playlists.rename(it, name) }
                snackbar.showSnackbar("عُدّل اسم القائمة")
            }
            renamingId = null
        }
    }
}

/** شاشة عناصر قائمة واحدة: إضافة من المكتبة، إعادة ترتيب، حذف، تكرار */
@Composable
private fun PlaylistItemsScreen(
    playlistId: Long,
    snackbar: androidx.compose.material3.SnackbarHostState,
    onBack: () -> Unit
) {
    val locator = ServiceLocator
    val scope = rememberCoroutineScope()

    val playlist: PlaylistEntity? by remember {
        locator.playlists.playlists.map { list -> list.firstOrNull { it.id == playlistId } }
    }.collectAsStateWithLifecycle(initialValue = null)
    val items by locator.playlists.observeItems(playlistId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val tracks by locator.library.tracks.collectAsStateWithLifecycle(initialValue = emptyList())

    var adding by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { adding = true }) {
                Icon(Icons.Rounded.Add, null)
                Text("  أضف مقاطع")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "رجوع")
                }
                Column(Modifier.weight(1f)) {
                    Text(playlist?.name ?: "…", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${items.size} مقطعاً • ${if (playlist?.repeat == true) "تكرار مفعّل" else "بدون تكرار"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = {
                    scope.launch {
                        locator.playlists.playlistById(playlistId)?.let {
                            locator.playlists.setRepeat(it, !it.repeat)
                        }
                    }
                }) {
                    Icon(
                        AppIcons.Repeat, "تبديل التكرار",
                        tint = if (playlist?.repeat == true) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = {
                    scope.launch {
                        val resolved = locator.playlists.resolvedTracks(playlistId)
                        if (resolved.isEmpty()) {
                            snackbar.showSnackbar("القائمة فارغة أو كل مقاطعها غير متاحة")
                        } else {
                            locator.playMainQueue(
                                resolved.map {
                                    // v0.23.0: مقاطع البث البعيدة تجلب تدفقاً طازجاً
                                    PlaybackConnection.QueueItem(
                                        locator.remote.playableUriFor(it),
                                        it.title, it.id, false,
                                        it.trimStartMs, it.trimEndMs, it.trimManual
                                    )
                                },
                                repeatAll = playlist?.repeat == true,
                                queueTag = "playlist:$playlistId"
                            )
                        }
                    }
                }) { Icon(Icons.Rounded.PlayArrow, "تشغيل القائمة") }
            }

            if (items.isEmpty()) {
                Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(
                        "القائمة فارغة — أضف مقاطع بزر (+)",
                        Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
                    val track = tracks.firstOrNull { it.id == item.trackId }
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${index + 1}.",
                                Modifier.padding(horizontal = 6.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    track?.title ?: "مقطع محذوف",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                if (track != null && !track.isAvailable) {
                                    Text(
                                        "غير متاح — أعد ربطه من المكتبة",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        locator.playlists.move(playlistId, index, index - 1)
                                    }
                                },
                                enabled = index > 0
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowForward, "تحريك لأعلى")
                            }
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        locator.playlists.move(playlistId, index, index + 1)
                                    }
                                },
                                enabled = index < items.size - 1
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "تحريك لأسفل")
                            }
                            IconButton(onClick = {
                                scope.launch {
                                    locator.playlists.removeItem(item.id)
                                    snackbar.showSnackbar("أُزيل المقطع من القائمة")
                                }
                            }) { Icon(Icons.Rounded.Close, "إزالة") }
                        }
                    }
                }
                item { Spacer(Modifier.padding(50.dp)) }
            }
        }
    }

    if (adding) {
        AddTracksDialog(
            excludeIds = items.map { it.trackId }.toSet(),
            onDismiss = { adding = false },
            onAdd = { trackId ->
                scope.launch { locator.playlists.addTrack(playlistId, trackId) }
            }
        )
    }
}

/** حوار إضافة مقاطع من المكتبة (اختيار متعدد) */
@Composable
private fun AddTracksDialog(
    excludeIds: Set<Long>,
    onDismiss: () -> Unit,
    onAdd: (Long) -> Unit
) {
    val tracks by ServiceLocator.library.tracks.collectAsStateWithLifecycle(initialValue = emptyList())
    val available = tracks.filter { it.isAvailable && it.id !in excludeIds }
    val selected = remember { mutableStateOf(setOf<Long>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("أضف مقاطع إلى القائمة") },
        text = {
            LazyColumn {
                items(available, key = { it.id }) { t ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = t.id in selected.value,
                            onCheckedChange = { checked ->
                                selected.value =
                                    if (checked) selected.value + t.id else selected.value - t.id
                            }
                        )
                        Text(t.title, Modifier.weight(1f))
                    }
                }
                if (available.isEmpty()) {
                    item {
                        Text(
                            "كل مقاطع المكتبة مضافة بالفعل أو المكتبة فارغة",
                            Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    selected.value.forEach { onAdd(it) }
                    onDismiss()
                },
                enabled = selected.value.isNotEmpty()
            ) { Text("إضافة (${selected.value.size})") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

/** حوار إدخال نص عام */
@Composable
private fun TextDialog(
    title: String,
    label: String,
    initial: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                label = { Text(label) }, singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text.trim()) }
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

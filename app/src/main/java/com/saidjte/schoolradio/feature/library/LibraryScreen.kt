package com.saidjte.schoolradio.feature.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.DropdownMenu
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
    // v0.10.0: إدارة التصنيفات — إضافة سريعة + حوار إدارة كامل
    var addCategoryOpen by remember { mutableStateOf(false) }
    var manageCategoriesOpen by remember { mutableStateOf(false) }

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

            // ===== شريط التصنيفات (v0.10.0: مع رقاقة إضافة جديدة + زر إدارة) =====
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
                // v0.10.0: رقاقة «+» سريعة — إضافة تصنيف جديد من مكان العرض نفسه
                item {
                    FilterChip(
                        selected = false,
                        onClick = { addCategoryOpen = true },
                        label = { Text("+ تصنيف") },
                        leadingIcon = {
                            Icon(Icons.Filled.Add, "إضافة تصنيف جديد", Modifier.size(16.dp))
                        }
                    )
                }
            }
            // v0.10.0: زر إدارة التصنيفات — إعادة تسمية وحذف بعدد المقاطع لكل تصنيف
            TextButton(
                onClick = { manageCategoriesOpen = true },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Icon(Icons.Filled.Edit, null, Modifier.size(15.dp))
                Text("إدارة التصنيفات", style = MaterialTheme.typography.labelMedium)
            }

            val visible = filtered.filter {
                selectedCategoryId == null || it.categoryId == selectedCategoryId
            }

            /** v0.8.0: التشغيل الموحّد عبر الحَكَم — يقاطع المقطع المطلوب ضمن قائمة المكتبة المرئية
             *  فتعمل أزرار التالي/السابق عبر كل مقاطع القائمة، وتتوقف أي معاينة تسجيل فوراً */
            fun playFromLibrary(track: AudioTrackEntity) {
                val queue = visible.filter { it.isAvailable }.map {
                    com.saidjte.schoolradio.audio.PlaybackConnection.QueueItem.of(it)
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
                            onRelink = { relinking = track },
                            // v0.18.0: تحليل يدوي لقص الفراغ الأول مع رسالة النتيجة
                            onTrim = {
                                scope.launch {
                                    val trim = locator.library.analyzeSilenceById(track.id)
                                    snackbar.showSnackbar(
                                        com.saidjte.schoolradio.core.util.SilenceLogic
                                            .message(trim, track.title)
                                    )
                                }
                            }
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

    // ===== v0.10.0: إضافة تصنيف سريعة من رقاقة «+» =====
    if (addCategoryOpen) {
        AddCategoryDialog(
            onDismiss = { addCategoryOpen = false },
            onConfirm = { name ->
                scope.launch {
                    val err = locator.library.addCategory(name)
                    snackbar.showSnackbar(err ?: "أُضيف التصنيف «${name.trim()}»")
                }
                addCategoryOpen = false
            }
        )
    }

    // ===== v0.10.0: إدارة التصنيفات الكاملة (تسمية/حذف/إضافة بعدد المقاطع) =====
    if (manageCategoriesOpen) {
        ManageCategoriesDialog(
            onDismiss = { manageCategoriesOpen = false },
            onAdd = { name, onDone ->
                scope.launch {
                    val err = locator.library.addCategory(name)
                    snackbar.showSnackbar(err ?: "أُضيف التصنيف «${name.trim()}»")
                    onDone(err)
                }
            },
            onRename = { category, newName, onDone ->
                scope.launch {
                    val err = locator.library.renameCategory(category, newName)
                    snackbar.showSnackbar(err ?: "عُدّل اسم التصنيف إلى «${newName.trim()}»")
                    onDone(err)
                }
            },
            onDelete = { category, onDone ->
                scope.launch {
                    val err = locator.library.deleteCategory(category)
                    snackbar.showSnackbar(
                        err ?: "حُذف التصنيف — عادت مقاطعه إلى «بدون تصنيف»"
                    )
                    onDone(err)
                }
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
    onRelink: () -> Unit,
    onTrim: () -> Unit = {}
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
                // v0.17.0: قائمة أدوات واحدة بدل أيقونتين ظاهرتين — اتساق مع الأرشيف والقوائم
                Box {
                    var menuOpen by remember { mutableStateOf(false) }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(AppIcons.MoreVert, "خيارات المقطع")
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false }
                    ) {
                        // v0.18.0: قياس الفراغ الأول يدوياً — النتيجة تظهر snackbar
                        DropdownMenuItem(
                            text = { Text("قص الفراغ الأول") },
                            leadingIcon = {
                                Icon(AppIcons.Cut, null, Modifier.size(20.dp))
                            },
                            onClick = { menuOpen = false; onTrim() }
                        )
                        DropdownMenuItem(
                            text = { Text("تعديل") },
                            leadingIcon = { Icon(Icons.Filled.Edit, null, Modifier.size(20.dp)) },
                            onClick = { menuOpen = false; onEdit() }
                        )
                        DropdownMenuItem(
                            text = { Text(
                                "حذف من المكتبة",
                                color = MaterialTheme.colorScheme.error
                            ) },
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

/**
 * v0.10.0: حوار إضافة تصنيف جديد — من رقاقة «+» في شريط التصنيفات.
 * تحقق فوري: اسم فارغ يُعطّل زر الحفظ، والتكرار تُرجعه المستودع كرسالة snackbar.
 */
@Composable
private fun AddCategoryDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تصنيف جديد") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "أضف تصنيفاً يناسب برنامج مدرستك (مثلاً: مسابقات، ترحيب، وقفات إيمانية…)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("اسم التصنيف") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value) },
                enabled = value.isNotBlank()
            ) { Text("إضافة") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

/**
 * v0.10.0: حوار إدارة التصنيفات الكامل — قائمة بكل التصنيفات مع عدد مقاطعها،
 * إعادة تسمية لأي تصنيف، حذف للمخصص فقط (الافتراضية محمية)، وإضافة جديدة من الأعلى.
 * العمليات غير متزامنة عبر [onDone] — رسالة الخطأ تُعرض للمستخدم ولا يُغلق الحوار.
 */
@Composable
private fun ManageCategoriesDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, onDone: (String?) -> Unit) -> Unit,
    onRename: (com.saidjte.schoolradio.data.local.entity.CategoryEntity, String, (String?) -> Unit) -> Unit,
    onDelete: (com.saidjte.schoolradio.data.local.entity.CategoryEntity, (String?) -> Unit) -> Unit
) {
    val locator = ServiceLocator
    val categoriesWithCounts by locator.library.categoriesWithCounts
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var newName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<com.saidjte.schoolradio.data.local.entity.CategoryEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<com.saidjte.schoolradio.data.local.entity.CategoryEntity?>(null) }
    var busy by remember { mutableStateOf(false) }

    /** ينفّذ العملية ثم يعرض الخطأ إن وُجد (لا يغلق شيئاً — القائمة تتجدد بالتدفق) */
    fun run(op: () -> Unit) {
        if (busy) return
        busy = true
        op()
    }

    fun clearBusy(err: String?) { busy = false; renameTarget = null; deleteTarget = null; if (err != null) Unit }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إدارة التصنيفات") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // ===== إضافة سريعة داخل الحوار نفسه =====
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("تصنيف جديد…") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledTonalIconButton(
                        onClick = { run { onAdd(newName) { err -> clearBusy(err) }; newName = "" } },
                        enabled = newName.isNotBlank() && !busy
                    ) {
                        Icon(Icons.Filled.Add, "إضافة التصنيف")
                    }
                }

                if (categoriesWithCounts.isEmpty()) {
                    Text(
                        "لا تصنيفات بعد",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    // قائمة التصنيفات — ارتفاع محدود كي لا يفيض الحوار على الشاشات الصغيرة
                    Column(
                        Modifier
                            .height(300.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        categoriesWithCounts.forEach { c ->
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        c.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Text(
                                        if (c.trackCount == 0) "لا مقاطع" else
                                            "${c.trackCount} مقطعاً" +
                                                if (c.isBuiltIn) " • افتراضي" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { renameTarget =
                                    com.saidjte.schoolradio.data.local.entity.CategoryEntity(
                                        id = c.id, name = c.name,
                                        sortOrder = c.sortOrder, isBuiltIn = c.isBuiltIn
                                    ) }) {
                                    Icon(Icons.Filled.Edit, "إعادة تسمية", Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = {
                                        deleteTarget =
                                            com.saidjte.schoolradio.data.local.entity.CategoryEntity(
                                                id = c.id, name = c.name,
                                                sortOrder = c.sortOrder, isBuiltIn = c.isBuiltIn
                                            )
                                    },
                                    enabled = !c.isBuiltIn
                                ) {
                                    Icon(
                                        Icons.Filled.Delete, "حذف التصنيف",
                                        Modifier.size(18.dp),
                                        tint = if (c.isBuiltIn)
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                        else MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("تم") }
        }
    )

    // حوار إعادة التسمية الفرعي
    renameTarget?.let { target ->
        var renameValue by remember(target.id) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("إعادة تسمية «${target.name}»") },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    label = { Text("الاسم الجديد") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        run { onRename(target, renameValue) { err -> clearBusy(err) } }
                    },
                    enabled = renameValue.isNotBlank() && !busy
                ) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("إلغاء") } }
        )
    }

    // حوار تأكيد الحذف الفرعي — يوضح مصير مقاطع التصنيف
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("حذف «${target.name}»؟") },
            text = {
                Text(
                    "سيُحذف التصنيف فقط وتعود مقاطعه إلى «بدون تصنيف» — لا يُحذف أي ملف صوتي."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    run { onDelete(target) { err -> clearBusy(err) } }
                }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("إلغاء") } }
        )
    }
}

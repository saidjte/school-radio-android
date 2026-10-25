package com.saidjte.schoolradio.feature.cartwall

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saidjte.schoolradio.audio.CartPlayer
import com.saidjte.schoolradio.core.ui.AppIcons
import com.saidjte.schoolradio.core.ui.RoundedProgressBar
import com.saidjte.schoolradio.core.ui.ScreenHeader
import com.saidjte.schoolradio.core.util.AppFormat
import com.saidjte.schoolradio.data.cartwall.CartPad
import com.saidjte.schoolradio.data.cartwall.CartWallStore
import com.saidjte.schoolradio.data.local.entity.AudioTrackEntity
import com.saidjte.schoolradio.data.settings.AppSettings
import com.saidjte.schoolradio.di.ServiceLocator
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * لوحة ألوان الرقائق الثابتة — 8 ألوان إذاعية واضحة تعمل على الفاتح والداكن.
 */
internal val CART_COLORS = listOf(
    Color(0xFFEF5350), // أحمر
    Color(0xFFAB47BC), // بنفسجي
    Color(0xFF5C6BC0), // نيلي
    Color(0xFF29B6F6), // أزرق
    Color(0xFF26A69A), // فيروزي
    Color(0xFF9CCC65), // أخضر
    Color(0xFFFFB74D), // برتقالي
    Color(0xFF8D6E63)  // بني
)

/**
 * حائط الجينغل (Cart Wall) — v0.11.0، القلب النابض للبث المباشر:
 *
 *  - شبكة رقائق لمسية بلمسة واحدة: النشيد الوطني، الفواصل، الأثر الصوتي،
 *    عدّاد تنازلي، موسيقى خلفية — كل شيء على بعد ضغطة أثناء الهواء.
 *  - صفحتان × 8 رقائق (16 رقاقة) بألوان مخصصة — المرجع: PlayIt Cartwall
 *    وصفحات الرقائق في الاستوديوهات الحقيقية.
 *  - دلالة SOLO: إطلاق رقاقة توقف التي قبلها بتلاشٍ سريع، والتكرار (Loop)
 *    لكل رقاقة على حدة لموسيقى الخلفية.
 *  - ★ الجينغل يعمل فوق البث المباشر عمداً — النشيد خلف صوت المذيع سلوك إذاعي طبيعي.
 *  - ضغطة على رقاقة فارغة أو ضغط مطول على أي رقاقة = محرر الرقاقة
 *    (اختيار مقطع من المكتبة + لون + تكرار + مسح).
 */
@Composable
fun CartWallScreen(snackbar: androidx.compose.material3.SnackbarHostState) {
    val locator = ServiceLocator
    val scope = rememberCoroutineScope()
    val cart = locator.cartPlayer

    val settings by locator.settings.settings
        .collectAsStateWithLifecycle(initialValue = AppSettings())
    val tracks by locator.library.tracks
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val cartState by cart.state.collectAsStateWithLifecycle()

    val pads = remember(settings.cartLayoutJson) { CartWallStore.parse(settings.cartLayoutJson) }
    var page by rememberSaveable { mutableStateOf(0) }
    /** رقاقة قيد التحرير (حوار الإسناد) — بلاها لا حوار */
    var editingPadKey by rememberSaveable { mutableStateOf<String?>(null) }

    // مزامنة الفيدَر المحفوظ مع المحرك عند الدخول (الخيط الرئيسي — آمن)
    LaunchedEffect(settings.cartVolume) { cart.setVolume(settings.cartVolume) }

    val message: (String) -> Unit = { m -> scope.launch { snackbar.showSnackbar(m) } }

    fun savePads(updated: List<CartPad>) {
        scope.launch { locator.settings.setCartLayoutJson(CartWallStore.serialize(updated)) }
    }

    val editingPad = pads.firstOrNull { it.key == editingPadKey }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenHeader(
            title = "حائط الجينغل",
            subtitle = "أطلق النشيد والفواصل والمؤثرات بلمسة واحدة — فوق البث والموسيقى"
        )

        // ===== الصفحات + زر الإيقاف =====
        Row(verticalAlignment = Alignment.CenterVertically) {
            (0 until CartWallStore.PAGES).forEach { p ->
                FilterChip(
                    selected = page == p,
                    onClick = { page = p },
                    label = { Text("الصفحة ${p + 1}") },
                    modifier = Modifier.padding(end = 8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
            Spacer(Modifier.weight(1f))
            if (cartState.activeKey != null) {
                TextButton(onClick = { cart.stop() }) {
                    Icon(AppIcons.Stop, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("إيقاف")
                }
            }
        }

        // ===== شبكة رقائق الصفحة الحالية (4×2) =====
        pads.filter { it.page == page }.chunked(4).forEach { rowPads ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowPads.forEach { pad ->
                    val track = tracks.firstOrNull { it.id == pad.trackId }
                    CartPadCell(
                        pad = pad,
                        track = track,
                        playing = cartState.activeKey == pad.key,
                        progress = if (cartState.activeKey == pad.key && cartState.durationMs > 0)
                            cartState.positionMs.toFloat() / cartState.durationMs else 0f,
                        remainingLabel = if (cartState.activeKey == pad.key)
                            remainingLabel(cartState) else null,
                        onTap = {
                            if (pad.isEmpty) {
                                editingPadKey = pad.key
                            } else if (track != null) {
                                cart.fire(
                                    padKey = pad.key,
                                    uri = track.uri,
                                    title = track.title,
                                    loop = pad.loop,
                                    volume = cartState.volume,
                                    // v0.18.0: قص الفراغ الأول المقيس لهذا المقطع
                                    trimStartMs = track.trimStartMs
                                )?.let { message(it) }
                            } else {
                                message("مقطع هذا الجينغل لم يعد في المكتبة — اضغط مطولاً لإعادة الربط")
                            }
                        },
                        onLongPress = { editingPadKey = pad.key }
                    )
                }
            }
        }

        // ===== فيدَر قناة الجينغل =====
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(AppIcons.VolumeUp, null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    "مستوى الجينغل: ${(settings.cartVolume * 100).roundToInt()}٪",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
                Slider(
                    value = settings.cartVolume,
                    onValueChange = { cart.setVolume(it) },
                    onValueChangeFinished = {
                        scope.launch { locator.settings.setCartVolume(cart.state.value.volume) }
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Text(
            "نصيحة إذاعية: عيّن النشيد الوطني والفواصل والأثر الصوتي على رقائق ملونة " +
                "واحفظ الترتيب — المذيع المحترف يطلق الجينغل دون أن يبحث في المكتبة أثناء الهواء. " +
                "ضغطة مطولة على أي رقاقة تعدّل مقطعها ولونها ووضع التكرار.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start
        )
        Spacer(Modifier.height(60.dp))
    }

    // ===== محرر الرقاقة =====
    editingPad?.let { pad ->
        PadEditorDialog(
            pad = pad,
            tracks = tracks,
            onDismiss = { editingPadKey = null },
            onApply = { updated ->
                savePads(pads.map { if (it.key == updated.key) updated else it })
                editingPadKey = null
            },
            onClear = {
                savePads(pads.map {
                    if (it.key == pad.key) it.copy(trackId = null, loop = false) else it
                })
                // الرقاقة تعمل؟ إيقافها فور مسحها
                if (cart.state.value.activeKey == pad.key) cart.stop()
                editingPadKey = null
            }
        )
    }
}

/** ملصق الوقت المتبقي للرقاقة النشطة */
private fun remainingLabel(s: CartPlayer.State): String {
    if (s.loop || s.durationMs <= 0) return "تكرار ∞"
    val remainingMs = (s.durationMs - s.positionMs).coerceAtLeast(0)
    val sec = remainingMs / 1000
    return "متبقٍ ${AppFormat.westernDigits(if (sec >= 60) "${sec / 60}:${"%02d".format(sec % 60)}" else "${sec}ث")}"
}

// =====================================================================
// رقاقة واحدة
// =====================================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.CartPadCell(
    pad: CartPad,
    track: AudioTrackEntity?,
    playing: Boolean,
    progress: Float,
    remainingLabel: String?,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val baseColor = CART_COLORS[pad.colorIndex.coerceIn(0, CART_COLORS.lastIndex)]
    val accent = baseColor.copy(alpha = 0.22f)
    val borderAlpha by animateFloatAsState(
        targetValue = if (playing) 1f else 0f,
        animationSpec = tween(180),
        label = "pad-border"
    )
    val title = when {
        track != null -> track.title
        pad.isEmpty -> ""
        else -> "مقطع محذوف — اضغط مطولاً"
    }

    Box(
        Modifier
            .weight(1f)
            .aspectRatio(1.05f)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (pad.isEmpty) MaterialTheme.colorScheme.surfaceContainerLow else accent
            )
            .border(
                width = if (playing) 2.dp else 0.dp,
                color = baseColor.copy(alpha = borderAlpha),
                shape = RoundedCornerShape(16.dp)
            )
            .combinedClickable(onClick = onTap, onLongClick = onLongPress)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (pad.isEmpty) {
                Text(
                    "＋",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "رقاقة فارغة",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            } else {
                Icon(
                    AppIcons.MusicNote, null,
                    tint = baseColor,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (pad.loop) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            AppIcons.Autorenew, "تكرار",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            "تكرار",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 3.dp)
                        )
                    }
                }
            }
        }

        // الوقت المتبقي — ركن علوي
        if (remainingLabel != null && !pad.isEmpty) {
            Text(
                remainingLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                        RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            )
        }

        // شريط التقدم أسفل الرقاقة
        if (playing) {
            RoundedProgressBar(
                progress = progress.coerceIn(0f, 1f),
                color = baseColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeWidth = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
    }
}

// =====================================================================
// محرر الرقاقة: إسناد مقطع + لون + تكرار + مسح
// =====================================================================

@Composable
private fun PadEditorDialog(
    pad: CartPad,
    tracks: List<AudioTrackEntity>,
    onDismiss: () -> Unit,
    onApply: (CartPad) -> Unit,
    onClear: () -> Unit
) {
    var colorIndex by remember(pad.key) { mutableStateOf(pad.colorIndex) }
    var loop by remember(pad.key) { mutableStateOf(pad.loop) }
    var selectedTrackId by remember(pad.key) { mutableStateOf(pad.trackId) }
    var query by remember(pad.key) { mutableStateOf("") }

    val filtered = remember(query, tracks) {
        val q = query.trim()
        if (q.isEmpty()) tracks else tracks.filter { it.title.contains(q, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onApply(pad.copy(colorIndex = colorIndex, loop = loop, trackId = selectedTrackId))
            }) { Text(if (selectedTrackId == null) "إفراغ الرقاقة" else "حفظ") }
        },
        dismissButton = {
            TextButton(onClick = onClear) { Text("مسح الربط", color = MaterialTheme.colorScheme.error) }
        },
        title = { Text("الرقاقة — الصفحة ${pad.page + 1} / موضع ${pad.index + 1}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // اختيار المقطع مع بحث
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("ابحث في المكتبة") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(
                    Modifier
                        .fillMaxWidth()
                        .height(230.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (filtered.isEmpty()) {
                        item {
                            Text(
                                if (tracks.isEmpty()) "المكتبة فارغة — أضف مقاطع من تبويب المكتبة أولاً"
                                else "لا نتائج مطابقة",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                    items(filtered, key = { it.id }) { t ->
                        val selected = selectedTrackId == t.id
                        Card(
                            onClick = {
                                selectedTrackId = if (selected) null else t.id
                            },
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected)
                                    MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    AppIcons.MusicNote, null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                    Text(
                                        t.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (t.durationMs > 0) {
                                        Text(
                                            formatDuration(t.durationMs),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (selected) {
                                    Icon(
                                        AppIcons.CheckCircle, "مختار",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // لوحة الألوان
                Text("لون الرقاقة", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CART_COLORS.forEachIndexed { i, c ->
                        Box(
                            Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(
                                    width = if (colorIndex == i) 2.5.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = CircleShape
                                )
                                .combinedClickableCompat { colorIndex = i }
                        )
                    }
                }

                // وضع التكرار
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("تكرار لا نهائي (موسيقى خلفية)", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "مناسب للنشيدها الخلفية أثناء الإعلان",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = loop, onCheckedChange = { loop = it })
                }
            }
        }
    )
}

/** تغليف لأن combinedClickable داخل العناصر القابلة للنقر (تجنب تكرار OptIn) */
@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit): Modifier =
    this.combinedClickable(onClick = onClick)

/** مدة مقروءة قصيرة "2:35" */
private fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return AppFormat.westernDigits("${s / 60}:${"%02d".format(s % 60)}")
}

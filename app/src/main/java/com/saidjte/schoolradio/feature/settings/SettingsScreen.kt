package com.saidjte.schoolradio.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saidjte.schoolradio.BuildConfig
import com.saidjte.schoolradio.audio.AudioEffects
import com.saidjte.schoolradio.core.ui.AppIcons
import com.saidjte.schoolradio.core.ui.EqualizerCard
import com.saidjte.schoolradio.core.util.PerformanceModeLogic
import com.saidjte.schoolradio.data.backup.BackupRepository
import com.saidjte.schoolradio.data.settings.AppSettings
import com.saidjte.schoolradio.di.ServiceLocator
import kotlinx.coroutines.launch

/** عنوان بطاقة إعداد بأيقونة حاوية */
@Composable
private fun CardTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        androidx.compose.material3.Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        ) {
            Icon(
                icon, null,
                Modifier.padding(7.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Text(
            "  $title",
            style = MaterialTheme.typography.titleMedium
        )
    }
}

/**
 * الإعدادات: هوية المدرسة، المظهر، مستويات الصوت والخفض التلقائي،
 * النسخ الاحتياطي عبر SAF (تصدير/استعادة مع تحقق)، ومعلومات الإصدار.
 */
@Composable
fun SettingsScreen(snackbar: androidx.compose.material3.SnackbarHostState) {
    val locator = ServiceLocator
    val scope = rememberCoroutineScope()

    val settings by locator.settings.settings
        .collectAsStateWithLifecycle(initialValue = AppSettings())
    var schoolName by remember(settings.schoolName) { mutableStateOf(settings.schoolName) }
    var busy by remember { mutableStateOf(false) }

    // تصدير: المستخدم يختار المكان (SAF)
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupRepository.BACKUP_MIME)
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                busy = true
                locator.backup.exportTo(uri)
                    .onSuccess { count ->
                        locator.settings.setLastBackupUri(uri.toString())
                        snackbar.showSnackbar("صُدّرت النسخة ($count مقطعاً) بنجاح")
                    }
                    .onFailure { snackbar.showSnackbar("فشل التصدير: ${it.message}") }
                busy = false
            }
        }
    }

    // استعادة: المستخدم يختار ملف النسخة
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                busy = true
                locator.backup.restoreFrom(uri)
                    .onSuccess { msg ->
                        snackbar.showSnackbar(msg)
                        locator.rearmScheduleAlarms()
                    }
                    .onFailure { snackbar.showSnackbar("فشل الاستعادة: ${it.message}") }
                busy = false
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("الإعدادات", style = MaterialTheme.typography.titleLarge)

        // ===== هوية المدرسة =====
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("هوية المدرسة", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = schoolName,
                    onValueChange = { schoolName = it },
                    label = { Text("اسم المدرسة") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Button(
                    onClick = {
                        scope.launch {
                            locator.settings.setSchoolName(schoolName.trim())
                            snackbar.showSnackbar("حُفظ اسم المدرسة")
                        }
                    },
                    enabled = schoolName.isNotBlank() && schoolName != settings.schoolName
                ) { Text("حفظ الاسم") }
            }
        }

        // ===== المظهر =====
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CardTitle(AppIcons.Palette, "المظهر")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(
                        0 to "حسب النظام",
                        1 to "فاتح",
                        2 to "داكن"
                    ).forEachIndexed { index, (mode, label) ->
                        SegmentedButton(
                            selected = settings.themeMode == mode,
                            onClick = { scope.launch { locator.settings.setThemeMode(mode) } },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = 3)
                        ) { Text(label) }
                    }
                }
            }
        }

        // ===== الصوت والخفض التلقائي =====
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CardTitle(AppIcons.VolumeUp, "الصوت والبث المباشر")

                Text("مستوى الصوت الافتراضي: ${(settings.defaultVolume * 100).toInt()}%")
                Slider(
                    value = settings.defaultVolume,
                    onValueChange = { scope.launch { locator.settings.setDefaultVolume(it) } }
                )

                HorizontalDivider()

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("الخفض التلقائي أثناء الإعلان")
                        Text(
                            "يُخفَّض صوت المقطع الجاري أثناء البث المباشر ويعود بعد الإنهاء",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.duckEnabled,
                        onCheckedChange = { scope.launch { locator.settings.setDuckEnabled(it) } }
                    )
                }
                if (settings.duckEnabled) {
                    Text("مستوى الموسيقى أثناء الإعلان: ${(settings.duckVolume * 100).toInt()}%")
                    Slider(
                        value = settings.duckVolume,
                        onValueChange = { scope.launch { locator.settings.setDuckVolume(it) } }
                    )
                }

                HorizontalDivider()

                // v0.29.0: ترميز تسجيلات الميكروفون — Opus HD عالي الدقة
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("تسجيل الميكروفون عالي الدقة (Opus HD)")
                        Text(
                            "يُسجَّل صوت البث المباشر والفقرات التحضيرية بترميز Opus عند 48kHz/128kbps في ملفات ‎.ogg أنقى صوتاً — شفافية كاملة للكلام وموسيقى جينلات بلا صفير — وبالحجم نفسه تقريباً. يعمل على أندرويد 10 وما بعده، والأجهزة الأقدم تعود تلقائياً إلى AAC دون أي إعداد. أطفئه إذا احتجت ملفات m4a متوافقة مع نظام قديم",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.recordingHd,
                        onCheckedChange = {
                            scope.launch {
                                locator.settings.setRecordingHd(it)
                                snackbar.showSnackbar(
                                    if (it) "التسجيلات القادمة ستُكتب Opus HD عالية الدقة"
                                    else "التسجيلات القادمة ستُكتب AAC متوافقة (‎.m4a)"
                                )
                            }
                        }
                    )
                }

                HorizontalDivider()

                // v0.31.0: وضع الأداء — واجهة مبسطة بلا زخارف للأجهزة الضعيفة
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("وضع الأداء")
                        Text(
                            PerformanceModeLogic.description(settings.performanceMode),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.performanceMode,
                        onCheckedChange = {
                            scope.launch {
                                locator.settings.setPerformanceMode(it)
                                snackbar.showSnackbar(
                                    if (it) "وضع الأداء مفعّل — واجهة مبسطة أسرع على هذا الجهاز"
                                    else "عادت الزخارف والحركات الحية"
                                )
                            }
                        }
                    )
                }

                HorizontalDivider()

                // v0.18.0: قص الفراغ الأول من المقاطع (أو تجاهله)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("قص الفراغ الأول من المقاطع")
                        Text(
                            "يقفز المشغل فوق الصمت في بداية المقاطع والجينلات لتنطلق من أول نغمة — أطفئه لتشغيلها كما هي (تجاهل القص). القص اليدوي من قائمة المقطع في المكتبة يُطبَّق دائماً بغضّ النظر عن هذا المفتاح",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.trimSilence,
                        onCheckedChange = {
                            scope.launch {
                                locator.settings.setTrimSilence(it)
                                snackbar.showSnackbar(
                                    if (it) "سُيُقص الفراغ الأول من المقاطع"
                                    else "سُيتجاهل القص — المقاطع تعمل من بدايتها كما هي"
                                )
                            }
                        }
                    )
                }
            }
        }

        // ===== v0.22.0: معادل الصوت — نفس مكوّن الاستوديو (الحالة مشتركة ومتزامنة) =====
        val fx by AudioEffects.state.collectAsStateWithLifecycle()
        fun persistEq() {
            val d = AudioEffects.snapshot()
            scope.launch {
                locator.settings.setEqConfig(d.presetId, d.gains)
                locator.settings.setEqEnabled(d.enabled)
                locator.settings.setBassStrength(d.bass)
                locator.settings.setVirtStrength(d.virt)
            }
        }
        EqualizerCard(
            fx = fx,
            onEnabled = { AudioEffects.setEnabled(it); persistEq() },
            onPreset = { AudioEffects.setPreset(it); persistEq() },
            onRadioPreset = { AudioEffects.applyRadioPreset(it); persistEq() },
            onBand = { index, db -> AudioEffects.setBandGainDb(index, db) },
            onBandFinished = { persistEq() },
            onBass = { AudioEffects.setBassStrength(it) },
            onBassFinished = { persistEq() },
            onVirt = { AudioEffects.setVirtualizerStrength(it) },
            onVirtFinished = { persistEq() },
            onReset = {
                fx.bands.forEach { b -> AudioEffects.setBandGainDb(b.index, 0f) }
                AudioEffects.setBassStrength(0f)
                AudioEffects.setVirtualizerStrength(0f)
                AudioEffects.setPresetClear()
                persistEq()
            }
        )

        // ===== المستخدمون والأدوار (v0.3.0) =====
        UsersAndRolesCard(snackbar)

        // ===== النسخ الاحتياطي =====
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("النسخ الاحتياطي والاستعادة", style = MaterialTheme.typography.titleMedium)
                Text(
                    "تشمل النسخة: المقاطع (URI) والتصنيفات والقوائم والجداول وسجل التنفيذ والإعدادات. " +
                        "لا تشمل الملفات الصوتية الأصلية نفسها — بقاء الوصول إليها مرهون ببقاء الملفات على الجهاز.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            exportLauncher.launch(
                                "إذاعة-المدرسية-${System.currentTimeMillis()}.schoolradio.json"
                            )
                        },
                        enabled = !busy
                    ) { Text("تصدير نسخة") }
                    OutlinedButton(
                        onClick = {
                            restoreLauncher.launch(arrayOf("*/*"))
                        },
                        enabled = !busy
                    ) { Text("استعادة") }
                }
                settings.lastBackupUri?.let {
                    Text(
                        "آخر نسخة مصدَّرة: ${it.substringAfterLast('/')}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (busy) {
                    Text("جارٍ التنفيذ…", color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // ===== الإصدار =====
        Card {
            Column(Modifier.padding(16.dp)) {
                Text("معلومات الإصدار", style = MaterialTheme.typography.titleMedium)
                Text(
                    "الإذاعة المدرسية v${BuildConfig.VERSION_NAME} " +
                        "(versionCode ${BuildConfig.VERSION_CODE}) — يعمل دون إنترنت بالكامل",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = {
                    scope.launch {
                        val missed = locator.schedules.markMissed(System.currentTimeMillis())
                        locator.rearmScheduleAlarms()
                        snackbar.showSnackbar("فُحصت الفقرات: $missed فائتة — أُعيدت ضبط المنبهات")
                    }
                }) { Text("فحص الفقرات الفائتة وإعادة ضبط المنبهات") }
            }
        }
    }
}

/**
 * بطاقة المستخدمين والأدوار (v0.3.0):
 *  - عرض المستخدم الحالي ودوره + قائمة المستخدمين مع التبديل بالنقر
 *  - إضافة/حذف مستخدم = مشرف فقط (تُخفى الأزرار عن غيره)
 *  - الأدوار: مشرف > معلم (يدير الجدولة والتسجيلات) > طالب (بث وتحضير)
 */
@Composable
private fun UsersAndRolesCard(snackbar: androidx.compose.material3.SnackbarHostState) {
    val locator = ServiceLocator
    val scope = rememberCoroutineScope()

    val currentUser by locator.session.currentUser
        .collectAsStateWithLifecycle(initialValue = null)
    val users by locator.session.observeUsers()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val canManageUsers by locator.session.canManageUsers
        .collectAsStateWithLifecycle(initialValue = true)

    var showDialog by remember { mutableStateOf(false) }

    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardTitle(AppIcons.Person, "المستخدمون والأدوار")
            Text(
                "الطالب: بث وتسجيل وتحضير — المعلم: + الجدولة وإدارة التسجيلات — المشرف: + إدارة المستخدمين",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // صفوف المستخدمين: النقر يبدّل المستخدم الحالي
            users.forEach { user ->
                val isCurrent = user.id == currentUser?.id
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    com.saidjte.schoolradio.core.ui.AppIcons.Person.let {
                        Icon(
                            it, contentDescription = null,
                            tint = if (isCurrent) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "${user.name} — ${user.roleEnum().labelAr}",
                        Modifier.weight(1f).padding(start = 8.dp),
                        style = if (isCurrent) MaterialTheme.typography.titleSmall
                        else MaterialTheme.typography.bodyMedium
                    )
                    if (isCurrent) {
                        Text(
                            "الحالي",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        TextButton(onClick = {
                            scope.launch {
                                locator.session.switchUser(user.id)
                                snackbar.showSnackbar(
                                    "الحساب الحالي: ${user.name} (${user.roleEnum().labelAr})"
                                )
                            }
                        }) { Text("تبديل") }
                        if (canManageUsers) {
                            TextButton(onClick = {
                                scope.launch {
                                    locator.session.deleteUser(user)
                                        .onSuccess { snackbar.showSnackbar("حُذف المستخدم") }
                                        .onFailure { snackbar.showSnackbar(it.message ?: "تعذر الحذف") }
                                }
                            }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }

            if (canManageUsers) {
                Button(onClick = { showDialog = true }) { Text("إضافة مستخدم") }
            } else {
                Text(
                    "إدارة المستخدمين متاحة للمشرف فقط — الحساب الحالي: " +
                        "${currentUser?.name ?: "—"} (${currentUser?.roleEnum()?.labelAr ?: "—"})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showDialog) {
        AddUserDialog(
            onDismiss = { showDialog = false },
            onConfirm = { name, role ->
                scope.launch {
                    locator.session.addUser(name, role)
                        .onSuccess {
                            snackbar.showSnackbar("أُضيف ${name.trim()} (${role.labelAr})")
                            showDialog = false
                        }
                        .onFailure { snackbar.showSnackbar(it.message ?: "تعذر الإضافة") }
                }
            }
        )
    }
}

/** حوار إضافة مستخدم: اسم + اختيار الدور (طلاب/معلمون/مشرف) */
@Composable
private fun AddUserDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, com.saidjte.schoolradio.data.local.entity.UserRole) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var role by remember {
        mutableStateOf(com.saidjte.schoolradio.data.local.entity.UserRole.STUDENT)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة مستخدم") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("الاسم") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    com.saidjte.schoolradio.data.local.entity.UserRole.entries.forEach { r ->
                        FilterChip(
                            selected = role == r,
                            onClick = { role = r },
                            label = { Text(r.labelAr) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, role) },
                enabled = name.isNotBlank()
            ) { Text("إضافة") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

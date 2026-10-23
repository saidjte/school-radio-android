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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.saidjte.schoolradio.data.backup.BackupRepository
import com.saidjte.schoolradio.data.settings.AppSettings
import com.saidjte.schoolradio.di.ServiceLocator
import kotlinx.coroutines.launch

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
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("المظهر", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "حسب النظام", 1 to "فاتح", 2 to "داكن").forEach { (mode, label) ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { scope.launch { locator.settings.setThemeMode(mode) } },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }

        // ===== الصوت والخفض التلقائي =====
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("الصوت والبث المباشر", style = MaterialTheme.typography.titleMedium)

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
            }
        }

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

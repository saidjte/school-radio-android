package com.saidjte.schoolradio.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** نموذج الإعدادات المستخدمة في الواجهة */
data class AppSettings(
    val schoolName: String = "مدرستي",
    val themeMode: Int = 0,               // 0=حسب النظام، 1=فاتح، 2=داكن
    val defaultVolume: Float = 0.9f,      // مستوى الصوت الافتراضي 0..1
    val duckVolume: Float = 0.25f,        // مستوى خفض الموسيقى أثناء الإعلان المباشر
    val duckEnabled: Boolean = true,      // تفعيل الخفض التلقائي
    val notificationsEnabled: Boolean = true,
    val startOfBroadcast: Int = 8 * 60,   // بداية يوم الإذاعة بالدقائق (تسبيك الحسابات)
    val lastBackupUri: String? = null
)

/**
 * إعدادات التطبيق عبر DataStore Preferences — تخزين محلي بسيط ومتفاعل (Flow).
 * بيانات المكتبة والجداول في Room أما الإعدادات الخفيفة فهنا.
 */
class SettingsStore(private val context: Context) {

    private object Keys {
        val SCHOOL_NAME = stringPreferencesKey("school_name")
        val THEME_MODE = intPreferencesKey("theme_mode")
        val DEFAULT_VOLUME = floatPreferencesKey("default_volume")
        val DUCK_VOLUME = floatPreferencesKey("duck_volume")
        val DUCK_ENABLED = booleanPreferencesKey("duck_enabled")
        val NOTIFICATIONS = booleanPreferencesKey("notifications")
        val START_OF_DAY = intPreferencesKey("start_of_broadcast")
        val LAST_BACKUP = stringPreferencesKey("last_backup_uri")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            schoolName = p[Keys.SCHOOL_NAME] ?: "مدرستي",
            themeMode = p[Keys.THEME_MODE] ?: 0,
            defaultVolume = p[Keys.DEFAULT_VOLUME] ?: 0.9f,
            duckVolume = p[Keys.DUCK_VOLUME] ?: 0.25f,
            duckEnabled = p[Keys.DUCK_ENABLED] ?: true,
            notificationsEnabled = p[Keys.NOTIFICATIONS] ?: true,
            startOfBroadcast = p[Keys.START_OF_DAY] ?: (8 * 60),
            lastBackupUri = p[Keys.LAST_BACKUP]
        )
    }

    suspend fun setSchoolName(v: String) = context.dataStore.edit { it[Keys.SCHOOL_NAME] = v }
    suspend fun setThemeMode(v: Int) = context.dataStore.edit { it[Keys.THEME_MODE] = v }
    suspend fun setDefaultVolume(v: Float) = context.dataStore.edit { it[Keys.DEFAULT_VOLUME] = v }
    suspend fun setDuckVolume(v: Float) = context.dataStore.edit { it[Keys.DUCK_VOLUME] = v }
    suspend fun setDuckEnabled(v: Boolean) = context.dataStore.edit { it[Keys.DUCK_ENABLED] = v }
    suspend fun setNotificationsEnabled(v: Boolean) = context.dataStore.edit { it[Keys.NOTIFICATIONS] = v }
    suspend fun setLastBackupUri(v: String?) = context.dataStore.edit {
        if (v == null) it.remove(Keys.LAST_BACKUP) else it[Keys.LAST_BACKUP] = v
    }
}

package com.saidjte.schoolradio.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
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
    val lastBackupUri: String? = null,
    val currentUserId: Long? = null,       // المستخدم الحالي (الأدوار v0.3.0)
    // ===== v0.9.0: الاستوديو الاحترافي (ميكسر + معادل صوت) =====
    val eqEnabled: Boolean = true,         // معادل الصوت مفعّل (شرائح مسطحة افتراضياً = صوت شفاف)
    val eqPresetId: Int = -1,              // -1 = ضبط مخصص بالشرائح
    val eqGains: String = "",              // كسب الشرائح بالديسيبل مفصولاً بـ | (مثل "0.0|1.5|-2.0")
    val bassStrength: Float = 0f,          // تعزيز الباص 0..1
    val virtStrength: Float = 0f,          // المؤثر المحيطي 0..1
    val micGain: Float = 1.0f              // كسب الميكروفون 0..2 (فيدر الميكسر)
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
        val CURRENT_USER = longPreferencesKey("current_user_id")
        // v0.9.0
        val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val EQ_PRESET = intPreferencesKey("eq_preset_id")
        val EQ_GAINS = stringPreferencesKey("eq_gains")
        val BASS_STRENGTH = floatPreferencesKey("bass_strength")
        val VIRT_STRENGTH = floatPreferencesKey("virt_strength")
        val MIC_GAIN = floatPreferencesKey("mic_gain")
    }

    /** معرف المستخدم الحالي (لتوليفه مع جدول users في SessionRepository) */
    val currentUserId: Flow<Long?> = context.dataStore.data.map { it[Keys.CURRENT_USER] }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            schoolName = p[Keys.SCHOOL_NAME] ?: "مدرستي",
            themeMode = p[Keys.THEME_MODE] ?: 0,
            defaultVolume = p[Keys.DEFAULT_VOLUME] ?: 0.9f,
            duckVolume = p[Keys.DUCK_VOLUME] ?: 0.25f,
            duckEnabled = p[Keys.DUCK_ENABLED] ?: true,
            notificationsEnabled = p[Keys.NOTIFICATIONS] ?: true,
            startOfBroadcast = p[Keys.START_OF_DAY] ?: (8 * 60),
            lastBackupUri = p[Keys.LAST_BACKUP],
            // v0.9.0
            eqEnabled = p[Keys.EQ_ENABLED] ?: true,
            eqPresetId = p[Keys.EQ_PRESET] ?: -1,
            eqGains = p[Keys.EQ_GAINS] ?: "",
            bassStrength = p[Keys.BASS_STRENGTH] ?: 0f,
            virtStrength = p[Keys.VIRT_STRENGTH] ?: 0f,
            micGain = p[Keys.MIC_GAIN] ?: 1.0f
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

    suspend fun setCurrentUserId(v: Long?) = context.dataStore.edit {
        if (v == null) it.remove(Keys.CURRENT_USER) else it[Keys.CURRENT_USER] = v
    }

    // ===== v0.9.0: الاستوديو الاحترافي =====
    suspend fun setEqEnabled(v: Boolean) = context.dataStore.edit { it[Keys.EQ_ENABLED] = v }

    suspend fun setEqConfig(presetId: Int, gains: String) = context.dataStore.edit {
        it[Keys.EQ_PRESET] = presetId
        it[Keys.EQ_GAINS] = gains
    }

    suspend fun setBassStrength(v: Float) = context.dataStore.edit { it[Keys.BASS_STRENGTH] = v }
    suspend fun setVirtStrength(v: Float) = context.dataStore.edit { it[Keys.VIRT_STRENGTH] = v }
    suspend fun setMicGain(v: Float) = context.dataStore.edit { it[Keys.MIC_GAIN] = v.coerceIn(0f, 2f) }
}

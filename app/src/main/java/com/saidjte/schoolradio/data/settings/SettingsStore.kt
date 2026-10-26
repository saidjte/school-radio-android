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
    val micGain: Float = 1.0f,             // كسب الميكروفون 0..2 (فيدر الميكسر)
    // v0.10.0: نمط البث — "HOLD" استمرار للبث / "TOGGLE" ضغطة للبدء والإنهاء
    val micMode: String = "TOGGLE",
    // ===== v0.11.0: معالجة الميكروفون الاحترافية (سلسلة تنظيف صوت المذيع) =====
    val micAgc: Boolean = true,            // توحيد الكسب تلقائياً (AGC) — صوت ثابت مهما بعد المذيع
    val micNoiseGate: Boolean = true,      // حجز الضجيج الخلفي (NoiseSuppressor)
    val micEchoCancel: Boolean = true,     // إلغاء الصدى (AEC) ضد صفير التغذية الراجعة
    // ===== v0.11.0: حائط الجينغل (Cart Wall) =====
    val cartVolume: Float = 0.85f,         // فيدَر قناة الجينغل 0..1
    val cartLayoutJson: String = "",       // تخطيط الرقائق JSON (راجع CartWallStore)
    // ===== v0.15.0: الميكسر الاحترافي — قنوات مستقلة + ماستر =====
    val masterVolume: Float = 1.0f,        // فيدَر الماستر (يضرب القنوات الموسيقية) 0..1
    val previewVolume: Float = 0.9f,       // (v0.41: محفوظ للتوافق مع النسخ السابقة فقط — قناة المعاينة أُزيلت، التسجيلات عبر قناة الموسيقى)
    // ===== v0.11.0: تفضيلات قارئ السكربت (Teleprompter) =====
    val prompterSpeed: Float = 55f,        // سرعة التمرير dp/ثانية
    val prompterFont: Float = 30f,         // حجم خط النص sp
    val prompterMirror: Boolean = false,   // وضع المرآة
    val prompterGuide: Boolean = true,     // الخط الإرشادي لموضع القراءة
    // ===== v0.18.0: قص الفراغ الأول من المقاطع (أو تجاهله) =====
    val trimSilence: Boolean = true,       // true = قفز فوق الصمت الأول، false = تشغيل الملف كما هو
    // ===== v0.36.0: ترميز تسجيلات الميكروفون =====
    val recordingHd: Boolean = true        // true = Opus HD ‎.ogg (أندرويد 10+)، false = AAC ‎.m4a دائماً
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
        // v0.10.0: نمط تشغيل الميكروفون في البث المباشر
        val MIC_MODE = stringPreferencesKey("mic_mode")
        // v0.11.0: معالجة الميكروفون + الجينغل + تفضيلات السكربت
        val MIC_AGC = booleanPreferencesKey("mic_agc")
        val MIC_NOISE_GATE = booleanPreferencesKey("mic_noise_gate")
        val MIC_ECHO_CANCEL = booleanPreferencesKey("mic_echo_cancel")
        val CART_VOLUME = floatPreferencesKey("cart_volume")
        val CART_LAYOUT = stringPreferencesKey("cart_layout_json")
        // v0.15.0: الميكسر الاحترافي
        val MASTER_VOLUME = floatPreferencesKey("master_volume")
        val PREVIEW_VOLUME = floatPreferencesKey("preview_volume")
        val PROMPTER_SPEED = floatPreferencesKey("prompter_speed")
        val PROMPTER_FONT = floatPreferencesKey("prompter_font")
        val PROMPTER_MIRROR = booleanPreferencesKey("prompter_mirror")
        val PROMPTER_GUIDE = booleanPreferencesKey("prompter_guide")
        // v0.18.0: قص الفراغ الأول
        val TRIM_SILENCE = booleanPreferencesKey("trim_silence")
        // v0.36.0: ترميز تسجيلات الميكروفون (Opus HD / AAC)
        val RECORDING_HD = booleanPreferencesKey("recording_hd")
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
            micGain = p[Keys.MIC_GAIN] ?: 1.0f,
            micMode = p[Keys.MIC_MODE] ?: "TOGGLE",
            // v0.11.0
            micAgc = p[Keys.MIC_AGC] ?: true,
            micNoiseGate = p[Keys.MIC_NOISE_GATE] ?: true,
            micEchoCancel = p[Keys.MIC_ECHO_CANCEL] ?: true,
            cartVolume = p[Keys.CART_VOLUME] ?: 0.85f,
            cartLayoutJson = p[Keys.CART_LAYOUT] ?: "",
            masterVolume = p[Keys.MASTER_VOLUME] ?: 1.0f,
            previewVolume = p[Keys.PREVIEW_VOLUME] ?: 0.9f,
            prompterSpeed = p[Keys.PROMPTER_SPEED] ?: 55f,
            prompterFont = p[Keys.PROMPTER_FONT] ?: 30f,
            prompterMirror = p[Keys.PROMPTER_MIRROR] ?: false,
            prompterGuide = p[Keys.PROMPTER_GUIDE] ?: true,
            trimSilence = p[Keys.TRIM_SILENCE] ?: true,
            recordingHd = p[Keys.RECORDING_HD] ?: true
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

    /** v0.10.0: حفظ نمط البث المختار (HOLD/TOGGLE) */
    suspend fun setMicMode(v: String) = context.dataStore.edit { it[Keys.MIC_MODE] = v }

    // ===== v0.11.0: معالجة الميكروفون =====
    suspend fun setMicAgc(v: Boolean) = context.dataStore.edit { it[Keys.MIC_AGC] = v }
    suspend fun setMicNoiseGate(v: Boolean) = context.dataStore.edit { it[Keys.MIC_NOISE_GATE] = v }
    suspend fun setMicEchoCancel(v: Boolean) = context.dataStore.edit { it[Keys.MIC_ECHO_CANCEL] = v }

    // ===== v0.11.0: حائط الجينغل =====
    suspend fun setCartVolume(v: Float) = context.dataStore.edit { it[Keys.CART_VOLUME] = v.coerceIn(0f, 1f) }
    suspend fun setCartLayoutJson(v: String) = context.dataStore.edit { it[Keys.CART_LAYOUT] = v }

    // ===== v0.15.0: الميكسر الاحترافي =====
    suspend fun setMasterVolume(v: Float) = context.dataStore.edit { it[Keys.MASTER_VOLUME] = v.coerceIn(0f, 1f) }
    suspend fun setPreviewVolume(v: Float) = context.dataStore.edit { it[Keys.PREVIEW_VOLUME] = v.coerceIn(0f, 1f) }

    // ===== v0.11.0: تفضيلات قارئ السكربت =====
    suspend fun setPrompterSpeed(v: Float) =
        context.dataStore.edit { it[Keys.PROMPTER_SPEED] = v.coerceIn(15f, 150f) }

    suspend fun setPrompterFont(v: Float) =
        context.dataStore.edit { it[Keys.PROMPTER_FONT] = v.coerceIn(18f, 64f) }

    suspend fun setPrompterMirror(v: Boolean) = context.dataStore.edit { it[Keys.PROMPTER_MIRROR] = v }

    suspend fun setPrompterGuide(v: Boolean) = context.dataStore.edit { it[Keys.PROMPTER_GUIDE] = v }

    // ===== v0.18.0: قص الفراغ الأول من المقاطع =====
    suspend fun setTrimSilence(v: Boolean) = context.dataStore.edit { it[Keys.TRIM_SILENCE] = v }

    // ===== v0.36.0: ترميز تسجيلات الميكروفون (Opus HD) =====
    suspend fun setRecordingHd(v: Boolean) = context.dataStore.edit { it[Keys.RECORDING_HD] = v }
}

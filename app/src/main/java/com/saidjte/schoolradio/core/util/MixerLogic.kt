package com.saidjte.schoolradio.core.util

import kotlin.math.roundToInt

/**
 * v0.15.0 — منطق الميكسر الاحترافي (صافٍ بلا أندرويد — قابل للاختبار وحدوياً).
 *
 * نمط لوحات المزج الإذاعية:
 *  - كل قناة لها فيدَر مستقل (0..1 أو 0..2 للميك) وزر كتم (M).
 *  - القناة الفعّالة المسموعة = الفيدَر × الماستر؛ الكتم يعامل كصفر مؤقت
 *    (يُحفظ الفيدَر تحته فتعود القيمة لحظة رفع الكتم — سلوك لوحات الاستوديو).
 *  - الماستر يمس القنوات الموسيقية (الموسيقى/الجينل/المعاينة) — قناة الميك
 *    لها كسبها المستقل لأنها خط مباشر للمكبرات.
 *  - «مشاهد» جاهزة تنقل الفيدرات كلها بضغطة كما في البرمجيات الاحترافية
 *    (Shows/Scenes) — للإعلان، للموسيقى الخالصة، وللبرنامج الكامل.
 */
object MixerLogic {

    /** القنوات الأربع + الماستر — معرّفات ثابتة للواجهة والحفظ */
    enum class Channel(val labelAr: String) {
        MIC("الميكروفون"),
        MUSIC("الموسيقى"),
        BREAKS("الجينل"),
        PREVIEW("المعاينة"),
        MASTER("الماستر")
    }

    /** لقطة قنوات الميكسر — كل الفيدرات في كائن واحد (تُستخدم في المشاهد) */
    data class ChannelSet(
        val mic: Float = 1.0f,        // 0..2
        val music: Float = 0.9f,      // 0..1
        val breaks: Float = 0.85f,    // 0..1
        val preview: Float = 0.9f,    // 0..1
        val master: Float = 1.0f,     // 0..1
        val duckEnabled: Boolean = true,
        val duckVolume: Float = 0.25f // 0..0.6
    )

    /** مشاهد جاهزة — أسماء عربية وأزمنة واقعية من الممارسة الإذاعية المدرسية */
    enum class Scene(val labelAr: String, val hintAr: String, val set: ChannelSet) {
        FULL(
            "برنامج كامل",
            "كل القنوات بمستوياتها المتوازنة الافتراضية",
            ChannelSet()
        ),
        ANNOUNCEMENT(
            "إعلان مباشر",
            "رفع كسب الميك وخفض عميق للموسيقى أثناء الحديث",
            ChannelSet(mic = 1.3f, music = 0.9f, master = 1f, duckEnabled = true, duckVolume = 0.15f)
        ),
        MUSIC_ONLY(
            "موسيقى فقط",
            "كتم الميك — تشغيل الفقرات المسجلة بوضوح كامل",
            ChannelSet(mic = 0f, music = 1f, master = 1f, duckEnabled = false)
        ),
        BREAK_MODE(
            "فاصل وجينل",
            "رفع قناة الجينل للفواصل الموسيقية بين الفقرات",
            ChannelSet(mic = 1f, music = 0.7f, breaks = 1f, master = 1f)
        )
    }

    /**
     * المستوى الفعّال المسموع لقناة: فيدَر القناة × الماستر، والكتم = صفر.
     * قناة الميك لا يضربها الماستر (خط مباشر) — كتمها وحده يصمّتها.
     */
    fun effectiveVolume(
        channel: Channel,
        fader: Float,
        master: Float,
        muted: Boolean
    ): Float = when {
        muted -> 0f
        channel == Channel.MIC || channel == Channel.MASTER -> fader.coerceIn(0f, 2f)
        else -> (fader.coerceIn(0f, 1f)) * master.coerceIn(0f, 1f)
    }

    /** هل القناة تخضع للماستر؟ (الميك والماستر نفسه لا يخضعان) */
    fun obeysMaster(channel: Channel): Boolean =
        channel != Channel.MIC && channel != Channel.MASTER

    /** نص نسبة الفيدَر بالأرقام المغربية — الميك حتى 200٪ والكتم صريح */
    fun faderText(channel: Channel, fader: Float): String = when {
        fader <= 0.001f -> "مكتوم"
        channel == Channel.MIC -> "${(fader * 100).roundToInt()}٪"
        else -> "${(fader * 100).roundToInt()}٪"
    }

    /** تنقية قيمة السحب — كل القنوات 0..1 عدا الميك 0..2 */
    fun clampFader(channel: Channel, raw: Float): Float = when (channel) {
        Channel.MIC -> raw.coerceIn(0f, 2f)
        else -> raw.coerceIn(0f, 1f)
    }
}

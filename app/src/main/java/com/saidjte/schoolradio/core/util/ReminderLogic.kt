package com.saidjte.schoolradio.core.util

/**
 * v0.28.0 — منطق تنبيه الفقرات المؤرخة (بلا تبعيات أندرويد) — قابل لاختبارات الوحدات.
 *
 * خلفية الطلب: الفقرة ذات الموعد الواحد (حفل تخرّج، احتفال وطني، بث خاص) قد تُنسى
 * وسط الزحام اليومي — المدرسة تحتاج إشعاراً واعياً **قبل** انطلاقها (يوم مثلاً)
 * حتى يُحضّر الاستوديو الميكروفونات والسكربت بوقت كافٍ.
 *
 * التمثيل: نفس مفاتيح النظام — دقائق قبل البدء (60 = ساعة، 1440 = يوم)،
 * والفقرات الأسبوعية المتكررة لا تنبّه (إيقاعها اليومي معروف) — للمواعيد الفريدة فقط.
 */
object ReminderLogic {

    /** «بدون تنبيه» — تُخزّن null أو 0 */
    const val OFF = 0

    /** التنبيه قبل ساعة واحدة */
    const val HOUR = 60

    /** التنبيه قبل يوم كامل */
    const val DAY = 1440

    /** خيارات المحرر بالترتيب (دقائق قبل الانطلاق) */
    val OPTIONS = listOf(OFF, HOUR, DAY)

    /** التسمية العربية القصيرة: «بدون تنبيه» / «قبل ساعة» / «قبل يوم» / «قبل N دقيقة» */
    fun labelAr(minutesBefore: Int?): String = when (minutesBefore) {
        null, OFF -> "بدون تنبيه"
        HOUR -> "قبل ساعة"
        DAY -> "قبل يوم"
        else -> "قبل " + TimeEditLogic.pluralAr(minutesBefore, "دقيقة", "دقيقتين", "دقائق")
    }

    /** شارة البطاقة: «تنبيه قبل يوم» — فارغة للخيار المعطل */
    fun badgeAr(minutesBefore: Int?): String? =
        if (minutesBefore == null || minutesBefore <= OFF) null
        else "تنبيه " + labelAr(minutesBefore)

    /** لحظة التنبيه = انطلاق الفقرة ناقص المدة قبلها (ملي ثانية) */
    fun reminderAt(eventTriggerMillis: Long, minutesBefore: Int): Long =
        eventTriggerMillis - minutesBefore * 60_000L

    /**
     * هل يُضبط منبه تنبيه لهذه الفقرة الآن؟
     * شروطه: فقرة مؤرخة (موعد واحد) + تنبيه مفعّل + انطلاق قادم معلوم
     * + لحظة التنبيه نفسها ما زالت في المستقبل (تنبيه ماضٍ لا فائدة منه).
     */
    fun shouldArm(
        isDated: Boolean,
        minutesBefore: Int?,
        eventTriggerMillis: Long?,
        nowMillis: Long
    ): Boolean {
        if (!isDated) return false
        if (minutesBefore == null || minutesBefore <= OFF) return false
        val trigger = eventTriggerMillis ?: return false
        return reminderAt(trigger, minutesBefore) > nowMillis
    }

    /**
     * جسم الإشعار العربي الجاهز للعرض:
     * «فقرة «حفل التخرج» — غداً الساعة 9:45 صباحاً (الخميس 5 نوفمبر 2026)»
     * الصيغة 12 ساعة من TimeEditLogic.format12hAr — أرقام لاتينية جدولية.
     */
    fun notificationTextAr(
        title: String,
        relativeLabel: String,
        dateAr: String,
        startMinutes: Int
    ): String =
        "فقرة «$title» — $relativeLabel الساعة ${TimeEditLogic.format12hAr(startMinutes)} ($dateAr)"

    /** عنوان الإشعار الموحّد */
    const val NOTIFICATION_TITLE = "تذكير بفقرة الإذاعة المدرسية"
}

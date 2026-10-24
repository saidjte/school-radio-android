package com.saidjte.schoolradio.core.util

/**
 * منطق الأتمتة الإذاعية الصرف (v0.13.0 — بلا تبعيات أندرويد، قابل لاختبارات الوحدات).
 *
 * لماذا خدمة حارسة إضافةً إلى المنبهات الدقيقة؟
 * - المنبهات الدقيقة (AlarmManager) أساس الإطلاق في الثانية المحددة، لكن أجهزة
 *   بعض المصنّعين (Tecno/Infinix/Xiaomi…) تبتلع المنبهات لتطبيقات غير محمية،
 *   وتقضي على العملية كاملة. الخدمة الأمامية الحارسة تعني أن العملية حية دائماً
 *   فتُطلق الفقرات في مواعيدها حتى لو انقلبت المنبهات — نمط أنظمة الأتمتة الإذاعية
 *   (الجهاز مخصص للبث فتبقى الخدمة تعمل دائماً بإشعار هادئ).
 */
object AutomationLogic {

    /** نافذة السماح: فقرة كان موعدها قبل الآن بهذا القدر فأقل تُطلق الآن (تعويض التأخير) */
    const val GRACE_MINUTES = 2

    /** فاصل نبض الخدمة الحارسة بالمللي ثانية (20 ثانية — دقة كافية لدقائق الإذاعة) */
    const val TICK_MILLIS = 20_000L

    /** كل فترة تعيد الخدمة ضبط منبهات النظام احتياطاً (ساعة — تضمن حداثتها) */
    const val REARM_PERIOD_MILLIS = 60L * 60_000

    /** صورة مبسطة لحدث مجدول تمرر للمنطق */
    data class EventTiming(
        val eventId: Long,
        val title: String,
        val startMinutes: Int,
        val weekDaysMask: Int
    )

    /**
     * الأحداث المستحقة للإطلاق الآن: اليوم يطابق أحد أيام الحدث، والدقائق الحالية
     * داخل النافذة [startMinutes, startMinutes + graceMinutes)، ولم يُنفَّذ اليوم.
     * مرتبة زمنياً (الأبعد تأخيراً أولاً لا يهم — منع التكرار يتكفل بقية المسار).
     */
    suspend fun dueEventIds(
        events: List<EventTiming>,
        nowMillis: Long,
        executedToday: suspend (Long) -> Boolean,
        graceMinutes: Int = GRACE_MINUTES
    ): List<Long> {
        val dayBit = ScheduleLogic.dayBitOf(nowMillis)
        val minutesNow = ScheduleLogic.minutesOfDay(nowMillis)
        return events
            .filter { (it.weekDaysMask and dayBit) != 0 }
            .filter { minutesNow >= it.startMinutes && minutesNow < it.startMinutes + graceMinutes }
            .filter { !executedToday(it.eventId) }
            .sortedBy { it.startMinutes }
            .map { it.eventId }
    }

    /**
     * الفقرة القادمة اليوم (لإشعار الخدمة الحارسة): أول حدث مفعّل وقتُه بعد الآن
     * ولم يُنفَّذ اليوم. يرجع null إن لم يبق شيء اليوم.
     */
    suspend fun nextUpToday(
        events: List<EventTiming>,
        nowMillis: Long,
        executedToday: suspend (Long) -> Boolean
    ): EventTiming? {
        val dayBit = ScheduleLogic.dayBitOf(nowMillis)
        val minutesNow = ScheduleLogic.minutesOfDay(nowMillis)
        return events
            .filter { (it.weekDaysMask and dayBit) != 0 }
            .filter { it.startMinutes > minutesNow }
            .filter { !executedToday(it.eventId) }
            .minByOrNull { it.startMinutes }
    }

    /** نص الإشعار: «القادمة: طابور الصباح — بعد 12 د» أو «لا فقرات قادمة اليوم» */
    fun notificationText(next: EventTiming?, minutesNow: Int): String {
        if (next == null) return "الأتمتة نشطة • لا فقرات قادمة اليوم"
        val remaining = next.startMinutes - minutesNow
        val when_ = when {
            remaining <= 0 -> "الآن"
            remaining == 1 -> "بعد دقيقة"
            else -> "بعد $remaining دقيقة"
        }
        return "القادمة: ${next.title} — $when_ (${ScheduleLogic.formatMinutes(next.startMinutes)})"
    }
}

package com.saidjte.schoolradio.core.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * منطق الجدولة الصرف (بلا تبعيات أندرويد) — قابل لاختبارات الوحدات.
 * قواعد التوقيت: نخزّن «الدقائق من منتصف الليل» وأيام الأسبوع بـ ISO (الاثنين=1..الأحد=7).
 */
object ScheduleLogic {

    /** بت اليوم في weekDaysMask ليوم ISO معين: الاثنين=1<<0 */
    fun dayBitOf(dayOfWeekIso: Int): Int = 1 shl (dayOfWeekIso - 1)

    fun dayBitOf(millis: Long): Int = dayBitOf(isoDayOfWeek(millis))

    /** يوم الأسبوع بمعيار ISO من وقت معين */
    fun isoDayOfWeek(millis: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            else -> 7
        }
    }

    /** الدقائق المنقضية من منتصف الليل */
    fun minutesOfDay(millis: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    /** مفتاح اليوم yyyy-MM-dd — أساس منع تكرار التشغيل */
    fun dateKey(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))

    /** التنسيق العربي للعرض: 585 → 09:45 */
    fun formatMinutes(minutes: Int): String =
        String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60)

    /** تحويل نص وقت HH:mm إلى دقائق منتصف الليل (يرجع null عند خطأ الإدخال) */
    fun parseHhMm(text: String): Int? {
        val m = Regex("^(\\d{1,2}):(\\d{2})$").find(text.trim()) ?: return null
        val h = m.groupValues[1].toIntOrNull() ?: return null
        val min = m.groupValues[2].toIntOrNull() ?: return null
        if (h !in 0..23 || min !in 0..59) return null
        return h * 60 + min
    }

    /** أقرب موعد إطلاق مستقبلي لحدث (ملي ثانية) — يراعي أيام الأسبوع */
    fun nextTriggerMillis(event: NextTriggerInput, nowMillis: Long): Long? {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        // جرّب اليوم ثم الأيام الستة التالية
        for (offsetDays in 0..7) {
            val candidate = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offsetDays) }
            val isoDay = when (candidate.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> 1; Calendar.TUESDAY -> 2; Calendar.WEDNESDAY -> 3
                Calendar.THURSDAY -> 4; Calendar.FRIDAY -> 5; Calendar.SATURDAY -> 6
                else -> 7
            }
            val bit = 1 shl (isoDay - 1)
            if (event.weekDaysMask and bit == 0) continue
            candidate.set(Calendar.HOUR_OF_DAY, event.startMinutes / 60)
            candidate.set(Calendar.MINUTE, event.startMinutes % 60)
            val t = candidate.timeInMillis
            if (t > nowMillis) return t
        }
        return null
    }

    /** صورة مبسطة لحدث الجدولة تُمرر للمنطق الصرف (فصل عن الكيان) */
    data class NextTriggerInput(val startMinutes: Int, val weekDaysMask: Int)

    /**
     * كشف تعارض المواعيد: حدثان في اليوم نفسه يتقاطعان زمنياً.
     * (نفترض مدة تقديرية لكل فقرة: 15 دقيقة افتراضياً)
     */
    fun hasOverlap(a: NextTriggerInput, b: NextTriggerInput, assumedDurationMin: Int = 15): Boolean {
        val commonDay = (a.weekDaysMask and b.weekDaysMask) != 0
        if (!commonDay) return false
        val aEnd = a.startMinutes + assumedDurationMin
        val bEnd = b.startMinutes + assumedDurationMin
        return a.startMinutes < bEnd && b.startMinutes < aEnd
    }

    /** عارض مدة بالميلي ثانية: 93_000 → 1:33 */
    fun formatDuration(ms: Long): String {
        if (ms <= 0) return "0:00"
        val s = ms / 1000
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60)
    }
}

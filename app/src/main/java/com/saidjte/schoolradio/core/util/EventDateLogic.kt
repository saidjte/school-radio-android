package com.saidjte.schoolradio.core.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * v0.27.0 — منطق الفقرات ذات الموعد الواحد (بلا تبعيات أندرويد) — قابل لاختبارات الوحدات.
 *
 * خلفية الطلب: كل الفقرات كانت أسبوعية متكررة إجباراً (قناع أيام) — لا سبيل لجدولة
 * فقرة مناسبة (تخرّج، احتفال وطني، بث لحفل) تُطلق مرة واحدة في تاريخ محدد.
 *
 * التمثيل: نفس مفاتيح ScheduleLogic — yyyy-MM-dd بتوقيت الجهاز المحلي،
 * والدقائق من منتصف الليل لوقت البدء.
 */
object EventDateLogic {

    private val KEY_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /** مفتاح تاريخ yyyy-MM-dd من مفتاح تاريخ + عدد أيام (يقفز عبر حدود الشهور والسنين) */
    fun plusDays(dateKey: String, days: Int): String? {
        val midnight = millisOfDateKey(dateKey) ?: return null
        val cal = Calendar.getInstance().apply {
            timeInMillis = midnight
            add(Calendar.DAY_OF_YEAR, days)
        }
        return KEY_FORMAT.format(Date(cal.timeInMillis))
    }

    /** منتصف ليل التاريخ المحلي بالميلي ثانية — null لمفتاح غير صالح */
    fun millisOfDateKey(dateKey: String): Long? {
        val m = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$").find(dateKey.trim()) ?: return null
        val y = m.groupValues[1].toIntOrNull() ?: return null
        val mo = m.groupValues[2].toIntOrNull() ?: return null
        val d = m.groupValues[3].toIntOrNull() ?: return null
        if (mo !in 1..12 || d !in 1..31) return null
        return runCatching {
            Calendar.getInstance().apply {
                clear()
                set(y, mo - 1, d, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }.getOrNull()
    }

    /**
     * تحويل selectedDateMillis من منتقي التاريخ المادي (DatePicker) إلى مفتاح تاريخ.
     * المنتقي يعيد منتصف ليل UTC — لا نستخدم التوقيت المحلي هنا وإلا انزلق التاريخ يوماً.
     */
    fun dateKeyFromUtcPickerMillis(utcMillis: Long): String {
        val cal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = utcMillis
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }

    /** عرض عربي: 2026-11-05 → «الخميس 5 نوفمبر 2026» بأرقام مغربية (AppFormat) */
    fun formatDateAr(dateKey: String): String? =
        millisOfDateKey(dateKey)?.let { AppFormat.dateDay(it) }

    /**
     * وصف نسبي عربي لمفتاح تاريخ مقابل الآن:
     * «اليوم»، «غداً»، «بعد يومين»، «بعد 3 أيام»، «بعد 11 يوماً»، والماضي «فائتة».
     * null فقط لمفتاح غير صالح.
     */
    fun relativeLabelAr(dateKey: String, nowMillis: Long): String? {
        val midnight = millisOfDateKey(dateKey) ?: return null
        val todayMidnight = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val days = ((midnight - todayMidnight) / (24 * 60 * 60 * 1000L)).toInt()
        return when {
            days == 0 -> "اليوم"
            days == 1 -> "غداً"
            days > 1 -> "بعد ${TimeEditLogic.pluralAr(days, "يوم", "يومين", "أيام")}"
            else -> "فائتة"
        }
    }

    /** هل التاريخ صالح وقادم (أو اليوم)؟ — لتمكين الحفظ في المحرر */
    fun isUpcoming(dateKey: String?, nowMillis: Long): Boolean {
        if (dateKey == null) return false
        val midnight = millisOfDateKey(dateKey) ?: return false
        val todayMidnight = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return midnight >= todayMidnight
    }
}

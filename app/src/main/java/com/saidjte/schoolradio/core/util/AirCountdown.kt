package com.saidjte.schoolradio.core.util

import java.util.Calendar
import java.util.Locale

/**
 * v0.14.0 — منطق المؤقت التنازلي للفقرة على شاشة العرض (منطق صافي قابل للاختبار).
 *
 * حالةان يغطيهما:
 *  1. أثناء تشغيل فقرة: متبقّي المقطع الحالي (من الموضع/المدة) ومتبقّي القائمة كاملة.
 *  2. بلا تشغيل: مدة الانتظار حتى الفقرة المجدولة القادمة اليوم.
 *
 * كل المخرجات بأرقام مغربية لاتينية (1234567890) عبر [AppFormat.westernDigits]
 * تطابقاً لقاعدة v0.6.0 المركزية — لا أرقام مشرقية في أي شاشة.
 */
object AirCountdown {

    /** ثواني اليوم (0..86399) عند لحظة [millis] — بتوقيت الجهاز المحلي */
    fun secondsOfDay(millis: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return cal.get(Calendar.HOUR_OF_DAY) * 3600L +
            cal.get(Calendar.MINUTE) * 60L +
            cal.get(Calendar.SECOND)
    }

    /**
     * ثواني الانتظار حتى فقرة تبدأ في [startMinutes] (دقائق من منتصف الليل).
     * إن حان موعدها أو فاتته تعيد 0 — للعرض فقط، قرار الانطلاق فعلياً بيد الأتمتة.
     */
    fun secondsUntil(startMinutes: Int, nowMillis: Long): Long =
        (startMinutes * 60L - secondsOfDay(nowMillis)).coerceAtLeast(0L)

    /** متبقّي مقطع جارٍ: المدة ناقص الموضع، مهذّبة لعدم السالب */
    fun remainingMs(positionMs: Long, durationMs: Long): Long =
        (durationMs - positionMs).coerceAtLeast(0L)

    /** «05:12» أو «1:02:07» بأرقام مغربية — دائماً Locale.US ثم توحيد الأرقام */
    fun mmss(totalSeconds: Long): String {
        val s = totalSeconds.coerceAtLeast(0L)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        val raw = if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
        else String.format(Locale.US, "%02d:%02d", m, sec)
        return AppFormat.westernDigits(raw)
    }

    /**
     * لون دلالي للمؤقت: عادي / قريب من النهاية (آخر دقيقة) / لحظات أخيرة (آخر 15 ثانية).
     * القيم فهارس ثابتة تحكمها الواجهة — لا ألوان هنا (فصل المنطق عن العرض).
     */
    enum class Urgency { NORMAL, SOON, FINAL }

    fun urgency(remainingMs: Long, playing: Boolean): Urgency = when {
        !playing || remainingMs <= 0L -> Urgency.NORMAL
        remainingMs <= 15_000L -> Urgency.FINAL
        remainingMs <= 60_000L -> Urgency.SOON
        else -> Urgency.NORMAL
    }
}

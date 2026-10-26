package com.saidjte.schoolradio.core.util

import java.util.Locale

/**
 * v0.26.0 — منطق تعديل وقت الفقرات المجدولة (بلا تبعيات أندرويد) — قابل لاختبارات الوحدات.
 *
 * خلفية الشكوى: كان وقت البدء حقل نص حر بصيغة HH:mm — أي إهمال في الكتابة
 * (نقطتان مفقودتان، دقيلة واحدة، أرقام عربية هندية) يُبطل الحفظ برسالة غامضة،
 * ولا سبيل لتقريب الموعد دقائق قليلة إلا إعادة كتابته كاملة.
 *
 * هذا المنطق يجعل التعديل عملية رقمية آمنة: القيمة int لا يمكن أن تكون غير صالحة،
 * والإزاحات السريعة تلبي الحالة الأشيع (تأجيل الطابور ربع ساعة)، والعرض
 * بصيغة 12 ساعة عربية يطابق طريقة تفكير المستخدم («9:45 صباحاً» لا «09:45»).
 */
object TimeEditLogic {

    const val MINUTES_PER_DAY = 24 * 60

    /** الرقائق السريعة للإزاحة بالدقائق (سالب = رجوع للخلف) */
    val QUICK_OFFSETS_MIN = listOf(-15, -5, 1, 5, 15)

    /** لف أي قيمة إلى نطاق 0..1439 — الإزاحة حول منتصف الليل لا تخرج عن اليوم */
    fun wrap(minutes: Int): Int =
        ((minutes % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY

    /** إزاحة الوقت بمقدار دقائق مع الالتفاف حول منتصف الليل */
    fun shifted(startMinutes: Int, deltaMinutes: Int): Int =
        wrap(startMinutes + deltaMinutes)

    /**
     * تقريب لأعلى إلى أقرب مضاعف خطوة — لوقت افتراضي ذكي عند إنشاء فقرة جديدة
     * (الآن مقرّباً للأقرب 5 دقائق بدل 09:45 الثابتة).
     * 1438 بخطوة 5 يلتف إلى 0 (بداية اليوم التالي عملياً).
     */
    fun roundUpToStep(minutes: Int, stepMin: Int): Int {
        if (stepMin <= 1) return wrap(minutes)
        val base = wrap(minutes)
        val rounded = ((base + stepMin - 1) / stepMin) * stepMin
        return wrap(rounded)
    }

    /** فترة اليوم بالعربية: 00:xx منتصف الليل، 12:xx ظهراً، حتى 11 صباحاً، بعدها مساءً */
    fun periodLabel(minutes: Int): String =
        when (wrap(minutes) / 60) {
            0 -> "منتصف الليل"
            12 -> "ظهراً"
            in 1..11 -> "صباحاً"
            else -> "مساءً"
        }

    /** صيغة 12 ساعة عربية: 585 → «9:45 صباحاً»، 0 → «12:00 منتصف الليل»، 720 → «12:00 ظهراً» */
    fun format12hAr(minutes: Int): String {
        val m = wrap(minutes)
        val h24 = m / 60
        val min = m % 60
        val h12 = when {
            h24 == 0 || h24 == 12 -> 12
            h24 > 12 -> h24 - 12
            else -> h24
        }
        return String.format(Locale.US, "%d:%02d %s", h12, min, periodLabel(m))
    }

    /**
     * وصف نسبي عربي من الآن:
     *  - المستقبل: «ينطلق خلال 14 ساعة و40 دقيقة من الآن»
     *  - المطابقة تماماً: «هذا الموعد هو الآن تماماً»
     *  - الماضي: null — لا نعلّق عليه لأن الإطلاق القادم يتوقف على الأيام المختارة.
     */
    fun relativeLabelAr(minutes: Int, nowMinutes: Int): String? {
        val delta = wrap(minutes) - wrap(nowMinutes)
        return when {
            delta == 0 -> "هذا الموعد هو الآن تماماً"
            delta > 0 -> "ينطلق خلال ${durationAr(delta)} من الآن"
            else -> null
        }
    }

    /** مدة بالدقائق إلى نص عربي: 62 → «ساعة واحدة ودقيقتين»، 120 → «ساعتين»، 0 → «أقل من دقيقة» */
    fun durationAr(deltaMin: Int): String {
        val h = deltaMin / 60
        val m = deltaMin % 60
        val parts = mutableListOf<String>()
        if (h > 0) parts.add(pluralAr(h, "ساعة", "ساعتين", "ساعات"))
        if (m > 0) parts.add(pluralAr(m, "دقيقة", "دقيقتين", "دقائق"))
        return if (parts.isEmpty()) "أقل من دقيقة" else parts.joinToString(" و")
    }

    /** جموع العربية: 1 مفرد مؤكد («ساعة واحدة»)، 2 مثنى، 3–10 جمع، 11+ مفرد */
    fun pluralAr(n: Int, one: String, two: String, few: String): String = when (n) {
        1 -> "$one واحدة"
        2 -> two
        in 3..10 -> "$n $few"
        else -> "$n $one"
    }
}

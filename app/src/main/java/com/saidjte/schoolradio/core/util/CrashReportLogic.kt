package com.saidjte.schoolradio.core.util

/**
 * v0.33.0 — منطق عرض تقارير الأعطال (منطق صافٍ بلا اعتماديات أندرويد).
 *
 * CrashGuard يكتب كل انهيار في ملف محلي منذ v0.15.0 — لكن بلا واجهة عرض
 * كان التقرير حبيس الجهاز. هذه الدوال الصافية تنقّي السجل وتلخّصه وتجهّزه
 * للمشاركة، ليتحول كل بلاغ «التطبيق ينغلق» إلى أثر مكتوب يُشخَّص بدقة.
 */
object CrashReportLogic {

    /** سقف معاينة معقول للحوار (والمشاركة تمر بسقف أكبر مستقلاً) */
    const val PREVIEW_MAX_CHARS = 4000

    /** هل يوجد تقرير يستحق العرض؟ */
    fun hasReport(text: String?): Boolean = !text.isNullOrBlank()

    /** عدد الأعطال الموثقة في السجل — عدّ أسطر فتح الأقسام (كل قسم يفتح بسطر =====) */
    fun crashCount(text: String?): Int =
        if (text.isNullOrBlank()) 0
        else Regex("(?m)^=====\\s").findAll(text).count()

    /**
     * نص المعاينة للعرض: الذيل الأحدث ضمن سقف المحارف
     * (الأعطال تُلحق بنهاية الملف فالأحدث هو الأخير — البداية قديمة).
     */
    fun preview(text: String?, maxChars: Int = PREVIEW_MAX_CHARS): String {
        if (text.isNullOrBlank()) return "لا توجد أعطال موثقة على هذا الجهاز"
        val t = text.trim()
        return if (t.length <= maxChars) t else "…" + t.takeLast(maxChars)
    }

    /** سطر الملخص تحت بند الإعدادات حسب عدد الأعطال */
    fun summaryLabel(text: String?): String = when (val n = crashCount(text)) {
        0 -> "لا أعطال موثقة"
        1 -> "عطل واحد موثق — اضغط لعرضه ومشاركته"
        else -> "$n أعطال موثقة — اضغط لعرضها ومشاركتها"
    }
}

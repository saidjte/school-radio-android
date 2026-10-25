package com.saidjte.schoolradio.core.util

/**
 * v0.19.0 — منطق القص اليدوي للمقاطع (منظّم صافٍ بلا أندرويد — قابل للاختبار).
 *
 * المبدأ: ميزة «قص الفراغ الأول» (v0.18.0) تقيس البداية آلياً، لكن المذيع
 * كثيراً ما يحتاج تحكماً بشرياً: يبدأ النشيد من الثانية 3 وينتهي قبل الكلمات
 * الأخيرة مثلاً. هذا الصنف يحمل قرارات القص اليدوي المشتركة بين محرر الواجهة
 * والمستودع — تطبيع القيم، التحقق من الصلاحية، ونصوص التغذية الراجعة العربية.
 *
 * القرارات الإذاعية:
 *  - المقطع المقصوص يجب أن يبقى مسموعاً: ثانية واحدة على الأقل بين البداية والنهاية.
 *  - القيم تُقرَّب لأقرب 100ms — دقة القص السمعي لا تتعداها الأذن عملياً.
 *  - القص اليدوي قرار المذيع الصريح: لا يجوز أن يمسّه التحليل الآلي بعدها أبداً.
 */
object TrimLogic {

    /** أقل مدة مسموعة تبقى بين نقطة البداية والنهاية */
    const val MIN_SEGMENT_MS = 1000L

    /** خطوة التقريب بالميلي ثانية */
    const val STEP_MS = 100L

    /** تقريب قيمة إلى أخرب خطوة (100ms) — تجانس القص عبر المنزلقات والحفظ */
    fun snap(ms: Long): Long = (ms / STEP_MS) * STEP_MS

    /**
     * تطبيع زوج (بداية، نهاية) داخل حدود الملف:
     *  - البداية لا تقل عن صفر، والنهاية لا تتجاوز مدة الملف (إن عُرفت).
     *  - فجوة دنيا [MIN_SEGMENT_MS] بين النقطتين تُفرض آلياً بسحب الطرف الأقرب.
     * @return الزوج المطبَّع (بداية، نهاية) جاهزاً للحفظ
     */
    fun normalize(startMs: Long, endMs: Long, durationMs: Long): Pair<Long, Long> {
        // ملف بمدة مجهولة: سقف أمان يوم كامل يمنع القيم الفوضوية دون تقييد حقيقي
        val maxEnd = if (durationMs > 0) durationMs else 86_400_000L
        var s = snap(startMs.coerceAtLeast(0L)).coerceAtMost((maxEnd - MIN_SEGMENT_MS).coerceAtLeast(0L))
        var e = snap(endMs.coerceIn(0L, maxEnd))
        // فرض الفجوة الدنيا — كل طرف ينجذب نحو الآخر
        if (e - s < MIN_SEGMENT_MS) {
            if (endMs <= s) s = (e - MIN_SEGMENT_MS).coerceAtLeast(0L)
            else e = (s + MIN_SEGMENT_MS).coerceAtMost(maxEnd)
        }
        // إعادة ضبط أخيرة بعد الانجذاب — قد تخرج البداية عن صفر أو النهاية عن السقف
        s = s.coerceIn(0L, (e - MIN_SEGMENT_MS).coerceAtLeast(0L))
        e = e.coerceIn((s + MIN_SEGMENT_MS).coerceAtMost(maxEnd), maxEnd)
        return s to e
    }

    /**
     * فحص صلاحية القص قبل الحفظ.
     * @return null عند الصلاحية، أو رسالة عربية جاهزة للعرض
     */
    fun validate(startMs: Long, endMs: Long, durationMs: Long): String? {
        if (endMs - startMs < MIN_SEGMENT_MS)
            return "المدة المختارة قصيرة جداً — يجب أن تبقى ثانية واحدة مسموعة على الأقل"
        if (durationMs > 0) {
            if (startMs >= durationMs) return "نقطة البداية بعد نهاية الملف"
            if (endMs > durationMs) return "نقطة النهاية بعد نهاية الملف — أعد ضبطها"
        }
        return null
    }

    /**
     * وصف القص الحالي بسطر واحد — يظهر داخل المحرر: «من 0:03 إلى 1:25 (المتبقي 1:22)»
     */
    fun summary(startMs: Long, endMs: Long): String =
        "من ${ScheduleLogic.formatDuration(startMs)} إلى ${ScheduleLogic.formatDuration(endMs)}" +
            " — المتبقي ${ScheduleLogic.formatDuration((endMs - startMs).coerceAtLeast(0))}"

    /**
     * رسالة تأكيد الحفظ — تعرض في snackbar بعد «قص يدوي»
     */
    fun message(startMs: Long, endMs: Long, title: String): String =
        "حُفظ قص «$title» — ${summary(startMs, endMs)}"

    /** رسالة إزالة القص */
    const val CLEAR_MESSAGE = "أُزيل القص — سيُشغَّل المقطع كاملاً من بدايته إلى نهايته"

    /** هل المقطع يحمل قصاً (يدوياً أو آلياً) يستحق إظهار زر الإزالة؟ */
    fun hasAnyTrim(trimStartMs: Long, trimEndMs: Long, trimManual: Boolean): Boolean =
        trimManual || trimStartMs > 0 || trimEndMs > 0
}

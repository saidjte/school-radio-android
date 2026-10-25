package com.saidjte.schoolradio.core.util

/**
 * v0.18.0 — منطق قص الفراغ الأول من المقاطع (منطق صافٍ بلا أندرويد — قابل للاختبار).
 *
 * الفكرة: المحلّل الصوتي (SilenceAnalyzer) يفك تشفير بداية الملف ويحسب قمة
 * السعة (Peak) لكل نافذة زمنية قصيرة. هذا الصنف يقرأ سلسلة القمم ويستنتج
 * كم ميلي ثانية من الصمت يبدأ الملف بها — لتقفز التشغيل إليه مباشرة
 * (ClippingConfiguration) فلا لحظات صمت ميتة قبل أن يسمع الجمهور أول صوت.
 *
 * القرارات الإذاعية:
 *  - عتبة السعة محافظة (~ -36dB) حتى لا نعتبر نفَساً خفيفاً صمتاً.
 *  - نُبقي وسادة 60ms قبل أول صوت — الدخول الحاد المفاجئ يبدو مقطوعاً.
 *  - القص لا يستحق التطبيق دون 100ms — إزاحات أصغر لا تُسمع وتعقّد التمرير.
 *  - ملف صامت بالكامل → لا قص (0) — قصّه كله عطلة لا ميزة.
 */
object SilenceLogic {

    /** قمة السعة التي فوقها نعد النافذة «صوتاً» — من 32767 (~ -36dB) */
    const val DEFAULT_THRESHOLD = 500

    /** طول النافذة الزمنية لكل قمة سعة بالميلي ثانية */
    const val WINDOW_MS = 20L

    /** وسادة تُبقى قبل أول صوت كي لا يبدأ التشغيل بقطعة صوت مقطوعة */
    const val KEEP_PADDING_MS = 60L

    /** أقل قص يستحق التطبيق — ما دونها لا يُسمع */
    const val MIN_TRIM_MS = 100L

    /** أقصى مدة نحللها من بداية الملف — بعدها نستسلم (لا قص) حماية من الملفات العجيبة */
    const val MAX_PROBE_MS = 60_000L

    /**
     * استنتاج مدة الفراغ الأول من قمم النوافذ.
     * @param peaks قمة السعة لكل نافذة متتالية من بداية الملف (0..32767)
     * @return عدد الميلي ثانية التي يجب تخطيها من البداية (0 = لا فراغ يستحق القص)
     */
    fun leadingSilenceMs(
        peaks: List<Int>,
        windowMs: Long = WINDOW_MS,
        threshold: Int = DEFAULT_THRESHOLD,
        paddingMs: Long = KEEP_PADDING_MS
    ): Long {
        if (peaks.isEmpty()) return 0L
        val firstSound = peaks.indexOfFirst { it >= threshold }
        if (firstSound < 0) return 0L // صمت كامل (حسب ما حللناه) — لا نقطع الملف
        val raw = firstSound * windowMs
        val trimmed = (raw - paddingMs).coerceAtLeast(0L)
        return if (trimmed < MIN_TRIM_MS) 0L else trimmed
    }

    /** رسالة عربية لنتيجة التحليل — تعرض في snackbar بعد «قص الفراغ الأول» */
    fun message(trimMs: Long, title: String): String = when {
        trimMs <= 0L -> "«$title» — لا فراغ يُذكر في بدايته"
        trimMs < 1000L -> "قُصّ ${AppFormat.number(trimMs)} م.ث من بداية «$title»"
        else -> "قُصّ ${AppFormat.numberOneDecimal(trimMs / 1000.0)} ثانية من بداية «$title»"
    }
}

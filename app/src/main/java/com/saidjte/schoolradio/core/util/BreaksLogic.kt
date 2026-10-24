package com.saidjte.schoolradio.core.util

import kotlin.random.Random

/**
 * v0.12.0 — منطق الفواصل المجدولة (Breaks) — النمط الإذاعي الاحترافي:
 *
 * في أنظمة الأتمتة الإذاعية (Radiojar وأمثالها) تُدرج «الفواصل» آلياً بين
 * عناصر البرنامج: تنتهي الفقرة فتنطلق جينغة الانتقال ثم تبدأ الفقرة التالية،
 * دون أن يلمس المذيع شيئاً.
 *
 * هذا الملف يحمل القرارات الصافية للخاصية — بلا أندرويد ولا مشغلات — ليبقى:
 *  1) قابلاً للاختبار وحدوياً على JVM الخالص (كمنطق الجدولة والمسجل قبله)،
 *  2) قابلاً لإعادة الاستخدام من مسارين:
 *     • المسار المجدول: AlarmReceiver يدرج فاصلاً بعد فقرة انتهت إن وُجدت فقرة تالية اليوم.
 *     • المسار اليدوي: زر «تشغيل مع الفواصل» يبني قائمة واحدة متتالية (فقرة، فاصل، فقرة…).
 *
 * قواعد السلوك المعتمدة:
 *  - الفاصل يقع **بين** الفقرات فقط — لا فاصل بعد الختام (المشاهدون يفهمون النهاية).
 *  - فقرة بلا محتوى متاح تُتخطى ولا تُسبق بفاصل يتيم.
 *  - جينغلات التصنيف تُختار عشوائياً **بلا تكرار مباشر** — لا يُعاد نفس النشيد مرتين متتاليتين.
 *  - إذا تعذّر تحديد جينغل (تصنيف فارغ / مقطع غير متاح) يُستمر بلا فواصل بلا أي عطل.
 */
object BreaksLogic {

    /** عنصر تشغيل مجرد — فصل المنطق عن PlaybackConnection (اختبار JVM بلا وسائط) */
    data class PlaySpec(val uri: String, val title: String, val trackId: Long)

    /**
     * إعداد فواصل جدول — مرآة لأعمدة radio_schedules المضافة في v0.12.0.
     * [trackId] جينغل محدد يتكرر بين كل فقرتين، [categoryId] اختيار عشوائي من تصنيف.
     * تحديد الاثنين معاً يعطي الأولوية للمقطع المحدد (أوضح للمستخدم).
     */
    data class BreakConfig(
        val enabled: Boolean = false,
        val trackId: Long? = null,
        val categoryId: Long? = null
    ) {
        val isConfigured: Boolean get() = trackId != null || categoryId != null
    }

    /** معلومة موعّية مصغرة لفقرة — تكفي لقرار «هل توجد فقرة تالية اليوم؟» */
    data class EventTiming(val eventId: Long, val startMinutes: Int, val weekDaysMask: Int)

    /**
     * هل توجد فقرة تالية اليوم لنفس الجدول بعد الفقرة الحالية؟
     * الفاصل يُدرج فقط عند وجود فقرة لاحقة في اليوم نفسه — وإلا فالفقرة ختامية.
     * ملاحظة: [minutesNow] يُقارن بموعد الفقرات (لا بوقت انتهائها) لأن مدة الفقرات
     * غير معلومة مسبقاً، والفقرات المتزامنة (نفس الدقيقة) تُحتسب تاليةً لبعضها.
     */
    fun hasNextSegmentToday(
        events: List<EventTiming>,
        currentEventId: Long,
        dayBit: Int,
        minutesNow: Int
    ): Boolean = events.any { e ->
        e.eventId != currentEventId &&
            (e.weekDaysMask and dayBit) != 0 &&
            e.startMinutes >= minutesNow
    }

    /**
     * تحديد الجينغل المناسب وفق الإعداد:
     *  - معطّل أو غير مضبوط → null (لا فاصل).
     *  - مقطع محدد → يُعاد إن وُجد في [pool] (الفحص يضمن توفره قبل التجميع).
     *  - تصنيف → اختيار عشوائي بلا تكرار مباشر عبر [pickNoRepeat].
     */
    fun resolveBreakSpec(
        config: BreakConfig,
        pool: List<PlaySpec>,
        lastBreakTrackId: Long? = null,
        rng: Random = Random.Default
    ): PlaySpec? = when {
        !config.enabled || !config.isConfigured -> null
        config.trackId != null -> pool.firstOrNull { it.trackId == config.trackId }
        else -> pickNoRepeat(pool, lastBreakTrackId, rng)
    }

    /**
     * اختيار عشوائي من التجمع مع منع التكرار المباشر:
     * إذا وُجدت بديلة غير الأخيرة تُستبعد الأخيرة، وإلا يُقبل التكرار (تجمع من عنصر واحد).
     */
    fun pickNoRepeat(
        pool: List<PlaySpec>,
        lastTrackId: Long?,
        rng: Random = Random.Default
    ): PlaySpec? {
        if (pool.isEmpty()) return null
        val candidates =
            if (pool.size > 1 && lastTrackId != null) pool.filter { it.trackId != lastTrackId }
            else pool
        return candidates.randomOrNull(rng)
    }

    /**
     * بناء قائمة التشغيل المتداخلة للبرنامج كاملاً:
     *   فقرة1 ← جينغل ← فقرة2 ← جينغل ← … ← فقرةN (بلا فاصل بعدها)
     *
     * القواعد المطبقة:
     *  - الفواصل معطّلة أو غير مضبوطة → دمج متسلسل عادي (سلوك البرامج المتتالية المألوف).
     *  - الفقرات الفارغة (محتواها غير متاح) تُتخطى مع ما قبلها — لا فاصل يتيم،
     *    وثبات القاعدة: كل فاصل يفصل بين محتويين فعليين.
     *  - تعذر تحديد جينغل في موضع ما → يُتخطى الفاصل ويُستمر (بلا عطل أبداً).
     *
     * [rng] حقن الاختبار — يضمن نتائج حتمية عند اختبار الاختيار العشوائي.
     */
    fun buildQueue(
        segments: List<List<PlaySpec>>,
        config: BreakConfig,
        breakPool: List<PlaySpec>,
        rng: Random = Random.Default
    ): List<PlaySpec> {
        val out = mutableListOf<PlaySpec>()
        val filled = segments.filter { it.isNotEmpty() }
        var lastBreakTrackId: Long? = null
        filled.forEachIndexed { index, segment ->
            if (index > 0 && config.enabled && config.isConfigured) {
                val brk = resolveBreakSpec(config, breakPool, lastBreakTrackId, rng)
                brk?.let {
                    out.add(it)
                    lastBreakTrackId = it.trackId
                }
            }
            out.addAll(segment)
        }
        return out
    }

    /** كم فواصلاً سيُدرج نظرياً لعدد فقرات فعلي معطى؟ (بين الفقرات فقط) */
    fun breaksCountFor(filledSegments: Int): Int = (filledSegments - 1).coerceAtLeast(0)
}

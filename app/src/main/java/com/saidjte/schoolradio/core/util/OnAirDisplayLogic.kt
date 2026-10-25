package com.saidjte.schoolradio.core.util

/**
 * v0.24.0 — منطق صافٍ لعناصر شاشة العرض الجديدة.
 *
 * ثلاث ميزات على «شاشة العرض» (OnAirScreen) تعتمد حسابات قابلة للاختبار:
 *  1. عناوين الفقرات القادمة (من قائمة التشغيل الجارية ومن جدول اليوم عند الخمول).
 *  2. مؤقت ترتيل المذيع — زمن الحديث المباشر ولون دلالي حسب التجاوز.
 *  3. مؤشر VU — امتصاص القمم الثابتة وتقسيم العمود إلى شرائح.
 */
object OnAirDisplayLogic {

    // ==================== 1) الفقرات القادمة من قائمة التشغيل ====================

    /**
     * عناوين الفقرات القادمة بعد العنصر الجاري في قائمة التشغيل.
     *
     * @param titles     عناوين القائمة الكاملة (بالترتيب) كما تصل من محرك التشغيل.
     * @param queueIndex فهرس العنصر الجاري — -1 يعني بلا عنصر جاري (تُعاد أول العناوين).
     * @param limit      أقصى عدد يُعرض (الشاشة الكبيرة تُظهر 4 بوضوح من آخر القاعة).
     */
    fun upcomingQueueTitles(titles: List<String>, queueIndex: Int, limit: Int = 4): List<String> {
        if (limit <= 0) return emptyList()
        return if (queueIndex < 0) titles.take(limit)
        else titles.drop(queueIndex + 1).take(limit)
    }

    // ==================== 2) الفقرات المجدولة القادمة (عند الخمول) ====================

    /** نمط مُجزَّأ لحدث مجدول — يفصل المنطق الصافي عن كيانات قاعدة البيانات */
    data class UpcomingEvent(val title: String, val startMinutes: Int)

    /**
     * أحداث اليوم القادمة زمنياً فقط (بعد الدقيقة الحالية)، مرتبة تصاعدياً بموعد البدء.
     * الأحداث المكررة بنفس الدقيقة تحتفظ بترتيبها الأصلي (استقرار الفرز).
     */
    fun upcomingEvents(
        events: List<UpcomingEvent>,
        nowMinutes: Int,
        limit: Int = 3
    ): List<UpcomingEvent> =
        events.asSequence()
            .filter { it.startMinutes > nowMinutes }
            .sortedBy { it.startMinutes }
            .take(limit.coerceAtLeast(0))
            .toList()

    // ==================== 3) مؤقت ترتيل المذيع ====================

    /** عتبات الترتيل بالثواني: كهرماني بعد دقيقة، أحمر بعد دقيقتين من الحديث المتواصل */
    const val PACING_SOON_SEC = 60L
    const val PACING_FINAL_SEC = 120L

    /**
     * اللون الدلالي لزمن الحديث المباشر — نفس ألوان العد التنازلي المعتادة
     * (زمردي عادي / كهرماني اقترب / أحمر تجاوز) حتى تبقى لغة الألوان موحدة على الشاشة.
     */
    fun pacingUrgency(elapsedSec: Long): AirCountdown.Urgency = when {
        elapsedSec >= PACING_FINAL_SEC -> AirCountdown.Urgency.FINAL
        elapsedSec >= PACING_SOON_SEC -> AirCountdown.Urgency.SOON
        else -> AirCountdown.Urgency.NORMAL
    }

    /** زمن الترتيل بالثواني منذ فتح الميكروفون (محمي من ساعة النظام المرتجعة) */
    fun pacingElapsedSec(startedAtMs: Long?, nowMs: Long): Long =
        if (startedAtMs == null || nowMs < startedAtMs) 0L
        else (nowMs - startedAtMs) / 1000L

    // ==================== 4) مؤشر VU ====================

    /** عدد الشرائح في كل عمود VU — 20 شريحة تُقرأ بوضوح من آخر القاعة */
    const val VU_SEGMENTS = 20

    /**
     * امتصاص القمة الثابتة (Peak Hold): ترتفع فوراً مع أي مستوى أعلى،
     * وتنحدر بمقدار [decay] في كل نبضة حتى تصفر.
     */
    fun peakHold(prevPeak: Float, level: Float, decay: Float = 0.015f): Float {
        val l = level.coerceIn(0f, 1f)
        val d = prevPeak.coerceIn(0f, 1f) - decay
        return maxOf(l, d, 0f).coerceAtMost(1f)
    }

    /** عدد الشرائح المضاءة في عمود VU لمستوى معين (مقارب لأعلى ليُظهر بداية الحركة) */
    fun vuSegments(level: Float, segments: Int = VU_SEGMENTS): Int {
        if (segments <= 0) return 0
        return (level.coerceIn(0f, 1f) * segments + 0.999f).toInt().coerceIn(0, segments)
    }

    /** منطقة الشريحة حسب موقعها: سفينة خضراء، ثم كهرماني، ثم قمة حمراء (نفس تقسيمات VU الكلاسيكي) */
    fun vuZoneColor(segIndex: Int, segments: Int): AirCountdown.Urgency = when {
        segIndex >= segments - 2 -> AirCountdown.Urgency.FINAL      // أعلى شريحتين
        segIndex >= (segments * 0.75f).toInt() -> AirCountdown.Urgency.SOON
        else -> AirCountdown.Urgency.NORMAL
    }
}

package com.saidjte.schoolradio.data.remote

/**
 * v0.42.0 — حسابات التنزيل المجزأ المتوازي (منطق صافٍ قابل للاختبار).
 *
 * سبب وجوده: خوادم المنصات (googlevideo لكبار يوتوب مثلاً) تخنق **الاتصال
 * الواحد** بمعدل بايتات متعمد — وهو سبب بطء التنزيل المبلغ عنه — بينما تقبل
 * عدة اتصالات على التدفق نفسه بلا خنق يُذكر. الحل القياسي المعتمد في
 * أدوات التنزيل النضيدة (yt-dlp بـ concurrent-fragments وaria2 بـ -x):
 * تقسيم الملف إلى شرائح بمحرف Range (RFC 7233) وتنزيلها اتصالات متوازية.
 *
 * كل القرارات هنا صافية: هل يصلح التقسيم؟ أين تبدأ وتنتهي كل شريحة؟
 * كيف يُجمع التقدم؟ — وI/O الفعلي في StreamResolver يتبع هذه الخطة حرفاً.
 */
object ParallelDownloadLogic {

    /** أقل حجم يبرر التقسيم — أصغر من ميغابايت: اتصال واحد أبسط وأسرع من إدارته */
    const val MIN_PARALLEL_BYTES = 1_000_000L

    /** عدد الاتصالات المتوازية — 6: يفك خنق الاتصال الواحد بلا إرهاق لشبكة المدارس */
    const val SEGMENT_COUNT = 6

    /** دفعة القراءة لكل اتصال — 256KB: تشبع أسرع من 64KB على الشبكات المتوسطة */
    const val READ_BUFFER_BYTES = 256 * 1024

    /** شريحة تنزيل واحدة: [start, endInclusive] بالمحارف ضمن الملف الكلي */
    data class Segment(val index: Int, val start: Long, val endInclusive: Long) {
        /** طول الشريحة بالمحارف */
        val length: Long get() = endInclusive - start + 1
    }

    /**
     * خطة التقسيم: [SEGMENT_COUNT] شرائح متتالية متساوية تقريباً تغطي الملف
     * كاملاً بلا فراغات ولا تداخل — أو null حين لا يصلح التقسيم:
     * لا دعم Range معلناً، أو الحجم مجهول، أو أصغر من عتبة الجدوى.
     */
    fun plan(total: Long, acceptRanges: Boolean): List<Segment>? {
        if (!acceptRanges) return null
        if (total < MIN_PARALLEL_BYTES) return null
        val base = total / SEGMENT_COUNT
        if (base <= 0) return null
        return List(SEGMENT_COUNT) { i ->
            val start = i * base
            val end = if (i == SEGMENT_COUNT - 1) total - 1 else start + base - 1
            Segment(i, start, end)
        }
    }

    /** ترويسة Range لشريحة — الصيغة القانونية الوحيدة: «bytes=start-end» */
    fun rangeHeader(segment: Segment): String =
        "bytes=${segment.start}-${segment.endInclusive}"

    /**
     * اجمع بايتات الشرائح الحية — مجموعها = موضع الملف الكلي المُنزَّل.
     * (تُقرأ من AtomicLongArray في الاستدعاء الفعلي فيُمرَّر المجموع هنا للعرض)
     */
    fun aggregate(downloadedBySegment: LongArray): Long = downloadedBySegment.sum()

    /**
     * تحقق النزاهة النهائية: مجموع الشرائح المخططة = الحجم المعلن —
     * أي نقص (شريحة قُطعت مبكراً وسكتت بهدوء) يُبطل الملف المتوازي كله
     * ويهبط التنزيل للتدفق الواحد الموثوق بدل حفظ ملف ناقص صامت.
     */
    fun isComplete(plan: List<Segment>, downloadedTotal: Long): Boolean =
        plan.sumOf { it.length } == downloadedTotal
}

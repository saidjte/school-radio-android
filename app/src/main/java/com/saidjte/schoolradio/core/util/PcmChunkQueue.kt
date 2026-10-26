package com.saidjte.schoolradio.core.util

/**
 * v0.40.0 — طابور حجز دفعات PCM بين خيط الميكروفون ومشفر MediaCodec (منطق صافٍ
 * بلا أي تبعية أندرويد — قابل للاختبار وحدوياً).
 *
 * ─── لماذا وُجد؟ العيب الحقيقي الثاني في سلسلة «التسجيلات لا تعمل» ───
 * كان OpusFileWriter يستدعي dequeueInputBuffer(0) ويُسقط دفعة الميكروفون كاملةً
 * متى كان مدخل المشفر مشغولاً لحظياً (-1) — فجوة صوت مسموعة لا تعوَّض أبداً،
 * تتكرر مع كل انشغال قصير للمشفر (بدء الترميز، انشغال المجدول، GC) فيخرج
 * التسجيل مكسور الإيقاع رغم سلامة حاويتها. الطابور يحجز الدفعات **نسخاً**
 * (الخيوط المصدرة تعيد استخدام مخازنها) ويُفرَّغ بدخول المشفر: صفر إسقاط
 * في التشغيل الطبيعي، وإذا انسد المشفر فعلاً فوق سعة الطابور تُبرح أقدم
 * دفعة (البث يكمل من الحاضر) مع عدّاد صادق للإسقاطات.
 *
 * العقد:
 * - FIFO صارم: الأقدم يخرج أولاً — ترتيب الصوت مقدس.
 * - push ينسخ الدفعة إلى مخزنه الخاص: تعديل المصدر بعد الدفع لا يؤثر.
 * - totalQueued يَعُدّ الدفعات الجاثمة في الطابور فقط — المدة الحية تُبنى
 *   على المشفَّر فعلاً (نفس فلسفة granule-from-TOC في OggOpusWriter).
 */
class PcmChunkQueue(private val capacity: Int = DEFAULT_CAPACITY) {

    /** دفعة PCM محجوزة: أول [length] عنصراً من [data] هي المحتوى الفعلي */
    class Chunk internal constructor(val data: ShortArray, val length: Int)

    init {
        require(capacity >= 1) { "سعة الطابور يجب أن تكون 1 على الأقل" }
    }

    private val chunks = ArrayDeque<Chunk>(capacity)

    /** عدد الدفعات المحجوزة بانتظار الترميز */
    val size: Int get() = chunks.size

    /** هل الطابور جاثم بلا دفعات؟ */
    val isEmpty: Boolean get() = chunks.isEmpty()

    /** هل يحمل الطابور دفعات بانتظار الترميز؟ */
    val isNotEmpty: Boolean get() = chunks.isNotEmpty()

    /** عدد الدفعات التي بُرحت بلا ترميز عند انسداد المشفر فوق السعة — عدّاد صادق */
    var droppedChunks: Int = 0
        private set

    /**
     * حجز دفعة (نسخاً) — إن امتلأ الطابور تُبرح أقدم دفعة ليتسع الجديد
     * (البث الحي يقدّم الحاضر على الماضي البعيد) ويُعدّ الإسقاط.
     */
    fun push(data: ShortArray, length: Int) {
        require(length >= 0 && length <= data.size) { "طول دفعة غير صالح" }
        if (length == 0) return
        while (chunks.size >= capacity) {
            chunks.removeFirst()
            droppedChunks++
        }
        val copy = ShortArray(length)
        System.arraycopy(data, 0, copy, 0, length)
        chunks.addLast(Chunk(copy, length))
    }

    /** أقدم دفعة محجوزة بلا سحب — لنسخها في مدخل المشفر قبل تقرير القبول */
    fun peek(): Chunk? = chunks.firstOrNull()

    /** سحب أقدم دفعة محجوزة (بعد قبولها في المشفر) */
    fun pop(): Chunk? = chunks.removeFirstOrNull()

    /** تفريغ الطابور وتصفير العدّاد — عند إغلاق الكاتب أو إعادة تهيئته */
    fun clear() {
        chunks.clear()
        droppedChunks = 0
    }

    companion object {
        /**
         * السعة الافتراضية: 32 دفعة × 10ms ≈ 320ms ذاكرة عازلة
         * (32 × 960 بايت مونو 16-bit ≈ 30KB) — تبتلع كل انشغالات المشفر
         * العابرة دون أن تُلمس، وبلا أي كلفة ذاكرة تُذكر.
         */
        const val DEFAULT_CAPACITY = 32
    }
}

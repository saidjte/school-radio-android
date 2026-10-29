package com.saidjte.schoolradio.core.util

/**
 * رياضيات دمج تسجيل البث الكامل (v0.49.0) — بلا أي تبعيات أندرويد حتى يكون
 * قابلاً للاختبار وحدات.
 *
 * خلفية القرار: التقاط إخراج الوسائط (MediaProjection) يعيد مزيج ما يسمعه
 * الجمهور فعلاً بعد الفيدرات والخفض والجينل — لكنه لا يلتقط مسار المايك
 * عندما يعمل بusage الاتصالات (مكبر الجهاز). الحل: تاب PCM المايك الموجود
 * أصلاً (onPcm) يُدمج حسابياً في دفعة الالتقاط:
 *  - الالتقاط (ستيريو) يُخفض إلى مونو بمتوسط القناتين — مكبرات الاستوديو مونو واقعياً.
 *  - دمج: تخفيف الالتقاط 15% ثم جمع المايك مع تقييد المدى — هامش رأس ضد القصّ
 *    عندما يتحدث المذيع فوق موسيقى بذروة كاملة.
 */
object BroadcastCaptureLogic {

    /** معامل بقاء الالتقاط عند وجود مايك يُدمج معه */
    const val CAPTURE_ATTENUATION = 0.85

    /**
     * دمج عيّنة التقاط مع عيّنة مايك (قيم خام قبل التحويل إلى Short).
     * تخفيف الالتقاط + جمع + تقييد مدى PCM-16 — قرار حتمي قابل للاختبار.
     */
    fun combine(capture: Int, mic: Int): Int {
        val mixed = (capture * CAPTURE_ATTENUATION).toInt() + mic
        return mixed.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
    }

    /** نسخة Short مباشرة لحلقة الخلط الساخنة (10ms × 480 عينة) */
    fun combineToShort(capture: Short, mic: Short): Short =
        combine(capture.toInt(), mic.toInt()).toShort()

    /**
     * خفض ستيريو متداخل (L/R) إلى مونو بمتوسط كل زوج — يكتب في المخزن المعاد
     * استخدامه بدل تخصيص جديد كل 10ms (خط صوتي لا يجوز أن يضغط جامع النفايات).
     */
    fun downmixInto(stereo: ShortArray, mono: ShortArray, frames: Int) {
        for (f in 0 until frames) {
            mono[f] = ((stereo[2 * f] + stereo[2 * f + 1]) / 2).toShort()
        }
    }
}

/**
 * خزان دفعات المايك بين خيط البث (منتج 10ms) وخيط الالتقاط (مستهلك 10ms) —
 * حلقي بسقف 100ms: أي اختناق لحظي يمتصه، وعند الامتلاء يُبرح الأقدم
 * (تسجيل حي لا يستحق حبيبات تأخر الماضي على حساب الحاضر).
 * خالص Kotlin بلا أقفال منصة — @Synchronized كافٍ على مقاطع بحجم دفعة واحدة.
 */
class MicFrameRing(private val capacity: Int) {
    init { require(capacity > 0) { "سعة الخزان يجب أن تكون موجبة" } }

    private val buf = ShortArray(capacity)
    private var head = 0 // موضع القراءة التالي
    private var size = 0

    val count: Int @Synchronized get() = size

    /** دفعة مايك من خيط البث — عند الامتلاء تُستبدل الأقدم عيّنة بعيّنة */
    @Synchronized
    fun push(pcm: ShortArray, n: Int) {
        for (i in 0 until n) {
            if (size == capacity) {
                buf[head] = pcm[i] // الأقدم مكان الرأس — ثم يتقدم الرأس
                head = (head + 1) % capacity
            } else {
                buf[(head + size) % capacity] = pcm[i]
                size++
            }
        }
    }

    /** سحب حتى [max] عينة لدمجها مع دفعة التقاط — يعيد العدد المسحوب فعلاً */
    @Synchronized
    fun pull(dest: ShortArray, max: Int): Int {
        val take = minOf(max, size)
        for (i in 0 until take) {
            dest[i] = buf[head]
            head = (head + 1) % capacity
        }
        size -= take
        return take
    }

    @Synchronized
    fun clear() {
        head = 0
        size = 0
    }
}

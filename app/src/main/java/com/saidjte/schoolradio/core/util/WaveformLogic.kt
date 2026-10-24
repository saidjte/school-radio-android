package com.saidjte.schoolradio.core.util

import kotlin.math.sqrt

/**
 * v0.12.0 — رياضيات الموجة الحية (Live Waveform) — نقية بالكامل للاختبار JVM.
 *
 * مستوى RMS الخام الواصل من حلقة الصوت (0..1) لا يصلح ارتفاعاً مباشراً للأعمدة:
 *  - صمت المذيع يترك قيماً صغيرة غير صفرية (همهمة الغرفة) فتبدو الموجة «ترتجف».
 *  - الإدراك البشري للجهارة ليس خطياً — الجذر التربيعي يجعل الهمس مرئياً
 *    والصوت القوي لا يصل سقف الشاشة فوراً.
 *  - التحديث كل 10ms يجعل الأعمدة ترتجف بين إطارين — انحدار ناعم يثبّت المشهد.
 */
object WaveformLogic {

    /** عدد أعمدة نافذة الموجة (كل عمود ≈ 45ms → نافذة ≈ 2.9 ثانية) */
    const val HISTORY_BARS = 64

    /** أرضية الضجيج — ما دونها يُعد صمتاً (يقص ارتجاف الغرفة الفارغة) */
    private const val NOISE_FLOOR = 0.004f

    /** مكسب عرضي بعد الجذر — يملأ الارتفاع المتوسط بمقدار مقبول بلا تشبع مبكر */
    private const val DISPLAY_GAIN = 1.12f

    /**
     * تحويل RMS الخام إلى ارتفاع عرضي 0..1:
     * قصّ الأرضية ← تطبيع ← جذري تربيعي (إدراكي) ← مكسب ← تشبع ناعم عند 1.
     * NaN أو قيم سالبة (لا ينبغي حدوثها) تعود صفراً بأمان.
     */
    fun normalize(rawRms: Float): Float {
        if (!rawRms.isFinite() || rawRms <= NOISE_FLOOR) return 0f
        val linear = ((rawRms - NOISE_FLOOR) / (1f - NOISE_FLOOR)).coerceIn(0f, 1f)
        return (sqrt(linear.toDouble()).toFloat() * DISPLAY_GAIN).coerceIn(0f, 1f)
    }

    /**
     * تنعيم القيمة بين إطارين — نموذج «هبوط مقاييس VU»:
     * الصعود فوري (استجابة للضربة)، الهبوط يتبع انحداراً أسيّاً هادئاً فلا ترتجف الأعمدة.
     * [decay] نسبة بقاء القيمة السابقة عند الهبوط (0 = بلا تنعيم، 1 = جمود).
     */
    fun smooth(previous: Float, next: Float, decay: Float = 0.72f): Float =
        when {
            next >= previous -> next
            else -> next + (previous - next) * decay.coerceIn(0f, 0.95f)
        }

    /**
     * تغذية نافذة تاريخ قابلة للتغيير — تُبقي آخر [capacity] قيمة بترتيبها الزمني.
     * (منفصلة عن Compose لاختبار سلوك النافذة المنزلقة وحده.)
     */
    fun pushWindow(history: MutableList<Float>, value: Float, capacity: Int = HISTORY_BARS) {
        history.add(value)
        while (history.size > capacity) history.removeAt(0)
    }
}

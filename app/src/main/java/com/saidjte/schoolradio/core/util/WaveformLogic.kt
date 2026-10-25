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

    // =====================================================================
    // v0.20.0 — مغلف الشكل الموجي لمحرر القص اليدوي
    // =====================================================================

    /**
     * تحويل قمم النوافذ الخام (قمة سعة 16bit لكل نافذة 20ms) إلى مغلف عرضي
     * من [columns] عموداً جاهزاً للرسم على Canvas.
     *
     * القرارات الإذاعية:
     *  - التجميع بـ«القمة القصوى» لا المتوسط: الطنين أو النبضة القصيرة لا تختفي
     *    عند ضغط دقائق طويلة في بضع عشرات من الأعمدة — ما يهم المذيع أين *الصوت*
     *    لا أين متوسط الطاقة.
     *  - التطبيع على القمة الكلية للملف ثم جذر تربيعي (إدراك بشري — نفس فلسفة
     *    الموجة الحية أعلاه): الأجزاء الهادئة تبقى مرئية والقوية لا تتشبع.
     *  - ملف صامت كلياً (قمة صفر) يعيد أصفاراً — لا قسمة على صفر أبداً.
     *
     * @param windowPeaks قمة كل نافذة زمنية (0..32767) — قد تكون فارغة
     * @param columns عدد أعمدة العرض المطلوبة (96 عموداً نموذجياً في الحوار)
     * @return مصفوفة بارتفاع [columns] قيم 0..1 (فارغة إن كان الإدخال فارغاً)
     */
    fun envelope(windowPeaks: IntArray, columns: Int): FloatArray {
        if (columns <= 0 || windowPeaks.isEmpty()) return FloatArray(0)
        val out = FloatArray(columns)
        var globalMax = 0
        // تجميع القمم القصوى داخل كل خانة عرض
        val buckets = IntArray(columns)
        for (i in windowPeaks.indices) {
            val idx = (i.toLong() * columns / windowPeaks.size).toInt().coerceIn(0, columns - 1)
            val p = if (windowPeaks[i] < 0) 0 else windowPeaks[i]
            if (p > buckets[idx]) buckets[idx] = p
            if (p > globalMax) globalMax = p
        }
        if (globalMax <= 0) return out // ملف صامت كلياً — كل الأعمدة أصفار
        for (c in 0 until columns) {
            val linear = buckets[c].toFloat() / globalMax
            out[c] = sqrt(linear.toDouble()).toFloat().coerceIn(0f, 1f)
        }
        return out
    }

    /**
     * تحويل موضع أفقي داخل الموجة (0..1) إلى زمن بالميلي ثانية — عكس خرائط السحب
     * في محرر القص. يتسامح مع القيم خارج المجال بالتقييد داخل حدود الملف.
     */
    fun msAtFraction(fraction: Float, durationMs: Long): Long =
        (fraction.toDouble().coerceIn(0.0, 1.0) * durationMs).toLong().coerceIn(0L, durationMs)
}

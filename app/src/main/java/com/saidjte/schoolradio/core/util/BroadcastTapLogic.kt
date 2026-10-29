package com.saidjte.schoolradio.core.util

/**
 * رياضيات تابات تسجيل البث الكامل (v0.51.0) — بلا أي تبعيات أندرويد حتى يكون
 * قابلاً للاختبار وحدات.
 *
 * **الجذر المعماري الذي قلب معمارية v0.49.0**: توثيق أندرويد الرسمي يقول عن
 * AudioPlaybackCaptureConfiguration حرفياً «Configuration for capturing audio
 * played by *other apps*» — المنصة تستثني صوت التطبيق الملتقط نفسه من مجرى
 * الالتقاط حصراً، فكان ملف MIX يخرج بالمايك وحده (بلاغ: «التسجيل يسجل فقط
 * المايكروفون ولا يسجل الموسيقى من المشغل»).
 *
 * الإصلاح الجذري: التطبيق لا يحتاج أن يطلب صوته من أحد — صوته يمر بأيديه.
 * كل مصدر صوتي (مشغل المكتبة، حائط الجينغل، نغمة الإعلان، خط المايك) يُلتقط
 * PCM الذي يبعثه فعلاً إلى النظام، وتُجمع المساهمات حسابياً كما يجمعها
 * الخلاط الصوتي على مكبرات الاستوديو. عيّنة واحدة تُخفف ولا مصدر يُضاعف —
 * الجمع بذاته هو الخلط الحقيقي، والتقييد عند الحواف يمنع لفّ الموجة.
 */
object BroadcastTapLogic {

    /**
     * طيّ قنوات متعددة إلى مونو بمتوسط كل إطار — مكبرات الاستوديو مونو واقعياً.
     * قناة واحدة = نسخ صريح؛ قناتان = نفس حساب downmix السابق (قسمة صحيحة)؛
     * أكثر = متوسط كل القنوات. يكتب في المخزن المعاد استخدامه بدل تخصيص
     * جديد كل دفعة (خط صوتي لا يجوز أن يضغط جامع النفايات).
     */
    fun foldChannelsInto(input: ShortArray, channels: Int, mono: ShortArray, frames: Int) {
        require(channels >= 1) { "عدد القنوات يجب أن يكون موجباً" }
        if (channels == 1) {
            System.arraycopy(input, 0, mono, 0, frames)
            return
        }
        for (f in 0 until frames) {
            var acc = 0
            for (c in 0 until channels) acc += input[f * channels + c]
            mono[f] = (acc / channels).toShort()
        }
    }

    /**
     * جمع تابٍّ على مراكم الخلط مع تقييد مدى PCM-16 — عينات التابات عند
     * مستوياتها الحقيقية (بعد الفيدرات والخفض التي طبقها المشغل نفسه) فالجمع
     * بذاته هو ما يسمعه الجمهور، والتقييد يمنع التشويه الرقمي عند ذروتين
     * متزامنتين كما يقتصد الخلاط التناظري.
     */
    fun sumInto(acc: ShortArray, add: ShortArray, n: Int) {
        for (i in 0 until n) {
            val s = acc[i] + add[i]
            acc[i] = when {
                s > Short.MAX_VALUE -> Short.MAX_VALUE
                s < Short.MIN_VALUE -> Short.MIN_VALUE
                else -> s.toShort()
            }
        }
    }

    /** تحويل عينة float إلى PCM-16 مع تقييد المدى — تابات المشغلات قد تأتي عائمة */
    fun floatToShort(v: Float): Short {
        val s = v * 32767f
        return when {
            s >= Short.MAX_VALUE -> Short.MAX_VALUE
            s <= Short.MIN_VALUE -> Short.MIN_VALUE
            else -> s.toInt().toShort()
        }
    }

    /** أعلى عدد إطارات متوقع لمخرج إعادة قلّم [n] إطاراً من [fromRate] إلى [toRate] */
    fun estimateResampled(fromRate: Int, toRate: Int, n: Int): Int =
        if (fromRate <= 0 || toRate <= 0) 0 else n * toRate / fromRate + 2
}

/**
 * إعادة قلّم خطية متقطعة الدفعات — المعدل النادر في تابات المشغلات:
 * فك الترميز يخرج بمعدل المقطع نفسه (44.1kHz غالباً) بينما الأرشيف كله 48kHz.
 *
 * **الموضع نسبيٌّ صحيح لا تراكمي**: موضع الإدخال للإطار المخرج k هو بالضبط
 * k×fromRate/toRate بحساب صحيح (لا جمع نقطة عائمة) — فتقسيم الدفعات لا
 * يغيّر الناتج ببت واحد: عشر دفعات تُنتج حرفياً ما تُنتجه الدفعة الواحدة
 * مهما طال التيار، بلا انحراف يتراكم ولا إطار يفقد أو يُكرر.
 *
 * الإطار الذي تحتاج عينته الثانية دفعةً قادمة يُعلَّق بعينته الأولى وكسره
 * الصحيح ويكتمل مع أول عينة في الدفعة التالية — الاستيفاء متصل كتيار واحد.
 * دقة الخطية كافية تماماً لأرشيف بث (نفس منهج مكبرات الاستوديو، بلا كلفة).
 */
class LinearResampler(private val fromRate: Int, private val toRate: Int) {
    init { require(fromRate > 0 && toRate > 0) { "المعدلات يجب أن تكون موجبة" } }

    private var k = 0L           // فهرس الإطار المخرج التالي منذ بداية التيار
    private var framesIn = 0L    // إطارات المدخل المسلَّمة حتى الآن (عبر الدفعات)
    private var carryS0 = 0.0    // عينة الإطار المعلق الأولى (آخر عينة في دفعته)
    private var carryFracNum = 0 // كسر موضع الإطار المعلق — بسط صحيح على toRate
    private var carryActive = false

    /**
     * قلّم دفعة مونو [input] (n إطاراً) إلى [out] — يعيد عدد الإطارات المكتوبة.
     * يسع [out] دائماً ما يُنتجه هذا الحجم عبر [BroadcastTapLogic.estimateResampled].
     */
    fun resample(input: ShortArray, n: Int, out: ShortArray): Int {
        require(n > 0) { "الدفعة يجب أن تحوي عينات" }
        var written = 0
        if (carryActive) {
            // s1 = أول عينة في هذه الدفعة — الإطار المعلق يكتمل أخيراً
            val v = (carryS0 + (input[0].toDouble() - carryS0) * carryFracNum / toRate).toInt()
            out[written++] = when {
                v > Short.MAX_VALUE -> Short.MAX_VALUE
                v < Short.MIN_VALUE -> Short.MIN_VALUE
                else -> v.toShort()
            }
            k++
            carryActive = false
        }
        while (written < out.size) {
            val pNum = k * fromRate                 // موضع المدخل × toRate — صحيح دقيق
            val i0g = pNum / toRate                 // فهرس s0 العالمي
            val fracNum = (pNum % toRate).toInt()   // بسط الكسر على toRate
            val local = i0g - framesIn              // فهرس s0 المحلي في هذه الدفعة
            if (local >= n - 1) {
                if (local == n - 1L) {
                    // s0 آخر عينة هنا و s1 في الدفعة القادمة — علّق الإطار
                    carryS0 = input[local.toInt()].toDouble()
                    carryFracNum = fracNum
                    carryActive = true
                }
                break // وإلا فs0 نفسه في دفعة قادمة (تخفيض قفزي) — لا شيء يُعلّق
            }
            val s0 = input[local.toInt()].toDouble()
            val s1 = input[local.toInt() + 1].toDouble()
            val v = (s0 + (s1 - s0) * fracNum / toRate).toInt()
            out[written++] = when {
                v > Short.MAX_VALUE -> Short.MAX_VALUE
                v < Short.MIN_VALUE -> Short.MIN_VALUE
                else -> v.toShort()
            }
            k++
        }
        framesIn += n.toLong()
        return written
    }
}

/**
 * خزان دفعات PCM بين خيط منتج (مشغل/مايك/نغمة) وخيط الخلط — حلقي بسقف ثابت:
 * أي اختناق لحظي يمتصه، وعند الامتلاء يُبرح الأقدم (تسجيل حي لا يستحق حبيبات
 * تأخر الماضي على حساب الحاضر).
 * خالص Kotlin بلا أقفال منصة — @Synchronized كافٍ على مقاطع بحجم دفعة واحدة.
 */
class MicFrameRing(private val capacity: Int) {
    init { require(capacity > 0) { "سعة الخزان يجب أن تكون موجبة" } }

    private val buf = ShortArray(capacity)
    private var head = 0 // موضع القراءة التالي
    private var size = 0

    val count: Int @Synchronized get() = size

    /** دفعة من خيط المنتج — عند الامتلاء تُستبدل الأقدم عيّنة بعيّنة */
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

    /** سحب حتى [max] عينة — يعيد العدد المسحوب فعلاً */
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

package com.saidjte.schoolradio.audio

/**
 * v0.13.0 — مخزن PCM حلقي (منتج واحد / مستهلك واحد) لفصل خيط الالتقاط عن خيط الإخراج.
 *
 * لماذا كان هذا ضرورياً؟ الحلقة القديمة كانت تسلسلية: read ← write ← read ← write
 * على نفس الخيط. أي توقف قصير (جمع قمامة، جدولة نظام، منافسة مع مشغل الموسيقى)
 * يترجم مباشرة إلى **فراغ صوتي مسموع** (underrun) — وهو «التقطيع» الذي يشتكي منه
 * المستخدم أثناء البث المباشر.
 *
 * الفصل المزدوج يحل المشكلة جذرياً:
 *  - خيط الالتقاط لا يفعل إلا القراءة من AudioRecord والكتابة في هذا المخزن.
 *  - خيط الإخراج يستهلك من المخزن ويكتب في AudioTrack.
 *  - أي اختناق لحظي في أي جهة يمتصه المخزن (سعة 200ms = عشرون دفعة 10ms)
 *    فلا يصل العطل إلى أذن المستمع.
 *
 * آمنة لخيطين بالضبط (SPSC): الكاتب منتج واحد والقارئ مستهلك واحد —
 * التزامن عبر synchronized + wait/notify، والاستهلاك الحقيقي 100 استدعاء/ثانية
 * لكل جهة فالنفقات مهملة قياساً إلى استقرار البث.
 */
class PcmRingBuffer(private val capacityShorts: Int) {

    init {
        require(capacityShorts > 0) { "سعة المخزن الحلقي يجب أن تكون موجبة" }
    }

    private val buf = ShortArray(capacityShorts)
    private var head = 0 // موضع الكتابة التالية
    private var tail = 0 // موضع القراءة التالية
    private var count = 0

    /** عدد العينات المتاحة للقراءة الآن */
    val available: Int
        @Synchronized get() = count

    /** عدد العينات الفارغة الآن */
    val freeSpace: Int
        @Synchronized get() = capacityShorts - count

    /** إحصاء تشخيصي: كم دفعة قُصّت من أقدمها بسبب امتلاء المخزن (يفترض نادراً جداً) */
    var droppedShorts: Long = 0
        private set

    /**
     * كتابة دفعة. إن لم تكفِ السعة تُقص أقدم العينات (ring semantics) —
     * الأولوية دائماً لأحدث الصوت في البث الحي، والفقد الواقعي شبه معدوم
     * لأن الاستهلاك يواكب الإنتاج في الزمن الحقيقي.
     * @return عدد العينات المكتوبة فعلاً
     */
    fun write(data: ShortArray, offset: Int, len: Int): Int = synchronized(this) {
        var toWrite = len
        var srcOff = offset
        if (len > capacityShorts) {
            // دفعة أضخم من المخزن كله: خذ آخر capacityShorts عينة منها
            srcOff = offset + (len - capacityShorts)
            toWrite = capacityShorts
        }
        var overflow = (count + toWrite) - capacityShorts
        if (overflow > 0) {
            // قص أقدم العينات لإفساح المجال
            tail = (tail + overflow) % capacityShorts
            count -= overflow
            droppedShorts += overflow
        }
        var pos = head
        var remaining = toWrite
        while (remaining > 0) {
            val seg = minOf(remaining, capacityShorts - pos)
            System.arraycopy(data, srcOff, buf, pos, seg)
            pos = (pos + seg) % capacityShorts
            srcOff += seg
            remaining -= seg
        }
        head = pos
        count += toWrite
        (this as java.lang.Object).notifyAll()
        toWrite
    }

    /**
     * قراءة حتى [len] عينة — تنتظر وصول بيانات حتى [timeoutMs] ثم تعيد ما وجدته.
     *
     * v0.15.0 — مناعة ضد المقاطعة (إصلاح انهيار إنهاء البث):
     * آلية الإيقاف قد تُقاطع خيط الإخراج بعد انتهاء مهلة الانتظار، والمقاطعة
     * أثناء wait() ترمي InterruptedException — قبل هذا الإصلاح كانت تقتل الخيط
     * وتُغلق التطبيق. الآن تُلتقط بهدوء وتُعاد القراءة كأن انتهى المهلة.
     *
     * @return عدد العينات المقروءة (0 إذا انتهى الانتظار بلا بيانات)
     */
    fun read(dest: ShortArray, offset: Int, len: Int, timeoutMs: Long): Int = synchronized(this) {
        if (count == 0 && timeoutMs > 0) {
            val deadline = System.currentTimeMillis() + timeoutMs
            var remaining = timeoutMs
            while (count == 0 && remaining > 0) {
                try {
                    (this as java.lang.Object).wait(remaining)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt() // حفظ إشارة المقاطعة دون موت الخيط
                    break
                }
                remaining = deadline - System.currentTimeMillis()
            }
        }
        if (count == 0) return 0
        val n = minOf(len, count)
        var pos = tail
        var dstOff = offset
        var remaining = n
        while (remaining > 0) {
            val seg = minOf(remaining, capacityShorts - pos)
            System.arraycopy(buf, pos, dest, dstOff, seg)
            pos = (pos + seg) % capacityShorts
            dstOff += seg
            remaining -= seg
        }
        tail = pos
        count -= n
        (this as java.lang.Object).notifyAll()
        n
    }

    /** تفريغ المخزن (عند إعادة الضبط/الإغلاق) */
    fun clear() = synchronized(this) {
        head = 0; tail = 0; count = 0
        (this as java.lang.Object).notifyAll()
    }

    /**
     * v0.15.0: إيقاف انتظار القراءة فوراً من خيط آخر (بلطف) —
     * يُستخدم عند إنهاء البث لاستيقاظ خيط الإخراج من wait() دون مقاطعة قاسية،
     * فلا InterruptedException إطلاقاً وينتهي الخيط بنفسه بسلاسة.
     */
    fun wakeUp() = synchronized(this) {
        (this as java.lang.Object).notifyAll()
    }
}

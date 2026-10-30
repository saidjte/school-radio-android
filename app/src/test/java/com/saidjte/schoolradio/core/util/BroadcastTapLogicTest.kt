package com.saidjte.schoolradio.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/** عقد رياضيات تابات تسجيل البث الكامل — v0.51.0 */
class BroadcastTapLogicTest {

    // ===== foldChannelsInto: طيّ القنوات إلى مونو =====

    @Test
    fun `قناة واحدة تنسخ حرفيا بلا مساس`() {
        val input = shortArrayOf(5, -7, 100)
        val mono = ShortArray(3)
        BroadcastTapLogic.foldChannelsInto(input, 1, mono, 3)
        assertEquals(shortArrayOf(5, -7, 100).toList(), mono.toList())
    }

    @Test
    fun `قناتان تتوسط كل زوج — نفس حساب downmix السابق`() {
        val stereo = shortArrayOf(100, 200, 300, 400)
        val mono = ShortArray(2)
        BroadcastTapLogic.foldChannelsInto(stereo, 2, mono, 2)
        assertEquals(150.toShort(), mono[0])
        assertEquals(350.toShort(), mono[1])
    }

    @Test
    fun `أربع قنوات تتوسط القنوات كلها لكل إطار`() {
        val input = shortArrayOf(10, 20, 30, 40, -10, -20, -30, -50)
        val mono = ShortArray(2)
        BroadcastTapLogic.foldChannelsInto(input, 4, mono, 2)
        assertEquals(25.toShort(), mono[0])   // (10+20+30+40)/4
        assertEquals((-27).toShort(), mono[1]) // -110/4 = -27.5 → قسمة صحيحة نحو الصفر
    }

    // ===== sumInto: جمع التابات مع تقييد المدى =====

    @Test
    fun `جمع تابين عاديين بلا تخفيف — كل مصدر بمستواه الحقيقي`() {
        val acc = shortArrayOf(1000, -1000, 0)
        BroadcastTapLogic.sumInto(acc, shortArrayOf(500, -500, 7), 3)
        assertEquals(shortArrayOf(1500, -1500, 7).toList(), acc.toList())
    }

    @Test
    fun `المراكم يجمع فوق ما قبلها — ثلاثة تابات متتالية`() {
        val acc = ShortArray(2)
        BroadcastTapLogic.sumInto(acc, shortArrayOf(10000, 20000), 2)
        BroadcastTapLogic.sumInto(acc, shortArrayOf(10000, 10000), 2)
        BroadcastTapLogic.sumInto(acc, shortArrayOf(5000, 5000), 2)
        assertEquals(25000.toShort(), acc[0])
        assertEquals(32767.toShort(), acc[1]) // 35000 تُقيَّد
    }

    @Test
    fun `اجتماع ذروتين لا يتجاوز السقف الموجب`() {
        val acc = shortArrayOf(30_000)
        BroadcastTapLogic.sumInto(acc, shortArrayOf(10_000), 1)
        assertEquals(32_767.toShort(), acc[0])
    }

    @Test
    fun `اجتماع ذروتين لا يتجاوز الأرضية السالبة`() {
        val acc = shortArrayOf(-30_000)
        BroadcastTapLogic.sumInto(acc, shortArrayOf(-10_000), 1)
        assertEquals((-32_768).toShort(), acc[0])
    }

    // ===== floatToShort: تابات المشغلات العائمة =====

    @Test
    fun `قيمة float وسطى تتحول بنسبة 32767`() {
        assertEquals(16383.toShort(), BroadcastTapLogic.floatToShort(0.5f))
        assertEquals((-16383).toShort(), BroadcastTapLogic.floatToShort(-0.5f))
        assertEquals(0.toShort(), BroadcastTapLogic.floatToShort(0f))
    }

    @Test
    fun `ذروات float خارج المدى تُقيَّد لا تلفّ`() {
        assertEquals(32767.toShort(), BroadcastTapLogic.floatToShort(1.9f))
        assertEquals((-32768).toShort(), BroadcastTapLogic.floatToShort(-1.9f))
    }

    // ===== estimateResampled: تحجيم مخازن المخرج =====

    @Test
    fun `تقدير المخرج يغطي الحاجة دائما مع هامش إطارين`() {
        // ثانية واحدة 44.1kHz → 48kHz = 48000 + هامش
        assertEquals(48_002, BroadcastTapLogic.estimateResampled(44_100, 48_000, 44_100))
        assertEquals(2, BroadcastTapLogic.estimateResampled(44_100, 48_000, 0))
    }

    // ===== LinearResampler: إعادة قلّم دفعات متقطعة =====

    @Test
    fun `قلّم 48 كيلوهرتز إلى نفسه يحافظ على المحتوى والترتيب عبر الدفعات`() {
        val r = LinearResampler(48_000, 48_000)
        val a = shortArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val b = shortArrayOf(9, 10, 11, 12)
        val out = ShortArray(64)
        val tmp = ShortArray(64)
        val n1 = r.resample(a, a.size, out)
        val n2 = r.resample(b, b.size, tmp)
        System.arraycopy(tmp, 0, out, n1, n2)
        val n = n1 + n2
        // الدفعة الأخيرة تحتفظ بإطار حدّي واحد معلّقاً — المحتوى نفسه بلا فقد ولا تكرار
        assertEquals(a.size + b.size - 1, n)
        assertEquals(shortArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11).toList(),
            out.take(n).toList())
    }

    @Test
    fun `قلّم 44100 إلى 48000 دفعات مقطعة يطابق الدفعة الواحدة حرفيا`() {
        // موجة جيبية 1kHz بمعدل المصدر — نفس الحالة الواقعية لمقاطع المكتبة
        val srcRate = 44_100
        val oneSec = 44_100
        val input = ShortArray(oneSec) { i -> (12_000.0 * sin(2 * PI * 1000.0 * i / srcRate)).toInt().toShort() }

        // المسار الأول: دفعة واحدة
        val single = LinearResampler(srcRate, 48_000)
        val outSingle = ShortArray(BroadcastTapLogic.estimateResampled(srcRate, 48_000, oneSec))
        val nSingle = single.resample(input, oneSec, outSingle)

        // المسار الثاني: عشر دفعات 100ms (نمط فك الترميز الحقيقي)
        val chunked = LinearResampler(srcRate, 48_000)
        val outChunked = ShortArray(BroadcastTapLogic.estimateResampled(srcRate, 48_000, oneSec))
        var nChunked = 0
        var off = 0
        val piece = ShortArray(4_410)
        val tmp = ShortArray(BroadcastTapLogic.estimateResampled(srcRate, 48_000, 4_410))
        while (off < oneSec) {
            val len = minOf(4_410, oneSec - off)
            System.arraycopy(input, off, piece, 0, len)
            val got = chunked.resample(piece, len, tmp)
            System.arraycopy(tmp, 0, outChunked, nChunked, got)
            nChunked += got
            off += len
        }

        assertEquals(nSingle, nChunked)
        // ثانية كاملة عند 48kHz ± إطاران حدّيان — صحيح حسابياً بلا انحراف
        assertTrue("عدد المخرج بعيد عن النسبة المتوقعة: $nSingle", kotlin.math.abs(nSingle - 48_000) <= 2)
        // وتطابق حرفي بين المسارين — الحالة العابرة للحدود سليمة
        for (i in 0 until nSingle) {
            assertEquals("انحراف عند العينة $i", outSingle[i], outChunked[i])
        }
    }

    @Test
    fun `قلّم 44100 إلى 48000 يرفع المعدل دون إزاحة زمنية ملموسة`() {
        val srcRate = 44_100
        val n = 4_410 // 100ms
        val input = ShortArray(n) { i -> (10_000.0 * sin(2 * PI * 440.0 * i / srcRate)).toInt().toShort() }
        val r = LinearResampler(srcRate, 48_000)
        val out = ShortArray(BroadcastTapLogic.estimateResampled(srcRate, 48_000, n))
        val written = r.resample(input, n, out)
        // 100ms عند 48kHz = 4800 إطاراً ± حديّان
        assertTrue("مخرج 100ms بعيد عن 4800: $written", kotlin.math.abs(written - 4_800) <= 2)
        // بداية الموجة (صفر) تبقى صفراً — لا إزاحة ولا قفزة عند البدء
        assertTrue(kotlin.math.abs(out[0].toInt()) <= 32)
    }

    @Test
    fun `تخفيض 96000 إلى 48000 يعطي نصف الإطارات بالضبط تقريبا`() {
        val n = 9_600 // 100ms عند 96kHz
        val input = ShortArray(n) { i -> ((i % 200) - 100).toShort() }
        val r = LinearResampler(96_000, 48_000)
        val out = ShortArray(BroadcastTapLogic.estimateResampled(96_000, 48_000, n))
        val written = r.resample(input, n, out)
        assertTrue("مخرج التخفيض بعيد عن النصف: $written", kotlin.math.abs(written - 4_800) <= 2)
        // القيم داخل مدى الدخل — لا تضخيم زائف
        for (i in 0 until written) {
            assertTrue(out[i] >= -100 && out[i] <= 100)
        }
    }

    @Test
    fun `إطاران مخرجان يتشاركان فهرس آخر عينة عند الرفع — لا فهرس سالب أبدا`() {
        // البلاغ الحقيقي v0.55.0: «index=-1 على مصفوفة 2304» — إطار MP3 ستيريو
        // 1152×2 يُطوى مونو فيصل الدفعة 1152 إطاراً وعند الرفع 44.1k→48k قد
        // يتشارك إطارا مخرجَان متتاليان فهرس s0 = آخر عينة الدفعة: الأول يُعلَّق
        // ويُستكمل، والثاني كان يقرأ input[-1]. أضيق إعادة إنتاج: دفعتان من
        // إطار واحد — الثانية كانت ترمي ArrayIndexOutOfBoundsException حرفياً.
        val r = LinearResampler(44_100, 48_000)
        val out1 = ShortArray(4)
        val w1 = r.resample(shortArrayOf(100), 1, out1) // الإطار المخرج 0 يُعلَّق (s1 في الدفعة القادمة)
        assertEquals(0, w1)
        val out2 = ShortArray(4)
        val w2 = r.resample(shortArrayOf(200), 1, out2) // كان ينفجر هنا: input[-1]
        // الإطار 0 اكتمل (موضعه 0 بالضبط = قيمة العينة الأولى حرفياً) والإطار 1
        // (موضعه 0.91875) استُوفى من carryS0 بعينته الأولى في الدفعة الجديدة
        assertEquals(2, w2)
        assertEquals(100.toShort(), out2[0])
        assertEquals(191.toShort(), out2[1]) // 100 + (200-100)×44100/48000 = 191.875
    }

    @Test
    fun `دفعات فك ترميز MP3 الحقيقية 1152 إطارا تطابق الدفعة الواحدة حرفيا`() {
        // نمط البلاغ الحقيقي: فك ترميز MP3 يخرج إطاراً كاملاً كل ~26ms —
        // النسخة السابقة كانت تنفجر في الدفعة التاسعة (offset 9216) ثم تعلق
        // حالة القلّم فيتفقد الأرشيف صوت المشغل كلياً على مقاطع 44.1kHz
        val srcRate = 44_100
        val oneSec = 44_100
        val input = ShortArray(oneSec) { i -> (12_000.0 * sin(2 * PI * 1000.0 * i / srcRate)).toInt().toShort() }

        val single = LinearResampler(srcRate, 48_000)
        val outSingle = ShortArray(BroadcastTapLogic.estimateResampled(srcRate, 48_000, oneSec))
        val nSingle = single.resample(input, oneSec, outSingle)

        val chunked = LinearResampler(srcRate, 48_000)
        val outChunked = ShortArray(BroadcastTapLogic.estimateResampled(srcRate, 48_000, oneSec))
        var nChunked = 0
        var off = 0
        val piece = ShortArray(1_152) // إطار MP3 واحد — نمط فك الترميز الفعلي
        val tmp = ShortArray(BroadcastTapLogic.estimateResampled(srcRate, 48_000, 1_152))
        while (off < oneSec) {
            val len = minOf(1_152, oneSec - off)
            System.arraycopy(input, off, piece, 0, len)
            val got = chunked.resample(piece, len, tmp)
            System.arraycopy(tmp, 0, outChunked, nChunked, got)
            nChunked += got
            off += len
        }
        assertEquals(nSingle, nChunked)
        for (i in 0 until nSingle) {
            assertEquals("انحراف عند العينة $i", outSingle[i], outChunked[i])
        }
    }

    @Test
    fun `الرفع بخطوة نصف إطار بدفعات عينة واحدة ينتج الاستيفاء المتصل الكامل`() {
        // fromRate=1/toRate=2: كل عينة مدخل يليها إطار مخرج عند نصفها — أبسط
        // حالة يظهر فيها تشارك فهرس s0 عبر حد الدفعة في كل مرة بلا استثناء.
        // المتوقع من تيار [10, 20, 30] كدفعة واحدة: 10, 15, 20, 25, 30
        val r = LinearResampler(1, 2)
        val sz = BroadcastTapLogic.estimateResampled(1, 2, 1)
        val acc = ArrayList<Short>()
        fun feed(s: Short) {
            val out = ShortArray(sz)
            val got = r.resample(shortArrayOf(s), 1, out)
            for (i in 0 until got) acc.add(out[i])
        }
        feed(10) // تعليق بلا مخرج — s1 في الدفعة القادمة
        feed(20) // كان ينفجر input[-1] هنا حرفياً
        feed(30)
        feed(30) // إطعام العينة الأخيرة ثانية لإتمام الإطار المعلق (نهاية التيار)
        // الاستيفاء المتصل الحرفي: 10 ثم منتصف 10-20 ثم 20 ثم منتصف 20-30 ثم 30
        assertEquals(shortArrayOf(10, 15, 20, 25, 30).toList(), acc.take(5).toList())
    }

    // ===== MicFrameRing: خزان التابات بين خيوط الصوت والخلط =====

    @Test
    fun `الخزان يحافظ على ترتيب FIFO`() {
        val ring = MicFrameRing(16)
        ring.push(shortArrayOf(1, 2, 3, 4), 4)
        ring.push(shortArrayOf(5, 6), 2)
        val out = ShortArray(6)
        assertEquals(6, ring.pull(out, 6))
        assertEquals(shortArrayOf(1, 2, 3, 4, 5, 6).toList(), out.toList())
    }

    @Test
    fun `الفيض يُبرح الأقدم — آخر 400ms هي محل الثقة`() {
        val ring = MicFrameRing(10)
        ring.push(ShortArray(10) { it.toShort() }, 10) // 0..9
        ring.push(shortArrayOf(10, 11, 12), 3)          // يفيض: 0..2 تُبرح
        val out = ShortArray(10)
        assertEquals(10, ring.pull(out, 10))
        assertEquals((3..12).map { it.toShort() }, out.toList())
    }

    @Test
    fun `السحب من خزان فارغ يعيد صفر بلا استثناء`() {
        val ring = MicFrameRing(8)
        assertEquals(0, ring.pull(ShortArray(8), 8))
        assertEquals(0, ring.count)
    }

    @Test
    fun `سحب جزئي ثم تكملة — نمط دفعات فك الترميز غير المنتظمة`() {
        val ring = MicFrameRing(32)
        ring.push(ShortArray(20) { (it + 1).toShort() }, 20) // 1..20
        val first = ShortArray(8)
        assertEquals(8, ring.pull(first, 8))
        assertEquals(1.toShort(), first[0])
        assertEquals(8.toShort(), first[7])
        val second = ShortArray(20)
        assertEquals(12, ring.pull(second, 20))
        assertEquals(9.toShort(), second[0])
        assertEquals(20.toShort(), second[11])
    }

    @Test
    fun `التفريغ يعيد الخزان للخمول بين جلستي تسجيل`() {
        val ring = MicFrameRing(8)
        ring.push(shortArrayOf(1, 2, 3), 3)
        ring.clear()
        assertEquals(0, ring.count)
        assertEquals(0, ring.pull(ShortArray(8), 8))
        // وبعده يعمل طبيعياً
        ring.push(shortArrayOf(9), 1)
        val out = ShortArray(1)
        assertEquals(1, ring.pull(out, 1))
        assertTrue(out[0] == 9.toShort())
    }
}

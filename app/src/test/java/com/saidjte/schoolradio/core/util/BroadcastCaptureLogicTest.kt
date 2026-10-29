package com.saidjte.schoolradio.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** عقد رياضيات دمج تسجيل البث الكامل — v0.49.0 */
class BroadcastCaptureLogicTest {

    // ===== combine: تخفيف الالتقاط + جمع المايك + تقييد المدى =====

    @Test
    fun `الالتقاط وحده يُخفف بمعامل 0 فاصل 85`() {
        assertEquals(850, BroadcastCaptureLogic.combine(1000, 0))
        assertEquals(-850, BroadcastCaptureLogic.combine(-1000, 0))
    }

    @Test
    fun `مايك بلا التقاط يمر كما هو`() {
        assertEquals(-5, BroadcastCaptureLogic.combine(0, -5))
        assertEquals(777, BroadcastCaptureLogic.combine(0, 777))
    }

    @Test
    fun `اجتماع ذروتين لا يتجاوز السقف الموجب`() {
        // 30000×0.85 + 10000 = 35500 → يُقيَّد
        assertEquals(32_767, BroadcastCaptureLogic.combine(30_000, 10_000))
    }

    @Test
    fun `اجتماع ذروتين لا يتجاوز الأرضية السالبة`() {
        assertEquals(-32_768, BroadcastCaptureLogic.combine(-30_000, -10_000))
    }

    @Test
    fun `الذروة الكاملة للالتقاط وحدها تبقى داخل المدى بلا تقييد`() {
        assertEquals((32_767 * 0.85).toInt(), BroadcastCaptureLogic.combine(32_767, 0))
    }

    @Test
    fun `نسخة Short تعيد قيمة الصحيح نفسها`() {
        assertEquals(
            BroadcastCaptureLogic.combine(1000, 500).toShort(),
            BroadcastCaptureLogic.combineToShort(1000, 500)
        )
    }

    // ===== downmixInto: خفض ستيريو متداخل إلى مونو =====

    @Test
    fun `متوسط كل زوج قناتين يُكتب في المونو`() {
        val stereo = shortArrayOf(100, 200, 300, 400)
        val mono = ShortArray(2)
        BroadcastCaptureLogic.downmixInto(stereo, mono, 2)
        assertEquals(150.toShort(), mono[0])
        assertEquals(350.toShort(), mono[1])
    }

    @Test
    fun `إطار واحد يكفي — لا قراءة بعد نهاية الدفعة`() {
        val stereo = shortArrayOf(9, 3, 999, 999)
        val mono = ShortArray(1)
        BroadcastCaptureLogic.downmixInto(stereo, mono, 1)
        assertEquals(6.toShort(), mono[0])
    }

    // ===== MicFrameRing: خزان المايك بين خيطي البث والالتقاط =====

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
    fun `الفيض يُبرح الأقدم — آخر 100ms هي محل الثقة`() {
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
    fun `سحب جزئي ثم تكملة — نمط دفعات الالتقاط غير المنتظمة`() {
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

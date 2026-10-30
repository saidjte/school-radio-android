package com.saidjte.schoolradio.core.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.40.0 — اختبارات طابور حجز دفعات PCM (عيب إسقاط الدفعات في OpusFileWriter).
 *
 * العقود المختبرة: FIFO صارم، نسخ عند الدفع (المصدر يُعاد استخدامه)،
 * بَرح الأقدم عند الامتلاء مع عدّاد صادق، واحترام طول الدفعة لا سعة المصفوفة.
 */
class PcmChunkQueueTest {

    private fun chunk(vararg samples: Short): ShortArray = samples

    // ===== FIFO + النسخ =====

    @Test
    fun `fifo ترتيب صارم والدفعات تخرج كما دخلت`() {
        val q = PcmChunkQueue(capacity = 4)
        q.push(chunk(1, 2, 3), 3)
        q.push(chunk(4, 5), 2)
        q.push(chunk(6), 1)
        assertEquals(3, q.size)
        assertEquals(3, q.peek()?.length)
        assertEquals(1, q.pop()?.data?.get(0)?.toInt())  // أول عنصر من الدفعة الأولى
        assertEquals(4, q.pop()?.data?.get(0)?.toInt())  // أول عنصر من الدفعة الثانية
        assertEquals(6, q.pop()?.data?.get(0)?.toInt())  // أول عنصر من الدفعة الثالثة
        assertTrue(q.isEmpty)
    }

    @Test
    fun `النسخ عند الدفع — تعديل المصدر لا يفسد المحجوز`() {
        val q = PcmChunkQueue()
        val src = shortArrayOf(10, 20, 30)
        q.push(src, 3)
        // الخيط المصدرو يعيد استخدام المخزن (نمط memoThread الفعلي)
        src[0] = 99
        src[1] = 99
        val out = q.pop()!!
        assertEquals(10, out.data[0].toInt())
        assertEquals(20, out.data[1].toInt())
        assertEquals(30, out.data[2].toInt())
    }

    @Test
    fun `طول الدفعة هو المحتوى الفعلي لا سعة المصفوفة`() {
        val q = PcmChunkQueue()
        val big = ShortArray(1024) { it.toShort() }
        q.push(big, 480) // دفعة 10ms من مخزن أكبر
        val out = q.pop()!!
        assertEquals(480, out.length)
        assertEquals(480, out.data.size) // النسخة بحجم المحتوى الفعلي
        assertEquals(0, out.data[0].toInt())
        assertEquals(479, out.data[479].toInt())
    }

    // ===== الامتلاء والبَرح =====

    @Test
    fun `الامتلاء يُبرح الأقدم ويحفظ ترتيب الباقي مع عدّاد صادق`() {
        val q = PcmChunkQueue(capacity = 3)
        for (v in 1..5) q.push(chunk(v.toShort()), 1)
        assertEquals(3, q.size)          // السعة لم تُتجاوز
        assertEquals(2, q.droppedChunks) // الدفعتان 1 و2 بُرحتا
        assertEquals(3, q.pop()?.data?.get(0)?.toInt())
        assertEquals(4, q.pop()?.data?.get(0)?.toInt())
        assertEquals(5, q.pop()?.data?.get(0)?.toInt())
        assertTrue(q.isEmpty)
    }

    @Test
    fun `دفعة واحدة فوق السعة تُبرح القديم فقط ولا تُسقط الجديد`() {
        val q = PcmChunkQueue(capacity = 1)
        q.push(chunk(1, 1), 2)
        q.push(chunk(2, 2), 2) // تُبرح الدفعة الأولى
        assertEquals(1, q.size)
        assertEquals(1, q.droppedChunks)
        assertEquals(2, q.pop()?.data?.get(0)?.toInt())
    }

    @Test
    fun `السعة الافتراضية تبتلع انشغالات المشفر العابرة بلا أي إسقاط`() {
        // الواقع: 100 دفعة متتالية (ثانية كاملة) والمشفر يستهلك لاحقاً —
        // في الطابور الافتراضي 32 لن يُسقط شيء إلا إذا جاءت 32 دفعة بلا أي استهلاك
        val q = PcmChunkQueue()
        for (v in 1..16) q.push(chunk(v.toShort()), 1)
        assertEquals(0, q.droppedChunks)
        assertEquals(16, q.size)
    }

    // ===== الحالات الحدية =====

    @Test
    fun `السحب من طابور فارغ يعيد null بلا استثناء`() {
        val q = PcmChunkQueue()
        assertNull(q.peek())
        assertNull(q.pop())
    }

    @Test
    fun `دفعات بطول صفر تُرفض بصمت ولا تلوث الطابور`() {
        val q = PcmChunkQueue()
        q.push(chunk(), 0)
        assertTrue(q.isEmpty)
        assertEquals(0, q.droppedChunks)
    }

    @Test
    fun `clear يفرّغ الطابور ويصفّر عدّاد الإسقاط`() {
        val q = PcmChunkQueue(capacity = 1)
        q.push(chunk(1), 1)
        q.push(chunk(2), 1) // بَرح
        assertEquals(1, q.droppedChunks)
        q.clear()
        assertTrue(q.isEmpty)
        assertEquals(0, q.droppedChunks)
    }

    @Test
    fun `سعة أقل من واحد مرفوضة بعقد صريح`() {
        try {
            PcmChunkQueue(capacity = 0)
            throw AssertionError("كان يجب رفض السعة 0")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("سعة"))
        }
    }

    // ===== محاكاة تدفق تسجيل حقيقي =====

    @Test
    fun `محاكاة تسجيل — دفع وإفراغ متبادلان (نمط feed الفعلي) يطابق الترتيب والحجم بلا إسقاط`() {
        val q = PcmChunkQueue()
        val seq = mutableListOf<Short>()
        val drained = mutableListOf<Short>()
        // المنتج: 100 دفعة × 480 عينة (ثانية كاملة من ميكروفون 48kHz)
        // والمستهلك يفرّغ في كل نداء — نفس نمط feed→drainInput الحقيقي
        repeat(100) { block ->
            val c = ShortArray(480) { ((block * 480 + it) % Short.MAX_VALUE).toShort() }
            seq.addAll(c.toList())
            q.push(c, 480)
            while (!q.isEmpty) {
                val p = q.pop()!!
                assertEquals(480, p.length)
                drained.addAll(p.data.toList())
            }
        }
        assertEquals(0, q.droppedChunks) // صفر إسقاط في التدفق الطبيعي
        assertArrayEquals(seq.toShortArray(), drained.toShortArray())
        assertTrue(drained.size == 100 * 480)
    }
}

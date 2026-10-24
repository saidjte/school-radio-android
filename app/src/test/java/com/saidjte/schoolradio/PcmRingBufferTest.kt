package com.saidjte.schoolradio

import com.saidjte.schoolradio.audio.PcmRingBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * اختبارات المخزن الحلقي PCM (v0.13.0) — قلب معالجة التقطيع في البث المباشر.
 * تشغيلها: ./gradlew :app:testDebugUnitTest
 */
class PcmRingBufferTest {

    @Test
    fun `الكتابة والقراءة البسيطة تحفظ ترتيب العينات`() {
        val rb = PcmRingBuffer(100)
        val data = ShortArray(40) { it -> (it * 3).toShort() }
        assertEquals(40, rb.write(data, 0, 40))
        assertEquals(40, rb.available)

        val out = ShortArray(40)
        assertEquals(40, rb.read(out, 0, 40, 0))
        for (i in 0 until 40) assertEquals(data[i], out[i])
        assertEquals(0, rb.available)
    }

    @Test
    fun `الالتفاف حول الحافة يحفظ البيانات`() {
        val rb = PcmRingBuffer(10)
        // نملأ ونفرغ بحجم غير مقسّم على السعة لفرض الالتفاف
        repeat(5) { round ->
            val chunk = ShortArray(7) { ((round * 7) + it).toShort() }
            rb.write(chunk, 0, 7)
            val out = ShortArray(7)
            assertEquals(7, rb.read(out, 0, 7, 0))
            for (i in 0 until 7) assertEquals(((round * 7) + i).toShort(), out[i])
        }
        // بعد 3 جولات (21 عينة على سعة 10) تكون الرؤوس التفتت عدة مرات
        assertEquals(0, rb.available)
    }

    @Test
    fun `الامتلاء يقص الأقدم ويحفظ الأحدث`() {
        val rb = PcmRingBuffer(10)
        val first = ShortArray(8) { it.toShort() }      // 0..7
        val second = ShortArray(8) { (10 + it).toShort() } // 10..17
        rb.write(first, 0, 8)
        rb.write(second, 0, 8) // امتلاء: تقص أول 6 عينات من الأقدم
        assertEquals(10, rb.available)
        assertTrue(rb.droppedShorts > 0)

        val out = ShortArray(10)
        assertEquals(10, rb.read(out, 0, 10, 0))
        // أحدث 10 عينات: 6,7 ثم 10..17
        assertEquals(6.toShort(), out[0])
        assertEquals(7.toShort(), out[1])
        for (i in 2 until 10) assertEquals((10 + i - 2).toShort(), out[i])
    }

    @Test
    fun `قراءة من مخزن فارغ بلا انتظار تعود صفراً فوراً`() {
        val rb = PcmRingBuffer(50)
        val out = ShortArray(10)
        val t0 = System.nanoTime()
        assertEquals(0, rb.read(out, 0, 10, 0))
        assertTrue((System.nanoTime() - t0) < 50_000_000L) // أقل من 50ms
    }

    @Test
    fun `القراءة المنتظرة تفيق عند وصول البيانات`() {
        val rb = PcmRingBuffer(100)
        val latch = CountDownLatch(1)
        val got = AtomicLong(-1)
        Thread {
            val out = ShortArray(10)
            got.set(rb.read(out, 0, 10, 2000).toLong())
            latch.countDown()
        }.start()
        Thread.sleep(80) // المستهلك الآن معلّق في wait
        val chunk = ShortArray(10) { (it + 5).toShort() }
        rb.write(chunk, 0, 10)
        assertTrue(latch.await(2, TimeUnit.SECONDS))
        assertEquals(10L, got.get())
    }

    @Test
    fun `دفعات أضخم من السعة تُختصر إلى آخر السعة`() {
        val rb = PcmRingBuffer(16)
        val big = ShortArray(40) { it.toShort() }
        rb.write(big, 0, 40)
        assertEquals(16, rb.available)
        val out = ShortArray(16)
        assertEquals(16, rb.read(out, 0, 16, 0))
        for (i in 0 until 16) assertEquals((24 + i).toShort(), out[i])
    }

    @Test
    fun `التفريغ يرجع المخزن إلى الصفر`() {
        val rb = PcmRingBuffer(20)
        rb.write(ShortArray(15), 0, 15)
        rb.clear()
        assertEquals(0, rb.available)
        assertEquals(20, rb.freeSpace)
    }

    @Test
    fun `إنتاج ومستهلك متزامنان يتبادلان بلا فقد`() {
        val rb = PcmRingBuffer(9600) // 200ms حقيقية
        val total = 48000L // ثانية واحدة كاملة 48k عينة
        val produced = AtomicLong(0)
        val consumed = AtomicLong(0)
        val sumProd = AtomicLong(0)
        val sumCons = AtomicLong(0)

        val producer = Thread {
            var v = 1L
            val chunk = ShortArray(480)
            var cadence = 0
            while (produced.get() < total) {
                for (i in chunk.indices) {
                    chunk[i] = v.toShort()
                    sumProd.addAndGet(v)
                    v = (v % 30000) + 1
                }
                rb.write(chunk, 0, chunk.size)
                produced.addAndGet(chunk.size.toLong())
                // محاكاة الإيقاع الزمني الحقيقي: في البث الفعلي يكتب المنتج بإيقاع
                // الزمن الحقيقي فيواكبه المستهلك دائماً — بلا هذا التحفظ يسبق المنتج
                // المستهلك بملايين المرات ويُقص الأقدم (سلوك المقص مقصود لكنه ليس موضوع الاختبار)
                if (++cadence % 2 == 0) Thread.sleep(1) // إيقاع أقرب للزمن الحقيقي — الصندوق بطيء المعالج
            }
        }
        val consumer = Thread {
            val out = ShortArray(480)
            var read: Int
            while (consumed.get() < total) {
                read = rb.read(out, 0, out.size, 500)
                if (read > 0) {
                    for (i in 0 until read) sumCons.addAndGet(out[i].toLong())
                    consumed.addAndGet(read.toLong())
                }
            }
        }
        producer.start(); consumer.start()
        producer.join(10000); consumer.join(10000)

        assertEquals(total, produced.get())
        assertEquals(total, consumed.get())
        assertEquals(sumProd.get(), sumCons.get()) // كل عينة مكتوبة قُرئت مرة واحدة وبنفس القيمة
    }
}

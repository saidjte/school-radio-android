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

    // ===== v0.45.0: التأسيس awaitFill — خاتمة التقطيع (الإخراج يُفتح على رصيد لا على خزان خاوٍ) =====

    @Test
    fun `التأسيس يعود فوراً عندما يكون الرصيد متوفراً`() {
        val rb = PcmRingBuffer(100)
        rb.write(ShortArray(60), 0, 60)
        val t0 = System.nanoTime()
        assertTrue(rb.awaitFill(50, 10_000))
        assertTrue((System.nanoTime() - t0) < 50_000_000L) // أقل من 50ms — بلا انتظار
    }

    @Test
    fun `التأسيس يفشل صادقاً عند انتهاء المهلة دون الرصيد`() {
        val rb = PcmRingBuffer(100)
        rb.write(ShortArray(10), 0, 10) // أقل من المطلوب
        val t0 = System.nanoTime()
        assertEquals(false, rb.awaitFill(50, 60))
        // انتظر المدة المطلوبة فعلاً ثم انصرف (لا نجاح خادع)
        assertTrue((System.nanoTime() - t0) >= 50_000_000L)
        // والمخزن لم يُمس
        assertEquals(10, rb.available)
    }

    @Test
    fun `التأسيس المنتظر يفيق عند وصول البيانات`() {
        val rb = PcmRingBuffer(100)
        val latch = CountDownLatch(1)
        val got = AtomicLong(-1)
        Thread {
            got.set(if (rb.awaitFill(10, 2000)) 1L else 0L)
            latch.countDown()
        }.start()
        Thread.sleep(80) // الخيط الآن معلّق في wait
        rb.write(ShortArray(12), 0, 12)
        assertTrue(latch.await(2, TimeUnit.SECONDS))
        assertEquals(1L, got.get())
    }

    @Test
    fun `التأسيس ينجح متى بلغ الرصيد الهدف ولو بعد انتظار`() {
        // إنتاج أبطأ من الهدف: دفعة كل 30ms حتى 40 عينة — الهدف 30
        val rb = PcmRingBuffer(100)
        val writer = Thread {
            repeat(4) {
                rb.write(ShortArray(10) { ((it + 1)).toShort() }, 0, 10)
                Thread.sleep(30)
            }
        }
        writer.start()
        assertTrue(rb.awaitFill(30, 2000))
        writer.join(2000)
        assertEquals(40, rb.available)
    }

    @Test
    fun `التأسيس برصيد صفر أو سالب ينجح فوراً بلا انتظار`() {
        val rb = PcmRingBuffer(10)
        val t0 = System.nanoTime()
        assertTrue(rb.awaitFill(0, 10_000))
        assertTrue(rb.awaitFill(-5, 10_000))
        assertTrue((System.nanoTime() - t0) < 20_000_000L)
    }

    @Test
    fun `إنتاج ومستهلك متزامنان يتبادلان بلا فقد`() {
        // v0.16.0: السعة = إجمالي الإنتاج ⇒ استحالة القص رياضياً (المقص موضوع
        // اختبارات أخرى) — أي جوع للمستهلك تحت حمل الاختبارات المتوازية
        // لن يفسد التطابق، والاختبار يبقى تحققاً تزامنياً كاملاً (FIFO سليم وقيم مطابقة)
        val rb = PcmRingBuffer(50000)
        val total = 48000L // ثانية واحدة كاملة 48k عينة
        val produced = AtomicLong(0)
        val consumed = AtomicLong(0)
        val sumProd = AtomicLong(0)
        val sumCons = AtomicLong(0)

        val producer = Thread {
            var v = 1L
            val chunk = ShortArray(480)
            while (produced.get() < total) {
                for (i in chunk.indices) {
                    chunk[i] = v.toShort()
                    sumProd.addAndGet(v)
                    v = (v % 30000) + 1
                }
                rb.write(chunk, 0, chunk.size)
                produced.addAndGet(chunk.size.toLong())
                // تنفس طفيف يسمح بتداخل حقيقي بين الخيطين (ليس شرطاً للصحة بعد رفع السعة)
                Thread.yield()
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
        producer.join(15000); consumer.join(15000)

        assertEquals(total, produced.get())
        assertEquals(total, consumed.get())
        assertEquals(sumProd.get(), sumCons.get()) // كل عينة مكتوبة قُرئت مرة واحدة وبنفس القيمة
    }
}

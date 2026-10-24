package com.saidjte.schoolradio

import com.saidjte.schoolradio.core.util.BreaksLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * اختبارات منطق الفواصل المجدولة (v0.12.0) — JVM خالص.
 * تُثبت القواعد الإذاعية: فاصل بين الفقرات فقط، لا تكرار مباشر،
 * سقوط آمن عند غياب الجينغل، وقرار «فقرة تالية اليوم» الصحيح.
 */
class BreaksLogicTest {

    private fun spec(id: Long, title: String = "مقطع $id") =
        BreaksLogic.PlaySpec("content://track/$id", title, id)

    // ===== hasNextSegmentToday =====

    @Test
    fun `توجد فقرة تالية اليوم عند حدث لاحق بنفس اليوم`() {
        val events = listOf(
            BreaksLogic.EventTiming(1, 9 * 60, 0b0111111),     // الاثنين..السبت 09:00
            BreaksLogic.EventTiming(2, 9 * 60 + 30, 0b0111111) // 09:30
        )
        // الاثنين = bit 1، الآن 09:05
        assertTrue(BreaksLogic.hasNextSegmentToday(events, 1, dayBit = 1, minutesNow = 9 * 60 + 5))
    }

    @Test
    fun `الفقرة الختامية اليوم لا تليها فقرة فلا فاصل`() {
        val events = listOf(
            BreaksLogic.EventTiming(1, 9 * 60, 0b0111111),
            BreaksLogic.EventTiming(2, 9 * 60 + 30, 0b0111111)
        )
        // بعد آخر فقرة اليوم (10:00)
        assertFalse(BreaksLogic.hasNextSegmentToday(events, 2, dayBit = 1, minutesNow = 10 * 60))
    }

    @Test
    fun `فقرة يوم آخر لا تُحتسب فاصلاً لليوم الحالي`() {
        val events = listOf(
            BreaksLogic.EventTiming(1, 9 * 60, 0b0000001), // الاثنين فقط
            BreaksLogic.EventTiming(2, 9 * 60 + 30, 0b0000010) // الثلاثاء فقط
        )
        // يوم الاثنين — فقرة الثلاثاء لا تُحتسب
        assertFalse(BreaksLogic.hasNextSegmentToday(events, 1, dayBit = 1, minutesNow = 9 * 60))
    }

    @Test
    fun `الفقرة المتزامنة نفس الدقيقة تُحتسب تالية — فاصل بينها`() {
        val events = listOf(
            BreaksLogic.EventTiming(1, 9 * 60, 0b0111111),
            BreaksLogic.EventTiming(2, 9 * 60, 0b0111111)
        )
        assertTrue(BreaksLogic.hasNextSegmentToday(events, 1, dayBit = 1, minutesNow = 9 * 60))
    }

    // ===== buildQueue =====

    @Test
    fun `قائمة معطلة تُدمج متسلسلة بلا فواصل`() {
        val segments = listOf(listOf(spec(1)), listOf(spec(2)), listOf(spec(3)))
        val queue = BreaksLogic.buildQueue(
            segments,
            BreaksLogic.BreakConfig(enabled = false, trackId = 9),
            breakPool = listOf(spec(9))
        )
        assertEquals(listOf(1L, 2L, 3L), queue.map { it.trackId })
    }

    @Test
    fun `جينغل محدد يُدرج بين الفقرات فقط وبعد الأخيرة لا شيء`() {
        val segments = listOf(
            listOf(spec(1), spec(11)),
            listOf(spec(2)),
            listOf(spec(3))
        )
        val queue = BreaksLogic.buildQueue(
            segments,
            BreaksLogic.BreakConfig(enabled = true, trackId = 9),
            breakPool = listOf(spec(9), spec(8))
        )
        assertEquals(listOf(1L, 11L, 9L, 2L, 9L, 3L), queue.map { it.trackId })
    }

    @Test
    fun `الفقرة الفارغة تُتخطى بلا فاصل يتيم قبلها`() {
        val segments = listOf(
            listOf(spec(1)),
            emptyList(), // فقرة محتواها غير متاح
            listOf(spec(3))
        )
        val queue = BreaksLogic.buildQueue(
            segments,
            BreaksLogic.BreakConfig(enabled = true, trackId = 9),
            breakPool = listOf(spec(9))
        )
        // فقرة1 ← فاصل ← فقرة3 — لا فاصل مزدوج ولا يتيم
        assertEquals(listOf(1L, 9L, 3L), queue.map { it.trackId })
    }

    @Test
    fun `تجمع فارغ يسقط الفواصل بهدوء ويبقي الفقرات`() {
        val segments = listOf(listOf(spec(1)), listOf(spec(2)))
        val queue = BreaksLogic.buildQueue(
            segments,
            BreaksLogic.BreakConfig(enabled = true, categoryId = 4),
            breakPool = emptyList()
        )
        assertEquals(listOf(1L, 2L), queue.map { it.trackId })
    }

    @Test
    fun `الاختيار العشوائي من تصنيف لا يكرر نفس الجينغل مرتين متتاليتين`() {
        val pool = listOf(spec(1), spec(2), spec(3))
        val segments = (1..4).map { listOf(spec(100 + it.toLong())) }
        val queue = BreaksLogic.buildQueue(
            segments,
            BreaksLogic.BreakConfig(enabled = true, categoryId = 4),
            breakPool = pool,
            rng = Random(42)
        )
        val breaks = queue.filter { it.trackId in 1..3L }.map { it.trackId }
        assertEquals(3, breaks.size) // 4 فقرات = 3 فواصل
        // لا تكرار متتالٍ
        breaks.zipWithNext().forEach { (a, b) -> assertTrue(a != b) }
    }

    @Test
    fun `تجمع من عنصر واحد يُقبل تكراره — بديل وحيد`() {
        val segments = listOf(listOf(spec(1)), listOf(spec(2)), listOf(spec(3)))
        val queue = BreaksLogic.buildQueue(
            segments,
            BreaksLogic.BreakConfig(enabled = true, categoryId = 4),
            breakPool = listOf(spec(9)),
            rng = Random(7)
        )
        assertEquals(listOf(1L, 9L, 2L, 9L, 3L), queue.map { it.trackId })
    }

    // ===== resolveBreakSpec =====

    @Test
    fun `الإعداد المعطل أو غير المضبوط يعيد null`() {
        val pool = listOf(spec(9))
        assertNull(BreaksLogic.resolveBreakSpec(BreaksLogic.BreakConfig(false, trackId = 9), pool))
        assertNull(BreaksLogic.resolveBreakSpec(BreaksLogic.BreakConfig(true), pool))
    }

    @Test
    fun `المقطع المحدد له الأولوية على التصنيف`() {
        val pool = listOf(spec(9), spec(8))
        val resolved = BreaksLogic.resolveBreakSpec(
            BreaksLogic.BreakConfig(true, trackId = 9, categoryId = 4),
            pool,
            rng = Random(1)
        )
        assertEquals(9L, resolved?.trackId)
    }

    @Test
    fun `المقطع المحدد المفقود من التجمع يعيد null — سقوط آمن`() {
        val pool = listOf(spec(8))
        assertNull(
            BreaksLogic.resolveBreakSpec(
                BreaksLogic.BreakConfig(true, trackId = 9),
                pool
            )
        )
    }

    // ===== breaksCountFor =====

    @Test
    fun `عدد الفواصل بين الفقرات = الفقرات ناقص واحدة`() {
        assertEquals(0, BreaksLogic.breaksCountFor(1))
        assertEquals(1, BreaksLogic.breaksCountFor(2))
        assertEquals(4, BreaksLogic.breaksCountFor(5))
        assertEquals(0, BreaksLogic.breaksCountFor(0))
    }

    // ===== v0.15.0: buildQueueTagged + مهلة الانتقال + نمط كل مقطع =====

    @Test
    fun `القائمة الموسومة تميز الفواصل من الفقرات`() {
        val segments = listOf(listOf(spec(1), spec(2)), listOf(spec(3)))
        val tagged = BreaksLogic.buildQueueTagged(
            segments,
            BreaksLogic.BreakConfig(enabled = true, trackId = 9),
            breakPool = listOf(spec(9))
        )
        // فقرة1، فقرة2، جينل، فقرة3 — الفاصل موسوم والبقية لا
        assertEquals(listOf(1L, 2L, 9L, 3L), tagged.map { it.spec.trackId })
        assertEquals(listOf(false, false, true, false), tagged.map { it.isBreak })
    }

    @Test
    fun `نمط كل مقطع يدرج جينلاً بين كل مقطعين متتاليين عبر الفقرات`() {
        val segments = listOf(listOf(spec(1), spec(2)), listOf(spec(3)))
        val tagged = BreaksLogic.buildQueueTagged(
            segments,
            BreaksLogic.BreakConfig(enabled = true, trackId = 9, everyTrack = true),
            breakPool = listOf(spec(9))
        )
        assertEquals(listOf(1L, 9L, 2L, 9L, 3L), tagged.map { it.spec.trackId })
        assertEquals(listOf(false, true, false, true, false), tagged.map { it.isBreak })
    }

    @Test
    fun `نمط كل مقطع يعمل مع الاختيار العشوائي من تصنيف`() {
        val segments = listOf(listOf(spec(1), spec(2)))
        val tagged = BreaksLogic.buildQueueTagged(
            segments,
            BreaksLogic.BreakConfig(enabled = true, categoryId = 4, everyTrack = true),
            breakPool = listOf(spec(9), spec(8)),
            rng = Random(3)
        )
        // مقطعان + جينل واحد بينهما = 3 عناصر
        assertEquals(3, tagged.size)
        assertTrue(tagged[0].spec.trackId == 1L && !tagged[0].isBreak)
        assertTrue(tagged[1].isBreak)
        assertTrue(tagged[2].spec.trackId == 2L && !tagged[2].isBreak)
    }

    @Test
    fun `الفواصل المعطلة تنتج قائمة موسومة بلا فواصل`() {
        val segments = listOf(listOf(spec(1)), listOf(spec(2)))
        val tagged = BreaksLogic.buildQueueTagged(
            segments,
            BreaksLogic.BreakConfig(enabled = false, trackId = 9),
            breakPool = listOf(spec(9))
        )
        assertEquals(listOf(1L, 2L), tagged.map { it.spec.trackId })
        assertTrue(tagged.none { it.isBreak })
    }

    @Test
    fun `مهلة الانتقال تتحول إلى ميلي ثانية محصورة`() {
        assertEquals(0L, BreaksLogic.BreakConfig(transitionSec = 0).transitionMs)
        assertEquals(12_000L, BreaksLogic.BreakConfig(transitionSec = 12).transitionMs)
        // سقف الأمان 300 ثانية
        assertEquals(300_000L, BreaksLogic.BreakConfig(transitionSec = 9999).transitionMs)
        assertEquals(0L, BreaksLogic.BreakConfig(transitionSec = -5).transitionMs)
    }

    @Test
    fun `عدد الفواصل في نمط كل مقطع يُحسب من المقاطع لا الفقرات`() {
        // 3 فقرات لكن 5 مقاطع فعلياً
        assertEquals(2, BreaksLogic.breaksCountFor(3, filledTracks = 5, everyTrack = false))
        assertEquals(4, BreaksLogic.breaksCountFor(3, filledTracks = 5, everyTrack = true))
    }
}

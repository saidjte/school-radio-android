package com.saidjte.schoolradio.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.42.0 — اختبارات خطة التنزيل المجزأ المتوازي: قرارات التقسيم، حدود
 * الشرائح بلا فراغات ولا تداخل، ترويسة Range، التجميع، وتحقق النزاهة.
 */
class ParallelDownloadLogicTest {

    // ================== قرار التقسيم ==================

    @Test fun `plan is null without range support`() {
        assertNull(ParallelDownloadLogic.plan(50_000_000L, acceptRanges = false))
    }

    @Test fun `plan is null for unknown size`() {
        assertNull(ParallelDownloadLogic.plan(-1L, acceptRanges = true))
        assertNull(ParallelDownloadLogic.plan(0L, acceptRanges = true))
    }

    @Test fun `plan is null below the worthwhile threshold`() {
        assertNull(ParallelDownloadLogic.plan(ParallelDownloadLogic.MIN_PARALLEL_BYTES - 1, true))
    }

    @Test fun `plan works at exact threshold`() {
        val plan = ParallelDownloadLogic.plan(ParallelDownloadLogic.MIN_PARALLEL_BYTES, true)
        assertEquals(ParallelDownloadLogic.SEGMENT_COUNT, plan!!.size)
    }

    // ================== بنية الشرائح ==================

    @Test fun `plan segments are contiguous and cover the whole file`() {
        val total = 10_000_001L // غير قابل للقسمة على 6 — حدود الشريحة الأخيرة حرجة
        val plan = ParallelDownloadLogic.plan(total, true)!!
        assertEquals(ParallelDownloadLogic.SEGMENT_COUNT, plan.size)
        assertEquals(0L, plan.first().start)
        assertEquals(total - 1, plan.last().endInclusive)
        for (i in 0 until plan.size - 1) {
            assertEquals(plan[i].endInclusive + 1, plan[i + 1].start)
        }
        assertEquals(total, plan.sumOf { it.length })
    }

    @Test fun `plan equal segments for divisible size`() {
        val total = 6_000_000L
        val plan = ParallelDownloadLogic.plan(total, true)!!
        plan.forEach { assertEquals(total / 6, it.length) }
        assertEquals(0L, plan[0].start)
        assertEquals(999_999L, plan[0].endInclusive)
        assertEquals(5_000_000L, plan[5].start)
        assertEquals(5_999_999L, plan[5].endInclusive)
    }

    @Test fun `segment indexes are ordered`() {
        val plan = ParallelDownloadLogic.plan(7_777_777L, true)!!
        plan.forEachIndexed { i, seg -> assertEquals(i, seg.index) }
    }

    // ================== ترويسة Range ==================

    @Test fun `range header follows rfc 7233`() {
        val seg = ParallelDownloadLogic.Segment(2, 1_000_000L, 1_999_999L)
        assertEquals("bytes=1000000-1999999", ParallelDownloadLogic.rangeHeader(seg))
    }

    // ================== التجميع والنزاهة ==================

    @Test fun `aggregate sums live segment counters`() {
        assertEquals(450L, ParallelDownloadLogic.aggregate(longArrayOf(100, 50, 200, 100)))
        assertEquals(0L, ParallelDownloadLogic.aggregate(longArrayOf(0, 0, 0)))
    }

    @Test fun `completeness verifies plan size against downloaded total`() {
        val plan = ParallelDownloadLogic.plan(6_000_000L, true)!!
        assertTrue(ParallelDownloadLogic.isComplete(plan, 6_000_000L))
        // شريحة قُطعت مبكراً — نقص 1 بايت يبطل الملف كله
        assertFalse(ParallelDownloadLogic.isComplete(plan, 5_999_999L))
        // بايت زائد عن المخطط مستحيل لكن التحقق صادق معه أيضاً
        assertFalse(ParallelDownloadLogic.isComplete(plan, 6_000_001L))
    }
}

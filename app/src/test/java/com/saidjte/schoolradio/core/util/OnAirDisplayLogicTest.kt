package com.saidjte.schoolradio.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.24.0 — اختبارات منطق عناصر شاشة العرض الجديدة:
 * الفقرات القادمة (من القائمة والجدول) + مؤقت ترتيل المذيع + مؤشر VU.
 */
class OnAirDisplayLogicTest {

    // ================== 1) القادم من قائمة التشغيل ==================

    @Test fun `queue upcoming drops played and current`() {
        val titles = listOf("افتتاحية", "قرآن", "خبر اليوم", "ناشئة", "ختام")
        assertEquals(
            listOf("خبر اليوم", "ناشئة", "ختام"),
            OnAirDisplayLogic.upcomingQueueTitles(titles, 1)
        )
    }

    @Test fun `queue upcoming respects limit`() {
        val titles = listOf("أ", "ب", "ج", "د", "هـ", "و")
        assertEquals(
            listOf("ب", "ج"),
            OnAirDisplayLogic.upcomingQueueTitles(titles, 0, limit = 2)
        )
    }

    @Test fun `queue upcoming with no current starts from beginning`() {
        val titles = listOf("أ", "ب")
        assertEquals(listOf("أ", "ب"), OnAirDisplayLogic.upcomingQueueTitles(titles, -1))
    }

    @Test fun `queue upcoming empty list`() {
        assertTrue(OnAirDisplayLogic.upcomingQueueTitles(emptyList(), 0).isEmpty())
    }

    @Test fun `queue upcoming when current is last is empty`() {
        val titles = listOf("أ", "ب")
        assertTrue(OnAirDisplayLogic.upcomingQueueTitles(titles, 1).isEmpty())
    }

    @Test fun `queue upcoming with zero or negative limit is empty`() {
        val titles = listOf("أ", "ب", "ج")
        assertTrue(OnAirDisplayLogic.upcomingQueueTitles(titles, 0, limit = 0).isEmpty())
        assertTrue(OnAirDisplayLogic.upcomingQueueTitles(titles, 0, limit = -3).isEmpty())
    }

    // ================== 2) القادم من جدول اليوم ==================

    private fun ev(title: String, start: Int) = OnAirDisplayLogic.UpcomingEvent(title, start)

    @Test fun `upcoming events filters past and current minute`() {
        val events = listOf(ev("القادم", 610), ev("الجاري", 600), ev("الماضي", 590))
        assertEquals(
            listOf("القادم"),
            OnAirDisplayLogic.upcomingEvents(events, 600).map { it.title }
        )
    }

    @Test fun `upcoming events sorted by start minute`() {
        val events = listOf(ev("ثالث", 800), ev("أول", 610), ev("ثاني", 700))
        assertEquals(
            listOf("أول", "ثاني", "ثالث"),
            OnAirDisplayLogic.upcomingEvents(events, 600).map { it.title }
        )
    }

    @Test fun `upcoming events respects limit of three`() {
        val events = listOf(
            ev("أ", 601), ev("ب", 602), ev("ج", 603), ev("د", 604), ev("هـ", 605)
        )
        assertEquals(3, OnAirDisplayLogic.upcomingEvents(events, 600).size)
    }

    @Test fun `upcoming events empty when all passed`() {
        val events = listOf(ev("قديم", 100), ev("أقدم", 90))
        assertTrue(OnAirDisplayLogic.upcomingEvents(events, 600).isEmpty())
    }

    // ================== 3) مؤقت ترتيل المذيع ==================

    @Test fun `pacing elapsed with no start is zero`() {
        assertEquals(0L, OnAirDisplayLogic.pacingElapsedSec(null, 100_000L))
    }

    @Test fun `pacing elapsed guards clock rollback`() {
        assertEquals(0L, OnAirDisplayLogic.pacingElapsedSec(50_000L, 49_999L))
    }

    @Test fun `pacing elapsed counts whole seconds`() {
        assertEquals(63L, OnAirDisplayLogic.pacingElapsedSec(1000L, 64_500L))
        assertEquals(0L, OnAirDisplayLogic.pacingElapsedSec(1000L, 1999L))
    }

    @Test fun `pacing urgency thresholds`() {
        assertEquals(AirCountdown.Urgency.NORMAL, OnAirDisplayLogic.pacingUrgency(0L))
        assertEquals(AirCountdown.Urgency.NORMAL, OnAirDisplayLogic.pacingUrgency(59L))
        assertEquals(AirCountdown.Urgency.SOON, OnAirDisplayLogic.pacingUrgency(60L))
        assertEquals(AirCountdown.Urgency.SOON, OnAirDisplayLogic.pacingUrgency(119L))
        assertEquals(AirCountdown.Urgency.FINAL, OnAirDisplayLogic.pacingUrgency(120L))
        assertEquals(AirCountdown.Urgency.FINAL, OnAirDisplayLogic.pacingUrgency(999L))
    }

    // ================== 4) مؤشر VU ==================

    @Test fun `peak hold rises instantly with louder level`() {
        assertEquals(0.8f, OnAirDisplayLogic.peakHold(0.5f, 0.8f), 1e-6f)
    }

    @Test fun `peak hold decays slowly when quieter`() {
        assertEquals(0.485f, OnAirDisplayLogic.peakHold(0.5f, 0.2f), 1e-6f)
    }

    @Test fun `peak hold never goes below zero`() {
        assertEquals(0f, OnAirDisplayLogic.peakHold(0.005f, 0f), 1e-6f)
    }

    @Test fun `peak hold clamps level above one`() {
        assertEquals(1f, OnAirDisplayLogic.peakHold(0.2f, 7f), 1e-6f)
    }

    @Test fun `vu segments bounds`() {
        assertEquals(0, OnAirDisplayLogic.vuSegments(0f))
        assertEquals(20, OnAirDisplayLogic.vuSegments(1f))
        assertEquals(20, OnAirDisplayLogic.vuSegments(5f)) // خارج النطاق يُقيَّد
        assertEquals(0, OnAirDisplayLogic.vuSegments(-1f))
    }

    @Test fun `vu segments scales with level`() {
        assertEquals(10, OnAirDisplayLogic.vuSegments(0.5f))
        assertEquals(1, OnAirDisplayLogic.vuSegments(0.05f))
        assertEquals(7, OnAirDisplayLogic.vuSegments(0.35f, segments = 20))
        assertEquals(0, OnAirDisplayLogic.vuSegments(0.9f, segments = 0))
    }

    @Test fun `vu zone colors follow classic layout`() {
        // 20 شريحة: 0..14 خضراء، 15..17 كهرمانية، 18..19 حمراوان
        assertEquals(AirCountdown.Urgency.NORMAL, OnAirDisplayLogic.vuZoneColor(0, 20))
        assertEquals(AirCountdown.Urgency.NORMAL, OnAirDisplayLogic.vuZoneColor(14, 20))
        assertEquals(AirCountdown.Urgency.SOON, OnAirDisplayLogic.vuZoneColor(15, 20))
        assertEquals(AirCountdown.Urgency.SOON, OnAirDisplayLogic.vuZoneColor(17, 20))
        assertEquals(AirCountdown.Urgency.FINAL, OnAirDisplayLogic.vuZoneColor(18, 20))
        assertEquals(AirCountdown.Urgency.FINAL, OnAirDisplayLogic.vuZoneColor(19, 20))
    }
}

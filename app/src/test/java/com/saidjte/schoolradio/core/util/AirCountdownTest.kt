package com.saidjte.schoolradio.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * اختبارات وحدة المؤقت التنازلي للفقرة على شاشة العرض (v0.14.0) — منطق صافٍ.
 */
class AirCountdownTest {

    /** لحظة معروفة: 2026-10-24 09:45:30 محلياً */
    private fun millisAt(hour: Int, minute: Int, second: Int): Long =
        Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 24, hour, minute, second)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    // ===== secondsOfDay =====

    @Test fun `secondsOfDay morning`() {
        assertEquals(9 * 3600L + 45 * 60L + 30L, AirCountdown.secondsOfDay(millisAt(9, 45, 30)))
    }

    @Test fun `secondsOfDay midnight`() {
        assertEquals(0L, AirCountdown.secondsOfDay(millisAt(0, 0, 0)))
    }

    // ===== secondsUntil =====

    @Test fun `secondsUntil future event counts down`() {
        // الآن 09:45:30 — الفقرة 10:00 → 14 دقيقة و30 ثانية
        assertEquals(14 * 60L + 30L, AirCountdown.secondsUntil(600, millisAt(9, 45, 30)))
    }

    @Test fun `secondsUntil exact moment is zero`() {
        assertEquals(0L, AirCountdown.secondsUntil(600, millisAt(10, 0, 0)))
    }

    @Test fun `secondsUntil passed event never negative`() {
        assertEquals(0L, AirCountdown.secondsUntil(585, millisAt(10, 0, 0)))
    }

    // ===== remainingMs =====

    @Test fun `remainingMs subtracts position`() {
        assertEquals(35_000L, AirCountdown.remainingMs(65_000, 100_000))
    }

    @Test fun `remainingMs never negative at end`() {
        assertEquals(0L, AirCountdown.remainingMs(120_000, 100_000))
    }

    // ===== mmss بأرقام مغربية دائماً =====

    @Test fun `mmss under hour`() {
        assertEquals("05:09", AirCountdown.mmss(5 * 60L + 9))
    }

    @Test fun `mmss over hour includes hours`() {
        assertEquals("1:02:07", AirCountdown.mmss(3600L + 2 * 60L + 7))
    }

    @Test fun `mmss zero`() {
        assertEquals("00:00", AirCountdown.mmss(0))
    }

    @Test fun `mmss negative coerces to zero`() {
        assertEquals("00:00", AirCountdown.mmss(-5000))
    }

    @Test fun `mmss output has no eastern digits`() {
        val s = AirCountdown.mmss(75 * 60L + 41)
        assertTrue("يجب أن تكون الأرقام مغربية: $s", s.none { it in '٠'..'٩' })
        assertTrue(s.matches(Regex("[0-9:]+")))
    }

    // ===== urgency =====

    @Test fun `urgency normal while far from end`() {
        assertEquals(AirCountdown.Urgency.NORMAL, AirCountdown.urgency(120_000, true))
    }

    @Test fun `urgency soon in last minute`() {
        assertEquals(AirCountdown.Urgency.SOON, AirCountdown.urgency(30_000, true))
    }

    @Test fun `urgency final in last 15 seconds`() {
        assertEquals(AirCountdown.Urgency.FINAL, AirCountdown.urgency(10_000, true))
    }

    @Test fun `urgency stays normal when paused`() {
        assertEquals(AirCountdown.Urgency.NORMAL, AirCountdown.urgency(10_000, false))
    }
}

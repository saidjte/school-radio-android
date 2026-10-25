package com.saidjte.schoolradio.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.23.0 — اختبارات منطق عدّ الفواصل بين الفقرات على شاشة العرض.
 */
class BreakCountdownTest {

    // ================== تحديد المرحلة ==================

    @Test fun `no signals means no break`() {
        assertEquals(
            BreakCountdown.Phase.NONE,
            BreakCountdown.phase(
                onAirIsBreak = false, currentIsBreak = false,
                breakStartsAtMs = null, nowMs = 1000L
            )
        )
    }

    @Test fun `transition deadline in future is waiting`() {
        assertEquals(
            BreakCountdown.Phase.WAITING,
            BreakCountdown.phase(
                onAirIsBreak = true, currentIsBreak = false,
                breakStartsAtMs = 10_000L, nowMs = 4_000L
            )
        )
    }

    @Test fun `transition deadline passed falls to playing`() {
        assertEquals(
            BreakCountdown.Phase.PLAYING,
            BreakCountdown.phase(
                onAirIsBreak = true, currentIsBreak = false,
                breakStartsAtMs = 10_000L, nowMs = 10_000L
            )
        )
    }

    @Test fun `onair flag alone activates playing`() {
        assertEquals(
            BreakCountdown.Phase.PLAYING,
            BreakCountdown.phase(
                onAirIsBreak = true, currentIsBreak = false,
                breakStartsAtMs = null, nowMs = 1L
            )
        )
    }

    @Test fun `tagged player item alone activates playing`() {
        // جينلات القوائم الموسومة (breaks:) بلا معلومات على الهواء — العد من وسم المشغل
        assertEquals(
            BreakCountdown.Phase.PLAYING,
            BreakCountdown.phase(
                onAirIsBreak = false, currentIsBreak = true,
                breakStartsAtMs = null, nowMs = 1L
            )
        )
    }

    // ================== عدّ مهلة الانتقال ==================

    @Test fun `wait seconds rounds up to show full first second`() {
        assertEquals(10L, BreakCountdown.waitSecondsLeft(10_500L, 1_000L)) // 9.5 ث → 10
        assertEquals(1L, BreakCountdown.waitSecondsLeft(1_001L, 1_000L))
        assertEquals(0L, BreakCountdown.waitSecondsLeft(1_000L, 1_000L))
        assertEquals(0L, BreakCountdown.waitSecondsLeft(500L, 1_000L)) // فات الموعد
    }

    // ================== إلحاح عدّ الفاصل ==================

    @Test fun `break urgency thresholds are tighter than segments`() {
        // آخر 5 ثوانٍ بالضبط → أحمر
        assertEquals(AirCountdown.Urgency.FINAL, BreakCountdown.urgency(5_000L, true))
        assertEquals(AirCountdown.Urgency.FINAL, BreakCountdown.urgency(3_000L, true))
        // 6–15 ثانية → كهرماني
        assertEquals(AirCountdown.Urgency.SOON, BreakCountdown.urgency(6_000L, true))
        assertEquals(AirCountdown.Urgency.SOON, BreakCountdown.urgency(15_000L, true))
        // أعلى من 15 ثانية → عادي
        assertEquals(AirCountdown.Urgency.NORMAL, BreakCountdown.urgency(15_001L, true))
    }

    @Test fun `break urgency is normal when paused or zero`() {
        assertEquals(AirCountdown.Urgency.NORMAL, BreakCountdown.urgency(5_000L, false))
        assertEquals(AirCountdown.Urgency.NORMAL, BreakCountdown.urgency(0L, true))
        assertEquals(AirCountdown.Urgency.NORMAL, BreakCountdown.urgency(-1L, true))
    }

    // ================== حواجز سلامة عامة ==================

    @Test fun `waiting takes priority over playing flag`() {
        // موعد انطلاق في المستقبل مع وسم جينل حالي — الانتظار أخص فله الأولوية
        assertEquals(
            BreakCountdown.Phase.WAITING,
            BreakCountdown.phase(
                onAirIsBreak = true, currentIsBreak = true,
                breakStartsAtMs = 99_999L, nowMs = 1_000L
            )
        )
    }

    @Test fun `mmss formats transition times with western digits`() {
        val t = AirCountdown.mmss(BreakCountdown.waitSecondsLeft(11_000L, 1_000L))
        assertEquals("00:10", t)
        assertFalse(t.contains("٠")) // لا أرقام مشرقية — قاعدة v0.6.0
    }
}

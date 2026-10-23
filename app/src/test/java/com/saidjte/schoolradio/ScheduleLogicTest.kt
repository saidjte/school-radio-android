package com.saidjte.schoolradio

import com.saidjte.schoolradio.core.util.ScheduleLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale

/**
 * اختبارات وحدات منطق الجدولة الصرف (بلا أندرويد).
 * تشغيلها: ./gradlew :app:testDebugUnitTest
 */
class ScheduleLogicTest {

    private fun calendarOf(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        // نستخدم المنطقة الزمنية الافتراضية للجهاز — نفس ما يفعله ScheduleLogic
        // (التطبيق يشغّل الفقرات بتوقيت المدرسة المحلي، لا UTC)
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(year, month - 1, day, hour, minute, 0)
        return cal.timeInMillis
    }

    // ===== التحويلات =====

    @Test
    fun `minutesOfDay يحسب الدقائق من منتصف الليل`() {
        val nineFortyFive = calendarOf(2026, 10, 21, 9, 45)
        assertEquals(9 * 60 + 45, ScheduleLogic.minutesOfDay(nineFortyFive))
    }

    @Test
    fun `isoDayOfWeek يعيد الاثنين 1 والأحد 7`() {
        // 2026-10-19 كان الاثنين، 2026-10-25 كان الأحد
        assertEquals(1, ScheduleLogic.isoDayOfWeek(calendarOf(2026, 10, 19, 12, 0)))
        assertEquals(7, ScheduleLogic.isoDayOfWeek(calendarOf(2026, 10, 25, 12, 0)))
    }

    @Test
    fun `formatMinutes يعرض HH مع صفر سابق`() {
        assertEquals("09:45", ScheduleLogic.formatMinutes(9 * 60 + 45))
        assertEquals("00:00", ScheduleLogic.formatMinutes(0))
        assertEquals("23:59", ScheduleLogic.formatMinutes(23 * 60 + 59))
    }

    @Test
    fun `parseHhMm يقبل الصحيح ويرفض الخاطئ`() {
        assertEquals(9 * 60 + 45, ScheduleLogic.parseHhMm("9:45"))
        assertEquals(0, ScheduleLogic.parseHhMm("00:00"))
        assertNull(ScheduleLogic.parseHhMm("24:00"))
        assertNull(ScheduleLogic.parseHhMm("12:60"))
        assertNull(ScheduleLogic.parseHhMm("abc"))
        assertNull(ScheduleLogic.parseHhMm(""))
    }

    // ===== أيام الأسبوع =====

    @Test
    fun `dayBitOf يوزع البتات صحيحا`() {
        assertEquals(1, ScheduleLogic.dayBitOf(1))   // الاثنين
        assertEquals(64, ScheduleLogic.dayBitOf(7))  // الأحد
    }

    @Test
    fun `maskOf يبني قناع أيام متعددة`() {
        val mask = ScheduleLogic.NextTriggerInput(0, ScheduledEventMask.maskOf(1, 3, 5))
        assertEquals(0b10101, mask.weekDaysMask)
    }

    // ===== أقرب موعد إطلاق =====

    @Test
    fun `nextTriggerMillis يعيد اليوم نفسه إذا كان الوقت قادما`() {
        // 2026-10-21 كان الأربعاء (ISO 3 = bit2) — الحدث كل ثلاثاء وأربعاء 09:45
        val now = calendarOf(2026, 10, 21, 8, 0)
        val trigger = ScheduleLogic.nextTriggerMillis(
            ScheduleLogic.NextTriggerInput(9 * 60 + 45, 0b00110), now
        )
        assertEquals(calendarOf(2026, 10, 21, 9, 45), trigger)
    }

    @Test
    fun `nextTriggerMillis يقفز للأسبوع القادم إذا مر الوقت اليوم`() {
        // الأربعاء 2026-10-21 — الوقت الحالي 11:00 أي بعد 09:45
        val now = calendarOf(2026, 10, 21, 11, 0)
        val trigger = ScheduleLogic.nextTriggerMillis(
            ScheduleLogic.NextTriggerInput(9 * 60 + 45, 0b00010), now
        )
        // الثلاثاء التالي = 2026-10-27
        assertEquals(calendarOf(2026, 10, 27, 9, 45), trigger)
    }

    @Test
    fun `nextTriggerMillis يعيد null لحدث بلا أيام`() {
        val now = calendarOf(2026, 10, 21, 8, 0)
        assertNull(
            ScheduleLogic.nextTriggerMillis(ScheduleLogic.NextTriggerInput(600, 0), now)
        )
    }

    // ===== التعارض =====

    @Test
    fun `hasOverlap يكشف التقاطع في نفس اليوم`() {
        val a = ScheduleLogic.NextTriggerInput(600, 0b1111111)  // 10:00
        val b = ScheduleLogic.NextTriggerInput(610, 0b1111111)  // 10:10 — يتقاطع مع مدة 15د
        val c = ScheduleLogic.NextTriggerInput(700, 0b1111111)  // 11:40 — لا تقاطع
        assertTrue(ScheduleLogic.hasOverlap(a, b))
        assertFalse(ScheduleLogic.hasOverlap(a, c))
    }

    @Test
    fun `hasOverlap لا يعتبر أياما مختلفة تعارضا`() {
        val monday = ScheduleLogic.NextTriggerInput(600, 0b0000001)
        val tuesday = ScheduleLogic.NextTriggerInput(600, 0b0000010)
        assertFalse(ScheduleLogic.hasOverlap(monday, tuesday))
    }

    // ===== المدة =====

    @Test
    fun `formatDuration ينسق الميلي ثانية`() {
        assertEquals("1:33", ScheduleLogic.formatDuration(93_000))
        assertEquals("0:00", ScheduleLogic.formatDuration(0))
        assertEquals("2:05", ScheduleLogic.formatDuration(125_000))
    }
}

/** مسترد خاص لتجنب استيراد الكيان داخل الاختبار الصرف */
private object ScheduledEventMask {
    fun maskOf(vararg days: Int): Int = days.fold(0) { acc, d -> acc or (1 shl (d - 1)) }
}

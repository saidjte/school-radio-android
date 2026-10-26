package com.saidjte.schoolradio.core.util

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات منطق فقرات الموعد الواحد (v0.27.0) —
 * مفاتيح التواريخ، الإزاحة عبر حدود الشهور، العرض العربي، الوصف النسبي،
 * اندماجها في مساري الإطلاق (nextTriggerMillis + AutomationLogic).
 */
class EventDateLogicTest {

    private fun todayKey(): String = ScheduleLogic.dateKey(System.currentTimeMillis())

    // ===== التحويلات الأساسية =====

    @Test
    fun `millisOfDateKey يعيد null للمفاتيح غير الصالحة`() {
        assertNull(EventDateLogic.millisOfDateKey("abc"))
        assertNull(EventDateLogic.millisOfDateKey("2026-13-05"))
        assertNull(EventDateLogic.millisOfDateKey("2026-00-10"))
        assertNull(EventDateLogic.millisOfDateKey("2026-11-32"))
        assertNull(EventDateLogic.millisOfDateKey("5-11-2026"))
        assertNotNull(EventDateLogic.millisOfDateKey("2026-11-05"))
    }

    @Test
    fun `plusDays يقفز عبر حدود الشهور والسنين`() {
        assertEquals("2026-02-06", EventDateLogic.plusDays("2026-01-30", 7))
        assertEquals("2027-01-01", EventDateLogic.plusDays("2026-12-31", 1))
        assertEquals("2026-11-05", EventDateLogic.plusDays("2026-11-05", 0))
        assertNull(EventDateLogic.plusDays("خطأ", 1))
    }

    @Test
    fun `dateKeyFromUtcPickerMillis يحول منتصف ليل UTC بلا انزلاق`() {
        // منتصف ليل UTC 2026-11-05 = 1793836800000
        assertEquals(
            "2026-11-05",
            EventDateLogic.dateKeyFromUtcPickerMillis(1793836800000L)
        )
    }

    @Test
    fun `formatDateAr يعرض التاريخ بأرقام مغربية مع السنة`() {
        val s = EventDateLogic.formatDateAr("2026-11-05")!!
        assertTrue(s.contains("2026"))
        assertTrue(s.contains("5"))
        // صيغة AppFormat.dateDay: «الخميس 5 نوفمبر 2026»
        assertTrue(s.split(" ").size >= 3)
        assertNull(EventDateLogic.formatDateAr("خطأ"))
    }

    // ===== الوصف النسبي =====

    @Test
    fun `relativeLabelAr يفرق اليوم والغد وبقية الأيام`() {
        assertEquals("اليوم", EventDateLogic.relativeLabelAr(todayKey(), System.currentTimeMillis()))
        assertEquals(
            "غداً",
            EventDateLogic.relativeLabelAr(
                EventDateLogic.plusDays(todayKey(), 1)!!, System.currentTimeMillis()
            )
        )
        assertEquals(
            "بعد يومين",
            EventDateLogic.relativeLabelAr(
                EventDateLogic.plusDays(todayKey(), 2)!!, System.currentTimeMillis()
            )
        )
        assertEquals(
            "بعد 3 أيام",
            EventDateLogic.relativeLabelAr(
                EventDateLogic.plusDays(todayKey(), 3)!!, System.currentTimeMillis()
            )
        )
        assertEquals(
            "بعد 11 يوم",
            EventDateLogic.relativeLabelAr(
                EventDateLogic.plusDays(todayKey(), 11)!!, System.currentTimeMillis()
            )
        )
    }

    @Test
    fun `relativeLabelAr يعيد فائتة للماضي و null للغير صالح`() {
        assertEquals(
            "فائتة",
            EventDateLogic.relativeLabelAr(
                EventDateLogic.plusDays(todayKey(), -3)!!, System.currentTimeMillis()
            )
        )
        assertNull(EventDateLogic.relativeLabelAr("خطأ", System.currentTimeMillis()))
    }

    @Test
    fun `isUpcoming يقبل اليوم والمستقبل ويرفض الماضي والغير صالح`() {
        assertTrue(EventDateLogic.isUpcoming(todayKey(), System.currentTimeMillis()))
        assertTrue(
            EventDateLogic.isUpcoming(
                EventDateLogic.plusDays(todayKey(), 5)!!, System.currentTimeMillis()
            )
        )
        assertFalse(
            EventDateLogic.isUpcoming(
                EventDateLogic.plusDays(todayKey(), -1)!!, System.currentTimeMillis()
            )
        )
        assertFalse(EventDateLogic.isUpcoming(null, System.currentTimeMillis()))
        assertFalse(EventDateLogic.isUpcoming("خطأ", System.currentTimeMillis()))
    }

    // ===== المسار الأول: منبهات النظام (nextTriggerMillis) =====

    @Test
    fun `nextTriggerMillis للموعد الواحد المستقبلي يعيد توقيته بدقة`() {
        val date = EventDateLogic.plusDays(todayKey(), 2)!!
        val midnight = EventDateLogic.millisOfDateKey(date)!!
        val now = midnight + 60 * 60_000L // 01:00 فجراً من نفس اليوم
        val trigger = ScheduleLogic.nextTriggerMillis(
            ScheduleLogic.NextTriggerInput(startMinutes = 9 * 60, weekDaysMask = 0, specificDate = date),
            now
        )
        assertEquals(midnight + 9 * 60 * 60_000L, trigger)
    }

    @Test
    fun `nextTriggerMillis للموعد الواحد الفائت يعيد null مهما كانت الأيام`() {
        val date = EventDateLogic.plusDays(todayKey(), -1)!!
        val now = System.currentTimeMillis()
        assertNull(
            ScheduleLogic.nextTriggerMillis(
                ScheduleLogic.NextTriggerInput(startMinutes = 540, weekDaysMask = 0b0111110, specificDate = date),
                now
            )
        )
    }

    @Test
    fun `nextTriggerMillis للموعد الواحد في نفس اليوم بعد الآن يعمل وقبله لا`() {
        val today = todayKey()
        val midnight = EventDateLogic.millisOfDateKey(today)!!
        val now = midnight + 10 * 60 * 60_000L // 10:00 صباحاً
        // 15:00 اليوم → مستحق
        assertEquals(
            midnight + 15 * 60 * 60_000L,
            ScheduleLogic.nextTriggerMillis(
                ScheduleLogic.NextTriggerInput(900, 0, today), now
            )
        )
        // 09:00 اليوم → فائت
        assertNull(
            ScheduleLogic.nextTriggerMillis(
                ScheduleLogic.NextTriggerInput(540, 0, today), now
            )
        )
    }

    @Test
    fun `nextTriggerMillis يتجاهل قناع الأيام عند وجود تاريخ محدد`() {
        // قناع فارغ + تاريخ مستقبلي → يعمل (كان سيرجع null بالمنطق الأسبوعي)
        val date = EventDateLogic.plusDays(todayKey(), 3)!!
        val midnight = EventDateLogic.millisOfDateKey(date)!!
        assertNotNull(
            ScheduleLogic.nextTriggerMillis(
                ScheduleLogic.NextTriggerInput(600, 0, date), midnight - 1
            )
        )
    }

    // ===== المسار الثاني: خدمة الأتمتة الاحتياطية =====

    @Test
    fun `dueEventIds يطلق موعداً واحداً بتاريخ اليوم داخل نافذة السماح فقط`(): Unit = runBlocking {
        val today = todayKey()
        val midnight = EventDateLogic.millisOfDateKey(today)!!
        val now = midnight + 9 * 60 * 60_000L + 60_000L // 09:01 — داخل نافذة السماح (دقيقتان)
        val timings = listOf(
            AutomationLogic.EventTiming(1, "افتتاح الحفل", 540, 0, today),      // داخل النافذة
            AutomationLogic.EventTiming(2, "فقرة أمس", 540, 0,
                EventDateLogic.plusDays(today, -1)!!),                          // تاريخ فائت
            AutomationLogic.EventTiming(3, "فقرة غداً", 540, 0,
                EventDateLogic.plusDays(today, 1)!!),                           // تاريخ قادم
            AutomationLogic.EventTiming(4, "أسبوعية", 545, 0, null)             // قناع فارغ — لا تطلق أبداً
        )
        val due = AutomationLogic.dueEventIds(timings, now, { false })
        assertEquals(listOf(1L), due)
    }

    @Test
    fun `nextUpToday يشمل الموعد الواحد بتاريخ اليوم ويستبعد غيره`(): Unit = runBlocking {
        val today = todayKey()
        val midnight = EventDateLogic.millisOfDateKey(today)!!
        val now = midnight + 8 * 60 * 60_000L // 08:00
        val next = AutomationLogic.nextUpToday(
            listOf(
                AutomationLogic.EventTiming(7, "حفل التخرج", 600, 0, today),
                AutomationLogic.EventTiming(8, "أسبوعية قادمة", 610, 0b0111110, null)
            ),
            now, { false }
        )
        assertEquals(7L, next?.eventId)
    }
}

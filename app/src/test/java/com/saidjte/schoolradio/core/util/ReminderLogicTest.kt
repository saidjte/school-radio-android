package com.saidjte.schoolradio.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * اختبارات منطق تنبيه الفقرات المؤرخة (v0.28.0) — 14 اختباراً.
 */
class ReminderLogicTest {

    // ===== التسميات العربية =====

    @Test
    fun `null يقرأ بدون تنبيه`() {
        assertEquals("بدون تنبيه", ReminderLogic.labelAr(null))
    }

    @Test
    fun `صفر يقرأ بدون تنبيه`() {
        assertEquals("بدون تنبيه", ReminderLogic.labelAr(0))
    }

    @Test
    fun `ساعة ويوم بتسميتيهما`() {
        assertEquals("قبل ساعة", ReminderLogic.labelAr(60))
        assertEquals("قبل يوم", ReminderLogic.labelAr(1440))
    }

    @Test
    fun `مدة مخصصة بجمع عربي سليم`() {
        assertEquals("قبل دقيقة واحدة", ReminderLogic.labelAr(1))
        assertEquals("قبل دقيقتين", ReminderLogic.labelAr(2))
        assertEquals("قبل 5 دقائق", ReminderLogic.labelAr(5))
        assertEquals("قبل 30 دقيقة", ReminderLogic.labelAr(30))
    }

    // ===== شارة البطاقة =====

    @Test
    fun `الشارة فارغة للمعطل وقصيرة للمفعّل`() {
        assertNull(ReminderLogic.badgeAr(null))
        assertNull(ReminderLogic.badgeAr(0))
        assertEquals("تنبيه قبل يوم", ReminderLogic.badgeAr(1440))
        assertEquals("تنبيه قبل ساعة", ReminderLogic.badgeAr(60))
    }

    // ===== لحظة التنبيه =====

    @Test
    fun `التنبيه قبل يوم يساوي الانطلاق ناقص 24 ساعة`() {
        val trigger = 1_700_000_000_000L
        assertEquals(
            trigger - 24 * 60 * 60_000L,
            ReminderLogic.reminderAt(trigger, ReminderLogic.DAY)
        )
    }

    @Test
    fun `التنبيه قبل ساعة يساوي الانطلاق ناقص 60 دقيقة`() {
        val trigger = 1_700_000_000_000L
        assertEquals(
            trigger - 3_600_000L,
            ReminderLogic.reminderAt(trigger, ReminderLogic.HOUR)
        )
    }

    // ===== شروط ضبط المنبه =====

    @Test
    fun `فقرة أسبوعية لا تنبه حتى لو ضبط التنبيه`() {
        val now = System.currentTimeMillis()
        assertFalse(
            ReminderLogic.shouldArm(false, ReminderLogic.DAY, now + 90_000_000L, now)
        )
    }

    @Test
    fun `فقرة مؤرخة بلا تنبيه لا تنبه`() {
        val now = System.currentTimeMillis()
        assertFalse(ReminderLogic.shouldArm(true, null, now + 90_000_000L, now))
        assertFalse(ReminderLogic.shouldArm(true, 0, now + 90_000_000L, now))
    }

    @Test
    fun `فقرة مؤرخة فائتة لا تنبه`() {
        val now = System.currentTimeMillis()
        assertFalse(ReminderLogic.shouldArm(true, ReminderLogic.DAY, now - 5_000L, now))
        assertFalse(ReminderLogic.shouldArm(true, ReminderLogic.DAY, null, now))
    }

    @Test
    fun `لحظة التنبيه الماضية لا تنبه حتى لو الانطلاق قادم`() {
        // الانطلاق بعد 12 ساعة لكن التنبيه المطلوب «قبل يوم» — فات قطاره
        val now = System.currentTimeMillis()
        val trigger = now + 12 * 3_600_000L
        assertFalse(ReminderLogic.shouldArm(true, ReminderLogic.DAY, trigger, now))
    }

    @Test
    fun `الموعد الصحيح يُضبط`() {
        // الانطلاق بعد 3 أيام والتنبيه قبل يوم — لحظة التنبيه بعد يومين: تُضبط
        val now = System.currentTimeMillis()
        val trigger = now + 3 * 24 * 3_600_000L
        assertTrue(ReminderLogic.shouldArm(true, ReminderLogic.DAY, trigger, now))
        assertTrue(ReminderLogic.shouldArm(true, ReminderLogic.HOUR, trigger, now))
    }

    // ===== نص الإشعار =====

    @Test
    fun `نص الإشعار يركب الصيغة 12 ساعة والتاريخ والنسبي`() {
        // 9:45 صباحاً على تاريخ بعينه
        val dateKey = "2026-11-05"
        val midnight = EventDateLogic.millisOfDateKey(dateKey)!!
        val text = ReminderLogic.notificationTextAr(
            title = "حفل التخرج",
            relativeLabel = EventDateLogic.relativeLabelAr(dateKey, midnight)!!,
            dateAr = EventDateLogic.formatDateAr(dateKey)!!,
            startMinutes = 585
        )
        assertTrue("يجب أن يذكر العنوان", text.contains("حفل التخرج"))
        assertTrue("يجب أن يذكر الصيغة 12 ساعة", text.contains("9:45 صباحاً"))
        assertTrue("يجب أن يذكر الوصف النسبي", text.contains("اليوم"))
        assertTrue("يجب أن يذكر التاريخ العربي", text.contains("نوفمبر"))
    }

    @Test
    fun `نص الإشعار للوقت المسائي صحيح`() {
        val text = ReminderLogic.notificationTextAr(
            title = "بث الاحتفال", relativeLabel = "غداً",
            dateAr = "الجمعة 6 نوفمبر 2026", startMinutes = 14 * 60 + 30
        )
        assertTrue(text.contains("2:30 مساءً"))
        assertTrue(text.contains("غداً"))
    }

    // ===== تكامل مع EventDateLogic: يوم كامل قبل الانطلاق =====

    @Test
    fun `تنبيه يوم قبل فقرة غداً الصباحية يساوي الآن تقريبا`() {
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 10); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val tomorrow = EventDateLogic.plusDays(
            ScheduleLogic.dateKey(now), 1
        )!!
        val trigger = EventDateLogic.millisOfDateKey(tomorrow)!! + 10 * 3_600_000L
        // فقرة غداً الساعة 10:00 بتنبيه «قبل يوم» → لحظة التنبيه الآن تقريباً
        val reminder = ReminderLogic.reminderAt(trigger, ReminderLogic.DAY)
        assertTrue("الفرق يجب أن يكون دقائق معدودة", (reminder - now) < 60_000L)
    }
}

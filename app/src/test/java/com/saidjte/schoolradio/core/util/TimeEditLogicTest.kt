package com.saidjte.schoolradio.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * اختبارات منطق تعديل وقت الفقرات المجدولة (v0.26.0) —
 * اللف حول منتصف الليل، الإزاحات السريعة، التقريب، صيغة 12 ساعة عربية،
 * الوصف النسبي وجموع الأعداد العربية.
 */
class TimeEditLogicTest {

    // ===== اللف حول منتصف الليل =====

    @Test
    fun `wrap يقبل نطاق اليوم الطبيعي كما هو`() {
        assertEquals(0, TimeEditLogic.wrap(0))
        assertEquals(585, TimeEditLogic.wrap(585))
        assertEquals(1439, TimeEditLogic.wrap(1439))
    }

    @Test
    fun `wrap يلف القيم السالبة والفائضة`() {
        assertEquals(1435, TimeEditLogic.wrap(-5))
        assertEquals(0, TimeEditLogic.wrap(1440))
        assertEquals(20, TimeEditLogic.wrap(1460))
        assertEquals(1439, TimeEditLogic.wrap(-1441))
    }

    // ===== الإزاحة السريعة =====

    @Test
    fun `shifted يرجع 15 دقيقة للخلف`() {
        assertEquals(570, TimeEditLogic.shifted(585, -15)) // 09:45 → 09:30
    }

    @Test
    fun `shifted يلتف عند منتصف الليل للأمام والخلف`() {
        assertEquals(0, TimeEditLogic.shifted(1435, 5))     // 23:55 + 5 = 00:00
        assertEquals(1439, TimeEditLogic.shifted(4, -5))    // 00:04 − 5 = 23:59
    }

    @Test
    fun `الرقائق السريعة تتضمن الإزاحات الأشيع`() {
        assertEquals(listOf(-15, -5, 1, 5, 15), TimeEditLogic.QUICK_OFFSETS_MIN)
    }

    // ===== التقريب لأعلى =====

    @Test
    fun `roundUpToStep يقرب لأعلى لأقرب 5 دقائق`() {
        assertEquals(585, TimeEditLogic.roundUpToStep(583, 5))
        assertEquals(585, TimeEditLogic.roundUpToStep(585, 5))
        assertEquals(590, TimeEditLogic.roundUpToStep(586, 5))
    }

    @Test
    fun `roundUpToStep يلتف عند نهاية اليوم`() {
        assertEquals(0, TimeEditLogic.roundUpToStep(1438, 5))
        assertEquals(1440 - 1, TimeEditLogic.roundUpToStep(1439, 1))
    }

    @Test
    fun `roundUpToStep بخطوة 1 أو أقل لا يغير شيئا`() {
        assertEquals(583, TimeEditLogic.roundUpToStep(583, 1))
        assertEquals(583, TimeEditLogic.roundUpToStep(583, 0))
    }

    // ===== فترة اليوم والصيغة 12 ساعة =====

    @Test
    fun `periodLabel يميز الفجر والظهر والمساء`() {
        assertEquals("منتصف الليل", TimeEditLogic.periodLabel(30))    // 00:30
        assertEquals("صباحاً", TimeEditLogic.periodLabel(585))        // 09:45
        assertEquals("ظهراً", TimeEditLogic.periodLabel(725))         // 12:05
        assertEquals("مساءً", TimeEditLogic.periodLabel(1290))        // 21:30
    }

    @Test
    fun `format12hAr يحول الصباح والظهر والمساء`() {
        assertEquals("9:45 صباحاً", TimeEditLogic.format12hAr(585))
        assertEquals("12:00 منتصف الليل", TimeEditLogic.format12hAr(0))
        assertEquals("12:00 ظهراً", TimeEditLogic.format12hAr(720))
        assertEquals("1:00 مساءً", TimeEditLogic.format12hAr(780))
        assertEquals("9:30 مساءً", TimeEditLogic.format12hAr(1290))
        assertEquals("11:59 مساءً", TimeEditLogic.format12hAr(1439))
    }

    @Test
    fun `format12hAr يلف القيم الخارجة عن النطاق`() {
        assertEquals("12:00 منتصف الليل", TimeEditLogic.format12hAr(1440))
        assertEquals("11:55 مساءً", TimeEditLogic.format12hAr(-5))
    }

    // ===== الوصف النسبي =====

    @Test
    fun `relativeLabelAr يصف المستقبل بالساعات والدقائق`() {
        assertEquals(
            "ينطلق خلال 5 دقائق من الآن",
            TimeEditLogic.relativeLabelAr(585, 580)
        )
        assertEquals(
            "ينطلق خلال 9 ساعات و40 دقيقة من الآن",
            TimeEditLogic.relativeLabelAr(585, 5)
        )
    }

    @Test
    fun `relativeLabelAr عند المطابقة تماماً وبالماضي`() {
        assertEquals("هذا الموعد هو الآن تماماً", TimeEditLogic.relativeLabelAr(585, 585))
        assertNull(TimeEditLogic.relativeLabelAr(570, 585))
    }

    @Test
    fun `relativeLabelAr يلف الفارق حول منتصف الليل`() {
        // 23:55 مقابل الآن 00:10 → فرق موجب 1425 دقيقة يعرض كمستقبل
        val label = TimeEditLogic.relativeLabelAr(1435, 10)!!
        assertEquals("ينطلق خلال 23 ساعة و45 دقيقة من الآن", label)
    }

    // ===== صياغة المدد والجموع العربية =====

    @Test
    fun `durationAr يركب الساعات والدقائق`() {
        assertEquals("أقل من دقيقة", TimeEditLogic.durationAr(0))
        assertEquals("دقيقة واحدة", TimeEditLogic.durationAr(1))
        assertEquals("دقيقتين", TimeEditLogic.durationAr(2))
        assertEquals("5 دقائق", TimeEditLogic.durationAr(5))
        assertEquals("11 دقيقة", TimeEditLogic.durationAr(11))
        assertEquals("ساعة واحدة", TimeEditLogic.durationAr(60))
        assertEquals("ساعة واحدة ودقيقتين", TimeEditLogic.durationAr(62))
        assertEquals("ساعتين", TimeEditLogic.durationAr(120))
        assertEquals("ساعتين و5 دقائق", TimeEditLogic.durationAr(125))
        assertEquals("3 ساعات و45 دقيقة", TimeEditLogic.durationAr(225))
        assertEquals("14 ساعة", TimeEditLogic.durationAr(14 * 60))
    }

    @Test
    fun `pluralAr يطبق قواعد الجموع العربية`() {
        assertEquals("ساعة واحدة", TimeEditLogic.pluralAr(1, "ساعة", "ساعتين", "ساعات"))
        assertEquals("ساعتين", TimeEditLogic.pluralAr(2, "ساعة", "ساعتين", "ساعات"))
        assertEquals("7 ساعات", TimeEditLogic.pluralAr(7, "ساعة", "ساعتين", "ساعات"))
        assertEquals("15 ساعة", TimeEditLogic.pluralAr(15, "ساعة", "ساعتين", "ساعات"))
    }
}

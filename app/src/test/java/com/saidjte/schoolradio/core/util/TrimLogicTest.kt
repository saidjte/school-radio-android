package com.saidjte.schoolradio.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات منطق القص اليدوي (v0.19.0) — تطبيع النقاط، فرض الفجوة الدنيا،
 * التحقق قبل الحفظ، ونصوص التغذية الراجعة العربية.
 */
class TrimLogicTest {

    // ================== snap ==================

    @Test
    fun `snap يقرب لأخرب مئة ميلي ثانية`() {
        assertEquals(1200L, TrimLogic.snap(1234L))
        assertEquals(100L, TrimLogic.snap(199L))
        assertEquals(0L, TrimLogic.snap(99L))
        assertEquals(0L, TrimLogic.snap(0L))
    }

    @Test
    fun `snap لا يغيّر القيم المسقوفة أصلاً`() {
        assertEquals(3000L, TrimLogic.snap(3000L))
    }

    // ================== normalize ==================

    @Test
    fun `normalize يبقي القيم السليمة كما هي`() {
        val (s, e) = TrimLogic.normalize(2000L, 10_000L, 30_000L)
        assertEquals(2000L, s)
        assertEquals(10_000L, e)
    }

    @Test
    fun `normalize يمنع البداية السالبة`() {
        val (s, _) = TrimLogic.normalize(-500L, 10_000L, 30_000L)
        assertEquals(0L, s)
    }

    @Test
    fun `normalize يقيد النهاية داخل مدة الملف`() {
        val (s, e) = TrimLogic.normalize(2000L, 99_999L, 30_000L)
        assertEquals(30_000L, e)
        assertTrue(s < e)
    }

    @Test
    fun `normalize يفرض الفجوة الدنيا بين النقطتين`() {
        // نهاية لصيقة بالبداية (أقل من ثانية) — يجب أن تُدفع النهاية
        val (s, e) = TrimLogic.normalize(5000L, 5200L, 30_000L)
        assertEquals(e - s, TrimLogic.MIN_SEGMENT_MS)
        assertTrue(e - s >= TrimLogic.MIN_SEGMENT_MS)
    }

    @Test
    fun `normalize يفرض الفجوة الدنيا بالانجذاب نحو البداية عندما تكون النهاية قبلها`() {
        // نهاية قبل البداية (خطأ شائع في السحب) — النهاية تُصحح
        val (s, e) = TrimLogic.normalize(8000L, 2000L, 30_000L)
        assertTrue(e > s)
        assertEquals(e - s, TrimLogic.MIN_SEGMENT_MS)
    }

    @Test
    fun `normalize يعمل مع مدة مجهولة (صفر) دون انهيار`() {
        val (s, e) = TrimLogic.normalize(1000L, 5000L, 0L)
        assertEquals(1000L, s)
        assertEquals(5000L, e)
        assertTrue(e > s)
    }

    @Test
    fun `normalize مع ملف قصير جداً يعيد فجوة دنيا مقبولة`() {
        // ملف 1.5 ثانية: القص من 0 يجب أن يترك ثانية على الأقل
        val (s, e) = TrimLogic.normalize(0L, 1400L, 1500L)
        assertTrue(e - s >= TrimLogic.MIN_SEGMENT_MS)
        assertTrue(e <= 1500L)
    }

    // ================== validate ==================

    @Test
    fun `validate يقبل قصاً سليماً`() {
        assertNull(TrimLogic.validate(2000L, 15_000L, 30_000L))
    }

    @Test
    fun `validate يرفض المقطع الأقصر من ثانية`() {
        val err = TrimLogic.validate(2000L, 2500L, 30_000L)
        assertTrue(err != null && err.contains("قصيرة جداً"))
    }

    @Test
    fun `validate يرفض بداية بعد نهاية الملف`() {
        val err = TrimLogic.validate(40_000L, 50_000L, 30_000L)
        assertTrue(err != null && err.contains("بعد نهاية الملف"))
    }

    @Test
    fun `validate يرفض نهاية بعد نهاية الملف`() {
        val err = TrimLogic.validate(2000L, 40_000L, 30_000L)
        assertTrue(err != null && err.contains("بعد نهاية الملف"))
    }

    @Test
    fun `validate يتسامح مع المدة المجهولة إذا كانت الفجوة سليمة`() {
        assertNull(TrimLogic.validate(1000L, 8000L, 0L))
    }

    // ================== summary / message ==================

    @Test
    fun `summary يعرض البداية والنهاية والمتبقي`() {
        val summary = TrimLogic.summary(3000L, 93_000L)
        assertTrue(summary.contains("0:03"))
        assertTrue(summary.contains("1:33"))
        assertTrue(summary.contains("1:30"))
    }

    @Test
    fun `message يذكر عنوان المقطع`() {
        val msg = TrimLogic.message(1000L, 5000L, "النشيد الوطني")
        assertTrue(msg.contains("النشيد الوطني"))
        assertTrue(msg.contains("حُفظ قص"))
    }

    // ================== hasAnyTrim ==================

    @Test
    fun `hasAnyTrim يكتشف كل أنواع القص`() {
        assertTrue(TrimLogic.hasAnyTrim(500L, 0L, false))    // قص آلي بالبداية
        assertTrue(TrimLogic.hasAnyTrim(0L, 8000L, false))   // نهاية يدوية فقط
        assertTrue(TrimLogic.hasAnyTrim(0L, 0L, true))       // وسم يدوي بلا قيم
        assertFalse(TrimLogic.hasAnyTrim(0L, 0L, false))     // لا قص إطلاقاً
    }
}

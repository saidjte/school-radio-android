package com.saidjte.schoolradio.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** اختبارات الإعدادات الإذاعية المخصصة للمعادل — v0.22.0 */
class RadioPresetsTest {

    // ---- interpolate ----

    @Test
    fun `قبل أول نقطة يتثبت على الكسب الطرفي`() {
        val pts = listOf(200 to -2.0, 2000 to 4.0)
        assertEquals(-2.0, RadioPresets.interpolate(pts, 80.0), 0.001)
    }

    @Test
    fun `بعد آخر نقطة يتثبت على الكسب الطرفي`() {
        val pts = listOf(200 to -2.0, 2000 to 4.0)
        assertEquals(4.0, RadioPresets.interpolate(pts, 16000.0), 0.001)
    }

    @Test
    fun `عند النقاط نفسها يعيد الكسب المحدد تماما`() {
        val pts = listOf(100 to 1.5, 1000 to -3.0, 5000 to 2.0)
        assertEquals(1.5, RadioPresets.interpolate(pts, 100.0), 0.001)
        assertEquals(-3.0, RadioPresets.interpolate(pts, 1000.0), 0.001)
        assertEquals(2.0, RadioPresets.interpolate(pts, 5000.0), 0.001)
    }

    @Test
    fun `منتصف العقد لوغاريتمي يعطي منتصف الكسب تقريبا`() {
        // 100→1000 هرتز: 316 هرتز تقريبا منتصف المحور اللوغاريتمي
        val pts = listOf(100 to 0.0, 1000 to 4.0)
        val mid = RadioPresets.interpolate(pts, 316.2)
        assertEquals(2.0, mid, 0.1)
        // الاستيفاء اللوغاريتمي لا يطابق منتصف الخطي (522 هرتز)
        val linearMid = RadioPresets.interpolate(pts, 550.0)
        assertTrue("الاستيفاء اللوغاريتمي يجب أن يسبق الخطي", linearMid < 3.4)
    }

    @Test
    fun `نقاط فارغة أو تردد غير طبيعي يعطي صفرا دفاعيا`() {
        assertEquals(0.0, RadioPresets.interpolate(emptyList(), 1000.0), 0.001)
        assertEquals(0.0, RadioPresets.interpolate(listOf(100 to 2.0), 0.0), 0.001)
        assertEquals(0.0, RadioPresets.interpolate(listOf(100 to 2.0), -5.0), 0.001)
    }

    // ---- gainsForBands ----

    @Test
    fun `خريطة الشرائح تحترم عدد الشرائح وترتيبها`() {
        val preset = RadioPresets.ALL.first()
        val centers = listOf(60.0, 230.0, 950.0, 3800.0, 12000.0) // جهاز 5 شرائح
        val gains = RadioPresets.gainsForBands(preset, centers, -15f, 15f)
        assertEquals(5, gains.size)
        // 60 هرتز تحت أول نقطة (80) → تثبيت عند -3
        assertEquals(-3f, gains[0], 0.01f)
        // 12000 عند آخر نقطة → 1.0
        assertEquals(1.0f, gains[4], 0.01f)
    }

    @Test
    fun `التقييد داخل نطاق الجهاز حتى لو طلب المغلف أكثر`() {
        val bass = RadioPresets.ALL.first { it.id == 1003 } // باص دافئ: +4.5 عند 60Hz
        val gains = RadioPresets.gainsForBands(bass, listOf(60.0), minDb = -10f, maxDb = 3f)
        assertEquals(3f, gains[0], 0.001f)
    }

    @Test
    fun `جهاز بشرائح كثيرة يوزع المغلف بلا قفزات`() {
        val preset = RadioPresets.ALL.first { it.id == 1002 } // موسيقى هادئة
        val centers = (1..10).map { 100.0 * it } // 100..1000 هرتز
        val gains = RadioPresets.gainsForBands(preset, centers, -15f, 15f)
        assertEquals(10, gains.size)
        // لا قفزة أكبر من 3dB بين شريحتين متتاليتين قريبتين
        gains.zipWithNext().forEach { (a, b) ->
            assertTrue("قفزة غير طبيعية $a→$b", kotlin.math.abs(b - a) < 3f)
        }
    }

    // ---- الكتالوج ----

    @Test
    fun `معرفات مخصصة فريدة في نطاق 1000+`() {
        val ids = RadioPresets.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it >= 1000 })
        assertEquals(5, ids.size)
    }

    @Test
    fun `byId يعيد النمط الصحيح ولا شيء لغيره`() {
        assertEquals("صوت المذيع", RadioPresets.byId(1000)?.name)
        assertNull(RadioPresets.byId(999))
        assertNull(RadioPresets.byId(-1))
    }
}

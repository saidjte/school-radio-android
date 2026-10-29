package com.saidjte.schoolradio.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات قرار نشيد الافتتاح بعد نغمة الإعلان (v0.50.0) — القرار حتمي
 * من الحقائق المصدرية الثلاث، وكل مسار له اختبار يعكس استخدامه الحقيقي.
 */
class OpeningAnthemLogicTest {

    @Test
    fun `بلا نشيد محفوظ - صمت بلا رسالة`() {
        val d = OpeningAnthemLogic.decide(configuredId = null, trackExists = true, isAvailable = true)
        assertTrue(d is OpeningAnthemLogic.Decision.Hold)
        assertNull((d as OpeningAnthemLogic.Decision.Hold).reason)
    }

    @Test
    fun `النشيد المحفوظ اختفى من المكتبة - رسالة صادقة بلا تشغيل`() {
        val d = OpeningAnthemLogic.decide(configuredId = 42L, trackExists = false, isAvailable = false)
        assertTrue(d is OpeningAnthemLogic.Decision.Hold)
        val reason = (d as OpeningAnthemLogic.Decision.Hold).reason
        assertTrue(reason!!.contains("المكتبة"))
    }

    @Test
    fun `ملف النشيد مفقود من الجهاز - رسالة إعادة الربط`() {
        val d = OpeningAnthemLogic.decide(configuredId = 7L, trackExists = true, isAvailable = false)
        assertTrue(d is OpeningAnthemLogic.Decision.Hold)
        val reason = (d as OpeningAnthemLogic.Decision.Hold).reason
        assertTrue(reason!!.contains("أعد ربط"))
    }

    @Test
    fun `نشيد متوفر - تشغيل بمعرفه نفسه`() {
        val d = OpeningAnthemLogic.decide(configuredId = 15L, trackExists = true, isAvailable = true)
        assertEquals(OpeningAnthemLogic.Decision.Play(15L), d)
    }
}

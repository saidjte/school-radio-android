package com.saidjte.schoolradio.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات تسمية محتوى الفقرة (v0.5.0) — قائمة/مقطع/تسجيل.
 */
class EventContentArTest {

    @Test fun `قائمة التشغيل لها الأولوية عند تعدد المصادر`() {
        val label = EventContentAr.label(
            playlistName = "الصباحية", trackTitle = "نشيد", recordingTitle = "تسجيل"
        )
        assertEquals("قائمة تشغيل: الصباحية", label)
    }

    @Test fun `المقطع ثانياً إن غابت القائمة`() {
        val label = EventContentAr.label(
            playlistName = null, trackTitle = "نشيد الوطن", recordingTitle = "تسجيل"
        )
        assertEquals("مقطع: نشيد الوطن", label)
    }

    @Test fun `التسجيل ثالثاً — حالة التسجيلات المجدولة`() {
        val label = EventContentAr.label(
            playlistName = null, trackTitle = null, recordingTitle = "طابور الخميس"
        )
        assertEquals("تسجيل: طابور الخميس", label)
    }

    @Test fun `بلا محتوى يعيد رسالة إرشادية`() {
        val label = EventContentAr.label(null, null, null)
        assertEquals("بلا محتوى — أرفق قائمة أو مقطعاً أو تسجيلاً", label)
    }

    @Test fun `hasContent يكتشف كل مصادر المحتوى`() {
        assertTrue(EventContentAr.hasContent(1L, null, null))
        assertTrue(EventContentAr.hasContent(null, 2L, null))
        assertTrue(EventContentAr.hasContent(null, null, 3L))
        assertFalse(EventContentAr.hasContent(null, null, null))
    }
}

package com.saidjte.schoolradio.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات وحدة تسمية ملفات تصدير التسجيلات (v0.14.0) — منطق صافٍ.
 */
class RecordingExportLogicTest {

    // ===== الامتدادات =====

    @Test fun `mp4 mime maps to m4a`() {
        assertEquals("m4a", RecordingExportLogic.extensionOf("audio/mp4"))
    }

    @Test fun `aac mime maps to aac`() {
        assertEquals("aac", RecordingExportLogic.extensionOf("audio/aac"))
    }

    @Test fun `unknown mime falls back to m4a`() {
        assertEquals("m4a", RecordingExportLogic.extensionOf("audio/weird"))
    }

    @Test fun `mime case insensitive`() {
        assertEquals("mp3", RecordingExportLogic.extensionOf("AUDIO/MP3"))
    }

    // ===== أسماء الملفات الآمنة =====

    @Test fun `forbidden characters replaced`() {
        val name = RecordingExportLogic.safeFileName(
            "نشيد: الوطن/الحب*؟", 1761000000000, "audio/mp4"
        )
        assertFalse("لا محارف ممنوعة: $name", name.any { it in "\\/:*?\"<>|" })
        assertTrue(name.endsWith(".m4a"))
    }

    @Test fun `control characters removed`() {
        val name = RecordingExportLogic.safeFileName("فقرة\u000Aالصباح", 1761000000000, "audio/mp4")
        assertFalse(name.contains('\u000A'))
        assertTrue(name.contains("فقرة"))
    }

    @Test fun `empty title falls back to تسجيل`() {
        val name = RecordingExportLogic.safeFileName("///", 1761000000000, "audio/mp4")
        assertTrue(name.startsWith("تسجيل"))
    }

    @Test fun `long title truncated to 60 chars`() {
        val longTitle = "أ".repeat(120)
        val name = RecordingExportLogic.safeFileName(longTitle, 1761000000000, "audio/mp4")
        val base = name.removeSuffix(".m4a").substringBeforeLast(" - ")
        assertTrue(base.length <= 60)
    }

    @Test fun `filename keeps arabic and date stamp`() {
        val name = RecordingExportLogic.safeFileName("كلمة المدير", 1761000000000, "audio/mp4")
        assertTrue("العنوان العربي محفوظ", name.contains("كلمة المدير"))
        assertTrue("امتداد m4a", name.endsWith(".m4a"))
        assertFalse("لا أرقام مشرقية: $name", name.any { it in '٠'..'٩' })
    }

    // ===== رسالة النجاح =====

    @Test fun `success message points to downloads subdir`() {
        val msg = RecordingExportLogic.successMessage("تسجيل.m4a")
        assertTrue(msg.contains("Downloads"))
        assertTrue(msg.contains(RecordingExportLogic.EXPORT_SUBDIR))
        assertTrue(msg.contains("تسجيل.m4a"))
    }
}

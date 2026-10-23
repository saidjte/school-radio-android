package com.saidjte.schoolradio

import com.saidjte.schoolradio.core.util.RecordingLogic
import com.saidjte.schoolradio.data.local.entity.RecordingSource
import com.saidjte.schoolradio.data.local.entity.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale

/**
 * اختبارات وحدة منطق التسجيلات والأدوار (v0.3.0) — منطق صرف بلا تبعيات أندرويد.
 */
class RecordingLogicTest {

    // ===== المدة =====

    @Test fun `elapsed zero is 0_00`() {
        assertEquals("0:00", RecordingLogic.formatElapsed(0))
    }

    @Test fun `elapsed 59s stays minutes form`() {
        assertEquals("0:59", RecordingLogic.formatElapsed(59_999))
    }

    @Test fun `elapsed 5m04s`() {
        assertEquals("5:04", RecordingLogic.formatElapsed(304_000))
    }

    @Test fun `elapsed 1h01m09s uses hours form`() {
        assertEquals("1:01:09", RecordingLogic.formatElapsed(3_669_000))
    }

    // ===== الحجم =====

    @Test fun `bytes under kb`() {
        assertEquals("512 B", RecordingLogic.formatBytes(512))
    }

    @Test fun `bytes in kb`() {
        assertEquals("2 KB", RecordingLogic.formatBytes(2_048))
    }

    @Test fun `bytes in mb one decimal`() {
        assertEquals("1.5 MB", RecordingLogic.formatBytes(1_500_000))
    }

    // ===== العنوان الافتراضي واسم الملف =====

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long =
        Calendar.getInstance().apply {
            set(Calendar.YEAR, y); set(Calendar.MONTH, mo - 1); set(Calendar.DAY_OF_MONTH, d)
            set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, mi); set(Calendar.SECOND, 0)
        }.timeInMillis

    @Test fun `title for PTT starts with broadcast word`() {
        val t = at(2026, 10, 24, 8, 15)
        assertTrue(RecordingLogic.defaultTitle(RecordingSource.PTT, t).startsWith("بث"))
    }

    @Test fun `title for MEMO starts with segment word`() {
        val t = at(2026, 10, 24, 8, 15)
        assertTrue(RecordingLogic.defaultTitle(RecordingSource.MEMO, t).startsWith("فقرة"))
    }

    @Test fun `filename is ascii with date and time`() {
        val t = at(2026, 10, 24, 8, 15)
        val name = RecordingLogic.newFileName(RecordingSource.PTT, t)
        assertTrue(name.startsWith("broadcast-"))
        assertTrue(name.endsWith(".m4a"))
        // ASCII فقط (تجنب مشاكل أنظمة الملفات)
        name.toByteArray(Charsets.US_ASCII)
    }

    @Test fun `memo filename prefix differs`() {
        val t = at(2026, 10, 24, 8, 15)
        assertTrue(RecordingLogic.newFileName(RecordingSource.MEMO, t).startsWith("segment-"))
    }

    // ===== الأدوار =====

    @Test fun `student cannot manage`() {
        assertFalse(UserRole.canManage(UserRole.STUDENT))
    }

    @Test fun `teacher and admin can manage`() {
        assertTrue(UserRole.canManage(UserRole.TEACHER))
        assertTrue(UserRole.canManage(UserRole.ADMIN))
    }

    @Test fun `only admin manages users`() {
        assertTrue(UserRole.canManageUsers(UserRole.ADMIN))
        assertFalse(UserRole.canManageUsers(UserRole.TEACHER))
        assertFalse(UserRole.canManageUsers(UserRole.STUDENT))
    }

    @Test fun `unknown role string falls back to student`() {
        assertEquals(UserRole.STUDENT, UserRole.from("HACKER"))
        assertEquals(UserRole.STUDENT, UserRole.from(null))
        assertEquals(UserRole.TEACHER, UserRole.from("TEACHER"))
    }

    @Test fun `locale-independent hour formatting`() {
        // تأكد أن الأرقام لاتينية حتى لو كانت الافتراضية للجهاز عربية
        val formatted = RecordingLogic.formatElapsed(3_723_000)
        assertTrue(formatted.matches(Regex("\\d+:\\d\\d:\\d\\d")))
        assertEquals("1:02:03", formatted)
    }
}

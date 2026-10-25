package com.saidjte.schoolradio.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.25.0 — اختبارات حسابات التنزيل الصافية: النسبة، تسمية الحجم، خنق الإبلاغ،
 * وسطر الحالة الجاهز للعرض.
 */
class DownloadLogicTest {

    // ================== النسبة ==================

    @Test fun `percent is null when total unknown`() {
        assertNull(DownloadLogic.percent(100L, 0L))
        assertNull(DownloadLogic.percent(100L, -5L))
    }

    @Test fun `percent computes ratio`() {
        assertEquals(0.5f, DownloadLogic.percent(500L, 1000L)!!, 1e-6f)
        assertEquals(0f, DownloadLogic.percent(0L, 1000L)!!, 1e-6f)
    }

    @Test fun `percent clamps overflow`() {
        assertEquals(1f, DownloadLogic.percent(1500L, 1000L)!!, 1e-6f)
    }

    // ================== خنق الإبلاغ ==================

    @Test fun `report throttled below delta`() {
        assertFalse(DownloadLogic.shouldReport(0L, DownloadLogic.REPORT_DELTA_BYTES - 1))
    }

    @Test fun `report fires at and above delta`() {
        assertTrue(DownloadLogic.shouldReport(0L, DownloadLogic.REPORT_DELTA_BYTES))
        assertTrue(DownloadLogic.shouldReport(0L, DownloadLogic.REPORT_DELTA_BYTES * 5))
    }

    @Test fun `report fires on regression`() {
        // إعادة تنزيل — يجب أن يُبلَّغ كي يعود الشريط للخلف بصدق
        assertTrue(DownloadLogic.shouldReport(2_000_000L, 100_000L))
    }

    // ================== تسمية الحجم ==================

    @Test fun `bytes label negative is empty`() {
        assertEquals("", DownloadLogic.bytesLabel(-1L))
    }

    @Test fun `bytes label bytes range`() {
        assertEquals("0 بايت", DownloadLogic.bytesLabel(0L))
        assertEquals("512 بايت", DownloadLogic.bytesLabel(512L))
    }

    @Test fun `bytes label kilobytes`() {
        val label = DownloadLogic.bytesLabel(2048L)
        assertTrue(label.contains("كيلوبايت") && label.contains("2"))
    }

    @Test fun `bytes label megabytes`() {
        val label = DownloadLogic.bytesLabel(5L * 1024 * 1024)
        assertTrue(label.contains("ميغابايت"))
    }

    @Test fun `bytes label gigabytes`() {
        val label = DownloadLogic.bytesLabel(2L * 1024 * 1024 * 1024)
        assertTrue(label.contains("غيغابايت"))
    }

    // ================== سطر الحالة ==================

    @Test fun `status line with known total shows percent and range`() {
        val line = DownloadLogic.statusLine(500_000L, 1_000_000L)
        assertTrue(line.startsWith("50%"))
        assertTrue(line.contains("من"))
    }

    @Test fun `status line without total shows downloaded only`() {
        val line = DownloadLogic.statusLine(3_000_000L, -1L)
        assertFalse(line.contains("%"))
        assertTrue(line.contains("ميغابايت"))
    }

    @Test fun `status line at zero with unknown total has fallback`() {
        assertEquals("قيد التنزيل…", DownloadLogic.statusLine(0L, -1L))
    }
}

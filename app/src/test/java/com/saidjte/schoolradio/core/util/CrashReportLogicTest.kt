package com.saidjte.schoolradio.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** اختبارات v0.33.0 — منطق تقارير الأعطال: العدّ والمعاينة والملخص */
class CrashReportLogicTest {

    private val log =
        "===== 111 | UNCAUGHT on main =====\n" +
            "java.lang.RuntimeException: boom\n" +
            "===== 222 | UNCAUGHT on main =====\n" +
            "java.lang.IllegalStateException: bam\n"

    // ==================== hasReport ====================

    @Test
    fun hasReportRejectsNullBlankAndEmpty() {
        assertFalse(CrashReportLogic.hasReport(null))
        assertFalse(CrashReportLogic.hasReport(""))
        assertFalse(CrashReportLogic.hasReport("   "))
    }

    @Test
    fun hasReportAcceptsRealLog() {
        assertTrue(CrashReportLogic.hasReport(log))
    }

    // ==================== crashCount ====================

    @Test
    fun crashCountCountsSections() {
        assertEquals(2, CrashReportLogic.crashCount(log))
        assertEquals(0, CrashReportLogic.crashCount(null))
        assertEquals(0, CrashReportLogic.crashCount(""))
        assertEquals(1, CrashReportLogic.crashCount("===== 1 | x =====\nerr"))
    }

    // ==================== preview ====================

    @Test
    fun previewPassthroughForShortLog() {
        assertEquals(log.trim(), CrashReportLogic.preview(log))
    }

    @Test
    fun previewTailForHugeLog() {
        val huge = "x".repeat(9000) + "|LAST-500"
        val p = CrashReportLogic.preview(huge, maxChars = 500)
        assertTrue(p.startsWith("…"))
        assertTrue(p.endsWith("LAST-500"))
        assertEquals(501, p.length)
    }

    @Test
    fun previewEmptyGivesFriendlyText() {
        assertEquals(
            "لا توجد أعطال موثقة على هذا الجهاز",
            CrashReportLogic.preview(null)
        )
    }

    // ==================== summaryLabel ====================

    @Test
    fun summaryLabelVariants() {
        assertTrue(CrashReportLogic.summaryLabel(null).contains("لا أعطال"))
        assertTrue(CrashReportLogic.summaryLabel("===== 1 | x =====").contains("عطل واحد"))
        assertTrue(CrashReportLogic.summaryLabel(log).contains("أعطال"))
        assertTrue(CrashReportLogic.summaryLabel(log).startsWith("2"))
    }
}

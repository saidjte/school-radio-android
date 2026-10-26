package com.saidjte.schoolradio.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات v0.31.0 — منطق وضع الأداء: القرار التلقائي حسب قوة الجهاز
 * والبوابات التي تطفئ الزخارف الكريستالية والحركات الزخرفية.
 */
class PerformanceModeLogicTest {

    // ==================== الافتراضي التلقائي ====================

    @Test
    fun autoDefaultOnLowRamFlag() {
        // جهاز مصنّف ضعيف الذاكرة من النظام — وضع الأداء فوراً مهما كانت الذاكرة المعلنة
        assertTrue(PerformanceModeLogic.autoDefault(isLowRamDevice = true, totalMemMb = 8192, apiLevel = 34))
    }

    @Test
    fun autoDefaultOnSmallTotalMemory() {
        // ذاكرة إجمالية تحت 3GB — هاتف مدرسي متوسط/قديم
        assertTrue(PerformanceModeLogic.autoDefault(false, 2048, 30))
        assertTrue(PerformanceModeLogic.autoDefault(false, 1024, 33))
    }

    @Test
    fun autoDefaultOnOldAndroid() {
        // أندرويد 7 وما قبله — التدرجات الكاملة ثقيلة على إطار العرض القديم
        assertTrue(PerformanceModeLogic.autoDefault(false, 4096, 25))
        assertTrue(PerformanceModeLogic.autoDefault(false, 6144, 24))
    }

    @Test
    fun autoDefaultOffOnCapableDevice() {
        // جهاز قوي حديث: ذاكرة جيدة + أندرويد 8+ + ليس ضعيف الذاكرة
        assertFalse(PerformanceModeLogic.autoDefault(false, 4096, 29))
        assertFalse(PerformanceModeLogic.autoDefault(false, 8192, 34))
        assertFalse(PerformanceModeLogic.autoDefault(false, 3000, 26))
    }

    @Test
    fun autoDefaultBoundaryExactly3GB() {
        // 3000MB بالضبط ليست أقل من العتبة — جهاز مقبول يبقى كريستالياً
        assertFalse(PerformanceModeLogic.autoDefault(false, 3000, 30))
        // أقل بعشرين ميغا فقط — يقلب القرار
        assertTrue(PerformanceModeLogic.autoDefault(false, 2980, 30))
    }

    @Test
    fun autoDefaultAnyWeaknessSuffices() {
        // كفاية سبب واحد من الثلاثة — لا يشترط اجتماعها
        assertTrue(PerformanceModeLogic.autoDefault(true, 2048, 25))
    }

    // ==================== بوابات الزخارف ====================

    @Test
    fun crystalEnabledIsInverseOfPerformanceMode() {
        assertTrue(PerformanceModeLogic.crystalEnabled(performanceMode = false))
        assertFalse(PerformanceModeLogic.crystalEnabled(performanceMode = true))
    }

    @Test
    fun animationsEnabledIsInverseOfPerformanceMode() {
        assertTrue(PerformanceModeLogic.animationsEnabled(performanceMode = false))
        assertFalse(PerformanceModeLogic.animationsEnabled(performanceMode = true))
    }

    // ==================== تحويل الذاكرة ====================

    @Test
    fun bytesToMbConvertsShift() {
        assertEquals(4096, PerformanceModeLogic.bytesToMb(4L * 1024 * 1024 * 1024))
        assertEquals(2048, PerformanceModeLogic.bytesToMb(2L * 1024 * 1024 * 1024))
        assertEquals(0, PerformanceModeLogic.bytesToMb(0L))
    }

    // ==================== وصف الإعداد ====================

    @Test
    fun descriptionMentionsPerformanceWhenEnabled() {
        val d = PerformanceModeLogic.description(enabled = true)
        assertTrue(d.contains("مفعّل"))
        assertTrue(d.contains("أسرع") || d.contains("بطارية"))
    }

    @Test
    fun descriptionMentionsCrystalWhenDisabled() {
        val d = PerformanceModeLogic.description(enabled = false)
        assertTrue(d.contains("مطفأ"))
        assertTrue(d.contains("كريستال"))
    }

    // ==================== تكامل: مسار الجهاز الضعيف إلى البوابات ====================

    @Test
    fun weakDeviceEndsWithEverythingOff() {
        val perf = PerformanceModeLogic.autoDefault(false, 2048, 27)
        assertTrue(perf)
        assertFalse(PerformanceModeLogic.crystalEnabled(perf))
        assertFalse(PerformanceModeLogic.animationsEnabled(perf))
    }

    @Test
    fun strongDeviceKeepsFullCrystal() {
        val perf = PerformanceModeLogic.autoDefault(false, 6144, 31)
        assertFalse(perf)
        assertTrue(PerformanceModeLogic.crystalEnabled(perf))
        assertTrue(PerformanceModeLogic.animationsEnabled(perf))
    }
}

package com.saidjte.schoolradio.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات وحدة قص الفراغ الأول (v0.18.0) — منطق صافٍ بلا أندرويد.
 * نافذة القياس الافتراضية 20ms لكل قمة سعة.
 */
class SilenceLogicTest {

    // ===== leadingSilenceMs =====

    @Test fun `immediate sound means no trim`() {
        // أول نافذة تحمل صوتاً — لا فراغ أصلاً
        val trim = SilenceLogic.leadingSilenceMs(listOf(800, 900, 1000))
        assertEquals(0L, trim)
    }

    @Test fun `three silent windows then sound`() {
        // 3 نوافذ صامتة (60ms) ثم صوت → 60 - وسادة 60 = 0 (دون حد القص الأدنى)
        val trim = SilenceLogic.leadingSilenceMs(listOf(0, 0, 0, 2000))
        assertEquals(0L, trim)
    }

    @Test fun `five silent windows yields 40ms but below minimum`() {
        // 5×20 = 100ms صمت → بعد الوسادة 40ms < 100ms الحد الأدنى → لا قص
        val trim = SilenceLogic.leadingSilenceMs(listOf(0, 0, 0, 0, 0, 3000))
        assertEquals(0L, trim)
    }

    @Test fun `long silence is trimmed with padding kept`() {
        // 15 نافذة صامتة (300ms) → 300 - 60 = 240ms قص
        val peaks = List(15) { 0 } + listOf(1500)
        val trim = SilenceLogic.leadingSilenceMs(peaks)
        assertEquals(240L, trim)
    }

    @Test fun `quiet-but-above-threshold window counts as sound`() {
        // النافذة عند العتبة نفسها (500) تُعد صوتاً — نفَس خفيف لا يُقص
        val peaks = List(10) { 0 } + listOf(500)
        val trim = SilenceLogic.leadingSilenceMs(peaks)
        assertEquals(10 * 20L - 60L, trim)
    }

    @Test fun `entirely silent analysis yields no trim`() {
        // لم نجد أي صوت فيما حللناه — لا نقطع الملف كله
        val trim = SilenceLogic.leadingSilenceMs(List(40) { 0 })
        assertEquals(0L, trim)
    }

    @Test fun `empty peaks yields no trim`() {
        assertEquals(0L, SilenceLogic.leadingSilenceMs(emptyList()))
    }

    @Test fun `custom window and threshold respected`() {
        // نوافذ 50ms وعتبة أعلى (1000): 300 و900 تحتها — أول صوت النافذة السادسة
        // (250ms صمت − 60 وسادة = 190ms ≥ الحد الأدنى فتُقبل)
        val trim = SilenceLogic.leadingSilenceMs(
            peaks = listOf(0, 0, 0, 0, 300, 1200),
            windowMs = 50L,
            threshold = 1000
        )
        assertEquals(5 * 50L - SilenceLogic.KEEP_PADDING_MS, trim)
    }

    @Test fun `trim below minimum is discarded`() {
        // 90ms بعد الوسادة < 100ms الحد الأدنى → 0 (لا إزاحة لا تُسمح تستحقها)
        val trim = SilenceLogic.leadingSilenceMs(
            peaks = listOf(0, 0, 300, 1200),
            windowMs = 50L,
            threshold = 1000
        )
        assertEquals(0L, trim)
    }

    @Test fun `padding never produces negative trim`() {
        // صمت واحد فقط (20ms) — الوسادة أكبر منه، النتيجة تُسقّط للحد الأدنى 0
        val trim = SilenceLogic.leadingSilenceMs(listOf(0, 4000))
        assertTrue(trim >= 0L)
        assertEquals(0L, trim)
    }

    // ===== الرسائل =====

    @Test fun `message for zero trim`() {
        val m = SilenceLogic.message(0L, "نشيد الصباح")
        assertTrue(m.contains("لا فراغ"))
        assertTrue(m.contains("نشيد الصباح"))
    }

    @Test fun `message for sub-second trim`() {
        val m = SilenceLogic.message(240L, "جينل")
        assertTrue(m.contains("240"))
    }

    @Test fun `message for seconds trim uses western digits`() {
        val m = SilenceLogic.message(2400L, "نشيد")
        assertTrue(m.contains("2.4"))
        // لا أرقام مشرقية
        assertTrue(!m.any { it in '٠'..'٩' })
    }
}

package com.saidjte.schoolradio.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات v0.33.0 — حارس تشغيل التسجيلات: تنقية القص ضد القيم التالفة
 * ورسائل الفشل العربية الجاهزة.
 */
class PlaybackGuardLogicTest {

    // ==================== تنقية القص ====================

    @Test
    fun normalClipPreserved() {
        // قيم سليمة من محرر الموجة — تمر كما هي
        val c = PlaybackGuardLogic.sanitizeClip(2000, 8000, 10000)
        assertEquals(2000L, c.startMs)
        assertEquals(8000L, c.endMs)
    }

    @Test
    fun negativeStartClampedToZero() {
        val c = PlaybackGuardLogic.sanitizeClip(-500, 4000, 10000)
        assertEquals(0L, c.startMs)
        assertEquals(4000L, c.endMs)
    }

    @Test
    fun startBeyondDurationClampedBeforeMinSegment() {
        // بداية تالفة بعد نهاية الملف — تُقيد قبل أقل جزء مسموع (1000ms)
        val c = PlaybackGuardLogic.sanitizeClip(99999, 0, 10000)
        assertEquals(9000L, c.startMs)
        assertNull(c.endMs)
    }

    @Test
    fun endBeforeStartDropped() {
        // نهاية قبل البداية — تسقط وتشغّل حتى النهاية الطبيعية
        val c = PlaybackGuardLogic.sanitizeClip(5000, 3000, 10000)
        assertEquals(5000L, c.startMs)
        assertNull(c.endMs)
    }

    @Test
    fun endEqualToStartDropped() {
        val c = PlaybackGuardLogic.sanitizeClip(4000, 4000, 10000)
        assertEquals(4000L, c.startMs)
        assertNull(c.endMs)
    }

    @Test
    fun endBeyondKnownDurationDropped() {
        // نهاية تتجاوز المدة الفعلية المعروفة — تسقط بدل قص غير شرعي
        val c = PlaybackGuardLogic.sanitizeClip(1000, 999999, 10000)
        assertEquals(1000L, c.startMs)
        assertNull(c.endMs)
    }

    @Test
    fun unknownDurationKeepsNumericBoundsOnly() {
        // مدة غير معروفة (0) — القص الرقمي السليم يبقى، والبداية ≥ 0 فقط
        val c = PlaybackGuardLogic.sanitizeClip(1500, 6000, 0)
        assertEquals(1500L, c.startMs)
        assertEquals(6000L, c.endMs)
    }

    @Test
    fun unknownDurationWithNegativeStartStillSafe() {
        val c = PlaybackGuardLogic.sanitizeClip(-99, -50, 0)
        assertEquals(0L, c.startMs)
        assertNull(c.endMs)
    }

    @Test
    fun veryShortDurationStartsAtZero() {
        // مدة أقصر من أقل جزء مسموع — البداية صفر دائماً
        val c = PlaybackGuardLogic.sanitizeClip(800, 0, 500)
        assertEquals(0L, c.startMs)
        assertNull(c.endMs)
    }

    @Test
    fun allGarbageValuesNeverCrash() {
        // أسوأ حالة تخزين ممكنة — نتيجة صالحة دائماً وحدود غير سالبة
        val c = PlaybackGuardLogic.sanitizeClip(Long.MIN_VALUE, Long.MAX_VALUE, Long.MAX_VALUE)
        assertTrue(c.startMs >= 0)
        assertTrue(c.endMs == null || c.endMs!! > c.startMs)
    }

    // ==================== الرسائل ====================

    @Test
    fun startFailureMessageIsArabicAndActionable() {
        val m = PlaybackGuardLogic.startFailureMessage()
        assertTrue(m.contains("تعذر"))
        assertTrue(m.contains("أعد"))
    }

    @Test
    fun decodeFailureMessageMentionsFormat() {
        val m = PlaybackGuardLogic.decodeFailureMessage()
        assertNotNull(m)
        assertTrue(m.contains("صيغة") || m.contains("تالفاً"))
    }
}

package com.saidjte.schoolradio

import com.saidjte.schoolradio.core.util.WaveformLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات رياضيات الموجة الحية (v0.12.0) — JVM خالص.
 */
class WaveformLogicTest {

    @Test
    fun `الصمت وأرضية الضجيج تعود صفراً`() {
        assertEquals(0f, WaveformLogic.normalize(0f), 1e-6f)
        assertEquals(0f, WaveformLogic.normalize(-0.5f), 1e-6f)
        assertEquals(0f, WaveformLogic.normalize(0.0039f), 1e-6f)
    }

    @Test
    fun `NaN يُعامل صمتاً بأمان`() {
        assertEquals(0f, WaveformLogic.normalize(Float.NaN), 1e-6f)
    }

    @Test
    fun `القمة الكاملة تتشبع عند واحد`() {
        assertEquals(1f, WaveformLogic.normalize(1f), 1e-6f)
        assertEquals(1f, WaveformLogic.normalize(2f), 1e-6f)
    }

    @Test
    fun `المنحنى جذري — الهمس يصبح مرئياً أكثر من الخطي`() {
        // مستوى هامس 0.01: الخطي يعطي ~0.006 (غير مرئي)، الجذري أعلى بوضوح
        val shaped = WaveformLogic.normalize(0.01f)
        assertTrue("الهامس يجب أن يظهر", shaped > 0.05f)
        assertTrue(shaped < 0.3f) // ومع ذلك لا يصل نصف الشاشة
        // المستوى المتوسط أعلى من الهامس — الترتيب الإدراكي محفوظ
        assertTrue(shaped < WaveformLogic.normalize(0.1f))
    }

    @Test
    fun `الترتيب أحادي الصعود — أعلى RMS يعطي ارتفاعاً أعلى أو مساوياً`() {
        var prev = -1f
        val samples = listOf(0f, 0.005f, 0.02f, 0.1f, 0.3f, 0.6f, 0.9f, 1f, 1.5f)
        for (s in samples) {
            val v = WaveformLogic.normalize(s)
            assertTrue("القيمة يجب أن تكون 0..1: $v", v in 0f..1f)
            assertTrue("التسلسل صاعد: $prev -> $v", v >= prev)
            prev = v
        }
    }

    @Test
    fun `التنعيم - الصعود فوري والهبوط هادئ`() {
        // صعود: القيمة الجديدة تتبناى فوراً
        assertEquals(0.8f, WaveformLogic.smooth(0.1f, 0.8f), 1e-6f)
        // هبوط: يبقى جزء من القيمة السابقة — لا سقوط حاد
        val dropped = WaveformLogic.smooth(0.8f, 0.1f)
        assertTrue(dropped > 0.1f && dropped < 0.8f)
        // decay صفر = تتبع مباشر
        assertEquals(0.1f, WaveformLogic.smooth(0.8f, 0.1f, decay = 0f), 1e-6f)
    }

    @Test
    fun `النافذة المنزلقة تحفظ آخر العناصر فقط بالترتيب`() {
        val window = mutableListOf<Float>()
        repeat(100) { i -> WaveformLogic.pushWindow(window, i / 100f) }
        assertEquals(WaveformLogic.HISTORY_BARS, window.size)
        // الأحدث في النهاية
        assertEquals(0.99f, window.last(), 1e-4f)
        // الأقدم المتبقي هو العنصر (100 - السعة)
        val expectedOldest = (100 - WaveformLogic.HISTORY_BARS) / 100f
        assertEquals(expectedOldest, window.first(), 1e-4f)
    }
}

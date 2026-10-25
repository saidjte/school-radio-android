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

    // =====================================================================
    // v0.20.0 — مغلف الشكل الموجي لمحرر القص
    // =====================================================================

    @Test
    fun `المغلف الفارغ يعيد مصفوفة فارغة بأمان`() {
        assertTrue(WaveformLogic.envelope(IntArray(0), 96).isEmpty())
        assertTrue(WaveformLogic.envelope(intArrayOf(100, 200), 0).isEmpty())
        assertTrue(WaveformLogic.envelope(intArrayOf(100, 200), -3).isEmpty())
    }

    @Test
    fun `المغلف يرسم ملفا صامتا كل الأعمدة أصفار بلا قسمة على صفر`() {
        val out = WaveformLogic.envelope(IntArray(500) { 0 }, 96)
        assertEquals(96, out.size)
        assertTrue(out.all { it == 0f })
    }

    @Test
    fun `المغلف يطبع على القمة الكلية — القمة القصوى تصل واحدا`() {
        // قمة واحدة كبيرة وسط قيم صغيرة
        val peaks = IntArray(1000) { if (it == 500) 30000 else 3000 }
        val out = WaveformLogic.envelope(peaks, 100)
        assertEquals(100, out.size)
        assertEquals(1f, out.max(), 1e-4f)
        // عمود غير القمة أقل بكثير (جذر النسبة 3000/30000 = ~0.316)
        val other = out[10]
        assertTrue("القيم غير القصوى أقل من الواحد: $other", other in 0.25f..0.35f)
    }

    @Test
    fun `المغلف يجمع بالقمة القصوى لا المتوسط — النبضة القصيرة لا تختفي`() {
        // خانة عرض واحدة تحتوي قيمة صفرية + نبضة عالية → العمود يعكس النبضة
        val peaks = IntArray(200) { 10 } // 200 نافذة → عمودان عند columns=2
        peaks[0] = 20000 // نبضة قصيرة جداً في بداية الخانة الأولى
        val out = WaveformLogic.envelope(peaks, 2)
        // الخانة الأولى يجب أن تعكس القمة 20000 (وأول عمود هو القمة الكلية → 1.0)
        assertEquals(1f, out[0], 1e-4f)
        // الخانة الثانية منخفضة (كل قيمها 10 → جذر نسبة صغيرة)
        assertTrue("الخانة الهادئة أقل بكثير: ${out[1]}", out[1] < 0.1f)
    }

    @Test
    fun `قيم سالبة أو فوضوية في القمم تعالج بأمان`() {
        val peaks = intArrayOf(-5, 100, -200, 400)
        val out = WaveformLogic.envelope(peaks, 4)
        assertEquals(4, out.size)
        assertTrue(out.all { it >= 0f })
        assertTrue(out.all { it <= 1f })
    }

    @Test
    fun `msAtFraction يحول موضع اللمس إلى زمن داخل الحدود`() {
        assertEquals(0L, WaveformLogic.msAtFraction(-0.5f, 60_000L))
        assertEquals(60_000L, WaveformLogic.msAtFraction(1.7f, 60_000L))
        assertEquals(30_000L, WaveformLogic.msAtFraction(0.5f, 60_000L))
        assertEquals(0L, WaveformLogic.msAtFraction(0.5f, 0L))
    }
}

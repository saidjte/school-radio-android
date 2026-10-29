package com.saidjte.schoolradio.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max

/** عقد نغمة إعلان بدء البث — v0.49.0 */
class BroadcastToneLogicTest {

    @Test
    fun `الطول الكلي = 3 نغمات 190ms + ختام 420ms عند 48k`() {
        // 3×190 + 420 = 990ms × 48 عينة/ms
        assertEquals(47_520, BroadcastToneLogic.totalSamples(48_000))
    }

    @Test
    fun `المقطع المولد طوله يساوي المعلن`() {
        val tone = BroadcastToneLogic.generate(ascending = true)
        assertEquals(BroadcastToneLogic.totalSamples(), tone.size)
    }

    @Test
    fun `أول عينة وآخر عينة صفر — بلا طقطقة هجوم أو توقف`() {
        val tone = BroadcastToneLogic.generate(ascending = true)
        assertEquals(0, tone.first().toInt())
        assertEquals(0, tone.last().toInt())
    }

    @Test
    fun `الذروة داخل الهامش الآمن لمكبرات الاستوديو`() {
        val tone = BroadcastToneLogic.generate(ascending = true)
        var peak = 0
        for (s in tone) peak = max(peak, abs(s.toInt()))
        // ذروة التصميم 0.5 × 32767 = 16383 — هامش التسامح يحمي من تقريب الجيب فقط
        assertTrue("peak=$peak", peak > 0.40 * Short.MAX_VALUE)
        assertTrue("peak=$peak", peak <= 0.55 * Short.MAX_VALUE)
    }

    @Test
    fun `التوليد حتمي — ضغطتان متتاليتان تصنعان النغمة نفسها`() {
        val a = BroadcastToneLogic.generate(ascending = true)
        val b = BroadcastToneLogic.generate(ascending = true)
        assertTrue(a.contentEquals(b))
    }

    @Test
    fun `الصعود والنزول مقطوعتان مختلفتان لا نفس المقاطع`() {
        val up = BroadcastToneLogic.generate(ascending = true)
        val down = BroadcastToneLogic.generate(ascending = false)
        assertTrue(!up.contentEquals(down))
    }

    @Test
    fun `الطول يتلاءم مع معدلات عينة أخرى غير 48k`() {
        assertEquals(9_900, BroadcastToneLogic.totalSamples(10_000))
    }
}

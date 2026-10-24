package com.saidjte.schoolradio.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات الميكسر الاحترافي (v0.15.0) — منطق صافٍ على JVM:
 * القناة الفعّالة = الفيدَر × الماستر، الكتم = صفر مؤقت،
 * والميك خط مباشر لا يخضع للماستر.
 */
class MixerLogicTest {

    @Test
    fun `effective volume multiplies fader by master for music channels`() {
        val v = MixerLogic.effectiveVolume(MixerLogic.Channel.MUSIC, 0.8f, 0.5f, muted = false)
        assertEquals(0.4f, v, 0.0001f)
    }

    @Test
    fun `effective volume ignores master for mic channel`() {
        val v = MixerLogic.effectiveVolume(MixerLogic.Channel.MIC, 1.5f, 0.2f, muted = false)
        assertEquals(1.5f, v, 0.0001f)
    }

    @Test
    fun `muted channel is zero but fader preserved by caller`() {
        val v = MixerLogic.effectiveVolume(MixerLogic.Channel.BREAKS, 0.9f, 1.0f, muted = true)
        assertEquals(0f, v, 0.0001f)
    }

    @Test
    fun `master channel itself does not obey master`() {
        val v = MixerLogic.effectiveVolume(MixerLogic.Channel.MASTER, 0.7f, 0.3f, muted = false)
        assertEquals(0.7f, v, 0.0001f)
    }

    @Test
    fun `obeysMaster distinguishes media channels from direct lines`() {
        assertTrue(MixerLogic.obeysMaster(MixerLogic.Channel.MUSIC))
        assertTrue(MixerLogic.obeysMaster(MixerLogic.Channel.BREAKS))
        assertTrue(MixerLogic.obeysMaster(MixerLogic.Channel.PREVIEW))
        assertFalse(MixerLogic.obeysMaster(MixerLogic.Channel.MIC))
        assertFalse(MixerLogic.obeysMaster(MixerLogic.Channel.MASTER))
    }

    @Test
    fun `clampFader allows mic up to 2 and caps others at 1`() {
        assertEquals(2f, MixerLogic.clampFader(MixerLogic.Channel.MIC, 5f), 0.0001f)
        assertEquals(0f, MixerLogic.clampFader(MixerLogic.Channel.MIC, -1f), 0.0001f)
        assertEquals(1f, MixerLogic.clampFader(MixerLogic.Channel.MUSIC, 5f), 0.0001f)
        assertEquals(0.5f, MixerLogic.clampFader(MixerLogic.Channel.PREVIEW, 0.5f), 0.0001f)
    }

    @Test
    fun `fader text shows muted label at zero`() {
        assertEquals("مكتوم", MixerLogic.faderText(MixerLogic.Channel.MUSIC, 0f))
        assertEquals("100٪", MixerLogic.faderText(MixerLogic.Channel.MUSIC, 1f))
        assertEquals("200٪", MixerLogic.faderText(MixerLogic.Channel.MIC, 2f))
    }

    @Test
    fun `announcement scene boosts mic and deepens duck`() {
        val s = MixerLogic.Scene.ANNOUNCEMENT.set
        assertTrue(s.mic > 1f)
        assertTrue(s.duckEnabled)
        assertTrue(s.duckVolume < 0.25f)
    }

    @Test
    fun `music only scene silences mic`() {
        val s = MixerLogic.Scene.MUSIC_ONLY.set
        assertEquals(0f, s.mic, 0.0001f)
    }

    @Test
    fun `full program scene uses balanced defaults`() {
        val s = MixerLogic.Scene.FULL.set
        assertEquals(1f, s.mic, 0.0001f)
        assertEquals(1f, s.master, 0.0001f)
    }

    @Test
    fun `break mode scene raises breaks fader`() {
        val s = MixerLogic.Scene.BREAK_MODE.set
        assertTrue(s.breaks >= 1f)
    }
}

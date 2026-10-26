package com.saidjte.schoolradio.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod

/**
 * v0.42.0 — اختبارات اختيار التدفق الصوتي بعد التصحيح الجذري: التقدمية
 * حصراً — قوائم HLS/DASH المظللة بصيغة M4A وبمعدلات بت أعلى لم تعد تفوز
 * قط (كانت تنزَّل ملفاً نصياً تالفاً وتُبثّ بفشل مضمون بلا وحدات media3-hls).
 */
class PickAudioLogicTest {

    private fun stream(
        url: String,
        format: MediaFormat,
        bitrate: Int,
        method: DeliveryMethod
    ): AudioStream = AudioStream.Builder()
        .setId("test-stream")
        .setContent(url, true)
        .setMediaFormat(format)
        .setDeliveryMethod(method)
        .setAverageBitrate(bitrate)
        .build()

    // ================== التصحيح الجذري: التقدمية حصراً ==================

    @Test fun `hls playlist with higher bitrate never beats progressive audio`() {
        val hls = stream("https://manifest.example.com/master.m3u8", MediaFormat.M4A, 258, DeliveryMethod.HLS)
        val progressive = stream("https://cdn.example.com/audio.m4a", MediaFormat.M4A, 128, DeliveryMethod.PROGRESSIVE_HTTP)
        assertEquals(progressive.url, StreamResolver.pickAudio(listOf(hls, progressive))?.url)
    }

    @Test fun `dash manifest is excluded`() {
        val dash = stream("https://manifest.example.com/audio.mpd", MediaFormat.M4A, 300, DeliveryMethod.DASH)
        val progressive = stream("https://cdn.example.com/audio.m4a", MediaFormat.M4A, 96, DeliveryMethod.PROGRESSIVE_HTTP)
        assertEquals(progressive.url, StreamResolver.pickAudio(listOf(dash, progressive))?.url)
    }

    @Test fun `only hls available yields null instead of a corrupt playlist`() {
        val hls = stream("https://manifest.example.com/master.m3u8", MediaFormat.M4A, 192, DeliveryMethod.HLS)
        assertNull(StreamResolver.pickAudio(listOf(hls)))
    }

    // ================== القواعد القديمة المحفوظة ==================

    @Test fun `plain http stream is excluded`() {
        val http = stream("http://cdn.example.com/audio.m4a", MediaFormat.M4A, 320, DeliveryMethod.PROGRESSIVE_HTTP)
        val https = stream("https://cdn.example.com/audio.m4a", MediaFormat.M4A, 128, DeliveryMethod.PROGRESSIVE_HTTP)
        assertEquals(https.url, StreamResolver.pickAudio(listOf(http, https))?.url)
    }

    @Test fun `m4a preferred over opus webm at equal bitrate`() {
        val webm = stream("https://cdn.example.com/audio.webm", MediaFormat.WEBMA_OPUS, 160, DeliveryMethod.PROGRESSIVE_HTTP)
        val m4a = stream("https://cdn.example.com/audio.m4a", MediaFormat.M4A, 160, DeliveryMethod.PROGRESSIVE_HTTP)
        assertEquals(m4a.url, StreamResolver.pickAudio(listOf(webm, m4a))?.url)
    }

    @Test fun `highest progressive bitrate wins among m4a`() {
        val low = stream("https://cdn.example.com/low.m4a", MediaFormat.M4A, 48, DeliveryMethod.PROGRESSIVE_HTTP)
        val high = stream("https://cdn.example.com/high.m4a", MediaFormat.M4A, 256, DeliveryMethod.PROGRESSIVE_HTTP)
        val mid = stream("https://cdn.example.com/mid.m4a", MediaFormat.M4A, 128, DeliveryMethod.PROGRESSIVE_HTTP)
        assertEquals(high.url, StreamResolver.pickAudio(listOf(low, mid, high))?.url)
    }

    @Test fun `empty stream list yields null`() {
        assertNull(StreamResolver.pickAudio(emptyList()))
    }
}

package com.saidjte.schoolradio.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod

/**
 * v0.42.0/v0.46.0 — اختبارات اختيار التدفق الصوتي بعد هرمية واقعية:
 * التقدمية أولاً (يوتوب — ملف واحد قابل للتنزيل بمحارف Range)، وإلا HLS
 * (الإثبات التجريبي: ساوندكلاود أسقطت التقدمية عن محتواها فكانت رسالة
 * «لا يوجد تدفق» كاذبة — والآن المشغل يدعم HLS بوحدة media3-exoplayer-hls).
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

    // ================== الهرمية: التقدمية أولاً وإلا HLS ==================

    @Test fun `progressive preferred over hls when both exist`() {
        val hls = stream("https://manifest.example.com/master.m3u8", MediaFormat.M4A, 258, DeliveryMethod.HLS)
        val progressive = stream("https://cdn.example.com/audio.m4a", MediaFormat.M4A, 128, DeliveryMethod.PROGRESSIVE_HTTP)
        assertEquals(progressive.url, StreamResolver.pickAudio(listOf(hls, progressive))?.url)
    }

    @Test fun `dash manifest is excluded`() {
        val dash = stream("https://manifest.example.com/audio.mpd", MediaFormat.M4A, 300, DeliveryMethod.DASH)
        val progressive = stream("https://cdn.example.com/audio.m4a", MediaFormat.M4A, 96, DeliveryMethod.PROGRESSIVE_HTTP)
        assertEquals(progressive.url, StreamResolver.pickAudio(listOf(dash, progressive))?.url)
    }

    // ================== v0.46.0: هرمية HLS = ساوندكلاود بعد إسقاط التقدمية ==================

    @Test fun `hls-only track picks the hls stream instead of false error`() {
        // الحالة المبلغ عنها حرفياً: المقطع المفحوص أعاد HLS حصراً (mime audio/mp4)
        val hls = stream(
            "https://playback.media-streaming.soundcloud.cloud/x/playlist.m3u8?expires=1",
            MediaFormat.M4A, 160, DeliveryMethod.HLS
        )
        assertEquals(hls.url, StreamResolver.pickAudio(listOf(hls))?.url)
    }

    @Test fun `within hls media playlist (mp4 mime) preferred over master playlist`() {
        val master = stream("https://p.example.com/playlist.m3u8?e=1", MediaFormat.MP3, 128, DeliveryMethod.HLS)
        val media = stream("https://p.example.com/media.m3u8?e=1", MediaFormat.M4A, 160, DeliveryMethod.HLS)
        assertEquals(media.url, StreamResolver.pickAudio(listOf(master, media))?.url)
    }

    @Test fun `within hls highest bitrate wins`() {
        val low = stream("https://p.example.com/96k.m3u8?e=1", MediaFormat.M4A, 96, DeliveryMethod.HLS)
        val high = stream("https://p.example.com/160k.m3u8?e=1", MediaFormat.M4A, 160, DeliveryMethod.HLS)
        assertEquals(high.url, StreamResolver.pickAudio(listOf(low, high))?.url)
    }

    @Test fun `http-only hls is excluded`() {
        val http = stream("http://p.example.com/media.m3u8?e=1", MediaFormat.M4A, 160, DeliveryMethod.HLS)
        assertNull(StreamResolver.pickAudio(listOf(http)))
    }

    @Test fun `dash-only still yields null`() {
        val dash = stream("https://m.example.com/audio.mpd", MediaFormat.M4A, 300, DeliveryMethod.DASH)
        assertNull(StreamResolver.pickAudio(listOf(dash)))
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

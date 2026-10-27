package com.saidjte.schoolradio.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.46.0 — اختبارات تحليل قوائم HLS للتنزيل المحلي، بعينات مأخوذة من الشكل
 * الفعلي المُثبَّت تجريبياً لقوائم ساوندكلاود (init.mp4 + dataNNN.m4s بلا
 * تشفير) وحالات الرفض الصادقة.
 */
class HlsPlaylistLogicTest {

    private val base = "https://playback.example.com/abc/aac_160k/uuid/playlist.m3u8?expires=1"

    private fun cloudSample(vararg extraSegments: String = emptyArray()): String = buildString {
        appendLine("#EXTM3U")
        appendLine("#EXT-X-VERSION:7")
        appendLine("#EXT-X-TARGETDURATION:10")
        appendLine("#EXT-X-MEDIA-SEQUENCE:0")
        appendLine("#EXT-X-PLAYLIST-TYPE:VOD")
        appendLine(
            "#EXT-X-MAP:URI=\"https://playback.example.com/abc/aac_160k/uuid/init.mp4?expires=1\""
        )
        appendLine("#EXTINF:10.007800,")
        appendLine("https://playback.example.com/abc/aac_160k/uuid/data000.m4s?expires=1")
        appendLine("#EXTINF:9.984580,")
        appendLine("https://playback.example.com/abc/aac_160k/uuid/data001.m4s?expires=1")
        for (s in extraSegments) appendLine(s)
    }

    // ================== قائمة وسائط سليمة ==================

    @Test fun `media playlist yields init and ordered segments`() {
        val r = HlsPlaylistLogic.parse(base, cloudSample())
        val media = r as HlsPlaylistLogic.Result.Media
        assertEquals(
            "https://playback.example.com/abc/aac_160k/uuid/init.mp4?expires=1",
            media.playlist.initUrl
        )
        assertEquals(2, media.playlist.segments.size)
        assertTrue(media.playlist.segments[0].endsWith("data000.m4s?expires=1"))
        assertTrue(media.playlist.segments[1].endsWith("data001.m4s?expires=1"))
        // مدة EXTINF المجموعة تقدير صادق
        assertEquals(19.992380, media.playlist.totalDurationSec, 0.0001)
    }

    @Test fun `segments without init map parse fine`() {
        val body = "#EXTM3U\n#EXTINF:2.0,\na.ts\n#EXTINF:3.0,\nb.ts\n"
        val r = HlsPlaylistLogic.parse(base, body) as HlsPlaylistLogic.Result.Media
        assertNull(r.playlist.initUrl)
        // دمج المسار النسبي مع مجلد القائمة (RFC 3986) دون وراثة query الأساس
        assertEquals(
            listOf(
                "https://playback.example.com/abc/aac_160k/uuid/a.ts",
                "https://playback.example.com/abc/aac_160k/uuid/b.ts"
            ),
            r.playlist.segments
        )
    }

    @Test fun `relative segment urls resolve against playlist url`() {
        val body = "#EXTM3U\n#EXT-X-MAP:URI=\"init.mp4\"\n#EXTINF:1.0,\nseg0.m4s\n"
        val r = HlsPlaylistLogic.parse(base, body) as HlsPlaylistLogic.Result.Media
        assertEquals("https://playback.example.com/abc/aac_160k/uuid/init.mp4", r.playlist.initUrl)
        assertEquals("https://playback.example.com/abc/aac_160k/uuid/seg0.m4s", r.playlist.segments[0])
    }

    // ================== الرفض الصادق ==================

    @Test fun `master playlist is named as master`() {
        val body = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=160000\nmedia.m3u8\n"
        assertEquals(HlsPlaylistLogic.Result.Master, HlsPlaylistLogic.parse(base, body))
    }

    @Test fun `encrypted playlist is refused`() {
        val body = "#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"k.key\"\n#EXTINF:1.0,\na.ts\n"
        assertEquals(HlsPlaylistLogic.Result.Encrypted, HlsPlaylistLogic.parse(base, body))
    }

    @Test fun `method none key is not encryption`() {
        val body = "#EXTM3U\n#EXT-X-KEY:METHOD=NONE\n#EXTINF:1.0,\na.ts\n"
        assertTrue(HlsPlaylistLogic.parse(base, body) is HlsPlaylistLogic.Result.Media)
    }

    @Test fun `html page is invalid`() {
        assertEquals(
            HlsPlaylistLogic.Result.Invalid,
            HlsPlaylistLogic.parse(base, "<html><body>403</body></html>")
        )
    }

    @Test fun `empty body is invalid`() {
        assertEquals(HlsPlaylistLogic.Result.Invalid, HlsPlaylistLogic.parse(base, ""))
        assertEquals(HlsPlaylistLogic.Result.Invalid, HlsPlaylistLogic.parse(base, "   \n  "))
    }

    @Test fun `m3u8 without segments is invalid`() {
        assertEquals(
            HlsPlaylistLogic.Result.Invalid,
            HlsPlaylistLogic.parse(base, "#EXTM3U\n#EXT-X-VERSION:7\n")
        )
    }

    @Test fun `garbage lines never enter segments`() {
        val r = HlsPlaylistLogic.parse(base, cloudSample()) as HlsPlaylistLogic.Result.Media
        assertTrue(r.playlist.segments.none { it.startsWith("#") })
        assertTrue(r.playlist.segments.none { it.isBlank() })
    }
}

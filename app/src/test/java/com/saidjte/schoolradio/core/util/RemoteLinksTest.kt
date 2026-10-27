package com.saidjte.schoolradio.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.23.0 — اختبارات التعرّف على روابط الوسائط من المنصات المدعومة.
 * تغطي كل أشكال الروابط الشائعة + الحالات المرفوضة بتعليل واضح.
 */
class RemoteLinksTest {

    // ================== يوتوب ==================

    @Test fun `youtube watch link`() {
        val p = RemoteLinks.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertEquals(RemoteLinks.Source.YOUTUBE, p!!.source)
        assertEquals("dQw4w9WgXcQ", p.mediaId)
        assertTrue(p.playable)
    }

    @Test fun `youtube short link youtu be`() {
        val p = RemoteLinks.parse("https://youtu.be/dQw4w9WgXcQ?si=abc")
        assertEquals(RemoteLinks.Source.YOUTUBE, p!!.source)
        assertEquals("dQw4w9WgXcQ", p.mediaId)
    }

    @Test fun `youtube shorts link`() {
        val p = RemoteLinks.parse("https://www.youtube.com/shorts/abcdefghijk")
        assertEquals(RemoteLinks.Source.YOUTUBE, p!!.source)
        assertEquals("abcdefghijk", p.mediaId)
    }

    @Test fun `youtube live and embed links`() {
        assertEquals("abcdefghijk", RemoteLinks.parse("https://youtube.com/live/abcdefghijk")!!.mediaId)
        assertEquals("abcdefghijk", RemoteLinks.parse("https://www.youtube.com/embed/abcdefghijk")!!.mediaId)
        assertEquals("abcdefghijk", RemoteLinks.parse("https://music.youtube.com/watch?v=abcdefghijk")!!.mediaId)
    }

    @Test fun `youtube watch with extra params`() {
        val p = RemoteLinks.parse("https://www.youtube.com/watch?t=120&v=xYz_12345-A&list=PL123")
        assertEquals("xYz_12345-A", p!!.mediaId)
    }

    @Test fun `youtube id validation 11 chars`() {
        assertTrue(RemoteLinks.isValidYouTubeId("dQw4w9WgXcQ"))
        assertFalse(RemoteLinks.isValidYouTubeId("short"))
        assertFalse(RemoteLinks.isValidYouTubeId("too_long_id_here!!"))
        assertFalse(RemoteLinks.isValidYouTubeId(null))
    }

    @Test fun `youtube channel link blocked with reason`() {
        val p = RemoteLinks.parse("https://www.youtube.com/@somechannel")
        assertNotNull(p)
        assertFalse(p!!.playable)
        assertNotNull(p.blockedReason)
    }

    // ================== SoundCloud ==================

    @Test fun `soundcloud track link`() {
        val p = RemoteLinks.parse("https://soundcloud.com/artist-name/track-name-2")
        assertEquals(RemoteLinks.Source.SOUNDCLOUD, p!!.source)
        assertEquals("artist-name/track-name-2", p.mediaId)
        assertTrue(p.playable)
    }

    @Test fun `soundcloud set blocked with reason`() {
        val p = RemoteLinks.parse("https://soundcloud.com/artist/sets/playlist-x")
        assertNotNull(p)
        assertFalse(p!!.playable)
        assertTrue(p.blockedReason!!.contains("Sets"))
    }

    @Test fun `soundcloud profile blocked`() {
        val p = RemoteLinks.parse("https://soundcloud.com/artist-name")
        assertNotNull(p)
        assertFalse(p!!.playable)
    }

    // v0.44.0: روابط المشاركة المختصرة — صيغة زر «مشاركة» القياسية في تطبيق ساوندكلاود
    @Test fun `soundcloud short share link is playable`() {
        val p = RemoteLinks.parse("https://on.soundcloud.com/qY9nKp")
        assertEquals(RemoteLinks.Source.SOUNDCLOUD, p!!.source)
        assertTrue(p.playable)
        assertNull(p.blockedReason)
    }

    @Test fun `soundcloud short share link with query params is playable`() {
        val p = RemoteLinks.parse("https://on.soundcloud.com/Ab3xYz?si=share-token")
        assertEquals(RemoteLinks.Source.SOUNDCLOUD, p!!.source)
        assertTrue(p.playable)
    }

    @Test fun `soundcloud short share link without code is blocked honestly`() {
        val p = RemoteLinks.parse("https://on.soundcloud.com")
        assertNotNull(p)
        assertFalse(p!!.playable)
    }

    @Test fun `soundcloud mobile m link parses as track`() {
        val p = RemoteLinks.parse("https://m.soundcloud.com/artist-name/track-name")
        assertEquals(RemoteLinks.Source.SOUNDCLOUD, p!!.source)
        assertEquals("artist-name/track-name", p.mediaId)
        assertTrue(p.playable)
    }

    // ================== Google Drive ==================

    @Test fun `drive file d link`() {
        val p = RemoteLinks.parse("https://drive.google.com/file/d/1AbCdEfGhIjK/view?usp=sharing")
        assertEquals(RemoteLinks.Source.GOOGLE_DRIVE, p!!.source)
        assertEquals("1AbCdEfGhIjK", p.mediaId)
        assertTrue(p.playable)
    }

    @Test fun `drive open id link`() {
        val p = RemoteLinks.parse("https://drive.google.com/open?id=FILE123")
        assertEquals("FILE123", p!!.mediaId)
    }

    @Test fun `drive uc id link`() {
        val p = RemoteLinks.parse("https://drive.google.com/uc?id=FILE456&export=download")
        assertEquals("FILE456", p!!.mediaId)
    }

    @Test fun `drive folder blocked with reason`() {
        val p = RemoteLinks.parse("https://drive.google.com/drive/folders/1XyZ")
        assertNotNull(p)
        assertFalse(p!!.playable)
        assertNotNull(p.blockedReason)
    }

    // ================== روابط مباشرة ==================

    @Test fun `direct audio link`() {
        val p = RemoteLinks.parse("https://example.com/nashid.mp3")
        assertEquals(RemoteLinks.Source.DIRECT, p!!.source)
        assertTrue(p.playable)
    }

    @Test fun `direct audio with query string`() {
        val p = RemoteLinks.parse("https://cdn.example.org/path/clip.wav?token=abc")
        assertEquals(RemoteLinks.Source.DIRECT, p!!.source)
    }

    @Test fun `fileExtensionFor mappings`() {
        assertEquals(
            "m4a",
            RemoteLinks.fileExtensionFor(RemoteLinks.Source.YOUTUBE, "https://x", "audio/mp4; codecs=mp4a")
        )
        assertEquals(
            "webm",
            RemoteLinks.fileExtensionFor(RemoteLinks.Source.SOUNDCLOUD, "https://x", "audio/webm")
        )
        assertEquals(
            "mp3",
            RemoteLinks.fileExtensionFor(RemoteLinks.Source.DIRECT, "https://x.com/a.mp3", null)
        )
        assertEquals(
            "m4a",
            RemoteLinks.fileExtensionFor(RemoteLinks.Source.GOOGLE_DRIVE, "https://x", null)
        )
    }

    // ================== حالات الرفض ==================

    @Test fun `empty and non url inputs`() {
        assertNull(RemoteLinks.parse(""))
        assertNull(RemoteLinks.parse("ابحث في المكتبة"))
        assertNull(RemoteLinks.parse("not-a-link"))
    }

    @Test fun `unsupported platform returns null`() {
        assertNull(RemoteLinks.parse("https://vimeo.com/123456"))
        assertNull(RemoteLinks.parse("https://instagram.com/p/abc"))
    }

    @Test fun `host parsing is lenient`() {
        assertEquals("youtube.com", RemoteLinks.hostOf("youtube.com/watch?v=x"))
        assertEquals("soundcloud.com", RemoteLinks.hostOf("HTTPS://SoundCloud.COM/a/b"))
        assertEquals("drive.google.com", RemoteLinks.hostOf("https://drive.google.com:443/file/d/x/"))
    }

    @Test fun `source labels are arabic for youtube`() {
        assertEquals("يوتوب", RemoteLinks.Source.YOUTUBE.label)
        assertTrue(RemoteLinks.Source.YOUTUBE.needsFreshResolve)
        assertTrue(RemoteLinks.Source.SOUNDCLOUD.needsFreshResolve)
        assertFalse(RemoteLinks.Source.GOOGLE_DRIVE.needsFreshResolve)
        assertFalse(RemoteLinks.Source.DIRECT.needsFreshResolve)
    }
}

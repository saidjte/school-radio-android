package com.saidjte.schoolradio.data.remote

import com.saidjte.schoolradio.core.util.RemoteLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

/**
 * v0.25.0 — اختبارات منطق البحث في يوتوب وساوندكلاوند: تنظيف الاستعلام،
 * قابلية البحث، وفلترة نتائج الاستخراج (مقاطع فقط).
 */
class RemoteSearchLogicTest {

    // ================== تنظيف الاستعلام ==================

    @Test fun `normalize trims and collapses whitespace`() {
        assertEquals("نشيد العلم", RemoteSearchLogic.normalizeQuery("  نشيد   العلم  "))
        assertEquals("نشيد العلم", RemoteSearchLogic.normalizeQuery("نشيد\t العلم\n"))
    }

    @Test fun `searchable requires two chars`() {
        assertFalse(RemoteSearchLogic.isSearchable(""))
        assertFalse(RemoteSearchLogic.isSearchable("   "))
        assertFalse(RemoteSearchLogic.isSearchable("أ"))
        assertTrue(RemoteSearchLogic.isSearchable("نشيد"))
    }

    // ================== فلترة النتائج ==================

    private fun streamItem(
        url: String = "https://www.youtube.com/watch?v=abc",
        name: String? = "مقطع تجريبي",
        durationSec: Long = 120L,
        type: StreamType = StreamType.VIDEO_STREAM
    ): StreamInfoItem = StreamInfoItem(0, url, name, type).apply {
        setDuration(durationSec)
        setUploaderName("قناة الإذاعة")
    }

    @Test fun `toResults keeps stream items with duration in ms`() {
        val results = RemoteSearchLogic.toResults(
            listOf(streamItem(durationSec = 95L)),
            RemoteLinks.Source.YOUTUBE, 15
        )
        assertEquals(1, results.size)
        val r = results[0]
        assertEquals("مقطع تجريبي", r.title)
        assertEquals("قناة الإذاعة", r.uploader)
        assertEquals(95_000L, r.durationMs)
        assertEquals("https://www.youtube.com/watch?v=abc", r.url)
        assertEquals(RemoteLinks.Source.YOUTUBE, r.source)
    }

    @Test fun `toResults drops channels`() {
        val channel = ChannelInfoItem(0, "https://www.youtube.com/c/x", "قناة")
        val results = RemoteSearchLogic.toResults(
            listOf(channel, streamItem()),
            RemoteLinks.Source.YOUTUBE, 15
        )
        assertEquals(1, results.size)
        assertEquals("مقطع تجريبي", results[0].title)
    }

    @Test fun `toResults drops live streams`() {
        val results = RemoteSearchLogic.toResults(
            listOf(streamItem(type = StreamType.LIVE_STREAM)),
            RemoteLinks.Source.YOUTUBE, 15
        )
        assertTrue(results.isEmpty())
    }

    @Test fun `toResults drops blank urls and blank names`() {
        val results = RemoteSearchLogic.toResults(
            listOf(
                streamItem(url = ""),
                streamItem(name = null),
                streamItem(url = "ftp://bad"),
                streamItem()
            ),
            RemoteLinks.Source.YOUTUBE, 15
        )
        assertEquals(1, results.size)
    }

    @Test fun `toResults respects limit`() {
        val items = (1..30).map { streamItem(url = "https://www.youtube.com/watch?v=v$it") }
        assertEquals(15, RemoteSearchLogic.toResults(items, RemoteLinks.Source.YOUTUBE, 15).size)
        assertEquals(5, RemoteSearchLogic.toResults(items, RemoteLinks.Source.YOUTUBE, 5).size)
    }

    @Test fun `toResults zero or negative limit is empty`() {
        assertTrue(
            RemoteSearchLogic.toResults(listOf(streamItem()), RemoteLinks.Source.YOUTUBE, 0).isEmpty()
        )
    }

    @Test fun `toResults keeps soundcloud source label mapping`() {
        val results = RemoteSearchLogic.toResults(
            listOf(streamItem()),
            RemoteLinks.Source.SOUNDCLOUD, 15
        )
        assertEquals("ساوندكلاود", results[0].sourceLabel)
    }

    @Test fun `result search url feeds the standard preview path`() {
        // كل نتيجة يجب أن تكون قابلة للتحليل بنفس محلل الروابط القياسي
        val results = RemoteSearchLogic.toResults(
            listOf(streamItem(url = "https://soundcloud.com/artist/track-name")),
            RemoteLinks.Source.SOUNDCLOUD, 15
        )
        val parsed = RemoteLinks.parse(results[0].url)
        assertEquals(RemoteLinks.Source.SOUNDCLOUD, parsed?.source)
        assertTrue(parsed?.playable == true)
    }
}

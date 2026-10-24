package com.saidjte.schoolradio

import com.saidjte.schoolradio.audio.PlaybackCoordinator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات وحدة حَكَم التشغيل الموحّد (v0.8.0) — سياسة العزلة بين المصادر الصوتية
 * بمنفذ مفارق (Fake) بلا تبعيات أندرويد.
 *
 * السياسة المتعاقد عليها:
 *  - معاينة التسجيل توقف القائمة الرئيسية مؤقتاً وتُعاد تلقائياً عند انتهائها.
 *  - بدء قائمة رئيسية يوقف المعاينة نهائياً (بلا استئناف).
 *  - البث المباشر يوقف المعاينة فوراً، ويستأنف القائمة التي كانت متوقفة بسبب معاينة.
 *  - المعاينة ممنوعة أثناء البث المباشر.
 */
class PlaybackCoordinatorTest {

    /** منفذ مفارق يسجل الطلبات — للتأكد من الترتيب والدلالة */
    private class FakePort(
        var mainPlaying: Boolean = false,
        var micLive: Boolean = false
    ) : PlaybackCoordinator.PlayerPort {
        val log = mutableListOf<String>()
        var mainPaused = false

        override fun isMainPlaying(): Boolean = mainPlaying && !mainPaused
        override fun pauseMain() { mainPaused = true; log += "pauseMain" }
        override fun resumeMain() { mainPaused = false; log += "resumeMain" }
        override fun stopPreview() { log += "stopPreview" }
        override fun isMicLive(): Boolean = micLive
    }

    @Test fun `preview pauses playing main queue`() {
        val port = FakePort(mainPlaying = true)
        val c = PlaybackCoordinator(port)

        val decision = c.requestPreviewStart()

        assertEquals(PlaybackCoordinator.PreviewDecision.OK, decision)
        assertTrue(c.mainPausedByPreview)
        assertEquals(listOf("pauseMain", "stopPreview"), port.log)
    }

    @Test fun `preview with silent main queue does not touch it`() {
        val port = FakePort(mainPlaying = false)
        val c = PlaybackCoordinator(port)

        val decision = c.requestPreviewStart()

        assertEquals(PlaybackCoordinator.PreviewDecision.OK, decision)
        assertFalse(c.mainPausedByPreview)
        assertEquals(listOf("stopPreview"), port.log)
    }

    @Test fun `preview is blocked while mic is live`() {
        val port = FakePort(micLive = true, mainPlaying = true)
        val c = PlaybackCoordinator(port)

        val decision = c.requestPreviewStart()

        assertEquals(PlaybackCoordinator.PreviewDecision.BLOCKED_LIVE, decision)
        // لا يجب أن يُوقف الرئيسي — لم تبدأ المعاينة أصلاً
        assertTrue(port.log.isEmpty())
    }

    @Test fun `preview natural end resumes main queue`() {
        val port = FakePort(mainPlaying = true)
        val c = PlaybackCoordinator(port)
        c.requestPreviewStart()

        c.previewFinished()

        assertFalse(c.mainPausedByPreview)
        assertEquals(listOf("pauseMain", "stopPreview", "resumeMain"), port.log)
    }

    @Test fun `main queue start stops preview without later resume`() {
        val port = FakePort(mainPlaying = true)
        val c = PlaybackCoordinator(port)
        c.requestPreviewStart()

        c.requestMainQueueStart()

        assertFalse(c.mainPausedByPreview)
        // استئناف بعد بدء قائمة جديدة سيكون خطأً (سيستبدل قائمة المستخدم الجديدة)
        assertEquals(listOf("pauseMain", "stopPreview", "stopPreview"), port.log)
    }

    @Test fun `mic start stops preview and resumes main paused by preview`() {
        val port = FakePort(mainPlaying = true)
        val c = PlaybackCoordinator(port)
        c.requestPreviewStart()

        c.micStarted()

        assertFalse(c.mainPausedByPreview)
        // الموسيقى تُستأنف مخفَضة (ducking) خلف الإعلان — السلوك الإذاعي الطبيعي
        assertEquals(listOf("pauseMain", "stopPreview", "stopPreview", "resumeMain"), port.log)
    }

    @Test fun `mic start does not resume main the user paused manually`() {
        val port = FakePort(mainPlaying = false)
        val c = PlaybackCoordinator(port)
        c.requestPreviewStart() // الرئيسي خامل أصلاً — لا علامة معاينة

        c.micStarted()

        assertEquals(listOf("stopPreview", "stopPreview"), port.log)
    }

    @Test fun `preview end while mic went live does not resume main`() {
        val port = FakePort(mainPlaying = true)
        val c = PlaybackCoordinator(port)
        c.requestPreviewStart()

        // بدأ البث أثناء المعاينة ثم انتهت المعاينة طبيعياً
        port.micLive = true
        c.previewFinished()

        assertFalse(c.mainPausedByPreview)
        assertFalse("لا استئناف فوق البث المباشر",
            port.log.contains("resumeMain"))
    }

    @Test fun `double preview request keeps single pause flag`() {
        val port = FakePort(mainPlaying = true)
        val c = PlaybackCoordinator(port)
        c.requestPreviewStart()

        // الرئيسي متوقف الآن (isMainPlaying=false) — الطلب الثاني لا يعيد الإيقاف
        val decision = c.requestPreviewStart()

        assertEquals(PlaybackCoordinator.PreviewDecision.OK, decision)
        assertTrue(c.mainPausedByPreview)
        assertEquals(1, port.log.count { it == "pauseMain" })
    }
}

package com.saidjte.schoolradio.core.util

import com.saidjte.schoolradio.data.local.entity.RecordingSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.29.0 — اختبارات منطق ترميز التسجيلات (Opus HD / AAC).
 */
class RecordingCodecLogicTest {

    // ===== preferOpus: مصفوفة القرار الكاملة =====

    @Test
    fun `التقاطع المطلوب - أندرويد 10 مع مشفر وتفضيل مستخدم`() {
        assertTrue(
            RecordingCodecLogic.preferOpus(
                apiLevel = 29, opusEncoderAvailable = true, hdPreferred = true
            )
        )
    }

    @Test
    fun `الجهاز الأقدم من أندرويد 10 يرجع دائماً إلى AAC حتى مع التفضيل`() {
        assertFalse(
            RecordingCodecLogic.preferOpus(
                apiLevel = 28, opusEncoderAvailable = true, hdPreferred = true
            )
        )
        assertFalse(
            RecordingCodecLogic.preferOpus(
                apiLevel = 24, opusEncoderAvailable = true, hdPreferred = true
            )
        )
    }

    @Test
    fun `غياب المشفر على الجهاز يرجع إلى AAC مهما بقي`() {
        assertFalse(
            RecordingCodecLogic.preferOpus(
                apiLevel = 34, opusEncoderAvailable = false, hdPreferred = true
            )
        )
    }

    @Test
    fun `إطفاء المستخدم للمفتاح يرجع إلى AAC حتى على أحدث الأجهزة`() {
        assertFalse(
            RecordingCodecLogic.preferOpus(
                apiLevel = 35, opusEncoderAvailable = true, hdPreferred = false
            )
        )
    }

    @Test
    fun `الحد الأدنى 29 مشمول بالدعم`() {
        assertTrue(
            RecordingCodecLogic.preferOpus(
                apiLevel = RecordingCodecLogic.OPUS_MIN_API,
                opusEncoderAvailable = true,
                hdPreferred = true
            )
        )
    }

    // ===== الامتداد وMIME والتسمية =====

    @Test
    fun `الامتداد ogg للـOpus وm4a للـAAC`() {
        assertEquals("ogg", RecordingCodecLogic.extension(true))
        assertEquals("m4a", RecordingCodecLogic.extension(false))
    }

    @Test
    fun `نوع MIME للـOpus هو audio ogg وللـAAC هو audio mp4`() {
        assertEquals("audio/ogg", RecordingCodecLogic.mimeType(true))
        assertEquals("audio/mp4", RecordingCodecLogic.mimeType(false))
    }

    @Test
    fun `التسمية العربية القصيرة للشارة`() {
        assertEquals("Opus HD", RecordingCodecLogic.codecLabel(true))
        assertEquals("AAC", RecordingCodecLogic.codecLabel(false))
    }

    @Test
    fun `كشف تسجيل Opus من MIME المخزن - حسب الحالة وإهمال الحروف`() {
        assertTrue(RecordingCodecLogic.isOpusRecord("audio/ogg"))
        assertTrue(RecordingCodecLogic.isOpusRecord("AUDIO/OGG"))
        assertFalse(RecordingCodecLogic.isOpusRecord("audio/mp4"))
        assertFalse(RecordingCodecLogic.isOpusRecord(""))
        // القيمة الافتراضية القديمة (سجلات ما قبل v0.29.0) تبقى AAC
        assertFalse(RecordingCodecLogic.isOpusRecord("audio/mp4a-latm"))
    }

    // ===== تكامل أسماء الملفات مع الترميز =====

    @Test
    fun `اسم ملف التسجيل يتبع امتداد الترميز المختار`() {
        val at = 1760000000000L
        assertTrue(RecordingLogic.newFileName(RecordingSource.PTT, at).endsWith(".m4a")) // الافتراضي التاريخي
        assertTrue(
            RecordingLogic.newFileName(RecordingSource.PTT, at, RecordingCodecLogic.extension(true))
                .startsWith("broadcast-") && RecordingCodecLogic.extension(true).let { true } &&
                RecordingLogic.newFileName(RecordingSource.PTT, at, "ogg").endsWith(".ogg")
        )
        assertTrue(
            RecordingLogic.newFileName(RecordingSource.MEMO, at, "ogg").startsWith("segment-")
        )
    }

    @Test
    fun `معدل بت Opus ثابت عند 128kbps`() {
        assertEquals(128_000, RecordingCodecLogic.OPUS_BIT_RATE)
    }
}

package com.saidjte.schoolradio.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v0.39.0 — اختبارات منطق سلامة تسجيلات الأرشيف.
 *
 * التوقيعات المبنية هنا مطابقة لما تنتجه الحاويات فعلاً:
 * - Ogg Opus: «OggS» + رأس الصفحة 27 بايت ثم «OpusHead» (إجمالاً عند 28)
 * - MP4/M4A: حجم صندوق ftyp (4 بايت) ثم «ftyp» عند الإزاحة 4
 */
class RecordingIntegrityLogicTest {

    /** رأس Ogg Opus صحيح كما يكتبه OggOpusWriter: صفحة BOS تبدأ بـOggS وحزمتها OpusHead */
    private fun oggHead(): ByteArray {
        val b = ByteArray(RecordingIntegrityLogic.HEAD_BYTES)
        "OggS".toByteArray(Charsets.US_ASCII).copyInto(b, 0)
        "OpusHead".toByteArray(Charsets.US_ASCII).copyInto(b, 28)
        return b
    }

    /** رأس M4A صحيح: حجم الصندوق ثم ftyp */
    private fun mp4Head(): ByteArray {
        val b = ByteArray(RecordingIntegrityLogic.HEAD_BYTES)
        b[0] = 0; b[1] = 0; b[2] = 0; b[3] = 0x20
        "ftyp".toByteArray(Charsets.US_ASCII).copyInto(b, 4)
        return b
    }

    /** رأس ملف .ogg تالف من عصر MediaMuxer القديم — يبدأ بـOggS لكن رأسه الداخلي مكسور */
    private fun legacyBrokenOggHead(): ByteArray {
        val b = ByteArray(RecordingIntegrityLogic.HEAD_BYTES)
        "OggS".toByteArray(Charsets.US_ASCII).copyInto(b, 0)
        // الحمولة لا تحمل OpusHead عند 28 (بقايا رأس تالفة كما في لقطة الملف 99KB)
        "XXXXXXXX".toByteArray(Charsets.US_ASCII).copyInto(b, 28)
        return b
    }

    // ===== OGG =====

    @Test
    fun `تسجيل أوغس سليم - توقيع كامل وحجم كاف`() {
        assertEquals(
            RecordingIntegrityLogic.State.OK,
            RecordingIntegrityLogic.classify(
                exists = true, fileSize = 14_876_403, head = oggHead(), mimeType = "audio/ogg"
            )
        )
    }

    @Test
    fun `ملف أوغس يبدأ بـOggS لكن بلا OpusHead عند 28 = تالف من الإصدار القديم`() {
        assertEquals(
            RecordingIntegrityLogic.State.CORRUPT,
            RecordingIntegrityLogic.classify(
                exists = true, fileSize = 99_000, head = legacyBrokenOggHead(), mimeType = "audio/ogg"
            )
        )
    }

    @Test
    fun `ملف أوغس أصغر من العتبة = تالف حتى لو حمل التوقيع`() {
        assertEquals(
            RecordingIntegrityLogic.State.CORRUPT,
            RecordingIntegrityLogic.classify(
                exists = true, fileSize = 150, head = oggHead(), mimeType = "audio/ogg"
            )
        )
    }

    @Test
    fun `رأس أوغس مقطوع أقصر من 36 بايت = تالف`() {
        assertEquals(
            RecordingIntegrityLogic.State.CORRUPT,
            RecordingIntegrityLogic.classify(
                exists = true, fileSize = 5_000,
                head = ByteArray(20) { 'O'.code.toByte() }, mimeType = "audio/ogg"
            )
        )
    }

    @Test
    fun `رأس مفقود مع وجود الملف وحجمه = تالف (لا نحكم بلا قراءة على أوغس)`() {
        assertEquals(
            RecordingIntegrityLogic.State.CORRUPT,
            RecordingIntegrityLogic.classify(
                exists = true, fileSize = 8_000, head = null, mimeType = "audio/ogg"
            )
        )
    }

    // ===== M4A =====

    @Test
    fun `تسجيل m4a سليم - صندوق ftyp عند الإزاحة 4`() {
        assertEquals(
            RecordingIntegrityLogic.State.OK,
            RecordingIntegrityLogic.classify(
                exists = true, fileSize = 57_000_000, head = mp4Head(), mimeType = "audio/mp4"
            )
        )
    }

    @Test
    fun `ملف m4a بلا ftyp = تالف`() {
        val broken = ByteArray(RecordingIntegrityLogic.HEAD_BYTES)
        "XXXX".toByteArray(Charsets.US_ASCII).copyInto(broken, 4)
        assertEquals(
            RecordingIntegrityLogic.State.CORRUPT,
            RecordingIntegrityLogic.classify(
                exists = true, fileSize = 5_000, head = broken, mimeType = "audio/mp4"
            )
        )
    }

    @Test
    fun `أنواع m4a البديلة (mpeg4 وaac) تُفحص بنفس القاعدة`() {
        assertEquals(
            RecordingIntegrityLogic.State.OK,
            RecordingIntegrityLogic.classify(
                exists = true, fileSize = 5_000, head = mp4Head(), mimeType = "audio/aac"
            )
        )
        assertEquals(
            RecordingIntegrityLogic.State.OK,
            RecordingIntegrityLogic.classify(
                exists = true, fileSize = 5_000, head = mp4Head(), mimeType = "audio/mpeg4"
            )
        )
    }

    // ===== MISSING =====

    @Test
    fun `ملف غير موجود = مفقود مهما كانت بقية المعطيات`() {
        assertEquals(
            RecordingIntegrityLogic.State.MISSING,
            RecordingIntegrityLogic.classify(
                exists = false, fileSize = 0, head = null, mimeType = "audio/ogg"
            )
        )
    }

    // ===== أنواع مجهولة (سجلات أقدم من v0.29.0) =====

    @Test
    fun `نوع مجهول بوجود الملف وحجم كاف = سليم بلا حكم بنيوي`() {
        assertEquals(
            RecordingIntegrityLogic.State.OK,
            RecordingIntegrityLogic.classify(
                exists = true, fileSize = 3_000, head = null, mimeType = ""
            )
        )
        assertEquals(
            RecordingIntegrityLogic.State.OK,
            RecordingIntegrityLogic.classify(
                exists = true, fileSize = 3_000, head = ByteArray(0), mimeType = "application/octet-stream"
            )
        )
    }

    @Test
    fun `نوع مجهول بحجم صفر = تالف`() {
        assertEquals(
            RecordingIntegrityLogic.State.CORRUPT,
            RecordingIntegrityLogic.classify(
                exists = true, fileSize = 0, head = null, mimeType = ""
            )
        )
    }

    // ===== تصنيف ملفات حقيقية نموذجية (محاكاة كاملة لمسار الواجهة) =====

    @Test
    fun `مصفوفة أرشيف مختلطة - كل تسجيل يصنف حقيقة بنيته`() {
        data class Row(val exists: Boolean, val size: Long, val head: ByteArray?, val mime: String)

        val archive = listOf(
            // تسجيل جديد v0.38.0 سليم
            Row(true, 3_000_000L, oggHead(), "audio/ogg"),
            // تسجيل قديم تالف من عصر MediaMuxer (الملف 99KB المشهود)
            Row(true, 99_000L, legacyBrokenOggHead(), "audio/ogg"),
            // تسجيل AAC قديم سليم من زمن الرجوع الصامت
            Row(true, 12_000_000L, mp4Head(), "audio/mp4"),
            // سجل بقي في القاعدة وملفه حُذف
            Row(false, 0L, null, "audio/ogg")
        )
        assertEquals(
            listOf(
                RecordingIntegrityLogic.State.OK,
                RecordingIntegrityLogic.State.CORRUPT,
                RecordingIntegrityLogic.State.OK,
                RecordingIntegrityLogic.State.MISSING
            ),
            archive.map { RecordingIntegrityLogic.classify(it.exists, it.size, it.head, it.mime) }
        )
    }
}

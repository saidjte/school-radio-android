package com.saidjte.schoolradio.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.random.Random

/**
 * v0.38.0 — اختبارات كاتب حاوية Ogg Opus (الإصلاح الجذري لعطل «التسجيل لا يُشغَّل»).
 *
 * الفلسفة: الحاوية حتمية بايتاً بايتاً — لا حاجة لجهاز للتحقق منها. كل اختبار
 * يبني تدفقاً كاملاً ثم يفكّه بمُحلِّل مستقل داخل الاختبار ويعيد حساب CRC لكل
 * صفحة ويتحقق من البنية وفق RFC 7845/RFC 3533. اختباران بيئيان اختياريان
 * (متغيرات بيئة) يصدّران ملفاً للفحص الخارجي بـffprobe ويعيدان تغليف تدفق
 * ffmpeg حقيقي — دليل مطابقة خارجي إضافي عند توفرهما.
 */
class OggOpusContainerTest {

    // ===== أدوات مستقلة عن الكاتب (المُحلِّل المرجعي) =====

    private fun le32(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or
            ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 3].toInt() and 0xFF) shl 24)

    private fun le64(b: ByteArray, o: Int): Long {
        var v = 0L
        for (k in 7 downTo 0) v = (v shl 8) or (b[o + k].toLong() and 0xFF)
        return v
    }

    private class Page(
        val headerType: Int, val granule: Long, val serial: Int, val seq: Int,
        val segments: IntArray, val payload: ByteArray
    )

    /** يفك كل صفحات Ogg ويتحقق من سحر الصفحة وCRC المُعاد حسابه لكل صفحة */
    private fun parsePages(bytes: ByteArray): List<Page> {
        val pages = mutableListOf<Page>()
        var o = 0
        while (o < bytes.size) {
            assertTrue("حجم متبقٍ أقل من رأس صفحة", bytes.size - o >= 27)
            assertEquals("سحر OggS", "OggS", String(bytes, o, 4, Charsets.US_ASCII))
            assertEquals("إصدار نمط الصفحة 0", 0, bytes[o + 4].toInt())
            val storedCrc = le32(bytes, o + 22)
            val nSeg = bytes[o + 26].toInt() and 0xFF
            var payloadLen = 0
            for (i in 0 until nSeg) payloadLen += bytes[o + 27 + i].toInt() and 0xFF
            val pageLen = 27 + nSeg + payloadLen
            val copy = bytes.copyOfRange(o, o + pageLen)
            for (k in 22..25) copy[k] = 0
            assertEquals("CRC الصفحة $pages معاد حسابه", storedCrc, OggCrc.compute(copy))
            pages.add(
                Page(
                    copy[5].toInt() and 0xFF, le64(copy, 6), le32(copy, 14), le32(copy, 18),
                    copy.copyOfRange(27, 27 + nSeg).map { it.toInt() and 0xFF }.toIntArray(),
                    copy.copyOfRange(27 + nSeg, pageLen)
                )
            )
            o += pageLen
        }
        return pages
    }

    /** استخراج الحزم داخل صفحة واحدة (كاتبنا لا يقسّم حزمة عبر صفحتين أبداً) */
    private fun pagePackets(p: Page): List<ByteArray> {
        val out = mutableListOf<ByteArray>()
        val buf = ByteArrayOutputStream()
        var off = 0
        for (s in p.segments) {
            buf.write(p.payload, off, s); off += s
            if (s < 255) { out.add(buf.toByteArray()); buf.reset() }
        }
        assertEquals("حمولة الصفحة استُهلكت كاملة", p.payload.size, off)
        return out
    }

    /** استخراج حزم دفق كامل مع دمج الحزم المقسّمة عبر صفحات (لملفات ffmpeg الحقيقية) */
    private fun extractPackets(pages: List<Page>): List<ByteArray> {
        val out = mutableListOf<ByteArray>()
        val carry = ByteArrayOutputStream()
        for (p in pages) {
            var off = 0
            for (s in p.segments) {
                carry.write(p.payload, off, s); off += s
                if (s < 255) { out.add(carry.toByteArray()); carry.reset() }
            }
        }
        return out
    }

    private fun packetOfSize(n: Int, toc: Int, seed: Int): ByteArray =
        ByteArray(n).also { b ->
            b[0] = toc.toByte()
            val r = Random(seed)
            for (i in 1 until n) b[i] = r.nextInt().toByte()
        }

    private fun buildStream(
        packetCount: Int, packetSize: Int, toc: Int = 0x48, preSkip: Int = 312,
        serial: Int = 0x11223344, vendor: String = "SchoolRadio-0.38.0-test"
    ): ByteArray {
        val bos = ByteArrayOutputStream()
        val w = OggOpusWriter(bos, channels = 1, sampleRate = 48_000, preSkip = preSkip, vendor = vendor, serial = serial)
        w.writeHeaderPages()
        repeat(packetCount) { w.writePacket(packetOfSize(packetSize, toc, it + 1)) }
        val finalGranule = w.finalizeStream()
        assertEquals("granule النهائي", packetCount * opusFrameSamples(toc).toLong(), finalGranule)
        return bos.toByteArray()
    }

    // ===== OggCrc: المتجهات المعروفة + مطابقة التنفيذ البتّي المستقل =====

    /** تنفيذ بتّي مستقل (بايت-ببايت بلا جدول) — يجب أن يطابق الجدول رياضياً */
    private fun crcBitwise(data: ByteArray): Int {
        var crc = 0
        for (raw in data) {
            crc = crc xor ((raw.toInt() and 0xFF) shl 24)
            repeat(8) {
                crc = if (crc and -0x80000000 != 0) (crc shl 1) xor 0x04c11db7 else crc shl 1
            }
        }
        return crc
    }

    @Test
    fun `crc التدفق الفارغ صفر وcrc للبايت 01 يساوي كثير الحدود نفسه`() {
        assertEquals(0, OggCrc.compute(ByteArray(0)))
        assertEquals(0x04c11db7, OggCrc.compute(byteArrayOf(0x01)))
        assertEquals(0x04c11db7, crcBitwise(byteArrayOf(0x01)))
    }

    @Test
    fun `جدول crc يطابق التنفيذ البتّي المستقل على مئات المخازن العشوائية`() {
        val r = Random(42)
        repeat(300) {
            val n = r.nextInt(600)
            val data = ByteArray(n) { r.nextInt().toByte() }
            assertEquals("طول $n", crcBitwise(data), OggCrc.compute(data))
        }
    }

    // ===== جدول مدد TOC (RFC 6716 §3.1) =====

    @Test
    fun `مدد حزم Opus من بايت TOC لكل العائلات`() {
        assertEquals(480, opusFrameSamples(0x00))  // SILK NB 10ms
        assertEquals(960, opusFrameSamples(0x08))  // SILK NB 20ms
        assertEquals(1920, opusFrameSamples(0x10)) // SILK NB 40ms
        assertEquals(2880, opusFrameSamples(0x18)) // SILK NB 60ms
        assertEquals(960, opusFrameSamples(0x48))  // SILK WB 20ms
        assertEquals(480, opusFrameSamples(0x60))  // هجين 10ms
        assertEquals(960, opusFrameSamples(0x70))  // هجين 20ms
        assertEquals(120, opusFrameSamples(0x80))  // CELT 2.5ms
        assertEquals(960, opusFrameSamples(0x98))  // CELT 20ms
        assertEquals(960, opusFrameSamples(0xF8))  // CELT 20ms (config 31)
    }

    // ===== OpusHead / OpusTags =====

    @Test
    fun `رأس OpusHead بايتاً بايتاً وفق RFC 7845`() {
        val h = OggOpusWriter.opusHead(channels = 1, sampleRate = 48_000, preSkip = 312)
        assertEquals(19, h.size)
        assertEquals("OpusHead", String(h, 0, 8, Charsets.US_ASCII))
        assertEquals(1, h[8].toInt())                    // الإصدار
        assertEquals(1, h[9].toInt())                    // القنوات
        assertEquals(312, (h[10].toInt() and 0xFF) or ((h[11].toInt() and 0xFF) shl 8)) // pre-skip LE
        assertEquals(48_000, le32(h, 12))                // معدل الإدخال LE
        assertEquals(0, (h[16].toInt() and 0xFF) or ((h[17].toInt() and 0xFF) shl 8)) // كسب الإخراج 0 (بايتان)
        assertEquals(0, h[18].toInt())                   // عائلة الخريطة 0
    }

    @Test
    fun `رأس OpusTags بالبائع وعدد تعليقات صفري`() {
        val t = OggOpusWriter.opusTags("SchoolRadio")
        assertEquals(8 + 4 + 11 + 4, t.size)
        assertEquals("OpusTags", String(t, 0, 8, Charsets.US_ASCII))
        assertEquals(11, le32(t, 8))
        assertEquals("SchoolRadio", String(t, 12, 11, Charsets.UTF_8))
        assertEquals(0, le32(t, 23))
    }

    @Test
    fun `قراءة pre-skip من csd-0 الحقيقي ورفض التالف`() {
        val csd = OggOpusWriter.opusHead(1, 48_000, 312)
        assertEquals(312, OggOpusWriter.preSkipFromCsd(csd))
        assertEquals(null, OggOpusWriter.preSkipFromCsd(null))
        assertEquals(null, OggOpusWriter.preSkipFromCsd(ByteArray(5)))
        val garbage = "NotHead!!".toByteArray(Charsets.US_ASCII) + ByteArray(11)
        assertEquals(null, OggOpusWriter.preSkipFromCsd(garbage))
    }

    // ===== بنية الدفق الكامل =====

    @Test
    fun `تدفق 50 حزمة - صفحات الرأس ثم صفحة بيانات واحدة بـEOS وgranule صحيح`() {
        val bytes = buildStream(packetCount = 50, packetSize = 100)
        val pages = parsePages(bytes)
        assertEquals("صفحتا رأس + صفحة بيانات واحدة (50 قطعة ≤ 255)", 3, pages.size)

        val p0 = pages[0]
        assertEquals("BOS على الصفحة الأولى", 0x02, p0.headerType)
        assertEquals("granule رأس الصفحة 0", 0L, p0.granule)
        assertEquals("seq 0", 0, p0.seq)
        assertEquals("OpusHead وحده في الصفحة الأولى", listOf("OpusHead"), pagePackets(p0).map { String(it, 0, 8, Charsets.US_ASCII) })

        val p1 = pages[1]
        assertEquals("بلا أعلام لصفحة OpusTags", 0x00, p1.headerType)
        assertEquals(0L, p1.granule)
        assertEquals(1, p1.seq)
        assertEquals(listOf("OpusTags"), pagePackets(p1).map { String(it, 0, 8, Charsets.US_ASCII) })

        val p2 = pages[2]
        assertEquals("EOS على الصفحة الأخيرة", 0x04, p2.headerType)
        assertEquals("granule الصفحة الأخيرة = مجموع عينات الحزم", 50 * 960L, p2.granule)
        assertEquals(2, p2.seq)
        val data = pagePackets(p2)
        assertEquals(50, data.size)
        assertTrue(data.all { it.size == 100 && (it[0].toInt() and 0xFF) == 0x48 })
        // كل الصفحات بنفس السلسلة (serial) واحد
        assertTrue(pages.all { it.serial == 0x11223344 })
        // المدة الفعلية = (granule − preSkip) / 48kHz
        val durationMs = (50 * 960L - 312) * 1000L / 48_000
        assertEquals(993L, durationMs)
    }

    @Test
    fun `قواعد lacing - الحزمة المضاعفة 255 تنتهي بقيمة صفرية والحزمة 100 بقطعة واحدة`() {
        val bos = ByteArrayOutputStream()
        val w = OggOpusWriter(bos, preSkip = 312, serial = 7)
        w.writeHeaderPages()
        w.writePacket(packetOfSize(255, 0x48, 1))  // قطعتان: 255، 0
        w.writePacket(packetOfSize(510, 0x48, 2))  // ثلاث: 255، 255، 0
        w.writePacket(packetOfSize(100, 0x48, 3))  // واحدة: 100
        w.finalizeStream()
        val pages = parsePages(bos.toByteArray())
        val data = pages[2]
        assertEquals(intArrayOf(255, 0, 255, 255, 0, 100).toList(), data.segments.toList())
        assertEquals(865, data.payload.size)
        val packets = pagePackets(data)
        assertEquals(listOf(255, 510, 100), packets.map { it.size })
    }

    @Test
    fun `تقسيم الصفحات عند تجاوز 255 قطعة مع granule متزايد وEOS أخيراً`() {
        val bytes = buildStream(packetCount = 1000, packetSize = 100)
        val pages = parsePages(bytes)
        assertTrue("تعدد صفحات البيانات متوقع", pages.size > 3)
        assertEquals("BOS على الأولى", 0x02, pages.first().headerType)
        assertEquals("EOS على الأخيرة", 0x04, pages.last().headerType)
        assertTrue("لا أعلام على الوسطى", pages.drop(1).dropLast(1).all { it.headerType == 0x00 })
        assertTrue("تسلسل صفحات متصل", pages.map { it.seq } == pages.indices.toList())
        assertTrue("granule لا يتراجع", pages.zipWithNext().all { (a, b) -> a.granule <= b.granule })
        val dataPackets = pages.drop(2).flatMap { pagePackets(it) }
        assertEquals(1000, dataPackets.size)
        assertEquals("granule النهائي", 960_000L, pages.last().granule)
        assertTrue("كل صفحة ≤ 255 قطعة", pages.all { it.segments.size <= 255 })
    }

    @Test
    fun `حزم CELT القصيرة 2_5ms تعطي granule دقيقاً أيضاً`() {
        val bytes = buildStream(packetCount = 400, packetSize = 60, toc = 0x80) // 2.5ms = 120 عينة
        val pages = parsePages(bytes)
        assertEquals(400 * 120L, pages.last().granule)
    }

    @Test
    fun `pre-skip مخصص ينعكس في الرأس والمدة`() {
        val bytes = buildStream(packetCount = 50, packetSize = 100, preSkip = 652)
        val pages = parsePages(bytes)
        val head = pagePackets(pages[0]).first()
        assertEquals(652, (head[10].toInt() and 0xFF) or ((head[11].toInt() and 0xFF) shl 8))
    }

    // ===== تصدير اختياري للفحص الخارجي (ffprobe) — لا يعمل إلا بمتغير بيئة =====

    @Test
    fun `تصدير تدفق تخليقي لفحص ffprobe الخارجي عند توفر المتغير`() {
        val path = System.getenv("OGG_PROBE_PATH") ?: return
        val bos = ByteArrayOutputStream()
        val w = OggOpusWriter(bos, channels = 1, sampleRate = 48_000, preSkip = 312, vendor = "SchoolRadio-0.38.0", serial = 123456789)
        w.writeHeaderPages()
        repeat(100) { w.writePacket(packetOfSize(300, 0x48, it + 1)) } // 300B = قطعتان، 100×960 عينة
        w.finalizeStream()
        File(path).parentFile?.mkdirs()
        File(path).writeBytes(bos.toByteArray())
        // والتحقق الداخلي نفسه يعمل عليه
        val pages = parsePages(bos.toByteArray())
        assertEquals(96_000L, pages.last().granule)
    }

    /**
     * إعادة تغليف تدفق Opus حقيقي أُنتج بـffmpeg (libopus) عبر كاتبنا —
     * يثبت أن كاتبنا يغلف حزم مشفر حقيقي بغض النظر عن مصدرها.
     */
    @Test
    fun `إعادة تغليف تدفق ffmpeg الحقيقي عبر كاتبنا عند توفر المتغيرات`() {
        val realPath = System.getenv("OGG_REAL_OPUS_PATH") ?: return
        val outPath = System.getenv("OGG_PROBE_PATH") ?: return
        val pages = parsePages(File(realPath).readBytes())
        val packets = extractPackets(pages)
        assertTrue("تدفق ffmpeg يجب أن يحوي رأسين وحزم بيانات", packets.size > 4)
        assertEquals("OpusHead", String(packets[0], 0, 8, Charsets.US_ASCII))
        assertEquals("OpusTags", String(packets[1], 0, 8, Charsets.US_ASCII))
        val preSkip = OggOpusWriter.preSkipFromCsd(packets[0]) ?: 312

        val bos = ByteArrayOutputStream()
        val w = OggOpusWriter(bos, channels = 1, sampleRate = 48_000, preSkip = preSkip, vendor = "SchoolRadio-0.38.0", serial = 42)
        w.writeHeaderPages()
        var granule = 0L
        for (p in packets.drop(2)) { w.writePacket(p); granule += opusFrameSamples(p[0].toInt() and 0xFF) }
        val final = w.finalizeStream()
        assertEquals("granule إعادة التغليف = مجموع عينات الحزم الحقيقية", granule, final)
        File(outPath).parentFile?.mkdirs()
        File(outPath).writeBytes(bos.toByteArray())

        // صفحة الرأس في ملفنا تحمل OpusHead نفسه
        val ours = parsePages(bos.toByteArray())
        assertEquals("OpusHead", String(pagePackets(ours[0]).first(), 0, 8, Charsets.US_ASCII))
    }
}

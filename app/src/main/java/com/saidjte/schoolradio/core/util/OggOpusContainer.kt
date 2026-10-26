package com.saidjte.schoolradio.core.util

import java.io.OutputStream
import kotlin.random.Random

/**
 * v0.38.0 — كاتب حاوية Ogg Opus صافٍ بلا أي تبعية أندرويد (RFC 7845 + RFC 3533).
 *
 * ─── لماذا وُجد؟ السبب الجذري لعطل «تسجيل Opus لا يُشغَّل» ───
 * كانت OpusFileWriter تُفوِّض تغليف الحاوية إلى MediaMuxer(MUXER_OUTPUT_OGG) —
 * ومنفذ OGG في MediaMuxer هو الحلقة الهشّة الموثقة على أجهزة كثيرة رغم توفر
 * المشفر نفسه: يفشل أحياناً عند البدء (فيسقط التسجيل صامتاً إلى AAC) وعند
 * «نجاحه» يكتب حاويات برؤوس/granule تالفة يرفضها ExoPlayer فيتصبح التسجيل
 * عالقاً بلا تشغيل. هذا الكاتب ينتج الحاوية حتمياً بايتاً بايتاً:
 * صفحات Ogg بـCRC الصحيح، OpusHead/OpusTags وفق RFC 7845، granule محسوب
 * من TOC كل حزمة مُصدَرة، وصفحة EOS نهائية — نتيجة متطابقة على كل جهاز،
 * وقابلة للتحقق وحدوياً بايتاً بايتاً دون أي جهاز.
 *
 * الحدود المعتمدة في التصميم:
 * - حزمة Opus واحدة لا تُقسَّم عبر صفحتين أبداً (أقصى حزمة Opus 1275 بايت =
 *   6 قطع lacing، والصفحة تتسع لـ255) — فلا صفحات continuation مطلوبة.
 * - كل حزمة تزيد granule بمقدار عيناتها المفكوكة من بايت TOC (RFC 6716 §3.1)
 *   فيصبح granule النهائي هو الحقيقة الفعلية لمحتوى الملف مهما سقط من دفعات
 *   إدخال، والمدة = (granule الأخير − preSkip).
 * - صفحة OpusHead وحدها (BOS) وصفحة OpusTags وحدها — كما تشترط المواصفة.
 */
object OggCrc {

    /** جدول CRC-32 الخاص بحاوية Ogg: كثير الحدود 0x04c11db7، MSB أولاً، بلا عكس، ابتداء 0 */
    private val TABLE = IntArray(256).also { t ->
        for (i in 0 until 256) {
            var r = i shl 24
            repeat(8) {
                r = if (r and -0x80000000 != 0) (r shl 1) xor 0x04c11db7 else r shl 1
            }
            t[i] = r
        }
    }

    /** CRC لبيانات بداية من [offset] بطول [length] — يُحسب على الصفحة كاملة وحقل CRC صفّري */
    fun compute(data: ByteArray, offset: Int = 0, length: Int = data.size): Int {
        var crc = 0
        for (i in offset until offset + length) {
            crc = (crc shl 8) xor TABLE[((crc ushr 24) and 0xFF) xor (data[i].toInt() and 0xFF)]
        }
        return crc
    }
}

/**
 * مدة حزمة Opus بالعينات عند [sampleRate] من بايت TOC الأول (RFC 6716 §3.1).
 * config أعلى 5 بت: 0-11 SILK (10/20/40/60ms حسب config%4)، 12-15 هجين (10/20ms)،
 * 16-31 CELT (2.5/5/10/20ms حسب config%4).
 */
fun opusFrameSamples(tocByte: Int, sampleRate: Int = 48_000): Int {
    val config = (tocByte shr 3) and 0x1F
    return when {
        config < 12 -> intArrayOf(10, 20, 40, 60)[config % 4] * sampleRate / 1000
        config < 16 -> (if (config % 4 < 2) 10 else 20) * sampleRate / 1000
        else -> intArrayOf(5, 10, 20, 40)[config % 4] * sampleRate / 2000
    }
}

class OggOpusWriter(
    private val out: OutputStream,
    private val channels: Int = 1,
    private val sampleRate: Int = 48_000,
    private val preSkip: Int = DEFAULT_PRE_SKIP,
    private val vendor: String = DEFAULT_VENDOR,
    serial: Int = Random.nextInt()
) {
    private val serialNumber: Int = serial
    private var pageSeq = 0
    private var granule = 0L
    private var finalGranule = 0L
    private var finished = false

    /** حزم الصفحة الجارية + مجموع قطع lacing لها + granule آخر حزمة مكتمة فيها */
    private val pending = ArrayList<ByteArray>(32)
    private var pendingSegments = 0
    private var pendingGranule = 0L

    /** مجموع العينات المفكوكة حتى الآن (يشمل منطقة pre-skip) — أساس المدة النهائية */
    val samplesEncoded: Long get() = granule

    /** كتابة رأس الدفق: صفحة OpusHead (BOS) ثم صفحة OpusTags — تُنادى مرة واحدة بعد البناء */
    fun writeHeaderPages() {
        emitPage(listOf(opusHead(channels, sampleRate, preSkip)), 0L, bos = true, eos = false)
        emitPage(listOf(opusTags(vendor)), 0L, bos = false, eos = false)
    }

    /**
     * حزمة Opus مُصدَرة من المشفر (بدون csd) — تحدّث granule من TOC وتُخزَّن
     * في الصفحة الجارية؛ إن لم تتسع تُفرَّغ الصفحة أولاً بgranule آخر حزمة مكتمة.
     */
    fun writePacket(packet: ByteArray) {
        require(packet.isNotEmpty()) { "حزمة Opus فارغة" }
        check(!finished) { "الكاتب أُنهي سابقاً" }
        val segments = packet.size / 255 + 1
        if (pendingSegments + segments > MAX_SEGMENTS) flushPage(eos = false)
        pending.add(packet)
        pendingSegments += segments
        granule += opusFrameSamples(packet[0].toInt() and 0xFF, sampleRate)
        pendingGranule = granule
    }

    /** إغلاق الدفق: صفحة EOS أخيرة — يعيد granule النهائي (المدة = granule − preSkip) */
    fun finalizeStream(): Long {
        if (!finished) {
            flushPage(eos = true)
            finished = true
        }
        return finalGranule
    }

    private fun flushPage(eos: Boolean) {
        val packets = ArrayList(pending)
        pending.clear()
        pendingSegments = 0
        val g = pendingGranule
        pendingGranule = 0
        emitPage(packets, g, bos = false, eos = eos)
        finalGranule = g // granule آخر حزمة مكتمة فُرغت — المرجع للمدة النهائية
    }

    /** بناء صفحة Ogg كاملة (رأس 27 بايت + جدول القطع + الحمولة) وكتابتها بCRC صحيح */
    private fun emitPage(packets: List<ByteArray>, granulePosition: Long, bos: Boolean, eos: Boolean) {
        var segCount = 0
        var payload = 0
        for (p in packets) {
            segCount += p.size / 255 + 1
            payload += p.size
        }
        require(segCount <= MAX_SEGMENTS) { "صفحة تتجاوز 255 قطعة lacing" }
        val page = ByteArray(PAGE_HEADER + segCount + payload)
        page[0] = 'O'.code.toByte(); page[1] = 'g'.code.toByte()
        page[2] = 'g'.code.toByte(); page[3] = 'S'.code.toByte()
        page[4] = 0 // إصدار نمط الصفحة
        page[5] = ((if (bos) 0x02 else 0) or (if (eos) 0x04 else 0)).toByte()
        // granule position (LE64) — المواضع 6..13
        for (k in 0 until 8) page[6 + k] = ((granulePosition shr (8 * k)) and 0xFF).toByte()
        // serial (LE32) 14..17
        for (k in 0 until 4) page[14 + k] = ((serialNumber shr (8 * k)) and 0xFF).toByte()
        // ترتيب الصفحة (LE32) 18..21 — ثم حقل CRC 22..25 يُترك صفرياً لحساب CRC
        for (k in 0 until 4) page[18 + k] = ((pageSeq shr (8 * k)) and 0xFF).toByte()
        pageSeq += 1
        page[26] = segCount.toByte()
        var w = PAGE_HEADER
        for (p in packets) {
            var rem = p.size
            while (rem >= 255) { page[w++] = 255.toByte(); rem -= 255 }
            page[w++] = rem.toByte() // البقية — وحتى الحزمة المضاعفة 255 تنتهي بقيمة 0
        }
        for (p in packets) { p.copyInto(page, w); w += p.size }
        val crc = OggCrc.compute(page)
        page[22] = (crc and 0xFF).toByte()
        page[23] = ((crc shr 8) and 0xFF).toByte()
        page[24] = ((crc shr 16) and 0xFF).toByte()
        page[25] = ((crc shr 24) and 0xFF).toByte()
        out.write(page)
    }

    companion object {
        /** pre-skip الافتراضي لمشفر libopus (312 عينة عند 48kHz ≈ 6.5ms) */
        const val DEFAULT_PRE_SKIP = 312
        const val DEFAULT_VENDOR = "SchoolRadio"
        const val MAX_SEGMENTS = 255
        const val PAGE_HEADER = 27

        /** OpusHead (19 بايت): الإصدار 1، القنوات، pre-skip، معدل الإدخال، كسب 0، خريطة عائلة 0 */
        fun opusHead(channels: Int, sampleRate: Int, preSkip: Int): ByteArray = ByteArray(19).also { b ->
            "OpusHead".toByteArray(Charsets.US_ASCII).copyInto(b)
            b[8] = 1
            b[9] = channels.toByte()
            b[10] = (preSkip and 0xFF).toByte()
            b[11] = ((preSkip shr 8) and 0xFF).toByte()
            for (k in 0 until 4) b[12 + k] = ((sampleRate shr (8 * k)) and 0xFF).toByte()
            b[16] = 0; b[17] = 0 // output gain
            b[18] = 0 // mapping family 0 (مونو/ستيريو)
        }

        /** OpusTags: البائع + عدد التعليقات صفر (لا حاجة لأكثر في تسجيلاتنا) */
        fun opusTags(vendor: String): ByteArray {
            val v = vendor.toByteArray(Charsets.UTF_8)
            return ByteArray(8 + 4 + v.size + 4).also { b ->
                "OpusTags".toByteArray(Charsets.US_ASCII).copyInto(b)
                b[8] = (v.size and 0xFF).toByte()
                b[9] = ((v.size shr 8) and 0xFF).toByte()
                b[10] = ((v.size shr 16) and 0xFF).toByte()
                b[11] = ((v.size shr 24) and 0xFF).toByte()
                v.copyInto(b, 12)
                // عدد التعليقات = 0 (البايتات الأربعة الأخيرة صفرية أصلًا)
            }
        }

        /**
         * قراءة pre-skip من csd-0 (OpusHead يمنحه المشفر في صيغة الإخراج) —
         * يعيد null إن غاب/تلف فيعتمد الافتراضي.
         */
        fun preSkipFromCsd(csd: ByteArray?): Int? {
            if (csd == null || csd.size < 12) return null
            val magicOk = csd.size >= 8 && String(csd, 0, 8, Charsets.US_ASCII) == "OpusHead"
            if (!magicOk) return null
            return (csd[10].toInt() and 0xFF) or ((csd[11].toInt() and 0xFF) shl 8)
        }
    }
}

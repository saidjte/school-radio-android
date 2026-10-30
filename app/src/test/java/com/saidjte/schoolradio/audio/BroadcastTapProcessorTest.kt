package com.saidjte.schoolradio.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * عقد تاب المشغلات — v0.54.0.
 *
 * **اختبار الجذر**: AudioProcessingPipeline في media3 1.5.1 يُغيِّط queueInput
 * بـEMPTY_BUFFER في أول دورة تفريغ (getOutput:229 → processData(EMPTY_BUFFER)
 * → queueInput:337 — نفس أرقام أسطر بلاغ الانهيار الحقيقي) قبل وصول أي بيانات.
 * الإصدارات 0.51–0.53 كانت تستدعي replaceOutputBuffer(0) على معالج طازج، فيعيدها
 * EMPTY_BUFFER المشترك نفسه (AudioProcessor.EMPTY_BUFFER — نفس النسخة الثابتة
 * في BaseAudioProcessor وAudioProcessingPipeline) فيصبح out === inputBuffer
 * وينفجر put بـ«IllegalArgumentException: The source buffer is this buffer» —
 * ERROR_CODE_FAILED_RUNTIME_CHECK — وموت كل مشغلات media3 من أول إطار صوت.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class BroadcastTapProcessorTest {

    private fun stereoPcm(vararg frames: Pair<Int, Int>): ByteBuffer {
        val b = ByteBuffer.allocateDirect(frames.size * 4).order(ByteOrder.nativeOrder())
        frames.forEach { (l, r) -> b.putShort(l.toShort()); b.putShort(r.toShort()) }
        b.flip()
        return b
    }

    private fun configuredProcessor(tap: BroadcastTap): BroadcastTapProcessor {
        val p = BroadcastTapProcessor(tap)
        p.configure(AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT))
        return p
    }

    // ===== اختبار الجذر: أول تفريغ فارغ على pipeline حديثة =====

    @Test
    fun `أول تفريغ فارغ على معالج طازج لا ينفجر ويعيد مخرجا فارغا`() {
        val p = configuredProcessor(BroadcastTap())
        // نفس نداء media3 الحرفي: processData(EMPTY_BUFFER) → queueInput(EMPTY_BUFFER)
        p.queueInput(AudioProcessor.EMPTY_BUFFER)
        val out = p.outputBufferForTest()
        assertEquals(0, out.remaining())
    }

    @Test
    fun `replaceOutputBuffer على معالج طازج لا يعيد EMPTY_BUFFER المشترك أبدا`() {
        val p = configuredProcessor(BroadcastTap())
        // العقد الداخلي لـBaseAudioProcessor: أول replaceOutputBuffer(0) كانت تعيد
        // EMPTY_BUFFER المشترك (buffer.capacity()=0 ليس أصغر من 0) — العلة كاملة.
        // بعد الإصلاح لا يُستدعى replaceOutputBuffer إطلاقاً على مدخل فارغ،
        // وهنا نثبت أن الحالة الطازجة سليمة عبر مسار التفريغ الكامل.
        p.queueInput(AudioProcessor.EMPTY_BUFFER)
        assertFalse(p.outputBufferForTest().hasRemaining())
    }

    // ===== العقد الشفاف: المدخل الفارغ بعد بيانات حقيقية =====

    @Test
    fun `مدخل فارغ بعد بيانات حقيقية آمن بلا مخرجات زائفة`() {
        val tap = BroadcastTap()
        val p = configuredProcessor(tap)
        val input = stereoPcm(100 to 200, 300 to 400)
        val expectedBytes = input.remaining() // 4 عينات = 8 بايتات
        p.queueInput(input)
        val out = p.outputBufferForTest()
        assertEquals(expectedBytes, out.remaining()) // المدخل يُستهلك بالعقد والمخرج مطابق
        p.queueInput(AudioProcessor.EMPTY_BUFFER) // دورة تفريغ لاحقة — آمنة دائماً
        assertEquals(0, p.outputBufferForTest().remaining())
    }

    // ===== التمرير الشفاف: البايتات تصل المخرج كما هي والتاب يلتقط مونو =====

    @Test
    fun `تمرير شفاف حرفي مع تاب حي يلتقط مونو مطوي من ستيريو`() {
        val tap = BroadcastTap()
        tap.enabled = true
        val p = configuredProcessor(tap)
        val input = stereoPcm(100 to 200, 300 to 400)
        val expectedBytes = input.remaining()
        val inBytes = ByteArray(expectedBytes)
        input.get(inBytes)     // نسخة للمقارنة قبل الاستهلاك — العقد يستهلك المدخل
        input.position(0)
        p.queueInput(input)
        val out = p.outputBufferForTest()
        assertEquals(expectedBytes, out.remaining())
        val outBytes = ByteArray(out.remaining())
        out.get(outBytes)
        assertArrayEquals(inBytes, outBytes)
        // التاب: ستيريو مطوي مونو بنفس معدل المصدر (48k = 48k بلا قلّم)
        val mono = ShortArray(16)
        val n = tap.pull(mono, mono.size)
        assertEquals(2, n)
        assertEquals(150.toShort(), mono[0])
        assertEquals(350.toShort(), mono[1])
    }

    @Test
    fun `تاب معطل يمرر شفافا ولا يدفع عينة واحدة للخزان`() {
        val tap = BroadcastTap()
        tap.enabled = false
        val p = configuredProcessor(tap)
        p.queueInput(stereoPcm(100 to 200))
        assertEquals(4, p.outputBufferForTest().remaining())
        val mono = ShortArray(8)
        assertEquals(0, tap.pull(mono, mono.size))
    }

    // ===== onFlush: تصفير القلّم والخزان — عقد v0.51.0 محفوظ =====

    @Test
    fun `التفريغ يصفّر خزان التاب بين الحالات`() {
        val tap = BroadcastTap()
        tap.enabled = true
        val p = configuredProcessor(tap)
        p.queueInput(stereoPcm(100 to 200))
        p.flush()
        val mono = ShortArray(8)
        assertEquals(0, tap.pull(mono, mono.size))
    }
}

/** نافذة اختبار: مخرج المعالج الحالي كما يقرؤه AudioProcessingPipeline.getOutput */
private fun BaseAudioProcessor.outputBufferForTest(): ByteBuffer = getOutput()

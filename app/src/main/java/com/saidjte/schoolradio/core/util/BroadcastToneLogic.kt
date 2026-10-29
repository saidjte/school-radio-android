package com.saidjte.schoolradio.core.util

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * منطق نغمة إعلان بدء البث (v0.49.0) — بلا أي تبعيات أندرويد حتى يكون قابلاً للاختبار
 * وحدات (نفس فلسفة RecordingLogic).
 *
 * الزر الجديد «إعلان بدء البث» يشغّل مقطوعة افتتاحية قصيرة (أرجيجي صاعد A4 → C#5 → E5
 * ثم نغمة ختامية متلاشية) عبر مخرج الوسائط — فيسمعها الجمهور من مكبرات الاستوديو،
 * ويلتقطها تسجيل البث الكامل أيضاً لأنها صوت MEDIA عادي.
 *
 * 100% مولَّدة حسابياً: لا ملفات أصول ولا شبكة ولا محرك TTS يعتمد على جهاز معين.
 */
object BroadcastToneLogic {

    const val SAMPLE_RATE = 48_000

    /** مقام الافتتاح: لَإ صُبحة — صعود من ثلاث نغمات ثم ثبات النهائية */
    private val NOTES = doubleArrayOf(440.00, 554.37, 659.26)

    /** مدة كل نغمة من الأرجيجي */
    const val NOTE_MS = 190L

    /** النغمة الختامية المتلاشية (نفس النهائية) */
    const val TAIL_MS = 420L

    private const val ATTACK_MS = 12
    private const val RELEASE_MS = 55

    /** ذروة السعة — حاضرة واضحة دون قصف مكبرات الاستوديو */
    const val PEAK = 0.5

    /** مجموع عينات الإعلان كاملاً عند معدل معطى (للاختبارات وللعرض) */
    fun totalSamples(sampleRate: Int = SAMPLE_RATE): Int =
        ((NOTES.size * NOTE_MS + TAIL_MS) * sampleRate / 1000L).toInt()

    /**
     * توليد الإعلان كاملاً.
     * @param ascending صعوداً لبدء البث (نزولاً محجوز لمستقبل اختياري)
     */
    fun generate(ascending: Boolean, sampleRate: Int = SAMPLE_RATE): ShortArray {
        val total = totalSamples(sampleRate)
        val out = ShortArray(total)
        val noteSamples = (NOTE_MS * sampleRate / 1000L).toInt()
        val tailSamples = total - NOTES.size * noteSamples
        val order = if (ascending) NOTES.copyOf() else NOTES.reversedArray()

        var pos = 0
        for (i in order.indices) {
            render(out, pos, noteSamples, order[i], sampleRate, final = false)
            pos += noteSamples
        }
        render(out, pos, tailSamples, order.last(), sampleRate, final = true)
        return out
    }

    /** رسم نغمة واحدة بمظروف هجوم/تلاشٍ — النهائية تتحلل أُسّياً كجرس استوديو */
    private fun render(
        out: ShortArray, start: Int, len: Int,
        freq: Double, sampleRate: Int, final: Boolean
    ) {
        val attack = (ATTACK_MS * sampleRate / 1000L).toInt()
        val release = (RELEASE_MS * sampleRate / 1000L).toInt()
        val inc = 2.0 * PI * freq / sampleRate
        val amp = PEAK * if (final) 0.9 else 1.0
        var phase = 0.0
        for (i in 0 until len) {
            val env = when {
                i < attack -> i.toDouble() / attack
                i >= len - release -> (len - i).toDouble() / release
                else -> 1.0
            }
            val decay = if (final) exp(-2.2 * i / len) else 1.0
            out[start + i] = (sin(phase) * env * decay * amp * Short.MAX_VALUE).toInt().toShort()
            phase += inc
        }
    }
}

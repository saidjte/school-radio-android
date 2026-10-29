package com.saidjte.schoolradio.audio

import android.content.Context
import android.media.AudioFormat
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.saidjte.schoolradio.core.util.BroadcastTapLogic
import com.saidjte.schoolradio.core.util.CrashGuard
import com.saidjte.schoolradio.core.util.LinearResampler
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * تاب مشغلات media3 في تسجيل البث الكامل (v0.51.0) — معالج صوتي شفاف يُغرَس
 * في أول سلسلة AudioSink فينسخ PCM المفكوك ثُمَ المُرسل إلى مكبرات الجهاز.
 *
 * **لماذا هنا وليس في النظام؟** توثيق أندرويد يخصص AudioPlaybackCapture
 * لصوت التطبيقات *الأخرى* حصراً — صوت التطبيق الملتقط نفسه لا يعود منه أبداً
 * (هذا جذر بلاغ «التسجيل يسجل فقط المايكروفون»). المعالج في داخل سلسلة
 * المشغل هو آخر نقطة يملكها التطبيق: ما يمر بها هو حرفياً ما يذهب إلى
 * مكبرات الاستوديو — لا تقدير ولا نسخة منحرفة.
 *
 * **مستوى المصدر**: media3 يطبق player.volume على AudioTrack *بعد* سلسلة
 * المعالجات، فيصل التاب بمستوى خام — يُضرب في [BroadcastTap.gain] الذي
 * يحدّثه مستمع onVolumeChanged من المشغل نفسه (فيدَر الميكسر + الخفض
 * التلقائي + مستوى البدء: مصدر حقيقة واحد لا نسخة منحرفة).
 *
 * **التمرير شفاف دائماً**: ما لا يُفهم ترميزه يمر كما هو بلا تاب — سلوك
 * المُبوِّق الافتراضي لا يُمس إطلاقاً. وترتيب السلسلة المحفوظ (SilenceSkipping
 * ثم Sonic بعد هذا المعالج — تُلحق آلياً) يبقي السرعة وقصّ الصمت كما كانت.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class BroadcastTapProcessor(private val tap: BroadcastTap) : BaseAudioProcessor() {

    // حالة التحويل — يملكها خيط تشغيل المشغل وحده فلا سباق
    // التنسيق يُلتقط عند onConfigure في حقل خاص — حقل inputAudioFormat في
    // BaseAudioProcessor يبقى NOT_SET حتى أول flush فلا يُعتمد عليه في queueInput
    private var fmt: AudioProcessor.AudioFormat = AudioProcessor.AudioFormat.NOT_SET
    private var resampler: LinearResampler? = null
    private var resamplerRate = 0
    private var allChannels = ShortArray(0)   // عينات كل القنوات قبل الطي
    private var mono = ShortArray(0)          // مونو بمعدل المصدر
    private var resampled = ShortArray(0)     // مونو 48kHz

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // تمرير شفاف لأي تنسيق — التاب يعمل فقط على PCM-16/float المفهومين.
        // إعادة نفس التنسيق = سلوك المُبوِّق الافتراضي حرفياً بلا أي مساس.
        fmt = inputAudioFormat
        resampler = null
        resamplerRate = 0
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (tap.enabled && remaining > 0) {
            try {
                tapChunk(inputBuffer.duplicate())
            } catch (t: Throwable) {
                // التاب خادم الأرشيف لا سيّد التشغيل — أي مفاجأة تُوثّق ولا تلمس
                // الصوت المسموع إطلاقًا (قاعدة v0.15.0: لا خيط صوتي يموت باستثناء)
                CrashGuard.record("BroadcastTapProcessor", t)
            }
        }
        // تمرير شفاف: نفس البايتات إلى المخرج كما هي
        val out = replaceOutputBuffer(remaining)
        out.put(inputBuffer)
        out.flip()
    }

    override fun onFlush() {
        // بحث/انتقال = حالة قلّم قديمة + محتوى ما قبل الانتقال مكرر زائف — تصفير نظيف
        resampler = null
        resamplerRate = 0
        tap.clear()
    }

    /** تحويل دفعة المشغل الخام إلى مونو 48kHz بمستوى المصدر ثم دفعها للتاب */
    private fun tapChunk(raw: ByteBuffer) {
        val f = fmt
        val channels = f.channelCount.coerceAtLeast(1)
        val sampleRate = f.sampleRate
        if (sampleRate <= 0) return

        val monoFrames: Int = when (f.encoding) {
            C.ENCODING_PCM_16BIT -> {
                raw.order(ByteOrder.nativeOrder())
                val sb = raw.asShortBuffer()
                val total = sb.remaining()
                if (total <= 0) return
                if (allChannels.size < total) allChannels = ShortArray(total)
                sb.get(allChannels, 0, total)
                if (mono.size < total) mono = ShortArray(total)
                BroadcastTapLogic.foldChannelsInto(allChannels, channels, mono, total / channels)
                total / channels
            }
            C.ENCODING_PCM_FLOAT -> {
                raw.order(ByteOrder.nativeOrder())
                val fb = raw.asFloatBuffer()
                val total = fb.remaining()
                if (total <= 0) return
                if (allChannels.size < total) allChannels = ShortArray(total)
                for (i in 0 until total) allChannels[i] = BroadcastTapLogic.floatToShort(fb.get(i))
                if (mono.size < total) mono = ShortArray(total)
                BroadcastTapLogic.foldChannelsInto(allChannels, channels, mono, total / channels)
                total / channels
            }
            else -> return // ترميز غير PCM — تمرير شفاف بلا تاب (نادر: مسارات مرورية خاصة)
        }
        if (monoFrames <= 0) return

        // إعادة القلّم إلى 48kHz — نفس المعدل = مرور مباشر بلا نسخة
        val out: ShortArray
        val outFrames: Int
        if (sampleRate == BroadcastTap.SAMPLE_RATE) {
            out = mono
            outFrames = monoFrames
        } else {
            if (resampler == null || resamplerRate != sampleRate) {
                resampler = LinearResampler(sampleRate, BroadcastTap.SAMPLE_RATE)
                resamplerRate = sampleRate
            }
            val need = BroadcastTapLogic.estimateResampled(sampleRate, BroadcastTap.SAMPLE_RATE, monoFrames)
            if (resampled.size < need) resampled = ShortArray(need)
            outFrames = resampler!!.resample(mono, monoFrames, resampled)
            out = resampled
        }
        if (outFrames <= 0) return

        // مستوى المصدر الفعلي (فيدَر المشغل بعد الخفض) — من نقطة واحدة يحدّثها المشغل
        val gain = tap.gain
        if (gain != 1f) {
            val g = gain.coerceIn(0f, 4f)
            for (i in 0 until outFrames) {
                val v = out[i] * g
                out[i] = when {
                    v > Short.MAX_VALUE -> Short.MAX_VALUE
                    v < Short.MIN_VALUE -> Short.MIN_VALUE
                    else -> v.toInt().toShort()
                }
            }
        }
        tap.push(out, outFrames)
    }
}

/**
 * مصنع مُبوِّقات يغرس تاب التسجيل الكامل في مشغل media3 — نفس تكوين
 * DefaultRenderersFactory الافتراضي حرفياً مع معالج واحد إضافي أول السلسلة
 * (SilenceSkipping وSonic يُلحَقان آلياً بعده فلا تُمس السرعة ولا قصّ الصمت).
 */
@androidx.annotation.OptIn(UnstableApi::class)
fun renderersFactoryWithTap(context: Context, tap: BroadcastTap): DefaultRenderersFactory =
    object : DefaultRenderersFactory(context) {
        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioTrackPlaybackParams: Boolean
        ): AudioSink? =
            DefaultAudioSink.Builder(context)
                .setAudioProcessors(arrayOf<AudioProcessor>(BroadcastTapProcessor(tap)))
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .build()
    }

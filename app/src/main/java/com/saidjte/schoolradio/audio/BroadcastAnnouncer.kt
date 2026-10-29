package com.saidjte.schoolradio.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.saidjte.schoolradio.core.util.BroadcastToneLogic
import com.saidjte.schoolradio.core.util.CrashGuard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * مُعلِن البث (v0.49.0) — يشغّل نغمة «إعلان بدء البث» عبر مخرج الوسائط.
 *
 * قرارا معماريان:
 *  1. USAGE_MEDIA عمداً — الصوت يتبع مسار الوسائط فيسمعه الجمهور من مكبرات
 *     الاستوديو. v0.51.0: النغمة تُبثّ من PCM مولّد حسابياً فيُدفَع نفسه
 *     إلى تاب النغمة في [BroadcastMixBus] فتبدأ أرشيف البث بالإعلان نفسه
 *     (v0.49.0 كان يتركها للتقاط النظام — والمنصة لا تعيد صوت التطبيق نفسه).
 *  2. ضغطة أثناء الإعلان تُتجاهل (لا تراكم نغمات فوق بعضها) — الاستوديو
 *     يحتاج قراراً صوتياً واحداً صافياً لا طبقات.
 *  v0.50.0: رد نهاية النغمة — الإعلان الكامل (شارة «مباشر» + نشيد افتتاح)
 *  ينتظر انقضاء النغمة فعلاً لا تقدير زمني، والرد يُستدعى مرة واحدة من
 *  خيط النغمة وللمستدعي إدارة خيوطه (قاعدة الرئيسي للمشغل — v0.47.0).
 */
class BroadcastAnnouncer(private val toneTap: BroadcastTap? = null) {

    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()

    private val busy = AtomicBoolean(false)

    /** نغمة بدء البث الصاعدة — يُستدعى من الواجهة (الخيط الرئيسي) */
    fun announceStart(afterTone: (() -> Unit)? = null) {
        play(BroadcastToneLogic.generate(ascending = true), afterTone)
    }

    private fun play(pcm: ShortArray, afterTone: (() -> Unit)?) {
        if (!busy.compareAndSet(false, true)) return
        _playing.value = true

        // خيط عابر قصير العمر (~ثانية واحدة) — تعقيد جهاز صوتي كامل لنبضة واحدة لا يبرر
        Thread({
            var track: AudioTrack? = null
            try {
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(BroadcastToneLogic.SAMPLE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(
                        maxOf(
                            AudioTrack.getMinBufferSize(
                                BroadcastToneLogic.SAMPLE_RATE,
                                AudioFormat.CHANNEL_OUT_MONO,
                                AudioFormat.ENCODING_PCM_16BIT
                            ) * 2,
                            pcm.size * 2
                        )
                    )
                    .build()
                track.play()
                // كتابة على دفعات 10ms — نفس نَفَس خطوط الصوت في التطبيق كله
                val chunk = ShortArray(MicrophoneController.SAMPLE_RATE / 100)
                var pos = 0
                while (pos < pcm.size) {
                    val n = minOf(chunk.size, pcm.size - pos)
                    System.arraycopy(pcm, pos, chunk, 0, n)
                    track.write(chunk, 0, n)
                    // v0.51.0: نفس الدفعة إلى تاب النغمة — الأرشيف يبدأ بالإعلان نفسه
                    if (toneTap?.enabled == true) toneTap.push(chunk, n)
                    pos += n
                }
                // انتظار آخر دفعة تخرج فعلاً إلى مكبر الاستوديو — الرأس يبلغ طول المقطع
                var waited = 0
                while (track.playbackHeadPosition < pcm.size && waited < 2_500) {
                    Thread.sleep(20); waited += 20
                }
            } catch (t: Throwable) {
                CrashGuard.record("BroadcastAnnouncer", t)
            } finally {
                runCatching { track?.stop() }
                runCatching { track?.release() }
                _playing.value = false
                busy.set(false)
                // v0.50.0: النغمة انقضت فعلاً (رأس التشغيل بلغ النهاية) — نقطة
                // انطلاق ما بعد الإعلان (نشيد الافتتاح). استدعاء واحد مهما حدث.
                afterTone?.invoke()
            }
        }, "broadcast-announcer").apply { priority = Thread.NORM_PRIORITY + 1; start() }
    }
}

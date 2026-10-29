package com.saidjte.schoolradio.audio

import com.saidjte.schoolradio.core.util.MicFrameRing

/**
 * تاب مصدر صوتي واحد في تسجيل البث الكامل (v0.51.0) — خزان حلقي + معامل كسب.
 *
 * كل مصدر في التطبيق (مشغل المكتبة، حائط الجينغل، نغمة الإعلان، خط المايك)
 * يملك تاباً واحداً يدفع إليه PCM الذي يبعثه فعلاً إلى النظام. الخزان حلقي
 * بسقف 400ms: أي اختناق لحظي في خيط الخلط يمتصه، والفيض يُبرح الأقدم.
 *
 * [gain] هو المستوى الذي يخرج به المصدر فعلاً إلى مكبرات الاستوديو —
 * يحدّثه مستمع onVolumeChanged من المشغل نفسه (مصدر الحقيقة الوحيد:
 * فيدَر الميكسر والخفض التلقائي ومستوى البدء كلها تمر من هناك).
 */
class BroadcastTap(ringMs: Int = RING_MS) {

    companion object {
        /** سقف الخزان بالمللي ثانية — يمتص انفجارات فك الترميز (دفعات كبيرة) */
        const val RING_MS = 400
        const val SAMPLE_RATE = MicrophoneController.SAMPLE_RATE
    }

    val ring = MicFrameRing(SAMPLE_RATE / 1000 * ringMs)

    /** مستوى المصدر الفعلي على الهواء — يحدّثه المشغل صاحب التاب */
    @Volatile var gain: Float = 1f

    /** التاب حي فقط أثناء تسجيل البث الكامل — يعطله الطريق السريع في خيوط الصوت */
    @Volatile var enabled: Boolean = false

    fun push(pcm: ShortArray, n: Int) = ring.push(pcm, n)

    fun pull(dest: ShortArray, max: Int): Int = ring.pull(dest, max)

    fun clear() = ring.clear()
}

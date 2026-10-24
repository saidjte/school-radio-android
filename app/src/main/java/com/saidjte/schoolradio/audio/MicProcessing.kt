package com.saidjte.schoolradio.audio

import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor

/**
 * تكوين معالجة صوت المذيع (v0.11.0) — سلسلة التنظيف القياسية في الإذاعات:
 *
 *  - [agc] توحيد الكسب تلقائياً (AGC): يرفع صوت المذيع الخافت ويخفض الصوت
 *    العالي فتبقى قناة الميك متوازنة مهما تغير بُعد المذيع عن الهاتف.
 *  - [noiseGate] حجز الضجيج (NoiseSuppressor): يكتم هسهسة المروحة وصوت
 *    الطلاب الخلفي أثناء الإعلان.
 *  - [echoCancel] إلغاء الصدى (AEC): يمنع صفير التغذية الراجعة عندما تكون
 *    مكبرات القاعة قريبة من الهاتف.
 *
 * مؤثرات المنصة مدمجة في أندرويد (android.media.audiofx) وتُربط بجلسة
 * AudioRecord عند فتح البث — أجهزة قليلة جداً لا تدعمها فيُتخطى الناقص بهدوء.
 */
data class MicProcessing(
    val agc: Boolean = true,
    val noiseGate: Boolean = true,
    val echoCancel: Boolean = true
)

/**
 * حامل سلسلة معالجة الميكروفون لعُقدة بث واحدة.
 * يُنشأ داخل MicrophoneController ويُربط بمعرّف جلسة AudioRecord عند البدء
 * ويُحرر عند الإيقاف — دورة حياة مطابقة تماماً لعُقدة البث نفسها.
 */
class MicProcessingChain {

    private var agc: AutomaticGainControl? = null
    private var ns: NoiseSuppressor? = null
    private var aec: AcousticEchoCanceler? = null

    /** ما طُبّق فعلاً — يُعرض في رسالة البدء عند اقتران التشخيص */
    fun attach(audioSessionId: Int, cfg: MicProcessing): List<String> {
        val applied = mutableListOf<String>()
        if (cfg.agc && AutomaticGainControl.isAvailable()) {
            runCatching {
                agc = AutomaticGainControl.create(audioSessionId)?.apply { enabled = true }
            }
            if (agc != null) applied += "AGC"
        }
        if (cfg.noiseGate && NoiseSuppressor.isAvailable()) {
            runCatching {
                ns = NoiseSuppressor.create(audioSessionId)?.apply { enabled = true }
            }
            if (ns != null) applied += "NS"
        }
        if (cfg.echoCancel && AcousticEchoCanceler.isAvailable()) {
            runCatching {
                aec = AcousticEchoCanceler.create(audioSessionId)?.apply { enabled = true }
            }
            if (aec != null) applied += "AEC"
        }
        return applied
    }

    fun release() {
        runCatching { agc?.release() }
        runCatching { ns?.release() }
        runCatching { aec?.release() }
        agc = null
        ns = null
        aec = null
    }
}

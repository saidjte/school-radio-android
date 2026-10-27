package com.saidjte.schoolradio.audio

import android.media.AudioDeviceInfo
import android.media.AudioManager

/**
 * v0.44.0 — اختيار مدخل الالتقاط الصوتي.
 *
 * الجذر: أندرويد يوجّه مصادر الالتقاط افتراضياً إلى الميكروفون المدمج وحده،
 * فمايك USB-C الخارجي (مِكرُفون مكثّف أو سماعة بمايك) يبقى صامتاً في التطبيق
 * كله رغم كونه مدخلاً مسجّلاً عند النظام (البلاغ: «التطبيق لا يتعرف على
 * المايكروفون الخارجي الدي يتصل عبر USB type C»).
 *
 * الحل بنقطة واحدة: عند وجود مدخل USB نلتقط منه صراحة عبر
 * [AudioRecord.setPreferredDevice] — والمصدر MIC الخام عند وجوده (أصدق
 * التزاماً بالاختيار الصريح عبر التطبيقات، إذ قد تُثبَّت مسارات
 * VOICE_COMMUNICATION المعالجة على المدمج ماركوياً في بعض الشرائح).
 * بلا USB: السلوك القائم لا يُمس إطلاقاً.
 */
object MicDeviceLogic {

    /**
     * أولوية مداخل USB المقبولة:
     *  - TYPE_USB_DEVICE: مايك USB مستقل (الميكروفون المكثّف/الاستوديو)
     *  - TYPE_USB_HEADSET: سماعة USB بمايك مدمج
     *  - TYPE_USB_ACCESSORY: ملحقات USB القديمة
     */
    val USB_INPUT_PRIORITY = intArrayOf(
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_ACCESSORY
    )

    /**
     * منطق صافٍ: أول نوع أولوية موجود في المداخل الملموسة —
     * يُختبر بلا أندرويد (قوائم أعداد صحيحة).
     */
    fun pickInput(priority: IntArray, presentTypes: List<Int>): Int? =
        priority.firstOrNull { presentTypes.contains(it) }
}

/** استعلام النظام: جهاز الإدخال USB المفضّل أو null — يخدم البث والتسجيل بنفس العقد */
fun usbInputDevice(am: AudioManager): AudioDeviceInfo? {
    val inputs = am.getDevices(AudioManager.GET_DEVICES_INPUTS)
    val wanted = MicDeviceLogic.pickInput(MicDeviceLogic.USB_INPUT_PRIORITY, inputs.map { it.type })
        ?: return null
    return inputs.firstOrNull { it.type == wanted }
}

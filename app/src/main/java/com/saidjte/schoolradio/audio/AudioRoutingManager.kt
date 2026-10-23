package com.saidjte.schoolradio.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.os.Build

/**
 * مدير التوجيه الصوتي — يحدد أين يخرج صوت *البث المباشر* (الميكروفون).
 *
 * حقيقة أندرويد المهمة: لا توجد واجهة موحدة لتحويل الميكروفون إلى مكبر على كل الأجهزة.
 * الاستراتيجية المجرّبة:
 *  - مسار «الوسائط» (USAGE_MEDIA + MODE_NORMAL): يعمل مع السماعة المدمجة وAUX والبلوتوث A2DP
 *    (جودة عالية، لكن لا يعمل جيداً مع مصدر VOICE_COMMUNICATION على بعض الأجهزة).
 *  - مسار «الاتصال» (MODE_IN_COMMUNICATION + USAGE_VOICE_COMMUNICATION): يسمح بفرض مكبر
 *    الجهاز عبر setSpeakerphoneOn/setCommunicationDevice ويعمل مع معالجة الصدى (AEC).
 */
class AudioRoutingManager(context: Context) {

    enum class Route(val labelAr: String) {
        SPEAKER("مكبر الجهاز"),
        WIRED("سماعة/AUX"),
        BLUETOOTH("بلوتوث"),
        MEDIA_DEFAULT("المسار الافتراضي للوسائط")
    }

    private val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** الأجهزة النشطة المتاحة حالياً — للعرض والاختيار في الواجهة */
    fun availableOutputs(): List<AudioDeviceInfo> =
        am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter { d ->
            d.type in setOf(
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_AUX_LINE,
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
            )
        }

    fun hasWiredDevice(): Boolean = availableOutputs().any {
        it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            it.type == AudioDeviceInfo.TYPE_AUX_LINE
    }

    fun hasBluetoothDevice(): Boolean = availableOutputs().any {
        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
    }

    /** اختيار أفضل مسار تلقائي حسب ما هو موصول فعلياً */
    fun suggestRoute(): Route = when {
        hasWiredDevice() -> Route.WIRED
        hasBluetoothDevice() -> Route.BLUETOOTH
        else -> Route.SPEAKER
    }

    /**
     * تطبيق المسار قبل بدء البث — يعيد نوع الاستعمال الصحيح لمسار AudioTrack.
     * @return usage المطلوب لمسار AudioTrack (USAGE_MEDIA أو USAGE_VOICE_COMMUNICATION)
     */
    fun applyRoute(route: Route): Int {
        return when (route) {
            Route.MEDIA_DEFAULT, Route.WIRED, Route.BLUETOOTH -> {
                // الوسائط تتبع المسار المتاح تلقائياً (AUX = سماكة سلكية، بلوتوث = A2DP بجودة عالية)
                am.mode = AudioManager.MODE_NORMAL
                setSpeaker(false)
                android.media.AudioAttributes.USAGE_MEDIA
            }
            Route.SPEAKER -> {
                // وضع الاتصال: يتيح فرض مكبر الجهاز + يعمل مع AEC ضد الصفير
                am.mode = AudioManager.MODE_IN_COMMUNICATION
                setSpeaker(true)
                android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION
            }
        }
    }

    /** إعادة الوضع الطبيعي بعد انتهاء البث حتى لا تتعطل نغمات النظام */
    fun restoreNormal() {
        am.mode = AudioManager.MODE_NORMAL
        setSpeaker(false)
    }

    private fun setSpeaker(on: Boolean) {
        if (Build.VERSION.SDK_INT >= 31) {
            // الواجهة الحديثة (بديل setSpeakerphoneOn المهجور): جهاز اتصال صريح
            val target = am.availableCommunicationDevices.firstOrNull {
                if (on) it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                else it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
            }
            target?.let { am.setCommunicationDevice(it) }
        } else {
            @Suppress("DEPRECATION")
            am.setSpeakerphoneOn(on)
        }
    }

    /** مراقبة انقطاع جهاز الصوت أثناء البث (فك السماعة مثلاً) لإيقاف البث بأمان */
    fun registerDeviceLossListener(onLoss: () -> Unit): AudioDeviceCallback {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesRemoved(added: Array<out AudioDeviceInfo>?) {
                // إن اختفى كل جهاز الإخراج الخارجي أوقف البث (يكفي أن ينبه المستخدم)
                if (!hasWiredDevice() && !hasBluetoothDevice()) onLoss()
            }
        }
        am.registerAudioDeviceCallback(callback, null)
        return callback
    }

    /** تحرير مستمع انقطاع الأجهزة */
    fun unregister(callback: AudioDeviceCallback) {
        am.unregisterAudioDeviceCallback(callback)
    }

    /** تحقق من أن الميكروفون متاح قبل البدء (لا يوجد مسجل آخر يحتجزه) */
    fun isMicrophoneFree(sampleRate: Int, encoding: Int, channel: Int): Boolean {
        val min = AudioRecord.getMinBufferSize(sampleRate, channel, encoding)
        if (min <= 0) return false
        return try {
            val probe = AudioRecord(
                android.media.MediaRecorder.AudioSource.MIC, sampleRate, channel, encoding, min
            )
            val state = probe.state
            probe.release()
            state == AudioRecord.STATE_INITIALIZED
        } catch (_: Exception) {
            false
        }
    }

    /** الحد الأدنى الفعلي لحجم مخزن AudioTrack (لتقدير التأخير في الواجهة) */
    fun minTrackBuffer(sampleRate: Int, encoding: Int, channelOut: Int): Int =
        AudioTrack.getMinBufferSize(sampleRate, channelOut, encoding)
}

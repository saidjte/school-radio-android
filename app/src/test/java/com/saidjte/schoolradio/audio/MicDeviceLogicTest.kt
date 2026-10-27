package com.saidjte.schoolradio.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * v0.44.0 — اختبارات اختيار مدخل الالتقاط (المايك الخارجي عبر USB).
 * الجذر: أندرويد يبقى على المدمج وحده افتراضياً فلا صوت من الخارجي —
 * الاختيار الصريح يجب أن يفضّل USB إن وُجد ويتساقط بأمان بلا وجوده.
 */
class MicDeviceLogicTest {

    private fun types(vararg t: Int) = t.toList()

    @Test fun `prefers usb device mic when present`() {
        val present = types(
            android.media.AudioDeviceInfo.TYPE_BUILTIN_MIC,
            android.media.AudioDeviceInfo.TYPE_USB_DEVICE
        )
        assertEquals(
            android.media.AudioDeviceInfo.TYPE_USB_DEVICE,
            MicDeviceLogic.pickInput(MicDeviceLogic.USB_INPUT_PRIORITY, present)
        )
    }

    @Test fun `prefers usb headset mic`() {
        val present = types(
            android.media.AudioDeviceInfo.TYPE_BUILTIN_MIC,
            android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET,
            android.media.AudioDeviceInfo.TYPE_USB_HEADSET
        )
        assertEquals(
            android.media.AudioDeviceInfo.TYPE_USB_HEADSET,
            MicDeviceLogic.pickInput(MicDeviceLogic.USB_INPUT_PRIORITY, present)
        )
    }

    @Test fun `falls back to usb accessory when devices absent`() {
        val present = types(
            android.media.AudioDeviceInfo.TYPE_BUILTIN_MIC,
            android.media.AudioDeviceInfo.TYPE_USB_ACCESSORY
        )
        assertEquals(
            android.media.AudioDeviceInfo.TYPE_USB_ACCESSORY,
            MicDeviceLogic.pickInput(MicDeviceLogic.USB_INPUT_PRIORITY, present)
        )
    }

    @Test fun `no usb input means null and builtin behavior stays untouched`() {
        val present = types(
            android.media.AudioDeviceInfo.TYPE_BUILTIN_MIC,
            android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET,
            android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        )
        assertNull(MicDeviceLogic.pickInput(MicDeviceLogic.USB_INPUT_PRIORITY, present))
        assertNull(MicDeviceLogic.pickInput(MicDeviceLogic.USB_INPUT_PRIORITY, emptyList()))
    }

    @Test fun `priority order is respected`() {
        // كل أنواع USB موجودة: الأولوية المعلنة تحسم (DEVICE قبل HEADSET قبل ACCESSORY)
        val present = types(
            android.media.AudioDeviceInfo.TYPE_USB_ACCESSORY,
            android.media.AudioDeviceInfo.TYPE_USB_HEADSET,
            android.media.AudioDeviceInfo.TYPE_USB_DEVICE
        )
        assertEquals(
            android.media.AudioDeviceInfo.TYPE_USB_DEVICE,
            MicDeviceLogic.pickInput(MicDeviceLogic.USB_INPUT_PRIORITY, present)
        )
        val headsetAndAccessory = types(
            android.media.AudioDeviceInfo.TYPE_USB_ACCESSORY,
            android.media.AudioDeviceInfo.TYPE_USB_HEADSET
        )
        assertEquals(
            android.media.AudioDeviceInfo.TYPE_USB_HEADSET,
            MicDeviceLogic.pickInput(MicDeviceLogic.USB_INPUT_PRIORITY, headsetAndAccessory)
        )
    }
}

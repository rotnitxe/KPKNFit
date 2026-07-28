package com.example.kpkn.services.workout

import android.media.AudioDeviceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutVoiceMicRouterTest {

    @Test
    fun preferenceScore_ordersAccessoryMicsAheadOfBluetoothSco() {
        val wired = WorkoutVoiceMicRouter.preferenceScore(AudioDeviceInfo.TYPE_WIRED_HEADSET)
        val usb = WorkoutVoiceMicRouter.preferenceScore(AudioDeviceInfo.TYPE_USB_HEADSET)
        val ble = WorkoutVoiceMicRouter.preferenceScore(AudioDeviceInfo.TYPE_BLE_HEADSET)
        val sco = WorkoutVoiceMicRouter.preferenceScore(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)

        assertTrue(wired != null && usb != null && ble != null && sco != null)
        assertTrue(wired!! < usb!!)
        assertTrue(usb < ble!!)
        assertTrue(ble < sco!!)
    }

    @Test
    fun preferenceScore_ignoresNonInputMediaRoutes() {
        assertNull(WorkoutVoiceMicRouter.preferenceScore(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER))
        assertNull(WorkoutVoiceMicRouter.preferenceScore(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE))
        assertNull(WorkoutVoiceMicRouter.preferenceScore(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP))
    }

    @Test
    fun continuousPreference_builtinMicIsDeferredNotChosenAsAccessory() {
        // En continuo el mic del teléfono es fallback implícito (null device), no accessory.
        assertEquals(90, WorkoutVoiceMicRouter.continuousPreferenceScore(AudioDeviceInfo.TYPE_BUILTIN_MIC))
        assertNull(WorkoutVoiceMicRouter.continuousPreferenceScore(AudioDeviceInfo.TYPE_BLUETOOTH_SCO))
    }

    @Test
    fun preferenceScore_wiredBeatsBluetoothSco() {
        val wired = WorkoutVoiceMicRouter.preferenceScore(AudioDeviceInfo.TYPE_WIRED_HEADSET)!!
        val sco = WorkoutVoiceMicRouter.preferenceScore(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)!!
        assertTrue(wired < sco)
        assertEquals(0, wired)
        assertEquals(6, sco)
    }
}

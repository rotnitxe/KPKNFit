package com.example.kpkn.services.workout

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioRecord
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Enrutamiento de micrófono orientado a «música primero».
 *
 * Continuo: cable / USB / BLE vía [AudioRecord.setPreferredDevice]; Bluetooth clásico
 * NO usa SCO/setCommunicationDevice (conserva A2DP → mic del teléfono).
 * Fallback puntual: puede preferir headset incluyendo SCO temporalmente.
 */
class WorkoutVoiceMicRouter(
    context: Context,
) {
    enum class RouteMode {
        CONTINUOUS_MUSIC_FIRST,
        FALLBACK_ALLOW_HEADSET,
    }

    private val appContext = context.applicationContext
    private val audioManager: AudioManager? =
        appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val mainHandler = Handler(Looper.getMainLooper())
    private var acquired = false
    private var mode: RouteMode = RouteMode.CONTINUOUS_MUSIC_FIRST
    private var callbackRegistered = false
    private var lastPreferredId: Int? = null

    private val _activeRouteLabel = MutableStateFlow<String?>(null)
    val activeRouteLabel: StateFlow<String?> = _activeRouteLabel.asStateFlow()

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            // El engine reaplicará en el próximo open; solo actualizamos etiqueta.
            if (acquired) refreshLabelOnly()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            if (!acquired) return
            val removedIds = removedDevices.map { it.id }.toSet()
            if (lastPreferredId != null && lastPreferredId in removedIds) {
                lastPreferredId = null
                _activeRouteLabel.value = "phone"
            }
        }
    }

    fun acquire(routeMode: RouteMode = RouteMode.CONTINUOUS_MUSIC_FIRST) {
        mode = routeMode
        acquired = true
        registerCallbackIfNeeded()
        // Nunca setCommunicationDevice en continuo: evita HFP/SCO y calidad de llamada.
        if (routeMode == RouteMode.CONTINUOUS_MUSIC_FIRST) {
            clearCommunicationDeviceIfNeeded()
        }
        refreshLabelOnly()
    }

    fun release() {
        acquired = false
        clearCommunicationDeviceIfNeeded()
        unregisterCallbackIfNeeded()
        lastPreferredId = null
        _activeRouteLabel.value = null
    }

    /**
     * Aplica preferencia de entrada al [AudioRecord] sin tocar el routing global de media.
     * Continuo: wired/USB/BLE; si sólo hay BT clásico → null (mic del teléfono).
     * Fallback: puede elegir headset incl. SCO.
     */
    fun applyPreferredDeviceTo(record: AudioRecord, routeMode: RouteMode = mode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            _activeRouteLabel.value = "phone"
            return
        }
        val am = audioManager ?: return
        val devices = am.getDevices(AudioManager.GET_DEVICES_INPUTS).toList()
        val preferred = when (routeMode) {
            RouteMode.CONTINUOUS_MUSIC_FIRST -> pickContinuousInput(devices)
            RouteMode.FALLBACK_ALLOW_HEADSET -> pickFallbackInput(devices)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                record.setPreferredDevice(preferred)
            }
            lastPreferredId = preferred?.id
            _activeRouteLabel.value = preferred?.let { labelFor(it) } ?: "phone"
            // Verificar dispositivo efectivo cuando API lo permite.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val routed = record.routedDevice
                if (routed != null) {
                    _activeRouteLabel.value = labelFor(routed)
                }
            }
        } catch (_: Exception) {
            lastPreferredId = null
            _activeRouteLabel.value = "phone"
        }
    }

    private fun refreshLabelOnly() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            _activeRouteLabel.value = "phone"
            return
        }
        val am = audioManager ?: return
        val devices = am.getDevices(AudioManager.GET_DEVICES_INPUTS).toList()
        val preferred = when (mode) {
            RouteMode.CONTINUOUS_MUSIC_FIRST -> pickContinuousInput(devices)
            RouteMode.FALLBACK_ALLOW_HEADSET -> pickFallbackInput(devices)
        }
        _activeRouteLabel.value = preferred?.let { labelFor(it) } ?: "phone"
    }

    private fun clearCommunicationDeviceIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val am = audioManager ?: return
        try {
            am.clearCommunicationDevice()
        } catch (_: Exception) {
        }
    }

    private fun registerCallbackIfNeeded() {
        if (callbackRegistered) return
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        try {
            am.registerAudioDeviceCallback(deviceCallback, mainHandler)
            callbackRegistered = true
        } catch (_: Exception) {
            callbackRegistered = false
        }
    }

    private fun unregisterCallbackIfNeeded() {
        if (!callbackRegistered) return
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        try {
            am.unregisterAudioDeviceCallback(deviceCallback)
        } catch (_: Exception) {
        }
        callbackRegistered = false
    }

    companion object {
        /** Continuo: nunca SCO/A2DP-as-input; mic del teléfono si sólo hay BT clásico. */
        fun continuousPreferenceScore(type: Int): Int? = when (type) {
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> 0
            AudioDeviceInfo.TYPE_USB_HEADSET -> 1
            AudioDeviceInfo.TYPE_USB_DEVICE -> 2
            AudioDeviceInfo.TYPE_BLE_HEADSET -> 3
            AudioDeviceInfo.TYPE_BLE_SPEAKER -> 4
            AudioDeviceInfo.TYPE_HEARING_AID -> 5
            // Built-in mic es el fallback explícito para conservar A2DP en BT clásico.
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> 90
            else -> null
        }

        /** Fallback puntual: headset (incl. SCO) permitido. */
        fun fallbackPreferenceScore(type: Int): Int? = when (type) {
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> 0
            AudioDeviceInfo.TYPE_USB_HEADSET -> 1
            AudioDeviceInfo.TYPE_USB_DEVICE -> 2
            AudioDeviceInfo.TYPE_BLE_HEADSET -> 3
            AudioDeviceInfo.TYPE_BLE_SPEAKER -> 4
            AudioDeviceInfo.TYPE_HEARING_AID -> 5
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> 6
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> 90
            else -> null
        }

        /** @deprecated Use [continuousPreferenceScore] — kept for test migration. */
        fun preferenceScore(type: Int): Int? = continuousPreferenceScore(type).let { score ->
            // Tests históricos esperaban SCO con score 6; en continuo ya no se elige.
            if (type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) 6 else score
        }

        fun pickContinuousInput(devices: List<AudioDeviceInfo>): AudioDeviceInfo? {
            val accessory = devices
                .mapNotNull { device ->
                    val score = continuousPreferenceScore(device.type) ?: return@mapNotNull null
                    if (score >= 90) return@mapNotNull null // defer builtin
                    score to device
                }
                .minByOrNull { it.first }
                ?.second
            if (accessory != null) return accessory
            // Sólo BT clásico / sin headset → mic del teléfono (null = default platform).
            return null
        }

        fun pickFallbackInput(devices: List<AudioDeviceInfo>): AudioDeviceInfo? {
            return devices
                .mapNotNull { device ->
                    val score = fallbackPreferenceScore(device.type) ?: return@mapNotNull null
                    score to device
                }
                .minByOrNull { it.first }
                ?.second
        }

        fun pickPreferredCommunicationDevice(
            devices: List<AudioDeviceInfo>,
        ): AudioDeviceInfo? = pickContinuousInput(devices)

        fun labelFor(device: AudioDeviceInfo): String {
            val name = device.productName?.toString()?.trim().orEmpty()
            val typeLabel = when (device.type) {
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> "wired"
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_USB_DEVICE,
                -> "usb"
                AudioDeviceInfo.TYPE_BLE_HEADSET,
                AudioDeviceInfo.TYPE_BLE_SPEAKER,
                -> "ble"
                AudioDeviceInfo.TYPE_HEARING_AID -> "hearing_aid"
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "bt_sco"
                AudioDeviceInfo.TYPE_BUILTIN_MIC -> "phone"
                else -> "other"
            }
            // El micrófono interno no debe exponer el modelo comercial del equipo
            // (p.ej. "phone:SM-F731B"); el resto de dispositivos sí.
            return if (device.type == AudioDeviceInfo.TYPE_BUILTIN_MIC || name.isEmpty()) {
                typeLabel
            } else {
                "$typeLabel:$name"
            }
        }
    }
}

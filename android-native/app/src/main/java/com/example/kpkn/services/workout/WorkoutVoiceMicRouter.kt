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
 * Selects the input route used by the workout voice session.
 *
 * Voice capture is intentionally headset-first. Classic Bluetooth exposes its
 * microphone through the communication/SCO route, so selecting only an
 * [AudioRecord] input while preserving A2DP leaves capture on the phone mic.
 * On API 31+ the communication device selects the matching source automatically;
 * the record-level preference is still applied when Android exposes the source
 * explicitly.
 */
class WorkoutVoiceMicRouter(
    context: Context,
) {
    enum class RouteMode {
        CONTINUOUS_VOICE_FIRST,
        /**
         * Kept for binary/source compatibility with the previous internal
         * caller. It now follows the same voice-first policy.
         */
        @Deprecated("Continuous voice capture is headset-first.")
        CONTINUOUS_MUSIC_FIRST,
        FALLBACK_ALLOW_HEADSET,
    }

    private val appContext = context.applicationContext
    private val audioManager: AudioManager? =
        appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val mainHandler = Handler(Looper.getMainLooper())
    private var acquired = false
    private var mode: RouteMode = RouteMode.CONTINUOUS_VOICE_FIRST
    private var callbackRegistered = false
    private var lastPreferredId: Int? = null
    private var communicationDeviceId: Int? = null
    private var communicationDeviceRequested = false
    private var legacyScoRequested = false

    private val _activeRouteLabel = MutableStateFlow<String?>(null)
    val activeRouteLabel: StateFlow<String?> = _activeRouteLabel.asStateFlow()

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            if (!acquired) return
            // A headset can become available after the session starts. Reapply
            // the communication request so the next record, and supported
            // active records, do not remain on the phone mic.
            requestCommunicationRoute()
            refreshLabelOnly()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            if (!acquired) return
            val removedIds = removedDevices.map { it.id }.toSet()
            if (communicationDeviceId in removedIds) {
                clearCommunicationDeviceIfNeeded()
            }
            if (lastPreferredId in removedIds) {
                lastPreferredId = null
            }
            requestCommunicationRoute()
            refreshLabelOnly()
        }
    }

    fun acquire(routeMode: RouteMode = RouteMode.CONTINUOUS_VOICE_FIRST) {
        if (acquired && mode != routeMode) {
            clearCommunicationDeviceIfNeeded()
        }
        mode = routeMode
        acquired = true
        registerCallbackIfNeeded()
        requestCommunicationRoute()
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
     * Applies the best available input to [record].
     *
     * Passing null intentionally restores platform-default input routing. If
     * [setCommunicationDevice] succeeded, that default includes its matching
     * Bluetooth source even when the source is not listed yet.
     */
    @Suppress("DEPRECATION")
    fun applyPreferredDeviceTo(
        record: AudioRecord,
        routeMode: RouteMode = mode,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            _activeRouteLabel.value = "phone"
            return
        }
        val am = audioManager ?: return
        val devices = inputDevices(am)
        val preferred = when (routeMode) {
            RouteMode.CONTINUOUS_VOICE_FIRST,
            RouteMode.CONTINUOUS_MUSIC_FIRST,
            -> pickVoiceInput(devices)
            RouteMode.FALLBACK_ALLOW_HEADSET -> pickFallbackInput(devices)
        }
        val applied = runCatching { record.setPreferredDevice(preferred) }.getOrDefault(false)
        if (preferred != null && !applied) {
            // Do not leave a stale preferred device on a reused AudioRecord.
            runCatching { record.setPreferredDevice(null) }
        }
        lastPreferredId = preferred?.id?.takeIf { applied }
        _activeRouteLabel.value = preferred?.let(::labelFor)
            ?: communicationLabelOrPhone()
    }

    /**
     * [AudioRecord.getRoutedDevice] is only valid after recording starts.
     * Calling this from the engine closes the gap between the requested route
     * and the route Android actually selected.
     */
    fun observeStartedRecord(record: AudioRecord) {
        val routed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { record.routedDevice }.getOrNull()
        } else {
            null
        }
        if (routed != null) {
            lastPreferredId = routed.id
            _activeRouteLabel.value = labelFor(routed)
            WorkoutVoiceDiagnosticLogger.event(
                "audio_route_observed",
                mapOf(
                    "route" to labelFor(routed),
                    "requestedRoute" to communicationLabelOrPhone(),
                    "recordDeviceId" to routed.id,
                ) + WorkoutVoiceDiagnosticLogger.runtimeStateFields(appContext),
            )
        } else {
            refreshLabelOnly()
        }
    }

    @Suppress("DEPRECATION")
    private fun refreshLabelOnly() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            _activeRouteLabel.value = "phone"
            return
        }
        val am = audioManager ?: return
        val preferred = when (mode) {
            RouteMode.CONTINUOUS_VOICE_FIRST,
            RouteMode.CONTINUOUS_MUSIC_FIRST,
            -> pickVoiceInput(inputDevices(am))
            RouteMode.FALLBACK_ALLOW_HEADSET -> pickFallbackInput(inputDevices(am))
        }
        _activeRouteLabel.value = preferred?.let(::labelFor) ?: communicationLabelOrPhone()
    }

    private fun communicationLabelOrPhone(): String {
        val am = audioManager ?: return "phone"
        val selected = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            communicationDeviceRequested
        ) {
            runCatching { am.communicationDevice }.getOrNull()
        } else {
            null
        }
        return selected?.let(::labelFor) ?: "phone"
    }

    private fun requestCommunicationRoute() {
        val am = audioManager ?: return
        val available = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                am.availableCommunicationDevices
            } else {
                @Suppress("DEPRECATION")
                am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
            }
        } catch (error: Exception) {
            // A transient device-manager failure must not discard a route that
            // was already selected successfully.
            logRouteRequest(
                requested = null,
                accepted = false,
                reason = "available_devices_failed",
                exception = error,
            )
            return
        }
        val preferred = pickPreferredCommunicationDevice(available)
        if (preferred == null) {
            clearCommunicationDeviceIfNeeded()
            logRouteRequest(
                requested = null,
                accepted = true,
                reason = "no_external_communication_device",
            )
            return
        }

        if (communicationDeviceRequested && communicationDeviceId != preferred.id) {
            clearCommunicationDeviceIfNeeded()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            communicationDeviceRequested && communicationDeviceId == preferred.id
        ) {
            val selectedId = runCatching { am.communicationDevice?.id }.getOrNull()
            if (selectedId == preferred.id) {
                logRouteRequest(
                    requested = preferred,
                    accepted = true,
                    reason = "communication_device_already_selected",
                )
                return
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val accepted = runCatching { am.setCommunicationDevice(preferred) }
                .onFailure {
                    logRouteRequest(
                        requested = preferred,
                        accepted = false,
                        reason = "set_communication_device_failed",
                        exception = it,
                    )
                }
                .getOrDefault(false)
            if (!accepted) {
                clearCommunicationDeviceIfNeeded()
            }
            communicationDeviceRequested = accepted
            communicationDeviceId = preferred.id.takeIf { accepted }
            if (!accepted) {
                _activeRouteLabel.value = "phone"
            }
            logRouteRequest(
                requested = preferred,
                accepted = accepted,
                reason = if (accepted) "communication_device_selected" else "communication_device_rejected",
            )
            return
        }

        if (preferred.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
            @Suppress("DEPRECATION")
            val accepted = runCatching {
                am.startBluetoothSco()
                am.isBluetoothScoOn = true
                true
            }.onFailure {
                logRouteRequest(
                    requested = preferred,
                    accepted = false,
                    reason = "legacy_sco_failed",
                    exception = it,
                )
            }.getOrDefault(false)
            legacyScoRequested = accepted
            communicationDeviceRequested = accepted
            communicationDeviceId = preferred.id.takeIf { accepted }
            logRouteRequest(
                requested = preferred,
                accepted = accepted,
                reason = if (accepted) "legacy_sco_requested" else "legacy_sco_rejected",
            )
        } else {
            if (legacyScoRequested) {
                clearCommunicationDeviceIfNeeded()
            }
            logRouteRequest(
                requested = preferred,
                accepted = true,
                reason = "record_input_route_only",
            )
        }
    }

    private fun clearCommunicationDeviceIfNeeded() {
        audioManager?.let { am ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && communicationDeviceRequested) {
                runCatching { am.clearCommunicationDevice() }
            }
            if (legacyScoRequested) {
                @Suppress("DEPRECATION")
                runCatching {
                    am.isBluetoothScoOn = false
                    am.stopBluetoothSco()
                }
            }
        }
        communicationDeviceRequested = false
        communicationDeviceId = null
        legacyScoRequested = false
    }

    private fun logRouteRequest(
        requested: AudioDeviceInfo?,
        accepted: Boolean,
        reason: String,
        exception: Throwable? = null,
    ) {
        WorkoutVoiceDiagnosticLogger.event(
            "audio_route_request",
            buildMap {
                put("mode", mode.name)
                put("requested", requested?.let(::labelFor))
                put("requestedDeviceId", requested?.id)
                put("accepted", accepted)
                put("reason", reason)
                exception?.let {
                    put("exceptionType", it.javaClass.name)
                    put("exceptionMessage", it.message)
                }
            } + WorkoutVoiceDiagnosticLogger.runtimeStateFields(appContext),
        )
    }

    private fun inputDevices(am: AudioManager): List<AudioDeviceInfo> =
        runCatching { am.getDevices(AudioManager.GET_DEVICES_INPUTS).toList() }
            .getOrElse {
                WorkoutVoiceDiagnosticLogger.exception("audio_input_devices_failed", it)
                emptyList()
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
        /** Voice capture: prefer an external microphone, including classic BT SCO. */
        fun voicePreferenceScore(type: Int): Int? = when (type) {
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

        /** Kept as a semantic alias for callers that classify continuous input. */
        fun continuousPreferenceScore(type: Int): Int? = voicePreferenceScore(type)

        /** Fallback uses the same headset-first policy as normal voice capture. */
        fun fallbackPreferenceScore(type: Int): Int? = voicePreferenceScore(type)

        /** Compatibility alias retained for older diagnostics/tests. */
        fun preferenceScore(type: Int): Int? = voicePreferenceScore(type)

        fun pickVoiceInput(devices: List<AudioDeviceInfo>): AudioDeviceInfo? =
            pickBest(devices, ::voicePreferenceScore, includeBuiltin = false)

        /** Compatibility alias; continuous capture is now voice-first. */
        fun pickContinuousInput(devices: List<AudioDeviceInfo>): AudioDeviceInfo? =
            pickVoiceInput(devices)

        fun pickFallbackInput(devices: List<AudioDeviceInfo>): AudioDeviceInfo? =
            pickBest(devices, ::fallbackPreferenceScore, includeBuiltin = true)

        /**
         * API 31+ expects an output/sink from getAvailableCommunicationDevices.
         * Android then selects the matching Bluetooth input automatically.
         */
        fun pickPreferredCommunicationDevice(
            devices: List<AudioDeviceInfo>,
        ): AudioDeviceInfo? = pickBest(
            devices = devices,
            score = ::communicationPreferenceScore,
            includeBuiltin = false,
        )

        private fun communicationPreferenceScore(type: Int): Int? =
            voicePreferenceScore(type)?.takeIf { it < 90 }

        private fun pickBest(
            devices: List<AudioDeviceInfo>,
            score: (Int) -> Int?,
            includeBuiltin: Boolean,
        ): AudioDeviceInfo? {
            return devices
                .mapNotNull { device ->
                    val deviceScore = score(device.type) ?: return@mapNotNull null
                    if (!includeBuiltin && device.type == AudioDeviceInfo.TYPE_BUILTIN_MIC) {
                        return@mapNotNull null
                    }
                    deviceScore to device
                }
                .minWithOrNull(
                    compareBy<Pair<Int, AudioDeviceInfo>> { it.first }
                        .thenBy { it.second.id }
                        .thenBy { safeProductName(it.second) },
                )
                ?.second
        }

        fun labelFor(device: AudioDeviceInfo): String {
            val name = safeProductName(device)
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
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "speaker"
                AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "earpiece"
                else -> "other"
            }
            // Never expose the phone model as the internal microphone label.
            return if (
                device.type == AudioDeviceInfo.TYPE_BUILTIN_MIC || name.isEmpty()
            ) {
                typeLabel
            } else {
                "$typeLabel:$name"
            }
        }

        private fun safeProductName(device: AudioDeviceInfo): String =
            runCatching { device.productName?.toString()?.trim().orEmpty() }
                .getOrDefault("")
    }
}

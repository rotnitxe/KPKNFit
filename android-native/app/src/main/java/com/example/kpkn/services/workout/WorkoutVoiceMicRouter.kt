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

    /** Descriptor puro para decisiones de ruteo testeables en JVM. */
    data class AudioRoutePeer(val type: Int, val address: String?)

    /** Resultado observado tras iniciar la captura. */
    data class ObservedRoute(
        val label: String,
        val deviceId: Int,
        val deviceType: Int,
    )

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
    private var previousAudioMode: Int? = null
    private var communicationModeApplied = false

    /**
     * En modo MÚSICA no se solicita ruta de comunicación: el SCO queda libre y
     * la música intacta (la captura usa el mic interno con VOICE_RECOGNITION).
     */
    var externalRouteEnabled: Boolean = true

    private val _activeRouteLabel = MutableStateFlow<String?>(null)
    val activeRouteLabel: StateFlow<String?> = _activeRouteLabel.asStateFlow()

    private val _routeRevoked = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val routeRevoked: kotlinx.coroutines.flow.SharedFlow<Unit> = _routeRevoked

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
            val wasActive = communicationDeviceId in removedIds || lastPreferredId in removedIds
            WorkoutVoiceDiagnosticLogger.event(
                "audio_route_revoked",
                mapOf(
                    "removedIds" to removedDevices.map { it.id },
                    "wasActive" to wasActive,
                ),
            )
            if (communicationDeviceId in removedIds) {
                clearCommunicationDeviceIfNeeded()
            }
            if (lastPreferredId in removedIds) {
                lastPreferredId = null
            }
            requestCommunicationRoute()
            refreshLabelOnly()
            if (wasActive) _routeRevoked.tryEmit(Unit)
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
        restoreAudioModeIfNeeded()
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
        if (!externalRouteEnabled) {
            runCatching { record.setPreferredDevice(null) }
            lastPreferredId = null
            _activeRouteLabel.value = "phone"
            return
        }
        val devices = inputDevices(am)
        // routeMode se conserva por compatibilidad de firma; todos los modos
        // comparten el ranking headset-first iterado abajo.
        val ranked = rankedVoiceInputs(devices)
        var appliedDevice: AudioDeviceInfo? = null
        for (candidate in ranked) {
            val applied = runCatching { record.setPreferredDevice(candidate) }.getOrDefault(false)
            if (applied) {
                appliedDevice = candidate
                break
            }
        }
        if (appliedDevice == null) {
            // Do not leave a stale preferred device on a reused AudioRecord.
            runCatching { record.setPreferredDevice(null) }
        }
        lastPreferredId = appliedDevice?.id
        _activeRouteLabel.value = appliedDevice?.let(::labelFor) ?: communicationLabelOrPhone()
    }

    /**
     * [AudioRecord.getRoutedDevice] is only valid after recording starts.
     * Calling this from the engine closes the gap between the requested route
     * and the route Android actually selected.
     */
    fun observeStartedRecord(record: AudioRecord): ObservedRoute? {
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
            return ObservedRoute(label = labelFor(routed), deviceId = routed.id, deviceType = routed.type)
        }
        refreshLabelOnly()
        return null
    }

    fun hasExternalRouteRequested(): Boolean = externalRouteEnabled && communicationDeviceRequested

    /**
     * Espera a que el enlace de comunicación quede efectivamente activo.
     * setCommunicationDevice()/startBluetoothSco() son asíncronos a nivel AudioPolicy:
     * abrir el AudioRecord antes de tiempo lo deja sobre el mic interno.
     */
    suspend fun awaitCommunicationRoute(timeoutMs: Long = 1_500L): Boolean {
        val am = audioManager ?: return false
        if (!communicationDeviceRequested) return false
        val deadline = android.os.SystemClock.elapsedRealtime() + timeoutMs
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            val ready = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val selected = runCatching { am.communicationDevice }.getOrNull()
                selected != null && selected.id == communicationDeviceId
            } else {
                @Suppress("DEPRECATION")
                runCatching { am.isBluetoothScoOn }.getOrDefault(false)
            }
            if (ready) return true
            kotlinx.coroutines.delay(50L)
        }
        return false
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
        if (!externalRouteEnabled) {
            logRouteRequest(
                requested = null,
                accepted = true,
                reason = "music_mode_suppressed",
            )
            _activeRouteLabel.value = "phone"
            return
        }
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
        val inputs = inputDevices(am).map { device ->
            AudioRoutePeer(device.type, device.addressOrNull())
        }
        val unfiltered = pickPreferredCommunicationDevice(available)
        val preferred = if (unfiltered != null &&
            !sinkHasInputSource(AudioRoutePeer(unfiltered.type, unfiltered.addressOrNull()), inputs)
        ) {
            // Varios OEM listan sinks A2DP-only como "de comunicación"; sin fuente
            // de entrada emparejable no sirven para captura. Descartarlos del ranking.
            val filtered = available.filter { device ->
                sinkHasInputSource(AudioRoutePeer(device.type, device.addressOrNull()), inputs)
            }
            val picked = pickPreferredCommunicationDevice(filtered)
            if (picked?.id != unfiltered.id) {
                logRouteRequest(
                    requested = picked,
                    accepted = true,
                    reason = "sink_without_input_skipped",
                    skipped = unfiltered,
                )
            }
            picked
        } else {
            unfiltered
        }
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
            if (accepted) {
                applyCommunicationAudioMode()
            }
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
            if (accepted) {
                applyCommunicationAudioMode()
            }
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

    private fun applyCommunicationAudioMode() {
        val am = audioManager ?: return
        if (communicationModeApplied) return
        runCatching {
            val before = am.mode
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            val after = am.mode
            communicationModeApplied = true
            if (after != before) {
                WorkoutVoiceDiagnosticLogger.event(
                    "audio_mode_changed",
                    mapOf("previous" to before, "current" to after, "reason" to "external_voice_route"),
                )
            }
        }
    }

    private fun restoreAudioModeIfNeeded() {
        val am = audioManager ?: return
        if (!communicationModeApplied) return
        runCatching {
            val previous = am.mode
            if (previous == AudioManager.MODE_IN_COMMUNICATION) {
                am.mode = previousAudioMode ?: AudioManager.MODE_NORMAL
            }
            val after = am.mode
            if (after != previous) {
                WorkoutVoiceDiagnosticLogger.event(
                    "audio_mode_changed",
                    mapOf("previous" to previous, "current" to after, "reason" to "route_released"),
                )
            }
        }
        previousAudioMode = null
        communicationModeApplied = false
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
        restoreAudioModeIfNeeded()
    }

    private fun logRouteRequest(
        requested: AudioDeviceInfo?,
        accepted: Boolean,
        reason: String,
        exception: Throwable? = null,
        skipped: AudioDeviceInfo? = null,
    ) {
        WorkoutVoiceDiagnosticLogger.event(
            "audio_route_request",
            buildMap {
                put("mode", mode.name)
                put("requested", requested?.let(::labelFor))
                put("requestedDeviceId", requested?.id)
                put("accepted", accepted)
                put("reason", reason)
                skipped?.let {
                    put("skipped", labelFor(it))
                    put("skippedId", it.id)
                }
                exception?.let {
                    put("exceptionType", it.javaClass.name)
                    put("exceptionMessage", it.message)
                }
            } + WorkoutVoiceDiagnosticLogger.runtimeStateFields(appContext),
        )
    }

    private fun AudioDeviceInfo.addressOrNull(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { address }.getOrNull()
        } else {
            null
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
        /** Un sink de comunicación solo sirve si existe una fuente de entrada emparejable. */
        fun sinkHasInputSource(sink: AudioRoutePeer, inputs: List<AudioRoutePeer>): Boolean {
            val byAddress = sink.address?.takeIf { it.isNotBlank() }?.let { sinkAddress ->
                inputs.any { it.address == sinkAddress }
            } == true
            if (byAddress) return true
            // Fallback por familia cuando el OEM no expone address.
            return when (sink.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO ->
                    inputs.any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
                AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER ->
                    inputs.any {
                        it.type == AudioDeviceInfo.TYPE_BLE_HEADSET || it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
                    }
                AudioDeviceInfo.TYPE_WIRED_HEADSET ->
                    inputs.any { it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET }
                AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE ->
                    inputs.any {
                        it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_DEVICE
                    }
                else -> false
            }
        }

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

        /** Ranking completo de entradas de voz (sin mic interno) en orden de preferencia. */
        fun rankedVoiceInputs(devices: List<AudioDeviceInfo>): List<AudioDeviceInfo> =
            devices
                .mapNotNull { device ->
                    val score = voicePreferenceScore(device.type) ?: return@mapNotNull null
                    if (device.type == AudioDeviceInfo.TYPE_BUILTIN_MIC) return@mapNotNull null
                    score to device
                }
                .sortedWith(compareBy<Pair<Int, AudioDeviceInfo>> { it.first }.thenBy { it.second.id })
                .map { it.second }

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

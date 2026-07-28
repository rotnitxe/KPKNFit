package com.example.kpkn.services.workout

/**
 * Reglas deterministas del micrófono continuo vs fallback / ocupación.
 */
object WorkoutVoiceCaptureGate {
    /** Lecturas negativas consecutivas antes de declarar el AudioRecord muerto. */
    const val MAX_CONSECUTIVE_READ_ERRORS = 8

    /** Si seguimos en MIC_BUSY (silenciado) demasiado tiempo, forzar reopen. */
    const val MIC_BUSY_RECOVERY_TIMEOUT_MS = 5_000L

    /**
     * Reabrir AudioRecord continuo.
     * [micBusy] **no** bloquea: si la sesión murió hay que poder abrir una nueva.
     * Solo el fallback nativo debe impedir la reapertura.
     */
    fun mayReopenContinuousMic(
        sessionActive: Boolean,
        fallbackInFlight: Boolean,
        usingNativeFallback: Boolean,
    ): Boolean = sessionActive &&
        !fallbackInFlight &&
        !usingNativeFallback

    fun shouldMarkMicBusy(
        sessionActive: Boolean,
        ourSessionId: Int?,
        configsContainOurSession: Boolean,
        clientSilenced: Boolean?,
    ): Boolean {
        if (!sessionActive || ourSessionId == null) return false
        if (clientSilenced == true) return true
        return !configsContainOurSession
    }

    /**
     * Sesión ausente = captura muerta o robada → liberar y reabrir (no esperar la misma sesión).
     * Silenciada con sesión presente → esperar unsilenecio o timeout.
     */
    fun busyRecoveryKind(
        configsContainOurSession: Boolean,
        clientSilenced: Boolean?,
    ): MicBusyRecoveryKind = when {
        !configsContainOurSession -> MicBusyRecoveryKind.RELEASE_AND_REOPEN
        clientSilenced == true -> MicBusyRecoveryKind.WAIT_OR_TIMEOUT
        else -> MicBusyRecoveryKind.NONE
    }

    fun shouldClearMicBusy(
        sessionActive: Boolean,
        currentlyBusy: Boolean,
        configsContainOurSession: Boolean,
        clientSilenced: Boolean?,
    ): Boolean {
        if (!sessionActive || !currentlyBusy) return false
        if (clientSilenced == true) return false
        return configsContainOurSession && clientSilenced != true
    }

    fun shouldAbandonDeadAudioRecord(consecutiveReadErrors: Int): Boolean =
        consecutiveReadErrors >= MAX_CONSECUTIVE_READ_ERRORS

    /**
     * Android puede entregar PCM positivo compuesto por silencio sintético.
     * Solo una configuración confirmada como no silenciada permite publicar LISTENING.
     */
    fun mayPublishListening(
        recordingConfigSilenced: Boolean?,
        hasPositivePcm: Boolean,
    ): Boolean = recordingConfigSilenced == false && hasPositivePcm
}

enum class MicBusyRecoveryKind {
    NONE,
    /** La sesión ya no existe: no puede “reaparecer”; hay que crear AudioRecord nuevo. */
    RELEASE_AND_REOPEN,
    /** Sigue silenciada: esperar callback o timeout. */
    WAIT_OR_TIMEOUT,
}

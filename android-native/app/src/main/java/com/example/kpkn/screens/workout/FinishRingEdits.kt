package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.PostSessionPreview

/**
 * Lo que el cierre del entreno cambia en los anillos, con los argumentos que `AugeViewModel.applyManualBatteries`
 * espera. Un canal nulo es «no tocado»; las predicciones son las del resumen del cierre (sin resumen, nulas: sin
 * predicción AUGE no aprende de la sensación).
 */
internal data class FinishRingEdits(
    val neural: Int?,
    val spinal: Int?,
    val muscleDeltas: Map<String, Int>,
    val sessionCnsDrain: Double,
    val sessionSpinalDrain: Double,
    val sessionMuscleDrain: Double,
    val predictedNeural: Int?,
    val predictedSpinal: Int?,
    val predictedMuscles: Map<String, Int>,
)

/**
 * Qué anillos cambió la persona al cerrar, o `null` si ninguno. La comparten la hoja del cierre y el cierre por voz.
 *
 * - Hoja: trae los dos valores finales y cuentan los que difieren del resumen ([preview]), por valor.
 * - Voz: solo dicta lo que dijo la persona, así que un final nulo es «no tocado» (nunca «distinto del resumen») y lo
 *   dictado es el ajuste. Si el resumen no estaba disponible (la hoja no estaba abierta) no hay con qué comparar: se
 *   aplica lo dictado sin predicciones ni drenajes y AUGE no aprende de ello (`learnFromManualAdjustment` exige predicción).
 * - Músculos: los `finalMuscleBatteries` de los `editedMuscleKeys`.
 */
internal fun finishRingEdits(closing: SessionClosingFeedback, preview: PostSessionPreview?): FinishRingEdits? {
    val neural = closing.finalNeuralBattery?.takeIf { preview == null || it != preview.neural }
    val spinal = closing.finalSpinalBattery?.takeIf { preview == null || it != preview.spinal }
    if (neural == null && spinal == null && closing.editedMuscleKeys.isEmpty()) return null
    return FinishRingEdits(
        neural = neural,
        spinal = spinal,
        muscleDeltas = closing.finalMuscleBatteries.filterKeys { it in closing.editedMuscleKeys },
        sessionCnsDrain = preview?.globalCnsDrain?.toDouble() ?: 0.0,
        sessionSpinalDrain = preview?.globalSpinalDrain?.toDouble() ?: 0.0,
        sessionMuscleDrain = preview?.globalMuscularDrain?.toDouble() ?: 0.0,
        predictedNeural = preview?.neural,
        predictedSpinal = preview?.spinalRaw,
        predictedMuscles = preview?.perMuscle?.mapValues { it.value.recoveryScore }.orEmpty(),
    )
}

/**
 * Puente entre el cierre por voz (`WorkoutViewModel`) y la pantalla. El ViewModel guarda la sesión sin pasar por Compose
 * (decisión P0), pero los anillos dictados los aplica quien tiene el `AugeViewModel` y, si la hoja seguía abierta, el
 * resumen del cierre: la pantalla. El ViewModel avisa con [onSessionSaved] cuando el log ya está escrito y la pantalla se
 * registra con [setApplier] mientras está compuesta. Si el cierre termina cuando no hay pantalla (p. ej. mientras se
 * recrea la Activity al plegar el teléfono) el aviso no se pierde: queda pendiente y se entrega al siguiente aplicador.
 * Un fallo del aplicador se comunica con [onFailure] y nunca se propaga. Solo se usa desde el hilo principal.
 */
internal class FinishRingsBridge(
    private val onFailure: (Throwable) -> Unit = {},
) {
    private var applier: ((SessionClosingFeedback) -> Unit)? = null
    private var pending: SessionClosingFeedback? = null

    /** Registra el aplicador (o lo quita con `null`). Si un cierre esperaba pantalla, se le entrega ahora. */
    fun setApplier(next: ((SessionClosingFeedback) -> Unit)?) {
        applier = next
        val waiting = pending
        if (next != null && waiting != null) {
            pending = null
            deliver(next, waiting)
        }
    }

    /** El cierre por voz ya guardó la sesión con [feedback]. */
    fun onSessionSaved(feedback: SessionClosingFeedback) {
        val current = applier
        if (current == null) pending = feedback else deliver(current, feedback)
    }

    private fun deliver(target: (SessionClosingFeedback) -> Unit, feedback: SessionClosingFeedback) {
        try {
            target(feedback)
        } catch (error: Exception) {
            onFailure(error)
        }
    }
}

/**
 * Recuerda para qué sesión ya se aplicaron los anillos del cierre. La hoja y la voz comparten aplicador y los guardas de
 * `finish` ya impiden que las dos guarden la misma sesión; si aun así llegaran las dos, la segunda no debe repetir el
 * ajuste (reanclaría y volvería a enseñar a AUGE). La clave es el id del log recién escrito. Solo se usa desde el hilo
 * principal.
 */
internal class FinishRingsOnce {
    private var appliedFor: String? = null

    /** `true` si toca aplicar para [sessionKey]; `false` si ya se aplicó para esa sesión. Sin clave no hay con qué comparar. */
    fun shouldApply(sessionKey: String?): Boolean {
        if (sessionKey.isNullOrBlank()) return true
        if (appliedFor == sessionKey) return false
        appliedFor = sessionKey
        return true
    }
}

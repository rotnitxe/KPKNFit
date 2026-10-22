package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.PostSessionPreview

/**
 * Lo que el cierre del entreno cambia en los anillos, con los argumentos que `AugeViewModel.applyManualBatteries`
 * espera. Un canal nulo es «no tocado»; las predicciones de músculos son las del resumen del cierre y solo rellenan
 * `automaticBatteryAtAnchor` (el aprendizaje evalúa su propia predicción sobre las entradas previas al gesto: el
 * resumen ya no aporta drenajes ni predicciones de Energía/Columna).
 */
internal data class FinishRingEdits(
    val neural: Int?,
    val spinal: Int?,
    val muscleDeltas: Map<String, Int>,
    val predictedMuscles: Map<String, Int>,
)

/**
 * Qué anillos cambió la persona al cerrar, o `null` si ninguno. La comparten la hoja del cierre y el cierre por voz.
 *
 * - Hoja: trae los dos valores finales y cuentan los que difieren del resumen ([preview]), por valor.
 * - Voz: solo dicta lo que dijo la persona, así que un final nulo es «no tocado» (nunca «distinto del resumen») y lo
 *   dictado es el ajuste. Si el resumen no estaba disponible (la hoja no estaba abierta) no hay con qué comparar: se
 *   aplica lo dictado sin predicciones de músculos; AUGE aprende igual con su propia predicción previa al gesto.
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
        predictedMuscles = preview?.perMuscle?.mapValues { it.value.recoveryScore }.orEmpty(),
    )
}

/**
 * Instante congelado del cierre: la finalización que ya comparten la vista previa y el log persistido. Con él el gesto
 * tiene la misma identidad aunque `onComplete` tarde o se reentregue tras recrear la pantalla. Sin instante (cierres
 * antiguos) devuelve `null` y el ViewModel usa «ahora» UNA vez, al recibir el gesto.
 */
internal fun finishObservedAtMs(closing: SessionClosingFeedback): Long? =
    closing.completionInstantIso
        ?.takeIf { it.isNotBlank() }
        ?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }

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
 * ajuste. La clave es el id del log recién escrito. Distingue «en curso» de «persistido»: [shouldApply] marca la clave
 * como provisional; solo [markPersisted] (tras confirmarse la transacción de bienestar+caché) la deja definitiva, y
 * [release] la libera si la escritura falló para poder reintentar con la misma identidad. La autoridad es el dedupe
 * durable del caché (marca de agua + anillo): esta marca en memoria solo evita entregas simultáneas. Hilo principal.
 */
internal class FinishRingsOnce {
    private val inFlight = mutableSetOf<String>()
    private val persisted = mutableSetOf<String>()

    /** `true` si toca aplicar para [sessionKey]; `false` si ya está en curso o persistida. Sin clave no hay con qué comparar. */
    fun shouldApply(sessionKey: String?): Boolean {
        if (sessionKey.isNullOrBlank()) return true
        if (sessionKey in persisted || sessionKey in inFlight) return false
        inFlight += sessionKey
        return true
    }

    /** La transacción se confirmó: la sesión queda aplicada. */
    fun markPersisted(sessionKey: String?) {
        if (sessionKey.isNullOrBlank()) return
        inFlight -= sessionKey
        persisted += sessionKey
    }

    /** La escritura falló o se canceló: se libera la marca provisional para reintentar con el mismo id. */
    fun release(sessionKey: String?) {
        if (sessionKey.isNullOrBlank()) return
        inFlight -= sessionKey
    }
}

package com.example.kpkn.domain.auge

/**
 * Lectura continua (Double) de un canal de AUGE, derivada del mismo cálculo que el entero visible.
 *
 * @property exact valor antes del redondeo visible de esa etapa (nunca una segunda evaluación del motor).
 * @property atFloor el valor está en el suelo fisiológico: `exact <= floor + 1e-9`.
 * @property blocked un tope impide que τ mande: tope de DOMS aplicado o penalización por molestia previa en un
 *   músculo; mínimo articular/guardia en la Columna mostrada; ajuste manual vigente en Músculos global.
 *   Energía y Columna cruda nunca se bloquean.
 */
data class AugeChannelReading(
    val exact: Double,
    val atFloor: Boolean,
    val blocked: Boolean,
)

/**
 * Lectura continua de los canales que el aprendiz puede observar, calculada una sola vez en [AugeSnapshotComputer].
 *
 * [energy] es la Energía cruda (antes de desaceleración y suelo) y [energyShown] la Energía visible del anillo
 * (desaceleración → suelo → composición de la evidencia inicial), que es la cifra que la persona ve y edita.
 * [spineRaw] la Columna cruda y [spineShown] el blend axial + articular + guardia del dashboard. [musclesGlobal] es
 * el anillo muscular final (con ACWR, desaceleración, suelo y evidencia inicial) y [perMuscle] cada músculo con su
 * composición de evidencia inicial si la hubiera.
 */
data class AugeContinuousReading(
    val energy: AugeChannelReading,
    val energyShown: AugeChannelReading,
    val spineRaw: AugeChannelReading,
    val spineShown: AugeChannelReading,
    val musclesGlobal: AugeChannelReading,
    val perMuscle: Map<String, AugeChannelReading>,
) {
    companion object {
        /** Valor neutro para construcciones sintéticas del snapshot; [AugeSnapshotComputer.compute] siempre lo rellena. */
        val NEUTRAL: AugeContinuousReading = AugeContinuousReading(
            energy = AugeChannelReading(exact = 0.0, atFloor = false, blocked = false),
            energyShown = AugeChannelReading(exact = 0.0, atFloor = false, blocked = false),
            spineRaw = AugeChannelReading(exact = 0.0, atFloor = false, blocked = false),
            spineShown = AugeChannelReading(exact = 0.0, atFloor = false, blocked = false),
            musclesGlobal = AugeChannelReading(exact = 0.0, atFloor = false, blocked = false),
            perMuscle = emptyMap(),
        )
    }
}

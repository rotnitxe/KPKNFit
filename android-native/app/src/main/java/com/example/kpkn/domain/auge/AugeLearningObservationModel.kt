package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.AugeAdaptiveCache
import kotlin.math.exp

/** Escala visible que consume el aprendiz: la Energía/Columna cruda del histórico o la que ve la persona. */
enum class ObservationScale { HISTORICAL_RAW, SHOWN }

/**
 * Modelo de observación real h(θ) del aprendiz: reevalúa el motor con las entradas causales ANTERIORES al gesto,
 * sustituyendo solo el τ del parámetro observado y sin escribir cachés ni activar proyecciones de horas.
 *
 * @property inputsBeforeGesture instantánea previa al gesto; su `nowMs` se reemplaza por [observedAtMs].
 * @property observedAtMs instante explícito de la observación (nunca el reloj real).
 * @property scale [ObservationScale.HISTORICAL_RAW] para el histórico comparable (Columna cruda) o
 *   [ObservationScale.SHOWN] para la cifra visible de los anillos.
 */
class SnapshotObservationModel(
    private val inputsBeforeGesture: AugeSnapshotInputs,
    private val observedAtMs: Long,
    private val scale: ObservationScale,
) : AugeObservationModel {

    override fun predict(parameterKey: String, lnTau: Double): AugePrediction {
        val key = AugeAdaptiveCacheMigration.parameterKey(parameterKey)
        val core = AugeSnapshotComputer.compute(
            inputsBeforeGesture.copy(
                nowMs = observedAtMs,
                adaptiveCache = candidateCache(key, exp(lnTau)),
            ),
        )
        val reading = when (key) {
            "cns" -> when (scale) {
                ObservationScale.HISTORICAL_RAW -> core.continuous.energy
                ObservationScale.SHOWN -> core.continuous.energyShown
            }
            "spinal" -> when (scale) {
                ObservationScale.HISTORICAL_RAW -> core.continuous.spineRaw
                ObservationScale.SHOWN -> core.continuous.spineShown
            }
            else -> lookupMuscleValue(core.continuous.perMuscle, key)
                ?: error("El snapshot no contiene la lectura del músculo '$key' (clave '$parameterKey')")
        }
        return AugePrediction(
            battery = reading.exact,
            atFloor = reading.atFloor,
            blocked = reading.blocked,
        )
    }

    /** Copia el caché cambiando únicamente el τ del parámetro; conserva estados, avisos, anclas e historial. */
    private fun candidateCache(key: String, tauHours: Double): AugeAdaptiveCache =
        when (key) {
            "cns" -> inputsBeforeGesture.adaptiveCache.copy(cnsRecoveryHours = tauHours)
            "spinal" -> inputsBeforeGesture.adaptiveCache.copy(spinalRecoveryHours = tauHours)
            else -> inputsBeforeGesture.adaptiveCache.copy(
                personalizedRecoveryHours = inputsBeforeGesture.adaptiveCache.personalizedRecoveryHours + (key to tauHours),
            )
        }
}

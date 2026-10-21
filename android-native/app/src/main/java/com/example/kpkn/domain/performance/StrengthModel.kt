package com.example.kpkn.domain.performance

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.PerformanceMode
import com.example.kpkn.data.models.plannedRepAnchor
import kotlin.math.pow
import kotlin.math.round

/** Costura para futuras curvas propias; las tres regiones comparten la curva de V1. */
enum class StrengthRegion { UNSPECIFIED, UPPER, LOWER }

/**
 * Modelo único strength-v1: curva híbrida histórica sobre reps + RIR, hasta 50.
 * Sin reserva registrada se usa RIR 0; el RIR explícito prevalece sobre el RPE.
 * Fallo, AMRAP y solo RM usan RIR 0, sin bonificación por AMRAP.
 * Las cargas se redondean a 0,1 kg, salvo la repetición única sin reserva,
 * que conserva exactamente la carga histórica. Solo proyecta; no modifica registros.
 */
object StrengthModel {
    const val VERSION = "strength-v1"

    /** RPE sin RIR: parte entera de max(0, 10 − RPE), como la prescripción histórica. */
    fun effectiveReps(reps: Int, rir: Int? = null, rpe: Double? = null, toFailure: Boolean = false): Int {
        if (reps <= 0) return 0
        val reserve = when {
            toFailure -> 0
            rir != null -> rir.coerceAtLeast(0)
            rpe != null && rpe.isFinite() -> (10.0 - rpe).coerceAtLeast(0.0).toInt()
            else -> 0
        }
        return (reps.toLong() + reserve).coerceAtMost(50).toInt()
    }

    fun estimate1RM(
        weight: Double,
        reps: Int,
        rir: Int? = null,
        rpe: Double? = null,
        toFailure: Boolean = false,
        isAmrap: Boolean = false,
        region: StrengthRegion = StrengthRegion.UNSPECIFIED,
    ): Double {
        if (!weight.isFinite() || weight <= 0.0) return 0.0
        val n = effectiveReps(reps, rir, rpe, toFailure || isAmrap)
        if (n == 0) return 0.0
        if (n == 1) return weight
        val estimated = if (n > 20) {
            weight * (1.0 + 20.0 / 30.0) * (1.0 + (n - 20) / 80.0).pow(0.9)
        } else weight * factor(n.toDouble(), region)
        return if (estimated.isFinite()) round(estimated * 10.0) / 10.0 else 0.0
    }

    /** Inversa de [estimate1RM], con el mismo redondeo histórico a 0,1 kg. */
    fun weightFor(
        e1rm: Double,
        reps: Int,
        rir: Int? = null,
        rpe: Double? = null,
        toFailure: Boolean = false,
        region: StrengthRegion = StrengthRegion.UNSPECIFIED,
    ): Double {
        if (!e1rm.isFinite() || e1rm <= 0.0) return 0.0
        val n = effectiveReps(reps, rir, rpe, toFailure)
        if (n == 0) return 0.0
        if (n == 1) return e1rm
        val weight = if (n <= 10) e1rm * ((37 - n) / 36.0) else e1rm / factor(n.toDouble(), region)
        return round(weight * 10.0) / 10.0
    }

    /** Inversa continua de la curva, sin redondear; saturada en 1..50, o 0 si no hay datos válidos. */
    fun repsToFailureAt(weight: Double, e1rm: Double, region: StrengthRegion = StrengthRegion.UNSPECIFIED): Double {
        if (!weight.isFinite() || !e1rm.isFinite() || weight <= 0.0 || e1rm <= 0.0) return 0.0
        val ratio = e1rm / weight
        return when {
            ratio <= 1.0 -> 1.0
            ratio <= factor(10.0, region) -> 37.0 - 36.0 / ratio
            ratio <= factor(20.0, region) -> 30.0 * (ratio - 1.0)
            else -> 20.0 + 80.0 * ((ratio / factor(20.0, region)).pow(1.0 / 0.9) - 1.0)
        }.coerceIn(1.0, 50.0)
    }

    /** Estima una serie realizada con su esfuerzo registrado; una serie saltada no estima. */
    fun estimate1RM(set: CompletedSet, region: StrengthRegion = StrengthRegion.UNSPECIFIED): Double {
        val payload = set.recordedPayloadV3
        if (set.skipped || payload?.skipped == true) return 0.0
        val mode = set.actualIntensityMode ?: payload?.actualIntensityMode
        val value = set.actualIntensityValue ?: payload?.actualIntensityValue
        return estimate1RM(
            weight = set.weight,
            reps = set.reps,
            rir = set.rir ?: value?.takeIf { mode == IntensityMode.RIR && it.isFinite() }?.toInt(),
            rpe = set.rpe ?: value?.takeIf { mode == IntensityMode.RPE },
            toFailure = set.isFailure || set.isFailedSet || payload?.reachedFailure == true || payload?.failedSet == true ||
                set.performanceMode == PerformanceMode.FAILURE || set.performanceMode == PerformanceMode.FAILED ||
                mode == IntensityMode.FAILURE || mode == IntensityMode.SOLO_RM,
            isAmrap = set.amrapPerformed || payload?.amrapPerformed == true || mode == IntensityMode.AMRAP,
            region = region,
        )
    }

    /** Prescribe una serie planificada sin inventar RIR cuando no está declarado. */
    fun weightFor(e1rm: Double, set: ExerciseSet, region: StrengthRegion = StrengthRegion.UNSPECIFIED): Double? {
        return weightFor(
            e1rm = e1rm,
            reps = set.plannedRepAnchor() ?: return null,
            rir = set.targetRIR,
            rpe = set.targetRPE,
            toFailure = set.isFailure || set.isAmrap || set.intensityMode == IntensityMode.FAILURE ||
                set.intensityMode == IntensityMode.AMRAP || set.intensityMode == IntensityMode.SOLO_RM,
            region = region,
        ).takeIf { it > 0.0 }
    }

    private fun factor(reps: Double, @Suppress("UNUSED_PARAMETER") region: StrengthRegion): Double = when {
        reps <= 10.0 -> 36.0 / (37.0 - reps)
        reps <= 20.0 -> 1.0 + reps / 30.0
        else -> (1.0 + 20.0 / 30.0) * (1.0 + (reps - 20.0) / 80.0).pow(0.9)
    }
}

package com.example.kpkn.domain.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.RecordedTechniqueStatus
import com.example.kpkn.data.models.SetPurpose
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.models.performedPurpose
import com.example.kpkn.data.models.plannedRepAnchor
import kotlin.math.floor

/** Pure, conservative reduction rule for an exact prior set occurrence. */
object WorkoutSetLoadAdaptation {
    fun reductionPercent(
        actual: CompletedSet,
        target: ExerciseSet,
        expectedSide: String?,
        expectedLoadMode: LoadModeV2,
        expectedUnitMode: UnitModeV2,
    ): Int? {
        if (actual.isWarmup || actual.skipped || actual.side != expectedSide ||
            actual.techniqueStatus != null || actual.dropSets.isNotEmpty() || actual.restPauses.isNotEmpty() ||
            actual.performedPurpose() != null && actual.performedPurpose() != SetPurpose.NONE
        ) return null
        val actualMode = actual.loadMode ?: actual.recordedPayloadV3?.loadInputMode ?: actual.homologatedResultV3?.loadMode
        val actualUnit = actual.recordedPayloadV3?.unitMode ?: actual.homologatedResultV3?.unitMode ?: UnitModeV2.REPS
        if (expectedLoadMode != LoadModeV2.LOAD || actualMode != LoadModeV2.LOAD ||
            expectedUnitMode != UnitModeV2.REPS || actualUnit != UnitModeV2.REPS ||
            actual.weight <= 0.0 || actual.reps <= 0
        ) return null

        val rom = actual.rom ?: actual.recordedPayloadV3?.romPercent
        if (rom != null && rom < 100) return 20
        val minimumReps = target.targetRepsRange?.min ?: target.targetReps ?: target.plannedRepAnchor()
        if (minimumReps != null && actual.reps < minimumReps) return 20

        val targetRpe = target.targetRPE
        val targetRir = target.targetRIR
        val toFailure = target.isFailure || target.intensityMode == IntensityMode.FAILURE ||
            targetRir == 0 || targetRpe?.let { it >= 10.0 } == true
        if (toFailure) return null
        val actualFailure = actual.isFailure || actual.isFailedSet ||
            actual.recordedPayloadV3?.reachedFailure == true || actual.recordedPayloadV3?.failedSet == true ||
            actual.actualIntensityMode == IntensityMode.FAILURE
        val actualRpe = actual.rpe ?: actual.actualIntensityValue
            ?.takeIf { actual.actualIntensityMode == IntensityMode.RPE && it.isFinite() }
        val actualRir = actual.rir ?: actual.actualIntensityValue
            ?.takeIf { actual.actualIntensityMode == IntensityMode.RIR && it.isFinite() }
            ?.toInt()
        val harderThanTarget = when {
            targetRpe != null -> when {
                actualFailure -> true
                actualRpe != null -> actualRpe > targetRpe
                actualRir != null -> 10.0 - actualRir < targetRpe
                else -> false
            }
            targetRir != null -> when {
                actualFailure -> true
                actualRir != null -> actualRir < targetRir
                actualRpe != null -> (10.0 - actualRpe).toInt() < targetRir
                else -> false
            }
            else -> false
        }
        return 15.takeIf { harderThanTarget }
    }

    /** For reductions, floor to the equipment step; no declared step means 0.25 kg. */
    fun reducedLoadKg(actualLoadKg: Double, reductionPercent: Int, incrementKg: Double? = null): Double? {
        if (!actualLoadKg.isFinite() || actualLoadKg <= 0.0 || reductionPercent !in setOf(15, 20)) return null
        val step = incrementKg?.takeIf { it.isFinite() && it > 0.0 } ?: 0.25
        val proposed = actualLoadKg * (100 - reductionPercent) / 100.0
        return (floor((proposed + 1e-9) / step) * step).coerceAtLeast(0.0)
    }
}

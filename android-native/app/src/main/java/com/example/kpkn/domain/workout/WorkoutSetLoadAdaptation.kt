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
import com.example.kpkn.domain.calculations.LoadRoundDirection
import com.example.kpkn.domain.calculations.LoadRounding
import com.example.kpkn.domain.performance.StrengthModel
import kotlin.math.floor

data class IntraSessionLoadSuggestion(
    val weightKg: Double,
    val reason: String,
    val isAdjusted: Boolean,
)

/** Pure, conservative reduction and intra-session progression rules for prior set occurrences. */
object WorkoutSetLoadAdaptation {
    fun reductionPercent(
        actual: CompletedSet,
        target: ExerciseSet,
        expectedSide: String?,
        expectedLoadMode: LoadModeV2,
        expectedUnitMode: UnitModeV2,
    ): Int? {
        if (!isEligiblePriorSet(actual, expectedSide, expectedLoadMode, expectedUnitMode)) return null

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
        val actualRpe = resolvedRpe(actual)
        val actualRir = actual.rir ?: actual.actualIntensityValue
            ?.takeIf { actual.actualIntensityMode == IntensityMode.RIR && it.isFinite() }
            ?.toInt()
        val harderThanTarget = when {
            targetRpe != null -> when {
                actualFailure -> true
                actualRpe != null -> actualRpe > targetRpe
                actualRir != null -> 10.0 - actualRir > targetRpe
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

    fun suggestNextSetLoad(
        priorSetsInSession: List<CompletedSet>,
        nextSet: ExerciseSet,
        previousPlannedSet: ExerciseSet? = null,
        expectedSide: String? = null,
        expectedLoadMode: LoadModeV2 = LoadModeV2.LOAD,
        expectedUnitMode: UnitModeV2 = UnitModeV2.REPS,
        stepKg: Double = LoadRounding.DEFAULT_INCREMENT_KG,
    ): IntraSessionLoadSuggestion? {
        val eligible = priorSetsInSession.filter {
            isEligiblePriorSet(it, expectedSide, expectedLoadMode, expectedUnitMode)
        }
        if (eligible.isEmpty()) return null
        val last = eligible.last()
        val lastLoad = LoadSuggestionEngine.inputLoad(last, LoadModeV2.LOAD)
        if (!lastLoad.isFinite() || lastLoad <= 0.0) return null
        val safeStep = stepKg.takeIf { it.isFinite() && it > 0.0 } ?: LoadRounding.DEFAULT_INCREMENT_KG
        val completedCount = eligible.size

        val reduction = reductionPercent(last, nextSet, expectedSide, expectedLoadMode, expectedUnitMode)
        val explicitLowerDrop = nextSet.weight != null &&
            nextSet.weight > 0.0 && (
                (previousPlannedSet?.weight != null && nextSet.weight < previousPlannedSet.weight - 1e-4) ||
                    (nextSet.isDropSet && nextSet.weight < lastLoad - 1e-4)
                )
        if (explicitLowerDrop && reduction != 20) {
            return null
        }

        val nextTargetReps = nextSet.targetReps ?: nextSet.plannedRepAnchor()
        val previousPlannedReps = previousPlannedSet?.let { it.targetReps ?: it.plannedRepAnchor() }
        val plannedRepsChanged = previousPlannedReps != null && nextTargetReps != null && nextTargetReps != previousPlannedReps
        val repProjectedBase = if (plannedRepsChanged && nextTargetReps > 0 && nextTargetReps != last.reps) {
            val e1rm = StrengthModel.estimate1RM(last)
            val forNextReps = if (e1rm.isFinite() && e1rm > 0.0) {
                StrengthModel.weightFor(e1rm, nextSet.copy(targetReps = nextTargetReps))
            } else null
            when {
                forNextReps == null || !forNextReps.isFinite() || forNextReps <= 0.0 -> lastLoad
                nextTargetReps > last.reps -> forNextReps.coerceAtMost(lastLoad)
                else -> forNextReps
            }
        } else {
            lastLoad
        }

        if (reduction == 20) {
            val rounded = LoadRounding.roundToStep(lastLoad * 0.80, safeStep, LoadRoundDirection.FLOOR)
            return IntraSessionLoadSuggestion(
                weightKg = rounded,
                reason = "Reducir 20 % · reps bajo el mínimo o ROM incompleto",
                isAdjusted = true,
            )
        }

        val actualRpe = resolvedRpe(last)
        val targetRpe = nextSet.targetRPE ?: nextSet.targetRIR?.let { 10.0 - it }
            ?: if (nextSet.isFailure || nextSet.intensityMode == IntensityMode.FAILURE) 10.0 else null

        if (reduction == 15) {
            val overshoot = if (actualRpe != null && targetRpe != null) (actualRpe - targetRpe).coerceAtLeast(0.0) else 1.0
            val rounded = if (overshoot >= 1.5) {
                val dropRatio = if (overshoot >= 2.0) 0.15 else 0.10
                val floored = LoadRounding.roundToStep(repProjectedBase * (1.0 - dropRatio), safeStep, LoadRoundDirection.FLOOR)
                if (floored >= lastLoad && lastLoad > safeStep) {
                    LoadRounding.roundToStep(lastLoad - safeStep, safeStep, LoadRoundDirection.NEAREST)
                } else {
                    floored
                }
            } else {
                val mildDrop = (0.035 + overshoot * 0.015).coerceAtMost(0.08)
                val nearest = LoadRounding.roundToStep(repProjectedBase * (1.0 - mildDrop), safeStep, LoadRoundDirection.NEAREST)
                if (nearest >= lastLoad && lastLoad > safeStep) {
                    LoadRounding.roundToStep(lastLoad - safeStep, safeStep, LoadRoundDirection.NEAREST)
                } else {
                    nearest
                }
            }
            return IntraSessionLoadSuggestion(
                weightKg = rounded,
                reason = "Ajuste por esfuerzo · serie previa",
                isAdjusted = true,
            )
        }

        val maintainsOrRaisesReps = nextTargetReps == null || nextTargetReps >= last.reps
        if (actualRpe != null && actualRpe >= 8.0 && maintainsOrRaisesReps) {
            val baseDrop = when {
                actualRpe >= 9.5 -> 0.05
                actualRpe >= 8.5 -> 0.035
                else -> 0.025
            }
            val accumBonus = if (completedCount >= 2) 0.015 else 0.0
            val totalDrop = (baseDrop + accumBonus).coerceAtMost(0.10)
            val rawTarget = repProjectedBase * (1.0 - totalDrop)
            val rounded = LoadRounding.roundToStep(rawTarget, safeStep, LoadRoundDirection.NEAREST)
            val changedFromLast = kotlin.math.abs(rounded - LoadRounding.roundToStep(lastLoad, safeStep, LoadRoundDirection.NEAREST)) > 1e-4 &&
                rounded < lastLoad
            return IntraSessionLoadSuggestion(
                weightKg = rounded,
                reason = if (changedFromLast) "Ajuste progresivo · serie $completedCount" else "Basado en serie $completedCount",
                isAdjusted = changedFromLast,
            )
        }

        val roundedCarryover = LoadRounding.roundToStep(repProjectedBase, safeStep, LoadRoundDirection.NEAREST)
        val repChanged = nextTargetReps != null && nextTargetReps != last.reps &&
            kotlin.math.abs(roundedCarryover - LoadRounding.roundToStep(lastLoad, safeStep, LoadRoundDirection.NEAREST)) > 1e-4
        return IntraSessionLoadSuggestion(
            weightKg = roundedCarryover,
            reason = if (repChanged) "Ajustado a $nextTargetReps reps · serie $completedCount" else "Basado en serie $completedCount",
            isAdjusted = repChanged,
        )
    }

    private fun isEligiblePriorSet(
        actual: CompletedSet,
        expectedSide: String?,
        expectedLoadMode: LoadModeV2,
        expectedUnitMode: UnitModeV2,
    ): Boolean {
        if (actual.isWarmup || actual.skipped || actual.side != expectedSide ||
            actual.techniqueStatus == RecordedTechniqueStatus.OPEN ||
            actual.dropSets.isNotEmpty() || actual.restPauses.isNotEmpty() ||
            (actual.performedPurpose() != null && actual.performedPurpose() != SetPurpose.NONE)
        ) return false
        val actualMode = actual.loadMode ?: actual.recordedPayloadV3?.loadInputMode ?: actual.homologatedResultV3?.loadMode ?: LoadModeV2.LOAD
        val actualUnit = actual.recordedPayloadV3?.unitMode ?: actual.homologatedResultV3?.unitMode ?: UnitModeV2.REPS
        return expectedLoadMode == LoadModeV2.LOAD && actualMode == LoadModeV2.LOAD &&
            expectedUnitMode == UnitModeV2.REPS && actualUnit == UnitModeV2.REPS &&
            actual.weight > 0.0 && actual.reps > 0
    }

    private fun resolvedRpe(set: CompletedSet): Double? {
        val explicitFailure = set.isFailure || set.isFailedSet ||
            set.recordedPayloadV3?.reachedFailure == true || set.recordedPayloadV3?.failedSet == true ||
            set.actualIntensityMode == IntensityMode.FAILURE
        if (explicitFailure) return 10.0
        val explicitRpe = set.rpe ?: set.actualIntensityValue
            ?.takeIf { set.actualIntensityMode == IntensityMode.RPE && it.isFinite() }
        if (explicitRpe != null) return explicitRpe.coerceIn(1.0, 10.0)
        val explicitRir = set.rir ?: set.actualIntensityValue
            ?.takeIf { set.actualIntensityMode == IntensityMode.RIR && it.isFinite() }
            ?.toInt()
        return explicitRir?.let { (10.0 - it).coerceIn(1.0, 10.0) }
    }
}

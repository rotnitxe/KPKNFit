package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.domain.calculations.resolveReferenceCapacity
import com.example.kpkn.domain.performance.StrengthModel

/** Shared eligibility rules for a measured live RM reference. */
internal fun eligibleWorkoutRmEstimate(
    set: CompletedSet,
    expectedLoadMode: LoadModeV2? = null,
): Double? {
    if (set.skipped || set.isWarmup || set.isFailedSet || set.reps <= 0 ||
        set.techniqueStatus != null || set.dropSets.isNotEmpty() || set.restPauses.isNotEmpty() ||
        set.timeSeconds != null || set.distanceKm != null || set.recordedPayloadV3?.durationSeconds != null
    ) return null
    val unit = set.recordedPayloadV3?.unitMode ?: set.homologatedResultV3?.unitMode
    if (unit != null && unit != UnitModeV2.REPS) return null
    val loadMode = set.loadMode ?: set.recordedPayloadV3?.loadInputMode
        ?: set.homologatedResultV3?.loadMode ?: LoadModeV2.LOAD
    if (expectedLoadMode != null && loadMode != expectedLoadMode) return null
    return StrengthModel.estimate1RM(set).takeIf { it.isFinite() && it > 0.0 }
}

/** Declared 1RM/PR/calculated capacity is a fallback; a TM remains a separate prescription. */
internal fun declaredWorkoutRmReference(exercise: Exercise): Double? =
    resolveReferenceCapacity(exercise.copy(trainingMode = TrainingMode.RM))
        ?.takeIf { it.isFinite() && it > 0.0 }
        ?: exercise.calculated1RM?.takeIf { it.isFinite() && it > 0.0 }

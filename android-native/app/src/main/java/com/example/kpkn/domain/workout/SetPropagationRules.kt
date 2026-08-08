package com.example.kpkn.domain.workout

import com.example.kpkn.data.models.ExerciseSet

/** Which unilateral target is copied when a planned set value propagates. */
enum class PropagationSide {
    BOTH,
    LEFT,
    RIGHT,
}

/**
 * Copies planned value fields while preserving identity and the empty-slot
 * marker. Empty superset slots are never turned into real rounds by this
 * operation; the editor filters them before calling this function.
 */
fun ExerciseSet.copyPlannedValueFrom(
    source: ExerciseSet,
    side: PropagationSide = PropagationSide.BOTH,
): ExerciseSet {
    val copiedBase = copy(
        targetReps = source.targetReps,
        targetDuration = source.targetDuration,
        plannedTargetV2 = source.plannedTargetV2,
        targetPercentageRM = source.targetPercentageRM,
        targetRPE = source.targetRPE,
        targetRIR = source.targetRIR,
        intensityMode = source.intensityMode,
        isFailure = source.isFailure,
        isAmrap = source.isAmrap,
        weight = source.weight,
        loadModeV2 = source.loadModeV2,
    )
    if (leftTarget == null && rightTarget == null && source.leftTarget == null && source.rightTarget == null) {
        return copiedBase
    }
    return when (side) {
        PropagationSide.BOTH -> copiedBase.copy(
            leftTarget = source.leftTarget,
            rightTarget = source.rightTarget,
        )
        PropagationSide.LEFT -> copiedBase.copy(leftTarget = source.leftTarget)
        PropagationSide.RIGHT -> copiedBase.copy(rightTarget = source.rightTarget)
    }
}

package com.example.kpkn.domain.workout

import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.UnilateralTarget

/** Which unilateral target is copied when a planned set value propagates. */
enum class PropagationSide {
    BOTH,
    LEFT,
    RIGHT,
}

/** A single editor value that is allowed to propagate to sibling sets. */
enum class SetPropagationField {
    REPS,
    DURATION,
    LOAD,
    LOAD_MODE,
    INTENSITY,
    FAILURE,
    LEFT_OBJECTIVE,
    RIGHT_OBJECTIVE,
    OBJECTIVES,
}

/**
 * Copies exactly one logical editor field. This deliberately does not reuse
 * [copyPlannedValueFrom], whose legacy contract copies the complete planned
 * value bundle.
 */
fun ExerciseSet.copyEditedFieldFrom(
    source: ExerciseSet,
    field: SetPropagationField,
    side: PropagationSide = PropagationSide.BOTH,
): ExerciseSet {
    val lateralSide = when {
        side == PropagationSide.LEFT && leftTarget != null -> PropagationSide.LEFT
        side == PropagationSide.RIGHT && rightTarget != null -> PropagationSide.RIGHT
        else -> null
    }
    if (lateralSide != null && field !in setOf(
            SetPropagationField.LOAD_MODE,
            SetPropagationField.FAILURE,
            SetPropagationField.LEFT_OBJECTIVE,
            SetPropagationField.RIGHT_OBJECTIVE,
            SetPropagationField.OBJECTIVES,
        )) {
        fun copyTarget(target: UnilateralTarget?, sourceTarget: UnilateralTarget?): UnilateralTarget? {
            if (sourceTarget == null) return null
            val base = target ?: UnilateralTarget()
            return when (field) {
                SetPropagationField.REPS -> base.copy(targetReps = sourceTarget.targetReps)
                SetPropagationField.DURATION -> base.copy(targetDuration = sourceTarget.targetDuration)
                SetPropagationField.LOAD -> base.copy(weight = sourceTarget.weight)
                SetPropagationField.INTENSITY -> base.copy(
                    targetRPE = sourceTarget.targetRPE,
                    targetRIR = sourceTarget.targetRIR,
                    intensityMode = sourceTarget.intensityMode,
                )
                else -> base
            }
        }
        return when (lateralSide) {
            PropagationSide.LEFT -> copy(leftTarget = copyTarget(leftTarget, source.leftTarget))
            PropagationSide.RIGHT -> copy(rightTarget = copyTarget(rightTarget, source.rightTarget))
            PropagationSide.BOTH -> this
        }
    }
    return when (field) {
        SetPropagationField.REPS -> copy(targetReps = source.targetReps)
        SetPropagationField.DURATION -> copy(targetDuration = source.targetDuration)
        SetPropagationField.LOAD -> copy(weight = source.weight)
        SetPropagationField.LOAD_MODE -> copy(loadModeV2 = source.loadModeV2)
        SetPropagationField.INTENSITY -> copy(
            targetRPE = source.targetRPE,
            targetRIR = source.targetRIR,
            intensityMode = source.intensityMode,
        )
        SetPropagationField.FAILURE -> copy(isFailure = source.isFailure)
        SetPropagationField.LEFT_OBJECTIVE -> copy(leftTarget = source.leftTarget)
        SetPropagationField.RIGHT_OBJECTIVE -> copy(rightTarget = source.rightTarget)
        SetPropagationField.OBJECTIVES -> when (side) {
            PropagationSide.LEFT -> copy(leftTarget = source.leftTarget)
            PropagationSide.RIGHT -> copy(rightTarget = source.rightTarget)
            PropagationSide.BOTH -> copy(leftTarget = source.leftTarget, rightTarget = source.rightTarget)
        }
    }
}

/** Propagates a field inside the already-scoped exercise/superset member list. */
fun propagateEditedField(
    sets: List<ExerciseSet>,
    sourceId: String,
    field: SetPropagationField,
    side: PropagationSide = PropagationSide.BOTH,
): List<ExerciseSet> {
    val source = sets.firstOrNull { it.id == sourceId } ?: return sets
    return sets.map { target ->
        if (target.id == sourceId || target.isEmptySlot) target
        else target.copyEditedFieldFrom(source, field, side)
    }
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

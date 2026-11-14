package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet

/** Allocates a new stable set slot without reusing identities from removed or retained sets. */
internal fun nextPrescriptionSlotId(exercise: Exercise, occupiedSlotIds: Set<String>): String? {
    val prefix = when {
        !exercise.recipeDayId.isNullOrBlank() && !exercise.recipeSlotId.isNullOrBlank() ->
            "${exercise.recipeDayId}|${exercise.recipeSlotId}|set:"
        !exercise.occurrenceId.isNullOrBlank() -> "occurrence:${exercise.occurrenceId}|set:"
        else -> return null
    }
    var ordinal = 0
    while ("$prefix$ordinal" in occupiedSlotIds) ordinal++
    return "$prefix$ordinal"
}

/** Repairs copied slot IDs on newly inserted sets while preserving every preexisting occurrence. */
internal fun assignStablePrescriptionSlots(previous: Exercise, proposed: Exercise): Exercise {
    val previousSetIds = previous.sets.mapTo(mutableSetOf()) { it.id }
    val previousSlotIds = previous.sets.mapNotNullTo(mutableSetOf()) { it.plannedSlotId }
    val occupied = (previous.sets + proposed.sets).mapNotNullTo(mutableSetOf()) { it.plannedSlotId }
    val seenNewSlots = mutableSetOf<String>()
    val sets = proposed.sets.map { set ->
        if (set.id in previousSetIds) {
            set.plannedSlotId?.let(seenNewSlots::add)
            set
        } else {
            val candidate = set.plannedSlotId
            val slotId = if (!candidate.isNullOrBlank() && candidate !in previousSlotIds && candidate !in seenNewSlots) {
                candidate
            } else {
                nextPrescriptionSlotId(proposed, occupied)
            }
            if (!slotId.isNullOrBlank()) {
                occupied += slotId
                seenNewSlots += slotId
            }
            set.copy(plannedSlotId = slotId)
        }
    }
    return proposed.copy(sets = sets)
}

private data class FieldPatch<T>(val valid: Boolean, val value: T?)

private fun <T> patchExact(current: T?, before: T?, after: T?, source: Boolean): FieldPatch<T> = when {
    before == after -> FieldPatch(true, current)
    source && current != before && current != after -> FieldPatch(false, null)
    else -> FieldPatch(true, after)
}

private fun patchInt(current: Int?, before: Int?, after: Int?, source: Boolean): FieldPatch<Int> = when {
    before == after -> FieldPatch(true, current)
    source && current != before && current != after -> FieldPatch(false, null)
    source || current == null || before == null || after == null -> FieldPatch(true, after)
    else -> FieldPatch(true, current + (after - before))
}

private fun patchDouble(current: Double?, before: Double?, after: Double?, source: Boolean): FieldPatch<Double> = when {
    before == after -> FieldPatch(true, current)
    source && current != before && current != after -> FieldPatch(false, null)
    source || current == null || before == null || after == null -> FieldPatch(true, after)
    else -> FieldPatch(true, current + (after - before))
}

/** Applies only changed prescription fields. Source conflicts reject; later occurrences receive numeric deltas. */
internal fun PrescriptionSetDelta.applyTo(current: ExerciseSet, source: Boolean): ExerciseSet? {
    val existing = SetPrescriptionSnapshot.from(current)
    if (source) {
        if (existing == before) return after.applyTo(current)
        if (existing == after) return current
        return null
    }

    val reps = patchInt(existing.targetReps, before.targetReps, after.targetReps, source)
    val duration = patchInt(existing.targetDuration, before.targetDuration, after.targetDuration, source)
    val rpe = patchDouble(existing.targetRPE, before.targetRPE, after.targetRPE, source)
    val rir = patchInt(existing.targetRIR, before.targetRIR, after.targetRIR, source)
    val intensity = patchExact(existing.intensityMode, before.intensityMode, after.intensityMode, source)
    val percentage = patchDouble(existing.targetPercentageRM, before.targetPercentageRM, after.targetPercentageRM, source)
    val weight = patchDouble(existing.weight, before.weight, after.weight, source)
    val plannedTarget = patchDouble(existing.plannedTargetV2, before.plannedTargetV2, after.plannedTargetV2, source)
    val range = patchExact(existing.targetRepsRange, before.targetRepsRange, after.targetRepsRange, source)
    val targetFields = listOf(reps.valid, duration.valid, rpe.valid, rir.valid, intensity.valid, percentage.valid, weight.valid, plannedTarget.valid, range.valid)
    if (targetFields.any { !it }) return null
    fun <T> exact(current: T?, old: T?, next: T?) = patchExact(current, old, next, source)

    val technique = exact(existing.advancedTechnique, before.advancedTechnique, after.advancedTechnique)
    val purpose = exact(existing.purpose, before.purpose, after.purpose)
    val amrap = exact(existing.amrapMinimumReps, before.amrapMinimumReps, after.amrapMinimumReps)
    val calibration = exact(existing.calibrationScope, before.calibrationScope, after.calibrationScope)
    val drop = exact(existing.isDropSet, before.isDropSet, after.isDropSet)
    val pause = exact(existing.isRestPause, before.isRestPause, after.isRestPause)
    val drops = exact(existing.dropSets, before.dropSets, after.dropSets)
    val pauses = exact(existing.restPauses, before.restPauses, after.restPauses)
    val performance = exact(existing.performanceMode, before.performanceMode, after.performanceMode)
    val loadMode = exact(existing.loadModeV2, before.loadModeV2, after.loadModeV2)
    val unitMode = exact(existing.unitModeV2, before.unitModeV2, after.unitModeV2)
    val progression = exact(existing.timeProgressionStrategyV3, before.timeProgressionStrategyV3, after.timeProgressionStrategyV3)
    val topSet = exact(existing.isTopSet, before.isTopSet, after.isTopSet)
    val loadBasis = exact(existing.loadBasis, before.loadBasis, after.loadBasis)
    val convention = exact(existing.loadQuantityConvention, before.loadQuantityConvention, after.loadQuantityConvention)
    val left = exact(existing.leftTarget, before.leftTarget, after.leftTarget)
    val right = exact(existing.rightTarget, before.rightTarget, after.rightTarget)
    val manualSides = exact(existing.manualLoadRequiredSides, before.manualLoadRequiredSides, after.manualLoadRequiredSides)
    val rest = exact(existing.restAfterSeconds, before.restAfterSeconds, after.restAfterSeconds)
    val plannedTechniques = exact(
        existing.plannedIntensityTechniques,
        before.plannedIntensityTechniques,
        after.plannedIntensityTechniques,
    )
    val exactFields = listOf(
        technique.valid, purpose.valid, amrap.valid, calibration.valid, drop.valid, pause.valid,
        drops.valid, pauses.valid, performance.valid, loadMode.valid, unitMode.valid, progression.valid,
        topSet.valid, loadBasis.valid, convention.valid, left.valid, right.valid, manualSides.valid,
        rest.valid, plannedTechniques.valid,
    )
    if (exactFields.any { !it }) return null

    return current.copy(
        targetReps = reps.value,
        targetRepsRange = range.value,
        targetDuration = duration.value,
        targetRPE = rpe.value,
        targetRIR = rir.value,
        intensityMode = intensity.value,
        targetPercentageRM = percentage.value,
        weight = weight.value,
        plannedTargetV2 = plannedTarget.value,
        advancedTechnique = technique.value,
        purpose = purpose.value ?: current.purpose,
        amrapMinimumReps = amrap.value,
        calibrationScope = calibration.value,
        isDropSet = drop.value ?: current.isDropSet,
        isRestPause = pause.value ?: current.isRestPause,
        dropSets = drops.value ?: current.dropSets,
        restPauses = pauses.value ?: current.restPauses,
        performanceMode = performance.value,
        loadModeV2 = loadMode.value,
        unitModeV2 = unitMode.value,
        timeProgressionStrategyV3 = progression.value ?: current.timeProgressionStrategyV3,
        isTopSet = topSet.value ?: current.isTopSet,
        loadBasis = loadBasis.value,
        loadQuantityConvention = convention.value ?: current.loadQuantityConvention,
        leftTarget = left.value,
        rightTarget = right.value,
        manualLoadRequiredSides = manualSides.value ?: current.manualLoadRequiredSides,
        restAfterSeconds = rest.value,
        plannedIntensityTechniques = plannedTechniques.value ?: current.plannedIntensityTechniques,
    )
}

internal fun SetPrescriptionSnapshot.applyTo(current: ExerciseSet): ExerciseSet = current.copy(
    targetReps = targetReps,
    targetRepsRange = targetRepsRange,
    targetDuration = targetDuration,
    targetRPE = targetRPE,
    targetRIR = targetRIR,
    intensityMode = intensityMode,
    targetPercentageRM = targetPercentageRM,
    weight = weight,
    advancedTechnique = advancedTechnique,
    purpose = purpose,
    amrapMinimumReps = amrapMinimumReps,
    calibrationScope = calibrationScope,
    isDropSet = isDropSet,
    isRestPause = isRestPause,
    dropSets = dropSets,
    restPauses = restPauses,
    performanceMode = performanceMode,
    loadModeV2 = loadModeV2,
    unitModeV2 = unitModeV2,
    plannedTargetV2 = plannedTargetV2,
    timeProgressionStrategyV3 = timeProgressionStrategyV3,
    isTopSet = isTopSet,
    loadBasis = loadBasis,
    loadQuantityConvention = loadQuantityConvention,
    leftTarget = leftTarget,
    rightTarget = rightTarget,
    manualLoadRequiredSides = manualLoadRequiredSides,
    restAfterSeconds = restAfterSeconds,
    plannedIntensityTechniques = plannedIntensityTechniques,
)

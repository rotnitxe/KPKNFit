package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.workout.WorkoutPerformanceHomologationEngine

/** Replays confirmed observations over historical baselines; retries and edits do not add samples. */
internal fun rebuildRecordedPerformance(
    state: WorkoutUiState,
    contextBaseline: (String) -> ContextPerformanceStateV2?,
    globalBaseline: (String) -> GlobalPerformanceStateV3?,
): WorkoutUiState {
    if (!state.featureFlags.workoutV2Homologation && !state.featureFlags.workoutV3UnifiedFlow && !state.featureFlags.workoutV2LoadModes) return state
    val contexts = linkedMapOf<String, ContextPerformanceStateV2>()
    val globals = linkedMapOf<String, GlobalPerformanceStateV3>()
    val evaluated = linkedMapOf<Triple<String, WorkoutSetExecutionRef?, String?>, CompletedSet>()
    val all = state.archivedCompletedExercises.flatMap { it.sets } + state.completedSets.values
    all.distinctBy(::recordedObservationIdentity).sortedWith(compareBy<CompletedSet> { it.recordedAtMs ?: Long.MIN_VALUE }
        .thenBy { it.sourceRef?.toString().orEmpty() }.thenBy { it.id }.thenBy { it.side.orEmpty() }).forEach { original ->
        val payload = original.recordedPayloadV3 ?: return@forEach
        if (original.skipped || original.isWarmup) return@forEach
        val old = original.setOutcomeV2 ?: return@forEach
        val quantity = completedLiveQuantity(original, payload.unitMode) ?: return@forEach
        val debt = if (payload.unitMode == UnitModeV2.REPS && (payload.plannedRepRange != null || original.amrapPerformed))
            evaluateRepRange(quantity, payload.plannedRepRange, original.amrapPerformed, original.amrapMinimumReps)?.debt ?: 0.0
            else ((payload.plannedTargetSnapshot ?: old.plannedTarget ?: quantity) - quantity).coerceAtLeast(0.0)
        val globalKey = payload.exerciseDbId ?: payload.exerciseId
        val entry = SetEntryV2(exerciseId = payload.exerciseId, exerciseDbId = payload.exerciseDbId,
            canonicalExerciseId = payload.exerciseDbId, setIndex = original.setIndex ?: 0,
            loadMode = payload.loadInputMode, unitMode = payload.unitMode, actualValue = quantity,
            completedReps = original.reps.takeIf { payload.unitMode == UnitModeV2.REPS },
            loggedLoad = if (payload.loadInputMode == LoadModeV2.ASSISTED) payload.assistedLoad else payload.externalLoad,
            bodyWeight = payload.bodyWeightSnapshot, plannedTarget = old.plannedTarget,
            actualIntensity = original.actualIntensityValue, debt = debt,
            failedSet = original.isFailedSet, reachedFailure = original.isFailure,
            amrapOverride = original.amrapPerformed, techniques = payload.techniques,
            contextKey = old.contextKey, tagId = original.tagId, setupId = original.setupProfileId,
            machineBrand = original.machineBrand, isFirstEvaluationInSession = old.contextKey !in contexts)
        val result = WorkoutPerformanceHomologationEngine.evaluate(entry,
            contexts[old.contextKey] ?: contextBaseline(old.contextKey), globals[globalKey] ?: globalBaseline(globalKey),
            original.actualIntensityMode)
        contexts[old.contextKey] = result.nextState
        globals[result.nextGlobalState.globalKey] = result.nextGlobalState
        evaluated[recordedObservationIdentity(original)] = original.copy(debt = debt, homologatedResultV3 = result.homologated, setOutcomeV2 = result.outcome)
    }
    return state.copy(completedSets = state.completedSets.mapValues { evaluated[recordedObservationIdentity(it.value)] ?: it.value },
        archivedCompletedExercises = state.archivedCompletedExercises.map { ex -> ex.copy(sets = ex.sets.map { evaluated[recordedObservationIdentity(it)] ?: it }) },
        contextualPerformanceCache = contexts, globalPerformanceCache = globals,
        lastSetOutcomeV2 = state.setJustLoggedKey?.let { state.completedSets[it]?.let(::recordedObservationIdentity) }?.let(evaluated::get)?.setOutcomeV2,
        lastHomologatedResultV3 = state.setJustLoggedKey?.let { state.completedSets[it]?.let(::recordedObservationIdentity) }?.let(evaluated::get)?.homologatedResultV3)
}

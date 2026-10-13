package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.exercises.ExerciseMuscleResolver
import com.example.kpkn.domain.sessionassistant.AssistantActionType
import com.example.kpkn.domain.sessionassistant.AssistantDetailAction
import com.example.kpkn.domain.sessionassistant.AssistantSuggestionDetail
import com.example.kpkn.domain.training.VolumeCalculator
import com.example.kpkn.domain.workout.SupersetRules
import kotlin.math.roundToInt

fun SessionEditorViewModel.applyAssistantSuggestion(
    suggestionId: String,
    acceptedDetailIds: List<String>? = null,
) {
    val suggestion = currentUiState.assistantReport?.ajustes
        ?.firstOrNull { it.id == suggestionId } ?: return

    val details = if (!acceptedDetailIds.isNullOrEmpty()) {
        suggestion.details.filter { it.id in acceptedDetailIds }
    } else if (suggestion.details.isNotEmpty()) {
        suggestion.details.filter { it.defaultAccepted }
    } else {
        emptyList()
    }

    if (details.isNotEmpty()) {
        updateSession { session ->
            var next = session
            details.forEach { detail ->
                // A4b: skip superset si ya está agrupado (evita solapamiento ex₁ en 2 sugerencias)
                if (detail.action is AssistantDetailAction.ConvertToSuperset) {
                    val exId = detail.action.exerciseId
                    val all = next.allExercises()
                    val idx = all.indexOfFirst { it.id == exId }
                    val target = all.getOrNull(idx)
                    val partner = all.getOrNull(idx + 1)
                    if (target?.supersetGroupRefOrLegacyId() != null || partner?.supersetGroupRefOrLegacyId() != null) {
                        return@forEach
                    }
                }
                next = applyAssistantDetail(next, detail)
            }
            next
        }
        return
    }

    // Legacy single-action suggestions (no details)
    when (suggestion.type) {
        AssistantActionType.REDUCE_SET -> {
            val muscle = suggestion.muscle
            if (muscle != null) {
                updateSession { session -> reduceSetsForMuscle(session, muscle) }
            }
        }
        AssistantActionType.LOWER_RPE -> {
            updateSession { session ->
                if (suggestion.exerciseId != null) {
                    lowerRpeOnExercise(session, suggestion.exerciseId)
                } else {
                    lowerRpeOnAllExercises(session)
                }
            }
        }
        AssistantActionType.REMOVE_FAILURE -> {
            updateSession { session -> convertFailureToRir(session) }
        }
        AssistantActionType.REDUCE_REST_TIME -> {
            updateSession { session ->
                session.transformExercises { exercise ->
                    val currentRest = exercise.restTime ?: 90
                    exercise.copy(restTime = maxOf(30, currentRest - 15))
                }
            }
        }
        AssistantActionType.CONVERT_TO_DROPSET -> {
            val exerciseId = suggestion.exerciseId ?: return
            updateSession { session -> convertExerciseToDropSet(session, exerciseId) }
        }
        AssistantActionType.CONVERT_TO_SUPERSET -> {
            val targetExerciseId = suggestion.exerciseId ?: return
            updateSession { session -> convertToSupersetWithNext(session, targetExerciseId) }
        }
        AssistantActionType.APPLY_TEMPLATE -> {
            val preview = currentUiState.assistantReport?.plantillasCompatibles?.firstOrNull()
            if (preview == null) {
                updateUi { it.copy(snackbarMessage = "No hay una plantilla compatible para aplicar.") }
            } else {
                selectTemplate(preview.template)
            }
        }
        else -> Unit
    }
}

private fun SessionEditorViewModel.applyAssistantDetail(
    session: Session,
    detail: AssistantSuggestionDetail,
): Session = when (val action = detail.action) {
    is AssistantDetailAction.LowerRpe -> {
        if (action.exerciseId != null) {
            lowerRpeOnExercise(session, action.exerciseId, action.amount)
        } else {
            lowerRpeOnAllExercises(session)
        }
    }
    is AssistantDetailAction.ReduceSet -> {
        when {
            action.exerciseId != null -> reduceSetsForExercise(session, action.exerciseId)
            action.muscle != null -> reduceSetsForMuscle(session, action.muscle)
            else -> session
        }
    }
    is AssistantDetailAction.ReduceRest -> {
        session.transformExercises { exercise ->
            val currentRest = exercise.restTime ?: 90
            exercise.copy(restTime = maxOf(30, currentRest - action.seconds))
        }
    }
    is AssistantDetailAction.ConvertToDropSet -> convertExerciseToDropSet(session, action.exerciseId)
    is AssistantDetailAction.ConvertToSuperset -> convertToSupersetWithNext(session, action.exerciseId)
    AssistantDetailAction.RemoveFailure -> convertFailureToRir(session)
}

internal fun SessionEditorViewModel.reduceSetsForExercise(session: Session, exerciseId: String): Session {
    return session.transformExercises { exercise ->
        if (exercise.id != exerciseId || exercise.sets.size <= 1) exercise
        else exercise.copy(sets = exercise.sets.dropLast(1))
    }
}

internal fun SessionEditorViewModel.lowerRpeOnExercise(
    session: Session,
    exerciseId: String,
    amount: Double = 0.5,
): Session {
    return session.transformExercises { exercise ->
        if (exercise.id != exerciseId) return@transformExercises exercise
        exercise.copy(sets = exercise.sets.map { set ->
            when (set.intensityMode) {
                IntensityMode.FAILURE -> set.copy(
                    intensityMode = IntensityMode.RIR,
                    targetRIR = 1,
                    isFailure = false,
                )
                IntensityMode.RPE -> {
                    val currentRpe = set.targetRPE ?: 8.0
                    set.copy(targetRPE = maxOf(6.0, currentRpe - amount))
                }
                IntensityMode.RIR -> {
                    val currentRir = set.targetRIR ?: 2
                    set.copy(targetRIR = (currentRir + amount.roundToInt()).coerceAtMost(5))
                }
                else -> set
            }
        })
    }
}

internal fun SessionEditorViewModel.convertExerciseToDropSet(session: Session, exerciseId: String): Session {
    return session.transformExercises { exercise ->
        if (exercise.id != exerciseId) return@transformExercises exercise
        val updatedSets = exercise.sets.map { set ->
            if (set.isDropSet) set else set.copy(
                isDropSet = true,
                dropSets = listOf(
                    com.example.kpkn.data.models.DropSetData(
                        weight = set.weight ?: 0.0,
                        reps = (set.plannedRepAnchor() ?: 8) / 2,
                    ),
                ),
            )
        }
        exercise.copy(sets = updatedSets)
    }
}

internal fun SessionEditorViewModel.convertToSupersetWithNext(session: Session, targetExerciseId: String): Session {
    val allExercises = session.allExercises()
    val targetIdx = allExercises.indexOfFirst { it.id == targetExerciseId }
    if (targetIdx < 0) return session
    val target = allExercises.getOrNull(targetIdx) ?: return session
    val partner = allExercises.getOrNull(targetIdx + 1) ?: return session
    if (partner.id == targetExerciseId) return session
    if (target.supersetGroupRefOrLegacyId() != null || partner.supersetGroupRefOrLegacyId() != null) return session
    val defaults = currentUiState.ruleDefaults
    val groupId = "superset_group_${System.currentTimeMillis()}"
    val anchorPartId = session.parts.firstOrNull { part ->
        part.exercises.any { it.id == targetExerciseId }
    }?.id
    return SupersetRules.createSuperset(
        session = session,
        groupId = groupId,
        exerciseIds = listOf(targetExerciseId, partner.id),
        restBetweenExercises = defaults.supersetBetweenRestSeconds,
        restAfterSuperset = defaults.supersetRoundRestSeconds,
        rounds = null,
        anchorPartId = anchorPartId,
        anchorExerciseId = targetExerciseId,
    )
}

internal fun SessionEditorViewModel.reduceSetsForMuscle(session: Session, muscle: String): Session {
    var applied = false
    fun updateExercise(exercise: Exercise): Exercise {
        if (applied) return exercise
        val muscles = ExerciseMuscleResolver.effectiveMuscles(exercise, exerciseIndex)
        val normalized = muscles.any { m ->
            VolumeCalculator.normalizeCanonicalMuscleGroup(m.muscle, m.emphasis) == muscle
        }
        if (!normalized) return exercise
        if (exercise.sets.size <= 1) return exercise
        applied = true
        return exercise.copy(sets = exercise.sets.dropLast(1))
    }
    val updatedParts = session.parts.map { part ->
        part.copy(exercises = part.exercises.map(::updateExercise))
    }
    val updatedExercises = session.exercises.map(::updateExercise)
    return session.copy(parts = updatedParts, exercises = updatedExercises)
}

internal fun SessionEditorViewModel.lowerRpeOnAllExercises(session: Session): Session {
    return session.transformExercises { exercise ->
        exercise.copy(sets = exercise.sets.map { set ->
            when (set.intensityMode) {
                IntensityMode.FAILURE -> set.copy(
                    intensityMode = IntensityMode.RIR,
                    targetRIR = 1,
                    isFailure = false,
                )
                IntensityMode.RPE -> {
                    val currentRpe = set.targetRPE ?: 8.0
                    set.copy(targetRPE = maxOf(6.0, currentRpe - 0.5))
                }
                IntensityMode.RIR -> {
                    val currentRir = set.targetRIR ?: 2
                    set.copy(targetRIR = (currentRir + 1).coerceAtMost(5))
                }
                else -> set
            }
        })
    }
}

internal fun SessionEditorViewModel.convertFailureToRir(session: Session): Session {
    return session.transformExercises { exercise ->
        exercise.copy(sets = exercise.sets.map { set ->
            if (set.isFailure || set.intensityMode == IntensityMode.FAILURE) {
                set.copy(
                    intensityMode = IntensityMode.RIR,
                    targetRIR = 1,
                    targetRPE = null,
                    isFailure = false,
                )
            } else {
                set
            }
        })
    }
}

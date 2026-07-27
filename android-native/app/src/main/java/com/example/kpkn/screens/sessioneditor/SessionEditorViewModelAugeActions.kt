package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.CompetitionRepository
import com.example.kpkn.domain.auge.AugeClassifiers
import com.example.kpkn.domain.exercises.ExerciseMuscleResolver
import com.example.kpkn.domain.exercises.normalizedIdentityFields
import com.example.kpkn.domain.exercises.resolvedCanonicalExerciseId
import com.example.kpkn.domain.sessionassistant.AssistantActionType
import com.example.kpkn.domain.sessionassistant.AssistantDetailAction
import com.example.kpkn.domain.sessionassistant.AssistantSuggestionDetail
import com.example.kpkn.domain.sessionassistant.SessionAssistantEngine
import com.example.kpkn.domain.sessionassistant.SessionAssistantInput
import com.example.kpkn.domain.training.VolumeCalculator
import com.example.kpkn.domain.workout.SupersetRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID
import kotlin.math.roundToInt

fun SessionEditorViewModel.applyAugeCorrection(
    alertId: String,
    overrideType: SessionEditorAugeCorrectionType? = null,
) {
    val alert = (currentUiState.augeSummary.alerts + currentUiState.augeSummary.suggestions)
        .firstOrNull { it.id == alertId } ?: return

    val correctionType = overrideType ?: alert.correctionType

    when (correctionType) {
        SessionEditorAugeCorrectionType.REDUCE_SERIES -> reduceSeriesForAugeAlert(alert)
        SessionEditorAugeCorrectionType.REDUCE_RPE -> reduceRpeForAugeAlert(alert)
        SessionEditorAugeCorrectionType.REDUCE_VOLUME_RPE -> reduceVolumeAndRpeForAugeAlert(alert)
        SessionEditorAugeCorrectionType.ADD_SERIES -> addSeriesForAugeAlert(alert)
        null -> Unit
    }
}

internal fun SessionEditorViewModel.reduceSeriesForAugeAlert(alert: SessionEditorAugeAlert) {
    val targetExerciseIds = orderedExerciseIdsForAlert(alert)
    updateSession { session ->
        session.transformExercises { exercise ->
            if (exercise.id !in targetExerciseIds || exercise.sets.size <= 1) return@transformExercises exercise
            exercise.copy(sets = exercise.sets.dropLast(1))
        }
    }
}

internal fun SessionEditorViewModel.reduceRpeForAugeAlert(alert: SessionEditorAugeAlert) {
    val targetExerciseIds = orderedExerciseIdsForAlert(alert)
    updateSession { session ->
        session.transformExercises { exercise ->
            if (exercise.id !in targetExerciseIds) return@transformExercises exercise
            exercise.copy(sets = exercise.sets.map { it.lowerAugeIntensity() })
        }
    }
}

internal fun SessionEditorViewModel.reduceVolumeAndRpeForAugeAlert(alert: SessionEditorAugeAlert) {
    val targetExerciseIds = orderedExerciseIdsForAlert(alert)
    updateSession { session ->
        session.transformExercises { exercise ->
            if (exercise.id !in targetExerciseIds) return@transformExercises exercise
            val trimmedSets = when {
                exercise.sets.size > 3 -> exercise.sets.take(3)
                exercise.sets.size > 1 -> exercise.sets.dropLast(1)
                else -> exercise.sets
            }
            exercise.copy(sets = trimmedSets.map { it.lowerAugeIntensity(capRpe = 7.0) })
        }
    }
}

internal fun SessionEditorViewModel.addSeriesForAugeAlert(alert: SessionEditorAugeAlert) {
    val targetExerciseIds = orderedExerciseIdsForAlert(alert)
    updateSession { session ->
        session.transformExercises { exercise ->
            if (exercise.id !in targetExerciseIds) return@transformExercises exercise
            val template = exercise.sets.lastOrNull()
            val nextSet = template?.let { createNextSetTemplate(exercise, it) } ?: ExerciseSet(
                id = UUID.randomUUID().toString(),
                targetReps = 8,
            )
            exercise.copy(sets = exercise.sets + nextSet)
        }
    }
}

fun SessionEditorViewModel.addGhostExercise(cardId: String) {
    val card = currentUiState.ghostExerciseCards.find { it.cardId == cardId } ?: return
    val info = exerciseIndex[card.exerciseDbId.lowercase()] ?: return
    val newExercise = Exercise(
        id = UUID.randomUUID().toString(),
        name = card.name,
        exerciseDbId = card.exerciseDbId,
        selectedMovementPattern = info.movementPattern,
        selectedExecutionOption = info.executionOptions?.firstOrNull(),
        sets = (1..card.sets).map {
            ExerciseSet(
                id = UUID.randomUUID().toString(),
                targetReps = card.reps,
                targetRPE = card.rpe,
                intensityMode = IntensityMode.RPE,
            )
        },
        restTime = card.restSeconds,
    )
    updateSession { session ->
        session.copy(
            exercises = session.exercises + newExercise,
        )
    }
}

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
                        reps = (set.targetReps ?: 8) / 2,
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
    val partner = allExercises.getOrNull(targetIdx + 1) ?: return session
    if (partner.id == targetExerciseId) return session
    val groupId = "superset_group_${System.currentTimeMillis()}"
    return session.copy(
        supersetGroups = session.supersetGroups + com.example.kpkn.data.models.SupersetGroup(
            id = groupId,
            exerciseOrder = listOf(targetExerciseId, partner.id),
        ),
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

internal fun SessionEditorViewModel.orderedExerciseIdsForAlert(alert: SessionEditorAugeAlert): Set<String> {
    val currentSession = currentUiState.session ?: return emptySet()
    val rankedExerciseIds = currentUiState.augeSummary.topExercises.map { it.exerciseId }
    return when {
        alert.exerciseId != null -> linkedSetOf(alert.exerciseId)
        alert.muscle != null -> {
            val matching = currentSession.allExercises()
                .filter { exerciseMatchesPrimaryMuscle(it, alert.muscle) }
                .map { it.id }
                .toSet()
            linkedSetOf<String>().apply {
                rankedExerciseIds.filterTo(this) { it in matching }
                matching.filterTo(this) { it !in this }
            }
        }
        else -> linkedSetOf<String>().apply {
            addAll(rankedExerciseIds)
            addAll(currentSession.allExercises().map { it.id })
        }
    }
}

internal fun SessionEditorViewModel.exerciseMatchesPrimaryMuscle(exercise: Exercise, muscle: String?): Boolean {
    if (muscle.isNullOrBlank()) return false
    val muscles = ExerciseMuscleResolver.effectiveMuscles(exercise, exerciseIndex)
    return muscles.any { it.muscle.equals(muscle, ignoreCase = true) && it.role == MuscleRole.PRIMARY }
}


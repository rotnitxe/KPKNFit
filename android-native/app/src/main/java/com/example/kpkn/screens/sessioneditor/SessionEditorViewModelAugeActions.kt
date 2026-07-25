package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.CompetitionRepository
import com.example.kpkn.domain.auge.AugeClassifiers
import com.example.kpkn.domain.exercises.ExerciseMuscleResolver
import com.example.kpkn.domain.exercises.normalizedIdentityFields
import com.example.kpkn.domain.exercises.resolvedCanonicalExerciseId
import com.example.kpkn.domain.sessionassistant.SessionAssistantEngine
import com.example.kpkn.domain.sessionassistant.SessionAssistantInput
import com.example.kpkn.domain.training.VolumeCalculator
import com.example.kpkn.domain.workout.SupersetRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

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

fun SessionEditorViewModel.applyAssistantSuggestion(suggestionId: String) {
    val suggestion = currentUiState.assistantReport?.ajustes
        ?.firstOrNull { it.id == suggestionId } ?: return

    when (suggestion.type) {
        com.example.kpkn.domain.sessionassistant.AssistantActionType.REDUCE_SET -> {
            val muscle = suggestion.muscle
            if (muscle != null) {
                updateSession { session ->
                    reduceSetsForMuscle(session, muscle)
                }
            }
        }
        com.example.kpkn.domain.sessionassistant.AssistantActionType.LOWER_RPE -> {
            updateSession { session ->
                lowerRpeOnAllExercises(session)
            }
        }
        com.example.kpkn.domain.sessionassistant.AssistantActionType.REMOVE_FAILURE -> {
            updateSession { session ->
                convertFailureToRir(session)
            }
        }
        com.example.kpkn.domain.sessionassistant.AssistantActionType.REDUCE_REST_TIME -> {
            updateSession { session ->
                session.transformExercises { exercise ->
                    val currentRest = exercise.restTime ?: 90
                    exercise.copy(restTime = maxOf(30, currentRest - 15))
                }
            }
        }
        com.example.kpkn.domain.sessionassistant.AssistantActionType.CONVERT_TO_DROPSET -> {
            val exerciseId = suggestion.exerciseId ?: return
            updateSession { session ->
                session.transformExercises { exercise ->
                    if (exercise.id != exerciseId) return@transformExercises exercise
                    val updatedSets = exercise.sets.map { set ->
                        if (set.isDropSet) set else set.copy(isDropSet = true, dropSets = listOf(com.example.kpkn.data.models.DropSetData(weight = set.weight ?: 0.0, reps = (set.targetReps ?: 8) / 2)))
                    }
                    exercise.copy(sets = updatedSets)
                }
            }
        }
        com.example.kpkn.domain.sessionassistant.AssistantActionType.CONVERT_TO_SUPERSET -> {
            val targetExerciseId = suggestion.exerciseId ?: return
            val session = currentUiState.session ?: return
            val allExercises = session.allExercises()
            val targetIdx = allExercises.indexOfFirst { it.id == targetExerciseId }
            if (targetIdx < 0) return
            val partner = allExercises.getOrNull(targetIdx + 1) ?: return
            if (partner.id == targetExerciseId) return
            val groupId = "superset_group_${System.currentTimeMillis()}"
            updateSession { s ->
                s.copy(
                    supersetGroups = s.supersetGroups + com.example.kpkn.data.models.SupersetGroup(
                        id = groupId,
                        exerciseOrder = listOf(targetExerciseId, partner.id),
                    )
                )
            }
        }
        else -> Unit
    }
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


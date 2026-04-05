package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.DropSetData
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.PlanDeviation
import com.example.kpkn.data.models.PlanDeviationType
import com.example.kpkn.data.models.PostSessionFeedback
import com.example.kpkn.data.models.MuscleFeedbackEntry
import com.example.kpkn.data.models.RestPauseData
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.auge.getAugeMuscleDisplayId
import com.example.kpkn.domain.training.VolumeCalculator
import java.time.LocalDate

data class SetAdvancedFeedback(
    val rir: Int? = null,
    val isFailure: Boolean = false,
    val isPartial: Boolean = false,
    val partialReps: Int? = null,
    val dropSets: List<DropSetEntry> = emptyList(),
    val restPauseReps: List<Int> = emptyList(),
    val isWarmup: Boolean = false,
)

data class DropSetEntry(
    val weight: Double,
    val reps: Int,
)

data class PostExerciseFeedback(
    val exerciseId: String,
    val exerciseName: String,
    val neuralFatigue: Int,
    val technicalQuality: Int,
    val discomforts: List<String>,
)

data class SessionClosingFeedback(
    val overallFatigue: Int,
    val neuralDrain: Int,
    val muscularDrain: Int,
    val spinalDrain: Int,
    val discomforts: List<String>,
    val clarityRating: Int = 5,           // 1–10: mental clarity / freshness at end
    val environmentTags: List<String> = emptyList(), // e.g. "buen sueño", "estresado"
)

data class ExerciseHistoryEntry(
    val date: String,
    val sets: List<CompletedSet>,
    val e1rm: Double?,
    val tag: String? = null,  // tag usado en esa sesión para ese ejercicio
)

data class WeightSuggestion(
    val suggestedWeight: Double,
    val reason: String,
)

object WorkoutPlanDeviationSupport {
    fun detect(
        exerciseId: String,
        exerciseName: String,
        setIdx: Int,
        plannedSet: ExerciseSet,
        actualWeight: Double,
        actualReps: Int,
        advanced: SetAdvancedFeedback,
        suggestedWeight: Double?,
    ): List<PlanDeviation> {
        val deviations = mutableListOf<PlanDeviation>()

        val targetWeight = suggestedWeight ?: 0.0
        if (targetWeight > 0 && actualWeight > 0) {
            val ratio = actualWeight / targetWeight
            if (ratio > 1.15) deviations.add(PlanDeviation(exerciseId, exerciseName, setIdx, PlanDeviationType.WEIGHT_HIGH, "+${"%.0f".format((ratio - 1) * 100)}% del sugerido"))
            else if (ratio < 0.85) deviations.add(PlanDeviation(exerciseId, exerciseName, setIdx, PlanDeviationType.WEIGHT_LOW, "-${"%.0f".format((1 - ratio) * 100)}% del sugerido"))
        }

        val targetReps = plannedSet.targetReps
        if (targetReps != null && targetReps > 0 && actualReps > 0) {
            if (actualReps > targetReps + 3) deviations.add(PlanDeviation(exerciseId, exerciseName, setIdx, PlanDeviationType.REPS_HIGH, "$actualReps vs $targetReps objetivo"))
            else if (actualReps < targetReps - 3) deviations.add(PlanDeviation(exerciseId, exerciseName, setIdx, PlanDeviationType.REPS_LOW, "$actualReps vs $targetReps objetivo"))
        }

        if (advanced.isFailure && !plannedSet.isFailure) deviations.add(PlanDeviation(exerciseId, exerciseName, setIdx, PlanDeviationType.UNPLANNED_FAILURE, "Fallo no programado"))
        if (advanced.dropSets.isNotEmpty() && plannedSet.dropSets.isEmpty()) deviations.add(PlanDeviation(exerciseId, exerciseName, setIdx, PlanDeviationType.UNPLANNED_DROPSET, "Dropset no programado"))
        if (advanced.restPauseReps.isNotEmpty() && plannedSet.restPauses.isEmpty()) deviations.add(PlanDeviation(exerciseId, exerciseName, setIdx, PlanDeviationType.UNPLANNED_REST_PAUSE, "Rest-pause no programado"))

        return deviations
    }
}

fun applyAdvancedFeedback(
    base: CompletedSet,
    advanced: SetAdvancedFeedback,
): CompletedSet {
    return base.copy(
        rir = advanced.rir,
        isFailure = advanced.isFailure,
        isPartial = advanced.isPartial,
        partialReps = advanced.partialReps,
        dropSets = advanced.dropSets.map { DropSetData(weight = it.weight, reps = it.reps) },
        restPauses = advanced.restPauseReps.map { RestPauseData(restTime = 15, reps = it) },
        isWarmup = advanced.isWarmup,
    )
}

fun mapWorkoutToPostSessionFeedback(
    log: WorkoutLog,
    postExerciseFeedback: List<PostExerciseFeedback>,
    exerciseDbById: Map<String, ExerciseMuscleInfo>,
): PostSessionFeedback {
    val grouped = postExerciseFeedback.groupBy { feedback ->
        val exercise = log.completedExercises.find { it.exerciseId == feedback.exerciseId }
        val dbInfo = exerciseDbById[exercise?.exerciseDbId ?: exercise?.exerciseId]
        val primary = dbInfo?.involvedMuscles
            ?.firstOrNull { it.role == MuscleRole.PRIMARY }
        if (primary != null) {
            val canonical = VolumeCalculator.normalizeCanonicalMuscleGroup(primary.muscle, primary.emphasis)
            getAugeMuscleDisplayId(canonical, primary.emphasis)
        } else feedback.exerciseName
    }

    val feedbackByMuscle = grouped.mapValues { (_, list) ->
        val avgNeural = if (list.isEmpty()) 7 else list.map { it.neuralFatigue }.average().toInt().coerceIn(1, 10)
        val avgTech = if (list.isEmpty()) 8 else list.map { it.technicalQuality }.average().toInt().coerceIn(1, 10)
        val hasJointPain = list.any { it.discomforts.any { d -> d.contains("dolor", ignoreCase = true) || d.contains("molestia", ignoreCase = true) } }
        MuscleFeedbackEntry(
            doms = (11 - avgTech).coerceIn(1, 5),
            jointPain = hasJointPain,
            strengthCapacity = avgNeural,
            notes = list.flatMap { it.discomforts }.distinct().joinToString().takeIf { it.isNotBlank() } ?: "",
        )
    }

    val cnsRecovery = if (postExerciseFeedback.isEmpty()) {
        7
    } else {
        postExerciseFeedback.map { it.neuralFatigue }.average().toInt().coerceIn(1, 10)
    }

    return PostSessionFeedback(
        logId = log.id,
        date = LocalDate.now().toString(),
        cnsRecovery = cnsRecovery,
        muscleFeedback = feedbackByMuscle,
    )
}

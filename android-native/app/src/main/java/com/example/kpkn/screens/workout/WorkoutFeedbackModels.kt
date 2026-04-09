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
import com.example.kpkn.data.models.HistoryColorV2
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.auge.AugeFatigueEngine
import com.example.kpkn.domain.auge.getAugeMuscleDisplayId
import com.example.kpkn.domain.training.VolumeCalculator
import java.time.LocalDate

data class SetAdvancedFeedback(
    val rir: Int? = null,
    val reachedFailure: Boolean = false,
    val isFailedSet: Boolean = false,
    val failureReason: String? = null,
    val isPartial: Boolean = false,
    val partialReps: Int? = null,
    val dropSets: List<DropSetEntry> = emptyList(),
    val restPauses: List<RestPauseEntry> = emptyList(),
    val isWarmup: Boolean = false,
    val actualIntensityMode: IntensityMode? = null,
    val actualIntensityValue: Double? = null,
    val timerElapsedSeconds: Int? = null,
    val timerTargetSeconds: Int? = null,
)

data class DropSetEntry(
    val weight: Double,
    val reps: Int,
)

data class RestPauseEntry(
    val reps: Int,
    val restSeconds: Int,
)

data class PostExerciseFeedback(
    val exerciseId: String,
    val exerciseDbId: String? = null,
    val exerciseName: String,
    val technicalQuality: Int,
    val discomfortIds: List<String> = emptyList(),
)

data class SessionClosingFeedback(
    val overallFatigue: Int,
    val systemAdjustment: Int,
    val muscularAdjustment: Int,
    val structureAdjustment: Int,
    val discomforts: List<String>,
    val clarityRating: Int = 5,           // 1–10: mental clarity / freshness at end
    val environmentTags: List<String> = emptyList(), // e.g. "buen sueño", "estresado"
    val finalNeuralBattery: Int? = null,
    val finalSpinalBattery: Int? = null,
    val finalMuscleBatteries: Map<String, Int> = emptyMap(),
    val additionalDiscomfortNote: String? = null,
)

data class ExerciseHistoryEntry(
    val date: String,
    val sets: List<CompletedSet>,
    val e1rm: Double?,
    val tag: String? = null,  // tag usado en esa sesión para ese ejercicio
    val latestHistoryColor: HistoryColorV2? = null,
    val latestMetricType: String? = null,
    val latestMetricValue: Double? = null,
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

        if (advanced.reachedFailure && !plannedSet.isFailure) deviations.add(PlanDeviation(exerciseId, exerciseName, setIdx, PlanDeviationType.UNPLANNED_FAILURE, "Fallo no programado"))
        if (advanced.dropSets.isNotEmpty() && plannedSet.dropSets.isEmpty()) deviations.add(PlanDeviation(exerciseId, exerciseName, setIdx, PlanDeviationType.UNPLANNED_DROPSET, "Dropset no programado"))
        if (advanced.restPauses.isNotEmpty() && plannedSet.restPauses.isEmpty()) deviations.add(PlanDeviation(exerciseId, exerciseName, setIdx, PlanDeviationType.UNPLANNED_REST_PAUSE, "Rest-pause no programado"))

        return deviations
    }
}

fun applyAdvancedFeedback(
    base: CompletedSet,
    advanced: SetAdvancedFeedback,
): CompletedSet {
    return base.copy(
        rir = advanced.rir,
        isFailure = advanced.reachedFailure,
        isFailedSet = advanced.isFailedSet,
        failureReason = advanced.failureReason,
        isPartial = advanced.isPartial,
        partialReps = advanced.partialReps,
        dropSets = advanced.dropSets.map { DropSetData(weight = it.weight, reps = it.reps) },
        restPauses = advanced.restPauses.map { RestPauseData(restTime = it.restSeconds, reps = it.reps) },
        isWarmup = advanced.isWarmup,
        actualIntensityMode = advanced.actualIntensityMode,
        actualIntensityValue = advanced.actualIntensityValue,
    )
}

fun calculateUnifiedSessionEffortSignal(
    sets: List<CompletedSet>,
): Double {
    val effectiveSets = sets.filter { set ->
        !set.isWarmup && AugeFatigueEngine.isSetEffective(set)
    }
    if (effectiveSets.isEmpty()) return 7.0

    return effectiveSets
        .map { set ->
            var signal = AugeFatigueEngine.getEffectiveRPE(set)
            if (set.isFailure) signal += 0.6
            if (set.dropSets.isNotEmpty()) signal += 0.4
            if (set.restPauses.isNotEmpty()) signal += 0.5
            signal.coerceIn(1.0, 12.0)
        }
        .average()
        .coerceIn(1.0, 12.0)
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
        val avgDemand = 7
        val avgTech = if (list.isEmpty()) 8 else list.map { it.technicalQuality }.average().toInt().coerceIn(1, 10)
        val hasJointPain = list.any { it.discomfortIds.any { d -> d != "none" } }
        MuscleFeedbackEntry(
            doms = (11 - avgTech).coerceIn(1, 5),
            jointPain = hasJointPain,
            strengthCapacity = avgDemand,
            notes = list.flatMap { it.discomfortIds }.filter { it != "none" }.distinct().joinToString().takeIf { it.isNotBlank() } ?: "",
        )
    }

    val cnsRecovery = 7

    return PostSessionFeedback(
        logId = log.id,
        date = LocalDate.now().toString(),
        cnsRecovery = cnsRecovery,
        muscleFeedback = feedbackByMuscle,
    )
}

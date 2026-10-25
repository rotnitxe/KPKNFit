package com.example.kpkn.screens.workout

import com.example.kpkn.data.exercises.resolveCatalogExerciseInfoInIndex
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.DropSetData
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.PlanDeviation
import com.example.kpkn.data.models.PlanDeviationType
import com.example.kpkn.data.models.PostExerciseFeedback
import com.example.kpkn.data.models.PostSessionFeedback
import com.example.kpkn.data.models.MuscleFeedbackEntry
import com.example.kpkn.data.models.RestPauseData
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.HistoryColorV2
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.RepRange
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.models.effectiveRepRange
import com.example.kpkn.data.models.performedPurpose
import com.example.kpkn.domain.training.VolumeCalculator
import java.time.LocalDate

data class SetAdvancedFeedback(
    val rir: Int? = null,
    val reachedFailure: Boolean = false,
    val isFailedSet: Boolean = false,
    val failureReason: String? = null,
    val executionError: Boolean = false,
    val isPartial: Boolean = false,
    val partialReps: Int? = null,
    val dropSets: List<DropSetData> = emptyList(),
    val restPauses: List<RestPauseData> = emptyList(),
    val skipped: Boolean = false,
    val superSetWithExerciseId: String? = null,
    val isWarmup: Boolean = false,
    val actualIntensityMode: IntensityMode? = null,
    val actualIntensityValue: Double? = null,
    /** Nullable live override: null follows the planned set, false disables planned AMRAP. */
    val amrapOverride: Boolean? = null,
    val amrapMinimumReps: Int? = null,
    /** Distinguishes an explicit live "no minimum" from following the prescription. */
    val amrapMinimumOverridden: Boolean = false,
    /** False si el atleta no movió el selector de reserva (llegó relleno con lo planificado): no cuenta como «reserva cumplida». */
    val intensityAdjusted: Boolean = true,
    val timerElapsedSeconds: Int? = null,
    val timerTargetSeconds: Int? = null,
    val rom: Int? = null,
    val assistedReps: Int? = null,
    val notes: String? = null,
)

internal fun resolveAmrapActive(
    plannedSet: ExerciseSet?,
    requestedOverride: Boolean,
    explicitOverride: Boolean? = null,
    recordedSet: CompletedSet? = null,
): Boolean {
    val override = explicitOverride ?: requestedOverride.takeIf { it }
    return override ?: recordedSet?.performedPurpose()?.isAmrap ?: (plannedSet?.purpose?.isAmrap == true)
}

internal fun resolveAmrapMinimumReps(
    plannedSet: ExerciseSet?,
    draft: WorkoutSetDraft? = null,
    recordedSet: CompletedSet? = null,
): Int? = when {
    draft?.amrapMinimumOverridden == true || draft?.amrapMinimumReps != null -> draft?.amrapMinimumReps
    recordedSet?.purpose?.isAmrap == true || recordedSet?.recordedPayloadV3?.purpose?.isAmrap == true -> recordedSet.amrapMinimumReps
    recordedSet?.amrapMinimumReps != null -> recordedSet.amrapMinimumReps
    else -> plannedSet?.amrapMinimumReps
}

internal data class RepRangeEvaluation(
    val delta: Double,
    val debt: Double,
    val isInRange: Boolean,
)

/**
 * Evaluates the performed value against the semantic edges of a rep range.
 * The upper anchor remains useful for load suggestions, but it is not a
 * deficit target when the performed value is between min and max.
 */
internal fun evaluateRepRange(
    actual: Double,
    range: RepRange?,
    amrapActive: Boolean = false,
    amrapMinimum: Int? = null,
): RepRangeEvaluation? {
    if (amrapActive) {
        val minimum = amrapMinimum?.takeIf { it > 0 } ?: return null
        return if (actual < minimum) {
            RepRangeEvaluation(
                delta = actual - minimum,
                debt = minimum - actual,
                isInRange = false,
            )
        } else {
            RepRangeEvaluation(delta = 0.0, debt = 0.0, isInRange = true)
        }
    }
    val target = range ?: return null
    return when {
        actual < target.min -> RepRangeEvaluation(actual - target.min, target.min - actual, false)
        actual > target.max -> RepRangeEvaluation(actual - target.max, 0.0, false)
        else -> RepRangeEvaluation(0.0, 0.0, true)
    }
}

sealed interface PostExerciseFeedbackTarget {
    data class Single(val exerciseId: String) : PostExerciseFeedbackTarget
    data class SupersetGroup(
        val groupId: String,
        val exerciseIds: List<String>,
    ) : PostExerciseFeedbackTarget
}

internal fun backfillCompletedSetIntensityFromPostExerciseFeedback(
    completedSets: Map<String, CompletedSet>,
    feedback: PostExerciseFeedback,
): Map<String, CompletedSet> {
    val perceived = feedback.perceivedIntensityRpe?.coerceIn(1.0, 10.0)
    if (perceived == null && !feedback.perceivedFailure) return completedSets
    val mode = if (feedback.perceivedFailure) IntensityMode.FAILURE else IntensityMode.RPE
    val value = if (feedback.perceivedFailure) 10.0 else perceived
    return completedSets.mapValues { (key, set) ->
        val belongsToExercise = key.startsWith("${feedback.exerciseId}_")
        val hasRecordedIntensity = set.actualIntensityValue != null ||
            set.actualIntensityMode != null ||
            set.rpe != null ||
            set.rir != null ||
            set.isFailure
        if (!belongsToExercise || hasRecordedIntensity) {
            set
        } else {
            set.copy(
                rpe = if (feedback.perceivedFailure) null else value,
                isFailure = feedback.perceivedFailure,
                actualIntensityMode = mode,
                actualIntensityValue = value,
            )
        }
    }
}

internal fun backfillCompletedSetIntensityFromPostExerciseFeedbacks(
    completedSets: Map<String, CompletedSet>,
    feedbacks: List<PostExerciseFeedback>,
): Map<String, CompletedSet> = feedbacks.fold(completedSets) { current, feedback ->
    backfillCompletedSetIntensityFromPostExerciseFeedback(current, feedback)
}

data class SessionClosingFeedback(
    val discomforts: List<String>,
    val clarityRating: Int? = null,
    val environmentTags: List<String> = emptyList(),
    val finishOperationId: String? = null,
    val completionInstantIso: String? = null,
    val completedSetInputHash: String? = null,
    val additionalDiscomfortNote: String? = null,
    val stillPresentDiscomfortIds: List<String> = emptyList(),
    val sessionEffortRpe: Double? = null,
)

data class ExerciseHistoryEntry(
    val date: String,
    val sets: List<CompletedSet>,
    val e1rm: Double?,
    val tag: String? = null,  // tag usado en esa sesión para ese ejercicio
    val notes: String? = null,
    val latestHistoryColor: HistoryColorV2? = null,
    val latestMetricType: String? = null,
    val latestMetricValue: Double? = null,
)

data class WeightSuggestion(
    val suggestedWeight: Double,
    val reason: String,
    val suggestedLoadMode: LoadModeV2? = null,
    val isCalibrationAssignment: Boolean = false,
)

data class WorkoutLoadSuggestionUi(
    val suggestedWeight: Double,
    val originalWeight: Double,
    val isRecalculated: Boolean = false,
    val reason: String,
    val source: WorkoutLoadSuggestionSource = WorkoutLoadSuggestionSource.PROGRAM,
    val suggestedLoadMode: LoadModeV2? = null,
    val isCalibrationAssignment: Boolean = false,
)

enum class WorkoutLoadSuggestionSource {
    PROGRAM,
    HISTORY,
    SESSION_ERM,
    MANUAL_BASE,
}

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
        amrapMinimumReps: Int? = if (advanced.amrapMinimumOverridden || advanced.amrapMinimumReps != null) {
            advanced.amrapMinimumReps
        } else plannedSet.amrapMinimumReps,
    ): List<PlanDeviation> {
        val deviations = mutableListOf<PlanDeviation>()

        val targetWeight = suggestedWeight ?: 0.0
        if (targetWeight > 0 && actualWeight > 0) {
            val ratio = actualWeight / targetWeight
            if (ratio > 1.15) deviations.add(PlanDeviation(exerciseId, exerciseName, setIdx, PlanDeviationType.WEIGHT_HIGH, "+${"%.0f".format((ratio - 1) * 100)}% del sugerido"))
            else if (ratio < 0.85) deviations.add(PlanDeviation(exerciseId, exerciseName, setIdx, PlanDeviationType.WEIGHT_LOW, "-${"%.0f".format((1 - ratio) * 100)}% del sugerido"))
        }

        val targetRange = plannedSet.effectiveRepRange()
        val amrapActive = resolveAmrapActive(
            plannedSet = plannedSet,
            requestedOverride = false,
            explicitOverride = advanced.amrapOverride,
        )
        if (amrapActive || actualReps > 0) {
            if (amrapActive) {
                val minimum = amrapMinimumReps?.takeIf { it > 0 }
                if (minimum != null && actualReps < minimum) {
                    deviations.add(
                        PlanDeviation(
                            exerciseId,
                            exerciseName,
                            setIdx,
                            PlanDeviationType.AMRAP_BELOW_MINIMUM,
                            "$actualReps vs mínimo AMRAP $minimum",
                        ),
                    )
                }
            } else if (targetRange != null && actualReps > targetRange.max) {
                deviations.add(PlanDeviation(exerciseId, exerciseName, setIdx, PlanDeviationType.REPS_HIGH, "$actualReps vs ${targetRange.format()} objetivo"))
            } else if (targetRange != null && actualReps < targetRange.min) {
                deviations.add(PlanDeviation(exerciseId, exerciseName, setIdx, PlanDeviationType.REPS_LOW, "$actualReps vs ${targetRange.format()} objetivo"))
            }
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
        isFailedSet = advanced.isFailedSet || advanced.executionError,
        failureReason = advanced.failureReason ?: if (advanced.executionError) "execution_error" else null,
        isPartial = advanced.isPartial,
        partialReps = advanced.partialReps,
        dropSets = advanced.dropSets,
        restPauses = advanced.restPauses,
        skipped = advanced.skipped,
        superSetWithExerciseId = advanced.superSetWithExerciseId,
        isWarmup = advanced.isWarmup,
        // A set without planned intensity must remain unlabelled in the live
        // record. Only explicit feedback may add an actual mode/value later.
        actualIntensityMode = advanced.actualIntensityMode ?: base.actualIntensityMode,
        actualIntensityValue = advanced.actualIntensityValue ?: base.actualIntensityValue,
        rom = advanced.rom,
        assistedReps = advanced.assistedReps,
        notes = advanced.notes ?: base.notes,
    )
}



data class SetAutoRegulation(
    val exerciseId: String,
    val nextSetIdx: Int,
    val adjustmentFactor: Double,
    val adjustedWeight: Double,
    val reason: String,
)


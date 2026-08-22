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
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.models.effectiveRepRange
import com.example.kpkn.domain.auge.AugeFatigueEngine
import com.example.kpkn.domain.auge.getAugeMusclePillarId
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
    val timerElapsedSeconds: Int? = null,
    val timerTargetSeconds: Int? = null,
    val rom: Int? = null,
    val assistedReps: Int? = null,
)

internal fun resolveAmrapActive(
    plannedSet: ExerciseSet?,
    requestedOverride: Boolean,
    explicitOverride: Boolean? = null,
): Boolean {
    val override = explicitOverride ?: requestedOverride.takeIf { it }
    return override ?: (plannedSet?.isAmrap == true || plannedSet?.intensityMode == IntensityMode.AMRAP)
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
    val overallFatigue: Int,
    val systemAdjustment: Int,
    val muscularAdjustment: Int,
    val structureAdjustment: Int,
    val discomforts: List<String>,
    val clarityRating: Int = 5,           // 1–10: mental clarity / freshness at end
    val environmentTags: List<String> = emptyList(), // e.g. "buen sueño", "estresado"
    val finalNeuralBattery: Int? = null,
    val finalSpinalBattery: Int? = null,
    val finalMuscularBattery: Int? = null,
    val finalMuscleBatteries: Map<String, Int> = emptyMap(),
    /** True when the user dragged/edited the neural ring vs engine seed. */
    val neuralEdited: Boolean = false,
    /** True when the user dragged/edited the spinal ring vs engine seed. */
    val spinalEdited: Boolean = false,
    /** True when the user edited any per-muscle slider vs engine seed. */
    val musclesEdited: Boolean = false,
    val additionalDiscomfortNote: String? = null,
    val stillPresentDiscomfortIds: List<String> = emptyList(),
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
)

data class WorkoutLoadSuggestionUi(
    val suggestedWeight: Double,
    val originalWeight: Double,
    val isRecalculated: Boolean = false,
    val reason: String,
    val source: WorkoutLoadSuggestionSource = WorkoutLoadSuggestionSource.PROGRAM,
    val suggestedLoadMode: LoadModeV2? = null,
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
        amrapMinimumReps: Int? = advanced.amrapMinimumReps,
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
        if (targetRange != null && actualReps > 0) {
            if (amrapActive) {
                val minimum = amrapMinimumReps ?: targetRange.min
                if (actualReps < minimum) {
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
            } else if (actualReps > targetRange.max + 3) {
                deviations.add(PlanDeviation(exerciseId, exerciseName, setIdx, PlanDeviationType.REPS_HIGH, "$actualReps vs ${targetRange.format()} objetivo"))
            } else if (actualReps < targetRange.min - 3) {
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
    )
}

fun calculateUnifiedSessionEffortSignal(
    sets: List<CompletedSet>,
): Double {
    val effectiveSets = sets.filter { set ->
        !set.isWarmup && AugeFatigueEngine.isSetEffective(set)
    }
    if (effectiveSets.isEmpty()) return 7.0

    // getEffectiveRPE already includes technique bonuses — do not double-count
    return effectiveSets
        .map { set -> AugeFatigueEngine.getEffectiveRPE(set).coerceIn(1.0, 12.0) }
        .average()
        .coerceIn(1.0, 12.0)
}

/** Maps a 1–10 technical quality score to the 1–5 scale expected by calculateTechniquePenalty. */
fun technicalQuality10ToPenaltyScale(technicalQuality: Int): Int {
    val q = technicalQuality.coerceIn(1, 10)
    return (1 + ((q - 1) * 4) / 9).coerceIn(1, 5)
}

@Suppress("unused")
fun mapWorkoutToPostSessionFeedback(
    log: WorkoutLog,
    postExerciseFeedback: List<PostExerciseFeedback>,
    exerciseDbById: Map<String, ExerciseMuscleInfo>,
): PostSessionFeedback {
    val grouped = postExerciseFeedback.groupBy { feedback ->
        val exercise = log.completedExercises.find { it.exerciseId == feedback.exerciseId }
        val dbInfo = exercise?.let {
            resolveCatalogExerciseInfoInIndex(
                index = exerciseDbById,
                catalogConfigurationId = it.catalogConfigurationId,
                exerciseDbId = it.exerciseDbId,
                exerciseId = it.exerciseId,
                exerciseName = it.exerciseName,
            )
        }
        val primary = (exercise?.effectiveMuscles?.takeIf { it.isNotEmpty() } ?: dbInfo?.involvedMuscles.orEmpty())
            .firstOrNull { it.role == MuscleRole.PRIMARY }
        if (primary != null) {
            val canonical = VolumeCalculator.normalizeCanonicalMuscleGroup(primary.muscle, primary.emphasis)
            getAugeMusclePillarId(canonical, primary.emphasis)
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

data class SetAutoRegulation(
    val exerciseId: String,
    val nextSetIdx: Int,
    val adjustmentFactor: Double,
    val adjustedWeight: Double,
    val reason: String,
)

object WorkoutAutoRegulation {
    private const val MIN_FACTOR = 0.60
    private const val MAX_FACTOR = 1.10

    fun computeAdjustmentFactor(
        weightedDrainPct: Double,
        effectiveRpe: Double,
        reachedFailure: Boolean,
        isFailedSet: Boolean,
        isPartial: Boolean,
        sessionProgress: Double,
    ): Double {
        var factor = 1.0

        val drainFactor = when {
            weightedDrainPct >= 10.0 -> -0.10
            weightedDrainPct >= 7.0 -> -0.07
            weightedDrainPct >= 4.5 -> -0.04
            weightedDrainPct >= 2.5 -> -0.02
            weightedDrainPct >= 1.0 -> -0.01
            else -> 0.0
        }
        factor += drainFactor

        val rpeFactor = when {
            effectiveRpe >= 11.0 -> -0.08
            effectiveRpe >= 10.5 -> -0.06
            effectiveRpe >= 10.0 -> -0.04
            effectiveRpe >= 9.5 -> -0.02
            effectiveRpe >= 8.5 -> -0.01
            effectiveRpe <= 7.0 -> +0.02
            else -> 0.0
        }
        factor += rpeFactor

        if (reachedFailure) factor -= 0.05
        if (isFailedSet) factor -= 0.03
        if (isPartial) factor -= 0.02

        val sessionFactor = when {
            sessionProgress >= 0.80 -> -0.03
            sessionProgress >= 0.60 -> -0.02
            sessionProgress >= 0.40 -> -0.01
            else -> 0.0
        }
        factor += sessionFactor

        return factor.coerceIn(MIN_FACTOR, MAX_FACTOR)
    }

    @Suppress("UNUSED_PARAMETER")
    fun buildReason(
        factor: Double,
        weightedDrainPct: Double,
        effectiveRpe: Double,
        reachedFailure: Boolean,
    ): String {
        val parts = mutableListOf<String>()
        if (reachedFailure) parts.add("Fallo")
        if (weightedDrainPct >= 5.0) parts.add("Fatiga acumulada")
        val detail = parts.joinToString(" · ")
        return when {
            factor < 0.95 -> buildString {
                append("Ajuste de carga")
                if (detail.isNotBlank()) append(" · $detail")
                append(" · −${((1 - factor) * 100).toInt()}%")
            }
            factor > 1.02 -> "Ajuste de carga · Recuperación buena · +${((factor - 1) * 100).toInt()}%"
            else -> "Sin ajuste de carga"
        }
    }

    private const val READINESS_REDUCTION_THRESHOLD = 70
    private const val READINESS_SEVERE_THRESHOLD = 50
    private const val READINESS_MIN_FACTOR = 0.70
    private const val READINESS_SEVERE_FACTOR = 0.85
    private const val READINESS_RECOVERY_FACTOR = 1.05

    fun computeReadinessAdjustmentFactor(
        readinessNeural: Int?,
        readinessSpinal: Int?,
        readinessMuscular: Int?,
        readinessPerMuscle: Map<String, Int>?,
        involvedMuscleIds: List<String>,
    ): Double {
        val perMuscleValues = readinessPerMuscle?.let { overrides ->
            val relevant = involvedMuscleIds.mapNotNull { overrides[it] }
            if (relevant.isNotEmpty()) relevant.min() else null
        }
        val relevantReadiness = listOfNotNull(
            readinessNeural?.toDouble(),
            readinessSpinal?.toDouble(),
            readinessMuscular?.toDouble(),
            perMuscleValues?.toDouble(),
        ).minOrNull()

        val readiness = (relevantReadiness ?: return 1.0).coerceIn(0.0, 100.0)

        return when {
            readiness < READINESS_SEVERE_THRESHOLD -> {
                val t = (readiness / READINESS_SEVERE_THRESHOLD).coerceIn(0.0, 1.0)
                (READINESS_MIN_FACTOR + (READINESS_SEVERE_FACTOR - READINESS_MIN_FACTOR) * t)
                    .coerceIn(READINESS_MIN_FACTOR, READINESS_SEVERE_FACTOR)
            }
            readiness < READINESS_REDUCTION_THRESHOLD -> {
                val t = ((readiness - READINESS_SEVERE_THRESHOLD) /
                    (READINESS_REDUCTION_THRESHOLD - READINESS_SEVERE_THRESHOLD)).coerceIn(0.0, 1.0)
                (READINESS_SEVERE_FACTOR + (1.0 - READINESS_SEVERE_FACTOR) * t)
                    .coerceIn(READINESS_SEVERE_FACTOR, 1.0)
            }
            readiness >= 90 -> READINESS_RECOVERY_FACTOR
            else -> 1.0
        }
    }

    fun buildReadinessReason(factor: Double, readinessValue: Int?): String {
        if (factor == 1.0) return ""
        return when {
            factor < 0.85 && readinessValue != null -> "Readiness ${readinessValue} · −${((1 - factor) * 100).toInt()}%"
            factor < 1.0 -> "Readiness ${readinessValue ?: "baja"}"
            factor > 1.0 -> "Readiness ${readinessValue ?: "alta"} · +${((factor - 1) * 100).toInt()}%"
            else -> ""
        }
    }
}

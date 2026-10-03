package com.example.kpkn.domain.training

import com.example.kpkn.data.models.CardioBlockType
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.effectiveRepRange
import com.example.kpkn.data.models.isCardioPart
import com.example.kpkn.domain.calculations.calculateSessionTimeBreakdown
import kotlin.math.ceil

/** Full materialized-session duration, including structured parts and their minimum durations. */
data class SessionDurationBreakdown(
    val setupSeconds: Int,
    val resistanceExecutionSeconds: Int,
    val resistanceRestSeconds: Int,
    /** Session warmup, approaches, mobility, and the fixed 3-minute resistance warmup. */
    val warmupSeconds: Int,
    /** Total cardio duration; cardio warmup/cooldown are subdivisions of this value. */
    val cardioSeconds: Int,
    val cardioWarmupSeconds: Int,
    val cardioWorkSeconds: Int,
    val cardioCooldownSeconds: Int,
    /** Additional non-cardio time required by exercise-level minimum durations. */
    val exerciseMinimumSeconds: Int,
    /** Additional non-cardio time required by structured-part minimum durations. */
    val partMinimumSeconds: Int,
    val totalSeconds: Int,
) {
    /** Rounded up so any remaining seconds consume the next budget minute. */
    val totalMinutes: Int
        get() = ceil(totalSeconds.coerceAtLeast(0) / 60.0).toInt().coerceAtLeast(1)

    /** Useful to callers comparing the maximum duration of one materialized session. */
    val maxSessionMinutes: Int get() = totalMinutes
}

/**
 * The single duration estimator for materialized [Session]s.
 *
 * Resistance setup/work/rest, approaches, session warmup, and attached/global
 * mobility use [calculateSessionTimeBreakdown]. Cardio's effective duration is
 * treated as one complete block; the default 2-minute easy start and 1-minute
 * easy finish (or explicit leading/trailing interval blocks) are subdivisions,
 * not added minutes and not repeated for interval rounds. Legacy exercises
 * mirrored in [Session.exercises] and [Session.parts] are counted once by ID.
 * Session/part/exercise duration fields are minimums, so only any shortfall is
 * added to the executable estimate. `Session.targetDurationMinutes` is not
 * evidence of actual duration and is intentionally not used as a measurement.
 */
object SessionDurationEstimator {
    private const val GENERAL_RESISTANCE_WARMUP_SECONDS = 180
    private const val DEFAULT_CARDIO_WARMUP_SECONDS = 120
    private const val DEFAULT_CARDIO_COOLDOWN_SECONDS = 60
    private const val SECONDS_PER_RESISTANCE_REP = 4
    private const val DEFAULT_RESISTANCE_SET_SECONDS = 45

    fun estimate(session: Session): SessionDurationBreakdown {
        val exercises = session.allExercises().distinctBy { it.id }
        val exerciseById = exercises.associateBy { it.id }
        val estimatedExercises = exercises.map { it.withRepRangeDurationEstimate() }
        val estimatedExerciseById = estimatedExercises.associateBy { it.id }
        val parts = session.parts.distinctBy { it.id }
        val groups = session.allSupersetGroups()
        val cardioExercises = exercises.filter { it.cardioDetails != null }
        val resistanceExercises = exercises.filter { it.cardioDetails == null }
        val mobilitySeries = parts.flatMap { it.mobilitySeries }

        val complete = calculateSessionTimeBreakdown(
            exercises = estimatedExercises,
            supersetGroups = groups,
            sessionWarmup = session.warmup,
            globalMobilitySeries = mobilitySeries,
        )
        val hasResistance = resistanceExercises.any { it.sets.isNotEmpty() || it.warmupSets.isNotEmpty() }
        val generalWarmup = if (hasResistance) GENERAL_RESISTANCE_WARMUP_SECONDS else 0

        val actualCardio = cardioExercises.mapNotNull { it.cardioDetails }
            .map { it.durationBreakdown() }
        val actualCardioSeconds = actualCardio.sumOf { it.totalSeconds }
        val cardioWarmup = actualCardio.sumOf { it.warmupSeconds }
        val cardioCooldown = actualCardio.sumOf { it.cooldownSeconds }
        val cardioWork = actualCardio.sumOf { it.workSeconds }

        // Per-exercise targets are floors on that exercise's executable time,
        // not extra work appended after it.
        val exerciseFloorExtra = exercises.associate { exercise ->
            exercise.id to exerciseDurationFloorExtra(estimatedExerciseById.getValue(exercise.id))
        }

        // A part's configured mobility timer and explicit duration are also
        // floors. Estimate each part independently so an exercise mirrored in
        // the legacy list does not make the part's activities count twice.
        val claimedPartExerciseIds = mutableSetOf<String>()
        var mobilityConfigExtraSeconds = 0
        var cardioPartFloorExtraSeconds = 0
        var cardioPartWithoutExercisesWarmupSeconds = 0
        var cardioPartWithoutExercisesWorkSeconds = 0
        var cardioPartWithoutExercisesCooldownSeconds = 0
        var otherPartFloorExtraSeconds = 0

        parts.forEach { part ->
            val partExercises = part.exercises.mapNotNull { exerciseById[it.id] }
                .distinctBy { it.id }
                .filter { claimedPartExerciseIds.add(it.id) }
            val estimatedPartExercises = partExercises.map { estimatedExerciseById.getValue(it.id) }
            val partIds = partExercises.mapTo(mutableSetOf()) { it.id }
            val partGroups = groups.filter { group ->
                group.exerciseOrder.isNotEmpty() && group.exerciseOrder.all { it in partIds }
            }
            val partBreakdown = calculateSessionTimeBreakdown(
                exercises = estimatedPartExercises,
                supersetGroups = partGroups,
                globalMobilitySeries = part.mobilitySeries,
            )
            val mobilitySeriesSeconds = mobilityDurationSeconds(part)
            val configuredMobilitySeconds = (part.mobilityConfig?.totalMinutes ?: 0)
                .coerceAtLeast(0) * 60
            val partMobilityExtra = (configuredMobilitySeconds - mobilitySeriesSeconds).coerceAtLeast(0)
            mobilityConfigExtraSeconds += partMobilityExtra

            val executablePartSeconds = partBreakdown.totalSeconds +
                partExercises.sumOf { exerciseFloorExtra[it.id] ?: 0 } + partMobilityExtra
            val configuredPartFloorSeconds = maxOf(
                (part.targetDurationMinutes ?: 0).coerceAtLeast(0) * 60,
                configuredMobilitySeconds,
            )
            val extraForPartFloor = (configuredPartFloorSeconds - executablePartSeconds).coerceAtLeast(0)
            if (extraForPartFloor == 0) return@forEach

            if (part.isCardioPart()) {
                // An empty cardio part's target is itself the block, so split
                // that floor using the same inside-block 2+1 minute convention.
                if (partExercises.none { it.cardioDetails != null }) {
                    val floorSplit = defaultCardioSplit(extraForPartFloor)
                    cardioPartWithoutExercisesWarmupSeconds += floorSplit.warmupSeconds
                    cardioPartWithoutExercisesWorkSeconds += floorSplit.workSeconds
                    cardioPartWithoutExercisesCooldownSeconds += floorSplit.cooldownSeconds
                } else {
                    cardioPartFloorExtraSeconds += extraForPartFloor
                }
            } else {
                otherPartFloorExtraSeconds += extraForPartFloor
            }
        }

        val cardioExerciseFloorExtra = cardioExercises.sumOf { exerciseFloorExtra[it.id] ?: 0 }
        val resistanceExerciseFloorExtra = resistanceExercises.sumOf { exerciseFloorExtra[it.id] ?: 0 }
        val cardioSeconds = actualCardioSeconds + cardioExerciseFloorExtra + cardioPartFloorExtraSeconds +
            cardioPartWithoutExercisesWarmupSeconds + cardioPartWithoutExercisesWorkSeconds +
            cardioPartWithoutExercisesCooldownSeconds
        val totalWarmupSeconds = complete.warmupSeconds + generalWarmup + mobilityConfigExtraSeconds
        val totalSeconds = complete.totalSeconds + generalWarmup +
            exerciseFloorExtra.values.sum() + mobilityConfigExtraSeconds + otherPartFloorExtraSeconds +
            cardioPartFloorExtraSeconds + cardioPartWithoutExercisesWarmupSeconds +
            cardioPartWithoutExercisesWorkSeconds + cardioPartWithoutExercisesCooldownSeconds

        return SessionDurationBreakdown(
            setupSeconds = complete.setupSeconds,
            resistanceExecutionSeconds = (complete.executionSeconds - actualCardioSeconds).coerceAtLeast(0),
            resistanceRestSeconds = complete.restSeconds,
            warmupSeconds = totalWarmupSeconds,
            cardioSeconds = cardioSeconds,
            cardioWarmupSeconds = cardioWarmup + cardioPartWithoutExercisesWarmupSeconds,
            cardioWorkSeconds = cardioWork + cardioExerciseFloorExtra + cardioPartFloorExtraSeconds +
                cardioPartWithoutExercisesWorkSeconds,
            cardioCooldownSeconds = cardioCooldown + cardioPartWithoutExercisesCooldownSeconds,
            exerciseMinimumSeconds = resistanceExerciseFloorExtra,
            partMinimumSeconds = otherPartFloorExtraSeconds,
            totalSeconds = totalSeconds,
        )
    }

    private fun exerciseDurationFloorExtra(exercise: Exercise): Int {
        val minimumSeconds = (exercise.targetDurationMinutes ?: 0).coerceAtLeast(0) * 60
        if (minimumSeconds == 0) return 0
        val standalone = calculateSessionTimeBreakdown(
            exercises = listOf(exercise.withoutSupersetReference()),
            supersetGroups = emptyList(),
        ).totalSeconds
        return (minimumSeconds - standalone).coerceAtLeast(0)
    }

    private fun Exercise.withoutSupersetReference(): Exercise = copy(
        supersetId = null,
        supersetGroupRef = null,
    )

    /**
     * §12.2 estimates the top of a prescribed rep range. Keep the established
     * 45-second set baseline, extending only ranges that exceed it with the
     * existing 4-seconds-per-rep estimate used by Calculations' movement-time
     * fallbacks. TIME prescriptions retain their authored seconds unchanged.
     */
    private fun Exercise.withRepRangeDurationEstimate(): Exercise {
        if (cardioDetails != null || trainingMode == TrainingMode.TIME) return this
        return copy(
            trainingMode = TrainingMode.TIME,
            sets = sets.map { set ->
                val maxReps = set.effectiveRepRange()?.max
                val seconds = maxReps?.times(SECONDS_PER_RESISTANCE_REP)
                    ?.coerceAtLeast(DEFAULT_RESISTANCE_SET_SECONDS)
                    ?: DEFAULT_RESISTANCE_SET_SECONDS
                set.copy(targetDuration = seconds)
            },
        )
    }

    private fun mobilityDurationSeconds(part: SessionPart): Int = part.mobilitySeries.sumOf { item ->
        val setCount = item.sets.coerceAtLeast(1)
        val reps = item.reps?.filter { it.isDigit() }?.toIntOrNull()?.coerceAtLeast(1)
        val secondsPerSet = item.durationSeconds?.takeIf { it > 0 } ?: reps?.times(4) ?: 30
        secondsPerSet * setCount
    }

    private data class CardioDurationParts(
        val totalSeconds: Int,
        val warmupSeconds: Int,
        val workSeconds: Int,
        val cooldownSeconds: Int,
    )

    private fun CardioDetails.durationBreakdown(): CardioDurationParts {
        val total = effectiveDurationSeconds().coerceAtLeast(0)
        val intervals = intervalBlocks
        val prescribedWarmup = intervals.firstOrNull { it.type == CardioBlockType.WARMUP }
            ?.durationSeconds?.coerceAtLeast(0)
        val warmup = (prescribedWarmup ?: minOf(DEFAULT_CARDIO_WARMUP_SECONDS, total))
            .coerceAtMost(total)
        val prescribedCooldown = intervals.lastOrNull { it.type == CardioBlockType.COOLDOWN }
            ?.durationSeconds?.coerceAtLeast(0)
        val cooldown = (prescribedCooldown ?: minOf(DEFAULT_CARDIO_COOLDOWN_SECONDS, total - warmup))
            .coerceAtMost(total - warmup)
        return CardioDurationParts(
            totalSeconds = total,
            warmupSeconds = warmup,
            workSeconds = (total - warmup - cooldown).coerceAtLeast(0),
            cooldownSeconds = cooldown,
        )
    }

    private fun defaultCardioSplit(totalSeconds: Int): CardioDurationParts {
        val total = totalSeconds.coerceAtLeast(0)
        val warmup = minOf(DEFAULT_CARDIO_WARMUP_SECONDS, total)
        val cooldown = minOf(DEFAULT_CARDIO_COOLDOWN_SECONDS, total - warmup)
        return CardioDurationParts(
            totalSeconds = total,
            warmupSeconds = warmup,
            workSeconds = (total - warmup - cooldown).coerceAtLeast(0),
            cooldownSeconds = cooldown,
        )
    }
}

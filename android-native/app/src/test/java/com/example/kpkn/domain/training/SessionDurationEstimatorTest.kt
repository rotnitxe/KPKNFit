package com.example.kpkn.domain.training

import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioBlockType
import com.example.kpkn.data.models.CardioIntervalBlock
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.RepRange
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.WarmupExercise
import com.example.kpkn.data.models.WarmupSetDefinition
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionDurationEstimatorTest {
    @Test
    fun resistanceIncludesSetupWorkInterSetRestAndGeneralWarmup() {
        val session = Session(
            id = "resistance",
            name = "Resistance",
            exercises = listOf(
                Exercise(
                    id = "squat",
                    name = "Squat",
                    restTime = 90,
                    sets = List(3) { ExerciseSet(id = "set-$it", targetRepsRange = RepRange(5, 8)) },
                ),
            ),
        )

        val duration = SessionDurationEstimator.estimate(session)

        assertEquals(60, duration.setupSeconds)
        assertEquals(135, duration.resistanceExecutionSeconds)
        assertEquals(180, duration.resistanceRestSeconds)
        assertEquals(180, duration.warmupSeconds)
        assertEquals(555, duration.totalSeconds)
        assertEquals(10, duration.totalMinutes)
    }

    @Test
    fun resistanceUsesTheUpperRepEndpointWhenItExceedsTheEstablishedSetEstimate() {
        val session = Session(
            id = "high-rep-resistance",
            name = "High rep resistance",
            exercises = listOf(
                Exercise(
                    id = "row",
                    name = "Row",
                    sets = List(2) { ExerciseSet(id = "set-$it", targetRepsRange = RepRange(10, 15)) },
                ),
            ),
        )

        val duration = SessionDurationEstimator.estimate(session)

        assertEquals(120, duration.resistanceExecutionSeconds)
        assertEquals(60 + 120 + 90 + 180, duration.totalSeconds)
    }

    @Test
    fun explicitAndApproachWarmupsAreCountedAlongsideTheThreeMinuteGeneralWarmup() {
        val session = Session(
            id = "warmups",
            name = "Warmups",
            warmup = listOf(WarmupExercise(id = "general", name = "Movilidad", duration = 60)),
            exercises = listOf(
                Exercise(
                    id = "bench",
                    name = "Bench",
                    restTime = 90,
                    sets = List(2) { ExerciseSet(id = "set-$it", targetReps = 8) },
                    warmupSets = listOf(
                        WarmupSetDefinition("approach-1", percentageOfWorkingWeight = 40.0, targetReps = 5, restBetween = 60),
                        WarmupSetDefinition("approach-2", percentageOfWorkingWeight = 60.0, targetReps = 3, restBetween = 90),
                    ),
                ),
            ),
        )

        val duration = SessionDurationEstimator.estimate(session)

        // 3:00 general + 1:15 explicit session warmup + (0:30+1:00) + (0:30+1:30).
        assertEquals(465, duration.warmupSeconds)
        assertEquals(60 + 90 + 90 + 465, duration.totalSeconds)
        assertEquals(12, duration.totalMinutes)
    }

    @Test
    fun cardioOnlyPartCountsItsBlockOnceAndKeepsWarmupAndCooldownInsideIt() {
        val cardio = Exercise(
            id = "cardio",
            name = "Walk",
            cardioDetails = CardioDetails(type = CardioType.WALK, targetDurationSeconds = 10 * 60),
        )
        val session = Session(
            id = "cardio-only",
            name = "Cardio only",
            parts = listOf(
                SessionPart(
                    id = "cardio-part",
                    name = "Cardio",
                    exercises = listOf(cardio),
                    targetDurationMinutes = 10,
                    isCardioGroup = true,
                ),
            ),
        )

        val duration = SessionDurationEstimator.estimate(session)

        assertEquals(600, duration.cardioSeconds)
        assertEquals(120, duration.cardioWarmupSeconds)
        assertEquals(420, duration.cardioWorkSeconds)
        assertEquals(60, duration.cardioCooldownSeconds)
        // The existing 60-second setup transition remains included.
        assertEquals(60, duration.setupSeconds)
        assertEquals(660, duration.totalSeconds)
        assertEquals(11, duration.maxSessionMinutes)
    }

    @Test
    fun mixedSessionDeduplicatesLegacyAndPartExercisesAndHonorsPartFloors() {
        val bench = Exercise(
            id = "bench",
            name = "Bench",
            restTime = 90,
            sets = List(2) { ExerciseSet(id = "bench-set-$it", targetReps = 8) },
        )
        val cardio = Exercise(
            id = "cardio",
            name = "Bike",
            cardioDetails = CardioDetails(type = CardioType.BIKE_STATIONARY, targetDurationSeconds = 10 * 60),
        )
        val session = Session(
            id = "mixed",
            name = "Mixed",
            exercises = listOf(bench),
            parts = listOf(
                SessionPart(id = "strength-part", name = "Strength", exercises = listOf(bench)),
                SessionPart(
                    id = "cardio-part",
                    name = "Cardio",
                    exercises = listOf(cardio),
                    targetDurationMinutes = 10,
                    isCardioGroup = true,
                ),
            ),
        )

        val duration = SessionDurationEstimator.estimate(session)

        assertEquals(120, duration.setupSeconds)
        assertEquals(90, duration.resistanceExecutionSeconds)
        assertEquals(90, duration.resistanceRestSeconds)
        assertEquals(180, duration.warmupSeconds)
        assertEquals(600, duration.cardioSeconds)
        assertEquals(1080, duration.totalSeconds)
        assertEquals(18, duration.totalMinutes)
    }

    @Test
    fun cardioPartWithoutExerciseUsesItsDurationFloorAsOneCardioBlock() {
        val session = Session(
            id = "empty-cardio-part",
            name = "Cardio",
            parts = listOf(
                SessionPart(
                    id = "cardio-part",
                    name = "Cardio",
                    targetDurationMinutes = 10,
                    isCardioGroup = true,
                ),
            ),
        )

        val duration = SessionDurationEstimator.estimate(session)

        assertEquals(600, duration.cardioSeconds)
        assertEquals(120, duration.cardioWarmupSeconds)
        assertEquals(420, duration.cardioWorkSeconds)
        assertEquals(60, duration.cardioCooldownSeconds)
        assertEquals(600, duration.totalSeconds)
        assertEquals(10, duration.totalMinutes)
    }

    @Test
    fun explicitIntervalWarmupAndCooldownAreNotMultipliedByRounds() {
        val session = Session(
            id = "interval-cardio",
            name = "Interval cardio",
            parts = listOf(
                SessionPart(
                    id = "cardio-part",
                    name = "Cardio",
                    isCardioGroup = true,
                    exercises = listOf(
                        Exercise(
                            id = "intervals",
                            name = "Intervals",
                            cardioDetails = CardioDetails(
                                type = CardioType.TREADMILL,
                                targetDurationSeconds = null,
                                intervalBlocks = listOf(
                                    CardioIntervalBlock(type = CardioBlockType.WARMUP, durationSeconds = 120),
                                    CardioIntervalBlock(type = CardioBlockType.WORK, durationSeconds = 300),
                                    CardioIntervalBlock(type = CardioBlockType.COOLDOWN, durationSeconds = 60),
                                ),
                                intervalRounds = 2,
                            ),
                        ),
                    ),
                ),
            ),
        )

        val duration = SessionDurationEstimator.estimate(session)

        assertEquals(960, duration.cardioSeconds)
        assertEquals(120, duration.cardioWarmupSeconds)
        assertEquals(780, duration.cardioWorkSeconds)
        assertEquals(60, duration.cardioCooldownSeconds)
    }

    @Test
    fun structuredPartDurationIsAppliedAsAFloorInsteadOfAddedOnTop() {
        val exercise = Exercise(
            id = "press",
            name = "Press",
            restTime = 90,
            sets = List(2) { ExerciseSet(id = "set-$it", targetReps = 8) },
        )
        val session = Session(
            id = "part-floor",
            name = "Part floor",
            parts = listOf(
                SessionPart(
                    id = "resistance-part",
                    name = "Resistance",
                    exercises = listOf(exercise),
                    targetDurationMinutes = 10,
                ),
            ),
        )

        val duration = SessionDurationEstimator.estimate(session)

        // Part execution is 4 min; the 10-minute floor adds only the 6-minute shortfall.
        assertEquals(360, duration.partMinimumSeconds)
        assertEquals(780, duration.totalSeconds)
        assertEquals(13, duration.totalMinutes)
    }
}

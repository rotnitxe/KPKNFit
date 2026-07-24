package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.AthleteType
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.WorkoutLog
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SessionDrainBoundsTest {

    private val exerciseDb = mapOf(
        "squat" to ExerciseMuscleInfo(
            id = "squat", name = "Squat", equipment = "barbell",
            efc = 8.0, cnc = 7.0, ssc = 6.0,
            involvedMuscles = listOf(InvolvedMuscle("Cuádriceps", MuscleRole.PRIMARY)),
        ),
        "deadlift" to ExerciseMuscleInfo(
            id = "deadlift", name = "Deadlift", equipment = "barbell",
            efc = 9.0, cnc = 9.0, ssc = 9.0,
            involvedMuscles = listOf(InvolvedMuscle("Erectores Espinales", MuscleRole.PRIMARY)),
        ),
    )

    @Test
    fun hardSession_withManyHighRpeSets_hasMinimumDrain() {
        val sets = List(8) { i ->
            CompletedSet(id = "s-$i", weight = 140.0, reps = 5, rpe = 9.0)
        }
        val exercises = listOf(
            CompletedExercise(
                exerciseId = "squat",
                exerciseName = "Squat",
                exerciseDbId = "squat",
                restTime = 180,
                sets = sets,
            ),
        )
        val drain = AugeFatigueEngine.calculateCompletedSessionDrain(
            completedExercises = exercises,
            exerciseDb = exerciseDb,
            settings = Settings(),
        )
        assertTrue("CNS drain should be >= 10 after hard work, got ${drain.cns}", drain.cns >= 10)
        assertTrue("Muscular drain should be >= 10 after hard work, got ${drain.muscular}", drain.muscular >= 10)
        assertTrue("Drain must not be (0,0,0)", drain.cns + drain.muscular + drain.spinal > 0)
    }

    @Test
    fun extremePowerlifterSessions_ringsStayAtOrAboveFloor() {
        val settings = Settings(athleteType = AthleteType.POWERLIFTER)
        val floor = AugeUtils.physiologicalFloor(settings)
        val now = System.currentTimeMillis()
        val history = (1..5).map { day ->
            WorkoutLog(
                id = "grind-$day",
                programId = "p",
                sessionId = "s",
                sessionName = "Grind",
                date = Instant.ofEpochMilli(now - (6 - day) * 86_400_000L).toString(),
                durationMinutes = 100,
                completedExercises = listOf(
                    CompletedExercise(
                        exerciseId = "squat",
                        exerciseName = "Squat",
                        exerciseDbId = "squat",
                        restTime = 60,
                        sets = List(10) { i ->
                            CompletedSet(id = "sq-$day-$i", weight = 180.0, reps = 3, rpe = 10.0)
                        },
                    ),
                    CompletedExercise(
                        exerciseId = "deadlift",
                        exerciseName = "Deadlift",
                        exerciseDbId = "deadlift",
                        restTime = 60,
                        sets = List(8) { i ->
                            CompletedSet(id = "dl-$day-$i", weight = 200.0, reps = 2, rpe = 10.0)
                        },
                    ),
                ),
            )
        }

        val batteries = AugeRecoveryEngine.calculateGlobalBatteries(
            history = history,
            wellbeing = null,
            settings = settings,
            exerciseDb = exerciseDb,
        )

        assertTrue("SYSTEM >= CNS floor (${floor.cns}), got ${batteries.cnc}", batteries.cnc >= floor.cns)
        assertTrue("STRUCTURE >= spinal floor (${floor.spinal}), got ${batteries.spinal}", batteries.spinal >= floor.spinal)
        assertTrue("MUSCULAR >= muscular floor (${floor.muscular}), got ${batteries.muscular}", batteries.muscular >= floor.muscular)
    }

    @Test
    fun perSetDrain_isCappedBelowFullTank() {
        val tanks = AugeFatigueEngine.calculatePersonalizedBatteryTanks(Settings())
        val metrics = AugeFatigueEngine.getDynamicAugeMetrics("Squat", "barbell", exerciseDb["squat"])!!
        val drain = AugeFatigueEngine.calculateSetBatteryDrain(
            set = CompletedSet(id = "x", weight = 250.0, reps = 1, rpe = 10.0),
            metrics = metrics,
            tanks = tanks,
            accumulatedSets = 1,
            restTime = 60,
        )
        assertTrue("Muscular set drain capped <= 32%, got ${drain.muscularDrainPct}", drain.muscularDrainPct <= 32.0)
        assertTrue("CNS set drain capped <= 32%, got ${drain.cnsDrainPct}", drain.cnsDrainPct <= 32.0)
        assertTrue("Spinal set drain capped <= 35%, got ${drain.spinalDrainPct}", drain.spinalDrainPct <= 35.0)
    }
}

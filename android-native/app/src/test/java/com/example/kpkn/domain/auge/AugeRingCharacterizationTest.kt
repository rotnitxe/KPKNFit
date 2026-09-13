package com.example.kpkn.domain.auge

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

class AugeRingCharacterizationTest {
    private val now = Instant.parse("2026-09-12T18:00:00Z").toEpochMilli()
    private val day = 24L * 3_600_000L

    private val db = mapOf(
        "bench" to ExerciseMuscleInfo(
            id = "bench", name = "Press Banca", equipment = "barra",
            efc = 3.2, cnc = 3.5, ssc = 0.35, axialLoadFactor = 0.0,
            involvedMuscles = listOf(InvolvedMuscle("Pectorales", MuscleRole.PRIMARY, volumeContribution = 1.0)),
        ),
        "squat" to ExerciseMuscleInfo(
            id = "squat", name = "Sentadilla", equipment = "barra",
            efc = 4.2, cnc = 4.5, ssc = 1.0, axialLoadFactor = 1.0,
            involvedMuscles = listOf(InvolvedMuscle("Cuádriceps", MuscleRole.PRIMARY, volumeContribution = 1.0)),
        ),
    )

    @Test
    fun energySpinalAndChest_recoverMonotonically_andHardDrainsMoreThanLight() {
        val hours = listOf(0, 12, 24, 48, 72)
        val hard = hardSquatAndBench(now)
        val light = lightBench(now)
        val settings = Settings()

        fun energy(log: WorkoutLog, h: Int) = AugeRecoveryEngine.calculateSystemicFatigue(
            history = listOf(log), wellbeing = null, settings = settings, exerciseDb = db,
            nowOverride = now + h * 3_600_000L,
        ).first
        fun spinal(log: WorkoutLog, h: Int) = AugeRecoveryEngine.calculateSpinalBattery(
            history = listOf(log), wellbeing = null, settings = settings, exerciseDb = db,
            nowOverride = now + h * 3_600_000L,
        )
        fun chest(log: WorkoutLog, h: Int) = AugeRecoveryEngine.calculateMuscleBattery(
            muscleName = "Pectorales", history = listOf(log), wellbeing = null, settings = settings,
            exerciseDb = db, nowOverride = now + h * 3_600_000L,
        ).recoveryScore

        hours.zipWithNext().forEach { (a, b) ->
            assertTrue("Energía $a -> $b", energy(hard, a) <= energy(hard, b) + 1)
            assertTrue("Columna $a -> $b", spinal(hard, a) <= spinal(hard, b) + 1)
            assertTrue("Pecho $a -> $b", chest(hard, a) <= chest(hard, b) + 1)
        }
        assertTrue(energy(hard, 0) <= energy(light, 0))
        assertTrue(chest(hard, 0) <= chest(light, 0))
        hours.forEach { h ->
            println("[PIN] t=${h}h energyHard=${energy(hard, h)} spinalHard=${spinal(hard, h)} chestHard=${chest(hard, h)} energyLight=${energy(light, h)} chestLight=${chest(light, h)}")
        }
        assertTrue("Pecho duro debe drenar de forma visible", chest(hard, 0) < 95)
        assertTrue("Columna con sentadilla debe drenar", spinal(hard, 0) < 100)
        pin("energyHard@0", energy(hard, 0), 80)
        pin("spinalHard@0", spinal(hard, 0), 93)
        pin("chestHard@0", chest(hard, 0), 72)
        pin("energyHard@12", energy(hard, 12), 92)
        pin("spinalHard@12", spinal(hard, 12), 96)
        pin("chestHard@12", chest(hard, 12), 80)
        pin("energyHard@24", energy(hard, 24), 97)
        pin("spinalHard@24", spinal(hard, 24), 99)
        pin("energyHard@48", energy(hard, 48), 99)
        pin("energyHard@72", energy(hard, 72), 99)
        pin("energyLight@0", energy(light, 0), 90)
        pin("chestLight@0", chest(light, 0), 92)
    }

    @Test
    fun frequency_1x_3x_5x_energyRanksAndPins() {
        val settings = Settings()
        fun week(count: Int): List<WorkoutLog> = (0 until count).map { i ->
            // Consecutive recent days so chronic load is visible after τ 95 %.
            hardSquatAndBench(now - 6L * 3_600_000L - i * day).copy(id = "w-$count-$i")
        }
        fun energy(count: Int) = AugeRecoveryEngine.calculateSystemicFatigue(
            history = week(count), wellbeing = null, settings = settings, exerciseDb = db, nowOverride = now,
        ).first
        val e1 = energy(1)
        val e3 = energy(3)
        val e5 = energy(5)
        println("[PIN] energy 1x=$e1 3x=$e3 5x=$e5")
        assertTrue("1× no puede dejar menos Energía que 3×, 1x=$e1 3x=$e3", e1 >= e3)
        assertTrue("5×/semana no puede dejar más Energía que 3×, 3x=$e3 5x=$e5", e5 <= e3)
        pin("energy1x", e1, 87)
        pin("energy3x", e3, 86)
        pin("energy5x", e5, 85)
    }

    private fun pin(label: String, value: Int, expected: Int, slack: Int = 2) {
        assertTrue("$label=$value fuera de ${expected - slack}..${expected + slack}", kotlin.math.abs(value - expected) <= slack)
    }

    private fun hardSquatAndBench(at: Long): WorkoutLog {
        val iso = Instant.ofEpochMilli(at).toString()
        val impact = MuscularSessionImpactV2(
            completionInstantIso = iso,
            globalMuscularDrain = 28,
            perMuscle = mapOf(
                "Pectorales" to MuscleSessionImpactV2(
                    stressUnits = 750.0, capacityAtCompletion = 2600.0, immediateDrainPct = 28.0,
                    directStressUnits = 750.0, indirectStressUnits = 0.0,
                ),
            ),
            involvedVolumeMuscles = setOf("Pectorales"),
            setInputHash = "hard",
            contextHash = "hard",
        )
        return WorkoutLog(
            id = "hard-$at", programId = "p", sessionId = "s", sessionName = "Dura",
            date = iso, durationMinutes = 70,
            muscularImpactV2 = impact,
            completedExercises = listOf(
                CompletedExercise(
                    exerciseId = "squat", exerciseName = "Sentadilla", exerciseDbId = "squat", restTime = 180,
                    sets = List(5) { i -> CompletedSet(id = "sq-$i", weight = 140.0, reps = 5, rpe = 8.5) },
                ),
                CompletedExercise(
                    exerciseId = "bench", exerciseName = "Press Banca", exerciseDbId = "bench", restTime = 120,
                    sets = List(4) { i -> CompletedSet(id = "b-$i", weight = 90.0, reps = 6, rpe = 8.5) },
                ),
            ),
        )
    }

    private fun lightBench(at: Long): WorkoutLog {
        val iso = Instant.ofEpochMilli(at).toString()
        return WorkoutLog(
            id = "light-$at", programId = "p", sessionId = "s", sessionName = "Ligera",
            date = iso, durationMinutes = 40,
            muscularImpactV2 = MuscularSessionImpactV2(
                completionInstantIso = iso,
                globalMuscularDrain = 10,
                perMuscle = mapOf(
                    "Pectorales" to MuscleSessionImpactV2(
                        stressUnits = 180.0, capacityAtCompletion = 2600.0, immediateDrainPct = 10.0,
                        directStressUnits = 180.0, indirectStressUnits = 0.0,
                    ),
                ),
                involvedVolumeMuscles = setOf("Pectorales"),
                setInputHash = "light",
                contextHash = "light",
            ),
            completedExercises = listOf(
                CompletedExercise(
                    exerciseId = "bench", exerciseName = "Press Banca", exerciseDbId = "bench", restTime = 120,
                    sets = List(3) { i -> CompletedSet(id = "lb-$i", weight = 60.0, reps = 10, rpe = 7.0) },
                ),
            ),
        )
    }
}

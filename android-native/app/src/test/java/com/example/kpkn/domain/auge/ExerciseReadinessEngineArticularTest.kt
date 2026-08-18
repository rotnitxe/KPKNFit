package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test

class ExerciseReadinessEngineArticularTest {

    @Test
    fun articularLookup_resolvesAliasesHeadsAndParentsThroughOneApi() {
        assertEquals(
            listOf(ArticularBattery.SHOULDER),
            AugeTtcEngine.articularBatteriesFor("Deltoides", "posterior"),
        )
        assertEquals(
            listOf(ArticularBattery.HIP),
            AugeTtcEngine.articularBatteriesFor("Glúteo Mayor"),
        )
        assertEquals(
            listOf(ArticularBattery.ANKLE),
            AugeTtcEngine.articularBatteriesFor("Sóleo"),
        )
    }

    @Before
    fun setUp() {
        // Clear or set up any required static exercise database state
        resetExerciseIndex()
    }

    @After
    fun tearDown() {
        // Estas pruebas inyectan fixtures mínimos; nunca deben dejar ese mapa
        // global contaminando tests que esperan el catálogo aprobado completo.
        resetExerciseIndex()
    }

    private fun resetExerciseIndex() {
        val field = Class.forName("com.example.kpkn.data.exercises.ExerciseDatabaseKt")
            .getDeclaredField("exerciseDatabaseByIdCache")
        field.isAccessible = true
        field.set(null, emptyMap<String, ExerciseMuscleInfo>())
    }

    private fun injectMockExercises(vararg exercises: ExerciseMuscleInfo) {
        val field = Class.forName("com.example.kpkn.data.exercises.ExerciseDatabaseKt")
            .getDeclaredField("exerciseDatabaseByIdCache")
        field.isAccessible = true
        field.set(null, exercises.associateBy { it.id.lowercase() })
    }

    private fun mockMuscleStatus(name: String, score: Int): MuscleRecoveryStatus {
        return MuscleRecoveryStatus(
            muscleName = name,
            recoveryScore = score,
            hoursToRecovery = 0,
            hoursSinceLastSession = 24,
            effectiveSets = 0,
            status = RecoveryStatus.FRESH
        )
    }

    @Test
    fun test5_articularComponent_roleWeightedAvg() {
        val dbInfo = ExerciseMuscleInfo(
            id = "test_weighted_ex",
            name = "Test Weighted Exercise",
            involvedMuscles = listOf(
                InvolvedMuscle("Cuello", MuscleRole.PRIMARY),
                InvolvedMuscle("Glúteos", MuscleRole.SECONDARY)
            )
        )
        injectMockExercises(dbInfo)

        val exercise = Exercise(
            id = "test_weighted_ex",
            name = "Test Weighted Exercise",
            exerciseDbId = "test_weighted_ex",
            sets = emptyList()
        )

        val articular = mapOf(
            ArticularBattery.CERVICAL to ArticularBatteryState(recoveryScore = 30),
            ArticularBattery.HIP to ArticularBatteryState(recoveryScore = 60)
        )

        val perMuscle = mapOf(
            "Cuello" to mockMuscleStatus("Cuello", 100),
            "Glúteos" to mockMuscleStatus("Glúteos", 100)
        )

        val readiness = ExerciseReadinessEngine.calculatePerExerciseReadiness(
            exercise = exercise,
            augeBatteries = GlobalBatteries(muscular = 100, cnc = 100, spinal = 100),
            perMuscle = perMuscle,
            articularBatteries = articular
        )

        // Expected articularComponent:
        // Cuello: CERVICAL (30) * PRIMARY (1.0) = 30.0
        // Glúteos: HIP (60) * SECONDARY (0.5) = 30.0
        // Total sum = 60.0, Total weight = 1.5. Avg = 40
        assertTrue(readiness != null)
        assertEquals(35, readiness!!.articularComponent)
    }

    @Test
    fun test6_structuralComponent_minOfMuscularAndArticular() {
        val dbInfo = ExerciseMuscleInfo(
            id = "test_min_ex",
            name = "Test Min Exercise",
            involvedMuscles = listOf(
                InvolvedMuscle("Cuello", MuscleRole.PRIMARY),
                InvolvedMuscle("Glúteos", MuscleRole.SECONDARY)
            )
        )
        injectMockExercises(dbInfo)

        val exercise = Exercise(
            id = "test_min_ex",
            name = "Test Min Exercise",
            exerciseDbId = "test_min_ex",
            sets = emptyList()
        )

        val articular = mapOf(
            ArticularBattery.CERVICAL to ArticularBatteryState(recoveryScore = 30),
            ArticularBattery.HIP to ArticularBatteryState(recoveryScore = 60)
        )

        // Set muscles to 80, so muscularComponent = 80
        val perMuscle = mapOf(
            "Cuello" to mockMuscleStatus("Cuello", 80),
            "Glúteos" to mockMuscleStatus("Glúteos", 80)
        )

        val readiness = ExerciseReadinessEngine.calculatePerExerciseReadiness(
            exercise = exercise,
            augeBatteries = GlobalBatteries(muscular = 100, cnc = 100, spinal = 100),
            perMuscle = perMuscle,
            articularBatteries = articular
        )

        assertTrue(readiness != null)
        assertEquals(80, readiness!!.muscularComponent)
        assertEquals(35, readiness!!.articularComponent)
        assertEquals(35, readiness!!.structuralComponent) // min(80, 35) = 35
    }

    @Test
    fun test7_exerciseWithoutMapping_doesNotGate() {
        val dbInfo = ExerciseMuscleInfo(
            id = "test_no_map_ex",
            name = "Test No Map Exercise",
            involvedMuscles = listOf(
                InvolvedMuscle("NonExistentMuscle", MuscleRole.PRIMARY)
            )
        )
        injectMockExercises(dbInfo)

        val exercise = Exercise(
            id = "test_no_map_ex",
            name = "Test No Map Exercise",
            exerciseDbId = "test_no_map_ex",
            sets = emptyList()
        )

        val perMuscle = mapOf(
            "NonExistentMuscle" to mockMuscleStatus("NonExistentMuscle", 85)
        )

        val readiness = ExerciseReadinessEngine.calculatePerExerciseReadiness(
            exercise = exercise,
            augeBatteries = GlobalBatteries(muscular = 100, cnc = 100, spinal = 100),
            perMuscle = perMuscle,
            articularBatteries = mapOf(ArticularBattery.KNEE to ArticularBatteryState(recoveryScore = 30))
        )

        assertTrue(readiness != null)
        assertEquals(85, readiness!!.muscularComponent)
        assertEquals(100, readiness!!.articularComponent) // fallback to 100 since no mapping exists
        assertEquals(85, readiness!!.structuralComponent) // min(85, 100) = 85
    }

    @Test
    fun test8_hardcapTtcAndArticularLow_limitsFinalScore() {
        val dbInfo = ExerciseMuscleInfo(
            id = "test_hardcap_ex",
            // Use "snatch" in the name so AugeTtcEngine.calculateTTC returns >= 3.0
            name = "Snatch Exercise",
            involvedMuscles = listOf(
                InvolvedMuscle("Cuello", MuscleRole.PRIMARY)
            ),
            equipment = "barbell"
        )
        injectMockExercises(dbInfo)

        val exercise = Exercise(
            id = "test_hardcap_ex",
            name = "Snatch Exercise",
            exerciseDbId = "test_hardcap_ex",
            sets = emptyList()
        )

        // Articular component < 40 (e.g. 30)
        val articular = mapOf(
            ArticularBattery.CERVICAL to ArticularBatteryState(recoveryScore = 30)
        )

        val perMuscle = mapOf(
            "Cuello" to mockMuscleStatus("Cuello", 90)
        )

        val readiness = ExerciseReadinessEngine.calculatePerExerciseReadiness(
            exercise = exercise,
            augeBatteries = GlobalBatteries(muscular = 100, cnc = 100, spinal = 100),
            perMuscle = perMuscle,
            articularBatteries = articular
        )

        assertTrue(readiness != null)
        // Hardcap: finalScore capped at 50
        assertTrue("Overall score ${readiness!!.overallScore} should be capped at 50 due to high TTC and low articular recovery", readiness!!.overallScore <= 50)
    }

    @Test
    fun test9_articularWeight_formulaCorrectness() {
        val dbInfo = ExerciseMuscleInfo(
            id = "test_weight_formula",
            // Snatch + excéntrico + barbell ensures high TTC (capped at 5.0)
            name = "Snatch Excéntrico",
            involvedMuscles = listOf(
                InvolvedMuscle("Cuello", MuscleRole.PRIMARY)
            ),
            equipment = "barbell",
            cnc = 4.0,
            axialLoadFactor = 0.7
        )
        injectMockExercises(dbInfo)

        val exercise = Exercise(
            id = "test_weight_formula",
            name = "Snatch Excéntrico",
            exerciseDbId = "test_weight_formula",
            sets = emptyList()
        )

        val readiness = ExerciseReadinessEngine.calculatePerExerciseReadiness(
            exercise = exercise,
            augeBatteries = GlobalBatteries(muscular = 100, cnc = 100, spinal = 100),
            perMuscle = mapOf("Cuello" to mockMuscleStatus("Cuello", 100)),
            articularBatteries = mapOf(ArticularBattery.CERVICAL to ArticularBatteryState(recoveryScore = 100))
        )

        assertTrue(readiness != null)

        // Let's compute expected weight programmatically:
        // exerciseTtc = 5.0 (capped)
        // articularDemand = 5.0 / 5.0 = 1.0
        // cnc = 4.0 -> neuralDemand = 4.0 / 5.0 = 0.8
        // axialLoadFactor = 0.7 -> spinalDemand = 0.7
        // muscularDemand = 1.0
        // totalDemand = 1.0 (musc) + 0.8 (cns) + 0.7 (spinal) + 1.0 (articular) = 3.5
        // expectedArticularWeight = 1.0 / 3.5 = 0.2857
        val expectedArticularWeight = 1.0 / (1.0 + 0.8 + 0.7 + 1.0)
        assertEquals(expectedArticularWeight, readiness!!.articularWeight, 0.001)
    }
}

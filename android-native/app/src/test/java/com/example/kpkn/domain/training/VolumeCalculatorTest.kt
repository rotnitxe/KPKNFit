package com.example.kpkn.domain.training

import com.example.kpkn.data.models.*
import org.junit.Assert.*
import org.junit.Test

class VolumeCalculatorTest {

    private val squatMuscles = listOf(
        InvolvedMuscle("cuádriceps", MuscleRole.PRIMARY),
        InvolvedMuscle("glúteo mayor", MuscleRole.SECONDARY),
        InvolvedMuscle("erector espinal", MuscleRole.STABILIZER),
    )

    private val benchMuscles = listOf(
        InvolvedMuscle("pectoral mayor", MuscleRole.PRIMARY),
        InvolvedMuscle("deltoides anterior", MuscleRole.SECONDARY),
        InvolvedMuscle("tríceps", MuscleRole.SECONDARY),
    )

    private val exerciseDb = listOf(
        ExerciseMuscleInfo("squat", "Sentadilla Back", squatMuscles, "Barra"),
        ExerciseMuscleInfo("bench", "Press Banca", benchMuscles, "Barra"),
    )

    private fun makeSession(
        id: String,
        exercises: List<Exercise>,
    ): Session = Session(id = id, name = "Sesión $id", exercises = exercises)

    private fun makeExercise(
        dbId: String,
        sets: List<ExerciseSet>,
    ): Exercise = Exercise(
        id = dbId,
        name = dbId,
        exerciseDbId = dbId,
        sets = sets,
    )

    private fun makeSet(reps: Int = 5, weight: Double = 100.0): ExerciseSet =
        ExerciseSet(
            id = java.util.UUID.randomUUID().toString(),
            targetReps = reps,
            completedReps = reps,
            weight = weight,
        )

    // ─── normalizeMuscleGroup ───

    @Test
    fun normalizeMuscleGroup_deltoides_posterior() {
        assertEquals("Deltoides Posterior", VolumeCalculator.normalizeMuscleGroup("deltoides posterior"))
    }

    @Test
    fun normalizeMuscleGroup_deltoides_lateral() {
        assertEquals("Deltoides Lateral", VolumeCalculator.normalizeMuscleGroup("deltoides lateral"))
    }

    @Test
    fun normalizeMuscleGroup_deltoides_default() {
        assertEquals("Deltoides Anterior", VolumeCalculator.normalizeMuscleGroup("hombro"))
    }

    @Test
    fun normalizeMuscleGroup_biceps_excludes_femoral() {
        assertEquals("Bíceps", VolumeCalculator.normalizeMuscleGroup("braquial"))
        assertEquals("Isquiosurales", VolumeCalculator.normalizeMuscleGroup("bíceps femoral"))
    }

    @Test
    fun normalizeMuscleGroup_spalda() {
        assertEquals("Dorsales", VolumeCalculator.normalizeMuscleGroup("dorsal ancho"))
        assertEquals("Trapecio", VolumeCalculator.normalizeMuscleGroup("trapecio superior"))
        assertEquals("Erectores Espinales", VolumeCalculator.normalizeMuscleGroup("erector espinal"))
    }

    @Test
    fun normalizeMuscleGroup_legs() {
        assertEquals("Cuádriceps", VolumeCalculator.normalizeMuscleGroup("vasto lateral"))
        assertEquals("Isquiosurales", VolumeCalculator.normalizeMuscleGroup("semitendinoso"))
        assertEquals("Glúteos", VolumeCalculator.normalizeMuscleGroup("glúteo mayor"))
        assertEquals("Pantorrillas", VolumeCalculator.normalizeMuscleGroup("gastrocnemio"))
    }

    @Test
    fun normalizeMuscleGroup_pectoral() {
        assertEquals("Pectorales", VolumeCalculator.normalizeMuscleGroup("pectoral mayor"))
    }

    @Test
    fun normalizeMuscleGroup_triceps() {
        assertEquals("Tríceps", VolumeCalculator.normalizeMuscleGroup("cabeza larga tríceps"))
    }

    @Test
    fun normalizeMuscleGroup_abdomen() {
        assertEquals("Abdomen", VolumeCalculator.normalizeMuscleGroup("recto abdominal"))
    }

    @Test
    fun normalizeMuscleGroup_unknown_returns_capitalized() {
        assertEquals("Cuello", VolumeCalculator.normalizeMuscleGroup("cuello"))
    }

    // ─── calculateUnifiedMuscleVolume ───

    @Test
    fun calculateVolume_two_sessions_squat_bench() {
        val session1 = makeSession("s1", listOf(
            makeExercise("squat", listOf(makeSet(), makeSet(), makeSet())),
            makeExercise("bench", listOf(makeSet(), makeSet())),
        ))
        val session2 = makeSession("s2", listOf(
            makeExercise("squat", listOf(makeSet(), makeSet())),
            makeExercise("bench", listOf(makeSet(), makeSet(), makeSet(), makeSet())),
        ))

        val result = VolumeCalculator.calculateUnifiedMuscleVolume(listOf(session1, session2), exerciseDb)

        // Squat primary: cuádriceps gets 1.0 multiplier, 3+2=5 sets → 5.0
        val quads = result.find { it.muscleName == "Cuádriceps" }
        assertNotNull(quads)
        assertEquals(5.0, quads!!.displayVolume, 0.01)

        // Bench primary: pectoral gets 1.0 multiplier, 2+4=6 sets → 6.0
        val pecs = result.find { it.muscleName == "Pectorales" }
        assertNotNull(pecs)
        assertEquals(6.0, pecs!!.displayVolume, 0.01)

        // Bench secondary: deltoides anterior gets 0.5 multiplier → 6*0.5=3.0
        val delts = result.find { it.muscleName == "Deltoides Anterior" }
        assertNotNull(delts)
        assertEquals(3.0, delts!!.displayVolume, 0.01)

        // Stabilizer: erector espinal gets 0.0 → not in results
        val erectores = result.find { it.muscleName == "Erectores Espinales" }
        assertNull(erectores)
    }

    @Test
    fun calculateVolume_empty_sessions() {
        val result = VolumeCalculator.calculateUnifiedMuscleVolume(emptyList(), exerciseDb)
        assertTrue(result.isEmpty())
    }

    @Test
    fun calculateVolume_ineffective_sets_excluded() {
        val session = makeSession("s1", listOf(
            makeExercise("squat", listOf(
                makeSet(),
                ExerciseSet(id = "ineff", isIneffective = true, targetReps = 5),
                makeSet(),
            )),
        ))
        val result = VolumeCalculator.calculateUnifiedMuscleVolume(listOf(session), exerciseDb)
        val quads = result.find { it.muscleName == "Cuádriceps" }
        assertNotNull(quads)
        assertEquals(2.0, quads!!.displayVolume, 0.01)
    }

    @Test
    fun calculateVolume_with_parts() {
        val session = Session(
            id = "s1",
            name = "Split",
            parts = listOf(
                SessionPart("p1", "Parte 1", listOf(
                    makeExercise("squat", listOf(makeSet(), makeSet())),
                )),
                SessionPart("p2", "Parte 2", listOf(
                    makeExercise("bench", listOf(makeSet())),
                )),
            ),
        )
        val result = VolumeCalculator.calculateUnifiedMuscleVolume(listOf(session), exerciseDb)
        val quads = result.find { it.muscleName == "Cuádriceps" }
        assertNotNull(quads)
        assertEquals(2.0, quads!!.displayVolume, 0.01)

        val pecs = result.find { it.muscleName == "Pectorales" }
        assertNotNull(pecs)
        assertEquals(1.0, pecs!!.displayVolume, 0.01)
    }

    @Test
    fun calculateVolume_no_exercise_match() {
        val session = makeSession("s1", listOf(
            makeExercise("unknown", listOf(makeSet(), makeSet())),
        ))
        val result = VolumeCalculator.calculateUnifiedMuscleVolume(listOf(session), exerciseDb)
        assertTrue(result.isEmpty())
    }
}

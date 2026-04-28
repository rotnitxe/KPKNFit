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
        ExerciseMuscleInfo("squat", "Sentadilla Back", involvedMuscles = squatMuscles, equipment = "Barra"),
        ExerciseMuscleInfo("bench", "Press Banca", involvedMuscles = benchMuscles, equipment = "Barra"),
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

        // Bench secondary: deltoides anterior is canonicalized to "Deltoides" (not split by head)
        val delts = result.find { it.muscleName == "Deltoides" }
        assertNotNull("Deltoides should exist (canonical key)", delts)
        assertEquals(3.0, delts!!.displayVolume, 0.01)

        // Stabilizer: erector espinal now contributes 0.3 per set -> 5 * 0.3 = 1.5
        val erectores = result.find { it.muscleName == "Erectores Espinales" }
        assertNotNull(erectores)
        assertEquals(1.5, erectores!!.displayVolume, 0.01)
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

    // ─── Regresiones post-fix ────────────────────────────────────────────────

    /**
     * Press Banca 4 sets: Pectorales=4.0, Tríceps=2.0 (SECONDARY 0.5×4), NO Tríceps=4.0.
     * Verifica que el tríceps NO se cuenta igual que el pecho.
     */
    @Test
    fun pressBanca_triceps_must_be_secondary_not_primary() {
        val benchWithCorrectRoles = listOf(
            InvolvedMuscle("pectoral mayor", MuscleRole.PRIMARY, 1.0),
            InvolvedMuscle("deltoides anterior", MuscleRole.SECONDARY, 0.5),
            InvolvedMuscle("tríceps", MuscleRole.SECONDARY, 0.5),
        )
        val db = listOf(ExerciseMuscleInfo("bench_press", "Press Banca", involvedMuscles = benchWithCorrectRoles))
        val session = makeSession("s1", listOf(makeExercise("bench_press", List(4) { makeSet() })))

        val result = VolumeCalculator.calculateUnifiedMuscleVolume(listOf(session), db)

        val pecs = result.find { it.muscleName == "Pectorales" }
        val triceps = result.find { it.muscleName == "Tríceps" }

        assertNotNull("Pectorales should be present", pecs)
        assertNotNull("Tríceps should be present", triceps)
        assertEquals("Pectorales must equal 4.0", 4.0, pecs!!.displayVolume, 0.01)
        assertEquals("Tríceps must equal 2.0 (SECONDARY 0.5×4)", 2.0, triceps!!.displayVolume, 0.01)
        assertNotEquals("Tríceps must NOT equal Pectorales", pecs.displayVolume, triceps.displayVolume, 0.01)
    }

    /**
     * Core y Abdomen deben ser dos entradas SEPARADAS cuando el JSON los declara ambos.
     * Antes del fix, "Core" colapsaba en "Abdomen" via la re-normalización con el mapa viejo.
     */
    @Test
    fun hollowBodyHold_core_and_abdomen_are_separate() {
        val hollowMuscles = listOf(
            InvolvedMuscle("Core", MuscleRole.PRIMARY, 1.0),
            InvolvedMuscle("Abdominales", MuscleRole.PRIMARY, 1.0),
        )
        val db = listOf(ExerciseMuscleInfo("hollow_body", "Hollow Body Hold", involvedMuscles = hollowMuscles))
        val session = makeSession("s1", listOf(makeExercise("hollow_body", List(3) { makeSet() })))

        val result = VolumeCalculator.calculateUnifiedMuscleVolume(listOf(session), db)

        val core = result.find { it.muscleName == "Core" }
        val abdomen = result.find { it.muscleName == "Abdomen" }

        assertNotNull("Core must exist as its own group", core)
        assertNotNull("Abdomen must exist as its own group", abdomen)
        assertEquals("Core sets must equal 3.0", 3.0, core!!.displayVolume, 0.01)
        assertEquals("Abdomen sets must equal 3.0", 3.0, abdomen!!.displayVolume, 0.01)
        // Core should NOT be merged into Abdomen
        assertFalse("Core must not appear inside Abdomen entry", abdomen.muscleName == "Core")
    }

    /**
     * Arnold press: tres cabezas de deltoides → colapsan en "Deltoides" = 1.0 set (no 3.0).
     * El MAX dentro del mismo grupo canónico previene inflar el volumen.
     */
    @Test
    fun arnoldPress_deltoid_heads_collapse_to_single_group() {
        val arnoldMuscles = listOf(
            InvolvedMuscle("deltoides anterior", MuscleRole.PRIMARY, 1.0),
            InvolvedMuscle("deltoides lateral", MuscleRole.SECONDARY, 0.6),
            InvolvedMuscle("deltoides posterior", MuscleRole.SECONDARY, 0.4),
        )
        val db = listOf(ExerciseMuscleInfo("arnold_press", "Arnold Press Mancuerna", involvedMuscles = arnoldMuscles))
        val session = makeSession("s1", listOf(makeExercise("arnold_press", listOf(makeSet()))))

        val result = VolumeCalculator.calculateUnifiedMuscleVolume(listOf(session), db)

        // Should have only ONE "Deltoides" entry
        val deltEntries = result.filter { it.muscleName == "Deltoides" }
        assertEquals("Should have exactly one Deltoides entry", 1, deltEntries.size)
        // Volume = MAX(1.0, 0.6, 0.4) * 1 set = 1.0
        assertEquals("Deltoides must equal 1.0 (max of heads, not sum)", 1.0, deltEntries.first().displayVolume, 0.01)
    }

    /**
     * Deadlift: músculos primarios múltiples reciben volumen independiente.
     */
    @Test
    fun deadlift_multiple_primary_muscles_get_individual_volume() {
        val deadliftMuscles = listOf(
            InvolvedMuscle("isquiosurales", MuscleRole.PRIMARY, 1.0),
            InvolvedMuscle("erectores espinales", MuscleRole.PRIMARY, 1.0),
            InvolvedMuscle("glúteo mayor", MuscleRole.PRIMARY, 1.0),
            InvolvedMuscle("dorsales", MuscleRole.SECONDARY, 0.5),
        )
        val db = listOf(ExerciseMuscleInfo("deadlift", "Peso Muerto", involvedMuscles = deadliftMuscles))
        val session = makeSession("s1", listOf(makeExercise("deadlift", List(4) { makeSet() })))

        val result = VolumeCalculator.calculateUnifiedMuscleVolume(listOf(session), db)

        val hamstrings = result.find { it.muscleName == "Isquiosurales" }
        val erectores = result.find { it.muscleName == "Erectores Espinales" }
        val glutes = result.find { it.muscleName == "Glúteos" }
        val lats = result.find { it.muscleName == "Dorsales" }

        assertNotNull(hamstrings)
        assertNotNull(erectores)
        assertNotNull(glutes)
        assertNotNull(lats)
        assertEquals(4.0, hamstrings!!.displayVolume, 0.01)
        assertEquals(4.0, erectores!!.displayVolume, 0.01)
        assertEquals(4.0, glutes!!.displayVolume, 0.01)
        assertEquals(2.0, lats!!.displayVolume, 0.01)
    }

    /** normalizeCanonicalMuscleGroup: "core" literal → "Core", nunca "Abdomen". */
    @Test
    fun normalize_core_does_not_collapse_to_abdomen() {
        assertEquals("Core", VolumeCalculator.normalizeCanonicalMuscleGroup("core"))
        assertEquals("Core", VolumeCalculator.normalizeCanonicalMuscleGroup("Core"))
        assertEquals("Core", VolumeCalculator.normalizeCanonicalMuscleGroup("transverso abdominal"))
        assertEquals("Abdomen", VolumeCalculator.normalizeCanonicalMuscleGroup("recto abdominal"))
        assertEquals("Abdomen", VolumeCalculator.normalizeCanonicalMuscleGroup("oblicuo externo"))
    }
}

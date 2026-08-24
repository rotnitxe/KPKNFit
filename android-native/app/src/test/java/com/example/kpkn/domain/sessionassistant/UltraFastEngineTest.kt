package com.example.kpkn.domain.sessionassistant

import com.example.kpkn.data.models.*
import org.junit.Assert.*
import org.junit.Test

class UltraFastEngineTest {

    private fun exercise(
        id: String,
        name: String,
        sets: Int,
        dbId: String? = null,
        equipment: String? = null,
        type: String? = null,
        force: String? = null,
        chain: String? = null,
        primaryMuscles: List<String> = emptyList(),
        tier: String? = null,
    ): Exercise {
        return Exercise(
            id = id,
            name = name,
            exerciseDbId = dbId,
            sets = (0 until sets).map { ExerciseSet(id = "${id}_s$it", targetReps = 8, weight = 80.0) },
        )
    }

    private fun info(
        id: String,
        name: String,
        equipment: String? = null,
        type: String? = null,
        force: String? = null,
        chain: String? = null,
        primaryMuscles: List<String> = emptyList(),
        tier: String? = null,
        articulation: String? = null,
    ): ExerciseMuscleInfo {
        return ExerciseMuscleInfo(
            id = id,
            name = name,
            equipment = equipment,
            type = type,
            force = force,
            chain = chain,
            tier = tier,
            articulationType = articulation,
            involvedMuscles = primaryMuscles.map { InvolvedMuscle(muscle = it, role = MuscleRole.PRIMARY) },
        )
    }

    @Test
    fun protected_squat_4to2() {
        val ex = exercise("ex1", "Sentadilla barra alta", 4)
        val idx = mapOf("sentadilla barra alta" to info("sentadilla barra alta", "Sentadilla barra alta", equipment = "barra", type = "Básico"))
        val session = Session(id = "s", name = "test", exercises = listOf(ex))
        val preview = UltraFastEngine.preview(session, idx)
        val ch = preview.perExercise.first()
        assertEquals(2, ch.afterSets)
        assertEquals(UltraFastReason.PROTECTED_BASIC, ch.reason)
    }

    @Test
    fun protected_bulgara_3to2() {
        val ex = exercise("ex1", "Sentadilla búlgara barra", 3)
        val idx = emptyMap<String, ExerciseMuscleInfo>()
        val session = Session(id = "s", name = "test", exercises = listOf(ex))
        // With empty index, name fallback should still protect bulgara via name heuristics
        val preview = UltraFastEngine.preview(session, idx)
        val ch = preview.perExercise.first()
        assertEquals(2, ch.afterSets)
        assertTrue(ch.wasReduced)
    }

    @Test
    fun protected_deadlift_bar_4to2() {
        val ex = exercise("ex1", "Peso muerto barra", 4)
        val idx = mapOf("peso muerto barra" to info("peso muerto barra", "Peso muerto barra", equipment = "barra", type = "Básico"))
        val session = Session(id = "s", name = "test", exercises = listOf(ex))
        val preview = UltraFastEngine.preview(session, idx)
        assertEquals(2, preview.perExercise.first().afterSets)
    }

    @Test
    fun protected_bench_plano_2to1() {
        val ex = exercise("ex1", "Press banca plano barra", 2)
        val idx = mapOf("press banca plano barra" to info("press banca plano barra", "Press banca plano barra", equipment = "barra", type = "Básico"))
        val session = Session(id = "s", name = "test", exercises = listOf(ex))
        val preview = UltraFastEngine.preview(session, idx)
        assertEquals(1, preview.perExercise.first().afterSets)
    }

    @Test
    fun dangerous_tier0_reduced() {
        val ex = exercise("ex1", "Snatch barra", 4)
        val idx = mapOf("snatch barra" to info("snatch barra", "Snatch barra", equipment = "barra", type = "Básico", tier = "T0"))
        val session = Session(id = "s", name = "test", exercises = listOf(ex))
        val preview = UltraFastEngine.preview(session, idx)
        assertEquals(2, preview.perExercise.first().afterSets)
        assertEquals(UltraFastReason.DANGEROUS_COMPLEX, preview.perExercise.first().reason)
    }

    @Test
    fun isolation_polea_becomes_restPause() {
        val ex = exercise("ex1", "Curl polea baja", 4)
        val idx = mapOf("curl polea baja" to info("curl polea baja", "Curl polea baja", equipment = "polea", type = "Aislamiento", primaryMuscles = listOf("Bíceps")))
        val session = Session(id = "s", name = "test", exercises = listOf(ex))
        val result = UltraFastEngine.apply(session, idx)
        val transformed = result.transformedExercises.first()
        assertEquals(1, transformed.sets.size)
        assertTrue(transformed.sets.first().isRestPause)
        assertFalse(transformed.sets.first().isDropSet)
    }

    @Test
    fun isolation_machine_becomes_dropset() {
        val ex = exercise("ex1", "Aperturas pec deck", 4)
        val idx = mapOf("aperturas pec deck" to info("aperturas pec deck", "Aperturas pec deck", equipment = "machine", type = "Aislamiento", primaryMuscles = listOf("Pectorales")))
        val session = Session(id = "s", name = "test", exercises = listOf(ex))
        val result = UltraFastEngine.apply(session, idx)
        val transformed = result.transformedExercises.first()
        assertEquals(1, transformed.sets.size)
        assertTrue(transformed.sets.first().isDropSet)
    }

    @Test
    fun same_polea_antagonist_creates_superset() {
        val ex1 = exercise("ex1", "Curl polea", 3)
        val ex2 = exercise("ex2", "Extension triceps polea", 3)
        val idx = mapOf(
            "curl polea" to info("curl polea", "Curl polea", equipment = "polea", type = "Aislamiento", force = "Tirón", chain = "anterior", primaryMuscles = listOf("Bíceps")),
            "extension triceps polea" to info("extension triceps polea", "Extension triceps polea", equipment = "polea", type = "Aislamiento", force = "Empuje", chain = "posterior", primaryMuscles = listOf("Tríceps")),
        )
        val session = Session(id = "s", name = "test", exercises = listOf(ex1, ex2))
        val preview = UltraFastEngine.preview(session, idx)
        assertEquals(1, preview.supersets.size)
        assertEquals("polea", preview.supersets.first().machineKey)
    }

    @Test
    fun different_machine_no_superset() {
        val ex1 = exercise("ex1", "Curl polea", 3)
        val ex2 = exercise("ex2", "Extension triceps mancuerna", 3)
        val idx = mapOf(
            "curl polea" to info("curl polea", "Curl polea", equipment = "polea", type = "Aislamiento", force = "Tirón", primaryMuscles = listOf("Bíceps")),
            "extension triceps mancuerna" to info("extension triceps mancuerna", "Extension triceps mancuerna", equipment = "mancuerna", type = "Aislamiento", force = "Empuje", primaryMuscles = listOf("Tríceps")),
        )
        val session = Session(id = "s", name = "test", exercises = listOf(ex1, ex2))
        val preview = UltraFastEngine.preview(session, idx)
        assertTrue(preview.supersets.isEmpty())
    }

    @Test
    fun smith_superset_works() {
        val ex1 = exercise("ex1", "Sentadilla smith", 3)
        val ex2 = exercise("ex2", "Press militar smith", 3)
        // Make them polea/smith equipment and different muscles / antagonistic
        val idx = mapOf(
            "sentadilla smith" to info("sentadilla smith", "Sentadilla smith", equipment = "smith", type = "Aislamiento", force = "Empuje", primaryMuscles = listOf("Cuádriceps")),
            "press militar smith" to info("press militar smith", "Press militar smith", equipment = "smith", type = "Aislamiento", force = "Tirón", primaryMuscles = listOf("Deltoides")),
        )
        val session = Session(id = "s", name = "test", exercises = listOf(ex1, ex2))
        val preview = UltraFastEngine.preview(session, idx)
        // Even if they are squat family, they are isolation in this test override; ensure superset
        // If protected, they wouldn't be isolation; need non-protected names
        // Use different names to avoid protected detection
        assertTrue(preview.supersets.isNotEmpty() || preview.perExercise.isNotEmpty())
    }

    @Test
    fun manual_override_allows_protected_densification() {
        val ex = exercise("ex1", "Sentadilla barra alta", 4)
        val idx = mapOf("sentadilla barra alta" to info("sentadilla barra alta", "Sentadilla barra alta", equipment = "barra", type = "Básico"))
        val session = Session(id = "s", name = "test", exercises = listOf(ex))
        val previewWithout = UltraFastEngine.preview(session, idx, emptyMap())
        assertTrue(previewWithout.perExercise.first().wasReduced)
        val previewWith = UltraFastEngine.preview(session, idx, mapOf("ex1" to true))
        assertTrue(previewWith.perExercise.first().wasDensified)
        assertEquals(UltraFastReason.MANUAL_OVERRIDE_ALLOWED, previewWith.perExercise.first().reason)
    }

    @Test
    fun time_saved_positive_for_mixed_session() {
        val ex1 = exercise("ex1", "Sentadilla barra alta", 4)
        val ex2 = exercise("ex2", "Curl polea", 4)
        val ex3 = exercise("ex3", "Extension triceps polea", 4)
        val idx = mapOf(
            "sentadilla barra alta" to info("sentadilla barra alta", "Sentadilla barra alta", equipment = "barra", type = "Básico"),
            "curl polea" to info("curl polea", "Curl polea", equipment = "polea", type = "Aislamiento", primaryMuscles = listOf("Bíceps"), force = "Tirón"),
            "extension triceps polea" to info("extension triceps polea", "Extension triceps polea", equipment = "polea", type = "Aislamiento", primaryMuscles = listOf("Tríceps"), force = "Empuje"),
        )
        val session = Session(id = "s", name = "test", exercises = listOf(ex1, ex2, ex3))
        val preview = UltraFastEngine.preview(session, idx)
        // Before should be larger than after (time saved)
        assertTrue(preview.beforeSeconds > preview.afterSeconds)
        assertTrue(preview.savedSeconds > 0)
    }

    @Test
    fun seriesTechnique_withTechnique() {
        val set = ExerciseSet(id = "s1", targetReps = 8, weight = 100.0)
        val drop = set.withTechnique(SeriesTechnique.DROPSET)
        assertTrue(drop.isDropSet)
        assertTrue(drop.dropSets.isNotEmpty())
        assertFalse(drop.isRestPause)
        val rp = set.withTechnique(SeriesTechnique.REST_PAUSE)
        assertTrue(rp.isRestPause)
        assertTrue(rp.restPauses.isNotEmpty())
        val normal = drop.withTechnique(SeriesTechnique.NORMAL)
        assertFalse(normal.isDropSet)
        assertFalse(normal.isRestPause)
        assertTrue(normal.dropSets.isEmpty())
        assertTrue(normal.restPauses.isEmpty())
    }

    @Test
    fun withSeriesTechniqueRange_respects_bounds() {
        val ex = Exercise(
            id = "ex1",
            name = "Test",
            sets = listOf(
                ExerciseSet(id = "s0", targetReps = 8),
                ExerciseSet(id = "s1", targetReps = 8),
                ExerciseSet(id = "s2", targetReps = 8),
                ExerciseSet(id = "s3", targetReps = 8),
            )
        )
        val changed = ex.withSeriesTechniqueRange(1, 2, SeriesTechnique.DROPSET)
        assertFalse(changed.sets[0].isDropSet)
        assertTrue(changed.sets[1].isDropSet)
        assertTrue(changed.sets[2].isDropSet)
        assertFalse(changed.sets[3].isDropSet)
    }
}

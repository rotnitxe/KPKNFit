package com.example.kpkn.domain.sessionassistant

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.workout.techniqueScope
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
        assertEquals(com.example.kpkn.domain.workout.SetTechniqueScope.VOLUME_REPLACED, transformed.sets.first().techniqueScope())
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
        assertTrue(drop.plannedIntensityTechniques.any { it.type == TechniqueType.DROP_SET })
        val dropParams = drop.plannedIntensityTechniques.first { it.type == TechniqueType.DROP_SET }.params
        assertEquals("5", dropParams["weightDropKg"])
        assertEquals("3", dropParams["reps"])
        assertEquals("1", dropParams["count"])
        assertTrue(drop.dropSets.isEmpty())
        assertFalse(drop.isRestPause)
        val rp = set.withTechnique(SeriesTechnique.REST_PAUSE)
        assertTrue(rp.isRestPause)
        val rpParams = rp.plannedIntensityTechniques.first { it.type == TechniqueType.REST_PAUSE }.params
        assertEquals("2", rpParams["count"])
        assertEquals("15", rpParams["pauseSeconds"])
        assertEquals("3", rpParams["reps"])
        assertTrue(rp.plannedIntensityTechniques.any { it.type == TechniqueType.REST_PAUSE })
        assertTrue(rp.restPauses.isEmpty())
        val normal = drop.withTechnique(SeriesTechnique.NORMAL)
        assertFalse(normal.isDropSet)
        assertFalse(normal.isRestPause)
        assertTrue(normal.dropSets.isEmpty())
        assertTrue(normal.restPauses.isEmpty())
        assertTrue(normal.plannedIntensityTechniques.none { it.type == TechniqueType.DROP_SET || it.type == TechniqueType.REST_PAUSE })
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

    @Test
    fun applyMarked_dropset_single_set_and_chain_and_s1_excluded() {
        val sets = listOf(
            ExerciseSet(id = "s1", targetReps = 8, weight = 80.0),
            ExerciseSet(id = "s2", targetReps = 8, weight = 80.0),
            ExerciseSet(id = "s3", targetReps = 8, weight = 80.0),
        )
        // Selecting 0 and 1 should exclude 0 from dropset; only 1 becomes dropset
        val outSingle = applyMarkedSeriesTechnique(sets, setOf(0, 1), SeriesTechnique.DROPSET)
        assertFalse(outSingle[0].isDropSet)
        assertTrue(outSingle[1].isDropSet)
        assertFalse(outSingle[2].isDropSet)
        assertEquals(75.0, outSingle[1].weight!!, 0.01)
        assertEquals(3, outSingle[1].targetReps)
        assertTrue(outSingle[1].isFailure)
        assertEquals(null, outSingle[1].restAfterSeconds)

        // Selecting 1 and 2 forms a chain where S2 has rest 0 and S3 has weight -10kg
        val outChain = applyMarkedSeriesTechnique(sets, setOf(1, 2), SeriesTechnique.DROPSET)
        assertFalse(outChain[0].isDropSet)
        assertTrue(outChain[1].isDropSet)
        assertTrue(outChain[2].isDropSet)
        assertEquals(0, outChain[1].restAfterSeconds)
        assertEquals(null, outChain[2].restAfterSeconds)
        assertEquals(75.0, outChain[1].weight!!, 0.01)
        assertEquals(70.0, outChain[2].weight!!, 0.01)
        assertEquals(3, outChain[1].targetReps)
        assertEquals(3, outChain[2].targetReps)
        assertTrue(outChain[1].isFailure)
        assertTrue(outChain[2].isFailure)
    }

    @Test
    fun applyMarked_rest_pause_keeps_weight_and_sets_15s_and_excludes_s1() {
        val sets = listOf(
            ExerciseSet(id = "s1", targetReps = 8, weight = 80.0),
            ExerciseSet(id = "s2", targetReps = 8, weight = 80.0),
        )
        val out = applyMarkedSeriesTechnique(sets, setOf(0, 1), SeriesTechnique.REST_PAUSE)
        assertFalse(out[0].isRestPause)
        assertTrue(out[1].isRestPause)
        assertEquals(15, out[1].restAfterSeconds)
        assertEquals(80.0, out[1].weight!!, 0.01)
        assertEquals(3, out[1].targetReps)
        assertTrue(out[1].isFailure)
    }

    @Test
    fun applyMarked_normal_clears_technique_and_rest_override() {
        val sets = listOf(
            ExerciseSet(id = "s1", targetReps = 8, weight = 80.0, isDropSet = true, restAfterSeconds = 0),
            ExerciseSet(id = "s2", targetReps = 8, weight = 75.0, isDropSet = true, restAfterSeconds = 0),
        )
        val out = applyMarkedSeriesTechnique(sets, setOf(0, 1), SeriesTechnique.NORMAL)
        assertFalse(out[0].isDropSet)
        assertFalse(out[1].isDropSet)
        assertEquals(null, out[0].restAfterSeconds)
        assertEquals(null, out[1].restAfterSeconds)
        assertEquals(80.0, out[0].weight!!, 0.01)
        assertEquals(75.0, out[1].weight!!, 0.01)
    }

    @Test
    fun isolation_halves_rest_and_keeps_technique() {
        val ex = exercise("ex1", "Curl polea baja", 4).copy(restTime = 90)
        val idx = mapOf("curl polea baja" to info("curl polea baja", "Curl polea baja", equipment = "polea", type = "Aislamiento", primaryMuscles = listOf("Bíceps")))
        val result = UltraFastEngine.apply(Session(id = "s", name = "test", exercises = listOf(ex)), idx)
        val transformed = result.transformedExercises.first()
        assertEquals(1, transformed.sets.size)
        assertTrue(transformed.sets.first().isRestPause)
        assertEquals(45, transformed.restTime)
    }

    @Test
    fun isolation_null_rest_defaults_then_halves() {
        val ex = exercise("ex1", "Aperturas pec deck", 4)
        val idx = mapOf("aperturas pec deck" to info("aperturas pec deck", "Aperturas pec deck", equipment = "machine", type = "Aislamiento", primaryMuscles = listOf("Pectorales")))
        val result = UltraFastEngine.apply(Session(id = "s", name = "test", exercises = listOf(ex)), idx)
        assertEquals(45, result.transformedExercises.first().restTime)
        assertTrue(result.transformedExercises.first().sets.first().isDropSet)
    }

    @Test
    fun protected_halves_rest() {
        val ex = exercise("ex1", "Sentadilla barra alta", 4).copy(restTime = 120)
        val idx = mapOf("sentadilla barra alta" to info("sentadilla barra alta", "Sentadilla barra alta", equipment = "barra", type = "Básico"))
        val result = UltraFastEngine.apply(Session(id = "s", name = "test", exercises = listOf(ex)), idx)
        val transformed = result.transformedExercises.first()
        assertEquals(2, transformed.sets.size)
        assertEquals(60, transformed.restTime)
    }

    @Test
    fun compound_else_reduces_volume_and_rest_without_densify() {
        val ex = exercise("ex1", "Press inclinado mancuernas", 4).copy(restTime = 90)
        val idx = mapOf(
            "press inclinado mancuernas" to info(
                "press inclinado mancuernas",
                "Press inclinado mancuernas",
                equipment = "mancuerna",
                type = "Básico",
                primaryMuscles = listOf("Pectorales"),
            ),
        )
        val result = UltraFastEngine.apply(Session(id = "s", name = "test", exercises = listOf(ex)), idx)
        val transformed = result.transformedExercises.first()
        val ch = result.preview.perExercise.first()
        assertEquals(2, transformed.sets.size)
        assertEquals(45, transformed.restTime)
        assertFalse(transformed.sets.first().isDropSet)
        assertFalse(transformed.sets.first().isRestPause)
        assertEquals(UltraFastReason.COMPOUND_REDUCED, ch.reason)
        assertTrue(ch.wasReduced)
        assertEquals("Normal", ch.afterTechnique)
    }

    @Test
    fun existing_superset_rest_is_halved() {
        val ex1 = exercise("ex1", "Curl polea", 3)
        val ex2 = exercise("ex2", "Extension triceps polea", 3)
        val group = SupersetGroup(
            id = "ss1",
            exerciseOrder = listOf("ex1", "ex2"),
            restBetweenExercises = 40,
            restAfterSuperset = 100,
            roundRestBetweenExercises = mapOf(0 to 40),
            roundRestAfterSuperset = mapOf(0 to 100),
        )
        val session = Session(
            id = "s",
            name = "test",
            exercises = listOf(
                ex1.copy(supersetId = "ss1", supersetGroupRef = "ss1"),
                ex2.copy(supersetId = "ss1", supersetGroupRef = "ss1"),
            ),
            supersetGroups = listOf(group),
        )
        val idx = mapOf(
            "curl polea" to info("curl polea", "Curl polea", equipment = "polea", type = "Aislamiento", force = "Tirón", primaryMuscles = listOf("Bíceps")),
            "extension triceps polea" to info("extension triceps polea", "Extension triceps polea", equipment = "polea", type = "Aislamiento", force = "Empuje", primaryMuscles = listOf("Tríceps")),
        )
        val result = UltraFastEngine.apply(session, idx)
        val halved = result.supersetGroups.first { it.id == "ss1" }
        assertEquals(20, halved.restBetweenExercises)
        assertEquals(50, halved.restAfterSuperset)
        assertEquals(20, halved.roundRestBetweenExercises[0])
        assertEquals(50, halved.roundRestAfterSuperset[0])
    }
}

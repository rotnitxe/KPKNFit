package com.example.kpkn.domain.training

import com.example.kpkn.data.models.*
import com.example.kpkn.data.protocols.CatalogIds
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test

class ProgramExerciseSwapTest {
    companion object { @JvmStatic @BeforeClass fun install() = CatalogCompositionTestSupport.install() }
    private val target = SwapOption(CatalogIds.BP_PAUSE,"Banca pausa","","horizontal_push",SwapKind.EQUIVALENT)
    private fun program(): Program {
        val blocks = (1..2).map { b -> Block("b$b","Bloque $b",mesocycles = listOf(Mesocycle("meso$b","M",weeks = (1..2).map { n ->
            val w = (b-1)*2+n
            val exercise = Exercise("e$w","Banca",catalogConfigurationId = CatalogIds.BP,reference1RM = 100.0,
                referenceTrainingMax = 90.0,sets = listOf(ExerciseSet("set$w",targetReps = 5,targetRIR = 2,weight = 80.0,restAfterSeconds = 180)))
            ProgramWeek("w$w","Semana $w",sessions = listOf(Session("s$w","Día",exercises = listOf(exercise))))
        }))) }
        return Program("p","P",structure = ProgramStructure.COMPLEX,macrocycles = listOf(Macrocycle("m","M",blocks)),
            runState = ProgramRunState("run",weekId = "w1",blockId = "b1"))
    }
    private fun apply(p: Program, scope: SwapScope, executed: Set<String> = emptySet()) =
        applyExerciseSwap(p,"s1","e1",target,scope,CatalogCompositionTestSupport.catalog,executed,10)
    private fun weeks(p: Program) = ProgramHierarchyIndex(p).orderedWeeks().map { it.week }

    @Test fun whole_program_preserves_recorded_sessions_and_marks_only_the_three_pending_sessions() {
        val p = program()
        val result = apply(p,SwapScope.WHOLE_PROGRAM,setOf("s4"))
        assertEquals(listOf(CatalogIds.BP_PAUSE,CatalogIds.BP_PAUSE,CatalogIds.BP_PAUSE,CatalogIds.BP),
            weeks(result).map { it.sessions.single().allExercises().single().catalogConfigurationId })
        assertEquals(weeks(p).last(),weeks(result).last())
        assertEquals(3,result.manualSessionOverrides.size)
        assertEquals("MANUAL",result.programDecisions.single().source)
        assertEquals(result,apply(result,SwapScope.WHOLE_PROGRAM,setOf("s4")))
    }
    @Test fun rest_of_block_keeps_the_next_block_identical() {
        val p = program()
        val result = apply(p,SwapScope.REST_OF_BLOCK)
        assertEquals(weeks(p).drop(2),weeks(result).drop(2))
        assertEquals(2,result.manualSessionOverrides.size)
        assertEquals(CatalogIds.BP_PAUSE,weeks(result).first().sessions.single().allExercises().single().catalogConfigurationId)
    }
    @Test fun equivalent_intent_never_transfers_a_different_configurations_1rm_tm_or_working_load() {
        val p = program()
        val result = apply(p,SwapScope.THIS_SESSION)
        val exercise = weeks(result).first().sessions.single().allExercises().single()
        assertNull(exercise.reference1RM)
        assertNull(exercise.referenceTrainingMax)
        assertNull(exercise.sets.single().weight)
        assertEquals(5,exercise.sets.single().targetReps)
        assertEquals(2,exercise.sets.single().targetRIR)
        assertEquals(180,exercise.sets.single().restAfterSeconds)
        assertEquals(CatalogIds.BP_PAUSE,exercise.exerciseDbId)
        assertEquals(CatalogIds.BP_PAUSE,exercise.canonicalExerciseId)
        assertNotNull(exercise.performanceProfileId)
        assertEquals(weeks(p).drop(1),weeks(result).drop(1))
    }
    @Test fun started_source_sessions_and_invalid_catalog_choices_are_no_ops() {
        val p = program()
        assertSame(p,apply(p,SwapScope.THIS_SESSION,setOf("s1")))
        assertSame(p,applyExerciseSwap(p,"s1","e1",target.copy(configId = "unknown"),SwapScope.WHOLE_PROGRAM,
            CatalogCompositionTestSupport.catalog))
        assertSame(p,applyExerciseSwap(p,"s1","e1",target.copy(configId = CatalogIds.BP),SwapScope.WHOLE_PROGRAM,
            CatalogCompositionTestSupport.catalog))
    }
    @Test fun an_explicit_manual_swap_keeps_an_author_recipe_snapshot_intact() {
        val base = program()
        val recipe = com.example.kpkn.data.protocols.TrainingPlanRecipe("author",emptyList())
        val p = base.copy(sourceProtocolId = "author",sourceRecipe = recipe)
        val result = apply(p,SwapScope.THIS_SESSION)
        assertSame(recipe,result.sourceRecipe)
        assertEquals("author",result.sourceProtocolId)
        assertEquals("Cambio de ejercicio",result.manualSessionOverrides.single().reason)
    }
    @Test fun changing_from_a_barbell_to_dumbbells_uses_the_new_quantity_convention() {
        val p = program()
        val result = applyExerciseSwap(p,"s1","e1",target.copy(configId = CatalogIds.BP_DB),SwapScope.THIS_SESSION,
            CatalogCompositionTestSupport.catalog)
        val exercise = weeks(result).first().sessions.single().allExercises().single()
        assertEquals(LoadQuantityConvention.PER_IMPLEMENT,exercise.loadQuantityConvention)
        assertEquals(LoadQuantityConvention.PER_IMPLEMENT,exercise.sets.single().loadQuantityConvention)
        assertNull(exercise.sets.single().weight)
    }
    @Test fun sessions_that_already_have_the_target_keep_their_exercise_and_the_decision_counts_them() {
        // DUP-03 (F-13): s2 ya tiene la configuración destino y s3 su definición: ninguna se cambia.
        val base = program()
        fun extra(id: String, config: String, definition: String) = Exercise(id,"Otro",catalogConfigurationId = config,catalogDefinitionId = definition,
            sets = listOf(ExerciseSet("$id-set",targetReps = 8,targetRIR = 2)))
        val p = base.copy(macrocycles = base.macrocycles.map { macro -> macro.copy(blocks = macro.blocks.map { block ->
            block.copy(mesocycles = block.mesocycles.map { meso -> meso.copy(weeks = meso.weeks.map { week ->
                week.copy(sessions = week.sessions.map { session -> when (session.id) {
                    "s2" -> session.copy(exercises = session.exercises + extra("x2",CatalogIds.BP_PAUSE,"paused_bench_press"))
                    "s3" -> session.copy(exercises = session.exercises + extra("x3","paused_bench_press__dumbbells","paused_bench_press"))
                    else -> session
                } })
            }) })
        }) })
        val result = apply(p,SwapScope.WHOLE_PROGRAM)
        assertEquals(listOf(CatalogIds.BP_PAUSE,CatalogIds.BP,CatalogIds.BP,CatalogIds.BP_PAUSE),
            weeks(result).map { it.sessions.single().allExercises().first().catalogConfigurationId })
        assertEquals(weeks(p)[1],weeks(result)[1])
        assertEquals(weeks(p)[2],weeks(result)[2])
        assertEquals(2,result.manualSessionOverrides.size)
        val decision = result.programDecisions.single()
        assertTrue(decision.detail,decision.detail.contains("en 2 sesiones (2 ejercicios)"))
        assertTrue(decision.detail,decision.detail.contains("2 sesiones conservan el ejercicio porque ya tenían «"))
    }
}

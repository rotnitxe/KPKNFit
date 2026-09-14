package com.example.kpkn.domain.training

import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramGoals
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.DayArchetypes
import com.example.kpkn.data.protocols.LiftSlot
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.PROTOCOL_LIBRARY
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.isVisibleForApplication
import com.example.kpkn.data.programs.PROGRAM_TEMPLATES
import com.example.kpkn.data.protocols.percentSets
import com.example.kpkn.data.protocols.repeatPercentSets
import com.example.kpkn.data.protocols.slot
import com.example.kpkn.data.protocols.weekRecipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

class PlanMaterializerTest {
    private class SeqIds : IdProvider {
        private var n = 0
        override fun newId(): String = "id_${++n}"
    }

    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    private fun sampleRecipe(): TrainingPlanRecipe {
        val day = DayArchetypes.plSquat(80.0, weekday = 1)
        return TrainingPlanRecipe(
            id = "test-recipe",
            weeks = listOf(
                weekRecipe(1, 0, "Fuerza", BlockGoal.INTENSIFICATION, listOf(day)),
            ),
            trainingMaxPercent = 0.90,
            liftSlots = mapOf(
                LiftSlot.SQUAT to CatalogIds.SQ_LOW,
                LiftSlot.BENCH to CatalogIds.BP,
                LiftSlot.DEADLIFT to CatalogIds.DL,
            ),
            claimedDaysPerWeek = 4,
        )
    }

    @Test
    fun materialize_copies_sets_reps_percent_and_amrap() {
        val amrapDay = DayArchetypes.plSquat(80.0, t1Amrap = true, weekday = 1)
        val recipe = sampleRecipe().copy(
            weeks = listOf(weekRecipe(1, 0, "Fuerza", BlockGoal.INTENSIFICATION, listOf(amrapDay))),
        )
        val program = PlanMaterializer.materialize(
            Program(id = "p", name = "T"),
            recipe,
            CatalogCompositionTestSupport.metadata,
            SeqIds(),
            profile = PowerliftingProfile(squat1RM = 200.0, bench1RM = 120.0, deadlift1RM = 220.0),
        )
        val t1 = program.macrocycles.first().blocks.first().mesocycles.first().weeks.first()
            .sessions.first().exercises.first()
        assertEquals(4, t1.sets.size)
        assertEquals(4, t1.sets.first().targetReps)
        assertEquals(80.0, t1.sets.first().targetPercentageRM)
        assertTrue(t1.sets.last().isAmrap)
        assertEquals(180.0, t1.reference1RM ?: -1.0, 0.001)
        assertEquals(180.0 * 0.80, t1.sets.first().weight ?: -1.0, 0.001)
        assertEquals(listOf(40.0, 55.0, 65.0), t1.warmupSets.map { it.percentageOfWorkingWeight })
    }

    @Test
    fun rematerializeWeek_does_not_rewrite_executed_weeks() {
        val recipe = sampleRecipe()
        val program = PlanMaterializer.materialize(
            Program(id = "p", name = "T"),
            recipe,
            CatalogCompositionTestSupport.metadata,
            SeqIds(),
        )
        val weekId = program.macrocycles.first().blocks.first().mesocycles.first().weeks.first().id
        val unchanged = PlanMaterializer.rematerializeWeek(
            program,
            weekId,
            recipe,
            CatalogCompositionTestSupport.metadata,
            SeqIds(),
            intensityScale = 0.5,
            executedWeekIds = setOf(weekId),
        )
        assertEquals(program.macrocycles, unchanged.macrocycles)
    }

    @Test
    fun materialize_is_deterministic_with_same_id_provider() {
        val profile = PowerliftingProfile(squat1RM = 200.0, bench1RM = 120.0, deadlift1RM = 220.0)
        val recipes = (
            PROTOCOL_LIBRARY.filter { it.isVisibleForApplication }.mapNotNull { it.recipe } +
                PROGRAM_TEMPLATES.mapNotNull { it.recipe }
            ).distinctBy { it.id }
        assertTrue("Debe haber recetas para el test de determinismo", recipes.size >= 4)
        recipes.forEach { recipe ->
            val first = PlanMaterializer.materialize(
                Program(id = "p", name = "T"),
                recipe,
                CatalogCompositionTestSupport.metadata,
                SeqIds(),
                profile = profile,
            )
            val second = PlanMaterializer.materialize(
                Program(id = "p", name = "T"),
                recipe,
                CatalogCompositionTestSupport.metadata,
                SeqIds(),
                profile = profile,
            )
            assertEquals("${recipe.id} bloques", first.macrocycles, second.macrocycles)
            assertEquals("${recipe.id} receta fuente", first.sourceRecipe, second.sourceRecipe)
        }
    }

    @Test
    fun hydrateProfile_uses_program_goals() {
        val profile = PlanMaterializer.hydrateProfile(
            Program(id = "p", name = "T", goals = ProgramGoals(squat1RM = 200.0, bench1RM = 120.0, deadlift1RM = 220.0)),
            profile = null,
            trainingMaxPercent = 0.90,
        )
        assertEquals(180.0, profile?.squatTM ?: -1.0, 0.001)
        assertEquals(108.0, profile?.benchTM ?: -1.0, 0.001)
    }

    @Test
    fun percent_sets_preserve_load_basis() {
        val sets = percentSets(180, 5 to 65.0, 5 to 75.0, 5 to 85.0, amrapLast = true, basis = LoadBasis.PERCENT_TM)
        assertEquals(3, sets.size)
        assertTrue(sets.last().amrap)
        assertEquals(LoadBasis.PERCENT_TM, sets.first().loadBasis)
        val t1 = slot("t1", SlotRole.T1_MAIN, CatalogIds.SQ_LOW, sets, 240, LiftSlot.SQUAT, isCompetitionLift = true)
        assertEquals(3, t1.sets.size)
        assertEquals(repeatPercentSets(3, 5, 80.0, 180).size, 3)
    }

    @Test
    fun texas_volume_is_90_percent_of_friday_top_and_recovery_is_80_percent_of_monday() {
        val protocol = com.example.kpkn.data.protocols.PROTOCOL_LIBRARY.first { it.id == "texas-method-3d" }
        val recipe = protocol.recipe!!
        val program = PlanMaterializer.materialize(
            Program(id = "tx", name = "Texas"),
            recipe,
            CatalogCompositionTestSupport.metadata,
            SeqIds(),
            profile = PowerliftingProfile(squat1RM = 200.0, bench1RM = 120.0, deadlift1RM = 220.0),
        )
        val week = program.macrocycles.first().blocks.first().mesocycles.first().weeks[1]
        val byName = week.sessions.associateBy { it.name }
        fun squatOf(sessionName: String) = byName.getValue(sessionName).exercises.first {
            it.catalogConfigurationId == CatalogIds.SQ_LOW
        }
        val monday = squatOf("Volumen 5x5")
        val wednesday = squatOf("Recuperación")
        val friday = squatOf("Intensidad PR")
        val tm = 200.0
        assertEquals(tm * 1.00, friday.sets.first().weight ?: -1.0, 0.001)
        assertEquals(tm * 0.90, monday.sets.first().weight ?: -1.0, 0.001)
        assertEquals(tm * 0.72, wednesday.sets.first().weight ?: -1.0, 0.001)
    }

    @Test
    fun materialize_persists_optional_slot_role_top_set_and_load_basis() {
        val recipe = TrainingPlanRecipe(
            id = "opt-fields",
            weeks = listOf(
                weekRecipe(
                    1,
                    0,
                    "Bloque",
                    BlockGoal.INTENSIFICATION,
                    listOf(
                        com.example.kpkn.data.protocols.DayRecipe(
                            label = "Dia",
                            slots = listOf(
                                slot(
                                    "t1",
                                    SlotRole.T1_MAIN,
                                    CatalogIds.SQ_LOW,
                                    listOf(
                                        com.example.kpkn.data.protocols.SetRecipe(
                                            reps = 5,
                                            percent = 75.0,
                                            isTopSet = true,
                                            loadBasis = LoadBasis.PERCENT_TM,
                                        ),
                                    ),
                                    180,
                                    LiftSlot.SQUAT,
                                    technique = com.example.kpkn.data.protocols.TechniqueModifier.PAUSE_2S,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val program = PlanMaterializer.materialize(
            Program(id = "p", name = "Opt"),
            recipe,
            CatalogCompositionTestSupport.metadata,
            SeqIds(),
            profile = PowerliftingProfile(squat1RM = 200.0, bench1RM = 120.0, deadlift1RM = 220.0),
            strict = false,
        )
        val exercise = program.macrocycles.first().blocks.first().mesocycles.first().weeks.first()
            .sessions.first().exercises.first()
        assertEquals(SlotRole.T1_MAIN, exercise.slotRole)
        assertEquals(com.example.kpkn.data.protocols.TechniqueModifier.PAUSE_2S, exercise.techniqueModifier)
        assertTrue(exercise.sets.first().isTopSet)
        assertEquals(LoadBasis.PERCENT_TM, exercise.sets.first().loadBasis)
    }
}

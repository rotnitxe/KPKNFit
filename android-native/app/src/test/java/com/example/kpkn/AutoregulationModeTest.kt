package com.example.kpkn

import com.example.kpkn.data.models.AutoregulationMode
import com.example.kpkn.data.models.AutoregulationProposal
import com.example.kpkn.data.models.AutoregulationProposalKind
import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.LoadAdvisoryLevel
import com.example.kpkn.data.models.PendingProgramActionType
import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramRunState
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.DayArchetypes
import com.example.kpkn.data.protocols.LiftSlot
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.weekRecipe
import com.example.kpkn.domain.training.AmrapHit
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.IdProvider
import com.example.kpkn.domain.training.PlanMaterializer
import com.example.kpkn.domain.training.ProgramAutoregulationEngine
import com.example.kpkn.domain.training.WeeklyAutoregulationSignals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * Ajustes inteligentes Off / Proponer / Automático: el modo elegido por el
 * usuario sobrevive a la materialización, OFF no propone ni muta, PROPOSE deja
 * propuesta pendiente sin mutar y AUTO aplica y audita. Sin descargas
 * periódicas fijas.
 */
class AutoregulationModeTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    private class SeqIds : IdProvider {
        private var n = 0
        override fun newId(): String = "auto_${++n}"
    }

    private fun recipe(): TrainingPlanRecipe = TrainingPlanRecipe(
        id = "autoreg-mode-test",
        weeks = listOf(
            weekRecipe(
                1, 0, "Fuerza", BlockGoal.INTENSIFICATION,
                listOf(DayArchetypes.plSquat(80.0, t1Amrap = true, weekday = 1)),
            ),
            weekRecipe(
                2, 0, "Fuerza", BlockGoal.INTENSIFICATION,
                listOf(DayArchetypes.plSquat(82.5, weekday = 1)),
            ),
        ),
        trainingMaxPercent = 0.90,
        liftSlots = mapOf(
            LiftSlot.SQUAT to CatalogIds.SQ_LOW,
            LiftSlot.BENCH to CatalogIds.BP,
            LiftSlot.DEADLIFT to CatalogIds.DL,
        ),
    )

    private fun materialized(mode: AutoregulationMode): Program = PlanMaterializer.materialize(
        Program(
            id = "p",
            name = "Auto",
            structure = ProgramStructure.COMPLEX,
            powerliftingProfile = PowerliftingProfile(
                squat1RM = 200.0, squatTM = 180.0,
                bench1RM = 140.0, benchTM = 126.0,
                deadlift1RM = 240.0, deadliftTM = 216.0,
            ),
            autoregulationMode = mode,
        ),
        recipe(),
        CatalogCompositionTestSupport.metadata,
        SeqIds(),
    ).copy(runState = ProgramRunState(runId = "run1"))

    private fun adjustTmProposal(): AutoregulationProposal = AutoregulationProposal(
        kind = AutoregulationProposalKind.ADJUST_TM,
        liftSlot = "SQUAT",
        percentDelta = -2.5,
        explanation = "AMRAP corto",
    )

    @Test
    fun materialization_preserves_the_chosen_autoregulation_mode() {
        AutoregulationMode.values().forEach { mode ->
            val materialized = PlanMaterializer.materialize(
                Program(id = "p", name = "T", autoregulationMode = mode),
                recipe(),
                CatalogCompositionTestSupport.metadata,
                SeqIds(),
            )
            assertEquals(
                "El modo $mode debe sobrevivir a la materialización",
                mode,
                materialized.autoregulationMode,
            )
        }
    }

    @Test
    fun off_mode_produces_no_proposals_and_no_mutation() {
        val program = materialized(AutoregulationMode.OFF)
        val weeks = program.macrocycles.first().blocks.first().mesocycles.first().weeks
        val proposals = ProgramAutoregulationEngine.evaluate(
            program = program,
            completedWeek = weeks.first(),
            logs = emptyList(),
            recipe = recipe(),
            signals = WeeklyAutoregulationSignals(
                readinessScore = 10,
                cumulativeFatigue = 90.0,
                loadAdvisoryLevel = LoadAdvisoryLevel.UNLOAD,
                amrapHits = listOf(AmrapHit(LiftSlot.SQUAT, 95.0, prescribedReps = 5, actualReps = 1)),
            ),
        )
        assertTrue("OFF no propone nada", proposals.isEmpty())

        val result = ProgramAutoregulationEngine.apply(
            program = program,
            nextWeekId = weeks[1].id,
            proposals = listOf(adjustTmProposal()),
            recipe = recipe(),
            executedWeekIds = setOf(weeks.first().id),
            metadata = CatalogCompositionTestSupport.metadata,
        )
        assertEquals(program, result.program) // sin mutación
        assertNull(result.program.runState?.pendingAction) // sin pendientes
        assertTrue(result.program.runState?.autoregulationAudit.orEmpty().isEmpty())
    }

    @Test
    fun propose_mode_creates_pending_action_without_mutating_the_plan() {
        val program = materialized(AutoregulationMode.PROPOSE)
        val weeks = program.macrocycles.first().blocks.first().mesocycles.first().weeks
        val result = ProgramAutoregulationEngine.apply(
            program = program,
            nextWeekId = weeks[1].id,
            proposals = listOf(adjustTmProposal()),
            recipe = recipe(),
            executedWeekIds = setOf(weeks.first().id),
            metadata = CatalogCompositionTestSupport.metadata,
        )
        assertFalse("PROPOSE no aplica", result.applied)
        assertEquals(PendingProgramActionType.CONFIRM_AUTOREGULATION, result.program.runState?.pendingAction?.type)
        // El plan queda intacto hasta que el usuario confirme.
        assertEquals(program.macrocycles, result.program.macrocycles)
        assertEquals(program.powerliftingProfile, result.program.powerliftingProfile)

        val accepted = ProgramAutoregulationEngine.resolvePending(
            result.program,
            accept = true,
            CatalogCompositionTestSupport.metadata,
        )
        assertNull(accepted.runState?.pendingAction)
        assertTrue((accepted.powerliftingProfile?.squatTM ?: 180.0) < 180.0)
    }

    @Test
    fun auto_mode_mutates_the_plan_and_records_the_audit() {
        val program = materialized(AutoregulationMode.AUTO)
        val weeks = program.macrocycles.first().blocks.first().mesocycles.first().weeks
        val result = ProgramAutoregulationEngine.apply(
            program = program,
            nextWeekId = weeks[1].id,
            proposals = listOf(adjustTmProposal()),
            recipe = recipe(),
            executedWeekIds = setOf(weeks.first().id),
            metadata = CatalogCompositionTestSupport.metadata,
        )
        assertTrue("AUTO aplica sin confirmación", result.applied)
        val audit = result.program.runState?.autoregulationAudit.orEmpty()
        assertTrue("AUTO deja rastro de auditoría", audit.isNotEmpty())
        assertEquals(AutoregulationMode.AUTO, audit.last().mode)
        assertTrue(audit.last().kinds.contains(AutoregulationProposalKind.ADJUST_TM))
        assertTrue("El TM muta en AUTO", (result.program.powerliftingProfile?.squatTM ?: 180.0) < 180.0)
    }

    @Test
    fun no_fixed_periodic_deload_is_ever_injected() {
        val periodic = TrainingPlanRecipe(
            id = "autoreg-periodic-test",
            weeks = listOf(4, 6, 8).map { number ->
                weekRecipe(
                    number, 0, "Base", BlockGoal.ACCUMULATION,
                    listOf(DayArchetypes.plSquat(80.0, weekday = 1)),
                )
            },
            trainingMaxPercent = 0.90,
            liftSlots = mapOf(
                LiftSlot.SQUAT to CatalogIds.SQ_LOW,
                LiftSlot.BENCH to CatalogIds.BP,
                LiftSlot.DEADLIFT to CatalogIds.DL,
            ),
        )
        val program = PlanMaterializer.materialize(
            Program(id = "periodic", name = "Periodic", structure = ProgramStructure.COMPLEX, autoregulationMode = AutoregulationMode.PROPOSE),
            periodic,
            CatalogCompositionTestSupport.metadata,
            SeqIds(),
        ).copy(runState = ProgramRunState(runId = "run-periodic"))

        // Ni en la semana 4, 6 ni 8: las descargas adicionales solo llegan como
        // propuesta de Rings/AUGE con señales reales, nunca por calendario fijo.
        program.macrocycles.first().blocks.first().mesocycles.first().weeks.forEach { week ->
            val proposals = ProgramAutoregulationEngine.evaluate(
                program = program,
                completedWeek = week,
                logs = emptyList(),
                recipe = periodic,
                signals = WeeklyAutoregulationSignals(readinessScore = 80, cumulativeFatigue = 40.0),
            )
            assertTrue(
                "Semana ${week.progressionIndex}: sin descarga periódica fija",
                proposals.none { it.kind == AutoregulationProposalKind.INSERT_DELOAD },
            )
        }
        val blockGoals = program.macrocycles.flatMap { it.blocks }.map { it.goal }
        assertTrue(blockGoals.none { it == BlockGoal.DELOAD })
    }

    @Test
    fun author_deload_weeks_keep_their_prescribed_doses() {
        val deloadRecipe = TrainingPlanRecipe(
            id = "autoreg-deload-test",
            weeks = listOf(
                weekRecipe(
                    1, 0, "Base", BlockGoal.ACCUMULATION,
                    listOf(DayArchetypes.plSquat(80.0, weekday = 1)),
                ),
                weekRecipe(
                    2, 0, "Descarga", BlockGoal.DELOAD,
                    listOf(DayArchetypes.plSquat(60.0, weekday = 1)),
                ),
            ),
            trainingMaxPercent = 0.90,
            liftSlots = mapOf(
                LiftSlot.SQUAT to CatalogIds.SQ_LOW,
                LiftSlot.BENCH to CatalogIds.BP,
                LiftSlot.DEADLIFT to CatalogIds.DL,
            ),
        )
        val program = PlanMaterializer.materialize(
            Program(id = "deload", name = "Deload", structure = ProgramStructure.COMPLEX, autoregulationMode = AutoregulationMode.OFF),
            deloadRecipe,
            CatalogCompositionTestSupport.metadata,
            SeqIds(),
        )
        val deloadWeek = program.macrocycles.first().blocks.first().mesocycles.first().weeks[1]
        val squat = deloadWeek.sessions.first().allExercises().first { it.catalogConfigurationId == CatalogIds.SQ_LOW }
        val percents = squat.sets.mapNotNull { it.targetPercentageRM }
        // La dosis de descarga de la receta de autor viaja intacta (60 %).
        assertTrue(percents.isNotEmpty())
        assertTrue("La receta de autor no se reescribe", percents.all { it == 60.0 })
    }
}

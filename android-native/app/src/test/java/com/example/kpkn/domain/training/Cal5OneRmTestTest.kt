package com.example.kpkn.domain.training

import com.example.kpkn.data.models.OneRmResolution
import com.example.kpkn.data.models.OneRmResolutionStatus
import com.example.kpkn.data.models.PendingProgramAction
import com.example.kpkn.data.models.PendingProgramActionType
import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramRunState
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.LiftSlot
import com.example.kpkn.domain.training.Cal5TestSupport.exerciseLog
import com.example.kpkn.domain.training.Cal5TestSupport.exerciseOf
import com.example.kpkn.domain.training.Cal5TestSupport.recipeOf
import com.example.kpkn.domain.training.Cal5TestSupport.templateRecipeOf
import com.example.kpkn.domain.training.Cal5TestSupport.weeksOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.BeforeClass
import org.junit.Test

/**
 * CAL5 · test de 1RM de bloques: pide solo los levantamientos del programa (Smolov: la sentadilla), añade el press militar y se
 * precarga con las series «Test» registradas (la persona confirma).
 */
class Cal5OneRmTestTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    @Test
    fun the_test_asks_only_for_the_lifts_of_the_program() {
        assertEquals("Smolov solo trabaja la sentadilla", listOf(LiftSlot.SQUAT), OneRmTestPlan.requiredLifts(recipeOf("smolov")))
        assertEquals(
            "5/3/1 trabaja también el press militar",
            listOf(LiftSlot.SQUAT, LiftSlot.BENCH, LiftSlot.DEADLIFT, LiftSlot.OVERHEAD),
            OneRmTestPlan.requiredLifts(recipeOf("wendler-531-bbb")),
        )
        assertEquals(
            "una plantilla de powerlifting pide sentadilla, banca y peso muerto",
            listOf(LiftSlot.SQUAT, LiftSlot.BENCH, LiftSlot.DEADLIFT),
            OneRmTestPlan.requiredLifts(templateRecipeOf("power-12-3")),
        )
        assertEquals("sin receta, los tres de siempre", listOf(LiftSlot.SQUAT, LiftSlot.BENCH, LiftSlot.DEADLIFT), OneRmTestPlan.requiredLifts(recipe = null))
    }

    private fun gate(recipeId: String, profile: PowerliftingProfile): Program {
        val program = Cal5TestSupport.materialize(recipeOf(recipeId), profile = profile, id = "gate-$recipeId")
        return program.copy(
            runState = ProgramRunState(
                runId = "run-gate",
                pendingAction = PendingProgramAction(type = PendingProgramActionType.CONFIRM_1RM_TEST, message = "Test de 1RM"),
            ),
        )
    }

    @Test
    fun smolov_records_with_the_squat_alone_and_the_other_lifts_keep_their_values() {
        val program = gate("smolov", PowerliftingProfile(squat1RM = 200.0, bench1RM = 120.0, deadlift1RM = 220.0))
        val result = ProgramProgressEngine.resolvePendingOneRmTest(
            program, null, OneRmResolution(status = OneRmResolutionStatus.RECORDED, squat1RM = 215.0),
        )
        val profile = result.program.powerliftingProfile!!
        assertEquals(215.0, profile.squat1RM!!, 1e-9)
        assertEquals("TM = 1RM × el porcentaje de la receta (100 % en Smolov)", 215.0, profile.squatTM!!, 1e-9)
        assertEquals("la banca no se pidió y no cambia", 120.0, profile.bench1RM!!, 1e-9)
        assertEquals(220.0, profile.deadlift1RM!!, 1e-9)
        assertEquals(215.0, result.program.goals!!.squat1RM!!, 1e-9)
    }

    @Test
    fun a_program_with_the_overhead_press_asks_for_it_and_records_it() {
        val profile = PowerliftingProfile(squat1RM = 200.0, bench1RM = 120.0, deadlift1RM = 220.0, overhead1RM = 80.0)
        val program = ExerciseTrainingMax.migrate(gate("wendler-531-bbb", profile))

        // Sin el press militar el test no se acepta.
        try {
            ProgramProgressEngine.resolvePendingOneRmTest(
                program, null, OneRmResolution(status = OneRmResolutionStatus.RECORDED, squat1RM = 210.0, bench1RM = 125.0, deadlift1RM = 230.0),
            )
            fail("falta el press militar")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!, expected.message!!.contains("press militar"))
        }

        val recorded = OneRmTestPlan.recorded(
            mapOf(LiftSlot.SQUAT to 210.0, LiftSlot.BENCH to 125.0, LiftSlot.DEADLIFT to 230.0, LiftSlot.OVERHEAD to 85.0),
        )
        val result = ProgramProgressEngine.resolvePendingOneRmTest(program, null, recorded)
        val updated = result.program.powerliftingProfile!!
        assertEquals(85.0, updated.overhead1RM!!, 1e-9)
        assertEquals("TM del press = 85 × 0,9", 76.5, updated.overheadTM!!, 1e-9)
        assertEquals(85.0, result.program.runState?.oneRmAuditTrail?.single()?.overhead1RM)
        val military = ExerciseTrainingMax.exercises(result.program).first { it.catalogConfigurationId == CatalogIds.OHP }
        val reference = ExerciseTrainingMax.resolved(result.program, military,
            com.example.kpkn.data.protocols.PlanLoadReferenceKind.EXERCISE_1RM)!!
        assertEquals(85.0, reference.capturedLoadKg!!, 0.0)
        assertEquals("TEST", reference.metadata!!.origin)
        assertEquals(recorded.resolvedAtMs, reference.capturedAtMs)
        assertEquals(76.5, ExerciseTrainingMax.capturedKg(result.program,military,
            com.example.kpkn.data.protocols.PlanLoadReferenceKind.EXERCISE_TM)!!,0.0)

    }

    @Test
    fun a_missing_or_zero_one_rm_is_not_valid_for_a_required_lift() {
        val required = listOf(LiftSlot.SQUAT, LiftSlot.OVERHEAD)
        val resolution = OneRmResolution(status = OneRmResolutionStatus.RECORDED, squat1RM = 200.0, overhead1RM = 0.0)
        assertEquals(listOf(LiftSlot.OVERHEAD), OneRmTestPlan.missingLifts(resolution, required))
        assertEquals(emptyList<LiftSlot>(), OneRmTestPlan.missingLifts(resolution.copy(overhead1RM = 70.0), required))
        assertNull(OneRmTestPlan.oneRmOf(OneRmResolution(status = OneRmResolutionStatus.SKIPPED), LiftSlot.OVERHEAD))
    }

    // ─── Precarga con las series «Test» ──────────────────────────────────────────

    @Test
    fun the_recorded_test_sets_prefill_the_sheet_and_other_sets_do_not() {
        val jt = recipeOf("gzcl-jt-2")
        val program = Cal5TestSupport.materialize(jt, profile = Cal5TestSupport.fullProfile(), id = "jt-test")
        val weeks = weeksOf(program)
        // La semana 6 del J&T cierra la primera ola con el single al 100 % del 1RM (un top set sobre el 1RM).
        val week6 = weeks[5]
        val squatDay = week6.sessions.first()
        val t1 = exerciseOf(squatDay, CatalogIds.SQ_LOW)
        assertTrue("la serie de test existe", t1.sets.first().isTopSet && t1.sets.first().targetPercentageRM == 100.0)
        val log = exerciseLog(program, week6, squatDay, t1, weightOf = { index, planned -> if (index == 0) 205.0 else planned.weight }, repsOf = { _, planned -> planned.targetReps ?: 2 })

        val hits = ProgramAutoregulationEngine.collectOneRmTestHits(listOf(week6), listOf(log), jt)
        assertEquals(1, hits.size)
        assertEquals(LiftSlot.SQUAT, hits.single().liftSlot)
        assertEquals(205.0, hits.single().weightKg, 1e-9)
        assertEquals(mapOf(LiftSlot.SQUAT to 205.0), OneRmTestPlan.prefill(hits, listOf(LiftSlot.SQUAT, LiftSlot.BENCH, LiftSlot.DEADLIFT)))
        assertTrue("un levantamiento que el programa no pide no se precarga", OneRmTestPlan.prefill(hits, listOf(LiftSlot.BENCH)).isEmpty())

        // Una semana normal (sin single al 100 %) no precarga nada.
        val week1 = weeks[0]
        val day1 = week1.sessions.first()
        val log1 = exerciseLog(program, week1, day1, exerciseOf(day1, CatalogIds.SQ_LOW))
        assertTrue(ProgramAutoregulationEngine.collectOneRmTestHits(listOf(week1), listOf(log1), jt).isEmpty())
    }

    @Test
    fun the_prefill_keeps_the_heaviest_set_per_lift_and_ignores_failed_attempts() {
        val hits = listOf(
            ProgramAutoregulationEngine.OneRmTestHit(LiftSlot.SQUAT, 195.0, 1),
            ProgramAutoregulationEngine.OneRmTestHit(LiftSlot.SQUAT, 205.0, 1),
            ProgramAutoregulationEngine.OneRmTestHit(LiftSlot.SQUAT, 215.0, 0),
            ProgramAutoregulationEngine.OneRmTestHit(LiftSlot.BENCH, 120.0, 1),
        )
        assertEquals(mapOf(LiftSlot.SQUAT to 205.0, LiftSlot.BENCH to 120.0), OneRmTestPlan.prefill(hits))
    }
}

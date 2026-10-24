package com.example.kpkn.domain.training

import com.example.kpkn.data.models.AutoregulationMode
import com.example.kpkn.data.models.AutoregulationProposalKind
import com.example.kpkn.data.models.PendingProgramActionType
import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.data.models.SetPurpose
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.LiftSlot
import com.example.kpkn.data.protocols.ProgressionRule
import com.example.kpkn.domain.training.Cal5TestSupport.exerciseLog
import com.example.kpkn.domain.training.Cal5TestSupport.exerciseOf
import com.example.kpkn.domain.training.Cal5TestSupport.recipeOf
import com.example.kpkn.domain.training.Cal5TestSupport.weeksOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * CAL5 · J&T 2.0: `RepMaxAutoregulated` por fin lee el RM semanal del T1 (una serie AMRAP con porcentaje); y Cube, Lilliebridge y The
 * Rippler, cuyas fuentes no dan regla de carga al AMRAP, lo dejan como AMRAP de registro explícito.
 */
class Cal5JackedTanAndRecordOnlyTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    // ─── J&T 2.0 ──────────────────────────────────────────────────────────────────

    private val jt = recipeOf("gzcl-jt-2")

    /** TM de sentadilla 150 (1RM 167: el TM al 90 %), para que el e1RM de un RM de 10 quede claramente por encima del 1RM implícito. */
    private val lowTmProfile = PowerliftingProfile(
        squat1RM = 167.0, squatTM = 150.0, bench1RM = 120.0, benchTM = 108.0, deadlift1RM = 220.0, deadliftTM = 198.0,
        overhead1RM = 80.0, overheadTM = 72.0,
    )

    private fun squatRmLog(program: com.example.kpkn.data.models.Program, rmReps: Int, weightKg: Double) = run {
        val week1 = weeksOf(program).first()
        val squatDay = week1.sessions.first()
        val t1 = exerciseOf(squatDay, CatalogIds.SQ_LOW)
        exerciseLog(program, week1, squatDay, t1, weightOf = { index, planned -> if (planned.purpose.isAmrap) weightKg else planned.weight }, repsOf = { _, planned ->
            if (planned.purpose.isAmrap) rmReps else planned.targetReps ?: 3
        })
    }

    @Test
    fun the_weekly_rm_of_the_t1_reaches_the_rep_max_hook() {
        assertEquals(ProgressionRule.RepMaxAutoregulated, jt.progression)
        val program = Cal5TestSupport.materialize(jt, profile = lowTmProfile)
        val week1 = weeksOf(program).first()
        val t1 = exerciseOf(week1.sessions.first(), CatalogIds.SQ_LOW)
        assertTrue("la semana 1 del J&T lleva su RM como AMRAP con porcentaje", t1.sets.any { it.purpose.isAmrap && it.targetPercentageRM != null })

        // 140 kg × 10 repeticiones: e1RM 186,7 kg frente al 1RM de 166,7 kg que implica el TM de 150.
        val hits = ProgramAutoregulationEngine.collectRepMaxHits(week1, listOf(squatRmLog(program, 10, 140.0)), jt)
        val hit = hits.single()
        assertEquals(LiftSlot.SQUAT, hit.liftSlot)
        assertEquals(10, hit.reps)
        assertEquals(140.0, hit.weightKg, 1e-9)
        assertEquals(186.7, hit.estimatedOneRmKg, 1e-9)
    }

    @Test
    fun a_rep_max_well_above_the_implied_one_rm_raises_the_tm_a_rep_max_below_lowers_it_and_the_modes_are_respected() {
        val propose = Cal5TestSupport.materialize(jt, AutoregulationMode.PROPOSE, lowTmProfile)
        val week1 = weeksOf(propose).first()
        val up = ProgramAutoregulationEngine.evaluate(
            propose, week1, listOf(squatRmLog(propose, 10, 140.0)), jt, WeeklyAutoregulationSignals(),
        ).filter { it.kind == AutoregulationProposalKind.ADJUST_TM }
        assertEquals("SQUAT", up.single().liftSlot)
        assertEquals("e1RM ≥ 1,025 × el 1RM implícito: +2,5 %", 2.5, up.single().percentDelta!!, 1e-9)

        // 100 kg × 10: e1RM 133,3 kg ≤ 0,95 × 166,7 kg → baja el 2,5 %.
        val down = ProgramAutoregulationEngine.evaluate(
            propose, week1, listOf(squatRmLog(propose, 10, 100.0)), jt, WeeklyAutoregulationSignals(),
        ).filter { it.kind == AutoregulationProposalKind.ADJUST_TM }
        assertEquals(-2.5, down.single().percentDelta!!, 1e-9)

        // PROPONER: pendiente, sin tocar el TM; AUTO: aplicado; OFF: nada.
        val weeks = weeksOf(propose)
        val pending = ProgramAutoregulationEngine.apply(
            propose, weeks[1].id, up, jt, setOf(week1.id), CatalogCompositionTestSupport.metadata,
        )
        assertFalse(pending.applied)
        assertEquals(PendingProgramActionType.CONFIRM_AUTOREGULATION, pending.program.runState?.pendingAction?.type)
        assertEquals(150.0, pending.program.powerliftingProfile!!.squatTM!!, 1e-9)

        val auto = Cal5TestSupport.materialize(jt, AutoregulationMode.AUTO, lowTmProfile)
        val autoWeeks = weeksOf(auto)
        val applied = ProgramAutoregulationEngine.apply(auto, autoWeeks[1].id, up, jt, setOf(autoWeeks.first().id), CatalogCompositionTestSupport.metadata)
        assertTrue(applied.applied)
        assertEquals(150.0 * 1.025, applied.program.powerliftingProfile!!.squatTM!!, 1e-9)

        val off = Cal5TestSupport.materialize(jt, AutoregulationMode.OFF, lowTmProfile)
        assertTrue(
            ProgramAutoregulationEngine.evaluate(off, weeksOf(off).first(), listOf(squatRmLog(off, 10, 140.0)), jt, WeeklyAutoregulationSignals()).isEmpty(),
        )
    }

    @Test
    fun a_short_amrap_no_longer_drops_the_tm_on_top_of_the_rep_max() {
        // El AMRAP corto de J&T lo mide el e1RM de la serie al máximo; no hay un −2,5 % aparte.
        val hit = AmrapHit(LiftSlot.SQUAT, 77.5, prescribedReps = 10, actualReps = 4)
        assertNull(ProgramAutoregulationEngine.amrapTmChange(jt.progression, hit))
        assertFalse(ProgramAutoregulationEngine.amrapMovesTm(jt))
    }

    // ─── Cube, Lilliebridge y The Rippler: AMRAP de registro ─────────────────────

    @Test
    fun cube_lilliebridge_and_rippler_mark_their_amrap_as_an_explicit_record() {
        listOf("cube-method", "lilliebridge", "gzcl-rippler").forEach { id ->
            val recipe = recipeOf(id)
            assertEquals("$id: sin regla de progresión", ProgressionRule.None, recipe.progression)
            assertFalse("$id: su AMRAP no mueve el TM", ProgramAutoregulationEngine.amrapMovesTm(recipe))
            val amraps = recipe.weeks.flatMap { it.days }.flatMap { it.slots }.flatMap { it.sets }.filter { it.amrap }
            assertTrue("$id: tiene series AMRAP", amraps.isNotEmpty())
            amraps.forEach { set ->
                assertEquals("$id: propósito explícito", SetPurpose.AMRAP, set.purpose)
                assertEquals("$id: el mínimo son las repeticiones prescritas", set.reps, set.amrapMinimumReps)
                assertNull("$id: no es un calibrador", set.calibrationScope)
            }
        }
    }

    @Test
    fun the_materialized_record_amrap_keeps_its_purpose_and_minimum() {
        val program = Cal5TestSupport.materialize(recipeOf("cube-method"), profile = Cal5TestSupport.fullProfile())
        val repsDay = weeksOf(program).first().sessions.first { session -> session.allExercises().any { it.sets.any { s -> s.purpose.isAmrap } } }
        val set = repsDay.allExercises().flatMap { it.sets }.first { it.purpose.isAmrap }
        assertEquals(SetPurpose.AMRAP, set.purpose)
        assertEquals(8, set.amrapMinimumReps)
        assertNull(set.calibrationScope)
    }

    @Test
    fun the_descriptions_say_the_amrap_is_a_record() {
        val library = com.example.kpkn.data.protocols.PROTOCOL_LIBRARY
        assertTrue(library.first { it.id == "cube-method" }.description.contains("registro"))
        assertTrue(library.first { it.id == "lilliebridge" }.description.contains("registro"))
        assertTrue(library.first { it.id == "gzcl-rippler" }.description.contains("registro"))
    }
}

package com.example.kpkn.domain.training

import com.example.kpkn.data.models.AutoregulationMode
import com.example.kpkn.data.models.AutoregulationProposalKind
import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.CalibrationScope
import com.example.kpkn.data.models.PendingProgramActionType
import com.example.kpkn.data.models.SetPurpose
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.LiftSlot
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.domain.training.Cal5TestSupport.exerciseLog
import com.example.kpkn.domain.training.Cal5TestSupport.exerciseOf
import com.example.kpkn.domain.training.Cal5TestSupport.templateRecipeOf
import com.example.kpkn.domain.training.Cal5TestSupport.weeksOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * CAL5/CAL6 · plantillas KPKN `power-12-3`, `power-16-4` y `power-20-5`: sus AMRAP son calibradores de «próximas sesiones» que salen por el
 * canal de propuestas del programa (OFF, PROPONER, AUTO), y las banderas muertas de las semanas de pico desaparecen.
 */
class Cal5PowerCalibratorTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    private val ids = listOf("power-12-3", "power-16-4", "power-20-5")

    private fun amrapSets(recipe: TrainingPlanRecipe, week: Int) =
        recipe.weeks.first { it.weekNumber == week }.days.flatMap { it.slots }.flatMap { it.sets }.filter { it.amrap }

    @Test
    fun every_amrap_of_the_power_templates_is_a_next_sessions_calibrator() {
        ids.forEach { id ->
            val recipe = templateRecipeOf(id)
            val sets = recipe.weeks.flatMap { it.days }.flatMap { it.slots }.flatMap { it.sets }.filter { it.amrap }
            assertTrue("$id: tiene AMRAP", sets.isNotEmpty())
            sets.forEach { set ->
                assertEquals("$id", SetPurpose.CALIBRATOR, set.purpose)
                assertEquals("$id", CalibrationScope.NEXT_SESSIONS, set.calibrationScope)
                assertEquals("$id: el mínimo son las repeticiones prescritas", set.reps, set.amrapMinimumReps)
            }
            assertEquals("$id: contenido nuevo", 3, recipe.contentVersion)
        }
    }

    @Test
    fun the_dead_flags_of_the_peak_weeks_are_gone() {
        // power-16-4: la semana 11 abre el pico y el pico sustituye el T1 por top set y back-offs; power-20-5: la semana 16.
        assertTrue(amrapSets(templateRecipeOf("power-16-4"), 11).isEmpty())
        assertTrue(amrapSets(templateRecipeOf("power-20-5"), 16).isEmpty())
        // Las semanas en las que el T1 sí lleva AMRAP lo conservan.
        listOf(1, 6).forEach { assertEquals("power-16-4 w$it", 1, amrapSets(templateRecipeOf("power-16-4"), it).size) }
        listOf(1, 7, 12).forEach { assertEquals("power-20-5 w$it", 1, amrapSets(templateRecipeOf("power-20-5"), it).size) }
        listOf(1, 5, 9).forEach { assertEquals("power-12-3 w$it", 1, amrapSets(templateRecipeOf("power-12-3"), it).size) }
        // Ninguna semana de pico de los tres planes lleva un AMRAP que nadie pueda ejecutar: en 16-4 y 20-5 el pico no tiene AMRAP.
        listOf("power-16-4", "power-20-5").forEach { id ->
            templateRecipeOf(id).weeks.filter { it.blockGoal == BlockGoal.PEAK }.forEach { week ->
                assertTrue("$id w${week.weekNumber}", week.days.flatMap { it.slots }.flatMap { it.sets }.none { it.amrap })
            }
        }
    }

    // ─── El canal de propuestas ──────────────────────────────────────────────────

    private val recipe = templateRecipeOf("power-12-3")

    /** Registro del T1 de sentadilla de la semana 1 (4×5 al 71 % del TM): [reps] y [weight] en la AMRAP, con [rir] declarado. */
    private fun squatLog(program: com.example.kpkn.data.models.Program, reps: Int, weight: Double, rir: Int?) = run {
        val week1 = weeksOf(program).first()
        val squatDay = week1.sessions.first()
        val t1 = exerciseOf(squatDay, CatalogIds.SQ_LOW)
        exerciseLog(
            program, week1, squatDay, t1, rir = rir,
            weightOf = { _, planned -> if (planned.purpose.isAmrap) weight else planned.weight },
            repsOf = { _, planned -> if (planned.purpose.isAmrap) reps else planned.targetReps ?: 5 },
        )
    }

    private fun proposalsOf(program: com.example.kpkn.data.models.Program, log: com.example.kpkn.data.models.WorkoutLog) =
        ProgramAutoregulationEngine.evaluate(
            program, weeksOf(program).first(), listOf(log), recipe, WeeklyAutoregulationSignals(),
        ).filter { it.kind == AutoregulationProposalKind.ADJUST_TM }

    @Test
    fun a_calibrated_amrap_well_above_the_tm_proposes_the_capped_raise() {
        val program = Cal5TestSupport.materialize(recipe)
        // 180 kg × 6 con 1 en reserva = 7 al fallo: e1RM 216 kg frente al 1RM implícito de 200 (TM 180 / 0,9): +8 % → tope del +5 %.
        val proposal = proposalsOf(program, squatLog(program, 6, 180.0, rir = 1)).single()
        assertEquals("SQUAT", proposal.liftSlot)
        assertEquals(5.0, proposal.percentDelta!!, 1e-6)
        assertTrue(proposal.explanation, proposal.explanation.startsWith("Calibrador de sentadilla: 6 reps con 180 kg estiman 216 kg de 1RM"))
    }

    @Test
    fun a_capacity_below_the_tm_proposes_the_capped_drop() {
        val program = Cal5TestSupport.materialize(recipe)
        // 150 kg × 5 al fallo (RIR 0): e1RM 150 × 36/32 = 168,8 kg < 200: −15,6 % → tope del −8 %.
        val proposal = proposalsOf(program, squatLog(program, 5, 150.0, rir = 0)).single()
        assertEquals(-8.0, proposal.percentDelta!!, 1e-6)
    }

    @Test
    fun a_capacity_inside_the_dead_zone_proposes_nothing() {
        val program = Cal5TestSupport.materialize(recipe)
        // 170 kg × 3 con 1 en reserva = 4 al fallo: e1RM 170 × 36/33 = 185,5 → ratio 0,93; 175 × 4+1=5 → 175×36/32 = 196,9 → 0,985 (zona muerta).
        assertTrue(proposalsOf(program, squatLog(program, 4, 175.0, rir = 1)).isEmpty())
    }

    @Test
    fun a_set_without_declared_effort_or_with_a_high_reserve_or_few_reps_is_not_calibrated() {
        val program = Cal5TestSupport.materialize(recipe)
        assertTrue("sin esfuerzo declarado", proposalsOf(program, squatLog(program, 6, 180.0, rir = null)).isEmpty())
        assertTrue("reserva de 2", proposalsOf(program, squatLog(program, 6, 180.0, rir = 2)).isEmpty())
        assertTrue("menos de 3 repeticiones", proposalsOf(program, squatLog(program, 2, 180.0, rir = 0)).isEmpty())
    }

    @Test
    fun the_modes_off_propose_and_auto_are_respected_and_the_next_week_uses_the_new_tm() {
        val signals = WeeklyAutoregulationSignals()

        val off = Cal5TestSupport.materialize(recipe, AutoregulationMode.OFF)
        assertTrue(ProgramAutoregulationEngine.evaluate(off, weeksOf(off).first(), listOf(squatLog(off, 6, 180.0, 1)), recipe, signals).isEmpty())

        val propose = Cal5TestSupport.materialize(recipe, AutoregulationMode.PROPOSE)
        val proposeWeeks = weeksOf(propose)
        val proposals = proposalsOf(propose, squatLog(propose, 6, 180.0, 1))
        val pending = ProgramAutoregulationEngine.apply(
            propose, proposeWeeks[1].id, proposals, recipe, setOf(proposeWeeks.first().id), CatalogCompositionTestSupport.metadata,
        )
        assertFalse(pending.applied)
        assertEquals(180.0, pending.program.powerliftingProfile!!.squatTM!!, 1e-9)
        assertEquals(PendingProgramActionType.CONFIRM_AUTOREGULATION, pending.program.runState?.pendingAction?.type)
        val accepted = ProgramAutoregulationEngine.resolvePending(pending.program, accept = true, CatalogCompositionTestSupport.metadata)
        assertEquals(189.0, accepted.powerliftingProfile!!.squatTM!!, 1e-6)

        val auto = Cal5TestSupport.materialize(recipe, AutoregulationMode.AUTO)
        val autoWeeks = weeksOf(auto)
        val applied = ProgramAutoregulationEngine.apply(
            auto, autoWeeks[1].id, proposals, recipe, setOf(autoWeeks.first().id), CatalogCompositionTestSupport.metadata,
        )
        assertTrue(applied.applied)
        assertEquals(189.0, applied.program.powerliftingProfile!!.squatTM!!, 1e-6)
        // La semana 2 del plan se reconstruye con el TM nuevo: la primera serie del T1 de sentadilla de esa semana.
        val before = exerciseOf(weeksOf(auto)[1].sessions.first(), CatalogIds.SQ_LOW).sets.first().weight!!
        val after = exerciseOf(weeksOf(applied.program)[1].sessions.first(), CatalogIds.SQ_LOW).sets.first().weight!!
        assertEquals(before * 189.0 / 180.0, after, 1e-6)
    }

    @Test
    fun a_calibrator_of_the_current_session_never_touches_the_program() {
        // Un calibrador de sesión (CAL3) no pasa por este canal: solo `NEXT_SESSIONS` produce una propuesta del programa.
        val hit = AmrapHit(
            LiftSlot.SQUAT, 71.0, prescribedReps = 5, actualReps = 6, weightKg = 180.0, purpose = SetPurpose.CALIBRATOR,
            calibrationScope = CalibrationScope.SESSION, declaredRir = 1, configurationId = CatalogIds.SQ_LOW,
        )
        assertFalse(hit.isNextSessionsCalibrator)
        assertNull(ProgramAutoregulationEngine.amrapTmChange(recipe.progression, hit))
        val program = Cal5TestSupport.materialize(recipe)
        val proposals = ProgramAutoregulationEngine.evaluate(
            program, weeksOf(program).first(), emptyList(), recipe, WeeklyAutoregulationSignals(amrapHits = listOf(hit), ),
        ).filter { it.kind == AutoregulationProposalKind.ADJUST_TM }
        assertTrue(proposals.isEmpty())
    }
}

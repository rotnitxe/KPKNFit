package com.example.kpkn.domain.training

import com.example.kpkn.data.models.AutoregulationMode
import com.example.kpkn.data.models.AutoregulationProposalKind
import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramRunState
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.LiftSlot
import com.example.kpkn.data.protocols.ProgressionRule
import com.example.kpkn.domain.calibration.CalibrationPolicy
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
 * CAL5 · 5/3/1 (RES1 §3): la serie «+» registra el récord de repeticiones y el e1RM y NO mueve el TM; el TM sube 2,5 kg (tren
 * superior) o 5 kg (inferior) por ciclo si se cumplieron las repeticiones mínimas; si no, se congela sin el −2,5 %; y tras dos ciclos
 * seguidos congelados se reinicia un 10 %.
 */
class Cal5Wendler531Test {
    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    private val bbb = recipeOf("wendler-531-bbb")
    private val fsl = recipeOf("wendler-531-fsl")
    private val profile = Cal5TestSupport.fullProfile()

    @Test
    fun the_e1rm_of_the_plus_set_uses_the_wendler_constant() {
        // peso × reps × 0,0333 + peso (algebraicamente Epley): 100 × 5 → 116,65; 150 × 3 → 164,985.
        assertEquals(116.65, Wendler531Progression.e1rmKg(100.0, 5), 1e-9)
        assertEquals(164.985, Wendler531Progression.e1rmKg(150.0, 3), 1e-9)
        assertEquals(0.0, Wendler531Progression.e1rmKg(100.0, 0), 0.0)
        assertEquals(0.0, Wendler531Progression.e1rmKg(0.0, 5), 0.0)
    }

    @Test
    fun the_plus_set_never_moves_the_tm_by_the_weekly_channel() {
        listOf(bbb, fsl).forEach { recipe ->
            assertFalse("${recipe.id}: su AMRAP no sube ni baja el TM", ProgramAutoregulationEngine.amrapMovesTm(recipe))
            // Corto (1 de 5), en el mínimo y muy por encima: ninguna propuesta, y mucho menos el −2,5 % de antes.
            listOf(0, 1, 4, 5, 6, 12, 20).forEach { reps ->
                val hit = AmrapHit(LiftSlot.SQUAT, 85.0, prescribedReps = 5, actualReps = reps)
                assertNull("${recipe.id} con $reps reps", ProgramAutoregulationEngine.amrapTmChange(recipe.progression, hit))
            }
        }
        val program = Cal5TestSupport.materialize(bbb)
        val week = weeksOf(program).first()
        listOf(1, 12).forEach { reps ->
            val proposals = ProgramAutoregulationEngine.evaluate(
                program, week, emptyList(), bbb,
                WeeklyAutoregulationSignals(amrapHits = listOf(AmrapHit(LiftSlot.SQUAT, 85.0, 5, reps)), readinessScore = 80),
            ).filter { it.kind == AutoregulationProposalKind.ADJUST_TM }
            assertTrue("con $reps reps no hay propuesta de TM: $proposals", proposals.isEmpty())
        }
    }

    @Test
    fun the_tm_rises_2_5_kg_in_the_upper_body_and_5_kg_in_the_lower_body_per_cycle() {
        assertEquals(ProgressionRule.CycleIncrement(2.5, 5.0, resetAfterFrozenCycles = 2, resetPercent = 10.0), bbb.progression)
        val changes = AuthoredProgressionEngine.tmChanges(profile, bbb).associateBy { it.liftSlot }
        assertEquals(185.0, changes.getValue(LiftSlot.SQUAT).afterKg, 1e-9)
        assertEquals(110.5, changes.getValue(LiftSlot.BENCH).afterKg, 1e-9)
        assertEquals(203.0, changes.getValue(LiftSlot.DEADLIFT).afterKg, 1e-9)
        assertEquals(74.5, changes.getValue(LiftSlot.OVERHEAD).afterKg, 1e-9)
    }

    @Test
    fun without_the_minimum_reps_the_lift_is_frozen_and_the_tm_is_not_lowered() {
        val frozen = setOf(LiftSlot.SQUAT)
        val changes = AuthoredProgressionEngine.tmChanges(profile, bbb, excludedLifts = frozen)
        assertTrue("la sentadilla no cambia", changes.none { it.liftSlot == LiftSlot.SQUAT })
        assertTrue("y ninguno baja", changes.all { it.afterKg > it.beforeKg })
        assertEquals(
            listOf(AuthoredProgressionEngine.TmHold(LiftSlot.SQUAT, 180.0)),
            AuthoredProgressionEngine.tmHolds(profile, bbb, excludedLifts = frozen),
        )
    }

    @Test
    fun two_frozen_cycles_in_a_row_reset_the_tm_10_percent_and_the_count_starts_again() {
        val frozen = setOf(LiftSlot.SQUAT)
        fun squatAt(streak: Int): AuthoredProgressionEngine.TmChange? = AuthoredProgressionEngine.tmChanges(
            profile, bbb, excludedLifts = frozen,
            evidence = AuthoredProgressionEngine.CloseEvidence(frozenStreaks = mapOf(LiftSlot.SQUAT to streak)),
        ).firstOrNull { it.liftSlot == LiftSlot.SQUAT }

        assertNull("racha 1: se congela, no hay reinicio", squatAt(1))
        val reset = squatAt(2)!!
        assertEquals(180.0, reset.beforeKg, 1e-9)
        assertEquals("180 − 10 % = 162", 162.0, reset.afterKg, 1e-9)
        assertTrue(reset.note!!.startsWith("reinicio"))
        assertNull("racha 3: tras el reinicio la cuenta vuelve a empezar", squatAt(3))
        assertEquals("racha 4: otro reinicio", 162.0, squatAt(4)!!.afterKg, 1e-9)
        // La racha de las demás se lee por levantamiento.
        assertEquals(listOf(false, true, false, true, false), (1..5).map { Wendler531Progression.resetsAtFrozenStreak(it, 2) })
        assertFalse("sin umbral no hay reinicio (Madcow)", Wendler531Progression.resetsAtFrozenStreak(2, 0))
    }

    @Test
    fun the_reset_threshold_is_the_one_of_the_calibration_policy() {
        val rule = bbb.progression as ProgressionRule.CycleIncrement
        assertEquals(CalibrationPolicy.defaults.fiveThreeOneResetAfterFrozenCycles, rule.resetAfterFrozenCycles)
        assertEquals(10.0, rule.resetPercent, 0.0)
        assertEquals(rule, (fsl.progression as ProgressionRule.CycleIncrement))
    }

    // ─── De punta a punta por el cierre de ciclo ──────────────────────────────────

    private fun program531(): Pair<Program, List<com.example.kpkn.data.models.WorkoutLog>> {
        val materialized = Cal5TestSupport.materialize(
            bbb,
            profile = PowerliftingProfile(squat1RM = 200.0, bench1RM = 120.0, deadlift1RM = 220.0),
            id = "w531",
        )
        val weeks = weeksOf(materialized)
        val last = weeks.last()
        val program = materialized.copy(
            runState = ProgramRunState(
                runId = "run_531",
                cycleNumber = 1,
                weekId = last.id,
                weekInstanceId = ProgramProgressEngine.instanceIdFor(1, last.id),
            ),
        )
        return program to Cal5TestSupport.emptyLogs(program, weeks)
    }

    /** El registro del T1 de banca de la semana 1 con [amrapReps] en la serie «+» (5 o más cumplen el mínimo de la semana de 5). */
    private fun shortBenchLog(program: Program, amrapReps: Int, cycle: Int, runId: String?) : com.example.kpkn.data.models.WorkoutLog {
        val week1 = weeksOf(program).first()
        val benchDay = week1.sessions[1]
        val t1 = exerciseOf(benchDay, CatalogIds.BP)
        return exerciseLog(program, week1, benchDay, t1, cycle = cycle, runId = runId, repsOf = { _, planned ->
            if (planned.purpose.isAmrap) amrapReps else planned.targetReps ?: 5
        })
    }

    @Test
    fun two_cycles_in_a_row_without_the_minimum_reps_reset_the_bench_tm_and_the_third_only_freezes_it() {
        val (program, plain1) = program531()
        val week1 = weeksOf(program).first()
        val benchDay = week1.sessions[1]

        // Ciclo 1: 3 repeticiones donde se pedían 5: la banca se congela en 108 y el resto sube.
        val logs1 = Cal5TestSupport.replacingLogOf(plain1, benchDay, shortBenchLog(program, 3, 1, null))
        val first = ProgramProgressEngine.completeCycle(program, null, 1, logs1)
        assertEquals(108.0, first.program.powerliftingProfile!!.benchTM!!, 1e-9)
        assertEquals(185.0, first.program.powerliftingProfile!!.squatTM!!, 1e-9)

        // Ciclo 2: otra vez corto. Dos ciclos congelados seguidos: reinicio del 10 % (108 → 97,2 → 97) en vez de otro «se mantiene».
        val plain2 = Cal5TestSupport.emptyLogs(first.program, weeksOf(first.program), cycle = 2, runId = "run_531")
        val benchDay2 = weeksOf(first.program).first().sessions[1]
        val logs2 = Cal5TestSupport.replacingLogOf(plain2, benchDay2, shortBenchLog(first.program, 4, 2, "run_531"))
        val second = ProgramProgressEngine.completeCycle(first.program, null, 2, logs1 + logs2)
        assertEquals("banca: 108 − 10 %", 97.0, second.program.powerliftingProfile!!.benchTM!!, 1e-9)
        assertEquals("el resto sigue subiendo", 190.0, second.program.powerliftingProfile!!.squatTM!!, 1e-9)
        val notice = second.program.nativeProgressionAudit.filter { it.userFacingNotice }.last().reason
        assertTrue(notice, notice.contains("banca 108 → 97 kg (reinicio"))
        assertTrue(notice, notice.contains("2 ciclos congelados"))

        // Ciclo 3: corto de nuevo. Es la racha 3: solo se congela (el TM ya reiniciado se mantiene en 97).
        val plain3 = Cal5TestSupport.emptyLogs(second.program, weeksOf(second.program), cycle = 3, runId = "run_531")
        val benchDay3 = weeksOf(second.program).first().sessions[1]
        val logs3 = Cal5TestSupport.replacingLogOf(plain3, benchDay3, shortBenchLog(second.program, 2, 3, "run_531"))
        val third = ProgramProgressEngine.completeCycle(second.program, null, 3, logs1 + logs2 + logs3)
        assertEquals(97.0, third.program.powerliftingProfile!!.benchTM!!, 1e-9)
        assertTrue(
            third.program.nativeProgressionAudit.filter { it.userFacingNotice }.last().reason.contains("banca se mantiene en 97 kg (AMRAP corto)"),
        )
    }

    @Test
    fun a_cycle_that_reaches_the_minimum_breaks_the_streak() {
        val (program, plain1) = program531()
        val benchDay = weeksOf(program).first().sessions[1]
        val logs1 = Cal5TestSupport.replacingLogOf(plain1, benchDay, shortBenchLog(program, 3, 1, null))
        val first = ProgramProgressEngine.completeCycle(program, null, 1, logs1)
        // Ciclo 2: cumple (6 ≥ 5). Ciclo 3: corto. La racha vale 1, no 2: solo se congela.
        val plain2 = Cal5TestSupport.emptyLogs(first.program, weeksOf(first.program), cycle = 2, runId = "run_531")
        val benchDay2 = weeksOf(first.program).first().sessions[1]
        val logs2 = Cal5TestSupport.replacingLogOf(plain2, benchDay2, shortBenchLog(first.program, 6, 2, "run_531"))
        val second = ProgramProgressEngine.completeCycle(first.program, null, 2, logs1 + logs2)
        assertEquals("sube de 108", 110.5, second.program.powerliftingProfile!!.benchTM!!, 1e-9)
        val plain3 = Cal5TestSupport.emptyLogs(second.program, weeksOf(second.program), cycle = 3, runId = "run_531")
        val benchDay3 = weeksOf(second.program).first().sessions[1]
        val logs3 = Cal5TestSupport.replacingLogOf(plain3, benchDay3, shortBenchLog(second.program, 2, 3, "run_531"))
        val third = ProgramProgressEngine.completeCycle(second.program, null, 3, logs1 + logs2 + logs3)
        assertEquals("racha 1: se mantiene", 110.5, third.program.powerliftingProfile!!.benchTM!!, 1e-9)
    }
}

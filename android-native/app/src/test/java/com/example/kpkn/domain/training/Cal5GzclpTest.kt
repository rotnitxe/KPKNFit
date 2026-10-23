package com.example.kpkn.domain.training

import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramRunState
import com.example.kpkn.data.models.SetPurpose
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.LiftSlot
import com.example.kpkn.data.protocols.ProgressionRule
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.domain.training.Cal5TestSupport.exerciseLog
import com.example.kpkn.domain.training.Cal5TestSupport.exerciseOf
import com.example.kpkn.domain.training.Cal5TestSupport.recipeOf
import com.example.kpkn.domain.training.Cal5TestSupport.replacingLogOf
import com.example.kpkn.domain.training.Cal5TestSupport.weeksOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * CAL5 · GZCLP (RES1 §6, Lefever): el T1 pasa de 5×3+ a 6×2+ y a 10×1+ con la misma carga cuando no completa su volumen base y, tras
 * la etapa 3, se reinicia con el 85 % de un 5RM nuevo. La AMRAP del T1 no cambia la carga y cuenta como mucho 10 repeticiones. El T2
 * tiene sus etapas (3×10, 3×8, 3×6) y el T3 sube cuando su última serie llega a 25.
 */
class Cal5GzclpTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    private val recipe = recipeOf("gzclp")

    @Test
    fun the_stage_table_matches_the_source() {
        val t1 = GzclpProgression.T1Stage.entries
        assertEquals(listOf(5 to 3, 6 to 2, 10 to 1), t1.map { it.sets to it.reps })
        assertEquals("volumen base del T1: 15, 12 y 10", listOf(15, 12, 10), t1.map { it.baseVolume })
        assertEquals(listOf("5×3+", "6×2+", "10×1+"), t1.map { it.label })
        val t2 = GzclpProgression.T2Stage.entries
        assertEquals("T2: 10x3, 8x3 y 6x3", listOf(30, 24, 18), t2.map { it.baseVolume })
        assertEquals(10, GzclpProgression.T1_AMRAP_CAP)
        assertEquals(25, GzclpProgression.T3_RAISE_REPS)
        assertEquals(ProgressionRule.StagedVolume(upperKg = 2.5, lowerKg = 5.0, t1AmrapCap = 10, t3RaiseReps = 25), recipe.progression)
    }

    @Test
    fun not_completing_the_base_volume_moves_to_the_next_stage_and_stage_3_restarts() {
        val s1 = GzclpProgression.T1Stage.STAGE_1
        assertEquals(GzclpProgression.TierDecision(1, 1, GzclpProgression.TierOutcome.PASSED), GzclpProgression.decideT1(s1, true))
        assertEquals(GzclpProgression.TierDecision(1, 2, GzclpProgression.TierOutcome.ADVANCE_STAGE), GzclpProgression.decideT1(s1, false))
        assertEquals(
            GzclpProgression.TierDecision(2, 3, GzclpProgression.TierOutcome.ADVANCE_STAGE),
            GzclpProgression.decideT1(GzclpProgression.T1Stage.STAGE_2, false),
        )
        assertEquals(
            GzclpProgression.TierDecision(3, 1, GzclpProgression.TierOutcome.RESTART),
            GzclpProgression.decideT1(GzclpProgression.T1Stage.STAGE_3, false),
        )
        assertEquals(
            GzclpProgression.TierDecision(3, 1, GzclpProgression.TierOutcome.RESTART),
            GzclpProgression.decideT2(GzclpProgression.T2Stage.STAGE_3, false),
        )
        // La reinicio usa el 85 % del 5RM nuevo: 5RM de 120 kg → 102 kg.
        assertEquals(102.0, GzclpProgression.restartLoadKg(120.0), 1e-9)
    }

    @Test
    fun the_t1_amrap_counts_ten_reps_at_most_and_never_changes_the_load() {
        assertEquals(7, GzclpProgression.cappedAmrapReps(7))
        assertEquals(10, GzclpProgression.cappedAmrapReps(10))
        assertEquals(10, GzclpProgression.cappedAmrapReps(14))
        assertEquals(0, GzclpProgression.cappedAmrapReps(-2))
        assertFalse(ProgramAutoregulationEngine.amrapMovesTm(recipe))
        listOf(0, 3, 4, 12).forEach { reps ->
            assertNull("$reps reps", ProgramAutoregulationEngine.amrapTmChange(recipe.progression, AmrapHit(LiftSlot.SQUAT, 85.0, 3, reps)))
        }
    }

    @Test
    fun the_t3_rises_when_the_last_set_reaches_25_reps() {
        assertFalse(GzclpProgression.t3ShouldRaise(24))
        assertTrue(GzclpProgression.t3ShouldRaise(25))
        assertTrue(GzclpProgression.t3ShouldRaise(31))
    }

    @Test
    fun the_overlay_rewrites_the_t1_and_t2_with_the_same_load() {
        val squatDay = recipe.weeks.first().days.first()
        val t1 = squatDay.slots.single { it.role == SlotRole.T1_MAIN }
        assertEquals(GzclpProgression.T1Stage.STAGE_1, GzclpProgression.t1StageOf(t1))
        val stage2 = GzclpProgression.overlay(
            recipe.weeks.first(),
            t1 = mapOf(LiftSlot.SQUAT to GzclpProgression.T1Stage.STAGE_2),
            t2 = mapOf(LiftSlot.SQUAT to GzclpProgression.T2Stage.STAGE_2),
        )
        val squat = stage2.days.first().slots
        val newT1 = squat.single { it.role == SlotRole.T1_MAIN }
        assertEquals(6, newT1.sets.size)
        assertTrue(newT1.sets.all { it.reps == 2 && it.percent == 85.0 })
        assertEquals("solo la última es AMRAP", listOf(false, false, false, false, false, true), newT1.sets.map { it.amrap })
        assertEquals(GzclpProgression.T1Stage.STAGE_2, GzclpProgression.t1StageOf(newT1))
        val newT2 = squat.single { it.role == SlotRole.T2_SUPPLEMENTAL }
        assertEquals(List(3) { 8 }, newT2.sets.map { it.reps })
        assertTrue("T2 con la misma carga", newT2.sets.all { it.percent == 65.0 })
        // Los demás días (banca, peso muerto, press) quedan en la etapa 1.
        stage2.days.drop(1).forEach { day ->
            assertEquals(5, day.slots.single { it.role == SlotRole.T1_MAIN }.sets.size)
        }
    }

    // ─── Con los registros: el motor del método ──────────────────────────────────

    private val profile = PowerliftingProfile(squat1RM = 200.0, bench1RM = 120.0, deadlift1RM = 220.0, overhead1RM = 80.0)

    private fun check(lift: LiftSlot?, role: SlotRole, planned: Int, done: Int, last: Int? = null, name: String? = null) =
        ProgramAutoregulationEngine.GzclpTierCheck(lift, role, planned, done, last, name)

    private fun outcomeOf(vararg checks: ProgramAutoregulationEngine.GzclpTierCheck): AuthoredProgressionEngine.MethodOutcome =
        AuthoredProgressionEngine.evaluate(
            Cal5TestSupport.fullProfile(), recipe,
            evidence = AuthoredProgressionEngine.CloseEvidence(gzclpChecks = checks.toList()),
        )

    @Test
    fun completing_the_base_volume_raises_the_tm_and_stays_in_the_stage() {
        val outcome = outcomeOf(check(LiftSlot.SQUAT, SlotRole.T1_MAIN, 15, 17, 5))
        assertEquals(185.0, outcome.changes.single { it.liftSlot == LiftSlot.SQUAT }.afterKg, 1e-9)
        assertEquals(GzclpProgression.T1Stage.STAGE_1, outcome.t1Stages.getValue(LiftSlot.SQUAT))
        assertTrue(outcome.holds.none { it.liftSlot == LiftSlot.SQUAT })
    }

    @Test
    fun a_base_volume_not_completed_freezes_the_tm_and_moves_on_with_the_same_load() {
        val outcome = outcomeOf(check(LiftSlot.BENCH, SlotRole.T1_MAIN, 15, 14, 2))
        assertTrue("la banca no sube", outcome.changes.none { it.liftSlot == LiftSlot.BENCH })
        val hold = outcome.holds.single { it.liftSlot == LiftSlot.BENCH }
        assertEquals(108.0, hold.tmKg, 1e-9)
        assertTrue(hold.note!!, hold.note!!.contains("5×3+") && hold.note!!.contains("6×2+"))
        assertEquals(GzclpProgression.T1Stage.STAGE_2, outcome.t1Stages.getValue(LiftSlot.BENCH))
        // Sin registros de los demás levantamientos: suben como siempre.
        assertEquals(185.0, outcome.changes.single { it.liftSlot == LiftSlot.SQUAT }.afterKg, 1e-9)
    }

    @Test
    fun stage_3_not_completed_asks_for_a_5rm_test_and_restarts_in_stage_1() {
        val outcome = outcomeOf(check(LiftSlot.DEADLIFT, SlotRole.T1_MAIN, 10, 9, 0))
        assertEquals(GzclpProgression.T1Stage.STAGE_1, outcome.t1Stages.getValue(LiftSlot.DEADLIFT))
        assertTrue(outcome.changes.none { it.liftSlot == LiftSlot.DEADLIFT })
        assertTrue(outcome.notes.single().contains("5RM"))
        assertTrue(outcome.notes.single().contains("85 %"))
    }

    @Test
    fun the_t2_has_its_own_stages_and_never_moves_the_tm() {
        val outcome = outcomeOf(check(LiftSlot.SQUAT, SlotRole.T2_SUPPLEMENTAL, 30, 27, 6))
        assertEquals(GzclpProgression.T2Stage.STAGE_2, outcome.t2Stages.getValue(LiftSlot.SQUAT))
        assertTrue(outcome.notes.single().contains("3×8"))
        // El T2 sin completar no congela el TM de la sentadilla (no hay registro de su T1: sube).
        assertEquals(185.0, outcome.changes.single { it.liftSlot == LiftSlot.SQUAT }.afterKg, 1e-9)
    }

    @Test
    fun a_t3_that_reaches_25_reps_tells_the_athlete_to_raise_the_load() {
        val outcome = outcomeOf(
            check(null, SlotRole.T3_ACCESSORY, 45, 60, 25, name = "Jalón al pecho"),
            check(null, SlotRole.T3_ACCESSORY, 45, 50, 20, name = "Remo"),
        )
        assertEquals(1, outcome.notes.size)
        assertEquals("T3 Jalón al pecho: la última serie llegó a 25 repeticiones; sube la carga.", outcome.notes.single())
    }

    // ─── Cierre de ciclo de punta a punta: etapa 1 → 2 → 3 → reinicio ────────────

    private fun gzProgram(): Pair<Program, List<com.example.kpkn.data.models.WorkoutLog>> {
        val materialized = Cal5TestSupport.materialize(recipe, profile = profile, id = "gz")
        val weeks = weeksOf(materialized)
        val last = weeks.last()
        val program = materialized.copy(
            runState = ProgramRunState(
                runId = "run_gz",
                cycleNumber = 1,
                weekId = last.id,
                weekInstanceId = ProgramProgressEngine.instanceIdFor(1, last.id),
            ),
        )
        return program to Cal5TestSupport.emptyLogs(program, weeks)
    }

    /** El registro del T1 de sentadilla de la semana 1: con [complete] se hace el volumen base; si no, la última serie se queda una repetición corta. */
    private fun squatT1Log(program: Program, cycle: Int, runId: String?, complete: Boolean): com.example.kpkn.data.models.WorkoutLog {
        val week1 = weeksOf(program).first()
        val squatDay = week1.sessions.first()
        val t1 = exerciseOf(squatDay, CatalogIds.SQ_LOW)
        val lastIndex = t1.sets.lastIndex
        return exerciseLog(program, week1, squatDay, t1, cycle = cycle, runId = runId, repsOf = { index, planned ->
            val target = planned.targetReps ?: 3
            if (!complete && index == lastIndex) (target - 1).coerceAtLeast(0) else target
        })
    }

    private fun squatT1(program: Program, weekIndex: Int = 0) =
        exerciseOf(weeksOf(program)[weekIndex].sessions.first(), CatalogIds.SQ_LOW)

    @Test
    fun the_t1_goes_through_the_three_stages_with_the_same_load_and_then_asks_for_a_5rm() {
        var (program, logs) = gzProgram()
        val tm = program.powerliftingProfile!!.squatTM!!
        assertEquals(180.0, tm, 1e-9)
        assertEquals(5, squatT1(program).sets.size)

        // Ciclo 1: la sentadilla no completa las 15 repeticiones → su TM se congela y el ciclo 2 trae 6×2+ con la misma carga (85 % de 180).
        val allLogs = mutableListOf<com.example.kpkn.data.models.WorkoutLog>()
        var cycle = 1
        var result = run {
            val weeks = weeksOf(program)
            val squatDay = weeks.first().sessions.first()
            val replaced = replacingLogOf(logs, squatDay, squatT1Log(program, cycle, null, complete = false))
            allLogs += replaced
            ProgramProgressEngine.completeCycle(program, null, cycle, allLogs)
        }
        assertTrue(result.advancedCycle)
        assertEquals("el TM de la sentadilla se mantiene", 180.0, result.program.powerliftingProfile!!.squatTM!!, 1e-9)
        assertEquals("los demás suben", 110.5, result.program.powerliftingProfile!!.benchTM!!, 1e-9)
        var stage2 = squatT1(result.program)
        assertEquals(6, stage2.sets.size)
        assertTrue(stage2.sets.all { it.targetReps == 2 })
        assertEquals(0.85 * 180.0, stage2.sets.first().weight!!, 1e-6)
        assertEquals("la AMRAP es la última, con 2 como mínimo", SetPurpose.AMRAP, stage2.sets.last().purpose)
        assertEquals(2, stage2.sets.last().amrapMinimumReps)
        assertEquals("la banca sigue en 5×3+", 5, exerciseOf(weeksOf(result.program).first().sessions[1], CatalogIds.BP).sets.size)
        val notice = result.program.nativeProgressionAudit.filter { it.userFacingNotice }.last().reason
        assertTrue(notice, notice.contains("sentadilla se mantiene en 180 kg"))
        assertTrue(notice, notice.contains("5×3+ a 6×2+"))

        // Ciclo 2: otra vez sin completar (11 de 12) → etapa 3, 10×1+ con la misma carga.
        program = result.program
        cycle = 2
        var logs2 = Cal5TestSupport.emptyLogs(program, weeksOf(program), cycle = cycle, runId = "run_gz")
        logs2 = replacingLogOf(logs2, weeksOf(program).first().sessions.first(), squatT1Log(program, cycle, "run_gz", complete = false))
        allLogs += logs2
        result = ProgramProgressEngine.completeCycle(program, null, cycle, allLogs)
        assertEquals(180.0, result.program.powerliftingProfile!!.squatTM!!, 1e-9)
        val stage3 = squatT1(result.program)
        assertEquals(10, stage3.sets.size)
        assertTrue(stage3.sets.all { it.targetReps == 1 })
        assertEquals(0.85 * 180.0, stage3.sets.first().weight!!, 1e-6)

        // Ciclo 3: la etapa 3 tampoco se completa → reinicio: vuelve a 5×3+, el TM sigue en 180 y el aviso pide el 5RM.
        program = result.program
        cycle = 3
        var logs3 = Cal5TestSupport.emptyLogs(program, weeksOf(program), cycle = cycle, runId = "run_gz")
        logs3 = replacingLogOf(logs3, weeksOf(program).first().sessions.first(), squatT1Log(program, cycle, "run_gz", complete = false))
        allLogs += logs3
        result = ProgramProgressEngine.completeCycle(program, null, cycle, allLogs)
        assertEquals(180.0, result.program.powerliftingProfile!!.squatTM!!, 1e-9)
        assertEquals("reinicio en la etapa 1", 5, squatT1(result.program).sets.size)
        val restartNotice = result.program.nativeProgressionAudit.filter { it.userFacingNotice }.last().reason
        assertTrue(restartNotice, restartNotice.contains("prueba un 5RM nuevo"))
    }

    @Test
    fun completing_the_base_volume_in_stage_2_raises_the_tm_and_keeps_the_stage() {
        val (program, logs) = gzProgram()
        val week1Squat = weeksOf(program).first().sessions.first()
        val first = ProgramProgressEngine.completeCycle(
            program, null, 1, replacingLogOf(logs, week1Squat, squatT1Log(program, 1, null, complete = false)),
        )
        // Ciclo 2: completa las 12 repeticiones de 6×2+ → el TM sube 5 kg (180 → 185) y el T1 sigue en 6×2+.
        val logs2 = Cal5TestSupport.emptyLogs(first.program, weeksOf(first.program), cycle = 2, runId = "run_gz")
        val squatDay2 = weeksOf(first.program).first().sessions.first()
        val second = ProgramProgressEngine.completeCycle(
            first.program, null, 2,
            replacingLogOf(logs2, squatDay2, squatT1Log(first.program, 2, "run_gz", complete = true)),
        )
        assertEquals(185.0, second.program.powerliftingProfile!!.squatTM!!, 1e-9)
        val t1 = squatT1(second.program)
        assertEquals(6, t1.sets.size)
        assertEquals(0.85 * 185.0, t1.sets.first().weight!!, 1e-6)
    }

    @Test
    fun the_published_description_says_the_stages_and_the_t3_rule() {
        val description = com.example.kpkn.data.protocols.PROTOCOL_LIBRARY.first { it.id == "gzclp" }.description
        assertTrue(description, description.contains("6×2+") && description.contains("10×1+"))
        assertTrue(description, description.contains("25"))
        assertFalse("ya no dice que es solo la etapa 1", description.contains("sin cambio de etapa"))
    }
}

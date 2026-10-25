package com.example.kpkn.domain.training

import com.example.kpkn.data.models.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ProgramProgressReportTest {
    private fun program(): Program = Program("p", "Programa", structure = ProgramStructure.COMPLEX,
        runState = ProgramRunState("run", weekId = "w2", blockId = "b1"),
        macrocycles = listOf(Macrocycle("m", "Macro", (1..2).map { b ->
            Block("b$b", "Bloque $b", goal = BlockGoal.ACCUMULATION,
                mesocycles = listOf(Mesocycle("meso$b", "Meso", weeks = (1..2).map { n ->
                    val w = (b - 1) * 2 + n
                    ProgramWeek("w$w", "Semana $w", sessions = (1..2).map { s -> Session("w${w}s$s", "Sesión $s") },
                        executionKind = if (w == 4) WeekExecutionKind.DELOAD else WeekExecutionKind.TRAINING)
                })))
        })))
    private fun log(w: Int, s: Int) = WorkoutLog("l$w$s", "p", "w${w}s$s", "Sesión", "2026-10-01T12:00:00Z", 60, weekId = "w$w")
    @Test fun adherence_ignores_future_weeks_and_reports_deload() {
        val report = ProgramProgressReportEngine.build(program(), listOf(log(1, 1), log(1, 2), log(2, 1)), emptyList())
        assertEquals(75, report.adherencePercent)
        assertEquals(2, report.position.weekIndex)
        assertEquals(4, report.position.weekCount)
        assertEquals("Bloque 1", report.position.blockName)
        assertEquals("Descarga en 2 semanas", report.position.nextMilestone)
    }
    @Test fun decisions_are_sorted_and_own_decisions_are_not_duplicated() {
        val p = program().copy(programDecisions = listOf(ProgramDecision("d", 20, "WEEKLY_CHECKIN", "Semana ajustada")),
            effectiveWeekRecipes = listOf(EffectiveWeekRecipe(1, appliedProposals = listOf(
                AppliedRecipeProposal("a", "ADJUST_TM", "TM", 10), AppliedRecipeProposal("d", "WEEKLY_CHECKIN", "Semana", 20)))))
        val report = ProgramProgressReportEngine.build(p, emptyList(), emptyList())
        assertEquals(listOf(20L, 10L), report.decisions.map { it.atMs })
    }
    @Test fun repeating_program_reports_current_cycle_and_ignores_old_logs() {
        val p = program().copy(structure = ProgramStructure.SIMPLE,
            macrocycles = listOf(program().macrocycles.first().copy(blocks = program().macrocycles.first().blocks.take(1))),
            runState = ProgramRunState("run", weekId = "w2", blockId = "b1", cycleNumber = 3))
        val report = ProgramProgressReportEngine.build(p, listOf(log(1, 1).copy(cycleNumber = 2)), emptyList())
        assertEquals(3, report.position.cycle)
        assertEquals(0, report.adherencePercent)
    }
    @Test fun optional_duplicate_and_foreign_logs_do_not_inflate_adherence() {
        val logs = listOf(log(1, 1), log(1, 1), log(1, 2).copy(programId = "other"), log(2, 1).copy(sessionId = "optional"))
        assertEquals(25, ProgramProgressReportEngine.build(program(), logs, emptyList()).adherencePercent)
    }
    @Test fun only_current_reference_audits_are_presented_as_protocol_decisions() {
        val p = program()
        val audits = listOf(
            AutoregulationAuditEntry(atMs = 10, mode = AutoregulationMode.PROPOSE,
                kinds = listOf(AutoregulationProposalKind.ADJUST_TM), resolutionReason = "Referencia confirmada"),
            AutoregulationAuditEntry(atMs = 20, mode = AutoregulationMode.AUTO,
                kinds = listOf(AutoregulationProposalKind.SCALE_WEEK_VOLUME), resolutionReason = "Dato derivado antiguo"),
        )
        val withHistory = p.copy(
            runState = p.runState!!.copy(autoregulationAudit = audits),
            effectiveWeekRecipes = listOf(EffectiveWeekRecipe(1, appliedProposals = listOf(
                AppliedRecipeProposal("legacy", AutoregulationProposalKind.SCALE_WEEK_VOLUME.name, "Dato derivado antiguo", 30),
            ))),
        )
        val report = ProgramProgressReportEngine.build(withHistory, emptyList(), emptyList())
        assertEquals(listOf("Referencia confirmada"), report.decisions.map { it.detail })
        assertEquals("PROTOCOL", report.decisions.single().source)
    }

    @Test fun decision_json_is_additive_and_keeps_history() {
        assertTrue(Json.decodeFromString<Program>("""{"id":"p","name":"P"}""").programDecisions.isEmpty())
        val p = program().copy(programDecisions = listOf(ProgramDecision("d", 20, "MANUAL", "Cambio")))
        assertEquals(p, Json.decodeFromString<Program>(Json.encodeToString(p)))
    }
}

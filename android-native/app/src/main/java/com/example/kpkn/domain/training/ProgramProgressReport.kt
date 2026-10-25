package com.example.kpkn.domain.training

import com.example.kpkn.data.models.*
import kotlin.math.abs
import kotlin.math.roundToInt

data class ProgressPosition(val scopeLabel: String, val blockName: String?, val blockGoalLabel: String?, val weekIndex: Int?, val weekCount: Int?, val cycle: Int?, val nextMilestone: String?)
data class ProgramWeekAdherence(val weekId: String, val weekName: String, val plannedSessions: Int, val completedSessions: Int)
data class LiftTrend(val exerciseName: String, val firstE1rmKg: Double, val lastE1rmKg: Double)
data class DecisionLine(val atMs: Long, val source: String, val title: String, val detail: String)
data class ProgramProgressReport(
    val position: ProgressPosition,
    val adherencePercent: Int,
    val weeks: List<ProgramWeekAdherence>,
    val lifts: List<LiftTrend>,
    val decisions: List<DecisionLine>,
)

/** Progreso de la ejecución actual e historial de decisiones del programa. Las semanas futuras no penalizan. */
object ProgramProgressReportEngine {
    fun build(program: Program, logs: List<WorkoutLog>, catalog: List<ExerciseMuscleInfo>): ProgramProgressReport {
        val ordered = ProgramHierarchyIndex(program).orderedWeeks()
        val run = program.runState
        val scope = ProgramScopes.of(program)
        val currentIndex = ordered.indexOfFirst { it.week.id == run?.weekId }
            .takeIf { it >= 0 } ?: if (run?.status == ProgramRunStatus.COMPLETED) ordered.lastIndex else null
        val current = currentIndex?.let { ordered.getOrNull(it) }
        val block = ordered.firstOrNull { it.blockId == run?.blockId }?.block ?: current?.block
        val programLogs = logs.filter { it.programId == program.id }
        val cycle = run?.cycleNumber ?: 1
        val currentLogs = programLogs.filter { log ->
            if (!log.calendarBreakId.isNullOrBlank()) return@filter false
            val fromInstance = (log.weekInstanceId ?: log.weekId)?.let(ProgramProgressEngine::cycleFromInstanceId)
            if (log.cycleNumber != null && fromInstance != null && log.cycleNumber != fromInstance) return@filter false
            val logCycle = log.cycleNumber ?: fromInstance
            (run == null || log.programRunId == run.runId || (log.programRunId == null && cycle == 1)) &&
                (logCycle == cycle || (logCycle == null && cycle == 1))
        }
        val weeks = ordered.map { location ->
            val required = location.week.sessions.filter { it.requirement == SessionRequirement.REQUIRED }.map { it.id }.toSet()
            ProgramWeekAdherence(location.week.id, location.week.name, required.size,
                ProgramProgressEngine.logsForInstance(currentLogs, program.id,
                    ProgramProgressEngine.instanceIdFor(cycle, location.week.id), cycle, run?.runId)
                    .filter { it.sessionId in required }.map { it.sessionId }.distinct().size)
        }
        val due = currentIndex?.let { weeks.take(it + 1) }.orEmpty()
        val planned = due.sumOf { it.plannedSessions }
        val adherence = if (planned == 0) 0 else (due.sumOf { it.completedSessions } * 100.0 / planned).roundToInt()
        val nextDeload = currentIndex?.let { index -> ordered.indices.firstOrNull { it > index && ordered[it].week.executionKind == WeekExecutionKind.DELOAD } }
        val milestone = when {
            nextDeload != null -> "Descarga en ${nextDeload - currentIndex!!} semanas"
            block?.goal in setOf(BlockGoal.PEAK, BlockGoal.REALIZATION, BlockGoal.TAPER) -> "Test o competición al cerrar el bloque"
            else -> null
        }
        val trends = ProgramAnalyticsEngine.analyze(program, currentLogs, catalog).progression
            .filter { it.firstE1rm != null && it.lastE1rm != null }
            .sortedByDescending { abs(it.deltaE1rm ?: 0.0) }.take(3)
            .map { LiftTrend(it.exerciseName, it.firstE1rm!!, it.lastE1rm!!) }
        val decisions = buildList {
            addAll(program.programDecisions.map { DecisionLine(it.atMs, it.source, it.title, it.detail) })
            addAll(program.effectiveWeekRecipes.flatMap { it.appliedProposals }.distinctBy { it.proposalId }
                .filterNot { it.kind in setOf("WEEKLY_CHECKIN", "BLOCK_REVIEW") }
                .filter { it.kind in setOf("ADJUST_TM", "ADJUST_XRM") }
                .map { DecisionLine(it.acceptedAtMs, "AUTOREGULATION:${it.kind}", "Propuesta aplicada", it.summary) })
            val referenceKinds = setOf(AutoregulationProposalKind.ADJUST_TM, AutoregulationProposalKind.ADJUST_XRM)
            addAll(run?.autoregulationAudit.orEmpty().filter { entry ->
                entry.kinds.isNotEmpty() && entry.kinds.all { it in referenceKinds }
            }.map {
                DecisionLine(it.atMs, "PROTOCOL", "Decisión de referencias de carga", it.resolutionReason)
            })
            addAll(program.nativeProgressionAudit.filter { it.status == NativeProgressionResolutionStatus.APPLIED }
                .map { DecisionLine(it.resolvedAtMs, "NATIVE_PROGRESSION", "Progresión aplicada", it.reason) })
        }.sortedByDescending { it.atMs }.take(50)
        return ProgramProgressReport(
            ProgressPosition(ProgramScopes.label(scope), block?.name, block?.goal?.label, currentIndex?.plus(1),
                ordered.size.takeIf { it > 0 }, run?.cycleNumber?.takeIf { scope is ProgramScope.RepeatingWeeks }, milestone),
            adherence, weeks, trends, decisions,
        )
    }
}

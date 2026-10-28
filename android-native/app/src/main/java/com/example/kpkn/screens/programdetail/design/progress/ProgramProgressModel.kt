package com.example.kpkn.screens.programdetail.design.progress

import androidx.compose.ui.graphics.Color
import com.example.kpkn.data.models.KeyDateType
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.training.ProgramDetailHelpers
import com.example.kpkn.domain.training.progress.LiftTrend
import com.example.kpkn.domain.training.progress.LiftTrendEngine
import com.example.kpkn.domain.training.progress.LiftTrendStatus
import com.example.kpkn.domain.training.progress.WeeklyWorkEngine
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.programdetail.design.programPhaseColor
import com.example.kpkn.screens.programdetail.design.programPhaseOf

/*
 * Modelo de la pestaña Progreso de Program Detail. Sin Compose salvo el color de fase, para que la prueba JVM decida cada cifra y cada
 * frase. Los datos salen de los motores de dominio (tendencia de fuerza y trabajo semanal); aquí solo se fija el alcance, se cuentan
 * las sesiones y se redactan los textos.
 */

/** Series de diferencia a partir de las que una semana empezada se anota como «series menos de lo planificado». */
private const val SHORTFALL_SETS = 5

/** Ejercicios que enseña la lista de fuerza como máximo; el contador de «Todos» cuenta el resto. */
private const val MAX_LIFT_ROWS = 5

/** Semanas de estancamiento que se dan por buenas si el motor no las calcula. */
private const val DEFAULT_STALLED_WEEKS = 3

/** Alcance de la pestaña: el programa entero, un bloque o una semana. */
internal enum class ProgressScope { PROGRAM, BLOCK, WEEK }

/**
 * Cifras del periodo. Las sesiones y las series previstas solo cuentan las semanas que ya han empezado; el tonelaje y las series
 * hechas, todo lo registrado en el alcance.
 */
internal data class ProgressKpis(
    val sessionsDone: Int,
    val sessionsPlanned: Int,
    val setsDone: Int,
    val setsPlanned: Int,
    val tonnageKg: Double,
) {
    /** Cumplimiento de sesiones en %, con tope del 100 %; null si no había ninguna sesión prevista. */
    val compliancePct: Int?
        get() = if (sessionsPlanned == 0) {
            null
        } else {
            Math.round(100.0 * minOf(sessionsDone, sessionsPlanned) / sessionsPlanned).toInt()
        }
}

/**
 * Una fila de la lista de fuerza: ejercicio, estado, último e1RM, cambio desde el primero, puntos de la línea y su pie. [caption] ya
 * viene redactada («3 sesiones», «5 sem. sin subir»…).
 */
internal data class ProgressLiftRow(
    val name: String,
    val status: LiftTrendStatus,
    val currentKg: Double?,
    val deltaKg: Double?,
    val points: List<Double>,
    val caption: String,
)

/** Una barra de la gráfica de series: [done] hechas frente a [planned] previstas de una semana, con el color de su fase. */
internal data class ProgressWeekBar(
    val label: String,
    val done: Int,
    val planned: Int,
    val color: Color,
    val isCurrent: Boolean,
)

/** Todo lo que pinta la pestaña Progreso para un alcance, ya calculado. */
internal data class ProgramProgressUi(
    /** Periodo en una línea: «En 2 semanas», «En el bloque» o «Esta semana». */
    val periodLabel: String,
    /** Frase de resumen sobre la fuerza y el estado de los ejercicios. */
    val headline: String,
    val kpis: ProgressKpis,
    /** Hasta el tope de filas de fuerza (por defecto [MAX_LIFT_ROWS]) de los ejercicios con sesiones, del que más sesiones tiene al que menos. */
    val lifts: List<ProgressLiftRow>,
    /** Todos los ejercicios con sesiones, sin el tope de la lista. */
    val allLiftsCount: Int,
    /** Una barra por semana del alcance; vacía en el alcance de semana. */
    val weeks: List<ProgressWeekBar>,
    /** Aviso de la primera semana empezada (no la actual) con mucho menos trabajo del previsto; null si no hay ninguna. */
    val weekNote: String?,
)

/**
 * Calcula la pestaña Progreso de [program] para [scope]. Solo cuentan los [logs] del programa cuya semana está en el alcance. Una
 * semana ha empezado si va antes de [currentWeekId] o es esa misma; si [currentWeekId] no está en el programa, han empezado todas.
 * [selectedBlockId] y [selectedWeekId] fijan el alcance de bloque o de semana. [maxLifts] pone el tope de filas de la lista de fuerza; los
 * ejercicios que no caben cuentan en [ProgramProgressUi.allLiftsCount].
 */
internal fun buildProgramProgress(
    program: Program,
    logs: List<WorkoutLog>,
    scope: ProgressScope,
    selectedBlockId: String?,
    selectedWeekId: String?,
    currentWeekId: String?,
    maxLifts: Int = MAX_LIFT_ROWS,
): ProgramProgressUi {
    val programWeeks = programWeeksOf(program)
    val currentPosition = programWeeks.indexOfFirst { it.id == currentWeekId }
    val scopeWeeks = when (scope) {
        ProgressScope.PROGRAM -> programWeeks
        ProgressScope.BLOCK -> programWeeks.filter { it.blockId == selectedBlockId }
        ProgressScope.WEEK -> programWeeks.filter { it.id == selectedWeekId }
    }
    val scopeWeekIds = scopeWeeks.mapTo(HashSet()) { it.id }
    val scopeLogs = logs.filter { log ->
        log.programId == program.id && log.weekId != null && log.weekId in scopeWeekIds
    }
    // Sin semana actual en el programa, currentPosition es -1 y todas las semanas cuentan como empezadas.
    val started = scopeWeeks.filter { currentPosition < 0 || it.position <= currentPosition }
    val work = WeeklyWorkEngine.byWeek(
        logs = scopeLogs,
        weekOrder = scopeWeeks.map { it.id },
        plannedSetsByWeek = scopeWeeks.associate { it.id to it.plannedSets },
    )
    val workByWeek = work.associateBy { it.weekId }
    fun doneOf(week: ScopeWeek): Int = workByWeek[week.id]?.hardSets ?: 0
    val trends = LiftTrendEngine.trends(scopeLogs).filter { it.sessions > 0 }.sortedByDescending { it.sessions }
    val bars = scopeWeeks.map { week ->
        ProgressWeekBar(
            label = "S${week.position + 1}",
            done = doneOf(week),
            planned = week.plannedSets,
            color = week.color,
            isCurrent = week.id == currentWeekId,
        )
    }
    val shortfall = started.firstOrNull { week ->
        week.id != currentWeekId && week.plannedSets - doneOf(week) > SHORTFALL_SETS
    }
    return ProgramProgressUi(
        periodLabel = periodLabelOf(scope, started.size),
        headline = headlineOf(hasLogs = scopeLogs.isNotEmpty(), trends = trends),
        kpis = ProgressKpis(
            sessionsDone = scopeLogs.mapTo(HashSet()) { it.weekId to it.sessionId }.size,
            sessionsPlanned = started.sumOf { it.sessions },
            setsDone = work.sumOf { it.hardSets },
            setsPlanned = started.sumOf { it.plannedSets },
            tonnageKg = work.sumOf { it.tonnageKg },
        ),
        lifts = trends.take(maxLifts).map { it.toRow() },
        allLiftsCount = trends.size,
        weeks = if (scope == ProgressScope.WEEK) emptyList() else bars,
        weekNote = shortfall?.let { week ->
            "Semana ${week.position + 1}: ${week.plannedSets - doneOf(week)} series menos de lo planificado."
        },
    )
}

/**
 * Una semana del programa: su bloque, su posición global (0 = la primera), sus sesiones, sus series previstas y el color de la fase de
 * su bloque.
 */
private data class ScopeWeek(
    val id: String,
    val blockId: String,
    val position: Int,
    val sessions: Int,
    val plannedSets: Int,
    val color: Color,
)

/**
 * Las semanas del programa en orden de bloques. La fase sale como en la cinta de fases: [programPhaseOf] con la competición de
 * cualquiera de las semanas del bloque. Las series previstas suman las series de todos los ejercicios de cada sesión.
 */
private fun programWeeksOf(program: Program): List<ScopeWeek> {
    val roadmap = ProgramDetailHelpers.buildRoadmapBlocks(program)
    val weeks = mutableListOf<ScopeWeek>()
    for (block in roadmap) {
        val blockWeeks = ProgramDetailHelpers.getWeeksForBlock(block.id, roadmap, program)
        val phase = programPhaseOf(
            goal = block.goal,
            hasCompetitionWeek = blockWeeks.any { it.keyDateType == KeyDateType.COMPETITION },
        )
        // Sin fase (una rutina, un bloque sin objetivo), lo hecho va en el acento de «Hechas»: en gris parecería lo previsto.
        val color = if (phase == null) SymbolPalette.musculo else programPhaseColor(phase)
        for (week in blockWeeks) {
            weeks += ScopeWeek(
                id = week.id,
                blockId = block.id,
                position = weeks.size,
                sessions = week.sessions.size,
                plannedSets = week.sessions.sumOf { session -> session.allExercises().sumOf { it.sets.size } },
                color = color,
            )
        }
    }
    return weeks
}

/** Periodo en una línea: el número de semanas empezadas en el programa, el bloque o la semana. */
private fun periodLabelOf(scope: ProgressScope, startedWeeks: Int): String = when (scope) {
    ProgressScope.PROGRAM -> if (startedWeeks == 1) "En 1 semana" else "En $startedWeeks semanas"
    ProgressScope.BLOCK -> "En el bloque"
    ProgressScope.WEEK -> "Esta semana"
}

/**
 * Frase de resumen: cuántos ejercicios suben y, si los hay, el primero estancado y el primero que baja. [trends] llega ordenada por
 * sesiones; la cuenta incluye todos los ejercicios, no solo la lista que se enseña.
 */
private fun headlineOf(hasLogs: Boolean, trends: List<LiftTrend>): String {
    if (!hasLogs) return "Aún no hay entrenos registrados en este periodo."
    val withTrend = trends.filter { it.status != LiftTrendStatus.NO_TREND }
    if (withTrend.isEmpty()) return "Aún no hay datos para ver tendencias."
    val up = withTrend.count { it.status == LiftTrendStatus.UP }
    val summary = "Subes en $up de ${withTrend.size} ejercicios."
    val stalled = trends.firstOrNull { it.status == LiftTrendStatus.STAGNANT }
    val down = trends.firstOrNull { it.status == LiftTrendStatus.DOWN }
    return when {
        stalled != null && down != null ->
            "$summary ${stalled.exerciseName} lleva ${stalledWeeksOf(stalled)} semanas parado y ${down.exerciseName} baja."
        stalled != null -> "$summary ${stalled.exerciseName} lleva ${stalledWeeksOf(stalled)} semanas parado."
        down != null -> "$summary ${down.exerciseName} baja."
        else -> summary
    }
}

/** Fila de fuerza de una tendencia: nombre, estado, último e1RM, cambio, puntos y pie. */
private fun LiftTrend.toRow(): ProgressLiftRow = ProgressLiftRow(
    name = exerciseName,
    status = status,
    currentKg = currentE1rm,
    deltaKg = deltaKg,
    points = points,
    caption = liftCaption(this),
)

/** Pie de una fila: sesiones si hay tendencia, semanas sin subir si está estancado y, si no hay tendencia aún, eso mismo. */
private fun liftCaption(trend: LiftTrend): String = when (trend.status) {
    LiftTrendStatus.UP, LiftTrendStatus.DOWN -> "${trend.sessions} sesiones"
    LiftTrendStatus.STAGNANT -> "${stalledWeeksOf(trend)} sem. sin subir"
    LiftTrendStatus.NO_TREND ->
        if (trend.sessions == 1) "1 sesión: aún sin tendencia" else "${trend.sessions} sesiones: aún sin tendencia"
}

/** Semanas que lleva parado un ejercicio estancado; si el motor no las da, las [DEFAULT_STALLED_WEEKS] por defecto. */
private fun stalledWeeksOf(trend: LiftTrend): Int = trend.stalledWeeks ?: DEFAULT_STALLED_WEEKS

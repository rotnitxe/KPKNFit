package com.example.kpkn.screens.programdetail.design.progress

import androidx.compose.ui.graphics.Color
import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.KeyDateType
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.SessionRequirement
import com.example.kpkn.data.models.WeekExecutionKind
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.training.ProgramDetailHelpers
import com.example.kpkn.domain.training.progress.LiftTrend
import com.example.kpkn.domain.training.progress.LiftTrendEngine
import com.example.kpkn.domain.training.progress.LiftTrendStatus
import com.example.kpkn.domain.training.progress.WeekWork
import com.example.kpkn.domain.training.progress.WeeklyWorkEngine
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.programdetail.design.programPhaseColor
import com.example.kpkn.screens.programdetail.design.programPhaseOf
import java.time.LocalDate
import kotlin.math.roundToInt

/*
 * Modelo de la pestaña Progreso de Program Detail (lienzo v6, escenas g-summary, g-work, g-adh y g-empty). Sin Compose salvo el color de
 * fase, para que la prueba JVM decida cada cifra y cada frase. Los datos salen de los motores de dominio (tendencia de fuerza y trabajo
 * semanal) y de los registros del alcance; aquí solo se fija el alcance, se cuenta y se redactan los textos.
 */

/** Series de diferencia a partir de las que una semana empezada se anota como «series menos de lo planificado». */
private const val SHORTFALL_SETS = 5

/** Ejercicios que enseña la lista de fuerza como máximo; «Todos» abre la lista entera. */
private const val MAX_LIFT_ROWS = 5

/** Semanas de estancamiento que se dan por buenas si el motor no las calcula. */
private const val DEFAULT_STALLED_WEEKS = 3

/** Alcance de la pestaña: el programa entero, un bloque o una semana. */
internal enum class ProgressScope { PROGRAM, BLOCK, WEEK }

/**
 * Cifras del periodo. Las sesiones previstas son las obligatorias de las semanas que ya han empezado (las opcionales no penalizan); el
 * tonelaje y las series hechas, todo lo registrado en el alcance.
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
 * Una fila de la lista de fuerza: ejercicio ([key] es su identidad, la de la ficha de progreso), estado, último e1RM, cambio desde el
 * primero, puntos de la línea y su pie ya redactado («6 sesiones», «3 sem. sin subir», «Baja 2 semanas», «1 sesión»).
 */
internal data class ProgressLiftRow(
    val key: String,
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

/** Una barra de «Tonelaje por semana». */
internal data class ProgressTonnageBar(val label: String, val tonnageKg: Double, val isCurrent: Boolean)

/** «Trabajo acumulado»: tonelaje, repeticiones efectivas, minutos y sesiones registradas del alcance, y el tonelaje por semana. */
internal data class ProgressWorkTotals(
    val tonnageKg: Double,
    val reps: Int,
    val minutes: Int,
    val sessions: Int,
    val weeks: List<ProgressTonnageBar>,
)

/** Todo lo que pinta la pestaña Progreso para un alcance, ya calculado. */
internal data class ProgramProgressUi(
    /** Eyebrow del resumen: «En 4 semanas» o, con una sola semana, «Semana 1». */
    val periodLabel: String,
    /** Frase de resumen sobre la fuerza y el estado de los ejercicios. */
    val headline: String,
    /** Sin ningún ejercicio con tendencia (g-empty): las cifras cambian de forma y la lista de fuerza no lleva «Todos» ni nota. */
    val early: Boolean,
    val kpis: ProgressKpis,
    /** Pie del tonelaje: «+9 % vs bloque 1», o el periodo si no hay con qué comparar. */
    val tonnageFooter: String,
    /** Hasta el tope de filas de fuerza de los ejercicios con sesiones, del que más sesiones tiene al que menos. */
    val lifts: List<ProgressLiftRow>,
    /** Todos los ejercicios con sesiones, sin tope: los de «Todos». */
    val allLifts: List<ProgressLiftRow>,
    /** Una barra por semana del alcance; vacía en el alcance de semana. */
    val weeks: List<ProgressWeekBar>,
    /** Aviso de la primera semana empezada (no la actual) con mucho menos trabajo del previsto; null si no hay ninguna. */
    val weekNote: String?,
    val totals: ProgressWorkTotals,
    /** Cumplimiento por semana; null si el alcance no tiene sesiones obligatorias. */
    val adherence: ProgressAdherence?,
    /** RIR medio por semana; null si no hay RIR registrado. */
    val effort: ProgressEffort?,
    /** Molestias del alcance; null si no hay ninguna. */
    val discomforts: ProgressDiscomforts?,
    /** Detalle de cada ejercicio de la lista, por su [ProgressLiftRow.key]. */
    val liftDetails: Map<String, ProgressLiftDetail>,
) {
    /** Ejercicios con sesiones en el alcance. */
    val allLiftsCount: Int
        get() = allLifts.size
}

/**
 * Calcula la pestaña Progreso de [program] para [scope]. Solo cuentan los [logs] del programa cuya semana está en el alcance. Una
 * semana ha empezado si va antes de [currentWeekId] o es esa misma; si [currentWeekId] no está en el programa, han empezado todas.
 * [selectedBlockId] y [selectedWeekId] fijan el alcance de bloque o de semana. [maxLifts] pone el tope de filas de la lista de fuerza.
 * [today] fecha las mejores series del detalle de cada ejercicio («Hoy»).
 */
internal fun buildProgramProgress(
    program: Program,
    logs: List<WorkoutLog>,
    scope: ProgressScope,
    selectedBlockId: String?,
    selectedWeekId: String?,
    currentWeekId: String?,
    maxLifts: Int = MAX_LIFT_ROWS,
    today: LocalDate = LocalDate.now(),
): ProgramProgressUi {
    val programWeeks = progressWeeksOf(program)
    val currentPosition = programWeeks.indexOfFirst { it.id == currentWeekId }
    val scopeWeeks = when (scope) {
        ProgressScope.PROGRAM -> programWeeks
        ProgressScope.BLOCK -> programWeeks.filter { it.blockId == selectedBlockId }
        ProgressScope.WEEK -> programWeeks.filter { it.id == selectedWeekId }
    }
    val scopeWeekIds = scopeWeeks.mapTo(HashSet()) { it.id }
    val programLogs = logs.filter { it.programId == program.id && it.weekId != null }
    val scopeLogs = programLogs.filter { it.weekId in scopeWeekIds }
    // Sin semana actual en el programa, currentPosition es -1 y todas las semanas cuentan como empezadas.
    val started = scopeWeeks.filter { currentPosition < 0 || it.position <= currentPosition }
    val work = WeeklyWorkEngine.byWeek(
        logs = scopeLogs,
        weekOrder = scopeWeeks.map { it.id },
        plannedSetsByWeek = scopeWeeks.associate { it.id to it.plannedSets },
        plannedRirByWeek = scopeWeeks.mapNotNull { week -> week.plannedRir?.let { week.id to it } }.toMap(),
    )
    val workByWeek = work.associateBy { it.weekId }
    fun doneOf(week: ProgressScopeWeek): Int = workByWeek[week.id]?.hardSets ?: 0
    val trends = LiftTrendEngine.trends(scopeLogs).filter { it.sessions > 0 }.sortedByDescending { it.sessions }
    val sessionsByLift = liftSessionsOf(scopeLogs)
    val rows = trends.map { it.toRow(sessionsByLift[it.exerciseKey].orEmpty()) }
    val bars = scopeWeeks.map { week ->
        ProgressWeekBar(
            label = "S${week.position + 1}",
            done = doneOf(week),
            planned = week.plannedSets,
            color = week.color,
            isCurrent = week.id == currentWeekId,
        )
    }
    val loggedSessions = scopeLogs.mapTo(HashSet()) { it.weekId to it.sessionId }
    val shortfall = started.firstOrNull { week ->
        week.id != currentWeekId && week.plannedSets - doneOf(week) > SHORTFALL_SETS
    }
    val kpis = ProgressKpis(
        sessionsDone = loggedSessions.size,
        sessionsPlanned = started.sumOf { it.requiredSessionIds.size },
        setsDone = work.sumOf { it.hardSets },
        setsPlanned = started.sumOf { it.plannedSets },
        tonnageKg = work.sumOf { it.tonnageKg },
    )
    val early = trends.none { it.status != LiftTrendStatus.NO_TREND }
    val deloadWeek = programWeeks.firstOrNull { it.isDeload && (currentPosition < 0 || it.position > currentPosition) }
    return ProgramProgressUi(
        periodLabel = periodLabelOf(started),
        headline = headlineOf(hasLogs = scopeLogs.isNotEmpty(), trends = trends),
        early = early,
        kpis = kpis,
        tonnageFooter = tonnageComparison(
            programWeeks = programWeeks,
            programLogs = programLogs,
            scope = scope,
            selectedBlockId = selectedBlockId,
            selectedWeekId = selectedWeekId,
            currentWeekId = currentWeekId,
        ) ?: periodFooterOf(started.size),
        lifts = rows.take(maxLifts),
        allLifts = rows,
        weeks = if (scope == ProgressScope.WEEK) emptyList() else bars,
        weekNote = shortfall?.let { week -> shortfallNote(week, doneOf(week), loggedSessions) },
        totals = ProgressWorkTotals(
            tonnageKg = kpis.tonnageKg,
            reps = work.sumOf { it.reps },
            minutes = work.sumOf { it.minutes },
            sessions = scopeLogs.size,
            weeks = if (scope == ProgressScope.WEEK) {
                emptyList()
            } else {
                scopeWeeks.map { week ->
                    ProgressTonnageBar("S${week.position + 1}", workByWeek[week.id]?.tonnageKg ?: 0.0, week.id == currentWeekId)
                }
            },
        ),
        adherence = progressAdherenceOf(
            scopeWeeks = scopeWeeks,
            currentPosition = currentPosition,
            currentWeekId = currentWeekId,
            loggedSessions = loggedSessions,
            compliancePct = kpis.compliancePct,
            scopeLogs = scopeLogs,
        ),
        effort = progressEffortOf(scopeWeeks, work),
        discomforts = progressDiscomfortsOf(scopeLogs, startedWeeks = started.size),
        liftDetails = rows.associate { row ->
            row.key to buildLiftDetail(
                row = row,
                trend = trends.first { it.exerciseKey == row.key },
                sessions = sessionsByLift[row.key].orEmpty(),
                weekNumberOf = { weekId -> programWeeks.firstOrNull { it.id == weekId }?.let { it.position + 1 } },
                deloadWeekNumber = deloadWeek?.let { it.position + 1 },
                program = program,
                currentPosition = currentPosition,
                today = today,
            )
        },
    )
}

/**
 * Una semana del programa: su bloque, su posición global (0 = la primera), sus sesiones obligatorias (con su día), sus series y su RIR
 * previstos, si es de descarga y el color de la fase de su bloque.
 */
internal data class ProgressScopeWeek(
    val id: String,
    val blockId: String,
    val position: Int,
    val requiredSessionIds: List<String>,
    val sessionDays: Map<String, Int?>,
    val plannedSets: Int,
    val plannedRir: Double?,
    val isDeload: Boolean,
    val color: Color,
)

/**
 * Las semanas del programa en orden de bloques. La fase sale como en la cinta de fases: [programPhaseOf] con la competición de
 * cualquiera de las semanas del bloque. Las series previstas suman las series de todos los ejercicios de cada sesión; el RIR previsto es
 * la media del RIR objetivo de esas series (o de 10 − RPE).
 */
internal fun progressWeeksOf(program: Program): List<ProgressScopeWeek> {
    val roadmap = ProgramDetailHelpers.buildRoadmapBlocks(program)
    val kinds = program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }
        .associate { it.id to it.executionKind }
    val weeks = mutableListOf<ProgressScopeWeek>()
    for (block in roadmap) {
        val blockWeeks = ProgramDetailHelpers.getWeeksForBlock(block.id, roadmap, program)
        val phase = programPhaseOf(
            goal = block.goal,
            hasCompetitionWeek = blockWeeks.any { it.keyDateType == KeyDateType.COMPETITION },
        )
        // Sin fase (una rutina, un bloque sin objetivo), lo hecho va en el acento de «Hechas»: en gris parecería lo previsto.
        val color = if (phase == null) SymbolPalette.musculo else programPhaseColor(phase)
        for (week in blockWeeks) {
            val sets = week.sessions.flatMap { session -> session.allExercises().flatMap { it.sets } }
            val rirs = sets.mapNotNull { set -> set.targetRIR?.toDouble() ?: set.targetRPE?.let { 10.0 - it } }
            val required = week.sessions.filter { it.requirement == SessionRequirement.REQUIRED }
            weeks += ProgressScopeWeek(
                id = week.id,
                blockId = block.id,
                position = weeks.size,
                requiredSessionIds = required.map { it.id },
                sessionDays = required.associate { it.id to it.dayOfWeek },
                plannedSets = sets.size,
                plannedRir = rirs.takeIf { it.isNotEmpty() }?.let { Math.round(it.average() * 10.0) / 10.0 },
                isDeload = kinds[week.id] == WeekExecutionKind.DELOAD || block.goal == BlockGoal.DELOAD,
                color = color,
            )
        }
    }
    return weeks
}

/** Eyebrow del resumen: con una sola semana empezada, su número («Semana 1»); si no, cuántas («En 4 semanas»). */
private fun periodLabelOf(started: List<ProgressScopeWeek>): String = when (started.size) {
    0 -> "Aún sin empezar"
    1 -> "Semana ${started.single().position + 1}"
    else -> "En ${started.size} semanas"
}

/** Pie del tonelaje sin comparación: el periodo («esta semana», «en 4 semanas»). */
private fun periodFooterOf(startedWeeks: Int): String = if (startedWeeks <= 1) "esta semana" else "en $startedWeeks semanas"

/**
 * Frase de resumen: cuántos básicos suben y, si los hay, el primero estancado y el primero que baja. [trends] llega ordenada por
 * sesiones; la cuenta incluye todos los ejercicios, no solo la lista que se enseña.
 */
private fun headlineOf(hasLogs: Boolean, trends: List<LiftTrend>): String {
    if (!hasLogs) return "Aún no hay entrenos registrados en este periodo."
    val withTrend = trends.filter { it.status != LiftTrendStatus.NO_TREND }
    if (withTrend.isEmpty()) return "Aún no hay tendencia. Con 2 sesiones de cada ejercicio empezamos a comparar."
    val up = withTrend.count { it.status == LiftTrendStatus.UP }
    val summary = "Subes en $up de ${withTrend.size} básicos."
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
private fun LiftTrend.toRow(sessions: List<LiftSession>): ProgressLiftRow = ProgressLiftRow(
    key = exerciseKey,
    name = exerciseName,
    status = status,
    currentKg = currentE1rm,
    deltaKg = deltaKg,
    points = points,
    caption = liftCaption(this, sessions),
)

/**
 * Pie de una fila: sesiones si sube; semanas sin subir si está estancado; semanas bajando si baja; sin tendencia, «1 sesión» o
 * «N sesiones: aún sin tendencia».
 */
private fun liftCaption(trend: LiftTrend, sessions: List<LiftSession>): String = when (trend.status) {
    LiftTrendStatus.UP -> "${trend.sessions} sesiones"
    LiftTrendStatus.DOWN -> {
        val weeks = downWeeksOf(sessions)
        when (weeks) {
            null -> "${trend.sessions} sesiones"
            1 -> "Baja 1 semana"
            else -> "Baja $weeks semanas"
        }
    }
    LiftTrendStatus.STAGNANT -> "${stalledWeeksOf(trend)} sem. sin subir"
    LiftTrendStatus.NO_TREND ->
        if (trend.sessions == 1) "1 sesión" else "${trend.sessions} sesiones: aún sin tendencia"
}

/**
 * Semanas que lleva bajando: desde la mejor sesión de la ventana anterior (las 3 sesiones antes de las 3 últimas) hasta la última
 * sesión, como mínimo 1. Null si no hay sesiones suficientes.
 */
private fun downWeeksOf(sessions: List<LiftSession>): Int? {
    if (sessions.size < LiftTrendEngine.MIN_SESSIONS) return null
    val peak = sessions.dropLast(3).takeLast(3).maxByOrNull { it.e1rm } ?: return null
    val days = java.time.temporal.ChronoUnit.DAYS.between(peak.date, sessions.last().date)
    return (days / 7).toInt().coerceAtLeast(1)
}

/** Semanas que lleva parado un ejercicio estancado; si el motor no las da, las [DEFAULT_STALLED_WEEKS] por defecto. */
internal fun stalledWeeksOf(trend: LiftTrend): Int = trend.stalledWeeks ?: DEFAULT_STALLED_WEEKS

/**
 * Nota de la semana con déficit: «Semana 3: 14 series menos de lo planificado (faltó la sesión del sábado).» El paréntesis dice las
 * sesiones obligatorias sin registro: una con su día, una sin día o varias.
 */
private fun shortfallNote(week: ProgressScopeWeek, done: Int, logged: Set<Pair<String?, String>>): String {
    val missing = week.requiredSessionIds.filter { (week.id to it) !in logged }
    val reason = when {
        missing.isEmpty() -> ""
        missing.size == 1 -> week.sessionDays[missing.single()]?.let { " (faltó la sesión del ${weekdayName(it)})" }
            ?: " (faltó una sesión)"
        else -> " (faltaron ${missing.size} sesiones)"
    }
    return "Semana ${week.position + 1}: ${week.plannedSets - done} series menos de lo planificado$reason."
}

/** Día de la semana en minúscula (1 = lunes). */
private fun weekdayName(day: Int): String = when (day) {
    1 -> "lunes"
    2 -> "martes"
    3 -> "miércoles"
    4 -> "jueves"
    5 -> "viernes"
    6 -> "sábado"
    7 -> "domingo"
    else -> "día"
}

/**
 * Comparación del tonelaje por sesión con el periodo anterior: en el programa, el bloque actual frente al primero; en un bloque, frente al
 * bloque anterior; en una semana, frente a la anterior. «+9 % vs bloque 1». Null si falta alguno de los dos lados.
 */
private fun tonnageComparison(
    programWeeks: List<ProgressScopeWeek>,
    programLogs: List<WorkoutLog>,
    scope: ProgressScope,
    selectedBlockId: String?,
    selectedWeekId: String?,
    currentWeekId: String?,
): String? {
    if (programWeeks.isEmpty()) return null
    val blockOrder = programWeeks.map { it.blockId }.distinct()
    val (current, base, label) = when (scope) {
        ProgressScope.PROGRAM -> {
            val currentBlock = programWeeks.firstOrNull { it.id == currentWeekId }?.blockId ?: return null
            val first = blockOrder.first()
            if (currentBlock == first) return null
            Triple(programWeeks.filter { it.blockId == currentBlock }, programWeeks.filter { it.blockId == first }, "bloque 1")
        }
        ProgressScope.BLOCK -> {
            val index = blockOrder.indexOf(selectedBlockId)
            if (index <= 0) return null
            Triple(
                programWeeks.filter { it.blockId == selectedBlockId },
                programWeeks.filter { it.blockId == blockOrder[index - 1] },
                "bloque $index",
            )
        }
        ProgressScope.WEEK -> {
            val index = programWeeks.indexOfFirst { it.id == selectedWeekId }
            if (index <= 0) return null
            Triple(listOf(programWeeks[index]), listOf(programWeeks[index - 1]), "semana $index")
        }
    }
    val now = perSessionTonnage(programLogs, current) ?: return null
    val before = perSessionTonnage(programLogs, base)?.takeIf { it > 0.0 } ?: return null
    val pct = ((now / before - 1.0) * 100.0).roundToInt()
    val signed = when {
        pct > 0 -> "+$pct"
        pct < 0 -> "−${-pct}"
        else -> "±0"
    }
    return "$signed % vs $label"
}

/** Tonelaje medio por sesión registrada de [weeks]; null si no hay ninguna sesión. */
private fun perSessionTonnage(logs: List<WorkoutLog>, weeks: List<ProgressScopeWeek>): Double? {
    val ids = weeks.mapTo(HashSet()) { it.id }
    val work: List<WeekWork> = WeeklyWorkEngine.byWeek(logs.filter { it.weekId in ids }, weeks.map { it.id })
    val sessions = work.sumOf { it.sessions }
    if (sessions == 0) return null
    return work.sumOf { it.tonnageKg } / sessions
}

/** El texto con la primera letra en minúscula, para nombrar un ejercicio a media frase. */
internal fun lowerFirst(text: String): String = text.replaceFirstChar { it.lowercase() }

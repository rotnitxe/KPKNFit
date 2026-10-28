package com.example.kpkn.screens.programdetail.design

import com.example.kpkn.data.models.Session
import com.example.kpkn.domain.training.SessionDurationEstimator
import java.time.LocalDate

/*
 * Modelo de la cabecera de la semana (pestaña Semana de Program Detail): las tres cifras del navegador, el rango de fechas, los títulos y el
 * subtítulo de la hoja «Semanas». Sin Compose: la prueba decide las cuentas y los textos.
 */

/** Las cifras de una semana: sesiones y series hechas de las totales, y el tiempo estimado de todas sus sesiones. */
internal data class ProgramWeekStats(
    val sessionsDone: Int,
    val sessionsTotal: Int,
    val setsDone: Int,
    val setsTotal: Int,
    val minutes: Int,
) {
    /** «2/5». */
    val sessionsLabel: String get() = "$sessionsDone/$sessionsTotal"

    /** «26/60». */
    val setsLabel: String get() = "$setsDone/$setsTotal"

    /** «≈ 4 h 36» (s-week): sin «min» tras las horas. */
    val minutesLabel: String get() = weekTotalLabel(minutes)
}

/**
 * Las cifras de la semana. Sesiones y series hechas salen de [completedSessionIds]; las totales, de todas las sesiones, y los minutos son la
 * suma del estimador común para todas ellas. Una serie es cada serie de trabajo de cada ejercicio de la sesión.
 */
internal fun programWeekStats(sessions: List<Session>, completedSessionIds: Set<String>): ProgramWeekStats {
    val done = sessions.filter { it.id in completedSessionIds }
    return ProgramWeekStats(
        sessionsDone = done.size,
        sessionsTotal = sessions.size,
        setsDone = done.sumOf { seriesOf(it) },
        setsTotal = sessions.sumOf { seriesOf(it) },
        minutes = sessions.sumOf { SessionDurationEstimator.estimate(it).totalMinutes },
    )
}

/** El tiempo de una semana en el navegador: «≈ 45 min», «≈ 4 h 36» (minutos con dos cifras: «≈ 4 h 05») o «≈ 4 h». */
internal fun weekTotalLabel(minutes: Int): String {
    if (minutes < 60) return "≈ $minutes min"
    val rest = minutes % 60
    return if (rest == 0) "≈ ${minutes / 60} h" else "≈ ${minutes / 60} h ${rest.toString().padStart(2, '0')}"
}

/** Cuántas semanas enseña la mini-cinta de una rutina (s-simple). */
internal const val ROUTINE_RIBBON_WEEKS = 12

/**
 * La mini-cinta de una rutina (s-simple): las 12 semanas del tramo en el que cae [cycle] (1–12, 13–24…), las anteriores a [cycle] hechas y
 * [cycle] elegida. Las semanas especiales de sus ciclos ([loops]: una cada `repeatEveryXLoops`) van en el color de su fase (la descarga en
 * verde, el test de 1RM en el de realización, la competición en el suyo); el resto, en tinta. Sin texto.
 */
internal fun routineMiniRibbon(cycle: Int?, loops: List<com.example.kpkn.data.models.Loop>): List<ProgramMiniRibWeek> {
    val current = cycle?.takeIf { it > 0 } ?: 1
    val first = (current - 1) / ROUTINE_RIBBON_WEEKS * ROUTINE_RIBBON_WEEKS + 1
    return (first until first + ROUTINE_RIBBON_WEEKS).map { week ->
        val special = loops.firstOrNull { loop ->
            loop.type != com.example.kpkn.data.models.LoopType.CUSTOM && loop.repeatEveryXLoops > 1 && week % loop.repeatEveryXLoops == 0
        }
        val color = when (special?.type) {
            com.example.kpkn.data.models.LoopType.DELOAD -> ProgramPhase.DELOAD.color
            com.example.kpkn.data.models.LoopType.ONE_RM_TEST -> ProgramPhase.REALIZATION.color
            com.example.kpkn.data.models.LoopType.COMPETITION -> ProgramPhase.COMPETITION.color
            else -> com.example.kpkn.screens.onboarding.design.WizardColors.text
        }
        ProgramMiniRibWeek(color = color, done = week < current, selected = week == current)
    }
}

/** Las series de trabajo de una sesión: las de todos sus ejercicios. */
private fun seriesOf(session: Session): Int = session.allExercises().sumOf { it.sets.size }

/** Los meses abreviados como salen en el rango de una semana. */
private val MonthAbbreviationsEs = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

/**
 * El rango de fechas de una semana: «3 – 9 nov» si cae en un mes, «29 oct – 2 nov» si cruza dos y «5 nov» si es un solo día. Null si la
 * semana no tiene fechas.
 */
internal fun weekDateRange(dates: Collection<LocalDate>): String? {
    val first = dates.minOrNull() ?: return null
    val last = dates.maxOrNull() ?: return null
    fun abbreviation(date: LocalDate): String = MonthAbbreviationsEs[date.monthValue - 1]
    return when {
        first == last -> "${first.dayOfMonth} ${abbreviation(first)}"
        first.year == last.year && first.monthValue == last.monthValue ->
            "${first.dayOfMonth} – ${last.dayOfMonth} ${abbreviation(last)}"
        else -> "${first.dayOfMonth} ${abbreviation(first)} – ${last.dayOfMonth} ${abbreviation(last)}"
    }
}

/**
 * El título del navegador: «Semana 4» según su posición en la lista. En una rutina (un programa simple de una sola semana que se repite),
 * el número del ciclo, y «Semana 1» si todavía no hay ciclo.
 */
internal fun weekNavigatorTitle(isRoutine: Boolean, weekIndex: Int, cycleNumber: Int?): String =
    if (isRoutine) {
        "Semana ${cycleNumber?.takeIf { it > 0 } ?: 1}"
    } else {
        "Semana ${weekIndex + 1}"
    }

/**
 * La línea bajo el título del navegador: la fase y el rango de fechas, unidos con « · ». Null si no hay ninguno de los dos.
 */
internal fun weekNavigatorSubtitle(phaseLabel: String?, dateRange: String?): String? =
    listOfNotNull(phaseLabel, dateRange)
        .filter { it.isNotBlank() }
        .joinToString(separator = " · ")
        .takeIf { it.isNotEmpty() }

/**
 * El subtítulo de la hoja «Semanas»: «12 semanas en 3 bloques» (en un programa simple, sin bloques) y la instrucción de mantener pulsada una
 * semana para copiarla, pegarla o editarla.
 */
internal fun weekSheetSubtitle(weekCount: Int, blockCount: Int?): String {
    val blocks = blockCount?.let { " en ${countWithNoun(it, "bloque", "bloques")}" }.orEmpty()
    return "${countWithNoun(weekCount, "semana", "semanas")}$blocks. Mantén pulsada una semana para copiarla, pegarla o editarla."
}

/** La cifra con su palabra: en singular solo con 1 («1 semana», «2 semanas»). */
private fun countWithNoun(count: Int, singular: String, plural: String): String =
    "$count ${if (count == 1) singular else plural}"

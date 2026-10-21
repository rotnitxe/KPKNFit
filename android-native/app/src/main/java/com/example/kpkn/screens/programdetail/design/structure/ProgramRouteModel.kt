package com.example.kpkn.screens.programdetail.design.structure

import androidx.compose.ui.graphics.Color
import com.example.kpkn.domain.text.SpanishPlurals
import com.example.kpkn.domain.training.ProgramKeyDateEngine
import com.example.kpkn.screens.programdetail.design.numberWithUnit
import com.example.kpkn.screens.programdetail.formatDate
import com.example.kpkn.screens.programdetail.formatDayMonth
import com.example.kpkn.screens.programdetail.formatShortDate
import java.time.LocalDate

/*
 * Modelo de la ruta del programa (P3c): la línea de tiempo y el ajuste a la competición, ya calculados. La hoja no conoce el motor: solo
 * recibe estas filas. Sin Compose, para fijar los textos con número con pruebas de JVM.
 */

/** Una semana del programa. [index] empieza en 0 y es la posición en la línea de tiempo. */
internal data class ProgramRouteWeek(
    val index: Int,
    val blockId: String,
    val weekId: String,
    val start: LocalDate,
    val end: LocalDate,
    val isCompetition: Boolean,
)

/**
 * Un bloque como tramo de la línea de tiempo: su primera semana, cuántas tiene y su color de fase (gris si la fase no se sabe). [start] es
 * el primer día del bloque. [phaseLabel] es la fase («Acumulación») si se sabe: la fila la muestra como título y el nombre va en la línea
 * de inicio; sin fase, el título es el nombre.
 */
internal data class ProgramRouteBlock(
    val id: String,
    val name: String,
    val firstWeekIndex: Int,
    val weeks: Int,
    val start: LocalDate,
    val firstWeekId: String,
    val color: Color,
    val phaseLabel: String? = null,
)

/** Una fecha clave que no es la competición. [weekIndex] es la semana que la contiene, si cae dentro del programa. */
internal data class ProgramRouteKeyDate(
    val title: String,
    val typeLabel: String,
    val date: LocalDate,
    val weekIndex: Int?,
)

/** Una bandera de la línea de tiempo: una fecha clave o la competición, en la semana que la contiene. */
internal data class ProgramRouteFlag(
    val weekIndex: Int,
    val label: String,
    val competition: Boolean,
)

/**
 * Toda la ruta. Sin semanas (no hay fecha de inicio) la hoja muestra su estado vacío. [fitStatus] es null si no hay competición que
 * encajar; [fitWeeks] son las semanas que faltan o sobran.
 */
internal data class ProgramRouteModel(
    val today: LocalDate,
    val weeks: List<ProgramRouteWeek>,
    val blocks: List<ProgramRouteBlock>,
    val keyDates: List<ProgramRouteKeyDate>,
    val competitionDate: LocalDate?,
    val competitionWeekIndex: Int?,
    val fitStatus: ProgramKeyDateEngine.KeyDateFitStatus?,
    val fitWeeks: Int,
)

/** Espacio sin corte: mantiene juntas las partes de una fecha («28 dic 2026») cuando una línea se parte. */
private const val NON_BREAKING_SPACE = ' '

private fun keepTogether(text: String): String = text.replace(' ', NON_BREAKING_SPACE)

/** «mar 6 oct», sin año: el día de la semana y la fecha corta en español (el formato de la app). */
internal fun dayMonthWeekdayLabel(date: LocalDate): String = formatShortDate(date).removeSuffix(" ${date.year}")

/** «6 oct → 25 ene 2027» con las dos fechas de la ruta; cada fecha no se parte por dentro. */
internal fun routeRangeLine(start: LocalDate, end: LocalDate): String =
    "${keepTogether(formatDayMonth(start))} → ${keepTogether(formatDate(end))}"

/** «4 bloques · 12 semanas · 6 oct → 25 ene 2027». Sin semanas, no hay línea. */
internal fun routeSummaryLine(blockCount: Int, weekCount: Int, start: LocalDate, end: LocalDate): String =
    listOf(
        numberWithUnit(SpanishPlurals.blocks(blockCount)),
        numberWithUnit(SpanishPlurals.weeks(weekCount)),
        routeRangeLine(start, end),
    ).joinToString(" · ")

/** La descripción de la línea de tiempo para TalkBack: bloques, semanas y en qué semana cae la competición. */
internal fun routeTimelineSummary(model: ProgramRouteModel): String {
    val competition = model.competitionWeekIndex?.let { index -> ", competición en la semana ${index + 1}" }.orEmpty()
    return "Línea de tiempo: ${numberWithUnit(SpanishPlurals.blocks(model.blocks.size))}, " +
        "${numberWithUnit(SpanishPlurals.weeks(model.weeks.size))}$competition"
}

/**
 * La línea de inicio de un bloque: «Bloque 2 · empieza el mar 3 nov» cuando el título es la fase; sin fase, el título ya es el nombre y
 * la línea dice solo «empieza el mar 3 nov».
 */
internal fun blockStartLine(block: ProgramRouteBlock): String {
    val start = "empieza el ${keepTogether(dayMonthWeekdayLabel(block.start))}"
    return if (block.phaseLabel != null) "${block.name} · $start" else start
}

/** Las banderas del programa de izquierda a derecha: las fechas clave que caen dentro del programa y la competición. */
internal fun programRouteFlags(model: ProgramRouteModel): List<ProgramRouteFlag> {
    val keyFlags = model.keyDates.mapNotNull { key ->
        key.weekIndex?.let { index -> ProgramRouteFlag(weekIndex = index, label = key.typeLabel, competition = false) }
    }
    val competition = model.competitionWeekIndex?.let { index ->
        ProgramRouteFlag(weekIndex = index, label = "Competición", competition = true)
    }
    return (keyFlags + listOfNotNull(competition)).sortedBy { it.weekIndex }
}

/**
 * La frase del ajuste a la competición, o null si no hay nada que decir. «Tu competición cae 2 semanas después del final del programa»
 * cuando faltan semanas; «antes» cuando sobran; y la confirmación cuando cae justo en la última semana.
 */
internal fun competitionFitLine(status: ProgramKeyDateEngine.KeyDateFitStatus?, weeks: Int): String? = when (status) {
    ProgramKeyDateEngine.KeyDateFitStatus.FITS ->
        "Tu competición cae en la última semana del programa."
    ProgramKeyDateEngine.KeyDateFitStatus.NEEDS_MORE_WEEKS ->
        "Tu competición cae ${numberWithUnit(SpanishPlurals.weeks(weeks))} después del final del programa."
    ProgramKeyDateEngine.KeyDateFitStatus.HAS_SURPLUS_WEEKS ->
        "Tu competición cae ${numberWithUnit(SpanishPlurals.weeks(weeks))} antes del final del programa."
    ProgramKeyDateEngine.KeyDateFitStatus.BEFORE_PROGRAM_START ->
        "Tu competición cae antes del inicio del programa. Ajusta la fecha de inicio o la de competición."
    else -> null
}

/**
 * La acción que cuadra el programa con la competición: «Añadir 2 semanas» o «Quitar 2 semanas». Es corta a propósito, para que quepa en
 * una píldora; dónde se aplica lo dice [competitionFixNote]. Null si no hay nada que cuadrar.
 */
internal fun competitionFixLabel(status: ProgramKeyDateEngine.KeyDateFitStatus?, weeks: Int): String? {
    if (weeks <= 0) return null
    return when (status) {
        ProgramKeyDateEngine.KeyDateFitStatus.NEEDS_MORE_WEEKS -> "Añadir ${numberWithUnit(SpanishPlurals.weeks(weeks))}"
        ProgramKeyDateEngine.KeyDateFitStatus.HAS_SURPLUS_WEEKS -> "Quitar ${numberWithUnit(SpanishPlurals.weeks(weeks))}"
        else -> null
    }
}

/** Dónde se aplica la acción de [competitionFixLabel]: siempre en el último bloque, y así se dice junto a ella. */
internal fun competitionFixNote(status: ProgramKeyDateEngine.KeyDateFitStatus?, weeks: Int): String? {
    if (weeks <= 0) return null
    return when (status) {
        ProgramKeyDateEngine.KeyDateFitStatus.NEEDS_MORE_WEEKS -> "Se suman al último bloque."
        ProgramKeyDateEngine.KeyDateFitStatus.HAS_SURPLUS_WEEKS -> "Se quitan del último bloque."
        else -> null
    }
}

/** Cuántas semanas mete (+) o saca (−) la acción de [competitionFixLabel] en el último bloque. Cero si no hay acción. */
internal fun competitionFixDelta(status: ProgramKeyDateEngine.KeyDateFitStatus?, weeks: Int): Int = when (status) {
    ProgramKeyDateEngine.KeyDateFitStatus.NEEDS_MORE_WEEKS -> weeks
    ProgramKeyDateEngine.KeyDateFitStatus.HAS_SURPLUS_WEEKS -> -weeks
    else -> 0
}

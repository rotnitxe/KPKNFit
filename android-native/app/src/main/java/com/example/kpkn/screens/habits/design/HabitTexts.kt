package com.example.kpkn.screens.habits.design

import com.example.kpkn.domain.habits.HabitCalendarCell
import com.example.kpkn.domain.habits.HabitKind
import com.example.kpkn.domain.habits.HabitStreaks
import com.example.kpkn.domain.habits.HabitUnit
import java.time.LocalDate

private val MESES = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
)
private val MESES_CORTOS = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

/** Fecha en español para las descripciones accesibles, por ejemplo «21 de octubre de 2026». */
internal fun habitDateLabel(date: LocalDate): String = "${date.dayOfMonth} de ${MESES[date.monthValue - 1]} de ${date.year}"

/** Abreviatura del mes para la fila de meses del calendario. */
internal fun habitMonthShort(date: LocalDate): String = MESES_CORTOS[date.monthValue - 1]

/** Palabra de la unidad según la cifra: «día» o «días», «semana» o «semanas». */
internal fun habitUnitWord(unit: HabitUnit, count: Int): String = when (unit) {
    HabitUnit.DAYS -> if (count == 1) "día" else "días"
    HabitUnit.WEEKS -> if (count == 1) "semana" else "semanas"
}

/** Lo que dice la racha sobre hoy: hoy no cuenta hasta que termine el día (o la semana). */
internal fun habitStreakHint(kind: HabitKind): String = when (kind) {
    HabitKind.TRAINING -> "La semana en curso sin sesión no rompe la racha."
    HabitKind.MEALS, HabitKind.WEIGHING -> "Hoy sin registro no rompe la racha: se cuenta hasta ayer."
}

/** Pie del calendario: cuántos días del último año tienen registro. */
internal fun habitRecordedCaption(kind: HabitKind, count: Int): String {
    val noun = if (count == 1) "día" else "días"
    val what = when (kind) {
        HabitKind.MEALS -> "con registro"
        HabitKind.WEIGHING -> "con pesaje"
        HabitKind.TRAINING -> "con entreno"
    }
    return "$count $noun $what en el último año."
}

internal fun habitEmptyTitle(kind: HabitKind): String = when (kind) {
    HabitKind.MEALS -> "Aún no hay días con comidas"
    HabitKind.WEIGHING -> "Aún no hay pesajes"
    HabitKind.TRAINING -> "Aún no hay entrenos completados"
}

internal fun habitEmptyBody(kind: HabitKind): String = when (kind) {
    HabitKind.MEALS -> "Registra una comida o marca un día completo o de ayuno para empezar tu racha."
    HabitKind.WEIGHING -> "Cada día en que registres tu peso aparecerá aquí."
    HabitKind.TRAINING -> "Cada sesión terminada con series de trabajo aparecerá aquí."
}

/** Rango de series de un nivel de entreno, por ejemplo «de 8 a 14» o «de 15 o más». */
internal fun trainingRangeLabel(level: Int): String = when (level) {
    1 -> "de 1 a ${HabitStreaks.TRAINING_LEVEL_2_FROM - 1}"
    2 -> "de ${HabitStreaks.TRAINING_LEVEL_2_FROM} a ${HabitStreaks.TRAINING_LEVEL_3_FROM - 1}"
    else -> "de ${HabitStreaks.TRAINING_LEVEL_3_FROM} o más"
}

/** Descripción accesible de una celda: fecha, si es hoy, y el registro o la carga de ese día. */
internal fun habitDayDescription(kind: HabitKind, cell: HabitCalendarCell): String {
    val date = habitDateLabel(cell.date) + if (cell.isToday) ", hoy" else ""
    return when {
        cell.isFuture -> "$date: todavía no ha llegado"
        kind == HabitKind.TRAINING ->
            if (cell.level <= 0) "$date: sin entreno" else "$date: entreno ${trainingRangeLabel(cell.level)} series"
        cell.level > 0 -> if (kind == HabitKind.WEIGHING) "$date: con pesaje" else "$date: con registro"
        else -> "$date: sin registro"
    }
}

/** Leyenda del calendario: pares de nivel y texto, en el orden de menos a más. */
internal fun habitLegend(kind: HabitKind): List<Pair<Int, String>> = when (kind) {
    HabitKind.TRAINING -> listOf(
        0 to "Sin entreno",
        1 to "1 a ${HabitStreaks.TRAINING_LEVEL_2_FROM - 1} series",
        2 to "${HabitStreaks.TRAINING_LEVEL_2_FROM} a ${HabitStreaks.TRAINING_LEVEL_3_FROM - 1} series",
        3 to "${HabitStreaks.TRAINING_LEVEL_3_FROM} o más series",
    )
    HabitKind.MEALS -> listOf(0 to "Sin registro", 1 to "Con registro")
    HabitKind.WEIGHING -> listOf(0 to "Sin registro", 1 to "Con pesaje")
}

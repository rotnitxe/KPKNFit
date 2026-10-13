package com.example.kpkn.screens.programdetail

import com.example.kpkn.data.models.KeyDateType
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Abreviatura del día (1 = lunes … 7 = domingo) para chips y listas compactas. */
internal fun dayLabelShort(dayOfWeek: Int): String = when (dayOfWeek) {
    1 -> "Lun"
    2 -> "Mar"
    3 -> "Mié"
    4 -> "Jue"
    5 -> "Vie"
    6 -> "Sáb"
    7 -> "Dom"
    else -> "Día"
}

/**
 * Fecha ISO (AAAA-MM-DD) recortando los espacios antes de leerla. Null si está vacía o no es una fecha válida.
 * La usan la pantalla y el ViewModel del programa.
 */
internal fun parseIsoDate(raw: String?): LocalDate? {
    val texto = raw?.trim() ?: return null
    if (texto.isEmpty()) return null
    return try {
        LocalDate.parse(texto)
    } catch (_: DateTimeParseException) {
        null
    }
}

/**
 * Como [parseIsoDate], pero sin recortar: «2026-10-13 » no es válida. Es lo que aceptan hoy la hoja de fechas clave
 * y el volumen; se conserva así para no cambiar qué fechas se aceptan.
 */
internal fun parseIsoDateExact(raw: String?): LocalDate? {
    if (raw.isNullOrBlank()) return null
    return try {
        LocalDate.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE)
    } catch (_: DateTimeParseException) {
        null
    }
}

/** Título de una semana del calendario, por ejemplo «Semana: 10/13». */
internal fun calendarWeekTitle(startDate: LocalDate): String =
    "Semana: ${startDate.format(DateTimeFormatter.ofPattern("MM/dd", Locale.US))}"

// ── Calendario del programa (P3a): textos fijos en español, sin depender del locale del sistema ──

/** Día abreviado de lunes (1) a domingo (7): minúscula y sin punto. */
private val DIAS_CORTOS = listOf("lun", "mar", "mié", "jue", "vie", "sáb", "dom")

/** Mes abreviado: minúscula y sin punto. */
private val MESES_CORTOS = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sept", "oct", "nov", "dic")

/** Letra de cada día ISO (1 = lunes): L M X J V S D. */
private val LETRAS_DIA = listOf("L", "M", "X", "J", "V", "S", "D")

/** «mar 6 oct 2026»: la fecha corta de la vista de Calendario del programa. */
internal fun formatShortDate(date: LocalDate): String =
    "${DIAS_CORTOS[date.dayOfWeek.value - 1]} ${date.dayOfMonth} ${MESES_CORTOS[date.monthValue - 1]} ${date.year}"

/** «2 nov 2026»: la fecha sin día de la semana, para las estimaciones y los textos cortos. */
internal fun formatDate(date: LocalDate): String =
    "${date.dayOfMonth} ${MESES_CORTOS[date.monthValue - 1]} ${date.year}"

/** «6 oct»: un día suelto, sin año. */
internal fun formatDayMonth(date: LocalDate): String = "${date.dayOfMonth} ${MESES_CORTOS[date.monthValue - 1]}"

/** «6 oct – 2 nov»: rango sin año. Si los dos extremos caen en años distintos, el año va en ambos. */
internal fun formatDateRange(start: LocalDate, end: LocalDate): String {
    val conAnio = start.year != end.year
    return listOf(start, end).joinToString(" – ") { fecha ->
        if (conAnio) formatDate(fecha) else formatDayMonth(fecha)
    }
}

/**
 * Cuenta atrás hasta la competición: «Faltan 41 días», «Mañana», «Hoy», «Fue ayer» o «Fue hace 3 días». Recibe los días ya contados
 * desde hoy (negativos si la fecha ya pasó).
 */
internal fun formatDaysUntil(days: Long): String = when {
    days == 0L -> "Hoy"
    days == 1L -> "Mañana"
    days > 1L -> "Faltan $days días"
    days == -1L -> "Fue ayer"
    else -> "Fue hace ${-days} días"
}

/** Tipo de una fecha clave en español. «Otra» es el tipo libre. */
internal fun keyDateTypeLabel(type: KeyDateType): String = when (type) {
    KeyDateType.COMPETITION -> "Competición"
    KeyDateType.EXAMS -> "Exámenes"
    KeyDateType.VACATION -> "Vacaciones"
    KeyDateType.TRAVEL -> "Viaje"
    KeyDateType.CUSTOM -> "Otra"
}

/** Letra de un día ISO (1 = lunes). Fuera de rango, un guion: nunca un día inventado. */
internal fun weekdayInitial(day: Int): String = LETRAS_DIA.getOrElse(day - 1) { "—" }

/** Los siete días ISO empezando por [startDay]; si está fuera de 1..7, empieza en lunes. */
internal fun weekdaysFrom(startDay: Int): List<Int> {
    val first = if (startDay in 1..7) startDay else 1
    return (0 until 7).map { (first - 1 + it) % 7 + 1 }
}

/** Semanas del calendario que cubren de [start] a [end], ambas incluidas. Como mínimo, una. */
internal fun calendarWeekCount(start: LocalDate, end: LocalDate): Int {
    val inclusiveDays = ChronoUnit.DAYS.between(start, end).coerceAtLeast(0) + 1
    return ((inclusiveDays + 6) / 7).toInt().coerceAtLeast(1)
}

/** Todas las semanas del programa, en el orden de sus bloques y mesociclos. */
private fun Program.weeksInOrder(): List<ProgramWeek> =
    macrocycles.flatMap { macro -> macro.blocks.flatMap { block -> block.mesocycles.flatMap { it.weeks } } }

/**
 * Días ISO (1 = lunes … 7 = domingo) con sesión en la semana [weekId]. Sin semana, o si esa semana no tiene sesiones con día, el
 * conjunto va vacío: no se pinta ningún día que el programa no tenga.
 */
internal fun programTrainingWeekdays(program: Program, weekId: String?): Set<Int> {
    val week = program.weeksInOrder().firstOrNull { it.id == weekId } ?: return emptySet()
    return week.sessions.mapNotNull { it.dayOfWeek }.filter { it in 1..7 }.toSet()
}

/** La semana en curso: la del estado de ejecución del programa o, si no hay, la primera. Null si el programa no tiene semanas. */
internal fun programCurrentWeekId(program: Program): String? =
    program.runState?.weekId ?: program.weeksInOrder().firstOrNull()?.id

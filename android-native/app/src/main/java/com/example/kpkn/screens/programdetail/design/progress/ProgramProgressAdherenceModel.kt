package com.example.kpkn.screens.programdetail.design.progress

import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.models.discomfortLabel
import com.example.kpkn.domain.training.DecisionLine
import com.example.kpkn.domain.training.progress.WeekWork
import com.example.kpkn.screens.onboarding.design.formatDecimalEs
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/*
 * Modelo de la escena g-adh de Progreso: «Cumplimiento», «Esfuerzo registrado», «Molestias» y «Decisiones del sistema». Sin Compose:
 * la prueba JVM decide cada cuadro y cada frase.
 */

/** Diferencia de RIR (pedido menos registrado) desde la que una semana se marca como «más cerca del fallo de lo pedido». */
private const val EFFORT_GAP = 0.5

/** Ejercicios más omitidos que se nombran. */
private const val MAX_OMITTED = 2

/** Molestias que se listan. */
private const val MAX_DISCOMFORTS = 4

/** Ejercicios que se nombran en «Coincide con …». */
private const val MAX_COINCIDENCES = 3

/** Un cuadro de la rejilla de cumplimiento: sesión hecha, que faltó o que aún no toca. */
internal enum class AdherenceCell { DONE, MISSED, FUTURE }

/** Una columna de la rejilla: la semana («S3») y un cuadro por sesión obligatoria, en el orden del plan. */
internal data class ProgressAdherenceWeek(val label: String, val cells: List<AdherenceCell>)

/** Un ejercicio omitido y las veces que se ha saltado. */
internal data class ProgressOmitted(val name: String, val count: Int)

/** «Cumplimiento»: el porcentaje, la rejilla por semana y los ejercicios más omitidos. */
internal data class ProgressAdherence(
    val pct: Int?,
    val weeks: List<ProgressAdherenceWeek>,
    val mostOmitted: List<ProgressOmitted>,
)

/** Una fila de «Esfuerzo registrado»: la semana, el RIR pedido (si el plan lo fija), el medio registrado y si fue más cerca del fallo. */
internal data class ProgressEffortRow(
    val label: String,
    val plannedRir: Double?,
    val rir: Double,
    val closerThanPlanned: Boolean,
)

/** «Esfuerzo registrado»: una fila por semana con RIR y la nota de la primera semana que se fue más cerca del fallo. */
internal data class ProgressEffort(val rows: List<ProgressEffortRow>, val note: String?)

/** Una molestia, las sesiones en que se registró y los ejercicios con los que coincide («Coincide con press militar (2) y fondos (1)»). */
internal data class ProgressDiscomfortRow(val name: String, val count: Int, val coincidence: String?)

/** «Molestias»: el periodo («4 semanas») y sus filas. */
internal data class ProgressDiscomforts(val window: String, val rows: List<ProgressDiscomfortRow>)

/** Una entrada de «Decisiones del sistema»: el título y la línea «Hoy · motivo». */
internal data class ProgressDecisionRow(val title: String, val line: String)

/**
 * La rejilla de cumplimiento de [scopeWeeks]. Una sesión obligatoria registrada es «hecha»; sin registro, «faltó» si su semana ya pasó
 * (antes de la actual) y «futura» si es la actual o una posterior: las semanas futuras no penalizan. Null si no hay ninguna sesión
 * obligatoria en el alcance.
 */
internal fun progressAdherenceOf(
    scopeWeeks: List<ProgressScopeWeek>,
    currentPosition: Int,
    currentWeekId: String?,
    loggedSessions: Set<Pair<String?, String>>,
    compliancePct: Int?,
    scopeLogs: List<WorkoutLog>,
): ProgressAdherence? {
    if (scopeWeeks.none { it.requiredSessionIds.isNotEmpty() }) return null
    val weeks = scopeWeeks.map { week ->
        val past = currentPosition < 0 || (week.position < currentPosition && week.id != currentWeekId)
        ProgressAdherenceWeek(
            label = "S${week.position + 1}",
            cells = week.requiredSessionIds.map { sessionId ->
                when {
                    (week.id to sessionId) in loggedSessions -> AdherenceCell.DONE
                    past -> AdherenceCell.MISSED
                    else -> AdherenceCell.FUTURE
                }
            },
        )
    }
    val omitted = scopeLogs.flatMap { it.omittedExercises }
        .groupingBy { it.exerciseName.ifBlank { it.exerciseId } }
        .eachCount()
        .map { (name, count) -> ProgressOmitted(name, count) }
        .sortedWith(compareByDescending<ProgressOmitted> { it.count }.thenBy { it.name })
        .take(MAX_OMITTED)
    return ProgressAdherence(pct = compliancePct, weeks = weeks, mostOmitted = omitted)
}

/**
 * «Esfuerzo registrado»: una fila por semana del alcance con RIR registrado. Una semana va marcada si su RIR medio queda al menos medio
 * punto por debajo del pedido. Null si ninguna semana tiene RIR.
 */
internal fun progressEffortOf(scopeWeeks: List<ProgressScopeWeek>, work: List<WeekWork>): ProgressEffort? {
    val byWeek = work.associateBy { it.weekId }
    val rows = scopeWeeks.mapNotNull { week ->
        val done = byWeek[week.id] ?: return@mapNotNull null
        val rir = done.rirMean ?: return@mapNotNull null
        val planned = done.plannedRirMean
        ProgressEffortRow(
            label = "Semana ${week.position + 1}",
            plannedRir = planned,
            rir = rir,
            closerThanPlanned = planned != null && planned - rir >= EFFORT_GAP,
        )
    }
    if (rows.isEmpty()) return null
    val flagged = rows.firstOrNull { it.closerThanPlanned }
    val note = flagged?.let {
        "En la ${it.label.lowercase()} entrenaste más cerca del fallo de lo pedido. Si se repite con cargas iguales, es señal de fatiga."
    }
    return ProgressEffort(rows = rows, note = note)
}

/**
 * «Molestias» de [scopeLogs]: cada molestia con las sesiones en que se registró, de la más repetida a la menos, y los ejercicios con
 * los que coincide según los partes de cada ejercicio. Null si no hay ninguna.
 */
internal fun progressDiscomfortsOf(scopeLogs: List<WorkoutLog>, startedWeeks: Int): ProgressDiscomforts? {
    val counts = LinkedHashMap<String, Int>()
    for (log in scopeLogs) {
        for (name in log.discomforts.filter { it.isNotBlank() }.distinct()) counts[name] = (counts[name] ?: 0) + 1
    }
    if (counts.isEmpty()) return null
    val rows = counts.entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(MAX_DISCOMFORTS)
        .map { (name, count) -> ProgressDiscomfortRow(name, count, coincidenceOf(name, scopeLogs)) }
    val window = if (startedWeeks <= 1) "1 semana" else "$startedWeeks semanas"
    return ProgressDiscomforts(window = window, rows = rows)
}

/** «Coincide con press militar (2) y fondos (1)»: los ejercicios cuyos partes registran [name], con las veces. Null si ninguno. */
private fun coincidenceOf(name: String, logs: List<WorkoutLog>): String? {
    val byExercise = LinkedHashMap<String, Int>()
    for (log in logs) {
        for (report in log.postExerciseReports) {
            val hit = report.discomfortIds.any { it != "none" && discomfortLabel(it) == name }
            if (hit && report.exerciseName.isNotBlank()) byExercise[report.exerciseName] = (byExercise[report.exerciseName] ?: 0) + 1
        }
    }
    if (byExercise.isEmpty()) return null
    val parts = byExercise.entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(MAX_COINCIDENCES)
        .map { (exercise, count) -> "${lowerFirst(exercise)} ($count)" }
    return "Coincide con ${spanishList(parts)}"
}

/** Las [max] decisiones más recientes, como entradas de la línea de tiempo. */
internal fun progressDecisionRows(
    decisions: List<DecisionLine>,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
    max: Int = 3,
): List<ProgressDecisionRow> = decisions.sortedByDescending { it.atMs }.take(max).map { line ->
    val date = progressDayLabel(Instant.ofEpochMilli(line.atMs).atZone(zone).toLocalDate(), today)
    ProgressDecisionRow(
        title = line.title,
        line = if (line.detail.isBlank()) date else "$date · ${line.detail.lines().first()}",
    )
}

/** Día corto del lienzo: «Hoy», «Lun 27» y, fuera del mes en curso, «Lun 27 sept». */
internal fun progressDayLabel(date: LocalDate, today: LocalDate): String {
    if (date == today) return "Hoy"
    val weekday = when (date.dayOfWeek.value) {
        1 -> "Lun"
        2 -> "Mar"
        3 -> "Mié"
        4 -> "Jue"
        5 -> "Vie"
        6 -> "Sáb"
        else -> "Dom"
    }
    val sameMonth = date.year == today.year && date.month == today.month
    return if (sameMonth) "$weekday ${date.dayOfMonth}" else "$weekday ${date.dayOfMonth} ${monthShort(date.monthValue)}"
}

/** Mes abreviado en español. */
private fun monthShort(month: Int): String =
    listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sept", "oct", "nov", "dic")[month - 1]

/** RIR con un decimal: «2,1», «1,0». */
internal fun formatRir(value: Double): String = formatDecimalEs(value, 1)

/** RIR pedido sin decimal sobrante: «2», «1,5». */
internal fun formatPlannedRir(value: Double): String = formatDecimalEs(value, 1).removeSuffix(",0")

/** «A», «A y B», «A, B y C». */
internal fun spanishList(items: List<String>): String = when (items.size) {
    0 -> ""
    1 -> items.single()
    else -> items.dropLast(1).joinToString(", ") + " y " + items.last()
}

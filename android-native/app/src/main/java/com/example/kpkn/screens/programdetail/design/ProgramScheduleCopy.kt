package com.example.kpkn.screens.programdetail.design

import com.example.kpkn.domain.training.ScheduleIssue
import java.time.LocalDate
import java.util.Locale

/** Título y mensaje del aviso de calendario que sale antes de empezar una sesión fuera de su fecha. */
internal data class ScheduleCopy(val title: String, val message: String)

private val WEEKDAYS_ES = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")
private val MONTHS_ES_SHORT = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
private const val KEEP_TRAINING_DEVIATION = "Puedes entrenar igual; KPKN lo registrará como desviación del plan."
private const val KEEP_TRAINING_DIFFERENCE = "Puedes entrenarla igual; KPKN guardará la diferencia de calendario."

/**
 * El aviso de calendario al empezar una sesión (función pura, sin Compose). Gana un problema del motor de calendario ([issue]); si no hay
 * ninguno, estar fuera del programa ([isOutsideProgram]); si tampoco, una fecha anclada distinta de [today] ([scheduledDate]). Null si no
 * hay nada que avisar: la sesión es de hoy y está dentro del plan.
 */
internal fun scheduleCopy(
    issue: ScheduleIssue?,
    isOutsideProgram: Boolean,
    scheduledDate: LocalDate?,
    today: LocalDate,
): ScheduleCopy? = when {
    issue is ScheduleIssue.WrongDate -> ScheduleCopy(
        title = "Esta sesión es del ${weekdayName(issue.plannedDate)}",
        message = "Hoy es ${weekdayName(issue.actualDate)} ${issue.actualDate.dayOfMonth}. $KEEP_TRAINING_DIFFERENCE",
    )
    issue is ScheduleIssue.OutsideProgram -> ScheduleCopy(
        title = "Hoy está fuera del programa",
        message = if (issue.startDate != null && issue.endDate != null) {
            "El programa va del ${shortDate(issue.startDate)} al ${shortDate(issue.endDate)}. $KEEP_TRAINING_DEVIATION"
        } else {
            KEEP_TRAINING_DEVIATION
        },
    )
    issue is ScheduleIssue.OutsideProgramDay -> ScheduleCopy(
        title = "Hoy no es día de entreno",
        message = "Tu plan con fechas no tiene entreno hoy. $KEEP_TRAINING_DEVIATION",
    )
    issue == null && isOutsideProgram -> ScheduleCopy(
        title = "Este día está fuera del programa",
        message = "Entrenar aquí se registrará como desviación del plan.",
    )
    issue == null && scheduledDate != null && scheduledDate != today -> ScheduleCopy(
        title = "Esta sesión es del ${weekdayName(scheduledDate)}",
        message = "Hoy es ${weekdayName(today)} ${today.dayOfMonth}. $KEEP_TRAINING_DIFFERENCE",
    )
    else -> null
}

/** «Fecha del viernes»: el título de la hoja de la fecha de un día, con su nombre en minúsculas. */
internal fun dayDateSheetTitle(dayName: String): String = "Fecha del ${dayName.lowercase(Locale.forLanguageTag("es"))}"

/** El nombre del día en minúsculas y en español («jueves»). */
private fun weekdayName(date: LocalDate): String = WEEKDAYS_ES[date.dayOfWeek.value - 1]

/** La fecha corta en español («12 nov»). */
private fun shortDate(date: LocalDate): String = "${date.dayOfMonth} ${MONTHS_ES_SHORT[date.monthValue - 1]}"

package com.example.kpkn.domain.nutrition.expenditure

import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.data.models.NutritionStatus
import java.time.Instant
import java.time.LocalDate

enum class DayMarkSource { USER, REVIEW_ACCEPTED, REVIEW_REJECTED }

/** Declaración durable; no cambia los alimentos ni sus totales. */
data class DayMarkEntry(
    val date: LocalDate,
    val mark: DayMark,
    val source: DayMarkSource,
    val updatedAt: Instant,
)

/** Comparte el día por prefijo ISO que usa el diario, sin reinterpretar su zona horaria. */
fun nutritionLogDay(log: NutritionLog): LocalDate? =
    runCatching { LocalDate.parse(log.date.take(10)) }.getOrNull()

fun validateDayMark(date: LocalDate, mark: DayMark, consumedLogs: List<NutritionLog>, today: LocalDate) {
    require(!date.isAfter(today)) { "No puedes marcar un día futuro." }
    require(mark != DayMark.NONE) { "Para dejar el día sin marcar, quita la marca." }
    val hasLogs = consumedLogs.any { it.status != NutritionStatus.PLANNED && nutritionLogDay(it) == date }
    when (mark) {
        DayMark.FASTING -> require(!hasLogs) { "Hay comidas registradas: no puedes marcar ayuno." }
        DayMark.PARTIAL, DayMark.COMPLETE_CONFIRMED -> require(hasLogs) { "Registra una comida antes de marcar este día." }
        DayMark.NONE -> Unit
    }
}

/**
 * Un total por día hasta [through], incluidos los huecos. Un vacío solo vale cero con ayuno declarado.
 * Si después se añaden comidas a un ayuno, o se vacía un día completo/parcial, la declaración se conserva
 * en almacenamiento, pero deja de ser válida para el estimador hasta que la persona la revise.
 */
fun buildDayObservations(
    logs: List<NutritionLog>,
    marks: Map<LocalDate, DayMarkEntry>,
    through: LocalDate,
): List<DayObservation> {
    val byDay = logs.asSequence().filter { it.status != NutritionStatus.PLANNED }
        .mapNotNull { log -> nutritionLogDay(log)?.takeUnless { it.isAfter(through) }?.let { it to log } }
        .groupBy({ it.first }, { it.second })
    val dates = byDay.keys + marks.keys.filterNot { it.isAfter(through) }
    val start = dates.minOrNull() ?: through
    return generateSequence(start) { if (it < through) it.plusDays(1) else null }.map { date ->
        val meals = byDay[date].orEmpty()
        val declared = marks[date]?.mark ?: DayMark.NONE
        val mark = when {
            declared == DayMark.FASTING && meals.isNotEmpty() -> DayMark.NONE
            declared != DayMark.FASTING && meals.isEmpty() -> DayMark.NONE
            else -> declared
        }
        val intake = when {
            meals.isNotEmpty() -> meals.sumOf { meal -> meal.foods.sumOf { it.calories } }
            mark == DayMark.FASTING -> 0.0
            else -> null
        }
        DayObservation(date, intake, mark)
    }.toList()
}

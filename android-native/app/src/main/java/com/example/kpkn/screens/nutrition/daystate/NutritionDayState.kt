package com.example.kpkn.screens.nutrition.daystate

import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.data.models.NutritionStatus
import com.example.kpkn.domain.nutrition.expenditure.*
import java.time.LocalDate

data class NutritionDayState(
    val date: LocalDate,
    val mark: DayMark = DayMark.NONE,
    val hasMeals: Boolean = false,
    val suggested: Boolean = false,
    val editable: Boolean = true,
    val saving: Boolean = false,
    val needsReview: Boolean = false,
)

/** La sugerencia usa el motor causal de N1; una declaración explícita evita que vuelva a aparecer. */
fun nutritionDayState(
    logs: List<NutritionLog>,
    marks: Map<LocalDate, DayMarkEntry>,
    selected: LocalDate,
    today: LocalDate,
): NutritionDayState {
    val mark = marks[selected]?.mark ?: DayMark.NONE
    val hasMeals = logs.any { it.status != NutritionStatus.PLANNED && nutritionLogDay(it) == selected }
    val needsReview = when (mark) {
        DayMark.FASTING -> hasMeals
        DayMark.PARTIAL, DayMark.COMPLETE_CONFIRMED -> !hasMeals
        DayMark.NONE -> false
    }
    val suggested = mark == DayMark.NONE && !selected.isAfter(today) &&
        selected in PartialDaySuggester.suggest(buildDayObservations(logs, marks, today), today)
    return NutritionDayState(selected, mark, hasMeals, suggested, !selected.isAfter(today), needsReview = needsReview)
}

fun nutritionDayStateLabel(state: NutritionDayState): String = when {
    state.needsReview -> "Revisar estado del día"
    !state.editable -> "Día futuro"
    else -> when (state.mark) {
        DayMark.NONE -> "Día sin marcar"
        DayMark.COMPLETE_CONFIRMED -> "Día completo"
        DayMark.PARTIAL -> "Registro parcial"
        DayMark.FASTING -> "Ayuno (0 kcal)"
    }
}

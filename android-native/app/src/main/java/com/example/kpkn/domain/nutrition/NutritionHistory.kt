package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.DailyGoalSnapshot
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.data.models.NutritionStatus
import java.time.LocalDate

/** A day remains present in the axis even when no food was logged. */
data class NutritionHistoryPoint(
    val date: LocalDate,
    val intakeCalories: Double?,
    val intakeCaloriesMin: Double? = null,
    val intakeCaloriesMax: Double? = null,
    val goal: DailyGoalSnapshot? = null,
    val historicalGoalRegistered: Boolean = goal != null,
)

data class NutritionHistoryCoverage(
    val registeredDays: Int,
    val totalDays: Int,
) {
    val label: String get() = "$registeredDays de $totalDays días"
}

data class NutritionHistorySeries(
    val points: List<NutritionHistoryPoint>,
    val coverage: NutritionHistoryCoverage,
    val averageCaloriesOnRegisteredDays: Double?,
)

/**
 * Builds a stable local-date series. A missing log is null (a gap), never zero;
 * uncertain food ranges are summed independently from the central value.
 */
fun buildNutritionHistory(
    startDate: LocalDate,
    endDate: LocalDate,
    logs: List<NutritionLog>,
    snapshots: List<DailyGoalSnapshot> = emptyList(),
): NutritionHistorySeries {
    val start = minOf(startDate, endDate)
    val end = maxOf(startDate, endDate)
    val byDay = logs.asSequence()
        .filter { it.status != NutritionStatus.PLANNED }
        .groupBy { runCatching { LocalDate.parse(it.date.take(10)) }.getOrNull() }
        .filterKeys { it != null }
        .mapKeys { it.key!! }
    val goals = snapshots.associateBy { runCatching { LocalDate.parse(it.date.take(10)) }.getOrNull() }
        .filterKeys { it != null }
        .mapKeys { it.key!! }
    val points = generateSequence(start) { previous ->
        previous.plusDays(1).takeIf { !it.isAfter(end) }
    }.map { date ->
        val dayLogs = byDay[date].orEmpty()
        val foods = dayLogs.flatMap { it.foods }
        val central = foods.sumOf { it.calories }.takeIf { foods.isNotEmpty() }
        val min = foods.sumOf { it.caloriesMin ?: it.calories }.takeIf { foods.isNotEmpty() }
        val max = foods.sumOf { it.caloriesMax ?: it.calories }.takeIf { foods.isNotEmpty() }
        NutritionHistoryPoint(
            date = date,
            intakeCalories = central,
            intakeCaloriesMin = min,
            intakeCaloriesMax = max,
            goal = goals[date],
        )
    }.toList()
    val registered = points.filter { it.intakeCalories != null }
    val average = registered.mapNotNull { it.intakeCalories }.average().takeIf { registered.isNotEmpty() }
    return NutritionHistorySeries(
        points = points,
        coverage = NutritionHistoryCoverage(registered.size, points.size),
        averageCaloriesOnRegisteredDays = average,
    )
}

package com.example.kpkn.screens.nutrition.checkin

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.nutrition.checkin.*
import com.example.kpkn.domain.nutrition.expenditure.*
import java.time.DayOfWeek
import java.time.LocalDate

/** Datos sintéticos; las cifras y la calidad se calculan con N1. */
object NutritionCheckinSamples {
    val today: LocalDate = LocalDate.of(2026, 10, 19)
    fun context(name: String = "ready"): CheckinContext {
        val days = (56L downTo 1L).map { ago -> DayObservation(today.minusDays(ago), when {
            name == "paused" && ago <= 8 -> null
            name == "partial_days" && ago == 2L -> 300.0
            (name == "partial_days" || name == "fasting") && ago == 4L -> null
            else -> 2400.0
        }) }
        return CheckinContext(
            plan = NutritionPlan(id = "plan-test", calorieTarget = if (name == "proposal_nochange") 2400 else 2100,
                isActive = true, direction = PlanDirection.MAINTENANCE, weeklyChangeKg = 0.0),
            weekStartDay = DayOfWeek.MONDAY, days = days,
            weights = (if (name == "weigh_in") days.takeLast(2) else days).map { WeightObservation(it.date, 80.0) },
            initialKcal = 2400.0, restingKcal = 1600.0,
        )
    }

    fun state(name: String): NutritionCheckinUiState {
        val context = context(name)
        val series = ExpenditureEstimator().estimateSeries(ExpenditureInput(context.initialKcal!!, 1440.0,
            context.days, context.weights, today.minusDays(1)))
        val plan = CheckinPlanner.build(series.last(), series, context.days, context.plan!!, today, today)
        val module = when (name) {
            "ready" -> CheckinModule.INTRODUCTION
            "paused" -> CheckinModule.LOGGING_BREAK
            "fasting" -> CheckinModule.FASTING
            "partial_days" -> CheckinModule.PARTIAL_DAYS
            "weigh_in" -> CheckinModule.WEIGH_IN
            else -> CheckinModule.TARGET_UPDATE
        }
        return NutritionCheckinUiState(CheckinAvailability.NOW, plan, loading = false,
            stepIndex = plan.modules.indexOf(module).coerceAtLeast(0),
            error = if (name == "apply_error") "No se pudo confirmar la decisión. Vuelve a intentarlo; no se aplicará dos veces." else null)
    }
}

package com.example.kpkn.domain.nutrition.checkin

import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.PlanDirection
import com.example.kpkn.domain.nutrition.expenditure.*
import java.time.LocalDate
import kotlin.math.abs
import kotlinx.serialization.Serializable

/** Evidencia que acompaña a una decisión; las fechas ISO mantienen el formato de la copia JSON. */
@Serializable
data class CheckinProposal(
    val planId: String,
    val beforeKcal: Int,
    val afterKcal: Int,
    val effectiveFrom: String,
    val estimateDate: String,
    val expenditureKcal: Double,
    val lowKcal: Double,
    val highKcal: Double,
    val state: String,
    val usedDates: List<String>,
    val excludedDates: Map<String, String>,
    val modelVersion: String,
)

data class CheckinPlan(
    val weekKey: LocalDate,
    val today: LocalDate,
    val basePlan: NutritionPlan,
    val estimate: ExpenditureEstimate?,
    val series: List<ExpenditureEstimate>,
    val modules: List<CheckinModule>,
    val fastingDays: List<LocalDate>,
    val partialDays: List<LocalDate>,
    val proposal: CheckinProposal?,
    val reason: String?,
    val loggingBreak: Boolean,
)

object CheckinPlanner {
    const val MAX_TARGET_STEP_KCAL = 150
    const val TARGET_FLOOR_KCAL = 1200.0

    /** Suelo del modelo, no una pauta de ingesta: 1000 kcal o el 90 % de la TMB ya estimada. */
    fun estimatorFloor(restingKcal: Double?): Double = maxOf(
        1000.0, restingKcal?.takeIf { it.isFinite() && it > 0.0 }?.times(0.90) ?: 0.0,
    )

    fun build(
        estimate: ExpenditureEstimate?,
        series: List<ExpenditureEstimate>,
        days: List<DayObservation>,
        plan: NutritionPlan,
        today: LocalDate,
        weekKey: LocalDate,
        preferences: CheckinPreferences = CheckinPreferences(),
        targetFloorKcal: Double = TARGET_FLOOR_KCAL,
    ): CheckinPlan {
        val through = today.minusDays(1)
        val recentStart = today.minusDays(7)
        val reviewStart = today.minusDays(ExpenditureParams.V1.windowDays.toLong())
        val past = days.filter { it.date <= through }
        val lastLogged = past.filter { it.intakeKcal != null || it.mark == DayMark.FASTING }.maxOfOrNull { it.date }
        val loggingBreak = (lastLogged != null && lastLogged <= through.minusDays(7)) ||
            (estimate?.state == ExpenditureState.PAUSED && estimate.pauseReason == PauseReason.INSUFFICIENT_LOGGING)
        val recent = past.filter { it.date >= recentStart }
        val fasting = recent.filter { it.intakeKcal == null && it.mark == DayMark.NONE }.map { it.date }
        val partial = (PartialDaySuggester.suggest(past, through) +
            estimate?.quality?.excludedDates.orEmpty().filterValues { it == ExclusionReason.UNREVIEWED_LOW }.keys)
            .filter { it >= reviewStart && it <= through }.distinct().sorted()
        val rate = when (plan.direction) {
            PlanDirection.DEFICIT -> -abs(plan.weeklyChangeKg)
            PlanDirection.SURPLUS -> abs(plan.weeklyChangeKg)
            PlanDirection.MAINTENANCE -> 0.0
            PlanDirection.PROFESSIONAL, null -> null
        }
        val reason = when {
            loggingBreak -> "El registro está en pausa. Retómalo antes de cambiar tu objetivo."
            estimate == null -> "Falta la estimación inicial del plan. Revisa el plan antes de ajustar."
            estimate.state == ExpenditureState.STARTING -> "Estamos iniciando: hacen falta más días completos y pesajes."
            estimate.state == ExpenditureState.PAUSED -> "Faltan pesajes recientes para ajustar con confianza."
            rate == null || !rate.isFinite() -> "Este plan necesita una revisión manual de su ritmo."
            else -> null
        }
        val target = if (reason == null && estimate != null && rate != null) CalorieTargetController.propose(
            estimate.kcal, rate, plan.calorieTarget, targetFloorKcal, MAX_TARGET_STEP_KCAL,
        ) else null
        val boundedTarget = target?.takeIf { abs(it.deltaKcal.toLong()) <= MAX_TARGET_STEP_KCAL }
        val finalReason = if (target != null && boundedTarget == null)
            "Tu objetivo está por debajo del suelo. Revisa el plan antes de hacer un cambio mayor." else reason
        val proposal = boundedTarget?.let {
            val quality = estimate!!.quality
            val start = estimate.date.minusDays(quality.windowDays.toLong() - 1)
            CheckinProposal(
                plan.id, plan.calorieTarget, it.targetKcal, today.plusDays(1).toString(), estimate.date.toString(),
                estimate.kcal, estimate.lowKcal, estimate.highKcal, estimate.state.name,
                (0 until quality.windowDays).map { offset -> start.plusDays(offset.toLong()) }
                    .filterNot { date -> date in quality.excludedDates }.map(LocalDate::toString),
                quality.excludedDates.mapKeys { entry -> entry.key.toString() }.mapValues { entry -> entry.value.name },
                estimate.modelVersion,
            )
        }
        val modules = buildList {
            if (!preferences.introductionSeen) add(CheckinModule.INTRODUCTION)
            if (loggingBreak) add(CheckinModule.LOGGING_BREAK)
            if (fasting.isNotEmpty()) add(CheckinModule.FASTING)
            if (partial.isNotEmpty()) add(CheckinModule.PARTIAL_DAYS)
            if (estimate == null || estimate.quality.weighIns < ExpenditureParams.V1.minimumWeighIns || estimate.trend != null) add(CheckinModule.WEIGH_IN)
            add(CheckinModule.TARGET_UPDATE)
        }.filterNot { it in preferences.dismissedModules }
        return CheckinPlan(weekKey, today, plan, estimate, series, modules, fasting, partial, proposal, finalReason, loggingBreak)
    }
}

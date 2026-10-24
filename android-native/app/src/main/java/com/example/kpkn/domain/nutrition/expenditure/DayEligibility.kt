package com.example.kpkn.domain.nutrition.expenditure

import java.time.LocalDate

internal data class EligibleDay(
    val date: LocalDate,
    val kcal: Double?,
    val exclusion: ExclusionReason?,
)

/** La misma revisión causal sirve al cálculo y a las sugerencias. */
internal fun classifyDays(
    observations: List<DayObservation>,
    through: LocalDate,
    params: ExpenditureParams,
): Map<LocalDate, EligibleDay> {
    val past = observations.filter { !it.date.isAfter(through) }.sortedBy { it.date }
    require(past.map { it.date }.distinct().size == past.size) { "Se espera un total de ingesta por día" }
    val eligible = mutableListOf<EligibleDay>()
    return past.associate { day ->
        val recent = eligible.asSequence()
            .filter { !it.date.isBefore(day.date.minusDays(params.typicalIntakeLookbackDays.toLong())) }
            .mapNotNull { it.kcal }
            .toList()
        val typical = recent.takeIf { it.size >= params.minimumTypicalIntakeDays }?.let(::median)
        val lowThreshold = maxOf(params.lowIntakeAbsoluteKcal, (typical ?: 0.0) * params.lowIntakeTypicalFraction)
        val intake = day.intakeKcal
        val reason = when {
            day.mark == DayMark.FASTING -> null
            day.mark == DayMark.PARTIAL -> ExclusionReason.PARTIAL
            intake == null -> ExclusionReason.NO_LOG
            !intake.isFinite() || intake <= 0.0 || intake > params.maximumIntakeKcal -> ExclusionReason.IMPLAUSIBLE
            day.mark == DayMark.NONE && intake < lowThreshold -> ExclusionReason.UNREVIEWED_LOW
            else -> null
        }
        val result = EligibleDay(day.date, if (reason != null) null else if (day.mark == DayMark.FASTING) 0.0 else intake, reason)
        if (reason == null) eligible += result
        day.date to result
    }
}

internal fun median(values: List<Double>): Double {
    val sorted = values.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2.0 else sorted[middle]
}

internal fun dailyWeights(weights: List<WeightObservation>, through: LocalDate): Map<LocalDate, Double> =
    weights.asSequence()
        .filter { !it.date.isAfter(through) && it.kg.isFinite() && it.kg > 0.0 }
        .groupBy { it.date }
        .mapValues { (_, observations) -> median(observations.map { it.kg }) }
        .toSortedMap()

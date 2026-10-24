package com.example.kpkn.domain.nutrition.expenditure

import java.time.LocalDate

/** Sugiere revisión; nunca cambia marcas ni registros. */
object PartialDaySuggester {
    fun suggest(days: List<DayObservation>, through: LocalDate): List<LocalDate> =
        classifyDays(days, through, ExpenditureParams.V1).values
            .filter { it.exclusion == ExclusionReason.UNREVIEWED_LOW }
            .map { it.date }
}

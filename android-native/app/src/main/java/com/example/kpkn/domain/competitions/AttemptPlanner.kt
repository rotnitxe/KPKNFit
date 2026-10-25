package com.example.kpkn.domain.competitions

import com.example.kpkn.data.models.*
import java.util.Locale
import kotlin.math.floor

enum class AttemptStrategy { CONSERVATIVE, TARGET, AMBITIOUS }

/** Plantilla editable de intentos; no certifica una marca futura ni conoce reglas de federación. */
object AttemptPlanner {
    internal fun roundKg(kg: Double): Double = floor(kg / 2.5 + 0.5) * 2.5

    fun plan(movement: CompetitionMovementType, expectedMaxKg: Double,
             strategy: AttemptStrategy = AttemptStrategy.TARGET): List<PlannedAttempt> {
        if (!expectedMaxKg.isFinite() || expectedMaxKg <= 0.0 || movement !in lifts) return emptyList()
        val percents = when (strategy) {
            AttemptStrategy.CONSERVATIVE -> listOf(88,94,98)
            AttemptStrategy.TARGET -> listOf(91,96,100)
            AttemptStrategy.AMBITIOUS -> listOf(92,97,103)
        }
        val result = percents.mapIndexed { i,p -> PlannedAttempt(movement,i + 1,roundKg(expectedMaxKg * p / 100),
            "$p % de ${String.format(Locale.ROOT,"%.1f",expectedMaxKg)} kg (referencia declarada)") }
        return result.takeIf { it.all { a -> a.weightKg.isFinite() && a.weightKg > 0 } }.orEmpty()
    }

    fun expectedMaxOf(profile: PowerliftingProfile, movement: CompetitionMovementType,
                      trainingMaxPercent: Double): Double? {
        if (!matchesCompetitionVariant(profile,movement)) return null
        val values = when (movement) {
            CompetitionMovementType.SQUAT -> Triple(profile.squat1RM,profile.squatE1RM,profile.squatTM)
            CompetitionMovementType.BENCH -> Triple(profile.bench1RM,profile.benchE1RM,profile.benchTM)
            CompetitionMovementType.DEADLIFT -> Triple(profile.deadlift1RM,profile.deadliftE1RM,profile.deadliftTM)
            else -> return null
        }
        fun valid(v: Double?) = v?.takeIf { it.isFinite() && it > 0 }
        return valid(values.first) ?: valid(values.second) ?: values.third?.let { tm ->
            if (trainingMaxPercent.isFinite() && trainingMaxPercent > 0 && trainingMaxPercent <= 1)
                valid(tm / trainingMaxPercent) else null
        }
    }

    val lifts = setOf(CompetitionMovementType.SQUAT,CompetitionMovementType.BENCH,CompetitionMovementType.DEADLIFT)

    internal fun matchesCompetitionVariant(profile: PowerliftingProfile?,movement: CompetitionMovementType): Boolean =
        profile == null || when (movement) {
            CompetitionMovementType.SQUAT -> profile.squatVariant != SquatVariant.FRONT_SQUAT
            CompetitionMovementType.BENCH -> profile.benchVariant == BenchVariant.BARBELL
            CompetitionMovementType.DEADLIFT -> profile.deadliftVariant != DeadliftVariant.HEX_BAR
            else -> false
        }
}

package com.example.kpkn.domain.training

import com.example.kpkn.data.models.MesocycleGoal
import kotlin.math.roundToInt

/**
 * Escala series, reps, %1RM y RPE según el objetivo del mesociclo (ACCUMULATION /
 * INTENSIFICATION / REALIZATION / DELOAD), aplicando también el volumeModifier
 * declarado por el bloque de protocolo. Es el equivalente "de bolsillo" a un
 * motor de periodización: no reemplaza autorregulación real, pero asegura que
 * el volumen y la intensidad varíen de forma consistente por fase.
 */
object PeriodizationEngine {

    data class SetPrescription(
        val sets: Int,
        val reps: Int,
        val percentageRM: Double,
        val rpe: Double,
    )

    private fun volumeGoalMultiplier(goal: MesocycleGoal): Double = when (goal) {
        MesocycleGoal.ACCUMULATION -> 1.15
        MesocycleGoal.INTENSIFICATION -> 1.0
        MesocycleGoal.REALIZATION -> 0.8
        MesocycleGoal.DELOAD -> 0.55
        MesocycleGoal.CUSTOM -> 1.0
    }

    private fun repsForGoal(baseReps: Int, goal: MesocycleGoal): Int = when (goal) {
        MesocycleGoal.ACCUMULATION -> baseReps + 2
        MesocycleGoal.INTENSIFICATION -> baseReps
        MesocycleGoal.REALIZATION -> baseReps - 2
        MesocycleGoal.DELOAD -> baseReps - 1
        MesocycleGoal.CUSTOM -> baseReps
    }.coerceIn(1, 20)

    private fun rpeForGoal(goal: MesocycleGoal): Double = when (goal) {
        MesocycleGoal.ACCUMULATION -> 6.5
        MesocycleGoal.INTENSIFICATION -> 8.0
        MesocycleGoal.REALIZATION -> 9.0
        MesocycleGoal.DELOAD -> 5.5
        MesocycleGoal.CUSTOM -> 7.5
    }

    /** Series ajustadas por objetivo del bloque y por el volumeModifier declarado en el protocolo. */
    fun scaleSets(baseSets: Int, goal: MesocycleGoal, volumeModifier: Double?): Int {
        val multiplier = volumeGoalMultiplier(goal) * (volumeModifier ?: 1.0)
        return (baseSets * multiplier).roundToInt().coerceIn(1, (baseSets * 3).coerceAtLeast(1))
    }

    /**
     * Ondulación mínima de %1RM dentro del bloque: sube gradualmente de
     * intensityMin (semana 1) a intensityMax (última semana del bloque).
     */
    fun percentageForWeek(intensityMin: Int, intensityMax: Int, weekNumber: Int, totalWeeksInBlock: Int): Double {
        if (totalWeeksInBlock <= 1) return (intensityMin + intensityMax) / 2.0
        val progress = ((weekNumber - 1).toDouble() / (totalWeeksInBlock - 1).toDouble()).coerceIn(0.0, 1.0)
        return intensityMin + (intensityMax - intensityMin) * progress
    }

    fun prescriptionFor(
        goal: MesocycleGoal,
        baseSets: Int,
        baseReps: Int,
        volumeModifier: Double?,
        intensityMin: Int,
        intensityMax: Int,
        weekNumber: Int,
        totalWeeksInBlock: Int,
        repScheme: List<Int>? = null,
    ): SetPrescription = SetPrescription(
        sets = scaleSets(baseSets, goal, volumeModifier),
        reps = repScheme
            ?.getOrNull((weekNumber - 1).coerceAtLeast(0))
            ?.coerceIn(1, 20)
            ?: repsForGoal(baseReps, goal),
        percentageRM = percentageForWeek(intensityMin, intensityMax, weekNumber, totalWeeksInBlock),
        rpe = rpeForGoal(goal),
    )
}

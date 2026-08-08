package com.example.kpkn.domain.workout

/** Applies a small first-working-set correction from the athlete's warm-up RPE. */
object WarmupCalibrationEngine {
    fun adjustWorkingLoad(baseKg: Double, warmupRpe: Double?): Double {
        if (baseKg <= 0.0 || warmupRpe == null) return baseKg
        val factor = when {
            warmupRpe >= 9.0 -> 0.975
            warmupRpe <= 5.0 -> 1.025
            else -> 1.0
        }
        return baseKg * factor
    }

    fun explanation(warmupRpe: Double?): String? = when {
        warmupRpe == null -> null
        warmupRpe >= 9.0 -> "Ajuste de aproximación: -2,5% por esfuerzo alto"
        warmupRpe <= 5.0 -> "Ajuste de aproximación: +2,5% por esfuerzo bajo"
        else -> "Ajuste de aproximación: sin cambio"
    }
}

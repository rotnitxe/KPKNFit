package com.example.kpkn.domain.calculations

import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.WeightUnit
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round

enum class LoadRoundDirection {
    FLOOR,
    NEAREST,
    CEIL,
}

object LoadRounding {
    const val DEFAULT_INCREMENT_KG: Double = 2.5
    const val DEFAULT_INCREMENT_LB: Double = 5.0

    val KG_INCREMENT_OPTIONS: List<Double> = listOf(5.0, 2.5, 1.25, 0.5, 0.25)
    val LB_INCREMENT_OPTIONS: List<Double> = listOf(10.0, 5.0, 2.5, 1.25)

    fun roundToStep(
        value: Double,
        step: Double = DEFAULT_INCREMENT_KG,
        direction: LoadRoundDirection = LoadRoundDirection.NEAREST,
    ): Double {
        if (!value.isFinite() || value <= 0.0) return 0.0
        val safeStep = step.takeIf { it.isFinite() && it > 0.0 } ?: DEFAULT_INCREMENT_KG
        val ratio = value / safeStep
        val units = when (direction) {
            LoadRoundDirection.FLOOR -> floor(ratio + 1e-9)
            LoadRoundDirection.NEAREST -> round(ratio)
            LoadRoundDirection.CEIL -> ceil(ratio - 1e-9)
        }
        val clean = (units * safeStep).coerceAtLeast(0.0)
        return round(clean * 100.0) / 100.0
    }

    fun resolveStepKg(
        exerciseOverrideKg: Double? = null,
        machineStepKg: Double? = null,
        defaultStepKg: Double = DEFAULT_INCREMENT_KG,
    ): Double =
        exerciseOverrideKg?.takeIf { it.isFinite() && it in 0.1..25.0 }
            ?: machineStepKg?.takeIf { it.isFinite() && it in 0.1..25.0 }
            ?: defaultStepKg.takeIf { it.isFinite() && it in 0.1..25.0 }
            ?: DEFAULT_INCREMENT_KG

    fun defaultStepKg(settings: Settings): Double =
        settings.defaultLoadIncrementKg.takeIf { it.isFinite() && it in 0.1..25.0 } ?: DEFAULT_INCREMENT_KG

    fun defaultStepLb(settings: Settings): Double =
        settings.defaultLoadIncrementLb.takeIf { it.isFinite() && it > 0.0 } ?: DEFAULT_INCREMENT_LB

    fun equivalentKgStepForLb(lbStep: Double): Double = when {
        lbStep >= 9.0 -> 5.0
        lbStep >= 4.0 -> 2.5
        lbStep >= 2.0 -> 1.25
        lbStep >= 1.0 -> 0.5
        else -> DEFAULT_INCREMENT_KG
    }

    fun defaultStepInDisplayUnit(settings: Settings): Double = when (settings.weightUnit) {
        WeightUnit.LBS -> defaultStepLb(settings)
        WeightUnit.KG -> defaultStepKg(settings)
    }

    fun formatIncrementLabel(step: Double, unit: WeightUnit): String {
        val clean = if (step % 1.0 == 0.0) {
            step.toInt().toString()
        } else {
            val twoDecimals = round(step * 100.0) / 100.0
            if (twoDecimals * 10.0 % 1.0 == 0.0) {
                String.format(java.util.Locale.ROOT, "%.1f", twoDecimals).replace('.', ',')
            } else {
                String.format(java.util.Locale.ROOT, "%.2f", twoDecimals).replace('.', ',')
            }
        }
        val unitLabel = if (unit == WeightUnit.LBS) "lb" else "kg"
        return "$clean $unitLabel"
    }
}

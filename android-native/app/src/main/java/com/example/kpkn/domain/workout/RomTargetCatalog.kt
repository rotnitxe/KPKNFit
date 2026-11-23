package com.example.kpkn.domain.workout

import com.example.kpkn.data.models.PlannedRomTarget
import com.example.kpkn.data.models.RomScaleMode
import kotlin.math.roundToInt

/**
 * Pure domain catalog and formatting helpers for programmable ROM targets (#13).
 * No `android.*` imports.
 */
object RomTargetCatalog {
    data class QualitativePreset(val label: String, val approxPercent: Int)

    val qualitativePresets: List<QualitativePreset> = listOf(
        QualitativePreset("Parcial corto", 35),
        QualitativePreset("Media repetición", 55),
        QualitativePreset("Hasta la paralela", 75),
        QualitativePreset("Rompiendo paralela", 90),
        QualitativePreset("Completo / Profundo", 100),
    )

    fun defaultForMode(mode: RomScaleMode): PlannedRomTarget = when (mode) {
        RomScaleMode.DEGREES -> PlannedRomTarget(mode = RomScaleMode.DEGREES, numericValue = 90)
        RomScaleMode.QUALITATIVE -> PlannedRomTarget(
            mode = RomScaleMode.QUALITATIVE,
            numericValue = 2,
            label = qualitativePresets[2].label,
        )
        RomScaleMode.SCALE_1_10 -> PlannedRomTarget(mode = RomScaleMode.SCALE_1_10, numericValue = 8)
        RomScaleMode.PERCENT -> PlannedRomTarget(mode = RomScaleMode.PERCENT, numericValue = 100)
    }

    fun format(target: PlannedRomTarget): String = when (target.mode) {
        RomScaleMode.DEGREES -> "${(target.numericValue ?: 90).coerceIn(15, 180)}°"
        RomScaleMode.QUALITATIVE -> {
            val custom = target.label?.takeIf { it.isNotBlank() }
            if (custom != null) custom
            else qualitativePresets[(target.numericValue ?: 2).coerceIn(0, qualitativePresets.lastIndex)].label
        }
        RomScaleMode.SCALE_1_10 -> "${(target.numericValue ?: 8).coerceIn(1, 10)}/10"
        RomScaleMode.PERCENT -> "${(target.numericValue ?: 100).coerceIn(10, 120)}%"
    }

    fun toApproxPercent(target: PlannedRomTarget): Int = when (target.mode) {
        RomScaleMode.DEGREES -> (((target.numericValue ?: 90).coerceIn(15, 180) / 140f) * 100f).roundToInt().coerceIn(15, 120)
        RomScaleMode.QUALITATIVE -> {
            val idx = target.numericValue?.coerceIn(0, qualitativePresets.lastIndex)
            val matched = idx?.let { qualitativePresets[it] }
                ?: qualitativePresets.firstOrNull { it.label.equals(target.label, ignoreCase = true) }
            matched?.approxPercent ?: 80
        }
        RomScaleMode.SCALE_1_10 -> ((target.numericValue ?: 8).coerceIn(1, 10) * 10).coerceIn(10, 100)
        RomScaleMode.PERCENT -> (target.numericValue ?: 100).coerceIn(10, 120)
    }

    fun sliderFraction(target: PlannedRomTarget): Float = when (target.mode) {
        RomScaleMode.DEGREES -> (((target.numericValue ?: 90).coerceIn(15, 180) - 15f) / 165f).coerceIn(0f, 1f)
        RomScaleMode.QUALITATIVE -> {
            val idx = (target.numericValue ?: qualitativePresets.indexOfFirst {
                it.label.equals(target.label, ignoreCase = true)
            }.takeIf { it >= 0 } ?: 2).coerceIn(0, qualitativePresets.lastIndex)
            idx.toFloat() / qualitativePresets.lastIndex.toFloat()
        }
        RomScaleMode.SCALE_1_10 -> (((target.numericValue ?: 8).coerceIn(1, 10) - 1f) / 9f).coerceIn(0f, 1f)
        RomScaleMode.PERCENT -> (((target.numericValue ?: 100).coerceIn(10, 120) - 10f) / 110f).coerceIn(0f, 1f)
    }

    fun fromFraction(mode: RomScaleMode, fraction: Float): PlannedRomTarget {
        val f = fraction.coerceIn(0f, 1f)
        return when (mode) {
            RomScaleMode.DEGREES -> {
                val snapped = (((15f + f * 165f) / 5f).roundToInt() * 5).coerceIn(15, 180)
                PlannedRomTarget(mode = RomScaleMode.DEGREES, numericValue = snapped)
            }
            RomScaleMode.QUALITATIVE -> {
                val idx = (f * qualitativePresets.lastIndex).roundToInt().coerceIn(0, qualitativePresets.lastIndex)
                PlannedRomTarget(
                    mode = RomScaleMode.QUALITATIVE,
                    numericValue = idx,
                    label = qualitativePresets[idx].label,
                )
            }
            RomScaleMode.SCALE_1_10 -> {
                val scale = (1f + f * 9f).roundToInt().coerceIn(1, 10)
                PlannedRomTarget(mode = RomScaleMode.SCALE_1_10, numericValue = scale)
            }
            RomScaleMode.PERCENT -> {
                val pct = (10f + f * 110f).roundToInt().coerceIn(10, 120)
                PlannedRomTarget(mode = RomScaleMode.PERCENT, numericValue = pct)
            }
        }
    }
}

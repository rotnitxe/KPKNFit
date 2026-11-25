package com.example.kpkn.screens.progress.design

import androidx.compose.ui.graphics.Color
import com.example.kpkn.domain.training.insights.MuscleIntensityRow
import com.example.kpkn.domain.training.insights.MuscleIntensitySummary
import com.example.kpkn.screens.progress.MuscleVolumeBodyModel
import com.example.kpkn.screens.progress.RegionKind
import com.example.kpkn.screens.progress.RegionPaint
import java.util.Locale

/**
 * Mapa de calor de intensidad muscular (RIR global / RPE ponderado 1.0 · 0.5 · 0.3 · 0.0)
 * sobre las regiones anatómicas compartidas con [MuscleVolumeBodyMap].
 */
object MuscleIntensityHeatmapModel {

    /** 4 escalones térmicos desde RPE < 7 hasta >= 9.2 / fallo. */
    val tierColors: List<Color> = listOf(
        Color(0xFF4BA3C7), // Baja / moderada (RPE < 7.0)
        Color(0xFFE0B341), // Media-alta (RPE 7.0..8.1)
        Color(0xFFE87A3A), // Alta (RPE 8.2..9.1)
        Color(0xFFE5484D), // Máxima / fallo (RPE >= 9.2)
    )

    val legendEntries: List<Pair<RegionPaint, String>> = listOf(
        RegionPaint(RegionKind.FILLED, tierColors[0].copy(alpha = 0.45f), tierColors[0].copy(alpha = 0.8f), 1) to "RIR 4+ (RPE <7)",
        RegionPaint(RegionKind.FILLED, tierColors[1].copy(alpha = 0.62f), tierColors[1].copy(alpha = 0.85f), 2) to "RIR 2–3",
        RegionPaint(RegionKind.FILLED, tierColors[2].copy(alpha = 0.78f), tierColors[2].copy(alpha = 0.9f), 3) to "RIR 1 (RPE ~9)",
        RegionPaint(RegionKind.FILLED, tierColors[3].copy(alpha = 0.92f), tierColors[3].copy(alpha = 0.95f), 4) to "Fallo / RIR 0",
        RegionPaint(RegionKind.NO_DATA, Color.Transparent, Color.White.copy(alpha = 0.16f)) to "Sin intensidad",
    )

    fun paintFor(row: MuscleIntensityRow?): RegionPaint {
        if (row == null || row.ratedSetCount <= 0) {
            return RegionPaint(RegionKind.NO_DATA, Color.Transparent, Color.White.copy(alpha = 0.16f))
        }
        val rpe = row.weightedRpeMean
        val (idx, alpha) = when {
            rpe >= 9.2 -> 3 to 0.92f
            rpe >= 8.2 -> 2 to 0.78f
            rpe >= 7.0 -> 1 to 0.62f
            else -> 0 to 0.46f
        }
        val base = tierColors[idx]
        return RegionPaint(
            kind = RegionKind.FILLED,
            fill = base.copy(alpha = alpha),
            edge = base.copy(alpha = 0.90f),
            level = idx + 1,
        )
    }

    fun paints(summary: MuscleIntensitySummary?): Map<String, RegionPaint> {
        val byName = summary?.muscles?.associateBy { it.muscleName }.orEmpty()
        return MuscleVolumeBodyModel.bodyRegions.associateWith { region ->
            paintFor(byName[region])
        }
    }

    fun regionDescriptions(summary: MuscleIntensitySummary?): Map<String, String> {
        val byName = summary?.muscles?.associateBy { it.muscleName }.orEmpty()
        return MuscleVolumeBodyModel.bodyRegions.associateWith { region ->
            val row = byName[region]
            if (row == null) {
                "$region, sin intensidad declarada"
            } else {
                val rpe = formatDecimal1(row.weightedRpeMean)
                val rir = formatDecimal1(row.weightedRirMean)
                val failNote = if (row.failureSetCount > 0) ", ${row.failureSetCount} al fallo" else ""
                "$region, RPE $rpe, RIR $rir$failNote"
            }
        }
    }

    fun summaryDescription(summary: MuscleIntensitySummary?): String {
        if (summary == null || !summary.hasIntensityData) {
            return "Mapa térmico de intensidad muscular: sin series con RIR, RPE o fallo registrados."
        }
        val top = summary.top3Muscles.joinToString(", ") {
            "${it.muscleName} RPE ${formatDecimal1(it.weightedRpeMean)}"
        }
        return "Mapa de calor de intensidad media por músculo. Mayor intensidad: $top."
    }

    fun formatDecimal1(value: Double): String =
        String.format(Locale.US, "%.1f", value).removeSuffix(".0").replace('.', ',')
}

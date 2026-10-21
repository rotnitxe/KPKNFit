package com.example.kpkn.screens.technique.compare

import com.example.kpkn.domain.biomechanics.bar.PlatePublicationPolicy

import com.example.kpkn.domain.biomechanics.bar.BarSampleSource
import com.example.kpkn.domain.biomechanics.bar.BarTrack
import com.example.kpkn.domain.biomechanics.metrics.MetricKey
import com.example.kpkn.domain.biomechanics.metrics.TechniqueAnalysisEngine
import com.example.kpkn.domain.biomechanics.metrics.ScaleInfo
import com.example.kpkn.domain.biomechanics.metrics.ScaleSource
import com.example.kpkn.domain.biomechanics.metrics.SetTechniqueReport
import com.example.kpkn.domain.biomechanics.metrics.VelocityUnit
import com.example.kpkn.screens.technique.analysis.AnalysisFormat
import com.example.kpkn.screens.technique.compare.design.BarPathPoint

/** Prepara la comparación sin sustituir una medida ausente por los metadatos de la grabación. */
internal object TechniqueComparisonData {
    fun calibratedScale(report: SetTechniqueReport?, bar: BarTrack? = null): ScaleInfo? {
        if (report == null || report.engineVersion < TechniqueAnalysisEngine.ENGINE_VERSION ||
            bar == null || !PlatePublicationPolicy.canUsePlate(bar)) return null
        return report.scale?.takeIf { scale ->
        scale.source == ScaleSource.PLATE &&
            scale.metersPerPxH?.let { it.isFinite() && it > 0.0 } == true &&
            scale.metersPerPxV?.let { it.isFinite() && it > 0.0 } == true &&
            (MetricKey.PATH_ABSOLUTE in report.available || MetricKey.VELOCITY_MPS in report.available)
        }
    }

    fun extractPath(bar: BarTrack?, report: SetTechniqueReport?, inCentimetres: Boolean): List<BarPathPoint> {
        if (bar == null || !PlatePublicationPolicy.canUsePlate(bar) ||
            report != null && report.engineVersion < TechniqueAnalysisEngine.ENGINE_VERSION) return emptyList()
        val fromUs = report?.reps?.minOfOrNull { it.phases.departUs }
        val toUs = report?.reps?.maxOfOrNull { it.phases.arriveUs }
        val samples = bar?.samples?.filter {
            (it.source == BarSampleSource.HOUGH || it.source == BarSampleSource.NCC) &&
                it.quality.isFinite() && it.quality >= 0.5f && it.cx.isFinite() && it.cy.isFinite() &&
                it.tUs >= 0L && (fromUs == null || it.tUs >= fromUs) && (toUs == null || it.tUs <= toUs)
        }?.sortedBy { it.tUs } ?: return emptyList()
        if (samples.size < 2) return emptyList()

        val scale = calibratedScale(report, bar)
        val verticalExcursionPx = samples.maxOf { it.cy }.toDouble() - samples.minOf { it.cy }
        if (!inCentimetres && verticalExcursionPx <= 0.0) return emptyList()
        if (inCentimetres && scale == null) return emptyList()
        val xScale = if (inCentimetres) scale!!.metersPerPxH!! * 100.0 else 100.0 / verticalExcursionPx
        val yScale = if (inCentimetres) scale!!.metersPerPxV!! * 100.0 else xScale
        val origin = samples.first()
        return samples.mapIndexed { index, sample ->
            BarPathPoint(
                dx = ((sample.cx - origin.cx) * xScale).toFloat(),
                dy = ((sample.cy - origin.cy) * yScale).toFloat(),
                startsSegment = index == 0 || sample.tUs - samples[index - 1].tUs > 200_000L,
            )
        }
    }

    fun differenceTable(reportA: SetTechniqueReport?, reportB: SetTechniqueReport?, barA: BarTrack? = null, barB: BarTrack? = null): List<CompareMetricRowUi> = buildList {
        val a = reportA?.takeIf { it.engineVersion >= TechniqueAnalysisEngine.ENGINE_VERSION }
        val b = reportB?.takeIf { it.engineVersion >= TechniqueAnalysisEngine.ENGINE_VERSION }
        fun metric(report: SetTechniqueReport?, key: MetricKey, value: Double?): Double? =
            value?.takeIf { report != null && key in report.available && it.isFinite() && it >= 0.0 }

        fun addMetric(label: String, left: Double?, right: Double?, digits: Int, unit: String, diffUnit: String = unit) {
            if (left == null && right == null) return
            fun cell(value: Double?): String = value?.let { "≈ ${AnalysisFormat.decimal(it, digits)}$unit" } ?: "—"
            val delta = if (left != null && right != null) {
                val diff = right - left
                "≈ ${if (diff > 0.0) "+" else ""}${AnalysisFormat.decimal(diff, digits)}$diffUnit"
            } else "—"
            add(CompareMetricRowUi(label, cell(left), cell(right), delta))
        }

        addMetric("Repeticiones", metric(a, MetricKey.REPS, a?.repCount?.toDouble()), metric(b, MetricKey.REPS, b?.repCount?.toDouble()), 0, " reps")
        addMetric("TUT", metric(a, MetricKey.TUT, a?.tutMs?.div(1000.0)), metric(b, MetricKey.TUT, b?.tutMs?.div(1000.0)), 1, " s")
        addMetric("ROM", metric(a, MetricKey.ROM, a?.romMeanDeg), metric(b, MetricKey.ROM, b?.romMeanDeg), 0, "°")
        addMetric("Pérdida de velocidad", metric(a, MetricKey.VELOCITY_RELATIVE, a?.velocityLossPct), metric(b, MetricKey.VELOCITY_RELATIVE, b?.velocityLossPct), 0, " %", " pp")

        // Sin calibrar solo se compara la pérdida (%); BH/s no es una velocidad absoluta ni se resta a m/s.
        fun mcv(report: SetTechniqueReport?, bar: BarTrack?): Double? = report?.takeIf {
            it.velocityUnit == VelocityUnit.MPS && calibratedScale(it, bar) != null
        }?.let { metric(it, MetricKey.VELOCITY_MPS, it.mcvBest) }
        addMetric("MCV (mejor)", mcv(a, barA), mcv(b, barB), 2, " m/s")
    }
}

package com.example.kpkn.screens.programdetail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.training.MuscleMetric
import com.example.kpkn.domain.training.NamedMetric
import com.example.kpkn.domain.training.ProgramAnalyticsReport
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknType
import java.util.Locale

@Composable
internal fun VolumeAnalyticsCard(
    report: ProgramAnalyticsReport,
    modifier: Modifier = Modifier,
) {
    AnalyticsCard(title = "Balance y cobertura", modifier = modifier) {
        val forgotten = report.coverage.forgottenMuscles.take(4)
        AnalyticsMetricRow(
            label = "Músculos olvidados",
            value = if (forgotten.isEmpty()) "0" else forgotten.joinToString(", "),
            detail = report.coverage.emptyReason ?: "Sale de series semanales ponderadas por rol muscular.",
        )
        AnalyticsMetricRow(
            label = "Empuje / tirón",
            value = ratioLabel(report.balance.pushPullRatio.leftValue, report.balance.pushPullRatio.rightValue),
            detail = report.balance.pushPullRatio.explanation,
        )
        AnalyticsMetricRow(
            label = "Quad / posterior",
            value = ratioLabel(report.balance.quadPosteriorRatio.leftValue, report.balance.quadPosteriorRatio.rightValue),
            detail = report.balance.quadPosteriorRatio.explanation,
        )
        CompactMetricList(
            title = "Top volumen",
            metrics = report.coverage.musclesByWeeklySets.take(3),
        )
        CompactMetricList(
            title = "Reps por músculo",
            metrics = report.coverage.repsByMuscle.take(3),
        )
        CompactDirectIndirectList(
            title = "Directo / indirecto",
            metrics = report.coverage.directIndirectByMuscle
                .sortedByDescending { it.directSets + it.indirectSets }
                .take(3),
        )
        CompactNamedMetricList(
            title = "Patrones",
            metrics = report.balance.movementPatterns.take(4),
        )
        AnalyticsMetricRow(
            label = "Empuje H / tirón H",
            value = ratioLabel(report.balance.horizontalPushPullRatio.leftValue, report.balance.horizontalPushPullRatio.rightValue),
            detail = report.balance.horizontalPushPullRatio.explanation,
        )
        AnalyticsMetricRow(
            label = "Unilateralidad",
            value = "${(report.coverage.unilateralExerciseRatio * 100).format1()}%",
            detail = "Porcentaje de ejercicios con trabajo unilateral.",
        )
        AnalyticsMetricRow(
            label = "Estabilidad requerida",
            value = report.coverage.stabilityDemand.format1(),
            detail = "1 = muy estable, 5 = alta demanda libre/unilateral/carry.",
        )
        CompactNamedMetricList(
            title = "Estabilidad",
            metrics = report.coverage.stabilityDistribution,
        )
    }
}

@Composable
private fun AnalyticsCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    KpknModule(modifier = modifier.fillMaxWidth()) {
        KpknModuleHeader(label = title)
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content()
        }
    }
}

@Composable
private fun AnalyticsMetricRow(
    label: String,
    value: String,
    detail: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = KpknType.bodyStrong, color = WizardColors.text, modifier = Modifier.weight(1f))
            Text(value, style = KpknType.bodyStrong.copy(fontFeatureSettings = "'tnum' 1"), color = WizardColors.textMuted)
        }
        Text(detail, style = KpknType.caption, color = WizardColors.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CompactMetricList(title: String, metrics: List<MuscleMetric>) {
    if (metrics.isEmpty()) return
    Text(title.uppercase(Locale("es")), style = KpknType.eyebrow, color = WizardColors.textMuted)
    metrics.forEach { metric ->
        AnalyticsMetricRow(metric.name, metric.value.format1(), metric.explanation)
    }
}

@Composable
private fun CompactNamedMetricList(title: String, metrics: List<NamedMetric>) {
    if (metrics.isEmpty()) return
    Text(title.uppercase(Locale("es")), style = KpknType.eyebrow, color = WizardColors.textMuted)
    metrics.forEach { metric ->
        AnalyticsMetricRow(metric.label, metric.value.format1(), metric.explanation)
    }
}

@Composable
private fun CompactDirectIndirectList(title: String, metrics: List<com.example.kpkn.domain.training.DirectIndirectMetric>) {
    if (metrics.isEmpty()) return
    Text(title.uppercase(Locale("es")), style = KpknType.eyebrow, color = WizardColors.textMuted)
    metrics.forEach { metric ->
        AnalyticsMetricRow(
            label = metric.muscle,
            value = "${metric.directSets.format1()} / sec ${metric.secondarySets.format1()} / estab ${metric.stabilizerSets.format1()}",
            detail = metric.explanation,
        )
    }
}

private fun ratioLabel(left: Double, right: Double): String =
    "${left.format1()} / ${right.format1()}"

private fun Double.format1(): String = String.format("%.1f", this)

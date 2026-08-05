package com.example.kpkn.screens.programdetail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.domain.training.DiagnosticSeverity
import com.example.kpkn.domain.training.MuscleMetric
import com.example.kpkn.domain.training.NamedMetric
import com.example.kpkn.domain.training.ProgramAnalyticsReport

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
internal fun ProgressAnalyticsCard(
    report: ProgramAnalyticsReport,
    modifier: Modifier = Modifier,
) {
    AnalyticsCard(title = "Fatiga y progreso", modifier = modifier) {
        AnalyticsMetricRow(
            label = "Fatiga lumbar",
            value = report.fatigue.lumbarFatigue.format1(),
            detail = report.fatigue.sourceSummary,
        )
        AnalyticsMetricRow(
            label = "Hombro anterior",
            value = report.fatigue.anteriorShoulderStress.format1(),
            detail = "Calculado desde presses, roles musculares y logs cuando existen.",
        )
        AnalyticsMetricRow(
            label = "Densidad",
            value = "${report.efficiency.densitySetsPerHour.format1()} sets/h",
            detail = "Usa duración real de logs; si falta, usa duración planificada.",
        )
        AnalyticsMetricRow(
            label = "Carga axial",
            value = report.fatigue.axialLoad.format1(),
            detail = "Suma de carga axial estimada desde el catálogo y las series.",
        )
        AnalyticsMetricRow(
            label = "Estrés de agarre",
            value = report.fatigue.gripDemand.format1(),
            detail = "Remos, dominadas, pesos muertos, carries y antebrazo indirecto.",
        )
        CompactMetricList(
            title = "Preparación por músculo",
            metrics = report.fatigue.readinessByMuscle.sortedBy { it.value }.take(4),
        )
        CompactMetricList(
            title = "Deuda recuperación",
            metrics = report.fatigue.recoveryDebtByMuscle.take(4),
        )
        CompactNamedMetricList(
            title = "Riesgo estancamiento",
            metrics = report.progression
                .sortedByDescending { it.stagnationRisk }
                .take(3)
                .map {
                    NamedMetric(
                        id = it.exerciseId,
                        label = it.exerciseName,
                        value = it.stagnationRisk,
                        explanation = "Sparkline e1RM: ${it.sparkline.joinToString(" -> ") { value -> value.format1() }}",
                    )
                },
        )
    }
}

@Composable
internal fun HistoryAnalyticsCard(
    report: ProgramAnalyticsReport,
    modifier: Modifier = Modifier,
) {
    AnalyticsCard(title = "Adherencia y diagnóstico", modifier = modifier) {
        AnalyticsMetricRow(
            label = "Sesiones cumplidas",
            value = "${(report.adherence.completedSessionRatio * 100).format1()}%",
            detail = report.adherence.diagnosis,
        )
        AnalyticsMetricRow(
            label = "Ejercicios cumplidos",
            value = "${(report.adherence.completedExerciseRatio * 100).format1()}%",
            detail = "Compara plan contra logs y ejercicios omitidos.",
        )
        AnalyticsMetricRow(
            label = "Descanso real",
            value = ratioLabel(report.efficiency.restCompliance.leftValue, report.efficiency.restCompliance.rightValue),
            detail = report.efficiency.restCompliance.explanation,
        )
        AnalyticsMetricRow(
            label = "Identidad del bloque",
            value = report.efficiency.blockIdentity,
            detail = "Diagnóstico estructural desde patrones, básicos, accesorios y equipamiento.",
        )
        AnalyticsMetricRow(
            label = "Pareto estímulo",
            value = "${(report.efficiency.topStimulusShare * 100).format1()}%",
            detail = "Porcentaje del estímulo acumulado producido por el bloque superior de ejercicios.",
        )
        CompactNamedMetricList(
            title = "Nube de fatiga",
            metrics = report.fatigue.residualCalendar.take(4),
        )
        CompactNamedMetricList(
            title = "Densidad por sesión",
            metrics = report.efficiency.sessionDensity.take(3),
        )
        val primaryDiagnostic = report.diagnostics.maxByOrNull { it.severity.ordinal }
        if (primaryDiagnostic != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = when (primaryDiagnostic.severity) {
                    DiagnosticSeverity.CRITICAL -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.42f)
                    DiagnosticSeverity.WARNING -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.38f)
                    DiagnosticSeverity.INFO -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.32f)
                },
            ) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(primaryDiagnostic.title, fontWeight = FontWeight.Black, fontSize = 13.sp)
                    Text(primaryDiagnostic.detail, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        CompactNamedMetricList(
            title = "Omitidos",
            metrics = report.adherence.omittedExercises.take(3),
        )
        CompactNamedMetricList(
            title = "Top fatigantes",
            metrics = report.efficiency.topFatiguingExercises.take(3),
        )
    }
}

@Composable
private fun AnalyticsCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Black)
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
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
        }
        Text(detail, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CompactMetricList(title: String, metrics: List<MuscleMetric>) {
    if (metrics.isEmpty()) return
    Text(title, fontSize = 12.sp, fontWeight = FontWeight.Black)
    metrics.forEach { metric ->
        AnalyticsMetricRow(metric.name, metric.value.format1(), metric.explanation)
    }
}

@Composable
private fun CompactNamedMetricList(title: String, metrics: List<NamedMetric>) {
    if (metrics.isEmpty()) return
    Text(title, fontSize = 12.sp, fontWeight = FontWeight.Black)
    metrics.forEach { metric ->
        AnalyticsMetricRow(metric.label, metric.value.format1(), metric.explanation)
    }
}

@Composable
private fun CompactDirectIndirectList(title: String, metrics: List<com.example.kpkn.domain.training.DirectIndirectMetric>) {
    if (metrics.isEmpty()) return
    Text(title, fontSize = 12.sp, fontWeight = FontWeight.Black)
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

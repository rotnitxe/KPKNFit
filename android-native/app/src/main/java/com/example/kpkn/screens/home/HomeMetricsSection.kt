package com.example.kpkn.screens.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.domain.training.insights.VolumeLandmarks
import com.example.kpkn.screens.home.design.homeGapAbove
import com.example.kpkn.screens.home.design.HomePageGap
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknSegmentedSize
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

private val UpGreen = Color(0xFF7FE3B0)
private val OverAmber = Color(0xFFFFA15C)

/**
 * «Métricas de tu entreno»: la semana como barras, una métrica por vez y las referencias de
 * volumen solo donde existen (series). No hay puntuación de recuperación.
 */
@Composable
internal fun HomeMetricsSection(
    metrics: HomeTrainingMetrics,
    onOpenHistory: () -> Unit,
    onOpenProgress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var metric by rememberSaveable { mutableStateOf(HomeMetric.SERIES) }
    var selected by rememberSaveable { mutableIntStateOf(3) }
    val weeks = metrics.weeks
    val safeSelected = selected.coerceIn(0, (weeks.size - 1).coerceAtLeast(0))

    KpknModule(
        modifier = modifier
            .homeGapAbove(HomePageGap)
            .padding(horizontal = 16.dp)
            .testTag("home-training-metrics"),
    ) {
        KpknModuleHeader(
            label = "Métricas de tu entreno",
            action = { KpknTextAction(text = "Historial ›", onClick = onOpenHistory) },
        )
        Spacer(Modifier.height(12.dp))
        KpknSegmentedControl(
            options = listOf("Series", "Tonelaje", "Esfuerzo"),
            selectedIndex = metric.ordinal,
            onSelect = { index -> metric = HomeMetric.entries[index]; selected = 3 },
            size = KpknSegmentedSize.Compact,
        )
        Spacer(Modifier.height(18.dp))

        Headline(metrics = metrics, metric = metric, selected = safeSelected)
        Spacer(Modifier.height(14.dp))
        Bars(
            metrics = metrics,
            metric = metric,
            selected = safeSelected,
            onSelect = { selected = it },
        )
        if (metric == HomeMetric.SERIES) {
            weekNote(weeks.getOrNull(safeSelected))?.let { note ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = note,
                    style = KpknType.caption,
                    color = OverAmber,
                    modifier = Modifier.testTag("home-metric-zone-note"),
                )
            }
        }

        if (metrics.readiness != null) {
            Divider()
            ReadinessRow(metrics.readiness)
        }

        if (metrics.progress.isNotEmpty()) {
            Divider()
            ProgressBlock(progress = metrics.progress, onOpenProgress = onOpenProgress)
        }
    }
}

@Composable
private fun Headline(metrics: HomeTrainingMetrics, metric: HomeMetric, selected: Int) {
    val value = metrics.valueAt(selected, metric)
    val delta = metrics.deltaAt(selected, metric)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(
            text = homeMetricNumber(value, metric),
            style = KpknType.metricL,
            color = WizardColors.text,
            modifier = Modifier.testTag("home-metric-value"),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = metrics.unitOf(metric),
            style = KpknType.unit,
            color = WizardColors.textMuted,
            modifier = Modifier.padding(bottom = 3.dp),
        )
        Spacer(Modifier.weight(1f))
        if (delta != null) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = homeDeltaLabel(delta, metric),
                    style = KpknType.bodyStrong.copy(fontSize = 14.sp),
                    color = if (delta > 0.0) UpGreen else WizardColors.textMuted,
                )
                Text(
                    text = "vs. 4 sem.",
                    style = KpknType.caption.copy(fontSize = 12.sp),
                    color = WizardColors.textFaint,
                )
            }
        }
    }
}

@Composable
private fun Bars(
    metrics: HomeTrainingMetrics,
    metric: HomeMetric,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val values = metrics.weeks.map { metrics.valueOf(it, metric) }
    val max = values.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
    val reduced = wizardReducedMotion()
    Row(
        modifier = Modifier.fillMaxWidth().height(96.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        metrics.weeks.forEachIndexed { index, week ->
            val target = (values[index] / max).toFloat().coerceIn(0.06f, 1f)
            val fraction by animateFloatAsState(
                targetValue = target,
                label = "homeMetricBar$index",
                animationSpec = if (reduced) androidx.compose.animation.core.snap() else androidx.compose.animation.core.tween(420),
            )
            val barColor = when {
                metric == HomeMetric.SERIES && week.aboveMrv -> OverAmber
                metric == HomeMetric.SERIES && VolumeLandmarks.forMuscle(week.worstMuscle.orEmpty()) != null &&
                    week.worstSets < (VolumeLandmarks.forMuscle(week.worstMuscle.orEmpty())?.mev ?: 0.0) ->
                    WizardColors.text.copy(alpha = 0.20f)
                else -> WizardColors.text
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable { onSelect(index) }
                    .testTag("home-metric-bar-$index"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                if (metric == HomeMetric.SERIES && week.aboveMrv) {
                    Canvas(Modifier.size(width = 8.dp, height = 6.dp)) {
                        val path = Path().apply {
                            moveTo(size.width / 2f, 0f)
                            lineTo(size.width, size.height)
                            lineTo(0f, size.height)
                            close()
                        }
                        drawPath(path, OverAmber)
                    }
                    Spacer(Modifier.height(3.dp))
                }
                Box(
                    Modifier
                        .width(18.dp)
                        .height((64 * fraction).dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (index == selected) barColor else barColor.copy(alpha = barColor.alpha * 0.45f)),
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    text = week.label,
                    style = KpknType.caption.copy(fontSize = 12.sp),
                    color = if (index == selected) WizardColors.text else WizardColors.textFaint,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ReadinessRow(readiness: HomeReadiness) {
    val items = listOf(
        Triple("SUEÑO", readiness.sleep?.let { "$it/5" } ?: "—", (readiness.sleep ?: 0) / 5f),
        Triple("ENERGÍA", readiness.energy?.let { "$it/10" } ?: "—", (readiness.energy ?: 0) / 10f),
        Triple("ESTRÉS", readiness.stress?.let { "$it/5" } ?: "—", (readiness.stress ?: 0) / 5f),
        Triple("AGUJETAS", readiness.doms?.let { "$it/5" } ?: "—", (readiness.doms ?: 0) / 5f),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEachIndexed { index, (label, value, fraction) ->
            Column(Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = KpknType.eyebrow.copy(fontSize = 12.sp),
                    color = WizardColors.textFaint,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Text(text = value, style = KpknType.metricM, color = WizardColors.text)
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(WizardColors.text.copy(alpha = 0.10f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction.coerceIn(0f, 1f))
                            .height(3.dp)
                            .background(if (index == 3) OverAmber else WizardColors.text),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressBlock(progress: List<HomeLiftProgress>, onOpenProgress: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("PROGRESO", style = KpknType.eyebrow.copy(fontSize = 12.sp), color = WizardColors.textMuted)
        Spacer(Modifier.weight(1f))
        KpknTextAction(text = "Ver los 5 ›", onClick = onOpenProgress)
    }
    Spacer(Modifier.height(8.dp))
    progress.take(2).forEach { lift ->
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = lift.name,
                style = KpknType.body.copy(fontSize = 14.sp),
                color = WizardColors.text,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Sparkline(lift.points)
            Spacer(Modifier.width(10.dp))
            Text(
                text = lift.deltaKg?.let { homeKgLabel(it) } ?: "—",
                style = KpknType.bodyStrong.copy(fontSize = 13.sp),
                color = if ((lift.deltaKg ?: 0.0) > 0.0) UpGreen else WizardColors.textMuted,
            )
        }
    }
}

@Composable
private fun Sparkline(points: List<Double>) {
    if (points.size < 2) return
    val low = points.min()
    val span = (points.max() - low).takeIf { it > 0.0 } ?: 1.0
    Canvas(Modifier.size(width = 56.dp, height = 18.dp)) {
        val path = Path()
        points.forEachIndexed { index, value ->
            val x = size.width * index / (points.size - 1).toFloat()
            val y = size.height - ((value - low) / span).toFloat() * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, WizardColors.textMuted, style = Stroke(width = 1.6f, cap = StrokeCap.Round))
        drawCircle(
            color = OverAmber,
            radius = 2.4f,
            center = Offset(size.width, size.height - ((points.last() - low) / span).toFloat() * size.height),
        )
    }
}

@Composable
private fun Divider() {
    Spacer(Modifier.height(14.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
    Spacer(Modifier.height(14.dp))
}

private fun weekNote(week: HomeMetricWeek?): String? {
    if (week == null || !week.aboveMrv) return null
    val muscle = week.worstMuscle ?: return null
    val reference = VolumeLandmarks.referenceFor(muscle) ?: return null
    return "$muscle ${homeMetricNumber(week.worstSets, HomeMetric.SERIES)} series · referencia ${homeMetricNumber(reference, HomeMetric.SERIES)}"
}

internal fun homeMetricNumber(value: Double, metric: HomeMetric): String {
    val decimals = if (metric == HomeMetric.SERIES) 1 else 0
    return com.example.kpkn.screens.workout.finishNumber(value, decimals).replace('\u00A0', ' ')
}

internal fun homeDeltaLabel(delta: Double, metric: HomeMetric): String = when {
    kotlin.math.abs(delta) < 0.005 -> "="
    delta > 0.0 -> "+${homeMetricNumber(delta, metric)}"
    else -> homeMetricNumber(delta, metric)
}

private fun homeKgLabel(delta: Double): String = when {
    kotlin.math.abs(delta) < 0.005 -> "="
    delta > 0.0 -> "+${com.example.kpkn.screens.workout.finishNumber(delta)}"
    else -> com.example.kpkn.screens.workout.finishNumber(delta)
}

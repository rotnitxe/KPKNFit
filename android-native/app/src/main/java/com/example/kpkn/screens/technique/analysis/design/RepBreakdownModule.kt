package com.example.kpkn.screens.technique.analysis.design

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.domain.biomechanics.metrics.RepKind
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.technique.analysis.RepBarUi
import com.example.kpkn.screens.technique.analysis.RepBreakdownUi
import com.example.kpkn.screens.technique.analysis.RepMetricKind
import com.example.kpkn.screens.technique.analysis.RepMetricUi
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknSegmentedSize
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Etiquetas de prueba del módulo «Por repetición» y de su gráfica. */
const val ANALYSIS_BREAKDOWN_TAG = "analysis-breakdown"
const val ANALYSIS_REP_CHART_TAG = "analysis-rep-chart"

private val ChartHeight = 168.dp
private val LabelZone = 26.dp
private val AxisZone = 22.dp
private val MaxBarWidth = 30.dp
private val StubHeight = 3.dp

/**
 * El módulo «Por repetición» (sin estado): un control segmentado compacto con las medidas que tienen dato («Velocidad · ROM ·
 * Tiempo») y, debajo, una gráfica de barras en Canvas, una por repetición.
 *
 * - **Altura:** relativa a la mejor de la medida, desde cero (no se recorta el eje). Sin eje con cifras: la cifra de la repetición
 *   elegida va sobre su barra.
 * - **Estilo:** la elegida, en tinta; las demás, al 40 %. Las parciales con trama y las fallidas en `danger`. Sin ninguna elegida,
 *   todas al 85 %. Una repetición sin dato en esa medida deja un tope bajo, no una barra inventada.
 * - **Elegir:** tocar una barra, o arrastrar el dedo por la gráfica, elige la repetición ([onSelectRep] recibe su posición en la capa)
 *   y el reproductor salta a ella. Para TalkBack, la gráfica se lee entera («Velocidad: la más rápida, la 1; la más lenta, la 8, un
 *   18 % menos») y se recorre con «Repetición anterior» y «Repetición siguiente».
 * - **Detalle:** bajo la gráfica, la línea de la elegida («Rep 4 · bajada 2,1 s · subida 0,9 s · ROM 104° · bajo el paralelo»).
 */
@Composable
fun RepBreakdownModule(
    breakdown: RepBreakdownUi,
    selectedMetric: RepMetricKind,
    onSelectMetric: (RepMetricKind) -> Unit,
    selectedRep: Int,
    onSelectRep: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = breakdown.metrics
    if (metrics.isEmpty()) return
    val metric = metrics.firstOrNull { it.kind == selectedMetric } ?: metrics.first()
    KpknModule(modifier = modifier.testTag(ANALYSIS_BREAKDOWN_TAG)) {
        KpknModuleHeader("Por repetición")
        if (metrics.size > 1) {
            KpknSegmentedControl(
                options = metrics.map { it.kind.label },
                selectedIndex = metrics.indexOf(metric),
                onSelect = { onSelectMetric(metrics[it].kind) },
                size = KpknSegmentedSize.Compact,
                descriptions = metrics.map { spokenName(it.kind) },
            )
            Spacer(Modifier.height(10.dp))
        }
        RepBarChart(metric = metric, reps = breakdown.reps, selected = selectedRep, onSelect = onSelectRep)
        Spacer(Modifier.height(10.dp))
        val chosen = breakdown.reps.getOrNull(selectedRep)
        Text(
            text = chosen?.detail ?: "Toca una barra para ver esa repetición en el vídeo.",
            style = KpknType.caption,
            color = if (chosen != null) WizardColors.text else WizardColors.textMuted,
        )
        if (breakdown.hasPartial || breakdown.hasFailed) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                if (breakdown.hasPartial) LegendItem(text = "Parcial", failed = false)
                if (breakdown.hasFailed) LegendItem(text = "Fallida", failed = true)
            }
        }
        metric.note?.let { note ->
            Spacer(Modifier.height(10.dp))
            Text(text = note, style = KpknType.caption, color = WizardColors.textFaint)
        }
    }
}

private fun spokenName(kind: RepMetricKind): String = when (kind) {
    RepMetricKind.VELOCITY -> "Velocidad"
    RepMetricKind.ROM -> "Rango de movimiento"
    RepMetricKind.TIME -> "Tiempo"
}

@Composable
private fun LegendItem(text: String, failed: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(12.dp)) {
            if (failed) {
                drawRoundRect(WizardColors.danger, cornerRadius = CornerRadius(3.dp.toPx()))
            } else {
                drawHatchedBar(
                    left = 0f, top = 0f, width = size.width, height = size.height, radius = 3.dp.toPx(),
                    color = WizardColors.text.copy(alpha = 0.85f), strokeWidth = 1.2.dp.toPx(), spacing = 3.5.dp.toPx(),
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(text = text, style = KpknType.caption, color = WizardColors.textMuted)
    }
}

/** Gráficas ya dibujadas en este proceso: el crecimiento de las barras es una sola vez por proceso. */
private object ChartMemory {
    private val grown = HashSet<String>()

    @Synchronized
    fun firstTime(key: String): Boolean = grown.add(key)
}

@Composable
private fun RepBarChart(metric: RepMetricUi, reps: List<RepBarUi>, selected: Int, onSelect: (Int) -> Unit) {
    val count = reps.size
    val reduced = wizardReducedMotion()
    val measurer = rememberTextMeasurer()
    val latestSelect by rememberUpdatedState(onSelect)

    // Las barras crecen desde cero la primera vez que se ven. Se lee en el dibujo, no en la composición.
    val animate = remember { ChartMemory.firstTime("rep-chart") } && !reduced
    var goal by remember { mutableFloatStateOf(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) { goal = 1f }
    val growSpec: AnimationSpec<Float> = if (reduced) snap() else tween(durationMillis = 700, easing = FastOutSlowInEasing)
    val grow = animateFloatAsState(targetValue = goal, animationSpec = growSpec, label = "analysisBars")

    val valueStyle = KpknType.chip.copy(color = WizardColors.text)
    val axisStyle = KpknType.caption.copy(fontSize = 11.sp, lineHeight = 14.sp, color = WizardColors.textFaint)
    val axisChosenStyle = KpknType.chip.copy(fontSize = 11.sp, lineHeight = 14.sp, color = WizardColors.text)
    val noDataStyle = KpknType.caption.copy(color = WizardColors.textMuted)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(ChartHeight)
            .testTag(ANALYSIS_REP_CHART_TAG)
            .semantics {
                contentDescription = metric.description
                stateDescription = if (selected in 0 until count) "Repetición ${selected + 1} de $count elegida" else "Ninguna repetición elegida"
                customActions = listOf(
                    CustomAccessibilityAction("Repetición anterior") {
                        latestSelect(if (selected <= 0) 0 else selected - 1)
                        true
                    },
                    CustomAccessibilityAction("Repetición siguiente") {
                        latestSelect(min(count - 1, selected + 1))
                        true
                    },
                )
            }
            // Un toque elige esa barra (aunque ya fuera la elegida: el vídeo vuelve a su inicio).
            .pointerInput(count) {
                detectTapGestures { offset ->
                    if (count > 0) latestSelect(((offset.x / (size.width.toFloat() / count)).toInt()).coerceIn(0, count - 1))
                }
            }
            // Arrastrar recorre las barras; solo avisa cuando cambia de una a otra.
            .pointerInput(count) {
                var last = -1
                detectHorizontalDragGestures(
                    onDragStart = { start ->
                        last = -1
                        if (count > 0) {
                            last = ((start.x / (size.width.toFloat() / count)).toInt()).coerceIn(0, count - 1)
                            latestSelect(last)
                        }
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        if (count > 0) {
                            val index = ((change.position.x / (size.width.toFloat() / count)).toInt()).coerceIn(0, count - 1)
                            if (index != last) {
                                last = index
                                latestSelect(index)
                            }
                        }
                    },
                )
            },
    ) {
        if (count == 0) return@Canvas
        val w = size.width
        val plotTop = LabelZone.toPx()
        val plotBottom = size.height - AxisZone.toPx()
        val plotHeight = plotBottom - plotTop
        val slot = w / count
        val barWidth = min(slot * 0.58f, MaxBarWidth.toPx()).coerceAtLeast(4.dp.toPx())
        val radius = min(6.dp.toPx(), barWidth / 2f)
        val stub = StubHeight.toPx()
        val growNow = grow.value
        val ink = WizardColors.text

        drawLine(KpknSurface.divider, Offset(0f, plotBottom), Offset(w, plotBottom), strokeWidth = 1.dp.toPx())

        val labelStep = max(1, ceil(count / 12.0).toInt())
        for (i in 0 until count) {
            val centerX = slot * (i + 0.5f)
            val left = centerX - barWidth / 2f
            val relative = metric.heights.getOrNull(i)
            val barHeight = if (relative != null) max(plotHeight * relative * growNow, stub) else stub
            val top = plotBottom - barHeight
            val isChosen = i == selected
            val alpha = when {
                selected < 0 -> 0.85f
                isChosen -> 1f
                else -> 0.4f
            }
            val kind = reps[i].kind
            val color = (if (kind == RepKind.FAILED) WizardColors.danger else ink).copy(alpha = if (relative == null) alpha * 0.75f else alpha)
            if (kind == RepKind.PARTIAL) {
                drawHatchedBar(left, top, barWidth, barHeight, radius, color, strokeWidth = 1.4.dp.toPx(), spacing = 4.dp.toPx())
            } else {
                drawBar(left, top, barWidth, barHeight, radius, color)
            }

            // Número de la repetición bajo la barra: todos hasta 12; con más, uno de cada tanto y siempre la elegida.
            if (i % labelStep == 0 || isChosen) {
                val number = measurer.measure(reps[i].number.toString(), if (isChosen) axisChosenStyle else axisStyle, maxLines = 1)
                drawText(number, topLeft = Offset(centerX - number.size.width / 2f, plotBottom + 5.dp.toPx()))
            }

            // La cifra de la elegida, sobre su barra. Sin dato no hay barra a la que subirse: el aviso va arriba del todo, libre de las
            // barras de al lado (que sí llegan a media altura y taparían un texto pequeño).
            if (isChosen) {
                val text = metric.valueLabels.getOrNull(i)
                val layout = if (text != null) measurer.measure(text, valueStyle, maxLines = 1) else measurer.measure("Sin dato", noDataStyle, maxLines = 1)
                val x = (centerX - layout.size.width / 2f).coerceIn(0f, max(0f, w - layout.size.width))
                val anchor = if (text != null) top else plotTop
                val y = max(0f, anchor - layout.size.height - 4.dp.toPx())
                drawText(layout, topLeft = Offset(x, y))
            }
        }
    }
}

/** Una barra con las esquinas de arriba redondeadas y las de abajo casi rectas. */
private fun DrawScope.drawBar(left: Float, top: Float, width: Float, height: Float, radius: Float, color: Color) {
    drawPath(barPath(left, top, width, height, radius), color)
}

/** Una barra con trama diagonal (parcial): rayas a 45° recortadas a la barra y un filete del mismo color. */
private fun DrawScope.drawHatchedBar(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    radius: Float,
    color: Color,
    strokeWidth: Float,
    spacing: Float,
) {
    val outline = barPath(left, top, width, height, radius)
    clipPath(outline) {
        var x = left - height
        while (x < left + width) {
            drawLine(color, Offset(x, top + height), Offset(x + height, top), strokeWidth = strokeWidth, cap = StrokeCap.Butt)
            x += spacing
        }
    }
    drawPath(outline, color, style = Stroke(width = strokeWidth))
}

private fun barPath(left: Float, top: Float, width: Float, height: Float, radius: Float): Path {
    val bottom = min(radius * 0.4f, height / 2f)
    return Path().apply {
        addRoundRect(
            RoundRect(
                left = left,
                top = top,
                right = left + width,
                bottom = top + height,
                topLeftCornerRadius = CornerRadius(min(radius, height)),
                topRightCornerRadius = CornerRadius(min(radius, height)),
                bottomRightCornerRadius = CornerRadius(bottom),
                bottomLeftCornerRadius = CornerRadius(bottom),
            ),
        )
    }
}

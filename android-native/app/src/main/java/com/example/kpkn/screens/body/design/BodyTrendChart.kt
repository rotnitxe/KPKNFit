package com.example.kpkn.screens.body.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.body.BodyMetricPoint
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.formatDecimalEs
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.design.KpknEntranceMemory
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/** Un día de la gráfica: su pesaje (null si no pesó ese día) y la tendencia (null si no la hay). */
data class BodyChartPoint(
    val date: LocalDate,
    val value: Double?,
    val trend: Double?,
)

/** Une la serie diaria con su tendencia por día. Las dos salen del mismo cálculo, así que casan por fecha. */
fun buildBodyChartPoints(series: List<BodyMetricPoint>, trend: List<BodyMetricPoint>): List<BodyChartPoint> {
    val trendByDay = trend.associate { it.date to it.value }
    return series.map { BodyChartPoint(date = it.date, value = it.value, trend = trendByDay[it.date]) }
}

/** Geometría en píxeles, calculada una vez por puntos y tamaño: nunca dentro del dibujo. */
internal class BodyChartGeometry(
    /** X de cada día. */
    val xs: FloatArray,
    /** Y del pesaje de cada día; NaN si no pesó. */
    val valueYs: FloatArray,
    /** Y de la tendencia de cada día; NaN si no la hay. */
    val trendYs: FloatArray,
    /** La tendencia unida, lista para un trazo. */
    val trendPath: Path,
    /** Y de la meta, solo si cae dentro de la escala. */
    val goalY: Float?,
    /** Y del valor máximo y del mínimo de los datos: ahí se centran las cifras de la escala. */
    val hiY: Float,
    val loY: Float,
)

/** Margen vertical de la escala, a cada lado de los datos (fracción del rango). */
private const val SCALE_MARGIN = 0.12

/** Alto mínimo de la escala, en unidad de la medida (kg o %). Evita que un dato plano se estire. */
private const val MIN_SCALE_SPAN = 0.5

/** Cuánto se queda la burbuja tras un toque. */
private const val BUBBLE_HOLD_MS = 2_500L

/** Duración del dibujo de izquierda a derecha de la tendencia. */
private const val REVEAL_MS = 700

/** Rangos de más de un año: el eje muestra mes y año. */
private const val LONG_SPAN_DAYS = 366L

/** Ancho de la columna de cifras de la escala, y su separación respecto a la gráfica. Holgado para el 130 % de letra. */
private val Y_LABEL_WIDTH: Dp = 52.dp

private val Y_GAP: Dp = 6.dp

private val AXIS_TABULAR: TextStyle = KpknType.caption.copy(fontFeatureSettings = "'tnum' 1")

/**
 * Escala y posiciones de los días. La meta solo aparece si cae dentro de la escala de los datos, salvo con [includeGoal]: entonces la
 * escala la incluye si queda a menos de tres veces el rango de los datos, para que la línea discontinua se vea.
 */
internal fun bodyChartGeometry(
    points: List<BodyChartPoint>,
    goal: Double?,
    width: Float,
    height: Float,
    pad: Float,
    includeGoal: Boolean = false,
): BodyChartGeometry? {
    if (points.isEmpty() || width <= 0f || height <= 0f) return null
    val values = points.flatMap { listOfNotNull(it.value, it.trend) }
    val lo = values.minOrNull() ?: return null
    val hi = values.maxOrNull() ?: return null
    val span = maxOf(hi - lo, MIN_SCALE_SPAN)
    // Las etiquetas y `hiY`/`loY` siguen siendo solo de los datos; la meta solo amplía la escala vertical.
    val meta = goal?.takeIf { includeGoal && it >= lo - 3 * span && it <= hi + 3 * span }
    val escalaLo = minOf(lo, meta ?: lo)
    val escalaHi = maxOf(hi, meta ?: hi)
    val escala = maxOf(escalaHi - escalaLo, MIN_SCALE_SPAN)
    val yMin = escalaLo - escala * SCALE_MARGIN
    val yMax = escalaHi + escala * SCALE_MARGIN
    val firstDay = points.first().date.toEpochDay()
    val days = (points.last().date.toEpochDay() - firstDay).toFloat()
    val plotWidth = width - 2f * pad
    val plotHeight = height - 2f * pad
    fun xOf(day: LocalDate): Float =
        if (days <= 0f) width / 2f else pad + (day.toEpochDay() - firstDay) / days * plotWidth
    fun yOf(value: Double): Float = (pad + (yMax - value) / (yMax - yMin) * plotHeight).toFloat()

    val xs = FloatArray(points.size)
    val valueYs = FloatArray(points.size)
    val trendYs = FloatArray(points.size)
    val path = Path()
    var started = false
    points.forEachIndexed { i, point ->
        xs[i] = xOf(point.date)
        valueYs[i] = point.value?.let { yOf(it) } ?: Float.NaN
        val trend = point.trend
        if (trend != null) {
            val y = yOf(trend)
            trendYs[i] = y
            if (started) path.lineTo(xs[i], y) else path.moveTo(xs[i], y)
            started = true
        } else {
            trendYs[i] = Float.NaN
        }
    }
    val goalY = goal?.takeIf { it >= yMin && it <= yMax }?.let { yOf(it) }
    return BodyChartGeometry(
        xs = xs,
        valueYs = valueYs,
        trendYs = trendYs,
        trendPath = path,
        goalY = goalY,
        hiY = yOf(hi),
        loY = yOf(lo),
    )
}

/** Etiquetas de la gráfica: máximo y mínimo de los datos, y las fechas del eje (inicio, mitad y fin). */
internal class BodyChartLabels(val max: String, val min: String, val axis: List<String>)

internal fun bodyChartLabels(points: List<BodyChartPoint>): BodyChartLabels? {
    val values = points.flatMap { listOfNotNull(it.value, it.trend) }
    val lo = values.minOrNull() ?: return null
    val hi = values.maxOrNull() ?: return null
    val first = points.first().date
    val last = points.last().date
    val days = last.toEpochDay() - first.toEpochDay()
    val axis = if (days <= 0L) {
        listOf(bodyDayShortEs(first))
    } else {
        val longSpan = days > LONG_SPAN_DAYS
        listOf(first, first.plusDays(days / 2), last).map { day ->
            if (longSpan) bodyMonthYearShortEs(day) else bodyDayShortEs(day)
        }
    }
    return BodyChartLabels(max = formatDecimalEs(hi, 1), min = formatDecimalEs(lo, 1), axis = axis)
}

/** Resumen para lectores de pantalla: «Tendencia de peso: de 66,4 a 64,0 kg, entre el 12 de agosto y el 13 de octubre». */
internal fun bodyChartDescription(points: List<BodyChartPoint>, name: String, unit: String): String {
    val trends = points.mapNotNull { point -> point.trend?.let { point.date to it } }
    val (fromDay, fromValue) = trends.firstOrNull() ?: return "Tendencia de $name: sin datos en este rango"
    val (toDay, toValue) = trends.last()
    return "Tendencia de $name: de ${formatDecimalEs(fromValue, 1)} a ${formatDecimalEs(toValue, 1)} $unit, " +
        "entre el ${bodyDayMonthEs(fromDay)} y el ${bodyDayMonthEs(toDay)}"
}

/** Índice del día más cercano a [x] (en píxeles), o -1 si no hay días. */
internal fun bodyNearestIndex(xs: FloatArray, x: Float): Int {
    var best = -1
    var bestDistance = Float.MAX_VALUE
    for (i in xs.indices) {
        val distance = abs(xs[i] - x)
        if (distance < bestDistance) {
            bestDistance = distance
            best = i
        }
    }
    return best
}

/**
 * Gráfica de tendencia de Cuerpo (KPKN_UI_V2 §1 y BODY_V1 §2.2): los pesajes diarios tenues, la tendencia en tinta y la meta
 * discontinua, con su cifra al final de la línea y en el eje (solo si cae dentro de la escala). Al pulsar o arrastrar, una guía vertical y una burbuja con el día, su pesaje y la
 * tendencia, con un toque háptico al cambiar de día. La primera vez que hay datos, la tendencia se dibuja de izquierda a derecha; con
 * movimiento reducido, no hay entrada. Escala a la derecha y tres fechas debajo. Sin estado externo: la selección vive aquí.
 *
 * @param name medida en minúsculas, para la descripción de accesibilidad («peso», «grasa corporal»).
 * @param includeGoal si es `true`, la escala incluye la meta cuando queda cerca de los datos, para que su línea se vea.
 */
@Composable
fun BodyTrendChart(
    points: List<BodyChartPoint>,
    goal: Double?,
    unit: String,
    modifier: Modifier = Modifier,
    name: String = "peso",
    height: Dp = 200.dp,
    includeGoal: Boolean = false,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val reduced = wizardReducedMotion()
    val padPx = with(density) { 8.dp.toPx() }
    val dotPx = with(density) { 1.5.dp.toPx() }
    val guidePx = with(density) { 1.dp.toPx() }
    val selectedDotPx = with(density) { 4.dp.toPx() }
    val goalStrokePx = with(density) { 1.5.dp.toPx() }
    // La cifra de la meta va 18 dp sobre su línea (16 dp de caption y 2 de aire). En el eje, una marca de la meta no entra si
    // queda a menos de 16 dp de la del máximo o la del mínimo.
    val goalLabelLiftPx = with(density) { 18.dp.toPx() }
    val axisMinGapPx = with(density) { 16.dp.toPx() }
    val goalDash = remember(density) {
        PathEffect.dashPathEffect(floatArrayOf(with(density) { 6.dp.toPx() }, with(density) { 5.dp.toPx() }))
    }
    val trendStroke = remember(density) {
        Stroke(width = with(density) { 2.5.dp.toPx() }, cap = StrokeCap.Round, join = StrokeJoin.Round)
    }

    // Entrada: la primera vez que hay datos, la tendencia se dibuja de izquierda a derecha (una vez por proceso).
    val hasData = points.any { it.value != null || it.trend != null }
    val reveal = remember(name, hasData, reduced) {
        hasData && !reduced && KpknEntranceMemory.firstTime("body-chart-$name", 0)
    }
    val progress = remember(reveal) { Animatable(if (reveal) 0f else 1f) }
    LaunchedEffect(progress) {
        if (reveal) progress.animateTo(1f, tween(durationMillis = REVEAL_MS))
    }

    var plotSize by remember { mutableStateOf(IntSize.Zero) }
    val geometry = remember(points, goal, plotSize, padPx, includeGoal) {
        bodyChartGeometry(points, goal, plotSize.width.toFloat(), plotSize.height.toFloat(), padPx, includeGoal)
    }
    val labels = remember(points) { bodyChartLabels(points) }
    val description = remember(points, name, unit) { bodyChartDescription(points, name, unit) }

    var selected by remember(points) { mutableStateOf<Int?>(null) }
    var dragging by remember(points) { mutableStateOf(false) }
    // Un toque deja la burbuja BUBBLE_HOLD_MS; mientras el dedo arrastra, se queda.
    LaunchedEffect(selected, dragging) {
        if (selected != null && !dragging) {
            delay(BUBBLE_HOLD_MS)
            selected = null
        }
    }
    fun select(x: Float) {
        val g = geometry ?: return
        val index = bodyNearestIndex(g.xs, x)
        if (index >= 0 && index != selected) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            selected = index
        }
    }

    Column(modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description }) {
        Row(modifier = Modifier.fillMaxWidth().height(height)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .onSizeChanged { plotSize = it }
                    .pointerInput(geometry) {
                        detectTapGestures(onTap = { offset -> select(offset.x) })
                    }
                    .pointerInput(geometry) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                dragging = true
                                select(offset.x)
                            },
                            onDragEnd = {
                                dragging = false
                                selected = null
                            },
                            onDragCancel = {
                                dragging = false
                                selected = null
                            },
                            onHorizontalDrag = { change, _ ->
                                change.consume()
                                select(change.position.x)
                            },
                        )
                    },
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val g = geometry ?: return@Canvas
                    g.goalY?.let { y ->
                        drawLine(
                            color = SymbolPalette.ok.copy(alpha = 0.6f),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = goalStrokePx,
                            pathEffect = goalDash,
                        )
                    }
                    for (i in g.valueYs.indices) {
                        val y = g.valueYs[i]
                        if (!y.isNaN()) drawCircle(color = WizardColors.textFaint, radius = dotPx, center = Offset(g.xs[i], y))
                    }
                    val p = progress.value
                    if (p < 1f) {
                        drawContext.canvas.save()
                        drawContext.canvas.clipRect(0f, 0f, size.width * p, size.height)
                        drawPath(path = g.trendPath, color = WizardColors.text, style = trendStroke)
                        drawContext.canvas.restore()
                    } else {
                        drawPath(path = g.trendPath, color = WizardColors.text, style = trendStroke)
                    }
                    selected?.let { i ->
                        val x = g.xs[i]
                        drawLine(
                            color = Color.White.copy(alpha = 0.3f),
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = guidePx,
                        )
                        val ty = g.trendYs[i]
                        if (!ty.isNaN()) drawCircle(color = WizardColors.text, radius = selectedDotPx, center = Offset(x, ty))
                    }
                }
                // La meta lleva su cifra al final de la línea discontinua, en caption y en el verde de «hecho».
                val goalLineY = geometry?.goalY
                if (goal != null && goalLineY != null) {
                    Text(
                        text = "Meta ${bodyTargetEs(goal)} $unit",
                        style = KpknType.caption,
                        color = WizardColors.done,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset { IntOffset(x = 0, y = (goalLineY - goalLabelLiftPx).roundToInt().coerceAtLeast(0)) },
                    )
                }
                val sel = selected
                if (geometry == null) {
                    Text(
                        text = "Sin datos en este rango",
                        style = KpknType.caption,
                        color = WizardColors.textFaint,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else if (sel != null) {
                    BodyChartBubble(
                        point = points[sel],
                        unit = unit,
                        guideX = geometry.xs[sel],
                        plotWidthPx = plotSize.width,
                    )
                }
            }
            Box(modifier = Modifier.width(Y_LABEL_WIDTH).fillMaxHeight().padding(start = Y_GAP)) {
                if (labels != null && geometry != null) {
                    // Cada cifra se centra en el dato al que se refiere: el máximo arriba y el mínimo abajo.
                    val half = with(density) { 10.dp.roundToPx() }
                    Text(
                        text = labels.max,
                        style = AXIS_TABULAR,
                        color = WizardColors.textFaint,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset { IntOffset(x = 0, y = (geometry.hiY - half).roundToInt().coerceAtLeast(0)) },
                    )
                    Text(
                        text = labels.min,
                        style = AXIS_TABULAR,
                        color = WizardColors.textFaint,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset { IntOffset(x = 0, y = (geometry.loY - half).roundToInt().coerceAtLeast(0)) },
                    )
                    // La meta también marca su cifra en el eje (con una decimal, como el máximo y el mínimo), salvo que choque con ellos.
                    val goalAxisY = geometry.goalY
                    if (goal != null && goalAxisY != null &&
                        abs(goalAxisY - geometry.hiY) >= axisMinGapPx && abs(goalAxisY - geometry.loY) >= axisMinGapPx
                    ) {
                        Text(
                            text = formatDecimalEs(goal, 1),
                            style = AXIS_TABULAR,
                            color = WizardColors.done,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset { IntOffset(x = 0, y = (goalAxisY - half).roundToInt().coerceAtLeast(0)) },
                        )
                    }
                }
            }
        }
        if (labels != null) {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(end = Y_LABEL_WIDTH + Y_GAP),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                labels.axis.forEach { label ->
                    Text(text = label, style = KpknType.caption, color = WizardColors.textFaint, maxLines = 1)
                }
            }
        }
    }
}

/** Burbuja de vidrio sólido (`KpknSurface.inset`, filete `moduleBorder`, radio 12 dp), centrada en la guía y sin salirse de la gráfica. */
@Composable
private fun BodyChartBubble(
    point: BodyChartPoint,
    unit: String,
    guideX: Float,
    plotWidthPx: Int,
) {
    var bubbleWidth by remember { mutableStateOf(0) }
    val left = (guideX - bubbleWidth / 2f).roundToInt().coerceIn(0, (plotWidthPx - bubbleWidth).coerceAtLeast(0))
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .offset { IntOffset(x = left, y = 0) }
            .onSizeChanged { bubbleWidth = it.width }
            .background(KpknSurface.inset, shape)
            .border(1.dp, KpknSurface.moduleBorder, shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(text = bodyWeekdayDayEs(point.date), style = KpknType.caption, color = WizardColors.textMuted)
        Text(
            text = point.value?.let { "${formatDecimalEs(it, 1)} $unit" } ?: "Sin pesaje este día",
            style = KpknType.bodyStrong,
            color = if (point.value != null) WizardColors.text else WizardColors.textFaint,
        )
        point.trend?.let { trend ->
            Text(
                text = "Tendencia ${formatDecimalEs(trend, 1)} $unit",
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
        }
    }
}

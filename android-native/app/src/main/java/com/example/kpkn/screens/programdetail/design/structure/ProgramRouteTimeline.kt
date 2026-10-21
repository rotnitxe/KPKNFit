package com.example.kpkn.screens.programdetail.design.structure

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.design.KpknEntranceMemory
import com.example.kpkn.ui.design.KpknType
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/*
 * Línea de tiempo de la ruta (P3c): los bloques como tramos de su color de fase, con su nombre debajo (hasta dos líneas, nunca cortado);
 * las semanas como puntos; las fechas clave como banderas con su tipo, y la competición en `danger`; «Hoy» como marca en tinta. Se dibuja
 * de izquierda a derecha la primera vez de cada proceso (600 ms; con movimiento reducido, de golpe) y, si no cabe, se desplaza con la
 * semana actual a la vista.
 */

/** Alto de la línea de tiempo: banderas y su etiqueta arriba, tramos, nombres de dos líneas y puntos de semana abajo. */
private val TimelineHeight = 158.dp

/** Margen a cada lado del primer y del último día. */
private val TimelineSidePad = 8.dp

/** Ancho mínimo y máximo de una semana: por debajo del mínimo la línea se desplaza en horizontal. */
private val MinWeekWidth = 22.dp
private val MaxWeekWidth = 44.dp

/** Mástil de las banderas y sus etiquetas: la primera fila y la segunda, si la primera está ocupada. */
private val FlagTop = 22.dp
private val FlagLabelRow1 = 20.dp
private val FlagLabelRow2 = 40.dp

/** Tramos de bloque, su nombre (hasta dos líneas) y los puntos de semana. */
private val BarTop = 66.dp
private val BarHeight = 22.dp
private val NameTop = 94.dp
private val DotsY = 148.dp

/** Ancho máximo de una etiqueta de bandera antes de cortarla con puntos suspensivos. */
private val FlagLabelMax = 160.dp

private val TimelineLabel = KpknType.caption.copy(color = WizardColors.textMuted)
private val TimelineBlockName = KpknType.caption.copy(color = WizardColors.textMuted, textAlign = TextAlign.Center)
private val TimelineInk = KpknType.caption.copy(color = WizardColors.text)
private val TimelineDanger = KpknType.caption.copy(color = WizardColors.danger)

/** La línea de tiempo del programa. Sin semanas no pinta nada (la hoja muestra su estado vacío). */
@Composable
internal fun ProgramRouteTimeline(
    model: ProgramRouteModel,
    modifier: Modifier = Modifier,
) {
    if (model.weeks.isEmpty()) return
    val reduced = wizardReducedMotion()
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val reveal = remember { Animatable(0f) }
    val flagPath = remember { Path() }
    val scroll = rememberScrollState()
    val flags = remember(model) { programRouteFlags(model) }
    LaunchedEffect(Unit) {
        // La primera vez de cada proceso se dibuja; después, el dibujo queda hecho.
        if (reduced || !KpknEntranceMemory.firstTime("programdetail.ruta", 0)) {
            reveal.snapTo(1f)
        } else {
            reveal.animateTo(1f, tween(durationMillis = 600))
        }
    }
    val currentIndex = model.weeks.indexOfFirst { week ->
        !model.today.isBefore(week.start) && !model.today.isAfter(week.end)
    }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val weekWidth = ((maxWidth - TimelineSidePad * 2) / model.weeks.size).coerceIn(MinWeekWidth, MaxWeekWidth)
        val canvasWidth = weekWidth * model.weeks.size + TimelineSidePad * 2
        LaunchedEffect(canvasWidth, currentIndex) {
            if (currentIndex >= 0 && canvasWidth > maxWidth) {
                val target: Dp = weekWidth * (currentIndex + 0.5f) + TimelineSidePad - maxWidth / 2
                scroll.scrollTo(with(density) { target.toPx() }.roundToInt().coerceAtLeast(0))
            }
        }
        Box(modifier = Modifier.fillMaxWidth().horizontalScroll(scroll)) {
            Canvas(
                modifier = Modifier
                    .width(canvasWidth)
                    .height(TimelineHeight)
                    .semantics { contentDescription = routeTimelineSummary(model) },
            ) {
                clipRect(right = size.width * reveal.value) {
                    drawProgramRoute(
                        model = model,
                        flags = flags,
                        measurer = measurer,
                        weekWidth = weekWidth,
                        flagPath = flagPath,
                    )
                }
            }
        }
    }
}

/** Dibuja la línea de tiempo entera en el lienzo de [DrawScope]; las posiciones salen de la anchura de semana. */
private fun DrawScope.drawProgramRoute(
    model: ProgramRouteModel,
    flags: List<ProgramRouteFlag>,
    measurer: TextMeasurer,
    weekWidth: Dp,
    flagPath: Path,
) {
    val week = weekWidth.toPx()
    val pad = TimelineSidePad.toPx()
    val gap = 2.dp.toPx()
    val barTop = BarTop.toPx()
    val barHeight = BarHeight.toPx()
    val radius = CornerRadius(8.dp.toPx())
    fun xOf(index: Int): Float = pad + index * week

    // Tramos de bloque, con su nombre debajo: centrado y en hasta dos líneas, sin cortar palabras.
    model.blocks.forEach { block ->
        val left = xOf(block.firstWeekIndex) + gap
        val right = xOf(block.firstWeekIndex + block.weeks) - gap
        drawRoundRect(
            color = block.color,
            topLeft = Offset(left, barTop),
            size = Size(right - left, barHeight),
            cornerRadius = radius,
        )
        drawWrappedLabel(measurer, block.name, TimelineBlockName, left, NameTop.toPx(), right - left)
    }

    // La semana de competición, con un contorno en `danger` sobre su columna.
    model.competitionWeekIndex?.let { index ->
        drawRoundRect(
            color = WizardColors.danger,
            topLeft = Offset(xOf(index) + gap, barTop - 4.dp.toPx()),
            size = Size(week - gap * 2, barHeight + 8.dp.toPx()),
            cornerRadius = radius,
            style = Stroke(width = 2.dp.toPx()),
        )
    }

    // Puntos de semana: la de competición, en `danger` y más grande.
    val dotsY = DotsY.toPx()
    model.weeks.forEach { item ->
        val x = xOf(item.index) + week / 2f
        if (item.isCompetition) {
            drawCircle(color = WizardColors.danger, radius = 4.dp.toPx(), center = Offset(x, dotsY))
        } else {
            drawCircle(color = WizardColors.textFaint, radius = 2.5.dp.toPx(), center = Offset(x, dotsY))
        }
    }

    // Banderas con su etiqueta. La etiqueta va a la derecha del mástil; si no cabe en la línea, a la izquierda; y si choca con la anterior,
    // baja a la segunda fila. Así «Vacaciones», al final, no se corta.
    val flagTop = FlagTop.toPx()
    val clearance = 6.dp.toPx()
    val labelMax = FlagLabelMax.toPx().toInt()
    var row1End = Float.NEGATIVE_INFINITY
    var row2End = Float.NEGATIVE_INFINITY
    flags.forEach { flag ->
        val x = xOf(flag.weekIndex) + week / 2f
        drawFlag(
            x = x,
            top = flagTop,
            color = if (flag.competition) WizardColors.danger else SymbolPalette.mente,
            path = flagPath,
        )
        val layout = measurer.measure(
            text = AnnotatedString(flag.label),
            style = if (flag.competition) TimelineDanger else TimelineLabel,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
            maxLines = 1,
            constraints = Constraints(maxWidth = labelMax),
        )
        val labelWidth = layout.size.width.toFloat()
        val rightStart = x + 12.dp.toPx()
        val start = if (rightStart + labelWidth <= size.width) rightStart else (x - clearance - labelWidth).coerceAtLeast(0f)
        val row = when {
            start > row1End + clearance -> 1
            start > row2End + clearance -> 2
            else -> 1
        }
        if (row == 1) row1End = start + labelWidth else row2End = start + labelWidth
        drawText(
            textLayoutResult = layout,
            topLeft = Offset(start, if (row == 1) FlagLabelRow1.toPx() else FlagLabelRow2.toPx()),
        )
    }

    // «Hoy»: una marca vertical en tinta, en el día exacto dentro de su semana, hasta la base de los tramos.
    val todayIndex = model.weeks.indexOfFirst { item ->
        !model.today.isBefore(item.start) && !model.today.isAfter(item.end)
    }
    if (todayIndex >= 0) {
        val days = ChronoUnit.DAYS.between(model.weeks[todayIndex].start, model.today).toFloat()
        val x = xOf(todayIndex) + week * (days / 7f)
        drawLine(
            color = WizardColors.text,
            start = Offset(x, 0f),
            end = Offset(x, barTop + barHeight + 6.dp.toPx()),
            strokeWidth = 1.5.dp.toPx(),
        )
        drawLabel(measurer, "Hoy", TimelineInk, x + 4.dp.toPx(), 0f, 48.dp.toPx())
    }
}

/** Un gallardete sobre su mástil. Reutiliza [path] para no crear un `Path` por cada bandera. */
private fun DrawScope.drawFlag(x: Float, top: Float, color: Color, path: Path) {
    drawLine(
        color = color,
        start = Offset(x, top),
        end = Offset(x, top + 18.dp.toPx()),
        strokeWidth = 1.5.dp.toPx(),
    )
    path.reset()
    path.moveTo(x, top)
    path.lineTo(x + 9.dp.toPx(), top + 4.5.dp.toPx())
    path.lineTo(x, top + 9.dp.toPx())
    path.close()
    drawPath(path = path, color = color)
}

/** Un nombre de bloque centrado bajo su tramo, en hasta dos líneas: si no cabe en una, parte por la palabra, nunca por dentro. */
private fun DrawScope.drawWrappedLabel(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    left: Float,
    top: Float,
    width: Float,
) {
    if (width <= 0f) return
    val layout = measurer.measure(
        text = AnnotatedString(text),
        style = style,
        overflow = TextOverflow.Ellipsis,
        softWrap = true,
        maxLines = 2,
        constraints = Constraints(maxWidth = width.toInt()),
    )
    drawText(textLayoutResult = layout, topLeft = Offset(left, top))
}

/** Una etiqueta en una sola línea, recortada con puntos suspensivos si no cabe en [maxWidth] píxeles. */
private fun DrawScope.drawLabel(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    x: Float,
    y: Float,
    maxWidth: Float,
) {
    if (maxWidth <= 0f) return
    val layout = measurer.measure(
        text = AnnotatedString(text),
        style = style,
        overflow = TextOverflow.Ellipsis,
        softWrap = false,
        maxLines = 1,
        constraints = Constraints(maxWidth = maxWidth.toInt()),
    )
    drawText(textLayoutResult = layout, topLeft = Offset(x, y))
}

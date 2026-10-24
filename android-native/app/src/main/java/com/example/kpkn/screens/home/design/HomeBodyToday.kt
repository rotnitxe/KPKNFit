package com.example.kpkn.screens.home.design

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.home.HomeBodyState
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.formatDecimalEs
import com.example.kpkn.screens.onboarding.design.formatSignedDecimalEs
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Clave de la memoria de la primera vez: el peso y la tendencia de «Cuerpo». */
private const val BODY_FIGURE_KEY = "home-body"

/** Fecha corta del pie de la tendencia: «14 sept». */
private val TrendDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es"))

/**
 * «Cuerpo» de hoy: un módulo con «Ver progreso» en la cabecera, el último peso con su cambio de 30 días, la tendencia a todo el ancho y los
 * datos pequeños que existan (% grasa, % músculo, IMC y FFMI; estos dos solo con altura real). Sin pesajes, la invitación a registrar. Cada
 * dato lleva a su detalle: [onOpen] recibe el destino de la pantalla de progreso.
 */
@Composable
fun HomeBodyToday(
    state: HomeBodyState,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = wizardReducedMotion()
    // Cifra y tendencia se animan solo la primera vez que se ven en el proceso.
    val firstSeen = remember { HomeFigureMemory.firstTime(BODY_FIGURE_KEY) }
    var appeared by remember { mutableStateOf(!firstSeen) }
    LaunchedEffect(Unit) { appeared = true }
    val figureSpec: AnimationSpec<Float> =
        if (reduced) snap() else tween(durationMillis = 900, easing = FastOutSlowInEasing)
    val weightShown by animateFloatAsState(
        targetValue = if (appeared) (state.trendKg ?: state.weightKg ?: 0.0).toFloat() else 0f,
        animationSpec = figureSpec,
        label = "home-weight",
    )
    val trendProgress by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = figureSpec,
        label = "home-trend",
    )
    val weight = state.weightKg
    val smallFigures = bodySmallFigures(state)

    KpknModule(modifier = modifier.fillMaxWidth().padding(horizontal = HomeModuleMargin)) {
        KpknModuleHeader(
            label = "Cuerpo",
            action = { KpknTextAction(text = "Ver progreso", onClick = { onOpen("body-progress") }) },
        )
        Spacer(Modifier.height(12.dp))
        if (weight != null) {
            BodyWeightBlock(state = state, weight = weight, shown = weightShown)
            Spacer(Modifier.height(12.dp))
            BodyTrendBlock(state = state, progress = trendProgress)
        } else {
            Text(
                text = "Registra tu peso para ver tu tendencia.",
                style = KpknType.body,
                color = WizardColors.text,
            )
            Spacer(Modifier.height(12.dp))
            KpknSecondaryPill(
                text = "Añadir pesaje",
                onClick = { onOpen("body-progress") },
                tag = "home-body-add",
            )
        }
        if (smallFigures.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            // A escala grande, las cifras pasan a dos filas de dos: a 130 % de letra, cuatro cifras de 18 sp no caben sin tocarse.
            if (LocalDensity.current.fontScale > HomeLargeTextScale) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    smallFigures.chunked(2).forEach { pair ->
                        Row(Modifier.fillMaxWidth()) {
                            pair.forEach { figure ->
                                BodySmallFigureCell(
                                    figure = figure,
                                    onClick = { onOpen(figure.target) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            // Un dato suelto ocupa su mitad y deja el hueco a la derecha, para que no se estire.
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth()) {
                    smallFigures.forEach { figure ->
                        BodySmallFigureCell(
                            figure = figure,
                            onClick = { onOpen(figure.target) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * La tendencia del peso (la cifra de Cuerpo; sin tendencia, el último peso) en Syne con «kg» y, a su derecha, el cambio de 30 días con su
 * flecha: en `done` si va hacia el objetivo, en `danger` si se aleja y en `textMuted` si no hay objetivo. Si no cabe a la derecha (a 130 %
 * de letra), el cambio baja debajo del peso. Con tendencia lleva encima la etiqueta «Tendencia» (una línea de `caption`), y el último
 * pesaje solo se dice en la descripción accesible.
 */
@Composable
private fun BodyWeightBlock(state: HomeBodyState, weight: Double, shown: Float) {
    // Con tendencia, el cambio y su color salen de la tendencia (la cifra de arriba); sin ella, del cambio de siempre.
    val change = state.trendChange30d ?: state.weightChange30d
    val toward = state.trendChangeTowardGoal ?: state.changeTowardGoal
    val tint = when {
        state.goalKg == null -> WizardColors.textMuted
        toward -> WizardColors.done
        else -> WizardColors.danger
    }
    val trend = state.trendKg
    val figure = if (trend != null) {
        "Tendencia del peso: ${formatDecimalEs(trend, 1)} kilos, último pesaje ${formatDecimalEs(weight, 1)} kilos"
    } else {
        "Peso: ${formatDecimalEs(weight, 1)} kilos"
    }
    val description = figure + change?.let { ", ${formatSignedDecimalEs(it, 1)} kilos en 30 días" }.orEmpty()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // La cifra cuenta: la semántica da el peso y el cambio finales.
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Column {
            if (trend != null) {
                Text(text = "Tendencia", style = KpknType.caption, color = WizardColors.textMuted)
            }
            HomeMetricNote(
                value = formatDecimalEs(shown.toDouble(), 1),
                unit = "kg",
                note = change?.let { "${formatSignedDecimalEs(it, 1)} kg en 30 días" },
                valueStyle = KpknType.metricXL,
                noteStyle = KpknType.caption,
                noteColor = tint,
                noteIcon = change?.let { if (it < 0) KpknLineIcons.TrendDown else KpknLineIcons.TrendUp },
            )
        }
    }
}

/**
 * La tendencia de 30 días (con al menos dos días con pesaje) a todo el ancho del módulo, y su pie: la fecha inicial y «hoy». Sin tendencia,
 * solo el texto del objetivo, si lo hay.
 */
@Composable
private fun BodyTrendBlock(state: HomeBodyState, progress: Float) {
    val goalText = bodyGoalText(state)
    if (state.trend.size >= 2) {
        BodyTrendChart(
            state = state,
            goalText = goalText,
            progress = progress,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(
                text = state.trend.first().date.format(TrendDateFormat),
                style = KpknType.caption,
                color = WizardColors.textFaint,
            )
            Spacer(Modifier.weight(1f))
            Text(text = "hoy", style = KpknType.caption, color = WizardColors.textFaint)
        }
    } else if (goalText != null) {
        Text(
            text = goalText,
            style = KpknType.caption,
            color = WizardColors.textMuted,
        )
    }
}

/**
 * Dibujo de la tendencia: línea de 1,8 dp en tinta, puntos de 3 dp y anillo en el último. Se dibuja de izquierda a derecha con
 * [progress]. El objetivo va en una línea discontinua `ok` al 60 %, con su texto encima a la derecha.
 */
@Composable
private fun BodyTrendChart(
    state: HomeBodyState,
    goalText: String?,
    progress: Float,
    modifier: Modifier,
) {
    val measurer = rememberTextMeasurer()
    val goalStyle = remember { WizardTypography.caption.copy(color = WizardColors.textMuted) }
    Canvas(modifier.semantics { contentDescription = "Tendencia de peso de los últimos 30 días" }) {
        val first = state.trend.first().date.toEpochDay()
        val end = (state.windowEnd ?: state.trend.last().date).toEpochDay()
        val span = (end - first).coerceAtLeast(1L).toFloat()
        val left = 4.dp.toPx()
        // A la derecha, el margen cubre el anillo del último punto (6 dp de radio más el trazo): si no, se corta en el borde.
        val right = size.width - 8.dp.toPx()
        // Arriba queda sitio para el texto del objetivo; abajo, un margen para el anillo del último punto.
        val top = 26.dp.toPx()
        val bottom = size.height - 10.dp.toPx()
        // La escala sale solo de la tendencia: si el objetivo cae lejos, no la aplasta y va solo como texto.
        val low = state.trend.minOf { it.kg }
        val high = state.trend.maxOf { it.kg }
        val pad = maxOf((high - low) * 0.15, 0.4)
        val yLow = low - pad
        val yHigh = high + pad
        val yRange = yHigh - yLow
        fun xOf(date: LocalDate): Float = left + (date.toEpochDay() - first).toFloat() / span * (right - left)
        fun yOf(kg: Double): Float = bottom - ((kg - yLow) / yRange).toFloat() * (bottom - top)

        val points = state.trend.map { Offset(xOf(it.date), yOf(it.kg)) }
        val line = Path()
        points.forEachIndexed { i, point ->
            if (i == 0) line.moveTo(point.x, point.y) else line.lineTo(point.x, point.y)
        }
        val revealWidth = size.width * progress
        val height = size.height
        withTransform({ clipRect(0f, 0f, revealWidth, height) }) {
            drawPath(line, WizardColors.text, style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            points.forEach { drawCircle(WizardColors.text, radius = 3.dp.toPx(), center = it) }
            drawCircle(
                WizardColors.text,
                radius = 6.dp.toPx(),
                center = points.last(),
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }
        state.goalKg?.let { goal ->
            val inside = goal in yLow..yHigh
            if (inside) {
                drawLine(
                    color = SymbolPalette.ok.copy(alpha = 0.6f),
                    start = Offset(left, yOf(goal)),
                    end = Offset(right, yOf(goal)),
                    strokeWidth = 1.2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                )
            }
            goalText?.let { text ->
                // Una línea que nunca pasa del ancho de la gráfica: un margen de fechas largo se corta con elipsis, no por la izquierda.
                val layout = measurer.measure(
                    text = text,
                    style = goalStyle,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    constraints = Constraints(maxWidth = (right - left).toInt().coerceAtLeast(0)),
                )
                // Con línea, el texto va encima de ella; sin línea (objetivo fuera de la escala), arriba a la derecha.
                val textTop = if (inside) yOf(goal) - layout.size.height - 3.dp.toPx() else 2.dp.toPx()
                drawText(layout, topLeft = Offset(right - layout.size.width, textTop))
            }
        }
    }
}

/** «Objetivo 72 kg» y, si el ritmo lo permite, «· ≈ abril» o, si el margen cambia de mes, «· abr – jun». Null sin objetivo de peso. */
private fun bodyGoalText(state: HomeBodyState): String? {
    val goal = state.goalKg ?: return null
    val weight = formatDecimalEs(goal, if (goal % 1.0 == 0.0) 0 else 1)
    val date = state.goalMonthRange ?: state.goalMonth?.let { "≈ $it" }
    return "Objetivo $weight kg" + (date?.let { " · $it" } ?: "")
}

/** Un dato pequeño de Cuerpo: su cifra con la unidad (si la tiene), su nombre y el destino de la pantalla de progreso. */
private class BodySmallFigure(val value: String, val unit: String?, val name: String, val target: String)

/** Los datos pequeños que existen: solo aparecen con valor real (el IMC y el FFMI, además, con altura real). */
private fun bodySmallFigures(state: HomeBodyState): List<BodySmallFigure> = buildList {
    state.bodyFatPct?.let { add(BodySmallFigure(formatDecimalEs(it, 1), "%", "Grasa", "fat")) }
    state.musclePct?.let { add(BodySmallFigure(formatDecimalEs(it, 1), "%", "Músculo", "muscle")) }
    state.bmi?.let { add(BodySmallFigure(formatDecimalEs(it, 1), null, "IMC", "imc")) }
    state.ffmi?.let { add(BodySmallFigure(formatDecimalEs(it, 1), null, "FFMI", "ffmi")) }
}

/**
 * Una celda de dato pequeño: la cifra con su unidad en `metricM` y el nombre debajo en `caption`. Toda la celda abre su destino.
 */
@Composable
private fun BodySmallFigureCell(figure: BodySmallFigure, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier
            .testTag("home-body-${figure.target}")
            .defaultMinSize(minHeight = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "${figure.name}, ${figure.value}" + figure.unit?.let { " $it" }.orEmpty() },
    ) {
        KpknMetric(value = figure.value, unit = figure.unit, style = KpknType.metricM)
        // Una línea: con la letra grande, el nombre se corta con elipsis antes que partirse.
        Text(
            text = figure.name,
            style = KpknType.caption,
            color = WizardColors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

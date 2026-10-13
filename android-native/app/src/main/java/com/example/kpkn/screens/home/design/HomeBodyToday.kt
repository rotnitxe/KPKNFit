package com.example.kpkn.screens.home.design

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.home.HomeBodyState
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.formatDecimalEs
import com.example.kpkn.screens.onboarding.design.formatSignedDecimalEs
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Clave de la memoria de la primera vez: el peso y la tendencia de «Cuerpo». */
private const val BODY_FIGURE_KEY = "home-body"

/** Fecha corta del pie de la tendencia: «14 sept». */
private val TrendDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es"))

/**
 * Cuerpo de hoy: el último peso con su cambio de 30 días, la tendencia y el objetivo, y los datos pequeños que existan (% grasa,
 * % músculo, IMC y FFMI; estos dos solo con altura real). Sin pesajes, la invitación a registrar. Cada dato lleva a su detalle:
 * [onOpen] recibe el destino de la pantalla de progreso.
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
        targetValue = if (appeared) (state.weightKg ?: 0.0).toFloat() else 0f,
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

    Column(modifier = modifier.fillMaxWidth()) {
        HomeSectionLabel(
            text = "CUERPO",
            action = {
                Box(
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .clickable(role = Role.Button, onClick = { onOpen("body-progress") })
                        .padding(start = 16.dp),
                    // Abajo a la derecha: el texto se alinea con la línea base de «CUERPO», y el área de toque sigue en 48 dp.
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    Text(text = "Ver progreso", style = WizardTypography.header, color = WizardColors.text)
                }
            },
        )
        Spacer(Modifier.height(14.dp))
        if (weight != null) {
            BodyWeightBlock(state = state, weight = weight, shown = weightShown)
            Spacer(Modifier.height(14.dp))
            BodyTrendBlock(state = state, progress = trendProgress)
        } else {
            Text(
                text = "Registra tu peso para ver tu tendencia.",
                style = WizardTypography.body,
                color = WizardColors.text,
                modifier = Modifier.padding(horizontal = WizardSpacing.gutter),
            )
            Spacer(Modifier.height(14.dp))
            HomeSecondaryPill(
                text = "Añadir pesaje",
                onClick = { onOpen("body-progress") },
                modifier = Modifier.padding(horizontal = WizardSpacing.gutter),
                tag = "home-body-add",
            )
        }
        if (smallFigures.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = WizardSpacing.gutter)) {
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

/** Último peso en Syne con «kg» y, a su lado, el cambio de 30 días: en `ok` si va hacia el objetivo, si no en `textMuted`. */
@Composable
private fun BodyWeightBlock(state: HomeBodyState, weight: Double, shown: Float) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = WizardSpacing.gutter),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = formatDecimalEs(shown.toDouble(), 1),
            style = HomeTypography.weightFigure,
            color = WizardColors.text,
            maxLines = 1,
            modifier = Modifier.semantics { contentDescription = "Peso: ${formatDecimalEs(weight, 1)} kilos" },
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "kg",
            style = WizardTypography.controlLabel,
            color = WizardColors.textMuted,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Spacer(Modifier.weight(1f))
        state.weightChange30d?.let { change ->
            // Separado de «kg» y en dos líneas: con la letra grande no se pisan ni se cortan.
            Spacer(Modifier.width(12.dp))
            Text(
                text = "${formatSignedDecimalEs(change, 1)} kg\nen 30 días",
                style = WizardTypography.bodySmall,
                color = if (state.changeTowardGoal) SymbolPalette.ok else WizardColors.textMuted,
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
    }
}

/**
 * La tendencia de 30 días (con al menos dos días con pesaje) y su pie: la fecha inicial y «hoy». Sin tendencia, solo el texto
 * del objetivo, si lo hay.
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
                .padding(horizontal = WizardSpacing.gutter)
                .height(96.dp),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = WizardSpacing.gutter)) {
            Text(
                text = state.trend.first().date.format(TrendDateFormat),
                style = WizardTypography.caption,
                color = WizardColors.textFaint,
            )
            Spacer(Modifier.weight(1f))
            Text(text = "hoy", style = WizardTypography.caption, color = WizardColors.textFaint)
        }
    } else if (goalText != null) {
        Text(
            text = goalText,
            style = WizardTypography.caption,
            color = WizardColors.textMuted,
            modifier = Modifier.padding(horizontal = WizardSpacing.gutter),
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
    val goalLayout = remember(goalText) {
        goalText?.let { measurer.measure(it, style = WizardTypography.caption.copy(color = WizardColors.textMuted)) }
    }
    Canvas(modifier.semantics { contentDescription = "Tendencia de peso de los últimos 30 días" }) {
        val first = state.trend.first().date.toEpochDay()
        val end = (state.windowEnd ?: state.trend.last().date).toEpochDay()
        val span = (end - first).coerceAtLeast(1L).toFloat()
        val left = 4.dp.toPx()
        val right = size.width - 4.dp.toPx()
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
            goalLayout?.let { layout ->
                // Con línea, el texto va encima de ella; sin línea (objetivo fuera de la escala), arriba a la derecha.
                val textTop = if (inside) yOf(goal) - layout.size.height - 3.dp.toPx() else 2.dp.toPx()
                drawText(layout, topLeft = Offset(right - layout.size.width, textTop))
            }
        }
    }
}

/** «Objetivo 72 kg» y, si el ritmo lo permite, «· ≈ abril». Null sin objetivo de peso. */
private fun bodyGoalText(state: HomeBodyState): String? {
    val goal = state.goalKg ?: return null
    val weight = formatDecimalEs(goal, if (goal % 1.0 == 0.0) 0 else 1)
    return "Objetivo $weight kg" + (state.goalMonth?.let { " · ≈ $it" } ?: "")
}

/** Un dato pequeño de Cuerpo: su cifra, su nombre y el destino de la pantalla de progreso. */
private class BodySmallFigure(val value: String, val name: String, val target: String)

/** Los datos pequeños que existen: solo aparecen con valor real (el IMC y el FFMI, además, con altura real). */
private fun bodySmallFigures(state: HomeBodyState): List<BodySmallFigure> = buildList {
    state.bodyFatPct?.let { add(BodySmallFigure(formatDecimalEs(it, 1), "% grasa", "fat")) }
    state.musclePct?.let { add(BodySmallFigure(formatDecimalEs(it, 1), "% músculo", "muscle")) }
    state.bmi?.let { add(BodySmallFigure(formatDecimalEs(it, 1), "IMC", "imc")) }
    state.ffmi?.let { add(BodySmallFigure(formatDecimalEs(it, 1), "FFMI", "ffmi")) }
}

/** Una celda de dato pequeño: cifra en Syne y nombre en `textMuted`; toda la celda abre su destino. */
@Composable
private fun BodySmallFigureCell(figure: BodySmallFigure, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier
            .testTag("home-body-${figure.target}")
            .defaultMinSize(minHeight = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "${figure.name}, ${figure.value}" },
    ) {
        Text(text = figure.value, style = WizardTypography.measure, color = WizardColors.text, maxLines = 1)
        // Dos líneas como máximo: con la letra grande «% músculo» baja a la segunda en lugar de cortarse.
        Text(text = figure.name, style = WizardTypography.caption, color = WizardColors.textMuted, maxLines = 2)
    }
}

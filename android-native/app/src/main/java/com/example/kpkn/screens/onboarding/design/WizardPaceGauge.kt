package com.example.kpkn.screens.onboarding.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.PlanDirection
import com.example.kpkn.domain.nutrition.PaceControl
import com.example.kpkn.domain.nutrition.PaceNotch
import com.example.kpkn.domain.nutrition.PaceZone
import com.example.kpkn.domain.nutrition.PlanTuning
import com.example.kpkn.domain.nutrition.PlanTuningRules
import com.example.kpkn.domain.nutrition.WizardPacePreset
import kotlin.math.abs
import kotlin.math.roundToInt

/** Semanas de la proyección que se dibuja bajo el ritmo. */
const val WIZARD_PACE_PROJECTION_WEEKS = 8

private val PaceTrackHeight = 28.dp
private val PaceThumbBox = 28.dp
private val PaceBarThickness = 6.dp

/**
 * Hasta dónde baja el control de ritmo antes de que empiecen las etiquetas de las muescas: las etiquetas (de
 * 48 dp, por la zona táctil) se meten 10 dp bajo el control, donde ya no queda pulgar, y quedan pegadas a la barra.
 */
private val NotchLabelsTop = 38.dp

/** Distancia (kg/sem) a la que una muesca cuenta como «la elegida». */
private const val NOTCH_MATCH_KG = 0.03

/** Muesca más cercana al ritmo actual, o null si ninguna está lo bastante cerca. */
fun nearestPaceNotch(control: PaceControl, magnitudeKgPerWeek: Double): PaceNotch? =
    control.notches
        .minByOrNull { abs(it.kgPerWeek - magnitudeKgPerWeek) }
        ?.takeIf { abs(it.kgPerWeek - magnitudeKgPerWeek) <= NOTCH_MATCH_KG }

/**
 * Ritmo semanal: el cambio de peso estimado en grande y con signo («−0,45 kg/sem»), un icono de dirección
 * (↓ ↑ =), una barra de zonas (sostenible → exigente → agresivo → extremo) con un marcador que se desliza
 * y cambia de color, las muescas Lento · Medio · Rápido y una proyección pequeña a [WIZARD_PACE_PROJECTION_WEEKS]
 * semanas. El ritmo SIEMPRE se deriva de las kcal reales frente al EER ([PlanTuning.weeklyChangeKg]): mover
 * los macros mueve el marcador y mover el marcador cambia las kcal.
 *
 * Sin estado. Sin [PlanTuning.paceControl] (mantenimiento) solo se ve la cifra y no hay barra ni
 * proyección; sin EER o peso no se compone nada (lo decide quien lo llama).
 */
@Composable
fun WizardPaceGauge(
    tuning: PlanTuning,
    reducedMotion: Boolean,
    editable: Boolean,
    onRateChange: (Double) -> Unit,
    onRateChangeFinished: () -> Unit,
    onPreset: (WizardPacePreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val change = tuning.weeklyChangeKg ?: return
    val zone = tuning.paceZone
    val control = tuning.paceControl
    val colorSpec: AnimationSpec<Color> = if (reducedMotion) snap() else tween(durationMillis = 350)
    val color by animateColorAsState(WizardNutritionPalette.zone(zone), colorSpec, label = "pace-color")
    val numberSpec: AnimationSpec<Float> =
        if (reducedMotion) snap() else tween(durationMillis = 450, easing = FastOutSlowInEasing)
    val shownChange by animateFloatAsState(change.toFloat(), numberSpec, label = "pace-rate")
    val loss = change < 0.0

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("setup-nutrition-pace"),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PaceDirectionBadge(zone = zone, loss = loss, color = color)
            Spacer(Modifier.width(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = formatSignedDecimalEs(shownChange.toDouble(), 2),
                    style = WizardTypography.controlValue,
                    color = color,
                    maxLines = 1,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "kg/sem",
                    style = WizardTypography.cardSubtitle,
                    color = WizardColors.textMuted,
                    modifier = Modifier.padding(bottom = 7.dp),
                    maxLines = 1,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = paceZoneLabel(zone),
                style = WizardTypography.note.copy(fontWeight = FontWeight.SemiBold),
                color = color,
                maxLines = 1,
            )
        }

        if (control != null && control.maxKgPerWeek > 0.0) {
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth()) {
                PaceSlider(
                    control = control,
                    magnitude = if ((change < 0.0) == (control.direction == PlanDirection.DEFICIT)) abs(change) else 0.0,
                    color = color,
                    editable = editable,
                    description = paceDescription(change, zone),
                    onRateChange = onRateChange,
                    onRateChangeFinished = onRateChangeFinished,
                )
                PaceNotchLabels(
                    control = control,
                    chosen = nearestPaceNotch(control, abs(change))?.preset,
                    enabled = editable,
                    onPreset = onPreset,
                    modifier = Modifier.padding(top = NotchLabelsTop),
                )
            }
        }

        if (control != null && zone != PaceZone.NONE) {
            Spacer(Modifier.height(4.dp))
            PaceProjection(
                changeKgPerWeek = change,
                weeks = WIZARD_PACE_PROJECTION_WEEKS,
                color = color,
                reducedMotion = reducedMotion,
            )
        }
    }
}

private fun paceDescription(change: Double, zone: PaceZone): String {
    val signed = formatSignedDecimalEs(change, 2)
    return "Ritmo estimado $signed kilos por semana, ${paceZoneLabel(zone).lowercase()}"
}

/** Círculo con el icono de dirección: ↓ pierdes, ↑ ganas, = mantienes. */
@Composable
private fun PaceDirectionBadge(zone: PaceZone, loss: Boolean, color: Color) {
    val icon = when {
        zone == PaceZone.NONE -> Icons.Filled.DragHandle
        loss -> Icons.Filled.ArrowDownward
        else -> Icons.Filled.ArrowUpward
    }
    Box(
        modifier = Modifier
            .size(40.dp)
            .border(1.dp, color.copy(alpha = 0.6f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaceSlider(
    control: PaceControl,
    magnitude: Double,
    color: Color,
    editable: Boolean,
    description: String,
    onRateChange: (Double) -> Unit,
    onRateChangeFinished: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val dragged by interaction.collectIsDraggedAsState()
    val maxKg = control.maxKgPerWeek.toFloat()
    Slider(
        value = magnitude.toFloat().coerceIn(0f, maxKg),
        onValueChange = { raw -> onRateChange(raw.toDouble()) },
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("setup-nutrition-pace-slider")
            .semantics {
                contentDescription = "Ritmo semanal"
                stateDescription = description
            },
        enabled = editable,
        valueRange = 0f..maxKg,
        onValueChangeFinished = onRateChangeFinished,
        interactionSource = interaction,
        thumb = { PaceThumb(color = color, active = pressed || dragged, enabled = editable) },
        track = { state ->
            val fraction = if (maxKg <= 0f) 0f else (state.value / maxKg).coerceIn(0f, 1f)
            PaceZoneTrack(control = control, fraction = fraction)
        },
    )
}

/** Marcador: punto blanco con un anillo del color de la zona actual. */
@Composable
private fun PaceThumb(color: Color, active: Boolean, enabled: Boolean) {
    val scale by animateFloatAsState(
        targetValue = if (active) 1.15f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "pace-thumb",
    )
    Canvas(Modifier.size(PaceThumbBox)) {
        val dot = 8.dp.toPx() * scale
        drawCircle(color = if (enabled) Color.White else WizardColors.textFaint, radius = dot)
        drawCircle(color = color, radius = dot, style = Stroke(width = 3.dp.toPx()))
    }
}

/**
 * Barra de zonas del recorrido [0, máximo]: cada zona con su color, apagada hasta el marcador y encendida
 * antes de él, y una marca fina en cada muesca. Solo se dibujan las zonas que caben en el rango.
 */
@Composable
private fun PaceZoneTrack(control: PaceControl, fraction: Float) {
    val loss = control.direction == PlanDirection.DEFICIT
    val max = control.maxKgPerWeek
    val edges = listOf(
        0.0,
        PlanTuningRules.sustainableKgPerWeek(loss),
        PlanTuningRules.aggressiveKgPerWeek(loss),
        PlanTuningRules.extremeKgPerWeek(loss),
        Double.MAX_VALUE,
    )
    val colors = listOf(
        WizardNutritionPalette.sustainable,
        WizardNutritionPalette.demanding,
        WizardNutritionPalette.aggressive,
        WizardNutritionPalette.extreme,
    )
    Canvas(Modifier.fillMaxWidth().height(PaceTrackHeight)) {
        val width = size.width
        val centerY = size.height / 2f
        val thickness = PaceBarThickness.toPx()
        val top = centerY - thickness / 2f
        val radius = thickness / 2f
        val thumbX = width * fraction
        val bar = Path().apply {
            addRoundRect(RoundRect(0f, top, width, top + thickness, CornerRadius(radius, radius)))
        }
        clipPath(bar) {
            for (zone in colors.indices) {
                val from = edges[zone].coerceAtMost(max)
                val to = edges[zone + 1].coerceAtMost(max)
                if (to <= from) continue
                val x0 = (from / max).toFloat() * width
                val x1 = (to / max).toFloat() * width
                drawRect(
                    color = colors[zone].copy(alpha = 0.28f),
                    topLeft = Offset(x0, top),
                    size = Size(x1 - x0, thickness),
                )
                val litEnd = minOf(x1, thumbX)
                if (litEnd > x0) {
                    drawRect(color = colors[zone], topLeft = Offset(x0, top), size = Size(litEnd - x0, thickness))
                }
            }
        }
        control.notches.forEach { notch ->
            val x = (notch.kgPerWeek / max).toFloat() * width
            drawLine(
                color = Color.White.copy(alpha = 0.5f),
                start = Offset(x, centerY - thickness / 2f - 4.dp.toPx()),
                end = Offset(x, centerY - thickness / 2f - 1.dp.toPx()),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

/** Nombres de las muescas colocados bajo su posición en la barra; tocarlos lleva el ritmo a ese preset. */
@Composable
private fun PaceNotchLabels(
    control: PaceControl,
    chosen: WizardPacePreset?,
    enabled: Boolean,
    onPreset: (WizardPacePreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (control.notches.isEmpty()) return
    val inset = with(LocalDensity.current) { (PaceThumbBox / 2).toPx() }
    Layout(
        content = {
            control.notches.forEach { notch ->
                PaceNotchLabel(
                    preset = notch.preset,
                    chosen = notch.preset == chosen,
                    enabled = enabled,
                    onClick = { onPreset(notch.preset) },
                )
            }
        },
        modifier = modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(constraints.maxWidth, height) {
            val trackWidth = constraints.maxWidth - 2f * inset
            placeables.forEachIndexed { index, placeable ->
                val center = inset + (control.notches[index].kgPerWeek / control.maxKgPerWeek).toFloat() * trackWidth
                val x = (center - placeable.width / 2f).roundToInt().coerceIn(0, constraints.maxWidth - placeable.width)
                placeable.placeRelative(x, 0)
            }
        }
    }
}

@Composable
private fun PaceNotchLabel(preset: WizardPacePreset, chosen: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val label = paceNotchLabel(preset)
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = WizardSpacing.touchTarget, minHeight = WizardSpacing.touchTarget)
            .clip(WizardShapes.pill)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Ritmo $label", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = WizardTypography.note.copy(fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal),
            color = if (chosen) WizardColors.text else WizardColors.textMuted,
            maxLines = 1,
        )
    }
}

/**
 * Proyección pequeña a [weeks] semanas: una fila de puntos que baja o sube con la pendiente del ritmo (más
 * empinada cuanto más cerca del umbral extremo) y el cambio total en una línea. Nunca un párrafo.
 */
@Composable
private fun PaceProjection(
    changeKgPerWeek: Double,
    weeks: Int,
    color: Color,
    reducedMotion: Boolean,
) {
    val total = changeKgPerWeek * weeks
    val loss = total < 0.0
    val extremeTotal = PlanTuningRules.extremeKgPerWeek(loss) * weeks
    val slopeTarget = (abs(total) / extremeTotal).toFloat().coerceIn(0.12f, 1f)
    val spec: AnimationSpec<Float> =
        if (reducedMotion) snap() else tween(durationMillis = 800, easing = FastOutSlowInEasing)
    val slope by animateFloatAsState(slopeTarget, spec, label = "projection-slope")
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val reveal by animateFloatAsState(if (appeared) 1f else 0f, spec, label = "projection-reveal")
    val text = buildAnnotatedString {
        append("en $weeks semanas ≈ ")
        withStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold)) {
            append(formatSignedDecimalEs(total, 1))
            append(" kg")
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = "En $weeks semanas, ${formatSignedDecimalEs(total, 1)} kilos" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.width(112.dp).height(34.dp)) {
            val padX = 6.dp.toPx()
            val padY = 7.dp.toPx()
            val usableW = size.width - 2f * padX
            val usableH = size.height - 2f * padY
            for (week in 0 until weeks) {
                val t = week / (weeks - 1).toFloat()
                val x = padX + t * usableW
                val travelled = t * slope * usableH
                val y = if (loss) padY + travelled else padY + usableH - travelled
                val visible = (reveal * weeks - week).coerceIn(0f, 1f)
                val last = week == weeks - 1
                drawCircle(
                    color = color.copy(alpha = visible * (if (last) 1f else 0.5f)),
                    radius = (if (last) 4.dp else 2.5.dp).toPx() * (0.5f + 0.5f * visible),
                    center = Offset(x, y),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(text = text, style = WizardTypography.bodySmall, color = WizardColors.textMuted, maxLines = 1)
    }
}

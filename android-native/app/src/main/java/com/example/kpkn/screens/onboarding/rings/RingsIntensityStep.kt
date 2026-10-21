package com.example.kpkn.screens.onboarding.rings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.InitialRecoveryIntensity
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.screens.onboarding.SetupWizardState
import com.example.kpkn.screens.onboarding.SetupWizardViewModel
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.entreno.IntensityFlameArt
import com.example.kpkn.screens.onboarding.design.entreno.IntensityFlameShape
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.entreno.flameTongue
import com.example.kpkn.screens.onboarding.design.entreno.rememberEntrenoForeground
import com.example.kpkn.screens.onboarding.design.entreno.rememberSymbolClock
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Un escalón de la intensidad: su valor de dominio y su etiqueta de pantalla. */
private class IntensityChoice(val level: InitialRecoveryIntensity, val label: String)

/** Los cuatro escalones, de menos a más: la llama crece con el deslizador y, en «Muy dura», salen chispas. */
private val IntensityScale = listOf(
    IntensityChoice(InitialRecoveryIntensity.EASY, "Suave"),
    IntensityChoice(InitialRecoveryIntensity.MODERATE, "Moderada"),
    IntensityChoice(InitialRecoveryIntensity.HARD, "Dura"),
    IntensityChoice(InitialRecoveryIntensity.VERY_HARD, "Muy dura"),
)

/** Alto de la llama: a todo el ancho de la página. */
private val FlameHeight = 160.dp

/** Alto de la línea del nivel elegido, o de la pista «Desliza o toca un nivel.» sin respuesta. */
private val LevelHeight = 44.dp

/** Grosor de la pista y radio del pulgar (28 dp de diámetro). */
private val TrackThickness = 6.dp
private val ThumbRadius = 14.dp

/** Salto del pulgar a su parada (al soltar o al tocar), y encendido de la llama en el primer toque. */
private const val SETTLE_MILLIS = 160
private const val LIGHT_MILLIS = 260

/** Índice del escalón de [level]. */
private fun stopIndexOf(level: InitialRecoveryIntensity): Int =
    IntensityScale.indexOfFirst { it.level == level }.coerceAtLeast(0)

/** Posición 0..1 en la pista de la coordenada [x]: el pulgar no se sale de los extremos. */
private fun trackFraction(x: Float, width: Float, thumbPx: Float): Float {
    val span = (width - 2f * thumbPx).coerceAtLeast(1f)
    return ((x - thumbPx) / span).coerceIn(0f, 1f)
}

/**
 * Paso `RINGS_INTENSITY`: «¿Cómo sentiste la intensidad?». Un deslizador de cuatro paradas con una llama que se transforma con él.
 * Escribe el dato con `setStepChoice` (la proyección de `intensityLevel`).
 */
@Composable
internal fun RingsIntensityStep(state: SetupWizardState, vm: SetupWizardViewModel, modifier: Modifier = Modifier) {
    RingsIntensityContent(
        selected = state.draft.ringsAnswers?.intensityLevel,
        onSelect = { level -> vm.setStepChoice(SetupStepId.RINGS_INTENSITY, level.name) },
        modifier = modifier,
    )
}

/**
 * Cuerpo del paso sin estado propio (lo usa también la hoja de render): la llama, el nivel elegido, el deslizador y las etiquetas
 * alineadas con sus paradas. Sin respuesta, la llama es una brasa apagada y el pulgar no aparece.
 */
@Composable
internal fun RingsIntensityContent(
    selected: InitialRecoveryIntensity?,
    onSelect: (InitialRecoveryIntensity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = wizardReducedMotion()
    val foreground = rememberEntrenoForeground()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val thumbPx = with(density) { ThumbRadius.toPx() }
    val trackPx = with(density) { TrackThickness.toPx() }
    val onSelectNow by rememberUpdatedState(onSelect)
    val selectedNow by rememberUpdatedState(selected)
    val answered = selected != null
    // Posición del arrastre en curso (0..1): la llama sigue al dedo sin tocar el borrador.
    var dragT by remember { mutableStateOf<Float?>(null) }
    // Posición asentada del pulgar: salta a su parada en 160 ms.
    val thumb = remember { Animatable(selected?.let { IntensityFlameShape.stopT(stopIndexOf(it)) } ?: 0f) }
    // Encendido de la llama: apagada (brasa) sin respuesta; el primer toque la enciende.
    val lit = remember { Animatable(if (answered) 1f else 0f) }
    val draggingT = dragT
    val clock = rememberSymbolClock(active = !reduced && foreground && (answered || draggingT != null))
    val path = remember { Path() }
    val litTarget = if (answered || draggingT != null) 1f else 0f
    LaunchedEffect(litTarget) {
        if (reduced) lit.snapTo(litTarget) else lit.animateTo(litTarget, tween(LIGHT_MILLIS))
    }
    // Una respuesta que llega de fuera (un borrador cargado, por ejemplo) lleva el pulgar a su parada.
    LaunchedEffect(selected) {
        val target = selected?.let { IntensityFlameShape.stopT(stopIndexOf(it)) } ?: return@LaunchedEffect
        if (dragT == null && abs(thumb.value - target) > 0.001f) {
            if (reduced) thumb.snapTo(target) else thumb.animateTo(target, tween(SETTLE_MILLIS))
        }
    }

    // Lleva el pulgar a [target] (en 160 ms, o de golpe con movimiento reducido).
    fun settleTo(target: Float) {
        scope.launch { if (reduced) thumb.snapTo(target) else thumb.animateTo(target, tween(SETTLE_MILLIS)) }
    }

    // Tocar una parada (etiqueta, pista o acción de accesibilidad): el pulgar va a ella y se guarda ese nivel.
    fun choose(index: Int) {
        settleTo(IntensityFlameShape.stopT(index))
        val level = IntensityScale[index].level
        if (selectedNow != level) onSelectNow(level)
    }

    // Al soltar: el pulgar salta a la parada más cercana y se guarda ese nivel.
    fun release(end: Float) {
        val index = IntensityFlameShape.nearestStop(end)
        val target = IntensityFlameShape.stopT(index)
        scope.launch {
            thumb.snapTo(end)
            dragT = null
            if (reduced) thumb.snapTo(target) else thumb.animateTo(target, tween(SETTLE_MILLIS))
        }
        val level = IntensityScale[index].level
        if (selectedNow != level) onSelectNow(level)
    }

    // Nivel que se muestra: el más cercano mientras se arrastra, o el elegido.
    val shownIndex: Int? = when {
        draggingT != null -> IntensityFlameShape.nearestStop(draggingT)
        selected != null -> stopIndexOf(selected)
        else -> null
    }

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        IntensityFlameArt(
            level = { dragT ?: thumb.value },
            lit = { lit.value },
            clock = clock,
            reducedMotion = reduced,
            modifier = Modifier
                .fillMaxWidth()
                .height(FlameHeight)
                .testTag("rings-intensity-flame")
                .semantics { stateDescription = if (lit.value > 0.5f) "Encendida" else "Apagada" },
        )
        Box(modifier = Modifier.fillMaxWidth().height(LevelHeight), contentAlignment = Alignment.Center) {
            if (shownIndex == null) {
                Text(text = "Desliza o toca un nivel.", style = WizardTypography.note, color = WizardColors.textMuted)
            } else {
                Text(text = IntensityScale[shownIndex].label, style = WizardTypography.controlValue, color = WizardColors.text)
            }
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(WizardSpacing.touchTarget)
                .testTag("rings-intensity-slider")
                .semantics {
                    contentDescription = "Intensidad"
                    stateDescription = selectedNow?.let { IntensityScale[stopIndexOf(it)].label } ?: "Sin elegir"
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = (selectedNow?.let { stopIndexOf(it) } ?: 0).toFloat(),
                        range = 0f..(IntensityFlameShape.STOP_COUNT - 1).toFloat(),
                        steps = IntensityFlameShape.STOP_COUNT - 2,
                    )
                    setProgress { target ->
                        choose(target.roundToInt().coerceIn(0, IntensityFlameShape.STOP_COUNT - 1))
                        true
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        choose(IntensityFlameShape.nearestStop(trackFraction(offset.x, size.width.toFloat(), thumbPx)))
                    }
                }
                .pointerInput(Unit) {
                    var latest = 0f
                    var lastStop = -1
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            latest = trackFraction(offset.x, size.width.toFloat(), thumbPx)
                            lastStop = IntensityFlameShape.nearestStop(latest)
                            dragT = latest
                        },
                        onDragEnd = { release(latest) },
                        onDragCancel = { dragT = null },
                        onHorizontalDrag = { change, _ ->
                            latest = trackFraction(change.position.x, size.width.toFloat(), thumbPx)
                            dragT = latest
                            // Un toque háptico al cruzar cada parada.
                            val stop = IntensityFlameShape.nearestStop(latest)
                            if (stop != lastStop) {
                                lastStop = stop
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        },
                    )
                },
        ) {
            drawIntensityTrack(
                shown = dragT ?: thumb.value,
                answered = selectedNow != null || dragT != null,
                thumbPx = thumbPx,
                trackPx = trackPx,
                path = path,
            )
        }
        // Las etiquetas van cada una centrada en su parada (en los extremos, pegadas al borde).
        Layout(
            content = {
                IntensityScale.forEachIndexed { index, choice ->
                    val emphasized = shownIndex == index
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = WizardSpacing.touchTarget, minHeight = WizardSpacing.touchTarget)
                            .testTag("rings-intensity-${choice.level.name}")
                            .selectable(
                                selected = selectedNow == choice.level,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.RadioButton,
                                onClick = { choose(index) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = choice.label,
                            style = if (emphasized) {
                                WizardTypography.caption.copy(fontWeight = FontWeight.SemiBold)
                            } else {
                                WizardTypography.caption
                            },
                            color = if (emphasized) WizardColors.text else WizardColors.textMuted,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().selectableGroup(),
        ) { measurables, constraints ->
            val width = constraints.maxWidth
            val placeables = measurables.map { it.measure(Constraints(maxWidth = width)) }
            val height = placeables.maxOfOrNull { it.height } ?: 0
            layout(width, height) {
                placeables.forEachIndexed { index, placeable ->
                    val center = thumbPx + (width - 2f * thumbPx) * index / (IntensityFlameShape.STOP_COUNT - 1)
                    val x = (center - placeable.width / 2f).roundToInt()
                        .coerceIn(0, (width - placeable.width).coerceAtLeast(0))
                    placeable.placeRelative(x, 0)
                }
            }
        }
    }
}

/**
 * Pista y pulgar: la pista vacía; con respuesta, el relleno con el degradado de la intensidad (de energía a músculo según la posición)
 * y el pulgar de tinta con una brasa dentro.
 */
private fun DrawScope.drawIntensityTrack(
    shown: Float,
    answered: Boolean,
    thumbPx: Float,
    trackPx: Float,
    path: Path,
) {
    val mid = size.height / 2f
    val left = thumbPx
    val right = size.width - thumbPx
    val x = left + (right - left) * shown.coerceIn(0f, 1f)
    val top = mid - trackPx / 2f
    val corner = CornerRadius(trackPx / 2f)
    drawRoundRect(
        color = WizardColors.divider,
        topLeft = Offset(left, top),
        size = Size(right - left, trackPx),
        cornerRadius = corner,
    )
    if (!answered) return
    drawRoundRect(
        brush = Brush.horizontalGradient(listOf(SymbolPalette.energia, SymbolPalette.musculo), startX = left, endX = right),
        topLeft = Offset(left, top),
        size = Size((x - left).coerceAtLeast(0f), trackPx),
        cornerRadius = corner,
    )
    drawCircle(color = WizardColors.text, radius = thumbPx, center = Offset(x, mid))
    path.flameTongue(x, mid + thumbPx * 0.42f, thumbPx * 0.34f, thumbPx * 0.95f, 0f)
    drawPath(path, SymbolPalette.musculo)
    path.flameTongue(x, mid + thumbPx * 0.42f, thumbPx * 0.16f, thumbPx * 0.5f, 0f)
    drawPath(path, SymbolPalette.energia)
}

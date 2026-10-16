package com.example.kpkn.screens.workout.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.workout.EffortScale
import com.example.kpkn.screens.workout.effortCenterPx
import com.example.kpkn.screens.workout.effortFireLevel
import com.example.kpkn.screens.workout.effortIndexAtX
import com.example.kpkn.screens.workout.effortLabelCandidates
import com.example.kpkn.screens.workout.effortParticleCount
import com.example.kpkn.screens.workout.effortParticlePeriodSeconds
import com.example.kpkn.screens.workout.effortSpokenDescription
import com.example.kpkn.screens.workout.effortThumbFraction
import com.example.kpkn.screens.workout.effortValueLabel
import com.example.kpkn.screens.workout.effortWord
import com.example.kpkn.screens.workout.placeEffortLabels
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/** Etiqueta de pruebas de la zona táctil del slider de esfuerzo (la que lleva la semántica de slider). */
internal const val EffortSliderTag = "effort_fire_slider"

/** Aviso que sale bajo el slider cuando se pulsa el ✓ sin haber marcado el esfuerzo. */
internal const val EffortRequiredHint = "Marca tu esfuerzo para registrar la serie"

private val TrackHeight = 46.dp
private val TouchHeight = 48.dp
private val ThumbSize = 36.dp

/** Margen del centro del pulgar al borde de la pista: 5 dp de holgura más el radio del pulgar (18 dp). */
private val ThumbInset = 23.dp

/** Alto de la marca del plan: 5 dp por encima y por debajo de la pista de 46 dp. */
private val PlanMarkHeight = 56.dp

// Los seis degradados del prototipo (Main): de gris frío a ascua y fuego. Cada uno, con las posiciones de sus colores.
private val FireLevelStops: List<List<Pair<Float, Color>>> = listOf(
    listOf(0f to Color(0xFF3A3A3A), 1f to Color(0xFF5A5753)),
    listOf(0f to Color(0xFF3A3A3A), 1f to Color(0xFF8A847B)),
    listOf(0f to Color(0xFF4A4640), 1f to Color(0xFFCFC8BC)),
    listOf(0f to Color(0xFF5B4A3A), 1f to Color(0xFFF7CF73)),
    listOf(0f to Color(0xFF6B3E2C), 0.7f to Color(0xFFF49A6E), 1f to Color(0xFFF7CF73)),
    listOf(
        0f to Color(0xFF7A2E1F),
        0.55f to Color(0xFFF06A3E),
        0.85f to Color(0xFFF7CF73),
        1f to Color(0xFFFFF1C8),
    ),
)

private val GradientSamples = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)

private val ParticleColors = arrayOf(Color(0xFFF7CF73), Color(0xFFF49A6E), Color(0xFFFFE2B0), Color(0xFFF06A3E))

private fun colorAt(stops: List<Pair<Float, Color>>, position: Float): Color {
    if (position <= stops.first().first) return stops.first().second
    for (i in 1 until stops.size) {
        val (endPosition, endColor) = stops[i]
        if (position <= endPosition) {
            val (startPosition, startColor) = stops[i - 1]
            val fraction = if (endPosition == startPosition) 1f else (position - startPosition) / (endPosition - startPosition)
            return lerp(startColor, endColor, fraction)
        }
    }
    return stops.last().second
}

/** Los cinco colores del degradado de un nivel de fuego (0 a 5): los de los dos niveles vecinos del prototipo, interpolados. */
internal fun effortFireColors(level: Float): List<Color> {
    val clamped = level.coerceIn(0f, 5f)
    val low = clamped.toInt().coerceAtMost(4)
    val fraction = clamped - low
    return GradientSamples.map { position ->
        lerp(colorAt(FireLevelStops[low], position), colorAt(FireLevelStops[low + 1], position), fraction)
    }
}

/**
 * El slider de esfuerzo de la tarjeta de la serie (LV8, `Main.dc.html`): una pista de 46 dp con relleno de fuego que crece con el esfuerzo,
 * partículas que suben dentro del relleno y un pulgar de tinta. Los valores son los de [scale] (los de siempre de la app). Empieza en el
 * MÍNIMO y sin marcar ([selectedIndex] nulo): el pulgar espera a la izquierda, sin fuego. El objetivo del plan es una marca tenue sobre la
 * pista. Se arrastra con ajuste a cada valor y también se toca un punto de la pista.
 *
 * [hint] ocupa el sitio de las etiquetas, bajo la pista, sin cambiar la altura. [wobbleKey] sube cada vez que hay que balancear la pista.
 * [animate] en falso (la tarjeta no es la activa o la app está en segundo plano) o «reducir movimiento»: sin partículas, el degradado sí.
 * [forcedFireSeconds] fija el fotograma del fuego (hojas de render y pruebas).
 */
@Composable
internal fun EffortFireSlider(
    scale: EffortScale,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    hint: String? = null,
    wobbleKey: Int = 0,
    animate: Boolean = true,
    hapticsEnabled: Boolean = true,
    forcedFireSeconds: Float? = null,
) {
    val items = scale.items
    if (items.isEmpty()) return
    val count = items.size
    val reduced = wizardReducedMotion()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentHaptics by rememberUpdatedState(hapticsEnabled)

    val chosen = selectedIndex?.coerceIn(0, count - 1)
    val selectedItem = chosen?.let { items[it] }
    // Sin marcar, el pulgar espera en el mínimo (la primera marca), sin relleno.
    val thumbFraction by animateFloatAsState(
        targetValue = effortThumbFraction(chosen ?: 0, count),
        animationSpec = if (reduced) snap() else tween(durationMillis = 140, easing = FastOutSlowInEasing),
        label = "effortThumb",
    )
    val level = selectedItem?.let { effortFireLevel(it) } ?: 0f
    val fireStops = remember(level) {
        val colors = effortFireColors(level)
        Array(GradientSamples.size) { i -> GradientSamples[i] to colors[i] }
    }

    val particlesRunning = animate && !reduced && enabled && level >= 2f
    val clock: State<Float>? = if (particlesRunning && forcedFireSeconds == null) {
        rememberInfiniteTransition(label = "effortFire").animateFloat(
            initialValue = 0f,
            targetValue = 600f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 600_000, easing = LinearEasing)),
            label = "effortFireClock",
        )
    } else {
        null
    }
    val drawParticles = level >= 2f && (forcedFireSeconds != null || particlesRunning)

    val wobble = remember { Animatable(0f) }
    LaunchedEffect(wobbleKey) {
        if (wobbleKey > 0 && !reduced) {
            val amplitude = with(density) { 6.dp.toPx() }
            listOf(-1f, 1f, -0.7f, 0.7f, -0.35f, 0f).forEach { factor ->
                wobble.animateTo(factor * amplitude, tween(durationMillis = 55, easing = LinearEasing))
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Cabecera: «ESFUERZO» a la izquierda y, a la derecha, el valor con su palabra («RIR 2 · exigente»).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "ESFUERZO",
                style = KpknType.eyebrow,
                color = WizardColors.textMuted,
                maxLines = 1,
            )
            Spacer(Modifier.width(8.dp))
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selectedItem == null) {
                    Text(
                        text = "Sin marcar",
                        style = KpknType.action,
                        color = WizardColors.textFaint,
                        maxLines = 1,
                    )
                } else {
                    Text(
                        text = effortValueLabel(selectedItem),
                        style = KpknType.action.copy(fontFeatureSettings = "'tnum' 1"),
                        color = WizardColors.text,
                        maxLines = 1,
                    )
                    effortWord(selectedItem)?.let { word ->
                        Text(
                            text = " · $word",
                            style = KpknType.action.copy(fontWeight = FontWeight.Normal),
                            color = WizardColors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))

        // Zona táctil de 48 dp con la pista de 46 dp, la marca del plan, el relleno de fuego y el pulgar.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(TouchHeight)
                .graphicsLayer { translationX = wobble.value }
                .testTag(EffortSliderTag)
                .semantics {
                    contentDescription = effortSpokenDescription(selectedItem)
                    stateDescription = selectedItem?.let { effortValueLabel(it) } ?: "Sin marcar"
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = (chosen ?: 0).toFloat(),
                        range = 0f..(count - 1).toFloat(),
                        steps = (count - 2).coerceAtLeast(0),
                    )
                    if (enabled) {
                        setProgress { target ->
                            currentOnSelect(target.roundToInt().coerceIn(0, count - 1))
                            true
                        }
                    }
                }
                .pointerInput(count, enabled) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val width = size.width.toFloat()
                        val inset = ThumbInset.toPx()
                        var last = effortIndexAtX(down.position.x, width, inset, count)
                        down.consume()
                        currentOnSelect(last)
                        if (currentHaptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        drag(down.id) { change ->
                            change.consume()
                            val index = effortIndexAtX(change.position.x, width, inset, count)
                            if (index != last) {
                                last = index
                                currentOnSelect(index)
                                if (currentHaptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    }
                },
        ) {
            val widthPx = with(density) { maxWidth.toPx() }
            val insetPx = with(density) { ThumbInset.toPx() }
            val centerPx = insetPx + thumbFraction * (widthPx - 2f * insetPx)
            val fillWidthPx = (centerPx + insetPx).coerceIn(0f, widthPx)

            // Pista: #1A1A1A con sombra interior.
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(TrackHeight)
                    .clip(KpknShape.pill)
                    .background(KpknSurface.inset)
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent),
                                startY = 0f,
                                endY = 8.dp.toPx(),
                            ),
                        )
                    },
            ) {
                if (chosen != null) {
                    // Relleno: desde la izquierda hasta 5 dp más allá del pulgar, con el degradado del nivel.
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(with(density) { fillWidthPx.toDp() })
                            .clip(KpknShape.pill)
                            .background(
                                Brush.horizontalGradient(
                                    *fireStops,
                                    startX = 0f,
                                    endX = fillWidthPx.coerceAtLeast(1f),
                                ),
                            )
                            .drawWithContent {
                                drawContent()
                                if (drawParticles) {
                                    val seconds = forcedFireSeconds ?: clock?.value ?: 0f
                                    drawFire(level, seconds, size.width, size.height)
                                }
                            },
                    )
                }
            }

            // Marca tenue del objetivo del plan: más alta que la pista, sin preseleccionarlo.
            scale.planIndex?.let { plan ->
                val planCenterPx = effortCenterPx(plan, count, widthPx, insetPx)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset { IntOffset((planCenterPx - 1.dp.toPx()).roundToInt(), 0) }
                        .size(width = 2.dp, height = PlanMarkHeight)
                        .background(WizardColors.text.copy(alpha = 0.35f), RoundedCornerShape(1.dp)),
                )
            }

            // Halo del pulgar en el fallo: un brillo de ascua que late.
            if (drawParticles && level >= 4.5f) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .drawBehind {
                            val seconds = forcedFireSeconds ?: clock?.value ?: 0f
                            val pulse = 0.5f + 0.5f * sin((2.0 * PI * seconds / 1.2).toFloat())
                            val radius = 30.dp.toPx()
                            val center = Offset(centerPx, size.height / 2f)
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color(0xFFF49A6E).copy(alpha = 0.20f + 0.25f * pulse), Color.Transparent),
                                    center = center,
                                    radius = radius,
                                ),
                                radius = radius,
                                center = center,
                            )
                        },
                )
            }

            // Pulgar de tinta de 36 dp.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset((centerPx - ThumbSize.toPx() / 2f).roundToInt(), 0) }
                    .size(ThumbSize)
                    .shadow(4.dp, CircleShape, clip = false)
                    .background(WizardColors.text, CircleShape),
            )
        }
        Spacer(Modifier.height(4.dp))

        // Etiquetas de la escala o, si hay que avisar, el aviso: las dos en el mismo alto reservado. El alto son las dos líneas del aviso (16 sp cada
        // una, convertidas igual que el texto: con la letra escalada la conversión no es lineal, así que no vale convertir 32 sp de golpe) y 2 dp más.
        val slotHeight = with(density) { 16.sp.toDp() } * 2 + 2.dp
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(slotHeight),
        ) {
            Crossfade(
                targetState = hint,
                animationSpec = tween(durationMillis = if (reduced) 0 else 160),
                label = "effortHint",
            ) { shownHint ->
                if (shownHint != null) {
                    Text(
                        text = shownHint,
                        style = KpknType.caption.copy(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
                        color = WizardColors.text,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    EffortScaleLabels(scale = scale, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** El fuego solo corre con la tarjeta a la vista ([isActive]) y la app en primer plano: ningún bucle corre fuera de pantalla. */
@Composable
internal fun rememberEffortAnimationAllowed(isActive: Boolean): Boolean {
    val owner = LocalLifecycleOwner.current
    val state by owner.lifecycle.currentStateFlow.collectAsState()
    return isActive && state.isAtLeast(Lifecycle.State.RESUMED)
}

/** Las etiquetas bajo la pista: el mínimo, el plan, el máximo y «Fallo», cada una bajo su marca y sin pisarse. */
@Composable
private fun EffortScaleLabels(scale: EffortScale, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val plain = KpknType.navLabel.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium)
    val strong = plain.copy(fontWeight = FontWeight.SemiBold)
    val candidates = remember(scale) { effortLabelCandidates(scale) }
    BoxWithConstraints(modifier = modifier) {
        val trackPx = with(density) { maxWidth.toPx() }
        val insetPx = with(density) { ThumbInset.toPx() }
        val gapPx = with(density) { 6.dp.toPx() }
        val count = scale.items.size
        val placed = remember(candidates, trackPx, density, scale.items.size) {
            fun styleOf(index: Int): TextStyle = if (index == scale.planIndex || scale.items[index].isFailure) strong else plain
            val widths = candidates.associate { c ->
                c.index to measurer.measure(AnnotatedString(c.text), styleOf(c.index)).size.width.toFloat()
            }
            val centers = candidates.associate { c -> c.index to effortCenterPx(c.index, count, trackPx, insetPx) }
            placeEffortLabels(candidates, widths, centers, trackPx, gapPx)
        }
        placed.forEach { label ->
            val isPlan = label.index == scale.planIndex
            val isFailure = scale.items[label.index].isFailure
            Text(
                text = label.text,
                style = if (isPlan || isFailure) strong else plain,
                color = when {
                    isPlan -> WizardColors.text
                    isFailure -> SymbolPalette.musculo
                    else -> WizardColors.textFaint
                },
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.offset { IntOffset(label.startPx.roundToInt(), 0) },
            )
        }
    }
}

/** Partículas de fuego que suben dentro del relleno (las del prototipo): más y más rápidas cuanto mayor el nivel. */
private fun DrawScope.drawFire(level: Float, seconds: Float, widthPx: Float, heightPx: Float) {
    val count = effortParticleCount(level)
    if (count == 0) return
    val period = effortParticlePeriodSeconds(level)
    for (i in 0 until count) {
        val leftFraction = (6 + (i * 37) % 88) / 100f
        val sizePx = (3 + (i * 7) % 5).dp.toPx()
        val delay = (i * 0.23f) % 1.6f
        val local = (seconds + delay) / period
        val phase = local - floor(local)
        val eased = 1f - (1f - phase) * (1f - phase)
        val alpha = if (phase < 0.25f) phase / 0.25f else (1f - phase) / 0.75f
        val scale = 0.5f + 0.65f * eased
        val rise = (10f - 40f * eased).dp.toPx()
        val centerX = widthPx * leftFraction
        val centerY = heightPx - 6.dp.toPx() - sizePx + rise + sizePx / 2f
        drawCircle(
            color = ParticleColors[i % ParticleColors.size].copy(alpha = alpha.coerceIn(0f, 1f)),
            radius = sizePx * scale / 2f,
            center = Offset(centerX, centerY),
        )
    }
    if (level >= 4.5f) {
        // Parpadeo del relleno en el fallo: un velo claro que late (el prototipo sube su brillo un 25 %).
        val pulse = 0.5f + 0.5f * sin((2.0 * PI * seconds / 0.9).toFloat())
        drawRect(Color.White.copy(alpha = 0.06f * pulse))
    }
}

package com.example.kpkn.ui.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Slider expresivo canónico de KPKN (#5 check-in y #13 ROM):
 * pista píldora de 10 dp con gradiente dinámico, halo difuminado (frío en el mínimo,
 * cálido con chispas/llama opcionales en el máximo), pulgar de 26 dp con aro `#1D1D1D`,
 * burbuja flotante en arrastre, marca vertical opcional [planFraction], háptico por paso
 * y semántica completa de accesibilidad.
 */
@Composable
fun KpknExpressiveSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..10f,
    steps: Int = 0,
    minColor: Color = Color(0xFF5B7FA6),
    midColor: Color = Color(0xFFE6E1D5),
    maxColor: Color = Color(0xFFF49A6E),
    sparkAtMax: Boolean = false,
    coolGlowAtMin: Boolean = false,
    planFraction: Float? = null,
    bubbleLabel: ((Float) -> String)? = null,
    semanticLabel: String = "Selector de valor",
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val reducedMotion = wizardReducedMotion()
    val latestOnChange by rememberUpdatedState(onValueChange)
    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)

    var isDragging by remember { mutableStateOf(false) }
    var lastSteppedValue by remember(valueRange, steps) { mutableFloatStateOf(value) }
    var sparkFrame by remember { mutableLongStateOf(0L) }
    var sparkClock by remember { mutableFloatStateOf(0f) }

    val thumbScale by animateFloatAsState(
        targetValue = if (isDragging && !reducedMotion) 1.18f else 1f,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = 460f),
        label = "kpknSliderThumbScale",
    )

    val activeColor = remember(fraction, minColor, midColor, maxColor) {
        if (fraction <= 0.5f) {
            lerp(minColor, midColor, (fraction * 2f).coerceIn(0f, 1f))
        } else {
            lerp(midColor, maxColor, ((fraction - 0.5f) * 2f).coerceIn(0f, 1f))
        }
    }

    val isHighHeat = sparkAtMax && fraction >= 0.78f
    val isCoolGlow = coolGlowAtMin && fraction <= 0.22f

    LaunchedEffect(isHighHeat, reducedMotion) {
        if (!isHighHeat || reducedMotion) return@LaunchedEffect
        var lastNs = 0L
        while (true) {
            withFrameNanos { ns ->
                val dt = if (lastNs == 0L) 0.016f else ((ns - lastNs) / 1_000_000_000f).coerceIn(0f, 0.05f)
                lastNs = ns
                sparkClock += dt
                sparkFrame++
            }
        }
    }

    fun snapValue(rawFraction: Float): Float {
        val clamped = rawFraction.coerceIn(0f, 1f)
        val raw = valueRange.start + clamped * span
        return if (steps > 0) {
            val stepCount = steps + 1
            val stepSize = span / stepCount
            val index = ((raw - valueRange.start) / stepSize).roundToInt().coerceIn(0, stepCount)
            (valueRange.start + index * stepSize).coerceIn(valueRange.start, valueRange.endInclusive)
        } else {
            raw
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = semanticLabel
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = value.coerceIn(valueRange.start, valueRange.endInclusive),
                    range = valueRange,
                    steps = steps,
                )
                setProgress { targetValue ->
                    val clampedTarget = targetValue.coerceIn(valueRange.start, valueRange.endInclusive)
                    val targetFraction = (clampedTarget - valueRange.start) / span
                    val next = if (steps > 0) snapValue(targetFraction) else clampedTarget
                    latestOnChange(next)
                    true
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val thumbSize = 26.dp
        val thumbPx = with(density) { thumbSize.toPx() }
        val trackHeightPx = with(density) { 10.dp.toPx() }
        val insetPx = thumbPx / 2f
        val usablePx = (widthPx - thumbPx).coerceAtLeast(1f)
        val thumbCenterPx = insetPx + usablePx * fraction

        val hapticStepDelta = if (steps > 0) 0.0001f else (span / 20f).coerceAtLeast(0.0001f)
        val gestureModifier = Modifier.pointerInput(valueRange, steps, widthPx) {
            val touchSlop = viewConfiguration.touchSlop
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var startedHorizontalDrag = false
                var movedVertically = false
                var pointerUp = false
                var initialChange = down
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            pointerUp = !change.isConsumed
                            initialChange = change
                            break
                        }
                        if (change.isConsumed) break
                        val dx = abs(change.position.x - down.position.x)
                        val dy = abs(change.position.y - down.position.y)
                        if (dy > touchSlop && dy > dx) {
                            movedVertically = true
                            break
                        }
                        if (dx > touchSlop) {
                            startedHorizontalDrag = true
                            initialChange = change
                            change.consume()
                            break
                        }
                    }
                    if (!movedVertically && (pointerUp || startedHorizontalDrag)) {
                        isDragging = startedHorizontalDrag
                        val initialFrac = ((initialChange.position.x - insetPx) / usablePx).coerceIn(0f, 1f)
                        val initialSnapped = snapValue(initialFrac)
                        if (abs(initialSnapped - lastSteppedValue) >= hapticStepDelta) {
                            lastSteppedValue = initialSnapped
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        latestOnChange(initialSnapped)

                        if (startedHorizontalDrag) {
                            horizontalDrag(down.id) { change ->
                                change.consume()
                                val frac = ((change.position.x - insetPx) / usablePx).coerceIn(0f, 1f)
                                val snapped = snapValue(frac)
                                if (abs(snapped - lastSteppedValue) >= hapticStepDelta) {
                                    lastSteppedValue = snapped
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                latestOnChange(snapped)
                            }
                        }
                    }
                } finally {
                    isDragging = false
                }
            }
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .then(gestureModifier),
            contentAlignment = Alignment.CenterStart,
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                // Read sparkFrame so Canvas redraws for embers when active.
                @Suppress("UNUSED_VARIABLE")
                val frameTick = sparkFrame
                val cy = size.height / 2f
                val trackTop = cy - trackHeightPx / 2f
                val trackRadius = CornerRadius(trackHeightPx / 2f, trackHeightPx / 2f)

                // Base track
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.12f),
                    topLeft = Offset(0f, trackTop),
                    size = Size(size.width, trackHeightPx),
                    cornerRadius = trackRadius,
                )

                // Active gradient fill
                val fillWidth = thumbCenterPx.coerceIn(trackHeightPx, size.width)
                val activeBrush = if (fraction <= 0.5f) {
                    Brush.horizontalGradient(
                        colors = listOf(minColor, activeColor),
                        startX = 0f,
                        endX = fillWidth,
                    )
                } else {
                    val midStop = (0.5f / fraction).coerceIn(0.01f, 0.99f)
                    Brush.horizontalGradient(
                        0f to minColor,
                        midStop to midColor,
                        1f to activeColor,
                        startX = 0f,
                        endX = fillWidth,
                    )
                }
                drawRoundRect(
                    brush = activeBrush,
                    topLeft = Offset(0f, trackTop),
                    size = Size(fillWidth, trackHeightPx),
                    cornerRadius = trackRadius,
                )

                // Optional plan target marker (2 dp x 24 dp)
                planFraction?.let { pf ->
                    val planX = insetPx + usablePx * pf.coerceIn(0f, 1f)
                    val markH = 24.dp.toPx()
                    val markW = 2.dp.toPx()
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.65f),
                        topLeft = Offset(planX - markW / 2f, cy - markH / 2f),
                        size = Size(markW, markH),
                        cornerRadius = CornerRadius(markW / 2f, markW / 2f),
                    )
                }

                // Halo glow around thumb
                val glowRadius = when {
                    isHighHeat -> 24.dp.toPx()
                    isCoolGlow -> 20.dp.toPx()
                    else -> 16.dp.toPx()
                }
                val glowAlpha = when {
                    isHighHeat -> 0.42f
                    isCoolGlow -> 0.34f
                    else -> 0.22f
                }
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(activeColor.copy(alpha = glowAlpha), Color.Transparent),
                        center = Offset(thumbCenterPx, cy),
                        radius = glowRadius,
                    ),
                    radius = glowRadius,
                    center = Offset(thumbCenterPx, cy),
                )

                // Subtle rising sparks at max when enabled
                if (isHighHeat && !reducedMotion) {
                    val rng = Random(7)
                    for (i in 0 until 6) {
                        val phase = ((sparkClock * (0.9f + i * 0.13f)) + i * 0.21f) % 1f
                        val angle = -PI.toFloat() * 0.5f + (rng.nextFloat() - 0.5f) * 1.1f
                        val dist = phase * 18.dp.toPx()
                        val sx = thumbCenterPx + cos(angle) * dist * 0.45f
                        val sy = cy - 6.dp.toPx() - abs(sin(angle)) * dist
                        val alpha = (1f - phase) * 0.85f
                        drawCircle(
                            color = lerp(maxColor, Color(0xFFFFF1C8), 1f - phase).copy(alpha = alpha),
                            radius = (2.4f - phase * 1.2f).dp.toPx().coerceAtLeast(0.8f),
                            center = Offset(sx, sy),
                        )
                    }
                }
            }

            // Thumb 26 dp with #1D1D1D ring
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (thumbCenterPx - thumbPx / 2f).roundToInt(),
                            y = 0,
                        )
                    }
                    .size(thumbSize)
                    .graphicsLayer {
                        scaleX = thumbScale
                        scaleY = thumbScale
                    }
                    .clip(CircleShape)
                    .background(activeColor)
                    .border(2.5.dp, Color(0xFF1D1D1D), CircleShape),
            )

            // Floating bubble while dragging
            if (bubbleLabel != null) {
                AnimatedVisibility(
                    visible = isDragging,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.offset {
                        IntOffset(
                            x = (thumbCenterPx - 28.dp.toPx()).roundToInt().coerceIn(0, (widthPx - 56.dp.toPx()).roundToInt().coerceAtLeast(0)),
                            y = (-34).dp.toPx().roundToInt(),
                        )
                    },
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1D1D1D))
                            .border(1.dp, activeColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = bubbleLabel(value),
                            color = WizardColors.text,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

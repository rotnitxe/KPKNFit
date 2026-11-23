package com.example.kpkn.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.sessioneditor.components.EdSheetSpec
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknSetFormat
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import kotlinx.coroutines.flow.filter
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val MAX_REST_MINUTES = 15
private const val MAX_REST_SECONDS = MAX_REST_MINUTES * 60
private val WheelRowHeight = 30.dp
private val ClockDialMaxSide = 148.dp
private const val INNER_WHEEL_DEAD_ZONE = 0.56f
private const val OUTER_RING_REACH = 1.08f

private object ConsumeWheelScroll : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = available
    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
}

/**
 * Selector unificado de descanso en formato reloj interactivo con estética KPKN.
 *
 * Soporta 1 o 2 temporizadores en la misma hoja (p. ej. `Series` solo; `Series` + `Entre lados`;
 * `Entre ejercicios` + `Tras la ronda`) con un anillo circular arrastrable sincronizado en tiempo
 * real con dos ruedas verticales `MM : SS`, presets rápidos contextuales y cierre animado.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KpknRestClockSheet(
    primaryLabel: String,
    initialPrimarySeconds: Int,
    onDismiss: () -> Unit,
    onConfirm: (primarySeconds: Int, secondarySeconds: Int?) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Descanso",
    subtitle: String? = null,
    secondaryLabel: String? = null,
    initialSecondarySeconds: Int? = null,
    primaryStepSeconds: Int = 15,
    secondaryStepSeconds: Int = 5,
    primaryPresets: List<Int>? = null,
    secondaryPresets: List<Int>? = null,
    accentColor: Color = Color(0xFFF2EEE6),
) {
    val hasSecondary = secondaryLabel != null && initialSecondarySeconds != null
    var primarySeconds by rememberSaveable(initialPrimarySeconds) {
        mutableIntStateOf(initialPrimarySeconds.coerceIn(0, MAX_REST_SECONDS))
    }
    var secondarySeconds by rememberSaveable(initialSecondarySeconds) {
        mutableIntStateOf((initialSecondarySeconds ?: 0).coerceIn(0, MAX_REST_SECONDS))
    }
    var activeTab by rememberSaveable(hasSecondary) { mutableIntStateOf(0) }
    var afterClose by remember { mutableStateOf<(() -> Unit)?>(null) }

    val isSecondaryActive = hasSecondary && activeTab == 1
    val activeSeconds = if (isSecondaryActive) secondarySeconds else primarySeconds
    val activeStep = if (isSecondaryActive) secondaryStepSeconds else primaryStepSeconds
    val activeLabel = if (isSecondaryActive) secondaryLabel.orEmpty() else primaryLabel

    val resolvedPrimaryPresets = primaryPresets ?: remember(primaryLabel) {
        if (primaryLabel.contains("ejercicio", ignoreCase = true) || primaryLabel.contains("lado", ignoreCase = true)) {
            listOf(15, 30, 45, 60, 90)
        } else {
            listOf(60, 90, 120, 180, 240)
        }
    }
    val resolvedSecondaryPresets = secondaryPresets ?: remember(secondaryLabel) {
        if (secondaryLabel?.contains("ronda", ignoreCase = true) == true ||
            secondaryLabel?.contains("superserie", ignoreCase = true) == true
        ) {
            listOf(60, 90, 120, 180, 240)
        } else {
            listOf(0, 10, 15, 30, 60)
        }
    }
    val activePresets = if (isSecondaryActive) resolvedSecondaryPresets else resolvedPrimaryPresets
    val haptics = LocalHapticFeedback.current

    fun updateActiveSeconds(newSeconds: Int, haptic: Boolean = false) {
        val clamped = newSeconds.coerceIn(0, MAX_REST_SECONDS)
        val previous = if (isSecondaryActive) secondarySeconds else primarySeconds
        if (clamped != previous) {
            if (isSecondaryActive) {
                secondarySeconds = clamped
            } else {
                primarySeconds = clamped
            }
            if (haptic) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    val effectiveSubtitle = subtitle ?: when {
        hasSecondary -> "$primaryLabel y ${secondaryLabel?.lowercase()}, en un solo reloj."
        else -> "Desliza el aro o gira las ruedas MM : SS."
    }

    KpknSheet(
        onDismissRequest = {
            afterClose?.invoke()
            onDismiss()
        },
        surfaceColor = Color(0xFF1B1B1D),
        maxHeightFraction = 0.92f,
        lienzo = EdSheetSpec,
    ) {
        val animatedDismiss = LocalKpknSheetDismiss.current
        Column(
            modifier = modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .testTag("kpkn-rest-clock-sheet")
                .padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = TextStyle(
                        fontFamily = WizardFonts.display,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        lineHeight = 23.sp,
                        letterSpacing = (-0.4).sp,
                        color = WizardColors.text,
                    ),
                )
                Text(
                    text = effectiveSubtitle,
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                )
            }

            if (hasSecondary) {
                KpknSegmentedControl(
                    options = listOf(
                        "$primaryLabel · ${KpknSetFormat.rest(primarySeconds)}",
                        "${secondaryLabel.orEmpty()} · ${KpknSetFormat.rest(secondarySeconds)}",
                    ),
                    selectedIndex = activeTab,
                    onSelect = { activeTab = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Control central en formato reloj interactivo + ruedas MM:SS sincronizadas
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(KpknSurface.module)
                    .border(1.dp, KpknSurface.moduleBorder, RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    val dialSide = if (maxWidth < ClockDialMaxSide) maxWidth else ClockDialMaxSide
                    androidx.compose.runtime.key(activeTab) {
                        RestClockDialWithWheels(
                            side = dialSide,
                            totalSeconds = activeSeconds,
                            stepSeconds = activeStep,
                            label = activeLabel,
                            accentColor = accentColor,
                            onSecondsChange = { updateActiveSeconds(it, haptic = true) },
                        )
                    }
                }

                // Ajuste fino rápido (−step / +step) con fila completa por temporizador (sin colisión horizontal)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "$primaryLabel (±${primaryStepSeconds}s)",
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                        modifier = Modifier.weight(1f),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        KpknIconButton(
                            icon = KpknLineIcons.Minus,
                            contentDescription = "Quitar $primaryStepSeconds segundos a $primaryLabel",
                            enabled = primarySeconds > 0,
                            onClick = {
                                activeTab = 0
                                primarySeconds = (primarySeconds - primaryStepSeconds).coerceIn(0, MAX_REST_SECONDS)
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                        )
                        Text(
                            text = KpknSetFormat.rest(primarySeconds),
                            style = KpknType.bodyStrong,
                            color = WizardColors.text,
                        )
                        KpknIconButton(
                            icon = KpknLineIcons.Plus,
                            contentDescription = "Sumar $primaryStepSeconds segundos a $primaryLabel",
                            enabled = primarySeconds < MAX_REST_SECONDS,
                            onClick = {
                                activeTab = 0
                                primarySeconds = (primarySeconds + primaryStepSeconds).coerceIn(0, MAX_REST_SECONDS)
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                        )
                    }
                }

                if (hasSecondary && secondaryLabel != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "$secondaryLabel (±${secondaryStepSeconds}s)",
                            style = KpknType.caption,
                            color = WizardColors.textMuted,
                            modifier = Modifier.weight(1f),
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            KpknIconButton(
                                icon = KpknLineIcons.Minus,
                                contentDescription = "Quitar $secondaryStepSeconds segundos a $secondaryLabel",
                                enabled = secondarySeconds > 0,
                                onClick = {
                                    activeTab = 1
                                    secondarySeconds = (secondarySeconds - secondaryStepSeconds).coerceIn(0, MAX_REST_SECONDS)
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                },
                            )
                            Text(
                                text = KpknSetFormat.rest(secondarySeconds),
                                style = KpknType.bodyStrong,
                                color = WizardColors.text,
                            )
                            KpknIconButton(
                                icon = KpknLineIcons.Plus,
                                contentDescription = "Sumar $secondaryStepSeconds segundos a $secondaryLabel",
                                enabled = secondarySeconds < MAX_REST_SECONDS,
                                onClick = {
                                    activeTab = 1
                                    secondarySeconds = (secondarySeconds + secondaryStepSeconds).coerceIn(0, MAX_REST_SECONDS)
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                },
                            )
                        }
                    }
                }
            }

            // Fila compacta de presets rápidos contextuales (1 sola línea)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            ) {
                activePresets.forEach { preset ->
                    val labelText = if (preset < 60) "$preset s" else KpknSetFormat.rest(preset)
                    KpknChip(
                        text = labelText,
                        selected = activeSeconds == preset,
                        onClick = { updateActiveSeconds(preset, haptic = true) },
                    )
                }
            }

            KpknPrimaryPill(
                text = "Listo",
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val commitAction = {
                        onConfirm(primarySeconds, if (hasSecondary) secondarySeconds else null)
                    }
                    afterClose = commitAction
                    if (animatedDismiss != null) {
                        animatedDismiss()
                    } else {
                        commitAction()
                        onDismiss()
                    }
                },
            )
        }
    }
}

@Composable
private fun RestClockDialWithWheels(
    side: Dp,
    totalSeconds: Int,
    stepSeconds: Int,
    label: String,
    accentColor: Color,
    onSecondsChange: (Int) -> Unit,
) {
    val currentSeconds by rememberUpdatedState(totalSeconds)
    val currentOnChange by rememberUpdatedState(onSecondsChange)
    var dragging by remember { mutableStateOf(false) }

    // 1 vuelta completa del reloj = 60 segundos; el progreso total ilumina también un anillo exterior hasta 15 min
    val secondsInMinute = (totalSeconds % 60).coerceIn(0, 59)
    val minutes = (totalSeconds / 60).coerceIn(0, MAX_REST_MINUTES)
    val targetAngleDeg = (secondsInMinute / 60f) * 360f
    val animatedAngleDeg by animateFloatAsState(
        targetValue = targetAngleDeg,
        animationSpec = if (dragging) tween(40) else spring(dampingRatio = 0.82f, stiffness = 600f),
        label = "restClockAngle",
    )
    val grabFraction by animateFloatAsState(
        targetValue = if (dragging) 1f else 0f,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "restClockGrab",
    )

    Box(
        modifier = Modifier
            .size(side)
            .testTag("kpkn-rest-clock-dial")
            .pointerInput(stepSeconds) {
                awaitEachGesture {
                    val half = size.width / 2f
                    val center = Offset(half, size.height / 2f)
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val firstVec = down.position - center
                    val distance = firstVec.getDistance()
                    if (distance < half * INNER_WHEEL_DEAD_ZONE || distance > half * OUTER_RING_REACH) {
                        return@awaitEachGesture
                    }
                    down.consume()
                    dragging = true

                    fun angleClockDegrees(offset: Offset): Float {
                        val rad = atan2(offset.y.toDouble(), offset.x.toDouble())
                        val deg = Math.toDegrees(rad).toFloat() + 90f
                        return ((deg % 360f) + 360f) % 360f
                    }

                    var lastAngle = angleClockDegrees(firstVec)
                    // Al tocar el perímetro, ajusta los segundos dentro del minuto actual al ángulo tocado
                    val initialMinuteBase = (currentSeconds / 60) * 60
                    val snappedSecInMin = (((lastAngle / 360f) * 60f) / 5f).roundToInt().coerceIn(0, 11) * 5
                    var accumulatedSeconds = (initialMinuteBase + snappedSecInMin).coerceIn(0, MAX_REST_SECONDS)
                    currentOnChange(accumulatedSeconds)

                    try {
                        drag(down.id) { change ->
                            change.consume()
                            val currentVec = change.position - center
                            val newAngle = angleClockDegrees(currentVec)
                            var deltaAngle = newAngle - lastAngle
                            if (deltaAngle > 180f) deltaAngle -= 360f
                            if (deltaAngle < -180f) deltaAngle += 360f
                            val deltaSeconds = ((deltaAngle / 360f) * 60f).roundToInt()
                            if (kotlin.math.abs(deltaSeconds) >= 5) {
                                val stepDelta = (deltaSeconds / 5) * 5
                                accumulatedSeconds = (accumulatedSeconds + stepDelta).coerceIn(0, MAX_REST_SECONDS)
                                lastAngle = newAngle
                                currentOnChange(accumulatedSeconds)
                            }
                        }
                    } finally {
                        dragging = false
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            val scale = radius / 118f

            val outerTrackRadius = 104f * scale
            val tickOuterRadius = 95f * scale
            val innerWellRadius = 68f * scale

            // Esfera exterior e interior
            drawCircle(
                color = Color(0xFF151517),
                radius = radius - 2f * scale,
                center = center,
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.08f),
                radius = radius - 2f * scale,
                center = center,
                style = Stroke(width = 1.dp.toPx()),
            )

            // Pista circular de progreso
            drawCircle(
                color = Color.White.copy(alpha = 0.09f),
                radius = outerTrackRadius,
                center = center,
                style = Stroke(width = 4.dp.toPx()),
            )

            // Arco activo: vuelta completa si hay >= 1 min o fracción de segundos actuales
            val sweepAngle = when {
                totalSeconds == 0 -> 0f
                secondsInMinute == 0 && minutes > 0 -> 360f
                else -> animatedAngleDeg
            }
            if (sweepAngle > 0f) {
                drawArc(
                    color = accentColor,
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(center.x - outerTrackRadius, center.y - outerTrackRadius),
                    size = Size(outerTrackRadius * 2f, outerTrackRadius * 2f),
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
                )
            }

            // 60 marcas de reloj alrededor del perímetro
            for (i in 0 until 60) {
                val isMajor = i % 5 == 0
                val tickLen = (if (isMajor) 9f else 4.5f) * scale
                val angleRad = ((i * 6f - 90f) * (PI / 180.0)).toFloat()
                val cosA = cos(angleRad)
                val sinA = sin(angleRad)
                val start = Offset(
                    center.x + cosA * (tickOuterRadius - tickLen),
                    center.y + sinA * (tickOuterRadius - tickLen),
                )
                val end = Offset(
                    center.x + cosA * tickOuterRadius,
                    center.y + sinA * tickOuterRadius,
                )
                drawLine(
                    color = Color.White.copy(alpha = if (isMajor) 0.36f else 0.14f),
                    start = start,
                    end = end,
                    strokeWidth = (if (isMajor) 1.8f else 1f) * scale,
                    cap = StrokeCap.Round,
                )
            }

            // Pozo central para las ruedas MM : SS
            drawCircle(
                color = Color(0xFF0D0D0F),
                radius = innerWellRadius,
                center = center,
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.08f),
                radius = innerWellRadius,
                center = center,
                style = Stroke(width = 1.dp.toPx()),
            )

            // Manecilla e indicador arrastrable en el perímetro
            val handAngleRad = ((animatedAngleDeg - 90f) * (PI / 180.0)).toFloat()
            val handCos = cos(handAngleRad)
            val handSin = sin(handAngleRad)
            val handStart = Offset(
                center.x + handCos * (innerWellRadius + 2f * scale),
                center.y + handSin * (innerWellRadius + 2f * scale),
            )
            val knobCenter = Offset(
                center.x + handCos * outerTrackRadius,
                center.y + handSin * outerTrackRadius,
            )
            drawLine(
                color = accentColor.copy(alpha = 0.85f),
                start = handStart,
                end = knobCenter,
                strokeWidth = 2.2f * scale,
                cap = StrokeCap.Round,
            )
            val knobRadius = (8.5f + 2f * grabFraction) * scale
            drawCircle(
                color = accentColor,
                radius = knobRadius,
                center = knobCenter,
            )
            drawCircle(
                color = Color(0xFF111111),
                radius = knobRadius * 0.38f,
                center = knobCenter,
            )
        }

        // Dos ruedas scrolleables verticales MM : SS en el centro del reloj
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            val secStepIndex = ((totalSeconds % 60) / 5).coerceIn(0, 11)
            RestTimeWheel(
                count = MAX_REST_MINUTES + 1,
                selectedIndex = minutes,
                label = "Minutos de $label",
                formatValue = { "%02d".format(it) },
                onIndexChange = { newMin ->
                    val currentSec = totalSeconds % 60
                    onSecondsChange((newMin * 60 + currentSec).coerceIn(0, MAX_REST_SECONDS))
                },
            )
            Text(
                text = ":",
                style = TextStyle(
                    fontFamily = WizardFonts.display,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp,
                    color = WizardColors.text,
                ),
                modifier = Modifier.padding(horizontal = 2.dp),
            )
            RestTimeWheel(
                count = 12,
                selectedIndex = secStepIndex,
                label = "Segundos de $label",
                formatValue = { "%02d".format(it * 5) },
                onIndexChange = { newSecIndex ->
                    val newSec = (newSecIndex * 5).coerceIn(0, 55)
                    onSecondsChange((minutes * 60 + newSec).coerceIn(0, MAX_REST_SECONDS))
                },
            )
        }
    }
}

@Composable
private fun RestTimeWheel(
    count: Int,
    selectedIndex: Int,
    label: String,
    formatValue: (Int) -> String,
    onIndexChange: (Int) -> Unit,
) {
    val density = LocalDensity.current
    val itemPx = with(density) { WheelRowHeight.roundToPx().coerceAtLeast(1) }
    val state = rememberScrollState(initial = selectedIndex.coerceIn(0, count - 1) * itemPx)
    val centered by remember(state, itemPx, count) {
        derivedStateOf { (state.value.toFloat() / itemPx).roundToInt().coerceIn(0, count - 1) }
    }
    val currentOnIndexChange by rememberUpdatedState(onIndexChange)
    var userScrolling by remember { mutableStateOf(false) }
    var programmaticScroll by remember { mutableStateOf(false) }

    LaunchedEffect(state, itemPx, count) {
        snapshotFlow { state.isScrollInProgress }
            .collect { inProgress ->
                if (inProgress) {
                    if (!programmaticScroll) {
                        userScrolling = true
                    }
                } else if (userScrolling) {
                    userScrolling = false
                    val target = (state.value.toFloat() / itemPx).roundToInt().coerceIn(0, count - 1)
                    if (state.value != target * itemPx) {
                        programmaticScroll = true
                        try {
                            state.animateScrollTo(target * itemPx, tween(120))
                        } finally {
                            programmaticScroll = false
                        }
                    }
                    currentOnIndexChange(target)
                }
            }
    }

    LaunchedEffect(selectedIndex, itemPx) {
        val target = selectedIndex.coerceIn(0, count - 1)
        val targetPx = target * itemPx
        if (!userScrolling && state.value != targetPx) {
            programmaticScroll = true
            try {
                state.scrollTo(targetPx)
            } finally {
                programmaticScroll = false
            }
        }
    }

    Column(
        modifier = Modifier
            .width(52.dp)
            .height(WheelRowHeight * 3)
            .semantics {
                contentDescription = label
                stateDescription = formatValue(centered)
            }
            .nestedScroll(ConsumeWheelScroll)
            .verticalScroll(state),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(WheelRowHeight))
        repeat(count) { index ->
            val formatted = formatValue(index)
            Box(
                modifier = Modifier
                    .height(WheelRowHeight)
                    .fillMaxWidth()
                    .graphicsLayer { alpha = if (index == centered) 1f else 0.28f }
                    .clickable(role = Role.Button, onClickLabel = "Elegir $formatted") {
                        currentOnIndexChange(index)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = formatted,
                    style = TextStyle(
                        fontFamily = WizardFonts.display,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 24.sp,
                        letterSpacing = (-1).sp,
                        fontFeatureSettings = "'tnum' 1, 'lnum' 1",
                        color = WizardColors.text,
                    ),
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(WheelRowHeight))
    }
}

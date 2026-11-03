package com.example.kpkn.screens.technique.player.design

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.biomechanics.metrics.RepKind
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.technique.player.TechniqueRepMark
import com.example.kpkn.ui.design.KpknType
import kotlin.math.max

/** Etiqueta de prueba de la línea de tiempo del reproductor. */
const val TECHNIQUE_SCRUBBER_TAG = "technique-scrubber"

/** Aire a cada lado de la pista: el radio de la bolita, para que en los extremos no se corte. */
private val TrackPad: Dp = 10.dp
private val TrackThickness: Dp = 3.dp
private val NotchHeight: Dp = 6.dp
private val NotchWidth: Dp = 2.dp
private val ChosenNotchHeight: Dp = 14.dp
private val ChosenNotchWidth: Dp = 3.dp
private val ThumbRadius: Dp = 6.dp
private val ThumbGrowth: Dp = 3.dp
private val ThumbHaloRadius: Dp = 15.dp

private val TrackColor = WizardColors.text.copy(alpha = 0.16f)
private val ChosenSpanColor = WizardColors.text.copy(alpha = 0.32f)
private val NotchColor = WizardColors.text.copy(alpha = 0.55f)
private val PartialNotchColor = WizardColors.text.copy(alpha = 0.28f)
private val FailedNotchColor = WizardColors.danger.copy(alpha = 0.85f)
private val HaloColor = WizardColors.text.copy(alpha = 0.18f)

/** Anillo oscuro de la bolita: la separa de las muescas y del trazo de progreso que pasan por debajo. */
private val ThumbRingColor = Color.Black.copy(alpha = 0.65f)
private val ThumbRing: Dp = 1.5.dp

private val TimeStyle = KpknType.caption.copy(fontFeatureSettings = "'tnum' 1")

/**
 * La línea de tiempo del reproductor de técnica (sin estado): una pista fina con el progreso, una muesca de 6 dp al inicio de cada
 * repetición —más alta y de tinta la elegida, atenuada las parciales, de peligro las fallidas— y, tras la elegida, su tramo
 * resaltado. Tocar o arrastrar busca la posición: [onSeek] recibe los µs (con [onScrubStart] antes del primero y [onScrubEnd]
 * después del último, para que el reproductor pase a la búsqueda rápida mientras se arrastra). A los lados, el tiempo actual y la
 * duración.
 *
 * - [positionUs] se lee SOLO al dibujar: el avance del vídeo no recompone. La etiqueta del tiempo y la lectura de TalkBack cambian
 *   una vez por segundo.
 * - TalkBack: «Posición 0:12 de 0:31, repetición 4 de 8», ajustable con el deslizamiento.
 * - Mientras se arrastra, la bolita sigue al dedo y suena un toque suave al cruzar el inicio de una repetición.
 */
@Composable
fun TechniqueScrubber(
    positionUs: () -> Long,
    durationUs: Long,
    reps: List<TechniqueRepMark>,
    selectedRep: Int,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onScrubStart: () -> Unit = {},
    onScrubEnd: () -> Unit = {},
) {
    val reduced = wizardReducedMotion()
    val haptics = LocalHapticFeedback.current
    val latestPosition by rememberUpdatedState(positionUs)
    val latestSeek by rememberUpdatedState(onSeek)
    val latestStart by rememberUpdatedState(onScrubStart)
    val latestEnd by rememberUpdatedState(onScrubEnd)
    val latestReps by rememberUpdatedState(reps)
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    // El segundo que se enseña: cambia una vez por segundo, así ni la etiqueta ni la lectura de TalkBack recomponen por fotograma.
    val shownSecond by remember(durationUs) {
        derivedStateOf {
            if (dragging) (dragFraction * (durationUs / 1_000_000f)).toLong() else latestPosition() / 1_000_000L
        }
    }
    val totalSecond = durationUs / 1_000_000L
    val shownFraction = if (durationUs > 0L) (shownSecond * 1_000_000f / durationUs).coerceIn(0f, 1f) else 0f
    val repNow = reps.indexOfFirst { shownSecond * 1_000_000L in it.startUs..it.endUs }
    val totalText = if (durationUs > 0L) formatClock(totalSecond) else "—"
    val spoken = buildString {
        append("Posición ${formatClock(shownSecond)}")
        if (durationUs > 0L) append(" de $totalText")
        if (repNow >= 0) append(", repetición ${repNow + 1} de ${reps.size}")
    }

    val thumbSpec: AnimationSpec<Float> = if (reduced) snap() else spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)
    val thumb = animateFloatAsState(
        targetValue = if (dragging) 1f else 0f,
        animationSpec = thumbSpec,
        label = "techniqueScrubberThumb",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatClock(shownSecond),
            style = TimeStyle,
            color = WizardColors.textMuted,
            maxLines = 1,
            modifier = Modifier.widthIn(min = 40.dp).clearAndSetSemantics { },
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .testTag(TECHNIQUE_SCRUBBER_TAG)
                .semantics {
                    contentDescription = "Posición del vídeo"
                    stateDescription = spoken
                    progressBarRangeInfo = ProgressBarRangeInfo(current = shownFraction, range = 0f..1f)
                    setProgress { target ->
                        if (durationUs <= 0L) {
                            false
                        } else {
                            latestStart()
                            latestSeek((target.coerceIn(0f, 1f) * durationUs).toLong())
                            latestEnd()
                            true
                        }
                    }
                }
                // Un toque busca esa posición. Es aparte del arrastre para que un gesto vertical que arranca sobre la línea
                // (el desplazamiento de la pantalla) no mueva el vídeo: el arrastre solo empieza al pasar el umbral horizontal.
                .pointerInput(durationUs) {
                    if (durationUs <= 0L) return@pointerInput
                    detectTapGestures { offset ->
                        val pad = TrackPad.toPx()
                        val span = size.width - 2f * pad
                        if (span > 0f) {
                            val us = (((offset.x - pad) / span).coerceIn(0f, 1f) * durationUs).toLong()
                            latestStart()
                            latestSeek(us)
                            latestEnd()
                        }
                    }
                }
                .pointerInput(durationUs) {
                    if (durationUs <= 0L) return@pointerInput
                    var lastRep = -1
                    fun moveTo(x: Float) {
                        val pad = TrackPad.toPx()
                        val span = size.width - 2f * pad
                        if (span <= 0f) return
                        val fraction = ((x - pad) / span).coerceIn(0f, 1f)
                        val us = (fraction * durationUs).toLong()
                        dragFraction = fraction
                        val list = latestReps
                        var rep = -1
                        for (i in list.indices) if (us in list[i].startUs..list[i].endUs) rep = i
                        if (rep != lastRep) {
                            if (rep >= 0) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            lastRep = rep
                        }
                        latestSeek(us)
                    }
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            latestStart()
                            dragging = true
                            lastRep = -1
                            moveTo(offset.x)
                        },
                        onDragEnd = {
                            dragging = false
                            latestEnd()
                        },
                        onDragCancel = {
                            dragging = false
                            latestEnd()
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            moveTo(change.position.x)
                        },
                    )
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val pad = TrackPad.toPx()
                val left = pad
                val span = size.width - 2f * pad
                if (span <= 0f) return@Canvas
                val cy = size.height / 2f
                val thickness = TrackThickness.toPx()
                val fraction = when {
                    dragging -> dragFraction
                    durationUs > 0L -> (latestPosition().toFloat() / durationUs).coerceIn(0f, 1f)
                    else -> 0f
                }

                // tc-track:before — línea base de 2 dp a 12 dp del borde inferior.
                val baseY = size.height - 12.dp.toPx()
                drawLine(TrackColor, Offset(left, baseY), Offset(left + span, baseY), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                if (durationUs > 0L) {
                    // tc-band: la repetición es una BANDA vertical (10 + v·16 px en el lienzo) con degradado
                    // #8fb2ff → #6e8fd0 y opacidad .62; la elegida va opaca y con anillo de tinta.
                    for (i in reps.indices) {
                        val rep = reps[i]
                        val isChosen = i == selectedRep
                        val a = left + span * (rep.startUs.toFloat() / durationUs).coerceIn(0f, 1f)
                        val b = left + span * (rep.endUs.toFloat() / durationUs).coerceIn(0f, 1f)
                        if (b <= a) continue
                        // `.tc-band`: alto 10 + v·16 px en el lienzo (v no está en el dato); 18 dp es la banda media
                        // común a todas las repeticiones. La elegida NO crece: solo cambia opacidad y anillo.
                        val bandHeight = 18.dp.toPx()
                        val top = baseY - bandHeight
                        val width = b - a
                        // `.tc-band`: border-radius 6px 6px 2px 2px.
                        val bandPath = Path().apply {
                            addRoundRect(
                                androidx.compose.ui.geometry.RoundRect(
                                    rect = androidx.compose.ui.geometry.Rect(Offset(a, top), androidx.compose.ui.geometry.Size(width, bandHeight)),
                                    topLeft = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
                                    topRight = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
                                    bottomRight = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
                                    bottomLeft = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
                                ),
                            )
                        }
                        when (rep.kind) {
                            RepKind.PARTIAL -> {
                                // tc-band.part: rayas diagonales a 135° (#f7cf73 3 px / #f7cf7355 3 px).
                                drawPath(bandPath, Color(0x1AF7CF73))
                                val stripe = 3.dp.toPx()
                                val step = stripe * 2f
                                clipRect(left = a, top = top, right = b, bottom = baseY) {
                                    var x = a - bandHeight
                                    while (x < b) {
                                        drawLine(Color(0xCCF7CF73), Offset(x, baseY), Offset(x + bandHeight, top), strokeWidth = stripe)
                                        drawLine(Color(0x55F7CF73), Offset(x + stripe, baseY), Offset(x + stripe + bandHeight, top), strokeWidth = stripe)
                                        x += step
                                    }
                                }
                            }
                            RepKind.FAILED -> drawPath(bandPath, Color(0x55FF9B92))
                            else -> drawPath(
                                path = bandPath,
                                brush = Brush.verticalGradient(
                                    listOf(Color(0xFF8FB2FF), Color(0xFF6E8FD0)),
                                    startY = top,
                                    endY = baseY,
                                ),
                                alpha = if (isChosen) 1f else 0.62f,
                            )
                        }
                        if (isChosen) {
                            // tc-band.sel: box-shadow 0 0 0 1.5px var(--ink)
                            drawPath(
                                path = bandPath,
                                color = WizardColors.text.copy(alpha = 0.85f),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()),
                            )
                        }
                    }
                }
                val grow = thumb.value
                val headX = left + span * fraction
                // tc-head: box-shadow rgba(242,238,230,.6) 0 0 8px. Se emula con un degradado horizontal que se
                // apaga a los lados (un trazo grueso translúcido se vería como una barra, no como un resplandor).
                val glowHalf = 8.dp.toPx()
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, Color(0x99F2EEE6), Color.Transparent),
                        startX = headX - glowHalf,
                        endX = headX + glowHalf,
                    ),
                    topLeft = Offset(headX - glowHalf, 0f),
                    size = androidx.compose.ui.geometry.Size(glowHalf * 2f, size.height),
                )
                drawLine(WizardColors.text, Offset(headX, 2.dp.toPx()), Offset(headX, size.height - 4.dp.toPx()), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                val knobRadius = 5.dp.toPx() + (ThumbGrowth.toPx() * 0.6f * grow)
                val knobCenter = Offset(headX, 4.dp.toPx())
                // Halo radial (no un disco plano): tres paradas hasta transparente.
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0x99F2EEE6), Color(0x40F2EEE6), Color.Transparent),
                        center = knobCenter,
                        radius = knobRadius + 5.dp.toPx(),
                    ),
                    radius = knobRadius + 5.dp.toPx(),
                    center = knobCenter,
                )
                drawCircle(WizardColors.text, radius = knobRadius, center = knobCenter)
            }
        }
        Text(
            text = totalText,
            style = TimeStyle,
            color = WizardColors.textFaint,
            maxLines = 1,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = 40.dp).clearAndSetSemantics { },
        )
    }
}

/** «m:ss» de un número de segundos (negativo cuenta como 0). */
internal fun formatClock(totalSeconds: Long): String {
    val s = max(0L, totalSeconds)
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

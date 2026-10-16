package com.example.kpkn.screens.workout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Momentos visuales del relator solo con animación (H5).
 */
enum class RelatorSilentAnimation {
    NONE,
    REST_BREATHING,
    RECORD_CELEBRATION,
    PREPARATION_SQUAT,
}

private val InkColor = Color(0xFFF2EEE6)
private val InkMutedColor = Color(0xFFF2EEE6).copy(alpha = 0.25f)
private val GroundColor = Color(0xFFF2EEE6).copy(alpha = 0.10f)
private val SparkGreen = Color(0xFF43D18C)
private val WaveBlue = Color(0xFF8FB2FF).copy(alpha = 0.55f)

/**
 * Animación de personajes de trazo fino (1,8 dp) en la franja inferior del relator (46 dp de alto, H2).
 * Corre durante la fase «trabajando» (≈ 1,9 s).
 */
@Composable
fun RelatorWorkingScene(
    actionKind: RelatorAssistActionKind,
    progress: Float,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth(),
    ) {
        val w = size.width
        val h = size.height
        val density = this.density
        val strokeWidth = 1.8f * density
        val strokeCap = StrokeCap.Round

        // Línea de suelo tenue al fondo de la franja (H2)
        val groundY = h - 2f * density
        val hipY = groundY - 7.5f * density
        drawLine(
            color = GroundColor,
            start = Offset(0f, groundY),
            end = Offset(w, groundY),
            strokeWidth = 1f * density,
        )

        when (actionKind) {
            RelatorAssistActionKind.APPLY_SUGGESTED_LOAD,
            RelatorAssistActionKind.ADJUST_LOAD,
            -> {
                // Dos figuras que cruzan andando de izquierda a derecha llevando un disco entre ambas, y otra que aprieta con llave al final a la derecha (H2)
                val walkStartX = -60f * density
                val walkEndX = 250f * density
                val walkX = if (reduceMotion) 80f * density else walkStartX + (walkEndX - walkStartX) * progress.coerceIn(0f, 1f)
                val walkCycles = progress * (1900f / 380f) * 2f * PI.toFloat()
                val bob = if (reduceMotion) 0f else (if (sin(walkCycles) > 0f) -1.5f * density else 0f)
                val legA = if (reduceMotion) 10f else sin(walkCycles) * 20f
                val legB = -legA

                // Pareja que camina con el disco
                translate(left = walkX, top = 0f) {
                    // Figura 1 (izquierda) en (35, hipY)
                    translate(left = 35f * density, top = hipY + bob) {
                        // Cabeza
                        drawCircle(
                            color = InkColor,
                            radius = 2.4f * density,
                            center = Offset(0f, -17f * density),
                            style = Stroke(width = strokeWidth),
                        )
                        // Tronco
                        drawLine(
                            color = InkColor,
                            start = Offset(0f, -14.5f * density),
                            end = Offset(0f, -7.5f * density),
                            strokeWidth = strokeWidth,
                            cap = strokeCap,
                        )
                        // Brazo hacia el disco
                        drawLine(
                            color = InkColor,
                            start = Offset(0f, -13f * density),
                            end = Offset(7f * density, -10f * density),
                            strokeWidth = strokeWidth,
                            cap = strokeCap,
                        )
                        // Piernas
                        translate(left = 0f, top = -7.5f * density) {
                            rotate(legA, pivot = Offset(0f, 0f)) {
                                drawLine(InkColor, Offset(0f, 0f), Offset(0f, 7.5f * density), strokeWidth, strokeCap)
                            }
                            rotate(legB, pivot = Offset(0f, 0f)) {
                                drawLine(InkColor, Offset(0f, 0f), Offset(0f, 7.5f * density), strokeWidth, strokeCap)
                            }
                        }
                    }

                    // Disco en (49, hipY - 10)
                    translate(left = 49f * density, top = (hipY - 10f * density) + bob) {
                        drawCircle(
                            color = SymbolPalette.musculo,
                            radius = 5.5f * density,
                            center = Offset.Zero,
                            style = Stroke(width = 1.8f * density),
                        )
                        drawCircle(
                            color = SymbolPalette.musculo,
                            radius = 1.5f * density,
                            center = Offset.Zero,
                        )
                    }

                    // Figura 2 (derecha) en (63, hipY)
                    translate(left = 63f * density, top = hipY + bob) {
                        // Cabeza
                        drawCircle(
                            color = InkColor,
                            radius = 2.4f * density,
                            center = Offset(0f, -17f * density),
                            style = Stroke(width = strokeWidth),
                        )
                        // Tronco
                        drawLine(
                            color = InkColor,
                            start = Offset(0f, -14.5f * density),
                            end = Offset(0f, -7.5f * density),
                            strokeWidth = strokeWidth,
                            cap = strokeCap,
                        )
                        // Brazo hacia el disco
                        drawLine(
                            color = InkColor,
                            start = Offset(0f, -13f * density),
                            end = Offset(-7f * density, -10f * density),
                            strokeWidth = strokeWidth,
                            cap = strokeCap,
                        )
                        // Piernas
                        translate(left = 0f, top = -7.5f * density) {
                            rotate(legB, pivot = Offset(0f, 0f)) {
                                drawLine(InkColor, Offset(0f, 0f), Offset(0f, 7.5f * density), strokeWidth, strokeCap)
                            }
                            rotate(legA, pivot = Offset(0f, 0f)) {
                                drawLine(InkColor, Offset(0f, 0f), Offset(0f, 7.5f * density), strokeWidth, strokeCap)
                            }
                        }
                    }
                }

                // Figura que aprieta con una llave al final a la derecha (H2)
                val wrenchX = (w - 38f * density).coerceAtLeast(180f * density)
                val wrenchCycles = progress * (1900f / 450f) * 2f * PI.toFloat()
                val wrenchAngle = if (reduceMotion) -10f else sin(wrenchCycles) * 25f
                translate(left = wrenchX, top = hipY) {
                    val energy = SymbolPalette.energia
                    // Cabeza
                    drawCircle(
                        color = energy,
                        radius = 2.4f * density,
                        center = Offset(0f, -17f * density),
                        style = Stroke(width = strokeWidth),
                    )
                    // Tronco
                    drawLine(energy, Offset(0f, -14.5f * density), Offset(0f, -7.5f * density), strokeWidth, strokeCap)
                    // Piernas
                    drawLine(energy, Offset(0f, -7.5f * density), Offset(-3.5f * density, 0f), strokeWidth, strokeCap)
                    drawLine(energy, Offset(0f, -7.5f * density), Offset(3.5f * density, 0f), strokeWidth, strokeCap)
                    // Brazo con llave que gira
                    translate(left = 0f, top = -12f * density) {
                        rotate(wrenchAngle, pivot = Offset(0f, 0f)) {
                            drawLine(energy, Offset(0f, 0f), Offset(-8f * density, 3f * density), strokeWidth, strokeCap)
                            drawCircle(
                                color = energy,
                                radius = 1.6f * density,
                                center = Offset(-9f * density, 3.3f * density),
                                style = Stroke(width = 1.2f * density),
                            )
                        }
                    }
                }
            }
            RelatorAssistActionKind.START_REST,
            RelatorAssistActionKind.EXTEND_REST,
            -> {
                // Figura que gira un reloj de arena
                val centerX = w / 2f
                val rotAngle = if (reduceMotion) 45f else (progress * 360f) % 360f
                translate(left = centerX, top = hipY) {
                    // Cabeza
                    drawCircle(
                        color = InkColor,
                        radius = 3.6f * density,
                        center = Offset(0f, -27f * density),
                        style = Stroke(width = strokeWidth),
                    )
                    // Tronco
                    drawLine(InkColor, Offset(0f, -23f * density), Offset(0f, -12f * density), strokeWidth, strokeCap)
                    // Piernas sentadas
                    drawLine(InkColor, Offset(0f, -12f * density), Offset(-6f * density, 0f), strokeWidth, strokeCap)
                    drawLine(InkColor, Offset(0f, -12f * density), Offset(6f * density, 0f), strokeWidth, strokeCap)
                    // Reloj de arena girando
                    translate(left = 12f * density, top = -16f * density) {
                        rotate(rotAngle, pivot = Offset(0f, 0f)) {
                            val path = Path().apply {
                                moveTo(-5f * density, -7f * density)
                                lineTo(5f * density, -7f * density)
                                lineTo(0f, 0f)
                                lineTo(5f * density, 7f * density)
                                lineTo(-5f * density, 7f * density)
                                lineTo(0f, 0f)
                                close()
                            }
                            drawPath(path, color = SymbolPalette.columna, style = Stroke(width = 1.6f * density))
                        }
                    }
                }
            }
            RelatorAssistActionKind.SKIP_REMAINING_WARMUPS -> {
                // Figura que salta una valla
                val centerX = w / 2f
                // Valla en el centro
                drawLine(
                    color = SymbolPalette.musculo,
                    start = Offset(centerX - 8f * density, groundY),
                    end = Offset(centerX - 8f * density, groundY - 18f * density),
                    strokeWidth = 2f * density,
                    cap = strokeCap,
                )
                drawLine(
                    color = SymbolPalette.musculo,
                    start = Offset(centerX + 8f * density, groundY),
                    end = Offset(centerX + 8f * density, groundY - 18f * density),
                    strokeWidth = 2f * density,
                    cap = strokeCap,
                )
                drawLine(
                    color = SymbolPalette.musculo,
                    start = Offset(centerX - 10f * density, groundY - 18f * density),
                    end = Offset(centerX + 10f * density, groundY - 18f * density),
                    strokeWidth = 2.2f * density,
                    cap = strokeCap,
                )

                // Figura saltando en parábola
                val jumpNorm = progress.coerceIn(0f, 1f)
                val jumperX = if (reduceMotion) centerX else (centerX - 50f * density) + (100f * density * jumpNorm)
                val jumpHeight = if (reduceMotion) 22f * density else sin(jumpNorm * PI.toFloat()) * 26f * density
                val jumperY = hipY - jumpHeight

                translate(left = jumperX, top = jumperY) {
                    drawCircle(
                        color = InkColor,
                        radius = 3.6f * density,
                        center = Offset(0f, -27f * density),
                        style = Stroke(width = strokeWidth),
                    )
                    drawLine(InkColor, Offset(0f, -23f * density), Offset(0f, -12f * density), strokeWidth, strokeCap)
                    // Piernas en salto de valla (una estirada adelante, otra doblada atrás)
                    drawLine(InkColor, Offset(0f, -12f * density), Offset(10f * density, -8f * density), strokeWidth, strokeCap)
                    drawLine(InkColor, Offset(0f, -12f * density), Offset(-8f * density, -2f * density), strokeWidth, strokeCap)
                    // Brazos en equilibrio
                    drawLine(InkColor, Offset(0f, -20f * density), Offset(-8f * density, -25f * density), strokeWidth, strokeCap)
                    drawLine(InkColor, Offset(0f, -20f * density), Offset(8f * density, -15f * density), strokeWidth, strokeCap)
                }
            }
            else -> Unit
        }
    }
}

/**
 * Destello breve en la fase «hecho» (0,9 s): anillo que se expande y rayos verdes (#43D18C, H2).
 */
@Composable
fun RelatorSparkDoneScene(
    progress: Float,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth(),
    ) {
        val density = this.density
        val centerX = size.width / 2f
        val centerY = size.height / 2f

        val p = progress.coerceIn(0f, 1f)
        val ringScale = if (reduceMotion) 1f else 0.2f + 1.1f * p
        val ringAlpha = if (reduceMotion) 0.6f else (1f - p).coerceIn(0f, 1f)

        // Anillo que se expande
        translate(left = centerX, top = centerY) {
            scale(ringScale, pivot = Offset.Zero) {
                drawCircle(
                    color = SymbolPalette.energia.copy(alpha = ringAlpha),
                    radius = 6.5f * density,
                    center = Offset.Zero,
                    style = Stroke(width = 1.3f * density),
                )
            }

            // Rayos verdes (#43D18C)
            val rayAlpha = if (reduceMotion) 0.85f else (1f - p).coerceIn(0f, 1f)
            val rayColor = SparkGreen.copy(alpha = rayAlpha)
            val rayDist = if (reduceMotion) 10f * density else (7.5f + 4.5f * p) * density
            val rayLen = 3.8f * density
            val strokeW = 1.7f * density

            // 6 rayos outward
            val angles = floatArrayOf(0f, 45f, 135f, 180f, 225f, 315f)
            for (angleDeg in angles) {
                val rad = angleDeg * PI.toFloat() / 180f
                val dx = cos(rad)
                val dy = sin(rad)
                drawLine(
                    color = rayColor,
                    start = Offset(dx * rayDist, dy * rayDist),
                    end = Offset(dx * (rayDist + rayLen), dy * (rayDist + rayLen)),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/**
 * Momento solo animación: onda de respiración lenta en el descanso (H5).
 */
@Composable
fun RelatorBreathingWaveScene(
    progress: Float,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
) {
    Canvas(modifier = modifier.fillMaxWidth().height(56.dp)) {
        val density = this.density
        val w = size.width
        val strokeWidth = 1.8f * density

        // Onda sinusoidal
        val wavePath = Path()
        val waveY = 32f * density
        val waveOffset = if (reduceMotion) 0f else (progress * 120f * density) % (120f * density)
        var x = -60f * density - waveOffset
        wavePath.moveTo(x, waveY)
        while (x < w + 60f * density) {
            wavePath.quadraticTo(x + 15f * density, waveY - 14f * density, x + 30f * density, waveY)
            wavePath.quadraticTo(x + 45f * density, waveY + 14f * density, x + 60f * density, waveY)
            x += 60f * density
        }
        drawPath(
            path = wavePath,
            color = WaveBlue,
            style = Stroke(width = 1.6f * density, cap = StrokeCap.Round),
        )

        // Figura en respiración sentada al centro
        val centerX = w / 2f
        val breatheScale = if (reduceMotion) 1f else 1f + 0.06f * sin(progress * 2f * PI.toFloat())
        translate(left = centerX, top = 46f * density) {
            scale(breatheScale, pivot = Offset(0f, 0f)) {
                // Suelo base
                drawLine(InkMutedColor, Offset(-14f * density, 0f), Offset(18f * density, 0f), 1.4f * density, StrokeCap.Round)
                // Cabeza
                drawCircle(InkColor, 4f * density, Offset(0f, -30f * density), style = Stroke(strokeWidth))
                // Espalda y piernas en meditación
                val body = Path().apply {
                    moveTo(0f, -26f * density)
                    lineTo(0f, -14f * density)
                    lineTo(10f * density, -14f * density)
                    lineTo(12f * density, 0f)
                }
                drawPath(body, InkColor, style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
                // Brazo
                drawLine(InkColor, Offset(0f, -22f * density), Offset(-7f * density, -16f * density), strokeWidth, StrokeCap.Round)
            }
        }
    }
}

/**
 * Momento solo animación: celebración con confeti tras registrar un récord personal (H5).
 */
@Composable
fun RelatorRecordCelebrationScene(
    progress: Float,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
) {
    Canvas(modifier = modifier.fillMaxWidth().height(56.dp)) {
        val density = this.density
        val w = size.width
        val centerX = w / 2f
        val strokeWidth = 1.8f * density

        // Levantador levantando barra sobre la cabeza (H5)
        val liftProgress = if (reduceMotion) 0.5f else progress
        val liftY = if (reduceMotion) -8f * density else sin(liftProgress * 2f * PI.toFloat()).coerceAtLeast(0f) * -10f * density

        translate(left = centerX, top = 50f * density) {
            translate(left = 0f, top = liftY) {
                // Barra
                drawLine(InkColor, Offset(-24f * density, -44f * density), Offset(24f * density, -44f * density), 2.2f * density, StrokeCap.Round)
                // Discos en extremos
                drawCircle(SymbolPalette.musculo, 5.5f * density, Offset(-24f * density, -44f * density), style = Stroke(2.2f * density))
                drawCircle(SymbolPalette.musculo, 5.5f * density, Offset(24f * density, -44f * density), style = Stroke(2.2f * density))
                // Brazos
                drawLine(InkColor, Offset(-9f * density, -44f * density), Offset(-5f * density, -30f * density), strokeWidth, StrokeCap.Round)
                drawLine(InkColor, Offset(9f * density, -44f * density), Offset(5f * density, -30f * density), strokeWidth, StrokeCap.Round)
                // Cabeza
                drawCircle(InkColor, 4f * density, Offset(0f, -34f * density), style = Stroke(strokeWidth))
            }
            // Tronco y piernas
            drawLine(InkColor, Offset(0f, -26f * density), Offset(0f, -12f * density), strokeWidth, StrokeCap.Round)
            drawLine(InkColor, Offset(0f, -12f * density), Offset(-6f * density, 0f), strokeWidth, StrokeCap.Round)
            drawLine(InkColor, Offset(0f, -12f * density), Offset(6f * density, 0f), strokeWidth, StrokeCap.Round)
        }

        // Confeti flotante/cayendo
        val confetti = listOf(
            Triple(-75f, 4f, SymbolPalette.energia),
            Triple(-45f, 0f, SymbolPalette.musculo),
            Triple(45f, 2f, SymbolPalette.columna),
            Triple(73f, 6f, SparkGreen),
            Triple(-97f, 8f, SymbolPalette.mente),
            Triple(95f, 0f, SymbolPalette.energia),
        )
        confetti.forEachIndexed { idx, (xOffset, yStart, color) ->
            val phase = (progress + (idx * 0.18f)) % 1f
            val fallY = if (reduceMotion) yStart * density else yStart * density + (36f * density * phase)
            val alpha = if (reduceMotion) 0.8f else (1f - phase).coerceIn(0.2f, 1f)
            val rot = if (reduceMotion) idx * 30f else phase * 220f
            translate(left = centerX + (xOffset * density), top = fallY) {
                rotate(rot, pivot = Offset.Zero) {
                    drawRect(
                        color = color.copy(alpha = alpha),
                        topLeft = Offset(-2.5f * density, -2.5f * density),
                        size = Size(5f * density, 5f * density),
                    )
                }
            }
        }
    }
}

/**
 * Momento solo animación: preparación antes de la primera serie con figura agachada y puntos (H5).
 */
@Composable
fun RelatorPreparationSquatScene(
    progress: Float,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
) {
    Canvas(modifier = modifier.fillMaxWidth().height(56.dp)) {
        val density = this.density
        val w = size.width
        val centerX = w / 2f
        val strokeWidth = 1.8f * density

        // Figura agachada (H5)
        val squatBob = if (reduceMotion) 3f * density else (sin(progress * 2f * PI.toFloat()) + 1f) * 3f * density
        translate(left = centerX - 15f * density, top = 50f * density + squatBob) {
            // Cabeza
            drawCircle(InkColor, 4f * density, Offset(0f, -40f * density), style = Stroke(strokeWidth))
            // Barra sobre los hombros
            drawLine(InkColor, Offset(-16f * density, -32f * density), Offset(16f * density, -32f * density), 2.2f * density, StrokeCap.Round)
            // Tronco y piernas agachadas
            val squatter = Path().apply {
                moveTo(0f, -36f * density)
                lineTo(0f, -20f * density)
                lineTo(-7f * density, -10f * density)
                lineTo(-5f * density, 0f)
                moveTo(0f, -20f * density)
                lineTo(7f * density, -10f * density)
                lineTo(5f * density, 0f)
            }
            drawPath(squatter, InkColor, style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        // Puntos suspensivos animados (...)
        val dot1Alpha = if (reduceMotion) 0.6f else (0.25f + 0.75f * ((sin(progress * 2f * PI.toFloat()) + 1f) / 2f))
        val dot2Alpha = if (reduceMotion) 0.6f else (0.25f + 0.75f * ((sin((progress + 0.2f) * 2f * PI.toFloat()) + 1f) / 2f))
        val dot3Alpha = if (reduceMotion) 0.6f else (0.25f + 0.75f * ((sin((progress + 0.4f) * 2f * PI.toFloat()) + 1f) / 2f))

        val dotY = 32f * density
        drawCircle(InkColor.copy(alpha = dot1Alpha), 3f * density, Offset(centerX + 19f * density, dotY))
        drawCircle(InkColor.copy(alpha = dot2Alpha), 3f * density, Offset(centerX + 31f * density, dotY))
        drawCircle(InkColor.copy(alpha = dot3Alpha), 3f * density, Offset(centerX + 43f * density, dotY))
    }
}

package com.example.kpkn.ui.components.rings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Elementos vivos de los RINGS de Inicio (I1):
 * - Puntos luminosos que orbitan cada anillo a distinta velocidad (Energía la más rápida).
 * - Personaje de trazo fino que salta entre anillos con aplastamiento al aterrizar,
 *   estiramiento en el aire, destello en el anillo de aterrizaje y saludo con el brazo.
 */

/** Canales en los que puede aterrizar el personaje según cuáles estén calibrados. */
internal object HopRoutePlanner {
    fun resolveLandingChannels(calibrated: Set<RingChannel>): List<RingChannel> {
        val canonical = listOf(RingChannel.COLUMNA, RingChannel.MUSCULOS, RingChannel.ENERGIA)
        val valid = canonical.filter { it in calibrated }
        return if (valid.isEmpty()) listOf(RingChannel.COLUMNA) else valid
    }
}

/** Pose del personaje en el sistema normalizado 300 × 230 de I1. */
data class LivingCharacterPose(
    val xNorm: Float,
    val yNorm: Float,
    val scaleX: Float,
    val scaleY: Float,
    val armRotationDeg: Float = 0f,
    val channel: RingChannel = RingChannel.COLUMNA,
    val flashChannel: RingChannel? = null,
    val flashAlpha: Float = 0.22f,
) {
    companion object {
        /** Pose estática de pie en el anillo inferior (Columna). */
        val STATIC_REST = LivingCharacterPose(
            xNorm = 219f,
            yNorm = 212f,
            scaleX = 1f,
            scaleY = 1f,
            channel = RingChannel.COLUMNA,
        )

        /** Fotograma para hoja de render: en el aire, estirado. */
        val AIR = LivingCharacterPose(
            xNorm = 214f,
            yNorm = 152f,
            scaleX = 0.94f,
            scaleY = 1.10f,
            channel = RingChannel.COLUMNA,
        )

        /** Fotograma para hoja de render: aterrizando, aplastado con destello. */
        val LANDING = LivingCharacterPose(
            xNorm = 202f,
            yNorm = 138f,
            scaleX = 1.14f,
            scaleY = 0.80f,
            channel = RingChannel.MUSCULOS,
            flashChannel = RingChannel.MUSCULOS,
            flashAlpha = 0.75f,
        )
    }
}

/** Calcula la pose del personaje a lo largo del ciclo continuo de 7,5 s de I1. */
internal fun calculateHopPose(
    progress: Float, // 0..1 en el ciclo de 7,5 s
    landingChannels: List<RingChannel>,
): LivingCharacterPose {
    // Si no tiene anillos intermedios o superiores, se queda o brinca en Columna
    val hasMuscle = RingChannel.MUSCULOS in landingChannels
    val hasEnergy = RingChannel.ENERGIA in landingChannels

    val p = progress.coerceIn(0f, 1f)
    return when {
        // Fase 1: preparación y salto a Músculos (o directo a Energía si Músculos no está)
        p <= 0.06f -> {
            val f = p / 0.06f
            LivingCharacterPose(
                xNorm = 219f,
                yNorm = 212f,
                scaleX = 1f + 0.12f * f,
                scaleY = 1f - 0.22f * f,
                channel = RingChannel.COLUMNA,
            )
        }
        p <= 0.14f -> {
            val f = (p - 0.06f) / 0.08f
            val targetX = if (hasMuscle) 214f else if (hasEnergy) 205f else 219f
            val targetY = if (hasMuscle) 152f else if (hasEnergy) 120f else 180f
            LivingCharacterPose(
                xNorm = lerpFloat(219f, targetX, f),
                yNorm = lerpFloat(212f, targetY, f),
                scaleX = lerpFloat(1.12f, 0.94f, f),
                scaleY = lerpFloat(0.78f, 1.10f, f),
                channel = RingChannel.COLUMNA,
            )
        }
        p <= 0.21f -> {
            val f = (p - 0.14f) / 0.07f
            if (hasMuscle) {
                LivingCharacterPose(
                    xNorm = lerpFloat(214f, 202f, f),
                    yNorm = lerpFloat(152f, 138f, f),
                    scaleX = lerpFloat(0.94f, 1.14f, f),
                    scaleY = lerpFloat(1.10f, 0.80f, f),
                    channel = RingChannel.MUSCULOS,
                    flashChannel = RingChannel.MUSCULOS,
                    flashAlpha = lerpFloat(0.22f, 0.75f, f),
                )
            } else if (hasEnergy) {
                // Sigue subiendo hacia Energía
                LivingCharacterPose(
                    xNorm = lerpFloat(205f, 196f, f),
                    yNorm = lerpFloat(120f, 84f, f),
                    scaleX = 0.94f,
                    scaleY = 1.10f,
                    channel = RingChannel.COLUMNA,
                )
            } else {
                // Aterriza de vuelta en Columna
                LivingCharacterPose(
                    xNorm = 219f,
                    yNorm = 212f,
                    scaleX = lerpFloat(0.94f, 1.14f, f),
                    scaleY = lerpFloat(1.10f, 0.80f, f),
                    channel = RingChannel.COLUMNA,
                )
            }
        }
        p <= 0.25f -> {
            val f = (p - 0.21f) / 0.04f
            val ch = if (hasMuscle) RingChannel.MUSCULOS else RingChannel.COLUMNA
            val (x, y) = if (hasMuscle) 202f to 138f else 219f to 212f
            LivingCharacterPose(
                xNorm = x,
                yNorm = y,
                scaleX = lerpFloat(1.14f, 1.0f, f),
                scaleY = lerpFloat(0.80f, 1.0f, f),
                channel = ch,
                flashChannel = if (hasMuscle) RingChannel.MUSCULOS else null,
                flashAlpha = lerpFloat(0.75f, 0.22f, f),
            )
        }
        p <= 0.35f -> {
            val ch = if (hasMuscle) RingChannel.MUSCULOS else RingChannel.COLUMNA
            val (x, y) = if (hasMuscle) 202f to 138f else 219f to 212f
            val f = (p - 0.30f).coerceAtLeast(0f) / 0.05f
            LivingCharacterPose(
                xNorm = x,
                yNorm = y,
                scaleX = 1f + 0.12f * f,
                scaleY = 1f - 0.22f * f,
                channel = ch,
            )
        }
        // Fase 2: salto a Energía
        p <= 0.43f -> {
            val f = (p - 0.35f) / 0.08f
            val startX = if (hasMuscle) 202f else 219f
            val startY = if (hasMuscle) 138f else 212f
            val targetX = if (hasEnergy) 196f else startX
            val targetY = if (hasEnergy) 84f else startY - 20f
            LivingCharacterPose(
                xNorm = lerpFloat(startX, targetX, f),
                yNorm = lerpFloat(startY, targetY, f),
                scaleX = lerpFloat(1.12f, 0.94f, f),
                scaleY = lerpFloat(0.78f, 1.10f, f),
                channel = if (hasMuscle) RingChannel.MUSCULOS else RingChannel.COLUMNA,
            )
        }
        p <= 0.50f -> {
            val f = (p - 0.43f) / 0.07f
            if (hasEnergy) {
                LivingCharacterPose(
                    xNorm = lerpFloat(196f, 185f, f),
                    yNorm = lerpFloat(84f, 72f, f),
                    scaleX = lerpFloat(0.94f, 1.14f, f),
                    scaleY = lerpFloat(1.10f, 0.80f, f),
                    channel = RingChannel.ENERGIA,
                    flashChannel = RingChannel.ENERGIA,
                    flashAlpha = lerpFloat(0.22f, 0.75f, f),
                )
            } else {
                val ch = if (hasMuscle) RingChannel.MUSCULOS else RingChannel.COLUMNA
                val (x, y) = if (hasMuscle) 202f to 138f else 219f to 212f
                LivingCharacterPose(
                    xNorm = x,
                    yNorm = y,
                    scaleX = lerpFloat(0.94f, 1.14f, f),
                    scaleY = lerpFloat(1.10f, 0.80f, f),
                    channel = ch,
                )
            }
        }
        p <= 0.54f -> {
            val f = (p - 0.50f) / 0.04f
            val ch = if (hasEnergy) RingChannel.ENERGIA else if (hasMuscle) RingChannel.MUSCULOS else RingChannel.COLUMNA
            val (x, y) = if (hasEnergy) 185f to 72f else if (hasMuscle) 202f to 138f else 219f to 212f
            LivingCharacterPose(
                xNorm = x,
                yNorm = y,
                scaleX = lerpFloat(1.14f, 1.0f, f),
                scaleY = lerpFloat(0.80f, 1.0f, f),
                channel = ch,
                flashChannel = if (hasEnergy) RingChannel.ENERGIA else null,
                flashAlpha = lerpFloat(0.75f, 0.22f, f),
            )
        }
        // Fase 3: en Energía, saludando con el brazo arriba
        p <= 0.73f -> {
            val arm = when {
                p in 0.60f..0.63f -> lerpFloat(0f, -70f, (p - 0.60f) / 0.03f)
                p in 0.63f..0.66f -> lerpFloat(-70f, -40f, (p - 0.63f) / 0.03f)
                p in 0.66f..0.68f -> lerpFloat(-40f, -70f, (p - 0.66f) / 0.02f)
                p in 0.68f..0.70f -> lerpFloat(-70f, 0f, (p - 0.68f) / 0.02f)
                else -> 0f
            }
            val ch = if (hasEnergy) RingChannel.ENERGIA else if (hasMuscle) RingChannel.MUSCULOS else RingChannel.COLUMNA
            val (x, y) = if (hasEnergy) 185f to 72f else if (hasMuscle) 202f to 138f else 219f to 212f
            val fSquash = if (p >= 0.70f) (p - 0.70f) / 0.03f else 0f
            LivingCharacterPose(
                xNorm = x,
                yNorm = y,
                scaleX = 1f + 0.12f * fSquash,
                scaleY = 1f - 0.22f * fSquash,
                armRotationDeg = arm,
                channel = ch,
            )
        }
        // Fase 4: gran salto de regreso a Columna
        p <= 0.81f -> {
            val f = (p - 0.73f) / 0.08f
            val startX = if (hasEnergy) 185f else if (hasMuscle) 202f else 219f
            val startY = if (hasEnergy) 72f else if (hasMuscle) 138f else 212f
            LivingCharacterPose(
                xNorm = lerpFloat(startX, 256f, f),
                yNorm = lerpFloat(startY, 34f, f),
                scaleX = lerpFloat(1.12f, 0.92f, f),
                scaleY = lerpFloat(0.78f, 1.12f, f),
                channel = RingChannel.ENERGIA,
            )
        }
        p <= 0.93f -> {
            val f = (p - 0.81f) / 0.12f
            val flash = if (f > 0.85f) lerpFloat(0.22f, 0.75f, (f - 0.85f) / 0.15f) else 0.22f
            LivingCharacterPose(
                xNorm = lerpFloat(256f, 219f, f),
                yNorm = lerpFloat(34f, 212f, f),
                scaleX = lerpFloat(0.92f, 1.16f, f),
                scaleY = lerpFloat(1.12f, 0.76f, f),
                channel = RingChannel.COLUMNA,
                flashChannel = if (f > 0.85f) RingChannel.COLUMNA else null,
                flashAlpha = flash,
            )
        }
        else -> {
            val f = (p - 0.93f) / 0.07f
            LivingCharacterPose(
                xNorm = 219f,
                yNorm = 212f,
                scaleX = lerpFloat(1.16f, 1.0f, f),
                scaleY = lerpFloat(0.76f, 1.0f, f),
                channel = RingChannel.COLUMNA,
                flashChannel = RingChannel.COLUMNA,
                flashAlpha = lerpFloat(0.75f, 0.22f, f),
            )
        }
    }
}

/** Pose del personaje durante el salto directo al tocar un anillo (0,55 s). */
internal fun calculateDirectJumpPose(
    progress: Float, // 0..1 en 0,55 s
    targetChannel: RingChannel,
): LivingCharacterPose {
    val (targetX, targetY, startY) = when (targetChannel) {
        RingChannel.COLUMNA -> Triple(219f, 212f, 150f)
        RingChannel.MUSCULOS -> Triple(202f, 138f, 70f)
        RingChannel.ENERGIA -> Triple(185f, 72f, 14f)
    }
    val p = progress.coerceIn(0f, 1f)
    return if (p <= 0.70f) {
        val f = p / 0.70f
        LivingCharacterPose(
            xNorm = targetX,
            yNorm = lerpFloat(startY, targetY, f),
            scaleX = lerpFloat(0.94f, 1.16f, f),
            scaleY = lerpFloat(1.10f, 0.76f, f),
            channel = targetChannel,
        )
    } else {
        val f = (p - 0.70f) / 0.30f
        LivingCharacterPose(
            xNorm = targetX,
            yNorm = targetY,
            scaleX = lerpFloat(1.16f, 1.0f, f),
            scaleY = lerpFloat(0.76f, 1.0f, f),
            channel = targetChannel,
            flashChannel = targetChannel,
            flashAlpha = lerpFloat(0.75f, 0.22f, f),
        )
    }
}

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float =
    start + fraction * (stop - start)

/** Dibuja el personaje KPKN de trazo fino según [pose] escalado al tamaño de la torre. */
internal fun DrawScope.drawLivingCharacter(
    pose: LivingCharacterPose,
    scale: Float,
    alpha: Float = 1f,
) {
    val x = pose.xNorm * scale
    val y = pose.yNorm * scale
    val accentColor = when (pose.channel) {
        RingChannel.COLUMNA -> Color(0xFF8FB2FF)
        RingChannel.MUSCULOS -> Color(0xFFF49A6E)
        RingChannel.ENERGIA -> Color(0xFFF7CF73)
    }

    // Sombra en el suelo (debajo de los pies)
    drawOval(
        color = Color.Black.copy(alpha = 0.55f * alpha),
        topLeft = Offset(x - 7f * scale, y),
        size = Size(14f * scale, 4f * scale),
    )

    withTransform({
        translate(x, y - 2f * scale)
        scale(pose.scaleX, pose.scaleY, pivot = Offset.Zero)
    }) {
        val s = scale
        val ink = Color(0xFFF2EEE6).copy(alpha = alpha)

        // Piernas (flexionadas / rectas): rodillas en x=±1, cadera en x=0, y=-14
        // Pierna izquierda
        drawLine(ink, Offset(-4f * s, 0f), Offset(-1f * s, -9f * s), strokeWidth = 2.6f * s, cap = StrokeCap.Round)
        drawLine(ink, Offset(-1f * s, -9f * s), Offset(0f, -14f * s), strokeWidth = 2.6f * s, cap = StrokeCap.Round)
        // Pierna derecha
        drawLine(ink, Offset(4f * s, 0f), Offset(1f * s, -9f * s), strokeWidth = 2.6f * s, cap = StrokeCap.Round)
        drawLine(ink, Offset(1f * s, -9f * s), Offset(0f, -14f * s), strokeWidth = 2.6f * s, cap = StrokeCap.Round)

        // Tronco
        drawLine(ink, Offset(0f, -14f * s), Offset(0f, -25f * s), strokeWidth = 3f * s, cap = StrokeCap.Round)

        // Brazo izquierdo
        drawLine(ink, Offset(0f, -23f * s), Offset(-6f * s, -16f * s), strokeWidth = 2.4f * s, cap = StrokeCap.Round)

        // Brazo derecho (con saludo si tiene rotación)
        val shoulder = Offset(0f, -23f * s)
        val rad = Math.toRadians(pose.armRotationDeg.toDouble())
        val defaultArmDx = 7f * s
        val defaultArmDy = 6f * s // (y pasa de -23 a -17: dy = +6)
        val rotArmDx = (defaultArmDx * cos(rad) - defaultArmDy * sin(rad)).toFloat()
        val rotArmDy = (defaultArmDx * sin(rad) + defaultArmDy * cos(rad)).toFloat()
        drawLine(ink, shoulder, Offset(shoulder.x + rotArmDx, shoulder.y + rotArmDy), strokeWidth = 2.4f * s, cap = StrokeCap.Round)

        // Cabeza
        drawCircle(ink, radius = 4.4f * s, center = Offset(0f, -30f * s))

        // Arco de la cabeza del color del anillo
        drawArc(
            color = accentColor.copy(alpha = alpha),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(-4.4f * s, -34.4f * s),
            size = Size(8.8f * s, 8.8f * s),
            style = Stroke(width = 2.2f * s, cap = StrokeCap.Round),
        )
    }
}

/** Configuración de un destello orbital de I1. */
private class OrbitFlareDef(
    val channel: RingChannel,
    val cx: Float,
    val cy: Float,
    val rx: Float,
    val ry: Float,
    val durMillis: Int,
    val offsetMillis: Int,
    val color: Color,
    val radius: Float,
    val hasCore: Boolean = true,
)

/** Puntos luminosos que orbitan cada elipse (como I1). */
private val OrbitFlares = listOf(
    // Columna (9 s principal, 13 s secundario)
    OrbitFlareDef(RingChannel.COLUMNA, 150f, 178f, 138f, 40f, 9000, 0, Color(0xFFB9CFFF), 2.2f, hasCore = true),
    OrbitFlareDef(RingChannel.COLUMNA, 150f, 178f, 138f, 40f, 13000, -6000, Color(0xFFB9CFFF), 1.6f, hasCore = false),
    // Músculos (6,5 s principal, 10 s secundario)
    OrbitFlareDef(RingChannel.MUSCULOS, 150f, 112f, 104f, 30f, 6500, -2000, Color(0xFFFFC2A3), 2.2f, hasCore = true),
    OrbitFlareDef(RingChannel.MUSCULOS, 150f, 112f, 104f, 30f, 10000, -7000, Color(0xFFFFC2A3), 1.6f, hasCore = false),
    // Energía (4,2 s la más rápida)
    OrbitFlareDef(RingChannel.ENERGIA, 150f, 54f, 70f, 21f, 4200, -1000, Color(0xFFFFE7A8), 2.2f, hasCore = true),
)

/** Dibuja los puntos luminosos orbitando sobre los anillos calibrados. */
internal fun DrawScope.drawOrbitFlares(
    timeMillis: Float,
    calibratedChannels: Set<RingChannel>,
    scale: Float,
    alpha: Float = 1f,
) {
    for (flare in OrbitFlares) {
        if (flare.channel !in calibratedChannels) continue
        val elapsed = (timeMillis - flare.offsetMillis).mod(flare.durMillis.toFloat())
        val progress = elapsed / flare.durMillis.toFloat()
        val rad = progress * 2.0 * Math.PI
        val cx = flare.cx * scale
        val cy = flare.cy * scale
        val rx = flare.rx * scale
        val ry = flare.ry * scale
        val x = (cx + rx * cos(rad)).toFloat()
        val y = (cy + ry * sin(rad)).toFloat()
        val center = Offset(x, y)

        if (flare.hasCore) {
            // Halo difuminado del color del anillo
            drawCircle(
                color = flare.color.copy(alpha = 0.60f * alpha),
                radius = 5f * scale,
                center = center,
            )
            // Núcleo blanco
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = flare.radius * scale,
                center = center,
            )
        } else {
            // Punto secundario del color del anillo
            drawCircle(
                color = flare.color.copy(alpha = 0.85f * alpha),
                radius = flare.radius * scale,
                center = center,
            )
        }
    }
}

/** Capa Compose con el personaje y las órbitas animadas de Inicio. */
@Composable
fun HomeLivingRingsOverlay(
    calibratedChannels: Set<RingChannel>,
    isAnimating: Boolean,
    modifier: Modifier = Modifier,
    jumpProgress: Float? = null,
    targetChannel: RingChannel? = null,
    forcedPose: LivingCharacterPose? = null,
    forcedTimeMillis: Float? = null,
) {
    val foreground = rememberRingAdjustForeground()
    val reduced = wizardReducedMotion()
    var inViewport by remember { mutableStateOf(false) }
    val active = isAnimating && foreground && inViewport && !reduced
    val transition = rememberInfiniteTransition(label = "homeLivingTransition")
    val hopRaw = if (active && forcedPose == null && jumpProgress == null) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 7500f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 7500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "homeHopTimer",
        )
    } else null

    val orbitRaw = if (active && forcedTimeMillis == null) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 90000f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 90000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "homeOrbitTimer",
        )
    } else null

    val landingChannels = HopRoutePlanner.resolveLandingChannels(calibratedChannels)

    val currentPose = when {
        forcedPose != null -> forcedPose
        !active -> LivingCharacterPose.STATIC_REST
        jumpProgress != null && targetChannel != null -> calculateDirectJumpPose(jumpProgress, targetChannel)
        hopRaw != null -> calculateHopPose(hopRaw.value / 7500f, landingChannels)
        else -> LivingCharacterPose.STATIC_REST
    }

    val currentOrbitTime = forcedTimeMillis ?: orbitRaw?.value ?: 0f

    Canvas(modifier.fillMaxSize().ringAdjustVisible { inViewport = it }) {
        val scale = minOf(size.width / 300f, size.height / 230f).coerceAtLeast(0f)
        if (isAnimating) {
            drawOrbitFlares(
                timeMillis = currentOrbitTime,
                calibratedChannels = calibratedChannels,
                scale = scale,
            )
        }
        drawLivingCharacter(
            pose = currentPose,
            scale = scale,
        )
    }
}

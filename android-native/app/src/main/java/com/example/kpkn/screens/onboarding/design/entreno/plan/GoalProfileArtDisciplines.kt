package com.example.kpkn.screens.onboarding.design.entreno.plan

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import com.example.kpkn.screens.onboarding.design.entreno.DEG
import com.example.kpkn.screens.onboarding.design.entreno.FrontPose
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPen
import com.example.kpkn.screens.onboarding.design.entreno.TAU
import com.example.kpkn.screens.onboarding.design.entreno.dumbbell
import com.example.kpkn.screens.onboarding.design.entreno.frontFigure
import com.example.kpkn.screens.onboarding.design.entreno.ik
import com.example.kpkn.screens.onboarding.design.entreno.riseHoldFall
import com.example.kpkn.screens.onboarding.design.entreno.smooth
import com.example.kpkn.screens.onboarding.design.lerpF
import com.example.kpkn.screens.onboarding.design.seg
import kotlin.math.cos
import kotlin.math.sin

/*
 * Los siete símbolos de disciplina que faltan: halterofilia, armwrestling, strongman, culturismo y powerlifting
 * (el powerbuilding y la calistenia están en `GoalProfileArt.kt`). Misma familia: línea fina, UN acento y un
 * movimiento que explica la disciplina.
 */

// ---------------------------------------------------------------- Halterofilia

/** Arranque: la barra sube del muslo a lo alto, la figura se agacha bajo ella y se levanta. Acento: columna. */
internal class WeightliftingArt(lk: Float) : GoalArt(SymbolPalette.columna, period = 3.4f, restT = 2.0f, lk = lk) {
    /** Altura de la barra: del muslo, al pecho (tirón), a lo alto (recepción), se queda y baja. */
    internal fun barY(t: Float): Float {
        var y = lerpF(37f, 27f, smooth(seg(t, 0.3f, 0.8f)))
        y = lerpF(y, 18f, smooth(seg(t, 0.8f, 1.15f)))
        y = lerpF(y, 11f, smooth(seg(t, 1.15f, 1.85f)))
        return lerpF(y, 37f, smooth(seg(t, 2.5f, 3.1f)))
    }

    /** Altura de los hombros: se estira en el tirón, se hunde en la recepción y se levanta. */
    internal fun shoulderY(t: Float): Float {
        var y = lerpF(24f, 22.5f, smooth(seg(t, 0.3f, 0.8f)))
        y = lerpF(y, 31f, smooth(seg(t, 0.8f, 1.15f)))
        return lerpF(y, 24f, smooth(seg(t, 1.15f, 1.85f)))
    }

    /** Media separación de los pies: se abren en la sentadilla bajo la barra. */
    internal fun stance(t: Float): Float {
        val s = lerpF(4.5f, 9f, smooth(seg(t, 0.8f, 1.15f)))
        return lerpF(s, 4.5f, smooth(seg(t, 1.15f, 1.85f)))
    }

    /** 0 = codos altos (tirón), 1 = codos hacia fuera (brazos bajo la barra): se mezclan las dos soluciones del codo. */
    internal fun elbowOut(t: Float): Float = smooth(seg(t, 0.8f, 1.1f)) - smooth(seg(t, 3.15f, 3.4f))

    override fun drawStatic(pen: SymbolPen) {
        with(pen) { line(10f, 61f, 54f, 61f, soft, fine) }
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) {
        with(pen) {
            val by = barY(t)
            val sy = shoulderY(t)
            val st = stance(t)
            // Barra con discos de goma (acento) a los lados.
            line(2f, by, 62f, by, ink, mid)
            for (m in floatArrayOf(1f, -1f)) {
                val x0 = if (m > 0f) 0f else 64f
                plateBox(x0, by, m, 3f, 8.5f, 9.5f, 2.2f, accent, mid)
                plateBox(x0, by, m, 10f, 13.5f, 6.5f, 1.8f, accent, mid)
            }
            val shL = Offset(27.5f, sy)
            val shR = Offset(36.5f, sy)
            val handL = Offset(18f, by)
            val handR = Offset(46f, by)
            val w = elbowOut(t)
            val hip = Offset(32f, sy + 14f)
            val ftL = Offset(32f - st, 58f)
            val ftR = Offset(32f + st, 58f)
            frontFigure(
                FrontPose(
                    head = Offset(32f, sy - 7.4f),
                    shL = shL, shR = shR,
                    elL = mix(ik(shL, handL, 8.2f, 8.2f, 1f), ik(shL, handL, 8.2f, 8.2f, -1f), w), haL = handL,
                    elR = mix(ik(shR, handR, 8.2f, 8.2f, -1f), ik(shR, handR, 8.2f, 8.2f, 1f), w), haR = handR,
                    hip = hip,
                    knL = ik(hip, ftL, 10.2f, 10.2f, 1f), ftL = ftL,
                    knR = ik(hip, ftR, 10.2f, 10.2f, -1f), ftR = ftR,
                ),
                3.3f, ink, bold,
            )
        }
    }

    private fun mix(a: Offset, b: Offset, k: Float): Offset = Offset(lerpF(a.x, b.x, k), lerpF(a.y, b.y, k))
}

// ---------------------------------------------------------------- Armwrestling

/**
 * Dos brazos de palitos que forman una W sobre la mesa: los codos apoyados, los antebrazos subiendo a un puño unido en el
 * centro y los brazos entrando desde los lados del lienzo (se desvanecen hacia fuera). El pulso se inclina a un lado y a
 * otro. Acento: músculo.
 */
internal class ArmwrestlingArt(lk: Float) : GoalArt(SymbolPalette.musculo, period = 2.4f, restT = 0.5f, lk = lk) {
    private val elbowL = Offset(14.5f, 51.6f)
    private val elbowR = Offset(49.5f, 51.6f)

    /** Desplazamiento lateral del puño unido (unidades): el pulso se inclina a un lado y a otro, nunca del todo. */
    internal fun sway(t: Float): Float = 4.2f * sin(TAU * t / period) + 1.1f * sin(TAU * 3f * t / period + 0.7f)

    override fun drawStatic(pen: SymbolPen) {
        with(pen) {
            // La mesa y, debajo, su canto; los brazos entran por los lados y se pierden fuera del lienzo.
            line(1.5f, 57.8f, 62.5f, 57.8f, ink, bold)
            line(8f, 61.4f, 56f, 61.4f, soft, fine)
            fadedLine(elbowL, Offset(0.5f, 31f), ink)
            fadedLine(elbowR, Offset(63.5f, 31f), accent)
        }
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) {
        with(pen) {
            val s = sway(t)
            val ax = 32f + s
            val ay = 20.5f - 0.35f * kotlin.math.abs(s)
            // Los antebrazos: del codo a las esquinas de abajo del puño; el de la derecha, en el acento.
            line(elbowL.x, elbowL.y, ax - 5.4f, ay + 5.4f, ink, bold)
            line(elbowR.x, elbowR.y, ax + 5.4f, ay + 5.4f, accent, bold)
            // Los codos, apoyados en la mesa.
            dot(elbowL.x, elbowL.y, dotR(1.9f), ink)
            dot(elbowR.x, elbowR.y, dotR(1.9f), accent)
            // El puño: dos manos unidas, con la línea donde se juntan y los nudillos.
            rotated(s * 1.5f, ax, ay) {
                box(ax - FIST_RX, ay - FIST_RY, ax + FIST_RX, ay + FIST_RY, FIST_R, ink, mid)
                curve(ax - 0.4f, ay - FIST_RY, ax + 2.2f, ay, ax - 0.4f, ay + FIST_RY, accent, fine)
                for (k in 0..2) arc(ax - 5.6f + k * 2.5f, ay - FIST_RY, 1.25f, 180f, 180f, soft, fine)
            }
            // El esfuerzo: unas rayas que parpadean sobre el puño (solo seleccionado).
            for (i in 0..3) {
                val a = (-132f + i * 28f) * DEG
                val flick = 0.45f + 0.55f * (0.5f + 0.5f * sin(t * 13f + i * 1.9f))
                fade(flick) {
                    line(
                        ax + cos(a) * 11.5f, ay + sin(a) * 9.5f, ax + cos(a) * 15f, ay + sin(a) * 12.6f,
                        motion, fine,
                    )
                }
            }
        }
    }

    /**
     * Un trazo que se va desvaneciendo de [a] a [b] (el brazo se pierde fuera del lienzo): UN solo trazo con un degradado de
     * opacidad a lo largo. Con varios tramos translúcidos, los remates redondos dejan una cuenta en cada unión (se montan o
     * se tocan de punta); con un degradado no hay uniones.
     */
    private fun SymbolPen.fadedLine(a: Offset, b: Offset, color: Color) {
        val head = color.copy(alpha = color.alpha * ga)
        val tail = head.copy(alpha = head.alpha * ARM_FADE_END)
        ds.drawLine(
            brush = Brush.linearGradient(listOf(head, tail), start = a, end = b),
            start = a,
            end = b,
            strokeWidth = bold,
            cap = StrokeCap.Round,
        )
    }

    private companion object {
        const val FIST_RX = 7.8f
        const val FIST_RY = 5.8f
        const val FIST_R = 4.4f

        /** Cuánta opacidad le queda al brazo en el borde del lienzo. */
        const val ARM_FADE_END = 0.18f
    }
}

// ---------------------------------------------------------------- Strongman

/** La forma de la piedra: nueve puntos a distinta distancia del centro, unidos con curvas (una roca, no una pelota). */
private val STONE_RADII = floatArrayOf(1.0f, 0.93f, 1.04f, 0.95f, 1.02f, 0.91f, 1.05f, 0.96f, 1.0f)

/**
 * Una piedra de atlas que se levanta y vuelve a caer, y un cargador con un yugo a los hombros que avanza a pasos.
 * Acento: energía.
 */
internal class StrongmanArt(lk: Float) : GoalArt(SymbolPalette.energia, period = 3.0f, restT = 0.9f, lk = lk) {
    private val stoneX = 17f
    private val stoneR = 13f
    private val ground = 58.8f
    private val stone = Path()

    init {
        // El contorno de la piedra, con el centro en el origen: se traslada y se gira al dibujarla.
        val n = STONE_RADII.size
        fun px(i: Int) = cos(TAU * (i % n) / n) * stoneR * STONE_RADII[i % n]
        fun py(i: Int) = sin(TAU * (i % n) / n) * stoneR * STONE_RADII[i % n]
        stone.moveTo((px(0) + px(1)) / 2f, (py(0) + py(1)) / 2f)
        for (i in 1..n) {
            stone.quadraticTo(px(i), py(i), (px(i) + px(i + 1)) / 2f, (py(i) + py(i + 1)) / 2f)
        }
        stone.close()
    }

    /** 0 = la piedra en el suelo, 1 = arriba. */
    internal fun lift(t: Float): Float = riseHoldFall(t, 0.3f, 1.2f, 1.7f, 2.5f)

    /** El paso del cargador, de −1 a 1: positivo levanta el pie izquierdo y negativo el derecho (tres pasos por bucle). */
    internal fun step(t: Float): Float = sin(TAU * t / (period / 3f))

    override fun drawStatic(pen: SymbolPen) {
        with(pen) { line(1.5f, ground, 62.5f, ground, soft, fine) }
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) {
        with(pen) {
            val l = lift(t)
            // Piedra: sombra, contorno en el acento, brillo y grietas que giran un poco al subir, y polvo al caer.
            val cy = ground - 0.3f - stoneR - 9.5f * l
            oval(stoneX, ground + 0.6f, 11.5f - 4.5f * l, 1.4f, soft, fine)
            drawStone(cy, l)
            val dust = seg(t, 2.45f, 3.0f)
            if (dust > 0f && dust < 1f) {
                fade(1f - dust) {
                    for (m in floatArrayOf(-1f, 1f)) {
                        dot(stoneX + m * (stoneR + 3f + 6f * dust), ground - 1.5f - 4f * dust, dotR(1.2f), soft)
                        dot(stoneX + m * (stoneR + 1f + 4f * dust), ground - 0.9f - 1.4f * dust, dotR(0.9f), soft)
                    }
                }
            }
            carrier(step(t))
        }
    }

    private fun SymbolPen.drawStone(cy: Float, l: Float) {
        shifted(stoneX, cy) {
            rotated(38f * l, 0f, 0f) {
                path(stone, accent, mid)
                // Brillo (un arco corto paralelo al borde) y dos grietas.
                arc(0f, 0f, stoneR * 0.7f, 195f, 55f, ink, fine)
                poly3(stoneR * 0.95f, -2.4f, 4.4f, 0.6f, 6.6f, 5.4f, ink, fine)
                poly3(-2.2f, stoneR * 0.9f, -1.6f, 4.8f, -6.4f, 2.6f, ink, fine)
                dot(-5.6f, -3.4f, dotR(0.9f), soft)
                dot(2.4f, -7.4f, dotR(0.9f), soft)
            }
        }
    }

    /** El cargador (de frente, con el yugo a los hombros y las manos en los postes) y el marco del yugo. */
    private fun SymbolPen.carrier(st: Float) {
        val bob = 0.7f * kotlin.math.abs(st)
        val cx = 49f
        val beamY = 25.2f - bob
        // Yugo: dos postes que se abren hacia el suelo y el travesaño, que apoya en los hombros.
        line(40.6f, beamY, 38.8f, ground, ink, mid)
        line(57.4f, beamY, 59.2f, ground, ink, mid)
        line(36.4f, ground, 41.4f, ground, ink, mid)
        line(56.6f, ground, 61.6f, ground, ink, mid)
        line(35.4f, beamY, 62.6f, beamY, accent, bold)
        dot(35.4f, beamY, dotR(1.5f), accent)
        dot(62.6f, beamY, dotR(1.5f), accent)
        // Figura: cabeza sobre el travesaño, brazos a los postes y piernas que dan pasos alternos.
        val shL = Offset(cx - 4.2f, 27.4f - bob)
        val shR = Offset(cx + 4.2f, 27.4f - bob)
        val hip = Offset(cx, 41.6f - bob)
        val ftL = Offset(cx - 3.4f, ground - 0.8f - 3.6f * maxOf(0f, st))
        val ftR = Offset(cx + 3.4f, ground - 0.8f - 3.6f * maxOf(0f, -st))
        val haL = Offset(40.6f + 0.6f, 32.4f - 0.5f * bob)
        val haR = Offset(57.4f - 0.6f, 32.4f - 0.5f * bob)
        frontFigure(
            FrontPose(
                head = Offset(cx, 18.6f - bob),
                shL = shL, shR = shR,
                elL = ik(shL, haL, 5.2f, 5.2f, 1f), haL = haL,
                elR = ik(shR, haR, 5.2f, 5.2f, -1f), haR = haR,
                hip = hip,
                knL = ik(hip, ftL, 8.9f, 8.9f, 1f), ftL = ftL,
                knR = ik(hip, ftR, 8.9f, 8.9f, -1f), ftR = ftR,
            ),
            3.3f, ink, mid * 1.15f,
        )
    }
}

// ---------------------------------------------------------------- Culturismo

/** Una espalda en V que se abre mientras sube una mancuerna a su lado. Acento: mente. */
internal class BodybuildingArt(lk: Float) : GoalArt(SymbolPalette.mente, period = 3.0f, restT = 1.0f, lk = lk) {
    private val spineX = 22.5f

    /** 0 = relajada, 1 = la espalda abierta y la mancuerna arriba. */
    internal fun flare(t: Float): Float = riseHoldFall(t, 0.3f, 1.0f, 1.8f, 2.6f)

    override fun drawStatic(pen: SymbolPen) {
        with(pen) {
            ring(spineX, 9f, 4.7f, ink, mid)
            line(spineX, 15.5f, spineX, 55f, soft, fine)
        }
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) {
        with(pen) {
            val fl = flare(t)
            half(-1f, fl)
            half(1f, fl)
            dumbbell(53.5f, 39f - 5f * fl, 12.5f, 90f - 18f * fl, accent, ink, mid)
        }
    }

    /** Una mitad de la espalda (−1 izquierda, +1 derecha): cuello, trapecio, hombro, dorsal y cintura. */
    private fun SymbolPen.half(m: Float, fl: Float) {
        fun x(dx: Float) = spineX + m * dx
        val sx = 15.2f + 1.5f * fl
        // Cuello y trapecio.
        line(x(2.3f), 13.4f, x(2.5f), 16.6f, ink, mid)
        curve(x(2.5f), 16.6f, x(9f), 16.9f - 0.6f * fl, x(sx - 0.6f), 21.4f - 0.9f * fl, ink, mid)
        // Deltoides.
        curve(x(sx - 0.6f), 21.4f - 0.9f * fl, x(sx + 3.6f), 21.8f - 0.9f * fl, x(sx + 2.4f), 28f, ink, mid)
        // Dorsal: del hombro a la cintura, más abierto con el movimiento.
        curve(x(sx - 0.2f), 26f, x(14.6f + 3.6f * fl), 37f, x(7.4f), 47.5f, ink, mid)
        curve(x(7.4f), 47.5f, x(7.9f), 51.5f, x(9.2f), 55.6f, ink, mid)
        // Los músculos de dentro, en el acento.
        curve(x(2.2f), 21.4f, x(8.6f), 21.4f, x(9.6f), 28.6f, accent, fine)
        curve(x(3.2f), 39f, x(8f), 36.4f, x(12.4f), 30.2f - 1.4f * fl, accent, fine)
    }
}

// ---------------------------------------------------------------- Powerlifting

/**
 * Una barra con discos enormes (vistos en perspectiva, como elipses) que se levanta despacio, tiembla al final del
 * esfuerzo y se curva bajo el peso. Acento: músculo.
 */
internal class PowerliftingArt(lk: Float) : GoalArt(SymbolPalette.musculo, period = 3.6f, restT = 1.3f, lk = lk) {
    /** 0 = barra en el suelo, 1 = arriba: sube lento (un levantamiento pesado) y baja controlada. */
    internal fun lift(t: Float): Float = riseHoldFall(t, 0.3f, 1.8f, 2.4f, 3.3f)

    /** Temblor del esfuerzo: solo en la última parte de la subida y en el arriba. */
    internal fun tremor(t: Float): Float = 0.45f * sin(t * 52f) * seg(t, 1.2f, 1.7f) * (1f - seg(t, 2.3f, 2.5f))

    internal fun barY(t: Float): Float = lerpF(35f, 28.5f, lift(t)) + tremor(t)

    override fun drawStatic(pen: SymbolPen) {
        with(pen) { line(5f, 60.8f, 59f, 60.8f, soft, fine) }
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) {
        with(pen) {
            val y = barY(t)
            val f = 1.7f * lift(t)
            curve(2f, y + f, 32f, y - f, 62f, y + f, ink, mid)
            for (m in floatArrayOf(1f, -1f)) {
                val x0 = if (m > 0f) 0f else 64f
                stackEllipse(x0, m, 10f, 3.8f, 25f, y, f, accent)
                stackEllipse(x0, m, 16f, 3.2f, 20.5f, y, f, ink)
                stackEllipse(x0, m, 21.2f, 2.7f, 16f, y, f, ink)
                // Collarín.
                val cx = x0 + m * 24.4f
                val cy = sagY(cx, 2f, 62f, y, f)
                plateBox(cx, cy, 1f, -0.9f, 0.9f, 3.2f, 0.8f, accent, mid)
            }
        }
    }

    /** Un disco: elipse estrecha y alta centrada en la barra, con el buje del eje. */
    private fun SymbolPen.stackEllipse(
        x0: Float, m: Float, dx: Float, rx: Float, ry: Float, y: Float, f: Float, color: androidx.compose.ui.graphics.Color,
    ) {
        val cx = x0 + m * dx
        val cy = sagY(cx, 2f, 62f, y, f)
        oval(cx, cy, rx, ry, color, mid)
        if (dx < 11f) dot(cx, cy, dotR(1.4f), ink)
    }
}

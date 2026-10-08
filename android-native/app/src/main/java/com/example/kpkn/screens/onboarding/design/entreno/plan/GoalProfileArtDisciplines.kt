package com.example.kpkn.screens.onboarding.design.entreno.plan

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import com.example.kpkn.screens.onboarding.design.entreno.DEG
import com.example.kpkn.screens.onboarding.design.entreno.FrontPose
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPen
import com.example.kpkn.screens.onboarding.design.entreno.TAU
import com.example.kpkn.screens.onboarding.design.entreno.dumbbell
import com.example.kpkn.screens.onboarding.design.entreno.fainter
import com.example.kpkn.screens.onboarding.design.entreno.frontFigure
import com.example.kpkn.screens.onboarding.design.entreno.ik
import com.example.kpkn.screens.onboarding.design.entreno.riseHoldFall
import com.example.kpkn.screens.onboarding.design.entreno.smooth
import com.example.kpkn.screens.onboarding.design.lerpF
import com.example.kpkn.screens.onboarding.design.seg
import kotlin.math.abs
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

/** Alto del tablero de la mesa, y dónde van las clavijas de sus extremos. */
private const val TABLE_Y = 57.2f
private const val PEG_LEFT = 2.8f
private const val PEG_RIGHT = 61.2f

/** Dónde están los codos, apoyados en la mesa, y por dónde entra cada brazo al lienzo (el brazo descansa a lo largo del tablero). */
private val ARM_ELBOW_LEFT = Offset(19f, 52.5f)
private val ARM_ELBOW_RIGHT = Offset(45f, 52.5f)
private val ARM_START_LEFT = Offset(9f, 52.5f)
private val ARM_START_RIGHT = Offset(55f, 52.5f)

/** Ancho del brazo (entre los dos trazos de su contorno). */
private const val ARM_TUBE = 6f

/**
 * Pulso sobre la mesa, de lado: dos brazos dibujados como tubos —el brazo descansa a lo largo de la mesa, el codo se apoya en ella y
 * el antebrazo sube— que se unen en un puño de dos manos, entre las clavijas del tablero. El pulso se inclina a un lado y a otro
 * (el puño y los antebrazos lo siguen) y unas rayas de esfuerzo parpadean sobre las manos. El brazo de la derecha y la unión de las
 * manos llevan el acento (músculo).
 */
internal class ArmwrestlingArt(lk: Float) : GoalArt(SymbolPalette.musculo, period = 2.4f, restT = 1.2f, lk = lk) {
    private val arm = Path()

    /** Desplazamiento lateral del puño unido (unidades): el pulso se inclina a un lado y a otro, nunca del todo. */
    internal fun sway(t: Float): Float = 4.0f * sin(TAU * t / period) + 0.9f * sin(TAU * 3f * t / period + 0.7f)

    override fun drawStatic(pen: SymbolPen) {
        with(pen) {
            // El tablero, su canto y las clavijas de los extremos de la mesa.
            line(1.5f, TABLE_Y, 62.5f, TABLE_Y, ink, bold)
            line(11f, TABLE_Y + 3.6f, 53f, TABLE_Y + 3.6f, soft, fine)
            line(PEG_LEFT, TABLE_Y - 9.5f, PEG_LEFT, TABLE_Y, soft, mid)
            line(PEG_RIGHT, TABLE_Y - 9.5f, PEG_RIGHT, TABLE_Y, soft, mid)
        }
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) {
        with(pen) {
            val s = sway(t)
            val ax = 32f + s
            val ay = 19.5f - 0.35f * abs(s)
            // Los brazos: de la mesa al codo y del codo a la muñeca, que cae bajo el puño.
            tube(ARM_START_LEFT, ARM_ELBOW_LEFT, Offset(ax - 4.6f, ay + 7f), ink)
            tube(ARM_START_RIGHT, ARM_ELBOW_RIGHT, Offset(ax + 4.6f, ay + 7f), accent)
            // El puño: dos manos unidas, con la línea donde se juntan y los dedos de una sobre la otra.
            rotated(s * 1.5f, ax, ay) {
                fillBox(ax - FIST_RX, ay - FIST_RY, ax + FIST_RX, ay + FIST_RY, FIST_R, Color.Black)
                box(ax - FIST_RX, ay - FIST_RY, ax + FIST_RX, ay + FIST_RY, FIST_R, ink, mid)
                curve(ax - 0.6f, ay - FIST_RY, ax + 2.6f, ay, ax - 0.6f, ay + FIST_RY, accent, mid)
                for (k in 0..2) {
                    line(ax + 2.4f + k * 2.3f, ay - 3.4f, ax + 1.2f + k * 2.3f, ay + 3.6f, if (k == 1) accent else soft, fine)
                }
            }
            // El esfuerzo: unas rayas que parpadean sobre el puño (solo seleccionado).
            for (i in 0..3) {
                val a = (-132f + i * 28f) * DEG
                val flick = 0.45f + 0.55f * (0.5f + 0.5f * sin(t * 13f + i * 1.9f))
                fade(flick) {
                    line(
                        ax + cos(a) * 13.8f, ay + sin(a) * 11.2f, ax + cos(a) * 17.2f, ay + sin(a) * 14.2f,
                        motion, fine,
                    )
                }
            }
        }
    }

    /**
     * Un brazo como tubo hueco: el recorrido ([from] → [elbow] → [wrist]) trazado dos veces, ancho con el color del brazo y, encima,
     * más estrecho con el negro de la página. El codo y el extremo salen redondos; el contorno es de línea fina.
     */
    private fun SymbolPen.tube(from: Offset, elbow: Offset, wrist: Offset, color: Color) {
        arm.reset()
        arm.moveTo(from.x, from.y)
        arm.lineTo(elbow.x, elbow.y)
        arm.lineTo(wrist.x, wrist.y)
        path(arm, color, ARM_TUBE + fine)
        path(arm, Color.Black, ARM_TUBE - fine)
    }

    private companion object {
        const val FIST_RX = 9f
        const val FIST_RY = 6.6f
        const val FIST_R = 5f
    }
}

// ---------------------------------------------------------------- Strongman

/** La forma de la roca: ocho puntos a distinta distancia del centro, unidos con rectas (una roca angulosa, no una pelota). */
private val STONE_RADII = floatArrayOf(1.02f, 0.86f, 1.07f, 0.9f, 1.04f, 0.85f, 1.08f, 0.93f)

/** Radio de la roca, suelo del lienzo y medidas de la figura (piernas, tronco, brazos y cabeza) del cargador. */
private const val STONE_R = 11f
private const val STRONG_GROUND = 59f
private const val STRONG_THIGH = 9.6f
private const val STRONG_TORSO = 13.5f
private const val STRONG_ARM = 7.2f
private const val STRONG_HEAD_R = 3.3f

/**
 * Una piedra de atlas que se levanta del suelo hasta el pecho y se baja otra vez: la figura de perfil se agacha, la abraza,
 * se endereza echándose hacia atrás con ella y la deja caer en el polvo. La roca tapa el brazo lejano. En reposo muestra el
 * momento más reconocible, con la piedra arriba. Acento: energía (la roca).
 */
internal class StrongmanArt(lk: Float) : GoalArt(SymbolPalette.energia, period = 3.2f, restT = 1.55f, lk = lk) {
    private val stone = Path()

    init {
        // El contorno de la roca, con el centro en el origen: se traslada y se gira al dibujarla.
        val n = STONE_RADII.size
        for (i in 0 until n) {
            val a = TAU * i / n + 0.35f
            val x = cos(a) * STONE_R * STONE_RADII[i]
            val y = sin(a) * STONE_R * STONE_RADII[i]
            if (i == 0) stone.moveTo(x, y) else stone.lineTo(x, y)
        }
        stone.close()
    }

    /** 0 = la piedra en el suelo, 1 = arriba, a la altura del pecho. */
    internal fun lift(t: Float): Float = riseHoldFall(t, 0.4f, 1.3f, 1.75f, 2.55f)

    /** El centro de la piedra: del suelo (a la derecha) a lo alto del pecho (algo más cerca de la figura). */
    internal fun stoneCenter(t: Float): Offset {
        val k = lift(t)
        val low = STRONG_GROUND - 0.3f - STONE_R
        return Offset(38f - 4f * k, low - (low - 29f) * k)
    }

    override fun drawStatic(pen: SymbolPen) {
        with(pen) { line(1.5f, STRONG_GROUND, 62.5f, STRONG_GROUND, soft, fine) }
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) {
        with(pen) {
            val k = lift(t)
            val c = stoneCenter(t)
            // La figura: de cuclillas con el tronco casi tumbado hacia la piedra a erguida y echada hacia atrás.
            val hip = Offset(lerpF(12f, 15.5f, k), lerpF(48.5f, 41f, k))
            val lean = lerpF(0.95f, -0.10f, k)
            val shoulder = hip + Offset(sin(lean), -cos(lean)) * STRONG_TORSO
            val head = shoulder + Offset(sin(lean + 0.12f), -cos(lean + 0.12f)) * 5.6f
            val footN = Offset(hip.x + lerpF(6.5f, 3.5f, k), STRONG_GROUND - 0.4f)
            val footF = Offset(hip.x - lerpF(2f, 4.5f, k), STRONG_GROUND - 0.4f)
            val kneeN = ik(hip, footN, STRONG_THIGH, STRONG_THIGH, -1f)
            val kneeF = ik(hip, footF, STRONG_THIGH, STRONG_THIGH, -1f)
            // Las manos abrazan la piedra por abajo y por el lado de la figura.
            val handN = Offset(c.x - STONE_R * 0.62f, c.y + STONE_R * 0.52f)
            val handF = Offset(c.x - STONE_R * 0.30f, c.y + STONE_R * 0.86f)
            val elbowN = ik(shoulder, handN, STRONG_ARM, STRONG_ARM, 1f)
            val elbowF = ik(shoulder, handF, STRONG_ARM, STRONG_ARM, 1f)
            val far = ink.fainter(SymbolPalette.FAR_LIMB)
            // Lado lejano, tronco, lado cercano y cabeza (con el negro de la página detrás, como las demás figuras).
            poly(shoulder, elbowF, handF, far, bold)
            poly(hip, kneeF, footF, far, bold)
            line(hip, shoulder, ink, bold)
            poly(hip, kneeN, footN, ink, bold)
            dot(head.x, head.y, STRONG_HEAD_R + 0.3f, Color.Black)
            ring(head.x, head.y, STRONG_HEAD_R, ink, bold * 0.9f)
            // La roca (tapa el brazo lejano): contorno en el acento, brillo y dos grietas; gira un poco al subir.
            shifted(c.x, c.y) {
                rotated(34f * k, 0f, 0f) {
                    fillPath(stone, Color.Black)
                    path(stone, accent, mid)
                    arc(0f, 0f, STONE_R * 0.68f, 195f, 55f, ink, fine)
                    poly3(STONE_R * 0.95f, -2.4f, 4.4f, 0.6f, 6.6f, 5.4f, ink, fine)
                    poly3(-2.2f, STONE_R * 0.9f, -1.6f, 4.8f, -6.4f, 2.6f, ink, fine)
                }
            }
            // El brazo cercano va por delante de la roca, con la mano agarrándola.
            poly(shoulder, elbowN, handN, ink, bold)
            dot(handN.x, handN.y, dotR(1.6f), ink)
            // Polvo al caer.
            val dust = seg(t, 2.45f, 3.0f)
            if (dust > 0f && dust < 1f) {
                fade(1f - dust) {
                    val reach = STONE_R + 3f + 6f * dust
                    val y = STRONG_GROUND - 1.5f - 4f * dust
                    dot(c.x - reach, y, dotR(1.2f), soft)
                    dot(c.x + reach, y, dotR(1.2f), soft)
                }
            }
        }
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

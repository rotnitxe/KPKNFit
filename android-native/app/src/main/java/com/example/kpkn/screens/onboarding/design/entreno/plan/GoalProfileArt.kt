package com.example.kpkn.screens.onboarding.design.entreno.plan

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.screens.onboarding.design.entreno.DEG
import com.example.kpkn.screens.onboarding.design.entreno.FrontPose
import com.example.kpkn.screens.onboarding.design.entreno.SymbolArt
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPen
import com.example.kpkn.screens.onboarding.design.entreno.SymbolStroke
import com.example.kpkn.screens.onboarding.design.entreno.TAU
import com.example.kpkn.screens.onboarding.design.entreno.dumbbell
import com.example.kpkn.screens.onboarding.design.entreno.fainter
import com.example.kpkn.screens.onboarding.design.entreno.frontFigure
import com.example.kpkn.screens.onboarding.design.entreno.ik
import com.example.kpkn.screens.onboarding.design.entreno.riseHoldFall
import com.example.kpkn.screens.onboarding.design.entreno.smooth
import com.example.kpkn.screens.onboarding.design.lerpF
import com.example.kpkn.screens.onboarding.design.seg
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * Los símbolos del paso «¿Cuál es tu objetivo?»: uno por perfil (3 generales y 7 disciplinas), de la misma familia
 * que los de lugar y material (`design/entreno`): línea fina de extremos redondos, tinta cálida sobre negro y UN
 * acento. Sin seleccionar son un cuadro estático tenue; seleccionado se encienden con el acento de su disciplina y
 * se mueven en bucle. Lienzo lógico de 64 × 64 unidades.
 *
 * El mismo dibujo sirve en dos tamaños: el símbolo de la lista (≈ 56 dp) y la ilustración grande de la portada
 * (≈ 200 dp). Por eso cada símbolo recibe una escala de línea [GoalArt.lk]: 1 en la lista y menos en la portada,
 * para que el trazo no engorde al agrandar el dibujo.
 */

/** Escala del grosor de línea de las ilustraciones de portada respecto al símbolo de la lista. */
internal const val COVER_LINE_SCALE = 0.46f

/** Base de los símbolos de objetivo: lienzo de 64 × 64 y grosores escalados por [lk]. */
internal abstract class GoalArt(
    override val accent: Color,
    override val period: Float,
    override val restT: Float,
    protected val lk: Float,
) : SymbolArt {
    override val width: Float get() = 64f
    override val height: Float get() = 64f

    /** Detalles finos, trazo común y trazo de cuerpo, ya escalados. */
    protected val fine: Float get() = SymbolStroke.FINE * lk
    protected val mid: Float get() = SymbolStroke.LINE * lk
    protected val bold: Float get() = SymbolStroke.BODY * lk

    /** Radio de un punto (la mano, el buje): crece menos que el dibujo al agrandarlo para que no se vea un bulto. */
    protected fun dotR(base: Float): Float = base * (0.55f + 0.45f * lk)
}

// ---------------------------------------------------------------- fábrica

private val LIST_ARTS: Map<TrainingGoalProfile, GoalArt> by lazy {
    TrainingGoalProfile.entries.associateWith { buildGoalArt(it, 1f) }
}
private val COVER_ARTS: Map<TrainingGoalProfile, GoalArt> by lazy {
    TrainingGoalProfile.entries.associateWith { buildGoalArt(it, COVER_LINE_SCALE) }
}

/** El símbolo de un perfil, del tamaño de la lista. */
internal fun goalArt(profile: TrainingGoalProfile): GoalArt = LIST_ARTS.getValue(profile)

/** La ilustración de un perfil para la portada (línea más fina); sin perfil, la de «Fuerza y masa muscular». */
internal fun goalCoverArt(profile: TrainingGoalProfile?): GoalArt =
    COVER_ARTS.getValue(profile ?: TrainingGoalProfile.STRENGTH_MUSCLE)

private fun buildGoalArt(profile: TrainingGoalProfile, lk: Float): GoalArt = when (profile) {
    TrainingGoalProfile.STRENGTH_MUSCLE -> StrengthMuscleArt(lk)
    TrainingGoalProfile.STRENGTH_CARDIO -> StrengthCardioArt(lk)
    TrainingGoalProfile.FUNCTIONAL_HEALTH -> FunctionalArt(lk)
    TrainingGoalProfile.POWERBUILDING -> PowerbuildingArt(lk)
    TrainingGoalProfile.CALISTHENICS -> CalisthenicsArt(lk)
    TrainingGoalProfile.BODYBUILDING -> BodybuildingArt(lk)
    TrainingGoalProfile.WEIGHTLIFTING -> WeightliftingArt(lk)
    TrainingGoalProfile.ARMWRESTLING -> ArmwrestlingArt(lk)
    TrainingGoalProfile.STRONGMAN -> StrongmanArt(lk)
    TrainingGoalProfile.POWERLIFTING -> PowerliftingArt(lk)
}

// ---------------------------------------------------------------- piezas comunes de estos símbolos

/** Una caja de disco vista de perfil, medida desde un pivote: `[a, b]` en el sentido [m] (+1 izquierda, −1 derecha). */
internal fun SymbolPen.plateBox(
    px: Float, py: Float, m: Float, a: Float, b: Float, halfH: Float, rad: Float, color: Color, w: Float,
) {
    val x1 = px + m * a
    val x2 = px + m * b
    box(min(x1, x2), py - halfH, max(x1, x2), py + halfH, rad, color, w)
}

/** Altura de una curva cuadrática simétrica (extremos a `y + f`, centro a `y`) en la abscisa [x] de `[x0, x1]`. */
internal fun sagY(x: Float, x0: Float, x1: Float, y: Float, f: Float): Float {
    val s = ((x - x0) / (x1 - x0)).coerceIn(0f, 1f)
    return y + f * (1f - 4f * s * (1f - s))
}

/** Inclinación en grados de esa misma curva en la abscisa [x] (positiva = baja hacia la derecha). */
internal fun sagDeg(x: Float, x0: Float, x1: Float, f: Float): Float {
    val s = ((x - x0) / (x1 - x0)).coerceIn(0f, 1f)
    val slope = -4f * f * (1f - 2f * s) / (x1 - x0)
    return atan2(slope, 1f) / DEG
}

// ---------------------------------------------------------------- Fuerza y masa muscular

/** Una barra cargada que sube y se curva bajo el peso. General: tinta con el detalle en verde (collarines y rayado). */
internal class StrengthMuscleArt(lk: Float) : GoalArt(SymbolPalette.ok, period = 2.6f, restT = 0.8f, lk = lk) {
    /** 0 = barra abajo, 1 = arriba. */
    internal fun lift(t: Float): Float = riseHoldFall(t, 0.3f, 1.1f, 1.5f, 2.3f)

    internal fun barY(t: Float): Float = lerpF(44f, 24f, lift(t))

    override fun drawStatic(pen: SymbolPen) {
        with(pen) { line(6f, 61.5f, 58f, 61.5f, soft, fine) }
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) {
        with(pen) {
            val y = barY(t)
            val f = 2.3f * lift(t)
            curve(2f, y + f, 32f, y - f, 62f, y + f, ink, mid)
            stack(16f, 1f, y, f)
            stack(48f, -1f, y, f)
            // El rayado de la barra, en el acento.
            for (x in floatArrayOf(29.2f, 32f, 34.8f)) line(x, y - 2.3f, x, y + 2.3f, accent, fine)
        }
    }

    /** Los tres discos y el collarín de un extremo, inclinados con la barra. */
    private fun SymbolPen.stack(px: Float, m: Float, y: Float, f: Float) {
        val py = sagY(px, 2f, 62f, y, f)
        rotated(sagDeg(px, 2f, 62f, f), px, py) {
            plateBox(px, py, m, 4f, 9.5f, 15f, 2.2f, ink, mid)
            plateBox(px, py, m, -2f, 2.5f, 11.5f, 2f, ink, mid)
            plateBox(px, py, m, -7f, -3.5f, 8f, 1.6f, ink, mid)
            plateBox(px, py, m, -10.5f, -8.5f, 3.6f, 1f, accent, mid)
        }
    }
}

// ---------------------------------------------------------------- Fuerza y cardio

private val KETTLEBELL_HANDLE: Path by lazy {
    Path().apply {
        moveTo(-5.5f, 13.2f)
        lineTo(-5.5f, 4.8f)
        quadraticTo(-5.5f, 0f, -1.5f, 0f)
        lineTo(1.5f, 0f)
        quadraticTo(5.5f, 0f, 5.5f, 4.8f)
        lineTo(5.5f, 13.2f)
    }
}

/** Híbrido: una kettlebell que se balancea sobre una barra cargada en el suelo. General: la kettlebell en verde. */
internal class StrengthCardioArt(lk: Float) : GoalArt(SymbolPalette.ok, period = 3.6f, restT = 0f, lk = lk) {
    private val px = 32f
    private val py = 3.5f
    private val bodyCy = 22f
    private val bodyR = 10.5f
    private val baseDy = 9.6f

    /** Ángulo del balanceo en grados (dos balanceos por bucle). */
    internal fun swingDeg(t: Float): Float = 29f * sin(TAU * t / (period / 2f))

    override fun drawStatic(pen: SymbolPen) {
        with(pen) {
            line(2f, 50f, 62f, 50f, ink, mid)
            for (m in floatArrayOf(1f, -1f)) {
                val x0 = if (m > 0f) 0f else 64f
                plateBox(x0, 50f, m, 17.5f, 23f, 11f, 2.2f, ink, mid)
                plateBox(x0, 50f, m, 12f, 15.5f, 7.5f, 1.8f, ink, mid)
                plateBox(x0, 50f, m, 8f, 10.5f, 4.5f, 1.4f, ink, mid)
            }
            line(4f, 63f, 60f, 63f, soft, fine)
        }
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) {
        with(pen) {
            // La mano (solo seleccionado), la estela del balanceo y la kettlebell colgando.
            dot(px, py - 1.6f, dotR(1.7f), motion)
            arc(px, py, 31f, 90f - 36f, 72f, motion, fine)
            shifted(px, py) {
                rotated(-swingDeg(t), 0f, 0f) {
                    path(KETTLEBELL_HANDLE, ink, mid)
                    val half = kotlin.math.acos(baseDy / bodyR) / DEG
                    arc(0f, bodyCy, bodyR, 90f + half, 360f - 2f * half, accent, mid)
                    val chord = bodyR * sin(half * DEG)
                    line(-chord, bodyCy + baseDy, chord, bodyCy + baseDy, accent, mid)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Funcional y saludable

/** Plantilla de un latido del electrocardiograma (14 unidades de ancho, relativa a la línea base). */
private val ECG_BEAT = floatArrayOf(
    0f, 0f, 2.5f, 0f, 3.3f, -0.9f, 4.1f, 0f, 5f, 0f, 5.6f, 1.2f, 6.4f, -6.5f, 7.2f, 2.6f, 8f, 0f,
    9.5f, 0f, 10.4f, -1.5f, 11.3f, 0f, 14f, 0f,
)

/** El trazo del pulso, calculado una vez: cuatro latidos de x = 4 a x = 60 sobre la línea y = 58. */
private object Ecg {
    const val BEAT = 14f
    const val BEATS = 4
    const val X0 = 4f
    const val BASE = 58f
    private val perBeat = ECG_BEAT.size / 2
    val count: Int = (perBeat - 1) * BEATS + 1
    val xs = FloatArray(count)
    val ys = FloatArray(count)

    init {
        var k = 0
        for (b in 0 until BEATS) {
            for (i in 0 until perBeat) {
                if (b > 0 && i == 0) continue
                xs[k] = X0 + b * BEAT + ECG_BEAT[2 * i]
                ys[k] = BASE + ECG_BEAT[2 * i + 1]
                k++
            }
        }
    }
}

private val HEART: Path by lazy {
    Path().apply {
        moveTo(0f, 4.4f)
        cubicTo(-6.8f, -0.6f, -3.8f, -5.6f, 0f, -2.2f)
        cubicTo(3.8f, -5.6f, 6.8f, -0.6f, 0f, 4.4f)
        close()
    }
}

/** Una figura que se agacha y sube con los brazos arriba, con un corazón que late y su pulso. General: corazón y pulso en verde. */
internal class FunctionalArt(lk: Float) : GoalArt(SymbolPalette.ok, period = 2.8f, restT = 1.9f, lk = lk) {
    /** 0 = erguida, 1 = en cuclillas. */
    internal fun squat(t: Float): Float = riseHoldFall(t, 0.2f, 0.9f, 1.25f, 1.7f)

    /** 0 = brazos abajo, 1 = brazos arriba. */
    internal fun reach(t: Float): Float = riseHoldFall(t, 1.25f, 1.8f, 2.15f, 2.65f)

    /** Intensidad del latido (0 a 1) en el instante [t]: un latido cada 0,7 s, el pico justo después de que el trazo pasa el pico R. */
    internal fun beat(t: Float): Float {
        val u = ((t - 0.3f) % BEAT_S + BEAT_S) % BEAT_S / BEAT_S
        return bell((u - 0.06f) / 0.07f) + 0.55f * bell((u - 0.3f) / 0.08f)
    }

    /** Campana de Gauss sin normalizar (1 en el centro). */
    private fun bell(x: Float): Float = kotlin.math.exp(-x * x)

    /** Posición de la cabeza lectora del trazo (unidades x) en el instante [t]. */
    internal fun headX(t: Float): Float = Ecg.X0 + (Ecg.BEAT * Ecg.BEATS) * (t / period)

    override fun drawStatic(pen: SymbolPen) = Unit

    override fun drawDynamic(pen: SymbolPen, t: Float) {
        with(pen) {
            val sq = squat(t)
            val rc = reach(t)
            val sy = 15.5f + 7.5f * sq
            val hipY = 29.5f + 9f * sq
            // Pulso: el trazo tenue y, detrás de la cabeza lectora, el tramo encendido.
            val head = headX(t)
            val edge = min(seg(head, 4f, 12f), 1f - seg(head, 54f, 61f))
            for (i in 0 until Ecg.count - 1) {
                line(Ecg.xs[i], Ecg.ys[i], Ecg.xs[i + 1], Ecg.ys[i + 1], soft, fine)
            }
            for (i in 0 until Ecg.count - 1) {
                val d = head - (Ecg.xs[i] + Ecg.xs[i + 1]) / 2f
                if (d < 0f || d > TAIL) continue
                val a = (1f - d / TAIL) * edge
                if (a > 0.03f) line(Ecg.xs[i], Ecg.ys[i], Ecg.xs[i + 1], Ecg.ys[i + 1], accent.fainter(a), mid)
            }
            if (edge > 0.05f) {
                val hy = ecgYAt(head)
                dot(head, hy, dotR(1.5f), accent.fainter(edge))
            }
            // Figura de frente: cabeza, hombros, tronco partido por el corazón, brazos y piernas.
            val cx = 32f
            ring(cx, sy - 7.2f, 3.4f, ink, bold * 0.9f)
            line(cx - 4.2f, sy, cx + 4.2f, sy, ink, bold)
            val beatK = beat(t)
            val hs = 1f + 0.3f * beatK
            line(cx, sy, cx, sy + 1.6f, ink, bold)
            line(cx, sy + 11.2f, cx, hipY, ink, bold)
            shifted(cx, sy + 6.3f) {
                scaled(hs, hs, 0f, 0f) { path(HEART, accent, mid) }
            }
            // Brazos: un giro del hombro (de colgar a levantar) y un codo que se pliega.
            val alpha = lerpF(14f, 168f, rc) * DEG
            val beta = lerpF(8f, 4f, rc) * DEG
            for (m in floatArrayOf(-1f, 1f)) {
                val shx = cx + m * 4.2f
                val ex = shx + m * sin(alpha) * ARM
                val ey = sy + cos(alpha) * ARM
                val gamma = alpha - beta
                val hx = ex + m * sin(gamma) * ARM
                val hy = ey + cos(gamma) * ARM
                poly(Offset(shx, sy), Offset(ex, ey), Offset(hx, hy), ink, bold)
            }
            // Piernas: los pies se abren al agacharse y las rodillas salen hacia fuera.
            val stance = lerpF(3.6f, 8f, sq)
            val hip = Offset(cx, hipY)
            for (m in floatArrayOf(-1f, 1f)) {
                val foot = Offset(cx + m * stance, FOOT_Y)
                val knee = ik(hip, foot, LEG, LEG, -m)
                poly(hip, knee, foot, ink, bold)
            }
        }
    }

    private fun ecgYAt(x: Float): Float {
        for (i in 0 until Ecg.count - 1) {
            if (x <= Ecg.xs[i + 1]) {
                val k = ((x - Ecg.xs[i]) / (Ecg.xs[i + 1] - Ecg.xs[i])).coerceIn(0f, 1f)
                return lerpF(Ecg.ys[i], Ecg.ys[i + 1], k)
            }
        }
        return Ecg.BASE
    }

    private companion object {
        const val BEAT_S = 0.7f
        const val TAIL = 18f
        const val ARM = 6.8f
        const val LEG = 11f
        const val FOOT_Y = 50f
    }
}

// ---------------------------------------------------------------- Powerbuilding

/**
 * Una barra arriba y una mancuerna abajo que se turnan: sube la barra (fuerza) y, después, la mancuerna hace su curl
 * (estética). El acento pasa de una a otra.
 */
internal class PowerbuildingArt(lk: Float) : GoalArt(SymbolPalette.energia, period = 3.2f, restT = 0.55f, lk = lk) {
    /** Elevación de la barra (0 a 1) en su turno, la primera mitad del bucle. */
    internal fun barLift(t: Float): Float = riseHoldFall(t, 0.15f, 0.7f, 0.9f, 1.45f)

    /** Elevación y giro de la mancuerna (0 a 1) en su turno, la segunda mitad. */
    internal fun curl(t: Float): Float = riseHoldFall(t, 1.75f, 2.3f, 2.55f, 3.05f)

    /** 0 = el acento está en la barra, 1 = en la mancuerna. */
    internal fun focus(t: Float): Float = smooth(seg(t, 1.5f, 1.7f)) - smooth(seg(t, 3.0f, 3.2f))

    override fun drawStatic(pen: SymbolPen) = Unit

    override fun drawDynamic(pen: SymbolPen, t: Float) {
        with(pen) {
            val fo = focus(t)
            val barColor = lerp(accent, ink, fo)
            val dbColor = lerp(ink, accent, fo)
            val bl = barLift(t)
            val by = 19f - 6f * bl
            val f = 1.4f * bl
            // Barra (arriba).
            curve(2f, by + f, 32f, by - f, 62f, by + f, ink, mid)
            for (m in floatArrayOf(1f, -1f)) {
                val px = if (m > 0f) 16f else 48f
                val py = sagY(px, 2f, 62f, by, f)
                rotated(sagDeg(px, 2f, 62f, f) , px, py) {
                    plateBox(px, py, m, 5f, 10.5f, 12f, 2.2f, barColor, mid)
                    plateBox(px, py, m, -1.5f, 2.5f, 8.5f, 1.8f, barColor, mid)
                    plateBox(px, py, m, -6f, -3.5f, 5f, 1.4f, ink, mid)
                }
            }
            // Mancuerna (abajo): sube un poco y gira en su curl.
            val c = curl(t)
            dumbbell(32f, 49.5f - 4.5f * c, 17.5f, -14f * c, dbColor, ink, mid)
        }
    }
}

// ---------------------------------------------------------------- Calistenia

/** Fondos en anillas: la figura sube y baja entre dos correas. Acento: ok. */
internal class CalisthenicsArt(lk: Float) : GoalArt(SymbolPalette.ok, period = 2.5f, restT = 0.65f, lk = lk) {
    /** 0 = brazos extendidos (arriba), 1 = abajo. */
    internal fun dip(t: Float): Float = riseHoldFall(t, 0.25f, 1.05f, 1.3f, 2.1f)

    /** Balanceo lateral de las anillas (unidades). */
    internal fun sway(t: Float): Float = 0.9f * sin(TAU * t / period)

    override fun drawStatic(pen: SymbolPen) {
        with(pen) {
            line(8f, 4f, 56f, 4f, ink, bold)
            box(18.4f, 10f, 21.6f, 16.5f, 1f, soft, fine)
            box(42.4f, 10f, 45.6f, 16.5f, 1f, soft, fine)
        }
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) {
        with(pen) {
            val d = dip(t)
            val sw = sway(t)
            val lx = 20f + sw
            val rx = 44f + sw
            val handY = 33f
            // Correas desde la barra hasta la mano y las anillas colgando bajo ella.
            line(20f, 4f, lx, handY, ink, mid)
            line(44f, 4f, rx, handY, ink, mid)
            ring(lx, handY + 5.5f, 5.5f, accent, mid * 1.15f)
            ring(rx, handY + 5.5f, 5.5f, accent, mid * 1.15f)
            val sy = lerpF(19.4f, 28.4f, d)
            val shL = Offset(27.8f, sy)
            val shR = Offset(36.2f, sy)
            val handL = Offset(lx, handY)
            val handR = Offset(rx, handY)
            val hip = Offset(32f, sy + 13f)
            val footY = hip.y + 16.5f
            frontFigure(
                FrontPose(
                    head = Offset(32f, sy - 7.4f),
                    shL = shL, shR = shR,
                    elL = ik(shL, handL, 7.9f, 7.9f, 1f), haL = handL,
                    elR = ik(shR, handR, 7.9f, 7.9f, -1f), haR = handR,
                    hip = hip,
                    knL = Offset(31.2f, (hip.y + footY) / 2f), ftL = Offset(30.6f, footY),
                    knR = Offset(32.8f, (hip.y + footY) / 2f), ftR = Offset(33.4f, footY),
                ),
                3.4f, ink, bold,
            )
        }
    }
}


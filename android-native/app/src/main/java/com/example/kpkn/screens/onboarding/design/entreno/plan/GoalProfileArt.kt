package com.example.kpkn.screens.onboarding.design.entreno.plan

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.screens.onboarding.design.eOutBack
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
import kotlin.math.sqrt

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

/** Un frenado suave (cúbico): arranca deprisa y se detiene al llegar a 1. Fuera de [0, 1] se satura. */
internal fun brake(x: Float): Float {
    val u = 1f - x.coerceIn(0f, 1f)
    return 1f - u * u * u
}

/**
 * Mancuerna de discos para los símbolos de fuerza: mango de 16 u y un disco de 3 × 12 a cada lado, centrada en ([cx], [cy])
 * y girada [tiltDeg] (negativo = la derecha sube). Los discos van en [plate] y el agarre en [grip].
 */
internal fun SymbolPen.plateDumbbell(
    cx: Float, cy: Float, tiltDeg: Float, plate: Color, grip: Color, w: Float, fine: Float,
) {
    rotated(tiltDeg, cx, cy) {
        line(cx - 8f, cy, cx + 8f, cy, plate, w)
        box(cx - 11f, cy - 6f, cx - 8f, cy + 6f, 1.2f, plate, w)
        box(cx + 8f, cy - 6f, cx + 11f, cy + 6f, 1.2f, plate, w)
        line(cx - 2.5f, cy, cx + 2.5f, cy, grip, fine)
    }
}

// ---------------------------------------------------------------- Fuerza y masa muscular

/**
 * Una mancuerna a la izquierda y, a la derecha, un gráfico de barras que crecen una tras otra, con una flecha ascendente y
 * el rótulo «KG» trazado con líneas. La mancuerna se inclina hacia arriba mientras crecen las barras. General: barras y
 * mancuerna en verde.
 */
internal class StrengthMuscleArt(lk: Float) : GoalArt(SymbolPalette.ok, period = 2.8f, restT = 2f, lk = lk) {
    /** Crecimiento (0 a 1) de la barra [i] (0, 1 o 2): cada una en su tramo de 0,35 s, con frenado suave. */
    internal fun barGrow(t: Float, i: Int): Float {
        val a = 0.2f + 0.35f * i
        return brake(seg(t, a, a + 0.35f))
    }

    /** Recogida al final del bucle (de 2,5 a 2,8 s): 0 mientras todo está visible, 1 en el cuadro inicial. */
    internal fun fall(t: Float): Float = smooth(seg(t, 2.5f, 2.8f))

    /** Inclinación de la mancuerna en grados: sube hasta −25° con las barras y se queda. */
    internal fun tilt(t: Float): Float = -25f * smooth(seg(t, 0.2f, 1.25f)) * (1f - fall(t))

    /** Avance (0 a 1) del trazo de la flecha, de 1,1 a 1,5 s. */
    internal fun arrow(t: Float): Float = smooth(seg(t, 1.1f, 1.5f)) * (1f - fall(t))

    /** Aparición (0 a 1) del rótulo «KG», de 1,45 a 1,65 s. */
    internal fun label(t: Float): Float = seg(t, 1.45f, 1.65f) * (1f - fall(t))

    override fun drawStatic(pen: SymbolPen) {
        with(pen) { line(30f, BASE_Y, 60f, BASE_Y, soft, fine) }
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) {
        with(pen) {
            val out = 1f - fall(t)
            plateDumbbell(15f, 42f, tilt(t), ink, accent, mid, fine)
            for (i in 0 until 3) {
                val h = BAR_H[i] * barGrow(t, i) * out
                if (h < 0.05f) continue
                val l = BAR_X[i]
                fillBox(l, BASE_Y - h, l + 5f, BASE_Y, 1f, accent.fainter(0.22f))
                box(l, BASE_Y - h, l + 5f, BASE_Y, 1f, accent, mid)
            }
            riseArrow(arrow(t), ink, mid)
            val k = label(t)
            if (k > 0f) {
                // «KG» salta de escala (0,6 → 1) al aparecer.
                val s = 0.6f + 0.4f * eOutBack(seg(t, 1.45f, 1.65f))
                fade(k) { scaled(s, s, 56f, 12f) { kgLetters(ink, mid) } }
            }
        }
    }

    private companion object {
        const val BASE_Y = 52f
        val BAR_X = floatArrayOf(32f, 41f, 50f)
        val BAR_H = floatArrayOf(10f, 17f, 26f)
    }
}

/** Longitudes de la flecha (33, 44) → (44, 36) → (56, 22). */
private val ARROW_L1 = sqrt(185f)
private val ARROW_L2 = sqrt(340f)

/**
 * La flecha ascendente dibujada hasta la fracción [p] de su trazo. Al final se abren sus dos alas (de 0,85 a 1 de [p]).
 */
private fun SymbolPen.riseArrow(p: Float, color: Color, w: Float) {
    if (p <= 0f) return
    val d = (ARROW_L1 + ARROW_L2) * p
    if (d <= ARROW_L1) {
        val k = d / ARROW_L1
        line(33f, 44f, lerpF(33f, 44f, k), lerpF(44f, 36f, k), color, w)
    } else {
        line(33f, 44f, 44f, 36f, color, w)
        val k = (d - ARROW_L1) / ARROW_L2
        line(44f, 36f, lerpF(44f, 56f, k), lerpF(36f, 22f, k), color, w)
    }
    val hk = seg(p, 0.85f, 1f)
    if (hk <= 0f) return
    // Las alas salen de la punta hacia atrás, abiertas 28° a cada lado.
    val c = cos(28f * DEG)
    val s = sin(28f * DEG)
    val bx = -12f / ARROW_L2
    val by = 14f / ARROW_L2
    val len = 6f * hk
    line(56f, 22f, 56f + len * (bx * c - by * s), 22f + len * (bx * s + by * c), color, w)
    line(56f, 22f, 56f + len * (bx * c + by * s), 22f + len * (-bx * s + by * c), color, w)
}

/**
 * «KG» trazado con líneas: la K con el palo en x = 50 y diagonales a (54, 8) y (54, 16); la G es un arco de radio 4
 * centrado en (59, 12), abierto a la derecha (de 45° a 315°). Su brazo sube desde el extremo de abajo, (61,8; 14,8), hasta
 * (61,8; 12) y entra hacia dentro hasta (59,5; 12). Nada a la derecha de x = 62.
 */
private fun SymbolPen.kgLetters(color: Color, w: Float) {
    line(50f, 8f, 50f, 16f, color, w)
    line(50f, 12f, 54f, 8f, color, w)
    line(50f, 12f, 54f, 16f, color, w)
    arc(59f, 12f, 4f, 45f, 270f, color, w)
    // Brazo de la G: sube del extremo de abajo del arco hasta la mitad y entra hacia dentro.
    val ex = 59f + 4f * cos(45f * DEG)
    line(ex, 12f + 4f * sin(45f * DEG), ex, 12f, color, w)
    line(ex, 12f, 59.5f, 12f, color, w)
}

// ---------------------------------------------------------------- Fuerza y cardio

/** Vértices del pulso bajo el corazón: de x = 28 a x = 62 sobre la línea y = 52, con un pico hacia abajo. */
private val PULSE_X = floatArrayOf(28f, 38f, 41f, 44f, 47f, 50f, 62f)
private val PULSE_Y = floatArrayOf(52f, 52f, 46f, 58f, 50f, 52f, 52f)

/** El pulso entero como trazo, construido una vez. */
private val PULSE_PATH: Path by lazy {
    Path().apply {
        moveTo(PULSE_X[0], PULSE_Y[0])
        for (i in 1 until PULSE_X.size) lineTo(PULSE_X[i], PULSE_Y[i])
    }
}

/** Ordenada del pulso en la abscisa [x] (interpolación lineal entre vértices). */
private fun pulseYAt(x: Float): Float {
    for (i in 0 until PULSE_X.size - 1) {
        if (x <= PULSE_X[i + 1]) {
            val k = ((x - PULSE_X[i]) / (PULSE_X[i + 1] - PULSE_X[i])).coerceIn(0f, 1f)
            return lerpF(PULSE_Y[i], PULSE_Y[i + 1], k)
        }
    }
    return PULSE_Y[PULSE_Y.size - 1]
}

/** El corazón: contorno con relleno, centrado en (44, 34). */
private val HEART_PATH: Path by lazy {
    Path().apply {
        moveTo(44f, 42f)
        cubicTo(30f, 32f, 34f, 20f, 44f, 26f)
        cubicTo(54f, 20f, 58f, 32f, 44f, 42f)
    }
}

/**
 * Una mancuerna a la izquierda y un corazón a la derecha que late dos veces por ciclo; un tramo en acento recorre el pulso
 * bajo el corazón en cada latido. General: mancuerna y corazón en verde.
 */
internal class StrengthCardioArt(lk: Float) : GoalArt(SymbolPalette.ok, period = 2f, restT = 0f, lk = lk) {
    /** Trazo reutilizado del tramo en acento del pulso. */
    private val span = Path()

    /** Latido (0 a 1) en el instante [t]: dos pulsos por bucle, con el pico a 0,35 y a 0,75 del período. */
    internal fun beat(t: Float): Float {
        val u = t / period
        return riseHoldFall(u, 0.25f, 0.35f, 0.35f, 0.45f) + riseHoldFall(u, 0.65f, 0.75f, 0.75f, 0.85f)
    }

    override fun drawStatic(pen: SymbolPen) {
        with(pen) { path(PULSE_PATH, ink, fine) }
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) {
        with(pen) {
            val u = t / period
            // El tramo en acento recorre el pulso en cada latido (de 0,2 a 0,5 y de 0,6 a 0,9 del período).
            sweep(seg(u, 0.2f, 0.5f), accent)
            sweep(seg(u, 0.6f, 0.9f), accent)
            // El corazón late alrededor de su centro.
            val b = beat(t)
            val hs = 1f + 0.12f * b
            scaled(hs, hs, 44f, 34f) {
                fillPath(HEART_PATH, accent.fainter(0.18f))
                path(HEART_PATH, accent, bold)
            }
            // La mancuerna sube 3 u con cada latido.
            plateDumbbell(16f, 38f - 3f * b, 0f, ink, accent, mid, fine)
        }
    }

    /** Dibuja el tramo en acento cuando la ventana [p] (0 a 1) lo recorre; fuera del trazo no se ve nada. */
    private fun SymbolPen.sweep(p: Float, color: Color) {
        val x0 = PULSE_X[0]
        val x1 = PULSE_X[PULSE_X.size - 1]
        val a = lerpF(x0 - SPAN_W, x1, p)
        val lo = max(a, x0)
        val hi = min(a + SPAN_W, x1)
        if (hi - lo < 0.3f) return
        span.reset()
        span.moveTo(lo, pulseYAt(lo))
        for (i in PULSE_X.indices) if (PULSE_X[i] > lo && PULSE_X[i] < hi) span.lineTo(PULSE_X[i], PULSE_Y[i])
        span.lineTo(hi, pulseYAt(hi))
        path(span, color, mid)
    }

    private companion object {
        const val SPAN_W = 12f
    }
}

// ---------------------------------------------------------------- Funcional y saludable

/** Cuadro clave de pie: la persona lista para el salto. */
private val JUMP_STAND = FrontPose(
    head = Offset(32f, 13f),
    shL = Offset(27.5f, 20f), shR = Offset(36.5f, 20f),
    elL = Offset(26f, 28f), haL = Offset(26.5f, 35f),
    elR = Offset(38f, 28f), haR = Offset(37.5f, 35f),
    hip = Offset(32f, 36f),
    knL = Offset(30f, 47f), ftL = Offset(29.5f, 58f),
    knR = Offset(34f, 47f), ftR = Offset(34.5f, 58f),
)

/** Cuadro clave en el aire: brazos en V y piernas abiertas, todo 3 u más arriba. */
private val JUMP_AIR = FrontPose(
    head = Offset(32f, 10f),
    shL = Offset(27.5f, 17f), shR = Offset(36.5f, 17f),
    elL = Offset(24f, 13f), haL = Offset(21f, 6f),
    elR = Offset(40f, 13f), haR = Offset(43f, 6f),
    hip = Offset(32f, 33f),
    knL = Offset(27f, 45f), ftL = Offset(23f, 55f),
    knR = Offset(37f, 45f), ftR = Offset(41f, 55f),
)

/** Cuadro clave agachada: como de pie, con la cadera 2 u más baja y las rodillas algo flexionadas. */
private val JUMP_CROUCH = FrontPose(
    head = Offset(32f, 15f),
    shL = Offset(27.5f, 22f), shR = Offset(36.5f, 22f),
    elL = Offset(26f, 30f), haL = Offset(26.5f, 37f),
    elR = Offset(38f, 30f), haR = Offset(37.5f, 37f),
    hip = Offset(32f, 38f),
    knL = Offset(28.2f, 47.6f), ftL = Offset(29.5f, 58f),
    knR = Offset(35.8f, 47.6f), ftR = Offset(34.5f, 58f),
)

/** Interpolación lineal entre dos poses de frente, articulación a articulación. */
private fun mixPose(a: FrontPose, b: FrontPose, k: Float): FrontPose = FrontPose(
    head = lerpOffset(a.head, b.head, k),
    shL = lerpOffset(a.shL, b.shL, k), shR = lerpOffset(a.shR, b.shR, k),
    elL = lerpOffset(a.elL, b.elL, k), haL = lerpOffset(a.haL, b.haL, k),
    elR = lerpOffset(a.elR, b.elR, k), haR = lerpOffset(a.haR, b.haR, k),
    hip = lerpOffset(a.hip, b.hip, k),
    knL = lerpOffset(a.knL, b.knL, k), ftL = lerpOffset(a.ftL, b.ftL, k),
    knR = lerpOffset(a.knR, b.knR, k), ftR = lerpOffset(a.ftR, b.ftR, k),
)

private fun lerpOffset(a: Offset, b: Offset, k: Float): Offset = Offset(lerpF(a.x, b.x, k), lerpF(a.y, b.y, k))

/**
 * Una persona que salta de frente: se agacha, salta con los brazos en V y las piernas abiertas, aterriza y vuelve a estar de
 * pie. Una sombra bajo los pies se encoge al subir y dos trazos junto a las manos marcan el punto alto. General: figura en
 * tinta y trazos de impulso en verde.
 */
internal class FunctionalArt(lk: Float) : GoalArt(SymbolPalette.ok, period = 1.8f, restT = 0.55f, lk = lk) {
    /** Cuánto está en el aire en el instante [t]: 0 en el suelo, 1 en el punto alto del salto. */
    internal fun airOf(t: Float): Float = when {
        t < 0.3f -> 0f
        t < 0.75f -> brake(seg(t, 0.3f, 0.75f))
        t < 1.2f -> 1f - smooth(seg(t, 0.75f, 1.2f))
        else -> 0f
    }

    /** Pose en el instante [t]: de pie (0), agachada (0,3), en el aire (0,75), agachada (1,2) y de pie otra vez (1,8). */
    internal fun pose(t: Float): FrontPose = when {
        t < 0.3f -> mixPose(JUMP_STAND, JUMP_CROUCH, smooth(seg(t, 0f, 0.3f)))
        t < 0.75f -> mixPose(JUMP_CROUCH, JUMP_AIR, brake(seg(t, 0.3f, 0.75f)))
        t < 1.2f -> mixPose(JUMP_AIR, JUMP_CROUCH, smooth(seg(t, 0.75f, 1.2f)))
        else -> mixPose(JUMP_CROUCH, JUMP_STAND, smooth(seg(t, 1.2f, 1.8f)))
    }

    override fun drawStatic(pen: SymbolPen) = Unit

    override fun drawDynamic(pen: SymbolPen, t: Float) {
        with(pen) {
            val air = airOf(t)
            // Sombra bajo los pies: se encoge y se aclara al subir.
            fade(0.5f - 0.25f * air) { oval(32f, 60.5f, 9f - 3.5f * air, 1.2f, ink, fine) }
            // Dos trazos de impulso junto a las manos en el punto alto.
            val burst = seg(air, 0.55f, 1f)
            if (burst > 0f) {
                line(18f, 4.5f, 14.5f, 2.5f, accent.fainter(burst), mid)
                line(46f, 4.5f, 49.5f, 2.5f, accent.fainter(burst), mid)
            }
            frontFigure(pose(t), 3.4f, ink, bold)
        }
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

package com.example.kpkn.screens.onboarding.design.entreno.plan

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import com.example.kpkn.domain.onboarding.LiftMark
import com.example.kpkn.screens.onboarding.design.entreno.DEG
import com.example.kpkn.screens.onboarding.design.entreno.FigGeo
import com.example.kpkn.screens.onboarding.design.entreno.FigPose
import com.example.kpkn.screens.onboarding.design.entreno.FrontPose
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPen
import com.example.kpkn.screens.onboarding.design.entreno.TAU
import com.example.kpkn.screens.onboarding.design.entreno.fainter
import com.example.kpkn.screens.onboarding.design.entreno.frontFigure
import com.example.kpkn.screens.onboarding.design.entreno.ik
import com.example.kpkn.screens.onboarding.design.entreno.plateFace
import com.example.kpkn.screens.onboarding.design.entreno.riseHoldFall
import com.example.kpkn.screens.onboarding.design.entreno.smooth
import com.example.kpkn.screens.onboarding.design.lerpF
import com.example.kpkn.screens.onboarding.design.seg
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Los símbolos de disciplina del paso «¿Cuál es tu objetivo?»: halterofilia, armwrestling, strongman, culturismo,
 * powerlifting y powerbuilding (la versión nueva, [PowerbuildingFlexArt]; la antigua vive en `GoalProfileArt.kt`).
 * Misma familia: línea fina, UN acento y un movimiento que explica la disciplina. Las poses y la geometría compartida
 * están en `DisciplinePoses.kt`.
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

// ---------------------------------------------------------------- Powerbuilding (versión nueva)

/** Semielipse del bíceps (radios 6 y 4,5), su base en el borde del brazo superior y los tramos de su contorno. */
private const val BICEPS_RX = 6f
private const val BICEPS_RY = 4.5f
private const val BICEPS_BASE_Y = 36.5f
private const val BICEPS_STEPS = 24

/**
 * Powerbuilding: primero dos brazos de frente que flexionan bíceps y después un atleta de perfil que hace peso muerto.
 * Los brazos son formas rellenas en tinta; el bíceps, una semielipse en acento que late desde su base. Acento: energía.
 * Es el símbolo de powerbuilding de la app (lo elige `GoalProfileArt.kt`).
 */
internal class PowerbuildingFlexArt(lk: Float) : GoalArt(SymbolPalette.energia, period = 4.8f, restT = 0.5f, lk = lk) {
    private val fig = FigPose()
    private val joints = ProfileJoints()

    /** Semielipse superior del bíceps con su base en y = 0: se traslada a cada brazo y crece desde esa base. */
    private val bicepsCap = Path()

    init {
        bicepsCap.moveTo(-BICEPS_RX, 0f)
        for (i in 1 until BICEPS_STEPS) {
            val a = PI.toFloat() * i / BICEPS_STEPS
            bicepsCap.lineTo(-BICEPS_RX * cos(a), -BICEPS_RY * sin(a))
        }
        bicepsCap.lineTo(BICEPS_RX, 0f)
        bicepsCap.close()
    }

    /** 0 = escena de los bíceps, 1 = escena del peso muerto: se funde de 1,8 a 2,1 s y vuelve de 4,4 a 4,8 s. */
    internal fun deadliftWeight(t: Float): Float = riseHoldFall(t, 1.8f, 2.1f, 4.4f, 4.8f)

    /** Fase del peso muerto (0 = en el suelo, 1 = de pie): sube de 2,2 a 3,4 s, aguanta y baja de 3,8 a 4,3 s. */
    internal fun deadliftU(t: Float): Float = riseHoldFall(t, 2.2f, 3.4f, 3.8f, 4.3f)

    /** Latido de los bíceps (0 a 1): dos por bucle, de 0,3 a 0,7 s y de 1,0 a 1,4 s. */
    internal fun beat(t: Float): Float = riseHoldFall(t, 0.3f, 0.5f, 0.5f, 0.7f) + riseHoldFall(t, 1.0f, 1.2f, 1.2f, 1.4f)

    override fun drawStatic(pen: SymbolPen) = Unit

    override fun drawDynamic(pen: SymbolPen, t: Float) {
        val b = deadliftWeight(t)
        with(pen) {
            if (b < 0.997f) fade(1f - b) { armsScene(t) }
            if (b > 0.003f) fade(b) { deadliftScene(t) }
        }
    }

    /**
     * Dos brazos de frente como formas rellenas en tinta: el brazo superior casi horizontal (cápsula de 6 u), el antebrazo
     * hacia arriba (5 u) y el puño redondo. Cabeza, cuello y trapecio completan el busto. El bíceps es la semielipse en
     * acento sobre cada brazo superior, con su base en el borde del brazo; late creciendo desde esa base.
     */
    private fun SymbolPen.armsScene(t: Float) {
        val s = 1f + 0.18f * beat(t)
        dot(32f, 30f, 4f, ink)
        line(Offset(32f, 34f), Offset(32f, 40f), ink, 4f)
        line(Offset(24f, 40f), Offset(40f, 40f), ink, 4f)
        // Brazo izquierdo: brazo superior (6 u), antebrazo (5 u) y puño.
        line(Offset(24f, 40f), Offset(10f, 38f), ink, 6f)
        line(Offset(10f, 38f), Offset(12f, 22f), ink, 5f)
        dot(13f, 19f, 3.6f, ink)
        // Brazo derecho: el espejo respecto de x = 32.
        line(Offset(40f, 40f), Offset(54f, 38f), ink, 6f)
        line(Offset(54f, 38f), Offset(52f, 22f), ink, 5f)
        dot(51f, 19f, 3.6f, ink)
        // Bíceps: semielipse en acento sobre cada brazo superior, creciendo desde su base.
        shifted(17f, BICEPS_BASE_Y) { scaled(s, s, 0f, 0f) { fillPath(bicepsCap, accent) } }
        shifted(47f, BICEPS_BASE_Y) { scaled(s, s, 0f, 0f) { fillPath(bicepsCap, accent) } }
    }

    /** Peso muerto de perfil con el disco de la barra en acento; el suelo solo aparece en esta escena. */
    private fun SymbolPen.deadliftScene(t: Float) {
        line(Offset(6f, 60.8f), Offset(58f, 60.8f), soft, fine)
        solveLiftCanvas(LiftMark.DEADLIFT, deadliftU(t), fig, joints)
        plateFace(joints.bar.x, joints.bar.y, LIFT_PLATE_R, accent, accent, mid)
        profileFigure(joints, ink, ink.fainter(SymbolPalette.FAR_LIMB), bold, LIFT_HEAD_R)
    }
}

// ---------------------------------------------------------------- Strongman

/** Centro de la piedra en el suelo y sobre la plataforma: la subida de la piedra se mide entre las dos alturas. */
private const val STRONG_LOW_Y = SM_GROUND - STONE_RADIUS
private const val STRONG_TOP_Y = 33.5f

/** La figura se yergue hasta 3,0 s; de 3,3 a 3,6 s se funde al cuadro inicial. */
private const val STRONG_HOLD_AT = 3.0f
private const val STRONG_FADE_AT = 3.3f

/** Radio de la cabeza de la figura de strongman. */
private const val STRONG_HEAD_R = 3.3f

/**
 * Una piedra de atlas que se levanta del suelo hasta el pecho y se empuja a una plataforma: la figura se agacha, la abraza,
 * se yergue y la suelta arriba. De perfil, mirando a la derecha: la piedra es un círculo con el contorno en tinta y un brillo
 * en acento; la plataforma, una caja con el canto superior en acento. Acento: energía.
 */
internal class StrongmanArt(lk: Float) : GoalArt(SymbolPalette.energia, period = 3.6f, restT = 2.7f, lk = lk) {
    private val params = FloatArray(11)
    private val figure = ProfileJoints()

    /** El centro de la piedra en el instante [t], en el lienzo de 64. */
    internal fun stoneCenter(t: Float): Offset {
        strongParams(t, params)
        return Offset(params[0], params[1])
    }

    /** Cuánto ha subido la piedra en [t]: 0 en el suelo, 1 sobre la plataforma. */
    internal fun lift(t: Float): Float =
        ((STRONG_LOW_Y - stoneCenter(t).y) / (STRONG_LOW_Y - STRONG_TOP_Y)).coerceIn(0f, 1f)

    override fun drawStatic(pen: SymbolPen) {
        with(pen) {
            line(Offset(1.5f, SM_GROUND), Offset(62.5f, SM_GROUND), soft, fine)
            // La plataforma: caja en tinta media y el canto superior en acento.
            box(42f, 42f, 60f, 60f, 1f, ink, mid)
            line(Offset(42f, 42f), Offset(60f, 42f), accent, fine)
        }
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) {
        // Tras 3,0 s la figura se queda yergue; de 3,3 a 3,6 s se funde al cuadro inicial (dos cuadros con opacidad de grupo).
        val back = smooth(seg(t, STRONG_FADE_AT, period))
        val hold = minOf(t, STRONG_HOLD_AT)
        if (back < 1f) pen.fade(1f - back) { figureAt(pen, hold) }
        if (back > 0f) pen.fade(back) { figureAt(pen, 0f) }
    }

    /** La figura y la piedra en [tt], sin fundidos: el lado lejano queda tras la piedra y el cercano delante de ella. */
    private fun figureAt(pen: SymbolPen, tt: Float) {
        strongPose(tt, params, figure)
        val j = figure
        val stone = Offset(params[0], params[1])
        with(pen) {
            val farInk = ink.fainter(SymbolPalette.FAR_LIMB)
            poly(j.shoulder, j.elbowF, j.handF, farInk, bold)
            poly(j.hip, j.kneeF, j.footF, farInk, bold)
            line(j.hip, j.shoulder, ink, bold)
            poly(j.hip, j.kneeN, j.footN, ink, bold)
            dot(j.head.x, j.head.y, STRONG_HEAD_R + 0.3f, Color.Black)
            ring(j.head.x, j.head.y, STRONG_HEAD_R, ink, bold * 0.9f)
            // La piedra: disco negro que tapa lo de detrás, contorno en tinta y brillo en acento.
            dot(stone.x, stone.y, STONE_RADIUS, Color.Black)
            ring(stone.x, stone.y, STONE_RADIUS, ink, bold)
            arc(stone.x, stone.y, STONE_RADIUS * 0.62f, 200f, 60f, accent, fine)
            // El brazo cercano va delante de la piedra, con la mano en su borde.
            poly(j.shoulder, j.elbowN, j.handN, ink, bold)
            dot(j.handN.x, j.handN.y, dotR(1.8f), ink)
        }
    }
}

// ---------------------------------------------------------------- Culturismo

/** Hombros y cintura de la figura de culturismo, en el lienzo de 64. */
private val CULT_SH_L = Offset(22f, 20f)
private val CULT_SH_R = Offset(42f, 20f)
private val CULT_WAIST_L = Offset(27f, 36f)
private val CULT_WAIST_R = Offset(37f, 36f)

/** Largo del brazo superior y del antebrazo; el ángulo del brazo superior va de la pose de bíceps a la de dorsales. */
private const val CULT_UARM = 8.5f
private const val CULT_FARM = 9.5f
private val CULT_ANGLE_L = floatArrayOf(-166f, -234.5f)
private val CULT_ANGLE_R = floatArrayOf(-14f, 54.5f)

/** Dónde buscan las manos en cada pose: junto a la cabeza (doble bíceps) y en la cintura (dorsales). */
private val CULT_HAND_1_L = Offset(16f, 8f)
private val CULT_HAND_1_R = Offset(48f, 8f)
private val CULT_HAND_2_L = Offset(27f, 34f)
private val CULT_HAND_2_R = Offset(37f, 34f)

/**
 * Figura posando de frente: doble bíceps y, después, dorsales abiertos con las manos en la cintura. El torso en V, los
 * pectorales, el abdomen y las piernas no cambian; los brazos giran por ángulo y el antebrazo apunta a la mano, así su largo
 * no cambia. Los dorsales se tiñen en acento al abrirse. Acento: mente.
 */
internal class BodybuildingArt(lk: Float) : GoalArt(SymbolPalette.mente, period = 3.6f, restT = 0.3f, lk = lk) {
    /** Contorno de los dorsales abiertos, reutilizado entre cuadros. */
    private val lats = Path()

    /** Los brazos en el cuadro actual (ocho valores: codo y mano izquierdos, codo y mano derechos). */
    private val arms = FloatArray(8)

    /** 0 = pose de bíceps, 1 = dorsales abiertos: sube de 1,2 a 1,6 s y vuelve de 2,8 a 3,2 s. */
    internal fun back(t: Float): Float = smooth(seg(t, 1.2f, 1.6f)) - smooth(seg(t, 2.8f, 3.2f))

    /** Cuánto se abren los dorsales: en la pose de dorsales se abren un poco más con el tiempo. */
    internal fun spread(t: Float): Float = back(t) * (0.85f + 0.15f * seg(t, 1.6f, 2.8f))

    /** Latido de los bíceps (0 a 1): dos por bucle, con los picos en 0,3 y 0,8 s. */
    internal fun beat(t: Float): Float = riseHoldFall(t, 0.1f, 0.3f, 0.3f, 0.5f) + riseHoldFall(t, 0.6f, 0.8f, 0.8f, 1.0f)

    /** Los brazos en [t] (ocho valores en [out]): codo y mano izquierdos, codo y mano derechos. */
    internal fun armsAt(t: Float, out: FloatArray) {
        val k = back(t)
        val aL = lerpF(CULT_ANGLE_L[0], CULT_ANGLE_L[1], k) * DEG
        val aR = lerpF(CULT_ANGLE_R[0], CULT_ANGLE_R[1], k) * DEG
        val eL = CULT_SH_L + Offset(cos(aL), sin(aL)) * CULT_UARM
        val eR = CULT_SH_R + Offset(cos(aR), sin(aR)) * CULT_UARM
        val hL = armHand(eL, FigGeo.lerp(CULT_HAND_1_L, CULT_HAND_2_L, k))
        val hR = armHand(eR, FigGeo.lerp(CULT_HAND_1_R, CULT_HAND_2_R, k))
        out[0] = eL.x
        out[1] = eL.y
        out[2] = hL.x
        out[3] = hL.y
        out[4] = eR.x
        out[5] = eR.y
        out[6] = hR.x
        out[7] = hR.y
    }

    /** La mano a [CULT_FARM] del codo, en la dirección de su objetivo: el antebrazo mantiene su largo. */
    private fun armHand(elbow: Offset, target: Offset): Offset {
        val dx = target.x - elbow.x
        val dy = target.y - elbow.y
        val len = sqrt(dx * dx + dy * dy)
        return Offset(elbow.x + dx / len * CULT_FARM, elbow.y + dy / len * CULT_FARM)
    }

    override fun drawStatic(pen: SymbolPen) = Unit

    override fun drawDynamic(pen: SymbolPen, t: Float) {
        val k = back(t)
        val o = spread(t)
        armsAt(t, arms)
        val eL = Offset(arms[0], arms[1])
        val hL = Offset(arms[2], arms[3])
        val eR = Offset(arms[4], arms[5])
        val hR = Offset(arms[6], arms[7])
        with(pen) {
            // Hombros, cuello y cabeza.
            line(CULT_SH_L, CULT_SH_R, ink, bold)
            ring(32f, 12f, 3.6f, ink, bold * 0.9f)
            line(Offset(32f, 15.6f), Offset(32f, 20f), ink, mid)
            // Torso en V que se abre con los dorsales; el relleno en acento tenue sigue al contorno.
            val fl = Offset(lerpF(24.5f, 19f, o), lerpF(28f, 26f, o))
            val fr = Offset(64f - fl.x, fl.y)
            line(CULT_SH_L, fl, ink, bold)
            line(fl, CULT_WAIST_L, ink, bold)
            line(CULT_SH_R, fr, ink, bold)
            line(fr, CULT_WAIST_R, ink, bold)
            if (o > 0.01f) {
                val fill = accent.fainter(0.30f * o)
                lats.reset()
                lats.moveTo(CULT_SH_L.x, CULT_SH_L.y)
                lats.lineTo(fl.x, fl.y)
                lats.lineTo(CULT_WAIST_L.x, CULT_WAIST_L.y)
                lats.lineTo(29.2f, 31f)
                lats.lineTo(28f, 24f)
                lats.close()
                fillPath(lats, fill)
                lats.reset()
                lats.moveTo(CULT_SH_R.x, CULT_SH_R.y)
                lats.lineTo(fr.x, fr.y)
                lats.lineTo(CULT_WAIST_R.x, CULT_WAIST_R.y)
                lats.lineTo(34.8f, 31f)
                lats.lineTo(36f, 24f)
                lats.close()
                fillPath(lats, fill)
            }
            // Pectorales (dos arcos) y abdomen (tres trazos cortos).
            curve(24.2f, 24.4f, 28.4f, 29.4f, 31.4f, 25.8f, ink, mid)
            curve(39.8f, 24.4f, 35.6f, 29.4f, 32.6f, 25.8f, ink, mid)
            line(Offset(29.2f, 30.2f), Offset(34.8f, 30.2f), ink.fainter(0.8f), fine)
            line(Offset(29.4f, 32.8f), Offset(34.6f, 32.8f), ink.fainter(0.8f), fine)
            line(Offset(29.8f, 35.2f), Offset(34.2f, 35.2f), ink.fainter(0.8f), fine)
            // Piernas (fijas) y arco de cuádriceps en cada muslo.
            poly(Offset(28f, 37f), Offset(25f, 48f), Offset(24f, 59f), ink, bold)
            poly(Offset(36f, 37f), Offset(39f, 48f), Offset(40f, 59f), ink, bold)
            curve(27.9f, 39.5f, 26.2f, 44.5f, 25.6f, 46.8f, ink.fainter(0.7f), fine)
            curve(36.1f, 39.5f, 37.8f, 44.5f, 38.4f, 46.8f, ink.fainter(0.7f), fine)
            // Brazos y manos.
            poly(CULT_SH_L, eL, hL, ink, bold)
            poly(CULT_SH_R, eR, hR, ink, bold)
            dot(hL.x, hL.y, dotR(2f), ink)
            dot(hR.x, hR.y, dotR(2f), ink)
            // Bíceps: relleno en acento sobre el brazo superior; solo en la pose de bíceps.
            val bl = 1f - k
            if (bl > 0.01f) {
                val s = 1f + 0.18f * beat(t)
                fillOvalRot(
                    (CULT_SH_L.x + eL.x) / 2f, (CULT_SH_L.y + eL.y) / 2f, 4.0f * s, 3.2f * s,
                    dirDeg(eL.x - CULT_SH_L.x, eL.y - CULT_SH_L.y), accent.fainter(bl),
                )
                fillOvalRot(
                    (CULT_SH_R.x + eR.x) / 2f, (CULT_SH_R.y + eR.y) / 2f, 4.0f * s, 3.2f * s,
                    dirDeg(eR.x - CULT_SH_R.x, eR.y - CULT_SH_R.y), accent.fainter(bl),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Powerlifting

/** Luces de los jueces: tres en la parte alta del lienzo, a la altura [PL_LAMP_Y]. */
private val PL_LAMP_X = floatArrayOf(22f, 32f, 42f)
private const val PL_LAMP_Y = 6f

/** Cuándo se encienden las luces (tras cada repetición) y cuándo se apagan (al cambiar de levantamiento). */
private val PL_LAMP_ON = floatArrayOf(1.4f, 3.4f, 5.4f)
private val PL_LAMP_OFF = floatArrayOf(2.0f, 4.0f, 6.0f)

/**
 * Un atleta con singlet oficial que alterna los tres básicos (sentadilla, banca y peso muerto), con un fundido entre ellos.
 * Al terminar cada repetición se encienden las tres luces de los jueces. El singlet va en acento (tronco y primer tramo de
 * muslo, con tirante); el cuerpo, el disco y la barra, en tinta. Acento: músculo.
 */
internal class PowerliftingArt(lk: Float) : GoalArt(SymbolPalette.musculo, period = 6f, restT = 1.5f, lk = lk) {
    private val fig = FigPose()
    private val joints = ProfileJoints()
    private val weight = FloatArray(3)

    /** Fase de la sentadilla en [t] (0 = de pie, 1 = abajo): baja de 0,1 a 0,7 s, aguanta y sube hasta 1,4 s. */
    internal fun squatU(t: Float): Float = riseHoldFall(t, 0.1f, 0.7f, 0.9f, 1.4f)

    /** Fase de la banca en [t] (0 = brazos estirados, 1 = barra en el pecho): baja de 2,1 a 2,6 s y empuja hasta 3,4 s. */
    internal fun benchU(t: Float): Float = riseHoldFall(t, 2.1f, 2.6f, 2.8f, 3.4f)

    /** Fase del peso muerto en [t] (0 = en el suelo, 1 = de pie): sube de 4,1 a 4,8 s y baja hasta 5,4 s. */
    internal fun deadliftU(t: Float): Float = riseHoldFall(t, 4.1f, 4.8f, 4.9f, 5.4f)

    /**
     * Peso de cada levantamiento en [t] (sentadilla, banca, peso muerto), en [out]: suman 1 y se funden de 1,8 a 2,0 s,
     * de 3,8 a 4,0 s y de 5,8 a 6,0 s.
     */
    internal fun weights(t: Float, out: FloatArray) {
        val s1 = smooth(seg(t, 1.8f, 2.0f))
        val s2 = smooth(seg(t, 3.8f, 4.0f))
        val s3 = smooth(seg(t, 5.8f, 6.0f))
        out[0] = 1f - s1 + s3
        out[1] = s1 - s2
        out[2] = s2 - s3
    }

    /** Cuánto están encendidas las luces de los jueces en [t] (0 a 1). */
    internal fun judgeLit(t: Float): Float {
        var lit = 0f
        for (i in PL_LAMP_ON.indices) {
            val on = PL_LAMP_ON[i]
            lit = max(lit, smooth(seg(t, on, on + 0.12f)) - smooth(seg(t, PL_LAMP_OFF[i], PL_LAMP_OFF[i] + 0.18f)))
        }
        return lit
    }

    override fun drawStatic(pen: SymbolPen) {
        with(pen) { line(Offset(5f, 60.8f), Offset(59f, 60.8f), soft, fine) }
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) {
        weights(t, weight)
        with(pen) {
            if (weight[0] > 0.003f) fade(weight[0]) { liftScene(LiftMark.SQUAT, squatU(t), bench = false) }
            if (weight[1] > 0.003f) fade(weight[1]) { liftScene(LiftMark.BENCH, benchU(t), bench = true) }
            if (weight[2] > 0.003f) fade(weight[2]) { liftScene(LiftMark.DEADLIFT, deadliftU(t), bench = false) }
            judgeLights(t)
        }
    }

    /** Un levantamiento en la fase [u]: el banco si toca, el disco detrás, el singlet y, encima, el cuerpo en tinta. */
    private fun SymbolPen.liftScene(lift: LiftMark, u: Float, bench: Boolean) {
        solveLiftCanvas(lift, u, fig, joints)
        val j = joints
        if (bench) benchStage(ink.fainter(0.9f), soft, mid, fine)
        // El disco (tinta y anillo interior) va detrás del cuerpo, al 60 % como en las marcas.
        plateFace(j.bar.x, j.bar.y, LIFT_PLATE_R, ink.fainter(0.6f), ink.fainter(0.6f), mid)
        // Singlet: tronco y primer 30 % de cada muslo, con un tirante sobre el hombro.
        val band = bold * 2.2f
        line(j.hip, j.shoulder, accent, band)
        line(j.hip, FigGeo.lerp(j.hip, j.kneeN, 0.3f), accent, band)
        line(j.hip, FigGeo.lerp(j.hip, j.kneeF, 0.3f), accent.fainter(SymbolPalette.FAR_LIMB), band)
        line(Offset(j.shoulder.x - 0.8f, j.shoulder.y - 2.6f), Offset(j.shoulder.x + 1.4f, j.shoulder.y + 0.8f), ink, fine)
        profileFigure(j, ink, ink.fainter(SymbolPalette.FAR_LIMB), bold, LIFT_HEAD_R)
    }

    /** Las tres luces de los jueces: apagadas, un aro tenue; encendidas, un disco blanco que da un pequeño salto. */
    private fun SymbolPen.judgeLights(t: Float) {
        val lit = judgeLit(t)
        var jump = 0f
        for (i in PL_LAMP_ON.indices) {
            val on = PL_LAMP_ON[i]
            jump = max(jump, riseHoldFall(t, on, on + 0.1f, on + 0.25f, on + 0.4f))
        }
        for (x in PL_LAMP_X) {
            ring(x, PL_LAMP_Y, 2.2f, ink.fainter(0.35f), mid * 0.8f)
            if (lit > 0.003f) dot(x, PL_LAMP_Y - 0.9f * jump, 2.2f + 0.4f * jump, ink.fainter(lit))
        }
    }
}

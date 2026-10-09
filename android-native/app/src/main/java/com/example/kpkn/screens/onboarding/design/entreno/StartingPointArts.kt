package com.example.kpkn.screens.onboarding.design.entreno

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.ArmStyle
import com.example.kpkn.domain.onboarding.AvoidGroup
import com.example.kpkn.domain.onboarding.CardioBase
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.lerpF
import com.example.kpkn.screens.onboarding.design.seg
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.onboarding.design.entreno.plan.ExercisePattern
import com.example.kpkn.screens.onboarding.design.entreno.plan.patternArt
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * Símbolos del punto de partida (paso «evitar», «tu cardio» y «estilo de mesa»). Misma familia que `EquipmentSymbolArt.kt`
 * y `plan/ExercisePatterns.kt`: lienzo de 64 × 64, línea fina, un acento de módulo, sin cajas. Cada figura nueva expone
 * [StartingPointShape.pointsAt]: sus articulaciones y extremos en un instante, para que la prueba compruebe que nada se
 * sale del lienzo (la misma idea que `EntrenoSymbolMathTest`).
 */

/** Lo que se puede comprobar de un símbolo del punto de partida: dónde están sus puntos en el instante [t]. */
internal interface StartingPointShape {
    fun pointsAt(t: Float): List<Offset>
}

/** Línea del suelo de las figuras de movimiento. */
private const val FLOOR_Y = 58f

/** Lado del lienzo de la cuadrícula de «evitar»: el dibujo mide ≈ 60 dp y el resto es margen para la marca de «hecho». */
internal val AVOID_CANVAS = 76.dp

/** Fracción del lienzo que ocupa el dibujo de un símbolo de la cuadrícula. */
internal const val AVOID_ART_SCALE = 60f / 76f

/** Lado del lienzo de cardio y de estilo de mesa, y su fracción de dibujo. */
internal val SCENE_CANVAS = 84.dp
internal const val SCENE_ART_SCALE = 0.78f

// ---------------------------------------------------------------- marcha (cardio y correr)

/** Cómo se mueve una figura de zancada: cuánto se inclina, cuánto abre la zancada, cuánto dobla la rodilla y cuánto suben los brazos. */
internal class StrideGait(val lean: Float, val swing: Float, val bend: Float, val arms: Float)

/** Caminar: erguida, zancada corta y brazos bajos. */
internal val WALK_GAIT = StrideGait(lean = 0f, swing = 0.28f, bend = 0.45f, arms = 0.22f)

/** Trotar: inclinada 8° y zancada media. */
internal val JOG_GAIT = StrideGait(lean = 8f * DEG, swing = 0.42f, bend = 0.8f, arms = 0.45f)

/** Correr: inclinada 15°, zancada larga y brazos altos. Las series rápidas corren con esta misma marcha. */
internal val RUN_GAIT = StrideGait(lean = 15f * DEG, swing = 0.62f, bend = 1.05f, arms = 0.85f)

/** Medidas de la figura de zancada en el lienzo de 64 unidades (el pictograma de patrón usa las mismas). */
private val STRIDE_DIMS = FigureDims(thigh = 13f, shin = 13f, torso = 16f, uarm = 8f, farm = 8f, head = 3.6f)

/** Centro y radio de la esfera de minutos (arriba a la derecha). */
private const val SPHERE_X = 48f
private const val SPHERE_Y = 15f
private const val SPHERE_R = 9f

/**
 * Pose de perfil de una zancada en la fase [ph] (radianes) con la planta más baja sobre el suelo y la cadera en [hipX]. Es la
 * cinemática de `runPose` con la marcha de [gait].
 */
internal fun strideFigure(gait: StrideGait, ph: Float, hipX: Float): Pose {
    val d = STRIDE_DIMS
    val half = PI.toFloat()
    val th0 = gait.swing * sin(ph)
    val bend0 = 0.25f + gait.bend * max(0f, cos(ph))
    val knee0 = downDir(th0) * d.thigh
    val foot0 = knee0 + downDir(th0 - bend0) * d.shin
    val th1 = gait.swing * sin(ph + half)
    val bend1 = 0.25f + gait.bend * max(0f, cos(ph + half))
    val knee1 = downDir(th1) * d.thigh
    val foot1 = knee1 + downDir(th1 - bend1) * d.shin
    val low = max(foot0.y, foot1.y)
    val hip = Offset(hipX, FLOOR_Y - low - 0.035f * (d.thigh + d.shin) * abs(cos(ph)))
    val sh = hip + Offset(sin(gait.lean), -cos(gait.lean)) * d.torso
    // Brazos en oposición a las piernas.
    val a0 = -gait.arms * sin(ph + half)
    val elbow0 = sh + downDir(a0) * d.uarm
    val hand0 = elbow0 + downDir(a0 + 1.5f) * d.farm
    val a1 = -gait.arms * sin(ph)
    val elbow1 = sh + downDir(a1) * d.uarm
    val hand1 = elbow1 + downDir(a1 + 1.5f) * d.farm
    return Pose(
        hip, hip + knee0, hip + foot0, hip + knee1, hip + foot1,
        sh, sh + Offset(sin(gait.lean) * 0.9f, -cos(gait.lean)) * (d.head * 1.55f),
        elbow0, hand0, elbow1, hand1,
    )
}

/**
 * Un símbolo de movimiento sobre el suelo: la figura en su zancada y, opcional, la esfera de minutos (un arco que se llena en
 * bucle hasta su minuto) o tres trazos de velocidad que entran y salen. Sin seleccionar, el cuadro de reposo.
 */
internal class StrideArt(
    accent: Color,
    period: Float,
    restT: Float,
    private val gait: StrideGait,
    private val hipX: Float,
    private val minutes: Int? = null,
    private val speedStrokes: Boolean = false,
) : EquipmentArt(accent, period, restT), StartingPointShape {

    override fun drawStatic(pen: SymbolPen) {
        with(pen) {
            line(6f, FLOOR_Y + 0.5f, 58f, FLOOR_Y + 0.5f, soft, SymbolStroke.FINE)
            if (minutes != null) ring(SPHERE_X, SPHERE_Y, SPHERE_R, soft, SymbolStroke.FINE)
        }
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) {
        with(pen) {
            val p = strideFigure(gait, TAU * t / period, hipX)
            figure(p, STRIDE_DIMS.head, accent, accent.fainter(SymbolPalette.FAR_LIMB), SymbolStroke.BODY)
            if (minutes != null) {
                val sweep = 360f * minutes / 60f * sphereFill(t)
                if (sweep >= 1f) arc(SPHERE_X, SPHERE_Y, SPHERE_R, -90f, sweep, accent, SymbolStroke.LINE)
            }
            if (speedStrokes) {
                for (i in 0 until SPEED_STROKES) {
                    val q = wrapLoop(t / period + i / SPEED_STROKES.toFloat(), 1f)
                    val x = SPEED_X0 + 10f * q
                    fade(sin(PI.toFloat() * q)) { line(x, SPEED_Y0 + i * 7f, x + 7f, SPEED_Y0 + i * 7f, accent, SymbolStroke.FINE) }
                }
            }
        }
    }

    /** Fracción del arco que está llena en el instante [t]: se llena en el primer 80 % del bucle y aguanta. */
    private fun sphereFill(t: Float): Float = smooth(seg(t / period, 0f, 0.8f))

    override fun pointsAt(t: Float): List<Offset> {
        val p = strideFigure(gait, TAU * t / period, hipX)
        val out = mutableListOf(p.hip, p.kneeN, p.footN, p.kneeF, p.footF, p.sh, p.head, p.elbowN, p.handN, p.elbowF, p.handF)
        if (minutes != null) {
            out += Offset(SPHERE_X - SPHERE_R, SPHERE_Y - SPHERE_R)
            out += Offset(SPHERE_X + SPHERE_R, SPHERE_Y + SPHERE_R)
        }
        if (speedStrokes) {
            for (i in 0 until SPEED_STROKES) {
                val q = wrapLoop(t / period + i / SPEED_STROKES.toFloat(), 1f)
                out += Offset(SPEED_X0 + 10f * q, SPEED_Y0 + i * 7f)
                out += Offset(SPEED_X0 + 10f * q + 7f, SPEED_Y0 + i * 7f)
            }
        }
        return out
    }

    private companion object {
        const val SPEED_STROKES = 3
        const val SPEED_X0 = 2f
        const val SPEED_Y0 = 26f
    }
}

/** «Me cuesta 10 min»: caminar erguida, con el arco de la esfera hasta su minuto. */
internal val WALK_ART = StrideArt(SymbolPalette.energia, period = 2.4f, restT = 2.16f, gait = WALK_GAIT, hipX = 22f, minutes = 10)

/** «Aguanto 20–30 min»: trote suave y la esfera en 25 minutos. */
internal val JOG_ART = StrideArt(SymbolPalette.energia, period = 1.5f, restT = 1.35f, gait = JOG_GAIT, hipX = 22f, minutes = 25)

/** «Corro 45 min»: carrera y la esfera en 45 minutos. */
internal val RUN_ART = StrideArt(SymbolPalette.energia, period = 1.05f, restT = 0.95f, gait = RUN_GAIT, hipX = 22f, minutes = 45)

/** «Hago series rápidas»: la carrera con tres trazos de velocidad que entran y salen a ritmo de series (sin esfera). */
internal val SPRINT_ART = StrideArt(SymbolPalette.energia, period = 0.8f, restT = 0.2f, gait = RUN_GAIT, hipX = 22f, speedStrokes = true)

/** «Correr», en «evitar»: la carrera de siempre, sin esfera. */
internal val RUNNING_ART = StrideArt(SymbolPalette.columna, period = 1.1f, restT = 0.3f, gait = RUN_GAIT, hipX = 32f)

// ---------------------------------------------------------------- evitar: fondos, hip thrust, saltos y «Ninguno»

/** «Ninguno» en «evitar»: un círculo vacío, tenue hasta que se elige. Sin movimiento. */
internal object NoneArt : EquipmentArt(SymbolPalette.musculo, period = 1f, restT = 0f), StartingPointShape {
    override fun drawStatic(pen: SymbolPen) {
        with(pen) { ring(32f, 32f, 17f, accent, SymbolStroke.LINE) }
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) = Unit

    override fun pointsAt(t: Float): List<Offset> = listOf(Offset(15f, 15f), Offset(49f, 49f))
}

/**
 * «Hip thrust»: la espalda sobre el banco, la cadera sube con la barra encima y sube y baja en bucle. El banco es el de la
 * escena; la barra lleva sus discos.
 */
internal object HipThrustArt : EquipmentArt(SymbolPalette.energia, period = 2.4f, restT = 1.1f), StartingPointShape {
    private val shoulder = Offset(14f, 37f)
    private val head = Offset(8.5f, 31f)
    private val foot = Offset(44f, 58f)

    /** Cuánto está arriba la cadera: 0 abajo, 1 arriba; sube, aguanta y baja. */
    internal fun lift(t: Float): Float = riseHoldFall(t, 0.2f, 0.8f, 1.2f, 1.9f)

    private fun hipAt(t: Float): Offset = Offset(34f, lerpF(34f, 23f, lift(t)))

    override fun drawStatic(pen: SymbolPen) {
        with(pen) {
            // Banco (tablero y patas) y suelo.
            box(3f, 39f, 25f, 43f, 1.6f, ink, SymbolStroke.LINE)
            line(7f, 43f, 7f, FLOOR_Y, soft, SymbolStroke.FINE)
            line(21f, 43f, 21f, FLOOR_Y, soft, SymbolStroke.FINE)
            line(6f, FLOOR_Y + 0.5f, 58f, FLOOR_Y + 0.5f, soft, SymbolStroke.FINE)
        }
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) {
        with(pen) {
            val hip = hipAt(t)
            val knee = Offset(hip.x + 12f, hip.y + 10f)
            line(shoulder, hip, ink, SymbolStroke.BODY)
            poly(hip, knee, foot, accent, SymbolStroke.BODY)
            ring(head.x, head.y, 3.4f, ink, SymbolStroke.BODY * 0.9f)
            // Barra sobre la cadera con un disco a cada lado.
            line(hip.x - 10f, hip.y - 2f, hip.x + 10f, hip.y - 2f, ink, SymbolStroke.LINE)
            box(hip.x - 13f, hip.y - 7f, hip.x - 10f, hip.y + 3f, 1.2f, accent, SymbolStroke.LINE)
            box(hip.x + 10f, hip.y - 7f, hip.x + 13f, hip.y + 3f, 1.2f, accent, SymbolStroke.LINE)
        }
    }

    override fun pointsAt(t: Float): List<Offset> {
        val hip = hipAt(t)
        return listOf(
            shoulder, head, hip, Offset(hip.x + 12f, hip.y + 10f), foot,
            Offset(hip.x - 13f, hip.y - 7f), Offset(hip.x + 13f, hip.y + 3f),
        )
    }
}

/**
 * «Saltos e impacto»: una figura en el aire sobre una línea de suelo; al aterrizar aparecen dos marcas de impacto a los lados de
 * los pies. Salta en bucle.
 */
internal object JumpsArt : EquipmentArt(SymbolPalette.energia, period = 1.6f, restT = 0.3f), StartingPointShape {
    private const val JUMP_HEIGHT = 16f

    /** Altura del salto en unidades: 0 en el suelo; sube y vuelve en la fase de vuelo. */
    internal fun lift(t: Float): Float = JUMP_HEIGHT * sin(PI.toFloat() * seg(t / period, 0.1f, 0.7f))

    /** Marcas de impacto: aparecen al aterrizar y se apagan antes del siguiente salto. */
    internal fun impact(t: Float): Float {
        val u = t / period
        return smooth(seg(u, 0.68f, 0.74f)) - smooth(seg(u, 0.9f, 1f))
    }

    /** Articulaciones de la figura en el instante [t]: cadera, rodillas, pies, brazos arriba y cabeza. */
    internal fun poseAt(t: Float): Pose {
        val h = lift(t)
        val hip = Offset(32f, 43f - h)
        val sh = Offset(hip.x + 0.8f, hip.y - 13f)
        val head = Offset(sh.x + 0.2f, sh.y - 5.8f)
        val kneeN = Offset(hip.x + 4.5f, hip.y + 7.5f)
        val footN = Offset(hip.x + 0.5f, hip.y + 15f)
        val kneeF = Offset(hip.x + 1.5f, hip.y + 7.5f)
        val footF = Offset(hip.x - 2.5f, hip.y + 15f)
        val elbowN = Offset(sh.x - 3.2f, sh.y - 7f)
        val handN = Offset(elbowN.x - 3.2f, elbowN.y - 6.8f)
        val elbowF = Offset(sh.x - 1.2f, sh.y - 7.2f)
        val handF = Offset(elbowF.x - 2f, elbowF.y - 6.8f)
        return Pose(hip, kneeN, footN, kneeF, footF, sh, head, elbowN, handN, elbowF, handF)
    }

    override fun drawStatic(pen: SymbolPen) {
        with(pen) { line(6f, FLOOR_Y + 0.5f, 58f, FLOOR_Y + 0.5f, soft, SymbolStroke.FINE) }
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) {
        with(pen) {
            fade(impact(t)) {
                line(20f, 56f, 15f, 52f, accent, SymbolStroke.FINE)
                line(44f, 56f, 49f, 52f, accent, SymbolStroke.FINE)
            }
            figure(poseAt(t), 3.4f, accent, accent.fainter(SymbolPalette.FAR_LIMB), SymbolStroke.BODY)
        }
    }

    override fun pointsAt(t: Float): List<Offset> {
        val p = poseAt(t)
        return listOf(p.hip, p.kneeN, p.footN, p.kneeF, p.footF, p.sh, p.head, p.elbowN, p.handN, p.elbowF, p.handF, Offset(15f, 52f), Offset(49f, 52f))
    }
}

// ---------------------------------------------------------------- estilo de mesa (V invertida, de frente)

/** Dónde están los dos antebrazos y las manos de un estilo de mesa en un instante (lienzo de 64 × 64). */
internal class ArmPose(
    val elbowOwn: Offset,
    val wristOwn: Offset,
    val handOwn: Offset,
    val elbowRival: Offset,
    val wristRival: Offset,
    val handRival: Offset,
    /** Cabeza, hombro y brazo propios: solo en el press (vacío en el resto). Empujan la mano rival hacia la mesa. */
    val torso: List<Offset>,
)

/** Centro de la mesa: el punto medio de los dos codos. Alrededor de él gira el agarre. */
private val ARM_CENTER = Offset(32f, 49f)

/** Codos sobre sus cojines, justo encima de la mesa (y = 50). */
private val ARM_ELBOW_OWN = Offset(22f, 49f)
private val ARM_ELBOW_RIVAL = Offset(42f, 49f)

/** Agarre central: la mano propia y la rival, solapadas, y la muñeca bajo cada una. */
private val ARM_HAND_OWN = Offset(31f, 21f)
private val ARM_HAND_RIVAL = Offset(33.5f, 21.5f)
private val ARM_WRIST_OWN = Offset(-1f, 4f)
private val ARM_WRIST_RIVAL = Offset(0.5f, 3.5f)

/** Cabeza, inicio y final del hombro del torso del press. */
private val ARM_HEAD = Offset(15f, 17f)
private val ARM_SHOULDER_TOP = Offset(16f, 23f)
private val ARM_SHOULDER_BOTTOM = Offset(23f, 31f)

/** Periodo del bucle de cada estilo, en segundos. */
private fun armPeriod(style: ArmStyle): Float = when (style) {
    ArmStyle.TOP_ROLL -> 2.2f
    ArmStyle.HOOK -> 2.4f
    ArmStyle.PRESS -> 2.0f
    ArmStyle.UNSURE -> 1.6f
}

/** El cuadro de reposo: el de cada estilo en su pose más reconocible. El press, con el agarre ya bajado a la mesa. */
private fun armRestT(style: ArmStyle): Float = if (style == ArmStyle.PRESS) armPeriod(style) / 2f else 0f

/** Gira [p] alrededor de [c] en [deg] grados; un giro positivo lleva el agarre hacia el lado rival. */
private fun turnAround(p: Offset, c: Offset, deg: Float): Offset {
    val a = deg * DEG
    val dx = p.x - c.x
    val dy = p.y - c.y
    return Offset(c.x + dx * cos(a) - dy * sin(a), c.y + dx * sin(a) + dy * cos(a))
}

/** Una pose con sus dos antebrazos: el codo fijo sobre su cojín y la muñeca bajo cada mano. */
private fun armPose(hand: Offset, rival: Offset, torso: List<Offset>): ArmPose = ArmPose(
    elbowOwn = ARM_ELBOW_OWN,
    wristOwn = hand + ARM_WRIST_OWN,
    handOwn = hand,
    elbowRival = ARM_ELBOW_RIVAL,
    wristRival = rival + ARM_WRIST_RIVAL,
    handRival = rival,
    torso = torso,
)

/**
 * La pose de un estilo en el instante [t]. Top roll: la mano propia queda encima de la rival y el agarre gira −12° hacia el lado
 * propio mientras la mano propia sube 3 u. Hook: la mano propia es una copa debajo de la rival y los antebrazos se inclinan −18°
 * hacia el cuerpo. Press: el agarre gira +35° hacia el lado rival y baja a la mesa, con la cabeza y el hombro propios acompañando.
 * Aún no lo sé: los antebrazos quedan verticales y el agarre se balancea ±6° en el centro.
 */
internal fun armPoseAt(style: ArmStyle, t: Float): ArmPose {
    val period = armPeriod(style)
    val u = pingPong(t / period)
    return when (style) {
        ArmStyle.TOP_ROLL -> {
            val turn = -12f * u
            val hand = turnAround(Offset(31f, 17.5f), ARM_CENTER, turn) + Offset(0f, -3f * u)
            armPose(hand, turnAround(ARM_HAND_RIVAL, ARM_CENTER, turn), emptyList())
        }
        ArmStyle.HOOK -> {
            val turn = -18f * u
            armPose(turnAround(Offset(31f, 25.5f), ARM_CENTER, turn), turnAround(ARM_HAND_RIVAL, ARM_CENTER, turn), emptyList())
        }
        ArmStyle.PRESS -> {
            val turn = 35f * u
            val shift = Offset(3.5f * u, 2.5f * u)
            armPose(
                hand = turnAround(ARM_HAND_OWN, ARM_CENTER, turn),
                rival = turnAround(ARM_HAND_RIVAL, ARM_CENTER, turn),
                torso = listOf(ARM_HEAD + shift, ARM_SHOULDER_TOP + shift, ARM_SHOULDER_BOTTOM + shift),
            )
        }
        ArmStyle.UNSURE -> {
            val sway = 6f * sin(TAU * t / period)
            armPose(turnAround(ARM_HAND_OWN, ARM_CENTER, sway), turnAround(ARM_HAND_RIVAL, ARM_CENTER, sway), emptyList())
        }
    }
}

/** Una escena de estilo de mesa: la mesa con sus cojines, los dos antebrazos en V invertida y las manos del agarre. */
internal class ArmStyleArt(private val style: ArmStyle) :
    EquipmentArt(SymbolPalette.musculo, armPeriod(style), armRestT(style)), StartingPointShape {

    override fun drawStatic(pen: SymbolPen) {
        with(pen) {
            line(6f, 50f, 58f, 50f, ink, SymbolStroke.BODY)
            fillBox(18.5f, 48.75f, 25.5f, 51.25f, 1.2f, soft)
            fillBox(38.5f, 48.75f, 45.5f, 51.25f, 1.2f, soft)
        }
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) {
        with(pen) {
            val p = armPoseAt(style, t)
            // Antebrazo rival en tinta suave; el propio, en tinta que toma el acento al elegir.
            line(p.elbowRival, p.wristRival, soft, SymbolStroke.BODY)
            line(p.elbowOwn, p.wristOwn, accent, SymbolStroke.BODY)
            if (p.torso.size == 3) {
                ring(p.torso[0].x, p.torso[0].y, 4f, ink, SymbolStroke.LINE)
                line(p.torso[1], p.torso[2], ink, SymbolStroke.BODY)
                line(p.torso[2], p.elbowOwn, ink, SymbolStroke.BODY)
            }
            ring(p.handRival.x, p.handRival.y, 4.5f, soft, SymbolStroke.LINE)
            if (style == ArmStyle.HOOK) {
                // La mano propia es una copa abierta hacia la rival.
                arc(p.handOwn.x, p.handOwn.y, 4.5f, 0f, 270f, accent, SymbolStroke.LINE)
            } else {
                ring(p.handOwn.x, p.handOwn.y, 4.5f, accent, SymbolStroke.LINE)
            }
            if (style == ArmStyle.TOP_ROLL) {
                // Un arco corto de dedos que pasa por encima de la mano rival.
                arc(p.handRival.x, p.handRival.y, 5.6f, -160f, 140f, accent, SymbolStroke.LINE)
            }
        }
    }

    override fun pointsAt(t: Float): List<Offset> {
        val p = armPoseAt(style, t)
        return listOf(p.elbowOwn, p.wristOwn, p.handOwn, p.elbowRival, p.wristRival, p.handRival) + p.torso +
            listOf(Offset(6f, 50f), Offset(58f, 50f), Offset(18.5f, 48.75f), Offset(45.5f, 51.25f))
    }
}

// ---------------------------------------------------------------- elecciones

/** El símbolo de cada grupo de «evitar»: los patrones de siempre cuando encajan, y cuatro dibujos nuevos. */
internal fun avoidArtOf(group: AvoidGroup): SymbolArt = when (group) {
    AvoidGroup.BARBELL_SQUAT -> patternArt(ExercisePattern.SQUAT)
    AvoidGroup.DEADLIFT -> patternArt(ExercisePattern.HINGE)
    AvoidGroup.BENCH_PRESS -> patternArt(ExercisePattern.PRESS_HORIZONTAL)
    AvoidGroup.OVERHEAD_PRESS -> patternArt(ExercisePattern.PRESS_VERTICAL)
    AvoidGroup.PULL_UPS -> patternArt(ExercisePattern.PULL_VERTICAL)
    // Fondos: la figura en paralelas con los codos a 90° que ya dibuja el símbolo de paralelas.
    AvoidGroup.DIPS -> ParallelBarsArt
    AvoidGroup.LUNGES -> patternArt(ExercisePattern.LUNGE)
    AvoidGroup.HIP_THRUST -> HipThrustArt
    AvoidGroup.JUMPS -> JumpsArt
    AvoidGroup.RUNNING -> RUNNING_ART
}

/** El símbolo de cada base de cardio, de menos a más: caminar, trote, carrera y series. */
internal fun cardioArtOf(base: CardioBase): SymbolArt = when (base) {
    CardioBase.LOW -> WALK_ART
    CardioBase.MODERATE -> JOG_ART
    CardioBase.HIGH -> RUN_ART
    CardioBase.INTERVALS -> SPRINT_ART
}

private val ARM_ARTS: Map<ArmStyle, ArmStyleArt> = ArmStyle.entries.associateWith { ArmStyleArt(it) }

/** La escena de cada estilo de mesa. */
internal fun armArtOf(style: ArmStyle): SymbolArt = ARM_ARTS.getValue(style)

// ---------------------------------------------------------------- celda y cuadrícula de elección

/** Duración del trazo de «evitar» al elegir (y su vuelta al soltar). */
private const val STRIKE_MS = 260

/** Duración del cambio de color de la etiqueta al elegir. */
private const val LABEL_MS = 300

/** Dónde empieza y acaba la diagonal de «evitar», en unidades del lienzo (de arriba a la izquierda a abajo a la derecha). */
private const val STRIKE_FROM = 9f
private const val STRIKE_TO = 55f

/** Una opción de un paso de símbolos: el dibujo, su texto, su estado y lo que pasa al tocarla. */
internal class StartingPointChoice(
    val art: SymbolArt,
    val label: String,
    val tag: String,
    val selected: Boolean,
    val description: String,
    val role: Role,
    val struck: Boolean = false,
    val onChoose: () -> Unit,
)

/**
 * Cuadrícula de opciones sin caja. [columns] son las columnas normales y [wideColumns], las que se usan desde [wideFrom]. Un
 * único reloj mueve las opciones seleccionadas, solo mientras haya alguna, se vea la cuadrícula y la app esté en primer plano.
 */
@Composable
internal fun StartingPointGrid(
    choices: List<StartingPointChoice>,
    columns: Int,
    canvas: Dp,
    artScale: Float,
    labelStyle: TextStyle,
    modifier: Modifier = Modifier,
    wideColumns: Int = columns,
    wideFrom: Dp = Dp.Infinity,
) {
    val reduced = wizardReducedMotion()
    val foreground = rememberEntrenoForeground()
    var visible by remember { mutableStateOf(true) }
    val anySelected = choices.any { it.selected }
    val clock = rememberSymbolClock(active = !reduced && foreground && visible && anySelected)
    BoxWithConstraints(modifier = modifier.fillMaxWidth().onSymbolsVisible { visible = it }) {
        val count = if (maxWidth >= wideFrom) wideColumns else columns
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (row in choices.chunked(count)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (choice in row) {
                        key(choice.tag) {
                            StartingPointCell(
                                choice = choice,
                                clock = clock,
                                reduced = reduced,
                                canvas = canvas,
                                artScale = artScale,
                                labelStyle = labelStyle,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    repeat(count - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * Una opción: el símbolo y su texto, SIN tarjeta. Es una casilla (`Role.Checkbox`) o un botón de opción (`Role.RadioButton`)
 * según [StartingPointChoice.role]; se anuncia con su [StartingPointChoice.description]. Con [StartingPointChoice.struck], una
 * diagonal tacha el dibujo en 260 ms y se queda (con movimiento reducido ya está dibujada).
 */
@Composable
private fun StartingPointCell(
    choice: StartingPointChoice,
    clock: SymbolClock,
    reduced: Boolean,
    canvas: Dp,
    artScale: Float,
    labelStyle: TextStyle,
    modifier: Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale = animateFloatAsState(
        targetValue = if (pressed && !reduced) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "startingPressScale",
    )
    val strike = animateFloatAsState(
        targetValue = if (choice.struck) 1f else 0f,
        animationSpec = if (reduced) snap() else tween(STRIKE_MS),
        label = "startingStrike",
    )
    val labelColor by animateColorAsState(
        targetValue = if (choice.selected) WizardColors.text else WizardColors.textMuted,
        animationSpec = if (reduced) snap() else tween(LABEL_MS),
        label = "startingLabel",
    )
    val choose = if (choice.role == Role.RadioButton) {
        Modifier.selectable(
            selected = choice.selected,
            interactionSource = interaction,
            indication = null,
            role = Role.RadioButton,
            onClick = choice.onChoose,
        )
    } else {
        Modifier.toggleable(
            value = choice.selected,
            interactionSource = interaction,
            indication = null,
            role = Role.Checkbox,
            onValueChange = { choice.onChoose() },
        )
    }
    val canvasModifier = Modifier.size(canvas).drawWithContent {
        drawContent()
        drawStrike(strike.value, choice.art, artScale)
    }
    Column(
        modifier = modifier
            .testTag(choice.tag)
            .then(choose)
            // Va DESPUÉS de elegir: conserva su rol y su estado y descarta el texto de la etiqueta (no se lee dos veces).
            .clearAndSetSemantics { contentDescription = choice.description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.graphicsLayer {
                val s = pressScale.value
                scaleX = s
                scaleY = s
            },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SymbolCanvas(
                art = choice.art,
                selected = choice.selected,
                clock = clock,
                reducedMotion = reduced,
                modifier = canvasModifier,
                artScale = artScale,
                badgeRadius = 8.dp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = choice.label,
                style = labelStyle.copy(fontWeight = if (choice.selected) FontWeight.Bold else FontWeight.Medium),
                color = labelColor,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** La diagonal de «evitar», de arriba a la izquierda hacia abajo a la derecha, con su avance [progress] (0 a 1). */
private fun DrawScope.drawStrike(progress: Float, art: SymbolArt, artScale: Float) {
    if (progress <= 0.001f) return
    val k = min(size.width / art.width, size.height / art.height) * artScale
    val ox = (size.width - art.width * k) / 2f
    val oy = (size.height - art.height * k) / 2f
    val from = Offset(ox + STRIKE_FROM * k, oy + STRIKE_FROM * k)
    val to = Offset(ox + STRIKE_TO * k, oy + STRIKE_TO * k)
    val end = Offset(from.x + (to.x - from.x) * progress, from.y + (to.y - from.y) * progress)
    drawLine(SymbolPalette.musculo, from, end, SymbolStroke.LINE * k, StrokeCap.Round)
}

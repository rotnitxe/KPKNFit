package com.example.kpkn.domain.biomechanics.testing

import com.example.kpkn.domain.biomechanics.kinematics.Side
import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Estado del movimiento en un instante: [bL]/[bR] son el progreso del brazo o pierna izquierdo/derecho (0 = reposo,
 * 1 = giro; con A < 1 no llegan a 1), [rootX] el desplazamiento de la raíz, [unrack] el desplazamiento de las manos al
 * sacar la barra (1 = en el soporte) y [gaitAmp]/[gaitPhase] el paso al caminar.
 */
internal class MotionState(
    val bL: Double,
    val bR: Double,
    val rootX: Double = 0.0,
    val unrack: Double = 0.0,
    val gaitAmp: Double = 0.0,
    val gaitPhase: Double = 0.0,
)

/** Propiedades fijas de un movimiento. */
internal class MovementDef(
    /** Arranque E (la excéntrica sale del reposo) o C (la concéntrica sale del reposo). */
    val startsEccentric: Boolean,
    /** La postura de reposo es de pie: al caminar se mueven las piernas. */
    val standingRest: Boolean,
    /** Brazos o piernas independientes: `Rep.side` y `alternating` tienen sentido. */
    val supportsSide: Boolean,
)

/**
 * Las 16 posturas. Cada función devuelve la postura (en el sistema local del atleta: tobillos de pie en torno a x = 0)
 * para un [MotionState]; la geometría es siempre de longitudes exactas (cadenas de dos segmentos con ik).
 * Longitudes en metros a partir de la altura (Drillis–Contini); ángulos en radianes salvo que se diga.
 */
internal object SynthMovements {
    private const val GAIT_THIGH_DEG = 26.0
    private const val GAIT_KNEE_DEG = 40.0

    fun def(m: SynthMovement): MovementDef = when (m) {
        SynthMovement.SQUAT -> MovementDef(startsEccentric = true, standingRest = true, supportsSide = false)
        SynthMovement.LUNGE -> MovementDef(startsEccentric = true, standingRest = false, supportsSide = true)
        SynthMovement.DEADLIFT -> MovementDef(startsEccentric = false, standingRest = false, supportsSide = false)
        SynthMovement.RDL -> MovementDef(startsEccentric = true, standingRest = true, supportsSide = false)
        SynthMovement.HIP_THRUST -> MovementDef(startsEccentric = false, standingRest = false, supportsSide = false)
        SynthMovement.BENCH -> MovementDef(startsEccentric = true, standingRest = false, supportsSide = true)
        SynthMovement.PUSH_UP -> MovementDef(startsEccentric = true, standingRest = false, supportsSide = false)
        SynthMovement.OHP -> MovementDef(startsEccentric = false, standingRest = true, supportsSide = true)
        SynthMovement.PULL_UP -> MovementDef(startsEccentric = false, standingRest = false, supportsSide = false)
        SynthMovement.LAT_PULLDOWN -> MovementDef(startsEccentric = false, standingRest = false, supportsSide = true)
        SynthMovement.ROW -> MovementDef(startsEccentric = false, standingRest = false, supportsSide = true)
        SynthMovement.CURL -> MovementDef(startsEccentric = false, standingRest = true, supportsSide = true)
        SynthMovement.TRICEPS_PUSHDOWN -> MovementDef(startsEccentric = false, standingRest = true, supportsSide = true)
        SynthMovement.LATERAL_RAISE -> MovementDef(startsEccentric = false, standingRest = true, supportsSide = true)
        SynthMovement.LEG_EXTENSION -> MovementDef(startsEccentric = false, standingRest = false, supportsSide = false)
        SynthMovement.CALF_RAISE -> MovementDef(startsEccentric = false, standingRest = true, supportsSide = false)
    }

    fun posture(m: SynthMovement, st: MotionState, a: Anthro, frontSide: Side): Posture = when (m) {
        SynthMovement.SQUAT -> squat(st, a)
        SynthMovement.LUNGE -> lunge(st, a, frontSide)
        SynthMovement.DEADLIFT -> deadlift(st, a)
        SynthMovement.RDL -> rdl(st, a)
        SynthMovement.HIP_THRUST -> hipThrust(st, a)
        SynthMovement.BENCH -> bench(st, a)
        SynthMovement.PUSH_UP -> pushUp(st, a)
        SynthMovement.OHP -> ohp(st, a)
        SynthMovement.PULL_UP -> pullUp(st, a)
        SynthMovement.LAT_PULLDOWN -> latPulldown(st, a)
        SynthMovement.ROW -> row(st, a)
        SynthMovement.CURL -> curl(st, a)
        SynthMovement.TRICEPS_PUSHDOWN -> tricepsPushdown(st, a)
        SynthMovement.LATERAL_RAISE -> lateralRaise(st, a)
        SynthMovement.LEG_EXTENSION -> legExtension(st, a)
        SynthMovement.CALF_RAISE -> calfRaise(st, a)
    }

    // ---------- ángulo principal (flexión en grados; 0 = extendido) ----------

    /** Ángulo principal del movimiento medido en los puntos 3D del lado [side]. */
    fun primaryAngleDeg(m: SynthMovement, j: Joints, side: Side, a: Anthro): Double {
        fun lm(left: PoseLandmark, right: PoseLandmark) = if (side == Side.LEFT) left else right
        val s = j[lm(PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER)]
        val e = j[lm(PoseLandmark.LEFT_ELBOW, PoseLandmark.RIGHT_ELBOW)]
        val w = j[lm(PoseLandmark.LEFT_WRIST, PoseLandmark.RIGHT_WRIST)]
        val h = j[lm(PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP)]
        val k = j[lm(PoseLandmark.LEFT_KNEE, PoseLandmark.RIGHT_KNEE)]
        val an = j[lm(PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE)]
        return when (m) {
            SynthMovement.SQUAT, SynthMovement.LUNGE, SynthMovement.LEG_EXTENSION -> 180.0 - includedDeg(h, k, an)
            SynthMovement.DEADLIFT, SynthMovement.RDL, SynthMovement.HIP_THRUST -> 180.0 - includedDeg(s, h, k)
            SynthMovement.LATERAL_RAISE -> {
                val len = (e - s).norm()
                Math.toDegrees(acos(((s.y - e.y) / len).coerceIn(-1.0, 1.0)))
            }
            SynthMovement.CALF_RAISE -> {
                val heel = j[lm(PoseLandmark.LEFT_HEEL, PoseLandmark.RIGHT_HEEL)]
                val toe = j[lm(PoseLandmark.LEFT_FOOT_INDEX, PoseLandmark.RIGHT_FOOT_INDEX)]
                Math.toDegrees(atan2(heel.y - toe.y, toe.x - heel.x))
            }
            else -> 180.0 - includedDeg(s, e, w)
        }
    }

    /** Ángulo incluido en [b] en grados (0..180), en 3D. */
    fun includedDeg(a: V3, b: V3, c: V3): Double {
        val ba = a - b
        val bc = c - b
        val cx = ba.y * bc.z - ba.z * bc.y
        val cy = ba.z * bc.x - ba.x * bc.z
        val cz = ba.x * bc.y - ba.y * bc.x
        return Math.toDegrees(atan2(sqrt(cx * cx + cy * cy + cz * cz), ba.dot(bc)))
    }

    // ---------- piezas comunes ----------

    private fun v(x: Double, y: Double) = V3(x, y, 0.0)

    private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t

    private fun lerp(a: V3, b: V3, t: Double) = V3(lerp(a.x, b.x, t), lerp(a.y, b.y, t), lerp(a.z, b.z, t))

    /** x del tobillo de pie (con la rodilla en su flexión de reposo). */
    private fun standAnkleX(a: Anthro) = -a.shank * sin(a.standKneeFlex)

    /** x del medio pie (entre el talón y la punta) con el tobillo en [ankleX]. */
    private fun midFootX(a: Anthro, ankleX: Double) = ankleX + (a.toeAhead - a.heelBack) / 2.0

    private class Legs(val hip: V3, val ankleL: V3, val ankleR: V3)

    /**
     * Piernas de pie: cinemática directa desde la cadera. Sin paso ([amp] = 0) el muslo queda vertical y el tobillo detrás
     * de la rodilla (flexión de reposo); con paso, el muslo oscila ±26° y la rodilla se dobla en la fase de balanceo.
     * La cadera sube o baja para que el pie más bajo toque el suelo.
     */
    private fun standingLegs(a: Anthro, amp: Double, phase: Double): Legs {
        val k0 = a.standKneeFlex
        fun ankleRel(phi: Double): V3 {
            val thighAngle = amp * Math.toRadians(GAIT_THIGH_DEG) * sin(phi)
            val knee = amp * Math.toRadians(GAIT_KNEE_DEG) * max(0.0, cos(phi)) + k0
            val kneePos = v(a.thigh * sin(thighAngle), -a.thigh * cos(thighAngle))
            val shankAngle = thighAngle - knee
            return kneePos + v(a.shank * sin(shankAngle), -a.shank * cos(shankAngle))
        }
        val l = ankleRel(phase)
        val r = ankleRel(phase + Math.PI)
        val hipY = a.ankleHeight - minOf(l.y, r.y)
        val hip = v(0.0, hipY)
        return Legs(
            hip,
            V3(l.x, hipY + l.y, a.hipHalf),
            V3(r.x, hipY + r.y, -a.hipHalf),
        )
    }

    /** Brazo por cinemática directa: flexión [flex] y abducción [abd] del hombro, flexión [elbowFlex] del codo (rad). */
    private fun armFk(shoulder: V3, a: Anthro, zSign: Double, flex: Double, abd: Double, elbowFlex: Double): Pair<V3, V3> {
        val d = V3(sin(flex) * cos(abd), -cos(flex) * cos(abd), zSign * sin(abd))
        val elbow = shoulder + d * a.upperArm
        // El codo flexiona hacia delante: n = X proyectado sobre el plano perpendicular al brazo.
        var n = V3(1.0, 0.0, 0.0) - d * d.x
        if (n.norm() < 1e-3) n = V3(0.0, 1.0, 0.0) - d * d.y
        val dir = d * cos(elbowFlex) + n.unit() * sin(elbowFlex)
        return Pair(elbow, elbow + dir * a.forearm)
    }

    /**
     * Interpola de [from] a [to] (vectores relativos al hombro) con el radio lineal y el ángulo desde la vertical
     * con b^1,5: el módulo disminuye de forma monótona y la trayectoria no se abre de golpe.
     */
    private fun polarBlend(from: V3, to: V3, b: Double): V3 {
        val a0 = atan2(from.x, from.y)
        val a1 = atan2(to.x, to.y)
        val r = lerp(from.norm(), to.norm(), b)
        val ang = lerp(a0, a1, Math.pow(b.coerceIn(0.0, 1.0), 1.5))
        return v(r * sin(ang), r * cos(ang))
    }

    private fun shoulderOf(post: V3, a: Anthro, zSign: Double) = post + V3(0.0, 0.0, zSign * a.shoulderHalf)

    private fun trunkUp(angle: Double) = v(sin(angle), cos(angle))

    private fun trunkForward(angle: Double) = v(cos(angle), -sin(angle))

    /** Brazos colgando (opcionalmente con algo de flexión de hombro y de codo). */
    private fun hangingArms(
        shoulder: V3,
        a: Anthro,
        flex: Double = 0.0,
        elbowFlex: Double = Math.toRadians(8.0),
    ): List<V3> {
        val l = armFk(shoulderOf(shoulder, a, 1.0), a, 1.0, flex, 0.0, elbowFlex)
        val r = armFk(shoulderOf(shoulder, a, -1.0), a, -1.0, flex, 0.0, elbowFlex)
        return listOf(l.first, l.second, r.first, r.second)
    }

    /** Postura de pie con las piernas de [standingLegs] y el tronco vertical; los brazos los pone [arms]. */
    private fun standing(st: MotionState, a: Anthro, arms: (V3, Double) -> List<V3>): Posture {
        val legs = standingLegs(a, st.gaitAmp, st.gaitPhase)
        val shoulder = legs.hip + v(0.0, a.trunk)
        val e = arms(shoulder, 0.0)
        return Posture(legs.hip, shoulder, legs.ankleL, legs.ankleR, e[0], e[1], e[2], e[3])
    }

    // ---------- sentadilla ----------

    private fun squat(st: MotionState, a: Anthro): Posture {
        val bars = { shoulder: V3, trunkAngle: Double -> squatArms(shoulder, trunkAngle, a) }
        if (st.gaitAmp > 1e-9) return standing(st, a, bars)
        val b = max(st.bL, st.bR)
        val k0 = a.standKneeFlex
        val kappa = lerp(k0, Math.toRadians(125.0), b)
        val delta = k0 + 0.24 * (kappa - k0)
        val ankleX = standAnkleX(a)
        val knee = v(ankleX + a.shank * sin(delta), a.ankleHeight + a.shank * cos(delta))
        val thighAngle = delta - kappa
        val hip = knee + v(a.thigh * sin(thighAngle), a.thigh * cos(thighAngle))
        // La barra queda sobre el medio pie: de pie el hombro está sobre la cadera, abajo sobre el medio pie.
        val sinLean = ((midFootX(a, ankleX) * b) - hip.x) / a.trunk
        val lean = asin(sinLean.coerceIn(-0.5, 0.95))
        val shoulder = hip + trunkUp(lean) * a.trunk
        val e = squatArms(shoulder, lean, a)
        return Posture(
            hip, shoulder,
            V3(ankleX, a.ankleHeight, a.hipHalf), V3(ankleX, a.ankleHeight, -a.hipHalf),
            e[0], e[1], e[2], e[3],
        )
    }

    /** Barra sobre la espalda: las manos agarran cerca del hombro y los codos apuntan abajo y atrás. */
    private fun squatArms(shoulder: V3, trunkAngle: Double, a: Anthro): List<V3> {
        val up = trunkUp(trunkAngle)
        val fwd = trunkForward(trunkAngle)
        val out = ArrayList<V3>(4)
        for (zs in doubleArrayOf(1.0, -1.0)) {
            val s = shoulderOf(shoulder, a, zs)
            val wrist = s + up * (0.063 * a.height) - fwd * (0.017 * a.height)
            val elbow = TwoLink.solve(s, wrist, a.upperArm, a.forearm, up * -1.0 - fwd * 0.3)
            out.add(elbow)
            out.add(wrist)
        }
        return listOf(out[0], out[1], out[2], out[3])
    }

    // ---------- zancada (sentadilla dividida) ----------

    private fun lunge(st: MotionState, a: Anthro, front: Side): Posture {
        val b = max(st.bL, st.bR)
        val h = a.height
        val ankleX = standAnkleX(a)
        val stride = 0.40 * h
        val pitch = Math.toRadians(55.0)
        // Pie trasero: la punta apoyada y el talón arriba; el tobillo sale del contacto con el suelo.
        val toe = V3(a.toeAhead, -a.footDrop)
        val toeRot = SynthBody.rotateClockwise(toe, pitch)
        val rearAnkleY = 0.010 * h - toeRot.y
        val frontAnkle = v(ankleX, a.ankleHeight)
        val rearAnkle = v(ankleX - stride, rearAnkleY)
        val hipX = ankleX - 0.47 * stride
        val hipY = lerp(0.489 * h, 0.297 * h, b)
        val hip = v(hipX, hipY)
        val lean = Math.toRadians(4.0)
        val shoulder = hip + trunkUp(lean) * a.trunk
        val e = hangingArms(shoulder, a)
        val (fz, rz) = if (front == Side.LEFT) Pair(a.hipHalf, -a.hipHalf) else Pair(-a.hipHalf, a.hipHalf)
        val frontA = V3(frontAnkle.x, frontAnkle.y, fz)
        val rearA = V3(rearAnkle.x, rearAnkle.y, rz)
        return if (front == Side.LEFT) {
            Posture(hip, shoulder, frontA, rearA, e[0], e[1], e[2], e[3], 0.0, pitch)
        } else {
            Posture(hip, shoulder, rearA, frontA, e[0], e[1], e[2], e[3], pitch, 0.0)
        }
    }

    // ---------- peso muerto ----------

    private fun deadlift(st: MotionState, a: Anthro): Posture {
        val b = max(st.bL, st.bR)
        val h = a.height
        val ankleX = standAnkleX(a)
        val legs = standingLegs(a, 0.0, 0.0)
        // Abajo: hombro sobre el medio pie y casi a la altura del brazo extendido sobre la barra; arriba: de pie.
        val shoulderBottom = v(midFootX(a, ankleX) + 0.01, 0.51 * h)
        val shoulderTop = legs.hip + v(0.0, a.trunk)
        val shoulder = lerp(shoulderBottom, shoulderTop, b)
        val lean = lerp(Math.toRadians(40.0), 0.0, b)
        val hip = shoulder - trunkUp(lean) * a.trunk
        val e = hangingArms(shoulder, a, elbowFlex = Math.toRadians(2.0))
        return Posture(
            hip, shoulder,
            V3(ankleX, a.ankleHeight, a.hipHalf), V3(ankleX, a.ankleHeight, -a.hipHalf),
            e[0], e[1], e[2], e[3],
        )
    }

    // ---------- peso muerto rumano ----------

    private fun rdl(st: MotionState, a: Anthro): Posture {
        val arms = { shoulder: V3, _: Double -> hangingArms(shoulder, a, elbowFlex = Math.toRadians(2.0)) }
        if (st.gaitAmp > 1e-9) return standing(st, a, arms)
        val b = max(st.bL, st.bR)
        val k0 = a.standKneeFlex
        val kappa = lerp(k0, Math.toRadians(40.0), b)
        val delta = k0 + 0.2 * (kappa - k0)
        val ankleX = standAnkleX(a)
        val knee = v(ankleX + a.shank * sin(delta), a.ankleHeight + a.shank * cos(delta))
        val thighAngle = delta - kappa
        val hip = knee + v(a.thigh * sin(thighAngle), a.thigh * cos(thighAngle))
        val lean = Math.toRadians(62.0) * b
        val shoulder = hip + trunkUp(lean) * a.trunk
        val e = arms(shoulder, lean)
        return Posture(
            hip, shoulder,
            V3(ankleX, a.ankleHeight, a.hipHalf), V3(ankleX, a.ankleHeight, -a.hipHalf),
            e[0], e[1], e[2], e[3],
        )
    }

    // ---------- hip thrust ----------

    private fun hipThrust(st: MotionState, a: Anthro): Posture {
        val b = max(st.bL, st.bR)
        // Pies fijos en x = 0; el hombro (omóplatos en el banco) fijo detrás; arriba el muslo queda horizontal.
        val ankleY = a.ankleHeight
        val shoulder = v(-(a.thigh + a.trunk), ankleY + a.shank)
        val theta = lerp(Math.toRadians(45.0), 0.0, b) // tronco por debajo de la horizontal
        val hip = shoulder + v(a.trunk * cos(theta), -a.trunk * sin(theta))
        // Las manos sujetan la barra sobre la cadera; los codos van abajo.
        val out = ArrayList<V3>(4)
        for (zs in doubleArrayOf(1.0, -1.0)) {
            val s = shoulderOf(shoulder, a, zs)
            val wrist = V3(hip.x + 0.02 * a.height, hip.y + 0.07 * a.height, zs * a.shoulderHalf)
            val elbow = TwoLink.solve(s, wrist, a.upperArm, a.forearm, v(0.0, -1.0))
            out.add(elbow)
            out.add(wrist)
        }
        return Posture(
            hip, shoulder,
            V3(0.0, ankleY, a.hipHalf), V3(0.0, ankleY, -a.hipHalf),
            out[0], out[1], out[2], out[3],
            kneePole = v(0.3, 1.0),
        )
    }

    // ---------- press de banca ----------

    private fun bench(st: MotionState, a: Anthro): Posture {
        val h = a.height
        val benchTop = 0.246 * h
        val shoulder = v(-0.40, benchTop + 0.069 * h)
        val hip = shoulder + v(a.trunk, 0.0)
        val ankle = v(hip.x + 0.17 * h, a.ankleHeight)
        // Arriba la barra está sobre el hombro, inclinada hacia la cabeza cuando está en el soporte; abajo, en el pecho.
        val racked = Math.toRadians(15.0) * st.unrack
        val topWrist = v(-a.armReach * sin(racked), a.armReach * cos(racked))
        val bottomWrist = v(0.16 * h, 0.057 * h)
        // La muñeca va del hombro (arriba) al pecho en coordenadas polares: el radio baja siempre, así que la flexión
        // del codo es monótona; el ángulo llega tarde (b^1,5) y la barra baja casi vertical al principio.
        val out = ArrayList<V3>(4)
        for ((zs, b) in listOf(Pair(1.0, st.bL), Pair(-1.0, st.bR))) {
            val s = shoulderOf(shoulder, a, zs)
            val wrist = s + polarBlend(topWrist, bottomWrist, b)
            val elbow = TwoLink.solve(s, wrist, a.upperArm, a.forearm, v(0.4, -1.0))
            out.add(elbow)
            out.add(wrist)
        }
        return Posture(
            hip, shoulder,
            V3(ankle.x, ankle.y, a.hipHalf), V3(ankle.x, ankle.y, -a.hipHalf),
            out[0], out[1], out[2], out[3],
            kneePole = v(0.3, 1.0),
        )
    }

    // ---------- flexiones ----------

    private fun pushUp(st: MotionState, a: Anthro): Posture {
        val b = max(st.bL, st.bR)
        val h = a.height
        // El cuerpo es una tabla que gira sobre las puntas de los pies; las manos quedan fijas en el suelo.
        val pitch = Math.toRadians(70.0)
        val toeTip = SynthBody.rotateClockwise(V3(a.toeAhead, -a.footDrop), pitch)
        val ankle = v(-toeTip.x, 0.010 * h - toeTip.y)
        val board = a.legReach + a.trunk
        val shoulderTopY = 0.023 * h + a.armReach
        val shoulderBottomY = 0.194 * h
        val shoulderY = lerp(shoulderTopY, shoulderBottomY, b)
        val gamma = asin(((shoulderY - ankle.y) / board).coerceIn(-0.9, 0.9))
        val e = v(cos(gamma), sin(gamma))
        val hip = ankle + e * a.legReach
        val shoulder = hip + e * a.trunk
        val gammaTop = asin(((shoulderTopY - ankle.y) / board).coerceIn(-0.9, 0.9))
        val handX = ankle.x + board * cos(gammaTop)
        val out = ArrayList<V3>(4)
        for (zs in doubleArrayOf(1.0, -1.0)) {
            val s = shoulderOf(shoulder, a, zs)
            val wrist = V3(handX, 0.023 * h, zs * a.shoulderHalf)
            val elbow = TwoLink.solve(s, wrist, a.upperArm, a.forearm, v(-1.0, 0.3))
            out.add(elbow)
            out.add(wrist)
        }
        return Posture(
            hip, shoulder,
            V3(ankle.x, ankle.y, a.hipHalf), V3(ankle.x, ankle.y, -a.hipHalf),
            out[0], out[1], out[2], out[3],
            footPitchL = pitch, footPitchR = pitch,
            kneePole = v(0.0, -1.0),
        )
    }

    // ---------- press militar ----------

    private fun ohp(st: MotionState, a: Anthro): Posture {
        val h = a.height
        val arms = { shoulder: V3, _: Double ->
            val out = ArrayList<V3>(4)
            for ((zs, b) in listOf(Pair(1.0, st.bL), Pair(-1.0, st.bR))) {
                val s = shoulderOf(shoulder, a, zs)
                val wrist = lerp(s + v(0.091 * h, 0.023 * h), s + v(0.01 * h, a.armReach), b)
                val elbow = TwoLink.solve(s, wrist, a.upperArm, a.forearm, v(0.3, -1.0))
                out.add(elbow)
                out.add(wrist)
            }
            listOf(out[0], out[1], out[2], out[3])
        }
        val legs = standingLegs(a, st.gaitAmp, st.gaitPhase)
        val lean = -Math.toRadians(3.0) * max(st.bL, st.bR)
        val shoulder = legs.hip + trunkUp(lean) * a.trunk
        val e = arms(shoulder, lean)
        return Posture(legs.hip, shoulder, legs.ankleL, legs.ankleR, e[0], e[1], e[2], e[3])
    }

    // ---------- dominadas ----------

    private fun pullUp(st: MotionState, a: Anthro): Posture {
        val b = max(st.bL, st.bR)
        val h = a.height
        val barY = 1.32 * h
        val barX = 0.03 * h
        val shoulderHang = v(barX, barY - a.armReach)
        val shoulderTop = v(barX - 0.07 * h, barY - 0.08 * h)
        val shoulder = lerp(shoulderHang, shoulderTop, b)
        val lean = -Math.toRadians(15.0) * b
        val hip = shoulder - trunkUp(lean) * a.trunk
        // Piernas colgando: muslo ligeramente adelantado y rodilla flexionada.
        val thighAngle = Math.toRadians(4.0)
        val kneeFlex = Math.toRadians(20.0)
        val knee = hip + v(a.thigh * sin(thighAngle), -a.thigh * cos(thighAngle))
        val ankle = knee + v(a.shank * sin(thighAngle - kneeFlex), -a.shank * cos(thighAngle - kneeFlex))
        val out = ArrayList<V3>(4)
        for (zs in doubleArrayOf(1.0, -1.0)) {
            val s = shoulderOf(shoulder, a, zs)
            val wrist = V3(barX, barY, zs * a.shoulderHalf)
            val elbow = TwoLink.solve(s, wrist, a.upperArm, a.forearm, v(-0.5, -1.0))
            out.add(elbow)
            out.add(wrist)
        }
        return Posture(
            hip, shoulder,
            V3(ankle.x, ankle.y, a.hipHalf), V3(ankle.x, ankle.y, -a.hipHalf),
            out[0], out[1], out[2], out[3],
            footPitchL = Math.toRadians(60.0), footPitchR = Math.toRadians(60.0),
            kneeL = V3(knee.x, knee.y, a.hipHalf), kneeR = V3(knee.x, knee.y, -a.hipHalf),
        )
    }

    // ---------- jalón al pecho ----------

    private fun latPulldown(st: MotionState, a: Anthro): Posture {
        val h = a.height
        val hip = v(0.0, a.ankleHeight + a.shank)
        val lean = -Math.toRadians(12.0)
        val shoulder = hip + trunkUp(lean) * a.trunk
        val ankle = v(a.thigh, a.ankleHeight)
        val out = ArrayList<V3>(4)
        for ((zs, b) in listOf(Pair(1.0, st.bL), Pair(-1.0, st.bR))) {
            val s = shoulderOf(shoulder, a, zs)
            val wrist = s + polarBlend(v(a.armReach * sin(0.2), a.armReach * cos(0.2)), v(0.10 * h, -0.011 * h), b)
            val elbow = TwoLink.solve(s, wrist, a.upperArm, a.forearm, v(-0.8, -0.6))
            out.add(elbow)
            out.add(wrist)
        }
        return Posture(
            hip, shoulder,
            V3(ankle.x, ankle.y, a.hipHalf), V3(ankle.x, ankle.y, -a.hipHalf),
            out[0], out[1], out[2], out[3],
            kneePole = v(0.2, 1.0),
        )
    }

    // ---------- remo con barra (inclinado) ----------

    private fun row(st: MotionState, a: Anthro): Posture {
        val h = a.height
        val k0 = a.standKneeFlex
        val kappa = Math.toRadians(30.0)
        val delta = k0 + 0.2 * (kappa - k0)
        val ankleX = standAnkleX(a)
        val knee = v(ankleX + a.shank * sin(delta), a.ankleHeight + a.shank * cos(delta))
        val thighAngle = delta - kappa
        val hip = knee + v(a.thigh * sin(thighAngle), a.thigh * cos(thighAngle))
        val lean = Math.toRadians(58.0)
        val shoulder = hip + trunkUp(lean) * a.trunk
        val up = trunkUp(lean)
        val fwd = trunkForward(lean)
        val out = ArrayList<V3>(4)
        for ((zs, b) in listOf(Pair(1.0, st.bL), Pair(-1.0, st.bR))) {
            val s = shoulderOf(shoulder, a, zs)
            val hang = s + v(0.0, -a.armReach)
            val pulled = s - up * (0.143 * h) + fwd * (0.086 * h)
            val wrist = lerp(hang, pulled, b)
            val elbow = TwoLink.solve(s, wrist, a.upperArm, a.forearm, v(-1.0, 0.6))
            out.add(elbow)
            out.add(wrist)
        }
        return Posture(
            hip, shoulder,
            V3(ankleX, a.ankleHeight, a.hipHalf), V3(ankleX, a.ankleHeight, -a.hipHalf),
            out[0], out[1], out[2], out[3],
        )
    }

    // ---------- curl de bíceps ----------

    private fun curl(st: MotionState, a: Anthro): Posture {
        val arms = { shoulder: V3, _: Double ->
            val l = armFk(shoulderOf(shoulder, a, 1.0), a, 1.0, 0.0, 0.0, lerp(Math.toRadians(5.0), Math.toRadians(145.0), st.bL))
            val r = armFk(shoulderOf(shoulder, a, -1.0), a, -1.0, 0.0, 0.0, lerp(Math.toRadians(5.0), Math.toRadians(145.0), st.bR))
            listOf(l.first, l.second, r.first, r.second)
        }
        return standing(st, a, arms)
    }

    // ---------- extensión de tríceps en polea ----------

    private fun tricepsPushdown(st: MotionState, a: Anthro): Posture {
        val legs = standingLegs(a, st.gaitAmp, st.gaitPhase)
        val lean = Math.toRadians(8.0)
        val shoulder = legs.hip + trunkUp(lean) * a.trunk
        // Codos pegados al cuerpo (brazo vertical); el antebrazo pasa de horizontal-arriba a colgando.
        val l = armFk(shoulderOf(shoulder, a, 1.0), a, 1.0, 0.0, 0.0, lerp(Math.toRadians(100.0), Math.toRadians(5.0), st.bL))
        val r = armFk(shoulderOf(shoulder, a, -1.0), a, -1.0, 0.0, 0.0, lerp(Math.toRadians(100.0), Math.toRadians(5.0), st.bR))
        return Posture(legs.hip, shoulder, legs.ankleL, legs.ankleR, l.first, l.second, r.first, r.second)
    }

    // ---------- elevaciones laterales ----------

    private fun lateralRaise(st: MotionState, a: Anthro): Posture {
        val arms = { shoulder: V3, _: Double ->
            val bl = lerp(Math.toRadians(6.0), Math.toRadians(92.0), st.bL)
            val br = lerp(Math.toRadians(6.0), Math.toRadians(92.0), st.bR)
            val l = armFk(shoulderOf(shoulder, a, 1.0), a, 1.0, 0.0, bl, Math.toRadians(15.0))
            val r = armFk(shoulderOf(shoulder, a, -1.0), a, -1.0, 0.0, br, Math.toRadians(15.0))
            listOf(l.first, l.second, r.first, r.second)
        }
        return standing(st, a, arms)
    }

    // ---------- extensión de cuádriceps en máquina ----------

    private fun legExtension(st: MotionState, a: Anthro): Posture {
        val b = max(st.bL, st.bR)
        val hip = v(0.0, a.ankleHeight + a.shank)
        val lean = -Math.toRadians(10.0)
        val shoulder = hip + trunkUp(lean) * a.trunk
        val knee = hip + v(a.thigh, 0.0)
        val beta = lerp(Math.toRadians(3.0), Math.toRadians(85.0), b) // la espinilla gira hacia delante
        val ankle = knee + v(a.shank * sin(beta), -a.shank * cos(beta))
        val out = ArrayList<V3>(4)
        for (zs in doubleArrayOf(1.0, -1.0)) {
            val s = shoulderOf(shoulder, a, zs)
            val wrist = V3(hip.x + 0.03 * a.height, hip.y + 0.125 * a.height, zs * a.shoulderHalf)
            val elbow = TwoLink.solve(s, wrist, a.upperArm, a.forearm, v(-0.3, -1.0))
            out.add(elbow)
            out.add(wrist)
        }
        return Posture(
            hip, shoulder,
            V3(ankle.x, ankle.y, a.hipHalf), V3(ankle.x, ankle.y, -a.hipHalf),
            out[0], out[1], out[2], out[3],
            footPitchL = -beta, footPitchR = -beta,
            kneeL = V3(knee.x, knee.y, a.hipHalf), kneeR = V3(knee.x, knee.y, -a.hipHalf),
        )
    }

    // ---------- elevación de gemelos ----------

    private fun calfRaise(st: MotionState, a: Anthro): Posture {
        val arms = { shoulder: V3, _: Double -> hangingArms(shoulder, a) }
        if (st.gaitAmp > 1e-9) return standing(st, a, arms)
        val b = max(st.bL, st.bR)
        val pitch = Math.toRadians(55.0) * b
        // La punta queda fija en el suelo; el pie gira sobre ella y el tobillo sube.
        val toeFixed = v(standAnkleX(a) + a.toeAhead, 0.010 * a.height)
        val toeRot = SynthBody.rotateClockwise(V3(a.toeAhead, -a.footDrop), pitch)
        val ankle = toeFixed - v(toeRot.x, toeRot.y)
        val k0 = a.standKneeFlex
        val hip = ankle + v(a.shank * sin(k0), a.shank * cos(k0) + a.thigh)
        val shoulder = hip + v(0.0, a.trunk)
        val e = arms(shoulder, 0.0)
        return Posture(
            hip, shoulder,
            V3(ankle.x, ankle.y, a.hipHalf), V3(ankle.x, ankle.y, -a.hipHalf),
            e[0], e[1], e[2], e[3],
            footPitchL = pitch, footPitchR = pitch,
        )
    }

}

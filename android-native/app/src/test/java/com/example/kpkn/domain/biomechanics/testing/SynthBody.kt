package com.example.kpkn.domain.biomechanics.testing

import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Vector 3D en metros. Ejes del atleta: X hacia delante, Y arriba, Z hacia su izquierda. */
internal class V3(val x: Double, val y: Double, val z: Double = 0.0) {
    operator fun plus(o: V3) = V3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: V3) = V3(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Double) = V3(x * k, y * k, z * k)
    fun dot(o: V3) = x * o.x + y * o.y + z * o.z
    fun norm() = sqrt(x * x + y * y + z * z)
    fun unit(): V3 {
        val n = norm()
        return if (n < 1e-12) V3(0.0, 0.0, 0.0) else V3(x / n, y / n, z / n)
    }
}

/** Antropometría de Drillis–Contini: todas las longitudes salen de la altura. */
internal class Anthro(val height: Double) {
    val thigh = 0.245 * height
    val shank = 0.246 * height
    val ankleHeight = 0.039 * height
    val footLength = 0.152 * height
    val heelBack = 0.045 * height          // el talón queda tras el tobillo
    val toeAhead = 0.107 * height          // la punta queda delante del tobillo
    val footDrop = 0.029 * height          // el talón y la punta quedan por debajo del tobillo
    val trunk = 0.288 * height             // cadera → hombro
    val upperArm = 0.186 * height
    val forearm = 0.146 * height
    val hand = 0.108 * height
    val shoulderHalf = 0.259 * height / 2.0
    val hipHalf = 0.191 * height / 2.0
    val headCenter = 0.115 * height        // hombro → centro de la cabeza
    val noseUp = 0.015 * height            // centro de la cabeza → nariz, hacia arriba
    val noseForward = 0.05 * height
    val earHalf = 0.07 * height
    val mouthHalf = 0.025 * height
    val mouthDown = 0.03 * height

    /** Alcance de la pierna casi estirada (3,6° de flexión de rodilla): las piernas «rectas» nunca llegan al tope. */
    val legReach = REACH * (thigh + shank)

    /** Alcance del brazo casi estirado. */
    val armReach = REACH * (upperArm + forearm)

    /** Flexión de rodilla de la postura de pie (rad), consistente con [legReach]. */
    val standKneeFlex: Double = Math.toRadians(3.6)

    companion object {
        const val REACH = 0.9995
    }
}

/** Resuelve el punto medio de una cadena de dos segmentos (rodilla o codo) por geometría, con un vector «polo». */
internal object TwoLink {
    /** Se pone a true si algún objetivo quedó fuera del alcance (el punto se recorta y la cadena deja de medir lo que debe). */
    var clamped = false

    fun solve(p0: V3, p2: V3, l1: Double, l2: Double, pole: V3): V3 {
        val delta = p2 - p0
        var d = delta.norm()
        val lo = abs(l1 - l2) + 1e-6
        val hi = l1 + l2 - 1e-6
        if (d < lo || d > hi) {
            clamped = true
            d = min(max(d, lo), hi)
        }
        val u = delta.unit()
        val a = (l1 * l1 - l2 * l2 + d * d) / (2.0 * d)
        val h = sqrt(max(0.0, l1 * l1 - a * a))
        var n = pole - u * pole.dot(u)
        if (n.norm() < 1e-9) {
            // El polo es paralelo a la cadena: se prueba con otro eje que no lo sea.
            val alt = if (abs(u.y) < 0.9) V3(0.0, 1.0, 0.0) else V3(1.0, 0.0, 0.0)
            n = alt - u * alt.dot(u)
        }
        return p0 + u * a + n.unit() * h
    }
}

/**
 * Postura completa en el plano sagital + las posiciones laterales: lo que cada movimiento calcula y [SynthBody.joints]
 * convierte en los 33 puntos. [kneeL]/[kneeR] a null = rodilla por geometría con el polo [kneePole].
 */
internal class Posture(
    val hip: V3,
    val shoulder: V3,
    val ankleL: V3,
    val ankleR: V3,
    val elbowL: V3,
    val wristL: V3,
    val elbowR: V3,
    val wristR: V3,
    val footPitchL: Double = 0.0,
    val footPitchR: Double = 0.0,
    val kneeL: V3? = null,
    val kneeR: V3? = null,
    val kneePole: V3 = V3(1.0, 0.0, 0.0),
)

/** Los 33 puntos del modelo en 3D (metros, ejes del atleta); los que no se generan quedan en null. */
internal class Joints(val p: Array<V3?>) {
    operator fun get(lm: PoseLandmark): V3 = p[lm.mp] ?: error("falta ${lm.name}")
    fun has(lm: PoseLandmark) = p[lm.mp] != null
    fun translated(dx: Double): Joints = Joints(Array(33) { i -> p[i]?.let { V3(it.x + dx, it.y, it.z) } })
}

internal object SynthBody {
    /** Ángulo (rad) del tronco desde la vertical (+ = hacia delante) a partir de cadera y hombro. */
    fun trunkAngle(hip: V3, shoulder: V3): Double = Math.atan2(shoulder.x - hip.x, shoulder.y - hip.y)

    /** Peso con el que la cabeza sigue al tronco: 0,4 de pie y 1 tumbado, con transición suave entre 60° y 85°. */
    private fun headFollow(trunkAbs: Double): Double {
        val t = ((trunkAbs - Math.toRadians(60.0)) / Math.toRadians(25.0)).coerceIn(0.0, 1.0)
        val smooth = t * t * (3.0 - 2.0 * t)
        return 0.4 + 0.6 * smooth
    }

    /** Convierte una postura en los 33 puntos (en el sistema local del atleta, sin el desplazamiento de la raíz). */
    fun joints(post: Posture, a: Anthro): Joints {
        val p = arrayOfNulls<V3>(33)
        fun put(lm: PoseLandmark, v: V3) {
            p[lm.mp] = v
        }

        val hipL = post.hip + V3(0.0, 0.0, a.hipHalf)
        val hipR = post.hip + V3(0.0, 0.0, -a.hipHalf)
        val shL = post.shoulder + V3(0.0, 0.0, a.shoulderHalf)
        val shR = post.shoulder + V3(0.0, 0.0, -a.shoulderHalf)
        put(PoseLandmark.LEFT_HIP, hipL)
        put(PoseLandmark.RIGHT_HIP, hipR)
        put(PoseLandmark.LEFT_SHOULDER, shL)
        put(PoseLandmark.RIGHT_SHOULDER, shR)

        // Piernas: rodilla por geometría (salvo que la postura la traiga) y pie rígido con su inclinación.
        val kneeL = post.kneeL ?: TwoLink.solve(hipL, post.ankleL, a.thigh, a.shank, post.kneePole)
        val kneeR = post.kneeR ?: TwoLink.solve(hipR, post.ankleR, a.thigh, a.shank, post.kneePole)
        put(PoseLandmark.LEFT_KNEE, kneeL)
        put(PoseLandmark.RIGHT_KNEE, kneeR)
        put(PoseLandmark.LEFT_ANKLE, post.ankleL)
        put(PoseLandmark.RIGHT_ANKLE, post.ankleR)
        foot(post.ankleL, post.footPitchL, a, PoseLandmark.LEFT_HEEL, PoseLandmark.LEFT_FOOT_INDEX, ::put)
        foot(post.ankleR, post.footPitchR, a, PoseLandmark.RIGHT_HEEL, PoseLandmark.RIGHT_FOOT_INDEX, ::put)

        // Brazos y manos.
        put(PoseLandmark.LEFT_ELBOW, post.elbowL)
        put(PoseLandmark.RIGHT_ELBOW, post.elbowR)
        put(PoseLandmark.LEFT_WRIST, post.wristL)
        put(PoseLandmark.RIGHT_WRIST, post.wristR)
        hand(post.elbowL, post.wristL, a, PoseLandmark.LEFT_PINKY, PoseLandmark.LEFT_INDEX, PoseLandmark.LEFT_THUMB, ::put)
        hand(post.elbowR, post.wristR, a, PoseLandmark.RIGHT_PINKY, PoseLandmark.RIGHT_INDEX, PoseLandmark.RIGHT_THUMB, ::put)

        // Cabeza: sigue al tronco (de pie, en parte; tumbado, del todo). u = arriba de la cabeza, f = hacia donde mira.
        val trunk = trunkAngle(post.hip, post.shoulder)
        val headAngle = trunk * headFollow(abs(trunk))
        val up = V3(sin(headAngle), cos(headAngle), 0.0)
        val fwd = V3(cos(headAngle), -sin(headAngle), 0.0)
        val center = post.shoulder + up * a.headCenter
        val nose = center + up * a.noseUp + fwd * a.noseForward
        put(PoseLandmark.NOSE, nose)
        put(PoseLandmark.LEFT_EAR, center + V3(0.0, 0.0, a.earHalf))
        put(PoseLandmark.RIGHT_EAR, center + V3(0.0, 0.0, -a.earHalf))
        put(PoseLandmark.MOUTH_LEFT, nose - up * a.mouthDown + V3(0.0, 0.0, a.mouthHalf))
        put(PoseLandmark.MOUTH_RIGHT, nose - up * a.mouthDown + V3(0.0, 0.0, -a.mouthHalf))
        return Joints(p)
    }

    /** Dirección «hacia donde mira» de la cara (unitaria, plano sagital) según el tronco. */
    fun faceDirection(post: Posture): V3 {
        val trunk = trunkAngle(post.hip, post.shoulder)
        val headAngle = trunk * headFollow(abs(trunk))
        return V3(cos(headAngle), -sin(headAngle), 0.0)
    }

    /** Talón y punta de un pie con el tobillo en [ankle]; [pitch] > 0 = punta hacia abajo (rad). */
    private fun foot(
        ankle: V3,
        pitch: Double,
        a: Anthro,
        heel: PoseLandmark,
        toe: PoseLandmark,
        put: (PoseLandmark, V3) -> Unit,
    ) {
        put(heel, ankle + rotateClockwise(V3(-a.heelBack, -a.footDrop), pitch))
        put(toe, ankle + rotateClockwise(V3(a.toeAhead, -a.footDrop), pitch))
    }

    /** Gira [v] (en el plano X–Y, Y arriba) [angle] radianes en sentido horario. */
    fun rotateClockwise(v: V3, angle: Double): V3 {
        val c = cos(angle)
        val s = sin(angle)
        return V3(v.x * c + v.y * s, -v.x * s + v.y * c, v.z)
    }

    /** Mano: los tres puntos quedan en la punta del antebrazo con desvíos pequeños. */
    private fun hand(
        elbow: V3,
        wrist: V3,
        a: Anthro,
        pinky: PoseLandmark,
        index: PoseLandmark,
        thumb: PoseLandmark,
        put: (PoseLandmark, V3) -> Unit,
    ) {
        val d = (wrist - elbow).unit()
        // Perpendicular «palma» en el plano sagital; si el antebrazo es perpendicular a ese plano, hacia arriba.
        var e = V3(-d.y, d.x, 0.0)
        e = if (e.norm() < 0.2) V3(0.0, 1.0, 0.0) else e.unit()
        val tip = wrist + d * a.hand
        put(pinky, tip - e * (0.010 * a.height) - d * (0.008 * a.height))
        put(index, tip + e * (0.010 * a.height))
        put(thumb, wrist + d * (0.85 * a.hand) + e * (0.020 * a.height))
    }
}

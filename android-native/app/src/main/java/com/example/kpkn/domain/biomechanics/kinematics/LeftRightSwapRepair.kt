package com.example.kpkn.domain.biomechanics.kinematics

import com.example.kpkn.domain.biomechanics.pose.PoseFrameV2
import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import com.example.kpkn.domain.biomechanics.pose.PoseTrackV2
import kotlin.math.sqrt

/**
 * Repara los cruces izquierda/derecha de MediaPipe (§4, punto 5): a veces el modelo etiqueta la pierna derecha como
 * izquierda (y al revés) durante unos fotogramas. Se arregla ANTES de elegir el lado cercano.
 *
 * Viterbi de 2 estados por par de extremidades: piernas (rodilla, tobillo, talón, punta) y brazos (codo, muñeca).
 * El estado 0 deja las etiquetas como vienen y el 1 («cruzado») intercambia los puntos izquierdo y derecho del grupo.
 * El coste de un camino es la suma de los desplazamientos entre fotogramas con el etiquetado resultante; cada cambio
 * de estado cuesta 0,1·S y cada fotograma cruzado una pequeña fracción más ([CROSSED_FRAME_COST]). Empezar cruzado
 * también cuesta un cambio: si toda la traza viniera cruzada no se puede saber, y se deja como está.
 */
object LeftRightSwapRepair {
    /** Penalización por cambio de estado, en fracciones de la escala S (altura corporal en FH). */
    const val SWITCH_PENALTY = 0.1

    /**
     * Coste por fotograma en estado cruzado, en fracciones de S. Rompe el empate entre «estos pocos fotogramas están
     * cruzados» y «todos los demás lo están»: sin él, un cruce al principio de la traza hace más barato girar toda
     * la serie que arreglar sus pocos fotogramas (menos cambios de estado). Con él gana el etiquetado que conserva
     * más etiquetas originales; una traza entera cruzada de verdad es indistinguible y se deja como está.
     */
    const val CROSSED_FRAME_COST = 0.0005

    /**
     * Devuelve una traza nueva con los cruces corregidos; los fotogramas sin cambios se comparten con la original.
     * [scaleFh] es S, la altura corporal en unidades FH (si no es positiva y finita no se corrige nada).
     *
     * Un punto con visibilidad < 0,3 no entra en el coste (su posición es ruido), y un par de puntos solo cuenta en
     * una transición si sus cuatro extremos (izquierdo y derecho, antes y después) son utilizables: así los cuatro
     * etiquetados posibles se comparan con los mismos términos.
     */
    fun repair(track: PoseTrackV2, scaleFh: Double): PoseTrackV2 {
        val frames = track.frames
        val n = frames.size
        if (n < 2 || !(scaleFh > 0.0) || !scaleFh.isFinite()) return track.copy(frames = frames.toList())
        val aspect = if (track.frameWidth > 0 && track.frameHeight > 0) track.frameWidth.toDouble() / track.frameHeight else 1.0
        val penalty = SWITCH_PENALTY * scaleFh
        val crossedFrame = CROSSED_FRAME_COST * scaleFh

        val legState = solve(frames, LEG_COST, aspect, penalty, crossedFrame)
        val armState = solve(frames, ARM_COST, aspect, penalty, crossedFrame)

        val repaired = ArrayList<PoseFrameV2>(n)
        for (k in 0 until n) {
            val legs = legState[k] == 1.toByte()
            val arms = armState[k] == 1.toByte()
            repaired.add(if (legs || arms) swapped(frames[k], legs, arms) else frames[k])
        }
        return track.copy(frames = repaired)
    }

    /** Estado (0 normal, 1 cruzado) de cada fotograma para un grupo, por Viterbi sobre los fotogramas con datos. */
    private fun solve(
        frames: List<PoseFrameV2>,
        cost: Array<PoseLandmark>,
        aspect: Double,
        penalty: Double,
        crossedFrame: Double,
    ): ByteArray {
        val n = frames.size
        val m = cost.size / 2 // pares (izquierdo, derecho)
        // Posiciones FH de cada punto utilizable: [fotograma][par][lado]; NaN si falta o no se ve.
        val xs = DoubleArray(n * m * 2)
        val ys = DoubleArray(n * m * 2)
        val hasData = BooleanArray(n)
        for (k in 0 until n) {
            val f = frames[k]
            for (j in 0 until m) {
                for (s in 0..1) {
                    val lm = cost[2 * j + s]
                    val o = (k * m + j) * 2 + s
                    val vis = f.vis(lm)
                    if (vis >= FrameGeometry.WEAK_VISIBILITY) {
                        xs[o] = f.x(lm) * aspect
                        ys[o] = f.y(lm).toDouble()
                        hasData[k] = true
                    } else {
                        xs[o] = Double.NaN
                        ys[o] = Double.NaN
                    }
                }
            }
        }

        val states = ByteArray(n)
        // Viterbi sobre la secuencia compacta de fotogramas con datos.
        var count = 0
        for (k in 0 until n) if (hasData[k]) count++
        if (count < 2) return states
        val idx = IntArray(count)
        var c = 0
        for (k in 0 until n) if (hasData[k]) idx[c++] = k
        val back = ByteArray(count * 2) // back[t*2+s] = estado previo óptimo
        var cost0 = 0.0
        var cost1 = penalty + crossedFrame // empezar cruzado cuesta un cambio
        for (t in 1 until count) {
            val a = idx[t - 1]
            val b = idx[t]
            var n0 = 0.0 // coste de la transición según (previo, actual)
            var n1 = 0.0
            var n2 = 0.0
            var n3 = 0.0
            for (j in 0 until m) {
                val oa = (a * m + j) * 2
                val ob = (b * m + j) * 2
                val laX = xs[oa]
                val raX = xs[oa + 1]
                val lbX = xs[ob]
                val rbX = xs[ob + 1]
                if (laX.isNaN() || raX.isNaN() || lbX.isNaN() || rbX.isNaN()) continue
                val laY = ys[oa]
                val raY = ys[oa + 1]
                val lbY = ys[ob]
                val rbY = ys[ob + 1]
                val straight = dist(lbX, lbY, laX, laY) + dist(rbX, rbY, raX, raY) // (0,0) y (1,1)
                val crossed = dist(rbX, rbY, laX, laY) + dist(lbX, lbY, raX, raY) // (0,1) y (1,0)
                n0 += straight
                n3 += straight
                n1 += crossed
                n2 += crossed
            }
            // Desde el estado previo p al actual s: cost[p] + (p == s ? 0 : penalty) + transición.
            val toNormalFromNormal = cost0 + n0
            val toNormalFromCrossed = cost1 + penalty + n2
            val toCrossedFromNormal = cost0 + penalty + n1
            val toCrossedFromCrossed = cost1 + n3
            if (toNormalFromNormal <= toNormalFromCrossed) {
                back[t * 2] = 0
                cost0 = toNormalFromNormal
            } else {
                back[t * 2] = 1
                cost0 = toNormalFromCrossed
            }
            if (toCrossedFromCrossed < toCrossedFromNormal) {
                back[t * 2 + 1] = 1
                cost1 = toCrossedFromCrossed + crossedFrame
            } else {
                back[t * 2 + 1] = 0
                cost1 = toCrossedFromNormal + crossedFrame
            }
        }
        var s = if (cost1 < cost0) 1 else 0
        for (t in count - 1 downTo 0) {
            val from = idx[t]
            val to = if (t + 1 < count) idx[t + 1] else n
            for (k in from until to) states[k] = s.toByte()
            if (t > 0) s = back[t * 2 + s].toInt()
        }
        return states
    }

    private fun dist(ax: Double, ay: Double, bx: Double, by: Double): Double {
        val dx = ax - bx
        val dy = ay - by
        return sqrt(dx * dx + dy * dy)
    }

    /** Copia del fotograma con los puntos izquierdo y derecho intercambiados en los grupos pedidos. */
    private fun swapped(f: PoseFrameV2, legs: Boolean, arms: Boolean): PoseFrameV2 {
        val img = f.imageData()?.copyOf()
        val wld = f.worldData()?.copyOf()
        if (legs) swapPairs(img, wld, LEG_SWAP)
        if (arms) swapPairs(img, wld, ARM_SWAP)
        return PoseFrameV2.fromArrays(f.tUs, f.posesDetected, img, wld)
    }

    private fun swapPairs(img: FloatArray?, wld: FloatArray?, pairs: Array<PoseLandmark>) {
        for (p in 0 until pairs.size / 2) {
            val l = pairs[2 * p]
            val r = pairs[2 * p + 1]
            if (img != null) swapSlots(img, PoseLandmark.imageSlot(l), PoseLandmark.imageSlot(r), PoseFrameV2.IMAGE_STRIDE)
            if (wld != null) swapSlots(wld, PoseLandmark.worldSlot(l), PoseLandmark.worldSlot(r), PoseFrameV2.WORLD_STRIDE)
        }
    }

    private fun swapSlots(a: FloatArray, slotL: Int, slotR: Int, stride: Int) {
        if (slotL < 0 || slotR < 0) return
        val offL = slotL * stride
        val offR = slotR * stride
        for (q in 0 until stride) {
            val tmp = a[offL + q]
            a[offL + q] = a[offR + q]
            a[offR + q] = tmp
        }
    }

    // Puntos que entran en el coste, como pares (izquierdo, derecho).
    private val LEG_COST: Array<PoseLandmark> = arrayOf(
        PoseLandmark.LEFT_KNEE, PoseLandmark.RIGHT_KNEE,
        PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE,
        PoseLandmark.LEFT_HEEL, PoseLandmark.RIGHT_HEEL,
        PoseLandmark.LEFT_FOOT_INDEX, PoseLandmark.RIGHT_FOOT_INDEX,
    )
    private val ARM_COST: Array<PoseLandmark> = arrayOf(
        PoseLandmark.LEFT_ELBOW, PoseLandmark.RIGHT_ELBOW,
        PoseLandmark.LEFT_WRIST, PoseLandmark.RIGHT_WRIST,
    )

    // Puntos que se intercambian en el estado cruzado: los del coste y, en los brazos, los de la mano (van con la muñeca).
    private val LEG_SWAP: Array<PoseLandmark> = LEG_COST
    private val ARM_SWAP: Array<PoseLandmark> = ARM_COST + arrayOf(
        PoseLandmark.LEFT_PINKY, PoseLandmark.RIGHT_PINKY,
        PoseLandmark.LEFT_INDEX, PoseLandmark.RIGHT_INDEX,
        PoseLandmark.LEFT_THUMB, PoseLandmark.RIGHT_THUMB,
    )
}

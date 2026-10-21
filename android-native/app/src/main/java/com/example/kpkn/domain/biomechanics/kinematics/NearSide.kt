package com.example.kpkn.domain.biomechanics.kinematics

import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import com.example.kpkn.domain.biomechanics.pose.PoseTrackV2
import com.example.kpkn.domain.biomechanics.signal.RobustStats
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Lado cercano a la cámara. [margin] es la diferencia que decidió: de visibilidad (0..1) con
 * [method] = "visibility", o de profundidad z (misma unidad que la z de imagen) con "depth".
 */
data class SideEstimate(val near: Side, val margin: Double, val method: String)

/** Elige el lado del cuerpo más cercano a la cámara (§4, punto 11). Queda fijo para toda la serie. */
object NearSideSelector {
    /** Diferencia mínima de visibilidad mediana para decidir por visibilidad. */
    const val VISIBILITY_MARGIN = 0.1

    const val METHOD_VISIBILITY = "visibility"
    const val METHOD_DEPTH = "depth"

    // Pequeña tolerancia para que 0,95 − 0,85 (que en coma flotante da 0,0999…) cuente como 0,1.
    private const val EPS = 1e-6

    /**
     * Puntuación de cada lado = mediana de la visibilidad de {hombro, codo, muñeca, cadera, rodilla, tobillo, talón,
     * punta} en los fotogramas de [frames] (todos los valores juntos). Gana el lado con ≥ 0,1 más; si no, el de menor
     * z mediana (más cerca de la cámara). `null` si ninguno de los dos lados tiene datos.
     */
    fun select(track: PoseTrackV2, frames: IntRange): SideEstimate? {
        val first = max(frames.first, 0)
        val last = min(frames.last, track.frames.size - 1)
        if (last < first) return null
        val capacity = (last - first + 1) * POINTS_PER_SIDE
        val visL = DoubleArray(capacity)
        val visR = DoubleArray(capacity)
        val zL = DoubleArray(capacity)
        val zR = DoubleArray(capacity)
        var nL = 0
        var nR = 0
        for (i in first..last) {
            val f = track.frames[i]
            if (!f.hasPose) continue
            for (lm in LEFT_POINTS) {
                val v = f.vis(lm)
                if (v.isNaN()) continue
                visL[nL] = v.toDouble()
                zL[nL] = f.z(lm).toDouble()
                nL++
            }
            for (lm in RIGHT_POINTS) {
                val v = f.vis(lm)
                if (v.isNaN()) continue
                visR[nR] = v.toDouble()
                zR[nR] = f.z(lm).toDouble()
                nR++
            }
        }
        if (nL == 0 && nR == 0) return null
        if (nL == 0) return SideEstimate(Side.RIGHT, RobustStats.median(visR.copyOf(nR)), METHOD_VISIBILITY)
        if (nR == 0) return SideEstimate(Side.LEFT, RobustStats.median(visL.copyOf(nL)), METHOD_VISIBILITY)
        val scoreL = RobustStats.median(visL.copyOf(nL))
        val scoreR = RobustStats.median(visR.copyOf(nR))
        val diff = scoreL - scoreR
        if (abs(diff) >= VISIBILITY_MARGIN - EPS) {
            return SideEstimate(if (diff > 0.0) Side.LEFT else Side.RIGHT, abs(diff), METHOD_VISIBILITY)
        }
        val depthL = RobustStats.median(zL.copyOf(nL))
        val depthR = RobustStats.median(zR.copyOf(nR))
        if (depthL.isNaN() || depthR.isNaN() || depthL == depthR) {
            // Sin profundidad que desempate: el de más visibilidad, aunque la diferencia sea pequeña.
            return SideEstimate(if (diff >= 0.0) Side.LEFT else Side.RIGHT, abs(diff), METHOD_VISIBILITY)
        }
        return SideEstimate(if (depthL < depthR) Side.LEFT else Side.RIGHT, abs(depthL - depthR), METHOD_DEPTH)
    }

    private const val POINTS_PER_SIDE = 8

    private val LEFT_POINTS = listOf(
        PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST, PoseLandmark.LEFT_HIP,
        PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE, PoseLandmark.LEFT_HEEL, PoseLandmark.LEFT_FOOT_INDEX,
    ).toTypedArray()

    private val RIGHT_POINTS = listOf(
        PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST, PoseLandmark.RIGHT_HIP,
        PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE, PoseLandmark.RIGHT_HEEL, PoseLandmark.RIGHT_FOOT_INDEX,
    ).toTypedArray()
}

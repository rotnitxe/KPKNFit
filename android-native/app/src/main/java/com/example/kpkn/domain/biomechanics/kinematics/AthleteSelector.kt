package com.example.kpkn.domain.biomechanics.kinematics

import com.example.kpkn.domain.biomechanics.pose.LandmarkSample
import com.example.kpkn.domain.biomechanics.pose.PoseLandmark

/**
 * Elige al atleta entre las poses que ve el modelo en un fotograma (§4, punto 2). Con `numPoses = 2` el estimador
 * devuelve hasta dos; se sigue a la que mantiene la continuidad (la de centro de torso más cercano al anterior) y,
 * mientras no hay un centro anterior (el primer segundo), a la de caja más grande. Esta elección la hace el pipeline
 * (VT4c) al construir la traza; aquí es una función pura.
 */
object AthleteSelector {
    private val TORSO = arrayOf(
        PoseLandmark.LEFT_SHOULDER,
        PoseLandmark.RIGHT_SHOULDER,
        PoseLandmark.LEFT_HIP,
        PoseLandmark.RIGHT_HIP,
    )

    /**
     * Índice en [candidates] de la pose del atleta, o −1 si no hay ninguna.
     * Con [previousTorsoCenter] (en unidades FH, el de la pose elegida en el fotograma anterior) gana la de centro de
     * torso más cercano; si es `null`, o ninguna tiene torso medible, la de caja (de los puntos visibles) más grande.
     * En un empate gana el índice menor.
     */
    fun select(
        candidates: List<Map<PoseLandmark, LandmarkSample>>,
        frameWidth: Int,
        frameHeight: Int,
        previousTorsoCenter: Vec2?,
    ): Int {
        if (candidates.isEmpty()) return -1
        if (candidates.size == 1) return 0
        if (previousTorsoCenter != null) {
            var best = -1
            var bestDistance = Double.POSITIVE_INFINITY
            for (i in candidates.indices) {
                val center = torsoCenter(candidates[i], frameWidth, frameHeight) ?: continue
                val d = (center - previousTorsoCenter).norm()
                if (d < bestDistance) {
                    bestDistance = d
                    best = i
                }
            }
            if (best >= 0) return best
        }
        var best = 0
        var bestArea = -1.0
        for (i in candidates.indices) {
            val area = boxArea(candidates[i], aspectOf(frameWidth, frameHeight))
            if (area > bestArea) {
                bestArea = area
                best = i
            }
        }
        return best
    }

    /**
     * Centro del torso en unidades FH (X = x·W/H, Y = y): la media de los hombros y las caderas visibles
     * (visibilidad ≥ 0,3; si hay menos de dos, de los que haya). `null` si no hay ninguno.
     */
    fun torsoCenter(pose: Map<PoseLandmark, LandmarkSample>, frameWidth: Int, frameHeight: Int): Vec2? {
        val aspect = aspectOf(frameWidth, frameHeight)
        var sx = 0.0
        var sy = 0.0
        var n = 0
        var ax = 0.0
        var ay = 0.0
        var all = 0
        for (lm in TORSO) {
            val p = pose[lm] ?: continue
            if (p.x.isNaN() || p.y.isNaN()) continue
            ax += p.x * aspect
            ay += p.y
            all++
            if (p.visibility >= FrameGeometry.WEAK_VISIBILITY) {
                sx += p.x * aspect
                sy += p.y
                n++
            }
        }
        if (n >= 2) return Vec2(sx / n, sy / n)
        if (all == 0) return null
        return Vec2(ax / all, ay / all)
    }

    private fun aspectOf(w: Int, h: Int): Double = if (w > 0 && h > 0) w.toDouble() / h else 1.0

    /** Área (FH²) de la caja de los puntos visibles (≥ 0,3); si ninguno lo es, de todos. 0 si no hay puntos. */
    private fun boxArea(pose: Map<PoseLandmark, LandmarkSample>, aspect: Double): Double {
        var area = boxArea(pose, aspect, onlyVisible = true)
        if (area < 0.0) area = boxArea(pose, aspect, onlyVisible = false)
        return if (area < 0.0) 0.0 else area
    }

    private fun boxArea(pose: Map<PoseLandmark, LandmarkSample>, aspect: Double, onlyVisible: Boolean): Double {
        var minX = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY
        var n = 0
        for (p in pose.values) {
            if (p.x.isNaN() || p.y.isNaN()) continue
            if (onlyVisible && p.visibility < FrameGeometry.WEAK_VISIBILITY) continue
            val x = p.x * aspect
            val y = p.y.toDouble()
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
            n++
        }
        return if (n == 0) -1.0 else (maxX - minX) * (maxY - minY)
    }
}

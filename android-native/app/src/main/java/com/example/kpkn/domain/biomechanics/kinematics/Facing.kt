package com.example.kpkn.domain.biomechanics.kinematics

import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import com.example.kpkn.domain.biomechanics.signal.RobustStats
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Orientación f del atleta: hacia dónde mira en la imagen (§4, punto 11). */
object FacingEstimator {
    /** El pie solo decide si su proyección horizontal mide al menos esta fracción de su longitud (no de frente). */
    private const val MIN_FOOT_FRACTION = 0.15

    /** Con menos muestras que estas la mediana no es de fiar. */
    private const val MIN_SAMPLES = 3

    /**
     * f = signo(mediana(X_punta − X_talón)) del lado [near], en unidades FH y con el roll de [geo] corregido:
     * +1 si el atleta mira hacia la derecha de la imagen y −1 si mira hacia la izquierda.
     *
     * Respaldos, en este orden: la nariz frente a la oreja (la nariz queda por delante) y, por último, +1.
     * Con [lying] = true (banca, suelo) el pie no sirve: f sale de hombro frente a cadera, de modo que la inclinación
     * del tronco τ de [Angles.trunkLean] sale positiva con la cabeza «hacia delante».
     */
    fun sign(geo: FrameGeometry, near: Side, frames: IntRange, lying: Boolean = false): Int {
        val first = max(frames.first, 0)
        val last = min(frames.last, geo.frameCount - 1)
        if (last < first) return 1
        val n = last - first + 1
        val dx = DoubleArray(n)
        val len = DoubleArray(n)

        if (lying) {
            val s = medianDx(geo, first, last, near.shoulder(), near.hip(), dx, len)
            if (s != 0) return s
            val h = headSign(geo, near, first, last, dx, len)
            return if (h != 0) h else 1
        }

        // Pie: de talón a punta. Solo si no mira de frente (la proyección horizontal es una parte apreciable del pie).
        var count = 0
        for (i in first..last) {
            val heel = geo.point(i, near.heel()) ?: continue
            val toe = geo.point(i, near.toe()) ?: continue
            dx[count] = toe.x - heel.x
            len[count] = (toe - heel).norm()
            count++
        }
        if (count >= MIN_SAMPLES) {
            val mdx = RobustStats.median(dx.copyOf(count))
            val mlen = RobustStats.median(len.copyOf(count))
            if (mdx != 0.0 && abs(mdx) >= MIN_FOOT_FRACTION * mlen) return if (mdx > 0.0) 1 else -1
        }
        val h = headSign(geo, near, first, last, dx, len)
        if (h != 0) return h
        // Último recurso: el pie, aunque sea poco concluyente.
        if (count >= 1) {
            val mdx = RobustStats.median(dx.copyOf(count))
            if (mdx > 0.0) return 1
            if (mdx < 0.0) return -1
        }
        return 1
    }

    /** Signo de la mediana de X_[a] − X_[b]; 0 si hay menos de [MIN_SAMPLES] muestras o la mediana es nula. */
    private fun medianDx(
        geo: FrameGeometry,
        first: Int,
        last: Int,
        a: PoseLandmark,
        b: PoseLandmark,
        dx: DoubleArray,
        len: DoubleArray,
    ): Int {
        var count = 0
        for (i in first..last) {
            val pa = geo.point(i, a) ?: continue
            val pb = geo.point(i, b) ?: continue
            dx[count] = pa.x - pb.x
            len[count] = (pa - pb).norm()
            count++
        }
        if (count < MIN_SAMPLES) return 0
        val m = RobustStats.median(dx.copyOf(count))
        return if (m > 0.0) 1 else if (m < 0.0) -1 else 0
    }

    /** La nariz queda por delante de la oreja: signo de X_nariz − X_oreja (la del lado cercano, o la otra). */
    private fun headSign(geo: FrameGeometry, near: Side, first: Int, last: Int, dx: DoubleArray, len: DoubleArray): Int {
        val s = medianDx(geo, first, last, PoseLandmark.NOSE, near.ear(), dx, len)
        if (s != 0) return s
        return medianDx(geo, first, last, PoseLandmark.NOSE, near.opposite.ear(), dx, len)
    }
}

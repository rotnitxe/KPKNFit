package com.example.kpkn.domain.biomechanics.kinematics

import com.example.kpkn.domain.biomechanics.pose.PoseFrameV2
import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import com.example.kpkn.domain.biomechanics.signal.RobustStats
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min

/**
 * Vista estimada. ψ es el ángulo entre la línea de los hombros y el plano de imagen: 0° = de frente, 90° = de lado.
 * [psi2dDeg] sale de la imagen, [psiWorldDeg] de los puntos «world» (si los hay) y [psiDeg] es la mediana de la
 * media de ambos por fotograma. [reliable] es `false` si los dos discrepan más de 25°.
 */
data class ViewEstimate(
    val kind: ViewKind,
    val psiDeg: Double?,
    val psi2dDeg: Double?,
    val psiWorldDeg: Double?,
    val reliable: Boolean,
)

/** Clasifica la vista del atleta (§4, punto 12). */
object ViewClassifier {
    /** ρ de un atleta de frente (hombros / tronco) según §4: ψ_2D = acos(ρ/0,80). */
    const val RHO_FRONT = 0.80

    /** SIDE desde este ψ. */
    const val SIDE_MIN_DEG = 65.0

    /** FRONT hasta este ψ. */
    const val FRONT_MAX_DEG = 25.0

    /** Discrepancia máxima entre ψ_2D y ψ_world para fiarse de la vista. */
    const val DISCREPANCY_DEG = 25.0

    /** BACK: visibilidad mediana de la nariz por debajo de esto… */
    const val NOSE_VISIBILITY_MAX = 0.3

    /** …y la de las orejas (la mejor de las dos) por debajo de esto. */
    const val EARS_VISIBILITY_MAX = 0.5

    /** LYING: |τ| por encima de esto en más de la mitad de los fotogramas. */
    const val LYING_TAU_DEG = 60.0
    const val LYING_FRACTION = 0.5

    /** Fotogramas con hombros y caderas necesarios para estimar ψ. */
    private const val MIN_PSI_FRAMES = 3

    /** Longitud mínima (FH) del tronco o de la línea de hombros para dar la medida por válida. */
    private const val MIN_LENGTH_FH = 1e-6

    /**
     * ψ_2D = acos(clamp(ρ/0,80, 0, 1)) con ρ = |S_L − S_R| / |S_mid − H_mid| (todo en FH); NaN si el tronco es degenerado.
     * Pública para que el evaluador de encuadre (una sola imagen) use exactamente la misma fórmula.
     */
    fun psi2dDeg(shoulderL: Vec2, shoulderR: Vec2, hipL: Vec2, hipR: Vec2): Double {
        val sMid = (shoulderL + shoulderR) * 0.5
        val hMid = (hipL + hipR) * 0.5
        val trunk = (sMid - hMid).norm()
        if (trunk < MIN_LENGTH_FH) return Double.NaN
        return psiFromRatio((shoulderL - shoulderR).norm() / trunk / RHO_FRONT)
    }

    /** acos(clamp(r, 0, 1)) en grados, con r = ρ/0,80. */
    private fun psiFromRatio(r: Double): Double = Math.toDegrees(acos(r.coerceIn(0.0, 1.0)))

    /** Clase de vista de un ψ (sin las reglas de BACK y LYING): FRONT ≤ 25°, SIDE ≥ 65°, OBLIQUE entre medias. */
    fun kindOfPsi(psiDeg: Double): ViewKind = when {
        psiDeg.isNaN() -> ViewKind.UNKNOWN
        psiDeg >= SIDE_MIN_DEG -> ViewKind.SIDE
        psiDeg <= FRONT_MAX_DEG -> ViewKind.FRONT
        else -> ViewKind.OBLIQUE
    }

    /**
     * Clasifica la vista en los fotogramas [frames]:
     * 1. LYING si |τ| > 60° en más del 50 % de los fotogramas con tronco;
     * 2. BACK si la nariz (mediana < 0,3) y las orejas (< 0,5) no se ven;
     * 3. si no, por ψ: SIDE ≥ 65°, FRONT ≤ 25°, OBLIQUE entre medias.
     *
     * ψ de cada fotograma = media de ψ_2D y ψ_w (§4.12), salvo cuando ψ_2D está saturado (ρ/0,80 ≥ 1), que manda ψ_w.
     *
     * Los hombros y las caderas se toman «débiles» (visibilidad ≥ 0,3): de lado, el hombro lejano casi nunca llega
     * a 0,5 y es justo el que hace falta para ver que los dos se superponen. Las trazas de aspecto desconocido (v1)
     * dan UNKNOWN: sin el aspecto, ρ no significa nada.
     */
    fun classify(geo: FrameGeometry, frames: IntRange): ViewEstimate {
        val first = max(frames.first, 0)
        val last = min(frames.last, geo.frameCount - 1)
        if (last < first || !geo.track.aspectKnown) return UNKNOWN_VIEW
        val n = last - first + 1
        val psi2d = DoubleArray(n)
        val psiW = DoubleArray(n)
        val psi = DoubleArray(n)
        var n2d = 0
        var nW = 0
        var nPsi = 0
        var saturated2d = 0
        var lyingFrames = 0
        var torsoFrames = 0
        for (i in first..last) {
            val f = geo.track.frames[i]
            if (!f.hasPose) continue

            val sMid = geo.mid(i, PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER, weak = true)
            val hMid = geo.mid(i, PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP, weak = true)

            // τ: con un solo hombro o una sola cadera visibles también vale. El signo da igual: |τ| no depende de
            // hacia dónde mira el atleta.
            val s = sMid ?: geo.point(i, PoseLandmark.LEFT_SHOULDER, weak = true)
                ?: geo.point(i, PoseLandmark.RIGHT_SHOULDER, weak = true)
            val h = hMid ?: geo.point(i, PoseLandmark.LEFT_HIP, weak = true)
                ?: geo.point(i, PoseLandmark.RIGHT_HIP, weak = true)
            if (s != null && h != null && (s - h).norm() >= MIN_LENGTH_FH) {
                torsoFrames++
                if (abs(Angles.trunkLean(s, h, 1)) > LYING_TAU_DEG) lyingFrames++
            }

            var a = Double.NaN
            var saturated = false
            if (sMid != null && hMid != null) {
                val shoulders = geo.distance(i, PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER, weak = true)
                val trunk = (sMid - hMid).norm()
                if (!shoulders.isNaN() && trunk >= MIN_LENGTH_FH) {
                    val r = shoulders / trunk / RHO_FRONT
                    saturated = r >= 1.0
                    a = psiFromRatio(r)
                }
            }
            val w = worldPsiDeg(f)
            if (!a.isNaN()) psi2d[n2d++] = a
            if (!w.isNaN()) psiW[nW++] = w
            if (saturated) saturated2d++
            val both = when {
                // ψ_2D saturado (ρ/0,80 ≥ 1: hombros tan anchos como de frente o más) solo dice «casi de frente»: no arrastra
                // hacia 0 la media y manda ψ_w. Un tronco inclinado hacia delante se acorta y satura antes (sentadilla).
                !a.isNaN() && !w.isNaN() -> if (saturated) w else (a + w) / 2.0
                !a.isNaN() -> a
                !w.isNaN() -> w
                else -> Double.NaN
            }
            if (!both.isNaN()) psi[nPsi++] = both
        }

        val med2d = if (n2d >= MIN_PSI_FRAMES) RobustStats.median(psi2d.copyOf(n2d)) else Double.NaN
        val medW = if (nW >= MIN_PSI_FRAMES) RobustStats.median(psiW.copyOf(nW)) else Double.NaN
        val medPsi = if (nPsi >= MIN_PSI_FRAMES) RobustStats.median(psi.copyOf(nPsi)) else Double.NaN
        val p2d = med2d.takeUnless { it.isNaN() }
        val pw = medW.takeUnless { it.isNaN() }
        val pp = medPsi.takeUnless { it.isNaN() }
        // Con ψ_2D saturado en la mayoría de los fotogramas su mediana (0°) es solo una cota: la vista es fiable si ψ_w
        // es compatible con «casi de frente» (hasta FRONT_MAX + 25°); si no, se compara con ψ_2D sin más.
        val saturatedMajority = n2d > 0 && saturated2d > 0.5 * n2d
        val reliablePsi = when {
            p2d != null && pw != null ->
                if (saturatedMajority) pw <= FRONT_MAX_DEG + DISCREPANCY_DEG else abs(p2d - pw) <= DISCREPANCY_DEG
            else -> pp != null
        }

        if (torsoFrames > 0 && lyingFrames > LYING_FRACTION * torsoFrames) {
            return ViewEstimate(ViewKind.LYING, pp, p2d, pw, true)
        }
        if (isBack(geo, first, last)) return ViewEstimate(ViewKind.BACK, pp, p2d, pw, true)
        if (pp == null) return UNKNOWN_VIEW
        return ViewEstimate(kindOfPsi(pp), pp, p2d, pw, reliablePsi)
    }

    private val UNKNOWN_VIEW = ViewEstimate(ViewKind.UNKNOWN, null, null, null, false)

    /** ψ_w = media de atan2(|Δz|, |Δx|) de S_L−S_R y H_L−H_R en «world»; NaN si falta algún punto o no se ve. */
    private fun worldPsiDeg(f: PoseFrameV2): Double {
        val sx = f.worldX(PoseLandmark.LEFT_SHOULDER) - f.worldX(PoseLandmark.RIGHT_SHOULDER)
        val sz = f.worldZ(PoseLandmark.LEFT_SHOULDER) - f.worldZ(PoseLandmark.RIGHT_SHOULDER)
        val hx = f.worldX(PoseLandmark.LEFT_HIP) - f.worldX(PoseLandmark.RIGHT_HIP)
        val hz = f.worldZ(PoseLandmark.LEFT_HIP) - f.worldZ(PoseLandmark.RIGHT_HIP)
        if (sx.isNaN() || sz.isNaN() || hx.isNaN() || hz.isNaN()) return Double.NaN
        // «World» no trae confianza propia: se fía de la visibilidad (débil) del punto de imagen.
        if (!(f.vis(PoseLandmark.LEFT_SHOULDER) >= FrameGeometry.WEAK_VISIBILITY) ||
            !(f.vis(PoseLandmark.RIGHT_SHOULDER) >= FrameGeometry.WEAK_VISIBILITY) ||
            !(f.vis(PoseLandmark.LEFT_HIP) >= FrameGeometry.WEAK_VISIBILITY) ||
            !(f.vis(PoseLandmark.RIGHT_HIP) >= FrameGeometry.WEAK_VISIBILITY)
        ) {
            return Double.NaN
        }
        val shoulders = Math.toDegrees(atan2(abs(sz.toDouble()), abs(sx.toDouble())))
        val hips = Math.toDegrees(atan2(abs(hz.toDouble()), abs(hx.toDouble())))
        return (shoulders + hips) / 2.0
    }

    /** De espaldas: la nariz (mediana < 0,3) y las dos orejas (la mejor, < 0,5) no se ven. */
    private fun isBack(geo: FrameGeometry, first: Int, last: Int): Boolean {
        val n = last - first + 1
        val nose = DoubleArray(n)
        val earL = DoubleArray(n)
        val earR = DoubleArray(n)
        var nn = 0
        var nl = 0
        var nr = 0
        for (i in first..last) {
            val f = geo.track.frames[i]
            if (!f.hasPose) continue
            val v = f.vis(PoseLandmark.NOSE)
            if (!v.isNaN()) nose[nn++] = v.toDouble()
            val l = f.vis(PoseLandmark.LEFT_EAR)
            if (!l.isNaN()) earL[nl++] = l.toDouble()
            val r = f.vis(PoseLandmark.RIGHT_EAR)
            if (!r.isNaN()) earR[nr++] = r.toDouble()
        }
        if (nn == 0 || (nl == 0 && nr == 0)) return false
        val noseMedian = RobustStats.median(nose.copyOf(nn))
        val earMedian = max(
            if (nl > 0) RobustStats.median(earL.copyOf(nl)) else Double.NEGATIVE_INFINITY,
            if (nr > 0) RobustStats.median(earR.copyOf(nr)) else Double.NEGATIVE_INFINITY,
        )
        return noseMedian < NOSE_VISIBILITY_MAX && earMedian < EARS_VISIBILITY_MAX
    }
}

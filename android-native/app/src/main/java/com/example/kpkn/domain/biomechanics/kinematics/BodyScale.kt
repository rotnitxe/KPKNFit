package com.example.kpkn.domain.biomechanics.kinematics

import com.example.kpkn.domain.biomechanics.signal.RobustStats
import kotlin.math.max
import kotlin.math.min

/** Segmento del que sale la escala corporal. */
enum class ScaleSegment { LEG, TRUNK, ARM }

/**
 * Escala corporal S (§4, punto 13): la altura corporal en unidades FH, `bodyHeightFh`, deducida del segmento más
 * estable. `segmentLengthFh` es L̂ (P90 de su longitud 2D) y `robustCv` su coeficiente de variación robusto.
 */
data class BodyScale(val bodyHeightFh: Double, val segment: ScaleSegment, val segmentLengthFh: Double, val robustCv: Double)

/** Estima la escala corporal con las proporciones de Drillis–Contini. */
object BodyScaleEstimator {
    /** Fracción de la altura que mide cada segmento (Drillis–Contini): pierna 0,491·H, tronco 0,288·H, brazo 0,332·H. */
    fun ratio(segment: ScaleSegment): Double = when (segment) {
        ScaleSegment.LEG -> 0.491
        ScaleSegment.TRUNK -> 0.288
        ScaleSegment.ARM -> 0.332
    }

    /** Fracción mínima de fotogramas de la ventana en los que el segmento debe medirse. */
    const val MIN_VALID_FRACTION = 0.60

    /** Diferencia de CV por debajo de la cual dos candidatos se consideran igual de estables (0,2 puntos). */
    const val TIE_CV = 0.002

    /**
     * L̂ = P90 de la longitud 2D de cada candidato (pierna = muslo + pierna baja, tronco = hombro–cadera, brazo =
     * brazo + antebrazo; los seis, lado izquierdo y derecho); se elige el de menor CV robusto (MAD/mediana) entre
     * los que se miden en ≥ 60 % de los fotogramas de [frames]; S = L̂/ratio. Un CV que mejora menos de [TIE_CV] es un
     * empate y gana el orden pierna, tronco y brazo (el más largo pesa menos el ruido). `null` si ninguno cumple.
     *
     * La longitud es la 2D del plano de imagen: con la vista de lado los segmentos sagitales salen enteros; de frente,
     * los de la pierna y el tronco se acortan por la perspectiva y el CV lo delata.
     */
    fun estimate(geo: FrameGeometry, frames: IntRange): BodyScale? {
        val first = max(frames.first, 0)
        val last = min(frames.last, geo.frameCount - 1)
        if (last < first) return null
        val n = last - first + 1
        val lengths = DoubleArray(n)
        var best: BodyScale? = null
        for (segment in ScaleSegment.entries) {
            for (side in Side.entries) {
                var valid = 0
                for (k in 0 until n) {
                    val len = segmentLength(geo, first + k, segment, side)
                    lengths[k] = len
                    if (!len.isNaN()) valid++
                }
                if (valid < 3 || valid < MIN_VALID_FRACTION * n) continue
                val cv = RobustStats.cv(lengths)
                if (cv.isNaN()) continue
                val current = best
                // Solo gana un candidato posterior si es CLARAMENTE más estable (el orden pierna, tronco, brazo desempata).
                if (current != null && cv >= current.robustCv - TIE_CV) continue
                val lHat = RobustStats.percentile(lengths, 90.0)
                if (!(lHat > 0.0)) continue
                best = BodyScale(lHat / ratio(segment), segment, lHat, cv)
            }
        }
        return best
    }

    /** Longitud 2D (FH) del segmento en el fotograma [i], o NaN si falta alguno de sus puntos. */
    private fun segmentLength(geo: FrameGeometry, i: Int, segment: ScaleSegment, side: Side): Double = when (segment) {
        ScaleSegment.LEG -> chain(
            geo.distance(i, side.hip(), side.knee()),
            geo.distance(i, side.knee(), side.ankle()),
        )
        ScaleSegment.TRUNK -> geo.distance(i, side.shoulder(), side.hip())
        ScaleSegment.ARM -> chain(
            geo.distance(i, side.shoulder(), side.elbow()),
            geo.distance(i, side.elbow(), side.wrist()),
        )
    }

    private fun chain(a: Double, b: Double): Double = if (a.isNaN() || b.isNaN()) Double.NaN else a + b
}

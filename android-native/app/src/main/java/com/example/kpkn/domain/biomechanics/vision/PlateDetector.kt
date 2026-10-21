package com.example.kpkn.domain.biomechanics.vision

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot

/** Un disco propuesto por el detector, en píxeles del fotograma. [score] ordena los candidatos; [coverage] va de 0 a 1 (§3.3). */
data class PlateCandidate(val cx: Float, val cy: Float, val r: Float, val score: Float, val coverage: Float)

/**
 * Umbrales de la adquisición del disco (§8 punto 3).
 *
 * - La búsqueda es una ventana de ±[roiRadii]·r_prior alrededor de cada muñeca, con r entre [radiusMinRatio] y [radiusMaxRatio] veces
 *   r_prior (el disco está más cerca de la cámara que el cuerpo, así que suele verse mayor que el radio esperado).
 * - Puntuación = acumulador × cobertura × exp(−d²/(2·([proximityRadii]·r)²)), con d la distancia del centro a la muñeca.
 * - Dos candidatos con centros a menos de [duplicateRadii]·r cuentan como el mismo.
 * - El a/b del disco no se conoce: cada ventana se prueba con la rejilla de hipótesis de `PlateVisionConfig` (el Hough vota en un
 *   espacio estirado para que esa elipse sea un círculo) y, con el a/b medido del mejor candidato, una vez más.
 */
data class PlateDetectorConfig(
    val roiRadii: Double = 4.0,
    val radiusMinRatio: Double = 0.75,
    val radiusMaxRatio: Double = 1.7,
    val proximityRadii: Double = 1.5,
    val duplicateRadii: Double = 0.5,
    val vision: PlateVisionConfig = PlateVisionConfig(),
)

/**
 * Adquisición del disco (§8 punto 3): propone los mejores candidatos cerca de las muñecas en un fotograma con la barra quieta.
 * El usuario confirma uno y ese es la semilla del rastreador.
 */
object PlateDetector {
    private const val CANDIDATES_PER_WINDOW = 3

    /**
     * Propone hasta [max] discos cerca de las muñecas (píxeles). [expectedRadiusPx] sale de la escala corporal: r_b = 0,5·D·k_s, con
     * k_s los píxeles por metro del cuerpo. Sin muñecas busca en todo el fotograma. Devuelve los candidatos por puntuación
     * descendente, sin duplicados.
     */
    fun propose(frame: LumaFrame, wristsPx: List<Pair<Float, Float>>, expectedRadiusPx: Float, max: Int = 3): List<PlateCandidate> =
        propose(frame, wristsPx, expectedRadiusPx, max, PlateDetectorConfig())

    /** Como [propose] con los umbrales de [config]. */
    fun propose(
        frame: LumaFrame,
        wristsPx: List<Pair<Float, Float>>,
        expectedRadiusPx: Float,
        max: Int,
        config: PlateDetectorConfig,
    ): List<PlateCandidate> {
        if (!(expectedRadiusPx > 0f) || !expectedRadiusPx.isFinite() || max <= 0) return emptyList()
        val rPrior = expectedRadiusPx.toDouble()
        val vision = CircleHough(config.vision)
        val scratch = Array(CANDIDATES_PER_WINDOW) { HoughCandidate() }
        val found = ArrayList<PlateCandidate>()
        val half = config.roiRadii * rPrior
        val windows = if (wristsPx.isEmpty()) 1 else wristsPx.size
        for (w in 0 until windows) {
            val wristX = if (wristsPx.isEmpty()) Double.NaN else wristsPx[w].first.toDouble()
            val wristY = if (wristsPx.isEmpty()) Double.NaN else wristsPx[w].second.toDouble()
            val x0: Int
            val y0: Int
            val x1: Int
            val y1: Int
            if (wristsPx.isEmpty() || !wristX.isFinite() || !wristY.isFinite()) {
                x0 = 0
                y0 = 0
                x1 = frame.width
                y1 = frame.height
            } else {
                x0 = floor(wristX - half).toInt()
                y0 = floor(wristY - half).toInt()
                x1 = ceil(wristX + half).toInt()
                y1 = ceil(wristY + half).toInt()
            }

            // Rejilla de hipótesis de a/b. El mejor candidato de la ventana (por puntuación) dice con qué a/b medido repetir.
            var bestScore = -1.0
            var bestRatio = 1.0
            var bestHypothesis = 1.0
            var bestMeasured = false
            val runs = config.vision.ratioGridSteps + 1
            var run = 0
            while (run < runs) {
                val hypothesis = if (run < config.vision.ratioGridSteps) 1.0 - run * config.vision.ratioGridStep else bestRatio
                if (run == config.vision.ratioGridSteps && !(bestMeasured && abs(bestRatio - bestHypothesis) > config.vision.ratioRefitDelta)) break
                val n = vision.detect(
                    frame, x0, y0, x1, y1,
                    rMin = config.radiusMinRatio * rPrior,
                    rMax = config.radiusMaxRatio * rPrior,
                    choice = RadiusChoice.OUTERMOST,
                    rRef = rPrior,
                    ratioRef = hypothesis,
                    out = scratch,
                    maxOut = CANDIDATES_PER_WINDOW,
                )
                for (i in 0 until n) {
                    val c = scratch[i]
                    if (!(c.r > 0.0)) continue
                    val distance = if (wristX.isFinite() && wristY.isFinite()) hypot(c.cx - wristX, c.cy - wristY) else 0.0
                    val sigma = config.proximityRadii * c.r
                    val proximity = exp(-distance * distance / (2.0 * sigma * sigma))
                    val score = c.accScore * c.coverage * proximity
                    found.add(PlateCandidate(c.cx.toFloat(), c.cy.toFloat(), c.r.toFloat(), score.toFloat(), c.coverage.toFloat()))
                    if (score > bestScore) {
                        bestScore = score
                        bestRatio = c.ratio
                        bestHypothesis = c.hypothesis
                        bestMeasured = c.ratioMeasured
                    }
                }
                run++
            }
        }
        found.sortByDescending { it.score }
        val picked = ArrayList<PlateCandidate>()
        for (candidate in found) {
            var duplicate = false
            for (kept in picked) {
                if (hypot((candidate.cx - kept.cx).toDouble(), (candidate.cy - kept.cy).toDouble()) < config.duplicateRadii * kept.r) {
                    duplicate = true
                    break
                }
            }
            if (duplicate) continue
            picked.add(candidate)
            if (picked.size >= max) break
        }
        return picked
    }
}

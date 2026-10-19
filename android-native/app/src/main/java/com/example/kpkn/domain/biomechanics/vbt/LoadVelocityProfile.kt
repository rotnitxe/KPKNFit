package com.example.kpkn.domain.biomechanics.vbt

import com.example.kpkn.domain.biomechanics.metrics.AnalysisTuning
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sqrt

/** Un punto del perfil carga-velocidad: la carga y la MCV (m/s) de la FULL más rápida de una serie. */
data class LoadVelocityPoint(val loadKg: Double, val mcvMps: Double, val recordedAtMs: Long)

/**
 * Resultado del perfil carga-velocidad (§3.6): el 1RM estimado con su recta, o por qué faltan datos (`reason`):
 * `few_loads`, `narrow_range`, `low_velocity_spread`, `heaviest_too_fast`, `positive_slope`, `low_r2`, `implausible`
 * o `wide_interval`.
 */
sealed interface LoadVelocityResult {
    data class Estimate(
        val e1rmKg: Double,
        val slope: Double,
        val intercept: Double,
        val r2: Double,
        val points: Int,
    ) : LoadVelocityResult

    data class NotEnough(val reason: String) : LoadVelocityResult
}

/**
 * Perfil carga-velocidad y 1RM estimado por velocidad (§7, «Perfil carga-velocidad»): regresión lineal v = a + b·carga y
 * 1RM ≈ (MVT − a)/b, redondeado a 2,5 kg. Es solo orientativo («orientativo · beta»): con peso libre el perfil de un día es poco
 * fiable (Banyard 2017, Hughes 2019), así que se exige mucho antes de dar una cifra, y un 1RM real registrado manda sobre la
 * estimación.
 *
 * Los puntos los escoge quien llama (el repositorio de tendencias): misma `configurationKey`, disco calibrado y MCV de la FULL más
 * rápida de cada serie. Aquí se descartan además los de más de 14 días respecto del más reciente.
 *
 * **Condiciones** (si una falla sale `NotEnough` con ese código, en este orden):
 * 1. `few_loads`: menos de 3 cargas distintas.
 * 2. `narrow_range`: el rango de carga es menos del 25 % de la mayor.
 * 3. `low_velocity_spread`: el rango de MCV es menos de 0,30 m/s.
 * 4. `heaviest_too_fast`: la MCV del punto más pesado pasa de 0,60 m/s (banca: 0,50, ver [heaviestMaxMps]).
 * 5. `positive_slope`: la pendiente no es negativa.
 * 6. `low_r2`: R² < 0,95 con 3 puntos, o < 0,90 con 4 o más.
 * 7. `implausible`: el 1RM sale por debajo de la carga mayor, por encima de 1,6× la carga mayor, o el MVT no es válido.
 * 8. `wide_interval`: el intervalo de predicción del 95 % del 1RM es más ancho que ±10 %.
 */
object LoadVelocityProfile {
    private const val MS_PER_DAY = 86_400_000L

    /** Valor crítico de la t de Student a dos colas al 95 % para 1 a 30 grados de libertad. */
    private val T_95 = doubleArrayOf(
        12.706, 4.303, 3.182, 2.776, 2.571, 2.447, 2.365, 2.306, 2.262, 2.228,
        2.201, 2.179, 2.160, 2.145, 2.131, 2.120, 2.110, 2.101, 2.093, 2.086,
        2.080, 2.074, 2.069, 2.064, 2.060, 2.056, 2.052, 2.048, 2.045, 2.042,
    )

    /**
     * La MCV máxima del punto más pesado para dar el 1RM: 0,50 m/s en la banca (su MVT es 0,17) y 0,60 en el resto. Quien llama la
     * pasa a [estimate] como `heaviestMaxMps`.
     */
    fun heaviestMaxMps(profile: AnalysisProfileId, tuning: AnalysisTuning = AnalysisTuning.DEFAULT): Double =
        if (profile == AnalysisProfileId.BENCH) tuning.vbt.lvMaxHeaviestBenchMps else tuning.vbt.lvMaxHeaviestMps

    fun estimate(
        points: List<LoadVelocityPoint>,
        mvtMps: Double,
        heaviestMaxMps: Double? = null,
        tuning: AnalysisTuning = AnalysisTuning.DEFAULT,
    ): LoadVelocityResult {
        val vbt = tuning.vbt
        val valid = points.filter { it.loadKg.isFinite() && it.loadKg > 0.0 && it.mcvMps.isFinite() && it.mcvMps > 0.0 }
        val newest = valid.maxOfOrNull { it.recordedAtMs } ?: return LoadVelocityResult.NotEnough("few_loads")
        val maxAgeMs = vbt.lvMaxAgeDays * MS_PER_DAY
        val used = valid.filter { newest - it.recordedAtMs <= maxAgeMs }

        // 1. Cargas distintas.
        if (distinctLoads(used, vbt.lvDistinctLoadKg) < vbt.lvMinLoads) return LoadVelocityResult.NotEnough("few_loads")

        // 2. Rango de carga.
        val maxLoad = used.maxOf { it.loadKg }
        val minLoad = used.minOf { it.loadKg }
        if ((maxLoad - minLoad) / maxLoad < vbt.lvMinLoadRangeFraction) return LoadVelocityResult.NotEnough("narrow_range")

        // 3. Rango de MCV.
        val maxV = used.maxOf { it.mcvMps }
        val minV = used.minOf { it.mcvMps }
        if (maxV - minV < vbt.lvMinVelocityRangeMps) return LoadVelocityResult.NotEnough("low_velocity_spread")

        // 4. El punto más pesado, lo bastante lento: se mira el más rápido de los que tienen la carga mayor.
        val heaviestMcv = used.filter { maxLoad - it.loadKg <= vbt.lvDistinctLoadKg }.maxOf { it.mcvMps }
        if (heaviestMcv > (heaviestMaxMps ?: vbt.lvMaxHeaviestMps)) return LoadVelocityResult.NotEnough("heaviest_too_fast")

        // 5. Regresión lineal v = a + b·carga.
        val n = used.size
        var meanX = 0.0
        var meanY = 0.0
        for (p in used) {
            meanX += p.loadKg
            meanY += p.mcvMps
        }
        meanX /= n
        meanY /= n
        var sxx = 0.0
        var sxy = 0.0
        var syy = 0.0
        for (p in used) {
            val dx = p.loadKg - meanX
            val dy = p.mcvMps - meanY
            sxx += dx * dx
            sxy += dx * dy
            syy += dy * dy
        }
        if (!(sxx > 0.0) || !(syy > 0.0)) return LoadVelocityResult.NotEnough("low_velocity_spread")
        val slope = sxy / sxx
        if (!(slope < 0.0)) return LoadVelocityResult.NotEnough("positive_slope")
        val intercept = meanY - slope * meanX

        // 6. Ajuste.
        var sse = 0.0
        for (p in used) {
            val e = p.mcvMps - (intercept + slope * p.loadKg)
            sse += e * e
        }
        val r2 = 1.0 - sse / syy
        val minR2 = if (n >= 4) vbt.lvMinR2FourPlusPoints else vbt.lvMinR2ThreePoints
        if (r2 < minR2) return LoadVelocityResult.NotEnough("low_r2")

        // 7. 1RM = (MVT − a)/b, a 2,5 kg; ni por debajo de la carga mayor ni por encima de 1,6× ella.
        if (!mvtMps.isFinite() || !(mvtMps > 0.0)) return LoadVelocityResult.NotEnough("implausible")
        val raw = (mvtMps - intercept) / slope
        if (!raw.isFinite()) return LoadVelocityResult.NotEnough("implausible")
        val e1rm = (raw / vbt.lvRoundingKg).roundToLong() * vbt.lvRoundingKg
        if (e1rm < maxLoad || e1rm > vbt.lvMaxOverMaxLoad * maxLoad) return LoadVelocityResult.NotEnough("implausible")

        // 8. Intervalo de predicción al 95 % del 1RM (predicción inversa): t·s/|b|·√(1 + 1/n + (x0 − x̄)²/Sxx).
        val dof = n - 2
        val s = sqrt(max(0.0, sse) / dof)
        val halfWidth = tCritical(dof) * s / abs(slope) * sqrt(1.0 + 1.0 / n + (raw - meanX) * (raw - meanX) / sxx)
        if (halfWidth / raw > vbt.lvMaxIntervalFraction) return LoadVelocityResult.NotEnough("wide_interval")

        return LoadVelocityResult.Estimate(e1rm, slope, intercept, r2, n)
    }

    /** Número de cargas distintas: ordenadas, una carga nueva empieza cuando se separa más de [toleranceKg] de la anterior. */
    private fun distinctLoads(points: List<LoadVelocityPoint>, toleranceKg: Double): Int {
        val loads = points.map { it.loadKg }.sorted()
        if (loads.isEmpty()) return 0
        var count = 1
        var reference = loads.first()
        for (load in loads) {
            if (load - reference >= toleranceKg) {
                count++
                reference = load
            }
        }
        return count
    }

    private fun tCritical(dof: Int): Double =
        if (dof <= 0) Double.POSITIVE_INFINITY else if (dof <= T_95.size) T_95[min(dof, T_95.size) - 1] else 1.96 + 2.5 / dof
}

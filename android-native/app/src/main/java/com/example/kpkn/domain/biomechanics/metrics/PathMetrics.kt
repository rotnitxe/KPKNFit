package com.example.kpkn.domain.biomechanics.metrics

import com.example.kpkn.domain.biomechanics.kinematics.Side
import com.example.kpkn.domain.biomechanics.kinematics.heel
import com.example.kpkn.domain.biomechanics.kinematics.toe
import com.example.kpkn.domain.biomechanics.profiles.PathRule
import com.example.kpkn.domain.biomechanics.signal.RobustStats
import com.example.kpkn.domain.biomechanics.vbt.PlateScaleResult
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Trayectoria de la barra en una repetición, en alturas corporales (BH); con el disco calibrado (`PathCalibration`), también en
 * centímetros. NaN donde no hay dato. Los valores horizontales llevan el signo de la orientación f: positivo es hacia donde mira
 * el atleta.
 *
 * @property offsetMeanBh media de |X − x_ref|, con x_ref el medio pie.
 * @property offsetMaxBh X − x_ref en el punto de mayor desvío, con signo.
 * @property driftBh X(fin de la CON) − X(inicio de la CON), con signo.
 * @property excursionBh máx X − mín X en la repetición.
 * @property romVBh recorrido vertical de la barra (máx Y − mín Y).
 * @property deviationRmsBh banca: PD, el RMS de la trayectoria respecto de la trayectoria mediana de la serie.
 * @property offsetMeanCm [offsetMeanBh] en cm, corregido de paralaje si la focal es conocida; NaN si el desvío absoluto está
 *   oculto (PARALLAX_SUPPRESSED) o no hay calibración. Lo mismo [offsetMaxCm].
 * @property driftCm [driftBh], [excursionCm] y [deviationRmsCm] en cm: son traslaciones del propio disco, así que no llevan
 *   paralaje. [romVCm] es el recorrido vertical en cm (con la escala vertical).
 */
class RepPath(
    val offsetMeanBh: Double,
    val offsetMaxBh: Double,
    val driftBh: Double,
    val excursionBh: Double,
    val romVBh: Double,
    val deviationRmsBh: Double,
    val offsetMeanCm: Double = Double.NaN,
    val offsetMaxCm: Double = Double.NaN,
    val driftCm: Double = Double.NaN,
    val excursionCm: Double = Double.NaN,
    val romVCm: Double = Double.NaN,
    val deviationRmsCm: Double = Double.NaN,
) {
    /** Excursión horizontal / ROM_v (sin unidad); el índice de verticalidad es 1 − min(1, esto). */
    val excursionFraction: Double get() = if (romVBh > 0.0) excursionBh / romVBh else Double.NaN

    companion object {
        val NONE: RepPath = RepPath(Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN)
    }
}

/**
 * Cómo pasar la trayectoria del disco a centímetros (VT3b). Solo vale cuando el punto de barra es el propio disco
 * (`series.source == "plate"`): la escala del disco es la de su plano y nunca se aplica a puntos del cuerpo.
 *
 * @property metersPerPxH metros por píxel horizontal y [metersPerPxV] vertical (corregidos por la elipse).
 * @property depthRatio Z_s/Z_p: cuánto más lejos de la cámara está el plano sagital del atleta que el del disco.
 * @property correctParallax la focal es conocida: el desvío respecto del medio pie se corrige de paralaje.
 * @property hideOffsets sin focal y con un error de paralaje de más de 2 cm: no se da el desvío absoluto (PARALLAX_SUPPRESSED).
 * @property frameHeightPx alto del fotograma de análisis en píxeles.
 */
class PathCalibration(
    val metersPerPxH: Double,
    val metersPerPxV: Double,
    val depthRatio: Double,
    val correctParallax: Boolean,
    val hideOffsets: Boolean,
    val frameHeightPx: Int,
) {
    companion object {
        /** La calibración de un [PlateScaleResult] con escala válida; `null` si no la tiene. */
        fun of(plate: PlateScaleResult, frameHeightPx: Int): PathCalibration? {
            val scale = plate.scale ?: return null
            val h = scale.metersPerPxH ?: return null
            val v = scale.metersPerPxV ?: return null
            if (!(h > 0.0) || !(v > 0.0) || frameHeightPx <= 0) return null
            return PathCalibration(h, v, plate.depthRatio, plate.parallaxCorrected, plate.offsetsHidden, frameHeightPx)
        }
    }
}

/**
 * Trayectoria de una serie: [reps] va por posición de ciclo. [barDeviationPctRom] es, en la sentadilla, el peso muerto y el
 * press militar, la mediana de offMean en % de ROM_v; en la banca, la mediana de PD en % de ROM_v (la consistencia).
 * [medianRomVBh] es la mediana de ROM_v de las FULL y [contactSdBh] (banca) la DE del punto de contacto, en BH.
 *
 * Con calibración ([calibrated]): [barDeviationCm] es lo mismo que [barDeviationPctRom] en cm (el desvío respecto del medio pie,
 * que es `null` si [offsetsHidden]; en la banca, la mediana de PD), [medianRomVCm] el recorrido vertical mediano y [contactSdCm]
 * la DE del punto de contacto.
 */
class PathSummary(
    val available: Boolean,
    val rule: PathRule,
    val reps: List<RepPath>,
    val medianRomVBh: Double?,
    val barDeviationPctRom: Double?,
    val contactSdBh: Double?,
    val calibrated: Boolean = false,
    val offsetsHidden: Boolean = false,
    val barDeviationCm: Double? = null,
    val medianRomVCm: Double? = null,
    val contactSdCm: Double? = null,
)

/**
 * Trayectoria de la barra (§7, «Trayectoria»). El punto de barra es el disco si la serie se cuenta con él (`source == "plate"`)
 * y, si no, el sustituto de pose de `BarPointRule`; viene de `CountSeries.barX/barY`, en BH, y aquí se suaviza a 5 Hz.
 *
 * - **MIDFOOT_VERTICAL** (sentadilla, peso muerto, rumano, press militar): x_ref = mediana de X del medio pie con visibilidad ≥ 0,6 y
 *   pies quietos (DE robusta < 0,02 BH); si los pies se mueven no hay referencia y no hay trayectoria. Por repetición: offMean,
 *   offMax con signo, deriva en la concéntrica y excursión / ROM_v.
 * - **CONSISTENCY** (banca): la bajada y la subida de cada repetición se remuestrean en 21 niveles de progreso vertical; PD = RMS de
 *   X respecto de la trayectoria mediana de las FULL (con al menos 3); y DE del punto de contacto. La J es normal: aquí solo
 *   se mide cuánto se repite.
 *
 * Solo en las vistas de métrica del perfil.
 *
 * **Con el disco calibrado** ([PathCalibration], VT3b) cada repetición lleva además sus cifras en centímetros. Los BH pasan a
 * píxeles con la altura corporal S (FH por BH) y el alto del fotograma, y a metros con la escala del disco: la horizontal para el
 * desvío, la deriva y la excursión, la vertical para el recorrido. La deriva y la excursión son traslaciones del propio disco y no
 * llevan paralaje. El desvío respecto del medio pie sí: el disco está ≈ 0,70 m más cerca de la cámara que el pie, y el pie se
 * proyecta con otra escala; con la focal conocida, el desvío se corrige poniendo el pie en el plano del disco,
 * `X_p − X_ref·Z_s/Z_p` medidos desde el centro de la imagen (el punto principal). Sin la focal y con un error de más de 2 cm
 * (PARALLAX_SUPPRESSED), el desvío absoluto queda sin dato.
 */
object PathMetrics {
    fun compute(ctx: MetricContext, calibration: PathCalibration? = null): PathSummary {
        val n = ctx.cycles.size
        val rule = ctx.profile.pathRule
        val none = PathSummary(false, rule, List(n) { RepPath.NONE }, null, null, null)
        if (rule == PathRule.NONE || n == 0 || !ctx.metricViewOk) return none
        val x = ctx.barXSmooth ?: return none
        val y = ctx.barYSmooth ?: return none
        return when (rule) {
            PathRule.MIDFOOT_VERTICAL -> midfoot(ctx, x, y, none, calibration)
            PathRule.CONSISTENCY -> consistency(ctx, x, y, none, calibration)
            PathRule.NONE -> none
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // MIDFOOT_VERTICAL
    // ------------------------------------------------------------------------------------------------------------

    private fun midfoot(ctx: MetricContext, x: DoubleArray, y: DoubleArray, none: PathSummary, calibration: PathCalibration?): PathSummary {
        val near = ctx.near ?: return none
        val s = ctx.bodyHeightFh ?: return none
        val xRef = footReference(ctx, near, s) ?: return none
        val tuning = ctx.tuning
        val f = ctx.facing.toDouble()
        val half = tuning.metrics.instantWindowHalfS

        // cm por BH: píxeles por BH (S·alto del fotograma) por metros por píxel por 100.
        val cmPerBhH = if (calibration != null) s * calibration.frameHeightPx * calibration.metersPerPxH * 100.0 else Double.NaN
        val cmPerBhV = if (calibration != null) s * calibration.frameHeightPx * calibration.metersPerPxV * 100.0 else Double.NaN
        // Desvío absoluto en cm: en FH, f·((X_p·S − c) − (x_ref·S − c)·k), con c = aspect/2 el punto principal y k = Z_s/Z_p si se corrige.
        val centre = ctx.prepared.geo.aspect / 2.0
        val depth = if (calibration != null && calibration.correctParallax) calibration.depthRatio else 1.0
        val footFh = xRef * s - centre
        val offsetsOk = calibration != null && !calibration.hideOffsets

        val reps = ArrayList<RepPath>(ctx.cycles.size)
        val offsets = ArrayList<Double>()
        val offsetsCm = ArrayList<Double>()
        val romVs = ArrayList<Double>()
        val romVsCm = ArrayList<Double>()
        var any = false
        for (cycle in ctx.cycles) {
            val w = ctx.repWindow(cycle, 0.0)
            if (w == null ||
                SeriesMath.validFraction(x, w.first, w.last) < tuning.metrics.minWindowValidFraction ||
                SeriesMath.validFraction(y, w.first, w.last) < tuning.metrics.minWindowValidFraction
            ) {
                reps.add(RepPath.NONE)
                continue
            }
            var sum = 0.0
            var count = 0
            var offMax = 0.0
            var sumCm = 0.0
            var offMaxCm = 0.0
            var minX = Double.POSITIVE_INFINITY
            var maxX = Double.NEGATIVE_INFINITY
            var minY = Double.POSITIVE_INFINITY
            var maxY = Double.NEGATIVE_INFINITY
            for (k in w) {
                val xv = x[k]
                val yv = y[k]
                if (xv.isNaN() || yv.isNaN()) continue
                val off = f * (xv - xRef)
                sum += abs(off)
                count++
                if (abs(off) > abs(offMax)) offMax = off
                if (calibration != null && offsetsOk) {
                    val offCm = f * ((xv * s - centre) - footFh * depth) * calibration.frameHeightPx * calibration.metersPerPxH * 100.0
                    sumCm += abs(offCm)
                    if (abs(offCm) > abs(offMaxCm)) offMaxCm = offCm
                }
                minX = min(minX, xv)
                maxX = max(maxX, xv)
                minY = min(minY, yv)
                maxY = max(maxY, yv)
            }
            if (count == 0) {
                reps.add(RepPath.NONE)
                continue
            }
            val span = ctx.concentric(cycle)
            val drift = if (span != null) {
                f * (ctx.meanAround(x, span.toUs, half) - ctx.meanAround(x, span.fromUs, half))
            } else {
                Double.NaN
            }
            val rep = if (calibration != null) {
                RepPath(
                    sum / count, offMax, drift, maxX - minX, maxY - minY, Double.NaN,
                    offsetMeanCm = if (offsetsOk) sumCm / count else Double.NaN,
                    offsetMaxCm = if (offsetsOk) offMaxCm else Double.NaN,
                    driftCm = drift * cmPerBhH,
                    excursionCm = (maxX - minX) * cmPerBhH,
                    romVCm = (maxY - minY) * cmPerBhV,
                )
            } else {
                RepPath(sum / count, offMax, drift, maxX - minX, maxY - minY, Double.NaN)
            }
            reps.add(rep)
            any = true
            if (cycle.kind == RepKind.FULL && rep.romVBh > 0.0) {
                offsets.add(rep.offsetMeanBh / rep.romVBh * 100.0)
                romVs.add(rep.romVBh)
                if (calibration != null) {
                    romVsCm.add(rep.romVCm)
                    if (offsetsOk) offsetsCm.add(rep.offsetMeanCm)
                }
            }
        }
        if (!any) return none
        return PathSummary(
            available = true,
            rule = PathRule.MIDFOOT_VERTICAL,
            reps = reps,
            medianRomVBh = if (romVs.isEmpty()) null else SeriesMath.finiteOrNull(SeriesMath.median(romVs)),
            barDeviationPctRom = if (offsets.isEmpty()) null else SeriesMath.finiteOrNull(SeriesMath.median(offsets)),
            contactSdBh = null,
            calibrated = calibration != null,
            offsetsHidden = calibration != null && !offsetsOk,
            barDeviationCm = if (offsetsCm.isEmpty()) null else SeriesMath.finiteOrNull(SeriesMath.median(offsetsCm)),
            medianRomVCm = if (romVsCm.isEmpty()) null else SeriesMath.finiteOrNull(SeriesMath.median(romVsCm)),
        )
    }

    /**
     * x_ref en BH: mediana de la X del medio pie del lado cercano (visibilidad ≥ 0,6 del talón y la punta) en la ventana de las
     * repeticiones. `null` si hay menos de [MIN_FOOT_SAMPLES] muestras o los pies no están quietos (DE robusta ≥ 0,02 BH).
     */
    private fun footReference(ctx: MetricContext, near: Side, s: Double): Double? {
        val tuning = ctx.tuning
        val geo = ctx.prepared.geo
        val frames = ctx.prepared.track.frames
        val heel = near.heel()
        val toe = near.toe()
        val raw = DoubleArray(frames.size) { Double.NaN }
        for (i in frames.indices) {
            val frame = frames[i]
            if (!(frame.vis(heel) >= tuning.metrics.pathFootVisibility) || !(frame.vis(toe) >= tuning.metrics.pathFootVisibility)) continue
            val mf = geo.midFoot(i, near) ?: continue
            raw[i] = mf.x / s
        }
        val feet = MetricSeriesBuilder.fromFrames(ctx.prepared, raw, tuning).v
        var from = Int.MAX_VALUE
        var to = Int.MIN_VALUE
        for (cycle in ctx.cycles) {
            val w = ctx.repWindow(cycle, 0.0) ?: continue
            from = min(from, w.first)
            to = max(to, w.last)
        }
        if (from > to) return null
        val values = DoubleArray(to - from + 1)
        var count = 0
        for (k in from..to) if (!feet[k].isNaN()) values[count++] = feet[k]
        if (count < MIN_FOOT_SAMPLES) return null
        val used = values.copyOf(count)
        val sd = RobustStats.mad(used)
        if (sd.isNaN() || sd >= tuning.metrics.pathFeetStillBh) return null
        return RobustStats.median(used)
    }

    // ------------------------------------------------------------------------------------------------------------
    // CONSISTENCY (banca)
    // ------------------------------------------------------------------------------------------------------------

    private fun consistency(ctx: MetricContext, x: DoubleArray, y: DoubleArray, none: PathSummary, calibration: PathCalibration?): PathSummary {
        val tuning = ctx.tuning
        val levels = tuning.metrics.pathLevels
        if (levels < 2) return none
        val s = ctx.bodyHeightFh
        val cmPerBhH = if (calibration != null && s != null) s * calibration.frameHeightPx * calibration.metersPerPxH * 100.0 else Double.NaN
        val cmPerBhV = if (calibration != null && s != null) s * calibration.frameHeightPx * calibration.metersPerPxV * 100.0 else Double.NaN
        val n = ctx.cycles.size
        val down = Array(n) { DoubleArray(levels) { Double.NaN } }
        val up = Array(n) { DoubleArray(levels) { Double.NaN } }
        val romV = DoubleArray(n) { Double.NaN }
        val contact = DoubleArray(n) { Double.NaN }
        for ((k, cycle) in ctx.cycles.withIndex()) {
            val w = ctx.repWindow(cycle, 0.0) ?: continue
            if (SeriesMath.validFraction(x, w.first, w.last) < tuning.metrics.minWindowValidFraction ||
                SeriesMath.validFraction(y, w.first, w.last) < tuning.metrics.minWindowValidFraction
            ) {
                continue
            }
            val top = SeriesMath.minIn(y, w.first, w.last)
            val pivot = SeriesMath.argMax(y, w.first, w.last)
            if (pivot < 0 || top.isNaN()) continue
            val range = y[pivot] - top
            if (!(range > 0.0)) continue
            sampleLevels(x, y, w.first, pivot, top, range, rising = true, out = down[k])
            sampleLevels(x, y, pivot, w.last, top, range, rising = false, out = up[k])
            romV[k] = range
            contact[k] = x[pivot]
        }

        // Trayectoria mediana de las FULL, nivel a nivel.
        val fullIdx = ctx.cycles.indices.filter { ctx.cycles[it].kind == RepKind.FULL && !romV[it].isNaN() }
        if (fullIdx.size < tuning.metrics.pathMinReps) return none
        val medianDown = DoubleArray(levels) { levelMedian(down, fullIdx, it, tuning.metrics.pathMinReps) }
        val medianUp = DoubleArray(levels) { levelMedian(up, fullIdx, it, tuning.metrics.pathMinReps) }

        val reps = ArrayList<RepPath>(n)
        val pds = ArrayList<Double>()
        for (k in 0 until n) {
            if (romV[k].isNaN()) {
                reps.add(RepPath.NONE)
                continue
            }
            var sumSq = 0.0
            var count = 0
            for (j in 0 until levels) {
                if (!down[k][j].isNaN() && !medianDown[j].isNaN()) {
                    sumSq += (down[k][j] - medianDown[j]) * (down[k][j] - medianDown[j])
                    count++
                }
                if (!up[k][j].isNaN() && !medianUp[j].isNaN()) {
                    sumSq += (up[k][j] - medianUp[j]) * (up[k][j] - medianUp[j])
                    count++
                }
            }
            val pd = if (count * 2 >= 2 * levels) sqrt(sumSq / count) else Double.NaN
            reps.add(
                if (calibration != null) {
                    RepPath(
                        Double.NaN, Double.NaN, Double.NaN, Double.NaN, romV[k], pd,
                        romVCm = romV[k] * cmPerBhV,
                        deviationRmsCm = pd * cmPerBhH,
                    )
                } else {
                    RepPath(Double.NaN, Double.NaN, Double.NaN, Double.NaN, romV[k], pd)
                },
            )
            if (ctx.cycles[k].kind == RepKind.FULL && !pd.isNaN()) pds.add(pd)
        }
        if (pds.isEmpty()) return none
        val medianRom = SeriesMath.median(fullIdx.map { romV[it] })
        val contactSd = SeriesMath.sampleSd(fullIdx.map { contact[it] }.filter { !it.isNaN() })
        return PathSummary(
            available = true,
            rule = PathRule.CONSISTENCY,
            reps = reps,
            medianRomVBh = SeriesMath.finiteOrNull(medianRom),
            barDeviationPctRom = if (medianRom > 0.0) SeriesMath.finiteOrNull(SeriesMath.median(pds) / medianRom * 100.0) else null,
            contactSdBh = SeriesMath.finiteOrNull(contactSd),
            calibrated = calibration != null,
            offsetsHidden = false,
            barDeviationCm = if (calibration != null) SeriesMath.finiteOrNull(SeriesMath.median(pds) * cmPerBhH) else null,
            medianRomVCm = if (calibration != null) SeriesMath.finiteOrNull(medianRom * cmPerBhV) else null,
            contactSdCm = if (calibration != null) SeriesMath.finiteOrNull(contactSd * cmPerBhH) else null,
        )
    }

    /** Mediana del nivel [level] entre las repeticiones [indices] que lo tienen medido; NaN con menos de [minReps]. */
    private fun levelMedian(paths: Array<DoubleArray>, indices: List<Int>, level: Int, minReps: Int): Double {
        val values = ArrayList<Double>(indices.size)
        for (i in indices) if (!paths[i][level].isNaN()) values.add(paths[i][level])
        return if (values.size < minReps) Double.NaN else SeriesMath.median(values)
    }

    /**
     * Remuestrea la X de la barra en `out.size` niveles de progreso vertical p = (Y − [top])/[range] (0 arriba, 1 en el fondo)
     * sobre [from, to]. Con [rising] (la bajada) p crece con el tiempo; si no (la subida), decrece. Cada nivel es el primer
     * cruce, con interpolación lineal; un nivel que la barra no alcanza queda NaN.
     */
    private fun sampleLevels(
        x: DoubleArray,
        y: DoubleArray,
        from: Int,
        to: Int,
        top: Double,
        range: Double,
        rising: Boolean,
        out: DoubleArray,
    ) {
        val levels = out.size
        var k = from
        // Los niveles se recorren en el orden en que la barra los alcanza.
        val order = if (rising) 0 until levels else (levels - 1) downTo 0
        for (j in order) {
            val level = j.toDouble() / (levels - 1)
            while (k <= to) {
                val p = (y[k] - top) / range
                if (!p.isNaN() && (if (rising) p >= level else p <= level)) break
                k++
            }
            if (k > to) break
            val p1 = (y[k] - top) / range
            if (k > from && !x[k - 1].isNaN() && !x[k].isNaN()) {
                val p0 = (y[k - 1] - top) / range
                if (!p0.isNaN() && p1 != p0) {
                    val t = ((level - p0) / (p1 - p0)).coerceIn(0.0, 1.0)
                    out[j] = x[k - 1] + (x[k] - x[k - 1]) * t
                    continue
                }
            }
            out[j] = x[k]
        }
    }

    /** Muestras de medio pie necesarias para dar una referencia. */
    private const val MIN_FOOT_SAMPLES = 10
}

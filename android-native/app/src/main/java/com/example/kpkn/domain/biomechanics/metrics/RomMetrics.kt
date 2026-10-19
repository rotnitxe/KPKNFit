package com.example.kpkn.domain.biomechanics.metrics

import com.example.kpkn.domain.biomechanics.kinematics.Side
import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.profiles.RomAngle
import com.example.kpkn.domain.biomechanics.reps.RepCycle

/**
 * ROM de una serie (§7). [perRepDeg] va por posición de ciclo (`MetricContext.cycles`) y vale NaN donde no hay dato. Las
 * cifras de la serie salen de las repeticiones FULL: [sdDeg] necesita 2, y [trendDeg] y [slopeDegPerRep], al menos
 * `MetricsTuning.romTrendMinFull`.
 *
 * @property trendDeg media de las 2 últimas FULL − media de las 2 primeras (negativo: el recorrido se acorta).
 * @property slopeDegPerRep pendiente OLS del ROM frente al número de repetición, en °/rep.
 */
class RomSummary(
    val available: Boolean,
    val perRepDeg: DoubleArray,
    val meanDeg: Double?,
    val sdDeg: Double?,
    val cvPct: Double?,
    val trendDeg: Double?,
    val slopeDegPerRep: Double?,
)

/**
 * ROM por repetición y por serie (§7, «ROM»).
 *
 * - **Por repetición:** el rango del ángulo del perfil (`JointAngleSeries`, a 5 Hz) durante la repetición. Para una repetición
 *   que sale del reposo, llega a un giro y vuelve es |ángulo(giro) − ángulo(salida)|, pero no depende del instante exacto del
 *   giro: se busca entre la salida y la llegada de `RepPhaseResult` con un margen de [MetricsTuning.repWindowMarginS] (la
 *   salida y la llegada de la sentadilla sin disco se sitúan a ±0,5 s) y sobre una media móvil de 0,3 s que quita el ruido
 *   del pico.
 * - **Por serie:** media, DE, CV y tendencia de las FULL.
 * - **Disponibilidad:** solo si la vista está en `metricViews` del perfil; en RAISE (β no depende del giro), en cualquier vista.
 *   En otra vista no hay ROM (D17: en oblicuo no hay ROM en v1).
 */
object RomMetrics {
    fun compute(ctx: MetricContext): RomSummary {
        val cycles = ctx.cycles
        val perRep = DoubleArray(cycles.size) { Double.NaN }
        val none = RomSummary(false, perRep, null, null, null, null, null)
        val angle = ctx.profile.romAngle ?: return none
        if (cycles.isEmpty() || !ctx.geometryOk) return none
        if (angle != RomAngle.ARM_ELEVATION && !ctx.metricViewOk) return none

        val tuning = ctx.tuning
        val smoothed = HashMap<Side, DoubleArray>()
        val missing = HashSet<Side>()
        fun smoothedOf(side: Side): DoubleArray? {
            smoothed[side]?.let { return it }
            if (side in missing) return null
            val sm = JointAngleSeries.build(ctx.prepared, angle, side, tuning)?.let { SeriesMath.movingAverage(it.v, ctx.extremumHalf) }
            if (sm == null) missing.add(side) else smoothed[side] = sm
            return sm
        }
        val front = if (usesFrontLeg(ctx)) frontLegSeries(ctx) else null

        for ((k, cycle) in cycles.withIndex()) {
            val side = sideOf(ctx, cycle, front) ?: continue
            val sm = smoothedOf(side) ?: continue
            val window = ctx.repWindow(cycle, tuning.metrics.repWindowMarginS) ?: continue
            if (SeriesMath.validFraction(sm, window.first, window.last) < tuning.metrics.minWindowValidFraction) continue
            val high = SeriesMath.maxIn(sm, window.first, window.last)
            val low = SeriesMath.minIn(sm, window.first, window.last)
            if (!high.isNaN() && !low.isNaN()) perRep[k] = high - low
        }

        val fullRoms = ArrayList<Double>()
        for ((k, cycle) in cycles.withIndex()) {
            if (cycle.kind == RepKind.FULL && !perRep[k].isNaN()) fullRoms.add(perRep[k])
        }
        val available = perRep.any { !it.isNaN() }
        if (fullRoms.isEmpty()) return RomSummary(available, perRep, null, null, null, null, null)

        val mean = fullRoms.average()
        val sd = SeriesMath.sampleSd(fullRoms)
        val cv = if (mean > 0.0 && !sd.isNaN()) sd / mean * 100.0 else Double.NaN
        val hasTrend = fullRoms.size >= tuning.metrics.romTrendMinFull
        val trend = if (hasTrend) fullRoms.takeLast(TREND_REPS).average() - fullRoms.take(TREND_REPS).average() else Double.NaN
        val slope = if (hasTrend) SeriesMath.olsSlope(fullRoms) else Double.NaN
        return RomSummary(
            available = available,
            perRepDeg = perRep,
            meanDeg = SeriesMath.finiteOrNull(mean),
            sdDeg = SeriesMath.finiteOrNull(sd),
            cvPct = SeriesMath.finiteOrNull(cv),
            trendDeg = SeriesMath.finiteOrNull(trend),
            slopeDegPerRep = SeriesMath.finiteOrNull(slope),
        )
    }

    /** La tendencia compara las medias de las [TREND_REPS] últimas y las [TREND_REPS] primeras FULL. */
    private const val TREND_REPS = 2

    /** Zancada y subida al cajón: la rodilla que se mide es la delantera (§5). */
    private fun usesFrontLeg(ctx: MetricContext): Boolean =
        ctx.profile.id == AnalysisProfileId.LUNGE || ctx.profile.id == AnalysisProfileId.STEP_UP

    /**
     * Lado cuyo ángulo se mide en [cycle]: en zancada y subida al cajón, el del tobillo adelantado (según la orientación f) en
     * el giro; en los demás, el lado del ciclo (conteo por lado), el único lado fiable o el cercano a la cámara.
     */
    private fun sideOf(ctx: MetricContext, cycle: RepCycle, front: MetricSeries?): Side? {
        if (front != null) {
            val ph = ctx.phasesOf(cycle)
            val v = if (ph != null) {
                front.valueAt((ph.turnStartUs + ph.turnEndUs) / 2)
            } else {
                front.v.getOrElse(cycle.peak) { Double.NaN }
            }
            if (!v.isNaN()) return if (v >= 0.0) Side.LEFT else Side.RIGHT
        }
        return ctx.workSideOf(cycle)
    }

    /** f·(X_tobillo izquierdo − X_tobillo derecho): positivo si el pie izquierdo va por delante de donde mira el atleta. */
    private fun frontLegSeries(ctx: MetricContext): MetricSeries {
        val geo = ctx.prepared.geo
        val f = ctx.facing.toDouble()
        val raw = DoubleArray(ctx.prepared.track.frames.size) { Double.NaN }
        for (i in raw.indices) {
            val l = geo.point(i, PoseLandmark.LEFT_ANKLE, weak = true) ?: continue
            val r = geo.point(i, PoseLandmark.RIGHT_ANKLE, weak = true) ?: continue
            raw[i] = f * (l.x - r.x)
        }
        return MetricSeriesBuilder.fromFrames(ctx.prepared, raw, ctx.tuning)
    }
}

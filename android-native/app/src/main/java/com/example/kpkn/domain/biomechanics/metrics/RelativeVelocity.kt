package com.example.kpkn.domain.biomechanics.metrics

import com.example.kpkn.domain.biomechanics.reps.RepCycle
import com.example.kpkn.domain.biomechanics.reps.SignalUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Velocidad de la concéntrica de una repetición, en la unidad de [VelocitySummary.unit]; NaN donde no hay dato. */
class RepVelocity(val mcv: Double, val peak: Double) {
    companion object {
        val NONE: RepVelocity = RepVelocity(Double.NaN, Double.NaN)
    }
}

/**
 * Velocidad relativa de una serie: [reps] va por posición de ciclo; el resto sale de las repeticiones FULL.
 * [lossPct] = (mejor − última)/mejor·100, con al menos 3 FULL. Todo en [unit]; nunca en m/s (eso lo hace VT3b con el disco calibrado).
 */
class VelocitySummary(
    val available: Boolean,
    val peakAvailable: Boolean,
    val unit: VelocityUnit?,
    val reps: List<RepVelocity>,
    val first: Double?,
    val best: Double?,
    val last: Double?,
    val peakBest: Double?,
    val lossPct: Double?,
)

/**
 * Velocidad sin calibrar (§7, «Velocidad»).
 *
 * - **Unidad:** con señal de desplazamiento, la velocidad vertical del punto de barra en BH/s ([VelocityUnit.BODY_HEIGHTS_PER_S]; si
 *   el perfil no tiene punto de barra, la de la propia señal de conteo); con señal de ángulo, la angular ([VelocityUnit.DEG_PER_S]).
 * - **MCV por repetición:** desplazamiento entre el inicio y el fin de la concéntrica de `RepPhaseResult`, entre su duración.
 *   Las repeticiones fallidas no tienen MCV.
 * - **Pico:** el máximo de |v| de la concéntrica (derivada central de la señal con paso bajo); solo con fs ≥ 50.
 * - **Por serie:** `first`, `best` y `last` son la MCV de la primera, la más rápida y la última FULL; la pérdida de velocidad
 *   = (mejor − última)/mejor·100, con al menos 3 FULL. Se excluyen las parciales y las fallidas. (En v1 no hay perfiles solo
 *   excéntricos: el resolver los marca `Unsupported`.)
 * - **Disponibilidad:** en las vistas de conteo del perfil, y con fs ≥ 20 (hay fases).
 *
 * Sin calibrar, las velocidades solo se muestran en % (la pérdida y las barras relativas a la mejor).
 */
object RelativeVelocity {
    fun compute(ctx: MetricContext): VelocitySummary {
        val n = ctx.cycles.size
        val none = VelocitySummary(false, false, null, List(n) { RepVelocity.NONE }, null, null, null, null, null)
        if (n == 0 || !ctx.geometryOk || ctx.phases.reps.isEmpty() || ctx.view !in ctx.profile.countViews) return none

        val tuning = ctx.tuning
        val angular = ctx.series.unit == SignalUnit.DEGREES
        val barY = ctx.barYSmooth
        // Una razón (dominadas, jalones) no es un desplazamiento: sin punto de barra no hay velocidad.
        if (!angular && barY == null && ctx.series.unit != SignalUnit.BODY_HEIGHTS) return none
        val unit = if (angular) VelocityUnit.DEG_PER_S else VelocityUnit.BODY_HEIGHTS_PER_S
        val fs = ctx.series.fs
        val wantsPeak = fs >= tuning.metrics.peakMinFps

        fun signalOf(cycle: RepCycle): DoubleArray = when {
            angular -> ctx.smoothedCount(cycle.side)
            barY != null -> barY
            else -> ctx.smoothedCount(cycle.side)
        }

        val reps = ArrayList<RepVelocity>(n)
        for (cycle in ctx.cycles) {
            val span = if (cycle.kind == RepKind.FAILED) null else ctx.concentric(cycle)
            if (span == null) {
                reps.add(RepVelocity.NONE)
                continue
            }
            val seconds = (span.toUs - span.fromUs) / SeriesMath.MICROS
            val x = signalOf(cycle)
            val i0 = ctx.indexOf(span.fromUs)
            val i1 = ctx.indexOf(span.toUs)
            val y0 = SeriesMath.interpolate(x, i0)
            val y1 = SeriesMath.interpolate(x, i1)
            if (seconds < tuning.metrics.minConcentricS || y0.isNaN() || y1.isNaN()) {
                reps.add(RepVelocity.NONE)
                continue
            }
            var peak = Double.NaN
            if (wantsPeak) {
                var best = 0.0
                var found = false
                for (k in max(1, ceil(i0).toInt())..min(x.size - 2, floor(i1).toInt())) {
                    val a = x[k + 1]
                    val b = x[k - 1]
                    if (a.isNaN() || b.isNaN()) continue
                    best = max(best, abs(a - b) * fs / 2.0)
                    found = true
                }
                if (found) peak = best
            }
            reps.add(RepVelocity(abs(y1 - y0) / seconds, peak))
        }

        val fullMcv = ArrayList<Double>()
        var peakBest = Double.NaN
        var any = false
        var anyPeak = false
        for ((k, cycle) in ctx.cycles.withIndex()) {
            val rep = reps[k]
            if (!rep.mcv.isNaN()) any = true
            if (!rep.peak.isNaN()) anyPeak = true
            if (cycle.kind != RepKind.FULL) continue
            if (!rep.mcv.isNaN()) fullMcv.add(rep.mcv)
            if (!rep.peak.isNaN() && (peakBest.isNaN() || rep.peak > peakBest)) peakBest = rep.peak
        }
        if (!any) return none
        val best = if (fullMcv.isEmpty()) Double.NaN else fullMcv.max()
        val loss = if (fullMcv.size >= tuning.metrics.vlMinFull && best > 0.0) (best - fullMcv.last()) / best * 100.0 else Double.NaN
        return VelocitySummary(
            available = true,
            peakAvailable = anyPeak,
            unit = unit,
            reps = reps,
            first = if (fullMcv.isEmpty()) null else SeriesMath.finiteOrNull(fullMcv.first()),
            best = SeriesMath.finiteOrNull(best),
            last = if (fullMcv.isEmpty()) null else SeriesMath.finiteOrNull(fullMcv.last()),
            peakBest = SeriesMath.finiteOrNull(peakBest),
            lossPct = SeriesMath.finiteOrNull(loss),
        )
    }
}

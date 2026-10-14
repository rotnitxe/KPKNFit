package com.example.kpkn.domain.biomechanics.metrics

import com.example.kpkn.domain.biomechanics.kinematics.ViewKind
import com.example.kpkn.domain.biomechanics.profiles.RomAngle
import com.example.kpkn.domain.biomechanics.reps.RepCycle
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Inclinación del tronco de una repetición (§7, «Tronco»); NaN donde no hay dato. τ es positivo si el tronco se inclina hacia
 * delante (hacia donde mira el atleta).
 *
 * @property atTurnDeg media de τ alrededor del giro.
 * @property maxDeg τ máximo de la repetición.
 * @property rangeDeg τ máximo − τ mínimo de la repetición.
 * @property earlyDeltaDeg τ al 30 % del desplazamiento de la concéntrica − τ al inicio de la concéntrica.
 */
class RepTrunk(val atTurnDeg: Double, val maxDeg: Double, val rangeDeg: Double, val earlyDeltaDeg: Double) {
    companion object {
        val NONE: RepTrunk = RepTrunk(Double.NaN, Double.NaN, Double.NaN, Double.NaN)
    }
}

/** Tronco de una serie: [reps] va por posición de ciclo; [meanDeg] y [sdDeg] son de τ en el giro de las FULL. */
class TrunkSummary(
    val available: Boolean,
    val reps: List<RepTrunk>,
    val meanDeg: Double?,
    val sdDeg: Double?,
)

/**
 * τ = atan2(f·(X_Sn − X_Hn), Y_Hn − Y_Sn) del lado cercano, con el giro del móvil corregido y a 5 Hz (§7, «Tronco»).
 *
 * - **Por repetición:** τ en el giro (media de la ventana del giro), τ máx., rango entre la salida y la llegada, y Δτ temprano: del
 *   inicio de la concéntrica al 30 % de su desplazamiento, medido sobre la señal de conteo suavizada (la misma de las fases).
 *   Los instantes de la concéntrica salen de `RepPhaseResult`.
 * - **Por serie:** media y DE de τ en el giro de las FULL.
 * - **Disponibilidad:** solo en SIDE, o en LYING en los perfiles tumbados; y siempre dentro de las vistas de métrica del perfil.
 */
object TrunkMetrics {
    fun compute(ctx: MetricContext): TrunkSummary {
        val n = ctx.cycles.size
        val none = TrunkSummary(false, List(n) { RepTrunk.NONE }, null, null)
        val near = ctx.near ?: return none
        val viewOk = (ctx.view == ViewKind.SIDE || ctx.view == ViewKind.LYING) && ctx.metricViewOk
        if (n == 0 || !viewOk) return none

        val tau = JointAngleSeries.build(ctx.prepared, RomAngle.TRUNK, near, ctx.tuning) ?: return none
        val sm = SeriesMath.movingAverage(tau.v, ctx.extremumHalf)

        val reps = ArrayList<RepTrunk>(n)
        val fullTurn = ArrayList<Double>()
        var any = false
        for (cycle in ctx.cycles) {
            val rep = repTrunk(ctx, cycle, tau, sm)
            reps.add(rep)
            if (!rep.atTurnDeg.isNaN()) {
                any = true
                if (cycle.kind == RepKind.FULL) fullTurn.add(rep.atTurnDeg)
            }
        }
        return TrunkSummary(
            available = any,
            reps = reps,
            meanDeg = if (fullTurn.isEmpty()) null else SeriesMath.finiteOrNull(fullTurn.average()),
            sdDeg = SeriesMath.finiteOrNull(SeriesMath.sampleSd(fullTurn)),
        )
    }

    private fun repTrunk(ctx: MetricContext, cycle: RepCycle, tau: MetricSeries, sm: DoubleArray): RepTrunk {
        val tuning = ctx.tuning
        val window = ctx.repWindow(cycle, tuning.metrics.repWindowMarginS)
        var max = Double.NaN
        var range = Double.NaN
        if (window != null && SeriesMath.validFraction(sm, window.first, window.last) >= tuning.metrics.minWindowValidFraction) {
            max = SeriesMath.maxIn(sm, window.first, window.last)
            range = max - SeriesMath.minIn(sm, window.first, window.last)
        }
        val turn = ctx.turnWindow(cycle)
        val atTurn = if (turn != null) SeriesMath.meanIn(tau.v, turn.first, turn.last, tuning.metrics.minWindowValidFraction) else Double.NaN
        return RepTrunk(atTurn, max, range, earlyDelta(ctx, cycle, tau))
    }

    /**
     * Δτ temprano. El 30 % del desplazamiento de la concéntrica se busca en la señal de conteo suavizada: el primer instante en
     * que ha recorrido el 30 % de lo que recorre de punta a punta de la concéntrica.
     */
    private fun earlyDelta(ctx: MetricContext, cycle: RepCycle, tau: MetricSeries): Double {
        val span = ctx.concentric(cycle) ?: return Double.NaN
        val x = ctx.smoothedCount(cycle.side)
        val i0 = ctx.indexOf(span.fromUs)
        val i1 = ctx.indexOf(span.toUs)
        val v0 = SeriesMath.interpolate(x, i0)
        val v1 = SeriesMath.interpolate(x, i1)
        val total = v1 - v0
        if (v0.isNaN() || v1.isNaN() || !(abs(total) > 0.0)) return Double.NaN

        val target = ctx.tuning.metrics.earlyConFraction
        val last = floor(i1).toInt().coerceAtMost(x.size - 1)
        var crossing = Double.NaN
        var prev = Double.NaN
        var prevProgress = Double.NaN
        var k = ceil(i0).toInt().coerceAtLeast(0)
        while (k <= last) {
            val progress = (x[k] - v0) / total
            if (!progress.isNaN()) {
                if (progress >= target) {
                    crossing = if (prev.isNaN() || !(progress > prevProgress)) {
                        k.toDouble()
                    } else {
                        prev + (k - prev) * (target - prevProgress) / (progress - prevProgress)
                    }
                    break
                }
                prev = k.toDouble()
                prevProgress = progress
            }
            k++
        }
        if (crossing.isNaN()) return Double.NaN

        val half = ctx.tuning.metrics.instantWindowHalfS
        val tStart = ctx.meanAround(tau.v, span.fromUs, half)
        val crossingUs = ctx.series.t0Us + Math.round(crossing / ctx.series.fs * SeriesMath.MICROS)
        val tEarly = ctx.meanAround(tau.v, crossingUs, half)
        return if (tStart.isNaN() || tEarly.isNaN()) Double.NaN else tEarly - tStart
    }
}

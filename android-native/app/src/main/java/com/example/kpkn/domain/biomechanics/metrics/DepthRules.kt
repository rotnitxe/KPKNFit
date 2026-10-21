package com.example.kpkn.domain.biomechanics.metrics

import com.example.kpkn.domain.biomechanics.kinematics.FrameGeometry
import com.example.kpkn.domain.biomechanics.kinematics.Side
import com.example.kpkn.domain.biomechanics.kinematics.ViewKind
import com.example.kpkn.domain.biomechanics.kinematics.elbow
import com.example.kpkn.domain.biomechanics.kinematics.hip
import com.example.kpkn.domain.biomechanics.kinematics.knee
import com.example.kpkn.domain.biomechanics.kinematics.shoulder
import com.example.kpkn.domain.biomechanics.kinematics.wrist
import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import com.example.kpkn.domain.biomechanics.profiles.DepthRule
import com.example.kpkn.domain.biomechanics.profiles.LockoutRule
import com.example.kpkn.domain.biomechanics.profiles.RomAngle
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2

/** Claves de `RepReport.criteria` (§3.5). */
object DepthCriteria {
    const val CHIN_OVER_BAR = "chinOverBar"
    const val FULL_HANG = "fullHang"
    const val LOCKOUT = "lockout"
    const val PUSH_UP_FULL = "pushUpFull"
    const val DIP_PARALLEL = "dipParallel"
    const val SHALLOWER = "shallower"
}

/** Profundidad y criterios de una repetición; lo que no se pudo medir queda `null` o ausente de [criteria]. */
class RepDepth(
    val depthClass: DepthClass? = null,
    val depthDeg: Double? = null,
    val criteria: Map<String, Boolean> = emptyMap(),
)

/** Profundidad y bloqueos de una serie: [reps] va por posición de ciclo (`MetricContext.cycles`). */
class DepthSummary(
    val reps: List<RepDepth>,
    val depthAvailable: Boolean,
    val lockoutAvailable: Boolean,
)

/**
 * Profundidad, criterios y bloqueos por repetición (§7, «Profundidad y criterios»). Todo se da solo en las vistas de métrica
 * del perfil. Los valores extremos (el fondo, el arriba) salen del máximo o el mínimo de una media móvil de 0,3 s entre la
 * salida y la llegada de la repetición (con margen), no de un instante, porque la sentadilla sin disco sitúa sus fases a ±0,5 s;
 * los bloqueos sí se miden en el fin de la concéntrica de `RepPhaseResult`.
 *
 * - SQUAT_PARALLEL: α = asin((Y_H − Y_K)/L̂_muslo) en el fondo (> 0: cadera bajo la rodilla): BELOW si α ≥ +5°, PARALLEL si |α| < 5°,
 *   ABOVE si α ≤ −5°. De lado se contrasta con atan2(Y_H − Y_K, |X_K − X_H|): si discrepan más de 10°, la repetición queda sin
 *   clase. No es arbitraje IPF; VT10 calibra el sesgo del punto de cadera de MediaPipe.
 * - CHIN_OVER_BAR: mentón ≈ boca + 1,5·(boca − nariz); barra ≈ Y media de índice y meñique. `chinOverBar` si el mentón queda
 *   al menos 0,02·L̂_tronco sobre la barra; `fullHang` si θE ≥ 155° abajo.
 * - PUSH_UP_ELBOW: `depthDeg` = θE mínimo; `pushUpFull` si ≤ 90°.
 * - DIP_SHOULDER: `depthDeg` = asin((Y_S − Y_E)/L̂_brazo); `dipParallel` si ≥ 0.
 * - BENCH_RELATIVE: sin regla absoluta; `shallower` si el fondo queda más de un 10 % de ROM_v por encima de la mediana de las FULL
 *   (con al menos 3 FULL).
 * - Bloqueos, al fin de la concéntrica: peso muerto θH ≥ 165° y θK ≥ 160°; hip thrust θH ≥ 165°; press militar θE ≥ 160° y la
 *   muñeca por encima de la nariz. Clave `lockout`.
 */
object DepthRules {
    fun compute(ctx: MetricContext): DepthSummary {
        val n = ctx.cycles.size
        val acc = Acc(n)
        var depthAvailable = false
        var lockoutAvailable = false
        val near = ctx.near
        if (n > 0 && ctx.metricViewOk && near != null) {
            val depthRule = ctx.profile.depthRule
            if (depthRule != null) {
                depthAvailable = when (depthRule) {
                    DepthRule.SQUAT_PARALLEL -> squat(ctx, near, acc)
                    DepthRule.CHIN_OVER_BAR -> chinOverBar(ctx, near, acc)
                    DepthRule.PUSH_UP_ELBOW -> pushUp(ctx, near, acc)
                    DepthRule.DIP_SHOULDER -> dip(ctx, near, acc)
                    DepthRule.BENCH_RELATIVE -> bench(ctx, acc)
                }
            }
            val lockoutRule = ctx.profile.lockoutRule
            if (lockoutRule != null) lockoutAvailable = lockout(ctx, near, lockoutRule, acc)
        }
        val reps = List(n) { k ->
            RepDepth(acc.classes[k], SeriesMath.finiteOrNull(acc.degs[k]), acc.criteria[k])
        }
        return DepthSummary(reps, depthAvailable, lockoutAvailable)
    }

    private class Acc(n: Int) {
        val classes = arrayOfNulls<DepthClass>(n)
        val degs = DoubleArray(n) { Double.NaN }
        val criteria = Array(n) { LinkedHashMap<String, Boolean>() }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Sentadilla
    // ------------------------------------------------------------------------------------------------------------

    private fun squat(ctx: MetricContext, near: Side, acc: Acc): Boolean {
        val geo = ctx.prepared.geo
        val hip = near.hip()
        val knee = near.knee()
        val ref = MetricSeriesBuilder.segmentRef(geo, hip, knee)
        if (!(ref > 0.0)) return false
        val frames = ctx.prepared.track.frames.size
        val alpha = DoubleArray(frames) { Double.NaN }
        val check = DoubleArray(frames) { Double.NaN }
        for (i in 0 until frames) {
            val ph = geo.point(i, hip) ?: continue
            val pk = geo.point(i, knee) ?: continue
            val dy = ph.y - pk.y
            alpha[i] = Math.toDegrees(asin((dy / ref).coerceIn(-1.0, 1.0)))
            check[i] = Math.toDegrees(atan2(dy, abs(pk.x - ph.x)))
        }
        val tuning = ctx.tuning
        val a = SeriesMath.movingAverage(MetricSeriesBuilder.fromFrames(ctx.prepared, alpha, tuning).v, ctx.extremumHalf)
        val c = SeriesMath.movingAverage(MetricSeriesBuilder.fromFrames(ctx.prepared, check, tuning).v, ctx.extremumHalf)
        val sideView = ctx.view == ViewKind.SIDE
        var any = false
        for ((k, cycle) in ctx.cycles.withIndex()) {
            val w = ctx.repWindow(cycle, tuning.metrics.repWindowMarginS) ?: continue
            if (SeriesMath.validFraction(a, w.first, w.last) < tuning.metrics.minWindowValidFraction) continue
            val at = SeriesMath.argMax(a, w.first, w.last)
            if (at < 0) continue
            val bottom = a[at]
            if (sideView) {
                val other = c[at]
                if (other.isNaN() || abs(bottom - other) > tuning.metrics.depthCrossCheckDeg) continue
            }
            acc.degs[k] = bottom
            acc.classes[k] = when {
                bottom >= tuning.metrics.depthParallelDeg -> DepthClass.BELOW
                bottom <= -tuning.metrics.depthParallelDeg -> DepthClass.ABOVE
                else -> DepthClass.PARALLEL
            }
            any = true
        }
        return any
    }

    // ------------------------------------------------------------------------------------------------------------
    // Dominada
    // ------------------------------------------------------------------------------------------------------------

    private fun chinOverBar(ctx: MetricContext, near: Side, acc: Acc): Boolean {
        val geo = ctx.prepared.geo
        val tuning = ctx.tuning
        val trunkRef = MetricSeriesBuilder.segmentRef(geo, near.shoulder(), near.hip())
        if (!(trunkRef > 0.0)) return false
        val frames = ctx.prepared.track.frames.size
        // Barra − mentón (FH): positivo si el mentón está por encima de la barra (Y crece hacia abajo).
        val clearance = DoubleArray(frames) { Double.NaN }
        for (i in 0 until frames) {
            val nose = geo.point(i, PoseLandmark.NOSE) ?: continue
            val mouthY = meanY(geo, i, PoseLandmark.MOUTH_LEFT, PoseLandmark.MOUTH_RIGHT)
            val left = meanY(geo, i, PoseLandmark.LEFT_INDEX, PoseLandmark.LEFT_PINKY)
            val right = meanY(geo, i, PoseLandmark.RIGHT_INDEX, PoseLandmark.RIGHT_PINKY)
            val handY = when {
                !left.isNaN() && !right.isNaN() -> 0.5 * (left + right)
                !left.isNaN() -> left
                else -> right
            }
            if (mouthY.isNaN() || handY.isNaN()) continue
            val chinY = mouthY + tuning.metrics.chinBelowMouthFactor * (mouthY - nose.y)
            clearance[i] = handY - chinY
        }
        val half = ctx.extremumHalf
        val top = SeriesMath.movingAverage(MetricSeriesBuilder.fromFrames(ctx.prepared, clearance, tuning).v, half)
        val elbow = JointAngleSeries.build(ctx.prepared, RomAngle.ELBOW, near, tuning)?.let { SeriesMath.movingAverage(it.v, half) }
        val needed = tuning.metrics.chinClearanceTrunk * trunkRef
        var any = false
        for ((k, cycle) in ctx.cycles.withIndex()) {
            val w = ctx.repWindow(cycle, tuning.metrics.repWindowMarginS) ?: continue
            if (SeriesMath.validFraction(top, w.first, w.last) >= tuning.metrics.minWindowValidFraction) {
                val best = SeriesMath.maxIn(top, w.first, w.last)
                if (!best.isNaN()) {
                    acc.criteria[k][DepthCriteria.CHIN_OVER_BAR] = best >= needed
                    any = true
                }
            }
            if (elbow != null && SeriesMath.validFraction(elbow, w.first, w.last) >= tuning.metrics.minWindowValidFraction) {
                val flexion = SeriesMath.minIn(elbow, w.first, w.last)
                if (!flexion.isNaN()) {
                    acc.criteria[k][DepthCriteria.FULL_HANG] = STRAIGHT - flexion >= tuning.metrics.fullHangElbowDeg
                    any = true
                }
            }
        }
        return any
    }

    /** Media de las Y de [a] y [b] (FH) si están, o la del que esté; NaN si no hay ninguno. */
    private fun meanY(geo: FrameGeometry, i: Int, a: PoseLandmark, b: PoseLandmark): Double {
        val pa = geo.point(i, a)
        val pb = geo.point(i, b)
        return when {
            pa != null && pb != null -> 0.5 * (pa.y + pb.y)
            pa != null -> pa.y
            pb != null -> pb.y
            else -> Double.NaN
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Flexiones y fondos
    // ------------------------------------------------------------------------------------------------------------

    private fun pushUp(ctx: MetricContext, near: Side, acc: Acc): Boolean {
        val tuning = ctx.tuning
        val elbow = JointAngleSeries.build(ctx.prepared, RomAngle.ELBOW, near, tuning) ?: return false
        val sm = SeriesMath.movingAverage(elbow.v, ctx.extremumHalf)
        var any = false
        for ((k, cycle) in ctx.cycles.withIndex()) {
            val w = ctx.repWindow(cycle, tuning.metrics.repWindowMarginS) ?: continue
            if (SeriesMath.validFraction(sm, w.first, w.last) < tuning.metrics.minWindowValidFraction) continue
            val flexion = SeriesMath.maxIn(sm, w.first, w.last)
            if (flexion.isNaN()) continue
            val thetaMin = STRAIGHT - flexion
            acc.degs[k] = thetaMin
            acc.criteria[k][DepthCriteria.PUSH_UP_FULL] = thetaMin <= tuning.metrics.pushUpFullElbowDeg
            any = true
        }
        return any
    }

    private fun dip(ctx: MetricContext, near: Side, acc: Acc): Boolean {
        val geo = ctx.prepared.geo
        val tuning = ctx.tuning
        val ref = MetricSeriesBuilder.segmentRef(geo, near.shoulder(), near.elbow())
        if (!(ref > 0.0)) return false
        val frames = ctx.prepared.track.frames.size
        val alpha = DoubleArray(frames) { Double.NaN }
        for (i in 0 until frames) {
            val ps = geo.point(i, near.shoulder()) ?: continue
            val pe = geo.point(i, near.elbow()) ?: continue
            alpha[i] = Math.toDegrees(asin(((ps.y - pe.y) / ref).coerceIn(-1.0, 1.0)))
        }
        val sm = SeriesMath.movingAverage(MetricSeriesBuilder.fromFrames(ctx.prepared, alpha, tuning).v, ctx.extremumHalf)
        var any = false
        for ((k, cycle) in ctx.cycles.withIndex()) {
            val w = ctx.repWindow(cycle, tuning.metrics.repWindowMarginS) ?: continue
            if (SeriesMath.validFraction(sm, w.first, w.last) < tuning.metrics.minWindowValidFraction) continue
            val bottom = SeriesMath.maxIn(sm, w.first, w.last)
            if (bottom.isNaN()) continue
            acc.degs[k] = bottom
            acc.criteria[k][DepthCriteria.DIP_PARALLEL] = bottom >= 0.0
            any = true
        }
        return any
    }

    // ------------------------------------------------------------------------------------------------------------
    // Banca
    // ------------------------------------------------------------------------------------------------------------

    private fun bench(ctx: MetricContext, acc: Acc): Boolean {
        val tuning = ctx.tuning
        val y = ctx.barYSmooth ?: return false
        val sm = SeriesMath.movingAverage(y, ctx.extremumHalf)
        val n = ctx.cycles.size
        val bottoms = DoubleArray(n) { Double.NaN }
        val ranges = DoubleArray(n) { Double.NaN }
        val fullBottoms = ArrayList<Double>()
        val fullRanges = ArrayList<Double>()
        for ((k, cycle) in ctx.cycles.withIndex()) {
            val w = ctx.repWindow(cycle, 0.0) ?: continue
            if (SeriesMath.validFraction(sm, w.first, w.last) < tuning.metrics.minWindowValidFraction) continue
            val low = SeriesMath.maxIn(sm, w.first, w.last) // Y crece hacia abajo: el fondo es el máximo
            val high = SeriesMath.minIn(sm, w.first, w.last)
            if (low.isNaN() || high.isNaN()) continue
            bottoms[k] = low
            ranges[k] = low - high
            if (cycle.kind == RepKind.FULL) {
                fullBottoms.add(low)
                fullRanges.add(low - high)
            }
        }
        if (fullBottoms.size < tuning.metrics.benchMinFull) return false
        val medianBottom = SeriesMath.median(fullBottoms)
        val medianRange = SeriesMath.median(fullRanges)
        if (medianBottom.isNaN() || !(medianRange > 0.0)) return false
        var any = false
        for (k in 0 until n) {
            if (bottoms[k].isNaN()) continue
            acc.criteria[k][DepthCriteria.SHALLOWER] = medianBottom - bottoms[k] > tuning.metrics.benchShallowerFraction * medianRange
            any = true
        }
        return any
    }

    // ------------------------------------------------------------------------------------------------------------
    // Bloqueos
    // ------------------------------------------------------------------------------------------------------------

    private fun lockout(ctx: MetricContext, near: Side, rule: LockoutRule, acc: Acc): Boolean {
        val tuning = ctx.tuning
        val hip = if (rule == LockoutRule.DEADLIFT || rule == LockoutRule.HIP_THRUST) {
            JointAngleSeries.build(ctx.prepared, RomAngle.HIP, near, tuning) ?: return false
        } else {
            null
        }
        val knee = if (rule == LockoutRule.DEADLIFT) {
            JointAngleSeries.build(ctx.prepared, RomAngle.KNEE, near, tuning) ?: return false
        } else {
            null
        }
        val elbow = if (rule == LockoutRule.OHP) {
            JointAngleSeries.build(ctx.prepared, RomAngle.ELBOW, near, tuning) ?: return false
        } else {
            null
        }
        val wristAboveNose = if (rule == LockoutRule.OHP) wristMinusNose(ctx, near) else null

        var any = false
        for ((k, cycle) in ctx.cycles.withIndex()) {
            val end = ctx.concentric(cycle)?.toUs ?: continue
            val half = tuning.metrics.instantWindowHalfS
            val ok: Boolean = when (rule) {
                LockoutRule.DEADLIFT -> {
                    val h = ctx.meanAround(hip!!.v, end, half)
                    val kn = ctx.meanAround(knee!!.v, end, half)
                    if (h.isNaN() || kn.isNaN()) continue
                    STRAIGHT - h >= tuning.metrics.lockoutDeadliftHipDeg && STRAIGHT - kn >= tuning.metrics.lockoutDeadliftKneeDeg
                }
                LockoutRule.HIP_THRUST -> {
                    val h = ctx.meanAround(hip!!.v, end, half)
                    if (h.isNaN()) continue
                    STRAIGHT - h >= tuning.metrics.lockoutHipThrustHipDeg
                }
                LockoutRule.OHP -> {
                    val e = ctx.meanAround(elbow!!.v, end, half)
                    val w = ctx.meanAround(wristAboveNose!!.v, end, half)
                    if (e.isNaN() || w.isNaN()) continue
                    STRAIGHT - e >= tuning.metrics.lockoutOhpElbowDeg && w > 0.0
                }
            }
            acc.criteria[k][DepthCriteria.LOCKOUT] = ok
            any = true
        }
        return any
    }

    /** Y_nariz − Y_muñeca (FH) del lado [near]: positivo si la muñeca está por encima de la nariz. */
    private fun wristMinusNose(ctx: MetricContext, near: Side): MetricSeries {
        val geo = ctx.prepared.geo
        val raw = DoubleArray(ctx.prepared.track.frames.size) { Double.NaN }
        for (i in raw.indices) {
            val nose = geo.point(i, PoseLandmark.NOSE) ?: continue
            val wrist = geo.point(i, near.wrist()) ?: continue
            raw[i] = nose.y - wrist.y
        }
        return MetricSeriesBuilder.fromFrames(ctx.prepared, raw, ctx.tuning)
    }

    private const val STRAIGHT = 180.0
}

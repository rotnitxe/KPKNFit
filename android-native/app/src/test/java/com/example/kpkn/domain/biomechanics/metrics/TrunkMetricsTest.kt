package com.example.kpkn.domain.biomechanics.metrics

import com.example.kpkn.domain.biomechanics.kinematics.ViewKind
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfiles
import com.example.kpkn.domain.biomechanics.profiles.CountSignal
import com.example.kpkn.domain.biomechanics.reps.RepTestSupport
import com.example.kpkn.domain.biomechanics.testing.SynthCamera
import com.example.kpkn.domain.biomechanics.testing.SynthMovement
import org.junit.Test
import java.util.Locale
import kotlin.math.abs

/** Inclinación del tronco τ (§7, «Tronco»): con el generador, sin ruido, contra la postura 3D de la que sale la traza. */
class TrunkMetricsTest {
    private fun fmt(x: Double) = String.format(Locale.ROOT, "%.1f", x)

    @Test
    fun trunkAtTheTurnOfASquatIsWithinTwoDegreesOfTheTruth() {
        val fails = RepTestSupport.Failures()
        // La sentadilla se cuenta aquí con el ángulo de rodilla (lineal en el progreso del generador), para que las fases —y con
        // ellas «el giro»— caigan donde dice el guion; la de cadera las sitúa a ±0,5 s.
        val profile = AnalysisProfiles.of(AnalysisProfileId.SQUAT).copy(poseSignal = CountSignal.KNEE_FLEXION, minAmplitude = 30.0)
        for (b in listOf(1.0, 0.8)) {
            val truth = MetricsTestSupport.trunkLeanDeg(SynthMovement.SQUAT, b)
            val rest = MetricsTestSupport.trunkLeanDeg(SynthMovement.SQUAT, 0.0)
            val r = MetricsTestSupport.gen(SynthMovement.SQUAT, MetricsTestSupport.script(List(5) { b }))
            val s = MetricsTestSupport.scene(r, profile)!!
            val trunk = TrunkMetrics.compute(s.ctx)
            println("[tronco] sentadilla b=$b: ${MetricsTestSupport.describe(s)} | verdad en el giro ${fmt(truth)}° (reposo ${fmt(rest)}°) | en el giro ${trunk.reps.map { fmt(it.atTurnDeg) }} máx ${trunk.reps.map { fmt(it.maxDeg) }} rango ${trunk.reps.map { fmt(it.rangeDeg) }} | media ${trunk.meanDeg} DE ${trunk.sdDeg}")
            fails.check(s.run.seg.fullCount == 5) { "b=$b: conteo ${MetricsTestSupport.describe(s)}" }
            fails.check(trunk.available) { "b=$b: el tronco no está disponible" }
            for ((i, rep) in trunk.reps.withIndex()) {
                fails.check(abs(rep.atTurnDeg - truth) <= 2.0) { "b=$b, repetición $i: τ en el giro ${rep.atTurnDeg}° (verdad $truth ± 2)" }
                fails.check(abs(rep.maxDeg - truth) <= 2.5) { "b=$b, repetición $i: τ máx ${rep.maxDeg}° (verdad $truth)" }
                fails.check(abs(rep.rangeDeg - (truth - rest)) <= 3.0) { "b=$b, repetición $i: rango ${rep.rangeDeg}° (verdad ${truth - rest})" }
            }
            val mean = trunk.meanDeg
            fails.check(mean != null && abs(mean - truth) <= 2.0) { "b=$b: τ medio $mean (verdad $truth)" }
        }
        fails.assertNone()
    }

    @Test
    fun theEarlyDeltaOfADeadliftIsTheLeanLostInTheFirstThirtyPercent() {
        val fails = RepTestSupport.Failures()
        val r = MetricsTestSupport.gen(SynthMovement.DEADLIFT, MetricsTestSupport.script(List(5) { 1.0 }))
        val s = MetricsTestSupport.scene(r, RepTestSupport.profile(AnalysisProfileId.DEADLIFT))!!
        val trunk = TrunkMetrics.compute(s.ctx)
        // El hombro sube linealmente con el progreso b y la inclinación baja 40° → 0°: al 30 % del desplazamiento (b = 0,3)
        // el tronco ha perdido 12°.
        val floorLean = MetricsTestSupport.trunkLeanDeg(SynthMovement.DEADLIFT, 0.0)
        val early = MetricsTestSupport.trunkLeanDeg(SynthMovement.DEADLIFT, 0.3) - floorLean
        val top = MetricsTestSupport.trunkLeanDeg(SynthMovement.DEADLIFT, 1.0)
        println("[tronco] peso muerto: ${MetricsTestSupport.describe(s)} | verdad: suelo ${fmt(floorLean)}°, Δ al 30 % ${fmt(early)}°, bloqueo ${fmt(top)}° | Δ ${trunk.reps.map { fmt(it.earlyDeltaDeg) }} giro ${trunk.reps.map { fmt(it.atTurnDeg) }} máx ${trunk.reps.map { fmt(it.maxDeg) }}")
        fails.check(s.run.seg.fullCount == 5) { "conteo: ${MetricsTestSupport.describe(s)}" }
        for ((i, rep) in trunk.reps.withIndex()) {
            fails.check(abs(rep.earlyDeltaDeg - early) <= 3.0) { "repetición $i: Δτ temprano ${rep.earlyDeltaDeg}° (verdad $early ± 3)" }
            fails.check(abs(rep.atTurnDeg - top) <= 2.5) { "repetición $i: τ en el bloqueo ${rep.atTurnDeg}° (verdad $top)" }
            fails.check(abs(rep.maxDeg - floorLean) <= 2.5) { "repetición $i: τ máx ${rep.maxDeg}° (verdad $floorLean)" }
        }
        fails.assertNone()
    }

    @Test
    fun theTrunkIsOnlyGivenFromTheSideOrLyingDown() {
        val fails = RepTestSupport.Failures()
        // De frente: no hay tronco (ni siquiera en un perfil que cuenta de frente).
        val front = MetricsTestSupport.gen(SynthMovement.SQUAT, MetricsTestSupport.script(List(5) { 1.0 }), camera = SynthCamera(yawDeg = 0.0))
        val sf = MetricsTestSupport.scene(front, RepTestSupport.profile(AnalysisProfileId.SQUAT))!!
        val tf = TrunkMetrics.compute(sf.ctx)
        fails.check(sf.ctx.view == ViewKind.FRONT) { "la vista es ${sf.ctx.view}" }
        fails.check(!tf.available && tf.meanDeg == null && tf.reps.all { it.atTurnDeg.isNaN() }) { "de frente no debería haber tronco" }

        // Tumbado (banca): sí, y el tronco está horizontal.
        val bench = MetricsTestSupport.gen(SynthMovement.BENCH, MetricsTestSupport.script(List(5) { 1.0 }))
        val sb = MetricsTestSupport.scene(bench, RepTestSupport.profile(AnalysisProfileId.BENCH))!!
        val tb = TrunkMetrics.compute(sb.ctx)
        println("[tronco] banca: vista ${sb.ctx.view} ${MetricsTestSupport.describe(sb)} | τ en el giro ${tb.reps.map { fmt(it.atTurnDeg) }}")
        fails.check(sb.ctx.view == ViewKind.LYING) { "la vista de la banca es ${sb.ctx.view}" }
        fails.check(tb.available) { "tumbado debería haber tronco" }
        fails.check(tb.reps.all { abs(it.atTurnDeg) in 80.0..100.0 }) { "τ de la banca: ${tb.reps.map { it.atTurnDeg }} (esperado ≈ ±90°)" }
        fails.assertNone()
    }
}

package com.example.kpkn.domain.biomechanics.metrics

import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.reps.RepTestSupport
import com.example.kpkn.domain.biomechanics.testing.SynthMovement
import com.example.kpkn.domain.biomechanics.testing.SynthRepKind
import com.example.kpkn.domain.biomechanics.testing.SynthSegment
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import kotlin.math.abs

/**
 * Velocidad relativa (§7, «Velocidad»). Se usa el peso muerto (arranque C): la altura de la mano es lineal en el progreso del
 * generador, así que las fases caen donde dice el guion; en la sentadilla sin disco, la cadera las sitúa a ±0,5 s. A 60 fps y
 * sin ruido, para que la verdad sea exacta.
 */
class RelativeVelocityTest {
    private fun fmt(x: Double) = String.format(Locale.ROOT, "%.3f", x)

    private fun deadlift(conSeconds: List<Double>, failedLast: Boolean = false): List<SynthSegment> {
        val reps = conSeconds.map { SynthSegment.Rep(1.5, 0.5, it, 0.5, 1.0) }
        val failed = if (failedLast) listOf(SynthSegment.Rep(1.5, 0.5, 3.0, 0.5, 1.0, null, SynthRepKind.FAILED)) else emptyList()
        return listOf<SynthSegment>(SynthSegment.Idle(2.0)) + reps + failed + SynthSegment.Idle(2.0)
    }

    @Test
    fun theMeanConcentricVelocityIsTheDisplacementOverTheTime() {
        val fails = RepTestSupport.Failures()
        val r = MetricsTestSupport.gen(SynthMovement.DEADLIFT, deadlift(List(5) { 1.0 }), fps = 60.0)
        val s = MetricsTestSupport.scene(r, RepTestSupport.profile(AnalysisProfileId.DEADLIFT))!!
        val velocity = RelativeVelocity.compute(s.ctx)
        val bodyHeightFh = s.ctx.bodyHeightFh!!
        println("[velocidad] peso muerto, CON de 1,0 s a 60 fps: ${MetricsTestSupport.describe(s)} unidad ${velocity.unit} | MCV ${velocity.reps.map { fmt(it.mcv) }}")
        fails.check(s.run.seg.fullCount == 5) { "conteo: ${MetricsTestSupport.describe(s)}" }
        fails.check(velocity.available && velocity.unit == VelocityUnit.BODY_HEIGHTS_PER_S) { "unidad ${velocity.unit}" }
        for ((i, truth) in r.reps.withIndex()) {
            // El desplazamiento de la mano entre la salida del suelo y el bloqueo, en la traza limpia, en alturas corporales.
            val displacementFh = abs(MetricsTestSupport.handCenterY(r.cleanTrack, truth.turnStartUs) - MetricsTestSupport.handCenterY(r.cleanTrack, truth.departUs))
            val expected = displacementFh / bodyHeightFh / ((truth.turnStartUs - truth.departUs) / 1e6)
            val got = velocity.reps[i].mcv
            println("[velocidad]   repetición $i: esperada ${fmt(expected)} BH/s, medida ${fmt(got)}")
            fails.check(!got.isNaN() && abs(got - expected) <= 0.03 * expected) { "repetición $i: MCV $got BH/s (esperada $expected ± 3 %)" }
        }
        fails.assertNone()
    }

    @Test
    fun aConcentricThatSlowsFromOneToOneAndAHalfSecondsLosesAThirdOfTheVelocity() {
        val fails = RepTestSupport.Failures()
        val seconds = listOf(1.0, 1.1, 1.2, 1.3, 1.4, 1.5)
        val r = MetricsTestSupport.gen(SynthMovement.DEADLIFT, deadlift(seconds), fps = 60.0)
        val s = MetricsTestSupport.scene(r, RepTestSupport.profile(AnalysisProfileId.DEADLIFT))!!
        val velocity = RelativeVelocity.compute(s.ctx)
        println(
            "[velocidad] CON de 1,0 a 1,5 s: ${MetricsTestSupport.describe(s)} | MCV ${velocity.reps.map { fmt(it.mcv) }} | " +
                "primera ${velocity.first} mejor ${velocity.best} última ${velocity.last} pérdida ${velocity.lossPct} %",
        )
        fails.check(s.run.seg.fullCount == 6) { "conteo: ${MetricsTestSupport.describe(s)}" }
        val loss = velocity.lossPct
        // VL = (1/1,0 − 1/1,5)/(1/1,0) = 33,3 %.
        fails.check(loss != null && abs(loss - 100.0 / 3.0) <= 3.0) { "pérdida de velocidad $loss % (esperada 33,3 ± 3)" }
        val first = velocity.first
        val best = velocity.best
        val last = velocity.last
        fails.check(first != null && best != null && last != null && best >= first - 1e-9 && best >= last) { "primera $first, mejor $best, última $last" }
        fails.check(first != null && last != null && last < first) { "la última ($last) debería ser más lenta que la primera ($first)" }
        fails.assertNone()
    }

    @Test
    fun failedAndPartialRepsDoNotCountForTheLossAndNeedThreeFullReps() {
        val fails = RepTestSupport.Failures()
        // Cuatro completas cada vez más lentas y una fallida al final: la fallida no tiene MCV ni cuenta para la pérdida.
        val r = MetricsTestSupport.gen(SynthMovement.DEADLIFT, deadlift(listOf(1.0, 1.0, 1.2, 1.4), failedLast = true), fps = 60.0)
        val s = MetricsTestSupport.scene(r, RepTestSupport.profile(AnalysisProfileId.DEADLIFT))!!
        val velocity = RelativeVelocity.compute(s.ctx)
        println("[velocidad] con una fallida: ${MetricsTestSupport.describe(s)} | MCV ${velocity.reps.map { fmt(it.mcv) }} | pérdida ${velocity.lossPct} %")
        fails.check(s.run.seg.fullCount == 4 && s.run.seg.failedCount == 1) { "conteo: ${MetricsTestSupport.describe(s)}" }
        val failedIndex = s.ctx.cycles.indexOfFirst { it.kind == RepKind.FAILED }
        fails.check(failedIndex >= 0 && velocity.reps[failedIndex].mcv.isNaN()) { "la fallida no debería tener MCV" }
        val loss = velocity.lossPct
        // VL = (1/1,0 − 1/1,4)/(1/1,0) = 28,6 %.
        fails.check(loss != null && abs(loss - 100.0 * (1.0 - 1.0 / 1.4)) <= 4.0) { "pérdida de velocidad $loss % (esperada 28,6 ± 4)" }

        // Con solo dos completas no hay pérdida (hacen falta 3), pero sí MCV.
        val two = MetricsTestSupport.scene(
            MetricsTestSupport.gen(SynthMovement.DEADLIFT, deadlift(listOf(1.0, 1.4)), fps = 60.0),
            RepTestSupport.profile(AnalysisProfileId.DEADLIFT),
        )!!
        val v2 = RelativeVelocity.compute(two.ctx)
        fails.check(two.run.seg.fullCount == 2) { "conteo de dos: ${MetricsTestSupport.describe(two)}" }
        fails.check(v2.available && v2.lossPct == null && v2.best != null) { "con dos FULL no hay pérdida: ${v2.lossPct}" }
        fails.assertNone()
    }

    @Test
    fun thePeakVelocityNeedsFiftyFramesPerSecond() {
        val fails = RepTestSupport.Failures()
        val profile = RepTestSupport.profile(AnalysisProfileId.DEADLIFT)
        val fast = MetricsTestSupport.scene(MetricsTestSupport.gen(SynthMovement.DEADLIFT, deadlift(List(5) { 1.0 }), fps = 60.0), profile)!!
        val vf = RelativeVelocity.compute(fast.ctx)
        // El pico de un tramo de jerk mínimo es 1,875 veces su velocidad media.
        val mean = vf.best
        val peak = vf.peakBest
        println("[velocidad] pico a 60 fps: media $mean, pico $peak (esperado ≈ ${mean?.times(1.875)})")
        fails.check(vf.peakAvailable) { "a 60 fps debería haber pico" }
        fails.check(mean != null && peak != null && abs(peak - 1.875 * mean) <= 0.1 * 1.875 * mean) { "pico $peak frente a 1,875 × $mean" }

        val slow = MetricsTestSupport.scene(MetricsTestSupport.gen(SynthMovement.DEADLIFT, deadlift(List(5) { 1.0 }), fps = 30.0), profile)!!
        val vs = RelativeVelocity.compute(slow.ctx)
        fails.check(vs.available && !vs.peakAvailable && vs.peakBest == null && vs.reps.all { it.peak.isNaN() }) { "a 30 fps no debería haber pico: ${vs.peakBest}" }
        fails.assertNone()
    }

    @Test
    fun belowTwentyFpsThereIsNoVelocity() {
        val r = MetricsTestSupport.gen(SynthMovement.SQUAT, MetricsTestSupport.script(List(5) { 1.0 }, eccS = 2.0, p1S = 1.0, conS = 1.0, p2S = 0.5), fps = 15.0)
        val s = MetricsTestSupport.scene(r, RepTestSupport.profile(AnalysisProfileId.SQUAT))!!
        val velocity = RelativeVelocity.compute(s.ctx)
        println("[velocidad] a 15 fps: ${MetricsTestSupport.describe(s)} | disponible ${velocity.available}")
        assertTrue("a 15 fps no hay fases ni velocidad", !velocity.available && velocity.unit == null && velocity.best == null)
    }
}

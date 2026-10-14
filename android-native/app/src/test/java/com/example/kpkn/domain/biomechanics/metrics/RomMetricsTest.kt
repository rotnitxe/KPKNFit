package com.example.kpkn.domain.biomechanics.metrics

import com.example.kpkn.domain.biomechanics.kinematics.Side
import com.example.kpkn.domain.biomechanics.kinematics.ViewKind
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfiles
import com.example.kpkn.domain.biomechanics.reps.RepTestSupport
import com.example.kpkn.domain.biomechanics.testing.SynthCamera
import com.example.kpkn.domain.biomechanics.testing.SynthMovement
import com.example.kpkn.domain.biomechanics.testing.SynthSegment
import com.example.kpkn.domain.biomechanics.testing.SynthTestSupport
import com.example.kpkn.domain.biomechanics.testing.SynthTestSupport.repScript
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import kotlin.math.abs

/** ROM por repetición y por serie (§7, «ROM»): con el generador, sin ruido, para que la verdad sea exacta. */
class RomMetricsTest {
    private fun fmt(x: Double) = String.format(Locale.ROOT, "%.1f", x)

    /** Amplitud del generador con la que el ROM de rodilla de la sentadilla es [romDeg]. */
    private fun squatAmplitudeForRom(romDeg: Double): Double {
        val rest = MetricsTestSupport.primaryAngleDeg(SynthMovement.SQUAT, 0.0)
        return MetricsTestSupport.solve(0.3, 1.5, rest + romDeg) { MetricsTestSupport.primaryAngleDeg(SynthMovement.SQUAT, it) }
    }

    @Test
    fun theKneeRomOfASideSquatIsTheScriptedOne() {
        val fails = RepTestSupport.Failures()
        val amplitude = squatAmplitudeForRom(100.0)
        val r = MetricsTestSupport.gen(
            SynthMovement.SQUAT,
            repScript(5, 2.0, 0.5, 1.5, 0.5, amplitude = amplitude, lead = 2.0, tail = 2.0),
        )
        val s = MetricsTestSupport.scene(r, RepTestSupport.profile(AnalysisProfileId.SQUAT))!!
        val rom = RomMetrics.compute(s.ctx)
        println("[rom] sentadilla de lado: ${MetricsTestSupport.describe(s)} | verdad ${r.reps.map { fmt(it.romDeg) }} | medido ${rom.perRepDeg.map { fmt(it) }} | media ${rom.meanDeg} DE ${rom.sdDeg} CV ${rom.cvPct}")
        fails.check(s.run.seg.fullCount == 5) { "conteo: ${MetricsTestSupport.describe(s)}" }
        fails.check(rom.available) { "el ROM debería estar disponible de lado" }
        for ((i, truth) in r.reps.withIndex()) {
            fails.check(abs(truth.romDeg - 100.0) <= 0.5) { "la verdad de la repetición $i es ${truth.romDeg}°, no 100°" }
            val got = rom.perRepDeg.getOrElse(i) { Double.NaN }
            fails.check(abs(got - 100.0) <= 3.0) { "repetición $i: ROM de rodilla $got° (esperado 100 ± 3)" }
        }
        val mean = rom.meanDeg
        val sd = rom.sdDeg
        val cv = rom.cvPct
        val trend = rom.trendDeg
        fails.check(mean != null && abs(mean - 100.0) <= 3.0) { "ROM medio $mean" }
        fails.check(sd != null && sd < 2.0) { "DE del ROM $sd: las cinco repeticiones son iguales" }
        fails.check(cv != null && cv < 2.0) { "CV del ROM $cv %" }
        // Con 5 FULL iguales no hay tendencia que valga.
        fails.check(trend != null && abs(trend) <= 2.0) { "tendencia de cinco repeticiones iguales: $trend" }
        fails.assertNone()
    }

    @Test
    fun aFrontViewHasNoRomButARaiseKeepsItInAnyView() {
        val fails = RepTestSupport.Failures()
        // Sentadilla de frente: se cuenta (FRONT está en countViews) pero no hay ROM (no está en metricViews).
        val front = MetricsTestSupport.gen(
            SynthMovement.SQUAT,
            repScript(5, 2.0, 0.5, 1.5, 0.5, lead = 2.0, tail = 2.0),
            camera = SynthCamera(yawDeg = 0.0),
        )
        val s = MetricsTestSupport.scene(front, RepTestSupport.profile(AnalysisProfileId.SQUAT))!!
        val rom = RomMetrics.compute(s.ctx)
        println("[rom] sentadilla de frente: vista ${s.ctx.view} ${MetricsTestSupport.describe(s)} | disponible ${rom.available}")
        fails.check(s.ctx.view == ViewKind.FRONT) { "la vista es ${s.ctx.view}, no FRONT" }
        fails.check(s.run.seg.fullCount == 5) { "conteo de frente: ${MetricsTestSupport.describe(s)}" }
        fails.check(!rom.available) { "de frente no debería haber ROM" }
        fails.check(rom.perRepDeg.all { it.isNaN() }) { "ROM por repetición de frente: ${rom.perRepDeg.toList()}" }
        fails.check(rom.meanDeg == null && rom.sdDeg == null && rom.trendDeg == null) { "estadísticos de ROM de frente" }

        // Los laterales: β no depende del giro, así que el ROM se da en cualquier vista, aunque el perfil no la liste.
        val camera = SynthTestSupport.LANDSCAPE_FAR.copy(yawDeg = 0.0, heightM = 1.4)
        val raise = MetricsTestSupport.gen(SynthMovement.LATERAL_RAISE, repScript(5, 1.5, 0.5, 1.5, 0.5, lead = 2.0, tail = 2.0), camera = camera)
        val noViews = AnalysisProfiles.of(AnalysisProfileId.RAISE).copy(metricViews = emptySet())
        val sr = MetricsTestSupport.scene(raise, noViews)!!
        val romRaise = RomMetrics.compute(sr.ctx)
        println("[rom] laterales de frente: ${MetricsTestSupport.describe(sr)} | verdad ${raise.reps.map { fmt(it.romDeg) }} | medido ${romRaise.perRepDeg.map { fmt(it) }}")
        fails.check(sr.run.seg.fullCount == 5) { "conteo de los laterales: ${MetricsTestSupport.describe(sr)}" }
        fails.check(romRaise.available) { "RAISE debería dar ROM en cualquier vista" }
        for ((i, truth) in raise.reps.withIndex()) {
            val got = romRaise.perRepDeg.getOrElse(i) { Double.NaN }
            fails.check(abs(got - truth.romDeg) <= 4.0) { "laterales, repetición $i: β medido $got°, verdad ${truth.romDeg}°" }
        }
        // Y un perfil de rodilla sí pierde el ROM en esa misma vista.
        val squatNoViews = AnalysisProfiles.of(AnalysisProfileId.SQUAT).copy(metricViews = emptySet())
        val sq = MetricsTestSupport.scene(MetricsTestSupport.gen(SynthMovement.SQUAT, repScript(5, 2.0, 0.5, 1.5, 0.5, lead = 2.0, tail = 2.0)), squatNoViews)!!
        fails.check(!RomMetrics.compute(sq.ctx).available) { "sin vistas de métrica, la rodilla no da ROM" }
        fails.assertNone()
    }

    @Test
    fun theTrendSeesTheLastTwoRepsTenDegreesShorter() {
        val fails = RepTestSupport.Failures()
        val full = squatAmplitudeForRom(100.0)
        val short = squatAmplitudeForRom(90.0)
        val r = MetricsTestSupport.gen(
            SynthMovement.SQUAT,
            MetricsTestSupport.script(listOf(full, full, full, full, short, short)),
        )
        val s = MetricsTestSupport.scene(r, RepTestSupport.profile(AnalysisProfileId.SQUAT))!!
        val rom = RomMetrics.compute(s.ctx)
        println("[rom] tendencia: ${MetricsTestSupport.describe(s)} | medido ${rom.perRepDeg.map { fmt(it) }} | Δ ${rom.trendDeg} pendiente ${rom.slopeDegPerRep} media ${rom.meanDeg} CV ${rom.cvPct}")
        fails.check(s.run.seg.fullCount == 6) { "conteo: ${MetricsTestSupport.describe(s)}" }
        val trend = rom.trendDeg
        fails.check(trend != null && abs(trend + 10.0) <= 2.0) { "tendencia $trend (esperada −10 ± 2)" }
        // Verdad: pendiente OLS de [100, 100, 100, 100, 90, 90] = −2,29 °/rep.
        val slope = rom.slopeDegPerRep
        fails.check(slope != null && abs(slope + 2.29) <= 0.5) { "pendiente $slope (esperada −2,29 ± 0,5)" }
        val mean = rom.meanDeg
        fails.check(mean != null && abs(mean - 96.7) <= 3.0) { "ROM medio $mean (esperado 96,7 ± 3)" }
        // Con menos de 4 FULL no hay tendencia.
        val three = MetricsTestSupport.scene(
            MetricsTestSupport.gen(SynthMovement.SQUAT, MetricsTestSupport.script(listOf(full, full, short))),
            RepTestSupport.profile(AnalysisProfileId.SQUAT),
        )!!
        val romThree = RomMetrics.compute(three.ctx)
        fails.check(three.run.seg.fullCount == 3) { "conteo de tres: ${MetricsTestSupport.describe(three)}" }
        fails.check(romThree.trendDeg == null && romThree.slopeDegPerRep == null) { "tendencia con tres FULL: ${romThree.trendDeg}" }
        fails.check(romThree.meanDeg != null) { "con tres FULL sí hay media" }
        fails.assertNone()
    }

    @Test
    fun theKneeOfALungeIsTheFrontOneWhicheverLegIsForward() {
        val fails = RepTestSupport.Failures()
        val profile = RepTestSupport.profile(AnalysisProfileId.LUNGE)
        for (front in listOf(Side.LEFT, Side.RIGHT)) {
            val script = listOf<SynthSegment>(SynthSegment.Idle(2.0)) +
                List(5) { SynthSegment.Rep(2.0, 0.5, 1.5, 0.5, 1.0, front) } + SynthSegment.Idle(2.0)
            val r = MetricsTestSupport.gen(SynthMovement.LUNGE, script)
            val s = MetricsTestSupport.scene(r, profile)!!
            val rom = RomMetrics.compute(s.ctx)
            println("[rom] zancada con la pierna $front delante: ${MetricsTestSupport.describe(s)} | verdad ${r.reps.map { fmt(it.romDeg) }} | medido ${rom.perRepDeg.map { fmt(it) }}")
            fails.check(s.run.seg.fullCount == 5) { "$front: conteo ${MetricsTestSupport.describe(s)}" }
            fails.check(rom.available) { "$front: el ROM no está disponible" }
            for ((i, truth) in r.reps.withIndex()) {
                val got = rom.perRepDeg.getOrElse(i) { Double.NaN }
                fails.check(abs(got - truth.romDeg) <= 4.0) { "$front, repetición $i: ROM de la rodilla delantera $got° (verdad ${truth.romDeg}°)" }
            }
        }
        fails.assertNone()
    }

    @Test
    fun theKneeRomIsTheSameInPortraitAndLandscape() {
        val amplitude = squatAmplitudeForRom(100.0)
        val script = repScript(5, 2.0, 0.5, 1.5, 0.5, amplitude = amplitude, lead = 2.0, tail = 2.0)
        val profile = RepTestSupport.profile(AnalysisProfileId.SQUAT)
        val portrait = MetricsTestSupport.scene(MetricsTestSupport.gen(SynthMovement.SQUAT, script), profile)!!
        val landscape = MetricsTestSupport.scene(
            MetricsTestSupport.gen(SynthMovement.SQUAT, script, camera = SynthCamera(yawDeg = 90.0, widthPx = 1280, heightPx = 720)),
            profile,
        )!!
        val a = RomMetrics.compute(portrait.ctx).meanDeg
        val b = RomMetrics.compute(landscape.ctx).meanDeg
        println("[rom] vertical frente a horizontal: $a / $b")
        assertNotNull(a)
        assertNotNull(b)
        assertTrue("ROM vertical $a y horizontal $b", abs(a!! - b!!) <= 2.0)
        assertTrue("ROM horizontal $b", abs(b - 100.0) <= 3.0)
    }
}

package com.example.kpkn.domain.biomechanics.metrics

import com.example.kpkn.domain.biomechanics.kinematics.ViewKind
import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.reps.RepTestSupport
import com.example.kpkn.domain.biomechanics.testing.SynthCamera
import com.example.kpkn.domain.biomechanics.testing.SynthMovement
import com.example.kpkn.domain.biomechanics.testing.SynthTestSupport.mapFrames
import org.junit.Test
import java.util.Locale
import kotlin.math.abs

/** Profundidad, criterios y bloqueos (§7, «Profundidad y criterios»): con el generador, sin ruido. */
class DepthRulesTest {
    private fun fmt(x: Double?) = if (x == null) "—" else String.format(Locale.ROOT, "%.1f", x)

    @Test
    fun squatDepthIsClassifiedByTheHipAgainstTheKnee() {
        val fails = RepTestSupport.Failures()
        val cases = listOf(10.0 to DepthClass.BELOW, 0.0 to DepthClass.PARALLEL, -10.0 to DepthClass.ABOVE)
        for ((target, expected) in cases) {
            val b = MetricsTestSupport.solve(0.5, 1.5, target) { MetricsTestSupport.squatDepthDeg(it) }
            val r = MetricsTestSupport.gen(SynthMovement.SQUAT, MetricsTestSupport.script(List(5) { b }))
            val s = MetricsTestSupport.scene(r, RepTestSupport.profile(AnalysisProfileId.SQUAT))!!
            val depth = DepthRules.compute(s.ctx)
            println("[profundidad] α=${fmt(target)}° (amplitud ${"%.3f".format(Locale.ROOT, b)}): ${MetricsTestSupport.describe(s)} | clases ${depth.reps.map { it.depthClass }} | α medido ${depth.reps.map { fmt(it.depthDeg) }}")
            fails.check(s.run.seg.fullCount == 5) { "α=$target: conteo ${MetricsTestSupport.describe(s)}" }
            fails.check(depth.depthAvailable) { "α=$target: la profundidad no está disponible" }
            for ((i, rep) in depth.reps.withIndex()) {
                fails.check(rep.depthClass == expected) { "α=$target, repetición $i: clase ${rep.depthClass} (esperada $expected), α ${rep.depthDeg}" }
                val got = rep.depthDeg
                fails.check(got != null && abs(got - target) <= 3.0) { "α=$target, repetición $i: α medido $got (esperado $target ± 3)" }
            }
        }
        fails.assertNone()
    }

    @Test
    fun squatDepthIsOnlyGivenInTheMetricViewsOfTheProfile() {
        val fails = RepTestSupport.Failures()
        // De frente no hay profundidad absoluta (SQUAT mide en SIDE).
        val front = MetricsTestSupport.gen(
            SynthMovement.SQUAT,
            MetricsTestSupport.script(List(5) { 1.0 }),
            camera = SynthCamera(yawDeg = 0.0),
        )
        val s = MetricsTestSupport.scene(front, RepTestSupport.profile(AnalysisProfileId.SQUAT))!!
        val depth = DepthRules.compute(s.ctx)
        fails.check(s.ctx.view == ViewKind.FRONT) { "la vista es ${s.ctx.view}" }
        fails.check(!depth.depthAvailable && depth.reps.all { it.depthClass == null && it.depthDeg == null }) { "de frente no debería haber profundidad" }
        fails.assertNone()
    }

    @Test
    fun theChinIsOverTheBarInFourOfFiveReps() {
        val fails = RepTestSupport.Failures()
        // La 4.ª se queda corta (amplitud 0,9); las demás sacan la barbilla de la barra (1,25).
        val amplitudes = listOf(1.25, 1.25, 1.25, 0.9, 1.25)
        val base = MetricsTestSupport.gen(SynthMovement.PULL_UP, MetricsTestSupport.script(amplitudes, eccS = 1.5, p1S = 0.5, conS = 1.5, p2S = 0.5))
        // Los nudillos rodean la barra: índice y meñique van a la altura de la muñeca (el generador los pone en la punta de la mano).
        val knuckles = listOf(
            PoseLandmark.LEFT_INDEX to PoseLandmark.LEFT_WRIST, PoseLandmark.LEFT_PINKY to PoseLandmark.LEFT_WRIST,
            PoseLandmark.RIGHT_INDEX to PoseLandmark.RIGHT_WRIST, PoseLandmark.RIGHT_PINKY to PoseLandmark.RIGHT_WRIST,
        )
        val track = base.track.mapFrames { _, image, _ ->
            for ((knuckle, wrist) in knuckles) {
                val w = image[wrist] ?: continue
                image[knuckle] = w
            }
        }
        val r = MetricsTestSupport.withTrack(base, track)
        val s = MetricsTestSupport.scene(r, RepTestSupport.profile(AnalysisProfileId.PULL_VERTICAL))!!
        val depth = DepthRules.compute(s.ctx)
        val chin = depth.reps.map { it.criteria[DepthCriteria.CHIN_OVER_BAR] }
        val hang = depth.reps.map { it.criteria[DepthCriteria.FULL_HANG] }
        println("[profundidad] dominadas: ${MetricsTestSupport.describe(s)} | mentón sobre la barra $chin | colgado del todo $hang")
        fails.check(s.run.seg.fullCount == 5) { "conteo de dominadas: ${MetricsTestSupport.describe(s)}" }
        fails.check(depth.depthAvailable) { "la regla de la dominada no está disponible" }
        fails.check(chin == listOf(true, true, true, false, true)) { "chinOverBar por repetición: $chin (esperado [true, true, true, false, true])" }
        fails.check(hang.all { it == true }) { "fullHang: $hang (el brazo cuelga estirado)" }
        fails.assertNone()
    }

    @Test
    fun theDeadliftLockoutNeedsTheHipAt165AndTheKneeAt160() {
        val fails = RepTestSupport.Failures()
        val profile = RepTestSupport.profile(AnalysisProfileId.DEADLIFT)
        // Bloqueado: se termina de pie (progreso 1): θH ≈ 178° y θK ≈ 176°.
        val locked = MetricsTestSupport.scene(MetricsTestSupport.gen(SynthMovement.DEADLIFT, MetricsTestSupport.script(List(5) { 1.0 })), profile)!!
        val lockedDepth = DepthRules.compute(locked.ctx)
        println("[profundidad] peso muerto bloqueado: ${MetricsTestSupport.describe(locked)} | bloqueo ${lockedDepth.reps.map { it.criteria[DepthCriteria.LOCKOUT] }}")
        fails.check(locked.run.seg.fullCount == 5) { "conteo del peso muerto bloqueado: ${MetricsTestSupport.describe(locked)}" }
        fails.check(lockedDepth.lockoutAvailable) { "el bloqueo no está disponible" }
        fails.check(lockedDepth.reps.all { it.criteria[DepthCriteria.LOCKOUT] == true }) { "bloqueo de pie: ${lockedDepth.reps.map { it.criteria }}" }

        // Sin terminar: la cadera se queda a 150°.
        val b = MetricsTestSupport.solve(0.3, 1.0, 150.0) { MetricsTestSupport.hipAngleDeg(SynthMovement.DEADLIFT, it) }
        val short = MetricsTestSupport.scene(MetricsTestSupport.gen(SynthMovement.DEADLIFT, MetricsTestSupport.script(List(5) { b })), profile)!!
        val shortDepth = DepthRules.compute(short.ctx)
        println("[profundidad] peso muerto con la cadera a 150° (progreso ${"%.3f".format(Locale.ROOT, b)}): ${MetricsTestSupport.describe(short)} | bloqueo ${shortDepth.reps.map { it.criteria[DepthCriteria.LOCKOUT] }}")
        fails.check(short.run.seg.fullCount == 5) { "conteo del peso muerto a 150°: ${MetricsTestSupport.describe(short)}" }
        fails.check(shortDepth.reps.all { it.criteria[DepthCriteria.LOCKOUT] == false }) { "bloqueo con la cadera a 150°: ${shortDepth.reps.map { it.criteria }}" }
        fails.assertNone()
    }

    @Test
    fun theHipThrustAndThePressLockoutsAreDecidedAtTheEndOfTheConcentric() {
        val fails = RepTestSupport.Failures()
        // Hip thrust: θH ≥ 165° arriba. Completo termina con la cadera extendida; el otro se queda a 150°.
        val thrust = RepTestSupport.profile(AnalysisProfileId.HIP_THRUST)
        val bThrust = MetricsTestSupport.solve(0.2, 1.0, 150.0) { MetricsTestSupport.hipAngleDeg(SynthMovement.HIP_THRUST, it) }
        for ((label, b, expected) in listOf(Triple("completo", 1.0, true), Triple("a 150°", bThrust, false))) {
            val r = MetricsTestSupport.gen(SynthMovement.HIP_THRUST, MetricsTestSupport.script(List(5) { b }))
            val s = MetricsTestSupport.scene(r, thrust)!!
            val depth = DepthRules.compute(s.ctx)
            val lockout = depth.reps.map { it.criteria[DepthCriteria.LOCKOUT] }
            println("[profundidad] hip thrust $label (progreso ${"%.3f".format(Locale.ROOT, b)}): vista ${s.ctx.view} ${MetricsTestSupport.describe(s)} | bloqueo $lockout")
            fails.check(s.run.seg.fullCount == 5) { "hip thrust $label: conteo ${MetricsTestSupport.describe(s)}" }
            fails.check(depth.lockoutAvailable && lockout.all { it == expected }) { "hip thrust $label: bloqueo $lockout (esperado $expected)" }
        }
        // Press militar: θE ≥ 160° y la muñeca por encima de la nariz.
        val ohp = RepTestSupport.profile(AnalysisProfileId.OHP)
        val bOhp = MetricsTestSupport.solve(0.2, 1.0, 140.0) { MetricsTestSupport.elbowAngleDeg(SynthMovement.OHP, it) }
        for ((label, b, expected) in listOf(Triple("completo", 1.0, true), Triple("con el codo a 140°", bOhp, false))) {
            val r = MetricsTestSupport.gen(SynthMovement.OHP, MetricsTestSupport.script(List(5) { b }))
            val s = MetricsTestSupport.scene(r, ohp)!!
            val depth = DepthRules.compute(s.ctx)
            val lockout = depth.reps.map { it.criteria[DepthCriteria.LOCKOUT] }
            println("[profundidad] press militar $label (progreso ${"%.3f".format(Locale.ROOT, b)}): ${MetricsTestSupport.describe(s)} | bloqueo $lockout")
            fails.check(s.run.seg.fullCount == 5) { "press militar $label: conteo ${MetricsTestSupport.describe(s)}" }
            fails.check(depth.lockoutAvailable && lockout.all { it == expected }) { "press militar $label: bloqueo $lockout (esperado $expected)" }
        }
        fails.assertNone()
    }

    @Test
    fun aPushUpIsFullWhenTheElbowGoesDownTo90() {
        val fails = RepTestSupport.Failures()
        val profile = RepTestSupport.profile(AnalysisProfileId.PUSH_UP)
        // 75° es completa; 100° queda en la zona «≈» (entre 90° y 110°) y no cuenta como completa. Más cortas que eso, el hombro
        // baja menos de 0,05 BH y el conteo ya no ve repeticiones.
        for ((target, expected) in listOf(75.0 to true, 100.0 to false)) {
            // θE baja con el progreso: se resuelve con −θE, que crece.
            val b = MetricsTestSupport.solve(0.05, 1.7, -target) { -MetricsTestSupport.elbowAngleDeg(SynthMovement.PUSH_UP, it) }
            val r = MetricsTestSupport.gen(SynthMovement.PUSH_UP, MetricsTestSupport.script(List(5) { b }))
            val s = MetricsTestSupport.scene(r, profile)!!
            val depth = DepthRules.compute(s.ctx)
            println("[profundidad] flexiones con θE mín ${fmt(target)}° (progreso ${"%.3f".format(Locale.ROOT, b)}): ${MetricsTestSupport.describe(s)} | θE ${depth.reps.map { fmt(it.depthDeg) }} | completa ${depth.reps.map { it.criteria[DepthCriteria.PUSH_UP_FULL] }}")
            fails.check(s.run.seg.fullCount == 5) { "θE=$target: conteo ${MetricsTestSupport.describe(s)}" }
            for ((i, rep) in depth.reps.withIndex()) {
                fails.check(rep.criteria[DepthCriteria.PUSH_UP_FULL] == expected) { "θE=$target, repetición $i: pushUpFull ${rep.criteria[DepthCriteria.PUSH_UP_FULL]} (esperado $expected), θE ${rep.depthDeg}" }
                val got = rep.depthDeg
                fails.check(got != null && abs(got - target) <= 5.0) { "θE=$target, repetición $i: θE medido $got" }
            }
        }
        fails.assertNone()
    }

    @Test
    fun aBenchRepWhoseBottomIsTenPercentOfTheRomHigherIsShallower() {
        val fails = RepTestSupport.Failures()
        val r = MetricsTestSupport.gen(SynthMovement.BENCH, MetricsTestSupport.script(listOf(1.0, 1.0, 1.0, 1.0, 0.85), eccS = 1.5, p1S = 0.5, conS = 1.5, p2S = 0.5))
        val s = MetricsTestSupport.scene(r, RepTestSupport.profile(AnalysisProfileId.BENCH, plate = true), RepTestSupport.plateFromWrist(r))!!
        val depth = DepthRules.compute(s.ctx)
        val shallower = depth.reps.map { it.criteria[DepthCriteria.SHALLOWER] }
        println("[profundidad] banca: ${MetricsTestSupport.describe(s)} fuente=${s.run.series.source} vista=${s.ctx.view} | más corta $shallower")
        fails.check(s.run.seg.fullCount == 5) { "conteo de la banca: ${MetricsTestSupport.describe(s)}" }
        fails.check(depth.depthAvailable) { "la regla de la banca no está disponible" }
        fails.check(shallower == listOf(false, false, false, false, true)) { "shallower por repetición: $shallower" }

        // Con dos repeticiones no hay mediana de la que fiarse.
        val two = MetricsTestSupport.gen(SynthMovement.BENCH, MetricsTestSupport.script(listOf(1.0, 0.85), eccS = 1.5, p1S = 0.5, conS = 1.5, p2S = 0.5))
        val st = MetricsTestSupport.scene(two, RepTestSupport.profile(AnalysisProfileId.BENCH, plate = true), RepTestSupport.plateFromWrist(two))!!
        fails.check(!DepthRules.compute(st.ctx).depthAvailable) { "con dos FULL no debería haber 'más corta'" }
        fails.assertNone()
    }
}

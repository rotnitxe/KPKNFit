package com.example.kpkn.domain.biomechanics.metrics

import com.example.kpkn.domain.biomechanics.kinematics.ViewKind
import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.profiles.PathRule
import com.example.kpkn.domain.biomechanics.reps.CountSeriesBuilder
import com.example.kpkn.domain.biomechanics.reps.RepTestSupport
import com.example.kpkn.domain.biomechanics.testing.SynthMovement
import com.example.kpkn.domain.biomechanics.testing.SynthTestSupport.mapFrames
import org.junit.Test
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** Trayectoria de la barra (§7, «Trayectoria»): con el generador, el disco sintético y defectos inyectados en él. */
class PathMetricsTest {
    private fun fmt(x: Double) = String.format(Locale.ROOT, "%.3f", x)

    @Test
    fun anInjectedLateralDriftKeepsItsSignAndSize() {
        val fails = RepTestSupport.Failures()
        val r = MetricsTestSupport.gen(SynthMovement.DEADLIFT, MetricsTestSupport.script(List(5) { 1.0 }))
        val profile = RepTestSupport.profile(AnalysisProfileId.DEADLIFT, plate = true)
        val bar = RepTestSupport.plateFromWrist(r)
        val base = MetricsTestSupport.scene(r, profile, bar)!!
        val basePath = PathMetrics.compute(base.ctx)

        // De lado, el atleta mira a la izquierda de la imagen (f = −1): −30 px es hacia delante.
        val shiftPx = -30f
        val deviated = 2
        val shifted = MetricsTestSupport.scene(r, profile, MetricsTestSupport.withLateralDrift(bar, r.reps[deviated], shiftPx))!!
        val path = PathMetrics.compute(shifted.ctx)
        val bodyHeightFh = base.ctx.bodyHeightFh!!
        val expectedBh = (-shiftPx / base.run.prepared.track.frameHeight) / bodyHeightFh
        println(
            "[trayectoria] deriva: fuente=${shifted.run.series.source} f=${shifted.ctx.facing} ${MetricsTestSupport.describe(shifted)} | " +
                "deriva base ${basePath.reps.map { fmt(it.driftBh) }} | inyectada ${path.reps.map { fmt(it.driftBh) }} | esperado +${fmt(expectedBh)} BH en la repetición $deviated",
        )
        fails.check(base.run.seg.fullCount == 5 && shifted.run.seg.fullCount == 5) { "conteo: ${MetricsTestSupport.describe(base)} / ${MetricsTestSupport.describe(shifted)}" }
        fails.check(shifted.run.series.source == CountSeriesBuilder.SOURCE_PLATE) { "la fuente es ${shifted.run.series.source}, no el disco" }
        fails.check(basePath.available && path.available) { "la trayectoria no está disponible" }
        fails.check(shifted.ctx.facing == -1) { "f = ${shifted.ctx.facing}: el atleta mira a la izquierda de la imagen" }
        for (i in 0 until 5) {
            val delta = path.reps[i].driftBh - basePath.reps[i].driftBh
            if (i == deviated) {
                fails.check(delta > 0.0) { "la deriva inyectada hacia delante debería salir positiva: Δ=$delta" }
                fails.check(abs(delta - expectedBh) <= 0.3 * expectedBh) { "deriva inyectada $delta BH (esperada $expectedBh ± 30 %)" }
            } else {
                fails.check(abs(delta) <= 0.01) { "la repetición $i no debería cambiar: Δ=$delta BH" }
            }
        }
        // Y en %ROM_v, que es lo que va al informe, sale con el mismo signo.
        val romV = path.reps[deviated].romVBh
        fails.check(romV > 0.0 && (path.reps[deviated].driftBh / romV) > (basePath.reps[deviated].driftBh / romV)) { "la deriva en %ROM_v debería crecer" }
        fails.assertNone()
    }

    @Test
    fun theMidfootReferenceGivesASmallDeviationForACleanSquat() {
        val fails = RepTestSupport.Failures()
        val r = MetricsTestSupport.gen(SynthMovement.SQUAT, MetricsTestSupport.script(List(5) { 1.0 }))
        val s = MetricsTestSupport.scene(r, RepTestSupport.profile(AnalysisProfileId.SQUAT))!!
        val path = PathMetrics.compute(s.ctx)
        println(
            "[trayectoria] sentadilla limpia: ${MetricsTestSupport.describe(s)} | desvío medio ${path.reps.map { fmt(it.offsetMeanBh) }} BH, ROM_v ${path.reps.map { fmt(it.romVBh) }} BH, " +
                "excursión/ROM_v ${path.reps.map { fmt(it.excursionFraction) }} | mediana del desvío ${path.barDeviationPctRom} %ROM",
        )
        fails.check(s.run.seg.fullCount == 5) { "conteo: ${MetricsTestSupport.describe(s)}" }
        fails.check(path.available && path.rule == PathRule.MIDFOOT_VERTICAL) { "la trayectoria de la sentadilla no está disponible" }
        val deviation = path.barDeviationPctRom
        // El generador pone la barra sobre el medio pie abajo y sobre la cadera (2,7 cm detrás) arriba: un desvío de pocos %.
        fails.check(deviation != null && deviation in 0.0..10.0) { "desvío de la barra $deviation %ROM (esperado entre 0 y 10)" }
        for ((i, rep) in path.reps.withIndex()) {
            fails.check(rep.romVBh in 0.15..0.6) { "repetición $i: ROM_v ${rep.romVBh} BH" }
            fails.check(rep.excursionFraction in 0.0..0.35) { "repetición $i: excursión/ROM_v ${rep.excursionFraction}" }
        }
        fails.assertNone()
    }

    @Test
    fun theBenchRepWithTheBumpHasTheLargestDeviation() {
        val fails = RepTestSupport.Failures()
        val r = MetricsTestSupport.gen(SynthMovement.BENCH, MetricsTestSupport.script(List(5) { 1.0 }, eccS = 1.5, p1S = 0.5, conS = 1.5, p2S = 0.5))
        val profile = RepTestSupport.profile(AnalysisProfileId.BENCH, plate = true)
        val bar = RepTestSupport.plateFromWrist(r)
        val deviated = 2
        val bumped = MetricsTestSupport.withPathBump(bar, r.reps[deviated], 12f)
        val s = MetricsTestSupport.scene(r, profile, bumped)!!
        val path = PathMetrics.compute(s.ctx)
        val pds = path.reps.map { it.deviationRmsBh }
        println(
            "[trayectoria] banca: fuente=${s.run.series.source} vista=${s.ctx.view} ${MetricsTestSupport.describe(s)} | PD ${pds.map { fmt(it) }} BH | " +
                "ROM_v mediano ${path.medianRomVBh} | consistencia ${path.barDeviationPctRom} %ROM | DE del contacto ${path.contactSdBh} BH",
        )
        fails.check(s.run.seg.fullCount == 5) { "conteo: ${MetricsTestSupport.describe(s)}" }
        fails.check(s.ctx.view == ViewKind.LYING) { "la vista de la banca es ${s.ctx.view}" }
        fails.check(path.available && path.rule == PathRule.CONSISTENCY) { "la consistencia no está disponible" }
        val largest = pds.indices.maxByOrNull { if (pds[it].isNaN()) Double.NEGATIVE_INFINITY else pds[it] }
        fails.check(largest == deviated) { "la repetición con más PD es la $largest, no la $deviated" }
        val others = pds.filterIndexed { i, v -> i != deviated && !v.isNaN() }
        val romV = path.medianRomVBh ?: Double.NaN
        fails.check(others.size == 4 && others.all { it < 0.01 * romV }) { "las repeticiones normales deberían tener PD ≈ 0: $others (ROM_v $romV)" }
        // 12 px de combadura en un recorrido de ~87 px: la PD de esa repetición es ~10 % del ROM_v.
        fails.check(pds[deviated] / romV in 0.04..0.2) { "PD de la repetición desviada ${pds[deviated] / romV} del ROM_v (esperado 0,04 a 0,2)" }
        // La combadura vale 0 en el fondo, así que el punto de contacto no cambia.
        val contact = path.contactSdBh
        fails.check(contact != null && contact < 0.01) { "DE del punto de contacto $contact BH (esperada ≈ 0)" }
        // La mediana de PD es la de una repetición normal: la consistencia es buena.
        val consistency = path.barDeviationPctRom
        fails.check(consistency != null && consistency < 2.0) { "consistencia (mediana de PD) $consistency %ROM" }
        fails.assertNone()
    }

    @Test
    fun withoutAStillFootThereIsNoMidfootReference() {
        val fails = RepTestSupport.Failures()
        val r = MetricsTestSupport.gen(SynthMovement.SQUAT, MetricsTestSupport.script(List(5) { 1.0 }))
        // Los pies se arrastran de un lado a otro durante toda la serie (±0,02 FH, a 0,3 Hz): no hay medio pie al que referir.
        val feet = listOf(PoseLandmark.LEFT_HEEL, PoseLandmark.LEFT_FOOT_INDEX, PoseLandmark.RIGHT_HEEL, PoseLandmark.RIGHT_FOOT_INDEX)
        val moved = r.track.mapFrames { i, image, _ ->
            val dx = (0.036 * sin(2.0 * PI * 0.3 * i / 30.0)).toFloat()
            for (lm in feet) {
                val p = image[lm] ?: continue
                image[lm] = p.copy(x = p.x + dx)
            }
        }
        val still = MetricsTestSupport.scene(r, RepTestSupport.profile(AnalysisProfileId.SQUAT))!!
        val walking = MetricsTestSupport.scene(MetricsTestSupport.withTrack(r, moved), RepTestSupport.profile(AnalysisProfileId.SQUAT))!!
        val stillPath = PathMetrics.compute(still.ctx)
        val walkingPath = PathMetrics.compute(walking.ctx)
        println("[trayectoria] pies quietos: ${stillPath.available}; pies que se mueven: ${walkingPath.available} (${MetricsTestSupport.describe(walking)})")
        fails.check(still.run.seg.fullCount == 5 && walking.run.seg.fullCount == 5) { "conteo: ${MetricsTestSupport.describe(still)} / ${MetricsTestSupport.describe(walking)}" }
        fails.check(stillPath.available) { "con los pies quietos debería haber trayectoria" }
        fails.check(!walkingPath.available && walkingPath.barDeviationPctRom == null) { "con los pies en movimiento no debería haber trayectoria" }
        fails.assertNone()
    }
}

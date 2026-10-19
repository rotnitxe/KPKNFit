package com.example.kpkn.domain.biomechanics.vision

import com.example.kpkn.domain.biomechanics.bar.BarTrack
import com.example.kpkn.domain.biomechanics.metrics.AnalysisFlag
import com.example.kpkn.domain.biomechanics.metrics.MetricKey
import com.example.kpkn.domain.biomechanics.metrics.MetricsTestSupport
import com.example.kpkn.domain.biomechanics.metrics.ScaleSource
import com.example.kpkn.domain.biomechanics.metrics.TechniqueAnalysisEngine
import com.example.kpkn.domain.biomechanics.metrics.TechniqueAnalysisInput
import com.example.kpkn.domain.biomechanics.metrics.VelocityUnit
import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.reps.RepTestSupport
import com.example.kpkn.domain.biomechanics.testing.SynthCamera
import com.example.kpkn.domain.biomechanics.testing.SynthMovement
import com.example.kpkn.domain.biomechanics.vbt.VbtTestSupport
import org.junit.Test
import java.util.Locale
import kotlin.math.abs

/**
 * De los fotogramas al metro: un peso muerto sintético con el disco DIBUJADO (no puesto como muestras) pasa por el rastreador, y
 * las muestras que salen entran en el motor, que calibra con el diámetro real (450 mm) y calcula la MCV con `CalibratedVelocity`.
 * Se compara con la verdad del generador (recorrido 3D de la muñeca entre la duración de la concéntrica).
 */
class PlateTrackerVelocityTest {
    private fun fmt(x: Double?) = if (x == null) "—" else String.format(Locale.ROOT, "%.4f", x)

    private class Case(val fps: Double, val camera: SynthCamera, val seed: Long, val axisRatio: Double = 1.0)

    @Test
    fun theMeanConcentricVelocityFromTheTrackedPlateIsWithinTheToleranceAt60And30Fps() {
        val fails = RepTestSupport.Failures()
        val conS = 1.0
        val trueLiftM = MetricsTestSupport.joints(SynthMovement.DEADLIFT, 1.0)[PoseLandmark.LEFT_WRIST].y -
            MetricsTestSupport.joints(SynthMovement.DEADLIFT, 0.0)[PoseLandmark.LEFT_WRIST].y
        val expected = trueLiftM / conS
        val cases = listOf(
            Case(60.0, SynthCamera(distanceM = 2.0), 1L),
            Case(30.0, SynthCamera(distanceM = 2.0), 2L),
            Case(60.0, SynthCamera(), 3L),
            Case(30.0, SynthCamera(), 4L),
            // Un disco visto de ≈ 26° (a/b = 0,90): r = √(a·b) y a/b del rastreador dan la escala vertical R/b del disco.
            Case(60.0, SynthCamera(distanceM = 2.0), 5L, 0.9),
        )
        for (case in cases) {
            val tolerance = if (case.fps >= 50.0) 0.02 else 0.03
            val r = MetricsTestSupport.gen(
                SynthMovement.DEADLIFT,
                MetricsTestSupport.script(List(4) { 1.0 }, eccS = 1.5, p1S = 0.5, conS = conS, p2S = 0.5, lead = 1.5, tail = 1.5),
                camera = case.camera,
                fps = case.fps,
            )
            val depth = VbtTestSupport.leftWristDepthM(case.camera)
            val radiusPx = VbtTestSupport.radiusPx(450, depth, case.camera).toDouble()

            // El disco sigue la muñeca de la verdad (en píxeles) y se dibuja con contraste, ruido y exposición típicos.
            val track = r.cleanTrack
            val w = track.frameWidth.toDouble()
            val h = track.frameHeight.toDouble()
            val times = DoubleArray(track.frames.size) { track.frames[it].tUs / 1e6 }
            val xs = DoubleArray(times.size) { track.frames[it].x(PoseLandmark.LEFT_WRIST) * w }
            val ys = DoubleArray(times.size) { track.frames[it].y(PoseLandmark.LEFT_WRIST) * h }
            val path = SampledPath(times, xs, ys)
            val params = SyntheticPlateFrames.params(
                case.seed,
                SceneOptions(width = track.frameWidth, height = track.frameHeight, radiusPx = radiusPx, axisRatio = case.axisRatio, contrast = 45.0, polarity = 1, noiseSigma = 3.0, exposureS = 1.0 / 90.0),
            )
            val scene = SyntheticPlateFrames.scene(params, path)
            val run = PlateTrackingSupport.run(scene, case.fps, times.last() + 1.0 / case.fps)
            println("[rastreador m/s] " + run.summary("peso muerto ${case.fps.toInt()} fps cámara ${case.camera.distanceM} m R=${fmt(radiusPx)}"))

            val bar = BarTrack(track.mediaId, track.frameWidth, track.frameHeight, 450, null, run.samples)
            val profile = RepTestSupport.profile(AnalysisProfileId.DEADLIFT, plate = true)
            val report = TechniqueAnalysisEngine.analyze(TechniqueAnalysisInput(r.track, bar, profile, null, null, emptySet()))
            val mcvs = report.reps.map { it.mcv }
            val truthScale = depth / VbtTestSupport.focalPx(case.camera)
            println(
                "[rastreador m/s] ${case.fps.toInt()} fps: ${report.repCount} reps ${report.confidence} escala ${report.scale?.source} V ${fmt(report.scale?.metersPerPxV)} " +
                    "(verdad ${fmt(truthScale)}) unidad ${report.velocityUnit} MCV ${mcvs.map { fmt(it) }} (verdad ${fmt(expected)}) pico ${fmt(report.peakBest)} avisos ${report.flags}",
            )
            fails.check(report.repCount == 4) { "${case.fps} fps: conteo ${report.repCount} (esperado 4)" }
            fails.check(report.scale?.source == ScaleSource.PLATE) { "${case.fps} fps: escala ${report.scale}" }
            val v = report.scale?.metersPerPxV
            fails.check(v != null && abs(v - truthScale) <= 0.015 * truthScale) { "${case.fps} fps: escala vertical $v (verdad $truthScale ± 1,5 %)" }
            fails.check(report.velocityUnit == VelocityUnit.MPS && MetricKey.VELOCITY_MPS in report.available) {
                "${case.fps} fps: unidad ${report.velocityUnit}, disponible ${report.available.sorted()}"
            }
            for ((i, mcv) in mcvs.withIndex()) {
                fails.check(mcv != null && abs(mcv - expected) <= tolerance) { "${case.fps} fps repetición $i: MCV $mcv (esperada $expected ± $tolerance)" }
            }
            fails.check(AnalysisFlag.PLATE_REJECTED !in report.flags && AnalysisFlag.PARALLAX_SUPPRESSED !in report.flags) { "${case.fps} fps: avisos ${report.flags}" }
        }
        fails.assertNone()
    }
}

package com.example.kpkn.domain.biomechanics.vbt

import com.example.kpkn.domain.biomechanics.bar.PlateSeed

import com.example.kpkn.domain.biomechanics.bar.PlateTrackingContinuity

import com.example.kpkn.domain.biomechanics.bar.BarSample
import com.example.kpkn.domain.biomechanics.bar.BarSampleSource
import com.example.kpkn.domain.biomechanics.bar.BarTrack
import com.example.kpkn.domain.biomechanics.metrics.AnalysisFlag
import com.example.kpkn.domain.biomechanics.metrics.MetricKey
import com.example.kpkn.domain.biomechanics.metrics.MetricsTestSupport
import com.example.kpkn.domain.biomechanics.metrics.ScaleInfo
import com.example.kpkn.domain.biomechanics.metrics.ScaleSource
import com.example.kpkn.domain.biomechanics.metrics.TechniqueAnalysisEngine
import com.example.kpkn.domain.biomechanics.metrics.TechniqueAnalysisInput
import com.example.kpkn.domain.biomechanics.metrics.VelocityUnit
import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.reps.RepTestSupport
import com.example.kpkn.domain.biomechanics.testing.SynthMovement
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** Velocidad en m/s con el disco calibrado: con un disco sintético de desplazamiento y tiempo conocidos, y de punta a punta en el motor. */
class CalibratedVelocityTest {
    private fun fmt(x: Double?) = if (x == null) "—" else String.format(Locale.ROOT, "%.4f", x)

    /** s(τ) = 10τ³ − 15τ⁴ + 6τ⁵ */
    private fun minJerk(tau: Double): Double {
        val t = tau.coerceIn(0.0, 1.0)
        return t * t * t * (10.0 - 15.0 * t + 6.0 * t * t)
    }

    /**
     * Un disco quieto que sube [dyPx] píxeles en [durationS] segundos a partir de [startS] (jerk mínimo) y queda quieto otra vez.
     * Con 0,0025 m/px, 220 px son 0,55 m: en 1,1 s hacen una MCV de 0,50 m/s.
     */
    private fun risingPlate(fps: Double, dyPx: Double = 220.0, startS: Double = 1.0, durationS: Double = 1.1, seconds: Double = 4.0): BarTrack {
        val n = (seconds * fps).roundToLong().toInt() + 1
        val samples = ArrayList<BarSample>(n)
        for (i in 0 until n) {
            val t = i / fps
            val y = 800.0 - dyPx * minJerk((t - startS) / durationS)
            samples.add(BarSample((t * 1e6).roundToLong(), 360f, y.toFloat(), 90f, 1f, 0.9f, BarSampleSource.HOUGH))
        }
        return BarTrack("synthetic-rise", 720, 1280, 450, PlateSeed(0L, 360f, 800f, 90f, true), samples, trackingContinuity = PlateTrackingContinuity.UNINTERRUPTED)
    }

    private val scale = ScaleInfo(ScaleSource.PLATE, 450, 0.0025, 0.0025, 1.0, 0.0)

    @Test
    fun aSyntheticPlateWithAKnownDisplacementAndTimeGivesItsMeanVelocity() {
        val fails = RepTestSupport.Failures()
        for ((fps, tolerance) in listOf(60.0 to 0.02, 30.0 to 0.03)) {
            val series = PlateVerticalSeries.of(risingPlate(fps), 0.0)
            val from = 1_000_000L
            val to = 2_100_000L
            val mcv = series?.meanSpeed(from, to, 0.0025, 0.1)
            val peak = series?.peakSpeed(from, to, 0.0025)
            println("[disco m/s] ${fps.toInt()} fps: MCV ${fmt(mcv)} m/s (esperada 0,5000) pico ${fmt(peak)} m/s (esperado 0,9375)")
            fails.check(series != null) { "no hay serie a $fps fps" }
            fails.check(mcv != null && abs(mcv - 0.5) <= tolerance) { "MCV a ${fps.toInt()} fps: $mcv (esperada 0,5 ± $tolerance)" }
            fails.check(peak != null && abs(peak - 0.9375) <= 0.06) { "pico a ${fps.toInt()} fps: $peak (esperado 1,875 × 0,5 = 0,9375 ± 0,06)" }
        }
        fails.assertNone()
    }

    @Test
    fun theRollOfThePhoneIsUndoneBeforeMeasuringTheVerticalDisplacement() {
        // Un disco que sube 220 px por la vertical de la gravedad, grabado con el móvil girado 10°: en la imagen se mueve en diagonal.
        val rollDeg = 10.0
        val fps = 60.0
        val base = risingPlate(fps)
        val cos = Math.cos(Math.toRadians(rollDeg))
        val sin = Math.sin(Math.toRadians(rollDeg))
        // Un giro +roll de la imagen (horario): el punto (dx, dy) respecto del centro pasa a (dx·cos − dy·sin, dx·sin + dy·cos).
        val rolled = base.copy(
            samples = base.samples.map { s ->
                val dx = s.cx - 360.0
                val dy = s.cy - 640.0
                s.copy(cx = (360.0 + dx * cos - dy * sin).toFloat(), cy = (640.0 + dx * sin + dy * cos).toFloat())
            },
        )
        val series = PlateVerticalSeries.of(rolled, rollDeg)
        val mcv = series?.meanSpeed(1_000_000L, 2_100_000L, 0.0025, 0.1)
        println("[disco m/s] con roll de 10°: MCV ${fmt(mcv)} m/s (esperada 0,5)")
        assertTrue("MCV con roll $mcv", mcv != null && abs(mcv - 0.5) <= 0.02)
        // Sin deshacer el roll saldría ≈ 1,5 % de más (1/cos 10°): lo que importa es que con la corrección no se desvía.
    }

    @Test
    fun aGapOfMoreThanATenthOfASecondLeavesTheRepWithoutVelocityButAShortOneIsInterpolated() {
        val fails = RepTestSupport.Failures()
        val fps = 30.0
        val base = risingPlate(fps)
        // Muestras de respaldo (POSE_PRIOR) en vez de detector: durante 0,3 s no hay cinemática; con 2 fotogramas perdidos (los vecinos
        // válidos quedan a 0,1 s uno de otro, el límite del hueco que se interpola), sí.
        fun withFallback(fromS: Double, toS: Double) = base.copy(
            samples = base.samples.map { s ->
                if (s.tUs >= (fromS * 1e6).toLong() && s.tUs < (toS * 1e6).toLong()) s.copy(source = BarSampleSource.POSE_PRIOR) else s
            },
        )
        val long = PlateVerticalSeries.of(withFallback(1.5, 1.8), 0.0)
        val short = PlateVerticalSeries.of(withFallback(1.5, 1.55), 0.0)
        val mcvLong = long?.meanSpeed(1_000_000L, 2_100_000L, 0.0025, 0.1)
        val mcvShort = short?.meanSpeed(1_000_000L, 2_100_000L, 0.0025, 0.1)
        println("[disco m/s] hueco de 0,3 s: MCV ${fmt(mcvLong)} ; 2 fotogramas perdidos: MCV ${fmt(mcvShort)}")
        fails.check(mcvLong != null && mcvLong.isNaN()) { "con 0,3 s de respaldo no debería haber MCV: $mcvLong" }
        fails.check(mcvShort != null && abs(mcvShort - 0.5) <= 0.03) { "con 2 fotogramas de respaldo se interpola: $mcvShort (esperada 0,5 ± 0,03)" }
        fails.assertNone()
    }

    @Test
    fun aDeadliftWithACalibratedPlateGivesMetersPerSecondCentimetresAndTheirKeys() {
        val fails = RepTestSupport.Failures()
        val conS = 1.0
        val trueLiftM = MetricsTestSupport.joints(SynthMovement.DEADLIFT, 1.0)[PoseLandmark.LEFT_WRIST].y -
            MetricsTestSupport.joints(SynthMovement.DEADLIFT, 0.0)[PoseLandmark.LEFT_WRIST].y
        val expected = trueLiftM / conS
        val depth = VbtTestSupport.leftWristDepthM()
        for ((fps, tolerance) in listOf(60.0 to 0.02, 30.0 to 0.03)) {
            val r = MetricsTestSupport.gen(
                SynthMovement.DEADLIFT,
                MetricsTestSupport.script(List(5) { 1.0 }, eccS = 1.5, p1S = 0.5, conS = conS, p2S = 0.5),
                fps = fps,
            )
            val bar = VbtTestSupport.plateFollowing(r, PoseLandmark.LEFT_WRIST, 450, VbtTestSupport.radiusPx(450, depth))
            val profile = RepTestSupport.profile(AnalysisProfileId.DEADLIFT, plate = true)
            val report = TechniqueAnalysisEngine.analyze(TechniqueAnalysisInput(r.track, bar, profile, null, null, emptySet()))
            val mcvs = report.reps.map { it.mcv }
            println(
                "[disco m/s] peso muerto a ${fps.toInt()} fps: ${report.repCount} reps ${report.confidence} escala ${report.scale?.source} " +
                    "V ${fmt(report.scale?.metersPerPxV)} H ${fmt(report.scale?.metersPerPxH)} (verdad ${fmt(depth / VbtTestSupport.focalPx())}) | unidad ${report.velocityUnit} " +
                    "MCV ${mcvs.map { fmt(it) }} (verdad ${fmt(expected)}) mejor ${fmt(report.mcvBest)} pico ${fmt(report.peakBest)} pérdida ${fmt(report.velocityLossPct)} % | " +
                    "desvío ${fmt(report.barDeviation)} ${report.barDeviationUnit} | disponible ${report.available.sorted()} avisos ${report.flags}",
            )
            fails.check(report.repCount == 5) { "conteo ${report.repCount} a ${fps.toInt()} fps" }
            fails.check(report.scale?.source == ScaleSource.PLATE && report.scale?.plateDiameterMm == 450) { "escala ${report.scale}" }
            val truthScale = depth / VbtTestSupport.focalPx()
            val measuredScale = report.scale?.metersPerPxV
            fails.check(measuredScale != null && abs(measuredScale - truthScale) <= 0.01 * truthScale) {
                "escala vertical $measuredScale (verdad $truthScale ± 1 %)"
            }
            fails.check(report.velocityUnit == VelocityUnit.MPS) { "unidad ${report.velocityUnit}" }
            fails.check(MetricKey.VELOCITY_MPS in report.available && MetricKey.PATH_ABSOLUTE in report.available) { "disponible ${report.available.sorted()}" }
            fails.check((MetricKey.PEAK_VELOCITY in report.available) == (fps >= 50.0)) { "PEAK_VELOCITY ${MetricKey.PEAK_VELOCITY in report.available} a ${fps.toInt()} fps" }
            for ((i, mcv) in mcvs.withIndex()) {
                fails.check(mcv != null && abs(mcv - expected) <= tolerance) { "MCV de la repetición $i a ${fps.toInt()} fps: $mcv (esperada $expected ± $tolerance)" }
            }
            val best = report.mcvBest
            fails.check(best != null && abs(best - expected) <= tolerance) { "mcvBest $best" }
            if (fps >= 50.0) {
                // El pico de un tramo de jerk mínimo es 1,875 veces su velocidad media.
                val peakBest = report.peakBest
                fails.check(peakBest != null && abs(peakBest - 1.875 * expected) <= 0.08) { "pico $peakBest (esperado ${1.875 * expected} ± 0,08)" }
            } else {
                fails.check(report.peakBest == null) { "a 30 fps no hay pico en m/s: ${report.peakBest}" }
            }
            // Con la focal conocida el paralaje se corrige: nada se oculta y la trayectoria va en cm.
            fails.check(AnalysisFlag.PARALLAX_SUPPRESSED !in report.flags) { "avisos ${report.flags}" }
            fails.check(report.barDeviationUnit == "cm" && report.barDeviation != null) { "desvío ${report.barDeviation} ${report.barDeviationUnit}" }
            fails.check(report.reps.all { it.pathOffsetMean != null && it.pathDrift != null }) { "trayectoria en cm ${report.reps.map { it.pathOffsetMean }}" }
        }
        fails.assertNone()
    }

    @Test
    fun aPhoneRolledSixDegreesDoesNotChangeTheCalibratedVelocity() {
        val fails = RepTestSupport.Failures()
        val trueLiftM = MetricsTestSupport.joints(SynthMovement.DEADLIFT, 1.0)[PoseLandmark.LEFT_WRIST].y -
            MetricsTestSupport.joints(SynthMovement.DEADLIFT, 0.0)[PoseLandmark.LEFT_WRIST].y
        val camera = com.example.kpkn.domain.biomechanics.testing.SynthCamera(rollDeg = 6.0)
        val r = MetricsTestSupport.gen(
            SynthMovement.DEADLIFT,
            MetricsTestSupport.script(List(5) { 1.0 }, eccS = 1.5, p1S = 0.5, conS = 1.0, p2S = 0.5),
            camera = camera,
            fps = 60.0,
        )
        val bar = VbtTestSupport.plateFollowing(r, PoseLandmark.LEFT_WRIST, 450, VbtTestSupport.radiusPx(450, VbtTestSupport.leftWristDepthM(camera), camera))
        val profile = RepTestSupport.profile(AnalysisProfileId.DEADLIFT, plate = true)
        val report = TechniqueAnalysisEngine.analyze(TechniqueAnalysisInput(r.track, bar, profile, null, null, emptySet()))
        println("[disco m/s] con roll de 6°: ${report.repCount} reps avisos ${report.flags} MCV ${report.reps.map { fmt(it.mcv) }} (verdad ${fmt(trueLiftM)})")
        fails.check(report.repCount == 5) { "conteo ${report.repCount}" }
        fails.check(AnalysisFlag.ROLL_CORRECTED in report.flags) { "avisos ${report.flags}" }
        fails.check(report.velocityUnit == VelocityUnit.MPS) { "unidad ${report.velocityUnit}" }
        for ((i, mcv) in report.reps.map { it.mcv }.withIndex()) {
            fails.check(mcv != null && abs(mcv - trueLiftM) <= 0.02) { "MCV de la repetición $i con roll: $mcv (esperada $trueLiftM ± 0,02)" }
        }
        fails.assertNone()
    }

    @Test
    fun anInjectedLateralDriftComesOutInCentimetres() {
        val fails = RepTestSupport.Failures()
        val r = MetricsTestSupport.gen(
            SynthMovement.DEADLIFT,
            MetricsTestSupport.script(List(5) { 1.0 }, eccS = 1.5, p1S = 0.5, conS = 1.0, p2S = 0.5),
            fps = 60.0,
        )
        val depth = VbtTestSupport.leftWristDepthM()
        val bar = VbtTestSupport.plateFollowing(r, PoseLandmark.LEFT_WRIST, 450, VbtTestSupport.radiusPx(450, depth))
        val profile = RepTestSupport.profile(AnalysisProfileId.DEADLIFT, plate = true)
        fun analyze(b: BarTrack) = TechniqueAnalysisEngine.analyze(TechniqueAnalysisInput(r.track, b, profile, null, null, emptySet()))
        val base = analyze(bar)
        // De lado el atleta mira a la izquierda de la imagen: −10 px es hacia delante. A 0,0054 m/px son 5,4 cm al final de la subida.
        val shiftPx = -10f
        val deviated = 2
        val shifted = analyze(MetricsTestSupport.withLateralDrift(bar, r.reps[deviated], shiftPx))
        val expectedCm = 10.0 * depth / VbtTestSupport.focalPx() * 100.0
        println("[disco cm] deriva en cm: base ${base.reps.map { fmt(it.pathDrift) }} | inyectada ${shifted.reps.map { fmt(it.pathDrift) }} | esperada +${fmt(expectedCm)} cm en la repetición $deviated")
        fails.check(base.repCount == 5 && shifted.repCount == 5) { "conteo ${base.repCount}/${shifted.repCount}" }
        fails.check(shifted.barDeviationUnit == "cm") { "unidad ${shifted.barDeviationUnit}" }
        for (i in 0 until 5) {
            val delta = (shifted.reps[i].pathDrift ?: Double.NaN) - (base.reps[i].pathDrift ?: Double.NaN)
            if (i == deviated) {
                fails.check(delta > 0.0 && abs(delta - expectedCm) <= 0.3 * expectedCm) { "deriva inyectada $delta cm (esperada $expectedCm ± 30 %)" }
            } else {
                fails.check(abs(delta) <= 0.3) { "la repetición $i no debería cambiar: Δ=$delta cm" }
            }
        }
        fails.assertNone()
    }

    @Test
    fun withoutTheCalibrationNothingGoesInMetersNorCentimetres() {
        val fails = RepTestSupport.Failures()
        val r = MetricsTestSupport.gen(
            SynthMovement.DEADLIFT,
            MetricsTestSupport.script(List(5) { 1.0 }, eccS = 1.5, p1S = 0.5, conS = 1.0, p2S = 0.5),
            fps = 60.0,
        )
        val depth = VbtTestSupport.leftWristDepthM()
        // El mismo disco, pero sin el diámetro (sin calibrar): sirve como señal de conteo, no como metro.
        val bar = VbtTestSupport.plateFollowing(r, PoseLandmark.LEFT_WRIST, null, VbtTestSupport.radiusPx(450, depth))
        val profile = RepTestSupport.profile(AnalysisProfileId.DEADLIFT, plate = true)
        val report = TechniqueAnalysisEngine.analyze(TechniqueAnalysisInput(r.track, bar, profile, null, null, emptySet()))
        println("[disco m/s] sin calibrar: ${report.repCount} reps unidad ${report.velocityUnit} escala ${report.scale} desvío ${fmt(report.barDeviation)} ${report.barDeviationUnit} disponible ${report.available.sorted()}")
        fails.check(report.repCount == 5) { "conteo ${report.repCount}" }
        fails.check(report.scale == ScaleInfo.NONE) { "escala ${report.scale}" }
        fails.check(report.velocityUnit == VelocityUnit.BODY_HEIGHTS_PER_S) { "unidad ${report.velocityUnit}" }
        fails.check(MetricKey.VELOCITY_MPS !in report.available && MetricKey.PEAK_VELOCITY !in report.available && MetricKey.PATH_ABSOLUTE !in report.available) {
            "disponible ${report.available.sorted()}"
        }
        fails.check(MetricKey.VELOCITY_RELATIVE in report.available) { "disponible ${report.available.sorted()}" }
        fails.check(report.barDeviationUnit == "%ROM") { "unidad del desvío ${report.barDeviationUnit}" }
        fails.assertNone()
    }

    @Test
    fun withoutTheFocalAndAnOffCentrePlateTheAbsoluteOffsetIsHiddenButTheDriftIsKept() {
        val fails = RepTestSupport.Failures()
        val r = MetricsTestSupport.gen(
            SynthMovement.DEADLIFT,
            MetricsTestSupport.script(List(5) { 1.0 }, eccS = 1.5, p1S = 0.5, conS = 1.0, p2S = 0.5),
            fps = 60.0,
        )
        val depth = VbtTestSupport.leftWristDepthM()
        val centred = VbtTestSupport.plateFollowing(r, PoseLandmark.LEFT_WRIST, 450, VbtTestSupport.radiusPx(450, depth))
        // La galería no trae la focal; y el disco se ve 250 px a la derecha de donde está el atleta (error de paralaje de ~29 cm).
        val offCentre = centred.copy(samples = centred.samples.map { it.copy(cx = it.cx + 250f) })
        val noFocal = r.track.copy(focalLengthPx = null)
        val profile = RepTestSupport.profile(AnalysisProfileId.DEADLIFT, plate = true)
        val report = TechniqueAnalysisEngine.analyze(TechniqueAnalysisInput(noFocal, offCentre, profile, null, null, emptySet()))
        println(
            "[disco cm] sin focal: ${report.repCount} reps avisos ${report.flags} error ${fmt(report.scale?.parallaxErrorCm)} cm | desvío ${fmt(report.barDeviation)} ${report.barDeviationUnit} " +
                "reps ${report.reps.map { fmt(it.pathOffsetMean) }} deriva ${report.reps.map { fmt(it.pathDrift) }} | disponible ${report.available.sorted()}",
        )
        fails.check(report.repCount == 5) { "conteo ${report.repCount}" }
        fails.check(AnalysisFlag.PARALLAX_SUPPRESSED in report.flags) { "avisos ${report.flags}" }
        fails.check(report.barDeviation == null && report.reps.all { it.pathOffsetMean == null }) { "el desvío absoluto debería estar oculto: ${report.barDeviation}" }
        fails.check(report.barDeviationUnit == "cm" && report.reps.all { it.pathDrift != null }) { "la deriva sigue en cm: ${report.reps.map { it.pathDrift }}" }
        fails.check(MetricKey.PATH_ABSOLUTE in report.available && report.velocityUnit == VelocityUnit.MPS) { "disponible ${report.available.sorted()}, unidad ${report.velocityUnit}" }
        fails.assertNone()
    }
}

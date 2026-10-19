package com.example.kpkn.domain.biomechanics.vbt

import com.example.kpkn.domain.biomechanics.bar.BarSampleSource
import com.example.kpkn.domain.biomechanics.metrics.AnalysisFlag
import com.example.kpkn.domain.biomechanics.metrics.ScaleSource
import com.example.kpkn.domain.biomechanics.reps.RepTestSupport
import org.junit.Test
import java.util.Locale
import kotlin.math.abs

/** Escala por disco (§8 punto 6): radio de calibración, elipse y paralaje. */
class PlateScaleTest {
    private fun fmt(x: Double?) = if (x == null) "—" else String.format(Locale.ROOT, "%.5f", x)

    @Test
    fun aPlateOfFortyFiveCentimetresWithARadiusOfNinetyPixelsIsTwoAndAHalfMillimetresPerPixel() {
        val fails = RepTestSupport.Failures()
        val result = PlateScale.estimate(VbtTestSupport.oscillatingPlate(), 30.0, 514.1f)
        val scale = result.scale
        println("[escala] 450 mm con r = 90 px: H ${fmt(scale?.metersPerPxH)} V ${fmt(scale?.metersPerPxV)} m/px, ${result.calibrationSamples} fotogramas lentos, Z_p ${fmt(result.planeDepthM)} m")
        val h = scale?.metersPerPxH
        val v = scale?.metersPerPxV
        fails.check(scale != null && scale.source == ScaleSource.PLATE && scale.plateDiameterMm == 450) { "escala $scale" }
        fails.check(h != null && abs(h - 0.0025) <= 0.0025 * 0.01) { "H $h (esperado 0,0025 ± 1 %)" }
        fails.check(v != null && abs(v - 0.0025) <= 0.0025 * 0.01) { "V $v (esperado 0,0025 ± 1 %)" }
        fails.check(scale?.axisRatio == 1.0) { "axisRatio ${scale?.axisRatio}" }
        fails.check(result.flags.isEmpty() && result.reason == null) { "avisos ${result.flags}, motivo ${result.reason}" }
        // La profundidad del plano del disco: f·R/r = 514,1·0,225/90 = 1,285 m.
        val depth = result.planeDepthM
        fails.check(depth != null && abs(depth - 1.285) <= 0.01) { "Z_p $depth (esperado 1,285)" }
        fails.assertNone()
    }

    @Test
    fun blurredFastFramesDoNotInflateTheCalibrationRadius() {
        val fails = RepTestSupport.Failures()
        // En movimiento el desenfoque ensancha los bordes verticales: hasta un 12 % en los fotogramas rápidos. Los lentos dan 90 px.
        val blurred = VbtTestSupport.oscillatingPlate(radiusAt = { _, speed -> if (speed > 36.0) (90.0 * (1.0 + 0.12 * speed / 157.0)).toFloat() else 90f })
        val result = PlateScale.estimate(blurred, 30.0, 514.1f)
        val h = result.scale?.metersPerPxH
        println("[escala] con desenfoque: r̂_cal ${fmt(result.radiusPx)} px, H ${fmt(h)} m/px, ${result.calibrationSamples} fotogramas lentos")
        val radius = result.radiusPx
        fails.check(radius != null && abs(radius - 90.0) <= 1.5) { "radio de calibración $radius (esperado 90 ± 1,5)" }
        fails.check(h != null && abs(h - 0.0025) <= 0.0025 * 0.02) { "H $h (esperado 0,0025 ± 2 %)" }
        // La mediana de TODOS los fotogramas sí saldría inflada: la guarda de fotogramas lentos importa.
        val all = blurred.samples.map { it.r.toDouble() }.sorted()
        val median = all[all.size / 2]
        fails.check(median > 91.0) { "la mediana de todos los radios ($median) debería estar inflada por el desenfoque" }
        fails.assertNone()
    }

    @Test
    fun anEllipseOfEightyPercentIsCorrectedWithADifferentScaleOnEachAxisAndFlagged() {
        val fails = RepTestSupport.Failures()
        val result = PlateScale.estimate(VbtTestSupport.oscillatingPlate(axisRatio = 0.8f), 30.0, 514.1f)
        val scale = result.scale
        println("[escala] a/b = 0,8: H ${fmt(scale?.metersPerPxH)} V ${fmt(scale?.metersPerPxV)} m/px, avisos ${result.flags}")
        fails.check(AnalysisFlag.PLATE_ELLIPSE in result.flags && AnalysisFlag.PLATE_REJECTED !in result.flags) { "avisos ${result.flags}" }
        val ratio = scale?.axisRatio
        fails.check(ratio != null && abs(ratio - 0.8) <= 1e-6) { "axisRatio $ratio" }
        val h = scale?.metersPerPxH
        val v = scale?.metersPerPxV
        fails.check(h != null && v != null && h > v) { "escalas H $h y V $v: la horizontal (el semieje comprimido) debe ser mayor" }
        // El círculo equivalente es √(a·b) = 90 px: a = 90·√0,8 y b = 90/√0,8; H = R/a, V = R/b, y H/V = 1/(a/b) = 1,25.
        fails.check(h != null && v != null && abs(h / v - 1.25) <= 0.005) { "H/V ${if (h != null && v != null) h / v else null} (esperado 1,25)" }
        fails.check(v != null && abs(v - 0.225 * Math.sqrt(0.8) / 90.0) <= 1e-6) { "V $v" }
        fails.assertNone()
    }

    @Test
    fun anEllipseBelowSeventyPercentRejectsThePlateAndGivesNoScale() {
        val result = PlateScale.estimate(VbtTestSupport.oscillatingPlate(axisRatio = 0.65f), 30.0, 514.1f)
        println("[escala] a/b = 0,65: escala ${result.scale} avisos ${result.flags} motivo ${result.reason}")
        org.junit.Assert.assertNull(result.scale)
        org.junit.Assert.assertEquals(setOf(AnalysisFlag.PLATE_REJECTED), result.flags)
        org.junit.Assert.assertEquals("ellipse_rejected", result.reason)
    }

    @Test
    fun theEllipseIsReadFromTheMedianOfTheMeasuredRatiosAndIgnoresWhatWasNotMeasured() {
        // La mitad de las muestras llevan 1 (no medido) y la otra mitad 0,9: solo cuentan las medidas.
        val plate = VbtTestSupport.oscillatingPlate()
        val mixed = plate.copy(samples = plate.samples.mapIndexed { i, s -> if (i % 2 == 0) s.copy(axisRatio = 0.9f) else s })
        val result = PlateScale.estimate(mixed, 30.0, 514.1f)
        val ratio = result.scale?.axisRatio
        println("[escala] mitad medida: axisRatio $ratio avisos ${result.flags}")
        org.junit.Assert.assertTrue("axisRatio $ratio", ratio != null && abs(ratio - 0.9) <= 1e-6)
        org.junit.Assert.assertTrue("0,9 no avisa de elipse: ${result.flags}", AnalysisFlag.PLATE_ELLIPSE !in result.flags)
    }

    @Test
    fun withoutTheFocalAndAnOffCentrePlateTheParallaxHidesTheAbsoluteOffsets() {
        val fails = RepTestSupport.Failures()
        // El disco está 250 px a la derecha del centro: a 1,285 m (r = 90 px) el paralaje de los 0,70 m hasta el plano del atleta
        // da un error de 22 cm en el desvío respecto del medio pie. Sin la focal no se puede corregir.
        val offCentre = VbtTestSupport.oscillatingPlate(centreX = 610f)
        val result = PlateScale.estimate(offCentre, 30.0, null)
        println("[escala] sin focal, disco a 250 px del centro: error ${fmt(result.scale?.parallaxErrorCm)} cm avisos ${result.flags} ocultos ${result.offsetsHidden}")
        fails.check(AnalysisFlag.PARALLAX_SUPPRESSED in result.flags && result.offsetsHidden) { "avisos ${result.flags}, ocultos ${result.offsetsHidden}" }
        fails.check(!result.parallaxCorrected) { "sin la focal no se corrige" }
        val error = result.scale?.parallaxErrorCm
        fails.check(error != null && abs(error - 22.0) <= 2.0) { "error de paralaje $error cm (esperado ≈ 22)" }
        // La escala sigue siendo válida: la deriva y la excursión no llevan paralaje.
        fails.check(result.scale?.metersPerPxH != null) { "debería haber escala" }

        // Con el disco centrado el error es nulo y no se oculta nada, aunque no haya focal.
        val centred = PlateScale.estimate(VbtTestSupport.oscillatingPlate(centreX = 360f), 30.0, null)
        fails.check(AnalysisFlag.PARALLAX_SUPPRESSED !in centred.flags && !centred.offsetsHidden) { "disco centrado: avisos ${centred.flags}" }
        fails.check((centred.scale?.parallaxErrorCm ?: 99.0) < 0.5) { "error con el disco centrado ${centred.scale?.parallaxErrorCm}" }
        fails.assertNone()
    }

    @Test
    fun withTheFocalTheParallaxIsCorrectedAndNothingIsHidden() {
        val fails = RepTestSupport.Failures()
        val offCentre = VbtTestSupport.oscillatingPlate(centreX = 610f)
        val result = PlateScale.estimate(offCentre, 30.0, 514.1f)
        val error = result.scale?.parallaxErrorCm
        println("[escala] con focal, disco a 250 px del centro: error residual ${fmt(error)} cm avisos ${result.flags} corregido ${result.parallaxCorrected} Z_s/Z_p ${fmt(result.depthRatio)}")
        fails.check(result.parallaxCorrected && !result.offsetsHidden && AnalysisFlag.PARALLAX_SUPPRESSED !in result.flags) { "corregido ${result.parallaxCorrected}, avisos ${result.flags}" }
        // Queda el 15 % del error sin corregir (la incertidumbre de los 0,70 m): ≈ 3,3 cm.
        fails.check(error != null && abs(error - 3.3) <= 0.6) { "error residual $error cm (esperado ≈ 3,3)" }
        fails.check(abs(result.depthRatio - (1.285 + 0.70) / 1.285) <= 0.01) { "Z_s/Z_p ${result.depthRatio}" }
        fails.assertNone()
    }

    @Test
    fun withoutAnyOfItsPrerequisitesThereIsNoScale() {
        val fails = RepTestSupport.Failures()
        // Sin diámetro (traza sin calibrar).
        val uncalibrated = PlateScale.estimate(VbtTestSupport.oscillatingPlate(diameterMm = null), 30.0, 514.1f)
        fails.check(uncalibrated.scale == null && uncalibrated.reason == "no_diameter") { "sin diámetro: ${uncalibrated.scale}, ${uncalibrated.reason}" }
        // Un disco que casi nunca se detiene (2 s con un periodo de 0,95 s: como mucho 2 fotogramas por debajo de 0,4 radios por segundo).
        val alwaysMoving = PlateScale.estimate(VbtTestSupport.oscillatingPlate(seconds = 2.0, amplitudePx = 150f, periodS = 0.95), 30.0, 514.1f)
        fails.check(alwaysMoving.scale == null && alwaysMoving.reason == "few_slow_samples") { "siempre en movimiento: ${alwaysMoving.scale}, ${alwaysMoving.reason}" }
        // Solo muestras de respaldo (POSE_PRIOR): no hay detector del que fiarse.
        val fallback = PlateScale.estimate(VbtTestSupport.oscillatingPlate(source = BarSampleSource.POSE_PRIOR), 30.0, 514.1f)
        fails.check(fallback.scale == null && fallback.reason == "few_hough_samples") { "solo respaldo: ${fallback.scale}, ${fallback.reason}" }
        // Calidad baja (q < 0,5): ningún radio de calibración.
        val poor = PlateScale.estimate(VbtTestSupport.oscillatingPlate(quality = 0.3f), 30.0, 514.1f)
        fails.check(poor.scale == null) { "con q = 0,3: ${poor.scale}" }
        fails.assertNone()
    }
}

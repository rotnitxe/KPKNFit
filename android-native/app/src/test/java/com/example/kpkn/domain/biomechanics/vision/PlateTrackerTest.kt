package com.example.kpkn.domain.biomechanics.vision

import com.example.kpkn.domain.biomechanics.bar.BarSampleSource
import com.example.kpkn.domain.biomechanics.reps.RepTestSupport
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale
import kotlin.math.abs

/** Rastreador del disco (§8 puntos 4 y 5) sobre el generador sintético de §10.4: centro, radio, readquisición y a/b. */
class PlateTrackerTest {
    private fun fmt(x: Double) = String.format(Locale.ROOT, "%.2f", x)

    private val seeds = listOf(3L, 8L, 14L, 21L)

    private fun dump(name: String, scene: PlateScene, tS: Double, index: Int) {
        val dir = System.getenv("KPKN_VT6A_DUMP_DIR") ?: return
        SyntheticPlateFrames.writePng(scene.frame(tS, index), File(dir, "$name.png"))
    }

    /** Una escena con repeticiones: la trayectoria sale de la semilla, a [fps] y con las opciones dadas. */
    private fun repScene(seed: Long, options: SceneOptions = SceneOptions(), reps: Int = 2): Pair<PlateScene, RepPlatePath> {
        val params = SyntheticPlateFrames.params(seed, options)
        val path = SyntheticPlateFrames.repPath(params, reps)
        return SyntheticPlateFrames.scene(params, path) to path
    }

    /** Comprueba el centro (mediana y P95 en % de D) y el radio en los fotogramas lentos; devuelve las líneas de fallo. */
    private fun checkRun(run: TrackRun, label: String, fails: RepTestSupport.Failures, medianMax: Double = 1.0, p95Max: Double = 2.0) {
        val all = run.centerErrors()
        val median = TrackRun.median(all)
        val p95 = TrackRun.percentile(all, 95.0)
        println("[rastreador] " + run.summary(label))
        fails.check(median <= medianMax) { "$label: error mediano del centro ${fmt(median)} % de D (> $medianMax)" }
        fails.check(p95 <= p95Max) { "$label: P95 del centro ${fmt(p95)} % de D (> $p95Max)" }
        // Radio: HOUGH en fotogramas lentos (velocidad < 0,4 radios por segundo), como los que calibran la escala.
        val slow = ArrayList<Double>()
        for (k in 0 until run.frames) {
            val s = run.samples[k]
            if (s.source != BarSampleSource.HOUGH || s.quality < 0.5f) continue
            if (run.speedPxPerS(k) >= 0.4 * run.trueRadius) continue
            slow.add(PlateTrackingSupport.relativePct(s.r.toDouble(), run.trueRadius))
        }
        val radius = slow.toDoubleArray()
        println("[rastreador] $label: radio en ${radius.size} fotogramas lentos: mediana ${fmt(TrackRun.median(radius))} % P95 ${fmt(TrackRun.percentile(radius, 95.0))} %")
        fails.check(radius.size >= 8) { "$label: solo ${radius.size} fotogramas lentos con HOUGH de calidad" }
        fails.check(radius.isNotEmpty() && TrackRun.median(radius) <= 1.0) { "$label: error mediano del radio ${fmt(TrackRun.median(radius))} % (> 1)" }
    }

    @Test
    fun theCentreAndTheRadiusFollowThePlateAt30And60Fps() {
        val fails = RepTestSupport.Failures()
        for (seed in seeds) {
            val (scene, path) = repScene(seed)
            for (fps in listOf(30.0, 60.0)) {
                val run = PlateTrackingSupport.run(scene, fps, path.durationS)
                checkRun(run, "semilla $seed R=${fmt(scene.params.radiusPx)} contraste ${fmt(scene.params.contrast)} pol ${scene.params.polarity} ruido ${fmt(scene.params.noiseSigma)} ${fps.toInt()} fps", fails)
            }
            if (seed == seeds.first()) dump("tracker-$seed", scene, 1.5, 45)
        }
        fails.assertNone()
    }

    @Test
    fun anArmAndAnUprightHidingPartOfTheRingDoNotLoseThePlate() {
        val fails = RepTestSupport.Failures()
        for (seed in listOf(5L, 12L, 27L)) {
            val (armScene, armPath) = repScene(seed, SceneOptions(arm = true))
            val armRun = PlateTrackingSupport.run(armScene, 30.0, armPath.durationS)
            checkRun(armRun, "brazo semilla $seed R=${fmt(armScene.params.radiusPx)}", fails)
            if (seed == 5L) dump("tracker-arm-$seed", armScene, 1.5, 45)
            val (upScene, upPath) = repScene(seed, SceneOptions(upright = true))
            val upRun = PlateTrackingSupport.run(upScene, 30.0, upPath.durationS)
            checkRun(upRun, "montante semilla $seed R=${fmt(upScene.params.radiusPx)}", fails)
            if (seed == 5L) dump("tracker-upright-$seed", upScene, 1.5, 45)
        }
        fails.assertNone()
    }

    @Test
    fun theTrackerReacquiresWithinFiveFramesAfterAFullOcclusionOfThreeTenthsOfASecond() {
        val fails = RepTestSupport.Failures()
        // (semilla, fps, con muñeca): las seis de siempre y dos sin muñeca, donde para readquirir solo hay la predicción.
        val combos = listOf(4L, 9L, 17L).flatMap { s -> listOf(30.0, 60.0).map { Triple(s, it, true) } } +
            listOf(Triple(4L, 60.0, false), Triple(9L, 60.0, false))
        for ((seed, fps, withWrist) in combos) {
            // El bloque tapa el disco entero 0,3 s a mitad de la primera concéntrica (el disco se mueve).
            val probe = SyntheticPlateFrames.params(seed)
            val probePath = SyntheticPlateFrames.repPath(probe, 2)
            val (con0, con1) = probePath.concentric(0)
            val start = 0.5 * (con0 + con1) - 0.15
            val options = SceneOptions(fullOcclusion = start..(start + 0.3))
            val params = SyntheticPlateFrames.params(seed, options)
            val path = SyntheticPlateFrames.repPath(params, 2)
            val scene = SyntheticPlateFrames.scene(params, path)
            val run = PlateTrackingSupport.run(scene, fps, path.durationS, useWrist = withWrist)
            // Primer fotograma con la exposición entera después de la oclusión.
            val end = start + 0.3
            var visible = 0
            while (visible < run.frames && run.timesS[visible] - params.exposureS / 2.0 < end) visible++
            val good = PlateTrackingSupport.firstGoodAfter(run, visible, 2.0)
            val frames = if (good < 0) Int.MAX_VALUE else good - visible + 1
            val occluded = (0 until run.frames).count { run.timesS[it] in start..end }
            val sources = (visible - occluded..visible + 6).filter { it in 0 until run.frames }.joinToString("") {
                when (run.samples[it].source) {
                    BarSampleSource.HOUGH -> "H"
                    BarSampleSource.NCC -> "N"
                    BarSampleSource.POSE_PRIOR -> "P"
                    BarSampleSource.PREDICTED -> "."
                }
            }
            println(
                "[rastreador] oclusión semilla $seed ${fps.toInt()} fps${if (withWrist) "" else " sin muñeca"} R=${fmt(params.radiusPx)}: ${occluded} fotogramas tapados, readquiere en $frames fotogramas " +
                    "(primero visible $visible, bueno $good); fuentes desde la oclusión: $sources",
            )
            if (frames > 3) {
                // Traza de depuración: fuente, calidad y error de cada muestra desde dos fotogramas antes de la oclusión.
                for (k in (visible - occluded - 2)..(visible + 8)) {
                    if (k !in 0 until run.frames) continue
                    val s = run.samples[k]
                    println(
                        "      k=$k ${s.source} q=${fmt(s.quality.toDouble())} r=${fmt(s.r.toDouble())} " +
                            "err=${fmt(Math.hypot(s.cx - run.truthX[k], s.cy - run.truthY[k]))} px de ${fmt(run.diameterPx)} de D, verdad (${fmt(run.truthX[k])}, ${fmt(run.truthY[k])})",
                    )
                }
                dump("tracker-occlusion-$seed-${fps.toInt()}", scene, run.timesS[visible - occluded / 2], visible - occluded / 2)
            }
            fails.check(frames <= 5) { "semilla $seed ${fps.toInt()} fps${if (withWrist) "" else " sin muñeca"}: readquisición en $frames fotogramas (≤ 5)" }
            // Pasada la readquisición, el seguimiento sigue bien hasta el final.
            val tail = run.centerErrors { it >= visible + 10 && run.isDetector(run.samples[it]) }
            fails.check(tail.isNotEmpty() && TrackRun.percentile(tail, 95.0) <= 2.0) {
                "semilla $seed ${fps.toInt()} fps: P95 del centro tras la oclusión ${fmt(TrackRun.percentile(tail, 95.0))} % de D"
            }
        }
        fails.assertNone()
    }

    @Test
    fun theAxisRatioOfTiltedPlatesIsEstimatedWithinTwoHundredthsAndTheRadiusIsTheEquivalentOne() {
        val fails = RepTestSupport.Failures()
        for (ratio in listOf(1.0, 0.94, 0.85)) {
            for (seed in listOf(6L, 13L, 25L)) {
                val (scene, path) = repScene(seed, SceneOptions(axisRatio = ratio))
                val run = PlateTrackingSupport.run(scene, 30.0, path.durationS)
                val ratios = ArrayList<Double>()
                val radii = ArrayList<Double>()
                for (k in 0 until run.frames) {
                    val s = run.samples[k]
                    if (s.source != BarSampleSource.HOUGH || s.quality < 0.5f) continue
                    if (run.speedPxPerS(k) >= 0.4 * run.trueRadius) continue
                    ratios.add(s.axisRatio.toDouble())
                    radii.add(PlateTrackingSupport.relativePct(s.r.toDouble(), run.trueRadius))
                }
                val med = TrackRun.median(ratios.toDoubleArray())
                val p90 = TrackRun.percentile(ratios.map { abs(it - ratio) }.toDoubleArray(), 90.0)
                if (ratio != 1.0) {
                    // Los primeros fotogramas: el a/b se aprende de la primera elipse medida.
                    println(
                        "      primeros fotogramas: " + (0 until 8.coerceAtMost(run.frames)).joinToString(" | ") { k ->
                            val s = run.samples[k]
                            "${s.source.name.take(1)} q=${fmt(s.quality.toDouble())} a/b=${fmt(s.axisRatio.toDouble())} r=${fmt(s.r.toDouble())} err=${fmt(run.centerErrorPct(k))}%"
                        },
                    )
                }
                println(
                    "[rastreador] a/b $ratio semilla $seed R=${fmt(scene.params.radiusPx)}: ${ratios.size} muestras lentas, a/b mediano ${fmt(med)} (P90 del error ${fmt(p90)}), " +
                        "radio equivalente mediana ${fmt(TrackRun.median(radii.toDoubleArray()))} % | " + run.summary("centro"),
                )
                fails.check(ratios.size >= 8) { "a/b $ratio semilla $seed: solo ${ratios.size} muestras" }
                fails.check(abs(med - ratio) <= 0.02) { "a/b $ratio semilla $seed: mediana ${fmt(med)} (±0,02)" }
                fails.check(TrackRun.median(radii.toDoubleArray()) <= 1.5) { "a/b $ratio semilla $seed: radio equivalente ${fmt(TrackRun.median(radii.toDoubleArray()))} %" }
                val all = run.centerErrors()
                fails.check(TrackRun.median(all) <= 1.0 && TrackRun.percentile(all, 95.0) <= 2.0) {
                    "a/b $ratio semilla $seed: centro mediana ${fmt(TrackRun.median(all))} P95 ${fmt(TrackRun.percentile(all, 95.0))} % de D"
                }
                if (ratio == 0.85 && seed == 6L) dump("tracker-ellipse-$seed", scene, 1.5, 45)
            }
        }
        fails.assertNone()
    }

    @Test
    fun withoutTheNccAndWithoutTheWristThePlateIsStillFollowedWhenItIsAlwaysVisible() {
        // Una pasada sin NCC ni muñeca, solo Hough + Kalman: el seguimiento no depende de las ayudas cuando el disco se ve.
        val fails = RepTestSupport.Failures()
        val (scene, path) = repScene(31L)
        val config = PlateTrackerConfig(plateDiameterMm = 450, nccEnabled = false)
        val run = PlateTrackingSupport.run(scene, 30.0, path.durationS, config, useWrist = false)
        checkRun(run, "sin NCC ni muñeca", fails)
        fails.assertNone()
    }

    @Test
    fun theTrackerWorksBackwardsInTimeToo() {
        // El analizador puede seguir el disco desde una semilla confirmada en mitad del vídeo hacia atrás: Δt negativo.
        val fails = RepTestSupport.Failures()
        for (seed in listOf(3L, 21L)) {
            val (scene, path) = repScene(seed)
            val run = PlateTrackingSupport.run(scene, 30.0, path.durationS, reverse = true)
            checkRun(run, "hacia atrás semilla $seed R=${fmt(scene.params.radiusPx)}", fails)
        }
        fails.assertNone()
    }

    @Test
    fun aFastLiftWithTheLongestExposureStaysTrackedAndNeverLosesThePlateForGood() {
        // Recorrido de 600 px en una concéntrica de 0,9 s con la exposición de 1/60 s: el pico de velocidad es 1,875·600/0,9 = 1250 px/s
        // y el desenfoque de ≈ 21 px (más del 10 % del diámetro). Los bordes horizontales del disco casi desaparecen.
        val fails = RepTestSupport.Failures()
        for (seed in listOf(10L, 33L)) {
            val params = SyntheticPlateFrames.params(seed, SceneOptions(radiusPx = 90.0, exposureS = 1.0 / 60.0))
            val path = SyntheticPlateFrames.repPath(params, 2, romPxOverride = 600.0, conSOverride = 0.9)
            val scene = SyntheticPlateFrames.scene(params, path)
            for (fps in listOf(30.0, 60.0)) {
                val run = PlateTrackingSupport.run(scene, fps, path.durationS)
                val all = run.centerErrors()
                val blurMax = (0 until run.frames).maxOf { scene.blurPx(run.timesS[it]) }
                println("[rastreador] rápido semilla $seed ${fps.toInt()} fps (desenfoque máx. ${fmt(blurMax)} px): " + run.summary("R=90"))
                fails.check(TrackRun.median(all) <= 1.0) { "rápido semilla $seed ${fps.toInt()} fps: mediana ${fmt(TrackRun.median(all))} % de D" }
                fails.check(TrackRun.percentile(all, 95.0) <= 3.0) { "rápido semilla $seed ${fps.toInt()} fps: P95 ${fmt(TrackRun.percentile(all, 95.0))} % de D (≤ 3)" }
                // Al final, con el disco quieto, vuelve a medirse bien.
                val tail = run.centerErrors { it >= run.frames - (fps * 0.8).toInt() }
                fails.check(TrackRun.percentile(tail, 95.0) <= 1.0) { "rápido semilla $seed ${fps.toInt()} fps: al final P95 ${fmt(TrackRun.percentile(tail, 95.0))} % de D" }
            }
        }
        fails.assertNone()
    }

    @Test
    fun theSamplesCarryTheirSourceAndTheDetectorOnesCoverTheSet() {
        val (scene, path) = repScene(44L)
        val run = PlateTrackingSupport.run(scene, 30.0, path.durationS)
        val detector = run.samples.count { run.isDetector(it) }
        val share = detector.toDouble() / run.frames
        println("[rastreador] cobertura del detector (HOUGH + NCC) ${fmt(share * 100.0)} % de ${run.frames} fotogramas; " + run.summary("semilla 44"))
        assertTrue("cobertura del detector ${fmt(share * 100.0)} % (≥ 95 %)", share >= 0.95)
        for (s in run.samples) {
            assertTrue("calidad fuera de 0..1: ${s.quality}", s.quality in 0f..1f)
            assertTrue("radio no positivo", s.r > 0f)
            assertTrue("axisRatio fuera de 0,5..2: ${s.axisRatio}", s.axisRatio in 0.5f..2f)
        }
    }
}

package com.example.kpkn.domain.biomechanics.vision

import com.example.kpkn.domain.biomechanics.bar.BarSampleSource
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.Locale

/**
 * Barridos OPCIONALES del generador de §10.4, para ver el reparto de errores con muchas más semillas que las de las pruebas de
 * aceptación (y para que VT10 reajuste los umbrales). Se saltan si no se pide el barrido con una variable de entorno:
 * - `KPKN_VT6A_SWEEP=100-159` (o «113,130-159»): el rastreador, con a/b de 0,85, 0,94 y 1, brazo, montante, 30 y 60 fps.
 * - `KPKN_VT6A_DET_SWEEP=51-170`: el detector, con la muñeca cerca y lejos del disco.
 * - `KPKN_VT6A_FLOOR=14` y `KPKN_VT6A_PCT=70`: con el barrido del rastreador, otro suelo de borde y otro percentil (§8 punto 1: 20 y 85),
 *   para ver su efecto con poco contraste (en ventanas con rack, el percentil manda y el suelo no pesa).
 */
class PlateTrackerSweepTest {
    private fun fmt(x: Double) = String.format(Locale.ROOT, "%.2f", x)

    /** Semillas de la variable KPKN_VT6A_SWEEP: «100-159» o «113,130-159» (listas y rangos). */
    private fun seeds(): List<Long> {
        val spec = System.getenv("KPKN_VT6A_SWEEP") ?: return emptyList()
        val out = ArrayList<Long>()
        for (part in spec.split(',')) {
            val range = part.trim().split('-')
            if (range.size == 2) {
                for (s in range[0].toLong()..range[1].toLong()) out.add(s)
            } else {
                out.add(range[0].toLong())
            }
        }
        return out
    }

    /** Barrido del detector con más semillas que las 50 de la aceptación: ¿cuántas fallan y por qué? */
    @Test
    fun detectorSweep() {
        val spec = System.getenv("KPKN_VT6A_DET_SWEEP")
        assumeTrue("barrido opcional: KPKN_VT6A_DET_SWEEP=51-170", spec != null)
        spec!!
        val range = spec.split('-')
        var bad = 0
        var total = 0
        val errors = ArrayList<Double>()
        for (seed in range[0].toLong()..range[1].toLong()) {
            total++
            val params = SyntheticPlateFrames.params(seed, SceneOptions())
            val rng = java.util.Random(seed * 17L + 3L)
            val r = params.equivalentRadiusPx
            val margin = 4.0 * r + 10.0
            val cx = margin + rng.nextDouble() * (720 - 2.0 * margin).coerceAtLeast(1.0)
            val cy = margin + rng.nextDouble() * (1280 - 2.0 * margin).coerceAtLeast(1.0)
            val scene = SyntheticPlateFrames.scene(params, StaticPath(cx, cy))
            val frame = scene.frame(0.0, 0)
            val near = Pair((cx + (rng.nextDouble() - 0.5) * 0.8 * r).toFloat(), (cy + (rng.nextDouble() - 0.5) * 0.8 * r).toFloat())
            val angle = rng.nextDouble() * 2.0 * Math.PI
            val farDistance = (1.5 + rng.nextDouble()) * r
            val far = Pair((cx + farDistance * Math.cos(angle)).toFloat(), (cy + farDistance * Math.sin(angle)).toFloat())
            val expected = (r * (0.85 + rng.nextDouble() * 0.3)).toFloat()
            val top = PlateDetector.propose(frame, listOf(near, far), expected).firstOrNull()
            val err = if (top == null) Double.NaN else Math.hypot(top.cx - cx, top.cy - cy) / (2.0 * r) * 100.0
            val onDistractor = top != null && scene.distractorR > 0.0 &&
                Math.hypot(top.cx - scene.distractorX, top.cy - scene.distractorY) < 0.7 * scene.distractorR
            if (top == null || err >= 2.0 || onDistractor) {
                bad++
                println(
                    "[barrido detector] semilla $seed FALLA: R=${fmt(r)} con=${fmt(params.contrast)} pol=${params.polarity} ruido=${fmt(params.noiseSigma)} " +
                        "→ ${if (top == null) "sin candidato" else "error ${fmt(err)} % de D, r=${fmt(top.r.toDouble())}"}${if (onDistractor) ", ES EL DISTRACTOR" else ""}",
                )
            } else {
                errors.add(err)
            }
        }
        println(
            "[barrido detector] $total semillas: $bad fallos (${fmt(100.0 * bad / total)} %) | error del centro de los acertados: mediana ${fmt(TrackRun.median(errors.toDoubleArray()))} " +
                "P95 ${fmt(TrackRun.percentile(errors.toDoubleArray(), 95.0))} % de D",
        )
    }

    @Test
    fun sweep() {
        assumeTrue("barrido opcional: KPKN_VT6A_SWEEP=100-159", System.getenv("KPKN_VT6A_SWEEP") != null)
        val medians = ArrayList<Double>()
        val p95s = ArrayList<Double>()
        val radiusMedians = ArrayList<Double>()
        val pooled = ArrayList<Double>()
        var bad = 0
        val list = seeds()
        // Experimento de umbrales: KPKN_VT6A_FLOOR=14 baja el suelo del borde (§8 p1: 20) para ver qué gana el contraste mínimo.
        val floor = System.getenv("KPKN_VT6A_FLOOR")?.toDouble() ?: PlateVisionConfig().edgeFloor
        val percentile = System.getenv("KPKN_VT6A_PCT")?.toDouble() ?: PlateVisionConfig().edgePercentile
        val config = PlateTrackerConfig(plateDiameterMm = 450, vision = PlateVisionConfig(edgeFloor = floor, edgePercentile = percentile))
        println("[barrido] umbral de bordes: suelo $floor, percentil $percentile")
        for (seed in list) {
            val ratio = when (seed % 4L) {
                0L -> 0.85
                1L -> 0.94
                else -> 1.0
            }
            val arm = seed % 5L == 0L
            val upright = seed % 7L == 0L
            val params = SyntheticPlateFrames.params(seed, SceneOptions(axisRatio = ratio, arm = arm, upright = upright))
            val path = SyntheticPlateFrames.repPath(params, 2)
            val scene = SyntheticPlateFrames.scene(params, path)
            val fps = if (seed % 2L == 0L) 30.0 else 60.0
            val run = PlateTrackingSupport.run(scene, fps, path.durationS, config)
            val all = run.centerErrors()
            pooled.addAll(all.toList())
            val med = TrackRun.median(all)
            val p95 = TrackRun.percentile(all, 95.0)
            val slow = ArrayList<Double>()
            val ratios = ArrayList<Double>()
            for (k in 0 until run.frames) {
                val s = run.samples[k]
                if (s.source != BarSampleSource.HOUGH || s.quality < 0.5f || run.speedPxPerS(k) >= 0.4 * run.trueRadius) continue
                slow.add(PlateTrackingSupport.relativePct(s.r.toDouble(), run.trueRadius))
                ratios.add(Math.abs(s.axisRatio - ratio))
            }
            val rMed = TrackRun.median(slow.toDoubleArray())
            val ratioErr = TrackRun.median(ratios.toDoubleArray())
            medians.add(med)
            p95s.add(p95)
            radiusMedians.add(rMed)
            val flag = if (med > 1.0 || p95 > 2.0 || rMed > 1.0 || ratioErr > 0.02) " <<<<<< FUERA" else ""
            if (flag.isNotEmpty()) bad++
            println(
                "[barrido] semilla $seed ${fps.toInt()} fps a/b=$ratio${if (arm) " brazo" else ""}${if (upright) " montante" else ""} R=${fmt(scene.params.radiusPx)} " +
                    "con=${fmt(scene.params.contrast)} ruido=${fmt(scene.params.noiseSigma)} exp=1/${Math.round(1.0 / scene.params.exposureS)}: centro med ${fmt(med)} P95 ${fmt(p95)} " +
                    "max ${fmt(all.maxOrNull() ?: Double.NaN)} % de D | radio med ${fmt(rMed)} % | a/b err med ${fmt(ratioErr)} | " +
                    BarSampleSource.entries.joinToString(" ") { src -> "${src.name.take(1)}=${run.samples.count { it.source == src }}" } + flag,
            )
            if (flag.isNotEmpty()) {
                // Traza de los fotogramas que no son HOUGH o que salen a más de 1 % de D: (k, fuente, q, error px).
                val lines = ArrayList<String>()
                for (k in 0 until run.frames) {
                    val s = run.samples[k]
                    val err = run.centerErrorPct(k)
                    if (s.source != BarSampleSource.HOUGH || err > 1.0) {
                        lines.add("k=$k ${s.source.name.take(1)} q=${fmt(s.quality.toDouble())} err=${fmt(err)}% v=${fmt(run.speedPxPerS(k))} px/s blur=${fmt(scene.blurPx(run.timesS[k]))}")
                    }
                }
                println("      fotogramas dudosos (${lines.size}): " + lines.take(60).joinToString(" | "))
            }
        }
        println(
            "[barrido] ${list.size} semillas: fuera de límites $bad | mediana de medianas ${fmt(TrackRun.median(medians.toDoubleArray()))} P95 de medianas ${fmt(TrackRun.percentile(medians.toDoubleArray(), 95.0))} | " +
                "P95 de P95 ${fmt(TrackRun.percentile(p95s.toDoubleArray(), 95.0))} máx ${fmt(p95s.maxOrNull() ?: Double.NaN)} | radio P95 ${fmt(TrackRun.percentile(radiusMedians.toDoubleArray(), 95.0))} | " +
                "TODOS los fotogramas juntos (${pooled.size}): mediana ${fmt(TrackRun.median(pooled.toDoubleArray()))} P95 ${fmt(TrackRun.percentile(pooled.toDoubleArray(), 95.0))} P99 ${fmt(TrackRun.percentile(pooled.toDoubleArray(), 99.0))} % de D",
        )
    }
}

package com.example.kpkn.domain.biomechanics.vision

import com.example.kpkn.domain.biomechanics.bar.BarSample
import com.example.kpkn.domain.biomechanics.bar.BarSampleSource
import com.example.kpkn.domain.biomechanics.bar.PlateSeed
import java.util.Locale
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToLong
import kotlin.math.sin

/** Una pasada del rastreador sobre una escena sintética, con la verdad de cada fotograma y el tiempo que tardó cada llamada. */
internal class TrackRun(
    val scene: PlateScene,
    val fps: Double,
    val samples: List<BarSample>,
    val timesS: DoubleArray,
    val truthX: DoubleArray,
    val truthY: DoubleArray,
    val millis: DoubleArray,
) {
    val frames: Int get() = samples.size

    /** Radio del círculo equivalente verdadero, √(a·b), y diámetro en píxeles. */
    val trueRadius: Double get() = scene.params.equivalentRadiusPx
    val diameterPx: Double get() = 2.0 * trueRadius

    fun isDetector(s: BarSample): Boolean = s.source == BarSampleSource.HOUGH || s.source == BarSampleSource.NCC

    /** Error del centro del fotograma [k], en % del diámetro. */
    fun centerErrorPct(k: Int): Double =
        hypot(samples[k].cx - truthX[k], samples[k].cy - truthY[k]) / diameterPx * 100.0

    /** Velocidad verdadera del disco (px/s) en el fotograma [k], por diferencia central de la verdad. */
    fun speedPxPerS(k: Int): Double {
        val a = (k - 1).coerceAtLeast(0)
        val b = (k + 1).coerceAtMost(frames - 1)
        if (b == a) return 0.0
        return hypot(truthX[b] - truthX[a], truthY[b] - truthY[a]) / abs(timesS[b] - timesS[a])
    }

    /** Errores del centro (% de D) de los fotogramas que cumplen [keep]. */
    fun centerErrors(keep: (Int) -> Boolean = { true }): DoubleArray {
        val out = ArrayList<Double>()
        for (k in 0 until frames) if (keep(k)) out.add(centerErrorPct(k))
        return out.toDoubleArray()
    }

    /** Resumen de una línea para el informe. */
    fun summary(label: String): String {
        val all = centerErrors()
        val det = centerErrors { isDetector(samples[it]) }
        val bySource = BarSampleSource.entries.joinToString(" ") { src -> "${src.name}=${samples.count { it.source == src }}" }
        val ms = millis.copyOf().also { it.sort() }
        return String.format(
            Locale.ROOT,
            "%s: %d fotogramas a %.0f fps, R=%.1f px (D=%.0f), centro %% de D: todos med %.2f P95 %.2f max %.2f | detector med %.2f P95 %.2f | %s | ms/fotograma med %.2f P95 %.2f max %.2f",
            label, frames, fps, trueRadius, diameterPx, median(all), percentile(all, 95.0), all.maxOrNull() ?: Double.NaN,
            median(det), percentile(det, 95.0), bySource, median(ms), percentile(ms, 95.0), ms.lastOrNull() ?: Double.NaN,
        )
    }

    companion object {
        fun median(values: DoubleArray): Double = percentile(values, 50.0)

        fun percentile(values: DoubleArray, p: Double): Double {
            if (values.isEmpty()) return Double.NaN
            val v = values.copyOf()
            v.sort()
            val rank = p / 100.0 * (v.size - 1)
            val lo = Math.floor(rank).toInt()
            val hi = Math.ceil(rank).toInt()
            return if (lo == hi) v[lo] else v[lo] + (v[hi] - v[lo]) * (rank - lo)
        }
    }
}

internal object PlateTrackingSupport {
    /**
     * Pasa el rastreador por [durationS] segundos de [scene] a [fps]. La semilla es el disco verdadero del primer fotograma con un
     * error de ≈ 2 % del radio, como si lo hubiera arrastrado el usuario. La muñeca cae a 0,3R del centro del disco, con ruido de
     * 0,03R y sin dato cada [wristDropEvery] fotogramas (0: siempre hay).
     */
    fun run(
        scene: PlateScene,
        fps: Double,
        durationS: Double,
        config: PlateTrackerConfig = PlateTrackerConfig(plateDiameterMm = 450),
        wristDropEvery: Int = 8,
        useWrist: Boolean = true,
        reverse: Boolean = false,
    ): TrackRun {
        val n = (durationS * fps).roundToLong().toInt()
        val rng = Random(scene.params.seed * 31L + 5L)
        val r = scene.params.equivalentRadiusPx
        // Con [reverse] los fotogramas van del último al primero: el tiempo decrece (la semilla es la del último instante).
        fun timeOf(k: Int): Double = if (reverse) (n - 1 - k) / fps else k / fps
        val t0 = timeOf(0)
        val seedX = scene.path.x(t0) + (rng.nextDouble() - 0.5) * 0.04 * r
        val seedY = scene.path.y(t0) + (rng.nextDouble() - 0.5) * 0.04 * r
        val seedR = r * (1.0 + (rng.nextDouble() - 0.5) * 0.04)
        val tracker = PlateTracker(PlateSeed((t0 * 1_000_000.0).roundToLong(), seedX.toFloat(), seedY.toFloat(), seedR.toFloat(), true), fps.toFloat(), config)
        val angle = rng.nextDouble() * 2.0 * PI
        val offX = 0.3 * r * cos(angle)
        val offY = 0.3 * r * sin(angle)

        val buffer = ByteArray(scene.width * scene.height)
        val frame = LumaFrame(scene.width, scene.height, buffer)
        val samples = ArrayList<BarSample>(n)
        val times = DoubleArray(n)
        val tx = DoubleArray(n)
        val ty = DoubleArray(n)
        val ms = DoubleArray(n)
        for (k in 0 until n) {
            val t = timeOf(k)
            scene.render(t, k, buffer)
            val wrist: Pair<Float, Float>? =
                if (!useWrist || (wristDropEvery > 0 && k % wristDropEvery == wristDropEvery - 1)) {
                    null
                } else {
                    Pair(
                        (scene.path.x(t) + offX + rng.nextGaussian() * 0.03 * r).toFloat(),
                        (scene.path.y(t) + offY + rng.nextGaussian() * 0.03 * r).toFloat(),
                    )
                }
            val start = System.nanoTime()
            val sample = tracker.track(frame, (t * 1_000_000.0).roundToLong(), wrist)
            ms[k] = (System.nanoTime() - start) / 1e6
            samples.add(sample)
            times[k] = t
            tx[k] = scene.path.x(t)
            ty[k] = scene.path.y(t)
        }
        return TrackRun(scene, fps, samples, times, tx, ty, ms)
    }

    /** Número de fotogramas HOUGH con error de centro ≤ [tolerancePct] entre [fromFrame] y [toFrame], para medir la readquisición. */
    fun firstGoodAfter(run: TrackRun, fromFrame: Int, tolerancePct: Double): Int {
        for (k in fromFrame until run.frames) {
            if (run.samples[k].source == BarSampleSource.HOUGH && run.centerErrorPct(k) <= tolerancePct) return k
        }
        return -1
    }

    /** Relativo |x − verdad|/verdad en %. */
    fun relativePct(value: Double, truth: Double): Double = abs(value - truth) / truth * 100.0
}

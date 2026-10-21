package com.example.kpkn.domain.biomechanics.vision

import com.example.kpkn.domain.biomechanics.bar.BarSampleSource
import com.example.kpkn.domain.biomechanics.bar.PlateSeed
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Milisegundos por fotograma del rastreador en la JVM, con fotogramas de 720×1280 (§8: menos de 8 ms al buscar solo en la ventana).
 * Se imprimen en el informe como orientación: el arnés corre con -Xmx1g y comparte la máquina, y la cifra que vale es la del teléfono.
 * Sin aserción dura salvo que la media quede por debajo de 30 ms.
 */
class PlateTrackerPerfTest {
    private fun fmt(x: Double) = String.format(Locale.ROOT, "%.2f", x)

    private fun timeTracker(frames: Array<LumaFrame>, scene: PlateScene, fps: Double, config: PlateTrackerConfig): DoubleArray {
        val r = scene.params.equivalentRadiusPx
        val tracker = PlateTracker(PlateSeed(0L, scene.path.x(0.0).toFloat(), scene.path.y(0.0).toFloat(), r.toFloat(), true), fps.toFloat(), config)
        val ms = DoubleArray(frames.size)
        for (k in frames.indices) {
            val t = k / fps
            val wrist = Pair((scene.path.x(t) + 0.3 * r).toFloat(), (scene.path.y(t) + 0.2 * r).toFloat())
            val start = System.nanoTime()
            tracker.track(frames[k], (t * 1_000_000.0).roundToLong(), wrist)
            ms[k] = (System.nanoTime() - start) / 1e6
        }
        return ms
    }

    /** Bytes asignados por el hilo actual, o `null` si esta JVM no los cuenta (se pide por reflexión: `com.sun.management` no es API estándar). */
    private fun allocatedBytes(): Long? = try {
        val bean = java.lang.management.ManagementFactory.getThreadMXBean()
        val type = Class.forName("com.sun.management.ThreadMXBean")
        if (!type.isInstance(bean)) {
            null
        } else {
            type.getMethod("getThreadAllocatedBytes", java.lang.Long.TYPE).invoke(bean, Thread.currentThread().id) as Long
        }
    } catch (e: Throwable) {
        null
    }

    @Test
    fun theTrackerDoesNotAllocateBuffersPerFrameInTheSteadyState() {
        val params = SyntheticPlateFrames.params(70L, SceneOptions(radiusPx = 90.0, contrast = 40.0, noiseSigma = 4.0, exposureS = 1.0 / 90.0))
        val path = SyntheticPlateFrames.repPath(params, reps = 1, leadS = 0.3, tailS = 0.3)
        val scene = SyntheticPlateFrames.scene(params, path)
        val fps = 30.0
        val frames = Array(150) { k -> scene.frame(k / fps, k) }
        val r = scene.params.equivalentRadiusPx
        val tracker = PlateTracker(PlateSeed(0L, scene.path.x(0.0).toFloat(), scene.path.y(0.0).toFloat(), r.toFloat(), true), fps.toFloat(), PlateTrackerConfig(plateDiameterMm = 450))
        // El propio contador asigna al pedirse por reflexión: se calibra con ventanas vacías y se resta.
        val empty = ArrayList<Long>()
        repeat(80) {
            val before = allocatedBytes()
            val after = allocatedBytes()
            if (before != null && after != null) empty.add(after - before)
        }
        if (empty.isEmpty()) {
            println("[rendimiento] esta JVM no cuenta los bytes asignados por hilo: no se mide")
            return
        }
        empty.sort()
        val overhead = empty[empty.size / 2]
        val perFrame = ArrayList<Long>()
        for (k in frames.indices) {
            val t = k / fps
            val wrist = Pair((scene.path.x(t) + 0.3 * r).toFloat(), (scene.path.y(t) + 0.2 * r).toFloat())
            val before = allocatedBytes()
            tracker.track(frames[k], (t * 1_000_000.0).roundToLong(), wrist)
            val after = allocatedBytes()
            if (before == null || after == null) return
            // Los primeros fotogramas crecen los buffers (gradientes, acumulador, perfiles, NCC): se miden los siguientes.
            if (k >= 50) perFrame.add(after - before - overhead)
        }
        perFrame.sort()
        val median = perFrame[perFrame.size / 2]
        val p90 = perFrame[(perFrame.size * 9) / 10]
        println(
            "[rendimiento] bytes asignados por fotograma en régimen (R=90 px, ${perFrame.size} fotogramas; el contador gasta $overhead B por medida y se resta): " +
                "mediana $median B, P90 $p90 B, máx ${perFrame.last()} B (la muestra de salida y el Pair de la muñeca de esta prueba son ≈ 110 B)",
        )
        assertTrue("mediana de $median B por fotograma (< 512): el rastreador asigna buffers por fotograma", median < 512)
    }

    @Test
    fun msPerFrameOfTheTrackerOnTheJvm() {
        var worstMean = 0.0
        for ((index, radius) in listOf(45.0, 90.0, 130.0).withIndex()) {
            val params = SyntheticPlateFrames.params(60L + index, SceneOptions(radiusPx = radius, contrast = 40.0, noiseSigma = 4.0, exposureS = 1.0 / 90.0))
            val path = SyntheticPlateFrames.repPath(params, reps = 1, leadS = 0.3, tailS = 0.3)
            val scene = SyntheticPlateFrames.scene(params, path)
            val fps = 30.0
            val n = 150
            val frames = Array(n) { k -> scene.frame(k / fps, k) }
            for ((label, config) in listOf(
                "con NCC" to PlateTrackerConfig(plateDiameterMm = 450),
                "sin NCC" to PlateTrackerConfig(plateDiameterMm = 450, nccEnabled = false),
            )) {
                // Dos pasadas de calentamiento (el JIT) y la tercera se mide.
                timeTracker(frames, scene, fps, config)
                timeTracker(frames, scene, fps, config)
                val ms = timeTracker(frames, scene, fps, config)
                val sorted = ms.copyOf().also { it.sort() }
                val mean = ms.average()
                worstMean = maxOf(worstMean, mean)
                println(
                    "[rendimiento] R=${fmt(radius)} px ($label, 720x1280, ${n} fotogramas): media ${fmt(mean)} ms, mediana ${fmt(TrackRun.median(ms))}, " +
                        "P95 ${fmt(TrackRun.percentile(ms, 95.0))}, máx ${fmt(sorted.last())} ms",
                )
            }
            // La fuente de cada muestra, para saber qué camino se midió.
            val r = scene.params.equivalentRadiusPx
            val tracker = PlateTracker(PlateSeed(0L, scene.path.x(0.0).toFloat(), scene.path.y(0.0).toFloat(), r.toFloat(), true), fps.toFloat())
            val counts = IntArray(BarSampleSource.entries.size)
            for (k in frames.indices) counts[tracker.track(frames[k], (k / fps * 1_000_000.0).roundToLong(), null).source.ordinal]++
            println("[rendimiento] R=${fmt(radius)} px: fuentes ${BarSampleSource.entries.joinToString(" ") { "${it.name}=${counts[it.ordinal]}" }}")
        }
        assertTrue("media de ${fmt(worstMean)} ms por fotograma (< 30)", worstMean < 30.0)
    }
}

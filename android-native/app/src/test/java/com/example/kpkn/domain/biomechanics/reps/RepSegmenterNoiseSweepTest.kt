package com.example.kpkn.domain.biomechanics.reps

import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.testing.SynthConfig
import com.example.kpkn.domain.biomechanics.testing.SynthMovement
import com.example.kpkn.domain.biomechanics.testing.SynthNoise
import com.example.kpkn.domain.biomechanics.testing.SynthTestSupport.repScript
import com.example.kpkn.domain.biomechanics.testing.SyntheticLifter
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Barrido de ruido (§10.2): σ de 0,002 a 0,012 FH por coordenada, con el 1 % de atípicos ×10, el 1 % de pérdidas y la
 * fluctuación del PTS de la captura típica, y 40 semillas por nivel. Imprime la tabla de aciertos («[barrido] …») y exige el
 * conteo exacto (5 completas, ni un parcial ni un fallo de más) en al menos el 95 % de las semillas con σ ≤ 0,008.
 *
 * Con la cámara por defecto (vertical, a 3 m) el cuerpo mide 0,25 FH: σ = 0,008 FH es un ruido de 0,03 BH por coordenada
 * contra los 0,28 BH de la bajada de la cadera en una sentadilla.
 */
class RepSegmenterNoiseSweepTest {
    private val sigmas = listOf(0.002, 0.004, 0.006, 0.008, 0.010, 0.012)
    private val seeds = 1L..40L

    private class Level(val sigma: Double, val exact: Int, val total: Int, val medianSnr: Double, val wrong: List<String>)

    private fun sweep(movement: SynthMovement, profileId: AnalysisProfileId): List<Level> {
        val script = repScript(5, 1.5, 0.5, 1.5, 0.5, lead = 2.0, tail = 2.0)
        val profile = RepTestSupport.profile(profileId)
        return sigmas.map { sigma ->
            var exact = 0
            val snrs = ArrayList<Double>()
            val wrong = ArrayList<String>()
            for (seed in seeds) {
                val r = SyntheticLifter.generate(
                    SynthConfig(movement, script = script, noise = SynthNoise().copy(sigmaFh = sigma), seed = seed),
                )
                val run = RepTestSupport.run(r, profile)
                val counts = run?.seg?.let { RepTestSupport.counts(it) }
                if (counts == Triple(5, 0, 0)) exact++ else wrong.add("semilla $seed: $counts")
                run?.seg?.let { if (it.noiseSigma > 0.0) snrs.add(it.aFull / it.noiseSigma) }
            }
            Level(sigma, exact, seeds.count(), snrs.sorted().let { if (it.isEmpty()) Double.NaN else it[it.size / 2] }, wrong)
        }
    }

    private fun print(title: String, levels: List<Level>) {
        for (l in levels) {
            println(
                String.format(
                    Locale.ROOT,
                    "[barrido] %s σ=%.3f FH: exactas %d/%d (%.1f %%), SNR mediana %.1f%s",
                    title, l.sigma, l.exact, l.total, 100.0 * l.exact / l.total, l.medianSnr,
                    if (l.wrong.isEmpty()) "" else " · fallos: " + l.wrong.take(6).joinToString("; "),
                ),
            )
        }
    }

    @Test
    fun squatCountIsExactInNinetyFivePercentOfTheSeedsUpToPoint008() {
        val levels = sweep(SynthMovement.SQUAT, AnalysisProfileId.SQUAT)
        print("sentadilla", levels)
        for (l in levels.filter { it.sigma <= 0.008 + 1e-9 }) {
            val needed = Math.ceil(0.95 * l.total).toInt()
            assertTrue(
                "σ=${l.sigma} FH: ${l.exact}/${l.total} exactas (hacen falta $needed). ${l.wrong.take(8)}",
                l.exact >= needed,
            )
        }
    }

    @Test
    fun deadliftAndPushUpSweepsArePrintedForReference() {
        // Otras dos señales (la Y del hombro, que lleva el doble de ruido que la media de las caderas): solo informativo,
        // salvo que con σ = 0,004 tampoco puede fallar.
        for ((movement, id) in listOf(SynthMovement.DEADLIFT to AnalysisProfileId.DEADLIFT, SynthMovement.PUSH_UP to AnalysisProfileId.PUSH_UP)) {
            val levels = sweep(movement, id)
            print(movement.name.lowercase(), levels)
            val low = levels.first { it.sigma == 0.004 }
            assertTrue("$movement σ=0,004: ${low.exact}/${low.total}. ${low.wrong.take(8)}", low.exact >= Math.ceil(0.95 * low.total).toInt())
        }
    }
}

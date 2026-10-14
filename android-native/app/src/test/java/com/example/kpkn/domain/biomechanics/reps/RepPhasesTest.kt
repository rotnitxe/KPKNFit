package com.example.kpkn.domain.biomechanics.reps

import com.example.kpkn.domain.biomechanics.metrics.RepKind
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfile
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.profiles.StartPhase
import com.example.kpkn.domain.biomechanics.signal.RobustStats
import com.example.kpkn.domain.biomechanics.testing.SynthConfig
import com.example.kpkn.domain.biomechanics.testing.SynthMovement
import com.example.kpkn.domain.biomechanics.testing.SynthNoise
import com.example.kpkn.domain.biomechanics.testing.SynthResult
import com.example.kpkn.domain.biomechanics.testing.SynthSegment
import com.example.kpkn.domain.biomechanics.testing.SynthTestSupport.repScript
import com.example.kpkn.domain.biomechanics.testing.SyntheticLifter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import kotlin.math.abs

/**
 * Fases, TUT y tempo (§6). Con el generador se usan flexiones (arranque E) y peso muerto (arranque C), cuya señal de conteo
 * es lineal en el progreso del movimiento; en la sentadilla, la altura de la cadera no lo es, y se mide aparte cuánto se
 * desvía. Los tiempos del guion son los de [SynthSegment.Rep]: EXC 2,0 s, P1 1,0 s, CON 1,0 s y P2 0,5 s.
 */
class RepPhasesTest {
    private fun gen(
        movement: SynthMovement,
        script: List<SynthSegment>,
        fps: Double = 30.0,
        noise: SynthNoise = SynthNoise(),
    ): SynthResult = SyntheticLifter.generate(SynthConfig(movement, fps = fps, script = script, noise = noise))

    private class Phased(val run: RepTestSupport.Run, val summary: PhaseSummary, val truth: SynthResult)

    private fun phased(movement: SynthMovement, id: AnalysisProfileId, script: List<SynthSegment>, fps: Double = 30.0, noise: SynthNoise = SynthNoise()): Phased {
        val truth = gen(movement, script, fps, noise)
        val run = RepTestSupport.run(truth, RepTestSupport.profile(id))!!
        return Phased(run, RepPhases.compute(run.series, run.seg, run.profile), truth)
    }

    private fun median(values: List<Int?>): Double = RobustStats.median(values.filterNotNull().map { it.toDouble() }.toDoubleArray())

    private fun frameMs(fps: Double) = 1000.0 / fps + 3.0

    private fun fmt(x: Double) = String.format(Locale.ROOT, "%.0f", x)

    /** [medianMs]: tolerancia de las medianas de las cinco repeticiones; [eventMs]: la de cada instante suelto. */
    private class Capture(val label: String, val fps: Double, val noise: SynthNoise, val medianMs: Double, val eventMs: Double)

    /**
     * Sin ruido, con poco ruido y con el ruido típico del generador. Sin ruido, a ±1 fotograma (más 3 ms de redondeo). Con
     * ruido manda la relación señal/ruido: la cota de Cramér–Rao de un tramo de jerk mínimo de 2 s da unos 40 ms de desviación
     * para su salida y su llegada con poco ruido y unos 70 ms con el ruido típico (en las flexiones, cuya señal mide solo el
     * 4 % del alto del fotograma; el peso muerto tiene el triple de amplitud). Las medianas de cinco repeticiones se piden a
     * ±100 y ±250 ms, y cada instante suelto a ±200 y ±450 ms.
     */
    private fun captures(): List<Capture> {
        val light = SynthNoise().copy(sigmaFh = 0.002, outlierRate = 0.0, dropRate = 0.0, ptsJitterMs = 0.0)
        return listOf(
            Capture("sin ruido, 30 fps", 30.0, SynthNoise.NONE, frameMs(30.0), 2 * frameMs(30.0)),
            Capture("sin ruido, 60 fps", 60.0, SynthNoise.NONE, frameMs(60.0), 2 * frameMs(60.0)),
            Capture("poco ruido, 30 fps", 30.0, light, 100.0, 200.0),
            Capture("ruido típico, 30 fps", 30.0, SynthNoise(), 250.0, 450.0),
            Capture("ruido típico, 60 fps", 60.0, SynthNoise().copy(ptsJitterMs = 1.0), 250.0, 450.0),
        )
    }

    @Test
    fun phasesMatchTheScriptWithinOneFrame() {
        val fails = RepTestSupport.Failures()
        val script = repScript(5, 2.0, 1.0, 1.0, 0.5, lead = 2.0, tail = 2.0)
        for ((movement, id, eccentricFirst) in listOf(
            Triple(SynthMovement.PUSH_UP, AnalysisProfileId.PUSH_UP, true),
            Triple(SynthMovement.DEADLIFT, AnalysisProfileId.DEADLIFT, false),
        )) {
            for (capture in captures()) {
                val p = phased(movement, id, script, capture.fps, capture.noise)
                val tol = capture.medianMs
                val name = "$movement, ${capture.label}"
                val s = p.summary
                // Excéntrica 2,0 s, concéntrica 1,0 s; la pausa del giro es P1 (arranque E) o P2 (arranque C); el reposo, al revés.
                val expectedTurn = if (eccentricFirst) 1000.0 else 500.0
                val expectedRest = if (eccentricFirst) 500.0 else 1000.0
                val ecc = median(s.reps.map { it.eccentricMs })
                val turn = median(s.reps.map { it.turnPauseMs })
                val con = median(s.reps.map { it.concentricMs })
                val rest = median(s.reps.map { it.restPauseMs })
                println("[fases] $name: EXC ${fmt(ecc)} giro ${fmt(turn)} CON ${fmt(con)} reposo ${fmt(rest)} (esperado 2000 / ${fmt(expectedTurn)} / 1000 / ${fmt(expectedRest)}, ±${fmt(tol)} ms)")
                fails.check(s.reps.size == 5) { "$name: ${s.reps.size} repeticiones con fases" }
                fails.check(abs(ecc - 2000.0) <= tol) { "$name: excéntrica $ecc ms" }
                fails.check(abs(turn - expectedTurn) <= tol) { "$name: pausa del giro $turn ms (esperada $expectedTurn)" }
                fails.check(abs(con - 1000.0) <= tol) { "$name: concéntrica $con ms" }
                fails.check(abs(rest - expectedRest) <= tol) { "$name: pausa de reposo $rest ms (esperada $expectedRest)" }
                // Los cuatro instantes de cada repetición, contra la verdad del generador (el doble de margen que una fase).
                var worstAll = 0.0
                for ((i, t) in p.truth.reps.withIndex()) {
                    val r = s.reps.getOrNull(i) ?: continue
                    val worst = maxOf(
                        abs(r.departUs - t.departUs), abs(r.turnStartUs - t.turnStartUs),
                        abs(r.turnEndUs - t.turnEndUs), abs(r.arriveUs - t.arriveUs),
                    ) / 1000.0
                    worstAll = maxOf(worstAll, worst)
                    fails.check(worst <= capture.eventMs) { "$name, repetición $i: un evento se desvía $worst ms de la verdad" }
                }
                println("[fases] $name: peor evento suelto ${fmt(worstAll)} ms")
            }
        }
        fails.assertNone()
    }

    /**
     * El umbral de 0,25 s es una regla sobre el tiempo medido, así que se prueba sin ruido: con ruido el hueco entre dos
     * tramos tiene una incertidumbre de ~0,1 s y una pausa de 0,2 o de 0,3 s caería a uno u otro lado al azar.
     */
    @Test
    fun pausesShorterThanAQuarterOfASecondAreZero() {
        val fails = RepTestSupport.Failures()
        val clean = SynthNoise.NONE
        val short = phased(SynthMovement.PUSH_UP, AnalysisProfileId.PUSH_UP, repScript(5, 2.0, 0.2, 1.0, 0.2, lead = 2.0, tail = 2.0), noise = clean)
        fails.check(short.summary.reps.all { it.turnPauseMs == 0 }) { "pausa de giro de 0,2 s: ${short.summary.reps.map { it.turnPauseMs }}" }
        fails.check(short.summary.reps.all { it.restPauseMs == null || it.restPauseMs == 0 }) {
            "pausa de reposo de 0,2 s: ${short.summary.reps.map { it.restPauseMs }}"
        }
        fails.check(short.summary.reps.all { it.turnStartUs == it.turnEndUs }) { "con pausa 0 el giro es un instante" }
        // Una pausa de 0,3 s sí existe.
        val brief = phased(SynthMovement.PUSH_UP, AnalysisProfileId.PUSH_UP, repScript(5, 2.0, 0.3, 1.0, 0.3, lead = 2.0, tail = 2.0), noise = clean)
        val turn = median(brief.summary.reps.map { it.turnPauseMs })
        fails.check(abs(turn - 300.0) <= frameMs(30.0)) { "pausa de giro de 0,3 s: $turn ms" }
        val rest = median(brief.summary.reps.map { it.restPauseMs })
        fails.check(abs(rest - 300.0) <= frameMs(30.0)) { "pausa de reposo de 0,3 s: $rest ms" }
        fails.assertNone()
    }

    @Test
    fun theTimeUnderTensionLeavesOutTheRestAtTheStartPosition() {
        val fails = RepTestSupport.Failures()
        val flexiones = repScript(5, 2.0, 1.0, 1.0, 0.5, lead = 2.0, tail = 2.0)
        // Sin ruido las definiciones salen exactas: TUT = 5 × (2 + 1 + 1) s, reposo = 4 × 0,5 s y TUT + reposo = la serie.
        val exact = phased(SynthMovement.PUSH_UP, AnalysisProfileId.PUSH_UP, flexiones, noise = SynthNoise.NONE).summary
        fails.check(abs((exact.tutMs ?: -1L) - 20_000L) <= 40L) { "TUT sin ruido ${exact.tutMs} (esperado 20000)" }
        fails.check(abs((exact.setDurationMs ?: -1L) - 22_000L) <= 40L) { "duración de la serie sin ruido ${exact.setDurationMs} (esperado 22000)" }
        fails.check(abs((exact.restPositionMs ?: -1L) - 2_000L) <= 40L) { "reposo sin ruido ${exact.restPositionMs} (esperado 2000)" }
        fails.check((exact.tutMs ?: 0L) + (exact.restPositionMs ?: 0L) in ((exact.setDurationMs ?: 0L) - 20)..((exact.setDurationMs ?: 0L) + 20)) {
            "TUT + reposo no suma la duración de la serie"
        }
        // La última repetición con arranque E no tiene pausa de reposo propia.
        fails.check(exact.reps.last().restPauseMs == null) { "la última repetición no debería tener pausa de reposo" }

        // Con el ruido típico de las flexiones (la señal mide el 4 % del alto del fotograma) cada tramo sale unos 0,1 s más
        // ancho de lo que es: el TUT, suma de cinco repeticiones, sale un 2 a un 3 % alto, y el reposo entre repeticiones
        // (cuatro huecos de 0,5 s con una incertidumbre de 0,1 s, y los menores de 0,25 s valen 0) sale un 30 % bajo.
        val s = phased(SynthMovement.PUSH_UP, AnalysisProfileId.PUSH_UP, flexiones).summary
        println("[fases] TUT ${s.tutMs} ms, serie ${s.setDurationMs} ms, reposo ${s.restPositionMs} ms")
        fails.check(abs((s.tutMs ?: -1L) - 20_000L) <= 800L) { "TUT ${s.tutMs} (esperado 20000)" }
        fails.check(abs((s.setDurationMs ?: -1L) - 22_000L) <= 400L) { "duración de la serie ${s.setDurationMs} (esperado 22000)" }
        fails.check(abs((s.restPositionMs ?: -1L) - 2_000L) <= 1_000L) { "reposo en la posición inicial ${s.restPositionMs} (esperado 2000)" }

        // Rest-pause: los descansos largos entre mini-series no entran ni en el TUT ni en el reposo entre repeticiones.
        val rep = SynthSegment.Rep(2.0, 1.0, 1.0, 0.5)
        val script = listOf<SynthSegment>(SynthSegment.Idle(2.0)) + List(3) { rep } + SynthSegment.Rest(12.0) +
            List(2) { rep } + SynthSegment.Rest(12.0) + List(3) { rep } + SynthSegment.Idle(2.0)
        val rp = phased(SynthMovement.PUSH_UP, AnalysisProfileId.PUSH_UP, script)
        val r = rp.summary
        println("[fases] rest-pause: TUT ${r.tutMs} ms, serie ${r.setDurationMs} ms, reposo ${r.restPositionMs} ms")
        fails.check(rp.run.seg.miniSets == 3) { "mini-series ${rp.run.seg.miniSets}" }
        fails.check(abs((r.tutMs ?: -1L) - 32_000L) <= 1_000L) { "TUT del rest-pause ${r.tutMs} (esperado 32000)" }
        fails.check((r.setDurationMs ?: 0L) >= 56_000L) { "duración del rest-pause ${r.setDurationMs}" }
        fails.check(abs((r.restPositionMs ?: -1L) - 2_500L) <= 1_000L) { "reposo del rest-pause ${r.restPositionMs} (esperado 2500, sin los descansos largos)" }
        fails.check(listOf(2, 4, 7).all { r.reps[it].restPauseMs == null }) { "pausas de reposo en los cortes: ${r.reps.map { it.restPauseMs }}" }
        fails.assertNone()
    }

    @Test
    fun theTempoIsTheMedianOfTheFullRepsAndLabelsLikeThis() {
        val fails = RepTestSupport.Failures()
        val e = phased(SynthMovement.PUSH_UP, AnalysisProfileId.PUSH_UP, repScript(5, 2.0, 1.0, 1.0, 0.0, lead = 2.0, tail = 2.0))
        fails.check(e.summary.tempo?.label() == "2-1-1-0") { "tempo de flexiones: ${e.summary.tempo} ${e.summary.tempo?.label()}" }
        // La etiqueta con decimales sale al décimo de segundo: sin ruido, exacta.
        val exact = phased(SynthMovement.PUSH_UP, AnalysisProfileId.PUSH_UP, repScript(5, 2.0, 1.0, 1.0, 0.0, lead = 2.0, tail = 2.0), noise = SynthNoise.NONE)
        fails.check(exact.summary.tempo?.detailLabel() == "2,0-1,0-1,0-0,0") { "tempo detallado de flexiones: ${exact.summary.tempo?.detailLabel()}" }
        // Arranque C: excéntrica, pausa de reposo (P1), concéntrica y pausa arriba (P2).
        val c = phased(SynthMovement.DEADLIFT, AnalysisProfileId.DEADLIFT, repScript(5, 2.0, 1.0, 1.0, 0.0, lead = 2.0, tail = 2.0))
        fails.check(c.summary.tempo?.label() == "2-1-1-0") { "tempo de peso muerto: ${c.summary.tempo} ${c.summary.tempo?.label()}" }
        // Una concéntrica de 0,4 s es «X».
        val x = phased(SynthMovement.PUSH_UP, AnalysisProfileId.PUSH_UP, repScript(5, 2.0, 1.0, 0.4, 0.0, lead = 2.0, tail = 2.0))
        fails.check(x.summary.tempo?.label() == "2-1-X-0") { "tempo con concéntrica explosiva: ${x.summary.tempo?.label()}" }
        fails.assertNone()
    }

    @Test
    fun theSquatHipSignalIsNotLinearSoItsPhasesAreOnlyApproximate() {
        val p = phased(SynthMovement.SQUAT, AnalysisProfileId.SQUAT, repScript(5, 2.0, 1.0, 1.0, 0.5, lead = 2.0, tail = 2.0))
        val s = p.summary
        val ecc = median(s.reps.map { it.eccentricMs })
        val turn = median(s.reps.map { it.turnPauseMs })
        val con = median(s.reps.map { it.concentricMs })
        println("[fases] sentadilla (cadera): EXC ${fmt(ecc)} giro ${fmt(turn)} CON ${fmt(con)} (esperado 2000 / 1000 / 1000)")
        assertEquals(5, s.reps.size)
        // La altura de la cadera no es lineal en el progreso del movimiento del generador, así que el tramo de jerk mínimo que
        // mejor la ajusta no coincide con el del guion: los tiempos se desvían hasta 0,4 s, y se piden a ±0,6 s.
        assertTrue("excéntrica $ecc", abs(ecc - 2000.0) <= 600.0)
        assertTrue("pausa del giro $turn", abs(turn - 1000.0) <= 600.0)
        assertTrue("concéntrica $con", abs(con - 1000.0) <= 600.0)
    }

    @Test
    fun belowTwentyFpsThereAreNoPhases() {
        val truth = gen(SynthMovement.SQUAT, repScript(5, 2.0, 1.0, 1.0, 0.5, lead = 2.0, tail = 2.0), fps = 15.0)
        val run = RepTestSupport.run(truth, RepTestSupport.profile(AnalysisProfileId.SQUAT))!!
        val s = RepPhases.compute(run.series, run.seg, run.profile)
        assertTrue(s.reps.isEmpty())
        assertNull(s.tutMs)
        assertNull(s.setDurationMs)
        assertNull(s.restPositionMs)
        assertNull(s.tempo)
    }

    // ---- sobre señales a mano, sin ruido ni pose: la exactitud del ajuste en sí ----

    private fun exactProfile(start: StartPhase): AnalysisProfile =
        if (start == StartPhase.ECCENTRIC_FIRST) RepTestSupport.eccentricProfile() else RepTestSupport.concentricProfile()

    @Test
    fun handMadeSignalsGiveTheScriptToTheSample() {
        val fails = RepTestSupport.Failures()
        for (start in StartPhase.entries) {
            // up = 1,0 s, arriba 1,0 s, down = 2,0 s, descanso 0,5 s. Con arranque E la subida de r es la excéntrica.
            val b = SignalBuilder().hold(2.0)
            repeat(5) { b.rep(1.0, up = 1.0, top = 1.0, down = 2.0, rest = 0.5) }
            b.hold(1.5)
            val profile = exactProfile(start)
            val series = b.series()
            val seg = RepSegmenter.segment(series, profile, emptySet())
            val s = RepPhases.compute(series, seg, profile)
            val eccentricFirst = start == StartPhase.ECCENTRIC_FIRST
            val ecc = median(s.reps.map { it.eccentricMs })
            val con = median(s.reps.map { it.concentricMs })
            val turn = median(s.reps.map { it.turnPauseMs })
            val rest = median(s.reps.map { it.restPauseMs })
            println("[fases] a mano, $start: EXC ${fmt(ecc)} CON ${fmt(con)} giro ${fmt(turn)} reposo ${fmt(rest)}")
            fails.check(seg.fullCount == 5 && s.reps.size == 5) { "$start: ${RepTestSupport.summary(seg)}" }
            fails.check(abs(ecc - (if (eccentricFirst) 1000.0 else 2000.0)) <= 20.0) { "$start: excéntrica $ecc" }
            fails.check(abs(con - (if (eccentricFirst) 2000.0 else 1000.0)) <= 20.0) { "$start: concéntrica $con" }
            fails.check(abs(turn - 1000.0) <= 20.0) { "$start: pausa del giro $turn" }
            fails.check(abs(rest - 500.0) <= 20.0) { "$start: pausa de reposo $rest" }
            // Con arranque E la pausa de reposo es la de después de cada repetición; con C, la de antes.
            fails.check((s.reps.last().restPauseMs == null) == eccentricFirst) { "$start: pausa de reposo de la última ${s.reps.last().restPauseMs}" }
            fails.check((s.reps.first().restPauseMs == null) == !eccentricFirst) { "$start: pausa de reposo de la primera ${s.reps.first().restPauseMs}" }
        }
        fails.assertNone()
    }

    @Test
    fun failedAndPartialRepsAddToTheTutButNotToTheTempo() {
        val b = SignalBuilder().hold(2.0)
        repeat(4) { b.rep(1.0, up = 1.5, top = 0.5, down = 1.5, rest = 0.5) }
        // Un intento fallido: sube a la mitad en 3 s, se queda 0,8 s y baja en 1,5 s.
        b.go(3.0, 0.5).hold(0.8).go(1.5, 0.0).hold(2.0)
        val series = b.series()
        val profile = RepTestSupport.concentricProfile()
        val seg = RepSegmenter.segment(series, profile, emptySet())
        val s = RepPhases.compute(series, seg, profile)
        assertEquals(RepTestSupport.summary(seg), listOf(RepKind.FULL, RepKind.FULL, RepKind.FULL, RepKind.FULL, RepKind.FAILED), seg.cycles.map { it.kind })
        // 4 × (1,5 + 0,5 + 1,5) + (3,0 + 0,8 + 1,5) = 19,3 s
        assertEquals(19_300.0, s.tutMs!!.toDouble(), 120.0)
        // El tempo sale solo de las FULL (arranque C): EXC 1,5 s, reposo 0,5 s, CON 1,5 s y pausa arriba 0,5 s.
        val tempo = s.tempo!!
        assertEquals(1500.0, tempo.eccentricMs!!.toDouble(), 40.0)
        assertEquals(500.0, tempo.pauseAfterEccentricMs!!.toDouble(), 40.0)
        assertEquals(1500.0, tempo.concentricMs!!.toDouble(), 40.0)
        assertEquals(500.0, tempo.pauseAfterConcentricMs!!.toDouble(), 40.0)
    }
}

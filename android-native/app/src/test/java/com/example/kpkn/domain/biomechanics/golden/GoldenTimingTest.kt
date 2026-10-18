package com.example.kpkn.domain.biomechanics.golden

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoldenTimingTest {
    @Test
    fun timingMatcherDoesNotReuseTurnsOrCrossLateralities() {
        val labels = listOf(GoldenTurn(1_000_000, "left", null), GoldenTurn(2_000_000, "right", null))
        val wrongSide = listOf(GoldenTurn(1_000_000, "right", null), GoldenTurn(2_000_000, "left", null))
        assertEquals(1, GoldenReport.matchTurns(labels, wrongSide).size)
        assertEquals(listOf(0 to 0), GoldenReport.matchTurns(labels, listOf(GoldenTurn(1_050_000, "left", null))))
    }

    @Test
    fun availableReliableTuningTurnsProduceDiagnosticReport() {
        val corpus = GoldenReport.load()
        val fixtures = GoldenReport.tuningFixtures(corpus).filter { it.id.substringBefore('-') in GoldenReport.RELIABLE_TIMING_PREFIXES }
        val results = GoldenReport.evaluateAvailable(corpus, fixtures)
        GoldenReport.publish("GOLDEN_TIMING_TUNING.md", GoldenReport.render(corpus, results, "VT10 · giros fiables del ajuste; sin aceptación"))
        assertTrue(results.none { it.fixture.subset == "holdout" })
    }

    /** Mismo cierre que conteo: no ejecutar durante el ajuste; exige el banco completo antes de leer reserva. */
    @Test
    fun completeReservedReliableTurnsAreWithin250Milliseconds() {
        val corpus = GoldenReport.load()
        assertTrue("Banco incompleto: no evaluar reserva temporal", GoldenReport.missingTraces(corpus).isEmpty())
        val results = GoldenReport.finalHoldout(corpus).filter { it.fixture.id.substringBefore('-') in GoldenReport.RELIABLE_TIMING_PREFIXES }
        val timing = results.filter(GoldenReport::timingEligible).mapNotNull(GoldenReport::timing)
        GoldenReport.publish("GOLDEN_TIMING_HOLDOUT.md", GoldenReport.render(corpus, results, "VT10 · giros finales de reserva", holdoutEvaluation = true))
        assertTrue("No hay eventos temporales fiables elegibles", timing.sumOf { it.expected } > 0)
        val errors = buildList {
            for (row in timing) {
                if (row.unmatchedExpected > 0 || row.unmatchedDetected > 0) add("${row.id}: sin pareja etiqueta/motor ${row.unmatchedExpected}/${row.unmatchedDetected}")
                if (row.errorsUs.any { it > GoldenReport.TIMING_TOLERANCE_US }) add("${row.id}: giro fuera de 250 ms; máximo ${row.errorsUs.maxOrNull()} µs")
            }
        }
        assertTrue(errors.joinToString("\n"), errors.isEmpty())
    }
}

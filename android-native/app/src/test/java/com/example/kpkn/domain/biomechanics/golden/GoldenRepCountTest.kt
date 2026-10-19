package com.example.kpkn.domain.biomechanics.golden

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** El diagnóstico parcial no reemplaza al gate completo ni consume la reserva. */
class GoldenRepCountTest {
    @Test
    fun pilotTracesReplayWithoutDatasetAcceptance() {
        val corpus = GoldenReport.load()
        val fixtures = corpus.fixtures.filter { it.id in GoldenReport.PILOT_IDS }
        assertEquals(3, fixtures.size)
        assertTrue("Faltan trazas piloto", fixtures.all { GoldenReport.isExtracted(corpus, it) })
        val results = GoldenReport.evaluateAvailable(corpus, fixtures)
        assertEquals(3, results.size)
        assertTrue(results.all { it.fixture.subset == "tuning" })
        assertTrue(corpus.fixtures.single { it.id == GoldenReport.PREEXISTING_SMOKE }.preexistingSmoke)
        GoldenReport.publish("GOLDEN_PILOT.md", GoldenReport.render(corpus, results, "VT10 · humo de tres trazas; sin aceptación"))
    }

    @Test
    fun availableTuningFixturesProduceDiagnosticReport() {
        val corpus = GoldenReport.load()
        val results = GoldenReport.evaluateAvailable(corpus, GoldenReport.tuningFixtures(corpus))
        assertTrue("No hay trazas de ajuste para evaluar", results.isNotEmpty())
        assertTrue(results.none { it.fixture.subset == "holdout" })
        GoldenReport.publish("GOLDEN_TUNING.md", GoldenReport.render(corpus, results, "VT10 · diagnóstico del reparto de ajuste"))
    }

    /** Ejecutar UNA vez al final; deliberadamente falla si se intenta aceptar un banco parcial. */
    @Test
    fun completeFrozenCompatibleManifestMeetsAcceptance() {
        val corpus = GoldenReport.load()
        val missing = GoldenReport.missingTraces(corpus)
        assertTrue("No se puede aceptar el banco con ${corpus.fixtures.size - missing.size}/97 extracciones. Faltan: ${missing.joinToString()}", missing.isEmpty())
        val holdout = corpus.fixtures.filter { it.subset == "holdout" && !it.preexistingSmoke }
        assertEquals("x14 ya fue observado: la reserva ciega tiene 37 clips", 37, holdout.size)
        val results = GoldenReport.finalHoldout(corpus)
        val text = GoldenReport.render(corpus, results, "VT10 · evaluación final de reserva compatible", finalGate = true)
        GoldenReport.publish("GOLDEN_REPORT.md", text)
        val errors = GoldenReport.countGateErrors(results)
        assertTrue(errors.joinToString("\n"), errors.isEmpty())
    }
}

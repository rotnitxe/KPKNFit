package com.example.kpkn.domain.biomechanics.golden

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Kaggle/UCF101 se leen exclusivamente fuera del repo; sin variable de entorno se omite esta prueba. */
class GoldenCorpusTest {
    @Test
    fun availableExternalTracesProduceSeparateDiagnosticReport() {
        val directory = System.getenv("KPKN_GOLDEN_CORPUS_DIR")
        assumeTrue("Corpus externo opcional: configura KPKN_GOLDEN_CORPUS_DIR", !directory.isNullOrBlank())
        val corpus = GoldenReport.load(File(requireNotNull(directory)), external = true)
        val results = GoldenReport.evaluateAvailable(corpus, corpus.fixtures)
        assertTrue("La ruta externa está configurada pero no contiene trazas verificadas", results.isNotEmpty())
        assertTrue(results.all { it.fixture.source in GoldenReport.EXTERNAL_SOURCES })
        GoldenReport.publish("GOLDEN_EXTERNAL.md", GoldenReport.render(corpus, results, "VT10 · corpus externo; Kaggle aislado y UCF por subconjunto"))
    }
}

package com.example.kpkn.data.importer

import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2Loader
import com.example.kpkn.domain.training.importer.ExerciseNameMatcher
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Informe sobre el corpus real de hojas de la comunidad. Solo corre con KPKN_IMPORT_CORPUS (si no, se salta).
 * El corpus no es del repositorio: el informe va a KPKN_IMPORT_REPORT, fuera del proyecto.
 */
class ImportCorpusReportTest {

    @Test
    fun `informe del corpus real con el importador de la app`() {
        val corpus = System.getenv("KPKN_IMPORT_CORPUS")
        assumeTrue("sin KPKN_IMPORT_CORPUS no hay corpus que leer", corpus != null)
        val reportDir = requireNotNull(System.getenv("KPKN_IMPORT_REPORT")) {
            "KPKN_IMPORT_REPORT es obligatoria cuando hay corpus"
        }
        val catalog = ExerciseCatalogV2Loader.decodeApproved(File("src/main/assets/exercise_catalog_v2.json").readText())

        val report = ImportCorpusReporter(ExerciseNameMatcher(catalog)).run(File(corpus!!), File(reportDir))

        // El informe ya está escrito; solo un FALLO (excepción no esperada) hace fallar la prueba.
        val failures = report.summary.failures
        assertTrue("Fallos del importador (ver $reportDir/report.md):\n" + failures.joinToString("\n"), failures.isEmpty())
    }
}

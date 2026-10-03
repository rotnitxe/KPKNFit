package com.example.kpkn.telemetry

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kpkn.KpknApplication
import com.example.kpkn.data.diagnostics.KpknDiagnosticLogger
import com.example.kpkn.telemetry.nutrition.NutritionTelemetry
import java.io.File
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TelemetryIntegrationTest {

    /**
     * `KpknApplication.onCreate` (proceso principal) inicializa
     * `NutritionTelemetry` antes de que exista cualquier pantalla. Se comprueba
     * sobre la Application real del proceso instrumentado, sin Compose: el
     * estado de inicialización y que un evento de nutrición emitido después
     * del arranque llega de verdad al bus JSONL central (área `nutrition`).
     */
    @Test
    fun app_should_initialize_telemetry_on_startup() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        assertTrue("la Application del proceso no es KpknApplication: ${app.javaClass.name}", app is KpknApplication)
        assertTrue(
            "NutritionTelemetry no se inicializó en KpknApplication.onCreate",
            NutritionTelemetry.isInitialized(),
        )
        assertTrue("la telemetría de nutrición debe estar siempre activa", NutritionTelemetry.isEnabled())

        val probe = "qa_startup_probe_${System.nanoTime()}"
        NutritionTelemetry.event(probe, mapOf("source" to "TelemetryIntegrationTest"))
        assertTrue("el escritor JSONL no quedó inactivo", KpknDiagnosticLogger.awaitIdle())
        val recorded = KpknDiagnosticLogger.filesForArea(app, "nutrition")
            .takeLast(PROBE_SCAN_FILES)
            .any { file -> file.readText(Charsets.UTF_8).contains(probe) }
        assertTrue("el evento $probe no llegó al área nutrition del bus central", recorded)
    }

    @Test
    fun telemetry_helper_should_be_accessible_from_app_context() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val root = File(context.filesDir, KpknDiagnosticLogger.LOG_ROOT)
        assertTrue(root.isDirectory)
        KpknDiagnosticLogger.officialAreas.forEach { area ->
            assertTrue("missing canonical area $area", File(root, area).isDirectory)
        }
        assertTrue(KpknDiagnosticLogger.awaitIdle())
    }

    private companion object {
        /** El evento de la sonda cae en uno de los archivos más recientes (rotación de 1 MB por archivo). */
        const val PROBE_SCAN_FILES = 5
    }
}

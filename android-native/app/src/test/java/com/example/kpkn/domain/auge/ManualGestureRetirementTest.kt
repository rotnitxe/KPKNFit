package com.example.kpkn.domain.auge

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guarda de retirada (paquete 3.3): la inversión sesgada (`deriveImpliedRecoveryTime`), el α global del EMA, el tope de
 * ±25 % (`capObservationTau`) y los actualizadores EMA manuales ya no existen en producción. La referencia vive SOLO en
 * pruebas (`LegacyEmaReference`). Lee el texto de las fuentes de producción, sin comentarios, como el escaneo de
 * `AugeParametersConsistencyTest`.
 */
class ManualGestureRetirementTest {

    private val prohibidos = listOf(
        "deriveImpliedRecoveryTime",
        "capObservationTau",
        "OBSERVATION_TAU_RATIO",
        "updatePersonalizedRecoveryHours",
        "updateSystemRecoveryHours",
        "learnFromManualAdjustment",
        // α global del EMA: alpha = max(0.05, min(0.5, 1.5 / samples)).
        "1.5 / samples",
        "min(0.5, 1.5",
    )

    private fun raizDeProduccion(): File {
        val relativo = "src/main/java/com/example/kpkn"
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            listOf(File(dir, relativo), File(dir, "app/$relativo")).firstOrNull { it.isDirectory }?.let { return it }
            dir = dir.parentFile
        }
        error("No encuentro $relativo subiendo desde ${File("").absolutePath}")
    }

    private fun sinComentarios(texto: String): String =
        texto.replace(Regex("""/\*[\s\S]*?\*/"""), " ").lines().joinToString("\n") { it.substringBefore("//") }

    @Test
    fun produccionNoConservaLaInversionSesgadaNiLosActualizadoresEma() {
        val hallazgos = raizDeProduccion().walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { archivo ->
                val limpio = sinComentarios(archivo.readText(Charsets.UTF_8))
                prohibidos.filter { it in limpio }.map { "${archivo.name}: $it" }.asSequence()
            }
            .toList()
        assertTrue("Quedan restos del aprendiz sesgado en producción: $hallazgos", hallazgos.isEmpty())
    }

    @Test
    fun laReferenciaHistoricaSigueDisponibleSoloParaLasPruebas() {
        val raizDePruebas = File(raizDeProduccion().path.replace("src/main", "src/test").replace("src\\main", "src\\test"))
        val referencia = File(raizDePruebas, "domain/auge/LegacyEmaReference.kt")
        assertTrue("falta la referencia congelada en pruebas: $referencia", referencia.isFile)
        val texto = referencia.readText(Charsets.UTF_8)
        assertTrue("deriveImpliedRecoveryTime" in texto && "capObservationTau" in texto)
    }
}

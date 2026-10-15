package com.example.kpkn.screens.workout

import com.example.kpkn.domain.relator.RELATOR_LINE_MAX_CHARS
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prueba de frases del catálogo del relator (LR1g, B-7): recorre todas las plantillas de [WorkoutLiveRelatorCatalog] y
 * aplica las mismas reglas que la prueba de frases de dominio.
 */
class RelatorCatalogPhraseRulesTest {

    private val jerga = listOf("timer", "recobro", "recetario", "tal cual", "overlay", "tarjeta")
    private val rotulos = listOf("Ahora", "Ojo", "Dato", "Plan", "Siguiente", "Hoy", "Carga", "Recuerda")
    private val cifraPegadaS = Regex("\\d+s\\b")
    private val xSinEspacios = Regex("\\d×|×\\d|\\dx\\d")
    private val kgSinEspacioDuro = Regex("\\d kg")

    private fun frases(): List<String> {
        val snap = snapshotDePrueba()
        return RelatorSpeechBucket.entries.flatMap { bucket ->
            WorkoutLiveRelatorCatalog.variantsFor(bucket, snap)
        }.map { renderRelatorTemplate(it, snap.shortExerciseName(), RelatorVoice.of(false)) }
    }

    @Test
    fun catalogo_sin_jerga_interna() {
        val fallos = frases().filter { frase -> jerga.any { frase.contains(it, ignoreCase = true) } }
        assertTrue("Jerga en: $fallos", fallos.isEmpty())
    }

    @Test
    fun catalogo_sin_cifras_pegadas_a_la_s() {
        val fallos = frases().filter { cifraPegadaS.containsMatchIn(it) }
        assertTrue("Cifra pegada a la s en: $fallos", fallos.isEmpty())
    }

    @Test
    fun catalogo_con_series_y_kilos_bien_escritos() {
        val fallos = frases().filter { xSinEspacios.containsMatchIn(it) || kgSinEspacioDuro.containsMatchIn(it) }
        assertTrue("Series o kilos mal escritos en: $fallos", fallos.isEmpty())
    }

    @Test
    fun catalogo_con_un_dos_puntos_como_mucho() {
        val fallos = frases().filter { it.split(": ").size - 1 > 1 }
        assertTrue("Dos «: » en: $fallos", fallos.isEmpty())
    }

    @Test
    fun catalogo_sin_rotulos_de_la_lista_vieja() {
        val fallos = frases().filter { frase -> rotulos.any { frase.startsWith("$it:") } }
        assertTrue("Rótulo en: $fallos", fallos.isEmpty())
    }

    @Test
    fun catalogo_cabe_en_90_caracteres() {
        val fallos = frases().filter { it.length > RELATOR_LINE_MAX_CHARS }
        assertTrue("Frases largas: $fallos", fallos.isEmpty())
    }

    /** Snapshot de una serie de press con historial, el mismo que usa la prueba de presupuesto de variantes. */
    private fun snapshotDePrueba(): LiveRelatorSnapshot = LiveRelatorSnapshot(
        visible = true,
        phase = RelatorPhase.WORKING,
        family = RelatorFamily.PRESS,
        exerciseDisplayName = "Press banca",
        setIndex = 1,
        setCount = 3,
        hasHistory = true,
        lastChangedField = RelatorChangedField.WEIGHT,
        enteredWeight = 70.0,
        enteredWeightRaw = "70",
        suggestedWeight = 80.0,
        lastLiftedWeight = 80.0,
        plannedReps = 10.0,
        plannedIntensity = 8.0,
        sessionLastSet = RelatorSessionSetMemory(1, 80.0, 8),
        idleCycle = 1,
        axialLoadFactor = 1.0,
    )
}

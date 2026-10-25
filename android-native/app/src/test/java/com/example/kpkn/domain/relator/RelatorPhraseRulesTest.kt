package com.example.kpkn.domain.relator

import com.example.kpkn.domain.concepts.relatorConceptLines
import com.example.kpkn.data.protocols.ProgressionRule
import com.example.kpkn.data.protocols.TechniqueModifier
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prueba de frases (LR1g, B): recorre las plantillas de los observadores y falla si alguna lleva una palabra de jerga,
 * una cifra pegada a la «s», «x» o «×» sin espacios, dos «: », un rótulo de la lista vieja o más de 90 caracteres.
 */
class RelatorPhraseRulesTest {

    private val jerga = listOf("timer", "recobro", "recetario", "tal cual", "overlay", "tarjeta")
    private val prefijoRotulo = Regex("^\\p{L}+( \\p{L}+){0,2}: ")
    private val rotulos = listOf("Ahora", "Ojo", "Dato", "Plan", "Siguiente", "Hoy", "Carga", "Recuerda")
    private val cifraPegadaS = Regex("\\d+s\\b")
    private val xSinEspacios = Regex("\\d×|×\\d|\\dx\\d")
    private val kgSinEspacioDuro = Regex("\\d kg")

    /** Contextos con las ramas encendidas, para que cada observador diga todas sus frases. */
    private fun contextosCompletos(): List<RelatorContext> = listOf(
        relatorContext(
            restActive = true,
            prJustNow = true,
            mediaCount = 2,
            conceptId = "concepto",
            conceptLines = listOf("Mantén la espalda neutra"),
            executionCues = listOf("Baja despacio"),
            coachBody = "Baja el peso hoy",
        ),
        relatorContext(
            isAmrap = true,
            progression = ProgressionRule.AmrapDrivenTm(),
            protocolId = "smolov",
            protocolName = "Smolov",
            autoregulationMode = com.example.kpkn.data.models.AutoregulationMode.PROPOSE,

        ),
        relatorContext(
            isTopSet = true,
            techniqueModifier = TechniqueModifier.PAUSE_2S,
            warmupRemaining = 1,
            isCompetitionLift = true,
            minutesAhead = -4,
        ),
        relatorContext(
            techniqueModifier = TechniqueModifier.TEMPO_3_0_3,
            lastSets = emptyList(),
            bestRm = 0.0,
            feminine = true,
            warmupRemaining = 2,
        ),
    )

    /** Frases del catálogo de los observadores, con el nombre de ejercicio de ejemplo ya en su sitio. */
    private fun frasesDeObservadores(): List<String> = contextosCompletos().flatMap { context ->
        RelatorEngine.defaultObservers().flatMap { observer ->
            observer.observe(context).flatMap { it.lines }
        }
    }.map { it.replace("{ex}", "Sentadilla") }

    @Test
    fun ninguna_frase_lleva_jerga_interna() {
        val fallos = frasesDeObservadores().filter { frase -> jerga.any { frase.contains(it, ignoreCase = true) } }
        assertTrue("Jerga en: $fallos", fallos.isEmpty())
    }

    @Test
    fun ninguna_cifra_va_pegada_a_la_s() {
        val fallos = frasesDeObservadores().filter { cifraPegadaS.containsMatchIn(it) }
        assertTrue("Cifra pegada a la s en: $fallos", fallos.isEmpty())
    }

    @Test
    fun las_series_llevan_espacios_alrededor_de_la_x_y_kg_con_espacio_duro() {
        val fallos = frasesDeObservadores().filter { xSinEspacios.containsMatchIn(it) || kgSinEspacioDuro.containsMatchIn(it) }
        assertTrue("Series o kilos mal escritos en: $fallos", fallos.isEmpty())
    }

    @Test
    fun cada_frase_tiene_como_mucho_un_dos_puntos_con_espacio() {
        val fallos = frasesDeObservadores().filter { it.split(": ").size - 1 > 1 }
        assertTrue("Dos «: » en: $fallos", fallos.isEmpty())
    }

    @Test
    fun ninguna_frase_empieza_por_un_rotulo_de_la_lista_vieja() {
        val fallos = frasesDeObservadores().filter { frase -> rotulos.any { frase.startsWith("$it:") } }
        assertTrue("Rótulo en: $fallos", fallos.isEmpty())
    }

    /** Frases de conceptos (RelatorConceptCue): sin prefijo de rótulo, dentro de 90 caracteres y sin jerga. */
    @Test
    fun conceptos_sin_prefijo_de_rotulo_y_dentro_de_90() {
        val ids = listOf(
            "carga-axial", "pliometria", "isometria", "fallo-muscular", "rir", "rpe",
            "fase-excentrica", "estres-metabolico", "poleas", "maquinas", "pesos-libres", "fuerza",
        )
        val frases = ids.flatMap { relatorConceptLines(it) }
        assertTrue("Concepto con menos de dos frases", ids.all { relatorConceptLines(it).size >= 2 })
        val prefijos = frases.filter { prefijoRotulo.containsMatchIn(it) }
        assertTrue("Prefijo de rótulo en: $prefijos", prefijos.isEmpty())
        val largas = frases.filter { it.length > RELATOR_LINE_MAX_CHARS }
        assertTrue("Más de 90 caracteres: $largas", largas.isEmpty())
        val fallos = frases.filter { frase ->
            jerga.any { frase.contains(it, ignoreCase = true) } ||
                cifraPegadaS.containsMatchIn(frase) || xSinEspacios.containsMatchIn(frase)
        }
        assertTrue("Jerga o cifras mal escritas en: $fallos", fallos.isEmpty())
    }

    @Test
    fun el_motor_no_saca_rotulos_con_ningun_momento() {
        val momentos: List<RelatorMoment?> = listOf(null) + RelatorMoment.values().toList()
        for (context in contextosCompletos()) {
            for (momento in momentos) {
                val texto = RelatorEngine.resolve(context.copy(moment = momento)).line.text ?: continue
                assertTrue("Rótulo en «$texto» con $momento", rotulos.none { texto.startsWith("$it:") })
                assertTrue("Frase de ${texto.length} caracteres: $texto", texto.length <= RELATOR_LINE_MAX_CHARS)
            }
        }
    }
}

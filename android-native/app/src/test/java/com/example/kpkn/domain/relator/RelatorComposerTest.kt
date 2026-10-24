package com.example.kpkn.domain.relator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RelatorComposerTest {
    private val rotulos = listOf("Ahora", "Ojo", "Dato", "Plan", "Siguiente", "Hoy", "Carga", "Recuerda")

    /** Frases genéricas que se quitaron de `padVariants` y del plan (LR1h): ninguna vuelve a salir. */
    private val relleno = listOf(
        "va como está escrito en la receta",
        "No adelantes el plan de",
        "Si dudas, baja 2,5",
        "sigue en el programa",
        "El estímulo de",
        "Respeta series y descanso",
        "Sigue la receta de",
        "entra como va escrito",
        "Esta pieza de",
        "Cumple el esquema de",
        "hoy no improvisamos",
        "cuenta con esta serie",
        "marca esta serie de",
    )

    @Test
    fun sin_rotulos_aunque_haya_ciclo_de_ociosidad() {
        // Antes, con idleCycle > 0 el compositor añadía un rótulo al azar (LR1g): ya no.
        val candidate = RelatorCandidate(
            topic = RelatorTopic.PLAN,
            priority = 50,
            relevance = 1.0,
            lines = listOf("Sigue la receta de {ex}; hoy no improvisamos el estímulo."),
            fingerprintBase = "PLAN",
        )
        for (cycle in 0..3) {
            val (line, _) = RelatorComposer.compose(
                candidate,
                relatorContext(idleCycle = cycle),
                RelatorSelectorState(),
                RelatorLongTermMemory(),
            )
            assertTrue(line.text!!, rotulos.none { line.text!!.startsWith("$it:") })
        }
    }

    @Test
    fun la_misma_frase_dentro_del_mismo_momento() {
        val candidate = RelatorCandidate(
            topic = RelatorTopic.PLAN,
            priority = 50,
            relevance = 1.0,
            lines = listOf("Primera {ex}.", "Segunda {ex}.", "Tercera {ex}."),
            fingerprintBase = "PLAN",
        )
        val context = relatorContext(idleCycle = 0)
        val first = RelatorComposer.compose(candidate, context, RelatorSelectorState(), RelatorLongTermMemory()).first
        val second = RelatorComposer.compose(candidate, context, RelatorSelectorState(), RelatorLongTermMemory()).first
        assertEquals(first.text, second.text)
    }

    @Test
    fun clips_to_90_chars_when_possible() {
        val candidate = RelatorCandidate(
            topic = RelatorTopic.PLAN,
            priority = 50,
            relevance = 1.0,
            lines = listOf("x".repeat(200)),
            fingerprintBase = "PLAN",
        )
        val (line, _) = RelatorComposer.compose(
            candidate,
            relatorContext(),
            RelatorSelectorState(),
            RelatorLongTermMemory(),
        )
        assertTrue((line.text?.length ?: 0) <= RELATOR_LINE_MAX_CHARS)
    }

    @Test
    fun applies_feminine_voice() {
        val candidate = RelatorCandidate(
            topic = RelatorTopic.SITUATE,
            priority = 10,
            relevance = 1.0,
            lines = listOf("Queda {ready} para {ex}."),
            fingerprintBase = "S",
            isReaction = true,
        )
        val (line, _) = RelatorComposer.compose(
            candidate,
            relatorContext(feminine = true, idleCycle = 0),
            RelatorSelectorState(),
            RelatorLongTermMemory(),
        )
        assertTrue(line.text!!.contains("lista"))
    }

    /**
     * LR1h: los observadores ya no completan hasta seis frases con relleno. Cada candidato tiene al menos una frase real y
     * ninguna de las frases genéricas que se quitaron de `padVariants` y del plan.
     */
    @Test
    fun observers_sin_relleno_generico() {
        val ctx = relatorContext(
            isAmrap = true,
            isTopSet = true,
            restActive = true,
            phase = RelatorSessionPhase.REST,

            warmupRemaining = 2,
            prJustNow = true,
            mediaCount = 2,
            coachBody = "Baja un punto.",
            conceptId = "brace",
            conceptLines = listOf("Brace antes de empujar."),
            executionCues = listOf("Rodillas afuera."),
        )
        RelatorEngine.defaultObservers().forEach { observer ->
            observer.observe(ctx).forEach { candidate ->
                assertTrue("${candidate.topic} sin frases", candidate.lines.isNotEmpty())
                candidate.lines.forEach { line ->
                    assertFalse("relleno en «$line»", relleno.any { line.contains(it) })
                }
            }
        }
    }
}

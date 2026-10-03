package com.example.kpkn.data.programs

import com.example.kpkn.data.wikilab.TRAINING_CONCEPTS_DATABASE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C.P10 · Glosario ligero de la hoja del plan, nivel A del diseño editorial §6 (JVM puro).
 *
 * Fija que cada [PlanTerm] tiene su definición corta, que los textos son llanos (1–2 frases, sin ids internos y
 * sin T1/T2/T3 fuera de su entrada), que cada sigla se explica en su propia entrada y que cada enlace a
 * Conceptos Clave apunta a un concepto que existe. Un término o un texto nuevos que rompan una regla fallan aquí.
 */
class PlanGlossaryTest {

    private val entries: List<GlossaryEntry> get() = PlanGlossary.entries.values.toList()

    // ─── 1 · Cobertura ────────────────────────────────────────────────────────

    @Test
    fun every_plan_term_has_exactly_one_entry_in_enum_order() {
        assertEquals(
            "el glosario debe tener una entrada por término, en el orden del enum",
            PlanTerm.entries.toList(),
            PlanGlossary.entries.keys.toList(),
        )
        PlanTerm.entries.forEach { term ->
            val entry = PlanGlossary.entryFor(term)
            assertEquals("la entrada de $term declara otro término", term, entry.term)
            assertEquals("entries[$term] y entryFor($term) difieren", PlanGlossary.entries[term], entry)
        }
    }

    @Test
    fun titles_are_present_and_unique() {
        val titles = entries.map { it.title }
        titles.forEach { assertTrue("título vacío", it.isNotBlank()) }
        assertEquals("títulos repetidos: $titles", titles.size, titles.toSet().size)
    }

    // ─── 2 · Forma de las definiciones ────────────────────────────────────────

    @Test
    fun definitions_are_one_or_two_plain_sentences() {
        entries.forEach { entry ->
            val text = entry.definition
            val words = wordsOf(text)
            val sentences = sentencesOf(text)
            assertTrue(
                "${entry.term}: $words palabras (deben ser $MIN_WORDS a $MAX_WORDS): «$text»",
                words in MIN_WORDS..MAX_WORDS,
            )
            assertTrue("${entry.term}: ${sentences.size} frases (deben ser 1 o 2): «$text»", sentences.size in 1..2)
            assertTrue("${entry.term}: la definición no termina en punto: «$text»", text.endsWith("."))
            assertEquals("${entry.term}: la definición lleva espacios sobrantes", text.trim(), text)
        }
    }

    @Test
    fun texts_carry_no_internal_ids() {
        val idLike = Regex("""\w:\w""") // native:full-body, protocol:smolov…
        entries.forEach { entry ->
            listOf(entry.title, entry.definition).forEach { text ->
                assertFalse("${entry.term}: «$text» lleva un guion bajo", text.contains('_'))
                assertFalse("${entry.term}: «$text» parece un id (palabra:palabra)", idLike.containsMatchIn(text))
            }
        }
    }

    @Test
    fun tier_labels_appear_only_in_the_tiers_entry() {
        entries.forEach { entry ->
            val text = entry.title + " " + entry.definition
            listOf("T1", "T2", "T3").forEach { tier ->
                val present = tokenRegex(tier).containsMatchIn(text)
                if (entry.term == PlanTerm.TIERS) {
                    assertTrue("la entrada de TIERS debe explicar $tier: «$text»", present)
                } else {
                    assertFalse("${entry.term}: «$text» nombra $tier fuera de la entrada de TIERS", present)
                }
            }
        }
    }

    // ─── 3 · Siglas ───────────────────────────────────────────────────────────

    @Test
    fun every_acronym_is_explained_in_its_own_entry() {
        ACRONYMS.forEach { acronym ->
            val entry = PlanGlossary.entryFor(acronym.owner)
            assertTrue(
                "${acronym.owner}: el título «${entry.title}» debe llevar la sigla ${acronym.sigla}",
                tokenRegex(acronym.sigla).containsMatchIn(entry.title),
            )
            val text = (entry.title + " " + entry.definition).lowercase()
            assertTrue(
                "${acronym.owner}: la entrada debe explicar ${acronym.sigla} con «${acronym.explanation}»: «$text»",
                text.contains(acronym.explanation.lowercase()),
            )
        }
    }

    @Test
    fun an_acronym_used_outside_its_own_entry_is_a_declared_dependency() {
        entries.forEach { entry ->
            ACRONYMS.filter { it.owner != entry.term }.forEach { acronym ->
                if (tokenRegex(acronym.sigla).containsMatchIn(entry.definition)) {
                    assertTrue(
                        "${entry.term}: la definición usa la sigla ${acronym.sigla}, que se explica en " +
                            "${acronym.owner}: añádela a DECLARED_DEPENDENCIES o redáctala sin la sigla",
                        (entry.term to acronym.sigla) in DECLARED_DEPENDENCIES,
                    )
                }
            }
        }
    }

    // ─── 4 · Enlaces a Conceptos Clave ────────────────────────────────────────

    @Test
    fun every_concept_link_points_to_an_existing_concept() {
        val knownIds = TRAINING_CONCEPTS_DATABASE.map { it.id }.toSet()
        entries.forEach { entry ->
            val id = entry.conceptId ?: return@forEach
            assertTrue("${entry.term}: el concepto «$id» no existe en Conceptos Clave", id in knownIds)
        }
    }

    @Test
    fun terms_link_to_the_expected_concepts_and_the_rest_have_none() {
        val expected = mapOf(
            PlanTerm.ONE_RM to "rm-tm",
            PlanTerm.TM to "rm-tm",
            PlanTerm.AMRAP to "amrap",
            PlanTerm.RPE to "rpe",
            PlanTerm.RIR to "rir",
            PlanTerm.DUP to "ondulacion-diaria",
            PlanTerm.MEV_MRV to "mev-mrv",
            PlanTerm.DELOAD to "deload",
        )
        val actual = entries.filter { it.conceptId != null }.associate { it.term to it.conceptId }
        assertEquals(expected, actual)
    }

    // ─── 5 · Consulta por conjunto de términos ────────────────────────────────

    @Test
    fun entries_for_returns_the_requested_terms_in_enum_order() {
        val expected = listOf(PlanTerm.TM, PlanTerm.AMRAP)
        assertEquals(expected, PlanGlossary.entriesFor(setOf(PlanTerm.TM, PlanTerm.AMRAP)).map { it.term })
        // El orden no depende del orden de inserción del conjunto.
        assertEquals(expected, PlanGlossary.entriesFor(linkedSetOf(PlanTerm.AMRAP, PlanTerm.TM)).map { it.term })
        assertEquals(PlanTerm.entries.toList(), PlanGlossary.entriesFor(PlanTerm.entries.toSet()).map { it.term })
        assertTrue(PlanGlossary.entriesFor(emptySet()).isEmpty())
    }

    @Test
    fun an_entry_rejects_blank_text() {
        val invalid = listOf(
            { GlossaryEntry(PlanTerm.TM, " ", "Una definición.") },
            { GlossaryEntry(PlanTerm.TM, "TM", "") },
            { GlossaryEntry(PlanTerm.TM, "TM", "Una definición.", conceptId = " ") },
        )
        invalid.forEach { build ->
            val error = runCatching { build() }.exceptionOrNull()
            assertTrue("debe lanzar IllegalArgumentException, lanzó $error", error is IllegalArgumentException)
        }
    }

    // ─── Utilidades ───────────────────────────────────────────────────────────

    private fun wordsOf(text: String): Int = text.trim().split(Regex("""\s+""")).count { it.isNotBlank() }

    private fun sentencesOf(text: String): List<String> =
        text.trim().split(Regex("""(?<=[.!?])\s+""")).filter { it.isNotBlank() }

    /** La sigla como palabra completa, sin letras ni dígitos pegados (T1 no casa con T10, ni TM con ATM). */
    private fun tokenRegex(token: String): Regex = Regex("""(?<![\p{L}\d])${Regex.escape(token)}(?![\p{L}\d])""")

    /** Sigla, término cuya entrada la explica y frase que esa entrada debe contener. */
    data class Acronym(val sigla: String, val owner: PlanTerm, val explanation: String)

    private companion object {
        /**
         * Suelo de palabras por definición. El diseño (§6) tiene definiciones de 9 y 10 palabras (series
         * rápidas, RIR, SBD): el suelo evita las definiciones vacías sin obligar a alargarlas.
         */
        const val MIN_WORDS = 8
        const val MAX_WORDS = 60

        val ACRONYMS = listOf(
            Acronym("1RM", PlanTerm.ONE_RM, "una sola vez"),
            Acronym("TM", PlanTerm.TM, "máximo de entrenamiento"),
            Acronym("AMRAP", PlanTerm.AMRAP, "tantas repeticiones como puedas"),
            Acronym("RPE", PlanTerm.RPE, "esfuerzo"),
            Acronym("RIR", PlanTerm.RIR, "repeticiones en reserva"),
            Acronym("DUP", PlanTerm.DUP, "ondulación diaria"),
            Acronym("MEV", PlanTerm.MEV_MRV, "volumen mínimo efectivo"),
            Acronym("MRV", PlanTerm.MEV_MRV, "máximo recuperable"),
            Acronym("SBD", PlanTerm.SBD, "sentadilla, banca y peso muerto"),
        )

        /**
         * Siglas que una entrada usa sin explicarlas porque las explica otra. La definición de TM habla de «tu
         * 1RM»: la hoja del plan debe mostrar también la entrada de 1RM cuando muestre la de TM (C.P8).
         */
        val DECLARED_DEPENDENCIES: Set<Pair<PlanTerm, String>> = setOf(PlanTerm.TM to "1RM")
    }
}

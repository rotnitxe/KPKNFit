package com.example.kpkn.domain.concepts

import com.example.kpkn.data.wikilab.ConceptArea
import com.example.kpkn.data.wikilab.ConceptCategory
import com.example.kpkn.data.wikilab.TRAINING_CONCEPTS_DATABASE
import com.example.kpkn.data.wikilab.TRAINING_CONCEPT_SOURCES
import com.example.kpkn.data.wikilab.TrainingConcept
import com.example.kpkn.data.wikilab.sourceReferencesForConcept
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ConceptosClaveIndexTest {
    private fun concept(id: String, name: String, category: ConceptCategory, description: String) =
        TrainingConcept(id, name, category, description)

    @Test
    fun ordersPedagogicalCategoriesThenSpanishNames() {
        val input = listOf(
            concept("z", "Ángulo", ConceptCategory.INTENSITY, "desc"),
            concept("a", "Carga", ConceptCategory.LOAD_MANAGEMENT, "desc"),
            concept("b", "Agarre", ConceptCategory.LOAD_MANAGEMENT, "desc"),
        )

        assertEquals(listOf("b", "a", "z"), orderConceptosClave(input).map { it.id })
    }

    @Test
    fun searchIsAccentInsensitiveAndUsesRichProjectionFields() {
        val target = concept("target", "Tensión", ConceptCategory.MOVEMENT, "Una explicación técnica y extensa")
        val longOnly = TrainingConcept("long", "Otro", ConceptCategory.MOVEMENT, "Nada coincide")

        assertEquals(listOf("target"), searchConceptosClave("tecnica", listOf(target, longOnly)).map { it.id })
        val projection = projectConceptoClave(target)
        assertEquals("Mecánica del movimiento", projection.category)
        assertEquals("Una explicación técnica y extensa", projection.description)
        assertFalse(projection.toString().contains("definition", ignoreCase = true))
    }

    @Test
    fun everyConceptHasDistinctRichCopyAndEditorialSources() {
        assertTrue(TRAINING_CONCEPTS_DATABASE.size >= 31)
        assertEquals(
            TRAINING_CONCEPTS_DATABASE.size,
            TRAINING_CONCEPTS_DATABASE.map { it.id }.toSet().size,
        )
        assertEquals(
            TRAINING_CONCEPTS_DATABASE.size,
            TRAINING_CONCEPTS_DATABASE.map { it.description }.toSet().size,
        )
        TRAINING_CONCEPTS_DATABASE.forEach { concept ->
            val words = plainConceptText(concept.description).trim().split(Regex("\\s+")).count { it.isNotBlank() }
            assertTrue("${concept.id} must have 180-240 words: $words", words in 180..240)
            assertTrue(
                "${concept.id} must have at least two paragraphs",
                concept.description.split(Regex("\\n\\s*\\n")).size >= 2,
            )
            assertTrue(
                "${concept.id} must have source references",
                TRAINING_CONCEPT_SOURCES[concept.id].orEmpty().isNotEmpty(),
            )
            assertTrue(concept.shortDescription.isNotBlank())
        }
        assertEquals(
            TRAINING_CONCEPTS_DATABASE.map { it.id }.toSet(),
            TRAINING_CONCEPT_SOURCES.keys,
        )
    }

    @Test
    fun planGlossaryConceptsAreRegisteredWithTheirCategoryShortCopyAndSources() {
        data class Expected(
            val id: String,
            val name: String,
            val category: ConceptCategory,
            val shortDescription: String,
            val keyTerms: List<String>,
        )

        val expected = listOf(
            Expected(
                id = "rm-tm",
                name = "1RM y Training Max",
                category = ConceptCategory.LOAD_MANAGEMENT,
                shortDescription = "Tu máximo de una repetición y el peso de referencia, más bajo, con el que se calculan los porcentajes.",
                keyTerms = listOf("1RM", "TM"),
            ),
            Expected(
                id = "amrap",
                name = "AMRAP",
                category = ConceptCategory.INTENSITY,
                shortDescription = "Una serie con tantas repeticiones como puedas con una carga fija.",
                keyTerms = listOf("AMRAP"),
            ),
            Expected(
                id = "ondulacion-diaria",
                name = "Ondulación diaria (DUP)",
                category = ConceptCategory.PERIODIZATION,
                shortDescription = "Variar pesado, ligero y volumen dentro de la misma semana.",
                keyTerms = listOf("DUP"),
            ),
            Expected(
                id = "mev-mrv",
                name = "Volumen mínimo efectivo y máximo recuperable",
                category = ConceptCategory.LOAD_MANAGEMENT,
                shortDescription = "El rango de series semanales entre lo que ya produce mejora y lo que aún puedes recuperar.",
                keyTerms = listOf("MEV", "MRV"),
            ),
        )
        val newIds = expected.map { it.id }.toSet()
        val alreadyRegistered = TRAINING_CONCEPT_SOURCES
            .filterKeys { it !in newIds }
            .values
            .flatten()
            .toSet()

        expected.forEach { spec ->
            val concept = TRAINING_CONCEPTS_DATABASE.single { it.id == spec.id }
            assertEquals(spec.name, concept.name)
            assertEquals(spec.category, concept.category)
            assertEquals(spec.shortDescription, concept.shortDescription)
            spec.keyTerms.forEach { term ->
                assertTrue("${spec.id} must explain $term", concept.description.contains(term))
            }
            val sources = sourceReferencesForConcept(spec.id)
            assertTrue("${spec.id} must have source references", sources.isNotEmpty())
            assertTrue(
                "${spec.id} must reuse sources that are already registered",
                alreadyRegistered.containsAll(sources),
            )
        }
    }
    @Test
    fun everyWikiLinkAndRelatedIdPointsToAnExistingConcept() {
        val ids = TRAINING_CONCEPTS_DATABASE.map { it.id }.toSet()
        TRAINING_CONCEPTS_DATABASE.forEach { concept ->
            Regex("""\[\[([a-z0-9-]+)""").findAll(concept.description).forEach { match ->
                assertTrue("${concept.id} enlaza a ${match.groupValues[1]}", match.groupValues[1] in ids)
            }
            concept.related.forEach { assertTrue("${concept.id} relaciona $it", it in ids && it != concept.id) }
            assertFalse("${concept.id} no debe tener «[[» sin cerrar", plainConceptText(concept.description).contains("[["))
        }
    }

    @Test
    fun parsesWikiLinksAndLeavesUnknownIdsAsPlainText() {
        val pool = listOf(
            concept("rir", "RIR", ConceptCategory.INTENSITY, "desc"),
            concept("x", "X", ConceptCategory.INTENSITY, "desc"),
        )
        val spans = parseConceptText("Usa el [[rir|RIR]] y no [[nada|esto]] ni [[rir]].", pool)
        assertEquals(
            listOf(
                ConceptTextSpan.Plain("Usa el "),
                ConceptTextSpan.Link("rir", "RIR"),
                ConceptTextSpan.Plain(" y no esto ni "),
                ConceptTextSpan.Link("rir", "RIR"),
                ConceptTextSpan.Plain("."),
            ),
            spans,
        )
        assertEquals("Usa el RIR y no esto ni rir.", plainConceptText("Usa el [[rir|RIR]] y no [[nada|esto]] ni [[rir]]."))
    }

    @Test
    fun practiceIsSeparatedFromTheBodyParagraphs() {
        val c = TrainingConcept("p", "P", ConceptCategory.NUTRITION, "Uno.\n\nDos.", shortDescription = "s", practice = "Haz esto.")
        assertEquals(listOf("Uno.", "Dos."), conceptBodyParagraphs(c))
        assertTrue(c.description.endsWith("Haz esto."))
    }

    @Test
    fun searchReportsWhereItMatchedWithAliasesAndBodySnippets() {
        val pool = listOf(
            TrainingConcept("a", "Series descendentes", ConceptCategory.METHODS, "Bajas el peso y sigues con la [[b|serie]].", shortDescription = "Bajar la carga", aliases = listOf("drop set")),
            TrainingConcept("b", "Otra", ConceptCategory.METHODS, "Texto largo donde aparece la creatína al final del párrafo.", shortDescription = "Nada"),
        )
        val drop = searchConceptosClaveHits("drop", pool).single()
        assertEquals(ConceptMatch.ALIAS, drop.match)
        val body = searchConceptosClaveHits("creatina", pool).single()
        assertEquals(ConceptMatch.BODY, body.match)
        assertNotNull(body.snippet)
        assertTrue(body.snippet!!.contains("creatína"))
        // El marcado de wiki no cuenta para la búsqueda.
        assertTrue(searchConceptosClaveHits("[[", pool).isEmpty())
    }

    @Test
    fun matchRangesIgnoreAccentsAndPointAtTheOriginalText() {
        val text = "La tensión mecánica y la TENSION"
        val ranges = conceptMatchRanges(text, "tension")
        assertEquals(listOf(3..9, 25..31), ranges)
        assertEquals("tensión", text.substring(ranges.first()))
        assertTrue(conceptMatchRanges(text, "  ").isEmpty())
    }

    @Test
    fun dailyConceptMatchesHomeAndNavigationHelpersFollowTheLibraryOrder() {
        val db = TRAINING_CONCEPTS_DATABASE
        assertEquals(db[Math.floorMod(20_000L, db.size.toLong()).toInt()], dailyConceptoClave(20_000L))
        assertNull(dailyConceptoClave(1L, emptyList()))
        val ordered = orderConceptosClave()
        val (previous, next) = adjacentConceptosClave(ordered[1].id)
        assertEquals(ordered[0].id, previous?.id)
        assertEquals(ordered[2].id, next?.id)
        assertNull(adjacentConceptosClave(ordered.first().id).first)
    }

    @Test
    fun relatedPrefersExplicitIdsThenTheSameTopic() {
        val pool = listOf(
            TrainingConcept("a", "A", ConceptCategory.METHODS, "d", shortDescription = "s", related = listOf("z", "c", "nope")),
            TrainingConcept("b", "B", ConceptCategory.METHODS, "d", shortDescription = "s"),
            TrainingConcept("c", "C", ConceptCategory.NUTRITION, "d", shortDescription = "s"),
            TrainingConcept("z", "Z", ConceptCategory.NUTRITION, "d", shortDescription = "s"),
        )
        assertEquals(listOf("z", "c", "b"), relatedConceptosClave(pool[0], 3, pool).map { it.id })
    }

    @Test
    fun categoriesGroupByAreaInPedagogicalOrder() {
        val pool = listOf(
            TrainingConcept("a", "A", ConceptCategory.SUPPLEMENTS, "d", shortDescription = "s"),
            TrainingConcept("b", "B", ConceptCategory.LOAD_MANAGEMENT, "d", shortDescription = "s"),
            TrainingConcept("c", "C", ConceptCategory.NUTRITION, "d", shortDescription = "s"),
            TrainingConcept("d", "D", ConceptCategory.NUTRITION, "d", shortDescription = "s"),
        )
        val areas = conceptCategoriesByArea(pool)
        assertEquals(listOf(ConceptArea.TRAINING, ConceptArea.NUTRITION), areas.keys.toList())
        assertEquals(listOf(ConceptCategory.NUTRITION to 2, ConceptCategory.SUPPLEMENTS to 1), areas.getValue(ConceptArea.NUTRITION))
        assertEquals(ConceptCategory.NUTRITION, nextConceptCategory(ConceptCategory.LOAD_MANAGEMENT, pool))
        assertEquals(ConceptCategory.LOAD_MANAGEMENT, nextConceptCategory(ConceptCategory.SUPPLEMENTS, pool))
    }

    @Test
    fun nameMatchesComeBeforeBodyMatchesAndSnippetsStayOnOneLine() {
        val pool = listOf(
            TrainingConcept("a", "Sistemas energéticos", ConceptCategory.LOAD_MANAGEMENT, "Primer párrafo.\n\nLa fosfocreatina da energía rápida.", shortDescription = "Energía"),
            TrainingConcept("b", "Creatina", ConceptCategory.SUPPLEMENTS, "Suplemento estudiado.", shortDescription = "Compuesto"),
        )
        val hits = searchConceptosClaveHits("creatina", pool)
        assertEquals(listOf("b", "a"), hits.map { it.concept.id })
        assertEquals(ConceptMatch.NAME, hits.first().match)
        assertFalse(hits.last().snippet!!.contains('\n'))
    }
}

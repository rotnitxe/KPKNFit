package com.example.kpkn.domain.nutrition.v2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConceptSearchV2Test {

    private fun concept(
        id: String,
        name: String,
        variants: List<ConceptVariantV2>,
        aliasesEs: List<String> = emptyList(),
        aliasesEn: List<String> = emptyList(),
        typos: List<String> = emptyList(),
        defaultState: FoodStateV2? = null,
        portions: List<ConceptPortionV2> = emptyList(),
        defaultGrams: Double = 100.0,
    ) = ConceptV2(
        id = id, name = name, kind = ConceptKindV2.FOOD, category = "test",
        aliasesEs = aliasesEs, aliasesEn = aliasesEn, typos = typos,
        defaultState = defaultState, defaultForm = FoodFormV2.NA,
        variants = variants,
        countable = false, unit = null, portions = portions, defaultGrams = defaultGrams,
        densityGPerMl = null, reconstitution = null, components = null,
    )

    private fun single(recordId: String?) = listOf(ConceptVariantV2(null, FoodFormV2.NA, recordId))

    private val catalog = ConceptCatalogV2(
        generation = "gen-test",
        conceptList = listOf(
            concept("palta", "Palta", single("usda_1"), aliasesEs = listOf("aguacate"), aliasesEn = listOf("avocado")),
            concept("marraqueta", "Marraqueta", single("kpkn_marraqueta"), aliasesEs = listOf("pan batido")),
            concept(
                "arroz", "Arroz blanco",
                listOf(
                    ConceptVariantV2(FoodStateV2.RAW, FoodFormV2.NA, "usda_10"),
                    ConceptVariantV2(FoodStateV2.COOKED, FoodFormV2.NA, "usda_11"),
                ),
                defaultState = FoodStateV2.COOKED,
                portions = listOf(ConceptPortionV2("1 taza", 60.0, FoodStateV2.RAW)),
                defaultGrams = 150.0,
            ),
            concept("huevo", "Huevo", single("usda_20")),
            concept("agua", "Agua", single("usda_30")),
            concept("sinficha", "Sin ficha", single(null)),
        ),
        records = mapOf(
            "usda_1" to RecordV2("usda_1", "Avocado", mapOf("energy" to 160.0, "protein" to 2.0, "fat" to 14.7, "carbs" to 8.5)),
            "kpkn_marraqueta" to RecordV2("kpkn_marraqueta", "Marraqueta", mapOf("energy" to 267.0, "protein" to 8.6, "fat" to 1.6, "carbs" to 55.0)),
            "usda_10" to RecordV2("usda_10", "Rice raw", mapOf("energy" to 365.0, "protein" to 7.1, "fat" to 0.7, "carbs" to 80.0)),
            "usda_11" to RecordV2("usda_11", "Rice cooked", mapOf("energy" to 130.0, "protein" to 2.7, "fat" to 0.3, "carbs" to 28.2)),
            "usda_20" to RecordV2("usda_20", "Egg", mapOf("energy" to 143.0, "protein" to 12.6, "fat" to 9.5, "carbs" to 0.7)),
            "usda_30" to RecordV2("usda_30", "Water", mapOf("energy" to 0.0, "protein" to 0.0, "fat" to 0.0, "carbs" to 0.0)),
        ),
    )

    private val search = ConceptSearchV2(catalog)

    @Test
    fun exactNameIsLevelZero() {
        val hits = search.search("palta")
        assertEquals(1, hits.size)
        val hit = hits.first()
        assertEquals("palta", hit.concept.id)
        assertEquals(0, hit.level)
        assertEquals("Palta", hit.label)
        assertEquals("usda_1", hit.record.id)
    }

    @Test
    fun spanishAndEnglishAliasesFindTheConcept() {
        val spanish = search.search("aguacate").first()
        assertEquals("palta", spanish.concept.id)
        assertEquals(0, spanish.level)
        assertEquals("aguacate", spanish.matchedAlias)

        val english = search.search("avocado").first()
        assertEquals("palta", english.concept.id)
        assertEquals("avocado", english.matchedAlias)
    }

    @Test
    fun prefixOfAWordIsLevelOne() {
        val hit = search.search("marraq").first()
        assertEquals("marraqueta", hit.concept.id)
        assertEquals(1, hit.level)
    }

    @Test
    fun pluralMatchesThroughTheStem() {
        val hit = search.search("huevos").first()
        assertEquals("huevo", hit.concept.id)
        assertEquals(0, hit.level)
    }

    @Test
    fun severalVariantsAreOfferedDefaultFirstWithTheirStateInTheLabel() {
        val hits = search.search("arroz")
        assertEquals(2, hits.size)
        assertEquals(listOf("Arroz blanco (cocido)", "Arroz blanco (crudo)"), hits.map { it.label })
        assertEquals(FoodStateV2.COOKED, hits[0].variant.state)
        assertEquals("usda_11", hits[0].record.id)
        assertEquals(FoodStateV2.RAW, hits[1].variant.state)
        assertEquals("usda_10", hits[1].record.id)
    }

    @Test
    fun hitForRecordFindsTheOfferedVariant() {
        val raw = search.hitForRecord("usda_10")!!
        assertEquals("Arroz blanco (crudo)", raw.label)
        assertEquals(FoodStateV2.RAW, raw.variant.state)
        assertEquals(0, raw.level)
        assertEquals("Arroz blanco", raw.matchedAlias)
        assertEquals("Palta", search.hitForRecord("usda_1")?.label)
        assertNull(search.hitForRecord("nope"))
    }

    @Test
    fun limitCapsTheHits() {
        assertEquals(1, search.search("arroz", limit = 1).size)
        assertTrue(search.search("arroz", limit = 0).isEmpty())
    }

    @Test
    fun conceptWithoutRecordIsNeverOffered() {
        assertTrue(search.search("sin ficha").isEmpty())
    }

    @Test
    fun blankQueryFindsNothing() {
        assertTrue(search.search("").isEmpty())
        assertTrue(search.search("   ").isEmpty())
    }

    @Test
    fun matchLevelOfTokenLists() {
        assertEquals(1, ConceptSearchV2.matchLevel(listOf("pan"), listOf("pan", "batido")))
        assertEquals(2, ConceptSearchV2.matchLevel(listOf("batido"), listOf("pan", "batido")))
        assertNull(ConceptSearchV2.matchLevel(listOf("queso"), listOf("pan", "batido")))
        assertEquals(0, ConceptSearchV2.matchLevel(listOf("pan", "batido"), listOf("pan", "batido")))
        assertNull(ConceptSearchV2.matchLevel(emptyList(), listOf("pan")))
        assertNull(ConceptSearchV2.matchLevel(listOf("pan"), emptyList()))
    }

    @Test
    fun searchTokensDropPunctuationAndAccents() {
        assertEquals(listOf("pure", "de", "papa"), ConceptSearchV2.searchTokens("Puré de papa."))
    }
}

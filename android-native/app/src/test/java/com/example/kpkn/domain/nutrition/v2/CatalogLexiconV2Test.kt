package com.example.kpkn.domain.nutrition.v2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogLexiconV2Test {

    private val catalog: ConceptCatalogV2 by lazy {
        checkNotNull(CatalogLexiconV2Test::class.java.classLoader!!.getResourceAsStream("nutrition_v2/concepts_fixture.json")) {
            "falta nutrition_v2/concepts_fixture.json"
        }.use { ConceptCatalogLoaderV2.read(it) }
    }

    private fun tokens(phrase: String) = NormalizerV2.tokenize(phrase).map { it.norm }

    private fun foodEntries(): List<LexEntry> = catalog.lexiconEntries()

    private fun kindOf(phrase: String, conceptId: String): List<AliasKind> =
        foodEntries().filter { it.tokens == tokens(phrase) }.map { it.tag as LexTag.Food }
            .filter { it.conceptId == conceptId }.map { it.aliasKind }

    @Test fun esEnAndTypoAliasesBecomeEntries() {
        assertEquals(listOf(AliasKind.ES), kindOf("arroz", "arroz_blanco"))
        assertEquals(listOf(AliasKind.ES), kindOf("arroz graneado", "arroz_blanco"))
        assertEquals(listOf(AliasKind.EN), kindOf("white rice", "arroz_blanco"))
        assertEquals(listOf(AliasKind.TYPO), kindOf("arros", "arroz_blanco"))
        assertEquals(listOf(AliasKind.TYPO), kindOf("aroz", "arroz_blanco"))
        assertEquals(listOf(AliasKind.ES), kindOf("Atún en aceite", "atun_en_aceite")) // acentos y mayúsculas normalizados
    }

    @Test fun nameIsAnEsAliasAndNothingRepeatsPerConcept() {
        // «Arroz blanco» es a la vez name y aliasesEs: una sola entrada.
        assertEquals(1, foodEntries().count { it.tokens == tokens("arroz blanco") && (it.tag as LexTag.Food).conceptId == "arroz_blanco" })
        // «yogurt» está en aliasesEs y aliasesEn: gana el primero (ES) y no se repite.
        assertEquals(listOf(AliasKind.ES), kindOf("yogurt", "yogur"))
        val pairs = foodEntries().map { it.tokens to (it.tag as LexTag.Food).conceptId }
        assertEquals(pairs.size, pairs.toSet().size)
        // Nombre de un concepto con tilde: entra por name.
        assertEquals(listOf(AliasKind.ES), kindOf("Café", "cafe"))
    }

    @Test fun sharedAliasKeepsBothConcepts() {
        val ids = foodEntries().filter { it.tokens == tokens("churrasco") }.map { (it.tag as LexTag.Food).conceptId }
        assertEquals(setOf("churrasco_corte", "churrasco_sandwich"), ids.toSet())
    }

    @Test fun aliasEqualToAModifierIsDropped() {
        val json = """
            {"concepts": [{"id": "raro", "name": "Raro",
              "aliasesEs": ["raro", "grande", "con", "sin azucar", "taza"],
              "aliasesEn": ["large"], "typos": ["cocido"]}]}
        """.trimIndent()
        val c = ConceptCatalogLoaderV2.parse(json)
        val entries = c.lexiconEntries()
        assertEquals(listOf(listOf("raro")), entries.map { it.tokens })
        // Y el léxico completo sigue etiquetando «grande» como tamaño, no como alimento.
        val tags = buildLexiconV2(c).tag(NormalizerV2.tokenize("grande")).single().tags
        assertTrue(tags.any { it is LexTag.Size })
        assertFalse(tags.any { it is LexTag.Food })
    }

    @Test fun buildLexiconTagsFoodsAndModifiers() {
        val lexicon = buildLexiconV2(catalog)
        val spans = lexicon.tag(NormalizerV2.tokenize("2 huevos fritos"))
        assertTrue(spans[0].tags.any { it is LexTag.Number })
        assertTrue(spans[1].tags.contains(LexTag.Food("huevo", AliasKind.ES)))
        assertTrue(spans[2].tags.contains(LexTag.State(FoodStateV2.FRIED)))
        // Falta del catálogo (typo) y alimento en inglés.
        assertTrue(lexicon.tag(NormalizerV2.tokenize("arros")).single().tags.contains(LexTag.Food("arroz_blanco", AliasKind.TYPO)))
        assertTrue(lexicon.tag(NormalizerV2.tokenize("coffee")).single().tags.contains(LexTag.Food("cafe", AliasKind.EN)))
        // Alias ambiguo: dos etiquetas de alimento en el mismo tramo.
        val churrasco = lexicon.tag(NormalizerV2.tokenize("churrasco")).single().tags.filterIsInstance<LexTag.Food>()
        assertEquals(setOf("churrasco_corte", "churrasco_sandwich"), churrasco.map { it.conceptId }.toSet())
    }
}

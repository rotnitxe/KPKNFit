package com.example.kpkn.domain.nutrition.v2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Punta a punta del parser v2 (normalizador, léxico, gramática, resolutor) con un catálogo inventado a mano. */
class NutritionParserV2Test {

    private fun record(id: String, name: String, kcal: Double, p: Double, f: Double, c: Double, density: Double? = null) =
        RecordV2(id, name, mapOf("energy" to kcal, "protein" to p, "fat" to f, "carbs" to c), densityGPerMl = density)

    private fun concept(
        id: String,
        name: String,
        es: List<String>,
        en: List<String>,
        variants: List<ConceptVariantV2>,
        kind: ConceptKindV2 = ConceptKindV2.FOOD,
        defaultState: FoodStateV2? = null,
        unit: CountUnitV2? = null,
        portions: List<ConceptPortionV2> = emptyList(),
        defaultGrams: Double = 100.0,
    ) = ConceptV2(
        id = id, name = name, kind = kind, category = "TEST", aliasesEs = es, aliasesEn = en, typos = emptyList(),
        defaultState = defaultState, defaultForm = FoodFormV2.NA, variants = variants, countable = unit != null,
        unit = unit, portions = portions, defaultGrams = defaultGrams, densityGPerMl = null, reconstitution = null,
        components = null,
    )

    private val catalog = ConceptCatalogV2(
        generation = "parser-test",
        conceptList = listOf(
            concept(
                "huevo_t", "Huevo de prueba", listOf("huevo", "huevos"), listOf("egg", "eggs"),
                listOf(ConceptVariantV2(null, FoodFormV2.NA, "t_huevo")),
                unit = CountUnitV2(listOf("unidad"), mapOf(SizeV2.M to 50.0)),
            ),
            concept(
                "arroz_t", "Arroz de prueba", listOf("arroz", "arroz blanco"), listOf("rice", "white rice"),
                listOf(ConceptVariantV2(FoodStateV2.COOKED, FoodFormV2.NA, "t_arroz")),
                defaultState = FoodStateV2.COOKED,
                portions = listOf(ConceptPortionV2("taza", 160.0, FoodStateV2.COOKED)),
                defaultGrams = 180.0,
            ),
            concept(
                "pollo_t", "Pollo de prueba", listOf("pollo"), listOf("chicken"),
                listOf(ConceptVariantV2(FoodStateV2.COOKED, FoodFormV2.NA, "t_pollo")),
                defaultState = FoodStateV2.COOKED,
                defaultGrams = 120.0,
            ),
            concept(
                "cafe_t", "Café de prueba", listOf("cafe"), listOf("coffee"),
                listOf(
                    ConceptVariantV2(null, FoodFormV2.NA, "t_cafe"),
                    ConceptVariantV2(null, FoodFormV2.SUGAR_FREE, "t_cafe_sin"),
                ),
                kind = ConceptKindV2.DRINK,
                defaultGrams = 200.0,
            ),
        ),
        records = listOf(
            record("t_huevo", "Egg, whole (test)", 140.0, 12.0, 10.0, 1.0),
            record("t_arroz", "Rice, cooked (test)", 130.0, 2.5, 0.3, 28.0),
            record("t_pollo", "Chicken, grilled (test)", 165.0, 31.0, 3.6, 0.0),
            record("t_cafe", "Coffee (test)", 5.0, 0.1, 0.0, 1.0, density = 1.0),
            record("t_cafe_sin", "Coffee, no sugar (test)", 1.0, 0.1, 0.0, 0.0, density = 1.0),
        ).associateBy { it.id },
    )

    private val parser = NutritionParserV2(catalog)

    @Test fun twoEggsAndACupOfRice() {
        val r = parser.parse("2 huevos y una taza de arroz")
        assertEquals(2, r.mentions.size)
        val egg = r.mentions.first { it.conceptId == "huevo_t" }
        val rice = r.mentions.first { it.conceptId == "arroz_t" }
        // Gramos por conteo (2 x 50 g) y por porción casera (1 taza = 160 g).
        assertEquals(100.0, egg.grams!!, 1e-6)
        assertEquals(QuantityKindV2.COUNT, egg.quantityKind)
        assertEquals(160.0, rice.grams!!, 1e-6)
        assertEquals(QuantityKindV2.HOUSEHOLD, rice.quantityKind)
        assertEquals(140.0, egg.eatenKcal!!, 1e-6)
        assertEquals(208.0, rice.eatenKcal!!, 1e-6)
        assertEquals("t_arroz", rice.recordId)
        assertFalse(egg.excluded)
    }

    @Test fun massOfChicken() {
        val r = parser.parse("150 g de pollo")
        assertEquals(1, r.mentions.size)
        val m = r.mentions.single()
        assertEquals("pollo_t", m.conceptId)
        assertEquals(QuantityKindV2.MASS, m.quantityKind)
        assertEquals(150.0, m.grams!!, 1e-6)
        assertEquals(247.5, m.eatenKcal!!, 1e-6)
        assertEquals(46.5, m.eatenProtein!!, 1e-6)
        assertEquals(FoodStateV2.COOKED, m.state)
    }

    @Test fun sugarFreeCoffeeIsAFormNotAnExclusion() {
        val r = parser.parse("café sin azúcar")
        val coffee = r.mentions.single { it.conceptId == "cafe_t" }
        assertEquals(FoodFormV2.SUGAR_FREE, coffee.form)
        assertEquals("t_cafe_sin", coffee.recordId)
        assertFalse(coffee.excluded)
        assertTrue(r.mentions.none { it.excluded })
    }

    @Test fun emptyTextHasNoMentions() {
        assertTrue(parser.parse("").mentions.isEmpty())
        assertTrue(parser.parse("   ").mentions.isEmpty())
    }

    @Test fun englishCupOfRice() {
        val r = parser.parse("a cup of rice")
        assertEquals(1, r.mentions.size)
        val m = r.mentions.single()
        assertEquals("arroz_t", m.conceptId)
        assertEquals(QuantityKindV2.HOUSEHOLD, m.quantityKind)
        assertEquals(160.0, m.grams!!, 1e-6)
        assertNotNull(m.recordId)
    }

    @Test fun mentionTextIsOriginalSubstring() {
        val text = "Almorcé 2 HUEVOS y una taza de arroz"
        val r = parser.parse(text)
        assertTrue(r.mentions.isNotEmpty())
        r.mentions.forEach { assertEquals(text.substring(it.draft.start, it.draft.end), it.text) }
    }

    @Test fun intentedFoodIsExcludedAndNeverAutoAccepted() {
        val r = parser.parse("iba a pedir pollo pero me comí 2 huevos")
        assertEquals(2, r.mentions.size)
        val chicken = r.mentions.first { it.conceptId == "pollo_t" }
        assertTrue(chicken.excluded)
        assertFalse(chicken.autoAccepted)
        assertEquals(null, chicken.eatenKcal)
        val eggs = r.mentions.first { it.conceptId == "huevo_t" }
        assertFalse(eggs.excluded)
        assertEquals(100.0, eggs.grams!!, 1e-6)
    }

    @Test fun otherPersonsMealIsExcluded() {
        val r = parser.parse("mi hermano pidió pollo, yo comí arroz")
        assertTrue(r.mentions.first { it.conceptId == "pollo_t" }.excluded)
        assertFalse(r.mentions.first { it.conceptId == "arroz_t" }.excluded)
    }

    @Test fun correctedQuantityReplacesTheEarlierMention() {
        val r = parser.parse("me comí un huevo y bueno 3 huevos")
        assertEquals(1, r.mentions.size)
        assertEquals(150.0, r.mentions.single().grams!!, 1e-6)
    }
}

package com.example.kpkn.domain.nutrition.v2

import com.example.kpkn.data.models.FoodCandidate
import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.data.models.SearchConfidence
import com.example.kpkn.data.models.SearchSource
import com.example.kpkn.domain.nutrition.NutrientBasis
import com.example.kpkn.domain.nutrition.nutrients.CatalogPortion
import com.example.kpkn.domain.nutrition.nutrients.CatalogRecord
import com.example.kpkn.domain.nutrition.nutrients.CatalogRecordLink
import com.example.kpkn.domain.nutrition.nutrients.NutrientId
import com.example.kpkn.domain.nutrition.nutrients.NutritionCatalogPort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoggerSearchV2Test {

    private fun concept(
        id: String,
        name: String,
        variants: List<ConceptVariantV2>,
        aliasesEs: List<String> = emptyList(),
        aliasesEn: List<String> = emptyList(),
        defaultState: FoodStateV2? = null,
        portions: List<ConceptPortionV2> = emptyList(),
        defaultGrams: Double = 100.0,
    ) = ConceptV2(
        id = id, name = name, kind = ConceptKindV2.FOOD, category = "test",
        aliasesEs = aliasesEs, aliasesEn = aliasesEn, typos = emptyList(),
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
            concept("agua", "Agua", single("usda_30")),
            concept("zapallo", "Zapallo", single("ciqual_200")),
            concept("sal", "Sal", single("usda_40")),
        ),
        records = mapOf(
            "usda_1" to RecordV2("usda_1", "Avocado", mapOf("energy" to 160.0, "protein" to 2.0, "fat" to 14.7, "carbs" to 8.5)),
            "usda_10" to RecordV2("usda_10", "Rice raw", mapOf("energy" to 365.0, "protein" to 7.1, "fat" to 0.7, "carbs" to 80.0)),
            "usda_11" to RecordV2("usda_11", "Rice cooked", mapOf("energy" to 130.0, "protein" to 2.7, "fat" to 0.3, "carbs" to 28.2)),
            "usda_30" to RecordV2("usda_30", "Water", mapOf("energy" to 0.0, "protein" to 0.0, "fat" to 0.0, "carbs" to 0.0)),
            "ciqual_200" to RecordV2("ciqual_200", "Courge", mapOf("energy" to 20.0, "protein" to 1.0, "fat" to 0.1, "carbs" to 4.0)),
            "usda_40" to RecordV2("usda_40", "Salt", mapOf("sodium" to 38758.0)),
        ),
    )

    private val search = ConceptSearchV2(catalog)

    private fun foodOf(query: String, label: String? = null): FoodItem {
        val hits = search.search(query)
        val hit = if (label == null) hits.first() else hits.first { it.label == label }
        return LoggerSearchV2.conceptFood(hit, catalog.generation)
    }

    @Test
    fun conceptFoodCarriesTheV2SourceAliasesAndPortion() {
        val food = foodOf("aguacate")
        assertEquals("usda_1", food.id)
        assertEquals("Palta", food.name)
        assertEquals(CatalogRecordLink.V2_SOURCE, food.source)
        assertTrue("aguacate" in food.searchAliases)
        assertTrue("avocado" in food.searchAliases)
        assertEquals(100.0, food.portionGrams!!, 1e-9)
        assertEquals(160.0, food.calories, 1e-9)
        assertEquals("gen-test", food.datasetVersion)
    }

    @Test
    fun portionFollowsTheStateOfTheVariant() {
        assertEquals(60.0, foodOf("arroz", "Arroz blanco (crudo)").portionGrams!!, 1e-9)
        assertEquals(150.0, foodOf("arroz", "Arroz blanco (cocido)").portionGrams!!, 1e-9)
    }

    @Test
    fun zeroEnergyConceptIsVerifiedThroughItsTag() {
        val food = foodOf("agua")
        assertTrue(NutrientBasis.ZERO_ENERGY_TAG in food.tags)
        assertTrue(NutrientBasis.isVerified(food))
    }

    @Test
    fun ciqualConceptLinksToThePack() {
        assertEquals("ciqual_200", CatalogRecordLink.recordIdFor(foodOf("zapallo")))
    }

    @Test
    fun conceptCandidatesRankTheExactNameFirst() {
        val candidates = LoggerSearchV2.conceptCandidates(search, "palta")
        assertEquals("usda_1", candidates.first().foodId)
        assertEquals("Palta", candidates.first().displayName)
        assertEquals(SearchConfidence.HIGH, candidates.first().confidence)
        assertEquals(SearchSource.LOCAL, candidates.first().source)
        assertEquals(1.0, candidates.first().score, 1e-9)
    }

    @Test
    fun conceptCandidatesDropRowsTheLoggerWouldReject() {
        // «Sal» solo trae sodio: sin energía ni macros y sin etiqueta de energía nula, la ficha no es verificable.
        assertTrue(LoggerSearchV2.conceptCandidates(search, "sal").isEmpty())
        assertTrue(LoggerSearchV2.conceptCandidates(search, "palta", limit = 0).isEmpty())
    }

    private fun milkRecord(id: String = "off_1", nameEs: String? = "Leche entera", name: String = "Whole milk") = CatalogRecord(
        id = id, source = "OFF", sourceRecordId = "1", name = name, nameEs = nameEs, brand = "Colun", category = null,
        preparation = "AS_SOLD", form = "NA", basisAmount = 100.0, basisUnit = "ml", densityGPerMl = 1.03,
    )

    @Test
    fun packFoodConvertsMillilitresToGramsWithTheDensity() {
        val per100 = mapOf(NutrientId.ENERGY to 61.0, NutrientId.PROTEIN to 3.1, NutrientId.FAT to 3.3, NutrientId.CARBS to 4.7)
        val food = LoggerSearchV2.packFood(milkRecord(), "Leche entera", per100, emptyList(), "gen-pack")!!
        assertEquals(61.0 / 1.03, food.calories, 1e-9)
        assertEquals(3.1 / 1.03, food.protein, 1e-9)
        assertEquals("Colun", food.brand)
        assertEquals("off_1", food.id)
        assertEquals(CatalogRecordLink.V2_SOURCE, food.source)
        assertEquals("gen-pack", food.datasetVersion)
    }

    @Test
    fun packFoodRejectsEmptyOrUnconvertibleRecords() {
        val per100 = mapOf(NutrientId.ENERGY to 61.0)
        assertNull(LoggerSearchV2.packFood(milkRecord(), "Leche", emptyMap(), emptyList(), "g"))
        assertNull(LoggerSearchV2.packFood(milkRecord().copy(basisUnit = "kg"), "Leche", per100, emptyList(), "g"))
        assertNull(LoggerSearchV2.packFood(milkRecord().copy(basisAmount = 0.0), "Leche", per100, emptyList(), "g"))
    }

    @Test
    fun packFoodTakesTheFirstUsablePortion() {
        val per100 = mapOf(NutrientId.ENERGY to 61.0)
        val portions = listOf(
            CatalogPortion(seq = 0, label = "bad", labelEs = null, grams = 0.0, ml = null),
            CatalogPortion(seq = 1, label = "cup", labelEs = "taza", grams = 245.0, ml = 240.0),
        )
        val food = LoggerSearchV2.packFood(milkRecord(), "Leche", per100, portions, "g")!!
        assertEquals(245.0, food.portionGrams!!, 1e-9)
    }

    private fun fakePort(records: List<CatalogRecord>, nutrients: Map<String, Map<NutrientId, Double>>) = object : NutritionCatalogPort {
        override val generation = "gen-pack"
        override fun record(id: String): CatalogRecord? = records.firstOrNull { it.id == id }
        override fun nutrientsPer100(id: String): Map<NutrientId, Double> = nutrients[id].orEmpty()
        override fun portions(id: String): List<CatalogPortion> = emptyList()
        override fun byGtin(gtin: String): CatalogRecord? = null
        override fun search(queryNorm: String, limit: Int): List<CatalogRecord> = records
        override fun recordFor(source: String, sourceRecordId: String): CatalogRecord? = null
    }

    @Test
    fun packCandidatesShowOffProductsAndSkipUnnamedUsdaRows() {
        val milk = milkRecord()
        val usda = milkRecord(id = "usda_9", nameEs = null, name = "Milk, whole").copy(source = "USDA", brand = null, basisUnit = "g")
        val milkNutrients = mapOf(NutrientId.ENERGY to 61.0, NutrientId.PROTEIN to 3.1, NutrientId.FAT to 3.3, NutrientId.CARBS to 4.7)
        val port = fakePort(listOf(milk, usda), mapOf("off_1" to milkNutrients, "usda_9" to milkNutrients))

        val candidates = LoggerSearchV2.packCandidates(port, "leche entera")

        assertEquals("off_1", candidates.first().foodId)
        assertEquals("Colun", candidates.first().food.brand)
        assertEquals(SearchSource.OFF, candidates.first().source)
        assertEquals(SearchConfidence.HIGH, candidates.first().confidence)
        assertTrue(candidates.none { it.foodId == "usda_9" })
    }

    @Test
    fun packFoodByIdBuildsTheRowOrNull() {
        val nutrients = mapOf(NutrientId.ENERGY to 61.0, NutrientId.PROTEIN to 3.1, NutrientId.FAT to 3.3, NutrientId.CARBS to 4.7)
        val port = fakePort(listOf(milkRecord()), mapOf("off_1" to nutrients))
        val food = LoggerSearchV2.packFoodById(port, "off_1")!!
        assertEquals("Leche entera", food.name)
        assertEquals("Colun", food.brand)
        assertNull(LoggerSearchV2.packFoodById(port, "missing"))
    }

    @Test
    fun packCandidatesOfABlankQueryAreEmpty() {
        val port = fakePort(listOf(milkRecord()), mapOf("off_1" to mapOf(NutrientId.ENERGY to 61.0)))
        assertTrue(LoggerSearchV2.packCandidates(port, "   ").isEmpty())
        assertTrue(LoggerSearchV2.packCandidates(port, "leche entera", limit = 0).isEmpty())
    }

    private fun candidate(
        id: String,
        name: String,
        brand: String? = null,
        custom: Boolean = false,
        learned: Boolean = false,
    ) = FoodCandidate(
        foodId = id,
        displayName = name,
        score = 1.0,
        confidence = SearchConfidence.HIGH,
        source = SearchSource.LOCAL,
        food = FoodItem(id = id, name = name, brand = brand, isCustom = custom),
        learned = learned,
    )

    private val legacy = listOf(candidate("static_1", "Pan amasado"), candidate("custom_1", "Mi pan", custom = true))
    private val concepts = listOf(candidate("kpkn_m", "Marraqueta"), candidate("usda_5", "Pan amasado"))
    private val pack = listOf(candidate("off_9", "Pan amasado", brand = "Ideal"), candidate("kpkn_m", "Marraqueta del paquete"))

    @Test
    fun mergePutsOwnFoodsFirstThenConceptsThenTheRestWithoutRepeats() {
        val merged = LoggerSearchV2.merge(legacy, concepts, pack, limit = 20)
        assertEquals(listOf("custom_1", "kpkn_m", "usda_5", "off_9"), merged.map { it.foodId })
    }

    @Test
    fun mergePutsTheLearnedFoodFirstAndMarksIt() {
        val merged = LoggerSearchV2.merge(legacy, concepts, pack, limit = 20, learnedFoodId = "usda_5")
        assertEquals(listOf("usda_5", "custom_1", "kpkn_m", "off_9"), merged.map { it.foodId })
        assertTrue(merged.first().learned)
        assertFalse(merged[1].learned)
    }

    @Test
    fun mergeRespectsTheLimit() {
        assertEquals(2, LoggerSearchV2.merge(legacy, concepts, pack, limit = 2).size)
        assertTrue(LoggerSearchV2.merge(legacy, concepts, pack, limit = 0).isEmpty())
        assertNotNull(LoggerSearchV2.merge(emptyList(), emptyList(), emptyList(), limit = 5))
    }
}

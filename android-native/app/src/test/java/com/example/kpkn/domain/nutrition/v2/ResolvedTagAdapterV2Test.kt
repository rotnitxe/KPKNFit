package com.example.kpkn.domain.nutrition.v2

import com.example.kpkn.data.models.AmountIntent
import com.example.kpkn.domain.nutrition.FoodResolutionStatus
import com.example.kpkn.domain.nutrition.FoodState
import com.example.kpkn.domain.nutrition.NutritionSourceKind
import com.example.kpkn.domain.nutrition.nutrients.CatalogPortion
import com.example.kpkn.domain.nutrition.nutrients.CatalogRecord
import com.example.kpkn.domain.nutrition.nutrients.NutrientId
import com.example.kpkn.domain.nutrition.nutrients.NutritionCatalogPort
import com.example.kpkn.domain.nutrition.nutrients.nutrientAmount
import com.example.kpkn.domain.nutrition.nutrients.withCatalogNutrients
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolvedTagAdapterV2Test {

    private fun concept(id: String, name: String, recordId: String?, state: FoodStateV2? = null) = ConceptV2(
        id = id, name = name, kind = ConceptKindV2.FOOD, category = "test",
        aliasesEs = emptyList(), aliasesEn = emptyList(), typos = emptyList(),
        defaultState = state, defaultForm = FoodFormV2.NA,
        variants = listOf(ConceptVariantV2(state, FoodFormV2.NA, recordId)),
        countable = false, unit = null, portions = emptyList(), defaultGrams = 100.0,
        densityGPerMl = null, reconstitution = null, components = null,
    )

    private val chickenPer100 = mapOf(
        "energy" to 239.0, "protein" to 27.3, "fat" to 13.6, "carbs" to 0.0,
        "sodium" to 82.0, "potassium" to 229.0,
    )
    private val ricePer100 = mapOf("energy" to 130.0, "protein" to 2.7, "fat" to 0.3, "carbs" to 28.2, "fiber" to 0.4, "sugars" to 0.1)
    private val pastelPer100 = mapOf("energy" to 150.5, "protein" to 7.25, "fat" to 6.5, "carbs" to 15.1)

    private val catalog = ConceptCatalogV2(
        generation = "gen-test",
        conceptList = listOf(
            concept("pollo", "Pollo frito", "usda_100", FoodStateV2.FRIED),
            concept("arroz", "Arroz cocido", "ciqual_200", FoodStateV2.COOKED),
            concept("pastel", "Pastel de choclo", "kpkn_pastel_de_choclo"),
            concept("pavo", "Pavo", "usda_300", FoodStateV2.COOKED),
            concept("pato", "Pato", "off_400"),
            concept("sinficha", "Sin ficha", null),
            concept("cuy", "Cuy", "usda_500"),
            concept("ganso", "Ganso", "usda_600"),
            concept("faisan", "Faisán", "usda_700"),
        ),
        records = mapOf(
            "usda_100" to RecordV2("usda_100", "Chicken fried", chickenPer100),
            "ciqual_200" to RecordV2("ciqual_200", "Riz cuit", ricePer100),
            "kpkn_pastel_de_choclo" to RecordV2("kpkn_pastel_de_choclo", "Pastel", pastelPer100),
            "usda_300" to RecordV2("usda_300", "Turkey", mapOf("energy" to 189.0, "protein" to 29.0)),
            "off_400" to RecordV2("off_400", "Duck", mapOf("energy" to 337.0, "protein" to 19.0)),
            "usda_500" to RecordV2("usda_500", "Guinea pig", mapOf("energy" to 100.0)),
            "usda_600" to RecordV2("usda_600", "Goose", mapOf("energy" to 305.0)),
            "usda_700" to RecordV2("usda_700", "Pheasant", mapOf("energy" to 181.0)),
        ),
    )

    private fun mention(
        text: String = "pollo frito",
        conceptId: String? = "pollo",
        conceptName: String? = "Pollo frito",
        recordId: String? = "usda_100",
        grams: Double? = 150.0,
        kind: QuantityKindV2 = QuantityKindV2.MASS,
        state: FoodStateV2? = FoodStateV2.FRIED,
        per100g: Map<String, Double> = chickenPer100,
        excluded: Boolean = false,
        fuzzy: Boolean = false,
        questions: List<QuestionV2> = emptyList(),
        confidence: Double = 0.9,
    ) = MentionV2(
        draft = MentionDraftV2(0, text.length, 0, text.length, listOfNotNull(conceptId), null, excluded = excluded, fuzzy = fuzzy),
        text = text, conceptId = conceptId, conceptName = conceptName, recordId = recordId, grams = grams,
        quantityKind = kind, state = state, form = FoodFormV2.NA, per100g = per100g,
        eatenKcal = null, eatenProtein = null, eatenFat = null, eatenCarbs = null,
        excluded = excluded, confidence = confidence, questions = questions, autoAccepted = questions.isEmpty(),
    )

    private fun adapt(vararg mentions: MentionV2) =
        ResolvedTagAdapterV2.toResolvedTags(MealParseV2("texto", mentions.toList()), catalog)

    @Test
    fun resolvedMentionScalesExactlyAndLinksCatalog() {
        val tag = adapt(mention()).single()
        val logged = tag.loggedFood!!
        assertTrue(tag.isResolved)
        assertEquals("pollo frito", tag.tag)
        assertEquals(150.0, tag.amountGrams!!, 1e-9)
        assertEquals(150.0, tag.baseAmountGrams!!, 1e-9)
        assertEquals("usda_100", tag.foodItem!!.id)
        assertEquals("Pollo frito", tag.foodItem!!.name)
        assertEquals(239.0, tag.foodItem!!.calories, 1e-9)
        assertEquals(82.0, tag.foodItem!!.micronutrients.first { it.name == "Sodio" }.amount, 1e-9)
        assertEquals(150.0, logged.amount, 1e-9)
        assertEquals(239.0 * 1.5, logged.calories, 0.01)
        assertEquals(27.3 * 1.5, logged.protein, 0.01)
        assertEquals(13.6 * 1.5, logged.fats, 0.01)
        assertEquals(0.0, logged.carbs, 0.01)
        assertEquals(82.0 * 1.5, logged.sodiumMg, 0.01)
        assertEquals("usda_100", logged.catalogRecordId)
        // Ficha del paquete: la instantánea completa la pone el guardado (withCatalogNutrients), no el adaptador.
        assertNull(logged.nutrientsPer100g)
        assertEquals("gen-test", logged.catalogGeneration)
        assertEquals(logged, tag.baseLoggedFood)
        assertEquals(FoodState.COOKED, tag.foodState)
        assertTrue(tag.oilApplied)
        assertEquals(AmountIntent.EXPLICIT_MASS, tag.amountIntent)
        assertEquals(FoodResolutionStatus.AUTO, tag.resolutionStatus)
        assertEquals(NutritionSourceKind.VERIFIED_GLOBAL, tag.nutritionSource)
        assertEquals(0.9, tag.resolutionConfidence!!, 1e-9)
        assertEquals("Interpretado", tag.statusText)
    }

    @Test
    fun nonIntegerMacrosAreNotRounded() {
        val tag = adapt(mention(text = "pastel", conceptId = "pastel", conceptName = "Pastel de choclo", recordId = "kpkn_pastel_de_choclo", grams = 333.0, state = null, per100g = pastelPer100)).single()
        val logged = tag.loggedFood!!
        assertEquals(150.5 * 3.33, logged.calories, 0.01)
        assertEquals(7.25 * 3.33, logged.protein, 0.01)
        assertEquals(6.5 * 3.33, logged.fats, 0.01)
        assertEquals(15.1 * 3.33, logged.carbs, 0.01)
    }

    @Test
    fun kpknRecordHasNoCatalogLinkAndUnknownState() {
        val tag = adapt(mention(text = "pastel", conceptId = "pastel", conceptName = "Pastel de choclo", recordId = "kpkn_pastel_de_choclo", state = null, per100g = pastelPer100)).single()
        assertNull(tag.loggedFood!!.catalogRecordId)
        assertEquals(pastelPer100, tag.loggedFood!!.nutrientsPer100g)
        assertEquals(FoodState.UNKNOWN, tag.foodState)
        assertEquals(NutritionSourceKind.CURATED_LOCAL, tag.nutritionSource)
    }

    @Test
    fun ciqualAndOffRecordsLinkToCatalogAndStatesMap() {
        val rice = adapt(mention(text = "arroz", conceptId = "arroz", conceptName = "Arroz cocido", recordId = "ciqual_200", state = FoodStateV2.STEAMED, per100g = ricePer100)).single()
        assertEquals("ciqual_200", rice.loggedFood!!.catalogRecordId)
        assertEquals(FoodState.COOKED, rice.foodState)
        val raw = adapt(mention(recordId = "off_400", state = FoodStateV2.RAW)).single()
        assertEquals("off_400", raw.loggedFood!!.catalogRecordId)
        assertEquals(FoodState.RAW, raw.foodState)
    }

    @Test
    fun missingKeyIsNotInventedInSnapshotOrBreakdown() {
        val tag = adapt(mention()).single()
        // Sin "fiber" ni "sugars" en la ficha: el FoodItem no tiene desglose y la instantánea no trae la clave.
        assertNull(tag.foodItem!!.carbBreakdown)
        val derived = adapt(mention(recordId = "kpkn_pollo")).single()
        assertFalse(derived.loggedFood!!.nutrientsPer100g!!.containsKey("fiber"))
        // LoggedFood.fiber es Double no nulo: el tipo no permite «sin dato», queda 0.0; la ausencia real viaja en nutrientsPer100g.
        assertEquals(0.0, tag.loggedFood!!.fiber, 0.0)

        val withFiber = adapt(mention(text = "arroz", conceptId = "arroz", conceptName = "Arroz cocido", recordId = "ciqual_200", per100g = ricePer100)).single()
        assertEquals(0.4, withFiber.foodItem!!.carbBreakdown!!.fiber, 1e-9)
        assertEquals(0.4 * 1.5, withFiber.loggedFood!!.fiber, 0.01)
    }

    @Test
    fun identityQuestionBuildsReviewCandidatesWithoutTheChosenOne() {
        val tag = adapt(
            mention(
                text = "pavo", conceptId = "pavo", conceptName = "Pavo", recordId = "usda_300", state = FoodStateV2.COOKED,
                per100g = mapOf("energy" to 189.0),
                questions = listOf(QuestionV2(QuestionDimensionV2.IDENTITY, listOf("pavo", "pato", "sinficha", "cuy", "ganso", "faisan"))),
            ),
        ).single()
        assertEquals(listOf("off_400", "usda_500", "usda_600", "usda_700"), tag.reviewCandidates.map { it.id })
        assertEquals(listOf("Pato", "Cuy", "Ganso", "Faisán"), tag.reviewCandidates.map { it.name })
        assertEquals("Revisar: identidad", tag.statusText)
        assertEquals(FoodResolutionStatus.NEEDS_CONFIRMATION, tag.resolutionStatus)
        assertFalse(tag.needsCookingClarification)
    }

    @Test
    fun stateQuestionFlagsCookingClarification() {
        val tag = adapt(mention(questions = listOf(QuestionV2(QuestionDimensionV2.STATE, listOf("RAW", "COOKED"))))).single()
        assertTrue(tag.needsCookingClarification)
        assertEquals("Revisar: estado", tag.statusText)
        assertEquals(FoodResolutionStatus.NEEDS_STATE, tag.resolutionStatus)
        assertTrue(tag.reviewCandidates.isEmpty())
    }

    @Test
    fun excludedMentionKeepsFoodAndIsMarked() {
        val tag = adapt(mention(excluded = true, questions = listOf(QuestionV2(QuestionDimensionV2.QUANTITY, emptyList())))).single()
        assertTrue(tag.isExcluded)
        assertEquals("Excluido", tag.statusText)
        assertNotNull(tag.loggedFood)
    }

    @Test
    fun mentionWithoutRecordOrGramsIsUnresolved() {
        val tags = adapt(
            mention(text = "algo", conceptId = null, conceptName = null, recordId = null, grams = null, per100g = emptyMap()),
            mention(text = "sin gramos", grams = null),
            mention(text = "sin ficha", conceptId = "sinficha", conceptName = "Sin ficha", recordId = null, per100g = emptyMap()),
        )
        assertEquals(3, tags.size)
        tags.forEach {
            assertFalse(it.isResolved)
            assertEquals("Sin coincidencia", it.statusText)
            assertNull(it.foodItem)
            assertNull(it.loggedFood)
        }
        assertEquals(listOf("algo", "sin gramos", "sin ficha"), tags.map { it.tag })
    }

    @Test
    fun quantityKindMapsToAmountIntent() {
        val intents = QuantityKindV2.entries.associateWith { kind -> adapt(mention(kind = kind)).single().amountIntent }
        assertEquals(AmountIntent.EXPLICIT_MASS, intents[QuantityKindV2.MASS])
        assertEquals(AmountIntent.EXPLICIT_MASS, intents[QuantityKindV2.VOLUME])
        assertEquals(AmountIntent.RESOLVED_SUBJECTIVE, intents[QuantityKindV2.COUNT])
        assertEquals(AmountIntent.RESOLVED_SUBJECTIVE, intents[QuantityKindV2.HOUSEHOLD])
        assertEquals(AmountIntent.RESOLVED_SUBJECTIVE, intents[QuantityKindV2.CONTAINER])
        assertEquals(AmountIntent.INFERRED_CONTEXT, intents[QuantityKindV2.VAGUE])
        assertEquals(AmountIntent.INFERRED_CONTEXT, intents[QuantityKindV2.DEFAULT])
    }

    @Test
    fun fuzzyFlagComesFromDraft() {
        assertTrue(adapt(mention(fuzzy = true)).single().isFuzzyMatch)
        assertFalse(adapt(mention(fuzzy = false)).single().isFuzzyMatch)
    }

    @Test
    fun orderAndCountFollowMentions() {
        val tags = adapt(
            mention(text = "uno"),
            mention(text = "dos", recordId = null, grams = null),
            mention(text = "tres", excluded = true),
            mention(text = "cuatro"),
        )
        assertEquals(listOf("uno", "dos", "tres", "cuatro"), tags.map { it.tag })
        assertEquals(listOf(true, false, true, true), tags.map { it.isResolved })
        assertEquals(0, ResolvedTagAdapterV2.toResolvedTags(MealParseV2("", emptyList()), catalog).size)
    }

    @Test
    fun packRecordGetsTheFullSnapshotWhenSaved() {
        val logged = adapt(mention()).single().loggedFood!!
        val port = object : NutritionCatalogPort {
            override val generation = "gen-pack"
            override fun record(id: String) = CatalogRecord(id, "USDA", "100", "Chicken, fried", null, null, null, "FRIED", "NA", 100.0, "g", null)
                .takeIf { id == "usda_100" }
            override fun nutrientsPer100(id: String) =
                if (id == "usda_100") mapOf(NutrientId.ENERGY to 239.0, NutrientId.CALCIUM to 15.0, NutrientId.IRON to 1.2) else emptyMap()
            override fun portions(id: String) = emptyList<CatalogPortion>()
            override fun byGtin(gtin: String): CatalogRecord? = null
            override fun search(queryNorm: String, limit: Int) = emptyList<CatalogRecord>()
            override fun recordFor(source: String, sourceRecordId: String): CatalogRecord? = null
        }
        // El guardado (F4-B) completa los micronutrientes que el vocabulario no trae.
        val saved = logged.withCatalogNutrients(port)
        assertEquals(15.0 * 1.5, saved.nutrientAmount(NutrientId.CALCIUM)!!, 1e-9)
        assertEquals(1.2 * 1.5, saved.nutrientAmount(NutrientId.IRON)!!, 1e-9)
        assertEquals("gen-pack", saved.catalogGeneration)
    }
}

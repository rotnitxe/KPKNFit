package com.example.kpkn.domain.nutrition.v2

import com.example.kpkn.domain.nutrition.NutritionInterpretationBridge
import com.example.kpkn.domain.nutrition.hasMaterialQuestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** «Usar lo estimado en todo»: canAcceptEstimate y acceptAllEstimates del puente de interpretación. */
class NutritionInterpretationBridgeAcceptAllTest {

    private fun concept(id: String, name: String, recordId: String, state: FoodStateV2) = ConceptV2(
        id = id, name = name, kind = ConceptKindV2.FOOD, category = "test",
        aliasesEs = emptyList(), aliasesEn = emptyList(), typos = emptyList(),
        defaultState = state, defaultForm = FoodFormV2.NA,
        variants = listOf(ConceptVariantV2(state, FoodFormV2.NA, recordId)),
        countable = false, unit = null, portions = emptyList(), defaultGrams = 100.0,
        densityGPerMl = null, reconstitution = null, components = null,
    )

    private val chickenPer100 = mapOf("energy" to 239.0, "protein" to 27.3, "fat" to 13.6, "carbs" to 0.0)
    private val ricePer100 = mapOf("energy" to 130.0, "protein" to 2.7, "fat" to 0.3, "carbs" to 28.2)

    private val catalog = ConceptCatalogV2(
        generation = "gen-test",
        conceptList = listOf(
            concept("pollo", "Pollo frito", "usda_100", FoodStateV2.FRIED),
            concept("arroz", "Arroz cocido", "ciqual_200", FoodStateV2.COOKED),
        ),
        records = mapOf(
            "usda_100" to RecordV2("usda_100", "Chicken fried", chickenPer100),
            "ciqual_200" to RecordV2("ciqual_200", "Riz cuit", ricePer100),
        ),
    )

    private fun mention(
        text: String,
        conceptId: String,
        conceptName: String,
        recordId: String,
        state: FoodStateV2,
        per100g: Map<String, Double>,
        autoAccepted: Boolean,
    ) = MentionV2(
        draft = MentionDraftV2(0, text.length, 0, text.length, listOf(conceptId), null, excluded = false, fuzzy = false),
        text = text, conceptId = conceptId, conceptName = conceptName, recordId = recordId, grams = 150.0,
        quantityKind = QuantityKindV2.MASS, state = state, form = FoodFormV2.NA, per100g = per100g,
        eatenKcal = null, eatenProtein = null, eatenFat = null, eatenCarbs = null,
        excluded = false, confidence = 0.9, questions = emptyList(), autoAccepted = autoAccepted,
    )

    private fun pollo(autoAccepted: Boolean) =
        mention("pollo frito", "pollo", "Pollo frito", "usda_100", FoodStateV2.FRIED, chickenPer100, autoAccepted)

    private fun arroz(autoAccepted: Boolean) =
        mention("arroz", "arroz", "Arroz cocido", "ciqual_200", FoodStateV2.COOKED, ricePer100, autoAccepted)

    private fun adapt(vararg mentions: MentionV2) =
        ResolvedTagAdapterV2.toResolvedTags(MealParseV2("texto", mentions.toList()), catalog)

    @Test
    fun acceptAllEstimatesClearsEveryMaterialQuestion() {
        val tags = adapt(pollo(autoAccepted = false), arroz(autoAccepted = false))
        assertEquals(2, tags.size)
        assertTrue(tags.all { it.hasMaterialQuestion() })
        assertTrue(tags.all { NutritionInterpretationBridge.canAcceptEstimate(it) })

        val accepted = NutritionInterpretationBridge.acceptAllEstimates(tags)

        assertEquals(2, accepted.size)
        assertTrue(accepted.none { it.hasMaterialQuestion() })
    }

    @Test
    fun unresolvedDeclaredAmountCannotAcceptEstimateAndStaysTheSameObject() {
        val tag = adapt(pollo(autoAccepted = false)).single().copy(unresolvedDeclaredAmount = true)
        assertTrue(tag.hasMaterialQuestion())
        assertFalse(NutritionInterpretationBridge.canAcceptEstimate(tag))

        val result = NutritionInterpretationBridge.acceptAllEstimates(listOf(tag)).single()

        assertSame(tag, result)
    }

    @Test
    fun tagWithoutQuestionStaysTheSameObject() {
        val tag = adapt(pollo(autoAccepted = true)).single()
        assertFalse(tag.hasMaterialQuestion())
        assertFalse(NutritionInterpretationBridge.canAcceptEstimate(tag))

        val result = NutritionInterpretationBridge.acceptAllEstimates(listOf(tag)).single()

        assertSame(tag, result)
    }

    @Test
    fun mixedListOnlyChangesTheEligibleCards() {
        val asking = adapt(pollo(autoAccepted = false)).single()
        val settled = adapt(arroz(autoAccepted = true)).single()

        val result = NutritionInterpretationBridge.acceptAllEstimates(listOf(asking, settled))

        assertFalse(result[0].hasMaterialQuestion())
        assertSame(settled, result[1])
    }
}

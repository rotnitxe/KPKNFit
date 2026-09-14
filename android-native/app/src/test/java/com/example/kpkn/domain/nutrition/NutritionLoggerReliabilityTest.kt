package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.food.findFoodExactByNormalized
import com.example.kpkn.data.food.findStaticFoodById
import com.example.kpkn.data.models.*
import org.junit.Assert.*
import org.junit.Test

class NutritionLoggerReliabilityTest {
    private fun rice(intent: AmountIntent = AmountIntent.UNSPECIFIED): ResolvedTag {
        val food = findFoodExactByNormalized("arroz cocido")!!
        return ResolvedTag(
            tag = "arroz", foodItem = food, amountGrams = 200.0, baseAmountGrams = 200.0,
            amountIntent = intent, loggedFood = scaleFoodByPortion(food, amountGrams = 200.0),
            isResolved = true, resolutionStatus = FoodResolutionStatus.AUTO,
            nutritionSource = NutritionSourceKind.CURATED_LOCAL, foodState = FoodState.COOKED,
            stateAssumed = true, resolutionConfidence = 0.95,
        )
    }

    @Test fun `habitual quantity remains saveable with an honest interval`() {
        val tag = NutritionInterpretationBridge.refresh(rice())
        val result = tag.interpretationV2!!
        assertTrue(result.pendingQuestions.isEmpty())
        assertTrue(result.canFinalize())
        assertTrue(result.isUncertain)
        assertTrue(result.caloriesMax > result.caloriesMin)
        assertEquals(200.0, result.observedGrams!!, 0.0)
        assertNull(tag.confirmedLearning())
        val log = result.toLoggedFood(tag.loggedFood!!)
        assertEquals(result.calories, log.calories, 0.0)
        assertEquals(result.caloriesMin, log.caloriesMin!!, 0.0)
        assertTrue(log.evidenceJson!!.contains("selectedCandidateId"))
        assertTrue(log.evidenceJson!!.contains("CURATED_LOCAL"))
    }

    @Test fun `explicit rice mass keeps critical state question until answer`() {
        val tag = NutritionInterpretationBridge.refresh(rice(AmountIntent.EXPLICIT_MASS))
        assertTrue(tag.interpretationV2!!.pendingQuestions.any { it is ClarificationRequest.WeightState })
        assertFalse(tag.interpretationV2!!.canFinalize())
        assertFalse(tag.isResolved)
        val answered = NutritionInterpretationBridge.refresh(tag.copy(
            cookingMethod = CookingMethod.COCIDO, confirmedDimensions = setOf("state"),
        ))
        assertTrue(answered.interpretationV2!!.canFinalize())
        assertEquals(setOf("state"), answered.confirmedLearning()!!.dimensions)
        assertNull(answered.confirmedLearning()!!.portionGrams)
    }

    @Test fun `unsure keeps state interval without creating any training event`() {
        val unresolved = NutritionInterpretationBridge.refresh(rice(AmountIntent.EXPLICIT_MASS))
        val unsure = NutritionInterpretationBridge.refresh(unresolved.copy(
            explicitDecision = true, isUncertain = true, confirmedDimensions = emptySet(),
        ))
        assertTrue(unsure.interpretationV2!!.canFinalize())
        assertTrue(unsure.interpretationV2!!.isUncertain)
        assertFalse(unsure.interpretationV2!!.isConfirmedEstimate)
        assertEquals(unresolved.interpretationV2!!.caloriesMin, unsure.interpretationV2!!.caloriesMin, 0.0)
        assertEquals(unresolved.interpretationV2!!.caloriesMax, unsure.interpretationV2!!.caloriesMax, 0.0)
        assertNull(unsure.confirmedLearning())
    }

    @Test fun `numbers do not confirm an unknown identity`() {
        val estimate = ResolvedTag(
            tag = "comida desconocida", amountGrams = 150.0,
            loggedFood = LoggedFood(foodName = "Estimación", amount = 150.0, calories = 250.0),
            resolutionStatus = FoodResolutionStatus.NEEDS_CONFIRMATION,
            nutritionSource = NutritionSourceKind.HEURISTIC_ESTIMATE,
        )
        val result = NutritionInterpretationBridge.refresh(estimate).interpretationV2!!
        assertEquals(FoodSource.HEURISTIC, result.source)
        assertTrue(result.pendingQuestions.first() is ClarificationRequest.Identity)
        assertFalse(result.canFinalize())
    }

    @Test fun `an unread declared measure remains blocked even when an estimate was accepted`() {
        val fallback = rice().copy(unresolvedDeclaredAmount = true, explicitDecision = true, confirmedDimensions = emptySet())
        val result = NutritionInterpretationBridge.refresh(fallback).interpretationV2!!
        assertFalse(result.canFinalize())
        assertTrue(result.pendingQuestions.any { it.requestId == "declared_amount" })
        assertTrue(NutritionInterpretationBridge.refresh(fallback.copy(unresolvedDeclaredAmount = false)).interpretationV2!!.canFinalize())
    }

    private fun packagedMilk(): ResolvedTag {
        val food = findStaticFoodById("gen016")!!
        return ResolvedTag(tag = "leche", foodItem = food, amountGrams = 200.0, baseAmountGrams = 200.0,
            amountIntent = AmountIntent.UNSPECIFIED, unitId = "ml", ambiguousPackageGrams = 1030.0,
            loggedFood = scaleFoodByPortion(food, amountGrams = 200.0), resolutionStatus = FoodResolutionStatus.AUTO,
            nutritionSource = NutritionSourceKind.CURATED_LOCAL)
    }

    @Test fun `package ambiguity offers the whole container or habitual portion while unsure keeps interval`() {
        val tag = NutritionInterpretationBridge.refresh(packagedMilk())
        val result = tag.interpretationV2!!
        val question = result.pendingQuestions.filterIsInstance<ClarificationRequest.Portion>().single()
        assertEquals("package_portion", question.requestId)
        assertEquals(listOf(1030.0, 200.0), question.options.map { it.grams })
        assertTrue(question.options.first().label.contains("1000 ml"))
        assertFalse(result.canFinalize())
        assertTrue(result.caloriesMax >= result.calories * 5.0)
        val unsure = NutritionInterpretationBridge.refresh(tag.copy(explicitDecision = true, confirmedDimensions = emptySet()))
        assertTrue(unsure.interpretationV2!!.canFinalize())
        assertEquals(200.0, unsure.interpretationV2!!.observedGrams!!, 0.0)
        assertEquals(result.caloriesMax, unsure.interpretationV2!!.caloriesMax, 0.0)
        assertNull(unsure.confirmedLearning())
    }

    @Test fun `confirming the complete package records exactly its canonical grams`() {
        val tag = packagedMilk()
        val answered = NutritionInterpretationBridge.refresh(tag.copy(
            ambiguousPackageGrams = null, amountGrams = 1030.0, baseAmountGrams = 1030.0,
            amountIntent = AmountIntent.EXPLICIT_MASS, portionMinGrams = 1030.0, portionMaxGrams = 1030.0,
            loggedFood = scaleFoodByPortion(tag.foodItem!!, amountGrams = 1030.0), confirmedDimensions = setOf("portion"),
        ))
        val result = answered.interpretationV2!!
        assertTrue(result.canFinalize())
        assertEquals(1030.0, result.toLoggedFood(answered.loggedFood!!).amount, 0.0)
        assertEquals(setOf("portion"), answered.confirmedLearning()!!.dimensions)
        assertEquals(1030.0, answered.confirmedLearning()!!.portionGrams!!, 0.0)
    }

    @Test fun `one mention never suppresses another mentions material cut question`() {
        val chicken = findStaticFoodById("gen004")!!
        val tag = rice().copy(tag = "pollo", foodQuery = "pollo", foodItem = chicken,
            loggedFood = scaleFoodByPortion(chicken, amountGrams = 200.0))
        val alone = NutritionInterpretationBridge.enrich(tag, parseMealDescription("pollo"))
        val combined = NutritionInterpretationBridge.enrich(tag, parseMealDescription("pechuga y pollo"))
        assertEquals(alone.interpretationV2!!.pendingQuestions.map { it.requestId }, combined.interpretationV2!!.pendingQuestions.map { it.requestId })
        assertTrue(combined.interpretationV2!!.pendingQuestions.any { it.requestId == "cut" })
    }

    @Test fun `cut selection cannot reintroduce an excluded ingredient`() {
        val food = findStaticFoodById("gen004")!!
        val tag = rice().copy(excludedIngredients = setOf("pollo"))
        assertEquals(tag, NutritionInterpretationBridge.applyCutOption(tag, food.id))
    }

    @Test fun `interpreting resolved mentions never reparses and drops subsequent foods`() {
        val engine = FoodInterpretationV2Engine(lookup = { error("resolved interpretation must not query or parse again") })
        val tags = listOf(rice().copy(id = "rice"), rice().copy(id = "second", tag = "another resolved mention"))
        val results = tags.map { engine.interpretResolved(it) }
        assertEquals(listOf("rice", "second"), results.map { it.draftId })
        assertEquals(tags.map { it.loggedFood!!.calories }, results.map { it.calories })
    }

    @Test fun `estimate sizes always scale from immutable original nutrients`() {
        val base = LoggedFood(foodName = "Comida estimada", amount = 100.0, calories = 180.0, protein = 8.0, carbs = 20.0, fats = 7.0)
        val large = rescaleEstimatedFood(base, 125.0)
        val medium = rescaleEstimatedFood(base, 100.0)
        val again = rescaleEstimatedFood(base, 125.0)
        assertEquals(225.0, large.calories, 0.0)
        assertEquals(base, medium)
        assertEquals(large, again)
    }

    @Test fun `manual nutrients become the immutable density for later size changes`() {
        val corrected = ResolvedTag(tag = "estimación", baseAmountGrams = 100.0, amountGrams = 100.0,
            baseLoggedFood = LoggedFood(amount = 100.0, calories = 200.0),
            loggedFood = LoggedFood(amount = 100.0, calories = 300.0)).rebaseManualNutrients()
        val large = rescaleEstimatedFood(corrected.baseLoggedFood!!, 130.0)
        val small = rescaleEstimatedFood(corrected.baseLoggedFood!!, 80.0)
        assertEquals(390.0, large.calories, 0.0)
        assertEquals(240.0, small.calories, 0.0)
        assertEquals(large, rescaleEstimatedFood(corrected.baseLoggedFood!!, 130.0))
        assertTrue(corrected.nutrientsManuallyEdited)
        assertEquals(NutritionSourceKind.USER_PROVIDED, corrected.nutritionSource)
    }

    @Test fun `converted cooked food uses calculated state in card and saved name while preserving raw source`() {
        val food = findStaticFoodById("gen023")!!
        val tag = ResolvedTag(tag = "espinaca", foodItem = food, foodState = FoodState.COOKED,
            cookingMethod = CookingMethod.COCIDO, amountGrams = 100.0,
            loggedFood = scaleFoodByPortion(food, amountGrams = 100.0, cookingMethod = CookingMethod.COCIDO))
        val result = FoodInterpretationV2Engine().interpretResolved(tag)
        assertEquals(WeightBasis.COOKED, result.weightBasis)
        assertTrue(result.canonicalIdentity!!.contains("cocido"))
        assertFalse(result.canonicalIdentity!!.contains("cruda"))
        assertEquals(food.id, result.selectedCandidateId)
        assertTrue(result.transformations.any { it.contains("RAW->COOKED") })
        assertEquals(result.canonicalIdentity, result.toLoggedFood(tag.loggedFood!!).foodName)
    }

    @Test fun `critical nutrition differences compare equal eaten amounts`() {
        val a = FoodItem(id = "a", name = "A", servingSize = 100.0, calories = 100.0, protein = 2.0)
        val b = FoodItem(id = "b", name = "B", servingSize = 100.0, calories = 160.0, protein = 2.0)
        assertFalse(NutritionInterpretationBridge.materialNutritionDelta(a, b, 50.0))
        assertTrue(NutritionInterpretationBridge.materialNutritionDelta(a, b, 100.0))
    }

    @Test fun `source serving size never changes a per100g materiality comparison`() {
        val a = FoodItem(id = "a", name = "A", servingSize = 50.0, nutritionBasis = "PER_100G_AS_SOLD", calories = 100.0)
        val b = FoodItem(id = "b", name = "B", servingSize = 100.0, nutritionBasis = "PER_100G_AS_SOLD", calories = 130.0)
        assertFalse(NutritionInterpretationBridge.materialNutritionDelta(a, b, 100.0))
        assertEquals(0.3, NutritionInterpretationBridge.materialKcalDelta(a, b, 100.0), 0.0001)
    }

    @Test fun `custom provenance and original unit survive canonical gram logging`() {
        val result = FoodInterpretationV2Engine().interpretResolved(rice().copy(
            nutritionSource = NutritionSourceKind.USER_PROVIDED, unitId = "taza",
        ))
        assertEquals(FoodSource.MANUAL, result.source)
        assertEquals("taza", result.declaredUnitId)
        val logged = result.toLoggedFood(LoggedFood(amount = 200.0, unit = "ml"))
        assertEquals("g", logged.unit)
        assertTrue(logged.evidenceJson!!.contains("taza"))
        assertTrue(logged.evidenceJson!!.contains("MANUAL"))
    }

    @Test fun `subjective size confirmation never becomes an explicit raw or cooked weight`() {
        val initial = rice().copy(amountGrams = 120.0, baseAmountGrams = 120.0)
        val size = NutritionInterpretationBridge.refresh(initial.copy(
            amountIntent = AmountIntent.RESOLVED_SUBJECTIVE, amountGrams = 180.0,
            portionMinGrams = 135.0, portionMaxGrams = 225.0, portion = PortionPreset.LARGE,
            loggedFood = scaleFoodByPortion(initial.foodItem!!, amountGrams = 180.0), confirmedDimensions = setOf("portion"),
        ))
        assertTrue(size.interpretationV2!!.pendingQuestions.none { it is ClarificationRequest.WeightState })
        assertTrue(size.interpretationV2!!.canFinalize())
        assertEquals(234.0, size.interpretationV2!!.calories, 0.01)
        assertTrue(size.interpretationV2!!.isUncertain)
        assertEquals(120.0, size.baseAmountGrams!!, 0.0)
    }

    private fun estimatedTag(name: String): ResolvedTag {
        val estimate = NutritionHeuristicEstimator.estimateWithEvidence(name, listOf("gen066", "gen026").mapNotNull(::findStaticFoodById))
        return ResolvedTag(tag = name, amountGrams = 100.0, baseAmountGrams = 100.0,
            amountIntent = AmountIntent.EXPLICIT_MASS, portionMinGrams = 100.0, portionMaxGrams = 100.0,
            loggedFood = LoggedFood(foodName = "$name (estimado)", amount = 100.0, calories = estimate.profile.calories,
                protein = estimate.profile.protein, carbs = estimate.profile.carbs, fats = estimate.profile.fats),
            nutritionSource = NutritionSourceKind.HEURISTIC_ESTIMATE, nutritionEstimate = estimate.evidence,
            resolutionStatus = FoodResolutionStatus.NEEDS_REVIEW)
    }

    @Test fun `exact grams and accepted unknown food retain density uncertainty and lack of reference`() {
        val pending = NutritionInterpretationBridge.refresh(estimatedTag("zorbatrón"))
        assertEquals(0.0, pending.interpretationV2!!.caloriesMin, 0.0)
        assertEquals(900.0, pending.interpretationV2!!.caloriesMax, 0.0)
        val accepted = NutritionInterpretationBridge.refresh(pending.copy(explicitDecision = true, confirmedDimensions = emptySet()))
        assertTrue(accepted.interpretationV2!!.canFinalize())
        assertTrue(accepted.loggedFood!!.nutritionReferenceNote!!.contains("Sin referencia nutricional"))
        assertTrue(accepted.loggedFood!!.evidenceJson!!.contains("unmatched_estimate"))
        assertFalse(accepted.interpretationV2!!.isConfirmedEstimate)
        assertNull(accepted.confirmedLearning())
        val resized = NutritionInterpretationBridge.refresh(accepted.copy(amountGrams = 160.0, portionMinGrams = 160.0, portionMaxGrams = 160.0,
            loggedFood = rescaleEstimatedFood(accepted.baseLoggedFood!!, 160.0)))
        assertEquals(1440.0, resized.interpretationV2!!.caloriesMax, 0.0)
        assertEquals(0.0, resized.interpretationV2!!.caloriesMin, 0.0)
        assertEquals(accepted.loggedFood!!.nutritionReferenceNote, resized.loggedFood!!.nutritionReferenceNote)
        assertNull(resized.confirmedLearning())
    }

    @Test fun `salad composition choice narrows only to referenced vegetables without learning an exact recipe`() {
        val pending = NutritionInterpretationBridge.refresh(estimatedTag("ensalada"))
        val question = pending.interpretationV2!!.pendingQuestions.first() as ClarificationRequest.Identity
        assertEquals("composition", question.requestId)
        assertTrue(question.candidateIds.contains(NutritionInterpretationBridge.VEGETABLE_COMPOSITION_ID))
        assertEquals(16.5, pending.interpretationV2!!.calories, 0.01)
        val confirmed = NutritionInterpretationBridge.confirmEstimatedComposition(pending)
        assertTrue(confirmed.interpretationV2!!.canFinalize())
        assertTrue(confirmed.interpretationV2!!.isUncertain)
        assertEquals(FoodSource.HEURISTIC, confirmed.interpretationV2!!.source)
        assertTrue(confirmed.interpretationV2!!.caloriesMax < 30.0)
        assertTrue(confirmed.interpretationV2!!.caloriesMax > confirmed.interpretationV2!!.caloriesMin)
        assertTrue(confirmed.loggedFood!!.evidenceJson!!.contains("169249"))
        assertTrue(confirmed.loggedFood!!.evidenceJson!!.contains("170457"))
        assertNull(confirmed.confirmedLearning())
        val unsure = NutritionInterpretationBridge.refresh(pending.copy(explicitDecision = true, confirmedDimensions = emptySet()))
        assertEquals(pending.interpretationV2!!.caloriesMax, unsure.interpretationV2!!.caloriesMax, 0.0)
        assertNull(unsure.confirmedLearning())
    }

    private val breakfast = MealTemplate(
        id = "breakfast", name = "Avena leche huevo", description = "avena 100g leche 250g huevo 60g",
        foods = listOf(
            LoggedFood(foodName = "avena", amount = 100.0),
            LoggedFood(foodName = "leche", amount = 250.0),
            LoggedFood(foodName = "huevo", amount = 60.0),
        ),
    )

    @Test fun `templates never introduce omitted or negated foods or override declared mass`() {
        for (query in listOf("avena", "avena sin leche ni huevo", "150 g avena con leche y huevo")) {
            val score = FoodTemplateMatcher.score(breakfast, FoodTemplateMatcher.normalizeSearchText(query))
            assertTrue("$query incorrectly recovered a whole meal: $score", score < FoodTemplateMatcher.THRESHOLD)
        }
    }
}

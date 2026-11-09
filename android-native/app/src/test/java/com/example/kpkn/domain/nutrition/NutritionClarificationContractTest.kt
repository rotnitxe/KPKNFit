package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.FoodItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionClarificationContractTest {
    @Test
    fun `portion options are absolute and stable`() {
        val first = absolutePortionOptions(200.0)
        val second = absolutePortionOptions(200.0)
        assertEquals(listOf("Pequeña", "Habitual", "Grande"), first.map { it.first })
        assertEquals(first, second)
        assertEquals(150.0, first[0].second, 0.0)
        assertEquals(250.0, first[2].second, 0.0)
    }

    @Test
    fun `material question gate accepts an explicit unsure decision but blocks unresolved identity`() {
        val unresolved = ResolvedTag(tag = "algo", resolutionConfidence = 0.4)
        assertTrue(unresolved.hasMaterialQuestion())
        val unsure = unresolved.copy(
            foodItem = FoodItem(id = "manual", name = "algo"),
            loggedFood = com.example.kpkn.data.models.LoggedFood(foodName = "algo", calories = 100.0),
            isResolved = true,
            isUncertain = true,
            explicitDecision = true,
        )
        assertFalse(unsure.hasMaterialQuestion())
    }

    @Test
    fun `auto assumed state is not a material question`() {
        val auto = ResolvedTag(
            tag = "arroz",
            foodItem = FoodItem(id = "gen005", name = "Arroz Blanco (cocido)"),
            loggedFood = com.example.kpkn.data.models.LoggedFood(foodName = "Arroz Blanco (cocido)", calories = 130.0),
            isResolved = true,
            resolutionStatus = FoodResolutionStatus.AUTO,
            stateAssumed = true,
            resolutionConfidence = 0.72,
            resolutionMargin = 0.10,
        )
        assertFalse(auto.hasMaterialQuestion())
    }

    @Test
    fun `identity clarification chips hide unverified and wrong brand candidates`() {
        fun milk(id: String, brand: String, calories: Double = 61.0) = FoodItem(
            id = id, name = "Leche entera", brand = brand, calories = calories,
            protein = 3.1, carbs = 4.7, fats = 3.3, nutritionBasis = "PER_100G",
            source = "OPEN_FOOD_FACTS", sourceRecordId = id,
        )
        val tag = ResolvedTag(
            tag = "Leche Colun",
            foodQuery = "leche colun",
            brandHint = "Colun",
            reviewCandidates = listOf(milk("colun", "Colun"), milk("lider", "Lider"), milk("bad", "Colun", calories = 0.0)),
            resolutionStatus = FoodResolutionStatus.NEEDS_REVIEW,
            resolutionConfidence = 0.4,
        )

        val refreshed = NutritionInterpretationBridge.refresh(tag)
        val identity = refreshed.interpretationV2!!.pendingQuestions.filterIsInstance<ClarificationRequest.Identity>().single()

        assertEquals(listOf("colun"), identity.candidateIds)
    }
}

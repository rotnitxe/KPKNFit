package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.food.findFoodByNormalized
import com.example.kpkn.data.food.findStaticFoodById
import com.example.kpkn.data.models.CookingMethod
import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.data.models.LoggedFood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodInterpretationV2Test {
    private val engine = FoodInterpretationV2Engine(::findFoodByNormalized)

    @Test
    fun `explicit cooked chicken uses cooked row without double conversion`() {
        val result = engine.interpret("200 g pechuga de pollo cocida")

        assertEquals("gen004", result.selectedCandidateId)
        assertEquals(WeightBasis.COOKED, result.weightBasis)
        assertEquals(64.2, result.proteinGrams, 0.01)
        assertEquals(64.2, result.proteinMinGrams, 0.01)
        assertTrue(result.transformations.any { it.startsWith("preparation") })
        assertTrue(result.pendingQuestions.isEmpty())
    }

    @Test
    fun `context does not mutate authoritative density`() {
        val plain = engine.interpret("200 g pechuga de pollo cocida")
        val postWorkout = engine.interpret(
            "200 g pechuga de pollo cocida",
            InterpretationContext(freeContext = "post-entreno"),
        )

        assertEquals(plain.proteinGrams, postWorkout.proteinGrams, 0.0)
        assertEquals(plain.calories, postWorkout.calories, 0.0)
    }

    @Test
    fun `vague portion exposes three absolute options and unsure keeps range`() {
        val draft = engine.interpret("porción de arroz cocido")
        val request = draft.pendingQuestions.filterIsInstance<ClarificationRequest.Portion>().single()

        assertEquals(listOf("Pequeña", "Habitual", "Grande"), request.options.map { it.label })
        assertTrue(request.options.zipWithNext().all { it.first.grams < it.second.grams })

        val unsure = engine.answerClarification(
            draft.draftId,
            request.requestId,
            ClarificationAnswer.Unsure(request.requestId),
        )
        assertNotNull(unsure)
        assertTrue(unsure!!.isUncertain)
        assertTrue(unsure.caloriesMax > unsure.caloriesMin)
        assertFalse(unsure.isConfirmedEstimate)
        assertNotNull(engine.finalize(draft.draftId))
    }

    @Test
    fun `explicit grams answer is idempotent and removes portion question`() {
        val draft = engine.interpret("pollo cocido")
        val request = draft.pendingQuestions.filterIsInstance<ClarificationRequest.Portion>().singleOrNull()
        assertNotNull(request)
        val answer = engine.answerClarification(
            draft.draftId,
            request!!.requestId,
            ClarificationAnswer.Grams(request.requestId, 180.0),
        )!!
        val repeated = engine.answerClarification(
            draft.draftId,
            request.requestId,
            ClarificationAnswer.Grams(request.requestId, 180.0),
        )!!
        assertEquals(180.0, answer.observedGrams!!, 0.0)
        assertEquals(answer.proteinGrams, repeated.proteinGrams, 0.0)
        assertTrue(repeated.pendingQuestions.none { it.requestId == request.requestId })
    }

    @Test
    fun `sensitive foods without state ask for weight basis`() {
        val result = engine.interpret("200 g arroz")
        assertTrue(result.pendingQuestions.any { it is ClarificationRequest.WeightState })
        assertEquals(WeightBasis.UNKNOWN, result.weightBasis)
    }

    // ─── WP-N10b: the name of a converted row keeps its dish ────────────────────────────────────────

    @Test
    fun `conversion stem strips state words only as qualifiers and never the first word`() {
        val cases = listOf(
            "Asado de Tira (crudo)" to "Asado de Tira",
            "Papas Fritas" to "Papas",
            "Huevo Entero (frito)" to "Huevo Entero",
            "Pechuga de Pollo (cruda)" to "Pechuga de Pollo",
            "Arroz (hidratado/cocido)" to "Arroz",
            "Pasta (hidratada/cocida)" to "Pasta",
            "Soya texturizada (seca)" to "Soya texturizada",
            "Posta Rosada (cocida)" to "Posta Rosada",
            "Pavo (pechuga cocida)" to "Pavo (pechuga)",
            "Jamón Cocido" to "Jamón",
            "Pollo al asado" to "Pollo",
            "Papas fritas (snack)" to "Papas (snack)",
            "Pechuga de pollo (cruda, sin piel)" to "Pechuga de pollo (sin piel)",
            "Lomo liso de vacuno magro crudo (FDC 334849)" to "Lomo liso de vacuno magro (FDC 334849)",
            // The first word is the dish: it stays even when it is a state word.
            "Asado alemán" to "Asado alemán",
            "Frito Lay Clásicas" to "Frito Lay Clásicas",
            "Asado" to "Asado",
            "Fritos" to "Fritos",
            // A word inside the name, or a stem inside a word, is not a qualifier.
            "Pan Amasado" to "Pan Amasado",
            "Pollo asado con papas" to "Pollo asado con papas",
        )
        for ((name, stem) in cases) assertEquals(name, stem, conversionStem(name))
    }

    private fun converted(food: FoodItem, to: FoodState, method: CookingMethod) = ResolvedTag(
        tag = food.name, foodItem = food, cookingMethod = method, foodState = to, amountGrams = 100.0,
        stateConversion = "weight_basis:test",
        loggedFood = LoggedFood(foodName = food.name, amount = 100.0, calories = 300.0, protein = 20.0, fats = 20.0),
    )

    @Test
    fun `the interpretation of a converted row is named after its dish`() {
        val cut = requireNotNull(findStaticFoodById("gen093c"))
        assertEquals("Asado de Tira (crudo)", cut.name)
        // The raw cut asked as a roast: "de Tira (cocido, estimado)" lost the first word of the name.
        val roast = engine.interpretResolved(converted(cut, FoodState.COOKED, CookingMethod.ASADO_PARRILLA))
        assertEquals("Asado de Tira (cocido, estimado)", roast.canonicalIdentity)
        assertTrue(roast.transformations.any { it.startsWith("weight_basis:RAW->COOKED;yield=0.7;source=gen093c") })
        // The other direction, and the names of the probe: a cooked row asked raw.
        for ((name, stem) in listOf("Papas Fritas" to "Papas", "Huevo Entero (frito)" to "Huevo Entero", "Arroz Blanco (cocido)" to "Arroz Blanco")) {
            val raw = engine.interpretResolved(converted(FoodItem(name = name), FoodState.RAW, CookingMethod.CRUDO))
            assertEquals(name, "$stem (crudo, estimado)", raw.canonicalIdentity)
        }
        val chicken = engine.interpretResolved(converted(FoodItem(name = "Pechuga de Pollo (cruda)"), FoodState.COOKED, CookingMethod.COCIDO))
        assertEquals("Pechuga de Pollo (cocido, estimado)", chicken.canonicalIdentity)
    }
}

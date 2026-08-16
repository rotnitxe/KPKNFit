package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.food.findFoodByNormalized
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
}

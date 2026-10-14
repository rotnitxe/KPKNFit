package com.example.kpkn.screens.nutrition.design.logger

import com.example.kpkn.domain.nutrition.ClarificationRequest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Candidatos de la aclaración del registro (N6): la tortilla no repite su respuesta corta; el resto sigue igual. */
class NutritionClarificationChoicesTest {

    @Test
    fun `la tortilla no repite el candidato que ya es su respuesta corta`() {
        val question = ClarificationRequest.Identity(
            requestId = "tortilla_composition",
            candidateIds = listOf("tortilla_maiz", "tortilla_papa", "gen090"),
            candidateLabels = listOf("Tortilla de maíz", "Tortilla de papas", "Trigo (wrap)"),
        )

        assertEquals(listOf("gen090" to "Trigo (wrap)"), clarificationCandidateChoices(question))
    }

    @Test
    fun `otras preguntas de identidad muestran todos sus candidatos`() {
        val question = ClarificationRequest.Identity(
            requestId = "identity",
            candidateIds = listOf("tortilla_maiz", "tortilla_papa"),
            candidateLabels = listOf("Tortilla de maíz", "Tortilla de papas"),
        )

        assertEquals(
            listOf("tortilla_maiz" to "Tortilla de maíz", "tortilla_papa" to "Tortilla de papas"),
            clarificationCandidateChoices(question),
        )
    }
}

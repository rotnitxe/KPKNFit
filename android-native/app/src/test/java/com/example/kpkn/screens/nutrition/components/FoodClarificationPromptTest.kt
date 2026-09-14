package com.example.kpkn.screens.nutrition.components

import com.example.kpkn.domain.nutrition.ClarificationRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FoodClarificationPromptTest {

    @Test
    fun `generic identity asks if the match is right and names it`() {
        val prompt = foodClarificationPrompt(
            question = ClarificationRequest.Identity(requestId = "identity"),
            matchedName = "Filete de Vacuno",
            unresolvedDeclaredAmount = false,
            dryVsCooked = false,
        )
        assertEquals("¿Es este el alimento?", prompt.title)
        assertEquals(
            "Coincidencia aproximada: Filete de Vacuno. Cámbiala si no es lo que comiste.",
            prompt.detail,
        )
        assertEquals("Cambiar alimento", prompt.pickOtherLabel)
        assertEquals("Seguir con esta estimación", prompt.unsureLabel)
        assertNull(prompt.reviewAmountLabel)
    }

    @Test
    fun `salad composition keeps a specific question without match hint`() {
        val prompt = foodClarificationPrompt(
            question = ClarificationRequest.Identity(requestId = "composition"),
            matchedName = "Ensalada",
            unresolvedDeclaredAmount = false,
            dryVsCooked = false,
        )
        assertEquals("¿Qué llevaba la ensalada?", prompt.title)
        assertNull(prompt.detail)
        assertEquals("Elegir otro tipo de ensalada", prompt.pickOtherLabel)
    }

    @Test
    fun `declared amount failure asks to review grams`() {
        val prompt = foodClarificationPrompt(
            question = ClarificationRequest.Identity(requestId = "identity"),
            matchedName = "Arroz",
            unresolvedDeclaredAmount = true,
            dryVsCooked = false,
        )
        assertEquals("Revisar cantidad", prompt.reviewAmountLabel)
        assertEquals(
            "No pudimos recuperar la medida que escribiste. Revisa la cantidad antes de guardar.",
            prompt.detail,
        )
    }
}

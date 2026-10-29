package com.example.kpkn.screens.nutrition.design.logger

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.domain.nutrition.ClarificationRequest
import com.example.kpkn.domain.nutrition.PortionOption
import com.example.kpkn.screens.nutrition.NutritionSamples
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La tarjeta de un alimento (Registros v4, f-result y f-clar): tocarla abre su ajuste, cada respuesta de su duda llama con su clave y
 * «Seguir con …» acepta la estimación. La tarjeta no cambia el borrador por su cuenta. También los textos puros de la tarjeta y de la
 * duda.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
class LoggerFoodItemTest {

    @get:Rule
    val rule = createComposeRule()

    private val tortillaQuestion = LoggerQuestion(
        text = "¿De qué era la tortilla?",
        choices = TORTILLA_COMPOSITIONS.map { (composition, label) -> LoggerQuestionChoice(LoggerChoiceKeys.COMPOSITION + composition, label) },
        skipLabel = "Seguir con la estimación",
    )

    @Test
    fun tocarLaTarjetaAbreSuAjuste() {
        var opened = 0
        rule.setContent {
            LoggerFoodCard(
                name = "Huevos revueltos",
                subtitle = "2 huevos · 110 g",
                kcal = "182",
                protein = 13.0,
                carbs = 1.0,
                fats = 14.0,
                icon = LoggerIcons.Egg,
                onOpen = { opened++ },
            )
        }
        rule.onNodeWithText("Huevos revueltos").assertExists()
        rule.onNodeWithText("2 huevos · 110 g").assertExists()
        rule.onNodeWithContentDescription("Huevos revueltos, 2 huevos · 110 g, 182 kcal. Ajustar").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun unaRespuestaDeLaDudaLlamaConSuClave() {
        val keys = mutableListOf<String>()
        var skipped = 0
        rule.setContent {
            LoggerFoodCard(
                name = "Tortilla",
                subtitle = "1 unidad · 120 g · aprox.",
                kcal = "≈ 240",
                protein = 8.0,
                carbs = 20.0,
                fats = 12.0,
                icon = LoggerIcons.Pan,
                onOpen = {},
                question = tortillaQuestion,
                onChoice = { keys += it },
                onSkip = { skipped++ },
            )
        }
        rule.onNodeWithText("¿De qué era la tortilla?").assertExists()
        rule.onNodeWithText("De maíz").performClick()
        rule.onNodeWithText("Seguir con la estimación").performClick()
        assertEquals(listOf(LoggerChoiceKeys.COMPOSITION + "maíz"), keys)
        assertEquals(1, skipped)
    }

    @Test
    fun laLineaDeLaTarjetaMarcaLoAproximado() {
        assertEquals("2 huevos · 110 g · aprox.", loggerFoodSubtitle("2 huevos · 110 g", approximate = true))
        assertEquals("150 g", loggerFoodSubtitle("150 g", approximate = false))
        assertEquals("≈ 96", loggerFoodKcal(96, approximate = true))
        assertEquals("182", loggerFoodKcal(182, approximate = false))
    }

    @Test
    fun laTortillaPreguntaSusCuatroComposiciones() {
        val question = loggerQuestionFor(NutritionSamples.tortillaTag, NutritionSamples.tortillaQuestion)
        assertEquals("¿De qué era la tortilla?", question.text)
        assertEquals(listOf("De papas", "De huevo", "Trigo (wrap)", "De maíz"), question.choices.map { it.label })
        assertTrue(question.choices.all { it.key.startsWith(LoggerChoiceKeys.COMPOSITION) })
    }

    @Test
    fun elAceiteSePreguntaConGotas() {
        val question = loggerQuestionFor(
            NutritionSamples.chickenTag,
            ClarificationRequest.Oil(requestId = "oil", options = listOf("sin aceite", "poco", "medio", "abundante")),
        )
        assertEquals("¿Cuánto aceite tenía?", question.text)
        assertEquals(listOf("Nada", "Poco", "Medio", "Mucho"), question.choices.map { it.label })
        assertEquals(listOf(0, 1, 2, 3), question.choices.map { it.drops })
        assertTrue(question.drops)
    }

    @Test
    fun laCantidadNoLeidaAbreElAjusteYNoSeSalta() {
        val question = loggerQuestionFor(NutritionSamples.chickenTag, ClarificationRequest.Portion("declared_amount", emptyList()))
        assertTrue(question.opensAdjust)
        assertNull(question.skipLabel)
        assertTrue(question.choices.isEmpty())
    }

    @Test
    fun elEnvaseOfreceTodoOUnaPorcion() {
        val question = loggerQuestionFor(
            NutritionSamples.chickenTag,
            ClarificationRequest.Portion(
                "package_portion",
                listOf(PortionOption("Comí todo el envase (180 g)", 180.0), PortionOption("Una porción habitual", 30.0)),
            ),
        )
        assertEquals("¿Comiste todo el envase?", question.text)
        assertEquals(listOf("Todo (180 g)", "Una porción (30 g)"), question.choices.map { it.label })
        assertFalse(question.opensAdjust)
    }

    @Test
    fun elNombreDelAceiteEsElDelLienzo() {
        assertEquals("Nada", loggerOilLabel("sin aceite"))
        assertEquals("Mucho", loggerOilLabel("abundante"))
    }
}

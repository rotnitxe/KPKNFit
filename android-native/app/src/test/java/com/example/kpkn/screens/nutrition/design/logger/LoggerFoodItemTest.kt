package com.example.kpkn.screens.nutrition.design.logger

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.data.models.PortionPreset
import com.example.kpkn.domain.nutrition.ClarificationRequest
import com.example.kpkn.domain.nutrition.ResolvedTag
import com.example.kpkn.domain.nutrition.VariantChoice
import com.example.kpkn.screens.nutrition.NutritionSamples
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Fila de alimento (N3b): cada respuesta llama a su lambda y la fila no cambia el borrador por su cuenta.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
class LoggerFoodItemTest {

    @get:Rule
    val rule = createComposeRule()

    /** Lo que la fila ha llamado, para comprobarlo después de cada toque. */
    private class Calls {
        var portion: PortionPreset? = null
        var declared: String? = null
        var corrections = 0
        var removed = 0
        val identities = mutableListOf<String>()
    }

    private fun actions(calls: Calls) = LoggerFoodActions(
        onToggleExpanded = {},
        onToggleCorrection = { calls.corrections++ },
        onCorrectionQueryChange = {},
        onPickCorrection = {},
        onPortionChange = { calls.portion = it },
        onPortionOption = {},
        onGramsChange = {},
        onMacroChange = { _, _ -> },
        onRemove = { calls.removed++ },
        onOilLevelChange = {},
        onCookingChange = {},
        onIdentityChoice = { calls.identities += it },
        onDeclaredComposition = { calls.declared = it },
        onUnsure = {},
    )

    @Test
    fun elegirUnaPorcionLlamaASuLambda() {
        val calls = Calls()
        rule.setContent {
            LoggerFoodItem(
                tag = NutritionSamples.chickenTag,
                correcting = false,
                correctionQuery = "",
                correctionResults = null,
                pendingQuestion = null,
                matchUncertain = false,
                actions = actions(calls),
            )
        }
        rule.onNodeWithText("Grande").performClick()
        assertEquals(PortionPreset.LARGE, calls.portion)
    }

    @Test
    fun unaRespuestaDeAclaracionLlamaASuLambda() {
        val calls = Calls()
        rule.setContent {
            LoggerFoodItem(
                tag = NutritionSamples.tortillaTag,
                correcting = false,
                correctionQuery = "",
                correctionResults = null,
                pendingQuestion = NutritionSamples.tortillaQuestion,
                matchUncertain = true,
                actions = actions(calls),
            )
        }
        rule.onNodeWithText("De huevo").performClick()
        assertEquals("huevo", calls.declared)
    }

    @Test
    fun corregirLlamaALaBusqueda() {
        val calls = Calls()
        rule.setContent {
            LoggerFoodItem(
                tag = NutritionSamples.chickenTag,
                correcting = false,
                correctionQuery = "",
                correctionResults = null,
                pendingQuestion = null,
                matchUncertain = false,
                actions = actions(calls),
            )
        }
        rule.onNodeWithText("Corregir").performClick()
        assertEquals(1, calls.corrections)
    }

    @Test
    fun quitarQuitaElAlimento() {
        val calls = Calls()
        rule.setContent {
            LoggerFoodItem(
                tag = NutritionSamples.chickenTag,
                correcting = false,
                correctionQuery = "",
                correctionResults = null,
                pendingQuestion = null,
                matchUncertain = false,
                actions = actions(calls),
            )
        }
        rule.onNodeWithContentDescription("Quitar Pollo a la plancha").performClick()
        assertEquals(1, calls.removed)
    }

    /** Arroz con chips de estado «Cocido» (el actual) y «Crudo», y de forma «En polvo» y «Preparado» (el actual). */
    private fun tagWithVariants(): ResolvedTag {
        val cocido = FoodItem(id = "arroz_cocido", name = "Arroz")
        val crudo = FoodItem(id = "arroz_crudo", name = "Arroz")
        val polvo = FoodItem(id = "arroz_polvo", name = "Arroz")
        val preparado = FoodItem(id = "arroz_prep", name = "Arroz (preparado)")
        return NutritionSamples.chickenTag.copy(
            foodItem = cocido,
            variantChoices = listOf(
                VariantChoice("state", "Cocido", cocido),
                VariantChoice("state", "Crudo", crudo),
                VariantChoice("form", "Preparado", preparado),
                VariantChoice("form", "En polvo", polvo),
            ),
        )
    }

    @Test
    fun tocarUnChipNoSeleccionadoLlamaAIdentityChoiceYElSeleccionadoNo() {
        val calls = Calls()
        rule.setContent {
            LoggerFoodItem(
                tag = tagWithVariants(),
                correcting = false,
                correctionQuery = "",
                correctionResults = null,
                pendingQuestion = null,
                matchUncertain = false,
                actions = actions(calls),
            )
        }
        rule.onNodeWithText("Cocido").performClick()
        assertEquals(emptyList<String>(), calls.identities)
        rule.onNodeWithText("Crudo").performClick()
        assertEquals(listOf("arroz_crudo"), calls.identities)
    }

    @Test
    fun sinFilaDeChipsCuandoElAlimentoActualNoEstaEntreLasElecciones() {
        val calls = Calls()
        rule.setContent {
            LoggerFoodItem(
                tag = tagWithVariants().copy(foodItem = FoodItem(id = "otro_alimento", name = "Otro")),
                correcting = false,
                correctionQuery = "",
                correctionResults = null,
                pendingQuestion = null,
                matchUncertain = false,
                actions = actions(calls),
            )
        }
        rule.onNodeWithText("Cocido").assertDoesNotExist()
        rule.onNodeWithText("Crudo").assertDoesNotExist()
        rule.onNodeWithText("En polvo").assertDoesNotExist()
    }

    @Test
    fun conPreguntaDeEstadoPendienteNoAparecenLosChipsDeEstado() {
        val calls = Calls()
        rule.setContent {
            LoggerFoodItem(
                tag = tagWithVariants(),
                correcting = false,
                correctionQuery = "",
                correctionResults = null,
                pendingQuestion = ClarificationRequest.Identity(
                    requestId = "state",
                    candidateIds = listOf("arroz_cocido", "arroz_crudo"),
                    candidateLabels = listOf("Opción uno", "Opción dos"),
                ),
                matchUncertain = false,
                actions = actions(calls),
            )
        }
        rule.onNodeWithText("Cocido").assertDoesNotExist()
        rule.onNodeWithText("Crudo").assertDoesNotExist()
        rule.onNodeWithText("En polvo").assertExists()
        rule.onNodeWithText("Preparado").assertExists()
    }
}

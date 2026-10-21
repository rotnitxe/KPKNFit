package com.example.kpkn.screens.nutrition.design.logger

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.data.models.PortionPreset
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
        onIdentityChoice = {},
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
}

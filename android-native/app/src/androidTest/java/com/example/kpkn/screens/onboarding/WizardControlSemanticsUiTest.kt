package com.example.kpkn.screens.onboarding

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import com.example.kpkn.screens.onboarding.design.WizardHeightUnit
import com.example.kpkn.screens.onboarding.design.WizardHeightWheel
import com.example.kpkn.screens.onboarding.design.WizardMassUnit
import com.example.kpkn.screens.onboarding.design.WizardWeightRule
import com.example.kpkn.screens.onboarding.design.WizardWeightScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Semántica de interacción de las reglas/ruedas de medida productiva,
 * preservada desde el retirado `WizChatComponentsUiTest` sobre los controles
 * vivos del prototipo visual:
 *
 * - Arrastrar AJUSTA el candidato pero NUNCA confirma (la confirmación es un
 *   toque explícito en el valor central, cubierto por `WizardGateComponentsUiTest`).
 *
 * Selectores canónicos: mismos contentDescription/escalas que producción
 * (`WizardWeightScale` / `WizardHeightScale`), así el valor es canónico con
 * independencia del idioma del dispositivo.
 */
class WizardControlSemanticsUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun weightRuleDragAdjustsTheCandidateWithoutConfirming() {
        val candidates = mutableListOf<Double>()
        var confirms = 0
        composeRule.setContent {
            WizardWeightRule(
                unit = WizardMassUnit.KG,
                valueKg = 70.0,
                onValueChange = { candidates += it },
                onConfirm = { confirms++ },
            )
        }
        composeRule.waitForIdle()

        val centerText = WizardWeightScale.formatWithUnit(70.0, WizardMassUnit.KG)
        composeRule.onNodeWithText(centerText).assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Regla de peso en kilogramos")
            .performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        // El candidato se movió de décima en décima…
        composeRule.onNodeWithText(centerText).assertDoesNotExist()
        assertEquals(1, candidates.size)
        assertTrue(candidates.single() != 70.0)
        // …y arrastrar nunca confirma.
        assertEquals(0, confirms)

        // Confirmar después el mismo candidato no duplica onValueChange.
        composeRule.onNodeWithTag("setup-weight-value").performClick()
        composeRule.waitForIdle()
        assertEquals(1, candidates.size)
        assertEquals(1, confirms)
    }

    @Test
    fun heightWheelDragChangesTheCandidateWithoutConfirming() {
        val candidates = mutableListOf<Int>()
        var confirms = 0
        composeRule.setContent {
            WizardHeightWheel(
                unit = WizardHeightUnit.CM,
                cm = 175,
                onValueChange = { candidates += it },
                onConfirm = { confirms++ },
            )
        }
        composeRule.waitForIdle()

        val wheel = "Rueda de altura en ${WizardHeightUnit.CM.label.lowercase()}"
        fun candidate(): String = composeRule.onNodeWithContentDescription(wheel)
            .fetchSemanticsNode().config[SemanticsProperties.StateDescription]

        val before = candidate()
        // TouchInjectionScope recibe coordenadas en píxeles, no fracciones: un
        // 0.5f → 0.1f solo recorría 0.4 px y no superaba el touch slop. El
        // arrastre usa ahora el centro y el 40 % de la altura real del nodo,
        // dentro de la rueda y con recorrido suficiente para desplazarla.
        composeRule.onNodeWithContentDescription(wheel)
            .performTouchInput {
                val halfTravel = height * 0.2f
                swipeUp(
                    startY = centerY + halfTravel,
                    endY = centerY - halfTravel,
                )
            }
        composeRule.waitForIdle()
        val after = candidate()

        // El candidato visible cambió (stateDescription lleva el valor central)…
        assertNotEquals(before, after)
        assertEquals(1, candidates.size)
        assertNotEquals(175, candidates.single())
        assertEquals("${candidates.single()}. Desliza para ajustar", after)
        // …y arrastrar nunca confirma.
        assertEquals(0, confirms)

        // Confirmar después el mismo candidato no duplica onValueChange.
        composeRule.onNodeWithTag("setup-height-value").performClick()
        composeRule.waitForIdle()
        assertEquals(1, candidates.size)
        assertEquals(1, confirms)

        // Accesibilidad intacta: las acciones de incremento siguen en el nodo.
        val actions = composeRule.onNodeWithContentDescription(wheel)
            .fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertTrue(actions.any { it.label.contains("Aumentar") })

        // Sin filas «−1/+1» visuales: no están en la referencia y no se
        // aprobaron como elementos nuevos.
        composeRule.onNodeWithText("+1").assertDoesNotExist()
        composeRule.onNodeWithText("−1").assertDoesNotExist()
    }
}

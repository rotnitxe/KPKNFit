package com.example.kpkn.screens.programdetail.design.structure

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Las herramientas de un programa por bloques a 320 dp (P3a, sustituye a la prueba de la barra `MacrocycleToolbar`): «Historial» se ve,
 * está habilitado, queda dentro del módulo, es más ancho que alto y el clic abre. Solo se compila en este entorno (no se ejecuta).
 */
class ProgramToolsModuleUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun narrowWidth_historyButton_staysVisibleEnabledAndInsideBounds() {
        var opened = false
        var toolsBounds: Rect? = null
        var historyBounds: Rect? = null
        composeRule.setContent {
            MaterialTheme {
                BoxWithConstraints(modifier = Modifier.width(320.dp)) {
                    Column(
                        modifier = Modifier.onGloballyPositioned { toolsBounds = it.boundsInRoot() },
                    ) {
                        ProgramToolsModule(
                            kind = ProgramToolsKind.BLOCKS,
                            blockCount = 4,
                            weekCount = 11,
                            sessionCount = 44,
                            keyDateCount = 0,
                            historyCount = 1,
                            onCalendar = {},
                            onKeyDates = {},
                            onRoute = {},
                            onCycles = {},
                            onTemplates = {},
                            onHistory = { opened = true },
                            onCalendarize = {},
                        )
                    }
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("macrocycle_history_button").assertIsDisplayed()
        composeRule.onNodeWithTag("macrocycle_history_button").assertIsEnabled()
        composeRule.onNodeWithContentDescription("Historial, 1 copia").assertIsDisplayed()
        composeRule.onNodeWithTag(
            "macrocycle_history_button",
            useUnmergedTree = true,
        ).fetchSemanticsNode().let { node ->
            historyBounds = node.boundsInRoot
        }
        val toolbar = toolsBounds
        val history = historyBounds
        assertTrue("módulo sin bounds", toolbar != null && !toolbar.isEmpty)
        assertTrue("historial sin bounds", history != null && !history.isEmpty)
        if (toolbar != null && history != null) {
            assertTrue(
                "Historial fuera del módulo: $history vs $toolbar",
                toolbar.contains(history.topLeft) && toolbar.contains(history.bottomRight),
            )
            // Una baldosa con letra partida carácter por carácter mediría alto >> ancho; una baldosa legible es más ancha que alta.
            assertTrue(
                "Historial con geometría ilegible (posible wrap por carácter): $history",
                history.width > history.height,
            )
        }
        composeRule.onNodeWithTag("macrocycle_history_button").performClick()
        composeRule.waitForIdle()
        assertTrue(opened)
    }
}

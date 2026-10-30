package com.example.kpkn.screens.sessioneditor.components.assistant

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import androidx.compose.runtime.CompositionLocalProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Objetivos táctiles del asistente (pasada B/C): cada control pulsable mide al menos 48 × 48 dp en su semántica, y el dibujo ocupa lo mismo que en el
 * lienzo (44, 40, 36… en la maqueta). Las alturas de las columnas comprueban que `asTouch48` no mueve nada.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class AssistantTouchTargetTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun clickableControlsAreAtLeast48dpAndKeepTheirLayoutSize() {
        var taps by mutableIntStateOf(0)
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                Column(Modifier.testTag("col").fillMaxWidth()) {
                    AsIconButton(AsIcons.More, "Más acciones", onClick = { taps += 1 })      // 44 × 44 en el lienzo
                    AsTextAction("Cancelar", onClick = {})                                   // 44 de alto
                    AsButton("Aplicar", Modifier.fillMaxWidth(), small = true, onClick = {}) // 44 de alto
                    AsChip("Piernas", on = false, onClick = {})                              // 36 de alto
                    AsStepper("3", "series", onMinus = {}, onPlus = {})                       // celda de 44 con botones de 44 × 40
                }
            }
        }
        rule.onNodeWithContentDescription("Más acciones").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        rule.onNodeWithContentDescription("Menos series").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        rule.onNodeWithContentDescription("Más series").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        // 44 + 44 + 44 + 36 + 44 (stepper: 2 de relleno + 40 + 2): la columna ocupa lo del lienzo, no más
        rule.onNodeWithTag("col").assertHeightIsEqualTo(212.dp)
        // el centro del botón sigue respondiendo
        rule.onNodeWithContentDescription("Más acciones").performClick()
        assertEquals(1, taps)
    }
}

package com.example.kpkn.ui.design

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Módulo de la app (KPKN_UI_V2 §1 y §5): tocable solo con [KpknModule.onClick] y la cifra con unidad en la semántica. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class KpknModuleTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun moduloConOnClickExponeBotonYLlamaALaLambda() {
        var taps = 0
        rule.setContent {
            KpknModule(
                modifier = Modifier.testTag("modulo"),
                onClick = { taps++ },
                onClickLabel = "Abrir sesión",
            ) {
                Text("Volumen de entrenamiento")
            }
        }
        rule.onNodeWithTag("modulo")
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        assertEquals(1, taps)
    }

    @Test
    fun moduloSinOnClickNoEsTocable() {
        rule.setContent {
            KpknModule(modifier = Modifier.testTag("modulo")) {
                Text("Solo lectura")
            }
        }
        rule.onNodeWithTag("modulo").assertHasNoClickAction()
    }

    @Test
    fun kpknMetricExponeLaCifraConLaUnidadEnLaSemantica() {
        rule.setContent {
            KpknMetric(value = "92", unit = "%")
        }
        rule.onNodeWithContentDescription("92 %").assertExists()
    }
}

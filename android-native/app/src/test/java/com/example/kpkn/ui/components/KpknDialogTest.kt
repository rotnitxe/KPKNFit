package com.example.kpkn.ui.components

import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.screens.home.NutritionTodayGlassOverlay
import com.example.kpkn.screens.onboarding.design.WizardColors
import dev.chrisbanes.haze.HazeState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Diálogos KPKN (D2): la confirmación destructiva en peligro, la cancelación y el contador de modales, también con la capa
 * «Comidas registradas» de Home abierta (la isla superior se oculta mientras hay una).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xxhdpi", application = android.app.Application::class)
class KpknDialogTest {

    @get:Rule
    val rule = createComposeRule()

    /** Si la capa de Home está en composición: la cambia la prueba fuera de la composición. */
    private var overlayOpen by mutableStateOf(false)

    @Composable
    private fun OverlayHost() {
        val haze = remember { HazeState() }
        if (overlayOpen) {
            NutritionTodayGlassOverlay(hazeState = haze, meals = emptyList(), onDismiss = {}, onAddMeal = {})
        }
    }

    @Test
    fun destructivoPintaEliminarYLlamaAConfirmar() {
        var confirmados = 0
        var cancelados = 0
        rule.setContent {
            KpknAlertDialog(
                onDismissRequest = {},
                title = "¿Eliminar esta medición?",
                text = "Se borran el peso y las medidas de este día y hora.",
                confirmLabel = "Eliminar",
                onConfirm = { confirmados++ },
                dismissLabel = "Cancelar",
                onDismiss = { cancelados++ },
                destructive = true,
            )
        }
        rule.onNodeWithText("Eliminar").assertIsDisplayed()
        rule.onNodeWithText("Eliminar").performClick()
        assertEquals(1, confirmados)
        assertEquals(0, cancelados)
    }

    @Test
    fun cancelarLlamaAOnDismiss() {
        var confirmados = 0
        var cancelados = 0
        rule.setContent {
            KpknAlertDialog(
                onDismissRequest = {},
                title = "¿Guardar los cambios?",
                text = "Tienes cambios sin guardar.",
                confirmLabel = "Guardar",
                onConfirm = { confirmados++ },
                dismissLabel = "Cancelar",
                onDismiss = { cancelados++ },
            )
        }
        rule.onNodeWithText("Cancelar").performClick()
        assertEquals(1, cancelados)
        assertEquals(0, confirmados)
    }

    @Test
    fun sinDismissLabelNoHayAccionDeCancelar() {
        rule.setContent {
            KpknAlertDialog(
                onDismissRequest = {},
                title = "Aviso",
                text = "Solo hay una acción.",
                confirmLabel = "Entendido",
                onConfirm = {},
            )
        }
        rule.onNodeWithText("Entendido").assertIsDisplayed()
        rule.onNodeWithText("Cancelar").assertDoesNotExist()
    }

    @Test
    fun capaDeComidasRegistradasCuentaComoModalYAlCerrarVuelveAtras() {
        val antes = KpknModalTracker.openCount
        rule.setContent { OverlayHost() }
        rule.runOnIdle { overlayOpen = true }
        rule.waitForIdle()
        assertEquals(antes + 1, KpknModalTracker.openCount)
        rule.runOnIdle { overlayOpen = false }
        rule.waitForIdle()
        assertEquals(antes, KpknModalTracker.openCount)
    }

    @Test
    fun dentroDelDialogoLosBotonesDeMaterialSalenEnTinta() {
        var primario: Color? = null
        var sobrePrimario: Color? = null
        rule.setContent {
            KpknAlertDialog(
                onDismissRequest = {},
                confirmButton = {
                    primario = MaterialTheme.colorScheme.primary
                    sobrePrimario = MaterialTheme.colorScheme.onPrimary
                    Button(onClick = {}) { Text("Activar") }
                },
                title = { Text("Permiso de notificaciones") },
                text = { Text("Activa las notificaciones para recibir el aviso.") },
            )
        }
        rule.waitForIdle()
        assertEquals(WizardColors.text, primario)
        assertEquals(WizardColors.background, sobrePrimario)
    }

    @Test
    fun dentroDeLaHojaDeVidrioElColorPrimarioEsTinta() {
        var primario: Color? = null
        rule.setContent {
            KpknGlassDialog(onDismissRequest = {}) {
                primario = MaterialTheme.colorScheme.primary
                Text("Contenido de prueba")
            }
        }
        rule.waitForIdle()
        assertEquals(WizardColors.text, primario)
    }
}

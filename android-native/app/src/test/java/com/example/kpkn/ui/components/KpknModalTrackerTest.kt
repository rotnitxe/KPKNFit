package com.example.kpkn.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.kpkn.ui.design.KpknTopIsland
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Contador de hojas y diálogos abiertos (I12): sube al entrar y baja al salir, y la isla superior no se compone mientras haya una hoja.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xxhdpi", application = android.app.Application::class)
class KpknModalTrackerTest {

    @get:Rule
    val rule = createComposeRule()

    /** Si la hoja de la prueba está en composición: la cambia la prueba fuera de la composición. */
    private var sheetOpen by mutableStateOf(false)

    /** Si la isla de la prueba está en composición. */
    private var islandShown by mutableStateOf(false)

    @Composable
    private fun SheetHost() {
        if (sheetOpen) {
            KpknSheet(onDismissRequest = {}) {
                Text("Hoja de prueba")
            }
        }
    }

    @Test
    fun abrirYCerrarUnaHojaSubeYBajaElContador() {
        val before = KpknModalTracker.openCount
        rule.setContent { SheetHost() }
        rule.waitForIdle()
        assertEquals(before, KpknModalTracker.openCount)
        rule.runOnIdle { sheetOpen = true }
        rule.waitForIdle()
        assertEquals(before + 1, KpknModalTracker.openCount)
        rule.runOnIdle { sheetOpen = false }
        rule.waitForIdle()
        assertEquals(before, KpknModalTracker.openCount)
    }

    @Test
    fun laIslaNoSeComponeMientrasHayUnaHojaAbierta() {
        val before = KpknModalTracker.openCount
        rule.setContent {
            SheetHost()
            if (islandShown) KpknTopIsland(visible = true, title = "Isla de prueba")
        }
        rule.runOnIdle { sheetOpen = true }
        rule.waitForIdle()
        rule.runOnIdle { islandShown = true }
        rule.waitForIdle()
        rule.onNodeWithText("Isla de prueba").assertDoesNotExist()
        // Al cerrar la hoja, la isla vuelve a aparecer.
        rule.runOnIdle { sheetOpen = false }
        rule.waitForIdle()
        rule.onNodeWithText("Isla de prueba").assertExists()
        assertEquals(before, KpknModalTracker.openCount)
    }
}

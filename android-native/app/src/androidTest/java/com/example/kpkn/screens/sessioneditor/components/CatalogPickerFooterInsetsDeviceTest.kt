package com.example.kpkn.screens.sessioneditor.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.view.WindowCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kpkn.screens.sessioneditor.design.CatalogPickerViewport
import com.example.kpkn.screens.sessioneditor.design.CatalogReplaceBar
import com.example.kpkn.screens.sessioneditor.design.CatalogReplaceButtonTag
import com.example.kpkn.screens.sessioneditor.design.CatalogSelectionIsland
import com.example.kpkn.screens.sessioneditor.design.CatalogSelectionSlot
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Insets reales del teléfono: pie del picker sobre navbar y un solo consumo del inset. */
@RunWith(AndroidJUnit4::class)
class CatalogPickerFooterInsetsDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private var navigationBottom = 0
    private var density = 0f
    private var confirmations = 0

    private fun replacement(parentConsumes: Boolean) {
        rule.runOnUiThread { WindowCompat.setDecorFitsSystemWindows(rule.activity.window, false) }
        rule.setContent {
            val currentDensity = LocalDensity.current
            val inset = WindowInsets.navigationBars.getBottom(currentDensity)
            SideEffect { density = currentDensity.density; navigationBottom = inset }
            KPKNTheme {
                Box(Modifier.fillMaxSize().testTag("picker-root")) {
                    Column(Modifier.fillMaxSize().then(if (parentConsumes) Modifier.navigationBarsPadding() else Modifier)) {
                        CatalogPickerViewport {
                            CatalogReplaceBar(
                                note = "Se mantienen las series del plan: 4 × 4–6 · 80 % 1RM · RIR 2",
                                buttonText = "Reemplazar por Press Arnold", enabled = true,
                                onConfirm = { confirmations += 1 }, modifier = Modifier.align(Alignment.BottomCenter),
                            )
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        val root = rule.onNodeWithTag("picker-root").fetchSemanticsNode().boundsInRoot
        val button = rule.onNodeWithTag(CatalogReplaceButtonTag).fetchSemanticsNode().boundsInRoot
        val note = rule.onNodeWithText("Se mantienen las series del plan: 4 × 4–6 · 80 % 1RM · RIR 2").fetchSemanticsNode().boundsInRoot
        assertTrue("Debe comprobar el inset real del teléfono", navigationBottom > 0)
        assertTrue("CTA completo sobre navbar", button.bottom < root.bottom - navigationBottom)
        assertTrue("Nota separada de CTA", note.bottom < button.top)
        assertEquals("Inset único + 16dp del pie", navigationBottom + 16f * density, root.bottom - button.bottom, 1.5f)
        rule.onNodeWithTag(CatalogReplaceButtonTag).performClick()
        rule.runOnIdle { assertEquals(1, confirmations) }
    }

    @Test fun reemplazarQuedaSobreNavegacionYRecibeConfirmacion() = replacement(parentConsumes = false)

    @Test fun anfitrionConInsetConsumidoNoDuplicaEspacioInferior() = replacement(parentConsumes = true)

    @Test fun seleccionMideTodaLaIslaConMargenesYSuCtaQuedaSobreNavegacion() {
        var measuredSlot = 0
        rule.runOnUiThread { WindowCompat.setDecorFitsSystemWindows(rule.activity.window, false) }
        rule.setContent {
            val currentDensity = LocalDensity.current
            val inset = WindowInsets.navigationBars.getBottom(currentDensity)
            SideEffect { density = currentDensity.density; navigationBottom = inset }
            KPKNTheme {
                Box(Modifier.fillMaxSize().testTag("picker-root")) {
                    Column(Modifier.fillMaxSize()) {
                        CatalogPickerViewport {
                            CatalogSelectionSlot(visible = true, reducedMotion = true,
                                modifier = Modifier.onSizeChanged { measuredSlot = it.height }) {
                                CatalogSelectionIsland(
                                    count = 1, summary = "Press Arnold", listExpanded = false, onToggleList = {},
                                    onAdd = { confirmations += 1 }, onSuperset = null, hazeState = null,
                                    modifier = Modifier.testTag("picker-island"),
                                )
                            }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        val root = rule.onNodeWithTag("picker-root").fetchSemanticsNode().boundsInRoot
        val button = rule.onNode(hasText("Añadir 1") and hasClickAction()).fetchSemanticsNode().boundsInRoot
        val island = rule.onNodeWithTag("picker-island").fetchSemanticsNode().boundsInRoot
        assertTrue(navigationBottom > 0)
        assertTrue("Añadir completo sobre navbar", button.bottom < root.bottom - navigationBottom)
        assertEquals("Reserva de lista incluye ambos márgenes del slot", 16f * density, measuredSlot - island.height, 1.5f)
        rule.onNode(hasText("Añadir 1") and hasClickAction()).performClick()
        rule.runOnIdle { assertEquals(1, confirmations) }
    }
}

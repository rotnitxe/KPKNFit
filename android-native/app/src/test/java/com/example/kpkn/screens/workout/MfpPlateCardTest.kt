package com.example.kpkn.screens.workout

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.kpkn.debug.MfpTrainingPreview
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class,qualifiers="w360dp-h1600dp-mdpi")
class MfpPlateCardTest {
    @get:Rule val rule=createComposeRule()
    @Test fun discosUsanCargaRealSinCambiarElMarcoYSinInventarInventario() {
        var state by mutableStateOf("plates")
        rule.setContent { KPKNTheme { MfpTrainingPreview.Myo(state) } }
        rule.waitUntil(20_000) { rule.onAllNodesWithTag("load-plate-label",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("load-plate-label",useUnmergedTree=true).assertTextContains("por lado: 25 + 10 + 5")
        val before=rule.onNodeWithTag("set_execution_card",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot.height
        rule.runOnIdle { state="no_inventory" }
        rule.waitForIdle()
        rule.onNodeWithTag("load-plate-label",useUnmergedTree=true).assertDoesNotExist()
        assertEquals(before,rule.onNodeWithTag("set_execution_card",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot.height,.01f)
    }
    @Test fun cargaNoMontableIdentificaLaMasCercanaYSuDistribucionSinCambiarCargaOriginal() {
        rule.setContent { KPKNTheme { MfpTrainingPreview.Myo("plates_unreachable") } }
        rule.waitUntil(20_000) { rule.onAllNodesWithTag("load-plate-label",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("load-plate-label",useUnmergedTree=true).assertContentDescriptionEquals("60 kg · más cercana montable · por lado: 20")
        rule.onNodeWithText("59.0").assertExists()
    }
    @Test fun reversoIdentificaMyoSinConfundirLaExplicacionConRestPauseNormal() {
        rule.setContent { KPKNTheme { MfpTrainingPreview.Myo("myo_back") } }
        rule.onNodeWithText("ASÍ SE VE MYO-REPS").assertExists()
        assertTrue(rule.onAllNodesWithText("Mini 1").fetchSemanticsNodes().isNotEmpty())
    }

}

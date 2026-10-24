package com.example.kpkn.screens.sessioneditor.components

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.debug.MfpTrainingPreview
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class,qualifiers="w360dp-h1800dp-mdpi")
class ExerciseSwapSuggestionsUiTest {
    @get:Rule val rule=createComposeRule()
    @Test fun sugeridoUsaSeleccionExistenteYEsperaConfirmacion() {
        var selected: ExerciseMuscleInfo?=null
        rule.setContent { KPKNTheme { MfpTrainingPreview.Swap("with_suggestions") { selected=it } } }
        rule.waitUntil(20_000) { rule.onAllNodesWithTag("exercise-swap-suggestions").fetchSemanticsNodes().isNotEmpty() }
        val first=rule.onAllNodes(hasTestTagPrefix("swap-suggestion-")).onFirst()
        val configId=first.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.TestTag].removePrefix("swap-suggestion-")
        first.performScrollTo().performClick()
        rule.runOnIdle { assertNull(selected) }
        rule.onNode(hasText("Reemplazar por",substring=true)).assertIsEnabled().performClick()
        rule.runOnIdle { assertNotNull("La confirmación no debe quedar tapada por la isla de Añadir",selected);assertEquals(configId,selected!!.catalogConfigurationId) }
    }
    private fun hasTestTagPrefix(prefix: String)=SemanticsMatcher("tag begins $prefix") { it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag)?.startsWith(prefix)==true }
}

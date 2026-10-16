package com.example.kpkn.screens.workout.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.domain.exercises.ExerciseHistorySummary
import com.example.kpkn.screens.sessioneditor.components.CatalogDetailModel
import com.example.kpkn.screens.sessioneditor.components.ExerciseFicha
import com.example.kpkn.screens.sessioneditor.components.ExerciseFichaContext
import com.example.kpkn.ui.components.KpknOverlayHostContent
import com.example.kpkn.ui.components.KpknOverlayHostController
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.theme.KPKNTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Portales reales sin cámara ni datos sembrados en los singletons de la app. */
@RunWith(AndroidJUnit4::class)
class MediaFichaPortalDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun cerrarFichaVuelveAVisualExpandidoConModoElegidoYPlegarLiberaHost() {
        val host = KpknOverlayHostController()
        rule.setContent {
            CompositionLocalProvider(LocalKpknOverlayHost provides host) {
                KPKNTheme {
                    Box(Modifier.fillMaxSize()) {
                        SetCardExerciseMediaBack(
                            exercise = Exercise("portal-test", "Ejercicio sin ficha editorial"), onFlipBack = {},
                            modifier = Modifier.width(272.dp).height(450.dp), startExpanded = true,
                            showControlsWithoutCamera = true,
                        )
                        KpknOverlayHostContent(host)
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.onNode(hasText("Foto") and hasAnyAncestor(hasTestTag(MediaExpandedTag))).performClick()
        rule.onNode(hasContentDescription("Ficha del ejercicio") and hasAnyAncestor(hasTestTag(MediaExpandedTag))).performClick()
        rule.onNodeWithTag("exercise-ficha").assertExists()
        rule.onNodeWithTag("exercise-ficha-close").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("exercise-ficha").assertDoesNotExist()
        rule.onNode(hasContentDescription("Hacer foto") and hasAnyAncestor(hasTestTag(MediaExpandedTag))).assertExists()
        rule.runOnIdle { assertEquals(1, host.entries.size) }
        rule.onNodeWithTag("media_fold").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(MediaExpandedTag).assertDoesNotExist()
        rule.runOnIdle { assertEquals(0, host.entries.size) }
    }

    @Test fun irAHistorialDesmontaFichaYDejaAccesibleElCuerpoSinVelo() {
        val host = KpknOverlayHostController()
        var ficha by mutableStateOf(true)
        var history by mutableStateOf(false)
        var taps = 0
        val model = CatalogDetailModel(
            name = "Curl", caption = null, description = null, imageVariants = emptyList(), selectedImplementation = null,
            axes = emptyList(), configurationLine = null, configurationIncomplete = false, muscleInfo = null,
            joints = emptyList(), patternId = null, isSelected = true, canSelect = false,
            context = ExerciseFichaContext.SESSION,
            historySummary = ExerciseHistorySummary(null, null, LocalDate.of(2026, 10, 15)),
        )
        rule.setContent {
            CompositionLocalProvider(LocalKpknOverlayHost provides host) {
                KPKNTheme {
                    Box(Modifier.fillMaxSize()) {
                        if (history) Text("Historial accesible", Modifier.clickable { taps += 1 })
                        if (ficha) ExerciseFicha(model, onDismiss = { ficha = false }, onShowHistory = { history = true })
                        KpknOverlayHostContent(host)
                    }
                }
            }
        }
        rule.onNodeWithText("Ver historial").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("exercise-ficha").assertDoesNotExist()
        rule.onNodeWithText("Historial accesible").performClick()
        rule.runOnIdle { assertEquals(0, host.entries.size); assertEquals(1, taps) }
    }
}

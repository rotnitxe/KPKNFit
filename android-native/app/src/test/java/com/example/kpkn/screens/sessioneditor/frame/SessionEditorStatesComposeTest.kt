package com.example.kpkn.screens.sessioneditor.frame

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WeekVariant
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.sessioneditor.SessionRoadmapOption
import dev.chrisbanes.haze.HazeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * ED-A · A6: los tres estados del editor (cargando, error de carga y guardado bloqueado) con un solo aviso cada uno y sin solapes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h900dp-xxhdpi", application = android.app.Application::class)
class SessionEditorStatesComposeTest {

    @get:Rule
    val rule = createComposeRule()

    private val nbsp = " "

    // ───────────────────────────── A6 · estados: un solo aviso por estado ─────────────────────────────

    private val loadingMatcher = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)

    @Test
    fun cargandoEsUnaSolaFranjaSinBarraDeProgreso() {
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) { SessionEditorLoadingState() }
        }

        rule.onNodeWithTag("session_editor_state_loading").assertIsDisplayed()
        rule.onAllNodesWithText("Preparando la sesión").assertCountEquals(1)
        rule.onAllNodesWithText("CARGANDO").assertCountEquals(1)
        rule.onAllNodes(loadingMatcher).assertCountEquals(0)
        // Y no hay a la vez un aviso de error.
        rule.onNodeWithTag("session_editor_state_error").assertDoesNotExist()
        rule.onAllNodesWithText("Reintentar").assertCountEquals(0)
    }

    @Test
    fun elErrorDeCargaEsUnaSolaFranjaConSusDosSalidas() {
        var back = 0
        var retry = 0
        rule.setContent {
            SessionEditorLoadErrorState(message = null, onBack = { back++ }, onRetry = { retry++ })
        }

        rule.onAllNodesWithText("No pudimos recuperar este programa.").assertCountEquals(1)
        rule.onAllNodesWithText("Puedes reintentar la carga o volver al programa.").assertCountEquals(1)
        rule.onNodeWithText("Reintentar").performClick()
        rule.onNodeWithText("Volver").performClick()
        assertEquals(1, retry)
        assertEquals(1, back)
        rule.onNodeWithTag("session_editor_state_loading").assertDoesNotExist()
    }

    @Test
    fun elErrorDeCargaUsaElMotivoConcretoSiLoHay() {
        rule.setContent {
            SessionEditorLoadErrorState(message = "No pudimos encontrar el programa activo.", onBack = {}, onRetry = {})
        }

        rule.onAllNodesWithText("No pudimos encontrar el programa activo.").assertCountEquals(1)
        rule.onAllNodesWithText("No pudimos recuperar este programa.").assertCountEquals(0)
    }

    @Test
    fun elGuardadoBloqueadoEsUnSoloAvisoConSuAcceso() {
        var opened = 0
        val message = "No se pudo guardar: «Press militar con barra» no tiene su ficha del catálogo. Cámbialo por uno del catálogo o quítalo."
        rule.setContent {
            SessionEditorFrameBlockNotice(message = message, onOpenExercise = { opened++ })
        }

        rule.onAllNodesWithText(message).assertCountEquals(1)
        rule.onNodeWithText("Ver el ejercicio").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun elGuardadoBloqueadoSinEjercicioNoOfreceAcceso() {
        rule.setContent { SessionEditorFrameBlockNotice(message = "No se pudo guardar: falta el nombre.", onOpenExercise = null) }

        rule.onAllNodesWithText("Ver el ejercicio").assertCountEquals(0)
    }

    @Test
    fun laSnackbarNoRepiteLoQueYaDiceElAvisoFijo() {
        val block = "No se pudo guardar: «Press militar con barra» no tiene su ficha del catálogo."
        // La misma causa: no se enseña dos veces.
        assertTrue(isRedundantWhileSaveBlocked(block, block))
        assertTrue(isRedundantWhileSaveBlocked("No se pudo guardar: otro ejercicio sin ficha.", block))
        // Otra causa (el borrador local) o cualquier otro mensaje: sigue saliendo.
        assertFalse(isRedundantWhileSaveBlocked("No se pudo guardar el borrador. Vuelve a intentarlo.", block))
        assertFalse(isRedundantWhileSaveBlocked("Sesión creada para Sábado", block))
        // Sin aviso fijo no se suprime nada.
        assertFalse(isRedundantWhileSaveBlocked(block, null))
    }

}

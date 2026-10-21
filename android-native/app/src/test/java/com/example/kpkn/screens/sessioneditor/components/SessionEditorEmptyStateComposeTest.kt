package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.RepRange
import com.example.kpkn.data.models.SupersetGroup
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.sessioneditor.SessionEditorDropKind
import com.example.kpkn.screens.sessioneditor.SessionEditorDropPlan
import com.example.kpkn.screens.sessioneditor.design.SessionEditorDashedAddButton
import com.example.kpkn.screens.sessioneditor.design.SessionEditorDropHintBanner
import com.example.kpkn.screens.sessioneditor.design.SessionEditorOutsideDropZone
import com.example.kpkn.screens.sessioneditor.design.SessionExerciseUndoNotice
import com.example.kpkn.screens.sessioneditor.design.SessionModuleEdge
import com.example.kpkn.screens.sessioneditor.design.SessionModuleSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ED-A · A2: la sesión vacía con sus dos caminos (Fuerza y Cardio), la opción pendiente y la colocación «Al inicio / Al final».
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h900dp-xxhdpi")
class SessionEditorEmptyStateComposeTest {

    @get:Rule
    val rule = createComposeRule()

    private val noop: () -> Unit = {}

    // ───────────────────────────── A2 · sesión vacía ─────────────────────────────

    @Test
    fun laSesionVaciaOfreceSoloFuerzaYCardio() {
        var strength = 0
        var cardio = 0
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                SessionEditorEmptyState(onChooseStrength = { strength++ }, onChooseCardio = { cardio++ })
            }
        }

        rule.onNodeWithText("Fuerza").assertIsDisplayed()
        rule.onNodeWithText("Cardio").assertIsDisplayed()
        rule.onNodeWithText("Ejercicios, grupos y superseries").assertIsDisplayed()
        rule.onNodeWithText("Continuo, series o intervalos").assertIsDisplayed()
        // Nada de una tercera opción ni de descripciones que relacionen una con otra.
        rule.onAllNodesWithText("Fuerza + cardio").assertCountEquals(0)
        rule.onAllNodesWithText("Puede ser toda tu sesión", substring = true).assertCountEquals(0)
        rule.onAllNodesWithText("Combina", substring = true).assertCountEquals(0)
        rule.onAllNodesWithText("Al inicio", substring = true).assertCountEquals(0)
        rule.onAllNodesWithText("Al final", substring = true).assertCountEquals(0)

        rule.onNodeWithTag("session_editor_option_strength").performClick()
        rule.onNodeWithTag("session_editor_option_cardio").performClick()
        assertEquals(1, strength)
        assertEquals(1, cardio)
    }

    @Test
    fun cadaOpcionDeLaSesionVaciaMideMasDe44dp() {
        var density = 1f
        rule.setContent {
            density = LocalDensity.current.density
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                SessionEditorEmptyState(onChooseStrength = {}, onChooseCardio = {})
            }
        }

        listOf("session_editor_option_strength", "session_editor_option_cardio").forEach { tag ->
            val size = rule.onNodeWithTag(tag).fetchSemanticsNode().size
            assertTrue("$tag mide ${size.height / density} dp de alto", size.height / density >= 44f)
        }
    }

    @Test
    fun laOpcionPendienteEsLaMismaPiezaQueLaDeLaSesionVacia() {
        var chosen = 0
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                SessionEditorSpaceOption(space = SessionEditorSpace.CARDIO, onClick = { chosen++ })
            }
        }

        rule.onNodeWithText("Cardio").assertIsDisplayed()
        rule.onAllNodesWithText("Fuerza").assertCountEquals(0)
        rule.onNodeWithTag("session_editor_option_cardio").performClick()
        assertEquals(1, chosen)
    }

    @Test
    fun laColocacionOfreceAlInicioYAlFinal() {
        val changes = mutableListOf<Boolean>()
        rule.setContent {
            SessionEditorCardioPlacementControl(atStart = true, onChange = { changes += it })
        }

        rule.onNodeWithText("CARDIO EN LA SESIÓN").assertIsDisplayed()
        rule.onNodeWithText("Al inicio").assertIsDisplayed()
        rule.onNodeWithText("Al final").performClick()
        rule.onNodeWithText("Al inicio").performClick()
        assertEquals(listOf(false, true), changes)
    }

}

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
 * ED-A · A4: el aviso de borrar un ejercicio, con «Deshacer» y su cuenta atrás de 5 s.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h900dp-xxhdpi")
class SessionEditorUndoNoticeComposeTest {

    @get:Rule
    val rule = createComposeRule()

    private val noop: () -> Unit = {}

    // ───────────────────────────── A4 · borrar con deshacer ─────────────────────────────

    @Test
    fun elAvisoDeBorrarTieneDeshacerYUnaCuentaAtrasDe5Segundos() {
        var undone = 0
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides false) {
                SessionExerciseUndoNotice(onUndo = { undone++ })
            }
        }
        fun remaining(): Float = rule.onNodeWithTag("session_undo_countdown").fetchSemanticsNode()
            .config[SemanticsProperties.ProgressBarRangeInfo].current

        rule.onNodeWithText("Ejercicio eliminado").assertIsDisplayed()
        rule.onNodeWithText("Deshacer").assertIsDisplayed()
        rule.mainClock.advanceTimeByFrame()
        assertTrue("al empezar queda toda la ventana", remaining() > 0.95f)

        rule.mainClock.advanceTimeBy(2_500)
        val half = remaining()
        assertTrue("a mitad de la ventana queda la mitad ($half)", half in 0.4f..0.6f)

        rule.mainClock.advanceTimeBy(2_600)
        assertEquals(0f, remaining(), 0.001f)

        rule.onNodeWithText("Deshacer").performClick()
        assertEquals(1, undone)
    }

    @Test
    fun conMovimientoReducidoLaCuentaAtrasQuedaQuieta() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                SessionExerciseUndoNotice(onUndo = {})
            }
        }
        rule.mainClock.advanceTimeBy(3_000)

        val remaining = rule.onNodeWithTag("session_undo_countdown").fetchSemanticsNode()
            .config[SemanticsProperties.ProgressBarRangeInfo].current
        assertEquals(0.6f, remaining, 0.001f)
    }

    @Test
    fun elAvisoDeBorrarOcupaElSitioDeLaFilaDe64dp() {
        var density = 1f
        rule.setContent {
            density = LocalDensity.current.density
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                SessionExerciseUndoNotice(onUndo = {}, modifier = Modifier.testTag("aviso"))
            }
        }

        val size = rule.onNodeWithTag("aviso").fetchSemanticsNode().size
        assertTrue("el aviso mide ${size.height / density} dp", size.height / density >= 64f)
    }

}

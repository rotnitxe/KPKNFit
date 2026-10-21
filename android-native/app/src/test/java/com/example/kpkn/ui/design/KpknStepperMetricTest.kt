package com.example.kpkn.ui.design

import android.app.Application
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Comportamiento de [KpknStepperMetric]: pasos, repetición al mantener, edición y botones deshabilitados. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
class KpknStepperMetricTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun unToqueAlSumarDaUnSoloPaso() {
        var plus = 0
        rule.setContent {
            KpknStepperMetric(label = "KG", value = "80", onMinus = {}, onPlus = { plus++ }, contentDescription = "80 kilos")
        }
        rule.onNodeWithContentDescription("Sumar kilos").performClick()
        rule.waitForIdle()
        assertEquals(1, plus)
    }

    @Test
    fun manteniendoPulsadoRepiteAlMenosCincoVeces() {
        rule.mainClock.autoAdvance = false
        var minus = 0
        rule.setContent {
            KpknStepperMetric(label = "REPS", value = "8", onMinus = { minus++ }, onPlus = {}, contentDescription = "8 repeticiones")
        }
        rule.onNodeWithContentDescription("Restar repeticiones").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithContentDescription("Restar repeticiones").performTouchInput { up() }
        rule.waitForIdle()
        assertTrue("repitió $minus veces", minus >= 5)
    }

    @Test
    fun tocarLaCifraLaEditaYHechoLlamaACommit() {
        var committed: String? = null
        rule.setContent {
            KpknStepperMetric(
                label = "KG",
                value = "80",
                onMinus = {},
                onPlus = {},
                contentDescription = "80 kilos",
                onCommit = { committed = it },
            )
        }
        rule.onNodeWithContentDescription("80 kilos").performClick()
        rule.waitForIdle()
        rule.onNode(hasSetTextAction()).performTextReplacement("72,5")
        rule.onNode(hasSetTextAction()).performImeAction()
        rule.waitForIdle()
        assertEquals("72,5", committed)
    }

    @Test
    fun sinOnCommitLaCifraNoSeEdita() {
        rule.setContent {
            KpknStepperMetric(label = "REPS", value = "8", onMinus = {}, onPlus = {}, contentDescription = "8 repeticiones")
        }
        rule.onNodeWithContentDescription("8 repeticiones").performClick()
        rule.waitForIdle()
        rule.onNode(hasSetTextAction()).assertDoesNotExist()
    }

    @Test
    fun menosDeshabilitadoNoLlamaYLoAnuncia() {
        var minus = 0
        rule.setContent {
            KpknStepperMetric(
                label = "REPS",
                value = "1",
                onMinus = { minus++ },
                onPlus = {},
                contentDescription = "1 repetición",
                minusEnabled = false,
            )
        }
        rule.onNodeWithContentDescription("Restar repeticiones").assertIsNotEnabled().performClick()
        rule.onNodeWithContentDescription("Sumar repeticiones").assertIsEnabled()
        rule.waitForIdle()
        assertEquals(0, minus)
    }
}

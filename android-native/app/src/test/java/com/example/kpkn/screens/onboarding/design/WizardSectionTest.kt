package com.example.kpkn.screens.onboarding.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La sección de la página larga con Compose sobre Robolectric: el control del paso que asoma llega tras la animación de llegada
 * (no en el cuadro en que el paso anterior se confirma) y el activo se compone en el acto.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h900dp", application = android.app.Application::class)
class WizardSectionTest {

    @get:Rule
    val rule = createComposeRule()

    private var mode by mutableStateOf(WizardPageMode.Peek)
    private var natural = 0

    private fun show(initial: WizardPageMode, reduced: Boolean = false, onEdit: (() -> Unit)? = null) {
        mode = initial
        rule.setContent {
            WizardPageItem(
                mode = mode,
                eyebrow = "Paso 2 de 14 · Entreno",
                title = "¿Dónde entrenas?",
                subtitle = "Elige uno o varios lugares.",
                summaryLabel = "Marcas",
                summaryValue = "Sentadilla 120 kg, Press banca 80 kg",
                stepTag = "step",
                summaryTag = "summary",
                onEdit = onEdit,
                onNaturalHeight = { natural = it },
                reducedMotion = reduced,
                peekWindowPx = 400,
            ) {
                Box(Modifier.testTag("control").width(200.dp)) { Text("El control") }
            }
        }
    }

    // ── El control del paso que asoma llega tras la animación ─────────────────────

    @Test
    fun theControlOfTheStepThatPeeksIsNotComposedInTheArrivalFrame() {
        rule.mainClock.autoAdvance = false
        show(WizardPageMode.Peek)
        rule.mainClock.advanceTimeByFrame()
        // La etiqueta, el título y el subtítulo sí están desde el primer cuadro; el control, no.
        rule.onNodeWithText("¿Dónde entrenas?", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Elige uno o varios lugares.", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("control", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun theControlArrivesOnceTheSlideIsOver() {
        rule.mainClock.autoAdvance = false
        show(WizardPageMode.Peek)
        rule.mainClock.advanceTimeBy(WizardMotion.SlideMillis.toLong())
        rule.onNodeWithTag("control", useUnmergedTree = true).assertDoesNotExist()
        rule.mainClock.advanceTimeBy(300)
        rule.onNodeWithTag("control", useUnmergedTree = true).assertExists()
    }

    @Test
    fun withReducedMotionTheControlArrivesAfterAShortBreath() {
        rule.mainClock.autoAdvance = false
        show(WizardPageMode.Peek, reduced = true)
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag("control", useUnmergedTree = true).assertDoesNotExist()
        rule.mainClock.advanceTimeBy(300)
        rule.onNodeWithTag("control", useUnmergedTree = true).assertExists()
    }

    @Test
    fun theActiveStepComposesItsControlAtOnce() {
        rule.mainClock.autoAdvance = false
        show(WizardPageMode.Active)
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag("control", useUnmergedTree = true).assertExists()
    }

    @Test
    fun aStepConfirmedBeforeItsControlArrivedComposesItAtOnceWithoutWaiting() {
        rule.mainClock.autoAdvance = false
        show(WizardPageMode.Peek)
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithTag("control", useUnmergedTree = true).assertDoesNotExist()
        // La persona confirma antes de que acabe la espera: el paso ya es el activo y no se hace esperar.
        rule.runOnUiThread { mode = WizardPageMode.Active }
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag("control", useUnmergedTree = true).assertExists()
    }

    @Test
    fun onceComposedTheControlIsNotTakenAwayWhenTheStepFoldsOrPeeksAgain() {
        rule.mainClock.autoAdvance = false
        show(WizardPageMode.Active)
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag("control", useUnmergedTree = true).assertExists()
        rule.runOnUiThread { mode = WizardPageMode.Peek }
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag("control", useUnmergedTree = true).assertExists()
    }

    @Test
    fun theStepThatPeeksIsInertAndOnlyAnnouncesWhatComesNext() {
        show(WizardPageMode.Peek)
        rule.waitForIdle()
        // El paso que asoma no es el activo (no lleva la marca del paso activo) y TalkBack solo oye cuál viene después.
        rule.onNodeWithTag("step").assertDoesNotExist()
        rule.onNodeWithContentDescription("Siguiente: ¿Dónde entrenas?").assertExists()
    }
}

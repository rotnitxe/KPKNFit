package com.example.kpkn.screens.onboarding.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.nutrition.bodyFatDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * El escenario completo del paso de grasa corporal (figura, botones de figura, regla y lectura) con Compose sobre
 * Robolectric: lo que se ve (la lectura sigue al dedo), lo que se guarda (solo al soltar) y la geometría de un
 * teléfono de 390 × 844 dp (escenario de 380 dp, regla a la derecha y botones de 48 dp en la esquina).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class WizardBodyFatPickerTest {

    @get:Rule
    val rule = createComposeRule()

    private var percent by mutableStateOf(25.0)
    private var model by mutableStateOf("male")
    private var declared by mutableStateOf(false)
    private val committed = mutableListOf<Int>()
    private val models = mutableListOf<String>()

    private fun show() {
        rule.setContent {
            WizardBodyFatPicker(
                percent = percent,
                model = model,
                declared = declared,
                onPercentChange = { value ->
                    committed += value
                    percent = value.toDouble()
                    declared = true
                },
                onModelChange = { value ->
                    models += value
                    model = value
                },
            )
        }
    }

    private fun textOf(tag: String): String =
        rule.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("") { it.text }

    @Test
    fun theReadingStartsOnTheStartingValueWithTheMaleDescriptor() {
        show()
        assertEquals("25 %", textOf(BODY_FAT_VALUE_TAG))
        assertEquals(bodyFatDescriptor(25.0, "male"), textOf(BODY_FAT_DESCRIPTOR_TAG))
        assertTrue("sin declarar no se escribe nada", committed.isEmpty())
    }

    @Test
    fun theModelButtonsAreRadioButtonsWithATouchZoneOfAtLeast48Dp() {
        show()
        for (tag in listOf(bodyFatModelTag("female"), bodyFatModelTag("male"))) {
            rule.onNodeWithTag(tag).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            assertEquals(Role.RadioButton, rule.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.Role])
        }
        rule.onNodeWithTag(bodyFatModelTag("male")).assertIsSelected()
        rule.onNodeWithTag(bodyFatModelTag("female")).assertIsNotSelected()
    }

    @Test
    fun switchingToTheFemaleFigureChangesTheDescriptorAndKeepsThePercent() {
        percent = 20.0
        declared = true
        show()
        assertEquals(bodyFatDescriptor(20.0, "male"), textOf(BODY_FAT_DESCRIPTOR_TAG))
        rule.onNodeWithTag(bodyFatModelTag("female")).performClick()
        rule.waitForIdle()
        assertEquals(listOf("female"), models)
        assertEquals(bodyFatDescriptor(20.0, "female"), textOf(BODY_FAT_DESCRIPTOR_TAG))
        assertEquals("20 %", textOf(BODY_FAT_VALUE_TAG))
        assertTrue("cambiar de figura no declara ningún porcentaje", committed.isEmpty())
        rule.onNodeWithTag(bodyFatModelTag("female")).assertIsSelected()
        rule.onNodeWithTag(bodyFatModelTag("male")).assertIsNotSelected()
    }

    @Test
    fun aTapOnTheRulerCommitsOnceAndTheReadingFollows() {
        show()
        rule.onNodeWithTag(BODY_FAT_RULER_TAG).performTouchInput { click(Offset(width / 2f, 0f)) }
        rule.waitForIdle()
        assertEquals(listOf(5), committed)
        assertEquals("5 %", textOf(BODY_FAT_VALUE_TAG))
        assertEquals(bodyFatDescriptor(5.0, "male"), textOf(BODY_FAT_DESCRIPTOR_TAG))
    }

    @Test
    fun draggingPaintsAtOnceButOnlyCommitsWhenTheFingerLifts() {
        show()
        rule.onNodeWithTag(BODY_FAT_RULER_TAG).performTouchInput {
            down(Offset(width / 2f, 0f))
            moveTo(Offset(width / 2f, height - 1f))
        }
        rule.waitForIdle()
        assertTrue("mientras el dedo sigue abajo no se escribe el borrador: $committed", committed.isEmpty())
        assertEquals("la lectura sigue al dedo", "50 %", textOf(BODY_FAT_VALUE_TAG))
        rule.onNodeWithTag(BODY_FAT_RULER_TAG).performTouchInput { up() }
        rule.waitForIdle()
        assertEquals(listOf(50), committed)
        assertEquals("50 %", textOf(BODY_FAT_VALUE_TAG))
    }

    @Test
    @Config(sdk = [34], application = android.app.Application::class, qualifiers = "w390dp-h844dp-xxhdpi")
    fun theStageMeasures380DpTallOnATypicalPhoneWithTheRulerOnTheRightAndTheButtonsInTheCorner() {
        rule.setContent {
            // La página deja 24 dp a cada lado: en 390 dp quedan 342 dp para el control.
            Box(Modifier.width(342.dp)) {
                WizardBodyFatPicker(
                    percent = percent,
                    model = model,
                    declared = declared,
                    onPercentChange = {},
                    onModelChange = {},
                )
            }
        }
        val ruler = rule.onNodeWithTag(BODY_FAT_RULER_TAG).getBoundsInRoot()
        val female = rule.onNodeWithTag(bodyFatModelTag("female")).getBoundsInRoot()
        val male = rule.onNodeWithTag(bodyFatModelTag("male")).getBoundsInRoot()
        val reading = rule.onNodeWithTag(BODY_FAT_VALUE_TAG).getBoundsInRoot()
        println(
            "STAGE_GEOMETRY ruler=[${ruler.left.value}, ${ruler.top.value}, ${ruler.right.value}, ${ruler.bottom.value}] " +
                "width=${(ruler.right.value - ruler.left.value)} height=${(ruler.bottom.value - ruler.top.value)} " +
                "female=[${female.left.value}, ${female.top.value}, ${(female.right.value - female.left.value)}x${(female.bottom.value - female.top.value)}] " +
                "male=[${male.left.value}, ${male.top.value}, ${(male.right.value - male.left.value)}x${(male.bottom.value - male.top.value)}] " +
                "reading=[${reading.left.value}, ${reading.top.value}, ${(reading.bottom.value - reading.top.value)}]",
        )
        // El escenario mide 380 dp de alto y la regla ocupa todo ese alto, pegada al borde derecho de los 342 dp.
        assertEquals(380f, (ruler.bottom.value - ruler.top.value), 0.5f)
        assertEquals(342f, ruler.right.value, 0.5f)
        // Ancho: 38 dp fijos (punta, marcas, hueco y aire) más lo que mida el número «50». Robolectric no mide fuentes
        // de verdad (casi 0 dp); en un teléfono el «50» a 13 sp suma ≈ 16 dp y la regla queda en ≈ 54 dp.
        val rulerWidth = ruler.right.value - ruler.left.value
        assertTrue("la regla mide entre 38 y 80 dp de ancho: $rulerWidth", rulerWidth in 38f..80f)
        // Los dos botones: zonas de 48 × 48 dp, juntos, en la esquina superior izquierda del escenario.
        assertEquals(48f, (female.right.value - female.left.value), 0.5f)
        assertEquals(48f, (female.bottom.value - female.top.value), 0.5f)
        assertEquals(0f, female.left.value, 0.5f)
        assertEquals(0f, female.top.value, 0.5f)
        assertEquals(48f, male.left.value, 0.5f)
        assertEquals(0f, male.top.value, 0.5f)
        // La lectura va debajo del escenario.
        assertTrue("la lectura empieza bajo el escenario: ${reading.top.value}", reading.top.value >= 380f)
    }
}

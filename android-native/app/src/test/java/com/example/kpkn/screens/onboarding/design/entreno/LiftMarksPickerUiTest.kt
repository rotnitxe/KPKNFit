package com.example.kpkn.screens.onboarding.design.entreno

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.example.kpkn.domain.onboarding.LiftMark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Las marcas con Compose sobre Robolectric: una regla desplegada a la vez, el conmutador de unidad, «No la sé»,
 * declarar al primer contacto, el escalón de 2,5 kg / 5 lb al arrastrar y que lo guardado sea siempre kg.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w420dp-h1600dp")
class LiftMarksPickerUiTest {

    @get:Rule
    val rule = createComposeRule()

    private val emitted = mutableListOf<Pair<LiftMark, Double?>>()
    private val units = mutableListOf<String>()
    private val values = mutableStateMapOf<LiftMark, Double>()
    private var unit by mutableStateOf("kg")

    private fun show(
        lifts: List<LiftMark> = listOf(LiftMark.SQUAT, LiftMark.BENCH, LiftMark.DEADLIFT),
        marks: Map<LiftMark, Double> = emptyMap(),
        startUnit: String = "kg",
    ) {
        values.clear()
        values.putAll(marks)
        unit = startUnit
        rule.setContent {
            LiftMarksPicker(
                lifts = lifts,
                valuesKg = values.toMap(),
                unit = unit,
                onValueKg = { lift, kg ->
                    emitted += lift to kg
                    if (kg == null) values.remove(lift) else values[lift] = kg
                },
                onUnit = {
                    units += it
                    unit = it
                },
            )
        }
    }

    private fun ruler(lift: LiftMark) = rule.onNodeWithTag("setup-mark-${lift.name}-ruler")

    @Test
    fun onlyTheFirstRulerIsUnfolded() {
        show()
        ruler(LiftMark.SQUAT).assertExists()
        ruler(LiftMark.BENCH).assertDoesNotExist()
        ruler(LiftMark.DEADLIFT).assertDoesNotExist()
    }

    @Test
    fun tappingAnotherLiftUnfoldsItAndFoldsThePreviousOne() {
        show()
        rule.onNodeWithTag("setup-mark-BENCH").performClick()
        rule.waitForIdle()
        ruler(LiftMark.BENCH).assertExists()
        ruler(LiftMark.SQUAT).assertDoesNotExist()
    }

    @Test
    fun theUnitSwitchReportsTheOtherUnitEachTime() {
        show()
        val config = rule.onNodeWithTag(MARK_UNIT_TAG).fetchSemanticsNode().config
        assertEquals(Role.Switch, config[SemanticsProperties.Role])
        rule.onNodeWithTag(MARK_UNIT_TAG).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(MARK_UNIT_TAG).performClick()
        rule.waitForIdle()
        assertEquals(listOf("lb", "kg"), units)
    }

    @Test
    fun setProgressDeclaresTheMarkInKgOnTheTwoAndAHalfStep() {
        show()
        ruler(LiftMark.SQUAT).performSemanticsAction(SemanticsActions.SetProgress) { it(152f) }
        rule.waitForIdle()
        assertTrue("sin emisiones", emitted.isNotEmpty())
        val last = emitted.last()
        assertEquals(LiftMark.SQUAT, last.first)
        assertEquals("152 se ajusta a 152,5", 152.5, last.second!!, 1e-9)
        for ((_, kg) in emitted) assertEquals("$kg no es múltiplo de 2,5", 0.0, kg!! % 2.5, 1e-9)
        rule.onNodeWithText("152,5 kg").assertExists()
    }

    @Test
    fun inPoundsTheMarkIsStillStoredInKg() {
        show(startUnit = "lb")
        ruler(LiftMark.SQUAT).performSemanticsAction(SemanticsActions.SetProgress) { it(315f) }
        rule.waitForIdle()
        val last = emitted.last()
        assertEquals(MarksMath.lbToKg(315.0), last.second!!, 1e-9)
        rule.onNodeWithText("315 lb").assertExists()
    }

    @Test
    fun changingTheUnitRestatesTheSameKgWithoutTouchingIt() {
        show(marks = mapOf(LiftMark.SQUAT to 142.5))
        rule.onNodeWithText("142,5 kg").assertExists()
        rule.onNodeWithTag(MARK_UNIT_TAG).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("314,2 lb").assertExists()
        assertTrue("cambiar de unidad no escribe nada: $emitted", emitted.isEmpty())
        assertEquals(142.5, values.getValue(LiftMark.SQUAT), 0.0)
    }

    @Test
    fun iDontKnowClearsTheMark() {
        show(marks = mapOf(LiftMark.SQUAT to 142.5))
        rule.onNodeWithTag("setup-mark-SQUAT-unknown").performClick()
        rule.waitForIdle()
        assertEquals(listOf<Pair<LiftMark, Double?>>(LiftMark.SQUAT to null), emitted)
        assertTrue(LiftMark.SQUAT !in values)
    }

    @Test
    fun iDontKnowWhileAlreadyUnknownDoesNotDeclareAnything() {
        show()
        // Sin marca el interruptor ya está activo: tocarlo no declara nada (declarar es mover o tocar la regla).
        rule.onNodeWithTag("setup-mark-SQUAT-unknown").performClick()
        rule.waitForIdle()
        assertTrue("no debe avisar: $emitted", emitted.isEmpty())
    }

    @Test
    fun theFirstTouchOnTheRulerDeclaresTheStartingMark() {
        show()
        assertTrue(emitted.isEmpty())
        ruler(LiftMark.SQUAT).performTouchInput { click(Offset(width / 2f, height / 2f)) }
        rule.waitForIdle()
        assertEquals(LiftMark.SQUAT, emitted.last().first)
        assertEquals(LiftMarkDefaults.startKg(LiftMark.SQUAT), emitted.last().second!!, 1e-9)
    }

    @Test
    fun draggingTheRulerLeftRaisesTheMarkInTwoAndAHalfSteps() {
        show()
        ruler(LiftMark.SQUAT).performTouchInput {
            swipe(Offset(width * 0.8f, height / 2f), Offset(width * 0.3f, height / 2f), durationMillis = 400)
        }
        rule.waitForIdle()
        assertTrue("sin emisiones", emitted.isNotEmpty())
        val start = LiftMarkDefaults.startKg(LiftMark.SQUAT)
        assertTrue("el valor sube: ${emitted.map { it.second }}", emitted.last().second!! > start + 10.0)
        for ((_, kg) in emitted) assertEquals("$kg no es múltiplo de 2,5", 0.0, kg!! % 2.5, 1e-9)
        // Escalón a escalón y en orden: sin repetidos consecutivos.
        val ordered = emitted.map { it.second!! }
        assertEquals(ordered.distinct().sorted(), ordered.distinct())
    }

    @Test
    fun aFoldedLiftShowsItsMarkOrIDontKnow() {
        show(marks = mapOf(LiftMark.BENCH to 95.0))
        rule.onNodeWithText("95 kg").assertExists()
        // Peso muerto sin marca: «No la sé» plegado, además del interruptor de la regla desplegada.
        rule.onAllNodesWithText("No la sé").assertCountEquals(2)
    }

    @Test
    fun theRulerAnnouncesItselfAsASlider() {
        show(marks = mapOf(LiftMark.SQUAT to 142.5))
        val config = ruler(LiftMark.SQUAT).fetchSemanticsNode().config
        val range = config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(142.5f, range.current, 0f)
        assertEquals(20f, range.range.start, 0f)
        assertEquals(400f, range.range.endInclusive, 0f)
        assertTrue(config.contains(SemanticsActions.SetProgress))
        assertEquals("142,5 kg", config[SemanticsProperties.StateDescription])
    }
}

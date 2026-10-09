package com.example.kpkn.screens.onboarding.design.entreno

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.domain.calculations.calculateHybrid1RM
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
    private val reps = mutableStateMapOf<LiftMark, Int>()
    private val repEvents = mutableListOf<Pair<LiftMark, Int?>>()

    private fun show(
        lifts: List<LiftMark> = listOf(LiftMark.SQUAT, LiftMark.BENCH, LiftMark.DEADLIFT),
        marks: Map<LiftMark, Double> = emptyMap(),
        startUnit: String = "kg",
        repsMarks: Map<LiftMark, Int> = emptyMap(),
        variants: Map<LiftMark, String> = emptyMap(),
    ) {
        values.clear()
        values.putAll(marks)
        reps.clear()
        reps.putAll(repsMarks)
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
                repsByLift = reps.toMap(),
                onReps = { lift, count ->
                    repEvents += lift to count
                    if (count == null) reps.remove(lift) else reps[lift] = count
                },
                variantLabels = variants,
            )
        }
    }

    private fun ruler(lift: LiftMark) = rule.onNodeWithTag("setup-mark-${lift.name}-ruler")

    private fun repsRuler(lift: LiftMark) = rule.onNodeWithTag("setup-mark-${lift.name}-reps")

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

    @Test
    fun withReducedMotionTheRulerJumpsStraightToTheMarkWithoutIntermediateValues() {
        // «Reducir movimiento» (escala de animaciones 0): nada de resorte, la regla salta a la marca y avisa una sola vez.
        Settings.Global.putFloat(
            ApplicationProvider.getApplicationContext<Context>().contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f,
        )
        show()
        ruler(LiftMark.SQUAT).performSemanticsAction(SemanticsActions.SetProgress) { it(150f) }
        rule.waitForIdle()
        assertEquals(listOf<Pair<LiftMark, Double?>>(LiftMark.SQUAT to 150.0), emitted)
    }

    @Test
    fun everyTouchTargetIsAtLeast48dp() {
        show(marks = mapOf(LiftMark.BENCH to 95.0))
        val tags = listOf(
            MARK_UNIT_TAG,
            "setup-mark-SQUAT",
            "setup-mark-BENCH",
            "setup-mark-DEADLIFT",
            "setup-mark-SQUAT-unknown",
            "setup-mark-SQUAT-ruler",
            "setup-mark-SQUAT-mode",
        )
        for (tag in tags) {
            rule.onNodeWithTag(tag).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    fun theModeSwitchShowsAndHidesTheRepsRuler() {
        show(marks = mapOf(LiftMark.SQUAT to 100.0))
        repsRuler(LiftMark.SQUAT).assertDoesNotExist()
        rule.onNodeWithTag("setup-mark-SQUAT-mode").performClick()
        rule.waitForIdle()
        repsRuler(LiftMark.SQUAT).assertExists()
        rule.onNodeWithTag("setup-mark-SQUAT-mode").performClick()
        rule.waitForIdle()
        repsRuler(LiftMark.SQUAT).assertDoesNotExist()
    }

    @Test
    fun choosingFiveWritesFiveRepsAndBackToOneRmWritesNull() {
        show(marks = mapOf(LiftMark.SQUAT to 100.0))
        rule.onNodeWithTag("setup-mark-SQUAT-mode").performClick()
        rule.waitForIdle()
        repsRuler(LiftMark.SQUAT).performSemanticsAction(SemanticsActions.SetProgress) { it(5f) }
        rule.waitForIdle()
        assertEquals(LiftMark.SQUAT to 5, repEvents.last())
        rule.onNodeWithText("100 kg × 5").assertExists()
        rule.onNodeWithTag("setup-mark-SQUAT-mode").performClick()
        rule.waitForIdle()
        assertEquals(LiftMark.SQUAT to null, repEvents.last())
        rule.onNodeWithText("100 kg").assertExists()
    }

    @Test
    fun aFoldedRowShowsTheSetAsKgTimesReps() {
        show(lifts = listOf(LiftMark.SQUAT, LiftMark.BENCH), marks = mapOf(LiftMark.BENCH to 100.0), repsMarks = mapOf(LiftMark.BENCH to 5))
        rule.onNodeWithText("100 kg × 5").assertExists()
    }

    @Test
    fun theEstimateLineMatchesTheHybridFormula() {
        show(marks = mapOf(LiftMark.SQUAT to 100.0), repsMarks = mapOf(LiftMark.SQUAT to 5))
        val expected = "≈ 1RM estimado: ${MarksMath.formatMark(calculateHybrid1RM(100.0, 5), "kg")}"
        assertEquals("≈ 1RM estimado: 112,5 kg", expected)
        rule.onNodeWithText(expected).assertExists()
    }

    @Test
    fun declaringRepsWithoutAWeightDeclaresTheShownWeightToo() {
        show(lifts = listOf(LiftMark.SQUAT))
        rule.onNodeWithTag("setup-mark-SQUAT-mode").performClick()
        rule.waitForIdle()
        // El centro de 2 a 10 es 6: tocarlo declara 6 repeticiones y el kg que se ve (el de arranque, 100 kg).
        repsRuler(LiftMark.SQUAT).performTouchInput { click(Offset(width / 2f, height / 2f)) }
        rule.waitForIdle()
        assertEquals(LiftMark.SQUAT to 6, repEvents.last())
        assertTrue("el kg que se ve no se declaró: $emitted", emitted.any { it.first == LiftMark.SQUAT && it.second == 100.0 })
        rule.onNodeWithText("100 kg × 6").assertExists()
    }

    @Test
    fun theVariantGoesIntoTheRowName() {
        show(lifts = listOf(LiftMark.SQUAT), marks = mapOf(LiftMark.SQUAT to 100.0), variants = mapOf(LiftMark.SQUAT to "Frontal"))
        rule.onNodeWithText("Sentadilla · Frontal").assertExists()
    }
}

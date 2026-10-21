package com.example.kpkn.screens.workout.components

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.*
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.workout.*
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

internal fun liveModeExercise(mode: TrainingMode, rom: Boolean = false): Exercise {
    val set = ExerciseSet("mode-set", targetReps = if (mode == TrainingMode.SOLO_RPE) 10 else 7,
        weight = when (mode) { TrainingMode.TIME, TrainingMode.CUSTOM -> 0.0; TrainingMode.DISTANCE -> 24.0; TrainingMode.SOLO_RPE -> 99.0; else -> 100.0 },
        targetPercentageRM = 80.0.takeIf { mode == TrainingMode.RM }, targetDuration = 60.takeIf { mode == TrainingMode.TIME },
        plannedTargetV2 = when (mode) { TrainingMode.DISTANCE -> 40.0; TrainingMode.CUSTOM -> 30.0; else -> null },
        purpose = if (mode == TrainingMode.AMRAP) SetPurpose.AMRAP else SetPurpose.NONE,
        amrapMinimumReps = 7.takeIf { mode == TrainingMode.AMRAP },
        targetRIR = 2.takeIf { mode !in listOf(TrainingMode.RM, TrainingMode.SOLO_RPE, TrainingMode.TIME) },
        targetRPE = 8.0.takeIf { mode == TrainingMode.SOLO_RPE || mode == TrainingMode.TIME },
        intensityMode = when (mode) { TrainingMode.RM -> IntensityMode.SOLO_RM; TrainingMode.SOLO_RPE, TrainingMode.TIME -> IntensityMode.RPE; TrainingMode.AMRAP -> IntensityMode.AMRAP; else -> IntensityMode.RIR },
        loadModeV2 = if (mode == TrainingMode.TIME || mode == TrainingMode.CUSTOM) LoadModeV2.BODYWEIGHT else LoadModeV2.LOAD)
    return Exercise("mode-exercise", when (mode) { TrainingMode.CUSTOM -> "Remo ergómetro"; TrainingMode.DISTANCE -> "Paseo del granjero"; TrainingMode.TIME -> "Plancha"; TrainingMode.SOLO_RPE -> "Elevaciones laterales"; else -> "Press banca" },
        trainingMode = mode, customUnit = "brazadas".takeIf { mode == TrainingMode.CUSTOM },
        reference1RM = 125.0.takeIf { mode == TrainingMode.RM }, trackRom = rom, sets = listOf(set))
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h880dp-xxhdpi")
class LiveSetModesUiTest {
    @get:Rule val rule = createComposeRule()
    private val holder = RecordActionHolder()
    private val mode = mutableStateOf(TrainingMode.REPS)
    private val rom = mutableStateOf(false)
    private var density = 1f
    private var now = 10_000L
    private var draft: WorkoutSetDraft? = null
    private data class Recorded(val unit: UnitModeV2, val weight: Double, val value: Double, val effort: Double?, val advanced: SetAdvancedFeedback)
    private val records = mutableListOf<Recorded>()

    private fun show(initial: TrainingMode, height: Int = 880, font: Float = 1f, initialDraft: WorkoutSetDraft? = null,
        transform: (Exercise) -> Exercise = { it }, extension: Boolean = false, completed: CompletedSet? = null) {
        mode.value = initial
        rule.setContent {
            density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, font), LocalWizardReducedMotion provides true, LocalKpknOverlayHost provides null,
                LocalLiveSessionCompactLayout provides (height < 800)) {
                KPKNTheme { MaterialTheme {
                    val exercise = transform(liveModeExercise(mode.value, rom.value))
                    val set = exercise.sets.single()
                    Box(Modifier.requiredSize(360.dp, height.dp)) {
                        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 156.dp).width(312.dp)) {
                            LiveSessionCardFrame {
                                key(mode.value, rom.value) {
                                    SetInputCardV2(exercise, 0, set, null,
                                        sessionCompletedSet = completed,
                                        weightSuggestion = WeightSuggestion(88.0, "histórico"), previousSessionFirstSetWeight = 90.0,
                                        onShowHistory = {}, onSetBodyWeight = {}, initialBodyWeight = 80.0,
                                        recordActionHolder = holder, recordActionPageKey = "mode", initialDraft = initialDraft,
                                        onDraftChange = { value, _ -> draft = value },
                                        onRecordV2 = { _, unit, weight, value, effort, advanced, _, _, _, result ->
                                            records += Recorded(unit, weight, value, effort, advanced); result(RecordSetResult.Created("mode-exercise_0"))
                                        },
                                        modeResultContent = if (extension) ({ value -> androidx.compose.material3.Text("Resultado ${value.name}", Modifier.testTag("cal3-slot")) }) else null,
                                        clockNowMillis = { now }, effortFireSecondsOverride = 1f)
                                }
                            }
                        }
                        LiveSessionLine(listOf(exercise), exercise.id, liveLineTestSnapshot(4), onOpenRoute = {}, onSelectPage = {}, onSelectExercise = {}, onOpenExerciseOptions = {},
                            onRecord = { holder.actionForPage("mode")?.invoke() }, recordVisible = true, recordEnabled = true,
                            onAddSet = {}, modifier = Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 32.dp))
                    }
                } }
            }
        }
        rule.waitForIdle()
    }
    private fun mark() {
        val m = mode.value
        val planned = when (m) { TrainingMode.RM -> IntensityMode.SOLO_RM; TrainingMode.SOLO_RPE, TrainingMode.TIME -> IntensityMode.RPE; TrainingMode.AMRAP -> IntensityMode.AMRAP; else -> IntensityMode.RIR }
        val scale = buildEffortScale(planned, if (m == TrainingMode.RM) null else planned,
            2.takeIf { planned == IntensityMode.RIR }, 8.0.takeIf { planned == IntensityMode.RPE })
        val index = scale.items.indexOfFirst { !it.isFailure && it.numericValue == if (planned == IntensityMode.RIR) 2.0 else 8.0 }
        rule.onNodeWithTag(EffortSliderTag).performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(index.toFloat()) }
        rule.waitForIdle()
    }
    private fun record() { rule.onNodeWithTag("live-session-record").performClick(); rule.waitForIdle() }
    private fun quantity(value: String) { rule.onNodeWithTag("live-quantity-input").performScrollTo().performTextReplacement(value); rule.waitForIdle() }

    @Test fun everyModeStartsAtTheMinimumWithoutChoosingAnEffort() {
        show(TrainingMode.REPS)
        TrainingMode.entries.forEach { next ->
            rule.runOnIdle { mode.value = next }
            val node = rule.onNodeWithTag(EffortSliderTag).fetchSemanticsNode()
            assertEquals(0f, node.config[SemanticsProperties.ProgressBarRangeInfo].current, 0f)
            assertEquals(listOf("Esfuerzo sin marcar"), node.config[SemanticsProperties.ContentDescription])
            record()
            assertTrue("$next no se registra sin esfuerzo", records.isEmpty())
        }
    }
    @Test fun allModesAndRomKeep312By425At880() = sameFrames(880, 1f)
    @Test fun allModesAndRomKeep312By425At740And130PercentText() = sameFrames(740, 1.3f)
    private fun sameFrames(height: Int, font: Float) {
        show(TrainingMode.REPS, height, font)
        TrainingMode.entries.forEach { next -> for (track in listOf(false, true)) {
            rule.runOnIdle { mode.value = next; rom.value = track }
            val frame = rule.onNodeWithTag("live-card-frame").fetchSemanticsNode().boundsInRoot
            assertEquals("$next/$track ancho", 312f, frame.width / density, .5f)
            assertEquals("$next/$track alto", 425f, frame.height / density, .5f)
            val actions = rule.onNodeWithTag("live-card-actions").fetchSemanticsNode().boundsInRoot
            assertEquals("$next/$track/$font acciones completas", 44f, actions.height / density, .5f)
            assertTrue("$next/$track acciones dentro", actions.left >= frame.left && actions.right <= frame.right && actions.bottom <= frame.bottom + 1f)
            if (track) {
                rule.onNodeWithTag("live-rom-slider").performScrollTo().assertIsDisplayed()
                assertEquals(44f, rule.onNodeWithTag("live-rom-slider").fetchSemanticsNode().boundsInRoot.height / density, .5f)
            }
        } }
    }
    @Test fun repsStillRecordsTheAthletesWheelValueThroughTheLine() {
        show(TrainingMode.REPS); rule.onNodeWithText("8").performClick(); mark(); record()
        val result = records.single(); assertEquals(UnitModeV2.REPS, result.unit); assertEquals(8.0, result.value, 0.0); assertEquals(2.0, result.effort!!, 0.0)
    }
    @Test fun rmRecordsTheRealResultRatherThanItsEstimate() {
        show(TrainingMode.RM); rule.onNodeWithText("9").performClick(); mark(); record()
        val result = records.single(); assertEquals(100.0, result.weight, 0.0); assertEquals(9.0, result.value, 0.0); assertEquals(UnitModeV2.REPS, result.unit)
    }
    @Test fun rmPercentRepsAndWeightStayLinkedWithoutEditingActualReps() {
        show(TrainingMode.RM)
        rule.onNodeWithTag("live-rm-edit").performClick()
        rule.onNodeWithTag("live-rm-percent").performTextReplacement("90")
        rule.onNodeWithTag("live-rm-weight").assertTextEquals("112,5")
        rule.onNodeWithTag("live-rm-reps").performTextReplacement("10")
        rule.onNodeWithTag("live-rm-percent").assertTextEquals("75")
        rule.onNodeWithTag("live-rm-weight").assertTextEquals("93,75")
        rule.onNodeWithTag("live-rm-weight").performTextReplacement("100")
        rule.onNodeWithTag("live-rm-percent").assertTextEquals("80")
        rule.onNodeWithTag("live-rm-reps").assertTextEquals("7")
        rule.onNodeWithTag("live-rm-weight").performTextReplacement("25")
        rule.onNodeWithTag("live-rm-percent").assertTextEquals("20")
        rule.onNodeWithTag("live-rm-weight").assertTextEquals("25")
        rule.onNodeWithTag("live-rm-weight").performTextReplacement("100")
        rule.onNodeWithTag("live-rm-percent").assertTextEquals("80")
        rule.waitForIdle()
        assertEquals(125.0, draft?.rmReference1RM)
        assertEquals(80.0, draft?.rmPercent)
        assertEquals("7", draft?.valueText)
    }
    @Test fun rmReferenceEditStaysInTheDraftAndDoesNotChangeTm() {
        show(TrainingMode.RM, transform = { it.copy(referenceTrainingMax = 120.0) })
        rule.onNodeWithTag("live-rm-edit").performClick()
        rule.onNodeWithTag("live-rm-reference").performScrollTo().performTextReplacement("150")
        rule.waitForIdle()
        assertEquals(150.0, draft?.rmReference1RM)
        assertEquals("120", draft?.weightText)
        rule.onNodeWithText("TM: 120 kg").assertExists()
    }
    @Test fun linkedQuarterKilosAreRecordedWithoutDisplayTruncation() {
        show(TrainingMode.RM)
        rule.onNodeWithTag("live-rm-edit").performClick()
        rule.onNodeWithTag("live-rm-percent").performTextReplacement("75")
        rule.onNodeWithText("Volver").performClick()
        mark(); record()
        assertEquals(93.75, records.single().weight, 0.0)
        assertEquals(7.0, records.single().value, 0.0)
    }
    @Test fun advancedTechniquesReceiveTheActualCommaDecimalLoad() {
        show(TrainingMode.RM, initialDraft = WorkoutSetDraft(weightText = "112,5", valueText = "9"))
        rule.onNodeWithTag("secondary_pill_avanzado").performClick()
        rule.onNode(hasText("112,5") and hasAnyAncestor(hasTestTag(TechniqueChainTag))).assertExists()
    }
    @Test fun aRestoredRmDraftKeepsReferencePercentActualLoadAndActualReps() {
        show(TrainingMode.RM, initialDraft = WorkoutSetDraft(weightText = "112,5", valueText = "9", rmReference1RM = 150.0, rmPercent = 75.0))
        rule.onNodeWithTag("live-rm-edit").performClick()
        rule.onNodeWithTag("live-rm-percent").assertTextEquals("75")
        rule.onNodeWithTag("live-rm-reference").performScrollTo().assertTextEquals("150")
        mark(); record()
        assertEquals(112.5, records.single().weight, 0.0); assertEquals(9.0, records.single().value, 0.0)
    }
    @Test fun aRecordedRmLoadReopensWithItsActualPercentageAndReps() {
        show(TrainingMode.RM, completed = CompletedSet("mode-exercise_0", weight = 112.5, reps = 5))
        rule.onNodeWithTag("live-rm-edit").performClick()
        rule.onNodeWithTag("live-rm-percent").assertTextEquals("90")
        rule.onNodeWithTag("live-rm-weight").assertTextEquals("112,5")
        rule.onNodeWithText("Volver").performClick()
        mark(); record()
        assertEquals(112.5, records.single().weight, 0.0); assertEquals(5.0, records.single().value, 0.0)
    }
    @Test fun tmPrescriptionShowsBothReferencesAndUsesTmForItsLoad() {
        show(TrainingMode.RM, transform = { e -> e.copy(reference1RM = 150.0, referenceTrainingMax = 120.0,
            sets = e.sets.map { it.copy(loadBasis = LoadBasis.PERCENT_TM) }) })
        rule.onNodeWithText("Tu referencia: 150 kg de 1RM").assertExists()
        rule.onNodeWithText("TM: 120 kg").assertExists()
        mark(); record(); assertEquals(96.0, records.single().weight, 0.0)
    }
    @Test fun soloRpeHasNoFixedOrHistoricalLoadSuggestion() {
        show(TrainingMode.SOLO_RPE)
        rule.onNodeWithTag("quick_suggestion_chip_0").assertDoesNotExist()
        assertEquals("", rule.onNodeWithTag("live-actual-load").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        assertEquals("0", draft?.valueText)
        rule.onNodeWithContentDescription("Cambiar tipo de carga").performClick()
        rule.onNodeWithTag("quick_suggestion_menu_1").assertDoesNotExist()
        rule.onNodeWithText("Carga").performClick()
        rule.onNodeWithText("2").performClick()
        mark(); record(); assertTrue(records.isEmpty())
        rule.onNodeWithTag("live-actual-load").performTextReplacement("12.5")
        record()
        val result = records.single(); assertEquals(12.5, result.weight, 0.0); assertEquals(2.0, result.value, 0.0)
        assertEquals(IntensityMode.RPE, result.advanced.actualIntensityMode); assertEquals(8.0, result.effort!!, 0.0)
    }
    @Test fun quickLoadsRemainAvailableInRmAndDistanceWithoutChangingActualQuantities() {
        show(TrainingMode.RM)
        listOf(TrainingMode.RM, TrainingMode.DISTANCE).forEach { next ->
            rule.runOnIdle { mode.value = next }
            if (next == TrainingMode.RM) rule.onNodeWithTag("live-rm-edit").performClick() else quantity("40")
            rule.onNodeWithContentDescription("Cambiar tipo de carga").performScrollTo().performClick()
            rule.onNodeWithTag("quick_suggestion_menu_1").performClick()
            if (next == TrainingMode.RM) {
                rule.onNodeWithTag("live-rm-percent").performScrollTo().assertTextEquals("70,4")
                rule.onNodeWithText("Volver").performClick()
            }
            mark(); record()
            assertEquals(88.0, records.last().weight, 0.0)
            assertEquals(if (next == TrainingMode.RM) 7.0 else 40.0, records.last().value, 0.0)
        }
        assertEquals(2, records.size)
    }
    @Test fun timeEntryRegistersMinutesAndSecondsWithTheTimeUnit() {
        show(TrainingMode.TIME); quantity("1:05"); mark(); record()
        val result = records.single(); assertEquals(UnitModeV2.TIME, result.unit); assertEquals(65.0, result.value, 0.0); assertEquals(65, result.advanced.timerElapsedSeconds)
    }
    @Test fun invalidMinuteSecondEntriesNeverFallBackToTheGoal() {
        show(TrainingMode.TIME); mark()
        listOf("0:00", "1:60", "-1:05", "1:05:00", "").forEach { quantity(it); record(); assertTrue("$it", records.isEmpty()) }
        rule.onNodeWithText("Introduce un tiempo positivo en min:s (segundos de 0 a 59).").assertExists()
    }
    @Test fun clockStartsStopsAndKeepsItsActualElapsedResult() {
        show(TrainingMode.TIME)
        rule.onNodeWithContentDescription("Iniciar").performClick()
        rule.runOnIdle { now += 12_000 }
        rule.onNodeWithContentDescription("Detener").performClick()
        rule.onNodeWithTag("live-quantity-input").assertTextEquals("0:12")
        assertTrue(records.isEmpty())
        mark(); record(); assertEquals(12.0, records.single().value, 0.0)
    }
    @Test fun completingTheClockWaitsForTheLineConfirmation() {
        show(TrainingMode.TIME)
        rule.onNodeWithContentDescription("Iniciar").performClick()
        rule.onNodeWithTag("live-quantity-input").assertIsNotEnabled()
        rule.runOnIdle { now += 60_000 }
        rule.mainClock.advanceTimeBy(200)
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("Detener").fetchSemanticsNodes().isEmpty() }
        assertTrue(records.isEmpty())
        rule.onNodeWithTag("live-quantity-input").assertTextEquals("1:00")
        rule.onNodeWithTag("live-quantity-input").assertIsEnabled()
        rule.onNodeWithText("Objetivo cumplido · Confirma con ✓ para registrar").assertIsDisplayed()
        mark(); record(); assertEquals(60.0, records.single().value, 0.0)
    }
    @Test fun correctingAManualTimeBelowTheGoalClearsTheCompletedStatus() {
        show(TrainingMode.TIME); quantity("1:00")
        rule.onNodeWithText("Objetivo cumplido · Confirma con ✓ para registrar").assertIsDisplayed()
        quantity("0:15")
        rule.onNodeWithText("Objetivo cumplido · Confirma con ✓ para registrar").assertDoesNotExist()
        mark(); record(); assertEquals(15.0, records.single().value, 0.0)
    }
    @Test fun manualTimeAfterStoppingOverridesTheClockSnapshot() {
        show(TrainingMode.TIME); rule.onNodeWithContentDescription("Iniciar").performClick()
        rule.runOnIdle { now += 12_000 }; rule.onNodeWithContentDescription("Detener").performClick()
        quantity("0:15"); mark(); record()
        assertEquals(15.0, records.single().value, 0.0); assertEquals(15, records.single().advanced.timerElapsedSeconds)
    }
    @Test fun distancePreservesDecimalMetersProgressAndUsedLoad() {
        show(TrainingMode.DISTANCE); quantity("12,25")
        rule.onNodeWithContentDescription("Añadir m").performClick()
        rule.onNodeWithTag("live-quantity-input").assertTextEquals("13,25")
        assertEquals(.33125f, rule.onNodeWithTag("live-quantity-progress").fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current, .0001f)
        mark(); record()
        val result = records.single(); assertEquals(UnitModeV2.DISTANCE, result.unit); assertEquals(13.25, result.value, 0.0); assertEquals(24.0, result.weight, 0.0)
    }
    @Test fun aRecordedDecimalDistanceReopensWithoutLosingItsFraction() {
        show(TrainingMode.DISTANCE, completed = CompletedSet("mode-exercise_0", weight = 24.0, reps = 12, distanceKm = .01225))
        rule.onNodeWithTag("live-quantity-input").assertTextEquals("12,25")
        mark(); record(); assertEquals(12.25, records.single().value, 0.0)
    }
    @Test fun customPreservesItsExerciseUnitAndActualQuantity() {
        show(TrainingMode.CUSTOM); quantity("31.5"); mark(); record()
        rule.onNodeWithText("Unidad del ejercicio: brazadas").assertExists()
        val result = records.single(); assertEquals(UnitModeV2.CUSTOM, result.unit); assertEquals(31.5, result.value, 0.0)
    }
    @Test fun aLongCustomUnitKeepsAnEditableQuantityAt130Percent() {
        val unit = "brazadas completas con ambos brazos"
        show(TrainingMode.CUSTOM, height = 740, font = 1.3f, transform = { it.copy(customUnit = unit) })
        quantity("31,25")
        assertTrue(rule.onNodeWithTag("live-quantity-input").fetchSemanticsNode().boundsInRoot.width / density >= 100f)
        rule.onNodeWithText(unit).assertExists()
        mark(); record()
        assertEquals(UnitModeV2.CUSTOM, records.single().unit); assertEquals(31.25, records.single().value, 0.0)
    }
    @Test fun nonpositiveAndNonfiniteQuantitiesAreRejected() {
        show(TrainingMode.DISTANCE); mark()
        listOf("0", "-2", "NaN", "Infinity", "").forEach { quantity(it); record(); assertTrue(it, records.isEmpty()) }
    }
    @Test fun anExplicitInvalidLoadDoesNotGetReplacedByThePlan() {
        show(TrainingMode.DISTANCE); quantity("40"); mark()
        rule.onNodeWithTag("live-actual-load").performTextReplacement("-1"); record(); assertTrue(records.isEmpty())
        rule.onNodeWithTag("live-actual-load").performTextReplacement("NaN"); record(); assertTrue(records.isEmpty())
    }
    @Test fun romRecordsItsChosenValueInsideTheSameCard() {
        rom.value = true; show(TrainingMode.CUSTOM); quantity("30")
        rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, androidx.compose.ui.semantics.ProgressBarRangeInfo(100f, 10f..100f))).performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(75f) }
        mark(); record(); assertEquals(75, records.single().advanced.rom)
    }
    @Test fun reducedMotionSuppressesTheFailureHaloEvenWithAFrozenReviewFrame() {
        show(TrainingMode.REPS)
        val info = rule.onNodeWithTag(EffortSliderTag).fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        rule.onNodeWithTag(EffortSliderTag).performSemanticsAction(SemanticsActions.SetProgress) { it(info.range.endInclusive) }
        rule.onNodeWithTag("effort-fire-halo", useUnmergedTree = true).assertDoesNotExist()
        record(); assertTrue(records.single().advanced.reachedFailure)
    }
    @Test fun cal3HasAResultSlotInsideTheAmrapCardWithoutChangingItsFrame() {
        show(TrainingMode.AMRAP, extension = true)
        rule.onNodeWithTag("cal3-slot").performScrollTo().assertIsDisplayed()
        assertEquals(425f, rule.onNodeWithTag("live-card-frame").fetchSemanticsNode().boundsInRoot.height / density, .5f)
        mark(); record(); assertEquals(UnitModeV2.REPS, records.single().unit)
    }
}

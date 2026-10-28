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
        transform: (Exercise) -> Exercise = { it }, extension: Boolean = false, completed: CompletedSet? = null, ghost: CompletedSet? = null) {
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
                                    SetInputCardV2(exercise, 0, set, ghost,
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
        rule.onNodeWithTag(EffortSliderTag).scrollToIfScrollable().performSemanticsAction(SemanticsActions.SetProgress) { it(index.toFloat()) }
        rule.waitForIdle()
    }
    private fun record() { rule.onNodeWithTag("live-session-record").performClick(); rule.waitForIdle() }
    private fun quantity(value: String) { quantityField().scrollToIfScrollable().performTextReplacement(value); rule.waitForIdle() }
    /** Refinamiento 02: la cantidad es el BasicTextField «Cantidad registrada» de la fila `live-card-quantity-input`. */
    private fun quantityField() = rule.onNodeWithContentDescription("Cantidad registrada")
    /** Abre la hoja «Referencia de RM» desde el icono de historial de la carga. */
    private fun openRmSheet() { rule.onNodeWithTag("live-card-reference-history").performClick(); rule.waitForIdle() }
    /** Abre la hoja de RM y entra en su edición (`live-rm-edit`). */
    private fun openRmEditor() { openRmSheet(); rule.onNodeWithTag("live-rm-edit").performClick(); rule.waitForIdle() }
    /** Cierra la hoja abierta con su pie «Listo». */
    private fun closeSheet() {
        // Elegir una carga rápida puede cerrar ya la hoja: solo se pulsa «Listo» si sigue abierta.
        if (rule.onAllNodesWithText("Listo").fetchSemanticsNodes().isNotEmpty()) rule.onAllNodesWithText("Listo")[0].performClick()
        rule.waitForIdle()
    }
    /** «Detalle del registro»: parciales, ayuda y ROM se abren desde la fila `live-card-detail`. */
    private fun openDetail() { rule.onNodeWithTag("live-card-detail").performClick(); rule.waitForIdle() }

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
    @Test fun allModesAndRomKeep312By394At880() = sameFrames(880, 1f)
    @Test fun allModesAndRomKeep312By394At740And130PercentText() = sameFrames(740, 1.3f)
    private fun sameFrames(height: Int, font: Float) {
        show(TrainingMode.REPS, height, font)
        TrainingMode.entries.forEach { next -> for (track in listOf(false, true)) {
            rule.runOnIdle { mode.value = next; rom.value = track }
            val frame = rule.onNodeWithTag("live-card-frame").fetchSemanticsNode().boundsInRoot
            assertEquals("$next/$track ancho", 312f, frame.width / density, .5f)
            assertEquals("$next/$track alto", 394f, frame.height / density, .5f)
            val actions = rule.onNodeWithTag("live-card-actions").fetchSemanticsNode().boundsInRoot
            assertEquals("$next/$track/$font acciones completas", 44f, actions.height / density, .5f)
            assertTrue("$next/$track acciones dentro", actions.left >= frame.left && actions.right <= frame.right && actions.bottom <= frame.bottom + 1f)
            if (track) {
                // Refinamiento 02: el ROM se resume en «Parciales, ayuda y ROM» (fila `live-card-detail`) y su campo vive en «Detalle del registro».
                rule.onNodeWithTag("live-card-detail").assert(hasText("ROM", substring = true)).assertIsDisplayed()
                openDetail()
                rule.onNodeWithTag("live-set-detail-rom").scrollToIfScrollable().assertIsDisplayed()
                assertTrue("$next/$track campo de ROM de 44 dp", rule.onNodeWithTag("live-set-detail-rom").fetchSemanticsNode().boundsInRoot.height / density >= 43.5f)
                closeSheet()
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
        openRmEditor()
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
        openRmEditor()
        rule.onNodeWithTag("live-rm-reference").scrollToIfScrollable().performTextReplacement("150")
        rule.waitForIdle()
        assertEquals(150.0, draft?.rmReference1RM)
        assertEquals("120", draft?.weightText)
        rule.onNodeWithText("TM: 120\u00A0kg").assertExists()
    }
    @Test fun linkedQuarterKilosAreRecordedWithoutDisplayTruncation() {
        show(TrainingMode.RM)
        openRmEditor()
        rule.onNodeWithTag("live-rm-percent").performTextReplacement("75")
        rule.onNodeWithText("Volver").performClick()
        closeSheet()
        mark(); record()
        assertEquals(93.75, records.single().weight, 0.0)
        assertEquals(7.0, records.single().value, 0.0)
    }
    @Test fun advancedTechniquesReceiveTheActualCommaDecimalLoad() {
        show(TrainingMode.RM, initialDraft = WorkoutSetDraft(weightText = "112,5", valueText = "9"))
        rule.onNodeWithTag("secondary_pill_avanzado").performClick()
        // Refinamiento 02: la carga principal va en el conmutador «PRINCIPAL 112,5 × 9» (`TechniqueLinkTag`); la cadena solo muestra «N tramos».
        rule.onNode(hasText("112,5", substring = true) and hasAnyAncestor(hasTestTag(TechniqueLinkTag))).assertExists()
    }
    @Test fun aRestoredRmDraftKeepsReferencePercentActualLoadAndActualReps() {
        show(TrainingMode.RM, initialDraft = WorkoutSetDraft(weightText = "112,5", valueText = "9", rmReference1RM = 150.0, rmPercent = 75.0))
        openRmEditor()
        rule.onNodeWithTag("live-rm-percent").assertTextEquals("75")
        rule.onNodeWithTag("live-rm-reference").scrollToIfScrollable().assertTextEquals("150")
        closeSheet()
        mark(); record()
        assertEquals(112.5, records.single().weight, 0.0); assertEquals(9.0, records.single().value, 0.0)
    }
    @Test fun aRecordedRmLoadReopensWithItsActualPercentageAndReps() {
        show(TrainingMode.RM, completed = CompletedSet("mode-exercise_0", weight = 112.5, reps = 5))
        openRmEditor()
        rule.onNodeWithTag("live-rm-percent").assertTextEquals("90")
        rule.onNodeWithTag("live-rm-weight").assertTextEquals("112,5")
        rule.onNodeWithText("Volver").performClick()
        closeSheet()
        mark(); record()
        assertEquals(112.5, records.single().weight, 0.0); assertEquals(5.0, records.single().value, 0.0)
    }
    @Test fun tmPrescriptionShowsBothReferencesAndUsesTmForItsLoad() {
        show(TrainingMode.RM, transform = { e -> e.copy(reference1RM = 150.0, referenceTrainingMax = 120.0,
            sets = e.sets.map { it.copy(loadBasis = LoadBasis.PERCENT_TM) }) })
        openRmSheet()
        rule.onNodeWithText("Tu referencia: 150 kg de 1RM").assertExists()
        rule.onNodeWithText("80 % de tu TM (120) → 96\u00A0kg").assertExists()
        closeSheet()
        mark(); record(); assertEquals(96.0, records.single().weight, 0.0)
    }
    @Test fun soloRpeHasNoFixedOrHistoricalLoadSuggestion() {
        show(TrainingMode.SOLO_RPE)
        rule.onNodeWithTag("quick_suggestion_chip_0").assertDoesNotExist()
        assertEquals("", rule.onNodeWithContentDescription("Carga en kg").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        assertEquals("10", draft?.valueText)
        rule.onNodeWithText("10").assertIsDisplayed()
        rule.onNodeWithContentDescription("Cambiar tipo de carga").performClick()
        rule.onNodeWithTag("quick_suggestion_menu-0").assertDoesNotExist()
        rule.onNodeWithTag("live-load-mode-0").performClick()
        rule.onNodeWithText("Listo").performClick()
        rule.onNodeWithText("11").performClick()
        mark(); record(); assertTrue(records.isEmpty())
        rule.onNodeWithContentDescription("Carga en kg").performTextReplacement("12.5")
        record()
        val result = records.single(); assertEquals(12.5, result.weight, 0.0); assertEquals(11.0, result.value, 0.0)
        assertEquals(IntensityMode.RPE, result.advanced.actualIntensityMode); assertEquals(8.0, result.effort!!, 0.0)
    }
    @Test fun soloRpeUsesThePreviousRepsWhenNoPositiveGoalExists() {
        show(TrainingMode.SOLO_RPE, ghost = CompletedSet("previous", weight = 25.0, reps = 6),
            transform = { e -> e.copy(sets = e.sets.map { it.copy(targetReps = 0) }) })
        assertEquals("6", draft?.valueText)
        assertEquals("", draft?.weightText)
        rule.onNodeWithText("6").assertIsDisplayed()
    }
    @Test fun soloRpeFallsBackToEightRepsWithoutChoosingAnEffortOrLoad() {
        show(TrainingMode.SOLO_RPE, transform = { e -> e.copy(sets = e.sets.map { it.copy(targetReps = null) }) })
        assertEquals("8", draft?.valueText)
        assertEquals("", draft?.weightText)
        record()
        assertTrue(records.isEmpty())
    }
    @Test fun soloRpeKeepsRestoredActualRepsOverTheGoal() {
        show(TrainingMode.SOLO_RPE, initialDraft = WorkoutSetDraft(valueText = "12", weightText = "15", isDirty = true))
        assertEquals("12", draft?.valueText)
        rule.onNodeWithText("12").assertIsDisplayed()
    }
    @Test fun soloRpeReopensTheRecordedRepsOverTheGoal() {
        show(TrainingMode.SOLO_RPE, completed = CompletedSet("mode-exercise_0", weight = 15.0, reps = 5))
        assertEquals("5", draft?.valueText)
        rule.onNodeWithText("5").assertIsDisplayed()
    }
    @Test fun quickLoadsRemainAvailableInRmAndDistanceWithoutChangingActualQuantities() {
        show(TrainingMode.RM)
        listOf(TrainingMode.RM, TrainingMode.DISTANCE).forEach { next ->
            rule.runOnIdle { mode.value = next }
            if (next == TrainingMode.DISTANCE) quantity("40")
            rule.onNodeWithContentDescription("Cambiar tipo de carga").performClick()
            rule.onNodeWithTag("quick_suggestion_menu-1").performClick()
            closeSheet()
            if (next == TrainingMode.RM) {
                openRmEditor()
                rule.onNodeWithTag("live-rm-percent").scrollToIfScrollable().assertTextEquals("70,4")
                rule.onNodeWithText("Volver").performClick()
                closeSheet()
            }
            mark(); record()
            assertEquals(88.0, records.last().weight, 0.0)
            assertEquals(if (next == TrainingMode.RM) 7.0 else 40.0, records.last().value, 0.0)
        }
        assertEquals(2, records.size)
    }
    @Test fun timeEntryRegistersMinutesAndSecondsWithTheTimeUnit() {
        show(TrainingMode.TIME); quantity("65"); mark(); record()
        val result = records.single(); assertEquals(UnitModeV2.TIME, result.unit); assertEquals(65.0, result.value, 0.0); assertEquals(65, result.advanced.timerElapsedSeconds)
    }
    @Test fun invalidMinuteSecondEntriesNeverFallBackToTheGoal() {
        show(TrainingMode.TIME); mark()
        listOf("0", "").forEach { quantity(it); record(); assertTrue("$it", records.isEmpty()) }
        rule.onNodeWithText("Introduce un tiempo positivo en min:s (segundos de 0 a 59).").assertExists()
    }
    @Test fun clockStartsStopsAndKeepsItsActualElapsedResult() {
        show(TrainingMode.TIME)
        rule.onNodeWithTag("live-card-set-clock").performClick()
        rule.runOnIdle { now += 12_000 }
        rule.onNodeWithTag("live-card-set-clock").performClick()
        quantityField().assertTextEquals("12")
        assertTrue(records.isEmpty())
        mark(); record(); assertEquals(12.0, records.single().value, 0.0)
    }
    @Test fun completingTheClockWaitsForTheLineConfirmation() {
        show(TrainingMode.TIME)
        rule.onNodeWithTag("live-card-set-clock").performClick()
        quantityField().assertIsNotEnabled()
        rule.runOnIdle { now += 60_000 }
        rule.mainClock.advanceTimeBy(200)
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("Pausar el cronómetro de la serie").fetchSemanticsNodes().isEmpty() }
        assertTrue(records.isEmpty())
        quantityField().assertTextEquals("60")
        quantityField().assertIsEnabled()
        // Refinamiento 02: el estado «Objetivo cumplido» ya no se pinta en el frente; queda el reloj parado en 1:00.
        rule.onNodeWithTag("live-card-set-clock").assertTextContains("1:00")
        mark(); record(); assertEquals(60.0, records.single().value, 0.0)
    }
    @Test fun correctingAManualTimeBelowTheGoalClearsTheCompletedStatus() {
        show(TrainingMode.TIME); quantity("60")
        quantityField().assertTextEquals("60")
        quantity("15")
        mark(); record(); assertEquals(15.0, records.single().value, 0.0)
    }
    @Test fun manualTimeAfterStoppingOverridesTheClockSnapshot() {
        show(TrainingMode.TIME); rule.onNodeWithTag("live-card-set-clock").performClick()
        rule.runOnIdle { now += 12_000 }; rule.onNodeWithTag("live-card-set-clock").performClick()
        quantity("15"); mark(); record()
        assertEquals(15.0, records.single().value, 0.0); assertEquals(15, records.single().advanced.timerElapsedSeconds)
    }
    @Test fun distancePreservesDecimalMetersProgressAndUsedLoad() {
        show(TrainingMode.DISTANCE); quantity("12,25")
        rule.onNodeWithContentDescription("Aumentar cantidad").performClick()
        quantityField().assertTextEquals("13,25")
        // Refinamiento 02: la barra de progreso de la cantidad era de LiveModeSetContent y ya no se pinta en el frente.
        rule.onNodeWithTag("live-quantity-progress").assertDoesNotExist()
        mark(); record()
        val result = records.single(); assertEquals(UnitModeV2.DISTANCE, result.unit); assertEquals(13.25, result.value, 0.0); assertEquals(24.0, result.weight, 0.0)
    }
    @Test fun aRecordedDecimalDistanceReopensWithoutLosingItsFraction() {
        show(TrainingMode.DISTANCE, completed = CompletedSet("mode-exercise_0", weight = 24.0, reps = 12, distanceKm = .01225))
        quantityField().assertTextEquals("12,25")
        mark(); record(); assertEquals(12.25, records.single().value, 0.0)
    }
    @Test fun customPreservesItsExerciseUnitAndActualQuantity() {
        show(TrainingMode.CUSTOM); quantity("31.5"); mark(); record()
        // Refinamiento 02: la unidad del ejercicio se muestra junto al campo de cantidad (la línea de estado ya no existe en el frente).
        rule.onNodeWithText("brazadas").assertExists()
        val result = records.single(); assertEquals(UnitModeV2.CUSTOM, result.unit); assertEquals(31.5, result.value, 0.0)
    }
    @Test fun aLongCustomUnitKeepsAnEditableQuantityAt130Percent() {
        val unit = "brazadas completas con ambos brazos"
        show(TrainingMode.CUSTOM, height = 740, font = 1.3f, transform = { it.copy(customUnit = unit) })
        quantity("31,25")
        assertTrue(quantityField().fetchSemanticsNode().boundsInRoot.width / density >= 100f)
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
        rule.onNodeWithContentDescription("Carga en kg").performTextReplacement("-1"); record(); assertTrue(records.isEmpty())
        rule.onNodeWithContentDescription("Carga en kg").performTextReplacement("NaN"); record(); assertTrue(records.isEmpty())
    }
    @Test fun romRecordsItsChosenValueInsideTheSameCard() {
        rom.value = true; show(TrainingMode.CUSTOM); quantity("30")
        // Refinamiento 02: el ROM se edita en «Detalle del registro» (campo `live-set-detail-rom`), ya no en un deslizador de la tarjeta.
        openDetail()
        rule.onNodeWithTag("live-set-detail-rom").scrollToIfScrollable().performTextReplacement("75")
        closeSheet()
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
        rule.onNodeWithTag("cal3-slot").scrollToIfScrollable().assertIsDisplayed()
        assertEquals(394f, rule.onNodeWithTag("live-card-frame").fetchSemanticsNode().boundsInRoot.height / density, .5f)
        mark(); record(); assertEquals(UnitModeV2.REPS, records.single().unit)
    }
}

/** La tarjeta del Refinamiento 02 no tiene scroll interno: solo desplaza si el nodo está dentro de una lista que lo tenga. */
private fun androidx.compose.ui.test.SemanticsNodeInteraction.scrollToIfScrollable(): androidx.compose.ui.test.SemanticsNodeInteraction =
    try { performScrollTo() } catch (_: AssertionError) { this }

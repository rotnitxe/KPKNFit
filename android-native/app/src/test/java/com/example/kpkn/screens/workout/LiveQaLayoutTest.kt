package com.example.kpkn.screens.workout

import android.app.Application
import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.sessionassistant.*
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.workout.components.*
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.components.rings.MuscleBodyAdjust
import com.example.kpkn.ui.theme.KPKNTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Regressions from the supplied Z Flip captures, without a device or microphone. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h880dp-xxhdpi")
@OptIn(ExperimentalTestApi::class)
class LiveQaLayoutTest {
    @get:Rule val rule = createComposeRule()
    private val peach = Color(0xFFF49A6E)
    private var density = 1f
    private var hostView: View? = null

    private fun content(scale: Float = 1f, body: @Composable () -> Unit) {
        rule.setContent {
            hostView = LocalView.current
            density = LocalDensity.current.density
            CompositionLocalProvider(
                LocalDensity provides Density(density, scale),
                LocalWizardReducedMotion provides true,
                LocalKpknOverlayHost provides null,
                LocalRestClockFrozen provides 1.7f,
            ) {
                KPKNTheme { MaterialTheme {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) { body() }
                } }
            }
        }
    }

    private fun save(tag: String, name: String) {
        val bitmap = capture(tag)
        val folder = File("build/lqa1/png").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    /** PixelCopy does not complete in Robolectric; use the repository's native view-draw path. */
    private fun capture(tag: String): Bitmap {
        rule.waitForIdle()
        val bounds = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val view = checkNotNull(hostView)
        val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread {
            val canvas = android.graphics.Canvas(full)
            canvas.drawColor(android.graphics.Color.BLACK)
            view.draw(canvas)
        }
        val left = bounds.left.toInt().coerceIn(0, full.width - 1)
        val top = bounds.top.toInt().coerceIn(0, full.height - 1)
        val bitmap = Bitmap.createBitmap(full, left, top, bounds.width.toInt().coerceIn(1, full.width - left),
            bounds.height.toInt().coerceIn(1, full.height - top))
        if (bitmap !== full) full.recycle()
        return bitmap
    }

    @Test fun focusAndProgrammaticScrollCannotDragTheSheetOffscreen() {
        val dispatcher = NestedScrollDispatcher()
        content {
            Box(Modifier.size(360.dp, 400.dp)) {
                HeaderSheet({}) { close ->
                    HeaderSheetFrame("Etiqueta", footer = { HeaderSheetAction("Listo", close) }) {
                        Box(Modifier.fillMaxWidth().height(80.dp)
                            .nestedScroll(remember { object : NestedScrollConnection {} }, dispatcher))
                    }
                }
            }
        }
        val body = rule.onNodeWithTag("live-header-sheet-body", true).assertIsDisplayed()
        val before = body.fetchSemanticsNode().boundsInRoot
        rule.runOnIdle {
            dispatcher.dispatchPostScroll(Offset.Zero, Offset(0f, 2000f), NestedScrollSource.SideEffect)
        }
        val after = body.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(before.top, after.top, 0f)
        assertEquals(before.bottom, after.bottom, 0f)
    }

    @Test fun advancedDropAt100() = advanced("Drop-set", 1f)
    @Test fun advancedDropAt130() = advanced("Drop-set", 1.3f)
    @Test fun advancedRestPauseAt100() = advanced("Rest-pause", 1f)
    @Test fun advancedRestPauseAt130() = advanced("Rest-pause", 1.3f)

    private fun advanced(technique: String, scale: Float) {
        var failed = false
        content(scale) {
            Box(Modifier.size(272.dp, 388.dp).background(Color.Black).testTag("qa-card")) {
                SetCardTechniqueBack(
                    currentSet = ExerciseSet("qa-set", weight = 60.0, targetReps = 8),
                    coverWeightLabel = "60 kg", coverValueLabel = "8", coverIntensityLabel = null,
                    mainWeight = 60.0, mainReps = 8, sessionAccentColor = peach,
                    isFailedSet = false, onFailedSetChange = { failed = it }, onFlipBack = {},
                    onCompleteTechniques = { _, _ -> }, setNumber = 1,
                )
            }
        }
        val initial = rule.onNodeWithTag("qa-card").fetchSemanticsNode().boundsInRoot
        rule.onNodeWithText(technique).performClick()
        if (technique == "Rest-pause") {
            rule.onNodeWithContentDescription("Más eslabones").assertIsDisplayed()
        }
        rule.onNodeWithTag("technique-explanation", true).performScrollTo().assertIsDisplayed()
        val explanation = rule.onNodeWithTag("technique-explanation", true).getUnclippedBoundsInRoot()
        val body = rule.onNodeWithTag("technique-body", true).getUnclippedBoundsInRoot()
        assertTrue("Explanation $explanation fits entirely in viewport $body", explanation.top >= body.top - 1.dp && explanation.bottom <= body.bottom + 1.dp)
        save("qa-card", "advanced-${technique}-${(scale * 100).toInt()}")
        rule.onNodeWithText("Error de ejecución").performScrollTo().assertIsDisplayed().performClick()
        assertTrue(failed)
        val after = rule.onNodeWithTag("qa-card").fetchSemanticsNode().boundsInRoot
        assertEquals(initial.height, after.height, 0f)
        assertEquals(initial.width, after.width, 0f)
    }

    @Test fun compactManagerAt100() = manager(1f)
    @Test fun compactManagerAt130() = manager(1.3f)

    private fun manager(scale: Float) {
        var renamed: String? = null
        var saved: TagSetupInput? = null
        var resets = 0
        content(scale) {
            Box(Modifier.size(360.dp, 400.dp).background(Color.Black).testTag("qa-manager")) {
                WorkoutTagManagerModal(
                    tag = WorkoutTag("qa-tag", "Máquina A", "exercise"), exerciseId = "exercise",
                    onRename = { renamed = it; RenameTagResult.Success }, onDelete = {}, onResetHistory = { resets++ },
                    onAddSubTag = { _, _ -> }, onRemoveSubTag = {}, onToggleSubTagActive = {}, activeSubTagIds = emptyList(),
                    onDismiss = {}, onSaveSetup = { saved = it },
                )
            }
        }
        rule.onNodeWithContentDescription("Nombre", useUnmergedTree = true).performScrollTo().performTextReplacement("Máquina B")
        tap("Guardar nombre")
        tap("Set-up · Mostrar")
        rule.onNodeWithContentDescription("Carga base (kg)", useUnmergedTree = true).performScrollTo().performTextReplacement("25,5")
        tap("Guardar set-up")
        tap("Reiniciar historial")
        assertEquals(0, resets)
        rule.onNodeWithText("Los registros de sesión no se borran.", substring = true, useUnmergedTree = true).assertExists()
        tap("Reiniciar")
        assertEquals("Máquina B", renamed)
        assertEquals(25.5, saved!!.baseLoadKg!!, 0.0)
        assertEquals(1, resets)
        rule.onNodeWithTag("live-header-sheet-body", true).assertIsDisplayed()
        save("qa-manager", "manager-${(scale * 100).toInt()}")
    }

    private fun tap(label: String) {
        val node = rule.onNodeWithText(label, useUnmergedTree = true)
        if (hasAnyAncestor(hasScrollAction()).matches(node.fetchSemanticsNode())) node.performScrollTo()
        node.assertIsDisplayed().performClick()
    }

    @Test fun compactUltraFastAt100() = ultraFast(1f)
    @Test fun compactUltraFastAt130() = ultraFast(1.3f)

    private fun ultraFast(scale: Float) {
        var submitted: Map<String, Int>? = null
        var override by mutableStateOf(false)
        content(scale) {
            Box(Modifier.size(360.dp, 400.dp).background(Color.Black)) {
                val preview = UltraFastPreview(1800, 1200, 600, listOf(
                    UltraFastExerciseChange("curl", "Curl", 3, 2, "NORMAL", "NORMAL", UltraFastReason.COMPOUND_REDUCED,
                        wasDensified = false, wasReduced = true),
                    UltraFastExerciseChange("press", "Press", 3, if (override) 2 else 3, "NORMAL", if (override) "DROPSET" else "NORMAL",
                        if (override) UltraFastReason.MANUAL_OVERRIDE_ALLOWED else UltraFastReason.PROTECTED_BASIC, override, false),
                ), listOf(UltraFastSupersetChange("curl", "press", "Curl", "Press", "Polea")))
                UltraFastPreviewSheet(preview, 600, emptyList(), mapOf("press" to override), { override = !override }, { submitted = it }, {})
            }
        }
        repeat(4) { rule.onNodeWithTag("ultra-plus-curl", true).performScrollTo().assertIsDisplayed().performClick() }
        rule.onNodeWithTag("ultra-plus-curl", true).assertIsNotEnabled()
        repeat(5) { rule.onNodeWithTag("ultra-minus-curl", true).performScrollTo().assertIsDisplayed().performClick() }
        rule.onNodeWithTag("ultra-minus-curl", true).assertIsNotEnabled()
        tap("Mantener protegido")
        rule.onNodeWithText("Mayor riesgo", useUnmergedTree = true).assertExists()
        tap("Permitir técnica intensa")
        rule.onNodeWithText("Curl + Press", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Aplicar").assertIsDisplayed().performClick()
        assertFalse(override)
        assertEquals(mapOf("curl" to 1), submitted)
    }

    @Test fun cockpitMilestonesStartBelowTabs() = cockpit("Hitos", "Hitos de la sesión")
    @Test fun cockpitPhotosStartBelowTabs() = cockpit("Fotos", "Cámara")
    @Test fun cockpitNotesStartBelowTabs() = cockpit("Notas", "Notas de sesión")

    private fun cockpit(tab: String, heading: String) {
        content {
            Box(Modifier.size(360.dp, 720.dp).background(Color.Black).testTag("qa-cockpit")) {
                WorkoutSessionCockpit(
                    exercises = emptyList(), completedSets = emptyMap(), milestones = emptyList(), sessionProgressLabel = "0 / 1",
                    liveEnergySummary = SessionEnergySummary(), sessionNotes = "", sessionPhotos = emptyList(), sessionChecklist = emptyList(),
                    onSessionNotesChange = {}, onAddSessionPhoto = {}, onRemoveSessionPhoto = {},
                    onAddChecklistItem = {}, onToggleChecklistItem = {}, onRemoveChecklistItem = {}, sessionAccentColor = peach,
                )
            }
        }
        rule.onNode(hasText(tab) and hasClickAction()).performScrollTo().performClick()
        rule.waitForIdle()
        val tabs = rule.onNodeWithTag("cockpit-tabs", true).fetchSemanticsNode().boundsInRoot
        val title = rule.onNodeWithText(heading, useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("Content starts directly below tabs", title.top >= tabs.bottom && title.top - tabs.bottom < 32 * density)
        save("qa-cockpit", "cockpit-$tab")
    }

    @Test fun singletonLiftAt100() = singletonLift(1f)
    @Test fun singletonLiftAt130() = singletonLift(1.3f)

    private fun singletonLift(scale: Float) {
        val lift = FinishLiftProgress("squat", "Sentadilla", CompletedSet("set", weight = 80.0, reps = 6), 1, null, emptyList())
        content(scale) {
            Column(Modifier.size(360.dp, 640.dp).background(Color.Black).testTag("qa-lifts"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                FinishLiftsPage(listOf(lift), "Pierna", emptyList(), 480.0, "Alta", "Buena")
            }
        }
        rule.onAllNodesWithText("Sin registro previo").assertCountEquals(1)
        val layouts = mutableListOf<TextLayoutResult>()
        val valueNode = rule.onNodeWithText(finishSetLabel(lift.best))
        valueNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals("The set and its repetition count stay on one line", 1, layouts.single().lineCount)
        assertFalse("The set label has no ellipsis", layouts.single().isLineEllipsized(0))
        assertTrue("The whole set label fits", layouts.single().getLineRight(0) - layouts.single().getLineLeft(0) <= valueNode.fetchSemanticsNode().boundsInRoot.width + 1f)
        val hero = rule.onNodeWithTag("finish-lift-highlight", true).fetchSemanticsNode().boundsInRoot
        val tonnage = rule.onNodeWithTag("finish-tonnage", true).fetchSemanticsNode().boundsInRoot
        assertTrue("No empty list between the only lift and tonnage", tonnage.top - hero.bottom <= 15 * density)
        val bitmap = capture("finish-tonnage-bars")
        val colored = (0 until bitmap.width).filter { x ->
            val pixel = bitmap.getPixel(x, bitmap.height / 2)
            android.graphics.Color.red(pixel) > 220 && android.graphics.Color.green(pixel) in 120..180 && android.graphics.Color.blue(pixel) in 70..140
        }
        assertTrue("Single tonnage datum remains visible", colored.isNotEmpty())
        assertTrue("Single bar has a bounded width", colored.last() - colored.first() <= 48 * density)
        bitmap.recycle()
        save("qa-lifts", "singleton-lift-${(scale * 100).toInt()}")
    }

    @Test fun aShortLiftListDoesNotLeaveAGapAboveTonnage() {
        val lifts = listOf(
            FinishLiftProgress("squat", "Sentadilla", CompletedSet("sq", weight = 80.0, reps = 6), 1, null, emptyList()),
            FinishLiftProgress("row", "Remo", CompletedSet("row", weight = 50.0, reps = 8), 1, null, emptyList()),
        )
        content {
            Column(Modifier.size(360.dp, 640.dp).background(Color.Black).testTag("qa-lifts"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                FinishLiftsPage(lifts, "Pierna", emptyList(), 880.0, "Alta", "Buena")
            }
        }
        val row = rule.onNodeWithText("Remo").fetchSemanticsNode().boundsInRoot
        val tonnage = rule.onNodeWithTag("finish-tonnage", true).fetchSemanticsNode().boundsInRoot
        assertTrue("Short list wraps its actual rows", tonnage.top - row.bottom < 44 * density)
        save("qa-lifts", "two-lifts")
    }

    @Test fun workingRelatorAt100() = workingRelator(1f)
    @Test fun workingRelatorAt130() = workingRelator(1.3f)

    @Test fun muscleMapExplainsTheApprovedRecoveryColors() {
        content {
            Column(Modifier.size(360.dp, 840.dp).background(Color.Black).verticalScroll(rememberScrollState()).testTag("qa-muscles")) {
                MuscleBodyAdjust(values = mapOf("Pectorales" to 92, "Cuádriceps" to 54, "Core" to 70), before = emptyMap(),
                    onValueChange = { _, _ -> }, initialSelected = "Pectorales")
            }
        }
        listOf("Recuperado · 80–100 %", "Algo cargado · 60–79 %", "Cargado · 0–59 %", "Borde grueso: músculo elegido").forEach {
            rule.onNodeWithText(it, useUnmergedTree = true).assertIsDisplayed()
        }
        save("qa-muscles", "muscle-recovery-legend")
    }

    @Test fun restTowerCardUsesTheSharedClock() = restCard(RestClockShape.TOWER)
    @Test fun restTrackCardUsesTheSharedClock() = restCard(RestClockShape.TRACK)
    @Test fun restSpiralCardUsesTheSharedClock() = restCard(RestClockShape.SPIRAL)

    private fun restCard(shape: RestClockShape) {
        val rest = WorkoutRestModalState(exerciseName = "Sentadilla", plannedSeconds = 180, activeSeconds = 249, suggestedSeconds = 249)
        content {
            Box(Modifier.size(272.dp, 388.dp).background(Color.Black).testTag("qa-rest-card")) {
                RestLiveCard(158, 249, peach, rest, null, null, CompletedSet("last", weight = 95.0, reps = 6),
                    {}, {}, {}, null, {}, isAdaptiveActive = true, shape = shape, clockActive = false)
            }
        }
        rule.onNodeWithContentDescription("Reloj de descanso: ${shape.label}").assertIsDisplayed()
        rule.onNodeWithText("de 4:09 · sugerido").assertExists()
        val reference = rule.onNodeWithText("Plan de sesión · 3:00").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val card = rule.onNodeWithTag("qa-rest-card").fetchSemanticsNode().boundsInRoot
        assertTrue("La referencia del plan queda completa junto al reloj", reference.top >= card.top && reference.bottom <= card.bottom)
        save("qa-rest-card", "rest-card-${shape.name}")
    }

    @Test fun relatorAcknowledgesTheAppliedLoadAndKeepsInlineUndo() {
        val offer = "Hoy conviene ajustar. Ajusta carga."
        val action = RelatorAssistAction(RelatorAssistActionKind.ADJUST_LOAD, "Ajusta carga", exerciseId = "press", setIndex = 0,
            span = "Ajusta carga", loadDeltaPercent = -5.0)
        var weight = 60.0
        var calls = 0
        content {
            WorkoutLiveRelatorLine(offer, "offer", actions = listOf(action), reduceMotionOverride = true,
                modifier = Modifier.width(360.dp).height(WorkoutUiTokens.liveRelatorBandHeight(LocalDensity.current)),
                actionContext = RelatorActionUiContext("press:0") { clicked ->
                    calls++
                    val previous = weight
                    weight = if (clicked.undoPreviousLoad) 60.0 else 57.0
                    RelatorAssistAck(clicked.kind, applied = true, appliedWeightKg = weight, previousWeightKg = previous,
                        canUndoLoad = !clicked.undoPreviousLoad)
                })
        }
        rule.mainClock.autoAdvance = false
        clickInline(offer, "Ajusta carga")
        rule.mainClock.advanceTimeBy(2032)
        val done = "Listo: la serie 1 va con 57\u00A0kg. ↶ vuelve a 60."
        rule.onNodeWithText(done, useUnmergedTree = true).assertExists()
        assertEquals(1, calls)
        assertEquals(57.0, weight, 0.0)
        clickInline(done, "vuelve a 60")
        rule.mainClock.advanceTimeBy(32)
        assertEquals(2, calls)
        assertEquals(60.0, weight, 0.0)
    }

    private fun clickInline(text: String, span: String) {
        val node = rule.onNodeWithText(text, useUnmergedTree = true)
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val point = layouts.single().getBoundingBox(text.indexOf(span) + 3).center
        node.performTouchInput { click(point) }
    }

    private fun workingRelator(scale: Float) {
        val action = RelatorAssistAction(RelatorAssistActionKind.ADJUST_LOAD, "Ajusta carga", exerciseId = "press", setIndex = 0, weightKg = 57.0)
        content(scale) {
            WorkoutLiveRelatorLine("Ajusta carga", "working", actions = listOf(action),
                modifier = Modifier.width(360.dp).height(WorkoutUiTokens.liveRelatorBandHeight(LocalDensity.current)).background(Color.Black).testTag("qa-relator"),
                forceWorkingProgress = .45f, forceShimmerSweepProgress = .32f, reduceMotionOverride = true)
        }
        val actors = rule.onNodeWithTag("relator-working-lane", true).fetchSemanticsNode().boundsInRoot
        val text = rule.onNodeWithText("El equipo está cargando los discos de 57\u00A0kg…", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Actors and ground stay above the phrase", text.top - actors.bottom >= 5 * density)
        save("qa-relator", "working-relator-${(scale * 100).toInt()}")
    }
}

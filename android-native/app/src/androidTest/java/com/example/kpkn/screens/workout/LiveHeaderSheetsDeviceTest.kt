package com.example.kpkn.screens.workout

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.sessionassistant.*
import com.example.kpkn.screens.workout.components.*
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Controls are exercised on the physical target; this does not assert microphone hardware behavior. */
@RunWith(AndroidJUnit4::class)
class LiveHeaderSheetsDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val subTag = WorkoutSubTag("seat", "Asiento 3", SubTagCategory.SETUP)
    private val tag = WorkoutTag("uuid-machine", "Máquina A", "exercise", subTags = listOf(subTag))
    private fun content(body: @Composable () -> Unit) {
        rule.setContent { KPKNTheme { MaterialTheme { Box(Modifier.fillMaxSize()) { body() } } } }
    }
    private fun tap(text: String) = rule.onNodeWithText(text).performScrollTo().performClick()

    @Test fun uuidTagsSelectManageAndToggleSubtagsWithoutCommittingLegacyStrings() {
        var selected: String? = null
        var managed: String? = null
        var sub: String? = null
        var opened = 0
        content {
            WorkoutTagListOverlay(listOf(WorkoutTagListRow(tag, tag.name, "Última: 80 kg", true)),
                onSelectTag = { selected = it }, onCreateTag = {}, onDismiss = {}, onOpened = { opened++ },
                onManageTag = { managed = it }, onToggleSubTag = { sub = it })
        }
        rule.onNodeWithText(tag.name).performClick()
        rule.onNodeWithText(tag.name).performTouchInput { longClick() }
        rule.onNodeWithText(subTag.name).performClick()
        rule.runOnIdle { assertEquals(tag.id, selected); assertEquals(tag.id, managed); assertEquals(subTag.id, sub); assertEquals(1, opened) }
        rule.onAllNodesWithText("Esta serie").assertCountEquals(0)
        rule.onAllNodesWithText("Color").assertCountEquals(0)
    }


    @Test fun tagListClosesFromItsButtonAndBackWithoutSelectingAnything() {
        var open by mutableStateOf(true)
        var selected = 0
        content { if (open) WorkoutTagListOverlay(emptyList(), { selected++ }, {}, { open = false }) }
        rule.onNodeWithText("Listo").performClick()
        rule.waitUntil(2_000) { !open }
        rule.runOnIdle { open = true }
        rule.onNodeWithText("Etiquetas").assertExists()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitUntil(2_000) { !open }
        rule.runOnIdle { assertEquals(0, selected) }
    }

    @Test fun inlineTagCreationKeepsSetupAndDuplicateValidation() {
        var createdName: String? = null
        var setup: TagSetupInput? = null
        content {
            WorkoutTagListOverlay(emptyList(), {}, {}, {}, hasSeenEducation = false,
                onCreateInline = { name, input -> createdName = name; setup = input; CreateTagResult.Duplicate(name) })
        }
        rule.onNodeWithContentDescription("Nombre de la etiqueta nueva").performTextReplacement("Máquina A")
        tap("Set-up opcional · Mostrar")
        rule.onNodeWithContentDescription("Máquina o marca").performScrollTo().performTextReplacement("Rack 2")
        rule.onNodeWithContentDescription("Carga base (kg)").performScrollTo().performTextReplacement("20,5")
        rule.onNodeWithContentDescription("Notas de set-up").performScrollTo().performTextReplacement("Pin 3")
        tap("Crear")
        rule.onNodeWithText("Ya existe una etiqueta con el nombre «Máquina A».").assertExists()
        rule.runOnIdle { assertEquals("Máquina A", createdName); assertEquals(TagSetupInput("Rack 2", 20.5, "Pin 3"), setup) }
    }

    @Test fun legacyTagsKeepTheirStringCallbacksAndCanBeCleared() {
        val values = mutableListOf<String>()
        var dismissed = false
        content { ExerciseTagSheetContent("Barra", { values += it }, { dismissed = true }, userTags = listOf("Barra", "Smith")) }
        rule.onNodeWithText("Smith").performClick()
        rule.onNodeWithText("Eliminar etiqueta").performClick()
        rule.onNodeWithText("Listo").performClick()
        rule.runOnIdle { assertEquals(listOf("Smith", ""), values); assertTrue(dismissed) }
    }

    @Test fun untaggedHistoryRetainsDistinctAdoptAndCleanActions() {
        var adopted = 0
        var rejected = 0
        content { WorkoutUntaggedAdoptionDialog(PendingUntaggedAdoption("exercise", tag.id, tag.name, 3), { adopted++ }, { rejected++ }, {}) }
        rule.onNodeWithText("Usar historial").performClick()
        rule.onNodeWithText("Dejar limpia").performClick()
        rule.runOnIdle { assertEquals(1, adopted); assertEquals(1, rejected) }
        rule.onNodeWithText("Default", substring = true).assertExists()
    }

    @Test fun managerRetainsRenameSetupAndResetConfirmation() {
        var renamed: String? = null
        var saved: TagSetupInput? = null
        var reset = 0
        lateinit var finishEditing: () -> Unit
        content {
            val focusManager = LocalFocusManager.current
            val keyboard = LocalSoftwareKeyboardController.current
            SideEffect { finishEditing = { focusManager.clearFocus(force = true); keyboard?.hide() } }
            WorkoutTagManagerModal(tag, "exercise", { renamed = it; RenameTagResult.Success }, {}, { reset++ },
                { _, _ -> }, {}, {}, emptyList(), {}, machineBrand = "Rack", baseLoadKg = "20", onSaveSetup = { saved = it })
        }
        rule.onNodeWithContentDescription("Nombre").performTextReplacement("Máquina B")
        tap("Guardar nombre")
        tap("Set-up · Mostrar")
        rule.onNodeWithContentDescription("Carga base (kg)").performScrollTo().performTextReplacement("25,5")
        tap("Guardar set-up")
        captureManagerDiagnostic("after-save-before-ending-edit")
        // Finish text editing explicitly in the ComponentActivity test host, then test the real touch target.
        rule.runOnIdle { finishEditing() }
        rule.onNodeWithContentDescription("Carga base (kg)").assertIsNotFocused()
        rule.waitUntil(2_000) { !managerImeVisible() }
        rule.waitForIdle()
        // The merged panel inherits scrolling but has the wrong viewport; gesture inside the actual body.
        val body = rule.onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange), useUnmergedTree = true)
            .assertIsDisplayed()
        captureManagerDiagnostic("before-body-swipe")
        body.performTouchInput { swipeUp(startY = height * 0.8f, endY = height * 0.2f) }
        rule.waitForIdle()
        captureManagerDiagnostic("after-body-swipe")
        rule.onNode(hasText("Reiniciar historial") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertIsDisplayed().performClick()
        rule.onNodeWithText("Los registros de sesión no se borran.", substring = true, useUnmergedTree = true).assertExists()
        rule.runOnIdle { assertEquals(0, reset) }
        rule.onNodeWithText("Reiniciar", useUnmergedTree = true).assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals("Máquina B", renamed); assertEquals(25.5, saved!!.baseLoadKg!!, .0001); assertEquals(1, reset) }
    }

    @Test fun realVoiceModeIsActivatedOnlyAfterExplicitStart() {
        val chosen = mutableListOf<VoiceCaptureMode>()
        content { VoiceCaptureModeDialog({ chosen += it }, {}) }
        rule.onNodeWithText("Empezar").assertIsNotEnabled()
        rule.onNodeWithText("Manos libres").performClick()
        rule.onNodeWithText("Música").performClick()
        rule.runOnIdle { assertTrue(chosen.isEmpty()) }
        rule.onNodeWithText("Empezar").performClick()
        rule.runOnIdle { assertEquals(listOf(VoiceCaptureMode.MUSIC), chosen) }
        rule.onAllNodesWithText("Prueba de sonido").assertCountEquals(0)
        rule.onAllNodesWithText("Te habla").assertCountEquals(0)
    }

    @Test fun decliningVoiceDoesNotActivateAMode() {
        var open by mutableStateOf(true)
        var activated = false
        content { if (open) VoiceCaptureModeDialog({ activated = true }, { open = false }) }
        rule.onNodeWithText("Ahora no").performClick()
        rule.waitUntil(2_000) { !open }
        rule.runOnIdle { assertFalse(activated) }
    }

    @Test fun timePresetsDeltasAndTemporaryCommitUseRealGoalCallback() {
        var goal: Pair<Int, Boolean>? = null
        content {
            WorkoutChronometer(System.currentTimeMillis() - 600_000, false, 3_000, {},
                onSetAbsoluteTimeLimit = { value, permanent -> goal = value to permanent }, currentTargetMinutes = 60,
                sessionHasProgrammedTime = true, openSessionTimeRequest = 1)
        }
        listOf("30 min", "45 min", "60 min", "90 min").forEach { tap(it) }
        tap("−5 min"); tap("+5 min"); tap("+15 min")
        tap("Solo esta vez")
        rule.runOnIdle { assertEquals(105 to false, goal) }
    }

    @Test fun programmedTimeClearRequiresThePermanentConfirmationAndAlertsKeepTheirModes() {
        var cleared: Boolean? = null
        val alerts = mutableListOf<PacingAlertMode>()
        content {
            WorkoutChronometer(System.currentTimeMillis() - 600_000, false, 3_000, {}, onClearTimeLimit = { cleared = it },
                onPacingAlertModeChange = { alerts += it }, currentTargetMinutes = 60,
                sessionHasProgrammedTime = true, openSessionTimeRequest = 1)
        }
        listOf("Nada", "15 y 5 min", "Ritmo", "Por ejercicio").forEach { tap(it) }
        tap("Quitar")
        rule.runOnIdle { assertNull(cleared) }
        tap("Permanente")
        rule.runOnIdle { assertEquals(true, cleared); assertEquals(listOf(PacingAlertMode.OFF, PacingAlertMode.FINAL, PacingAlertMode.SOFT, PacingAlertMode.STRICT), alerts) }
    }

    @Test fun assigningCustomTimeKeepsTheNativePickerAndPermanentCommit() {
        var goal: Pair<Int, Boolean>? = null
        content {
            WorkoutChronometer(System.currentTimeMillis() - 600_000, false, 3_000, {},
                onSetAbsoluteTimeLimit = { value, permanent -> goal = value to permanent }, currentTargetMinutes = 60,
                sessionHasProgrammedTime = true, openSessionTimeRequest = 1)
        }
        tap("45 min")
        tap("Asignar tiempo")
        rule.onNodeWithText("Duración de sesión").assertExists()
        rule.onNodeWithText("Aceptar").performClick()
        tap("Permanente")
        rule.runOnIdle { assertEquals(45 to true, goal) }
    }

    @Test fun ultraFastCountLimitsAndRecalculatedProtectionRemainReversible() {
        var submitted: Map<String, Int>? = null
        var override by mutableStateOf(false)
        content {
            val preview = UltraFastPreview(1_800, 1_200, 600, listOf(
                UltraFastExerciseChange("curl", "Curl", 3, 2, "NORMAL", "DROPSET", UltraFastReason.ISOLATION_DENSIFIED, true, false),
                UltraFastExerciseChange("press", "Press", 3, if (override) 2 else 3, "NORMAL", if (override) "DROPSET" else "NORMAL",
                    if (override) UltraFastReason.MANUAL_OVERRIDE_ALLOWED else UltraFastReason.PROTECTED_BASIC, override, false),
            ), listOf(UltraFastSupersetChange("curl", "press", "Curl", "Press", "Polea")))
            UltraFastPreviewSheet(preview, 600, emptyList(), mapOf("press" to override), { override = !override }, { submitted = it }, {})
        }
        repeat(4) { rule.onNodeWithTag("ultra-plus-curl").performScrollTo().performClick() }
        rule.onNodeWithTag("ultra-plus-curl").assertIsNotEnabled()
        repeat(5) { rule.onNodeWithTag("ultra-minus-curl").performScrollTo().performClick() }
        rule.onNodeWithTag("ultra-minus-curl").assertIsNotEnabled()
        tap("Mantener protegido")
        rule.onNodeWithText("Mayor riesgo").assertExists()
        tap("Permitir técnica intensa")
        rule.runOnIdle { assertFalse(override) }
        rule.onNodeWithText("Curl + Press", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Aplicar").performClick()
        rule.runOnIdle { assertEquals(mapOf("curl" to 1), submitted) }
        rule.onAllNodesWithText("Como la anterior").assertCountEquals(0)
    }
    private fun managerImeVisible(): Boolean = rule.runOnIdle {
        ViewCompat.getRootWindowInsets(rule.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
    }

    /** Records focus/IME/viewport evidence around the single physical swipe and touch target. */
    @OptIn(ExperimentalTestApi::class)
    private fun captureManagerDiagnostic(phase: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "lv12-manager-diagnostics").apply { mkdirs() }
        val window = rule.runOnIdle {
            val view = rule.activity.window.decorView
            val visible = Rect().also { view.getWindowVisibleDisplayFrame(it) }
            "imeVisible=${ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime())}; " +
                "softInputMode=${rule.activity.window.attributes.softInputMode}; decorHeight=${view.height}; visibleFrame=$visible"
        }
        File(directory, "$phase.txt").writeText("$window\nMERGED\n" + rule.onRoot().printToString() +
            "\nUNMERGED\n" + rule.onRoot(useUnmergedTree = true).printToString())
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
            ?: rule.onRoot(useUnmergedTree = true).captureToImage().asAndroidBitmap()
        File(directory, "$phase.png").outputStream().use { output ->
            check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }

}

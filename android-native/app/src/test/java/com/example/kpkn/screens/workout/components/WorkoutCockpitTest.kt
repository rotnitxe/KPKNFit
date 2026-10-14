package com.example.kpkn.screens.workout.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.data.models.SessionEnergySummary
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.workout.WorkoutFinishSamples
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Cockpit «Sesión» (LS6), con el cuerpo montado suelto: abre en la página que se pide y cambiar de pestaña muestra su página.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h1800dp-xxhdpi")
class WorkoutCockpitTest {

    @get:Rule
    val rule = createComposeRule()

    private fun mount(page: WorkoutCockpitPage) {
        rule.setContent {
            KPKNTheme {
                WorkoutSessionCockpitBody(
                    exercises = emptyList(),
                    completedSets = emptyMap(),
                    milestones = WorkoutFinishSamples.milestones,
                    sessionProgressLabel = "Progreso: 12/22",
                    liveEnergySummary = SessionEnergySummary(),
                    sessionNotes = "",
                    sessionSavedNotes = WorkoutFinishSamples.savedNotes,
                    sessionPhotos = emptyList(),
                    sessionMedia = emptyList(),
                    sessionChecklist = WorkoutFinishSamples.checklist,
                    onSessionNotesChange = {},
                    onSaveSessionNote = {},
                    onAddSessionPhoto = {},
                    onBeginSessionCameraCapture = { null },
                    onCompleteSessionCameraCapture = { _, _ -> },
                    onSessionCameraLaunchFailed = {},
                    mediaCaptureError = null,
                    onRetryPendingMediaCaptures = {},
                    onRemoveSessionPhoto = {},
                    onRemoveSessionMedia = {},
                    onAddChecklistItem = {},
                    onToggleChecklistItem = {},
                    onRemoveChecklistItem = {},
                    sessionAccentColor = SymbolPalette.musculo,
                    bodyWeight = 80.0,
                    poseOverlayEnabled = false,
                    initialPage = page,
                )
            }
        }
    }

    @Test
    fun abreEnResumenPorDefecto() {
        mount(WorkoutCockpitPage.Overview)
        rule.onNodeWithText("HITOS DE LA SESIÓN").assertExists()
        rule.onNodeWithText("Cámara").assertDoesNotExist()
    }

    @Test
    fun abreEnNotasCuandoSeLePide() {
        mount(WorkoutCockpitPage.Notes)
        rule.onNodeWithText("Anotaciones generales de la sesión…").assertExists()
        rule.onNodeWithText("HITOS DE LA SESIÓN").assertDoesNotExist()
    }

    @Test
    fun cambiarDePestanaMuestraSuPagina() {
        mount(WorkoutCockpitPage.Notes)
        rule.onNodeWithText("Fotos").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Cámara").assertExists()
        rule.onNodeWithText("Galería").assertExists()
        rule.onNodeWithText("Anotaciones generales de la sesión…").assertDoesNotExist()
    }
}

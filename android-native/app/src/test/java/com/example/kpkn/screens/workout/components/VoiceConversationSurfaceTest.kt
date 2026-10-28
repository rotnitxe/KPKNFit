package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.workout.VoiceHistoryAudit
import com.example.kpkn.domain.workout.VoiceHistoryEntry
import com.example.kpkn.domain.workout.VoiceHistoryStatus
import com.example.kpkn.domain.workout.VoiceHistoryValues
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h880dp-xxhdpi")
class VoiceConversationSurfaceTest {
    @get:Rule val rule = createComposeRule()

    private val actions = mutableListOf<String>()
    private val corrections = mutableListOf<Triple<Double, Int, Double>>()

    private fun show(entries: List<VoiceHistoryEntry>, voiceEnabled: Boolean = true, notice: String? = null) {
        rule.setContent {
            CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides Density(3f, 1.3f),
                LocalWizardReducedMotion provides true,
            ) {
                KPKNTheme {
                    Box(Modifier.width(360.dp).height(880.dp)) {
                        VoiceConversationSurface(
                            entries = entries,
                            voiceEnabled = voiceEnabled,
                            captureModeLabel = "Manos libres",
                            sessionName = "Pierna · Semana 1",
                            currentExerciseName = "Sentadilla",
                            currentSetLabel = "serie 3",
                            notice = notice,
                            onClose = { actions += "close" },
                            onTogglePause = { actions += "toggle" },
                            onDisable = { actions += "disable" },
                            onListenExample = { actions += "example" },
                            onConfirmCorrection = { _, weight, reps, rir ->
                                corrections += Triple(weight, reps, rir)
                            },
                        )
                    }
                }
            }
        }
    }

    @Test
    fun showsTimelineStatusesAndPendingCount() {
        show(listOf(reviewEntry(), registeredEntry()))

        rule.onNodeWithTag("voice-conversation-surface").assertExists()
        rule.onNodeWithText("HOY · PIERNA · SEMANA 1").assertExists()
        rule.onNodeWithText("Por revisar · 1").assertExists()
        rule.onNodeWithText("Revisar antes de registrar").assertExists()
        rule.onNodeWithText("Corregida · v2").assertExists()
        rule.onNodeWithText("Ver cambios").assertExists()
        rule.onNodeWithText("Te escucho").assertExists()
    }

    @Test
    fun reviewFilterHidesRegisteredCapturesUntilConversationIsSelected() {
        show(listOf(reviewEntry(), registeredEntry()))

        rule.onNodeWithTag("voice-tab-review").performClick()
        rule.onNodeWithText("ochenta kilos ocho repeticiones", substring = true).assertExists()
        rule.onNodeWithText("setenta y cinco kilos ocho repeticiones", substring = true).assertDoesNotExist()

        rule.onNodeWithTag("voice-tab-all").performClick()
        rule.onNodeWithText("setenta y cinco kilos ocho repeticiones", substring = true).assertExists()
    }

    @Test
    fun emptyReviewFilterExplainsEverythingIsReviewed() {
        show(listOf(registeredEntry()))

        rule.onNodeWithTag("voice-tab-review").performClick()

        rule.onNodeWithText("Todas las capturas están revisadas.").assertExists()
    }

    @Test
    fun pausedListeningOffersResumeAndKeepsExampleDisabled() {
        show(listOf(reviewEntry()), voiceEnabled = false, notice = "Registro confirmado. Se actualiza la misma serie.")

        rule.onNodeWithText("En pausa").assertExists()
        rule.onNodeWithTag("voice-example").assertIsNotEnabled()
        rule.onNodeWithTag("voice-history-notice").assertExists()

        rule.onNodeWithTag("voice-pause").performClick()
        rule.onNodeWithTag("voice-disable").performClick()

        assertTrue(actions.contains("toggle"))
        assertTrue(actions.contains("disable"))
    }

    @Test
    fun reviewCaptureValidatesFieldsAndConfirmsTheCorrection() {
        show(listOf(reviewEntry()))

        rule.onNodeWithTag("voice-entry-review-review").performClick()
        rule.onNodeWithText("Corregir captura").assertExists()
        rule.onNodeWithText("SE ESCUCHÓ").assertExists()

        field("voice-correction-rir").performTextReplacement("11")
        rule.onNodeWithTag("voice-correction-confirm").performClick()
        rule.onNodeWithText("Completa carga, repeticiones y RIR entre 0 y 10.").assertExists()
        assertTrue(corrections.isEmpty())

        field("voice-correction-rir").performTextReplacement("2")
        field("voice-correction-reps").performTextReplacement("9")
        rule.onNodeWithTag("voice-correction-confirm").performClick()

        assertEquals(listOf(Triple(80.0, 9, 2.0)), corrections)
        rule.onNodeWithText("Corregir captura").assertDoesNotExist()
    }

    @Test
    fun auditSheetShowsOriginalPreviousAndCurrentRevision() {
        show(listOf(registeredEntry()))

        rule.onNodeWithText("Ver cambios").performClick()

        rule.onNodeWithText("Historial de correcciones").assertExists()
        rule.onNodeWithText("La transcripción original se conserva.").assertExists()
        rule.onNodeWithText("ORIGINAL").assertExists()
        rule.onNodeWithText("VALOR ANTERIOR").assertExists()
        rule.onNodeWithText("ACTUAL · V2").assertExists()
        rule.onNodeWithText("75 kg × 8 reps · RIR 2").assertExists()

        rule.onNodeWithText("Cerrar").performClick()
        rule.onNodeWithText("Historial de correcciones").assertDoesNotExist()
    }

    @Test
    fun timedCaptureLabelsTheMetricInSeconds() {
        show(listOf(registeredEntry().copy(
            id = "timed",
            values = VoiceHistoryValues(weightKg = 0.0, seconds = 45, intensityValue = 2.0, intensityKind = "RIR"),
        )))

        rule.onNodeWithTag("voice-entry-edit-timed").performClick()

        rule.onNodeWithText("Segundos").assertExists()
    }

    private fun field(tag: String) =
        rule.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)

    private fun reviewEntry() = VoiceHistoryEntry(
        id = "review",
        sessionKey = "session",
        exerciseId = "squat",
        exerciseName = "Sentadilla",
        setIdx = 2,
        transcript = "ochenta kilos ocho repeticiones",
        values = VoiceHistoryValues(weightKg = 80.0, reps = 8, intensityValue = 2.0, intensityKind = "RIR"),
        status = VoiceHistoryStatus.REVIEW,
        revision = 1,
        createdAtEpochMs = 1_759_000_000_000,
        updatedAtEpochMs = 1_759_000_000_000,
    )

    private fun registeredEntry(): VoiceHistoryEntry {
        val values = VoiceHistoryValues(weightKg = 75.0, reps = 8, intensityValue = 2.0, intensityKind = "RIR")
        return VoiceHistoryEntry(
            id = "registered",
            sessionKey = "session",
            exerciseId = "squat",
            exerciseName = "Sentadilla",
            setIdx = 1,
            transcript = "setenta y cinco kilos ocho repeticiones",
            values = values,
            status = VoiceHistoryStatus.REGISTERED,
            revision = 2,
            audit = VoiceHistoryAudit(previous = values.copy(weightKg = 72.5), next = values),
            createdAtEpochMs = 1_759_000_100_000,
            updatedAtEpochMs = 1_759_000_200_000,
        )
    }
}

package com.example.kpkn.render

import android.app.Application
import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.workout.VoiceCommandRecord
import com.example.kpkn.domain.workout.VoiceHistoryEntry
import com.example.kpkn.domain.workout.VoiceHistoryStatus
import com.example.kpkn.domain.workout.VoiceHistoryValues
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.workout.LinkedDiscomfort
import com.example.kpkn.screens.workout.LiveSessionListView
import com.example.kpkn.screens.workout.LiveSessionVmFixture
import com.example.kpkn.screens.workout.LocalLiveSessionCompactLayout
import com.example.kpkn.screens.workout.PendingRestSuggestion
import com.example.kpkn.screens.workout.PostExerciseFeedbackDraft
import com.example.kpkn.screens.workout.RestClockShape
import com.example.kpkn.screens.workout.RestHeadline
import com.example.kpkn.screens.workout.RestNextSetSummary
import com.example.kpkn.screens.workout.RestOverrun
import com.example.kpkn.screens.workout.RestTimerKind
import com.example.kpkn.screens.workout.WorkoutRestModalState
import com.example.kpkn.screens.workout.FeedbackInsights
import com.example.kpkn.screens.workout.CardioTransitionOut
import com.example.kpkn.screens.workout.components.LocalRestClockFrozen
import com.example.kpkn.screens.workout.components.RestFeedbackCard
import com.example.kpkn.screens.workout.components.RestLiveCard
import com.example.kpkn.screens.workout.components.RestOverrunOverlay
import com.example.kpkn.screens.workout.components.RestRef02FeedbackActions
import com.example.kpkn.screens.workout.components.RestRef02FeedbackBody
import com.example.kpkn.screens.workout.components.RestTimerOverlay
import com.example.kpkn.screens.workout.components.VoiceCapsuleContext
import com.example.kpkn.screens.workout.components.VoiceCapsuleLayer
import com.example.kpkn.testsupport.RoomTestRule
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.theme.KPKNTheme
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * HOJA DE RENDER de la paridad con el Refinamiento 02 (sesión en vivo): el descanso (tarjeta, pantalla completa, tiempo extra, feedback
 * y selector de juegos), la cápsula de voz (escucha y registros), «Ahora vamos con fuerza» y la vista Lista. No comprueba nada: escribe
 * PNG de 360 × 740 dp en `KPKN_RENDER_DIR`. Sin esa variable de entorno se salta. Usa los composables REALES con datos de ejemplo y sin
 * animaciones (movimiento reducido y reloj del descanso congelado) para que el PNG sea estable.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h880dp-xxhdpi")
class Ref02ParityRenderSheetTest {
    private val room = RoomTestRule()
    private val compose = createComposeRule()
    @get:Rule val chain: RuleChain = RuleChain.outerRule(room).around(compose)
    private val rule get() = compose

    private var view: View? = null

    private val squat = Exercise(
        id = "sq", name = "Sentadilla trasera", trainingMode = TrainingMode.REPS, restTime = 120,
        sets = List(3) { index ->
            ExerciseSet(id = "sq-${index + 1}", targetReps = 8, weight = 80.0, targetRIR = 2,
                intensityMode = IntensityMode.RIR, loadModeV2 = LoadModeV2.LOAD, unitModeV2 = UnitModeV2.REPS)
        },
    )
    private val bench = Exercise(
        id = "bp", name = "Press banca", trainingMode = TrainingMode.REPS, restTime = 90,
        sets = List(3) { index ->
            ExerciseSet(id = "bp-${index + 1}", targetReps = 10, weight = 60.0, targetRIR = 2,
                intensityMode = IntensityMode.RIR, loadModeV2 = LoadModeV2.LOAD, unitModeV2 = UnitModeV2.REPS)
        },
    )
    private val row = Exercise(
        id = "rw", name = "Remo con barra", trainingMode = TrainingMode.REPS, restTime = 90,
        sets = List(3) { index ->
            ExerciseSet(id = "rw-${index + 1}", targetReps = 10, weight = 50.0, targetRIR = 2,
                intensityMode = IntensityMode.RIR, loadModeV2 = LoadModeV2.LOAD, unitModeV2 = UnitModeV2.REPS)
        },
    )

    // Descanso de 2:00 con 1:13 restantes, tras la serie 1 de 3 de la sentadilla.
    private val restState = WorkoutRestModalState(
        exerciseId = "sq", exerciseName = "Sentadilla trasera", kind = RestTimerKind.STANDARD,
        plannedSeconds = 120, suggestedSeconds = 150, activeSeconds = 120,
    )
    private val lastSet = CompletedSet(id = "last", weight = 80.0, reps = 8)
    private val suggestion = PendingRestSuggestion(
        plannedSeconds = 120, adaptiveSeconds = 150, exerciseName = "Sentadilla trasera", exerciseId = "sq",
        lastSet = lastSet, advancedFeedback = null,
    )
    private val nextSet = RestNextSetSummary(setNumber = 2, setTotal = 3, loadKg = 80.0, repsText = "8", intensityText = "RIR 2")
    private val headline = RestHeadline("Sentadilla trasera", "Serie 1 de 3 hecha")
    private val remaining = 73
    private val total = 120

    @Test fun restCard() = scene("ref02-rest-card") {
        Box(Modifier.align(Alignment.Center).size(312.dp, 394.dp)) {
            RestLiveCard(
                remainingSeconds = remaining, totalSeconds = total, sessionAccentColor = Color(0xFFF49A6E),
                restState = restState, pendingRestSuggestion = suggestion, lastSetOutcome = null, lastCompletedSet = lastSet,
                onDecrease = {}, onIncrease = {}, onSkip = {}, onUseAdaptive = {}, onExpand = {},
                nextSet = nextSet, clockActive = false,
            )
        }
    }

    @Test fun restFull() = scene("ref02-rest-full") {
        RestTimerOverlay(
            state = restState, remainingSeconds = remaining, hazeState = HazeState(), pendingRestSuggestion = suggestion,
            lastCompletedSet = lastSet, onDecrease = {}, onIncrease = {}, onSkip = {}, onUseAdaptive = {}, onMinimize = {},
            headline = headline, nextSet = nextSet, clockActive = false,
        )
    }

    @Test fun restOverrun() = scene("ref02-rest-overrun") {
        RestOverrunOverlay(
            overrun = RestOverrun(endedAtMs = 1_000L, totalSeconds = total, kind = RestTimerKind.STANDARD, exerciseIdx = 0),
            hazeState = HazeState(), shape = RestClockShape.TOWER, headline = headline, nextSet = nextSet,
            onStart = {}, onExtend = {}, onMinimize = {}, clockActive = false, extraSecondsOverride = 12,
            lastSetText = "80 kg × 8",
        )
    }

    @Test fun feedback() = scene("ref02-feedback") {
        val draft = PostExerciseFeedbackDraft.from(listOf(squat), emptyMap())
        Box(Modifier.align(Alignment.Center).size(312.dp, 394.dp)) {
            RestFeedbackCard(
                remainingSeconds = remaining, totalSeconds = total, shape = RestClockShape.TOWER, feedbackExercises = listOf(squat),
                draft = draft, insights = FeedbackInsights.Empty, explainDiscomfort = { null }, linked = emptyList(),
                onSave = {}, onDefer = {}, onExpand = {}, clockActive = false,
            )
        }
    }

    @Test fun feedbackFull() = scene("ref02-feedback-full") {
        val draft = PostExerciseFeedbackDraft.from(listOf(squat), emptyMap())
        RestTimerOverlay(
            state = restState, remainingSeconds = remaining, hazeState = HazeState(), pendingRestSuggestion = suggestion,
            lastCompletedSet = lastSet, onDecrease = {}, onIncrease = {}, onSkip = {}, onUseAdaptive = {}, onMinimize = {},
            headline = headline, nextSet = nextSet, clockActive = false, feedbackTitle = "Sentadilla trasera",
            postExerciseFeedbackContent = {
                androidx.compose.foundation.layout.Column {
                    RestRef02FeedbackBody(
                        feedbackExercises = listOf(squat), draft = draft, linked = emptyList<LinkedDiscomfort>(),
                        wide = true, compact = false, onOpenDiscomforts = {},
                    )
                    RestRef02FeedbackActions(onDefer = {}, onSave = {}, inCard = false)
                }
            },
        )
    }

    @Test fun gamesPicker() = scene("ref02-games-picker") {
        RestTimerOverlay(
            state = restState, remainingSeconds = remaining, hazeState = HazeState(), pendingRestSuggestion = suggestion,
            lastCompletedSet = lastSet, onDecrease = {}, onIncrease = {}, onSkip = {}, onUseAdaptive = {}, onMinimize = {},
            headline = headline, nextSet = nextSet, clockActive = false, initiallyPlaying = true,
        )
    }

    private val voiceNow = 10_000_000L
    private val voiceContext = VoiceCapsuleContext(exerciseName = "Sentadilla trasera", setLabel = "serie 2", captureModeLabel = "Voz")
    private val voiceEntry = VoiceHistoryEntry(
        id = "v1", sessionKey = "s", exerciseId = "sq", exerciseName = "Sentadilla trasera", setIdx = 0,
        transcript = "ochenta por ocho a dos",
        values = VoiceHistoryValues(weightKg = 80.0, reps = 8, intensityValue = 2.0, intensityKind = "RIR"),
        status = VoiceHistoryStatus.REGISTERED, createdAtEpochMs = 1_000L, updatedAtEpochMs = 1_000L,
    )
    private val voiceCommand = VoiceCommandRecord(id = "c1", atEpochMs = 2_000L, transcript = "más quince", value = "+15 s", detail = "descanso")

    @Test fun voiceCapsule() = scene("ref02-voice-capsule") {
        VoiceCapsuleLayer(
            entries = listOf(voiceEntry), voiceEnabled = true, context = voiceContext, expanded = false, onExpandedChange = {},
            bottomPadding = 130.dp, notice = null, guideSpeech = null, onStopGuide = {}, onTogglePause = {}, onDisable = {},
            onListenExample = {}, onConfirmCorrection = { _, _, _, _ -> }, onUndo = {}, onDiscard = {}, nowMs = { voiceNow },
        )
    }

    @Test fun voiceRecords() = scene("ref02-voice-records") {
        VoiceCapsuleLayer(
            entries = listOf(voiceEntry), voiceEnabled = true, context = voiceContext, expanded = true, onExpandedChange = {},
            bottomPadding = 130.dp, notice = null, guideSpeech = null, onStopGuide = {}, onTogglePause = {}, onDisable = {},
            onListenExample = {}, onConfirmCorrection = { _, _, _, _ -> }, onUndo = {}, onDiscard = {}, nowMs = { voiceNow },
            commands = listOf(voiceCommand),
        )
    }

    @Test fun cardioTransition() = scene("ref02-cardio-transition") {
        CardioTransitionOut(
            lead = "Terminaste el cardio: 12:00 · 2,1 km.", exerciseName = "Sentadilla trasera", meta = "3 series · descanso 2:00",
            firstSet = "80 kg × 8", planEffort = "RIR 2 · plan", lastTime = "80 kg × 8 · RIR 2", chips = listOf("A1", "1", "2", "3"),
            footnote = "Aproximación A1 · 40 kg × 8 antes de la primera serie", onContinue = {},
        )
    }

    @Test fun list() {
        assumeTrue(!System.getenv("KPKN_RENDER_DIR").isNullOrBlank())
        Dispatchers.setMain(UnconfinedTestDispatcher())
        LiveSessionVmFixture.resetTrainingObservationRepository()
        val fixture = LiveSessionVmFixture(Session("ref02-list", "Fuerza A", exercises = listOf(squat, bench, row)))
        try {
            val vm = runBlocking { fixture.prepare(); fixture.open() }
            Dispatchers.resetMain()
            val state = vm.uiState.value
            val exercises = state.session!!.allExercises()
            scene("ref02-list") {
                CompositionLocalProvider(LocalLiveSessionCompactLayout provides true) {
                    LiveSessionListView(
                        uiState = state, exercises = exercises, currentExerciseId = exercises.first().id,
                        accentColor = Color(0xFFF49A6E), warmupWorkingWeight = null, viewModel = vm,
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
            }
        } finally {
            try {
                fixture.close(); LiveSessionVmFixture.resetTrainingObservationRepository()
                ProgramRepository.closeInstance(); KpknDatabase.closeInstance()
            } finally {
                runCatching { Dispatchers.resetMain() }
            }
        }
    }

    private fun scene(name: String, height: Int = 740, content: @Composable BoxScope.() -> Unit) {
        assumeTrue(!System.getenv("KPKN_RENDER_DIR").isNullOrBlank())
        rule.setContent {
            view = LocalView.current
            CompositionLocalProvider(
                LocalWizardReducedMotion provides true, LocalKpknOverlayHost provides null, LocalRestClockFrozen provides 1.7f,
            ) {
                KPKNTheme { MaterialTheme {
                    Box(Modifier.requiredSize(360.dp, height.dp).background(Color.Black).testTag("ref02-scene")) { content() }
                } }
            }
        }
        rule.waitForIdle()
        val folder = File(checkNotNull(System.getenv("KPKN_RENDER_DIR"))).apply { mkdirs() }
        val bounds = rule.onNodeWithTag("ref02-scene").fetchSemanticsNode().boundsInRoot
        val host = checkNotNull(view)
        val full = Bitmap.createBitmap(host.width, host.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { host.draw(android.graphics.Canvas(full)) }
        val left = bounds.left.toInt().coerceAtLeast(0); val top = bounds.top.toInt().coerceAtLeast(0)
        val cropped = Bitmap.createBitmap(full, left, top, bounds.width.toInt().coerceAtMost(full.width - left),
            bounds.height.toInt().coerceAtMost(full.height - top))
        File(folder, "$name.png").outputStream().use { assertTrue(cropped.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        cropped.recycle(); full.recycle()
    }
}

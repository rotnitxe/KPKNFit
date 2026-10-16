package com.example.kpkn.screens.workout

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kpkn.data.models.*
import com.example.kpkn.data.workout.RestGameRecordStore
import com.example.kpkn.domain.discomfort.DiscomfortEvidence
import com.example.kpkn.domain.discomfort.DiscomfortEvidenceKind
import com.example.kpkn.domain.discomfort.DiscomfortInsight
import com.example.kpkn.screens.workout.components.*
import com.example.kpkn.ui.adapt.LocalViewportAdapt
import com.example.kpkn.ui.adapt.LiveViewportPolicyMath
import com.example.kpkn.ui.adapt.ViewportAdaptProvider
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.components.kpknGlassOrFallback
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.theme.KPKNTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.io.File
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Native production components, captured on the physical window; no mock UI or screenshot labels. */
@RunWith(AndroidJUnit4::class)
class RedesignReviewRestCaptureTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val gamePreferences = "review-rest-game-${System.nanoTime()}"
    private val accent = Color(0xFFF49A6E)
    private val now = 1_792_170_000_000L
    private val squat = Exercise("review-squat", "Sentadilla trasera", isStarTarget = true, goal1RM = 130.0,
        sets = List(4) { ExerciseSet("sq-$it", targetReps = 6, targetRepsRange = RepRange(4, 6), targetRIR = 2, weight = 95.0) })
    private val row = Exercise("review-row", "Remo con mancuerna", sets = List(3) { ExerciseSet("row-$it", targetReps = 10, targetRIR = 2) })
    private val last = CompletedSet("review-last", weight = 95.0, reps = 6, rir = 2)
    private val rest = WorkoutRestModalState(exerciseId = squat.id, exerciseName = squat.name,
        plannedSeconds = 180, suggestedSeconds = 249, activeSeconds = 249)
    private val suggestion = PendingRestSuggestion(180, 249, squat.name, squat.id, last, null)
    private val next = RestNextSetSummary(2, 4, loadKg = 95.0, repsText = "4–6", intensityText = "RIR 2")
    private val overtime = RestOverrun(endedAtMs = now - 24_000, totalSeconds = 249)
    private val evidence = DiscomfortInsight("knee_patellar", "La zona acumuló más trabajo esta semana.",
        today = listOf(DiscomfortEvidence(DiscomfortEvidenceKind.HEAVY_SETS, 3.0,
            "3 series con una repetición en reserva.", "3 series exigentes hoy.", "Baja la carga si pierdes técnica.", 0.8)),
        week = listOf(DiscomfortEvidence(DiscomfortEvidenceKind.WEEKLY_VOLUME_RISE, 35.0,
            "35 % más series que tu media reciente.", "El volumen semanal subió un 35 %.", "Reduce algo de volumen esta semana.", 0.7)),
        advice = listOf("Baja la carga si pierdes técnica."), persistent = false, strength = 0.8)
    private val insights = FeedbackInsights(likely = listOf("knee_patellar"),
        reported = listOf(ReportedDiscomfort("lumbar", now - 2 * 86_400_000L)), notice = evidence)

    @After fun clearCaptureGamePreferences() {
        context.getSharedPreferences(gamePreferences, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun restTower() = render("rest-tower")
    @Test fun restTrack() = render("rest-track")
    @Test fun restSpiral() = render("rest-spiral")
    @Test fun restMinimizedCard() = render("rest-card", module = true)
    @Test fun restMinimizedChip() = render("rest-chip", module = true)
    @Test fun restOvertimeFull() = render("rest-overtime-full")
    @Test fun restOvertimeCard() = render("rest-overtime-card", module = true)
    @Test fun restGameFull() = render("rest-game-full")
    @Test fun restGameCard() = render("rest-game-card", module = true)
    @Test fun techniqueAndFeaturedDiscomforts() = render("feedback-technique")
    @Test fun techniqueAndDiscomfortsMinimized() = render("feedback-card", module = true)
    @Test fun discomfortSearch() = render("feedback-search")
    @Test fun discomfortExplanation() = render("discomfort-detail")
    @Test fun seriesOptions() = render("series-options")
    @Test fun cockpitMilestones() = render("cockpit-hitos", module = true, tab = "Hitos")
    @Test fun cockpitPhotoEntry() = render("cockpit-fotos", module = true, tab = "Fotos")
    @Test fun cockpitNotesAndChecklist() = render("cockpit-notas", module = true, tab = "Notas")

    @OptIn(ExperimentalTestApi::class)
    private fun render(scene: String, module: Boolean = false, tab: String? = null) {
        rule.setContent {
            KPKNTheme {
                ViewportAdaptProvider {
                    val haze = remember { HazeState() }
                    CompositionLocalProvider(LocalKpknOverlayHost provides null, LocalRestClockFrozen provides 1.7f) {
                        Box(Modifier.fillMaxSize().background(KpknSurface.canvas).testTag(CanvasTag)) {
                            Box(Modifier.fillMaxSize().hazeSource(haze).background(KpknSurface.canvas))
                            when {
                                scene.startsWith("cockpit-") -> Cockpit(haze)
                                scene == "series-options" -> SeriesOptionsSheet(squat, SeriesTypeTarget(squat.id, 1, 3),
                                    mapOf("${squat.id}_0" to last), emptySet(), {}, { _, _ -> }, {}, {}, {}, {})
                                scene == "discomfort-detail" -> DiscomfortSheet(checkNotNull(discomfortKnowledge("knee_patellar")),
                                    false, evidence, {}, {}, animated = true)
                                scene == "rest-chip" -> Box(Modifier.align(Alignment.TopCenter).padding(16.dp).testTag(ModuleTag)) {
                                    RestMiniChip(158, 249, {}, {})
                                }
                                scene.endsWith("-card") -> MeasuredRestCard(scene)
                                scene == "rest-overtime-full" -> RestOverrunOverlay(overtime, haze, RestClockShape.TOWER,
                                    RestHeadline(squat.name, "Serie 2 de 4"), next, {}, {}, {}, clockActive = false, extraSecondsOverride = 24)
                                else -> RestOverlay(scene, haze)
                            }
                        }
                    }
                }
            }
        }
        if (tab != null) rule.onNode(hasText(tab) and hasClickAction()).performScrollTo().performClick()
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        if (scene.endsWith("-card")) rule.onNodeWithTag(CalibrationTag).assertDoesNotExist()
        when (scene) {
            "cockpit-hitos" -> rule.onNodeWithText("Hitos de la sesión").assertIsDisplayed()
            "cockpit-fotos" -> rule.onNodeWithText("Cámara").assertIsDisplayed()
            "cockpit-notas" -> rule.onNodeWithText("Notas de sesión").assertIsDisplayed()
            "discomfort-detail" -> rule.onNodeWithText("Reportar molestia").assertIsDisplayed()
            "series-options" -> rule.onNodeWithText("Aplicar").assertIsDisplayed()
            "feedback-technique", "feedback-search", "feedback-card" -> rule.onNodeWithText("Guardar").assertIsDisplayed()
        }
        val target = rule.onNodeWithTag(if (module) ModuleTag else CanvasTag).assertIsDisplayed()
        val image = target.captureToImage().asAndroidBitmap()
        assertTrue("Capture must contain the physical component", image.width > 0 && image.height > 0)
        val directory = File(context.getExternalFilesDir(null), "review-rest").apply { mkdirs() }
        val file = File(directory, "$scene.png")
        file.outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        assertTrue(file.length() > 0)
    }

    @Composable private fun RestOverlay(scene: String, haze: HazeState) {
        val shape = when (scene) { "rest-track" -> RestClockShape.TRACK; "rest-spiral" -> RestClockShape.SPIRAL; else -> RestClockShape.TOWER }
        val feedback = scene.startsWith("feedback-")
        val draft = remember { PostExerciseFeedbackDraft(listOf(squat.id), mapOf(squat.id to 6),
            initialQuery = if (scene == "feedback-search") "rodilla" else "") }
        RestTimerOverlay(state = rest, remainingSeconds = 158, hazeState = haze,
            pendingRestSuggestion = if (feedback) null else suggestion, lastCompletedSet = last,
            isAdaptiveActive = true, sessionAccentColor = accent, onDecrease = {}, onIncrease = {}, onSkip = {},
            onUseAdaptive = {}, onMinimize = {}, relatorText = if (feedback) null else "Buen ritmo. Recupera el aire para la siguiente serie.",
            shape = shape, headline = RestHeadline(squat.name, "Serie 1 de 4 completada"), nextSet = next,
            clockActive = false, initiallyPlaying = scene == "rest-game-full", gameEngine = remember { game() },
            recordStore = remember { gameStore() }, feedbackExerciseCount = if (feedback) 1 else 0,
            feedbackAfter = row.name,
            postExerciseFeedbackContent = if (feedback) ({
                PostExerciseFeedbackPanel(listOf(squat), emptyMap(), accent, { it.id }, {}, draft = draft,
                    insights = insights, explainDiscomfort = { evidence }, nowMs = now)
            }) else null)
    }

    /** Measure a genuine working-set front, then use its same production height owner and frame for rest. */
    @Composable private fun MeasuredRestCard(scene: String) {
        val height = remember { mutableIntStateOf(0) }
        val adapt = LocalViewportAdapt.current
        val policy = LiveViewportPolicyMath.compute(widthDp = adapt.widthDp, heightDp = adapt.heightDp, godModeActive = false).copy(cardHeightMul = 1f, cardPageFillX = 0f)
        CompositionLocalProvider(LocalLivePagerWorkingSetVisualHeightPx provides height,
            LocalLivePagerAdaptScale provides adapt.uniformScale, LocalLiveViewportPolicy provides policy) {
            LivePagerCardFrame(modifier = Modifier.testTag(ModuleTag), allowContentExpansion = true,
                lockToWorkingSetHeight = height.intValue > 0) {
                if (height.intValue == 0) {
                    Box(Modifier.testTag(CalibrationTag)) {
                        SetInputCardV2(squat, 0, squat.sets.first(), null,
                            onShowHistory = {}, onSetBodyWeight = {}, initialBodyWeight = 75.0,
                            recordActionHolder = remember { RecordActionHolder() }, effortFireSecondsOverride = 1.7f,
                            onRecordV2 = { _, _, _, _, _, _, _, _, _, _ -> })
                    }
                } else when (scene) {
                    "rest-overtime-card" -> RestOverrunCard(overtime, RestClockShape.TOWER, next, {}, {}, {},
                        clockActive = false, extraSecondsOverride = 24)
                    "feedback-card" -> {
                        val draft = remember { PostExerciseFeedbackDraft(listOf(squat.id), mapOf(squat.id to 6)) }
                        RestFeedbackCard(158, 249, RestClockShape.TOWER, listOf(squat), draft, insights,
                            { evidence }, emptyList(), {}, {}, {}, clockActive = false, nowMs = now)
                    }
                    else -> RestLiveCard(158, 249, accent, rest, suggestion, null, last, {}, {}, {}, {}, {},
                        isAdaptiveActive = true, shape = RestClockShape.TOWER, nextSet = next,
                        clockActive = false, initiallyPlaying = scene == "rest-game-card",
                        gameEngine = remember { game() }, recordStore = remember { gameStore() })
                }
            }
        }
    }

    @Composable private fun Cockpit(haze: HazeState) {
        val scale = LocalViewportAdapt.current.uniformScale
        val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        Box(Modifier.fillMaxSize().navigationBarsPadding()
            .padding(top = 24.dp, bottom = WorkoutUiTokens.liveCockpitCompactHeight(scale) + 8.dp)) {
            Box(Modifier.fillMaxSize().clip(shape).kpknGlassOrFallback(haze, shape).testTag(ModuleTag)) {
                WorkoutSessionCockpit(listOf(squat, row), mapOf("${squat.id}_0" to last),
                    milestones = listOf(SessionMilestone("pr", squat.id, squat.name, "PR", "Nuevo récord de repeticiones",
                        6.0, "95 kg × 6 · antes, 5 repeticiones", "2026-10-16T18:00:00Z")),
                    sessionProgressLabel = "1 / 7", liveEnergySummary = SessionEnergySummary(),
                    sessionNotes = "Mantener el abdomen firme y no apresurar las últimas repeticiones.",
                    sessionSavedNotes = listOf(SessionSavedNote("note", "La primera serie se sintió estable.", "2026-10-16T18:00:00Z")),
                    sessionPhotos = emptyList(), sessionChecklist = listOf(SessionChecklistItem("water", "Beber agua", true),
                        SessionChecklistItem("load", "Revisar la carga de la siguiente serie")),
                    onSessionNotesChange = {}, onSaveSessionNote = {}, onAddSessionPhoto = {}, onRemoveSessionPhoto = {},
                    onAddChecklistItem = {}, onToggleChecklistItem = {}, onRemoveChecklistItem = {}, sessionAccentColor = accent,
                    bodyWeight = 75.0, currentExerciseId = squat.id)
            }
        }
    }

    private fun gameStore() = RestGameRecordStore(context.getSharedPreferences(gamePreferences, Context.MODE_PRIVATE))
    private fun game() = RestGameEngine(initialBest = 7, random = kotlin.random.Random(41)).apply {
        obstacles.add(RestGameObstacle(220f))
        jump()
        repeat(6) { step(0.05f) }
    }
    private companion object {
        const val CanvasTag = "review-rest-canvas"
        const val ModuleTag = "review-rest-module"
        const val CalibrationTag = "review-rest-calibration"
    }
}
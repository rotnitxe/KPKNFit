package com.example.kpkn.screens.workout

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kpkn.data.models.*
import com.example.kpkn.data.workout.RestGameRecordStore
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.workout.components.*
import com.example.kpkn.ui.adapt.LocalViewportAdapt
import com.example.kpkn.ui.adapt.LiveViewportPolicyMath
import com.example.kpkn.ui.components.*
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.theme.KPKNTheme
import com.example.kpkn.ui.tour.TourSeenStore
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.io.File
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith

/**
 * Source-only QA fixture. Production composables and physical window; no density/font override.
 * Captures prove component rendering only, not ViewModel integration, persistence, microphone or timekeeping.
 * Each PNG is UiAutomation.takeScreenshot(), including Android window chrome and all portal layers.
 */
@RunWith(AndroidJUnit4::class)
class Revision14VisualDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val gamePreferences = "qa14-visual-game-${System.nanoTime()}"
    private var authorized = false
    private var scene by mutableStateOf("readiness")
    private lateinit var gameStore: RestGameRecordStore
    private val accent = Color(0xFFF49A6E)
    private val squat = Exercise("qa14-squat", "Sentadilla trasera con barra alta",
        sets = List(4) { ExerciseSet("qa14-sq-$it", targetReps = 6,
            targetRepsRange = RepRange(4, 6), targetRIR = 2, weight = 95.0) })
    private val last = CompletedSet("qa14-last", weight = 95.0, reps = 6, rir = 2)
    private val rest = WorkoutRestModalState(exerciseId = squat.id, exerciseName = squat.name,
        plannedSeconds = 180, suggestedSeconds = 249, activeSeconds = 249,
        startedAtMs = 1_792_170_000_123L, endsAtMs = 1_792_170_249_123L)
    private val suggestion = PendingRestSuggestion(180, 249, squat.name, squat.id, last, null)
    private val next = RestNextSetSummary(2, 4, loadKg = 95.0, repsText = "4–6", intensityText = "RIR 2")

    @Before fun guardAndConfigureWindow() {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        assumeTrue("Only the isolated .dbg application may run this fixture", context.packageName.endsWith(".dbg"))
        assumeTrue("Fixture is bounded to frozen revision 14/code 47", code == 47L && info.versionName == "KPKN Pre Release 14")
        authorized = true
        gameStore = RestGameRecordStore(context.getSharedPreferences(gamePreferences, Context.MODE_PRIVATE))
        rule.mainClock.autoAdvance = false
        rule.activityRule.scenario.onActivity { activity ->
            activity.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            )
            if (Build.VERSION.SDK_INT >= 29) activity.window.isNavigationBarContrastEnforced = false
        }
    }

    @After fun cleanOnlyFixturePreferences() {
        if (authorized) {
            context.getSharedPreferences(gamePreferences, Context.MODE_PRIVATE).edit().clear().commit()
            context.deleteSharedPreferences(gamePreferences)
        }
    }

    @Test fun captureProductionReadinessRestAndSeriesOptions() {
        check(authorized)
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true, LocalRestClockFrozen provides 1.7f) {
                KPKNTheme {
                    val haze = remember { HazeState() }
                    val host = remember { KpknOverlayHostController(tourSeen = object : TourSeenStore {
                        override fun seenIds() = emptySet<String>()
                        override fun markSeen(tourId: String) = Unit
                    }) }
                    CompositionLocalProvider(LocalKpknOverlayHost provides host,
                        LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                        Box(Modifier.fillMaxSize().background(KpknSurface.canvas)) {
                            Box(Modifier.fillMaxSize().hazeSource(haze).background(KpknSurface.canvas))
                            key(scene) {
                                when (scene) {
                                    "readiness" -> WorkoutReadinessSheet(
                                        showReadinessSheet = true, gender = null,
                                        sessionMuscleStartingBatteries = mapOf("Cuádriceps" to 75, "Isquiosurales" to 70,
                                            "Glúteos" to 80, "Bíceps" to 85),
                                        readinessNeuralStart = 88, readinessMuscularStart = 78, readinessSpinalStart = 82,
                                        hazeState = haze,
                                        onSave = { _, _, _, _, _, _, _, _, _ -> },
                                        sessionExercises = listOf(squat), suggestionPillars = listOf("Cuádriceps", "Glúteos"),
                                        forcedGleamProgress = .35f, onDismiss = {}, onOpenVoice = {},
                                    )
                                    "rest-card" -> MeasuredRestCard()
                                    "series-options" -> SeriesOptionsSheet(
                                        exercise = squat, target = SeriesTypeTarget(squat.id, 1, 3),
                                        completedSets = mapOf("${squat.id}_0" to last), omittedSetKeys = emptySet(),
                                        onDismiss = {}, onApply = { _, _ -> }, onEdit = {}, onAddSet = {},
                                        onSkipSet = {}, onDeleteSet = {},
                                    )
                                    else -> RestTimerOverlay(
                                        state = rest, remainingSeconds = 158, hazeState = haze,
                                        pendingRestSuggestion = suggestion, lastCompletedSet = last,
                                        isAdaptiveActive = true, sessionAccentColor = accent,
                                        onDecrease = {}, onIncrease = {}, onSkip = {}, onUseAdaptive = {}, onMinimize = {},
                                        shape = when (scene) {
                                            "rest-track" -> RestClockShape.TRACK
                                            "rest-spiral" -> RestClockShape.SPIRAL
                                            else -> RestClockShape.TOWER
                                        },
                                        headline = RestHeadline(squat.name, "Serie 1 de 4 completada"), nextSet = next,
                                        clockActive = false, showIntro = scene == "rest-intro", onIntroDone = {},
                                        recordStore = gameStore, initiallyPlaying = scene == "game-picker",
                                    )
                                }
                            }
                            KpknOverlayHostContent(host)
                        }
                    }
                }
            }
        }
        settle()
        rule.onNodeWithTag("readiness-voice-fab", useUnmergedTree = true).assertIsDisplayed()
        capture("01-readiness")
        openChannel("ENERGIA")
        capture("02-readiness-energy")
        backToReadiness()
        openChannel("COLUMNA")
        capture("03-readiness-spine")
        backToReadiness()
        openChannel("MUSCULOS")
        rule.onNodeWithTag("ring-adjust-body", useUnmergedTree = true).assertIsDisplayed()
        // The component may initially select the back for glute-focused sessions.
        rule.onNodeWithText("Frente").performClick()
        settle()
        rule.onNodeWithText("Frente").assertIsSelected()
        capture("04-readiness-muscles-front")
        rule.onNodeWithText("Espalda").performClick()
        settle()
        capture("05-readiness-muscles-back")
        backToReadiness()
        capture("06-readiness-return")
        listOf(
            "rest-intro" to "07-rest-intro",
            "rest-tower" to "08-rest-tower",
            "rest-track" to "09-rest-track",
            "rest-spiral" to "10-rest-spiral",
            "rest-card" to "11-rest-card",
            "series-options" to "13-series-options",
            "game-picker" to "12-game-picker",
        ).forEach { (target, name) ->
            rule.runOnIdle { scene = target }
            settle()
            // Preserve the actual physical state even when a visibility assertion fails.
            // In particular, do not "repair" a clipped/transparent arcade by advancing the clock again.
            capture(name)
            when (target) {
                "rest-intro" -> rule.onNodeWithTag("rest-intro-done").assertIsDisplayed()
                "rest-card" -> {
                    rule.waitUntil(5_000) { rule.onAllNodesWithTag("qa14-calibration", useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
                    rule.onNodeWithTag("qa14-rest-card", useUnmergedTree = true).assertIsDisplayed()
                }
                "game-picker" -> {
                    listOf("runner", "flappy", "shooter").forEach { game ->
                        rule.onNodeWithTag("rest-game-$game", useUnmergedTree = true).assertIsDisplayed()
                    }
                    rule.onNodeWithTag("arcade-rest-clock", useUnmergedTree = true).assertTextEquals("2:38")
                    rule.onNodeWithTag("rest-game-runner", useUnmergedTree = true)
                        .performTouchInput { click(center) }
                    settle()
                    rule.onNodeWithTag("rest-game-restart", useUnmergedTree = true).assertIsDisplayed()
                    rule.onNodeWithTag("arcade-rest-clock", useUnmergedTree = true).assertTextEquals("2:38")
                    capture("14-runner-after-physical-tap")
                }
                "series-options" -> rule.onNodeWithText("Aplicar").assertIsDisplayed()
            }
        }
    }

    private fun openChannel(channel: String) {
        rule.onNodeWithTag("readiness-legend-$channel", useUnmergedTree = true).performClick()
        settle()
        rule.onNodeWithTag("ring-adjust-back", useUnmergedTree = true).assertIsDisplayed()
    }

    private fun backToReadiness() {
        rule.onNodeWithTag("ring-adjust-back", useUnmergedTree = true).performClick()
        settle()
        rule.onNodeWithTag("readiness-voice-fab", useUnmergedTree = true).assertIsDisplayed()
    }

    private fun settle() {
        rule.mainClock.advanceTimeBy(900)
        rule.waitForIdle()
    }

    @Composable private fun MeasuredRestCard() {
        val measuredHeight = remember { mutableIntStateOf(0) }
        CompositionLocalProvider(LocalLivePagerWorkingSetVisualHeightPx provides measuredHeight) {
            Box(Modifier.testTag("qa14-rest-card")) {
              LiveSessionCardFrame {
                RestLiveCard(
                    remainingSeconds = 158, totalSeconds = 249, sessionAccentColor = accent,
                    restState = rest, pendingRestSuggestion = suggestion, lastSetOutcome = null, lastCompletedSet = last,
                    onDecrease = {}, onIncrease = {}, onSkip = {}, onUseAdaptive = {}, onExpand = {},
                    isAdaptiveActive = true, shape = RestClockShape.TOWER, nextSet = next,
                    clockActive = false, recordStore = gameStore,
                )
              }
            }
        }
    }

    private fun capture(name: String) {
        check(authorized && context.packageName.endsWith(".dbg"))
        instrumentation.waitForIdleSync()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot()) { "No physical window screenshot for $name" }
        val output = File(checkNotNull(context.getExternalFilesDir(null)), "qa14-visual").apply {
            assertTrue("Create the single flat QA folder", exists() || mkdirs())
        }
        try {
            File(output, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            File(output, "$name-semantics.txt").writeText(runCatching {
                rule.onRoot(useUnmergedTree = true).printToString()
            }.getOrElse { "Semantics dump failed: ${it.javaClass.name}: ${it.message}" })
            val config = context.resources.configuration
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            File(output, "$name.json").writeText(JSONObject().apply {
                put("capture", name); put("scene", scene); put("package", context.packageName)
                put("versionName", info.versionName); put("versionCode", 47)
                put("captureMethod", "UiAutomation.takeScreenshot")
                put("fixture", "production-composable-isolated-component")
                put("model", Build.MODEL); put("sdk", Build.VERSION.SDK_INT)
                put("widthPx", bitmap.width); put("heightPx", bitmap.height)
                put("density", context.resources.displayMetrics.density); put("fontScale", config.fontScale)
                put("customDensityOverride", false); put("customFontScaleOverride", false)
                put("localWizardReducedMotion", true); put("composeClockAutoAdvance", false)
                put("restClockFrozenSeconds", 1.7); put("restRemainingFixtureSeconds", 158)
                put("clockActive", false); put("timestampMs", System.currentTimeMillis())
                put("scopeLimit", "Rendering only; no real session, clock, persistence, microphone or Bluetooth acceptance")
            }.toString(2))
        } finally { bitmap.recycle() }
    }
}

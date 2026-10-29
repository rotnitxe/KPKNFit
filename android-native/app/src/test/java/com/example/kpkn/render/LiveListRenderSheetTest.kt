package com.example.kpkn.render

import android.app.Application
import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.models.WarmupSetDefinition
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.workout.LiveSessionListView
import com.example.kpkn.screens.workout.LiveSessionVmFixture
import com.example.kpkn.screens.workout.LocalLiveSessionCompactLayout
import com.example.kpkn.screens.workout.RestTimerKind
import com.example.kpkn.screens.workout.WorkoutRestModalState
import com.example.kpkn.screens.workout.WorkoutStepRules
import com.example.kpkn.screens.workout.WorkoutUiState
import com.example.kpkn.screens.workout.WorkoutViewModel
import com.example.kpkn.screens.workout.components.LocalRestClockFrozen
import com.example.kpkn.testsupport.RoomTestRule
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.theme.KPKNTheme
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
 * HOJA DE RENDER de la vista Lista de la sesión en vivo (pedido del usuario del 29-oct): ejercicio con movilidad, aproximaciones y
 * series a medio registrar; una aproximación abierta con sus kg editables; el descanso de aproximación desplegado y minimizado, y el de
 * una serie efectiva minimizado; y un ejercicio terminado plegado con el siguiente desplegado. No comprueba nada: escribe PNG de
 * 360 × 740 dp en `KPKN_RENDER_DIR` (sin esa variable se salta), con los composables REALES y sin animaciones.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h880dp-xxhdpi")
class LiveListRenderSheetTest {
    private val room = RoomTestRule()
    private val compose = createComposeRule()
    @get:Rule val chain: RuleChain = RuleChain.outerRule(room).around(compose)
    private val rule get() = compose

    private var view: View? = null

    private val squat = Exercise(
        id = "sq", name = "Sentadilla trasera con barra alta", trainingMode = TrainingMode.REPS, restTime = 120,
        mobilitySeries = listOf(
            MobilitySeries(id = "m1", name = "Movilidad de tobillo", sets = 2, reps = "10", bodyZones = listOf("ankle")),
            MobilitySeries(id = "m2", name = "Apertura de cadera", sets = 1, durationSeconds = 40),
        ),
        warmupSets = listOf(
            WarmupSetDefinition(id = "w1", percentageOfWorkingWeight = 0.4, targetReps = 8, restBetween = 60),
            WarmupSetDefinition(id = "w2", percentageOfWorkingWeight = 0.6, targetReps = 5, restBetween = 60),
            WarmupSetDefinition(id = "w3", percentageOfWorkingWeight = 0.8, targetReps = 3, restBetween = 90),
        ),
        sets = List(3) { index ->
            ExerciseSet(id = "sq-${index + 1}", targetReps = 8, weight = 82.5, targetRIR = 2,
                intensityMode = IntensityMode.RIR, loadModeV2 = LoadModeV2.LOAD, unitModeV2 = UnitModeV2.REPS)
        },
    )
    private val bench = Exercise(
        id = "bp", name = "Press banca", trainingMode = TrainingMode.REPS, restTime = 90,
        warmupSets = listOf(WarmupSetDefinition(id = "bw1", percentageOfWorkingWeight = 50.0, targetReps = 8, restBetween = 60)),
        sets = List(3) { index ->
            ExerciseSet(id = "bp-${index + 1}", targetReps = 10, weight = 62.5, targetRIR = 2,
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
    private val exercises = listOf(squat, bench, row)

    private val w1Key = WorkoutStepRules.warmupStepKey("sq", "w1")
    private val w2Key = WorkoutStepRules.warmupStepKey("sq", "w2")
    private val w3Key = WorkoutStepRules.warmupStepKey("sq", "w3")

    /** Sentadilla a medio hacer: la movilidad hecha, A1 y A2 registradas (A2 con menos kg de los sugeridos) y la serie 1 registrada. */
    private fun WorkoutUiState.midSquat(): WorkoutUiState = copy(
        mobilityCompletedExerciseIds = setOf(WorkoutStepRules.mobilityStepKey("sq", "m1"), WorkoutStepRules.mobilityStepKey("sq", "m2")),
        warmupCompletedExerciseIds = setOf(w1Key, w2Key),
        completedSets = mapOf(
            w1Key to CompletedSet(id = w1Key, weight = 32.5, reps = 8, isWarmup = true),
            w2Key to CompletedSet(id = w2Key, weight = 47.5, reps = 5, isWarmup = true),
            "sq_0" to CompletedSet(id = "sq_0", weight = 82.5, reps = 8, rir = 2),
        ),
        activeStepKey = WorkoutStepRules.workingStepKey("sq", 1),
        currentSetIdx = 1,
    )

    @Test fun midSession() = listScene("live-list-01-mid-session") { it.midSquat() }

    @Test fun warmupEditorOpen() = listScene("live-list-02-warmup-editor") { state ->
        state.midSquat().copy(
            warmupCompletedExerciseIds = setOf(w1Key),
            completedSets = mapOf(w1Key to CompletedSet(id = w1Key, weight = 32.5, reps = 8, isWarmup = true)),
            activeStepKey = w2Key,
            currentSetIdx = 0,
        )
    }

    @Test fun warmupRestExpanded() = listScene("live-list-03-rest-expanded", warmupRest = 42) { state ->
        state.midSquat().copy(
            warmupCompletedExerciseIds = setOf(w1Key),
            completedSets = mapOf(w1Key to CompletedSet(id = w1Key, weight = 32.5, reps = 8, isWarmup = true)),
            activeStepKey = w2Key,
            currentSetIdx = 0,
            isRestTimerRunning = true,
            isRestMinimized = false,
            restModalState = WorkoutRestModalState(exerciseId = "sq", exerciseName = "Sentadilla", kind = RestTimerKind.WARMUP,
                warmupSetId = "w1", plannedSeconds = 60, activeSeconds = 60),
        )
    }

    @Test fun warmupRestMinimized() = listScene("live-list-04-rest-minimized", warmupRest = 42) { state ->
        state.midSquat().copy(
            warmupCompletedExerciseIds = setOf(w1Key),
            completedSets = mapOf(w1Key to CompletedSet(id = w1Key, weight = 32.5, reps = 8, isWarmup = true)),
            activeStepKey = w2Key,
            currentSetIdx = 0,
            isRestTimerRunning = true,
            isRestMinimized = true,
            restModalState = WorkoutRestModalState(exerciseId = "sq", exerciseName = "Sentadilla", kind = RestTimerKind.WARMUP,
                warmupSetId = "w1", plannedSeconds = 60, activeSeconds = 60),
        )
    }

    @Test fun workingRestMinimized() = listScene("live-list-05-working-rest-minimized", workingRest = 73) { state ->
        state.midSquat().copy(
            isRestTimerRunning = true,
            isRestMinimized = true,
            setJustLoggedKey = "sq_0",
            restModalState = WorkoutRestModalState(exerciseId = "sq", exerciseName = "Sentadilla trasera", kind = RestTimerKind.STANDARD,
                plannedSeconds = 120, activeSeconds = 120),
        )
    }

    @Test fun finishedFoldedNextOpen() = listScene("live-list-06-folded-next", current = "bp") { state ->
        state.copy(
            mobilityCompletedExerciseIds = setOf("sq"),
            warmupCompletedExerciseIds = setOf("sq"),
            completedSets = (0 until 3).associate { "sq_$it" to CompletedSet(id = "sq_$it", weight = 82.5, reps = 8, rir = 2) },
            activeStepKey = WorkoutStepRules.warmupStepKey("bp", "bw1"),
            currentExerciseIdx = 1,
            currentSetIdx = 0,
        )
    }

    private fun listScene(
        name: String,
        current: String = "sq",
        warmupRest: Int? = null,
        workingRest: Int? = null,
        state: (WorkoutUiState) -> WorkoutUiState,
    ) {
        assumeTrue(!System.getenv("KPKN_RENDER_DIR").isNullOrBlank())
        Dispatchers.setMain(UnconfinedTestDispatcher())
        LiveSessionVmFixture.resetTrainingObservationRepository()
        val fixture = LiveSessionVmFixture(Session("live-list", "Pierna · Semana 1", exercises = exercises))
        try {
            val vm = runBlocking { fixture.prepare(); fixture.open() }
            Dispatchers.resetMain()
            val shown = state(vm.uiState.value)
            val sessionExercises = shown.session!!.allExercises()
            scene(name) { ListUnderTest(vm, shown, sessionExercises, current, warmupRest, workingRest) }
        } finally {
            try {
                fixture.close(); LiveSessionVmFixture.resetTrainingObservationRepository()
                ProgramRepository.closeInstance(); KpknDatabase.closeInstance()
            } finally {
                runCatching { Dispatchers.resetMain() }
            }
        }
    }

    @Composable
    private fun ListUnderTest(
        vm: WorkoutViewModel,
        state: WorkoutUiState,
        sessionExercises: List<Exercise>,
        current: String,
        warmupRest: Int?,
        workingRest: Int?,
    ) {
        CompositionLocalProvider(LocalLiveSessionCompactLayout provides true) {
            LiveSessionListView(
                uiState = state, exercises = sessionExercises, currentExerciseId = current,
                accentColor = Color(0xFFF49A6E), warmupWorkingWeight = null, viewModel = vm,
                topInset = 16.dp,
                warmupRestRemaining = warmupRest, warmupRestTotal = warmupRest?.let { 60 },
                warmupRestIsMobility = false,
                workingRestRemaining = workingRest, workingRestTotal = workingRest?.let { 120 },
            )
        }
    }

    private fun scene(name: String, height: Int = 740, content: @Composable () -> Unit) {
        rule.setContent {
            view = LocalView.current
            CompositionLocalProvider(
                LocalWizardReducedMotion provides true, LocalKpknOverlayHost provides null, LocalRestClockFrozen provides 1.7f,
            ) {
                KPKNTheme { MaterialTheme {
                    Box(Modifier.requiredSize(360.dp, height.dp).background(Color.Black).testTag("list-scene")) { content() }
                } }
            }
        }
        rule.waitForIdle()
        val folder = File(checkNotNull(System.getenv("KPKN_RENDER_DIR"))).apply { mkdirs() }
        val bounds = rule.onNodeWithTag("list-scene").fetchSemanticsNode().boundsInRoot
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

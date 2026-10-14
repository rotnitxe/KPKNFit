package com.example.kpkn.screens.workout

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.data.models.CardioBlockType
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioExecutionStatus
import com.example.kpkn.data.models.CardioIntervalBlock
import com.example.kpkn.data.models.CardioTimerState
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.screens.workout.components.MobilityPhaseLiveCard
import com.example.kpkn.screens.workout.components.WarmupPhaseLiveCard
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * Calentamiento, movilidad y cardio en vivo (KPKN LS5b): la cabecera con su dosis, las acciones de los temporizadores y de saltar, y el
 * GPS con su unidad. Las cifras de distancia se miran con el locale de EE. UU. para que el punto decimal sea fijo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
class WorkoutPrepAndCardioTest {

    @get:Rule
    val rule = createComposeRule()

    private var previousLocale: Locale? = null

    @Before
    fun fijarLocale() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun restaurarLocale() {
        previousLocale?.let { Locale.setDefault(it) }
    }

    @Test
    fun elCalentamientoMuestraLaAproximacionYLaDosis() {
        rule.setContent {
            WarmupPhaseLiveCard(
                rows = WorkoutPrepSamples.WARMUP_ROWS,
                onToggleComplete = { _, _, _ -> },
                onSkip = {},
                onContinue = {},
            )
        }

        rule.onNodeWithText("Aproximación 1 de 3", ignoreCase = true).assertExists()
        rule.onNodeWithText("60 kg", substring = true).assertExists()
    }

    @Test
    fun empezarDelTemporizadorLlamaAEmpezar() {
        var started = false
        rule.setContent {
            MobilityPhaseLiveCard(
                items = WorkoutPrepSamples.MOBILITY_ITEMS,
                completedStepKeys = emptySet(),
                remainingSeconds = 300,
                isTimerRunning = false,
                onToggleComplete = { _, _ -> },
                onStartTimer = { started = true },
                onPauseTimer = {},
                onAddTimerSeconds = {},
                onResetTimer = {},
                onSkip = {},
                onContinue = {},
            )
        }

        rule.onNodeWithText("Empezar").performClick()
        rule.waitForIdle()

        assertTrue(started)
    }

    @Test
    fun saltarCalentamientoLlamaASaltar() {
        var skipped = false
        rule.setContent {
            WarmupPhaseLiveCard(
                rows = WorkoutPrepSamples.WARMUP_ROWS,
                onToggleComplete = { _, _, _ -> },
                onSkip = { skipped = true },
                onContinue = {},
            )
        }

        rule.onNodeWithText("Saltar calentamiento").performClick()
        rule.waitForIdle()

        assertTrue(skipped)
    }

    @Test
    fun elCardioConGpsMuestraDistanciaYRitmoConSuUnidad() {
        rule.setContent {
            CardioLiveCard(
                details = WorkoutPrepSamples.CARDIO_GPS,
                completedSet = null,
                accentColor = Color.White,
                executionState = WorkoutPrepSamples.GPS_RUNNING_TIMER,
                gpsState = WorkoutPrepSamples.GPS_RUNNING,
                onRecord = { _, _, _ -> false },
            )
        }

        rule.onNodeWithContentDescription("3.42 km").assertExists()
        rule.onNodeWithContentDescription("5:30 /km").assertExists()
    }

    @Test
    fun pausarLlamaAPausar() {
        var paused = false
        rule.setContent {
            CardioLiveCard(
                details = CardioDetails(type = CardioType.TREADMILL, targetDurationSeconds = 1200),
                completedSet = null,
                accentColor = Color.White,
                executionState = CardioTimerState(
                    exerciseId = "cinta",
                    totalSeconds = 1200,
                    remainingSeconds = 900,
                    elapsedSeconds = 300,
                    status = CardioExecutionStatus.RUNNING,
                ),
                onPauseTimer = { paused = true },
                onRecord = { _, _, _ -> false },
            )
        }

        rule.onNodeWithText("Pausar").performClick()
        rule.waitForIdle()

        assertTrue(paused)
    }

    @Test
    fun losIntervalosDicenEnQueTramoVamos() {
        val intervalos = CardioDetails(
            type = CardioType.BIKE_STATIONARY,
            targetDurationSeconds = null,
            intervalBlocks = listOf(
                CardioIntervalBlock(id = "e1", type = CardioBlockType.WORK, durationSeconds = 40),
                CardioIntervalBlock(id = "p1", type = CardioBlockType.RECOVER, durationSeconds = 20),
                CardioIntervalBlock(id = "e2", type = CardioBlockType.WORK, durationSeconds = 40),
                CardioIntervalBlock(id = "p2", type = CardioBlockType.RECOVER, durationSeconds = 20),
            ),
            intervalRounds = 2,
        )
        rule.setContent {
            CardioLiveCard(
                details = intervalos,
                completedSet = null,
                accentColor = Color.White,
                executionState = WorkoutPrepSamples.INTERVALS_TIMER,
                onRecord = { _, _, _ -> false },
            )
        }

        rule.onNodeWithText("Intervalo 3 de 8 · Trabajo").assertExists()
    }
}

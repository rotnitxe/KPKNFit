package com.example.kpkn.render

import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioExecutionStatus
import com.example.kpkn.data.models.CardioTimerState
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.PostExerciseFeedback
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.screens.workout.CardioLiveCard
import com.example.kpkn.screens.workout.ExerciseDrainOverlayCard
import com.example.kpkn.screens.workout.ExerciseDrainOverlayChannel
import com.example.kpkn.screens.workout.ExerciseDrainOverlayItem
import com.example.kpkn.screens.workout.ExerciseDrainOverlayState
import com.example.kpkn.screens.workout.PendingRestSuggestion
import com.example.kpkn.screens.workout.PostExerciseFeedbackPanel
import com.example.kpkn.screens.workout.QuickDiscomfortSheetBody
import com.example.kpkn.screens.workout.RelatorAssistAction
import com.example.kpkn.screens.workout.RelatorAssistActionKind
import com.example.kpkn.screens.workout.RestTimerKind
import com.example.kpkn.screens.workout.WorkoutLiveRelatorLine
import com.example.kpkn.screens.workout.WorkoutRestModalState
import com.example.kpkn.screens.workout.components.RestLiveCard
import com.example.kpkn.services.cardio.CardioGpsState
import com.example.kpkn.services.cardio.CardioGpsStatus
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

private const val REST_TAG = "lr1c1-descanso-pagina"
private const val CARDIO_TAG = "lr1c1-cardio"
private const val RELATOR_TAG = "lr1c1-relator"
private const val GASTO_TAG = "lr1c1-gasto"
private const val FEEDBACK_TAG = "lr1c1-feedback"
private const val MOLESTIAS_TAG = "lr1c1-molestias"

private val ACCENT = Color(0xFFF49A6E)

// Datos de ejemplo: una sentadilla con un descanso de 3:14 en la tarjeta de la página (272 dp, como en el teléfono).
private val SAMPLE_REST_STATE = WorkoutRestModalState(
    exerciseName = "Sentadilla trasera",
    plannedSeconds = 180,
    suggestedSeconds = 230,
    activeSeconds = 230,
)

private val SAMPLE_SUPERSET_STATE = WorkoutRestModalState(
    exerciseName = "Press banca",
    kind = RestTimerKind.SUPERSET_INTRA,
    plannedSeconds = 90,
    suggestedSeconds = 90,
    activeSeconds = 90,
)

private val SAMPLE_LAST_SET = CompletedSet(id = "last", weight = 100.5, reps = 6)

private val SAMPLE_SUGGESTION = PendingRestSuggestion(
    plannedSeconds = 180,
    adaptiveSeconds = 230,
    exerciseName = "Sentadilla trasera",
    exerciseId = "sq",
    lastSet = SAMPLE_LAST_SET,
    advancedFeedback = null,
)

// Serie sin intensidad planeada: antes la hoja de feedback preguntaba «Qué tan intenso fue»; ahora el RIR/RPE se anota en cada serie.
private val SAMPLE_FEEDBACK_SET = ExerciseSet(
    id = "sq-1",
    targetReps = 8,
    weight = 80.0,
    loadModeV2 = LoadModeV2.LOAD,
    unitModeV2 = UnitModeV2.REPS,
)

private val SAMPLE_FEEDBACK_EXERCISE = Exercise(
    id = "sq",
    name = "Sentadilla trasera",
    trainingMode = TrainingMode.REPS,
    sets = listOf(SAMPLE_FEEDBACK_SET),
)

/**
 * HOJA DE RENDER del descanso en página, el cardio, el relator, el gasto, el feedback y las molestias (LR1c1). No comprueba nada:
 * escribe PNG. Solo corre con la variable de entorno `KPKN_RENDER_DIR` apuntando a una carpeta; sin ella se salta. Dibuja las piezas
 * REALES con datos de ejemplo, a 360 dp, con la letra al 100 % y al 130 %.
 *
 * La confirmación del cardio no sale aquí: es un diálogo con ventana propia (`KpknAlertDialog`, envoltorio que no se toca) y la hoja
 * solo captura la vista. La hoja de «Reportar molestias» pinta su cuerpo sin la hoja `KpknSheet` (envoltorio).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class LiveSessionRestCardioRenderSheetTest {

    companion object {
        /** Sin `KPKN_RENDER_DIR` la clase se salta antes de arrancar el entorno con gráficos nativos. */
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }
    }

    @get:Rule
    val rule = createComposeRule()

    /** La vista de Compose de la actividad, para dibujarla a un bitmap (igual que LiveSessionRefineRenderSheetTest). */
    private var hostView: View? = null

    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    @Test
    fun descansoPagina100() {
        onlyWhenAsked()
        drawRest(superserie = false, fontScale = 1f, name = "descanso_pagina_100")
    }

    @Test
    fun descansoPagina130() {
        onlyWhenAsked()
        drawRest(superserie = false, fontScale = 1.3f, name = "descanso_pagina_130")
    }

    @Test
    fun descansoSuperserie100() {
        onlyWhenAsked()
        drawRest(superserie = true, fontScale = 1f, name = "descanso_superserie_100")
    }

    @Test
    fun descansoSuperserie130() {
        onlyWhenAsked()
        drawRest(superserie = true, fontScale = 1.3f, name = "descanso_superserie_130")
    }

    @Test
    fun cardioMarcha100() {
        onlyWhenAsked()
        drawCardio(fontScale = 1f, name = "cardio_marcha_100")
    }

    @Test
    fun cardioMarcha130() {
        onlyWhenAsked()
        drawCardio(fontScale = 1.3f, name = "cardio_marcha_130")
    }

    @Test
    fun relator100() {
        onlyWhenAsked()
        drawRelator(fontScale = 1f, name = "relator_100")
    }

    @Test
    fun relator130() {
        onlyWhenAsked()
        drawRelator(fontScale = 1.3f, name = "relator_130")
    }

    @Test
    fun gasto100() {
        onlyWhenAsked()
        drawGasto(fontScale = 1f, name = "gasto_100")
    }

    @Test
    fun gasto130() {
        onlyWhenAsked()
        drawGasto(fontScale = 1.3f, name = "gasto_130")
    }

    @Test
    fun feedback100() {
        onlyWhenAsked()
        drawFeedback(fontScale = 1f, name = "feedback_100")
    }

    @Test
    fun feedback130() {
        onlyWhenAsked()
        drawFeedback(fontScale = 1.3f, name = "feedback_130")
    }

    @Test
    fun molestias100() {
        onlyWhenAsked()
        drawMolestias(fontScale = 1f, name = "molestias_100")
    }

    @Test
    fun molestias130() {
        onlyWhenAsked()
        drawMolestias(fontScale = 1.3f, name = "molestias_130")
    }

    private fun drawRest(superserie: Boolean, fontScale: Float, name: String) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            hostView = LocalView.current
            // 3 px por dp: pantalla de 360 dp. La tarjeta de la página mide 272 dp, como en el teléfono.
            CompositionLocalProvider(LocalDensity provides Density(3f, fontScale)) {
                KPKNTheme {
                    MaterialTheme {
                        Box(
                            Modifier
                                .width(360.dp)
                                .background(Color.Black)
                                .testTag(REST_TAG)
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            // Altura de la tarjeta de la serie medida en LR1a (388 dp al 100 %, 418 dp al 130 %): el paginador la usa para el descanso.
                            Box(Modifier.width(272.dp).height(if (fontScale > 1f) 418.dp else 388.dp)) {
                                if (superserie) {
                                    RestLiveCard(
                                        remainingSeconds = 62,
                                        totalSeconds = 90,
                                        sessionAccentColor = ACCENT,
                                        restState = SAMPLE_SUPERSET_STATE,
                                        pendingRestSuggestion = SAMPLE_SUGGESTION,
                                        lastSetOutcome = null,
                                        lastCompletedSet = CompletedSet(id = "last", weight = 100.5, reps = 6, isFailure = true),
                                        onDecrease = {},
                                        onIncrease = {},
                                        onSkip = {},
                                        onUseAdaptive = {},
                                        onExpand = {},
                                        skipExerciseLabel = "Saltar series restantes de Press banca",
                                        onSkipExercise = {},
                                    )
                                } else {
                                    RestLiveCard(
                                        remainingSeconds = 194,
                                        totalSeconds = 230,
                                        sessionAccentColor = ACCENT,
                                        restState = SAMPLE_REST_STATE,
                                        pendingRestSuggestion = SAMPLE_SUGGESTION,
                                        lastSetOutcome = null,
                                        lastCompletedSet = SAMPLE_LAST_SET,
                                        onDecrease = {},
                                        onIncrease = {},
                                        onSkip = {},
                                        onUseAdaptive = {},
                                        onExpand = {},
                                        skipExerciseLabel = "Saltar series restantes de Sentadilla trasera",
                                        onSkipExercise = {},
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        save(REST_TAG, name)
    }

    private fun drawCardio(fontScale: Float, name: String) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            hostView = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(3f, fontScale)) {
                KPKNTheme {
                    MaterialTheme {
                        Box(
                            Modifier
                                .width(360.dp)
                                .height(640.dp)
                                .background(Color.Black)
                                .testTag(CARDIO_TAG),
                        ) {
                            CardioLiveCard(
                                details = CardioDetails(
                                    type = CardioType.RUN_OUTDOOR,
                                    targetDurationSeconds = 1200,
                                    requiresGps = true,
                                    supportsDistance = true,
                                ),
                                completedSet = null,
                                accentColor = ACCENT,
                                modifier = Modifier.fillMaxSize(),
                                executionState = CardioTimerState(
                                    exerciseId = "run",
                                    totalSeconds = 1200,
                                    remainingSeconds = 600,
                                    elapsedSeconds = 600,
                                    status = CardioExecutionStatus.RUNNING,
                                ),
                                liveHeartRateBpm = 152,
                                gpsState = CardioGpsState(
                                    status = CardioGpsStatus.RECORDING,
                                    distanceMeters = 1234.0,
                                    elapsedActiveSeconds = 600L,
                                    paceSecondsPerKm = 325,
                                    kmSplitPaces = listOf(338, 322),
                                    pointCount = 80,
                                ),
                                onRecord = { _, _, _ -> true },
                            )
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        save(CARDIO_TAG, name)
    }

    private fun drawRelator(fontScale: Float, name: String) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            hostView = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(3f, fontScale)) {
                KPKNTheme {
                    MaterialTheme {
                        Box(
                            Modifier
                                .width(360.dp)
                                .background(Color.Black)
                                .testTag(RELATOR_TAG)
                                .padding(vertical = 16.dp, horizontal = 16.dp),
                        ) {
                            WorkoutLiveRelatorLine(
                                text = "Buen trabajo: tres series de 8 con 62,5 kg. Ver la serie 3 para ajustar el peso.",
                                phaseKey = "p1",
                                actions = listOf(
                                    RelatorAssistAction(
                                        kind = RelatorAssistActionKind.JUMP_TO_SET,
                                        label = "Ver la serie 3",
                                    ),
                                ),
                                accentColor = ACCENT,
                            )
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        save(RELATOR_TAG, name)
    }

    private fun drawGasto(fontScale: Float, name: String) {
        // Sin avance del reloj: la barra queda en su valor inicial, no en el vaciado de la animación.
        rule.mainClock.autoAdvance = false
        rule.setContent {
            hostView = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(3f, fontScale)) {
                KPKNTheme {
                    MaterialTheme {
                        Box(
                            Modifier
                                .width(360.dp)
                                .background(Color.Black)
                                .testTag(GASTO_TAG)
                                .padding(16.dp),
                        ) {
                            ExerciseDrainOverlayCard(
                                state = ExerciseDrainOverlayState(
                                    key = 1L,
                                    exerciseName = "Sentadilla trasera",
                                    items = listOf(
                                        ExerciseDrainOverlayItem(
                                            label = "Energía",
                                            delta = 18,
                                            channel = ExerciseDrainOverlayChannel.ENERGY,
                                        ),
                                        ExerciseDrainOverlayItem(
                                            label = "Columna",
                                            delta = 11,
                                            channel = ExerciseDrainOverlayChannel.BACK,
                                        ),
                                        ExerciseDrainOverlayItem(
                                            label = "Músculos",
                                            delta = 24,
                                            channel = ExerciseDrainOverlayChannel.MUSCLE,
                                        ),
                                    ),
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        save(GASTO_TAG, name)
    }

    private fun drawFeedback(fontScale: Float, name: String) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            hostView = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(3f, fontScale)) {
                KPKNTheme {
                    MaterialTheme {
                        Box(
                            Modifier
                                .width(360.dp)
                                .background(Color.Black)
                                .testTag(FEEDBACK_TAG)
                                .padding(16.dp),
                        ) {
                            PostExerciseFeedbackPanel(
                                feedbackExercises = listOf(SAMPLE_FEEDBACK_EXERCISE),
                                postExerciseFeedbackByExerciseId = mapOf(
                                    "sq" to PostExerciseFeedback(
                                        exerciseId = "sq",
                                        exerciseName = "Sentadilla trasera",
                                        technicalQuality = 7,
                                        discomfortIds = listOf("shoulder_anterior"),
                                        perceivedIntensityRpe = 8.0,
                                        perceivedFailure = false,
                                    ),
                                ),
                                sessionAccentColor = ACCENT,
                                canonicalKeyOf = { exercise -> exercise.id },
                                onSave = {},
                                initialDiscomfortQuery = "hombro",
                            )
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        save(FEEDBACK_TAG, name)
    }

    private fun drawMolestias(fontScale: Float, name: String) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            hostView = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(3f, fontScale)) {
                KPKNTheme {
                    MaterialTheme {
                        Box(
                            Modifier
                                .width(360.dp)
                                .height(620.dp)
                                .background(Color.Black)
                                .testTag(MOLESTIAS_TAG)
                                .padding(top = 16.dp),
                        ) {
                            QuickDiscomfortSheetBody(
                                exerciseName = "Sentadilla trasera",
                                onSave = {},
                                initialQuery = "hombro",
                                initialSelectedIds = listOf("shoulder_anterior"),
                            )
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        save(MOLESTIAS_TAG, name)
    }

    /** Recorta la hoja a la caja con la etiqueta [tag] y la guarda como PNG en `KPKN_RENDER_DIR`. */
    private fun save(tag: String, name: String) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        val bounds = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val view = hostView!!
        val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread {
            val canvas = android.graphics.Canvas(full)
            canvas.drawColor(android.graphics.Color.BLACK)
            view.draw(canvas)
        }
        val left = bounds.left.toInt().coerceIn(0, full.width - 1)
        val top = bounds.top.toInt().coerceIn(0, full.height - 1)
        val width = bounds.width.toInt().coerceIn(1, full.width - left)
        val height = bounds.height.toInt().coerceIn(1, full.height - top)
        val cropped = Bitmap.createBitmap(full, left, top, width, height)
        FileOutputStream(File(dir, "$name.png")).use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

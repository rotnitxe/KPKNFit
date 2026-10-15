package com.example.kpkn.render

import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseReadiness
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.workout.PendingRestSuggestion
import com.example.kpkn.screens.workout.RecordActionHolder
import com.example.kpkn.screens.workout.WeightSuggestion
import com.example.kpkn.screens.workout.WorkoutHeaderBar
import com.example.kpkn.screens.workout.WorkoutRestModalState
import com.example.kpkn.screens.workout.components.RestTimerOverlay
import com.example.kpkn.screens.workout.components.SetInputCardV2
import com.example.kpkn.screens.workout.components.WorkoutRecordFab
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.theme.KPKNTheme
import dev.chrisbanes.haze.HazeState
import org.junit.Assert.assertTrue
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

private const val SERIE_TAG = "lr1a-serie-sheet"
private const val DESCANSO_TAG = "lr1a-descanso-sheet"
private const val DESCANSO_HEIGHT_DP = 760

// Geometría a 360 dp (LR1a2): etiquetas de los nodos, altura a la que va la tarjeta en el teléfono y escala de densidad.
private const val CHECK_TAG = "lr1a2-check-sheet"
private const val CARD_TAG = "lr1a2-card"
private const val HEADER_TAG = "lr1a2-header-sheet"
private const val CARD_TOP_DP = 215
private const val DENSITY = 3f

// Datos de ejemplo tomados de la captura original (Peso Muerto Convencional, 100,5 kg, 6 reps, RIR 2).
private val SAMPLE_SET = ExerciseSet(
    id = "pm-1",
    targetReps = 6,
    targetRIR = 2,
    intensityMode = IntensityMode.RIR,
    weight = 100.5,
    loadModeV2 = LoadModeV2.LOAD,
    unitModeV2 = UnitModeV2.REPS,
)

private val SAMPLE_EXERCISE = Exercise(
    id = "pm",
    name = "Peso Muerto Convencional",
    trainingMode = TrainingMode.REPS,
    sets = listOf(SAMPLE_SET),
)

private val SAMPLE_REST_STATE = WorkoutRestModalState(
    exerciseName = "Peso Muerto Convencional",
    plannedSeconds = 180,
    suggestedSeconds = 230,
    activeSeconds = 180,
)

private val SAMPLE_SUGGESTION = PendingRestSuggestion(
    plannedSeconds = 180,
    adaptiveSeconds = 230,
    exerciseName = "Peso Muerto Convencional",
    exerciseId = "pm",
    lastSet = CompletedSet(id = "last", weight = 100.5, reps = 6),
    advancedFeedback = null,
)

// Readiness de ejemplo (82 %): el chip verde de la cabecera del teléfono.
private val SAMPLE_READINESS = ExerciseReadiness(
    exerciseId = "sentadilla",
    exerciseName = "Sentadilla Trasera con Barra Alta",
    overallScore = 82,
    muscularComponent = 82,
    cnsComponent = 82,
    spinalComponent = 82,
    articularComponent = 82,
    structuralComponent = 82,
    relatedArticular = emptyList(),
    muscularWeight = 1.0,
    cnsWeight = 1.0,
    spinalWeight = 1.0,
    articularWeight = 1.0,
    setsPenaltyFactor = 1.0,
    intensityPenaltyFactor = 1.0,
    ermProximityFactor = 1.0,
    patternId = null,
    involvedMuscleIds = emptyList(),
)

/**
 * HOJA DE RENDER del refinado de la serie y del descanso (LR1a). Escribe PNG; las dos pruebas de geometría a 360 dp (LR1a2) además
 * comprueban el ✓ frente al texto y los chips de la cabecera. Solo corre con la variable de
 * entorno `KPKN_RENDER_DIR` apuntando a una carpeta; sin ella se salta. Dibuja las piezas REALES de la sesión (`SetInputCardV2` y
 * `RestTimerOverlay`) con datos de ejemplo, a 360 dp, con la letra al 100 % y al 130 %. El bloque de feedback del descanso es una
 * pieza de ejemplo: el real lo pone el anfitrión de la sesión.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class LiveSessionRefineRenderSheetTest {

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

    /** La vista de Compose de la actividad, para dibujarla a un bitmap (igual que RestClockTextRenderSheetTest). */
    private var hostView: View? = null

    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    @Test
    fun serieConSugerencia100() {
        onlyWhenAsked()
        drawSerie(withSuggestion = true, fontScale = 1f, name = "serie_sugerencia_100")
    }

    @Test
    fun serieConSugerencia130() {
        onlyWhenAsked()
        drawSerie(withSuggestion = true, fontScale = 1.3f, name = "serie_sugerencia_130")
    }

    @Test
    fun serieSinSugerencia100() {
        onlyWhenAsked()
        drawSerie(withSuggestion = false, fontScale = 1f, name = "serie_sin_sugerencia_100")
    }

    @Test
    fun serieSinSugerencia130() {
        onlyWhenAsked()
        drawSerie(withSuggestion = false, fontScale = 1.3f, name = "serie_sin_sugerencia_130")
    }

    @Test
    fun descansoNormal100() {
        onlyWhenAsked()
        drawDescanso(withFeedback = false, fontScale = 1f, name = "descanso_normal_100")
    }

    @Test
    fun descansoNormal130() {
        onlyWhenAsked()
        drawDescanso(withFeedback = false, fontScale = 1.3f, name = "descanso_normal_130")
    }

    @Test
    fun descansoConFeedback100() {
        onlyWhenAsked()
        drawDescanso(withFeedback = true, fontScale = 1f, name = "descanso_feedback_100")
    }

    @Test
    fun descansoConFeedback130() {
        onlyWhenAsked()
        drawDescanso(withFeedback = true, fontScale = 1.3f, name = "descanso_feedback_130")
    }

    /**
     * Geometría de la serie a 360 × 880 dp (LR1a2): la tarjeta a la altura del teléfono y el ✓ real encima, en su sitio. Mide el borde
     * derecho del texto «Ver ejercicio/Fotos» frente al borde izquierdo del ✓, y la base de la tarjeta frente al cajón plegado (652 dp).
     * Escribe el PNG de la pantalla y falla si el texto pisa el ✓ o si la tarjeta queda a menos de 12 dp del cajón.
     */
    @Test
    fun serieCheckGeometria360() {
        onlyWhenAsked()
        rule.setContent {
            hostView = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(DENSITY, 1f)) {
                KPKNTheme {
                    MaterialTheme {
                        // El ✓ va anclado a 16 dp del borde derecho y a 241 dp del borde inferior (su base queda sobre el cajón plegado).
                        Box(
                            Modifier
                                .width(360.dp)
                                .height(880.dp)
                                .background(Color.Black)
                                .testTag(CHECK_TAG),
                        ) {
                            Box(
                                Modifier
                                    .width(272.dp)
                                    .align(Alignment.TopCenter)
                                    .padding(top = CARD_TOP_DP.dp)
                                    .testTag(CARD_TAG),
                            ) {
                                SetInputCardV2(
                                    exercise = SAMPLE_EXERCISE,
                                    setIndex = 0,
                                    currentSet = SAMPLE_SET,
                                    ghostSet = null,
                                    weightSuggestion = WeightSuggestion(suggestedWeight = 98.0, reason = "Ajuste por RIR"),
                                    previousSessionFirstSetWeight = 122.5,
                                    sessionAccentColor = Color(0xFFF49A6E),
                                    initialBodyWeight = 80.0,
                                    recordActionHolder = RecordActionHolder(),
                                    onShowHistory = {},
                                    onSetBodyWeight = {},
                                    onRecordV2 = { _, _, _, _, _, _, _, _, _, _ -> },
                                )
                            }
                            WorkoutRecordFab(
                                sessionAccentColor = Color(0xFFF49A6E),
                                isUpdateMode = false,
                                enabled = true,
                                onClick = {},
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 16.dp, bottom = 241.dp),
                            )
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        // El texto va sin fusionar con la píldora (que es clicable) para medir el propio Text.
        val textoDerecho = rule.onNodeWithText("Ver ejercicio/Fotos", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.right / DENSITY
        val checkIzquierdo = rule.onAllNodesWithContentDescription("Registrar serie").onFirst()
            .fetchSemanticsNode().boundsInRoot.left / DENSITY
        val baseTarjeta = rule.onNodeWithTag(CARD_TAG).fetchSemanticsNode().boundsInRoot.bottom / DENSITY
        println("LR1a2 geometria 360 dp: texto derecho $textoDerecho; check izquierdo $checkIzquierdo; base tarjeta $baseTarjeta")
        save(CHECK_TAG, "serie_check_360_100")
        assertTrue(
            "el texto «Ver ejercicio/Fotos» acaba en $textoDerecho dp y el ✓ empieza en $checkIzquierdo dp",
            textoDerecho < checkIzquierdo,
        )
        assertTrue(
            "la base de la tarjeta queda en $baseTarjeta dp: a menos de 12 dp del cajón plegado (652 dp)",
            baseTarjeta <= 640f,
        )
    }

    /**
     * Cabecera a 360 dp (LR1a2): la fila de chips (cronómetro, «82 %», «Sin etiquetas») ocupa todo el ancho, debajo del subtítulo. Mide
     * que los chips de texto fijo acaban dentro del margen de 16 dp (344 dp) y escribe el PNG de la cabecera.
     */
    @Test
    fun cabeceraChips360() {
        onlyWhenAsked()
        rule.setContent {
            hostView = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(DENSITY, 1f)) {
                KPKNTheme {
                    MaterialTheme {
                        Box(
                            Modifier
                                .width(360.dp)
                                .height(150.dp)
                                .background(Color.Black)
                                .testTag(HEADER_TAG),
                        ) {
                            WorkoutHeaderBar(
                                exerciseName = "Sentadilla Trasera con Barra Alta",
                                protocolLabel = "Accesorio",
                                sessionName = "Pierna A",
                                groupName = null,
                                startTimeMs = System.currentTimeMillis() - 3_586_000L,
                                isComplete = false,
                                background = null,
                                sessionTimeRemainingSeconds = null,
                                onAdjustTimeLimit = {},
                                exerciseReadiness = SAMPLE_READINESS,
                                hasCreatedTags = false,
                                onOpenTagList = {},
                            )
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        // Borde derecho de cada chip = borde del texto + su relleno lateral (8 dp en «Sin etiquetas», 9 dp en «82 %»).
        val sinEtiquetasDerecho = rule.onNodeWithText("Sin etiquetas", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.right / DENSITY + 8f
        // El % va con espacio inseparable (U+00A0), igual que en WorkoutHeaderBar.kt.
        val readinessDerecho = rule.onNodeWithText("82 %", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.right / DENSITY + 9f
        println("LR1a2 cabecera 360 dp: sin etiquetas acaba en $sinEtiquetasDerecho; 82 % acaba en $readinessDerecho")
        save(HEADER_TAG, "cabecera_chips_360_100")
        assertTrue("«Sin etiquetas» acaba en $sinEtiquetasDerecho dp, fuera del margen de 344 dp", sinEtiquetasDerecho <= 344f)
        assertTrue("«82 %» acaba en $readinessDerecho dp, fuera del margen de 344 dp", readinessDerecho <= 344f)
    }

    private fun drawSerie(withSuggestion: Boolean, fontScale: Float, name: String) {
        rule.setContent {
            hostView = LocalView.current
            // 3 px por dp: pantalla de 360 dp.
            CompositionLocalProvider(LocalDensity provides Density(3f, fontScale)) {
                KPKNTheme {
                    MaterialTheme {
                        // En el teléfono la tarjeta mide unos 272 dp y a sus lados asoman las vecinas: así se miden los botones.
                        Box(
                            Modifier
                                .width(360.dp)
                                .background(Color.Black)
                                .padding(vertical = 16.dp)
                                .testTag(SERIE_TAG),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            Box(Modifier.width(272.dp)) {
                                SetInputCardV2(
                                exercise = SAMPLE_EXERCISE,
                                setIndex = 0,
                                currentSet = SAMPLE_SET,
                                ghostSet = null,
                                weightSuggestion = if (withSuggestion) {
                                    WeightSuggestion(suggestedWeight = 98.0, reason = "Ajuste por RIR")
                                } else {
                                    null
                                },
                                previousSessionFirstSetWeight = 122.5,
                                sessionAccentColor = Color(0xFFF49A6E),
                                initialBodyWeight = 80.0,
                                recordActionHolder = RecordActionHolder(),
                                onShowHistory = {},
                                onSetBodyWeight = {},
                                onRecordV2 = { _, _, _, _, _, _, _, _, _, _ -> },
                                )
                            }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        save(SERIE_TAG, name)
    }

    private fun drawDescanso(withFeedback: Boolean, fontScale: Float, name: String) {
        val feedbackSlot: (@Composable () -> Unit)? = if (withFeedback) {
            { SampleFeedback() }
        } else {
            null
        }
        rule.setContent {
            hostView = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(3f, fontScale)) {
                KPKNTheme {
                    MaterialTheme {
                        Box(
                            Modifier
                                .width(360.dp)
                                .height(DESCANSO_HEIGHT_DP.dp)
                                .background(Color.Black)
                                .testTag(DESCANSO_TAG),
                        ) {
                            RestTimerOverlay(
                                state = SAMPLE_REST_STATE,
                                remainingSeconds = 180,
                                hazeState = HazeState(),
                                pendingRestSuggestion = SAMPLE_SUGGESTION,
                                lastCompletedSet = CompletedSet(id = "last", weight = 100.5, reps = 6),
                                sessionAccentColor = Color(0xFFF49A6E),
                                onDecrease = {},
                                onIncrease = {},
                                onSkip = {},
                                onUseAdaptive = {},
                                skipExerciseLabel = "Saltar series restantes de Peso Muerto Convencional",
                                onSkipExercise = {},
                                postExerciseFeedbackContent = feedbackSlot,
                                feedbackExerciseCount = 1,
                            )
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        save(DESCANSO_TAG, name)
    }

    /** Pieza de ejemplo del feedback posterior al ejercicio: un módulo con una pregunta y el botón principal. */
    @Composable
    private fun SampleFeedback() {
        KpknModule(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(
                    text = "¿Cómo ha ido el ejercicio?",
                    style = KpknType.bodyStrong,
                    color = WizardColors.text,
                )
                Spacer(Modifier.height(16.dp))
                KpknPrimaryPill(text = "Continuar", onClick = {}, modifier = Modifier.fillMaxWidth())
            }
        }
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

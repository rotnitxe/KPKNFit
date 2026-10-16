package com.example.kpkn.render

import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.AugeSnapshot
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.data.models.RecoveryBand
import com.example.kpkn.data.models.RecoveryChannelId
import com.example.kpkn.data.models.RecoveryChannelSnapshot
import com.example.kpkn.data.models.RecoveryDashboard
import com.example.kpkn.data.models.VoiceCaptureMode
import com.example.kpkn.data.models.WarmupSetDefinition
import com.example.kpkn.screens.workout.components.MobilityPhaseLiveCard
import com.example.kpkn.screens.workout.components.VoiceCaptureModeDialog
import com.example.kpkn.screens.workout.components.WarmupPhaseLiveCard
import com.example.kpkn.screens.workout.components.WarmupPhaseRow
import com.example.kpkn.screens.workout.components.WorkoutMobilityChecklistItem
import com.example.kpkn.screens.workout.components.WorkoutReadinessSheet
import com.example.kpkn.ui.theme.KPKNTheme
import dev.chrisbanes.haze.HazeState
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

private const val ESTADO_TAG = "lr1b-estado"
private const val APROX_TAG = "lr1b-aproximacion"
private const val APROX_DESCANSO_TAG = "lr1b-aproximacion-descanso"
private const val MOV_TAG = "lr1b-movilidad"
private const val VOZ_TAG = "lr1b-voz"

/** Acento de la sesión: solo para el progreso (la barra del descanso) y lo activo. */
private val SESSION_ACCENT = Color(0xFFF49A6E)

// Datos de ejemplo tomados de la captura original de la hoja de estado: músculos 90 %, energía 89 %, columna 83 %.
private val SAMPLE_AUGE = AugeSnapshot(
    dashboard = RecoveryDashboard(
        overallScore = 89,
        headline = "Listo para entrenar",
        summary = "Recuperación buena en todos los canales.",
        recommendation = "Entrena con normalidad.",
        confidenceLabel = "Alta",
        channels = listOf(
            RecoveryChannelSnapshot(
                id = RecoveryChannelId.MUSCULAR,
                title = "Músculos",
                shortTitle = "Músculos",
                score = 90,
                band = RecoveryBand.NORMAL,
                description = "Recuperación muscular.",
                action = "Volumen normal y buena ejecución.",
            ),
            RecoveryChannelSnapshot(
                id = RecoveryChannelId.SYSTEM,
                title = "Energía",
                shortTitle = "Energía",
                score = 89,
                band = RecoveryBand.HIGH,
                description = "Sistema nervioso.",
                action = "Buen día para intensidad y coordinación.",
            ),
            RecoveryChannelSnapshot(
                id = RecoveryChannelId.STRUCTURE,
                title = "Columna",
                shortTitle = "Columna",
                score = 83,
                band = RecoveryBand.NORMAL,
                description = "Carga estructural.",
                action = "Carga estructural normal con buena técnica.",
            ),
        ),
    ),
)

/** Aproximación de ejemplo: dos series hechas (una con peso distinto del sugerido) y una pendiente. */
private fun aproxRows(): List<WarmupPhaseRow> = listOf(
    WarmupPhaseRow(
        exerciseId = "sent",
        exerciseBadge = null,
        index = 0,
        warmup = WarmupSetDefinition(id = "w1", percentageOfWorkingWeight = 0.5, targetReps = 8),
        suggestedWeightKg = 50.0,
        actualWeightKg = 50.0,
        isCompleted = true,
    ),
    WarmupPhaseRow(
        exerciseId = "sent",
        exerciseBadge = null,
        index = 1,
        warmup = WarmupSetDefinition(id = "w2", percentageOfWorkingWeight = 0.7, targetReps = 5),
        suggestedWeightKg = 70.0,
        actualWeightKg = 72.5,
        isCompleted = true,
    ),
    WarmupPhaseRow(
        exerciseId = "sent",
        exerciseBadge = null,
        index = 2,
        warmup = WarmupSetDefinition(id = "w3", percentageOfWorkingWeight = 0.85, targetReps = 3),
        suggestedWeightKg = 85.0,
        actualWeightKg = null,
        isCompleted = false,
    ),
)

/** Movilidad de ejemplo: tres series, la primera hecha; una con tiempo y dos con repeticiones. */
private fun movItems(): List<WorkoutMobilityChecklistItem> = listOf(
    WorkoutMobilityChecklistItem(
        exerciseId = "sent",
        exerciseName = "Sentadilla trasera",
        mobility = MobilitySeries(id = "m1", name = "Gato-vaca", reps = "10"),
        stepKey = "m1",
    ),
    WorkoutMobilityChecklistItem(
        exerciseId = "sent",
        exerciseName = "Sentadilla trasera",
        mobility = MobilitySeries(id = "m2", name = "Movilidad de cadera", durationSeconds = 30),
        stepKey = "m2",
    ),
    WorkoutMobilityChecklistItem(
        exerciseId = "sent",
        exerciseName = "Sentadilla trasera",
        mobility = MobilitySeries(id = "m3", name = "Bisagra con palo", reps = "8"),
        stepKey = "m3",
    ),
)

/**
 * HOJA DE RENDER de lo de antes de la primera serie (LR1b): la hoja de estado entera, la aproximación (con dos series hechas y una
 * pendiente, y con el descanso en curso), la movilidad y el diálogo de modo de voz. No comprueba nada: escribe PNG. Solo corre con la
 * variable de entorno `KPKN_RENDER_DIR`; sin ella se salta. Dibuja las piezas REALES con datos de ejemplo, a 360 dp, con la letra al
 * 100 % y al 130 %.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class LiveSessionPrepRenderSheetTest {

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
    fun estado100() {
        onlyWhenAsked()
        drawEstado(fontScale = 1f, name = "estado_100")
    }

    @Test
    fun estado130() {
        onlyWhenAsked()
        drawEstado(fontScale = 1.3f, name = "estado_130")
    }

    @Test
    fun estadoAlto100() {
        onlyWhenAsked()
        drawEstado(fontScale = 1f, name = "estado_alto_100", heightDp = 1400)
    }

    @Test
    fun estadoAlto130() {
        onlyWhenAsked()
        drawEstado(fontScale = 1.3f, name = "estado_alto_130", heightDp = 1400)
    }

    @Test
    fun aproximacion100() {
        onlyWhenAsked()
        drawAproximacion(fontScale = 1f, name = "aproximacion_100", inlineRest = false)
    }

    @Test
    fun aproximacion130() {
        onlyWhenAsked()
        drawAproximacion(fontScale = 1.3f, name = "aproximacion_130", inlineRest = false)
    }

    @Test
    fun aproximacionDescanso100() {
        onlyWhenAsked()
        drawAproximacion(fontScale = 1f, name = "aproximacion_descanso_100", inlineRest = true)
    }

    @Test
    fun movilidad100() {
        onlyWhenAsked()
        drawMovilidad(fontScale = 1f, name = "movilidad_100")
    }

    @Test
    fun movilidad130() {
        onlyWhenAsked()
        drawMovilidad(fontScale = 1.3f, name = "movilidad_130")
    }

    @Test
    fun voz100() {
        onlyWhenAsked()
        drawVoz(fontScale = 1f, name = "voz_100")
    }

    @Test
    fun voz130() {
        onlyWhenAsked()
        drawVoz(fontScale = 1.3f, name = "voz_130")
    }

    @Test
    fun estadoDestelloFotograma1_100() {
        onlyWhenAsked()
        drawEstado(fontScale = 1f, name = "estado_destello_fotograma1_100", gleamProgress = 0.35f)
    }

    @Test
    fun estadoDestelloFotograma2_100() {
        onlyWhenAsked()
        drawEstado(fontScale = 1f, name = "estado_destello_fotograma2_100", gleamProgress = 0.75f)
    }

    @Test
    fun estadoDestelloFotograma1_130() {
        onlyWhenAsked()
        drawEstado(fontScale = 1.3f, name = "estado_destello_fotograma1_130", gleamProgress = 0.35f)
    }

    @Test
    fun estadoDestelloFotograma2_130() {
        onlyWhenAsked()
        drawEstado(fontScale = 1.3f, name = "estado_destello_fotograma2_130", gleamProgress = 0.75f)
    }

    private fun drawEstado(fontScale: Float, name: String, heightDp: Int = 880, gleamProgress: Float? = null) {
        rule.setContent {
            hostView = LocalView.current
            // 3 px por dp: pantalla de 360 dp.
            CompositionLocalProvider(LocalDensity provides Density(3f, fontScale)) {
                KPKNTheme {
                    MaterialTheme {
                        Box(
                            Modifier
                                .width(360.dp)
                                .height(heightDp.dp)
                                .background(Color.Black)
                                .testTag(ESTADO_TAG),
                        ) {
                            WorkoutReadinessSheet(
                                showReadinessSheet = true,
                                gender = null,
                                sessionMuscleStartingBatteries = mapOf("Pecho" to 92, "Cuádriceps" to 88),
                                readinessNeuralStart = 89,
                                readinessMuscularStart = 90,
                                readinessSpinalStart = 83,
                                hazeState = HazeState(),
                                onSave = { _, _, _, _, _, _, _, _, _ -> },
                                augeSnapshot = SAMPLE_AUGE,
                                voiceSessionEnabled = true,
                                voiceCaptureMode = VoiceCaptureMode.HANDS_FREE,
                                forcedGleamProgress = gleamProgress,
                            )
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        save(ESTADO_TAG, name)
    }

    private fun drawAproximacion(fontScale: Float, name: String, inlineRest: Boolean) {
        val tag = if (inlineRest) APROX_DESCANSO_TAG else APROX_TAG
        rule.setContent {
            hostView = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(3f, fontScale)) {
                KPKNTheme {
                    MaterialTheme {
                        Box(
                            Modifier
                                .width(360.dp)
                                .height(460.dp)
                                .background(Color.Black)
                                .testTag(tag),
                            contentAlignment = Alignment.Center,
                        ) {
                            // La tarjeta mide unos 272 × 420 dp en el teléfono (como la de la serie en LR1a).
                            Box(Modifier.width(272.dp).height(420.dp)) {
                                WarmupPhaseLiveCard(
                                    rows = aproxRows(),
                                    sessionAccentColor = SESSION_ACCENT,
                                    onToggleComplete = { _, _, _ -> },
                                    onSkip = {},
                                    onContinue = {},
                                    onAddSet = {},
                                    inlineRestRemainingSeconds = if (inlineRest) 75 else null,
                                    inlineRestTotalSeconds = if (inlineRest) 120 else null,
                                    onSkipInlineRest = {},
                                )
                            }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        save(tag, name)
    }

    private fun drawMovilidad(fontScale: Float, name: String) {
        rule.setContent {
            hostView = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(3f, fontScale)) {
                KPKNTheme {
                    MaterialTheme {
                        Box(
                            Modifier
                                .width(360.dp)
                                .height(460.dp)
                                .background(Color.Black)
                                .testTag(MOV_TAG),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(Modifier.width(272.dp).height(420.dp)) {
                                MobilityPhaseLiveCard(
                                    items = movItems(),
                                    completedStepKeys = setOf("m1"),
                                    remainingSeconds = 245,
                                    totalMinutes = 5,
                                    isTimerRunning = true,
                                    sessionAccentColor = SESSION_ACCENT,
                                    onToggleComplete = { _, _ -> },
                                    onStartTimer = {},
                                    onPauseTimer = {},
                                    onAddTimerSeconds = {},
                                    onResetTimer = {},
                                    onSkip = {},
                                    onContinue = {},
                                )
                            }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        save(MOV_TAG, name)
    }

    private fun drawVoz(fontScale: Float, name: String) {
        rule.setContent {
            hostView = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(3f, fontScale)) {
                KPKNTheme {
                    MaterialTheme {
                        Box(
                            Modifier
                                .width(360.dp)
                                .height(880.dp)
                                .background(Color.Black)
                                .testTag(VOZ_TAG),
                        ) {
                            VoiceCaptureModeDialog(onChosen = {}, onDismissRequest = {})
                        }
                    }
                }
            }
        }
        // Se elige «Modo Música» para ver la opción elegida en tinta.
        rule.onNodeWithText("Modo Música", substring = true).performClick()
        rule.waitForIdle()
        save(VOZ_TAG, name)
    }

    /** Recorta la pantalla a la caja con la etiqueta [tag] y la guarda como PNG en `KPKN_RENDER_DIR`. */
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

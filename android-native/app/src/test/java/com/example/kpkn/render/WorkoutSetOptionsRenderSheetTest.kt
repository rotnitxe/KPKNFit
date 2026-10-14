package com.example.kpkn.render

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.HistoryColorV2
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.domain.sessionassistant.UltraFastExerciseChange
import com.example.kpkn.domain.sessionassistant.UltraFastPreview
import com.example.kpkn.domain.sessionassistant.UltraFastReason
import com.example.kpkn.domain.sessionassistant.UltraFastSupersetChange
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.workout.ExerciseHistoryEntry
import com.example.kpkn.screens.workout.ExerciseTagSheetContent
import com.example.kpkn.screens.workout.WorkoutExerciseHistoryContent
import com.example.kpkn.screens.workout.WorkoutRmCalcContent
import com.example.kpkn.screens.workout.components.SetAdjustmentBody
import com.example.kpkn.screens.workout.components.SetTechniqueBody
import com.example.kpkn.screens.workout.components.TechniqueCheckRow
import com.example.kpkn.screens.workout.components.UltraFastPreviewBody
import com.example.kpkn.screens.workout.design.WorkoutSetOptionsBody
import com.example.kpkn.ui.design.KpknType
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
import java.time.LocalDate

/**
 * HOJA DE DIBUJO de «Opciones de la serie», la técnica y las hojas de contexto (KPKN LS7b): no comprueba nada, escribe PNG a 360 dp,
 * al 100 % y al 130 % de letra. Solo corre con `KPKN_RENDER_DIR`; sin ella se salta. Las piezas van sueltas, con datos de ejemplo.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = Application::class)
class WorkoutSetOptionsRenderSheetTest {

    companion object {
        /** Sin `KPKN_RENDER_DIR` la clase se salta antes de arrancar Robolectric con gráficos nativos. */
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }
    }

    @get:Rule
    val rule = createComposeRule()

    /** Escena que se dibuja ahora, su escala de letra y una revisión que fuerza una composición nueva en cada captura. */
    private var scene by mutableStateOf("opciones")
    private var fontScale by mutableStateOf(1f)
    private var revision by mutableStateOf(0)

    /** La vista de Compose, para dibujarla a un bitmap. */
    private var hostView: View? = null

    private val noop: () -> Unit = {}
    private val noopInt: (Int) -> Unit = {}

    /** El recuadro de cada escena: sin caja, sobre negro, con el recuadro «sheet» para recortar la captura. */
    @Composable
    private fun Frame(content: @Composable () -> Unit) {
        Column(
            modifier = Modifier
                .testTag("sheet")
                .fillMaxWidth()
                .background(WizardColors.background)
                .padding(top = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            content()
        }
    }

    /** Margen lateral de las piezas que no lo traen (las hojas lo dan con su cuerpo). */
    @Composable
    private fun Pad(content: @Composable () -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            content()
        }
    }

    @Composable
    private fun Scene() {
        when (scene) {
            "opciones" -> Frame {
                Pad {
                    Text("Opciones de la serie", style = KpknType.title, color = WizardColors.text)
                    WorkoutSetOptionsBody(
                        showRom = true,
                        romPercent = 85,
                        onRomChange = noopInt,
                        assistedReps = 2,
                        maxAssistedReps = 8,
                        onAssistedStep = noopInt,
                        partialReps = 1,
                        onPartialStep = noopInt,
                        onOpenTechnique = noop,
                        accentColor = SymbolPalette.musculo,
                    )
                }
            }
            "tecnica" -> Frame {
                SetTechniqueBody(
                    dropEnabled = true,
                    restPauseEnabled = false,
                    onToggleDrop = noop,
                    onToggleRestPause = noop,
                    dropRows = listOf(
                        TechniqueCheckRow(weightText = "80", repsText = "8"),
                        TechniqueCheckRow(weightText = "70", repsText = "6"),
                    ),
                    onDropRowsChange = {},
                    onAddDrop = noop,
                    restPauseRows = emptyList(),
                    onRestPauseRowsChange = {},
                    onAddRestPause = noop,
                    noteText = "Agarre neutro; el último drop, sin llegar al fallo.",
                    onNoteChange = {},
                    isFailedSet = false,
                    onFailedSetChange = {},
                    showPrMediaShortcut = false,
                    onOpenPrMedia = null,
                    onFlipBack = noop,
                    onSave = noop,
                )
            }
            "ajuste" -> Frame {
                SetAdjustmentBody(
                    subtitle = "Remo sentado · Serie 2 de 4",
                    readinessScore = 72,
                    readinessLabel = "Buena",
                    readinessDetail = "Músc. 70% · SNC 75% · Col. 68%",
                    readinessTone = WizardColors.done,
                    plannedMode = "Carga normal",
                    plannedValue = "80",
                    plannedUnit = "kg",
                    suggestedMode = "Carga normal",
                    suggestedValue = "76",
                    suggestedUnit = "kg",
                    suggestedDelta = "−5%",
                    erm = "eRM promedio: ≈ 92 kg",
                    severity = 0.5f,
                    onSeverityChange = {},
                    applyLabel = "Aplicar 76 kg",
                    onApply = noop,
                    onDismiss = noop,
                )
            }
            "ultra" -> Frame {
                UltraFastPreviewBody(
                    preview = sampleUltraFast(),
                    savedSeconds = 720,
                    manualOverrides = mapOf("sq" to false),
                    setCounts = emptyMap(),
                    onSetCountChange = { _, _ -> },
                    onToggleOverride = {},
                    onConfirm = noop,
                    onDismiss = noop,
                )
            }
            "historial" -> Frame {
                Pad {
                    WorkoutExerciseHistoryContent(
                        history = sampleHistory(),
                        activeTag = "Máquina SmartFit",
                        maxEntries = null,
                        maxSetsPerEntry = 6,
                    )
                }
            }
            "rm" -> Frame {
                Pad {
                    WorkoutRmCalcContent(
                        bodyWeight = null,
                        currentLoadMode = LoadModeV2.LOAD,
                        onWeightSelected = null,
                        sessionAccentColor = SymbolPalette.musculo,
                        initialWeightText = "80",
                        initialRepsText = "8",
                    )
                }
            }
            "etiquetas" -> Frame {
                Pad {
                    ExerciseTagSheetContent(
                        currentTag = "Máquina SmartFit",
                        onTagSet = {},
                        onDismiss = noop,
                        userTags = listOf("Máquina SmartFit", "Barra vacía", "Mancuerna"),
                        suggestedTag = "Barra vacía",
                        profiles = emptyList(),
                    )
                }
            }
        }
    }

    private fun sampleUltraFast(): UltraFastPreview = UltraFastPreview(
        beforeSeconds = 2700,
        afterSeconds = 1980,
        savedSeconds = 720,
        perExercise = listOf(
            UltraFastExerciseChange(
                exerciseId = "sq",
                exerciseName = "Sentadilla trasera",
                beforeSets = 4,
                afterSets = 3,
                beforeTechnique = "Normal",
                afterTechnique = "Normal",
                reason = UltraFastReason.PROTECTED_BASIC,
                wasDensified = false,
                wasReduced = true,
            ),
            UltraFastExerciseChange(
                exerciseId = "ext",
                exerciseName = "Extensión de cuádriceps",
                beforeSets = 3,
                afterSets = 3,
                beforeTechnique = "Normal",
                afterTechnique = "Drop-set",
                reason = UltraFastReason.ISOLATION_DENSIFIED,
                wasDensified = true,
                wasReduced = false,
            ),
        ),
        supersets = listOf(
            UltraFastSupersetChange(
                exerciseIdA = "pe",
                exerciseIdB = "tr",
                nameA = "Press de pecho",
                nameB = "Jalón al pecho",
                machineKey = "Máquina",
            ),
        ),
    )

    private fun sampleHistory(): List<ExerciseHistoryEntry> {
        val today = LocalDate.now()
        return listOf(
            ExerciseHistoryEntry(
                date = today.minusDays(1).toString(),
                sets = listOf(
                    CompletedSet(id = "h1", weight = 80.0, reps = 8, rpe = 8.0),
                    CompletedSet(id = "h2", weight = 80.0, reps = 7, rpe = 9.0, partialReps = 2),
                    CompletedSet(id = "h3", weight = 75.0, reps = 8, isFailedSet = true, failureReason = "Rango incompleto"),
                ),
                e1rm = 98.4,
                tag = "Máquina SmartFit",
                latestHistoryColor = HistoryColorV2.YELLOW,
            ),
            ExerciseHistoryEntry(
                date = today.minusDays(9).toString(),
                sets = listOf(
                    CompletedSet(id = "h4", weight = 20.0, reps = 10, side = "left"),
                    CompletedSet(id = "h5", weight = 20.0, reps = 9, side = "right", isFailure = true, assistedReps = 1),
                ),
                e1rm = 27.3,
                tag = null,
                latestHistoryColor = HistoryColorV2.NEUTRAL,
            ),
        )
    }

    /** Dibuja la vista entera sobre negro, tal como está ahora. */
    private fun drawView(): Bitmap {
        val view = hostView!!
        val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread {
            val canvas = Canvas(full)
            canvas.drawColor(android.graphics.Color.BLACK)
            view.draw(canvas)
        }
        return full
    }

    private fun crop(full: Bitmap, bounds: Rect): Bitmap {
        val left = bounds.left.toInt().coerceIn(0, full.width - 1)
        val top = bounds.top.toInt().coerceIn(0, full.height - 1)
        val width = bounds.width.toInt().coerceIn(1, full.width - left)
        val height = bounds.height.toInt().coerceIn(1, full.height - top)
        return Bitmap.createBitmap(full, left, top, width, height)
    }

    private fun saveBitmap(name: String, bitmap: Bitmap) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Guarda el recuadro «sheet» tal como se ve ahora. */
    private fun save(name: String) {
        val bounds = rule.onNodeWithTag("sheet").fetchSemanticsNode().boundsInRoot
        saveBitmap(name, crop(drawView(), bounds))
    }

    /** Pinta una escena a 100 % y 130 % de letra, con la escena recompuesta desde cero en cada captura. */
    private fun renderStatic(sceneName: String, file: String) {
        for ((scale, suffix) in listOf(1f to "", 1.3f to "_s130")) {
            rule.runOnIdle {
                scene = sceneName
                fontScale = scale
                revision++
            }
            rule.waitForIdle()
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
            rule.mainClock.advanceTimeBy(400)
            rule.waitForIdle()
            save("$file$suffix")
        }
    }

    @Test
    fun renderWorkoutSetOptionsSheets() {
        assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val base = LocalDensity.current
            hostView = LocalView.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale = fontScale),
                // Movimiento reducido: las capturas son estados finales, sin desplazamientos a mitad de camino.
                LocalWizardReducedMotion provides true,
            ) {
                key(revision) { Scene() }
            }
        }
        renderStatic("opciones", "ls7b_opciones_serie")
        renderStatic("tecnica", "ls7b_tecnica_dos_drops")
        renderStatic("ajuste", "ls7b_ajuste_serie")
        renderStatic("ultra", "ls7b_ultra_rapido")
        renderStatic("historial", "ls7b_historial")
        renderStatic("rm", "ls7b_rm")
        renderStatic("etiquetas", "ls7b_etiquetas")
    }
}

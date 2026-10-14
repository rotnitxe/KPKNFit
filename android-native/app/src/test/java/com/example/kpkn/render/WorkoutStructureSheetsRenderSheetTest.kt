package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnilateralSideOrder
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.workout.ExerciseHistoryContent
import com.example.kpkn.screens.workout.ExerciseHistoryEntry
import com.example.kpkn.screens.workout.WorkoutDrawer
import com.example.kpkn.screens.workout.design.WorkoutSetSettingsBody
import com.example.kpkn.screens.workout.design.WorkoutStructureActionRow
import com.example.kpkn.screens.workout.design.WorkoutStructureDivider
import com.example.kpkn.screens.workout.design.WorkoutSupersetCandidate
import com.example.kpkn.screens.workout.design.WorkoutSupersetCreatorBody
import com.example.kpkn.screens.workout.design.WorkoutSupersetRoundsBody
import com.example.kpkn.screens.workout.design.WorkoutTagSheetBody
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknSurface
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
 * HOJA DE DIBUJO de las hojas de estructura de la sesión en vivo (KPKN LS7a): no comprueba nada, escribe PNG a 360 dp, al 100 % y al
 * 130 % de letra. Solo corre con `KPKN_RENDER_DIR`; sin ella se salta. Cada escena es un [WorkoutDrawer] real con datos de ejemplo: las
 * etiquetas, los ajustes de la serie, la superserie al crearla, «Rondas y descansos», el historial con tres sesiones y una escena con las
 * filas de acción y sus iconos nuevos. Las acciones del ejercicio dentro del host no se dibujan aquí (dependen del ViewModel).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class WorkoutStructureSheetsRenderSheetTest {

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
    private var scene by mutableStateOf("etiquetas")
    private var fontScale by mutableStateOf(1f)
    private var revision by mutableStateOf(0)

    /** La vista de Compose, para dibujarla a un bitmap. */
    private var hostView: View? = null

    private val noop: () -> Unit = {}

    /** Las tres sesiones de muestra: dos de esta semana y una de la anterior, con una serie fallida, otra por lados y un calentamiento. */
    private fun historySamples(): List<ExerciseHistoryEntry> {
        val today = LocalDate.now()
        return listOf(
            ExerciseHistoryEntry(
                date = today.minusDays(1).toString(),
                sets = listOf(
                    CompletedSet(id = "a1", weight = 80.0, reps = 8, rpe = 8.0),
                    CompletedSet(id = "a2", weight = 80.0, reps = 8, rir = 2),
                    CompletedSet(id = "a3", weight = 82.5, reps = 6, rir = 1),
                ),
                e1rm = null,
                tag = "Tirón",
            ),
            ExerciseHistoryEntry(
                date = today.minusDays(3).toString(),
                sets = listOf(
                    CompletedSet(id = "b0", weight = 40.0, reps = 12, isWarmup = true),
                    CompletedSet(id = "b1", weight = 75.0, reps = 10),
                    CompletedSet(
                        id = "b2",
                        weight = 82.5,
                        reps = 5,
                        isFailedSet = true,
                        failureReason = "Serie marcada como fallida",
                    ),
                    CompletedSet(id = "b3", weight = 75.0, reps = 8, partialReps = 2),
                ),
                e1rm = null,
                tag = "Empuje",
                notes = "Pausa de tres segundos abajo.",
            ),
            ExerciseHistoryEntry(
                date = today.minusDays(9).toString(),
                sets = listOf(
                    CompletedSet(id = "c1", weight = 35.0, reps = 10, side = "left"),
                    CompletedSet(id = "c2", weight = 35.0, reps = 10, side = "right"),
                    CompletedSet(id = "c3", weight = 37.5, reps = 8, side = "left"),
                ),
                e1rm = null,
                tag = null,
            ),
        )
    }

    private val candidates = listOf(
        WorkoutSupersetCandidate(id = "a", name = "Remo sentado", subtitle = "Origen"),
        WorkoutSupersetCandidate(id = "b", name = "Curl de bíceps"),
        WorkoutSupersetCandidate(id = "c", name = "Press de hombro"),
        WorkoutSupersetCandidate(id = "d", name = "Elevación lateral"),
    )

    /** Escena con su cajón real: título arriba, la cruz y el contenido de muestra. */
    @Composable
    private fun Scene() {
        Box(
            modifier = Modifier
                .testTag("sheet")
                .width(360.dp)
                .height(780.dp)
                .background(KpknSurface.canvas),
            contentAlignment = Alignment.BottomCenter,
        ) {
            when (scene) {
                "etiquetas" -> WorkoutDrawer(title = "Remo sentado · Etiquetas", onDismiss = noop, hazeState = null) {
                    WorkoutTagSheetBody(
                        currentTag = "Tirón",
                        userTags = listOf("Empuje", "Tirón", "Pierna", "Empuje con mancuernas"),
                        suggestedTag = "Pierna",
                        profiles = emptyList(),
                        onTagSet = {},
                        onDismiss = noop,
                    )
                }
                "ajustes" -> WorkoutDrawer(title = "Remo sentado · Editar series", onDismiss = noop, hazeState = null) {
                    WorkoutSetSettingsBody(
                        trainingMode = TrainingMode.REPS,
                        onTrainingModeChange = {},
                        unilateral = true,
                        onUnilateralChange = {},
                        sideOrder = UnilateralSideOrder.LEFT_RIGHT,
                        onSideOrderChange = {},
                        restBetweenSidesSeconds = 90,
                        onRestBetweenSidesChange = {},
                        smartLoadSummary = "1RM: 100 kg",
                        onOpenSmartLoad = noop,
                    )
                }
                "superserie" -> WorkoutDrawer(title = "Crear superserie", onDismiss = noop, hazeState = null) {
                    WorkoutSupersetCreatorBody(
                        candidates = candidates,
                        anchorId = "a",
                        selectedIds = listOf("a", "b"),
                        onSelectedIdsChange = {},
                        defaultRounds = 3,
                        defaultRestBetween = 60,
                        defaultRestAfter = 120,
                        confirmLabel = "Crear superserie",
                        canOpenCatalog = true,
                        onConfirm = { _, _, _, _ -> },
                        onOpenCatalog = noop,
                        onDismiss = noop,
                    )
                }
                "rondas" -> WorkoutDrawer(title = "Rondas y descansos", onDismiss = noop, hazeState = null) {
                    WorkoutSupersetRoundsBody(
                        rounds = 4,
                        restBetween = 45,
                        restAfter = 150,
                        onSave = { _, _, _ -> },
                        onCancel = noop,
                    )
                }
                "historial" -> WorkoutDrawer(title = "Historial", onDismiss = noop, hazeState = null) {
                    ExerciseHistoryContent(history = historySamples(), activeTag = "Tirón")
                }
                else -> WorkoutDrawer(title = "Remo sentado", onDismiss = noop, hazeState = null) {
                    KpknModuleHeader(label = "Este ejercicio")
                    KpknModule(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                        WorkoutStructureActionRow(icon = KpknLineIcons.History, title = "Historial", onClick = noop)
                        WorkoutStructureDivider()
                        WorkoutStructureActionRow(icon = KpknLineIcons.Tag, title = "Etiquetas", subtitle = "Tirón", onClick = noop)
                        WorkoutStructureDivider()
                        WorkoutStructureActionRow(icon = KpknLineIcons.Edit, title = "Editar", onClick = noop)
                        WorkoutStructureDivider()
                        WorkoutStructureActionRow(icon = KpknLineIcons.Swap, title = "Reemplazar", onClick = noop)
                    }
                    KpknModuleHeader(label = "La sesión")
                    KpknModule(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                        WorkoutStructureActionRow(icon = KpknLineIcons.Plus, title = "Agregar otro", onClick = noop)
                        WorkoutStructureDivider()
                        WorkoutStructureActionRow(icon = KpknLineIcons.Reorder, title = "Reordenar", onClick = noop)
                        WorkoutStructureDivider()
                        WorkoutStructureActionRow(icon = KpknLineIcons.Link, title = "Crear superserie", onClick = noop)
                        WorkoutStructureDivider()
                        WorkoutStructureActionRow(
                            icon = KpknLineIcons.Unlink,
                            title = "Disolver superserie",
                            destructive = true,
                            onClick = noop,
                        )
                    }
                }
            }
        }
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
    fun renderWorkoutStructureSheets() {
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
        renderStatic(sceneName = "etiquetas", file = "ls7a_etiquetas")
        renderStatic(sceneName = "ajustes", file = "ls7a_ajustes_serie")
        renderStatic(sceneName = "superserie", file = "ls7a_superserie_crear")
        renderStatic(sceneName = "rondas", file = "ls7a_superserie_rondas")
        renderStatic(sceneName = "historial", file = "ls7a_historial")
        renderStatic(sceneName = "filas", file = "ls7a_filas_iconos")
    }
}

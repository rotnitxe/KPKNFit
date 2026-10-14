package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.entreno.plan.exercisePatternOf
import com.example.kpkn.screens.sessioneditor.SessionEditorSamples
import com.example.kpkn.screens.sessioneditor.SessionEditorScreenSamples
import com.example.kpkn.screens.sessioneditor.components.SessionEditorEmptyState
import com.example.kpkn.screens.sessioneditor.design.SessionAssistantSheetContent
import com.example.kpkn.screens.sessioneditor.design.SessionAssistantTab
import com.example.kpkn.screens.sessioneditor.design.SessionCardioModule
import com.example.kpkn.screens.sessioneditor.design.SessionEditorBottomIsland
import com.example.kpkn.screens.sessioneditor.design.SessionEditorHeader
import com.example.kpkn.screens.sessioneditor.design.SessionEditorTopActions
import com.example.kpkn.screens.sessioneditor.design.SessionExerciseExpanded
import com.example.kpkn.screens.sessioneditor.design.SessionExerciseRow
import com.example.kpkn.screens.sessioneditor.design.SessionImpactModule
import com.example.kpkn.screens.sessioneditor.design.SessionPartHeader
import com.example.kpkn.screens.sessioneditor.design.SessionPreparationModule
import com.example.kpkn.screens.sessioneditor.design.SessionSupersetCaption
import com.example.kpkn.screens.sessioneditor.sessionExerciseDose
import com.example.kpkn.screens.sessioneditor.sessionExerciseSpokenDose
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknSurface
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

/**
 * HOJA DE DIBUJO de la pantalla entera del editor de sesiones (SE8). No comprueba nada: escribe PNG en `KPKN_RENDER_DIR`. Cada escena es
 * un cuadro de 360 × 740 dp, como el teléfono, con la pantalla desplazada al principio: la sesión de fuerza con dos partes, una superserie
 * y un ejercicio desplegado; la de cardio al inicio y con preparación; la sesión vacía; y el asistente abierto en Impacto. Al 100 % y al
 * 130 % de letra. Sin ViewModel: la pantalla se monta con sus piezas y datos de ejemplo.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class SessionEditorScreenRenderSheetTest {

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

    /** Lo primero de cada prueba: sin `KPKN_RENDER_DIR` no se dibuja nada y la prueba se salta (no falla). */
    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    /** Escena que se dibuja ahora y letra. Fuera de la composición para poder cambiarlas desde la prueba. */
    private var scene by mutableStateOf("fuerza")
    private var fontScale by mutableStateOf(1f)

    /** La vista de Compose, para dibujarla a un bitmap. */
    private var hostView: View? = null

    @Composable
    private fun ExerciseLine(exercise: Exercise, superset: Boolean = false, expanded: Boolean = false) {
        SessionExerciseRow(
            name = exercise.name,
            dose = sessionExerciseDose(exercise.sets, exercise.trainingMode, exercise.restTime) ?: "Sin series",
            expanded = expanded,
            accent = SymbolPalette.musculo,
            imageRes = null,
            pattern = exercisePatternOf(exercise.name),
            onToggle = {},
            onLongPress = {},
            onDragStart = { _: Offset -> },
            onDrag = { _: Offset -> },
            onDragEnd = {},
            onDragCancel = {},
            onDelete = {},
            superset = superset,
            spokenDose = sessionExerciseSpokenDose(exercise.sets, exercise.trainingMode, exercise.restTime),
        )
    }

    @Composable
    private fun PartHeader(name: String, caption: String) {
        SessionPartHeader(
            name = name,
            caption = caption,
            colorId = null,
            collapsed = false,
            hasExercises = true,
            onRename = {},
            onChangeColor = {},
            onToggleCollapse = {},
            onRemove = {},
            onDragStart = { _: Rect?, _: Offset -> },
            onDrag = { _: Float -> },
            onDragEnd = {},
            onDragCancel = {},
        )
    }

    /** La parte «Empuje»: superserie de dos (con su línea) y el tercero desplegado con su tabla y sus opciones. */
    @Composable
    private fun EmpujeModule() {
        val exercises = SessionEditorScreenSamples.empuje
        KpknModule {
            PartHeader(name = "Empuje", caption = "3 ejercicios · 14 series")
            SessionSupersetCaption(text = "Superserie · 2 rondas · 1:30 entre rondas", editing = false, onEdit = {})
            ExerciseLine(exercises[0], superset = true)
            ExerciseLine(exercises[1], superset = true)
            ExerciseLine(exercises[2], expanded = true)
            SessionExerciseExpanded(
                exercise = exercises[2],
                inSuperset = false,
                suppressRest = false,
                relationshipAnchorName = null,
                accentColor = SymbolPalette.musculo,
                coverColor = SymbolPalette.musculo,
                onUpdateExercise = {},
                onUpdateSet = { _, _ -> },
                onAddSet = {},
                onRemoveSet = {},
                onMoveSet = { _, _ -> },
                onOpenReplacePicker = {},
                onOpenRelationshipPicker = {},
                onClearRelationship = {},
                onUpdateRelationshipType = {},
                onUpdateRelationshipNotes = {},
                onOpenSuperset = {},
                onOpenGoal = {},
                onOpenCustomUnit = {},
            )
        }
    }

    @Composable
    private fun TironModule() {
        KpknModule {
            PartHeader(name = "Tirón", caption = "1 ejercicio · 3 series")
            ExerciseLine(SessionEditorSamples.partExercises.last())
        }
    }

    /** Cabecera, acciones de la página, impacto y la pantalla de la sesión. */
    @Composable
    private fun EditorBody() {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // La cabecera trae sus 16 dp laterales; los bloques, el margen de página de 16 dp (como en la pantalla).
                SessionEditorHeader(
                    sessionId = "s-torso",
                    name = "Torso A",
                    description = "Empuje y tirón",
                    navigatorLabel = "Lun · Torso A",
                    exerciseCount = 4,
                    setCount = 14,
                    durationMinutes = 58,
                    onNameCommit = {},
                    onDescriptionCommit = {},
                    onOpenNavigator = {},
                )
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                SessionEditorTopActions(saveStatus = "Guardado", onMore = {}, onMoreBoundsChanged = {})
                when (scene) {
                    "vacia" -> SessionEditorEmptyState(onChooseStrength = {}, onChooseCardio = {})
                    "cardio" -> {
                        // Como en la pantalla: el cardio va en un módulo sin cabecera de parte.
                        KpknModule(contentPadding = PaddingValues(0.dp)) {
                        SessionCardioModule(
                            name = "Cinta",
                            details = SessionEditorScreenSamples.treadmill,
                            expanded = false,
                            onToggle = {},
                            onLongPress = {},
                            onDragStart = { _: Offset -> },
                            onDrag = { _: Offset -> },
                            onDragEnd = {},
                            onDragCancel = {},
                            onDelete = {},
                        )
                        }
                        SessionPreparationModule(rows = SessionEditorScreenSamples.preparation, onEdit = {})
                        SessionImpactModule(impact = SessionEditorSamples.impact, onOpenAssistant = {})
                        EmpujeModule()
                    }
                    else -> {
                        SessionImpactModule(impact = SessionEditorSamples.impact, onOpenAssistant = {})
                        SessionPreparationModule(rows = SessionEditorScreenSamples.preparation, onEdit = {})
                        EmpujeModule()
                        TironModule()
                    }
                }
                }
                // Hueco para que la isla inferior no tape la última fila al desplazar.
                Box(modifier = Modifier.height(96.dp))
            }
            SessionEditorBottomIsland(
                visible = true,
                timeLabel = "≈ 58 min",
                hazeState = remember { HazeState() },
                onAddExercise = {},
                onAssistant = {},
                onTime = {},
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    @Composable
    private fun Scene() {
        Box(
            modifier = Modifier
                .testTag("screen")
                .width(360.dp)
                .height(740.dp)
                .background(KpknSurface.canvas),
        ) {
            if (scene == "asistente") {
                SessionAssistantSheetContent(
                    tab = SessionAssistantTab.IMPACT,
                    onTabSelect = {},
                    impact = SessionEditorSamples.impact,
                    analysisReady = true,
                    suggestions = SessionEditorSamples.suggestions,
                    onApplySuggestion = { _, _ -> },
                    templatesTab = {},
                )
            } else {
                EditorBody()
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

    /** Guarda el recuadro [tag] tal como se ve ahora. */
    private fun save(tag: String, name: String) {
        val bounds = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        saveBitmap(name, crop(drawView(), bounds))
    }

    /** Pinta una escena a 100 % y 130 % de letra; con el reloj parado, espera a que acaben los efectos de arranque. */
    private fun renderScene(sceneName: String, file: String) {
        for ((scale, suffix) in listOf(1f to "", 1.3f to "_s130")) {
            rule.runOnIdle {
                scene = sceneName
                fontScale = scale
            }
            rule.waitForIdle()
            rule.mainClock.advanceTimeBy(600)
            rule.waitForIdle()
            save("screen", "$file$suffix")
        }
    }

    @Test
    fun renderSessionEditorScreen() {
        onlyWhenAsked()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val base = LocalDensity.current
            hostView = LocalView.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale = fontScale),
                LocalWizardReducedMotion provides false,
            ) {
                Scene()
            }
        }
        renderScene("fuerza", "se8_fuerza")
        renderScene("cardio", "se8_cardio")
        renderScene("vacia", "se8_vacia")
        renderScene("asistente", "se8_asistente")
    }
}

package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
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
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.entreno.plan.exercisePatternOf
import com.example.kpkn.screens.sessioneditor.SessionEditorSamples
import com.example.kpkn.screens.sessioneditor.design.SessionExerciseExpanded
import com.example.kpkn.screens.sessioneditor.design.SessionExerciseRow
import com.example.kpkn.screens.sessioneditor.design.SessionIntensityRow
import com.example.kpkn.screens.sessioneditor.design.SessionOptionChips
import com.example.kpkn.screens.sessioneditor.design.SessionPartAddExerciseAction
import com.example.kpkn.screens.sessioneditor.design.SessionPartHeader
import com.example.kpkn.screens.sessioneditor.design.SessionSetMenuRows
import com.example.kpkn.screens.sessioneditor.design.SessionUnilateralPanel
import com.example.kpkn.screens.sessioneditor.sessionExerciseDose
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

/**
 * HOJA DE DIBUJO del ejercicio desplegado (SE3b). No comprueba nada: escribe PNG a 360 dp, al 100 % y al 130 % de letra. Escenas: RIR con
 * dos aproximaciones y tres series, RPE con una serie de carga fija, % del 1RM, tiempo, un drop con técnica, la fila de opciones con los
 * chips activos, el menú de una serie y una parte con un ejercicio desplegado entre dos plegados. Solo corre con `KPKN_RENDER_DIR`.
 *
 * El menú (KpknContextMenu) se pinta en un overlay que la hoja no recoge: aquí se dibuja su cuerpo dentro de un panel del mismo color.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class SessionExerciseExpandedRenderSheetTest {

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

    /** Escena que se dibuja ahora y letra. Fuera de la composición para poder cambiarlas desde la prueba. */
    private var scene by mutableStateOf("rir")
    private var fontScale by mutableStateOf(1f)

    /** La vista de Compose, para dibujarla a un bitmap. */
    private var hostView: View? = null

    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    /** Módulo de la parte: el relleno `module` a 16 dp del margen, con la etiqueta de la captura. */
    @Composable
    private fun Modulo(content: @Composable ColumnScope.() -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .background(KpknSurface.module, RoundedCornerShape(24.dp))
                .padding(vertical = 12.dp)
                .testTag("sheet"),
            content = content,
        )
    }

    /** Lo que va debajo de la fila: 20 dp a cada lado, como en la tarjeta del editor. */
    @Composable
    private fun Detalle(content: @Composable ColumnScope.() -> Unit) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
            content = content,
        )
    }

    @Composable
    private fun Fila(name: String, exercise: Exercise?, expanded: Boolean, chips: String? = null) {
        SessionExerciseRow(
            name = name,
            dose = exercise?.let { sessionExerciseDose(it.sets, it.trainingMode, it.restTime) } ?: "Sin series",
            expanded = expanded,
            accent = SymbolPalette.musculo,
            imageRes = null,
            pattern = exercisePatternOf(name),
            onToggle = {},
            onLongPress = {},
            onDragStart = {},
            onDrag = {},
            onDragEnd = {},
            onDragCancel = {},
            onDelete = {},
            chips = chips,
        )
    }

    @Composable
    private fun Desplegado(exercise: Exercise) {
        SessionExerciseExpanded(
            exercise = exercise,
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

    @Composable
    private fun Escena() {
        when (scene) {
            "rir" -> Modulo {
                Fila("Press banca", SessionEditorSamples.exerciseRir(), expanded = true, chips = "Pecho")
                Detalle { Desplegado(SessionEditorSamples.exerciseRir()) }
            }
            "rpe" -> Modulo {
                Fila("Sentadilla", SessionEditorSamples.exerciseRpe(), expanded = true, chips = "Piernas")
                Detalle { Desplegado(SessionEditorSamples.exerciseRpe()) }
            }
            "porcentaje" -> Modulo {
                Fila("Peso muerto", SessionEditorSamples.exercisePercent(), expanded = true, chips = "Espalda")
                Detalle { Desplegado(SessionEditorSamples.exercisePercent()) }
            }
            "tiempo" -> Modulo {
                Fila("Plancha", SessionEditorSamples.exerciseTime(), expanded = true, chips = "Core")
                Detalle { Desplegado(SessionEditorSamples.exerciseTime()) }
            }
            "drop" -> Modulo {
                Fila("Curl de bíceps", SessionEditorSamples.exerciseDrop(), expanded = true, chips = "Brazos")
                Detalle { Desplegado(SessionEditorSamples.exerciseDrop()) }
            }
            "opciones" -> Modulo {
                Detalle {
                    SessionIntensityRow(selected = 0, onSelect = {}, onMore = {})
                    SessionOptionChips(
                        modeLabel = "Modo · Reps",
                        unilateral = true,
                        smartLoad = true,
                        technique = true,
                        onMode = {},
                        onUnilateral = {},
                        onSmartLoad = {},
                        onTechnique = {},
                    )
                    SessionUnilateralPanel(
                        choiceIndex = 2,
                        unilateral = true,
                        sideOrderIndex = 0,
                        onChoose = {},
                        onSideOrder = {},
                    )
                }
            }
            "menu" -> Modulo {
                Fila("Press banca", SessionEditorSamples.exerciseRir(), expanded = true, chips = "Pecho")
                Detalle {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(KpknSurface.module, RoundedCornerShape(16.dp))
                            .border(1.dp, KpknSurface.moduleBorder, RoundedCornerShape(16.dp))
                            .padding(vertical = 6.dp),
                    ) {
                        SessionSetMenuRows(
                            canDuplicate = true,
                            onWork = {},
                            onDrop = {},
                            onDuplicate = {},
                            onRemove = {},
                        )
                    }
                    Desplegado(SessionEditorSamples.exerciseRir())
                }
            }
            "parte" -> Modulo {
                SessionPartHeader(
                    name = "PECHO",
                    caption = "3 ejercicios · 10 series",
                    colorId = null,
                    collapsed = false,
                    hasExercises = true,
                    onRename = {},
                    onChangeColor = {},
                    onToggleCollapse = {},
                    onRemove = {},
                    onDragStart = { _, _ -> },
                    onDrag = {},
                    onDragEnd = {},
                    onDragCancel = {},
                )
                Fila("Press inclinado", null, expanded = false, chips = "Pecho")
                Fila("Press banca", SessionEditorSamples.exerciseRir(), expanded = true, chips = "Pecho")
                Detalle { Desplegado(SessionEditorSamples.exerciseRir()) }
                Fila("Aperturas", null, expanded = false, chips = "Pecho")
                SessionPartAddExerciseAction(text = "Añadir ejercicio a Pecho", onClick = {})
            }
        }
    }

    /** Dibuja la vista entera a un bitmap, sobre negro. */
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

    /** Pinta una escena estática a 100 % y 130 % de letra, con su PNG para cada uno. */
    private fun renderStatic(sceneName: String, file: String) {
        for ((scale, suffix) in listOf(1f to "", 1.3f to "_s130")) {
            rule.runOnIdle {
                scene = sceneName
                fontScale = scale
            }
            rule.waitForIdle()
            // Con el reloj parado no hay cuadros: uno para que la escena nueva llegue a la vista antes de capturar.
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
            save("sheet", "$file$suffix")
        }
    }

    @Test
    fun renderSessionExerciseExpanded() {
        onlyWhenAsked()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val base = LocalDensity.current
            hostView = LocalView.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale = fontScale),
                LocalWizardReducedMotion provides false,
            ) {
                Escena()
            }
        }
        renderStatic("rir", "se3b_rir")
        renderStatic("rpe", "se3b_rpe")
        renderStatic("porcentaje", "se3b_porcentaje")
        renderStatic("tiempo", "se3b_tiempo")
        renderStatic("drop", "se3b_drop")
        renderStatic("opciones", "se3b_opciones")
        renderStatic("menu", "se3b_menu")
        renderStatic("parte", "se3b_parte")
    }
}

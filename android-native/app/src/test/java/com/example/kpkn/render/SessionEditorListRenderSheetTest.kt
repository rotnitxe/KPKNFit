package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.R
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.screens.onboarding.design.entreno.plan.exercisePatternOf
import com.example.kpkn.screens.sessioneditor.DragLiftPreview
import com.example.kpkn.screens.sessioneditor.DragPartLiftPreview
import com.example.kpkn.screens.sessioneditor.SessionEditorDropIndicator
import com.example.kpkn.screens.sessioneditor.SessionEditorInvalidDropBanner
import com.example.kpkn.screens.sessioneditor.SessionEditorSamples
import com.example.kpkn.screens.sessioneditor.components.SessionEditorEmptyState
import com.example.kpkn.screens.sessioneditor.design.SessionExerciseRow
import com.example.kpkn.screens.sessioneditor.design.SessionGroupLabel
import com.example.kpkn.screens.sessioneditor.design.SessionModuleEdge
import com.example.kpkn.screens.sessioneditor.design.SessionPartAddExerciseAction
import com.example.kpkn.screens.sessioneditor.design.SessionPartHeader
import com.example.kpkn.screens.sessioneditor.design.SessionSupersetCaption
import com.example.kpkn.screens.sessioneditor.design.bottomGap
import com.example.kpkn.screens.sessioneditor.design.sessionModuleSurface
import com.example.kpkn.screens.sessioneditor.sessionExerciseDose
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
 * HOJA DE DIBUJO de la lista del editor (SE3a). No comprueba nada: escribe PNG. Tres escenas: la lista de una sesión de fuerza (una parte con
 * una superserie, ejercicios sueltos, el espacio de cardio y las etiquetas «CARDIO» y «FUERZA»); la sesión vacía; y el arrastre (ejercicio
 * levantado, línea de destino, aviso de zona no válida y parte levantada). A 360 dp, al 100 % y al 130 % de letra. Solo corre con
 * `KPKN_RENDER_DIR`; sin ella se salta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class SessionEditorListRenderSheetTest {

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
    private var escena by mutableStateOf("lista")
    private var letra by mutableStateOf(1f)

    /** La vista de Compose, para dibujarla a un bitmap. */
    private var hostView: View? = null

    @Test
    fun pintaLaListaDelEditor() {
        assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        rule.setContent { Anfitrion() }
        for ((escala, sufijo) in listOf(1f to "", 1.3f to "_s130")) {
            letra = escala
            for (nombre in listOf("lista", "vacia", "arrastre")) {
                escena = nombre
                rule.waitForIdle()
                saveBitmap("se3a_$nombre$sufijo", drawView())
            }
        }
    }

    @Composable
    private fun Anfitrion() {
        val densidad = LocalDensity.current
        val vista = LocalView.current
        SideEffect { hostView = vista }
        CompositionLocalProvider(LocalDensity provides Density(densidad.density, letra)) {
            when (escena) {
                "vacia" -> Vacia()
                "arrastre" -> Arrastre()
                else -> Lista()
            }
        }
    }

    /** Lista de una sesión de fuerza con cardio al final: ejercicio suelto, parte con superserie y pie, y el espacio de cardio. */
    @Composable
    private fun Lista() {
        val ej = SessionEditorSamples.partExercises
        Column(modifier = Modifier.fillMaxWidth().background(Color.Black).padding(top = 8.dp, bottom = 24.dp)) {
            SessionGroupLabel(label = "FUERZA", moveLabel = "Mover al final", onMove = {})
            Pieza(SessionModuleEdge.ONLY) { Fila(ej[2]) }
            Pieza(SessionModuleEdge.TOP) {
                SessionPartHeader(
                    name = "PECHO",
                    caption = "3 ejercicios · 8 series",
                    colorId = "#00F0FF",
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
            }
            Pieza(SessionModuleEdge.MIDDLE) { Fila(ej[0], imagen = R.drawable.exercise_sentadilla_trasera_barra_alta) }
            Pieza(SessionModuleEdge.MIDDLE) {
                SessionSupersetCaption(text = "Superserie · 2 rondas · 1:30 entre rondas", editing = false, onEdit = {})
                Fila(ej[1], superset = true)
                Fila(ej[3], superset = true)
            }
            Pieza(SessionModuleEdge.BOTTOM) {
                SessionPartAddExerciseAction(text = "Añadir ejercicio a PECHO", onClick = {})
            }
            SessionGroupLabel(label = "CARDIO", moveLabel = "Mover al inicio", onMove = {})
            Pieza(SessionModuleEdge.TOP) {
                SessionExerciseRow(
                    name = "Bici estática",
                    dose = "30 min · zona 2",
                    expanded = false,
                    accent = Color(0xFF557F8C),
                    imageRes = null,
                    pattern = exercisePatternOf("Bici estática"),
                    onToggle = {},
                    onLongPress = {},
                    onDragStart = {},
                    onDrag = {},
                    onDragEnd = {},
                    onDragCancel = {},
                    onDelete = {},
                )
            }
            Pieza(SessionModuleEdge.BOTTOM) {
                SessionPartAddExerciseAction(text = "Añadir cardio", onClick = {})
            }
        }
    }

    /** Sesión vacía: la pregunta y las dos filas grandes. */
    @Composable
    private fun Vacia() {
        Column(modifier = Modifier.fillMaxWidth().background(Color.Black).padding(16.dp)) {
            SessionEditorEmptyState(onChooseStrength = {}, onChooseCardio = {})
        }
    }

    /** Arrastre: el ejercicio levantado, la línea de destino, el aviso de zona no válida y la parte levantada. */
    @Composable
    private fun Arrastre() {
        val ejercicio: Exercise = SessionEditorSamples.partExercises[0]
        Column(modifier = Modifier.fillMaxWidth().background(Color.Black).padding(16.dp)) {
            Box(modifier = Modifier.fillMaxWidth().height(116.dp)) {
                DragLiftPreview(
                    exercise = ejercicio,
                    rect = Rect(0f, 0f, 984f, 180f),
                    offsetProvider = { Offset.Zero },
                    rootBounds = Rect(0f, 0f, 1080f, 1920f),
                )
            }
            SessionEditorDropIndicator(visible = true)
            Box(modifier = Modifier.fillMaxWidth().height(48.dp)) {
                SessionEditorInvalidDropBanner(visible = true, modifier = Modifier.align(Alignment.Center))
            }
            Box(modifier = Modifier.fillMaxWidth().height(96.dp)) {
                DragPartLiftPreview(
                    partName = "PECHO",
                    rect = Rect(0f, 0f, 984f, 168f),
                    offsetYProvider = { 0f },
                    rootBounds = Rect(0f, 0f, 1080f, 1920f),
                )
            }
        }
    }

    /** Un trozo de módulo con el margen de la pantalla y su relleno. */
    @Composable
    private fun Pieza(edge: SessionModuleEdge, contenido: @Composable () -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = edge.bottomGap())
                .sessionModuleSurface(edge),
        ) {
            contenido()
        }
    }

    /** Una fila plegada con sus datos de muestra; la dosis es la de la función pura. */
    @Composable
    private fun Fila(ejercicio: Exercise, superset: Boolean = false, imagen: Int? = null) {
        SessionExerciseRow(
            name = ejercicio.name,
            dose = sessionExerciseDose(ejercicio.sets, ejercicio.trainingMode, ejercicio.restTime, includeRest = !superset) ?: "Sin series",
            expanded = false,
            accent = Color(0xFF5D8390),
            imageRes = imagen,
            pattern = exercisePatternOf(ejercicio.name),
            onToggle = {},
            onLongPress = {},
            onDragStart = {},
            onDrag = {},
            onDragEnd = {},
            onDragCancel = {},
            onDelete = {},
            superset = superset,
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

    private fun saveBitmap(name: String, bitmap: Bitmap) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

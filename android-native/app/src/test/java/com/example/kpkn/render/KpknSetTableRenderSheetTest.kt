package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.ui.design.KpknCellInput
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknNumberCell
import com.example.kpkn.ui.design.KpknSetCell
import com.example.kpkn.ui.design.KpknSetColumn
import com.example.kpkn.ui.design.KpknSetColumns
import com.example.kpkn.ui.design.KpknSetFormat
import com.example.kpkn.ui.design.KpknSetKind
import com.example.kpkn.ui.design.KpknSetRow
import com.example.kpkn.ui.design.KpknSetTable
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
 * HOJA DE DIBUJO de la celda numérica y la tabla de series (SE1). No comprueba nada: escribe PNG. Pinta la tabla en modo RIR (con «A»,
 * «1», «2», «3» y «D», rangos y descansos), RPE, % del 1RM, tiempo, distancia, una tabla vacía y una hoja con celdas (una editando, una con
 * error, una con selector y una sin dato). A 360 dp, al 100 % y al 130 % de letra. Solo corre con `KPKN_RENDER_DIR`; sin ella se salta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class KpknSetTableRenderSheetTest {

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
    private var scene by mutableStateOf("rir")
    private var fontScale by mutableStateOf(1f)

    /** La vista de Compose, para dibujarla a un bitmap. */
    private var hostView: View? = null

    private val picker = KpknCellInput.Picker(onOpen = {})

    private fun celda(
        value: String,
        description: String = value,
        input: KpknCellInput = KpknCellInput.ReadOnly,
        error: Boolean = false,
    ) = KpknSetCell(value = value, input = input, contentDescription = description, error = error)

    private fun fila(id: String, label: String, kind: KpknSetKind, vararg valores: Pair<String, KpknSetCell>) =
        KpknSetRow(id = id, label = label, kind = kind, cells = mapOf(*valores))

    private val rirFilas = listOf(
        fila(
            "a", "A", KpknSetKind.APPROACH,
            "kg" to celda("60", "Aproximación, 60 kilos"),
            "reps" to celda("10", "Aproximación, 10 repeticiones"),
            "rir" to celda("", "Aproximación, sin RIR"),
            "rest" to celda(KpknSetFormat.rest(60), "Descanso, 1 minuto", picker),
        ),
        fila(
            "1", "1", KpknSetKind.WORK,
            "kg" to celda("80", "Serie 1, 80 kilos"),
            "reps" to celda("6–8", "Serie 1, de 6 a 8 repeticiones"),
            "rir" to celda("2", "Serie 1, 2 repeticiones en reserva"),
            "rest" to celda(KpknSetFormat.rest(150), "Descanso, 2 minutos y 30 segundos", picker),
        ),
        fila(
            "2", "2", KpknSetKind.WORK,
            "kg" to celda("82,5", "Serie 2, 82,5 kilos"),
            "reps" to celda("6–8", "Serie 2, de 6 a 8 repeticiones"),
            "rir" to celda("2", "Serie 2, 2 repeticiones en reserva"),
            "rest" to celda(KpknSetFormat.rest(150), "Descanso, 2 minutos y 30 segundos", picker),
        ),
        fila(
            "3", "3", KpknSetKind.WORK,
            "kg" to celda("82,5", "Serie 3, 82,5 kilos"),
            "reps" to celda("6–8", "Serie 3, de 6 a 8 repeticiones"),
            "rir" to celda("1", "Serie 3, 1 repetición en reserva"),
            "rest" to celda(KpknSetFormat.rest(180), "Descanso, 3 minutos", picker),
        ),
        fila(
            "d", "D", KpknSetKind.DROP,
            "kg" to celda("60", "Drop set, 60 kilos"),
            "reps" to celda("12", "Drop set, 12 repeticiones"),
            "rir" to celda("0", "Drop set, 0 repeticiones en reserva"),
            "rest" to celda("", "Drop set, sin descanso"),
        ),
    )

    private val rpeFilas = listOf(
        fila(
            "1", "1", KpknSetKind.WORK,
            "kg" to celda("100"), "reps" to celda("5"), "rpe" to celda("7,5"),
            "rest" to celda(KpknSetFormat.rest(180), input = picker),
        ),
        fila(
            "2", "2", KpknSetKind.WORK,
            "kg" to celda("100"), "reps" to celda("5"), "rpe" to celda("8"),
            "rest" to celda(KpknSetFormat.rest(240), input = picker),
        ),
        fila(
            "3", "3", KpknSetKind.WORK,
            "kg" to celda("102,5"), "reps" to celda("4"), "rpe" to celda("9"),
            "rest" to celda(KpknSetFormat.rest(240), input = picker),
        ),
    )

    private val porcentajeFilas = listOf(
        fila(
            "1", "1", KpknSetKind.WORK,
            "percent" to celda("75"), "reps" to celda("8"), "rest" to celda(KpknSetFormat.rest(120), input = picker),
        ),
        fila(
            "2", "2", KpknSetKind.WORK,
            "percent" to celda("80"), "reps" to celda("6–8"), "rest" to celda(KpknSetFormat.rest(150), input = picker),
        ),
        fila(
            "3", "3", KpknSetKind.WORK,
            "percent" to celda("85"), "reps" to celda("4"), "rest" to celda(KpknSetFormat.rest(180), input = picker),
        ),
    )

    private val tiempoFilas = listOf(
        fila(
            "1", "1", KpknSetKind.WORK,
            "kg" to celda("20"), "time" to celda("0:45"), "rest" to celda(KpknSetFormat.rest(60), input = picker),
        ),
        fila(
            "2", "2", KpknSetKind.WORK,
            "kg" to celda("20"), "time" to celda("0:45"), "rest" to celda(KpknSetFormat.rest(60), input = picker),
        ),
    )

    private val distanciaFilas = listOf(
        fila(
            "1", "1", KpknSetKind.WORK,
            "distance" to celda("5 km"), "time" to celda("25:00"), "rest" to celda(KpknSetFormat.rest(90), input = picker),
        ),
    )

    @Composable
    private fun SceneTabla(columns: List<KpknSetColumn>, rows: List<KpknSetRow>) {
        KpknModule(modifier = Modifier.fillMaxWidth()) {
            KpknModuleHeader(label = "Series")
            Spacer(Modifier.height(12.dp))
            KpknSetTable(columns = columns, rows = rows, onAddSet = {}, onRowMenu = {})
        }
    }

    @Composable
    private fun SceneCeldas() {
        KpknModule(modifier = Modifier.fillMaxWidth()) {
            KpknModuleHeader(label = "Celdas")
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KpknNumberCell(
                    value = "82,5",
                    input = KpknCellInput.Text(onCommit = {}),
                    contentDescription = "Serie 2, kilos, 82,5",
                    modifier = Modifier.weight(1f).testTag("celda_editando"),
                )
                KpknNumberCell(
                    value = "300",
                    input = KpknCellInput.Text(onCommit = {}),
                    contentDescription = "Serie 3, kilos, 300",
                    modifier = Modifier.weight(1f).testTag("celda_error"),
                    error = true,
                )
                KpknNumberCell(
                    value = KpknSetFormat.rest(150),
                    input = picker,
                    contentDescription = "Descanso, 2 minutos y 30 segundos",
                    modifier = Modifier.weight(1f),
                )
                KpknNumberCell(
                    value = "",
                    input = KpknCellInput.ReadOnly,
                    contentDescription = "Serie D, sin dato",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    @Composable
    private fun Scene() {
        Column(
            modifier = Modifier.testTag("sheet").width(360.dp).background(KpknSurface.canvas).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (scene) {
                "rpe" -> SceneTabla(KpknSetColumns.rpe, rpeFilas)
                "porcentaje" -> SceneTabla(KpknSetColumns.percent, porcentajeFilas)
                "tiempo" -> SceneTabla(KpknSetColumns.time, tiempoFilas)
                "distancia" -> SceneTabla(KpknSetColumns.distance, distanciaFilas)
                "vacia" -> SceneTabla(KpknSetColumns.rir, emptyList())
                "celdas" -> SceneCeldas()
                else -> SceneTabla(KpknSetColumns.rir, rirFilas)
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

    /**
     * Pinta una escena estática a 100 % y 130 % de letra. [afterShow] prepara el estado (p. ej., abrir una celda para editarla) solo en la
     * pasada del 100 %: al subir la letra la escena sigue igual y ese estado se conserva.
     */
    private fun renderStatic(sceneName: String, file: String, afterShow: () -> Unit = {}) {
        for ((scale, suffix) in listOf(1f to "", 1.3f to "_s130")) {
            rule.runOnIdle {
                scene = sceneName
                fontScale = scale
            }
            rule.waitForIdle()
            // Con el reloj parado no hay cuadros: uno para que la escena nueva llegue a la vista antes de capturar.
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
            if (suffix.isEmpty()) afterShow()
            save("sheet", "$file$suffix")
        }
    }

    @Test
    fun renderKpknSetTableSheets() {
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
        renderStatic("rir", "se1_tabla_rir")
        renderStatic("rpe", "se1_tabla_rpe")
        renderStatic("porcentaje", "se1_tabla_porcentaje")
        renderStatic("tiempo", "se1_tabla_tiempo")
        renderStatic("distancia", "se1_tabla_distancia")
        renderStatic("vacia", "se1_tabla_vacia")
        renderStatic("celdas", "se1_celdas", afterShow = {
            rule.onNodeWithContentDescription("Serie 2, kilos, 82,5").performClick()
            // Con el reloj parado, el foco y el filete de edición llegan con cuadros: avanzamos 400 ms antes de capturar.
            rule.mainClock.advanceTimeBy(400)
            rule.waitForIdle()
        })
    }
}

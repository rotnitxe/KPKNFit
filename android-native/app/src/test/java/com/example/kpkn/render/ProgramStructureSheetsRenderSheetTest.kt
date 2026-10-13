package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.programdetail.ProgramStructureSheetSamples
import com.example.kpkn.screens.programdetail.design.structure.ProgramCyclesContent
import com.example.kpkn.screens.programdetail.design.structure.ProgramHistoryContent
import com.example.kpkn.screens.programdetail.design.structure.ProgramRouteContent
import com.example.kpkn.screens.programdetail.design.structure.ProgramTemplateConfirmContent
import com.example.kpkn.screens.programdetail.design.structure.ProgramTemplatesContent
import com.example.kpkn.screens.programdetail.design.structure.templateSummaryLine
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
 * HOJA DE DIBUJO de la pestaña Estructura, parte 3 (P3c): los ciclos con dos ciclos activos, una semana especial y el aviso de eventos
 * antiguos; la ruta con cuatro bloques, dos fechas clave y la competición desajustada; las plantillas y los protocolos; la confirmación de
 * una plantilla con sesiones; y el historial con copias y vacío. Escribe PNG a 360 dp, con la letra al 100 % y al 130 %.
 * No comprueba nada: solo corre con `KPKN_RENDER_DIR` y sin ella se salta.
 *
 * Los datos son de ejemplo (`ProgramStructureSheetSamples`). Se dibuja el contenido de cada hoja, sin la hoja.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h2600dp-xxhdpi", application = android.app.Application::class)
class ProgramStructureSheetsRenderSheetTest {

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

    /** Los casos de la hoja, en orden. */
    private val cases = listOf(
        "ciclos",
        "ruta",
        "plantillas",
        "confirmarPlantilla",
        "historial",
        "historialVacio",
    )

    /** El caso que se dibuja ahora (-1 = nada) y la escala de letra. */
    private var current by mutableStateOf(-1)
    private var fontScale by mutableStateOf(1f)

    /** La vista de Compose, para dibujarla a un bitmap. */
    private var hostView: View? = null

    private fun save(tag: String, name: String) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        // Sin el árbol sin fusionar, las etiquetas dentro de un módulo tocable no se encuentran.
        val bounds = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val view = hostView!!
        val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread {
            val canvas = Canvas(full)
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

    /** Alto del lienzo de cada caso, en dp: el contenido más largo cabe sin recortarse. */
    private fun heightOf(name: String): Int = when (name) {
        "ciclos" -> 2400
        "ruta" -> 2000
        "plantillas" -> 2400
        "confirmarPlantilla" -> 1400
        "historial" -> 1100
        else -> 800
    }

    /** Pinta el contenido del caso [name] con los datos de ejemplo y sin acciones de verdad. */
    @Composable
    private fun Case(name: String) {
        val samples = ProgramStructureSheetSamples
        when (name) {
            "ciclos" -> ProgramCyclesContent(
                model = samples.cycles,
                onSaveLoop = {},
                onLoopAction = { _, _ -> },
                onAddTemplate = {},
                onMigrateLegacy = {},
                onPostponeWeek = {},
                onCancelWeek = {},
                onViewWeek = { _, _ -> },
            )
            "ruta" -> ProgramRouteContent(
                model = samples.route,
                onFocusWeek = { _, _ -> },
                onAdjustBlockWeeks = { _, _ -> },
            )
            "plantillas" -> ProgramTemplatesContent(
                templates = samples.templateCards,
                protocols = samples.protocolCards,
                onPickTemplate = {},
                onPickProtocol = {},
            )
            "confirmarPlantilla" -> ProgramTemplateConfirmContent(
                name = samples.template.name,
                summary = templateSummaryLine(samples.template),
                description = samples.template.description,
                hasSessions = true,
                onConfirm = {},
                onCancel = {},
            )
            "historial" -> ProgramHistoryContent(snapshots = samples.copies, onRestore = {})
            "historialVacio" -> ProgramHistoryContent(snapshots = emptyList(), onRestore = {})
        }
    }

    @Test
    fun renderProgramStructureSheets() {
        onlyWhenAsked()
        rule.setContent {
            val base = LocalDensity.current
            hostView = LocalView.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale = fontScale),
                LocalWizardReducedMotion provides true,
            ) {
                MaterialTheme {
                    Box(
                        Modifier
                            .testTag("p3c_host")
                            .width(360.dp)
                            .height(if (current >= 0) heightOf(cases[current]).dp else 200.dp)
                            .background(Color.Black),
                    ) {
                        if (current >= 0) Case(cases[current])
                    }
                }
            }
        }
        for ((index, name) in cases.withIndex()) {
            for ((scale, suffix) in listOf(1f to "s100", 1.3f to "s130")) {
                // Primero se vacía el lienzo: así cada captura parte de una composición nueva y no hereda el estado anterior.
                rule.runOnUiThread {
                    current = -1
                    fontScale = scale
                }
                rule.waitForIdle()
                rule.runOnUiThread { current = index }
                rule.waitForIdle()
                save("p3c_host", "${name}_$suffix")
            }
        }
    }
}

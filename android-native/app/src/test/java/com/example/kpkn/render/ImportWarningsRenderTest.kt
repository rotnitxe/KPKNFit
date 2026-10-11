package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.training.importer.ImportWarning
import com.example.kpkn.domain.training.importer.ImportWarningKind
import com.example.kpkn.domain.training.importer.ImportedDay
import com.example.kpkn.domain.training.importer.ImportedExercise
import com.example.kpkn.domain.training.importer.ImportedProgramDraft
import com.example.kpkn.domain.training.importer.ImportedWeek
import com.example.kpkn.screens.programs.importer.ImportPreviewWeeks
import com.example.kpkn.screens.programs.importer.ImportWarningsPanel
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
 * HOJAS DE DIBUJO del panel de avisos (con su acción) y del panel «Añadir ejercicio» bajo el último ejercicio del día (no comprueba
 * nada: escribe PNG). Solo corre con la variable de entorno `KPKN_RENDER_DIR`; sin ella se salta. Cada vista se dibuja a 411 dp y a
 * 320 dp con la letra al 130 %, con los gráficos nativos de Robolectric. Patrón de [ImportStructureRenderTest].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp-xxhdpi", application = android.app.Application::class)
class ImportWarningsRenderTest {

    companion object {
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

    /** La vista de Compose de la actividad, para dibujarla a un bitmap. */
    private var hostView: View? = null

    /** Dos semanas; cuatro avisos de tipos distintos. [reviewed] marca los que ya están revisados. */
    private fun program(reviewed: Set<String> = emptySet()) = ImportedProgramDraft(
        title = "Plan",
        weeks = listOf(
            ImportedWeek(
                1,
                listOf(
                    ImportedDay(
                        "w1-d1",
                        "Pierna",
                        listOf(
                            ImportedExercise("w1-d1-e1", "Sentadilla", sets = 4, repsMin = 6, repsMax = 8, loadKg = 120.0),
                            ImportedExercise("w1-d1-e2", "Prensa", sets = 3, repsMin = 10, repsMax = 10),
                        ),
                    ),
                    ImportedDay(
                        "w1-d2",
                        "Espalda",
                        listOf(ImportedExercise("w1-d2-e1", "Remo con barra", sets = 4, repsMin = 8, repsMax = 8)),
                    ),
                ),
            ),
            ImportedWeek(
                2,
                listOf(
                    ImportedDay(
                        "w2-d1",
                        "Pierna",
                        listOf(ImportedExercise("w2-d1-e1", "Sentadilla", sets = 4, repsMin = 6, repsMax = 8, loadKg = 125.0)),
                    ),
                ),
            ),
        ),
        warnings = listOf(
            ImportWarning(
                "a1",
                ImportWarningKind.SKIPPED_ROW,
                "Hoja «Pierna», fila 5",
                "Fondos en paralelas · 3 · 10",
                "Hoja «Pierna», fila 5: «Fondos en paralelas» sin carga; se omite",
                1,
                "w1-d1",
                null,
            ),
            ImportWarning(
                "a2",
                ImportWarningKind.IGNORED_VALUE,
                "Hoja «Pierna», fila 2",
                "Sentadilla · 4 · 6-8 · 120 kg · RPE 12",
                "Hoja «Pierna», fila 2: el RPE de «Sentadilla» está fuera de rango; se ignora",
                1,
                "w1-d1",
                "w1-d1-e1",
            ),
            ImportWarning(
                "a3",
                ImportWarningKind.EMPTY_DAY,
                "Hoja «Semana 2», fila 4",
                "Descanso",
                "«Descanso» no tiene ejercicios; se omite",
                2,
                null,
                null,
            ),
            ImportWarning(
                "a4",
                ImportWarningKind.SHEET_NOTE,
                "Hoja «Guía»",
                null,
                "Hoja «Guía»: se omitieron 3 líneas de texto.",
                null,
                null,
                null,
            ),
        ),
        reviewedWarnings = reviewed,
    )

    /** Lo que decide el catálogo para la sentadilla; el resto, propio. */
    private val decided: Map<String, String?> = mapOf(
        "Sentadilla" to "cfg-squat",
        "Prensa" to null,
        "Remo con barra" to null,
    )

    /** Un recuadro de la hoja: fondo negro y el ancho de la pantalla, con su etiqueta de prueba «sheet». */
    @Composable
    private fun Sheet(width: Dp = 411.dp, fontScale: Float = 1f, content: @Composable () -> Unit) {
        val base = LocalDensity.current
        hostView = LocalView.current
        CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = fontScale)) {
            Box(Modifier.testTag("sheet").wrapContentSize().background(Color.Black)) {
                Box(Modifier.width(width).padding(horizontal = 16.dp, vertical = 16.dp)) {
                    content()
                }
            }
        }
    }

    /** El panel de avisos solo, como lo pone la pantalla bajo el título y la línea de deshacer. */
    @Composable
    private fun Warnings(draft: ImportedProgramDraft) {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            ImportWarningsPanel(
                imported = draft,
                reveal = 0,
                search = { emptyList() },
                onOpenExercise = {},
                onEdit = {},
            )
        }
    }

    /** (a) El panel con cuatro avisos de tipos distintos, cada uno con su acción. */
    @Test
    fun renderTheWarningsPanel() = renderWarnings(width = 411.dp, fontScale = 1f, name = "a_avisos")

    @Test
    fun renderTheWarningsPanelAt320WithLargeText() = renderWarnings(width = 320.dp, fontScale = 1.3f, name = "a2_avisos_320_letra_130")

    private fun renderWarnings(width: Dp, fontScale: Float, name: String) {
        onlyWhenAsked()
        val draft = program()
        rule.setContent {
            Sheet(width = width, fontScale = fontScale) { Warnings(draft) }
        }
        save(name)
    }

    /** (b) «Añadir como ejercicio» abierto en la fila no entendida: el nombre propuesto y el selector de día. */
    @Test
    fun renderAddingFromAWarning() = renderAddFromWarning(width = 411.dp, fontScale = 1f, name = "b_anadir_desde_aviso")

    @Test
    fun renderAddingFromAWarningAt320WithLargeText() =
        renderAddFromWarning(width = 320.dp, fontScale = 1.3f, name = "b2_anadir_desde_aviso_320_letra_130")

    private fun renderAddFromWarning(width: Dp, fontScale: Float, name: String) {
        onlyWhenAsked()
        val draft = program()
        rule.setContent {
            Sheet(width = width, fontScale = fontScale) { Warnings(draft) }
        }
        rule.onNodeWithTag("import-warning-add-a1").performClick()
        save(name)
    }

    /** (c) Todo revisado: el panel sale plegado en una línea, sin la lista de revisados. */
    @Test
    fun renderTheFoldedPanel() = renderFolded(width = 411.dp, fontScale = 1f, name = "c_avisos_plegados")

    @Test
    fun renderTheFoldedPanelAt320WithLargeText() = renderFolded(width = 320.dp, fontScale = 1.3f, name = "c2_avisos_plegados_320_letra_130")

    private fun renderFolded(width: Dp, fontScale: Float, name: String) {
        onlyWhenAsked()
        val draft = program(reviewed = setOf("a1", "a2", "a3", "a4"))
        rule.setContent {
            Sheet(width = width, fontScale = fontScale) { Warnings(draft) }
        }
        save(name)
    }

    /** (d) El arreglo de H2b: «Añadir ejercicio» de un día sale bajo su último ejercicio, donde queda el nuevo. */
    @Test
    fun renderTheAddPanelBelowTheLastExercise() = renderAddBelowLast(width = 411.dp, fontScale = 1f, name = "d_anadir_bajo_ultimo")

    @Test
    fun renderTheAddPanelBelowTheLastExerciseAt320WithLargeText() =
        renderAddBelowLast(width = 320.dp, fontScale = 1.3f, name = "d2_anadir_bajo_ultimo_320_letra_130")

    private fun renderAddBelowLast(width: Dp, fontScale: Float, name: String) {
        onlyWhenAsked()
        val draft = program()
        rule.setContent {
            Sheet(width = width, fontScale = fontScale) {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    ImportPreviewWeeks(
                        imported = draft,
                        resolutions = decided,
                        openedExerciseId = null,
                        onOpen = {},
                        onEdit = {},
                        suggest = { emptyList() },
                        search = { emptyList() },
                        namesOf = { emptyMap() },
                    )
                }
            }
        }
        rule.onNodeWithTag("import-day-menu-w1-d1").performClick()
        rule.onNodeWithTag("import-day-add-exercise").performClick()
        save(name)
    }

    /** Dibuja la vista de la actividad y recorta el recuadro «sheet» a `KPKN_RENDER_DIR/<name>.png`. */
    private fun save(name: String) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        val bounds = rule.onNodeWithTag("sheet").fetchSemanticsNode().boundsInRoot
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
}

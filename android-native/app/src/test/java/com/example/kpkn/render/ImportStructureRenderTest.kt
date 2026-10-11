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
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.training.importer.ExerciseNameCandidate
import com.example.kpkn.domain.training.importer.ImportedDay
import com.example.kpkn.domain.training.importer.ImportedExercise
import com.example.kpkn.domain.training.importer.ImportedProgramDraft
import com.example.kpkn.domain.training.importer.ImportedWeek
import com.example.kpkn.screens.programs.importer.ImportPreviewWeeks
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
 * HOJAS DE DIBUJO de los menús de día y de semana y del panel «Añadir ejercicio» (no comprueba nada: escribe PNG). Solo corre con
 * la variable de entorno `KPKN_RENDER_DIR`; sin ella se salta. Cada vista se dibuja a 411 dp y a 320 dp con la letra al 130 %, con
 * los gráficos nativos de Robolectric. Patrón de [ImportEditorRenderTest].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp-xxhdpi", application = android.app.Application::class)
class ImportStructureRenderTest {

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

    /** Una semana: «Pierna» con la Sentadilla decidida (120 kg) y «Espalda» con un remo propio. */
    private fun program() = ImportedProgramDraft(
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
    )

    /** Lo que decide el catálogo para la sentadilla, y el resto como propio. */
    private val resolutions: Map<String, String?> = mapOf(
        "Sentadilla" to "cfg-squat",
        "Prensa" to null,
        "Remo con barra" to null,
    )

    /** Un resultado del catálogo para «Añadir ejercicio». */
    private val remo = ExerciseNameCandidate("cfg-row", "Remo con barra (barra)", 0.0, "barbell")

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

    @Composable
    private fun Preview(draft: ImportedProgramDraft, search: suspend (String) -> List<ExerciseNameCandidate> = { emptyList() }) {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            ImportPreviewWeeks(
                imported = draft,
                resolutions = resolutions,
                openedExerciseId = null,
                onOpen = {},
                onEdit = {},
                suggest = { emptyList() },
                search = search,
                namesOf = { emptyMap() },
            )
        }
    }

    /** (a) El menú de un día abierto, bajo su nombre. */
    @Test
    fun renderTheDayMenuOpen() = renderDayMenu(width = 411.dp, fontScale = 1f, name = "a_menu_dia")

    @Test
    fun renderTheDayMenuOpenAt320WithLargeText() = renderDayMenu(width = 320.dp, fontScale = 1.3f, name = "a2_menu_dia_320_letra_130")

    private fun renderDayMenu(width: Dp, fontScale: Float, name: String) {
        onlyWhenAsked()
        val draft = program()
        rule.setContent {
            Sheet(width = width, fontScale = fontScale) { Preview(draft) }
        }
        rule.onNodeWithTag("import-day-menu-w1-d1").performClick()
        save(name)
    }

    /** (b) El menú de una semana abierto, bajo «Semana N». */
    @Test
    fun renderTheWeekMenuOpen() = renderWeekMenu(width = 411.dp, fontScale = 1f, name = "b_menu_semana")

    @Test
    fun renderTheWeekMenuOpenAt320WithLargeText() = renderWeekMenu(width = 320.dp, fontScale = 1.3f, name = "b2_menu_semana_320_letra_130")

    private fun renderWeekMenu(width: Dp, fontScale: Float, name: String) {
        onlyWhenAsked()
        val draft = program()
        rule.setContent {
            Sheet(width = width, fontScale = fontScale) { Preview(draft) }
        }
        rule.onNodeWithTag("import-week-menu-1").performClick()
        save(name)
    }

    /** (c) «Añadir ejercicio» con un resultado del catálogo ya elegido: el nombre queda fijado. */
    @Test
    fun renderTheAddPanelWithACatalogChoice() = renderAddWithCatalog(width = 411.dp, fontScale = 1f, name = "c_anadir_elegido")

    @Test
    fun renderTheAddPanelWithACatalogChoiceAt320WithLargeText() =
        renderAddWithCatalog(width = 320.dp, fontScale = 1.3f, name = "c2_anadir_elegido_320_letra_130")

    private fun renderAddWithCatalog(width: Dp, fontScale: Float, name: String) {
        onlyWhenAsked()
        val draft = program()
        rule.setContent {
            Sheet(width = width, fontScale = fontScale) { Preview(draft, search = { listOf(remo) }) }
        }
        rule.onNodeWithTag("import-day-menu-w1-d2").performClick()
        rule.onNodeWithTag("import-day-add-exercise").performClick()
        rule.onNodeWithTag("import-choice-search").performTextInput("remo")
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag("import-candidate-cfg-row").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("import-candidate-cfg-row").performClick()
        save(name)
    }

    /** (d) «Añadir ejercicio» con un nombre propio escrito a mano. */
    @Test
    fun renderTheAddPanelWithAnOwnName() = renderAddWithOwnName(width = 411.dp, fontScale = 1f, name = "d_anadir_propio")

    @Test
    fun renderTheAddPanelWithAnOwnNameAt320WithLargeText() =
        renderAddWithOwnName(width = 320.dp, fontScale = 1.3f, name = "d2_anadir_propio_320_letra_130")

    private fun renderAddWithOwnName(width: Dp, fontScale: Float, name: String) {
        onlyWhenAsked()
        val draft = program()
        rule.setContent {
            Sheet(width = width, fontScale = fontScale) { Preview(draft) }
        }
        rule.onNodeWithTag("import-day-menu-w1-d2").performClick()
        rule.onNodeWithTag("import-day-add-exercise").performClick()
        rule.onNodeWithTag("import-add-name").performTextInput("Fondos en paralelas")
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

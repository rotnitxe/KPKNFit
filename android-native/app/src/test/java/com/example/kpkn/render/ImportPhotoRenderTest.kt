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
import com.example.kpkn.domain.training.importer.ImportLoadUnit
import com.example.kpkn.domain.training.importer.ImportedDay
import com.example.kpkn.domain.training.importer.ImportedExercise
import com.example.kpkn.domain.training.importer.ImportedProgramDraft
import com.example.kpkn.domain.training.importer.ImportedWeek
import com.example.kpkn.screens.programs.importer.ImportPreviewWeeks
import com.example.kpkn.screens.programs.importer.ImportReadingNotice
import com.example.kpkn.screens.programs.importer.ImportSourceChoices
import java.io.File
import java.io.FileOutputStream
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * HOJAS DE DIBUJO de la foto de la rutina, las cargas en libras y la línea de progreso del importador (no comprueba nada:
 * escribe PNG). Solo corre con la variable de entorno `KPKN_RENDER_DIR`; sin ella se salta. Cada hoja a 411 dp y a 320 dp con la
 * letra al 130 %. Patrón de [ImportEditorRenderTest].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp-xxhdpi", application = android.app.Application::class)
class ImportPhotoRenderTest {

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

    /** Un programa con cargas: el primer valor (135) es el que muestra la nota de la conversión a kg. */
    private fun withLoads(unit: ImportLoadUnit) = ImportedProgramDraft(
        title = "Plan",
        weeks = listOf(
            ImportedWeek(
                1,
                listOf(
                    ImportedDay(
                        "w1-d1",
                        "Pierna",
                        listOf(
                            ImportedExercise("w1-d1-e1", "Sentadilla", sets = 4, repsMin = 6, repsMax = 8, loadKg = 135.0, rpe = 8.0),
                            ImportedExercise("w1-d1-e2", "Prensa", sets = 3, repsMin = 10, repsMax = 10, loadKg = 270.0),
                        ),
                    ),
                    ImportedDay("w1-d2", "Empuje", listOf(ImportedExercise("w1-d2-e1", "Press banca", sets = 4, repsMin = 8, repsMax = 8))),
                ),
            ),
        ),
        loadUnit = unit,
    )

    /** Un recuadro de la hoja: fondo negro y el ancho de [width] con la letra de [fontScale], con su etiqueta de prueba «sheet». */
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

    @Test
    fun renderTheThreeSymbolsWithThePhotoActionsOpen() {
        onlyWhenAsked()
        rule.setContent {
            Sheet {
                ImportSourceChoices(onChooseFile = {}, onPaste = {}, onPhoto = { _, _ -> })
            }
        }
        rule.onNodeWithTag("import-source-photo").performClick()
        rule.waitForIdle()
        save("a_tres_simbolos_foto_411")
    }

    @Test
    fun renderTheThreeSymbolsWithThePhotoActionsOpenAt320WithLargeText() {
        onlyWhenAsked()
        rule.setContent {
            Sheet(width = 320.dp, fontScale = 1.3f) {
                ImportSourceChoices(onChooseFile = {}, onPaste = {}, onPhoto = { _, _ -> })
            }
        }
        rule.onNodeWithTag("import-source-photo").performClick()
        rule.waitForIdle()
        save("a_tres_simbolos_foto_320_letra_130")
    }

    @Test
    fun renderThePreviewWithPoundsAndItsConversionNote() {
        onlyWhenAsked()
        val draft = withLoads(ImportLoadUnit.LB)
        rule.setContent {
            Sheet {
                ImportPreviewWeeks(
                    imported = draft,
                    resolutions = mapOf("Sentadilla" to null, "Prensa" to null, "Press banca" to null),
                    openedExerciseId = null,
                    onOpen = {},
                    onEdit = {},
                    suggest = { emptyList() },
                    search = { emptyList() },
                    namesOf = { emptyMap() },
                )
            }
        }
        save("b_vista_previa_lb_411")
    }

    @Test
    fun renderThePreviewWithPoundsAndItsConversionNoteAt320WithLargeText() {
        onlyWhenAsked()
        val draft = withLoads(ImportLoadUnit.LB)
        rule.setContent {
            Sheet(width = 320.dp, fontScale = 1.3f) {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    ImportPreviewWeeks(
                        imported = draft,
                        resolutions = mapOf("Sentadilla" to null, "Prensa" to null, "Press banca" to null),
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
        save("b_vista_previa_lb_320_letra_130")
    }

    @Test
    fun renderTheReadingLineWithCancel() {
        onlyWhenAsked()
        rule.setContent {
            Sheet {
                ImportReadingNotice(text = "Leyendo la página 2 de 5…", onCancel = {})
            }
        }
        save("c_progreso_cancelar_411")
    }

    @Test
    fun renderTheReadingLineWithCancelAt320WithLargeText() {
        onlyWhenAsked()
        rule.setContent {
            Sheet(width = 320.dp, fontScale = 1.3f) {
                ImportReadingNotice(text = "Leyendo la página 2 de 5…", onCancel = {})
            }
        }
        save("c_progreso_cancelar_320_letra_130")
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

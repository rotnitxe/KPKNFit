package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.training.importer.ImportDraftValidation
import com.example.kpkn.domain.training.importer.ImportWarning
import com.example.kpkn.domain.training.importer.ImportWarningKind
import com.example.kpkn.domain.training.importer.ImportedDay
import com.example.kpkn.domain.training.importer.ImportedExercise
import com.example.kpkn.domain.training.importer.ImportedProgramDraft
import com.example.kpkn.domain.training.importer.ImportedWeek
import com.example.kpkn.screens.programs.importer.ImportPreviewWeeks
import com.example.kpkn.screens.programs.importer.ImportReview
import com.example.kpkn.screens.programs.importer.ImportUndoLine
import com.example.kpkn.screens.programs.importer.ImportWarningsNote
import com.example.kpkn.screens.onboarding.design.WizardColors
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
 * HOJAS DE DIBUJO del panel de ajuste y de la revisión final del importador (no comprueba nada: escribe PNG). Solo corre
 * con la variable de entorno `KPKN_RENDER_DIR`; sin ella se salta. Ancho de 411 dp, con los gráficos nativos de Robolectric
 * para ver las tipografías reales. Patrón de [WizardRenderSheetTest].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp-xxhdpi", application = android.app.Application::class)
class ImportEditorRenderTest {

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

    /** Un ejercicio con carga en kg y RPE, y otro sin carga: el panel abierto muestra kg y RPE. */
    private fun kgAndRpe() = ImportedProgramDraft(
        title = "Plan",
        weeks = listOf(
            ImportedWeek(
                1,
                listOf(
                    ImportedDay(
                        "w1-d1",
                        "Pierna",
                        listOf(
                            ImportedExercise(
                                "w1-d1-e1", "Sentadilla", sets = 4, repsMin = 6, repsMax = 8, loadKg = 120.0, rpe = 8.0,
                                restSeconds = 150, notes = "Profundidad", sourceText = "Sentadilla 4x6-8 @120kg RPE 8",
                            ),
                            ImportedExercise("w1-d1-e2", "Prensa", sets = 3, repsMin = 10, repsMax = 10),
                        ),
                    ),
                ),
            ),
        ),
    )

    /** Un ejercicio sin decidir: el panel muestra la elección del catálogo. */
    private fun undecided() = ImportedProgramDraft(
        title = "Plan",
        weeks = listOf(
            ImportedWeek(
                1,
                listOf(
                    ImportedDay(
                        "w1-d1",
                        "Espalda",
                        listOf(ImportedExercise("w1-d1-e1", "Remo con barra", sets = 4, repsMin = 8, repsMax = 8, sourceText = "Remo con barra 4x8")),
                    ),
                ),
            ),
        ),
    )

    /** Tres problemas: un ejercicio sin decidir, un día vacío y dos avisos sin revisar. */
    private fun threeProblems() = ImportedProgramDraft(
        title = "Plan",
        weeks = listOf(
            ImportedWeek(
                1,
                listOf(
                    ImportedDay("w1-d1", "Pierna", listOf(ImportedExercise("w1-d1-e1", "Remo con barra", sets = 4, repsMin = 8, repsMax = 8))),
                    ImportedDay("w1-d2", "Empuje", emptyList()),
                ),
            ),
        ),
        warnings = listOf(
            ImportWarning("a1", ImportWarningKind.SKIPPED_ROW, "Línea 5", "Hacer 3 vueltas", "Línea 5: no se entiende «Hacer 3 vueltas»", 1, "w1-d1", null),
            ImportWarning("a2", ImportWarningKind.SKIPPED_ROW, "Línea 9", "Nota 2", "Línea 9: no se entiende «Nota 2»", 1, null, null),
        ),
    )

    /** Un programa listo: una semana, dos días y dos ejercicios decididos. */
    private fun ready() = ImportedProgramDraft(
        title = "Plan",
        weeks = listOf(
            ImportedWeek(
                1,
                listOf(
                    ImportedDay("w1-d1", "Pierna", listOf(ImportedExercise("w1-d1-e1", "Sentadilla", sets = 4, repsMin = 6, repsMax = 8, loadKg = 120.0))),
                    ImportedDay("w1-d2", "Empuje", listOf(ImportedExercise("w1-d2-e1", "Press banca", sets = 4, repsMin = 8, repsMax = 10))),
                ),
            ),
        ),
    )

    /** Un recuadro de la hoja: fondo negro y el ancho de la pantalla de 411 dp, con su etiqueta de prueba «sheet». */
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
    fun renderTheOpenPanelWithKgAndRpe() {
        onlyWhenAsked()
        val draft = kgAndRpe()
        rule.setContent {
            Sheet {
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(20.dp)) {
                    ImportPreviewWeeks(
                        imported = draft,
                        resolutions = mapOf("Sentadilla" to null, "Prensa" to null),
                        openedExerciseId = "w1-d1-e1",
                        onOpen = {},
                        onEdit = {},
                        suggest = { emptyList() },
                        search = { emptyList() },
                        namesOf = { emptyMap() },
                    )
                }
            }
        }
        save("a_panel_kg_rpe")
    }

    @Test
    fun renderTheOpenPanelOfAnUndecidedExercise() {
        onlyWhenAsked()
        val draft = undecided()
        rule.setContent {
            Sheet {
                ImportPreviewWeeks(
                    imported = draft,
                    resolutions = emptyMap(),
                    openedExerciseId = "w1-d1-e1",
                    onOpen = {},
                    onEdit = {},
                    suggest = { emptyList() },
                    search = { emptyList() },
                    namesOf = { emptyMap() },
                )
            }
        }
        save("b_panel_undecided")
    }

    @Test
    fun renderTheReviewWithThreeProblems() {
        onlyWhenAsked()
        val draft = threeProblems()
        rule.setContent {
            val issues = ImportDraftValidation.check(draft) { false }
            Sheet {
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(20.dp)) {
                    ImportUndoLine(label = "Quitaste «Pierna».", onUndo = {})
                    ImportWarningsNote(warnings = draft.warningMessages, expanded = false, onExpandedChange = {})
                    ImportReview(
                        imported = draft,
                        issues = issues,
                        onOpenExercise = {},
                        onEdit = {},
                        onShowWarnings = {},
                        onRestart = {},
                    )
                }
            }
        }
        save("c_review_three_problems")
    }

    @Test
    fun renderTheReviewReady() {
        onlyWhenAsked()
        val draft = ready()
        rule.setContent {
            val issues = ImportDraftValidation.check(draft) { true }
            Sheet {
                ImportReview(
                    imported = draft,
                    issues = issues,
                    onOpenExercise = {},
                    onEdit = {},
                    onShowWarnings = {},
                    onRestart = {},
                )
            }
        }
        save("d_review_ready")
    }

    /** La hoja (e): el panel a 320 dp con la letra al 130 % y un descanso de 630 s («10 min 30 s»), que no debe cortarse. */
    @Test
    fun renderThePanelAt320WithLargeTextAndLongRest() {
        onlyWhenAsked()
        val draft = ImportedProgramDraft(
            title = "Plan",
            weeks = listOf(
                ImportedWeek(
                    1,
                    listOf(
                        ImportedDay(
                            "w1-d1",
                            "Pierna",
                            listOf(
                                ImportedExercise(
                                    "w1-d1-e1", "Sentadilla", sets = 4, repsMin = 6, repsMax = 8, loadKg = 120.0, restSeconds = 630,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        rule.setContent {
            Sheet(width = 320.dp, fontScale = 1.3f) {
                ImportPreviewWeeks(
                    imported = draft,
                    resolutions = mapOf("Sentadilla" to null),
                    openedExerciseId = "w1-d1-e1",
                    onOpen = {},
                    onEdit = {},
                    suggest = { emptyList() },
                    search = { emptyList() },
                    namesOf = { emptyMap() },
                )
            }
        }
        save("e_panel_320_letra_130_descanso_630")
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

package com.example.kpkn.screens.programs.importer

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.AnnotatedString
import com.example.kpkn.domain.training.importer.ImportDraftValidation
import com.example.kpkn.domain.training.importer.ImportWarning
import com.example.kpkn.domain.training.importer.ImportWarningKind
import com.example.kpkn.domain.training.importer.ImportedDay
import com.example.kpkn.domain.training.importer.ImportedExercise
import com.example.kpkn.domain.training.importer.ImportedProgramDraft
import com.example.kpkn.domain.training.importer.ImportedWeek
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * El panel de avisos con su acción, dentro de un contenedor con estado como la pantalla: aplica cada [ImportEditOp], deja
 * deshacer, muestra la vista previa y la revisión final.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h3000dp-xxhdpi")
class ImportWarningsPanelTest {
    @get:Rule
    val rule = createComposeRule()

    /**
     * Una semana: «Pierna» (Sentadilla decidida y Prensa propia, con el aviso a2 de un valor descartado) y «Empuje» (Press banca
     * propia). Cuatro avisos: a1 fila no entendida de Pierna, a2 valor descartado, a3 día vacío y a4 nota de hoja.
     */
    private fun program(): ImportedProgramDraft = ImportedProgramDraft(
        title = "Plan",
        weeks = listOf(
            ImportedWeek(
                1,
                listOf(
                    ImportedDay(
                        "w1-d1",
                        "Pierna",
                        listOf(ImportedExercise("w1-d1-e1", "Sentadilla", sets = 4, repsMin = 6, repsMax = 6, loadKg = 120.0)),
                    ),
                    ImportedDay("w1-d2", "Empuje", listOf(ImportedExercise("w1-d2-e1", "Press banca", sets = 4, repsMin = 8, repsMax = 8))),
                ),
            ),
        ),
        warnings = listOf(
            ImportWarning(
                id = "a1",
                kind = ImportWarningKind.SKIPPED_ROW,
                location = "Hoja «Pierna», fila 3",
                sourceText = "Remo con barra · 3 · 10",
                message = "Hoja «Pierna», fila 3: «Remo con barra» sin repeticiones; se omite",
                weekIndex = 1,
                dayId = "w1-d1",
                exerciseId = null,
            ),
            ImportWarning(
                id = "a2",
                kind = ImportWarningKind.IGNORED_VALUE,
                location = "Hoja «Pierna», fila 2",
                sourceText = "Sentadilla · 4 · 6 · 120 kg · RPE 12",
                message = "Hoja «Pierna», fila 2: el RPE de «Sentadilla» está fuera de rango; se ignora",
                weekIndex = 1,
                dayId = "w1-d1",
                exerciseId = "w1-d1-e1",
            ),
            ImportWarning(
                id = "a3",
                kind = ImportWarningKind.EMPTY_DAY,
                location = "Hoja «Vacío», fila 1",
                sourceText = "Día 3",
                message = "«Día 3» no tiene ejercicios; se omite",
                weekIndex = 1,
                dayId = null,
                exerciseId = null,
            ),
            ImportWarning(
                id = "a4",
                kind = ImportWarningKind.SHEET_NOTE,
                location = "Hoja «Guía»",
                sourceText = null,
                message = "Hoja «Guía»: se omitieron 2 líneas de texto.",
                weekIndex = null,
                dayId = null,
                exerciseId = null,
            ),
        ),
    )

    /** Lo que decide el catálogo para la sentadilla; el resto, propio. */
    private val decided: Map<String, String?> = mapOf("Sentadilla" to "cfg-squat", "Press banca" to null)

    /** Contenedor con estado: el panel de avisos, la vista previa, la línea de deshacer y la revisión final, como en la pantalla. */
    @Composable
    private fun WarningsHost(initial: ImportedProgramDraft = program()) {
        var snapshot by remember { mutableStateOf(ImportSnapshot(initial, decided)) }
        val undo = remember { ImportUndoStack() }
        var undoLabel by remember { mutableStateOf<String?>(null) }
        var opened by remember { mutableStateOf<String?>(null) }
        var reveal by remember { mutableStateOf(0) }
        val issues = ImportDraftValidation.check(snapshot.imported) { snapshot.resolutions.containsKey(it) }
        val edit: (ImportEditOp) -> Unit = { op ->
            ImportEdits.apply(snapshot, op)?.let { (after, label) ->
                undo.push(snapshot, label)
                snapshot = after
                undoLabel = undo.label
            }
        }
        Column {
            undoLabel?.let { label ->
                ImportUndoLine(
                    label = label,
                    onUndo = {
                        undo.pop()?.let { previous -> snapshot = previous }
                        undoLabel = undo.label
                    },
                )
            }
            ImportWarningsPanel(
                imported = snapshot.imported,
                reveal = reveal,
                search = { emptyList() },
                onOpenExercise = { opened = it },
                onEdit = edit,
            )
            ImportPreviewWeeks(
                imported = snapshot.imported,
                resolutions = snapshot.resolutions,
                openedExerciseId = opened,
                onOpen = { opened = it },
                onEdit = edit,
                suggest = { emptyList() },
                search = { emptyList() },
                namesOf = { emptyMap() },
            )
            ImportReview(
                imported = snapshot.imported,
                issues = issues,
                onOpenExercise = { opened = it },
                onEdit = edit,
                onShowWarnings = { reveal += 1 },
                onRestart = {},
            )
        }
    }

    @Test
    fun cadaTipoDeAvisoTieneSusAcciones() {
        rule.setContent { WarningsHost() }

        rule.onNodeWithText("4 sin revisar").assertExists()
        // Una fila no entendida: añadirla como ejercicio o ignorarla.
        rule.onNodeWithTag("import-warning-add-a1").assertExists()
        rule.onNodeWithTag("import-warning-ignore-a1").assertExists()
        // Un valor descartado: revisar su ejercicio o darlo por visto.
        rule.onNodeWithTag("import-warning-review-a2").assertExists()
        rule.onNodeWithTag("import-warning-ok-a2").assertExists()
        // Un día vacío o una nota de hoja: solo «Entendido».
        rule.onNodeWithTag("import-warning-ok-a3").assertExists()
        rule.onNodeWithTag("import-warning-ok-a4").assertExists()
        rule.onNodeWithTag("import-warning-review-a3").assertDoesNotExist()
        rule.onNodeWithTag("import-warning-add-a2").assertDoesNotExist()
    }

    @Test
    fun anadirComoEjercicioUsaElNombrePropuestoYElDiaElegido() {
        rule.setContent { WarningsHost() }

        rule.onNodeWithTag("import-warning-add-a1").performClick()
        // El nombre sale de la fila; el día por defecto es el del aviso («Pierna»), y 3 × 10 si el texto no dice otra cosa.
        // El campo lleva su etiqueta («Nombre») como texto: se mira el valor escrito.
        rule.onNodeWithTag("import-add-name")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("Remo con barra")))
        rule.onNodeWithText("✓ Día 1 — Pierna").assertExists()
        rule.onNodeWithTag("import-warning-day-w1-d2").performClick()
        rule.onNodeWithText("✓ Día 2 — Empuje").assertExists()
        rule.onNodeWithContentDescription("Más series").performClick()
        rule.onNodeWithContentDescription("Menos repeticiones").performClick()
        rule.onNodeWithContentDescription("Menos repeticiones").performClick()
        rule.onNodeWithTag("import-add-exercise-save").performClick()

        rule.onNodeWithText("Añadiste «Remo con barra» desde un aviso.").assertExists()
        rule.onNodeWithTag("import-exercise-Remo con barra").assertExists()
        // Press banca ya era 4 × 8: el nuevo ejercicio suma otra fila con esa prescripción.
        rule.onAllNodesWithText("4 × 8").assertCountEquals(2)
        rule.onNodeWithTag("import-warning-a1").assertDoesNotExist()
        rule.onNodeWithText("3 sin revisar").assertExists()
    }

    @Test
    fun unDiaNuevoSeLlamaDiaN1() {
        rule.setContent { WarningsHost() }

        rule.onNodeWithTag("import-warning-add-a1").performClick()
        rule.onNodeWithTag("import-warning-day-new").performClick()
        rule.onNodeWithTag("import-add-exercise-save").performClick()

        // La semana 1 ya tiene dos días: el nuevo es «Día 3», aunque el aviso fuera de «Pierna».
        rule.onNodeWithText("Día 3").assertExists()
        rule.onNodeWithTag("import-exercise-Remo con barra").assertExists()
        rule.onNodeWithText("Añadiste «Remo con barra» desde un aviso.").assertExists()
    }

    @Test
    fun deshacerDeUnAnadirDesdeUnAvisoDevuelveElAvisoYQuitaElEjercicio() {
        rule.setContent { WarningsHost() }

        rule.onNodeWithTag("import-warning-add-a1").performClick()
        rule.onNodeWithTag("import-add-exercise-save").performClick()
        rule.onNodeWithTag("import-exercise-Remo con barra").assertExists()

        rule.onNodeWithTag("import-undo").performClick()

        rule.onNodeWithTag("import-exercise-Remo con barra").assertDoesNotExist()
        rule.onNodeWithTag("import-warning-a1").assertExists()
        rule.onNodeWithText("4 sin revisar").assertExists()
    }

    @Test
    fun revisarElEjercicioAbreSuPanelYElAvisoNoCambia() {
        rule.setContent { WarningsHost() }

        rule.onNodeWithTag("import-warning-review-a2").performClick()

        rule.onNodeWithTag("import-edit-panel").assertExists()
        rule.onNodeWithText("4 sin revisar").assertExists()
    }

    @Test
    fun ignorarTodosDejaLaRevisionListaYPliegaLosAvisos() {
        rule.setContent { WarningsHost() }

        rule.onNodeWithTag("import-warnings-ignore-all").performClick()

        rule.onNodeWithTag("import-warnings-folded").assertExists()
        rule.onNodeWithText("Avisos: todo revisado ·").assertExists()
        rule.onNodeWithText("Listo: 1 semana · 2 días · 2 ejercicios.").assertExists()
    }

    @Test
    fun elPanelPlegadoSeDespliegaConVerYSeReplieguaConOcultar() {
        rule.setContent { WarningsHost() }
        rule.onNodeWithTag("import-warnings-ignore-all").performClick()

        rule.onNodeWithTag("import-warnings-open").performClick()
        rule.onNodeWithText("Todo revisado").assertExists()
        rule.onNodeWithTag("import-warnings-reviewed").performClick()
        rule.onNodeWithTag("import-warning-reviewed-a2").assertExists()

        rule.onNodeWithTag("import-warnings-close").performClick()
        rule.onNodeWithTag("import-warnings-folded").assertExists()
    }

    @Test
    fun elDetalleQuitaLaUbicacionQueYaSaleArriba() {
        assertEquals("«Remo con barra» sin repeticiones; se omite", importWarningDetail(program().warnings[0]))
        assertEquals("No se omitió nada", importWarningDetail(program().warnings[0].copy(message = "no se omitió nada", location = "")))
    }

    @Test
    fun elNombrePropuestoQuitaLasCifrasYLaNotacion() {
        assertEquals("Remo con barra", importProposedName("Remo con barra · 3 · 10"))
        assertEquals("Squat", importProposedName("5x5 Squat · 100 kg"))
        assertEquals("Press banca", importProposedName("3 · 8 · Press banca"))
        assertEquals("", importProposedName("12 · 3 · 5"))
        assertEquals("", importProposedName(null))
    }

    @Test
    fun lasSeriesYRepeticionesSeLeenDelTextoOSonTresPorDiez() {
        assertEquals(ImportAddReading(3, 10, 10), importAddReading("Remo con barra · 3 · 10"))
        assertEquals(ImportAddReading(3, 8, 12), importAddReading("3x8-12 Remo"))
        assertEquals(ImportAddReading(5, 5, 5), importAddReading("5x5 Squat"))
        assertEquals(ImportAddReading(3, 10, 10), importAddReading("21x8 Algo"))
    }
}

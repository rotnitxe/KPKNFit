package com.example.kpkn.screens.onboarding.entreno

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.training.importer.ImportedDay
import com.example.kpkn.domain.training.importer.ImportedExercise
import com.example.kpkn.domain.training.importer.ImportedProgramDraft
import com.example.kpkn.domain.training.importer.ImportedWeek
import com.example.kpkn.domain.training.importer.ProgramImportParser
import com.example.kpkn.screens.onboarding.SetupWizardDraft
import com.example.kpkn.screens.onboarding.SetupWizardValidation
import com.example.kpkn.screens.onboarding.importProblem
import com.example.kpkn.screens.programs.importer.ImportEditOp
import com.example.kpkn.screens.programs.importer.ImportPasteField
import com.example.kpkn.screens.programs.importer.ImportPreviewWeeks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * El paso «Importa tu programa»: el texto pegado se lee a semanas, días y ejercicios; un ejercicio con dudas bloquea el
 * paso hasta que la persona lo elige o lo deja como está; y «Leer el texto» solo actúa con texto.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class ProgramImportStepTest {
    @get:Rule
    val rule = createComposeRule()

    private val pasted = "Día 1 — Pierna\nSentadilla 4x6 @120kg\nPrensa 3x10"

    private fun parsed(): ImportedProgramDraft = ProgramImportParser.parseText(pasted, "Mi programa")

    /** Un contenedor con estado para que el panel «Elegir» se abra y cierre como en el paso real. */
    @Composable
    private fun PreviewHost(imported: ImportedProgramDraft, initial: Map<String, String?>, decisions: MutableList<Pair<String, String?>>) {
        var resolutions by remember { mutableStateOf(initial) }
        var opened by remember { mutableStateOf<String?>(null) }
        ImportPreviewWeeks(
            imported = imported,
            resolutions = resolutions,
            openedExerciseId = opened,
            onOpen = { opened = it },
            onEdit = { op ->
                if (op is ImportEditOp.Decide) {
                    decisions += op.rawName to op.configId
                    resolutions = resolutions + (op.rawName to op.configId)
                }
            },
            suggest = { emptyList() },
            search = { emptyList() },
            namesOf = { emptyMap() },
        )
    }

    @Test
    fun pastedTextShowsItsWeekDaysAndExercises() {
        rule.setContent { PreviewHost(parsed(), initial = emptyMap(), decisions = mutableListOf()) }

        rule.onNodeWithText("Semana 1").assertExists()
        rule.onNodeWithText("Sentadilla", substring = true).assertExists()
        rule.onNodeWithText("Prensa", substring = true).assertExists()
    }

    @Test
    fun anExerciseWithDoubtsOffersToChooseAndKeepingItOwnResolvesIt() {
        val decisions = mutableListOf<Pair<String, String?>>()
        rule.setContent { PreviewHost(parsed(), initial = emptyMap(), decisions = decisions) }

        rule.onAllNodesWithText("Elegir").assertCountEquals(2)
        rule.onNodeWithText("Sentadilla", substring = true).performClick()
        rule.onNodeWithTag("import-choice-own").performClick()

        assertEquals(1, decisions.size)
        assertTrue(decisions.single().second == null)
    }

    @Test
    fun theStepStaysBlockedUntilEveryDoubtfulExerciseIsDecided() {
        val imported = parsed()
        val undecided = SetupWizardDraft(importedProgram = imported, selectedWeekdays = setOf(1, 3))
        assertTrue(SetupWizardValidation.validateStep(undecided, SetupStepId.PROGRAM_IMPORT).any { it.isBlocking })
        assertEquals("Elige o deja como está los ejercicios con dudas.", undecided.importProblem())

        val keptAsOwn = undecided.copy(importResolutions = imported.exerciseNames().associateWith { null })
        assertFalse(SetupWizardValidation.validateStep(keptAsOwn, SetupStepId.PROGRAM_IMPORT).any { it.isBlocking })
    }

    @Test
    fun readingThePastedTextNeedsSomeText() {
        var read = 0
        var text by mutableStateOf("")
        rule.setContent {
            ImportPasteField(text = text, onTextChange = { text = it }, onRead = { read++ })
        }

        rule.onNodeWithText("Leer el texto").assertIsNotEnabled()
        rule.onNodeWithTag("import-paste-field").performTextInput(pasted)
        rule.onNodeWithText("Leer el texto").assertIsEnabled().performClick()

        assertEquals(1, read)
    }

    @Test
    fun importProblemNamesTheFirstThingToFixInOrder() {
        fun draftOf(imported: ImportedProgramDraft, resolutions: Map<String, String?>, days: Set<Int> = setOf(1, 3)) =
            SetupWizardDraft(importedProgram = imported, importResolutions = resolutions, selectedWeekdays = days)
        fun decidedAll(imported: ImportedProgramDraft) = imported.exerciseNames().associateWith { null as String? }

        val noExercises = ImportedProgramDraft("E", listOf(ImportedWeek(1, listOf(emptyDay("w1-d1", "Pierna")))))
        assertEquals("No encontramos ejercicios en lo que importaste.", draftOf(noExercises, emptyMap()).importProblem())

        val emptyWeek = ImportedProgramDraft(
            "S",
            listOf(ImportedWeek(1, listOf(dayWith("w1-d1", "Pierna", "Sentadilla"))), ImportedWeek(2, listOf(emptyDay("w2-d1", "Pierna")))),
        )
        assertEquals("Hay semanas sin ejercicios: quítalas o añade ejercicios.", draftOf(emptyWeek, decidedAll(emptyWeek)).importProblem())

        val withEmptyDay = ImportedProgramDraft(
            "D",
            listOf(ImportedWeek(1, listOf(dayWith("w1-d1", "Pierna", "Sentadilla"), emptyDay("w1-d2", "Empuje")))),
        )
        assertEquals("Hay días sin ejercicios: quítalos o añade ejercicios.", draftOf(withEmptyDay, decidedAll(withEmptyDay)).importProblem())

        val withWarning = ProgramImportParser.parseText("Día 1 — Pierna\nSentadilla 4x6 @120kg\nHacer 3 vueltas", "X")
        assertEquals("Revisa los avisos de la importación o ignóralos.", draftOf(withWarning, decidedAll(withWarning)).importProblem())

        val twoSessions = ProgramImportParser.parseText("Día 1 — Pierna\nSentadilla 4x6 @120kg\nDía 2 — Empuje\nPress banca 4x8 @80kg", "X")
        assertEquals(
            "Tu programa tiene 2 sesiones por semana y elegiste 1 días. Cambia los días.",
            draftOf(twoSessions, decidedAll(twoSessions), days = setOf(1)).importProblem(),
        )

        assertNull(draftOf(parsed(), decidedAll(parsed())).importProblem())
    }

    private fun emptyDay(id: String, name: String): ImportedDay = ImportedDay(id = id, name = name, exercises = emptyList())

    private fun dayWith(id: String, name: String, vararg exercises: String): ImportedDay =
        ImportedDay(id = id, name = name, exercises = exercises.mapIndexed { index, exercise ->
            ImportedExercise(id = "$id-e${index + 1}", rawName = exercise, sets = 3, repsMin = 8, repsMax = 8)
        })

    private fun ImportedProgramDraft.exerciseNames(): List<String> =
        weeks.flatMap { week -> week.days.flatMap { day -> day.exercises } }.map { it.rawName }.distinct()
}

package com.example.kpkn.screens.programs.importer

import com.example.kpkn.data.importer.ImportedEditorDocument
import com.example.kpkn.data.importer.ImportedEditorDraftPersistence
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.domain.training.importer.ImportWarning
import com.example.kpkn.domain.training.importer.ImportWarningKind
import com.example.kpkn.domain.training.importer.ImportedProgramDraft
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.async
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImportedEditorDraftSessionTest {
    @get:Rule val folder = TemporaryFolder()

    private fun document(): ImportedEditorDocument {
        val warning = ImportWarning("a1", ImportWarningKind.SKIPPED_ROW, "Fila 2", "Texto original", "Revisar", 1, null, null)
        return ImportedEditorDocument(
            id = "draft", revision = 0, source = ImportedProgramDraft("Origen", emptyList(), warnings = listOf(warning)),
            resolutions = emptyMap(), title = "Plan", weekdays = setOf(1), program = Program(id = "program", name = "Plan"),
        )
    }

    @Test
    fun editedProgramAndReviewedSourceSurviveReopeningTogether() = runTest {
        val file = folder.newFile("draft.json").also { it.delete() }
        val persistence = ImportedEditorDraftPersistence(file)
        val session = ImportedEditorDraftSession.create(persistence, document()).getOrThrow()
        assertTrue(session.programStore.mutate { it.copy(name = "Editado") })
        assertTrue(session.reviewWarning("a1"))
        val restored = ImportedEditorDraftSession.restore(ImportedEditorDraftPersistence(file)).getOrThrow()!!
        assertEquals(2L, restored.document.value.revision)
        assertEquals("Editado", restored.programStore.program.value.name)
        assertEquals("Texto original", restored.document.value.source.warnings.single().sourceText)
        assertEquals(setOf("a1"), restored.document.value.source.reviewedWarnings)
    }

    @Test
    fun staleSessionCannotOverwriteOrDeleteANewerDraft() = runTest {
        val file = folder.newFile("draft.json").also { it.delete() }
        val persistence = ImportedEditorDraftPersistence(file)
        val first = ImportedEditorDraftSession.create(persistence, document()).getOrThrow()
        val stale = ImportedEditorDraftSession.restore(ImportedEditorDraftPersistence(file)).getOrThrow()!!
        assertTrue(first.programStore.mutate { it.copy(name = "Nuevo") })
        assertFalse(stale.programStore.mutate { it.copy(name = "Antiguo") })
        assertFalse(stale.discard().getOrThrow())
        assertEquals("Nuevo", persistence.read().getOrThrow()?.program?.name)
        assertTrue(first.discard().getOrThrow())
        assertEquals(null, persistence.read().getOrThrow())
        assertFalse(first.programStore.mutate { it.copy(name = "Tardío") })
    }

    @Test
    fun reviewingAWarningAndEditingTheProgramDoNotOverwriteEachOther() = runTest {
        val file = folder.newFile("draft.json").also { it.delete() }
        val persistence = ImportedEditorDraftPersistence(file)
        val session = ImportedEditorDraftSession.create(persistence, document()).getOrThrow()
        val edit = async { session.programStore.mutate { it.copy(name = "Editado") } }
        val review = async { session.reviewWarning("a1") }
        assertTrue(edit.await())
        assertTrue(review.await())
        val restored = persistence.read().getOrThrow()!!
        assertEquals(2L, restored.revision)
        assertEquals("Editado", restored.program?.name)
        assertEquals(setOf("a1"), restored.source.reviewedWarnings)
    }

    @Test
    fun reviewingAnImportOnlyDraftDoesNotCreateAParallelProgram() = runTest {
        val file = folder.newFile("import-only-draft.json").also { it.delete() }
        val persistence = ImportedEditorDraftPersistence(file)
        val initial = document().copy(program = null, schemaVersion = 3)
        val session = ImportedEditorDraftSession.create(persistence, initial).getOrThrow()

        assertTrue(session.updateReview(
            source = initial.source,
            resolutions = mapOf("Remo" to "catalog-row"),
            title = "Importado",
            weekdays = setOf(1, 3),
        ))
        assertTrue(session.reviewWarning("a1"))

        val restored = persistence.read().getOrThrow()!!
        assertEquals("Importado", restored.title)
        assertEquals(setOf(1, 3), restored.weekdays)
        assertEquals(mapOf("Remo" to "catalog-row"), restored.resolutions)
        assertEquals(setOf("a1"), restored.source.reviewedWarnings)
        assertEquals(null, restored.program)
    }

    @Test
    fun creatingAnEmptySessionTargetsItsWeekAndDoesNotInventExercisesOrSets() {
        val first = ProgramWeek(id = "week-1", name = "Semana 1", sessions = listOf(Session(id = "session-1", name = "Día 1")))
        val empty = ProgramWeek(id = "week-2", name = "Semana 2", sessions = emptyList())
        val original = Program(
            id = "program",
            name = "Plan",
            macrocycles = listOf(Macrocycle(
                id = "macro",
                name = "Macro",
                blocks = listOf(Block(
                    id = "block",
                    name = "Bloque",
                    mesocycles = listOf(Mesocycle(id = "meso", name = "Meso", weeks = listOf(first, empty))),
                )),
            )),
        )

        val (updated, createdId) = original.withEmptyImportedSession("week-2")!!
        val weeks = updated.macrocycles.single().blocks.single().mesocycles.single().weeks

        assertEquals(listOf("session-1"), weeks.first().sessions.map { it.id })
        val created = weeks.last().sessions.single()
        assertEquals(createdId, created.id)
        assertTrue(created.allExercises().isEmpty())
    }
}

package com.example.kpkn.data.importer

import android.util.AtomicFile
import com.example.kpkn.screens.programs.importer.ImportedEditorDraftSession
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.domain.training.importer.ImportedDay
import com.example.kpkn.domain.training.importer.ImportedExercise
import com.example.kpkn.domain.training.importer.ImportedProgramDraft
import com.example.kpkn.domain.training.importer.ImportedWeek
import com.example.kpkn.domain.training.importer.ImportDocumentType
import com.example.kpkn.domain.training.importer.ImportSourceEvidence
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImportedEditorDraftPersistenceTest {
    @get:Rule val folder = TemporaryFolder()

    /** API 24/25-style rollback files, with a simulated post-finish verification failure. */
    private class FailingVerificationAtomicFile(file: File) : AtomicFile(file) {
        private val backup = File(getBaseFile().path + ".bak")
        var failWriteCalled = false
            private set

        override fun startWrite(): FileOutputStream {
            val base = getBaseFile()
            if (base.exists()) check(base.renameTo(backup))
            return FileOutputStream(base)
        }

        override fun finishWrite(str: FileOutputStream) {
            str.close()
            getBaseFile().writeText("contenido que no coincide")
            // Keep the old revision as Android 24/25 AtomicFile does until a verified finish.
        }

        override fun failWrite(str: FileOutputStream) {
            failWriteCalled = true
            runCatching { str.close() }
            getBaseFile().delete()
            backup.renameTo(getBaseFile())
        }

        override fun openRead(): FileInputStream {
            val base = getBaseFile()
            if (backup.exists()) {
                base.delete()
                check(backup.renameTo(base))
            }
            return FileInputStream(base)
        }
    }

    /** API 24/25 AtomicFile can continue after a failed rename; exercise the caller's rollback guard. */
    private class FailingStartWriteAtomicFile(file: File) : AtomicFile(file) {
        override fun startWrite(): FileOutputStream {
            getBaseFile().writeText("contenido truncado")
            throw java.io.IOException("fallo después de truncar")
        }
    }

    private class FailingDeleteAtomicFile(file: File) : AtomicFile(file) {
        override fun delete() {
            getBaseFile().delete()
            throw java.io.IOException("fallo después de eliminar el archivo base")
        }
    }

    private fun document(): ImportedEditorDocument {
        val exercise = Exercise(id = "e", name = "Remo", sets = listOf(
            ExerciseSet(id = "s1", targetReps = 5, weight = 40.0),
            ExerciseSet(id = "s2", targetReps = 8, targetRIR = 2),
        ))
        val session = Session(id = "s", name = "Día", exercises = listOf(exercise))
        val week = ProgramWeek(id = "w", name = "Semana", sessions = listOf(session))
        val meso = Mesocycle(id = "me", name = "Me", weeks = listOf(week))
        val block = Block(id = "b", name = "B", mesocycles = listOf(meso))
        val macro = Macrocycle(id = "m", name = "M", blocks = listOf(block))
        val program = Program(id = "p", name = "Plan", macrocycles = listOf(macro))
        return ImportedEditorDocument(
            "draft",
            0,
            ImportedProgramDraft(
                title = "Origen",
                weeks = listOf(
                    ImportedWeek(
                        1,
                        listOf(
                            ImportedDay(
                                "w1-d1",
                                "Día A",
                                listOf(ImportedExercise("w1-d1-e1", "Plank", 3, 1, 1, durationSeconds = 40)),
                            ),
                        ),
                    ),
                ),
                sourceTitle = "Rutina de fuerza",
                sourceUrl = "https://training.example",
            ),
            emptyMap(),
            "Plan",
            setOf(1),
            program,
        )
    }

    @Test
    fun roundTripPreservesWholeProgramAndRejectsStaleWritesAndDeletes() = runTest {
        val file = folder.newFile("draft.json").also { it.delete() }
        val first = ImportedEditorDraftPersistence(file)
        val original = document()
        assertTrue(first.write(original, null).isSuccess)
        val reopened = ImportedEditorDraftPersistence(file)
        assertEquals(original, reopened.read().getOrThrow())
        val updated = original.copy(revision = 1, title = "Nuevo")
        assertTrue(reopened.write(updated, 0).isSuccess)
        assertTrue(first.write(original.copy(revision = 1, title = "Antiguo"), 0).isFailure)
        assertFalse(first.delete("draft", 0).getOrThrow())
        assertEquals(updated, first.read().getOrThrow())
    }

    @Test
    fun freshImportDraftCanBeStoredWithoutBuildingAProgram() = runTest {
        val file = folder.newFile("import-only-draft.json").also { it.delete() }
        val persistence = ImportedEditorDraftPersistence(file)
        val importOnly = document().copy(program = null, schemaVersion = 3)

        assertTrue(persistence.write(importOnly, null).isSuccess)
        assertNull(persistence.read().getOrThrow()?.program)
        assertTrue(file.readText().contains("\"program\":null"))
    }

    @Test
    fun reviewedSessionIdsAreSavedOnlyForExistingSessionsAndSurviveRestore() = runTest {
        val file = folder.newFile("draft-reviewed-session.json").also { it.delete() }
        val persistence = ImportedEditorDraftPersistence(file)
        val session = ImportedEditorDraftSession.create(persistence, document()).getOrThrow()

        assertFalse(session.markSessionReviewed("missing"))
        assertTrue(session.markSessionReviewed("s"))
        assertEquals(setOf("s"), session.document.value.reviewedSessionIds)
        assertTrue(session.markSessionReviewed("s"))

        val restored = ImportedEditorDraftSession.restore(ImportedEditorDraftPersistence(file)).getOrThrow()
        assertEquals(setOf("s"), restored?.document?.value?.reviewedSessionIds)
    }

    @Test
    fun `drafts previos sin cita web siguen abriendose`() = runTest {
        val file = folder.newFile("draft-legacy.json").also { it.delete() }
        val persistence = ImportedEditorDraftPersistence(file)
        val original = document()
        assertTrue(persistence.write(original, null).isSuccess)

        val legacyJson = file.readText()
            .replace(Regex(",\"sourceTitle\":\"[^\"]*\""), "")
            .replace(Regex(",\"sourceUrl\":\"[^\"]*\""), "")
        val sourceStart = legacyJson.indexOf("\"source\":")
        val sourceEnd = legacyJson.indexOf(",\"resolutions\":", sourceStart)
        val legacySource = legacyJson.substring(sourceStart, sourceEnd)
        assertFalse("\"sourceTitle\"" in legacySource)
        assertFalse("\"sourceUrl\"" in legacySource)
        file.writeText(legacyJson)

        assertEquals(
            original.copy(source = original.source.copy(sourceTitle = null, sourceUrl = null)),
            ImportedEditorDraftPersistence(file).read().getOrThrow(),
        )
    }

    @Test
    fun `drafts previos sin el campo de duracion siguen abriendose`() = runTest {
        val file = folder.newFile("draft-without-duration.json").also { it.delete() }
        val original = document()
        assertTrue(ImportedEditorDraftPersistence(file).write(original, null).isSuccess)

        val legacyJson = file.readText().replace(Regex(",\\\"durationSeconds\\\":40"), "")
        assertFalse("durationSeconds" in legacyJson)
        file.writeText(legacyJson)

        val oldDraft = original.source.copy(
            weeks = original.source.weeks.map { week ->
                week.copy(days = week.days.map { day ->
                    day.copy(exercises = day.exercises.map { it.copy(durationSeconds = null) })
                })
            },
        )
        assertEquals(oldDraft, ImportedEditorDraftPersistence(file).read().getOrThrow()?.source)
    }

    @Test
    fun concurrentInstancesCannotBothCommitTheSameRevision() = runTest {
        val file = folder.newFile("draft.json").also { it.delete() }
        val a = ImportedEditorDraftPersistence(file)
        val b = ImportedEditorDraftPersistence(file)
        val original = document()
        assertTrue(a.write(original, null).isSuccess)
        val first = async { a.write(original.copy(revision = 1, title = "A"), 0) }
        val second = async { b.write(original.copy(revision = 1, title = "B"), 0) }
        assertEquals(1, listOf(first.await(), second.await()).count { it.isSuccess })
        assertEquals(1L, a.read().getOrThrow()?.revision)
    }

    @Test
    fun replacingRequiresTheCurrentVersionAndIsAtomicFromTheReadersPerspective() = runTest {
        val file = folder.newFile("draft.json").also { it.delete() }
        val persistence = ImportedEditorDraftPersistence(file)
        val original = document()
        val replacement = original.copy(id = "replacement", title = "Otro")
        assertTrue(persistence.write(original, null).isSuccess)
        assertTrue(persistence.replace(replacement, "draft", 1).isFailure)
        assertEquals(original, persistence.read().getOrThrow())
        assertTrue(persistence.replace(replacement, "draft", 0).isSuccess)
        assertEquals(replacement, ImportedEditorDraftPersistence(file).read().getOrThrow())
        assertFalse(persistence.delete("draft", 0).getOrThrow())
    }

    @Test
    fun corruptDraftIsNotOverwrittenOrDeleted() = runTest {
        val file = folder.newFile("draft.json")
        file.writeText("contenido inválido")
        val persistence = ImportedEditorDraftPersistence(file)
        assertTrue(persistence.read().isFailure)
        assertTrue(persistence.write(document(), null).isFailure)
        assertTrue(persistence.delete("draft", 0).isFailure)
        assertEquals("contenido inválido", file.readText())
    }

    @Test
    @Config(sdk = [24])
    fun postFinishVerificationFailureLeavesThePreviousDraftRecoverable() = runTest {
        val file = folder.newFile("draft.json").also { it.delete() }
        val original = document()
        assertTrue(ImportedEditorDraftPersistence(file).write(original, null).isSuccess)

        val atomicFile = FailingVerificationAtomicFile(file)
        val persistence = ImportedEditorDraftPersistence(file, atomicFile)
        val attempted = original.copy(revision = 1, title = "Nuevo")

        assertTrue(persistence.write(attempted, 0).isFailure)
        assertFalse("no se debe llamar failWrite después de finishWrite", atomicFile.failWriteCalled)
        assertEquals(original, persistence.read().getOrThrow())
    }

    @Test
    @Config(sdk = [24])
    fun startWriteFailureCannotReplaceThePreviousDraft() = runTest {
        val file = folder.newFile("draft.json").also { it.delete() }
        val original = document()
        assertTrue(ImportedEditorDraftPersistence(file).write(original, null).isSuccess)

        val persistence = ImportedEditorDraftPersistence(file, FailingStartWriteAtomicFile(file))
        assertTrue(persistence.write(original.copy(revision = 1, title = "Nuevo"), 0).isFailure)
        assertEquals(original, ImportedEditorDraftPersistence(file).read().getOrThrow())
    }

    @Test
    @Config(sdk = [24])
    fun failedFirstWriteDoesNotLeaveAnUnreadableDraft() = runTest {
        val file = folder.newFile("draft.json").also { it.delete() }
        val persistence = ImportedEditorDraftPersistence(file, FailingStartWriteAtomicFile(file))

        assertTrue(persistence.write(document(), null).isFailure)
        assertNull(ImportedEditorDraftPersistence(file).read().getOrThrow())
        assertTrue(folder.root.listFiles().orEmpty().any { it.name.startsWith("draft.json.uncommitted-") })
    }

    @Test
    @Config(sdk = [24])
    fun interruptedFirstWriteIsQuarantinedWhenTheDraftIsReopened() = runTest {
        val file = folder.newFile("draft.json").also { it.delete() }
        File(file.path + ".rollback-empty").writeBytes(byteArrayOf())
        file.writeText("escritura interrumpida")
        File(file.path + ".new").writeText("pendiente interrumpido")

        assertNull(ImportedEditorDraftPersistence(file).read().getOrThrow())
        assertFalse(file.exists())
        val preserved = folder.root.listFiles().orEmpty().map { it.name }
        assertTrue(preserved.any { it.startsWith("draft.json.uncommitted-") })
        assertTrue(preserved.any { it.startsWith("draft.json.new.uncommitted-") })
    }

    @Test
    @Config(sdk = [24])
    fun failedDeleteRestoresThePreviousDraft() = runTest {
        val file = folder.newFile("draft.json").also { it.delete() }
        val original = document()
        assertTrue(ImportedEditorDraftPersistence(file).write(original, null).isSuccess)

        val persistence = ImportedEditorDraftPersistence(file, FailingDeleteAtomicFile(file))
        assertTrue(persistence.delete("draft", 0).isFailure)
        assertEquals(original, ImportedEditorDraftPersistence(file).read().getOrThrow())
    }

    @Test
    fun legacyAtomicBackupIsRecoveredEvenWhenBaseFileIsMissing() = runTest {
        val file = folder.newFile("draft.json").also { it.delete() }
        val persistence = ImportedEditorDraftPersistence(file)
        val original = document()
        assertTrue(persistence.write(original, null).isSuccess)
        val backup = java.io.File(file.path + ".bak")
        assertTrue(file.renameTo(backup))
        assertEquals(original, ImportedEditorDraftPersistence(file).read().getOrThrow())
    }

    @Test
    fun versionOneDraftsUpgradeWithDefaultWizardRouteAndEvidence() = runTest {
        val file = folder.newFile("draft-v1.json").also { it.delete() }
        val persistence = ImportedEditorDraftPersistence(file)
        val original = document()
        assertTrue(persistence.write(original, null).isSuccess)

        val encoded = kotlinx.serialization.json.Json.parseToJsonElement(file.readText()) as kotlinx.serialization.json.JsonObject
        val legacy = kotlinx.serialization.json.JsonObject(encoded.toMutableMap().apply {
            remove("wizardPage")
            remove("wizardSheet")
            remove("sourceEvidence")
            remove("reviewedSessionIds")
            this["schemaVersion"] = kotlinx.serialization.json.JsonPrimitive(1)
        })
        file.writeText(kotlinx.serialization.json.Json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), legacy))

        val reopened = ImportedEditorDraftPersistence(file).read().getOrThrow()!!
        assertEquals(3, reopened.schemaVersion)
        assertEquals(com.example.kpkn.domain.training.importer.ImportWizardPage.EXERCISES, reopened.wizardPage)
        assertNull(reopened.wizardSheet)
        assertEquals(com.example.kpkn.domain.training.importer.ImportSourceEvidence(), reopened.sourceEvidence)
    }

    @Test
    fun replacingAndDeletingDraftAlsoRemovesItsPrivateOriginal() = runTest {
        val file = folder.newFile("draft-media.json").also { it.delete() }
        val media = ImportSourceMediaStore(folder.root)
        val firstId = requireNotNull(media.retainBytes("%PDF-first".toByteArray(), ImportDocumentType.PDF))
        val persistence = ImportedEditorDraftPersistence(file, sourceMediaStore = media)
        val initial = document().copy(sourceEvidence = ImportSourceEvidence(
            kind = com.example.kpkn.domain.training.importer.ImportSourceKind.FILE,
            documentType = ImportDocumentType.PDF,
            sourceMediaId = firstId,
        ))
        assertTrue(persistence.write(initial, null).isSuccess)
        assertTrue(requireNotNull(media.fileFor(firstId, ImportDocumentType.PDF)).exists())

        val secondId = requireNotNull(media.retainBytes("%PDF-second".toByteArray(), ImportDocumentType.PDF))
        val replacement = initial.copy(
            id = "replacement",
            revision = 0,
            sourceEvidence = initial.sourceEvidence.copy(sourceMediaId = secondId),
        )
        assertTrue(persistence.replace(replacement, initial.id, initial.revision).isSuccess)
        assertNull(media.fileFor(firstId, ImportDocumentType.PDF))
        assertTrue(requireNotNull(media.fileFor(secondId, ImportDocumentType.PDF)).exists())

        assertEquals(true, persistence.delete(replacement.id, replacement.revision).getOrThrow())
        assertNull(media.fileFor(secondId, ImportDocumentType.PDF))
    }

    private suspend fun repeatedReplacementKeepsTheLastCompleteRevision() {
        val file = folder.newFile("repeated-${android.os.Build.VERSION.SDK_INT}.json").also { it.delete() }
        val persistence = ImportedEditorDraftPersistence(file)
        var expectedRevision: Long? = null
        repeat(12) { index ->
            val next = document().copy(
                revision = index.toLong(),
                title = "Reemplazo $index",
                program = requireNotNull(document().program).copy(name = "Programa $index"),
            )
            assertTrue("falló la escritura $index en API ${android.os.Build.VERSION.SDK_INT}", persistence.write(next, expectedRevision).isSuccess)
            assertEquals("lectura distinta tras la escritura $index", next, persistence.read().getOrThrow())
            expectedRevision = index.toLong()
        }
    }

    @Test
    @Config(sdk = [24])
    fun repeatedlyReplacesExistingDraftOnApi24() = runTest {
        repeatedReplacementKeepsTheLastCompleteRevision()
    }

    @Test
    @Config(sdk = [25])
    fun repeatedlyReplacesExistingDraftOnApi25() = runTest {
        repeatedReplacementKeepsTheLastCompleteRevision()
    }

    @Test
    @Config(sdk = [34])
    fun repeatedlyReplacesExistingDraftOnModernAndroid() = runTest {
        repeatedReplacementKeepsTheLastCompleteRevision()
    }
}

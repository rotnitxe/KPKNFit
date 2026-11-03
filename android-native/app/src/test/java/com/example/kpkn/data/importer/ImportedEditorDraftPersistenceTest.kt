package com.example.kpkn.data.importer

import android.util.AtomicFile
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.domain.training.importer.ImportedProgramDraft
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
        return ImportedEditorDocument("draft", 0, ImportedProgramDraft("Origen", emptyList()), emptyMap(), "Plan", setOf(1), program)
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

    private suspend fun repeatedReplacementKeepsTheLastCompleteRevision() {
        val file = folder.newFile("repeated-${android.os.Build.VERSION.SDK_INT}.json").also { it.delete() }
        val persistence = ImportedEditorDraftPersistence(file)
        var expectedRevision: Long? = null
        repeat(12) { index ->
            val next = document().copy(
                revision = index.toLong(),
                title = "Reemplazo $index",
                program = document().program.copy(name = "Programa $index"),
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

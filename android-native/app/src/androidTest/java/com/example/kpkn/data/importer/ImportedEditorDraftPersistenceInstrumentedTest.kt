package com.example.kpkn.data.importer

import android.os.Build
import android.util.AtomicFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
import com.example.kpkn.domain.training.importer.ImportedProgramSourceMap
import com.example.kpkn.domain.training.importer.ImportedWeek
import com.example.kpkn.domain.training.importer.ImportWarning
import com.example.kpkn.domain.training.importer.ImportWarningKind
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the platform AtomicFile and filesystem on the two oldest supported Android APIs. */
@RunWith(AndroidJUnit4::class)
class ImportedEditorDraftPersistenceInstrumentedTest {
    private val runId = "api${Build.VERSION.SDK_INT}-${System.nanoTime()}"
    private val file: File by lazy {
        File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "importer-draft-$runId.json")
    }

    private class FailAfterTruncatingAtomicFile(file: File) : AtomicFile(file) {
        override fun startWrite(): FileOutputStream {
            val base = baseFile
            val backup = File(base.path + ".bak")
            if (base.exists()) {
                if (backup.exists() || !base.renameTo(backup)) throw IOException("could not stage AtomicFile backup")
            }
            FileOutputStream(base).use { stream ->
                stream.write("partial uncommitted bytes".toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            throw IOException("injected failure after truncating the base file")
        }
    }

    @Test
    fun repeatedPlatformWritesPreserveTheFullProgramSourceAndWarnings() = runBlocking {
        assertTrue("This test is intended for Android API 24 or 25", Build.VERSION.SDK_INT in 24..25)
        val initial = document(revision = 0)
        val persistence = ImportedEditorDraftPersistence(file)

        try {
            assertTrue("first write failed on API ${Build.VERSION.SDK_INT}", persistence.write(initial, null).isSuccess)
            assertEquals(initial, ImportedEditorDraftPersistence(file).read().getOrThrow())

            var expectedRevision = 0L
            repeat(12) { index ->
                val next = initial.copy(
                    revision = index + 1L,
                    title = "Plan revision ${index + 1}",
                    program = initial.program.copy(name = "Program revision ${index + 1}"),
                )
                assertTrue(
                    "replacement $index failed on API ${Build.VERSION.SDK_INT}",
                    persistence.write(next, expectedRevision).isSuccess,
                )
                assertEquals("replacement $index was not readable", next, ImportedEditorDraftPersistence(file).read().getOrThrow())
                expectedRevision = next.revision
            }
        } finally {
            deleteOnlyThisTestFileFamily()
        }
    }

    @Test
    fun failedPlatformWriteRetainsThePreviouslyCommittedDraft() = runBlocking {
        assertTrue("This test is intended for Android API 24 or 25", Build.VERSION.SDK_INT in 24..25)
        val original = document(revision = 0)

        try {
            assertTrue(ImportedEditorDraftPersistence(file).write(original, null).isSuccess)
            val failing = ImportedEditorDraftPersistence(file, FailAfterTruncatingAtomicFile(file))
            val attempted = original.copy(revision = 1, title = "Never committed")

            assertTrue("injected write failure was reported as success", failing.write(attempted, 0).isFailure)
            assertEquals("failed replacement overwrote the last good draft", original, ImportedEditorDraftPersistence(file).read().getOrThrow())
        } finally {
            deleteOnlyThisTestFileFamily()
        }
    }

    private fun document(revision: Long): ImportedEditorDocument {
        val sourceExerciseId = "source-row-$runId"
        val sessionId = "session-$runId"
        val exerciseId = "exercise-$runId"
        val source = ImportedProgramDraft(
            title = "Programa original",
            weeks = listOf(
                ImportedWeek(
                    index = 1,
                    days = listOf(
                        ImportedDay(
                            id = "day-1",
                            name = "Día 1",
                            exercises = listOf(
                                ImportedExercise(
                                    id = sourceExerciseId,
                                    rawName = "Remo con barra",
                                    sets = 2,
                                    repsMin = 5,
                                    repsMax = 8,
                                    loadKg = 40.0,
                                    rpe = 8.0,
                                    restSeconds = 120,
                                    sourceText = "Remo con barra 2x5-8 @40 kg",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            warnings = listOf(
                ImportWarning(
                    id = "a1",
                    kind = ImportWarningKind.SHEET_NOTE,
                    location = "Hoja Resumen",
                    sourceText = "Totales semanales",
                    message = "Se conservó una nota informativa de la hoja",
                    weekIndex = null,
                    dayId = null,
                    exerciseId = null,
                ),
            ),
            loadUnit = com.example.kpkn.domain.training.importer.ImportLoadUnit.KG,
        )
        val exercise = Exercise(
            id = exerciseId,
            name = "Remo con barra",
            sets = listOf(
                ExerciseSet(id = "set-1", targetReps = 5, weight = 40.0),
                ExerciseSet(id = "set-2", targetReps = 8, targetRIR = 2),
            ),
        )
        val session = Session(id = sessionId, name = "Día 1", exercises = listOf(exercise))
        val program = Program(
            id = "program-$runId",
            name = "Programa importado",
            macrocycles = listOf(
                Macrocycle(
                    id = "macro-$runId",
                    name = "Macrociclo",
                    blocks = listOf(
                        Block(
                            id = "block-$runId",
                            name = "Bloque",
                            mesocycles = listOf(
                                Mesocycle(
                                    id = "meso-$runId",
                                    name = "Mesociclo",
                                    weeks = listOf(
                                        ProgramWeek(
                                            id = "week-$runId",
                                            name = "Semana 1",
                                            sessions = listOf(session),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        return ImportedEditorDocument(
            id = "draft-$runId",
            revision = revision,
            source = source,
            resolutions = mapOf(sourceExerciseId to "row:barbell-row"),
            title = "Programa importado",
            weekdays = setOf(1, 3),
            program = program,
            sourceMap = ImportedProgramSourceMap(
                sessionIdsByDayId = mapOf("day-1" to sessionId),
                programExerciseIdsBySourceExerciseId = mapOf(sourceExerciseId to exerciseId),
            ),
            editingStarted = true,
        )
    }

    private fun deleteOnlyThisTestFileFamily() {
        val prefix = file.name
        file.parentFile?.listFiles()?.filter { it.name == prefix || it.name.startsWith("$prefix.") }?.forEach { it.delete() }
    }
}

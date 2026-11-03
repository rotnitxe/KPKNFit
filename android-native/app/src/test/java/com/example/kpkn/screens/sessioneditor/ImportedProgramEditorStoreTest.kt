package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.Program
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportedProgramEditorStoreTest {
    @Test
    fun cancellationDuringPersistenceDoesNotLeavePublishedStateBehindDurableState() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var durable: Program? = null
        val store = ImportedProgramEditorStore(Program(id = "draft", name = "Antes")) {
            started.complete(Unit)
            release.await()
            durable = it
            true
        }
        val edit = launch { store.mutate { it.copy(name = "Después") } }
        started.await()
        edit.cancel()
        release.complete(Unit)
        edit.join()
        assertEquals("Después", durable?.name)
        assertEquals(durable, store.program.value)
    }

    @Test
    fun failedPersistenceDoesNotPublishOrChangeIdentity() = runTest {
        val original = Program(id = "draft", name = "Antes")
        val store = ImportedProgramEditorStore(original) { false }
        assertFalse(store.mutate { it.copy(name = "Después") })
        assertEquals(original, store.program.value)
        assertFalse(store.mutate { it.copy(id = "otro") })
        assertEquals(original, store.program.value)
    }

    @Test
    fun concurrentEditsArePersistedInOrderWithoutLosingTheFirstChange() = runTest {
        val firstWriteStarted = CompletableDeferred<Unit>()
        val releaseFirstWrite = CompletableDeferred<Unit>()
        val persisted = mutableListOf<Program>()
        val store = ImportedProgramEditorStore(Program(id = "draft", name = "Plan")) { program ->
            if (persisted.isEmpty()) {
                firstWriteStarted.complete(Unit)
                releaseFirstWrite.await()
            }
            persisted += program
            true
        }
        val first = async { store.mutate { it.copy(name = "Editado") } }
        firstWriteStarted.await()
        val second = async { store.mutate { it.copy(description = "Nota") } }
        releaseFirstWrite.complete(Unit)
        assertTrue(first.await())
        assertTrue(second.await())
        assertEquals(listOf("Editado", "Editado"), persisted.map { it.name })
        assertEquals("Nota", store.program.value.description)
    }
}

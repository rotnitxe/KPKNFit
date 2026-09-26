package com.example.kpkn.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toProgram
import com.example.kpkn.data.models.OptionalSessionConfirmation
import com.example.kpkn.data.models.Program
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

/** Real Room coverage for durable program read-modify-write mutations. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class ProgramRepositoryMutateProgramNowTest {

    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        ProgramRepository.closeInstance()
        KpknDatabase.closeInstance()
        Dispatchers.resetMain()
    }

    private suspend fun freshRepository(): ProgramRepository {
        val repository = ProgramRepository.initForTests(
            ApplicationProvider.getApplicationContext<Context>(),
        )
        withTimeout(10_000) { repository.isReady.first { it } }
        repository.resetAllStateSync()
        return repository
    }

    @Test
    fun rapid_mutations_accumulate_in_room_and_cache_before_returning() = runBlocking {
        val repository = freshRepository()
        val room = repository.databaseForTests()
        val program = Program(id = "serial-rmw", name = "Base")
        assertTrue(repository.addProgramNow(program).isSuccess)

        val firstCommitReached = CompletableDeferred<Unit>()
        val releaseFirstCommit = CompletableDeferred<Unit>()
        val commitCount = AtomicInteger()
        repository.durableCommitInterleaverForTests = {
            if (commitCount.incrementAndGet() == 1) {
                // Pausa tras el upsert y antes de publicar. La segunda mutación
                // se lanza mientras la primera conserva el lane RMW.
                firstCommitReached.complete(Unit)
                releaseFirstCommit.await()
            }
        }

        val firstConfirmation = OptionalSessionConfirmation("2031-04-07", "optional-a")
        val secondConfirmation = OptionalSessionConfirmation("2031-04-09", "optional-b")
        val first = async(Dispatchers.Default) {
            repository.mutateProgramNow(program.id) { current ->
                current.copy(optionalSessionConfirmations = current.optionalSessionConfirmations + firstConfirmation)
            }
        }
        try {
            withTimeout(10_000) { firstCommitReached.await() }
            val secondStarted = CompletableDeferred<Unit>()
            val second = async(Dispatchers.Default) {
                secondStarted.complete(Unit)
                repository.mutateProgramNow(program.id) { current ->
                    current.copy(optionalSessionConfirmations = current.optionalSessionConfirmations + secondConfirmation)
                }
            }
            withTimeout(10_000) { secondStarted.await() }
            releaseFirstCommit.complete(Unit)

            assertTrue("primera mutación solo devuelve tras su commit", withTimeout(10_000) { first.await() })
            assertTrue("segunda mutación solo devuelve tras su commit", withTimeout(10_000) { second.await() })
        } finally {
            releaseFirstCommit.complete(Unit)
            repository.durableCommitInterleaverForTests = null
        }

        val expectedKeys = setOf(
            firstConfirmation.dayIso to firstConfirmation.sessionId,
            secondConfirmation.dayIso to secondConfirmation.sessionId,
        )
        val cached = repository.getProgramById(program.id)
        assertNotNull(cached)
        assertEquals(
            expectedKeys,
            cached!!.optionalSessionConfirmations.map { it.dayIso to it.sessionId }.toSet(),
        )
        val persisted = room.programDao().getById(program.id)?.toProgram()
        assertEquals(
            expectedKeys,
            persisted?.optionalSessionConfirmations?.map { it.dayIso to it.sessionId }?.toSet(),
        )
    }

    @Test
    fun real_room_failure_propagates_without_publishing_the_candidate() = runBlocking {
        val repository = freshRepository()
        val original = Program(
            id = "failed-rmw",
            name = "Persistido",
            optionalSessionConfirmations = listOf(
                OptionalSessionConfirmation("2031-04-07", "already-confirmed"),
            ),
        )
        assertTrue(repository.addProgramNow(original).isSuccess)
        val cachedBefore = repository.getProgramById(original.id)
        assertEquals(original.optionalSessionConfirmations, cachedBefore?.optionalSessionConfirmations)

        // Cierre real de Room: el siguiente upsert falla después del transform,
        // pero antes de que el candidato pueda llegar a la caché.
        repository.databaseForTests().close()
        val outcome = runCatching {
            repository.mutateProgramNow(original.id) { latest ->
                latest.copy(name = "No debe publicarse")
            }
        }

        assertTrue("el error de Room no puede convertirse en éxito", outcome.isFailure)
        assertEquals(cachedBefore, repository.getProgramById(original.id))
        assertEquals("Persistido", repository.getProgramById(original.id)?.name)
        assertEquals(original.optionalSessionConfirmations, repository.getProgramById(original.id)?.optionalSessionConfirmations)
    }

    @Test
    fun superseded_mutation_returns_false_and_never_publishes_its_candidate() = runBlocking {
        val repository = freshRepository()
        val room = repository.databaseForTests()
        val original = Program(id = "stale-rmw", name = "Base")
        assertTrue(repository.addProgramNow(original).isSuccess)

        repository.durableCommitBeforeWriteForTests = {
            // Escritor legacy concurrente: reserva y publica la versión nueva
            // antes del chequeo durable; su Room write espera este lane.
            repository.updateProgram(original.copy(name = "Escritura más nueva"))
        }
        val committed = try {
            repository.mutateProgramNow(original.id) { current ->
                current.copy(name = "Mutación obsoleta")
            }
        } finally {
            repository.durableCommitBeforeWriteForTests = null
        }

        assertFalse("una versión obsoleta no es un commit durable", committed)
        assertEquals("Escritura más nueva", repository.getProgramById(original.id)?.name)
        withTimeout(10_000) {
            while (room.programDao().getById(original.id)?.toProgram()?.name != "Escritura más nueva") {
                delay(10)
            }
        }
        assertEquals("Escritura más nueva", room.programDao().getById(original.id)?.toProgram()?.name)
    }
}

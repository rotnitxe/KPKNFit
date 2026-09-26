package com.example.kpkn.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toProgram
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.persistence.PersistenceWriteCoordinator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Real Room/Robolectric seam para el guardado DURABLE
 * [ProgramRepository.addProgramNow]: conflicto detectado, publicación coherente
 * entre Room y caché, y cancelación pendiente que no deja la fila durable sin
 * su caché.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class ProgramRepositoryAddProgramNowTest {

    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        // El bootstrap publica readiness en Dispatchers.Main; con el looper de
        // Robolectric pausado la carga nunca terminaría.
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        ProgramRepository.closeInstance()
        KpknDatabase.closeInstance()
        Dispatchers.resetMain()
    }

    /** Sostiene el lane de storage hasta que el test lo libera. */
    private fun CoroutineScope.holdWriteLane(
        gate: CompletableDeferred<Unit>,
        held: CompletableDeferred<Unit>,
    ): Job = launch(Dispatchers.IO) {
        PersistenceWriteCoordinator.mutex.withLock {
            held.complete(Unit)
            gate.await()
        }
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
    fun superseded_commit_reports_conflict_without_stale_db_or_cache_write() = runBlocking {
        val repository = freshRepository()
        val program = Program(id = "race-program", name = "base")
        val room = repository.databaseForTests()

        // Estado inicial ya materializado (caché + Room) para que la carrera
        // sea entre dos escrituras reales del MISMO id.
        repository.addProgram(program)
        withTimeout(10_000) {
            while (room.programDao().getById(program.id) == null) delay(10)
        }
        assertEquals("base", repository.getProgramById(program.id)?.name)

        val gate = CompletableDeferred<Unit>()
        val gateHeld = CompletableDeferred<Unit>()
        val lane = holdWriteLane(gate, gateHeld)
        gateHeld.await()

        // v1 reserva su versión y queda esperando el lock de storage.
        // UNDISPATCHED garantiza la reserva ANTES de cualquier otra escritura.
        var durableResult: Result<Unit>? = null
        val durable = launch(start = CoroutineStart.UNDISPATCHED) {
            durableResult = repository.addProgramNow(program.copy(name = "v1"))
        }

        // v2 llega después: reserva una versión más nueva y publica su caché.
        repository.updateProgram(program.copy(name = "v2"))

        gate.complete(Unit)
        withTimeout(10_000) { durable.join() }
        withTimeout(10_000) { lane.join() }

        // El commit obsoleto NO informa éxito: es conflicto explícito.
        assertTrue(
            "el commit superseded debe fallar, no devolver éxito",
            durableResult?.isFailure == true,
        )
        assertTrue(
            "el fallo debe ser un conflicto de escritura",
            durableResult?.exceptionOrNull() is ProgramWriteConflictException,
        )
        // Tampoco publica estado viejo en la caché: sigue mandando v2.
        assertEquals("v2", repository.getProgramById(program.id)?.name)

        // Room solo puede reflejar la escritora más nueva, nunca v1.
        withTimeout(10_000) {
            while (room.programDao().getById(program.id)?.toProgram()?.name != "v2") {
                delay(10)
            }
        }
        assertEquals("v2", room.programDao().getById(program.id)?.toProgram()?.name)
        assertEquals(1, room.programDao().getAllHeaders().count { it.id == program.id })
    }

    @Test
    fun cancellation_during_pending_commit_publishes_room_and_cache_together() = runBlocking {
        val repository = freshRepository()

        val gate = CompletableDeferred<Unit>()
        val gateHeld = CompletableDeferred<Unit>()
        val lane = holdWriteLane(gate, gateHeld)
        gateHeld.await()

        val program = Program(id = "cancel-coherence", name = "Cancelada")
        val durable = launch(start = CoroutineStart.UNDISPATCHED) {
            repository.addProgramNow(program)
        }

        // El caller se cancela mientras el commit aún espera su turno: el
        // commit es NonCancellable, así que se completa; su publicación también.
        durable.cancel()
        gate.complete(Unit)
        withTimeout(10_000) { durable.join() }
        withTimeout(10_000) { lane.join() }

        val roomRow = repository.databaseForTests().programDao().getById(program.id)
        val cached = repository.getProgramById(program.id)

        assertNotNull("Room conserva el commit confirmado", roomRow)
        assertNotNull("la caché se publica aunque el caller cancele", cached)
        assertEquals(program.id, cached?.id)
        assertEquals("Cancelada", cached?.name)
    }

    @Test
    fun durable_save_is_row_backed_and_repeat_writes_no_duplicate() = runBlocking {
        val repository = freshRepository()
        val program = Program(id = "idempotent", name = "Uno")
        val room = repository.databaseForTests()

        val first = repository.addProgramNow(program)
        assertTrue(first.isSuccess)
        assertEquals("Uno", room.programDao().getById(program.id)?.toProgram()?.name)
        assertEquals("Uno", repository.getProgramById(program.id)?.name)

        val second = repository.addProgramNow(program.copy(name = "Dos"))
        assertTrue(second.isSuccess)
        assertEquals("Dos", room.programDao().getById(program.id)?.toProgram()?.name)
        assertEquals(1, room.programDao().getAllHeaders().count { it.id == program.id })
        assertEquals(1, repository.programs.value.count { it.id == program.id })
        assertEquals("Dos", repository.getProgramById(program.id)?.name)
        // El alta durable NO auto-activa: eso lo decide el detalle.
        assertNull(repository.activeProgramState.value)
    }

    @Test
    fun v2_reserving_between_db_write_and_publish_wins_and_durable_reports_conflict() = runBlocking {
        val repository = freshRepository()
        val room = repository.databaseForTests()
        val program = Program(id = "interleave", name = "base")

        repository.addProgram(program)
        withTimeout(10_000) { while (room.programDao().getById(program.id) == null) delay(10) }
        assertEquals("base", repository.getProgramById(program.id)?.name)

        // v2 llega ENTRE el upsert durable y su chequeo+publicación. Dentro del
        // hook la fila de Room YA es v1 pero la caché todavía no se tocó.
        var cacheBeforePublish: String? = null
        var cacheAfterNewerReservation: String? = null
        repository.durableCommitInterleaverForTests = {
            cacheBeforePublish = repository.getProgramById(program.id)?.name
            repository.updateProgram(program.copy(name = "v2"))
            cacheAfterNewerReservation = repository.getProgramById(program.id)?.name
        }
        val result = repository.addProgramNow(program.copy(name = "v1"))
        repository.durableCommitInterleaverForTests = null

        // El hook corrió tras el upsert y antes de publicar: v1 solo está en Room.
        assertEquals("base", cacheBeforePublish)
        // Reserva + publicación de v2 son indivisibles: su caché ya es la nueva
        // y el durable no puede escribirse encima después.
        assertEquals("v2", cacheAfterNewerReservation)
        // Perdió la versión DESPUÉS del upsert: conflicto, nunca un éxito que
        // describa una caché que no llegó a publicarse.
        assertTrue(
            "el commit superseded tras el upsert debe informar conflicto",
            result.isFailure,
        )
        assertTrue(result.exceptionOrNull() is ProgramWriteConflictException)
        assertEquals("v2", repository.getProgramById(program.id)?.name)

        // Room converge a la escritora más nueva (su persistencia corre al
        // liberarse los locks de storage) y jamás queda la fila v1.
        withTimeout(10_000) {
            while (room.programDao().getById(program.id)?.toProgram()?.name != "v2") delay(10)
        }
        assertEquals("v2", room.programDao().getById(program.id)?.toProgram()?.name)
        assertEquals(1, room.programDao().getAllHeaders().count { it.id == program.id })
    }

    @Test
    fun durable_write_with_interleaved_delete_does_not_resurrect_cache() = runBlocking {
        val repository = freshRepository()
        val room = repository.databaseForTests()
        val program = Program(id = "delete-interleave", name = "base")

        repository.addProgram(program)
        withTimeout(10_000) { while (room.programDao().getById(program.id) == null) delay(10) }

        // Un delete entra ENTRE el upsert durable y su chequeo+publicación
        // (desde otro hilo, como cualquier escritor concurrente real).
        repository.durableCommitInterleaverForTests = {
            withContext(Dispatchers.Default) { repository.deleteProgram(program.id) }
        }
        val result = repository.addProgramNow(program.copy(name = "v1"))
        repository.durableCommitInterleaverForTests = null

        assertTrue(result.exceptionOrNull() is ProgramWriteConflictException)
        // La retirada de caché del delete es indivisible con su reserva: el
        // durable no puede re-animar la fila en memoria tras el borrado.
        assertNull(repository.getProgramById(program.id))
        // Y Room termina limpio: el borrado posterior gana.
        withTimeout(10_000) { while (room.programDao().getById(program.id) != null) delay(10) }
        assertNull(room.programDao().getById(program.id))
    }
}

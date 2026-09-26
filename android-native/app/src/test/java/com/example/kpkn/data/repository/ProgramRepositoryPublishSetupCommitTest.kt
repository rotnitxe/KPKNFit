package com.example.kpkn.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toActiveProgramState
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.models.ActiveProgramState
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Real Room/Robolectric seam para [ProgramRepository.publishSetupCommit].
 *
 * M12 verifica en sus tests que la DB queda correcta; aquí se cubre el error
 * que quedaba en memoria: un replay de un commit viejo (sin programa y con el
 * flag de activación heredado en false) debe re-sincronizar el estado activo
 * desde la fila commiteada en Room, igual que hace
 * `NutritionRepository.publishSetupCommit`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class ProgramRepositoryPublishSetupCommitTest {

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

    private suspend fun freshRepository(): ProgramRepository {
        val repository = ProgramRepository.initForTests(
            ApplicationProvider.getApplicationContext<Context>(),
        )
        withTimeout(10_000) { repository.isReady.first { it } }
        repository.resetAllStateSync()
        return repository
    }

    @Test
    fun replay_of_old_setup_commit_keeps_the_committed_activation_in_memory() = runBlocking {
        val repository = freshRepository()
        val room = repository.databaseForTests()
        val programA = Program(id = "prog-a", name = "Activo A")
        val programB = Program(id = "prog-b", name = "Commit B")

        // 1) activoA: memoria y Room apuntan a A. Se usa el cursor público para
        //    no dejar escrituras de runState en vuelo durante el test.
        repository.addProgram(programA)
        repository.addProgram(programB)
        repository.updateActiveProgramState(
            ActiveProgramState(programId = programA.id, status = ProgramStatus.ACTIVE),
        )
        withTimeout(10_000) {
            while (room.stateDao().getActiveProgram()?.toActiveProgramState()?.programId != programA.id) {
                delay(10)
            }
        }
        assertEquals(programA.id, repository.activeProgramState.value?.programId)

        // 2) Un commit más nuevo deja B activo en Room SIN pasar por esta caché
        //    (así es como M12 escribe su transacción): la memoria queda obsoleta
        //    respecto a un DB que ya es correcta.
        room.stateDao().upsertActiveProgram(
            ActiveProgramState(programId = programB.id, status = ProgramStatus.ACTIVE).toEntity(),
        )
        assertEquals(programA.id, repository.activeProgramState.value?.programId)

        // 3) Replay del commit viejo: sin programa y con flag de activación false.
        repository.publishSetupCommit(
            settings = repository.settings.value,
            program = null,
            activateProgram = false,
        )

        // La memoria refleja el committedDB (B); el replay no la deja en A.
        assertEquals(programB.id, repository.activeProgramState.value?.programId)

        // 4) Variante con programa y con el flag heredado en false: también
        //    re-sincroniza el activo (no solo publica el programa).
        repository.publishSetupCommit(
            settings = repository.settings.value,
            program = programA,
            activateProgram = false,
        )
        assertEquals(programB.id, repository.activeProgramState.value?.programId)

        // 5) La publicación solo refleja Room: no activa nada nuevo ni escribe.
        assertEquals(programB.id, room.stateDao().getActiveProgram()?.toActiveProgramState()?.programId)
        assertEquals(programB.id, repository.activeProgramState.value?.programId)
    }
}

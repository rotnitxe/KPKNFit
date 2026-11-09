package com.example.kpkn.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.db.toSettings
import com.example.kpkn.data.db.toWorkoutLog
import com.example.kpkn.data.db.toWorkoutMedia
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutMediaKind
import com.example.kpkn.testsupport.RoomTestRule
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class ProgramRepositoryProgramRemovalTest {
    @get:Rule val room = RoomTestRule()

    private val mainDispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var databaseName: String
    private lateinit var repository: ProgramRepository

    @Before
    fun setUp() = runBlocking {
        Dispatchers.setMain(mainDispatcher)
        context = ApplicationProvider.getApplicationContext()
        databaseName = "program-removal-${UUID.randomUUID()}.db"
        repository = openRepository()
    }

    @After
    fun tearDown() {
        try {
            ProgramRepository.closeInstance()
            com.example.kpkn.data.db.KpknDatabase.closeInstance()
        } finally {
            context.deleteDatabase(databaseName)
            Dispatchers.resetMain()
        }
    }

    private suspend fun openRepository(): ProgramRepository =
        ProgramRepository.initForTestsWithDatabaseFile(context, databaseName).also { repo ->
            withTimeout(10_000) { repo.isReady.first { it } }
        }

    @Test
    fun archive_keepsProgramAcrossRestart() = runBlocking {
        val id = "archive-${UUID.randomUUID()}"
        repository.addProgram(Program(id = id, name = "Programa archivado"))
        repository.flushPendingWrites()
        repository.startProgram(id)
        repository.addProgramToQueue(id)

        assertEquals(ProgramArchiveResult.Archived, repository.archiveProgram(id))

        val room = repository.databaseForTests()
        withTimeout(10_000) {
            while (
                room.stateDao().getActiveProgram() != null ||
                room.settingsDao().get()?.toSettings()?.archivedProgramIds?.contains(id) != true ||
                room.settingsDao().get()?.toSettings()?.programQueueIds?.contains(id) == true
            ) delay(10)
        }
        assertTrue(repository.programs.value.any { it.id == id })
        assertTrue(repository.settings.value.archivedProgramIds.contains(id))
        assertFalse(repository.programQueue.value.contains(id))

        repository.flushPendingWrites()
        ProgramRepository.closeInstance()
        com.example.kpkn.data.db.KpknDatabase.closeInstance()
        repository = openRepository()

        assertTrue(repository.programs.value.any { it.id == id })
        assertTrue(repository.settings.value.archivedProgramIds.contains(id))
        assertFalse(repository.programQueue.value.contains(id))
        assertNull(repository.activeProgramState.value)
    }

    @Test
    fun delete_keepsHistoryAndMediaAcrossRestart() = runBlocking {
        val id = "remove-${UUID.randomUUID()}"
        val program = Program(id = id, name = "Programa conservado en historial")
        repository.addProgram(program)
        repository.flushPendingWrites()
        repository.startProgram(id)
        repository.addProgramToQueue(id)
        repository.updateSettings { it.copy(archivedProgramIds = it.archivedProgramIds + id) }

        val log = WorkoutLog(
            id = "log-$id",
            programId = id,
            sessionId = "session-$id",
            sessionName = "Sesión registrada",
            date = "2026-11-06T10:00:00.000Z",
            durationMinutes = 45,
        )
        repository.addWorkoutLog(log)
        val media = WorkoutMedia(
            id = "media-$id",
            kind = WorkoutMediaKind.PHOTO,
            filePath = "workout_media/$id.jpg",
            createdAtMs = 1_799_000_000_000L,
            workoutLogId = log.id,
            programId = id,
            sessionId = log.sessionId,
        )
        repository.databaseForTests().workoutMediaDao().upsert(media.toEntity())

        val room = repository.databaseForTests()
        withTimeout(10_000) {
            while (
                room.programDao().getById(id) == null ||
                room.workoutLogDao().getById(log.id) == null ||
                room.stateDao().getActiveProgram() == null ||
                room.settingsDao().get()?.toSettings()?.programQueueIds?.contains(id) != true
            ) delay(10)
        }
        assertTrue(repository.settings.value.archivedProgramIds.contains(id))

        assertEquals(ProgramDeleteResult.Deleted, repository.deleteProgram(id))

        withTimeout(10_000) {
            while (
                room.programDao().getById(id) != null ||
                room.stateDao().getActiveProgram() != null ||
                room.settingsDao().get()?.toSettings()?.programQueueIds?.contains(id) == true ||
                room.settingsDao().get()?.toSettings()?.archivedProgramIds?.contains(id) == true
            ) delay(10)
        }
        repository.flushPendingWrites()
        assertFalse(repository.programs.value.any { it.id == id })
        assertFalse(repository.programQueue.value.contains(id))
        assertEquals(log, room.workoutLogDao().getById(log.id)?.toWorkoutLog())
        assertEquals(log, room.workoutLogDao().getByProgram(id).single().toWorkoutLog())
        assertEquals(media, room.workoutMediaDao().getById(media.id)?.toWorkoutMedia())

        ProgramRepository.closeInstance()
        com.example.kpkn.data.db.KpknDatabase.closeInstance()
        repository = openRepository()

        assertFalse(repository.programs.value.any { it.id == id })
        assertFalse(repository.programQueue.value.contains(id))
        assertFalse(repository.settings.value.archivedProgramIds.contains(id))
        assertNull(repository.activeProgramState.value)
        assertEquals(log, repository.history.value.single { it.id == log.id })
        assertEquals(media, repository.databaseForTests().workoutMediaDao().getById(media.id)?.toWorkoutMedia())
    }
}

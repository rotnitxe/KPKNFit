package com.example.kpkn.screens.workout

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toOngoingWorkoutState
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutMediaKind
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.data.repository.WorkoutMediaRepository
import com.example.kpkn.screens.workout.WorkoutMediaCaptureController
import com.example.kpkn.screens.workout.WorkoutVoiceCommandHandler
import com.example.kpkn.services.workout.WorkoutRestAlertManager
import com.example.kpkn.services.workout.VoiceSessionCommand
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android/Room integration coverage for live-workout durability. The test owns
 * an in-memory Room database and a synthetic one-exercise program; it never
 * reads or mutates the installed QA user's database. Voice coverage dispatches
 * the parsed command directly through WorkoutVoiceCommandHandler and does not
 * start speech recognition or microphone capture.
 */
@RunWith(AndroidJUnit4::class)
class WorkoutLifecycleDurabilityInstrumentedTest {
    private val suffix = System.nanoTime().toString()
    private val programId = "androidtest-lifecycle-$suffix"
    private val sessionId = "androidtest-session-$suffix"
    private val exerciseId = "androidtest-squat-$suffix"
    private val logId = "androidtest-log-$suffix"
    private val triggerNames = linkedSetOf<String>()
    private val mediaIds = linkedSetOf<String>()
    private val temporarySources = mutableListOf<File>()
    private val viewModelStore = ViewModelStore()

    private lateinit var context: Context
    private lateinit var repository: ProgramRepository
    private lateinit var db: KpknDatabase
    private lateinit var mediaRepository: WorkoutMediaRepository
    private lateinit var mediaFilesDir: File
    private var viewModel: WorkoutViewModel? = null

    private val fixtureExercise = Exercise(
        id = exerciseId,
        name = "Sentadilla de prueba",
        exerciseDbId = exerciseId,
        exerciseId = exerciseId,
        canonicalExerciseId = exerciseId,
        sets = listOf(
            ExerciseSet(
                id = "set-0",
                targetReps = 5,
                targetRPE = 8.0,
                weight = 20.0,
                intensityMode = IntensityMode.RPE,
                loadModeV2 = LoadModeV2.LOAD,
                unitModeV2 = UnitModeV2.REPS,
            ),
        ),
    )

    private val fixtureSession = Session(
        id = sessionId,
        name = "Sesión sintética de durabilidad",
        exercises = listOf(fixtureExercise),
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repository = ProgramRepository.initForTests(context)
        db = repository.databaseForTests()
        // initForTests creates Room in-memory without publishing it to the DB singleton.
        // The real ViewModel constructs PerformanceRangeStore(context), which resolves Room
        // through KpknDatabase.getInstance(); point that path at this exact test database.
        setDatabaseSingletonForTest(db)
        assertSame(db, KpknDatabase.getInstance(context))
        mediaFilesDir = File(context.cacheDir, "workout-media-test-$suffix").apply { mkdirs() }
        val mediaContext = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = mediaFilesDir
        }
        mediaRepository = WorkoutMediaRepository.forDatabase(mediaContext, db)
        runBlocking {
            withTimeout(30_000L) { repository.isReady.first { it } }
            assertTrue(repository.addProgramNow(fixtureProgram()).isSuccess)
        }
    }

    @After
    fun tearDown() {
        triggerNames.toList().forEach(::dropTrigger)
        runCatching {
            viewModel?.let { vm ->
                if (!vm.uiState.value.wasCancelled && repository.ongoingWorkout.value != null) {
                    runBlocking {
                        onMainSuspending { vm.cancelWorkout() }
                        withTimeout(10_000L) {
                            vm.uiState.first { it.wasCancelled }
                        }
                    }
                }
            }
        }
        runCatching {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                viewModelStore.clear()
                viewModel = null
            }
        }
        runCatching {
            runBlocking {
                repository.ongoingWorkout.value?.let { repository.clearOngoingWorkoutAndFlush(it) }
                mediaIds.forEach { mediaRepository.delete(it) }
                repository.resetAllStateSync()
            }
        }
        temporarySources.forEach(File::delete)
        if (::mediaFilesDir.isInitialized) mediaFilesDir.deleteRecursively()
        ProgramRepository.closeInstance()
        runCatching { setDatabaseSingletonForTest(null) }
        WorkoutMediaRepository.closeInstance()
    }

    @Test
    fun failed_set_write_keeps_entry_retryable_and_retry_commits_once() = runBlocking {
        val vm = createViewModelAndAwaitActiveSession()
        val draft = WorkoutSetDraft(
            weightText = "20",
            valueText = "5",
            intensityText = "8",
            loadMode = LoadModeV2.LOAD,
            isDirty = true,
        )
        onMain { vm.updateSetDraft(exerciseId = exerciseId, setIdx = 0, draft = draft) }
        withTimeout(10_000L) {
            while (db.stateDao().getOngoingWorkout()?.toOngoingWorkoutState()?.setDrafts?.values?.any {
                    it.weightText == draft.weightText && it.valueText == draft.valueText && it.intensityText == draft.intensityText
                } != true
            ) {
                delay(25L)
            }
        }
        assertDraftMatches(draft, vm.getSetDraft(exerciseId, 0))
        assertTrue(
            "El borrador debe estar durable antes de simular el fallo",
            db.stateDao().getOngoingWorkout()!!.toOngoingWorkoutState()!!.setDrafts.values.any {
                it.weightText == draft.weightText && it.valueText == draft.valueText && it.intensityText == draft.intensityText
            },
        )
        delay(250L) // Let any queued initial hydration snapshot settle before installing the trigger.
        val trigger = installTrigger(
            table = "ongoing_workout",
            event = "UPDATE",
            stem = "fail_record",
        )

        val failed = onMainSuspending {
            vm.recordSetV2(
                weight = 20.0,
                value = 5.0,
                intensity = 8.0,
                loadMode = LoadModeV2.LOAD,
                unitMode = UnitModeV2.REPS,
                expectedExerciseId = exerciseId,
                expectedSetIdx = 0,
            )
        }

        assertTrue("La escritura Room debe fallar dentro del trigger", failed is RecordSetResult.PersistenceFailed)
        assertFalse("La serie fallida no se publica en la UI", vm.uiState.value.completedSets.containsKey("${exerciseId}_0"))
        assertDraftMatches(draft, vm.getSetDraft(exerciseId, 0))
        assertFalse(
            "La serie fallida no contamina el estado durable",
            db.stateDao().getOngoingWorkout()!!.toOngoingWorkoutState()!!.completedSets.containsKey("${exerciseId}_0"),
        )
        assertTrue(
            "La escritura fallida conserva el borrador durable",
            db.stateDao().getOngoingWorkout()!!.toOngoingWorkoutState()!!.setDrafts.values.any {
                it.weightText == draft.weightText && it.valueText == draft.valueText && it.intensityText == draft.intensityText
            },
        )

        dropTrigger(trigger)
        val retried = onMainSuspending {
            vm.recordSetV2(
                weight = 20.0,
                value = 5.0,
                intensity = 8.0,
                loadMode = LoadModeV2.LOAD,
                unitMode = UnitModeV2.REPS,
                expectedExerciseId = exerciseId,
                expectedSetIdx = 0,
            )
        }

        assertTrue("El mismo ingreso debe poder reintentarse", retried.succeeded)
        assertNull("El borrador se limpia únicamente tras confirmar el retry", vm.getSetDraft(exerciseId, 0))
        assertEquals(1, vm.uiState.value.completedSets.keys.count { it == "${exerciseId}_0" })
        val durable = db.stateDao().getOngoingWorkout()!!.toOngoingWorkoutState()!!
        assertEquals(1, durable.completedSets.keys.count { it == "${exerciseId}_0" })
        assertEquals(5, durable.completedSets.getValue("${exerciseId}_0").reps)
        assertFalse(
            "El borrador durable desaparece después del commit exitoso",
            durable.setDrafts.values.any {
                it.weightText == draft.weightText && it.valueText == draft.valueText && it.intensityText == draft.intensityText
            },
        )
    }

    @Test
    fun voice_cancel_delete_failure_keeps_session_and_manual_retry_deletes_it() = runBlocking {
        val vm = createViewModelAndAwaitActiveSession()
        onMain {
            vm.startRestTimer(seconds = 120)
            vm.minimizeRestOverlay()
        }
        val activeRest = withTimeout(10_000L) {
            vm.uiState.first { it.isRestTimerRunning && it.isRestMinimized && it.restModalState != null }
        }
        assertEquals(120, activeRest.restTimerTotal)
        delay(250L)
        val trigger = installTrigger(
            table = "ongoing_workout",
            event = "DELETE",
            stem = "fail_cancel_delete",
        )

        // Dispatch the production parsed command directly; acoustic recognition is intentionally out of scope.
        onMain { voiceHandler(vm).handleVoiceCommand(VoiceSessionCommand.CancelSession) }
        val failedState = withTimeout(10_000L) {
            vm.uiState.first { it.cancellationError != null }
        }

        assertFalse(failedState.wasCancelled)
        assertNotNull("El error de borrado debe ser visible y reintentable", failedState.cancellationError)
        assertFalse("El comando de voz detiene el descanso aunque falle el borrado", failedState.isRestTimerRunning)
        assertFalse(failedState.isRestMinimized)
        assertEquals(0, failedState.restTimerTotal)
        assertNull(failedState.restModalState)
        assertEquals(0, vm.restTimerRemaining.value)
        assertNotNull("Room conserva la sesión hasta confirmar DELETE", db.stateDao().getOngoingWorkout())
        assertNotNull("El cache también conserva la sesión", repository.ongoingWorkout.value)
        assertTrue("Cancelar no debe crear un log parcial", db.workoutLogDao().getAll().isEmpty())

        dropTrigger(trigger)
        onMain { vm.cancelWorkout() }
        val cancelled = withTimeout(10_000L) { vm.uiState.first { it.wasCancelled } }

        assertNull(cancelled.cancellationError)
        assertFalse(cancelled.isRestTimerRunning)
        assertFalse(cancelled.isRestMinimized)
        assertEquals(0, cancelled.restTimerTotal)
        assertNull(cancelled.restModalState)
        assertNull(db.stateDao().getOngoingWorkout())
        assertNull(repository.ongoingWorkout.value)
        assertTrue("Cancelar no debe materializar un registro parcial", db.workoutLogDao().getAll().isEmpty())
    }

    @Test
    fun media_before_failed_finalize_and_after_retry_bind_once_to_same_log() = runBlocking {
        val vm = createViewModelAndAwaitActiveSession()
        delay(250L)
        val ongoing = requireNotNull(repository.ongoingWorkout.value)
        val mediaSessionKey = WorkoutMediaCaptureController.sessionKey(programId, sessionId, ongoing.startTime)
        val beforeId = "androidtest-media-before-$suffix"
        val lateId = "androidtest-media-late-$suffix"
        val beforeSource = createPng("before")
        val lateSource = createPng("late")

        val before = requireNotNull(
            mediaRepository.ingestFile(
                source = beforeSource,
                kind = WorkoutMediaKind.PHOTO,
                seed = mediaSeed(beforeId, mediaSessionKey),
            ),
        )
        mediaIds += beforeId
        assertNull(before.workoutLogId)

        val trigger = installTrigger(
            table = "workout_logs",
            event = "INSERT",
            stem = "fail_finalize_log",
        )
        val log = fixtureLog()
        val failedFinalize = runCatching {
            repository.finalizeWorkout(log, mediaSessionKey = mediaSessionKey)
        }
        assertTrue("La finalización debe propagar el fallo de SQLite", failedFinalize.isFailure)
        assertNull(db.workoutLogDao().getById(logId))
        assertNull(db.workoutMediaSessionAssociationDao().getBySessionKey(mediaSessionKey))
        assertNull(db.workoutMediaDao().getById(beforeId)!!.workoutLogId)
        assertNotNull("El rollback conserva la ejecución activa", db.stateDao().getOngoingWorkout())

        dropTrigger(trigger)
        repository.finalizeWorkout(log, mediaSessionKey = mediaSessionKey)
        assertNull(repository.ongoingWorkout.value)
        assertEquals(logId, db.workoutLogDao().getById(logId)!!.id)
        assertEquals(logId, db.workoutMediaSessionAssociationDao().getBySessionKey(mediaSessionKey)!!.workoutLogId)
        assertEquals(logId, db.workoutMediaDao().getById(beforeId)!!.workoutLogId)

        val late = requireNotNull(
            mediaRepository.ingestFile(
                source = lateSource,
                kind = WorkoutMediaKind.PHOTO,
                seed = mediaSeed(lateId, mediaSessionKey),
            ),
        )
        mediaIds += lateId
        assertEquals("La inserción tardía consulta el vínculo durable", logId, late.workoutLogId)

        // Retrying the same UUID is idempotent and cannot create a second Room row.
        val duplicateRetry = mediaRepository.ingestFile(
            source = lateSource,
            kind = WorkoutMediaKind.PHOTO,
            seed = mediaSeed(lateId, mediaSessionKey),
        )
        assertEquals(lateId, duplicateRetry!!.id)
        assertEquals(2, db.workoutMediaDao().getBySessionKey(mediaSessionKey).size)
        assertEquals(2, db.workoutMediaDao().getByWorkoutLogId(logId).size)
    }

    @Test
    fun callback_failure_after_confirmed_cancel_does_not_restore_recoverable_session() = runBlocking {
        val vm = createViewModelAndAwaitActiveSession()

        onMain {
            vm.abandonWorkoutWithoutSaving {
                throw IllegalStateException("synthetic post-delete callback failure")
            }
        }
        withTimeout(10_000L) { vm.uiState.first { it.wasCancelled } }
        delay(100L) // Let any post-callback error handling publish before checking terminal UI state.
        val terminal = vm.uiState.value

        assertTrue("Room DELETE ya confirmado es terminal aunque falle un callback", terminal.wasCancelled)
        assertNull(terminal.cancellationError)
        assertNull(db.stateDao().getOngoingWorkout())
        assertNull(repository.ongoingWorkout.value)
        assertTrue(db.workoutLogDao().getAll().isEmpty())
    }

    @Test
    fun manual_cancel_clears_active_rest_overlay_before_terminal_ack() = runBlocking {
        val vm = createViewModelAndAwaitActiveSession()
        onMain {
            vm.startRestTimer(seconds = 120)
            vm.minimizeRestOverlay()
        }
        val activeRest = withTimeout(10_000L) {
            vm.uiState.first { it.isRestTimerRunning && it.isRestMinimized && it.restModalState != null }
        }
        assertEquals(120, activeRest.restTimerTotal)
        assertTrue(vm.restTimerRemaining.value > 0)

        onMain { vm.cancelWorkout() }
        val cancelled = withTimeout(10_000L) { vm.uiState.first { it.wasCancelled } }

        assertFalse("La confirmación terminal no debe conservar el descanso", cancelled.isRestTimerRunning)
        assertFalse("La cancelación elimina también el estado minimizado", cancelled.isRestMinimized)
        assertEquals(0, cancelled.restTimerTotal)
        assertNull(cancelled.restModalState)
        assertEquals(0, vm.restTimerRemaining.value)
        assertNull(vm.sessionTimeRemainingSeconds.value)
        assertNull(db.stateDao().getOngoingWorkout())
        assertTrue("Descartar no crea un log parcial", db.workoutLogDao().getAll().isEmpty())

        delay(1_300L)
        assertFalse(vm.uiState.value.isRestTimerRunning)
        assertFalse(vm.uiState.value.isRestMinimized)
        assertNull(vm.uiState.value.restModalState)
        assertEquals(0, vm.uiState.value.restTimerTotal)
        assertEquals(0, vm.restTimerRemaining.value)
    }

    private suspend fun createViewModelAndAwaitActiveSession(): WorkoutViewModel {
        val vm = onMain {
            WorkoutViewModel(
                appContext = context,
                programId = programId,
                sessionId = sessionId,
                restAlertManager = WorkoutRestAlertManager(context),
            ).also {
                viewModel = it
                viewModelStore.put("lifecycle-$suffix", it)
            }
        }
        withTimeout(30_000L) {
            vm.uiState.first { state ->
                state.session?.id == sessionId && !state.isStartingWorkout &&
                    repository.ongoingWorkout.value?.session?.id == sessionId
            }
        }
        assertEquals(sessionId, db.stateDao().getOngoingWorkout()!!.toOngoingWorkoutState()!!.session.id)
        return vm
    }

    private fun voiceHandler(vm: WorkoutViewModel): WorkoutVoiceCommandHandler {
        val field = WorkoutViewModel::class.java.getDeclaredField("voiceCommandHandler").apply {
            isAccessible = true
        }
        return field.get(vm) as WorkoutVoiceCommandHandler
    }

    /** The generated bytecode stores `INSTANCE` as a private static field on KpknDatabase itself. */
    private fun setDatabaseSingletonForTest(database: KpknDatabase?) {
        KpknDatabase::class.java.getDeclaredField("INSTANCE").apply {
            isAccessible = true
        }.set(null, database)
    }

    private fun <T : Any> onMain(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        var result: T? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result = block() }
        return checkNotNull(result)
    }

    private suspend fun <T> onMainSuspending(block: suspend () -> T): T =
        withContext(Dispatchers.Main.immediate) { block() }

    private fun assertDraftMatches(expected: WorkoutSetDraft, actual: WorkoutSetDraft?) {
        assertNotNull("El borrador escrito por el usuario debe permanecer disponible", actual)
        assertEquals(expected.weightText, actual!!.weightText)
        assertEquals(expected.valueText, actual.valueText)
        assertEquals(expected.intensityText, actual.intensityText)
        assertEquals(expected.loadMode, actual.loadMode)
        assertTrue(actual.isDirty)
    }

    private fun fixtureProgram() = Program(
        id = programId,
        name = "Programa sintético de durabilidad",
        macrocycles = listOf(
            Macrocycle(
                id = "macro-$suffix",
                name = "Macro",
                blocks = listOf(
                    Block(
                        id = "block-$suffix",
                        name = "Bloque",
                        mesocycles = listOf(
                            Mesocycle(
                                id = "meso-$suffix",
                                name = "Meso",
                                weeks = listOf(
                                    ProgramWeek(
                                        id = "week-$suffix",
                                        name = "Semana",
                                        sessions = listOf(fixtureSession),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    private fun fixtureLog() = WorkoutLog(
        id = logId,
        programId = programId,
        sessionId = sessionId,
        sessionName = fixtureSession.name,
        date = "2026-09-30T12:00:00.000Z",
        durationMinutes = 1,
        completedExercises = listOf(
            CompletedExercise(
                exerciseId = exerciseId,
                exerciseName = fixtureExercise.name,
                sets = listOf(CompletedSet(id = "${exerciseId}_0", weight = 20.0, reps = 5)),
            ),
        ),
    )

    private fun mediaSeed(id: String, sessionKey: String) = WorkoutMedia(
        id = id,
        kind = WorkoutMediaKind.PHOTO,
        filePath = "",
        createdAtMs = System.currentTimeMillis(),
        sessionKey = sessionKey,
        programId = programId,
        sessionId = sessionId,
        sessionName = fixtureSession.name,
        exerciseId = exerciseId,
        canonicalExerciseId = exerciseId,
        exerciseName = fixtureExercise.name,
        setIndex = 0,
        weightKg = 20.0,
        reps = 5,
    )

    private fun createPng(label: String): File {
        val file = File(context.cacheDir, "androidtest-$suffix-$label.png")
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        try {
            file.outputStream().use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally {
            bitmap.recycle()
        }
        temporarySources += file
        return file
    }

    private fun installTrigger(table: String, event: String, stem: String): String {
        val name = "${stem}_${suffix.replace('-', '_')}"
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER `$name` BEFORE $event ON `$table` " +
                "BEGIN SELECT RAISE(ABORT, 'androidtest forced $stem'); END",
        )
        triggerNames += name
        return name
    }

    private fun dropTrigger(name: String) {
        runCatching { db.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS `$name`") }
        triggerNames -= name
    }
}

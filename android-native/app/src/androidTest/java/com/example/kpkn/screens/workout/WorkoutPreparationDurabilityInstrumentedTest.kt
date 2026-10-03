package com.example.kpkn.screens.workout

import android.content.Context
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toOngoingWorkoutState
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.models.WarmupSetDefinition
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.services.workout.WorkoutRestAlertManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android/Room regression for preparation completion. Room update failures must leave both
 * the progress cursor and its durable preparation values untouched until a retry commits.
 */
@RunWith(AndroidJUnit4::class)
class WorkoutPreparationDurabilityInstrumentedTest {
    private val suffix = System.nanoTime().toString()
    private val programId = "androidtest-preparation-$suffix"
    private val sessionId = "androidtest-preparation-session-$suffix"
    private val exerciseId = "androidtest-preparation-exercise-$suffix"
    private val warmupId = "androidtest-warmup-$suffix"
    private val mobilityId = "androidtest-mobility-$suffix"
    private val triggerNames = linkedSetOf<String>()
    private val viewModelStore = ViewModelStore()

    private lateinit var context: Context
    private lateinit var repository: ProgramRepository
    private lateinit var db: KpknDatabase
    private var viewModel: WorkoutViewModel? = null

    private val fixtureExercise = Exercise(
        id = "androidtest-preparation-exercise-$suffix",
        name = "Sentadilla de preparación",
        exerciseDbId = "androidtest-preparation-exercise-$suffix",
        exerciseId = "androidtest-preparation-exercise-$suffix",
        canonicalExerciseId = "androidtest-preparation-exercise-$suffix",
        sets = listOf(
            ExerciseSet(
                id = "working-set",
                targetReps = 5,
                targetRPE = 8.0,
                weight = 20.0,
                intensityMode = IntensityMode.RPE,
                loadModeV2 = LoadModeV2.LOAD,
                unitModeV2 = UnitModeV2.REPS,
            ),
        ),
        warmupSets = listOf(
            WarmupSetDefinition(
                id = "androidtest-warmup-$suffix",
                percentageOfWorkingWeight = 0.5,
                targetReps = 6,
                restBetween = 0,
            ),
        ),
        mobilitySeries = listOf(
            MobilitySeries(
                id = "androidtest-mobility-$suffix",
                name = "Movilidad de prueba",
                sets = 1,
                durationSeconds = 20,
                restBetweenSeconds = 0,
            ),
        ),
    )

    private val fixtureSession = Session(
        id = sessionId,
        name = "Sesión sintética de preparación",
        exercises = listOf(fixtureExercise),
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repository = ProgramRepository.initForTests(context)
        db = repository.databaseForTests()
        // WorkoutViewModel's PerformanceRangeStore uses the database singleton.
        setDatabaseSingletonForTest(db)
        assertTrue(KpknDatabase.getInstance(context) === db)
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
                        withTimeout(10_000L) { vm.uiState.first { it.wasCancelled } }
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
                repository.resetAllStateSync()
            }
        }
        ProgramRepository.closeInstance()
        runCatching { setDatabaseSingletonForTest(null) }
    }

    @Test
    fun roomFailure_keepsWarmupAndMobilityProgressUntilRetryCommits() = runBlocking {
        val vm = createViewModelAndAwaitActiveSession()
        delay(250L) // Let initial hydration/persistence settle before installing failure triggers.

        val warmupKey = WorkoutStepRules.warmupStepKey(exerciseId, warmupId)
        val beforeWarmupUi = vm.uiState.value
        val beforeWarmupRoom = ongoingFromRoom()
        val warmupTrigger = installUpdateFailureTrigger("fail_warmup")

        val warmupFailed = onMainSuspending {
            vm.reportWarmupStepAndAwait(
                exerciseId = exerciseId,
                warmupSetId = warmupId,
                usedWeightKg = 30.0,
                reportedReps = 7,
            )
        }

        assertTrue("El fallo de SQLite debe propagarse al caller", warmupFailed is WorkoutPersistResult.Failed)
        assertPreparationProgressUnchanged(beforeWarmupUi, vm.uiState.value)
        assertPreparationProgressUnchanged(beforeWarmupRoom, ongoingFromRoom())
        assertFalse(warmupKey in vm.uiState.value.warmupCompletedExerciseIds)
        assertFalse(warmupKey in ongoingFromRoom().warmupCompletedExerciseIds)
        assertFalse(warmupKey in vm.uiState.value.preparationReports)
        assertFalse(warmupKey in ongoingFromRoom().preparationReports)

        dropTrigger(warmupTrigger)
        val warmupRetry = onMainSuspending {
            vm.reportWarmupStepAndAwait(
                exerciseId = exerciseId,
                warmupSetId = warmupId,
                usedWeightKg = 30.0,
                reportedReps = 7,
            )
        }
        assertTrue("El retry debe completar el commit Room", warmupRetry.succeeded)
        val warmupDurable = ongoingFromRoom()
        assertTrue(warmupKey in vm.uiState.value.warmupCompletedExerciseIds)
        assertTrue(warmupKey in warmupDurable.warmupCompletedExerciseIds)
        assertEquals(7, warmupDurable.completedSets.getValue(warmupKey).reps)
        assertEquals(30.0, warmupDurable.completedSets.getValue(warmupKey).weight, 0.0)
        assertEquals(7.0, warmupDurable.preparationReports.getValue(warmupKey).value, 0.0)
        assertEquals(1, warmupDurable.completedSets.keys.count { it == warmupKey })

        val mobilityKey = WorkoutStepRules.mobilityStepKey(exerciseId, mobilityId, 0)
        val beforeMobilityUi = vm.uiState.value
        val beforeMobilityRoom = ongoingFromRoom()
        val mobilityTrigger = installUpdateFailureTrigger("fail_mobility")

        val mobilityFailed = onMainSuspending {
            vm.reportMobilityStepAndAwait(
                exerciseId = exerciseId,
                mobilityId = mobilityId,
                value = 25.0,
                unit = PreparationReportUnit.SECONDS,
            )
        }

        assertTrue("El fallo de SQLite debe propagarse al caller", mobilityFailed is WorkoutPersistResult.Failed)
        assertPreparationProgressUnchanged(beforeMobilityUi, vm.uiState.value)
        assertPreparationProgressUnchanged(beforeMobilityRoom, ongoingFromRoom())
        assertFalse(mobilityKey in vm.uiState.value.mobilityCompletedExerciseIds)
        assertFalse(mobilityKey in ongoingFromRoom().mobilityCompletedExerciseIds)
        assertFalse(mobilityKey in vm.uiState.value.preparationReports)
        assertFalse(mobilityKey in ongoingFromRoom().preparationReports)

        dropTrigger(mobilityTrigger)
        val mobilityRetry = onMainSuspending {
            vm.reportMobilityStepAndAwait(
                exerciseId = exerciseId,
                mobilityId = mobilityId,
                value = 25.0,
                unit = PreparationReportUnit.SECONDS,
            )
        }
        assertTrue("El retry debe completar el commit Room", mobilityRetry.succeeded)
        val mobilityDurable = ongoingFromRoom()
        assertTrue(mobilityKey in vm.uiState.value.mobilityCompletedExerciseIds)
        assertTrue(mobilityKey in mobilityDurable.mobilityCompletedExerciseIds)
        assertEquals(25.0, mobilityDurable.preparationReports.getValue(mobilityKey).value, 0.0)
        assertEquals(beforeMobilityRoom.preparationReports.size + 1, mobilityDurable.preparationReports.size)
        assertEquals(25.0, vm.uiState.value.preparationReports.getValue(mobilityKey).value, 0.0)
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
                viewModelStore.put("preparation-$suffix", it)
            }
        }
        withTimeout(30_000L) {
            vm.uiState.first { state ->
                state.session?.id == sessionId && !state.isStartingWorkout &&
                    repository.ongoingWorkout.value?.session?.id == sessionId
            }
        }
        assertEquals(sessionId, ongoingFromRoom().session.id)
        return vm
    }

    private suspend fun ongoingFromRoom() =
        checkNotNull(db.stateDao().getOngoingWorkout()?.toOngoingWorkoutState())

    private fun assertPreparationProgressUnchanged(before: WorkoutUiState, after: WorkoutUiState) {
        assertEquals("La navegación no avanza tras fallo", before.activeStepKey, after.activeStepKey)
        assertEquals(before.currentExerciseIdx, after.currentExerciseIdx)
        assertEquals(before.currentSetIdx, after.currentSetIdx)
        assertEquals(before.completedSets, after.completedSets)
        assertEquals(before.warmupCompletedExerciseIds, after.warmupCompletedExerciseIds)
        assertEquals(before.mobilityCompletedExerciseIds, after.mobilityCompletedExerciseIds)
        assertEquals(before.mobilityTotalCompletedStepKeys, after.mobilityTotalCompletedStepKeys)
        assertEquals(before.preparationReports, after.preparationReports)
    }

    private fun assertPreparationProgressUnchanged(
        before: com.example.kpkn.data.models.OngoingWorkoutState,
        after: com.example.kpkn.data.models.OngoingWorkoutState,
    ) {
        assertEquals("La sesión durable es la misma", before.startTime, after.startTime)
        assertEquals("La navegación durable no avanza", before.activeStepKey, after.activeStepKey)
        assertEquals(before.activeExerciseIndex, after.activeExerciseIndex)
        assertEquals(before.activeSetIndex, after.activeSetIndex)
        assertEquals(before.completedSets, after.completedSets)
        assertEquals(before.warmupCompletedExerciseIds, after.warmupCompletedExerciseIds)
        assertEquals(before.mobilityCompletedExerciseIds, after.mobilityCompletedExerciseIds)
        assertEquals(before.mobilityTotalCompletedStepKeys, after.mobilityTotalCompletedStepKeys)
        assertEquals(before.preparationReports, after.preparationReports)
    }

    private fun fixtureProgram() = Program(
        id = programId,
        name = "Programa sintético de preparación",
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

    private fun installUpdateFailureTrigger(stem: String): String {
        val name = "${stem}_${suffix.replace('-', '_')}"
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER `$name` BEFORE UPDATE ON `ongoing_workout` " +
                "BEGIN SELECT RAISE(ABORT, 'androidtest forced $stem'); END",
        )
        triggerNames += name
        return name
    }

    private fun dropTrigger(name: String) {
        runCatching { db.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS `$name`") }
        triggerNames -= name
    }

    private fun setDatabaseSingletonForTest(database: KpknDatabase?) {
        KpknDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }
            .set(null, database)
    }

    private fun <T : Any> onMain(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        var result: T? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result = block() }
        return checkNotNull(result)
    }

    private suspend fun <T> onMainSuspending(block: suspend () -> T): T =
        withContext(Dispatchers.Main.immediate) { block() }
}

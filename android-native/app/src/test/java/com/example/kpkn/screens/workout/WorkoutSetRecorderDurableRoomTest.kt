package com.example.kpkn.screens.workout

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toOngoingWorkoutState
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.data.repository.StartWorkoutResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy
import com.example.kpkn.screens.workout.components.GuidedMainCapture
import com.example.kpkn.screens.workout.components.GuidedTechniquePhase
import com.example.kpkn.screens.workout.components.toGuidedMainCaptureOrNull
import com.example.kpkn.screens.workout.components.toGuidedPhaseOrNull

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutSetRecorderDurableRoomTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val owner = CoroutineScope(SupervisorJob() + dispatcher)
    private lateinit var repository: ProgramRepository
    private lateinit var recorder: WorkoutSetRecorder
    private lateinit var persistence: WorkoutPersistenceController
    private lateinit var state: WorkoutUiState
    private var advances = 0
    private var rests = 0
    private var profileWrites = 0
    private var failAncillary = false
    private var failFirstPublication = false
    private val session = Session(id = "durable-session", name = "Regresión", exercises = listOf(
        Exercise(id = "exercise", name = "Regression fixture exercise", sets = listOf(
            ExerciseSet(id = "s1", targetReps = 6), ExerciseSet(id = "s2", targetReps = 6),
        ), restTime = 30),
    ))

    @Before fun setUp() = runBlocking {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        repository = ProgramRepository.initForTests(context)
        withTimeout(10_000) { repository.isReady.first { it } }
        repository.resetAllStateSync()
        assertEquals(StartWorkoutResult.Started, repository.startWorkout(OngoingWorkoutState(
            programId = "", session = session, startTime = 100L,
        )))
        state = WorkoutUiState(session = session, startTimeMs = 100L, featureFlags = WorkoutFeatureFlags(
            workoutV2Homologation = false, workoutV2LoadModes = false, workoutV3UnifiedFlow = false,
        ), setDrafts = mapOf("exercise_0" to WorkoutSetDraft(weightText = "20", valueText = "6", isDirty = true)))
        persistence = WorkoutPersistenceController(owner, "", session.id, { state }, { it.session!!.exercises },
            writeOngoing = repository::updateOngoingWorkoutAndFlush, persistDispatcher = dispatcher,
            publishDispatcher = dispatcher)
        val ports = Proxy.newProxyInstance(WorkoutSetRecorder.Ports::class.java.classLoader,
            arrayOf(WorkoutSetRecorder.Ports::class.java)) { _, method, args ->
            when (method.name) {
                "visibleExercises" -> (args[0] as WorkoutUiState).session!!.exercises
                "activeContextProfile", "inferPlannedIntensity", "computeImbalanceNotice", "nextIncompleteStepAfter" -> null
                "inferUnitMode" -> UnitModeV2.REPS
                "effectiveLoadModeForExercise" -> LoadModeV2.LOAD
                "currentBodyWeight" -> 75.0
                "canonicalExerciseKey" -> "regression-fixture"
                "inferPlannedTarget" -> 6.0
                "workoutStepPositions" -> emptyList<WorkoutStep>()
                "recomputeLiveEnergy" -> SessionEnergySummary()
                "sessionForActiveMode" -> args[0]
                "adjustRestTimeForPace" -> args[0]
                "evaluateSetEntryV3" -> error("La homologación está desactivada en este fixture")
                "persistOngoingStateAndAwait" -> runBlocking {
                    @Suppress("UNCHECKED_CAST")
                    persistence.persistAndAwait(args[0] as WorkoutUiState, args[1] as () -> Unit)
                }
                "nextSet" -> { advances++; state = state.copy(currentSetIdx = state.currentSetIdx + 1); Unit }
                "startRestTimer" -> { rests++; Unit }
                "persistLoadModeToProfile", "registerManualLoadOverride" -> {
                    if (failAncillary) error("injected profile failure")
                    profileWrites++; Unit
                }
                else -> Unit
            }
        } as WorkoutSetRecorder.Ports
        val gate = WorkoutRecordingGate()
        recorder = WorkoutSetRecorder(gate::tryStart, gate::finish, mutableSetOf(), repository, owner,
            { state }, { transform ->
                val next = transform(state)
                if (failFirstPublication && next.completedSets.isNotEmpty()) {
                    failFirstPublication = false
                    error("injected publication failure")
                }
                state = next
            }, ports)
    }

    @After fun tearDown() {
        owner.cancel()
        ProgramRepository.closeInstance()
        KpknDatabase.closeInstance()
        Dispatchers.resetMain()
    }

    private suspend fun record(
        value: Double = 6.0,
        index: Int? = null,
        advanced: SetAdvancedFeedback = SetAdvancedFeedback(),
    ) = recorder.record(
        weight = 20.0, value = value, intensity = 8.0, advanced = advanced, loadMode = LoadModeV2.LOAD,
        unitMode = UnitModeV2.REPS, setIdxOverride = index,
    )
    private suspend fun room() = repository.databaseForTests().stateDao().getOngoingWorkout()!!.toOngoingWorkoutState()!!

    @Test fun uiPublicationFailureDoesNotDisguiseACommittedSetAsPersistenceFailure() = runBlocking {
        failFirstPublication = true
        assertTrue(record() is RecordSetResult.Created)
        assertEquals(1, room().completedSets.size)
        assertEquals(room().completedSets, state.completedSets)
        assertTrue(state.setDrafts.isEmpty())
        assertEquals(1, advances)
    }
    private fun failWrites() {
        repository.databaseForTests().openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_set_write BEFORE INSERT ON ongoing_workout BEGIN SELECT RAISE(ABORT, 'injected room failure'); END",
        )
    }
    private fun failWritesWhenSerializedSetReps(reps: Int) {
        repository.databaseForTests().openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_set_write BEFORE INSERT ON ongoing_workout WHEN NEW.data LIKE '%\"reps\":$reps%' BEGIN SELECT RAISE(ABORT, 'injected completed-set transaction failure'); END",
        )
    }
    private fun allowWrites() {
        repository.databaseForTests().openHelper.writableDatabase.execSQL("DROP TRIGGER fail_set_write")
    }

    @Test fun s1ThenS2PersistsTwoSetsAndReopensAsTwoOfTwo() = runBlocking {
        assertTrue(record() is RecordSetResult.Created)
        assertTrue(record() is RecordSetResult.Created)
        val restored = room()
        assertEquals(2, restored.completedSets.size)
        assertEquals(2, state.completedSets.size)
        assertEquals(2, advances)
        assertEquals(6, restored.completedSets["exercise_1"]!!.reps)
    }

    @Test fun ancillaryFailureAfterCommitKeepsSuccessAndStillAdvances() = runBlocking {
        failAncillary = true
        assertTrue(record() is RecordSetResult.Created)
        assertEquals(1, room().completedSets.size)
        assertEquals(1, state.completedSets.size)
        assertEquals(1, advances)
        assertFalse(state.setDrafts.containsKey("exercise_0"))
    }

    @Test fun failedInsertPreservesDraftAndProgressThenRetryCreatesOneRow() = runBlocking {
        failWrites()
        assertTrue(record() is RecordSetResult.PersistenceFailed)
        assertTrue(state.completedSets.isEmpty())
        assertTrue(room().completedSets.isEmpty())
        assertEquals("20", state.setDrafts["exercise_0"]!!.weightText)
        assertEquals(0, state.currentSetIdx)
        assertEquals(0, advances)
        assertEquals(0, rests)
        assertEquals(0, profileWrites)
        allowWrites()
        assertTrue(record() is RecordSetResult.Created)
        assertEquals(1, room().completedSets.size)
        assertFalse(state.setDrafts.containsKey("exercise_0"))
    }

    @Test fun failedUpdatePreservesOriginalAndRetryKeepsItsStableId() = runBlocking {
        assertTrue(record() is RecordSetResult.Created)
        val old = room().completedSets["exercise_0"]!!
        val techniqueProgress = WorkoutTechniqueProgressDraft(
            kind = WorkoutTechniqueDraftKind.GUIDED_DROP,
            phaseIndex = 0,
            phaseCount = 1,
            mainCapture = WorkoutTechniqueMainCaptureDraft(
                loadMode = LoadModeV2.LOAD,
                unitMode = UnitModeV2.REPS,
                weight = 20.0,
                value = 8.0,
                intensity = 8.0,
                amrapOverride = false,
            ),
            dropRows = emptyList(),
            dropWeightText = "12",
            dropRepsText = "6",
        )
        state = state.copy(currentSetIdx = 0, setDrafts = mapOf("exercise_0" to WorkoutSetDraft(
            weightText = "20", valueText = "8", isDirty = true, techniqueProgress = techniqueProgress)))
        assertTrue(persistence.persistAndAwait(state) {}.succeeded)
        failWritesWhenSerializedSetReps(8)
        val dropRows = listOf(DropSetData(weight = 12.0, reps = 6))
        assertTrue(record(
            value = 8.0,
            index = 0,
            advanced = SetAdvancedFeedback(dropSets = dropRows),
        ) is RecordSetResult.PersistenceFailed)
        assertEquals(old, room().completedSets["exercise_0"])
        assertEquals(old, state.completedSets["exercise_0"])
        assertEquals(0, state.currentSetIdx)
        assertEquals(1, advances)
        assertEquals(1, rests)
        assertEquals("8", state.setDrafts["exercise_0"]!!.valueText)
        assertEquals(techniqueProgress, room().setDrafts["exercise_0"]!!.techniqueProgress)
        allowWrites()
        assertTrue(record(
            value = 8.0,
            index = 0,
            advanced = SetAdvancedFeedback(dropSets = dropRows),
        ) is RecordSetResult.Updated)
        assertEquals(old.id, room().completedSets["exercise_0"]!!.id)
        assertEquals(8, room().completedSets["exercise_0"]!!.reps)
        assertEquals(dropRows, room().completedSets["exercise_0"]!!.dropSets)
        assertEquals(1, room().completedSets.size)
        assertEquals(0, state.currentSetIdx)
        assertEquals(1, advances)
        assertEquals(1, rests)
    }

    @Test fun techniqueProgressIsCommittedBeforeSetAttemptAndSurvivesRoomFailureForRetry() = runBlocking {
        val progress = WorkoutTechniqueProgressDraft(
            kind = WorkoutTechniqueDraftKind.GUIDED_DROP,
            phaseIndex = 1,
            phaseCount = 2,
            mainCapture = WorkoutTechniqueMainCaptureDraft(
                loadMode = LoadModeV2.LOAD,
                unitMode = UnitModeV2.REPS,
                weight = 20.0,
                value = 6.0,
                intensity = 8.0,
                amrapOverride = false,
            ),
            dropRows = listOf(DropSetData(weight = 15.0, reps = 8)),
            dropWeightText = "12",
            dropRepsText = "6",
        )
        state = state.copy(setDrafts = mapOf("exercise_0" to WorkoutSetDraft(
            weightText = "12",
            valueText = "6",
            isDirty = true,
            techniqueProgress = progress,
        )))

        assertTrue(persistence.persistAndAwait(state) {}.succeeded)
        val roomDraftProgress = room().setDrafts["exercise_0"]!!.techniqueProgress!!
        assertEquals(progress, roomDraftProgress)
        assertEquals(
            GuidedTechniquePhase.DropSet(index = 1, total = 2, suggestedWeight = 12.0),
            roomDraftProgress.toGuidedPhaseOrNull(),
        )
        assertEquals(
            GuidedMainCapture(
                loadMode = LoadModeV2.LOAD,
                unitMode = UnitModeV2.REPS,
                weight = 20.0,
                value = 6.0,
                intensity = 8.0,
                amrapOverride = false,
                bodyWeight = null,
                side = null,
            ),
            roomDraftProgress.mainCapture.toGuidedMainCaptureOrNull(),
        )

        val fullDropRows = listOf(DropSetData(weight = 15.0, reps = 8), DropSetData(weight = 12.0, reps = 6))
        failWritesWhenSerializedSetReps(6)
        assertTrue(record(advanced = SetAdvancedFeedback(dropSets = fullDropRows)) is RecordSetResult.PersistenceFailed)
        assertEquals(0, state.currentSetIdx)
        assertTrue(state.completedSets.isEmpty())
        assertEquals(progress, state.setDrafts["exercise_0"]!!.techniqueProgress)
        assertEquals(progress, room().setDrafts["exercise_0"]!!.techniqueProgress)
        assertTrue(room().completedSets.isEmpty())
        assertEquals(0, advances)

        allowWrites()
        assertTrue(record(advanced = SetAdvancedFeedback(dropSets = fullDropRows)) is RecordSetResult.Created)
        val committed = room().completedSets.getValue("exercise_0")
        assertEquals(fullDropRows, committed.dropSets)
        assertTrue(room().setDrafts.isEmpty())
        assertEquals(1, room().completedSets.size)
        assertEquals(1, advances)
    }

    @Test fun legacyDraftJsonWithoutTechniquePayloadStillDecodesAndGuidedPayloadRoundTrips() {
        val legacy = Json.decodeFromString<WorkoutSetDraft>(
            """{"weightText":"20","valueText":"6","isDirty":true,"updatedAtMs":10}""",
        )
        assertNull(legacy.techniqueProgress)

        val progress = WorkoutTechniqueProgressDraft(
            kind = WorkoutTechniqueDraftKind.GUIDED_REST_PAUSE,
            phaseIndex = 1,
            phaseCount = 2,
            mainCapture = WorkoutTechniqueMainCaptureDraft(
                loadMode = LoadModeV2.LOAD,
                unitMode = UnitModeV2.REPS,
                weight = 20.0,
                value = 6.0,
                intensity = 8.0,
                amrapOverride = false,
            ),
            restPauseRows = listOf(RestPauseData(restTime = 20, reps = 8)),
            restPauseRepsText = "5",
            restRemainingSeconds = 0,
        )
        val draft = WorkoutSetDraft(
            weightText = "15",
            valueText = "5",
            isDirty = true,
            // Fijo: con el default System.currentTimeMillis() el campo se omite al codificar si coincide
            // el milisegundo y la decodificación recalcula otro valor (flaky por 1 ms).
            updatedAtMs = 1_790_000_000_000L,
            techniqueProgress = progress,
        )
        val restored = Json.decodeFromString<WorkoutSetDraft>(Json.encodeToString(draft))
        assertEquals(draft, restored)
        assertEquals(
            GuidedTechniquePhase.RestPauseReps(index = 1, total = 2),
            restored.techniqueProgress!!.toGuidedPhaseOrNull(),
        )
        assertEquals(
            GuidedMainCapture(
                loadMode = LoadModeV2.LOAD,
                unitMode = UnitModeV2.REPS,
                weight = 20.0,
                value = 6.0,
                intensity = 8.0,
                amrapOverride = false,
                bodyWeight = null,
                side = null,
            ),
            restored.techniqueProgress!!.mainCapture.toGuidedMainCaptureOrNull(),
        )
    }

    @Test fun techniqueDraftFlushFailureKeepsPayloadAndRetryCommitsOnce() = runBlocking {
        val progress = WorkoutTechniqueProgressDraft(
            kind = WorkoutTechniqueDraftKind.GUIDED_DROP,
            phaseIndex = 1,
            phaseCount = 1,
            mainCapture = WorkoutTechniqueMainCaptureDraft(
                loadMode = LoadModeV2.LOAD,
                unitMode = UnitModeV2.REPS,
                weight = 20.0,
                value = 6.0,
                intensity = 8.0,
                amrapOverride = false,
            ),
            dropRows = listOf(DropSetData(weight = 15.0, reps = 8)),
            dropWeightText = "12",
            dropRepsText = "6",
            awaitingCommit = true,
        )
        state = state.copy(setDrafts = mapOf("exercise_0" to WorkoutSetDraft(
            weightText = "12",
            valueText = "6",
            isDirty = true,
            techniqueProgress = progress,
        )))
        assertTrue(persistence.persistAndAwait(state) {}.succeeded)
        failWrites()
        val fullDropRows = listOf(DropSetData(weight = 15.0, reps = 8), DropSetData(weight = 12.0, reps = 6))
        assertTrue(record(advanced = SetAdvancedFeedback(dropSets = fullDropRows)) is RecordSetResult.PersistenceFailed)
        assertTrue(room().completedSets.isEmpty())
        assertEquals(progress, state.setDrafts["exercise_0"]!!.techniqueProgress)
        assertEquals(progress, room().setDrafts["exercise_0"]!!.techniqueProgress)
        assertEquals(0, state.currentSetIdx)
        assertEquals(0, advances)
        assertEquals(0, rests)

        allowWrites()
        assertTrue(record(advanced = SetAdvancedFeedback(dropSets = fullDropRows)) is RecordSetResult.Created)
        assertEquals(1, room().completedSets.size)
        assertEquals(fullDropRows, room().completedSets.getValue("exercise_0").dropSets)
        assertTrue(room().setDrafts.isEmpty())
        assertEquals(1, advances)
        assertEquals(1, rests)
    }

    @Test fun guidedPendingCommitPayloadRoundTripsThroughRoomWithExplicitEmptyOverrides() = runBlocking {
        val capture = WorkoutTechniqueMainCaptureDraft(
            loadMode = LoadModeV2.LOAD,
            unitMode = UnitModeV2.REPS,
            weight = 20.0,
            value = 8.0,
            intensity = 8.0,
            amrapOverride = false,
        )
        val drop = WorkoutTechniqueProgressDraft(
            kind = WorkoutTechniqueDraftKind.GUIDED_DROP,
            phaseIndex = 1,
            phaseCount = 2,
            mainCapture = capture,
            dropRows = listOf(DropSetData(weight = 15.0, reps = 8), DropSetData(weight = 12.5, reps = 6)),
            restPauseRows = emptyList(),
            awaitingCommit = true,
            commitDropRows = listOf(DropSetData(weight = 15.0, reps = 8), DropSetData(weight = 12.5, reps = 6)),
            commitRestPauseRows = emptyList(),
        )
        state = state.copy(setDrafts = mapOf("exercise_0" to WorkoutSetDraft(
            weightText = "12.5", valueText = "6", isDirty = true, techniqueProgress = drop,
        )))
        assertTrue(persistence.persistAndAwait(state) {}.succeeded)
        assertEquals(drop, room().setDrafts["exercise_0"]!!.techniqueProgress)

        val restPause = WorkoutTechniqueProgressDraft(
            kind = WorkoutTechniqueDraftKind.GUIDED_REST_PAUSE,
            phaseIndex = 1,
            phaseCount = 2,
            mainCapture = capture,
            dropRows = emptyList(),
            restPauseRows = listOf(
                RestPauseData(restTime = 20, reps = 8),
                RestPauseData(restTime = 20, reps = 6),
            ),
            awaitingCommit = true,
            commitDropRows = emptyList(),
            commitRestPauseRows = listOf(
                RestPauseData(restTime = 20, reps = 8),
                RestPauseData(restTime = 20, reps = 6),
            ),
        )
        state = state.copy(setDrafts = mapOf("exercise_0" to WorkoutSetDraft(
            weightText = "20", valueText = "8", isDirty = true, techniqueProgress = restPause,
        )))
        assertTrue(persistence.persistAndAwait(state) {}.succeeded)
        assertEquals(restPause, room().setDrafts["exercise_0"]!!.techniqueProgress)
    }
}

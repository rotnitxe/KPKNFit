package com.example.kpkn.screens.workout

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toOngoingWorkoutState
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.data.repository.StartWorkoutResult
import com.example.kpkn.domain.workout.LoadSuggestionEngine
import com.example.kpkn.services.workout.VoiceSetEditPatch
import com.example.kpkn.services.workout.VoiceUndoPayload
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicReference

/**
 * Voice "deshacer" / "editar la última serie" against a REAL Room database (Robolectric):
 * nothing is published, cleared or aborted before Room acknowledged the snapshot, a Room failure
 * leaves UI / rest / undo token untouched and retryable, and the shared recording gate keeps the
 * corrections from racing set recording, finish and discard.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutVoiceSetMutationRoomTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val owner = CoroutineScope(SupervisorJob() + dispatcher)
    private lateinit var repository: ProgramRepository
    private lateinit var persistence: WorkoutPersistenceController
    private lateinit var controller: WorkoutVoiceSetMutationController
    private lateinit var gate: WorkoutRecordingGate
    private lateinit var state: WorkoutUiState

    private val token = AtomicReference<VoiceUndoPayload?>(null)
    private val events = mutableListOf<String>()
    private val refreshedExercises = mutableListOf<String>()
    private var aborts = 0
    private var reconciles = 0
    private var energyCalls = 0
    private var persistCalls = 0
    private var publicationFailures = 0
    private var roomKeysAtPublish: Set<String>? = null
    private var tokenAtPublish: VoiceUndoPayload? = null
    private var persistEntered: CompletableDeferred<Unit>? = null
    private var persistRelease: CompletableDeferred<Unit>? = null
    private var persistFailure: Throwable? = null
    private var visibleExercisesFailure: Throwable? = null
    private val persistScript = ArrayDeque<WorkoutPersistResult>()

    private val session = Session(
        id = "voice-session",
        name = "Voice corrections",
        exercises = listOf(
            Exercise(
                id = "exercise",
                name = "Bilateral fixture",
                sets = listOf(
                    ExerciseSet(id = "b0", targetReps = 6),
                    ExerciseSet(id = "b1", targetReps = 6),
                    ExerciseSet(id = "b2", targetReps = 6),
                ),
                restTime = 30,
            ),
            Exercise(
                id = "uni",
                name = "Unilateral fixture",
                isUnilateral = true,
                sets = listOf(ExerciseSet(id = "u0", targetReps = 8), ExerciseSet(id = "u1", targetReps = 8)),
                restTime = 30,
            ),
        ),
    )

    @Before
    fun setUp() = runBlocking<Unit> {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        repository = ProgramRepository.initForTests(context)
        withTimeout(10_000) { repository.isReady.first { it } }
        repository.resetAllStateSync()
        assertEquals(
            StartWorkoutResult.Started,
            repository.startWorkout(OngoingWorkoutState(programId = "", session = session, startTime = START_MS)),
        )
        gate = WorkoutRecordingGate()
        state = bilateralState()
        persistence = newPersistence()
        controller = newController()
        assertTrue(persistence.persistAndAwait(state).succeeded)
    }

    @After
    fun tearDown() {
        owner.cancel()
        ProgramRepository.closeInstance()
        KpknDatabase.closeInstance()
        Dispatchers.resetMain()
    }

    // ─── fixtures ────────────────────────────────────────────────────────────────────────────────

    private fun newPersistence() = WorkoutPersistenceController(
        scope = owner,
        programId = "",
        sessionId = session.id,
        getState = { state },
        visibleExercises = { it.session!!.exercises },
        writeOngoing = repository::updateOngoingWorkoutAndFlush,
        persistDispatcher = dispatcher,
        publishDispatcher = dispatcher,
    )

    private fun newController(gateWaitMs: Long = 200L) = WorkoutVoiceSetMutationController(
        programId = "",
        sessionId = session.id,
        recordingGate = gate,
        getState = { state },
        updateState = { transform ->
            if (publicationFailures > 0) {
                publicationFailures -= 1
                error("injected publication failure")
            }
            val previous = state
            val next = transform(previous)
            if (next != previous) {
                events += "publish"
                roomKeysAtPublish = repository.ongoingWorkout.value?.completedSets?.keys.orEmpty().toSet()
                tokenAtPublish = token.get()
                state = next.copy(persistenceRevision = previous.persistenceRevision + 1)
            }
        },
        visibleExercises = { current ->
            visibleExercisesFailure?.let { throw it }
            current.session!!.exercises
        },
        persistAndAwait = { snapshot, onCommitted ->
            persistCalls += 1
            persistFailure?.let { throw it }
            persistEntered?.complete(Unit)
            persistRelease?.await()
            persistScript.removeFirstOrNull() ?: persistence.persistAndAwait(snapshot, onCommitted)
        },
        isCurrentUndo = { payload, nowMs -> token.get()?.takeIf { it.isActive(nowMs) } == payload },
        clearUndoIf = { payload ->
            val current = token.get()
            val cleared = current == payload && token.compareAndSet(current, null)
            if (cleared) events += "clear-token"
            cleared
        },
        abortRestTimer = {
            aborts += 1
            events += "abort-rest"
        },
        reconcileFromRoom = {
            reconciles += 1
            events += "reconcile"
            val ongoing = repository.databaseForTests().stateDao().getOngoingWorkout()!!.toOngoingWorkoutState()!!
            state = state.copy(completedSets = ongoing.completedSets)
        },
        recomputeLiveEnergy = { completedSets, _ ->
            energyCalls += 1
            SessionEnergySummary(projectedTotalKcal = 100 + completedSets.size)
        },
        refreshLoadSuggestions = { _, exerciseId -> refreshedExercises += exerciseId },
        gateWaitMs = gateWaitMs,
    )

    private fun recordedSet(
        id: String,
        exerciseId: String,
        load: Double,
        reps: Int,
        side: String? = null,
    ) = CompletedSet(
        id = id,
        weight = load,
        reps = reps,
        rpe = 8.0,
        side = side,
        actualIntensityMode = IntensityMode.RPE,
        actualIntensityValue = 8.0,
        recordedPayloadV3 = RecordedSetPayload(
            exerciseId = exerciseId,
            side = side,
            loadInputMode = LoadModeV2.LOAD,
            unitMode = UnitModeV2.REPS,
            externalLoad = load,
            bodyWeightSnapshot = 75.0,
            completedReps = reps,
            actualIntensityMode = IntensityMode.RPE,
            actualIntensityValue = 8.0,
        ),
    )

    private fun restModal() = WorkoutRestModalState(
        exerciseId = "exercise",
        exerciseName = "Bilateral fixture",
        plannedSeconds = 30,
        suggestedSeconds = 30,
        activeSeconds = 30,
        endsAtMs = 9_000_000L,
    )

    /** Two of three sets of the bilateral exercise logged, resting before set 3. */
    private fun bilateralState(): WorkoutUiState {
        val first = recordedSet("c0", "exercise", 20.0, 6)
        val second = recordedSet("c1", "exercise", 20.0, 6)
        return WorkoutUiState(
            session = session,
            startTimeMs = START_MS,
            programId = "",
            completedSets = mapOf("exercise_0" to first, "exercise_1" to second),
            currentExerciseIdx = 0,
            currentSetIdx = 2,
            activeStepKey = "exercise_2",
            setJustLoggedKey = "exercise_1",
            isRestTimerRunning = true,
            restTimerTotal = 30,
            restModalState = restModal(),
            pendingRestSuggestion = PendingRestSuggestion(
                plannedSeconds = 30,
                adaptiveSeconds = 45,
                exerciseName = "Bilateral fixture",
                exerciseId = "exercise",
                lastSet = second,
                advancedFeedback = null,
            ),
            featureFlags = WorkoutFeatureFlags(
                workoutV2Homologation = false,
                workoutV2LoadModes = false,
                workoutV3UnifiedFlow = false,
            ),
        )
    }

    /** Both sides of the first unilateral set logged (left, then right). */
    private fun unilateralState(onlyLeft: Boolean = false): WorkoutUiState {
        val left = recordedSet("u-left", "uni", 12.0, 8, side = "left")
        val right = recordedSet("u-right", "uni", 12.0, 8, side = "right")
        val sets = if (onlyLeft) mapOf("uni_0_L" to left) else mapOf("uni_0_L" to left, "uni_0_R" to right)
        return bilateralState().copy(
            completedSets = sets,
            currentExerciseIdx = 1,
            currentSetIdx = 1,
            activeStepKey = "uni_1_L",
            setJustLoggedKey = if (onlyLeft) "uni_0_L" else "uni_0_R",
            planDeviations = listOf(
                PlanDeviation(
                    exerciseId = "uni",
                    exerciseName = "Unilateral fixture",
                    setIdx = 0,
                    type = PlanDeviationType.WEIGHT_LOW,
                    detail = "fixture",
                ),
            ),
        )
    }

    private suspend fun seed(next: WorkoutUiState) {
        state = next
        assertTrue(persistence.persistAndAwait(next).succeeded)
    }

    private fun armUndo(
        setKey: String = "exercise_1",
        exerciseId: String = "exercise",
        setIdx: Int = 1,
        side: String? = null,
        expiresInMs: Long = 60_000L,
    ): VoiceUndoPayload {
        val payload = VoiceUndoPayload(setKey, exerciseId, setIdx, side, System.currentTimeMillis() + expiresInMs)
        token.set(payload)
        return payload
    }

    private suspend fun room() = repository.databaseForTests().stateDao().getOngoingWorkout()!!.toOngoingWorkoutState()!!

    private fun failWrites() {
        val db = repository.databaseForTests().openHelper.writableDatabase
        db.execSQL("CREATE TRIGGER fail_voice_insert BEFORE INSERT ON ongoing_workout BEGIN SELECT RAISE(ABORT, 'injected voice insert failure'); END")
        db.execSQL("CREATE TRIGGER fail_voice_update BEFORE UPDATE ON ongoing_workout BEGIN SELECT RAISE(ABORT, 'injected voice update failure'); END")
    }

    private fun allowWrites() {
        val db = repository.databaseForTests().openHelper.writableDatabase
        db.execSQL("DROP TRIGGER IF EXISTS fail_voice_insert")
        db.execSQL("DROP TRIGGER IF EXISTS fail_voice_update")
    }

    private fun holdPersist() {
        persistEntered = CompletableDeferred()
        persistRelease = CompletableDeferred()
    }

    private suspend fun awaitPersistEntered() = withTimeout(5_000) { persistEntered!!.await() }

    private fun releasePersist() {
        persistRelease!!.complete(Unit)
    }

    private fun weightPatch(kg: Double) = VoiceSetEditPatch(weightKg = kg)

    // ─── undo ────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun undoCommitsRemovalToRoomBeforeClearingTokenAndAbortingRest() = runBlocking<Unit> {
        val undo = armUndo()
        holdPersist()
        val pending = async { controller.undo(undo) }
        awaitPersistEntered()

        // The Room write has not happened yet: nothing may have been published, spoken-for or cleared.
        assertEquals(setOf("exercise_0", "exercise_1"), room().completedSets.keys)
        assertEquals(setOf("exercise_0", "exercise_1"), state.completedSets.keys)
        assertSame(undo, token.get())
        assertEquals(0, aborts)
        assertTrue(state.isRestTimerRunning)
        assertTrue(gate.isBusy())
        assertTrue(events.isEmpty())

        releasePersist()
        assertEquals(WorkoutVoiceMutationResult.Committed("exercise_1", null), pending.await())

        assertEquals(setOf("exercise_0"), room().completedSets.keys)
        assertEquals(room().completedSets, state.completedSets)
        // Room already had the removal when the UI was published, and the token was still armed then.
        assertEquals(setOf("exercise_0"), roomKeysAtPublish)
        assertSame(undo, tokenAtPublish)
        assertEquals(listOf("publish", "clear-token", "abort-rest"), events)
        assertNull(token.get())
        assertEquals(1, aborts)
        assertFalse(gate.isBusy())
        // Derived state is reset like revertExecutionError does.
        assertNull(state.restModalState)
        assertNull(state.pendingRestSuggestion)
        assertFalse(state.isRestTimerRunning)
        assertEquals(0, state.restTimerTotal)
        assertNull(state.setJustLoggedKey)
        assertEquals(1, state.currentSetIdx)
        assertEquals("exercise_1", state.activeStepKey)
        assertNull(room().restModalState)
        assertEquals("exercise_1", room().activeStepKey)
        assertEquals(1, room().activeSetIndex)
        assertEquals(listOf("exercise"), refreshedExercises)
    }

    @Test
    fun undoRoomFailureKeepsSetCursorRestAndToken() = runBlocking<Unit> {
        val undo = armUndo()
        val before = state
        failWrites()

        val result = controller.undo(undo)

        assertTrue(result is WorkoutVoiceMutationResult.PersistenceFailed)
        assertEquals(before.completedSets, state.completedSets)
        assertEquals(before.completedSets, room().completedSets)
        assertEquals(before.restModalState, state.restModalState)
        assertEquals(before.restModalState, room().restModalState)
        assertTrue(state.isRestTimerRunning)
        assertEquals(before.pendingRestSuggestion, state.pendingRestSuggestion)
        assertEquals("exercise_1", state.setJustLoggedKey)
        assertEquals(2, state.currentSetIdx)
        assertEquals("exercise_2", state.activeStepKey)
        assertEquals(0, aborts)
        assertSame(undo, token.get())
        assertTrue(events.isEmpty())
        assertFalse(gate.isBusy())
    }

    @Test
    fun undoRetryAfterFailureRemovesOnceAndSecondUndoIsNoRecentSet() = runBlocking<Unit> {
        val undo = armUndo()
        failWrites()
        assertTrue(controller.undo(undo) is WorkoutVoiceMutationResult.PersistenceFailed)
        allowWrites()

        assertEquals(WorkoutVoiceMutationResult.Committed("exercise_1", null), controller.undo(undo))
        assertEquals(setOf("exercise_0"), room().completedSets.keys)

        val afterFirstCommit = room().completedSets
        assertEquals(WorkoutVoiceMutationResult.NoRecentSet, controller.undo(undo))
        assertEquals(afterFirstCommit, room().completedSets)
        assertEquals(1, aborts)
        assertNull(token.get())
    }

    @Test
    fun undoWithNewerTokenArmedDuringTheCommitDoesNotClearIt() = runBlocking<Unit> {
        val older = armUndo()
        holdPersist()
        val pending = async { controller.undo(older) }
        awaitPersistEntered()
        // A registration finishing meanwhile arms a newer token.
        val newer = armUndo(setKey = "exercise_2", setIdx = 2)
        releasePersist()

        assertEquals(WorkoutVoiceMutationResult.Committed("exercise_1", null), pending.await())
        assertSame(newer, token.get())
        assertFalse("clear-token" in events)
    }

    @Test
    fun undoWithReplacedTokenReturnsNoRecentSetAndDoesNotTouchRoom() = runBlocking<Unit> {
        val stale = armUndo()
        val newer = armUndo(setKey = "exercise_2", setIdx = 2)

        assertEquals(WorkoutVoiceMutationResult.NoRecentSet, controller.undo(stale))

        assertEquals(setOf("exercise_0", "exercise_1"), room().completedSets.keys)
        assertEquals(0, persistCalls)
        assertEquals(0, aborts)
        assertSame(newer, token.get())
    }

    @Test
    fun undoExpiredTokenReturnsNoRecentSetWithoutTouchingRoom() = runBlocking<Unit> {
        val expired = armUndo(expiresInMs = -1_000L)

        assertEquals(WorkoutVoiceMutationResult.NoRecentSet, controller.undo(expired))

        assertEquals(setOf("exercise_0", "exercise_1"), room().completedSets.keys)
        assertEquals(0, persistCalls)
        assertEquals(0, aborts)
    }

    @Test
    fun undoOfASetAlreadyRemovedIsNoRecentSetAndNeverWritesRoom() = runBlocking<Unit> {
        val undo = armUndo(setKey = "exercise_2", setIdx = 2)

        assertEquals(WorkoutVoiceMutationResult.NoRecentSet, controller.undo(undo))

        assertEquals(0, persistCalls)
        assertSame(undo, token.get())
    }

    @Test
    fun undoUnilateralRemovesOnlyTheRecordedSideAndKeepsSharedDeviations() = runBlocking<Unit> {
        seed(unilateralState())
        val undo = armUndo(setKey = "uni_0_R", exerciseId = "uni", setIdx = 0, side = "right")

        assertEquals(WorkoutVoiceMutationResult.Committed("uni_0_R", null), controller.undo(undo))

        assertEquals(setOf("uni_0_L"), room().completedSets.keys)
        assertEquals(room().completedSets, state.completedSets)
        assertEquals(1, state.currentExerciseIdx)
        assertEquals(0, state.currentSetIdx)
        assertEquals("uni_0_R", state.activeStepKey)
        assertEquals("uni_0_R", room().activeStepKey)
        // The left side still counts for this set: its plan deviation must survive.
        assertEquals(1, room().planDeviations.size)
        assertEquals(1, state.planDeviations.size)
    }

    @Test
    fun undoLastSideOfASetDropsItsPlanDeviations() = runBlocking<Unit> {
        seed(unilateralState(onlyLeft = true))
        val undo = armUndo(setKey = "uni_0_L", exerciseId = "uni", setIdx = 0, side = "left")

        assertEquals(WorkoutVoiceMutationResult.Committed("uni_0_L", null), controller.undo(undo))

        assertTrue(room().completedSets.isEmpty())
        assertTrue(room().planDeviations.isEmpty())
        assertTrue(state.planDeviations.isEmpty())
    }

    @Test
    fun undoLastSetOfTheSessionClosesFinishAndPostExerciseSheets() = runBlocking<Unit> {
        seed(
            state.copy(
                showFinishSheet = true,
                showPostExerciseSheet = true,
                postExerciseTargetIdx = 0,
                pendingPostExerciseIdx = -2,
            ),
        )
        assertTrue(room().showFinishSheet)
        val undo = armUndo()

        assertEquals(WorkoutVoiceMutationResult.Committed("exercise_1", null), controller.undo(undo))

        assertFalse(state.showFinishSheet)
        assertFalse(state.showPostExerciseSheet)
        assertEquals(-1, state.postExerciseTargetIdx)
        assertEquals(-1, state.pendingPostExerciseIdx)
        assertFalse(room().showFinishSheet)
    }

    @Test
    fun undoResetsPendingFeedbackStateInsteadOfOpeningTheFeedbackSheet() = runBlocking<Unit> {
        // The old path called restOrchestrator.stop(), which OPENS the post-exercise sheet while a
        // feedback handoff is pending. The correction aborts the timer hard and resets the handoff.
        seed(state.copy(pendingPostExerciseIdx = 1, postExerciseTargetIdx = 0))
        val undo = armUndo()

        assertEquals(WorkoutVoiceMutationResult.Committed("exercise_1", null), controller.undo(undo))

        assertEquals(1, aborts)
        assertFalse(state.showPostExerciseSheet)
        assertNull(state.postExerciseFeedbackTarget)
        assertEquals(-1, state.pendingPostExerciseIdx)
    }

    @Test
    fun undoRecomputesLiveEnergyOnceAndPublishesTheSameValueRoomWasBuiltWith() = runBlocking<Unit> {
        val undo = armUndo()

        assertTrue(controller.undo(undo).isCommitted)

        assertEquals(1, energyCalls)
        assertEquals(101, state.liveEnergySummary.projectedTotalKcal)
    }

    // ─── edit ────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun patchAbsoluteWeightCommitsRoomThenPublishes() = runBlocking<Unit> {
        holdPersist()
        val pending = async { controller.patchLast(weightPatch(82.0)) }
        awaitPersistEntered()

        assertEquals(20.0, state.completedSets.getValue("exercise_1").weight, 0.0)
        assertEquals(20.0, room().completedSets.getValue("exercise_1").weight, 0.0)
        assertTrue(events.isEmpty())

        releasePersist()
        val result = pending.await()

        assertTrue(result is WorkoutVoiceMutationResult.Committed)
        val committed = result as WorkoutVoiceMutationResult.Committed
        assertEquals("exercise_1", committed.setKey)
        assertEquals(82.0, committed.set!!.weight, 0.0)
        assertEquals(82.0, room().completedSets.getValue("exercise_1").weight, 0.0)
        assertEquals(room().completedSets, state.completedSets)
        assertEquals(20.0, state.completedSets.getValue("exercise_0").weight, 0.0)
        assertEquals(listOf("publish"), events)
        assertEquals(0, aborts)
        assertEquals(listOf("exercise"), refreshedExercises)
        // Editing never touches the rest timer or the undo token.
        assertTrue(state.isRestTimerRunning)
    }

    @Test
    fun patchDeltaWeightClampsAtZero() = runBlocking<Unit> {
        val result = controller.patchLast(VoiceSetEditPatch(weightDeltaKg = -50.0))

        assertTrue(result.isCommitted)
        assertEquals(0.0, room().completedSets.getValue("exercise_1").weight, 0.0)
        assertNull(room().completedSets.getValue("exercise_1").recordedPayloadV3!!.externalLoad)
    }

    @Test
    fun patchRepsAndRpeThenRirKeepsTheTwoIntensityScalesExclusive() = runBlocking<Unit> {
        assertTrue(
            controller.patchLast(
                VoiceSetEditPatch(
                    metricValue = 9,
                    intensityValue = 9.0,
                    intensityKind = WorkoutVoiceIntensityKind.RPE,
                ),
            ).isCommitted,
        )
        val afterRpe = room().completedSets.getValue("exercise_1")
        assertEquals(9, afterRpe.reps)
        assertEquals(9.0, afterRpe.rpe!!, 0.0)
        assertNull(afterRpe.rir)
        assertEquals(9, afterRpe.recordedPayloadV3!!.completedReps)
        assertEquals(IntensityMode.RPE, afterRpe.recordedPayloadV3!!.actualIntensityMode)

        assertTrue(
            controller.patchLast(
                VoiceSetEditPatch(intensityValue = 2.0, intensityKind = WorkoutVoiceIntensityKind.RIR),
            ).isCommitted,
        )
        val afterRir = room().completedSets.getValue("exercise_1")
        assertEquals(2, afterRir.rir)
        assertNull(afterRir.rpe)
        assertEquals(IntensityMode.RIR, afterRir.recordedPayloadV3!!.actualIntensityMode)
        assertEquals(2.0, afterRir.recordedPayloadV3!!.actualIntensityValue!!, 0.0)
        assertEquals(9, afterRir.reps)
        assertEquals(room().completedSets, state.completedSets)
    }

    @Test
    fun patchRoomFailureKeepsUiAndRoomAtOriginalValues() = runBlocking<Unit> {
        val before = state.completedSets
        failWrites()

        val result = controller.patchLast(weightPatch(82.0))

        assertTrue(result is WorkoutVoiceMutationResult.PersistenceFailed)
        assertEquals(before, state.completedSets)
        assertEquals(before, room().completedSets)
        assertTrue(events.isEmpty())
        assertTrue(refreshedExercises.isEmpty())
        assertFalse(gate.isBusy())
    }

    @Test
    fun patchRetryAfterFailureAppliesDeltaOnlyOnceAndKeepsTheSetId() = runBlocking<Unit> {
        val delta = VoiceSetEditPatch(weightDeltaKg = 2.5)
        failWrites()
        assertTrue(controller.patchLast(delta) is WorkoutVoiceMutationResult.PersistenceFailed)
        allowWrites()

        assertTrue(controller.patchLast(delta).isCommitted)

        val stored = room().completedSets.getValue("exercise_1")
        assertEquals(22.5, stored.weight, 0.0)
        assertEquals(22.5, stored.recordedPayloadV3!!.externalLoad!!, 0.0)
        assertEquals("c1", stored.id)
        assertEquals(2, room().completedSets.size)
        assertEquals(room().completedSets, state.completedSets)
    }

    @Test
    fun patchSynchronizesRecordedPayloadSoProgressionReadsTheNewLoad() = runBlocking<Unit> {
        val result = controller.patchLast(
            VoiceSetEditPatch(
                weightKg = 82.0,
                metricValue = 9,
                intensityValue = 8.5,
                intensityKind = WorkoutVoiceIntensityKind.RPE,
            ),
        )

        assertTrue(result.isCommitted)
        val stored = room().completedSets.getValue("exercise_1")
        assertEquals(82.0, LoadSuggestionEngine.inputLoad(stored, LoadModeV2.LOAD), 0.0)
        assertEquals(82.0, stored.weight, 0.0)
        assertEquals(9, stored.recordedPayloadV3!!.completedReps)
        assertEquals(8.5, stored.recordedPayloadV3!!.actualIntensityValue!!, 0.0)
        assertEquals(8.5, stored.rpe!!, 0.0)
    }

    @Test
    fun patchPercentRmOnlyReturnsNoChangeAndNeverWritesRoom() = runBlocking<Unit> {
        failWrites() // a write attempt would surface as PersistenceFailed instead of NoChange

        val result = controller.patchLast(
            VoiceSetEditPatch(intensityValue = 80.0, intensityKind = WorkoutVoiceIntensityKind.PERCENT_RM),
        )

        assertEquals(WorkoutVoiceMutationResult.NoChange, result)
        assertEquals(0, persistCalls)
        assertFalse(gate.isBusy())
    }

    @Test
    fun patchToTheSameValuesReturnsNoChange() = runBlocking<Unit> {
        assertEquals(WorkoutVoiceMutationResult.NoChange, controller.patchLast(weightPatch(20.0)))
        assertEquals(0, persistCalls)
    }

    @Test
    fun patchSideWithoutRecordedCounterpartReturnsNoRecentSet() = runBlocking<Unit> {
        seed(unilateralState(onlyLeft = true))
        val before = room().completedSets

        val result = controller.patchLast(VoiceSetEditPatch(weightKg = 30.0, side = "right"))

        assertEquals(WorkoutVoiceMutationResult.NoRecentSet, result)
        assertEquals(before, room().completedSets)
        assertEquals(0, persistCalls)
    }

    @Test
    fun patchSideRetargetsToTheRequestedCounterpartSet() = runBlocking<Unit> {
        seed(unilateralState())

        val result = controller.patchLast(VoiceSetEditPatch(weightKg = 40.0, side = "left"))

        assertEquals("uni_0_L", (result as WorkoutVoiceMutationResult.Committed).setKey)
        assertEquals(40.0, room().completedSets.getValue("uni_0_L").weight, 0.0)
        assertEquals(12.0, room().completedSets.getValue("uni_0_R").weight, 0.0)
    }

    @Test
    fun patchIgnoresADanglingJustLoggedKeyAndFallsBackToTheLastSet() = runBlocking<Unit> {
        seed(state.copy(setJustLoggedKey = "exercise_9"))

        val result = controller.patchLast(weightPatch(55.0))

        assertEquals("exercise_1", (result as WorkoutVoiceMutationResult.Committed).setKey)
        assertEquals(55.0, room().completedSets.getValue("exercise_1").weight, 0.0)
        assertEquals(20.0, room().completedSets.getValue("exercise_0").weight, 0.0)
    }

    @Test
    fun patchWithNoCompletedSetsReturnsNoRecentSet() = runBlocking<Unit> {
        seed(state.copy(completedSets = emptyMap(), setJustLoggedKey = null))

        assertEquals(WorkoutVoiceMutationResult.NoRecentSet, controller.patchLast(weightPatch(55.0)))
        assertEquals(0, persistCalls)
    }

    @Test
    fun patchedValueSurvivesReopeningFromRoom() = runBlocking<Unit> {
        assertTrue(controller.patchLast(VoiceSetEditPatch(weightKg = 82.0, metricValue = 9)).isCommitted)

        val reopened = room().completedSets
        assertEquals(state.completedSets, reopened)
        assertEquals(82.0, reopened.getValue("exercise_1").weight, 0.0)
        assertEquals(9, reopened.getValue("exercise_1").reps)
    }

    // ─── concurrency / lifecycle ─────────────────────────────────────────────────────────────────

    @Test
    fun mutationWaitsForInFlightRecordThenEditsTheNewSet() = runBlocking<Unit> {
        controller = newController(gateWaitMs = 5_000L)
        assertTrue(gate.tryStart("exercise_2")) // set recording in flight
        val pending = async { controller.patchLast(weightPatch(50.0)) }
        delay(100)
        assertFalse(pending.isCompleted)

        // The recorder commits set 3 and releases the gate.
        val third = recordedSet("c2", "exercise", 20.0, 6)
        seed(state.copy(completedSets = state.completedSets + ("exercise_2" to third), setJustLoggedKey = "exercise_2"))
        gate.finish("exercise_2")

        val result = withTimeout(5_000) { pending.await() }
        assertEquals("exercise_2", (result as WorkoutVoiceMutationResult.Committed).setKey)
        assertEquals(50.0, room().completedSets.getValue("exercise_2").weight, 0.0)
        assertEquals(20.0, room().completedSets.getValue("exercise_1").weight, 0.0)
        assertFalse(gate.isBusy())
    }

    @Test
    fun mutationReturnsBusyWhenTheGateStaysHeldAndNeverTouchesRoom() = runBlocking<Unit> {
        controller = newController(gateWaitMs = 50L)
        assertTrue(gate.tryStart("exercise_2"))

        val result = controller.patchLast(weightPatch(50.0))

        assertEquals(WorkoutVoiceMutationResult.Busy, result)
        assertEquals(0, persistCalls)
        assertEquals(20.0, room().completedSets.getValue("exercise_1").weight, 0.0)
        // The gate still belongs to the recorder: the waiter must not have released or stolen it.
        assertTrue(gate.isBusy())
        gate.finish("exercise_2")
        assertTrue(controller.patchLast(weightPatch(50.0)).isCommitted)
    }

    @Test
    fun secondMutationWhileFirstIsCommittingIsBusyAndNeverDuplicates() = runBlocking<Unit> {
        controller = newController(gateWaitMs = 50L)
        val delta = VoiceSetEditPatch(weightDeltaKg = 2.5)
        holdPersist()
        val first = async { controller.patchLast(delta) }
        awaitPersistEntered()

        assertEquals(WorkoutVoiceMutationResult.Busy, controller.patchLast(delta))
        releasePersist()
        assertTrue(first.await().isCommitted)

        assertEquals(22.5, room().completedSets.getValue("exercise_1").weight, 0.0)
        assertEquals(1, persistCalls)
    }

    @Test
    fun finishInProgressRejectsMutationsWithClosingAndKeepsTheToken() = runBlocking<Unit> {
        val undo = armUndo()
        state = state.copy(isFinishingWorkout = true)

        assertEquals(WorkoutVoiceMutationResult.Closing, controller.undo(undo))
        assertEquals(WorkoutVoiceMutationResult.Closing, controller.patchLast(weightPatch(50.0)))

        assertSame(undo, token.get())
        assertEquals(0, persistCalls)
        assertEquals(0, aborts)
        assertEquals(20.0, room().completedSets.getValue("exercise_1").weight, 0.0)
    }

    @Test
    fun cancelInProgressRejectsMutationsWithClosing() = runBlocking<Unit> {
        val undo = armUndo()

        state = state.copy(isCancellingWorkout = true)
        assertEquals(WorkoutVoiceMutationResult.Closing, controller.undo(undo))
        assertEquals(WorkoutVoiceMutationResult.Closing, controller.patchLast(weightPatch(50.0)))

        state = state.copy(isCancellingWorkout = false, wasCancelled = true)
        assertEquals(WorkoutVoiceMutationResult.Closing, controller.undo(undo))
        assertEquals(WorkoutVoiceMutationResult.Closing, controller.patchLast(weightPatch(50.0)))

        assertEquals(0, persistCalls)
        assertSame(undo, token.get())
    }

    @Test
    fun mutationHoldsTheGateSoFinishAndDiscardAwaitIdle() = runBlocking<Unit> {
        holdPersist()
        val pending = async { controller.patchLast(weightPatch(50.0)) }
        awaitPersistEntered()

        assertTrue(gate.isBusy())
        assertFalse(gate.awaitIdle(50L))

        releasePersist()
        assertTrue(pending.await().isCommitted)
        assertFalse(gate.isBusy())
        assertTrue(gate.awaitIdle(50L))
    }

    @Test
    fun discardBeforeCommitReturnsSessionChangedAndLeavesUiAndTokenUntouched() = runBlocking<Unit> {
        val undo = armUndo()
        val before = state
        repository.clearOngoingWorkoutAndFlush()

        val result = controller.undo(undo)

        assertEquals(WorkoutVoiceMutationResult.SessionChanged, result)
        assertEquals(before, state)
        assertSame(undo, token.get())
        assertEquals(0, aborts)
        assertTrue(events.isEmpty())
        assertFalse(gate.isBusy())
    }

    @Test
    fun staleExecutionReturnsSessionChanged() = runBlocking<Unit> {
        // A fresh persistence controller has never written, so the stale-execution guard inside the
        // Room update (not the controller's own start-time check) is what rejects the snapshot.
        persistence = newPersistence()
        controller = newController()
        state = state.copy(startTimeMs = 999L)
        val before = state

        val result = controller.patchLast(weightPatch(50.0))

        assertEquals(WorkoutVoiceMutationResult.SessionChanged, result)
        assertEquals(before, state)
        assertEquals(20.0, room().completedSets.getValue("exercise_1").weight, 0.0)
    }

    @Test
    fun uiPublicationFailureStillReturnsCommittedRepublishesAndDoesNotBlockLaterPersist() = runBlocking<Unit> {
        publicationFailures = 1

        val result = controller.patchLast(weightPatch(50.0))

        assertTrue(result.isCommitted)
        assertEquals(50.0, room().completedSets.getValue("exercise_1").weight, 0.0)
        assertEquals(room().completedSets, state.completedSets)
        assertEquals(0, reconciles)
        // Without the republication pendingUiCommit would make every later snapshot "Skipped".
        assertTrue(persistence.persistAndAwait(state).succeeded)
    }

    @Test
    fun uiPublicationFailingTwiceTriggersReconcileFromRoom() = runBlocking<Unit> {
        publicationFailures = 2

        val result = controller.patchLast(weightPatch(50.0))

        assertTrue(result.isCommitted)
        assertEquals(1, reconciles)
        assertEquals(room().completedSets, state.completedSets)
        assertEquals(50.0, state.completedSets.getValue("exercise_1").weight, 0.0)
        assertFalse(gate.isBusy())
    }

    @Test
    fun undoWhosePublicationFailsTwiceStillClearsTheTokenAndAbortsRest() = runBlocking<Unit> {
        val undo = armUndo()
        publicationFailures = 2

        assertTrue(controller.undo(undo).isCommitted)

        assertEquals(1, reconciles)
        assertNull(token.get())
        assertEquals(1, aborts)
        assertEquals(setOf("exercise_0"), room().completedSets.keys)
    }

    @Test
    fun cancellationWhileWaitingForTheGateDoesNotLeakOrStealIt() = runBlocking<Unit> {
        controller = newController(gateWaitMs = 10_000L)
        assertTrue(gate.tryStart("exercise_2"))
        val waiting = async { controller.patchLast(weightPatch(50.0)) }
        delay(50)
        assertFalse(waiting.isCompleted)

        waiting.cancel()
        waiting.join()

        assertTrue(gate.isBusy()) // still the recorder's
        gate.finish("exercise_2")
        assertFalse(gate.isBusy())
        assertEquals(0, persistCalls)
        assertTrue(controller.patchLast(weightPatch(50.0)).isCommitted)
    }

    @Test
    fun unexpectedExceptionWhilePersistingReturnsPersistenceFailedAndReleasesTheGate() = runBlocking<Unit> {
        val undo = armUndo()
        persistFailure = IllegalStateException("boom")

        val result = controller.undo(undo)

        assertTrue(result is WorkoutVoiceMutationResult.PersistenceFailed)
        assertEquals("boom", (result as WorkoutVoiceMutationResult.PersistenceFailed).cause.message)
        assertFalse(gate.isBusy())
        assertSame(undo, token.get())
        assertEquals(0, aborts)
    }

    @Test
    fun unexpectedExceptionWhilePreparingReturnsPersistenceFailedAndReleasesTheGate() = runBlocking<Unit> {
        val undo = armUndo()
        visibleExercisesFailure = IllegalStateException("prepare failed")

        val result = controller.undo(undo)

        assertTrue(result is WorkoutVoiceMutationResult.PersistenceFailed)
        assertFalse(gate.isBusy())
        assertSame(undo, token.get())
        assertEquals(0, persistCalls)
    }

    @Test
    fun skippedSnapshotIsRetriedOnceWithAFreshCapture() = runBlocking<Unit> {
        persistScript.addLast(WorkoutPersistResult.Skipped)

        val result = controller.patchLast(weightPatch(50.0))

        assertTrue(result.isCommitted)
        assertEquals(2, persistCalls)
        assertEquals(50.0, room().completedSets.getValue("exercise_1").weight, 0.0)
    }

    @Test
    fun skippedTwiceReturnsStaleAndKeepsUiTokenAndRoom() = runBlocking<Unit> {
        val undo = armUndo()
        persistScript.addLast(WorkoutPersistResult.Skipped)
        persistScript.addLast(WorkoutPersistResult.Skipped)
        val before = state

        val result = controller.undo(undo)

        assertEquals(WorkoutVoiceMutationResult.Stale, result)
        assertEquals(2, persistCalls)
        assertEquals(before, state)
        assertEquals(before.completedSets, room().completedSets)
        assertSame(undo, token.get())
        assertEquals(0, aborts)
    }

    @Test
    fun cancelledPersistResultIsReportedAsSessionChanged() = runBlocking<Unit> {
        persistScript.addLast(WorkoutPersistResult.Cancelled)

        assertEquals(WorkoutVoiceMutationResult.SessionChanged, controller.patchLast(weightPatch(50.0)))
        assertEquals(20.0, state.completedSets.getValue("exercise_1").weight, 0.0)
    }

    private companion object {
        const val START_MS = 100L
    }
}

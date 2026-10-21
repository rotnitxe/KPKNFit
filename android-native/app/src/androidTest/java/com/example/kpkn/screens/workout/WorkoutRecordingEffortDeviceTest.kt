package com.example.kpkn.screens.workout

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toOngoingWorkoutState
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.data.repository.StartWorkoutResult
import java.lang.reflect.Proxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Recorder y Room reales en el dispositivo: un rechazo no puede persistir ni avanzar ni limpiar el borrador. */
@RunWith(AndroidJUnit4::class)
class WorkoutRecordingEffortDeviceTest {
    private val owner = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var repository: ProgramRepository
    private lateinit var recorder: WorkoutSetRecorder
    private lateinit var state: WorkoutUiState
    private var persists = 0
    private var advances = 0
    private val rejections = mutableListOf<String>()
    private val session = Session(id = "effort-device", name = "Contrato", exercises = listOf(
        Exercise(id = "exercise", name = "Press", sets = listOf(ExerciseSet(id = "s1", targetReps = 6, targetRIR = 2))),
    ))

    @Before fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        repository = ProgramRepository.initForTests(context)
        withTimeout(10_000) { repository.isReady.first { it } }
        assertEquals(StartWorkoutResult.Started, repository.startWorkout(OngoingWorkoutState(programId = "", session = session, startTime = 100L)))
        state = WorkoutUiState(session = session, startTimeMs = 100L,
            featureFlags = WorkoutFeatureFlags(workoutV2Homologation = false, workoutV2LoadModes = false, workoutV3UnifiedFlow = false),
            setDrafts = mapOf("exercise_0" to WorkoutSetDraft(weightText = "20", valueText = "6", isDirty = true,
                techniqueProgress = WorkoutTechniqueProgressDraft())),
        )
        val persistence = WorkoutPersistenceController(owner, "", session.id, { state }, { it.session!!.exercises },
            writeOngoing = repository::updateOngoingWorkoutAndFlush, persistDispatcher = Dispatchers.IO, publishDispatcher = Dispatchers.Main.immediate)
        val ports = Proxy.newProxyInstance(WorkoutSetRecorder.Ports::class.java.classLoader, arrayOf(WorkoutSetRecorder.Ports::class.java)) { _, method, args ->
            when (method.name) {
                "visibleExercises" -> (args[0] as WorkoutUiState).session!!.exercises
                "activeContextProfile", "inferPlannedIntensity", "computeImbalanceNotice", "nextIncompleteStepAfter" -> null
                "inferUnitMode" -> UnitModeV2.REPS
                "effectiveLoadModeForExercise" -> LoadModeV2.LOAD
                "currentBodyWeight" -> 75.0
                "canonicalExerciseKey" -> "press"
                "inferPlannedTarget" -> 6.0
                "workoutStepPositions" -> emptyList<WorkoutStep>()
                "recomputeLiveEnergy" -> SessionEnergySummary()
                "sessionForActiveMode", "adjustRestTimeForPace" -> args[0]
                "evaluateSetEntryV3" -> error("La homologación está desactivada")
                "persistOngoingStateAndAwait" -> runBlocking {
                    persists += 1
                    @Suppress("UNCHECKED_CAST")
                    persistence.persistAndAwait(args[0] as WorkoutUiState, args[1] as () -> Unit)
                }
                "nextSet" -> { advances += 1; Unit }
                "clearDraftForSet" -> { state = state.copy(setDrafts = emptyMap()); Unit }
                "onRecordingRejected" -> { rejections += args[0] as String; Unit }
                else -> Unit
            }
        } as WorkoutSetRecorder.Ports
        val gate = WorkoutRecordingGate()
        recorder = WorkoutSetRecorder(gate::tryStart, gate::finish, mutableSetOf(), repository, owner,
            { state }, { transform -> state = transform(state) }, ports)
    }
    @After fun teardown() {
        owner.cancel()
        ProgramRepository.closeInstance()
        KpknDatabase.closeInstance()
    }
    private suspend fun record(intensity: Double? = null, advanced: SetAdvancedFeedback = SetAdvancedFeedback(), value: Double = 6.0) =
        recorder.record(weight = 20.0, value = value, intensity = intensity, advanced = advanced, loadMode = LoadModeV2.LOAD, unitMode = UnitModeV2.REPS)
    private suspend fun room() = repository.databaseForTests().stateDao().getOngoingWorkout()!!.toOngoingWorkoutState()!!

    @Test fun sinInformeRechazaAntesDelPreflushYNoMutaElEstado() = runBlocking {
        val before = state
        val stored = room()
        assertTrue(record() is RecordSetResult.Rejected)
        assertEquals(before, state)
        assertEquals(stored, room())
        assertEquals(0, persists)
        assertEquals(0, advances)
        assertEquals(listOf(RecordingEffortRequired), rejections)
    }
    @Test fun unObjetivoDeFalloNoCuentaComoFalloReportado() = runBlocking {
        state = state.copy(session = session.copy(exercises = listOf(session.exercises.single().copy(sets = listOf(
            session.exercises.single().sets.single().copy(isFailure = true, intensityMode = IntensityMode.FAILURE),
        )))))
        val before = state
        assertTrue(record() is RecordSetResult.Rejected)
        assertEquals(before, state)
        assertEquals(0, persists)
    }
    @Test fun elMinimoRpeCeroElegidoSeGuardaYAvanza() = runBlocking {
        assertTrue(record(0.0, SetAdvancedFeedback(actualIntensityMode = IntensityMode.RPE, actualIntensityValue = 0.0)) is RecordSetResult.Created)
        assertEquals(0.0, room().completedSets["exercise_0"]!!.actualIntensityValue!!, 0.0)
        assertEquals(1, advances)
        assertTrue(persists > 0)
    }
    @Test fun rirMayorQueDiezEsValidoYUnaEdicionConservaElInforme() = runBlocking {
        assertTrue(record(advanced = SetAdvancedFeedback(rir = 12, actualIntensityMode = IntensityMode.RIR, actualIntensityValue = 12.0)) is RecordSetResult.Created)
        assertTrue(record(value = 7.0) is RecordSetResult.Updated)
        val saved = room().completedSets["exercise_0"]!!
        assertEquals(IntensityMode.RIR, saved.actualIntensityMode)
        assertEquals(12.0, saved.actualIntensityValue!!, 0.0)
        assertEquals(12, saved.rir)
        assertEquals(1, advances)
    }
    @Test fun falloExplicitoCuentaAunqueElPlanNoLoPida() = runBlocking {
        assertTrue(record(advanced = SetAdvancedFeedback(actualIntensityMode = IntensityMode.FAILURE)) is RecordSetResult.Created)
        assertTrue(room().completedSets["exercise_0"]!!.isFailure)
        assertEquals(1, advances)
    }
    @Test fun calentamientoConservaSuContratoSinExigirUnReporteDeFuerza() = runBlocking {
        assertTrue(record(advanced = SetAdvancedFeedback(isWarmup = true)) is RecordSetResult.Created)
        assertTrue(room().completedSets["exercise_0"]!!.isWarmup)
        assertTrue(rejections.isEmpty())
    }

    @Test fun valoresNoFinitosFueraDeEscalaYGuionSoloRmNoPersisten() = runBlocking {
        val before = state
        listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0, 11.0).forEach { value ->
            assertTrue(record(value, SetAdvancedFeedback(actualIntensityMode = IntensityMode.RPE, actualIntensityValue = value)) is RecordSetResult.Rejected)
        }
        assertTrue(record(advanced = SetAdvancedFeedback(actualIntensityMode = IntensityMode.SOLO_RM)) is RecordSetResult.Rejected)
        assertEquals(before, state)
        assertEquals(0, persists)
        assertEquals(0, advances)
    }
    @Test fun completarElRelojDeFuerzaNoEsCompletarDescansoNiCardio() {
        val timed = session.exercises.single().copy(trainingMode = TrainingMode.TIME, sets = listOf(ExerciseSet("t", targetDuration = 30, unitModeV2 = UnitModeV2.TIME)))
        val step = WorkoutStep(WorkoutStepType.WORKING_SET, timed.id, timed.name, stepKey = "timed", setIndex = 0)
        val pending = state.copy(activeStepKey = "timed")
        assertEquals(step, timedStrengthStepAwaitingReport(pending, listOf(timed), listOf(step)))
        assertNull(timedStrengthStepAwaitingReport(pending.copy(isRestTimerRunning = true), listOf(timed), listOf(step)))
        val cardioStep = step.copy(type = WorkoutStepType.CARDIO)
        assertNull(timedStrengthStepAwaitingReport(pending, listOf(timed), listOf(cardioStep)))
        val done = pending.copy(completedSets = mapOf("exercise_0" to CompletedSet("actual", timeSeconds = 30, rpe = 8.0)))
        assertNull(timedStrengthStepAwaitingReport(done, listOf(timed), listOf(step)))
    }
}

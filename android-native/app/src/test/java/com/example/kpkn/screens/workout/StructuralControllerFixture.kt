package com.example.kpkn.screens.workout

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WeekVariant
import com.example.kpkn.data.models.WorkoutContextProfile
import com.example.kpkn.data.repository.TrainingObservationRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.exercises.resolvedCanonicalExerciseId
import com.example.kpkn.services.workout.WorkoutRestAlertManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout

/**
 * El controlador estructural de la sesión en vivo con un repositorio de verdad (Room en Robolectric) y un estado en memoria, para las
 * pruebas de reemplazo y de edición de series de LV5. Abrirlo con [open] y cerrarlo con [close] (en `@Before` y `@After`).
 *
 * Las pruebas que lo usan llevan `@RunWith(RobolectricTestRunner::class)` y la regla `RoomTestRule`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class StructuralControllerFixture private constructor(
    val repository: ProgramRepository,
    initialState: WorkoutUiState,
    programId: String,
) {
    /** El estado vivo de la pantalla: lo lee y lo escribe el controlador. */
    var state: WorkoutUiState = initialState

    val controller: WorkoutStructuralPersistenceController

    init {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val session = checkNotNull(initialState.session) { "el estado inicial necesita sesión" }
        val finishController = WorkoutFinishController(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            appContext = context,
            repository = repository,
            programId = programId,
            sessionId = session.id,
            exerciseIndex = { emptyMap() },
            performanceRangeStore = PerformanceRangeStore(context),
            restAlertManager = WorkoutRestAlertManager(context),
            restTimer = RestTimerController(
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
                alertSink = object : RestTimerAlertSink {
                    override fun scheduleRestEnd(
                        durationSeconds: Int,
                        sessionName: String,
                        exerciseName: String,
                        endAtOverrideMs: Long,
                        isAdjustment: Boolean,
                    ): String = "timer"

                    override fun onTimerFinishedInApp(expectedTimerId: String?) = Unit

                    override fun cancelRestAlerts() = Unit
                },
            ),
            getState = { state },
            updateState = { transform -> state = transform(state) },
            sessionForActiveMode = { current, _ -> current },
            canonicalExerciseKey = { it.id },
            catalogInfoForCompletedExercise = { null },
            updatePredictionBias = {},
            deferOnComplete = {},
            prepareVoiceDiagnosticExport = {},
            clearActiveWorkout = {},
        )
        controller = WorkoutStructuralPersistenceController(
            repository = repository,
            programId = programId,
            sessionId = session.id,
            finishController = finishController,
            getState = { state },
            updateState = { transform -> state = transform(state) },
            ports = object : WorkoutStructuralPersistenceController.Ports {
                override fun visibleExercises(state: WorkoutUiState): List<Exercise> = state.session!!.allExercises()
                override fun sessionForActiveMode(base: Session, mode: WeekVariant): Session = base
                override fun normalizeSupersetsForWorkout(session: Session): Session = session
                override fun canonicalExerciseKey(exercise: Exercise): String = exercise.resolvedCanonicalExerciseId()
                override fun activeContextProfile(exerciseId: String): WorkoutContextProfile? = null
                override fun defaultContextProfileForExercise(exercise: Exercise): WorkoutContextProfile =
                    WorkoutContextProfile(id = "default", exerciseKey = exercise.resolvedCanonicalExerciseId())
                override fun refreshLoadSuggestions(state: WorkoutUiState) = Unit
                override fun persistOngoingState() = Unit
                override fun invalidateEditorDraft() = Unit
                override fun firstIncompleteStepKey(state: WorkoutUiState): String? = null
                override fun stepKeyExists(state: WorkoutUiState, stepKey: String?): Boolean = false
            },
        )
    }

    /** El ejercicio [id] de la sesión de ahora. */
    fun exercise(id: String): Exercise = state.session!!.allExercises().first { it.id == id }

    companion object {
        /** [programId] vacío = sesión suelta; con el id de un programa ya añadido al repositorio, el controlador puede persistir en él. */
        fun open(initialState: WorkoutUiState, programId: String = ""): StructuralControllerFixture = runBlocking {
            Dispatchers.setMain(UnconfinedTestDispatcher())
            val context = ApplicationProvider.getApplicationContext<Context>()
            val repository = ProgramRepository.initForTests(context)
            withTimeout(10_000) { repository.isReady.first { it } }
            // Las piezas que abren la base por su cuenta (el rastreador de rangos) usan la misma base de la prueba.
            KpknDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }
                .set(null, repository.databaseForTests())
            StructuralControllerFixture(repository, initialState, programId)
        }

        fun close() {
            try {
                TrainingObservationRepository::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }.set(null, null)
                ProgramRepository.closeInstance()
                KpknDatabase.closeInstance()
            } finally {
                Dispatchers.resetMain()
            }
        }
    }
}

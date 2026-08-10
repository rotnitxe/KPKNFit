package com.example.kpkn.screens.sessioneditor

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.WeekVariant
import com.example.kpkn.data.repository.ProgramRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class SessionEditorViewModelRulesTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: ProgramRepository

    @Before
    fun setup() = runBlocking {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        ProgramRepository.initForTests(context)
        repository = ProgramRepository.getInstance()
        repository.clearPrograms()
        repository.clearActiveProgram()
        repository.clearOngoingWorkout()
        withTimeout(10_000) {
            while (!repository.isReady.value) delay(25)
        }
    }

    @After
    fun tearDown() {
        ProgramRepository.closeInstance()
        Dispatchers.resetMain()
    }

    @Test
    fun applyingRules_updatesVisibleSession_persistsDraft_andReopensWithSameValues() = runBlocking {
        val programId = "program-rules-global"
        val sessionId = "session-rules-global"
        val session = Session(
            id = sessionId,
            name = "Reglas",
            exercises = listOf(
                Exercise(
                    id = "exercise-rules-global",
                    name = "Back Squat",
                    exerciseDbId = "squat",
                    restTime = 90,
                    sets = listOf(
                        ExerciseSet("set-1", targetReps = 8, targetRPE = 8.0),
                        ExerciseSet("set-2", targetReps = 8, targetRPE = 8.0),
                    ),
                ),
            ),
        )
        repository.addProgram(programWithSession(programId, session))

        val vm = createViewModel(programId, sessionId)
        awaitSession(vm)

        vm.updateRuleDefaults(
            setCount = 3,
            reps = 6,
            rpe = 7.5,
            normalRestSeconds = 45,
            intensityType = DefaultIntensityType.RPE,
        )

        val outcome = vm.applyRuleDefaultsToSession()
        assertTrue(outcome is ApplyRulesOutcome.Applied)

        val visible = vm.uiState.value.session!!.exercises.single()
        assertEquals(3, visible.sets.size)
        assertEquals(6, visible.sets.first().targetReps)
        assertEquals(7.5, visible.sets.first().targetRPE ?: 0.0, 0.001)
        assertEquals(IntensityMode.RPE, visible.sets.first().intensityMode)
        assertEquals(45, visible.restTime)
        assertEquals(SessionEditorSheet.NONE, vm.uiState.value.sheet)

        val persisted = vm.persistedDraftFor("week", 0, 0, sessionId)
        assertNotNull(persisted)
        assertEquals(3, persisted!!.session.exercises.single().sets.size)
        assertEquals(6, persisted.session.exercises.single().sets.first().targetReps)
        assertEquals(45, persisted.session.exercises.single().restTime)

        val reopened = createViewModel(programId, sessionId)
        awaitSession(reopened)
        val restored = reopened.uiState.value.session!!.exercises.single()
        assertEquals(3, restored.sets.size)
        assertEquals(6, restored.sets.first().targetReps)
        assertEquals(7.5, restored.sets.first().targetRPE ?: 0.0, 0.001)
        assertEquals(IntensityMode.RPE, restored.sets.first().intensityMode)
        assertEquals(45, restored.restTime)
    }

    @Test
    fun applyingRules_toGroup_onlyChangesExercisesInsideThatGroup() = runBlocking {
        val programId = "program-rules-group"
        val sessionId = "session-rules-group"
        val session = Session(
            id = sessionId,
            name = "Grupo",
            exercises = listOf(
                Exercise(
                    id = "exercise-outside-group",
                    name = "Press",
                    sets = listOf(ExerciseSet("outside-set", targetReps = 10, targetRPE = 8.0)),
                    restTime = 90,
                ),
            ),
            parts = listOf(
                SessionPart(
                    id = "part-rules",
                    name = "Piernas",
                    exercises = listOf(
                        Exercise(
                            id = "exercise-inside-group",
                            name = "Back Squat",
                            exerciseDbId = "squat",
                            sets = listOf(ExerciseSet("inside-set", targetReps = 10, targetRPE = 8.0)),
                            restTime = 90,
                        ),
                    ),
                ),
            ),
        )
        repository.addProgram(programWithSession(programId, session))

        val vm = createViewModel(programId, sessionId)
        awaitSession(vm)
        vm.updateRuleDefaults(
            partId = "part-rules",
            setCount = 2,
            reps = 5,
            rpe = 6.5,
            normalRestSeconds = 30,
        )

        val outcome = vm.applyRuleDefaultsToSession("part-rules")
        assertTrue(outcome is ApplyRulesOutcome.Applied)

        val current = vm.uiState.value.session!!
        val inside = current.parts.single().exercises.single()
        val outside = current.exercises.single()
        assertEquals(2, inside.sets.size)
        assertEquals(5, inside.sets.first().targetReps)
        assertEquals(30, inside.restTime)
        assertEquals(1, outside.sets.size)
        assertEquals(10, outside.sets.first().targetReps)
        assertEquals(90, outside.restTime)
    }

    @Test
    fun applyingRules_toActiveVariant_keepsOtherVariantsUntouched() = runBlocking {
        val programId = "program-rules-variant"
        val sessionId = "session-rules-variant"
        val main = Exercise(
            id = "main-exercise",
            name = "Main",
            sets = listOf(ExerciseSet("main-set", targetReps = 10, targetRPE = 8.0)),
        )
        val variant = Session(
            id = "variant-b",
            name = "Variante B",
            exercises = listOf(
                Exercise(
                    id = "variant-exercise",
                    name = "Variant",
                    sets = listOf(ExerciseSet("variant-set", targetReps = 10, targetRPE = 8.0)),
                ),
            ),
        )
        repository.addProgram(
            programWithSession(
                programId,
                Session(
                    id = sessionId,
                    name = "Variantes",
                    exercises = listOf(main),
                    sessionB = variant,
                ),
            ),
        )

        val vm = createViewModel(programId, sessionId)
        awaitSession(vm)
        vm.switchVariant(WeekVariant.B)
        vm.updateRuleDefaults(setCount = 2, reps = 4, rpe = 6.0, normalRestSeconds = 25)

        val outcome = vm.applyRuleDefaultsToSession()
        assertTrue(outcome is ApplyRulesOutcome.Applied)

        val current = vm.uiState.value.session!!
        assertEquals(10, current.exercises.single().sets.first().targetReps)
        assertEquals(2, current.sessionB!!.exercises.single().sets.size)
        assertEquals(4, current.sessionB.exercises.single().sets.first().targetReps)
        assertEquals(25, current.sessionB.exercises.single().restTime)
    }

    private fun createViewModel(programId: String, sessionId: String): SessionEditorViewModel =
        SessionEditorViewModel(
            application = ApplicationProvider.getApplicationContext<Application>(),
            programId = programId,
            sessionId = sessionId,
            draftWeekId = "week",
            draftMacroIndex = 0,
            draftMesoIndex = 0,
            draftDayOfWeek = null,
        )

    private suspend fun awaitSession(vm: SessionEditorViewModel) {
        withTimeout(5_000) {
            while (vm.uiState.value.session == null) {
                vm.retryLoadSession()
                delay(50)
            }
        }
    }

    private fun programWithSession(programId: String, session: Session): Program =
        Program(
            id = programId,
            name = "Programa de reglas",
            structure = ProgramStructure.SIMPLE,
            macrocycles = listOf(
                Macrocycle(
                    id = "macro",
                    name = "Macro",
                    blocks = listOf(
                        Block(
                            id = "block",
                            name = "Block",
                            mesocycles = listOf(
                                Mesocycle(
                                    id = "meso",
                                    name = "Meso",
                                    weeks = listOf(
                                        ProgramWeek(
                                            id = "week",
                                            name = "Semana",
                                            sessions = listOf(session),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
}

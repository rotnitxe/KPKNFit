package com.example.kpkn.screens.sessioneditor

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.repository.ProgramRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class SessionEditorViewModelCatalogTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: ProgramRepository

    @Before
    fun setup() = runBlocking {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        ProgramRepository.init(context)
        repository = ProgramRepository.getInstance()
        repository.clearPrograms()
        repository.clearActiveProgram()
        repository.clearOngoingWorkout()
        withTimeout(10_000) {
            while (!repository.isReady.value) {
                delay(25)
            }
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun strengthCatalogExerciseDefaultsToRepsWithoutPercentRm() = runBlocking {
        val programId = "program-catalog-reps"
        val sessionId = "session-catalog-reps"
        repository.addProgram(
            Program(
                id = programId,
                name = "Programa",
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
                                                sessions = listOf(Session(id = sessionId, name = "Día")),
                                            )
                                        ),
                                    )
                                ),
                            )
                        ),
                    )
                ),
            )
        )
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = SessionEditorViewModel(
            application = app,
            programId = programId,
            sessionId = sessionId,
            draftWeekId = "week",
            draftMacroIndex = 0,
            draftMesoIndex = 0,
            draftDayOfWeek = null,
        )
        withTimeout(5_000) {
            while (vm.uiState.value.session == null) {
                vm.retryLoadSession()
                delay(50)
            }
        }

        vm.addExerciseToPart(
            partId = null,
            info = ExerciseMuscleInfo(
                id = "sentadilla_barra",
                name = "Sentadilla con barra",
                category = "Fuerza",
                equipment = "Barra",
            ),
        )

        val exercise = vm.uiState.value.session!!.exercises.single()
        assertEquals(TrainingMode.REPS, exercise.trainingMode)
        assertNull(exercise.sets.single().targetPercentageRM)
        assertNull(exercise.sets.single().intensityMode)
    }
}

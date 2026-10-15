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
import com.example.kpkn.data.models.RepRange
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.repository.ProgramRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ER2c, por el ViewModel real: aplicar reglas desde el editor deja la carga del generador (95 kg al 80 % de un 1RM de 120 kg), igual
 * que guardar. Antes, la normalización de las reglas la reescribía a 96 kg.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class SessionEditorRulesRmLoadViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: ProgramRepository

    @Before
    fun setUp() = runBlocking {
        Dispatchers.setMain(dispatcher)
        ProgramRepository.initForTests(ApplicationProvider.getApplicationContext<Context>())
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

    /** Sentadilla del generador: 4 × 4–6 al 80 % de un 1RM de 120 kg, con 95 kg guardados. */
    private fun generatedSquat(): Exercise = Exercise(
        id = "squat",
        name = "Sentadilla trasera con barra alta",
        trainingMode = TrainingMode.RM,
        reference1RM = 120.0,
        sets = List(4) { index ->
            ExerciseSet(
                id = "squat-$index",
                targetReps = 6,
                targetRepsRange = RepRange(4, 6),
                targetRIR = 2,
                intensityMode = IntensityMode.SOLO_RM,
                targetPercentageRM = 80.0,
                weight = 95.0,
                loadBasis = LoadBasis.PERCENT_1RM,
            )
        },
    )

    private suspend fun openEditor(exercise: Exercise): SessionEditorViewModel {
        val session = Session(id = "pierna-a", name = "Pierna A", exercises = listOf(exercise))
        repository.addProgram(program("program-rules-rm", session))
        val viewModel = SessionEditorViewModel(
            application = ApplicationProvider.getApplicationContext<Application>(),
            programId = "program-rules-rm",
            sessionId = session.id,
            draftWeekId = "week",
            draftMacroIndex = 0,
            draftMesoIndex = 0,
            draftDayOfWeek = 1,
        )
        viewModel.awaitSessionLoaded()
        return viewModel
    }

    private fun SessionEditorViewModel.exercise(id: String): Exercise =
        requireNotNull(uiState.value.session).exercises.single { it.id == id }

    @Test
    fun aplicarReglasDesdeElEditorNoReescribeLaCargaDelGenerador() = runBlocking {
        val viewModel = openEditor(generatedSquat())
        viewModel.updateRuleDefaults(setCount = 4, reps = 6, rpe = 2.0, intensityType = DefaultIntensityType.RIR)
        assertTrue(viewModel.applyRuleDefaultsToSession() is ApplyRulesOutcome.Applied)
        // Las reglas fijan series, reps y RIR; el 80 % y los 95 kg del generador se quedan.
        assertEquals(listOf(95.0, 95.0, 95.0, 95.0), viewModel.exercise("squat").sets.map { it.weight })
    }

    private fun program(programId: String, vararg sessions: Session) = Program(
        id = programId,
        name = "Programa de prueba",
        structure = ProgramStructure.SIMPLE,
        macrocycles = listOf(
            Macrocycle(
                id = "macro",
                name = "Macro",
                blocks = listOf(
                    Block(
                        id = "block",
                        name = "Bloque",
                        mesocycles = listOf(
                            Mesocycle(
                                id = "meso",
                                name = "Meso",
                                weeks = listOf(ProgramWeek(id = "week", name = "Semana 1", sessions = sessions.toList())),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )
}

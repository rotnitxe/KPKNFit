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
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ER2a, por el ViewModel real: cambiar el %RM, cambiar la referencia, añadir una serie y crear una superserie. Solo el primero y el
 * segundo recalculan cargas; los otros dos dejan la carga guardada del programa.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class SessionEditorRmLoadEditTest {
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

    /** Sentadilla y press del generador: 4 series al 80 % de un 1RM de 120 kg (95 kg) y de 100 kg (75 kg). */
    private fun rmExercise(id: String, name: String, reference: Double, weight: Double) = Exercise(
        id = id,
        name = name,
        trainingMode = TrainingMode.RM,
        reference1RM = reference,
        sets = List(4) { index ->
            ExerciseSet(
                id = "$id-$index",
                targetReps = 6,
                targetRepsRange = RepRange(4, 6),
                targetRIR = 2,
                intensityMode = IntensityMode.SOLO_RM,
                targetPercentageRM = 80.0,
                weight = weight,
                loadBasis = LoadBasis.PERCENT_1RM,
            )
        },
    )

    private suspend fun openEditor(vararg exercises: Exercise): SessionEditorViewModel {
        val session = Session(id = "pierna-a", name = "Pierna A", exercises = exercises.toList())
        repository.addProgram(program("program-rm", session))
        val viewModel = SessionEditorViewModel(
            application = ApplicationProvider.getApplicationContext<Application>(),
            programId = "program-rm",
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
    fun cambiarElPorcentajeDeUnaSerieRecalculaSuCargaRedondeada() = runBlocking {
        val viewModel = openEditor(rmExercise("squat", "Sentadilla", reference = 120.0, weight = 95.0))
        viewModel.updateSet(null, "squat", "squat-0") {
            it.copy(targetPercentageRM = 85.0, intensityMode = null, targetRPE = null)
        }
        // 120 × 85 % = 102 → 102,5. La carga de las otras tres series no cambia.
        assertEquals(listOf(102.5, 95.0, 95.0, 95.0), viewModel.exercise("squat").sets.map { it.weight })
    }

    @Test
    fun cambiarLaReferenciaRecalculaLasSeriesRmRedondeadas() = runBlocking {
        val viewModel = openEditor(rmExercise("squat", "Sentadilla", reference = 120.0, weight = 95.0))
        viewModel.updateExercise(null, "squat") { it.copy(reference1RM = 130.0) }
        // 130 × 80 % = 104 → 105.
        assertEquals(listOf(105.0, 105.0, 105.0, 105.0), viewModel.exercise("squat").sets.map { it.weight })
    }

    @Test
    fun anadirUnaSerieCalculaSuCargaConSuPorcentajeDeFatiga() = runBlocking {
        val viewModel = openEditor(rmExercise("squat", "Sentadilla", reference = 120.0, weight = 95.0))
        viewModel.addSet(null, "squat")
        // La nueva va al 76 % (80 − 4 por las 6 reps): 91,2 → 90. Las anteriores conservan sus 95 kg.
        val sets = viewModel.exercise("squat").sets
        assertEquals(listOf(95.0, 95.0, 95.0, 95.0, 90.0), sets.map { it.weight })
    }

    @Test
    fun crearUnaSuperserieNoCambiaNingunaCargaDelPrograma() = runBlocking {
        val viewModel = openEditor(
            rmExercise("squat", "Sentadilla", reference = 120.0, weight = 95.0),
            rmExercise("bench", "Press de banca", reference = 100.0, weight = 75.0),
        )
        viewModel.openSupersetCreator(null, listOf("squat", "bench"))
        assertNotNull(viewModel.exercise("squat").supersetGroupRef)
        assertEquals(listOf(95.0, 95.0, 95.0, 95.0), viewModel.exercise("squat").sets.map { it.weight })
        assertEquals(listOf(75.0, 75.0, 75.0, 75.0), viewModel.exercise("bench").sets.map { it.weight })
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

package com.example.kpkn.screens.sessioneditor

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
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
import com.example.kpkn.data.repository.SessionTemplateRepository
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
    private val viewModelStore = ViewModelStore()
    private var nextViewModelKey = 0

    @Before
    fun setup() = runBlocking {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        // El repositorio de plantillas del editor es un singleton: que no llegue vivo desde otra prueba.
        SessionTemplateRepository.resetForTests()
        ProgramRepository.initForTests(context)
        repository = ProgramRepository.getInstance()
        // Y que lea la base en memoria de ESTA prueba, no el kpkn.db del disco.
        setDatabaseSingletonForTest(repository.databaseForTests())
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
        try {
            // Primero los editores (cancela su viewModelScope), luego el repositorio de plantillas y al final la base.
            viewModelStore.clear()
            SessionTemplateRepository.resetForTests()
            ProgramRepository.closeInstance()
        } finally {
            setDatabaseSingletonForTest(null)
            Dispatchers.resetMain()
        }
    }

    /** Cada editor va al store de la prueba: [tearDown] lo limpia antes de cerrar la base. */
    private fun createEditor(programId: String, sessionId: String): SessionEditorViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        return SessionEditorViewModel(
            application = app,
            programId = programId,
            sessionId = sessionId,
            draftWeekId = "week",
            draftMacroIndex = 0,
            draftMesoIndex = 0,
            draftDayOfWeek = null,
        ).also { viewModel -> viewModelStore.put("session-editor-${nextViewModelKey++}", viewModel) }
    }

    private fun setDatabaseSingletonForTest(database: KpknDatabase?) {
        KpknDatabase::class.java.getDeclaredField("INSTANCE").apply {
            isAccessible = true
        }.set(null, database)
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
        val vm = createEditor(programId, sessionId)
        vm.awaitSessionLoaded()

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

package com.example.kpkn.screens.sessioneditor

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WeekVariant
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.data.repository.SessionTemplateRepository
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.data.sessions.SessionTemplateKind
import com.example.kpkn.data.sessions.SessionTemplatePublicationStatus
import com.example.kpkn.data.sessions.SessionTemplateSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
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
class SessionEditorViewModelTemplatesTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: ProgramRepository

    @Before
    fun setup() = runBlocking {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        SessionTemplateRepository.resetForTests()
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
    fun selectTemplate_on_empty_session_adds_exercises() = runBlocking {
        val programId = "program-template-empty"
        val sessionId = "session-template-empty"
        repository.addProgram(programWithSession(programId, Session(id = sessionId, name = "Vacía")))
        val vm = createViewModel(programId, sessionId)
        awaitSession(vm)
        assertTrue(vm.uiState.value.session!!.allExercises().isEmpty())

        val template = trainingTemplate()
        vm.selectTemplate(template)
        awaitAppliedExercises(vm)

        assertTrue(vm.uiState.value.session!!.allExercises().isNotEmpty())
        assertTrue(vm.uiState.value.snackbarMessage.orEmpty().contains(template.name))
    }

    @Test
    fun selectTemplate_with_null_variant_falls_back_to_session() = runBlocking {
        val programId = "program-template-variant"
        val sessionId = "session-template-variant"
        repository.addProgram(programWithSession(programId, Session(id = sessionId, name = "Base")))
        val vm = createViewModel(programId, sessionId)
        awaitSession(vm)
        vm.switchVariant(WeekVariant.B)
        assertTrue(vm.uiState.value.activeVariantSession == null)
        assertNotNull(vm.uiState.value.session)

        val template = trainingTemplate()
        vm.selectTemplate(template)
        awaitAppliedExercises(vm)

        assertTrue(vm.uiState.value.session!!.allExercises().isNotEmpty())
        assertTrue(vm.uiState.value.snackbarMessage.orEmpty().contains("aplicada"))
    }

    private fun trainingTemplate() = SessionTemplate(
        id = "sys-training-fixture",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Empuje de prueba",
        description = "Plantilla TRAINING de fixture",
        kind = SessionTemplateKind.TRAINING,
        publicationStatus = SessionTemplatePublicationStatus.VERIFIED,
        session = Session(
            id = "tpl-session",
            name = "Empuje",
            exercises = listOf(Exercise(id = "ex-press", name = "Press banca")),
        ),
    )

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

    private suspend fun awaitAppliedExercises(vm: SessionEditorViewModel) {
        withTimeout(10_000) {
            while (
                vm.uiState.value.session?.allExercises().isNullOrEmpty() ||
                    vm.uiState.value.snackbarMessage.isNullOrBlank()
            ) {
                delay(50)
            }
        }
    }

    private fun programWithSession(programId: String, session: Session): Program =
        Program(
            id = programId,
            name = "Programa de plantillas",
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

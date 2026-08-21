package com.example.kpkn.screens.sessioneditor

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.ExerciseMuscleInfo
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
import com.example.kpkn.data.repository.SessionTemplateRepository
import com.example.kpkn.screens.sessioneditor.components.UserTemplateSaveState
import com.example.kpkn.screens.sessioneditor.components.executeUserTemplateSave
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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

    @Test
    fun picker_relationship_cardio_and_superset_commands_route_to_active_b_c_d() = runBlocking {
        val programId = "program-routes-bcd"
        val sessionId = "session-routes-bcd"
        fun variant(slot: String): Session = Session(
            id = "variant-$slot",
            name = "Variante $slot",
            exercises = listOf(
                Exercise(
                    id = "anchor-$slot",
                    name = "Ancla $slot",
                    exerciseDbId = "anchor-$slot",
                ),
                Exercise(
                    id = "second-$slot",
                    name = "Segundo $slot",
                    exerciseDbId = "second-$slot",
                ),
            ),
            parts = listOf(SessionPart(id = "cardio-$slot", name = "Cardio", isCardioGroup = true)),
        )
        repository.addProgram(
            programWithSession(
                programId,
                Session(
                    id = sessionId,
                    name = "Variantes BCD",
                    exercises = listOf(Exercise(id = "base", name = "Base")),
                    sessionB = variant("B"),
                    sessionC = variant("C"),
                    sessionD = variant("D"),
                ),
            ),
        )

        val vm = createViewModel(programId, sessionId)
        awaitSession(vm)
        listOf(WeekVariant.B, WeekVariant.C, WeekVariant.D).forEach { slot ->
            vm.switchVariant(slot)
            val suffix = slot.name
            val active = vm.uiState.value.activeVariantSession!!
            val addedId = vm.addExerciseToPart(
                partId = null,
                info = ExerciseMuscleInfo(
                    id = "added-$suffix",
                    name = "Añadido $suffix",
                    category = "Fuerza",
                    equipment = "Barra",
                ),
            )
            assertTrue(vm.uiState.value.activeVariantSession!!.allExercises().any { it.id == addedId })
            assertTrue("A no debe mutar al editar $slot", vm.uiState.value.session!!.exercises.none { it.id == addedId })

            vm.openRelationshipPicker(partId = null, exerciseId = active.exercises.first().id)
            assertEquals(SessionEditorSheet.RELATIONSHIP_PICKER, vm.uiState.value.sheet)
            assertEquals(active.exercises.first().id, vm.uiState.value.pickerTargetExerciseId)

            vm.openCardioPicker()
            assertEquals(SessionEditorSheet.CARDIO_PICKER, vm.uiState.value.sheet)
            assertEquals("cardio-$suffix", vm.uiState.value.pickerTargetPartId)

            vm.openSupersetCreator(null, listOf(active.exercises[0].id, active.exercises[1].id))
            val updatedActive = vm.uiState.value.activeVariantSession!!
            assertTrue("Superset debe editar sólo $slot", updatedActive.allSupersetGroups().isNotEmpty())
            assertTrue(vm.uiState.value.session!!.allSupersetGroups().isEmpty())
        }
    }

    @Test
    fun saveCurrentSessionAsTemplate_returns_success_only_after_room_readback() = runBlocking {
        val programId = "program-template-durable"
        val sessionId = "session-template-durable"
        repository.addProgram(
            programWithSession(
                programId,
                Session(
                    id = sessionId,
                    name = "Plantilla durable",
                    exercises = listOf(
                        Exercise(
                            id = "exercise-durable",
                            name = "Press banca",
                            sets = listOf(ExerciseSet("durable-set", targetReps = 5, targetRPE = 7.0)),
                        ),
                    ),
                ),
            ),
        )
        val vm = createViewModel(programId, sessionId)
        awaitSession(vm)
        val result = vm.saveCurrentSessionAsTemplate(
            name = "Mi plantilla durable",
            description = "Read-back",
            tags = emptyList(),
        )
        assertTrue(result.exceptionOrNull()?.stackTraceToString().orEmpty(), result.isSuccess)
        val template = result.getOrNull()!!
        try {
            val repo = SessionTemplateRepository.getInstance(ApplicationProvider.getApplicationContext())
            withTimeout(5_000) { repo.isReady.first { it } }
            assertEquals(template.id, repo.getByIdAfterReady(template.id)?.id)
            assertEquals("Mi plantilla durable", repo.getByIdAfterReady(template.id)?.name)
        } finally {
            SessionTemplateRepository.getInstance(ApplicationProvider.getApplicationContext())
                .deleteUserTemplateNow(template.id)
        }
    }

    @Test
    fun saveCurrentSessionAsTemplate_fromBaseSession_publishesFlow_after_room_write() = runBlocking {
        val programId = "program-template-base-flow"
        val sessionId = "session-template-base-flow"
        repository.addProgram(
            programWithSession(
                programId,
                Session(
                    id = sessionId,
                    name = "Base visible",
                    exercises = listOf(
                        Exercise(
                            id = "base-flow-exercise",
                            name = "Sentadilla",
                            sets = listOf(ExerciseSet("base-flow-set", targetReps = 5, targetRPE = 7.0)),
                        ),
                    ),
                ),
            ),
        )
        val vm = createViewModel(programId, sessionId)
        awaitSession(vm)

        val result = vm.saveCurrentSessionAsTemplateNow(
            name = "Plantilla base Flow",
            description = "La sesión base también es persistible",
        )
        assertTrue(result.exceptionOrNull()?.stackTraceToString().orEmpty(), result.isSuccess)
        val template = result.getOrNull()!!
        try {
            val repo = SessionTemplateRepository.getInstance(ApplicationProvider.getApplicationContext())
            withTimeout(5_000) {
                repo.userTemplates.first { templates -> templates.any { it.id == template.id } }
            }
            withTimeout(5_000) {
                repo.allTemplates.first { templates -> templates.any { it.id == template.id } }
            }
            assertEquals("Plantilla base Flow", repo.getByIdAfterReady(template.id)?.name)
        } finally {
            SessionTemplateRepository.getInstance(ApplicationProvider.getApplicationContext())
                .deleteUserTemplateNow(template.id)
        }
    }

    @Test
    fun saveTemplateDialogCommand_is_result_driven_and_publishes_after_room_write() = runBlocking {
        val programId = "program-template-dialog-command"
        val sessionId = "session-template-dialog-command"
        repository.addProgram(
            programWithSession(
                programId,
                Session(
                    id = sessionId,
                    name = "Sesión dialog",
                    exercises = listOf(
                        Exercise(
                            id = "dialog-exercise",
                            name = "Press banca",
                            sets = listOf(ExerciseSet("dialog-set", targetReps = 5, targetRPE = 7.0)),
                        ),
                    ),
                ),
            ),
        )
        val vm = createViewModel(programId, sessionId)
        awaitSession(vm)
        val state = executeUserTemplateSave(
            name = "Plantilla dialog durable",
            description = "Sólo éxito después de Room",
            onSave = { name, description -> vm.saveCurrentSessionAsTemplateNow(name, description) },
        )
        assertTrue(state is UserTemplateSaveState.Success)
        val template = (state as UserTemplateSaveState.Success).template
        try {
            val repo = SessionTemplateRepository.getInstance(ApplicationProvider.getApplicationContext())
            withTimeout(5_000) {
                repo.allTemplates.first { templates -> templates.any { it.id == template.id && it.name == "Plantilla dialog durable" } }
            }
            assertEquals("Plantilla dialog durable", repo.getByIdAfterReady(template.id)?.name)
        } finally {
            SessionTemplateRepository.getInstance(ApplicationProvider.getApplicationContext())
                .deleteUserTemplateNow(template.id)
        }
    }

    @Test
    fun saveCurrentSessionAsTemplate_rejects_nonExecutable_base_and_variant() = runBlocking {
        val programId = "program-template-invalid"
        val sessionId = "session-template-invalid"
        val base = Session(
            id = sessionId,
            name = "Sin receta",
            exercises = listOf(Exercise(id = "base-placeholder", name = "Press banca")),
            sessionB = Session(
                id = "variant-b-invalid",
                name = "B sin receta",
                exercises = listOf(Exercise(id = "variant-placeholder", name = "Press banca")),
            ),
        )
        repository.addProgram(programWithSession(programId, base))
        val vm = createViewModel(programId, sessionId)
        awaitSession(vm)

        assertTrue(vm.saveCurrentSessionAsTemplateNow("Inválida A", "").isFailure)
        vm.switchVariant(WeekVariant.B)
        assertTrue(vm.saveCurrentSessionAsTemplateNow("Inválida B", "").isFailure)
    }

    @Test
    fun saveCurrentSessionAsTemplate_rejects_mixed_valid_and_placeholder_strength_cards() = runBlocking {
        val programId = "program-template-mixed-invalid"
        val sessionId = "session-template-mixed-invalid"
        val mixed = Session(
            id = sessionId,
            name = "Fuerza mixta",
            exercises = listOf(
                Exercise(
                    id = "valid-card",
                    name = "Press banca",
                    sets = listOf(ExerciseSet("valid-set", targetReps = 5, targetRPE = 7.0)),
                ),
                Exercise(
                    id = "placeholder-card",
                    name = "Accesorio sin receta",
                    sets = listOf(ExerciseSet("mode-only", intensityMode = IntensityMode.LOAD)),
                ),
            ),
        )
        repository.addProgram(programWithSession(programId, mixed))
        val vm = createViewModel(programId, sessionId)
        awaitSession(vm)

        assertTrue(vm.saveCurrentSessionAsTemplateNow("Mixta inválida", "").isFailure)
    }

    @Test
    fun saveCurrentSessionAsTemplate_rejects_target_only_modality_hidden_by_valid_strength() = runBlocking {
        val programId = "program-template-modal-invalid"
        val sessionId = "session-template-modal-invalid"
        val mixed = Session(
            id = sessionId,
            name = "Fuerza y cardio incompleto",
            exercises = listOf(
                Exercise(
                    id = "valid-card",
                    name = "Press banca",
                    sets = listOf(ExerciseSet("valid-set", targetReps = 5, targetRPE = 7.0)),
                ),
            ),
            parts = listOf(
                SessionPart(
                    id = "cardio-placeholder",
                    name = "Cardio sin receta",
                    isCardioGroup = true,
                    targetDurationMinutes = 20,
                ),
            ),
        )
        repository.addProgram(programWithSession(programId, mixed))
        val vm = createViewModel(programId, sessionId)
        awaitSession(vm)

        assertTrue(vm.saveCurrentSessionAsTemplateNow("Modal inválida", "").isFailure)
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

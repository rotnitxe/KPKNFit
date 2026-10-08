package com.example.kpkn.screens.onboarding

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramSchedulePlan
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.domain.onboarding.CapabilityLevel
import com.example.kpkn.domain.onboarding.CapabilitySkill
import com.example.kpkn.domain.onboarding.EquipmentSymbolId
import com.example.kpkn.domain.onboarding.GeneratedPlans
import com.example.kpkn.domain.onboarding.LiftMark
import com.example.kpkn.domain.onboarding.MuscleSymbol
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.domain.onboarding.WizChatMachineState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes

/**
 * Entreno v2 · toques rápidos con el reloj virtual: el aviso «No pude guardar esta respuesta» que se vio una vez en el
 * teléfono (y no se reprodujo en 32 toques rápidos) sale de la ruta de guardado del borrador (`mutateDraft` →
 * `persistAndPublish` → `persistDraft`). Esta prueba la castiga: cientos de escrituras de los pasos de Entreno (lugares,
 * material, objetivo, días, tiempo, programa, semana armada), navegación (atrás, editar, continuar) y salidas con
 * «Guardar y salir» sin esperar a que termine nada, con el reloj virtual avanzando a saltos, contra una persistencia que
 * replica el guard de revisión de Room (`SetupDraftRepository.save`): una escritura con revisión antigua o con la misma
 * revisión y otro contenido se rechaza y se anota.
 *
 * Lo que debe cumplirse al final: ningún guardado rechazado, ningún aviso «save» y la fila guardada en la misma revisión
 * que el borrador en memoria. Tras «Guardar y salir» se reabre un ViewModel nuevo sobre la misma fila (reanudar) y se sigue
 * golpeando: tampoco debe chocar con la revisión guardada.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class SetupWizardRapidTapsTest {

    private val dispatcher = StandardTestDispatcher()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val viewModelStore = ViewModelStore()
    private var vmCounter = 0
    private lateinit var app: Application
    private lateinit var persistence: FakeSetupWizardPersistence

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        app = ApplicationProvider.getApplicationContext()
        persistence = FakeSetupWizardPersistence()
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        Dispatchers.resetMain()
    }

    private fun newVm(handle: SavedStateHandle = SavedStateHandle()): SetupWizardViewModel =
        SetupWizardViewModel(
            app,
            handle,
            persistence,
            FakeSetupWizardEnvironment(),
            materializeOverride = SetupWizardMaterializer { draft -> SetupPreview(cannedProgram(draft.commitId), null) },
        ).also { viewModelStore.put("rapid-${vmCounter++}", it) }

    @Test
    fun hundredsOfFastTapsAndExitsNeverTripTheRevisionGuardNorShowTheSaveError() = runTest(dispatcher.scheduler, timeout = 10.minutes) {
        val random = Random(20261007)
        val first = newVm()
        first.initialize(SetupWizardMode.TRAINING_ONLY)
        settle(first)
        first.update { it.withEntrenoInputs() }
        settle(first)

        hammer(first, random, rounds = 220)
        settle(first)
        assertHealthy("tras los toques rápidos", first)

        // «Guardar y salir» en pleno ajetreo y reanudar con un ViewModel nuevo sobre la misma fila.
        hammer(first, random, rounds = 60, exitAt = 30)
        settle(first)
        assertHealthy("tras salir guardando", first)
        val exitedRevision = persistence.rows.getValue(first.state.value.draft.draftId).revision

        val second = newVm()
        second.initialize(SetupWizardMode.RESUME)
        settle(second)
        assertEquals("se retoma la misma fila", first.state.value.draft.draftId, second.state.value.draft.draftId)
        assertTrue("sin retroceder revisiones", persistence.rows.getValue(second.state.value.draft.draftId).revision >= exitedRevision)
        hammer(second, random, rounds = 220)
        settle(second)
        assertHealthy("tras reanudar y seguir golpeando", second)
        assertTrue("hubo muchas escrituras: ${persistence.saveLog.size}", persistence.saveLog.size > 100)
    }

    /** Lo que debe cumplirse siempre: ni un guardado rechazado, ni el aviso de guardado, y la fila al día con la memoria. */
    private fun assertHealthy(moment: String, vm: SetupWizardViewModel) {
        assertTrue("$moment: el guard de revisión rechazó guardados: ${persistence.rejectedSaves}", persistence.rejectedSaves.isEmpty())
        val state = vm.state.value
        assertNull("$moment: aviso de guardado ${state.errors["save"]}", state.errors["save"])
        val row = persistence.rows.getValue(state.draft.draftId)
        assertEquals("$moment: la fila guardada y el borrador en memoria están en la misma revisión", state.draft.revision.toLong(), row.revision)
        assertEquals(
            "$moment: la fila guardada es el borrador de memoria",
            state.draft.copy(stepProgress = state.draft.stepProgress.copy(stalePreviews = emptySet())),
            json.decodeFromString<SetupWizardDraft>(row.payloadJson).let { saved ->
                saved.copy(stepProgress = saved.stepProgress.copy(stalePreviews = emptySet()))
            },
        )
    }

    /**
     * [rounds] ráfagas de 1 a 5 escrituras seguidas, sin esperar a que terminen, con el reloj virtual avanzando a saltos
     * de unos milisegundos entre ráfagas y, de vez en cuando, dejando respirar a la cola. En la ráfaga [exitAt] pide «Guardar
     * y salir» sin esperar.
     */
    private fun TestScope.hammer(vm: SetupWizardViewModel, random: Random, rounds: Int, exitAt: Int = -1) {
        repeat(rounds) { round ->
            repeat(1 + random.nextInt(5)) { tap(vm, random) }
            if (round == exitAt) {
                launch {
                    vm.requestExit()
                    vm.saveAndExit()
                }
            }
            advanceTimeBy(1L + random.nextInt(25))
            if (random.nextInt(6) == 0) advanceUntilIdle()
            if (random.nextInt(9) == 0) Thread.sleep(2)
        }
    }

    private fun tap(vm: SetupWizardViewModel, random: Random) {
        val state = vm.state.value
        val generatedId = GeneratedPlans.entryIdFor(state.draft.goalProfile ?: TrainingGoalProfile.STRENGTH_MUSCLE)
        when (random.nextInt(26)) {
            0 -> vm.togglePlace(TrainingPlace.entries.random(random))
            1 -> vm.toggleEquipmentSymbol(EquipmentSymbolId.entries.random(random))
            2 -> vm.setGoalProfile(TrainingGoalProfile.entries.random(random))
            3 -> vm.setFreshDay(1 + random.nextInt(7))
            4, 5 -> vm.toggleWeekday(1 + random.nextInt(7))
            6 -> vm.setWeekStart(1 + random.nextInt(7))
            7 -> vm.setDayPlace(1 + random.nextInt(7), TrainingPlace.entries.random(random))
            8, 9 -> vm.setSessionMinutes(20 + random.nextInt(161))
            10 -> vm.setCapability(CapabilitySkill.entries.random(random), CapabilityLevel.entries.random(random))
            11 -> vm.toggleMuscle(MuscleSymbol.entries.random(random))
            12 -> vm.setLiftMark(LiftMark.entries.random(random), 40.0 + random.nextInt(160))
            13, 14 -> vm.selectPlan(generatedId)
            15 -> vm.anotherPlanVersion()
            16 -> if (random.nextInt(4) == 0) vm.deferProgramUntilLater() else vm.setProgramRoute(SetupProgramRoute.CUSTOMIZABLE)
            17, 18 -> state.weekLayout?.let { layout ->
                layout.sessions.randomOrNull(random)?.let { session -> vm.moveSession(session.id, 1 + random.nextInt(7)) }
            }
            19 -> vm.resetWeekLayout()
            20, 21 -> vm.submitCurrentStep()
            22 -> vm.goBack()
            23 -> SetupStepGraph.stepIds(state.draft.stepContext()).randomOrNull(random)?.let { vm.editStep(it) }
            24 -> vm.setStepChoice(SetupStepId.EXPERIENCE, listOf("new", "returning", "intermediate", "advanced").random(random))
            else -> vm.update { draft -> draft.copy(name = "Ana ${random.nextInt(100)}") }
        }
    }

    /** Deja que la cola, los barridos y las vistas previas terminen (los hilos de fondo no obedecen al reloj virtual). */
    private fun TestScope.settle(vm: SetupWizardViewModel) {
        val deadline = System.currentTimeMillis() + 60_000
        var calm = 0
        while (calm < 4) {
            advanceUntilIdle()
            val state = vm.state.value
            val busy = state.isLoading || state.isCommitting || state.isSubmittingAnswer || state.isSavingAndExiting ||
                state.isPreviewLoading || state.isCandidateLoading ||
                state.machineState == WizChatMachineState.PersistingAnswer || state.machineState == WizChatMachineState.PreparingPreview
            calm = if (busy) 0 else calm + 1
            check(System.currentTimeMillis() < deadline) { "la cola no se vació: ${state.machineState}" }
            Thread.sleep(15)
        }
    }

    /** Entradas completas de candidato (como las de las otras pruebas del ViewModel), escritas de una vez. */
    private fun SetupWizardDraft.withEntrenoInputs(): SetupWizardDraft =
        copy(
            includeTraining = true,
            programRoute = SetupProgramRoute.CUSTOMIZABLE,
            trainingPath = SetupTrainingPath.PERSONALIZE,
            experience = SetupExperience.INTERMEDIATE,
        )
            .withPlaces(setOf(TrainingPlace.GYM, TrainingPlace.HOME))
            .withGoalProfile(TrainingGoalProfile.STRENGTH_MUSCLE)
            .withFreshestDay(1)
            .withWeekdays(setOf(1, 3, 5))
            .withSessionMinutes(60)

    private fun cannedProgram(id: String): Program {
        val exercise = Exercise(
            id = "$id-ex",
            name = "Press de banca",
            sets = (1..3).map { ExerciseSet(id = "$id-set-$it", targetReps = 8, weight = 20.0) },
            restTime = 90,
        )
        val sessions = listOf(1, 3, 5).map { day ->
            Session(id = "$id-s$day", name = "Día $day", exercises = listOf(exercise.copy(id = "$id-ex$day")), dayOfWeek = day, assignedDays = listOf(day))
        }
        val week = ProgramWeek("$id-week", "Semana", sessions = sessions)
        val meso = Mesocycle("$id-meso", "Meso", weeks = listOf(week))
        val block = Block("$id-block", "Bloque", mesocycles = listOf(meso))
        return Program(
            id = id,
            name = "Plan preparado",
            startDay = 1,
            macrocycles = listOf(Macrocycle("$id-macro", "Macro", blocks = listOf(block))),
            schedulePlan = ProgramSchedulePlan(weekStartDay = 1, trainingDays = setOf(1, 3, 5)),
        )
    }
}

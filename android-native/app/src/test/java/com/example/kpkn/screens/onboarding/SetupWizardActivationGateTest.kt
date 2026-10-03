package com.example.kpkn.screens.onboarding

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.ApparatusPresence
import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.models.EquipmentCategory
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramSchedulePlan
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.onboarding.SetupCommitRequest
import com.example.kpkn.data.onboarding.SetupCommitResult
import com.example.kpkn.data.onboarding.SetupDraft
import com.example.kpkn.data.onboarding.SetupDraftCandidate
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.PlanRejectionReason
import com.example.kpkn.domain.onboarding.SetupTrainingOptions
import com.example.kpkn.domain.onboarding.WizChatMachineState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * T-005 / AC-T005-05 — puerta de activación §15.4:
 *
 * - una edición posterior al preview deja la activación BLOQUEADA hasta que el
 *   preview se re-prepare con la huella actual (`lastSuccessfulTrainingKey ==
 *   previewKey(draft)`), con el motivo en `errors["program"]`;
 * - el commit usa el MISMO program/commit id, se publica solo con receipt y el
 *   reintento no duplica operaciones;
 * - una respuesta editada NUNCA se activa con el resultado de otras entradas.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class SetupWizardActivationGateTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class RecordingCommits : SetupWizardCommits {
        var invocations = 0
        override suspend fun commit(request: SetupCommitRequest): SetupCommitResult {
            invocations++
            return SetupCommitResult(request.commitId, null, null, emptyList())
        }
    }

    /** Persistencia en memoria: sin Dispatchers.IO real que el scheduler no drena. */
    private class InMemoryPersistence : SetupWizardPersistence {
        val store = mutableMapOf<String, SetupDraft>()
        override suspend fun load(draftId: String): SetupDraft? = store[draftId]
        override suspend fun save(draftId: String, payloadJson: String, revision: Long, catalogRevision: String?): SetupDraft =
            SetupDraft(draftId, payloadJson, revision, catalogRevision, System.currentTimeMillis())
                .also { store[it.draftId] = it }
        override suspend fun discard(draftId: String) {
            store.remove(draftId)
        }
        override suspend fun listRecoverable(): List<SetupDraftCandidate> = emptyList()
    }

    private class FixedSettingsEnvironment(override val settings: Settings) : SetupWizardEnvironment {
        override suspend fun awaitReady() = Unit
        override fun activeProgramId(): String? = null
        override fun activeNutritionPlanId(): String? = null
        override fun activeNutritionPlan(): com.example.kpkn.data.models.NutritionPlan? = null
        override fun nutritionPlan(id: String): com.example.kpkn.data.models.NutritionPlan? = null
        override fun hasInitialRecoveryEvidence(): Boolean = false
        override suspend fun refreshBodyProgress() = Unit
    }

    private fun cannedProgram(id: String): Program {
        val exercise = Exercise(
            id = "$id-ex",
            name = "Press de banca",
            sets = (1..3).map { ExerciseSet(id = "$id-set-$it", targetReps = 8, weight = 20.0) },
            restTime = 90,
        )
        val session = Session(
            id = "$id-session",
            name = "Día 1",
            exercises = listOf(exercise),
            dayOfWeek = 1,
            assignedDays = listOf(1),
        )
        val week = ProgramWeek("$id-week", "Semana", sessions = listOf(session))
        val mesocycle = Mesocycle("$id-meso", "Meso", weeks = listOf(week))
        val block = Block("$id-block", "Bloque", mesocycles = listOf(mesocycle))
        return Program(
            id = id,
            name = "Plan preparado",
            startDay = 1,
            weekDays = 1,
            macrocycles = listOf(Macrocycle("$id-macro", "Macro", blocks = listOf(block))),
            schedulePlan = ProgramSchedulePlan(weekStartDay = 1, trainingDays = setOf(1)),
        )
    }

    private fun kotlinx.coroutines.test.TestScope.awaitWall(
        what: String = "condición",
        budgetMs: Long = 20_000L,
        condition: () -> Boolean,
    ) {
        val start = System.nanoTime()
        while (!condition() && (System.nanoTime() - start) < budgetMs * 1_000_000L) {
            advanceUntilIdle()
            Thread.sleep(20)
        }
        advanceUntilIdle()
        assertTrue("$what no se alcanzó en ${budgetMs}ms", condition())
    }

    private fun kotlinx.coroutines.test.TestScope.confirm(
        vm: SetupWizardViewModel,
        step: SetupStepId,
        expectedNext: SetupStepId,
        write: () -> Unit = {},
    ) {
        write()
        awaitWall("reposo tras escribir $step") { !vm.state.value.isSubmittingAnswer && !vm.state.value.isSavingAndExiting }
        val revision = vm.state.value.draft.revision
        val result = vm.submitCurrentStep(step, expectedRevision = revision)
        assertEquals("Continuar sobre $step (errores=${vm.state.value.errors})", SetupSubmitOutcome.ACCEPTED, result.outcome)
        awaitWall("cursor en $expectedNext tras $step") {
            vm.state.value.draft.stepProgress.currentStepId == expectedNext
        }
    }

    /** Recorrido completo hasta la revisión con el motor enmascarado. */
    private fun kotlinx.coroutines.test.TestScope.walkToReview(
        vm: SetupWizardViewModel,
        materializationCalls: AtomicInteger? = null,
    ) {
        confirm(vm, SetupStepId.NAME, SetupStepId.AGE) { vm.setStepText(SetupStepId.NAME, "Ana") }
        confirm(vm, SetupStepId.AGE, SetupStepId.HEIGHT) { vm.setStepNumber(SetupStepId.AGE, 30.0) }
        confirm(vm, SetupStepId.HEIGHT, SetupStepId.WEIGHT) { vm.setStepNumber(SetupStepId.HEIGHT, 175.0) }
        confirm(vm, SetupStepId.WEIGHT, SetupStepId.EQUATION_SEX) { vm.setStepNumber(SetupStepId.WEIGHT, 72.0) }
        confirm(vm, SetupStepId.EQUATION_SEX, SetupStepId.BODY_FAT) { vm.setStepChoice(SetupStepId.EQUATION_SEX, "unknown") }
        confirm(vm, SetupStepId.BODY_FAT, SetupStepId.MILESTONE_BASICS) { vm.setStepChoice(SetupStepId.BODY_FAT, "unknown") }
        confirm(vm, SetupStepId.MILESTONE_BASICS, SetupStepId.EXPERIENCE)
        // §15.1: material antes de perfiles.
        confirm(vm, SetupStepId.EXPERIENCE, SetupStepId.EQUIPMENT) { vm.setStepChoice(SetupStepId.EXPERIENCE, "intermediate") }
        confirm(vm, SetupStepId.EQUIPMENT, SetupStepId.AVAILABILITY) { vm.setStepChoice(SetupStepId.EQUIPMENT, "gym") }
        confirm(vm, SetupStepId.AVAILABILITY, SetupStepId.GOAL)
        confirm(vm, SetupStepId.GOAL, SetupStepId.DAYS) { vm.setStepChoice(SetupStepId.GOAL, "muscle") }
        confirm(vm, SetupStepId.DAYS, SetupStepId.WEEKDAYS) { vm.setStepChoice(SetupStepId.DAYS, "3") }
        confirm(vm, SetupStepId.WEEKDAYS, SetupStepId.SESSION_TIME) { vm.setStepChoices(SetupStepId.WEEKDAYS, setOf("1", "3", "5")) }
        confirm(vm, SetupStepId.SESSION_TIME, SetupStepId.VOLUME_TECHNIQUE) {
            vm.setStepText(SetupStepId.SESSION_TIME, "60")
            vm.setStepNumber(SetupStepId.SESSION_TIME, 60.0)
        }
        confirm(vm, SetupStepId.VOLUME_TECHNIQUE, SetupStepId.VOLUME_CONSISTENCY) { vm.setStepChoice(SetupStepId.VOLUME_TECHNIQUE, "2") }
        confirm(vm, SetupStepId.VOLUME_CONSISTENCY, SetupStepId.VOLUME_STRENGTH) { vm.setStepChoice(SetupStepId.VOLUME_CONSISTENCY, "2") }
        confirm(vm, SetupStepId.VOLUME_STRENGTH, SetupStepId.VOLUME_MOBILITY) { vm.setStepChoice(SetupStepId.VOLUME_STRENGTH, "2") }
        confirm(vm, SetupStepId.VOLUME_MOBILITY, SetupStepId.PRIORITIES) { vm.setStepChoice(SetupStepId.VOLUME_MOBILITY, "2") }
        confirm(vm, SetupStepId.PRIORITIES, SetupStepId.TRAINING_MAX)
        confirm(vm, SetupStepId.TRAINING_MAX, SetupStepId.SPLIT) { vm.setStepChoice(SetupStepId.TRAINING_MAX, "no") }
        confirm(vm, SetupStepId.SPLIT, SetupStepId.PLAN) { vm.setStepChoice(SetupStepId.SPLIT, "recommended") }
        awaitWall("candidatos reales") {
            !vm.state.value.isCandidateLoading && vm.state.value.availablePlanCandidates.isNotEmpty()
        }
        val candidate = vm.state.value.availablePlanCandidates.first()
        val callsBeforeSelection = materializationCalls?.get()
        confirm(vm, SetupStepId.PLAN, SetupStepId.AUTOREGULATION) { vm.selectPlan(candidate.id) }
        if (callsBeforeSelection != null) {
            awaitWall("preview reutilizado desde Ready") {
                !vm.state.value.isCandidateLoading && !vm.state.value.isPreviewLoading &&
                    vm.state.value.programPreview != null
            }
            assertEquals(
                "el preview reutiliza el programa Ready del barrido de candidatos",
                callsBeforeSelection,
                materializationCalls.get(),
            )
        }
        confirm(vm, SetupStepId.AUTOREGULATION, SetupStepId.WARMUPS)
        confirm(vm, SetupStepId.WARMUPS, SetupStepId.TRAINING_REVIEW)
        confirm(vm, SetupStepId.TRAINING_REVIEW, SetupStepId.MILESTONE_TRAINING)
        confirm(vm, SetupStepId.MILESTONE_TRAINING, SetupStepId.RINGS_RECENT)
        // Sin datos declarados de Rings no hay check-in real que exiger.
        confirm(vm, SetupStepId.RINGS_RECENT, SetupStepId.RINGS_MUSCLE_FEELING) { vm.setStepChoice(SetupStepId.RINGS_RECENT, "unknown") }
        vm.skipStep(SetupStepId.RINGS_MUSCLE_FEELING)
        confirm(vm, SetupStepId.RINGS_MUSCLE_FEELING, SetupStepId.RINGS_ENERGY_FEELING)
        vm.skipStep(SetupStepId.RINGS_ENERGY_FEELING)
        confirm(vm, SetupStepId.RINGS_ENERGY_FEELING, SetupStepId.RINGS_STRUCTURE_FEELING)
        vm.skipStep(SetupStepId.RINGS_STRUCTURE_FEELING)
        confirm(vm, SetupStepId.RINGS_STRUCTURE_FEELING, SetupStepId.RINGS_DISCOMFORT)
        vm.skipStep(SetupStepId.RINGS_DISCOMFORT)
        confirm(vm, SetupStepId.RINGS_DISCOMFORT, SetupStepId.RINGS_RESULT)
        confirm(vm, SetupStepId.RINGS_RESULT, SetupStepId.MILESTONE_RINGS)
        confirm(vm, SetupStepId.MILESTONE_RINGS, SetupStepId.REVIEW_ACTIVATE)
    }

    @Test
    fun activationGateBlocksStalePreviewAndRetryKeepsTheSameProgramId() = runTest(dispatcher.scheduler) {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val commits = RecordingCommits()
        val gate = CompletableDeferred<Unit>()
        val blocked = AtomicBoolean(false)
        val materializationCalls = AtomicInteger()
        val override = SetupWizardMaterializer { draft ->
            materializationCalls.incrementAndGet()
            if (blocked.get()) gate.await()
            SetupPreview(cannedProgram(draft.commitId), null)
        }
        val vm = SetupWizardViewModel(
            application,
            SavedStateHandle(),
            persistence = InMemoryPersistence(),
            environment = FixedSettingsEnvironment(Settings()),
            commits = commits,
            materializeOverride = override,
        )
        vm.initialize(SetupWizardMode.FULL)
        awaitWall("wizard cargado") { vm.state.value.draft.draftId.isNotBlank() && !vm.state.value.isLoading }
        // Sin nutrición: el bloque no entra en la ruta de ESTE recorrido.
        vm.setModuleChoice(SetupModuleChoice.TRAINING)
        awaitWall("scope solo entrenamiento") { !vm.state.value.draft.includeNutrition }

        walkToReview(vm, materializationCalls)

        assertFalse(
            "sin datos de Rings no se exige check-in en este recorrido",
            vm.ringsPreview().savesRealCheckIn,
        )
        awaitWall("preview preparado") {
            !vm.state.value.isPreviewLoading && vm.state.value.programPreview != null
        }
        assertEquals(
            "el programa preparado conserva el id del draft (asignado una sola vez)",
            vm.state.value.draft.commitId,
            checkNotNull(vm.state.value.programPreview).id,
        )
        assertFalse("preview vigente antes de editar", vm.state.value.selectionStale)

        // ── Editar una respuesta invalida el preview: activación bloqueada ──
        blocked.set(true)
        vm.editStep(SetupStepId.SESSION_TIME)
        awaitWall("cursor en SESSION_TIME") { vm.state.value.draft.stepProgress.currentStepId == SetupStepId.SESSION_TIME }
        vm.setStepNumber(SetupStepId.SESSION_TIME, 61.0)
        awaitWall("minutos escritos") { vm.state.value.draft.minutesPerSession == 61 }
        confirm(vm, SetupStepId.SESSION_TIME, SetupStepId.REVIEW_ACTIVATE)
        assertTrue("la edición marca el preview obsoleto", vm.state.value.selectionStale)
        // El recálculo se lanza YA (candidatos y/o preview); con la puerta
        // armada, ambos trabajos quedan suspendidos esperando la liberación.
        awaitWall("recálculo lanzado") {
            vm.state.value.isCandidateLoading || vm.state.value.isPreviewLoading
        }

        val blockedReceipt = vm.commit()
        assertNull("no se activa un resultado de otras entradas", blockedReceipt)
        assertNotNull(
            "la puerta explica que falta re-preparar la vista previa: ${vm.state.value.errors}",
            vm.state.value.errors["program"],
        )
        assertFalse(
            "el bloqueo es de huella, no del flujo de pasos",
            vm.state.value.errors.containsKey("flow"),
        )
        assertEquals("el commit fallido no invoca el coordinador", 0, commits.invocations)

        // ── Re-preparar y activar; reintento con el MISMO id, sin duplicar ──
        blocked.set(false)
        gate.complete(Unit)
        awaitWall("preview re-preparado") {
            !vm.state.value.isPreviewLoading && vm.state.value.programPreview != null &&
                !vm.state.value.selectionStale
        }

        val receipt = vm.commit()
        assertNotNull("activación con la huella vigente", receipt)
        assertEquals(WizChatMachineState.Committed, vm.state.value.machineState)
        assertEquals("un solo commit real", 1, commits.invocations)
        assertEquals(
            "el receipt es el id único del programa",
            vm.state.value.draft.commitId,
            receipt,
        )
        assertEquals(
            "el reintento devuelve el mismo receipt sin duplicar",
            receipt,
            vm.commit(),
        )
        assertEquals("el reintento no vuelve a invocar el coordinador", 1, commits.invocations)
    }

    @Test
    fun apparatusRejectionReportsDefinitiveAbsenceWhenOtherRequirementsAreUnknown() = runTest(dispatcher.scheduler) {
        val vm = SetupWizardViewModel(
            ApplicationProvider.getApplicationContext(),
            SavedStateHandle(),
            persistence = InMemoryPersistence(),
            environment = FixedSettingsEnvironment(Settings()),
        )
        val store = ViewModelStore().also { it.put("apparatus-reason", vm) }
        try {
            val availability = EquipmentAvailability(
                categories = setOf(EquipmentCategory.MACHINES, EquipmentCategory.SUPPORT),
                supports = mapOf("bench_flat" to ApparatusPresence.ABSENT),
            )
            val draft = SetupWizardDraft().copy(
                trainingOptions = SetupTrainingOptions(availability = availability),
            )

            assertEquals(
                PlanRejectionReason.APPARATUS_ABSENT,
                vm.apparatusReason("Esta receta necesita material: bench, machine", draft),
            )
            assertEquals(
                PlanRejectionReason.APPARATUS_UNKNOWN,
                vm.apparatusReason("Esta receta necesita material: machine", draft),
            )
        } finally {
            store.clear()
        }
    }
}

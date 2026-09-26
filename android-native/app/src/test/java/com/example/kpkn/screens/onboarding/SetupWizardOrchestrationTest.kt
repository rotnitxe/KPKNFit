package com.example.kpkn.screens.onboarding

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.UserVitals
import com.example.kpkn.data.onboarding.SetupDraft
import com.example.kpkn.data.onboarding.SetupDraftCandidate
import com.example.kpkn.data.onboarding.SetupDraftScope
import com.example.kpkn.domain.onboarding.SetupAnswerProvenance
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.WizChatMachineState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Orchestration of the setup wizard against an in-memory persistence fake that
 * replicates the Room revision guard (SetupPersistence.save @126): a write with
 * a stale revision or with the same revision but a different payload throws, so
 * the essay proves the wizard never trips that guard.
 *
 * The environment fake keeps the tests hermetic: no ProgramRepository /
 * NutritionRepository singletons, no initialization of dispatchers, no catalog
 * assets. Steps are the authority of the cursor; every save bumps the row
 * revision monotonically (withNextDraftRevision in the ViewModel).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class SetupWizardOrchestrationTest {

    private val dispatcher = StandardTestDispatcher()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private lateinit var persistence: FakeSetupWizardPersistence
    private lateinit var app: Application
    private lateinit var handle: SavedStateHandle

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        app = ApplicationProvider.getApplicationContext()
        persistence = FakeSetupWizardPersistence()
        handle = SavedStateHandle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm(environment: SetupWizardEnvironment = FakeSetupWizardEnvironment()) =
        SetupWizardViewModel(app, handle, persistence, environment)

    // --- name → Continuar → Atrás → save/resume ------------------------------

    @Test
    fun nameNextBackKeepsRevisionMonotonic() = runTest {
        val vm = vm()
        vm.initialize(SetupWizardMode.FULL)
        advanceUntilIdle()
        assertEquals(SetupStepId.NAME, vm.state.value.currentStep)

        vm.setName("Ana")
        advanceUntilIdle()
        vm.submitCurrentStep(SetupStepId.NAME)
        advanceUntilIdle()
        assertEquals(SetupStepId.AGE, vm.state.value.currentStep)

        assertTrue(vm.goBack())
        advanceUntilIdle()
        assertEquals(SetupStepId.NAME, vm.state.value.currentStep)

        // Cada escritura avanza la revisión de forma estrictamente creciente;
        // el guard de Room (revisión antigua o igual + payload distinto) nunca
        // puede dispararse en el flujo nombre → Continuar → Atrás.
        val revisions = persistence.saveLog.map { it.revision }
        assertTrue("revisions=$revisions", revisions.size >= 3)
        assertTrue(revisions.zipWithNext().all { (a, b) -> a < b })

        // Continuar de nuevo sobre la misma revisión: sin conflicto.
        assertEquals(SetupSubmitOutcome.ACCEPTED, vm.submitCurrentStep(SetupStepId.NAME).outcome)
        advanceUntilIdle()
        assertEquals(SetupStepId.AGE, vm.state.value.currentStep)
        assertEquals(SetupAnswerProvenance.USER_DECLARED, vm.state.value.draft.stepProgress.answers[SetupStepId.NAME])
    }

    @Test
    fun backPersistsTheRevisionBump() = runTest {
        val vm = vm()
        vm.initialize(SetupWizardMode.FULL)
        advanceUntilIdle()
        vm.setName("Ana")
        advanceUntilIdle()

        val before = persistence.rows.getValue(vm.state.value.draft.draftId).revision
        vm.submitCurrentStep(SetupStepId.NAME)
        advanceUntilIdle()
        val afterContinue = persistence.rows.getValue(vm.state.value.draft.draftId).revision
        assertTrue("afterContinue=$afterContinue must beat before=$before", afterContinue > before)

        assertTrue(vm.goBack())
        advanceUntilIdle()
        val afterBack = persistence.rows.getValue(vm.state.value.draft.draftId).revision
        assertTrue("afterBack=$afterBack must beat afterContinue=$afterContinue", afterBack > afterContinue)
        assertEquals(SetupStepId.NAME, vm.state.value.currentStep)
    }

    @Test
    fun twoRapidSubmitsAdvanceExactlyOnce() = runTest {
        val vm = vm()
        vm.initialize(SetupWizardMode.FULL)
        advanceUntilIdle()
        vm.setName("Ana")
        advanceUntilIdle()

        val first = vm.submitCurrentStep(SetupStepId.NAME)
        val second = vm.submitCurrentStep(SetupStepId.NAME)
        assertEquals(SetupSubmitOutcome.ACCEPTED, first.outcome)
        // The synchronous gate drops the repeated callback while the first
        // confirmation is in flight: exactly one advance below.
        assertEquals(SetupSubmitOutcome.DROPPED, second.outcome)
        advanceUntilIdle()
        assertEquals(SetupStepId.AGE, vm.state.value.currentStep)
    }

    @Test
    fun emptyNameIsRejectedInlineWithoutAdvancing() = runTest {
        val vm = vm()
        vm.initialize(SetupWizardMode.FULL)
        advanceUntilIdle()

        val result = vm.submitCurrentStep(SetupStepId.NAME)
        assertEquals(SetupSubmitOutcome.REJECTED, result.outcome)
        assertTrue(vm.state.value.errors.isNotEmpty())
        assertEquals(SetupStepId.NAME, vm.state.value.currentStep)
    }

    @Test
    fun retryAfterSaveFailureRetainsText() = runTest {
        val vm = vm()
        vm.initialize(SetupWizardMode.FULL)
        advanceUntilIdle()

        persistence.failNextSaves = 1
        vm.setName("Ana")
        advanceUntilIdle()

        // El fallo de guardado es inline y no pierde la respuesta en memoria.
        assertEquals("Ana", vm.state.value.draft.name)
        assertEquals(WizChatMachineState.AwaitingAnswer, vm.state.value.machineState)
        assertTrue(vm.state.value.errors.isNotEmpty())
        assertNull(persistence.rows[vm.state.value.draft.draftId])

        // Reintentar persiste el mismo borrador sin perder nada.
        vm.retryFailedOperation(SetupRetryOperation.SAVE)
        advanceUntilIdle()
        assertTrue(vm.state.value.errors.isEmpty())
        val row = persistence.rows[vm.state.value.draft.draftId]
            // `fail` devuelve Unit: sin `throw` el elvis tiparía `row` como Any.
            ?: throw AssertionError("no row after retry")
        assertEquals("Ana", json.decodeFromString<SetupWizardDraft>(row.payloadJson).name)
    }

    // --- salida: solo navega cuando realmente se guardó -----------------------

    @Test
    fun saveAndExitFailureDoesNotCompleteExit() = runTest {
        val vm = vm()
        vm.initialize(SetupWizardMode.FULL)
        advanceUntilIdle()
        vm.setName("Ana")
        advanceUntilIdle()

        vm.requestExit()
        assertEquals(SetupWizardDialog.EXIT, vm.state.value.dialog)

        persistence.failNextSaves = 1
        val saved = vm.saveAndExit()
        assertFalse(saved)
        assertFalse(vm.state.value.exitCompleted)
        assertEquals(SetupWizardDialog.EXIT, vm.state.value.dialog)
        assertTrue(vm.state.value.errors.isNotEmpty())
        assertFalse(vm.state.value.isSavingAndExiting)

        // El reintento directo desde el diálogo sí completa la salida.
        val second = vm.saveAndExit()
        assertTrue(second)
        assertTrue(vm.state.value.exitCompleted)
    }

    // --- resume: exact step restore y Continuar sin conflicto ------------------

    @Test
    fun resumePersistedDraftRestoresStepAndContinuarDoesNotConflict() = runTest {
        val first = vm()
        first.initialize(SetupWizardMode.FULL)
        advanceUntilIdle()
        first.setName("Ana")
        advanceUntilIdle()
        first.submitCurrentStep(SetupStepId.NAME)
        advanceUntilIdle()
        assertEquals(SetupStepId.AGE, first.state.value.currentStep)
        first.goBack()
        advanceUntilIdle()
        assertEquals(SetupStepId.NAME, first.state.value.currentStep)

        // Segunda sesión: se retoma EXACTAMENTE el paso guardado, nombre incluido.
        val second = vm()
        second.initialize(SetupWizardMode.RESUME)
        advanceUntilIdle()
        assertEquals(SetupWizardMode.FULL, second.state.value.mode)
        assertEquals(SetupStepId.NAME, second.state.value.currentStep)
        assertEquals("Ana", second.state.value.draft.name)
        assertEquals(SetupAnswerProvenance.USER_DECLARED, second.state.value.draft.stepProgress.answers[SetupStepId.NAME])

        // Continuar sobre el borrador retomado jamás choca con la revisión
        // guardada (regresión del emulador del jefe: "Conflicto de revisión").
        val retainedRevision = persistence.rows.getValue(second.state.value.draft.draftId).revision
        assertEquals(SetupSubmitOutcome.ACCEPTED, second.submitCurrentStep(SetupStepId.NAME).outcome)
        advanceUntilIdle()
        assertEquals(SetupStepId.AGE, second.state.value.currentStep)
        val after = persistence.rows.getValue(second.state.value.draft.draftId).revision
        assertTrue("after=$after must beat retained=$retainedRevision", after > retainedRevision)
    }

    // --- procedencia: prefill sin tocar vs. valor tocado por el usuario ---------

    @Test
    fun untouchedPrefillIsEstimatedAndTouchedIsDeclared() = runTest {
        val environment = FakeSetupWizardEnvironment(
            settings = Settings(username = "Usuario", userVitals = UserVitals(weight = 72.0)),
        )
        val vm = vm(environment)
        vm.initialize(SetupWizardMode.FULL)
        advanceUntilIdle()

        vm.setName("Ana")
        advanceUntilIdle()
        vm.submitCurrentStep(SetupStepId.NAME)
        advanceUntilIdle()
        vm.setAge(30)
        advanceUntilIdle()
        vm.submitCurrentStep(SetupStepId.AGE)
        advanceUntilIdle()
        vm.setHeightCm(175.0)
        advanceUntilIdle()
        vm.submitCurrentStep(SetupStepId.HEIGHT)
        advanceUntilIdle()
        assertEquals(SetupStepId.WEIGHT, vm.state.value.currentStep)
        assertEquals(72.0, vm.state.value.draft.weightKg!!, 0.001)

        // Peso venido de ajustes y nunca tocado: ESTIMATED (SUGGESTED).
        vm.submitCurrentStep(SetupStepId.WEIGHT)
        advanceUntilIdle()
        assertEquals(
            SetupAnswerProvenance.SUGGESTED,
            vm.state.value.draft.stepProgress.answers[SetupStepId.WEIGHT],
        )

        // Vuelta al paso y escritura manual: DECLARED (USER_DECLARED).
        assertTrue(vm.goBack())
        advanceUntilIdle()
        assertEquals(SetupStepId.WEIGHT, vm.state.value.currentStep)
        vm.setWeightKg(73.5)
        advanceUntilIdle()
        vm.submitCurrentStep(SetupStepId.WEIGHT)
        advanceUntilIdle()
        assertEquals(
            SetupAnswerProvenance.USER_DECLARED,
            vm.state.value.draft.stepProgress.answers[SetupStepId.WEIGHT],
        )
        assertTrue(SetupStepId.WEIGHT in vm.state.value.draft.declaredSteps)
    }

    // --- edición desde la revisión sobrevive a guardar/salir y al recreado -----

    @Test
    fun reviewEditSurvivesSaveExitAndResumeThenReturnsToReview() = runTest {
        val first = vm()
        first.initialize(SetupWizardMode.FULL)
        advanceUntilIdle()
        first.setName("Ana")
        advanceUntilIdle()

        // Fixture: la fila persistida queda en la revisión final (evita
        // recorrer toda la ruta solo para llegar a ella). La revisión del
        // payload y la de la fila suben juntas para no pisar el guard de Room.
        val id = first.state.value.draft.draftId
        val row = persistence.rows.getValue(id)
        val restored = json.decodeFromString<SetupWizardDraft>(row.payloadJson)
        // `SetupWizardDraft.revision` es Int (DTO del payload); la fila usa Long.
        val nextRevision = restored.revision + 1
        val atReview = restored.copy(
            stepProgress = restored.stepProgress.at(SetupStepId.REVIEW_ACTIVATE, restored.stepContext()),
            revision = nextRevision,
        )
        persistence.rows[id] = FakeSetupWizardPersistence.Row(
            payloadJson = json.encodeToString(atReview),
            revision = nextRevision.toLong(),
            catalogRevision = row.catalogRevision,
        )

        // Sesión 1: abrir la revisión, editar un paso y salir guardando.
        val second = vm()
        second.initialize(SetupWizardMode.RESUME)
        advanceUntilIdle()
        assertEquals(SetupWizardMode.FULL, second.state.value.mode)
        assertEquals(SetupStepId.REVIEW_ACTIVATE, second.state.value.currentStep)

        second.editStep(SetupStepId.NAME)
        advanceUntilIdle()
        assertEquals(SetupStepId.NAME, second.state.value.currentStep)
        // La intención de volver vive en el BORRADOR, no en memoria volátil…
        assertEquals(SetupStepId.REVIEW_ACTIVATE, second.state.value.draft.reviewReturnStep)

        second.requestExit()
        assertTrue(second.saveAndExit())
        advanceUntilIdle()
        // …y queda escrita en la fila que se persistió al salir.
        assertEquals(
            SetupStepId.REVIEW_ACTIVATE,
            json.decodeFromString<SetupWizardDraft>(persistence.rows.getValue(id).payloadJson)
                .reviewReturnStep,
        )

        // Sesión 2 (ViewModel nuevo): se retoma el paso editado y al
        // confirmarlo se vuelve a la revisión SIN recontestar el formulario.
        val third = vm()
        third.initialize(SetupWizardMode.RESUME)
        advanceUntilIdle()
        assertEquals(SetupStepId.NAME, third.state.value.currentStep)
        assertEquals(SetupStepId.REVIEW_ACTIVATE, third.state.value.draft.reviewReturnStep)

        assertEquals(SetupSubmitOutcome.ACCEPTED, third.submitCurrentStep(SetupStepId.NAME).outcome)
        advanceUntilIdle()
        assertEquals(SetupStepId.REVIEW_ACTIVATE, third.state.value.currentStep)
        // La intención se consume en el borrador confirmado (y persistido).
        assertNull(third.state.value.draft.reviewReturnStep)
        // Solo el paso editado se reconfirmó: nadie recontestó nada más.
        assertEquals(setOf(SetupStepId.NAME), third.state.value.draft.stepProgress.answers.keys)
    }

    // --- fakes ----------------------------------------------------------------

    /** Persistence fake replicando el guard de Room (SetupPersistence @122-134). */
    private class FakeSetupWizardPersistence : SetupWizardPersistence {
        data class Row(val payloadJson: String, val revision: Long, val catalogRevision: String?) {
            fun toDraft(draftId: String) = SetupDraft(draftId, payloadJson, revision, catalogRevision, 0L)
        }

        val rows = LinkedHashMap<String, Row>()
        val saveLog = mutableListOf<SetupDraft>()
        var failNextSaves = 0

        override suspend fun load(draftId: String): SetupDraft? = rows[draftId]?.toDraft(draftId)

        override suspend fun save(draftId: String, payloadJson: String, revision: Long, catalogRevision: String?): SetupDraft {
            if (failNextSaves > 0) {
                failNextSaves--
                throw java.io.IOException("Fallo de guardado inyectado")
            }
            val current = rows[draftId]
            val model = SetupDraft(draftId, payloadJson, revision, catalogRevision, 0L)
            return when {
                current == null -> {
                    rows[draftId] = Row(payloadJson, revision, catalogRevision)
                    saveLog += model
                    model
                }
                revision < current.revision -> throw IllegalArgumentException("La revisión del borrador es antigua")
                revision == current.revision && payloadJson != current.payloadJson ->
                    throw IllegalArgumentException("Conflicto de revisión del borrador")
                revision == current.revision -> current.toDraft(draftId)
                else -> {
                    rows[draftId] = Row(payloadJson, revision, catalogRevision)
                    saveLog += model
                    model
                }
            }
        }

        override suspend fun discard(draftId: String) {
            rows.remove(draftId)
        }

        override suspend fun listRecoverable(): List<SetupDraftCandidate> =
            rows.map { (id, row) -> SetupDraftCandidate(id, SetupDraftScope.FULL, row.revision, 0L, row.catalogRevision) }
    }

    /** Environment hermético: sin singletons, sin dispatch, sin assets. */
    private class FakeSetupWizardEnvironment(
        override val settings: Settings = Settings(),
    ) : SetupWizardEnvironment {
        var bodyProgressRefreshes = 0
            private set

        override suspend fun awaitReady() = Unit
        override fun activeProgramId(): String? = null
        override fun activeNutritionPlanId(): String? = null
        override fun activeNutritionPlan(): NutritionPlan? = null
        override fun nutritionPlan(id: String): NutritionPlan? = null
        override fun hasInitialRecoveryEvidence(): Boolean = false
        override suspend fun refreshBodyProgress() {
            bodyProgressRefreshes++
        }
    }
}
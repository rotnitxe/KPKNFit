package com.example.kpkn.screens.onboarding

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.EquipmentInventory
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.onboarding.SetupCommitRequest
import com.example.kpkn.data.onboarding.SetupCommitResult
import com.example.kpkn.data.onboarding.SetupDraft
import com.example.kpkn.data.onboarding.SetupDraftCandidate
import com.example.kpkn.domain.onboarding.SetupAnswerProvenance
import com.example.kpkn.domain.onboarding.SetupStepDefinitions
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.WizChatMachineState
import com.example.kpkn.domain.training.TrainingOptions
import com.example.kpkn.domain.training.effectiveEquipment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.decodeFromString
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
 * Contrato de la API de pasos del wizard:
 *
 * - los setters solo escriben (mutar + tocar el paso + persistir con revisión
 *   monótona) y NUNCA avanzan el cursor; solo [SetupWizardViewModel.submitCurrentStep]
 *   confirma;
 * - `skipStep` es posible únicamente cuando la definición lo permite y guarda
 *   procedencia sin fabricar datos;
 * - editar no duplica confirmaciones ni borra respuestas;
 * - `selectPlan` solo selecciona;
 * - el commit falla con error honesto sin tocar el puerto cuando la revisión
 *   real no está completa.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class SetupWizardStepApiTest {

    private val dispatcher = StandardTestDispatcher()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private lateinit var persistence: FakePersistence
    private lateinit var commits: FakeCommits
    private lateinit var app: Application
    private lateinit var handle: SavedStateHandle

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        app = ApplicationProvider.getApplicationContext()
        persistence = FakePersistence()
        commits = FakeCommits()
        handle = SavedStateHandle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm() = SetupWizardViewModel(app, handle, persistence, FakeEnvironment(), commits)

    // --- setters: escriben, persisten y no avanzan ---------------------------

    @Test
    fun settersPersistWithoutAdvancingTheCursor() = runTest {
        val vm = vm()
        vm.initialize(SetupWizardMode.FULL)
        advanceUntilIdle()

        vm.setStepText(SetupStepId.NAME, "Ana")
        vm.setStepNumber(SetupStepId.AGE, 30.0)
        advanceUntilIdle()

        assertEquals("Ana", vm.state.value.draft.name)
        assertEquals(30, vm.state.value.draft.ageYears)
        // Ningún setter mueve el cursor ni confirma el paso.
        assertEquals(SetupStepId.NAME, vm.state.value.currentStep)
        assertTrue(vm.state.value.draft.stepProgress.answers.isEmpty())
        assertTrue(vm.state.value.errors.isEmpty())

        val revisions = persistence.saveLog.map { it.revision }
        assertTrue("revisions=$revisions", revisions.isNotEmpty())
        assertTrue(revisions.zipWithNext().all { (a, b) -> a < b })
    }

    @Test
    fun updateStepKeepsAcceptedAnswersUntouched() = runTest {
        val vm = vm()
        vm.initialize(SetupWizardMode.FULL)
        advanceUntilIdle()
        val answersBefore = vm.state.value.draft.wizChat.acceptedAnswers

        vm.updateStep(SetupStepId.NAME) { it.copy(name = "Berta") }
        advanceUntilIdle()

        assertEquals("Berta", vm.state.value.draft.name)
        assertEquals(answersBefore, vm.state.value.draft.wizChat.acceptedAnswers)
        assertEquals(SetupStepId.NAME, vm.state.value.currentStep)
    }

    // --- skip: solo si la definición lo permite ------------------------------

    @Test
    fun skipStepRecordsProvenanceWithoutInventingData() = runTest {
        assertTrue("NAME debe permitir omitir", SetupStepDefinitions.of(SetupStepId.NAME)!!.allowSkip)
        val vm = vm()
        vm.initialize(SetupWizardMode.FULL)
        advanceUntilIdle()

        vm.skipStep(SetupStepId.NAME)
        advanceUntilIdle()

        // Procedencia guardada, sin ningún dato inventado y sin avanzar.
        assertEquals(SetupAnswerProvenance.USER_DECLARED, vm.state.value.draft.stepProgress.answers[SetupStepId.NAME])
        assertEquals("", vm.state.value.draft.name)
        assertEquals(SetupStepId.NAME, vm.state.value.currentStep)
        // El paso omitido deja de bloquear Continuar.
        assertFalse(vm.state.value.stepValidation.any { it.isBlocking })

        // Y Continuar sobre el paso omitido avanza exactamente un paso.
        assertEquals(SetupSubmitOutcome.ACCEPTED, vm.submitCurrentStep(SetupStepId.NAME).outcome)
        advanceUntilIdle()
        assertEquals(SetupStepId.AGE, vm.state.value.currentStep)
    }

    @Test
    fun skipStepIsRejectedWhenTheDefinitionForbidsIt() = runTest {
        assertFalse("AGE no debe permitir omitir", SetupStepDefinitions.of(SetupStepId.AGE)!!.allowSkip)
        val vm = vm()
        vm.initialize(SetupWizardMode.FULL)
        advanceUntilIdle()

        vm.skipStep(SetupStepId.AGE)
        advanceUntilIdle()

        assertNull(vm.state.value.draft.stepProgress.answers[SetupStepId.AGE])
        assertTrue(vm.state.value.errors.isNotEmpty())
        assertEquals(SetupStepId.NAME, vm.state.value.currentStep)
    }

    // --- edición: una sola confirmación por edición --------------------------

    @Test
    fun editedStepConfirmsExactlyOnceAndKeepsItsAnswer() = runTest {
        val vm = vm()
        vm.initialize(SetupWizardMode.FULL)
        advanceUntilIdle()

        vm.setStepText(SetupStepId.NAME, "Ana")
        advanceUntilIdle()
        assertEquals(SetupSubmitOutcome.ACCEPTED, vm.submitCurrentStep(SetupStepId.NAME).outcome)
        advanceUntilIdle()
        assertEquals(SetupStepId.AGE, vm.state.value.currentStep)

        val answersBeforeEdit = vm.state.value.draft.wizChat.acceptedAnswers
        vm.editStep(SetupStepId.NAME)
        advanceUntilIdle()
        assertEquals(SetupStepId.NAME, vm.state.value.currentStep)
        assertEquals("Ana", vm.state.value.draft.name)
        // Editar no borra respuestas del espejo legacy.
        assertEquals(answersBeforeEdit, vm.state.value.draft.wizChat.acceptedAnswers)
        assertEquals(SetupAnswerProvenance.USER_DECLARED, vm.state.value.draft.stepProgress.answers[SetupStepId.NAME])

        // Doble pulsación sobre el paso editado: exactamente un avance.
        val first = vm.submitCurrentStep(SetupStepId.NAME)
        val second = vm.submitCurrentStep(SetupStepId.NAME)
        assertEquals(SetupSubmitOutcome.ACCEPTED, first.outcome)
        assertEquals(SetupSubmitOutcome.DROPPED, second.outcome)
        advanceUntilIdle()
        assertEquals(SetupStepId.AGE, vm.state.value.currentStep)
        assertEquals(SetupAnswerProvenance.USER_DECLARED, vm.state.value.draft.stepProgress.answers[SetupStepId.NAME])

        val revisions = persistence.saveLog.map { it.revision }
        assertTrue(revisions.zipWithNext().all { (a, b) -> a < b })
    }

    // --- selectPlan solo selecciona ------------------------------------------

    @Test
    fun selectPlanOnlySelectsAndNeverAdvances() = runTest {
        val vm = vm()
        vm.initialize(SetupWizardMode.FULL)
        advanceUntilIdle()
        val answersBefore = vm.state.value.draft.wizChat.acceptedAnswers
        val stepBefore = vm.state.value.currentStep

        vm.selectPlan("catalog-plan-x")
        advanceUntilIdle()

        assertEquals("catalog-plan-x", vm.state.value.draft.selectedCatalogId)
        assertEquals(stepBefore, vm.state.value.currentStep)
        assertEquals(answersBefore, vm.state.value.draft.wizChat.acceptedAnswers)
        assertTrue(vm.state.value.errors.isEmpty())
    }

    // --- errores con reintento específico -------------------------------------

    @Test
    fun eachInlineErrorMapsToItsOwnRetryOperation() {
        val vm = vm()
        assertEquals(SetupRetryOperation.LOAD, vm.retryOperationForError("initialize"))
        assertEquals(SetupRetryOperation.SAVE, vm.retryOperationForError("save"))
        assertEquals(SetupRetryOperation.PREVIEW, vm.retryOperationForError("preview"))
        assertEquals(SetupRetryOperation.CANDIDATES, vm.retryOperationForError("candidates"))
        assertEquals(SetupRetryOperation.RINGS_PREVIEW, vm.retryOperationForError("rings_preview"))
        assertEquals(SetupRetryOperation.COMMIT, vm.retryOperationForError("commit"))
        // Un error de validación no tiene reintento automático.
        assertNull(vm.retryOperationForError("age"))
        assertNull(vm.retryOperationForError("draft"))
    }

    @Test
    fun loadRetryIsNeverANoOpWhenTheDraftIsAlreadyLoaded() = runTest {
        val vm = vm()
        vm.initialize(SetupWizardMode.FULL)
        advanceUntilIdle()

        persistence.failNextSaves = 1
        vm.setStepText(SetupStepId.NAME, "Ana")
        advanceUntilIdle()
        assertTrue(vm.state.value.errors.isNotEmpty())
        assertTrue(persistence.rows.isEmpty())

        // El reintento de LOAD sobre un borrador ya cargado persiste en lugar
        // de quedarse en un no-op silencioso.
        vm.retryFailedOperation(SetupRetryOperation.LOAD)
        advanceUntilIdle()
        assertTrue(vm.state.value.errors.isEmpty())
        val row = persistence.rows[vm.state.value.draft.draftId] ?: error("sin fila tras el reintento")
        assertTrue(row.payloadJson.contains("Ana"))
    }

    // --- commit: puerta real, sin tocar el puerto -----------------------------

    @Test
    fun commitWithoutRealReviewFailsHonestlyAndNeverReachesThePort() = runTest {
        val vm = vm()
        vm.initialize(SetupWizardMode.FULL)
        advanceUntilIdle()

        val receipt = vm.commit()

        assertNull(receipt)
        assertTrue(commits.requests.isEmpty())
        assertNull(vm.state.value.receiptId)
        assertFalse(vm.state.value.isCommitting)
        assertTrue(vm.state.value.errors.isNotEmpty())
        // El fallo de commit deja los pasos visibles con el error inline.
        assertTrue(vm.state.value.machineState != WizChatMachineState.Committed)
        assertTrue(vm.state.value.machineState != WizChatMachineState.RecoverableError)
        assertEquals(SetupStepId.NAME, vm.state.value.currentStep)
    }

    // --- P0: en casa + inventario finito llega a candidatos o estado explícito --

    @Test
    fun homeWithFiniteInventoryProvisionsCandidatesOrExplicitState() = runTest {
        // Conexión con la API de M7: con inventario no nulo manda él y aporta
        // equipo (BODYWEIGHT incluido) aunque el perfil legacy vaya vacío, que
        // es exactamente el contrato de «Entreno en casa» tras M1.
        val homeOptions = TrainingOptions(inventory = EquipmentInventory())
        assertTrue(
            "inventario no nulo debe aportar equipo efectivo con perfil legacy vacío",
            homeOptions.effectiveEquipment(emptySet()).isNotEmpty(),
        )

        val vm = vm()
        vm.initialize(SetupWizardMode.FULL)
        advanceUntilIdle()

        // Semilla legítima sobre el borrador real: entorno en casa (perfil
        // legacy vacío por contrato) + inventario finito declarado + todas las
        // entradas de candidato. Sin recalcular nada declarado.
        vm.update { draft ->
            draft.copy(
                trainingEnvironment = "Entreno en casa",
                equipment = emptySet(),
                trainingPath = SetupTrainingPath.PERSONALIZE,
                programRoute = SetupProgramRoute.CUSTOMIZABLE,
                daysPerWeek = 3,
                selectedWeekdays = setOf(1, 3, 5),
                minutesPerSession = 60,
                goal = SetupGoal.STRENGTH,
                experience = SetupExperience.INTERMEDIATE,
                focus = SetupFocus.FULL_BODY,
                trainingOptions = draft.trainingOptions.copy(inventory = EquipmentInventory()),
            )
        }
        advanceUntilIdle()

        // La compuerta YA abrió: el job de candidatos se lanza (antes quedaba
        // `canPrepare=false` en silencio: vacío, sin carga y sin explicación).
        val gated = vm.state.value
        assertTrue(
            "compuerta de candidatos cerrada en silencio con inventario declarado: " +
                "loading=${gated.isCandidateLoading} candidates=${gated.planCandidates.size} " +
                "previewError=${gated.previewError} errors=${gated.errors}",
            gated.isCandidateLoading ||
                gated.planCandidates.isNotEmpty() ||
                gated.previewError != null ||
                gated.errors.containsKey("candidates"),
        )

        // Estado terminal honesto: candidatos provisionados o explicación real
        // (en Robolectric el catálogo real puede fallar → previewError +
        // errors["candidates"], con reintento). Nunca «cargando para siempre».
        val deadline = System.currentTimeMillis() + CANDIDATE_TIMEOUT_MS
        var state = vm.state.value
        while (System.currentTimeMillis() < deadline &&
            state.isCandidateLoading &&
            state.planCandidates.isEmpty() &&
            state.previewError == null &&
            !state.errors.containsKey("candidates")
        ) {
            advanceUntilIdle()
            Thread.sleep(25)
            state = vm.state.value
        }
        assertFalse(
            "job de candidatos colgado con inventario declarado: loading=${state.isCandidateLoading}",
            state.isCandidateLoading,
        )
        assertTrue(
            "sin candidatos ni explicación: candidates=${state.planCandidates.size} " +
                "previewError=${state.previewError} errors=${state.errors}",
            state.planCandidates.isNotEmpty() ||
                state.previewError != null ||
                state.errors.containsKey("candidates"),
        )
    }

    // --- declaración con valor igual (confirmar el default70 kg) -------------

    @Test
    fun sameValueSetterDeclaresOnceAndRepeatedWriteDoesNotBumpRevision() = runTest {
        val vm = vm()
        vm.initialize(SetupWizardMode.FULL)
        advanceUntilIdle()

        // Prefill SIN declarar (equivale al peso ya presente con su texto
        // canónico, p. ej. venido de Ajustes o de un borrador migrado): la
        // extensión pura escribe exactamente lo que después escribiría el tap.
        vm.update { draft -> draft.withStepNumber(SetupStepId.WEIGHT, 70.0, System.currentTimeMillis()) }
        advanceUntilIdle()
        assertEquals(70.0, vm.state.value.draft.weightKg!!, 0.001)
        assertFalse(
            "el prefill no declara el paso",
            SetupStepId.WEIGHT in vm.state.value.draft.declaredSteps,
        )

        // Confirmación explícita del MISMO valor (tap sin mover nada): el
        // cambio es identico al borrador, pero la escritura explícita declara.
        vm.setWeightKg(70.0)
        advanceUntilIdle()
        assertEquals(70.0, vm.state.value.draft.weightKg!!, 0.001)
        assertTrue(
            "declarar el paso aunque el valor no cambie",
            SetupStepId.WEIGHT in vm.state.value.draft.declaredSteps,
        )
        val row = persistence.rows[vm.state.value.draft.draftId]
            ?: throw AssertionError("la declaración debe persistirse en la fila")
        val persisted = json.decodeFromString<SetupWizardDraft>(row.payloadJson)
        assertTrue("declaración persistida: ${persisted.declaredSteps}", SetupStepId.WEIGHT in persisted.declaredSteps)
        // Declarar no confirma: el paso sigue sin responder y no avanza.
        assertFalse(SetupStepId.WEIGHT in vm.state.value.draft.stepProgress.answers)
        assertEquals(SetupStepId.NAME, vm.state.value.currentStep)

        // Repetir el mismo valor: idempotente, sin revisión inútil.
        val revisionAfter = row.revision
        val savesAfter = persistence.saveLog.size
        vm.setWeightKg(70.0)
        advanceUntilIdle()
        assertEquals(revisionAfter, persistence.rows.getValue(vm.state.value.draft.draftId).revision)
        assertEquals(savesAfter, persistence.saveLog.size)
    }

    private companion object {
        /** Espera del estado terminal de candidatos (catálogo real, puede fallar en Robolectric). */
        const val CANDIDATE_TIMEOUT_MS = 10_000L
    }

    // --- fakes ----------------------------------------------------------------

    private class FakePersistence : SetupWizardPersistence {
        data class Row(val payloadJson: String, val revision: Long) {
            fun toDraft(draftId: String) = SetupDraft(draftId, payloadJson, revision, null, 0L)
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
                    rows[draftId] = Row(payloadJson, revision)
                    saveLog += model
                    model
                }
                revision < current.revision -> throw IllegalArgumentException("La revisión del borrador es antigua")
                revision == current.revision && payloadJson != current.payloadJson ->
                    throw IllegalArgumentException("Conflicto de revisión del borrador")
                revision == current.revision -> current.toDraft(draftId)
                else -> {
                    rows[draftId] = Row(payloadJson, revision)
                    saveLog += model
                    model
                }
            }
        }

        override suspend fun discard(draftId: String) {
            rows.remove(draftId)
        }

        override suspend fun listRecoverable(): List<SetupDraftCandidate> = emptyList()
    }

    private class FakeCommits : SetupWizardCommits {
        val requests = mutableListOf<SetupCommitRequest>()

        override suspend fun commit(request: SetupCommitRequest): SetupCommitResult {
            requests += request
            return SetupCommitResult(request.commitId, null, null, emptyList())
        }
    }

    private class FakeEnvironment(
        override val settings: Settings = Settings(),
    ) : SetupWizardEnvironment {
        override suspend fun awaitReady() = Unit
        override fun activeProgramId(): String? = null
        override fun activeNutritionPlanId(): String? = null
        override fun activeNutritionPlan(): NutritionPlan? = null
        override fun nutritionPlan(id: String): NutritionPlan? = null
        override fun hasInitialRecoveryEvidence(): Boolean = false
        override suspend fun refreshBodyProgress() = Unit
    }
}

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
 * C1 · El paso de grasa corporal recorrido con el [SetupWizardViewModel] REAL
 * (persistencia y entorno hermeticos, como [SetupWizardOrchestrationTest]):
 * Continuar queda bloqueado hasta una accion explicita (mover o tocar la
 * figura, escribir una medicion u «Omitir este paso»), omitir limpia lo ya
 * elegido y deja Continuar habilitado sin avanzar, y quien vuelve con una
 * grasa declarada en Ajustes no se queda bloqueado.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class SetupWizardBodyFatViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private lateinit var persistence: InMemoryPersistence
    private lateinit var app: Application

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        app = ApplicationProvider.getApplicationContext()
        persistence = InMemoryPersistence()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm(settings: Settings = Settings()) =
        SetupWizardViewModel(app, SavedStateHandle(), persistence, FixedSettingsEnvironment(settings))

    /** Recorre las respuestas reales de NAME…EQUATION_SEX hasta dejar el cursor en BODY_FAT. */
    private fun TestScope.walkToBodyFat(vm: SetupWizardViewModel) {
        vm.initialize(SetupWizardMode.FULL)
        advanceUntilIdle()

        vm.setName("Ana")
        advanceUntilIdle()
        confirm(vm, SetupStepId.NAME, SetupStepId.AGE)
        vm.setAge(30)
        advanceUntilIdle()
        confirm(vm, SetupStepId.AGE, SetupStepId.HEIGHT)
        vm.setHeightCm(175.0)
        advanceUntilIdle()
        confirm(vm, SetupStepId.HEIGHT, SetupStepId.WEIGHT)
        vm.setWeightKg(72.0)
        advanceUntilIdle()
        confirm(vm, SetupStepId.WEIGHT, SetupStepId.EQUATION_SEX)
        // «No lo sé» a solas ya no avanza: se contesta la consulta hormonal (equilibrio → ecuación promedio).
        vm.setStepChoice(SetupStepId.EQUATION_SEX, "hormones_mixed")
        advanceUntilIdle()
        confirm(vm, SetupStepId.EQUATION_SEX, SetupStepId.BODY_FAT)
    }

    private fun TestScope.confirm(vm: SetupWizardViewModel, step: SetupStepId, expectedNext: SetupStepId) {
        assertEquals("Continuar sobre $step (errores=${vm.state.value.errors})", SetupSubmitOutcome.ACCEPTED, vm.submitCurrentStep(step).outcome)
        advanceUntilIdle()
        assertEquals(expectedNext, vm.state.value.currentStep)
    }

    private fun bodyFatDraft(vm: SetupWizardViewModel) = vm.state.value.draft

    // ─── Sin acción explícita: bloqueado ─────────────────────────────────────

    @Test
    fun continuarStaysBlockedUntilTheUserActsOnTheBodyFatStep() = runTest {
        val vm = vm()
        walkToBodyFat(vm)

        val state = vm.state.value
        assertEquals(SetupBodyFatState.PENDING, state.draft.bodyFatState())
        assertFalse("la figura de arranque no habilita Continuar", state.canConfirmStep)
        assertEquals(BODY_FAT_PENDING_MESSAGE, state.stepValidation.single().message)
        // La pantalla muestra ≈25 % pero no hay ningún dato guardado.
        assertNull(state.draft.bodyFatPercent)
        assertNull(state.draft.bodyFatSource)
        assertNull(state.draft.importedBodyFatPercent)

        val rejected = vm.submitCurrentStep(SetupStepId.BODY_FAT)
        advanceUntilIdle()
        assertEquals(SetupSubmitOutcome.REJECTED, rejected.outcome)
        assertEquals(BODY_FAT_PENDING_MESSAGE, vm.state.value.errors["bodyFat"])
        assertEquals("el cursor no se mueve", SetupStepId.BODY_FAT, vm.state.value.currentStep)
        assertNull("nada se confirmó", vm.state.value.draft.stepProgress.answers[SetupStepId.BODY_FAT])
    }

    // ─── Cada acción explícita desbloquea y avanza ───────────────────────────

    @Test
    fun movingOrTappingTheFigureEnablesContinuarAndSavesTheEstimate() = runTest {
        val vm = vm()
        walkToBodyFat(vm)

        // Mismas llamadas que hace la figura (arrastre o toque) y la línea «Usar ≈ N %».
        vm.setStepChoice(SetupStepId.BODY_FAT, SetupBodyFatSource.VISUAL_ESTIMATE.name)
        vm.setStepNumber(SetupStepId.BODY_FAT, 22.0)
        advanceUntilIdle()

        assertEquals(SetupBodyFatState.VISUAL, vm.state.value.draft.bodyFatState())
        assertTrue(vm.state.value.canConfirmStep)
        assertEquals(SetupBodyFatSource.VISUAL_ESTIMATE, bodyFatDraft(vm).bodyFatSource)
        assertEquals(22.0, bodyFatDraft(vm).bodyFatPercent!!, 0.001)

        confirm(vm, SetupStepId.BODY_FAT, SetupStepId.MILESTONE_BASICS)
        assertEquals(
            "la estimación confirmada es una respuesta declarada",
            SetupAnswerProvenance.USER_DECLARED,
            bodyFatDraft(vm).stepProgress.answers[SetupStepId.BODY_FAT],
        )
        assertEquals(22.0, bodyFatDraft(vm).bodyFatPercent!!, 0.001)
    }

    @Test
    fun writingAMeasurementEnablesContinuarAndSavesItAsMeasured() = runTest {
        val vm = vm()
        walkToBodyFat(vm)

        vm.setStepChoice(SetupStepId.BODY_FAT, SetupBodyFatSource.MEASURED.name)
        vm.setStepText(SetupStepId.BODY_FAT, "17,5")
        vm.setStepNumber(SetupStepId.BODY_FAT, 17.5)
        advanceUntilIdle()

        assertEquals(SetupBodyFatState.MEASURED, vm.state.value.draft.bodyFatState())
        assertTrue(vm.state.value.canConfirmStep)
        confirm(vm, SetupStepId.BODY_FAT, SetupStepId.MILESTONE_BASICS)
        assertEquals(SetupBodyFatSource.MEASURED, bodyFatDraft(vm).bodyFatSource)
        assertEquals(17.5, bodyFatDraft(vm).bodyFatPercent!!, 0.001)
    }

    @Test
    fun skippingEnablesContinuarWithoutAdvancingAndWithoutInventingAPercentage() = runTest {
        val vm = vm()
        walkToBodyFat(vm)
        assertFalse(vm.state.value.canConfirmStep)

        vm.skipStep(SetupStepId.BODY_FAT)
        advanceUntilIdle()

        val skipped = bodyFatDraft(vm)
        assertEquals(SetupBodyFatState.SKIPPED, skipped.bodyFatState())
        assertEquals(SetupBodyFatSource.UNKNOWN, skipped.bodyFatSource)
        assertNull(skipped.bodyFatPercent)
        assertEquals(SetupAnswerProvenance.USER_DECLARED, skipped.stepProgress.answers[SetupStepId.BODY_FAT])
        assertTrue("Omitir deja Continuar habilitado", vm.state.value.canConfirmStep)
        assertEquals("Omitir no avanza por sí solo", SetupStepId.BODY_FAT, vm.state.value.currentStep)

        confirm(vm, SetupStepId.BODY_FAT, SetupStepId.MILESTONE_BASICS)
        assertNull(bodyFatDraft(vm).bodyFatPercent)
    }

    // ─── Omitir limpia lo que ya se había elegido ────────────────────────────

    @Test
    fun skippingAfterMovingTheFigureClearsThePercentageDateAndText() = runTest {
        val vm = vm()
        walkToBodyFat(vm)
        vm.setStepChoice(SetupStepId.BODY_FAT, SetupBodyFatSource.VISUAL_ESTIMATE.name)
        vm.setStepNumber(SetupStepId.BODY_FAT, 22.0)
        advanceUntilIdle()
        assertEquals(22.0, bodyFatDraft(vm).bodyFatPercent!!, 0.001)
        assertTrue(bodyFatDraft(vm).bodyFatCapturedAtEpochMs != null)
        assertTrue("BODY_FAT" in bodyFatDraft(vm).inputTexts)

        vm.skipStep(SetupStepId.BODY_FAT)
        advanceUntilIdle()

        val cleared = bodyFatDraft(vm)
        assertEquals(SetupBodyFatSource.UNKNOWN, cleared.bodyFatSource)
        assertNull("el porcentaje elegido se descarta", cleared.bodyFatPercent)
        assertNull("y su fecha", cleared.bodyFatCapturedAtEpochMs)
        assertFalse("y el texto escrito", "BODY_FAT" in cleared.inputTexts)
        assertTrue(vm.state.value.canConfirmStep)

        // Lo persistido es lo mismo que se ve: ningún porcentaje fantasma en el borrador guardado.
        val saved = json.decodeFromString<SetupWizardDraft>(persistence.rows.getValue(cleared.draftId).payloadJson)
        assertEquals(SetupBodyFatSource.UNKNOWN, saved.bodyFatSource)
        assertNull(saved.bodyFatPercent)
        assertNull(saved.bodyFatCapturedAtEpochMs)
    }

    @Test
    fun movingTheFigureAfterSkippingDeclaresTheNewEstimate() = runTest {
        val vm = vm()
        walkToBodyFat(vm)
        vm.skipStep(SetupStepId.BODY_FAT)
        advanceUntilIdle()
        assertEquals(SetupBodyFatState.SKIPPED, bodyFatDraft(vm).bodyFatState())

        vm.setStepChoice(SetupStepId.BODY_FAT, SetupBodyFatSource.VISUAL_ESTIMATE.name)
        vm.setStepNumber(SetupStepId.BODY_FAT, 18.0)
        advanceUntilIdle()

        assertEquals(SetupBodyFatState.VISUAL, bodyFatDraft(vm).bodyFatState())
        assertEquals(18.0, bodyFatDraft(vm).bodyFatPercent!!, 0.001)
        assertTrue(vm.state.value.canConfirmStep)
    }

    // ─── Quien vuelve: grasa ya declarada en Ajustes ─────────────────────────

    @Test
    fun aReturningUserWithABodyFatInSettingsIsNotBlockedAndNothingIsFabricated() = runTest {
        val vm = vm(Settings(userVitals = UserVitals(bodyFatPercentage = 21.0)))
        walkToBodyFat(vm)

        val draft = bodyFatDraft(vm)
        assertEquals("el dato previo viaja en el borrador", 21.0, draft.importedBodyFatPercent!!, 0.001)
        assertEquals(SetupBodyFatState.ON_FILE, draft.bodyFatState())
        assertTrue("no se bloquea a quien ya declaró su grasa en Ajustes", vm.state.value.canConfirmStep)

        confirm(vm, SetupStepId.BODY_FAT, SetupStepId.MILESTONE_BASICS)
        val after = bodyFatDraft(vm)
        // Sin acción propia: ni fuente ni porcentaje declarados en este alta (no se crea observación nueva).
        assertNull(after.bodyFatSource)
        assertNull(after.bodyFatPercent)
        assertEquals(
            "sin tocar el paso la respuesta es una sugerencia, no una declaración",
            SetupAnswerProvenance.SUGGESTED,
            after.stepProgress.answers[SetupStepId.BODY_FAT],
        )
    }

    @Test
    fun aReturningUserCanStillOmitTheStepAndTheSettingsValueStaysAsHistory() = runTest {
        val vm = vm(Settings(userVitals = UserVitals(bodyFatPercentage = 21.0)))
        walkToBodyFat(vm)

        vm.skipStep(SetupStepId.BODY_FAT)
        advanceUntilIdle()

        assertEquals(SetupBodyFatState.SKIPPED, bodyFatDraft(vm).bodyFatState())
        assertNull(bodyFatDraft(vm).bodyFatPercent)
        assertEquals(21.0, bodyFatDraft(vm).importedBodyFatPercent!!, 0.001)
        assertTrue(vm.state.value.canConfirmStep)
    }

    // ─── Fakes herméticos ────────────────────────────────────────────────────

    private class InMemoryPersistence : SetupWizardPersistence {
        val rows = LinkedHashMap<String, SetupDraft>()

        override suspend fun load(draftId: String): SetupDraft? = rows[draftId]

        override suspend fun save(draftId: String, payloadJson: String, revision: Long, catalogRevision: String?): SetupDraft =
            SetupDraft(draftId, payloadJson, revision, catalogRevision, 0L).also { rows[draftId] = it }

        override suspend fun discard(draftId: String) {
            rows.remove(draftId)
        }

        override suspend fun listRecoverable(): List<SetupDraftCandidate> =
            rows.map { (id, row) -> SetupDraftCandidate(id, SetupDraftScope.FULL, row.revision, 0L, row.catalogRevision) }
    }

    private class FixedSettingsEnvironment(override val settings: Settings) : SetupWizardEnvironment {
        override suspend fun awaitReady() = Unit
        override fun activeProgramId(): String? = null
        override fun activeNutritionPlanId(): String? = null
        override fun activeNutritionPlan(): NutritionPlan? = null
        override fun nutritionPlan(id: String): NutritionPlan? = null
        override fun hasInitialRecoveryEvidence(): Boolean = false
        override suspend fun refreshBodyProgress() = Unit
    }
}

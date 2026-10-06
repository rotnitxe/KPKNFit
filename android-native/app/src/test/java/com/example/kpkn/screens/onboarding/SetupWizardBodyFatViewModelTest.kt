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
import com.example.kpkn.domain.nutrition.physiqueSliderPositionForBodyFat
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
 * El paso de grasa corporal (regla vertical, obligatorio) recorrido con el [SetupWizardViewModel] REAL
 * (persistencia y entorno hermeticos, como [SetupWizardOrchestrationTest]): Continuar queda bloqueado hasta
 * que se mueve la regla (arrastrar o tocar un punto, que declara el valor en una sola escritura), «omitir» ya
 * no existe (el ViewModel lo rechaza) y una omision de un borrador antiguo no valida, y quien vuelve con una
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
    fun continuarStaysBlockedUntilTheUserMovesTheRuler() = runTest {
        val vm = vm()
        walkToBodyFat(vm)

        val state = vm.state.value
        assertEquals(SetupBodyFatState.PENDING, state.draft.bodyFatState())
        assertFalse("la posición de arranque no habilita Continuar", state.canConfirmStep)
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

    // ─── Mover la regla desbloquea y avanza ──────────────────────────────────

    @Test
    fun movingTheRulerDeclaresTheValueEnablesContinuarAndSavesTheEstimate() = runTest {
        val vm = vm()
        walkToBodyFat(vm)

        // Lo que hace la regla al soltar (o al tocar un punto): una sola escritura del borrador.
        vm.updateStep(SetupStepId.BODY_FAT) { draft -> draft.withBodyFatRulerValue(22) }
        advanceUntilIdle()

        assertEquals(SetupBodyFatState.VISUAL, vm.state.value.draft.bodyFatState())
        assertTrue("el check queda habilitado sin pulsar nada más", vm.state.value.canConfirmStep)
        assertEquals(SetupBodyFatSource.VISUAL_ESTIMATE, bodyFatDraft(vm).bodyFatSource)
        assertEquals(22.0, bodyFatDraft(vm).bodyFatPercent!!, 0.001)
        assertEquals(
            "la posición de la figura se deriva del porcentaje",
            physiqueSliderPositionForBodyFat(22.0),
            bodyFatDraft(vm).physiqueSliderPosition,
            0f,
        )
        assertEquals("el cursor no se mueve hasta que el check confirma", SetupStepId.BODY_FAT, vm.state.value.currentStep)

        confirm(vm, SetupStepId.BODY_FAT, SetupStepId.MILESTONE_BASICS)
        assertEquals(
            "la estimación confirmada es una respuesta declarada",
            SetupAnswerProvenance.USER_DECLARED,
            bodyFatDraft(vm).stepProgress.answers[SetupStepId.BODY_FAT],
        )
        assertEquals(22.0, bodyFatDraft(vm).bodyFatPercent!!, 0.001)

        // Lo persistido es lo mismo que se ve: porcentaje, fuente, fecha real y posición de la figura.
        val saved = json.decodeFromString<SetupWizardDraft>(persistence.rows.getValue(bodyFatDraft(vm).draftId).payloadJson)
        assertEquals(SetupBodyFatSource.VISUAL_ESTIMATE, saved.bodyFatSource)
        assertEquals(22.0, saved.bodyFatPercent!!, 0.001)
        assertTrue(saved.bodyFatCapturedAtEpochMs != null)
        assertEquals(physiqueSliderPositionForBodyFat(22.0), saved.physiqueSliderPosition, 0f)
    }

    @Test
    fun theSetterContractTheOldFigureUsedStillDeclaresTheSameThing() = runTest {
        val vm = vm()
        walkToBodyFat(vm)

        vm.setStepChoice(SetupStepId.BODY_FAT, SetupBodyFatSource.VISUAL_ESTIMATE.name)
        vm.setStepNumber(SetupStepId.BODY_FAT, 22.0)
        advanceUntilIdle()

        assertEquals(SetupBodyFatState.VISUAL, vm.state.value.draft.bodyFatState())
        assertTrue(vm.state.value.canConfirmStep)
        assertEquals(22.0, bodyFatDraft(vm).bodyFatPercent!!, 0.001)
    }

    @Test
    fun theFirstTouchOnTheStartingValueIsAlreadyAnAnswer() = runTest {
        val vm = vm()
        walkToBodyFat(vm)
        assertFalse(vm.state.value.canConfirmStep)

        // Tocar el 25 % de arranque: el valor no cambia, pero ahora está declarado.
        vm.updateStep(SetupStepId.BODY_FAT) { draft -> draft.withBodyFatRulerValue(25) }
        advanceUntilIdle()

        assertEquals(25.0, bodyFatDraft(vm).bodyFatPercent!!, 0.0)
        assertEquals(SetupBodyFatState.VISUAL, bodyFatDraft(vm).bodyFatState())
        assertTrue(vm.state.value.canConfirmStep)
    }

    @Test
    fun switchingTheFigureNeverDeclaresThePercentage() = runTest {
        val vm = vm()
        walkToBodyFat(vm)

        vm.updateStep(SetupStepId.BODY_FAT) { draft -> draft.copy(physiqueModel = "female") }
        advanceUntilIdle()

        assertEquals("female", bodyFatDraft(vm).physiqueModel)
        assertNull("cambiar de figura no fija ningún porcentaje", bodyFatDraft(vm).bodyFatPercent)
        assertNull(bodyFatDraft(vm).bodyFatSource)
        assertFalse("y el check sigue bloqueado", vm.state.value.canConfirmStep)
        assertNull("ni toca el sexo de cálculo", bodyFatDraft(vm).nutritionDraft?.equationSex)
    }

    @Test
    fun writingAMeasurementThroughTheSettersStillSavesItAsMeasuredForOldDrafts() = runTest {
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

    // ─── Ya no se puede omitir ───────────────────────────────────────────────

    @Test
    fun skippingTheBodyFatStepIsRejectedAndNothingChanges() = runTest {
        val vm = vm()
        walkToBodyFat(vm)
        val before = bodyFatDraft(vm)

        vm.skipStep(SetupStepId.BODY_FAT)
        advanceUntilIdle()

        assertEquals("Este paso no se puede omitir", vm.state.value.errors[SetupStepId.BODY_FAT.name])
        assertEquals("el borrador no cambia", before, bodyFatDraft(vm))
        assertNull(bodyFatDraft(vm).bodyFatSource)
        assertNull(bodyFatDraft(vm).stepProgress.answers[SetupStepId.BODY_FAT])
        assertFalse("y Continuar sigue bloqueado", vm.state.value.canConfirmStep)
        assertEquals(SetupStepId.BODY_FAT, vm.state.value.currentStep)
    }

    @Test
    fun skippingAfterMovingTheRulerIsRejectedAndKeepsTheDeclaredValue() = runTest {
        val vm = vm()
        walkToBodyFat(vm)
        vm.updateStep(SetupStepId.BODY_FAT) { draft -> draft.withBodyFatRulerValue(22) }
        advanceUntilIdle()

        vm.skipStep(SetupStepId.BODY_FAT)
        advanceUntilIdle()

        assertEquals("Este paso no se puede omitir", vm.state.value.errors[SetupStepId.BODY_FAT.name])
        assertEquals(SetupBodyFatSource.VISUAL_ESTIMATE, bodyFatDraft(vm).bodyFatSource)
        assertEquals(22.0, bodyFatDraft(vm).bodyFatPercent!!, 0.001)
        assertTrue(vm.state.value.canConfirmStep)
    }

    @Test
    fun anOldOmittedDraftIsBlockedUntilTheRulerDeclaresAPercentage() = runTest {
        val vm = vm()
        walkToBodyFat(vm)
        // Un borrador de una versión anterior que usó «Omitir este paso» (la fuente «No lo sé»).
        vm.setStepChoice(SetupStepId.BODY_FAT, SetupBodyFatSource.UNKNOWN.name)
        advanceUntilIdle()
        assertEquals(SetupBodyFatState.SKIPPED, bodyFatDraft(vm).bodyFatState())
        assertFalse("la omisión antigua ya no valida", vm.state.value.canConfirmStep)
        assertEquals(BODY_FAT_PENDING_MESSAGE, vm.state.value.stepValidation.single().message)

        val rejected = vm.submitCurrentStep(SetupStepId.BODY_FAT)
        advanceUntilIdle()
        assertEquals(SetupSubmitOutcome.REJECTED, rejected.outcome)
        assertEquals(SetupStepId.BODY_FAT, vm.state.value.currentStep)

        vm.updateStep(SetupStepId.BODY_FAT) { draft -> draft.withBodyFatRulerValue(18) }
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
        assertEquals("la regla arranca en el dato de Ajustes", 21.0, draft.bodyFatRulerPercent(), 0.001)
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
    fun aReturningUserCanReplaceTheSettingsValueWithTheRulerAndItStaysAsHistory() = runTest {
        val vm = vm(Settings(userVitals = UserVitals(bodyFatPercentage = 21.0)))
        walkToBodyFat(vm)

        vm.updateStep(SetupStepId.BODY_FAT) { draft -> draft.withBodyFatRulerValue(18) }
        advanceUntilIdle()

        assertEquals(SetupBodyFatState.VISUAL, bodyFatDraft(vm).bodyFatState())
        assertEquals(18.0, bodyFatDraft(vm).bodyFatPercent!!, 0.001)
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

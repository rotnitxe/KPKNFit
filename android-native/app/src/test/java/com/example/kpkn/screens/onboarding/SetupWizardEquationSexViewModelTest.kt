package com.example.kpkn.screens.onboarding

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.onboarding.SetupDraft
import com.example.kpkn.data.onboarding.SetupDraftCandidate
import com.example.kpkn.data.onboarding.SetupDraftScope
import com.example.kpkn.domain.nutrition.EerSex
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
 * El paso de género recorrido con el [SetupWizardViewModel] REAL (persistencia y entorno
 * herméticos, como [SetupWizardBodyFatViewModelTest]).
 *
 * «No lo sé» ya no es una salida válida: la energía se calcula con una ecuación científica en todo
 * momento, así que a solas deja Continuar bloqueado y pide contar qué hormonas predominan. Cada
 * respuesta hormonal elige la base de la ecuación (estrógenos → femenina, andrógenos → masculina,
 * equilibrio → promedio) y desbloquea el paso.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class SetupWizardEquationSexViewModelTest {

    private val dispatcher = StandardTestDispatcher()

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

    private fun vm() = SetupWizardViewModel(app, SavedStateHandle(), persistence, FixedSettingsEnvironment(Settings()))

    /** Recorre las respuestas reales de NAME…WEIGHT hasta dejar el cursor en EQUATION_SEX. */
    private fun TestScope.walkToEquationSex(vm: SetupWizardViewModel) {
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
    }

    private fun TestScope.confirm(vm: SetupWizardViewModel, step: SetupStepId, expectedNext: SetupStepId) {
        assertEquals("Continuar sobre $step (errores=${vm.state.value.errors})", SetupSubmitOutcome.ACCEPTED, vm.submitCurrentStep(step).outcome)
        advanceUntilIdle()
        assertEquals(expectedNext, vm.state.value.currentStep)
    }

    private fun TestScope.choose(vm: SetupWizardViewModel, value: String) {
        vm.setStepChoice(SetupStepId.EQUATION_SEX, value)
        advanceUntilIdle()
    }

    private val askForHormones = "Cuéntanos qué hormonas predominan en tu cuerpo"

    @Test
    fun unknownAloneKeepsContinueBlockedUntilTheHormonalQuestionIsAnswered() = runTest {
        val vm = vm()
        walkToEquationSex(vm)

        // Sin tocar nada: bloqueado.
        assertFalse(vm.state.value.canConfirmStep)

        // «No lo sé» a solas: sigue bloqueado y pide contar qué hormonas predominan.
        choose(vm, "unknown")
        assertEquals(setOf("unknown"), vm.state.value.draft.selectedValues(SetupStepId.EQUATION_SEX))
        assertNull(vm.state.value.draft.nutritionDraft?.equationSex)
        assertFalse("«No lo sé» a solas no habilita Continuar", vm.state.value.canConfirmStep)
        assertEquals(askForHormones, vm.state.value.stepValidation.single().message)

        val rejected = vm.submitCurrentStep(SetupStepId.EQUATION_SEX)
        advanceUntilIdle()
        assertEquals(SetupSubmitOutcome.REJECTED, rejected.outcome)
        assertEquals("el cursor no se mueve", SetupStepId.EQUATION_SEX, vm.state.value.currentStep)
        assertEquals(askForHormones, vm.state.value.errors["equationSex"])
        assertNull("nada se confirmó", vm.state.value.draft.stepProgress.answers[SetupStepId.EQUATION_SEX])

        // Contestar la consulta hormonal reemplaza «unknown» y desbloquea el paso.
        choose(vm, "hormones_estrogen")
        assertEquals(setOf("hormones_estrogen"), vm.state.value.draft.selectedValues(SetupStepId.EQUATION_SEX))
        assertEquals(EerSex.FEMALE, vm.state.value.draft.nutritionDraft?.equationSex)
        assertTrue(vm.state.value.canConfirmStep)

        confirm(vm, SetupStepId.EQUATION_SEX, SetupStepId.BODY_FAT)
        assertEquals(
            SetupAnswerProvenance.USER_DECLARED,
            vm.state.value.draft.stepProgress.answers[SetupStepId.EQUATION_SEX],
        )
        // La respuesta confirmada sigue siendo la hormonal, nunca un «unknown» a solas.
        assertEquals(setOf("hormones_estrogen"), vm.state.value.draft.selectedValues(SetupStepId.EQUATION_SEX))
    }

    @Test
    fun eachHormonalAnswerUnlocksContinueWithItsEquationBase() = runTest {
        val vm = vm()
        walkToEquationSex(vm)

        mapOf(
            "hormones_estrogen" to EerSex.FEMALE,
            "hormones_androgen" to EerSex.MALE,
            "hormones_mixed" to EerSex.AVERAGE,
        ).forEach { (value, sex) ->
            // Cada vez se parte de «No lo sé»: es la tarjeta que abre el panel.
            choose(vm, "unknown")
            assertFalse("unknown antes de $value", vm.state.value.canConfirmStep)

            choose(vm, value)
            assertEquals("base de $value", sex, vm.state.value.draft.nutritionDraft?.equationSex)
            assertEquals(setOf(value), vm.state.value.draft.selectedValues(SetupStepId.EQUATION_SEX))
            assertTrue("$value habilita Continuar", vm.state.value.canConfirmStep)
        }
    }

    @Test
    fun aGlyphAfterTheHormonalPanelReplacesTheHormonalAnswer() = runTest {
        val vm = vm()
        walkToEquationSex(vm)

        choose(vm, "unknown")
        choose(vm, "hormones_androgen")
        assertEquals(EerSex.MALE, vm.state.value.draft.nutritionDraft?.equationSex)

        // Elegir un glifo cierra el panel: la selección pasa a ser solo el glifo.
        choose(vm, "trans_female")
        assertEquals(setOf("trans_female"), vm.state.value.draft.selectedValues(SetupStepId.EQUATION_SEX))
        assertEquals(EerSex.FEMALE, vm.state.value.draft.nutritionDraft?.equationSex)
        assertTrue(vm.state.value.canConfirmStep)

        confirm(vm, SetupStepId.EQUATION_SEX, SetupStepId.BODY_FAT)
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

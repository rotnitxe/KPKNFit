package com.example.kpkn.screens.onboarding

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.onboarding.SetupDraft
import com.example.kpkn.data.onboarding.SetupDraftCandidate
import com.example.kpkn.domain.onboarding.SetupStepId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regresión de la multiselección: dos toques **sin esperar** el persisto del
 * primero (sin `await` entre llamadas) deben acumularse, porque
 * `toggleStepChoice` deriva el conjunto dentro del mutex del ViewModel a partir
 * del último borrador y nunca del Set que la tarjeta leyó al componer. También
 * cubre que un valor excluyente desplaza a los anteriores y que las molestias
 * manuales fuera del catálogo se conservan.
 *
 * Sin tests visuales: solo contrato de datos del setter.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class SetupWizardToggleStepChoiceTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var persistence: MemoryPersistence
    private lateinit var app: Application
    private lateinit var handle: SavedStateHandle

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        app = ApplicationProvider.getApplicationContext()
        persistence = MemoryPersistence()
        handle = SavedStateHandle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm() = SetupWizardViewModel(app, handle, persistence, FakeEnvironment())

    @Test
    fun twoTogglesBackToBackWithoutAwaitKeepBothSelections() = runTest {
        val vm = vm()
        vm.initialize(SetupWizardMode.FULL)
        advanceUntilIdle()

        // Barrera: cada toggle escribe y publica en el mutex; no se espera
        // entre llamadas y aun así los dos toques deben sobrevivir.
        vm.toggleStepChoice(SetupStepId.NUTRITION_ELIGIBILITY, "pregnancy")
        vm.toggleStepChoice(SetupStepId.NUTRITION_ELIGIBILITY, "lactation")
        advanceUntilIdle()

        val draft = vm.state.value.draft
        assertEquals(
            setOf("pregnancy", "lactation"),
            draft.selectedValues(SetupStepId.NUTRITION_ELIGIBILITY),
        )
        assertTrue(draft.nutritionDraft?.pregnant == true)
        assertTrue(draft.nutritionDraft?.lactating == true)

        // Un excluyente desplaza a todo lo anterior (y su proyección tipada).
        vm.toggleStepChoice(SetupStepId.NUTRITION_ELIGIBILITY, "unknown")
        advanceUntilIdle()

        val after = vm.state.value.draft
        assertEquals(setOf("unknown"), after.selectedValues(SetupStepId.NUTRITION_ELIGIBILITY))
        assertTrue(after.nutritionDraft?.eligibilityUnknown == true)
        assertTrue(after.nutritionDraft?.pregnant != true)
        assertTrue(after.nutritionDraft?.lactating != true)
    }

    @Test
    fun ringsDiscomfortKeepsNonCatalogOccurrencesAndExclusiveDisplaces() = runTest {
        val vm = vm()
        vm.initialize(SetupWizardMode.FULL)
        advanceUntilIdle()

        // Ocurrencias declaradas a mano (no del catálogo): se conservan.
        vm.toggleStepChoice(SetupStepId.RINGS_DISCOMFORT, "manual_neck_pain")
        vm.toggleStepChoice(SetupStepId.RINGS_DISCOMFORT, "manual_low_back")
        advanceUntilIdle()

        val draft = vm.state.value.draft
        assertEquals(
            setOf("manual_neck_pain", "manual_low_back"),
            draft.selectedValues(SetupStepId.RINGS_DISCOMFORT),
        )
        assertEquals(
            listOf("manual_low_back", "manual_neck_pain"),
            draft.ringsAnswers?.discomfortIds,
        )

        vm.toggleStepChoice(SetupStepId.RINGS_DISCOMFORT, "none")
        advanceUntilIdle()

        val after = vm.state.value.draft
        assertEquals(setOf("none"), after.selectedValues(SetupStepId.RINGS_DISCOMFORT))
        assertEquals(SetupDiscomfortState.NONE, after.ringsAnswers?.discomfortState)
        assertEquals(emptyList<String>(), after.ringsAnswers?.discomfortIds)
    }

    // --- Fakes mínimos: mismos contratos que SetupWizardPorts ---------------

    private class MemoryPersistence : SetupWizardPersistence {
        private var row: SetupDraft? = null

        override suspend fun load(draftId: String): SetupDraft? =
            row?.takeIf { it.draftId == draftId }

        override suspend fun save(
            draftId: String,
            payloadJson: String,
            revision: Long,
            catalogRevision: String?,
        ): SetupDraft {
            val model = SetupDraft(draftId, payloadJson, revision, catalogRevision, 0L)
            row = model
            return model
        }

        override suspend fun discard(draftId: String) {
            row = null
        }

        override suspend fun listRecoverable(): List<SetupDraftCandidate> = emptyList()
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

package com.example.kpkn.screens.onboarding

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.onboarding.SetupDraft
import com.example.kpkn.data.onboarding.SetupDraftCandidate
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * El prólogo «ANTES DE EMPEZAR» del alta: en un alta FULL sin tocar sale antes de la pantalla de arranque, «Vamos» lleva a esa
 * pantalla, y al retomar un borrador (RESUME) no sale.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h780dp-xxhdpi")
class WizardPrologueTest {

    @get:Rule
    val rule = createComposeRule()

    @Before
    fun setUp() {
        // Sin animaciones del sistema, la pantalla de arranque muestra su cuadro final y no deja fotogramas pendientes.
        val app = ApplicationProvider.getApplicationContext<Application>()
        android.provider.Settings.Global.putFloat(
            app.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            0f,
        )
    }

    /** Monta el anfitrión del wizard como lo hace la app; con movimiento reducido el prólogo sale ya en su cuadro final. */
    private fun mount(mode: SetupWizardMode, draftId: String): SetupWizardViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = SetupWizardViewModel(
            application = app,
            savedStateHandle = SavedStateHandle(),
            persistence = NoPersistence,
            environment = StubEnvironment,
            materializeOverride = SetupWizardMaterializer { SetupPreview(null, null) },
        )
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                SetupWizardScreen(mode = mode, draftId = draftId, onDone = {}, onCancel = {}, viewModel = vm)
            }
        }
        return vm
    }

    private fun exists(tag: String): Boolean = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun enUnAltaNuevaElPrologoVaAntesDeLaPantallaDeArranqueYVamosLleva() {
        mount(SetupWizardMode.FULL, draftId = "prologue-alta-nueva")
        rule.waitUntil(timeoutMillis = 10_000) { exists("setup-prologue") }
        assertFalse("el arranque espera a que la persona diga «Vamos»", exists("setup-intro"))
        rule.onNodeWithTag("setup-prologue-start").assertIsEnabled()

        rule.onNodeWithTag("setup-prologue-start").performClick()
        rule.waitUntil(timeoutMillis = 10_000) { exists("setup-intro") }
        assertFalse("el prólogo se cierra al pasar a la pantalla de arranque", exists("setup-prologue"))
    }

    @Test
    fun alRetomarUnBorradorNoSaleElPrologo() {
        val vm = mount(SetupWizardMode.RESUME, draftId = "prologue-retomar")
        rule.waitUntil(timeoutMillis = 10_000) { !vm.state.value.isLoading }
        rule.waitForIdle()
        assertFalse("retomar un borrador no muestra el prólogo", exists("setup-prologue"))
    }

    // Puertos de prueba: un alta sin nada guardado y sin catálogo externo, como en las demás pruebas del wizard.
    private object NoPersistence : SetupWizardPersistence {
        override suspend fun load(draftId: String): SetupDraft? = null
        override suspend fun save(
            draftId: String,
            payloadJson: String,
            revision: Long,
            catalogRevision: String?,
        ): SetupDraft = SetupDraft(draftId, payloadJson, revision, catalogRevision, 0L)

        override suspend fun discard(draftId: String) = Unit
        override suspend fun listRecoverable(): List<SetupDraftCandidate> = emptyList()
    }

    private object StubEnvironment : SetupWizardEnvironment {
        override val settings: Settings = Settings()
        override suspend fun awaitReady() = Unit
        override fun activeProgramId(): String? = null
        override fun activeNutritionPlanId(): String? = null
        override fun activeNutritionPlan(): NutritionPlan? = null
        override fun nutritionPlan(id: String): NutritionPlan? = null
        override fun hasInitialRecoveryEvidence(): Boolean = false
        override suspend fun refreshBodyProgress() = Unit
    }
}

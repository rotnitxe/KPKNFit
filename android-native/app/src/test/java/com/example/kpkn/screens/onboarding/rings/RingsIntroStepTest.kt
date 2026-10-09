package com.example.kpkn.screens.onboarding.rings

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.onboarding.SetupDraft
import com.example.kpkn.data.onboarding.SetupDraftCandidate
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.screens.onboarding.SetupPreview
import com.example.kpkn.screens.onboarding.SetupRingsStepContent
import com.example.kpkn.screens.onboarding.SetupWizardDraft
import com.example.kpkn.screens.onboarding.SetupWizardEnvironment
import com.example.kpkn.screens.onboarding.SetupWizardMaterializer
import com.example.kpkn.screens.onboarding.SetupWizardPersistence
import com.example.kpkn.screens.onboarding.SetupWizardState
import com.example.kpkn.screens.onboarding.SetupWizardViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Paso `RINGS_INTRO`: tocar un anillo cambia la línea, «Simular» baja la torre a los valores de la sesión, la línea de
 * tiempo solo existe con la sesión, el deslizador salta al soltar y el paso no escribe nada en el borrador.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class RingsIntroStepTest {

    @get:Rule
    val rule = createComposeRule()

    private val store = ViewModelStore()
    private lateinit var vm: SetupWizardViewModel

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        vm = SetupWizardViewModel(
            application = app,
            savedStateHandle = SavedStateHandle(),
            persistence = NoPersistence,
            environment = StubEnvironment,
            materializeOverride = SetupWizardMaterializer { SetupPreview(null, null) },
        ).also { store.put("rings-intro-step-vm", it) }
    }

    @After
    fun tearDown() {
        store.clear()
    }

    @Composable
    private fun Page() {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SetupRingsStepContent(
                step = SetupStepId.RINGS_INTRO,
                state = SetupWizardState(draft = SetupWizardDraft()),
                vm = vm,
            )
        }
    }

    @Test
    fun sinToqueLaLineaInvitaATocarUnAnillo() {
        rule.setContent { RingsIntroStep() }
        rule.onNodeWithText("Toca un anillo para ver qué mide.").assertExists()
    }

    @Test
    fun tocarColumnaCambiaLaLinea() {
        rule.setContent { RingsIntroStep() }
        rule.onNodeWithContentDescription("Columna 100 %").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("La carga que acumula tu espalda.").assertExists()
    }

    @Test
    fun simularBajaLosAnillosALosValoresDeLaSesion() {
        rule.setContent { RingsIntroStep() }
        rule.onNodeWithTag("rings-intro-simulate").performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Columna 55 %, Músculos 45 %, Energía 70 %").assertExists()
        rule.onNodeWithText("Quitar el entreno").assertExists()
    }

    @Test
    fun laLineaDeTiempoExisteSoloConLaSesion() {
        rule.setContent { RingsIntroStep() }
        rule.onNodeWithTag("rings-intro-timeline").assertDoesNotExist()
        rule.onNodeWithTag("rings-intro-simulate").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("rings-intro-timeline").assertExists()
        rule.onNodeWithText("0 h").assertExists()
    }

    @Test
    fun arrastrarLaLineaMueveLaHora() {
        rule.setContent { RingsIntroStep() }
        rule.onNodeWithTag("rings-intro-simulate").performClick()
        rule.onNodeWithTag("rings-intro-timeline").performTouchInput {
            // Termina en el borde derecho: la línea satura en 72 h (el 95 % del ancho quedaba en ~71 h).
            swipe(Offset(width * 0.05f, height / 2f), Offset(width - 1f, height / 2f), durationMillis = 400)
        }
        rule.waitForIdle()
        rule.onNodeWithText("72 h").assertExists()
    }

    @Test
    fun elDeslizadorSaltaAlValorAlSoltarYElAnilloLoMuestra() {
        rule.setContent { RingsIntroStep() }
        rule.onNodeWithContentDescription("Columna 100 %").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("rings-intro-slider").performSemanticsAction(SemanticsActions.SetProgress) { it(30f) }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Columna 30 %, Músculos 100 %, Energía 100 %").assertExists()
    }

    @Test
    fun elPasoNoEscribeNadaEnElBorrador() {
        rule.setContent { Page() }
        rule.waitForIdle()
        val before = vm.state.value.draft
        rule.onNodeWithTag("rings-intro-simulate").performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Músculos 45 %").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("rings-intro-slider").performSemanticsAction(SemanticsActions.SetProgress) { it(20f) }
        rule.waitForIdle()
        assertEquals(before, vm.state.value.draft)
    }

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

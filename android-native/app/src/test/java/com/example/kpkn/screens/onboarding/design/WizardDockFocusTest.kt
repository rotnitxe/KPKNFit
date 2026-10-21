package com.example.kpkn.screens.onboarding.design

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.onboarding.SetupDraft
import com.example.kpkn.data.onboarding.SetupDraftCandidate
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.screens.onboarding.SetupBasicsStepContent
import com.example.kpkn.screens.onboarding.SetupPreview
import com.example.kpkn.screens.onboarding.SetupWizardDraft
import com.example.kpkn.screens.onboarding.SetupWizardEnvironment
import com.example.kpkn.screens.onboarding.SetupWizardMaterializer
import com.example.kpkn.screens.onboarding.SetupWizardPersistence
import com.example.kpkn.screens.onboarding.SetupWizardState
import com.example.kpkn.screens.onboarding.SetupWizardViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * El check del wizard suelta el foco, cierra el teclado y solo entonces avisa; con el botón deshabilitado no hace nada.
 * Esas llamadas se comprueban con un foco y un teclado que solo anotan lo que reciben: el foco real de Robolectric no es
 * fiable (tras quitarlo, el campo vuelve a quedar enfocado). En el paso 1, «Siguiente» del alias pasa el foco a la fecha y
 * «Listo» de la fecha cierra el teclado sin confirmar el paso.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
class WizardDockFocusTest {

    @get:Rule
    val rule = createComposeRule()

    private val store = ViewModelStore()
    private lateinit var vm: SetupWizardViewModel
    private val wizardState = SetupWizardState(draft = SetupWizardDraft())

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        vm = SetupWizardViewModel(
            application = app,
            savedStateHandle = SavedStateHandle(),
            persistence = NoPersistence,
            environment = StubEnvironment,
            materializeOverride = SetupWizardMaterializer { SetupPreview(null, null) },
        ).also { store.put("wizard-dock-focus-vm", it) }
    }

    @After
    fun tearDown() {
        store.clear()
    }

    // ── Check del wizard (WizardDock) ──────────────────────────────────────

    @Test
    fun elCheckSueltaElFocoYElTecladoAntesDeAvisar() {
        val calls = mutableListOf<String>()
        val focus = RecordingFocusManager(calls)
        val keyboard = RecordingKeyboard(calls)
        rule.setContent {
            DockWithRecorders(focus = focus, keyboard = keyboard, enabled = true, onConfirm = { calls.add("onClick") })
        }
        rule.onNodeWithTag("setup-continue").performClick()
        rule.waitForIdle()
        assertEquals(listOf("clearFocus(force=true)", "hide", "onClick"), calls)
    }

    @Test
    fun elCheckDeshabilitadoNoAvisaNiSueltaElFoco() {
        val calls = mutableListOf<String>()
        val focus = RecordingFocusManager(calls)
        val keyboard = RecordingKeyboard(calls)
        rule.setContent {
            DockWithRecorders(focus = focus, keyboard = keyboard, enabled = false, onConfirm = { calls.add("onClick") })
        }
        rule.onNodeWithTag("setup-continue").performClick()
        rule.waitForIdle()
        assertTrue(calls.isEmpty())
    }

    // ── Paso 1: alias y fecha de nacimiento ────────────────────────────────

    @Test
    fun siguienteDelAliasPasaElFocoALaFecha() {
        rule.setContent { SetupBasicsStepContent(step = SetupStepId.NAME, state = wizardState, vm = vm) }
        rule.onNodeWithTag("setup-name").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("setup-name").assertIsFocused()
        rule.onNodeWithTag("setup-name").performImeAction()
        rule.waitForIdle()
        rule.onNodeWithTag("setup-birth-date").assertIsFocused()
    }

    @Test
    fun listoDeLaFechaCierraElFocoSinConfirmarElPaso() {
        rule.setContent { SetupBasicsStepContent(step = SetupStepId.NAME, state = wizardState, vm = vm) }
        rule.onNodeWithTag("setup-birth-date").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("setup-birth-date").assertIsFocused()
        rule.onNodeWithTag("setup-birth-date").performImeAction()
        rule.waitForIdle()
        rule.onNodeWithTag("setup-birth-date").assertIsNotFocused()
    }

    /** Un `FocusManager` que solo anota lo que se le pide. */
    private class RecordingFocusManager(private val calls: MutableList<String>) : FocusManager {
        override fun clearFocus(force: Boolean) {
            calls.add("clearFocus(force=$force)")
        }

        override fun moveFocus(focusDirection: FocusDirection): Boolean = false
    }

    /** Un teclado que solo anota `show` y `hide`. */
    private class RecordingKeyboard(private val calls: MutableList<String>) : SoftwareKeyboardController {
        override fun show() {
            calls.add("show")
        }

        override fun hide() {
            calls.add("hide")
        }
    }

    /** El check con el foco y el teclado de prueba en el ámbito que el dock lee. */
    @Composable
    private fun DockWithRecorders(
        focus: FocusManager,
        keyboard: SoftwareKeyboardController,
        enabled: Boolean,
        onConfirm: () -> Unit,
    ) {
        CompositionLocalProvider(
            LocalFocusManager provides focus,
            LocalSoftwareKeyboardController provides keyboard,
        ) {
            WizardDock(enabled = enabled, label = "Continuar", onClick = onConfirm)
        }
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

package com.example.kpkn.screens.onboarding.rings

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
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
import com.example.kpkn.domain.auge.RingsIntroSimulator
import com.example.kpkn.domain.auge.RingsIntroState
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.screens.onboarding.SetupPreview
import com.example.kpkn.screens.onboarding.SetupRingsStepContent
import com.example.kpkn.screens.onboarding.SetupWizardDraft
import com.example.kpkn.screens.onboarding.SetupWizardEnvironment
import com.example.kpkn.screens.onboarding.SetupWizardMaterializer
import com.example.kpkn.screens.onboarding.SetupWizardPersistence
import com.example.kpkn.screens.onboarding.SetupWizardState
import com.example.kpkn.screens.onboarding.SetupWizardViewModel
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val ENERGY_SENTENCE =
    "Si no coincide con cómo te sientes, ajústalo: KPKN usa tus ajustes para afinar tu ritmo de recuperación con cada entreno."

/**
 * Intro de RINGS como overlay: el umbral con «Siguiente», los tres anillos (tocar uno cambia su línea), «Pruébalo» con sus
 * invitaciones en orden, «Saltar» y «Empezar». El paso no escribe nada en el borrador. Salvo el recorrido, las pruebas van con
 * movimiento reducido, así que no dependen del reloj.
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

    /** El paso con su overlay encima, como lo pinta el anfitrión mientras el cursor está en RINGS_INTRO. */
    @Composable
    private fun Page() {
        CompositionLocalProvider(LocalWizardReducedMotion provides true) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SetupRingsStepContent(
                    step = SetupStepId.RINGS_INTRO,
                    state = SetupWizardState(draft = SetupWizardDraft()),
                    vm = vm,
                )
            }
            RingsIntroOverlay(onContinue = {}, onBack = {})
        }
    }

    /** El overlay con movimiento reducido: sin caídas ni pistas, y «Siguiente» habilitado desde el primer cuadro. */
    private fun mountOverlay(onContinue: () -> Unit = {}) {
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                RingsIntroOverlay(onContinue = onContinue, onBack = {})
            }
        }
    }

    /** «Siguiente» del umbral o «Ver cómo se mueven» del segundo acto. */
    private fun next() {
        rule.onNodeWithTag("rings-intro-next").performClick()
        rule.waitForIdle()
    }

    @Test
    fun elOverlayEmpiezaConElUmbralYSiguienteHabilitado() {
        mountOverlay()
        rule.onNodeWithTag("rings-intro-overlay").assertExists()
        rule.onNodeWithText("Un último paso para comenzar").assertExists()
        rule.onNodeWithTag("rings-intro-next").assertIsEnabled()
    }

    @Test
    fun siguienteAvanzaLosTiempos() {
        mountOverlay()
        next()
        rule.onNodeWithText("Ver cómo se mueven").assertExists()
        rule.onNodeWithText("Un último paso para comenzar").assertDoesNotExist()
        next()
        rule.onNodeWithText("Toca para simular un entreno pesado").assertExists()
    }

    @Test
    fun saltarMuestraEmpezarConLaFraseDeCierre() {
        mountOverlay()
        rule.onNodeWithTag("rings-intro-skip").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("rings-intro-start").assertExists()
        rule.onNodeWithText("Con unas preguntas rápidas calculamos cómo llegas hoy.").assertExists()
        rule.onNodeWithTag("rings-intro-skip").assertDoesNotExist()
    }

    @Test
    fun empezarConfirmaElPaso() {
        var confirmed = 0
        mountOverlay(onContinue = { confirmed++ })
        rule.onNodeWithTag("rings-intro-skip").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("rings-intro-start").performClick()
        rule.waitUntil(timeoutMillis = 5_000) { confirmed == 1 }
    }

    @Test
    fun conMovimientoReducidoLosTresAnillosSeLeenALaVez() {
        mountOverlay()
        next()
        rule.onNodeWithText("Cuánto se han recuperado tus músculos.").assertExists()
        rule.onNodeWithText("Tu reserva para entrenar fuerte.").assertExists()
        rule.onNodeWithText("La carga que acumula tu espalda.").assertExists()
    }

    @Test
    fun tocarUnAnilloEnElSegundoActoCambiaLaLinea() {
        // Con el reloj a mano: «Siguiente» se activa a los 1,2 s, y el recorrido (1,6 s por anillo) se para al tocar.
        rule.mainClock.autoAdvance = false
        rule.setContent { RingsIntroOverlay(onContinue = {}, onBack = {}) }
        rule.mainClock.advanceTimeBy(1_300)
        rule.onNodeWithTag("rings-intro-next").assertIsEnabled()
        rule.onNodeWithTag("rings-intro-next").performClick()
        rule.mainClock.advanceTimeBy(600)
        rule.onNodeWithContentDescription("Columna 100 %").performClick()
        rule.mainClock.advanceTimeBy(300)
        // La leyenda siempre muestra las tres frases: lo que cambia al tocar es qué fila queda destacada.
        rule.onNodeWithTag("rings-intro-legend-COLUMNA")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Destacado"))
    }

    @Test
    fun simularBajaLosAnillosALosValoresDeLaSesion() {
        mountOverlay()
        next()
        next()
        rule.onNodeWithTag("rings-intro-simulate").performClick()
        rule.waitForIdle()
        // Los valores que muestra la torre son los del motor para la sesión de referencia (ver RingsIntroSimulatorTest).
        val values = RingsIntroSimulator.valuesAt(RingsIntroState(heavySession = true))
        rule.onNodeWithContentDescription(
            "Columna ${values.columna} %, Músculos ${values.musculos} %, Energía ${values.energia} %",
        ).assertExists()
        rule.onNodeWithTag("rings-intro-timeline").assertExists()
    }

    @Test
    fun arrastrarMasDeVeinticuatroHorasPasaAlAjusteYSoltarCierraConLaFrase() {
        mountOverlay()
        next()
        next()
        rule.onNodeWithTag("rings-intro-simulate").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("rings-intro-timeline").performTouchInput {
            // Termina en el borde derecho: la línea satura en 72 h.
            swipe(Offset(width * 0.05f, height / 2f), Offset(width - 1f, height / 2f), durationMillis = 400)
        }
        rule.waitForIdle()
        // Al pasar de 24 h la invitación queda hecha: se retira la línea de tiempo y aparece el deslizador.
        rule.onNodeWithTag("rings-intro-timeline").assertDoesNotExist()
        rule.onNodeWithTag("rings-intro-slider").assertExists()
        rule.onNodeWithTag("rings-intro-slider").performSemanticsAction(SemanticsActions.SetProgress) { it(30f) }
        rule.waitForIdle()
        rule.onNodeWithText(ENERGY_SENTENCE).assertExists()
        rule.onNodeWithTag("rings-intro-start").assertExists()
    }

    @Test
    fun elPasoNoEscribeNadaEnElBorrador() {
        rule.setContent { Page() }
        rule.waitForIdle()
        val before = vm.state.value.draft
        next()
        next()
        rule.onNodeWithTag("rings-intro-simulate").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("rings-intro-timeline").performTouchInput {
            swipe(Offset(width * 0.05f, height / 2f), Offset(width - 1f, height / 2f), durationMillis = 400)
        }
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

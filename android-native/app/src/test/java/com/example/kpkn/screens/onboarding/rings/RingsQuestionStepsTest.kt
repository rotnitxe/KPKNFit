package com.example.kpkn.screens.onboarding.rings

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.InitialRecoveryIntensity
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.RingsLiftPattern
import com.example.kpkn.data.models.RingsMuscleGroup
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.onboarding.SetupDraft
import com.example.kpkn.data.onboarding.SetupDraftCandidate
import com.example.kpkn.domain.onboarding.RingsMainLiftsState
import com.example.kpkn.domain.onboarding.RingsSessionDaysState
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.screens.onboarding.SetupPreview
import com.example.kpkn.screens.onboarding.SetupRingsAnswers
import com.example.kpkn.screens.onboarding.SetupRingsStepContent
import com.example.kpkn.screens.onboarding.SetupWizardEnvironment
import com.example.kpkn.screens.onboarding.SetupWizardMaterializer
import com.example.kpkn.screens.onboarding.SetupWizardMode
import com.example.kpkn.screens.onboarding.SetupWizardPersistence
import com.example.kpkn.screens.onboarding.SetupWizardViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Las cinco pantallas de las preguntas de RINGS con el ViewModel real: los días y sus exclusivos, la cuadrícula de ejercicios, la
 * regla de series (y «No lo sé», que escribe `null`), la intensidad de elección única y las sensaciones (una sola por fila, y
 * «Prefiero no decirlo» deja ese canal vacío).
 *
 * El ViewModel no escribe hasta `initialize` (carga el borrador); por eso cada prueba espera a que `isLoading` baje antes de
 * tocar nada.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class RingsQuestionStepsTest {

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
        ).also { store.put("rings-question-steps-vm", it) }
        // Solo RINGS: el borrador de este modo tiene la ruta de RINGS completa y ningún otro bloque que cargar.
        vm.initialize(mode = SetupWizardMode.RINGS_ONLY)
    }

    @After
    fun tearDown() {
        store.clear()
    }

    /** Un paso de RINGS con el estado en vivo del ViewModel (cada escritura se refleja en la pantalla). */
    @Composable
    private fun Page(step: SetupStepId) {
        val state by vm.state.collectAsState()
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SetupRingsStepContent(step = step, state = state, vm = vm)
        }
    }

    /** Monta el paso y espera a que el borrador esté cargado: solo entonces el ViewModel acepta escrituras. */
    private fun showStep(step: SetupStepId) {
        rule.setContent { Page(step) }
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 10_000) { !vm.state.value.isLoading }
        rule.waitForIdle()
    }

    private fun answers(): SetupRingsAnswers? = vm.state.value.draft.ringsAnswers

    @Test
    fun losDiasYSusExclusivosEscribenEnElBorrador() {
        showStep(SetupStepId.RINGS_SESSION_DAYS)
        rule.onNodeWithTag("rings-day-0").performClick()
        rule.onNodeWithTag("rings-day-2").performClick()
        rule.waitForIdle()
        assertEquals(setOf(0, 2), answers()?.trainedDayOffsets)
        assertEquals(RingsSessionDaysState.DECLARED, answers()?.sessionDaysState)
        rule.onNodeWithTag("rings-day-2").assertIsOn()

        // «No entrené» vacía los días y deja sus dos símbolos exclusivos.
        rule.onNodeWithTag("rings-days-none").performClick()
        rule.waitForIdle()
        assertEquals(RingsSessionDaysState.NONE, answers()?.sessionDaysState)
        assertTrue(answers()?.trainedDayOffsets.orEmpty().isEmpty())
        rule.onNodeWithTag("rings-day-0").assertIsOff()
        rule.onNodeWithTag("rings-days-none").assertIsOn()

        // «No lo recuerdo» sustituye a «No entrené»; tocar un día apaga «No lo recuerdo».
        rule.onNodeWithTag("rings-days-unknown").performClick()
        rule.waitForIdle()
        assertEquals(RingsSessionDaysState.UNKNOWN, answers()?.sessionDaysState)
        rule.onNodeWithTag("rings-days-none").assertIsOff()
        rule.onNodeWithTag("rings-days-unknown").assertIsOn()
        rule.onNodeWithTag("rings-day-1").performClick()
        rule.waitForIdle()
        assertEquals(setOf(1), answers()?.trainedDayOffsets)
        rule.onNodeWithTag("rings-days-unknown").assertIsOff()
    }

    @Test
    fun laCuadriculaDeEjerciciosTieneSusExclusivos() {
        showStep(SetupStepId.RINGS_MAIN_LIFTS)
        rule.onNodeWithTag("rings-lift-SQUAT").performClick()
        rule.onNodeWithTag("rings-lift-BENCH").performClick()
        rule.waitForIdle()
        assertEquals(setOf(RingsLiftPattern.SQUAT, RingsLiftPattern.BENCH), answers()?.mainLifts)
        assertEquals(RingsMainLiftsState.DECLARED, answers()?.mainLiftsState)
        rule.onNodeWithTag("rings-lift-SQUAT").assertIsOn()

        rule.onNodeWithTag("rings-lift-none").performClick()
        rule.waitForIdle()
        assertEquals(RingsMainLiftsState.NONE, answers()?.mainLiftsState)
        assertTrue(answers()?.mainLifts.orEmpty().isEmpty())
        rule.onNodeWithTag("rings-lift-SQUAT").assertIsOff()

        rule.onNodeWithTag("rings-lift-unknown").performClick()
        rule.waitForIdle()
        assertEquals(RingsMainLiftsState.UNKNOWN, answers()?.mainLiftsState)
        rule.onNodeWithTag("rings-lift-none").assertIsOff()
        rule.onNodeWithTag("rings-lift-unknown").assertIsOn()
    }

    @Test
    fun laReglaDeSeriesDePiernasEscribeElSetterYNoLoSeEscribeNull() {
        showStep(SetupStepId.RINGS_GROUP_SETS)
        // Tocar la celda de Piernas la elige para el editor; su regla escribe las series de ese grupo.
        rule.onNodeWithTag("rings-group-LEGS").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("rings-group-ruler-LEGS").performSemanticsAction(SemanticsActions.SetProgress) { it(12f) }
        rule.waitForIdle()
        assertEquals(12, answers()?.groupSets?.get(RingsMuscleGroup.LEGS))
        rule.onNodeWithContentDescription("Piernas: 12 series").assertExists()

        // «No lo sé» en Pecho escribe `null`: el grupo queda sin cifra y marcado como desconocido.
        rule.onNodeWithTag("rings-group-CHEST").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("rings-group-unknown-CHEST").performClick()
        rule.waitForIdle()
        assertNull(answers()?.groupSets?.get(RingsMuscleGroup.CHEST))
        assertTrue(RingsMuscleGroup.CHEST in answers()?.groupSetsUnknown.orEmpty())
        rule.onNodeWithTag("rings-group-unknown-CHEST").assertIsOn()
    }

    @Test
    fun laIntensidadEsDeEleccionUnicaYEscribeSuNivel() {
        showStep(SetupStepId.RINGS_INTENSITY)
        rule.onNodeWithTag("rings-intensity-HARD").performClick()
        rule.onNodeWithTag("rings-intensity-EASY").performClick()
        rule.waitForIdle()
        assertEquals(InitialRecoveryIntensity.EASY, answers()?.intensityLevel)
        rule.onNodeWithTag("rings-intensity-EASY").assertIsSelected()
        rule.onNodeWithTag("rings-intensity-HARD").assertIsNotSelected()
    }

    @Test
    fun laEscalaDeEnergiaEsDeEleccionUnicaYPreferirNoDecirloDejaElCanalVacio() {
        showStep(SetupStepId.RINGS_FEELINGS)
        rule.onNodeWithTag("rings-feeling-MUSCULAR-3").performClick()
        rule.onNodeWithTag("rings-feeling-ENERGY-2").performClick()
        rule.onNodeWithTag("rings-feeling-ENERGY-4").performClick()
        rule.waitForIdle()
        assertEquals(4, answers()?.energy)
        // Los otros canales no se tocan al elegir en Energía.
        assertEquals(3, answers()?.muscleFeeling)
        rule.onNodeWithTag("rings-feeling-ENERGY-4").assertIsSelected()
        rule.onNodeWithContentDescription("Energía: Bien").assertIsNotSelected()
        rule.onNodeWithTag("rings-feeling-MUSCULAR-3").assertIsSelected()

        rule.onNodeWithTag("rings-feeling-skip-MUSCULAR").performClick()
        rule.waitForIdle()
        assertNull(answers()?.muscleFeeling)
        assertEquals(4, answers()?.energy)
        rule.onNodeWithTag("rings-feeling-MUSCULAR-3").assertIsNotSelected()
    }

    @Test
    fun laVentanaDeLasSeriesNombraElDiaMasAntiguoDeLas72Horas() {
        // 9 de octubre de 2026 es viernes: a dos días, miércoles.
        val today = LocalDate.of(2026, 10, 9)
        assertEquals("Desde hoy.", ringsWindowStartLabel(0, today))
        assertEquals("Desde ayer.", ringsWindowStartLabel(1, today))
        assertEquals("Desde el miércoles.", ringsWindowStartLabel(2, today))
        assertNull(ringsWindowStartLabel(null, today))
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

    @Test
    fun elEditorArrancaEnElPrimerGrupoSinResponder() {
        showStep(SetupStepId.RINGS_GROUP_SETS)
        rule.onNodeWithTag("rings-group-editor").assertExists()
        rule.onNodeWithTag("rings-group-ruler-CHEST").assertExists()
        rule.onNodeWithTag("rings-group-ruler-BACK").assertDoesNotExist()
    }

    @Test
    fun alSoltarEnUnGrupoSinRespuestaLaSeleccionPasaAlSiguienteSinResponder() {
        showStep(SetupStepId.RINGS_GROUP_SETS)
        // Un toque en la regla de Pecho declara su valor; al soltar, tras 400 ms el editor pasa a Espalda.
        rule.onNodeWithTag("rings-group-ruler-CHEST").performTouchInput { click(center) }
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag("rings-group-ruler-BACK").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("rings-group-ruler-CHEST").assertDoesNotExist()
        assertEquals(10, answers()?.groupSets?.get(RingsMuscleGroup.CHEST))
    }

    @Test
    fun elPrimerYElSiguienteGrupoSinResponderSeCalculanEnElOrdenDelMosaico() {
        val sample = SetupRingsAnswers(
            groupSets = mapOf(RingsMuscleGroup.CHEST to 8),
            groupSetsUnknown = setOf(RingsMuscleGroup.BACK),
        )
        assertEquals(RingsMuscleGroup.SHOULDERS, firstUnansweredRingsGroup(sample))
        assertEquals(RingsMuscleGroup.CHEST, firstUnansweredRingsGroup(null))
        assertEquals(RingsMuscleGroup.SHOULDERS, nextUnansweredRingsGroup(RingsMuscleGroup.CHEST, sample))
        // Desde Core se da la vuelta al mosaico: Pecho y Espalda ya responden, así que queda Hombros.
        assertEquals(RingsMuscleGroup.SHOULDERS, nextUnansweredRingsGroup(RingsMuscleGroup.CORE, sample))
        val all = SetupRingsAnswers(groupSets = RingsMuscleGroup.entries.associateWith { 5 })
        assertNull(nextUnansweredRingsGroup(RingsMuscleGroup.CORE, all))
    }
}

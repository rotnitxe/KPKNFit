package com.example.kpkn.screens.onboarding

import android.app.Application
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasInsertTextAtCursorAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.WizChatMachineState
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Recorrido real del wizard: `SetupWizardScreen` (Host) + `SetupWizardViewModel`
 * + adaptadores reales de `SetupWizardPorts` sobre Room del dispositivo.
 *
 * No hay estados fabricados ni helpers de estado: la semilla de un paso se hace
 * con las APIs públicas del ViewModel (`update`/`setStepChoices`, que editan sin
 * avanzar) y las aserciones leen la `StateFlow` del VM junto a los testTags
 * reales de la UI (`setup-step-<ID>`, `setup-name`, `setup-continue`).
 *
 * Cada prueba usa un `draftId` propio `setup-qa-<UUID>` y **solo** borra ese
 * borrador en el tearDown: nunca toca la base, los borradores canónicos ni los
 * ficheros del usuario.
 */
@RunWith(AndroidJUnit4::class)
class SetupWizardJourneyUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var app: Application
    private lateinit var persistence: SetupWizardPersistence
    private val draftIds = mutableListOf<String>()

    /** Cada VM creado se registra aquí para limpiarlo (onCleared) en After. */
    private val viewModelStore = ViewModelStore()
    private var viewModelCounter = 0

    @Before
    fun setUp() {
        // PRIMERO: sólo en la app del usuario (uid 10xxx). Sin este guard, la
        // inicialización de repositorios de abajo tocaría la base antes de
        // cualquier suposición.
        Assume.assumeTrue(Process.myUid() / 100000 == 10)
        app = ApplicationProvider.getApplicationContext()
        // El entorno real (awaitReady + previews de nutrición) lee estos
        // singletons: se inicializan aquí porque el ComponentActivity de la
        // prueba no es MainActivity.
        ProgramRepository.init(app)
        NutritionRepository.init(app)
        runBlocking {
            withTimeout(SETUP_TIMEOUT_MS) { ProgramRepository.getInstance().isReady.first { it } }
        }
        persistence = realSetupWizardPersistence(app)
    }

    @After
    fun tearDown() {
        // Cierre de TODOS los VM (onCleared → viewModelScope cancelado) ANTES
        // de tocar filas: ningún job sigue vivo durante el borrado.
        viewModelStore.clear()
        val ids = draftIds.toList()
        if (ids.isEmpty()) return
        runBlocking { withTimeout(SETUP_TIMEOUT_MS) { ids.forEach { persistence.discard(it) } } }
    }

    private fun newDraftId(): String {
        val id = "setup-qa-${UUID.randomUUID()}"
        draftIds.add(id)
        return id
    }

    private fun newViewModel(): SetupWizardViewModel =
        SetupWizardViewModel(app, SavedStateHandle(), persistence, RealSetupWizardEnvironment(app.applicationContext))
            // Registrado en el store: After hace clear() → onCleared →
            // viewModelScope cancelado, sin jobs huérfanos.
            .also { viewModelStore.put("vm-${viewModelCounter++}", it) }

    /** Espera acotada y visible: si no llega a AwaitingAnswer la prueba falla. */
    private fun awaitReady(vm: SetupWizardViewModel) {
        composeRule.waitUntil(TIMEOUT_MS) {
            val state = vm.state.value
            !state.isLoading && state.machineState == WizChatMachineState.AwaitingAnswer
        }
    }

    /**
     * Punto de partida determinista: el nombre vacío deja el CTA deshabilitado
     * aunque el dispositivo traiga un usuario configurado. Se hace antes de
     * componer, porque el campo local del paso captura su valor al montarse.
     */
    private fun prepareBlankName(vm: SetupWizardViewModel) {
        awaitReady(vm)
        if (vm.state.value.draft.name.isBlank()) return
        vm.setName("")
        composeRule.waitUntil(TIMEOUT_MS) { vm.state.value.draft.name.isBlank() }
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * Espera a que el cursor llegue a [expected]. Si se agota el tiempo, FALLA
     * adjuntando el estado real del VM (currentStep, revision, machineState,
     * isSubmittingAnswer, canConfirmStep, errors, lastFailure) y el cursor
     * persistido en Room: el diagnóstico sale del propio fallo, sin tocar
     * producción, sin alargar el timeout y sin necesidad de un segundo clic.
     */
    private fun awaitStepOrDump(vm: SetupWizardViewModel, draftId: String, expected: SetupStepId) {
        try {
            composeRule.waitUntil(TIMEOUT_MS) { vm.state.value.currentStep == expected }
        } catch (timeout: Throwable) {
            // Sigue fallando (assert intacto); solo se adjunta el estado real.
            val failure = AssertionError(stepDump(vm, draftId, expected))
            failure.initCause(timeout)
            throw failure
        }
    }

    /** Estado real al timeout: VM + fila persistida (cursor y revisión). */
    private fun stepDump(vm: SetupWizardViewModel, draftId: String, expected: SetupStepId): String {
        val state = vm.state.value
        var persisted = ""
        try {
            val row = runBlocking { withTimeout(SETUP_TIMEOUT_MS) { persistence.load(draftId) } }
            persisted = if (row == null) {
                "sin fila persistida"
            } else {
                val draft = runCatching { json.decodeFromString<SetupWizardDraft>(row.payloadJson) }.getOrNull()
                if (draft == null) "payload ilegible (filaRev=${row.revision})"
                else "cursor=${draft.stepProgress.currentStepId} revision=${draft.revision} filaRev=${row.revision}"
            }
        } catch (error: Throwable) {
            persisted = "carga fallida: ${error::class.simpleName}"
        }
        return "timeout esperando currentStep=$expected | vm[" +
            "currentStep=${state.currentStep}" +
            " revision=${state.draft.revision}" +
            " machineState=${state.machineState}" +
            " isSubmittingAnswer=${state.isSubmittingAnswer}" +
            " canConfirmStep=${state.canConfirmStep}" +
            " errors=${state.errors}" +
            " lastFailure=${state.lastFailure}" +
            "] persisted[$persisted]"
    }

    // ─── Nombre → Continuar → edad, y Atrás sin perder el nombre ──────────────

    @Test
    fun nameInputContinueShowsAgeAndBackKeepsTheName() {
        val draftId = newDraftId()
        val vm = newViewModel()
        vm.initialize(SetupWizardMode.FULL, draftId = draftId)
        prepareBlankName(vm)

        composeRule.setContent {
            SetupWizardScreen(
                mode = SetupWizardMode.FULL,
                draftId = draftId,
                onDone = {},
                onCancel = {},
                viewModel = vm,
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("setup-step-NAME").assertIsDisplayed()
        composeRule.onNodeWithTag(NAME_FIELD).assertIsDisplayed()

        // CTA inválido: rol de botón y estado deshabilitado exactos.
        composeRule.onNodeWithTag(CTA).assertIsNotEnabled()
        composeRule.onNode(
            hasTestTag(CTA) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button),
        ).assertExists()

        composeRule.onNode(hasInsertTextAtCursorAction()).performTextInput(TYPED_NAME)
        composeRule.waitUntil(TIMEOUT_MS) { vm.state.value.draft.name == TYPED_NAME }

        composeRule.onNodeWithTag(CTA).assertIsEnabled()
        composeRule.onNodeWithTag(CTA).performClick()
        awaitStepOrDump(vm, draftId, SetupStepId.AGE)

        // La edad aparece sin conflictos: sin el campo de nombre y sin errores.
        composeRule.onNodeWithTag("setup-step-AGE").assertIsDisplayed()
        composeRule.onNodeWithTag("setup-step-NAME").assertDoesNotExist()
        composeRule.onNodeWithTag(NAME_FIELD).assertDoesNotExist()
        assertTrue("sin conflicto, errors=${vm.state.value.errors}", vm.state.value.errors.isEmpty())

        composeRule.onNodeWithContentDescription(BACK_LABEL).performClick()
        awaitStepOrDump(vm, draftId, SetupStepId.NAME)

        assertEquals(TYPED_NAME, vm.state.value.draft.name)
        composeRule.onNodeWithTag(NAME_FIELD).assertIsDisplayed()
        composeRule.onNodeWithText(TYPED_NAME).assertIsDisplayed()
    }

    // ─── Guardar y salir devuelve true y un VM nuevo reanuda exacto ───────────

    @Test
    fun saveAndExitNavigatesOnlyWhenItReturnsTrueAndResumeRestoresTheName() {
        val draftId = newDraftId()
        val vm = newViewModel()
        vm.initialize(SetupWizardMode.FULL, draftId = draftId)
        prepareBlankName(vm)

        val cancelled = AtomicInteger(0)
        composeRule.setContent {
            SetupWizardScreen(
                mode = SetupWizardMode.FULL,
                draftId = draftId,
                onDone = {},
                onCancel = { cancelled.incrementAndGet() },
                viewModel = vm,
            )
        }
        composeRule.waitForIdle()

        composeRule.onNode(hasInsertTextAtCursorAction()).performTextInput(RESUME_NAME)
        composeRule.waitUntil(TIMEOUT_MS) { vm.state.value.draft.name == RESUME_NAME }

        // Salir → diálogo → «Guardar y salir» solo navega si saveAndExit() es true.
        composeRule.onNodeWithText(EXIT_LABEL).performClick()
        composeRule.waitUntil(TIMEOUT_MS) { vm.state.value.dialog == SetupWizardDialog.EXIT }
        composeRule.onNodeWithText(SAVE_AND_EXIT_LABEL).performClick()
        composeRule.waitUntil(TIMEOUT_MS) { cancelled.get() == 1 }
        assertEquals(RESUME_NAME, vm.state.value.draft.name)

        // Nueva instancia del VM con el mismo id en modo RESUME: restaura exacto.
        val resumed = newViewModel()
        resumed.initialize(SetupWizardMode.RESUME, draftId = draftId)
        composeRule.waitUntil(TIMEOUT_MS) {
            val state = resumed.state.value
            !state.isLoading && state.machineState == WizChatMachineState.AwaitingAnswer
        }
        assertEquals(RESUME_NAME, resumed.state.value.draft.name)
        assertEquals(SetupStepId.NAME, resumed.state.value.currentStep)
        assertEquals(0, resumed.state.value.errors.size)
    }

    // ─── Descartar un borrador normal: confirmación y alcance ─────────────────

    /**
     * Un borrador normal sí puede descartarse: Salir → «Descartar borrador» →
     * diálogo DISCARD existente. Cancelar conserva la respuesta y la fila;
     * confirmar borra SOLO ese draft (los demás siguen intactos) y la navegación
     * ocurre únicamente después de que el borrado se persistió.
     */
    @Test
    fun discardNormalDraftNeedsConfirmationAndOnlyDeletesItsOwnRow() {
        val draftId = newDraftId()
        val vm = newViewModel()
        vm.initialize(SetupWizardMode.FULL, draftId = draftId)
        prepareBlankName(vm)

        val done = AtomicInteger(0)
        val cancelled = AtomicInteger(0)
        composeRule.setContent {
            SetupWizardScreen(
                mode = SetupWizardMode.FULL,
                draftId = draftId,
                onDone = { done.incrementAndGet() },
                onCancel = { cancelled.incrementAndGet() },
                viewModel = vm,
            )
        }
        composeRule.waitForIdle()

        // Respuesta propia (QA10) escrita con la API pública y persistida.
        vm.setName(QA10_NAME)
        composeRule.waitUntil(TIMEOUT_MS) { vm.state.value.draft.name == QA10_NAME }
        assertNotNull(
            "la respuesta debe estar en la fila",
            runBlocking { withTimeout(SETUP_TIMEOUT_MS) { persistence.load(draftId) } },
        )
        val rowsBefore = runBlocking { withTimeout(SETUP_TIMEOUT_MS) { persistence.listRecoverable() } }
            .map { it.draftId }

        // Salir → Descartar → Cancelar: conserva respuesta y fila.
        composeRule.onNodeWithText(EXIT_LABEL).performClick()
        composeRule.waitUntil(TIMEOUT_MS) { vm.state.value.dialog == SetupWizardDialog.EXIT }
        composeRule.onNodeWithText(DISCARD_ACTION_LABEL).performClick()
        composeRule.waitUntil(TIMEOUT_MS) { vm.state.value.dialog == SetupWizardDialog.DISCARD }
        composeRule.onNodeWithText(KEEP_LABEL).performClick()
        composeRule.waitUntil(TIMEOUT_MS) { vm.state.value.dialog == SetupWizardDialog.NONE }

        assertEquals(QA10_NAME, vm.state.value.draft.name)
        assertNotNull(
            "cancelar el descarte conserva la fila",
            runBlocking { withTimeout(SETUP_TIMEOUT_MS) { persistence.load(draftId) } },
        )
        assertEquals("no navega al cancelar", 0, cancelled.get())
        assertEquals("no activa nada", 0, done.get())

        // Repetir → Confirmar: borra SOLO su draft y navega tras persistir.
        composeRule.onNodeWithText(EXIT_LABEL).performClick()
        composeRule.waitUntil(TIMEOUT_MS) { vm.state.value.dialog == SetupWizardDialog.EXIT }
        composeRule.onNodeWithText(DISCARD_ACTION_LABEL).performClick()
        composeRule.waitUntil(TIMEOUT_MS) { vm.state.value.dialog == SetupWizardDialog.DISCARD }
        composeRule.onNodeWithText(DISCARD_CONFIRM_LABEL).performClick()
        composeRule.waitUntil(TIMEOUT_MS) { cancelled.get() == 1 }

        assertNull(
            "la navegación sólo llega tras borrar: la fila ya no existe",
            runBlocking { withTimeout(SETUP_TIMEOUT_MS) { persistence.load(draftId) } },
        )
        assertEquals("nada se activa al descartar", 0, done.get())

        // No destruye otros borradores.
        val remaining = runBlocking { withTimeout(SETUP_TIMEOUT_MS) { persistence.listRecoverable() } }
            .map { it.draftId }.toSet()
        rowsBefore.filter { it != draftId }.forEach { other ->
            assertTrue("no debe borrar borradores ajenos: $other", other in remaining)
            assertNotNull(
                "no debe borrar borradores ajenos: $other",
                runBlocking { withTimeout(SETUP_TIMEOUT_MS) { persistence.load(other) } },
            )
        }
    }

    // ─── Multiselección: tocar nunca avanza; avanza solo el CTA ──────────────

    @Test
    fun multiWeekdayPicksNeverAdvanceUntilTheContinueCta() {
        val draftId = newDraftId()
        val vm = newViewModel()
        vm.initialize(SetupWizardMode.FULL, draftId = draftId)
        awaitReady(vm)

        // Semilla legítima sobre el borrador real: se edita con la API pública
        // `update`, que persiste en Room sin confirmar ni mover el cursor por sí sola.
        vm.update { draft ->
            draft.copy(
                daysPerWeek = 3,
                stepProgress = draft.stepProgress.at(SetupStepId.WEEKDAYS, draft.stepContext()),
            )
        }
        composeRule.waitUntil(TIMEOUT_MS) { vm.state.value.currentStep == SetupStepId.WEEKDAYS }

        composeRule.setContent {
            SetupWizardScreen(
                mode = SetupWizardMode.FULL,
                draftId = draftId,
                onDone = {},
                onCancel = {},
                viewModel = vm,
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("setup-step-WEEKDAYS").assertIsDisplayed()
        composeRule.onNodeWithTag(CTA).assertIsNotEnabled()

        composeRule.onNodeWithText(WEEKDAY_1).performClick()
        composeRule.waitUntil(TIMEOUT_MS) { 1 in vm.state.value.draft.selectedWeekdays }
        assertEquals(SetupStepId.WEEKDAYS, vm.state.value.currentStep)

        composeRule.onNodeWithText(WEEKDAY_2).performClick()
        composeRule.waitUntil(TIMEOUT_MS) { 2 in vm.state.value.draft.selectedWeekdays }
        assertEquals(SetupStepId.WEEKDAYS, vm.state.value.currentStep)

        composeRule.onNodeWithText(WEEKDAY_3).performClick()
        composeRule.waitUntil(TIMEOUT_MS) { vm.state.value.draft.selectedWeekdays.size == 3 }
        assertEquals(SetupStepId.WEEKDAYS, vm.state.value.currentStep)

        // Con la semana completa el CTA habilita y solo él mueve el cursor.
        composeRule.onNodeWithTag(CTA).assertIsEnabled()
        composeRule.onNodeWithTag(CTA).performClick()
        composeRule.waitUntil(TIMEOUT_MS) { vm.state.value.currentStep == SetupStepId.SESSION_TIME }
        composeRule.onNodeWithTag("setup-step-SESSION_TIME").assertIsDisplayed()
        composeRule.onNodeWithText(WEEKDAY_1).assertDoesNotExist()
    }

    private companion object {
        const val CTA = "setup-continue"
        const val NAME_FIELD = "setup-name"
        const val BACK_LABEL = "Volver al paso anterior"
        const val EXIT_LABEL = "Salir"
        const val SAVE_AND_EXIT_LABEL = "Guardar y salir"
        const val DISCARD_ACTION_LABEL = "Descartar borrador"
        const val DISCARD_CONFIRM_LABEL = "Descartar"
        const val KEEP_LABEL = "Conservar"
        const val QA10_NAME = "QA10 Descarte"
        const val TYPED_NAME = "QA M11"
        const val RESUME_NAME = "QA Resume 7"
        const val WEEKDAY_1 = "Lunes"
        const val WEEKDAY_2 = "Martes"
        const val WEEKDAY_3 = "Miércoles"
        const val TIMEOUT_MS = 15_000L
        const val SETUP_TIMEOUT_MS = 60_000L
    }
}

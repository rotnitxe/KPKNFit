package com.example.kpkn.screens.onboarding

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasInsertTextAtCursorAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Escritura real en el subeditor de inventario con `SetupWizardViewModel` +
 * Room del dispositivo (`realSetupWizardPersistence`), sin estados fabricados.
 *
 * Reproduce el bug P0 capturado: `adb shell input text20.` en el campo de la
 * barra dejaba `0.` porque el valor controlado sólo se componía tras el eco
 * lento de Room y el IME componía sobre texto viejo. Por eso el teclado se
 * inyecta **insert a insert, sin esperas entre teclas** (nunca
 * `performTextInput("20.")` en bloque: un solo commit oculta la carrera) y se
 * exige el texto exacto `20.` tanto en pantalla como persistido en el
 * borrador (`stepEditors`) y en la fila de Room; además «Guardar y salir» con
 * el editor abierto reanuda el mismo crudo en un VM nuevo.
 */
@RunWith(AndroidJUnit4::class)
class SetupInventoryTypingUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var app: Application
    private lateinit var persistence: SetupWizardPersistence
    private val draftIds = mutableListOf<String>()

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        // Singletons reales que lee el entorno del wizard (el ComponentActivity
        // de la prueba no es MainActivity).
        ProgramRepository.init(app)
        NutritionRepository.init(app)
        runBlocking {
            withTimeout(SETUP_TIMEOUT_MS) { ProgramRepository.getInstance().isReady.first { it } }
        }
        persistence = realSetupWizardPersistence(app)
    }

    @After
    fun tearDown() {
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

    private fun awaitReady(vm: SetupWizardViewModel) {
        composeRule.waitUntil(TIMEOUT_MS) {
            val state = vm.state.value
            !state.isLoading && state.machineState == WizChatMachineState.AwaitingAnswer
        }
    }

    /**
     * Paso real de inventario en la ruta: el entorno «gimnasio» abre los cinco
     * grupos y el cursor se mueve con la API pública `update` (la copia se
     * encadena para que el contexto ya incluya el entorno).
     */
    private fun seedInventoryStep(vm: SetupWizardViewModel, step: SetupStepId) {
        awaitReady(vm)
        vm.update { draft ->
            val withEnvironment = draft.copy(trainingEnvironment = "gym")
            withEnvironment.copy(
                stepProgress = withEnvironment.stepProgress.at(step, withEnvironment.stepContext()),
            )
        }
        composeRule.waitUntil(TIMEOUT_MS) { vm.state.value.currentStep == step }
    }

    private fun openBarbellEditor() {
        composeRule.onNodeWithText("Barra").performClick()
        composeRule.onNode(hasInsertTextAtCursorAction()).assertExists()
        // Foco real del campo antes de inyectar, como haría el usuario.
        composeRule.onNode(hasInsertTextAtCursorAction()).performClick()
    }

    /**
     * Inserta carácter a carácter SIN esperar la persistencia entre teclas: es
     * la condición exacta del bug (el eco de Room llega tarde respecto al IME).
     */
    private fun typeSuccessively(vararg chars: String) {
        chars.forEach { char ->
            composeRule.onNode(hasInsertTextAtCursorAction()).performTextInput(char)
        }
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun persistedRaw(draftId: String): String? {
        val row = runBlocking { withTimeout(SETUP_TIMEOUT_MS) { persistence.load(draftId) } } ?: return null
        val draft = runCatching { json.decodeFromString<SetupWizardDraft>(row.payloadJson) }.getOrNull()
        return draft?.stepEditors?.get(SetupStepId.INVENTORY_BARBELL)?.values?.get("weight")
    }

    // ─── P0: inserts rápidos no pierden el punto y el crudo queda en Room ─────

    @Test
    fun insertsDelEditorConservanElPuntoExactoYPersistenElCrudo() {
        val draftId = newDraftId()
        val vm = newViewModel()
        vm.initialize(SetupWizardMode.FULL, draftId = draftId)
        seedInventoryStep(vm, SetupStepId.INVENTORY_BARBELL)

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

        composeRule.onNodeWithTag("setup-step-INVENTORY_BARBELL").assertIsDisplayed()
        openBarbellEditor()
        composeRule.onNodeWithTag(FIELD_TAG).assertIsDisplayed()

        // Tres commits sucesivos sin esperar ecos: "2" → "20" → "20.".
        typeSuccessively("2", "0", ".")

        composeRule.waitUntil(TIMEOUT_MS) {
            vm.state.value.draft.stepEditors[SetupStepId.INVENTORY_BARBELL]?.values?.get("weight") == RAW_TYPED
        }

        // Pantalla exacta tras los ecos: ni "0." (bug real) ni "20" sin punto.
        composeRule.onNodeWithText(RAW_TYPED).assertIsDisplayed()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(RAW_TYPED).assertIsDisplayed()

        // Fila Room real: el crudo llegó sin recortes.
        assertEquals("el crudo no llegó intacto a Room", RAW_TYPED, persistedRaw(draftId))

        // Subfase de soportes: sólo se espera el eco de la fase; la tecla final
        // y los cambios siguientes viajan en la cola y el transform de Guardar
        // los lee dentro del mutex (snapshot más reciente).
        composeRule.onNodeWithText("Siguiente").performClick()
        composeRule.waitUntil(TIMEOUT_MS) {
            vm.state.value.draft.stepEditors[SetupStepId.INVENTORY_BARBELL]?.phase == 1
        }
        composeRule.onNodeWithText("¿Qué soportes tienes?").assertIsDisplayed()

        // Soporte explícito y Guardar acto seguido, SIN esperar su eco.
        composeRule.onNodeWithText("Bandas").performClick()
        composeRule.onNodeWithText("Guardar").performClick()
        composeRule.waitUntil(TIMEOUT_MS) {
            val state = vm.state.value
            state.draft.trainingOptions.inventory?.barbellWeightKg == 20.0 &&
                state.draft.trainingOptions.inventory?.supportEquipment?.contains("band") == true &&
                state.draft.stepEditors[SetupStepId.INVENTORY_BARBELL] == null
        }
        composeRule.onNodeWithText("20 kg").assertIsDisplayed()

        // none explícito tras el cierre: retira el peso y conserva los soportes.
        composeRule.onNodeWithText("No tengo este material").performClick()
        composeRule.waitUntil(TIMEOUT_MS) {
            val inventory = vm.state.value.draft.trainingOptions.inventory
            inventory?.barbellWeightKg == null && inventory?.supportEquipment?.contains("band") == true
        }
        composeRule.onNodeWithText("Sin declarar: el peso de la barra sigue desconocido.")
            .assertIsDisplayed()
    }

    // ─── Cola: última tecla → Guardar sin esperar eco, snapshot más reciente ──

    @Test
    fun teclaFinalYPulsarGuardarSinEsperarElEcoGuardaElValorMasReciente() {
        val draftId = newDraftId()
        val vm = newViewModel()
        vm.initialize(SetupWizardMode.FULL, draftId = draftId)
        seedInventoryStep(vm, SetupStepId.INVENTORY_KETTLEBELLS)

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

        composeRule.onNodeWithTag("setup-step-INVENTORY_KETTLEBELLS").assertIsDisplayed()
        composeRule.onNodeWithText("Añadir kettlebell").performClick()
        composeRule.onNode(hasInsertTextAtCursorAction()).assertExists()

        // Relleno inicial con eco: «Guardar» queda habilitado con "16".
        typeSuccessively("1", "6")
        composeRule.waitUntil(TIMEOUT_MS) {
            vm.state.value.draft.stepEditors[SetupStepId.INVENTORY_KETTLEBELLS]
                ?.values?.get("weight") == "16"
        }

        // Última tecla SIN esperar la persistencia y Guardar acto seguido: el
        // snapshot que se guarda debe ser "160", no el "16" que aún dominaría
        // en la captura de pantalla. Con el transform leído dentro de la cola
        // el valor es siempre el más reciente.
        typeSuccessively("0")
        composeRule.onNodeWithText("Guardar").performClick()

        composeRule.waitUntil(TIMEOUT_MS) {
            val state = vm.state.value
            state.draft.stepEditors[SetupStepId.INVENTORY_KETTLEBELLS] == null &&
                state.draft.trainingOptions.inventory?.kettlebells?.singleOrNull()?.weightKg == 160.0
        }
        composeRule.onNodeWithText("Kettlebell 160 kg").assertIsDisplayed()
    }

    // ─── Guardar y salir con editor abierto reanuda el crudo exacto ───────────

    @Test
    fun guardarYSalirReanudaElCrudoDelEditorAbierto() {
        val draftId = newDraftId()
        val vm1 = newViewModel()
        vm1.initialize(SetupWizardMode.FULL, draftId = draftId)
        seedInventoryStep(vm1, SetupStepId.INVENTORY_BARBELL)

        val cancelled = AtomicInteger(0)
        val vmHolder = mutableStateOf(vm1)
        composeRule.setContent {
            SetupWizardScreen(
                mode = SetupWizardMode.FULL,
                draftId = draftId,
                onDone = {},
                onCancel = { cancelled.incrementAndGet() },
                viewModel = vmHolder.value,
            )
        }
        composeRule.waitForIdle()

        openBarbellEditor()
        typeSuccessively("2", "0", ".")
        composeRule.waitUntil(TIMEOUT_MS) {
            vm1.state.value.draft.stepEditors[SetupStepId.INVENTORY_BARBELL]?.values?.get("weight") == RAW_TYPED
        }

        // Salir → diálogo → Guardar y salir (el editor sigue abierto).
        composeRule.onNodeWithText(EXIT_LABEL).performClick()
        composeRule.waitUntil(TIMEOUT_MS) { vm1.state.value.dialog == SetupWizardDialog.EXIT }
        composeRule.onNodeWithText(SAVE_AND_EXIT_LABEL).performClick()
        composeRule.waitUntil(TIMEOUT_MS) { cancelled.get() == 1 }
        assertEquals("el crudo no quedó en el borrador", RAW_TYPED, persistedRaw(draftId))

        // VM nuevo en RESUME con la misma fila: reanuda la edición exacta.
        val vm2 = newViewModel()
        vm2.initialize(SetupWizardMode.RESUME, draftId = draftId)
        composeRule.waitUntil(SETUP_TIMEOUT_MS) {
            val state = vm2.state.value
            !state.isLoading && state.machineState == WizChatMachineState.AwaitingAnswer
        }
        val resumed = vm2.state.value.draft.stepEditors[SetupStepId.INVENTORY_BARBELL]
        assertTrue("sin editor reanudado", resumed != null && resumed.editing)
        assertEquals("el crudo reanudado difiere", RAW_TYPED, resumed?.values?.get("weight"))

        // La UI reanudada muestra el mismo crudo (el buffer siembra desde el draft).
        vmHolder.value = vm2
        composeRule.waitForIdle()
        composeRule.onNodeWithText(RAW_TYPED).assertIsDisplayed()
    }

    private companion object {
        const val FIELD_TAG = "inventory-field-weight"
        const val RAW_TYPED = "20."
        const val EXIT_LABEL = "Salir"
        const val SAVE_AND_EXIT_LABEL = "Guardar y salir"
        const val TIMEOUT_MS = 15_000L
        const val SETUP_TIMEOUT_MS = 60_000L
    }
}

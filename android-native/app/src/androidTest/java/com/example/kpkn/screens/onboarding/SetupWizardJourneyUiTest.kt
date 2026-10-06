package com.example.kpkn.screens.onboarding

import android.app.Application
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasInsertTextAtCursorAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.WizChatMachineState
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Job
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

    /**
     * Solo es cierto cuando [setUp] llegó al final. Si el guard de uid o la
     * inicialización fallaron, `app`/`persistence` no existen: el tearDown no
     * los toca, no inicializa nada y no borra filas. Un fallo de preparación no
     * se convierte en un borrado accidental.
     */
    private var fixtureReady = false

    /** Cada VM creado se registra aquí para limpiarlo (onCleared) en After. */
    private val viewModelStore = ViewModelStore()
    private var viewModelCounter = 0

    /**
     * Job padre de `viewModelScope` de cada VM, capturado **mientras el VM está
     * vivo**. `ViewModelStore.clear()` cancela ese padre, pero la cancelación es
     * asíncrona: sin esperar a los hijos, el `discard` de las filas propias
     * podría correr con escrituras todavía en vuelo. Esto es *quietud causal*
     * (join acotado antes de borrar), no una carrera de producción reproducida.
     */
    private val viewModelScopes = mutableListOf<Job>()

    @Before
    fun setUp() {
        // PRIMERO y EN VOZ ALTA: sólo en la app del usuario (uid 10xxx), y antes
        // de cualquier acceso al fixture, al repositorio o a la base. Un
        // `Assume` aquí se salta la inicialización en silencio; un fallo hace que
        // la prueba NO se ejecute nunca por accidente sobre datos ajenos.
        assertTrue(
            "Las pruebas del wizard exigen la app de usuario del dispositivo (uid 10xxx); " +
                "uid observado=${Process.myUid()}. No se inicializó ningún repositorio.",
            Process.myUid() / 100000 == 10,
        )
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
        fixtureReady = true
    }

    @After
    fun tearDown() {
        // Cierre de TODOS los VM (onCleared → viewModelScope cancelado) ANTES
        // de tocar filas: ningún job sigue vivo durante el borrado.
        viewModelStore.clear()
        val ids = draftIds.toList()
        if (!fixtureReady || ids.isEmpty()) return
        val parents = viewModelScopes.toList()
        runBlocking {
            // Un solo reloj para el cierre y el borrado. Si el join se agota, la
            // excepción sale del tearDown y la prueba FALLA: no se descarta
            // ninguna fila, no se cierra Room ni se limpia la base. El store y
            // los repositorios globales nunca se cierran aquí.
            withTimeout(SETUP_TIMEOUT_MS) {
                parents.forEach { it.join() }
                ids.forEach { persistence.discard(it) }
            }
        }
    }

    private fun newDraftId(): String {
        val id = "setup-qa-${UUID.randomUUID()}"
        draftIds.add(id)
        return id
    }

    private fun newViewModel(): SetupWizardViewModel {
        val vm = SetupWizardViewModel(app, SavedStateHandle(), persistence, RealSetupWizardEnvironment(app.applicationContext))
        // Registrado en el store: After hace clear() → onCleared →
        // viewModelScope cancelado, sin jobs huérfanos.
        viewModelStore.put("vm-${viewModelCounter++}", vm)
        // El padre se captura aquí, con el VM vivo, y se espera en el tearDown.
        viewModelScopes += requireNotNull(vm.viewModelScope.coroutineContext[Job]) {
            "viewModelScope sin Job padre: no se puede esperar su cierre"
        }
        return vm
    }

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
        composeRule.onNodeWithTag(CTA).assertIsNotEnabled()
        vm.setAge(28)
        composeRule.waitUntil(TIMEOUT_MS) { vm.state.value.draft.ageYears == 28 }

        composeRule.onNodeWithTag(CTA).assertIsEnabled()
        composeRule.onNodeWithTag(CTA).performClick()
        awaitStepOrDump(vm, draftId, SetupStepId.HEIGHT)

        composeRule.onNodeWithTag("setup-step-HEIGHT").assertIsDisplayed()
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

        composeRule.onNodeWithText(EXIT_LABEL).performClick()
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
     * Salir no ofrece descartar: lo respondido se conserva y «Guardar y salir»
     * deja la fila en su sitio.
     */
    @Test
    fun leavingKeepsTheSavedConfigurationAndDoesNotOfferDiscard() {
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
        composeRule.onNodeWithText(EXIT_LABEL).performClick()
        composeRule.waitUntil(TIMEOUT_MS) { cancelled.get() == 1 }
        composeRule.onNodeWithText(DISCARD_ACTION_LABEL).assertDoesNotExist()

        assertEquals(QA10_NAME, vm.state.value.draft.name)
        assertNotNull(
            "salir conserva la configuración guardada",
            runBlocking { withTimeout(SETUP_TIMEOUT_MS) { persistence.load(draftId) } },
        )
        assertEquals("no activa nada", 0, done.get())
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
    }

    // ─── Chips de prioridad: presets, ajuste manual y viewport estrecho ────────
    //
    // Estas tres pruebas usan el host real (`SetupWizardScreen`) y el ViewModel
    // real sobre un borrador `setup-qa-<UUID>` propio. Nada aquí fabrica estados
    // ni reimplementa el catálogo: se lee la bolsa que el motor publicó y los
    // testTags reales de la pantalla.

    /**
     * Deja el cursor real en PRIORITIES con un perfil de entrenamiento plausible
     * y verificable. Se hace con la API pública `update`, que persiste en Room
     * sin confirmar ni avanzar por sí sola (mismo patrón que la prueba de
     * weekdays).
     */
    private fun seedPrioritiesStep(vm: SetupWizardViewModel) {
        vm.update { draft ->
            draft.copy(
                daysPerWeek = 3,
                selectedWeekdays = setOf(1, 3, 5),
                minutesPerSession = 60,
                equipment = setOf(SetupEquipment.GYM, SetupEquipment.BARBELL),
                includeNutrition = true,
                nutritionMode = "create",
                stepProgress = draft.stepProgress.at(SetupStepId.PRIORITIES, draft.stepContext()),
            )
        }
    }

    /** Espera a que el borrador real quede en PRIORITIES y compone el host. */
    private fun showPriorities(vm: SetupWizardViewModel, draftId: String) {
        awaitStepOrDump(vm, draftId, SetupStepId.PRIORITIES)
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
        composeRule.onNodeWithTag("setup-step-PRIORITIES").assertIsDisplayed()
    }

    /**
     * Vista de prueba: acota el host REAL a 360 dp de ancho y 520 dp de alto y
     * publica un `fontScale` de 2.0. No cambia la densidad, la configuración ni
     * el dispositivo: solo el espacio y la escala que ve la composición.
     */
    @Composable
    private fun NarrowViewport(fontScale: Float, content: @Composable () -> Unit) {
        val base = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density = base.density, fontScale = fontScale),
        ) {
            Box(
                modifier = Modifier
                    .size(NARROW_VIEWPORT_WIDTH.dp, NARROW_VIEWPORT_HEIGHT.dp)
                    .testTag(VIEWPORT_TAG),
            ) { content() }
        }
    }

    private fun chipTag(index: Int): String = PRIORITY_CHIP_PREFIX + index
    private fun addTag(option: String): String = PRIORITY_ADD_PREFIX + option
    private fun removeTag(option: String): String = PRIORITY_REMOVE_PREFIX + option

    /** Tag real del nodo, o `null` si no lo publica. */
    private fun SemanticsNode.testTagOrNull(): String? =
        if (config.contains(SemanticsProperties.TestTag)) config[SemanticsProperties.TestTag] else null

    /** Índice de catálogo a partir del tag real `setup-priority-preset-<i>`. */
    private fun SemanticsNode.chipIndex(): Int {
        val tag = requireNotNull(testTagOrNull()) { "el nodo del chip no publica testTag" }
        return requireNotNull(tag.removePrefix(PRIORITY_CHIP_PREFIX).toIntOrNull()) {
            "tag de chip inesperado: $tag"
        }
    }

    /** `Selected` real del nodo, leído de la semántica y no del color. */
    private fun SemanticsNode.isSelected(): Boolean =
        config.contains(SemanticsProperties.Selected) && config[SemanticsProperties.Selected] == true

    /**
     * Caja de LAYOUT real del nodo, sin recorte.
     *
     * `boundsInRoot` **no** sirve aquí: en Compose es
     * `coordinates.localBoundingBoxOf(root)`, que **intersecta con el recorte de
     * cada nodo intermedio**. Los chips viven dentro del `verticalScroll` del
     * andamiaje, cuya capa recorta: un chip bajo el pliegue salía como caja
     * vacía (`top = 0` en los siete) y las comparaciones de filas se volvían
     * triviales.
     *
     * `positionInRoot` es `localToRoot(Offset.Zero)` — una transformación de
     * coordenadas pura, sin recorte — y `size` es el tamaño de layout sin
     * recortar. Con las dos, en el MISMO marco que el nodo, se reconstruye la
     * caja verdadera. Nada de esto depende de un hook de producción.
     */
    private fun SemanticsNode.rawLayoutRect(): Rect {
        val origin = positionInRoot
        val measured = size
        return Rect(
            left = origin.x,
            top = origin.y,
            right = origin.x + measured.width,
            bottom = origin.y + measured.height,
        )
    }

    /** Los siete chips, por prefijo de tag: no depende de su orden en pantalla. */
    private val chipMatcher = SemanticsMatcher("chip de preset de prioridad") { node ->
        node.testTagOrNull()?.startsWith(PRIORITY_CHIP_PREFIX) == true
    }

    /** Índices marcados, leídos de la semántica real (`Selected`), no del color. */
    private fun selectedChipIndexes(): Set<Int> =
        composeRule.onAllNodes(chipMatcher).fetchSemanticsNodes()
            .filter { it.isSelected() }
            .map { it.chipIndex() }
            .toSet()

    /** Clic real sobre un chip, con scroll hasta el nodo (puede estar bajo el pliegue). */
    private fun clickChip(index: Int) {
        val tag = chipTag(index)
        composeRule.onNodeWithTag(tag).performScrollTo()
        composeRule.onNodeWithTag(tag).performClick()
    }

    /** Clic real sobre un ajuste manual `+` / `−`. */
    private fun clickManual(tag: String) {
        composeRule.onNodeWithTag(tag).performScrollTo()
        composeRule.onNodeWithTag(tag).performClick()
    }

    private fun bag(vm: SetupWizardViewModel): Map<String, Int> =
        vm.state.value.draft.trainingOptions.orderPriorities

    /**
     * A: la pregunta aprobada, los siete presets exactos y que tocar un chip
     * escriba EXACTAMENTE su bolsa sin avanzar y sin tocar el resto del perfil.
     */
    @Test
    fun approvedPriorityQuestionAndEveryPresetChipWritesItsExactBagWithoutAdvancing() {
        val draftId = newDraftId()
        val vm = newViewModel()
        vm.initialize(SetupWizardMode.FULL, draftId = draftId)
        prepareBlankName(vm)
        seedPrioritiesStep(vm)
        showPriorities(vm, draftId)

        // La pregunta es la aprobada, no la anterior.
        composeRule.onNodeWithText(APPROVED_PRIORITY_QUESTION).assertIsDisplayed()
        composeRule.onNodeWithTag(PRIORITY_PRESETS_TAG).assertExists()

        // Los siete chips existen, con la etiqueta exacta del catálogo vivo.
        PRIORITY_PRESET_LABELS.forEachIndexed { index, label ->
            composeRule.onNodeWithTag(chipTag(index)).assertExists()
            composeRule.onNode(
                hasTestTag(chipTag(index)) and
                    SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton),
            ).assertExists()
            composeRule.onNode(
                hasTestTag(chipTag(index)) and hasText(label),
            ).assertExists()
        }
        assertEquals("los siete chips del catálogo", 7, composeRule.onAllNodes(chipMatcher).fetchSemanticsNodes().size)

        // Perfil de referencia: nada de esto puede cambiar al aplicar presets.
        val before = vm.state.value.draft
        val beforeAutoregulation = before.trainingOptions.autoregulationMode
        val beforeWarmup = before.trainingOptions.warmup
        val beforeInventory = before.trainingOptions.inventory
        val beforeAvailability = before.trainingOptions.availability
        val beforeCardioType = before.cardioType
        val beforeCardioMinutes = before.cardioMinutes
        val beforeNutrition = before.nutritionDraft
        val beforeNutritionMode = before.nutritionMode
        val beforeNutritionPlan = before.nutritionPlanId
        val beforeNutritionIndex = before.nutritionStepIndex

        PRIORITY_PRESET_BAGS.forEachIndexed { index, expected ->
            val label = PRIORITY_PRESET_LABELS[index]
            val revisionBefore = vm.state.value.draft.revision
            clickChip(index)
            composeRule.waitUntil(TIMEOUT_MS) { bag(vm) == expected }

            // Bolsa exacta del preset, incluido el de glúteos y el vacío.
            assertEquals("bolsa exacta de «$label»", expected, bag(vm))
            composeRule.waitForIdle()
            // Solo el chip aplicado queda marcado.
            assertEquals("solo «$label» marcado", setOf(index), selectedChipIndexes())
            // Un preset es solo orden: no avanza y sí escribe una revisión nueva.
            assertEquals("«$label» no auto-avanza", SetupStepId.PRIORITIES, vm.state.value.currentStep)
            assertTrue(
                "«$label» debe persistir una revisión nueva",
                vm.state.value.draft.revision > revisionBefore,
            )
        }

        // El perfil ajeno a las preferencias queda intacto (salvo bookkeeping
        // legítimo de revisión/procedencia).
        val after = vm.state.value.draft
        assertEquals(3, after.daysPerWeek)
        assertEquals(setOf(1, 3, 5), after.selectedWeekdays)
        assertEquals(60, after.minutesPerSession)
        assertEquals(setOf(SetupEquipment.GYM, SetupEquipment.BARBELL), after.equipment)
        assertEquals(beforeCardioType, after.cardioType)
        assertEquals(beforeCardioMinutes, after.cardioMinutes)
        assertTrue("nutrición sigue incluida", after.includeNutrition)
        assertEquals(beforeNutritionMode, after.nutritionMode)
        assertEquals(beforeNutrition, after.nutritionDraft)
        assertEquals(beforeNutritionPlan, after.nutritionPlanId)
        assertEquals(beforeNutritionIndex, after.nutritionStepIndex)
        assertEquals(beforeAutoregulation, after.trainingOptions.autoregulationMode)
        assertEquals(beforeWarmup, after.trainingOptions.warmup)
        assertEquals(beforeInventory, after.trainingOptions.inventory)
        assertEquals(beforeAvailability, after.trainingOptions.availability)
    }

    /**
     * B: el ajuste manual `+`/`−` real después de un preset. Comprueba la
     * igualdad EXACTA bolsa↔preset (no un remembers del último toque), los
     * topes 2 por músculo y 5 en total, que un cero se retira y que una bolsa
     * vacía es válida sin gastar el presupuesto.
     */
    @Test
    fun manualPriorityAdjustmentsResistTweaksCapsAndDropsZeroWhileSelectionFollowsTheBag() {
        val draftId = newDraftId()
        val vm = newViewModel()
        vm.initialize(SetupWizardMode.FULL, draftId = draftId)
        prepareBlankName(vm)

        // Bolsa propia que NO coincide con ningún preset.
        vm.update { draft ->
            draft.copy(
                daysPerWeek = 3,
                selectedWeekdays = setOf(1, 3, 5),
                minutesPerSession = 60,
                equipment = setOf(SetupEquipment.GYM, SetupEquipment.BARBELL),
                stepProgress = draft.stepProgress.at(SetupStepId.PRIORITIES, draft.stepContext()),
                trainingOptions = draft.trainingOptions.copy(orderPriorities = mapOf("Pectorales" to 1)),
            )
        }
        showPriorities(vm, draftId)

        // Una bolsa ajena a los presets deja la fila sin marcar.
        assertEquals(mapOf("Pectorales" to 1), bag(vm))
        assertEquals("ningún chip marcado sin coincidencia exacta", emptySet<Int>(), selectedChipIndexes())

        // Quitar el único punto deja la bolsa vacía: presupuesto sin gastar es
        // válido y es la única bolsa que marca «Todo el cuerpo».
        clickManual(removeTag("Pectorales"))
        composeRule.waitUntil(TIMEOUT_MS) { bag(vm).isEmpty() }
        composeRule.waitForIdle()
        assertEquals("bolsa vacía marca «Todo el cuerpo»", setOf(0), selectedChipIndexes())
        assertTrue(
            "una bolsa vacía no bloquea el paso",
            vm.state.value.stepValidation.none { it.isBlocking },
        )
        assertTrue("el CTA sigue operable sin gastar los 5 puntos", vm.state.value.canConfirmStep)

        // Volver a añadir y quitar NO es una bandera que se apaga: la selección
        // se recupera cuando la bolsa vuelve a coincidir exactamente.
        clickManual(addTag("Pectorales"))
        composeRule.waitUntil(TIMEOUT_MS) { bag(vm) == mapOf("Pectorales" to 1) }
        composeRule.waitForIdle()
        assertEquals("«Pectorales 1» no es ningún preset", emptySet<Int>(), selectedChipIndexes())

        // El punto de Pectorales se retira con SU BOTÓN real, no con un reset
        // del ViewModel: `writePriorities` es estrictamente aditivo, así que el
        // caso aislado de Dorsales tiene que empezar de una bolsa vacía.
        clickManual(removeTag("Pectorales"))
        composeRule.waitUntil(TIMEOUT_MS) { bag(vm).isEmpty() }
        composeRule.waitForIdle()
        assertTrue("la bolsa debe quedar vacía antes del caso aislado", bag(vm).isEmpty())
        assertEquals("bolsa vacía vuelve a marcar «Todo el cuerpo»", setOf(0), selectedChipIndexes())

        // Dos Dorsales: exactamente el preset «Espalda amplia».
        clickManual(addTag("Dorsales"))
        composeRule.waitUntil(TIMEOUT_MS) { bag(vm) == mapOf("Dorsales" to 1) }
        clickManual(addTag("Dorsales"))
        composeRule.waitUntil(TIMEOUT_MS) { bag(vm) == mapOf("Dorsales" to 2) }
        composeRule.waitForIdle()
        assertEquals("«Espalda amplia» marcado por igualdad exacta", setOf(2), selectedChipIndexes())
        assertEquals(SetupStepId.PRIORITIES, vm.state.value.currentStep)

        // Tope por músculo: el `+` de un músculo ya en 2 está deshabilitado.
        composeRule.onNodeWithTag(addTag("Dorsales")).assertIsNotEnabled()
        val revisionAtCap = vm.state.value.draft.revision
        clickManual(addTag("Dorsales"))
        composeRule.waitForIdle()
        assertEquals("el tope de 2 por músculo no se rebasa", mapOf("Dorsales" to 2), bag(vm))
        assertEquals("un movimiento inválido no gasta revisión", revisionAtCap, vm.state.value.draft.revision)

        // Tope global: 2 + 2 + 1 = 5 y el sexto punto no entra.
        clickManual(addTag("Pectorales"))
        composeRule.waitUntil(TIMEOUT_MS) { bag(vm)["Pectorales"] == 1 }
        clickManual(addTag("Pectorales"))
        composeRule.waitUntil(TIMEOUT_MS) { bag(vm)["Pectorales"] == 2 }
        clickManual(addTag("Bíceps"))
        composeRule.waitUntil(TIMEOUT_MS) { bag(vm)["Bíceps"] == 1 }
        composeRule.waitForIdle()
        assertEquals(5, bag(vm).values.sum())
        composeRule.onNodeWithTag(addTag("Bíceps")).assertIsNotEnabled()
        val revisionAtBudget = vm.state.value.draft.revision
        clickManual(addTag("Bíceps"))
        composeRule.waitForIdle()
        assertEquals("el presupuesto de 5 no se rebasa", 5, bag(vm).values.sum())
        assertEquals(1, bag(vm)["Bíceps"])
        assertEquals("un movimiento inválido no gasta revisión", revisionAtBudget, vm.state.value.draft.revision)
        assertTrue(
            "5 puntos repartidos siguen siendo válidos",
            vm.state.value.stepValidation.none { it.isBlocking },
        )

        // Un 1 a 0 retira la clave: no queda un cero fantasma.
        clickManual(removeTag("Bíceps"))
        composeRule.waitUntil(TIMEOUT_MS) { "Bíceps" !in bag(vm) }
        assertEquals(mapOf("Dorsales" to 2, "Pectorales" to 2), bag(vm))
        composeRule.waitForIdle()
        assertEquals("la bolsa ya no es ningún preset", emptySet<Int>(), selectedChipIndexes())
        assertTrue(
            "quedan 4 de 5 puntos sin bloquear",
            vm.state.value.stepValidation.none { it.isBlocking },
        )
        // 2 → 1 resta un punto, no borra la entrada de golpe.
        clickManual(removeTag("Dorsales"))
        composeRule.waitUntil(TIMEOUT_MS) { bag(vm)["Dorsales"] == 1 }
        assertEquals(1, bag(vm)["Dorsales"])
        clickManual(removeTag("Dorsales"))
        composeRule.waitUntil(TIMEOUT_MS) { bag(vm)["Dorsales"] == null }
        assertEquals(mapOf("Pectorales" to 2), bag(vm))
        assertEquals(SetupStepId.PRIORITIES, vm.state.value.currentStep)
    }

    /**
     * C: viewport estrecho real (360 dp) y `fontScale` 2.0 sobre el host real.
     * Mide la geometría **relativa al contenedor**, no offsets absolutos.
     */
    @Test
    fun priorityChipsWrapInANarrowViewportAtDoubleFontAndStayOperable() {
        val draftId = newDraftId()
        val vm = newViewModel()
        vm.initialize(SetupWizardMode.FULL, draftId = draftId)
        prepareBlankName(vm)
        seedPrioritiesStep(vm)

        composeRule.setContent {
            NarrowViewport(fontScale = DOUBLE_FONT_SCALE) {
                SetupWizardScreen(
                    mode = SetupWizardMode.FULL,
                    draftId = draftId,
                    onDone = {},
                    onCancel = {},
                    viewModel = vm,
                )
            }
        }
        composeRule.waitUntil(TIMEOUT_MS) { vm.state.value.currentStep == SetupStepId.PRIORITIES }
        composeRule.waitForIdle()

        // ── Medición única, en un estado idle estable, ANTES de desplazar ──
        //
        // `boundsInRoot` queda descartado para la geometría de layout: interseca
        // con el recorte de la capa del `verticalScroll` y devolvía cajas vacías
        // para los chips bajo el pliegue. Se usa `positionInRoot` + `size`
        // (transformación pura + tamaño de layout), en el mismo marco para el
        // wrapper, el contenedor y los siete chips. Solo geometría y tags: aquí
        // no se imprime ningún dato del usuario.
        val wrapperNode = composeRule.onNodeWithTag(VIEWPORT_TAG).fetchSemanticsNode()
        val containerNode = composeRule.onNodeWithTag(PRIORITY_PRESETS_TAG).fetchSemanticsNode()
        val chips = composeRule.onAllNodes(chipMatcher).fetchSemanticsNodes()
        assertEquals("los siete chips se componen", PRIORITY_PRESET_LABELS.size, chips.size)
        val chipNodes = chips.associate { node -> node.chipIndex() to node }
        val wrapper = wrapperNode.rawLayoutRect()
        val container = containerNode.rawLayoutRect()
        val chipBounds = chipNodes.mapValues { (_, node) -> node.rawLayoutRect() }
        val diag = buildString {
            append("wrapper=$wrapper clipped=${wrapperNode.boundsInRoot}")
            append(" container=$container clipped=${containerNode.boundsInRoot}")
            append(" chips=")
            append(
                chipBounds.entries.sortedBy { it.key }.joinToString(" ") { (index, box) ->
                    "$index:$box clipped=${chipNodes.getValue(index).boundsInRoot}"
                },
            )
        }

        // 0) Puerta anti-degeneración: tamaños crudos positivos ANTES de comparar
        //    nada. Con ceros la comprobación de filas pasaría por vacuocidad.
        val degenerate = chipBounds.filterValues { it.width <= 0f || it.height <= 0f }
        assertTrue("geometría de chips degenerada ($degenerate) — $diag", degenerate.isEmpty())
        assertTrue("wrapper sin área ($wrapper) — $diag", wrapper.width > 0f && wrapper.height > 0f)
        assertTrue("contenedor sin área ($container) — $diag", container.width > 0f && container.height > 0f)

        // El wrapper mide lo pedido: de ahí sale la escala px/dp de la prueba.
        val pxPerDp = wrapper.width / NARROW_VIEWPORT_WIDTH
        assertTrue("el wrapper no tiene el ancho pedido ($wrapper) — $diag", pxPerDp > 0f)
        val minTouchHeight = TOUCH_TARGET_DP * pxPerDp

        // 1) Los chips ocupan AL MENOS dos filas. Dos pruebas independientes sobre
        //    las cajas crudas: `top` distintos y un par de bandas verticales
        //    disjuntas (orden de flujo libre, no solo vecinos ordenados).
        val distinctTops = chipBounds.values.map { it.top }.distinct()
        assertTrue(
            "se esperan 2+ filas distintas, hubo ${distinctTops.size} — $diag",
            distinctTops.size >= 2,
        )
        val disjointPair = chipBounds.entries.any { (ai, a) ->
            chipBounds.entries.any { (bi, b) -> ai != bi && a.bottom <= b.top }
        }
        assertTrue("no hay dos chips en filas disjuntas — $diag", disjointPair)

        // 2) No es una pila de tarjetas de ancho completo.
        assertTrue(
            "al menos un chip debe ser más estrecho que el contenedor " +
                "(contenedor=${container.width}) — $diag",
            chipBounds.values.any { it.width < container.width - 1f },
        )

        // 3) Objetivo táctil ≥ 48 dp y nada se sale horizontalmente.
        chipBounds.forEach { (index, box) ->
            assertTrue(
                "chip $index con altura real ${box.height}px < ${minTouchHeight}px (48 dp) — $diag",
                box.height >= minTouchHeight - 0.5f,
            )
            assertTrue(
                "chip $index se sale por la izquierda (${box.left} < ${container.left}) — $diag",
                box.left >= container.left - 1f,
            )
            assertTrue(
                "chip $index se sale por la derecha (${box.right} > ${container.right}) — $diag",
                box.right <= container.right + 1f,
            )
        }

        // 4) La etiqueta larga CRECE el chip: envuelve en varias líneas en vez
        //    de recortarse con elipsis ni de usar una altura fija.
        val longLabel = chipBounds.getValue(GLUTES_CHIP_INDEX).height
        val shortLabel = chipBounds.getValue(SHORT_LABEL_CHIP_INDEX).height
        assertTrue(
            "con fuente al 2x la etiqueta larga debe ocupar más alto que la corta " +
                "(larga=$longLabel, corta=$shortLabel) — $diag",
            longLabel > shortLabel,
        )

        // 5) Visibilidad e interacción REALES. La geometría fuera de pantalla no
        //    prueba visibilidad: el chip se alcanza con `performScrollTo` (el
        //    contenido puede desplazarse de forma legítima; no se exige ver los
        //    siete a la vez), se afirma displayed y se pulsa de verdad.
        val gluteTag = chipTag(GLUTES_CHIP_INDEX)
        composeRule.onNodeWithTag(gluteTag).performScrollTo()
        composeRule.onNodeWithTag(gluteTag).assertIsDisplayed()
        composeRule.onNodeWithTag(gluteTag).performClick()
        composeRule.waitUntil(TIMEOUT_MS) { bag(vm) == GLUTE_BAG }
        composeRule.waitForIdle()
        assertEquals("el chip de glúteos escribe su bolsa exacta — $diag", GLUTE_BAG, bag(vm))
        assertEquals("solo el chip de glúteos queda marcado — $diag", setOf(GLUTES_CHIP_INDEX), selectedChipIndexes())

        // 6) El CTA es operable de verdad: se alcanza, se pulsa y el cursor
        //    avanza en el ViewModel real. Existir el nodo no lo demuestra.
        //    Destino vigente del contrato de ruta: `SetupStepGraph.nodes` añade
        //    TRAINING_MAX, sin condición, justo después de PRIORITIES y antes de
        //    SPLIT (la misma ruta PRIORITIES → TRAINING_MAX → SPLIT que recorre
        //    `SetupWizardFullJourneyUiTest.walkTraining`), así que con este
        //    borrador sembrado (sin meta ni experiencia) el siguiente paso es
        //    TRAINING_MAX, no SPLIT. Se fija contra el grafo real antes de pulsar.
        assertEquals(
            "el grafo vigente coloca TRAINING_MAX tras PRIORITIES — $diag",
            SetupStepId.TRAINING_MAX,
            SetupStepGraph.next(SetupStepId.PRIORITIES, vm.state.value.draft.stepContext()),
        )
        composeRule.onNodeWithTag(CTA).assertIsDisplayed()
        composeRule.onNodeWithTag(CTA).assertIsEnabled()
        composeRule.onNodeWithTag(CTA).performClick()
        awaitStepOrDump(vm, draftId, SetupStepId.TRAINING_MAX)
        assertEquals(GLUTE_BAG, bag(vm))
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

        /** Pregunta de prioridades, literal (copy corto de la página larga: título de hasta 40 caracteres). */
        const val APPROVED_PRIORITY_QUESTION =
            "¿Qué músculos quieres priorizar?"

        /** Tags reales publicados por la rama de prioridades. */
        const val PRIORITY_PRESETS_TAG = "setup-priority-presets"
        const val PRIORITY_CHIP_PREFIX = "setup-priority-preset-"
        const val PRIORITY_ADD_PREFIX = "setup-priority-add-"
        const val PRIORITY_REMOVE_PREFIX = "setup-priority-remove-"

        /** Viewport de la prueba C. */
        const val VIEWPORT_TAG = "qa-viewport-360x520"
        const val NARROW_VIEWPORT_WIDTH = 360
        const val NARROW_VIEWPORT_HEIGHT = 520
        const val DOUBLE_FONT_SCALE = 2.0f
        const val TOUCH_TARGET_DP = 48

        /** Índice de la etiqueta más corta («Brazos») y de la de glúteos. */
        const val SHORT_LABEL_CHIP_INDEX = 4
        const val GLUTES_CHIP_INDEX = 1

        /** Etiquetas del catálogo vivo, en el orden real de los chips. */
        val PRIORITY_PRESET_LABELS = listOf(
            "Todo el cuerpo",
            "Quiero los mejores glúteos",
            "Espalda amplia",
            "Espalda densa y fuerte",
            "Brazos",
            "Pecho y hombros",
            "Piernas fuertes",
        )

        /** Bolsas exactas que cada preset debe escribir, en el mismo orden. */
        val PRIORITY_PRESET_BAGS = listOf<Map<String, Int>>(
            emptyMap(),
            mapOf("Glúteos" to 2, "Isquiosurales" to 2),
            mapOf("Dorsales" to 2),
            mapOf("Trapecio" to 2, "Dorsales" to 2, "Erectores Espinales" to 1),
            mapOf("Bíceps" to 2, "Tríceps" to 2),
            mapOf("Pectorales" to 2, "Deltoides" to 2),
            mapOf("Cuádriceps" to 2, "Isquiosurales" to 2, "Glúteos" to 1),
        )

        val GLUTE_BAG = PRIORITY_PRESET_BAGS[GLUTES_CHIP_INDEX]
    }
}

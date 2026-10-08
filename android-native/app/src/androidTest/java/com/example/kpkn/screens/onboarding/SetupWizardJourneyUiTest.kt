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
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasInsertTextAtCursorAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
import com.example.kpkn.domain.onboarding.MuscleSuggestions
import com.example.kpkn.domain.onboarding.MuscleSymbol
import com.example.kpkn.domain.onboarding.MuscleSymbols
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.onboarding.TrainingPlace
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
import org.junit.Assert.assertFalse
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
                showIntro = false,
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
                showIntro = false,
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
                showIntro = false,
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

    // ─── Días de entreno: tocar nunca avanza; avanza solo el CTA ──────────────

    @Test
    fun multiWeekdayPicksNeverAdvanceUntilTheContinueCta() {
        val draftId = newDraftId()
        val vm = newViewModel()
        vm.initialize(SetupWizardMode.FULL, draftId = draftId)
        awaitReady(vm)

        // Semilla legítima sobre el borrador real: se edita con la API pública
        // `update`, que persiste en Room sin confirmar ni mover el cursor por sí sola.
        vm.update { draft ->
            val seeded = draft.withPlaces(setOf(TrainingPlace.GYM))
            seeded.copy(stepProgress = seeded.stepProgress.at(SetupStepId.WEEKDAYS, seeded.stepContext()))
        }
        composeRule.waitUntil(TIMEOUT_MS) { vm.state.value.currentStep == SetupStepId.WEEKDAYS }

        composeRule.setContent {
            SetupWizardScreen(
                mode = SetupWizardMode.FULL,
                draftId = draftId,
                onDone = {},
                onCancel = {},
                viewModel = vm,
                showIntro = false,
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("setup-step-WEEKDAYS").assertIsDisplayed()
        // Sin ningún día el check sigue apagado: la semana necesita al menos uno.
        composeRule.onNodeWithTag(CTA).assertIsNotEnabled()

        // El calendario de la semana: cada toque alterna un día (1 = lunes) y el número de días es el de los elegidos.
        listOf(WEEKDAY_1, WEEKDAY_2, WEEKDAY_3).forEachIndexed { index, day ->
            composeRule.onNodeWithTag(weekdayTag(day)).performScrollTo()
            composeRule.onNodeWithTag(weekdayTag(day)).performClick()
            composeRule.waitUntil(TIMEOUT_MS) { day in vm.state.value.draft.selectedWeekdays }
            assertEquals(SetupStepId.WEEKDAYS, vm.state.value.currentStep)
            assertEquals(index + 1, vm.state.value.draft.daysPerWeek)
        }
        assertEquals(setOf(WEEKDAY_1, WEEKDAY_2, WEEKDAY_3), vm.state.value.draft.selectedWeekdays)

        // Tocar un día elegido lo quita, y tampoco avanza.
        composeRule.onNodeWithTag(weekdayTag(WEEKDAY_3)).performClick()
        composeRule.waitUntil(TIMEOUT_MS) { WEEKDAY_3 !in vm.state.value.draft.selectedWeekdays }
        assertEquals(SetupStepId.WEEKDAYS, vm.state.value.currentStep)
        composeRule.onNodeWithTag(weekdayTag(WEEKDAY_3)).performClick()
        composeRule.waitUntil(TIMEOUT_MS) { WEEKDAY_3 in vm.state.value.draft.selectedWeekdays }

        // Con la semana elegida el CTA habilita y solo él mueve el cursor.
        composeRule.onNodeWithTag(CTA).assertIsEnabled()
        composeRule.onNodeWithTag(CTA).performClick()
        composeRule.waitUntil(TIMEOUT_MS) { vm.state.value.currentStep == SetupStepId.SESSION_TIME }
        composeRule.onNodeWithTag("setup-step-SESSION_TIME").assertIsDisplayed()
    }

    // ─── Músculos a mejorar: la cuadrícula de símbolos ────────────────────────
    //
    // Estas tres pruebas usan el host real (`SetupWizardScreen`) y el ViewModel real sobre un borrador `setup-qa-<UUID>` propio.
    // Nada aquí fabrica estados ni reimplementa el catálogo: se lee la bolsa de orden que el motor publicó y las marcas de
    // prueba reales de la pantalla (`setup-muscle-<MÚSCULO>` y `setup-muscles-skip`).

    /**
     * Deja el cursor real en PRIORITIES con un perfil de entrenamiento plausible y verificable. Se hace con la API pública
     * `update`, que persiste en Room sin confirmar ni avanzar por sí sola (mismo patrón que la prueba de días).
     */
    private fun seedMusclesStep(vm: SetupWizardViewModel, profile: TrainingGoalProfile) {
        vm.update { draft ->
            val seeded = draft.withPlaces(setOf(TrainingPlace.GYM))
                .withGoalProfile(profile)
                .withWeekdays(setOf(1, 3, 5))
                .withSessionMinutes(60)
                .copy(experience = SetupExperience.INTERMEDIATE)
            seeded.copy(stepProgress = seeded.stepProgress.at(SetupStepId.PRIORITIES, seeded.stepContext()))
        }
    }

    /** Espera a que el borrador real quede en PRIORITIES y compone el host. */
    private fun showMuscles(vm: SetupWizardViewModel, draftId: String) {
        awaitStepOrDump(vm, draftId, SetupStepId.PRIORITIES)
        composeRule.setContent {
            SetupWizardScreen(
                mode = SetupWizardMode.FULL,
                draftId = draftId,
                onDone = {},
                onCancel = {},
                viewModel = vm,
                showIntro = false,
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

    private fun muscleTag(muscle: MuscleSymbol): String = MUSCLE_PREFIX + muscle.name

    /** Tag real del nodo, o `null` si no lo publica. */
    private fun SemanticsNode.testTagOrNull(): String? =
        if (config.contains(SemanticsProperties.TestTag)) config[SemanticsProperties.TestTag] else null

    /** Músculo de una celda a partir de su tag real `setup-muscle-<NOMBRE>`. */
    private fun SemanticsNode.muscle(): MuscleSymbol {
        val tag = requireNotNull(testTagOrNull()) { "el nodo de la celda no publica testTag" }
        return MuscleSymbol.valueOf(tag.removePrefix(MUSCLE_PREFIX))
    }

    /**
     * Caja de LAYOUT real del nodo, sin recorte.
     *
     * `boundsInRoot` **no** sirve aquí: en Compose es `coordinates.localBoundingBoxOf(root)`, que **intersecta con el recorte
     * de cada nodo intermedio**. Las celdas viven dentro del `verticalScroll` del andamiaje, cuya capa recorta: una celda bajo
     * el pliegue saldría como caja vacía y las comparaciones de filas se volverían triviales.
     *
     * `positionInRoot` es `localToRoot(Offset.Zero)` — una transformación de coordenadas pura, sin recorte — y `size` es el
     * tamaño de layout sin recortar. Con las dos, en el MISMO marco que el nodo, se reconstruye la caja verdadera.
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

    /** Las doce celdas, por prefijo de tag: no depende de su orden en pantalla. */
    private val muscleMatcher = SemanticsMatcher("celda de músculo") { node ->
        node.testTagOrNull()?.startsWith(MUSCLE_PREFIX) == true
    }

    /** Clic real sobre una celda, con scroll hasta el nodo (puede estar bajo el pliegue). */
    private fun clickMuscle(muscle: MuscleSymbol) {
        val tag = muscleTag(muscle)
        composeRule.onNodeWithTag(tag).performScrollTo()
        composeRule.onNodeWithTag(tag).performClick()
    }

    /** La bolsa de orden que el motor lee: un punto por músculo canónico. */
    private fun bag(vm: SetupWizardViewModel): Map<String, Int> =
        vm.state.value.draft.trainingOptions.orderPriorities

    /**
     * A: tocar un músculo lo añade a la bolsa (un punto, en su nombre canónico), tocarlo otra vez lo quita, nada de eso avanza
     * y con cinco elegidos el sexto no entra y la pantalla lo dice.
     */
    @Test
    fun tappingMusclesTogglesTheOrderBagWithoutAdvancingAndTheCapStopsAtFive() {
        val draftId = newDraftId()
        val vm = newViewModel()
        vm.initialize(SetupWizardMode.FULL, draftId = draftId)
        prepareBlankName(vm)
        // Un perfil general no sugiere músculos: la cuadrícula parte vacía.
        seedMusclesStep(vm, TrainingGoalProfile.STRENGTH_MUSCLE)
        showMuscles(vm, draftId)

        assertEquals("los doce músculos", MuscleSymbol.entries.size, composeRule.onAllNodes(muscleMatcher).fetchSemanticsNodes().size)
        assertTrue("sin sugerencias la bolsa parte vacía", bag(vm).isEmpty())

        val revisionBefore = vm.state.value.draft.revision
        clickMuscle(MuscleSymbol.CHEST)
        composeRule.waitUntil(TIMEOUT_MS) { bag(vm) == mapOf("Pectorales" to 1) }
        assertEquals("elegir un músculo no avanza", SetupStepId.PRIORITIES, vm.state.value.currentStep)
        assertTrue("elegir un músculo persiste una revisión nueva", vm.state.value.draft.revision > revisionBefore)

        // Tocarlo otra vez lo quita.
        clickMuscle(MuscleSymbol.CHEST)
        composeRule.waitUntil(TIMEOUT_MS) { bag(vm).isEmpty() }

        // Cinco músculos llenan la bolsa, con un punto cada uno.
        val five = listOf(MuscleSymbol.CHEST, MuscleSymbol.BACK, MuscleSymbol.SHOULDERS, MuscleSymbol.BICEPS, MuscleSymbol.TRICEPS)
        five.forEach { muscle -> clickMuscle(muscle) }
        composeRule.waitUntil(TIMEOUT_MS) { bag(vm).size == five.size }
        assertEquals(five.associate { MuscleSymbols.canonical(it) to 1 }, bag(vm))
        composeRule.onNodeWithText(CAP_NOTE).assertExists()

        // El sexto no entra: la bolsa y la revisión quedan como estaban.
        val revisionAtCap = vm.state.value.draft.revision
        clickMuscle(MuscleSymbol.ABS)
        composeRule.waitForIdle()
        assertEquals("el tope de cinco no se rebasa", five.associate { MuscleSymbols.canonical(it) to 1 }, bag(vm))
        assertEquals("un toque rechazado no gasta revisión", revisionAtCap, vm.state.value.draft.revision)
        assertEquals(SetupStepId.PRIORITIES, vm.state.value.currentStep)

        // Quitar uno libera el sitio.
        clickMuscle(MuscleSymbol.TRICEPS)
        composeRule.waitUntil(TIMEOUT_MS) { bag(vm).size == five.size - 1 }
        assertTrue(vm.state.value.stepValidation.none { it.isBlocking })
    }

    /**
     * B: las sugerencias del perfil llegan marcadas («Sugerido») pero no confirmadas, y «Omitir» las quita todas y confirma
     * el paso con la bolsa vacía.
     */
    @Test
    fun suggestedMusclesArriveMarkedAndSkipClearsThemAndConfirmsTheStepWithAnEmptyBag() {
        val draftId = newDraftId()
        val vm = newViewModel()
        vm.initialize(SetupWizardMode.FULL, draftId = draftId)
        prepareBlankName(vm)
        seedMusclesStep(vm, TrainingGoalProfile.POWERBUILDING)
        showMuscles(vm, draftId)

        val suggested = MuscleSuggestions.forProfile(TrainingGoalProfile.POWERBUILDING)
        assertEquals("las sugerencias del perfil llegan precargadas", suggested, MuscleSymbols.symbolsOf(bag(vm)))
        composeRule.onAllNodesWithText(SUGGESTED_TAG).assertCountEquals(suggested.size)
        assertFalse(
            "las sugerencias nunca se confirman solas",
            SetupStepId.PRIORITIES in vm.state.value.draft.stepProgress.answers,
        )

        val next = checkNotNull(SetupStepGraph.next(SetupStepId.PRIORITIES, vm.state.value.draft.stepContext()))
        composeRule.onNodeWithTag(MUSCLES_SKIP_TAG).performClick()
        awaitStepOrDump(vm, draftId, next)
        assertTrue("omitir quita también las sugerencias", bag(vm).isEmpty())
        assertTrue(
            "omitir confirma el paso: es una respuesta válida",
            SetupStepId.PRIORITIES in vm.state.value.draft.stepProgress.answers,
        )
    }

    /**
     * C: viewport estrecho real (360 dp) y `fontScale` 2.0 sobre el host real. La cuadrícula conserva sus tres columnas,
     * sus objetivos táctiles de 48 dp y sus doce celdas dentro de la ventana, y el check sigue siendo operable.
     * Mide la geometría **relativa al contenedor**, no offsets absolutos.
     */
    @Test
    fun theMuscleGridKeepsItsTouchTargetsInANarrowViewportAtDoubleFontAndStaysOperable() {
        val draftId = newDraftId()
        val vm = newViewModel()
        vm.initialize(SetupWizardMode.FULL, draftId = draftId)
        prepareBlankName(vm)
        seedMusclesStep(vm, TrainingGoalProfile.STRENGTH_MUSCLE)

        composeRule.setContent {
            NarrowViewport(fontScale = DOUBLE_FONT_SCALE) {
                SetupWizardScreen(
                    mode = SetupWizardMode.FULL,
                    draftId = draftId,
                    onDone = {},
                    onCancel = {},
                    viewModel = vm,
                    showIntro = false,
                )
            }
        }
        composeRule.waitUntil(TIMEOUT_MS) { vm.state.value.currentStep == SetupStepId.PRIORITIES }
        composeRule.waitForIdle()

        // ── Medición única, en un estado idle estable, ANTES de desplazar ──
        val wrapperNode = composeRule.onNodeWithTag(VIEWPORT_TAG).fetchSemanticsNode()
        val cells = composeRule.onAllNodes(muscleMatcher).fetchSemanticsNodes()
        assertEquals("las doce celdas se componen", MuscleSymbol.entries.size, cells.size)
        val wrapper = wrapperNode.rawLayoutRect()
        val bounds = cells.associate { node -> node.muscle() to node.rawLayoutRect() }
        val diag = "wrapper=$wrapper celdas=" + bounds.entries.sortedBy { it.key.ordinal }.joinToString(" ") { (muscle, box) -> "${muscle.name}:$box" }

        // 0) Puerta anti-degeneración: tamaños crudos positivos ANTES de comparar nada.
        assertTrue("wrapper sin área ($wrapper) — $diag", wrapper.width > 0f && wrapper.height > 0f)
        val degenerate = bounds.filterValues { it.width <= 0f || it.height <= 0f }
        assertTrue("geometría de celdas degenerada ($degenerate) — $diag", degenerate.isEmpty())

        // El wrapper mide lo pedido: de ahí sale la escala px/dp de la prueba.
        val pxPerDp = wrapper.width / NARROW_VIEWPORT_WIDTH
        assertTrue("el wrapper no tiene el ancho pedido ($wrapper) — $diag", pxPerDp > 0f)
        val minTouchHeight = TOUCH_TARGET_DP * pxPerDp

        // 1) Tres columnas y cuatro filas: no se apilan ni se parten con la letra grande.
        val distinctLefts = bounds.values.map { it.left }.distinct()
        val distinctTops = bounds.values.map { it.top }.distinct()
        assertEquals("tres columnas — $diag", MUSCLE_COLUMNS, distinctLefts.size)
        assertEquals("cuatro filas — $diag", MuscleSymbol.entries.size / MUSCLE_COLUMNS, distinctTops.size)

        // 2) Objetivo táctil ≥ 48 dp y nada se sale horizontalmente de la ventana.
        bounds.forEach { (muscle, box) ->
            assertTrue("${muscle.name} con altura real ${box.height}px < ${minTouchHeight}px (48 dp) — $diag", box.height >= minTouchHeight - 0.5f)
            assertTrue("${muscle.name} se sale por la izquierda (${box.left} < ${wrapper.left}) — $diag", box.left >= wrapper.left - 1f)
            assertTrue("${muscle.name} se sale por la derecha (${box.right} > ${wrapper.right}) — $diag", box.right <= wrapper.right + 1f)
        }

        // 3) Visibilidad e interacción REALES: la celda se alcanza con `performScrollTo`, se afirma displayed y se pulsa de verdad.
        val tag = muscleTag(MuscleSymbol.QUADS)
        composeRule.onNodeWithTag(tag).performScrollTo()
        composeRule.onNodeWithTag(tag).assertIsDisplayed()
        composeRule.onNodeWithTag(tag).performClick()
        composeRule.waitUntil(TIMEOUT_MS) { bag(vm) == mapOf("Cuádriceps" to 1) }

        // 4) El CTA es operable de verdad: se alcanza, se pulsa y el cursor avanza en el ViewModel real.
        val next = checkNotNull(SetupStepGraph.next(SetupStepId.PRIORITIES, vm.state.value.draft.stepContext()))
        composeRule.onNodeWithTag(CTA).assertIsDisplayed()
        composeRule.onNodeWithTag(CTA).assertIsEnabled()
        composeRule.onNodeWithTag(CTA).performClick()
        awaitStepOrDump(vm, draftId, next)
        assertEquals(mapOf("Cuádriceps" to 1), bag(vm))
    }

    private fun weekdayTag(day: Int): String = "setup-weekday-$day"

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
        const val WEEKDAY_1 = 1
        const val WEEKDAY_2 = 2
        const val WEEKDAY_3 = 3
        const val TIMEOUT_MS = 15_000L
        const val SETUP_TIMEOUT_MS = 60_000L

        /** Marcas y textos reales de la cuadrícula de músculos (`MuscleSymbolGrid` y su «Omitir»). */
        const val MUSCLE_PREFIX = "setup-muscle-"
        const val MUSCLES_SKIP_TAG = "setup-muscles-skip"
        const val CAP_NOTE = "Máximo 5 músculos."
        const val SUGGESTED_TAG = "Sugerido"
        const val MUSCLE_COLUMNS = 3

        /** Viewport de la prueba C. */
        const val VIEWPORT_TAG = "qa-viewport-360x520"
        const val NARROW_VIEWPORT_WIDTH = 360
        const val NARROW_VIEWPORT_HEIGHT = 520
        const val DOUBLE_FONT_SCALE = 2.0f
        const val TOUCH_TARGET_DP = 48
    }
}

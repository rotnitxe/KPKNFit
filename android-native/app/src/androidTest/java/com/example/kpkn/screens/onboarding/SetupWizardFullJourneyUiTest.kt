package com.example.kpkn.screens.onboarding

import android.app.Application
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasInsertTextAtCursorAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toActiveProgramState
import com.example.kpkn.data.db.toBodyObservation
import com.example.kpkn.data.db.toNutritionPlan
import com.example.kpkn.data.db.toProgram
import com.example.kpkn.data.db.toSettings
import com.example.kpkn.data.models.AutoregulationMode
import com.example.kpkn.data.models.BodyMetric
import com.example.kpkn.data.models.BodyObservationQuality
import com.example.kpkn.data.models.CalculationOrigin
import com.example.kpkn.data.onboarding.persistenceFactory
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.nutrition.NutritionConfigurationMode
import com.example.kpkn.domain.nutrition.NutritionPlanPreparationStatus
import com.example.kpkn.domain.onboarding.SetupStepDefinitions
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.WizChatMachineState
import com.example.kpkn.domain.training.ProgramExecutionContract
import com.example.kpkn.screens.onboarding.design.WizardHeightScale
import com.example.kpkn.screens.onboarding.design.WizardMassUnit
import com.example.kpkn.screens.onboarding.design.WizardWeightScale
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Recorrido COMPLETO del wizard por UI real: Bienvenida → Datos básicos →
 * Entreno → Nutrición → Rings → Revisión y activación, con la activación REAL
 * (Room del dispositivo) al final.
 *
 * Matriz aprobada — recomendación/protocolo × automática/manual/solo registro:
 * seis altas, cada una en su propio `@Test` y todas sobre las mismas ayudas de
 * UI, con la condición de cada rama escrita de forma explícita.
 *
 * Reglas de esta prueba:
 *  - Solo interacción de UI: clic, tecleo y scroll (semántica o arrastre). Nunca
 *    se llama a las APIs de escritura del ViewModel (`vm.update`, `setStepText`,
 *    `setStepNumber`, `setStepChoice`, `setStepChoices`, `skipStep`, `editStep`,
 *    `submitCurrentStep`) para responder ni para saltar pasos: el único avance
 *    es el CTA del Host (`setup-continue`). Leer el estado del ViewModel para
 *    afirmar cursor/respuestas y localizar el rótulo visible del candidato SÍ
 *    está permitido (aserción, no forzado).
 *  - Cada paso se declara a mano con su siguiente paso esperado
 *    (`answerAndContinue(paso, siguiente)`): un desvío de ruta falla con el
 *    cursor real en el mensaje. No hay bucle «salta todo».
 *  - Borrador FULL propio con prefijo `setup-qa-<UUID>`; tearDown solo descarta
 *    ESTOS borradores y `ViewModelStore.clear()` cierra los scopes reales. No
 *    se borra nada del usuario; las altas nuevas quedan para inspección.
 *  - La DB es compartida (usuario QA10): los conteos se capturan ANTES del
 *    recorrido y las aserciones son sobre filas PROPIAS (receipt, commitId,
 *    observaciones con el prefijo del borrador), nunca «global == 0».
 *
 * Huecos conocidos que esta prueba NO rellena (falla con el error real si
 * aparecen, en lugar de fabricar material):
 *  - El inventario declara SOLO el material propio: barra 20 kg + soportes
 *    (`support`), discos 20/10/5/2.5 ×2, mancuernas 10 en pareja, una estación
 *    de polea (`cable`) con rango 10–90 kg, y «No tengo este material» en
 *    kettlebells. La máquina genérica exigiría configuración del catálogo y el
 *    token de guardia impide que «cualquier máquina» acredite «todas»: si una
 *    receta exige material que esta fixture no declara, el candidato no llega y
 *    el fallo reproduce el motivo real.
 */
@RunWith(AndroidJUnit4::class)
class SetupWizardFullJourneyUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var app: Application
    private lateinit var persistence: SetupWizardPersistence
    private lateinit var room: KpknDatabase
    private val viewModelStore = ViewModelStore()
    private val draftIds = mutableListOf<String>()

    private lateinit var vm: SetupWizardViewModel
    private lateinit var wizardVisible: MutableState<Boolean>
    private val activations = AtomicInteger(0)
    private val cancellations = AtomicInteger(0)

    // ─── Ciclo de vida ────────────────────────────────────────────────────────

    @Before
    fun setUp() {
        assertRunsOnQaUser10()
        app = ApplicationProvider.getApplicationContext()
        // Singletons reales que lee el entorno del wizard (el ComponentActivity
        // de la prueba no es MainActivity).
        ProgramRepository.init(app)
        NutritionRepository.init(app)
        runBlocking {
            withTimeout(SETUP_TIMEOUT_MS) { ProgramRepository.getInstance().isReady.first { it } }
        }
        persistence = realSetupWizardPersistence(app)
        // Misma fábrica (singleton) que usa el propio wizard: Room real.
        room = persistenceFactory(app).database
    }

    @After
    fun tearDown() {
        // Los VMs primero: clear() cierra su viewModelScope real.
        viewModelStore.clear()
        val ids = draftIds.toList()
        if (ids.isEmpty()) return
        runBlocking { withTimeout(SETUP_TIMEOUT_MS) { ids.forEach { persistence.discard(it) } } }
    }

    /**
     * Guardia explícita de entorno: este recorrido solo corre en el usuario
     * QA10 (`Process.myUid() / 100000 == 10`). Si el runner es Owner0 la
     * prueba FALLA aquí con el diagnóstico en lugar de escribir programas,
     * planes y Ajustes del usuario real.
     */
    private fun assertRunsOnQaUser10() {
        val userId = Process.myUid() / 100000
        if (userId != 10) {
            fail(
                "Este recorrido exige el usuario QA10 (Process.myUid()/100000 == 10) y el runner está en " +
                    "$userId. Nunca se ejecuta como Owner0: activaría programas/planes sobre datos del usuario real.",
            )
        }
    }

    // ─── Matriz: recomendación/protocolo × automática/manual/solo registro ───

    @Test
    fun recommendedAutomaticActivatesNativeProgramAndPlan() =
        runJourney(PlanRoute.RECOMMENDED, NutritionFlavor.AUTOMATIC)

    @Test
    fun recommendedSelfDefinedActivatesNativeProgramAndManualTargets() =
        runJourney(PlanRoute.RECOMMENDED, NutritionFlavor.SELF_DEFINED)

    @Test
    fun recommendedTrackingOnlyActivatesProgramWithoutNutritionPlan() =
        runJourney(PlanRoute.RECOMMENDED, NutritionFlavor.TRACKING_ONLY)

    @Test
    fun protocolAutomaticActivatesProtocolProgramAndPlan() =
        runJourney(PlanRoute.PROTOCOL, NutritionFlavor.AUTOMATIC)

    @Test
    fun protocolSelfDefinedActivatesProtocolProgramAndManualTargets() =
        runJourney(PlanRoute.PROTOCOL, NutritionFlavor.SELF_DEFINED)

    @Test
    fun protocolTrackingOnlyActivatesProtocolProgramWithoutNutritionPlan() =
        runJourney(PlanRoute.PROTOCOL, NutritionFlavor.TRACKING_ONLY)

    /** Rama de NUTRITION_START y candidato de plan que exige cada variante. */
    private enum class PlanRoute(
        val label: String,
        val candidateSource: String,
        val expectedCandidateId: String? = null,
    ) {
        RECOMMENDED("Qué me lo recomiendes", "NATIVE"),
        PROTOCOL("Elegir un protocolo", "PROTOCOL", expectedCandidateId = "protocol:coan-phillipi-dl"),
    }

    /** Modo de nutrición declarado en NUTRITION_START. */
    private enum class NutritionFlavor { AUTOMATIC, SELF_DEFINED, TRACKING_ONLY }

    // ─── Orquestación del recorrido ──────────────────────────────────────────

    private fun runJourney(route: PlanRoute, flavor: NutritionFlavor) {
        // Snapshot de la DB compartida ANTES de tocar nada: todas las
        // aserciones posteriores son sobre filas propias de ESTA alta.
        val pre = roomSnapshot()

        // Datos de la matriz: la recomendación automática exige ≥19 años
        // (NutritionIneligibility.UNDER_19), sexo de cálculo y los vitales.
        val ageText = if (flavor == NutritionFlavor.AUTOMATIC) AGE_AUTO else AGE_OTHER
        val weightKg = if (flavor == NutritionFlavor.AUTOMATIC) WEIGHT_AUTO else WEIGHT_OTHER
        val equationSexLabel = if (flavor == NutritionFlavor.AUTOMATIC) "Masculino" else "No lo sé"

        val draftId = "setup-qa-${UUID.randomUUID()}"
        draftIds += draftId
        vm = SetupWizardViewModel(
            app,
            SavedStateHandle(),
            persistence,
            RealSetupWizardEnvironment(app.applicationContext),
        )
        viewModelStore.put(VM_STORE_KEY, vm)
        vm.initialize(SetupWizardMode.FULL, draftId = draftId)
        awaitState("wizard FULL cargado") {
            !it.isLoading && it.machineState == WizChatMachineState.AwaitingAnswer
        }
        assertEquals("modo FULL", SetupWizardMode.FULL, vm.state.value.mode)

        wizardVisible = mutableStateOf(false)
        activations.set(0)
        cancellations.set(0)
        composeRule.setContent {
            if (!wizardVisible.value) {
                SetupWelcomeScreen(onStart = { wizardVisible.value = true })
            } else {
                SetupWizardScreen(
                    mode = SetupWizardMode.FULL,
                    draftId = draftId,
                    onDone = { activations.incrementAndGet() },
                    onCancel = { cancellations.incrementAndGet() },
                    viewModel = vm,
                )
            }
        }
        composeRule.waitForIdle()

        walkWelcome()
        walkBasics(ageText = ageText, weightKg = weightKg, equationSexLabel = equationSexLabel)
        walkTraining(route = route)
        walkNutrition(flavor = flavor)
        walkRings()
        walkReviewAndActivate(flavor = flavor, pre = pre, draftId = draftId, weightKg = weightKg, ageText = ageText)
    }

    // ─── Bienvenida ──────────────────────────────────────────────────────────

    private fun walkWelcome() {
        composeRule.onNodeWithText("Comenzar").assertIsDisplayed().performClick()
        // La UI del wizard se compone al cerrar la bienvenida: espera explícita.
        awaitUi("paso NAME visible tras la bienvenida") {
            composeRule.onNodeWithTag(stepTag(SetupStepId.NAME)).assertIsDisplayed()
            true
        }
        assertEquals("el cursor arranca en NAME tras la bienvenida", SetupStepId.NAME, vm.state.value.currentStep)
    }

    // ─── Bloque 1: Datos básicos ─────────────────────────────────────────────

    private fun walkBasics(ageText: String, weightKg: Int, equationSexLabel: String) {
        answerAndContinue(SetupStepId.NAME, SetupStepId.AGE) {
            typeInto(SetupStepId.NAME, NAME_FIELD_LABEL to TEST_NAME)
        }
        answerAndContinue(SetupStepId.AGE, SetupStepId.HEIGHT) {
            typeInto(SetupStepId.AGE, ageFieldLabel() to ageText)
        }
        answerAndContinue(SetupStepId.HEIGHT, SetupStepId.WEIGHT) {
            setHeightCm(TARGET_HEIGHT_CM)
        }
        answerAndContinue(SetupStepId.WEIGHT, SetupStepId.EQUATION_SEX) {
            setWeightKg(weightKg)
        }
        answerAndContinue(SetupStepId.EQUATION_SEX, SetupStepId.BODY_FAT) {
            clickOption(SetupStepId.EQUATION_SEX, equationSexLabel)
        }
        // Grasa corporal: desconocimiento EXPLÍCITO, nunca un porcentaje inventado.
        answerAndContinue(SetupStepId.BODY_FAT, SetupStepId.MILESTONE_BASICS) {
            clickOption(SetupStepId.BODY_FAT, "No lo sé")
        }
        assertEquals(SetupBodyFatSource.UNKNOWN, vm.state.value.draft.bodyFatSource)
        answerAndContinue(SetupStepId.MILESTONE_BASICS, SetupStepId.EXPERIENCE)
    }

    // ─── Bloque 2: Entreno ───────────────────────────────────────────────────

    private fun walkTraining(route: PlanRoute) {
        val protocolRoute = route == PlanRoute.PROTOCOL
        answerAndContinue(SetupStepId.EXPERIENCE, SetupStepId.ROUTE) {
            clickOption(
                SetupStepId.EXPERIENCE,
                if (protocolRoute) "Tengo experiencia" else "Ya entreno con constancia",
            )
        }
        answerAndContinue(SetupStepId.ROUTE, SetupStepId.GOAL) {
            clickOption(SetupStepId.ROUTE, route.label)
        }
        // El objetivo infiere el estilo: el paso STYLE no entra en esta ruta.
        answerAndContinue(SetupStepId.GOAL, SetupStepId.VOLUME_TECHNIQUE) {
            // La rama de protocolos declara un perfil que sí tiene candidato
            // publicado y ejecutable con este inventario: Coan-Phillipi, avanzado
            // y de fuerza, con barra y polea. No hay protocolo de hipertrofia
            // publicado para 3 días; los de fuerza de 3 días requieren además
            // material que esta declaración finita no incluye.
            clickOption(SetupStepId.GOAL, if (protocolRoute) "Fuerza" else "Músculo")
        }
        answerAndContinue(SetupStepId.VOLUME_TECHNIQUE, SetupStepId.VOLUME_CONSISTENCY) {
            clickOption(SetupStepId.VOLUME_TECHNIQUE, "Bastante estable")
        }
        answerAndContinue(SetupStepId.VOLUME_CONSISTENCY, SetupStepId.VOLUME_STRENGTH) {
            clickOption(SetupStepId.VOLUME_CONSISTENCY, "Bastante constante")
        }
        answerAndContinue(SetupStepId.VOLUME_STRENGTH, SetupStepId.VOLUME_MOBILITY) {
            clickOption(SetupStepId.VOLUME_STRENGTH, "Intermedia")
        }
        answerAndContinue(SetupStepId.VOLUME_MOBILITY, SetupStepId.EQUIPMENT) {
            clickOption(SetupStepId.VOLUME_MOBILITY, "Suficiente")
        }
        // Gimnasio completo: abre los cinco grupos de inventario.
        answerAndContinue(SetupStepId.EQUIPMENT, SetupStepId.INVENTORY_BARBELL) {
            clickOption(SetupStepId.EQUIPMENT, "Gimnasio completo")
        }
        walkInventory()

        answerAndContinue(SetupStepId.DAYS, SetupStepId.WEEKDAYS) {
            clickOption(SetupStepId.DAYS, if (protocolRoute) "1 día" else "3 días")
        }
        answerAndContinue(SetupStepId.WEEKDAYS, SetupStepId.SESSION_TIME) {
            val weekdays = if (protocolRoute) listOf("Jueves") else listOf("Lunes", "Miércoles", "Viernes")
            weekdays.forEach { weekday -> clickOption(SetupStepId.WEEKDAYS, weekday) }
        }
        answerAndContinue(SetupStepId.SESSION_TIME, SetupStepId.PRIORITIES) {
            typeInto(SetupStepId.SESSION_TIME, sessionTimeFieldLabel() to "60")
        }
        // Bolsa de orden vacía: válida por contrato, sin puntos fabricados.
        answerAndContinue(SetupStepId.PRIORITIES, SetupStepId.SPLIT)
        answerAndContinue(SetupStepId.SPLIT, SetupStepId.PLAN) {
            if (route == PlanRoute.RECOMMENDED) clickOption(SetupStepId.SPLIT, "Recomendado para ti")
        }
        answerAndContinue(SetupStepId.PLAN, SetupStepId.TRAINING_MAX) {
            selectPlanCandidate(route.candidateSource)
        }
        route.expectedCandidateId?.let { expectedId ->
            assertEquals(
                "la ruta de protocolo selecciona el protocolo real compatible con el inventario declarado",
                expectedId,
                vm.state.value.draft.selectedCatalogId,
            )
        }
        answerAndContinue(SetupStepId.TRAINING_MAX, SetupStepId.AUTOREGULATION) {
            clickOption(SetupStepId.TRAINING_MAX, "Todavía no")
        }
        // PROPOSE es el valor por defecto YA visible en pantalla y es la única
        // opción que el contrato acepta sin confirmación: Continuar es la
        // respuesta real, no se fabrica una confirmación de AUTO.
        answerAndContinue(SetupStepId.AUTOREGULATION, SetupStepId.WARMUPS)
        // «Estándar del plan» es también el estado por defecto (warmup == null):
        // se deja tal cual y se confirma con el CTA, sin escrituras no-op.
        answerAndContinue(SetupStepId.WARMUPS, SetupStepId.TRAINING_REVIEW)
        awaitTrainingPreviewReady()
        answerAndContinue(SetupStepId.TRAINING_REVIEW, SetupStepId.MILESTONE_TRAINING)
        answerAndContinue(SetupStepId.MILESTONE_TRAINING, SetupStepId.NUTRITION_START)
    }

    /** Inventario finito y honesto: declara SOLO el material que este recorrido usa. */
    private fun walkInventory() {
        answerAndContinue(SetupStepId.INVENTORY_BARBELL, SetupStepId.INVENTORY_PLATES) {
            declareBarbell(weightKg = 20)
        }
        answerAndContinue(SetupStepId.INVENTORY_PLATES, SetupStepId.INVENTORY_DUMBBELLS) {
            // Cuatro filas reales, dos discos por lado (editor ≤2 campos).
            listOf("20" to "2", "10" to "2", "5" to "2", "2.5" to "2").forEach { (kg, perSide) ->
                addPlate(kg = kg, perSide = perSide)
            }
        }
        answerAndContinue(SetupStepId.INVENTORY_DUMBBELLS, SetupStepId.INVENTORY_KETTLEBELLS) {
            addDumbbell(weightKg = 10)
        }
        // Ausencia EXPLÍCITA: nunca material fantasma ni «general_gym» ilimitado.
        answerAndContinue(SetupStepId.INVENTORY_KETTLEBELLS, SetupStepId.INVENTORY_MACHINES) {
            clickOption(SetupStepId.INVENTORY_KETTLEBELLS, "No tengo este material")
        }
        // Coan-Phillipi exige barra para peso muerto/variantes y polea para el
        // jalón; ambos están declarados. La estación se declara con TIPO
        // explícito y cargas finitas.
        answerAndContinue(SetupStepId.INVENTORY_MACHINES, SetupStepId.DAYS) {
            declareCableStation()
        }
    }

    /**
     * Barra en sus DOS subfases (M4): peso → «¿Qué soportes tienes?» → Guardar.
     * Rack y banco se acreditan como `support`, el id que valida el catálogo.
     */
    private fun declareBarbell(weightKg: Int) {
        val fieldsBefore = editableFieldCount()
        clickOption(SetupStepId.INVENTORY_BARBELL, BARBELL_ROW_LABEL)
        awaitUi("fase 1 del subeditor de la barra") { editableFieldCount() > fieldsBefore }
        typeInto(SetupStepId.INVENTORY_BARBELL, BARBELL_FIELD_LABEL to weightKg.toString())
        clickOption(SetupStepId.INVENTORY_BARBELL, NEXT_LABEL)
        clickOption(SetupStepId.INVENTORY_BARBELL, SUPPORT_CHOICE_LABEL)
        clickOption(SetupStepId.INVENTORY_BARBELL, SAVE_LABEL)
        awaitState("barra $weightKg kg con soportes y editor cerrado") { state ->
            val inventory = state.draft.trainingOptions.inventory
            inventory != null &&
                inventory.barbellWeightKg == weightKg.toDouble() &&
                SUPPORT_ID in inventory.supportEquipment &&
                state.draft.stepEditors[SetupStepId.INVENTORY_BARBELL] == null
        }
    }

    /**
     * M4/M7: una máquina se declara con TIPO explícito y rango finito. Se usa la
     * estación multi «Polea» (`cable`), que exige solo tipo + rango; la genérica
     * (`machine`) exigiría además configuración del catálogo y una máquina sin
     * tipo/configuración nunca acredita «todas las máquinas».
     */
    private fun declareCableStation() {
        val fieldsBefore = editableFieldCount()
        clickOption(SetupStepId.INVENTORY_MACHINES, MACHINE_ADD_LABEL)
        awaitUi("subeditor de máquina abierto") { editableFieldCount() > fieldsBefore }
        typeInto(SetupStepId.INVENTORY_MACHINES, MACHINE_NAME_FIELD_LABEL to STATION_NAME)
        clickOption(SetupStepId.INVENTORY_MACHINES, MACHINE_KIND_CABLE_LABEL)
        clickOption(SetupStepId.INVENTORY_MACHINES, NEXT_LABEL)
        typeInto(
            SetupStepId.INVENTORY_MACHINES,
            MACHINE_MIN_FIELD_LABEL to MACHINE_MIN,
            MACHINE_MAX_FIELD_LABEL to MACHINE_MAX,
        )
        clickOption(SetupStepId.INVENTORY_MACHINES, NEXT_LABEL)
        typeInto(
            SetupStepId.INVENTORY_MACHINES,
            MACHINE_INC_FIELD_LABEL to MACHINE_INC,
            MACHINE_BASE_FIELD_LABEL to MACHINE_BASE,
        )
        clickOption(SetupStepId.INVENTORY_MACHINES, SAVE_LABEL)
        awaitState("estación de polea ${MACHINE_MIN}–${MACHINE_MAX} kg declarada") { state ->
            val inventory = state.draft.trainingOptions.inventory
            inventory != null &&
                inventory.machines.any { machine ->
                    machine.equipmentKind == MACHINE_KIND_CABLE_ID &&
                        machine.minLoadKg == MACHINE_MIN.toDouble() &&
                        machine.maxLoadKg == MACHINE_MAX.toDouble() &&
                        machine.incrementKg == MACHINE_INC.toDouble() &&
                        machine.baseLoadKg == MACHINE_BASE.toDouble()
                } &&
                state.draft.stepEditors[SetupStepId.INVENTORY_MACHINES] == null
        }
    }

    private fun addPlate(kg: String, perSide: String) {
        val fieldsBefore = editableFieldCount()
        clickOption(SetupStepId.INVENTORY_PLATES, "Añadir disco")
        awaitUi("subeditor de disco con dos campos") { editableFieldCount() >= fieldsBefore + 2 }
        typeInto(
            SetupStepId.INVENTORY_PLATES,
            PLATE_WEIGHT_FIELD_LABEL to kg,
            PLATE_COUNT_FIELD_LABEL to perSide,
        )
        clickOption(SetupStepId.INVENTORY_PLATES, SAVE_LABEL)
        awaitState("disco $kg kg ×$perSide por lado") { state ->
            state.draft.trainingOptions.inventory?.plates.orEmpty()
                .any { row -> row.weightKg == kg.toDouble() && row.countPerSide == perSide.toInt() }
        }
    }

    private fun addDumbbell(weightKg: Int) {
        val fieldsBefore = editableFieldCount()
        clickOption(SetupStepId.INVENTORY_DUMBBELLS, "Añadir mancuerna")
        awaitUi("subeditor de mancuerna abierto") { editableFieldCount() > fieldsBefore }
        typeInto(SetupStepId.INVENTORY_DUMBBELLS, DUMBBELL_FIELD_LABEL to weightKg.toString())
        clickOption(SetupStepId.INVENTORY_DUMBBELLS, SAVE_LABEL)
        awaitState("mancuerna $weightKg kg por unidad con pareja") { state ->
            state.draft.trainingOptions.inventory?.dumbbells.orEmpty()
                .any { row -> row.weightPerUnitKg == weightKg.toDouble() && row.pairAvailable }
        }
    }

    // ─── Bloque 3: Nutrición ─────────────────────────────────────────────────

    private fun walkNutrition(flavor: NutritionFlavor) {
        when (flavor) {
            NutritionFlavor.TRACKING_ONLY -> {
                answerAndContinue(SetupStepId.NUTRITION_START, SetupStepId.NUTRITION_RESULT) {
                    clickOption(SetupStepId.NUTRITION_START, "Solo registrar comidas")
                }
                assertConfigurationMode(NutritionConfigurationMode.TRACKING_ONLY)
                awaitNutritionPrepared()
                assertNull(
                    "solo registro: la revisión nunca promete plan",
                    vm.state.value.nutritionPlanPreview,
                )
                assertInTree("Solo registro de comidas")
                answerAndContinue(SetupStepId.NUTRITION_RESULT, SetupStepId.MILESTONE_NUTRITION)
            }

            NutritionFlavor.SELF_DEFINED -> {
                answerAndContinue(SetupStepId.NUTRITION_START, SetupStepId.NUTRITION_DIRECTION) {
                    clickOption(SetupStepId.NUTRITION_START, "Yo traigo mis números")
                }
                assertConfigurationMode(NutritionConfigurationMode.SELF_DEFINED)
                // La dirección es OBLIGATORIA (sin ella no hay plan); «Mantener»
                // además evita la cadena de ritmo.
                answerAndContinue(SetupStepId.NUTRITION_DIRECTION, SetupStepId.NUTRITION_TARGET) {
                    clickOption(SetupStepId.NUTRITION_DIRECTION, "Mantener")
                }
                answerAndContinue(SetupStepId.NUTRITION_TARGET, SetupStepId.NUTRITION_HISTORY_CONTEXT) {
                    clickOption(SetupStepId.NUTRITION_TARGET, "No fijar peso objetivo")
                }
                answerAndContinue(
                    SetupStepId.NUTRITION_HISTORY_CONTEXT,
                    SetupStepId.NUTRITION_MANUAL_CALORIES,
                ) { clickOption(SetupStepId.NUTRITION_HISTORY_CONTEXT, "Sin contexto de peso") }
                answerAndContinue(
                    SetupStepId.NUTRITION_MANUAL_CALORIES,
                    SetupStepId.NUTRITION_MANUAL_CARBS_FAT,
                ) {
                    typeInto(
                        SetupStepId.NUTRITION_MANUAL_CALORIES,
                        CALORIES_FIELD_LABEL to MANUAL_KCAL,
                        PROTEIN_FIELD_LABEL to MANUAL_PROTEIN,
                    )
                }
                answerAndContinue(
                    SetupStepId.NUTRITION_MANUAL_CARBS_FAT,
                    SetupStepId.NUTRITION_DISTRIBUTION,
                ) {
                    typeInto(
                        SetupStepId.NUTRITION_MANUAL_CARBS_FAT,
                        CARBS_FIELD_LABEL to MANUAL_CARBS,
                        FAT_FIELD_LABEL to MANUAL_FAT,
                    )
                }
                answerAndContinue(
                    SetupStepId.NUTRITION_DISTRIBUTION,
                    SetupStepId.NUTRITION_WEIGH_INS,
                ) { clickOption(SetupStepId.NUTRITION_DISTRIBUTION, "Uniforme todos los días") }
                answerAndContinue(SetupStepId.NUTRITION_WEIGH_INS, SetupStepId.NUTRITION_RESULT) {
                    clickOption(SetupStepId.NUTRITION_WEIGH_INS, "No añadir pesajes")
                }
                awaitNutritionPrepared()
                val manual = checkNotNull(vm.state.value.nutritionPlanPreview) { "plan manual preparado" }
                assertEquals("kcal propias", MANUAL_KCAL.toInt(), manual.calorieTarget)
                assertEquals(CalculationOrigin.MANUAL, manual.calculationOrigin)
                awaitCaloriesShownInUi(manual.calorieTarget)
                assertInTree("${manual.proteinGoal} g proteína")
                answerAndContinue(SetupStepId.NUTRITION_RESULT, SetupStepId.MILESTONE_NUTRITION)
            }

            NutritionFlavor.AUTOMATIC -> {
                answerAndContinue(SetupStepId.NUTRITION_START, SetupStepId.NUTRITION_ELIGIBILITY) {
                    clickOption(SetupStepId.NUTRITION_START, "Calcula mis referencias")
                }
                assertConfigurationMode(NutritionConfigurationMode.AUTOMATIC)
                answerAndContinue(SetupStepId.NUTRITION_ELIGIBILITY, SetupStepId.NUTRITION_DIRECTION) {
                    clickOption(SetupStepId.NUTRITION_ELIGIBILITY, "Ninguna de estas")
                }
                answerAndContinue(SetupStepId.NUTRITION_DIRECTION, SetupStepId.NUTRITION_TARGET) {
                    clickOption(SetupStepId.NUTRITION_DIRECTION, "Mantener")
                }
                answerAndContinue(SetupStepId.NUTRITION_TARGET, SetupStepId.NUTRITION_HISTORY_CONTEXT) {
                    clickOption(SetupStepId.NUTRITION_TARGET, "No fijar peso objetivo")
                }
                answerAndContinue(
                    SetupStepId.NUTRITION_HISTORY_CONTEXT,
                    SetupStepId.NUTRITION_ACTIVITY,
                ) { clickOption(SetupStepId.NUTRITION_HISTORY_CONTEXT, "Sin contexto de peso") }
                answerAndContinue(SetupStepId.NUTRITION_ACTIVITY, SetupStepId.NUTRITION_DISTRIBUTION) {
                    clickOption(SetupStepId.NUTRITION_ACTIVITY, "Algo activo")
                }
                answerAndContinue(
                    SetupStepId.NUTRITION_DISTRIBUTION,
                    SetupStepId.NUTRITION_WEIGH_INS,
                ) { clickOption(SetupStepId.NUTRITION_DISTRIBUTION, "Uniforme todos los días") }
                answerAndContinue(SetupStepId.NUTRITION_WEIGH_INS, SetupStepId.NUTRITION_RESULT) {
                    clickOption(SetupStepId.NUTRITION_WEIGH_INS, "No añadir pesajes")
                }
                awaitNutritionPrepared()
                // La recomendación automática exige ≥19 años (edad 30), sexo de
                // cálculo, vitales y elegibilidad confirmada: si falta algo, el
                // estado lo dice con el motivo real.
                val preparation = vm.state.value.nutritionPreparation
                if (preparation == null) {
                    fail("Sin preparación nutricional: ${vm.state.value.describe()}")
                    return
                }
                assertEquals(
                    "la cadena automática no está lista (¿<19 años o faltan datos?)",
                    NutritionPlanPreparationStatus.READY,
                    preparation.status,
                )
                assertTrue("sin errores de nutrición", vm.state.value.nutritionErrors.isEmpty())
                val automatic = checkNotNull(vm.state.value.nutritionPlanPreview) { "plan automático" }
                assertEquals(CalculationOrigin.PLAN, automatic.calculationOrigin)
                awaitCaloriesShownInUi(automatic.calorieTarget)
                answerAndContinue(SetupStepId.NUTRITION_RESULT, SetupStepId.MILESTONE_NUTRITION)
            }
        }
        answerAndContinue(SetupStepId.MILESTONE_NUTRITION, SetupStepId.RINGS_RECENT)
    }

    // ─── Bloque 4: Rings ─────────────────────────────────────────────────────

    private fun walkRings() {
        // «No lo sé» del historial no equivale a no haber entrenado: no añade
        // sesiones (RINGS_SESSIONS queda fuera de la ruta) y no fabrica evidencia.
        answerAndContinue(SetupStepId.RINGS_RECENT, SetupStepId.RINGS_MUSCLE_FEELING) {
            clickOption(SetupStepId.RINGS_RECENT, "No lo sé")
        }
        answerAndContinue(SetupStepId.RINGS_MUSCLE_FEELING, SetupStepId.RINGS_ENERGY_FEELING) {
            clickOption(SetupStepId.RINGS_MUSCLE_FEELING, "Algo cargados")
        }
        answerAndContinue(SetupStepId.RINGS_ENERGY_FEELING, SetupStepId.RINGS_STRUCTURE_FEELING) {
            clickOption(SetupStepId.RINGS_ENERGY_FEELING, "Prefiero omitir esta sensación")
        }
        answerAndContinue(SetupStepId.RINGS_STRUCTURE_FEELING, SetupStepId.RINGS_DISCOMFORT) {
            clickOption(SetupStepId.RINGS_STRUCTURE_FEELING, "Prefiero omitir esta sensación")
        }
        answerAndContinue(SetupStepId.RINGS_DISCOMFORT, SetupStepId.RINGS_RESULT) {
            clickOption(SetupStepId.RINGS_DISCOMFORT, "Prefiero omitir las molestias")
        }

        val rings = checkNotNull(vm.state.value.draft.ringsAnswers) { "respuestas de RINGS" }
        assertEquals("una sola sensación declarada", 2, rings.muscleFeeling)
        assertNull("energía omitida, sin nivel fabricado", rings.energy)
        assertNull("columna omitida, sin nivel fabricado", rings.structureFeeling)
        assertEquals(SetupRecentTrainingState.UNKNOWN, rings.recentTrainingState)
        assertFalse(
            "el desconocido del historial nunca añade sesiones",
            SetupStepId.RINGS_SESSIONS in vm.state.value.draft.stepProgress.visited,
        )

        if (vm.state.value.ringsPreviewLoading) {
            awaitState("preview de RINGS listo", PREVIEW_TIMEOUT_MS) { state ->
                !state.ringsPreviewLoading && state.ringsPreviewError == null &&
                    state.ringsBatteriesPreview != null
            }
        }
        // El resultado expone cobertura por canal; sin datos jamás un 100 %.
        assertInTree("no afirma un porcentaje", substring = true)
        answerAndContinue(SetupStepId.RINGS_RESULT, SetupStepId.MILESTONE_RINGS)
        answerAndContinue(SetupStepId.MILESTONE_RINGS, SetupStepId.REVIEW_ACTIVATE)
    }

    // ─── Revisión y activación ───────────────────────────────────────────────

    private fun walkReviewAndActivate(
        flavor: NutritionFlavor,
        pre: RoomSnapshot,
        draftId: String,
        weightKg: Int,
        ageText: String,
    ) {
        assertCurrentStep(SetupStepId.REVIEW_ACTIVATE)
        val ringsWasLoading = vm.state.value.ringsPreviewLoading

        // Los cuatro bloques con datos reales del borrador.
        listOf("Datos básicos", "Entreno", "Nutrición", "Rings").forEach { assertInTree(it) }
        assertInTree(TEST_NAME)
        assertInTree("$ageText años")
        assertInTree("$TARGET_HEIGHT_CM cm")
        assertInTree(WizardWeightScale.formatWithUnit(weightKg.toDouble(), WizardMassUnit.KG))
        assertInTree("Sexo de cálculo")
        assertInTree("Grasa corporal")
        assertInTree("Sesiones · muestra 1ª semana")
        assertFirstWeekExercisesShown()

        // Datos del plan según el modo (nada se rellena: se lee lo que hay).
        when (flavor) {
            NutritionFlavor.TRACKING_ONLY -> assertInTree("Solo registrar comidas")
            else -> {
                val plan = checkNotNull(vm.state.value.nutritionPlanPreview) { "plan en revisión" }
                awaitCaloriesShownInUi(plan.calorieTarget)
                if (flavor == NutritionFlavor.SELF_DEFINED) assertInTree("${plan.proteinGoal} g proteína")
            }
        }

        // Confirmaciones reales del alta: solo existen si corresponde.
        if (existsInTree(ACTIVATION_CONFIRM_LABEL)) {
            clickOption(SetupStepId.REVIEW_ACTIVATE, ACTIVATION_CONFIRM_LABEL)
        }
        if (existsInTree(RECIPE_CONFIRM_LABEL)) {
            clickOption(SetupStepId.REVIEW_ACTIVATE, RECIPE_CONFIRM_LABEL)
        }

        // Puerta real de la revisión: todo listo antes de tocar el CTA.
        awaitState("previews listas para activar", PREVIEW_TIMEOUT_MS) { state ->
            state.programPreview != null && state.previewError == null && !state.isPreviewLoading &&
                state.nutritionPreparation != null && state.nutritionErrors.isEmpty() &&
                !state.ringsPreviewLoading && state.ringsPreviewError == null &&
                state.errors.isEmpty()
        }
        if (ringsWasLoading) {
            awaitState("baterías de RINGS calculadas", PREVIEW_TIMEOUT_MS) { it.ringsBatteriesPreview != null }
        }

        composeRule.onNodeWithContentDescription(CTA_REVIEW_LABEL).assertExists()
        composeRule.onNodeWithTag(CTA).assertIsDisplayed().assertIsEnabled()

        // Un ÚNICO click: la protección del Host garantiza una sola navegación.
        composeRule.onNodeWithTag(CTA).performClick()
        awaitState("alta resuelta (Committed o error explícito)", COMMIT_TIMEOUT_MS) { state ->
            state.machineState == WizChatMachineState.Committed || state.errors.isNotEmpty()
        }
        if (vm.state.value.machineState != WizChatMachineState.Committed) {
            fail(
                "La activación fue rechazada por la puerta real: errores=${vm.state.value.errors} " +
                    "lastFailure=${vm.state.value.lastFailure} (${vm.state.value.describe()})",
            )
        }
        composeRule.waitForIdle()
        // Segundo toque: la protección (navigatedAfterCommit) no puede navegar dos veces.
        composeRule.onNodeWithTag(CTA).performClick()
        composeRule.waitForIdle()
        assertEquals("una sola navegación tras la activación", 1, activations.get())
        assertEquals("sin cancelaciones", 0, cancellations.get())
        assertTrue("sin errores tras el alta", vm.state.value.errors.isEmpty())

        assertCommittedRoom(flavor = flavor, pre = pre, draftId = draftId, weightKg = weightKg)
    }

    /** Revisión: TODOS los ejercicios reales de la primera semana del preview. */
    private fun assertFirstWeekExercisesShown() {
        val preview = vm.state.value.programPreview
        if (preview == null) {
            fail("La revisión no tiene programa: ${vm.state.value.describe()}")
            return
        }
        val sessions = preview.macrocycles
            .flatMap { it.blocks }
            .flatMap { it.mesocycles }
            .flatMap { it.weeks }
            .firstOrNull()
            ?.sessions
            .orEmpty()
        assertTrue("la primera semana del preview está vacía", sessions.isNotEmpty())
        val exercises = sessions.flatMap { session -> session.allExercises() }
        assertTrue("el preview no trae ejercicios reales", exercises.isNotEmpty())
        exercises.forEach { exercise ->
            assertInTree(exercise.name, substring = true, what = "ejercicio «${exercise.name}»")
        }
    }

    // ─── Aserciones sobre Room tras el alta real ─────────────────────────────

    private fun assertCommittedRoom(
        flavor: NutritionFlavor,
        pre: RoomSnapshot,
        draftId: String,
        weightKg: Int,
    ) {
        val state = vm.state.value
        val commitId = state.draft.commitId
        val receipt = checkNotNull(state.receiptId) { "recibo publicado por el VM" }

        // Recibo + borrador consumidos en la MISMA transacción.
        assertNotNull("recibo real en Room ($receipt)", room { room.setupCommitReceiptDao().get(receipt) })
        assertNull("el borrador propio se consume al activar", room { room.setupDraftDao().getDraft(draftId) })

        // Programa propio, activo y ejecutable.
        val programEntity = checkNotNull(room { room.programDao().getById(commitId) }) {
            "el programa $commitId no está en Room"
        }
        val program = programEntity.toProgram()
        ProgramExecutionContract.requireExecutable(program)
        assertTrue(
            "sesiones reales del programa",
            program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }
                .flatMap { it.weeks }.flatMap { it.sessions }.isNotEmpty(),
        )
        assertEquals("autorregulación PROPOSE", AutoregulationMode.PROPOSE, program.autoregulationMode)
        assertEquals(
            "el programa activo es el de ESTE alta",
            commitId,
            room { room.stateDao().getActiveProgram() }?.toActiveProgramState()?.programId,
        )

        // Observaciones corporales: filas PROPIAS, reales y fechadas.
        val observations = room { room.bodyProgressDao().getAllObservations() }
            .mapNotNull { it.toBodyObservation() }
        val own = observations.filter { it.id.startsWith("$draftId/") }
        val added = observations.filter { it.id !in pre.observationIds }
        assertEquals(
            "las filas nuevas son las de ESTE alta (ids=${added.map { row -> row.id }})",
            own.map { row -> row.id }.toSet(),
            added.map { row -> row.id }.toSet(),
        )
        val weight = own.singleOrNull { row -> row.metric == BodyMetric.WEIGHT }
        assertNotNull("peso actual como observación real", weight)
        assertEquals(weightKg.toDouble(), checkNotNull(weight).valueSi, 0.001)
        assertEquals(BodyObservationQuality.MEASURED, weight.quality)
        assertTrue(
            "sin grasa corporal fabricada («No lo sé»)",
            own.none { row -> row.metric == BodyMetric.BODY_FAT_PERCENT },
        )
        val now = System.currentTimeMillis()
        own.forEach { row ->
            assertTrue(
                "observación fechada en esta sesión (${row.id} = ${row.timestampEpochMs})",
                row.timestampEpochMs in (now - TEN_MINUTES_MS)..now,
            )
            assertTrue("valor finito (${row.id})", row.valueSi.isFinite())
        }

        // Metas: este recorrido no declara peso objetivo ⇒ ninguna meta nueva.
        assertEquals(
            "sin metas nuevas (no se declaró peso objetivo)",
            pre.goalCount,
            room { room.bodyProgressDao().getAllGoals() }.size,
        )

        val settings = checkNotNull(room { room.settingsDao().get() }) { "settings no persistidos" }.toSettings()
        assertEquals(true, settings.onboardingCompleted)
        assertEquals(TEST_NAME, settings.username)
        val inventory = checkNotNull(settings.equipmentInventory) { "inventario finito sin persistir" }
        assertEquals("barra", 20.0, checkNotNull(inventory.barbellWeightKg) { "barra declarada" }, 0.001)
        assertTrue("soportes declarados (rack/banco como «support»)", SUPPORT_ID in inventory.supportEquipment)
        assertEquals("discos", setOf(20.0, 10.0, 5.0, 2.5), inventory.plates.map { it.weightKg }.toSet())
        assertTrue("discos con cantidad por lado explícita", inventory.plates.all { (it.countPerSide ?: 0) == 2 })
        assertEquals("una mancuerna", 1, inventory.dumbbells.size)
        assertEquals(10.0, inventory.dumbbells.single().weightPerUnitKg, 0.001)
        assertTrue("mancuerna en pareja", inventory.dumbbells.single().pairAvailable)
        assertTrue("sin kettlebells fantasma", inventory.kettlebells.isEmpty())
        // Una estación multi (polea) con tipo explícito y rango finito: nada de
        // «cualquier máquina ⇒ todas las máquinas».
        assertEquals("una estación de polea", 1, inventory.machines.size)
        val station = inventory.machines.single()
        assertEquals("tipo explícito", MACHINE_KIND_CABLE_ID, station.equipmentKind)
        assertTrue(
            "toda máquina declara su tipo (sin deducirlo del nombre)",
            inventory.machines.all { !it.equipmentKind.isNullOrBlank() },
        )
        assertEquals(MACHINE_MIN.toDouble(), station.minLoadKg, 0.001)
        assertEquals(
            "tope superior obligatorio",
            MACHINE_MAX.toDouble(),
            checkNotNull(station.maxLoadKg) { "máquina declarada sin tope" },
            0.001,
        )
        assertEquals(MACHINE_INC.toDouble(), station.incrementKg, 0.001)
        assertEquals(MACHINE_BASE.toDouble(), station.baseLoadKg, 0.001)
        assertTrue(
            "cargas finitas",
            listOf(station.minLoadKg, checkNotNull(station.maxLoadKg), station.incrementKg, station.baseLoadKg)
                .all { it.isFinite() },
        )

        if (flavor == NutritionFlavor.TRACKING_ONLY) {
            assertEquals("flag de solo registro", true, settings.nutritionTrackingOnly)
            assertEquals("el gasto diario no cambia en solo registro", pre.dailyCalorieGoal, settings.dailyCalorieGoal)
            val planIds = room { room.nutritionDao().getAllPlans() }.map { it.id }.toSet()
            assertEquals("solo registro: ningún plan nuevo", pre.planIds, planIds)
            assertEquals(
                "solo registro: el plan activo sigue siendo el previo",
                pre.activePlanId,
                room { room.nutritionDao().getActiveState() }?.activePlanId,
            )
            assertNull(
                "solo registro: ninguna fila de plan con el id de ESTE alta",
                room { room.nutritionDao().getAllPlans() }.firstOrNull { it.id == commitId },
            )
            return
        }

        assertEquals("flag de registro activo", false, settings.nutritionTrackingOnly)
        val planId = checkNotNull(room { room.nutritionDao().getActiveState() }?.activePlanId) { "sin plan activo" }
        assertTrue("el plan activo es una fila NUEVA de esta alta", planId !in pre.planIds)
        val plan = room { room.nutritionDao().getAllPlans() }.single { it.id == planId }.toNutritionPlan()
        assertTrue("plan activo", plan.isActive)

        val previewPlan = checkNotNull(vm.state.value.nutritionPlanPreview) { "plan de la revisión" }
        assertEquals("kcal persistidas = las revisadas", previewPlan.calorieTarget, plan.calorieTarget)
        if (flavor == NutritionFlavor.SELF_DEFINED) {
            assertEquals("kcal manuales", MANUAL_KCAL.toInt(), plan.calorieTarget)
            assertEquals(CalculationOrigin.MANUAL, plan.calculationOrigin)
        } else {
            assertTrue("kcal automáticas > 0", plan.calorieTarget > 0)
            assertEquals(CalculationOrigin.PLAN, plan.calculationOrigin)
        }
        assertTrue(
            "plan con metas diarias duraderas",
            plan.proteinGoal > 0 && plan.carbGoal > 0 && plan.fatGoal > 0,
        )
        assertEquals("meta calórica durable en Ajustes", plan.calorieTarget, checkNotNull(settings.dailyCalorieGoal))
        assertNotNull("proteína durable", settings.dailyProteinGoal)
        assertNotNull("hidratos duraderos", settings.dailyCarbGoal)
        assertNotNull("grasas duraderas", settings.dailyFatGoal)

        val preparation = vm.state.value.nutritionPreparation
        if (preparation != null && preparation.days.isNotEmpty()) {
            val snapshot = room { room.nutritionDao().getDailyGoalSnapshot(LocalDate.now().toString()) }
            assertNotNull("objetivo de HOY escrito en la transacción del alta", snapshot)
            assertEquals(planId, snapshot?.planId)
        }
    }

    /** Conteos previos de la DB compartida: nunca se asume que empieza vacía. */
    private data class RoomSnapshot(
        val planIds: Set<String>,
        val goalCount: Int,
        val observationIds: Set<String>,
        val activePlanId: String?,
        val dailyCalorieGoal: Int?,
    )

    private fun roomSnapshot(): RoomSnapshot {
        val settings = room { room.settingsDao().get() }?.toSettings()
        return RoomSnapshot(
            planIds = room { room.nutritionDao().getAllPlans() }.map { it.id }.toSet(),
            goalCount = room { room.bodyProgressDao().getAllGoals() }.size,
            observationIds = room { room.bodyProgressDao().getAllObservations() }
                .mapNotNull { it.toBodyObservation() }
                .map { it.id }
                .toSet(),
            activePlanId = room { room.nutritionDao().getActiveState() }?.activePlanId,
            dailyCalorieGoal = settings?.dailyCalorieGoal,
        )
    }

    // ─── Motores de pasos (solo UI) ──────────────────────────────────────────

    /** Afirma el cursor, ejecuta la respuesta por UI y exige el siguiente paso. */
    private fun answerAndContinue(step: SetupStepId, expectedNext: SetupStepId, answer: () -> Unit = {}) {
        assertCurrentStep(step)
        answer()
        continueTo(step, expectedNext)
    }

    private fun assertCurrentStep(step: SetupStepId) {
        val current = vm.state.value.currentStep
        assertEquals("cursor del wizard", step, current)
        composeRule.onNodeWithTag(stepTag(step)).assertIsDisplayed()
    }

    /** El CTA del Host es el ÚNICO avance: comprueba estado, pulsa y exige destino. */
    private fun continueTo(step: SetupStepId, expectedNext: SetupStepId) {
        awaitState("CTA habilitado en ${step.name}") { it.canConfirmStep }
        composeRule.onNodeWithTag(CTA).assertIsEnabled()
        composeRule.onNodeWithTag(CTA).performClick()
        awaitState("cursor en ${expectedNext.name} tras ${step.name}") { it.currentStep == expectedNext }
        val errors = vm.state.value.errors
        assertTrue("errores al confirmar ${step.name} → ${expectedNext.name}: $errors", errors.isEmpty())
        composeRule.onNodeWithTag(stepTag(expectedNext)).assertIsDisplayed()
    }

    /**
     * Clic real sobre un rótulo (opción, omitir o fila): el nodo se muestra
     * antes de pulsar y la respuesta debe llegar al borrador (revisión monótona).
     */
    private fun clickOption(step: SetupStepId, label: String) {
        val before = vm.state.value.draft.revision
        ensureVisible(step, label)
        composeRule.onNodeWithText(label).performClick()
        awaitState("respuesta «$label» persistida en ${step.name}") { it.draft.revision > before }
    }

    /**
     * Escribe en los campos de texto del paso. El campo se localiza por su
     * rótulo cuando la semántica lo fusiona y, si no, por orden (con la
     * comprobación de cuántos campos expone el paso). Los campos vienen
     * precargados de Ajustes reales, así que primero se selecciona todo el
     * contenido y luego se escribe: el resultado final se verifica campo a campo.
     */
    private fun typeInto(step: SetupStepId, vararg fields: Pair<String, String>) {
        val before = vm.state.value.draft.revision
        var needsTyping = false
        fields.forEachIndexed { index, (label, value) ->
            val field = fieldNode(step, label, index)
            if (editableTextOf(field) != value) needsTyping = true
            ensureFieldValue(step, field, label, value)
        }
        if (needsTyping) {
            awaitState("texto de ${step.name} persistido en el borrador") { it.draft.revision > before }
        }
    }

    private fun fieldNode(step: SetupStepId, label: String, index: Int): SemanticsNodeInteraction {
        val byLabel = composeRule.onAllNodes(hasInsertTextAtCursorAction() and hasText(label, substring = true))
        val labelMatches = byLabel.fetchSemanticsNodes().size
        if (labelMatches == 1) return byLabel[0]
        if (labelMatches > 1) {
            fail("El rótulo «$label» identifica $labelMatches campos en ${step.name}")
        }
        val all = composeRule.onAllNodes(hasInsertTextAtCursorAction())
        val total = all.fetchSemanticsNodes().size
        if (total < index + 1) {
            fail("El paso ${step.name} expone $total campos editables y se necesita el ${index + 1} («$label»)")
        }
        return all[index]
    }

    private fun ensureFieldValue(step: SetupStepId, field: SemanticsNodeInteraction, label: String, value: String) {
        repeat(MAX_TYPE_ATTEMPTS) {
            val current = editableTextOf(field)
            if (current == value) return
            if (current.isNotEmpty()) field.performTextInputSelection(TextRange(0, current.length))
            field.performTextInput(value)
            runCatching { composeRule.waitUntil(TYPE_TIMEOUT_MS) { editableTextOf(field) == value } }
        }
        val final = editableTextOf(field)
        if (final != value) {
            fail("El campo «$label» de ${step.name} quedó en «$final» y no en «$value»")
        }
    }

    /** Rueda de altura: tocar el valor central/literal declara exactamente ese cm. */
    private fun setHeightCm(target: Int) {
        if (heightCandidateCm() != target) {
            scrollControlTo(WHEEL_CONTENT_DESCRIPTION, target - WizardHeightScale.MIN_CM)
        }
        if (heightCandidateCm() == target) {
            composeRule.onNodeWithTag(HEIGHT_VALUE_TAG).performClick()
        } else {
            composeRule.onNodeWithText(WizardHeightScale.formatCm(target)).performClick()
        }
        awaitState("altura declarada = $target cm") { it.draft.heightCm == target.toDouble() }
    }

    /**
     * Regla de peso: la posición inicial no es respuesta. El objetivo se centra
     * con la acción de scroll del propio control y se confirma con UN toque
     * real: con el setter de M2 el toque deja el paso declarado aunque el valor
     * precargado de Ajustes ya coincidiera con el objetivo (sin idas y vueltas).
     */
    private fun setWeightKg(target: Int) {
        emitWeightCandidate(target)
        awaitState("peso declarado = $target kg") { it.draft.weightKg == target.toDouble() }
        assertTrue(
            "peso $target kg declarado por el usuario (declaredSteps=${vm.state.value.draft.declaredSteps})",
            SetupStepId.WEIGHT in vm.state.value.draft.declaredSteps,
        )
    }

    /** Centra el candidato de la regla y lo confirma con un toque real. */
    private fun emitWeightCandidate(target: Int) {
        if (weightCandidateKg() != target.toDouble()) {
            scrollControlTo(RULE_CONTENT_DESCRIPTION, weightTickIndex(target))
        }
        if (weightCandidateKg() == target.toDouble()) {
            composeRule.onNodeWithTag(WEIGHT_VALUE_TAG).performClick()
        } else {
            composeRule.onNodeWithText(WizardWeightScale.format(target.toDouble())).performClick()
        }
    }

    private fun weightTickIndex(targetKg: Int): Int {
        val range = WizardWeightScale.displayRange(WizardMassUnit.KG)
        val firstStep = Math.round(range.start * TICKS_PER_KG).toInt()
        return Math.round(targetKg * TICKS_PER_KG).toInt() - firstStep
    }

    /**
     * Centra un valor del control (rueda/regla) con la propia acción de scroll
     * del `LazyList` contenido en el control identificado por su
     * contentDescription: nunca se arrastra a ciegas.
     */
    private fun scrollControlTo(contentDescription: String, index: Int) {
        composeRule.onNode(
            hasScrollAction() and hasAnyAncestor(hasContentDescription(contentDescription)),
        ).performScrollToIndex(index)
        composeRule.waitForIdle()
    }

    private fun heightCandidateCm(): Int =
        candidateStateDescription(WHEEL_CONTENT_DESCRIPTION)
            .substringBefore(".")
            .toIntOrNull()
            ?: Int.MIN_VALUE

    private fun weightCandidateKg(): Double =
        candidateStateDescription(RULE_CONTENT_DESCRIPTION)
            .substringBefore(" ")
            .replace(',', '.')
            .toDoubleOrNull()
            ?: Double.NaN

    private fun candidateStateDescription(contentDescription: String): String = try {
        composeRule.onNodeWithContentDescription(contentDescription)
            .fetchSemanticsNode()
            .config[SemanticsProperties.StateDescription]
    } catch (error: Throwable) {
        ""
    }

    /**
     * Espera los candidatos reales. Si no llegan, distingue el aviso REAL de la
     * UI («no hay un plan compatible…») de un simple tiempo de espera: nunca se
     * fabrica material para forzar un candidato.
     */
    private fun awaitPlanCandidates() {
        try {
            composeRule.waitUntil(CANDIDATE_TIMEOUT_MS) {
                val state = vm.state.value
                !state.isCandidateLoading && (
                    state.availablePlanCandidates.isNotEmpty() ||
                        state.previewError != null ||
                        state.errors.containsKey("candidates")
                    )
            }
            composeRule.waitForIdle()
        } catch (error: Throwable) {
            if (existsInTree(NO_COMPATIBLE_PLAN_LABEL, substring = true)) {
                fail(
                    "Sin candidatos: la UI confirma «no hay un plan compatible con tus respuestas» con el " +
                        "material declarado (${vm.state.value.describe()})",
                )
            }
            fail("Timeout esperando candidatos de plan · ${vm.state.value.describe()} (${error.message})")
        }
    }

    /** Candidatos reales; si no llegan, se reproduce el error REAL del motor. */
    private fun selectPlanCandidate(preferredSource: String) {
        awaitPlanCandidates()
        val state = vm.state.value
        val problem = state.previewError ?: state.errors["candidates"]
        if (problem != null) {
            fail("La generación de candidatos falló con el error REAL: $problem (${state.describe()})")
            return
        }
        val all = state.availablePlanCandidates.ifEmpty { state.planCandidates }
        val chosen = all.firstOrNull { it.source == preferredSource }
        if (chosen == null) {
            fail(
                "No hay candidato $preferredSource entre ${all.map { "${it.source}=${it.title}" }}; " +
                    "no se fabrica material para forzar uno (${state.describe()})",
            )
            return
        }
        if (chosen.id !in vm.state.value.planCandidates.map { candidate -> candidate.id }) {
            if (!existsInTree(MORE_CANDIDATES_LABEL, substring = true)) {
                fail(
                    "El candidato $preferredSource «${chosen.title}» no está visible y la UI no ofrece " +
                        "«$MORE_CANDIDATES_LABEL»",
                )
                return
            }
            composeRule.onNodeWithText(MORE_CANDIDATES_LABEL, substring = true).performClick()
            awaitState("candidato «${chosen.title}» visible") { current ->
                chosen.id in current.planCandidates.map { candidate -> candidate.id }
            }
        }
        val before = vm.state.value.draft.revision
        ensureVisible(SetupStepId.PLAN, chosen.title)
        composeRule.onNodeWithText(chosen.title).performClick()
        awaitState("plan «${chosen.title}» elegido") {
            it.draft.selectedCatalogId == chosen.id && it.draft.revision > before
        }
    }

    private fun awaitTrainingPreviewReady() {
        awaitState("programa preparado", PREVIEW_TIMEOUT_MS) { state ->
            state.programPreview != null && state.previewError == null && !state.isPreviewLoading
        }
    }

    private fun awaitNutritionPrepared() {
        awaitState("preparación nutricional publicada", PREVIEW_TIMEOUT_MS) { it.nutritionPreparation != null }
        awaitState("nutrición sin errores", PREVIEW_TIMEOUT_MS) { it.nutritionErrors.isEmpty() }
    }

    /**
     * Oráculo de UI para la cifra de calorías: el valor del VM es correcto, pero
     * el Text REAL solo existe cuando se compone. En Resultado hay UN solo
     * Text completo («Referencia diaria: 2100 kcal · 150 g proteína · …»), así
     * que se ancla el fragmento entero con el numeral exacto; en Revisión sí
     * existe el valor solitario. Nunca una subcadena suelta («210» no casa en
     * «2100»). Si no llega, vuelca por qué no se compuso: días con objetivo
     * publicados, el aviso de «sin objetivos» y el prefijo real en pantalla.
     * La cifra exacta se sigue exigiendo en DOM y en Room por las aserciones.
     */
    private fun awaitCaloriesShownInUi(calorieTarget: Int) {
        // Revisión: valor solitario EXACTO. Resultado: UN solo Text completo, así
        // que el numeral va anclado entre «Referencia diaria: » y « kcal ·»
        // (substring sobre el fragmento entero: «210» nunca casa en «2100»).
        val standalone = listOf("$calorieTarget kcal", "$calorieTarget.0 kcal")
        val caption = "Referencia diaria: $calorieTarget kcal ·"
        fun rendered(): Boolean =
            standalone.any { needle -> existsInTree(needle) } || existsInTree(caption, substring = true)
        val shown = runCatching {
            composeRule.waitUntil(STEP_TIMEOUT_MS) { rendered() }
            rendered()
        }.getOrDefault(false)
        if (shown) return
        val state = vm.state.value
        fail(
            "No se compone la cifra de $calorieTarget kcal (esperado Text exacto «$calorieTarget kcal» o " +
                "«$calorieTarget.0 kcal», o el caption anclado «$caption») · " +
                "días con objetivo=${state.nutritionPreparation?.days?.size} · " +
                "aviso «$NO_DAYS_LABEL»=${existsInTree(NO_DAYS_LABEL)} · " +
                "prefijo real en pantalla=${existsInTree(REFERENCE_PREFIX, substring = true)} · " +
                state.describe(),
        )
    }

    private fun assertConfigurationMode(expected: NutritionConfigurationMode) {
        assertEquals(
            "modo de nutrición declarado en NUTRITION_START",
            expected,
            vm.state.value.draft.nutritionDraft?.configurationMode,
        )
    }

    // ─── Semántica: visibilidad, texto y esperas ─────────────────────────────

    private fun stepTag(step: SetupStepId): String = "setup-step-${step.name}"

    private fun ageFieldLabel(): String =
        SetupStepDefinitions.of(SetupStepId.AGE)?.unit?.let { "Edad ($it)" } ?: "Edad"

    private fun sessionTimeFieldLabel(): String =
        SetupStepDefinitions.of(SetupStepId.SESSION_TIME)?.range?.unit
            ?.let { "Minutos por sesión ($it)" }
            ?: "Minutos por sesión"

    /** Muestra un nodo antes de pulsarlo: scroll semántico y, si no, arrastre. */
    private fun ensureVisible(step: SetupStepId, label: String) {
        if (isDisplayed(label)) return
        runCatching { composeRule.onNodeWithText(label).performScrollTo() }
        if (isDisplayed(label)) return
        repeat(MAX_SWIPES) {
            composeRule.onNodeWithTag(stepTag(step)).performTouchInput { swipeUp() }
            if (isDisplayed(label)) return
        }
        val matches = try {
            composeRule.onAllNodesWithText(label).fetchSemanticsNodes().size
        } catch (error: Throwable) {
            -1
        }
        fail(
            "No pude mostrar «$label» en el paso ${step.name} (nodos=$matches, scroll semántico y " +
                "arrastre agotados) · ${vm.state.value.describe()}",
        )
    }

    private fun isDisplayed(label: String): Boolean = try {
        composeRule.onNodeWithText(label).assertIsDisplayed()
        true
    } catch (error: Throwable) {
        false
    }

    private fun existsInTree(needle: String, substring: Boolean = false): Boolean = try {
        composeRule.onAllNodesWithText(needle, substring).fetchSemanticsNodes().isNotEmpty()
    } catch (error: Throwable) {
        false
    }

    private fun assertInTree(needle: String, substring: Boolean = false, what: String = needle) {
        assertTrue("No aparece en la pantalla actual: «$what»", existsInTree(needle, substring))
    }

    private fun editableFieldCount(): Int = try {
        composeRule.onAllNodes(hasInsertTextAtCursorAction()).fetchSemanticsNodes().size
    } catch (error: Throwable) {
        0
    }

    private fun editableTextOf(field: SemanticsNodeInteraction): String = try {
        field.fetchSemanticsNode().config[SemanticsProperties.EditableText].text
    } catch (error: Throwable) {
        ""
    }

    /** Espera acotada sobre el ESTADO del VM; si no llega, vuelca el diagnóstico. */
    private fun awaitState(
        what: String,
        timeoutMs: Long = STEP_TIMEOUT_MS,
        condition: (SetupWizardState) -> Boolean,
    ) {
        try {
            composeRule.waitUntil(timeoutMs) { condition(vm.state.value) }
            // El estado ya cambió: deja la UI al día (recomposición) antes de la
            // siguiente interacción, para que ninguna respuesta se apoye en una
            // pantalla desactualizada.
            composeRule.waitForIdle()
        } catch (error: Throwable) {
            fail("Timeout esperando $what · ${vm.state.value.describe()} (${error.message})")
        }
    }

    /** Espera acotada sobre la UI; los falros de búsqueda cuentan como «aún no». */
    private fun awaitUi(what: String, timeoutMs: Long = STEP_TIMEOUT_MS, condition: () -> Boolean) {
        val satisfied = runCatching {
            composeRule.waitUntil(timeoutMs) { runCatching { condition() }.getOrDefault(false) }
        }
        if (satisfied.isFailure) {
            fail("Timeout esperando en UI: $what · ${vm.state.value.describe()}")
        }
    }

    private fun SetupWizardState.describe(): String =
        "paso=$currentStep canConfirm=$canConfirmStep validación=$stepValidation " +
            "errores=$errors machine=$machineState candidatos=${availablePlanCandidates.size} " +
            "preview=${previewError ?: "ok"} nutrición=${nutritionErrors} " +
            "rings=${ringsPreviewError ?: "ok"} baterías=${ringsBatteriesPreview != null}"

    private fun <T> room(block: suspend () -> T): T = runBlocking { block() }

    // ─── Constantes del recorrido ────────────────────────────────────────────

    private companion object {
        const val CTA = "setup-continue"
        const val CTA_REVIEW_LABEL = "Activar y entrar a KPKN"
        const val HEIGHT_VALUE_TAG = "setup-height-value"
        const val WEIGHT_VALUE_TAG = "setup-weight-value"
        const val WHEEL_CONTENT_DESCRIPTION = "Rueda de altura en centímetros"
        const val RULE_CONTENT_DESCRIPTION = "Regla de peso en kilogramos"
        const val MORE_CANDIDATES_LABEL = "Ver más opciones"
        const val NO_COMPATIBLE_PLAN_LABEL = "no hay un plan compatible"
        const val NO_DAYS_LABEL = "Todavía no hay objetivos por fecha."
        const val REFERENCE_PREFIX = "Referencia diaria:"
        const val VM_STORE_KEY = "setup-wizard-full-journey"

        const val ACTIVATION_CONFIRM_LABEL = "Confirmo la activación"
        const val RECIPE_CONFIRM_LABEL = "Confirmo la rotación y la duración reales"
        const val SAVE_LABEL = "Guardar"

        const val NAME_FIELD_LABEL = "Nombre"
        const val BARBELL_ROW_LABEL = "Barra"
        const val BARBELL_FIELD_LABEL = "Peso de la barra (kg)"
        const val NEXT_LABEL = "Siguiente"
        const val SUPPORT_CHOICE_LABEL = "Soportes, rack o banco"
        const val SUPPORT_ID = "support"
        const val PLATE_WEIGHT_FIELD_LABEL = "Peso del disco (kg)"
        const val PLATE_COUNT_FIELD_LABEL = "Discos por lado"
        const val DUMBBELL_FIELD_LABEL = "Peso por unidad (kg)"
        const val MACHINE_ADD_LABEL = "Añadir máquina o polea"
        const val MACHINE_KIND_CABLE_LABEL = "Polea"
        const val MACHINE_KIND_CABLE_ID = "cable"
        const val STATION_NAME = "Estación de polea"
        const val MACHINE_NAME_FIELD_LABEL = "Nombre de la máquina"
        const val MACHINE_MIN_FIELD_LABEL = "Carga mínima (kg)"
        const val MACHINE_MAX_FIELD_LABEL = "Carga máxima (kg) · opcional"
        const val MACHINE_INC_FIELD_LABEL = "Incremento por paso (kg)"
        const val MACHINE_BASE_FIELD_LABEL = "Carga base del carro (kg)"
        const val MACHINE_MIN = "10"
        const val MACHINE_MAX = "90"
        const val MACHINE_INC = "2.5"
        const val MACHINE_BASE = "20"
        const val CALORIES_FIELD_LABEL = "Calorías (kcal)"
        const val PROTEIN_FIELD_LABEL = "Proteína (g)"
        const val CARBS_FIELD_LABEL = "Hidratos (g)"
        const val FAT_FIELD_LABEL = "Grasas (g)"

        const val TEST_NAME = "QA M14"
        const val TARGET_HEIGHT_CM = 175
        const val AGE_AUTO = "30"
        const val AGE_OTHER = "34"
        const val WEIGHT_AUTO = 77
        const val WEIGHT_OTHER = 70
        const val MANUAL_KCAL = "2100"
        const val MANUAL_PROTEIN = "150"
        const val MANUAL_CARBS = "250"
        const val MANUAL_FAT = "55"

        const val TICKS_PER_KG = 10.0

        const val MAX_TYPE_ATTEMPTS = 3
        const val MAX_SWIPES = 4
        const val STEP_TIMEOUT_MS = 30_000L
        const val TYPE_TIMEOUT_MS = 5_000L
        const val PREVIEW_TIMEOUT_MS = 120_000L
        const val CANDIDATE_TIMEOUT_MS = 120_000L
        const val COMMIT_TIMEOUT_MS = 120_000L
        const val SETUP_TIMEOUT_MS = 120_000L
        const val TEN_MINUTES_MS = 600_000L
    }
}

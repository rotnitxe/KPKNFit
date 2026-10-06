package com.example.kpkn.screens.onboarding

import android.app.Application
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Process
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasInsertTextAtCursorAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
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
import com.example.kpkn.data.models.ApparatusPresence
import com.example.kpkn.data.models.BodyMetric
import com.example.kpkn.data.models.BodyObservationQuality
import com.example.kpkn.data.models.CalculationOrigin
import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.models.EquipmentCategory
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.exercises.catalogv2.ApprovedAssetExerciseCatalogRepositoryV2
import com.example.kpkn.data.onboarding.persistenceFactory
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogStateV2
import com.example.kpkn.domain.nutrition.NutritionConfigurationMode
import com.example.kpkn.domain.nutrition.NutritionPlanPreparationStatus
import com.example.kpkn.domain.onboarding.SetupAnswerProvenance
import com.example.kpkn.domain.onboarding.SetupStepDefinitions
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.WizChatMachineState
import com.example.kpkn.domain.training.ProgramExecutionContract
import com.example.kpkn.data.models.resolvedSchedulePlan
import com.example.kpkn.screens.onboarding.design.WizardHeightScale
import com.example.kpkn.screens.onboarding.design.WizardMassUnit
import com.example.kpkn.screens.onboarding.design.WizardWeightScale
import com.example.kpkn.screens.home.HOME_PROGRAMS_ROW_TAG
import com.example.kpkn.screens.home.HomeScreen
import com.example.kpkn.screens.home.HomeViewModel
import com.example.kpkn.screens.home.homeProgramCardTag
import com.example.kpkn.ui.theme.AppThemeMode
import com.example.kpkn.ui.theme.KPKNTheme
import java.time.LocalDate
import java.io.File
import java.io.FileOutputStream
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
 * Recorridos actuales — plan nativo Fuerza y músculo a cinco días, Músculo E0
 * de principiante a tres días y protocolo autorado del catálogo unificado,
 * cruzados con automática/manual/solo registro. Ningún recorrido nuevo responde
 * ROUTE: empieza por material y elige después uno de los cuatro perfiles.
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
 *  - La disponibilidad se declara por categorías y presencia, sin kilos ni
 *    cantidades. «Gimnasio completo» declara las categorías, pero no confirma
 *    aparatos; el testigo confirma banco, rack, barra de dominadas y polea
 *    alta/baja por sus controles Sí, y deja hack squat desconocido.
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
    private lateinit var homeVisible: MutableState<Boolean>
    private lateinit var homeViewModel: HomeViewModel
    private val activations = AtomicInteger(0)
    private val cancellations = AtomicInteger(0)

    // ─── Ciclo de vida ────────────────────────────────────────────────────────

    @Before
    fun setUp() {
        assertRunsOnQaUser10()
        app = ApplicationProvider.getApplicationContext()
        assertCatalogCanBootstrapOffline()
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

    /**
     * The VM's exercise-catalog bootstrap reads the bundled approved asset.
     * Make the device precondition explicit so a successful candidate scan is
     * observed without validated Internet, rather than assumed to be offline.
     */
    private fun assertCatalogCanBootstrapOffline() {
        val connectivity = checkNotNull(app.getSystemService(ConnectivityManager::class.java))
        val activeNetwork = connectivity.activeNetwork
        val capabilities = activeNetwork?.let { network -> connectivity.getNetworkCapabilities(network) }
        val validatedInternet = capabilities?.let { network ->
            network.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                network.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } == true
        assertFalse(
            "Este recorrido comprueba bootstrap local y exige el AVD de QA sin Internet validado " +
                "(network=$activeNetwork, capabilities=$capabilities)",
            validatedInternet,
        )
    }

    /** App-specific external storage is isolated to QA10 and pullable without repository artifacts. */
    private fun createQ6ScreenshotDirectory(scenario: PlanScenario): File {
        val externalRoot = checkNotNull(app.getExternalFilesDir("q6-ui-captures")) {
            "Q6 screenshots require mounted app-specific external files storage"
        }
        val outputDirectory = File(
            externalRoot,
            "${scenario.name.lowercase()}-${UUID.randomUUID()}",
        )
        check(outputDirectory.mkdirs() || outputDirectory.isDirectory) {
            "No se pudo crear el directorio aislado de capturas Q6: ${outputDirectory.absolutePath}"
        }
        emitQ6Artifact("Q6_UI_ARTIFACT_DIR=${outputDirectory.absolutePath}")
        return outputDirectory
    }

    /** Captures the actual visible Compose root as a PNG; null keeps other journeys unchanged. */
    @OptIn(ExperimentalTestApi::class)
    private fun captureQ6Screenshot(outputDirectory: File?, filename: String) {
        if (outputDirectory == null) return
        require(filename.matches(Regex("[a-z0-9-]+"))) { "Nombre de captura Q6 inválido: $filename" }
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val outputFile = File(outputDirectory, "$filename.png")
        val encoded = FileOutputStream(outputFile).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        bitmap.recycle()
        check(encoded && outputFile.isFile && outputFile.length() > 0L) {
            "No se pudo escribir la captura PNG Q6: ${outputFile.absolutePath}"
        }
        emitQ6Artifact("Q6_UI_SCREENSHOT=${outputFile.absolutePath}")
    }

    private fun emitQ6Artifact(message: String) {
        println(message)
        Log.i("KPKN_Q6_UI", message)
    }

    // ─── Matriz: perfil/fuente unificados × automática/manual/solo registro ──

    @Test
    fun fiveDayPowerbuildingAutomaticActivatesNativeProgramAndPlan() =
        runJourney(PlanScenario.POWERBUILDING_FIVE_DAY, NutritionFlavor.AUTOMATIC, captureQ6Screenshots = true)

    @Test
    fun fiveDayPowerbuildingSelfDefinedActivatesNativeProgramAndManualTargets() =
        runJourney(PlanScenario.POWERBUILDING_FIVE_DAY, NutritionFlavor.SELF_DEFINED)

    @Test
    fun fiveDayPowerbuildingTrackingOnlyActivatesProgramWithoutNutritionPlan() =
        runJourney(PlanScenario.POWERBUILDING_FIVE_DAY, NutritionFlavor.TRACKING_ONLY)

    @Test
    fun e0BodyweightMuscleBeginnerThreeDayThirtyMinuteJourneyActivatesNativePlan() =
        runJourney(PlanScenario.MUSCLE_E0_THREE_DAY, NutritionFlavor.TRACKING_ONLY, captureQ6Screenshots = true)

    @Test
    fun unifiedCatalogAutomaticStillActivatesAuthoredProtocolAndPlan() =
        runJourney(PlanScenario.AUTHORED_STRENGTH_PROTOCOL, NutritionFlavor.AUTOMATIC)

    @Test
    fun unifiedCatalogSelfDefinedStillActivatesAuthoredProtocolAndManualTargets() =
        runJourney(PlanScenario.AUTHORED_STRENGTH_PROTOCOL, NutritionFlavor.SELF_DEFINED)

    @Test
    fun unifiedCatalogTrackingOnlyStillActivatesAuthoredProtocolWithoutNutritionPlan() =
        runJourney(PlanScenario.AUTHORED_STRENGTH_PROTOCOL, NutritionFlavor.TRACKING_ONLY)

    /** Perfil, calendario y testigo real elegido dentro de la biblioteca unificada. */
    private enum class PlanScenario(
        val goalLabel: String,
        val experienceLabel: String,
        val candidateSource: String,
        val expectedCandidateId: String,
        val materialProfile: MaterialProfile,
        val daysPerWeek: Int,
        val minutesPerSession: Int,
        val weekdayLabels: List<String>,
        val weekdayIds: Set<Int>,
        val assertExactWeeklySessions: Boolean = false,
    ) {
        POWERBUILDING_FIVE_DAY(
            goalLabel = "Fuerza y músculo",
            experienceLabel = "Ya entreno con constancia",
            candidateSource = "NATIVE",
            expectedCandidateId = "native:powerbuilding-foundation-v2",
            materialProfile = MaterialProfile.GYM,
            daysPerWeek = 5,
            minutesPerSession = 60,
            weekdayLabels = listOf("Lunes", "Martes", "Jueves", "Viernes", "Sábado"),
            weekdayIds = setOf(1, 2, 4, 5, 6),
            assertExactWeeklySessions = true,
        ),
        MUSCLE_E0_THREE_DAY(
            goalLabel = "Músculo",
            experienceLabel = "Estoy empezando",
            candidateSource = "NATIVE",
            expectedCandidateId = "native:muscle-foundation-v2",
            materialProfile = MaterialProfile.E0_BODYWEIGHT,
            daysPerWeek = 3,
            minutesPerSession = 30,
            weekdayLabels = listOf("Lunes", "Miércoles", "Viernes"),
            weekdayIds = setOf(1, 3, 5),
            assertExactWeeklySessions = true,
        ),
        AUTHORED_STRENGTH_PROTOCOL(
            goalLabel = "Fuerza",
            experienceLabel = "Tengo experiencia",
            candidateSource = "PROTOCOL",
            expectedCandidateId = "protocol:coan-phillipi-dl",
            materialProfile = MaterialProfile.GYM,
            daysPerWeek = 1,
            minutesPerSession = 60,
            weekdayLabels = listOf("Jueves"),
            weekdayIds = setOf(4),
        ),
    }

    /** UI route into the material declarations used by a fresh draft. */
    private enum class MaterialProfile(
        val environmentChoiceLabel: String,
        val availabilityChoiceLabel: String?,
    ) {
        GYM("Gimnasio completo", null),
        E0_BODYWEIGHT("Entreno en casa", "Solo peso corporal"),
    }

    /** Modo de nutrición declarado en NUTRITION_START. */
    private enum class NutritionFlavor { AUTOMATIC, SELF_DEFINED, TRACKING_ONLY }

    // ─── Orquestación del recorrido ──────────────────────────────────────────

    private fun runJourney(
        scenario: PlanScenario,
        flavor: NutritionFlavor,
        captureQ6Screenshots: Boolean = false,
    ) {
        // Snapshot de la DB compartida ANTES de tocar nada: todas las
        // aserciones posteriores son sobre filas propias de ESTA alta.
        val pre = roomSnapshot()
        val screenshotDirectory = if (captureQ6Screenshots) createQ6ScreenshotDirectory(scenario) else null

        // Datos de la matriz: la recomendación automática exige ≥19 años
        // (NutritionIneligibility.UNDER_19), sexo de cálculo y los vitales.
        val ageText = if (flavor == NutritionFlavor.AUTOMATIC) AGE_AUTO else AGE_OTHER
        val weightKg = if (flavor == NutritionFlavor.AUTOMATIC) WEIGHT_AUTO else WEIGHT_OTHER
        val equationSexLabel = if (flavor == NutritionFlavor.AUTOMATIC) "Hombre" else "No lo sé"

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
        homeVisible = mutableStateOf(false)
        homeViewModel = HomeViewModel().also { viewModelStore.put("setup-home-${UUID.randomUUID()}", it) }
        activations.set(0)
        cancellations.set(0)
        composeRule.setContent {
            if (!wizardVisible.value) {
                SetupWelcomeScreen(onStart = { wizardVisible.value = true })
            } else if (homeVisible.value) {
                KPKNTheme {
                    HomeScreen(
                        themeMode = AppThemeMode.HIGH_CONTRAST,
                        onThemeChange = {},
                        viewModel = homeViewModel,
                    )
                }
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
        walkTraining(scenario = scenario, screenshotDirectory = screenshotDirectory)
        walkNutrition(flavor = flavor)
        walkRings()
        walkReviewAndActivate(
            scenario = scenario,
            flavor = flavor,
            pre = pre,
            draftId = draftId,
            weightKg = weightKg,
            ageText = ageText,
            screenshotDirectory = screenshotDirectory,
        )
    }

    // ─── Bienvenida ──────────────────────────────────────────────────────────

    private fun walkWelcome() {
        composeRule.onNodeWithText("Comenzar").assertIsDisplayed().performClick()
        // Tras la bienvenida el wizard abre su pantalla de arranque (nada completado) sobre la primera
        // pregunta; su botón se habilita cuando termina la animación de entrada.
        awaitUi("pantalla de arranque visible") {
            composeRule.onNodeWithTag(INTRO).assertIsDisplayed()
            true
        }
        awaitUi("botón Empezar habilitado") {
            composeRule.onNodeWithTag(INTRO_START).assertIsEnabled()
            true
        }
        composeRule.onNodeWithTag(INTRO_START).performClick()
        // La UI del wizard se compone al cerrar la bienvenida: espera explícita.
        awaitUi("paso NAME visible tras la bienvenida") {
            composeRule.onNodeWithTag(stepTag(SetupStepId.NAME)).assertIsDisplayed()
            true
        }
        assertEquals("el cursor arranca en NAME tras la bienvenida", SetupStepId.NAME, vm.state.value.currentStep)
    }

    // ─── Bloque 1: Datos básicos ─────────────────────────────────────────────

    private fun walkBasics(ageText: String, weightKg: Int, equationSexLabel: String) {
        answerAndContinue(SetupStepId.NAME, SetupStepId.HEIGHT) {
            typeInto(SetupStepId.NAME, NAME_FIELD_LABEL to TEST_NAME)
            vm.setAge(ageText.toInt())
            composeRule.waitUntil(STEP_TIMEOUT_MS) { vm.state.value.draft.ageYears == ageText.toInt() }
        }
        // Altura y peso: el PRODUCTO elige el layout según el hueco real de la
        // viewport y la escala de fuente (`currentAnthropometryLayout` →
        // `WizardAnthropometryFit.fits`, SetupWizardSteps.kt / WizardScaffold.kt):
        //  - SEPARATE: paso HEIGHT con la rueda de altura y paso WEIGHT aparte.
        //  - COMBINED: el paso HEIGHT muestra las reglas de altura y peso juntas y
        //    su único CTA (`submitAnthropometryPair`) confirma ambas, así que el
        //    cursor salta de HEIGHT a EQUATION_SEX sin pasar por WEIGHT.
        // La prueba no fija la viewport: detecta qué control está en pantalla y
        // conduce SOLO los controles reales de ese layout.
        assertCurrentStep(SetupStepId.HEIGHT)
        val layout = awaitAnthropometryLayout()
        // Queda en stdout y logcat: la evidencia dice qué layout recorrió cada prueba.
        println("WIZARD_ANTHROPOMETRY_LAYOUT=$layout")
        Log.i("KPKN_Q6_UI", "WIZARD_ANTHROPOMETRY_LAYOUT=$layout")
        when (layout) {
            AnthropometryLayout.SEPARATE -> {
                answerAndContinue(SetupStepId.HEIGHT, SetupStepId.WEIGHT) {
                    setHeightCm(TARGET_HEIGHT_CM, layout)
                }
                answerAndContinue(SetupStepId.WEIGHT, SetupStepId.EQUATION_SEX) {
                    setWeightKg(weightKg)
                }
            }
            AnthropometryLayout.COMBINED -> {
                answerAndContinue(SetupStepId.HEIGHT, SetupStepId.EQUATION_SEX) {
                    setHeightCm(TARGET_HEIGHT_CM, layout)
                    // El CTA combinado solo se habilita con el peso ya declarado
                    // (`ctaReady` en SetupWizardSteps.kt): `continueTo` lo exige.
                    setWeightKg(weightKg)
                }
                // El CTA único confirma las DOS medidas como declaradas por el usuario.
                val answers = vm.state.value.draft.stepProgress.answers
                assertEquals(
                    "altura confirmada como declarada en el CTA combinado",
                    SetupAnswerProvenance.USER_DECLARED,
                    answers[SetupStepId.HEIGHT],
                )
                assertEquals(
                    "peso confirmado como declarado en el CTA combinado",
                    SetupAnswerProvenance.USER_DECLARED,
                    answers[SetupStepId.WEIGHT],
                )
            }
        }
        answerAndContinue(SetupStepId.EQUATION_SEX, SetupStepId.BODY_FAT) {
            clickOption(SetupStepId.EQUATION_SEX, equationSexLabel)
        }
        // Grasa corporal: omisión EXPLÍCITA («Omitir este paso»), nunca un porcentaje inventado.
        // El paso ya no ofrece una tarjeta «No lo sé»: es la figura con slider más un campo
        // opcional de medición exacta, y omitir deja la respuesta declarada sin valor.
        // La figura de arranque (≈25 %) NO es una respuesta: sin dato previo en Ajustes el
        // paso dice «Sin dato todavía» y Continuar sigue bloqueado hasta actuar (C1).
        assertCurrentStep(SetupStepId.BODY_FAT)
        if (vm.state.value.draft.importedBodyFatPercent == null) {
            assertInTree("Sin dato todavía")
            composeRule.onNodeWithTag(CTA).assertIsNotEnabled()
        }
        answerAndContinue(SetupStepId.BODY_FAT, SetupStepId.MILESTONE_BASICS) {
            clickOption(SetupStepId.BODY_FAT, "Omitir este paso")
            awaitUi("estado «Omitido» visible tras omitir la grasa corporal") { existsInTree("Omitido") }
        }
        val bodyFatDraft = vm.state.value.draft
        assertEquals(
            "omitir deja la fuente como omitida (limpia lo elegido)",
            SetupBodyFatSource.UNKNOWN,
            bodyFatDraft.bodyFatSource,
        )
        assertNull("omitir no fabrica un porcentaje de grasa", bodyFatDraft.bodyFatPercent)
        assertTrue(
            "omitir no deja fuente MEASURED ni VISUAL_ESTIMATE (fuente=${bodyFatDraft.bodyFatSource})",
            bodyFatDraft.bodyFatSource != SetupBodyFatSource.MEASURED &&
                bodyFatDraft.bodyFatSource != SetupBodyFatSource.VISUAL_ESTIMATE,
        )
        assertEquals(
            "la omisión queda registrada como respuesta del usuario",
            SetupAnswerProvenance.USER_DECLARED,
            bodyFatDraft.stepProgress.answers[SetupStepId.BODY_FAT],
        )
        answerAndContinue(SetupStepId.MILESTONE_BASICS, SetupStepId.EXPERIENCE)
    }

    // ─── Bloque 2: Entreno ───────────────────────────────────────────────────

    private fun walkTraining(scenario: PlanScenario, screenshotDirectory: File?) {
        answerAndContinue(SetupStepId.EXPERIENCE, SetupStepId.EQUIPMENT) {
            clickOption(SetupStepId.EXPERIENCE, scenario.experienceLabel)
        }
        assertTrue(
            "el recorrido productivo nuevo no contiene ROUTE",
            SetupStepId.ROUTE !in SetupStepGraph.stepIds(vm.state.value.draft.stepContext()),
        )

        answerAndContinue(SetupStepId.EQUIPMENT, SetupStepId.AVAILABILITY) {
            clickOption(SetupStepId.EQUIPMENT, scenario.materialProfile.environmentChoiceLabel)
        }
        assertCurrentStep(SetupStepId.AVAILABILITY)
        if (scenario.materialProfile == MaterialProfile.E0_BODYWEIGHT) {
            assertInTree("Solo peso corporal")
        } else {
            assertInTree("¿Qué tienes disponible?", substring = true)
        }
        captureQ6Screenshot(screenshotDirectory, "material-options")
        assertTrue(
            "el paso GOAL aparece después del material",
            SetupStepGraph.indexOf(SetupStepId.AVAILABILITY, vm.state.value.draft.stepContext()) <
                SetupStepGraph.indexOf(SetupStepId.GOAL, vm.state.value.draft.stepContext()),
        )

        answerAndContinue(SetupStepId.AVAILABILITY, SetupStepId.GOAL) {
            when (scenario.materialProfile) {
                MaterialProfile.GYM -> {
                    // «Gimnasio completo» confirma categorías, no aparatos concretos.
                    // Confirmar solo los soportes/configuraciones que este testigo usa.
                    confirmApparatus("bench_flat", isSupport = true)
                    confirmApparatus("squat_rack", isSupport = true)
                    confirmApparatus("pullup_bar", isSupport = true)
                    confirmApparatus("cable_high_low", isSupport = false)
                }
                MaterialProfile.E0_BODYWEIGHT -> clickOption(
                    SetupStepId.AVAILABILITY,
                    checkNotNull(scenario.materialProfile.availabilityChoiceLabel),
                )
            }
            captureQ6Screenshot(screenshotDirectory, "material-declared")
        }
        val declaredAvailability = checkNotNull(vm.state.value.draft.trainingOptions.availability)
        when (scenario.materialProfile) {
            MaterialProfile.GYM -> {
                assertEquals(
                    "el aparato no declarado sigue desconocido aunque se eligió categoría Máquinas",
                    ApparatusPresence.UNKNOWN,
                    declaredAvailability.apparatus["hack_squat"] ?: ApparatusPresence.UNKNOWN,
                )
            }
            MaterialProfile.E0_BODYWEIGHT -> {
                assertEquals("E0: categorías vacías confirmadas", emptySet<EquipmentCategory>(), declaredAvailability.categories)
                assertTrue("E0: sin presencias inventadas", declaredAvailability.apparatus.isEmpty() && declaredAvailability.supports.isEmpty())
                assertTrue("E0: sin perfiles legacy de equipo", vm.state.value.draft.equipment.isEmpty())
            }
        }

        // La vuelta al material conserva respuestas/presencias; volver a avanzar
        // llega de nuevo a GOAL sin pasar por ninguna ruta legacy.
        composeRule.onNodeWithContentDescription(BACK_LABEL).performClick()
        awaitState("Atrás desde perfiles vuelve al material", STEP_TIMEOUT_MS) {
            it.currentStep == SetupStepId.AVAILABILITY
        }
        when (scenario.materialProfile) {
            MaterialProfile.GYM -> assertEquals(
                ApparatusPresence.PRESENT,
                vm.state.value.draft.trainingOptions.availability?.supports?.get("bench_flat"),
            )
            MaterialProfile.E0_BODYWEIGHT -> assertEquals(
                EquipmentAvailability(emptySet()),
                vm.state.value.draft.trainingOptions.availability,
            )
        }
        continueTo(SetupStepId.AVAILABILITY, SetupStepId.GOAL)

        val visibleGoals = listOf("Fuerza", "Músculo", "Fuerza y músculo", "Atleta completo")
        visibleGoals.forEach { assertInTree(it) }
        assertFalse("no se ofrecen etiquetas legacy de meta", existsInTree("Salud y condición"))
        assertFalse("no se ofrece Fuerza + cardio como quinta meta", existsInTree("Fuerza + cardio"))
        captureQ6Screenshot(screenshotDirectory, "goal-profiles")
        answerAndContinue(SetupStepId.GOAL, SetupStepId.DAYS) {
            clickOption(SetupStepId.GOAL, scenario.goalLabel)
        }
        if (scenario == PlanScenario.MUSCLE_E0_THREE_DAY) {
            assertEquals("E0 selecciona Músculo", "Músculo", vm.state.value.draft.goal?.label)
            assertEquals("E0 selecciona nivel principiante", "Estoy empezando", vm.state.value.draft.experience?.label)
        }

        answerAndContinue(SetupStepId.DAYS, SetupStepId.WEEKDAYS) {
            clickOption(SetupStepId.DAYS, "${scenario.daysPerWeek} ${if (scenario.daysPerWeek == 1) "día" else "días"}")
            captureQ6Screenshot(screenshotDirectory, "schedule-frequency")
        }
        answerAndContinue(SetupStepId.WEEKDAYS, SetupStepId.SESSION_TIME) {
            scenario.weekdayLabels.forEach { weekday -> clickOption(SetupStepId.WEEKDAYS, weekday) }
            captureQ6Screenshot(screenshotDirectory, "schedule-weekdays")
        }
        assertCurrentStep(SetupStepId.SESSION_TIME)
        composeRule.onNodeWithTag(CTA).assertIsNotEnabled()
        answerAndContinue(SetupStepId.SESSION_TIME, SetupStepId.VOLUME_TECHNIQUE) {
            typeInto(SetupStepId.SESSION_TIME, sessionTimeFieldLabel() to scenario.minutesPerSession.toString())
            captureQ6Screenshot(screenshotDirectory, "session-time")
        }
        assertEquals("sesiones semanales declaradas", scenario.daysPerWeek, vm.state.value.draft.daysPerWeek)
        assertEquals("días declarados", scenario.weekdayIds, vm.state.value.draft.selectedWeekdays)
        assertEquals("minutos explícitos", scenario.minutesPerSession, vm.state.value.draft.minutesPerSession)

        // El objetivo infiere el estilo, así que no se recorre STYLE.
        answerAndContinue(SetupStepId.VOLUME_TECHNIQUE, SetupStepId.VOLUME_CONSISTENCY) {
            clickOption(
                SetupStepId.VOLUME_TECHNIQUE,
                if (scenario == PlanScenario.MUSCLE_E0_THREE_DAY) "Aprendiendo" else "Bastante estable",
            )
        }
        answerAndContinue(SetupStepId.VOLUME_CONSISTENCY, SetupStepId.VOLUME_STRENGTH) {
            clickOption(
                SetupStepId.VOLUME_CONSISTENCY,
                if (scenario == PlanScenario.MUSCLE_E0_THREE_DAY) "Irregular" else "Bastante constante",
            )
        }
        answerAndContinue(SetupStepId.VOLUME_STRENGTH, SetupStepId.VOLUME_MOBILITY) {
            clickOption(
                SetupStepId.VOLUME_STRENGTH,
                if (scenario == PlanScenario.MUSCLE_E0_THREE_DAY) "Inicial" else "Intermedia",
            )
        }
        answerAndContinue(SetupStepId.VOLUME_MOBILITY, SetupStepId.PRIORITIES) {
            clickOption(SetupStepId.VOLUME_MOBILITY, "Suficiente")
        }
        // Bolsa de orden vacía: válida y sin puntos fabricados.
        answerAndContinue(SetupStepId.PRIORITIES, SetupStepId.TRAINING_MAX)
        answerAndContinue(SetupStepId.TRAINING_MAX, SetupStepId.SPLIT) {
            clickOption(SetupStepId.TRAINING_MAX, "Todavía no")
        }
        answerAndContinue(SetupStepId.SPLIT, SetupStepId.PLAN) {
            clickOption(SetupStepId.SPLIT, "Recomendado para ti")
        }
        awaitPlanCandidates()
        composeRule.onNodeWithTag("setup-candidate-counts").assertIsDisplayed()
        assertTrue("la evaluación local terminó con candidatos evaluados", vm.state.value.candidateCounts.evaluated > 0)
        assertTrue("el candidato nativo publicado es viable", vm.state.value.candidateCounts.viable > 0)
        assertNull("PLAN requiere una selección explícita", vm.state.value.draft.selectedCatalogId)
        composeRule.onNodeWithTag(CTA).assertIsNotEnabled()
        captureQ6Screenshot(screenshotDirectory, "plan-candidates")
        answerAndContinue(SetupStepId.PLAN, SetupStepId.AUTOREGULATION) {
            selectPlanCandidate(scenario)
        }
        assertEquals(
            "selección exacta publicada en el catálogo unificado",
            scenario.expectedCandidateId,
            vm.state.value.draft.selectedCatalogId,
        )
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
                // Resultado pinta UN solo Text («Referencia diaria: … kcal · P g proteína · …»).
                assertInTree("${manual.proteinGoal} g proteína", substring = true)
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
        scenario: PlanScenario,
        flavor: NutritionFlavor,
        pre: RoomSnapshot,
        draftId: String,
        weightKg: Int,
        ageText: String,
        screenshotDirectory: File?,
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

        // Datos del plan según el modo (nada se rellena: se lee lo que hay).
        when (flavor) {
            NutritionFlavor.TRACKING_ONLY -> assertInTree("Solo registrar comidas")
            else -> {
                val plan = checkNotNull(vm.state.value.nutritionPlanPreview) { "plan en revisión" }
                awaitCaloriesShownInUi(plan.calorieTarget)
                // Revisión: fila «Proteína (media)» con el valor solitario «P g» (SetupReviewStep).
                if (flavor == NutritionFlavor.SELF_DEFINED) assertInTree("${plan.proteinGoal} g")
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
        val selectedPreview = checkNotNull(vm.state.value.programPreview) { "programa preparado para revisión" }
        assertEquals("revisión del candidato seleccionado", scenario.expectedCandidateId, vm.state.value.draft.selectedCatalogId)
        ProgramExecutionContract.requireExecutable(selectedPreview)
        if (scenario.materialProfile == MaterialProfile.E0_BODYWEIGHT) {
            assertBodyweightOnlyMaterialization(selectedPreview, "preview de revisión")
        }
        assertFirstWeekExercisesShown(scenario)
        captureQ6Screenshot(screenshotDirectory, "review-preview")
        // Lo que la revisión mostró ANTES de activar: contra esto se compara lo guardado, sin
        // depender de lo que el VM conserve (o limpie) después del alta.
        val reviewed = ReviewedActivation(
            program = selectedPreview,
            nutritionPlan = vm.state.value.nutritionPlanPreview,
            todayCalorieTargetKcal = vm.state.value.nutritionPreparation?.days
                ?.firstOrNull { day -> day.date == LocalDate.now() }
                ?.calorieTargetKcal,
        )

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

        // La shell del test sigue la misma salida: tras aceptar onDone presenta
        // el Home real (no una imitación) y este observa el programa activo.
        homeVisible.value = true
        val activeProgramId = vm.state.value.draft.commitId
        awaitUi("Home muestra el programa recién activado") {
            homeViewModel.uiState.value.activeProgramId == activeProgramId
        }
        // El Home es un LazyColumn: «Tus Programas» es su 4º ítem y fuera de la ventana no está
        // compuesto, así que `performScrollTo()` (exige un nodo existente) no basta.
        // `SectionHeader` pinta `title.uppercase()` («TUS PROGRAMAS»): se compara sin distinguir mayúsculas.
        scrollHomeTo("Tus Programas")
        composeRule.onNodeWithText("Tus Programas", ignoreCase = true).assertIsDisplayed()
        // La insignia vive en la fila horizontal de programas: el programa recién activado es
        // la última tarjeta y no está compuesta hasta desplazar la fila (identidad por testTag).
        assertActiveProgramCardOnHome(activeProgramId)
        captureQ6Screenshot(screenshotDirectory, "home-active-program")

        assertCommittedRoom(
            scenario = scenario,
            flavor = flavor,
            pre = pre,
            draftId = draftId,
            weightKg = weightKg,
            reviewed = reviewed,
        )
    }

    /**
     * Tarjeta del programa recién activado en la fila horizontal del Home. La BD compartida de
     * QA acumula programas (21 o más) con nombres repetidos y el activo queda al final de un
     * LazyRow, así que no está compuesto hasta desplazar la fila. Se desplaza a su ÍNDICE REAL
     * en la lista del Home (sin `runCatching`: si el scroll falla, falla la prueba) y la tarjeta
     * se identifica por su testTag único (lleva el id del programa) más la insignia ACTIVO y el
     * nombre; nunca por un texto suelto, que se repite.
     */
    private fun assertActiveProgramCardOnHome(programId: String) {
        awaitUi("la lista de programas del Home incluye el recién activado") {
            homeViewModel.uiState.value.programs.any { program -> program.id == programId }
        }
        // La fila ya recibió la lista nueva: sin esperar la recomposición, el scroll podría
        // pedir un índice que la fila todavía no tiene.
        composeRule.waitForIdle()
        val programs = homeViewModel.uiState.value.programs
        val index = programs.indexOfFirst { program -> program.id == programId }
        assertTrue(
            "el programa $programId no está en la lista del Home (${programs.size} programas)",
            index >= 0,
        )
        val programName = programs[index].name
        composeRule.onNodeWithTag(HOME_PROGRAMS_ROW_TAG).performScrollToIndex(index)
        composeRule.waitForIdle()
        val cardTag = homeProgramCardTag(programId)
        if (composeRule.onAllNodes(hasTestTag(cardTag)).fetchSemanticsNodes().isEmpty()) {
            // El índice no compuso la tarjeta (p. ej. el scroll quedó a medio asentar): segunda
            // vía, también por semántica y sin ocultar errores, buscando el nodo por su tag.
            composeRule.onNodeWithTag(HOME_PROGRAMS_ROW_TAG).performScrollToNode(hasTestTag(cardTag))
            composeRule.waitForIdle()
        }
        assertEquals(
            "exactamente una tarjeta con la identidad del programa recién activado ($cardTag)",
            1,
            composeRule.onAllNodes(hasTestTag(cardTag)).fetchSemanticsNodes().size,
        )
        val identified = composeRule.onAllNodes(
            hasTestTag(cardTag) and hasText("ACTIVO") and hasText(programName),
        )
        assertEquals(
            "la tarjeta $cardTag debe mostrar la insignia ACTIVO y el nombre «$programName» " +
                "(programas con ese nombre en la BD compartida: ${programs.count { it.name == programName }})",
            1,
            identified.fetchSemanticsNodes().size,
        )
        identified[0].assertIsDisplayed()
    }

    /** Revisión: TODOS los ejercicios reales de la primera semana del preview. */
    private fun assertFirstWeekExercisesShown(scenario: PlanScenario) {
        val preview = vm.state.value.programPreview
        if (preview == null) {
            fail("La revisión no tiene programa: ${vm.state.value.describe()}")
            return
        }
        val sessions = firstWeekSessions(preview)
        assertTrue("la primera semana del preview está vacía", sessions.isNotEmpty())
        if (scenario.assertExactWeeklySessions) {
            assertEquals("sesiones reales de la primera semana", scenario.daysPerWeek, sessions.size)
            assertEquals(
                "días del preview = días elegidos",
                scenario.weekdayIds,
                sessions.mapNotNull { it.dayOfWeek }.toSet(),
            )
        }
        val exercises = sessions.flatMap { session -> session.allExercises() }
        assertTrue("el preview no trae ejercicios reales", exercises.isNotEmpty())
        exercises.forEach { exercise ->
            assertInTree(exercise.name, substring = true, what = "ejercicio «${exercise.name}»")
        }
    }

    private fun firstWeekSessions(program: Program): List<Session> = program.macrocycles
        .flatMap { it.blocks }
        .flatMap { it.mesocycles }
        .flatMap { it.weeks }
        .firstOrNull()
        ?.sessions
        .orEmpty()

    /** Cross-check every materialized configuration against the approved catalog for E0. */
    private fun assertBodyweightOnlyMaterialization(program: Program, phase: String) {
        val repository = ApprovedAssetExerciseCatalogRepositoryV2(app)
        runBlocking { repository.load() }
        val catalog = checkNotNull((repository.state.value as? ExerciseCatalogStateV2.Ready)?.catalog) {
            "$phase: el catálogo aprobado local no quedó listo"
        }
        val equipmentByConfiguration = catalog.families
            .flatMap { it.definitions }
            .flatMap { it.configurations }
            .associate { it.id to it.profile.equipmentId }
        val exercises = program.macrocycles
            .flatMap { it.blocks }
            .flatMap { it.mesocycles }
            .flatMap { it.weeks }
            .flatMap { it.sessions }
            .flatMap { it.allExercises() }
        assertTrue("$phase: programa E0 con ejercicios reales", exercises.isNotEmpty())
        exercises.forEach { exercise ->
            val configurationId = checkNotNull(exercise.catalogConfigurationId) {
                "$phase: ${exercise.name} no conserva configuración del catálogo"
            }
            val equipmentId = checkNotNull(equipmentByConfiguration[configurationId]) {
                "$phase: configuración $configurationId de ${exercise.name} no existe en catálogo"
            }
            assertEquals(
                "$phase: ${exercise.name} no puede introducir material ajeno a E0",
                "bodyweight",
                equipmentId,
            )
        }
    }

    /** IDs prove that activation persisted the exact preview, not a regenerated lookalike. */
    private fun sessionExerciseSignature(sessions: List<Session>): List<String> = sessions
        .sortedBy { it.dayOfWeek }
        .map { session ->
            "${session.dayOfWeek}:${session.allExercises().joinToString(",") { exercise -> exercise.id }}"
        }

    // ─── Aserciones sobre Room tras el alta real ─────────────────────────────

    private fun assertCommittedRoom(
        scenario: PlanScenario,
        flavor: NutritionFlavor,
        pre: RoomSnapshot,
        draftId: String,
        weightKg: Int,
        reviewed: ReviewedActivation,
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
        if (scenario.materialProfile == MaterialProfile.E0_BODYWEIGHT) {
            assertBodyweightOnlyMaterialization(program, "programa reabierto desde Room")
        }
        val roomFirstWeekSessions = firstWeekSessions(program)
        assertTrue("sesiones reales del programa", roomFirstWeekSessions.isNotEmpty())
        val previewProgram = reviewed.program
        assertEquals("nombre guardado = nombre revisado", previewProgram.name, program.name)
        val previewFirstWeekSessions = firstWeekSessions(previewProgram)
        assertEquals(
            "sesiones/ejercicios de la revisión = sesiones/ejercicios en Room",
            sessionExerciseSignature(previewFirstWeekSessions),
            sessionExerciseSignature(roomFirstWeekSessions),
        )
        assertEquals("plan elegido en UI", scenario.expectedCandidateId, state.draft.selectedCatalogId)
        if (scenario.assertExactWeeklySessions) {
            assertEquals("frecuencia semanal materializada", scenario.daysPerWeek, roomFirstWeekSessions.size)
            assertEquals(
                "sesiones semanales persistidas en los días declarados",
                scenario.weekdayIds,
                roomFirstWeekSessions.mapNotNull { it.dayOfWeek }.toSet(),
            )
            assertEquals(
                "calendario semanal persistido",
                scenario.weekdayIds,
                program.resolvedSchedulePlan().trainingDays,
            )
        }
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
        val availability = checkNotNull(settings.equipmentAvailability) { "disponibilidad confirmada en Room" }
        when (scenario.materialProfile) {
            MaterialProfile.GYM -> {
                assertEquals("categorías declaradas desde la UI", EquipmentCategory.entries.toSet(), availability.categories)
                assertEquals("banco confirmado", ApparatusPresence.PRESENT, availability.supports["bench_flat"])
                assertEquals("rack confirmado", ApparatusPresence.PRESENT, availability.supports["squat_rack"])
                assertEquals("barra de dominadas confirmada", ApparatusPresence.PRESENT, availability.supports["pullup_bar"])
                assertEquals("polea alta/baja confirmada", ApparatusPresence.PRESENT, availability.apparatus["cable_high_low"])
                assertEquals(
                    "máquinas genéricas no confirman hack squat",
                    ApparatusPresence.UNKNOWN,
                    availability.apparatus["hack_squat"] ?: ApparatusPresence.UNKNOWN,
                )
            }
            MaterialProfile.E0_BODYWEIGHT -> assertEquals(
                "E0 confirmado por la opción de UI Solo peso corporal",
                EquipmentAvailability(emptySet()),
                availability,
            )
        }

        if (flavor == NutritionFlavor.TRACKING_ONLY) {
            assertEquals("flag de solo registro", true, settings.nutritionTrackingOnly)
            assertEquals("el gasto diario no cambia en solo registro", pre.dailyCalorieGoal, settings.dailyCalorieGoal)
            val planRows = room { room.nutritionDao().getAllPlans() }
            assertEquals("solo registro: ningún plan nuevo (los previos se conservan)", pre.planIds, planRows.map { it.id }.toSet())
            // Solo registro DESACTIVA el plan que estuviera activo (el historial de planes se
            // conserva): en la BD compartida de QA casi siempre había uno, así que el estado
            // activo queda vacío y ninguna fila queda marcada activa.
            assertNull(
                "solo registro: el estado activo queda vacío (plan activo antes del alta: ${pre.activePlanId})",
                room { room.nutritionDao().getActiveState() },
            )
            assertTrue("solo registro: ningún plan queda marcado activo", planRows.none { it.isActive })
            assertNull(
                "solo registro: ninguna fila de plan con el id de ESTE alta",
                planRows.firstOrNull { it.id == commitId },
            )
            return
        }

        assertEquals("flag de registro activo", false, settings.nutritionTrackingOnly)
        val planId = checkNotNull(room { room.nutritionDao().getActiveState() }?.activePlanId) { "sin plan activo" }
        assertTrue("el plan activo es una fila NUEVA de esta alta", planId !in pre.planIds)
        val plan = room { room.nutritionDao().getAllPlans() }.single { it.id == planId }.toNutritionPlan()
        assertTrue("plan activo", plan.isActive)

        val previewPlan = checkNotNull(reviewed.nutritionPlan) { "plan de la revisión" }
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

        // Meta de HOY: el alta la fija en la MISMA transacción. La BD compartida de QA suele traer
        // la de otro plan (de una pasada anterior); activar un plan DISTINTO el mismo día la
        // REEMPLAZA, así que la aserción es estricta: siempre la del plan recién activado.
        val reviewedTodayKcal = reviewed.todayCalorieTargetKcal
        if (reviewedTodayKcal != null) {
            val today = LocalDate.now().toString()
            val snapshot = checkNotNull(room { room.nutritionDao().getDailyGoalSnapshot(today) }) {
                "objetivo de HOY escrito en la transacción del alta"
            }
            assertEquals("la meta de HOY es la del plan recién activado", planId, snapshot.planId)
            assertEquals("kcal de HOY guardadas = las previsualizadas", reviewedTodayKcal, snapshot.calorieTargetKcal)
            // La caché que lee el Home también se republicó (publishSetupCommit relee los snapshots).
            val cachedToday = NutritionRepository.getInstance().dailyGoalSnapshots.value
                .firstOrNull { cached -> cached.date == today }
            assertEquals("la caché de metas diarias tiene la meta de HOY del plan nuevo", planId, cachedToday?.planId)
            // Efecto visible: la meta diaria del Home ya es la del plan nuevo, no la ajena.
            awaitUi("la meta diaria del Home es la del plan recién activado") {
                homeViewModel.uiState.value.dailyCalorieGoal == snapshot.calorieTargetKcal
            }
        }
    }

    /** Lo que la revisión mostró ANTES de activar: contra esto se compara lo guardado. */
    private data class ReviewedActivation(
        val program: Program,
        val nutritionPlan: NutritionPlan?,
        /** Calorías previsualizadas para HOY; null si la revisión no traía objetivo de hoy. */
        val todayCalorieTargetKcal: Int?,
    )

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
        // Un hito es el overlay de «bloque completado»: su botón se habilita al terminar la animación.
        val cta = if (SetupStepGraph.isMilestone(step)) MILESTONE_CTA else CTA
        awaitUi("CTA $cta habilitado en ${step.name}") {
            composeRule.onNodeWithTag(cta).assertIsEnabled()
            true
        }
        composeRule.onNodeWithTag(cta).performClick()
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

    /** Confirm one exact apparatus through its real presence button in the UI. */
    private fun confirmApparatus(key: String, isSupport: Boolean) {
        val rowTag = "setup-apparatus-$key"
        val before = vm.state.value.draft.revision
        composeRule.onNodeWithTag(rowTag).performScrollTo()
        val yesInRow = composeRule.onAllNodes(
            hasText("Sí") and hasAnyAncestor(hasTestTag(rowTag)),
        )
        assertEquals("un botón Sí en la fila $key", 1, yesInRow.fetchSemanticsNodes().size)
        yesInRow[0].performClick()
        awaitState("presencia de $key guardada en el borrador") { it.draft.revision > before }
        val availability = checkNotNull(vm.state.value.draft.trainingOptions.availability)
        val presence = if (isSupport) availability.supports[key] else availability.apparatus[key]
        assertEquals("presencia explícita de $key", ApparatusPresence.PRESENT, presence)
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

    /** Layout de altura/peso que el producto compone en el paso HEIGHT. */
    private enum class AnthropometryLayout { SEPARATE, COMBINED }

    /**
     * Espera a que el producto resuelva el layout del paso HEIGHT y lo devuelve
     * cuando lleva estable [LAYOUT_SETTLE_MS]. Mientras mide (`Pending`) no
     * compone ningún control de altura; después compone la rueda (SEPARATE) o la
     * regla (COMBINED), nunca las dos. La espera de estabilidad es defensiva: el
     * hueco de la viewport (y con él el layout) depende de insets/IME, que pueden
     * asentarse tras componer el paso.
     */
    private fun awaitAnthropometryLayout(): AnthropometryLayout {
        var settled: AnthropometryLayout? = null
        var seen: AnthropometryLayout? = null
        var since = 0L
        try {
            composeRule.waitUntil(STEP_TIMEOUT_MS) {
                val now = visibleAnthropometryLayout()
                val tick = System.nanoTime() / NANOS_PER_MS
                if (now == null || now != seen) {
                    seen = now
                    since = tick
                }
                if (now != null && tick - since >= LAYOUT_SETTLE_MS) {
                    settled = now
                    true
                } else {
                    false
                }
            }
        } catch (error: Throwable) {
            fail(
                "El paso HEIGHT no muestra una única rueda o regla de altura estable " +
                    "(rueda=${existsByContentDescription(WHEEL_CONTENT_DESCRIPTION)}, " +
                    "regla=${existsByContentDescription(HEIGHT_RULE_CONTENT_DESCRIPTION)}) · " +
                    "${vm.state.value.describe()} (${error.message})",
            )
        }
        return checkNotNull(settled)
    }

    /** SEPARATE si solo está la rueda, COMBINED si solo está la regla; null si ninguna o ambas. */
    private fun visibleAnthropometryLayout(): AnthropometryLayout? {
        val wheel = existsByContentDescription(WHEEL_CONTENT_DESCRIPTION)
        val rule = existsByContentDescription(HEIGHT_RULE_CONTENT_DESCRIPTION)
        return when {
            wheel && !rule -> AnthropometryLayout.SEPARATE
            rule && !wheel -> AnthropometryLayout.COMBINED
            else -> null
        }
    }

    private fun heightControlDescription(layout: AnthropometryLayout): String = when (layout) {
        AnthropometryLayout.SEPARATE -> WHEEL_CONTENT_DESCRIPTION
        AnthropometryLayout.COMBINED -> HEIGHT_RULE_CONTENT_DESCRIPTION
    }

    private fun heightValueTag(layout: AnthropometryLayout): String = when (layout) {
        AnthropometryLayout.SEPARATE -> HEIGHT_VALUE_TAG
        AnthropometryLayout.COMBINED -> HEIGHT_RULE_VALUE_TAG
    }

    /**
     * Altura: tocar el valor central declara exactamente ese cm. SEPARATE usa la
     * rueda vertical y COMBINED la regla horizontal; las dos centran el índice
     * `cm - MIN_CM` con la acción de scroll del `LazyList` contenido en el control.
     */
    private fun setHeightCm(target: Int, layout: AnthropometryLayout) {
        centerControlOn(heightControlDescription(layout), target - WizardHeightScale.MIN_CM) {
            heightCandidateCm(layout).takeIf { cm -> cm != Int.MIN_VALUE }?.let { cm -> cm - WizardHeightScale.MIN_CM }
        }
        if (heightCandidateCm(layout) == target) {
            composeRule.onNodeWithTag(heightValueTag(layout)).performClick()
        } else if (layout == AnthropometryLayout.SEPARATE) {
            composeRule.onNodeWithText(WizardHeightScale.formatCm(target)).performClick()
        } else {
            fail(
                "La regla de altura no quedó centrada en $target cm " +
                    "(candidato=${heightCandidateCm(layout)}) · ${vm.state.value.describe()}",
            )
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

    /**
     * Centra el candidato de la regla y lo confirma con un toque real. Es la
     * misma regla en ambos layouts: el paso WEIGHT (SEPARATE) o la mitad
     * inferior del par altura/peso del paso HEIGHT (COMBINED).
     */
    private fun emitWeightCandidate(target: Int) {
        centerControlOn(RULE_CONTENT_DESCRIPTION, weightTickIndex(target.toDouble())) {
            weightCandidateKg().takeIf { kg -> kg.isFinite() }?.let { kg -> weightTickIndex(kg) }
        }
        if (weightCandidateKg() == target.toDouble()) {
            composeRule.onNodeWithTag(WEIGHT_VALUE_TAG).performClick()
        } else {
            composeRule.onNodeWithText(WizardWeightScale.format(target.toDouble())).performClick()
        }
    }

    private fun weightTickIndex(kg: Double): Int {
        val range = WizardWeightScale.displayRange(WizardMassUnit.KG)
        val firstStep = Math.round(range.start * TICKS_PER_KG).toInt()
        return Math.round(kg * TICKS_PER_KG).toInt() - firstStep
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

    /**
     * Deja [targetIndex] como ítem central del control. `scrollToItem` pega el
     * ítem pedido al centro y el control elige como candidato el ítem más
     * cercano al centro, que en un empate de media casilla puede ser el vecino
     * (depende de la densidad): se relee el candidato del propio control
     * ([centeredIndex], null si no se puede leer) y se corrige por la diferencia,
     * como mucho [MAX_CENTER_ATTEMPTS] veces. Si ya está centrado no se toca.
     */
    private fun centerControlOn(contentDescription: String, targetIndex: Int, centeredIndex: () -> Int?) {
        var requested = targetIndex
        repeat(MAX_CENTER_ATTEMPTS) {
            if (centeredIndex() == targetIndex) return
            scrollControlTo(contentDescription, requested)
            val centered = centeredIndex() ?: return
            if (centered == targetIndex) return
            requested = (requested + targetIndex - centered).coerceAtLeast(0)
        }
    }

    private fun heightCandidateCm(layout: AnthropometryLayout): Int =
        candidateStateDescription(heightControlDescription(layout))
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

    /** Candidato exacto del catálogo real; paginar solo expande la UI, no recalcula. */
    private fun selectPlanCandidate(scenario: PlanScenario) {
        awaitPlanCandidates()
        val state = vm.state.value
        val problem = state.previewError ?: state.errors["candidates"]
        if (problem != null) {
            fail("La generación de candidatos falló con el error REAL: $problem (${state.describe()})")
            return
        }
        val all = state.availablePlanCandidates.ifEmpty { state.planCandidates }
        val chosen = all.firstOrNull { it.id == scenario.expectedCandidateId }
        if (chosen == null) {
            fail(
                "No está publicado/viable ${scenario.expectedCandidateId} entre " +
                    "${all.map { "${it.source}=${it.id}:${it.title}" }}; " +
                    "no se fabrica material para forzar uno (${state.describe()})",
            )
            return
        }
        assertEquals("procedencia técnica del testigo", scenario.candidateSource, chosen.source)
        var pageCount = 0
        while (chosen.id !in vm.state.value.planCandidates.map { candidate -> candidate.id }) {
            if (!existsInTree(MORE_CANDIDATES_LABEL, substring = true)) {
                fail(
                    "El candidato ${chosen.id} «${chosen.title}» no está visible y la UI no ofrece " +
                        "«$MORE_CANDIDATES_LABEL»",
                )
                return
            }
            val visibleCount = vm.state.value.planCandidates.size
            composeRule.onNodeWithText(MORE_CANDIDATES_LABEL, substring = true).performClick()
            awaitState("candidato «${chosen.title}» visible") { current ->
                chosen.id in current.planCandidates.map { candidate -> candidate.id } ||
                    current.planCandidates.size > visibleCount
            }
            pageCount += 1
            assertTrue("la paginación alcanza un candidato del catálogo completo", pageCount <= all.size)
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

    /**
     * Lleva [text] a la ventana del Home real (LazyColumn): si el ítem no está compuesto se
     * desplaza con `performScrollToNode` (ScrollToIndex + clave) en cada contenedor con scroll;
     * si ya existe basta `performScrollTo()`.
     */
    private fun scrollHomeTo(text: String) {
        val matcher = hasText(text, ignoreCase = true)
        fun composed(): Boolean = composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
        if (!composed()) {
            val containers = composeRule.onAllNodes(hasScrollAction()).fetchSemanticsNodes().size
            for (index in 0 until containers) {
                runCatching {
                    composeRule.onAllNodes(hasScrollAction())[index].performScrollToNode(matcher)
                }
                composeRule.waitForIdle()
                if (composed()) break
            }
        }
        composeRule.onNode(matcher).performScrollTo()
        composeRule.waitForIdle()
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

    private fun existsByContentDescription(contentDescription: String): Boolean = try {
        composeRule.onAllNodesWithContentDescription(contentDescription).fetchSemanticsNodes().isNotEmpty()
    } catch (error: Throwable) {
        false
    }

    private fun assertInTree(needle: String, substring: Boolean = false, what: String = needle) {
        assertTrue("No aparece en la pantalla actual: «$what»", existsInTree(needle, substring))
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
        const val MILESTONE_CTA = "setup-milestone-continue"
        const val INTRO = "setup-intro"
        const val INTRO_START = "setup-intro-start"
        const val CTA_REVIEW_LABEL = "Activar y entrar a KPKN"
        const val BACK_LABEL = "Volver al paso anterior"
        // Layout SEPARATE (altura sola): rueda vertical de `WizardHeightWheel`.
        const val HEIGHT_VALUE_TAG = "setup-height-value"
        const val WHEEL_CONTENT_DESCRIPTION = "Rueda de altura en centímetros"
        // Layout COMBINED (altura + peso juntos): regla horizontal de `WizardHeightRule`.
        const val HEIGHT_RULE_VALUE_TAG = "setup-height-rule-value"
        const val HEIGHT_RULE_CONTENT_DESCRIPTION = "Regla de altura en centímetros"
        // Regla de peso: misma en ambos layouts (paso WEIGHT o mitad inferior del par).
        const val WEIGHT_VALUE_TAG = "setup-weight-value"
        const val RULE_CONTENT_DESCRIPTION = "Regla de peso en kilogramos"
        const val MORE_CANDIDATES_LABEL = "Ver más opciones"
        const val NO_COMPATIBLE_PLAN_LABEL = "no hay un plan compatible"
        const val NO_DAYS_LABEL = "Todavía no hay objetivos por fecha."
        const val REFERENCE_PREFIX = "Referencia diaria:"
        const val VM_STORE_KEY = "setup-wizard-full-journey"

        const val ACTIVATION_CONFIRM_LABEL = "Confirmo la activación"
        const val RECIPE_CONFIRM_LABEL = "Confirmo la rotación y la duración reales"

        const val NAME_FIELD_LABEL = "Nombre"
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
        const val MAX_CENTER_ATTEMPTS = 3
        const val LAYOUT_SETTLE_MS = 750L
        const val NANOS_PER_MS = 1_000_000L
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

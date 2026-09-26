package com.example.kpkn.screens.onboarding

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toActiveProgramState
import com.example.kpkn.data.db.toBodyObservation
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.db.toNutritionPlan
import com.example.kpkn.data.db.toProgram
import com.example.kpkn.data.db.toSettings
import com.example.kpkn.data.exercises.catalogv2.CatalogV2ProcessCache
import com.example.kpkn.data.models.AutoregulationMode
import com.example.kpkn.data.models.BodyMetric
import com.example.kpkn.data.models.BodyObservationQuality
import com.example.kpkn.data.models.CalculationOrigin
import com.example.kpkn.data.models.DumbbellPairStock
import com.example.kpkn.data.models.EquipmentInventory
import com.example.kpkn.data.models.MachineLoadRange
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.TrainingStyle
import com.example.kpkn.data.onboarding.SetupCommitCoordinator
import com.example.kpkn.data.onboarding.SetupCommitRequest
import com.example.kpkn.data.onboarding.SetupCommitResult
import com.example.kpkn.data.onboarding.SetupDraft
import com.example.kpkn.data.onboarding.SetupDraftCandidate
import com.example.kpkn.data.onboarding.SetupDraftRepository
import com.example.kpkn.data.onboarding.SetupDraftResolver
import com.example.kpkn.data.onboarding.SetupRingsEvidenceInput
import com.example.kpkn.data.onboarding.SetupRingsPreviewCalculator
import com.example.kpkn.data.onboarding.previewCheckIn
import com.example.kpkn.data.repository.AugeRepository
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.nutrition.EerSex
import com.example.kpkn.domain.nutrition.NutritionConfigurationMode
import com.example.kpkn.domain.nutrition.WizardPacePreset
import com.example.kpkn.domain.onboarding.SetupAnswerProvenance
import com.example.kpkn.domain.onboarding.SetupRingsResponseMapping
import com.example.kpkn.domain.onboarding.SetupStepDefinitions
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.WizChatMachineState
import com.example.kpkn.domain.training.OrderPrioritiesContract
import com.example.kpkn.domain.training.OrderPrioritiesStatus
import com.example.kpkn.domain.training.ProgramExecutionContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Viaje COMPLETO del wizard sobre los puertos reales: [SetupWizardViewModel]
 * real, [SetupStepGraph] real, reductores tipados reales
 * (`withStepChoice/Choices/Text/Number` + `updateStep`), preparación real de
 * nutrición (`SetupNutritionPreparation`) y de entrenamiento
 * (`OnboardingPlanGenerator` sobre el catálogo aprobado), persistencia REAL en
 * Room (adaptador fino sobre [SetupDraftRepository]/[SetupDraftResolver]) y
 * commit REAL en [SetupCommitCoordinator] (transacción + receipt + rollback).
 *
 * Lo único doblegado es el entorno de lectura ([FixedSettingsEnvironment]):
 * fija `Settings` sin la latencia de los repos de Android. Aquí NO hay una
 * segunda máquina falsa: las dos carreras exponen la ruta esperada paso a paso
 * (sin bucles que salten pasos) y las aserciones leen Room, no el ViewModel a
 * ciegas.
 *
 * Cableado pendiente de puerto: el preview de RINGS sale de
 * `SetupRingsPreviewCalculator`, que todavía NO pasa por
 * [SetupWizardEnvironment] y llama a `ProgramRepository.getInstance()` /
 * `NutritionRepository.getInstance()` / `AugeRepository.getInstance(context)`.
 * Por eso este test inicializa los repos reales con sus seams de producción
 * (mismo patrón que la prueba instrumentada `SetupRingsPreviewIntegrationTest`);
 * el entorno del wizard sigue siendo solo Settings.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class SetupWizardFullJourneyTest {

    private val dispatcher = StandardTestDispatcher()
    private val viewModelStore = ViewModelStore()
    private lateinit var app: Application
    private lateinit var handle: SavedStateHandle
    private lateinit var db: KpknDatabase
    private lateinit var persistence: RoomWizardPersistence
    private lateinit var commits: RoomWizardCommits
    private lateinit var environment: FixedSettingsEnvironment

    /** Pasos confirmados en orden: la cobertura explícita de la ruta. */
    private val submitted = mutableListOf<SetupStepId>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        app = ApplicationProvider.getApplicationContext()
        // Robolectric recrea su shadow SQLite en cada método; los singletons de
        // conexión (AugeRepository -> BD de fichero, repos de Program/Nutrition)
        // se limpian AQUÍ para que cada test use punteros de SU propio método.
        resetAugeRepositorySingleton()
        ProgramRepository.closeInstance()
        NutritionRepository.closeInstance()
        KpknDatabase.closeInstance()
        handle = SavedStateHandle()
        db = KpknDatabase.createInMemory(app)
        persistence = RoomWizardPersistence(db)
        commits = RoomWizardCommits(db)
        environment = FixedSettingsEnvironment(Settings())
        // Instancias frescas de ESTE test: las reales que RINGS consulta fuera
        // del puerto, sin latencia ni estado heredado de otro método.
        ProgramRepository.init(app)
        NutritionRepository.init(app)
        // Cargador REAL del catálogo aprobado, fuera del presupuesto del runTest.
        val warm = runCatching { runBlocking { CatalogV2ProcessCache.getOrLoad(app) } }
        assertTrue(
            "El catálogo real exercise_catalog_v2.json no carga en Robolectric: " +
                warm.exceptionOrNull()?.message,
            warm.isSuccess,
        )
    }

    @After
    fun tearDown() {
        // Ciclo de vida en orden: los VMs primero (ViewModelStore.clear cierra
        // su viewModelScope real), luego los repos, después las bases de datos y
        // solo al final se resetea Main: nadie toca una BD ya cerrada.
        viewModelStore.clear()
        ProgramRepository.closeInstance()
        NutritionRepository.closeInstance()
        resetAugeRepositorySingleton()
        KpknDatabase.closeInstance()
        db.close()
        Dispatchers.resetMain()
    }

    /**
     * `AugeRepository` no expone ningún reset y su `dao` apunta a la BD de
     * fichero que Robolectric (ShadowLegacySQLiteConnection) recrea entre
     * métodos: sin limpiarlo, el singleton conserva un puntero SQLite ajeno y
     * TODAS sus consultas revientan con «Illegal connection pointer». Acceso por
     * reflejo SOLO desde este test y con fallo explícito si la estructura del
     * producto cambia: ningún `runCatching` que lo oculte.
     */
    private fun resetAugeRepositorySingleton() {
        val field = try {
            AugeRepository::class.java.getDeclaredField("INSTANCE")
        } catch (error: NoSuchFieldException) {
            throw AssertionError(
                "AugeRepository.INSTANCE ya no es un campo privado estático: " +
                    "revisa este helper de test, no se pudo limpiar la conexión SQLite entre métodos",
                error,
            )
        }
        field.isAccessible = true
        field.set(null, null)
        check(field.get(null) == null) {
            "No se pudo limpiar AugeRepository.INSTANCE: su dao quedaría apuntando a la conexión de otro test"
        }
    }

    // ─── Caso 1: FULL con objetivos propios y Rings parcial ──────────────────

    @Test
    fun fullJourneySelfDefinedNutritionCommitsExecutableProgramAndDurableTargets() = runTest {
        val vm = vm()
        vm.initialize(SetupWizardMode.FULL)
        awaitCondition(vm, "wizard cargado") {
            !it.isLoading && it.draft.stepProgress.currentStepId == SetupStepId.NAME
        }
        assertEquals(SetupWizardMode.FULL, vm.state.value.mode)

        walkBasicsAndTraining(vm)

        // Sin revisión no se activa nada: la puerta real ni toca Room.
        val earlyReceipt = vm.commit()
        assertNull("sin revisión no hay recibo", earlyReceipt)
        assertTrue("el intento temprano explica por qué", vm.state.value.errors.isNotEmpty())
        assertNotEquals(WizChatMachineState.Committed, vm.state.value.machineState)
        assertTrue("Room intacto antes de la revisión (solo vive el borrador)", roomUntouchedBeforeReview())

        // Retomo desde Room: mismo borrador, mismo paso, mismas respuestas.
        vm.initialize(SetupWizardMode.RESUME)
        awaitCondition(vm, "reanudación sobre Room") {
            !it.isLoading && it.mode == SetupWizardMode.FULL &&
                it.draft.stepProgress.currentStepId == SetupStepId.NUTRITION_START
        }
        assertEquals("Ana", vm.state.value.draft.name)
        assertNotNull("la selección de plan sobrevive a la reanudación", vm.state.value.draft.selectedCatalogId)
        assertTrue(
            "los hitos confirmados siguen confirmados",
            vm.state.value.draft.stepProgress.answers.containsKey(SetupStepId.MILESTONE_TRAINING),
        )

        walkSelfDefinedNutrition(vm)
        walkRingsPartial(vm)
        assertEquals(SetupStepId.REVIEW_ACTIVATE, vm.state.value.currentStep)

        val expectedRoute = fullSelfDefinedRoute()
        val draftAtReview = vm.state.value.draft
        assertEquals("ruta FULL esperada", expectedRoute, SetupStepGraph.stepIds(draftAtReview.stepContext()))
        assertEquals("trail de visitas sin pasos inventados", expectedRoute, draftAtReview.stepProgress.visited)
        assertEquals(
            "confirmaciones esperadas (incluye la vuelta de Atrás)",
            expectedConfirmations(expectedRoute),
            submitted,
        )
        assertEquals(
            "cada paso de la ruta está confirmado; la revisión no registra respuesta propia",
            (expectedRoute - SetupStepId.REVIEW_ACTIVATE).toSet(),
            draftAtReview.stepProgress.answers.keys,
        )
        assertNull("el género nunca se pregunta en la ruta", draftAtReview.profileGender)
        assertEquals("figura visual independiente del sexo de cálculo", "female", draftAtReview.physiqueModel)
        assertEquals(SetupBodyFatSource.VISUAL_ESTIMATE, draftAtReview.bodyFatSource)
        assertEquals("sexo de cálculo declarado aparte", EerSex.MALE, draftAtReview.nutritionDraft?.equationSex)
        assertEquals(
            NutritionConfigurationMode.SELF_DEFINED,
            draftAtReview.nutritionDraft?.configurationMode,
        )
        assertEquals(AutoregulationMode.PROPOSE, draftAtReview.trainingOptions.autoregulationMode)
        // Texto crudo por paso con clave `step.name`, tal como lo escribe el reducer.
        assertEquals("34", draftAtReview.inputTexts[SetupStepId.AGE.name])
        assertEquals("78", draftAtReview.inputTexts[SetupStepId.WEIGHT.name])
        assertEquals("2100", draftAtReview.nutritionDraft?.manualCalorieTargetText)
        assertEquals("165", draftAtReview.nutritionDraft?.manualProteinText)
        assertEquals("180", draftAtReview.nutritionDraft?.manualCarbsText)
        assertEquals("65", draftAtReview.nutritionDraft?.manualFatText)
        assertFalse("nada bloquea la revisión", vm.state.value.globalValidation.any { it.isBlocking })
        awaitQuiescent(vm)

        // Revisión editable: editar desde la revisión vuelve a la revisión.
        val answersBeforeEdit = vm.state.value.draft.stepProgress.answers
        vm.editStep(SetupStepId.WEIGHT)
        awaitCondition(vm, "edición del peso") { it.draft.stepProgress.currentStepId == SetupStepId.WEIGHT }
        assertEquals("editar no borra el resto de respuestas", "Ana", vm.state.value.draft.name)
        vm.setStepNumber(SetupStepId.WEIGHT, 77.0)
        awaitRest(vm)
        assertEquals(
            SetupSubmitOutcome.ACCEPTED,
            vm.submitCurrentStep(SetupStepId.WEIGHT, expectedRevision = vm.state.value.draft.revision).outcome,
        )
        awaitCondition(vm, "vuelta a la revisión") {
            it.draft.stepProgress.currentStepId == SetupStepId.REVIEW_ACTIVATE
        }
        assertEquals("una edición = una confirmación", answersBeforeEdit, vm.state.value.draft.stepProgress.answers)
        assertEquals(SetupAnswerProvenance.USER_DECLARED, vm.state.value.draft.stepProgress.answers[SetupStepId.WEIGHT])
        assertEquals(77.0, checkNotNull(vm.state.value.draft.weightKg) { "peso tras la edición" }, 0.001)
        assertEquals("77", vm.state.value.draft.inputTexts[SetupStepId.WEIGHT.name])
        awaitQuiescent(vm)
        // Prerrequisito real de la revisión: si el preview de RINGS está roto el
        // commit nunca llega al coordinador, así que se diagnostica ANTES del
        // bloqueo (con la causa real, que el VM esconde).
        assertRingsPreviewReady(vm)

        // ── Rollback real: el coordinador rechaza el alta sin efectos parciales ─
        val commitId = vm.state.value.draft.commitId
        val draftId = vm.state.value.draft.draftId
        room { db.programDao().upsert(Program(id = commitId, name = "Duplicado").toEntity()) }
        val failed = vm.commit()
        assertNull("el coordinador real rechaza el duplicado", failed)
        assertNotNull(
            "el fallo viene del coordinador, no de la puerta de revisión: " +
                "errors=${vm.state.value.errors} ringsPreviewError=${vm.state.value.ringsPreviewError} " +
                "lastFailure=${vm.state.value.lastFailure}",
            vm.state.value.errors["commit"],
        )
        assertNotEquals(WizChatMachineState.Committed, vm.state.value.machineState)
        assertNull("receipt sin escribir", room { db.setupCommitReceiptDao().get(commitId) })
        assertTrue("sin plan de nutrición", room { db.nutritionDao().getAllPlans() }.isEmpty())
        assertTrue("sin observaciones", room { db.bodyProgressDao().getAllObservations() }.isEmpty())
        assertNull("sin settings", room { db.settingsDao().get() })
        assertNull("sin programa activo", room { db.stateDao().getActiveProgram() })
        assertNotNull("el borrador sobrevive al rollback", room { db.setupDraftDao().getDraft(draftId) })

        // Reintento del MISMO commit tras limpiar el obstáculo.
        room { db.programDao().delete(commitId) }
        vm.retryFailedOperation(SetupRetryOperation.COMMIT)
        awaitCondition(vm, "commit real completado") { it.machineState == WizChatMachineState.Committed }
        val receipt = checkNotNull(vm.state.value.receiptId) { "recibo de la alta" }
        assertEquals("el VM devuelve su propio recibo", receipt, vm.commit())

        assertCommittedFullJourney(commitId, draftId, receipt, draftAtReview, historyDate())

        // Presupuesto real capturado ANTES del replay: filas completas e ids.
        val observationsBefore = room { db.bodyProgressDao().getAllObservations() }.sortedBy { it.id }
        val plansBefore = room { db.nutritionDao().getAllPlans() }.sortedBy { it.id }
        val goalsBefore = room { db.bodyProgressDao().getAllGoals() }.sortedBy { it.id }
        val receiptsBefore = room { db.setupCommitReceiptDao().getAll() }

        // Presupuesto legítimo del alta (el mismo que valida
        // assertCommittedFullJourney): 3 observaciones reales, 1 plan, 1 meta.
        assertEquals("observaciones legítimas del alta", 3, observationsBefore.size)
        assertEquals("plan real del alta", 1, plansBefore.size)
        assertEquals("meta real derivada del alta", 1, goalsBefore.size)
        assertEquals("receipt real del alta", 1, receiptsBefore.size)
        assertNotNull("receipt del alta", room { db.setupCommitReceiptDao().get(receipt) })

        // Repetir el commit no duplica nada: mismo recibo y mismo presupuesto.
        assertEquals("recibo idempotente", receipt, vm.commit())

        // Mismos ids y mismos valores exactos tras el replay.
        val observationsAfter = room { db.bodyProgressDao().getAllObservations() }.sortedBy { it.id }
        assertEquals(
            "ids de observaciones idénticos",
            observationsBefore.map { it.id },
            observationsAfter.map { it.id },
        )
        assertEquals("filas y valores exactos de observaciones", observationsBefore, observationsAfter)
        assertEquals("plan sin duplicar", plansBefore, room { db.nutritionDao().getAllPlans() }.sortedBy { it.id })
        assertEquals("metas sin duplicar", goalsBefore, room { db.bodyProgressDao().getAllGoals() }.sortedBy { it.id })
        assertEquals("receipts sin duplicar", receiptsBefore, room { db.setupCommitReceiptDao().getAll() })
        assertNull("el borrador sigue consumido", room { db.setupDraftDao().getDraft(draftId) })
    }

    // ─── Caso 2: misma rama, nutrición solo registro ─────────────────────────

    @Test
    fun trackingOnlyJourneyActivatesProgramAndPersistsSettingsFlagWithoutPlan() = runTest {
        val vm = vm()
        vm.initialize(SetupWizardMode.FULL)
        awaitCondition(vm, "wizard cargado") {
            !it.isLoading && it.draft.stepProgress.currentStepId == SetupStepId.NAME
        }

        walkBasicsAndTraining(vm)

        confirmStep(vm, SetupStepId.NUTRITION_START, SetupStepId.NUTRITION_RESULT) {
            vm.setStepChoice(SetupStepId.NUTRITION_START, "tracking_only")
        }
        assertEquals(
            NutritionConfigurationMode.TRACKING_ONLY,
            vm.state.value.draft.nutritionDraft?.configurationMode,
        )
        confirmStep(vm, SetupStepId.NUTRITION_RESULT, SetupStepId.MILESTONE_NUTRITION)
        confirmStep(vm, SetupStepId.MILESTONE_NUTRITION, SetupStepId.RINGS_RECENT)

        walkRingsPartial(vm)

        val expectedRoute = fullTrackingOnlyRoute()
        val draftAtReview = vm.state.value.draft
        assertEquals("ruta sin cadena EER", expectedRoute, SetupStepGraph.stepIds(draftAtReview.stepContext()))
        assertEquals("trail de visitas", expectedRoute, draftAtReview.stepProgress.visited)
        assertEquals(
            "confirmaciones esperadas (incluye la vuelta de Atrás)",
            expectedConfirmations(expectedRoute),
            submitted,
        )
        assertEquals(
            (expectedRoute - SetupStepId.REVIEW_ACTIVATE).toSet(),
            draftAtReview.stepProgress.answers.keys,
        )
        assertNull("solo registro no fabrica un plan previo", vm.state.value.nutritionPlanPreview)
        assertTrue("sin errores de preparación", vm.state.value.nutritionErrors.isEmpty())
        assertFalse("la revisión no bloquea", vm.state.value.globalValidation.any { it.isBlocking })
        awaitQuiescent(vm)
        assertRingsPreviewReady(vm)

        val receipt = vm.commit()
        assertNotNull("alta de solo registro sin errores=${vm.state.value.errors}", receipt)
        assertEquals(WizChatMachineState.Committed, vm.state.value.machineState)
        assertEquals(receipt, vm.state.value.receiptId)

        val commitId = vm.state.value.draft.commitId
        val committed = checkNotNull(room { db.programDao().getById(commitId) }) { "programa no persistido" }.toProgram()
        ProgramExecutionContract.requireExecutable(committed)
        assertTrue(
            "sesiones reales",
            committed.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }
                .flatMap { it.weeks }.flatMap { it.sessions }.isNotEmpty(),
        )
        assertEquals(AutoregulationMode.PROPOSE, committed.autoregulationMode)
        assertEquals(commitId, room { db.stateDao().getActiveProgram()?.toActiveProgramState()?.programId })

        // Sin plan, sin metas, sin snapshot: duradero el modo de solo registro.
        assertTrue(room { db.nutritionDao().getAllPlans() }.isEmpty())
        assertNull(room { db.nutritionDao().getActiveState() })
        assertTrue(room { db.bodyProgressDao().getAllGoals() }.isEmpty())
        assertNull(room { db.nutritionDao().getDailyGoalSnapshot(LocalDate.now().toString()) })
        val settings = checkNotNull(room { db.settingsDao().get() }) { "settings no persistidos" }.toSettings()
        assertEquals(true, settings.nutritionTrackingOnly)
        assertEquals(true, settings.onboardingCompleted)
        assertEquals(true, settings.onboardingProgramDone)
        assertEquals(true, settings.onboardingNutritionDone)
        assertNull(settings.dailyCalorieGoal)
        assertNotNull(room { db.setupCommitReceiptDao().get(checkNotNull(receipt) { "recibo" }) })
        assertNull(room { db.setupDraftDao().getDraft(vm.state.value.draft.draftId) })
    }

    // ─── Recorridos explícitos (sin bucles que salten pasos) ─────────────────

    /** Bloque 1 + Bloque 2 completos, con la navegación Atrás cubierta. */
    private fun TestScope.walkBasicsAndTraining(vm: SetupWizardViewModel) {
        // NAME: el setter escribe sin avanzar; cursor y revisión se descartan.
        vm.setStepText(SetupStepId.NAME, "Ana")
        awaitRest(vm)
        assertEquals("un setter nunca mueve el cursor", SetupStepId.NAME, vm.state.value.currentStep)
        assertTrue("un setter nunca confirma el paso", vm.state.value.draft.stepProgress.answers.isEmpty())
        assertEquals(
            "cursor esperado descartado",
            SetupSubmitOutcome.DROPPED,
            vm.submitCurrentStep(SetupStepId.HEIGHT).outcome,
        )
        assertEquals(
            "revisión obsoleta descartada",
            SetupSubmitOutcome.DROPPED,
            vm.submitCurrentStep(SetupStepId.NAME, expectedRevision = vm.state.value.draft.revision - 1).outcome,
        )
        confirmStep(vm, SetupStepId.NAME, SetupStepId.AGE)

        // AGE: omitir está prohibido aquí y no inventa respuesta.
        assertFalse("AGE no es opcional", SetupStepDefinitions.of(SetupStepId.AGE)!!.allowSkip)
        vm.skipStep(SetupStepId.AGE)
        awaitRest(vm)
        assertTrue("omitir en AGE deja error", vm.state.value.errors.isNotEmpty())
        assertNull("sin respuesta fabricada", vm.state.value.draft.stepProgress.answers[SetupStepId.AGE])
        confirmStep(vm, SetupStepId.AGE, SetupStepId.HEIGHT) { vm.setStepNumber(SetupStepId.AGE, 34.0) }

        // Atrás mueve el cursor por la estela de visitas y no borra nada.
        assertTrue(vm.canGoBack())
        assertTrue(vm.goBack())
        awaitCondition(vm, "cursor en AGE tras Atrás") {
            it.draft.stepProgress.currentStepId == SetupStepId.AGE
        }
        assertEquals("Ana", vm.state.value.draft.name)
        assertEquals(34, checkNotNull(vm.state.value.draft.ageYears) { "edad persistida en el borrador" })
        confirmStep(vm, SetupStepId.AGE, SetupStepId.HEIGHT)

        confirmStep(vm, SetupStepId.HEIGHT, SetupStepId.WEIGHT) { vm.setStepNumber(SetupStepId.HEIGHT, 176.0) }
        confirmStep(vm, SetupStepId.WEIGHT, SetupStepId.EQUATION_SEX) { vm.setStepNumber(SetupStepId.WEIGHT, 78.0) }
        confirmStep(vm, SetupStepId.EQUATION_SEX, SetupStepId.BODY_FAT) {
            vm.setStepChoice(SetupStepId.EQUATION_SEX, "male")
        }
        // Figura visual (hombre/mujer) como estado actual: no toca la ecuación.
        confirmStep(vm, SetupStepId.BODY_FAT, SetupStepId.MILESTONE_BASICS) {
            vm.updateStep(SetupStepId.BODY_FAT) { draft ->
                draft.copy(
                    bodyFatPercent = 18.0,
                    bodyFatSource = SetupBodyFatSource.VISUAL_ESTIMATE,
                    physiqueModel = "female",
                )
            }
        }
        confirmStep(vm, SetupStepId.MILESTONE_BASICS, SetupStepId.EXPERIENCE)

        confirmStep(vm, SetupStepId.EXPERIENCE, SetupStepId.ROUTE) {
            vm.setStepChoice(SetupStepId.EXPERIENCE, "intermediate")
        }
        confirmStep(vm, SetupStepId.ROUTE, SetupStepId.GOAL) { vm.setStepChoice(SetupStepId.ROUTE, "recommended") }
        // Músculo infiere el estilo: el paso STYLE no entra en la ruta.
        confirmStep(vm, SetupStepId.GOAL, SetupStepId.VOLUME_TECHNIQUE) {
            vm.setStepChoice(SetupStepId.GOAL, "muscle")
        }
        assertEquals(TrainingStyle.BODYBUILDER, vm.state.value.draft.volumeAnswers.style)
        confirmStep(vm, SetupStepId.VOLUME_TECHNIQUE, SetupStepId.VOLUME_CONSISTENCY) {
            vm.setStepChoice(SetupStepId.VOLUME_TECHNIQUE, "2")
        }
        confirmStep(vm, SetupStepId.VOLUME_CONSISTENCY, SetupStepId.VOLUME_STRENGTH) {
            vm.setStepChoice(SetupStepId.VOLUME_CONSISTENCY, "2")
        }
        confirmStep(vm, SetupStepId.VOLUME_STRENGTH, SetupStepId.VOLUME_MOBILITY) {
            vm.setStepChoice(SetupStepId.VOLUME_STRENGTH, "2")
        }
        confirmStep(vm, SetupStepId.VOLUME_MOBILITY, SetupStepId.EQUIPMENT) {
            vm.setStepChoice(SetupStepId.VOLUME_MOBILITY, "2")
        }
        assertNotNull("calibración de volumen real", vm.state.value.draft.volumeCalibrationProfile)

        confirmStep(vm, SetupStepId.EQUIPMENT, SetupStepId.INVENTORY_DUMBBELLS) {
            vm.setStepChoice(SetupStepId.EQUIPMENT, "machines")
        }
        confirmStep(vm, SetupStepId.INVENTORY_DUMBBELLS, SetupStepId.INVENTORY_MACHINES) {
            vm.updateStep(SetupStepId.INVENTORY_DUMBBELLS) { draft ->
                val current = draft.trainingOptions.inventory ?: EquipmentInventory()
                draft.copy(
                    trainingOptions = draft.trainingOptions.copy(
                        inventory = current.copy(dumbbells = listOf(DumbbellPairStock(weightPerUnitKg = 16.0))),
                    ),
                )
            }
        }
        // Mismo gesto que la UI (`updateInventory`): fila real y finita. El gate
        // de M1/M4 exige dato propio por grupo o el token explícito `none`; aquí
        // se declara material real, nunca datos desconocidos ni auto-declaración.
        confirmStep(vm, SetupStepId.INVENTORY_MACHINES, SetupStepId.DAYS) {
            vm.updateStep(SetupStepId.INVENTORY_MACHINES) { draft ->
                val current = draft.trainingOptions.inventory ?: EquipmentInventory()
                draft.copy(
                    trainingOptions = draft.trainingOptions.copy(
                        inventory = current.copy(
                            machines = current.machines + MachineLoadRange(
                                name = "Polea de poleas",
                                minLoadKg = 10.0,
                                maxLoadKg = 90.0,
                                incrementKg = 2.5,
                                baseLoadKg = 20.0,
                            ),
                        ),
                    ),
                )
            }
        }

        confirmStep(vm, SetupStepId.DAYS, SetupStepId.WEEKDAYS) { vm.setStepChoice(SetupStepId.DAYS, "3") }
        confirmStep(vm, SetupStepId.WEEKDAYS, SetupStepId.SESSION_TIME) {
            vm.setStepChoices(SetupStepId.WEEKDAYS, setOf("1", "3", "5"))
        }
        confirmStep(vm, SetupStepId.SESSION_TIME, SetupStepId.PRIORITIES) {
            vm.setStepText(SetupStepId.SESSION_TIME, "60")
            vm.setStepNumber(SetupStepId.SESSION_TIME, 60.0)
        }

        confirmStep(vm, SetupStepId.PRIORITIES, SetupStepId.SPLIT) {
            vm.updateStep(SetupStepId.PRIORITIES) { draft ->
                draft.copy(trainingOptions = draft.trainingOptions.copy(orderPriorities = priorityBag()))
            }
        }
        assertEquals(priorityBag(), vm.state.value.draft.trainingOptions.orderPriorities)
        confirmStep(vm, SetupStepId.SPLIT, SetupStepId.PLAN) { vm.setStepChoice(SetupStepId.SPLIT, "recommended") }

        // Candidatos reales del catálogo: se elige uno, nunca un plan inventado.
        awaitCondition(
            vm,
            "candidatos de plan reales",
            timeoutMs = 40_000,
        ) { !it.isCandidateLoading && (it.availablePlanCandidates + it.planCandidates).isNotEmpty() }
        val candidates = vm.state.value.availablePlanCandidates.ifEmpty { vm.state.value.planCandidates }
        val native = candidates.firstOrNull { it.source == "NATIVE" }
        if (native == null) {
            fail("sin candidato nativo entre ${candidates.map { it.source }}")
            return
        }
        confirmStep(vm, SetupStepId.PLAN, SetupStepId.TRAINING_MAX) { vm.selectPlan(native.id) }
        assertEquals("el plan elegido es el candidato real", native.id, vm.state.value.draft.selectedCatalogId)

        confirmStep(vm, SetupStepId.TRAINING_MAX, SetupStepId.AUTOREGULATION) {
            vm.setStepChoice(SetupStepId.TRAINING_MAX, "no")
        }
        // PROPOSE es el valor por defecto del contrato: el paso nunca bloquea.
        assertEquals(AutoregulationMode.PROPOSE, vm.state.value.draft.trainingOptions.autoregulationMode)
        confirmStep(vm, SetupStepId.AUTOREGULATION, SetupStepId.WARMUPS)
        confirmStep(vm, SetupStepId.WARMUPS, SetupStepId.TRAINING_REVIEW)
        confirmStep(vm, SetupStepId.TRAINING_REVIEW, SetupStepId.MILESTONE_TRAINING)
        confirmStep(vm, SetupStepId.MILESTONE_TRAINING, SetupStepId.NUTRITION_START)
    }

    /** Objetivos propios: cadena manual completa, pesaje real y omisión explícita. */
    private fun TestScope.walkSelfDefinedNutrition(vm: SetupWizardViewModel) {
        confirmStep(vm, SetupStepId.NUTRITION_START, SetupStepId.NUTRITION_DIRECTION) {
            vm.setStepChoice(SetupStepId.NUTRITION_START, "self_defined")
        }
        assertEquals(NutritionConfigurationMode.SELF_DEFINED, vm.state.value.draft.nutritionDraft?.configurationMode)
        confirmStep(vm, SetupStepId.NUTRITION_DIRECTION, SetupStepId.NUTRITION_RHYTHM) {
            vm.setStepChoice(SetupStepId.NUTRITION_DIRECTION, "deficit")
        }
        val rhythmStep = SetupStepId.NUTRITION_RHYTHM
        // Igual que NutritionRhythmContent: proyecta el enum y registra además
        // la selección estable que valida el paso. La prueba antes omitía la
        // primera escritura real de la UI.
        vm.updateStep(rhythmStep) { draft ->
            draft.copy(
                nutritionDraft = checkNotNull(draft.nutritionDraft) { "borrador SELF_DEFINED" }
                    .copy(pacePreset = WizardPacePreset.MEDIUM),
            )
        }
        vm.setStepChoice(rhythmStep, "medium")
        awaitRest(vm)
        assertEquals(setOf("medium"), vm.state.value.draft.selectedValues(rhythmStep))
        assertEquals(WizardPacePreset.MEDIUM, vm.state.value.draft.nutritionDraft?.pacePreset)
        confirmStep(vm, rhythmStep, SetupStepId.NUTRITION_TARGET)
        confirmStep(vm, SetupStepId.NUTRITION_TARGET, SetupStepId.NUTRITION_HISTORY_CONTEXT) {
            vm.updateStep(SetupStepId.NUTRITION_TARGET) { draft ->
                draft.copy(nutritionDraft = draft.nutritionDraft?.copy(targetWeightText = "76"))
            }
            vm.setStepText(SetupStepId.NUTRITION_TARGET, "76")
            vm.setStepNumber(SetupStepId.NUTRITION_TARGET, 76.0)
        }
        // Contexto opcional: OMITIR explícito, sin tendencia ni máximo fabricados.
        assertTrue(
            "el contexto de historia es opcional",
            SetupStepDefinitions.of(SetupStepId.NUTRITION_HISTORY_CONTEXT)!!.allowSkip,
        )
        confirmStep(vm, SetupStepId.NUTRITION_HISTORY_CONTEXT, SetupStepId.NUTRITION_MANUAL_CALORIES) {
            vm.skipStep(SetupStepId.NUTRITION_HISTORY_CONTEXT)
        }
        assertNull("sin tendencia inventada", vm.state.value.draft.weightTrend)
        assertNull("sin máximo inventado", vm.state.value.draft.previousMaximumWeightKg)
        assertEquals(
            SetupAnswerProvenance.USER_DECLARED,
            vm.state.value.draft.stepProgress.answers[SetupStepId.NUTRITION_HISTORY_CONTEXT],
        )
        confirmStep(vm, SetupStepId.NUTRITION_MANUAL_CALORIES, SetupStepId.NUTRITION_MANUAL_CARBS_FAT) {
            vm.updateStep(SetupStepId.NUTRITION_MANUAL_CALORIES) { draft ->
                draft.copy(
                    nutritionDraft = draft.nutritionDraft?.copy(
                        manualCalorieTargetText = "2100",
                        manualProteinText = "165",
                    ),
                )
            }
            vm.setStepText(SetupStepId.NUTRITION_MANUAL_CALORIES, "2100")
        }
        confirmStep(vm, SetupStepId.NUTRITION_MANUAL_CARBS_FAT, SetupStepId.NUTRITION_DISTRIBUTION) {
            vm.updateStep(SetupStepId.NUTRITION_MANUAL_CARBS_FAT) { draft ->
                draft.copy(nutritionDraft = draft.nutritionDraft?.copy(manualCarbsText = "180", manualFatText = "65"))
            }
            vm.setStepText(SetupStepId.NUTRITION_MANUAL_CARBS_FAT, "180")
        }
        confirmStep(vm, SetupStepId.NUTRITION_DISTRIBUTION, SetupStepId.NUTRITION_WEIGH_INS) {
            vm.setStepChoice(SetupStepId.NUTRITION_DISTRIBUTION, "variable")
        }
        confirmStep(vm, SetupStepId.NUTRITION_WEIGH_INS, SetupStepId.NUTRITION_RESULT) {
            vm.updateStep(SetupStepId.NUTRITION_WEIGH_INS) { draft ->
                draft.copy(
                    historicalWeighIns = draft.historicalWeighIns + SetupWeighIn(
                        id = "setup-weighin-1",
                        dateIso = historyDate().toString(),
                        weightKg = 81.0,
                    ),
                )
            }
        }
        confirmStep(vm, SetupStepId.NUTRITION_RESULT, SetupStepId.MILESTONE_NUTRITION)
        confirmStep(vm, SetupStepId.MILESTONE_NUTRITION, SetupStepId.RINGS_RECENT)
    }

    /** Rings parcial: historial «No lo sé» + sensaciones declaradas (muscular 2). */
    private fun TestScope.walkRingsPartial(vm: SetupWizardViewModel) {
        confirmStep(vm, SetupStepId.RINGS_RECENT, SetupStepId.RINGS_MUSCLE_FEELING) {
            vm.setStepChoice(SetupStepId.RINGS_RECENT, "unknown")
        }
        val route = SetupStepGraph.stepIds(vm.state.value.draft.stepContext())
        assertFalse("el desconocido del historial no añade evidencia", SetupStepId.RINGS_SESSIONS in route)
        confirmStep(vm, SetupStepId.RINGS_MUSCLE_FEELING, SetupStepId.RINGS_ENERGY_FEELING) {
            vm.setStepChoice(SetupStepId.RINGS_MUSCLE_FEELING, "2")
        }
        confirmStep(vm, SetupStepId.RINGS_ENERGY_FEELING, SetupStepId.RINGS_STRUCTURE_FEELING) {
            vm.setStepChoice(SetupStepId.RINGS_ENERGY_FEELING, "3")
        }
        confirmStep(vm, SetupStepId.RINGS_STRUCTURE_FEELING, SetupStepId.RINGS_DISCOMFORT) {
            vm.setStepChoice(SetupStepId.RINGS_STRUCTURE_FEELING, "2")
        }
        confirmStep(vm, SetupStepId.RINGS_DISCOMFORT, SetupStepId.RINGS_RESULT) {
            vm.setStepChoices(SetupStepId.RINGS_DISCOMFORT, setOf("none"))
        }
        confirmStep(vm, SetupStepId.RINGS_RESULT, SetupStepId.MILESTONE_RINGS)
        confirmStep(vm, SetupStepId.MILESTONE_RINGS, SetupStepId.REVIEW_ACTIVATE)
        val rings = checkNotNull(vm.state.value.draft.ringsAnswers) { "respuestas de RINGS" }
        assertEquals(2, checkNotNull(rings.muscleFeeling) { "sensación muscular declarada" })
        assertEquals(SetupRecentTrainingState.UNKNOWN, rings.recentTrainingState)
    }

    // ─── Aserciones sobre Room tras el commit real ────────────────────────────

    private fun assertCommittedFullJourney(
        commitId: String,
        draftId: String,
        receipt: String,
        draftAtReview: SetupWizardDraft,
        historyDate: LocalDate,
    ) {
        // Programa activo y ejecutable, con el contrato de entrenamiento escrito.
        val committed = checkNotNull(room { db.programDao().getById(commitId) }) { "programa no persistido" }.toProgram()
        ProgramExecutionContract.requireExecutable(committed)
        assertTrue(
            "sesiones reales",
            committed.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }
                .flatMap { it.weeks }.flatMap { it.sessions }.isNotEmpty(),
        )
        assertEquals("autorregulación PROPOSE", AutoregulationMode.PROPOSE, committed.autoregulationMode)
        assertNull("calentamientos: preset del plan", committed.planWarmupConfig)
        assertEquals(commitId, room { db.stateDao().getActiveProgram()?.toActiveProgramState()?.programId })

        // Prioridades: SOLO orden, contrastadas con el programa realmente guardado.
        val capabilities = OrderPrioritiesContract.capabilitiesOf(committed, options = draftAtReview.trainingOptions)
        assertEquals("la bolsa pedida viaja al contrato", priorityBag(), capabilities.requested)
        assertNotNull("bolsa válida (≤2 por músculo, 5 en total)", capabilities.normalizedRequested)
        assertEquals("la bolsa se aplicó al generar", OrderPrioritiesStatus.APPLIED, capabilities.status)
        assertEquals(priorityBag(), capabilities.appliedBag)

        // Plan de nutrición activo + metas duraderas.
        val planId = checkNotNull(room { db.nutritionDao().getActiveState()?.activePlanId }) { "sin plan activo" }
        val plan = room { db.nutritionDao().getAllPlans() }.single { it.id == planId }.toNutritionPlan()
        assertTrue("plan activo", plan.isActive)
        assertEquals(2100, plan.calorieTarget)
        assertEquals(CalculationOrigin.MANUAL, plan.calculationOrigin)

        val settings = checkNotNull(room { db.settingsDao().get() }) { "settings no persistidos" }.toSettings()
        assertEquals(false, settings.nutritionTrackingOnly)
        assertEquals(true, settings.onboardingCompleted)
        assertEquals(true, settings.onboardingProgramDone)
        assertEquals(true, settings.onboardingNutritionDone)
        assertEquals("Ana", settings.username)
        assertEquals(2100, checkNotNull(settings.dailyCalorieGoal) { "meta calórica diaria durable" })
        assertNotNull("proteína diaria durable", settings.dailyProteinGoal)
        assertNotNull("hidratos diarios duraderos", settings.dailyCarbGoal)
        assertNotNull("grasas diarias duraderas", settings.dailyFatGoal)

        val snapshot = checkNotNull(
            room { db.nutritionDao().getDailyGoalSnapshot(LocalDate.now().toString()) },
        ) { "objetivo del día persistido en la transacción" }
        assertEquals(planId, snapshot.planId)
        assertTrue(
            "objetivo de hoy real",
            checkNotNull(snapshot.calorieTargetKcal) { "kcal del snapshot de hoy" } > 0,
        )

        // Meta derivada del objetivo declarado en el wizard.
        val goals = room { db.bodyProgressDao().getAllGoals() }
        assertEquals(1, goals.size)
        assertEquals("WEIGHT", goals.single().metric)
        assertEquals(planId, goals.single().linkedPlanId)
        assertEquals(76.0, goals.single().targetValueSi, 0.001)

        // Observaciones reales: actual fechado, grasa estimada y pesaje histórico.
        val observations = room { db.bodyProgressDao().getAllObservations() }.map { row ->
            checkNotNull(row.toBodyObservation()) { "observación ilegible en Room: ${row.id}" }
        }
        assertEquals("tendencia y omisiones nunca se convierten en observaciones", 3, observations.size)
        val now = System.currentTimeMillis()
        val currentWeight = observations.single { it.metric == BodyMetric.WEIGHT && it.id.contains("/weight/") }
        assertEquals(77.0, currentWeight.valueSi, 0.001)
        assertEquals(BodyObservationQuality.MEASURED, currentWeight.quality)
        assertTrue(
            "pesaje actual fechado en esta sesión (${currentWeight.timestampEpochMs})",
            currentWeight.timestampEpochMs in (now - 600_000L)..now,
        )
        val bodyFat = observations.single { it.metric == BodyMetric.BODY_FAT_PERCENT }
        assertEquals(18.0, bodyFat.valueSi, 0.001)
        assertEquals("estimación visual nunca se vuelve medición", BodyObservationQuality.ESTIMATED, bodyFat.quality)
        val historical = observations.single { it.id.contains("/weighin/") }
        assertEquals(
            "pesaje histórico con su fecha explícita",
            historyDate.toString(),
            historical.id.substringAfterLast('/'),
        )
        assertEquals(81.0, historical.valueSi, 0.001)

        // Inventario declarado, finito y persistido en Settings: los dos grupos
        // de la ruta fija (DUMBBELLS + MACHINES) con cantidades reales.
        val inventory = checkNotNull(settings.equipmentInventory) { "inventario sin persistir" }
        assertEquals(16.0, inventory.dumbbells.single().weightPerUnitKg, 0.001)
        assertTrue(inventory.dumbbells.single().weightPerUnitKg.isFinite())
        val machine = inventory.machines.single()
        assertEquals(90.0, checkNotNull(machine.maxLoadKg) { "máximo de la máquina" }, 0.001)
        assertTrue(
            "rangos de máquina finitos",
            listOf(machine.minLoadKg, machine.incrementKg, machine.baseLoadKg).all { it.isFinite() },
        )
        assertTrue(
            "sin discos ni barra declarados no se inventa material",
            inventory.plates.isEmpty() && inventory.barbellWeightKg == null,
        )

        // Check-in de Rings escrito con la sensación muscular declarada.
        assertNotNull(
            "check-in real de Rings",
            room { db.augeDao().getWellbeingForDate(LocalDate.now().toString()) },
        )

        // Receipt + borrador consumidos en la MISMA transacción.
        assertNotNull(room { db.setupCommitReceiptDao().get(receipt) })
        assertNull("el borrador principal se borra al confirmar", room { db.setupDraftDao().getDraft(draftId) })
    }

    /** Nada del alta en Room; solo el borrador del wizard, que ya está en curso. */
    private fun roomUntouchedBeforeReview(): Boolean =
        room { db.nutritionDao().getAllPlans() }.isEmpty() &&
            room { db.bodyProgressDao().getAllObservations() }.isEmpty() &&
            room { db.bodyProgressDao().getAllGoals() }.isEmpty() &&
            room { db.stateDao().getActiveProgram() } == null &&
            room { db.settingsDao().get() } == null &&
            room { db.setupDraftDao().getAllDrafts() }.isNotEmpty()

    // ─── Esperas: scheduler acotado, sin Thread.sleep ────────────────────────

    /**
     * Espera acotada sobre el scheduler de pruebas: drena el trabajo encolado y,
     * si la condición sigue sin cumplirse, vuelve a drenar hasta el plazo. El
     * trabajo real de Room corre en `Dispatchers.IO` en paralelo, así que la
     * espera termina cuando la publicación del ViewModel llega al scheduler.
     * Nunca duerme el hilo y falla con diagnóstico completo si no se cumple.
     */
    private fun TestScope.awaitCondition(
        vm: SetupWizardViewModel,
        what: String,
        timeoutMs: Long = 20_000,
        condition: (SetupWizardState) -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            advanceUntilIdle()
            val state = vm.state.value
            if (condition(state)) return
            if (System.currentTimeMillis() > deadline) {
                fail(
                    "Timeout esperando $what en ${state.currentStep}: machine=${state.machineState}, " +
                        "errores=${state.errors}, lastFailure=${state.lastFailure}, preview=${state.previewError} " +
                        "(cargando=${state.isPreviewLoading}/${state.isCandidateLoading}/${state.ringsPreviewLoading}), " +
                        "candidatos=${state.planCandidates.size}/${state.availablePlanCandidates.size}, " +
                        "nutrition=${state.nutritionErrors}, rings=${state.ringsPreviewError}, " +
                        "bateriasAusentes=${state.ringsBatteriesPreview == null}",
                )
            }
        }
    }

    /** Ninguna escritura del wizard en vuelo (setters, confirmaciones, commit). */
    private fun TestScope.awaitRest(vm: SetupWizardViewModel) =
        awaitCondition(vm, "escrituras del wizard en reposo") { state ->
            !state.isLoading && !state.isCommitting && !state.isSubmittingAnswer && !state.isSavingAndExiting &&
                state.machineState != WizChatMachineState.PersistingAnswer &&
                state.machineState != WizChatMachineState.Committing
        }

    /** Lo anterior más previews en calma, verificado dos veces seguidas. */
    private fun TestScope.awaitQuiescent(vm: SetupWizardViewModel) {
        val idle: (SetupWizardState) -> Boolean = { state ->
            !state.isLoading && !state.isCommitting && !state.isSubmittingAnswer && !state.isSavingAndExiting &&
                state.machineState != WizChatMachineState.PersistingAnswer &&
                state.machineState != WizChatMachineState.Committing &&
                state.machineState != WizChatMachineState.PreparingPreview &&
                !state.isPreviewLoading && !state.isCandidateLoading && !state.ringsPreviewLoading
        }
        awaitCondition(vm, "previews en reposo", timeoutMs = 30_000, condition = idle)
        repeat(25) { advanceUntilIdle() }
        awaitCondition(vm, "previews en reposo (estable)", timeoutMs = 30_000, condition = idle)
    }

    /**
     * El preview de RINGS es prerrequisito REAL de la revisión: sin baterías el
     * commit se queda en la puerta y nunca llega al coordinador. El VM esconde
     * la excepción detrás de un mensaje genérico (no guarda `lastFailure`), así
     * que aquí se vuelca TODO el estado y se reproduce el cálculo con el MISMO
     * motor real para ver la causa exacta. Falla sin ocultar nada; nunca se
     * valida el commit con el preview roto.
     */
    private fun TestScope.assertRingsPreviewReady(vm: SetupWizardViewModel) {
        val state = vm.state.value
        if (state.ringsBatteriesPreview != null && state.ringsPreviewError == null && !state.ringsPreviewLoading) return
        fail(
            "Preview de RINGS no utilizable: " +
                "ringsPreviewError=${state.ringsPreviewError} lastFailure=${state.lastFailure} " +
                "bateriasAusentes=${state.ringsBatteriesPreview == null} loading=${state.ringsPreviewLoading} " +
                "machine=${state.machineState} errors=${state.errors} || " +
                "dependencias=${ringsDependencyProbes()} || ${ringsCalculatorReproducer(vm)}",
        )
    }

    /**
     * Cada dependencia REAL que el preview consulta fuera del puerto
     * `SetupWizardEnvironment`: así se nombra exactamente cuál está rota
     * (repositorio, singleton de AUGE o la base de fichero).
     */
    private fun ringsDependencyProbes(): String = listOf(
        probe("programRepo") { "history=${ProgramRepository.getInstance().history.value.size}" },
        probe("nutritionRepo") { "logs=${NutritionRepository.getInstance().nutritionLogs.value.size}" },
        probe("augeWellbeing") { "hoy=${runBlocking { AugeRepository.getInstance(app).getTodayWellbeing() }}" },
        probe("archivoDb") { "settings=${runBlocking { KpknDatabase.getInstance(app).settingsDao().get() }}" },
    ).joinToString(" | ")

    private fun probe(name: String, block: () -> Any): String = try {
        "$name=OK(${block()})"
    } catch (error: Throwable) {
        "$name=FALLA ${error::class.java.name}: ${error.message}"
    }

    /**
     * Reproductor REAL: mismo motor, mismo mapeo y mismas entradas que el VM
     * (`ringsPreview()` es su API pública). Si el intento del VM falló y este idéntico
     * no, el estado quedó de un intento cancelado; si también falla, aquí se ve la
     * excepción exacta que el VM no publica.
     */
    private fun ringsCalculatorReproducer(vm: SetupWizardViewModel): String = try {
        val mapping = vm.ringsPreview()
        val draft = vm.state.value.draft
        val evidenceInput = mapping.evidence?.let { SetupRingsEvidenceInput.Available(it) }
            ?: if (environment.settings.initialRecoveryEvidence != null) SetupRingsEvidenceInput.Preserved
            else SetupRingsEvidenceInput.Absent
        val preview = runBlocking {
            SetupRingsPreviewCalculator(app).calculate(
                settings = environment.settings,
                evidenceInput = evidenceInput,
                commitId = draft.commitId,
                manualMuscles = draft.manualMuscleOverrides,
                manualEnergy = draft.manualEnergyOverride,
                manualStructure = draft.manualStructureOverride,
                discomforts = SetupRingsResponseMapping.discomfortField(
                    mapping.discomfortResponse,
                    mapping.discomfortIds,
                ),
                nowMs = System.currentTimeMillis(),
                checkIn = mapping.previewCheckIn(),
            )
        }
        "reproductor=OK baterias=${preview.batteries.muscular}/${preview.batteries.cnc}/${preview.batteries.spinal} " +
            "(el intento del VM falló y este idéntico no: el estado conserva un intento fallido)"
    } catch (error: Throwable) {
        "reproductor=FALLA ${error::class.java.name}: ${error.message} @ ${error.stackTrace.firstOrNull()}"
    }

    /** Escribe, confirma y exige el paso siguiente; registra la cobertura. */
    private fun TestScope.confirmStep(
        vm: SetupWizardViewModel,
        step: SetupStepId,
        expectedNext: SetupStepId,
        write: () -> Unit = {},
    ) {
        write()
        awaitRest(vm)
        val revision = vm.state.value.draft.revision
        val result = vm.submitCurrentStep(step, expectedRevision = revision)
        assertEquals(
            "Continuar sobre $step debe aceptarse (errores=${vm.state.value.errors})",
            SetupSubmitOutcome.ACCEPTED,
            result.outcome,
        )
        awaitCondition(vm, "cursor en $expectedNext tras $step") {
            it.draft.stepProgress.currentStepId == expectedNext
        }
        submitted += step
    }

    // ─── Rutas esperadas (explícitas) ────────────────────────────────────────

    private fun fullSelfDefinedRoute(): List<SetupStepId> = listOf(
        SetupStepId.NAME, SetupStepId.AGE, SetupStepId.HEIGHT, SetupStepId.WEIGHT,
        SetupStepId.EQUATION_SEX, SetupStepId.BODY_FAT, SetupStepId.MILESTONE_BASICS,
        SetupStepId.EXPERIENCE, SetupStepId.ROUTE, SetupStepId.GOAL,
        SetupStepId.VOLUME_TECHNIQUE, SetupStepId.VOLUME_CONSISTENCY,
        SetupStepId.VOLUME_STRENGTH, SetupStepId.VOLUME_MOBILITY,
        SetupStepId.EQUIPMENT, SetupStepId.INVENTORY_DUMBBELLS, SetupStepId.INVENTORY_MACHINES,
        SetupStepId.DAYS, SetupStepId.WEEKDAYS, SetupStepId.SESSION_TIME,
        SetupStepId.PRIORITIES, SetupStepId.SPLIT, SetupStepId.PLAN, SetupStepId.TRAINING_MAX,
        SetupStepId.AUTOREGULATION, SetupStepId.WARMUPS, SetupStepId.TRAINING_REVIEW,
        SetupStepId.MILESTONE_TRAINING,
        SetupStepId.NUTRITION_START, SetupStepId.NUTRITION_DIRECTION, SetupStepId.NUTRITION_RHYTHM,
        SetupStepId.NUTRITION_TARGET, SetupStepId.NUTRITION_HISTORY_CONTEXT,
        SetupStepId.NUTRITION_MANUAL_CALORIES, SetupStepId.NUTRITION_MANUAL_CARBS_FAT,
        SetupStepId.NUTRITION_DISTRIBUTION, SetupStepId.NUTRITION_WEIGH_INS,
        SetupStepId.NUTRITION_RESULT, SetupStepId.MILESTONE_NUTRITION,
        SetupStepId.RINGS_RECENT, SetupStepId.RINGS_MUSCLE_FEELING, SetupStepId.RINGS_ENERGY_FEELING,
        SetupStepId.RINGS_STRUCTURE_FEELING, SetupStepId.RINGS_DISCOMFORT,
        SetupStepId.RINGS_RESULT, SetupStepId.MILESTONE_RINGS,
        SetupStepId.REVIEW_ACTIVATE,
    )

    private fun fullTrackingOnlyRoute(): List<SetupStepId> = listOf(
        SetupStepId.NAME, SetupStepId.AGE, SetupStepId.HEIGHT, SetupStepId.WEIGHT,
        SetupStepId.EQUATION_SEX, SetupStepId.BODY_FAT, SetupStepId.MILESTONE_BASICS,
        SetupStepId.EXPERIENCE, SetupStepId.ROUTE, SetupStepId.GOAL,
        SetupStepId.VOLUME_TECHNIQUE, SetupStepId.VOLUME_CONSISTENCY,
        SetupStepId.VOLUME_STRENGTH, SetupStepId.VOLUME_MOBILITY,
        SetupStepId.EQUIPMENT, SetupStepId.INVENTORY_DUMBBELLS, SetupStepId.INVENTORY_MACHINES,
        SetupStepId.DAYS, SetupStepId.WEEKDAYS, SetupStepId.SESSION_TIME,
        SetupStepId.PRIORITIES, SetupStepId.SPLIT, SetupStepId.PLAN, SetupStepId.TRAINING_MAX,
        SetupStepId.AUTOREGULATION, SetupStepId.WARMUPS, SetupStepId.TRAINING_REVIEW,
        SetupStepId.MILESTONE_TRAINING,
        SetupStepId.NUTRITION_START, SetupStepId.NUTRITION_RESULT, SetupStepId.MILESTONE_NUTRITION,
        SetupStepId.RINGS_RECENT, SetupStepId.RINGS_MUSCLE_FEELING, SetupStepId.RINGS_ENERGY_FEELING,
        SetupStepId.RINGS_STRUCTURE_FEELING, SetupStepId.RINGS_DISCOMFORT,
        SetupStepId.RINGS_RESULT, SetupStepId.MILESTONE_RINGS,
        SetupStepId.REVIEW_ACTIVATE,
    )

    /**
     * Confirmaciones esperadas: la ruta sin revisión más la reconfirmación que
     * produce Atrás (HEIGHT → Atrás → AGE → Continuar).
     */
    private fun expectedConfirmations(route: List<SetupStepId>): List<SetupStepId> =
        route.dropLast(1).toMutableList().apply { addAll(2, listOf(SetupStepId.AGE)) }

    private fun priorityBag(): Map<String, Int> = linkedMapOf(
        "Pectorales" to 2,
        "Dorsales" to 2,
        "Tríceps" to 1,
    )

    private fun historyDate(): LocalDate = LocalDate.now().minusMonths(3)

    // ─── Adaptadores reales sobre Room ───────────────────────────────────────

    /** Adaptador fino y REAL sobre Room: delega, nunca reimplementa el guard. */
    private class RoomWizardPersistence(db: KpknDatabase) : SetupWizardPersistence {
        private val drafts = SetupDraftRepository(db)
        private val resolver = SetupDraftResolver(db)

        override suspend fun load(draftId: String): SetupDraft? = drafts.load(draftId)

        override suspend fun save(
            draftId: String,
            payloadJson: String,
            revision: Long,
            catalogRevision: String?,
        ): SetupDraft = drafts.save(draftId, payloadJson, revision, catalogRevision)

        override suspend fun discard(draftId: String) = drafts.discard(draftId)

        override suspend fun listRecoverable(): List<SetupDraftCandidate> = resolver.listRecoverable()
    }

    /**
     * Coordinador REAL de altas (transacción, receipt idempotente y rollback),
     * sin repos: la publicación en memoria la sustituye la lectura directa de
     * Room, que es lo que este test verifica.
     */
    private class RoomWizardCommits(db: KpknDatabase) : SetupWizardCommits {
        private val coordinator = SetupCommitCoordinator(db)

        override suspend fun commit(request: SetupCommitRequest): SetupCommitResult = coordinator.commit(request)
    }

    /** Entorno acotado: fija Settings y no toca los repos de Android. */
    private class FixedSettingsEnvironment(override val settings: Settings) : SetupWizardEnvironment {
        override suspend fun awaitReady() = Unit
        override fun activeProgramId(): String? = null
        override fun activeNutritionPlanId(): String? = null
        override fun activeNutritionPlan(): NutritionPlan? = null
        override fun nutritionPlan(id: String): NutritionPlan? = null
        override fun hasInitialRecoveryEvidence(): Boolean = false
        override suspend fun refreshBodyProgress() = Unit
    }

    /** Un único store por test: [ViewModelStore.clear] cierra el `viewModelScope` real. */
    private fun vm(): SetupWizardViewModel =
        SetupWizardViewModel(app, handle, persistence, environment, commits).also { model ->
            viewModelStore.put("setup-wizard", model)
        }

    private fun <T> room(block: suspend () -> T): T = runBlocking { block() }
}

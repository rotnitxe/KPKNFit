package com.example.kpkn.screens.onboarding

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toActiveProgramState
import com.example.kpkn.data.db.toProgram
import com.example.kpkn.data.db.toSettings
import com.example.kpkn.data.exercises.catalogv2.CatalogCompositionMetadataProvider
import com.example.kpkn.data.exercises.catalogv2.CatalogV2ProcessCache
import com.example.kpkn.data.models.AthleteType
import com.example.kpkn.data.models.AutoregulationMode
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.resolvedSchedulePlan
import com.example.kpkn.data.onboarding.SetupCommitCoordinator
import com.example.kpkn.data.onboarding.SetupCommitRequest
import com.example.kpkn.data.onboarding.SetupCommitResult
import com.example.kpkn.data.onboarding.SetupDraft
import com.example.kpkn.data.onboarding.SetupDraftCandidate
import com.example.kpkn.data.onboarding.SetupDraftRepository
import com.example.kpkn.data.onboarding.SetupDraftResolver
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.protocols.definitions.AuthoredPhulPhatRecipes
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.onboarding.CapabilityLevel
import com.example.kpkn.domain.onboarding.CapabilitySkill
import com.example.kpkn.domain.onboarding.EquipmentSymbolId
import com.example.kpkn.domain.onboarding.EquipmentSymbols
import com.example.kpkn.domain.onboarding.GeneratedPlans
import com.example.kpkn.domain.onboarding.LiftMark
import com.example.kpkn.domain.onboarding.MuscleSymbol
import com.example.kpkn.domain.onboarding.MuscleSymbols
import com.example.kpkn.domain.onboarding.PlanEvaluationStage
import com.example.kpkn.domain.onboarding.PlanMaterializationException
import com.example.kpkn.domain.onboarding.PlanRejectionReason
import com.example.kpkn.domain.onboarding.SetupAnswerProvenance
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupValueState
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.domain.onboarding.WizChatMachineState
import com.example.kpkn.domain.training.ProgramExecutionContract
import com.example.kpkn.domain.training.approach.ApproachInfoProvider
import com.example.kpkn.domain.training.approach.ApproachLevel
import com.example.kpkn.domain.training.approach.ApproachOptions
import com.example.kpkn.domain.training.approach.ApproachPlanner
import com.example.kpkn.domain.training.split.SplitRedistributor
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
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.minutes

/**
 * Entreno v2 · los pasos PLAN y WEEK_LAYOUT de punta a punta con el ViewModel REAL (catálogo aprobado, generador de
 * rutinas, planificador, evaluador y redistribuidor reales), persistencia y coordinador de altas reales sobre Room en
 * memoria. Solo el entorno de lectura (Ajustes) es fijo.
 *
 * Cubre: objetivo general → un solo programa «a medida» → elegir → semana armada (adaptar a un reparto y mover una
 * sesión) → activar, con el programa activado IGUAL al previsualizado y todo lo declarado persistido; disciplina con
 * autores (el «a medida» al frente); disciplina sin autores (una «versión inicial»); cambiar días o material (vuelve a
 * barrer y deja PLAN por revisar); «Otra versión»; «lo haré más adelante»; y el fallo del motor con «Reintentar».
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class SetupWizardEntrenoPlanTest {

    private val dispatcher = StandardTestDispatcher()
    private val viewModelStore = ViewModelStore()
    private lateinit var app: Application
    private lateinit var db: KpknDatabase
    private var vmCounter = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        app = ApplicationProvider.getApplicationContext()
        ProgramRepository.closeInstance()
        NutritionRepository.closeInstance()
        KpknDatabase.closeInstance()
        db = KpknDatabase.createInMemory(app)
        val warm = runCatching { runBlocking { CatalogV2ProcessCache.getOrLoad(app) } }
        assertTrue("el catálogo real no carga en Robolectric: ${warm.exceptionOrNull()?.message}", warm.isSuccess)
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        ProgramRepository.closeInstance()
        NutritionRepository.closeInstance()
        KpknDatabase.closeInstance()
        db.close()
        Dispatchers.resetMain()
    }

    // ─── Objetivo general: revelado, semana armada y activación ─────────────────────────────────

    @Test
    fun aGeneralGoalRevealsOneTailoredProgramWhoseWeekIsArrangedAndActivatedAsPreviewed() = runTest(dispatcher.scheduler, timeout = 10.minutes) {
        val vm = newVm()
        vm.initialize(SetupWizardMode.TRAINING_ONLY)
        await(vm, "wizard cargado") { !it.isLoading && it.currentStep == SetupStepId.NAME }
        val answers = Answers(
            profile = TrainingGoalProfile.STRENGTH_MUSCLE,
            places = setOf(TrainingPlace.GYM),
            days = setOf(1, 2, 4, 5),
            freshDay = 4,
            minutes = 60,
            muscles = setOf(MuscleSymbol.CHEST, MuscleSymbol.BACK),
            marks = mapOf(LiftMark.SQUAT to 120.0),
        )
        walkUntil(vm, SetupStepId.PLAN, answers)

        // Barrido: el general recibe SOLO su programa «a medida», con su revelado.
        val swept = await(vm, "programa a medida listo") { idle(it) && it.planSweep == SetupPlanSweep.READY }
        val generatedId = GeneratedPlans.entryIdFor(TrainingGoalProfile.STRENGTH_MUSCLE)
        assertEquals(listOf(generatedId), swept.availablePlanCandidates.map { it.id })
        val reveal = swept.planReveals.single()
        assertEquals(generatedId, reveal.planId)
        assertTrue(reveal.generated)
        assertEquals(SetupPlanReveals.KICKER_TAILORED, reveal.kicker)
        assertEquals("la semana del revelado son los días elegidos", listOf(1, 2, 4, 5), reveal.week.map { it.day })
        assertEquals("la principal cae el día con más energía", listOf(4), reveal.week.filter { it.isMain }.map { it.day })
        assertTrue("3–5 razones: ${reveal.reasons}", reveal.reasons.size in 3..5)
        assertNull("sin elegir, PLAN no deja continuar", swept.draft.selectedCatalogId)
        assertEquals(SetupSubmitOutcome.REJECTED, vm.submitCurrentStep(SetupStepId.PLAN).outcome)

        confirm(vm, SetupStepId.PLAN, SetupStepId.WEEK_LAYOUT) { vm.selectPlan(generatedId) }
        val prepared = await(vm, "programa previsualizado") { idle(it) && it.programPreview != null && it.weekLayout != null }
        val preview = checkNotNull(prepared.programPreview)
        assertEquals(PersonalizedPlanCatalog.find(generatedId)?.displayName, preview.name)
        assertEquals(generatedId, preview.structureTemplateId)
        assertEquals(setOf(1, 2, 4, 5), preview.resolvedSchedulePlan().trainingDays)
        val layout = checkNotNull(prepared.weekLayout)
        assertEquals(setOf(1, 2, 4, 5), layout.assignment.keys)
        assertEquals("la semana empieza el día con más energía", 4, layout.weekStartDay)
        assertTrue("repartos de 4 días para adaptar", layout.splitOptions.isNotEmpty())
        assertFalse(layout.authoredStructure)

        // Adaptar a un reparto: redistribuye los ejercicios y guarda `adaptedSplitId`.
        val split = layout.splitOptions.firstOrNull { it.id == "ul_x4" } ?: layout.splitOptions.first()
        vm.adaptToSplit(split.id)
        val adapted = await(vm, "programa adaptado al reparto") {
            idle(it) && it.draft.adaptedSplitId == split.id && it.weekLayout?.selectedSplitId == split.id
        }
        assertTrue(adapted.weekLayout!!.canReset)
        val adaptedProgram = checkNotNull(adapted.programPreview)
        assertNotEquals("las sesiones son las del reparto", sessionsOf(preview).map { it.name }, sessionsOf(adaptedProgram).map { it.name })
        assertEquals(
            "se mueven ejercicios enteros: el mismo total",
            sessionsOf(preview).sumOf { it.allExercises().size },
            sessionsOf(adaptedProgram).sumOf { it.allExercises().size },
        )

        // Mover una sesión del lunes al miércoles (día libre).
        val mondaySession = adapted.weekLayout!!.assignment.getValue(1)
        vm.moveSession(mondaySession, 3)
        // El tablero lo ve al instante (sin esperar a que la vista previa se re-arme), y ya se puede restablecer.
        assertEquals(mondaySession, vm.state.value.weekLayout?.assignment?.get(3))
        assertNull(vm.state.value.weekLayout?.assignment?.get(1))
        val moved = await(vm, "sesión movida y programa re-armado") {
            idle(it) && it.weekLayout?.assignment?.get(3) == mondaySession &&
                it.programPreview?.resolvedSchedulePlan()?.trainingDays == setOf(2, 3, 4, 5)
        }
        val final = checkNotNull(moved.programPreview)
        assertEquals(setOf(2, 3, 4, 5), final.resolvedSchedulePlan().trainingDays)
        assertEquals(mapOf(mondaySession to 3), moved.draft.weekLayoutOverrides.filterKeys { it == mondaySession })

        confirm(vm, SetupStepId.WEEK_LAYOUT, SetupStepId.MILESTONE_TRAINING)
        confirm(vm, SetupStepId.MILESTONE_TRAINING, SetupStepId.REVIEW_ACTIVATE)
        await(vm, "revisión en reposo") { idle(it) && it.programPreview != null }
        val shown = checkNotNull(vm.state.value.programPreview)

        val receipt = vm.commit()
        assertNotNull("alta sin errores: ${vm.state.value.errors}", receipt)
        assertEquals(WizChatMachineState.Committed, vm.state.value.machineState)

        // Lo activado es EXACTAMENTE lo previsualizado, con todo lo declarado.
        val commitId = vm.state.value.draft.commitId
        val saved = checkNotNull(room { db.programDao().getById(commitId) }) { "programa no guardado" }.toProgram()
        assertEquals("el programa activado es el previsualizado", weekShape(shown), weekShape(saved))
        // Entreno v2 (D3): la aproximación y la movilidad obligatorias viajan con el programa. Lo activado lleva EXACTAMENTE las
        // del previsualizado (el generador las pone una vez; la semana armada y la activación no las pierden ni las duplican) y la
        // sesión principal, la del día con más energía, abre con movilidad previa y, si su primer ejercicio es un básico con
        // carga, con su rampa de aproximación.
        assertEquals("la aproximación y la movilidad activadas son las previsualizadas", approachShape(shown), approachShape(saved))
        assertMainSessionOpensApproached("perfil general (generado)", saved, mainDay = 4)
        assertEquals(shown.id, saved.id)
        assertEquals(shown.name, saved.name)
        assertEquals(shown.structureTemplateId, saved.structureTemplateId)
        ProgramExecutionContract.requireExecutable(saved)
        assertEquals(commitId, room { db.stateDao().getActiveProgram()?.toActiveProgramState()?.programId })
        assertEquals(setOf(2, 3, 4, 5), saved.resolvedSchedulePlan().trainingDays)
        assertEquals(AutoregulationMode.PROPOSE, saved.autoregulationMode)
        assertEquals(
            "prioridades aplicadas",
            MuscleSymbols.orderBagOf(setOf(MuscleSymbol.CHEST, MuscleSymbol.BACK)),
            saved.planOrderPriorities,
        )
        assertEquals("marca de sentadilla hacia las cargas", 120.0, saved.powerliftingProfile?.squat1RM ?: Double.NaN, 0.001)
        assertTrue("cada sesión con su lugar", sessionsOf(saved).all { it.placeId == TrainingPlace.GYM.name })
        val settings = checkNotNull(room { db.settingsDao().get() }) { "ajustes no guardados" }.toSettings()
        assertEquals("Fuerza y masa muscular → entusiasta", AthleteType.ENTHUSIAST, settings.athleteType)
        val gym = setOf(TrainingPlace.GYM)
        assertEquals(
            "el material declarado",
            EquipmentSymbols.availabilityOf(EquipmentSymbols.seedFor(gym), gym),
            settings.equipmentAvailability,
        )
    }

    // ─── Plan de autor: la aproximación viaja con lo activado ──────────────────────────────────

    /**
     * Entreno v2 (D3): un programa de AUTOR (PHUL, que sale de su receta por `PlanMaterializer`) se activa IGUAL que se previsualizó
     * también en la aproximación y la movilidad: el materializador las pone una sola vez, la semana armada y la activación no las
     * pierden ni las duplican, la sesión principal abre con movilidad y con la rampa de su básico pesado, y aplicar el planificador
     * otra vez sobre lo activado no cambia nada (no falta nada y no hay nada repetido).
     */
    @Test
    fun anAuthoredProgramIsActivatedAsPreviewedWithTheMandatoryApproachAndMobility() = runTest(dispatcher.scheduler, timeout = 10.minutes) {
        val vm = newVm()
        vm.initialize(SetupWizardMode.TRAINING_ONLY)
        await(vm, "wizard cargado") { !it.isLoading && it.currentStep == SetupStepId.NAME }
        val answers = Answers(
            profile = TrainingGoalProfile.POWERBUILDING,
            places = setOf(TrainingPlace.GYM),
            days = setOf(1, 2, 4, 5),
            freshDay = 1,
            minutes = 100,
            marks = mapOf(
                LiftMark.SQUAT to 140.0,
                LiftMark.BENCH to 100.0,
                LiftMark.DEADLIFT to 170.0,
                LiftMark.OVERHEAD_PRESS to 60.0,
            ),
        )
        walkUntil(vm, SetupStepId.PLAN, answers)
        val swept = await(vm, "planes de powerbuilding") { idle(it) && it.planSweep == SetupPlanSweep.READY }
        val authoredId = AuthoredPhulPhatRecipes.PHUL_ORIGINAL_ID
        assertTrue(
            "PHUL original es candidato de Powerbuilding con 4 días y gimnasio: ${swept.availablePlanCandidates.map { it.id }}",
            swept.availablePlanCandidates.any { it.id == authoredId },
        )

        confirm(vm, SetupStepId.PLAN, SetupStepId.WEEK_LAYOUT) { vm.selectPlan(authoredId) }
        val prepared = await(vm, "programa de autor previsualizado") { idle(it) && it.programPreview != null && it.weekLayout != null }
        assertEquals(authoredId, prepared.draft.selectedCatalogId)
        confirm(vm, SetupStepId.WEEK_LAYOUT, SetupStepId.MILESTONE_TRAINING)
        confirm(vm, SetupStepId.MILESTONE_TRAINING, SetupStepId.REVIEW_ACTIVATE)
        await(vm, "revisión en reposo") { idle(it) && it.programPreview != null }
        val shown = checkNotNull(vm.state.value.programPreview)

        assertNotNull("alta sin errores: ${vm.state.value.errors}", vm.commit())
        assertEquals(WizChatMachineState.Committed, vm.state.value.machineState)

        val commitId = vm.state.value.draft.commitId
        val saved = checkNotNull(room { db.programDao().getById(commitId) }) { "programa no guardado" }.toProgram()
        assertEquals("el programa activado es el previsualizado", weekShape(shown), weekShape(saved))
        assertEquals("la aproximación y la movilidad activadas son las previsualizadas", approachShape(shown), approachShape(saved))
        ProgramExecutionContract.requireExecutable(saved)
        assertMainSessionOpensApproached("perfil de autor (PHUL)", saved, mainDay = 1)
        sessionsOf(saved).forEach { session ->
            assertEquals(
                "«${session.name}»: aplicar el planificador otra vez no cambia la sesión activada",
                session,
                ApproachPlanner.apply(session, ApproachOptions(level = ApproachLevel.INTERMEDIATE), approachInfoOf),
            )
        }
    }

    // ─── Disciplinas ──────────────────────────────────────────────────────────────────────────

    @Test
    fun aDisciplineWithAuthorsPutsTheTailoredProgramFirstAndTheAuthorsBehind() = runTest(dispatcher.scheduler, timeout = 10.minutes) {
        val vm = newVm()
        vm.initialize(SetupWizardMode.TRAINING_ONLY)
        await(vm, "wizard cargado") { !it.isLoading }
        vm.update { it.withInputs(TrainingGoalProfile.POWERLIFTING, setOf(TrainingPlace.GYM), days = setOf(1, 2, 4, 5), minutes = 90) }
        val swept = await(vm, "programas de powerlifting") { idle(it) && it.planSweep == SetupPlanSweep.READY }
        val ids = swept.availablePlanCandidates.map { it.id }
        assertEquals("el «a medida» al frente", GeneratedPlans.entryIdFor(TrainingGoalProfile.POWERLIFTING), ids.first())
        assertTrue("y detrás los planes propios y de autor: $ids", ids.size > 1)
        assertTrue("el propio de fuerza sigue en la lista: $ids", "native:strength-foundation-v2" in ids)
        assertEquals(ids, swept.planReveals.map { it.planId })
        assertEquals(SetupPlanReveals.KICKER_TAILORED, swept.planReveals.first().kicker)
        assertTrue("los demás no son «a medida»", swept.planReveals.drop(1).none { it.generated })
    }

    @Test
    fun aDisciplineWithoutAuthorsOffersOneInitialVersion() = runTest(dispatcher.scheduler, timeout = 10.minutes) {
        val vm = newVm()
        vm.initialize(SetupWizardMode.TRAINING_ONLY)
        await(vm, "wizard cargado") { !it.isLoading }
        vm.update {
            it.withInputs(
                TrainingGoalProfile.CALISTHENICS,
                setOf(TrainingPlace.PUBLIC),
                days = setOf(1, 3, 5),
                minutes = 45,
            ).withCapability(CapabilitySkill.PULL_UP, CapabilityLevel.SOME)
        }
        val swept = await(vm, "programa de calistenia") { idle(it) && it.planSweep == SetupPlanSweep.READY }
        assertEquals(listOf(GeneratedPlans.entryIdFor(TrainingGoalProfile.CALISTHENICS)), swept.availablePlanCandidates.map { it.id })
        val reveal = swept.planReveals.single()
        assertTrue(reveal.isInitialVersion)
        assertEquals(SetupPlanReveals.KICKER_INITIAL, reveal.kicker)
        assertTrue("la nota honesta viaja al detalle: ${reveal.notes}", reveal.notes.isNotEmpty())
    }

    // ─── Cambiar respuestas, otra versión y aplazar ─────────────────────────────────────────────

    @Test
    fun changingDaysOrMaterialSweepsAgainAndLeavesTheChosenProgramPendingReview() = runTest(dispatcher.scheduler, timeout = 10.minutes) {
        val vm = newVm()
        vm.initialize(SetupWizardMode.TRAINING_ONLY)
        await(vm, "wizard cargado") { !it.isLoading }
        val profile = TrainingGoalProfile.FUNCTIONAL_HEALTH
        vm.update { it.withInputs(profile, setOf(TrainingPlace.HOME), days = setOf(1, 3, 5), minutes = 45) }
        await(vm, "programa funcional") { idle(it) && it.planSweep == SetupPlanSweep.READY }
        val id = GeneratedPlans.entryIdFor(profile)
        vm.selectPlan(id)
        await(vm, "programa elegido y preparado") { idle(it) && it.programPreview != null }
        // PLAN confirmado: a partir de aquí un cambio de respuestas lo deja por revisar.
        vm.update {
            it.copy(
                stepProgress = it.stepProgress
                    .recordAnswer(SetupStepId.PLAN, SetupAnswerProvenance.USER_DECLARED, SetupValueState.DECLARED)
                    .reviewDone(SetupStepId.PLAN),
            )
        }
        await(vm, "PLAN confirmado") { idle(it) && SetupStepId.PLAN in it.draft.stepProgress.answers }

        val sweepsSeen = AtomicBoolean(false)
        vm.toggleWeekday(6)
        await(vm, "barrido nuevo con 4 días") {
            if (it.isCandidateLoading || it.planSweep == SetupPlanSweep.LOADING) sweepsSeen.set(true)
            idle(it) && it.draft.selectedWeekdays == setOf(1, 3, 5, 6) && it.planSweep == SetupPlanSweep.READY &&
                it.programPreview?.resolvedSchedulePlan()?.trainingDays == setOf(1, 3, 5, 6)
        }
        val afterDays = vm.state.value
        assertEquals("sin cambiar de plan en silencio", id, afterDays.draft.selectedCatalogId)
        assertTrue("PLAN queda por revisar", SetupStepId.PLAN in afterDays.draft.stepProgress.pendingReview)

        vm.toggleEquipmentSymbol(EquipmentSymbolId.DUMBBELLS)
        await(vm, "barrido nuevo con mancuernas") {
            idle(it) && EquipmentSymbolId.DUMBBELLS in it.draft.selectedEquipmentSymbols() &&
                it.planSweep == SetupPlanSweep.READY && it.programPreview != null
        }
        assertEquals(id, vm.state.value.draft.selectedCatalogId)
        assertTrue(SetupStepId.PLAN in vm.state.value.draft.stepProgress.pendingReview)
    }

    @Test
    fun anotherVersionRegeneratesTheTailoredProgramAndKeepsItChosen() = runTest(dispatcher.scheduler, timeout = 10.minutes) {
        val vm = newVm()
        vm.initialize(SetupWizardMode.TRAINING_ONLY)
        await(vm, "wizard cargado") { !it.isLoading }
        val profile = TrainingGoalProfile.STRENGTH_MUSCLE
        vm.update { it.withInputs(profile, setOf(TrainingPlace.GYM), days = setOf(1, 3, 5), minutes = 60) }
        await(vm, "programa a medida") { idle(it) && it.planSweep == SetupPlanSweep.READY }
        val id = GeneratedPlans.entryIdFor(profile)
        vm.selectPlan(id)
        val first = await(vm, "primera versión") { idle(it) && it.programPreview != null }
        val firstExercises = sessionsOf(first.programPreview!!).flatMap { session -> session.allExercises().map { it.catalogConfigurationId } }

        vm.anotherPlanVersion()
        val second = await(vm, "otra versión") {
            idle(it) && it.draft.planVariantSeed == 1 && it.planSweep == SetupPlanSweep.READY && it.programPreview != null
        }
        assertEquals("sigue elegido", id, second.draft.selectedCatalogId)
        val secondExercises = sessionsOf(second.programPreview!!).flatMap { session -> session.allExercises().map { it.catalogConfigurationId } }
        assertNotEquals("otra elección entre ejercicios equivalentes", firstExercises, secondExercises)
        assertTrue("la semana armada empieza de cero", second.draft.weekLayoutOverrides.isEmpty() && second.draft.adaptedSplitId == null)
    }

    @Test
    fun deferringTheProgramSkipsTheWeekAndClearsThePreview() = runTest(dispatcher.scheduler, timeout = 10.minutes) {
        val vm = newVm()
        vm.initialize(SetupWizardMode.TRAINING_ONLY)
        await(vm, "wizard cargado") { !it.isLoading }
        vm.update { it.withInputs(TrainingGoalProfile.STRENGTH_CARDIO, setOf(TrainingPlace.GYM), days = setOf(2, 4, 6), minutes = 60) }
        await(vm, "programa híbrido") { idle(it) && it.planSweep == SetupPlanSweep.READY }
        vm.selectPlan(GeneratedPlans.entryIdFor(TrainingGoalProfile.STRENGTH_CARDIO))
        await(vm, "preparado") { idle(it) && it.programPreview != null }

        vm.deferProgramUntilLater()
        val deferred = await(vm, "aplazado") { idle(it) && it.draft.programRoute == SetupProgramRoute.LATER }
        assertNull(deferred.draft.selectedCatalogId)
        assertNull("sin programa que previsualizar", deferred.programPreview)
        assertNull(deferred.weekLayout)
        assertFalse(
            "sin programa no hay semana que armar",
            SetupStepId.WEEK_LAYOUT in SetupStepGraph.stepIds(deferred.draft.stepContext()),
        )
        assertTrue("PLAN se puede confirmar", planSelectionGate(deferred, SetupStepId.PLAN).isEmpty())
    }

    // ─── Fallo del motor ──────────────────────────────────────────────────────────────────────

    @Test
    fun aFailingEngineShowsTheCopyMessageAndRetryRecovers() = runTest(dispatcher.scheduler, timeout = 10.minutes) {
        val failing = AtomicBoolean(true)
        val vm = newVm(
            materializer = SetupWizardMaterializer { draft ->
                if (failing.get()) {
                    throw PlanMaterializationException(
                        PlanEvaluationStage.MATERIALIZATION,
                        PlanRejectionReason.INTERNAL_MATERIALIZATION,
                        "fallo simulado del motor",
                    )
                }
                SetupPreview(cannedProgram(draft.commitId), null)
            },
        )
        vm.initialize(SetupWizardMode.TRAINING_ONLY)
        await(vm, "wizard cargado") { !it.isLoading }
        vm.update { it.withInputs(TrainingGoalProfile.STRENGTH_MUSCLE, setOf(TrainingPlace.GYM), days = setOf(1, 3, 5), minutes = 60) }
        val failed = await(vm, "barrido fallido") { idle(it) && it.planSweep == SetupPlanSweep.FAILED }
        assertEquals(PLAN_PREPARE_FAILED_MESSAGE, failed.errors["candidates"])
        assertTrue(failed.planReveals.isEmpty())
        assertEquals(SetupRetryOperation.CANDIDATES, vm.retryOperationForError("candidates"))

        failing.set(false)
        vm.retryFailedOperation(SetupRetryOperation.CANDIDATES)
        val recovered = await(vm, "barrido tras reintentar") { idle(it) && it.planSweep == SetupPlanSweep.READY }
        assertEquals(listOf(GeneratedPlans.entryIdFor(TrainingGoalProfile.STRENGTH_MUSCLE)), recovered.availablePlanCandidates.map { it.id })
        assertNull(recovered.errors["candidates"])
    }

    // ─── Una sesión movida a un día de otro lugar ───────────────────────────────────────────────────

    /** Gimnasio y casa: lunes y viernes en el gimnasio y el miércoles en casa (solo cuerpo: la casa no trae más). */
    private val gymAndHomeAnswers = Answers(
        profile = TrainingGoalProfile.STRENGTH_MUSCLE,
        places = setOf(TrainingPlace.GYM, TrainingPlace.HOME),
        days = setOf(1, 3, 5),
        freshDay = 1,
        minutes = 60,
        dayPlaces = mapOf(3 to TrainingPlace.HOME),
    )

    private fun TestScope.reachWeekLayout(vm: SetupWizardViewModel, answers: Answers): SetupWizardState {
        vm.initialize(SetupWizardMode.TRAINING_ONLY)
        await(vm, "wizard cargado") { !it.isLoading && it.currentStep == SetupStepId.NAME }
        walkUntil(vm, SetupStepId.PLAN, answers)
        await(vm, "programa a medida listo") { idle(it) && it.planSweep == SetupPlanSweep.READY }
        val generatedId = GeneratedPlans.entryIdFor(answers.profile)
        confirm(vm, SetupStepId.PLAN, SetupStepId.WEEK_LAYOUT) { vm.selectPlan(generatedId) }
        return await(vm, "semana armada") { idle(it) && it.programPreview != null && it.weekLayout != null }
    }

    private fun placeOfSession(state: SetupWizardState, sessionId: String): TrainingPlace? =
        state.weekLayout?.sessions?.firstOrNull { it.id == sessionId }?.place

    @Test
    fun aSessionMovedToADayOfAnotherPlaceTakesThatPlaceWhenItsMaterialFitsAndIsActivatedAsPreviewed() =
        runTest(dispatcher.scheduler, timeout = 10.minutes) {
            val vm = newVm()
            val prepared = reachWeekLayout(vm, gymAndHomeAnswers)
            val layout = checkNotNull(prepared.weekLayout)
            val homeSession = layout.assignment.getValue(3)
            assertEquals("el miércoles se arma con el material de casa", TrainingPlace.HOME, placeOfSession(prepared, homeSession))
            assertTrue("sin nada movido no hay aviso", layout.placeConflicts.isEmpty())

            // La sesión de casa (solo cuerpo) pasa al martes, que se entrena en el gimnasio: cabe, así que cambia de lugar.
            vm.moveSession(homeSession, 2)
            val moved = await(vm, "sesión movida y lugar puesto al día") {
                idle(it) && it.weekLayout?.assignment?.get(2) == homeSession && placeOfSession(it, homeSession) == TrainingPlace.GYM
            }
            assertTrue("compatible: sin ruido", moved.weekLayout!!.placeConflicts.isEmpty())
            assertEquals(TrainingPlace.GYM.name, sessionsOf(checkNotNull(moved.programPreview)).single { it.id == homeSession }.placeId)

            confirm(vm, SetupStepId.WEEK_LAYOUT, SetupStepId.MILESTONE_TRAINING)
            confirm(vm, SetupStepId.MILESTONE_TRAINING, SetupStepId.REVIEW_ACTIVATE)
            await(vm, "revisión en reposo") { idle(it) && it.programPreview != null }
            val shown = checkNotNull(vm.state.value.programPreview)
            assertTrue("la revisión no avisa de nada", vm.state.value.weekLayout!!.placeConflicts.isEmpty())

            assertNotNull("alta sin errores: ${vm.state.value.errors}", vm.commit())
            val saved = checkNotNull(room { db.programDao().getById(vm.state.value.draft.commitId) }) { "programa no guardado" }.toProgram()
            assertEquals("el programa activado es el previsualizado", weekShape(shown), weekShape(saved))
            assertEquals(TrainingPlace.GYM.name, sessionsOf(saved).single { it.id == homeSession }.placeId)
            assertEquals(2, sessionsOf(saved).single { it.id == homeSession }.dayOfWeek)
        }

    @Test
    fun aSessionThatDoesNotFitTheNewDaysPlaceStaysWarnedInThePreviewTheReviewAndTheActivationUntilItIsUndone() =
        runTest(dispatcher.scheduler, timeout = 10.minutes) {
            val vm = newVm()
            val prepared = reachWeekLayout(vm, gymAndHomeAnswers)
            val original = checkNotNull(prepared.programPreview)
            val mondaySession = checkNotNull(prepared.weekLayout).assignment.getValue(1)
            assertEquals(TrainingPlace.GYM, placeOfSession(prepared, mondaySession))

            // El lunes (gimnasio) cae en el miércoles (casa) e intercambia con la sesión de casa.
            vm.moveSession(mondaySession, 3)
            val warned = await(vm, "aviso de la sesión movida") { idle(it) && it.weekLayout?.placeConflicts?.isNotEmpty() == true }
            val conflict = warned.weekLayout!!.placeConflicts.single()
            assertEquals(mondaySession, conflict.sessionId)
            assertEquals(3, conflict.day)
            assertEquals(TrainingPlace.GYM, conflict.sessionPlace)
            assertEquals(TrainingPlace.HOME, conflict.dayPlace)
            assertEquals("conserva su lugar", TrainingPlace.GYM, placeOfSession(warned, mondaySession))
            assertTrue(warned.weekLayout!!.canReset)

            // «Restablecer» deshace el aviso y devuelve la semana preparada.
            vm.resetWeekLayout()
            val clean = await(vm, "semana restablecida") { idle(it) && it.weekLayout?.placeConflicts?.isEmpty() == true && !it.weekLayout!!.canReset }
            assertEquals("la semana vuelve a ser la del programa", weekShape(original), weekShape(checkNotNull(clean.programPreview)))

            // Se vuelve a mover y esta vez la persona sigue: la revisión y la activación llevan el mismo aviso.
            vm.moveSession(mondaySession, 3)
            await(vm, "aviso otra vez") { idle(it) && it.weekLayout?.placeConflicts?.isNotEmpty() == true }
            confirm(vm, SetupStepId.WEEK_LAYOUT, SetupStepId.MILESTONE_TRAINING)
            confirm(vm, SetupStepId.MILESTONE_TRAINING, SetupStepId.REVIEW_ACTIVATE)
            val review = await(vm, "revisión en reposo") { idle(it) && it.programPreview != null && it.weekLayout != null }
            assertEquals("el aviso persiste en la revisión", listOf(conflict), review.weekLayout!!.placeConflicts)
            val shown = checkNotNull(review.programPreview)

            assertNotNull("avisada, la persona sí puede activar: ${vm.state.value.errors}", vm.commit())
            val saved = checkNotNull(room { db.programDao().getById(vm.state.value.draft.commitId) }) { "programa no guardado" }.toProgram()
            assertEquals("el programa activado es el previsualizado", weekShape(shown), weekShape(saved))
            val savedMonday = sessionsOf(saved).single { it.id == mondaySession }
            assertEquals(3, savedMonday.dayOfWeek)
            assertEquals("conserva el lugar con cuyo material se armó", TrainingPlace.GYM.name, savedMonday.placeId)
        }

    // ─── «Configurar este plan» desde la biblioteca: sin «selección caída» espuria ───────────────────

    @Test
    fun aLibraryPlanThatAGeneralProfileNoLongerOffersIsExplainedWithoutAlarmAndTheTailoredProgramIsOneTapAway() =
        runTest(dispatcher.scheduler, timeout = 10.minutes) {
            val vm = newVm()
            vm.initialize(SetupWizardMode.TRAINING_ONLY, preselectedPlanId = ATHLETE_OWN)
            val loaded = await(vm, "wizard cargado con la intención de la biblioteca") { !it.isLoading }
            // Atleta completo prefija «Fuerza y cardio», un perfil general que solo ofrece su programa a medida.
            assertEquals(TrainingGoalProfile.STRENGTH_CARDIO, loaded.draft.goalProfile)
            assertEquals(ATHLETE_OWN, loaded.draft.selectedCatalogId)

            vm.update { it.withInputs(TrainingGoalProfile.STRENGTH_CARDIO, setOf(TrainingPlace.GYM), days = setOf(1, 3, 5), minutes = 60) }
            val state = await(vm, "barrido listo y plan de la biblioteca sustituido") {
                idle(it) && it.planSweep == SetupPlanSweep.READY && it.droppedSelection != null
            }
            val tailoredId = GeneratedPlans.entryIdFor(TrainingGoalProfile.STRENGTH_CARDIO)
            assertEquals(listOf(tailoredId), state.availablePlanCandidates.map { it.id })
            val dropped = checkNotNull(state.droppedSelection)
            assertEquals(ATHLETE_OWN, dropped.planId)
            assertEquals(tailoredId, dropped.tailoredId)

            // El aviso dice qué pasó, no es una alarma y deja el programa a medida a un toque.
            val notice = droppedSelectionNotice(dropped, state.draft)
            assertEquals("Este programa de la biblioteca ahora se arma a medida en el asistente.", notice.text)
            assertTrue("sin alarma", notice.informational)
            assertEquals(NoticeButton(CHOOSE_TAILORED_LABEL, NoticeEffect.Choose(tailoredId)), notice.primary)
            assertNull("sin segunda salida: no hace falta cambiar nada", notice.secondary)

            // El aviso persiste mientras la persona responde lo demás (cada respuesta vuelve a barrer).
            vm.update { it.withMuscles(setOf(MuscleSymbol.CHEST)) }
            val again = await(vm, "otro barrido") { idle(it) && it.planSweep == SetupPlanSweep.READY && it.droppedSelection != null }
            assertEquals(tailoredId, again.droppedSelection?.tailoredId)

            performNoticeEffect(notice.primary!!.effect, vm)
            val chosen = await(vm, "programa a medida elegido y preparado") {
                idle(it) && it.draft.selectedCatalogId == tailoredId && it.programPreview != null
            }
            assertNull("elegir el programa a medida cierra el aviso", chosen.droppedSelection)
            assertEquals(PersonalizedPlanCatalog.find(tailoredId)?.displayName, chosen.programPreview?.name)
        }

    @Test
    fun theOwnPlansOfTheSpecificProfilesAreOfferedFromThemAndNeverFallForNothing() =
        runTest(dispatcher.scheduler, timeout = 15.minutes) {
            // Una prueba por perfil: el plan propio que prefija cada disciplina se ofrece desde ella, se queda elegido y no hay aviso.
            listOf(
                STRENGTH_OWN to TrainingGoalProfile.POWERLIFTING,
                MUSCLE_OWN to TrainingGoalProfile.BODYBUILDING,
                POWERBUILDING_OWN to TrainingGoalProfile.POWERBUILDING,
            ).forEach { (planId, profile) ->
                val vm = newVm()
                vm.initialize(SetupWizardMode.TRAINING_ONLY, draftId = "biblioteca-${profile.name}", preselectedPlanId = planId)
                val loaded = await(vm, "wizard cargado ($profile)") { !it.isLoading }
                assertEquals("$planId prefija $profile", profile, loaded.draft.goalProfile)
                vm.update { it.withInputs(profile, setOf(TrainingPlace.GYM), days = setOf(1, 2, 4, 5), minutes = 90) }
                val ready = await(vm, "barrido y programa preparado ($profile)") {
                    idle(it) && it.planSweep == SetupPlanSweep.READY && it.programPreview != null
                }
                assertTrue("$planId se ofrece desde $profile: ${ready.availablePlanCandidates.map { c -> c.id }}", ready.availablePlanCandidates.any { it.id == planId })
                assertNull("$planId: ni aviso ni selección caída", ready.droppedSelection)
                assertEquals("$planId sigue elegido", planId, ready.draft.selectedCatalogId)
                assertEquals("el «a medida» va delante, el propio detrás", GeneratedPlans.entryIdFor(profile), ready.availablePlanCandidates.first().id)
            }
        }

    // ═════════════════════════════════════════════════════════════════════════════════════════
    // Arnés
    // ═════════════════════════════════════════════════════════════════════════════════════════

    /** Lo que la persona responde en el recorrido (solo lo que cambia entre pruebas). */
    private data class Answers(
        val profile: TrainingGoalProfile,
        val places: Set<TrainingPlace>,
        val days: Set<Int>,
        val freshDay: Int,
        val minutes: Int,
        val muscles: Set<MuscleSymbol> = emptySet(),
        val marks: Map<LiftMark, Double> = emptyMap(),
        /** El lugar de cada día de entreno (solo con dos o más lugares); sin entrada, el primero de la lista. */
        val dayPlaces: Map<Int, TrainingPlace> = emptyMap(),
    )

    /** Recorre la ruta REAL del alta (con sus ramas) respondiendo cada paso hasta llegar a [target]. */
    private fun TestScope.walkUntil(vm: SetupWizardViewModel, target: SetupStepId, answers: Answers) {
        var guard = 0
        while (vm.state.value.currentStep != target) {
            check(guard++ < 60) { "el recorrido no llega a $target (cursor ${vm.state.value.currentStep})" }
            val step = vm.state.value.currentStep
            answer(vm, step, answers)
            await(vm, "reposo tras responder $step") { rest(it) }
            val next = checkNotNull(SetupStepGraph.next(step, vm.state.value.draft.stepContext())) { "ruta sin paso tras $step" }
            confirm(vm, step, next)
        }
    }

    private fun answer(vm: SetupWizardViewModel, step: SetupStepId, answers: Answers) {
        when (step) {
            SetupStepId.NAME -> vm.setStepText(SetupStepId.NAME, "Ana")
            SetupStepId.AGE -> vm.setStepNumber(SetupStepId.AGE, 30.0)
            SetupStepId.HEIGHT -> vm.setStepNumber(SetupStepId.HEIGHT, 175.0)
            SetupStepId.WEIGHT -> vm.setStepNumber(SetupStepId.WEIGHT, 72.0)
            SetupStepId.EQUATION_SEX -> vm.setStepChoice(SetupStepId.EQUATION_SEX, "male")
            SetupStepId.BODY_FAT -> vm.updateStep(SetupStepId.BODY_FAT) { it.withBodyFatRulerValue(18) }
            SetupStepId.EXPERIENCE -> vm.setStepChoice(SetupStepId.EXPERIENCE, "intermediate")
            SetupStepId.EQUIPMENT -> vm.updateStep(SetupStepId.EQUIPMENT) { it.withPlaces(answers.places) }
            SetupStepId.GOAL -> vm.setGoalProfile(answers.profile)
            SetupStepId.FRESH_DAY -> vm.setFreshDay(answers.freshDay)
            SetupStepId.WEEKDAYS -> vm.updateStep(SetupStepId.WEEKDAYS) { draft ->
                answers.dayPlaces.entries.fold(draft.withWeekdays(answers.days)) { current, (day, place) -> current.withDayPlace(day, place) }
            }
            SetupStepId.SESSION_TIME -> vm.setSessionMinutes(answers.minutes)
            SetupStepId.CARDIO_TYPE -> vm.setStepChoice(SetupStepId.CARDIO_TYPE, "WALK")
            SetupStepId.CARDIO_TIME -> vm.setStepChoice(SetupStepId.CARDIO_TIME, "20")
            SetupStepId.VOLUME_TECHNIQUE, SetupStepId.VOLUME_CONSISTENCY, SetupStepId.VOLUME_STRENGTH,
            SetupStepId.VOLUME_MOBILITY -> vm.setStepChoice(step, "2")
            SetupStepId.CAPABILITIES ->
                vm.state.value.draft.capabilitySkills().forEach { skill -> vm.setCapability(skill, CapabilityLevel.SOME) }
            SetupStepId.PRIORITIES -> vm.updateStep(SetupStepId.PRIORITIES) { it.withMuscles(answers.muscles) }
            SetupStepId.TRAINING_MAX -> answers.marks.forEach { (lift, kg) -> vm.setLiftMark(lift, kg) }
            else -> Unit
        }
    }

    /** Entradas completas de candidato escritas de una vez (sin recorrer la ruta), con los reductores reales. */
    private fun SetupWizardDraft.withInputs(
        profile: TrainingGoalProfile,
        places: Set<TrainingPlace>,
        days: Set<Int>,
        minutes: Int,
    ): SetupWizardDraft {
        val goal = GoalProfileMapping.setupGoalOf(profile)
        return copy(
            includeTraining = true,
            programRoute = SetupProgramRoute.CUSTOMIZABLE,
            trainingPath = SetupTrainingPath.PERSONALIZE,
            experience = SetupExperience.INTERMEDIATE,
            cardioType = if (goal.requiresCardio) com.example.kpkn.data.models.CardioType.WALK else null,
            cardioMinutes = if (goal.requiresCardio) 20 else null,
        )
            .withPlaces(places)
            .withGoalProfile(profile)
            .withFreshestDay(days.min())
            .withWeekdays(days)
            .withSessionMinutes(minutes)
    }

    private fun sessionsOf(program: Program): List<Session> =
        program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }.flatMap { it.sessions }

    /** Lo que sabe el planificador de cada ejercicio (articulaciones, si es compuesto y si lleva carga), del catálogo real. */
    private val approachInfoOf: (Exercise) -> com.example.kpkn.domain.training.approach.ApproachExerciseInfo? by lazy {
        ApproachInfoProvider.fromMetadata(
            CatalogCompositionMetadataProvider.fromCatalog(runBlocking { CatalogV2ProcessCache.getOrLoad(app) }.catalog),
        )
    }

    /** La aproximación y la movilidad de cada ejercicio del programa (lo que [weekShape] no mira). */
    private fun approachShape(program: Program): List<Triple<String, Any, Any>> =
        sessionsOf(program).flatMap { it.allExercises() }.map { Triple(it.id, it.warmupSets, it.mobilitySeries) }

    /**
     * La regla del usuario sobre la sesión principal (el día con más energía): su primer ejercicio de fuerza lleva SIEMPRE
     * movilidad previa y, si es un básico con carga (el primer ejercicio pesado), series de aproximación.
     */
    private fun assertMainSessionOpensApproached(label: String, program: Program, mainDay: Int) {
        val sessions = sessionsOf(program)
        val main = sessions.singleOrNull { it.isMainSession }
            ?: sessions.first { it.dayOfWeek == mainDay || mainDay in it.assignedDays }
        val first = main.allExercises().first { it.cardioDetails == null && it.sets.isNotEmpty() }
        assertTrue("$label: '${first.name}' abre «${main.name}» y debe llevar movilidad previa", first.mobilitySeries.isNotEmpty())
        val info = approachInfoOf(first)
        assertNotNull("$label: '${first.name}' no está en el catálogo", info)
        if (info!!.canBeHeavy) {
            assertTrue(
                "$label: '${first.name}' es un básico con carga y debe llevar series de aproximación",
                first.warmupSets.isNotEmpty(),
            )
        }
    }

    /** La forma de la semana: cada sesión con su día, su lugar y sus ejercicios (configuración y series). */
    private fun weekShape(program: Program): List<Any?> = sessionsOf(program).map { session ->
        listOf(
            session.id, session.name, session.dayOfWeek, session.isMainSession, session.placeId,
            session.allExercises().map { exercise -> exercise.catalogConfigurationId to exercise.sets.size },
        )
    }

    private fun cannedProgram(id: String): Program {
        val exercise = com.example.kpkn.data.models.Exercise(
            id = "$id-ex",
            name = "Press de banca",
            sets = (1..3).map { com.example.kpkn.data.models.ExerciseSet(id = "$id-set-$it", targetReps = 8, weight = 20.0) },
            restTime = 90,
        )
        val sessions = listOf(1, 3, 5).map { day ->
            Session(id = "$id-s$day", name = "Día $day", exercises = listOf(exercise.copy(id = "$id-ex$day")), dayOfWeek = day, assignedDays = listOf(day))
        }
        val week = com.example.kpkn.data.models.ProgramWeek("$id-week", "Semana", sessions = sessions)
        val meso = com.example.kpkn.data.models.Mesocycle("$id-meso", "Meso", weeks = listOf(week))
        val block = com.example.kpkn.data.models.Block("$id-block", "Bloque", mesocycles = listOf(meso))
        return Program(
            id = id,
            name = "Plan preparado",
            startDay = 1,
            macrocycles = listOf(com.example.kpkn.data.models.Macrocycle("$id-macro", "Macro", blocks = listOf(block))),
            schedulePlan = com.example.kpkn.data.models.ProgramSchedulePlan(weekStartDay = 1, trainingDays = setOf(1, 3, 5)),
        )
    }

    private fun rest(state: SetupWizardState): Boolean =
        !state.isLoading && !state.isCommitting && !state.isSubmittingAnswer && !state.isSavingAndExiting &&
            state.machineState != WizChatMachineState.PersistingAnswer &&
            state.machineState != WizChatMachineState.Committing

    private fun idle(state: SetupWizardState): Boolean =
        rest(state) && state.machineState != WizChatMachineState.PreparingPreview &&
            !state.isPreviewLoading && !state.isCandidateLoading

    private fun TestScope.confirm(vm: SetupWizardViewModel, step: SetupStepId, expectedNext: SetupStepId, write: () -> Unit = {}) {
        write()
        await(vm, "reposo antes de confirmar $step") { idle(it) }
        val result = vm.submitCurrentStep(step, expectedRevision = vm.state.value.draft.revision)
        assertEquals(
            "Continuar sobre $step (errores=${vm.state.value.errors}, cursor=${vm.state.value.currentStep})",
            SetupSubmitOutcome.ACCEPTED,
            result.outcome,
        )
        await(vm, "cursor en $expectedNext tras $step") { it.currentStep == expectedNext }
    }

    private fun TestScope.await(
        vm: SetupWizardViewModel,
        what: String,
        timeoutMs: Long = 60_000,
        condition: (SetupWizardState) -> Boolean,
    ): SetupWizardState {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            advanceUntilIdle()
            val state = vm.state.value
            if (condition(state)) return state
            if (System.currentTimeMillis() > deadline) {
                fail(
                    "Timeout esperando $what en ${state.currentStep}: máquina=${state.machineState} barrido=${state.planSweep} " +
                        "errores=${state.errors} preview=${state.previewError} cargando=${state.isPreviewLoading}/${state.isCandidateLoading} " +
                        "candidatos=${state.availablePlanCandidates.map { it.id }}",
                )
            }
            Thread.sleep(10)
        }
    }

    private fun newVm(materializer: SetupWizardMaterializer? = null): SetupWizardViewModel =
        SetupWizardViewModel(
            app,
            SavedStateHandle(),
            persistence = RoomPersistence(db),
            environment = FixedSettingsEnvironment(Settings()),
            commits = RoomCommits(db),
            materializeOverride = materializer,
        ).also { viewModelStore.put("entreno-plan-${vmCounter++}", it) }

    private fun <T> room(block: suspend () -> T): T = runBlocking { block() }

    private companion object {
        val STRENGTH_OWN: String = com.example.kpkn.data.protocols.definitions.NativeProfileKind.STRENGTH.entryId
        val MUSCLE_OWN: String = com.example.kpkn.data.protocols.definitions.NativeProfileKind.MUSCLE.entryId
        val POWERBUILDING_OWN: String = com.example.kpkn.data.protocols.definitions.NativeProfileKind.POWERBUILDING.entryId
        val ATHLETE_OWN: String = com.example.kpkn.data.protocols.definitions.NativeProfileKind.COMPLETE_ATHLETE.entryId
    }

    private class RoomPersistence(db: KpknDatabase) : SetupWizardPersistence {
        private val drafts = SetupDraftRepository(db)
        private val resolver = SetupDraftResolver(db)
        override suspend fun load(draftId: String): SetupDraft? = drafts.load(draftId)
        override suspend fun save(draftId: String, payloadJson: String, revision: Long, catalogRevision: String?): SetupDraft =
            drafts.save(draftId, payloadJson, revision, catalogRevision)
        override suspend fun discard(draftId: String) = drafts.discard(draftId)
        override suspend fun listRecoverable(): List<SetupDraftCandidate> = resolver.listRecoverable()
    }

    private class RoomCommits(db: KpknDatabase) : SetupWizardCommits {
        private val coordinator = SetupCommitCoordinator(db)
        override suspend fun commit(request: SetupCommitRequest): SetupCommitResult = coordinator.commit(request)
    }

    private class FixedSettingsEnvironment(override val settings: Settings) : SetupWizardEnvironment {
        override suspend fun awaitReady() = Unit
        override fun activeProgramId(): String? = null
        override fun activeNutritionPlanId(): String? = null
        override fun activeNutritionPlan(): NutritionPlan? = null
        override fun nutritionPlan(id: String): NutritionPlan? = null
        override fun hasInitialRecoveryEvidence(): Boolean = false
        override suspend fun refreshBodyProgress() = Unit
    }
}

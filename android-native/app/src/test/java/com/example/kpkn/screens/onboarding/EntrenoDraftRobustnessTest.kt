package com.example.kpkn.screens.onboarding

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.exercises.catalogv2.CatalogV2ProcessCache
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.resolvedSchedulePlan
import com.example.kpkn.data.onboarding.SetupCommitCoordinator
import com.example.kpkn.data.onboarding.SetupCommitRequest
import com.example.kpkn.data.onboarding.SetupCommitResult
import com.example.kpkn.data.onboarding.SetupDraft
import com.example.kpkn.data.onboarding.SetupDraftCandidate
import com.example.kpkn.data.onboarding.SetupDraftRepository
import com.example.kpkn.data.onboarding.SetupDraftResolver
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.onboarding.CapabilityLevel
import com.example.kpkn.domain.onboarding.CapabilitySkill
import com.example.kpkn.domain.onboarding.EquipmentSymbolId
import com.example.kpkn.domain.onboarding.GeneratedPlans
import com.example.kpkn.domain.onboarding.LiftMark
import com.example.kpkn.domain.onboarding.MuscleSymbol
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.domain.onboarding.WizChatMachineState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.minutes

/**
 * Entreno v2 (Q) · robustez del borrador del bloque Entreno: matar el proceso en CUALQUIER punto del bloque, o volver atrás desde
 * cualquier paso, no pierde nada de lo respondido.
 *
 *  - «Matar el proceso»: después de contestar cada paso (sin confirmarlo) se tira el ViewModel entero y se abre otro nuevo sobre la
 *    misma base de datos —lo único que sobrevive a un proceso muerto—; el cursor, la respuesta de ese paso y todas las anteriores,
 *    el programa elegido en PLAN y la semana armada en WEEK_LAYOUT tienen que ser los mismos, y el programa se vuelve a preparar.
 *  - «Atrás desde cada paso»: tras confirmar cada paso se vuelve atrás una vez; el cursor retrocede al paso anterior y los datos
 *    del borrador son idénticos; confirmar de nuevo devuelve al mismo sitio.
 *
 * Lo del giro de pantalla (el ViewModel sobrevive a la recreación de la actividad) se comprueba en el teléfono con el arnés.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class EntrenoDraftRobustnessTest {

    private val dispatcher = StandardTestDispatcher()
    private val viewModelStore = ViewModelStore()
    private lateinit var app: Application
    private var vmCounter = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        app = ApplicationProvider.getApplicationContext()
        ProgramRepository.closeInstance()
        NutritionRepository.closeInstance()
        KpknDatabase.closeInstance()
        val warm = runCatching { kotlinx.coroutines.runBlocking { CatalogV2ProcessCache.getOrLoad(app) } }
        assertTrue("el catálogo real no carga en Robolectric: ${warm.exceptionOrNull()?.message}", warm.isSuccess)
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        ProgramRepository.closeInstance()
        NutritionRepository.closeInstance()
        KpknDatabase.closeInstance()
        Dispatchers.resetMain()
    }

    /** Lo que el bloque Entreno escribe en el borrador (sin ids ni revisiones): la forma de comparar «antes» y «después». */
    private data class EntrenoData(
        val places: Set<TrainingPlace>,
        val material: Set<EquipmentSymbolId>,
        val profile: TrainingGoalProfile?,
        val freshDay: Int?,
        val weekStart: Int?,
        val weekdays: Set<Int>,
        val dayPlaces: Map<Int, TrainingPlace>,
        val minutes: Int?,
        val cardio: Pair<CardioType?, Int?>,
        val calibration: SetupVolumeAnswers,
        val experience: SetupExperience?,
        val capabilities: Map<CapabilitySkill, CapabilityLevel>,
        val orderBag: Map<String, Int>,
        val marks: Map<LiftMark, Double>,
        val marksUnit: String,
        val planId: String?,
        val version: Int,
        val moves: Map<String, Int>,
        val splitId: String?,
        val confirmed: Set<SetupStepId>,
    )

    private fun SetupWizardDraft.entrenoData() = EntrenoData(
        places = trainingPlaces,
        material = selectedEquipmentSymbols(),
        profile = goalProfile,
        freshDay = freshestDay,
        weekStart = weekStartDay,
        weekdays = selectedWeekdays,
        dayPlaces = dayPlaces,
        minutes = minutesPerSession,
        cardio = cardioType to cardioMinutes,
        calibration = volumeAnswers,
        experience = experience,
        capabilities = capabilities,
        orderBag = trainingOptions.orderPriorities,
        marks = liftMarks,
        marksUnit = marksUnit,
        planId = selectedCatalogId,
        version = planVariantSeed,
        moves = weekLayoutOverrides,
        splitId = adaptedSplitId,
        confirmed = stepProgress.answers.keys,
    )

    /** Las dos personas del recorrido: una con pesas (marcas, lugares y repartos) y otra de peso corporal (cardio y capacidades). */
    private enum class Person(
        val profile: TrainingGoalProfile,
        val places: Set<TrainingPlace>,
        val material: Set<EquipmentSymbolId>?,
        val days: Set<Int>,
        val dayPlaces: Map<Int, TrainingPlace>,
        val minutes: Int,
        val experience: String,
    ) {
        LIFTER(
            TrainingGoalProfile.POWERLIFTING, setOf(TrainingPlace.GYM, TrainingPlace.HOME), null,
            setOf(1, 3, 5), mapOf(3 to TrainingPlace.HOME), 90, "intermediate",
        ),
        BODYWEIGHT(
            TrainingGoalProfile.FUNCTIONAL_HEALTH, setOf(TrainingPlace.HOME), emptySet(),
            setOf(2, 3, 5, 6), emptyMap(), 45, "returning",
        ),
    }

    @Test
    fun killingTheProcessAtAnyStepOfTheBlockKeepsTheDraftTheAnswersTheChosenProgramAndTheWeek() = runTest(dispatcher.scheduler, timeout = 20.minutes) {
        Person.entries.forEach { person ->
            val db = KpknDatabase.createInMemory(app)
            try {
                val draftId = "q-robust-${person.name}"
                var vm = newVm(db, draftId)
                walkBasics(vm)
                var guard = 0
                val killedAt = mutableListOf<SetupStepId>()
                while (vm.state.value.currentStep != SetupStepId.MILESTONE_TRAINING) {
                    check(guard++ < 40) { "${person.name}: el recorrido no llega al hito (cursor ${vm.state.value.currentStep})" }
                    val step = vm.state.value.currentStep
                    answer(vm, step, person)
                    await(vm, "reposo tras responder $step") { rest(it) }
                    if (step == SetupStepId.PLAN) {
                        await(vm, "barrido de ${person.name}") { idle(it) && it.planSweep == SetupPlanSweep.READY }
                        vm.selectPlan(GeneratedPlans.entryIdFor(person.profile))
                        await(vm, "programa elegido y preparado") { idle(it) && it.programPreview != null }
                    }
                    if (step == SetupStepId.WEEK_LAYOUT) {
                        await(vm, "semana armada") { idle(it) && it.weekLayout != null }
                        val layout = checkNotNull(vm.state.value.weekLayout)
                        val movable = layout.assignment.entries.minBy { it.key }
                        val freeDay = (1..7).first { it !in layout.assignment.keys }
                        vm.moveSession(movable.value, freeDay)
                        await(vm, "sesión movida") { idle(it) && it.weekLayout?.assignment?.get(freeDay) == movable.value }
                    }
                    val before = vm.state.value
                    val dataBefore = before.draft.entrenoData()

                    // ── El proceso muere aquí: nada en memoria sobrevive, solo la base de datos. ──
                    viewModelStore.clear()
                    vm = newVm(db, draftId)
                    val reopened = await(vm, "borrador reabierto en $step") { idle(it) }
                    killedAt += step
                    assertEquals("${person.name} · $step: el cursor", step, reopened.currentStep)
                    assertEquals("${person.name} · $step: lo respondido", dataBefore, reopened.draft.entrenoData())
                    if (step == SetupStepId.PLAN || step == SetupStepId.WEEK_LAYOUT) {
                        val prepared = await(vm, "programa preparado de nuevo en $step") { idle(it) && it.programPreview != null }
                        assertEquals("${person.name} · $step: el programa elegido", GeneratedPlans.entryIdFor(person.profile), prepared.draft.selectedCatalogId)
                        assertEquals(
                            "${person.name} · $step: los días del programa son los elegidos (con la sesión movida)",
                            before.programPreview?.resolvedSchedulePlan()?.trainingDays,
                            prepared.programPreview?.resolvedSchedulePlan()?.trainingDays,
                        )
                    }
                    val next = checkNotNull(SetupStepGraph.next(step, vm.state.value.draft.stepContext())) { "ruta sin paso tras $step" }
                    confirm(vm, step, next)
                }
                assertTrue("${person.name}: se mató el proceso en ${killedAt.size} pasos del bloque: $killedAt", killedAt.size >= 12)
                assertTrue("${person.name}: PLAN y WEEK_LAYOUT entre los pasos", SetupStepId.PLAN in killedAt && SetupStepId.WEEK_LAYOUT in killedAt)
            } finally {
                viewModelStore.clear()
                db.close()
            }
        }
    }

    @Test
    fun goingBackFromEveryStepOfTheBlockKeepsEveryAnswerAndConfirmingComesBackToTheSamePlace() = runTest(dispatcher.scheduler, timeout = 20.minutes) {
        Person.entries.forEach { person ->
            val db = KpknDatabase.createInMemory(app)
            try {
                val vm = newVm(db, "q-back-${person.name}")
                walkBasics(vm)
                var guard = 0
                var wentBack = 0
                while (vm.state.value.currentStep != SetupStepId.MILESTONE_TRAINING) {
                    check(guard++ < 40) { "${person.name}: el recorrido no llega al hito (cursor ${vm.state.value.currentStep})" }
                    val step = vm.state.value.currentStep
                    answer(vm, step, person)
                    await(vm, "reposo tras responder $step") { rest(it) }
                    if (step == SetupStepId.PLAN) {
                        await(vm, "barrido") { idle(it) && it.planSweep == SetupPlanSweep.READY }
                        vm.selectPlan(GeneratedPlans.entryIdFor(person.profile))
                        await(vm, "programa elegido y preparado") { idle(it) && it.programPreview != null }
                    }
                    val dataBefore = vm.state.value.draft.entrenoData()
                    val previous = SetupStepGraph.previous(step, vm.state.value.draft.stepContext(), vm.state.value.draft.stepProgress.visited)
                    if (previous != null && step in ENTRENO_STEPS && previous in ENTRENO_STEPS) {
                        assertTrue("${person.name} · $step: se puede volver atrás", vm.canGoBack())
                        assertTrue("${person.name} · $step: atrás", vm.goBack())
                        val back = await(vm, "atrás desde $step") { it.currentStep == previous && rest(it) }
                        assertEquals("${person.name} · $step: lo respondido tras volver atrás", dataBefore, back.draft.entrenoData())
                        wentBack++
                        // Confirmar el paso al que se volvió devuelve al punto de partida, con todo igual.
                        confirm(vm, previous, step)
                        assertEquals("${person.name} · $step: lo respondido tras volver a avanzar", dataBefore, vm.state.value.draft.entrenoData())
                    }
                    val next = checkNotNull(SetupStepGraph.next(step, vm.state.value.draft.stepContext())) { "ruta sin paso tras $step" }
                    confirm(vm, step, next)
                }
                assertTrue("${person.name}: se volvió atrás desde $wentBack pasos del bloque", wentBack >= 10)
            } finally {
                viewModelStore.clear()
                db.close()
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════
    // Recorrido
    // ═════════════════════════════════════════════════════════════════════════════════════════

    private val ENTRENO_STEPS = setOf(
        SetupStepId.EXPERIENCE, SetupStepId.EQUIPMENT, SetupStepId.AVAILABILITY, SetupStepId.GOAL, SetupStepId.FRESH_DAY,
        SetupStepId.WEEKDAYS, SetupStepId.SESSION_TIME, SetupStepId.CARDIO_TYPE, SetupStepId.CARDIO_TIME,
        SetupStepId.VOLUME_TECHNIQUE, SetupStepId.VOLUME_CONSISTENCY, SetupStepId.VOLUME_STRENGTH, SetupStepId.VOLUME_MOBILITY,
        SetupStepId.CAPABILITIES, SetupStepId.PRIORITIES, SetupStepId.TRAINING_MAX, SetupStepId.PLAN, SetupStepId.WEEK_LAYOUT,
    )

    private fun TestScope.newVm(db: KpknDatabase, draftId: String): SetupWizardViewModel {
        val vm = SetupWizardViewModel(
            app,
            SavedStateHandle(),
            persistence = RoomPersistence(db),
            environment = FixedSettingsEnvironment(Settings()),
            commits = RoomCommits(db),
        ).also { viewModelStore.put("q-robust-${vmCounter++}", it) }
        vm.initialize(SetupWizardMode.TRAINING_ONLY, draftId = draftId)
        await(vm, "wizard cargado") { !it.isLoading }
        return vm
    }

    /** Los datos básicos hasta la primera pregunta del bloque Entreno. */
    private fun TestScope.walkBasics(vm: SetupWizardViewModel) {
        await(vm, "primera pregunta") { !it.isLoading && it.currentStep == SetupStepId.NAME }
        var guard = 0
        while (vm.state.value.currentStep != SetupStepId.EXPERIENCE) {
            check(guard++ < 12) { "los datos básicos no llegan a EXPERIENCE (cursor ${vm.state.value.currentStep})" }
            val step = vm.state.value.currentStep
            when (step) {
                SetupStepId.NAME -> vm.setStepText(SetupStepId.NAME, "Ana")
                SetupStepId.AGE -> vm.setStepNumber(SetupStepId.AGE, 30.0)
                SetupStepId.HEIGHT -> vm.setStepNumber(SetupStepId.HEIGHT, 175.0)
                SetupStepId.WEIGHT -> vm.setStepNumber(SetupStepId.WEIGHT, 72.0)
                SetupStepId.EQUATION_SEX -> vm.setStepChoice(SetupStepId.EQUATION_SEX, "male")
                SetupStepId.BODY_FAT -> vm.updateStep(SetupStepId.BODY_FAT) { it.withBodyFatRulerValue(18) }
                else -> Unit
            }
            await(vm, "reposo tras responder $step") { rest(it) }
            val next = checkNotNull(SetupStepGraph.next(step, vm.state.value.draft.stepContext()))
            confirm(vm, step, next)
        }
    }

    private fun answer(vm: SetupWizardViewModel, step: SetupStepId, person: Person) {
        when (step) {
            SetupStepId.EXPERIENCE -> vm.setStepChoice(SetupStepId.EXPERIENCE, person.experience)
            SetupStepId.EQUIPMENT -> vm.updateStep(SetupStepId.EQUIPMENT) { it.withPlaces(person.places) }
            SetupStepId.AVAILABILITY -> person.material?.let { material -> vm.updateStep(SetupStepId.AVAILABILITY) { it.withMaterial(material) } }
            SetupStepId.GOAL -> vm.setGoalProfile(person.profile)
            SetupStepId.FRESH_DAY -> vm.setFreshDay(person.days.first())
            SetupStepId.WEEKDAYS -> vm.updateStep(SetupStepId.WEEKDAYS) { draft ->
                person.dayPlaces.entries.fold(draft.withWeekdays(person.days)) { current, (day, place) -> current.withDayPlace(day, place) }
            }
            SetupStepId.SESSION_TIME -> vm.setSessionMinutes(person.minutes)
            SetupStepId.CARDIO_TYPE -> vm.setStepChoice(SetupStepId.CARDIO_TYPE, "RUN_OUTDOOR")
            SetupStepId.CARDIO_TIME -> vm.setStepChoice(SetupStepId.CARDIO_TIME, "30")
            SetupStepId.VOLUME_TECHNIQUE -> vm.setStepChoice(step, "3")
            SetupStepId.VOLUME_CONSISTENCY -> vm.setStepChoice(step, "1")
            SetupStepId.VOLUME_STRENGTH -> vm.setStepChoice(step, "2")
            SetupStepId.VOLUME_MOBILITY -> vm.setStepChoice(step, "3")
            SetupStepId.CAPABILITIES -> vm.state.value.draft.capabilitySkills().forEachIndexed { index, skill ->
                vm.setCapability(skill, CapabilityLevel.entries[index % CapabilityLevel.entries.size])
            }
            SetupStepId.PRIORITIES -> vm.updateStep(SetupStepId.PRIORITIES) { it.withMuscles(setOf(MuscleSymbol.CHEST, MuscleSymbol.GLUTES)) }
            SetupStepId.TRAINING_MAX -> {
                vm.setLiftMark(LiftMark.SQUAT, 160.0)
                vm.setLiftMark(LiftMark.BENCH, 110.0)
                vm.setLiftMark(LiftMark.DEADLIFT, 200.0)
                vm.setMarksUnit("lb")
            }
            else -> Unit
        }
    }

    private fun rest(state: SetupWizardState): Boolean =
        !state.isLoading && !state.isCommitting && !state.isSubmittingAnswer && !state.isSavingAndExiting &&
            state.machineState != WizChatMachineState.PersistingAnswer &&
            state.machineState != WizChatMachineState.Committing

    private fun idle(state: SetupWizardState): Boolean =
        rest(state) && state.machineState != WizChatMachineState.PreparingPreview &&
            !state.isPreviewLoading && !state.isCandidateLoading

    private fun TestScope.confirm(vm: SetupWizardViewModel, step: SetupStepId, expectedNext: SetupStepId) {
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
                        "errores=${state.errors} preview=${state.previewError} cargando=${state.isPreviewLoading}/${state.isCandidateLoading}",
                )
            }
            Thread.sleep(10)
        }
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

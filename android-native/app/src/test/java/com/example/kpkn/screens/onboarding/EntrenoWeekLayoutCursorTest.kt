package com.example.kpkn.screens.onboarding

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.exercises.catalogv2.CatalogV2ProcessCache
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.onboarding.SetupCommitCoordinator
import com.example.kpkn.data.onboarding.SetupCommitRequest
import com.example.kpkn.data.onboarding.SetupCommitResult
import com.example.kpkn.data.onboarding.SetupDraft
import com.example.kpkn.data.onboarding.SetupDraftCandidate
import com.example.kpkn.data.onboarding.SetupDraftRepository
import com.example.kpkn.data.onboarding.SetupDraftResolver
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.onboarding.GeneratedPlans
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
 * Entreno v2 (Q2) · el cursor no rebota a PLAN al armar la semana.
 *
 * La semana armada (WEEK_LAYOUT) depende del programa elegido (PLAN), nunca al revés: mover una sesión o adaptar el programa a
 * otro reparto es una decisión sobre ESA semana, así que no reabre el programa ni deja pasos «por revisar». Antes, adaptar el
 * reparto contaba como un cambio de reparto del plan (`SetupChangeSource.SPLIT`) y dejaba PLAN y WEEK_LAYOUT por revisar: si la
 * persona salía justo ahí, el borrador se reabría en PLAN («PLAN por revisar»).
 *
 * Cada prueba recorre el asistente REAL (ViewModel, generador de rutinas y planificador reales; Room en memoria) hasta la
 * semana armada y comprueba qué paso queda activo y qué pasos quedan por revisar, también tras «matar el proceso» (un
 * ViewModel nuevo sobre la misma base de datos, que es lo único que sobrevive).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class EntrenoWeekLayoutCursorTest {

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

    /** La persona: gimnasio, «Fuerza y masa muscular», cuatro días, el jueves como día fuerte y 60 min por sesión. */
    private val profile = TrainingGoalProfile.STRENGTH_MUSCLE
    private val days = setOf(1, 2, 4, 5)

    @Test
    fun movingASessionInTheWeekDoesNotReopenThePlan() = runTest(dispatcher.scheduler, timeout = 15.minutes) {
        val db = KpknDatabase.createInMemory(app)
        try {
            var vm = newVm(db, "q2-cursor-move")
            val prepared = reachWeekLayout(vm)
            assertStaysOnTheWeek("al llegar a la semana armada", prepared)

            val layout = checkNotNull(prepared.weekLayout)
            val movable = layout.assignment.entries.minBy { it.key }
            val freeDay = (1..7).first { it !in layout.assignment.keys }
            vm.moveSession(movable.value, freeDay)
            val moved = await(vm, "sesión movida") { idle(it) && it.weekLayout?.assignment?.get(freeDay) == movable.value }
            assertStaysOnTheWeek("tras mover una sesión", moved)

            // Intercambiar dos sesiones también es solo una decisión sobre la semana.
            val taken = moved.weekLayout!!.assignment.keys.sorted()
            vm.moveSession(moved.weekLayout!!.assignment.getValue(taken.first()), taken.last())
            val swapped = await(vm, "sesiones intercambiadas") { idle(it) && it.draft.weekLayoutOverrides != moved.draft.weekLayoutOverrides }
            assertStaysOnTheWeek("tras intercambiar dos sesiones", swapped)

            // Salir justo ahí y volver: el borrador se reabre en la semana, con la sesión donde se dejó.
            val before = swapped.draft.weekLayoutOverrides
            viewModelStore.clear()
            vm = newVm(db, "q2-cursor-move")
            val reopened = await(vm, "borrador reabierto") { idle(it) && it.weekLayout != null }
            assertStaysOnTheWeek("al reabrir el borrador", reopened)
            assertEquals("la semana armada se conserva", before, reopened.draft.weekLayoutOverrides)
        } finally {
            viewModelStore.clear()
            db.close()
        }
    }

    @Test
    fun adaptingTheSplitInTheWeekDoesNotReopenThePlan() = runTest(dispatcher.scheduler, timeout = 15.minutes) {
        val db = KpknDatabase.createInMemory(app)
        try {
            var vm = newVm(db, "q2-cursor-adapt")
            val prepared = reachWeekLayout(vm)
            assertStaysOnTheWeek("al llegar a la semana armada", prepared)

            val split = checkNotNull(prepared.weekLayout).splitOptions.let { options -> options.firstOrNull { it.id == "ul_x4" } ?: options.first() }
            vm.adaptToSplit(split.id)
            val adapted = await(vm, "programa adaptado al reparto") {
                idle(it) && it.draft.adaptedSplitId == split.id && it.weekLayout?.selectedSplitId == split.id
            }
            assertStaysOnTheWeek("tras adaptar el reparto", adapted)

            // Adaptar y luego mover una sesión del reparto nuevo: sigue siendo la semana y nada más.
            val layout = checkNotNull(adapted.weekLayout)
            val freeDay = (1..7).first { it !in layout.assignment.keys }
            vm.moveSession(layout.assignment.entries.minBy { it.key }.value, freeDay)
            val movedAfter = await(vm, "sesión movida tras adaptar") { idle(it) && it.weekLayout?.assignment?.containsKey(freeDay) == true }
            assertStaysOnTheWeek("tras adaptar y mover una sesión", movedAfter)

            // «Restablecer» tampoco reabre nada.
            vm.resetWeekLayout()
            val reset = await(vm, "semana restablecida") { idle(it) && it.draft.adaptedSplitId == null && it.weekLayout != null }
            assertStaysOnTheWeek("tras restablecer la semana", reset)

            // Y el reparto adaptado, con el proceso muerto, vuelve a la semana (no a PLAN).
            vm.adaptToSplit(split.id)
            val again = await(vm, "otra vez adaptado") { idle(it) && it.draft.adaptedSplitId == split.id && it.weekLayout?.selectedSplitId == split.id }
            viewModelStore.clear()
            vm = newVm(db, "q2-cursor-adapt")
            val reopened = await(vm, "borrador reabierto") { idle(it) && it.weekLayout != null }
            assertStaysOnTheWeek("al reabrir el borrador adaptado", reopened)
            assertEquals("el reparto adaptado se conserva", again.draft.adaptedSplitId, reopened.draft.adaptedSplitId)
            assertEquals("el programa elegido se conserva", GeneratedPlans.entryIdFor(profile), reopened.draft.selectedCatalogId)
        } finally {
            viewModelStore.clear()
            db.close()
        }
    }

    @Test
    fun whatTheWeekDependsOnStillReopensTheProgramAndTheWeek() = runTest(dispatcher.scheduler, timeout = 15.minutes) {
        val db = KpknDatabase.createInMemory(app)
        try {
            val vm = newVm(db, "q2-cursor-depends")
            val prepared = reachWeekLayout(vm)
            val layout = checkNotNull(prepared.weekLayout)
            val freeDay = (1..7).first { it !in layout.assignment.keys }
            vm.moveSession(layout.assignment.entries.minBy { it.key }.value, freeDay)
            val moved = await(vm, "sesión movida") { idle(it) && it.weekLayout?.assignment?.containsKey(freeDay) == true }
            assertStaysOnTheWeek("tras mover una sesión", moved)
            assertTrue("hay una decisión sobre la semana", moved.draft.weekLayoutOverrides.isNotEmpty())

            // La dependencia va en un solo sentido: un día más cambia el programa elegido, así que sí lo deja por revisar
            // (y la semana de antes ya no vale).
            vm.toggleWeekday(6)
            val changed = await(vm, "días cambiados y programa re-armado") {
                idle(it) && it.draft.selectedWeekdays == days + 6 && it.planSweep == SetupPlanSweep.READY && it.weekLayout != null
            }
            assertTrue(
                "cambiar los días deja PLAN y la semana por revisar: ${changed.draft.stepProgress.pendingReview}",
                changed.draft.stepProgress.pendingReview.containsAll(setOf(SetupStepId.PLAN, SetupStepId.WEEK_LAYOUT)),
            )
            assertTrue("la semana de antes ya no está", changed.draft.weekLayoutOverrides.isEmpty() && changed.draft.adaptedSplitId == null)
        } finally {
            viewModelStore.clear()
            db.close()
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════
    // Comprobaciones
    // ═════════════════════════════════════════════════════════════════════════════════════════

    /**
     * El cursor sigue en la semana armada, el programa elegido sigue confirmado y NINGÚN paso anterior queda por revisar. La
     * propia semana puede figurar por revisar (elegir el programa la marcó al llegar y se cierra al confirmarla): es el
     * paso activo, no un paso al que haya que volver.
     */
    private fun assertStaysOnTheWeek(what: String, state: SetupWizardState) {
        assertEquals("$what: el paso activo", SetupStepId.WEEK_LAYOUT, state.currentStep)
        assertEquals(
            "$what: pasos anteriores por revisar",
            emptySet<SetupStepId>(),
            state.draft.stepProgress.pendingReview - SetupStepId.WEEK_LAYOUT,
        )
        assertTrue("$what: PLAN sigue confirmado", SetupStepId.PLAN in state.draft.stepProgress.answers)
        assertNotNull("$what: el programa elegido", state.draft.selectedCatalogId)
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════
    // Recorrido
    // ═════════════════════════════════════════════════════════════════════════════════════════

    private fun TestScope.newVm(db: KpknDatabase, draftId: String): SetupWizardViewModel {
        val vm = SetupWizardViewModel(
            app,
            SavedStateHandle(),
            persistence = RoomPersistence(db),
            environment = FixedSettingsEnvironment(Settings()),
            commits = RoomCommits(db),
        ).also { viewModelStore.put("q2-cursor-${vmCounter++}", it) }
        vm.initialize(SetupWizardMode.TRAINING_ONLY, draftId = draftId)
        await(vm, "wizard cargado") { !it.isLoading }
        return vm
    }

    /** Recorre la ruta real hasta PLAN, elige el programa «a medida» y confirma: el cursor queda en WEEK_LAYOUT. */
    private fun TestScope.reachWeekLayout(vm: SetupWizardViewModel): SetupWizardState {
        await(vm, "primera pregunta") { !it.isLoading && it.currentStep == SetupStepId.NAME }
        var guard = 0
        while (vm.state.value.currentStep != SetupStepId.PLAN) {
            check(guard++ < 60) { "el recorrido no llega a PLAN (cursor ${vm.state.value.currentStep})" }
            val step = vm.state.value.currentStep
            answer(vm, step)
            await(vm, "reposo tras responder $step") { rest(it) }
            val next = checkNotNull(SetupStepGraph.next(step, vm.state.value.draft.stepContext())) { "ruta sin paso tras $step" }
            confirm(vm, step, next)
        }
        await(vm, "programa a medida listo") { idle(it) && it.planSweep == SetupPlanSweep.READY }
        vm.selectPlan(GeneratedPlans.entryIdFor(profile))
        await(vm, "programa elegido y preparado") { idle(it) && it.programPreview != null && it.draft.selectedCatalogId != null }
        confirm(vm, SetupStepId.PLAN, SetupStepId.WEEK_LAYOUT)
        return await(vm, "semana armada") { idle(it) && it.programPreview != null && it.weekLayout != null }
    }

    private fun answer(vm: SetupWizardViewModel, step: SetupStepId) {
        when (step) {
            SetupStepId.NAME -> vm.setStepText(SetupStepId.NAME, "Ana")
            SetupStepId.AGE -> vm.setStepNumber(SetupStepId.AGE, 30.0)
            SetupStepId.HEIGHT -> vm.setStepNumber(SetupStepId.HEIGHT, 175.0)
            SetupStepId.WEIGHT -> vm.setStepNumber(SetupStepId.WEIGHT, 72.0)
            SetupStepId.EQUATION_SEX -> vm.setStepChoice(SetupStepId.EQUATION_SEX, "male")
            SetupStepId.BODY_FAT -> vm.updateStep(SetupStepId.BODY_FAT) { it.withBodyFatRulerValue(18) }
            SetupStepId.EXPERIENCE -> vm.setStepChoice(SetupStepId.EXPERIENCE, "intermediate")
            SetupStepId.EQUIPMENT -> vm.updateStep(SetupStepId.EQUIPMENT) { it.withPlaces(setOf(TrainingPlace.GYM)) }
            SetupStepId.GOAL -> vm.setGoalProfile(profile)
            SetupStepId.FRESH_DAY -> vm.setFreshDay(4)
            SetupStepId.WEEKDAYS -> vm.updateStep(SetupStepId.WEEKDAYS) { it.withWeekdays(days) }
            SetupStepId.SESSION_TIME -> vm.setSessionMinutes(60)
            SetupStepId.VOLUME_TECHNIQUE, SetupStepId.VOLUME_CONSISTENCY, SetupStepId.VOLUME_STRENGTH,
            SetupStepId.VOLUME_MOBILITY -> vm.setStepChoice(step, "2")
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
                        "errores=${state.errors} preview=${state.previewError} cargando=${state.isPreviewLoading}/${state.isCandidateLoading} " +
                        "por revisar=${state.draft.stepProgress.pendingReview}",
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

package com.example.kpkn.screens.onboarding

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.exercises.catalogv2.CatalogV2ProcessCache
import com.example.kpkn.data.models.Gender
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.onboarding.SetupDraft
import com.example.kpkn.data.onboarding.SetupDraftCandidate
import com.example.kpkn.data.protocols.PlanLoadReferenceKind
import com.example.kpkn.data.protocols.PlanProvenanceClass
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.definitions.AuthoredPhulPhatRecipes
import com.example.kpkn.data.protocols.definitions.NativeProfileKind
import com.example.kpkn.domain.onboarding.AuthoredPlanFixtures
import com.example.kpkn.domain.onboarding.AuthoredPlanFixtures.Gear
import com.example.kpkn.domain.onboarding.PlanRejectionReason
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.WizChatMachineState
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.minutes

/**
 * Q5-F2 y F-A2 recorridos por el [SetupWizardViewModel] REAL (sin
 * `materializeOverride`, catálogo de producción, motor y evaluador reales):
 *
 *  - los cuatro planes de autor ya NO fallan como `INTERNAL_MATERIALIZATION`
 *    (antes `PROTOCOL_LIBRARY.first { it.id == entry.sourceId }` lanzaba
 *    `NoSuchElementException` porque sus `sourceId` no están en esa biblioteca);
 *  - con gimnasio completo PHUL y PHAT (original y adaptado) son candidatos
 *    viables, se pueden elegir y el preview es el programa preparado por la
 *    receta de la propia entrada, con procedencia;
 *  - sin material cada uno se rechaza con motivo TIPADO y no hay preview;
 *  - el rechazo por tiempo de un nativo v2 conserva `reasonCode` y los minutos
 *    mínimos del fitter (F-A2).
 *
 * La igualdad "preview == programa activado" a nivel de Room la prueba
 * `AuthoredPlansActivationParityTest` con el mismo `Ready`; aquí `commit()`
 * exigiría recorrer los ~40 pasos de la ruta y solo repetiría su compuerta.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class SetupWizardAuthoredPlansTest {

    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private lateinit var app: Application
    private var vmCounter = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        app = ApplicationProvider.getApplicationContext()
        // Cargador REAL del catálogo aprobado fuera del presupuesto de la prueba.
        val warm = runCatching { runBlocking { CatalogV2ProcessCache.getOrLoad(app) } }
        assertTrue(
            "El catálogo real exercise_catalog_v2.json no carga en Robolectric: ${warm.exceptionOrNull()?.message}",
            warm.isSuccess,
        )
    }

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    // ─── Arnés mínimo (mismo patrón que SetupWizardActivationGateTest) ───────

    private class InMemoryPersistence : SetupWizardPersistence {
        private val drafts = mutableMapOf<String, SetupDraft>()
        override suspend fun load(draftId: String): SetupDraft? = drafts[draftId]
        override suspend fun save(draftId: String, payloadJson: String, revision: Long, catalogRevision: String?): SetupDraft =
            SetupDraft(draftId, payloadJson, revision, catalogRevision, System.currentTimeMillis())
                .also { drafts[it.draftId] = it }
        override suspend fun discard(draftId: String) {
            drafts.remove(draftId)
        }
        override suspend fun listRecoverable(): List<SetupDraftCandidate> = emptyList()
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

    /** `materializeOverride` INTENCIONALMENTE ausente: motor, evaluador y catálogo reales. */
    private fun newVm(): SetupWizardViewModel =
        SetupWizardViewModel(
            app,
            SavedStateHandle(),
            persistence = InMemoryPersistence(),
            environment = FixedSettingsEnvironment(Settings()),
        ).also { store.put("authored-vm-${vmCounter++}", it) }

    private class Scenario(
        val label: String,
        val experience: SetupExperience,
        val goal: SetupGoal,
        val days: Int,
        val weekdays: Set<Int>,
        val minutes: Int,
        val gear: Gear,
    )

    private fun phulScenario(gear: Gear) = Scenario(
        "phul-${gear.label}", SetupExperience.INTERMEDIATE, SetupGoal.STRENGTH_MUSCLE, 4, setOf(1, 2, 4, 5), 100, gear,
    )

    private fun phatScenario(gear: Gear) = Scenario(
        "phat-${gear.label}", SetupExperience.ADVANCED, SetupGoal.STRENGTH_MUSCLE, 5, setOf(1, 2, 4, 5, 6), 100, gear,
    )

    /** Eventos PÚBLICOS reales del VM; el material es una declaración explícita, no un default. */
    private fun applyFixture(vm: SetupWizardViewModel, scenario: Scenario) {
        vm.updateStep(SetupStepId.NAME) { it.copy(name = "AUTORIA") }
        vm.updateStep(SetupStepId.AGE) { it.copy(ageYears = 30) }
        vm.updateStep(SetupStepId.HEIGHT) { it.copy(heightCm = 175.0) }
        vm.updateStep(SetupStepId.WEIGHT) { it.copy(weightKg = 70.0) }
        vm.updateStep(SetupStepId.EQUATION_SEX) { it.copy(profileGender = Gender.MALE) }
        vm.updateStep(SetupStepId.EXPERIENCE) { it.copy(experience = scenario.experience) }
        vm.updateStep(SetupStepId.GOAL) { it.copy(goal = scenario.goal, focus = SetupFocus.FULL_BODY) }
        vm.updateStep(SetupStepId.EQUIPMENT) {
            it.copy(trainingEnvironment = if (scenario.gear.availability.categories.isEmpty()) "none" else "gym")
        }
        vm.updateStep(SetupStepId.AVAILABILITY) {
            it.copy(trainingOptions = it.trainingOptions.copy(availability = scenario.gear.availability))
        }
        vm.updateStep(SetupStepId.WEEKDAYS) { it.copy(daysPerWeek = scenario.days, selectedWeekdays = scenario.weekdays) }
        vm.updateStep(SetupStepId.SESSION_TIME) { it.copy(minutesPerSession = scenario.minutes) }
    }

    private fun matches(draft: SetupWizardDraft, scenario: Scenario): Boolean =
        draft.goal == scenario.goal &&
            draft.experience == scenario.experience &&
            draft.daysPerWeek == scenario.days &&
            draft.selectedWeekdays == scenario.weekdays &&
            draft.minutesPerSession == scenario.minutes &&
            draft.trainingOptions.availability == scenario.gear.availability

    private fun isIdle(state: SetupWizardState): Boolean =
        !state.isLoading && !state.isCommitting && !state.isSubmittingAnswer && !state.isSavingAndExiting &&
            state.machineState != WizChatMachineState.PersistingAnswer &&
            state.machineState != WizChatMachineState.Committing &&
            state.machineState != WizChatMachineState.PreparingPreview &&
            !state.isPreviewLoading && !state.isCandidateLoading && !state.ringsPreviewLoading

    private fun TestScope.awaitUntil(vm: SetupWizardViewModel, timeoutMs: Long, condition: (SetupWizardState) -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            advanceUntilIdle()
            if (condition(vm.state.value)) return true
            if (System.currentTimeMillis() > deadline) return false
            Thread.sleep(5)
        }
    }

    /** Preview terminal del plan recién elegido, observado DOS veces seguidas tras drenar. */
    private fun TestScope.awaitPreviewFor(vm: SetupWizardViewModel, planId: String): Boolean {
        val deadline = System.currentTimeMillis() + PREVIEW_BUDGET_MS
        var stable = 0
        while (true) {
            repeat(10) { advanceUntilIdle() }
            val state = vm.state.value
            val terminal = state.draft.selectedCatalogId == planId && !state.isPreviewLoading &&
                !state.isCandidateLoading && !state.isLoading &&
                state.machineState != WizChatMachineState.PreparingPreview &&
                state.machineState != WizChatMachineState.PersistingAnswer &&
                (state.programPreview != null || state.previewError != null)
            stable = if (terminal) stable + 1 else 0
            if (stable >= 2) return true
            if (System.currentTimeMillis() > deadline) return false
            Thread.sleep(5)
        }
    }

    private fun stateDump(state: SetupWizardState): String =
        "machine=${state.machineState} errores=${state.errors} previewError=${state.previewError} " +
            "cargando=${state.isPreviewLoading}/${state.isCandidateLoading} " +
            "candidatos=${state.availablePlanCandidates.map { it.id }} " +
            "rechazos=${state.candidateRejections.map { "${it.planId}:${it.reasonCode}" }}"

    /** Inicializa el wizard real, declara el escenario y espera el barrido COMPLETO de candidatos. */
    private fun TestScope.settle(vm: SetupWizardViewModel, scenario: Scenario): SetupWizardState {
        vm.initialize(SetupWizardMode.FULL, draftId = "authored-${scenario.label}")
        assertTrue("el wizard no salió de isLoading: ${stateDump(vm.state.value)}", awaitUntil(vm, SETTLE_BUDGET_MS) { !it.isLoading })
        applyFixture(vm, scenario)
        var settled = awaitUntil(vm, SETTLE_BUDGET_MS) { isIdle(it) && matches(it.draft, scenario) }
        if (settled) {
            repeat(30) { advanceUntilIdle() }
            settled = awaitUntil(vm, SETTLE_BUDGET_MS) { isIdle(it) && matches(it.draft, scenario) }
        }
        assertTrue(
            "no se alcanzó un borrador en reposo con las entradas pedidas: ${stateDump(vm.state.value)}",
            settled,
        )
        val swept = awaitUntil(vm, SETTLE_BUDGET_MS) {
            isIdle(it) && (it.availablePlanCandidates.isNotEmpty() || it.candidateRejections.isNotEmpty())
        }
        assertTrue("el barrido de candidatos no terminó: ${stateDump(vm.state.value)}", swept)
        repeat(10) { advanceUntilIdle() }
        return vm.state.value
    }

    private fun rejectionOf(state: SetupWizardState, planId: String): SetupCandidateRejection? =
        state.candidateRejections.firstOrNull { it.planId == planId }

    private fun TestScope.selectAndAwait(vm: SetupWizardViewModel, planId: String): SetupWizardState {
        vm.selectPlan(planId)
        assertTrue("el preview de $planId no se asentó: ${stateDump(vm.state.value)}", awaitPreviewFor(vm, planId))
        return vm.state.value
    }

    private fun assertNoInternalFailureForAuthoredPlans(state: SetupWizardState) {
        val authoredIds = setOf(
            AuthoredPhulPhatRecipes.PHUL_ORIGINAL_ID, AuthoredPhulPhatRecipes.PHUL_ADAPTED_ID,
            AuthoredPhulPhatRecipes.PHAT_ORIGINAL_ID, AuthoredPhulPhatRecipes.PHAT_ADAPTED_ID,
        )
        val internal = state.candidateRejections.filter {
            it.planId in authoredIds && it.reasonCode == PlanRejectionReason.INTERNAL_MATERIALIZATION
        }
        assertTrue(
            "Q5-F2: un plan de autor no puede terminar como INTERNAL_MATERIALIZATION (PROTOCOL_LIBRARY.first): $internal",
            internal.isEmpty(),
        )
    }

    private fun assertPreviewIsThePreparedAuthoredProgram(
        state: SetupWizardState,
        planId: String,
        expectedCategory: PlanProvenanceClass,
        expectedSetCounts: List<Int>,
    ): Program {
        assertNull("sin error de preview para $planId: ${state.previewError}", state.previewError)
        val preview = checkNotNull(state.programPreview) { "$planId sin programa preparado: ${stateDump(state)}" }
        assertEquals("el programa preparado conserva el id estable del borrador", state.draft.commitId, preview.id)
        assertEquals(planId, preview.structureTemplateId)
        assertEquals(expectedCategory, preview.planProvenance?.category)
        assertEquals("programa y receta citan la misma procedencia", preview.sourceRecipe?.provenance, preview.planProvenance)
        assertEquals(expectedSetCounts, AuthoredPlanFixtures.firstWeekSetCounts(preview))
        val issues = ProgramExecutionContract.validate(preview)
        assertTrue("el preview debe ser ejecutable: ${issues.joinToString("; ") { it.message }}", issues.isEmpty())
        return preview
    }

    // ─── PHUL: original y adaptado con gimnasio completo ─────────────────────

    @Test
    fun fullGymOffersPhulOriginalAndAdaptedAndThePreviewIsTheAuthoredProgram() = runTest(dispatcher.scheduler, timeout = 6.minutes) {
        val vm = newVm()
        val swept = settle(vm, phulScenario(AuthoredPlanFixtures.fullGym))

        assertNoInternalFailureForAuthoredPlans(swept)
        val viable = swept.availablePlanCandidates.map { it.id }
        val original = AuthoredPhulPhatRecipes.PHUL_ORIGINAL_ID
        val adapted = AuthoredPhulPhatRecipes.PHUL_ADAPTED_ID
        assertTrue("PHUL original viable con gimnasio completo a 100 min: ${stateDump(swept)}", original in viable)
        assertTrue("PHUL adaptado viable con gimnasio completo a 100 min: ${stateDump(swept)}", adapted in viable)
        // §15.3: las adaptaciones van detrás de los planes propios, nunca delante por el desempate por id.
        viable.withIndex().filter { it.value.startsWith("native:") }.map { it.index }.maxOrNull()?.let { lastNative ->
            assertTrue("«adapted:» no puede preceder a «native:»: $viable", viable.indexOf(adapted) > lastNative)
        }

        val first = selectAndAwait(vm, original)
        val originalPreview = assertPreviewIsThePreparedAuthoredProgram(
            first, original, PlanProvenanceClass.ORIGINAL, listOf(18, 16, 21, 18),
        )
        assertEquals(original, originalPreview.sourceRecipe?.id)
        assertTrue(originalPreview.planProvenance?.slotChanges.orEmpty().isEmpty())

        val second = selectAndAwait(vm, adapted)
        val adaptedPreview = assertPreviewIsThePreparedAuthoredProgram(
            second, adapted, PlanProvenanceClass.ADAPTED, listOf(18, 16, 21, 18),
        )
        assertEquals("sin faltantes la adaptación conserva su receta", adapted, adaptedPreview.sourceRecipe?.id)

        // Volver al original no rehace nada: mismas sesiones, ejercicios, receta y procedencia.
        val third = selectAndAwait(vm, original)
        val again = checkNotNull(third.programPreview)
        assertEquals(
            originalPreview.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }
                .flatMap { it.sessions }.map { it.id },
            again.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }
                .flatMap { it.sessions }.map { it.id },
        )
        assertEquals(AuthoredPlanFixtures.exercisesOf(originalPreview).map { it.id }, AuthoredPlanFixtures.exercisesOf(again).map { it.id })
        assertEquals(originalPreview.sourceRecipe, again.sourceRecipe)
        assertEquals(originalPreview.planProvenance, again.planProvenance)
    }

    // ─── PHAT: original y adaptado con gimnasio completo ─────────────────────

    @Test
    fun fullGymOffersPhatOriginalAndAdaptedWithDeferredSpeedLoads() = runTest(dispatcher.scheduler, timeout = 6.minutes) {
        val vm = newVm()
        val swept = settle(vm, phatScenario(AuthoredPlanFixtures.fullGym))

        assertNoInternalFailureForAuthoredPlans(swept)
        val viable = swept.availablePlanCandidates.map { it.id }
        val original = AuthoredPhulPhatRecipes.PHAT_ORIGINAL_ID
        val adapted = AuthoredPhulPhatRecipes.PHAT_ADAPTED_ID
        assertTrue("PHAT original viable con gimnasio completo a 100 min: ${stateDump(swept)}", original in viable)
        assertTrue("PHAT adaptado viable con gimnasio completo a 100 min: ${stateDump(swept)}", adapted in viable)

        val state = selectAndAwait(vm, original)
        val preview = assertPreviewIsThePreparedAuthoredProgram(
            state, original, PlanProvenanceClass.ORIGINAL, listOf(21, 17, 24, 28, 28),
        )
        val speed = AuthoredPlanFixtures.weeksOf(preview).first().sessions
            .flatMap { it.allExercises() }.filter { it.slotRole == SlotRole.SPEED }
        assertEquals("tres bloques SPEED en los días de hipertrofia", 3, speed.size)
        speed.forEach { exercise ->
            assertEquals(PlanLoadReferenceKind.OBSERVED_WORKING_SET, exercise.loadReference?.kind)
            assertTrue("carga pendiente (null ≠ 0 kg)", exercise.sets.all { it.weight == null && it.targetPercentageRM == 65.0 })
        }

        val adaptedState = selectAndAwait(vm, adapted)
        assertPreviewIsThePreparedAuthoredProgram(
            adaptedState, adapted, PlanProvenanceClass.ADAPTED, listOf(21, 17, 24, 28, 28),
        )
    }

    // ─── Sin material: motivo tipado y ningún preview ────────────────────────

    @Test
    fun bodyweightOnlyRejectsAuthoredPlansWithTypedReasonsAndPublishesNoPreview() = runTest(dispatcher.scheduler, timeout = 6.minutes) {
        val vm = newVm()
        val swept = settle(vm, phulScenario(AuthoredPlanFixtures.bodyweightOnly))

        assertNoInternalFailureForAuthoredPlans(swept)
        val original = AuthoredPhulPhatRecipes.PHUL_ORIGINAL_ID
        val adapted = AuthoredPhulPhatRecipes.PHUL_ADAPTED_ID
        val viable = swept.availablePlanCandidates.map { it.id }
        assertTrue("sin material ningún PHUL es viable: $viable", original !in viable && adapted !in viable)

        val originalRejection = checkNotNull(rejectionOf(swept, original)) { "falta el rechazo de $original: ${stateDump(swept)}" }
        assertEquals(PlanRejectionReason.APPARATUS_ABSENT, originalRejection.reasonCode)
        assertEquals(SetupCandidateRejectionStage.MATERIAL, originalRejection.stage)
        assertFalse(originalRejection.needsApparatusConfirmation)
        assertTrue("el autorado conserva los requisitos estructurados", originalRejection.missingRequirements.isNotEmpty())

        val adaptedRejection = checkNotNull(rejectionOf(swept, adapted)) { "falta el rechazo de $adapted: ${stateDump(swept)}" }
        assertEquals(PlanRejectionReason.NO_VALID_SUBSTITUTION, adaptedRejection.reasonCode)
        assertEquals(SetupCandidateRejectionStage.MATERIAL, adaptedRejection.stage)
        assertEquals(listOf("inc-db=incline_bench_press__dumbbells"), adaptedRejection.affectedSlots)

        // Un borrador restaurado puede conservar la selección: el preview falla con el MISMO motivo y no publica nada.
        val state = selectAndAwait(vm, original)
        assertNull("nada se publica para un original sin material", state.programPreview)
        assertNotNull(state.previewError)
        assertTrue(
            "el error explica el material que falta: ${state.previewError}",
            state.previewError.orEmpty().contains("barra y carga") &&
                state.previewError.orEmpty().contains("que dijiste que no tienes"),
        )
        listOf("barbell", "machine_config", "APPARATUS_", "Diagnóstico").forEach { technical ->
            assertFalse("el preview no muestra $technical", state.previewError.orEmpty().contains(technical))
        }
    }

    // ─── F-A2: razón tipada de los nativos v2 ────────────────────────────────

    @Test
    fun nativeFitterTimeBudgetRejectionKeepsItsReasonCodeAndMinimumMinutes() = runTest(dispatcher.scheduler, timeout = 6.minutes) {
        val vm = newVm()
        val scenario = Scenario(
            "f-a2-muscle-20", SetupExperience.NEW, SetupGoal.MUSCLE, 1, setOf(1), 20, AuthoredPlanFixtures.bodyweightOnly,
        )
        val swept = settle(vm, scenario)

        val muscle = checkNotNull(rejectionOf(swept, NativeProfileKind.MUSCLE.entryId)) {
            "Músculo corporal de 1 día a 20 min debe rechazarse con motivo: ${stateDump(swept)}"
        }
        assertEquals(PlanRejectionReason.TIME_BUDGET, muscle.reasonCode)
        assertEquals(SetupCandidateRejectionStage.DURATION, muscle.stage)
        val required = muscle.requiredMinutes
        assertNotNull("el fitter calculó el mínimo y el VM ya no lo pierde: ${muscle.reason}", required)
        assertTrue("el mínimo real supera los 20 min elegidos: $required", (required ?: 0) > 20)
        assertTrue("la UI muestra «Este plan necesita N min»: ${muscle.reason}", muscle.reason.contains("min"))
        assertTrue("un rechazo interno no se presenta como incompatibilidad de usuario", muscle.reasonCode != PlanRejectionReason.INTERNAL_MATERIALIZATION)
        if (swept.availablePlanCandidates.isEmpty()) {
            // Ningún candidato cabe: la explicación del tiempo viaja intacta hasta la pantalla
            // (CandidateIncompatibility muestra «Este plan necesita N min por sesión»).
            assertTrue(swept.candidateRejections.any { it.reasonCode == PlanRejectionReason.TIME_BUDGET && it.requiredMinutes != null })
        }
    }

    private companion object {
        const val SETTLE_BUDGET_MS = 90_000L
        const val PREVIEW_BUDGET_MS = 90_000L
    }
}

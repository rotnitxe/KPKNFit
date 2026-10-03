package com.example.kpkn.screens.onboarding

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.exercises.catalogv2.CatalogV2ProcessCache
import com.example.kpkn.data.models.Gender
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.onboarding.SetupDraft
import com.example.kpkn.data.onboarding.SetupDraftCandidate
import com.example.kpkn.domain.onboarding.AuthoredPlanFixtures
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.WizChatMachineState
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.minutes

/**
 * C3 · El [SetupWizardViewModel] REAL (motor y catálogo de producción, sin
 * `materializeOverride`) con UN solo día por semana: las tarjetas de plan
 * explican el encaje con la frase singular «Encaja con tu semana de 1 día» y
 * ningún texto visible mezcla el número 1 con un sustantivo en plural
 * («1 días»). Mismo arnés mínimo que [SetupWizardAuthoredPlansTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class SetupWizardOneDayCopyTest {

    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private lateinit var app: Application

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        app = ApplicationProvider.getApplicationContext()
        // Catálogo REAL aprobado, calentado fuera del presupuesto de la prueba.
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

    // ─── Arnés mínimo ────────────────────────────────────────────────────────

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
        ).also { store.put("one-day-vm", it) }

    /** Eventos PÚBLICOS reales del VM: Músculo, empezando, solo peso corporal, 1 día (lunes), 60 min. */
    private fun applyOneDayFixture(vm: SetupWizardViewModel) {
        vm.updateStep(SetupStepId.NAME) { it.copy(name = "UNDIA") }
        vm.updateStep(SetupStepId.AGE) { it.copy(ageYears = 30) }
        vm.updateStep(SetupStepId.HEIGHT) { it.copy(heightCm = 175.0) }
        vm.updateStep(SetupStepId.WEIGHT) { it.copy(weightKg = 70.0) }
        vm.updateStep(SetupStepId.EQUATION_SEX) { it.copy(profileGender = Gender.MALE) }
        vm.updateStep(SetupStepId.EXPERIENCE) { it.copy(experience = SetupExperience.NEW) }
        vm.updateStep(SetupStepId.GOAL) { it.copy(goal = SetupGoal.MUSCLE, focus = SetupFocus.FULL_BODY) }
        vm.updateStep(SetupStepId.EQUIPMENT) { it.copy(trainingEnvironment = "none") }
        vm.updateStep(SetupStepId.AVAILABILITY) {
            it.copy(trainingOptions = it.trainingOptions.copy(availability = AuthoredPlanFixtures.bodyweightOnly.availability))
        }
        vm.updateStep(SetupStepId.DAYS) { it.copy(daysPerWeek = 1, selectedWeekdays = setOf(1)) }
        vm.updateStep(SetupStepId.SESSION_TIME) { it.copy(minutesPerSession = 60) }
    }

    private fun matchesFixture(draft: SetupWizardDraft): Boolean =
        draft.goal == SetupGoal.MUSCLE &&
            draft.experience == SetupExperience.NEW &&
            draft.daysPerWeek == 1 &&
            draft.selectedWeekdays == setOf(1) &&
            draft.minutesPerSession == 60 &&
            draft.trainingOptions.availability == AuthoredPlanFixtures.bodyweightOnly.availability

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

    private fun stateDump(state: SetupWizardState): String =
        "machine=${state.machineState} errores=${state.errors} previewError=${state.previewError} " +
            "cargando=${state.isPreviewLoading}/${state.isCandidateLoading} " +
            "candidatos=${state.availablePlanCandidates.map { it.id }} " +
            "rechazos=${state.candidateRejections.map { "${it.planId}:${it.reasonCode}" }}"

    /** Inicializa el wizard real, declara el escenario y espera el barrido COMPLETO de candidatos. */
    private fun TestScope.settleOneDay(vm: SetupWizardViewModel): SetupWizardState {
        vm.initialize(SetupWizardMode.FULL, draftId = "plural-one-day")
        assertTrue("el wizard no salió de isLoading: ${stateDump(vm.state.value)}", awaitUntil(vm, SETTLE_BUDGET_MS) { !it.isLoading })
        applyOneDayFixture(vm)
        var settled = awaitUntil(vm, SETTLE_BUDGET_MS) { isIdle(it) && matchesFixture(it.draft) }
        if (settled) {
            repeat(30) { advanceUntilIdle() }
            settled = awaitUntil(vm, SETTLE_BUDGET_MS) { isIdle(it) && matchesFixture(it.draft) }
        }
        assertTrue("no se alcanzó un borrador en reposo con las entradas pedidas: ${stateDump(vm.state.value)}", settled)
        val swept = awaitUntil(vm, SETTLE_BUDGET_MS) {
            isIdle(it) && (it.availablePlanCandidates.isNotEmpty() || it.candidateRejections.isNotEmpty())
        }
        assertTrue("el barrido de candidatos no terminó: ${stateDump(vm.state.value)}", swept)
        repeat(10) { advanceUntilIdle() }
        return vm.state.value
    }

    // ─── Un día por semana ───────────────────────────────────────────────────

    @Test
    fun oneDayWeekExplainsTheFitWithTheSingularPhraseAndNeverSaysOneDays() = runTest(dispatcher.scheduler, timeout = 6.minutes) {
        val vm = newVm()
        val state = settleOneDay(vm)

        assertEquals(1, state.draft.daysPerWeek)
        val candidates = state.availablePlanCandidates
        assertTrue("con 1 día y 60 min tiene que haber al menos un plan viable: ${stateDump(state)}", candidates.isNotEmpty())

        candidates.forEach { candidate ->
            assertTrue(
                "${candidate.id}: falta «Encaja con tu semana de 1 día» en ${candidate.reasons}",
                "Encaja con tu semana de 1 día" in candidate.reasons,
            )
            val visible = listOf(candidate.title, candidate.subtitle, candidate.description) + candidate.reasons
            visible.forEach { text ->
                assertTrue(
                    "${candidate.id}: «$text» mezcla 1 con un sustantivo en plural",
                    !ONE_WITH_PLURAL.containsMatchIn(text),
                )
            }
        }
        // El plural no se cuela donde ya hay un solo día (frase de varios días).
        assertTrue(
            "ninguna tarjeta usa la frase de varios días con un solo día",
            candidates.none { candidate -> candidate.reasons.any { it.startsWith("Encaja con tus ") } },
        )
    }

    private companion object {
        const val SETTLE_BUDGET_MS = 90_000L

        /** Cantidad uno seguida de un sustantivo en plural: lo que C3 elimina. */
        val ONE_WITH_PLURAL =
            Regex("""\b1 (días|sesiones|semanas|ejercicios|series|bloques|planes|pasos)\b""")
    }
}

package com.example.kpkn.screens.onboarding

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.exercises.catalogv2.CatalogV2ProcessCache
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.onboarding.SetupDraft
import com.example.kpkn.data.onboarding.SetupDraftCandidate
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.domain.onboarding.SetupAnswerProvenance
import com.example.kpkn.domain.onboarding.SetupProgressOrigin
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.SetupStepProgress
import com.example.kpkn.domain.onboarding.SetupWizardBlock
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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.minutes

/**
 * Entreno v2 · el reloj de tiempo por sesión ya no baja de 30 min (decisión del 2026-10-08) y los borradores que lo guardaron con
 * 20 o 25 min se abren, por el camino real de carga del [SetupWizardViewModel], leídos como 30 y con el paso por revisar:
 * el cursor vuelve a SESSION_TIME, nada se confirma solo, el resto de lo respondido sigue como estaba y lo que se guarda ya está
 * reparado. Confirmar el paso (la persona decide si los 30 le sirven) cierra su revisión y avanza. La regla pura de la reparación
 * está en `SetupEntrenoCompatibilityTest`; aquí se prueba que el asistente real la aplica al abrir y la persiste.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class EntrenoSessionTimeDraftTest {

    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var app: Application

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        app = ApplicationProvider.getApplicationContext()
        // Catálogo REAL aprobado, calentado fuera del presupuesto de la prueba.
        val warm = runCatching { runBlocking { CatalogV2ProcessCache.getOrLoad(app) } }
        assertTrue("el catálogo real no carga en Robolectric: ${warm.exceptionOrNull()?.message}", warm.isSuccess)
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

    private fun newVm(persistence: SetupWizardPersistence): SetupWizardViewModel =
        SetupWizardViewModel(
            application = app,
            savedStateHandle = SavedStateHandle(),
            persistence = persistence,
            environment = FixedSettingsEnvironment(Settings()),
            materializeOverride = SetupWizardMaterializer { SetupPreview(null, null) },
        ).also { store.put("session-time-vm", it) }

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

    /** Lo que un alta de la ruta nueva ya había respondido hasta la constancia, con el tiempo por sesión confirmado. */
    private val answered = listOf(
        SetupStepId.NAME, SetupStepId.AGE, SetupStepId.HEIGHT, SetupStepId.WEIGHT, SetupStepId.EQUATION_SEX,
        SetupStepId.BODY_FAT, SetupStepId.MILESTONE_BASICS, SetupStepId.EXPERIENCE, SetupStepId.EQUIPMENT,
        SetupStepId.GOAL, SetupStepId.DAYS, SetupStepId.WEEKDAYS, SetupStepId.AVAILABILITY, SetupStepId.FRESH_DAY,
        SetupStepId.SESSION_TIME, SetupStepId.VOLUME_TECHNIQUE, SetupStepId.VOLUME_CONSISTENCY,
    )

    /** Un borrador guardado con [minutes] por sesión y el cursor en la constancia. */
    private fun storedDraft(minutes: Int) = SetupWizardDraft(
        draftId = DRAFT_ID,
        draftScope = "full",
        trainingPath = SetupTrainingPath.PERSONALIZE,
        ageYears = 30,
        heightCm = 175.0,
        weightKg = 72.0,
        experience = SetupExperience.INTERMEDIATE,
        trainingEnvironment = "gym",
        equipment = setOf(SetupEquipment.GYM),
        goal = SetupGoal.STRENGTH,
        daysPerWeek = 3,
        selectedWeekdays = setOf(1, 3, 5),
        minutesPerSession = minutes,
        stepProgress = SetupStepProgress(
            block = SetupWizardBlock.TRAINING,
            currentStepId = SetupStepId.VOLUME_CONSISTENCY,
            answers = answered.associateWith { SetupAnswerProvenance.USER_DECLARED },
            visited = answered,
            origin = SetupProgressOrigin.NATIVE,
            graphRevision = SetupStepGraph.REVISION,
        ),
    )

    private fun persistedDraft(persistence: SetupWizardPersistence): SetupWizardDraft = runBlocking {
        json.decodeFromString<SetupWizardDraft>(checkNotNull(persistence.load(DRAFT_ID)) { "el borrador no se guardó" }.payloadJson)
    }

    // ─── Abrir un borrador con menos de 30 min ───────────────────────────────

    @Test
    fun aStoredDraftWithTwentyOrTwentyFiveMinutesOpensAtTheTimeStepReadAsThirtyAndConfirmingItKeepsTheRest() =
        runTest(dispatcher.scheduler, timeout = 5.minutes) {
            for (oldMinutes in listOf(20, 25)) {
                val persistence = InMemoryPersistence()
                val stored = storedDraft(oldMinutes)
                runBlocking { persistence.save(DRAFT_ID, json.encodeToString(stored), 7L, PersonalizedPlanCatalog.REVISION) }
                val vm = newVm(persistence)

                vm.initialize(SetupWizardMode.FULL, draftId = DRAFT_ID)
                assertTrue("$oldMinutes: el wizard no salió de isLoading", awaitUntil(vm, SETTLE_BUDGET_MS) { !it.isLoading })
                assertTrue("$oldMinutes: el wizard no quedó en reposo", awaitUntil(vm, SETTLE_BUDGET_MS) { isIdle(it) })
                val opened = vm.state.value.draft

                // Se lee como 30, el paso queda por revisar y el cursor vuelve a él.
                assertEquals("$oldMinutes min se leen como 30", 30, opened.minutesPerSession)
                assertTrue(SetupStepId.SESSION_TIME in opened.stepProgress.pendingReview)
                assertEquals(SetupStepId.SESSION_TIME, opened.stepProgress.currentStepId)
                // Nada se confirma solo ni se pierde: las respuestas son las guardadas y lo demás sigue como estaba.
                assertEquals(stored.stepProgress.answers, opened.stepProgress.answers)
                assertEquals(stored.selectedWeekdays, opened.selectedWeekdays)
                assertEquals(stored.experience, opened.experience)
                assertEquals(stored.ageYears, opened.ageYears)
                // Lo que se guarda ya está reparado: no hay que reparar dos veces ni se queda el 20 o el 25 en disco.
                val saved = persistedDraft(persistence)
                assertEquals(30, saved.minutesPerSession)
                assertTrue(SetupStepId.SESSION_TIME in saved.stepProgress.pendingReview)

                // «Continuar» está permitido con el 30 (el reloj lo enseña) y la persona decide: confirmar cierra la revisión.
                assertTrue(SetupWizardValidation.validateStep(opened, SetupStepId.SESSION_TIME).none { it.isBlocking })
                vm.setSessionMinutes(30)
                assertTrue(awaitUntil(vm, SETTLE_BUDGET_MS) { isIdle(it) && SetupStepId.SESSION_TIME in it.draft.declaredSteps })
                val submitted = vm.submitCurrentStep(SetupStepId.SESSION_TIME)
                assertTrue("$oldMinutes: confirmar el tiempo se acepta", submitted.accepted)
                assertTrue(
                    "$oldMinutes: el cursor avanza y la revisión del tiempo se cierra",
                    awaitUntil(vm, SETTLE_BUDGET_MS) {
                        isIdle(it) && it.draft.stepProgress.currentStepId != SetupStepId.SESSION_TIME &&
                            SetupStepId.SESSION_TIME !in it.draft.stepProgress.pendingReview
                    },
                )
                assertEquals(30, vm.state.value.draft.minutesPerSession)
                assertNotNull(vm.state.value.draft.stepProgress.answers[SetupStepId.SESSION_TIME])
                store.clear()
            }
        }

    @Test
    fun aStoredDraftWithThirtyMinutesOrMoreOpensWhereItWasLeft() = runTest(dispatcher.scheduler, timeout = 5.minutes) {
        for (minutes in listOf(30, 60)) {
            val persistence = InMemoryPersistence()
            runBlocking { persistence.save(DRAFT_ID, json.encodeToString(storedDraft(minutes)), 7L, PersonalizedPlanCatalog.REVISION) }
            val vm = newVm(persistence)

            vm.initialize(SetupWizardMode.FULL, draftId = DRAFT_ID)
            assertTrue("$minutes: el wizard no salió de isLoading", awaitUntil(vm, SETTLE_BUDGET_MS) { !it.isLoading })
            val opened = vm.state.value.draft

            assertEquals(minutes, opened.minutesPerSession)
            assertTrue(SetupStepId.SESSION_TIME !in opened.stepProgress.pendingReview)
            assertEquals("$minutes min: el cursor no se mueve", SetupStepId.VOLUME_CONSISTENCY, opened.stepProgress.currentStepId)
            store.clear()
        }
    }

    private companion object {
        const val DRAFT_ID = "session-time-stored"
        const val SETTLE_BUDGET_MS = 90_000L
    }
}

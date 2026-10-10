package com.example.kpkn.screens.onboarding

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.onboarding.SetupCommitRequest
import com.example.kpkn.data.onboarding.SetupCommitResult
import com.example.kpkn.data.onboarding.SetupDraft
import com.example.kpkn.data.onboarding.SetupDraftCandidate
import com.example.kpkn.domain.onboarding.SetupAnswerProvenance
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.SetupValueState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guard de fuente de la ruta PROTOCOL (D-005 / E-015 / AC-T004-03):
 *
 * (A) Con un [SetupWizardMaterializer] **indexado por `selectedCatalogId`** que
 *     solo sabe materializar un ID nativo testigo, la ruta PROTOCOL sin receta
 *     viable no debe publicar NINGÚN candidato nativo, no debe marcar
 *     `planAdaptedToBodyweight` y jamás debe consultar el motor con IDs nativos
 *     (el segundo pase `protocolOnly=false` está suprimido). La ruta tampoco se
 *     cambia sola.
 *
 * (B) **Sin override** (motor real + asset real), forzar un ID nativo bajo
 *     PROTOCOL —selección manipulada o borrador restaurado— debe rechazarse con
 *     motivo tipado de FUENTE EQUIVOCADA en preview y en la puerta real de
 *     activación, sin generar receipt; cambiar de ruta es siempre un acto
 *     explícito del usuario.
 *
 *     Paquete A · D2 (B-01): el ID forzado llega con el paso PLAN YA confirmado. Es lo único que
 *     conserva una selección que no está entre los candidatos viables de la lista nueva (si PLAN
 *     no se hubiera confirmado, el ViewModel la limpiaría como selección caída y la guardia de
 *     activación ya no tendría nada que rechazar; ese camino lo cubre
 *     `SetupWizardCandidateGateTest`). Con el paso confirmado la selección se conserva, no se
 *     relanza su preview y la puerta de activación sigue rechazándola por FUENTE EQUIVOCADA.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class SetupProtocolSourceGuardTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class RecordingCommits : SetupWizardCommits {
        var invocations = 0
        override suspend fun commit(request: SetupCommitRequest): SetupCommitResult {
            invocations++
            throw AssertionError("HARNESS: no debe alcanzarse el receipt con fuente equivocada")
        }
    }

    /** Persistencia en memoria: evita el Dispatchers.IO real que el scheduler de test no drena. */
    private class InMemoryPersistence : SetupWizardPersistence {
        val store = mutableMapOf<String, SetupDraft>()
        override suspend fun load(draftId: String): SetupDraft? = store[draftId]
        override suspend fun save(draftId: String, payloadJson: String, revision: Long, catalogRevision: String?): SetupDraft =
            SetupDraft(draftId, payloadJson, revision, catalogRevision, System.currentTimeMillis()).also { store[draftId] = it }
        override suspend fun discard(draftId: String) {
            store.remove(draftId)
        }
        override suspend fun listRecoverable(): List<SetupDraftCandidate> = emptyList()
    }

    private class FixedSettingsEnvironment(override val settings: Settings) : SetupWizardEnvironment {
        override suspend fun awaitReady() = Unit
        override fun activeProgramId(): String? = null
        override fun activeNutritionPlanId(): String? = null
        override fun activeNutritionPlan(): com.example.kpkn.data.models.NutritionPlan? = null
        override fun nutritionPlan(id: String): com.example.kpkn.data.models.NutritionPlan? = null
        override fun hasInitialRecoveryEvidence(): Boolean = false
        override suspend fun refreshBodyProgress() = Unit
    }

    /** Espera de RELOJ DE PARED acotada + drenado del scheduler: la persistencia real corre en Dispatchers.IO real, que `advanceUntilIdle` no espera. */
    private fun kotlinx.coroutines.test.TestScope.awaitWall(budgetMs: Long = 10_000L, condition: () -> Boolean) {
        val start = System.nanoTime()
        while (!condition() && (System.nanoTime() - start) < budgetMs * 1_000_000L) {
            advanceUntilIdle()
            Thread.sleep(20)
        }
        advanceUntilIdle()
    }

    private fun protocolDraft(draft: SetupWizardDraft): SetupWizardDraft = draft.copy(
            includeTraining = true,
            trainingPath = SetupTrainingPath.PERSONALIZE,
            goal = SetupGoal.MUSCLE,
            experience = SetupExperience.NEW,
            focus = SetupFocus.FULL_BODY,
            daysPerWeek = 3,
            selectedWeekdays = setOf(1, 2, 3),
            minutesPerSession = 60,
            equipment = setOf(SetupEquipment.BODYWEIGHT),
            trainingEnvironment = "none",
        )

    @Test
    fun `A - PROTOCOL sin receta no publica nativos ni adapta a bodyweight`() = runTest(dispatcher.scheduler) {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val consulted = mutableListOf<Pair<String, SetupProgramRoute?>>()
        val override = SetupWizardMaterializer { draft ->
            consulted += (draft.selectedCatalogId ?: "null") to draft.programRoute
            if (draft.selectedCatalogId == "native:bodyweight") {
                SetupPreview(Program(id = draft.commitId, name = "testigo nativo"), null)
            } else {
                SetupPreview(null, null)
            }
        }
        val vm = SetupWizardViewModel(
            application,
            SavedStateHandle(),
            persistence = InMemoryPersistence(),
            environment = FixedSettingsEnvironment(Settings()),
            commits = RecordingCommits(),
            materializeOverride = override,
        )
        vm.initialize(SetupWizardMode.FULL)
        awaitWall { vm.state.value.draft.draftId.isNotBlank() }
        vm.updateStep(SetupStepId.ROUTE) { protocolDraft(it) }
        awaitWall { vm.state.value.draft.daysPerWeek == 3 }
        vm.setStepChoice(SetupStepId.ROUTE, "protocol")
        awaitWall { vm.state.value.draft.programRoute == SetupProgramRoute.PROTOCOL }
        awaitWall { consulted.isNotEmpty() && !vm.state.value.isCandidateLoading }

        val state = vm.state.value
        println("DBG route=${state.draft.programRoute} selections=${state.draft.stepSelections} consultados=$consulted")
        val report = consultadosState(state, consulted.map { it.first })
        assertTrue(
            "El motor no debe consultarse con IDs nativos bajo PROTOCOL.$report",
            consulted.none { (id, route) -> route == SetupProgramRoute.PROTOCOL && id.startsWith("native:") },
        )
        assertTrue(
            "Ningún candidato nativo debe publicarse bajo PROTOCOL.$report",
            state.planCandidates.none { it.id.startsWith("native:") } &&
                state.availablePlanCandidates.none { it.id.startsWith("native:") },
        )
        assertTrue("planAdaptedToBodyweight debe ser false bajo PROTOCOL.$report", !state.planAdaptedToBodyweight)
        assertEquals(
            "La ruta PROTOCOL no puede cambiarse sola",
            SetupProgramRoute.PROTOCOL,
            state.draft.programRoute,
        )
    }

    @Test
    fun `B - ID nativo forzado bajo PROTOCOL se rechaza por fuente equivocada sin receipt`() =
        runTest(dispatcher.scheduler) {
            val application = ApplicationProvider.getApplicationContext<Application>()
            val commits = RecordingCommits()
            val vm = SetupWizardViewModel(
                application,
                SavedStateHandle(),
                persistence = InMemoryPersistence(),
                environment = FixedSettingsEnvironment(Settings()),
                commits = commits,
            )
            vm.initialize(SetupWizardMode.FULL)
            awaitWall { vm.state.value.draft.draftId.isNotBlank() }
            vm.updateStep(SetupStepId.ROUTE) { draft ->
                protocolDraft(draft).copy(
                    selectedCatalogId = "native:bodyweight",
                    // D2: paso PLAN ya confirmado, para que la selección forzada sobreviva a la lista nueva.
                    stepProgress = draft.stepProgress.recordAnswer(
                        SetupStepId.PLAN,
                        SetupAnswerProvenance.USER_DECLARED,
                        SetupValueState.DECLARED,
                    ),
                )
            }
            awaitWall { vm.state.value.draft.daysPerWeek == 3 }
            vm.setStepChoice(SetupStepId.ROUTE, "protocol")
            awaitWall { vm.state.value.draft.programRoute == SetupProgramRoute.PROTOCOL }
            awaitWall { vm.state.value.draft.selectedCatalogId == "native:bodyweight" && !vm.state.value.isPreviewLoading }
            awaitWall(budgetMs = 30_000L) { !vm.state.value.isCandidateLoading && !vm.state.value.isPreviewLoading }
            println("DBG-B previewError=${vm.state.value.previewError} lastFailure=${vm.state.value.lastFailure} errors=${vm.state.value.errors} preview=${vm.state.value.programPreview?.id}")

            // PLAN: jamás puede materializarse un plan nativo bajo la ruta PROTOCOL.
            assertNull(
                "Bajo PROTOCOL no puede existir preview de un plan nativo: ${vm.state.value.programPreview}",
                vm.state.value.programPreview,
            )
            val receipt = vm.commit()
            assertNull("No puede generarse receipt con fuente equivocada", receipt)
            assertEquals("El puerto de commits no debe invocarse", 0, commits.invocations)
            assertTrue(
                "La puerta de activación debe bloquear por fuente equivocada: ${vm.state.value.errors}",
                vm.state.value.errors.values.any { it.contains("Fuente equivocada") },
            )
        }

    private fun consultadosState(state: SetupWizardState, consulted: List<String>): String =
        " estado(candidatos=${state.planCandidates.size}/${state.availablePlanCandidates.size}, " +
        "adaptado=${state.planAdaptedToBodyweight}, previewError=${state.previewError}, " +
        "consultados=$consulted)"
}

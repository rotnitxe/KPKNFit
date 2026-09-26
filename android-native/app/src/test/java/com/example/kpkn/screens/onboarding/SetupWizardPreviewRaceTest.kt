package com.example.kpkn.screens.onboarding

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.onboarding.SetupDraft
import com.example.kpkn.data.onboarding.SetupDraftCandidate
import com.example.kpkn.domain.onboarding.WizChatMachineState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Carrera de previews A→B→A con el puerto de materialización (fakes actuales):
 *
 *  1. clave A se calcula y queda en caché;
 *  2. clave B arranca y queda a medias (respuesta tardía retenida en el puerto);
 *  3. un cambio con la MISMA clave B no duplica el job (dedup con job activo);
 *  4. volver a A es cache-hit: debe cancelar B y apagar `isPreviewLoading`
 *     (el bug: el cache-hit salía sin liberar y B ya no publicaba → loading
 *     eterno);
 *  5. la respuesta tardía de B, al llegar, no es dueña: no publica preview, no
 *     reenciende el loading y no deja error por la cancelación.
 *
 * Sin aumentar timeouts, sin fabricar previews y sin tocar la cola real: sólo
 * el puerto controla el orden/tiempo del materializado.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class SetupWizardPreviewRaceTest {

    private val dispatcher = StandardTestDispatcher()
    /** VM registrado para clear() → onCleared (cancela viewModelScope) antes de resetMain. */
    private val viewModelStore = ViewModelStore()
    private var viewModelCounter = 0

    private lateinit var persistence: FakePersistence
    private lateinit var app: Application
    private lateinit var handle: SavedStateHandle
    private lateinit var materializer: ControlledMaterializer

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        app = ApplicationProvider.getApplicationContext()
        persistence = FakePersistence()
        handle = SavedStateHandle()
        materializer = ControlledMaterializer()
    }

    @After
    fun tearDown() {
        // onCleared ANTES de resetMain: se cancela el viewModelScope y no queda
        // ninguna corrutina viva al final del test. Suelta también cualquier B
        // retenido, incluso si una aserción anterior abortó el recorrido.
        if (this::materializer.isInitialized) materializer.releaseB()
        viewModelStore.clear()
        Dispatchers.resetMain()
    }

    private fun vm() = SetupWizardViewModel(app, handle, persistence, FakeEnvironment(), null, materializer)
        .also { viewModelStore.put("vm-${viewModelCounter++}", it) }

    @Test
    fun cachedAIntermediateBCacheHitReleasesBAndLateBDoesNotPublish() = runTest {
        val vm = vm()
        vm.initialize(SetupWizardMode.FULL)
        advanceUntilIdle()

        // 1) Clave A calculada y cacheada.
        vm.update { it.withPreviewInputs(DAYS_A, WEEKDAYS_A) }
        awaitUntil(vm, "preview A publicado") {
            vm.state.value.programPreview?.id == previewId(DAYS_A) && !vm.state.value.isPreviewLoading
        }
        assertEquals(listOf(DAYS_A), materializer.calls())

        // 2) Clave B arranca y queda a medias (respuesta retenida en el puerto).
        vm.update { it.withPreviewInputs(DAYS_B, WEEKDAYS_B) }
        awaitUntil(vm, "job de B en marcha") {
            vm.state.value.isPreviewLoading &&
                vm.state.value.machineState == WizChatMachineState.PreparingPreview &&
                materializer.calls() == listOf(DAYS_A, DAYS_B)
        }
        // PreparingPreview se publica antes del dispatch IO. La llamada observada
        // por el puerto es la barrera de entrada real, no solo el estado previo.
        assertEquals(listOf(DAYS_A, DAYS_B), materializer.calls())

        // 3) Misma clave B de nuevo: dedup con job ACTIVO, sin duplicar materialización.
        vm.update { it.copy(name = "mismo clave B") }
        advanceUntilIdle()
        assertTrue(vm.state.value.isPreviewLoading)
        assertEquals(listOf(DAYS_A, DAYS_B), materializer.calls())

        // 4) Vuelta a A: cache-hit que DEBE liberar a B (cancelar) y apagar el loading.
        vm.update { it.withPreviewInputs(DAYS_A, WEEKDAYS_A) }
        awaitUntil(vm, "cache-hit de A con loading apagado") { !vm.state.value.isPreviewLoading }
        val cached = vm.state.value
        assertEquals(previewId(DAYS_A), cached.programPreview?.id)
        assertNull("la cancelación de B no deja error", cached.previewError)
        assertFalse("preview" in cached.errors)
        assertTrue(
            "el cache-hit retira PreparingPreview (machineState=${cached.machineState})",
            cached.machineState != WizChatMachineState.PreparingPreview,
        )
        assertFalse("B sigue retenido hasta después del cache-hit", materializer.hasBResponded())

        // 5) Respuesta tardía de B: ya no es dueño → nada cambia.
        materializer.releaseB()
        materializer.awaitBResponded()
        repeat(6) { advanceUntilIdle(); Thread.sleep(10) }
        val after = vm.state.value
        assertFalse("la respuesta tardía de B no reenciende el loading", after.isPreviewLoading)
        assertEquals("nunca se publica el preview de B", previewId(DAYS_A), after.programPreview?.id)
        assertNull(after.previewError)
        assertFalse("preview" in after.errors)
        assertTrue(after.machineState != WizChatMachineState.PreparingPreview)
        assertEquals("B se materializa una sola vez", 1, materializer.calls().count { it == DAYS_B })
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun awaitUntil(vm: SetupWizardViewModel, what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            // Sin receptor TestScope en el helper: se avanza el scheduler real.
            dispatcher.scheduler.advanceUntilIdle()
            if (condition()) return
            Thread.sleep(10)
        }
        val state = vm.state.value
        throw AssertionError(
            "timeout esperando: $what | loading=${state.isPreviewLoading} " +
                "preview=${state.programPreview?.id} machine=${state.machineState} " +
                "errors=${state.errors} previewError=${state.previewError}",
        )
    }

    /** Entradas completas de candidato/preview sin activar el job de candidatos. */
    private fun SetupWizardDraft.withPreviewInputs(days: Int, weekdays: Set<Int>): SetupWizardDraft = copy(
        includeTraining = true,
        programRoute = SetupProgramRoute.CUSTOMIZABLE,
        trainingPath = SetupTrainingPath.PERSONALIZE,
        selectedCatalogId = "demo-plan",
        daysPerWeek = days,
        selectedWeekdays = weekdays,
        minutesPerSession = 60,
        goal = SetupGoal.STRENGTH,
        // Sin material declarado: el job de candidatos queda apagado y sólo se
        // ejercita el de preview (mismo materializador controlado).
        equipment = emptySet(),
    )

    /** Puerto controlado: A responde ya; B se retiene hasta [releaseB]. */
    private class ControlledMaterializer : SetupWizardMaterializer {
        private val entered = mutableListOf<Int>()
        private val gateB = CompletableDeferred<Unit>()
        private val bResponded = CompletableDeferred<Unit>()

        override suspend fun materialize(draft: SetupWizardDraft): SetupPreview {
            val days = requireNotNull(draft.daysPerWeek) { "el preview exige daysPerWeek" }
            synchronized(entered) { entered += days }
            val response = SetupPreview(Program(id = previewId(days), name = previewId(days)), null)
            if (days != DAYS_B) return response

            // Simula una respuesta de materialización que llega aunque el
            // dueño del preview ya haya cancelado su espera. La barrera y la
            // respuesta completa quedan dentro de NonCancellable; limitarlo al
            // await haría que la cancelación impidiera producir la respuesta.
            return withContext(NonCancellable) {
                gateB.await()
                bResponded.complete(Unit)
                response
            }
        }

        fun calls(): List<Int> = synchronized(entered) { entered.toList() }

        fun hasBResponded(): Boolean = bResponded.isCompleted

        suspend fun awaitBResponded() = bResponded.await()

        fun releaseB() {
            gateB.complete(Unit)
        }
    }

    private class FakePersistence : SetupWizardPersistence {
        data class Row(val payloadJson: String, val revision: Long)

        val rows = LinkedHashMap<String, Row>()
        val saveLog = mutableListOf<SetupDraft>()

        override suspend fun load(draftId: String): SetupDraft? =
            rows[draftId]?.let { SetupDraft(draftId, it.payloadJson, it.revision, null, 0L) }

        override suspend fun save(draftId: String, payloadJson: String, revision: Long, catalogRevision: String?): SetupDraft {
            val current = rows[draftId]
            require(current == null || revision >= current.revision) { "revisión antigua" }
            rows[draftId] = Row(payloadJson, revision)
            val model = SetupDraft(draftId, payloadJson, revision, catalogRevision, 0L)
            saveLog += model
            return model
        }

        override suspend fun discard(draftId: String) {
            rows.remove(draftId)
        }

        override suspend fun listRecoverable(): List<SetupDraftCandidate> = emptyList()
    }

    private class FakeEnvironment(
        override val settings: Settings = Settings(),
    ) : SetupWizardEnvironment {
        override suspend fun awaitReady() = Unit
        override fun activeProgramId(): String? = null
        override fun activeNutritionPlanId(): String? = null
        override fun activeNutritionPlan(): NutritionPlan? = null
        override fun nutritionPlan(id: String): NutritionPlan? = null
        override fun hasInitialRecoveryEvidence(): Boolean = false
        override suspend fun refreshBodyProgress() = Unit
    }

    private companion object {
        const val DAYS_A = 3
        const val DAYS_B = 4
        val WEEKDAYS_A = setOf(1, 3, 5)
        val WEEKDAYS_B = setOf(1, 2, 4, 5)
        const val AWAIT_TIMEOUT_MS = 5_000L

        fun previewId(days: Int) = "preview-$days"
    }
}

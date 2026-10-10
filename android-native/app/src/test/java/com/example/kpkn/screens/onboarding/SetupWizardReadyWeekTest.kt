package com.example.kpkn.screens.onboarding

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.exercises.catalogv2.CatalogV2ProcessCache
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.models.EquipmentCategory
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramSchedulePlan
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.onboarding.SetupDraft
import com.example.kpkn.data.onboarding.SetupDraftCandidate
import com.example.kpkn.domain.onboarding.PlanEvaluationStage
import com.example.kpkn.domain.onboarding.PlanMaterializationException
import com.example.kpkn.domain.onboarding.PlanRejectionReason
import com.example.kpkn.domain.onboarding.WizChatMachineState
import com.example.kpkn.screens.programs.ReadyExercise
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
 * C.P5 · `SetupWizardViewModel.readyWeekSnapshotFor`: la semana REAL del candidato para la hoja «Cómo funciona».
 *
 * El ViewModel es el real (catálogo, planificador, evaluador y caché de producción); solo se sustituye, con el
 * puerto [SetupWizardMaterializer], el programa que cada plan deja preparado, para saber exactamente qué semana
 * tiene que salir. Fija que la semana sale de la caché del barrido VIGENTE (nunca materializa), tanto en el pase
 * pedido como en el pase a peso corporal, y que un plan que ya no es viable o que no existe no entrega nada.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class SetupWizardReadyWeekTest {

    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private lateinit var app: Application
    private var vmCounter = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        app = ApplicationProvider.getApplicationContext()
        // Catálogo REAL aprobado, calentado fuera del presupuesto de cada prueba.
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

    @Test
    fun aViableCandidateHandsOverTheRealFirstWeekOfItsPreparedProgram() = runTest(dispatcher.scheduler, timeout = 5.minutes) {
        val vm = newVm(Script())
        vm.initialize(SetupWizardMode.TRAINING_ONLY)
        awaitUntil(vm, "wizard cargado") { !it.isLoading }
        vm.update { it.withCandidateInputs() }
        val listed = awaitUntil(vm, "lista inicial de candidatos") { isIdle(it) && it.availablePlanCandidates.isNotEmpty() }

        // Las tres primeras tarjetas son las que el barrido deja siempre en la caché.
        listed.planCandidates.forEach { candidate ->
            val snapshot = requireNotNull(vm.readyWeekSnapshotFor(candidate.id)) { "${candidate.id}: sin semana lista" }
            val session = snapshot.sessions.single()
            assertEquals("día 1 del programa preparado, sin nombre propio", "Lunes", session.label)
            assertEquals(listOf(ReadyExercise("Press de banca", "3 × 8")), session.exercises)
        }
    }

    @Test
    fun aPlanThatIsNotACandidateHandsOverNothing() = runTest(dispatcher.scheduler, timeout = 5.minutes) {
        val vm = newVm(Script())
        assertNull("antes de cargar no hay nada", vm.readyWeekSnapshotFor("native:muscle-foundation-v2"))
        vm.initialize(SetupWizardMode.TRAINING_ONLY)
        awaitUntil(vm, "wizard cargado") { !it.isLoading }
        vm.update { it.withCandidateInputs() }
        awaitUntil(vm, "lista inicial de candidatos") { isIdle(it) && it.availablePlanCandidates.isNotEmpty() }

        assertNull(vm.readyWeekSnapshotFor("native:no-existe"))
        assertNull(vm.readyWeekSnapshotFor(""))
    }

    @Test
    fun aPlanThatStopsBeingViableHandsOverNothingWhileTheOthersKeepTheirWeek() = runTest(dispatcher.scheduler, timeout = 5.minutes) {
        val script = Script()
        val vm = newVm(script)
        vm.initialize(SetupWizardMode.TRAINING_ONLY)
        awaitUntil(vm, "wizard cargado") { !it.isLoading }
        vm.update { it.withCandidateInputs() }
        val listed = awaitUntil(vm, "lista inicial de candidatos") { isIdle(it) && it.availablePlanCandidates.size >= 2 }
        val dropped = listed.availablePlanCandidates.first().id
        assertNotNull(vm.readyWeekSnapshotFor(dropped))

        // Con 45 minutos el primer plan ya no cabe: el barrido nuevo lo deja fuera y sus respuestas cambiaron.
        script.failure = { draft -> if (draft.selectedCatalogId == dropped && draft.minutesPerSession == 45) timeBudget(80) else null }
        vm.update { it.copy(minutesPerSession = 45) }
        val after = awaitUntil(vm, "barrido con 45 minutos") {
            isIdle(it) && it.draft.minutesPerSession == 45 && it.availablePlanCandidates.isNotEmpty() &&
                it.availablePlanCandidates.none { candidate -> candidate.id == dropped }
        }

        assertNull("el plan que ya no encaja no enseña semana", vm.readyWeekSnapshotFor(dropped))
        val stillViable = after.planCandidates.first().id
        assertNotNull("los demás siguen con su semana", vm.readyWeekSnapshotFor(stillViable))
    }

    @Test
    fun theBodyweightPassHandsOverTheWeekOfTheAdaptedPlan() = runTest(dispatcher.scheduler, timeout = 5.minutes) {
        val script = Script()
        // Con el material pedido ningún plan se puede ejecutar; el pase a peso corporal sí.
        script.failure = { draft -> if (isBodyweightPass(draft)) null else apparatusAbsent() }
        val vm = newVm(script)
        vm.initialize(SetupWizardMode.TRAINING_ONLY)
        awaitUntil(vm, "wizard cargado") { !it.isLoading }
        vm.update { it.withCandidateInputs() }
        val state = awaitUntil(vm, "barrido con pase corporal") { isIdle(it) && it.availablePlanCandidates.isNotEmpty() }

        assertTrue("las tarjetas salen del pase a peso corporal", state.planAdaptedToBodyweight)
        state.planCandidates.forEach { candidate ->
            val snapshot = requireNotNull(vm.readyWeekSnapshotFor(candidate.id)) { "${candidate.id}: sin semana lista" }
            assertFalse(snapshot.sessions.isEmpty())
        }
    }

    @Test
    fun theSnapshotNeverMaterializesAnything() = runTest(dispatcher.scheduler, timeout = 5.minutes) {
        val script = Script()
        val vm = newVm(script)
        vm.initialize(SetupWizardMode.TRAINING_ONLY)
        awaitUntil(vm, "wizard cargado") { !it.isLoading }
        vm.update { it.withCandidateInputs() }
        val listed = awaitUntil(vm, "lista inicial de candidatos") { isIdle(it) && it.availablePlanCandidates.isNotEmpty() }
        val callsAfterSweep = script.calls

        repeat(3) { listed.planCandidates.forEach { vm.readyWeekSnapshotFor(it.id) } }
        advanceUntilIdle()

        assertEquals("leer la semana no evalúa ningún plan más", callsAfterSweep, script.calls)
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Arnés (el mismo patrón que SetupWizardCandidateGateTest)
    // ═════════════════════════════════════════════════════════════════════════

    private fun newVm(materializer: SetupWizardMaterializer): SetupWizardViewModel =
        SetupWizardViewModel(
            app,
            SavedStateHandle(),
            persistence = InMemoryPersistence(),
            environment = FixedSettingsEnvironment(Settings()),
            materializeOverride = materializer,
        ).also { store.put("ready-week-vm-${vmCounter++}", it) }

    /** Entradas completas de candidato: Músculo, 3 días, 60 min, mancuernas declaradas de forma explícita. */
    private fun SetupWizardDraft.withCandidateInputs(): SetupWizardDraft = copy(
        includeTraining = true,
        programRoute = SetupProgramRoute.CUSTOMIZABLE,
        trainingPath = SetupTrainingPath.PERSONALIZE,
        goal = SetupGoal.MUSCLE,
        experience = SetupExperience.INTERMEDIATE,
        focus = SetupFocus.FULL_BODY,
        daysPerWeek = 3,
        selectedWeekdays = setOf(1, 3, 5),
        minutesPerSession = 60,
        trainingEnvironment = "gym",
        trainingOptions = trainingOptions.copy(
            availability = EquipmentAvailability(categories = setOf(EquipmentCategory.DUMBBELLS)),
        ),
    )

    private fun isIdle(state: SetupWizardState): Boolean =
        !state.isLoading && !state.isCommitting && !state.isSubmittingAnswer && !state.isSavingAndExiting &&
            state.machineState != WizChatMachineState.PersistingAnswer &&
            state.machineState != WizChatMachineState.Committing &&
            state.machineState != WizChatMachineState.PreparingPreview &&
            !state.isPreviewLoading && !state.isCandidateLoading && !state.ringsPreviewLoading

    private fun TestScope.awaitUntil(
        vm: SetupWizardViewModel,
        what: String,
        timeoutMs: Long = AWAIT_BUDGET_MS,
        condition: (SetupWizardState) -> Boolean,
    ): SetupWizardState {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            advanceUntilIdle()
            val state = vm.state.value
            if (condition(state)) return state
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("timeout esperando: $what | ${stateDump(state)}")
            }
            Thread.sleep(5)
        }
    }

    private fun stateDump(state: SetupWizardState): String =
        "machine=${state.machineState} errores=${state.errors} previewError=${state.previewError} " +
            "cargando=${state.isPreviewLoading}/${state.isCandidateLoading} " +
            "candidatos=${state.availablePlanCandidates.map { it.id }} " +
            "rechazos=${state.candidateRejections.map { "${it.planId}:${it.reasonCode}" }}"

    /** Puerto de materialización guionado: todo plan queda listo con el mismo programa, salvo lo que [failure] rechace. */
    private class Script : SetupWizardMaterializer {
        @Volatile
        var failure: (SetupWizardDraft) -> Throwable? = { null }

        private val counter = java.util.concurrent.atomic.AtomicInteger()

        val calls: Int get() = counter.get()

        override suspend fun materialize(draft: SetupWizardDraft): SetupPreview {
            counter.incrementAndGet()
            failure(draft)?.let { throw it }
            return SetupPreview(cannedProgram(draft.commitId), null)
        }
    }

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

    private companion object {
        const val AWAIT_BUDGET_MS = 90_000L

        fun timeBudget(minutes: Int) = PlanMaterializationException(
            PlanEvaluationStage.SESSION_DURATION,
            PlanRejectionReason.TIME_BUDGET,
            "Este plan necesita $minutes min por sesión.",
            requiredMinutes = minutes,
        )

        fun apparatusAbsent() = PlanMaterializationException(
            PlanEvaluationStage.MATERIAL,
            PlanRejectionReason.APPARATUS_ABSENT,
            "Esta receta necesita material que no has declarado: barbell",
            missingRequirements = listOf("barbell"),
        )

        /** El segundo pase evalúa el borrador adaptado: categorías de material vacías (solo peso corporal). */
        fun isBodyweightPass(draft: SetupWizardDraft): Boolean =
            draft.trainingOptions.availability?.categories?.isEmpty() == true

        /** Programa preparado mínimo y ejecutable: un día, un ejercicio de tres series de 8 (el patrón de `SetupWizardCandidateGateTest`). */
        fun cannedProgram(id: String): Program {
            val exercise = Exercise(
                id = "$id-ex",
                name = "Press de banca",
                sets = (1..3).map { ExerciseSet(id = "$id-set-$it", targetReps = 8, weight = 20.0) },
                restTime = 90,
            )
            val session = Session(
                id = "$id-session",
                name = "Día 1",
                exercises = listOf(exercise),
                dayOfWeek = 1,
                assignedDays = listOf(1),
            )
            val week = ProgramWeek("$id-week", "Semana", sessions = listOf(session))
            val mesocycle = Mesocycle("$id-meso", "Meso", weeks = listOf(week))
            val block = Block("$id-block", "Bloque", mesocycles = listOf(mesocycle))
            return Program(
                id = id,
                name = "Plan preparado",
                startDay = 1,
                weekDays = 1,
                macrocycles = listOf(Macrocycle("$id-macro", "Macro", blocks = listOf(block))),
                schedulePlan = ProgramSchedulePlan(weekStartDay = 1, trainingDays = setOf(1)),
            )
        }
    }
}

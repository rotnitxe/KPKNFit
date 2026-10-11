package com.example.kpkn.screens.onboarding

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toProgram
import com.example.kpkn.data.exercises.catalogv2.CatalogV2ProcessCache
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.onboarding.SetupCommitCoordinator
import com.example.kpkn.data.onboarding.SetupCommitRequest
import com.example.kpkn.data.onboarding.SetupCommitResult
import com.example.kpkn.data.onboarding.SetupDraft
import com.example.kpkn.data.onboarding.SetupDraftCandidate
import com.example.kpkn.data.onboarding.SetupDraftRepository
import com.example.kpkn.data.onboarding.SetupDraftResolver
import com.example.kpkn.data.protocols.PlanProvenanceClass
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.entitlements.PlanLevel
import com.example.kpkn.domain.onboarding.CapabilityLevel
import com.example.kpkn.domain.onboarding.CompetitionAnswer
import com.example.kpkn.domain.onboarding.LiftMark
import com.example.kpkn.domain.onboarding.MuscleSymbol
import com.example.kpkn.domain.onboarding.ProgramSource
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.domain.onboarding.WizChatMachineState
import com.example.kpkn.domain.training.ProgramExecutionContract
import com.example.kpkn.domain.training.importer.Change
import com.example.kpkn.domain.training.importer.ImportExercisePatch
import com.example.kpkn.screens.programs.importer.ImportEditOp
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.minutes

/**
 * Alta con «Tengo experiencia» → «Importar mi programa», con el ViewModel real, persistencia y coordinador de altas sobre
 * Room en memoria (nivel Pro). Un texto pegado de dos días se importa, los dudosos se deciden, PROGRAM_IMPORT y WEEK_LAYOUT
 * se confirman y el programa activado tiene esos días, sus ejercicios y la procedencia «importado».
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class ImportActivationWizardTest {

    private val dispatcher = StandardTestDispatcher()
    private val viewModelStore = ViewModelStore()
    private lateinit var app: Application
    private lateinit var db: KpknDatabase
    private var vmCounter = 0

    /** Dos días de entreno: el primero con dos ejercicios, el segundo con otros dos. */
    private val pasted = """
        Día 1 — Pierna
        Sentadilla 4x6 @120kg
        Prensa 3x10
        Día 2 — Empuje
        Press banca 4x8 @80kg
        Press militar 3x10
    """.trimIndent()

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

    @Test
    fun anAdvancedDraftImportsAPastedProgramOfTwoDaysAndActivatesIt() = runTest(dispatcher.scheduler, timeout = 10.minutes) {
        val vm = newVm()
        vm.initialize(SetupWizardMode.TRAINING_ONLY)
        await(vm, "wizard cargado") { !it.isLoading && it.currentStep == SetupStepId.NAME }
        val answers = Answers(
            profile = TrainingGoalProfile.STRENGTH_MUSCLE,
            places = setOf(TrainingPlace.GYM),
            days = setOf(1, 4),
            freshDay = 1,
            minutes = 60,
        )
        walkUntil(vm, SetupStepId.PROGRAM_SOURCE, answers)

        // «Tengo experiencia» → «Importar mi programa».
        vm.setProgramSource(ProgramSource.IMPORT)
        await(vm, "elección de importar guardada") { it.draft.programSource == ProgramSource.IMPORT }
        confirm(vm, SetupStepId.PROGRAM_SOURCE, SetupStepId.PROGRAM_IMPORT)

        // Texto pegado; los dudosos los decide la persona: el primero con la sugerencia del catálogo y el resto propios.
        vm.importFromText(pasted)
        await(vm, "programa leído y emparejado") { !it.importBusy && it.draft.importedProgram != null }
        vm.state.value.draft.importUndecidedNames().forEachIndexed { index, rawName ->
            val configId = if (index == 0) vm.importSuggestions(rawName).firstOrNull()?.configurationId else null
            vm.resolveImportedExercise(rawName, configId)
        }
        await(vm, "decisiones guardadas") { it.draft.importUndecidedNames().isEmpty() }
        vm.setImportTitle("Plan de prueba")
        await(vm, "título guardado") { it.draft.importTitle == "Plan de prueba" }
        confirm(vm, SetupStepId.PROGRAM_IMPORT, SetupStepId.WEEK_LAYOUT)

        // La semana armada reparte las dos sesiones en los dos días elegidos.
        await(vm, "semana previsualizada") { idle(it) && it.programPreview != null && it.weekLayout != null }
        confirm(vm, SetupStepId.WEEK_LAYOUT, SetupStepId.MILESTONE_TRAINING)
        confirm(vm, SetupStepId.MILESTONE_TRAINING, SetupStepId.REVIEW_ACTIVATE)
        val shown = checkNotNull(await(vm, "revisión en reposo") { idle(it) && it.programPreview != null }.programPreview)
        assertEquals("Plan de prueba", shown.name)
        assertEquals(PlanProvenanceClass.IMPORTED, shown.planProvenance?.category)

        assertNotNull("alta sin errores: ${vm.state.value.errors}", vm.commit())
        assertEquals(WizChatMachineState.Committed, vm.state.value.machineState)

        val saved = checkNotNull(room { db.programDao().getById(shown.id) }) { "programa no guardado" }.toProgram()
        assertEquals("Plan de prueba", saved.name)
        assertEquals(PlanProvenanceClass.IMPORTED, saved.planProvenance?.category)
        ProgramExecutionContract.requireExecutable(saved)

        // Dos días de entreno, con sus ejercicios y sus series en el orden del texto.
        val sessions = sessionsOf(saved)
        assertEquals(2, sessions.size)
        assertEquals(setOf(1, 4), sessions.mapNotNull { it.dayOfWeek }.toSet())
        assertEquals(listOf(listOf(4, 3), listOf(4, 3)), sessions.map { session -> session.exercises.map { it.sets.size } })

        // Cada ejercicio: el propio conserva su nombre; el decidido lleva la configuración del catálogo elegida.
        val decisions = vm.state.value.draft.importResolutions
        val rawNames = vm.state.value.draft.importedProgram!!.weeks.flatMap { week -> week.days.flatMap { day -> day.exercises } }
            .map { it.rawName }
        val exercises = sessions.flatMap { it.exercises }
        assertEquals(rawNames.size, exercises.size)
        rawNames.zip(exercises).forEach { (rawName, exercise) ->
            val configId = decisions[rawName]
            if (configId == null) {
                assertEquals("«$rawName» como ejercicio propio conserva su nombre", rawName, exercise.name)
                assertEquals(null, exercise.catalogConfigurationId)
            } else {
                assertEquals("«$rawName» decidido lleva su configuración", configId, exercise.catalogConfigurationId)
            }
        }
    }

    // ── Arnés: el mismo recorrido que el de SetupWizardEntrenoPlanTest, con «Tengo experiencia» ─────────────────────

    private data class Answers(
        val profile: TrainingGoalProfile,
        val places: Set<TrainingPlace>,
        val days: Set<Int>,
        val freshDay: Int,
        val minutes: Int,
        val muscles: Set<MuscleSymbol> = emptySet(),
        val marks: Map<LiftMark, Double> = emptyMap(),
    )

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
            SetupStepId.EXPERIENCE -> vm.setStepChoice(SetupStepId.EXPERIENCE, "advanced")
            SetupStepId.EQUIPMENT -> vm.updateStep(SetupStepId.EQUIPMENT) { it.withPlaces(answers.places) }
            SetupStepId.GOAL -> vm.setGoalProfile(answers.profile)
            SetupStepId.COMPETITION -> vm.setCompetition(CompetitionAnswer.NO)
            SetupStepId.FRESH_DAY -> vm.setFreshDay(answers.freshDay)
            SetupStepId.WEEKDAYS -> vm.updateStep(SetupStepId.WEEKDAYS) { draft -> draft.withWeekdays(answers.days) }
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
                    "Timeout esperando $what en ${state.currentStep}: máquina=${state.machineState} " +
                        "errores=${state.errors} preview=${state.previewError} cargando=${state.isPreviewLoading}/${state.isCandidateLoading}",
                )
            }
            Thread.sleep(10)
        }
    }

    private fun rest(state: SetupWizardState): Boolean =
        !state.isLoading && !state.isCommitting && !state.isSubmittingAnswer && !state.isSavingAndExiting &&
            state.machineState != WizChatMachineState.PersistingAnswer &&
            state.machineState != WizChatMachineState.Committing

    private fun idle(state: SetupWizardState): Boolean =
        rest(state) && state.machineState != WizChatMachineState.PreparingPreview &&
            !state.isPreviewLoading && !state.isCandidateLoading

    /**
     * La vista previa del alta: cada edición deja su texto para deshacer, deshacer devuelve el borrador tal como estaba, la
     * pila guarda 30 pasos y «Empezar de nuevo» (clearImport) la vacía.
     */
    @Test
    fun editsOfTheImportedProgramCanBeUndoneAndClearingEmptiesTheStack() = runTest(dispatcher.scheduler, timeout = 10.minutes) {
        val vm = newVm()
        vm.initialize(SetupWizardMode.TRAINING_ONLY)
        await(vm, "wizard cargado") { !it.isLoading && it.currentStep == SetupStepId.NAME }
        vm.importFromText(pasted)
        await(vm, "programa leído") { !it.importBusy && it.draft.importedProgram != null }
        val original = vm.state.value.draft.importedProgram!!
        val squat = original.weeks[0].days[0].exercises[0]

        vm.editImport(ImportEditOp.Update(squat.id, ImportExercisePatch(sets = Change.Set(5)), allWeeks = false))
        await(vm, "edición guardada") { it.draft.importedProgram?.weeks?.first()?.days?.first()?.exercises?.first()?.sets == 5 }
        assertEquals("Cambiaste «Sentadilla».", vm.state.value.importUndoLabel)

        vm.undoImportEdit()
        await(vm, "deshecho") { it.draft.importedProgram == original }
        assertNull(vm.state.value.importUndoLabel)

        // 31 cambios: la pila conserva 30; deshacer 30 veces vuelve a la primera edición, y ya no hay nada más que deshacer.
        repeat(31) { step ->
            vm.editImport(ImportEditOp.Update(squat.id, ImportExercisePatch(sets = Change.Set(if (step % 2 == 0) 5 else 6)), allWeeks = false))
        }
        await(vm, "31 ediciones") { idle(it) && it.importUndoLabel != null }
        repeat(30) { vm.undoImportEdit() }
        await(vm, "30 deshacer") { idle(it) && it.importUndoLabel == null }
        assertEquals(5, vm.state.value.draft.importedProgram!!.weeks.first().days.first().exercises.first().sets)
        vm.undoImportEdit()
        advanceUntilIdle()
        assertEquals(5, vm.state.value.draft.importedProgram!!.weeks.first().days.first().exercises.first().sets)

        // «Empezar de nuevo» vacía lo importado y la pila: deshacer ya no hace nada.
        vm.editImport(ImportEditOp.Update(squat.id, ImportExercisePatch(sets = Change.Set(7)), allWeeks = false))
        await(vm, "edición antes de empezar de nuevo") { it.importUndoLabel != null }
        vm.clearImport()
        await(vm, "importación quitada") { it.draft.importedProgram == null && it.importUndoLabel == null }
        vm.undoImportEdit()
        advanceUntilIdle()
        assertNull(vm.state.value.draft.importedProgram)
        assertNull(vm.state.value.importUndoLabel)
    }

    private fun newVm(level: PlanLevel = PlanLevel.PRO): SetupWizardViewModel =
        SetupWizardViewModel(
            app,
            SavedStateHandle(),
            persistence = RoomPersistence(db),
            environment = FixedSettingsEnvironment(Settings()),
            commits = RoomCommits(db, level),
        ).also { viewModelStore.put("import-activation-${vmCounter++}", it) }

    private fun sessionsOf(program: Program): List<Session> =
        program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }.flatMap { it.sessions }

    private fun <T> room(block: suspend () -> T): T = runBlocking { block() }

    private class RoomPersistence(db: KpknDatabase) : SetupWizardPersistence {
        private val drafts = SetupDraftRepository(db)
        private val resolver = SetupDraftResolver(db)
        override suspend fun load(draftId: String): SetupDraft? = drafts.load(draftId)
        override suspend fun save(draftId: String, payloadJson: String, revision: Long, catalogRevision: String?): SetupDraft =
            drafts.save(draftId, payloadJson, revision, catalogRevision)
        override suspend fun discard(draftId: String) = drafts.discard(draftId)
        override suspend fun listRecoverable(): List<SetupDraftCandidate> = resolver.listRecoverable()
    }

    private class RoomCommits(db: KpknDatabase, level: PlanLevel) : SetupWizardCommits {
        private val coordinator = SetupCommitCoordinator(db, accessLevel = { level })
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

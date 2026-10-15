package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.db.toProgram
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.ManualOverrideScope
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPersistedRuleDefaults
import com.example.kpkn.data.models.WeekVariant
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.DayRecipe
import com.example.kpkn.data.protocols.LiftSlot
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.percentSets
import com.example.kpkn.data.protocols.slot
import com.example.kpkn.data.protocols.weekRecipe
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.PlanMaterializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * §14.5 + AC-G2: `saveSession` guarda la sesión Y marca `ManualSessionOverride`
 * en la misma mutación; la reconstrucción conserva esa edición y «restaurar
 * desde el plan» solo retira la marca de la sesión elegida, sin tocar la
 * historia (el id de la sesión no cambia).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class SessionEditorManualOverrideTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: ProgramRepository

    companion object {
        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            CatalogCompositionTestSupport.install()
        }
    }

    @Before
    fun setup() = runBlocking {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        ProgramRepository.initForTests(context)
        repository = ProgramRepository.getInstance()
        repository.clearPrograms()
        repository.clearActiveProgram()
        repository.clearOngoingWorkout()
        withTimeout(10_000) {
            while (!repository.isReady.value) delay(25)
        }
    }

    @After
    fun tearDown() {
        ProgramRepository.closeInstance()
        runCatching { com.example.kpkn.data.db.KpknDatabase.closeInstance() }
        Dispatchers.resetMain()
    }

    /** Identidad materializada §14.4 para la semana 1 / ocurrencia 1 / ciclo 1. */
    private fun stableId(programId: String, dayId: String): String =
        com.example.kpkn.domain.exercises.stableRecipeElementId(
            programId = programId,
            recipeId = "freeze-recipe",
            contentVersion = 1,
            weekOccurrence = 1,
            weekNumber = 1,
            dayId = dayId,
        )

    private fun recipe(): TrainingPlanRecipe = TrainingPlanRecipe(
        id = "freeze-recipe",
        weeks = listOf(
            weekRecipe(1, 0, "Base", BlockGoal.ACCUMULATION, listOf(day("day_a", 1, "Día plan A"), day("day_b", 3, "Día plan B"))),
        ),
        claimedDaysPerWeek = 2,
    )

    private fun day(dayId: String, weekday: Int, label: String): DayRecipe = DayRecipe(
        id = dayId,
        label = label,
        weekday = weekday,
        slots = listOf(
            slot(
                "bp",
                SlotRole.T1_MAIN,
                CatalogIds.BP,
                percentSets(150, 5 to 75.0, 5 to 75.0),
                150,
                LiftSlot.BENCH,
                isCompetitionLift = true,
            ),
        ),
    )

    private fun session(id: String, dayOfWeek: Int, name: String, main: Boolean): Session = Session(
        id = id,
        name = name,
        dayOfWeek = dayOfWeek,
        isMainSession = main,
        exercises = listOf(
            Exercise(
                id = "$id-ex",
                name = "Press banca",
                catalogConfigurationId = CatalogIds.BP,
                exerciseId = CatalogIds.BP,
                recipeDayId = if (dayOfWeek == 1) "day_a" else if (dayOfWeek == 3) "day_b" else null,
                sets = listOf(ExerciseSet(id = "$id-set", targetReps = 5, targetRPE = 7.0)),
            ),
        ),
    )

    private fun programWithSessionAndRecipe(programId: String): Program = Program(
        id = programId,
        name = "Freeze",
        structure = ProgramStructure.SIMPLE,
        sourceRecipe = recipe(),
        macrocycles = listOf(
            Macrocycle(
                id = "macro",
                name = "Macro",
                blocks = listOf(
                    Block(
                        id = "block",
                        name = "Block",
                        mesocycles = listOf(
                            Mesocycle(
                                id = "meso",
                                name = "Meso",
                                weeks = listOf(
                                    ProgramWeek(
                                        id = "week",
                                        name = "Semana",
                                        progressionIndex = 1,
                                        sessions = listOf(
                                            session(stableId(programId, "day_a"), 1, "Día plan A", main = true),
                                            session(stableId(programId, "day_b"), 3, "Día plan B", main = false),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    private fun createViewModel(programId: String, sessionId: String): SessionEditorViewModel =
        SessionEditorViewModel(
            application = ApplicationProvider.getApplicationContext<Application>(),
            programId = programId,
            sessionId = sessionId,
            draftWeekId = "week",
            draftMacroIndex = 0,
            draftMesoIndex = 0,
            draftDayOfWeek = null,
        )

    private suspend fun awaitSession(vm: SessionEditorViewModel) = vm.awaitSessionLoaded()

    private suspend fun awaitDraftWithTransfer(
        vm: SessionEditorViewModel,
        sessionId: String,
    ): PersistedSessionEditorDraft = withTimeout(5_000) {
        while (true) {
            vm.persistedDraftFor("week", 0, 0, sessionId)
                ?.takeIf { it.pendingTransferToDays != null }
                ?.let { return@withTimeout it }
            delay(20)
        }
        error("unreachable")
    }

    private fun weekOf(program: Program, weekId: String): ProgramWeek =
        program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }
            .first { it.id == weekId }

    // ─── Global rule extras (scope, RIR/intensity type, compound/isolation) ───────────────

    /** Every one of the ten extras is non-default; none of them is stored by Room. */
    private val richExtras = SessionEditorGlobalRuleExtras(
        scope = RuleScope.COMPOUND_ISOLATION,
        intensityType = DefaultIntensityType.RIR,
        compoundRestSeconds = 180,
        compoundReps = 5,
        compoundRpe = 8.5,
        compoundIntensityType = DefaultIntensityType.RPE,
        isolationRestSeconds = 60,
        isolationReps = 12,
        isolationRpe = 2.0,
        isolationIntensityType = DefaultIntensityType.RIR,
    )

    private fun draftStore(): SessionEditorDraftStore =
        SessionEditorDraftStore.getInstance(ApplicationProvider.getApplicationContext<Context>())

    private suspend fun readRecord(programId: String, sessionId: String): SessionEditorRulePreferences? =
        draftStore().readRulePreferences(sessionEditorRulePreferencesStorageKey(programId, sessionId))

    private suspend fun roomProgram(programId: String): Program =
        repository.databaseForTests().programDao().getById(programId)?.toProgram()
            ?: error("programa Room ausente: $programId")

    private suspend fun roomSession(programId: String, sessionId: String, weekId: String = "week"): Session =
        weekOf(roomProgram(programId), weekId).sessions.first { it.id == sessionId }

    /**
     * A change made behind the editor's back (another screen, a plan rebuild): it lands in Room with
     * a timestamp ahead of any editor draft written so far, so Room's copy is the newest one.
     */
    private suspend fun setRoomCore(
        programId: String,
        sessionId: String,
        transform: (SessionPersistedRuleDefaults?) -> SessionPersistedRuleDefaults?,
    ) {
        val committed = repository.mutateProgramNow(programId) { current ->
            current.updateWeekSessions(0, 0, "week") { sessions ->
                sessions.map { existing ->
                    if (existing.id != sessionId) existing else existing.copy(
                        persistedRuleDefaults = transform(existing.persistedRuleDefaults),
                        lastModifiedAtMs = System.currentTimeMillis() + 1_000L,
                    )
                }
            }
        }
        assertTrue("el cambio externo debe confirmarse en Room", committed)
    }

    private suspend fun <T> withFailingRulePreferenceWrite(
        programId: String,
        sessionId: String,
        block: suspend () -> T,
    ): T {
        SessionEditorDraftStore.rulePreferencesWriteOverrideForTests = { key, _ ->
            if (key == sessionEditorRulePreferencesStorageKey(programId, sessionId)) false else null
        }
        try {
            return block()
        } finally {
            SessionEditorDraftStore.rulePreferencesWriteOverrideForTests = null
        }
    }

    private suspend fun awaitDraftWrite(programId: String, sessionId: String) {
        val key = sessionEditorDraftStorageKey(programId, "week", 0, 0, sessionId)
        val outcome = withTimeout(5_000) { draftStore().writer.awaitLatest(key) }
        assertEquals(DraftWriteStatus.WRITTEN, outcome?.status)
    }

    private val compoundInfo = ExerciseMuscleInfo(id = "prefs-compound", name = "Compuesto", articulationType = "MULTIARTICULAR")
    private val isolationInfo = ExerciseMuscleInfo(id = "prefs-isolation", name = "Aislado", articulationType = "AISLADO")

    private fun mesocycleProgram(programId: String, firstId: String, cloneId: String): Program {
        fun sessionOn(id: String) = Session(
            id = id,
            name = "Día 1",
            dayOfWeek = 1,
            isMainSession = true,
            exercises = listOf(
                Exercise(
                    id = "$id-ex",
                    name = "Press banca",
                    catalogConfigurationId = CatalogIds.BP,
                    exerciseId = CatalogIds.BP,
                    sets = listOf(ExerciseSet(id = "$id-set", targetReps = 5, targetRPE = 7.0)),
                ),
            ),
        )
        return Program(
            id = programId,
            name = "Mesociclo",
            structure = ProgramStructure.COMPLEX,
            macrocycles = listOf(
                Macrocycle(
                    id = "macro",
                    name = "Macro",
                    blocks = listOf(
                        Block(
                            id = "block",
                            name = "Block",
                            mesocycles = listOf(
                                Mesocycle(
                                    id = "meso",
                                    name = "Meso",
                                    weeks = listOf(
                                        ProgramWeek(id = "week", name = "Semana 1", progressionIndex = 1, sessions = listOf(sessionOn(firstId))),
                                        ProgramWeek(id = "week2", name = "Semana 2", progressionIndex = 2, sessions = listOf(sessionOn(cloneId))),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun saveSession_marks_the_manual_override_in_the_same_mutation_and_rebuild_preserves_it() = runBlocking {
        val programId = "program-freeze"
        val sessionId = stableId(programId, "day_a")
        val otherId = stableId(programId, "day_b")
        repository.addProgram(programWithSessionAndRecipe(programId))
        val vm = createViewModel(programId, sessionId)
        awaitSession(vm)

        vm.updateSession { it.copy(name = "Editada a mano", description = "cambios del usuario") }
        val result = vm.saveSession()
        assertTrue("Guardado: ${result.message}", result.success)

        val saved = repository.getProgramById(programId) ?: error("programa ausente")
        val override = saved.manualSessionOverrides.firstOrNull { it.sessionId == sessionId }
        assertNotNull("saveSession congela la sesión en la MISMA mutación", override)
        assertEquals(ManualOverrideScope.SESSION, override!!.scope)
        assertEquals("week", override.weekId)
        assertEquals("Editada a mano", weekOf(saved, "week").sessions.first { it.id == sessionId }.name)

        // AC-G2: la reconstrucción conserva la edición congelada…
        val rebuilt = PlanMaterializer.rematerializeWeek(
            saved,
            "week",
            saved.sourceRecipe!!,
            CatalogCompositionTestSupport.metadata,
        )
        val rebuiltWeek = weekOf(rebuilt, "week")
        assertEquals(2, rebuiltWeek.sessions.size)
        val preserved = rebuiltWeek.sessions.first { it.id == sessionId }
        assertEquals("Editada a mano", preserved.name)
        assertEquals("cambios del usuario", preserved.description)
        // …mientras la sesión sin editar vuelve a la receta con su id estable.
        assertEquals("Día plan B", rebuiltWeek.sessions.first { it.id == otherId }.name)

        // Restaurar desde el plan: solo esa sesión/ocurrencia y sin borrar historia.
        val restored = PlanMaterializer.removeManualSessionOverride(saved, sessionId)
        assertTrue(restored.manualSessionOverrides.isEmpty())
        val rebuiltRestored = PlanMaterializer.rematerializeWeek(
            restored,
            "week",
            restored.sourceRecipe!!,
            CatalogCompositionTestSupport.metadata,
        )
        val restoredWeek = weekOf(rebuiltRestored, "week")
        val reverted = restoredWeek.sessions.first { it.id == sessionId }
        assertEquals("Día plan A", reverted.name)
        assertEquals("El id no cambia: los logs siguen apuntando a la sesión", sessionId, reverted.id)
        assertEquals("Día plan B", restoredWeek.sessions.first { it.id == otherId }.name)
    }

    @Test
    fun editorOnlyPreferencesHaveIndependentDirtyBaselineSaveDiscardAndRecreation() = runBlocking {
        val programId = "editor-local-preferences"
        val sessionId = stableId(programId, "day_a")
        assertTrue(repository.addProgramNow(programWithSessionAndRecipe(programId)).isSuccess)
        val vm = createViewModel(programId, sessionId)
        awaitSession(vm)
        val originalRoomProgram = repository.databaseForTests().programDao().getById(programId)?.toProgram()
            ?: error("programa ausente antes de editar preferencias")
        val committedDefaults = mapOf("part-a" to SessionEditorRuleDefaults(reps = 8, applyToNewItems = true))
        val editedDefaults = mapOf("part-a" to SessionEditorRuleDefaults(reps = 10, applyToNewItems = true))
        val editedLimits = SessionEditorRuleLimits(maxRPE = 8.5, maxExercisesPerMuscle = 4, rigidLimits = true)

        vm.updateUi { it.copy(partRuleDefaults = editedDefaults, ruleLimits = editedLimits) }
        assertTrue("las preferencias editor-only ensucian el borrador", vm.uiState.value.hasUnsavedChanges)
        SessionEditorDraftStore.rulePreferencesWriteOverrideForTests = { key, _ ->
            if (key == sessionEditorRulePreferencesStorageKey(programId, sessionId)) false else null
        }
        val failed = try {
            vm.saveSession()
        } finally {
            SessionEditorDraftStore.rulePreferencesWriteOverrideForTests = null
        }
        assertFalse("un fallo de escritura de preferencias es visible", failed.success)
        assertEquals("preferencias editor-only no mutan Room", originalRoomProgram,
            repository.databaseForTests().programDao().getById(programId)?.toProgram())
        assertTrue("el formulario conserva el cambio", vm.uiState.value.hasUnsavedChanges)
        assertEquals(editedDefaults, vm.persistedDraftFor("week", 0, 0, sessionId)?.partRuleDefaults)

        val pendingAfterRecreation = createViewModel(programId, sessionId)
        awaitSession(pendingAfterRecreation)
        assertEquals(editedDefaults, pendingAfterRecreation.uiState.value.partRuleDefaults)
        assertEquals("baseline vacío permanece sin confirmar", emptyMap<String, SessionEditorRuleDefaults>(), pendingAfterRecreation.uiState.value.savedPartRuleDefaults)
        assertTrue("la preferencia no guardada sigue sucia después de recrear el VM", pendingAfterRecreation.uiState.value.hasUnsavedChanges)
        val retry = pendingAfterRecreation.saveSession()
        assertTrue("reintento de preferencias: ${retry.message}", retry.success)
        assertFalse(pendingAfterRecreation.uiState.value.hasUnsavedChanges)
        assertEquals("el guardado pref-only no crea override ni toca el programa", originalRoomProgram,
            repository.databaseForTests().programDao().getById(programId)?.toProgram())

        val reopened = createViewModel(programId, sessionId)
        awaitSession(reopened)
        assertEquals(editedDefaults, reopened.uiState.value.partRuleDefaults)
        assertEquals(editedDefaults, reopened.uiState.value.savedPartRuleDefaults)
        assertEquals(editedLimits, reopened.uiState.value.ruleLimits)
        assertEquals(editedLimits, reopened.uiState.value.savedRuleLimits)
        assertFalse("la lectura normalizada restaura un estado limpio", reopened.uiState.value.hasUnsavedChanges)

        reopened.updateUi {
            it.copy(
                partRuleDefaults = committedDefaults,
                ruleLimits = SessionEditorRuleLimits(maxRPE = 9.0),
            )
        }
        assertTrue(reopened.saveDraftForExitAndAwait())
        assertTrue(reopened.discardDraftForCurrentSessionAndAwait())
        assertEquals(editedDefaults, reopened.uiState.value.partRuleDefaults)
        assertEquals(editedLimits, reopened.uiState.value.ruleLimits)
        assertFalse("descartar restaura el baseline persistido", reopened.uiState.value.hasUnsavedChanges)
        val afterDiscard = createViewModel(programId, sessionId)
        awaitSession(afterDiscard)
        assertEquals(editedDefaults, afterDiscard.uiState.value.partRuleDefaults)
        assertEquals(editedLimits, afterDiscard.uiState.value.ruleLimits)
        assertFalse(afterDiscard.uiState.value.hasUnsavedChanges)
    }

    @Test
    fun stagedAppendTransferSurvivesViewModelRecreationAndIsAppliedOnce() = runBlocking {
        val programId = "editor-transfer-recreate"
        val sessionId = stableId(programId, "day_a")
        val original = programWithSessionAndRecipe(programId)
        assertTrue(repository.addProgramNow(original).isSuccess)
        val first = createViewModel(programId, sessionId)
        awaitSession(first)
        first.updateSession { it.copy(name = "Fuente recuperada") }
        val targetKey = first.uiState.value.cloneDayOptions.single { it.dayOfWeek == 3 }.key
        assertTrue(first.cloneCurrentSessionToTargets(setOf(targetKey), null, SessionCloneApplyMode.APPEND).success)
        val staged = awaitDraftWithTransfer(first, sessionId)
        val transferId = requireNotNull(staged.pendingTransferToDays).transferId
        assertEquals("el contenido cambia también queda en el mismo borrador", "Fuente recuperada", staged.session.name)

        val recreated = createViewModel(programId, sessionId)
        awaitSession(recreated)
        assertEquals(transferId, recreated.uiState.value.pendingTransferToDays?.transferId)
        assertEquals("Fuente recuperada", recreated.uiState.value.session?.name)
        val saved = recreated.saveSession()
        assertTrue("guardado tras recrear VM: ${saved.message}", saved.success)

        val once = repository.databaseForTests().programDao().getById(programId)?.toProgram()
            ?: error("programa ausente tras transferencia")
        val targetId = stableId(programId, "day_b")
        assertEquals(2, weekOf(once, "week").sessions.single { it.id == targetId }.allExercises().size)
        assertEquals(1, once.manualSessionOverrides.count { it.sessionId == targetId && "editor-transfer:$transferId" in it.reason })
        assertNull(recreated.persistedDraftFor("week", 0, 0, sessionId))

        val secondSave = recreated.saveSession()
        assertTrue(secondSave.message, secondSave.success)
        val twice = repository.databaseForTests().programDao().getById(programId)?.toProgram()
            ?: error("programa ausente tras no-op")
        assertEquals("el no-op no vuelve a añadir sets", once, twice)
        assertEquals(2, weekOf(twice, "week").sessions.single { it.id == targetId }.allExercises().size)
    }

    @Test
    fun preferenceFailureAfterRoomTransferCommitLeavesTransferClearedDraftAndRetryDoesNotAppend() = runBlocking {
        val programId = "editor-transfer-pref-retry"
        val sessionId = stableId(programId, "day_a")
        assertTrue(repository.addProgramNow(programWithSessionAndRecipe(programId)).isSuccess)
        val vm = createViewModel(programId, sessionId)
        awaitSession(vm)
        vm.updateSession { it.copy(name = "Guardado con preferencias pendientes") }
        val changedDefaults = mapOf("part-a" to SessionEditorRuleDefaults(reps = 9))
        vm.updateUi { it.copy(partRuleDefaults = changedDefaults, ruleLimits = SessionEditorRuleLimits(maxRPE = 8.0)) }
        val targetKey = vm.uiState.value.cloneDayOptions.single { it.dayOfWeek == 3 }.key
        assertTrue(vm.cloneCurrentSessionToTargets(setOf(targetKey), null, SessionCloneApplyMode.APPEND).success)
        val transferId = requireNotNull(vm.uiState.value.pendingTransferToDays).transferId
        awaitDraftWithTransfer(vm, sessionId)

        SessionEditorDraftStore.rulePreferencesWriteOverrideForTests = { key, _ ->
            if (key == sessionEditorRulePreferencesStorageKey(programId, sessionId)) false else null
        }
        val committedWithPreferenceFailure = try {
            vm.saveSession()
        } finally {
            SessionEditorDraftStore.rulePreferencesWriteOverrideForTests = null
        }
        assertFalse("no debe navegar al salir mientras el editor pref-only requiera retry: ${committedWithPreferenceFailure.message}", committedWithPreferenceFailure.success)
        assertTrue("Room confirmó la edición aunque la preferencia siga pendiente", committedWithPreferenceFailure.message.contains("guardado en Room"))
        assertTrue("per-session preferences remain dirty", vm.uiState.value.hasUnsavedChanges)
        assertNull("committed transfer is removed from the recoverable draft", vm.uiState.value.pendingTransferToDays)
        val recovery = vm.persistedDraftFor("week", 0, 0, sessionId)
            ?: error("falta el borrador durable después del fallo de preferencias")
        assertEquals("Guardado con preferencias pendientes", recovery.session.name)
        assertNull(recovery.pendingTransferToDays)
        val committedProgram = repository.databaseForTests().programDao().getById(programId)?.toProgram()
            ?: error("programa ausente tras commit de Room")
        val targetId = stableId(programId, "day_b")
        assertEquals(2, weekOf(committedProgram, "week").sessions.single { it.id == targetId }.allExercises().size)
        assertEquals(1, committedProgram.manualSessionOverrides.count { it.sessionId == targetId && "editor-transfer:$transferId" in it.reason })

        val recreated = createViewModel(programId, sessionId)
        awaitSession(recreated)
        assertTrue("la preferencia no guardada sobrevive a la recreación", recreated.uiState.value.hasUnsavedChanges)
        assertNull("el transfer pendiente no se restaura tras el commit Room", recreated.uiState.value.pendingTransferToDays)
        val preferenceRetry = recreated.saveSession()
        assertTrue("el retry solo guarda preferencias: ${preferenceRetry.message}", preferenceRetry.success)
        assertFalse(recreated.uiState.value.hasUnsavedChanges)
        assertEquals("el segundo guardado no muta Room", committedProgram,
            repository.databaseForTests().programDao().getById(programId)?.toProgram())
        assertEquals(2, weekOf(repository.getProgramById(programId)!!, "week").sessions.single { it.id == targetId }.allExercises().size)
    }

    @Test
    fun stalePreferenceWriteRevisionCannotReplaceANewerCommit() = runBlocking {
        val store = SessionEditorDraftStore.getInstance(ApplicationProvider.getApplicationContext())
        val key = sessionEditorRulePreferencesStorageKey("editor-pref-order", "session-pref-order")
        val olderRevision = store.nextRulePreferencesWriteRevision(key)
        val older = SessionEditorRulePreferences(
            partRuleDefaults = mapOf("part-a" to SessionEditorRuleDefaults(reps = 6)),
            ruleLimits = SessionEditorRuleLimits(maxRPE = 7.0),
        )
        val newer = SessionEditorRulePreferences(
            partRuleDefaults = mapOf("part-a" to SessionEditorRuleDefaults(reps = 11)),
            ruleLimits = SessionEditorRuleLimits(maxRPE = 9.0),
        )

        val olderWriteEntered = CountDownLatch(1)
        val releaseOlderWrite = CountDownLatch(1)
        val olderResult = CompletableDeferred<Boolean>()
        SessionEditorDraftStore.rulePreferencesWriteOverrideForTests = { writeKey, _ ->
            if (writeKey == key) {
                olderWriteEntered.countDown()
                check(releaseOlderWrite.await(5, TimeUnit.SECONDS)) { "timed out waiting to release old preference write" }
            }
            null
        }
        val olderWriteJob = launch(Dispatchers.IO) {
            olderResult.complete(store.writeRulePreferences(key, older, olderRevision))
        }
        val newerRevision = try {
            assertTrue("older write entered its serialized commit section", olderWriteEntered.await(5, TimeUnit.SECONDS))
            val revision = store.nextRulePreferencesWriteRevision(key)
            releaseOlderWrite.countDown()
            assertFalse("an in-flight old write invalidated before commit must be skipped", withTimeout(5_000) {
                olderResult.await()
            })
            revision
        } finally {
            releaseOlderWrite.countDown()
            SessionEditorDraftStore.rulePreferencesWriteOverrideForTests = null
            olderWriteJob.join()
        }
        assertTrue("el token más reciente queda persistido", store.writeRulePreferences(key, newer, newerRevision))
        assertEquals(newer, store.readRulePreferences(key))
    }

    @Test
    fun legacyDraftSettingsMigrateAsCommittedBaselineAndSurviveDiscard() = runBlocking {
        val programId = "editor-legacy-preferences"
        val sessionId = stableId(programId, "day_a")
        val program = programWithSessionAndRecipe(programId)
        assertTrue(repository.addProgramNow(program).isSuccess)
        val session = weekOf(program, "week").sessions.single { it.id == sessionId }
        val key = sessionEditorDraftStorageKey(programId, "week", 0, 0, sessionId)
        val preferencesKey = sessionEditorRulePreferencesStorageKey(programId, sessionId)
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences(SESSION_EDITOR_RULE_PREFERENCES_PREFS, Context.MODE_PRIVATE)
            .edit().remove(preferencesKey).commit()
        val legacyDefaults = mapOf("part-a" to SessionEditorRuleDefaults(reps = 7, applyToNewItems = true))
        val legacyLimits = SessionEditorRuleLimits(maxRPE = 7.5, maxVolumePerMuscleWeekly = 24.0)
        val legacyDraft = PersistedSessionEditorDraft(
            programId = programId,
            sessionId = sessionId,
            weekId = "week",
            macroIndex = 0,
            mesoIndex = 0,
            dayOfWeek = 1,
            session = session,
            // Match the old ViewModel snapshot: its global defaults were
            // inferred from the saved session, independently of local prefs.
            ruleDefaults = session.persistedRuleDefaults?.let(SessionEditorRuleDefaults::fromPersisted)
                ?: session.inferredEditorRuleDefaults(),
            partRuleDefaults = legacyDefaults,
            ruleLimits = legacyLimits,
        )
        val legacyWrite = SessionEditorDraftStore.getInstance(context).writer.writeLatest(key, legacyDraft)
        assertEquals(DraftWriteStatus.WRITTEN, legacyWrite.status)

        val vm = createViewModel(programId, sessionId)
        awaitSession(vm)
        assertEquals(legacyDefaults, vm.uiState.value.partRuleDefaults)
        assertEquals(legacyDefaults, vm.uiState.value.savedPartRuleDefaults)
        assertEquals(legacyLimits, vm.uiState.value.ruleLimits)
        assertEquals(legacyDraft.ruleDefaults, vm.uiState.value.ruleDefaults)
        assertFalse("legacy preference migration alone is clean", vm.uiState.value.hasUnsavedChanges)
        // The migration now writes a v2 record: parts and limits plus the (default) confirmed extras.
        assertEquals(SessionEditorRulePreferences(legacyDefaults, legacyLimits, SessionEditorGlobalRuleExtras()),
            SessionEditorDraftStore.getInstance(context).readRulePreferences(preferencesKey))

        assertTrue(vm.discardDraftForCurrentSessionAndAwait())
        assertNull(vm.persistedDraftFor("week", 0, 0, sessionId))
        val reopened = createViewModel(programId, sessionId)
        awaitSession(reopened)
        assertEquals(legacyDefaults, reopened.uiState.value.partRuleDefaults)
        assertEquals(legacyLimits, reopened.uiState.value.ruleLimits)
        assertFalse(reopened.uiState.value.hasUnsavedChanges)
    }

    @Test
    fun real_room_write_failure_keeps_editor_transfer_and_retry_appends_once() = runBlocking {
        val programId = "editor-room-retry"
        val sourceId = stableId(programId, "day_a")
        val original = programWithSessionAndRecipe(programId)
        assertTrue(repository.addProgramNow(original).isSuccess)
        val vm = createViewModel(programId, sourceId)
        awaitSession(vm)

        vm.updateSession { it.copy(name = "Cambio conservado para reintentar") }
        val targetKey = vm.uiState.value.cloneDayOptions.single { it.dayOfWeek == 3 }.key
        assertTrue(
            vm.cloneCurrentSessionToTargets(
                targetKeys = setOf(targetKey),
                selectedExerciseIds = null,
                applyMode = SessionCloneApplyMode.APPEND,
            ).success,
        )
        val transferId = requireNotNull(vm.uiState.value.pendingTransferToDays).transferId

        repository.durableCommitBeforeWriteForTests = {
            throw IllegalStateException("fallo Room antes del upsert")
        }
        val failed = try {
            vm.saveSession()
        } finally {
            repository.durableCommitBeforeWriteForTests = null
        }

        assertFalse(failed.success)
        assertEquals("Cambio conservado para reintentar", vm.uiState.value.session?.name)
        assertTrue("el formulario sigue sucio", vm.uiState.value.hasUnsavedChanges)
        assertEquals(transferId, vm.uiState.value.pendingTransferToDays?.transferId)
        assertTrue("el error deja una acción de reintento", !vm.uiState.value.snackbarMessage.isNullOrBlank())
        assertEquals("el fallo previo al commit no publica en Room", "Freeze", repository.databaseForTests().programDao().getById(programId)?.toProgram()?.name)
        val recovery = vm.persistedDraftFor("week", 0, 0, sourceId)
        assertEquals("Cambio conservado para reintentar", recovery?.session?.name)
        assertEquals(transferId, recovery?.pendingTransferToDays?.transferId)

        val retried = vm.saveSession()
        assertTrue("Reintento durable: ${retried.message}", retried.success)
        val persisted = repository.databaseForTests().programDao().getById(programId)?.toProgram()
            ?: error("programa Room ausente tras reintento")
        val target = weekOf(persisted, "week").sessions.single { it.dayOfWeek == 3 }
        assertEquals(2, target.allExercises().size)
        assertTrue(persisted.manualSessionOverrides.any { it.sessionId == target.id && "editor-transfer:$transferId" in it.reason })
        assertNull(vm.persistedDraftFor("week", 0, 0, sourceId))
    }

    @Test
    fun postCommitExceptionBeforeCachePublication_recoversRoomAndRetryDoesNotAppendAgain() = runBlocking {
        val programId = "editor-postcommit-recovery"
        val sourceId = stableId(programId, "day_a")
        assertTrue(repository.addProgramNow(programWithSessionAndRecipe(programId)).isSuccess)
        val vm = createViewModel(programId, sourceId)
        awaitSession(vm)
        vm.updateSession { it.copy(name = "Durable aunque falle la publicación") }
        val targetKey = vm.uiState.value.cloneDayOptions.single { it.dayOfWeek == 3 }.key
        assertTrue(vm.cloneCurrentSessionToTargets(setOf(targetKey), null, SessionCloneApplyMode.APPEND).success)

        repository.durableCommitInterleaverForTests = {
            throw IllegalStateException("fallo después del upsert Room, antes de publicar caché")
        }
        val result = try {
            vm.saveSession()
        } finally {
            repository.durableCommitInterleaverForTests = null
        }

        assertTrue("Room confirmó el guardado y la recuperación reconoce el commit: ${result.message}", result.success)
        val durable = repository.databaseForTests().programDao().getById(programId)?.toProgram()
            ?: error("programa ausente en Room")
        val targetId = stableId(programId, "day_b")
        assertEquals(2, weekOf(durable, "week").sessions.single { it.id == targetId }.allExercises().size)
        assertEquals("la caché se reconcilia desde Room", durable, repository.getProgramById(programId))
        assertNull("el borrador confirmado ya no se restaura", vm.persistedDraftFor("week", 0, 0, sourceId))

        val retry = vm.saveSession()
        assertTrue(retry.message, retry.success)
        val afterRetry = repository.databaseForTests().programDao().getById(programId)?.toProgram()
            ?: error("programa ausente después del retry")
        assertEquals(2, weekOf(afterRetry, "week").sessions.single { it.id == targetId }.allExercises().size)
    }

    @Test
    fun editorRecoveryDoesNotRepublishAfterANewerProgramWriteReservation() = runBlocking {
        val programId = "editor-newer-reservation"
        val sourceId = stableId(programId, "day_a")
        assertTrue(repository.addProgramNow(programWithSessionAndRecipe(programId)).isSuccess)
        val vm = createViewModel(programId, sourceId)
        awaitSession(vm)
        vm.updateSession { it.copy(name = "Edición local pendiente") }

        repository.durableCommitInterleaverForTests = {
            val current = repository.getProgramById(programId) ?: error("programa ausente en caché")
            repository.updateProgram(current.copy(name = "Reserva más nueva"))
        }
        val result = try {
            vm.saveSession()
        } finally {
            repository.durableCommitInterleaverForTests = null
        }

        assertFalse("la reserva más reciente hace que el guardado viejo no se anuncie como durable", result.success)
        assertEquals("la recuperación no pisa la versión nueva", "Reserva más nueva", repository.getProgramById(programId)?.name)
        assertTrue("el formulario queda disponible para reintento", vm.uiState.value.hasUnsavedChanges)
        assertEquals("Edición local pendiente", vm.uiState.value.session?.name)
        assertNotNull(vm.persistedDraftFor("week", 0, 0, sourceId))
        withTimeout(5_000) {
            while (repository.databaseForTests().programDao().getById(programId)?.toProgram()?.name != "Reserva más nueva") {
                delay(20)
            }
        }
    }

    @Test
    fun cancelledCallerAfterRoomCommitStillAcknowledgesDraftAndDoesNotRepeatAppend() = runBlocking {
        val programId = "editor-cancel-after-commit"
        val sourceId = stableId(programId, "day_a")
        assertTrue(repository.addProgramNow(programWithSessionAndRecipe(programId)).isSuccess)
        val vm = createViewModel(programId, sourceId)
        awaitSession(vm)
        vm.updateSession { it.copy(name = "Guardado antes de cancelar") }
        val targetKey = vm.uiState.value.cloneDayOptions.single { it.dayOfWeek == 3 }.key
        vm.cloneCurrentSessionToTargets(setOf(targetKey), null, SessionCloneApplyMode.APPEND)

        val committedToRoom = CompletableDeferred<Unit>()
        val releaseCommit = CompletableDeferred<Unit>()
        repository.durableCommitInterleaverForTests = {
            committedToRoom.complete(Unit)
            releaseCommit.await()
        }
        val save = launch { vm.saveSession() }
        try {
            withTimeout(5_000) { committedToRoom.await() }
            save.cancel()
            releaseCommit.complete(Unit)
            withTimeout(5_000) { save.join() }
        } finally {
            releaseCommit.complete(Unit)
            repository.durableCommitInterleaverForTests = null
        }

        val persisted = repository.databaseForTests().programDao().getById(programId)?.toProgram()
            ?: error("programa Room ausente tras cancelación")
        val firstTarget = weekOf(persisted, "week").sessions.single { it.dayOfWeek == 3 }
        assertEquals(2, firstTarget.allExercises().size)
        assertNull("la escritura confirmada limpió el borrador antes de devolver control", vm.persistedDraftFor("week", 0, 0, sourceId))
        assertFalse("el editor actual reconoce el commit", vm.uiState.value.hasUnsavedChanges)
        assertNull(vm.uiState.value.pendingTransferToDays)

        repository.durableCommitInterleaverForTests = null
        val retry = vm.saveSession()
        assertTrue(retry.message, retry.success)
        val afterRetry = repository.databaseForTests().programDao().getById(programId)?.toProgram()
            ?: error("programa Room ausente tras retry")
        assertEquals(2, weekOf(afterRetry, "week").sessions.single { it.dayOfWeek == 3 }.allExercises().size)
    }

    @Test
    fun variantBTransferUsesLatestVariantAtSaveAndFreezesTheDestinationRecipeDay() = runBlocking {
        val programId = "editor-variant-b-transfer"
        val sourceId = stableId(programId, "day_a")
        assertTrue(repository.addProgramNow(programWithSessionAndRecipe(programId)).isSuccess)
        val vm = createViewModel(programId, sourceId)
        awaitSession(vm)

        assertTrue(vm.createVariant(WeekVariant.B, "Variante B"))
        vm.updateSession { it.copy(name = "B antes de preparar", exercises = it.exercises.map { ex -> ex.copy(name = "Banco B antes") }) }
        val targetKey = vm.uiState.value.cloneDayOptions.single { it.dayOfWeek == 3 }.key
        assertTrue(vm.cloneCurrentSessionToTargets(setOf(targetKey), null, SessionCloneApplyMode.REPLACE).success)
        // La transferencia se preparó; la edición B posterior debe ser la que se persista y copie.
        vm.updateSession { it.copy(name = "B guardada", exercises = it.exercises.map { ex -> ex.copy(name = "Banco B actualizado") }) }

        val result = vm.saveSession()
        assertTrue("Guardado variante B: ${result.message}", result.success)
        val persisted = repository.databaseForTests().programDao().getById(programId)?.toProgram()
            ?: error("programa Room ausente")
        val destinationId = stableId(programId, "day_b")
        val destination = weekOf(persisted, "week").sessions.single { it.id == destinationId }
        assertEquals("B guardada", destination.name)
        assertEquals("Banco B actualizado", destination.allExercises().single().name)
        val targetOverride = persisted.manualSessionOverrides.single { it.sessionId == destinationId }
        assertEquals("day_b", targetOverride.recipeDayId)
        assertEquals("week", targetOverride.weekId)
        assertEquals(1, targetOverride.weekOccurrence)
        assertEquals("B guardada", weekOf(persisted, "week").sessions.single { it.id == sourceId }.sessionB?.name)
    }

    @Test
    fun appendReplaceAndCreatedTransfersSurviveRealPlanRematerialization() = runBlocking {
        val cases = listOf(
            SessionCloneApplyMode.APPEND to false,
            SessionCloneApplyMode.REPLACE to false,
            SessionCloneApplyMode.APPEND to true,
        )
        cases.forEachIndexed { index, (mode, createDestination) ->
            val programId = "editor-rematerialize-$index"
            val base = programWithSessionAndRecipe(programId)
            val sourceId = stableId(programId, "day_a")
            val targetId = stableId(programId, "day_b")
            val prepared = base.copy(
                macrocycles = base.macrocycles.map { macro ->
                    macro.copy(
                        blocks = macro.blocks.map { block ->
                            block.copy(
                                mesocycles = block.mesocycles.map { meso ->
                                    meso.copy(
                                        weeks = meso.weeks.map { week ->
                                            if (week.id != "week") week else week.copy(
                                                sessions = week.sessions.map { session ->
                                                    when {
                                                        session.id == sourceId -> session.copy(exercises = session.exercises.map { it.copy(name = "Press fuente") })
                                                        session.id == targetId && !createDestination -> session.copy(exercises = session.exercises.map { it.copy(name = "Press destino") })
                                                        session.id == targetId -> null
                                                        else -> session
                                                    }
                                                }.filterNotNull(),
                                            )
                                        },
                                    )
                                },
                            )
                        },
                    )
                },
            )
            assertTrue(repository.addProgramNow(prepared).isSuccess)
            val source = weekOf(prepared, "week").sessions.single { it.id == sourceId }
            val targetKey = buildCloneDayOptions(prepared, sourceId).single { it.dayOfWeek == 3 }.key
            val pending = PendingTransferToDays(
                targetKeys = setOf(targetKey),
                applyMode = mode,
                sourceSession = source,
                sourceMainSessionId = sourceId,
            )
            assertTrue(repository.mutateProgramNow(programId) { current ->
                applySessionTransfersToProgram(current, sourceId, pending).freezeTransferredSessionOverrides()
            })

            val saved = repository.databaseForTests().programDao().getById(programId)?.toProgram()
                ?: error("programa Room ausente para $mode/create=$createDestination")
            val beforeRebuild = weekOf(saved, "week").sessions.single { it.dayOfWeek == 3 }
            val rebuilt = PlanMaterializer.rematerializeWeek(
                program = saved,
                weekId = "week",
                recipe = requireNotNull(saved.sourceRecipe),
                metadata = CatalogCompositionTestSupport.metadata,
            )
            val rebuiltWeek = weekOf(rebuilt, "week")
            val afterRebuild = rebuiltWeek.sessions.single { it.dayOfWeek == 3 }
            assertEquals("un destino por día tras $mode/create=$createDestination", 1, rebuiltWeek.sessions.count { it.dayOfWeek == 3 })
            assertEquals("el destino conserva identidad", beforeRebuild.id, afterRebuild.id)
            assertEquals("el contenido transferido sobrevive", beforeRebuild.allExercises().map { it.name }, afterRebuild.allExercises().map { it.name })
            assertEquals("el override apunta al día de receta destino", "day_b", rebuilt.manualSessionOverrides.single { it.sessionId == afterRebuild.id }.recipeDayId)
            if (createDestination) assertNotEquals(targetId, afterRebuild.id)
        }
    }

    // ═══ Global rule defaults: Room keeps the eight core fields, the preference record keeps the extras ═══

    @Test
    fun globalScopeAndExtrasSurviveSaveAndReopenWhileRoomKeepsOnlyTheEightCoreFields() = runBlocking {
        val programId = "prefs-extras-scope"
        val sessionId = stableId(programId, "day_a")
        assertTrue(repository.addProgramNow(programWithSessionAndRecipe(programId)).isSuccess)
        val vm = createViewModel(programId, sessionId)
        awaitSession(vm)

        vm.patchRuleDefaults(null) { it.withExtras(richExtras) }
        vm.updateRuleDefaults(reps = 6)
        assertTrue(vm.uiState.value.hasUnsavedChanges)

        val saved = vm.saveSession()
        assertTrue("Guardado: ${saved.message}", saved.success)
        assertFalse("tras guardar no queda nada pendiente", vm.uiState.value.hasUnsavedChanges)
        assertEquals("el core viaja a Room", 6, roomSession(programId, sessionId).persistedRuleDefaults?.reps)
        assertEquals("los extras viajan al registro de preferencias, en un solo registro", richExtras,
            readRecord(programId, sessionId)?.globalRuleExtras)
        assertNull("el borrador se borra tras guardar todo", vm.persistedDraftFor("week", 0, 0, sessionId))

        val reopened = createViewModel(programId, sessionId)
        awaitSession(reopened)
        val state = reopened.uiState.value
        assertEquals(RuleScope.COMPOUND_ISOLATION, state.ruleDefaults.scope)
        assertEquals(DefaultIntensityType.RIR, state.ruleDefaults.intensityType)
        assertEquals(6, state.ruleDefaults.reps)
        assertEquals(richExtras, state.ruleDefaults.extras())
        assertEquals(richExtras, state.savedRuleExtras)
        assertFalse("recargar lo guardado no deja nada sucio", state.hasUnsavedChanges)
    }

    @Test
    fun extrasOnlyEditSavesAsPreferencesWithoutOverrideOrTimestampAndSurvivesReopen() = runBlocking {
        val programId = "prefs-extras-pref-only"
        val sessionId = stableId(programId, "day_a")
        assertTrue(repository.addProgramNow(programWithSessionAndRecipe(programId)).isSuccess)
        val vm = createViewModel(programId, sessionId)
        awaitSession(vm)
        val before = roomProgram(programId)
        val timestampBefore = roomSession(programId, sessionId).lastModifiedAtMs

        vm.updateRuleDefaults(intensityType = DefaultIntensityType.RIR)
        vm.patchRuleDefaults(null) {
            it.copy(
                scope = RuleScope.COMPOUND_ISOLATION,
                compoundReps = 4,
                isolationIntensityType = DefaultIntensityType.FALLO,
            )
        }
        assertTrue("las preferencias ensucian el formulario", vm.uiState.value.hasUnsavedChanges)
        assertFalse("pero no son contenido de Room", vm.uiState.value.hasMeaningfulSessionChanges())

        val result = vm.saveSession()
        assertTrue("guardado pref-only: ${result.message}", result.success)
        assertFalse(vm.uiState.value.hasUnsavedChanges)
        val after = roomProgram(programId)
        assertEquals("ni override, ni timestamp, ni contenido: Room queda byte a byte igual", before, after)
        assertTrue(after.manualSessionOverrides.isEmpty())
        assertEquals(timestampBefore, roomSession(programId, sessionId).lastModifiedAtMs)

        val reopened = createViewModel(programId, sessionId)
        awaitSession(reopened)
        val restored = reopened.uiState.value.ruleDefaults
        assertEquals(DefaultIntensityType.RIR, restored.intensityType)
        assertEquals(RuleScope.COMPOUND_ISOLATION, restored.scope)
        assertEquals(4, restored.compoundReps)
        assertEquals(DefaultIntensityType.FALLO, restored.isolationIntensityType)
        assertFalse(reopened.uiState.value.hasUnsavedChanges)
        assertNull(reopened.persistedDraftFor("week", 0, 0, sessionId))
    }

    @Test
    fun compoundAndIsolationOverridesSurviveReopenAndDriveNewExercises() = runBlocking {
        val programId = "prefs-extras-new-items"
        val sessionId = stableId(programId, "day_a")
        assertTrue(repository.addProgramNow(programWithSessionAndRecipe(programId)).isSuccess)
        val vm = createViewModel(programId, sessionId)
        awaitSession(vm)
        vm.updateRuleDefaults(setCount = 2, applyToNewItems = true)
        vm.patchRuleDefaults(null) { it.withExtras(richExtras) }
        val saved = vm.saveSession()
        assertTrue("Guardado: ${saved.message}", saved.success)

        val reopened = createViewModel(programId, sessionId)
        awaitSession(reopened)
        assertTrue(reopened.uiState.value.ruleDefaults.applyToNewItems)
        assertEquals(richExtras, reopened.uiState.value.ruleDefaults.extras())

        val compoundId = reopened.addExerciseToPart(null, compoundInfo)
        val isolationId = reopened.addExerciseToPart(null, isolationInfo)
        val exercises = reopened.uiState.value.session!!.allExercises()
        val compound = exercises.first { it.id == compoundId }
        val isolation = exercises.first { it.id == isolationId }

        assertEquals(2, compound.sets.size)
        assertTrue(compound.sets.all { it.targetReps == 5 })
        assertTrue(compound.sets.all { it.intensityMode == IntensityMode.RPE })
        assertTrue(compound.sets.all { it.targetRPE == 8.5 })
        assertEquals(180, compound.restTime)
        assertEquals(2, isolation.sets.size)
        assertTrue(isolation.sets.all { it.targetReps == 12 })
        assertTrue(isolation.sets.all { it.intensityMode == IntensityMode.RIR })
        assertTrue(isolation.sets.all { it.targetRIR == 2 })
        assertEquals(60, isolation.restTime)
    }

    @Test
    fun applyToNewItemsComesFromRoomAndGatesTheRestoredExtras() = runBlocking {
        val programId = "prefs-extras-apply-gate"
        val sessionId = stableId(programId, "day_a")
        assertTrue(repository.addProgramNow(programWithSessionAndRecipe(programId)).isSuccess)
        val vm = createViewModel(programId, sessionId)
        awaitSession(vm)
        vm.updateRuleDefaults(setCount = 2, applyToNewItems = true)
        vm.patchRuleDefaults(null) { it.withExtras(richExtras) }
        assertTrue(vm.saveSession().success)

        setRoomCore(programId, sessionId) { it!!.copy(applyToNewItems = false) }
        val gated = createViewModel(programId, sessionId)
        awaitSession(gated)
        assertFalse("applyToNewItems es core: manda Room", gated.uiState.value.ruleDefaults.applyToNewItems)
        assertEquals("los extras siguen restaurados", richExtras, gated.uiState.value.ruleDefaults.extras())
        assertFalse(gated.uiState.value.hasUnsavedChanges)
        val plainId = gated.addExerciseToPart(null, compoundInfo)
        val plain = gated.uiState.value.session!!.allExercises().first { it.id == plainId }
        assertEquals("sin applyToNewItems no se aplican reglas", 1, plain.sets.size)
        assertEquals(8, plain.sets.first().targetReps)

        setRoomCore(programId, sessionId) { it!!.copy(applyToNewItems = true) }
        val enabled = createViewModel(programId, sessionId)
        awaitSession(enabled)
        assertTrue(enabled.uiState.value.ruleDefaults.applyToNewItems)
        val appliedId = enabled.addExerciseToPart(null, compoundInfo)
        val applied = enabled.uiState.value.session!!.allExercises().first { it.id == appliedId }
        assertEquals(2, applied.sets.size)
        assertTrue(applied.sets.all { it.targetReps == 5 })
        assertTrue(applied.sets.all { it.intensityMode == IntensityMode.RPE })
    }

    @Test
    fun externalRoomRepsChangeIsNotOverwrittenByOldPreferencesAndPrefOnlyEditsLeaveItAlone() = runBlocking {
        val programId = "prefs-extras-external-reps"
        val sessionId = stableId(programId, "day_a")
        assertTrue(repository.addProgramNow(programWithSessionAndRecipe(programId)).isSuccess)
        setRoomCore(programId, sessionId) { SessionPersistedRuleDefaults(setCount = 3, reps = 5) }
        val first = createViewModel(programId, sessionId)
        awaitSession(first)
        first.patchRuleDefaults(null) { it.withExtras(richExtras) }
        assertTrue(first.saveSession().success)
        assertEquals(richExtras, readRecord(programId, sessionId)?.globalRuleExtras)
        assertEquals(5, roomSession(programId, sessionId).persistedRuleDefaults?.reps)

        // Another route moves reps 5 -> 8 in Room; the stored record still holds the old extras.
        setRoomCore(programId, sessionId) { it!!.copy(reps = 8) }
        val second = createViewModel(programId, sessionId)
        awaitSession(second)
        assertEquals("Room manda sobre el core", 8, second.uiState.value.ruleDefaults.reps)
        assertEquals(richExtras, second.uiState.value.ruleDefaults.extras())
        assertFalse(second.uiState.value.hasUnsavedChanges)

        val roomBeforeEdit = roomProgram(programId)
        second.patchRuleDefaults(null) { it.copy(scope = RuleScope.ALL_SESSION) }
        val result = second.saveSession()
        assertTrue(result.message, result.success)
        assertEquals("editar solo un extra no reescribe reps en Room", roomBeforeEdit, roomProgram(programId))

        val third = createViewModel(programId, sessionId)
        awaitSession(third)
        assertEquals(8, third.uiState.value.ruleDefaults.reps)
        assertEquals(RuleScope.ALL_SESSION, third.uiState.value.ruleDefaults.scope)
        assertEquals(DefaultIntensityType.RIR, third.uiState.value.ruleDefaults.intensityType)
        assertFalse(third.uiState.value.hasUnsavedChanges)
    }

    @Test
    fun externalRoomChangeWithPendingExtrasDraftKeepsUntouchedCoreFromRoom() = runBlocking {
        val programId = "prefs-extras-d2-external"
        val sessionId = stableId(programId, "day_a")
        assertTrue(repository.addProgramNow(programWithSessionAndRecipe(programId)).isSuccess)
        setRoomCore(programId, sessionId) { SessionPersistedRuleDefaults(setCount = 3, reps = 5) }
        val first = createViewModel(programId, sessionId)
        awaitSession(first)
        first.patchRuleDefaults(null) { it.withExtras(richExtras) }
        awaitDraftWrite(programId, sessionId)
        val draft = first.persistedDraftFor("week", 0, 0, sessionId) ?: error("falta el borrador con extras pendientes")
        assertEquals("el borrador fija el core confirmado", 5, draft.committedRuleBaseline?.ruleDefaults?.reps)
        assertEquals(1, draft.committedRuleBaseline?.version)

        setRoomCore(programId, sessionId) { it!!.copy(reps = 8) }
        val roomAfterExternalChange = roomProgram(programId)

        val second = createViewModel(programId, sessionId)
        awaitSession(second)
        val state = second.uiState.value
        assertEquals("el campo que el usuario no tocó sigue a Room", 8, state.ruleDefaults.reps)
        assertEquals("los extras pendientes sobreviven", richExtras, state.ruleDefaults.extras())
        assertEquals(SessionEditorGlobalRuleExtras(), state.savedRuleExtras)
        assertTrue(state.hasUnsavedChanges)
        assertFalse("el cambio externo de Room no es una edición del usuario", state.hasMeaningfulSessionChanges())

        val result = second.saveSession()
        assertTrue("el guardado solo guarda preferencias: ${result.message}", result.success)
        assertEquals(roomAfterExternalChange, roomProgram(programId))
        assertEquals(8, roomSession(programId, sessionId).persistedRuleDefaults?.reps)

        val third = createViewModel(programId, sessionId)
        awaitSession(third)
        assertEquals(8, third.uiState.value.ruleDefaults.reps)
        assertEquals(richExtras, third.uiState.value.ruleDefaults.extras())
        assertFalse(third.uiState.value.hasUnsavedChanges)
    }

    @Test
    fun userEditedCoreFieldInDraftWinsOverAnExternalRoomChange() = runBlocking {
        val programId = "prefs-extras-d2-user-edit"
        val sessionId = stableId(programId, "day_a")
        assertTrue(repository.addProgramNow(programWithSessionAndRecipe(programId)).isSuccess)
        setRoomCore(programId, sessionId) { SessionPersistedRuleDefaults(setCount = 3, reps = 5) }
        val first = createViewModel(programId, sessionId)
        awaitSession(first)
        first.updateRuleDefaults(reps = 7)
        awaitDraftWrite(programId, sessionId)

        setRoomCore(programId, sessionId) { it!!.copy(reps = 8, setCount = 4) }

        val second = createViewModel(programId, sessionId)
        awaitSession(second)
        assertEquals("el usuario tocó reps: gana el borrador", 7, second.uiState.value.ruleDefaults.reps)
        assertEquals("setCount no se tocó: sigue a Room", 4, second.uiState.value.ruleDefaults.setCount)
        assertTrue(second.uiState.value.hasUnsavedChanges)
        assertTrue("la edición de core del usuario sigue siendo contenido", second.uiState.value.hasMeaningfulSessionChanges())
    }

    @Test
    fun preferenceFailureAfterRoomCommitKeepsConsumedTransferAndExtrasAndRetryDoesNotAppend() = runBlocking {
        val programId = "prefs-extras-pref-retry"
        val sessionId = stableId(programId, "day_a")
        val targetId = stableId(programId, "day_b")
        assertTrue(repository.addProgramNow(programWithSessionAndRecipe(programId)).isSuccess)
        val vm = createViewModel(programId, sessionId)
        awaitSession(vm)
        vm.updateSession { it.copy(name = "Guardado con extras pendientes") }
        vm.updateRuleDefaults(reps = 9)
        vm.patchRuleDefaults(null) { it.withExtras(richExtras) }
        val targetKey = vm.uiState.value.cloneDayOptions.single { it.dayOfWeek == 3 }.key
        assertTrue(vm.cloneCurrentSessionToTargets(setOf(targetKey), null, SessionCloneApplyMode.APPEND).success)
        val transferId = requireNotNull(vm.uiState.value.pendingTransferToDays).transferId
        awaitDraftWithTransfer(vm, sessionId)

        val partial = withFailingRulePreferenceWrite(programId, sessionId) { vm.saveSession() }
        assertFalse(partial.message, partial.success)
        assertTrue(partial.message, partial.message.contains("guardado en Room"))
        assertTrue("los extras siguen pendientes", vm.uiState.value.hasUnsavedChanges)
        assertNull("la transferencia consumida no vuelve", vm.uiState.value.pendingTransferToDays)
        assertEquals(SessionEditorGlobalRuleExtras(), vm.uiState.value.savedRuleExtras)
        assertFalse("Room ya tiene el contenido: reintentar no es un guardado completo", vm.uiState.value.hasMeaningfulSessionChanges())
        assertNull("el registro de preferencias no se escribió", readRecord(programId, sessionId))

        val recovery = vm.persistedDraftFor("week", 0, 0, sessionId) ?: error("falta el borrador durable tras el fallo de preferencias")
        assertNull(recovery.pendingTransferToDays)
        assertEquals("los extras nuevos viven en el borrador", richExtras, recovery.ruleDefaults.extras())
        assertEquals("el baseline lleva el core que ya está en Room", 9, recovery.committedRuleBaseline?.ruleDefaults?.reps)
        assertEquals("pero los extras confirmados siguen siendo los viejos", SessionEditorGlobalRuleExtras(),
            recovery.committedRuleBaseline?.ruleDefaults?.extras())

        val committedProgram = roomProgram(programId)
        assertEquals(9, roomSession(programId, sessionId).persistedRuleDefaults?.reps)
        assertEquals(2, weekOf(committedProgram, "week").sessions.single { it.id == targetId }.allExercises().size)
        assertEquals(1, committedProgram.manualSessionOverrides.count { it.sessionId == targetId && "editor-transfer:$transferId" in it.reason })

        val recreated = createViewModel(programId, sessionId)
        awaitSession(recreated)
        assertTrue("los extras pendientes sobreviven a la recreación", recreated.uiState.value.hasUnsavedChanges)
        assertEquals(richExtras, recreated.uiState.value.ruleDefaults.extras())
        assertEquals(9, recreated.uiState.value.ruleDefaults.reps)
        assertNull(recreated.uiState.value.pendingTransferToDays)
        assertFalse(recreated.uiState.value.hasMeaningfulSessionChanges())

        val retry = recreated.saveSession()
        assertTrue("el reintento solo guarda preferencias: ${retry.message}", retry.success)
        assertFalse(recreated.uiState.value.hasUnsavedChanges)
        assertEquals("el reintento no repite APPEND ni toca Room", committedProgram, roomProgram(programId))
        assertEquals(richExtras, readRecord(programId, sessionId)?.globalRuleExtras)
        assertNull(recreated.persistedDraftFor("week", 0, 0, sessionId))

        val reopened = createViewModel(programId, sessionId)
        awaitSession(reopened)
        assertEquals(richExtras, reopened.uiState.value.ruleDefaults.extras())
        assertEquals(9, reopened.uiState.value.ruleDefaults.reps)
        assertFalse(reopened.uiState.value.hasUnsavedChanges)
    }

    @Test
    fun roomFailureLeavesPreferencesUntouchedKeepsTheTransferAndRetryAppendsOnce() = runBlocking {
        val programId = "prefs-extras-room-failure"
        val sessionId = stableId(programId, "day_a")
        val targetId = stableId(programId, "day_b")
        assertTrue(repository.addProgramNow(programWithSessionAndRecipe(programId)).isSuccess)
        val vm = createViewModel(programId, sessionId)
        awaitSession(vm)
        vm.updateSession { it.copy(name = "Pendiente de Room") }
        vm.updateRuleDefaults(reps = 9)
        vm.patchRuleDefaults(null) { it.withExtras(richExtras) }
        val targetKey = vm.uiState.value.cloneDayOptions.single { it.dayOfWeek == 3 }.key
        assertTrue(vm.cloneCurrentSessionToTargets(setOf(targetKey), null, SessionCloneApplyMode.APPEND).success)
        val transferId = requireNotNull(vm.uiState.value.pendingTransferToDays).transferId

        repository.durableCommitBeforeWriteForTests = { throw IllegalStateException("fallo Room antes del upsert") }
        val failed = try {
            vm.saveSession()
        } finally {
            repository.durableCommitBeforeWriteForTests = null
        }
        assertFalse(failed.success)
        assertNull("si Room falla, las preferencias no se tocan", readRecord(programId, sessionId))
        assertEquals(transferId, vm.uiState.value.pendingTransferToDays?.transferId)
        assertEquals(SessionEditorGlobalRuleExtras(), vm.uiState.value.savedRuleExtras)
        val recovery = vm.persistedDraftFor("week", 0, 0, sessionId) ?: error("falta el borrador recuperable")
        assertEquals(transferId, recovery.pendingTransferToDays?.transferId)
        assertEquals(9, recovery.ruleDefaults.reps)
        assertEquals("el core confirmado sigue siendo el de Room", 5, recovery.committedRuleBaseline?.ruleDefaults?.reps)

        val retried = vm.saveSession()
        assertTrue("reintento completo: ${retried.message}", retried.success)
        val persisted = roomProgram(programId)
        assertEquals(2, weekOf(persisted, "week").sessions.single { it.id == targetId }.allExercises().size)
        assertEquals(1, persisted.manualSessionOverrides.count { it.sessionId == targetId && "editor-transfer:$transferId" in it.reason })
        assertEquals(9, roomSession(programId, sessionId).persistedRuleDefaults?.reps)
        assertEquals(richExtras, readRecord(programId, sessionId)?.globalRuleExtras)
    }

    @Test
    fun discardAfterPreferenceFailureRestoresTheConfirmedExtrasAndRoomCoreWithoutARedundantWrite() = runBlocking {
        val programId = "prefs-extras-discard-after-failure"
        val sessionId = stableId(programId, "day_a")
        assertTrue(repository.addProgramNow(programWithSessionAndRecipe(programId)).isSuccess)
        val vm = createViewModel(programId, sessionId)
        awaitSession(vm)
        vm.updateRuleDefaults(reps = 9)
        vm.patchRuleDefaults(null) { it.withExtras(richExtras) }

        val partial = withFailingRulePreferenceWrite(programId, sessionId) { vm.saveSession() }
        assertFalse(partial.success)
        assertTrue(vm.uiState.value.hasUnsavedChanges)
        assertNotNull(vm.persistedDraftFor("week", 0, 0, sessionId))

        assertTrue(vm.discardDraftForCurrentSessionAndAwait())
        val restored = vm.uiState.value.ruleDefaults
        assertEquals("el core ya confirmado en Room se conserva", 9, restored.reps)
        assertEquals("los extras vuelven al baseline confirmado", SessionEditorGlobalRuleExtras(), restored.extras())
        assertFalse(vm.uiState.value.hasUnsavedChanges)
        assertNull(vm.persistedDraftFor("week", 0, 0, sessionId))
        assertNull("el baseline por defecto no obliga a escribir un registro", readRecord(programId, sessionId))

        val reopened = createViewModel(programId, sessionId)
        awaitSession(reopened)
        assertEquals(9, reopened.uiState.value.ruleDefaults.reps)
        assertEquals(SessionEditorGlobalRuleExtras(), reopened.uiState.value.ruleDefaults.extras())
        assertFalse(reopened.uiState.value.hasUnsavedChanges)
    }

    @Test
    fun staleExtrasWriteRevisionCannotRevertNewerExtras() = runBlocking {
        val store = draftStore()
        val key = sessionEditorRulePreferencesStorageKey("prefs-extras-order", "session-extras-order")
        val older = SessionEditorRulePreferences(globalRuleExtras = richExtras)
        val newer = SessionEditorRulePreferences(globalRuleExtras = richExtras.copy(scope = RuleScope.PER_GROUP))
        val olderRevision = store.nextRulePreferencesWriteRevision(key)
        val newerRevision = store.nextRulePreferencesWriteRevision(key)

        assertTrue(store.writeRulePreferences(key, newer, newerRevision))
        assertFalse("una revisión más vieja no pisa extras más nuevos", store.writeRulePreferences(key, older, olderRevision))
        assertEquals(newer, store.readRulePreferences(key))
    }

    @Test
    fun legacyDraftWithGlobalExtrasMigratesAsCommittedAndSurvivesDiscard() = runBlocking {
        val programId = "prefs-extras-legacy-d0"
        val sessionId = stableId(programId, "day_a")
        val program = programWithSessionAndRecipe(programId)
        assertTrue(repository.addProgramNow(program).isSuccess)
        val session = weekOf(program, "week").sessions.single { it.id == sessionId }
        val draftKey = sessionEditorDraftStorageKey(programId, "week", 0, 0, sessionId)
        val legacyDefaults = mapOf("part-a" to SessionEditorRuleDefaults(reps = 7, applyToNewItems = true))
        val legacyLimits = SessionEditorRuleLimits(maxRPE = 7.5)
        val legacyDraft = PersistedSessionEditorDraft(
            programId = programId,
            sessionId = sessionId,
            weekId = "week",
            macroIndex = 0,
            mesoIndex = 0,
            dayOfWeek = 1,
            session = session,
            ruleDefaults = session.inferredEditorRuleDefaults().withExtras(richExtras),
            partRuleDefaults = legacyDefaults,
            ruleLimits = legacyLimits,
        )
        assertEquals(DraftWriteStatus.WRITTEN, draftStore().writer.writeLatest(draftKey, legacyDraft).status)

        val vm = createViewModel(programId, sessionId)
        awaitSession(vm)
        assertEquals(legacyDraft.ruleDefaults, vm.uiState.value.ruleDefaults)
        assertEquals("sin baseline versionado ni registro, los extras del borrador migran como confirmados",
            richExtras, vm.uiState.value.savedRuleExtras)
        assertFalse("la migración por sí sola no ensucia", vm.uiState.value.hasUnsavedChanges)
        assertEquals(SessionEditorRulePreferences(legacyDefaults, legacyLimits, richExtras), readRecord(programId, sessionId))

        assertTrue(vm.discardDraftForCurrentSessionAndAwait())
        assertNull(vm.persistedDraftFor("week", 0, 0, sessionId))
        val reopened = createViewModel(programId, sessionId)
        awaitSession(reopened)
        assertEquals(richExtras, reopened.uiState.value.ruleDefaults.extras())
        assertEquals(legacyDefaults, reopened.uiState.value.partRuleDefaults)
        assertFalse(reopened.uiState.value.hasUnsavedChanges)
    }

    @Test
    fun v1RecordUpgradesToV2OnTheFirstExtrasSaveAndASplitDraftKeepsItsExtrasPending() = runBlocking {
        val programId = "prefs-extras-v1-record"
        val sessionId = stableId(programId, "day_a")
        val program = programWithSessionAndRecipe(programId)
        assertTrue(repository.addProgramNow(program).isSuccess)
        val session = weekOf(program, "week").sessions.single { it.id == sessionId }
        val draftKey = sessionEditorDraftStorageKey(programId, "week", 0, 0, sessionId)
        val parts = mapOf("part-a" to SessionEditorRuleDefaults(reps = 8))
        val limits = SessionEditorRuleLimits(maxRPE = 9.0, rigidLimits = true)
        val recordKey = sessionEditorRulePreferencesStorageKey(programId, sessionId)
        assertTrue(draftStore().writeRulePreferences(recordKey, SessionEditorRulePreferences(parts, limits)))
        assertNull("el registro v1 no lleva extras", readRecord(programId, sessionId)?.globalRuleExtras)

        val plain = createViewModel(programId, sessionId)
        awaitSession(plain)
        assertEquals(SessionEditorGlobalRuleExtras(), plain.uiState.value.savedRuleExtras)
        assertFalse("un registro v1 sin borrador carga limpio", plain.uiState.value.hasUnsavedChanges)
        assertEquals(parts, plain.uiState.value.partRuleDefaults)

        val splitDraft = PersistedSessionEditorDraft(
            programId = programId,
            sessionId = sessionId,
            weekId = "week",
            macroIndex = 0,
            mesoIndex = 0,
            dayOfWeek = 1,
            session = session,
            ruleDefaults = session.inferredEditorRuleDefaults().withExtras(richExtras),
            partRuleDefaults = parts,
            ruleLimits = limits,
            committedPartRuleDefaults = parts,
            committedRuleLimits = limits,
        )
        assertEquals(DraftWriteStatus.WRITTEN, draftStore().writer.writeLatest(draftKey, splitDraft).status)

        val vm = createViewModel(programId, sessionId)
        awaitSession(vm)
        assertEquals("los extras de un borrador con baseline de partes nunca llegaron a un almacén",
            SessionEditorGlobalRuleExtras(), vm.uiState.value.savedRuleExtras)
        assertEquals(richExtras, vm.uiState.value.ruleDefaults.extras())
        assertTrue(vm.uiState.value.hasUnsavedChanges)
        assertFalse(vm.uiState.value.hasMeaningfulSessionChanges())
        assertNull("cargar no migra encima de un registro existente", readRecord(programId, sessionId)?.globalRuleExtras)

        val roomBefore = roomProgram(programId)
        val result = vm.saveSession()
        assertTrue(result.message, result.success)
        assertEquals(roomBefore, roomProgram(programId))
        val upgraded = readRecord(programId, sessionId) ?: error("falta el registro v2")
        assertEquals(richExtras, upgraded.globalRuleExtras)
        assertEquals(parts, upgraded.partRuleDefaults)
        assertEquals(limits, upgraded.ruleLimits)
        assertFalse(vm.uiState.value.hasUnsavedChanges)
    }

    @Test
    fun extrasAreIsolatedPerSessionAndPerProgram() = runBlocking {
        val programId = "prefs-extras-isolation"
        val firstId = stableId(programId, "day_a")
        val secondId = stableId(programId, "day_b")
        assertTrue(repository.addProgramNow(programWithSessionAndRecipe(programId)).isSuccess)
        val vm = createViewModel(programId, firstId)
        awaitSession(vm)
        vm.patchRuleDefaults(null) { it.withExtras(richExtras) }
        assertTrue(vm.saveSession().success)

        val other = createViewModel(programId, secondId)
        awaitSession(other)
        assertEquals("las preferencias de A no aparecen en B", SessionEditorGlobalRuleExtras(), other.uiState.value.ruleDefaults.extras())
        assertFalse(other.uiState.value.hasUnsavedChanges)
        assertNull(readRecord(programId, secondId))

        val store = draftStore()
        assertTrue(store.writeRulePreferences(
            sessionEditorRulePreferencesStorageKey("prefs-program-one", "shared-session"),
            SessionEditorRulePreferences(globalRuleExtras = richExtras),
        ))
        assertNull("otro programa con el mismo sessionId no comparte registro",
            store.readRulePreferences(sessionEditorRulePreferencesStorageKey("prefs-program-two", "shared-session")))
    }

    @Test
    fun mesocycleSaveCopiesTheCoreThroughTheCloneAndTheExtrasBestEffortKeepingTheClonesOwnPreferences() = runBlocking {
        val programId = "prefs-extras-mesocycle"
        val firstId = "prefs-meso-first"
        val cloneId = "prefs-meso-clone"
        assertTrue(repository.addProgramNow(mesocycleProgram(programId, firstId, cloneId)).isSuccess)
        val cloneOwn = SessionEditorRulePreferences(
            partRuleDefaults = mapOf("part-own" to SessionEditorRuleDefaults(reps = 4)),
            ruleLimits = SessionEditorRuleLimits(maxRPE = 8.0),
        )
        assertTrue(draftStore().writeRulePreferences(sessionEditorRulePreferencesStorageKey(programId, cloneId), cloneOwn))
        val vm = createViewModel(programId, firstId)
        awaitSession(vm)
        vm.updateRuleDefaults(reps = 7)
        vm.patchRuleDefaults(null) { it.withExtras(richExtras) }

        val result = vm.saveSession(SessionSaveScope.MESOCYCLE)
        assertTrue("Guardado de mesociclo: ${result.message}", result.success)

        assertEquals("el core viaja dentro de la sesión clonada", 7,
            roomSession(programId, cloneId, weekId = "week2").persistedRuleDefaults?.reps)
        assertEquals(richExtras, readRecord(programId, firstId)?.globalRuleExtras)
        val cloneRecord = readRecord(programId, cloneId) ?: error("falta el registro del clon")
        assertEquals("los extras se copian al clon", richExtras, cloneRecord.globalRuleExtras)
        assertEquals("las partes del clon se conservan", cloneOwn.partRuleDefaults, cloneRecord.partRuleDefaults)
        assertEquals("los límites del clon se conservan", cloneOwn.ruleLimits, cloneRecord.ruleLimits)
    }

    @Test
    fun sessionOnlySaveDoesNotTouchTheCloneRecords() = runBlocking {
        val programId = "prefs-extras-session-only"
        val firstId = "prefs-solo-first"
        val cloneId = "prefs-solo-clone"
        assertTrue(repository.addProgramNow(mesocycleProgram(programId, firstId, cloneId)).isSuccess)
        val vm = createViewModel(programId, firstId)
        awaitSession(vm)
        vm.updateRuleDefaults(reps = 7)
        vm.patchRuleDefaults(null) { it.withExtras(richExtras) }

        val result = vm.saveSession(SessionSaveScope.SESSION_ONLY)
        assertTrue(result.message, result.success)

        assertEquals(richExtras, readRecord(programId, firstId)?.globalRuleExtras)
        assertNull(readRecord(programId, cloneId))
        assertNull(roomSession(programId, cloneId, weekId = "week2").persistedRuleDefaults)
    }

    @Test
    fun unreadableRecordIsNotOverwrittenOnLoadOnlyByAnExplicitSave() = runBlocking {
        val programId = "prefs-extras-unreadable"
        val sessionId = stableId(programId, "day_a")
        val program = programWithSessionAndRecipe(programId)
        assertTrue(repository.addProgramNow(program).isSuccess)
        val session = weekOf(program, "week").sessions.single { it.id == sessionId }
        val recordKey = sessionEditorRulePreferencesStorageKey(programId, sessionId)
        val draftKey = sessionEditorDraftStorageKey(programId, "week", 0, 0, sessionId)
        val corrupt = "{esto-no-es-json"
        assertTrue(draftStore().writeRawRulePreferencesForTests(recordKey, corrupt))
        val legacyParts = mapOf("part-a" to SessionEditorRuleDefaults(reps = 7))
        val legacyDraft = PersistedSessionEditorDraft(
            programId = programId,
            sessionId = sessionId,
            weekId = "week",
            macroIndex = 0,
            mesoIndex = 0,
            dayOfWeek = 1,
            session = session,
            ruleDefaults = session.inferredEditorRuleDefaults().withExtras(richExtras),
            partRuleDefaults = legacyParts,
        )
        assertEquals(DraftWriteStatus.WRITTEN, draftStore().writer.writeLatest(draftKey, legacyDraft).status)

        val vm = createViewModel(programId, sessionId)
        awaitSession(vm)
        assertEquals("el borrador sigue siendo la fuente cuando el registro es ilegible", richExtras, vm.uiState.value.ruleDefaults.extras())
        assertEquals(legacyParts, vm.uiState.value.partRuleDefaults)
        assertNull("un registro ilegible no es un registro ausente", readRecord(programId, sessionId))
        assertEquals("cargar no pisa el registro ilegible", corrupt, draftStore().readRawRulePreferencesForTests(recordKey))

        vm.updateRuleDefaults(intensityType = DefaultIntensityType.FALLO)
        val result = vm.saveSession()
        assertTrue(result.message, result.success)
        val replaced = readRecord(programId, sessionId) ?: error("el guardado explícito debe reemplazar el registro ilegible")
        assertEquals(DefaultIntensityType.FALLO, replaced.globalRuleExtras?.intensityType)
        assertEquals(legacyParts, replaced.partRuleDefaults)
    }

    @Test
    fun discardClaimsNoRevisionWhenTheRecordAlreadyMatchesButRestoresTheBaselineWhenItDoesNot() = runBlocking {
        val programId = "prefs-extras-discard-quiet"
        val sessionId = stableId(programId, "day_a")
        assertTrue(repository.addProgramNow(programWithSessionAndRecipe(programId)).isSuccess)
        val seed = createViewModel(programId, sessionId)
        awaitSession(seed)
        seed.patchRuleDefaults(null) { it.withExtras(richExtras) }
        assertTrue(seed.saveSession().success)

        val vm = createViewModel(programId, sessionId)
        awaitSession(vm)
        assertEquals(richExtras, vm.uiState.value.savedRuleExtras)
        vm.patchRuleDefaults(null) { it.copy(scope = RuleScope.PER_GROUP) }
        assertTrue(vm.uiState.value.hasUnsavedChanges)
        assertTrue(vm.saveDraftForExitAndAwait())

        val store = draftStore()
        val recordKey = sessionEditorRulePreferencesStorageKey(programId, sessionId)
        val before = store.nextRulePreferencesWriteRevision(recordKey)
        assertTrue(vm.discardDraftForCurrentSessionAndAwait())
        val after = store.nextRulePreferencesWriteRevision(recordKey)
        assertEquals("descartar no reclama revisión si el registro ya coincide con el baseline", before + 1, after)
        assertEquals(richExtras, vm.uiState.value.ruleDefaults.extras())
        assertFalse(vm.uiState.value.hasUnsavedChanges)
        assertNull(vm.persistedDraftFor("week", 0, 0, sessionId))
        assertEquals(richExtras, readRecord(programId, sessionId)?.globalRuleExtras)

        // Something else changed the record after this editor loaded it: Discard restores the baseline.
        vm.patchRuleDefaults(null) { it.copy(scope = RuleScope.PER_GROUP) }
        assertTrue(vm.saveDraftForExitAndAwait())
        assertTrue(store.writeRulePreferences(recordKey, SessionEditorRulePreferences(globalRuleExtras = richExtras.copy(compoundReps = 99))))
        assertTrue(vm.discardDraftForCurrentSessionAndAwait())
        assertEquals(richExtras, readRecord(programId, sessionId)?.globalRuleExtras)
        assertFalse(vm.uiState.value.hasUnsavedChanges)
    }
}

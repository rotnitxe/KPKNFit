package com.example.kpkn.screens.sessioneditor

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.domain.training.ApreVariant
import com.example.kpkn.domain.training.resolveLiveApreBlock
import com.example.kpkn.screens.sessioneditor.components.withApreBlock
import com.example.kpkn.testsupport.RoomTestRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/** AUD2: state transitions exercised against the real editor and Room repository. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class SessionEditorAuditRegressionTest {
    @get:Rule val roomRule = RoomTestRule()
    private val dispatcher = UnconfinedTestDispatcher()
    private val store = ViewModelStore()
    private lateinit var repository: ProgramRepository
    private val auditProgramId = "audit-program-${UUID.randomUUID()}"

    @Before fun setup() = runBlocking {
        Dispatchers.setMain(dispatcher)
        val app = ApplicationProvider.getApplicationContext<Application>()
        val drafts = SessionEditorDraftStore.getInstance(app)
        listOf("audit-main", "audit-other", "audit-alternative", "audit-legacy").forEach { id ->
            drafts.writer.clear(sessionEditorDraftStorageKey(auditProgramId, "week", 0, 0, id))
        }
        ProgramRepository.initForTests(app)
        repository = ProgramRepository.getInstance()
        withTimeout(10_000) { while (!repository.isReady.value) delay(25) }
        repository.clearPrograms()
        repository.flushPendingWrites()
        val base = Session(
            id = "audit-main", name = "Principal", dayOfWeek = 1,
            exercises = listOf(exercise("a"), exercise("b")),
            sessionB = Session(id = "audit-variant", name = "Variante", exercises = listOf(exercise("variant"))),
        )
        repository.addProgram(Program(
            id = auditProgramId, name = "AUD2", structure = ProgramStructure.SIMPLE,
            macrocycles = listOf(Macrocycle(id = "macro", name = "Macro", blocks = listOf(
                Block(id = "block", name = "Bloque", mesocycles = listOf(
                    Mesocycle(id = "meso", name = "Meso", weeks = listOf(
                        ProgramWeek(id = "week", name = "Semana", sessions = listOf(
                            base, Session(id = "audit-other", name = "Otro día", dayOfWeek = 2, exercises = listOf(exercise("other"))),
                            Session(id = "audit-alternative", name = "Alternativa", dayOfWeek = 1, isMainSession = false,
                                exercises = listOf(exercise("alternative"))),
                            Session(id = "audit-legacy", name = "Importada", dayOfWeek = 4,
                                exercises = listOf(exercise("mirror")),
                                parts = listOf(SessionPart(id = "legacy-group", name = "Grupo heredado", exercises = listOf(exercise("mirror"))))),
                        )),
                    )),
                )),
            ))),
        ))
        repository.flushPendingWrites()
    }

    @After fun teardown() {
        store.clear()
        ProgramRepository.closeInstance()
        Dispatchers.resetMain()
    }

    private fun exercise(id: String) = Exercise(
        id = id, name = "Ejercicio $id", exerciseDbId = "custom:$id",
        sets = listOf(ExerciseSet(id = "$id-set", targetReps = 8)),
    )

    private suspend fun editor(): SessionEditorViewModel = SessionEditorViewModel(
        ApplicationProvider.getApplicationContext(), auditProgramId, "audit-main", "week", 0, 0, 1,
    ).also { store.put("editor", it); it.awaitSessionLoaded() }

    private fun storedMain() = repository.getProgramById(auditProgramId)!!
        .macrocycles.single().blocks.single().mesocycles.single().weeks.single().sessions.single { it.id == "audit-main" }

    @Test fun applyingManualApreFromRmPreservesPurposesThroughRoomSaveAndReadback() = runBlocking {
        val vm = editor()
        vm.updateExercise(null, "a") { current ->
            current.copy(
                trainingMode = TrainingMode.RM, reference1RM = 100.0, referenceTrainingMax = 80.0,
                sets = listOf(ExerciseSet(id = "a-rm", targetReps = 8, targetPercentageRM = 80.0, loadBasis = LoadBasis.PERCENT_TM)),
            )
        }
        var setId = 0
        vm.updateExercise(null, "a") { current ->
            current.withApreBlock(ApreVariant.APRE_6) { "manual-apre-${++setId}" }
        }

        assertTrue(vm.flushProgramAutoSaveAndAwait())
        repository.flushPendingWrites()
        val saved = storedMain()
        val readback = kotlinx.serialization.json.Json.decodeFromString<Session>(
            kotlinx.serialization.json.Json.encodeToString(Session.serializer(), saved),
        ).exercises.single { it.id == "a" }

        val liveBlock = resolveLiveApreBlock(readback)
        assertEquals(ApreVariant.APRE_6, liveBlock?.variant)
        assertEquals(listOf(SetPurpose.NONE, SetPurpose.NONE, SetPurpose.AMRAP, SetPurpose.AMRAP),
            readback.sets.map { it.purpose })
        assertEquals(listOf(null, null, 6, 6), readback.sets.map { it.amrapMinimumReps })
    }

    @Test fun leavingBeforeUndoExpiresPersistsTheDeletion() = runBlocking {
        val vm = editor()
        vm.requestExerciseRemoval(null, "a")
        assertTrue(vm.flushProgramAutoSaveAndAwait())
        assertEquals(listOf("b"), storedMain().exercises.map { it.id })
        assertNull(vm.currentUiState.pendingExerciseRemoval)
    }

    @Test fun changingDayBeforeUndoExpiresDeletesFromTheOriginalDay() = runBlocking {
        val vm = editor()
        vm.requestExerciseRemoval(null, "a")
        vm.requestSessionSwitch("audit-other")
        withTimeout(10_000) { while (vm.currentUiState.session?.id != "audit-other") delay(25) }
        assertEquals(listOf("b"), storedMain().exercises.map { it.id })
        assertNull(vm.currentUiState.pendingExerciseRemoval)
    }

    @Test fun changingVariantCommitsTheRemovalInItsOriginalVariant() = runBlocking {
        val vm = editor()
        vm.requestExerciseRemoval(null, "a")
        vm.switchVariant(WeekVariant.B)
        assertEquals(listOf("b"), vm.currentUiState.session!!.exercises.map { it.id })
        assertNull(vm.currentUiState.pendingExerciseRemoval)
        assertEquals(listOf("variant"), vm.currentUiState.activeVariantSession!!.exercises.map { it.id })
    }

    @Test fun creatingANewDayFromVariantBStartsInVariantA() = runBlocking {
        val vm = editor()
        vm.switchVariant(WeekVariant.B)
        assertTrue(vm.createSessionForDay(3).success)
        assertEquals(WeekVariant.A, vm.currentUiState.activeVariant)
        assertNotNull(vm.currentUiState.activeVariantSession)
        assertEquals(listOf(WeekVariant.A), vm.currentUiState.availableVariants)
        vm.updateSessionName("Miércoles editado")
        assertEquals("Miércoles editado", vm.currentUiState.session!!.name)
    }

    @Test fun selectingAMissingVariantKeepsAnEditableSession() = runBlocking {
        val vm = editor()
        vm.switchVariant(WeekVariant.C)
        assertEquals(WeekVariant.A, vm.currentUiState.activeVariant)
        assertNotNull(vm.currentUiState.activeVariantSession)
    }

    @Test fun restoringVersionWithoutTheActiveVariantKeepsEditingFunctional() = runBlocking {
        val vm = editor()
        val withoutVariant = vm.currentUiState.session!!.copy(sessionB = null)
        vm.switchVariant(WeekVariant.B)
        vm.restoreDraftSnapshot(snapshot(withoutVariant))
        assertEquals(WeekVariant.A, vm.currentUiState.activeVariant)
        assertEquals(listOf(WeekVariant.A), vm.currentUiState.availableVariants)
        vm.updateSessionName("Restaurada y editada")
        assertEquals("Restaurada y editada", vm.currentUiState.session!!.name)
    }

    @Test fun restoringAVersionMakesItsVariantsAvailableAgain() = runBlocking {
        val vm = editor()
        val saved = vm.currentUiState.session!!
        vm.deleteVariant(WeekVariant.B)
        vm.restoreDraftSnapshot(snapshot(saved))
        assertEquals(listOf(WeekVariant.A, WeekVariant.B), vm.currentUiState.availableVariants)
    }

    @Test fun changingCardioPlacementInBUsesTheActiveVariant() = runBlocking {
        val vm = editor()
        vm.switchVariant(WeekVariant.B)
        vm.toggleCardioPlacement()
        assertTrue(vm.currentUiState.activeVariantSession!!.cardioFirst)
        assertEquals(CardioSpacePlacement.START, vm.currentUiState.cardioSpacePlacement)
        assertFalse(vm.currentUiState.session!!.cardioFirst)
    }

    @Test fun changingTheMainSessionPreservesWorkEvenWhenSavingIsBlocked() = runBlocking {
        val vm = editor()
        vm.updateSessionName(" ") // Invalid name: Room autosave is deliberately blocked.
        assertFalse(vm.flushProgramAutoSaveAndAwait())
        vm.setMainSessionForDay("audit-alternative")
        withTimeout(10_000) { while (vm.currentUiState.session?.id != "audit-alternative") delay(25) }
        val recovered = vm.persistedDraftFor("week", 0, 0, "audit-main")
        assertNotNull("Blocked work must survive changing the main session", recovered)
        assertEquals(" ", recovered!!.session.name)
    }

    @Test fun aCatalogSupersetWithFiveMembersCannotBePartiallyCreated() = runBlocking {
        val vm = editor()
        val before = vm.currentUiState.session
        val added = vm.addExercisesAsSupersetToPart(null,
            List(5) { ExerciseMuscleInfo(id = "custom:new-$it", name = "Nuevo $it") }, CatalogSupersetConfig())
        assertTrue(added.isEmpty())
        assertEquals(before, vm.currentUiState.session)
    }

    @Test fun quickActionDeleteActuallyDeletesASupersetMember() = runBlocking {
        val vm = editor()
        vm.openSupersetCreator(null, listOf("a", "b"))
        vm.createSupersetGroupFromDraft()
        vm.openExerciseQuickActions(null, "a")
        vm.triggerQuickActionDelete()
        vm.commitPendingExerciseRemoval()
        assertEquals(listOf("b"), vm.currentUiState.session!!.allExercises().map { it.id })
    }

    /** ED-D · C2: con dos o más ejercicios ya marcados tampoco se crea nada hasta confirmar en la hoja única. */
    @Test fun openingTheSupersetCreatorWithTwoMembersWaitsForTheSheetConfirmation() = runBlocking {
        val vm = editor()
        vm.openSupersetCreator(null, listOf("a", "b"))

        assertEquals(SessionEditorSheet.SUPERSET_CREATOR, vm.currentUiState.sheet)
        assertTrue(vm.currentUiState.activeVariantSession!!.allSupersetGroups().isEmpty())
        assertNull(vm.currentUiState.activeVariantSession!!.allExercises().first { it.id == "a" }.supersetGroupRef)

        vm.createSupersetGroupFromDraft()
        val group = vm.currentUiState.activeVariantSession!!.allSupersetGroups().single()
        assertEquals(listOf("a", "b"), group.exerciseOrder)
        assertEquals(SessionEditorSheet.NONE, vm.currentUiState.sheet)
    }

    @Test fun restoringAVersionCancelsTheOldDeletionTimer() = runBlocking {
        val vm = editor()
        val original = vm.currentUiState.session!!
        vm.requestExerciseRemoval(null, "a")
        vm.restoreDraftSnapshot(snapshot(original))
        vm.commitPendingExerciseRemoval()
        assertEquals(listOf("a", "b"), vm.currentUiState.session!!.exercises.map { it.id })
        assertNull(vm.currentUiState.pendingExerciseRemoval)
    }

    @Test fun switchingVariantRefreshesItsDurationWithoutEditingIt() = runBlocking {
        val vm = editor()
        val initial = com.example.kpkn.domain.training.SessionDurationEstimator.estimate(vm.currentUiState.session!!).totalMinutes
        val expected = com.example.kpkn.domain.training.SessionDurationEstimator.estimate(vm.currentUiState.session!!.sessionB!!).totalMinutes
        assertNotEquals(initial, expected)
        vm.refreshDerivedStateImmediate()
        withTimeout(10_000) { while (vm.currentUiState.estimatedDurationMinutes != initial) delay(25) }
        vm.switchVariant(WeekVariant.B)
        dispatcher.scheduler.advanceTimeBy(301)
        dispatcher.scheduler.runCurrent()
        withTimeout(5_000) { while (vm.currentUiState.estimatedDurationMinutes != expected) delay(25) }
    }

    @Test fun changingDayNormalizesImportedMirrorsLikeTheInitialLoadDoes() = runBlocking {
        val vm = editor()
        vm.requestSessionSwitch("audit-legacy")
        withTimeout(10_000) { while (vm.currentUiState.session?.id != "audit-legacy") delay(25) }
        val ids = vm.currentUiState.session!!.allExercises().map { it.id }
        assertEquals(listOf("mirror"), ids)
    }

    private fun snapshot(session: Session) = SessionDraftSnapshot(
        id = "snapshot", session = session, savedAtMs = 1, reason = "Prueba AUD2", changedFields = emptyList(),
        exerciseCount = session.allExercises().size, setCount = 2, partCount = session.parts.size,
    )
}

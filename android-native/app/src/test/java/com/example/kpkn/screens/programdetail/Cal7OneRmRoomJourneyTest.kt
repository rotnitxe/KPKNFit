package com.example.kpkn.screens.programdetail

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.models.*
import com.example.kpkn.data.protocols.*
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.onboarding.LiftMark
import com.example.kpkn.domain.training.*
import com.example.kpkn.screens.onboarding.design.entreno.markTag
import com.example.kpkn.screens.programdetail.design.ONE_RM_SAVE_TAG
import com.example.kpkn.screens.programdetail.design.ProgramOneRmSheet
import com.example.kpkn.testsupport.RoomTestRule
import com.example.kpkn.ui.components.*
import com.example.kpkn.ui.tour.TourSeenStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Sheet -> real detail ViewModel -> Room -> pending recipe: includes an already migrated reference pool. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h880dp-xxhdpi")
@OptIn(ExperimentalCoroutinesApi::class)
class Cal7OneRmRoomJourneyTest {
    @get:Rule val room = RoomTestRule()
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()
    private lateinit var repository: ProgramRepository
    private val id = "cal7-one-rm"
    @Before fun setup() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        CatalogCompositionTestSupport.install()
        repository = ProgramRepository.initForTests(ApplicationProvider.getApplicationContext())
        withTimeout(10_000) { repository.isReady.first { it } }
        repository.resetAllStateSync()
    }
    @After fun cleanup() {
        try { store.clear(); ProgramRepository.closeInstance(); KpknDatabase.closeInstance() }
        finally { Dispatchers.resetMain() }
    }
    private fun weeks(program: Program) = program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }
    private fun gateProgram(): Program {
        fun session() = day("Banca y militar", weekday = 1, slots = listOf(
            slot("bench", SlotRole.T1_MAIN, CatalogIds.BP, percentSets(180, 5 to 70.0), 180, LiftSlot.BENCH),
            slot("press", SlotRole.T1_MAIN, CatalogIds.OHP, percentSets(180, 5 to 70.0), 180, LiftSlot.OVERHEAD)))
        val recipe = TrainingPlanRecipe("cal7-bench-press", weeks = listOf(
            weekRecipe(1, 0, "Test", BlockGoal.REALIZATION, listOf(session())),
            weekRecipe(2, 1, "Siguiente", BlockGoal.ACCUMULATION, listOf(session()))), trainingMaxPercent = .9,
            liftSlots = mapOf(LiftSlot.BENCH to CatalogIds.BP, LiftSlot.OVERHEAD to CatalogIds.OHP))
        val materialized = PlanMaterializer.materialize(Program(id, "CAL7 banca y militar"), recipe,
            CatalogCompositionTestSupport.metadata, Cal5TestSupport.SeqIds(), profile = Cal5TestSupport.fullProfile(), strict = false)
        val first = weeks(materialized).first()
        return ExerciseTrainingMax.migrate(materialized.copy(runState = ProgramRunState("cal7-test-run", weekId = first.id,
            weekInstanceId = first.id, pendingAction = PendingProgramAction(PendingProgramActionType.CONFIRM_1RM_TEST,
                "Confirma los principales de este programa", nextBlockId = materialized.macrocycles.first().blocks[1].id))))
    }
    private fun mark(lift: LiftSlot): LiftMark = when (lift) {
        LiftSlot.SQUAT -> LiftMark.SQUAT
        LiftSlot.BENCH -> LiftMark.BENCH
        LiftSlot.DEADLIFT -> LiftMark.DEADLIFT
        LiftSlot.OVERHEAD -> LiftMark.OVERHEAD_PRESS
    }
    private fun mount(vm: ProgramDetailViewModel, onDismiss: () -> Unit = {}) {
        val overlays = KpknOverlayHostController(tourSeen = object : TourSeenStore {
            override fun seenIds(): Set<String> = emptySet()
            override fun markSeen(tourId: String) = Unit
        })
        compose.setContent { CompositionLocalProvider(LocalKpknOverlayHost provides overlays) {
            ProgramOneRmSheet(valuesKg = mapOf(LiftMark.BENCH to 127.5, LiftMark.OVERHEAD_PRESS to 82.5),
                unit = "kg", lifts = vm.oneRmTestLifts().map(::mark), onValueKg = { _, _ -> },
                onSave = { _, _, _ -> error("The legacy three-lift callback must not be used") }, onDismiss = onDismiss,
                onSaveMarks = { marks -> vm.recordPendingOneRmTestMarks(marks.mapKeys { (lift, _) ->
                    when (lift) {
                        LiftMark.BENCH -> LiftSlot.BENCH
                        LiftMark.OVERHEAD_PRESS -> LiftSlot.OVERHEAD
                        else -> error("An unrelated lift entered the test")
                    }
                }) })
            KpknOverlayHostContent(overlays)
        } }
        compose.mainClock.advanceTimeBy(3_000)
        compose.waitForIdle()
    }

    @Test fun confirmsOnlyBenchAndMilitaryThenPersistsProfilePoolAndPendingLoadsTogether() = runBlocking {
        val before = gateProgram()
        repository.addProgram(before)
        repository.flushPendingWrites()
        val vm = ProgramDetailViewModel(id).also { store.put("detail", it) }
        withTimeout(10_000) { vm.program.first { it != null } }
        assertEquals(listOf(LiftSlot.BENCH, LiftSlot.OVERHEAD), vm.oneRmTestLifts())
        mount(vm)
        compose.onNodeWithTag(markTag(LiftMark.SQUAT)).assertDoesNotExist()
        compose.onNodeWithTag(markTag(LiftMark.DEADLIFT)).assertDoesNotExist()
        compose.onNodeWithTag(markTag(LiftMark.BENCH)).assertExists()
        compose.onNodeWithTag(markTag(LiftMark.OVERHEAD_PRESS)).assertExists()
        assertEquals(before, repository.getProgramById(id))
        compose.onNodeWithTag(ONE_RM_SAVE_TAG).assertIsEnabled().assertHeightIsAtLeast(48.dp).performClick()
        withTimeout(20_000) { vm.uiState.first { it.snackbarMessage?.startsWith("1RM registrado") == true } }
        repository.flushPendingWrites()
        val after = repository.getProgramById(id)!!
        val profile = after.powerliftingProfile!!
        assertEquals(127.5, profile.bench1RM!!, 0.0)
        assertEquals(82.5, profile.overhead1RM!!, 0.0)
        assertEquals(before.powerliftingProfile!!.squat1RM, profile.squat1RM)
        assertEquals(before.powerliftingProfile!!.deadlift1RM, profile.deadlift1RM)
        assertNull(after.runState!!.pendingAction)
        assertEquals(OneRmResolutionStatus.RECORDED, after.runState!!.oneRmAuditTrail.last().status)
        for ((config, oneRm) in listOf(CatalogIds.BP to 127.5, CatalogIds.OHP to 82.5)) {
            val exercise = ExerciseTrainingMax.exercises(after).first { it.catalogConfigurationId == config }
            assertEquals(oneRm, ExerciseTrainingMax.capturedKg(after, exercise, PlanLoadReferenceKind.EXERCISE_1RM)!!, 0.0)
            assertEquals(oneRm * .9, ExerciseTrainingMax.capturedKg(after, exercise, PlanLoadReferenceKind.EXERCISE_TM)!!, 1e-8)
            val pending = weeks(after).last().sessions.single().allExercises().single { it.catalogConfigurationId == config }
            assertEquals(oneRm, pending.reference1RM!!, 0.0)
            assertEquals(oneRm * .9, pending.referenceTrainingMax!!, 1e-8)
            assertEquals(oneRm * .9 * .7, pending.sets.single().weight!!, 1e-8)
        }
    }
}

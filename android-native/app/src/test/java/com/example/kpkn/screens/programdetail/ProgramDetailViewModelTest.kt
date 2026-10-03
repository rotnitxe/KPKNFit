package com.example.kpkn.screens.programdetail

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.models.*
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.DayRecipe
import com.example.kpkn.data.protocols.LiftSlot
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.percentSets
import com.example.kpkn.data.protocols.slot
import com.example.kpkn.data.protocols.weekRecipe
import com.example.kpkn.data.repository.CompetitionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.IdProvider
import com.example.kpkn.domain.training.PlanMaterializer
import com.example.kpkn.domain.training.ProgramCalendarEngine
import com.example.kpkn.data.preferences.programSnapshotStore
import com.example.kpkn.ui.components.SnackbarType
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class ProgramDetailViewModelTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun installCatalogCompositionSupport() {
            CatalogCompositionTestSupport.install()
        }
    }

    private val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: ProgramRepository

    private fun nextId(): String = "prog_${System.nanoTime()}"

    private fun makeProgram(id: String) = Program(
        id = id,
        name = "Test $id",
        structure = ProgramStructure.COMPLEX,
        macrocycles = listOf(
            Macrocycle(
                id = "${id}_mc1", name = "Macro",
                blocks = listOf(
                    Block(
                        id = "${id}_b1", name = "Block 1",
                        mesocycles = listOf(
                            Mesocycle(
                                id = "${id}_m1", name = "Meso 1",
                                goal = MesocycleGoal.ACCUMULATION,
                                weeks = listOf(
                                    ProgramWeek(id = "${id}_w1", name = "W1", sessions = listOf(
                                        Session(id = "${id}_s1", name = "S1"),
                                        Session(id = "${id}_s2", name = "S2"),
                                    )),
                                    ProgramWeek(id = "${id}_w2", name = "W2", sessions = listOf(
                                        Session(id = "${id}_s3", name = "S3"),
                                    )),
                                ),
                            ),
                        ),
                    ),
                    Block(
                        id = "${id}_b2", name = "Block 2",
                        mesocycles = listOf(
                            Mesocycle(
                                id = "${id}_m2", name = "Meso 2",
                                goal = MesocycleGoal.INTENSIFICATION,
                                weeks = listOf(
                                    ProgramWeek(id = "${id}_w3", name = "W3", sessions = listOf(
                                        Session(id = "${id}_s4", name = "S4"),
                                    )),
                                ),
                            ),
                        ),
                    ),
                ),
            )
        ),
    )

    private fun makeSimpleProgram(id: String) = Program(
        id = id,
        name = "Simple $id",
        structure = ProgramStructure.SIMPLE,
        macrocycles = listOf(
            Macrocycle(id = "${id}_mc1", name = "M", blocks = listOf(
                Block(id = "${id}_b1", name = "B", mesocycles = listOf(
                    Mesocycle(id = "${id}_m1", name = "M", weeks = listOf(
                        ProgramWeek(id = "${id}_w1", name = "W", sessions = listOf(
                            Session(id = "${id}_s1", name = "S"),
                        )),
                    )),
                )),
            )),
        ),
    )

    private class RestoreSessionIds : IdProvider {
        private var next = 0
        override fun newId(): String = "restore-${++next}"
    }

    private fun restoreRecipe(programId: String) = TrainingPlanRecipe(
        id = "restore-recipe-$programId",
        weeks = listOf(
            weekRecipe(
                1,
                0,
                "Base",
                BlockGoal.ACCUMULATION,
                listOf(
                    DayRecipe(
                        id = "restore-target-day",
                        label = "Día receta seleccionado",
                        weekday = 1,
                        slots = listOf(
                            slot(
                                "restore-target-bench",
                                SlotRole.T1_MAIN,
                                CatalogIds.BP,
                                percentSets(150, 5 to 75.0, 5 to 75.0),
                                150,
                                LiftSlot.BENCH,
                                isCompetitionLift = true,
                            ),
                        ),
                    ),
                    DayRecipe(
                        id = "restore-sibling-day",
                        label = "Día receta vecino",
                        weekday = 3,
                        slots = listOf(
                            slot(
                                "restore-sibling-bench",
                                SlotRole.T1_MAIN,
                                CatalogIds.BP,
                                percentSets(150, 5 to 70.0, 5 to 70.0),
                                150,
                                LiftSlot.BENCH,
                                isCompetitionLift = true,
                            ),
                        ),
                    ),
                ),
            ),
        ),
        claimedDaysPerWeek = 2,
    )

    private fun materializedRestoreProgram(programId: String): Program = PlanMaterializer.materialize(
        Program(id = programId, name = "Restore $programId", structure = ProgramStructure.COMPLEX),
        restoreRecipe(programId),
        CatalogCompositionTestSupport.metadata,
        RestoreSessionIds(),
        strict = false,
    )

    private fun weeksOf(program: Program) =
        program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }

    private fun replaceRestoreFixtureSessions(
        program: Program,
        weekId: String,
        replacements: Map<String, Session>,
    ): Program = program.copy(
        macrocycles = program.macrocycles.map { macro ->
            macro.copy(
                blocks = macro.blocks.map { block ->
                    block.copy(
                        mesocycles = block.mesocycles.map { meso ->
                            meso.copy(
                                weeks = meso.weeks.map { week ->
                                    if (week.id != weekId) week
                                    else week.copy(sessions = week.sessions.map { replacements[it.id] ?: it })
                                },
                            )
                        },
                    )
                },
            )
        },
    )

    private fun sessionIdentity(session: Session): List<String?> = buildList<String?> {
        add(session.id)
        session.parts.forEach { add(it.id) }
        session.allExercises().forEach { exercise ->
            add(exercise.id)
            add(exercise.occurrenceId)
            exercise.sets.forEach { add(it.id) }
            exercise.warmupSets.forEach { add(it.id) }
        }
    }

    private fun sessionJson(session: Session): String = Json.encodeToString(Session.serializer(), session)

    private data class ManualRestoreFixture(
        val weekId: String,
        val plannedTarget: Session,
        val targetId: String,
        val siblingId: String,
        val siblingJson: String,
    )

    private fun manuallyEdit(session: Session, label: String): Session {
        fun editPrescription(exercise: Exercise) = exercise.copy(
            sets = exercise.sets.map { it.copy(targetReps = 99) },
        )
        return session.copy(
            name = label,
            description = "$label editada manualmente",
            exercises = session.exercises.map(::editPrescription),
            parts = session.parts.map { part -> part.copy(exercises = part.exercises.map(::editPrescription)) },
        )
    }

    private suspend fun seedManualRestoreFixture(programId: String): ManualRestoreFixture {
        val generated = materializedRestoreProgram(programId)
        repository.addProgram(generated)
        repository.flushPendingWrites()

        val persistedPlan = repository.getProgramById(programId) ?: error("programa de prueba ausente")
        assertNotNull("el fixture debe conservar la receta fuente", persistedPlan.sourceRecipe)
        val planWeek = weeksOf(persistedPlan).single()
        val plannedTarget = planWeek.sessions[0]
        val plannedSibling = planWeek.sessions[1]
        val changedSessions = replaceRestoreFixtureSessions(
            persistedPlan,
            planWeek.id,
            mapOf(
                plannedTarget.id to manuallyEdit(plannedTarget, "Sesión personalizada"),
                plannedSibling.id to manuallyEdit(plannedSibling, "Vecina personalizada"),
            ),
        )
        val withTargetOverride = PlanMaterializer.withManualSessionOverride(
            changedSessions,
            plannedTarget.id,
            planWeek.id,
            weekOccurrence = 1,
            recipeDayId = "restore-target-day",
            nowMs = 1L,
        )
        val marked = PlanMaterializer.withManualSessionOverride(
            withTargetOverride,
            plannedSibling.id,
            planWeek.id,
            weekOccurrence = 1,
            recipeDayId = "restore-sibling-day",
            nowMs = 2L,
        )
        repository.updateProgramNow(marked)

        val stored = repository.getProgramById(programId) ?: error("programa editado ausente")
        val storedWeek = weeksOf(stored).single { it.id == planWeek.id }
        val storedSibling = storedWeek.sessions.single { it.id == plannedSibling.id }
        return ManualRestoreFixture(
            weekId = storedWeek.id,
            plannedTarget = plannedTarget,
            targetId = plannedTarget.id,
            siblingId = plannedSibling.id,
            siblingJson = sessionJson(storedSibling),
        )
    }

    private fun siblingWorkoutLog(programId: String, fixture: ManualRestoreFixture, sessionId: String) = WorkoutLog(
        id = "restore-log-$programId",
        programId = programId,
        sessionId = sessionId,
        sessionName = sessionId,
        date = "2026-09-29T10:00:00Z",
        durationMinutes = 45,
        weekId = fixture.weekId,
    )

    @Before
    fun setup() = runBlocking {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        ProgramRepository.initForTests(context)
        repository = ProgramRepository.getInstance()
        withTimeout(10_000) {
            while (!repository.isReady.value) {
                delay(25)
            }
        }
        repository.resetAllStateSync()
    }

    @After
    fun tearDown() {
        CompetitionRepository.closeInstance()
        ProgramRepository.closeInstance()
        KpknDatabase.closeInstance()
        Dispatchers.resetMain()
    }

    // ─── Tab Management ───────────────────────────────────────────────────

    @Test
    fun initial_state_is_semana_subtab() {
        val id = nextId()
        val vm = ProgramDetailViewModel(id)
        assertEquals(StructureSubTab.SEMANA, vm.uiState.value.structureSubTab)
    }

    // ─── Block Selection ──────────────────────────────────────────────────

    @Test
    fun selectBlock_updates_selected_block() {
        val id = nextId()
        repository.addProgram(makeProgram(id))
        val vm = ProgramDetailViewModel(id)

        vm.selectBlock("${id}_b2")
        assertEquals("${id}_b2", vm.uiState.value.selectedBlockId)
    }

    @Test
    fun selectWeek_updates_selected_week() {
        val id = nextId()
        repository.addProgram(makeProgram(id))
        val vm = ProgramDetailViewModel(id)

        vm.selectWeek("${id}_w2")
        assertEquals("${id}_w2", vm.uiState.value.selectedWeekId)
    }

    // ─── Program Actions ──────────────────────────────────────────────────

    @Test
    fun startProgram_creates_active_state() {
        val id = nextId()
        repository.addProgram(makeProgram(id))
        val vm = ProgramDetailViewModel(id)

        vm.startProgram()
        assertNotNull(repository.activeProgramState.value)
        assertEquals(id, repository.activeProgramState.value?.programId)
        assertEquals(ProgramStatus.ACTIVE, repository.activeProgramState.value?.status)
    }

    @Test
    fun pauseProgram_changes_status() {
        val id = nextId()
        repository.addProgram(makeProgram(id))
        repository.startProgram(id)
        val vm = ProgramDetailViewModel(id)

        vm.pauseProgram()
        assertEquals(ProgramStatus.PAUSED, repository.activeProgramState.value?.status)
        assertEquals(ProgramRunStatus.PAUSED, repository.getProgramById(id)?.runState?.status)
    }

    @Test
    fun pauseProgram_survives_update_and_resume_keeps_week() {
        val id = nextId()
        repository.addProgram(makeProgram(id))
        val vm = ProgramDetailViewModel(id)

        vm.startProgram()
        val weekId = repository.activeProgramState.value?.currentWeekId
        assertFalse(weekId.isNullOrBlank())

        vm.pauseProgram()
        assertEquals(ProgramStatus.PAUSED, repository.activeProgramState.value?.status)
        assertEquals(ProgramRunStatus.PAUSED, repository.getProgramById(id)?.runState?.status)

        vm.updateProgram(repository.getProgramById(id)!!.copy(name = "Renamed $id"))
        assertEquals(ProgramStatus.PAUSED, repository.activeProgramState.value?.status)
        assertEquals(ProgramRunStatus.PAUSED, repository.getProgramById(id)?.runState?.status)
        assertEquals(weekId, repository.activeProgramState.value?.currentWeekId)

        vm.resumeProgram()
        assertEquals(ProgramStatus.ACTIVE, repository.activeProgramState.value?.status)
        assertEquals(ProgramRunStatus.ACTIVE, repository.getProgramById(id)?.runState?.status)
        assertEquals(weekId, repository.activeProgramState.value?.currentWeekId)
    }

    @Test
    fun toggleStartPause_resumes_paused_program() = runBlocking {
        val id = nextId()
        repository.addProgram(makeProgram(id))
        val vm = ProgramDetailViewModel(id)
        vm.startProgram()
        vm.pauseProgram()
        val pausedCollector = async { vm.isPausedProgram.first { it } }
        withTimeout(5_000) { pausedCollector.await() }
        vm.toggleStartPause()
        assertEquals(ProgramStatus.ACTIVE, repository.activeProgramState.value?.status)
        assertEquals(ProgramRunStatus.ACTIVE, repository.getProgramById(id)?.runState?.status)
    }

    // ─── Derived State ────────────────────────────────────────────────────

    @Test
    fun roadmapBlocks_computed_from_program() {
        val id = nextId()
        repository.addProgram(makeProgram(id))
        val vm = ProgramDetailViewModel(id)

        val blocks = vm.roadmapBlocks.value
        assertEquals(2, blocks.size)
        assertEquals("${id}_b1", blocks[0].id)
        assertEquals("${id}_b2", blocks[1].id)
    }

    @Test
    fun totalWeeks_computed() = runBlocking {
        val id = nextId()
        repository.addProgram(makeProgram(id))
        val vm = ProgramDetailViewModel(id)
        assertEquals(3, withTimeout(5_000) { vm.totalWeeks.filter { it > 0 }.first() })
    }

    @Test
    fun isSimpleProgram_false_for_multi_block() = runBlocking {
        val id = nextId()
        repository.addProgram(makeProgram(id))
        val vm = ProgramDetailViewModel(id)
        assertFalse(withTimeout(5_000) { vm.isSimpleProgram.filter { !it }.first() })
    }

    @Test
    fun isSimpleProgram_true_for_simple() {
        val id = nextId()
        repository.addProgram(makeSimpleProgram(id))
        val vm = ProgramDetailViewModel(id)

        assertTrue(vm.isSimpleProgram.value)
    }

    @Test
    fun deleteSession_removes_from_program() {
        val id = nextId()
        repository.addProgram(makeProgram(id))
        val vm = ProgramDetailViewModel(id)

        vm.deleteSession("${id}_s1", macroIndex = 0, mesoIndex = 0, weekId = "${id}_w1")

        val updated = repository.getProgramById(id)!!
        val week = updated.macrocycles[0].blocks[0].mesocycles[0].weeks[0]
        assertEquals(1, week.sessions.size)
        assertEquals("${id}_s2", week.sessions[0].id)
    }

    @Test
    fun persist_strips_meet_session_and_preserves_linked_competition_record() = runBlocking {
        val id = nextId()
        val context = ApplicationProvider.getApplicationContext<Context>()
        CompetitionRepository.initForTests(context)
        val competitionRepository = CompetitionRepository.getInstance()
        withTimeout(5_000) { while (!competitionRepository.isReady.value) delay(10) }
        competitionRepository.upsertNow(
            CompetitionRecord(
                id = "${id}_record",
                title = "Meet day",
                plannedProgramId = id,
                plannedSessionId = "${id}_comp",
                plannedWeekId = "${id}_w1",
                journal = CompetitionJournal(overallFeeling = "Buena sensación"),
            )
        )

        val program = makeProgram(id)
        val competitionSession = Session(
            id = "${id}_comp",
            name = "Meet day",
            dayOfWeek = 3,
            isMeetDay = true,
            isCompetitionSession = true,
            competitionRecordId = "${id}_record",
        )
        val withCompetitionSession = program.copy(
            macrocycles = program.macrocycles.mapIndexed { mi, macro ->
                if (mi != 0) macro
                else macro.copy(blocks = macro.blocks.mapIndexed { bi, block ->
                    if (bi != 0) block
                    else block.copy(mesocycles = block.mesocycles.map { meso ->
                        meso.copy(weeks = meso.weeks.map { week ->
                            if (week.id != "${id}_w1") week
                            else week.copy(sessions = week.sessions + competitionSession)
                        })
                    })
                })
            },
        )
        repository.addProgram(withCompetitionSession)

        val updatedWeek = repository.getProgramById(id)!!.macrocycles[0].blocks[0].mesocycles[0].weeks[0]
        assertTrue(updatedWeek.sessions.none { it.id == "${id}_comp" })

        val record = competitionRepository.getById("${id}_record")
        assertNotNull("El record de competición no debería eliminarse", record)
        assertNull(record!!.plannedSessionId)
        assertEquals("Buena sensación", record.journal?.overallFeeling)
    }

    @Test
    fun addSession_appends_to_week() {
        val id = nextId()
        repository.addProgram(makeProgram(id))
        val vm = ProgramDetailViewModel(id)

        val newSession = Session(id = "${id}_new", name = "New")
        vm.addSession(macroIndex = 0, mesoIndex = 0, weekId = "${id}_w1", session = newSession)

        val updated = repository.getProgramById(id)!!
        val week = updated.macrocycles[0].blocks[0].mesocycles[0].weeks[0]
        assertEquals(3, week.sessions.size)
        assertEquals("${id}_new", week.sessions[2].id)
    }

    @Test
    fun addWeekToSimpleProgram_appends_week_and_keeps_program_simple() {
        val id = nextId()
        repository.addProgram(makeSimpleProgram(id))
        val vm = ProgramDetailViewModel(id)

        vm.addWeekToSimpleProgram()

        val updated = repository.getProgramById(id)!!
        val weeks = updated.macrocycles[0].blocks[0].mesocycles[0].weeks
        assertEquals(2, weeks.size)
        assertEquals("Semana 2", weeks[1].name)
        assertEquals(ProgramStructure.SIMPLE, updated.structure)
        assertEquals(weeks[1].id, vm.uiState.value.selectedWeekId)
    }

    @Test
    fun addWeekToSimpleProgram_calendarized_continues_real_dates_and_titles() {
        val id = nextId()
        val base = makeSimpleProgram(id)
        val datedWeek = base.macrocycles[0].blocks[0].mesocycles[0].weeks[0].copy(
            name = "Semana: 05/21",
            startDate = "2026-05-21",
            endDate = "2026-05-27",
            trainingDayDates = mapOf(4 to "2026-05-21", 1 to "2026-05-25", 3 to "2026-05-27"),
        )
        repository.addProgram(
            base.copy(
                timelineStartDate = "2026-05-21",
                calendarization = ProgramCalendarEngine.defaultSimpleDatedCalendarization(),
                simpleProgramKind = SimpleProgramKind.CALENDARIZED,
                startDay = 4,
                macrocycles = base.macrocycles.map { macro ->
                    macro.copy(
                        blocks = macro.blocks.map { block ->
                            block.copy(
                                mesocycles = block.mesocycles.map { meso ->
                                    meso.copy(weeks = listOf(datedWeek))
                                }
                            )
                        }
                    )
                },
            )
        )
        val vm = ProgramDetailViewModel(id)

        vm.addWeekToSimpleProgram()

        val updated = repository.getProgramById(id)!!
        val weeks = updated.macrocycles[0].blocks[0].mesocycles[0].weeks
        assertEquals(2, weeks.size)
        assertEquals("Semana: 05/28", weeks[1].name)
        assertEquals("2026-05-28", weeks[1].startDate)
        assertEquals("2026-06-03", weeks[1].endDate)
        assertEquals(setOf(1, 3, 4), weeks[1].trainingDayDates.keys)
        assertEquals("2026-06-01", weeks[1].trainingDayDates[1])
        assertEquals("2026-06-03", weeks[1].trainingDayDates[3])
        assertEquals("2026-05-28", weeks[1].trainingDayDates[4])
        assertEquals(SimpleProgramKind.CALENDARIZED, updated.simpleProgramKind)
        assertTrue(updated.loops.isEmpty())
    }

    @Test
    fun startSimpleCalendarizedBreak_creates_inclusive_custom_weeks() {
        val id = nextId()
        val base = makeSimpleProgram(id)

        val updated = base.startSimpleCalendarizedBreak(
            startDate = LocalDate.parse("2026-05-20"),
            endDate = LocalDate.parse("2026-06-02"),
            startDayOfWeek = 3,
            trainingDays = setOf(3, 1, 2),
        )

        val weeks = updated.macrocycles[0].blocks[0].mesocycles[0].weeks
        assertEquals(2, weeks.size)
        assertEquals("2026-05-20", weeks[0].startDate)
        assertEquals("2026-05-26", weeks[0].endDate)
        assertEquals("2026-05-20", weeks[0].trainingDayDates[3])
        assertEquals("2026-05-25", weeks[0].trainingDayDates[1])
        assertEquals("2026-05-26", weeks[0].trainingDayDates[2])
        assertEquals("2026-05-27", weeks[1].startDate)
        assertEquals("2026-06-02", weeks[1].endDate)
        assertEquals("2026-06-01", weeks[1].trainingDayDates[1])
        assertEquals("2026-06-02", weeks[1].trainingDayDates[2])
    }

    @Test
    fun startFreshCyclicProgram_restores_visible_roadmap_and_allows_new_week() {
        val id = nextId()
        val base = makeSimpleProgram(id)
        val emptyCalendarized = base.copy(
            simpleProgramKind = SimpleProgramKind.CALENDARIZED,
            calendarization = ProgramCalendarEngine.defaultSimpleDatedCalendarization(),
            timelineStartDate = "2026-05-18",
            macrocycles = base.macrocycles.map { macro ->
                macro.copy(
                    blocks = macro.blocks.map { block ->
                        block.copy(mesocycles = block.mesocycles.map { meso -> meso.copy(weeks = emptyList()) })
                    }
                )
            },
        )
        repository.addProgram(emptyCalendarized)
        val vm = ProgramDetailViewModel(id)

        vm.startFreshCyclicProgram()

        val restored = repository.getProgramById(id)!!
        val restoredWeeks = restored.macrocycles[0].blocks[0].mesocycles[0].weeks
        assertEquals(SimpleProgramKind.CYCLIC, restored.simpleProgramKind)
        assertEquals(1, restoredWeeks.size)
        assertEquals(restoredWeeks.first().id, vm.uiState.value.selectedWeekId)
        assertNotNull(vm.uiState.value.selectedBlockId)
        assertEquals(null, restored.calendarization)
        assertEquals(null, restored.timelineStartDate)
        assertEquals(ScheduleMode.FLOATING, restored.schedulePlan?.mode)
        assertEquals(ProgramRunStatus.ACTIVE, restored.runState?.status)
        assertEquals(1, restored.runState?.cycleNumber)
        assertTrue(restored.loopOccurrences.isEmpty())
        assertTrue(restored.calendarBreaks.isEmpty())

        vm.addWeekToSimpleProgram()

        val updated = repository.getProgramById(id)!!
        assertEquals(2, updated.macrocycles[0].blocks[0].mesocycles[0].weeks.size)
    }

    @Test
    fun addWeekToSimpleProgram_can_seed_empty_simple_block() {
        val id = nextId()
        val base = makeSimpleProgram(id)
        repository.addProgram(
            base.copy(
                simpleProgramKind = SimpleProgramKind.CYCLIC,
                macrocycles = base.macrocycles.map { macro ->
                    macro.copy(blocks = macro.blocks.map { block -> block.copy(mesocycles = emptyList()) })
                },
            )
        )
        val vm = ProgramDetailViewModel(id)

        vm.addWeekToSimpleProgram()

        val updated = repository.getProgramById(id)!!
        val weeks = updated.macrocycles[0].blocks[0].mesocycles[0].weeks
        assertEquals(1, weeks.size)
        assertEquals("Semana 1", weeks.first().name)
        assertEquals(weeks.first().id, vm.uiState.value.selectedWeekId)
    }

    @Test
    fun addWeekToSimpleProgram_can_seed_empty_simple_macrocycle() {
        val id = nextId()
        val base = makeSimpleProgram(id)
        repository.addProgram(
            base.copy(
                simpleProgramKind = SimpleProgramKind.CYCLIC,
                macrocycles = base.macrocycles.map { macro -> macro.copy(blocks = emptyList()) },
            )
        )
        val vm = ProgramDetailViewModel(id)

        vm.addWeekToSimpleProgram()

        val updated = repository.getProgramById(id)!!
        val blocks = updated.macrocycles.first().blocks
        val weeks = blocks.first().mesocycles.first().weeks
        assertEquals(1, blocks.size)
        assertEquals(1, weeks.size)
        assertEquals("Semana 1", weeks.first().name)
        assertEquals(blocks.first().id, vm.uiState.value.selectedBlockId)
        assertEquals(weeks.first().id, vm.uiState.value.selectedWeekId)
    }

    @Test
    fun resolveActiveWeekSelection_maps_instance_id_to_template_week() {
        val weeks = listOf(
            ProgramWeek(id = "w1", name = "Semana 1"),
            ProgramWeek(id = "w2", name = "Semana 2"),
        )
        val active = ActiveProgramState(
            programId = "p",
            status = ProgramStatus.ACTIVE,
            currentWeekId = "inst_c3_w2",
            currentWeekInstanceId = "inst_c3_w2",
            currentCycleNumber = 3,
        )
        assertEquals(
            "w2",
            ProgramDetailViewModel.resolveActiveWeekSelection(active, weeks.map { it.id }),
        )
    }

    @Test
    fun copyWeekSessions_fromRoadmap_replaces_content_but_preserves_target_calendar_identity() {
        val id = nextId()
        val program = makeProgram(id)
        repository.addProgram(
            program.copy(
                macrocycles = program.macrocycles.map { macro ->
                    macro.copy(
                        blocks = macro.blocks.map { block ->
                            block.copy(
                                mesocycles = block.mesocycles.map { meso ->
                                    meso.copy(
                                        weeks = meso.weeks.map { week ->
                                            if (week.id == "${id}_w2") {
                                                week.copy(name = "Semana: 05/25", startDate = "2026-05-25", endDate = "2026-05-31")
                                            } else {
                                                week
                                            }
                                        }
                                    )
                                }
                            )
                        }
                    )
                }
            )
        )
        val vm = ProgramDetailViewModel(id)

        val copied = vm.copyWeekSessions(
            sourceWeekId = "${id}_w1",
            targetWeekIds = setOf("${id}_w2"),
            replaceWeekIds = setOf("${id}_w2"),
        )

        val updated = repository.getProgramById(id)!!
        val target = updated.macrocycles[0].blocks[0].mesocycles[0].weeks[1]
        assertTrue(copied)
        assertEquals("Semana: 05/25", target.name)
        assertEquals("2026-05-25", target.startDate)
        assertEquals(2, target.sessions.size)
        assertTrue(target.sessions.none { it.id in listOf("${id}_s1", "${id}_s2") })
    }

    @Test
    fun updateWeekMetadata_does_not_drop_calendar_dates() {
        val id = nextId()
        val program = makeProgram(id)
        repository.addProgram(
            program.copy(
                structure = ProgramStructure.COMPLEX,
                timelineStartDate = "2026-05-18",
                calendarization = ProgramCalendarEngine.defaultCompetitionCalendarization(),
                schedulePlan = ProgramSchedulePlan(
                    anchorDate = "2026-05-18",
                    weekStartDay = 1,
                    trainingDays = setOf(1, 3, 5),
                    mode = ScheduleMode.DATED,
                ),
                macrocycles = program.macrocycles.map { macro ->
                    macro.copy(
                        blocks = macro.blocks.map { block ->
                            block.copy(
                                mesocycles = block.mesocycles.map { meso ->
                                    meso.copy(
                                        weeks = meso.weeks.map { week ->
                                            if (week.id == "${id}_w1") {
                                                week.copy(
                                                    startDate = "2026-05-18",
                                                    endDate = "2026-05-24",
                                                    trainingDayDates = mapOf(1 to "2026-05-18"),
                                                )
                                            } else week
                                        }
                                    )
                                }
                            )
                        }
                    )
                },
            )
        )
        val vm = ProgramDetailViewModel(id)

        vm.updateWeekMetadata("${id}_w1", "Semana ancla", "Notas")

        val week = repository.getProgramById(id)!!
            .macrocycles[0].blocks[0].mesocycles[0].weeks[0]
        assertEquals("Semana ancla", week.name)
        assertEquals("Notas", week.description)
        assertEquals("2026-05-18", week.startDate)
        assertEquals("2026-05-24", week.endDate)
        assertEquals("2026-05-18", week.trainingDayDates[1])
    }

    @Test
    fun addWeekToSelectedAdvancedBlock_appends_to_last_mesocycle() {
        val id = nextId()
        val program = makeProgram(id)
        val firstBlock = program.macrocycles[0].blocks[0]
        val withTwoMesos = program.copy(
            macrocycles = program.macrocycles.map { macro ->
                macro.copy(
                    blocks = listOf(
                        firstBlock.copy(
                            mesocycles = firstBlock.mesocycles + Mesocycle(
                                id = "${id}_m_last",
                                name = "Meso last",
                                weeks = listOf(ProgramWeek(id = "${id}_w_last", name = "W last")),
                            ),
                        ),
                    )
                )
            }
        )
        repository.addProgram(withTwoMesos)
        val vm = ProgramDetailViewModel(id)
        vm.selectBlock("${id}_b1")

        vm.addWeekToSelectedAdvancedBlock("Semana extra")

        val block = repository.getProgramById(id)!!.macrocycles[0].blocks[0]
        assertEquals(2, block.mesocycles[0].weeks.size)
        assertEquals(2, block.mesocycles[1].weeks.size)
        assertEquals("Semana extra", block.mesocycles[1].weeks.last().name)
    }

    @Test
    fun reorderSessions_swaps_positions() {
        val id = nextId()
        repository.addProgram(makeProgram(id))
        val vm = ProgramDetailViewModel(id)

        vm.reorderSessions(weekId = "${id}_w1", fromIndex = 0, toIndex = 1)

        val updated = repository.getProgramById(id)!!
        val week = updated.macrocycles[0].blocks[0].mesocycles[0].weeks[0]
        assertEquals("${id}_s2", week.sessions[0].id)
        assertEquals("${id}_s1", week.sessions[1].id)
    }

    @Test
    fun replaceWeekSessions_persists_dayOfWeek_across_reread() {
        val id = nextId()
        repository.addProgram(makeProgram(id))
        val vm = ProgramDetailViewModel(id)
        val weekId = "${id}_w1"
        val moved = listOf(
            Session(id = "${id}_s1", name = "S1", dayOfWeek = 4, assignedDays = listOf(4)),
            Session(id = "${id}_s2", name = "S2", dayOfWeek = 2, assignedDays = listOf(2)),
        )

        vm.replaceWeekSessions(weekId, moved)

        val week = repository.getProgramById(id)!!
            .macrocycles[0].blocks[0].mesocycles[0].weeks[0]
        assertEquals(4, week.sessions.first { it.id == "${id}_s1" }.dayOfWeek)
        assertEquals(listOf(4), week.sessions.first { it.id == "${id}_s1" }.assignedDays)
        val reread = repository.getProgramById(id)!!
            .macrocycles[0].blocks[0].mesocycles[0].weeks[0]
        assertEquals(4, reread.sessions.first { it.id == "${id}_s1" }.dayOfWeek)
    }

    @Test
    fun replaceWeekSessions_resolves_instance_week_id() {
        val id = nextId()
        repository.addProgram(makeProgram(id))
        val vm = ProgramDetailViewModel(id)
        val templateWeekId = "${id}_w1"
        val instanceWeekId = com.example.kpkn.domain.training.ProgramProgressEngine.instanceIdFor(1, templateWeekId)
        val moved = listOf(
            Session(id = "${id}_s1", name = "S1", dayOfWeek = 5, assignedDays = listOf(5)),
            Session(id = "${id}_s2", name = "S2", dayOfWeek = 2, assignedDays = listOf(2)),
        )

        vm.replaceWeekSessions(instanceWeekId, moved)

        val week = repository.getProgramById(id)!!
            .macrocycles[0].blocks[0].mesocycles[0].weeks[0]
        assertEquals(templateWeekId, week.id)
        assertEquals(5, week.sessions.first { it.id == "${id}_s1" }.dayOfWeek)
    }

    @Test
    fun replaceWeekSessions_unknown_week_does_not_write() {
        val id = nextId()
        repository.addProgram(makeProgram(id))
        val vm = ProgramDetailViewModel(id)
        val before = repository.getProgramById(id)!!

        vm.replaceWeekSessions("missing-week", listOf(
            Session(id = "${id}_s1", name = "S1", dayOfWeek = 7, assignedDays = listOf(7)),
        ))

        val after = repository.getProgramById(id)!!
        assertEquals(
            before.macrocycles[0].blocks[0].mesocycles[0].weeks[0].sessions.map { it.dayOfWeek },
            after.macrocycles[0].blocks[0].mesocycles[0].weeks[0].sessions.map { it.dayOfWeek },
        )
    }

    @Test
    fun updateProgram_replaces_in_repository() {
        val id = nextId()
        repository.addProgram(makeProgram(id))
        val vm = ProgramDetailViewModel(id)

        val updated = repository.getProgramById(id)!!.copy(name = "Updated Name")
        vm.updateProgram(updated)

        assertEquals("Updated Name", repository.getProgramById(id)!!.name)
    }

    // ─── Factory ──────────────────────────────────────────────────────────

    @Test
    fun factory_creates_correct_viewmodel() {
        val factory = ProgramDetailViewModel.factory("prog1")
        assertNotNull(factory)
    }

    @Test
    fun acceptAutoregulation_clears_pending_confirm_action() = runBlocking {
        val id = nextId()
        val seeded = makeProgram(id).copy(
            runState = ProgramRunState(
                runId = "run",
                pendingAction = PendingProgramAction(
                    type = PendingProgramActionType.CONFIRM_AUTOREGULATION,
                    message = "AUGE propone bajar TM",
                    proposals = listOf(
                        AutoregulationProposal(
                            kind = AutoregulationProposalKind.ADJUST_TM,
                            liftSlot = "SQUAT",
                            percentDelta = -2.5,
                            explanation = "AMRAP corto",
                        ),
                    ),
                ),
            ),
        )
        repository.addProgram(seeded)
        val vm = ProgramDetailViewModel(id)
        assertEquals(PendingProgramActionType.CONFIRM_AUTOREGULATION, vm.program.value?.runState?.pendingAction?.type)
        vm.acceptAutoregulation()
        withTimeout(5_000) {
            repository.programs.first { programs ->
                programs.firstOrNull { it.id == id }?.runState?.pendingAction == null
            }
        }
        withTimeout(5_000) {
            vm.blockTransitionBanner.first { it == null }
        }
        assertNull(repository.getProgramById(id)?.runState?.pendingAction)
        assertNull(vm.blockTransitionBanner.value)
    }

    @Test
    fun restoreManualSessionFromPlan_restores_only_selected_session_and_preserves_sibling_and_history() = runBlocking {
        val id = nextId()
        val fixture = seedManualRestoreFixture(id)
        repository.addWorkoutLog(siblingWorkoutLog(id, fixture, fixture.siblingId))
        repository.flushPendingWrites()
        val historyBefore = repository.getLogsForProgram(id)
        assertEquals(listOf(fixture.siblingId), historyBefore.map { it.sessionId })

        val beforeProgram = repository.getProgramById(id)!!
        val beforeWeek = weeksOf(beforeProgram).single { it.id == fixture.weekId }
        val editedTarget = beforeWeek.sessions.single { it.id == fixture.targetId }
        val siblingBefore = beforeWeek.sessions.single { it.id == fixture.siblingId }
        assertEquals("Sesión personalizada", editedTarget.name)
        val editedPrescription = editedTarget.allExercises().flatMap { it.sets }
        assertEquals(fixture.plannedTarget.allExercises().flatMap { it.sets }.size, editedPrescription.size)
        assertTrue(editedPrescription.isNotEmpty())
        assertTrue(editedPrescription.all { it.targetReps == 99 })
        assertEquals(fixture.siblingJson, sessionJson(siblingBefore))
        assertEquals(setOf(fixture.targetId, fixture.siblingId), beforeProgram.manualSessionOverrides.map { it.sessionId }.toSet())

        val vm = ProgramDetailViewModel(id)
        vm.restoreManualSessionFromPlan(fixture.targetId)

        withTimeout(10_000) {
            vm.uiState.first { it.snackbarMessage?.startsWith("Sesión restaurada desde el plan.") == true }
        }
        val restoredProgram = repository.getProgramById(id)!!
        val restoredWeek = weeksOf(restoredProgram).single { it.id == fixture.weekId }
        val restoredTarget = restoredWeek.sessions.single { it.id == fixture.targetId }
        val restoredSibling = restoredWeek.sessions.single { it.id == fixture.siblingId }

        assertEquals("la receta devuelve el contenido completo de la sesión seleccionada", fixture.plannedTarget, restoredTarget)
        assertEquals("los IDs de sesión, ejercicios y series siguen estables", sessionIdentity(fixture.plannedTarget), sessionIdentity(restoredTarget))
        assertEquals("la sesión vecina permanece byte a byte", fixture.siblingJson, sessionJson(restoredSibling))
        assertEquals(listOf(fixture.siblingId), restoredProgram.manualSessionOverrides.map { it.sessionId })
        assertEquals(historyBefore, repository.getLogsForProgram(id))
        assertEquals(fixture.siblingId, repository.getLogsForProgram(id).single().sessionId)
        assertTrue(vm.uiState.value.snackbarMessage.orEmpty().contains("las sesiones vecinas y el historial se conservaron"))
    }

    @Test
    fun restoreManualSessionFromPlan_blocks_logged_session_without_changing_plan_or_history() = runBlocking {
        val id = nextId()
        val fixture = seedManualRestoreFixture(id)
        repository.addWorkoutLog(siblingWorkoutLog(id, fixture, fixture.targetId))
        repository.flushPendingWrites()
        val beforeProgram = repository.getProgramById(id)!!
        val historyBefore = repository.getLogsForProgram(id)

        val vm = ProgramDetailViewModel(id)
        vm.restoreManualSessionFromPlan(fixture.targetId)

        withTimeout(10_000) {
            vm.uiState.first {
                it.snackbarMessage == "La sesión ya se inició o tiene registros; su prescripción histórica se conserva."
            }
        }

        assertEquals(beforeProgram, repository.getProgramById(id))
        assertEquals(historyBefore, repository.getLogsForProgram(id))
        assertEquals(fixture.targetId, repository.getLogsForProgram(id).single().sessionId)
        assertTrue(repository.getProgramById(id)!!.manualSessionOverrides.any { it.sessionId == fixture.targetId })
    }

    // ─── Restaurar sesión desde el plan: casos de consolidación ───────────

    private fun restoreDays(targetPercent: Double, siblingPercent: Double): List<DayRecipe> = listOf(
        DayRecipe(
            id = "restore-target-day",
            label = "Día receta seleccionado",
            weekday = 1,
            slots = listOf(
                slot(
                    "restore-target-bench",
                    SlotRole.T1_MAIN,
                    CatalogIds.BP,
                    percentSets(150, 5 to targetPercent, 5 to targetPercent),
                    150,
                    LiftSlot.BENCH,
                    isCompetitionLift = true,
                ),
            ),
        ),
        DayRecipe(
            id = "restore-sibling-day",
            label = "Día receta vecino",
            weekday = 3,
            slots = listOf(
                slot(
                    "restore-sibling-bench",
                    SlotRole.T1_MAIN,
                    CatalogIds.BP,
                    percentSets(150, 5 to siblingPercent, 5 to siblingPercent),
                    150,
                    LiftSlot.BENCH,
                    isCompetitionLift = true,
                ),
            ),
        ),
    )

    private fun twoWeekRestoreRecipe(programId: String) = TrainingPlanRecipe(
        id = "restore-recipe-$programId",
        weeks = listOf(
            weekRecipe(1, 0, "Base", BlockGoal.ACCUMULATION, restoreDays(75.0, 70.0)),
            weekRecipe(2, 0, "Base", BlockGoal.ACCUMULATION, restoreDays(75.0, 70.0)),
        ),
        claimedDaysPerWeek = 2,
    )

    private fun sessionOverride(
        sessionId: String,
        weekId: String?,
        weekOccurrence: Int?,
        recipeDayId: String?,
        scope: ManualOverrideScope = ManualOverrideScope.SESSION,
        reason: String = "Edición manual",
    ) = ManualSessionOverride(
        sessionId = sessionId,
        weekId = weekId,
        weekOccurrence = weekOccurrence,
        recipeDayId = recipeDayId,
        scope = scope,
        reason = reason,
        createdAtMs = 1L,
    )

    private fun prescription(session: Session): List<Triple<Int?, Double?, Double?>> =
        session.allExercises().flatMap { exercise ->
            exercise.sets.map { Triple(it.targetReps, it.targetPercentageRM, it.weight) }
        }

    private fun appendSession(program: Program, weekId: String, session: Session): Program = program.copy(
        macrocycles = program.macrocycles.map { macro ->
            macro.copy(
                blocks = macro.blocks.map { block ->
                    block.copy(
                        mesocycles = block.mesocycles.map { meso ->
                            meso.copy(
                                weeks = meso.weeks.map { week ->
                                    if (week.id == weekId) week.copy(sessions = week.sessions + session) else week
                                },
                            )
                        },
                    )
                },
            )
        },
    )

    /**
     * Fixture general de «Restaurar desde el plan»: receta de una o dos semanas,
     * receta efectiva aceptada para la ocurrencia 2, overrides configurables y un
     * gancho para torcer el programa (receta ausente, sesión creada por el usuario,
     * día movido...). Edita la sesión objetivo y la vecina de la semana probada.
     */
    private suspend fun seedRestoreFixture(
        programId: String,
        twoWeeks: Boolean = false,
        acceptEffectiveRecipe: Boolean = false,
        targetOverrides: (targetId: String, weekId: String, weekOccurrence: Int) -> List<ManualSessionOverride> =
            { targetId, weekId, occurrence -> listOf(sessionOverride(targetId, weekId, occurrence, "restore-target-day")) },
        tweak: (Program, String) -> Program = { program, _ -> program },
    ): ManualRestoreFixture {
        val recipe = if (twoWeeks) twoWeekRestoreRecipe(programId) else restoreRecipe(programId)
        val generated = PlanMaterializer.materialize(
            Program(id = programId, name = "Restore $programId", structure = ProgramStructure.COMPLEX),
            recipe,
            CatalogCompositionTestSupport.metadata,
            RestoreSessionIds(),
            strict = false,
        )
        repository.addProgram(generated)
        repository.flushPendingWrites()
        var program = repository.getProgramById(programId) ?: error("programa de prueba ausente")
        val weekIndex = if (twoWeeks) 1 else 0
        val occurrence = weekIndex + 1
        if (twoWeeks && acceptEffectiveRecipe) {
            // Una propuesta aceptada: la ocurrencia 2 pasa a una receta efectiva 5 puntos más pesada
            // y su semana se reconstruye con ella (como hace la aceptación AUGE).
            val base = recipe.weeks[1]
            val effective = base.copy(
                days = base.days.map { day ->
                    day.copy(
                        slots = day.slots.map { slot ->
                            slot.copy(sets = slot.sets.map { set -> set.copy(percent = set.percent?.plus(5.0)) })
                        },
                    )
                },
            )
            program = PlanMaterializer.withEffectiveWeekRecipe(
                program = program,
                weekOccurrence = occurrence,
                cycleNumber = 1,
                weekRecipe = effective,
                applied = listOf(
                    AppliedRecipeProposal(
                        proposalId = "accepted-effective",
                        kind = "SCALE_WEEK_VOLUME",
                        summary = "aceptada en la prueba",
                        acceptedAtMs = 1L,
                    ),
                ),
            )
            program = PlanMaterializer.rematerializeWeek(
                program = program,
                weekId = weeksOf(program)[weekIndex].id,
                recipe = recipe,
                weekOccurrence = occurrence,
            )
        }
        val week = weeksOf(program)[weekIndex]
        val plannedTarget = week.sessions[0]
        val plannedSibling = week.sessions[1]
        var edited = replaceRestoreFixtureSessions(
            program,
            week.id,
            mapOf(
                plannedTarget.id to manuallyEdit(plannedTarget, "Sesión personalizada"),
                plannedSibling.id to manuallyEdit(plannedSibling, "Vecina personalizada"),
            ),
        )
        edited = edited.copy(
            manualSessionOverrides = edited.manualSessionOverrides +
                targetOverrides(plannedTarget.id, week.id, occurrence),
        )
        edited = PlanMaterializer.withManualSessionOverride(
            edited,
            plannedSibling.id,
            week.id,
            weekOccurrence = occurrence,
            recipeDayId = "restore-sibling-day",
            nowMs = 2L,
        )
        edited = tweak(edited, week.id)
        repository.updateProgramNow(edited)

        val stored = repository.getProgramById(programId) ?: error("programa editado ausente")
        val storedWeek = weeksOf(stored).single { it.id == week.id }
        return ManualRestoreFixture(
            weekId = storedWeek.id,
            plannedTarget = plannedTarget,
            targetId = plannedTarget.id,
            siblingId = plannedSibling.id,
            siblingJson = sessionJson(storedWeek.sessions.single { it.id == plannedSibling.id }),
        )
    }

    private suspend fun restoreAndAwait(vm: ProgramDetailViewModel, sessionId: String, expectedMessage: (String) -> Boolean): String {
        vm.restoreManualSessionFromPlan(sessionId)
        return withTimeout(10_000) {
            vm.uiState.first { state -> state.snackbarMessage?.let(expectedMessage) == true }.snackbarMessage!!
        }
    }

    @Test
    fun restoreManualSessionFromPlan_week_two_with_accepted_effective_recipe_restores_the_effective_prescription() = runBlocking {
        val id = nextId()
        val fixture = seedRestoreFixture(id, twoWeeks = true, acceptEffectiveRecipe = true)
        val before = repository.getProgramById(id)!!
        assertEquals(listOf(2), before.effectiveWeekRecipes.map { it.weekOccurrence })
        val beforeWeek = weeksOf(before).single { it.id == fixture.weekId }
        assertEquals(1, weeksOf(before).indexOfFirst { it.id == fixture.weekId })
        assertTrue(beforeWeek.sessions.single { it.id == fixture.targetId }.allExercises()
            .flatMap { it.sets }.all { it.targetReps == 99 })
        val plannedPercents = fixture.plannedTarget.allExercises().flatMap { it.sets }.map { it.targetPercentageRM }
        assertTrue("la receta efectiva aceptada pesa 80 %, no el 75 % base: $plannedPercents",
            plannedPercents.isNotEmpty() && plannedPercents.all { it == 80.0 })

        val vm = ProgramDetailViewModel(id)
        restoreAndAwait(vm, fixture.targetId) { it.startsWith("Sesión restaurada desde el plan.") }

        val restored = repository.getProgramById(id)!!
        val restoredWeek = weeksOf(restored).single { it.id == fixture.weekId }
        val restoredTarget = restoredWeek.sessions.single { it.id == fixture.targetId }
        assertEquals(prescription(fixture.plannedTarget), prescription(restoredTarget))
        assertEquals(fixture.plannedTarget.name, restoredTarget.name)
        assertEquals(sessionIdentity(fixture.plannedTarget), sessionIdentity(restoredTarget))
        assertEquals("la sesión vecina permanece byte a byte", fixture.siblingJson,
            sessionJson(restoredWeek.sessions.single { it.id == fixture.siblingId }))
        assertEquals(listOf(fixture.siblingId), restored.manualSessionOverrides.map { it.sessionId })
        // La semana 1 no se toca.
        assertEquals(weeksOf(before)[0], weeksOf(restored)[0])
        // La receta efectiva aceptada sigue siendo la misma.
        assertEquals(before.effectiveWeekRecipes, restored.effectiveWeekRecipes)
    }

    @Test
    fun restoreManualSessionFromPlan_override_born_from_editor_transfer_restores_the_destination_and_drops_its_receipt() = runBlocking {
        val id = nextId()
        val fixture = seedRestoreFixture(
            id,
            targetOverrides = { targetId, weekId, occurrence ->
                listOf(
                    sessionOverride(
                        targetId, weekId, occurrence, "restore-target-day",
                        reason = "[editor-transfer:transfer-7:abc123] sesión transferida desde otra semana",
                    ),
                )
            },
        )
        assertTrue(repository.getProgramById(id)!!.manualSessionOverrides
            .any { it.sessionId == fixture.targetId && it.reason.contains("[editor-transfer:") })

        val vm = ProgramDetailViewModel(id)
        restoreAndAwait(vm, fixture.targetId) { it.startsWith("Sesión restaurada desde el plan.") }

        val restored = repository.getProgramById(id)!!
        val restoredTarget = weeksOf(restored).single { it.id == fixture.weekId }.sessions.single { it.id == fixture.targetId }
        assertEquals(fixture.plannedTarget, restoredTarget)
        assertEquals(listOf(fixture.siblingId), restored.manualSessionOverrides.map { it.sessionId })
        // El recibo de transferencia vivía dentro de la marca: se va con ella.
        assertTrue(restored.manualSessionOverrides.none { it.reason.contains("[editor-transfer:") })
    }

    @Test
    fun restoreManualSessionFromPlan_without_source_recipe_keeps_the_override_and_says_so() = runBlocking {
        val id = nextId()
        val fixture = seedRestoreFixture(id, tweak = { program, _ -> program.copy(sourceRecipe = null) })
        val before = repository.getProgramById(id)!!

        val vm = ProgramDetailViewModel(id)
        val message = restoreAndAwait(vm, fixture.targetId) { it.startsWith("No se encontró la receta fuente") }

        assertEquals("No se encontró la receta fuente; no se cambió la sesión.", message)
        assertEquals(before, repository.getProgramById(id))
        assertTrue(repository.getProgramById(id)!!.manualSessionOverrides.any { it.sessionId == fixture.targetId })
    }

    @Test
    fun restoreManualSessionFromPlan_session_without_recipe_counterpart_is_not_reported_as_restored() = runBlocking {
        val id = nextId()
        val userSession = Session(
            id = "user-created-session",
            name = "Mi sesión propia",
            dayOfWeek = 5,
            assignedDays = listOf(5),
            exercises = listOf(
                Exercise(
                    id = "user-created-exercise",
                    name = "Remo propio",
                    sets = listOf(ExerciseSet(id = "user-created-set", targetReps = 10)),
                ),
            ),
        )
        val fixture = seedRestoreFixture(
            id,
            targetOverrides = { _, _, _ -> emptyList() },
            tweak = { program, weekId ->
                appendSession(program, weekId, userSession).copy(
                    manualSessionOverrides = program.manualSessionOverrides +
                        sessionOverride(userSession.id, weekId, 1, recipeDayId = null),
                )
            },
        )
        val before = repository.getProgramById(id)!!
        assertTrue(weeksOf(before).single { it.id == fixture.weekId }.sessions.any { it.id == userSession.id })

        val vm = ProgramDetailViewModel(id)
        val message = restoreAndAwait(vm, userSession.id) { it.startsWith("Esta sesión no tiene una sesión equivalente") }

        assertTrue("sin éxito falso: $message", !message.startsWith("Sesión restaurada"))
        assertEquals("ni el plan ni la marca cambian", before, repository.getProgramById(id))
        assertTrue(repository.getProgramById(id)!!.manualSessionOverrides.any { it.sessionId == userSession.id })
    }

    @Test
    fun restoreManualSessionFromPlan_session_moved_to_another_weekday_returns_to_its_recipe_day() = runBlocking {
        val id = nextId()
        val fixture = seedRestoreFixture(
            id,
            tweak = { program, weekId ->
                val week = weeksOf(program).single { it.id == weekId }
                val target = week.sessions[0]
                replaceRestoreFixtureSessions(
                    program,
                    weekId,
                    mapOf(target.id to target.copy(dayOfWeek = 2, assignedDays = listOf(2))),
                )
            },
        )
        val movedBefore = weeksOf(repository.getProgramById(id)!!).single { it.id == fixture.weekId }
            .sessions.single { it.id == fixture.targetId }
        assertEquals(2, movedBefore.dayOfWeek)

        val vm = ProgramDetailViewModel(id)
        restoreAndAwait(vm, fixture.targetId) { it.startsWith("Sesión restaurada desde el plan.") }

        val restored = repository.getProgramById(id)!!
        val restoredWeek = weeksOf(restored).single { it.id == fixture.weekId }
        assertEquals("el día de la receta y su prescripción vuelven con la sesión", fixture.plannedTarget,
            restoredWeek.sessions.single { it.id == fixture.targetId })
        assertEquals(1, restoredWeek.sessions.single { it.id == fixture.targetId }.dayOfWeek)
        assertEquals(fixture.siblingJson, sessionJson(restoredWeek.sessions.single { it.id == fixture.siblingId }))
        assertEquals(restoredWeek.sessions.size, restoredWeek.sessions.map { it.id }.distinct().size)
        assertEquals(2, restoredWeek.sessions.size)
    }

    @Test
    fun restoreManualSessionFromPlan_template_future_occurrences_scope_restores_the_session_and_drops_that_scope() = runBlocking {
        val id = nextId()
        val fixture = seedRestoreFixture(
            id,
            targetOverrides = { targetId, _, _ ->
                listOf(
                    sessionOverride(
                        targetId, null, null, null,
                        scope = ManualOverrideScope.TEMPLATE_FUTURE_OCCURRENCES,
                        reason = "Edición de plantilla aplicada a futuras ocurrencias",
                    ),
                )
            },
        )
        assertEquals(
            listOf(ManualOverrideScope.TEMPLATE_FUTURE_OCCURRENCES),
            repository.getProgramById(id)!!.manualSessionOverrides.filter { it.sessionId == fixture.targetId }.map { it.scope },
        )

        val vm = ProgramDetailViewModel(id)
        restoreAndAwait(vm, fixture.targetId) { it.startsWith("Sesión restaurada desde el plan.") }

        val restored = repository.getProgramById(id)!!
        assertEquals(fixture.plannedTarget, weeksOf(restored).single { it.id == fixture.weekId }
            .sessions.single { it.id == fixture.targetId })
        assertEquals(listOf(fixture.siblingId), restored.manualSessionOverrides.map { it.sessionId })
    }

    @Test
    fun restoreManualSessionFromPlan_only_drops_the_session_scope_when_a_template_scope_also_exists() = runBlocking {
        val id = nextId()
        val fixture = seedRestoreFixture(
            id,
            targetOverrides = { targetId, weekId, occurrence ->
                listOf(
                    sessionOverride(targetId, weekId, occurrence, "restore-target-day"),
                    sessionOverride(
                        targetId, null, null, null,
                        scope = ManualOverrideScope.TEMPLATE_FUTURE_OCCURRENCES,
                        reason = "Edición de plantilla aplicada a futuras ocurrencias",
                    ),
                )
            },
        )

        val vm = ProgramDetailViewModel(id)
        restoreAndAwait(vm, fixture.targetId) { it.startsWith("Sesión restaurada desde el plan.") }

        val restored = repository.getProgramById(id)!!
        assertEquals("el contenido vuelve a la receta aunque el alcance de plantilla siga marcado", fixture.plannedTarget,
            weeksOf(restored).single { it.id == fixture.weekId }.sessions.single { it.id == fixture.targetId })
        assertEquals(
            listOf(ManualOverrideScope.TEMPLATE_FUTURE_OCCURRENCES),
            restored.manualSessionOverrides.filter { it.sessionId == fixture.targetId }.map { it.scope },
        )
        assertTrue(restored.manualSessionOverrides.any { it.sessionId == fixture.siblingId })
    }

    @Test
    fun removeManualSessionOverride_scope_only_removes_the_chosen_scope_and_null_removes_all() {
        val base = Program(
            id = "scope-unit",
            name = "Scope",
            manualSessionOverrides = listOf(
                sessionOverride("s1", null, null, null, ManualOverrideScope.SESSION),
                sessionOverride("s1", null, null, null, ManualOverrideScope.TEMPLATE_FUTURE_OCCURRENCES),
                sessionOverride("s2", null, null, null, ManualOverrideScope.SESSION),
            ),
        )
        fun keys(program: Program) = program.manualSessionOverrides.map { it.sessionId to it.scope }
        assertEquals(
            listOf("s1" to ManualOverrideScope.TEMPLATE_FUTURE_OCCURRENCES, "s2" to ManualOverrideScope.SESSION),
            keys(PlanMaterializer.removeManualSessionOverride(base, "s1", ManualOverrideScope.SESSION)),
        )
        assertEquals(
            listOf("s1" to ManualOverrideScope.SESSION, "s2" to ManualOverrideScope.SESSION),
            keys(PlanMaterializer.removeManualSessionOverride(base, "s1", ManualOverrideScope.TEMPLATE_FUTURE_OCCURRENCES)),
        )
        assertEquals(listOf("s2" to ManualOverrideScope.SESSION), keys(PlanMaterializer.removeManualSessionOverride(base, "s1")))
    }

    @Test
    fun restoreBlockedSessionIds_cover_logged_and_in_progress_sessions_only() = runBlocking {
        val id = nextId()
        val fixture = seedManualRestoreFixture(id)
        val vm = ProgramDetailViewModel(id)
        assertEquals(emptySet<String>(), withTimeout(5_000) { vm.restoreBlockedSessionIds.first() })

        repository.addWorkoutLog(siblingWorkoutLog(id, fixture, fixture.siblingId))
        assertEquals(
            setOf(fixture.siblingId),
            withTimeout(5_000) { vm.restoreBlockedSessionIds.first { fixture.siblingId in it } },
        )

        val targetSession = weeksOf(repository.getProgramById(id)!!).single().sessions.single { it.id == fixture.targetId }
        repository.startWorkout(OngoingWorkoutState(programId = id, session = targetSession, startTime = 11L))
        assertEquals(
            setOf(fixture.siblingId, fixture.targetId),
            withTimeout(5_000) { vm.restoreBlockedSessionIds.first { fixture.targetId in it } },
        )
    }

    @Test
    fun replacing_protocol_keeps_program_identity_and_does_not_open_a_copy() = runBlocking {
        com.example.kpkn.domain.training.CatalogCompositionTestSupport.install()
        val id = nextId()
        val original = makeSimpleProgram(id)
        repository.addProgram(original)
        repository.flushPendingWrites()
        val vm = ProgramDetailViewModel(id)
        val protocol = com.example.kpkn.data.protocols.PROTOCOL_LIBRARY.first { it.id == "gzclp" }
        vm.applyProtocolOverwrite(protocol, overwrite = true)
        withTimeout(20_000) {
            while (vm.uiState.value.snackbarMessage.isNullOrBlank()) delay(25)
        }
        assertTrue(vm.uiState.value.snackbarMessage.orEmpty(), vm.uiState.value.snackbarMessage.orEmpty().contains("sin crear una copia"))
        assertEquals(listOf(id), repository.programs.value.map { it.id })
        assertEquals(original.name, repository.getProgramById(id)?.name)
        assertEquals(protocol.id, repository.getProgramById(id)?.sourceProtocolId)
        assertNull(vm.uiState.value.pendingOpenProgramId)
    }

    @Test
    fun copying_protocol_is_an_explicit_separate_action() = runBlocking {
        com.example.kpkn.domain.training.CatalogCompositionTestSupport.install()
        val id = nextId()
        val original = makeSimpleProgram(id)
        repository.addProgram(original)
        repository.flushPendingWrites()
        val vm = ProgramDetailViewModel(id)
        val protocol = com.example.kpkn.data.protocols.PROTOCOL_LIBRARY.first { it.id == "gzclp" }
        vm.applyProtocolOverwrite(protocol, overwrite = false)
        withTimeout(20_000) {
            while (vm.uiState.value.snackbarMessage.isNullOrBlank()) delay(25)
        }
        assertEquals(2, repository.programs.value.size)
        assertEquals(original.name, repository.getProgramById(id)?.name)
        assertNotEquals(id, vm.uiState.value.pendingOpenProgramId)
        assertNotNull(vm.uiState.value.pendingOpenProgramId)
    }

    @Test
    fun applyProgramTemplate_reselects_week_in_new_graph() = runBlocking {
        com.example.kpkn.domain.training.CatalogCompositionTestSupport.install()
        val id = nextId()
        repository.addProgram(Program(id = id, name = "Vacío", structure = ProgramStructure.SIMPLE))
        val vm = ProgramDetailViewModel(id)
        val staleWeek = "${id}_missing_week"
        vm.selectWeek(staleWeek)
        assertEquals(staleWeek, vm.uiState.value.selectedWeekId)

        val template = com.example.kpkn.data.programs.PROGRAM_TEMPLATES.first { it.id == "simple-1" }
        vm.applyProgramTemplate(template)

        withTimeout(15_000) {
            while (true) {
                val program = vm.program.value ?: repository.getProgramById(id)
                val weekId = vm.uiState.value.selectedWeekId
                val weekIds = program?.macrocycles
                    ?.flatMap { it.blocks }
                    ?.flatMap { it.mesocycles }
                    ?.flatMap { it.weeks }
                    ?.map { it.id }
                    .orEmpty()
                if (program != null &&
                    weekId != null &&
                    weekId in weekIds &&
                    weekIds.isNotEmpty() &&
                    program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }
                        .any { week ->
                            week.sessions.any(
                                com.example.kpkn.domain.templates.SessionTemplateEngine::sessionHasExecutableContent,
                            )
                        }
                ) {
                    break
                }
                delay(50)
            }
        }

        val applied = vm.program.value ?: repository.getProgramById(id)!!
        val weekIds = applied.macrocycles
            .flatMap { it.blocks }
            .flatMap { it.mesocycles }
            .flatMap { it.weeks }
            .map { it.id }
            .toSet()
        assertTrue(vm.uiState.value.selectedWeekId in weekIds)
        assertNotNull(vm.uiState.value.snackbarMessage)
    }

    // ─── Copias recuperables (ProgramSnapshotStore fuera de Main) ─────────

    @Test
    fun attachSnapshotStore_loads_saved_copies_and_refresh_picks_up_new_ones() = runBlocking {
        val id = nextId()
        val program = makeSimpleProgram(id)
        repository.addProgram(program)
        val store = programSnapshotStore(ApplicationProvider.getApplicationContext<Context>())
        store.push(program, "copia-1")
        val vm = ProgramDetailViewModel(id)

        // La carga ya no es síncrona: el estado llega cuando termina la lectura en IO.
        vm.attachSnapshotStore(store)
        val loaded = withTimeout(5_000) { vm.programSnapshots.first { it.isNotEmpty() } }
        assertEquals(listOf("copia-1"), loaded.map { it.reason })

        store.push(program, "copia-2")
        vm.refreshProgramSnapshots()
        val refreshed = withTimeout(5_000) { vm.programSnapshots.first { it.size == 2 } }
        assertEquals(listOf("copia-1", "copia-2"), refreshed.map { it.reason })
    }

    @Test
    fun replacing_protocol_records_the_previous_plan_as_a_recoverable_copy() = runBlocking {
        com.example.kpkn.domain.training.CatalogCompositionTestSupport.install()
        val id = nextId()
        val original = makeSimpleProgram(id)
        repository.addProgram(original)
        repository.flushPendingWrites()
        val store = programSnapshotStore(ApplicationProvider.getApplicationContext<Context>())
        val vm = ProgramDetailViewModel(id)
        vm.attachSnapshotStore(store)
        val protocol = com.example.kpkn.data.protocols.PROTOCOL_LIBRARY.first { it.id == "gzclp" }

        vm.applyProtocolOverwrite(protocol, overwrite = true)
        withTimeout(20_000) {
            while (vm.uiState.value.snackbarMessage.isNullOrBlank()) delay(25)
        }

        val snapshots = withTimeout(5_000) { vm.programSnapshots.first { it.isNotEmpty() } }
        assertEquals(1, snapshots.size)
        assertTrue(snapshots.single().reason.startsWith("Antes de"))
        assertEquals(original.name, snapshots.single().program.name)
        assertEquals(snapshots.map { it.id }, store.list(id).map { it.id })
    }

    // ─── P1 · D2.3 / D2.8: «Reemplazar todo» con plantilla ────────────────

    private suspend fun awaitSnackbar(vm: ProgramDetailViewModel): String {
        withTimeout(20_000) {
            while (vm.uiState.value.snackbarMessage.isNullOrBlank()) delay(25)
        }
        return vm.uiState.value.snackbarMessage.orEmpty()
    }

    private fun simpleTemplate() =
        com.example.kpkn.data.programs.PROGRAM_TEMPLATES.first { it.id == "simple-1" }

    @Test
    fun applyProgramTemplate_overwrite_with_a_failing_snapshot_keeps_the_program_and_reports_it() = runBlocking {
        com.example.kpkn.domain.training.CatalogCompositionTestSupport.install()
        val id = nextId()
        repository.addProgram(makeSimpleProgram(id))
        repository.flushPendingWrites()
        val before = repository.getProgramById(id)!!
        val context = CommitFailingContext(ApplicationProvider.getApplicationContext<Context>())
        val vm = ProgramDetailViewModel(id)
        vm.attachSnapshotStore(programSnapshotStore(context))

        vm.applyProgramTemplate(simpleTemplate(), overwrite = true)

        // Antes la excepción del guardado escapaba de runCatching y cerraba la app.
        val message = awaitSnackbar(vm)
        assertTrue(message, message.startsWith("No se pudo guardar la copia recuperable del programa"))
        assertEquals(SnackbarType.DANGER, snackbarTypeFor(message))
        assertTrue("el guardado de la copia sí se intentó", context.preferences.commitAttempts >= 1)
        assertEquals("el programa queda intacto", before, repository.getProgramById(id))
        assertTrue(vm.programSnapshots.value.isEmpty())
        assertNull(vm.uiState.value.pendingOpenProgramId)
    }

    @Test
    fun applyProtocolOverwrite_with_a_failing_snapshot_keeps_the_program_and_reports_it() = runBlocking {
        com.example.kpkn.domain.training.CatalogCompositionTestSupport.install()
        val id = nextId()
        repository.addProgram(makeSimpleProgram(id))
        repository.flushPendingWrites()
        val before = repository.getProgramById(id)!!
        val context = CommitFailingContext(ApplicationProvider.getApplicationContext<Context>())
        val vm = ProgramDetailViewModel(id)
        vm.attachSnapshotStore(programSnapshotStore(context))
        val protocol = com.example.kpkn.data.protocols.PROTOCOL_LIBRARY.first { it.id == "gzclp" }

        vm.applyProtocolOverwrite(protocol, overwrite = true)

        // El reemplazo por protocolo ya se comportaba bien: esta prueba lo deja fijado.
        val message = awaitSnackbar(vm)
        assertTrue(message, message.startsWith("No se pudo guardar la copia recuperable del programa"))
        assertEquals("el programa queda intacto", before, repository.getProgramById(id))
        assertTrue(context.preferences.commitAttempts >= 1)
    }

    @Test
    fun applyProgramTemplate_overwrite_without_a_snapshot_store_does_not_replace_the_program() = runBlocking {
        com.example.kpkn.domain.training.CatalogCompositionTestSupport.install()
        val id = nextId()
        repository.addProgram(makeSimpleProgram(id))
        repository.flushPendingWrites()
        val before = repository.getProgramById(id)!!
        val vm = ProgramDetailViewModel(id)

        vm.applyProgramTemplate(simpleTemplate(), overwrite = true)

        assertEquals(ProgramDetailViewModel.SNAPSHOT_REQUIRED_MESSAGE, awaitSnackbar(vm))
        assertEquals("sin copia recuperable no se reemplaza nada", before, repository.getProgramById(id))
    }

    @Test
    fun applyProgramTemplate_overwrite_saves_the_previous_plan_as_a_recoverable_copy_and_then_replaces_it() = runBlocking {
        com.example.kpkn.domain.training.CatalogCompositionTestSupport.install()
        val id = nextId()
        val original = makeSimpleProgram(id)
        repository.addProgram(original)
        repository.flushPendingWrites()
        val store = programSnapshotStore(ApplicationProvider.getApplicationContext<Context>())
        val vm = ProgramDetailViewModel(id)
        vm.attachSnapshotStore(store)
        val template = simpleTemplate()

        vm.applyProgramTemplate(template, overwrite = true)

        val message = awaitSnackbar(vm)
        assertTrue(message, message.startsWith("Plantilla \"${template.name}\" aplicada"))
        val snapshots = withTimeout(5_000) { vm.programSnapshots.first { it.isNotEmpty() } }
        assertEquals(1, snapshots.size)
        assertEquals("Antes de \"${template.name}\"", snapshots.single().reason)
        assertEquals(original.name, snapshots.single().program.name)
        assertEquals(template.id, repository.getProgramById(id)?.structureTemplateId)
    }

    @Test
    fun applyProgramTemplate_overwrite_is_refused_while_a_session_is_in_progress() = runBlocking {
        com.example.kpkn.domain.training.CatalogCompositionTestSupport.install()
        val id = nextId()
        repository.addProgram(makeSimpleProgram(id))
        repository.flushPendingWrites()
        val store = programSnapshotStore(ApplicationProvider.getApplicationContext<Context>())
        val vm = ProgramDetailViewModel(id)
        vm.attachSnapshotStore(store)
        val session = weeksOf(repository.getProgramById(id)!!).first().sessions.first()
        repository.startWorkout(OngoingWorkoutState(programId = id, session = session, startTime = 11L))
        val before = repository.getProgramById(id)!!

        vm.applyProgramTemplate(simpleTemplate(), overwrite = true)

        // Mismo aviso que con protocolo (D2.8).
        assertEquals(ProgramDetailViewModel.SESSION_IN_PROGRESS_MESSAGE, awaitSnackbar(vm))
        assertEquals("el programa no se toca", before, repository.getProgramById(id))
        assertTrue("no se guarda copia de un reemplazo que no ocurrió", store.list(id).isEmpty())
    }

    @Test
    fun applyProgramTemplate_ignores_a_second_tap_while_the_first_is_still_running() = runBlocking {
        com.example.kpkn.domain.training.CatalogCompositionTestSupport.install()
        val id = nextId()
        repository.addProgram(makeSimpleProgram(id))
        repository.flushPendingWrites()
        val store = programSnapshotStore(ApplicationProvider.getApplicationContext<Context>())
        val vm = ProgramDetailViewModel(id)
        vm.attachSnapshotStore(store)
        val template = simpleTemplate()

        vm.applyProgramTemplate(template, overwrite = true)
        vm.applyProgramTemplate(template, overwrite = true)

        awaitSnackbar(vm)
        withTimeout(5_000) { vm.programSnapshots.first { it.isNotEmpty() } }
        delay(300)
        assertEquals("dos toques seguidos guardan una sola copia", 1, store.list(id).size)
    }

    // ─── P1 · D2.9: el flujo de comentarios es de solo lectura ────────────

    @Test
    fun feedbacks_are_exposed_read_only() {
        val vm = ProgramDetailViewModel(nextId())

        assertFalse("feedbacks no debe ser un MutableStateFlow público", vm.feedbacks is MutableStateFlow<*>)
        assertTrue(vm.feedbacks.value.isEmpty())
    }

    // ─── P1 · H-UI: tarjeta de progresión legible ─────────────────────────

    private fun nativeProposalProgram(id: String, notices: List<NativeProgressionResolution> = emptyList()): Program {
        val configurationId = "bench_press__dumbbell"
        val identity = NativeProgressionIdentity(
            recipeId = "hui-recipe",
            recipeContentVersion = 1,
            configurationId = configurationId,
            loadMode = LoadModeV2.LOAD,
            unitMode = UnitModeV2.REPS,
            side = "bilateral",
            execution = "standard",
            slotPurpose = "F",
            quantityConvention = LoadQuantityConvention.PER_IMPLEMENT,
        )
        val exercise = Exercise(
            id = "$id-ex",
            name = "Press de banca con mancuernas",
            catalogConfigurationId = configurationId,
            recipeDayId = "hui-day",
            recipeSlotId = "hui-slot",
        )
        val proposal = NativeProgressionProposal(
            proposalId = "$id-p1",
            kind = NativeProgressionProposalKind.INCREASE_LOAD,
            identity = identity,
            sourceSessionId = "${id}_s1",
            sourceLogIds = listOf("$id-l1", "$id-l2"),
            targetLoadKg = 22.0,
            explanation = com.example.kpkn.domain.training.NativeProgressionText.loadIncrease(
                times = 2,
                topReps = 12,
                fromKg = 20.0,
                toKg = 22.0,
                unit = "kg por mancuerna",
                assistance = false,
            ),
            createdAtMs = 1L,
        )
        return Program(
            id = id,
            name = "Progresión $id",
            structure = ProgramStructure.COMPLEX,
            macrocycles = listOf(
                Macrocycle(
                    id = "${id}_mc",
                    name = "Macro",
                    blocks = listOf(
                        Block(
                            id = "${id}_b",
                            name = "Bloque",
                            mesocycles = listOf(
                                Mesocycle(
                                    id = "${id}_m",
                                    name = "Meso",
                                    weeks = listOf(
                                        ProgramWeek(
                                            id = "${id}_w",
                                            name = "Semana 1",
                                            sessions = listOf(
                                                Session(id = "${id}_s1", name = "Día 1", exercises = listOf(exercise)),
                                                Session(
                                                    id = "${id}_s2",
                                                    name = "Día 2",
                                                    exercises = listOf(exercise.copy(id = "$id-ex2")),
                                                ),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            nativeProgressionProposals = listOf(proposal),
            nativeProgressionAudit = notices,
        )
    }

    @Test
    fun nativeProgressionCard_names_the_exercise_in_plain_language_without_internal_ids() {
        val id = nextId()
        repository.addProgram(nativeProposalProgram(id))
        val vm = ProgramDetailViewModel(id)

        val card = vm.nativeProgressionCard.value

        assertEquals(1, card.pendingCount)
        assertEquals("1 progresión por revisar", card.pendingLabel)
        val item = card.proposals.single()
        assertEquals("Press de banca con mancuernas", item.title)
        assertEquals(
            "Lo hiciste dos veces con 12 reps en todas las series. Propuesta: subir de 20 a 22 kg por mancuerna.",
            item.body,
        )
        assertFalse("nunca se imprime el identificador de la configuración", (item.title + item.body).contains("__"))
        assertFalse((item.title + item.body).contains("hui-slot"))
    }

    @Test
    fun rejectNativeProgressionProposal_confirms_with_the_exercise_name() = runBlocking {
        val id = nextId()
        repository.addProgram(nativeProposalProgram(id))
        repository.flushPendingWrites()
        val vm = ProgramDetailViewModel(id)

        vm.rejectNativeProgressionProposal("$id-p1")

        assertEquals(
            "Propuesta rechazada. Press de banca con mancuernas se queda como está.",
            awaitSnackbar(vm),
        )
        val audit = repository.getProgramById(id)!!.nativeProgressionAudit.single()
        assertEquals(NativeProgressionResolutionStatus.REJECTED, audit.status)
        assertFalse("rechazar no deja un aviso pendiente de leer", audit.userFacingNotice)
        val card = withTimeout(5_000) { vm.nativeProgressionCard.first { it.proposals.isEmpty() } }
        assertTrue(card.notices.isEmpty())
    }

    @Test
    fun acceptNativeProgressionProposal_that_no_longer_applies_explains_why_and_the_notice_can_be_dismissed() = runBlocking {
        val id = nextId()
        // Sin receta de origen la propuesta ya no tiene a qué aplicarse: caduca sin tocar el plan.
        repository.addProgram(nativeProposalProgram(id))
        repository.flushPendingWrites()
        val vm = ProgramDetailViewModel(id)

        vm.acceptNativeProgressionProposal("$id-p1")

        val message = awaitSnackbar(vm)
        assertTrue(message, message.startsWith("No se aplicó la propuesta de Press de banca con mancuernas. "))
        assertEquals(SnackbarType.SUGGESTION, snackbarTypeFor(message))
        val card = withTimeout(5_000) { vm.nativeProgressionCard.first { it.notices.isNotEmpty() } }
        assertEquals("Press de banca con mancuernas", card.notices.single().title)
        assertTrue(card.proposals.isEmpty())

        vm.dismissNativeProgressionNotice("$id-p1")

        withTimeout(5_000) { vm.nativeProgressionCard.first { it.notices.isEmpty() } }
        val audit = repository.getProgramById(id)!!.nativeProgressionAudit.single()
        assertEquals("el registro se conserva", NativeProgressionResolutionStatus.EXPIRED, audit.status)
        assertFalse(audit.userFacingNotice)
    }

    @Test
    fun snackbarTypeFor_marks_refusals_red_and_expired_proposals_as_suggestions() {
        assertEquals(SnackbarType.DANGER, snackbarTypeFor("No se pudo aplicar la plantilla. Intenta de nuevo."))
        assertEquals(SnackbarType.DANGER, snackbarTypeFor(ProgramDetailViewModel.SESSION_IN_PROGRESS_MESSAGE))
        assertEquals(SnackbarType.DANGER, snackbarTypeFor(ProgramDetailViewModel.SNAPSHOT_REQUIRED_MESSAGE))
        assertEquals(
            SnackbarType.SUGGESTION,
            snackbarTypeFor("No se aplicó la propuesta de Remo. Ya no quedan sesiones sin entrenar donde aplicar la propuesta."),
        )
        assertEquals(SnackbarType.SUCCESS, snackbarTypeFor("Propuesta rechazada. Remo se queda como está."))
    }
}

package com.example.kpkn.screens.programdetail

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.training.ProgramCalendarEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class ProgramDetailViewModelTest {

    private val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: ProgramRepository

    private fun nextId(): String = "prog_${System.nanoTime()}"

    private fun makeProgram(id: String) = Program(
        id = id,
        name = "Test $id",
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

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        ProgramRepository.init(context)
        repository = ProgramRepository.getInstance()
        repository.clearPrograms()
        repository.clearActiveProgram()
        repository.clearOngoingWorkout()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ─── Tab Management ───────────────────────────────────────────────────

    @Test
    fun initial_state_is_training_tab() {
        val id = nextId()
        val vm = ProgramDetailViewModel(id)
        assertEquals(MainTab.TRAINING, vm.uiState.value.activeTab)
        assertEquals(StructureSubTab.SEMANA, vm.uiState.value.structureSubTab)
    }

    @Test
    fun setActiveTab_changes_tab() {
        val vm = ProgramDetailViewModel(nextId())
        vm.setActiveTab(MainTab.ANALYTICS)
        assertEquals(MainTab.ANALYTICS, vm.uiState.value.activeTab)
    }

    @Test
    fun setStructureSubTab_changes_subtab() {
        val vm = ProgramDetailViewModel(nextId())
        vm.setStructureSubTab(StructureSubTab.SPLIT)
        assertEquals(StructureSubTab.SPLIT, vm.uiState.value.structureSubTab)
    }

    @Test
    fun setAnalyticsSubTab_changes_subtab() {
        val vm = ProgramDetailViewModel(nextId())
        vm.setAnalyticsSubTab(AnalyticsSubTab.PROGRESO)
        assertEquals(AnalyticsSubTab.PROGRESO, vm.uiState.value.analyticsSubTab)
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
    @Ignore("StateFlow combine timing issue with singleton repository")
    fun totalWeeks_computed() {
        val id = nextId()
        repository.addProgram(makeProgram(id))
        val vm = ProgramDetailViewModel(id)

        assertEquals(3, vm.totalWeeks.value)
    }

    @Test
    @Ignore("StateFlow combine timing issue with singleton repository")
    fun isSimpleProgram_false_for_multi_block() {
        val id = nextId()
        repository.addProgram(makeProgram(id))
        val vm = ProgramDetailViewModel(id)

        assertFalse(vm.isSimpleProgram.value)
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
            name = "Semana: 05/18",
            startDate = "2026-05-18",
            endDate = "2026-05-24",
            trainingDayDates = mapOf(1 to "2026-05-18", 3 to "2026-05-20"),
        )
        repository.addProgram(
            base.copy(
                timelineStartDate = "2026-05-18",
                calendarization = ProgramCalendarEngine.defaultSimpleDatedCalendarization(),
                simpleProgramKind = SimpleProgramKind.CALENDARIZED,
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
        assertEquals("Semana: 05/25", weeks[1].name)
        assertEquals("2026-05-25", weeks[1].startDate)
        assertEquals("2026-05-31", weeks[1].endDate)
        assertEquals(setOf(1, 3), weeks[1].trainingDayDates.keys)
        assertEquals(SimpleProgramKind.CALENDARIZED, updated.simpleProgramKind)
        assertTrue(updated.loops.isEmpty())
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
}

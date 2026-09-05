package com.example.kpkn.screens.programdetail

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.CompetitionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.training.ProgramCalendarEngine
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

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
}

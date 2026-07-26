package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramCalendarization
import com.example.kpkn.data.models.ProgramCalendarizationMode
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SimpleProgramKind
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.data.splits.SplitTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitApplicationEngineTest {

    private val upperLower = SplitTemplate(
        id = "test_ul",
        name = "Upper Lower",
        pattern = listOf("Torso", "Pierna", "Descanso", "Torso", "Pierna", "Descanso", "Descanso"),
        description = "Test",
    )

    private fun programWithWeeks(weeks: List<ProgramWeek>) = Program(
        id = "program",
        name = "Program",
        startDay = 1,
        macrocycles = listOf(
            Macrocycle(
                id = "macro",
                name = "Macro",
                blocks = listOf(
                    Block(
                        id = "block",
                        name = "Block",
                        mesocycles = listOf(Mesocycle(id = "meso", name = "Meso", weeks = weeks)),
                    )
                ),
            )
        ),
    )

    @Test
    fun migrate_without_muscle_data_distributes_sessions_in_split_order() {
        val sessions = listOf(
            Session(id = "s1", name = "A"),
            Session(id = "s2", name = "B"),
            Session(id = "s3", name = "C"),
        )

        val migrated = SplitApplicationEngine.buildSessionsForSplit(
            pattern = upperLower.pattern,
            startDay = 1,
            existingSessions = sessions,
            migrationMode = SessionMigrationMode.MIGRATE,
        )

        assertEquals(listOf(1, 2, 4), migrated.take(3).map { it.dayOfWeek })
        assertTrue(migrated.any { it.dayOfWeek == 5 })
    }

    @Test
    fun clean_mode_reports_destructive_impact_before_replacing_sessions() {
        val program = programWithWeeks(
            listOf(
                ProgramWeek("w1", "W1", sessions = listOf(Session("s1", "A"), Session("s2", "B"))),
                ProgramWeek("w2", "W2", sessions = listOf(Session("s3", "C"))),
            )
        )

        val impact = SplitApplicationEngine.impactSummary(
            SplitApplicationRequest(
                program = program,
                selectedSplit = upperLower,
                selectedBlockId = "block",
                selectedWeekId = "w1",
                startDay = 1,
                temporalScope = SplitTemporalScope.CURRENT_BLOCK,
                migrationMode = SessionMigrationMode.CLEAN,
            )
        )

        assertEquals(2, impact.affectedWeeks)
        assertEquals(3, impact.affectedSessions)
        assertTrue(impact.willReplaceSessions)
    }

    @Test
    fun copied_sessions_receive_new_ids_and_keep_day_assignment() {
        val copied = SplitApplicationEngine.copySessionsWithNewIds(
            listOf(Session(id = "s1", name = "Pierna", dayOfWeek = 1, assignedDays = listOf(1)))
        )

        assertEquals(1, copied.size)
        assertNotEquals("s1", copied.first().id)
        assertEquals(1, copied.first().dayOfWeek)
        assertEquals(listOf(1), copied.first().assignedDays)
    }

    @Test
    fun start_day_change_can_shift_sessions_only_from_selected_week() {
        val program = programWithWeeks(
            listOf(
                ProgramWeek("w1", "W1", sessions = listOf(Session("s1", "A", dayOfWeek = 1))),
                ProgramWeek("w2", "W2", sessions = listOf(Session("s2", "B", dayOfWeek = 1))),
            )
        )

        val updated = SplitApplicationEngine.applyStartDayChange(
            program = program,
            selectedWeekId = "w2",
            newStartDay = 6,
            temporalScope = StartDayTemporalScope.FROM_SELECTED_WEEK,
            sessionMode = StartDaySessionMode.KEEP_SPLIT_ORDER,
        )

        val weeks = updated.macrocycles[0].blocks[0].mesocycles[0].weeks
        assertEquals(6, updated.startDay)
        assertEquals(1, weeks[0].sessions.first().dayOfWeek)
        assertEquals(6, weeks[1].sessions.first().dayOfWeek)
    }

    @Test
    fun start_day_change_can_keep_session_days() {
        val program = programWithWeeks(listOf(ProgramWeek("w1", "W1", sessions = listOf(Session("s1", "A", dayOfWeek = 1)))))

        val updated = SplitApplicationEngine.applyStartDayChange(
            program = program,
            selectedWeekId = "w1",
            newStartDay = 6,
            temporalScope = StartDayTemporalScope.ALL_WEEKS,
            sessionMode = StartDaySessionMode.KEEP_DAYS,
        )

        assertEquals(6, updated.startDay)
        assertEquals(1, updated.macrocycles[0].blocks[0].mesocycles[0].weeks[0].sessions.first().dayOfWeek)
    }

    @Test
    fun prebuilt_mode_uses_matching_templates_and_clones_repeated_days() {
        val split = SPLIT_TEMPLATES.first { it.id == "ul_x4" }

        val sessions = SplitApplicationEngine.buildSessionsForSplit(
            splitId = split.id,
            pattern = split.pattern,
            startDay = 1,
            existingSessions = emptyList(),
            migrationMode = SessionMigrationMode.PREBUILT,
        )

        assertEquals(4, sessions.size)
        assertTrue(sessions.all { session ->
            session.exercises.isNotEmpty() || session.parts.any { it.exercises.isNotEmpty() }
        })
        val torsoSessions = sessions.filter { it.scheduleLabel == "Torso" }
        val firstIds = torsoSessions[0].exercises.map { it.id } + torsoSessions[0].parts.flatMap { part -> part.exercises.map { it.id } }
        val secondIds = torsoSessions[1].exercises.map { it.id } + torsoSessions[1].parts.flatMap { part -> part.exercises.map { it.id } }
        assertTrue(firstIds.intersect(secondIds.toSet()).isEmpty())
    }

    @Test
    fun current_week_records_override_without_destroying_global_or_block_selection() {
        val base = programWithWeeks(listOf(ProgramWeek("w1", "W1"), ProgramWeek("w2", "W2"))).copy(
            selectedSplitId = "global_split",
            blockSplitSelections = mapOf("block" to "block_split"),
        )

        val updated = SplitApplicationEngine.apply(
            SplitApplicationRequest(
                program = base,
                selectedSplit = upperLower,
                selectedBlockId = "block",
                selectedWeekId = "w1",
                startDay = 1,
                temporalScope = SplitTemporalScope.CURRENT_WEEK,
                migrationMode = SessionMigrationMode.CLEAN,
            )
        )

        assertEquals("global_split", updated.selectedSplitId)
        assertEquals("block_split", updated.blockSplitSelections["block"])
        assertEquals(upperLower.id, updated.weekSplitSelections["w1"])
        assertFalse(updated.weekSplitSelections.containsKey("w2"))
    }

    @Test
    fun whole_program_replaces_global_selection_and_clears_narrower_overrides() {
        val base = programWithWeeks(listOf(ProgramWeek("w1", "W1"), ProgramWeek("w2", "W2"))).copy(
            selectedSplitId = "old",
            blockSplitSelections = mapOf("block" to "old_block"),
            weekSplitSelections = mapOf("w1" to "old_week"),
        )

        val updated = SplitApplicationEngine.apply(
            SplitApplicationRequest(
                program = base,
                selectedSplit = upperLower,
                selectedBlockId = "block",
                selectedWeekId = "w1",
                startDay = 1,
                temporalScope = SplitTemporalScope.WHOLE_PROGRAM,
                migrationMode = SessionMigrationMode.CLEAN,
            )
        )

        assertEquals(upperLower.id, updated.selectedSplitId)
        assertTrue(updated.blockSplitSelections.isEmpty())
        assertTrue(updated.weekSplitSelections.isEmpty())
    }

    @Test
    fun per_block_mode_applies_every_week_in_each_configured_block() {
        val ul = SPLIT_TEMPLATES.first { it.id == "ul_x4" }
        val fullBody = SPLIT_TEMPLATES.first { it.id == "fullbody_x3" }
        val program = Program(
            id = "advanced",
            name = "Advanced",
            structure = ProgramStructure.COMPLEX,
            macrocycles = listOf(
                Macrocycle(
                    id = "macro",
                    name = "Macro",
                    blocks = listOf(
                        Block("b1", "B1", mesocycles = listOf(Mesocycle("m1", "M1", weeks = listOf(ProgramWeek("w1", "W1"), ProgramWeek("w2", "W2"))))),
                        Block("b2", "B2", mesocycles = listOf(Mesocycle("m2", "M2", weeks = listOf(ProgramWeek("w3", "W3"))))),
                    ),
                )
            ),
        )

        val updated = SplitApplicationEngine.apply(
            SplitApplicationRequest(
                program = program,
                selectedSplit = ul,
                selectedBlockId = "b1",
                selectedWeekId = "w1",
                startDay = 1,
                temporalScope = SplitTemporalScope.CURRENT_WEEK,
                advancedMode = AdvancedSplitMode.PER_BLOCK,
                migrationMode = SessionMigrationMode.CLEAN,
                perBlockSelections = mapOf("b1" to ul.id, "b2" to fullBody.id),
            )
        )

        val weeks = SplitApplicationEngine.buildWeekOptions(updated).associateBy { it.id }
        assertEquals(4, weeks.getValue("w1").sessions.size)
        assertEquals(4, weeks.getValue("w2").sessions.size)
        assertEquals(3, weeks.getValue("w3").sessions.size)
        assertEquals(ul.id, updated.blockSplitSelections["b1"])
        assertEquals(fullBody.id, updated.blockSplitSelections["b2"])
    }

    @Test
    fun calendarized_program_keeps_its_existing_week_start_when_applying_split() {
        val calendarized = programWithWeeks(listOf(ProgramWeek("w1", "W1"))).copy(
            startDay = 3,
            timelineStartDate = "2026-07-01",
            calendarization = ProgramCalendarization(ProgramCalendarizationMode.SIMPLE_DATED),
            simpleProgramKind = SimpleProgramKind.CALENDARIZED,
        )

        val updated = SplitApplicationEngine.apply(
            SplitApplicationRequest(
                program = calendarized,
                selectedSplit = upperLower,
                selectedBlockId = "block",
                selectedWeekId = "w1",
                startDay = 6,
                temporalScope = SplitTemporalScope.CURRENT_WEEK,
                migrationMode = SessionMigrationMode.CLEAN,
            )
        )

        assertEquals(3, updated.startDay)
        val days = SplitApplicationEngine.buildWeekOptions(updated).first().sessions.mapNotNull { it.dayOfWeek }
        assertEquals(listOf(3, 4, 6, 7), days)
    }
}

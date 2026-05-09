package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
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
}

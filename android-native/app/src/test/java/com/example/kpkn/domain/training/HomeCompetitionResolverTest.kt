package com.example.kpkn.domain.training

import com.example.kpkn.data.models.CompetitionRecord
import com.example.kpkn.data.models.CompetitionRecordStatus
import com.example.kpkn.data.models.KeyDateType
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramKeyDate
import com.example.kpkn.data.models.ProgramStructure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class HomeCompetitionResolverTest {

    private val today = LocalDate.of(2026, 9, 5)

    private fun program(
        eventDate: String,
        keyDateId: String = "kd-1",
        structure: ProgramStructure = ProgramStructure.COMPLEX,
    ) = Program(
        id = "p1",
        name = "Plan avanzado",
        structure = structure,
        keyDates = listOf(
            ProgramKeyDate(
                id = keyDateId,
                title = "Open nacional",
                type = KeyDateType.COMPETITION,
                startDate = eventDate,
                eventDate = eventDate,
            ),
        ),
    )

    private fun planned(eventDate: String, id: String = "rec-1") = CompetitionRecord(
        id = id,
        title = "Open nacional",
        eventDate = eventDate,
        status = CompetitionRecordStatus.PLANNED,
        plannedProgramId = "p1",
        keyDateId = "kd-1",
    )

    @Test
    fun upcoming_when_days_remain() {
        val state = HomeCompetitionResolver.resolve(
            program = program("2026-09-12"),
            records = listOf(planned("2026-09-12")),
            today = today,
        )
        requireNotNull(state)
        assertEquals("rec-1", state.recordId)
        assertEquals(7L, state.daysUntil)
        assertEquals(CompetitionHomePhase.UPCOMING, state.phase)
        assertEquals("7 días", state.countdownLabel)
    }

    @Test
    fun register_on_event_day() {
        val state = HomeCompetitionResolver.resolve(
            program = program("2026-09-05"),
            records = listOf(planned("2026-09-05")),
            today = today,
        )
        requireNotNull(state)
        assertEquals(CompetitionHomePhase.REGISTER, state.phase)
        assertEquals(0L, state.daysUntil)
        assertEquals("Hoy", state.countdownLabel)
    }

    @Test
    fun register_when_date_passed_and_still_planned() {
        val state = HomeCompetitionResolver.resolve(
            program = program("2026-09-01"),
            records = listOf(planned("2026-09-01")),
            today = today,
        )
        requireNotNull(state)
        assertEquals(CompetitionHomePhase.REGISTER, state.phase)
        assertEquals(-4L, state.daysUntil)
        assertEquals("Hace 4 días", state.countdownLabel)
    }

    @Test
    fun hidden_when_record_is_completed() {
        val completed = planned("2026-09-05").copy(status = CompetitionRecordStatus.COMPLETED)
        val state = HomeCompetitionResolver.resolve(
            program = program("2026-09-05"),
            records = listOf(completed),
            today = today,
        )
        assertNull(state)
    }

    @Test
    fun hidden_for_simple_temporal_program() {
        val state = HomeCompetitionResolver.resolve(
            program = program("2026-09-12", structure = ProgramStructure.SIMPLE),
            records = listOf(planned("2026-09-12")),
            today = today,
        )
        assertNull(state)
    }
}

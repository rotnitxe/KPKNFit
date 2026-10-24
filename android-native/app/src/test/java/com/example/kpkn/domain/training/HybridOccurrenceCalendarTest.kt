package com.example.kpkn.domain.training

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.multisport.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class HybridOccurrenceCalendarTest {
    @Test fun floatingOccurrenceHasDatesAndDistinctTimesWithoutCalendarizingTheTemplate() {
        val source = HybridCalendarFixtures.active()
        val applied = HybridCalendarFixtures.applyActive(source)
        val projection = ProgramCalendarEngine.project(applied)
        assertFalse(ProgramCalendarEngine.isCalendarized(applied))
        assertTrue(projection.enabled)
        assertFalse(ProgramCalendarEngine.project(applied, includeOccurrenceOverlays = false).enabled)
        val sessions = HybridCalendarFixtures.week(applied).sessions
        assertEquals(LocalDate.parse("2026-10-19"), projection.scheduledDateFor(sessions[0], "week"))
        assertEquals(LocalDate.parse("2026-10-19"), projection.scheduledDateFor(sessions[1], "inst_c3_week"))
        assertEquals(480, projection.scheduledPlacementFor(sessions[0], "week")!!.startMinute)
        assertEquals(720, projection.scheduledPlacementFor(sessions[1], "week")!!.startMinute)
        assertNull(projection.scheduledDateFor(sessions[0], "inst_c4_week"))
        assertEquals(applied, ProgramCalendarEngine.materializeWeekDates(applied))
        assertEquals(source.macrocycles, ProgramPersistNormalizer.normalize(applied).macrocycles)
        assertEquals(setOf(1), ProgramCalendarEngine.resolveTrainingDays(applied, HybridCalendarFixtures.week(applied)))
        assertEquals(setOf(2, 4), ProgramCalendarEngine.resolveTrainingDays(applied, HybridCalendarFixtures.week(applied), false))
    }

    @Test fun datedOccurrenceChangesRuntimeProjectionAndNeverMaterializesIntoTheTemplate() {
        val source = HybridCalendarFixtures.active(HybridCalendarFixtures.prepared(HybridCalendarFixtures.program(dated = true)))
        val applied = HybridCalendarFixtures.applyActive(source)
        val session = HybridCalendarFixtures.week(applied).sessions.first()
        assertEquals(LocalDate.parse("2026-10-19"), ProgramCalendarEngine.project(applied).scheduledDateFor(session, "week"))
        assertEquals(LocalDate.parse("2026-10-20"),
            ProgramCalendarEngine.project(applied, false).scheduledDateFor(session, "week"))
        assertEquals(applied, ProgramCalendarEngine.materializeWeekDates(applied))
        assertEquals(source.macrocycles, ProgramPersistNormalizer.normalize(applied).macrocycles)
        assertEquals(HybridCalendarFixtures.week(source, "other-week"), HybridCalendarFixtures.week(applied, "other-week"))
    }

    @Test fun changingRunOrCycleCannotReusePriorFloatingDates() {
        val applied = HybridCalendarFixtures.applyActive()
        val nextCycle = applied.copy(runState = applied.runState!!.copy(cycleNumber = 4, weekInstanceId = "inst_c4_week"))
        assertFalse(ProgramCalendarEngine.project(nextCycle).enabled)
        val nextRun = applied.copy(runState = applied.runState!!.copy(runId = "new-run"))
        assertFalse(ProgramCalendarEngine.project(nextRun).enabled)
    }

    @Test fun optionalOmissionHasNoGhostDateAndAuthoredSessionRemainsStored() {
        val raw = HybridCalendarFixtures.replaceWeek(HybridCalendarFixtures.program(dated = true)) { week ->
            week.copy(sessions = week.sessions + Session("optional", "Opcional", requirement = SessionRequirement.OPTIONAL,
                targetDurationMinutes = 30, dayOfWeek = 3, assignedDays = listOf(3),
                exercises = listOf(Exercise("optional-ex", "Accessory", sets = listOf(ExerciseSet("optional-set", targetReps = 8)))),
            ))
        }
        val normalized = ProgramCalendarEngine.materializeWeekDates(raw)
        val proposal = (HybridProgramPlanner.prepare(normalized, "week", listOf(
            HybridSessionGoalBinding("strength-session", "strength"),
            HybridSessionGoalBinding("run-session", "running"),
            HybridSessionGoalBinding("optional", "strength", allowedWeekdays = setOf(2)),
        )) as HybridProgramPlanResult.Ready).proposal
        val stored = HybridCalendarFixtures.replaceWeek(normalized) { it.copy(hybridProposal = proposal) }
        val applied = (HybridCalendarApplicator.apply(HybridCalendarSourceSnapshot(stored),
            HybridCalendarFixtures.request(stored), LocalDate.parse("2026-10-18"), 123456L)
            as HybridCalendarApplicationResult.Applied).program
        val optional = HybridCalendarFixtures.week(applied).sessions.last()
        assertEquals("optional", optional.id)
        assertEquals(3, optional.dayOfWeek)
        val projection = ProgramCalendarEngine.project(applied)
        assertNull(projection.scheduledDateFor(optional, "week"))
        assertNull(projection.scheduledPlacementFor(optional, "week"))
        assertEquals(setOf(1), projection.weeks.first().trainingDayDates.keys)
        assertEquals(applied, ProgramCalendarEngine.materializeWeekDates(applied))
    }
}

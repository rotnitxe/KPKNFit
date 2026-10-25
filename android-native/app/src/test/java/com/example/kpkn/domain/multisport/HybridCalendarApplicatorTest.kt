package com.example.kpkn.domain.multisport

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.training.ProgramCalendarEngine
import com.example.kpkn.domain.training.ProgramPersistNormalizer
import java.time.LocalDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class HybridCalendarApplicatorTest {
    private val today = LocalDate.parse("2026-10-18")

    @Test fun acceptanceMovesEachIdentityAndKeepsPrescriptionVariantsAndOtherWeek() {
        val source = HybridCalendarFixtures.prepared()
        val before = HybridCalendarFixtures.week(source)
        val applied = apply(source) as HybridCalendarApplicationResult.Applied
        val after = HybridCalendarFixtures.week(applied.program)
        assertEquals(listOf(1, 1), after.sessions.map { it.dayOfWeek })
        assertEquals(listOf(listOf(1), listOf(1)), after.sessions.map { it.assignedDays })
        before.sessions.zip(after.sessions).forEach { (old, moved) ->
            assertEquals(old, moved.copy(dayOfWeek = old.dayOfWeek, assignedDays = old.assignedDays))
        }
        assertEquals(HybridCalendarFixtures.week(source, "other-week"), HybridCalendarFixtures.week(applied.program, "other-week"))
        assertEquals(source.schedulePlan, applied.program.schedulePlan)
        assertEquals(source.runState, applied.program.runState)
        assertNull(after.hybridProposal)
        assertEquals(before.hybridProposal, after.hybridAppliedSchedule!!.proposalSnapshot)
        assertEquals(setOf("strength-session", "run-session"), applied.schedule.assignments.map { it.sessionId }.toSet())
        assertEquals(setOf(480, 720), applied.schedule.assignments.map { it.startMinute }.toSet())
        assertEquals(123456L, applied.schedule.acceptedAtMs)
        val json = Json { encodeDefaults = true }
        assertEquals(applied.program, json.decodeFromString<Program>(json.encodeToString(applied.program)))
    }

    @Test fun datedAcceptanceMaterializesOnlyTargetWeekAndSurvivesNativeNormalizer() {
        val source = HybridCalendarFixtures.prepared(HybridCalendarFixtures.program(dated = true))
        val result = apply(source) as HybridCalendarApplicationResult.Applied
        assertEquals(mapOf(1 to "2026-10-19"), HybridCalendarFixtures.week(result.program).trainingDayDates)
        assertEquals("2026-10-19", ProgramCalendarEngine.project(result.program)
            .scheduledDateFor(HybridCalendarFixtures.week(result.program).sessions.first(), "week").toString())
        assertEquals(HybridCalendarFixtures.week(source, "other-week"), HybridCalendarFixtures.week(result.program, "other-week"))
        assertEquals(result.program, ProgramPersistNormalizer.normalize(result.program))
    }

    @Test fun recomputesAssignmentsEvenWhenFreshnessCheckPasses() {
        val original = HybridCalendarFixtures.prepared()
        val proposal = HybridCalendarFixtures.week(original).hybridProposal!!
        val mutations = listOf(
            proposal.copy(assignments = proposal.assignments.drop(1)),
            proposal.copy(assignments = proposal.assignments + proposal.assignments.first()),
            proposal.copy(assignments = proposal.assignments.map { it.copy(startMinute = 0) }),
            proposal.copy(assignments = proposal.assignments.map { it.copy(sessionId = "alien") }),
            proposal.copy(assignments = proposal.assignments.map { it.copy(goalId = "alien") }),
            proposal.copy(omittedOptionalSessions = listOf(OptionalSessionOmission("strength-session", "strength", emptyList()))),
        )
        mutations.forEach { forged ->
            val stored = HybridCalendarFixtures.replaceWeek(original) { it.copy(hybridProposal = forged) }
            assertTrue(HybridProgramPlanner.isCurrent(stored, "week", forged))
            assertEquals(HybridCalendarRejection.ProposalIntegrityMismatch, rejected(stored))
        }
    }

    @Test fun invalidBindingsAreRevalidatedAndNotAuthenticatedByFingerprint() {
        val source = HybridCalendarFixtures.prepared()
        val forged = HybridCalendarFixtures.week(source).hybridProposal!!.copy(bindings = emptyList())
        val stored = HybridCalendarFixtures.replaceWeek(source) { it.copy(hybridProposal = forged) }
        assertTrue(HybridProgramPlanner.isCurrent(stored, "week", forged))
        assertTrue(rejected(stored) is HybridCalendarRejection.CannotRecompute)
    }

    @Test fun staleWrongProgramWrongWeekAndUnstoredProposalNeverApply() {
        val source = HybridCalendarFixtures.prepared()
        val request = HybridCalendarFixtures.request(source)
        assertEquals(HybridCalendarRejection.WrongProgram, reason(source, request.copy(programId = "alien")))
        assertEquals(HybridCalendarRejection.WeekNotFound, reason(source, request.copy(weekId = "missing")))
        val changed = HybridCalendarFixtures.replaceWeek(source) { it.copy(name = "Changed after planning") }
        assertEquals(HybridCalendarRejection.StaleProposal, reason(changed, request))
        val applied = apply(source) as HybridCalendarApplicationResult.Applied
        assertEquals(HybridCalendarRejection.ProposalNotStored, reason(applied.program, request))
    }

    @Test fun duplicateBaseAndAlternativeIdentitiesAreRejectedGlobally() {
        val original = HybridCalendarFixtures.prepared()
        val collision = HybridCalendarFixtures.replaceWeek(original) { week ->
            week.copy(sessions = week.sessions.map { session ->
                if (session.sessionB == null) session else session.copy(sessionB = session.sessionB.copy(id = "other-session"))
            })
        }
        assertEquals(HybridCalendarRejection.InvalidHierarchy, rejected(collision))
    }

    @Test fun activeScopeRequiresExactRunCycleInstanceAndOnlyAppendsOneOverlay() {
        val source = HybridCalendarFixtures.prepared().copy(runState = ProgramRunState(
            "run", cycleNumber = 3, weekId = "week", weekInstanceId = "inst_c3_week",
        ))
        val request = HybridCalendarFixtures.request(source).copy(
            scope = HybridCalendarApplicationScope.ACTIVE_WEEK_OCCURRENCE,
            expectedRunId = "run", expectedCycleNumber = 3, expectedWeekInstanceId = "inst_c3_week",
            occurrenceWeekStartDate = "2026-10-19",
        )
        val applied = HybridCalendarApplicator.apply(HybridCalendarSourceSnapshot(source), request, today, 123456L)
            as HybridCalendarApplicationResult.Applied
        assertEquals(source, applied.program.copy(hybridOccurrenceSchedules = emptyList()))
        assertEquals(1, applied.program.hybridOccurrenceSchedules.size)
        assertEquals(HybridCalendarRejection.AlreadyAppliedToOccurrence, reason(applied.program, request))
        assertEquals(HybridCalendarRejection.RunIdentityMismatch, reason(source, request.copy(expectedRunId = "other")))
        assertEquals(HybridCalendarRejection.RunIdentityMismatch, reason(source, request.copy(expectedCycleNumber = 2)))
        assertEquals(HybridCalendarRejection.RunIdentityMismatch, reason(source, request.copy(expectedWeekInstanceId = "week")))
        assertEquals(HybridCalendarRejection.ProgramAlreadyStarted, rejected(source))
    }

    @Test fun completedAndOngoingWorkoutsAndHistoricalProgramsAreProtected() {
        val source = HybridCalendarFixtures.prepared()
        val log = WorkoutLog("log", source.id, "strength-session", "Done", "2026-10-17", 40)
        val completed = apply(source, HybridCalendarSourceSnapshot(source, history = listOf(log))) as HybridCalendarApplicationResult.Rejected
        assertTrue(completed.reason is HybridCalendarRejection.CompletedSessions)
        assertEquals(HybridCalendarRejection.OngoingWorkout,
            (apply(source, HybridCalendarSourceSnapshot(source, hasOngoingWorkout = true)) as HybridCalendarApplicationResult.Rejected).reason)
        assertEquals(HybridCalendarRejection.ProgramAlreadyStarted,
            (apply(source, HybridCalendarSourceSnapshot(source, history = listOf(log.copy(sessionId = "other-session")))) as HybridCalendarApplicationResult.Rejected).reason)
        assertEquals(HybridCalendarRejection.ProgramAlreadyStarted,
            (apply(source, HybridCalendarSourceSnapshot(source, activeProgram = ActiveProgramState(source.id))) as HybridCalendarApplicationResult.Rejected).reason)
    }

    @Test fun pastDatesAndMultipleOccurrencesCannotBeSilentlyRescheduled() {
        val dated = HybridCalendarFixtures.prepared(HybridCalendarFixtures.program(dated = true))
        val past = HybridCalendarApplicator.apply(HybridCalendarSourceSnapshot(dated), HybridCalendarFixtures.request(dated),
            LocalDate.parse("2026-10-27"), 123456L) as HybridCalendarApplicationResult.Rejected
        assertTrue(past.reason is HybridCalendarRejection.PastScheduledDate)
        val multi = HybridCalendarFixtures.replaceWeek(HybridCalendarFixtures.program()) { week ->
            week.copy(sessions = week.sessions.map { it.copy(assignedDays = listOf(2, 4)) })
        }
        assertTrue(rejected(HybridCalendarFixtures.prepared(multi)) is HybridCalendarRejection.MultipleOccurrencesOfSession)
    }

    @Test fun genuineOptionalOmissionsAreAppliedWithoutDeletingAuthoredSessions() {
        val source = HybridCalendarFixtures.replaceWeek(HybridCalendarFixtures.program()) { week ->
            week.copy(sessions = week.sessions + Session(
                "optional", "Opcional intacta", targetDurationMinutes = 30, requirement = SessionRequirement.OPTIONAL,
                exercises = listOf(Exercise("optional-ex", "Accessory", sets = listOf(ExerciseSet("optional-set", targetReps = 8)))),
            ))
        }
        val proposal = (HybridProgramPlanner.prepare(source, "week", listOf(
            HybridSessionGoalBinding("strength-session", "strength"),
            HybridSessionGoalBinding("run-session", "running"),
            HybridSessionGoalBinding("optional", "strength", allowedWeekdays = setOf(2)),
        )) as HybridProgramPlanResult.Ready).proposal
        assertEquals(listOf("optional"), proposal.omittedOptionalSessionIds)
        val stored = HybridCalendarFixtures.replaceWeek(source) { it.copy(hybridProposal = proposal) }
        val applied = apply(stored) as HybridCalendarApplicationResult.Applied
        val authored = HybridCalendarFixtures.week(applied.program)
        assertEquals(3, authored.sessions.size)
        val runtime = HybridOccurrenceResolver.resolveWeek(applied.program, authored, null, null, null)
        assertEquals(setOf("strength-session", "run-session"), runtime.sessions.map { it.id }.toSet())
        assertEquals(HybridCalendarFixtures.week(source).sessions.last(), authored.sessions.last())
    }

    @Test fun invalidExplicitDatesCannotBeReinterpretedByTheCalendarProjection() {
        val wrongWeekday = HybridCalendarFixtures.replaceWeek(HybridCalendarFixtures.program(dated = true)) {
            it.copy(trainingDayDates = mapOf(2 to "2026-10-19"))
        }
        assertEquals(HybridCalendarRejection.InvalidCalendarDates, rejected(HybridCalendarFixtures.prepared(wrongWeekday)))
        val malformedLimit = HybridCalendarFixtures.program(dated = true).let {
            it.copy(schedulePlan = it.schedulePlan!!.copy(targetEndDate = "invalid"))
        }
        assertEquals(HybridCalendarRejection.InvalidCalendarDates, rejected(HybridCalendarFixtures.prepared(malformedLimit)))
    }

    private fun apply(program: Program, snapshot: HybridCalendarSourceSnapshot = HybridCalendarSourceSnapshot(program)) =
        HybridCalendarApplicator.apply(snapshot, HybridCalendarFixtures.request(program), today, 123456L)
    private fun rejected(program: Program) = (apply(program) as HybridCalendarApplicationResult.Rejected).reason
    private fun reason(program: Program, request: HybridCalendarAcceptanceRequest) =
        (HybridCalendarApplicator.apply(HybridCalendarSourceSnapshot(program), request, today, 123456L)
            as HybridCalendarApplicationResult.Rejected).reason
}

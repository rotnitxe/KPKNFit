package com.example.kpkn.domain.multisport

import com.example.kpkn.data.models.*
import java.time.LocalDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class HybridOccurrenceResolverTest {
    @Test fun onlyExactRunCycleAndInstanceReceiveOverlay() {
        val original = HybridCalendarFixtures.active()
        val applied = HybridCalendarFixtures.applyActive(original)
        val template = HybridCalendarFixtures.week(applied)
        val exact = HybridOccurrenceResolver.resolveWeek(applied, template, "run", 3, "inst_c3_week")
        assertEquals(listOf(1, 1), exact.sessions.map { it.dayOfWeek })
        assertEquals("2026-10-19", exact.startDate)
        assertEquals(template, HybridOccurrenceResolver.resolveWeek(applied, template, "run", 4, "inst_c4_week"))
        assertEquals(template, HybridOccurrenceResolver.resolveWeek(applied, template, "another-run", 3, "inst_c3_week"))
        assertEquals(template, HybridOccurrenceResolver.resolveWeek(applied, template, "run", 3, "week"))
        assertEquals(template, HybridOccurrenceResolver.resolveWeek(applied, template, null, null, null))
        assertEquals(original.macrocycles, applied.macrocycles)
        assertEquals(original.runState, applied.runState)
        assertEquals(original.schedulePlan, applied.schedulePlan)
    }

    @Test fun duplicateOrMalformedPersistedOverlaysFailClosed() {
        val applied = HybridCalendarFixtures.applyActive()
        val overlay = applied.hybridOccurrenceSchedules.single()
        val template = HybridCalendarFixtures.week(applied)
        val duplicate = applied.copy(hybridOccurrenceSchedules = listOf(overlay, overlay))
        assertNull(HybridOccurrenceResolver.scheduleFor(duplicate, template))
        val malformed = applied.copy(hybridOccurrenceSchedules = listOf(overlay.copy(weekStartDate = "invalid")))
        assertNull(HybridOccurrenceResolver.scheduleFor(malformed, template))
        val changedIds = HybridCalendarFixtures.replaceWeek(applied) { week ->
            week.copy(sessions = week.sessions.map { it.copy(id = "renamed-${it.id}") })
        }
        assertNull(HybridOccurrenceResolver.scheduleFor(changedIds, HybridCalendarFixtures.week(changedIds)))
    }

    @Test fun overlayRoundTripsAndDoesNotCreateWorkoutOrProgressRecords() {
        val applied = HybridCalendarFixtures.applyActive()
        val json = Json { encodeDefaults = true }
        val decoded = json.decodeFromString<Program>(json.encodeToString(applied))
        assertEquals(applied, decoded)
        assertEquals(emptySet<String>(), decoded.runState!!.completedSessionIds)
        assertEquals(3, decoded.runState!!.cycleNumber)
        assertEquals("inst_c3_week", decoded.runState!!.weekInstanceId)
    }

    @Test fun editedPrescriptionIntentOrAvailabilityInvalidatesAcceptedOccurrence() {
        val applied = HybridCalendarFixtures.applyActive()
        val contentChanged = HybridCalendarFixtures.replaceWeek(applied) { week ->
            week.copy(sessions = week.sessions.map { session ->
                session.copy(exercises = session.exercises.map { exercise -> exercise.copy(name = "Edited prescription") })
            })
        }
        assertNull(HybridOccurrenceResolver.scheduleFor(contentChanged, HybridCalendarFixtures.week(contentChanged)))
        val intentChanged = applied.copy(trainingIntent = TrainingIntent(goals = applied.trainingIntent!!.goals, primaryGoalId = "running"))
        assertNull(HybridOccurrenceResolver.scheduleFor(intentChanged, HybridCalendarFixtures.week(intentChanged)))
        val configurationChanged = applied.copy(hybridPlanning = applied.hybridPlanning!!.copy(
            fixedCommitments = listOf(FixedTrainingCommitment("new-class", 1, 480, 600)),
        ))
        assertNull(HybridOccurrenceResolver.scheduleFor(configurationChanged, HybridCalendarFixtures.week(configurationChanged)))
        val overlay = applied.hybridOccurrenceSchedules.single()
        val unbound = applied.copy(hybridOccurrenceSchedules = listOf(overlay.copy(
            schedule = overlay.schedule.copy(acceptedWeekFingerprint = ""),
        )))
        assertNull(HybridOccurrenceResolver.scheduleFor(unbound, HybridCalendarFixtures.week(unbound)))
    }

    @Test fun acceptedTemplateUsesPostPlacementFingerprintAndInvalidatesAfterEdit() {
        val source = HybridCalendarFixtures.prepared()
        val applied = (HybridCalendarApplicator.apply(HybridCalendarSourceSnapshot(source),
            HybridCalendarFixtures.request(source), LocalDate.parse("2026-10-18"), 123456L)
            as HybridCalendarApplicationResult.Applied).program
        val accepted = HybridCalendarFixtures.week(applied).hybridAppliedSchedule!!
        assertNotEquals(accepted.proposalSnapshot.sourceWeekFingerprint, accepted.acceptedWeekFingerprint)
        assertNotNull(HybridOccurrenceResolver.scheduleFor(applied, HybridCalendarFixtures.week(applied)))
        val edited = HybridCalendarFixtures.replaceWeek(applied) { week ->
            week.copy(sessions = week.sessions.map { it.copy(targetDurationMinutes = 180) })
        }
        assertNull(HybridOccurrenceResolver.scheduleFor(edited, HybridCalendarFixtures.week(edited)))
    }

    @Test fun importedMetadataCannotClaimSatisfiedConstraintsWithoutMeetingThem() {
        val applied = HybridCalendarFixtures.applyActive()
        val original = applied.hybridOccurrenceSchedules.single()
        val proposal = original.schedule.proposalSnapshot
        val impossibleConfiguration = proposal.configurationSnapshot.copy(separationRules = listOf(
            HybridSessionSeparationRule("impossible", "strength-session", "run-session", 2000),
        ))
        val forgedProposal = proposal.copy(configurationSnapshot = impossibleConfiguration,
            trainingAssessment = HybridTrainingPolicy.assessment(impossibleConfiguration.separationRules, emptyList()))
        val forged = applied.copy(hybridPlanning = impossibleConfiguration, hybridOccurrenceSchedules = listOf(original.copy(
            schedule = original.schedule.copy(proposalSnapshot = forgedProposal),
        )))
        assertTrue(HybridProgramPlanner.isCurrent(forged, "week", forgedProposal))
        assertNull(HybridOccurrenceResolver.scheduleFor(forged, HybridCalendarFixtures.week(forged)))

        val falselyAssessed = applied.copy(hybridOccurrenceSchedules = listOf(original.copy(schedule = original.schedule.copy(
            proposalSnapshot = proposal.copy(trainingAssessment = HybridTrainingAssessment(
                declaredConstraints = HybridDeclaredConstraintsStatus.SATISFIED)),
        ))))
        assertNull(HybridOccurrenceResolver.scheduleFor(falselyAssessed, HybridCalendarFixtures.week(falselyAssessed)))
    }

    @Test fun importedBudgetCannotBeCreditedAsWeeklyTrainingDose() {
        val applied = HybridCalendarFixtures.applyActive()
        val original = applied.hybridOccurrenceSchedules.single()
        val proposal = original.schedule.proposalSnapshot
        val forgedConfiguration = proposal.configurationSnapshot.copy(weeklyGoalRequirements = listOf(
            HybridWeeklyGoalRequirement("strength", minimumMinutes = 45),
        ))
        val forgedProposal = proposal.copy(configurationSnapshot = forgedConfiguration,
            trainingAssessment = HybridTrainingPolicy.assessment(emptyList(), forgedConfiguration.weeklyGoalRequirements))
        val forged = applied.copy(hybridPlanning = forgedConfiguration, hybridOccurrenceSchedules = listOf(original.copy(
            schedule = original.schedule.copy(proposalSnapshot = forgedProposal),
        )))
        assertTrue(HybridProgramPlanner.isCurrent(forged, "week", forgedProposal))
        assertNull(HybridOccurrenceResolver.scheduleFor(forged, HybridCalendarFixtures.week(forged)))
    }

    @Test fun pastCurrentOccurrenceAndCompletedSessionCannotBeAccepted() {
        val source = HybridCalendarFixtures.active()
        val request = HybridCalendarFixtures.activeRequest(source)
        val pastTime = HybridCalendarApplicator.apply(HybridCalendarSourceSnapshot(source), request,
            LocalDate.parse("2026-10-19"), 123456L, currentMinuteOfDay = 600) as HybridCalendarApplicationResult.Rejected
        assertTrue(pastTime.reason is HybridCalendarRejection.PastScheduledTime)
        val log = WorkoutLog("log", source.id, "strength-session", "Done", "2026-10-19", 45,
            programRunId = "run", cycleNumber = 3, weekInstanceId = "inst_c3_week", weekId = "week")
        val completed = HybridCalendarApplicator.apply(HybridCalendarSourceSnapshot(source, history = listOf(log)),
            request, LocalDate.parse("2026-10-18"), 123456L) as HybridCalendarApplicationResult.Rejected
        assertTrue(completed.reason is HybridCalendarRejection.CompletedSessions)
        val pastCycle = log.copy(programRunId = "old-run", cycleNumber = 2, weekInstanceId = "inst_c2_week")
        assertTrue(HybridCalendarApplicator.apply(HybridCalendarSourceSnapshot(source, history = listOf(pastCycle)),
            request, LocalDate.parse("2026-10-18"), 123456L) is HybridCalendarApplicationResult.Applied)
    }
}

package com.example.kpkn.domain.multisport

import com.example.kpkn.data.db.dbJson
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HybridWeekPlannerTest {
    @Test
    fun plansThreeStrengthAndTwoRunningCandidatesWithoutChangingPrescriptions() {
        val intent = intent(
            goal("strength", SportId.STRENGTH),
            goal("running", SportId.RUNNING),
            primary = "strength",
        )
        val windows = listOf(
            window("mon-gym", 1, 480, 570, "gym", "barbell", "rack"),
            window("tue-track", 2, 480, 570, "track"),
            window("wed-gym", 3, 480, 570, "gym", "barbell", "rack"),
            window("thu-track", 4, 480, 570, "track"),
            window("fri-gym", 5, 480, 570, "gym", "barbell", "rack"),
        )
        val prescribed = listOf(
            session("strength-a", "strength", 45, requiredEquipment = setOf("barbell")),
            session("strength-b", "strength", 45, requiredEquipment = setOf("barbell")),
            session("strength-c", "strength", 45, requiredEquipment = setOf("barbell")),
            session("run-a", "running", 30, allowedPlaces = setOf("track")),
            session("run-b", "running", 30, allowedPlaces = setOf("track")),
        )

        val result = plan(intent, windows, sessions = prescribed)

        assertTrue(result is HybridWeekPlanResult.Ready)
        result as HybridWeekPlanResult.Ready
        assertEquals(5, result.assignments.size)
        assertTrue(result.omittedOptionalSessions.isEmpty())
        assertEquals(
            prescribed.sortedBy(CandidateSession::id),
            result.assignments.map(SessionAssignment::session).sortedBy(CandidateSession::id),
        )
    }

    @Test
    fun supportsAWeekWithOnlyOneSport() {
        val result = plan(
            intent(goal("running", SportId.RUNNING)),
            windows = listOf(window("run-1", 2, 420, 480, "track"), window("run-2", 5, 420, 480, "track")),
            sessions = listOf(
                session("run-a", "running", 30, allowedWeekdays = setOf(2)),
                session("run-b", "running", 30, allowedWeekdays = setOf(5)),
            ),
        )

        assertTrue(result is HybridWeekPlanResult.Ready)
        val assignments = (result as HybridWeekPlanResult.Ready).assignments
        assertEquals(2, assignments.size)
        assertEquals(mapOf("run-a" to 2, "run-b" to 5), assignments.associate { it.session.id to it.weekday })
    }

    @Test
    fun placesCandidateAfterFixedClassAndDoesNotOverlapIt() {
        val result = plan(
            intent(goal("strength", SportId.STRENGTH)),
            windows = listOf(window("evening", 1, 480, 660, "gym")),
            commitments = listOf(FixedTrainingCommitment("class", 1, 510, 570)),
            sessions = listOf(session("strength-a", "strength", 60)),
        ) as HybridWeekPlanResult.Ready

        val assignment = result.assignments.single()
        assertEquals(570, assignment.startMinute)
        assertEquals(630, assignment.endMinute)
    }

    @Test
    fun equipmentMustExistAtTheAssignedPlaceForEachSession() {
        val result = plan(
            intent(goal("strength", SportId.STRENGTH), goal("running", SportId.RUNNING)),
            windows = listOf(
                window("barbell-room", 1, 480, 540, "room-a", "barbell"),
                window("treadmill-room", 2, 480, 540, "room-b", "treadmill"),
            ),
            sessions = listOf(
                session("lift", "strength", 30, requiredEquipment = setOf("barbell")),
                session("run", "running", 30, requiredEquipment = setOf("treadmill")),
            ),
        ) as HybridWeekPlanResult.Ready

        assertEquals(mapOf("lift" to "room-a", "run" to "room-b"),
            result.assignments.associate { it.session.id to it.placeId })
    }

    @Test
    fun equipmentFromDifferentPlacesIsNeverCombined() {
        val result = plan(
            intent(goal("hybrid", SportId.STRENGTH)),
            windows = listOf(
                window("barbell", 1, 480, 540, "room-a", "barbell"),
                window("treadmill", 1, 480, 540, "room-b", "treadmill"),
            ),
            sessions = listOf(session("hybrid-session", "hybrid", 30, requiredEquipment = setOf("barbell", "treadmill"))),
        )

        assertTrue(result is HybridWeekPlanResult.NotFeasible)
        val failure = (result as HybridWeekPlanResult.NotFeasible).reasons.single()
        assertEquals("hybrid-session", failure.sessionId)
        assertTrue(failure.reasons.contains(SessionPlacementReason.NO_COMPATIBLE_WINDOW))
    }

    @Test
    fun impossibleRequiredSetDoesNotReturnPartialSuccess() {
        val result = plan(
            intent(goal("strength", SportId.STRENGTH)),
            windows = listOf(window("only-slot", 1, 480, 510, "gym")),
            sessions = listOf(session("one", "strength", 30), session("two", "strength", 30)),
        )

        assertTrue(result is HybridWeekPlanResult.NotFeasible)
        result as HybridWeekPlanResult.NotFeasible
        assertEquals(setOf("one", "two"), result.reasons.map(SessionPlacementFailure::sessionId).toSet())
        assertTrue(result.reasons.all {
            it.reasons == listOf(SessionPlacementReason.NO_FEASIBLE_REQUIRED_COMBINATION)
        })
    }

    @Test
    fun sameDaySessionsMayBeAdjacentButMayNotOverlap() {
        val result = plan(
            intent(goal("strength", SportId.STRENGTH)),
            windows = listOf(window("morning", 1, 480, 540, "gym")),
            sessions = listOf(session("a", "strength", 30), session("b", "strength", 30)),
        ) as HybridWeekPlanResult.Ready

        val (first, second) = result.assignments
        assertEquals(first.endMinute, second.startMinute)
        assertFalse(first.startMinute < second.endMinute && first.endMinute > second.startMinute)
    }

    @Test
    fun backtracksWhenPrimaryGoalFirstChoiceBlocksAnotherGoal() {
        val intent = intent(
            goal("primary", SportId.RUNNING),
            goal("secondary", SportId.STRENGTH),
            primary = "primary",
        )
        val result = plan(
            intent,
            windows = listOf(
                window("narrow", 1, 480, 510, "gym", "mat"),
                window("wide", 1, 480, 600, "gym", "barbell"),
            ),
            sessions = listOf(
                session("primary-session", "primary", 30),
                session("secondary-session", "secondary", 30, requiredEquipment = setOf("mat")),
            ),
        ) as HybridWeekPlanResult.Ready

        assertEquals(
            mapOf("primary-session" to 510, "secondary-session" to 480),
            result.assignments.associate { it.session.id to it.startMinute },
        )
    }

    @Test
    fun findsMinuteBoundaryBetweenDisjointNarrowSessions() {
        val intent = intent(
            goal("flexible", SportId.STRENGTH),
            goal("early-only", SportId.RUNNING),
            goal("late-only", SportId.CYCLING),
            primary = "flexible",
        )

        val result = plan(
            intent,
            windows = listOf(
                window("general", 1, 0, 100, "general", "basic"),
                window("b-window", 1, 0, 50, "place-b", "b-equipment"),
                window("c-window", 1, 70, 100, "place-c", "c-equipment"),
            ),
            sessions = listOf(
                session(
                    "flexible-session",
                    "flexible",
                    40,
                    allowedPlaces = setOf("general"),
                    requiredEquipment = setOf("basic"),
                ),
                session("early-session", "early-only", 30, requiredEquipment = setOf("b-equipment")),
                session("late-session", "late-only", 30, requiredEquipment = setOf("c-equipment")),
            ),
        ) as HybridWeekPlanResult.Ready

        assertEquals(
            mapOf("early-session" to 0, "flexible-session" to 30, "late-session" to 70),
            result.assignments.associate { it.session.id to it.startMinute },
        )
    }

    @Test
    fun prioritizesPrimaryGoalAndNormalizesInputSetAndListOrder() {
        val intent = intent(
            goal("strength", SportId.STRENGTH),
            goal("running", SportId.RUNNING),
            goal("cycling", SportId.CYCLING),
            primary = "running",
        )
        val firstWindows = listOf(
            window("late", 1, 540, 570, "gym", "barbell", "rack"),
            window("middle", 1, 510, 540, "gym", "barbell", "rack"),
            window("early", 1, 480, 510, "gym", "rack", "barbell"),
        )
        val firstSessions = listOf(
            session("cycling", "cycling", 30, requiredEquipment = linkedSetOf("rack", "barbell")),
            session("strength", "strength", 30, requiredEquipment = linkedSetOf("rack", "barbell")),
            session("running", "running", 30, requiredEquipment = linkedSetOf("barbell", "rack")),
        )
        val first = plan(intent, firstWindows, sessions = firstSessions) as HybridWeekPlanResult.Ready
        val second = plan(
            intent,
            firstWindows.reversed(),
            sessions = firstSessions.reversed(),
        ) as HybridWeekPlanResult.Ready

        assertEquals(first.assignments, second.assignments)
        assertEquals("running", first.assignments.first().session.id)
        assertEquals("strength", first.assignments[1].session.id)
        assertEquals("cycling", first.assignments[2].session.id)
        assertEquals(480, first.assignments.first().startMinute)
    }

    @Test
    fun rejectsOvernightRangesAndExplicitlyEmptyAllowedWeekdaysAsInvalid() {
        val result = plan(
            intent(goal("running", SportId.RUNNING)),
            windows = listOf(window("overnight", 1, 1_320, 60, "home")),
            sessions = listOf(session("run", "running", 30, allowedWeekdays = emptySet())),
        )

        assertTrue(result is HybridWeekPlanResult.Invalid)
        val reasons = (result as HybridWeekPlanResult.Invalid).reasons
        assertTrue(reasons.any { it is PlannerInputError.InvalidWindowTimeRange })
        assertTrue(reasons.contains(PlannerInputError.EmptyAllowedWeekdays("run")))
    }

    @Test
    fun rejectsFutureIntentVersionBeforePlanningItsKnownFields() {
        val futureIntent = dbJson.decodeFromString<TrainingIntent>(
            """{"schemaVersion":2,"goals":{"future":"opaque-shape"},"primaryGoalId":["opaque"]}""",
        )
        assertTrue(futureIntent.isOpaque)

        val result = plan(
            futureIntent,
            windows = listOf(window("slot", 1, 480, 540, "track")),
            sessions = listOf(session("run", "running", 30)),
        )

        assertTrue(result is HybridWeekPlanResult.Invalid)
        assertTrue((result as HybridWeekPlanResult.Invalid).reasons.single() is PlannerInputError.InvalidIntent)
    }

    @Test
    fun acceptsLateNightWindowWithinTheSameConventionalWeekday() {
        val result = plan(
            intent(goal("running", SportId.RUNNING)),
            windows = listOf(window("late-night", 7, 1_320, 1_440, "track")),
            sessions = listOf(session("run", "running", 60)),
        )

        assertTrue(result is HybridWeekPlanResult.Ready)
        val assignment = (result as HybridWeekPlanResult.Ready).assignments.single()
        assertEquals(7, assignment.weekday)
        assertEquals(1_320, assignment.startMinute)
        assertEquals(1_380, assignment.endMinute)
    }

    @Test
    fun returnsExplicitSearchLimitInsteadOfClaimingInfeasibility() {
        val result = plan(
            intent(goal("strength", SportId.STRENGTH)),
            windows = listOf(window("slot", 1, 480, 540, "gym")),
            sessions = listOf(session("lift", "strength", 30)),
            budget = 1,
        )

        assertEquals(HybridWeekPlanResult.SearchLimitReached(exploredNodes = 1, budget = 1), result)
    }

    @Test
    fun omittedOptionalSessionsIncludeAnExplanation() {
        val result = plan(
            intent(goal("strength", SportId.STRENGTH), goal("running", SportId.RUNNING)),
            windows = listOf(window("slot", 1, 480, 510, "shared")),
            sessions = listOf(
                session("required", "strength", 30),
                session("optional", "running", 30, required = false),
            ),
        ) as HybridWeekPlanResult.Ready

        assertEquals(listOf("required"), result.assignments.map { it.session.id })
        assertEquals("optional", result.omittedOptionalSessions.single().sessionId)
        assertTrue(result.omittedOptionalSessions.single().reasons.contains(SessionPlacementReason.BLOCKED_BY_OTHER_SESSION))
    }

    private fun plan(
        intent: TrainingIntent,
        windows: List<AvailableTrainingWindow>,
        commitments: List<FixedTrainingCommitment> = emptyList(),
        sessions: List<CandidateSession>,
        budget: Int = HybridWeekPlanRequest.DEFAULT_SEARCH_NODE_BUDGET,
    ) = HybridWeekPlanner.plan(
        HybridWeekPlanRequest(
            intent = intent,
            availableWindows = windows,
            fixedCommitments = commitments,
            candidateSessions = sessions,
            searchNodeBudget = budget,
        ),
    )

    private fun intent(vararg goals: SportGoal, primary: String = goals.first().id) = TrainingIntent(
        goals = goals.toList(),
        primaryGoalId = primary,
    )

    private fun goal(id: String, sport: SportId) = SportGoal(
        id = id,
        sport = sport,
        objective = "Progress in $id",
        priority = GoalPriority.PROGRESS,
        experience = SportExperience.BEGINNER,
    )

    private fun window(
        id: String,
        weekday: Int,
        start: Int,
        end: Int,
        place: String,
        vararg equipment: String,
    ) = AvailableTrainingWindow(
        id = id,
        weekday = weekday,
        startMinute = start,
        endMinute = end,
        placeId = place,
        equipment = equipment.toSet(),
    )

    private fun session(
        id: String,
        goal: String,
        duration: Int,
        allowedWeekdays: Set<Int> = (1..7).toSet(),
        allowedPlaces: Set<String> = emptySet(),
        requiredEquipment: Set<String> = emptySet(),
        required: Boolean = true,
    ) = CandidateSession(
        id = id,
        goalId = goal,
        durationMinutes = duration,
        allowedWeekdays = allowedWeekdays,
        allowedPlaces = allowedPlaces,
        requiredEquipment = requiredEquipment,
        required = required,
    )
}

package com.example.kpkn.domain.multisport

import com.example.kpkn.data.db.dbJson
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.HybridPlannedSession
import com.example.kpkn.data.models.HybridCompletedActivityEvidence
import com.example.kpkn.data.models.HybridOccurrenceIdentity
import com.example.kpkn.data.models.HybridOccurrencePlanningContext
import com.example.kpkn.data.models.HybridProtectedSessionEvidence
import com.example.kpkn.data.models.HybridProtectedSessionKind
import com.example.kpkn.data.models.HybridProposalScope
import com.example.kpkn.data.models.HybridRecordedDurationSource
import com.example.kpkn.data.models.HybridPlanningConfiguration
import com.example.kpkn.data.models.HybridSessionGoalBinding
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionRequirement
import com.example.kpkn.domain.training.ProgramHierarchyIndex
import com.example.kpkn.domain.training.SessionDurationEstimator
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HybridProgramPlannerTest {
    @Test
    fun remainingAdapterPreservesCompletedPlacementAndPlansOnlyAfterCurrentTime() {
        val sourceWeek = week(session("done", duration = 30), session("remaining", duration = 30))
        val source = program(sourceWeek).let { it.copy(hybridPlanning = it.hybridPlanning!!.copy(
            weeklyGoalRequirements = listOf(HybridWeeklyGoalRequirement("strength", minimumSessions = 2)),
            separationRules = listOf(HybridSessionSeparationRule("gap", "done", "remaining", 0)),
        )) }
        val context = remainingContext()
        val result = HybridProgramPlanner.prepareAdapted(source, "week",
            listOf(binding("done", "strength"), binding("remaining", "strength")),
            HybridPlanningAdaptation(context, listOf(HybridWeeklyGoalRequirement("strength", minimumSessions = 1)),
                mapOf(1 to 555))) as HybridProgramPlanResult.Ready
        assertEquals(HybridProposalScope.ACTIVE_OCCURRENCE_REMAINING_SESSIONS, result.proposal.scope)
        assertEquals(listOf("remaining"), result.proposal.assignments.map { it.sessionId })
        assertTrue(result.proposal.assignments.single().startMinute >= 570)
        assertEquals(context.protectedSessions, result.proposal.occurrenceContext!!.protectedSessions)
        val dose = result.proposal.occurrenceContext!!.weeklyDose.single()
        assertEquals(1, dose.recordedActivitiesInWeek)
        assertEquals(1800L, dose.recordedExactSecondsInWeek)
        assertEquals(1, dose.plannedRemainingSessions)
        assertEquals(sourceWeek, ProgramHierarchyIndex(source).locateWeek("week")!!.week)
    }

    @Test
    fun remainingAdapterCannotPlaceIntoAnElapsedDay() {
        val source = program(week(session("done", duration = 30), session("remaining", duration = 30)))
        val result = HybridProgramPlanner.prepareAdapted(source, "week",
            listOf(binding("done", "strength"), binding("remaining", "strength")),
            HybridPlanningAdaptation(remainingContext(), emptyList(), mapOf(1 to 1440)))
        assertTrue(result is HybridProgramPlanResult.PlacementFailed)
    }

    @Test
    fun remainingAdapterKeepsUnknownHistoricalDoseUnassessed() {
        val source = program(week(session("done", duration = 30), session("remaining", duration = 30)))
            .let { it.copy(hybridPlanning = it.hybridPlanning!!.copy(weeklyGoalRequirements =
                listOf(HybridWeeklyGoalRequirement("strength", minimumMinutes = 30)))) }
        val context = remainingContext().let { it.copy(protectedSessions = it.protectedSessions.map { row ->
            row.copy(activities = emptyList())
        }) }
        val result = HybridProgramPlanner.prepareAdapted(source, "week",
            listOf(binding("done", "strength"), binding("remaining", "strength")),
            HybridPlanningAdaptation(context, emptyList(), mapOf(1 to 555))) as HybridProgramPlanResult.Ready
        assertEquals(HybridDeclaredConstraintsStatus.UNASSESSED, result.proposal.trainingAssessment.declaredConstraints)
    }

    private fun remainingContext() = HybridOccurrencePlanningContext(
        identity = HybridOccurrenceIdentity("run", 1, "instance", "week"),
        weekStartDate = "2026-10-19", asOfDate = "2026-10-19", asOfMinute = 555,
        zoneId = "UTC", sourceHistoryFingerprint = "fixture",
        protectedSessions = listOf(HybridProtectedSessionEvidence("done", "strength",
            HybridProtectedSessionKind.COMPLETED, "2026-10-19",
            HybridPlannedSession("done", "strength", 1, 540, 570, "gym", "gym-window"),
            listOf(HybridCompletedActivityEvidence("log", "2026-10-19", "2026-10-19", 1800L,
                "RECORDED", durationSource = HybridRecordedDurationSource.EXACT_RECORDED_SECONDS)))),
    )

    @Test
    fun preparesOnlyExplicitlyBoundBaseSessionsAndPreservesTheProgram() {
        val base = session(
            id = "strength-session",
            name = "Could be anything",
            duration = 45,
            exerciseName = "Squat",
            variants = true,
        )
        val sourceWeek = week(base)
        val source = program(sourceWeek)
        val result = HybridProgramPlanner.prepare(
            source,
            sourceWeek.id,
            listOf(binding(base.id, "strength", duration = 30)),
        )

        assertTrue(result is HybridProgramPlanResult.Ready)
        val ready = result as HybridProgramPlanResult.Ready
        val proposal = ready.proposal
        assertEquals("strength", proposal.assignments.single().goalId)
        assertEquals("strength-session", proposal.assignments.single().sessionId)
        assertEquals(30, proposal.assignments.single().endMinute - proposal.assignments.single().startMinute)
        assertEquals(HybridSessionDurationProvenance.BUDGET, ready.durationPlans.single().resolution.provenance)
        assertEquals(HybridSessionDurationBudgetSource.BINDING, ready.durationPlans.single().resolution.budgetSource)
        assertEquals("strength", proposal.intentSnapshot.primaryGoalId)
        assertEquals(listOf("strength"), proposal.intentSnapshot.goals.map { it.id })
        assertEquals(source, program(sourceWeek))
        assertEquals(base, ProgramHierarchyIndex(source).locateWeek(sourceWeek.id)!!.week.sessions.single())
        assertTrue(base.sessionB != null && base.sessionC != null && base.sessionD != null)
    }

    @Test
    fun explicitSessionPlaceCannotBeRelaxedAndAllowedPlaceConflictIsInvalid() {
        val fixedAtGym = session("lift", duration = 30, placeId = "gym")
        val source = program(week(fixedAtGym))
        val conflictingBinding = binding("lift", "strength", allowedPlaces = setOf("home"))

        val invalid = HybridProgramPlanner.prepare(source, "week", listOf(conflictingBinding))
        assertTrue(invalid is HybridProgramPlanResult.Invalid)
        assertTrue((invalid as HybridProgramPlanResult.Invalid).reasons.any {
            it is HybridProgramPlanningError.BindingExcludesSessionPlace
        })

        val compatibleSource = program(week(fixedAtGym))
        val ready = HybridProgramPlanner.prepare(
            compatibleSource,
            "week",
            listOf(binding("lift", "strength", allowedPlaces = setOf("gym", "home"))),
        ) as HybridProgramPlanResult.Ready
        assertEquals("gym", ready.proposal.assignments.single().placeId)
    }

    @Test
    fun proposalCannotBeReusedUnderAnIdenticalWeekInAnotherProgram() {
        val source = program(week(session("lift", duration = 30)))
        val proposal = (HybridProgramPlanner.prepare(
            source, "week", listOf(binding("lift", "strength")),
        ) as HybridProgramPlanResult.Ready).proposal
        assertEquals(source.id, proposal.sourceProgramId)
        assertTrue(HybridProgramPlanner.isCurrent(source, "week", proposal))
        assertFalse(HybridProgramPlanner.isCurrent(source.copy(id = "another-program"), "week", proposal))
        assertFalse(HybridProgramPlanner.isCurrent(source, "week", proposal.copy(sourceProgramId = "")))
        assertTrue(HybridProgramPlanner.prepare(source.copy(id = " "), "week", listOf(binding("lift", "strength")))
            is HybridProgramPlanResult.Invalid)
    }

    @Test
    fun bindingWeekdayPlaceAndEquipmentConstraintsReachThePlacementPlanner() {
        val sourceWeek = week(session("ambiguous-name", name = "Morning workout", duration = 25))
        val source = program(
            sourceWeek,
            intent = TrainingIntent(
                goals = validIntent(includeRunning = true).goals.filter { it.id == "running" },
                primaryGoalId = "running",
            ),
            windows = listOf(
                window("wrong-day", 1, 480, 540, "track", setOf("treadmill")),
                window("wrong-place", 2, 480, 540, "gym", setOf("treadmill")),
                window("wrong-equipment", 2, 480, 540, "track", setOf("mat")),
                window("only-match", 2, 480, 540, "track", setOf("treadmill")),
            ),
        )

        val ready = HybridProgramPlanner.prepare(
            source,
            sourceWeek.id,
            listOf(binding(
                "ambiguous-name",
                "running",
                allowedWeekdays = setOf(2),
                allowedPlaces = setOf("track"),
                requiredEquipment = setOf("treadmill"),
            )),
        ) as HybridProgramPlanResult.Ready

        assertEquals(
            listOf(HybridPlannedSession("ambiguous-name", "running", 2, 480, 505, "track", "only-match")),
            ready.proposal.assignments,
        )
    }

    @Test
    fun fixedCommitmentsAreSnapshottedAndKeepSessionsOutsideTheirIntervals() {
        val sourceWeek = week(session("lift", duration = 30))
        val classBlock = FixedTrainingCommitment("class", 1, 480, 510)
        val source = program(
            sourceWeek,
            windows = listOf(window("morning", 1, 480, 540, "gym")),
            commitments = listOf(classBlock),
        )

        val proposal = (HybridProgramPlanner.prepare(
            source,
            sourceWeek.id,
            listOf(binding("lift", "strength")),
        ) as HybridProgramPlanResult.Ready).proposal

        assertEquals(listOf(classBlock), proposal.configurationSnapshot.fixedCommitments)
        assertEquals(510, proposal.assignments.single().startMinute)
        assertEquals(540, proposal.assignments.single().endMinute)
    }

    @Test
    fun requiredSessionsMustAllPlaceAndOptionalOmissionsAreExplicit() {
        val required = session("required", duration = 30, requirement = SessionRequirement.REQUIRED)
        val optional = session("optional", duration = 30, requirement = SessionRequirement.OPTIONAL)
        val sourceWeek = week(required, optional)
        val source = program(sourceWeek, windows = listOf(window("only", 1, 480, 510, "gym")))
        val result = HybridProgramPlanner.prepare(
            source,
            sourceWeek.id,
            listOf(binding("required", "strength"), binding("optional", "strength")),
        )

        assertTrue(result is HybridProgramPlanResult.Ready)
        val proposal = (result as HybridProgramPlanResult.Ready).proposal
        assertEquals(listOf("required"), proposal.assignments.map { it.sessionId })
        assertEquals(listOf("optional"), proposal.omittedOptionalSessionIds)
        assertEquals("strength", proposal.omittedOptionalSessions.single().goalId)
        assertTrue(proposal.omittedOptionalSessions.single().reasons.isNotEmpty())

        val impossible = program(
            week(required, session("another-required", duration = 30)),
            intent = validIntent(includeRunning = true),
            windows = listOf(window("only", 1, 480, 510, "gym")),
        )
        val failed = HybridProgramPlanner.prepare(
            impossible,
            "week",
            listOf(binding("required", "strength"), binding("another-required", "running")),
        )
        assertTrue(failed is HybridProgramPlanResult.PlacementFailed)
        assertTrue((failed as HybridProgramPlanResult.PlacementFailed).result is HybridWeekPlanResult.NotFeasible)
    }

    @Test
    fun everyIntentGoalMustBeBoundBeforePlacement() {
        val sourceWeek = week(session("lift", duration = 30))
        val source = program(sourceWeek, intent = validIntent(includeRunning = true))

        val result = HybridProgramPlanner.prepare(
            source,
            sourceWeek.id,
            listOf(binding("lift", "strength")),
        ) as HybridProgramPlanResult.Invalid

        assertTrue(result.reasons.contains(HybridProgramPlanningError.IntentGoalUnboundBeforePlacement("running")))
    }

    @Test
    fun optionalOmissionCannotLeaveAnIntentGoalWithoutTrainingAfterPlacement() {
        val required = session("required-strength", duration = 30)
        val optional = session("optional-running", duration = 30, requirement = SessionRequirement.OPTIONAL)
        val sourceWeek = week(required, optional)
        val source = program(
            sourceWeek,
            intent = validIntent(includeRunning = true),
            windows = listOf(window("only", 1, 480, 510, "gym")),
        )

        val result = HybridProgramPlanner.prepare(
            source,
            sourceWeek.id,
            listOf(binding("required-strength", "strength"), binding("optional-running", "running")),
        ) as HybridProgramPlanResult.Invalid

        assertTrue(result.reasons.contains(
            HybridProgramPlanningError.IntentGoalUncoveredAfterPlacement(
                goalId = "running",
                omittedOptionalSessionIds = listOf("optional-running"),
            ),
        ))
    }

    @Test
    fun plannerReportsWhenEstimatedContentExceedsTheBindingBudget() {
        val mixed = session("mixed", duration = 60).copy(
            exercises = listOf(
                Exercise(
                    id = "lift",
                    name = "Lift",
                    sets = listOf(ExerciseSet("lift-set", targetReps = 5)),
                ),
                Exercise(
                    id = "run",
                    name = "Run",
                    cardioDetails = CardioDetails(
                        type = CardioType.RUN_OUTDOOR,
                        targetDurationSeconds = 10 * 60,
                    ),
                ),
            ),
        )
        val sourceWeek = week(mixed)

        val result = HybridProgramPlanner.prepare(
            program(sourceWeek),
            sourceWeek.id,
            listOf(binding("mixed", "strength", duration = 10)),
        ) as HybridProgramPlanResult.Invalid

        val budgetError = result.reasons.filterIsInstance<HybridProgramPlanningError.InsufficientSessionDurationBudget>()
            .single()
        assertEquals("mixed", budgetError.sessionId)
        assertTrue(budgetError.estimatedMinutes > 10)
        assertEquals(10, budgetError.budgetMinutes)
        assertEquals(HybridSessionDurationBudgetSource.BINDING, budgetError.budgetSource)
    }

    @Test
    fun searchBudgetIsReportedSeparatelyFromPlacementImpossibility() {
        val oneSession = session("lift", duration = 30)
        val source = program(week(oneSession), windows = listOf(window("slot", 1, 480, 540, "gym")))

        val result = HybridProgramPlanner.prepare(
            source,
            "week",
            listOf(binding("lift", "strength")),
            searchNodeBudget = 1,
        )

        assertTrue(result is HybridProgramPlanResult.PlacementFailed)
        assertTrue((result as HybridProgramPlanResult.PlacementFailed).result is HybridWeekPlanResult.SearchLimitReached)
    }

    @Test
    fun rejectsLegacyProgramMissingBindingsAndMalformedDuration() {
        val sourceWeek = week(session("lift", duration = 30))
        assertTrue(
            (HybridProgramPlanner.prepare(
                program(sourceWeek, intent = null),
                sourceWeek.id,
                listOf(binding("lift", "strength")),
            ) as HybridProgramPlanResult.Invalid).reasons.contains(HybridProgramPlanningError.MissingTrainingIntent),
        )

        val missingBinding = HybridProgramPlanner.prepare(program(sourceWeek), sourceWeek.id, emptyList())
        assertTrue(missingBinding is HybridProgramPlanResult.Invalid)
        assertTrue((missingBinding as HybridProgramPlanResult.Invalid).reasons.contains(
            HybridProgramPlanningError.MissingSessionBinding("lift"),
        ))

        val noDurationWeek = week(session("untimed", duration = null))
        val estimateFallback = HybridProgramPlanner.prepare(
            program(noDurationWeek),
            noDurationWeek.id,
            listOf(binding("untimed", "strength")),
        )
        assertTrue(estimateFallback is HybridProgramPlanResult.Ready)
        val estimateReady = estimateFallback as HybridProgramPlanResult.Ready
        val estimatedMinutes = SessionDurationEstimator.estimate(noDurationWeek.sessions.single()).totalMinutes
        assertEquals(
            estimatedMinutes,
            estimateReady.proposal.assignments.single().endMinute -
                estimateReady.proposal.assignments.single().startMinute,
        )
        assertEquals(HybridSessionDurationProvenance.ESTIMATE, estimateReady.durationPlans.single().resolution.provenance)

        val noContentWeek = week(Session(id = "empty", name = "Empty", targetDurationMinutes = 15))
        val durationWithoutContent = HybridProgramPlanner.prepare(
            program(noContentWeek),
            noContentWeek.id,
            listOf(binding("empty", "strength")),
        ) as HybridProgramPlanResult.Invalid
        assertTrue(durationWithoutContent.reasons.contains(
            HybridProgramPlanningError.SessionDurationWithoutContent(
                sessionId = "empty",
                budgetMinutes = 15,
                budgetSource = HybridSessionDurationBudgetSource.SESSION_TARGET,
            ),
        ))
    }

    @Test
    fun proposalCurrentnessTracksWeekIntentAndConfiguration() {
        val baseSession = session("lift", duration = 30)
        val baseExercise = baseSession.exercises.single()
        val setWithSides = ExerciseSet(
            id = "lift-set",
            targetReps = 5,
            manualLoadRequiredSides = linkedSetOf("left", "right"),
        )
        val sourceSession = baseSession.copy(
            exercises = listOf(baseExercise.copy(sets = listOf(setWithSides))),
        )
        val sourceWeek = week(sourceSession).copy(
            trainingDayDates = linkedMapOf(2 to "2026-10-20", 1 to "2026-10-19"),
        )
        val original = program(sourceWeek)
        val proposal = (HybridProgramPlanner.prepare(
            original,
            sourceWeek.id,
            listOf(binding("lift", "strength")),
        ) as HybridProgramPlanResult.Ready).proposal

        assertTrue(HybridProgramPlanner.isCurrent(original, sourceWeek.id, proposal))
        assertTrue(HybridProgramPlanner.isCurrent(
            withWeek(
                original,
                sourceWeek.copy(
                    sessions = listOf(sourceSession.copy(
                        exercises = listOf(baseExercise.copy(sets = listOf(
                            setWithSides.copy(manualLoadRequiredSides = linkedSetOf("right", "left")),
                        ))),
                    )),
                    trainingDayDates = linkedMapOf(1 to "2026-10-19", 2 to "2026-10-20"),
                ),
            ),
            sourceWeek.id,
            proposal,
        ))
        assertTrue(HybridProgramPlanner.isCurrent(
            withWeek(original, sourceWeek.copy(hybridProposal = proposal)),
            sourceWeek.id,
            proposal,
        ))
        assertFalse(HybridProgramPlanner.isCurrent(original, "missing", proposal))
        assertFalse(HybridProgramPlanner.isCurrent(
            withWeek(original, sourceWeek.copy(sessions = listOf(sourceWeek.sessions.single().copy(name = "edited")))),
            sourceWeek.id,
            proposal,
        ))
        assertFalse(HybridProgramPlanner.isCurrent(
            original.copy(hybridPlanning = original.hybridPlanning!!.copy(
                availableWindows = listOf(window("changed", 1, 480, 540, "gym")),
            )),
            sourceWeek.id,
            proposal,
        ))
        assertFalse(HybridProgramPlanner.isCurrent(
            original.copy(trainingIntent = validIntent(primaryGoalId = "running")),
            sourceWeek.id,
            proposal,
        ))
        assertFalse(HybridProgramPlanner.isCurrent(original, sourceWeek.id, proposal.copy(schemaVersion = 99)))
    }

    @Test
    fun proposalSnapshotsCallerCollectionsAndSurviveProgramJsonRoundTrip() {
        val mutableDays = linkedSetOf(1)
        val mutablePlaces = linkedSetOf("gym")
        val mutableEquipment = linkedSetOf("rack")
        val mutableWindows = mutableListOf(window("gym-window", 1, 480, 540, "gym", mutableEquipment))
        val sourceWeek = week(session("lift", duration = 30))
        val source = program(
            sourceWeek,
            windows = mutableWindows,
        )
        val inputBindings = mutableListOf(
            HybridSessionGoalBinding(
                sessionId = "lift",
                goalId = "strength",
                allowedWeekdays = mutableDays,
                allowedPlaces = mutablePlaces,
                requiredEquipment = mutableEquipment,
            ),
        )

        val proposal = (HybridProgramPlanner.prepare(source, sourceWeek.id, inputBindings)
            as HybridProgramPlanResult.Ready).proposal
        mutableDays.clear()
        mutablePlaces.clear()
        mutableEquipment.clear()
        mutableWindows.clear()
        inputBindings.clear()

        assertEquals(setOf(1), proposal.bindings.single().allowedWeekdays)
        assertEquals(setOf("gym"), proposal.bindings.single().allowedPlaces)
        assertEquals(setOf("rack"), proposal.bindings.single().requiredEquipment)
        assertEquals(setOf("rack"), proposal.configurationSnapshot.availableWindows.single().equipment)

        val persisted = withWeek(
            source.copy(hybridPlanning = proposal.configurationSnapshot),
            sourceWeek.copy(hybridProposal = proposal),
        )
        val restored = dbJson.decodeFromString<Program>(dbJson.encodeToString(persisted))
        assertEquals(persisted, restored)
        val restoredWeek = ProgramHierarchyIndex(restored).locateWeek(sourceWeek.id)!!.week
        assertTrue(HybridProgramPlanner.isCurrent(restored, sourceWeek.id, restoredWeek.hybridProposal!!))
    }

    @Test
    fun futureOpaqueIntentIsRejectedWithoutReadingItsV1Fields() {
        val opaque = dbJson.decodeFromString<TrainingIntent>(
            """{"schemaVersion":2,"goals":{"future":"not-a-v1-list"},"primaryGoalId":["opaque"]}""",
        )
        assertTrue(opaque.isOpaque)
        val sourceWeek = week(session("lift", duration = 30))
        val source = program(sourceWeek, intent = opaque)

        val result = HybridProgramPlanner.prepare(source, sourceWeek.id, listOf(binding("lift", "strength")))
        assertTrue(result is HybridProgramPlanResult.Invalid)
        assertTrue((result as HybridProgramPlanResult.Invalid).reasons.single() is HybridProgramPlanningError.InvalidTrainingIntent)
        assertFalse(HybridProgramPlanner.isCurrent(source, sourceWeek.id, proposalStub(opaque)))
    }

    @Test
    fun rejectsDuplicateHierarchyAndUnknownOrDuplicateBindings() {
        val first = session("same-id", duration = 30)
        val duplicateWeek = week(first).copy(id = "week")
        val source = program(duplicateWeek, extraWeek = duplicateWeek)
        val hierarchyError = HybridProgramPlanner.prepare(source, "week", listOf(binding("same-id", "strength")))
        assertTrue(hierarchyError is HybridProgramPlanResult.Invalid)
        assertTrue((hierarchyError as HybridProgramPlanResult.Invalid).reasons.single() is HybridProgramPlanningError.AmbiguousHierarchy)

        val normal = program(week(session("lift", duration = 30)))
        val bindingErrors = HybridProgramPlanner.prepare(
            normal,
            "week",
            listOf(binding("lift", "strength"), binding("lift", "running"), binding("ghost", "strength")),
        )
        assertTrue(bindingErrors is HybridProgramPlanResult.Invalid)
        val reasons = (bindingErrors as HybridProgramPlanResult.Invalid).reasons
        assertTrue(reasons.contains(HybridProgramPlanningError.DuplicateBindingSessionId("lift")))
        assertTrue(reasons.contains(HybridProgramPlanningError.UnknownBindingSessionId("ghost")))
        assertTrue(reasons.size >= 2)
    }

    @Test
    fun loopRestAndEmptyWeeksCannotProduceProposals() {
        val validSession = session("lift", duration = 30)
        val cases = listOf(
            week(validSession).copy(isLoopWeek = true) to WeekRejectionReason.LOOP,
            week(validSession).copy(executionKind = com.example.kpkn.data.models.WeekExecutionKind.REST) to WeekRejectionReason.REST,
            week() to WeekRejectionReason.EMPTY,
        )
        cases.forEach { (sourceWeek, reason) ->
            val result = HybridProgramPlanner.prepare(
                program(sourceWeek),
                sourceWeek.id,
                sourceWeek.sessions.map { binding(it.id, "strength") },
            ) as HybridProgramPlanResult.Invalid
            assertTrue(result.reasons.contains(HybridProgramPlanningError.UnsupportedWeek(sourceWeek.id, reason)))
        }
    }

    @Test
    fun competitionSessionsCannotBeRepositionedAsOrdinaryTrainingCandidates() {
        val meet = session("meet", duration = 30).copy(isMeetDay = true)
        val result = HybridProgramPlanner.prepare(program(week(meet)), "week", listOf(binding("meet", "strength")))
            as HybridProgramPlanResult.Invalid
        assertTrue(result.reasons.contains(HybridProgramPlanningError.UnsupportedCompetitionSession("meet")))
    }

    private fun proposalStub(intent: TrainingIntent): com.example.kpkn.data.models.HybridWeekProposal =
        com.example.kpkn.data.models.HybridWeekProposal(
            sourceProgramId = "program",
            sourceWeekFingerprint = "irrelevant",
            intentSnapshot = intent,
            configurationSnapshot = HybridPlanningConfiguration(availableWindows = emptyList()),
            bindings = emptyList(),
            assignments = emptyList(),
        )

    private fun program(
        week: ProgramWeek,
        intent: TrainingIntent? = validIntent(),
        windows: List<AvailableTrainingWindow> = listOf(window("gym-window", 1, 480, 600, "gym", setOf("rack"))),
        commitments: List<FixedTrainingCommitment> = emptyList(),
        extraWeek: ProgramWeek? = null,
    ): Program {
        val weeks = listOf(week) + listOfNotNull(extraWeek)
        return Program(
            id = "program",
            name = "Hybrid candidate",
            trainingIntent = intent,
            hybridPlanning = HybridPlanningConfiguration(availableWindows = windows, fixedCommitments = commitments),
            macrocycles = listOf(
                Macrocycle(
                    id = "macro",
                    name = "Macro",
                    blocks = listOf(
                        Block(
                            id = "block",
                            name = "Block",
                            mesocycles = listOf(Mesocycle(id = "meso", name = "Meso", weeks = weeks)),
                        ),
                    ),
                ),
            ),
        )
    }

    private fun withWeek(program: Program, week: ProgramWeek): Program {
        val location = ProgramHierarchyIndex(program).locateWeek(week.id)!!
        return program.copy(macrocycles = program.macrocycles.map { macro ->
            if (macro.id != location.macrocycleId) macro else macro.copy(blocks = macro.blocks.map { block ->
                if (block.id != location.blockId) block else block.copy(mesocycles = block.mesocycles.map { meso ->
                    if (meso.id != location.mesocycleId) meso else meso.copy(weeks = meso.weeks.map { existing ->
                        if (existing.id == week.id) week else existing
                    })
                })
            })
        })
    }

    private fun week(vararg sessions: Session) = ProgramWeek(id = "week", name = "Week", sessions = sessions.toList())

    private fun session(
        id: String,
        name: String = id,
        duration: Int?,
        requirement: SessionRequirement = SessionRequirement.REQUIRED,
        placeId: String? = null,
        exerciseName: String = "Prescribed movement",
        variants: Boolean = false,
    ) = Session(
        id = id,
        name = name,
        exercises = listOf(Exercise(id = "$id-exercise", name = exerciseName, sets = listOf(ExerciseSet("$id-set", targetReps = 5)))),
        targetDurationMinutes = duration,
        requirement = requirement,
        placeId = placeId,
        sessionB = if (variants) Session("$id-b", "Variant B", targetDurationMinutes = 35) else null,
        sessionC = if (variants) Session("$id-c", "Variant C", targetDurationMinutes = 40) else null,
        sessionD = if (variants) Session("$id-d", "Variant D", targetDurationMinutes = 45) else null,
    )

    private fun binding(
        sessionId: String,
        goalId: String,
        duration: Int? = null,
        allowedWeekdays: Set<Int> = setOf(1),
        allowedPlaces: Set<String> = emptySet(),
        requiredEquipment: Set<String> = emptySet(),
    ) = HybridSessionGoalBinding(
        sessionId = sessionId,
        goalId = goalId,
        durationMinutes = duration,
        allowedWeekdays = allowedWeekdays,
        allowedPlaces = allowedPlaces,
        requiredEquipment = requiredEquipment,
    )

    private fun window(
        id: String,
        weekday: Int,
        start: Int,
        end: Int,
        place: String,
        equipment: Set<String> = emptySet(),
    ) = AvailableTrainingWindow(
        id = id,
        weekday = weekday,
        startMinute = start,
        endMinute = end,
        placeId = place,
        equipment = equipment,
    )

    private fun validIntent(
        primaryGoalId: String = "strength",
        includeRunning: Boolean = primaryGoalId == "running",
    ) = TrainingIntent(
        goals = listOf(
            SportGoal("strength", SportId.STRENGTH, "Build strength", GoalPriority.PROGRESS, SportExperience.INTERMEDIATE),
        ) + if (includeRunning) listOf(
            SportGoal("running", SportId.RUNNING, "Improve running", GoalPriority.MAINTAIN, SportExperience.BEGINNER),
        ) else emptyList(),
        primaryGoalId = primaryGoalId,
    )
}

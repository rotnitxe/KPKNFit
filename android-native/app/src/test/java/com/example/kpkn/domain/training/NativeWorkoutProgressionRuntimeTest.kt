package com.example.kpkn.domain.training

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.toProgram
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.models.*
import com.example.kpkn.data.protocols.*
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.workout.LoadSuggestionEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class NativeWorkoutProgressionRuntimeTest {
    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        ProgramRepository.closeInstance()
        KpknDatabase.closeInstance()
        Dispatchers.resetMain()
    }

    @Test
    fun requiresTwoCompleteTopRangeExposuresBeforeProposal() {
        val sessions = (1..3).map { nativeSession("s$it") }
        val program = nativeProgram(sessions)
        val first = NativeWorkoutProgressionRuntime.observeCompletedWorkout(
            program = program,
            logs = listOf(workoutLog(program.id, sessions[0], "l1", "w1")),
            inventory = null,
            curatedConfigurations = emptySet(),
            nowMs = 1L,
        )
        assertTrue(first.nativeProgressionProposals.isEmpty())

        val second = NativeWorkoutProgressionRuntime.observeCompletedWorkout(
            program = program,
            logs = sessions.take(2).mapIndexed { index, session ->
                workoutLog(program.id, session, "l${index + 1}", "w${index + 1}")
            },
            inventory = null,
            curatedConfigurations = emptySet(),
            nowMs = 2L,
        )
        assertEquals(1, second.nativeProgressionProposals.size)
        assertEquals(NativeProgressionProposalKind.INCREASE_LOAD, second.nativeProgressionProposals.single().kind)
    }

    @Test
    fun recipeConfigurationAndSlotIdentityDoNotPoolExposures() {
        val sessions = listOf(
            nativeSession("s1", configurationId = "bench_press__barbell"),
            nativeSession("s2", configurationId = "bench_press__dumbbell"),
            nativeSession("s3", configurationId = "bench_press__barbell"),
        )
        val program = nativeProgram(sessions)
        val logs = sessions.mapIndexed { index, session ->
            workoutLog(program.id, session, "identity-$index", "w${index + 1}")
        }

        val observed = NativeWorkoutProgressionRuntime.observeCompletedWorkout(
            program = program,
            logs = logs,
            inventory = null,
            curatedConfigurations = emptySet(),
            nowMs = 3L,
        )

        assertTrue(observed.nativeProgressionProposals.isEmpty())
    }

    @Test
    fun knownStockChoosesSmallestRealIncrementAndUnknownStockLeavesLoadPending() {
        val sessions = (1..3).map { nativeSession("s$it") }
        val program = nativeProgram(sessions)
        val logs = sessions.take(2).mapIndexed { index, session ->
            workoutLog(program.id, session, "known-$index", "w${index + 1}")
        }
        val known = NativeWorkoutProgressionRuntime.observeCompletedWorkout(
            program = program,
            logs = logs,
            inventory = EquipmentInventory(plates = listOf(PlateStock(weightKg = 0.5, countPerSide = 2))),
            curatedConfigurations = emptySet(),
            nowMs = 4L,
        ).nativeProgressionProposals.single()
        assertEquals(21.0, known.targetLoadKg!!, 0.0001)

        val unknown = NativeWorkoutProgressionRuntime.observeCompletedWorkout(
            program = program,
            logs = logs,
            inventory = null,
            curatedConfigurations = emptySet(),
            nowMs = 5L,
        ).nativeProgressionProposals.single()
        assertNull(unknown.targetLoadKg)
        assertTrue(unknown.explanation.contains("elige una carga"))

        val accepted = NativeWorkoutProgressionRuntime.resolveProposal(
            program = program.copy(nativeProgressionProposals = listOf(unknown)),
            proposalId = unknown.proposalId,
            accept = true,
            logs = logs,
            ongoingSessionIds = emptySet(),
            curatedExercises = emptyMap(),
            nowMs = 6L,
        )
        val futureSet = ProgramHierarchyIndex(accepted).locateSession(sessions[2].id)!!
            .session.allExercises().single().sets.first()
        assertNull(futureSet.weight)
        assertEquals(setOf("bilateral"), futureSet.manualLoadRequiredSides)
        assertEquals(PlanLoadReferenceState.PENDING, futureSetReferenceState(accepted, sessions[2].id))
        val genericSuggestion = LoadSuggestionEngine.suggestFromLastWorkingSet(
            lastSet = CompletedSet(id = "generic-set", weight = 20.0, reps = 8),
            targetReps = 8,
            loadMode = LoadModeV2.LOAD,
            activeTag = null,
            baseEntryTag = null,
            techniqueSignal = 0,
        )
        assertEquals(20.5, genericSuggestion!!.suggestedWeight, 0.0001)
        assertTrue(LoadSuggestionEngine.shouldDeferToNativeProgression(
            ProgramHierarchyIndex(accepted).locateSession(sessions[2].id)!!.session.allExercises().single()
                .copy(nativeProgressionManaged = false),
            setIdx = 0,
        ))
    }

    @Test
    fun assistedLoadProgressionMovesAssistanceInTheResistanceDirection() {
        val configurationId = "pull_up__assisted__machine"
        val sessions = (1..3).map { sessionId ->
            nativeSession(
                id = "assisted-$sessionId",
                configurationId = configurationId,
                loadMode = LoadModeV2.ASSISTED,
                quantityConvention = LoadQuantityConvention.ASSISTANCE,
            )
        }
        val program = nativeProgram(sessions, sourceRecipe = nativeRecipe(configurationId = configurationId))
        val inventory = EquipmentInventory(
            machines = listOf(
                MachineLoadRange(
                    configurationId = configurationId,
                    minLoadKg = 0.0,
                    maxLoadKg = 100.0,
                    incrementKg = 5.0,
                    baseLoadKg = 0.0,
                ),
            ),
        )

        val topLogs = sessions.take(2).mapIndexed { index, session ->
            workoutLog(program.id, session, "assisted-top-${index + 1}", "w${index + 1}", LoadModeV2.ASSISTED)
        }
        val strongerProposal = NativeWorkoutProgressionRuntime.observeCompletedWorkout(
            program = program,
            logs = topLogs,
            inventory = inventory,
            curatedConfigurations = emptySet(),
        ).nativeProgressionProposals.single()
        assertEquals(NativeProgressionProposalKind.INCREASE_LOAD, strongerProposal.kind)
        assertEquals(15.0, strongerProposal.targetLoadKg!!, 0.0001)

        val belowMinimumLogs = sessions.take(2).mapIndexed { index, session ->
            workoutLog(
                program.id,
                session,
                "assisted-low-${index + 1}",
                "w${index + 1}",
                loadMode = LoadModeV2.ASSISTED,
                reps = 5,
            )
        }
        val easierProposal = NativeWorkoutProgressionRuntime.observeCompletedWorkout(
            program = program,
            logs = belowMinimumLogs,
            inventory = inventory,
            curatedConfigurations = emptySet(),
        ).nativeProgressionProposals.single()
        assertEquals(NativeProgressionProposalKind.REDUCE_LOAD, easierProposal.kind)
        assertEquals(25.0, easierProposal.targetLoadKg!!, 0.0001)
    }

    @Test
    fun everyTerminalProposalConsumesAllOfItsSourceExposures() {
        val sessions = (1..8).map { nativeSession("boundary$it") }
        val program = nativeProgram(sessions)
        fun logsThrough(count: Int) = sessions.take(count).mapIndexed { index, session ->
            workoutLog(program.id, session, "boundary-${index + 1}", "w${index + 1}")
        }
        fun reject(current: Program, proposal: NativeProgressionProposal, logs: List<WorkoutLog>) =
            NativeWorkoutProgressionRuntime.resolveProposal(
                program = current.copy(nativeProgressionProposals = listOf(proposal)),
                proposalId = proposal.proposalId,
                accept = false,
                logs = logs,
                ongoingSessionIds = emptySet(),
                curatedExercises = emptyMap(),
            )

        val firstProposal = NativeWorkoutProgressionRuntime.observeCompletedWorkout(
            program,
            logsThrough(2),
            inventory = null,
            curatedConfigurations = emptySet(),
        ).nativeProgressionProposals.single()
        val afterFirstResolution = reject(program, firstProposal, logsThrough(2))
        val fourLogs = logsThrough(4)
        val secondProposal = NativeWorkoutProgressionRuntime.observeCompletedWorkout(
            afterFirstResolution,
            fourLogs,
            inventory = null,
            curatedConfigurations = emptySet(),
        ).nativeProgressionProposals.single()
        assertEquals(listOf("boundary-3", "boundary-4"), secondProposal.sourceLogIds)

        val afterSecondResolution = reject(afterFirstResolution, secondProposal, fourLogs)
        val afterOneFreshExposure = NativeWorkoutProgressionRuntime.observeCompletedWorkout(
            afterSecondResolution,
            logsThrough(5),
            inventory = null,
            curatedConfigurations = emptySet(),
        )
        assertTrue(afterOneFreshExposure.nativeProgressionProposals.isEmpty())

        val afterTwoFreshExposures = NativeWorkoutProgressionRuntime.observeCompletedWorkout(
            afterSecondResolution,
            logsThrough(6),
            inventory = null,
            curatedConfigurations = emptySet(),
        )
        assertEquals(
            listOf("boundary-5", "boundary-6"),
            afterTwoFreshExposures.nativeProgressionProposals.single().sourceLogIds,
        )
    }

    @Test
    fun executionErrorPayloadDoesNotCountAsACompleteProgressionExposure() {
        val sessions = (1..3).map { nativeSession("error$it") }
        val program = nativeProgram(sessions)
        val logs = sessions.take(2).mapIndexed { index, session ->
            workoutLog(
                programId = program.id,
                session = session,
                logId = "execution-error-${index + 1}",
                weekId = "w${index + 1}",
                executionError = true,
            )
        }

        val observed = NativeWorkoutProgressionRuntime.observeCompletedWorkout(
            program = program,
            logs = logs,
            inventory = null,
            curatedConfigurations = emptySet(),
        )

        assertTrue(observed.nativeProgressionProposals.isEmpty())
    }

    @Test
    fun acceptingKnownLoadChangesOnlyUnexecutedFuturePrescriptions() {
        val sessions = (1..4).map { nativeSession("future$it") }
        val program = nativeProgram(sessions)
        val logs = sessions.take(2).mapIndexed { index, session ->
            workoutLog(program.id, session, "future-$index", "w${index + 1}")
        }
        val proposal = NativeWorkoutProgressionRuntime.observeCompletedWorkout(
            program = program,
            logs = logs,
            inventory = EquipmentInventory(plates = listOf(PlateStock(weightKg = 0.5, countPerSide = 2))),
            curatedConfigurations = emptySet(),
            nowMs = 13L,
        ).nativeProgressionProposals.single()

        val applied = NativeWorkoutProgressionRuntime.resolveProposal(
            program = program.copy(nativeProgressionProposals = listOf(proposal)),
            proposalId = proposal.proposalId,
            accept = true,
            logs = logs,
            ongoingSessionIds = emptySet(),
            curatedExercises = emptyMap(),
            nowMs = 14L,
        )
        val hierarchy = ProgramHierarchyIndex(applied)
        assertEquals(20.0, hierarchy.locateSession(sessions[0].id)!!.session.allExercises().single().sets.first().weight!!, 0.0001)
        assertEquals(20.0, hierarchy.locateSession(sessions[1].id)!!.session.allExercises().single().sets.first().weight!!, 0.0001)
        assertEquals(21.0, hierarchy.locateSession(sessions[2].id)!!.session.allExercises().single().sets.first().weight!!, 0.0001)
        assertEquals(21.0, hierarchy.locateSession(sessions[3].id)!!.session.allExercises().single().sets.first().weight!!, 0.0001)
        assertEquals(listOf("future-0", "future-1"), logs.map { it.id })
        assertTrue(applied.nativeProgressionAudit.single().reason.contains("futura(s)"))
    }

    @Test
    fun unresolvedCatalogLoadModeDoesNotHideMatchingFutureRecipeSlots() {
        val sessions = (1..3).map { sessionId ->
            nativeSession("catalog-load-mode-$sessionId", persistedLoadMode = null)
        }
        val program = nativeProgram(sessions)
        val logs = sessions.take(2).mapIndexed { index, session ->
            workoutLog(program.id, session, "catalog-mode-${index + 1}", "w${index + 1}")
        }
        val proposal = NativeWorkoutProgressionRuntime.observeCompletedWorkout(
            program = program,
            logs = logs,
            inventory = EquipmentInventory(plates = listOf(PlateStock(weightKg = 0.5, countPerSide = 2))),
            curatedConfigurations = emptySet(),
        ).nativeProgressionProposals.single()

        val applied = NativeWorkoutProgressionRuntime.resolveProposal(
            program = program.copy(nativeProgressionProposals = listOf(proposal)),
            proposalId = proposal.proposalId,
            accept = true,
            logs = logs,
            ongoingSessionIds = emptySet(),
            curatedExercises = emptyMap(),
        )

        val future = ProgramHierarchyIndex(applied).locateSession(sessions[2].id)!!.session.allExercises().single()
        assertEquals(21.0, future.sets.first().weight!!, 0.0001)
        assertEquals(NativeProgressionResolutionStatus.APPLIED, applied.nativeProgressionAudit.single().status)
    }

    @Test
    fun manuallyChosenLoadIsCapturedForFutureAndNeedsTwoFreshExposures() {
        val sessions = (1..5).map { nativeSession("manual$it") }
        val program = nativeProgram(sessions)
        val firstLogs = sessions.take(2).mapIndexed { index, session ->
            workoutLog(program.id, session, "manual-${index + 1}", "w${index + 1}")
        }
        val proposal = NativeWorkoutProgressionRuntime.observeCompletedWorkout(
            program = program,
            logs = firstLogs,
            inventory = null,
            curatedConfigurations = emptySet(),
            nowMs = 15L,
        ).nativeProgressionProposals.single()
        val accepted = NativeWorkoutProgressionRuntime.resolveProposal(
            program = program.copy(nativeProgressionProposals = listOf(proposal)),
            proposalId = proposal.proposalId,
            accept = true,
            logs = firstLogs,
            ongoingSessionIds = emptySet(),
            curatedExercises = emptyMap(),
            nowMs = 16L,
        )

        val thirdLog = workoutLog(program.id, sessions[2], "manual-3", "w3", loadKg = 22.0)
        val afterManualChoice = NativeWorkoutProgressionRuntime.observeCompletedWorkout(
            program = accepted,
            logs = firstLogs + thirdLog,
            inventory = null,
            curatedConfigurations = emptySet(),
            completedLogId = thirdLog.id,
            nowMs = 17L,
        )
        assertTrue(afterManualChoice.nativeProgressionProposals.isEmpty())
        listOf(sessions[3], sessions[4]).forEach { future ->
            val exercise = ProgramHierarchyIndex(afterManualChoice).locateSession(future.id)!!.session.allExercises().single()
            assertEquals(22.0, exercise.sets.first().weight!!, 0.0001)
            assertTrue(exercise.sets.all { it.manualLoadRequiredSides.isEmpty() })
            assertEquals(PlanLoadReferenceState.CAPTURED, exercise.loadReference?.state)
            assertEquals("bilateral", exercise.loadReference?.side)
        }

        val fourthLog = workoutLog(program.id, sessions[3], "manual-4", "w4", loadKg = 22.0)
        val afterTwoFreshExposures = NativeWorkoutProgressionRuntime.observeCompletedWorkout(
            program = afterManualChoice,
            logs = firstLogs + thirdLog + fourthLog,
            inventory = null,
            curatedConfigurations = emptySet(),
            completedLogId = fourthLog.id,
            nowMs = 18L,
        )
        assertEquals(listOf("manual-3", "manual-4"), afterTwoFreshExposures.nativeProgressionProposals.single().sourceLogIds)
    }

    @Test
    fun bodyweightVariantReplacementClearsLoadReferencesAndPreservesSessionIdentity() {
        val sessions = (1..3).map { nativeSession("bw$it", configurationId = "knee_push_up__default", loadMode = LoadModeV2.BODYWEIGHT) }
        val futureExercise = sessions[2].allExercises().single()
        val program = nativeProgram(
            sessions = sessions,
            sourceRecipe = nativeRecipe(
                strategy = NativeProgressionStrategy.BODYWEIGHT_VARIANT_ESCALATION,
                configurationId = "knee_push_up__default",
            ),
            exerciseLoadReferences = listOf(
                ExerciseLoadReference(
                    exerciseId = futureExercise.id,
                    references = listOf(
                        PlanLoadReference(
                            kind = PlanLoadReferenceKind.EXERCISE_1RM,
                            configurationId = "knee_push_up__default",
                            quantityConvention = LoadQuantityConvention.UNSPECIFIED,
                            state = PlanLoadReferenceState.CAPTURED,
                            capturedLoadKg = 40.0,
                        ),
                    ),
                ),
            ),
        )
        val logs = sessions.take(2).mapIndexed { index, session ->
            workoutLog(program.id, session, "bw-$index", "w${index + 1}", loadMode = LoadModeV2.BODYWEIGHT)
        }
        val proposalProgram = NativeWorkoutProgressionRuntime.observeCompletedWorkout(
            program = program,
            logs = logs,
            inventory = null,
            curatedConfigurations = setOf("push_up__flat"),
            nowMs = 7L,
        )
        val proposal = proposalProgram.nativeProgressionProposals.single()
        val applied = NativeWorkoutProgressionRuntime.resolveProposal(
            program = proposalProgram,
            proposalId = proposal.proposalId,
            accept = true,
            logs = logs,
            ongoingSessionIds = emptySet(),
            curatedExercises = mapOf(
                "push_up__flat" to ExerciseMuscleInfo(
                    id = "push_up",
                    name = "Flexión",
                    equipment = "peso corporal",
                    catalogDefinitionId = "push_up",
                    catalogConfigurationId = "push_up__flat",
                    catalogRevision = "catalog-test",
                    performanceProfileId = "profile-test",
                ),
            ),
            nowMs = 8L,
        )

        val replaced = ProgramHierarchyIndex(applied).locateSession(sessions[2].id)!!.session.allExercises().single()
        assertEquals("push_up__flat", replaced.catalogConfigurationId)
        assertEquals(futureExercise.id, replaced.id)
        assertEquals("day-1", replaced.recipeDayId)
        assertEquals("slot-1", replaced.recipeSlotId)
        assertNull(replaced.loadReference)
        assertTrue(applied.exerciseLoadReferences.isEmpty())
        assertEquals("knee_push_up__default", ProgramHierarchyIndex(applied).locateSession(sessions[1].id)!!.session.allExercises().single().catalogConfigurationId)
    }

    @Test
    fun cardioLadderUsesOnlyOfferedStepsAndHonorsLimits() {
        assertEquals(15, NativeWorkoutProgressionRuntime.nextCardioDurationAfterTwoConversationalExposures(2, 10, false, null, true))
        assertNull(NativeWorkoutProgressionRuntime.nextCardioDurationAfterTwoConversationalExposures(2, 15, false, null, true))
        assertEquals(15, NativeWorkoutProgressionRuntime.nextCardioDurationAfterTwoConversationalExposures(2, 10, true, null, true))
        assertEquals(20, NativeWorkoutProgressionRuntime.nextCardioDurationAfterTwoConversationalExposures(2, 15, true, null, true))
        assertEquals(30, NativeWorkoutProgressionRuntime.nextCardioDurationAfterTwoConversationalExposures(2, 20, true, null, true))
        assertNull(NativeWorkoutProgressionRuntime.nextCardioDurationAfterTwoConversationalExposures(2, 10, true, null, false))
        assertNull(NativeWorkoutProgressionRuntime.nextCardioDurationAfterTwoConversationalExposures(2, 10, false, 20, true))
    }

    @Test
    fun rejectionIsTerminalAndRepeatedResolutionIsIdempotent() {
        val sessions = (1..3).map { nativeSession("reject$it") }
        val program = nativeProgram(sessions)
        val logs = sessions.take(2).mapIndexed { index, session ->
            workoutLog(program.id, session, "reject-$index", "w${index + 1}")
        }
        val proposal = NativeWorkoutProgressionRuntime.observeCompletedWorkout(
            program = program,
            logs = logs,
            inventory = null,
            curatedConfigurations = emptySet(),
            nowMs = 9L,
        ).nativeProgressionProposals.single()
        val withProposal = program.copy(nativeProgressionProposals = listOf(proposal))

        val rejected = NativeWorkoutProgressionRuntime.resolveProposal(
            withProposal,
            proposal.proposalId,
            accept = false,
            logs = logs,
            ongoingSessionIds = emptySet(),
            curatedExercises = emptyMap(),
            nowMs = 10L,
        )
        val repeated = NativeWorkoutProgressionRuntime.resolveProposal(
            rejected,
            proposal.proposalId,
            accept = true,
            logs = logs,
            ongoingSessionIds = emptySet(),
            curatedExercises = emptyMap(),
            nowMs = 11L,
        )

        assertTrue(rejected.nativeProgressionProposals.isEmpty())
        assertEquals(NativeProgressionResolutionStatus.REJECTED, rejected.nativeProgressionAudit.single().status)
        assertEquals(rejected.nativeProgressionAudit, repeated.nativeProgressionAudit)
        assertEquals(rejected.macrocycles, repeated.macrocycles)
    }

    @Test
    fun acceptedProposalExpiresWhenItsRecipeRevisionIsStale() {
        val sessions = (1..3).map { nativeSession("stale$it") }
        val program = nativeProgram(sessions)
        val logs = sessions.take(2).mapIndexed { index, session ->
            workoutLog(program.id, session, "stale-${index + 1}", "w${index + 1}")
        }
        val proposal = NativeWorkoutProgressionRuntime.observeCompletedWorkout(
            program,
            logs,
            inventory = null,
            curatedConfigurations = emptySet(),
        ).nativeProgressionProposals.single()
        val revisedRecipe = program.sourceRecipe!!.copy(contentVersion = proposal.identity.recipeContentVersion + 1)
        val revised = program.copy(
            sourceRecipe = revisedRecipe,
            nativeProgressionProposals = listOf(proposal),
        )

        val resolved = NativeWorkoutProgressionRuntime.resolveProposal(
            program = revised,
            proposalId = proposal.proposalId,
            accept = true,
            logs = logs,
            ongoingSessionIds = emptySet(),
            curatedExercises = emptyMap(),
        )

        assertTrue(resolved.nativeProgressionProposals.isEmpty())
        assertEquals(program.macrocycles, resolved.macrocycles)
        assertEquals(NativeProgressionResolutionStatus.EXPIRED, resolved.nativeProgressionAudit.single().status)
    }

    @Test
    fun rematerializationRestoresBilateralCapturedAndPendingNativeLoads() {
        val recipe = nativeRecipe()
        val source = PlanMaterializer.materialize(
            program = Program(id = "reference-rematerialization", name = "Native reference"),
            recipe = recipe,
            metadata = CatalogCompositionTestSupport.metadata,
            strict = false,
        )
        val week = source.macrocycles.first().blocks.first().mesocycles.first().weeks.first()
        val exercise = week.sessions.single().allExercises().single()
        val capturedReference = PlanLoadReference(
            kind = PlanLoadReferenceKind.OBSERVED_WORKING_SET,
            configurationId = exercise.catalogConfigurationId!!,
            quantityConvention = LoadQuantityConvention.TOTAL_EXTERNAL,
            side = "bilateral",
            repMin = 6,
            repMax = 8,
            state = PlanLoadReferenceState.CAPTURED,
            capturedLoadKg = 22.0,
        )
        fun withReference(reference: PlanLoadReference) = source.copy(
            exerciseLoadReferences = listOf(
                ExerciseLoadReference(exerciseId = exercise.id, references = listOf(reference)),
            ),
        )
        fun rematerialize(program: Program) = PlanMaterializer.rematerializeWeek(
            program = program,
            weekId = week.id,
            recipe = recipe,
            metadata = CatalogCompositionTestSupport.metadata,
        ).let { rebuilt ->
            ProgramHierarchyIndex(rebuilt).locateSession(week.sessions.single().id)!!.session.allExercises().single()
        }

        val rebuiltCaptured = rematerialize(withReference(capturedReference))
        assertEquals(listOf(22.0, 22.0), rebuiltCaptured.sets.map { it.weight })
        assertEquals(PlanLoadReferenceState.CAPTURED, rebuiltCaptured.loadReference?.state)

        val pending = capturedReference.copy(state = PlanLoadReferenceState.PENDING, capturedLoadKg = null, capturedAtMs = null)
        val rebuiltPending = rematerialize(withReference(pending))
        assertTrue(rebuiltPending.sets.all { it.weight == null })
        assertTrue(rebuiltPending.sets.all { "bilateral" in it.manualLoadRequiredSides })
        assertEquals(PlanLoadReferenceState.PENDING, rebuiltPending.loadReference?.state)
    }

    @Test
    fun rematerializationKeepsUnilateralSideLoadsSeparate() {
        val recipe = nativeRecipe().let { source ->
            source.copy(weeks = source.weeks.map { week ->
                week.copy(days = week.days.map { day ->
                    day.copy(slots = day.slots.map { slot -> slot.copy(isUnilateral = true) })
                })
            })
        }
        val source = PlanMaterializer.materialize(
            program = Program(id = "side-reference-rematerialization", name = "Native side reference"),
            recipe = recipe,
            metadata = CatalogCompositionTestSupport.metadata,
            strict = false,
        )
        val week = source.macrocycles.first().blocks.first().mesocycles.first().weeks.first()
        val exercise = week.sessions.single().allExercises().single()
        val references = listOf(
            PlanLoadReference(
                kind = PlanLoadReferenceKind.OBSERVED_WORKING_SET,
                configurationId = exercise.catalogConfigurationId!!,
                quantityConvention = LoadQuantityConvention.PER_IMPLEMENT,
                side = "left",
                state = PlanLoadReferenceState.CAPTURED,
                capturedLoadKg = 12.0,
            ),
            PlanLoadReference(
                kind = PlanLoadReferenceKind.OBSERVED_WORKING_SET,
                configurationId = exercise.catalogConfigurationId,
                quantityConvention = LoadQuantityConvention.PER_IMPLEMENT,
                side = "right",
                state = PlanLoadReferenceState.CAPTURED,
                capturedLoadKg = 14.0,
            ),
        )
        val withReferences = source.copy(
            exerciseLoadReferences = listOf(ExerciseLoadReference(exerciseId = exercise.id, references = references)),
        )

        val rebuilt = PlanMaterializer.rematerializeWeek(
            program = withReferences,
            weekId = week.id,
            recipe = recipe,
            metadata = CatalogCompositionTestSupport.metadata,
        )
        val rebuiltExercise = ProgramHierarchyIndex(rebuilt)
            .locateSession(week.sessions.single().id)!!.session.allExercises().single()

        assertTrue(rebuiltExercise.isUnilateral)
        assertEquals(LoadQuantityConvention.PER_IMPLEMENT, rebuiltExercise.loadQuantityConvention)
        assertTrue(rebuiltExercise.sets.all { it.leftTarget?.weight == 12.0 })
        assertTrue(rebuiltExercise.sets.all { it.rightTarget?.weight == 14.0 })
        assertTrue(rebuiltExercise.sets.all { it.weight == null })
    }

    @Test
    fun legacySourceProgressionIsNotReplacedByNativeRuntime() {
        val authoredRule = ProgressionRule.CycleIncrement(upperKg = 2.5, lowerKg = 1.25)
        val sessions = (1..3).map { nativeSession("legacy$it") }
        val program = nativeProgram(sessions, sourceRecipe = nativeRecipe(strategy = null).copy(progression = authoredRule))

        val observed = NativeWorkoutProgressionRuntime.observeCompletedWorkout(
            program = program,
            logs = sessions.mapIndexed { index, session -> workoutLog(program.id, session, "legacy-$index", "w${index + 1}") },
            inventory = null,
            curatedConfigurations = emptySet(),
            nowMs = 12L,
        )

        assertSame(program, observed)
        assertEquals(authoredRule, observed.sourceRecipe?.progression)
    }

    @Test
    fun finalizeWorkoutPersistsNativeProposalAlongsideTheRealCompletedLogs() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = ProgramRepository.initForTests(context)
        withTimeout(10_000) { repository.isReady.first { it } }
        repository.resetAllStateSync()

        val sessions = (1..3).map { nativeSession("durable$it") }
        val weekIds = (1..3).map { "durable-week-$it" }
        val weeks = sessions.mapIndexed { index, session ->
            ProgramWeek(id = weekIds[index], name = "Week ${index + 1}", sessions = listOf(session))
        }
        val mesocycle = Mesocycle(id = "durable-meso", name = "Meso", weeks = weeks)
        val block = Block(id = "durable-block", name = "Block", mesocycles = listOf(mesocycle))
        val macrocycle = Macrocycle(id = "durable-macro", name = "Macro", blocks = listOf(block))
        val run = ProgramRunState(
            runId = "durable-run",
            weekId = weekIds.first(),
            weekInstanceId = weekIds.first(),
            macrocycleId = macrocycle.id,
            blockId = block.id,
            mesocycleId = mesocycle.id,
        )
        val program = Program(
            id = "native-progression-finalize",
            name = "Native progression persistence",
            structure = ProgramStructure.COMPLEX,
            macrocycles = listOf(macrocycle),
            runState = run,
            sourceRecipe = nativeRecipe(),
        )
        repository.addProgram(program)
        withTimeout(5_000) { repository.programs.first { it.any { item -> item.id == program.id } } }
        repository.updateActiveProgramState(
            ActiveProgramState(
                programId = program.id,
                currentWeekId = weekIds.first(),
                currentWeekInstanceId = weekIds.first(),
                currentMacrocycleId = macrocycle.id,
                currentBlockId = block.id,
                currentMesocycleId = mesocycle.id,
                programRunId = run.runId,
            ),
        )
        repository.updateProgramNow(program)

        sessions.take(2).forEachIndexed { index, session ->
            repository.finalizeWorkout(
                workoutLog(
                    programId = program.id,
                    session = session,
                    logId = "durable-log-${index + 1}",
                    weekId = weekIds[index],
                    programRunId = run.runId,
                    cycleNumber = 1,
                ),
            )
        }

        val persisted = repository.databaseForTests().programDao().getById(program.id)?.toProgram()
            ?: error("program not persisted after workout finalization")
        assertEquals(2, repository.history.value.count { it.programId == program.id })
        assertEquals(1, persisted.nativeProgressionProposals.size)
        assertEquals(listOf("durable-log-1", "durable-log-2"), persisted.nativeProgressionProposals.single().sourceLogIds)
    }

    // ─── §12.4 calidad de la evidencia de exposición (P7 / F-08) ───────────────

    @Test
    fun mixedLoadsInsideWindow_doNotProposeIncrement() {
        val sessions = (1..5).map { nativeSession("mixed-load-$it") }
        val program = nativeProgram(sessions)
        val first = workoutLog(program.id, sessions[0], "mixed-load-log-1", "w1", loadKg = 20.0)
        val raised = workoutLog(program.id, sessions[1], "mixed-load-log-2", "w2", loadKg = 22.5)

        val afterTwo = observe(program, listOf(first, raised))
        assertTrue("dos exposiciones en el tope a cargas distintas no son evidencia comparable", afterTwo.nativeProgressionProposals.isEmpty())
        assertTrue(afterTwo.nativeProgressionAudit.isEmpty())

        val repeated = workoutLog(program.id, sessions[2], "mixed-load-log-3", "w3", loadKg = 22.5)
        val afterThree = observe(program, listOf(first, raised, repeated))
        val proposal = afterThree.nativeProgressionProposals.single()
        assertEquals(listOf("mixed-load-log-2", "mixed-load-log-3"), proposal.sourceLogIds)
        assertNull(proposal.targetLoadKg)
    }

    @Test
    fun lastSetDropDoesNotBecomeCapturedLoad() {
        val sessions = (1..3).map { nativeSession("drop-capture-$it", plannedWeight = null) }
        val program = nativeProgram(sessions)
        val log = workoutLog(program.id, sessions[0], "drop-capture-log-1", "w1", setLoads = listOf(22.0, 18.0))

        val observed = observe(program, listOf(log), completedLogId = log.id)

        listOf(sessions[1], sessions[2]).forEach { future ->
            val exercise = ProgramHierarchyIndex(observed).locateSession(future.id)!!.session.allExercises().single()
            assertEquals(listOf(22.0, 22.0), exercise.sets.map { it.weight })
            assertEquals(22.0, exercise.loadReference?.capturedLoadKg ?: Double.NaN, 0.0001)
        }
        val trained = ProgramHierarchyIndex(observed).locateSession(sessions[0].id)!!.session.allExercises().single()
        assertTrue("la sesión entrenada no se reescribe", trained.sets.all { it.weight == null })
    }

    @Test
    fun deloadWeekExposure_doesNotCountAsSuccess() {
        val sessions = (1..5).map { nativeSession("deload-$it") }
        val program = nativeProgram(sessions, deloadWeekIndices = setOf(1, 2))
        fun log(number: Int) = workoutLog(program.id, sessions[number - 1], "deload-log-$number", "w$number")

        val duringDeload = observe(program, listOf(log(1), log(2), log(3)))
        assertTrue(duringDeload.nativeProgressionProposals.isEmpty())
        assertTrue("una descarga no es éxito ni fallo: ni propuesta ni aviso", duringDeload.nativeProgressionAudit.isEmpty())

        val afterRegularWeek = observe(program, listOf(log(1), log(2), log(3), log(4)))
        assertEquals(
            listOf("deload-log-1", "deload-log-4"),
            afterRegularWeek.nativeProgressionProposals.single().sourceLogIds,
        )
    }

    @Test
    fun failureSet_countsAsRirZero_andProposesReduction() {
        val sessions = (1..4).map { nativeSession("failure-$it") }
        val program = nativeProgram(sessions)
        val logs = (1..2).map { number ->
            workoutLog(program.id, sessions[number - 1], "failure-log-$number", "w$number", failureSets = setOf(1))
        }

        val proposal = observe(program, logs).nativeProgressionProposals.single()

        assertEquals(NativeProgressionProposalKind.REDUCE_LOAD, proposal.kind)
        assertNull(proposal.targetLoadKg)
    }

    @Test
    fun liveDropSetExposure_isExcludedFromTheEvidence() {
        val sessions = (1..3).map { nativeSession("live-drop-$it") }
        val program = nativeProgram(sessions)
        val logs = (1..2).map { number ->
            workoutLog(program.id, sessions[number - 1], "live-drop-log-$number", "w$number", dropSetIndices = setOf(1))
        }

        val observed = observe(program, logs)

        assertTrue(observed.nativeProgressionProposals.isEmpty())
        assertTrue(observed.nativeProgressionAudit.isEmpty())
    }

    @Test
    fun timedIdentity_isNeverProgressedByTheRepRangeLoadRule() {
        val sessions = (1..3).map { nativeSession("timed-$it", unitMode = UnitModeV2.TIME) }
        val program = nativeProgram(sessions)
        val logs = (1..2).map { number ->
            workoutLog(program.id, sessions[number - 1], "timed-log-$number", "w$number", reps = 0, unitMode = UnitModeV2.TIME)
        }

        val observed = observe(program, logs)

        assertTrue("reps = 0 contra un rango de reps no puede producir «bajo el mínimo»", observed.nativeProgressionProposals.isEmpty())
        assertTrue(observed.nativeProgressionAudit.isEmpty())
    }

    @Test
    fun recipeFallbackCountsOnlyWorkSets_notTheWarmupThatHeadsTheSlot() {
        val sessions = (1..3).map { nativeSession("fallback-$it", plannedRange = null, plannedRir = null) }
        val program = nativeProgram(sessions, sourceRecipe = nativeRecipe(withWarmup = true))
        val logs = (1..2).map { number -> workoutLog(program.id, sessions[number - 1], "fallback-log-$number", "w$number") }

        val proposal = observe(program, logs).nativeProgressionProposals.single()

        assertEquals(NativeProgressionProposalKind.INCREASE_LOAD, proposal.kind)
    }

    // ─── §12.4 estrategia por identidad en recetas mixtas (P5 / F-06) ──────────

    @Test
    fun bodyweightSlotInMixedRecipe_usesVariantEscalation() {
        val sessions = (1..3).map {
            nativeSession("mixed-bw-$it", configurationId = "knee_push_up__default", loadMode = LoadModeV2.BODYWEIGHT)
        }
        // La receta es de CARGA (no corporal pura): el slot corporal debe progresar igual.
        val program = nativeProgram(
            sessions,
            sourceRecipe = nativeRecipe(
                strategy = NativeProgressionStrategy.REP_RANGE_THEN_LOAD,
                configurationId = "knee_push_up__default",
            ),
        )
        val logs = (1..2).map { number ->
            workoutLog(program.id, sessions[number - 1], "mixed-bw-log-$number", "w$number", loadMode = LoadModeV2.BODYWEIGHT)
        }

        val proposed = observe(program, logs, curated = setOf("push_up__flat"))
        val proposal = proposed.nativeProgressionProposals.single()
        assertEquals(NativeProgressionProposalKind.HARDER_BODYWEIGHT_VARIANT, proposal.kind)
        assertEquals("push_up__flat", proposal.targetConfigurationId)

        val applied = NativeWorkoutProgressionRuntime.resolveProposal(
            program = proposed,
            proposalId = proposal.proposalId,
            accept = true,
            logs = logs,
            ongoingSessionIds = emptySet(),
            curatedExercises = mapOf("push_up__flat" to pushUpInfo()),
            nowMs = 101L,
        )
        val future = ProgramHierarchyIndex(applied).locateSession(sessions[2].id)!!.session.allExercises().single()
        assertEquals("push_up__flat", future.catalogConfigurationId)
        assertEquals(NativeProgressionResolutionStatus.APPLIED, applied.nativeProgressionAudit.single().status)
    }

    @Test
    fun bodyweightSlotWithoutHarderCuratedVariant_inMixedRecipe_explainsTheLimit() {
        val configurationId = "calf_raise__bilateral__bodyweight"
        val sessions = (1..3).map {
            nativeSession("mixed-calf-$it", configurationId = configurationId, loadMode = LoadModeV2.BODYWEIGHT)
        }
        val program = nativeProgram(
            sessions,
            sourceRecipe = nativeRecipe(strategy = NativeProgressionStrategy.REP_RANGE_THEN_LOAD, configurationId = configurationId),
        )
        val logs = (1..2).map { number ->
            workoutLog(program.id, sessions[number - 1], "mixed-calf-log-$number", "w$number", loadMode = LoadModeV2.BODYWEIGHT)
        }

        val observed = observe(program, logs, curated = setOf("push_up__flat"))

        assertTrue(observed.nativeProgressionProposals.isEmpty())
        val notice = observed.nativeProgressionAudit.single()
        assertEquals(NativeProgressionResolutionStatus.EXPIRED, notice.status)
        assertTrue(notice.userFacingNotice)
        assertTrue(notice.reason.contains("No hay una variante corporal más difícil"))
    }

    // ─── H-UI: textos de las propuestas en español llano ─────────────────────────

    @Test
    fun proposalText_isPlainSpanishWithTheEvidenceAndTheLoads() {
        val sessions = (1..3).map { nativeSession("plain$it") }
        val program = nativeProgram(sessions)
        val logs = sessions.take(2).mapIndexed { index, session ->
            workoutLog(program.id, session, "plain-${index + 1}", "w${index + 1}")
        }

        val known = NativeWorkoutProgressionRuntime.observeCompletedWorkout(
            program = program,
            logs = logs,
            inventory = EquipmentInventory(plates = listOf(PlateStock(weightKg = 0.5, countPerSide = 2))),
            curatedConfigurations = emptySet(),
            nowMs = 4L,
            equipmentIdOf = { "barbell" },
        ).nativeProgressionProposals.single()
        assertEquals(
            "Lo hiciste dos veces con 8 reps en todas las series. Propuesta: subir de 20 a 21 kg en total.",
            known.explanation,
        )

        val unknown = NativeWorkoutProgressionRuntime.observeCompletedWorkout(
            program = program,
            logs = logs,
            inventory = null,
            curatedConfigurations = emptySet(),
            nowMs = 5L,
            equipmentIdOf = { "barbell" },
        ).nativeProgressionProposals.single()
        assertEquals(
            "Lo hiciste dos veces con 8 reps en todas las series. Propuesta: en el próximo entrenamiento " +
                "elige una carga un poco mayor y deja las mismas repeticiones en reserva.",
            unknown.explanation,
        )
        listOf(known.explanation, unknown.explanation).forEach { text ->
            assertTrue(text, listOf("RIR", "exposicion", "bench_press", "__").none { text.contains(it, ignoreCase = true) })
        }
    }

    @Test
    fun reductionText_explainsTheShortfallAndTheLighterLoad() {
        val sessions = (1..3).map { nativeSession("lighter$it", plannedWeight = 22.0) }
        val program = nativeProgram(sessions)
        val logs = sessions.take(2).mapIndexed { index, session ->
            workoutLog(program.id, session, "lighter-${index + 1}", "w${index + 1}", loadKg = 22.0, reps = 5)
        }

        val proposal = NativeWorkoutProgressionRuntime.observeCompletedWorkout(
            program = program,
            logs = logs,
            inventory = EquipmentInventory(barbellWeightKg = 20.0, plates = listOf(PlateStock(weightKg = 1.0, countPerSide = 2))),
            curatedConfigurations = emptySet(),
            nowMs = 6L,
            equipmentIdOf = { "barbell" },
        ).nativeProgressionProposals.single()

        assertEquals(NativeProgressionProposalKind.REDUCE_LOAD, proposal.kind)
        assertEquals(20.0, proposal.targetLoadKg!!, 0.0001)
        assertEquals(
            "Las últimas dos veces no llegaste a las 6 reps mínimas o terminaste con menos repeticiones en " +
                "reserva de las previstas. Propuesta: bajar de 22 a 20 kg en total.",
            proposal.explanation,
        )
    }

    @Test
    fun bodyweightVariantText_namesTheProposedVariant() {
        val sessions = (1..3).map {
            nativeSession("text-bw-$it", configurationId = "knee_push_up__default", loadMode = LoadModeV2.BODYWEIGHT)
        }
        val program = nativeProgram(
            sessions,
            sourceRecipe = nativeRecipe(
                strategy = NativeProgressionStrategy.REP_RANGE_THEN_LOAD,
                configurationId = "knee_push_up__default",
            ),
        )
        val logs = (1..2).map { number ->
            workoutLog(program.id, sessions[number - 1], "text-bw-log-$number", "w$number", loadMode = LoadModeV2.BODYWEIGHT)
        }

        val proposal = NativeWorkoutProgressionRuntime.observeCompletedWorkout(
            program = program,
            logs = logs,
            inventory = null,
            curatedConfigurations = setOf("push_up__flat"),
            nowMs = 100L,
            displayNameOf = { configurationId -> if (configurationId == "push_up__flat") "Flexión" else null },
        ).nativeProgressionProposals.single()

        assertEquals(NativeProgressionProposalKind.HARDER_BODYWEIGHT_VARIANT, proposal.kind)
        assertEquals(
            "Lo hiciste dos veces con 8 reps en todas las series. Propuesta: pasar a «Flexión», una variante más difícil.",
            proposal.explanation,
        )
    }

    @Test
    fun resolutionReasons_shownToTheAthleteAreFreeOfInternalJargon() {
        val sessions = (1..3).map { nativeSession("reasons$it") }
        val program = nativeProgram(sessions)
        val logs = sessions.take(2).mapIndexed { index, session ->
            workoutLog(program.id, session, "reasons-${index + 1}", "w${index + 1}")
        }
        val proposal = observe(program, logs).nativeProgressionProposals.single()
        // Sin la receta original, la propuesta caduca: el motivo es lo que verá el atleta.
        val withoutRecipe = observe(program, logs).copy(sourceRecipe = null)

        val expired = NativeWorkoutProgressionRuntime.resolveProposal(
            program = withoutRecipe,
            proposalId = proposal.proposalId,
            accept = true,
            logs = logs,
            ongoingSessionIds = emptySet(),
            curatedExercises = emptyMap(),
            nowMs = 7L,
        ).nativeProgressionAudit.single()

        assertEquals(NativeProgressionResolutionStatus.EXPIRED, expired.status)
        assertTrue(expired.userFacingNotice)
        assertEquals("El plan cambió desde que se hizo la propuesta; no se modificó nada.", expired.reason)
        assertTrue(expired.reason, listOf("slot", "identidad", "curada", "propósito").none { expired.reason.contains(it, ignoreCase = true) })
    }

    // ─── H-IDENT: identidad por ejercicio, sin día ni posición ───────────────────

    private val plates = EquipmentInventory(plates = listOf(PlateStock(weightKg = 0.5, countPerSide = 2)))

    private fun observeBarbell(program: Program, logs: List<WorkoutLog>, inventory: EquipmentInventory? = plates): Program =
        NativeWorkoutProgressionRuntime.observeCompletedWorkout(
            program = program,
            logs = logs,
            inventory = inventory,
            curatedConfigurations = emptySet(),
            nowMs = 100L,
            equipmentIdOf = { "barbell" },
        )

    private fun weightsOf(program: Program, session: Session): List<Double?> =
        ProgramHierarchyIndex(program).locateSession(session.id)!!.session.allExercises().first().sets.map { it.weight }

    @Test
    fun sameExerciseOnTwoDaysSharesOneProgression_andRaisesTogether() {
        val sessions = listOf(
            nativeSession("two-days-1", dayNumber = 1),
            nativeSession("two-days-2", dayNumber = 2),
            nativeSession("two-days-3", dayNumber = 1),
            nativeSession("two-days-4", dayNumber = 2),
        )
        val program = nativeProgram(sessions, sourceRecipe = nativeRecipe(extraDays = 1))
        val logs = listOf(
            workoutLog(program.id, sessions[0], "two-days-log-1", "w1"),
            workoutLog(program.id, sessions[1], "two-days-log-2", "w2"),
        )

        val observed = observeBarbell(program, logs)

        val proposal = observed.nativeProgressionProposals.single()
        assertEquals("un solo ejercicio, una sola propuesta aunque venga de dos días", listOf("two-days-log-1", "two-days-log-2"), proposal.sourceLogIds)
        assertEquals(21.0, proposal.targetLoadKg!!, 0.0001)

        val accepted = NativeWorkoutProgressionRuntime.resolveProposal(
            program = observed,
            proposalId = proposal.proposalId,
            accept = true,
            logs = logs,
            ongoingSessionIds = emptySet(),
            curatedExercises = emptyMap(),
            nowMs = 101L,
        )
        assertEquals("el día 1 futuro sube", listOf(21.0, 21.0), weightsOf(accepted, sessions[2]))
        assertEquals("el día 2 futuro sube con la misma carga", listOf(21.0, 21.0), weightsOf(accepted, sessions[3]))
        assertEquals("lo ya entrenado no cambia", listOf(20.0, 20.0), weightsOf(accepted, sessions[0]))
        assertEquals(listOf(20.0, 20.0), weightsOf(accepted, sessions[1]))
    }

    @Test
    fun firstManualLoadChosenOnOneDayIsKeptForTheSameExerciseOnTheOtherDays() {
        val sessions = listOf(
            nativeSession("manual-1", dayNumber = 1, plannedWeight = null),
            nativeSession("manual-2", dayNumber = 2, plannedWeight = null),
            nativeSession("manual-3", dayNumber = 1, plannedWeight = null),
        )
        val program = nativeProgram(sessions, sourceRecipe = nativeRecipe(extraDays = 1))
        val log = workoutLog(program.id, sessions[0], "manual-log-1", "w1", loadKg = 24.0)

        val observed = NativeWorkoutProgressionRuntime.observeCompletedWorkout(
            program = program, logs = listOf(log), inventory = null, curatedConfigurations = emptySet(),
            completedLogId = log.id, nowMs = 100L, equipmentIdOf = { "barbell" },
        )

        assertEquals("el día 2 ya no vuelve a pedir «Elige una carga»", listOf(24.0, 24.0), weightsOf(observed, sessions[1]))
        assertEquals(listOf(24.0, 24.0), weightsOf(observed, sessions[2]))
    }

    @Test
    fun twoSlotsOfTheSameExerciseInOneWorkoutAreOneExposure() {
        val sessions = (1..3).map { nativeSession("dup-$it", withDuplicateSlot = true) }
        val program = nativeProgram(sessions, sourceRecipe = nativeRecipe(duplicateSlotInDay1 = true))
        val first = workoutLog(program.id, sessions[0], "dup-log-1", "w1")

        val afterOne = observeBarbell(program, listOf(first))
        assertTrue("dos slots en un entreno no son «dos exposiciones»", afterOne.nativeProgressionProposals.isEmpty())

        val second = workoutLog(program.id, sessions[1], "dup-log-2", "w2")
        val afterTwo = observeBarbell(program, listOf(first, second))
        assertEquals(listOf("dup-log-1", "dup-log-2"), afterTwo.nativeProgressionProposals.single().sourceLogIds)
    }

    @Test
    fun aSlotWithAnotherPurposeNeverReceivesTheProposal() {
        val sessions = (1..3).map { nativeSession("purpose-$it", withDuplicateSlot = true) }
        val program = nativeProgram(
            sessions,
            sourceRecipe = nativeRecipe(duplicateSlotInDay1 = true, duplicateSlotIntent = SlotIntent.P),
        )
        val logs = (1..2).map { workoutLog(program.id, sessions[it - 1], "purpose-log-$it", "w$it") }

        val observed = observeBarbell(program, logs)
        val proposal = observed.nativeProgressionProposals.single()
        val accepted = NativeWorkoutProgressionRuntime.resolveProposal(
            program = observed, proposalId = proposal.proposalId, accept = true, logs = logs,
            ongoingSessionIds = emptySet(), curatedExercises = emptyMap(), nowMs = 102L,
        )

        val future = ProgramHierarchyIndex(accepted).locateSession(sessions[2].id)!!.session.allExercises()
        assertEquals(listOf(21.0, 21.0), future.first { it.recipeSlotId == "slot-1" }.sets.map { it.weight })
        assertEquals("el slot de otro propósito conserva su carga", listOf(20.0, 20.0), future.first { it.recipeSlotId == "slot-dup" }.sets.map { it.weight })
    }

    // ─── H-DESCARGA: una subida aceptada nunca cae en la semana de descarga ──────

    @Test
    fun acceptedIncrease_skipsTheDeloadWeekAndLandsOnTheNextTrainingWeek() {
        val sessions = (1..4).map { nativeSession("deload-target-$it") }
        val program = nativeProgram(sessions, deloadWeekIndices = setOf(2))
        val logs = (1..2).map { workoutLog(program.id, sessions[it - 1], "deload-target-log-$it", "w$it") }

        val observed = observeBarbell(program, logs)
        val proposal = observed.nativeProgressionProposals.single()
        val accepted = NativeWorkoutProgressionRuntime.resolveProposal(
            program = observed, proposalId = proposal.proposalId, accept = true, logs = logs,
            ongoingSessionIds = emptySet(), curatedExercises = emptyMap(), nowMs = 103L,
        )

        assertEquals("la semana de descarga conserva su carga", listOf(20.0, 20.0), weightsOf(accepted, sessions[2]))
        assertEquals("la subida cae en la siguiente semana de entrenamiento", listOf(21.0, 21.0), weightsOf(accepted, sessions[3]))
    }

    @Test
    fun ifOnlyTheDeloadWeekIsLeft_noProposalIsMadeAndTheAthleteIsToldWhy() {
        val sessions = (1..3).map { nativeSession("deload-only-$it") }
        val program = nativeProgram(sessions, deloadWeekIndices = setOf(2))
        val logs = (1..2).map { workoutLog(program.id, sessions[it - 1], "deload-only-log-$it", "w$it") }

        val observed = observeBarbell(program, logs)

        assertTrue(observed.nativeProgressionProposals.isEmpty())
        val notice = observed.nativeProgressionAudit.single()
        assertTrue(notice.userFacingNotice)
        assertEquals(NativeProgressionText.noFutureSessionNotice(), notice.reason)
    }

    // ─── H-VERIF: una reserva que el atleta no movió no es «reserva cumplida» ────

    @Test
    fun untouchedReserveDoesNotCountAsMetReserve_butAdjustedOneStillDoes() {
        val sessions = (1..3).map { nativeSession("untouched-$it") }
        val program = nativeProgram(sessions)

        val untouched = (1..2).map { workoutLog(program.id, sessions[it - 1], "untouched-log-$it", "w$it", rirAdjusted = false) }
        val noProposal = observeBarbell(program, untouched)
        assertTrue("sin mover la reserva no hay subida", noProposal.nativeProgressionProposals.isEmpty())
        assertTrue(noProposal.nativeProgressionAudit.isEmpty())

        val adjusted = (1..2).map { workoutLog(program.id, sessions[it - 1], "adjusted-log-$it", "w$it") }
        assertEquals(1, observeBarbell(program, adjusted).nativeProgressionProposals.size)
    }

    @Test
    fun untouchedReserveStillLetsAShortfallProposeALighterLoad() {
        val sessions = (1..3).map { nativeSession("short-$it", plannedWeight = 22.0) }
        val program = nativeProgram(sessions)
        val logs = (1..2).map {
            workoutLog(program.id, sessions[it - 1], "short-log-$it", "w$it", loadKg = 22.0, reps = 5, rirAdjusted = false)
        }

        val proposal = observeBarbell(
            program, logs, EquipmentInventory(barbellWeightKg = 20.0, plates = listOf(PlateStock(weightKg = 1.0, countPerSide = 2))),
        ).nativeProgressionProposals.single()

        assertEquals(NativeProgressionProposalKind.REDUCE_LOAD, proposal.kind)
    }

    // ─── H-BW: escalera de la flexión con comprobación de apoyo ──────────────────

    private fun flatPushUpScenario(): Triple<Program, List<WorkoutLog>, List<Session>> {
        val sessions = (1..3).map {
            nativeSession("flat-$it", configurationId = "push_up__flat", loadMode = LoadModeV2.BODYWEIGHT)
        }
        val program = nativeProgram(
            sessions,
            sourceRecipe = nativeRecipe(
                strategy = NativeProgressionStrategy.REP_RANGE_THEN_LOAD,
                configurationId = "push_up__flat",
            ),
        )
        val logs = (1..2).map { workoutLog(program.id, sessions[it - 1], "flat-log-$it", "w$it", loadMode = LoadModeV2.BODYWEIGHT) }
        return Triple(program, logs, sessions)
    }

    @Test
    fun standardPushUpMovesToFeetElevatedOnlyWithAStableSupport() {
        val (program, logs, _) = flatPushUpScenario()
        val curated = setOf("push_up__feet_elevated")

        val withoutSupport = NativeWorkoutProgressionRuntime.observeCompletedWorkout(
            program, logs, inventory = null, curatedConfigurations = curated, nowMs = 100L,
        )
        assertTrue("sin apoyo declarado no se propone la variante", withoutSupport.nativeProgressionProposals.isEmpty())
        val notice = withoutSupport.nativeProgressionAudit.single()
        assertTrue(notice.userFacingNotice)
        assertTrue(notice.reason, notice.reason.contains("apoyo estable"))

        val withSupport = NativeWorkoutProgressionRuntime.observeCompletedWorkout(
            program, logs, inventory = EquipmentInventory(supportEquipment = setOf("support")),
            curatedConfigurations = curated, nowMs = 100L,
        )
        val proposal = withSupport.nativeProgressionProposals.single()
        assertEquals(NativeProgressionProposalKind.HARDER_BODYWEIGHT_VARIANT, proposal.kind)
        assertEquals("push_up__feet_elevated", proposal.targetConfigurationId)
    }

    @Test
    fun feetElevatedPushUpCanStepBackToTheStandardOne() {
        val sessions = (1..3).map {
            nativeSession("feet-$it", configurationId = "push_up__feet_elevated", loadMode = LoadModeV2.BODYWEIGHT)
        }
        val program = nativeProgram(
            sessions,
            sourceRecipe = nativeRecipe(
                strategy = NativeProgressionStrategy.REP_RANGE_THEN_LOAD,
                configurationId = "push_up__feet_elevated",
            ),
        )
        val logs = (1..2).map {
            workoutLog(program.id, sessions[it - 1], "feet-log-$it", "w$it", loadMode = LoadModeV2.BODYWEIGHT, reps = 3)
        }

        val proposal = NativeWorkoutProgressionRuntime.observeCompletedWorkout(
            program, logs, inventory = null, curatedConfigurations = setOf("push_up__flat"), nowMs = 100L,
        ).nativeProgressionProposals.single()

        assertEquals(NativeProgressionProposalKind.EASIER_BODYWEIGHT_VARIANT, proposal.kind)
        assertEquals("push_up__flat", proposal.targetConfigurationId)
    }

    // ─── Ciclos: «ya entrenada» es el par (ciclo, sesión) (P2 / F-02) ───────────

    @Test
    fun logCycleReadsTheCycleScopedInstanceBecauseComplexPlansStampCycleOne() {
        val session = nativeSession("cycle-source")
        val program = nativeProgram(listOf(session))
        fun log(weekInstanceId: String?, cycleNumber: Int?) = workoutLog(
            program.id, session, "cycle-log-1", "w1", weekInstanceId = weekInstanceId, cycleNumber = cycleNumber,
        )

        assertEquals(2, NativeWorkoutProgressionRuntime.logCycle(log("inst_c2_w1", cycleNumber = 1)))
        assertEquals(3, NativeWorkoutProgressionRuntime.logCycle(log("w1", cycleNumber = 3)))
        assertEquals(1, NativeWorkoutProgressionRuntime.logCycle(log("w1", cycleNumber = null)))
    }

    @Test
    fun sessionsTrainedInAnEarlierCycleAreEligibleAgainInTheCurrentCycle() {
        val sessions = (1..5).map { nativeSession("recycle-$it") }
        val cycleTwoProgram = nativeProgram(sessions).let { base ->
            base.copy(runState = ProgramRunState(runId = "recycle-run", cycleNumber = 2, weekInstanceId = "inst_c2_w1", weekId = "w1"))
        }
        // Todo el ciclo 1 ya se entrenó con los mismos ids de sesión.
        val cycleOne = sessions.mapIndexed { index, session ->
            workoutLog(
                cycleTwoProgram.id, session, "recycle-c1-${index + 1}", "w${index + 1}",
                weekInstanceId = "inst_c1_w${index + 1}", cycleNumber = 1,
            )
        }
        val cycleTwo = (0..1).map { index ->
            workoutLog(
                cycleTwoProgram.id, sessions[index], "recycle-c2-${index + 10}", "w${index + 1}",
                weekInstanceId = "inst_c2_w${index + 1}", cycleNumber = 1,
            )
        }

        val observed = observe(cycleTwoProgram, cycleOne + cycleTwo)

        val proposal = observed.nativeProgressionProposals.single()
        assertEquals(listOf("recycle-c2-10", "recycle-c2-11"), proposal.sourceLogIds)
        assertTrue("las sesiones 3..5 del ciclo 2 siguen sin entrenar: no hay aviso de caducidad", observed.nativeProgressionAudit.isEmpty())

        val accepted = NativeWorkoutProgressionRuntime.resolveProposal(
            program = observed,
            proposalId = proposal.proposalId,
            accept = true,
            logs = cycleOne + cycleTwo,
            ongoingSessionIds = emptySet(),
            curatedExercises = emptyMap(),
            nowMs = 103L,
        )
        assertEquals(NativeProgressionResolutionStatus.APPLIED, accepted.nativeProgressionAudit.single().status)
        val weights = sessions.map { session ->
            ProgramHierarchyIndex(accepted).locateSession(session.id)!!.session.allExercises().single().sets.first().weight
        }
        assertEquals("sesiones del ciclo 2 ya entrenadas conservan su carga", listOf(20.0, 20.0), weights.take(2))
        assertEquals("sesiones 3..5 del ciclo 2 reciben la propuesta (carga por elegir)", listOf(null, null, null), weights.drop(2))
    }

    private fun observe(
        program: Program,
        logs: List<WorkoutLog>,
        completedLogId: String? = null,
        inventory: EquipmentInventory? = null,
        curated: Set<String> = emptySet(),
    ): Program = NativeWorkoutProgressionRuntime.observeCompletedWorkout(
        program = program,
        logs = logs,
        inventory = inventory,
        curatedConfigurations = curated,
        completedLogId = completedLogId,
        nowMs = 100L,
    )

    private fun pushUpInfo() = ExerciseMuscleInfo(
        id = "push_up",
        name = "Flexión",
        equipment = "peso corporal",
        catalogDefinitionId = "push_up",
        catalogConfigurationId = "push_up__flat",
        catalogRevision = "catalog-test",
        performanceProfileId = "profile-test",
    )

    private fun nativeRecipe(
        strategy: NativeProgressionStrategy? = NativeProgressionStrategy.REP_RANGE_THEN_LOAD,
        configurationId: String = "bench_press__barbell",
        withWarmup: Boolean = false,
        /** H-IDENT: días extra (`day-2`, `day-3`…) con el MISMO ejercicio en su propio slot (`slot-2`…). */
        extraDays: Int = 0,
        /** H-IDENT: un segundo slot del mismo ejercicio en `day-1` (`slot-dup`). */
        duplicateSlotInDay1: Boolean = false,
        /** Intención del slot SPEED/otras de `slot-dup` si se quiere distinta de H. */
        duplicateSlotIntent: SlotIntent = SlotIntent.H,
    ): TrainingPlanRecipe = TrainingPlanRecipe(
        id = "native-test-recipe",
        weeks = listOf(
            WeekRecipe(
                weekNumber = 1,
                blockIndex = 0,
                days = (1..(1 + extraDays)).map { dayNumber ->
                    fun slot(slotId: String, intent: SlotIntent) = SlotRecipe(
                        id = slotId,
                        role = SlotRole.T2_SUPPLEMENTAL,
                        lift = LiftRef(configurationId = configurationId),
                        // Los calentamientos encabezan `slot.sets`; el runtime debe contar solo el trabajo.
                        sets = listOfNotNull(
                            SetRecipe(reps = 5, isWarmup = true, loadBasis = LoadBasis.RPE).takeIf { withWarmup },
                        ) + listOf(
                            SetRecipe(repsMin = 6, repsMax = 8, rir = 2),
                            SetRecipe(repsMin = 6, repsMax = 8, rir = 2),
                        ),
                        restSeconds = 120,
                        intent = intent,
                    )
                    DayRecipe(
                        id = "day-$dayNumber",
                        label = "Upper $dayNumber",
                        slots = listOfNotNull(
                            slot("slot-$dayNumber", SlotIntent.H),
                            slot("slot-dup", duplicateSlotIntent).takeIf { duplicateSlotInDay1 && dayNumber == 1 },
                        ),
                    )
                },
            ),
        ),
        progression = ProgressionRule.CycleIncrement(upperKg = 2.5, lowerKg = 1.25),
        nativeProgression = strategy?.let { NativeProgressionSpec(strategy = it, exposuresBeforeProposal = 2) },
    )

    private fun nativeProgram(
        sessions: List<Session>,
        sourceRecipe: TrainingPlanRecipe = nativeRecipe(),
        exerciseLoadReferences: List<ExerciseLoadReference> = emptyList(),
        deloadWeekIndices: Set<Int> = emptySet(),
    ): Program {
        val weeks = sessions.mapIndexed { index, session ->
            ProgramWeek(
                id = "w${index + 1}",
                name = "Week ${index + 1}",
                sessions = listOf(session),
                executionKind = if (index in deloadWeekIndices) WeekExecutionKind.DELOAD else WeekExecutionKind.TRAINING,
            )
        }
        return Program(
            id = "runtime-program",
            name = "Native test",
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
            sourceRecipe = sourceRecipe,
            exerciseLoadReferences = exerciseLoadReferences,
        )
    }

    private fun nativeSession(
        id: String,
        configurationId: String = "bench_press__barbell",
        loadMode: LoadModeV2 = LoadModeV2.LOAD,
        quantityConvention: LoadQuantityConvention = LoadQuantityConvention.TOTAL_EXTERNAL,
        persistedLoadMode: LoadModeV2? = loadMode,
        plannedWeight: Double? = 20.0,
        unitMode: UnitModeV2 = UnitModeV2.REPS,
        plannedRange: RepRange? = RepRange(6, 8),
        plannedRir: Int? = 2,
        /** H-IDENT: a qué día de la receta pertenece (`day-N` / `slot-N`). */
        dayNumber: Int = 1,
        /** H-IDENT: añade un segundo ejercicio igual en el slot `slot-dup` del mismo día. */
        withDuplicateSlot: Boolean = false,
    ): Session {
        fun exerciseFor(exerciseId: String, slotId: String): Exercise {
            val sets = (0..1).map { index ->
                ExerciseSet(
                    id = "$exerciseId-set-$index",
                    targetRepsRange = plannedRange,
                    targetRIR = plannedRir,
                    weight = if (loadMode == LoadModeV2.BODYWEIGHT) null else plannedWeight,
                    loadModeV2 = persistedLoadMode,
                    unitModeV2 = unitMode,
                    loadQuantityConvention = quantityConvention,
                )
            }
            return Exercise(
                id = exerciseId,
                name = "Press",
                sets = sets,
                trainingMode = TrainingMode.REPS,
                catalogRevision = "catalog-test",
                catalogDefinitionId = configurationId.substringBefore("__"),
                catalogConfigurationId = configurationId,
                performanceProfileId = "profile-test",
                selectedExecutionOption = "standard",
                loadQuantityConvention = quantityConvention,
                recipeDayId = "day-$dayNumber",
                recipeSlotId = slotId,
                nativeProgressionManaged = true,
            )
        }
        return Session(
            id = id,
            name = "Workout $id",
            exercises = listOfNotNull(
                exerciseFor("$id-exercise", "slot-$dayNumber"),
                exerciseFor("$id-exercise-dup", "slot-dup").takeIf { withDuplicateSlot },
            ),
        )
    }

    private fun workoutLog(
        programId: String,
        session: Session,
        logId: String,
        weekId: String,
        loadMode: LoadModeV2 = LoadModeV2.LOAD,
        loadKg: Double = 20.0,
        reps: Int = 8,
        executionError: Boolean = false,
        programRunId: String? = null,
        cycleNumber: Int? = null,
        /** Carga por serie (sobrescribe [loadKg]); p. ej. una bajada en la última serie. */
        setLoads: List<Double>? = null,
        /** Series registradas al fallo (sin RIR; modo FAILURE). */
        failureSets: Set<Int> = emptySet(),
        /** Series con un drop set vivo. */
        dropSetIndices: Set<Int> = emptySet(),
        unitMode: UnitModeV2 = UnitModeV2.REPS,
        weekInstanceId: String? = null,
        /** False: el atleta no movió el selector de reserva (llegó relleno con lo planificado). */
        rirAdjusted: Boolean = true,
    ): WorkoutLog {
        fun completedFor(planned: Exercise): CompletedExercise {
        val sets = planned.sets.mapIndexed { index, _ ->
            val load = setLoads?.getOrNull(index) ?: loadKg
            val failed = index in failureSets
            CompletedSet(
                id = "$logId-${planned.id}-set-$index",
                weight = if (loadMode == LoadModeV2.BODYWEIGHT) 0.0 else load,
                reps = reps,
                rir = if (failed) null else 2,
                isFailure = failed,
                dropSets = if (index in dropSetIndices) listOf(DropSetData(weight = load * 0.8, reps = 6)) else emptyList(),
                recordedPayloadV3 = RecordedSetPayload(
                    exerciseId = planned.id,
                    side = "bilateral",
                    loadInputMode = loadMode,
                    unitMode = unitMode,
                    externalLoad = load.takeIf { loadMode == LoadModeV2.LOAD || loadMode == LoadModeV2.LASTRE },
                    assistedLoad = load.takeIf { loadMode == LoadModeV2.ASSISTED },
                    completedReps = reps,
                    actualIntensityMode = if (failed) IntensityMode.FAILURE else IntensityMode.RIR,
                    actualIntensityValue = if (failed) 10.0 else 2.0,
                    reachedFailure = failed,
                    executionError = executionError,
                    intensityAdjusted = rirAdjusted,
                ),
            )
        }
        return CompletedExercise(
            exerciseId = planned.id,
            exerciseName = planned.name,
            catalogConfigurationId = planned.catalogConfigurationId,
            sets = sets,
        )
        }
        return WorkoutLog(
            id = logId,
            programId = programId,
            sessionId = session.id,
            sessionName = session.name,
            // Fechas distintas y crecientes por log (sufijo numérico del id): el orden de las
            // exposiciones no depende del desempate por id (F-14).
            date = dateForLog(logId),
            durationMinutes = 45,
            completedExercises = session.allExercises().map(::completedFor),
            weekId = weekId,
            weekInstanceId = weekInstanceId ?: weekId,
            programRunId = programRunId,
            cycleNumber = cycleNumber,
        )
    }

    /** `…-7` → 7.º día de septiembre de 2026; ids sin sufijo numérico caen al día 1. */
    private fun dateForLog(logId: String): String {
        val ordinal = Regex("(\\d+)$").find(logId)?.value?.toIntOrNull() ?: 0
        return java.time.LocalDate.of(2026, 9, 1).plusDays(ordinal.toLong()).toString() + "T10:00:00Z"
    }

    private fun futureSetReferenceState(program: Program, sessionId: String): PlanLoadReferenceState? =
        ProgramHierarchyIndex(program).locateSession(sessionId)?.session?.allExercises()?.single()?.loadReference?.state
}

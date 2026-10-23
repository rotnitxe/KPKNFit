package com.example.kpkn.screens.workout

import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.models.*
import com.example.kpkn.data.protocols.*
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.training.ExerciseTrainingMax
import com.example.kpkn.domain.training.Cal5TestSupport
import com.example.kpkn.testsupport.RoomTestRule
import com.example.kpkn.testsupport.awaitRepositoryScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Twelve complete method/week journeys. Real recorder and finish commit are the only source of workout logs. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class Cal7ManagedProgramRoomJourneyTest {
    @get:Rule val room = RoomTestRule()
    private var fixture: Cal7ManagedRoomFixture? = null
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()); LiveSessionVmFixture.resetAugeRepository() }
    @After fun cleanup() {
        try {
            runBlocking {
                try { fixture?.close() }
                finally {
                    try { fixture?.preparedRepository?.let { awaitRepositoryScope(it) } }
                    finally {
                        try { LiveSessionVmFixture.resetAugeRepository() }
                        finally {
                            try { ProgramRepository.closeInstance() }
                            finally { KpknDatabase.closeInstance() }
                        }
                    }
                }
            }
        } finally { Dispatchers.resetMain() }
    }
    private val configs = mapOf(LiftSlot.SQUAT to CatalogIds.SQ_LOW, LiftSlot.BENCH to CatalogIds.BP,
        LiftSlot.DEADLIFT to CatalogIds.DL, LiftSlot.OVERHEAD to CatalogIds.OHP)
    private fun tms(profile: PowerliftingProfile) = mapOf(LiftSlot.SQUAT to profile.squatTM!!,
        LiftSlot.BENCH to profile.benchTM!!, LiftSlot.DEADLIFT to profile.deadliftTM!!, LiftSlot.OVERHEAD to profile.overheadTM!!)
    private fun oneRms(profile: PowerliftingProfile) = listOf(profile.squat1RM, profile.bench1RM, profile.deadlift1RM, profile.overhead1RM)
    private fun findSession(program: Program, id: String) = Cal5TestSupport.weeksOf(program).flatMap { it.sessions }.single { it.id == id }
    private fun assertTms(expected: Map<LiftSlot, Double>, program: Program) {
        expected.forEach { (lift, kg) -> assertEquals("$lift TM", kg, tms(program.powerliftingProfile!!).getValue(lift), 1e-8) }
    }
    private fun assertProtection(f: Cal7ManagedRoomFixture, foreign: List<ExerciseLoadReference>, logs: List<WorkoutLog>) {
        assertEquals("A manually customized session remains authoritative", f.manualSession, findSession(f.program, f.manualSession.id))
        assertEquals(f.explicitManualKg, findSession(f.program, f.manualSession.id).allExercises()
            .single { it.id == f.manualExerciseId }.sets.first().weight!!, 0.0)
        assertEquals("A different press configuration is never updated by LiftSlot", foreign,
            f.program.exerciseLoadReferences.filter { it.exerciseId == f.foreignExerciseId })
        logs.forEach { before ->
            val durable = f.repository.history.value.single { it.id == before.id }
            assertEquals("An already performed log remains unchanged after progression", before, durable)
        }
    }
    private suspend fun verifyNextSession(f: Cal7ManagedRoomFixture, weekId: String, expectedTm: Double) {
        val week = f.weeks.single { it.id == weekId }
        val session = week.sessions.first { candidate -> candidate.allExercises().any {
            it.slotRole == SlotRole.T1_MAIN && it.catalogConfigurationId == CatalogIds.OHP } }
        val vm = f.open(session)
        val exercises = vm.uiState.value.session!!.allExercises()
        val index = exercises.indexOfFirst { it.slotRole == SlotRole.T1_MAIN && it.catalogConfigurationId == CatalogIds.OHP }
        val exercise = exercises[index]
        assertEquals(expectedTm, exercise.referenceTrainingMax!!, 1e-8)
        val set = exercise.sets.first()
        assertEquals(LoadBasis.PERCENT_TM, set.loadBasis)
        val expectedKg = expectedTm * set.targetPercentageRM!! / 100.0
        assertEquals("The next real session uses the new TM rather than an old pool snapshot", expectedKg, set.weight!!, 1e-8)
        vm.selectExercise(index)
        vm.jumpToSet(0)
        val result = vm.recordSetV2(expectedKg, (set.targetReps ?: 5).toDouble(), 1.0, SetAdvancedFeedback(rir = 1),
            LoadModeV2.LOAD, UnitModeV2.REPS, 75.0, setIdxOverride = 0,
            expectedExerciseId = exercise.id, expectedSetIdx = 0)
        assertTrue("The next session must really record its prescribed load: $result", result is RecordSetResult.Created)
        assertEquals(expectedKg, vm.uiState.value.completedSets.getValue(workoutSetKey(exercise.id, 0)).weight, 1e-8)
        vm.flushOngoingForBackgroundAndAwait()
    }

    private suspend fun journey(protocol: String, mode: AutoregulationMode) {
        val count = if (protocol == "nsuns-531-lp-4d") 1 else 4
        val f = Cal7ManagedRoomFixture(protocol, mode).prepare(manualWeekIndex = when (protocol) {
            "nsuns-531-lp-4d" -> 1
            "juggernaut-2" -> 4
            else -> 0
        }).also { fixture = it }
        val before = f.program
        val initialTms = tms(before.powerliftingProfile!!)
        val sourceWeeks = f.weeks.take(count)
        val sourceWeekIds = sourceWeeks.map { it.id }
        val sessionIds = sourceWeeks.map { week -> week.id to week.sessions.map { it.id } }
        val allSessionCount = sessionIds.sumOf { it.second.size }
        val foreign = before.exerciseLoadReferences.filter { it.exerciseId == f.foreignExerciseId }
        assertEquals(1, foreign.size)
        val logs = mutableListOf<WorkoutLog>()
        var ordinal = 0
        // The finish API accepts the frozen completion instant used by the product preview/log contract.
        // Space fixture sessions two days apart; do not manufacture logs or feed helper-only AMRAP hits.
        val lastInstant = Instant.now().minus(1, ChronoUnit.DAYS)
        sessionIds.forEachIndexed { weekIndex, (weekId, ids) ->
            assertEquals("The real cursor must reach each programmed week in order", weekId, f.program.runState!!.weekId)
            ids.forEachIndexed { sessionIndex, sessionId ->
                val vm = f.open(findSession(f.program, sessionId))
                val date = lastInstant.minus((allSessionCount - 1L - ordinal++) * 2, ChronoUnit.DAYS)
                val log = f.completeAndFinish(vm, date) { exercise, set ->
                    val minimum = set.amrapMinimumReps ?: set.targetReps ?: 8
                    when {
                        !set.purpose.isAmrap -> set.targetReps ?: 8
                        exercise.slotRole == SlotRole.T1_MAIN && exercise.catalogConfigurationId == CatalogIds.OHP &&
                            protocol == "nsuns-531-lp-4d" -> 5 // Published nSuns band: +5kg at the 95% 1+.
                        exercise.slotRole == SlotRole.T1_MAIN && exercise.catalogConfigurationId == CatalogIds.OHP &&
                            protocol == "juggernaut-2" -> minimum + 4 // Four over standard: +5kg upper body.
                        else -> minimum
                    }
                }
                logs += log
                assertEquals(before.runState!!.runId, log.programRunId)
                assertEquals(1, log.cycleNumber)
                assertEquals(weekId, log.weekId)
                assertEquals("Every prescribed session reached durable history", logs.size,
                    f.repository.history.value.count { it.programId == f.programId })
                assertEquals("Actual recorded loads survive subsequent sessions", log,
                    f.repository.history.value.single { it.id == log.id })
                if (sessionIndex != ids.lastIndex || weekIndex != sourceWeeks.lastIndex) {
                    // Authored method changes only on its actual cycle/wave close, not after a + set or a single day.
                    assertTms(initialTms, f.program)
                }
                assertProtection(f, foreign, logs)
            }
            val pending = f.program.runState?.pendingAction
            if (protocol == "nsuns-531-lp-4d") {
                val expected = initialTms + (LiftSlot.OVERHEAD to 77.0)
                when (mode) {
                    AutoregulationMode.OFF -> {
                        assertTms(initialTms, f.program)
                        assertNull(pending)
                        assertTrue(f.program.runState!!.autoregulationAudit.isEmpty())
                    }
                    AutoregulationMode.PROPOSE -> {
                        assertTms(initialTms, f.program)
                        assertEquals(PendingProgramActionType.CONFIRM_AUTOREGULATION, pending!!.type)
                        val tmProposals = pending.proposals.filter { it.kind == AutoregulationProposalKind.ADJUST_TM }
                        assertEquals(1, tmProposals.size)
                        assertEquals("OVERHEAD", tmProposals.single().liftSlot)
                        assertEquals(5.0, tmProposals.single().kgDelta!!, 0.0)
                        assertEquals("Before accepting, the untouched target still has its prior load", 72.0,
                            f.weeks[1].sessions.first().allExercises().single { it.slotRole == SlotRole.T1_MAIN }.referenceTrainingMax!!, 0.0)
                        f.resolveHumanGate(acceptPerformance = true)
                        assertTms(expected, f.program)
                        assertTrue(f.program.runState!!.autoregulationAudit.any {
                            it.resolution == PendingActionResolutionStatus.APPLIED && AutoregulationProposalKind.ADJUST_TM in it.kinds })
                    }
                    AutoregulationMode.AUTO -> {
                        assertTms(expected, f.program)
                        assertNull(pending)
                        assertTrue(f.program.runState!!.autoregulationAudit.any {
                            it.mode == AutoregulationMode.AUTO && it.resolution == PendingActionResolutionStatus.APPLIED &&
                                AutoregulationProposalKind.ADJUST_TM in it.kinds })
                    }
                }
            } else if (pending != null) {
                if (pending.type == PendingProgramActionType.CONFIRM_AUTOREGULATION) {
                    assertTrue("The author's + set must not be converted into generic weekly TM proposals",
                        pending.proposals.none { it.kind == AutoregulationProposalKind.ADJUST_TM })
                }
                // A separate recovery gate remains an explicit athlete choice; this fixture declines it.
                f.resolveHumanGate(acceptPerformance = false)
            }
            assertProtection(f, foreign, logs)
        }
        assertEquals(allSessionCount, logs.size)
        assertEquals(sourceWeekIds.toSet(), logs.mapNotNull { it.weekId }.toSet())
        val expectedTms = when (protocol) {
            "wendler-531-bbb", "gzclp" -> mapOf(LiftSlot.SQUAT to 185.0, LiftSlot.BENCH to 110.5,
                LiftSlot.DEADLIFT to 203.0, LiftSlot.OVERHEAD to 74.5)
            "juggernaut-2" -> initialTms + (LiftSlot.OVERHEAD to 77.0)
            else -> if (mode == AutoregulationMode.OFF) initialTms else initialTms + (LiftSlot.OVERHEAD to 77.0)
        }
        assertTms(expectedTms, f.program)
        assertEquals("TM progression must never manufacture new 1RM values", oneRms(before.powerliftingProfile!!), oneRms(f.program.powerliftingProfile!!))
        for ((lift, expected) in expectedTms) {
            val exercise = ExerciseTrainingMax.exercises(f.program).first { it.slotRole == SlotRole.T1_MAIN && it.catalogConfigurationId == configs.getValue(lift) }
            assertEquals("The canonical JSON pool must follow the real $protocol progression for $lift", expected,
                ExerciseTrainingMax.capturedKg(f.program, exercise, PlanLoadReferenceKind.EXERCISE_TM)!!, 1e-8)
        }
        if (protocol == "wendler-531-bbb" || protocol == "gzclp") {
            assertEquals(2, f.program.runState!!.cycleNumber)
            assertEquals(sourceWeeks.first().id, f.program.runState!!.weekId)
        } else {
            assertEquals(f.weeks[count].id, f.program.runState!!.weekId)
        }
        assertProtection(f, foreign, logs)
        val nextWeekId = f.program.runState!!.weekId!!
        verifyNextSession(f, nextWeekId, expectedTms.getValue(LiftSlot.OVERHEAD))
        assertProtection(f, foreign, logs)
    }

    @Test fun wendler531OffCompletesTheCycleAndStartsTheNextOne() = runBlocking { journey("wendler-531-bbb", AutoregulationMode.OFF) }
    @Test fun wendler531ProposeCompletesTheCycleAndStartsTheNextOne() = runBlocking { journey("wendler-531-bbb", AutoregulationMode.PROPOSE) }
    @Test fun wendler531AutoCompletesTheCycleAndStartsTheNextOne() = runBlocking { journey("wendler-531-bbb", AutoregulationMode.AUTO) }
    @Test fun nsunsOffCompletesTheWeekWithoutAProposalOrTmChange() = runBlocking { journey("nsuns-531-lp-4d", AutoregulationMode.OFF) }
    @Test fun nsunsProposeCompletesTheWeekThenTheAthleteAcceptsTheFiveKg() = runBlocking { journey("nsuns-531-lp-4d", AutoregulationMode.PROPOSE) }
    @Test fun nsunsAutoCompletesTheWeekAndPersistsTheFiveKgWithAudit() = runBlocking { journey("nsuns-531-lp-4d", AutoregulationMode.AUTO) }
    @Test fun juggernautOffCompletesTheFourWeekWaveAndUsesRepsOverStandard() = runBlocking { journey("juggernaut-2", AutoregulationMode.OFF) }
    @Test fun juggernautProposeCompletesTheFourWeekWaveAndUsesRepsOverStandard() = runBlocking { journey("juggernaut-2", AutoregulationMode.PROPOSE) }
    @Test fun juggernautAutoCompletesTheFourWeekWaveAndUsesRepsOverStandard() = runBlocking { journey("juggernaut-2", AutoregulationMode.AUTO) }
    @Test fun gzclpOffCompletesTheCycleWithTheBaseVolume() = runBlocking { journey("gzclp", AutoregulationMode.OFF) }
    @Test fun gzclpProposeCompletesTheCycleWithTheBaseVolume() = runBlocking { journey("gzclp", AutoregulationMode.PROPOSE) }
    @Test fun gzclpAutoCompletesTheCycleWithTheBaseVolume() = runBlocking { journey("gzclp", AutoregulationMode.AUTO) }
}

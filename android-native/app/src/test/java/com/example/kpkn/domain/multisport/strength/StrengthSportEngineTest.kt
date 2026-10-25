package com.example.kpkn.domain.multisport.strength

import com.example.kpkn.domain.multisport.SportId
import com.example.kpkn.domain.multisport.tracking.SportTrackingDocument
import com.example.kpkn.domain.multisport.tracking.SportTrackingRegistry
import com.example.kpkn.domain.multisport.tracking.SportDocumentValidation
import com.example.kpkn.domain.multisport.tracking.SportDocumentPurpose
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrengthSportEngineTest {
    @Test fun weightliftingSlotIdentityDoesNotCollideWhenIdsContainSeparators() {
        val payload = WeightliftingPayload(
            complexes = listOf(
                WeightliftingComplexPrescription("a|1|b", 1, listOf(WeightliftingComplexStep("c", "olympic", "clean", 1))),
                WeightliftingComplexPrescription("a", 1, listOf(WeightliftingComplexStep("b|1|c", "olympic", "clean", 1))),
            ),
            attempts = listOf(
                WeightliftingAttempt("first", "olympic", "clean", StrengthExecutionStatus.VALID,
                    complexId = "a|1|b", roundIndex = 1, stepId = "c", repetitionIndex = 1),
                WeightliftingAttempt("second", "olympic", "clean", StrengthExecutionStatus.VALID,
                    complexId = "a", roundIndex = 1, stepId = "b|1|c", repetitionIndex = 1),
            ),
        )
        assertTrue(WeightliftingEngine.validate(payload).isEmpty())
        assertEquals(2, value(WeightliftingEngine.calculateMetrics(payload)).families.single().completedRepetitions)
        assertTrue(WeightliftingEngine.validate(payload.copy(attempts = payload.attempts + payload.attempts.first().copy(id = "actual-duplicate")))
            .any { it.code == StrengthSportIssueCode.DUPLICATE_SUCCESS })
    }

    @Test fun failedCalisthenicsCanRecordZeroOrUnknownDoseWithoutInventingCompletedWork() {
        val skill = CalisthenicsSkillDefinition("lever", "beginner", "strict")
        val payload = CalisthenicsPayload(skills = listOf(skill), trials = listOf(
            CalisthenicsTrial("failed-zero", "lever", "beginner", "strict", CalisthenicsTrialMode.REPS,
                StrengthExecutionStatus.FAILED, repetitions = 0),
            CalisthenicsTrial("invalid-unknown", "lever", "beginner", "strict", CalisthenicsTrialMode.REPS,
                StrengthExecutionStatus.INVALIDATED),
            CalisthenicsTrial("hold-unknown", "lever", "beginner", "strict", CalisthenicsTrialMode.HOLD,
                StrengthExecutionStatus.FAILED),
            CalisthenicsTrial("hold-zero", "lever", "beginner", "strict", CalisthenicsTrialMode.HOLD,
                StrengthExecutionStatus.INVALIDATED, holdSeconds = 0),
        ))
        assertTrue(CalisthenicsEngine.validate(payload).isEmpty())
        val metrics = value(CalisthenicsEngine.calculateMetrics(payload))
        assertTrue(metrics.all { it.completedRepetitions == null && it.completedHoldSeconds == null })
        assertTrue(metrics.all { it.failedTrials == 1 && it.invalidatedTrials == 1 })
        val failed = payload.trials.first()
        assertFalse(CalisthenicsEngine.validate(payload.copy(trials = listOf(failed.copy(repetitions = -1)))).isEmpty())
        assertFalse(CalisthenicsEngine.validate(payload.copy(trials = listOf(failed.copy(holdSeconds = 1)))).isEmpty())
        assertFalse(CalisthenicsEngine.validate(payload.copy(trials = listOf(failed.copy(status = StrengthExecutionStatus.VALID)))).isEmpty())
        assertFalse(CalisthenicsEngine.validate(payload.copy(trials = listOf(failed.copy(status = StrengthExecutionStatus.VALID, repetitions = null)))).isEmpty())
    }

    @Test fun failedArmwrestlingCanRecordZeroOrUnknownAndStillRejectsMixedModes() {
        val drill = ArmwrestlingDrillDefinition("drill", ArmwrestlingSide.LEFT, "start", "handle", "grip", "setup",
            ArmwrestlingLoadMode.NO_EXTERNAL_LOAD)
        val payload = ArmwrestlingPayload(drills = listOf(drill), trials = listOf(
            ArmwrestlingDrillTrial("failed-zero", "drill", ArmwrestlingTrialMode.REPS, StrengthExecutionStatus.FAILED, repetitions = 0),
            ArmwrestlingDrillTrial("invalid-unknown", "drill", ArmwrestlingTrialMode.REPS, StrengthExecutionStatus.INVALIDATED),
            ArmwrestlingDrillTrial("hold-unknown", "drill", ArmwrestlingTrialMode.HOLD, StrengthExecutionStatus.FAILED),
            ArmwrestlingDrillTrial("hold-zero", "drill", ArmwrestlingTrialMode.HOLD, StrengthExecutionStatus.INVALIDATED, holdSeconds = 0),
        ))
        assertTrue(ArmwrestlingEngine.validate(payload).isEmpty())
        val metrics = value(ArmwrestlingEngine.calculateMetrics(payload))
        assertTrue(metrics.all { it.completedRepetitions == null && it.completedHoldSeconds == null })
        assertTrue(metrics.all { it.failedTrials == 1 && it.invalidatedTrials == 1 })
        val failed = payload.trials.first()
        assertFalse(ArmwrestlingEngine.validate(payload.copy(trials = listOf(failed.copy(repetitions = -1)))).isEmpty())
        assertFalse(ArmwrestlingEngine.validate(payload.copy(trials = listOf(failed.copy(holdSeconds = 1)))).isEmpty())
        assertFalse(ArmwrestlingEngine.validate(payload.copy(trials = listOf(failed.copy(status = StrengthExecutionStatus.VALID)))).isEmpty())
        assertFalse(ArmwrestlingEngine.validate(payload.copy(trials = listOf(failed.copy(status = StrengthExecutionStatus.VALID, repetitions = null)))).isEmpty())
    }

    @Test fun failedStrongmanHasNoRequiredScoreAndNeverBecomesBest() {
        val event = StrongmanEventDefinition("event", "implement", "setup", StrongmanScoreCriterion.TIME, StrongmanScoreDirection.MINIMIZE)
        val payload = StrongmanPayload(events = listOf(event), attempts = listOf(
            StrongmanEventAttempt("zero", "event", StrengthExecutionStatus.FAILED, time = StrongmanTime(0.0, StrongmanTimeUnit.SECONDS)),
            StrongmanEventAttempt("unknown", "event", StrengthExecutionStatus.INVALIDATED),
            StrongmanEventAttempt("valid", "event", StrengthExecutionStatus.VALID, time = StrongmanTime(10.0, StrongmanTimeUnit.SECONDS)),
        ))
        assertTrue(StrongmanEngine.validate(payload).isEmpty())
        val metrics = value(StrongmanEngine.calculateMetrics(payload)).single()
        assertEquals(10.0, metrics.bestScore!!, 0.0)
        assertEquals(1, metrics.validAttempts)
        assertEquals(1, metrics.failedAttempts)
        assertEquals(1, metrics.invalidatedAttempts)
        StrongmanScoreCriterion.entries.forEach { criterion ->
            val unknown = payload.copy(events = listOf(event.copy(scoringCriterion = criterion)),
                attempts = listOf(StrongmanEventAttempt("unknown", "event", StrengthExecutionStatus.FAILED)))
            assertTrue(StrongmanEngine.validate(unknown).isEmpty())
            assertNull(value(StrongmanEngine.calculateMetrics(unknown)).single().bestScore)
        }
        listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { bad ->
            assertFalse(StrongmanEngine.validate(payload.copy(attempts = listOf(payload.attempts.first().copy(
                time = StrongmanTime(bad, StrongmanTimeUnit.SECONDS))))).isEmpty())
        }
        assertFalse(StrongmanEngine.validate(payload.copy(attempts = listOf(payload.attempts.first().copy(status = StrengthExecutionStatus.VALID)))).isEmpty())
        assertFalse(StrongmanEngine.validate(payload.copy(attempts = listOf(payload.attempts[1].copy(status = StrengthExecutionStatus.VALID)))).isEmpty())
    }

    @Test
    fun weightliftingComplexCountsIndividualSuccessfulLiftsAndSeparatesFailedAttempts() {
        val payload = WeightliftingPayload(
            references = listOf(
                WeightliftingMovementReference("clean-ref", "olympic", "clean", StrengthLoad(100.0, StrengthLoadUnit.KILOGRAMS)),
                WeightliftingMovementReference("jerk-ref", "olympic", "jerk", StrengthLoad(120.0, StrengthLoadUnit.KILOGRAMS)),
            ),
            complexes = listOf(
                WeightliftingComplexPrescription(
                    id = "clean-and-jerk",
                    rounds = 1,
                    steps = listOf(
                        WeightliftingComplexStep("clean-step", "olympic", "clean", 2, referenceId = "clean-ref"),
                        WeightliftingComplexStep("jerk-step", "olympic", "jerk", 1, referenceId = "jerk-ref"),
                    ),
                ),
            ),
            attempts = listOf(
                WeightliftingAttempt("clean-fail", "olympic", "clean", StrengthExecutionStatus.FAILED,
                    StrengthLoad(80.0, StrengthLoadUnit.KILOGRAMS), "clean-ref", "clean-and-jerk", 1, "clean-step", 1, 1),
                WeightliftingAttempt("clean-retry", "olympic", "clean", StrengthExecutionStatus.VALID,
                    StrengthLoad(80.0, StrengthLoadUnit.KILOGRAMS), "clean-ref", "clean-and-jerk", 1, "clean-step", 1, 2),
                WeightliftingAttempt("clean-second-rep", "olympic", "clean", StrengthExecutionStatus.VALID,
                    StrengthLoad(82.0, StrengthLoadUnit.KILOGRAMS), "clean-ref", "clean-and-jerk", 1, "clean-step", 2, 1),
                WeightliftingAttempt("jerk", "olympic", "jerk", StrengthExecutionStatus.VALID,
                    StrengthLoad(60.0, StrengthLoadUnit.KILOGRAMS), "jerk-ref", "clean-and-jerk", 1, "jerk-step", 1, 1),
            ),
        )

        val result = WeightliftingEngine.calculateMetrics(payload)
        assertTrue(result is StrengthSportResult.Valid)
        val metrics = (result as StrengthSportResult.Valid).value
        assertEquals(1, metrics.families.size)
        assertEquals(4, metrics.families.single().attemptCount)
        assertEquals(3, metrics.families.single().completedRepetitions)
        assertEquals(1, metrics.families.single().failedAttempts)
        assertEquals(0, metrics.families.single().invalidatedAttempts)
        assertEquals(222.0, metrics.families.single().completedTonnageKilograms!!, 0.000001)
        assertEquals(80.0, metrics.attempts.single { it.attemptId == "clean-retry" }.relativeLoadPercent!!, 0.000001)
        assertEquals(50.0, metrics.attempts.single { it.attemptId == "jerk" }.relativeLoadPercent!!, 0.000001)
        // The complex prescription itself contributes no additional set or tonnage.
    }

    @Test
    fun weightliftingRejectsDuplicateSuccessAndReferenceFromAnotherMovement() {
        val complex = WeightliftingComplexPrescription(
            "c", 1, listOf(WeightliftingComplexStep("s", "olympic", "clean", 1)),
        )
        val duplicateSuccess = WeightliftingPayload(
            complexes = listOf(complex),
            attempts = listOf(
                WeightliftingAttempt("a", "olympic", "clean", StrengthExecutionStatus.VALID,
                    StrengthLoad(50.0, StrengthLoadUnit.KILOGRAMS), complexId = "c", roundIndex = 1, stepId = "s", repetitionIndex = 1),
                WeightliftingAttempt("b", "olympic", "clean", StrengthExecutionStatus.VALID,
                    StrengthLoad(51.0, StrengthLoadUnit.KILOGRAMS), complexId = "c", roundIndex = 1, stepId = "s", repetitionIndex = 1),
            ),
        )
        assertTrue(WeightliftingEngine.validate(duplicateSuccess).any { it.code == StrengthSportIssueCode.DUPLICATE_SUCCESS })

        val mismatched = WeightliftingPayload(
            references = listOf(WeightliftingMovementReference("snatch-ref", "olympic", "snatch", StrengthLoad(80.0, StrengthLoadUnit.KILOGRAMS))),
            attempts = listOf(WeightliftingAttempt("clean", "olympic", "clean", StrengthExecutionStatus.VALID,
                StrengthLoad(60.0, StrengthLoadUnit.KILOGRAMS), "snatch-ref")),
        )
        assertTrue(WeightliftingEngine.validate(mismatched).any {
            it.code == StrengthSportIssueCode.MOVEMENT_REFERENCE_MISMATCH
        })
    }

    @Test
    fun weightliftingDoesNotTurnMissingLoadIntoZeroAndConvertsPoundsExplicitly() {
        val payload = WeightliftingPayload(
            attempts = listOf(
                WeightliftingAttempt("unrecorded", "barbell", "press", StrengthExecutionStatus.VALID),
            ),
        )
        val metrics = value(WeightliftingEngine.calculateMetrics(payload)).families.single()
        assertNull(metrics.completedTonnageKilograms)
        assertEquals(1, metrics.successfulExecutionsMissingLoad)

        val explicitZero = value(WeightliftingEngine.calculateMetrics(payload.copy(
            attempts = listOf(payload.attempts.single().copy(load = StrengthLoad(0.0, StrengthLoadUnit.KILOGRAMS))),
        ))).families.single()
        assertEquals(0.0, explicitZero.completedTonnageKilograms!!, 0.0)
        assertEquals(0, explicitZero.successfulExecutionsMissingLoad)

        assertEquals(99.7903214, StrengthLoad(220.0, StrengthLoadUnit.POUNDS).kilograms(), 0.0000001)
    }

    @Test
    fun calisthenicsProgressionRequiresAuthoredEdgeAndExplicitPrerequisiteEvidence() {
        val payload = CalisthenicsPayload(
            skills = listOf(
                CalisthenicsSkillDefinition("tuck", "beginner", "strict", progressionTargetSkillIds = listOf("advanced-tuck")),
                CalisthenicsSkillDefinition("advanced-tuck", "intermediate", "strict", prerequisiteSkillIds = listOf("tuck")),
            ),
        )
        val withoutEvidence = CalisthenicsEngine.proposeProgression(payload, "proposal", "tuck", "advanced-tuck", emptyList())
        assertTrue(withoutEvidence is StrengthSportResult.Invalid)
        assertTrue((withoutEvidence as StrengthSportResult.Invalid).issues.any {
            it.code == StrengthSportIssueCode.PREREQUISITE_NOT_VERIFIED
        })

        val proposal = value(CalisthenicsEngine.proposeProgression(
            payload, "proposal", "tuck", "advanced-tuck", listOf("tuck"),
        ))
        assertEquals(CalisthenicsProgressionState.PROPOSED, proposal.state)
        assertEquals(listOf("tuck"), proposal.verifiedPrerequisiteSkillIds)
        assertEquals(emptyList<CalisthenicsProgressionProposal>(), payload.progressionProposals)
        assertTrue(CalisthenicsEngine.proposeProgression(payload, "p2", "tuck", "unlisted", listOf("tuck"))
            is StrengthSportResult.Invalid)
    }

    @Test
    fun calisthenicsKeepsHoldRepsAssistanceAndAddedLoadInSeparateBuckets() {
        val skill = CalisthenicsSkillDefinition("front-lever", "advanced", "straight-arm")
        val payload = CalisthenicsPayload(
            skills = listOf(skill),
            trials = listOf(
                CalisthenicsTrial("assisted", skill.skillId, skill.levelId, skill.techniqueId,
                    CalisthenicsTrialMode.REPS, StrengthExecutionStatus.VALID, repetitions = 5,
                    loadKind = CalisthenicsLoadKind.ASSISTED,
                    externalLoad = StrengthLoad(10.0, StrengthLoadUnit.KILOGRAMS), loadContextId = "counterweight"),
                CalisthenicsTrial("assisted-band", skill.skillId, skill.levelId, skill.techniqueId,
                    CalisthenicsTrialMode.REPS, StrengthExecutionStatus.VALID, repetitions = 4,
                    loadKind = CalisthenicsLoadKind.ASSISTED,
                    externalLoad = StrengthLoad(10.0, StrengthLoadUnit.KILOGRAMS), loadContextId = "band-A"),
                CalisthenicsTrial("added", skill.skillId, skill.levelId, skill.techniqueId,
                    CalisthenicsTrialMode.REPS, StrengthExecutionStatus.VALID, repetitions = 3,
                    loadKind = CalisthenicsLoadKind.ADDED_LOAD,
                    externalLoad = StrengthLoad(10.0, StrengthLoadUnit.KILOGRAMS), loadContextId = "belt"),
                CalisthenicsTrial("hold", skill.skillId, skill.levelId, skill.techniqueId,
                    CalisthenicsTrialMode.HOLD, StrengthExecutionStatus.VALID, holdSeconds = 12),
                CalisthenicsTrial("failed", skill.skillId, skill.levelId, skill.techniqueId,
                    CalisthenicsTrialMode.REPS, StrengthExecutionStatus.FAILED, repetitions = 7),
                CalisthenicsTrial("invalidated", skill.skillId, skill.levelId, skill.techniqueId,
                    CalisthenicsTrialMode.HOLD, StrengthExecutionStatus.INVALIDATED, holdSeconds = 30),
            ),
        )

        val metrics = value(CalisthenicsEngine.calculateMetrics(payload))
        assertEquals(5, metrics.size)
        assertEquals(5L, metrics.single { it.loadKind == CalisthenicsLoadKind.ASSISTED &&
            it.loadContextId == "counterweight" }.completedRepetitions)
        assertEquals(4L, metrics.single { it.loadKind == CalisthenicsLoadKind.ASSISTED &&
            it.loadContextId == "band-A" }.completedRepetitions)
        assertEquals(3L, metrics.single { it.loadKind == CalisthenicsLoadKind.ADDED_LOAD }.completedRepetitions)
        assertEquals(12L, metrics.single { it.mode == CalisthenicsTrialMode.HOLD &&
            it.loadKind == CalisthenicsLoadKind.BODYWEIGHT }.completedHoldSeconds)
        assertEquals(1, metrics.single { it.mode == CalisthenicsTrialMode.REPS &&
            it.loadKind == CalisthenicsLoadKind.BODYWEIGHT }.failedTrials)
        assertNull(metrics.single { it.mode == CalisthenicsTrialMode.REPS &&
            it.loadKind == CalisthenicsLoadKind.BODYWEIGHT }.completedRepetitions)
        assertEquals(1, metrics.single { it.mode == CalisthenicsTrialMode.HOLD &&
            it.loadKind == CalisthenicsLoadKind.BODYWEIGHT }.invalidatedTrials)
    }

    @Test
    fun calisthenicsRejectsMissingOrMixedDoseAndExternalLoadContext() {
        val skill = CalisthenicsSkillDefinition("dip", "intermediate", "rings")
        val invalid = CalisthenicsPayload(
            skills = listOf(skill),
            trials = listOf(
                CalisthenicsTrial("missing-dose", "dip", "intermediate", "rings",
                    CalisthenicsTrialMode.REPS, StrengthExecutionStatus.VALID),
                CalisthenicsTrial("mixed-dose", "dip", "intermediate", "rings",
                    CalisthenicsTrialMode.HOLD, StrengthExecutionStatus.VALID, repetitions = 4, holdSeconds = 5),
                CalisthenicsTrial("unscoped-assistance", "dip", "intermediate", "rings",
                    CalisthenicsTrialMode.REPS, StrengthExecutionStatus.VALID, repetitions = 4,
                    loadKind = CalisthenicsLoadKind.ASSISTED, externalLoad = StrengthLoad(5.0, StrengthLoadUnit.KILOGRAMS)),
            ),
        )
        val issues = CalisthenicsEngine.validate(invalid)
        assertTrue(issues.any { it.path.contains("missing-dose") || it.path == "trials[0]" })
        assertTrue(issues.any { it.path == "trials[1]" && it.code == StrengthSportIssueCode.INVALID_SHAPE })
        assertTrue(issues.any { it.code == StrengthSportIssueCode.REQUIRED && it.path.endsWith("loadContextId") })
    }

    @Test
    fun strongmanScoresOnlyDeclaredCriterionAndNeverCombinesDifferentImplements() {
        val payload = StrongmanPayload(
            events = listOf(
                StrongmanEventDefinition("farmers", "farmers-handles-A", "20m-course-A",
                    StrongmanScoreCriterion.LOAD, StrongmanScoreDirection.MAXIMIZE),
                StrongmanEventDefinition("farmers-b", "farmers-handles-B", "20m-course-A",
                    StrongmanScoreCriterion.LOAD, StrongmanScoreDirection.MAXIMIZE),
            ),
            attempts = listOf(
                StrongmanEventAttempt("a1", "farmers", StrengthExecutionStatus.VALID, load = StrengthLoad(220.0, StrengthLoadUnit.POUNDS)),
                StrongmanEventAttempt("a2", "farmers", StrengthExecutionStatus.VALID, load = StrengthLoad(100.0, StrengthLoadUnit.KILOGRAMS)),
                StrongmanEventAttempt("a3-failed", "farmers", StrengthExecutionStatus.FAILED, load = StrengthLoad(140.0, StrengthLoadUnit.KILOGRAMS)),
                StrongmanEventAttempt("b1", "farmers-b", StrengthExecutionStatus.VALID, load = StrengthLoad(90.0, StrengthLoadUnit.KILOGRAMS)),
            ),
        )
        val metrics = value(StrongmanEngine.calculateMetrics(payload))
        assertEquals(2, metrics.size)
        val first = metrics.single { it.implementId == "farmers-handles-A" }
        assertEquals(2, first.validAttempts)
        assertEquals(1, first.failedAttempts)
        assertEquals(100.0, first.bestScore!!, 0.000001)
        assertEquals(90.0, metrics.single { it.implementId == "farmers-handles-B" }.bestScore!!, 0.0)
        assertEquals(StrongmanScoreUnit.KILOGRAMS, first.scoreUnit)
        val missingScore = payload.copy(attempts = listOf(
            StrongmanEventAttempt("missing", "farmers", StrengthExecutionStatus.VALID),
        ))
        assertTrue(StrongmanEngine.validate(missingScore).any { it.code == StrengthSportIssueCode.REQUIRED })
    }

    @Test
    fun strongmanNormalizesFeetAndUsesDeclaredMinimizeDirectionWithoutOfficialScoringClaims() {
        val payload = StrongmanPayload(
            events = listOf(StrongmanEventDefinition(
                "carry", "yoke-X", "lane-1", StrongmanScoreCriterion.DISTANCE, StrongmanScoreDirection.MAXIMIZE,
            )),
            attempts = listOf(
                StrongmanEventAttempt("metric", "carry", StrengthExecutionStatus.VALID,
                    distance = StrongmanDistance(100.0, StrongmanDistanceUnit.FEET)),
                StrongmanEventAttempt("failed", "carry", StrengthExecutionStatus.FAILED,
                    distance = StrongmanDistance(50.0, StrongmanDistanceUnit.METERS)),
            ),
        )
        val result = value(StrongmanEngine.calculateMetrics(payload)).single()
        assertEquals(30.48, result.bestScore!!, 0.000001)
        assertEquals(1, result.failedAttempts)

        val timed = payload.copy(
            events = listOf(StrongmanEventDefinition(
                "timed-carry", "yoke-X", "lane-1", StrongmanScoreCriterion.TIME, StrongmanScoreDirection.MINIMIZE,
            )),
            attempts = listOf(
                StrongmanEventAttempt("slow", "timed-carry", StrengthExecutionStatus.VALID,
                    time = StrongmanTime(60.0, StrongmanTimeUnit.SECONDS)),
                StrongmanEventAttempt("fast", "timed-carry", StrengthExecutionStatus.VALID,
                    time = StrongmanTime(0.5, StrongmanTimeUnit.MINUTES)),
            ),
        )
        assertEquals(30.0, value(StrongmanEngine.calculateMetrics(timed)).single().bestScore!!, 0.0)
    }

    @Test
    fun armwrestlingKeepsSidePositionGripLoadModeAndDoseSeparate() {
        val payload = ArmwrestlingPayload(
            drills = listOf(
                ArmwrestlingDrillDefinition("left-cup", ArmwrestlingSide.LEFT, "cupping", "rolling-handle", "cup", "table-A",
                    ArmwrestlingLoadMode.EXTERNAL_LOAD),
                ArmwrestlingDrillDefinition("right-cup", ArmwrestlingSide.RIGHT, "cupping", "rolling-handle", "cup", "table-A",
                    ArmwrestlingLoadMode.EXTERNAL_LOAD),
                ArmwrestlingDrillDefinition("left-pronation", ArmwrestlingSide.LEFT, "pronation", "strap", "pronation-grip", "table-A",
                    ArmwrestlingLoadMode.NO_EXTERNAL_LOAD),
            ),
            trials = listOf(
                ArmwrestlingDrillTrial("l1", "left-cup", ArmwrestlingTrialMode.REPS, StrengthExecutionStatus.VALID,
                    repetitions = 8, externalLoad = StrengthLoad(20.0, StrengthLoadUnit.KILOGRAMS), loadContextId = "stack-A"),
                ArmwrestlingDrillTrial("l2", "left-cup", ArmwrestlingTrialMode.REPS, StrengthExecutionStatus.VALID,
                    repetitions = 4, externalLoad = StrengthLoad(20.0, StrengthLoadUnit.KILOGRAMS), loadContextId = "stack-B"),
                ArmwrestlingDrillTrial("l-fail", "left-cup", ArmwrestlingTrialMode.REPS, StrengthExecutionStatus.FAILED,
                    repetitions = 3, externalLoad = StrengthLoad(20.0, StrengthLoadUnit.KILOGRAMS), loadContextId = "stack-A"),
                ArmwrestlingDrillTrial("r1", "right-cup", ArmwrestlingTrialMode.REPS, StrengthExecutionStatus.VALID,
                    repetitions = 6, externalLoad = StrengthLoad(20.0, StrengthLoadUnit.KILOGRAMS), loadContextId = "stack-A"),
                ArmwrestlingDrillTrial("hold", "left-pronation", ArmwrestlingTrialMode.HOLD, StrengthExecutionStatus.VALID,
                    holdSeconds = 15),
            ),
        )
        val metrics = value(ArmwrestlingEngine.calculateMetrics(payload))
        assertEquals(4, metrics.size)
        assertEquals(8L, metrics.single { it.side == ArmwrestlingSide.LEFT &&
            it.mode == ArmwrestlingTrialMode.REPS && it.loadContextId == "stack-A" }.completedRepetitions)
        assertEquals(4L, metrics.single { it.side == ArmwrestlingSide.LEFT &&
            it.mode == ArmwrestlingTrialMode.REPS && it.loadContextId == "stack-B" }.completedRepetitions)
        assertEquals(1, metrics.single { it.drillId == "left-cup" && it.loadContextId == "stack-A" }.failedTrials)
        assertEquals(6L, metrics.single { it.side == ArmwrestlingSide.RIGHT }.completedRepetitions)
        assertEquals(15L, metrics.single { it.mode == ArmwrestlingTrialMode.HOLD }.completedHoldSeconds)
    }

    @Test
    fun durationIsOnlyADeclaredPositiveValueAndPayloadsAreVersionedSerializable() {
        assertNull(declaredStrengthDuration(null))
        assertNull(declaredStrengthDuration(0L))
        assertEquals(1200L, declaredStrengthDuration(1200L)!!.seconds)
        assertEquals(StrengthDurationEvidenceSource.DECLARED_EXTERNAL, declaredStrengthDuration(1200L)!!.source)

        val json = Json { encodeDefaults = true }
        val payload = StrongmanPayload(
            declaredDurationSeconds = 600L,
            events = listOf(StrongmanEventDefinition("deadlift", "bar-A", "gym", StrongmanScoreCriterion.LOAD,
                StrongmanScoreDirection.MAXIMIZE)),
        )
        val encoded = json.encodeToString(payload)
        assertEquals(payload, json.decodeFromString<StrongmanPayload>(encoded))
        val weightlifting = WeightliftingPayload(
            complexes = listOf(WeightliftingComplexPrescription(
                "complex", 1, listOf(WeightliftingComplexStep("step", "olympic", "clean", 1)),
            )),
        )
        assertEquals(weightlifting, json.decodeFromString<WeightliftingPayload>(json.encodeToString(weightlifting)))
        val calisthenics = CalisthenicsPayload(
            skills = listOf(CalisthenicsSkillDefinition("front-lever", "advanced", "straight-arm")),
        )
        assertEquals(calisthenics, json.decodeFromString<CalisthenicsPayload>(json.encodeToString(calisthenics)))
        val armwrestling = ArmwrestlingPayload(
            drills = listOf(ArmwrestlingDrillDefinition(
                "cup", ArmwrestlingSide.LEFT, "cupping", "rolling-handle", "cup", "table-A",
                ArmwrestlingLoadMode.NO_EXTERNAL_LOAD,
            )),
        )
        assertEquals(armwrestling, json.decodeFromString<ArmwrestlingPayload>(json.encodeToString(armwrestling)))
        val jsonObject = json.decodeFromString<JsonObject>(encoded)
        assertEquals(600L, StrongmanEngine.plannedDurationSeconds(jsonObject))
        assertNull(StrongmanEngine.plannedDurationSeconds(json.encodeToString(
            payload.copy(schemaVersion = STRENGTH_SPORT_SCHEMA_VERSION + 1),
        ).let { json.decodeFromString<JsonObject>(it) }))
        assertEquals(setOf(SportId.WEIGHTLIFTING), WeightliftingEngine.sports)
        assertTrue(WeightliftingEngine.validate(WeightliftingPayload(), SportDocumentPurpose.PLAN)
            .any { it.code == StrengthSportIssueCode.INVALID_PURPOSE_CONTENT })
        assertFalse(StrengthLoad(0.0, StrengthLoadUnit.KILOGRAMS).kilograms().isNaN())

        val registry = SportTrackingRegistry(listOf(
            WeightliftingEngine, CalisthenicsEngine, StrongmanEngine, ArmwrestlingEngine,
        ))
        val document = SportTrackingDocument(
            id = "strongman-plan",
            engineId = StrongmanEngine.id,
            version = StrongmanEngine.version,
            sportId = SportId.STRONGMAN.name,
            purpose = SportDocumentPurpose.PLAN.name,
            payload = json.encodeToJsonElement(payload).jsonObject,
        )
        assertEquals(SportDocumentValidation.Valid, registry.validate(document))
        assertEquals(600L, registry.plannedDurationSeconds(document))
    }

    private fun <T> value(result: StrengthSportResult<T>): T {
        assertTrue("Expected a valid calculation, got $result", result is StrengthSportResult.Valid)
        return (result as StrengthSportResult.Valid).value
    }
}


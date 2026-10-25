package com.example.kpkn.domain.multisport.combat

import com.example.kpkn.domain.multisport.SportId
import com.example.kpkn.domain.multisport.tracking.SportDocumentPurpose
import com.example.kpkn.domain.multisport.tracking.SportDocumentValidation
import com.example.kpkn.domain.multisport.tracking.SportTrackingDocument
import com.example.kpkn.domain.multisport.tracking.SportTrackingRegistry
import kotlinx.serialization.SerializationException
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

class CombatSportEngineTest {
    private val boxingEngine = CombatSportEngine.forDiscipline(CombatDiscipline.BOXING)

    @Test
    fun planDurationCountsWorkAndOnlyBetweenRoundRestOnce() {
        val plan = CombatSessionPlan(
            sportId = CombatDiscipline.BOXING,
            blocks = listOf(
                blockPlan(
                    id = "pads",
                    rounds = CombatRoundPrescription(
                        roundCount = 3,
                        workSecondsPerRound = 180,
                        restSecondsBetweenRounds = 60,
                    ),
                    type = CombatBlockType.PADS,
                ),
                blockPlan(
                    id = "class",
                    type = CombatBlockType.EXTERNAL_CLASS,
                    rounds = CombatRoundPrescription(),
                    declaredExternalDurationSeconds = 3600,
                ),
            ),
        )

        assertTrue(boxingEngine.validatePlan(plan).isEmpty())
        // Pads are 3 × 180 seconds plus two 60-second rests; no terminal rest is added.
        assertEquals(4260L, boxingEngine.plannedDurationSeconds(plan))
        assertEquals(CombatSportResult.Valid(4260L), boxingEngine.calculatePlannedDurationSeconds(plan))
        val campPlan = plan.copy(camp = CombatCampPlan(
            targetWeeks = 8,
            milestones = listOf(CombatCampMilestone("m1", 4, "technique", "Review authored technique goal")),
            fixedClassCommitments = listOf(fixedClass("weekly-boxing", weekday = 2, minute = 600).copy(
                declaredDurationSeconds = 3600,
            )),
        ))
        assertTrue(boxingEngine.validatePlan(campPlan).isEmpty())
        assertEquals(4260L, boxingEngine.plannedDurationSeconds(campPlan))

        val singleRound = plan.copy(blocks = listOf(blockPlan(
            "one", CombatRoundPrescription(1, 100, 60), CombatBlockType.DRILL,
        )))
        assertEquals(100L, boxingEngine.plannedDurationSeconds(singleRound))
    }

    @Test
    fun planRejectsIncompleteOrDoubleDeclaredTimingAndInvalidCampMetadata() {
        val noRest = CombatSessionPlan(
            sportId = CombatDiscipline.BOXING,
            blocks = listOf(blockPlan(
                "bag", CombatRoundPrescription(roundCount = 3, workSecondsPerRound = 120),
                CombatBlockType.BAG,
            )),
        )
        assertTrue(boxingEngine.validatePlan(noRest).any { it.code == CombatSportIssueCode.INVALID_SHAPE })

        val doubleTime = CombatSessionPlan(
            sportId = CombatDiscipline.BOXING,
            blocks = listOf(blockPlan(
                "external",
                CombatRoundPrescription(2, 120, 30),
                CombatBlockType.EXTERNAL_CLASS,
                declaredExternalDurationSeconds = 900,
            )),
        )
        assertTrue(boxingEngine.validatePlan(doubleTime).any { it.code == CombatSportIssueCode.INVALID_SHAPE })

        val missingContext = CombatSessionPlan(
            sportId = CombatDiscipline.BOXING,
            blocks = listOf(blockPlan("drill").copy(
                rulesetId = "",
                rulesetVersion = 0,
                contextId = "",
                declaredContact = null,
                declaredIntensity = null,
            )),
        )
        val metadataIssues = boxingEngine.validatePlan(missingContext)
        assertTrue(metadataIssues.any { it.path.endsWith("rulesetId") && it.code == CombatSportIssueCode.REQUIRED })
        assertTrue(metadataIssues.any { it.path.endsWith("rulesetVersion") && it.code == CombatSportIssueCode.MUST_BE_POSITIVE })
        assertTrue(metadataIssues.any { it.path.endsWith("contextId") && it.code == CombatSportIssueCode.REQUIRED })
        assertTrue(metadataIssues.any { it.path.endsWith("declaredContact") && it.code == CombatSportIssueCode.REQUIRED })
        assertTrue(metadataIssues.any { it.path.endsWith("declaredIntensity") && it.code == CombatSportIssueCode.REQUIRED })

        val invalidCamp = CombatCampPlan(
            targetWeeks = 6,
            milestones = listOf(CombatCampMilestone("late", 7, "goal", "Too late")),
            fixedClassCommitments = listOf(
                fixedClass("class-a", weekday = 2, minute = 600),
                fixedClass("class-b", weekday = 2, minute = 600),
            ),
        )
        assertTrue(boxingEngine.validateCamp(invalidCamp).any { it.code == CombatSportIssueCode.OUT_OF_RANGE })
        assertTrue(boxingEngine.validateCamp(invalidCamp).any { it.code == CombatSportIssueCode.DUPLICATE_VALUE })
    }

    @Test
    fun planDurationOverflowIsTypedAndNeverReportedAsAValidEstimate() {
        val overflow = CombatSessionPlan(
            sportId = CombatDiscipline.BOXING,
            blocks = listOf(blockPlan(
                "extreme",
                CombatRoundPrescription(Int.MAX_VALUE, Long.MAX_VALUE, 0),
            )),
        )

        val result = boxingEngine.calculatePlannedDurationSeconds(overflow)
        assertTrue(result is CombatSportResult.Invalid)
        assertTrue((result as CombatSportResult.Invalid).issues.any {
            it.code == CombatSportIssueCode.AGGREGATE_OVERFLOW
        })
        assertNull(boxingEngine.plannedDurationSeconds(overflow))
    }

    @Test
    fun roundsMetricsKeepPartialAndInvalidatedSeparateAndDoNotRebuildSessionDuration() {
        val result = result(
            rounds = listOf(
                round(
                    1, CombatRoundStatus.COMPLETED, 180,
                    CombatActionCounter(CombatCounterKind.TECHNIQUE, "jab", 4, 3),
                ),
                round(
                    2, CombatRoundStatus.PARTIAL, 80,
                    CombatActionCounter(CombatCounterKind.TECHNIQUE, "jab", 2, 1),
                ),
                round(
                    3, CombatRoundStatus.INVALIDATED, null,
                    CombatActionCounter(CombatCounterKind.TECHNIQUE, "jab", 9, 8),
                ),
            ),
            actualDuration = 420,
            rpe = CombatRpeRating(
                value = 7.0,
                unit = CombatRpeUnit.SCALE_POINT,
                scaleId = "CR10:user-declared",
                source = CombatRpeSource.ATHLETE_REPORTED,
            ),
        )

        val metrics = valid(boxingEngine.calculateMetrics(result))
        val block = metrics.blocks.single()
        assertEquals(1, block.completedRounds)
        assertEquals(1, block.partialRounds)
        assertEquals(1, block.invalidatedRounds)
        assertEquals(260L, block.observedRoundWorkSeconds)
        val jab = block.counters.single()
        assertEquals(6L, jab.attemptedCount)
        assertEquals(4L, jab.successfulCount)
        assertEquals(2, jab.contributingRounds)
        assertEquals(0, jab.performedRoundsMissingCounter)
        // The direct session timer remains 420 seconds; 260 seconds of work are not added again.
        assertEquals(420L, metrics.actualDurationSeconds)
        val load = (boxingEngine.calculateSessionRpeLoad(result) as CombatSessionRpeLoadResult.Available).load
        assertEquals(49.0, load.value, 0.000001)
        assertEquals(CombatRpeLoadUnit.RPE_SCALE_POINT_MINUTES, load.unit)
        assertEquals(CombatObservedDurationSource.SESSION_TIMER, load.durationSource)
        assertEquals(CombatRpeSource.ATHLETE_REPORTED, load.rpeSource)
    }

    @Test
    fun countersRequireSuccessNoGreaterThanAttemptsAndDoNotTreatMissingAsZero() {
        val invalid = result(rounds = listOf(
            round(1, CombatRoundStatus.COMPLETED, 30,
                CombatActionCounter(CombatCounterKind.POSITION, "guard-entry", 1, 2)),
        ))
        assertTrue(boxingEngine.validateResult(invalid).any {
            it.code == CombatSportIssueCode.SUCCESS_EXCEEDS_ATTEMPTS
        })
        assertTrue(boxingEngine.calculateMetrics(invalid) is CombatSportResult.Invalid)

        val incomplete = result(rounds = listOf(
            round(1, CombatRoundStatus.COMPLETED, 30,
                CombatActionCounter(CombatCounterKind.TECHNIQUE, "jab", 0, 0)),
            round(2, CombatRoundStatus.PARTIAL, 10),
        ))
        val summary = valid(boxingEngine.calculateMetrics(incomplete)).blocks.single().counters.single()
        assertNull(summary.attemptedCount)
        assertNull(summary.successfulCount)
        assertEquals(1, summary.contributingRounds)
        assertEquals(1, summary.performedRoundsMissingCounter)

        val explicitZero = result(rounds = listOf(
            round(1, CombatRoundStatus.COMPLETED, 30,
                CombatActionCounter(CombatCounterKind.TECHNIQUE, "jab", 0, 0)),
        ))
        val zero = valid(boxingEngine.calculateMetrics(explicitZero)).blocks.single().counters.single()
        assertEquals(0L, zero.attemptedCount)
        assertEquals(0L, zero.successfulCount)
    }

    @Test
    fun durationAndRpeMustBeExplicitForSessionLoadAndInvalidMetadataIsTyped() {
        val noInputs = result(rounds = listOf(round(1, CombatRoundStatus.COMPLETED, 30)), actualDuration = null)
        val unavailable = boxingEngine.calculateSessionRpeLoad(noInputs) as CombatSessionRpeLoadResult.Unavailable
        assertEquals(
            setOf(CombatSessionLoadMissingInput.ACTUAL_DURATION, CombatSessionLoadMissingInput.SESSION_RPE),
            unavailable.missingInputs,
        )

        val onlyRpe = noInputs.copy(sessionRpe = CombatRpeRating(
            5.0,
            unit = CombatRpeUnit.SCALE_POINT,
            scaleId = "custom",
            source = CombatRpeSource.COACH_RECORDED,
        ))
        assertEquals(
            setOf(CombatSessionLoadMissingInput.ACTUAL_DURATION),
            (boxingEngine.calculateSessionRpeLoad(onlyRpe) as CombatSessionRpeLoadResult.Unavailable).missingInputs,
        )

        val invalid = noInputs.copy(
            actualDurationSeconds = 0,
            actualDurationSource = null,
            sessionRpe = CombatRpeRating(
                Double.NaN,
                unit = CombatRpeUnit.SCALE_POINT,
                scaleId = "",
                source = CombatRpeSource.ATHLETE_REPORTED,
            ),
        )
        val issues = boxingEngine.validateResult(invalid)
        assertTrue(issues.any { it.path == "actualDurationSeconds" && it.code == CombatSportIssueCode.MUST_BE_POSITIVE })
        assertTrue(issues.any { it.path == "actualDurationSource" && it.code == CombatSportIssueCode.REQUIRED })
        assertTrue(issues.any { it.path == "sessionRpe.value" && it.code == CombatSportIssueCode.NON_FINITE })
        assertTrue(issues.any { it.path == "sessionRpe.scaleId" && it.code == CombatSportIssueCode.REQUIRED })
    }

    @Test
    fun serializedRpeWithoutUnitIsRejectedInsteadOfReceivingAnImplicitDefault() {
        val missingUnit = """{"value":5.0,"scaleId":"custom","source":"ATHLETE_REPORTED"}"""
        val failure = runCatching { Json.decodeFromString<CombatRpeRating>(missingUnit) }.exceptionOrNull()

        assertTrue(failure is SerializationException)
    }

    @Test
    fun comparisonRequiresExactDisciplineRuleVersionBlockAndContext() {
        val base = result(
            sport = CombatDiscipline.BJJ,
            blockType = CombatBlockType.POSITIONAL,
            rulesetId = "submission-only-custom",
            rulesetVersion = 3,
            protocolId = "guard-retention",
            contextId = "partner-level-1",
            rounds = listOf(round(1, CombatRoundStatus.COMPLETED, 40)),
        )
        val bjj = CombatSportEngine.forDiscipline(CombatDiscipline.BJJ)
        val sameKey = base.copy(blocks = listOf(base.blocks.single().copy(id = "another-id")))
        assertTrue(bjj.areComparable(base, "positional", sameKey, "another-id"))
        val originalBlock = base.blocks.single()
        val mismatchedContexts = listOf(
            originalBlock.copy(rulesetId = "different-rule-set"),
            originalBlock.copy(rulesetVersion = 4),
            originalBlock.copy(type = CombatBlockType.SPARRING),
            originalBlock.copy(protocolId = "different-protocol"),
            originalBlock.copy(contextId = "different-gym-or-partner-context"),
            originalBlock.copy(partnerRole = CombatPartnerRole.COACH),
            originalBlock.copy(declaredContact = CombatDeclaredContact.FULL_CONTACT),
            originalBlock.copy(declaredIntensity = CombatDeclaredIntensity.HARD),
        )
        mismatchedContexts.forEach { block ->
            assertFalse(bjj.areComparable(base, "positional", base.copy(blocks = listOf(block)), "positional"))
        }
        val otherDiscipline = base.copy(sportId = CombatDiscipline.WRESTLING)
        assertFalse(bjj.areComparable(base, "positional", otherDiscipline, "positional"))
    }

    @Test
    fun campSparringExposureIsDescriptiveAndIncludesOnlyRecordedSparringRounds() {
        val sparringRounds = listOf(
            round(1, CombatRoundStatus.COMPLETED, 90),
            round(2, CombatRoundStatus.PARTIAL, 30),
            round(3, CombatRoundStatus.INVALIDATED, null),
        )
        val sparringBlock = result(
            sport = CombatDiscipline.MUAY_THAI,
            campWeek = 3,
            actualDuration = 240,
            rounds = sparringRounds,
            blockType = CombatBlockType.SPARRING,
        ).blocks.single().copy(id = "sparring")
        val bagBlock = result(
            sport = CombatDiscipline.MUAY_THAI,
            campWeek = 3,
            actualDuration = 240,
            rounds = listOf(round(1, CombatRoundStatus.COMPLETED, 60)),
            blockType = CombatBlockType.BAG,
        ).blocks.single().copy(id = "bag")
        val observation = result(
            sport = CombatDiscipline.MUAY_THAI,
            campWeek = 3,
            actualDuration = 240,
            rounds = sparringRounds,
            blockType = CombatBlockType.SPARRING,
        ).copy(blocks = listOf(sparringBlock, bagBlock))
        val exposures = valid(CombatSportEngine.forDiscipline(CombatDiscipline.MUAY_THAI).campSparringExposure(observation))
        assertEquals(1, exposures.size)
        assertEquals(3, exposures.single().campWeekNumber)
        assertEquals(2, exposures.single().observedNonInvalidatedRounds)
        assertEquals(120L, exposures.single().observedWorkSeconds)
        assertTrue(exposures.single().comparisonKey.discipline == CombatDiscipline.MUAY_THAI)

        val withoutSparring = observation.copy(blocks = observation.blocks.filter { it.type != CombatBlockType.SPARRING })
        assertTrue(valid(CombatSportEngine.forDiscipline(CombatDiscipline.MUAY_THAI)
            .campSparringExposure(withoutSparring)).isEmpty())
    }

    @Test
    fun registryRejectsInternalSportMismatchAndPlanRoundTrips() {
        val json = Json { encodeDefaults = true }
        val plan = CombatSessionPlan(
            sportId = CombatDiscipline.BOXING,
            blocks = listOf(blockPlan("bag", CombatRoundPrescription(2, 120, 30), CombatBlockType.BAG)),
        )
        assertEquals(plan, json.decodeFromString<CombatSessionPlan>(json.encodeToString(plan)))
        val registry = SportTrackingRegistry(CombatSportEngine.all)
        val document = SportTrackingDocument(
            id = "box-plan",
            engineId = boxingEngine.id,
            version = boxingEngine.version,
            sportId = SportId.BOXING.name,
            purpose = SportDocumentPurpose.PLAN.name,
            payload = json.encodeToJsonElement(plan).jsonObject,
        )
        assertEquals(SportDocumentValidation.Valid, registry.validate(document))
        assertEquals(270L, registry.plannedDurationSeconds(document))

        val forgedSport = plan.copy(sportId = CombatDiscipline.BJJ)
        val rejected = registry.validate(document.copy(payload = json.encodeToJsonElement(forgedSport).jsonObject))
        assertTrue(rejected is SportDocumentValidation.InvalidPayload)
        assertTrue((rejected as SportDocumentValidation.InvalidPayload).issues.any {
            it.code == CombatSportIssueCode.SPORT_MISMATCH.name
        })

        val envelopeMismatch = registry.validate(document.copy(sportId = SportId.BJJ.name))
        assertTrue(envelopeMismatch is SportDocumentValidation.InvalidMetadata)
        val resultJson = json.encodeToJsonElement(result(rounds = listOf(round(1, CombatRoundStatus.COMPLETED, 20)))).jsonObject
        assertTrue(boxingEngine.validate(resultJson, SportDocumentPurpose.RESULT).isEmpty())
    }

    private fun blockPlan(
        id: String,
        rounds: CombatRoundPrescription = CombatRoundPrescription(1, 60, 0),
        type: CombatBlockType = CombatBlockType.DRILL,
        declaredExternalDurationSeconds: Long? = null,
    ) = CombatTrainingBlockPlan(
        id = id,
        type = type,
        rulesetId = "user-rule-set",
        rulesetVersion = 1,
        protocolId = "authored-protocol",
        contextId = "gym-A",
        declaredContact = CombatDeclaredContact.CONTROLLED,
        declaredIntensity = CombatDeclaredIntensity.MODERATE,
        rounds = rounds,
        declaredExternalDurationSeconds = declaredExternalDurationSeconds,
    )

    private fun fixedClass(id: String, weekday: Int, minute: Int) = CombatFixedClassCommitment(
        id = id,
        classId = "boxing-fundamentals",
        weekdayIso = weekday,
        startMinuteLocal = minute,
        contextId = "gym-A",
        protocolId = "class-protocol",
        rulesetId = "none-training-context",
        rulesetVersion = 1,
    )

    private fun result(
        sport: CombatDiscipline = CombatDiscipline.BOXING,
        rounds: List<CombatRoundResult>,
        actualDuration: Long? = 300,
        rpe: CombatRpeRating? = null,
        blockType: CombatBlockType = CombatBlockType.DRILL,
        rulesetId: String = "user-rule-set",
        rulesetVersion: Int = 1,
        protocolId: String = "authored-protocol",
        contextId: String = "gym-A",
        campWeek: Int? = null,
    ) = CombatSessionResult(
        sportId = sport,
        blocks = listOf(
            CombatTrainingBlockResult(
                id = if (blockType == CombatBlockType.POSITIONAL) "positional" else "block",
                type = blockType,
                rulesetId = rulesetId,
                rulesetVersion = rulesetVersion,
                protocolId = protocolId,
                contextId = contextId,
                declaredContact = CombatDeclaredContact.CONTROLLED,
                declaredIntensity = CombatDeclaredIntensity.MODERATE,
                rounds = rounds,
            ),
        ),
        actualDurationSeconds = actualDuration,
        actualDurationSource = if (actualDuration == null) null else CombatObservedDurationSource.SESSION_TIMER,
        sessionRpe = rpe,
        campWeekNumber = campWeek,
    )

    private fun round(
        number: Int,
        status: CombatRoundStatus,
        workSeconds: Long?,
        vararg counters: CombatActionCounter,
    ) = CombatRoundResult(number, status, workSeconds, counters.toList())

    private fun <T> valid(result: CombatSportResult<T>): T {
        assertTrue("Expected valid result, got $result", result is CombatSportResult.Valid)
        return (result as CombatSportResult.Valid).value
    }
}


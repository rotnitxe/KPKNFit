package com.example.kpkn.domain.multisport.field

import com.example.kpkn.domain.multisport.SportId
import com.example.kpkn.domain.multisport.tracking.SportDocumentPurpose
import com.example.kpkn.domain.multisport.tracking.SportDocumentValidation
import com.example.kpkn.domain.multisport.tracking.SportTrackingDocument
import com.example.kpkn.domain.multisport.tracking.SportTrackingRegistry
import com.example.kpkn.domain.performance.lab.PerformanceLabContext
import com.example.kpkn.domain.performance.lab.PerformanceLabMark
import com.example.kpkn.domain.performance.lab.PerformanceLabMetric
import com.example.kpkn.domain.performance.lab.PerformanceLabProvenance
import com.example.kpkn.domain.performance.lab.PerformanceLabProtocol
import com.example.kpkn.domain.performance.lab.PerformanceLabTrialStatus
import com.example.kpkn.domain.performance.lab.PerformanceLabUnit
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

class FieldSportEngineTest {
    private val climbingEngine = FieldSportEngine.forDiscipline(FieldSportDiscipline.CLIMBING)
    private val sprintEngine = FieldSportEngine.forDiscipline(FieldSportDiscipline.SPRINT)
    private val jumpEngine = FieldSportEngine.forDiscipline(FieldSportDiscipline.JUMP)
    private val protocol = PerformanceLabProtocol("manual-field-test", 2, "Authored field protocol")

    @Test
    fun climbingKeepsRouteRevisionGradeSystemAndDeclaredAttemptOutcomesDistinct() {
        val route = route()
        val session = ClimbingSessionResult(
            sportId = FieldSportDiscipline.CLIMBING,
            blocks = listOf(
                ClimbingBlockResult(
                    id = "route-block",
                    type = ClimbingBlockType.ROUTE_ATTEMPTS,
                    route = route,
                    betaDisclosure = ClimbingBetaDisclosure.BETA_RECEIVED,
                    attempts = listOf(
                        ClimbingAttempt("a1", ClimbingAttemptStyle.REDPOINT, ClimbingAttemptOutcome.FALL, 42),
                        ClimbingAttempt("a2", ClimbingAttemptStyle.REDPOINT, ClimbingAttemptOutcome.SEND, 31),
                        ClimbingAttempt(
                            "a3", ClimbingAttemptStyle.REDPOINT, ClimbingAttemptOutcome.INVALIDATED,
                            actualWorkingSeconds = null, reason = "Timing record interrupted",
                        ),
                    ),
                ),
            ),
        )

        assertTrue(climbingEngine.validateResult(session).isEmpty())
        val summary = valid(climbingEngine.summarizeClimbingRoutes(session)).single()
        assertEquals(2, summary.tries)
        assertEquals(1, summary.sends)
        assertEquals(73L, summary.workingSeconds)
        assertEquals(1, summary.invalidatedAttempts)
        assertEquals("Font", summary.key?.gradeSystemId)
        assertTrue(climbingEngine.areClimbingSummariesComparable(summary, summary))

        val otherGradeSystem = session.copy(blocks = listOf(session.blocks.single().copy(
            route = route.copy(gradeSystemId = "V-scale", gradeValue = "V7"),
        )))
        val otherSummary = valid(climbingEngine.summarizeClimbingRoutes(otherGradeSystem)).single()
        assertFalse(climbingEngine.areClimbingSummariesComparable(summary, otherSummary))

        val otherRevision = session.copy(blocks = listOf(session.blocks.single().copy(
            route = route.copy(revision = "rev-3"),
        )))
        val revisionSummary = valid(climbingEngine.summarizeClimbingRoutes(otherRevision)).single()
        assertFalse(climbingEngine.areClimbingSummariesComparable(summary, revisionSummary))

        val differentContexts = listOf(
            route.copy(setting = ClimbingSetting.OUTDOOR),
            route.copy(wallAngleDegrees = 25.0),
            route.copy(holdStyle = "crimps"),
            route.copy(contextId = "gym-B-wall-1"),
            route.copy(exposureContext = "lead-fall-context"),
        )
        differentContexts.forEach { changedRoute ->
            val changed = session.copy(blocks = listOf(session.blocks.single().copy(route = changedRoute)))
            val changedSummary = valid(climbingEngine.summarizeClimbingRoutes(changed)).single()
            assertFalse(climbingEngine.areClimbingSummariesComparable(summary, changedSummary))
        }
        val changedBeta = session.copy(blocks = listOf(session.blocks.single().copy(
            betaDisclosure = ClimbingBetaDisclosure.BETA_SHARED,
        )))
        assertFalse(
            climbingEngine.areClimbingSummariesComparable(
                summary,
                valid(climbingEngine.summarizeClimbingRoutes(changedBeta)).single(),
            ),
        )

        val unknownWallOrBeta = session.copy(blocks = listOf(session.blocks.single().copy(
            route = route.copy(wallAngleDegrees = null),
            betaDisclosure = null,
            attempts = session.blocks.single().attempts.map { it.copy(betaDisclosure = null) },
        )))
        val unknownSummary = valid(climbingEngine.summarizeClimbingRoutes(unknownWallOrBeta)).first()
        assertNull(unknownSummary.key)
        assertFalse(climbingEngine.areClimbingSummariesComparable(summary, unknownSummary))
    }

    @Test
    fun climbingPlanDurationIsExplicitAndDoesNotAppendFinalRest() {
        val routePlan = ClimbingSessionPlan(
            sportId = FieldSportDiscipline.CLIMBING,
            blocks = listOf(
                ClimbingPlanBlock(
                    id = "boulders",
                    type = ClimbingBlockType.BOULDER_ATTEMPTS,
                    route = route(),
                    attemptStyle = ClimbingAttemptStyle.PROJECT,
                    plannedAttemptCount = 3,
                    betaDisclosure = ClimbingBetaDisclosure.UNKNOWN,
                    duration = FieldDurationPrescription(
                        repetitions = 3,
                        workSecondsPerRepetition = 20,
                        restSecondsBetweenRepetitions = 60,
                    ),
                ),
            ),
        )
        assertTrue(climbingEngine.validatePlan(routePlan).isEmpty())
        assertEquals(180L, climbingEngine.plannedDurationSeconds(routePlan))

        val untimed = routePlan.copy(blocks = listOf(routePlan.blocks.single().copy(duration = null)))
        assertEquals(
            FieldSportDurationResult.Unavailable(listOf("boulders")),
            climbingEngine.calculatePlanDuration(untimed),
        )
        assertNull(climbingEngine.plannedDurationSeconds(untimed))

        val external = ClimbingSessionPlan(
            sportId = FieldSportDiscipline.CLIMBING,
            blocks = listOf(ClimbingPlanBlock(
                id = "class",
                type = ClimbingBlockType.EXTERNAL_CLASS,
                duration = FieldDurationPrescription(declaredExternalDurationSeconds = 3600),
            )),
        )
        assertEquals(3600L, climbingEngine.plannedDurationSeconds(external))
    }

    @Test
    fun hangboardStoresLoadComponentsWithoutSubstitutingMissingBodyMass() {
        val prescription = HangboardPrescription(
            gripId = "half-crimp",
            edgeDepthMillimeters = 20.0,
            setCount = 3,
            holdSeconds = 10,
            restSecondsBetweenSets = 60,
            load = HangboardLoadComponents(
                bodyMass = null,
                addedLoad = ClimbingMassComponent(5.0, ClimbingMassUnit.KILOGRAM),
                assistance = ClimbingMassComponent(2.5, ClimbingMassUnit.KILOGRAM),
            ),
        )
        val plan = ClimbingSessionPlan(
            sportId = FieldSportDiscipline.CLIMBING,
            blocks = listOf(ClimbingPlanBlock(
                id = "hangs",
                type = ClimbingBlockType.HANGBOARD,
                hangboard = prescription,
                duration = FieldDurationPrescription(
                    repetitions = 3,
                    workSecondsPerRepetition = 10,
                    restSecondsBetweenRepetitions = 60,
                ),
            )),
        )

        assertTrue(climbingEngine.validatePlan(plan).isEmpty())
        assertEquals(150L, climbingEngine.plannedDurationSeconds(plan))
        val recorded = plan.blocks.single().hangboard!!
        assertNull(recorded.load.bodyMass)
        assertEquals(5.0, recorded.load.addedLoad!!.value, 0.0)
        assertEquals(2.5, recorded.load.assistance!!.value, 0.0)

        val invalidMass = plan.copy(blocks = listOf(plan.blocks.single().copy(
            hangboard = prescription.copy(load = HangboardLoadComponents(
                addedLoad = ClimbingMassComponent(Double.NaN, ClimbingMassUnit.POUND),
            )),
        )))
        assertTrue(climbingEngine.validatePlan(invalidMass).any {
            it.code == FieldSportIssueCode.NON_FINITE
        })

        val log = ClimbingSessionResult(sportId = FieldSportDiscipline.CLIMBING, blocks = listOf(ClimbingBlockResult(
            id = "hangs-log",
            type = ClimbingBlockType.HANGBOARD,
            hangboard = prescription,
            hangboardSets = listOf(HangboardSetResult(
                setNumber = 1,
                status = PerformanceLabTrialStatus.VALID,
                actualHangSeconds = 9,
                actualRestSecondsAfter = 65,
                load = prescription.load,
            ), HangboardSetResult(
                setNumber = 2,
                status = PerformanceLabTrialStatus.FAILED,
                actualHangSeconds = 0,
                load = prescription.load,
            )),
        )))
        assertTrue(climbingEngine.validateResult(log).isEmpty())
        assertEquals(65L, log.blocks.single().hangboardSets.first().actualRestSecondsAfter)
        assertNull(log.blocks.single().hangboardSets.first().load.bodyMass)
        assertEquals(0L, log.blocks.single().hangboardSets.last().actualHangSeconds)
        assertNull(log.blocks.single().hangboardSets.last().actualRestSecondsAfter)
    }

    @Test
    fun sprintSummariesUseOnlyManualMarksAndKeepProtocolContextAndWindSeparated() {
        val context = sprintContext(
            SprintStartType.BLOCKS,
            surface = "track-A",
            method = "hand-timing",
            windSpeed = 0.0,
        )
        val repetitions = listOf(
            sprintRep("rep-1", 10.05, context = context, wind = 0.0, splits = listOf(
                split("s5-1", 5.0, "split-5m", 4.0, context),
                split("s10-1", 10.0, "split-10m", 7.0, context),
                split("s30-1", 30.0, "split-30m", 10.05, context),
            ), splitMode = SprintSplitTimeMode.CUMULATIVE_ELAPSED),
            sprintRep("rep-2", 9.90, context = context, wind = 0.0),
            sprintRep(
                "rep-manual-measured", 9.85, context = context, wind = 0.0,
                provenance = PerformanceLabProvenance.MANUAL_MEASURED,
            ),
            sprintRep(
                "rep-video", 9.0, context = context,
                wind = 0.0,
                provenance = PerformanceLabProvenance.VIDEO_ESTIMATE,
            ),
            sprintRep("rep-unknown-wind", 9.1, context = sprintContext(
                SprintStartType.BLOCKS, surface = "track-A", method = "hand-timing",
            )),
            sprintRep(
                "rep-wind", 9.80,
                context = sprintContext(
                    SprintStartType.BLOCKS, surface = "track-A", method = "hand-timing",
                    windSpeed = 1.2, windDirection = SprintWindDirection.TAILWIND,
                ),
                wind = 1.2,
                direction = SprintWindDirection.TAILWIND,
            ),
        )
        val result = SprintSessionResult(
            sportId = FieldSportDiscipline.SPRINT,
            blocks = listOf(SprintBlockResult("30m-block", repetitions)),
        )

        assertTrue(sprintEngine.validateResult(result).isEmpty())
        val summaries = valid(sprintEngine.summarizeManualSprintResults(result))
        assertEquals(3, summaries.size)
        val calmEstimate = summaries.single {
            it.windSpeedMetersPerSecond == 0.0 && it.markSummary.provenance == PerformanceLabProvenance.MANUAL_ESTIMATE
        }
        assertEquals(2, calmEstimate.markSummary.sampleCount)
        assertEquals(9.90, calmEstimate.markSummary.bestValue!!, 0.000001)
        val calmMeasured = summaries.single {
            it.windSpeedMetersPerSecond == 0.0 && it.markSummary.provenance == PerformanceLabProvenance.MANUAL_MEASURED
        }
        assertEquals(1, calmMeasured.markSummary.sampleCount)
        assertEquals(9.85, calmMeasured.markSummary.bestValue!!, 0.000001)
        val windy = summaries.single { it.windSpeedMetersPerSecond == 1.2 }
        assertEquals(1, windy.markSummary.sampleCount)
        assertEquals(9.80, windy.markSummary.bestValue!!, 0.000001)
        assertFalse(summaries.any { it.markSummary.bestValue == 9.0 })
    }

    @Test
    fun sprintRejectsInconsistentSplitsAndPreservesRealRestSeparately() {
        val context = sprintContext(SprintStartType.STANDING, surface = "lane-1", method = "timing-gate")
        val validRep = sprintRep(
            "rep",
            12.0,
            context = context,
            actualRest = 90,
            splits = listOf(
                split("split-a", 5.0, "at-5m", 4.0, context),
                    split("split-b", 10.0, "at-10m", 9.0, context),
            ),
            splitMode = SprintSplitTimeMode.SEGMENT_ELAPSED,
            distance = 10.0,
                dimension = "10m-straight",
                start = SprintStartType.STANDING,
        )
        val inconsistent = SprintSessionResult(
            sportId = FieldSportDiscipline.SPRINT,
            blocks = listOf(SprintBlockResult("block", listOf(validRep))),
        )
        assertTrue(sprintEngine.validateResult(inconsistent).any {
            it.code == FieldSportIssueCode.INCONSISTENT_SPLITS
        })

        val corrected = inconsistent.copy(blocks = listOf(SprintBlockResult("block", listOf(validRep.copy(
            elapsedMark = validRep.elapsedMark!!.copy(value = 12.0),
            splits = validRep.splits.mapIndexed { index, item ->
                item.copy(mark = item.mark.copy(value = if (index == 0) 4.0 else 8.0))
            },
        )))))
        assertTrue(sprintEngine.validateResult(corrected).isEmpty())
        assertEquals(90L, corrected.blocks.single().repetitions.single().actualRestSecondsBefore)
    }

    @Test
    fun sprintSplitUnitConversionIsExplicitAndExactAcrossSecondsAndMilliseconds() {
        val context = sprintContext(SprintStartType.STANDING, surface = "lane-2", method = "timing-gate")
        val base = sprintRep(
            id = "mixed-units",
            elapsedSeconds = 12.0,
            context = context,
            distance = 10.0,
            dimension = "10m-straight",
            start = SprintStartType.STANDING,
            splitMode = SprintSplitTimeMode.SEGMENT_ELAPSED,
            splits = listOf(
                split("a", 5.0, "at-5m", 4.0, context),
                split("b", 10.0, "at-10m", 8.0, context),
            ),
        )
        val converted = base.copy(
            elapsedMark = base.elapsedMark!!.copy(unit = PerformanceLabUnit.MILLISECOND, value = 12_000.0),
            splits = listOf(
                base.splits[0].copy(mark = base.splits[0].mark.copy(
                    unit = PerformanceLabUnit.MILLISECOND, value = 4_000.0,
                )),
                base.splits[1],
            ),
        )
        val result = SprintSessionResult(
            sportId = FieldSportDiscipline.SPRINT,
            blocks = listOf(SprintBlockResult("block", listOf(converted))),
        )

        assertTrue(sprintEngine.validateResult(result).isEmpty())
    }

    @Test
    fun sprintDurationNeedsACompleteAuthoredPrescriptionAndNeverUsesBestTime() {
        val block = SprintPlanBlock(
            id = "sprints",
            dimensionKey = "30m-flying",
            distanceMeters = 30.0,
            repetitionCount = 3,
            startType = SprintStartType.FLYING,
            surfaceId = "track-A",
            timingMethod = "timing-gate",
            protocol = protocol,
            context = sprintContext(SprintStartType.FLYING, "track-A", "timing-gate"),
            duration = FieldDurationPrescription(
                repetitions = 3,
                workSecondsPerRepetition = 8,
                restSecondsBetweenRepetitions = 90,
            ),
        )
        val plan = SprintSessionPlan(sportId = FieldSportDiscipline.SPRINT, blocks = listOf(block))
        assertTrue(sprintEngine.validatePlan(plan).isEmpty())
        assertEquals(204L, sprintEngine.plannedDurationSeconds(plan))

        val unknown = plan.copy(blocks = listOf(block.copy(duration = null)))
        assertEquals(
            FieldSportDurationResult.Unavailable(listOf("sprints")),
            sprintEngine.calculatePlanDuration(unknown),
        )
        assertNull(sprintEngine.plannedDurationSeconds(unknown))

        val mismatch = plan.copy(blocks = listOf(block.copy(
            splitDistances = listOf(SprintSplitPrescription(20.0, "at-20m")),
        )))
        assertTrue(sprintEngine.validatePlan(mismatch).any {
            it.code == FieldSportIssueCode.INCONSISTENT_SPLITS
        })

        val overflow = plan.copy(blocks = listOf(block.copy(
            repetitionCount = Int.MAX_VALUE,
            duration = FieldDurationPrescription(
                repetitions = Int.MAX_VALUE,
                workSecondsPerRepetition = Long.MAX_VALUE,
                restSecondsBetweenRepetitions = 0,
            ),
        )))
        val overflowResult = sprintEngine.calculatePlanDuration(overflow)
        assertTrue(overflowResult is FieldSportDurationResult.Invalid)
        assertTrue((overflowResult as FieldSportDurationResult.Invalid).issues.any {
            it.code == FieldSportIssueCode.AGGREGATE_OVERFLOW
        })
    }

    @Test
    fun jumpUsesPerformanceLabForHeightDistanceAndContactMarksWithoutCombiningTests() {
        val cmjContext = jumpContext(JumpTestType.CMJ, JumpApproach.STANDING, "cmj")
        val cmjMark = perfMark(
            id = "cmj-height",
            metric = PerformanceLabMetric.JUMP_HEIGHT,
            dimension = "cmj",
            unit = PerformanceLabUnit.CENTIMETER,
            value = 42.0,
            context = cmjContext,
        )
        val horizontalContext = jumpContext(JumpTestType.HORIZONTAL, JumpApproach.APPROACH, "long-jump")
        val distanceMark = perfMark(
            id = "horizontal-distance",
            metric = PerformanceLabMetric.JUMP_DISTANCE,
            dimension = "long-jump",
            unit = PerformanceLabUnit.METER,
            value = 2.31,
            context = horizontalContext,
        )
        val result = JumpSessionResult(
            sportId = FieldSportDiscipline.JUMP,
            blocks = listOf(
                JumpBlockResult(
                    "cmj",
                    listOf(jumpAttempt("cmj-1", JumpTestType.CMJ, "cmj", cmjContext, listOf(cmjMark))),
                ),
                JumpBlockResult(
                    "horizontal",
                    listOf(jumpAttempt(
                        "horizontal-1", JumpTestType.HORIZONTAL, "long-jump",
                        horizontalContext, listOf(distanceMark), approach = JumpApproach.APPROACH,
                    )),
                ),
            ),
        )

        assertTrue(jumpEngine.validateResult(result).isEmpty())
        val summaries = valid(jumpEngine.summarizeJumpMarks(result))
        assertEquals(2, summaries.size)
        assertTrue(summaries.any { it.metric == PerformanceLabMetric.JUMP_HEIGHT && it.bestValue == 42.0 })
        assertTrue(summaries.any { it.metric == PerformanceLabMetric.JUMP_DISTANCE && it.bestValue == 2.31 })
    }

    @Test
    fun jumpSeparatesFailedAndInvalidatedAttemptsAndRequiresExplicitDropContext() {
        val context = jumpContext(JumpTestType.DROP_JUMP, JumpApproach.DROP_FROM_BOX, "drop-jump", boxCm = 30.0)
        val height = perfMark(
            id = "dj-height",
            metric = PerformanceLabMetric.JUMP_HEIGHT,
            dimension = "drop-jump",
            unit = PerformanceLabUnit.CENTIMETER,
            value = 28.0,
            context = context,
            eventId = "event-1",
        )
        val contact = perfMark(
            id = "dj-contact",
            metric = PerformanceLabMetric.CONTACT_TIME,
            dimension = "drop-jump",
            unit = PerformanceLabUnit.MILLISECOND,
            value = 220.0,
            context = context,
            eventId = "event-1",
        )
        val result = JumpSessionResult(
            sportId = FieldSportDiscipline.JUMP,
            blocks = listOf(JumpBlockResult(
                "drop-block",
                listOf(
                    jumpAttempt(
                        "valid", JumpTestType.DROP_JUMP, "drop-jump", context, listOf(height, contact),
                        approach = JumpApproach.DROP_FROM_BOX, boxCm = 30.0,
                    ),
                    jumpAttempt(
                        "failed", JumpTestType.DROP_JUMP, "drop-jump", context, emptyList(),
                        status = PerformanceLabTrialStatus.FAILED,
                        approach = JumpApproach.DROP_FROM_BOX, boxCm = 30.0,
                    ),
                    jumpAttempt(
                        "invalid", JumpTestType.DROP_JUMP, "drop-jump", context, emptyList(),
                        status = PerformanceLabTrialStatus.INVALIDATED,
                        approach = JumpApproach.DROP_FROM_BOX, boxCm = 30.0,
                        reason = "Marking interrupted",
                    ),
                ),
            )),
        )
        assertTrue(jumpEngine.validateResult(result).isEmpty())
        assertEquals(
            JumpAttemptCounts(1, 1, 1),
            valid(jumpEngine.countJumpAttempts(result)).single(),
        )

        val wrongContext = result.copy(blocks = listOf(result.blocks.single().copy(
            attempts = listOf(result.blocks.single().attempts.first().copy(
                context = context.copy(conditions = context.conditions + ("field.jump.box_height_cm" to "25")),
            )),
        )))
        assertTrue(jumpEngine.validateResult(wrongContext).any {
            it.code == FieldSportIssueCode.PERFORMANCE_MARK_MISMATCH
        })
    }

    @Test
    fun registryBindsPayloadAndEnvelopeSportAndPreservesTypedPlanCodec() {
        val json = Json { encodeDefaults = true }
        val plan = JumpSessionPlan(sportId = FieldSportDiscipline.JUMP, blocks = listOf(
            JumpPlanBlock(
                id = "cmj",
                testType = JumpTestType.CMJ,
                dimensionKey = "cmj",
                approach = JumpApproach.STANDING,
                protocol = protocol,
                context = jumpContext(JumpTestType.CMJ, JumpApproach.STANDING, "cmj"),
                plannedAttemptCount = 3,
            duration = FieldDurationPrescription(
                repetitions = 3,
                workSecondsPerRepetition = 5,
                restSecondsBetweenRepetitions = 30,
            ),
            ),
        ))
        val encoded = json.encodeToJsonElement(plan).jsonObject
        val registry = SportTrackingRegistry(FieldSportEngine.all)
        val document = SportTrackingDocument(
            id = "jump-plan",
            engineId = jumpEngine.id,
            version = jumpEngine.version,
            sportId = SportId.JUMP.name,
            purpose = SportDocumentPurpose.PLAN.name,
            payload = encoded,
        )

        assertEquals(plan, json.decodeFromString<JumpSessionPlan>(json.encodeToString(plan)))
        assertEquals(SportDocumentValidation.Valid, registry.validate(document))
        assertEquals(75L, registry.plannedDurationSeconds(document))

        val payloadMismatch = registry.validate(document.copy(
            payload = json.encodeToJsonElement(plan.copy(sportId = FieldSportDiscipline.SPRINT)).jsonObject,
        ))
        assertTrue(payloadMismatch is SportDocumentValidation.InvalidPayload)
        assertTrue((payloadMismatch as SportDocumentValidation.InvalidPayload).issues.any {
            it.code == FieldSportIssueCode.SPORT_MISMATCH.name
        })
        val missingInternalSport = JsonObject(encoded.jsonObject.filterKeys { it != "sportId" })
        assertTrue(registry.validate(document.copy(payload = missingInternalSport)) is SportDocumentValidation.InvalidPayload)
        assertTrue(registry.validate(document.copy(sportId = SportId.SPRINT.name)) is SportDocumentValidation.InvalidMetadata)
    }

    @Test
    fun malformedProtocolAndZeroMeasurementsAreRejectedNotCoerced() {
        val context = jumpContext(JumpTestType.CMJ, JumpApproach.STANDING, "cmj")
        val zeroMark = perfMark(
            id = "zero",
            metric = PerformanceLabMetric.JUMP_HEIGHT,
            dimension = "cmj",
            unit = PerformanceLabUnit.CENTIMETER,
            value = 0.0,
            context = context,
        )
        val invalid = JumpSessionResult(sportId = FieldSportDiscipline.JUMP, blocks = listOf(JumpBlockResult(
            "cmj",
            listOf(jumpAttempt("attempt", JumpTestType.CMJ, "cmj", context, listOf(zeroMark))),
        )))
        val issues = jumpEngine.validateResult(invalid)
        assertTrue(issues.any { it.code == FieldSportIssueCode.PERFORMANCE_MARK_INVALID })
        assertTrue(issues.any { it.detail == "POSITIVE_FINITE_OR_NULL_REQUIRED" })

        val malformedPlan = JumpSessionPlan(sportId = FieldSportDiscipline.JUMP, blocks = listOf(JumpPlanBlock(
            id = "bad",
            testType = JumpTestType.CMJ,
            dimensionKey = "cmj",
            approach = JumpApproach.STANDING,
            protocol = protocol.copy(version = 0),
            context = context,
            plannedAttemptCount = 0,
        )))
        assertTrue(jumpEngine.validatePlan(malformedPlan).any {
            it.code == FieldSportIssueCode.MUST_BE_POSITIVE
        })
    }

    private fun route(
        routeId: String = "route-7",
        revision: String = "rev-2",
        gradeSystemId: String = "Font",
    ) = ClimbingRouteContext(
        routeId = routeId,
        revision = revision,
        gradeSystemId = gradeSystemId,
        gradeValue = "7A",
        setting = ClimbingSetting.INDOOR,
        wallAngleDegrees = 15.0,
        holdStyle = "slopers",
        contextId = "gym-A-wall-3",
        exposureContext = "top-rope-belay",
    )

    private fun sprintContext(
        start: SprintStartType,
        surface: String,
        method: String,
        windSpeed: Double? = null,
        windDirection: SprintWindDirection? = null,
    ) = PerformanceLabContext(
        instrument = "timing-gates",
        surface = surface,
        method = method,
        conditions = mapOf(
            "field.sprint.start_type" to start.name,
            "field.sprint.wind_speed_mps" to (windSpeed?.let(::decimalText) ?: "unknown"),
            "field.sprint.wind_direction" to when {
                windSpeed == null -> "unknown"
                windSpeed == 0.0 && windDirection == null -> "not_applicable"
                else -> windDirection?.name ?: "missing"
            },
        ),
    )

    private fun sprintRep(
        id: String,
        elapsedSeconds: Double,
        context: PerformanceLabContext,
        distance: Double = 30.0,
        dimension: String = "30m-flying",
        start: SprintStartType = SprintStartType.BLOCKS,
        wind: Double? = null,
        direction: SprintWindDirection? = null,
        provenance: PerformanceLabProvenance = PerformanceLabProvenance.MANUAL_ESTIMATE,
        splits: List<SprintSplitResult> = emptyList(),
        splitMode: SprintSplitTimeMode? = null,
        actualRest: Long? = null,
    ) = SprintRepResult(
        id = id,
        status = PerformanceLabTrialStatus.VALID,
        dimensionKey = dimension,
        distanceMeters = distance,
        startType = start,
        surfaceId = context.surface,
        timingMethod = context.method,
        protocol = protocol,
        context = context,
        windSpeedMetersPerSecond = wind,
        windDirection = direction,
        elapsedMark = perfMark(
            id = "$id-elapsed",
            metric = PerformanceLabMetric.SPRINT_TIME,
            dimension = dimension,
            unit = PerformanceLabUnit.SECOND,
            value = elapsedSeconds,
            context = context,
            provenance = provenance,
        ),
        splitTimeMode = splitMode,
        splits = splits,
        actualRestSecondsBefore = actualRest,
    )

    private fun split(
        id: String,
        distance: Double,
        dimension: String,
        elapsed: Double,
        context: PerformanceLabContext,
    ) = SprintSplitResult(
        cumulativeDistanceMeters = distance,
        dimensionKey = dimension,
        mark = perfMark(
            id = id,
            metric = PerformanceLabMetric.SPLIT_TIME,
            dimension = dimension,
            unit = PerformanceLabUnit.SECOND,
            value = elapsed,
            context = context,
        ),
    )

    private fun jumpContext(
        type: JumpTestType,
        approach: JumpApproach,
        dimension: String,
        boxCm: Double? = null,
    ) = PerformanceLabContext(
        instrument = "force-platform-or-manual",
        surface = "jump-mat",
        method = "declared-protocol-method",
        conditions = mapOf(
            "field.jump.test_type" to type.name,
            "field.jump.approach" to approach.name,
            "field.jump.box_height_cm" to (boxCm?.let(::decimalText) ?: "not_applicable"),
            "field.jump.dimension" to dimension,
            "field.jump.custom_test_id" to "not_applicable",
        ),
    )

    private fun jumpAttempt(
        id: String,
        type: JumpTestType,
        dimension: String,
        context: PerformanceLabContext,
        marks: List<PerformanceLabMark>,
        status: PerformanceLabTrialStatus = PerformanceLabTrialStatus.VALID,
        approach: JumpApproach = JumpApproach.STANDING,
        boxCm: Double? = null,
        reason: String? = null,
    ) = JumpAttemptResult(
        id = id,
        testType = type,
        dimensionKey = dimension,
        approach = approach,
        boxHeightCentimeters = boxCm,
        protocol = protocol,
        context = context,
        status = status,
        marks = marks,
        reason = reason,
    )

    private fun perfMark(
        id: String,
        metric: PerformanceLabMetric,
        dimension: String,
        unit: PerformanceLabUnit,
        value: Double?,
        context: PerformanceLabContext,
        provenance: PerformanceLabProvenance = PerformanceLabProvenance.INSTRUMENT_MEASURED,
        status: PerformanceLabTrialStatus = PerformanceLabTrialStatus.VALID,
        eventId: String? = null,
    ) = PerformanceLabMark(
        id = id,
        protocol = protocol,
        metric = metric,
        dimension = dimension,
        unit = unit,
        value = value,
        provenance = provenance,
        context = context,
        status = status,
        eventId = eventId,
        reason = if (status == PerformanceLabTrialStatus.INVALIDATED) "invalidated" else null,
    )

    private fun decimalText(value: Double) =
        java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

    private fun <T> valid(result: FieldSportOperation<T>): T {
        assertTrue("Expected Success, got $result", result is FieldSportOperation.Success)
        return (result as FieldSportOperation.Success).value
    }
}


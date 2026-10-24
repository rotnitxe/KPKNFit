package com.example.kpkn.domain.multisport.functional

import com.example.kpkn.domain.multisport.SportId
import com.example.kpkn.domain.multisport.tracking.SportDocumentPurpose
import com.example.kpkn.domain.multisport.tracking.SportDocumentValidation
import com.example.kpkn.domain.multisport.tracking.SportTrackingDocument
import com.example.kpkn.domain.multisport.tracking.SportTrackingRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FunctionalSportEngineTest {
    private val reps = FunctionalQuantityUnit.REPETITIONS
    private val scaling = FunctionalScalingSnapshot("custom", "Custom", mapOf("load" to "user declared"))
    private val targets = listOf(
        FunctionalMovementTarget("burpees", "Burpees", FunctionalQuantity(10.0, reps)),
        FunctionalMovementTarget("wallballs", "Wall balls", FunctionalQuantity(15.0, reps),
            FunctionalLoad(6.0, FunctionalLoadUnit.KILOGRAM, "medicine ball")),
    )

    private fun crossfitDefinition(
        format: FunctionalWorkoutFormat = FunctionalWorkoutFormat.FOR_TIME,
        duration: Long? = 900L,
        targetsValue: List<FunctionalMovementTarget> = targets,
        cap: Long? = null,
        emom: List<FunctionalEmomWindowPlan> = emptyList(),
        intervals: List<FunctionalIntervalPlan> = emptyList(),
        scalingValue: FunctionalScalingSnapshot = scaling,
    ) = FunctionalWorkoutDefinition(
        sportId = SportId.CROSSFIT,
        workoutId = "wod-001",
        workoutRevision = "rev-1",
        title = "Manual WOD",
        format = format,
        declaredDurationSeconds = duration,
        scaling = scalingValue,
        targetSequence = if (format == FunctionalWorkoutFormat.FOR_TIME || format == FunctionalWorkoutFormat.AMRAP) targetsValue else emptyList(),
        timeCapMillis = cap,
        emomWindows = emom,
        intervalPlan = intervals,
    )

    private fun hyroxDefinition() = FunctionalWorkoutDefinition(
        sportId = SportId.HYROX,
        workoutId = "race-001",
        workoutRevision = "rules-snapshot-a",
        title = "Local simulation",
        format = FunctionalWorkoutFormat.HYROX,
        scaling = FunctionalScalingSnapshot("open-custom", "Open custom", mapOf("sled" to "local setup")),
        division = FunctionalDivisionSnapshot("open", "Open", sexCategory = "women"),
        ruleset = FunctionalRulesetSnapshot("local-rules", 3, "Local custom rules", custom = true),
        hyroxSegments = listOf(
            FunctionalHyroxSegment(
                "run-1", FunctionalHyroxSegmentKind.RUN, "Run segment",
                target = FunctionalQuantity(1.0, FunctionalQuantityUnit.KILOMETER),
            ),
            FunctionalHyroxSegment(
                "sled", FunctionalHyroxSegmentKind.STATION, "Sled station",
                target = FunctionalQuantity(50.0, reps),
                prescribedLoad = FunctionalLoad(100.0, FunctionalLoadUnit.KILOGRAM, "sled load"),
            ),
            FunctionalHyroxSegment("transition-1", FunctionalHyroxSegmentKind.TRANSITION, "Transition"),
        ),
    )

    @Test
    fun forTimeFinishAndCappedPartialAreSeparateAndPartialProgressCannotCompleteWorkout() {
        val definition = crossfitDefinition(cap = 600_000L)
        val capped = FunctionalSportPayload(
            definition = definition,
            result = FunctionalWorkoutResult(
                forTime = FunctionalForTimeOutcome(
                    cappedPartial = FunctionalForTimeCappedPartial(
                        elapsedMillis = 600_000L,
                        progress = listOf(
                            FunctionalTargetProgress("burpees", FunctionalQuantity(10.0, reps)),
                            FunctionalTargetProgress("wallballs", FunctionalQuantity(3.0, reps)),
                        ),
                    ),
                ),
            ),
        )

        assertTrue(FunctionalSportEngine.crossfit.validatePayload(capped, SportDocumentPurpose.RESULT).isEmpty())

        val fullRoundAsPartial = capped.copy(result = capped.result!!.copy(forTime = FunctionalForTimeOutcome(
            cappedPartial = FunctionalForTimeCappedPartial(
                600_000L,
                listOf(
                    FunctionalTargetProgress("burpees", FunctionalQuantity(10.0, reps)),
                    FunctionalTargetProgress("wallballs", FunctionalQuantity(15.0, reps)),
                ),
            ),
        )))
        assertTrue(FunctionalSportEngine.crossfit.validatePayload(fullRoundAsPartial, SportDocumentPurpose.RESULT)
            .any { it.code == "FULL_ROUND_MUST_NOT_BE_STORED_AS_PARTIAL" })

        val bothFinishAndPartial = capped.copy(result = capped.result!!.copy(forTime = FunctionalForTimeOutcome(
            finish = FunctionalForTimeFinish(500_000L),
            cappedPartial = capped.result!!.forTime!!.cappedPartial,
        )))
        assertTrue(FunctionalSportEngine.crossfit.validatePayload(bothFinishAndPartial, SportDocumentPurpose.RESULT)
            .any { it.code == "EXACTLY_ONE_COMPLETION_STATE_REQUIRED" })
    }

    @Test
    fun forTimeRejectsFinishOrCappedPartialBeyondDeclaredCap() {
        val definition = crossfitDefinition(cap = 600_000L)
        val finishBeyondCap = FunctionalSportPayload(
            definition,
            result = FunctionalWorkoutResult(
                forTime = FunctionalForTimeOutcome(finish = FunctionalForTimeFinish(900_000L)),
            ),
        )
        assertTrue(FunctionalSportEngine.crossfit.validatePayload(finishBeyondCap, SportDocumentPurpose.RESULT)
            .any { it.code == "FINISH_EXCEEDS_TIME_CAP" })

        val cappedPartialBeyondCap = FunctionalSportPayload(
            definition,
            result = FunctionalWorkoutResult(
                forTime = FunctionalForTimeOutcome(
                    cappedPartial = FunctionalForTimeCappedPartial(900_000L, emptyList()),
                ),
            ),
        )
        assertTrue(FunctionalSportEngine.crossfit.validatePayload(cappedPartialBeyondCap, SportDocumentPurpose.RESULT)
            .any { it.code == "ELAPSED_EXCEEDS_TIME_CAP" })

        val finishAtCap = finishBeyondCap.copy(result = FunctionalWorkoutResult(
            forTime = FunctionalForTimeOutcome(finish = FunctionalForTimeFinish(600_000L)),
        ))
        assertFalse(FunctionalSportEngine.crossfit.validatePayload(finishAtCap, SportDocumentPurpose.RESULT)
            .any { it.code == "FINISH_EXCEEDS_TIME_CAP" })
    }

    @Test
    fun amrapKeepsCompletedRoundsSeparateFromNextRoundRepsAndMissingIsNotZero() {
        val definition = crossfitDefinition(format = FunctionalWorkoutFormat.AMRAP, cap = 300_000L)
        val result = FunctionalWorkoutResult(
            amrap = FunctionalAmrapOutcome(
                completedRounds = 4,
                partialRoundProgress = listOf(
                    FunctionalTargetProgress("burpees", FunctionalQuantity(10.0, reps)),
                    FunctionalTargetProgress("wallballs", FunctionalQuantity(3.0, reps)),
                ),
            ),
        )
        val payload = FunctionalSportPayload(definition, result = result)

        assertTrue(FunctionalSportEngine.crossfit.validatePayload(payload, SportDocumentPurpose.RESULT).isEmpty())
        assertEquals(4, FunctionalSportEngine.crossfit.amrapScore(payload)!!.completedRounds)
        assertEquals(13.0, FunctionalSportEngine.crossfit.amrapScore(payload)!!.partialRepetitions!!, 0.0)

        val unknownPartial = payload.copy(result = result.copy(amrap = FunctionalAmrapOutcome(0, null)))
        val zeroPartial = payload.copy(result = result.copy(amrap = FunctionalAmrapOutcome(0, emptyList())))
        assertNull(FunctionalSportEngine.crossfit.amrapScore(unknownPartial)!!.partialRepetitions)
        assertEquals(0.0, FunctionalSportEngine.crossfit.amrapScore(zeroPartial)!!.partialRepetitions!!, 0.0)

        val skippedTarget = payload.copy(result = result.copy(amrap = FunctionalAmrapOutcome(4, listOf(
            FunctionalTargetProgress("wallballs", FunctionalQuantity(3.0, reps)),
        ))))
        assertTrue(FunctionalSportEngine.crossfit.validatePayload(skippedTarget, SportDocumentPurpose.RESULT)
            .any { it.code == "PROGRESS_MUST_FOLLOW_TARGET_ORDER" })
    }

    @Test
    fun amrapScoreReturnsTypedOverflowAndNeverExportsInfinitePartialRepetitions() {
        val hugeTargets = listOf(
            FunctionalMovementTarget("huge-a", "Huge A", FunctionalQuantity(Double.MAX_VALUE, reps)),
            FunctionalMovementTarget("huge-b", "Huge B", FunctionalQuantity(Double.MAX_VALUE, reps)),
        )
        val definition = crossfitDefinition(
            format = FunctionalWorkoutFormat.AMRAP,
            targetsValue = hugeTargets,
            cap = 300_000L,
        )
        val payload = FunctionalSportPayload(
            definition,
            result = FunctionalWorkoutResult(
                amrap = FunctionalAmrapOutcome(
                    completedRounds = 0,
                    partialRoundProgress = listOf(
                        FunctionalTargetProgress("huge-a", FunctionalQuantity(Double.MAX_VALUE, reps)),
                        FunctionalTargetProgress("huge-b", FunctionalQuantity(Double.MAX_VALUE / 2.0, reps)),
                    ),
                ),
            ),
        )

        assertTrue(FunctionalSportEngine.crossfit.validatePayload(payload, SportDocumentPurpose.RESULT).isEmpty())
        val scoreResult = FunctionalSportEngine.crossfit.calculateAmrapScore(payload)
        assertTrue(scoreResult is FunctionalSportEngine.AmrapScoreResult.Invalid)
        assertTrue((scoreResult as FunctionalSportEngine.AmrapScoreResult.Invalid).issues.any {
            it.code == "AGGREGATE_OVERFLOW"
        })
        assertNull(FunctionalSportEngine.crossfit.amrapScore(payload))
    }

    @Test
    fun emomReportsObservedAdherenceAndDoesNotTreatUnrecordedAsMissed() {
        val windows = listOf(0L, 60_000L, 120_000L).mapIndexed { index, start ->
            FunctionalEmomWindowPlan(
                id = "minute-${index + 1}",
                startsAtMillis = start,
                durationMillis = 40_000L,
                targets = listOf(FunctionalMovementTarget("squats-${index + 1}", "Squats", FunctionalQuantity(10.0, reps))),
            )
        }
        val definition = crossfitDefinition(
            format = FunctionalWorkoutFormat.EMOM,
            duration = null,
            targetsValue = emptyList(),
            emom = windows,
        )
        val payload = FunctionalSportPayload(
            definition,
            result = FunctionalWorkoutResult(
                emom = FunctionalEmomOutcome(listOf(
                    FunctionalEmomWindowResult("minute-1", FunctionalEmomWindowAdherence.COMPLETED),
                    FunctionalEmomWindowResult("minute-2", FunctionalEmomWindowAdherence.MISSED),
                    FunctionalEmomWindowResult("minute-3", FunctionalEmomWindowAdherence.UNRECORDED),
                )),
            ),
        )

        assertTrue(FunctionalSportEngine.crossfit.validatePayload(payload, SportDocumentPurpose.RESULT).isEmpty())
        val adherence = FunctionalSportEngine.crossfit.emomAdherence(payload)!!
        assertEquals(1, adherence.completedWindows)
        assertEquals(0, adherence.partialWindows)
        assertEquals(1, adherence.missedWindows)
        assertEquals(2, adherence.observedWindows)
        assertEquals(1, adherence.unrecordedWindows)
        assertEquals(3, adherence.totalWindows)
    }

    @Test
    fun emomRejectsWindowEndOverflowEvenForSingleWindow() {
        val definition = crossfitDefinition(
            format = FunctionalWorkoutFormat.EMOM,
            duration = null,
            targetsValue = emptyList(),
            emom = listOf(
                FunctionalEmomWindowPlan(
                    id = "last-window",
                    startsAtMillis = Long.MAX_VALUE - 10L,
                    durationMillis = 20L,
                    targets = listOf(FunctionalMovementTarget("reps", "Repetitions", FunctionalQuantity(1.0, reps))),
                ),
            ),
        )

        val issues = FunctionalSportEngine.crossfit.validatePayload(
            FunctionalSportPayload(definition), SportDocumentPurpose.PLAN,
        )
        assertTrue(issues.any { it.code == "WINDOW_END_OVERFLOW" })
    }

    @Test
    fun intervalWorkAndRestTimestampsMustBeOrderedAndExplicit() {
        val plan = listOf(
            FunctionalIntervalPlan("i1", workDurationMillis = 30_000L, restDurationMillis = 15_000L),
            FunctionalIntervalPlan("i2", workDurationMillis = 30_000L, restDurationMillis = 15_000L),
        )
        val definition = crossfitDefinition(
            format = FunctionalWorkoutFormat.INTERVALS,
            duration = null,
            targetsValue = emptyList(),
            intervals = plan,
        )
        val valid = FunctionalSportPayload(
            definition,
            result = FunctionalWorkoutResult(
                intervals = FunctionalIntervalsOutcome(listOf(
                    FunctionalIntervalObservation("i1", 1_000L, 31_000L, 31_000L, 46_000L),
                    FunctionalIntervalObservation("i2", 61_000L, 91_000L, 91_000L, 106_000L),
                )),
            ),
        )
        assertTrue(FunctionalSportEngine.crossfit.validatePayload(valid, SportDocumentPurpose.RESULT).isEmpty())

        val invalidOrder = valid.copy(result = valid.result!!.copy(intervals = FunctionalIntervalsOutcome(listOf(
            FunctionalIntervalObservation("i1", 1_000L, 31_000L, 30_000L, 46_000L),
            FunctionalIntervalObservation("i2", 40_000L, 55_000L, 55_000L, 70_000L),
        ))))
        val orderIssues = FunctionalSportEngine.crossfit.validatePayload(invalidOrder, SportDocumentPurpose.RESULT)
        assertTrue(orderIssues.any { it.code == "TIMESTAMP_ORDER_INVALID" })
        assertTrue(orderIssues.any { it.code == "INTERVAL_SEQUENCE_ORDER_INVALID" })

        val missing = valid.copy(result = valid.result!!.copy(intervals = FunctionalIntervalsOutcome(listOf(
            FunctionalIntervalObservation("i1"),
            FunctionalIntervalObservation("i2"),
        ))))
        assertTrue(FunctionalSportEngine.crossfit.validatePayload(missing, SportDocumentPurpose.RESULT)
            .any { it.code == "AT_LEAST_ONE_TIMESTAMP_REQUIRED" })
    }

    @Test
    fun hyroxKeepsOrderedSplitsPenaltiesAndComparisonSnapshotSeparate() {
        val definition = hyroxDefinition()
        val penalties = listOf(
            FunctionalPenalty("p1", "custom_extra_work", 5.0, FunctionalPenaltyUnit.SECOND, segmentId = "sled"),
            FunctionalPenalty("p2", "custom_rep_deduction", 1.0, FunctionalPenaltyUnit.REPETITIONS, segmentId = "sled"),
        )
        val payload = FunctionalSportPayload(
            definition,
            result = FunctionalWorkoutResult(
                hyrox = FunctionalHyroxOutcome(
                    status = FunctionalHyroxRaceStatus.FINISHED,
                    elapsedMillis = 550_000L,
                    segments = listOf(
                        FunctionalHyroxSegmentResult(
                            "run-1", FunctionalHyroxSegmentStatus.COMPLETED, splitMillis = 300_000L,
                            actualQuantity = FunctionalQuantity(1.0, FunctionalQuantityUnit.KILOMETER),
                        ),
                        FunctionalHyroxSegmentResult(
                            "sled", FunctionalHyroxSegmentStatus.COMPLETED, splitMillis = 240_000L,
                            actualQuantity = FunctionalQuantity(50.0, reps),
                        ),
                        FunctionalHyroxSegmentResult(
                            "transition-1", FunctionalHyroxSegmentStatus.COMPLETED, splitMillis = 10_000L,
                        ),
                    ),
                ),
                penalties = penalties,
            ),
        )

        assertTrue(FunctionalSportEngine.hyrox.validatePayload(payload, SportDocumentPurpose.RESULT).isEmpty())
        assertEquals(550_000L, payload.result!!.hyrox!!.elapsedMillis)
        assertEquals(penalties, payload.result!!.penalties)
        assertTrue(FunctionalSportEngine.hyrox.areComparable(definition, definition.copy(title = "Renamed display label")))
        assertFalse(FunctionalSportEngine.hyrox.areComparable(definition, definition.copy(workoutRevision = "rev-2")))
        assertFalse(FunctionalSportEngine.hyrox.areComparable(definition, definition.copy(
            scaling = definition.scaling.copy(id = "another-scale"),
        )))
        assertFalse(FunctionalSportEngine.hyrox.areComparable(definition, definition.copy(
            division = definition.division!!.copy(sexCategory = "men"),
        )))
        assertFalse(FunctionalSportEngine.hyrox.areComparable(definition, definition.copy(
            ruleset = definition.ruleset!!.copy(version = 4),
        )))

        val segmentQuantityBelowTarget = payload.copy(result = payload.result!!.copy(hyrox = payload.result!!.hyrox!!.copy(
            segments = payload.result!!.hyrox!!.segments.mapIndexed { index, segment ->
                if (index == 1) segment.copy(actualQuantity = FunctionalQuantity(1.0, reps)) else segment
            },
        )))
        assertTrue(FunctionalSportEngine.hyrox.validatePayload(segmentQuantityBelowTarget, SportDocumentPurpose.RESULT)
            .any { it.code == "COMPLETED_QUANTITY_BELOW_TARGET" })

        val segmentSplitsExceedElapsed = payload.copy(result = payload.result!!.copy(hyrox = payload.result!!.hyrox!!.copy(
            elapsedMillis = 549_999L,
        )))
        assertTrue(FunctionalSportEngine.hyrox.validatePayload(segmentSplitsExceedElapsed, SportDocumentPurpose.RESULT)
            .any { it.code == "SEGMENT_SPLITS_EXCEED_ELAPSED" })

        val splitTotalOverflow = payload.copy(result = payload.result!!.copy(hyrox = payload.result!!.hyrox!!.copy(
            elapsedMillis = Long.MAX_VALUE,
            segments = payload.result!!.hyrox!!.segments.mapIndexed { index, segment ->
                segment.copy(splitMillis = if (index == 0) Long.MAX_VALUE else 1L)
            },
        )))
        assertTrue(FunctionalSportEngine.hyrox.validatePayload(splitTotalOverflow, SportDocumentPurpose.RESULT)
            .any { it.code == "SEGMENT_SPLIT_TOTAL_OVERFLOW" })

        val reordered = payload.copy(result = payload.result!!.copy(hyrox = payload.result!!.hyrox!!.copy(
            segments = payload.result!!.hyrox!!.segments.reversed(),
        )))
        assertTrue(FunctionalSportEngine.hyrox.validatePayload(reordered, SportDocumentPurpose.RESULT)
            .any { it.code == "SEGMENT_ORDER_OR_ID_MISMATCH" })

        val invalidated = payload.copy(result = payload.result!!.copy(hyrox = FunctionalHyroxOutcome(
            status = FunctionalHyroxRaceStatus.PARTIAL,
            elapsedMillis = null,
            segments = listOf(
                FunctionalHyroxSegmentResult("run-1", FunctionalHyroxSegmentStatus.INVALIDATED),
                FunctionalHyroxSegmentResult("sled", FunctionalHyroxSegmentStatus.NOT_ATTEMPTED),
                FunctionalHyroxSegmentResult("transition-1", FunctionalHyroxSegmentStatus.UNRECORDED),
            ),
        )))
        assertTrue(FunctionalSportEngine.hyrox.validatePayload(invalidated, SportDocumentPurpose.RESULT)
            .any { it.code == "INVALIDATION_REASON_REQUIRED" })
    }

    @Test
    fun sportAndPurposeRegistrationAndDeclaredDurationAreExplicit() {
        val registry = SportTrackingRegistry(FunctionalSportEngine.registeredEngines)
        val definition = crossfitDefinition(
            format = FunctionalWorkoutFormat.AMRAP,
            duration = null,
            cap = 300_000L,
        )
        val planPayload = FunctionalSportPayload(definition)
        val plan = SportTrackingDocument(
            id = "plan-1",
            engineId = FunctionalSportEngine.crossfit.id,
            version = FunctionalSportEngine.crossfit.version,
            sportId = SportId.CROSSFIT.name,
            purpose = SportDocumentPurpose.PLAN.name,
            payload = planPayload.toJsonObject(),
        )
        assertEquals(SportDocumentValidation.Valid, registry.validate(plan))
        assertNull(registry.plannedDurationSeconds(plan))

        val declaredPlanPayload = planPayload.copy(definition = definition.copy(declaredDurationSeconds = 600L))
        val declaredPlan = plan.copy(payload = declaredPlanPayload.toJsonObject())
        assertEquals(600L, registry.plannedDurationSeconds(declaredPlan))

        val result = plan.copy(
            id = "result-1",
            purpose = SportDocumentPurpose.RESULT.name,
            payload = FunctionalSportPayload(
                definition = definition.copy(declaredDurationSeconds = 600L),
                result = FunctionalWorkoutResult(amrap = FunctionalAmrapOutcome(0, null)),
            ).toJsonObject(),
        )
        assertEquals(SportDocumentValidation.Valid, registry.validate(result))
        assertNull(registry.plannedDurationSeconds(result))

        val mismatchedSport = FunctionalSportEngine.crossfit.validatePayload(
            FunctionalSportPayload(hyroxDefinition()), SportDocumentPurpose.PLAN,
        )
        assertTrue(mismatchedSport.any { it.code == "ENGINE_SPORT_MISMATCH" })
    }

    @Test
    fun nonFiniteValuesAndUnspecifiedHyroxUnitsAreRejected() {
        val nonFiniteTarget = crossfitDefinition(targetsValue = listOf(
            FunctionalMovementTarget("bad", "Bad", FunctionalQuantity(Double.POSITIVE_INFINITY, reps)),
        ))
        assertTrue(FunctionalSportEngine.crossfit.validatePayload(FunctionalSportPayload(nonFiniteTarget), SportDocumentPurpose.PLAN)
            .any { it.code == "POSITIVE_FINITE_REQUIRED" })

        val badPenaltyPayload = FunctionalSportPayload(
            definition = crossfitDefinition(),
            result = FunctionalWorkoutResult(
                forTime = FunctionalForTimeOutcome(finish = FunctionalForTimeFinish(100_000L)),
                penalties = listOf(FunctionalPenalty("bad", "nan", Double.NaN, FunctionalPenaltyUnit.POINTS)),
            ),
        )
        assertTrue(FunctionalSportEngine.crossfit.validatePayload(badPenaltyPayload, SportDocumentPurpose.RESULT)
            .any { it.code == "POSITIVE_FINITE_REQUIRED" })

        val missingRunDistance = hyroxDefinition().copy(hyroxSegments = listOf(
            FunctionalHyroxSegment("run", FunctionalHyroxSegmentKind.RUN, "Missing distance"),
        ))
        assertTrue(FunctionalSportEngine.hyrox.validatePayload(
            FunctionalSportPayload(missingRunDistance), SportDocumentPurpose.PLAN,
        ).any { it.code == "RUN_DISTANCE_REQUIRED" })
    }
}

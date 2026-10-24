package com.example.kpkn.domain.multisport.games

import com.example.kpkn.domain.multisport.SportId
import com.example.kpkn.domain.multisport.tracking.SportDocumentPurpose
import com.example.kpkn.domain.multisport.tracking.SportDocumentValidation
import com.example.kpkn.domain.multisport.tracking.SportTrackingDocument
import com.example.kpkn.domain.multisport.tracking.SportTrackingRegistry
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSportEngineTest {
    private val ruleset = GameRulesetSnapshot(
        id = "local-rules",
        version = "rev-7",
        label = "Locally declared match rules",
        custom = true,
        parameters = mapOf("game-score" to "user-declared"),
    )
    private val racketFormat = GameFormatSnapshot(
        id = "format-best-of-three",
        version = "2",
        label = "Declared format",
        ruleset = ruleset,
        parameters = mapOf("sets" to "best-of-3", "advantage" to "no-ad"),
    )
    private val racketContext = RacketMatchContext(
        matchId = "match-1",
        sideAId = "athlete-a",
        sideBId = "athlete-b",
        subjectSide = GameRacketSide.A,
        subjectAthleteId = "athlete-a",
        courtId = "court-1",
        surface = "clay",
        conditions = mapOf("wind" to "manual note"),
    )

    private fun completedRacketResult() = RacketMatchResult(
        format = racketFormat,
        context = racketContext,
        status = GameMatchStatus.COMPLETED,
        winner = GameRacketSide.A,
        sets = listOf(
            RacketSetResult(1, GameSetStatus.COMPLETED, gamesA = 7, gamesB = 6, tieBreakPointsA = 7, tieBreakPointsB = 5),
            RacketSetResult(2, GameSetStatus.COMPLETED, gamesA = 6, gamesB = 4),
        ),
    )

    private val teamFormat = TeamFormatSnapshot(
        id = "custom-three-period",
        version = "a",
        label = "Custom format",
        custom = true,
        periods = listOf(
            TeamPeriodDefinition("p1", "Period one", 100_000L, GameClockBehavior.PAUSES_DURING_STOPPAGE),
        ),
    )
    private val teamProtocol = TeamActionProtocolSnapshot(
        id = "manual-actions",
        version = "1",
        label = "Manual action count",
        custom = true,
        actions = listOf(TeamActionDefinition("pass", "Pass", GameActionUnit.COUNT)),
    )
    private val teamContext = TeamSportContext(
        teamSportId = "user-defined-field-sport",
        teamId = "team-a",
        athleteId = "athlete-1",
        athleteRoleId = "role-keeper",
        sideId = "home",
        opponentId = "team-b",
        position = "left",
        surface = "synthetic",
        conditions = mapOf("weather" to "dry"),
    )

    private fun teamResult(period: TeamPeriodResult) = TeamSportResult(
        format = teamFormat,
        actionProtocol = teamProtocol,
        context = teamContext,
        periods = listOf(period),
        rpe = TeamRpe(6.0, "declared-0-10", "scale-point", GameRpeSource.ATHLETE),
    )

    @Test
    fun tennisAndPadelAcceptExplicitNonDefaultFormatsAndEnforceEnvelopeSport() {
        val payload = GameSportPayload(
            sportId = SportId.TENNIS,
            racketPlan = RacketPlan(racketFormat, racketContext, declaredDurationSeconds = 5_400L),
        )
        assertTrue(GameSportEngine.tennis.validatePayload(payload, SportDocumentPurpose.PLAN).isEmpty())
        assertEquals(5_400L, GameSportEngine.tennis.plannedDurationSeconds(
            Json.encodeToJsonElement(payload).jsonObject,
        )!!)
        assertTrue(GameSportEngine.tennis.validateForSport(
            Json.encodeToJsonElement(payload).jsonObject,
            SportDocumentPurpose.PLAN,
            SportId.PADEL,
        ).any { it.code == "ENGINE_SPORT_MISMATCH" })

        val padelPayload = payload.copy(sportId = SportId.PADEL)
        assertTrue(GameSportEngine.padel.validatePayload(padelPayload, SportDocumentPurpose.PLAN).isEmpty())
        assertNull(GameSportEngine.padel.plannedDurationSeconds(
            Json.encodeToJsonElement(padelPayload.copy(racketPlan = padelPayload.racketPlan!!.copy(declaredDurationSeconds = null)))
                .jsonObject,
        ))
    }

    @Test
    fun completedPartialRetiredAndWalkoverRemainDistinctStates() {
        val engine = GameSportEngine.tennis
        val completed = GameSportPayload(sportId = SportId.TENNIS, racketResult = completedRacketResult())
        assertTrue(engine.validatePayload(completed, SportDocumentPurpose.RESULT).isEmpty())

        val partial = completed.copy(racketResult = completed.racketResult!!.copy(
            status = GameMatchStatus.PARTIAL,
            winner = null,
            sets = listOf(RacketSetResult(1, GameSetStatus.PARTIAL, gamesA = 3, gamesB = 2)),
        ))
        assertTrue(engine.validatePayload(partial, SportDocumentPurpose.RESULT).isEmpty())

        val retired = completed.copy(racketResult = completed.racketResult!!.copy(
            status = GameMatchStatus.RETIRED,
            winner = null,
            retiringSide = GameRacketSide.B,
            sets = listOf(RacketSetResult(1, GameSetStatus.PARTIAL, gamesA = 4, gamesB = 3)),
        ))
        assertTrue(engine.validatePayload(retired, SportDocumentPurpose.RESULT).isEmpty())

        val invalidWalkover = completed.copy(racketResult = completed.racketResult!!.copy(
            status = GameMatchStatus.WALKOVER,
            sets = listOf(RacketSetResult(1, GameSetStatus.COMPLETED, 6, 0)),
        ))
        assertTrue(engine.validatePayload(invalidWalkover, SportDocumentPurpose.RESULT)
            .any { it.code == "WALKOVER_CANNOT_CONTAIN_PLAYED_EVIDENCE" })
    }

    @Test
    fun servePercentagesNeedExplicitKnownDenominatorsAndValidCounts() {
        val engine = GameSportEngine.tennis
        val partial = RacketSideStatistics(firstServeAttempts = 10, firstServesIn = 8)
        assertNull(engine.serveSummary(partial))

        val zeroSecondAttempts = RacketSideStatistics(
            servicePoints = 10,
            firstServeAttempts = 10,
            firstServesIn = 8,
            secondServeAttempts = 0,
            secondServesIn = 0,
            doubleFaults = 0,
        )
        val summary = engine.serveSummary(zeroSecondAttempts)!!
        assertEquals(80.0, summary.firstServeInPercent!!, 0.0)
        assertNull(summary.secondServeInPercent)

        val impossible = GameSportPayload(
            sportId = SportId.TENNIS,
            racketResult = completedRacketResult().copy(
                statisticsA = RacketSideStatistics(
                    servicePoints = 3,
                    firstServeAttempts = 4,
                    firstServesIn = 5,
                ),
            ),
        )
        val errors = engine.validatePayload(impossible, SportDocumentPurpose.RESULT)
        assertTrue(errors.any { it.code == "EXCEEDS_KNOWN_DENOMINATOR" })
    }

    @Test
    fun pointSequenceDerivesServeAndBreakpointOnlyFromCompleteExplicitEvidence() {
        val engine = GameSportEngine.tennis
        val points = RacketPointSequence(
            complete = true,
            points = listOf(
                RacketPointEvidence(1, 1, 1, GameRacketSide.A, GameRacketSide.A,
                    RacketServiceEvidence(listOf(GameServeAttemptOutcome.IN)), false),
                RacketPointEvidence(1, 1, 2, GameRacketSide.A, GameRacketSide.B,
                    RacketServiceEvidence(listOf(GameServeAttemptOutcome.FAULT, GameServeAttemptOutcome.FAULT)), true),
            ),
        )
        val serve = engine.serveSummary(points)!!
        assertEquals(2L, serve.firstServeAttempts)
        assertEquals(1L, serve.firstServesIn)
        assertEquals(1L, serve.secondServeAttempts)
        assertEquals(1L, serve.doubleFaults)
        assertEquals(RacketBreakpointSummary(1, 1), engine.breakpointSummary(points))

        assertNull(engine.serveSummary(points.copy(complete = false)))
        assertNull(engine.breakpointSummary(points.copy(points = points.points.map { it.copy(breakPointOpportunity = null) })))
        val badOrder = points.copy(points = points.points.reversed())
        val result = completedRacketResult().copy(pointSequence = badOrder)
        assertTrue(engine.validatePayload(
            GameSportPayload(sportId = SportId.TENNIS, racketResult = result),
            SportDocumentPurpose.RESULT,
        ).any { it.code == "POINT_ORDER_INVALID" })
    }

    @Test
    fun teamIntervalsRejectOverlapAndOnlyCompleteCaptureProducesTime() {
        val engine = GameSportEngine.teamSport
        val overlap = TeamPeriodResult(
            periodId = "p1",
            elapsedDurationMillis = 100_000L,
            clockBehavior = GameClockBehavior.PAUSES_DURING_STOPPAGE,
            clockPausedIntervals = listOf(GameTimeInterval(20_000L, 30_000L)),
            clockPauseCaptureComplete = true,
            presenceIntervals = listOf(
                TeamPresenceInterval(0L, 60_000L, GamePresence.ON_FIELD),
                TeamPresenceInterval(50_000L, 100_000L, GamePresence.OFF_FIELD),
            ),
            presenceSequenceComplete = true,
            actions = listOf(TeamActionCount("pass", observedCount = 4, attemptedCount = 3)),
        )
        val invalid = teamResult(overlap)
        val issues = engine.validatePayload(
            GameSportPayload(sportId = SportId.TEAM_SPORT, teamResult = invalid),
            SportDocumentPurpose.RESULT,
        )
        assertTrue(issues.any { it.code == "PRESENCE_INTERVAL_OVERLAP_OR_ORDER" })
        assertTrue(issues.any { it.code == "EXCEEDS_KNOWN_DENOMINATOR" })
        assertNull(engine.teamTimeSummary(invalid))

        val valid = teamResult(overlap.copy(
            presenceIntervals = listOf(
                TeamPresenceInterval(0L, 60_000L, GamePresence.ON_FIELD),
                TeamPresenceInterval(60_000L, 100_000L, GamePresence.OFF_FIELD),
            ),
            actions = listOf(TeamActionCount("pass", observedCount = 3, attemptedCount = 4, totalCount = 5)),
        ))
        assertTrue(engine.validatePayload(
            GameSportPayload(sportId = SportId.TEAM_SPORT, teamResult = valid),
            SportDocumentPurpose.RESULT,
        ).isEmpty())
        assertEquals(60_000L, engine.teamTimeSummary(valid)!!.actualOnFieldMillis!!)
        assertEquals(50_000L, engine.teamTimeSummary(valid)!!.activeClockOnFieldMillis!!)
    }

    @Test
    fun unknownClockOrPauseCaptureDoesNotInventActivePlayingTime() {
        val engine = GameSportEngine.teamSport
        val period = TeamPeriodResult(
            periodId = "p1",
            elapsedDurationMillis = 100_000L,
            clockBehavior = GameClockBehavior.PAUSES_DURING_STOPPAGE,
            clockPausedIntervals = null,
            clockPauseCaptureComplete = false,
            presenceIntervals = listOf(TeamPresenceInterval(0L, 100_000L, GamePresence.ON_FIELD)),
            presenceSequenceComplete = true,
        )
        val summary = engine.teamTimeSummary(teamResult(period))!!
        assertEquals(100_000L, summary.actualOnFieldMillis!!)
        assertNull(summary.activeClockOnFieldMillis)
    }

    @Test
    fun snapshotsAndPayloadJsonRoundTripThroughRegistry() {
        val engine = GameSportEngine.tennis
        val payload = GameSportPayload(
            sportId = SportId.TENNIS,
            racketPlan = RacketPlan(racketFormat, racketContext, declaredDurationSeconds = 3_000L),
        )
        val serialized = engine.toPayloadJson(payload)
        assertEquals(payload, engine.fromPayloadJson(serialized))
        assertEquals(payload, Json.decodeFromString<GameSportPayload>(serialized))

        val registry = SportTrackingRegistry(GameSportEngine.registeredEngines)
        val envelope = SportTrackingDocument(
            id = "doc-plan",
            engineId = engine.id,
            version = engine.version,
            sportId = SportId.TENNIS.name,
            purpose = SportDocumentPurpose.PLAN.name,
            payload = Json.encodeToJsonElement(payload).jsonObject,
        )
        assertEquals(SportDocumentValidation.Valid, registry.validate(envelope))
        assertEquals(3_000L, registry.plannedDurationSeconds(envelope)!!)

        val wrongEnvelope = envelope.copy(payload = Json.encodeToJsonElement(payload.copy(sportId = SportId.PADEL)).jsonObject)
        assertTrue(registry.validate(wrongEnvelope) is SportDocumentValidation.InvalidPayload)
    }

    @Test
    fun racketAndTeamObservationsHaveTheirOwnPurposeContracts() {
        val racketObservation = GameSportPayload(
            sportId = SportId.PADEL,
            racketObservation = RacketObservation(
                format = racketFormat,
                context = racketContext,
                statisticsA = RacketSideStatistics(servicePoints = 0L),
            ),
        )
        assertTrue(GameSportEngine.padel.validatePayload(racketObservation, SportDocumentPurpose.OBSERVATION).isEmpty())
        assertTrue(GameSportEngine.padel.validatePayload(racketObservation, SportDocumentPurpose.PLAN)
            .any { it.code == "PURPOSE_CONTENT_REQUIRED" })

        val teamObservation = GameSportPayload(
            sportId = SportId.TEAM_SPORT,
            teamObservation = TeamSportObservation(
                format = teamFormat,
                actionProtocol = teamProtocol,
                context = teamContext,
                observedAtEpochMillis = 123L,
                period = TeamPeriodResult(
                    periodId = "p1",
                    elapsedDurationMillis = 100_000L,
                    clockBehavior = GameClockBehavior.UNKNOWN,
                    actions = listOf(TeamActionCount("pass", observedCount = 2L)),
                ),
            ),
        )
        assertTrue(GameSportEngine.teamSport.validatePayload(teamObservation, SportDocumentPurpose.OBSERVATION).isEmpty())
    }

    @Test
    fun teamRpeMustBeFiniteAndSourceAndSnapshotAreRequired() {
        val engine = GameSportEngine.teamSport
        val invalid = TeamSportPlan(
            format = teamFormat.copy(periods = emptyList()),
            actionProtocol = teamProtocol,
            context = teamContext.copy(athleteRoleId = ""),
        )
        val planIssues = engine.validatePayload(
            GameSportPayload(sportId = SportId.TEAM_SPORT, teamPlan = invalid),
            SportDocumentPurpose.PLAN,
        )
        assertTrue(planIssues.any { it.code == "AT_LEAST_ONE_PERIOD_REQUIRED" })
        assertTrue(planIssues.any { it.path.endsWith("athleteRoleId") })

        val result = teamResult(
            TeamPeriodResult(
                periodId = "p1",
                elapsedDurationMillis = 100_000L,
                clockBehavior = GameClockBehavior.UNKNOWN,
                presenceIntervals = listOf(TeamPresenceInterval(0L, 100_000L, GamePresence.ON_FIELD)),
                presenceSequenceComplete = true,
            ),
        ).copy(rpe = TeamRpe(Double.NaN, "", "", GameRpeSource.OTHER))
        val issues = engine.validatePayload(
            GameSportPayload(sportId = SportId.TEAM_SPORT, teamResult = result),
            SportDocumentPurpose.RESULT,
        )
        assertTrue(issues.any { it.code == "FINITE_NON_NEGATIVE_REQUIRED" })
        assertTrue(issues.any { it.path.endsWith("scaleId") })
    }

    @Test
    fun comparisonRequiresMatchingContextAndFormatSnapshots() {
        val first = completedRacketResult()
        val otherMatchSameConditions = first.copy(context = first.context.copy(matchId = "match-2"))
        assertTrue(GameSportEngine.tennis.areComparable(first, otherMatchSameConditions).comparable)
        val otherSurface = first.copy(context = first.context.copy(surface = "hard"))
        assertFalse(GameSportEngine.tennis.areComparable(first, otherSurface).comparable)
        val otherRules = first.copy(format = first.format.copy(ruleset = first.format.ruleset.copy(version = "rev-8")))
        assertFalse(GameSportEngine.tennis.areComparable(first, otherRules).comparable)
    }
}

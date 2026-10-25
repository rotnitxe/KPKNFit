package com.example.kpkn.domain.multisport.games

import com.example.kpkn.domain.multisport.SportId
import kotlinx.serialization.Serializable

const val GAME_SPORT_SCHEMA_VERSION = 1

@Serializable
enum class GameRacketSide { A, B }

@Serializable
enum class GameMatchStatus { COMPLETED, PARTIAL, RETIRED, WALKOVER, INVALIDATED }

@Serializable
enum class GameSetStatus { COMPLETED, PARTIAL, NOT_PLAYED, INVALIDATED }

@Serializable
enum class GameServeAttemptOutcome { IN, FAULT }

@Serializable
enum class GameActionUnit { COUNT, ATTEMPT, REPETITION, METER, SECOND }

@Serializable
enum class GamePresence { ON_FIELD, OFF_FIELD, SUBSTITUTED_OUT }

@Serializable
enum class GameClockBehavior { PAUSES_DURING_STOPPAGE, CONTINUES_DURING_STOPPAGE, UNKNOWN }

@Serializable
enum class GameRpeSource { ATHLETE, COACH, OTHER }

/** Rules are snapshots: custom rules remain usable without implying an official governing body. */
@Serializable
data class GameRulesetSnapshot(
    val id: String,
    val version: String,
    val label: String,
    val custom: Boolean,
    val sourceReference: String? = null,
    val parameters: Map<String, String> = emptyMap(),
)

@Serializable
data class GameFormatSnapshot(
    val id: String,
    val version: String,
    val label: String,
    val ruleset: GameRulesetSnapshot,
    /** Declared scoring and match-format choices; the engine does not supply defaults. */
    val parameters: Map<String, String> = emptyMap(),
)

@Serializable
data class RacketMatchContext(
    val matchId: String,
    val sideAId: String,
    val sideBId: String,
    val subjectSide: GameRacketSide? = null,
    val subjectAthleteId: String? = null,
    val courtId: String? = null,
    val surface: String? = null,
    val venue: String? = null,
    val competitionId: String? = null,
    val conditions: Map<String, String> = emptyMap(),
)

@Serializable
data class RacketPlan(
    val format: GameFormatSnapshot,
    val context: RacketMatchContext,
    val declaredDurationSeconds: Long? = null,
    val note: String? = null,
)

@Serializable
data class RacketSetResult(
    val setNumber: Int,
    val status: GameSetStatus,
    val gamesA: Int? = null,
    val gamesB: Int? = null,
    val tieBreakPointsA: Int? = null,
    val tieBreakPointsB: Int? = null,
)

/** A recorded service sequence; absent means unknown, not zero attempts. */
@Serializable
data class RacketServiceEvidence(val attempts: List<GameServeAttemptOutcome>)

@Serializable
data class RacketPointEvidence(
    val setNumber: Int,
    val gameNumber: Int,
    val pointNumber: Int,
    val server: GameRacketSide,
    val winner: GameRacketSide,
    val service: RacketServiceEvidence? = null,
    /** Only a captured adjudication; the engine never reconstructs point scores to infer it. */
    val breakPointOpportunity: Boolean? = null,
)

@Serializable
data class RacketPointSequence(
    val complete: Boolean,
    val points: List<RacketPointEvidence>,
)

/** Counts stay nullable so that a missing denominator is not mistaken for zero. */
@Serializable
data class RacketSideStatistics(
    val servicePoints: Long? = null,
    val firstServeAttempts: Long? = null,
    val firstServesIn: Long? = null,
    val secondServeAttempts: Long? = null,
    val secondServesIn: Long? = null,
    val doubleFaults: Long? = null,
    val pointsPlayed: Long? = null,
    val winners: Long? = null,
    val errors: Long? = null,
)

@Serializable
data class RacketMatchResult(
    val format: GameFormatSnapshot,
    val context: RacketMatchContext,
    val status: GameMatchStatus,
    val winner: GameRacketSide? = null,
    val retiringSide: GameRacketSide? = null,
    val sets: List<RacketSetResult> = emptyList(),
    val statisticsA: RacketSideStatistics? = null,
    val statisticsB: RacketSideStatistics? = null,
    val pointSequence: RacketPointSequence? = null,
    /** Optional captured elapsed match time; no time is inferred from score structure. */
    val elapsedDurationMillis: Long? = null,
    /** Declared timing method/context, needed before comparing timed outcomes. */
    val timingMethod: String? = null,
    val penalties: List<GamePenalty> = emptyList(),
    val note: String? = null,
)

@Serializable
data class RacketObservation(
    val format: GameFormatSnapshot,
    val context: RacketMatchContext,
    val observedAtEpochMillis: Long? = null,
    val statisticsA: RacketSideStatistics? = null,
    val statisticsB: RacketSideStatistics? = null,
    val pointSequence: RacketPointSequence? = null,
    val note: String? = null,
)

@Serializable
data class GamePenalty(val code: String, val value: Double, val unit: String, val note: String? = null)

@Serializable
data class TeamPeriodDefinition(
    val id: String,
    val label: String,
    val declaredDurationMillis: Long? = null,
    val clockBehavior: GameClockBehavior = GameClockBehavior.UNKNOWN,
)

@Serializable
data class TeamFormatSnapshot(
    val id: String,
    val version: String,
    val label: String,
    val custom: Boolean,
    val sourceReference: String? = null,
    val parameters: Map<String, String> = emptyMap(),
    val periods: List<TeamPeriodDefinition>,
)

@Serializable
data class TeamActionDefinition(val id: String, val label: String, val unit: GameActionUnit)

@Serializable
data class TeamActionProtocolSnapshot(
    val id: String,
    val version: String,
    val label: String,
    val custom: Boolean,
    val sourceReference: String? = null,
    val actions: List<TeamActionDefinition>,
)

@Serializable
data class TeamSportContext(
    /** A caller-declared sport subtype (for example, a specific team sport); no universal list is assumed. */
    val teamSportId: String,
    val teamId: String,
    val athleteId: String? = null,
    val athleteRoleId: String,
    val sideId: String? = null,
    val opponentId: String? = null,
    val position: String? = null,
    val courtId: String? = null,
    val surface: String? = null,
    val venue: String? = null,
    val competitionId: String? = null,
    val conditions: Map<String, String> = emptyMap(),
)

@Serializable
data class TeamSportPlan(
    val format: TeamFormatSnapshot,
    val actionProtocol: TeamActionProtocolSnapshot,
    val context: TeamSportContext,
    val declaredDurationSeconds: Long? = null,
    val note: String? = null,
)

@Serializable
data class GameTimeInterval(val startOffsetMillis: Long, val endOffsetMillis: Long)

@Serializable
data class TeamPresenceInterval(
    val startOffsetMillis: Long,
    val endOffsetMillis: Long,
    val presence: GamePresence,
)

@Serializable
data class TeamActionCount(
    val actionId: String,
    val observedCount: Long? = null,
    val attemptedCount: Long? = null,
    val totalCount: Long? = null,
)

@Serializable
data class TeamRpe(
    val value: Double,
    val scaleId: String,
    val unitId: String,
    val source: GameRpeSource,
)

@Serializable
data class TeamPeriodResult(
    val periodId: String,
    /** Wall-clock duration captured for this period. It is not inferred from the score or format. */
    val elapsedDurationMillis: Long,
    val clockBehavior: GameClockBehavior,
    val clockPausedIntervals: List<GameTimeInterval>? = null,
    val clockPauseCaptureComplete: Boolean = false,
    val presenceIntervals: List<TeamPresenceInterval> = emptyList(),
    val presenceSequenceComplete: Boolean = false,
    val actions: List<TeamActionCount> = emptyList(),
)

@Serializable
data class TeamSportResult(
    val format: TeamFormatSnapshot,
    val actionProtocol: TeamActionProtocolSnapshot,
    val context: TeamSportContext,
    val periods: List<TeamPeriodResult>,
    val rpe: TeamRpe? = null,
    val note: String? = null,
)

@Serializable
data class TeamSportObservation(
    val format: TeamFormatSnapshot,
    val actionProtocol: TeamActionProtocolSnapshot,
    val context: TeamSportContext,
    val period: TeamPeriodResult? = null,
    val rpe: TeamRpe? = null,
    val observedAtEpochMillis: Long? = null,
    val note: String? = null,
)

/** A discriminated payload. Exactly one branch is populated for its declared purpose. */
@Serializable
data class GameSportPayload(
    val schemaVersion: Int = GAME_SPORT_SCHEMA_VERSION,
    val sportId: SportId,
    val racketPlan: RacketPlan? = null,
    val racketResult: RacketMatchResult? = null,
    val racketObservation: RacketObservation? = null,
    val teamPlan: TeamSportPlan? = null,
    val teamResult: TeamSportResult? = null,
    val teamObservation: TeamSportObservation? = null,
)

@Serializable
data class GameComparability(val comparable: Boolean, val reason: String? = null)

@Serializable
data class RacketServeSummary(
    val firstServeAttempts: Long,
    val firstServesIn: Long,
    val secondServeAttempts: Long,
    val secondServesIn: Long,
    val doubleFaults: Long,
    val firstServeInPercent: Double?,
    val secondServeInPercent: Double?,
)

@Serializable
data class RacketBreakpointSummary(val opportunities: Long, val converted: Long)

@Serializable
data class TeamTimeSummary(
    val actualOnFieldMillis: Long?,
    val activeClockOnFieldMillis: Long?,
)

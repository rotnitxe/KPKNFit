package com.example.kpkn.domain.multisport.games

import com.example.kpkn.domain.multisport.SportId
import com.example.kpkn.domain.multisport.tracking.SportDocumentEngine
import com.example.kpkn.domain.multisport.tracking.SportDocumentIssue
import com.example.kpkn.domain.multisport.tracking.SportDocumentPurpose
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

/** Manual score, observation and individual-load contracts for racket and configurable team sports. */
class GameSportEngine(private val sport: SportId) : SportDocumentEngine {
    init {
        require(sport in SUPPORTED_SPORTS) { "GameSportEngine requires a supported game SportId" }
    }

    override val id: String = "game.manual.${sport.name.lowercase()}"
    override val version: Int = GAME_SPORT_SCHEMA_VERSION
    override val sports: Set<SportId> = setOf(sport)
    override val purposes: Set<SportDocumentPurpose> = SportDocumentPurpose.entries.toSet()

    private val json = Json { ignoreUnknownKeys = false }

    companion object {
        private val SUPPORTED_SPORTS = setOf(SportId.TENNIS, SportId.PADEL, SportId.TEAM_SPORT)
        val tennis = GameSportEngine(SportId.TENNIS)
        val padel = GameSportEngine(SportId.PADEL)
        val teamSport = GameSportEngine(SportId.TEAM_SPORT)

        /** Root registers these with its shared SportTrackingRegistry. */
        val registeredEngines: List<GameSportEngine> = listOf(tennis, padel, teamSport)
    }

    override fun validate(payload: JsonObject, purpose: SportDocumentPurpose): List<SportDocumentIssue> {
        val decoded = runCatching { json.decodeFromJsonElement<GameSportPayload>(payload) }.getOrNull()
            ?: return listOf(SportDocumentIssue("payload", "PAYLOAD_SCHEMA_INVALID"))
        return validatePayload(decoded, purpose).map { SportDocumentIssue(it.path, it.code) }
    }

    override fun validateForSport(
        payload: JsonObject,
        purpose: SportDocumentPurpose,
        sport: SportId,
    ): List<SportDocumentIssue> {
        if (sport != this.sport) return listOf(SportDocumentIssue("sportId", "ENGINE_SPORT_MISMATCH"))
        return validate(payload, purpose)
    }

    override fun plannedDurationSeconds(payload: JsonObject): Long? {
        val decoded = runCatching { json.decodeFromJsonElement<GameSportPayload>(payload) }.getOrNull()
            ?: return null
        if (validatePayload(decoded, SportDocumentPurpose.PLAN).isNotEmpty()) return null
        return decoded.racketPlan?.declaredDurationSeconds ?: decoded.teamPlan?.declaredDurationSeconds
    }

    fun validatePayload(
        payload: GameSportPayload,
        purpose: SportDocumentPurpose,
    ): List<GameSportIssue> = buildList {
        if (payload.schemaVersion != GAME_SPORT_SCHEMA_VERSION) {
            add(GameSportIssue("schemaVersion", "UNSUPPORTED_SCHEMA_VERSION"))
        }
        if (payload.sportId != sport) add(GameSportIssue("sportId", "ENGINE_SPORT_MISMATCH"))
        val racket = payload.sportId == SportId.TENNIS || payload.sportId == SportId.PADEL
        val racketContent = listOf(payload.racketPlan, payload.racketResult, payload.racketObservation)
        val teamContent = listOf(payload.teamPlan, payload.teamResult, payload.teamObservation)
        val expectedIndex = when (purpose) {
            SportDocumentPurpose.PLAN -> 0
            SportDocumentPurpose.RESULT -> 1
            SportDocumentPurpose.OBSERVATION -> 2
        }
        val selectedContent = if (racket) racketContent else teamContent
        if (selectedContent[expectedIndex] == null) add(GameSportIssue("payload", "PURPOSE_CONTENT_REQUIRED"))
        if (selectedContent.count { it != null } != 1) add(GameSportIssue("payload", "PURPOSE_CONTENT_MISMATCH"))
        if (racket && (payload.teamPlan != null || payload.teamResult != null || payload.teamObservation != null)) {
            add(GameSportIssue("payload.team", "WRONG_SPORT_CONTENT"))
        }
        if (!racket && (payload.racketPlan != null || payload.racketResult != null || payload.racketObservation != null)) {
            add(GameSportIssue("payload.racket", "WRONG_SPORT_CONTENT"))
        }
        when {
            racket && purpose == SportDocumentPurpose.PLAN -> payload.racketPlan?.let { validateRacketPlan(it, this) }
            racket && purpose == SportDocumentPurpose.RESULT -> payload.racketResult?.let { validateRacketResult(it, this) }
            racket -> payload.racketObservation?.let { validateRacketObservation(it, this) }
            purpose == SportDocumentPurpose.PLAN -> payload.teamPlan?.let { validateTeamPlan(it, this) }
            purpose == SportDocumentPurpose.RESULT -> payload.teamResult?.let { validateTeamResult(it, this) }
            else -> payload.teamObservation?.let { validateTeamObservation(it, this) }
        }
    }.distinct()

    fun validateRacketFormat(format: GameFormatSnapshot, path: String = "format"): List<GameSportIssue> =
        buildList { validateFormat(format, path, this) }

    fun validateTeamFormat(format: TeamFormatSnapshot, path: String = "format"): List<GameSportIssue> =
        buildList { validateTeamFormatSnapshot(format, path, this) }

    private fun validateRacketPlan(plan: RacketPlan, issues: MutableList<GameSportIssue>) {
        validateFormat(plan.format, "racketPlan.format", issues)
        validateRacketContext(plan.context, "racketPlan.context", issues)
        validateDeclaredDuration(plan.declaredDurationSeconds, "racketPlan.declaredDurationSeconds", issues)
        validateNote(plan.note, "racketPlan.note", issues)
    }

    private fun validateRacketObservation(observation: RacketObservation, issues: MutableList<GameSportIssue>) {
        validateFormat(observation.format, "racketObservation.format", issues)
        validateRacketContext(observation.context, "racketObservation.context", issues)
        if (observation.observedAtEpochMillis != null && observation.observedAtEpochMillis < 0L) {
            issues += GameSportIssue("racketObservation.observedAtEpochMillis", "NON_NEGATIVE_REQUIRED")
        }
        observation.statisticsA?.let { validateRacketStatistics(it, "racketObservation.statisticsA", issues) }
        observation.statisticsB?.let { validateRacketStatistics(it, "racketObservation.statisticsB", issues) }
        observation.pointSequence?.let { validatePointSequence(it, emptySet(), "racketObservation.pointSequence", issues) }
        validateNote(observation.note, "racketObservation.note", issues)
    }

    private fun validateRacketResult(result: RacketMatchResult, issues: MutableList<GameSportIssue>) {
        validateFormat(result.format, "racketResult.format", issues)
        validateRacketContext(result.context, "racketResult.context", issues)
        val setNumbers = result.sets.map { it.setNumber }
        if (setNumbers.distinct().size != setNumbers.size) issues += GameSportIssue("racketResult.sets", "DUPLICATE_SET_NUMBER")
        if (setNumbers != setNumbers.sorted()) issues += GameSportIssue("racketResult.sets", "SET_ORDER_INVALID")
        result.sets.forEachIndexed { index, set ->
            val path = "racketResult.sets[$index]"
            if (set.setNumber <= 0) issues += GameSportIssue("$path.setNumber", "POSITIVE_REQUIRED")
            validatePairedNonNegative(set.gamesA, set.gamesB, "$path.games", issues)
            validatePairedNonNegative(set.tieBreakPointsA, set.tieBreakPointsB, "$path.tieBreakPoints", issues)
            if (set.status == GameSetStatus.COMPLETED && (set.gamesA == null || set.gamesB == null)) {
                issues += GameSportIssue("$path.games", "COMPLETED_SET_SCORE_REQUIRED")
            }
            if (set.status in setOf(GameSetStatus.NOT_PLAYED, GameSetStatus.INVALIDATED) &&
                (set.gamesA != null || set.gamesB != null || set.tieBreakPointsA != null || set.tieBreakPointsB != null)
            ) issues += GameSportIssue(path, "SCORE_NOT_ALLOWED_FOR_SET_STATUS")
        }
        result.statisticsA?.let { validateRacketStatistics(it, "racketResult.statisticsA", issues) }
        result.statisticsB?.let { validateRacketStatistics(it, "racketResult.statisticsB", issues) }
        result.pointSequence?.let { validatePointSequence(it, setNumbers.toSet(), "racketResult.pointSequence", issues) }
        if (result.elapsedDurationMillis != null && result.elapsedDurationMillis <= 0L) {
            issues += GameSportIssue("racketResult.elapsedDurationMillis", "POSITIVE_OR_NULL_REQUIRED")
        }
        if (result.elapsedDurationMillis != null && result.timingMethod.isNullOrBlank()) {
            issues += GameSportIssue("racketResult.timingMethod", "REQUIRED_WHEN_DURATION_CAPTURED")
        }
        validateOptionalText(result.timingMethod, "racketResult.timingMethod", issues)
        validatePenalties(result.penalties, "racketResult.penalties", issues)
        when (result.status) {
            GameMatchStatus.COMPLETED -> {
                if (result.winner == null) issues += GameSportIssue("racketResult.winner", "COMPLETED_MATCH_WINNER_REQUIRED")
                if (result.retiringSide != null) issues += GameSportIssue("racketResult.retiringSide", "NOT_ALLOWED_FOR_COMPLETED")
                if (result.sets.isEmpty() || result.sets.any { it.status != GameSetStatus.COMPLETED }) {
                    issues += GameSportIssue("racketResult.sets", "COMPLETED_MATCH_REQUIRES_COMPLETED_SETS")
                }
            }
            GameMatchStatus.PARTIAL -> if (result.winner != null) {
                issues += GameSportIssue("racketResult.winner", "PARTIAL_MATCH_WINNER_MUST_REMAIN_UNKNOWN")
            }
            GameMatchStatus.RETIRED -> if (result.retiringSide == null) {
                issues += GameSportIssue("racketResult.retiringSide", "RETIRING_SIDE_REQUIRED")
            }
            GameMatchStatus.WALKOVER -> {
                if (result.winner == null) issues += GameSportIssue("racketResult.winner", "WALKOVER_WINNER_REQUIRED")
                if (result.retiringSide != null || result.sets.isNotEmpty() || result.statisticsA != null ||
                    result.statisticsB != null || result.pointSequence != null
                ) issues += GameSportIssue("racketResult", "WALKOVER_CANNOT_CONTAIN_PLAYED_EVIDENCE")
            }
            GameMatchStatus.INVALIDATED -> Unit
        }
        validateNote(result.note, "racketResult.note", issues)
    }

    private fun validatePointSequence(
        sequence: RacketPointSequence,
        knownSetNumbers: Set<Int>,
        path: String,
        issues: MutableList<GameSportIssue>,
    ) {
        if (sequence.complete && sequence.points.isEmpty()) issues += GameSportIssue("$path.points", "COMPLETE_SEQUENCE_CANNOT_BE_EMPTY")
        var previous: Triple<Int, Int, Int>? = null
        sequence.points.forEachIndexed { index, point ->
            val pointPath = "$path.points[$index]"
            if (point.setNumber <= 0 || point.gameNumber <= 0 || point.pointNumber <= 0) {
                issues += GameSportIssue(pointPath, "SEQUENCE_NUMBERS_MUST_BE_POSITIVE")
            }
            if (knownSetNumbers.isNotEmpty() && point.setNumber !in knownSetNumbers) {
                issues += GameSportIssue("$pointPath.setNumber", "SET_NOT_FOUND")
            }
            val key = Triple(point.setNumber, point.gameNumber, point.pointNumber)
            if (previous != null && compareKeys(previous!!, key) >= 0) {
                issues += GameSportIssue(pointPath, "POINT_ORDER_INVALID")
            }
            if (sequence.complete && previous != null && !isNextPoint(previous!!, key)) {
                issues += GameSportIssue(pointPath, "COMPLETE_SEQUENCE_HAS_GAP")
            }
            previous = key
            point.service?.let { service ->
                if (service.attempts.isEmpty() || service.attempts.size > 2) {
                    issues += GameSportIssue("$pointPath.service.attempts", "ONE_OR_TWO_ATTEMPTS_REQUIRED")
                }
                if (service.attempts.size == 2 && service.attempts.firstOrNull() != GameServeAttemptOutcome.FAULT) {
                    issues += GameSportIssue("$pointPath.service.attempts", "SECOND_ATTEMPT_REQUIRES_FIRST_FAULT")
                }
            }
        }
    }

    private fun validateRacketStatistics(
        stats: RacketSideStatistics,
        path: String,
        issues: MutableList<GameSportIssue>,
    ) {
        val counts = mapOf(
            "servicePoints" to stats.servicePoints,
            "firstServeAttempts" to stats.firstServeAttempts,
            "firstServesIn" to stats.firstServesIn,
            "secondServeAttempts" to stats.secondServeAttempts,
            "secondServesIn" to stats.secondServesIn,
            "doubleFaults" to stats.doubleFaults,
            "pointsPlayed" to stats.pointsPlayed,
            "winners" to stats.winners,
            "errors" to stats.errors,
        )
        counts.forEach { (key, value) ->
            if (value != null && value < 0L) issues += GameSportIssue("$path.$key", "NON_NEGATIVE_REQUIRED")
        }
        atMost(stats.firstServesIn, stats.firstServeAttempts, "$path.firstServesIn", issues)
        atMost(stats.secondServesIn, stats.secondServeAttempts, "$path.secondServesIn", issues)
        atMost(stats.firstServeAttempts, stats.servicePoints, "$path.firstServeAttempts", issues)
        atMost(stats.secondServeAttempts, stats.servicePoints, "$path.secondServeAttempts", issues)
        atMost(stats.doubleFaults, stats.secondServeAttempts, "$path.doubleFaults", issues)
        atMost(stats.winners, stats.pointsPlayed, "$path.winners", issues)
        atMost(stats.errors, stats.pointsPlayed, "$path.errors", issues)
        if (stats.firstServeAttempts != null && stats.secondServeAttempts != null &&
            stats.servicePoints != null && stats.firstServeAttempts <= stats.servicePoints &&
            stats.secondServeAttempts <= stats.servicePoints &&
            stats.firstServeAttempts > stats.servicePoints - stats.secondServeAttempts
        ) issues += GameSportIssue("$path.servicePoints", "SERVE_ATTEMPTS_EXCEED_SERVICE_POINTS")
    }

    private fun validateTeamPlan(plan: TeamSportPlan, issues: MutableList<GameSportIssue>) {
        validateTeamFormatSnapshot(plan.format, "teamPlan.format", issues)
        validateActionProtocol(plan.actionProtocol, "teamPlan.actionProtocol", issues)
        validateTeamContext(plan.context, "teamPlan.context", issues)
        validateDeclaredDuration(plan.declaredDurationSeconds, "teamPlan.declaredDurationSeconds", issues)
        validateNote(plan.note, "teamPlan.note", issues)
    }

    private fun validateTeamResult(result: TeamSportResult, issues: MutableList<GameSportIssue>) {
        validateTeamFormatSnapshot(result.format, "teamResult.format", issues)
        validateActionProtocol(result.actionProtocol, "teamResult.actionProtocol", issues)
        validateTeamContext(result.context, "teamResult.context", issues)
        validateTeamPeriods(result.format, result.actionProtocol, result.periods, "teamResult.periods", issues)
        validateRpe(result.rpe, "teamResult.rpe", issues)
        validateNote(result.note, "teamResult.note", issues)
    }

    private fun validateTeamObservation(observation: TeamSportObservation, issues: MutableList<GameSportIssue>) {
        validateTeamFormatSnapshot(observation.format, "teamObservation.format", issues)
        validateActionProtocol(observation.actionProtocol, "teamObservation.actionProtocol", issues)
        validateTeamContext(observation.context, "teamObservation.context", issues)
        observation.period?.let {
            val periodDefinition = observation.format.periods.firstOrNull { definition -> definition.id == it.periodId }
            validateTeamPeriods(
                observation.format,
                observation.actionProtocol,
                listOf(it),
                "teamObservation.period",
                issues,
            )
            if (periodDefinition == null) issues += GameSportIssue("teamObservation.period.periodId", "PERIOD_NOT_FOUND")
        }
        validateRpe(observation.rpe, "teamObservation.rpe", issues)
        if (observation.observedAtEpochMillis != null && observation.observedAtEpochMillis < 0L) {
            issues += GameSportIssue("teamObservation.observedAtEpochMillis", "NON_NEGATIVE_REQUIRED")
        }
        validateNote(observation.note, "teamObservation.note", issues)
    }

    private fun validateTeamFormatSnapshot(
        format: TeamFormatSnapshot,
        path: String,
        issues: MutableList<GameSportIssue>,
    ) {
        validateSnapshotIdentity(format.id, format.version, format.label, format.custom, format.sourceReference, path, issues)
        validateParameters(format.parameters, "$path.parameters", issues)
        if (format.periods.isEmpty()) issues += GameSportIssue("$path.periods", "AT_LEAST_ONE_PERIOD_REQUIRED")
        val ids = format.periods.map { it.id }
        if (ids.distinct().size != ids.size) issues += GameSportIssue("$path.periods", "DUPLICATE_PERIOD_ID")
        format.periods.forEachIndexed { index, period ->
            val periodPath = "$path.periods[$index]"
            if (period.id.isBlank()) issues += GameSportIssue("$periodPath.id", "REQUIRED")
            if (period.label.isBlank()) issues += GameSportIssue("$periodPath.label", "REQUIRED")
            if (period.declaredDurationMillis != null && period.declaredDurationMillis <= 0L) {
                issues += GameSportIssue("$periodPath.declaredDurationMillis", "POSITIVE_OR_NULL_REQUIRED")
            }
        }
    }

    private fun validateActionProtocol(
        protocol: TeamActionProtocolSnapshot,
        path: String,
        issues: MutableList<GameSportIssue>,
    ) {
        validateSnapshotIdentity(protocol.id, protocol.version, protocol.label, protocol.custom, protocol.sourceReference, path, issues)
        if (protocol.actions.isEmpty()) issues += GameSportIssue("$path.actions", "AT_LEAST_ONE_ACTION_REQUIRED")
        val duplicateIds = protocol.actions.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        protocol.actions.forEachIndexed { index, action ->
            val actionPath = "$path.actions[$index]"
            if (action.id.isBlank()) issues += GameSportIssue("$actionPath.id", "REQUIRED")
            if (action.id in duplicateIds) issues += GameSportIssue("$actionPath.id", "DUPLICATE")
            if (action.label.isBlank()) issues += GameSportIssue("$actionPath.label", "REQUIRED")
        }
    }

    private fun validateTeamPeriods(
        format: TeamFormatSnapshot,
        protocol: TeamActionProtocolSnapshot,
        periods: List<TeamPeriodResult>,
        path: String,
        issues: MutableList<GameSportIssue>,
    ) {
        val ids = periods.map { it.periodId }
        if (ids.distinct().size != ids.size) issues += GameSportIssue(path, "DUPLICATE_PERIOD_RESULT")
        val expectedOrder = format.periods.map { it.id }.filter { it in ids }
        if (ids != expectedOrder) issues += GameSportIssue(path, "PERIOD_ORDER_INVALID")
        val knownActionIds = protocol.actions.map { it.id }.toSet()
        periods.forEachIndexed { index, period ->
            val periodPath = "$path[$index]"
            val definition = format.periods.firstOrNull { it.id == period.periodId }
            if (definition == null) issues += GameSportIssue("$periodPath.periodId", "PERIOD_NOT_FOUND")
            if (period.elapsedDurationMillis <= 0L) issues += GameSportIssue("$periodPath.elapsedDurationMillis", "POSITIVE_REQUIRED")
            validateIntervals(period.clockPausedIntervals, period.elapsedDurationMillis, "$periodPath.clockPausedIntervals", issues)
            if (period.clockPauseCaptureComplete && period.clockPausedIntervals == null) {
                issues += GameSportIssue("$periodPath.clockPausedIntervals", "COMPLETE_CAPTURE_REQUIRES_INTERVAL_LIST")
            }
            if (period.clockBehavior == GameClockBehavior.CONTINUES_DURING_STOPPAGE &&
                !period.clockPausedIntervals.isNullOrEmpty()
            ) issues += GameSportIssue("$periodPath.clockPausedIntervals", "CLOCK_CONTINUES_DURING_STOPPAGE")
            if (period.clockPauseCaptureComplete && period.clockBehavior == GameClockBehavior.UNKNOWN) {
                issues += GameSportIssue("$periodPath.clockBehavior", "KNOWN_CLOCK_BEHAVIOR_REQUIRED")
            }
            validatePresence(period, periodPath, issues)
            val duplicateActions = period.actions.groupingBy { it.actionId }.eachCount().filterValues { it > 1 }.keys
            period.actions.forEachIndexed { actionIndex, action ->
                val actionPath = "$periodPath.actions[$actionIndex]"
                if (action.actionId !in knownActionIds) issues += GameSportIssue("$actionPath.actionId", "ACTION_NOT_IN_PROTOCOL")
                if (action.actionId in duplicateActions) issues += GameSportIssue("$actionPath.actionId", "DUPLICATE")
                val values = listOf(
                    "observedCount" to action.observedCount,
                    "attemptedCount" to action.attemptedCount,
                    "totalCount" to action.totalCount,
                )
                values.forEach { (name, value) ->
                    if (value != null && value < 0L) issues += GameSportIssue("$actionPath.$name", "NON_NEGATIVE_OR_NULL_REQUIRED")
                }
                atMost(action.observedCount, action.attemptedCount, "$actionPath.observedCount", issues)
                atMost(action.attemptedCount, action.totalCount, "$actionPath.attemptedCount", issues)
                atMost(action.observedCount, action.totalCount, "$actionPath.observedCount", issues)
            }
        }
    }

    private fun validatePresence(period: TeamPeriodResult, path: String, issues: MutableList<GameSportIssue>) {
        val intervals = period.presenceIntervals
        var priorEnd: Long? = null
        intervals.forEachIndexed { index, interval ->
            val intervalPath = "$path.presenceIntervals[$index]"
            if (interval.startOffsetMillis < 0L || interval.endOffsetMillis <= interval.startOffsetMillis ||
                interval.endOffsetMillis > period.elapsedDurationMillis
            ) issues += GameSportIssue(intervalPath, "INVALID_INTERVAL_BOUNDS")
            if (priorEnd != null && interval.startOffsetMillis < priorEnd!!) {
                issues += GameSportIssue(intervalPath, "PRESENCE_INTERVAL_OVERLAP_OR_ORDER")
            }
            priorEnd = interval.endOffsetMillis
        }
        if (period.presenceSequenceComplete) {
            if (intervals.isEmpty()) issues += GameSportIssue("$path.presenceIntervals", "COMPLETE_CAPTURE_CANNOT_BE_EMPTY")
            if (intervals.firstOrNull()?.startOffsetMillis != 0L ||
                intervals.lastOrNull()?.endOffsetMillis != period.elapsedDurationMillis ||
                intervals.zipWithNext().any { (first, second) -> first.endOffsetMillis != second.startOffsetMillis }
            ) issues += GameSportIssue("$path.presenceIntervals", "COMPLETE_CAPTURE_MUST_COVER_PERIOD_WITHOUT_GAPS")
        }
    }

    private fun validateIntervals(
        intervals: List<GameTimeInterval>?,
        periodDurationMillis: Long,
        path: String,
        issues: MutableList<GameSportIssue>,
    ) {
        if (intervals == null) return
        var priorEnd: Long? = null
        intervals.forEachIndexed { index, interval ->
            val intervalPath = "$path[$index]"
            if (interval.startOffsetMillis < 0L || interval.endOffsetMillis <= interval.startOffsetMillis ||
                interval.endOffsetMillis > periodDurationMillis
            ) issues += GameSportIssue(intervalPath, "INVALID_INTERVAL_BOUNDS")
            if (priorEnd != null && interval.startOffsetMillis < priorEnd!!) {
                issues += GameSportIssue(intervalPath, "INTERVAL_OVERLAP_OR_ORDER")
            }
            priorEnd = interval.endOffsetMillis
        }
    }

    private fun validateRpe(rpe: TeamRpe?, path: String, issues: MutableList<GameSportIssue>) {
        if (rpe == null) return
        if (!rpe.value.isFinite() || rpe.value < 0.0) issues += GameSportIssue("$path.value", "FINITE_NON_NEGATIVE_REQUIRED")
        if (rpe.scaleId.isBlank()) issues += GameSportIssue("$path.scaleId", "REQUIRED")
        if (rpe.unitId.isBlank()) issues += GameSportIssue("$path.unitId", "REQUIRED")
    }

    private fun validateFormat(format: GameFormatSnapshot, path: String, issues: MutableList<GameSportIssue>) {
        validateSnapshotIdentity(format.id, format.version, format.label, true, null, path, issues)
        validateSnapshotIdentity(
            format.ruleset.id, format.ruleset.version, format.ruleset.label, format.ruleset.custom,
            format.ruleset.sourceReference, "$path.ruleset", issues,
        )
        validateParameters(format.parameters, "$path.parameters", issues)
        validateParameters(format.ruleset.parameters, "$path.ruleset.parameters", issues)
    }

    private fun validateRacketContext(context: RacketMatchContext, path: String, issues: MutableList<GameSportIssue>) {
        if (context.matchId.isBlank()) issues += GameSportIssue("$path.matchId", "REQUIRED")
        if (context.sideAId.isBlank()) issues += GameSportIssue("$path.sideAId", "REQUIRED")
        if (context.sideBId.isBlank()) issues += GameSportIssue("$path.sideBId", "REQUIRED")
        if (context.sideAId.isNotBlank() && context.sideAId == context.sideBId) {
            issues += GameSportIssue("$path.sideBId", "SIDES_MUST_DIFFER")
        }
        validateOptionalText(context.subjectAthleteId, "$path.subjectAthleteId", issues)
        validateOptionalText(context.courtId, "$path.courtId", issues)
        validateOptionalText(context.surface, "$path.surface", issues)
        validateOptionalText(context.venue, "$path.venue", issues)
        validateOptionalText(context.competitionId, "$path.competitionId", issues)
        validateParameters(context.conditions, "$path.conditions", issues)
    }

    private fun validateTeamContext(context: TeamSportContext, path: String, issues: MutableList<GameSportIssue>) {
        if (context.teamSportId.isBlank()) issues += GameSportIssue("$path.teamSportId", "REQUIRED")
        if (context.teamId.isBlank()) issues += GameSportIssue("$path.teamId", "REQUIRED")
        if (context.athleteRoleId.isBlank()) issues += GameSportIssue("$path.athleteRoleId", "REQUIRED")
        validateOptionalText(context.athleteId, "$path.athleteId", issues)
        validateOptionalText(context.sideId, "$path.sideId", issues)
        validateOptionalText(context.opponentId, "$path.opponentId", issues)
        validateOptionalText(context.position, "$path.position", issues)
        validateOptionalText(context.courtId, "$path.courtId", issues)
        validateOptionalText(context.surface, "$path.surface", issues)
        validateOptionalText(context.venue, "$path.venue", issues)
        validateOptionalText(context.competitionId, "$path.competitionId", issues)
        validateParameters(context.conditions, "$path.conditions", issues)
    }

    private fun validateSnapshotIdentity(
        id: String,
        version: String,
        label: String,
        custom: Boolean,
        sourceReference: String?,
        path: String,
        issues: MutableList<GameSportIssue>,
    ) {
        if (id.isBlank()) issues += GameSportIssue("$path.id", "REQUIRED")
        if (version.isBlank()) issues += GameSportIssue("$path.version", "REQUIRED")
        if (label.isBlank()) issues += GameSportIssue("$path.label", "REQUIRED")
        if (!custom && sourceReference.isNullOrBlank()) {
            issues += GameSportIssue("$path.sourceReference", "REFERENCE_REQUIRED_FOR_NON_CUSTOM_SNAPSHOT")
        }
        validateOptionalText(sourceReference, "$path.sourceReference", issues)
    }

    private fun validateParameters(values: Map<String, String>, path: String, issues: MutableList<GameSportIssue>) {
        values.forEach { (key, value) ->
            if (key.isBlank() || value.isBlank()) issues += GameSportIssue(path, "PARAMETER_KEY_AND_VALUE_REQUIRED")
        }
    }

    private fun validateDeclaredDuration(value: Long?, path: String, issues: MutableList<GameSportIssue>) {
        if (value != null && value <= 0L) issues += GameSportIssue(path, "POSITIVE_OR_NULL_REQUIRED")
    }

    private fun validateOptionalText(value: String?, path: String, issues: MutableList<GameSportIssue>) {
        if (value != null && value.isBlank()) issues += GameSportIssue(path, "NON_BLANK_OR_NULL_REQUIRED")
    }

    private fun validateNote(note: String?, path: String, issues: MutableList<GameSportIssue>) =
        validateOptionalText(note, path, issues)

    private fun validatePairedNonNegative(a: Int?, b: Int?, path: String, issues: MutableList<GameSportIssue>) {
        if ((a == null) != (b == null)) issues += GameSportIssue(path, "BOTH_SIDES_REQUIRED_OR_UNKNOWN")
        if (a != null && a < 0 || b != null && b < 0) issues += GameSportIssue(path, "NON_NEGATIVE_REQUIRED")
    }

    private fun atMost(value: Long?, bound: Long?, path: String, issues: MutableList<GameSportIssue>) {
        if (value != null && bound != null && value > bound) issues += GameSportIssue(path, "EXCEEDS_KNOWN_DENOMINATOR")
    }

    private fun validatePenalties(penalties: List<GamePenalty>, path: String, issues: MutableList<GameSportIssue>) {
        penalties.forEachIndexed { index, penalty ->
            if (penalty.code.isBlank()) issues += GameSportIssue("$path[$index].code", "REQUIRED")
            if (!penalty.value.isFinite() || penalty.value <= 0.0) {
                issues += GameSportIssue("$path[$index].value", "POSITIVE_FINITE_REQUIRED")
            }
            if (penalty.unit.isBlank()) issues += GameSportIssue("$path[$index].unit", "REQUIRED")
            validateOptionalText(penalty.note, "$path[$index].note", issues)
        }
    }

    /** Percentages are absent when the captured denominator is missing or zero. */
    fun serveSummary(
        statistics: RacketSideStatistics,
    ): RacketServeSummary? {
        val firstAttempts = statistics.firstServeAttempts ?: return null
        val firstIn = statistics.firstServesIn ?: return null
        val secondAttempts = statistics.secondServeAttempts ?: return null
        val secondIn = statistics.secondServesIn ?: return null
        val doubleFaults = statistics.doubleFaults ?: return null
        val issues = mutableListOf<GameSportIssue>()
        validateRacketStatistics(statistics, "statistics", issues)
        if (issues.isNotEmpty()) return null
        return RacketServeSummary(
            firstServeAttempts = firstAttempts,
            firstServesIn = firstIn,
            secondServeAttempts = secondAttempts,
            secondServesIn = secondIn,
            doubleFaults = doubleFaults,
            firstServeInPercent = percentage(firstIn, firstAttempts),
            secondServeInPercent = percentage(secondIn, secondAttempts),
        )
    }

    /** Point-derived service counts require a complete sequence and explicit service evidence per point. */
    fun serveSummary(sequence: RacketPointSequence): RacketServeSummary? {
        if (!sequence.complete || sequence.points.isEmpty() || sequence.points.any { it.service == null }) return null
        val sequenceIssues = mutableListOf<GameSportIssue>()
        validatePointSequence(sequence, emptySet(), "pointSequence", sequenceIssues)
        if (sequenceIssues.isNotEmpty()) return null
        var firstAttempts = 0L
        var firstIn = 0L
        var secondAttempts = 0L
        var secondIn = 0L
        var doubleFaults = 0L
        sequence.points.forEach { point ->
            val attempts = point.service!!.attempts
            if (attempts.isEmpty() || attempts.size > 2 ||
                attempts.size == 2 && attempts.first() != GameServeAttemptOutcome.FAULT
            ) return null
            firstAttempts++
            if (attempts.first() == GameServeAttemptOutcome.IN) firstIn++
            if (attempts.size == 2) {
                secondAttempts++
                if (attempts[1] == GameServeAttemptOutcome.IN) secondIn++ else doubleFaults++
            }
        }
        return RacketServeSummary(
            firstAttempts, firstIn, secondAttempts, secondIn, doubleFaults,
            percentage(firstIn, firstAttempts), percentage(secondIn, secondAttempts),
        )
    }

    /** Conversion means the returner won an explicitly marked breakpoint point. */
    fun breakpointSummary(sequence: RacketPointSequence): RacketBreakpointSummary? {
        if (!sequence.complete || sequence.points.isEmpty() || sequence.points.any { it.breakPointOpportunity == null }) return null
        val sequenceIssues = mutableListOf<GameSportIssue>()
        validatePointSequence(sequence, emptySet(), "pointSequence", sequenceIssues)
        if (sequenceIssues.isNotEmpty()) return null
        val breakPoints = sequence.points.filter { it.breakPointOpportunity == true }
        return RacketBreakpointSummary(
            opportunities = breakPoints.size.toLong(),
            converted = breakPoints.count { it.winner != it.server }.toLong(),
        )
    }

    /** Returns null unless every declared period and its on-field timeline was captured completely. */
    fun teamTimeSummary(result: TeamSportResult): TeamTimeSummary? {
        if (validatePayload(
                GameSportPayload(sportId = SportId.TEAM_SPORT, teamResult = result),
                SportDocumentPurpose.RESULT,
            ).isNotEmpty()
        ) return null
        if (result.periods.size != result.format.periods.size || result.periods.any { !it.presenceSequenceComplete }) return null
        var onField = 0L
        for (period in result.periods) for (interval in period.presenceIntervals) {
            if (interval.presence == GamePresence.ON_FIELD) {
                onField = safeAdd(onField, interval.endOffsetMillis - interval.startOffsetMillis) ?: return null
            }
        }
        var activeClock = 0L
        for (period in result.periods) {
            val onFieldIntervals = period.presenceIntervals.filter { it.presence == GamePresence.ON_FIELD }
            for (interval in onFieldIntervals) {
                var activeSegment = interval.endOffsetMillis - interval.startOffsetMillis
                if (period.clockBehavior == GameClockBehavior.PAUSES_DURING_STOPPAGE) {
                    if (!period.clockPauseCaptureComplete || period.clockPausedIntervals == null) return TeamTimeSummary(onField, null)
                    val pausedOverlap = period.clockPausedIntervals.sumOf { pause ->
                        maxOf(0L, minOf(interval.endOffsetMillis, pause.endOffsetMillis) -
                            maxOf(interval.startOffsetMillis, pause.startOffsetMillis))
                    }
                    activeSegment -= pausedOverlap
                } else if (period.clockBehavior == GameClockBehavior.UNKNOWN) {
                    return TeamTimeSummary(onField, null)
                }
                activeClock = safeAdd(activeClock, activeSegment) ?: return null
            }
        }
        return TeamTimeSummary(onField, activeClock)
    }

    fun areComparable(first: RacketMatchResult, second: RacketMatchResult): GameComparability {
        if (validatePayload(GameSportPayload(sportId = sport, racketResult = first), SportDocumentPurpose.RESULT).isNotEmpty() ||
            validatePayload(GameSportPayload(sportId = sport, racketResult = second), SportDocumentPurpose.RESULT).isNotEmpty()
        ) return GameComparability(false, "INVALID_RESULT")
        if (first.format != second.format) return GameComparability(false, "FORMAT_OR_RULESET_DIFFERS")
        if (first.context.copy(matchId = "") != second.context.copy(matchId = "")) {
            return GameComparability(false, "MATCH_CONTEXT_DIFFERS")
        }
        if (first.status != GameMatchStatus.COMPLETED || second.status != GameMatchStatus.COMPLETED) {
            return GameComparability(false, "MATCH_NOT_COMPLETED")
        }
        if ((first.elapsedDurationMillis == null) != (second.elapsedDurationMillis == null) ||
            first.timingMethod != second.timingMethod
        ) return GameComparability(false, "TIMING_EVIDENCE_DIFFERS")
        if (first.pointSequence?.complete != second.pointSequence?.complete) {
            return GameComparability(false, "POINT_EVIDENCE_COMPLETENESS_DIFFERS")
        }
        return GameComparability(true)
    }

    fun areComparable(first: TeamSportResult, second: TeamSportResult): GameComparability {
        if (sport != SportId.TEAM_SPORT ||
            validatePayload(GameSportPayload(sportId = sport, teamResult = first), SportDocumentPurpose.RESULT).isNotEmpty() ||
            validatePayload(GameSportPayload(sportId = sport, teamResult = second), SportDocumentPurpose.RESULT).isNotEmpty()
        ) return GameComparability(false, "INVALID_RESULT")
        if (first.format != second.format) return GameComparability(false, "FORMAT_OR_PERIODS_DIFFERS")
        if (first.actionProtocol != second.actionProtocol) return GameComparability(false, "ACTION_PROTOCOL_DIFFERS")
        if (first.context != second.context) return GameComparability(false, "TEAM_ROLE_OR_CONDITIONS_DIFFERS")
        val firstTiming = first.periods.map {
            listOf(it.periodId, it.clockBehavior.name, it.presenceSequenceComplete.toString(), it.clockPauseCaptureComplete.toString())
        }
        val secondTiming = second.periods.map {
            listOf(it.periodId, it.clockBehavior.name, it.presenceSequenceComplete.toString(), it.clockPauseCaptureComplete.toString())
        }
        if (firstTiming != secondTiming) {
            return GameComparability(false, "TIMING_CONTEXT_DIFFERS")
        }
        return GameComparability(true)
    }

    fun toPayloadJson(payload: GameSportPayload): String = json.encodeToString(payload)

    fun fromPayloadJson(serialized: String): GameSportPayload? =
        runCatching { json.decodeFromString<GameSportPayload>(serialized) }.getOrNull()

    private fun percentage(numerator: Long, denominator: Long): Double? {
        if (denominator <= 0L) return null
        return (numerator.toDouble() * 100.0 / denominator.toDouble()).takeIf { it.isFinite() }
    }

    private fun compareKeys(first: Triple<Int, Int, Int>, second: Triple<Int, Int, Int>): Int {
        val setComparison = first.first.compareTo(second.first)
        if (setComparison != 0) return setComparison
        val gameComparison = first.second.compareTo(second.second)
        return if (gameComparison != 0) gameComparison else first.third.compareTo(second.third)
    }

    private fun isNextPoint(previous: Triple<Int, Int, Int>, current: Triple<Int, Int, Int>): Boolean = when {
        current.first == previous.first && current.second == previous.second -> current.third == previous.third + 1
        current.first == previous.first -> current.second == previous.second + 1 && current.third == 1
        else -> current.first == previous.first + 1 && current.second == 1 && current.third == 1
    }

    private fun safeAdd(a: Long, b: Long): Long? {
        if (b > 0L && a > Long.MAX_VALUE - b) return null
        if (b < 0L && a < Long.MIN_VALUE - b) return null
        return a + b
    }
}

data class GameSportIssue(val path: String, val code: String)

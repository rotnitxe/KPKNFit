package com.example.kpkn.domain.multisport.games

import com.example.kpkn.domain.multisport.SportId
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GameSportModelsTest {
    private val json = Json { encodeDefaults = true; explicitNulls = true }

    @Test
    fun nullableScoreCountersPreserveUnknownSeparatelyFromRecordedZero() {
        val unknown = RacketSideStatistics(firstServeAttempts = null)
        val zero = RacketSideStatistics(firstServeAttempts = 0L)

        assertNotEquals(unknown, zero)
        assertEquals(unknown, json.decodeFromString<RacketSideStatistics>(json.encodeToString(unknown)))
        assertEquals(zero, json.decodeFromString<RacketSideStatistics>(json.encodeToString(zero)))
    }

    @Test
    fun planAndResultModelsRoundTripWithDeclaredSportAndNoImplicitDuration() {
        val payload = GameSportPayload(
            sportId = SportId.TEAM_SPORT,
            teamPlan = TeamSportPlan(
                format = TeamFormatSnapshot(
                    id = "custom",
                    version = "v1",
                    label = "Declared local format",
                    custom = true,
                    periods = listOf(TeamPeriodDefinition("period-1", "Period 1")),
                ),
                actionProtocol = TeamActionProtocolSnapshot(
                    id = "manual",
                    version = "v1",
                    label = "Manual counts",
                    custom = true,
                    actions = listOf(TeamActionDefinition("tackle", "Tackle", GameActionUnit.COUNT)),
                ),
                context = TeamSportContext(
                    teamSportId = "club-defined",
                    teamId = "team-1",
                    athleteRoleId = "role-keeper",
                ),
            ),
        )
        val decoded = json.decodeFromString<GameSportPayload>(json.encodeToString(payload))

        assertEquals(payload, decoded)
        assertNull(decoded.teamPlan!!.declaredDurationSeconds)
        assertNull(decoded.teamResult)
        assertNull(decoded.teamObservation)
    }

    @Test
    fun timeSummaryKeepsUnknownActiveClockDistinctFromKnownZero() {
        val unknown = TeamTimeSummary(actualOnFieldMillis = 12_000L, activeClockOnFieldMillis = null)
        val zero = TeamTimeSummary(actualOnFieldMillis = 12_000L, activeClockOnFieldMillis = 0L)
        assertNotEquals(unknown, zero)
    }
}

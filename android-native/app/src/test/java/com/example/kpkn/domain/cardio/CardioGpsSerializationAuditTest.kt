package com.example.kpkn.domain.cardio

import com.example.kpkn.data.models.CardioTimerState
import com.example.kpkn.data.models.CompletedSet
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardioGpsSerializationAuditTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test fun oldRecordsRemainReadableAndNewRoutesRoundTrip() {
        val old = json.decodeFromString<CompletedSet>("""{"id":"old"}""")
        assertNull(old.gpsTrack)
        val route = GpsTrackSnapshot("session", points = listOf(GpsTrackPoint(1234L, -33.4, -70.6)))
        val recorded = CompletedSet(id = "set", gpsTrack = route)
        assertEquals(route, json.decodeFromString<CompletedSet>(json.encodeToString(recorded)).gpsTrack)
    }

    @Test fun timerRestoresDistanceAnchorAndOldTimersHaveNoAnchor() {
        val old = json.decodeFromString<CardioTimerState>("""{"exerciseId":"cardio","totalSeconds":60,"remainingSeconds":30}""")
        assertNull(old.gpsBlockStartMeters)
        val timer = old.copy(gpsBlockIndex = 3, gpsBlockStartMeters = 450.0)
        assertEquals(timer, json.decodeFromString<CardioTimerState>(json.encodeToString(timer)))
    }
}

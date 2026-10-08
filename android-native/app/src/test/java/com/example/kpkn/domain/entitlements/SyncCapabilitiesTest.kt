package com.example.kpkn.domain.entitlements

import com.example.kpkn.domain.sync.SyncCapabilities
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class SyncCapabilitiesTest {
    private fun program(sessions: Int) = Json.parseToJsonElement("""{"structure":"SIMPLE","macrocycles":[{"blocks":[{"mesocycles":[{"weeks":[{"sessions":[${List(sessions) { "{}" }.joinToString()}]}]}]}]}]}""").jsonObject
    @Test fun ordinarySyncAndControlDoNotRequirePro() {
        assertFalse(SyncCapabilities.requiresPro("workout_log", null, false))
        assertFalse(SyncCapabilities.requiresPro("nutrition_log", null, true))
        assertFalse(SyncCapabilities.requiresPro("workout_media", null, true))
        assertFalse(SyncCapabilities.requiresPro("program", program(5), false))
    }
    @Test fun paidEditsWaitWithoutChangingTheirPayload() {
        val advanced = program(6)
        val preserved = advanced.toString()
        assertTrue(SyncCapabilities.requiresPro("program", advanced, false))
        assertTrue(SyncCapabilities.requiresPro("nutrition_log", null, false))
        assertTrue(SyncCapabilities.requiresPro("workout_media", null, false))
        assertEquals(preserved, advanced.toString())
    }
    @Test fun advancedSchedulingAndMalformedStructuresFailClosed() {
        assertTrue(SyncCapabilities.requiresPro("program", Json.parseToJsonElement("""{"schedulePlan":{},"macrocycles":[]}""").jsonObject, false))
        assertTrue(SyncCapabilities.requiresPro("program", Json.parseToJsonElement("""{"macrocycles":"invalid"}""").jsonObject, false))
    }
}

package com.example.kpkn.domain.training.schedule

import com.example.kpkn.data.models.Program
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class MissedSessionJsonTest {
    @Test fun oldProgramDefaultsAndNewOriginalChoicesRoundTrip() {
        val old = Json.decodeFromString<Program>("""{"id":"p","name":"Plan"}""")
        assertTrue(old.skippedSessionKeys.isEmpty())
        assertNull(old.pendingMissedSessions)
        val pending = old.copy(skippedSessionKeys = listOf(MissedSessionPolicy.skippedKey("run",2,"w","s")),
            pendingMissedSessions = MissedSessionsRequest("w","inst_c2_w",listOf("s2"),20,"run",2))
        assertEquals(pending,Json.decodeFromString<Program>(Json.encodeToString(pending)))
    }
}

package com.example.kpkn.data.models

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class CompetitionModelsJsonTest {
    @Test fun legacyDefaultsAndOriginalPlanningFieldsRoundTrip() {
        val old = Json.decodeFromString<CompetitionRecord>("""{"id":"m","title":"Meet"}""")
        assertTrue(old.plannedAttempts.isEmpty())
        assertNull(old.targetWeightClass)
        assertNull(old.targetBodyweightKg)
        assertNull(old.meetResultAppliedAtMs)
        val record = old.copy(plannedAttempts = listOf(PlannedAttempt(CompetitionMovementType.SQUAT,1,182.5,"91 % de 200 kg")),
            targetWeightClass = "83 kg",targetBodyweightKg = 83.0,meetResultAppliedAtMs = 45)
        assertEquals(record,Json.decodeFromString<CompetitionRecord>(Json.encodeToString(record)))
    }
}

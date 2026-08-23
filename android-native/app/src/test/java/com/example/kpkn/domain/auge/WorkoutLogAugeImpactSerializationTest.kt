package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.WorkoutLog
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutLogAugeImpactSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun legacyLogStillDecodesAndV2RoundTrips() {
        val legacy = json.decodeFromString<WorkoutLog>(
            """{"id":"legacy","programId":"p","sessionId":"s","sessionName":"old","date":"2026-08-23","durationMinutes":30}""",
        )
        assertEquals(null, legacy.muscularImpactV2)
        val impact = MuscularSessionImpactEngine.evaluate(
            AugeRealSessionFixtures.completedExercises,
            "2026-08-23T17:25:00-04:00",
            AugeRealSessionFixtures.exerciseDb,
        )
        val log = legacy.copy(completedExercises = AugeRealSessionFixtures.completedExercises, muscularImpactV2 = impact)
        val decoded = json.decodeFromString<WorkoutLog>(json.encodeToString(log))
        assertNotNull(decoded.muscularImpactV2)
        assertEquals(30, decoded.completedExercises.sumOf { it.sets.size })
        assertEquals(impact.setInputHash, decoded.muscularImpactV2?.setInputHash)
        assertTrue(decoded.completedExercises.flatMap { it.sets }.any { it.rir == 0 })
    }
}

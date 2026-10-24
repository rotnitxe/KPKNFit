package com.example.kpkn.data.db

import com.example.kpkn.data.compatibility.LegacyTrainingBackup
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class LegacyTrainingBackupTest {
    @Test fun `versions one through six preserve originals without reviving derived fields`() {
        for (version in 1..6) {
            val raw = """{"schemaVersion":$version,"settings":{"name":"AUGE es mi nota","augePredictionBias":2},"wellbeingLogs":[{"id":"w","date":"2026-10-01","sleepHours":0,"manualMuscleOverridesV2":{"quad":{"battery":33,"anchorEpochMs":5,"automaticBatteryAtAnchor":90}}}],"ongoingWorkout":{"programId":"p","startTime":123,"sessionNotes":"Conservar","readinessNeuralOverride":45,"completedSets":{"s":{"reps":0,"weight":0,"spinalScore":3}},"godModeUndoStack":[{"label":"Mi edición","completedSets":{"s":{"reps":0,"spinalScore":3}}}]},"workoutLogs":[{"id":"l","notes":"RINGS","sessionStressScore":55}],"adaptiveCache":{"tau":8}}"""
            val normalized = LegacyTrainingBackup.normalize(raw)
            assertEquals(Json.parseToJsonElement(normalized), Json.parseToJsonElement(LegacyTrainingBackup.normalize(normalized)))
            val root=Json.parseToJsonElement(normalized).jsonObject
            assertFalse(root.containsKey("wellbeingLogs"));assertFalse(root.containsKey("adaptiveCache"))
            assertEquals("AUGE es mi nota",root.getValue("settings").jsonObject.getValue("name").jsonPrimitive.content)
            val observations=root.getValue("trainingObservations").jsonArray
            assertEquals(3,observations.size)
            val payloads=observations.map { Json.parseToJsonElement(it.jsonObject.getValue("payload").jsonPrimitive.content).jsonObject }
            assertTrue(payloads.any { it["sleepHours"]?.jsonPrimitive?.intOrNull==0 })
            assertFalse(normalized.contains("automaticBatteryAtAnchor"));assertFalse(normalized.contains("spinalScore"));assertFalse(normalized.contains("sessionStressScore"))
            val ongoing=root.getValue("ongoingWorkout").jsonObject
            assertFalse(ongoing.containsKey("readinessNeuralOverride"));assertEquals("Conservar",ongoing.getValue("sessionNotes").jsonPrimitive.content)
            assertEquals(0,ongoing.getValue("completedSets").jsonObject.getValue("s").jsonObject.getValue("reps").jsonPrimitive.int)
        }
    }
    @Test(expected=IllegalArgumentException::class) fun `conflicting identities abort instead of losing originals`() {
        LegacyTrainingBackup.normalize("""{"schemaVersion":6,"sleepLogs":[{"id":"same","date":"2026-10-01","duration":7},{"id":"same","date":"2026-10-01","duration":8}]}""")
    }
}

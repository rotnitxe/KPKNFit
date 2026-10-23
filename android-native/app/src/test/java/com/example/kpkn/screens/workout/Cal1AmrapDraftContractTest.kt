package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class Cal1AmrapDraftContractTest {
    private val planned = ExerciseSet("s", targetReps = 6, purpose = SetPurpose.AMRAP, amrapMinimumReps = 4)

    @Test fun oldDraftWithoutAMinimumKeepsFollowingThePlan() {
        val old = Json.decodeFromString<WorkoutSetDraft>("""{"valueText":"5"}""")
        assertEquals(4, resolveAmrapMinimumReps(planned, old))
    }

    @Test fun explicitlyNoMinimumSurvivesDraftJsonAndRecordedSetRestoration() {
        val codec = Json { encodeDefaults = true }
        val draft = WorkoutSetDraft(amrapMinimumReps = null, amrapMinimumOverridden = true)
        val restored = codec.decodeFromString<WorkoutSetDraft>(codec.encodeToString(draft))
        assertNull(resolveAmrapMinimumReps(planned, restored))
        assertNull(resolveAmrapMinimumReps(planned, recordedSet = CompletedSet("done", purpose = SetPurpose.AMRAP)))
    }

    @Test fun restoringAnExplicitLiveOptOutDoesNotReactivateThePrescription() {
        val done = CompletedSet("done", purpose = SetPurpose.NONE)
        assertFalse(resolveAmrapActive(planned, requestedOverride = false, recordedSet = done))
        assertTrue(resolveAmrapActive(planned, requestedOverride = true, recordedSet = done))
        assertTrue(resolveAmrapActive(planned, requestedOverride = false, recordedSet = CompletedSet("legacy")))
    }
}

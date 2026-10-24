package com.example.kpkn.domain.multisport.functional

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FunctionalSportModelsTest {
    @Test
    fun jsonRoundTripKeepsCappedPartialDistinctFromFinishAndUnknownProgress() {
        val payload = FunctionalSportPayload(
            definition = FunctionalWorkoutDefinition(
                sportId = com.example.kpkn.domain.multisport.SportId.CROSSFIT,
                workoutId = "wod-1",
                workoutRevision = "rev-1",
                title = "For time",
                format = FunctionalWorkoutFormat.FOR_TIME,
                scaling = FunctionalScalingSnapshot("custom", "Custom"),
                targetSequence = listOf(
                    FunctionalMovementTarget("a", "Burpees", FunctionalQuantity(10.0, FunctionalQuantityUnit.REPETITIONS)),
                ),
                timeCapMillis = 600_000L,
            ),
            result = FunctionalWorkoutResult(
                forTime = FunctionalForTimeOutcome(
                    cappedPartial = FunctionalForTimeCappedPartial(elapsedMillis = 600_000L, progress = null),
                ),
            ),
        )

        val decoded = Json.decodeFromJsonElement<FunctionalSportPayload>(payload.toJsonObject())

        assertEquals(payload, decoded)
        assertTrue(FunctionalSportEngine.crossfit.validatePayload(decoded, com.example.kpkn.domain.multisport.tracking.SportDocumentPurpose.RESULT).isEmpty())
        assertNull(decoded.result!!.forTime!!.cappedPartial!!.progress)
        assertNull(decoded.result!!.forTime!!.finish)
    }
}

package com.example.kpkn.domain.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutTagLoadHomologationTest {
    @Test fun `uses destination history and clamps source change to ten percent`() {
        val destination = listOf(
            WorkoutTagCapacityObservation("d1", 100, 100.0),
            WorkoutTagCapacityObservation("d2", 200, 110.0),
        )
        val source = listOf(
            WorkoutTagCapacityObservation("s1", 90, 100.0),
            WorkoutTagCapacityObservation("s2", 150, 100.0),
            WorkoutTagCapacityObservation("s3", 300, 130.0),
            WorkoutTagCapacityObservation("s4", 400, 140.0),
        )
        // Destination median 105, source ratio 1.35 is limited to 1.10.
        assertEquals(115.5, WorkoutTagLoadHomologation.adjustedDestinationCapacityKg(
            destinationTagId = "hammer",
            sourceTagId = "movement",
            destination = destination,
            source = source,
        )!!, 1e-8)
    }

    @Test fun `requires own destination history and two source sessions on each side`() {
        val destination = listOf(WorkoutTagCapacityObservation("d1", 100, 100.0))
        val source = listOf(
            WorkoutTagCapacityObservation("s1", 90, 100.0),
            WorkoutTagCapacityObservation("s2", 150, 110.0),
            WorkoutTagCapacityObservation("s3", 300, 120.0),
        )
        assertNull(WorkoutTagLoadHomologation.adjustedDestinationCapacityKg("d", "s", destination, source))
        assertNull(WorkoutTagLoadHomologation.adjustedDestinationCapacityKg(
            "d", "s", destination + WorkoutTagCapacityObservation("d2", 200, 105.0), source,
        ))
    }

    @Test fun `duplicate sessions do not inflate evidence and same tag is not homologated`() {
        val destination = listOf(
            WorkoutTagCapacityObservation("d1", 100, 100.0),
            WorkoutTagCapacityObservation("d1", 101, 101.0),
            WorkoutTagCapacityObservation("d2", 200, 100.0),
        )
        val source = listOf(
            WorkoutTagCapacityObservation("s1", 90, 100.0),
            WorkoutTagCapacityObservation("s2", 95, 100.0),
            WorkoutTagCapacityObservation("s3", 300, 105.0),
            WorkoutTagCapacityObservation("s4", 400, 105.0),
        )
        assertNull(WorkoutTagLoadHomologation.adjustedDestinationCapacityKg("same", "same", destination, source))
        assertEquals(105.525, WorkoutTagLoadHomologation.adjustedDestinationCapacityKg("d", "s", destination, source)!!, 1e-8)
    }
}

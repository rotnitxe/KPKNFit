package com.example.kpkn.data.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedExerciseLoadReferenceTest {
    private fun reference(kind: SharedLoadReferenceKind = SharedLoadReferenceKind.ESTIMATED_PR) =
        SharedExerciseLoadReference(
            id = "id", exerciseKey = "bench", configurationId = "barbell", tagId = "hammer",
            unitMode = UnitModeV2.REPS, loadMode = LoadModeV2.LOAD,
            quantityConvention = LoadQuantityConvention.TOTAL_EXTERNAL, kind = kind,
            baseValueKg = 100.0, sourceAtMs = 100,
        )

    @Test fun `estimated pr uses median of up to three later distinct sessions`() {
        val one = reference().withComparableObservation(ComparableCapacityObservation("a", 110, 108.0))
        assertEquals(100.0, one.suggestedValueKg(), 0.0)
        val two = one.withComparableObservation(ComparableCapacityObservation("b", 120, 112.0))
        assertEquals(110.0, two.suggestedValueKg(), 0.0)
        val three = two.withComparableObservation(ComparableCapacityObservation("c", 130, 116.0))
        assertEquals(112.0, three.suggestedValueKg(), 0.0)
        val duplicate = three.withComparableObservation(ComparableCapacityObservation("c", 130, 120.0))
        assertEquals(3, duplicate.comparableObservations.size)
        assertEquals(112.0, duplicate.suggestedValueKg(), 0.0)
    }

    @Test fun `direct references stay fixed and identities include every load axis`() {
        val fixed = reference(SharedLoadReferenceKind.ONE_RM)
            .withComparableObservation(ComparableCapacityObservation("a", 200, 150.0))
        assertEquals(100.0, fixed.suggestedValueKg(), 0.0)
        assertTrue(reference().sameIdentity(reference()))
        assertFalse(reference().sameIdentity(reference().copy(configurationId = "dumbbell")))
        assertFalse(reference().sameIdentity(reference().copy(tagId = "movement")))
        assertFalse(reference().sameIdentity(reference().copy(quantityConvention = LoadQuantityConvention.PER_IMPLEMENT)))
    }
}

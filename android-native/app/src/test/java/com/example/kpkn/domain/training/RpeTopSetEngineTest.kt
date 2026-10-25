package com.example.kpkn.domain.training

import com.example.kpkn.data.protocols.LiftSlot
import org.junit.Assert.*
import org.junit.Test

class RpeTopSetEngineTest {
    private val hit = RpeTopSetHit(LiftSlot.SQUAT, 3, 8.0, 100.0, 3, 8.0)

    @Test fun increase_stays_below_five_percent_after_rounding() {
        val result = RpeTopSetEngine.evaluate(hit, 90.0, 0.9)!!
        assertTrue(result.estimatedOneRmKg > 100.0)
        assertTrue(result.tmDeltaKg > 0.0 && result.tmDeltaKg <= 4.5)
        assertEquals(0.0, result.tmDeltaKg % 2.5, 0.0)
    }

    @Test fun decrease_stays_within_five_percent() {
        val result = RpeTopSetEngine.evaluate(hit, 120.0, 0.9)!!
        assertTrue(result.tmDeltaKg < 0.0 && result.tmDeltaKg >= -6.0)
    }

    @Test fun single_at_rpe_ten_does_not_inflate_the_observed_maximum() {
        val single = hit.copy(prescribedReps = 1, prescribedRpe = 10.0, actualReps = 1, actualRpe = 10.0)
        val result = RpeTopSetEngine.evaluate(single, 90.0, 0.9)!!
        assertEquals(100.0, result.estimatedOneRmKg, 0.0)
        assertEquals(0.0, result.tmDeltaKg, 0.0)
    }

    @Test fun dead_band_and_small_reference_do_not_invent_a_minimum_step() {
        val implied = RpeTopSetEngine.evaluate(hit, 90.0, 0.9)!!.impliedTrainingMaxKg
        assertEquals(0.0, RpeTopSetEngine.evaluate(hit, implied + 0.5, 0.9)!!.tmDeltaKg, 0.0)
        assertEquals(0.0, RpeTopSetEngine.evaluate(hit, 25.0, 0.9)!!.tmDeltaKg, 0.0)
    }

    @Test fun invalid_or_missing_evidence_does_not_propose() {
        assertNull(RpeTopSetEngine.evaluate(hit, null, 0.9))
        assertNull(RpeTopSetEngine.evaluate(hit, Double.NaN, 0.9))
        assertNull(RpeTopSetEngine.evaluate(hit.copy(actualRpe = Double.NaN), 90.0, 0.9))
        assertNull(RpeTopSetEngine.evaluate(hit.copy(actualReps = 0), 90.0, 0.9))
        assertNull(RpeTopSetEngine.evaluate(hit.copy(actualWeightKg = Double.POSITIVE_INFINITY), 90.0, 0.9))
        assertNull(RpeTopSetEngine.evaluate(hit, 90.0, 90.0))
    }
}

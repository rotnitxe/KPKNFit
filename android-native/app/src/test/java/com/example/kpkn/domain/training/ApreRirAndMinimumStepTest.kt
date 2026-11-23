package com.example.kpkn.domain.training

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApreRirAndMinimumStepTest {

    @Test
    fun smallDropOnLightLoadAlwaysStepsDownByAtLeastOneIncrement() {
        // 40 kg × 3 reps in APRE 6 is DROP_SMALL (-2.5%). Previously rounded back to 40.0 kg.
        val next = Apre.nextLoadKg(
            loadKg = 40.0,
            validReps = 3,
            variant = ApreVariant.APRE_6,
            incrementKg = 2.5,
        )
        assertEquals(37.5, next, 0.001)
    }

    @Test
    fun largeRaiseStepsUpByAtLeastOneIncrementOnLightLoadAndTwoOnHeavyLoad() {
        val nextLight = Apre.nextLoadKg(
            loadKg = 40.0,
            validReps = 13,
            variant = ApreVariant.APRE_6,
            incrementKg = 2.5,
        )
        assertEquals(42.5, nextLight, 0.001)

        val nextHeavy = Apre.nextLoadKg(
            loadKg = 100.0,
            validReps = 13,
            variant = ApreVariant.APRE_6,
            incrementKg = 2.5,
        )
        assertEquals(105.0, nextHeavy, 0.001)
    }

    @Test
    fun rirAndRpeReservePreventsFalseDropOnApreTable() {
        // 4 valid reps in APRE 6 would be DROP_SMALL, but with RIR 2 effective reps = 6 -> HOLD.
        val effective = Apre.effectiveRepsForTable(validReps = 4, targetRir = 2, targetRpe = null)
        assertEquals(6, effective)
        val nextWithRir = Apre.nextLoadKg(
            loadKg = 100.0,
            validReps = 4,
            variant = ApreVariant.APRE_6,
            targetRir = 2,
            targetRpe = null,
            incrementKg = 2.5,
        )
        assertEquals(100.0, nextWithRir, 0.001)

        // RPE 8 -> reserve = 2 -> effective = 6 -> HOLD.
        val nextWithRpe = Apre.nextLoadKg(
            loadKg = 100.0,
            validReps = 4,
            variant = ApreVariant.APRE_6,
            targetRir = null,
            targetRpe = 8.0,
            incrementKg = 2.5,
        )
        assertEquals(100.0, nextWithRpe, 0.001)
    }
}

package com.example.kpkn.domain.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** LV-C: el % de una aproximación es de la carga de TRABAJO; la capacidad (1RM) solo cubre su ausencia. */
class WarmupLoadBaseTest {
    @Test
    fun workingLoad100WithA1Rm130GivesFiftyKgForAFiftyPercentWarmup() {
        assertEquals(50.0, warmupTargetLoadKg(0.5, workingLoadKg = 100.0, referenceCapacityKg = 130.0), 1e-9)
        assertEquals(100.0, warmupBaseLoadKg(100.0, 130.0)!!, 0.0)
    }

    @Test
    fun theLegacyFiftyRepresentationAndTheFractionGiveTheSameLoad() {
        assertEquals(50.0, warmupTargetLoadKg(50.0, 100.0, 130.0), 1e-9)
        assertEquals(60.0, warmupTargetLoadKg(0.6, 100.0, 130.0), 1e-9)
        assertEquals(85.0, warmupTargetLoadKg(85.0, 100.0, 130.0), 1e-9)
    }

    @Test
    fun withoutAWorkingLoadTheCapacityCoversTheAbsence() {
        assertEquals(65.0, warmupTargetLoadKg(0.5, workingLoadKg = null, referenceCapacityKg = 130.0), 1e-9)
        assertEquals(65.0, warmupTargetLoadKg(0.5, workingLoadKg = 0.0, referenceCapacityKg = 130.0), 1e-9)
        assertEquals(130.0, warmupBaseLoadKg(null, 130.0)!!, 0.0)
    }

    @Test
    fun withNeitherThereIsNoBaseAndNoInventedKilograms() {
        assertNull(warmupBaseLoadKg(null, null))
        assertNull(warmupBaseLoadKg(0.0, -3.0))
        assertEquals(0.0, warmupTargetLoadKg(0.5, null, null), 0.0)
        assertEquals(0.0, warmupTargetLoadKg(0.5, Double.NaN, null), 0.0)
    }
}

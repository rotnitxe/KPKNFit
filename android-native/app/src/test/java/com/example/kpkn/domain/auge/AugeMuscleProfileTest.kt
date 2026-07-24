package com.example.kpkn.domain.auge

import org.junit.Assert.assertEquals
import org.junit.Test

class AugeMuscleProfileTest {

    @Test
    fun quadricepsAndDeltoidHeadsResolveToPillars() {
        assertEquals("Cuádriceps", getAugeMusclePillarId("Cuadriceps"))
        assertEquals("Cuádriceps", getAugeMusclePillarId("Cuádriceps"))
        assertEquals("Deltoides", getAugeMusclePillarId("Deltoides Lateral"))
        assertEquals("Deltoides Lateral", getAugeMuscleDisplayId("Deltoides Lateral"))
    }
}

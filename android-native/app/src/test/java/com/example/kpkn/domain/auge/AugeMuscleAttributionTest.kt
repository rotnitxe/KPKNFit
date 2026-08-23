package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.Settings
import org.junit.Assert.assertTrue
import org.junit.Test

class AugeMuscleAttributionTest {
    @Test
    fun chestAndTricepsReceivePositiveLocalImpact_once() {
        val impact = MuscularSessionImpactEngine.evaluate(
            completedExercises = AugeRealSessionFixtures.completedExercises,
            completionInstantIso = "2026-08-23T17:25:00-04:00",
            exerciseDb = AugeRealSessionFixtures.exerciseDb,
            settings = Settings(),
        )
        assertTrue(impact.perMuscle["Pectorales"]!!.stressUnits > 0.0)
        assertTrue(impact.perMuscle["Tríceps"]!!.stressUnits > 0.0)
        assertTrue(impact.perMuscle["Pectorales"]!!.directStressUnits > 0.0)
        assertTrue(impact.perMuscle["Tríceps"]!!.indirectStressUnits > 0.0)
        assertTrue(impact.perMuscle["Tríceps"]!!.directStressUnits > 0.0)
    }

    @Test
    fun reorderingLegExercises_doesNotChangeUpperBodyImpact() {
        val base = AugeRealSessionFixtures.completedExercises
        val first = MuscularSessionImpactEngine.evaluate(base, "2026-08-23T17:25:00-04:00", AugeRealSessionFixtures.exerciseDb, Settings())
        val reordered = MuscularSessionImpactEngine.evaluate(
            base.sortedBy { if (it.exerciseId in setOf("bench", "incline", "fly", "shoulder", "french")) 0 else 1 },
            "2026-08-23T17:25:00-04:00",
            AugeRealSessionFixtures.exerciseDb,
            Settings(),
        )
        listOf("Pectorales", "Tríceps", "Deltoides").forEach { muscle ->
            assertTrue(kotlin.math.abs(first.perMuscle[muscle]!!.stressUnits - reordered.perMuscle[muscle]!!.stressUnits) < 0.001)
        }
    }

    @Test
    fun harderExecution_neverDrainsLess() {
        val base = MuscularSessionImpactEngine.evaluate(AugeRealSessionFixtures.completedExercises, "2026-08-23T17:25:00-04:00", AugeRealSessionFixtures.exerciseDb, Settings())
        val harder = MuscularSessionImpactEngine.evaluate(AugeRealSessionFixtures.harder(), "2026-08-23T17:25:00-04:00", AugeRealSessionFixtures.exerciseDb, Settings())
        base.involvedVolumeMuscles.forEach { muscle ->
            assertTrue("$muscle", harder.perMuscle[muscle]!!.stressUnits >= base.perMuscle[muscle]!!.stressUnits)
        }
    }
}

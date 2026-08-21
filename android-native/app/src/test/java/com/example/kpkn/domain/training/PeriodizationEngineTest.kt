package com.example.kpkn.domain.training

import com.example.kpkn.data.models.MesocycleGoal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PeriodizationEngineTest {

    @Test
    fun scaleSets_increases_for_accumulation_and_shrinks_for_deload() {
        val accumulation = PeriodizationEngine.scaleSets(baseSets = 3, goal = MesocycleGoal.ACCUMULATION, volumeModifier = 1.0)
        val deload = PeriodizationEngine.scaleSets(baseSets = 3, goal = MesocycleGoal.DELOAD, volumeModifier = 1.0)
        assertTrue(accumulation > deload)
    }

    @Test
    fun scaleSets_applies_protocol_volumeModifier() {
        val boosted = PeriodizationEngine.scaleSets(baseSets = 3, goal = MesocycleGoal.INTENSIFICATION, volumeModifier = 1.5)
        val reduced = PeriodizationEngine.scaleSets(baseSets = 3, goal = MesocycleGoal.INTENSIFICATION, volumeModifier = 0.5)
        assertTrue(boosted > reduced)
    }

    @Test
    fun scaleSets_never_returns_less_than_one() {
        val sets = PeriodizationEngine.scaleSets(baseSets = 3, goal = MesocycleGoal.DELOAD, volumeModifier = 0.1)
        assertTrue(sets >= 1)
    }

    @Test
    fun percentageForWeek_ramps_from_min_to_max_across_the_block() {
        val week1 = PeriodizationEngine.percentageForWeek(intensityMin = 60, intensityMax = 80, weekNumber = 1, totalWeeksInBlock = 4)
        val week4 = PeriodizationEngine.percentageForWeek(intensityMin = 60, intensityMax = 80, weekNumber = 4, totalWeeksInBlock = 4)
        assertEquals(60.0, week1, 0.001)
        assertEquals(80.0, week4, 0.001)
        assertTrue(week4 > week1)
    }

    @Test
    fun percentageForWeek_returns_midpoint_for_single_week_block() {
        val pct = PeriodizationEngine.percentageForWeek(intensityMin = 70, intensityMax = 90, weekNumber = 1, totalWeeksInBlock = 1)
        assertEquals(80.0, pct, 0.001)
    }

    @Test
    fun percentageForWeek_can_unload_from_peak_to_taper() {
        val weeks = (1..4).map {
            PeriodizationEngine.percentageForWeek(60, 82, it, 4, descending = true)
        }
        assertEquals(82.0, weeks[0], 0.001)
        assertEquals(74.6667, weeks[1], 0.001)
        assertEquals(67.3333, weeks[2], 0.001)
        assertEquals(60.0, weeks[3], 0.001)
        assertTrue(weeks.zipWithNext().all { (from, to) -> to <= from })
    }

    @Test
    fun prescriptionFor_varies_reps_and_rpe_by_goal() {
        val accumulation = PeriodizationEngine.prescriptionFor(
            goal = MesocycleGoal.ACCUMULATION,
            baseSets = 3,
            baseReps = 5,
            volumeModifier = 1.0,
            intensityMin = 60,
            intensityMax = 80,
            weekNumber = 1,
            totalWeeksInBlock = 4,
        )
        val realization = PeriodizationEngine.prescriptionFor(
            goal = MesocycleGoal.REALIZATION,
            baseSets = 3,
            baseReps = 5,
            volumeModifier = 1.0,
            intensityMin = 60,
            intensityMax = 80,
            weekNumber = 1,
            totalWeeksInBlock = 4,
        )
        assertTrue(accumulation.reps > realization.reps)
        assertTrue(accumulation.rpe < realization.rpe)
        assertTrue(accumulation.sets > realization.sets)
    }
}

package com.example.kpkn.screens.workout.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntensityTechniqueBackLogicTest {

    @Test
    fun lastUncheckedRow_doesNotAutoCommit() {
        assertFalse(shouldAutoCommitTechniqueRows(listOf(true, false)))
    }

    @Test
    fun allChecked_autoCommits() {
        assertTrue(shouldAutoCommitTechniqueRows(listOf(true, true)))
    }

    @Test
    fun empty_doesNotAutoCommit() {
        assertFalse(shouldAutoCommitTechniqueRows(emptyList()))
    }

    @Test
    fun suggestedDrops_areAboutFiveKgLighter() {
        val loads = suggestedDropLoadsForMainSet(mainWeight = 100.0, mainReps = 8, count = 2)
        assertEquals(2, loads.size)
        assertEquals(95.0, loads[0], 0.51)
        assertEquals(90.0, loads[1], 0.51)
        assertTrue(loads.all { it < 100.0 })
        assertTrue(loads[1] <= loads[0])
    }
}

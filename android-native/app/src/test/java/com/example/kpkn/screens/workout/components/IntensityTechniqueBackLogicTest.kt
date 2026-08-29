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
    fun suggestedDrops_areLighterThanMainSet() {
        val loads = suggestedDropLoadsForMainSet(mainWeight = 100.0, mainReps = 8, count = 2)
        assertEquals(2, loads.size)
        assertTrue(loads.all { it < 100.0 })
        assertTrue(loads[1] <= loads[0])
    }
}

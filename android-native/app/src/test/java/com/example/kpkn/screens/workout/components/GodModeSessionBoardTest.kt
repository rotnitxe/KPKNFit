package com.example.kpkn.screens.workout.components

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.SupersetGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GodModeSessionBoardTest {

    @Test
    fun cluster_keepsStandaloneExercisesSeparate() {
        val exercises = listOf(
            Exercise(id = "a", name = "Press"),
            Exercise(id = "b", name = "Remo"),
        )
        val clusters = clusterGodModeBoardExercises(exercises, emptyList())
        assertEquals(2, clusters.size)
        assertNull(clusters[0].groupId)
        assertEquals(listOf("a"), clusters[0].exercises.map { it.id })
        assertEquals(listOf("b"), clusters[1].exercises.map { it.id })
    }

    @Test
    fun cluster_groupsSupersetMembersFromTable() {
        val exercises = listOf(
            Exercise(id = "a", name = "Press"),
            Exercise(id = "b", name = "Aperturas"),
            Exercise(id = "c", name = "Remo"),
        )
        val groups = listOf(
            SupersetGroup(id = "ss1", exerciseOrder = listOf("a", "b")),
        )
        val clusters = clusterGodModeBoardExercises(exercises, groups)
        assertEquals(2, clusters.size)
        assertEquals("ss1", clusters[0].groupId)
        assertEquals(listOf("a", "b"), clusters[0].exercises.map { it.id })
        assertNull(clusters[1].groupId)
        assertEquals(listOf("c"), clusters[1].exercises.map { it.id })
    }
}

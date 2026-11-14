package com.example.kpkn.screens.programdetail.design.structure

import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramBlockCreationTest {
    private val sourceWeek = ProgramWeek(
        id = "source-week",
        name = "Semana base",
        sessions = listOf(Session(
            id = "source-session",
            name = "Día A",
            exercises = listOf(Exercise(
                id = "source-exercise",
                name = "Sentadilla",
                sets = listOf(ExerciseSet("source-set", targetReps = 5)),
            )),
        )),
    )

    @Test
    fun copied_block_uses_requested_week_count_and_fresh_nested_ids() {
        val block = newProgramBlock(
            name = "Fuerza",
            description = "Bloque base",
            goal = BlockGoal.ACCUMULATION,
            scheme = null,
            weekCount = 3,
            sourceWeek = sourceWeek,
        )
        val weeks = block.mesocycles.single().weeks

        assertEquals(3, weeks.size)
        assertEquals(3, weeks.map { it.id }.distinct().size)
        assertFalse(weeks.any { it.id == sourceWeek.id })
        val sessions = weeks.map { it.sessions.single() }
        assertEquals(3, sessions.map { it.id }.distinct().size)
        assertFalse(sessions.any { it.id == "source-session" })
        val exerciseIds = sessions.map { it.exercises.single().id }
        assertEquals(3, exerciseIds.distinct().size)
        assertFalse(exerciseIds.contains("source-exercise"))
        assertEquals(5, sessions.first().exercises.single().sets.single().targetReps)
        assertFalse(block.materializationPending)
    }

    @Test
    fun blank_block_has_no_fabricated_sessions_and_can_be_materialized_later() {
        val block = newProgramBlock(
            name = "Bloque vacío",
            description = null,
            goal = null,
            scheme = null,
            weekCount = 2,
            sourceWeek = null,
        )

        assertTrue(block.materializationPending)
        assertTrue(block.mesocycles.single().weeks.all { it.sessions.isEmpty() })
        assertNotEquals("source-week", block.mesocycles.single().weeks.first().id)
    }
}

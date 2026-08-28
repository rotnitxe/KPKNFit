package com.example.kpkn.domain.workout

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.WeekVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutStructuralEditorTest {

    @Test
    fun replaceExerciseByIdUpdatesNestedPart() {
        val session = Session(
            id = "s1",
            name = "Push",
            parts = listOf(
                SessionPart(
                    id = "p1",
                    name = "Main",
                    exercises = listOf(ex("a", "A"), ex("b", "B")),
                ),
            ),
        )
        val updated = WorkoutStructuralEditor.replaceExerciseById(session, "b") {
            it.copy(name = "B2")
        }
        assertEquals("B2", updated.parts.single().exercises[1].name)
        assertEquals("A", updated.parts.single().exercises[0].name)
    }

    @Test
    fun moveExerciseByIdSwapsWithinPart() {
        val session = Session(
            id = "s1",
            name = "Push",
            parts = listOf(
                SessionPart(
                    id = "p1",
                    name = "Main",
                    exercises = listOf(ex("a", "A"), ex("b", "B"), ex("c", "C")),
                ),
            ),
        )
        val moved = WorkoutStructuralEditor.moveExerciseById(session, "a", direction = 1)
        assertEquals(listOf("b", "a", "c"), moved.parts.single().exercises.map { it.id })
    }

    @Test
    fun withModeSessionUpdatesVariantB() {
        val base = Session(id = "s1", name = "A", exercises = listOf(ex("a", "A")))
        val withB = base.copy(sessionB = Session(id = "s1b", name = "B", exercises = listOf(ex("b", "B"))))
        val updated = WorkoutStructuralEditor.withModeSession(withB, WeekVariant.B) { mode ->
            WorkoutStructuralEditor.replaceExerciseById(mode, "b") { it.copy(name = "B2") }
        }
        assertEquals("B2", updated.sessionB?.exercises?.single()?.name)
        assertEquals("A", updated.exercises.single().name)
    }

    @Test
    fun insertAfterSupersetMembers() {
        val session = Session(
            id = "s1",
            name = "Push",
            exercises = listOf(ex("a", "A"), ex("b", "B"), ex("c", "C")),
        )
        val inserted = WorkoutStructuralEditor.insertExerciseAfterSupersetMembers(
            session = session,
            memberIds = listOf("a", "b"),
            exercise = ex("x", "X"),
        )
        assertEquals(listOf("a", "b", "x", "c"), inserted.exercises.map { it.id })
    }

    @Test
    fun reorderExercisesByIdsKeepsMissingAtEnd() {
        val session = Session(
            id = "s1",
            name = "Push",
            exercises = listOf(ex("a", "A"), ex("b", "B"), ex("c", "C")),
        )
        val reordered = WorkoutStructuralEditor.reorderExercisesByIds(
            session = session,
            partId = null,
            orderedExerciseIds = listOf("c", "a"),
        )
        assertEquals(listOf("c", "a", "b"), reordered.exercises.map { it.id })
        assertTrue(reordered.exercises.size == 3)
    }

    @Test
    fun removeSetFromExerciseKeepsAtLeastOne() {
        val session = Session(
            id = "s1",
            name = "Push",
            exercises = listOf(
                Exercise(
                    id = "a",
                    name = "A",
                    sets = listOf(ExerciseSet(id = "s1"), ExerciseSet(id = "s2")),
                ),
            ),
        )
        val oneLeft = WorkoutStructuralEditor.removeSetFromExercise(session, "a", 0)
        assertEquals(1, oneLeft.exercises.single().sets.size)
        val blocked = WorkoutStructuralEditor.removeSetFromExercise(oneLeft, "a", 0)
        assertEquals(1, blocked.exercises.single().sets.size)
    }

    @Test
    fun removeExerciseByIdLeavesAtLeastOne() {
        val session = Session(
            id = "s1",
            name = "Push",
            exercises = listOf(ex("a", "A"), ex("b", "B")),
        )
        val removed = WorkoutStructuralEditor.removeExerciseById(session, "a")
        assertEquals(listOf("b"), removed.exercises.map { it.id })
        val blocked = WorkoutStructuralEditor.removeExerciseById(removed, "b")
        assertEquals(listOf("b"), blocked.exercises.map { it.id })
    }

    @Test
    fun removeExerciseByIdRemovesSupersetMember() {
        val withGroup = Session(
            id = "s1",
            name = "Push",
            exercises = listOf(
                Exercise(id = "a", name = "A", sets = listOf(ExerciseSet(id = "a_s1")), supersetGroupRef = "g1"),
                Exercise(id = "b", name = "B", sets = listOf(ExerciseSet(id = "b_s1")), supersetGroupRef = "g1"),
                Exercise(id = "c", name = "C", sets = listOf(ExerciseSet(id = "c_s1"))),
            ),
            supersetGroups = listOf(
                com.example.kpkn.data.models.SupersetGroup(
                    id = "g1",
                    exerciseOrder = listOf("a", "b"),
                ),
            ),
        )
        val removed = WorkoutStructuralEditor.removeExerciseById(withGroup, "a")
        assertEquals(listOf("b", "c"), removed.exercises.map { it.id })
        assertTrue(removed.supersetGroups.none { it.id == "g1" })
    }

    @Test
    fun removeExercisesByIdsLeavesAtLeastOne() {
        val session = Session(
            id = "s1",
            name = "Push",
            exercises = listOf(ex("a", "A"), ex("b", "B"), ex("c", "C")),
        )
        val removed = WorkoutStructuralEditor.removeExercisesByIds(session, listOf("a", "b", "c"))
        assertEquals(1, removed.exercises.size)
        assertTrue(removed.exercises.single().id in listOf("a", "b", "c"))
    }

    private fun ex(id: String, name: String) = Exercise(
        id = id,
        name = name,
        sets = listOf(ExerciseSet(id = "${id}_s1")),
    )
}

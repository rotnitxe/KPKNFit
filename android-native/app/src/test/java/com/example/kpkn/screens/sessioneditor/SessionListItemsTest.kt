package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionListItemsTest {

    @Test
    fun buildSessionListItems_startsWithHeroAndEndsWithAddActions() {
        val session = Session(id = "s1", name = "Push")
        val items = buildSessionListItems(session)
        assertTrue(items.first() is SessionListItem.Hero)
        assertTrue(items.last() is SessionListItem.AddActions)
    }

    @Test
    fun buildSessionListItems_includesLooseExercises() {
        val ex = Exercise(id = "e1", name = "Bench", exerciseDbId = "bench")
        val session = Session(id = "s1", name = "Pull", exercises = listOf(ex))
        val items = buildSessionListItems(session)
        assertTrue(items.any { it is SessionListItem.LooseExercise && it.exerciseId == "e1" })
    }

    @Test
    fun buildSessionListItems_collapsedPartSkipsExercisesAndAddButton() {
        val part = SessionPart(
            id = "p1",
            name = "Chest",
            exercises = listOf(Exercise(id = "e1", name = "Fly", exerciseDbId = "fly")),
        )
        val session = Session(id = "s1", name = "Legs", parts = listOf(part))
        val items = buildSessionListItems(session, collapsedPartIds = setOf("p1"))
        assertTrue(items.any { it is SessionListItem.PartHeader && it.partId == "p1" })
        assertTrue(items.none { it is SessionListItem.PartExercise })
        assertTrue(items.none { it is SessionListItem.PartAddExercise })
    }

    @Test
    fun buildSessionListItems_emptyPartIncludesAddExerciseFooter() {
        val part = SessionPart(id = "p1", name = "Chest", exercises = emptyList())
        val session = Session(id = "s1", name = "Push", parts = listOf(part))
        val items = buildSessionListItems(session)
        assertTrue(items.any { it is SessionListItem.PartHeader && it.partId == "p1" })
        assertTrue(items.any { it is SessionListItem.PartAddExercise && it.partId == "p1" })
    }

    @Test
    fun findListIndexForExercise_returnsCorrectIndex() {
        val ex = Exercise(id = "e1", name = "Row", exerciseDbId = "row")
        val session = Session(id = "s1", name = "Pull", exercises = listOf(ex))
        val items = buildSessionListItems(session)
        val index = findListIndexForExercise(items, "e1")
        assertTrue(index > 0)
        assertEquals(SessionListItem.LooseExercise::class, items[index]::class)
    }

    @Test
    fun lazyColumnIndexForExercise_accountsForHeroOffset() {
        val ex = Exercise(id = "e1", name = "Row", exerciseDbId = "row")
        val session = Session(id = "s1", name = "Pull", exercises = listOf(ex))
        val full = buildSessionListItems(session)
        val scrollable = full.drop(1).dropLast(1)
        val lazyIndex = lazyColumnIndexForExercise(scrollable, "e1")
        // LazyColumn: [0]=Hero, [1]=first scrollable item
        assertEquals(1, lazyIndex)
        assertEquals(findListIndexForExercise(full, "e1"), lazyIndex)
    }

    @Test
    fun isUncategorizedPart_filtersSinGrupo() {
        val part = SessionPart(id = "p1", name = "Sin grupo")
        assertTrue(part.isUncategorizedPart())
    }
}

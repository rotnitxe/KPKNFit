package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionListItemsTest {

    @Test
    fun buildSessionListItems_startsWithHeroAndEndsWithStrengthAddActions() {
        val session = Session(id = "s1", name = "Push")
        val items = buildSessionListItems(session)
        assertTrue(items.first() is SessionListItem.Hero)
        assertTrue(items.last() is SessionListItem.StrengthAddActions)
    }

    @Test
    fun buildSessionListItems_includesLooseExercises() {
        val ex = Exercise(id = "e1", name = "Bench", exerciseDbId = "bench")
        val session = Session(id = "s1", name = "Pull", exercises = listOf(ex))
        val items = buildSessionListItems(session)
        assertTrue(items.any { it is SessionListItem.LooseExercise && it.exerciseId == "e1" })
    }

    @Test
    fun buildSessionListItems_keepsCardioAtItsInsertionPosition() {
        val first = Exercise(id = "e1", name = "Press", exerciseDbId = "press")
        val cardio = Exercise(
            id = "c1",
            name = "Bicicleta",
            cardioDetails = CardioDetails(type = CardioType.BIKE_STATIONARY),
        )
        val last = Exercise(id = "e2", name = "Remo", exerciseDbId = "row")
        val items = buildSessionListItems(
            Session(id = "s1", name = "Pull", exercises = listOf(first, cardio, last)),
        )

        assertEquals(
            listOf("e1", "c1", "e2"),
            items.filterIsInstance<SessionListItem.LooseExercise>().map { it.exerciseId },
        )
    }

    @Test
    fun buildSessionListItems_keepsCardioAtItsInsertionPositionInsidePart() {
        val part = SessionPart(
            id = "p1",
            name = "Cardio y fuerza",
            exercises = listOf(
                Exercise(id = "e1", name = "Press", exerciseDbId = "press"),
                Exercise(
                    id = "c1",
                    name = "Bicicleta",
                    cardioDetails = CardioDetails(type = CardioType.BIKE_STATIONARY),
                ),
                Exercise(id = "e2", name = "Remo", exerciseDbId = "row"),
            ),
        )
        val items = buildSessionListItems(Session(id = "s1", name = "Pull", parts = listOf(part)))

        assertEquals(
            listOf("e1", "c1", "e2"),
            items.filterIsInstance<SessionListItem.PartExercise>().map { it.exerciseId },
        )
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
    fun buildSessionListItems_withCardioSpaceAppendsCardioDividerAndCardioPart() {
        val strengthPart = SessionPart(id = "p1", name = "Pecho", exercises = listOf(Exercise(id = "e1", name = "Press")))
        val cardioPart = SessionPart(id = "p2", name = "Espacio de cardio", isCardioGroup = true, exercises = listOf(Exercise(id = "c1", name = "Cinta")))
        val session = Session(id = "s1", name = "Mixto", parts = listOf(strengthPart, cardioPart))
        val items = buildSessionListItems(session)

        val headerP1Idx = items.indexOfFirst { it is SessionListItem.PartHeader && it.partId == "p1" }
        val strengthActionsIdx = items.indexOfFirst { it is SessionListItem.StrengthAddActions }
        val dividerIdx = items.indexOfFirst { it is SessionListItem.CardioDivider }
        val headerP2Idx = items.indexOfFirst { it is SessionListItem.PartHeader && it.partId == "p2" }

        assertTrue(headerP1Idx in 0..<strengthActionsIdx)
        assertTrue(strengthActionsIdx < dividerIdx)
        assertTrue(dividerIdx < headerP2Idx)
    }

    @Test
    fun buildSessionListItems_withCardioAtTopRendersCardioFirstAndStrengthDividerBelow() {
        val cardioPart = SessionPart(id = "p2", name = "Espacio de cardio", isCardioGroup = true, exercises = listOf(Exercise(id = "c1", name = "Cinta")))
        val strengthPart = SessionPart(id = "p1", name = "Pecho", exercises = listOf(Exercise(id = "e1", name = "Press")))
        val session = Session(id = "s1", name = "Mixto", parts = listOf(cardioPart, strengthPart))
        val items = buildSessionListItems(session)

        val cardioDividerIdx = items.indexOfFirst { it is SessionListItem.CardioDivider }
        val headerP2Idx = items.indexOfFirst { it is SessionListItem.PartHeader && it.partId == "p2" }
        val strengthDividerIdx = items.indexOfFirst { it is SessionListItem.StrengthDivider }
        val headerP1Idx = items.indexOfFirst { it is SessionListItem.PartHeader && it.partId == "p1" }
        val strengthActionsIdx = items.indexOfFirst { it is SessionListItem.StrengthAddActions }

        assertTrue(cardioDividerIdx in 0..<headerP2Idx)
        assertTrue(headerP2Idx < strengthDividerIdx)
        assertTrue(strengthDividerIdx < headerP1Idx)
        assertTrue(headerP1Idx < strengthActionsIdx)
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
        val scrollable = full.drop(1)
        val lazyIndex = lazyColumnIndexForExercise(scrollable, "e1")
        // LazyColumn: [0]=Hero, [1]=first scrollable item
        assertEquals(1, lazyIndex)
        assertEquals(findListIndexForExercise(full, "e1"), lazyIndex)
    }

    @Test
    fun buildSessionListItems_emptyWithoutCommitHasNoStrengthDivider() {
        val session = Session(id = "s1", name = "Push")
        val items = buildSessionListItems(session, showStrengthDivider = false)
        assertTrue(items.none { it is SessionListItem.StrengthDivider })
        assertTrue(items.any { it is SessionListItem.StrengthAddActions })
    }

    @Test
    fun buildSessionListItems_strengthCommittedShowsStrengthDividerWhenEmpty() {
        val session = Session(id = "s1", name = "Push")
        val items = buildSessionListItems(session, showStrengthDivider = true)
        val dividerIdx = items.indexOfFirst { it is SessionListItem.StrengthDivider }
        val actionsIdx = items.indexOfFirst { it is SessionListItem.StrengthAddActions }
        assertTrue(dividerIdx >= 0)
        assertTrue(dividerIdx < actionsIdx)
    }

    @Test
    fun buildSessionListItems_onlyCardioPart_respectsCardioAtStartPreference() {
        val cardioPart = SessionPart(
            id = "p2",
            name = "Espacio de cardio",
            isCardioGroup = true,
            exercises = listOf(Exercise(id = "c1", name = "Cinta")),
        )
        val session = Session(id = "s1", name = "Mixto", parts = listOf(cardioPart))

        val atStart = buildSessionListItems(session, cardioAtStart = true)
        val startCardioIdx = atStart.indexOfFirst { it is SessionListItem.CardioDivider }
        val startStrengthIdx = atStart.indexOfFirst { it is SessionListItem.StrengthDivider }
        assertTrue(startCardioIdx in 0..<startStrengthIdx)

        val atEnd = buildSessionListItems(session, showStrengthDivider = true, cardioAtStart = false)
        val endStrengthIdx = atEnd.indexOfFirst { it is SessionListItem.StrengthDivider }
        val endActionsIdx = atEnd.indexOfFirst { it is SessionListItem.StrengthAddActions }
        val endCardioIdx = atEnd.indexOfFirst { it is SessionListItem.CardioDivider }
        assertTrue(endStrengthIdx in 0..<endActionsIdx)
        assertTrue(endActionsIdx < endCardioIdx)
    }

    @Test
    fun isUncategorizedPart_filtersSinGrupo() {
        val part = SessionPart(id = "p1", name = "Sin grupo")
        assertTrue(part.isUncategorizedPart())
    }
}

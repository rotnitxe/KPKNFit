package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.SupersetGroup
import com.example.kpkn.data.models.supersetGroupRefOrLegacyId
import com.example.kpkn.domain.workout.SupersetRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionEditorMoveEngineTest {
    @Test
    fun blockMove_keepsMembersTogetherAndOrder() {
        val session = groupedSession(listOf("a", "b"), listOf("c"))

        val moved = SessionEditorMoveEngine.move(
            session,
            SessionEditorMoveRequest(
                sourcePartId = null,
                exerciseId = "a",
                targetPartId = null,
                targetIndex = 3,
                moveAsGroup = true,
            ),
        )

        assertEquals(listOf("c", "a", "b"), moved.exercises.map { it.id })
        assertEquals(listOf("a", "b"), moved.supersetGroups.single().exerciseOrder)
        assertEquals(setOf("g"), moved.exercises.filter { it.id != "c" }.mapNotNull { it.supersetGroupRefOrLegacyId() }.toSet())
    }

    @Test
    fun individualMove_outOfGroup_dissolvesOnlyWhenNecessary() {
        val session = groupedSession(listOf("a", "b", "c"), emptyList())

        val moved = SessionEditorMoveEngine.move(
            session,
            SessionEditorMoveRequest(null, "b", null, 3, moveAsGroup = false),
        )

        assertEquals(listOf("a", "c", "b"), moved.exercises.map { it.id })
        assertNull(moved.exercises.first { it.id == "b" }.supersetGroupRefOrLegacyId())
        assertEquals(listOf("a", "c"), moved.supersetGroups.single().exerciseOrder)
    }

    @Test
    fun individualMove_intoGroup_attachesAtMostFourMembers() {
        val session = groupedSession(listOf("a", "b"), listOf("c"))

        val moved = SessionEditorMoveEngine.move(
            session,
            SessionEditorMoveRequest(
                sourcePartId = null,
                exerciseId = "c",
                targetPartId = null,
                targetIndex = 1,
                moveAsGroup = false,
                targetGroupId = "g",
            ),
        )

        assertEquals(1, moved.supersetGroups.size)
        assertEquals(listOf("a", "c", "b"), moved.supersetGroups.single().exerciseOrder)
        assertTrue(moved.allExercises().all { it.supersetGroupRefOrLegacyId() == "g" })
    }

    @Test
    fun blockMove_nonContiguousGroupCorrectsOnlyRemovedPrefix() {
        val a = Exercise("a", "A", sets = listOf(ExerciseSet("a-set")), supersetGroupRef = "g")
        val b = Exercise("b", "B", sets = listOf(ExerciseSet("b-set")), supersetGroupRef = "g")
        val session = Session(
            id = "session",
            name = "Test",
            exercises = listOf(
                a,
                Exercise("x", "X", sets = listOf(ExerciseSet("x-set"))),
                b,
                Exercise("y", "Y", sets = listOf(ExerciseSet("y-set"))),
                Exercise("z", "Z", sets = listOf(ExerciseSet("z-set"))),
            ),
            supersetGroups = listOf(
                SupersetGroup(
                    id = "g",
                    exerciseOrder = listOf("a", "b"),
                    restBetweenExercises = 30,
                    restAfterSuperset = 90,
                ),
            ),
        )

        val moved = SessionEditorMoveEngine.move(
            session,
            SessionEditorMoveRequest(null, "a", null, targetIndex = 2, moveAsGroup = true),
        )

        assertEquals(listOf("x", "a", "b", "y", "z"), moved.exercises.map { it.id })
    }

    @Test
    fun move_doesNotMixCardioAndStrengthZones() {
        val strength = Exercise(id = "strength", name = "Press", sets = listOf(ExerciseSet("s")))
        val cardio = Exercise(
            id = "cardio",
            name = "Carrera",
            sets = listOf(ExerciseSet("c")),
            cardioDetails = CardioDetails(type = CardioType.RUN_OUTDOOR),
        )
        val session = Session(
            id = "session",
            name = "Test",
            exercises = listOf(strength),
            parts = listOf(SessionPart(id = "cardio-part", name = "Cardio", isCardioGroup = true, exercises = listOf(cardio))),
        )

        val moved = SessionEditorMoveEngine.move(
            session,
            SessionEditorMoveRequest(null, "strength", "cardio-part", 0, moveAsGroup = false),
        )

        assertSame(session, moved)
        assertEquals(listOf("strength"), moved.exercises.map { it.id })
        assertEquals(listOf("cardio"), moved.parts.single().exercises.map { it.id })
    }

    private fun groupedSession(groupIds: List<String>, looseIds: List<String>): Session {
        val grouped = groupIds.map { id ->
            Exercise(id = id, name = id.uppercase(), sets = listOf(ExerciseSet("$id-set")), supersetGroupRef = "g")
        }
        val loose = looseIds.map { id -> Exercise(id = id, name = id.uppercase(), sets = listOf(ExerciseSet("$id-set"))) }
        return SupersetRules.createSuperset(
            session = Session(id = "session", name = "Test", exercises = grouped + loose),
            groupId = "g",
            exerciseIds = groupIds,
            restBetweenExercises = 30,
            restAfterSuperset = 90,
        )
    }
}

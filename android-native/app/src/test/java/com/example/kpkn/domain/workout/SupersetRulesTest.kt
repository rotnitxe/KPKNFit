package com.example.kpkn.domain.workout

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SupersetRulesTest {
    @Test
    fun createSuperset_canLinkExercisesAcrossWholeSession() {
        val press = Exercise(id = "press", name = "Press", sets = listOf(ExerciseSet("p1")))
        val row = Exercise(id = "row", name = "Row", sets = listOf(ExerciseSet("r1")))
        val curl = Exercise(id = "curl", name = "Curl", sets = listOf(ExerciseSet("c1")))
        val session = Session(
            id = "s",
            name = "Upper",
            exercises = listOf(curl),
            parts = listOf(
                SessionPart(id = "push", name = "Push", exercises = listOf(press)),
                SessionPart(id = "pull", name = "Pull", exercises = listOf(row)),
            ),
        )

        val updated = SupersetRules.createSuperset(
            session = session,
            groupId = "ss-1",
            exerciseIds = listOf("row", "press"),
            restBetweenExercises = 30,
            restAfterSuperset = 150,
            anchorPartId = "pull",
        )

        assertEquals(listOf("row", "press"), updated.supersetGroups.single().exerciseOrder)
        assertEquals(emptyList<Exercise>(), updated.parts[0].exercises)
        assertEquals(listOf("row", "press"), updated.parts[1].exercises.map { it.id })
        assertEquals(listOf("ss-1", "ss-1"), updated.parts[1].exercises.map { it.supersetGroupRef })
        assertNull(updated.exercises.single().supersetGroupRef)
        assertEquals("pull", updated.supersetGroups.single().visualPlacement?.partId)
    }

    @Test
    fun nextTarget_respectsConfiguredOrderAndUnevenSetCounts() {
        val a = Exercise(id = "a", name = "A", sets = listOf(ExerciseSet("a1"), ExerciseSet("a2")))
        val b = Exercise(id = "b", name = "B", sets = listOf(ExerciseSet("b1")))
        val c = Exercise(id = "c", name = "C", sets = listOf(ExerciseSet("c1"), ExerciseSet("c2"), ExerciseSet("c3")))
        val session = SupersetRules.createSuperset(
            session = Session(id = "s", name = "Sesion", exercises = listOf(a, b, c)),
            groupId = "ss-1",
            exerciseIds = listOf("c", "a", "b"),
            restBetweenExercises = 45,
            restAfterSuperset = 120,
        )
        val visible = session.exercises

        assertEquals(3, SupersetRules.roundCount(session, "ss-1"))
        assertEquals(1 to 0, SupersetRules.nextTarget(session, visible, currentExerciseIdx = 0, currentSetIdx = 0))
        assertEquals(2 to 0, SupersetRules.nextTarget(session, visible, currentExerciseIdx = 1, currentSetIdx = 0))
        assertEquals(0 to 1, SupersetRules.nextTarget(session, visible, currentExerciseIdx = 2, currentSetIdx = 0))
        assertEquals(1 to 1, SupersetRules.nextTarget(session, visible, currentExerciseIdx = 0, currentSetIdx = 1))
        assertEquals(0 to 2, SupersetRules.nextTarget(session, visible, currentExerciseIdx = 1, currentSetIdx = 1))
        assertNull(SupersetRules.nextTarget(session, visible, currentExerciseIdx = 0, currentSetIdx = 2))
    }

    @Test
    fun removeExercise_dissolvesGroupWhenOnlyOneMemberWouldRemain() {
        val session = SupersetRules.createSuperset(
            session = Session(
                id = "s",
                name = "Sesion",
                exercises = listOf(
                    Exercise(id = "a", name = "A"),
                    Exercise(id = "b", name = "B"),
                ),
            ),
            groupId = "ss-1",
            exerciseIds = listOf("a", "b"),
            restBetweenExercises = 45,
            restAfterSuperset = 120,
        )

        val updated = SupersetRules.removeExercise(session, "ss-1", "a")

        assertEquals(0, updated.supersetGroups.size)
        assertNull(updated.exercises.first { it.id == "a" }.supersetGroupRef)
        assertNull(updated.exercises.first { it.id == "b" }.supersetGroupRef)
        assertNotNull(session.supersetGroups.single())
    }

    @Test
    fun deleteExercise_removesOnlyMemberAndKeepsRemainingSuperset() {
        val session = SupersetRules.createSuperset(
            session = Session(
                id = "s",
                name = "Sesion",
                exercises = listOf(
                    Exercise(id = "a", name = "A"),
                    Exercise(id = "b", name = "B"),
                    Exercise(id = "c", name = "C"),
                ),
            ),
            groupId = "ss-1",
            exerciseIds = listOf("a", "b", "c"),
            restBetweenExercises = 45,
            restAfterSuperset = 120,
        )

        val updated = SupersetRules.deleteExercise(session, "ss-1", "b")

        assertEquals(listOf("a", "c"), updated.exercises.map { it.id })
        assertEquals(listOf("a", "c"), updated.supersetGroups.single().exerciseOrder)
        assertEquals(setOf("ss-1"), updated.exercises.mapNotNull { it.supersetGroupRef }.toSet())
    }

    @Test
    fun deleteExercise_dissolvesGroupWhenDeletingOneOfTwo() {
        val session = SupersetRules.createSuperset(
            session = Session(
                id = "s",
                name = "Sesion",
                exercises = listOf(
                    Exercise(id = "a", name = "A"),
                    Exercise(id = "b", name = "B"),
                ),
            ),
            groupId = "ss-1",
            exerciseIds = listOf("a", "b"),
            restBetweenExercises = 45,
            restAfterSuperset = 120,
        )

        val updated = SupersetRules.deleteExercise(session, "ss-1", "a")

        assertEquals(listOf("b"), updated.exercises.map { it.id })
        assertEquals(emptyList<Any>(), updated.supersetGroups)
        assertNull(updated.exercises.single().supersetGroupRef)
    }

    @Test
    fun deleteGroup_removesAllMembersAndGroup() {
        val session = SupersetRules.createSuperset(
            session = Session(
                id = "s",
                name = "Sesion",
                exercises = listOf(
                    Exercise(id = "a", name = "A"),
                    Exercise(id = "b", name = "B"),
                    Exercise(id = "outside", name = "Outside"),
                ),
            ),
            groupId = "ss-1",
            exerciseIds = listOf("a", "b"),
            restBetweenExercises = 45,
            restAfterSuperset = 120,
        )

        val updated = SupersetRules.deleteGroup(session, "ss-1")

        assertEquals(listOf("outside"), updated.exercises.map { it.id })
        assertEquals(0, updated.supersetGroups.size)
    }

    @Test
    fun createSuperset_removesMovedExercisesFromPreviousGroups() {
        val initial = Session(
            id = "s",
            name = "Sesion",
            exercises = listOf(
                Exercise(id = "a", name = "A"),
                Exercise(id = "b", name = "B"),
                Exercise(id = "c", name = "C"),
                Exercise(id = "d", name = "D"),
            ),
        )
        val first = SupersetRules.createSuperset(
            session = initial,
            groupId = "old",
            exerciseIds = listOf("a", "b", "c"),
            restBetweenExercises = 30,
            restAfterSuperset = 90,
        )

        val updated = SupersetRules.createSuperset(
            session = first,
            groupId = "new",
            exerciseIds = listOf("b", "d"),
            restBetweenExercises = 45,
            restAfterSuperset = 120,
        )

        assertEquals(listOf("a", "c"), updated.supersetGroups.first { it.id == "old" }.exerciseOrder)
        assertEquals(listOf("b", "d"), updated.supersetGroups.first { it.id == "new" }.exerciseOrder)
        assertEquals("new", updated.exercises.first { it.id == "b" }.supersetGroupRef)
    }

    @Test
    fun removeExercise_clearsLegacyMembersEvenWhenOrderIsStale() {
        val initial = SupersetRules.createSuperset(
            session = Session(
                id = "s",
                name = "Sesion",
                exercises = listOf(
                    Exercise(id = "a", name = "A"),
                    Exercise(id = "b", name = "B"),
                    Exercise(id = "c", name = "C"),
                ),
            ),
            groupId = "ss",
            exerciseIds = listOf("a", "b", "c"),
            restBetweenExercises = 30,
            restAfterSuperset = 90,
        )
        val stale = initial.copy(
            supersetGroups = initial.supersetGroups.map { it.copy(exerciseOrder = listOf("a", "b")) },
        )

        val updated = SupersetRules.removeExercise(stale, "ss", "a")

        assertEquals(listOf("b", "c"), updated.supersetGroups.single().exerciseOrder)
        assertNull(updated.exercises.first { it.id == "a" }.supersetGroupRef)
        assertEquals("ss", updated.exercises.first { it.id == "b" }.supersetGroupRef)
        assertEquals("ss", updated.exercises.first { it.id == "c" }.supersetGroupRef)
    }

    @Test
    fun dissolve_copiesRoundRestAsIndividualRest() {
        val session = SupersetRules.createSuperset(
            session = Session(
                id = "s",
                name = "Sesion",
                exercises = listOf(
                    Exercise(id = "a", name = "A", restTime = 60),
                    Exercise(id = "b", name = "B", restTime = 75),
                ),
            ),
            groupId = "ss",
            exerciseIds = listOf("a", "b"),
            restBetweenExercises = 30,
            restAfterSuperset = 150,
        )

        val dissolved = SupersetRules.dissolve(session, "ss")

        assertEquals(150, dissolved.exercises.first { it.id == "a" }.restTime)
        assertEquals(150, dissolved.exercises.first { it.id == "b" }.restTime)
        assertNull(dissolved.exercises.first { it.id == "a" }.supersetGroupRef)
        assertNull(dissolved.exercises.first { it.id == "b" }.supersetGroupRef)
        assertEquals(0, dissolved.supersetGroups.size)
    }

    @Test
    fun normalizeSession_promotesLegacySupersetIdsToCanonicalGroups() {
        val session = Session(
            id = "s",
            name = "Sesion",
            exercises = listOf(
                Exercise(id = "a", name = "A", supersetId = "legacy", supersetRestBetween = 20, supersetRestAfter = 80),
                Exercise(id = "b", name = "B", supersetId = "legacy"),
                Exercise(id = "c", name = "C"),
            ),
        )

        val normalized = SupersetRules.normalizeSession(session)

        assertEquals(listOf("a", "b"), normalized.supersetGroups.single().exerciseOrder)
        assertEquals(20, normalized.supersetGroups.single().restBetweenExercises)
        assertEquals(80, normalized.supersetGroups.single().restAfterSuperset)
        assertEquals("legacy", normalized.exercises[0].supersetGroupRef)
        assertEquals("legacy", normalized.exercises[1].supersetGroupRef)
        assertNull(normalized.exercises[2].supersetGroupRef)
    }

    @Test
    fun normalizeSession_capsLegacyGroupsAtFourMembers() {
        val session = Session(
            id = "s",
            name = "Sesion",
            exercises = listOf("a", "b", "c", "d", "e").map { id ->
                Exercise(id = id, name = id.uppercase(), supersetId = "legacy")
            },
        )

        val normalized = SupersetRules.normalizeSession(session)

        assertEquals(listOf("a", "b", "c", "d"), normalized.supersetGroups.single().exerciseOrder)
        assertEquals("legacy", normalized.exercises[0].supersetGroupRef)
        assertEquals("legacy", normalized.exercises[3].supersetGroupRef)
        assertNull(normalized.exercises[4].supersetGroupRef)
        assertNull(normalized.exercises[4].supersetId)
    }

    @Test
    fun moveGroup_movesSupersetAsSingleVisualBlock() {
        val base = Session(
            id = "s",
            name = "Sesion",
            exercises = listOf(
                Exercise(id = "a", name = "A"),
                Exercise(id = "b", name = "B"),
                Exercise(id = "c", name = "C"),
            ),
            parts = listOf(SessionPart(id = "p", name = "Parte", exercises = listOf(Exercise(id = "d", name = "D")))),
        )
        val grouped = SupersetRules.createSuperset(
            session = base,
            groupId = "ss",
            exerciseIds = listOf("b", "c"),
            restBetweenExercises = 30,
            restAfterSuperset = 90,
        )

        val moved = SupersetRules.moveGroup(grouped, groupId = "ss", targetPartId = "p", targetIndex = 0)

        assertEquals(listOf("a"), moved.exercises.map { it.id })
        assertEquals(listOf("b", "c", "d"), moved.parts.single().exercises.map { it.id })
        assertEquals(listOf("b", "c"), moved.supersetGroups.single().exerciseOrder)
    }
}

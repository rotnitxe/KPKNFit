package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Las filas de «Orden de la sesión» (LV11, A3; prototipo C3): qué está hecho, qué está en curso y qué se puede mover, y cómo se sanea un
 * orden pedido para que nada se ponga delante de lo fijo ni el cardio se mezcle con la fuerza.
 */
class ReorderSheetModelTest {

    private fun ex(id: String, sets: Int = 4, group: String? = null) = Exercise(
        id = id,
        name = "Ejercicio $id",
        sets = (0 until sets).map { ExerciseSet(id = "$id-s$it", targetReps = 8) },
        supersetGroupRef = group,
        supersetId = group,
    )

    private fun cardio(id: String) = Exercise(
        id = id,
        name = "Cardio $id",
        cardioDetails = CardioDetails(type = CardioType.TREADMILL, targetDurationSeconds = 1200),
        sets = listOf(ExerciseSet(id = "$id-s0")),
    )

    private fun done(id: String, vararg indices: Int): Map<String, CompletedSet> =
        indices.associate { "${id}_$it" to CompletedSet(id = "c-$id-$it", weight = 50.0, reps = 8) }

    private fun rows(
        order: List<String>,
        exercises: List<Exercise>,
        completed: Map<String, CompletedSet> = emptyMap(),
        omitted: Set<String> = emptySet(),
        rounds: (String) -> Int? = { null },
    ) = buildReorderRows(order, exercises.associateBy { it.id }, completed, omitted, rounds)

    @Test
    fun anExerciseWithAllItsSetsDoneIsDoneAndFixed() {
        val all = listOf(ex("a", sets = 2), ex("b"))

        val result = rows(listOf("a", "b"), all, done("a", 0, 1))

        assertEquals(ReorderStatus.DONE, result[0].status)
        assertTrue(result[0].locked)
        assertNull("lo hecho no lleva subtítulo", result[0].subtitle)
        assertEquals(ReorderStatus.PENDING, result[1].status)
        assertFalse(result[1].locked)
    }

    @Test
    fun anExerciseWithSomeSetsDoneIsInProgressAndFixedWithItsCount() {
        val all = listOf(ex("a"), ex("b"))

        val result = rows(listOf("a", "b"), all, done("a", 0))

        assertEquals(ReorderStatus.IN_PROGRESS, result[0].status)
        assertTrue(result[0].locked)
        assertEquals("en curso · 1/4", result[0].subtitle)
    }

    @Test
    fun anExerciseWithNothingDoneCanMoveAndSaysHowManySets() {
        val result = rows(listOf("a", "b"), listOf(ex("a", sets = 3), ex("b", sets = 1)))

        assertEquals("3 series", result[0].subtitle)
        assertEquals("1 serie", result[1].subtitle)
        assertTrue(result.none { it.locked })
    }

    @Test
    fun theSetsOmittedTodayDoNotCountForTheStatusNorTheSubtitle() {
        // «a» tiene 4 series; las dos últimas están omitidas y las dos primeras hechas: está hecho (2/2).
        val result = rows(listOf("a"), listOf(ex("a")), done("a", 0, 1), omitted = setOf("a_2", "a_3"))

        assertEquals(ReorderStatus.DONE, result.single().status)
    }

    @Test
    fun aSupersetIsOneRowThatMovesAsAUnitAndItsMembersAreNamedTogether() {
        val all = listOf(ex("x"), ex("m1", sets = 3, group = "g"), ex("m2", sets = 3, group = "g"), ex("y"))

        val result = rows(listOf("x", "m1", "m2", "y"), all, rounds = { 3 })

        assertEquals(listOf("x", "m1", "y"), result.map { it.key })
        val superset = result[1]
        assertTrue(superset.isSuperset)
        assertEquals(listOf("m1", "m2"), superset.memberIds)
        assertEquals("Ejercicio m1 + Ejercicio m2", superset.title)
        assertEquals("superserie · 3 rondas", superset.subtitle)
    }

    @Test
    fun aSupersetWithOneMemberStartedIsInProgressAsAWholeAndCountsItsFinishedRounds() {
        val all = listOf(ex("m1", sets = 3, group = "g"), ex("m2", sets = 3, group = "g"))
        // Ronda 1 completa (los dos miembros) y la segunda a medias.
        val completed = done("m1", 0, 1) + done("m2", 0)

        val superset = rows(listOf("m1", "m2"), all, completed, rounds = { 3 }).single()

        assertEquals(ReorderStatus.IN_PROGRESS, superset.status)
        assertEquals("superserie · en curso · 1/3 rondas", superset.subtitle)
        assertEquals(setOf("m1", "m2"), lockedExerciseIds(listOf(superset)))
    }

    @Test
    fun theCardioRowSaysItsDurationAndIsMarkedAsCardio() {
        val result = rows(listOf("a", "run"), listOf(ex("a"), cardio("run")))

        assertTrue(result[1].isCardio)
        assertEquals("cardio · 20 min", result[1].subtitle)
        assertFalse(result[0].isCardio)
    }

    @Test
    fun anExerciseThatIsNoLongerInTheSessionDoesNotAppear() {
        val result = rows(listOf("a", "gone", "b"), listOf(ex("a"), ex("b")))

        assertEquals(listOf("a", "b"), result.map { it.key })
    }

    @Test
    fun theLockedIdsAreThoseOfTheDoneAndInProgressRows() {
        val all = listOf(ex("a", sets = 1), ex("b"), ex("c"))

        val locked = lockedExerciseIds(rows(listOf("a", "b", "c"), all, done("a", 0) + done("b", 0)))

        assertEquals(setOf("a", "b"), locked)
    }

    // ── Sanear lo pedido ───────────────────────────────────────────────────────────────────────────────────────────────────────

    private val noCardio: (String) -> Boolean = { false }

    @Test
    fun aMoveInFrontOfLockedExercisesIsSentBackBehindThem() {
        val current = listOf("a", "b", "c", "d")

        val next = sanitizeReorder(candidate = listOf("c", "a", "b", "d"), current = current, locked = setOf("a", "b"), isCardio = noCardio)

        assertEquals(listOf("a", "b", "c", "d"), next)
    }

    @Test
    fun aMoveBehindTheLockedExercisesIsKept() {
        val next = sanitizeReorder(
            candidate = listOf("a", "b", "d", "c"),
            current = listOf("a", "b", "c", "d"),
            locked = setOf("a", "b"),
            isCardio = noCardio,
        )

        assertEquals(listOf("a", "b", "d", "c"), next)
    }

    @Test
    fun cardioCannotBeMixedIntoTheStrength() {
        val isCardio: (String) -> Boolean = { it == "run" }

        val refused = sanitizeReorder(listOf("a", "run", "b"), listOf("a", "b", "run"), emptySet(), isCardio)
        val kept = sanitizeReorder(listOf("b", "a", "run"), listOf("a", "b", "run"), emptySet(), isCardio)

        assertEquals("el cardio no sube entre la fuerza: el orden se queda", listOf("a", "b", "run"), refused)
        assertEquals("la fuerza sí se reordena entre sí", listOf("b", "a", "run"), kept)
    }

    @Test
    fun theSelectionNeverIncludesFixedRows() {
        val all = listOf(ex("a", sets = 1), ex("b"), ex("c"))
        val result = rows(listOf("a", "b", "c"), all, done("a", 0))

        assertEquals(listOf("b", "c"), selectedExerciseIds(result, setOf("a", "b", "c")))
    }
}

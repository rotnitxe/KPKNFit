package com.example.kpkn.domain.workout

import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.SupersetGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reordenar una sesión en vivo (LV4, 10.1). El orden pedido tiene que acabar siendo el orden de la sesión (lo que pinta el carrusel y
 * recorre el navegador de pasos), sin perder ningún ejercicio y diciendo cuáles cambian de grupo para lograrlo.
 */
class LiveSessionReorderTest {

    private fun ex(id: String, group: String? = null) = Exercise(
        id = id,
        name = "Ejercicio $id",
        sets = listOf(ExerciseSet(id = "$id-s0"), ExerciseSet(id = "$id-s1")),
        supersetGroupRef = group,
        supersetId = group,
    )

    private fun cardio(id: String) = Exercise(
        id = id,
        name = "Cardio $id",
        cardioDetails = CardioDetails(type = CardioType.TREADMILL),
        sets = listOf(ExerciseSet(id = "$id-s0")),
    )

    private fun loose(vararg ids: String) = Session(id = "s", name = "Sesión", exercises = ids.map { ex(it) })

    private fun parts(vararg groups: List<String>) = Session(
        id = "s",
        name = "Sesión",
        parts = groups.mapIndexed { index, ids -> SessionPart(id = "p$index", name = "Grupo ${index + 1}", exercises = ids.map { ex(it) }) },
    )

    private fun Session.order() = allExercises().map { it.id }

    private fun Session.partIds(index: Int) = parts[index].exercises.map { it.id }

    // ── Sin grupos ─────────────────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun looseExercisesTakeTheRequestedOrder() {
        val plan = LiveSessionReorder.plan(loose("a", "b", "c", "d"), listOf("c", "a", "d", "b"))

        assertEquals(listOf("c", "a", "d", "b"), plan.session.order())
        assertTrue(plan.groupChanges.isEmpty())
    }

    @Test
    fun theSameOrderGivesTheSameSessionUntouched() {
        val session = loose("a", "b", "c")

        val plan = LiveSessionReorder.plan(session, listOf("a", "b", "c"))

        assertEquals(session, plan.session)
        assertTrue(plan.groupChanges.isEmpty())
    }

    @Test
    fun anExerciseThatIsNotInTheRequestStaysAfterItsFormerPredecessor() {
        // «b» está omitido hoy: no viene en la lista que ve la persona y no se pierde.
        val plan = LiveSessionReorder.plan(loose("a", "b", "c", "d"), listOf("c", "a", "d"))

        assertEquals(listOf("c", "a", "b", "d"), plan.session.order())
    }

    @Test
    fun aLeadingExerciseNotInTheRequestStaysFirst() {
        val plan = LiveSessionReorder.plan(loose("x", "a", "b"), listOf("b", "a"))

        assertEquals(listOf("x", "b", "a"), plan.session.order())
    }

    @Test
    fun unknownAndRepeatedIdsAreIgnored() {
        val plan = LiveSessionReorder.plan(loose("a", "b", "c"), listOf("c", "zzz", "c", "a"))

        assertEquals(listOf("c", "a", "b"), plan.session.order())
    }

    @Test
    fun aSessionWithRepeatedExerciseIdsIsNotTouched() {
        val session = Session(id = "s", name = "Sesión", exercises = listOf(ex("a"), ex("a"), ex("b")))

        val plan = LiveSessionReorder.plan(session, listOf("b", "a"))

        assertSame(session, plan.session)
    }

    // ── Con grupos ─────────────────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun reorderingInsideAGroupKeepsTheGroups() {
        val plan = LiveSessionReorder.plan(parts(listOf("a", "b"), listOf("c", "d")), listOf("b", "a", "d", "c"))

        assertEquals(listOf("b", "a"), plan.session.partIds(0))
        assertEquals(listOf("d", "c"), plan.session.partIds(1))
        assertTrue(plan.groupChanges.isEmpty())
    }

    @Test
    fun theFirstExerciseOfTheSecondGroupMovedToTheTopJoinsTheFirstGroup() {
        val plan = LiveSessionReorder.plan(parts(listOf("a", "b"), listOf("c", "d")), listOf("c", "a", "b", "d"))

        assertEquals(listOf("c", "a", "b", "d"), plan.session.order())
        assertEquals(listOf("c", "a", "b"), plan.session.partIds(0))
        assertEquals(listOf("d"), plan.session.partIds(1))
        assertEquals(listOf(ReorderGroupChange("c", "Grupo 2", "Grupo 1")), plan.groupChanges)
    }

    @Test
    fun theFirstExerciseOfTheFirstGroupMovedToTheEndJoinsTheLastGroup() {
        val plan = LiveSessionReorder.plan(parts(listOf("a", "b"), listOf("c", "d")), listOf("b", "c", "d", "a"))

        assertEquals(listOf("b", "c", "d", "a"), plan.session.order())
        assertEquals(listOf("b"), plan.session.partIds(0))
        assertEquals(listOf("c", "d", "a"), plan.session.partIds(1))
        assertEquals(listOf(ReorderGroupChange("a", "Grupo 1", "Grupo 2")), plan.groupChanges)
    }

    @Test
    fun anExerciseSlippedBetweenTwoOfAnotherGroupSwapsGroupsWithTheNeighbour() {
        // La regla de siempre: un ejercicio solo entre dos del mismo grupo se une a ese grupo.
        val plan = LiveSessionReorder.plan(parts(listOf("a", "b", "c"), listOf("d", "e", "f")), listOf("a", "b", "d", "c", "e", "f"))

        assertEquals(listOf("a", "b", "d", "c", "e", "f"), plan.session.order())
        assertEquals(listOf("a", "b", "d"), plan.session.partIds(0))
        assertEquals(listOf("c", "e", "f"), plan.session.partIds(1))
        assertEquals(
            listOf(ReorderGroupChange("d", "Grupo 2", "Grupo 1"), ReorderGroupChange("c", "Grupo 1", "Grupo 2")),
            plan.groupChanges,
        )
    }

    @Test
    fun theExerciseSkippedInsideAGroupStaysInItsGroupWhenTheRestMoveAcrossGroups() {
        val plan = LiveSessionReorder.plan(parts(listOf("a", "b"), listOf("c")), listOf("c", "a"))

        assertEquals(setOf("a", "b", "c"), plan.session.order().toSet())
        assertEquals(listOf("c", "a", "b"), plan.session.order())
    }

    @Test
    fun looseExercisesOfASessionWithGroupsAreReorderedToo() {
        val session = Session(
            id = "s",
            name = "Sesión",
            exercises = listOf(ex("x"), ex("y")),
            parts = listOf(SessionPart(id = "p0", name = "Grupo 1", exercises = listOf(ex("a"), ex("b")))),
        )

        val plan = LiveSessionReorder.plan(session, listOf("y", "x", "b", "a"))

        assertEquals(listOf("y", "x", "b", "a"), plan.session.order())
        assertEquals(listOf("y", "x"), plan.session.exercises.map { it.id })
    }

    @Test
    fun theUndoOfThePlanSendsEachExerciseBackToItsHomeGroup() {
        val moved = LiveSessionReorder.plan(parts(listOf("a", "b"), listOf("c", "d")), listOf("c", "a", "b", "d")).session
        assertEquals(listOf("c", "a", "b"), moved.partIds(0))

        val reverted = LiveSessionReorder.plan(
            moved,
            listOf("a", "b", "c", "d"),
            homePartNames = mapOf("a" to "Grupo 1", "b" to "Grupo 1", "c" to "Grupo 2", "d" to "Grupo 2"),
        ).session

        assertEquals(listOf("a", "b"), reverted.partIds(0))
        assertEquals(listOf("c", "d"), reverted.partIds(1))
    }

    @Test
    fun theOldEntryPointGlobalReorderAppliesTheSameRule() {
        val session = parts(listOf("a", "b"), listOf("c", "d"))
        val homes = mapOf("a" to "Grupo 1", "b" to "Grupo 1", "c" to "Grupo 2", "d" to "Grupo 2")

        val reordered = WorkoutStructuralEditor.globalReorder(session, listOf("c", "a", "b", "d"), homes)

        assertEquals(listOf("c", "a", "b", "d"), reordered.order())
        assertEquals(listOf("c", "a", "b"), reordered.partIds(0))
        assertEquals(listOf("d"), reordered.partIds(1))
    }

    // ── Cardio ─────────────────────────────────────────────────────────────────────────────────────────────────────────────────

    private fun withCardio(cardioFirst: Boolean = false) = Session(
        id = "s",
        name = "Sesión",
        cardioFirst = cardioFirst,
        parts = listOf(
            SessionPart(id = "fuerza", name = "Fuerza", exercises = listOf(ex("a"), ex("b"))),
            SessionPart(id = "cardio", name = "Cardio", isCardioGroup = true, exercises = listOf(cardio("run"))),
        ),
    )

    @Test
    fun cardioNeverMixesWithStrength() {
        val plan = LiveSessionReorder.plan(withCardio(), listOf("run", "b", "a"))

        assertEquals(listOf("b", "a", "run"), plan.session.order())
        assertEquals(listOf("run"), plan.session.parts[1].exercises.map { it.id })
        assertTrue(plan.groupChanges.isEmpty())
    }

    @Test
    fun cardioStaysFirstWhenTheSessionStartsWithIt() {
        val plan = LiveSessionReorder.plan(withCardio(cardioFirst = true), listOf("b", "run", "a"))

        assertEquals(listOf("run", "b", "a"), plan.session.order())
    }

    // ── Superseries ────────────────────────────────────────────────────────────────────────────────────────────────────────────

    private fun withSuperset() = Session(
        id = "s",
        name = "Sesión",
        exercises = listOf(ex("x"), ex("m1", "g"), ex("m2", "g"), ex("y")),
        supersetGroups = listOf(SupersetGroup(id = "g", exerciseOrder = listOf("m1", "m2"))),
    )

    @Test
    fun theMembersOfASupersetCanChangeTheirOrderAndTheGroupFollows() {
        val plan = LiveSessionReorder.plan(withSuperset(), listOf("x", "m2", "m1", "y"))

        assertEquals(listOf("x", "m2", "m1", "y"), plan.session.order())
        assertEquals(listOf("m2", "m1"), plan.session.supersetGroups.single().exerciseOrder)
    }

    @Test
    fun aSupersetThatIsNotTouchedKeepsItsOwnMemberOrder() {
        // La lista y el orden interno del grupo pueden diferir (sesiones antiguas): si no se toca, no se cambia.
        val session = withSuperset().copy(supersetGroups = listOf(SupersetGroup(id = "g", exerciseOrder = listOf("m2", "m1"))))

        val plan = LiveSessionReorder.plan(session, listOf("y", "x", "m1", "m2"))

        assertEquals(listOf("y", "x", "m1", "m2"), plan.session.order())
        assertEquals(listOf("m2", "m1"), plan.session.supersetGroups.single().exerciseOrder)
    }

    @Test
    fun anExerciseSlippedBetweenTheMembersOfASupersetGoesAfterTheWholeGroup() {
        val plan = LiveSessionReorder.plan(withSuperset(), listOf("m1", "x", "m2", "y"))

        assertEquals(listOf("m1", "m2", "x", "y"), plan.session.order())
    }

    @Test
    fun aSupersetMovesAsAWholeWhenItsMembersAreRequestedTogether() {
        val plan = LiveSessionReorder.plan(withSuperset(), listOf("m1", "m2", "x", "y"))

        assertEquals(listOf("m1", "m2", "x", "y"), plan.session.order())
        assertEquals(listOf("m1", "m2"), plan.session.supersetGroups.single().exerciseOrder)
    }

    // ── Mover una fila del cajón: las superseries son una unidad ───────────────────────────────────────────────────────────────

    private val groupOf: (String) -> String? = { id -> if (id.startsWith("m")) "g" else null }

    @Test
    fun aLooseRowJumpsOneWholeNeighbour() {
        assertEquals(listOf("y", "x", "m1", "m2"), LiveSessionReorder.moveUnit(listOf("x", "y", "m1", "m2"), groupOf, fromIndex = 1, delta = -1))
        assertEquals(listOf("x", "m1", "m2", "y"), LiveSessionReorder.moveUnit(listOf("x", "y", "m1", "m2"), groupOf, fromIndex = 1, delta = 1))
    }

    @Test
    fun aLooseRowNextToASupersetJumpsTheWholeBlock() {
        val order = listOf("x", "m1", "m2", "y")

        assertEquals(listOf("m1", "m2", "x", "y"), LiveSessionReorder.moveUnit(order, groupOf, fromIndex = 0, delta = 1))
        assertEquals(listOf("x", "y", "m1", "m2"), LiveSessionReorder.moveUnit(order, groupOf, fromIndex = 3, delta = -1))
    }

    @Test
    fun theEdgeRowOfASupersetMovesTheWholeBlockAndAnInnerRowSwapsMembers() {
        val order = listOf("x", "m1", "m2", "y")

        assertEquals("subir el primer miembro sube el bloque", listOf("m1", "m2", "x", "y"), LiveSessionReorder.moveUnit(order, groupOf, fromIndex = 1, delta = -1))
        assertEquals("bajar el último baja el bloque", listOf("x", "y", "m1", "m2"), LiveSessionReorder.moveUnit(order, groupOf, fromIndex = 2, delta = 1))
        assertEquals("bajar el primero cambia el orden de los miembros", listOf("x", "m2", "m1", "y"), LiveSessionReorder.moveUnit(order, groupOf, fromIndex = 1, delta = 1))
        assertEquals("subir el segundo también", listOf("x", "m2", "m1", "y"), LiveSessionReorder.moveUnit(order, groupOf, fromIndex = 2, delta = -1))
    }

    @Test
    fun movingPastTheEndsOfTheListChangesNothing() {
        val order = listOf("x", "y")

        assertEquals(order, LiveSessionReorder.moveUnit(order, groupOf, fromIndex = 0, delta = -1))
        assertEquals(order, LiveSessionReorder.moveUnit(order, groupOf, fromIndex = 1, delta = 1))
        assertEquals(order, LiveSessionReorder.moveUnit(order, groupOf, fromIndex = 5, delta = 1))
    }
}

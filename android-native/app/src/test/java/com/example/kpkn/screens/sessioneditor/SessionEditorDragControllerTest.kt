package com.example.kpkn.screens.sessioneditor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionEditorDragControllerTest {

    @Test
    fun beginExerciseDrag_setsActiveExercise() {
        val controller = SessionEditorDragController()
        controller.beginExerciseDrag("__loose__", "e1")
        assertEquals("e1", controller.draggingExerciseId)
        assertEquals("__loose__", controller.draggingExercisePartId)
    }

    @Test
    fun updateExerciseDrag_selectsInsertIndexByYOrder() {
        val controller = SessionEditorDragController()
        val session = Session(
            id = "s1",
            name = "Test",
            exercises = listOf(
                Exercise(id = "e1", name = "A", exerciseDbId = "a"),
                Exercise(id = "e2", name = "B", exerciseDbId = "b"),
                Exercise(id = "e3", name = "C", exerciseDbId = "c"),
            ),
        )
        controller.looseContentBounds = Rect(0f, 0f, 200f, 500f)
        controller.exerciseBounds["__loose__|e1"] = Rect(0f, 0f, 100f, 80f)
        controller.exerciseBounds["__loose__|e2"] = Rect(0f, 100f, 100f, 180f)
        controller.exerciseBounds["__loose__|e3"] = Rect(0f, 200f, 100f, 280f)
        controller.beginExerciseDrag("__loose__", "e1")
        // Move center of e1 below e2 → insert before e3 (index 2)
        controller.updateExerciseDrag(Offset(0f, 150f), session)
        assertEquals("__loose__", controller.exerciseDropTargetPartId)
        assertEquals(2, controller.exerciseDropTargetIndex)
    }

    @Test
    fun endExerciseDrag_invokesMoveWithTargetIndex() {
        val controller = SessionEditorDragController()
        val session = Session(
            id = "s1",
            name = "Test",
            exercises = listOf(
                Exercise(id = "e1", name = "A", exerciseDbId = "a"),
                Exercise(id = "e2", name = "B", exerciseDbId = "b"),
            ),
        )
        controller.looseContentBounds = Rect(0f, 0f, 200f, 400f)
        controller.exerciseBounds["__loose__|e1"] = Rect(0f, 0f, 100f, 80f)
        controller.exerciseBounds["__loose__|e2"] = Rect(0f, 100f, 100f, 180f)
        controller.beginExerciseDrag("__loose__", "e1")
        controller.updateExerciseDrag(Offset(0f, 120f), session)
        var movedExercise: String? = null
        var movedIndex: Int? = null
        controller.endExerciseDrag(session) { _, exId, _, idx ->
            movedExercise = exId
            movedIndex = idx
        }
        assertEquals("e1", movedExercise)
        assertTrue(movedIndex != null)
        assertNull(controller.draggingExerciseId)
    }

    @Test
    fun movingExerciseDownUsesThePositionShownByTheDropIndicator() {
        val controller = SessionEditorDragController()
        val session = Session(
            id = "s1",
            name = "Test",
            exercises = listOf(
                Exercise(id = "e1", name = "A", exerciseDbId = "a"),
                Exercise(id = "e2", name = "B", exerciseDbId = "b"),
                Exercise(id = "e3", name = "C", exerciseDbId = "c"),
            ),
        )
        controller.looseContentBounds = Rect(0f, 0f, 200f, 500f)
        controller.exerciseBounds["__loose__|e1"] = Rect(0f, 0f, 100f, 80f)
        controller.exerciseBounds["__loose__|e2"] = Rect(0f, 100f, 100f, 180f)
        controller.exerciseBounds["__loose__|e3"] = Rect(0f, 200f, 100f, 280f)
        controller.beginExerciseDrag("__loose__", "e1")
        controller.updateExerciseDrag(Offset(0f, 150f), session)

        var result = session
        controller.endExerciseDrag(session) { from, exerciseId, to, index ->
            result = result.moveExerciseForTest(from, exerciseId, to, index)
        }

        assertEquals(listOf("e2", "e1", "e3"), result.exercises.map { it.id })
    }

    private fun Session.moveExerciseForTest(
        sourcePartId: String?,
        exerciseId: String,
        targetPartId: String?,
        targetIndex: Int?,
    ): Session {
        val source = if (sourcePartId == null) exercises else parts.first { it.id == sourcePartId }.exercises
        val dragged = source.first { it.id == exerciseId }
        val remaining = source.filterNot { it.id == exerciseId }.toMutableList()
        val requested = targetIndex ?: remaining.size
        val sourceIndex = source.indexOfFirst { it.id == exerciseId }
        val adjusted = if (sourcePartId == targetPartId && requested > sourceIndex) requested - 1 else requested
        remaining.add(adjusted.coerceIn(0, remaining.size), dragged)
        return if (targetPartId == null) copy(exercises = remaining) else this
    }

    @Test
    fun multiGroupSession_dragWithinSecondGroup_doesNotJumpToFirstGroup() {
        val controller = SessionEditorDragController()
        val part1 = SessionPart(
            id = "p1",
            name = "Grupo 1",
            exercises = listOf(
                Exercise(id = "e1_1", name = "P1 Ex 1", exerciseDbId = "1"),
                Exercise(id = "e1_2", name = "P1 Ex 2", exerciseDbId = "2"),
            ),
        )
        val part2 = SessionPart(
            id = "p2",
            name = "Grupo 2",
            exercises = listOf(
                Exercise(id = "e2_1", name = "P2 Ex 1", exerciseDbId = "3"),
                Exercise(id = "e2_2", name = "P2 Ex 2", exerciseDbId = "4"),
                Exercise(id = "e2_3", name = "P2 Ex 3", exerciseDbId = "5"),
            ),
        )
        val session = Session(
            id = "s1",
            name = "Workout",
            parts = listOf(part1, part2),
        )

        // Register bounds
        controller.partBounds["p1"] = Rect(0f, 0f, 300f, 50f)
        controller.exerciseBounds["p1|e1_1"] = Rect(0f, 50f, 300f, 150f)
        controller.exerciseBounds["p1|e1_2"] = Rect(0f, 150f, 300f, 250f)
        controller.registerPartFooterBounds("p1", Rect(0f, 250f, 300f, 300f))

        controller.partBounds["p2"] = Rect(0f, 320f, 300f, 370f)
        controller.exerciseBounds["p2|e2_1"] = Rect(0f, 370f, 300f, 470f)
        controller.exerciseBounds["p2|e2_2"] = Rect(0f, 470f, 300f, 570f)
        controller.exerciseBounds["p2|e2_3"] = Rect(0f, 570f, 300f, 670f)
        controller.registerPartFooterBounds("p2", Rect(0f, 670f, 300f, 720f))

        // Begin dragging e2_1 in p2 (grab at card center: 50px into 100px card)
        controller.beginExerciseDrag("p2", "e2_1", Offset(0f, 50f))

        // 1. Small drag downwards (+20px) within e2_1's space -> should stay in p2
        controller.updateExerciseDrag(Offset(0f, 20f), session)
        assertEquals("p2", controller.exerciseDropTargetPartId)

        // 2. Incremental drag downwards (+90px, total +110px) past e2_2's midpoint (e2_2 center is at 520; dragged center 420 + 110 = 530 > 520) -> target index 2
        controller.updateExerciseDrag(Offset(0f, 90f), session)
        assertEquals("p2", controller.exerciseDropTargetPartId)
        assertEquals(2, controller.exerciseDropTargetIndex)

        // 3. Incremental drag downwards (+100px, total +210px) past e2_3's midpoint (e2_3 center is at 620; dragged center 420 + 210 = 630 > 620) -> target index 3 (end)
        controller.updateExerciseDrag(Offset(0f, 100f), session)
        assertEquals("p2", controller.exerciseDropTargetPartId)
        assertEquals(3, controller.exerciseDropTargetIndex)

        // 4. Large incremental drag UPWARDS (-460px, total -250px) from p2 into p1 (dragged center 420 - 250 = 170 in p1 between e1_1 and e1_2) -> target p1, index 1
        controller.updateExerciseDrag(Offset(0f, -460f), session)
        assertEquals("p1", controller.exerciseDropTargetPartId)
        assertEquals(1, controller.exerciseDropTargetIndex)
    }

    @Test
    fun reorderingUpward_usesMidpointCorrectly() {
        val controller = SessionEditorDragController()
        val session = Session(
            id = "s1",
            name = "Test",
            exercises = listOf(
                Exercise(id = "e1", name = "A", exerciseDbId = "a"),
                Exercise(id = "e2", name = "B", exerciseDbId = "b"),
                Exercise(id = "e3", name = "C", exerciseDbId = "c"),
            ),
        )
        controller.exerciseBounds["__loose__|e1"] = Rect(0f, 0f, 100f, 100f)
        controller.exerciseBounds["__loose__|e2"] = Rect(0f, 100f, 100f, 200f)
        controller.exerciseBounds["__loose__|e3"] = Rect(0f, 200f, 100f, 300f)

        // Begin drag on e3 (grab at center: 50px)
        controller.beginExerciseDrag("__loose__", "e3", Offset(0f, 50f))

        // Drag upwards by -110px so center moves from 250 to 140 (above e2 center 150) -> target index 1 (before e2)
        controller.updateExerciseDrag(Offset(0f, -110f), session)
        assertEquals("__loose__", controller.exerciseDropTargetPartId)
        assertEquals(1, controller.exerciseDropTargetIndex)

        // Incremental drag upwards by -100px (total -210px) so center moves to 40 (above e1 center 50) -> target index 0 (before e1)
        controller.updateExerciseDrag(Offset(0f, -100f), session)
        assertEquals("__loose__", controller.exerciseDropTargetPartId)
        assertEquals(0, controller.exerciseDropTargetIndex)
    }

    @Test
    fun endPartDrag_movesToTargetIndex() {
        val controller = SessionEditorDragController()
        val parts = listOf(
            SessionPart(id = "p1", name = "A"),
            SessionPart(id = "p2", name = "B"),
        )
        controller.partBounds["p1"] = Rect(0f, 0f, 100f, 50f)
        controller.partBounds["p2"] = Rect(0f, 60f, 100f, 110f)
        controller.beginPartDrag("p1")
        controller.updatePartDrag(70f, parts)
        var movedTo: Int? = null
        controller.endPartDrag(parts) { _, index -> movedTo = index }
        assertEquals(1, movedTo)
    }
}

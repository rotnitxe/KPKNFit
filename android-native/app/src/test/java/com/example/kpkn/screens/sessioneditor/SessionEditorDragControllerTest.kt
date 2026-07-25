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

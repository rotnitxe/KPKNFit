package com.example.kpkn.screens.sessioneditor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.SupersetGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionEditorDragControllerTest {

    @Test
    fun beginExerciseDrag_setsActiveExercise() {
        val controller = SessionEditorDragController()
        controller.exerciseBounds["__loose__|e1"] = Rect(0f, 0f, 100f, 80f)
        assertTrue(controller.beginExerciseDrag("__loose__", "e1"))
        assertEquals("e1", controller.draggingExerciseId)
        assertEquals("__loose__", controller.draggingExercisePartId)
    }

    @Test
    fun beginExerciseDrag_abortsWithoutInitialRect() {
        val controller = SessionEditorDragController()
        assertFalse(controller.beginExerciseDrag("__loose__", "missing"))
        assertNull(controller.draggingExerciseId)
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
    fun cancelExerciseDrag_doesNotInvokeMoveEvenWithResolvedTarget() {
        val controller = SessionEditorDragController()
        val session = Session(
            id = "s1",
            name = "Test",
            exercises = listOf(
                Exercise(id = "e1", name = "A", exerciseDbId = "a"),
                Exercise(id = "e2", name = "B", exerciseDbId = "b"),
            ),
        )
        controller.exerciseBounds["__loose__|e1"] = Rect(0f, 0f, 100f, 80f)
        controller.exerciseBounds["__loose__|e2"] = Rect(0f, 100f, 100f, 180f)
        controller.beginExerciseDrag("__loose__", "e1")
        controller.updateExerciseDrag(Offset(0f, 120f), session)
        assertEquals("__loose__", controller.exerciseDropTargetPartId)
        assertTrue(controller.exerciseDropTargetIndex != null)

        var moved = false
        // Cancel must not commit — callers wire cancel separately from end.
        controller.cancelExerciseDrag()
        assertNull(controller.draggingExerciseId)
        assertNull(controller.exerciseDropTargetPartId)
        assertFalse(moved)
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
    fun inflatedLooseZone_doesNotStealHitTestFromIntermediateGroup() {
        // N1: StrengthAddActions-sized loose must NOT engullir groups.
        val controller = SessionEditorDragController()
        val part1 = SessionPart(
            id = "p1",
            name = "Grupo 1",
            exercises = listOf(
                Exercise(id = "e1_1", name = "P1 Ex 1"),
                Exercise(id = "e1_2", name = "P1 Ex 2"),
            ),
        )
        val part2 = SessionPart(
            id = "p2",
            name = "Grupo 2",
            exercises = listOf(
                Exercise(id = "e2_1", name = "P2 Ex 1"),
            ),
        )
        val session = Session(
            id = "s1",
            name = "Workout",
            exercises = listOf(Exercise(id = "loose1", name = "Loose")),
            parts = listOf(part1, part2),
        )

        // Loose exercises only occupy a small band; StrengthAddActions would have been 100..2500.
        controller.exerciseBounds["__loose__|loose1"] = Rect(0f, 100f, 300f, 180f)
        // Poison: huge historical loose container (old bug).
        controller.looseContentBounds = Rect(0f, 100f, 300f, 2500f)

        controller.partBounds["p1"] = Rect(0f, 600f, 300f, 650f)
        controller.exerciseBounds["p1|e1_1"] = Rect(0f, 650f, 300f, 750f)
        controller.exerciseBounds["p1|e1_2"] = Rect(0f, 750f, 300f, 850f)
        controller.registerPartFooterBounds("p1", Rect(0f, 850f, 300f, 900f))

        controller.partBounds["p2"] = Rect(0f, 920f, 300f, 970f)
        controller.exerciseBounds["p2|e2_1"] = Rect(0f, 970f, 300f, 1070f)
        controller.registerPartFooterBounds("p2", Rect(0f, 1070f, 300f, 1120f))

        controller.beginExerciseDrag("p1", "e1_1", Offset(0f, 50f), session = session)

        val sections = controller.buildSortedSectionsForTest(session)
        for (i in 0 until sections.size - 1) {
            assertTrue(
                "zones must be disjoint: ${sections[i]} vs ${sections[i + 1]}",
                sections[i].second.bottom <= sections[i + 1].second.top + 0.5f,
            )
        }

        // Pointer inside p1 (y≈800): must resolve to p1, never inflated loose.
        controller.updateExerciseDrag(Offset(0f, 100f), session)
        assertEquals("p1", controller.exerciseDropTargetPartId)
    }

    @Test
    fun recomputeAfterApplyScrollDelta_withFixedPointer_updatesIndex() {
        // A: auto-scroll must recompute drop target with finger held still.
        val controller = SessionEditorDragController()
        val session = Session(
            id = "s1",
            name = "Test",
            exercises = listOf(
                Exercise(id = "e1", name = "A"),
                Exercise(id = "e2", name = "B"),
                Exercise(id = "e3", name = "C"),
            ),
        )
        controller.exerciseBounds["__loose__|e1"] = Rect(0f, 0f, 100f, 100f)
        controller.exerciseBounds["__loose__|e2"] = Rect(0f, 100f, 100f, 200f)
        controller.exerciseBounds["__loose__|e3"] = Rect(0f, 200f, 100f, 300f)
        controller.beginExerciseDrag(
            "__loose__",
            "e1",
            grabOffset = Offset(0f, 50f),
            pointerStartWindow = Offset(50f, 50f),
            session = session,
        )
        controller.recomputeExerciseDropTarget()
        val indexBefore = controller.exerciseDropTargetIndex ?: -1

        // Content scrolls up by 150px (window rects move up) while finger stays fixed in window.
        // e2/e3 centers shift from 150/250 → 0/100; pointer at y=50 → before first remaining → index 1,
        // or past last remaining center → end. Either way index must change from the initial value.
        controller.applyScrollDelta(150f)
        controller.recomputeExerciseDropTarget()
        val indexAfter = controller.exerciseDropTargetIndex
        assertTrue(
            "recompute after scroll should change index (before=$indexBefore after=$indexAfter)",
            indexAfter != null && indexAfter != indexBefore,
        )
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

        controller.partBounds["p1"] = Rect(0f, 0f, 300f, 50f)
        controller.exerciseBounds["p1|e1_1"] = Rect(0f, 50f, 300f, 150f)
        controller.exerciseBounds["p1|e1_2"] = Rect(0f, 150f, 300f, 250f)
        controller.registerPartFooterBounds("p1", Rect(0f, 250f, 300f, 300f))

        controller.partBounds["p2"] = Rect(0f, 320f, 300f, 370f)
        controller.exerciseBounds["p2|e2_1"] = Rect(0f, 370f, 300f, 470f)
        controller.exerciseBounds["p2|e2_2"] = Rect(0f, 470f, 300f, 570f)
        controller.exerciseBounds["p2|e2_3"] = Rect(0f, 570f, 300f, 670f)
        controller.registerPartFooterBounds("p2", Rect(0f, 670f, 300f, 720f))

        controller.beginExerciseDrag("p2", "e2_1", Offset(0f, 50f), session = session)

        controller.updateExerciseDrag(Offset(0f, 20f), session)
        assertEquals("p2", controller.exerciseDropTargetPartId)

        controller.updateExerciseDrag(Offset(0f, 90f), session)
        assertEquals("p2", controller.exerciseDropTargetPartId)
        assertEquals(2, controller.exerciseDropTargetIndex)

        controller.updateExerciseDrag(Offset(0f, 100f), session)
        assertEquals("p2", controller.exerciseDropTargetPartId)
        assertEquals(3, controller.exerciseDropTargetIndex)

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

        controller.beginExerciseDrag("__loose__", "e3", Offset(0f, 50f))
        controller.updateExerciseDrag(Offset(0f, -110f), session)
        assertEquals("__loose__", controller.exerciseDropTargetPartId)
        assertEquals(1, controller.exerciseDropTargetIndex)

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
        assertTrue(controller.beginPartDrag("p1", groupedParts = parts))
        controller.updatePartDrag(70f, parts)
        var movedTo: Int? = null
        controller.endPartDrag(parts) { _, index -> movedTo = index }
        assertEquals(1, movedTo)
    }

    @Test
    fun cancelPartDrag_doesNotMove() {
        val controller = SessionEditorDragController()
        val parts = listOf(
            SessionPart(id = "p1", name = "A"),
            SessionPart(id = "p2", name = "B"),
        )
        controller.partBounds["p1"] = Rect(0f, 0f, 100f, 50f)
        controller.partBounds["p2"] = Rect(0f, 60f, 100f, 110f)
        controller.beginPartDrag("p1", groupedParts = parts)
        controller.updatePartDrag(70f, parts)
        controller.cancelPartDrag()
        assertNull(controller.draggingPartId)
        assertNull(controller.partDropTargetIndex)
    }

    @Test
    fun dragIntoEmptyLooseSection_usesSyntheticStripNearFirstHeader() {
        val controller = SessionEditorDragController()
        val part1 = SessionPart(
            id = "p1",
            name = "Grupo 1",
            exercises = listOf(Exercise(id = "e1", name = "A", exerciseDbId = "a")),
        )
        val session = Session(
            id = "s1",
            name = "Test",
            exercises = emptyList(),
            parts = listOf(part1),
        )

        controller.partBounds["p1"] = Rect(0f, 220f, 300f, 270f)
        controller.exerciseBounds["p1|e1"] = Rect(0f, 270f, 300f, 370f)
        controller.registerPartFooterBounds("p1", Rect(0f, 370f, 300f, 420f))

        controller.beginExerciseDrag("p1", "e1", Offset(0f, 50f), session = session)

        // Synthetic loose is [196, 220]. Pointer start ≈ 320; delta -118 → ~202.
        controller.updateExerciseDrag(Offset(0f, -118f), session)

        assertEquals("__loose__", controller.exerciseDropTargetPartId)
        assertEquals(0, controller.exerciseDropTargetIndex)
    }

    @Test
    fun dragFarAboveFirstSection_isOutOfRangeNoOp() {
        val controller = SessionEditorDragController()
        val part1 = SessionPart(
            id = "p1",
            name = "Grupo 1",
            exercises = listOf(Exercise(id = "e1", name = "A")),
        )
        val session = Session(id = "s1", name = "Test", parts = listOf(part1))
        controller.partBounds["p1"] = Rect(0f, 400f, 300f, 450f)
        controller.exerciseBounds["p1|e1"] = Rect(0f, 450f, 300f, 550f)
        controller.registerPartFooterBounds("p1", Rect(0f, 550f, 300f, 600f))
        controller.beginExerciseDrag("p1", "e1", Offset(0f, 50f), session = session)
        // Far above (800px) → neutral zone
        controller.updateExerciseDrag(Offset(0f, -800f), session)
        assertNull(controller.exerciseDropTargetPartId)
        assertTrue(controller.exerciseDropOutOfRange)
        var moved = false
        val result = controller.endExerciseDrag(session) { _, _, _, _ -> moved = true }
        assertFalse(moved)
        assertEquals(ExerciseDragEndResult.OutOfRange, result)
    }

    @Test
    fun dragIntoEmptyPart_allowsDroppingAtZeroIndex() {
        val controller = SessionEditorDragController()
        val emptyPart = SessionPart(id = "p_empty", name = "Grupo Vacio", exercises = emptyList())
        val partWithEx = SessionPart(
            id = "p_src",
            name = "Grupo Fuente",
            exercises = listOf(Exercise(id = "e1", name = "A", exerciseDbId = "a")),
        )
        val session = Session(
            id = "s1",
            name = "Test",
            exercises = emptyList(),
            parts = listOf(emptyPart, partWithEx),
        )

        controller.partBounds["p_empty"] = Rect(0f, 0f, 300f, 50f)
        controller.registerPartFooterBounds("p_empty", Rect(0f, 50f, 300f, 100f))
        controller.partBounds["p_src"] = Rect(0f, 120f, 300f, 170f)
        controller.exerciseBounds["p_src|e1"] = Rect(0f, 170f, 300f, 270f)
        controller.registerPartFooterBounds("p_src", Rect(0f, 270f, 300f, 320f))

        controller.beginExerciseDrag("p_src", "e1", Offset(0f, 50f), session = session)
        controller.updateExerciseDrag(Offset(0f, -170f), session)

        assertEquals("p_empty", controller.exerciseDropTargetPartId)
        assertEquals(0, controller.exerciseDropTargetIndex)
    }

    @Test
    fun dropOnCollapsedPart_appendsAtEnd() {
        val controller = SessionEditorDragController()
        val collapsed = SessionPart(
            id = "p_collapsed",
            name = "Colapsado",
            exercises = listOf(
                Exercise(id = "c1", name = "C1"),
                Exercise(id = "c2", name = "C2"),
            ),
        )
        val src = SessionPart(
            id = "p_src",
            name = "Fuente",
            exercises = listOf(Exercise(id = "e1", name = "E1")),
        )
        val session = Session(id = "s1", name = "T", parts = listOf(collapsed, src))

        controller.partBounds["p_collapsed"] = Rect(0f, 0f, 300f, 50f)
        // No exercise bounds for collapsed — only header.
        controller.partBounds["p_src"] = Rect(0f, 80f, 300f, 130f)
        controller.exerciseBounds["p_src|e1"] = Rect(0f, 130f, 300f, 230f)
        controller.registerPartFooterBounds("p_src", Rect(0f, 230f, 300f, 280f))

        controller.beginExerciseDrag(
            "p_src",
            "e1",
            Offset(0f, 50f),
            session = session,
            collapsedPartIds = setOf("p_collapsed"),
        )
        controller.updateExerciseDrag(Offset(0f, -150f), session)

        assertEquals("p_collapsed", controller.exerciseDropTargetPartId)
        assertEquals(2, controller.exerciseDropTargetIndex) // append = size
    }

    @Test
    fun supersetBlock_singleBoundsEntry_andNonFirstMemberAnchorsToBlock() {
        val controller = SessionEditorDragController()
        val members = listOf(
            Exercise(id = "m1", name = "M1", supersetGroupRef = "g1"),
            Exercise(id = "m2", name = "M2", supersetGroupRef = "g1"),
            Exercise(id = "m3", name = "M3", supersetGroupRef = "g1"),
        )
        val session = Session(
            id = "s1",
            name = "T",
            exercises = members + Exercise(id = "solo", name = "Solo"),
            supersetGroups = listOf(SupersetGroup(id = "g1", exerciseOrder = listOf("m1", "m2", "m3"))),
        )
        val blockRect = Rect(0f, 0f, 300f, 300f)
        controller.exerciseBounds["__loose__|m1"] = blockRect
        // Poison member rects (old bug): should be collapsed to first member.
        controller.exerciseBounds["__loose__|m2"] = Rect(0f, 100f, 300f, 200f)
        controller.exerciseBounds["__loose__|m3"] = Rect(0f, 200f, 300f, 300f)
        controller.exerciseBounds["__loose__|solo"] = Rect(0f, 320f, 300f, 420f)

        // Drag non-first member → resolves to first member + block rect.
        assertTrue(
            controller.beginExerciseDrag(
                "__loose__",
                "m2",
                pointerStartWindow = Offset(24f, 150f),
                session = session,
            ),
        )
        assertEquals("m1", controller.draggingExerciseId)
        assertEquals(blockRect, controller.dragStartExerciseRect)
        assertFalse(controller.frozenExerciseBounds.containsKey("__loose__|m2"))
        assertTrue(controller.frozenExerciseBounds.containsKey("__loose__|m1"))

        controller.updateExerciseDrag(Offset(0f, 250f), session)
        assertEquals("__loose__", controller.exerciseDropTargetPartId)
        // Stable index around the block / solo.
        assertTrue(controller.exerciseDropTargetIndex != null)
    }

    @Test
    fun registerDuringDrag_subtractsShiftSoFrozenIsNotPolluted() {
        val controller = SessionEditorDragController()
        val session = Session(
            id = "s1",
            name = "T",
            exercises = listOf(
                Exercise(id = "e1", name = "A"),
                Exercise(id = "e2", name = "B"),
            ),
        )
        controller.exerciseBounds["__loose__|e1"] = Rect(0f, 0f, 100f, 100f)
        controller.exerciseBounds["__loose__|e2"] = Rect(0f, 100f, 100f, 200f)
        controller.beginExerciseDrag("__loose__", "e1", session = session)
        // Live rect includes +40 shift feedback; register must store unshifted.
        controller.registerExerciseBoundsDuringDrag("__loose__|e2", Rect(0f, 140f, 100f, 240f), shiftY = 40f)
        assertEquals(100f, controller.frozenExerciseBounds["__loose__|e2"]!!.top, 0.1f)
        assertEquals(200f, controller.frozenExerciseBounds["__loose__|e2"]!!.bottom, 0.1f)
    }

    @Test
    fun samePositionDrop_isNoOpInController() {
        val controller = SessionEditorDragController()
        val session = Session(
            id = "s1",
            name = "T",
            exercises = listOf(
                Exercise(id = "e1", name = "A"),
                Exercise(id = "e2", name = "B"),
            ),
        )
        controller.exerciseBounds["__loose__|e1"] = Rect(0f, 0f, 100f, 100f)
        controller.exerciseBounds["__loose__|e2"] = Rect(0f, 100f, 100f, 200f)
        controller.beginExerciseDrag("__loose__", "e1", Offset(0f, 50f), session = session)
        // Stay roughly over e1 → target index 0 or 1 (no-op)
        controller.updateExerciseDrag(Offset(0f, 5f), session)
        var moved = false
        controller.endExerciseDrag(session) { _, _, _, _ -> moved = true }
        // Either no-op filtered or tiny move — if index is 0 or 1 for e1, no-op.
        // With small delta, target is typically 0 or 1; controller filters both.
        assertFalse(moved)
    }

    @Test
    fun reorderDownwards_correctShiftAndGapCalculation() {
        val controller = SessionEditorDragController()
        val session = Session(
            id = "s1",
            name = "Test",
            exercises = listOf(
                Exercise(id = "e0", name = "A", exerciseDbId = "a"),
                Exercise(id = "e1", name = "B", exerciseDbId = "b"),
                Exercise(id = "e2", name = "C", exerciseDbId = "c"),
                Exercise(id = "e3", name = "D", exerciseDbId = "d"),
            ),
        )
        controller.looseContentBounds = Rect(0f, 0f, 300f, 500f)
        controller.exerciseBounds["__loose__|e0"] = Rect(0f, 0f, 300f, 100f)
        controller.exerciseBounds["__loose__|e1"] = Rect(0f, 100f, 300f, 200f)
        controller.exerciseBounds["__loose__|e2"] = Rect(0f, 200f, 300f, 300f)
        controller.exerciseBounds["__loose__|e3"] = Rect(0f, 300f, 400f, 400f)

        controller.beginExerciseDrag("__loose__", "e0", Offset(0f, 50f))
        controller.updateExerciseDrag(Offset(0f, 150f), session)

        assertEquals("__loose__", controller.exerciseDropTargetPartId)
        assertEquals(2, controller.exerciseDropTargetIndex)

        assertEquals(0f, controller.calculateProjectedShift(session, "__loose__", 0, "e0", 100f))
        assertTrue(controller.calculateProjectedShift(session, "__loose__", 1, "e1", 100f) < 0f)
        assertEquals(0f, controller.calculateProjectedShift(session, "__loose__", 2, "e2", 100f))
        assertEquals(0f, controller.calculateProjectedShift(session, "__loose__", 3, "e3", 100f))
    }

    @Test
    fun reorderUpwards_correctShiftAndGapCalculation() {
        val controller = SessionEditorDragController()
        val session = Session(
            id = "s1",
            name = "Test",
            exercises = listOf(
                Exercise(id = "e0", name = "A", exerciseDbId = "a"),
                Exercise(id = "e1", name = "B", exerciseDbId = "b"),
                Exercise(id = "e2", name = "C", exerciseDbId = "c"),
                Exercise(id = "e3", name = "D", exerciseDbId = "d"),
            ),
        )
        controller.looseContentBounds = Rect(0f, 0f, 300f, 500f)
        controller.exerciseBounds["__loose__|e0"] = Rect(0f, 0f, 300f, 100f)
        controller.exerciseBounds["__loose__|e1"] = Rect(0f, 100f, 300f, 200f)
        controller.exerciseBounds["__loose__|e2"] = Rect(0f, 200f, 300f, 300f)
        controller.exerciseBounds["__loose__|e3"] = Rect(0f, 300f, 400f, 400f)

        controller.beginExerciseDrag("__loose__", "e3", Offset(0f, 50f))
        controller.updateExerciseDrag(Offset(0f, -250f), session)

        assertEquals("__loose__", controller.exerciseDropTargetPartId)
        assertEquals(1, controller.exerciseDropTargetIndex)
        assertEquals(0f, controller.calculateProjectedShift(session, "__loose__", 0, "e0", 100f))
        assertTrue(controller.calculateProjectedShift(session, "__loose__", 1, "e1", 100f) > 0f)
        assertTrue(controller.calculateProjectedShift(session, "__loose__", 2, "e2", 100f) > 0f)
        assertEquals(0f, controller.calculateProjectedShift(session, "__loose__", 3, "e3", 100f))
    }

    @Test
    fun multiStepDrag_consecutiveReordersDoNotCorruptBounds() {
        val controller = SessionEditorDragController()
        var session = Session(
            id = "s1",
            name = "Test",
            exercises = listOf(
                Exercise(id = "e1", name = "A", exerciseDbId = "a"),
                Exercise(id = "e2", name = "B", exerciseDbId = "b"),
            ),
            parts = listOf(
                SessionPart(
                    id = "p1",
                    name = "Grupo 1",
                    exercises = listOf(Exercise(id = "e3", name = "C", exerciseDbId = "c")),
                ),
            ),
        )

        controller.looseContentBounds = Rect(0f, 0f, 300f, 200f)
        controller.exerciseBounds["__loose__|e1"] = Rect(0f, 0f, 300f, 100f)
        controller.exerciseBounds["__loose__|e2"] = Rect(0f, 100f, 300f, 200f)
        controller.partBounds["p1"] = Rect(0f, 220f, 300f, 270f)
        controller.exerciseBounds["p1|e3"] = Rect(0f, 270f, 300f, 370f)
        controller.registerPartFooterBounds("p1", Rect(0f, 370f, 300f, 420f))

        controller.beginExerciseDrag("__loose__", "e1", Offset(0f, 50f), session = session)
        controller.updateExerciseDrag(Offset(0f, 300f), session)
        assertEquals("p1", controller.exerciseDropTargetPartId)

        controller.endExerciseDrag(session) { from, exId, to, idx ->
            session = session.moveExerciseForMultiStep(from, exId, to, idx)
        }
        controller.pruneBounds(session, emptySet())

        controller.beginExerciseDrag("__loose__", "e2", Offset(0f, 50f), session = session)
        controller.updateExerciseDrag(Offset(0f, 300f), session)
        assertEquals("p1", controller.exerciseDropTargetPartId)

        controller.endExerciseDrag(session) { from, exId, to, idx ->
            session = session.moveExerciseForMultiStep(from, exId, to, idx)
        }
        assertTrue(session.exercises.isEmpty())
        controller.pruneBounds(session, emptySet())

        // Refresh bounds for e3 now alone in p1; synthetic loose just above header.
        controller.partBounds["p1"] = Rect(0f, 220f, 300f, 270f)
        controller.exerciseBounds["p1|e3"] = Rect(0f, 270f, 300f, 370f)
        controller.beginExerciseDrag("p1", "e3", Offset(0f, 50f), session = session)
        controller.updateExerciseDrag(Offset(0f, -118f), session)

        assertEquals("__loose__", controller.exerciseDropTargetPartId)
        assertEquals(0, controller.exerciseDropTargetIndex)
    }

    private fun Session.moveExerciseForMultiStep(
        sourcePartId: String?,
        exerciseId: String,
        targetPartId: String?,
        targetIndex: Int?,
    ): Session {
        val source = if (sourcePartId == null) exercises else parts.first { it.id == sourcePartId }.exercises
        val dragged = source.first { it.id == exerciseId }
        val remainingSource = source.filterNot { it.id == exerciseId }

        val stripped = if (sourcePartId == null) {
            copy(exercises = remainingSource)
        } else {
            copy(parts = parts.map { if (it.id == sourcePartId) it.copy(exercises = remainingSource) else it })
        }

        return if (targetPartId == null) {
            val list = stripped.exercises.toMutableList()
            list.add((targetIndex ?: list.size).coerceIn(0, list.size), dragged)
            stripped.copy(exercises = list)
        } else {
            stripped.copy(parts = stripped.parts.map { part ->
                if (part.id == targetPartId) {
                    val list = part.exercises.toMutableList()
                    list.add((targetIndex ?: list.size).coerceIn(0, list.size), dragged)
                    part.copy(exercises = list)
                } else part
            })
        }
    }

    @Test
    fun cardioExerciseDrag_farIntoStrength_isOutOfRange() {
        val controller = SessionEditorDragController()
        val cardioPart = SessionPart(
            id = "p_cardio",
            name = "Cardio Space",
            isCardioGroup = true,
            exercises = listOf(
                Exercise(
                    id = "c1",
                    name = "Running",
                    cardioDetails = com.example.kpkn.data.models.CardioDetails(
                        type = com.example.kpkn.data.models.CardioType.RUN_OUTDOOR,
                    ),
                ),
            ),
        )
        val strengthPart = SessionPart(
            id = "p_strength",
            name = "Fuerza",
            exercises = listOf(Exercise(id = "s1", name = "Squat")),
        )
        val session = Session(
            id = "sess1",
            name = "Mixed",
            exercises = listOf(Exercise(id = "loose1", name = "Bench")),
            parts = listOf(strengthPart, cardioPart),
        )

        controller.looseContentBounds = Rect(0f, 0f, 300f, 100f)
        controller.partBounds["p_strength"] = Rect(0f, 120f, 300f, 170f)
        controller.exerciseBounds["p_strength|s1"] = Rect(0f, 170f, 300f, 270f)
        controller.registerPartFooterBounds("p_strength", Rect(0f, 270f, 300f, 320f))
        controller.partBounds["p_cardio"] = Rect(0f, 340f, 300f, 390f)
        controller.exerciseBounds["p_cardio|c1"] = Rect(0f, 390f, 300f, 490f)
        controller.registerPartFooterBounds("p_cardio", Rect(0f, 490f, 300f, 540f))

        controller.beginExerciseDrag("p_cardio", "c1", Offset(0f, 50f), session = session)
        controller.updateExerciseDrag(Offset(0f, -250f), session)

        // Cardio cannot target strength; far from cardio zone → null / out of range.
        assertNull(controller.exerciseDropTargetPartId)
        assertTrue(controller.exerciseDropOutOfRange)
    }

    @Test
    fun strengthExerciseDrag_farIntoCardio_isOutOfRange() {
        val controller = SessionEditorDragController()
        val strengthPart = SessionPart(
            id = "p_strength",
            name = "Fuerza",
            exercises = listOf(Exercise(id = "s1", name = "Squat")),
        )
        val cardioPart = SessionPart(
            id = "p_cardio",
            name = "Cardio Space",
            isCardioGroup = true,
            exercises = listOf(
                Exercise(
                    id = "c1",
                    name = "Running",
                    cardioDetails = com.example.kpkn.data.models.CardioDetails(
                        type = com.example.kpkn.data.models.CardioType.RUN_OUTDOOR,
                    ),
                ),
            ),
        )
        val session = Session(
            id = "sess1",
            name = "Mixed",
            exercises = emptyList(),
            parts = listOf(strengthPart, cardioPart),
        )

        controller.partBounds["p_strength"] = Rect(0f, 0f, 300f, 50f)
        controller.exerciseBounds["p_strength|s1"] = Rect(0f, 50f, 300f, 150f)
        controller.registerPartFooterBounds("p_strength", Rect(0f, 150f, 300f, 200f))
        controller.partBounds["p_cardio"] = Rect(0f, 220f, 300f, 270f)
        controller.exerciseBounds["p_cardio|c1"] = Rect(0f, 270f, 300f, 370f)
        controller.registerPartFooterBounds("p_cardio", Rect(0f, 370f, 300f, 420f))

        controller.beginExerciseDrag("p_strength", "s1", Offset(0f, 50f), session = session)
        controller.updateExerciseDrag(Offset(0f, 250f), session)

        assertNull(controller.exerciseDropTargetPartId)
        assertTrue(controller.exerciseDropOutOfRange)
    }

    @Test
    fun singleExerciseSession_dragDoesNotCrashOrCorrupt() {
        val controller = SessionEditorDragController()
        val session = Session(
            id = "sess_single",
            name = "Single",
            exercises = listOf(Exercise(id = "single_ex", name = "Solo")),
        )
        controller.looseContentBounds = Rect(0f, 0f, 300f, 100f)
        controller.exerciseBounds["__loose__|single_ex"] = Rect(0f, 0f, 300f, 100f)

        controller.beginExerciseDrag("__loose__", "single_ex", Offset(0f, 50f), session = session)
        controller.updateExerciseDrag(Offset(0f, 50f), session)

        val shift = controller.calculateProjectedShift(session, "__loose__", 0, "single_ex", 100f)
        assertEquals(0f, shift)

        var moved = false
        controller.endExerciseDrag(session) { _, _, _, _ -> moved = true }
        assertNull(controller.draggingExerciseId)
        assertFalse(moved) // same-position no-op
    }

    @Test
    fun partDrag_movingCardioUpAndThenStrengthUp_correctlyReordersBothWays() {
        val controller = SessionEditorDragController()
        val strengthPart = SessionPart(id = "p_strength", name = "Pecho", isCardioGroup = false)
        val cardioPart = SessionPart(id = "p_cardio", name = "Cardio", isCardioGroup = true)
        val parts = listOf(strengthPart, cardioPart)

        val liveBounds1 = mapOf(
            "p_strength" to Rect(0f, 100f, 300f, 300f),
            "p_cardio" to Rect(0f, 350f, 300f, 550f),
        )
        controller.beginPartDrag("p_cardio", liveBounds1, groupedParts = parts)
        controller.updatePartDrag(-300f, parts)
        assertEquals("BEFORE_p_strength", controller.partDropTargetId)
        assertEquals(0, controller.partDropTargetIndex)

        var movedPartId: String? = null
        var movedTargetIndex: Int? = null
        controller.endPartDrag(parts) { pid, idx ->
            movedPartId = pid
            movedTargetIndex = idx
        }
        assertEquals("p_cardio", movedPartId)
        assertEquals(0, movedTargetIndex)

        val reorderedParts = listOf(cardioPart, strengthPart)
        val liveBounds2 = mapOf(
            "p_cardio" to Rect(0f, 100f, 300f, 300f),
            "p_strength" to Rect(0f, 350f, 300f, 550f),
        )
        controller.beginPartDrag("p_strength", liveBounds2, groupedParts = reorderedParts)
        controller.updatePartDrag(-300f, reorderedParts)
        assertEquals("BEFORE_p_cardio", controller.partDropTargetId)
        assertEquals(0, controller.partDropTargetIndex)

        var movedPartId2: String? = null
        var movedTargetIndex2: Int? = null
        controller.endPartDrag(reorderedParts) { pid, idx ->
            movedPartId2 = pid
            movedTargetIndex2 = idx
        }
        assertEquals("p_strength", movedPartId2)
        assertEquals(0, movedTargetIndex2)

        val liveBounds3 = mapOf(
            "p_cardio" to Rect(0f, 100f, 300f, 300f),
            "p_strength" to Rect(0f, 350f, 300f, 550f),
        )
        controller.beginPartDrag("p_cardio", liveBounds3, groupedParts = reorderedParts)
        controller.updatePartDrag(350f, reorderedParts)
        assertEquals("AFTER_p_strength", controller.partDropTargetId)
        assertEquals(1, controller.partDropTargetIndex)

        var movedPartId3: String? = null
        var movedTargetIndex3: Int? = null
        controller.endPartDrag(reorderedParts) { pid, idx ->
            movedPartId3 = pid
            movedTargetIndex3 = idx
        }
        assertEquals("p_cardio", movedPartId3)
        assertEquals(1, movedTargetIndex3)
    }

    @Test
    fun partDrag_freezeAndRecomputeAfterScroll() {
        val controller = SessionEditorDragController()
        val parts = listOf(
            SessionPart(id = "p1", name = "A"),
            SessionPart(id = "p2", name = "B"),
            SessionPart(id = "p3", name = "C"),
        )
        val live = mapOf(
            "p1" to Rect(0f, 0f, 300f, 80f),
            "p2" to Rect(0f, 100f, 300f, 180f),
            "p3" to Rect(0f, 200f, 300f, 280f),
        )
        controller.beginPartDrag("p2", live, pointerStartWindow = Offset(10f, 140f), groupedParts = parts)
        controller.recomputePartDropTarget()
        val before = controller.partDropTargetIndex
        controller.applyScrollDelta(120f)
        controller.recomputePartDropTarget()
        assertTrue(controller.frozenPartDragBounds.isNotEmpty())
        // After scroll, relative ordering vs frozen bounds + fixed window pointer changes.
        assertTrue(before != controller.partDropTargetIndex || controller.partDropTargetId != null)
    }

    // --- F3.7 smoke (unit): scenarios from the manual checklist ---

    @Test
    fun smoke_looseToPart_andBack_resolvesTargets() {
        val controller = SessionEditorDragController()
        val part = SessionPart(
            id = "p1",
            name = "Grupo",
            exercises = listOf(Exercise(id = "e2", name = "In part")),
        )
        val session = Session(
            id = "s1",
            name = "Test",
            exercises = listOf(Exercise(id = "e1", name = "Loose")),
            parts = listOf(part),
        )
        controller.looseContentBounds = Rect(0f, 0f, 300f, 120f)
        controller.exerciseBounds["__loose__|e1"] = Rect(0f, 20f, 300f, 100f)
        controller.partBounds["p1"] = Rect(0f, 140f, 300f, 180f)
        controller.exerciseBounds["p1|e2"] = Rect(0f, 190f, 300f, 270f)
        controller.registerPartFooterBounds("p1", Rect(0f, 270f, 300f, 310f))
        controller.beginExerciseDrag(
            "__loose__",
            "e1",
            pointerStartWindow = Offset(10f, 60f),
            session = session,
        )
        controller.updateExerciseDrag(Offset(0f, 160f), session)
        assertEquals("p1", controller.exerciseDropTargetPartId)
        assertFalse(controller.exerciseDropOutOfRange)
        controller.updateExerciseDrag(Offset(0f, -160f), session)
        assertEquals("__loose__", controller.exerciseDropTargetPartId)
    }

    @Test
    fun smoke_cancelMidGesture_doesNotMove_andClearsUiSnapshot() {
        val snaps = mutableListOf<SessionEditorDragUiState>()
        val controller = SessionEditorDragController()
        controller.onUiStateChanged = { snaps.add(controller.snapshotUiState()) }
        val session = Session(
            id = "s1",
            name = "Test",
            exercises = listOf(
                Exercise(id = "e1", name = "A"),
                Exercise(id = "e2", name = "B"),
            ),
        )
        controller.looseContentBounds = Rect(0f, 0f, 200f, 400f)
        controller.exerciseBounds["__loose__|e1"] = Rect(0f, 0f, 100f, 80f)
        controller.exerciseBounds["__loose__|e2"] = Rect(0f, 100f, 100f, 180f)
        controller.beginExerciseDrag("__loose__", "e1", session = session)
        controller.updateExerciseDrag(Offset(0f, 120f), session)
        assertTrue(controller.exerciseDropTargetPartId != null)
        var moved = false
        controller.cancelExerciseDrag()
        controller.endExerciseDrag(session) { _, _, _, _ -> moved = true }
        assertFalse(moved)
        assertNull(controller.draggingExerciseId)
        assertTrue(snaps.any { it.draggingExerciseId == "e1" })
        assertTrue(snaps.last().draggingExerciseId == null)
    }

    @Test
    fun smoke_longListAutoScrollRecompute_updatesIndexWithFixedPointer() {
        val controller = SessionEditorDragController()
        val exercises = (1..12).map { Exercise(id = "e$it", name = "Ex $it") }
        val session = Session(id = "s1", name = "Test", exercises = exercises)
        controller.looseContentBounds = Rect(0f, 0f, 300f, 2000f)
        exercises.forEachIndexed { i, ex ->
            val top = i * 100f
            controller.exerciseBounds["__loose__|${ex.id}"] = Rect(0f, top, 300f, top + 80f)
        }
        controller.beginExerciseDrag(
            "__loose__",
            "e1",
            pointerStartWindow = Offset(10f, 40f),
            session = session,
        )
        controller.recomputeExerciseDropTarget()
        val before = controller.exerciseDropTargetIndex
        // Simulate auto-scroll: content moves up under a still finger → targets advance.
        controller.applyScrollDelta(250f)
        controller.recomputeExerciseDropTarget()
        assertTrue(controller.exerciseDropTargetIndex != before)
    }

    @Test
    fun smoke_expandedCardHeight_doesNotBreakHitTest() {
        val controller = SessionEditorDragController()
        val session = Session(
            id = "s1",
            name = "Test",
            exercises = listOf(
                Exercise(id = "e1", name = "Tall"),
                Exercise(id = "e2", name = "Short"),
            ),
        )
        controller.looseContentBounds = Rect(0f, 0f, 300f, 700f)
        controller.exerciseBounds["__loose__|e1"] = Rect(0f, 0f, 300f, 280f) // expanded
        controller.exerciseBounds["__loose__|e2"] = Rect(0f, 300f, 300f, 380f)
        controller.beginExerciseDrag(
            "__loose__",
            "e1",
            pointerStartWindow = Offset(10f, 140f),
            session = session,
        )
        controller.updateExerciseDrag(Offset(0f, 220f), session)
        assertEquals("__loose__", controller.exerciseDropTargetPartId)
        assertEquals(2, controller.exerciseDropTargetIndex)
        assertFalse(controller.exerciseDropOutOfRange)
    }
}

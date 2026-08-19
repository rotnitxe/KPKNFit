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

    @Test
    fun dragIntoEmptyLooseSection_allowsDroppingWhenLooseHasZeroExercises() {
        val controller = SessionEditorDragController()
        val part1 = SessionPart(
            id = "p1",
            name = "Grupo 1",
            exercises = listOf(
                Exercise(id = "e1", name = "A", exerciseDbId = "a"),
            ),
        )
        // Session with 0 loose exercises, 1 exercise in p1
        val session = Session(
            id = "s1",
            name = "Test",
            exercises = emptyList(),
            parts = listOf(part1),
        )

        // Loose container bounds registered by StrengthAddActions
        controller.looseContentBounds = Rect(0f, 0f, 300f, 200f)
        controller.partBounds["p1"] = Rect(0f, 220f, 300f, 270f)
        controller.exerciseBounds["p1|e1"] = Rect(0f, 270f, 300f, 370f)
        controller.registerPartFooterBounds("p1", Rect(0f, 370f, 300f, 420f))

        controller.beginExerciseDrag("p1", "e1", Offset(0f, 50f))

        // Drag upwards into loose container space (target Y = 100 in loose container)
        // e1 center starts at 320. Delta Y = -220 -> pointer Y = 100
        controller.updateExerciseDrag(Offset(0f, -220f), session)

        assertEquals("__loose__", controller.exerciseDropTargetPartId)
        assertEquals(0, controller.exerciseDropTargetIndex)
    }

    @Test
    fun dragIntoEmptyPart_allowsDroppingAtZeroIndex() {
        val controller = SessionEditorDragController()
        val emptyPart = SessionPart(
            id = "p_empty",
            name = "Grupo Vacio",
            exercises = emptyList(),
        )
        val partWithEx = SessionPart(
            id = "p_src",
            name = "Grupo Fuente",
            exercises = listOf(
                Exercise(id = "e1", name = "A", exerciseDbId = "a"),
            ),
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

        controller.beginExerciseDrag("p_src", "e1", Offset(0f, 50f))

        // Drag upwards into p_empty space (pointer Y = 50)
        // e1 center starts at 220. Delta Y = -170 -> pointer Y = 50
        controller.updateExerciseDrag(Offset(0f, -170f), session)

        assertEquals("p_empty", controller.exerciseDropTargetPartId)
        assertEquals(0, controller.exerciseDropTargetIndex)
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

        // Drag e0 downwards to insert before e2 (targetIndex = 2, between e1 and e2)
        controller.beginExerciseDrag("__loose__", "e0", Offset(0f, 50f))
        // e0 center = 50. Drag to between e1 center (150) and e2 center (250), e.g. pointer Y = 200 (delta = 150)
        controller.updateExerciseDrag(Offset(0f, 150f), session)

        assertEquals("__loose__", controller.exerciseDropTargetPartId)
        assertEquals(2, controller.exerciseDropTargetIndex)

        // Calculate shifts for each item:
        // Dragged e0 -> 0 shift
        val shiftE0 = controller.calculateProjectedShift(session, "__loose__", 0, "e0", 100f)
        assertEquals(0f, shiftE0)

        // e1 (index 1): in range (0 < index < 2) -> shifts UP by -gap so it takes position 0
        val shiftE1 = controller.calculateProjectedShift(session, "__loose__", 1, "e1", 100f)
        assertTrue("e1 should shift UP to open gap at position 1", shiftE1 < 0f)

        // e2 (index 2): at targetIndex -> stays at 0f so gap is open right ABOVE e2
        val shiftE2 = controller.calculateProjectedShift(session, "__loose__", 2, "e2", 100f)
        assertEquals(0f, shiftE2)

        // e3 (index 3): after targetIndex -> stays at 0f
        val shiftE3 = controller.calculateProjectedShift(session, "__loose__", 3, "e3", 100f)
        assertEquals(0f, shiftE3)
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

        // Drag e3 (index 3) upwards to insert before e1 (targetIndex = 1)
        controller.beginExerciseDrag("__loose__", "e3", Offset(0f, 50f))
        // e3 center = 350. Drag to between e0 center (50) and e1 center (150), e.g. pointer Y = 100 (delta = -250)
        controller.updateExerciseDrag(Offset(0f, -250f), session)

        assertEquals("__loose__", controller.exerciseDropTargetPartId)
        assertEquals(1, controller.exerciseDropTargetIndex)

        // e0 (index 0): before targetIndex -> stays at 0f
        val shiftE0 = controller.calculateProjectedShift(session, "__loose__", 0, "e0", 100f)
        assertEquals(0f, shiftE0)

        // e1 (index 1): in range (1 <= index < 3) -> shifts DOWN by gap
        val shiftE1 = controller.calculateProjectedShift(session, "__loose__", 1, "e1", 100f)
        assertTrue("e1 should shift DOWN to open gap at position 1", shiftE1 > 0f)

        // e2 (index 2): in range (1 <= index < 3) -> shifts DOWN by gap
        val shiftE2 = controller.calculateProjectedShift(session, "__loose__", 2, "e2", 100f)
        assertTrue("e2 should shift DOWN", shiftE2 > 0f)

        // e3 (dragged) -> 0 shift
        val shiftE3 = controller.calculateProjectedShift(session, "__loose__", 3, "e3", 100f)
        assertEquals(0f, shiftE3)
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
                    exercises = listOf(
                        Exercise(id = "e3", name = "C", exerciseDbId = "c"),
                    ),
                ),
            ),
        )

        controller.looseContentBounds = Rect(0f, 0f, 300f, 200f)
        controller.exerciseBounds["__loose__|e1"] = Rect(0f, 0f, 300f, 100f)
        controller.exerciseBounds["__loose__|e2"] = Rect(0f, 100f, 300f, 200f)
        controller.partBounds["p1"] = Rect(0f, 220f, 300f, 270f)
        controller.exerciseBounds["p1|e3"] = Rect(0f, 270f, 300f, 370f)
        controller.registerPartFooterBounds("p1", Rect(0f, 370f, 300f, 420f))

        // Step 1: Drag e1 from loose to p1
        controller.beginExerciseDrag("__loose__", "e1", Offset(0f, 50f))
        controller.updateExerciseDrag(Offset(0f, 300f), session)
        assertEquals("p1", controller.exerciseDropTargetPartId)

        controller.endExerciseDrag(session) { from, exId, to, idx ->
            session = session.moveExerciseForMultiStep(from, exId, to, idx)
        }

        // Prune bounds with updated session (e1 is now in p1, loose has e2)
        controller.pruneBounds(session, emptySet())

        // Step 2: Drag e2 from loose to p1 (leaving loose with 0 exercises!)
        controller.beginExerciseDrag("__loose__", "e2", Offset(0f, 50f))
        controller.updateExerciseDrag(Offset(0f, 300f), session)
        assertEquals("p1", controller.exerciseDropTargetPartId)

        controller.endExerciseDrag(session) { from, exId, to, idx ->
            session = session.moveExerciseForMultiStep(from, exId, to, idx)
        }

        // Now loose has 0 exercises!
        assertTrue(session.exercises.isEmpty())
        controller.pruneBounds(session, emptySet())

        // Step 3: Drag e3 from p1 BACK to loose (even though loose has 0 exercises!)
        controller.beginExerciseDrag("p1", "e3", Offset(0f, 50f))
        // Drag upwards to loose container (pointer Y in loose range)
        controller.updateExerciseDrag(Offset(0f, -300f), session)

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
    fun cardioExerciseDrag_onlyTargetsCardioGroups() {
        val controller = SessionEditorDragController()
        val cardioPart = SessionPart(
            id = "p_cardio",
            name = "Cardio Space",
            isCardioGroup = true,
            exercises = listOf(
                Exercise(
                    id = "c1",
                    name = "Running",
                    cardioDetails = com.example.kpkn.data.models.CardioDetails(type = com.example.kpkn.data.models.CardioType.RUN_OUTDOOR),
                ),
            ),
        )
        val strengthPart = SessionPart(
            id = "p_strength",
            name = "Fuerza",
            exercises = listOf(
                Exercise(id = "s1", name = "Squat"),
            ),
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

        controller.beginExerciseDrag("p_cardio", "c1", Offset(0f, 50f))

        // Drag upwards into strength area (delta = -250 -> pointer Y = 190)
        controller.updateExerciseDrag(Offset(0f, -250f), session)

        // Cardio exercise cannot target strength parts or loose container, so target remains p_cardio
        assertEquals("p_cardio", controller.exerciseDropTargetPartId)
    }

    @Test
    fun strengthExerciseDrag_onlyTargetsStrengthGroupsAndLoose() {
        val controller = SessionEditorDragController()
        val strengthPart = SessionPart(
            id = "p_strength",
            name = "Fuerza",
            exercises = listOf(
                Exercise(id = "s1", name = "Squat"),
            ),
        )
        val cardioPart = SessionPart(
            id = "p_cardio",
            name = "Cardio Space",
            isCardioGroup = true,
            exercises = listOf(
                Exercise(
                    id = "c1",
                    name = "Running",
                    cardioDetails = com.example.kpkn.data.models.CardioDetails(type = com.example.kpkn.data.models.CardioType.RUN_OUTDOOR),
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

        controller.beginExerciseDrag("p_strength", "s1", Offset(0f, 50f))

        // Drag downwards directly into cardio area (delta = +250 -> pointer Y = 350)
        controller.updateExerciseDrag(Offset(0f, 250f), session)

        // Strength exercise cannot target cardio space, so target remains p_strength
        assertEquals("p_strength", controller.exerciseDropTargetPartId)
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

        controller.beginExerciseDrag("__loose__", "single_ex", Offset(0f, 50f))
        controller.updateExerciseDrag(Offset(0f, 50f), session)

        // Target index is 0, shift is 0f
        val shift = controller.calculateProjectedShift(session, "__loose__", 0, "single_ex", 100f)
        assertEquals(0f, shift)

        var moved = false
        controller.endExerciseDrag(session) { _, _, _, _ -> moved = true }
        // End drag cleanly resets
        assertNull(controller.draggingExerciseId)
    }

    @Test
    fun partDrag_movingCardioUpAndThenStrengthUp_correctlyReordersBothWays() {
        val controller = SessionEditorDragController()
        val strengthPart = SessionPart(id = "p_strength", name = "Pecho", isCardioGroup = false)
        val cardioPart = SessionPart(id = "p_cardio", name = "Cardio", isCardioGroup = true)
        val parts = listOf(strengthPart, cardioPart)

        // 1. Drag Cardio UP to index 0
        val liveBounds1 = mapOf(
            "p_strength" to Rect(0f, 100f, 300f, 300f),
            "p_cardio" to Rect(0f, 350f, 300f, 550f),
        )
        controller.beginPartDrag("p_cardio", liveBounds1)
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

        // 2. Now cardio is at index 0, strength at index 1
        val reorderedParts = listOf(cardioPart, strengthPart)
        val liveBounds2 = mapOf(
            "p_cardio" to Rect(0f, 100f, 300f, 300f),
            "p_strength" to Rect(0f, 350f, 300f, 550f),
        )
        controller.beginPartDrag("p_strength", liveBounds2)
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

        // 3. Drag cardio from top all the way DOWN past strength
        val liveBounds3 = mapOf(
            "p_cardio" to Rect(0f, 100f, 300f, 300f),
            "p_strength" to Rect(0f, 350f, 300f, 550f),
        )
        controller.beginPartDrag("p_cardio", liveBounds3)
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
}

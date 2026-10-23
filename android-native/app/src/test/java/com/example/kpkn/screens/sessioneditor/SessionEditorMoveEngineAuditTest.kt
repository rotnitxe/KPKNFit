package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionEditorMoveEngineAuditTest {
    @Test fun droppingIntoADeletedGroupCannotDeleteTheExercise() {
        val session = Session(id = "session", name = "Día", exercises = listOf(Exercise(id = "e", name = "Press")))
        val moved = SessionEditorMoveEngine.move(session, SessionEditorMoveRequest(
            sourcePartId = null, exerciseId = "e", targetPartId = "group-removed-during-drag", targetIndex = 0,
        ))
        assertEquals(session, moved)
    }

    @Test fun twentyExercisesKeepTheirIdentityWhenMovedBetweenGroups() {
        val exercises = List(20) { Exercise(id = "e$it", name = "Ejercicio $it") }
        var session = Session(id = "session", name = "Día", exercises = exercises,
            parts = listOf(SessionPart(id = "group", name = "Grupo")))
        exercises.forEach {
            session = SessionEditorMoveEngine.move(session, SessionEditorMoveRequest(null, it.id, "group", 0))
        }
        assertEquals(20, session.allExercises().size)
        assertEquals(exercises.map { it.id }.toSet(), session.allExercises().map { it.id }.toSet())
        assertEquals(exercises.reversed().map { it.id }, session.parts.single().exercises.map { it.id })
    }
}

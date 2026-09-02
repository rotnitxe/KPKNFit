package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SessionEditorSessionHelpersTest {

    private fun session(id: String, modifiedAt: Long, extraSets: Int = 1): Session = Session(
        id = id,
        name = if (extraSets > 1) "updated" else "original",
        lastModifiedAtMs = modifiedAt,
        exercises = listOf(
            Exercise(
                id = "ex",
                name = "Press",
                sets = (0 until extraSets).map { ExerciseSet(id = "s$it", targetReps = 8) },
            ),
        ),
    )

    @Test
    fun programSessionWinsWhenNewerThanEditorDraft() {
        val program = session("s1", modifiedAt = 2_000L, extraSets = 3)
        val draftSession = session("s1", modifiedAt = 500L, extraSets = 1)
        val draft = PersistedSessionEditorDraft(
            programId = "p",
            sessionId = "s1",
            weekId = "w",
            macroIndex = 0,
            mesoIndex = 0,
            session = draftSession,
            savedAtMs = 1_500L,
        )
        val resolved = resolveNewestSession(existing = program, fallback = program, persistedDraft = draft)
        assertEquals("updated", resolved.name)
        assertEquals(3, resolved.exercises.first().sets.size)
    }

    @Test
    fun draftWinsWhenNewerThanProgram() {
        val program = session("s1", modifiedAt = 1_000L, extraSets = 1)
        val draftSession = session("s1", modifiedAt = 2_000L, extraSets = 2)
        val draft = PersistedSessionEditorDraft(
            programId = "p",
            sessionId = "s1",
            weekId = "w",
            macroIndex = 0,
            mesoIndex = 0,
            session = draftSession,
            savedAtMs = 2_500L,
        )
        val resolved = resolveNewestSession(existing = program, fallback = program, persistedDraft = draft)
        assertEquals(2, resolved.exercises.first().sets.size)
    }

    @Test
    fun missingDraftReturnsProgram() {
        val program = session("s1", modifiedAt = 1L)
        val resolved = resolveNewestSession(existing = program, fallback = program, persistedDraft = null)
        assertSame(program, resolved)
    }
}

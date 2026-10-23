package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Session
import com.example.kpkn.domain.exercises.resolvedCanonicalExerciseId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionEditorRelationshipAnchorNameTest {
    private val reverseFlyConfigurationId = "reverse_pec_fly__bilateral__dumbbells"

    @Test
    fun `la identidad de ancla de otra sesion resuelve su nombre real de programa`() {
        val anchor = Exercise(
            id = "anchor",
            name = "Aperturas Inversas",
            exerciseDbId = reverseFlyConfigurationId,
        )
        val anchorId = anchor.resolvedCanonicalExerciseId()
        val variation = Exercise(
            id = "variation",
            name = "Cruce inverso",
            relativeToCanonicalExerciseId = anchorId,
        )
        val candidate = ProgramExerciseCandidate(
            exerciseId = anchor.id,
            exerciseName = anchor.name,
            exerciseDbId = anchor.exerciseDbId,
            sessionDayOfWeek = 2,
            sessionName = "Día B",
            partName = null,
        )

        val name = resolveRelationshipAnchorName(
            session = Session(id = "day-a", name = "Día A", exercises = listOf(variation)),
            exercise = variation,
            programCandidates = listOf(candidate),
        )

        assertEquals("Aperturas Inversas", name)
        assertNotEquals(anchorId, name)
    }

    @Test
    fun `sin una ancla real en sesion o programa no se muestra la identidad cruda`() {
        val variation = Exercise(
            id = "variation",
            name = "Cruce inverso",
            relativeToCanonicalExerciseId = reverseFlyConfigurationId,
        )

        val name = resolveRelationshipAnchorName(
            session = Session(id = "day-a", name = "Día A", exercises = listOf(variation)),
            exercise = variation,
        )

        assertNull(name)
    }
}

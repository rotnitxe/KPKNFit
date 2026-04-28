package com.example.kpkn.domain.templates

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.data.sessions.SessionTemplateApplyMode
import com.example.kpkn.data.sessions.SessionTemplateSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTemplateEngineTest {

    private fun makeSet(id: String = "set-${java.util.UUID.randomUUID()}") = ExerciseSet(
        id = id,
        targetReps = 10,
        targetRPE = 8.0,
        intensityMode = IntensityMode.RPE,
    )

    private fun makeExercise(
        id: String = "ex-${java.util.UUID.randomUUID()}",
        name: String = "Exercise",
        sets: List<ExerciseSet> = listOf(makeSet()),
    ) = Exercise(id = id, name = name, sets = sets)

    private fun makePart(
        id: String = "part-${java.util.UUID.randomUUID()}",
        name: String = "Part",
        exercises: List<Exercise> = listOf(makeExercise()),
    ) = SessionPart(id = id, name = name, exercises = exercises)

    private fun makeTemplate(
        id: String = "template-${java.util.UUID.randomUUID()}",
        name: String = "Template",
        exercises: List<Exercise> = listOf(makeExercise()),
        parts: List<SessionPart> = emptyList(),
    ) = SessionTemplate(
        id = id,
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = name,
        description = "Test template",
        session = Session(
            id = "session-$id",
            name = name,
            exercises = exercises,
            parts = parts,
        ),
    )

    private fun makeTargetSession(
        exercises: List<Exercise> = emptyList(),
        parts: List<SessionPart> = emptyList(),
    ) = Session(
        id = "target-session",
        name = "Target Session",
        exercises = exercises,
        parts = parts,
    )

    @Test
    fun `applyReplace_clearsExistingExercises`() {
        val template = makeTemplate(
            exercises = listOf(makeExercise(id = "tpl-ex1", name = "Template Exercise")),
        )
        val target = makeTargetSession(
            exercises = listOf(makeExercise(id = "existing-ex1", name = "Existing Exercise")),
        )

        val result = SessionTemplateEngine.applyTemplate(
            template = template,
            targetSession = target,
            mode = SessionTemplateApplyMode.REPLACE,
        )

        assertTrue("Must have template exercises", result.exercises.isNotEmpty())
        assertTrue("Must not have existing exercises", result.exercises.none { it.id == "existing-ex1" })
        assertEquals("Template Exercise", result.exercises.first().name)
    }

    @Test
    fun `applyAppend_keepsExistingAndAddsNew`() {
        val template = makeTemplate(
            exercises = listOf(makeExercise(id = "tpl-ex1", name = "Template Exercise")),
        )
        val target = makeTargetSession(
            exercises = listOf(makeExercise(id = "existing-ex1", name = "Existing Exercise")),
        )

        val result = SessionTemplateEngine.applyTemplate(
            template = template,
            targetSession = target,
            mode = SessionTemplateApplyMode.APPEND,
        )

        assertTrue("Must have both existing and template exercises", result.exercises.size >= 2)
        assertTrue("Must keep existing", result.exercises.any { it.name == "Existing Exercise" })
        assertTrue("Must add template", result.exercises.any { it.name == "Template Exercise" })
    }

    @Test
    fun `templateApplyRegeneratesIds`() {
        val template = makeTemplate(
            exercises = listOf(
                makeExercise(id = "tpl-ex1", name = "Exercise 1", sets = listOf(makeSet("tpl-set1"), makeSet("tpl-set2"))),
            ),
        )
        val target = makeTargetSession()

        val result = SessionTemplateEngine.applyTemplate(
            template = template,
            targetSession = target,
            mode = SessionTemplateApplyMode.REPLACE,
        )

        val originalIds = setOf("tpl-ex1", "tpl-set1", "tpl-set2")
        val resultExerciseIds = result.exercises.map { it.id }.toSet()
        val resultSetIds = result.exercises.flatMap { it.sets.map { s -> s.id } }.toSet()

        assertTrue("Exercise IDs must be regenerated", originalIds.intersect(resultExerciseIds).isEmpty())
        assertTrue("Set IDs must be regenerated", originalIds.intersect(resultSetIds).isEmpty())
    }

    @Test
    fun `supersetLinksPreservedWithinTemplate`() {
        val supersetId = "superset-1"
        val ex1 = makeExercise(id = "tpl-ex1", name = "Exercise 1").copy(supersetId = supersetId)
        val ex2 = makeExercise(id = "tpl-ex2", name = "Exercise 2").copy(supersetId = supersetId)
        val template = makeTemplate(exercises = listOf(ex1, ex2))
        val target = makeTargetSession()

        val result = SessionTemplateEngine.applyTemplate(
            template = template,
            targetSession = target,
            mode = SessionTemplateApplyMode.REPLACE,
        )

        val supersetIds = result.exercises.mapNotNull { it.supersetId }.distinct()
        assertEquals("Must have one superset ID", 1, supersetIds.size)
        assertFalse("Superset ID must be different from original", supersetIds.contains(supersetId))
        assertEquals("Both exercises must share the same superset ID", 2, result.exercises.count { it.supersetId == supersetIds.first() })
    }

    @Test
    fun `sessionHasContent_returnsTrueForNonEmpty`() {
        val session = makeTargetSession(exercises = listOf(makeExercise()))
        assertTrue(SessionTemplateEngine.sessionHasContent(session))
    }

    @Test
    fun `sessionHasContent_returnsFalseForEmpty`() {
        val session = makeTargetSession()
        assertFalse(SessionTemplateEngine.sessionHasContent(session))
    }

    @Test
    fun `cloneSessionContent_generatesNewIds`() {
        val original = makeTargetSession(
            exercises = listOf(
                makeExercise(id = "orig-ex1", name = "Exercise 1", sets = listOf(makeSet("orig-set1"))),
            ),
        )

        val cloned = SessionTemplateEngine.cloneSessionContent(original)

        assertNotEquals("Cloned session ID must differ", original.id, cloned.id)
        assertTrue("Cloned exercises must have new IDs", cloned.exercises.none { it.id == "orig-ex1" })
        assertTrue("Cloned sets must have new IDs", cloned.exercises.flatMap { it.sets }.none { it.id == "orig-set1" })
    }

    @Test
    fun `applyAppend_withParts_addsPartsCorrectly`() {
        val template = makeTemplate(
            parts = listOf(makePart(id = "tpl-part1", name = "Template Part")),
        )
        val target = makeTargetSession(
            parts = listOf(makePart(id = "existing-part1", name = "Existing Part")),
        )

        val result = SessionTemplateEngine.applyTemplate(
            template = template,
            targetSession = target,
            mode = SessionTemplateApplyMode.APPEND,
        )

        assertTrue("Must have both existing and template parts", result.parts.size >= 2)
        assertTrue("Must keep existing part", result.parts.any { it.name == "Existing Part" })
        assertTrue("Must add template part", result.parts.any { it.name == "Template Part" })
    }
}

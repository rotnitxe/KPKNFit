package com.example.kpkn.domain.training.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Bloque3ImportDraftIntegrityTest {
    private val parsed = ProgramImportParser.parseText("Día de fuerza\nPress banca 3x8", "Plan")
    private val original = parsed.weeks.single().days.single()
    private val day = original.copy(id = "x40", exercises = original.exercises.map { it.copy(id = "x41") })

    @Test
    fun `structural edits rebase warnings and honor IDs already in use`() {
        val warning = ImportWarning(
            id = "warning", kind = ImportWarningKind.IGNORED_VALUE,
            location = "Línea 2", sourceText = null, message = "Revisar", weekIndex = 2, dayId = "day2", exerciseId = "exercise2",
        )
        val second = day.copy(id = "day2", exercises = day.exercises.map { it.copy(id = "exercise2") })
        val draft = parsed.copy(
            weeks = listOf(ImportedWeek(1, listOf(day)), ImportedWeek(2, listOf(second))),
            warnings = listOf(warning), reviewedWarnings = setOf(warning.id), nextId = 1,
        )
        val copied = ImportDraftEditor.duplicateWeek(draft, 1)
        assertEquals("x42", copied.weeks[1].days.single().id)
        assertEquals("x43", copied.weeks[1].days.single().exercises.single().id)
        assertEquals(3, copied.warnings.single().weekIndex)
        assertEquals(1, ImportDraftEditor.removeWeek(draft, 1).warnings.single().weekIndex)
        assertTrue(ImportDraftEditor.removeWeek(draft, 2).reviewedWarnings.isEmpty())
        val withoutDay = ImportDraftEditor.removeDay(draft, second.id)
        assertTrue(withoutDay.warnings.isEmpty())
        assertTrue(withoutDay.reviewedWarnings.isEmpty())
    }

    @Test
    fun `superset boundaries and duplicate week numbers yield distinct IDs`() {
        val exercise = day.exercises.single()
        assertEquals(2, ImportedProgramBuilder.groupConsecutive(listOf(exercise.copy(supersetGroup = "A"), exercise.copy(supersetGroup = "B"))).size)
        assertTrue(ImportedProgramBuilder.kilograms(Double.NaN, ImportLoadUnit.LB).isNaN())
        val weeks = listOf(ImportedWeek(1, listOf(day)), ImportedWeek(1, listOf(day.copy(id = "second"))))
        val draft = parsed.copy(weeks = weeks)
        val sourceMap = ImportedProgramBuilder.sourceMap(draft, emptyMap(), "plan")
        assertEquals(2, sourceMap.sessionIdsByDayId.values.toSet().size)
    }
}
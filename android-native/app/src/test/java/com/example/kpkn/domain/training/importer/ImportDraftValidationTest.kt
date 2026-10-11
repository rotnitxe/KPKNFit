package com.example.kpkn.domain.training.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportDraftValidationTest {

    @Test
    fun `un borrador listo no tiene problemas`() {
        val ready = ImportedProgramDraft(
            title = "Listo",
            weeks = listOf(
                ImportedWeek(1, listOf(ImportedDay("w1-d1", "Pierna", listOf(ex("w1-d1-e1", "Sentadilla"))))),
            ),
            warnings = listOf(warning("a1")),
            reviewedWarnings = setOf("a1"),
        )

        val issues = ImportDraftValidation.check(ready) { true }

        assertTrue(issues.isEmpty())
        assertTrue(ImportDraftValidation.isReady(issues))
    }

    @Test
    fun `sin semanas el problema es NO_WEEKS`() {
        val empty = ImportedProgramDraft(title = "Vacío", weeks = emptyList())

        val issues = ImportDraftValidation.check(empty) { true }

        assertEquals(listOf(ImportIssueKind.NO_WEEKS), issues.map { it.kind })
        assertFalse(ImportDraftValidation.isReady(issues))
    }

    @Test
    fun `una semana vacia y un dia vacio salen como EMPTY_WEEK y EMPTY_DAY con su sitio`() {
        val draft = ImportedProgramDraft(
            title = "Huecos",
            weeks = listOf(
                ImportedWeek(1, listOf(ImportedDay("w1-d1", "Pierna", listOf(ex("w1-d1-e1", "Sentadilla"))))),
                ImportedWeek(2, listOf(ImportedDay("w2-d1", "Pierna", emptyList()))),
                ImportedWeek(3, emptyList()),
            ),
        )

        val issues = ImportDraftValidation.check(draft) { true }

        assertEquals(
            listOf(ImportIssueKind.EMPTY_WEEK, ImportIssueKind.EMPTY_DAY, ImportIssueKind.EMPTY_WEEK),
            issues.map { it.kind },
        )
        assertEquals(listOf(2, 2, 3), issues.map { it.weekIndex })
        assertEquals(listOf(null, "w2-d1", null), issues.map { it.dayId })
    }

    @Test
    fun `un nombre sin decidir sale una vez por nombre distinto, en su primer sitio`() {
        val draft = ImportedProgramDraft(
            title = "Nombres",
            weeks = listOf(
                ImportedWeek(
                    1,
                    listOf(
                        ImportedDay(
                            "w1-d1",
                            "Pierna",
                            listOf(
                                ex("w1-d1-e1", "Sentadilla"),
                                ex("w1-d1-e2", "Press banca"),
                                ex("w1-d1-e3", "Sentadilla"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val issues = ImportDraftValidation.check(draft) { it == "Press banca" }

        val undecided = issues.filter { it.kind == ImportIssueKind.UNDECIDED_EXERCISE }
        assertEquals(1, undecided.size)
        assertEquals("Sentadilla", undecided.single().rawName)
        assertEquals("w1-d1-e1", undecided.single().exerciseId)
        assertEquals("w1-d1", undecided.single().dayId)
        assertEquals(1, undecided.single().weekIndex)
        assertFalse(ImportDraftValidation.isReady(issues))
    }

    @Test
    fun `los avisos sin revisar salen con su numero`() {
        val draft = ImportedProgramDraft(
            title = "Avisos",
            weeks = listOf(
                ImportedWeek(1, listOf(ImportedDay("w1-d1", "Pierna", listOf(ex("w1-d1-e1", "Sentadilla"))))),
            ),
            warnings = listOf(warning("a1"), warning("a2"), warning("a3")),
            reviewedWarnings = setOf("a2"),
        )

        val issues = ImportDraftValidation.check(draft) { true }

        assertEquals(listOf(ImportIssueKind.PENDING_WARNINGS), issues.map { it.kind })
        assertEquals(2, issues.single().count)
    }

    @Test
    fun `sin semanas y con un aviso pendiente salen los dos problemas`() {
        val draft = ImportedProgramDraft(title = "Nada", weeks = emptyList(), warnings = listOf(warning("a1")))

        val issues = ImportDraftValidation.check(draft) { true }

        assertEquals(listOf(ImportIssueKind.NO_WEEKS, ImportIssueKind.PENDING_WARNINGS), issues.map { it.kind })
    }

    private fun ex(id: String, name: String): ImportedExercise =
        ImportedExercise(id = id, rawName = name, sets = 3, repsMin = 8, repsMax = 8)

    private fun warning(id: String): ImportWarning = ImportWarning(
        id = id,
        kind = ImportWarningKind.SKIPPED_ROW,
        location = "Línea 2",
        sourceText = "Hacer 3 vueltas",
        message = "Línea 2: no se entiende «Hacer 3 vueltas»",
        weekIndex = 1,
        dayId = "w1-d1",
        exerciseId = null,
    )
}

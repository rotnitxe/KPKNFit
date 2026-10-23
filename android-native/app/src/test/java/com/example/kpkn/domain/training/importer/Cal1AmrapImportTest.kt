package com.example.kpkn.domain.training.importer

import com.example.kpkn.data.models.SetPurpose
import org.junit.Assert.*
import org.junit.Test

class Cal1AmrapImportTest {
    @Test fun aPlusInTheLoadFieldCannotInventAnAmrapMinimum() {
        val draft = ProgramImportParser.parseSheets(listOf(ImportSheet("Día 1", listOf(
            listOf("Ejercicio", "Series", "Reps", "Peso (kg)"),
            listOf("Sentadilla", "3", "5", "80+"),
        ))), "Carga")
        val exercise = draft.weeks.single().days.single().exercises.single()
        assertEquals(SetPurpose.NONE, exercise.purpose)
        assertNull(exercise.amrapMinimumReps)
        assertTrue(plan(draft).single().sets.all { it.purpose == SetPurpose.NONE && it.amrapMinimumReps == null })
    }

    private fun plan(draft: ImportedProgramDraft) = ImportedProgramBuilder.build(
        draft, resolved = emptyMap(), weekdays = listOf(1), programId = "cal1",
    ).macrocycles.single().blocks.single().mesocycles.single().weeks.single().sessions.single().allExercises()

    @Test fun plusNotationReachesTheActualLastSetWithItsMinimum() {
        for ((scheme, count, minimum) in listOf(Triple("5+", 1, 5), Triple("3x5+", 3, 5), Triple("1+", 1, 1))) {
            val draft = ProgramImportParser.parseSheets(listOf(ImportSheet("Día 1", listOf(
                listOf("Ejercicio", "Series", "Reps"), listOf("Sentadilla", count.toString(), scheme),
            ))), "AMRAP")
            val parsed = draft.weeks.single().days.single().exercises.single()
            assertEquals(scheme, SetPurpose.AMRAP, parsed.purpose)
            assertEquals(minimum, parsed.amrapMinimumReps)
            val sets = plan(draft).single().sets
            assertEquals(count, sets.size)
            assertEquals(SetPurpose.AMRAP, sets.last().purpose)
            assertEquals(minimum, sets.last().amrapMinimumReps)
            assertTrue(sets.dropLast(1).all { it.purpose == SetPurpose.NONE })
            assertTrue(parsed.notes.orEmpty().contains("AMRAP"))
        }
    }

    @Test fun bareAmrapImportsWithExplicitlyNoMinimumInTableAndPlainText() {
        val drafts = listOf(
            ProgramImportParser.parseText("Sentadilla AMRAP", "Libre"),
            ProgramImportParser.parseSheets(listOf(ImportSheet("Día 1", listOf(
                listOf("Ejercicio", "Series", "Reps"), listOf("Sentadilla", "3", "AMRAP"),
            ))), "Libre"),
        )
        for (draft in drafts) {
            val sets = plan(draft).single().sets
            assertTrue(sets.isNotEmpty())
            assertTrue(sets.all { it.purpose == SetPurpose.AMRAP && it.amrapMinimumReps == null && it.targetReps == null })
        }
    }
}

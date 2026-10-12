package com.example.kpkn.domain.training.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * H3h: regresiones de H3g en hojas de la colección (HLM, Nuckols, Seba Kot, Sheikburn) y la notación de porcentaje.
 * Las hojas están escritas a mano, con nombres inventados; solo imitan la disposición de las plantillas reales.
 */
class ProgramImportParserRegressionsTest {

    @Test
    fun `HLM, una columna de repeticiones base no abre un grupo, el porcentaje y la carga van con su semana`() {
        val sheet = ImportSheet(
            name = "Phase 1",
            rows = listOf(
                listOf("Phase 1  -  Weeks 1-6 (volume)"),
                listOf("Monday  (Heavy day)"),
                listOf("", "", "", "", "Week 1", "", "", "Week 2", "", "", "Week 3", "", "", "Week 4", "", ""),
                listOf("Type", "Movement", "Sets", "Reps", "%", "Load", "Reps Done", "%", "Load", "Reps Done", "%", "Load", "Reps Done", "%", "Load", "Reps Done"),
                listOf("Heavy", "Squat", "5", "5", "0.7", "245", "", "0.725", "255", "", "0.75", "265", "", "0.775", "270", ""),
                listOf("Heavy", "Bench Press", "5", "5", "0.7", "165", "", "0.725", "170", "", "0.75", "175", "", "0.775", "180", ""),
            ),
        )

        val draft = ProgramImportParser.parseSheets(listOf(sheet), title = "HLM")

        assertEquals(listOf(1, 2, 3, 4), draft.weeks.map { it.index })
        draft.weeks.forEach { week -> assertEquals(listOf("Monday  (Heavy day)"), week.days.map { it.name }) }
        val squat = draft.weeks.map { week -> week.days.single().exercises.first() }
        assertEquals(listOf("Squat", "Squat", "Squat", "Squat"), squat.map { it.rawName })
        assertEquals(listOf(5, 5, 5, 5), squat.map { it.sets })
        assertEquals(listOf(5, 5, 5, 5), squat.map { it.repsMin })
        assertEquals(listOf(245.0, 255.0, 265.0, 270.0), squat.map { it.loadKg })
        assertEquals(listOf(70.0, 72.5, 75.0, 77.5), squat.map { it.percent1Rm })
        assertEquals(listOf(165.0, 170.0, 175.0, 180.0), draft.weeks.map { week -> week.days.single().exercises[1].loadKg })
    }

    @Test
    fun `Nuckols, el dia repetido en cada semana no corta el bloque y las filas de cada semana se leen`() {
        val sheet = ImportSheet(
            name = "High Frequency",
            rows = listOf(
                listOf("High", "Week 1", "", "", "", "", "High", "Week 2", "", "", "", "", "High", "Week 3", "", "", ""),
                listOf("Frequency", "Exercise", "Weight", "Sets", "Reps", "", "Frequency", "Exercise", "Weight", "Sets", "Reps", "", "Frequency", "Exercise", "Weight", "Sets", "Reps"),
                listOf("Monday", "Bench", "50", "4", "3", "", "Monday", "Bench", "50", "5", "3", "", "Monday", "Bench", "50", "5", "3"),
                listOf("", "Tricep Work", "RPE8", "2", "?", "", "", "Tricep Work", "RPE8", "2", "?", "", "", "Tricep Work", "RPE8", "2", "?"),
                listOf("", "Squat", "67.5", "4", "3", "", "", "Squat", "67.5", "5", "3", "", "", "Squat", "67.5", "5", "3"),
                listOf("Tuesday", "Bench", "55", "3", "2", "", "Tuesday", "Bench", "55", "4", "2", "", "Tuesday", "Bench", "55", "4", "2"),
                listOf("", "Squat", "72.5", "3", "2", "", "", "Squat", "72.5", "4", "2", "", "", "Squat", "72.5", "4", "2"),
            ),
        )

        val draft = ProgramImportParser.parseSheets(listOf(sheet), title = "Nuckols")

        assertEquals(listOf(1, 2, 3), draft.weeks.map { it.index })
        draft.weeks.forEach { week -> assertEquals(listOf("Monday", "Tuesday"), week.days.map { it.name }) }
        val monday = draft.weeks.map { week -> week.days[0].exercises }
        assertEquals(listOf(listOf("Bench", "Squat"), listOf("Bench", "Squat"), listOf("Bench", "Squat")), monday.map { day -> day.map { it.rawName } })
        assertEquals(listOf(4, 5, 5), monday.map { it.first().sets })
        assertEquals(listOf(50.0, 67.5), monday[0].map { it.loadKg })
        assertEquals(listOf(55.0, 72.5), draft.weeks[0].days[1].exercises.map { it.loadKg })
        assertEquals(listOf(3, 3, 3), monday.map { it.first().repsMin })
    }

    @Test
    fun `Seba Kot, la notacion 8x8 junto al nombre, sin cabecera, es la prescripcion de la fila`() {
        val sheet = ImportSheet(
            name = "Weeks 1 -4",
            rows = listOf(
                listOf("", "Week 1", "", "", "", "", "", "", "", "Week 2", "", "", "", "", "", "", ""),
                listOf("", "", "", "Tempo", "Rest", "RPE", "Weight", "Notes", "", "", "", "Tempo", "Rest", "RPE", "Weight", "Notes", ""),
                listOf("", "Day A1:", "", "", "", "", "", "", "", "Day A1:", "", "", "", "", "", "", ""),
                listOf("", "1. Squat", "8x8", "2010", "180s", "0.6", "60", "", "", "1. Squat", "8x8", "2010", "180s", "", "62.5", "+2.5kg", ""),
            ),
        )

        val draft = ProgramImportParser.parseSheets(listOf(sheet), title = "Seba")

        assertEquals(listOf(1, 2), draft.weeks.map { it.index })
        val week1 = draft.weeks[0].days.flatMap { it.exercises }
        assertEquals(listOf("Squat"), week1.map { it.rawName })
        assertEquals(listOf(8), week1.map { it.sets })
        assertEquals(listOf(8), week1.map { it.repsMin })
        assertEquals(listOf(60.0), week1.map { it.loadKg })
        assertEquals(listOf(62.5), draft.weeks[1].days.flatMap { it.exercises }.map { it.loadKg })
    }

    @Test
    fun `Sheikburn, el nombre del levantamiento va en la subcabecera de cada grupo y el bloque apilado hereda sus dias`() {
        val sheet = ImportSheet(
            name = "Sheet1",
            rows = listOf(
                listOf("WEEK 1:"),
                listOf("Day 1:", "", "", "", "Day 2", "", "", "", "Day 3", "", "", "", "Day 4"),
                listOf("SQUAT", "Reps", "Weight", "", "DEADLIFT", "Reps", "Weight", "", "SQUAT", "Reps", "Weight", "", "DEADLIFT", "Reps", "Weight"),
                listOf("5 x 50%", "5", "50", "", "5 x 50%", "5", "50", "", "5 x 50%", "5", "50", "", "5 x 50%", "5", "50"),
                listOf("2 x 3 x 70%", "2 x 3", "70", "", "2 x 3 x 70%", "2 x 3", "70", "", "2 x 3 x 70%", "2 x 3", "70", "", "2 x 3 x 70%", "2 x 3", "70"),
                listOf("4 x 3 x 80%", "4 x 3", "80", "", "4 x 3 x 80%", "4 x 3", "80", "", "4 x 3 x 80%", "4 x 3", "80", "", "4 x 3 x 80%", "4 x 3", "80"),
                listOf("BENCH", "Reps", "Weight", "", "OHP", "Reps", "Weight", "", "BENCH", "Reps", "Weight", "", "OHP", "Reps", "Weight"),
                listOf("2 x 3 x 70%", "2 x 3", "65", "", "2 x 3 x 70%", "2 x 3", "65", "", "2 x 3 x 70%", "2 x 3", "65", "", "2 x 3 x 70%", "2 x 3", "65"),
            ),
        )

        val draft = ProgramImportParser.parseSheets(listOf(sheet), title = "Sheikburn")

        assertEquals(listOf(1), draft.weeks.map { it.index })
        val days = draft.weeks.single().days
        assertEquals(listOf("Day 1", "Day 2", "Day 3", "Day 4"), days.map { it.name })
        assertEquals(listOf("SQUAT", "SQUAT", "BENCH"), days[0].exercises.map { it.rawName })
        assertEquals(listOf(2, 4, 2), days[0].exercises.map { it.sets })
        assertEquals(listOf(70.0, 80.0, 65.0), days[0].exercises.map { it.loadKg })
        // El bloque apilado (BENCH | OHP) hereda los días: su OHP cae en el Día 2, junto a los DEADLIFT.
        assertEquals(listOf("DEADLIFT", "DEADLIFT", "OHP"), days[1].exercises.map { it.rawName })
        assertTrue(days.flatMap { it.exercises }.none { it.repsMin == 50 })
    }

    @Test
    fun `un porcentaje con x no es una notacion de series y repeticiones`() {
        val draft = ProgramImportParser.parseText("Día 1\nSentadilla 5 x 50%\nPress 3x8", title = "Porcentaje")

        val exercises = draft.weeks.single().days.single().exercises
        assertEquals(listOf("Press"), exercises.map { it.rawName })
        assertTrue(draft.weeks.flatMap { it.days }.flatMap { it.exercises }.none { it.repsMin == 50 })
    }
}

package com.example.kpkn.data.importer

import org.junit.Assert.assertEquals
import org.junit.Test

class TextProgramReaderTest {

    @Test
    fun `con bloques cercados conserva tambien el texto fuera de ellos`() {
        val text = """
            Programa de hipertrofia, 3 días por semana.

            ```text
            Notas del autor, sin ejercicios
            ```

            ```liftoscript
            # Week 1
            ## Día 1 — Pierna
            Sentadilla / 4x6 / 120kg / 120s
            Prensa / 3x10 / 90s
            ```

            Descansa bien entre días.
        """.trimIndent()

        val sheet = TextProgramReader.read(text, "programa")

        assertEquals("programa", sheet.name)
        assertEquals(
            listOf(
                listOf("Programa de hipertrofia, 3 días por semana.", "", "", ""),
                listOf("Notas del autor, sin ejercicios", "", "", ""),
                listOf("Week 1", "", "", ""),
                listOf("Día 1 — Pierna", "", "", ""),
                listOf("Sentadilla", "4x6", "120kg", "120s"),
                listOf("Prensa", "3x10", "90s", ""),
                listOf("Descansa bien entre días.", "", "", ""),
            ),
            sheet.rows,
        )
        assertEquals(listOf(1, 4, 8, 9, 10, 11, 14), sheet.sourceLineNumbers)
    }

    @Test
    fun `tabuladores conservan columnas vacias y lineas originales`() {
        val sheet = TextProgramReader.read("# Semana 1\n\nEjercicio\tSeries\tReps\nRemo\t3\t10\n\t\t", "tabla")
        assertEquals(listOf(1, 3, 4), sheet.sourceLineNumbers)
        assertEquals(listOf("Ejercicio", "Series", "Reps"), sheet.rows[1])
        assertEquals(listOf("Remo", "3", "10"), sheet.rows[2])
    }

    @Test
    fun `texto pegado y archivo comparten lectura de tabla markdown`() {
        val sheet = TextProgramReader.read("# Día 1\n\n| Ejercicio | Series | Reps |\n|---|---|---|\n| Remo | 3 | 10 |", "")
        val pasted = com.example.kpkn.domain.training.importer.ProgramImportParser.parsePreparedText(sheet, "Plan")
        val file = com.example.kpkn.domain.training.importer.ProgramImportParser.parseSheets(listOf(sheet), "Plan")
        val fileExercise = file.weeks.single().days.single().exercises.single()
        val pastedExercise = pasted.weeks.single().days.single().exercises.single()
        assertEquals(
            fileExercise.copy(sourceDocumentType = com.example.kpkn.domain.training.importer.ImportDocumentType.TEXT_FILE),
            pastedExercise,
        )
        assertEquals(com.example.kpkn.domain.training.importer.ImportDocumentType.UNKNOWN, fileExercise.sourceDocumentType)
        assertEquals(com.example.kpkn.domain.training.importer.ImportDocumentType.TEXT_FILE, pastedExercise.sourceDocumentType)
        assertEquals("Remo", pasted.weeks.single().days.single().exercises.single().rawName)
        assertEquals(3, pasted.weeks.single().days.single().exercises.single().sets)
    }

    @Test
    fun `una tabla Markdown da una celda por columna y no lee la línea separadora`() {
        val text = """
            | Ejercicio | Series | Reps |
            |---|:--:|---|
            | Press banca | 4 | 8 |
            | **Remo** | 3 | 10 |
        """.trimIndent()

        val sheet = TextProgramReader.read(text, "tabla")

        assertEquals(
            listOf(
                listOf("Ejercicio", "Series", "Reps"),
                listOf("Press banca", "4", "8"),
                listOf("Remo", "3", "10"),
            ),
            sheet.rows,
        )
    }

    @Test
    fun `sin bloques lee el texto entero, sin almohadillas, viñetas ni énfasis`() {
        val text = """
            # Semana 1
            - **Día 1**
            * Sentadilla 4x6 / 120 kg
            1. Prensa 3x10
            ---
            Notas: descanso libre
        """.trimIndent()

        val sheet = TextProgramReader.read(text, "texto")

        assertEquals(
            listOf(
                listOf("Semana 1", ""),
                listOf("Día 1", ""),
                listOf("Sentadilla 4x6", "120 kg"),
                listOf("Prensa 3x10", ""),
                listOf("Notas: descanso libre", ""),
            ),
            sheet.rows,
        )
    }

    @Test
    fun `más de 2000 filas o más de 40 columnas se recortan con una nota`() {
        val tall = TextProgramReader.read((1..2_001).joinToString("\n") { "Línea $it" }, "alto")
        assertEquals(2_000, tall.rows.size)
        assertEquals(listOf(TrimNotes.ROWS), tall.notes)

        val wide = TextProgramReader.read((1..41).joinToString(" / ") { "c$it" }, "ancha")
        assertEquals(listOf(List(40) { "c${it + 1}" }), wide.rows)
        assertEquals(listOf(TrimNotes.COLUMNS), wide.notes)
    }
}

package com.example.kpkn.data.importer

import org.junit.Assert.assertEquals
import org.junit.Test

class TextProgramReaderTest {

    @Test
    fun `con bloques cercados de series por repeticiones lee solo esos bloques`() {
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
                listOf("Week 1", "", "", ""),
                listOf("Día 1 — Pierna", "", "", ""),
                listOf("Sentadilla", "4x6", "120kg", "120s"),
                listOf("Prensa", "3x10", "90s", ""),
            ),
            sheet.rows,
        )
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
    fun `más de 2000 filas o más de 40 columnas se rechazan con su mensaje`() {
        val tall = (1..2_001).joinToString("\n") { "Línea $it" }
        val wide = (1..41).joinToString(" / ") { "c$it" }

        assertEquals("La hoja «alto» tiene más de 2000 filas", rejectionOf { TextProgramReader.read(tall, "alto") })
        assertEquals("La hoja «ancha» tiene más de 40 columnas", rejectionOf { TextProgramReader.read(wide, "ancha") })
    }

    /** Mensaje del rechazo del lector, o null si no lo hubo. */
    private fun rejectionOf(block: () -> Unit): String? = try {
        block()
        null
    } catch (error: ImportFileException) {
        error.message
    }
}

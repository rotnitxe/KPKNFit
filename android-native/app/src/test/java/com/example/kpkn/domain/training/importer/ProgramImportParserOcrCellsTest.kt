package com.example.kpkn.domain.training.importer

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Celdas partidas en una tabla de OCR y días con número delante (H4e). Solo actúan en hojas de OCR (con `pageStarts`):
 * la misma hoja sin `pageStarts` da lo de siempre.
 */
class ProgramImportParserOcrCellsTest {

    /** Una línea de tabla como la lee el OCR: celdas separadas por « | ». */
    private fun cells(line: String): List<String> = line.split("|").map { it.trim() }

    private fun sheet(ocr: Boolean, lines: List<String>): ImportSheet = ImportSheet(
        name = "Hoja",
        rows = lines.map { cells(it) },
        pageStarts = if (ocr) listOf(0) else emptyList(),
    )

    private fun exercisesOf(draft: ImportedProgramDraft): List<ImportedExercise> =
        draft.weeks.flatMap { week -> week.days.flatMap { it.exercises } }

    private fun skippedMessages(draft: ImportedProgramDraft): List<String> =
        draft.warnings.filter { it.kind == ImportWarningKind.SKIPPED_ROW }.map { it.message }

    /** Dos días, cada uno con su tabla: cuatro nombres partidos (dos por día) y el «2 Día 2» que lee el OCR. */
    private val twoDays = listOf(
        "Día 1: Torso A",
        "EJERCICIO | SERIES | REPS | TEMPO |  | RPE | DESCANSO CARGA | NOTAS",
        "Curl de bíceps con |  |  |  |  |  |  |",
        " | 2 | 10 a 12 | 2-0-1-0 |  |  | 60 s |",
        "mancuernas |  |  |  |  |  |  |",
        "Press inclinado con |  |  |  |  |  |  |  |",
        " | 3 | 8 a 10 | 3-0-1-0 |  | 8 | 2 min |  |",
        "mancuernas |  |  |  |  |  |  |  |",
        "2 Día 2: Pierna A",
        "EJERCICIO | SERIES | REPS | TEMPO |  | RPE | DESCANSO CARGA | NOTAS",
        "Dominadas o jalón agarre |  |  |  |  |  |  |  |",
        " | 3 | 6 a 10 | 2-1-1-0 |  | 8 | 2 min |  |",
        "cerrado |  |  |  |  |  |  |  |",
        "Zancada andando con |  | 8 a 10 por |  |  |  |  |  |",
        " | 3 |  | 2-0-1-0 |  | 8 | 90 s |  |",
        "mancuernas |  | pierna |  |  |  |  |  |",
    )

    @Test
    fun `en una hoja de OCR, las celdas partidas se unen y el dia con numero delante se abre`() {
        val draft = ProgramImportParser.parseSheets(listOf(sheet(ocr = true, lines = twoDays)), title = "OCR")

        val (torso, pierna) = draft.weeks.single().days
        assertEquals(listOf("Día 1: Torso A", "Día 2: Pierna A"), listOf(torso.name, pierna.name))
        assertEquals(listOf("Curl de bíceps con mancuernas", "Press inclinado con mancuernas"), torso.exercises.map { it.rawName })
        assertEquals(listOf(2, 3), torso.exercises.map { it.sets })
        assertEquals(listOf(10, 8), torso.exercises.map { it.repsMin })
        assertEquals(listOf(12, 10), torso.exercises.map { it.repsMax })
        assertEquals(listOf(60, 120), torso.exercises.map { it.restSeconds })
        assertEquals(listOf(null, 8.0), torso.exercises.map { it.rpe })
        assertEquals(listOf("Dominadas o jalón agarre cerrado", "Zancada andando con mancuernas"), pierna.exercises.map { it.rawName })
        assertEquals(listOf(3, 3), pierna.exercises.map { it.sets })
        assertEquals(listOf(6, 8), pierna.exercises.map { it.repsMin })
        assertEquals(listOf(10, 10), pierna.exercises.map { it.repsMax })
        assertEquals(listOf(120, 90), pierna.exercises.map { it.restSeconds })
        assertEquals(listOf(8.0, 8.0), pierna.exercises.map { it.rpe })
        assertEquals(emptyList<String>(), draft.warnings.map { it.message })
    }

    @Test
    fun `las mismas filas en una hoja normal no se unen ni abren el dia, como antes`() {
        val draft = ProgramImportParser.parseSheets(listOf(sheet(ocr = false, lines = twoDays)), title = "Normal")

        assertEquals(emptyList<String>(), exercisesOf(draft).map { it.rawName })
        assertEquals(
            listOf(
                "Hoja «Hoja», fila 4: fila sin nombre de ejercicio; se omite",
                "Hoja «Hoja», fila 7: fila sin nombre de ejercicio; se omite",
                "Hoja «Hoja», fila 9: no se entiende «2 Día 2: Pierna A»",
                "Hoja «Hoja», fila 12: fila sin nombre de ejercicio; se omite",
                "Hoja «Hoja», fila 14: «Zancada andando con» sin series; se omite",
                "Hoja «Hoja», fila 15: fila sin nombre de ejercicio; se omite",
                "Hoja «Hoja», fila 16: «mancuernas» sin series; se omite",
            ),
            skippedMessages(draft),
        )
    }

    @Test
    fun `el trozo de abajo no se une si la fila que sigue es otra fila de datos huerfana`() {
        val draft = ProgramImportParser.parseSheets(
            listOf(
                sheet(
                    ocr = true,
                    lines = listOf(
                        "EJERCICIO | SERIES | REPS | TEMPO |  | RPE | DESCANSO CARGA | NOTAS",
                        "Curl de bíceps con |  |  |  |  |  |  |",
                        " | 2 | 10 a 12 | 2-0-1-0 |  |  | 60 s |",
                        "Press inclinado con |  |  |  |  |  |  |  |",
                        " | 3 | 8 a 10 | 3-0-1-0 |  | 8 | 2 min |  |",
                        "mancuernas |  |  |  |  |  |  |  |",
                    ),
                ),
            ),
            title = "Trozos",
        )

        // «Press inclinado con» es el trozo de arriba de la segunda fila de datos, no el de abajo de la primera.
        assertEquals(listOf("Curl de bíceps con", "Press inclinado con mancuernas"), exercisesOf(draft).map { it.rawName })
        assertEquals(emptyList<String>(), skippedMessages(draft))
    }

    @Test
    fun `una fila de datos huerfana sin trozo de arriba sigue avisando`() {
        val draft = ProgramImportParser.parseSheets(
            listOf(
                sheet(
                    ocr = true,
                    lines = listOf(
                        "EJERCICIO | SERIES | REPS | TEMPO |  | RPE | DESCANSO CARGA | NOTAS",
                        " | 3 | 10 | 2-0-1-0 |  |  | 60 s |",
                    ),
                ),
            ),
            title = "Sin trozo",
        )

        assertEquals(emptyList<String>(), exercisesOf(draft).map { it.rawName })
        assertEquals(listOf("Hoja «Hoja», fila 2: fila sin nombre de ejercicio; se omite"), skippedMessages(draft))
    }

    @Test
    fun `un trozo de arriba con prosa o con cifras no se une`() {
        val draft = ProgramImportParser.parseSheets(
            listOf(
                sheet(
                    ocr = true,
                    lines = listOf(
                        "EJERCICIO | SERIES | REPS | TEMPO |  | RPE | DESCANSO CARGA | NOTAS",
                        "Haz un calentamiento suave antes de empezar la sesión de hoy |  |  |",
                        " | 3 | 10 | 2-0-1-0 |  |  | 60 s |",
                        "Curl 2 brazos |  |  |  |  |  |  |",
                        " | 2 | 12 | 2-0-1-0 |  |  | 60 s |",
                    ),
                ),
            ),
            title = "Trozos inválidos",
        )

        assertEquals(emptyList<String>(), exercisesOf(draft).map { it.rawName })
        assertEquals(
            listOf(
                "Hoja «Hoja», fila 3: fila sin nombre de ejercicio; se omite",
                "Hoja «Hoja», fila 4: no se entiende «Curl 2 brazos»",
                "Hoja «Hoja», fila 5: fila sin nombre de ejercicio; se omite",
            ),
            skippedMessages(draft),
        )
    }
}

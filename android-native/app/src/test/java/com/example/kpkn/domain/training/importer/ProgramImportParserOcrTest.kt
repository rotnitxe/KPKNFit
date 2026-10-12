package com.example.kpkn.domain.training.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Filas de OCR (H4d): nombre y prescripción en líneas distintas, días repetidos, pirámides, notación larga y por lado. */
class ProgramImportParserOcrTest {

    private fun exercisesOf(draft: ImportedProgramDraft): List<ImportedExercise> =
        draft.weeks.flatMap { week -> week.days.flatMap { it.exercises } }

    private fun skippedMessages(draft: ImportedProgramDraft): List<String> =
        draft.warnings.filter { it.kind == ImportWarningKind.SKIPPED_ROW }.map { it.message }

    @Test
    fun `el nombre de la linea de arriba da nombre a la prescripcion suelta`() {
        val draft = ProgramImportParser.parseText("Sentadilla\n4x8\nPrensa\n3x10", title = "Pegado")

        val exercises = exercisesOf(draft)
        assertEquals(listOf("Sentadilla", "Prensa"), exercises.map { it.rawName })
        assertEquals(listOf(4, 3), exercises.map { it.sets })
        assertEquals(listOf(8, 10), exercises.map { it.repsMin })
        assertEquals(emptyList<String>(), skippedMessages(draft))
    }

    @Test
    fun `el nombre se busca hacia atras saltando la etiqueta de dia`() {
        val draft = ProgramImportParser.parseText("Press banca\nDía 1\n4x8", title = "Hacia atrás")

        assertEquals(listOf("Press banca"), exercisesOf(draft).map { it.rawName })
        assertEquals(listOf("Día 1"), draft.weeks.single().days.map { it.name })
    }

    @Test
    fun `no busca el nombre mas de tres filas atras`() {
        val draft = ProgramImportParser.parseText(
            "Press banca\n" +
                "Esta es una frase de instrucciones muy larga para la hoja.\n" +
                "Otra frase de instrucciones para leer en este texto de la hoja\n" +
                "Una tercera línea de prosa en la hoja de texto y ya basta\n" +
                "4x8",
            title = "Lejos",
        )

        assertEquals(emptyList<String>(), exercisesOf(draft).map { it.rawName })
        assertEquals(listOf("Línea 5: falta el nombre del ejercicio («4x8»)"), skippedMessages(draft))
    }

    @Test
    fun `en una hoja de OCR, la etiqueta de dia igual en la cabecera de pagina sigue el dia abierto`() {
        val rows = listOf(listOf("Día 1"), listOf("Sentadilla 4x8"), listOf("DÍA 1"), listOf("Prensa 3x10"))

        val draft = ProgramImportParser.parseSheets(
            listOf(ImportSheet(name = "Hoja", rows = rows, pageStarts = listOf(0))),
            title = "OCR",
        )

        assertEquals(listOf("Día 1"), draft.weeks.single().days.map { it.name })
        assertEquals(listOf("Sentadilla", "Prensa"), draft.weeks.single().days.single().exercises.map { it.rawName })
    }

    @Test
    fun `en una hoja de OCR, una etiqueta de dia repetida fuera de la cabecera de pagina abre otro dia`() {
        val rows = listOf(listOf("Día 1"), listOf("Sentadilla 4x8"), listOf("Press 3x8"), listOf("DÍA 1"), listOf("Prensa 3x10"))

        val draft = ProgramImportParser.parseSheets(
            listOf(ImportSheet(name = "Hoja", rows = rows, pageStarts = listOf(0))),
            title = "OCR",
        )

        assertEquals(listOf("Día 1", "DÍA 1"), draft.weeks.single().days.map { it.name })
    }

    @Test
    fun `fuera de una hoja de OCR, una etiqueta de dia repetida abre otro dia, como antes`() {
        val draft = ProgramImportParser.parseText("Día 1\nSentadilla 4x8\nDÍA 1\nPrensa 3x10", title = "Texto")

        assertEquals(listOf("Día 1", "DÍA 1"), draft.weeks.single().days.map { it.name })
    }

    @Test
    fun `un nombre suelto sin cifras es linea de texto si la hoja tiene ejercicios`() {
        val draft = ProgramImportParser.parseText("Día 1\nHacer muchas vueltas de cada una\nSentadilla 4x6", title = "Lista")

        assertEquals(listOf("Sentadilla"), exercisesOf(draft).map { it.rawName })
        assertEquals(listOf(ImportWarningKind.SHEET_NOTE), draft.warnings.map { it.kind })
        assertEquals("Texto pegado: se omitió 1 línea de texto.", draft.warnings.single().message)
    }

    @Test
    fun `un nombre suelto con cifras da su aviso SKIPPED como antes`() {
        val draft = ProgramImportParser.parseText("Día 1\nPress 4/8\nSentadilla 4x6", title = "Cifras")

        assertEquals(listOf("Sentadilla"), exercisesOf(draft).map { it.rawName })
        assertEquals(listOf("Línea 2: no se entiende «Press 4/8»"), skippedMessages(draft))
    }

    @Test
    fun `sin ningun ejercicio, un nombre suelto con cifras avisa como antes`() {
        val draft = ProgramImportParser.parseText("Hacer 3 vueltas de cada una", title = "Vacía")

        assertEquals(listOf("Línea 1: no se entiende «Hacer 3 vueltas de cada una»"), skippedMessages(draft))
    }

    @Test
    fun `una pirámide da el rango de sus repeticiones y su nota`() {
        val draft = ProgramImportParser.parseText(
            "Sentadilla 4x 15-12-10-8\nPrensa 15-12-10-8\nRemo 12/10/8/6",
            title = "Pirámide",
        )

        val exercises = exercisesOf(draft)
        assertEquals(listOf(4, 4, 4), exercises.map { it.sets })
        assertEquals(listOf(8, 8, 6), exercises.map { it.repsMin })
        assertEquals(listOf(15, 15, 12), exercises.map { it.repsMax })
        assertEquals(
            listOf("Pirámide: 15-12-10-8.", "Pirámide: 15-12-10-8.", "Pirámide: 12-10-8-6."),
            exercises.map { it.notes },
        )
    }

    @Test
    fun `una celda de repeticiones en piramide da el rango y la nota`() {
        val sheet = ImportSheet(
            name = "Día 1",
            rows = listOf(
                listOf("Ejercicio", "Series", "Reps"),
                listOf("Sentadilla", "4", "12-10-8-8"),
                listOf("Prensa", "3", "8/leg"),
            ),
        )

        val exercises = exercisesOf(ProgramImportParser.parseSheets(listOf(sheet), title = "Tabla"))
        assertEquals(listOf(8, 8), exercises.map { it.repsMin })
        assertEquals(listOf(12, 8), exercises.map { it.repsMax })
        assertEquals(listOf("Pirámide: 12-10-8-8.", "Por lado."), exercises.map { it.notes })
    }

    @Test
    fun `dos cifras no son una piramide sino un rango`() {
        val draft = ProgramImportParser.parseText("Remo 2x10-12", title = "Rango")

        val exercise = exercisesOf(draft).single()
        assertEquals(2, exercise.sets)
        assertEquals(10, exercise.repsMin)
        assertEquals(12, exercise.repsMax)
        assertNull(exercise.notes)
    }

    @Test
    fun `la notacion larga en ingles y en espanol da series y repeticiones`() {
        val draft = ProgramImportParser.parseText(
            "Sentadilla 2 sets x 10 reps\n" +
                "Prensa 3 sets of 8\n" +
                "Curl 4 sets of 8-12 reps\n" +
                "Remo 3 series de 10 repeticiones\n" +
                "Press 3 series x 12\n" +
                "Peso muerto 4 x 10 reps\n" +
                "Bent-Over Rows (2 sets x 10 reps)",
            title = "Largas",
        )

        val exercises = exercisesOf(draft)
        assertEquals(listOf("Sentadilla", "Prensa", "Curl", "Remo", "Press", "Peso muerto", "Bent-Over Rows"), exercises.map { it.rawName })
        assertEquals(listOf(2, 3, 4, 3, 3, 4, 2), exercises.map { it.sets })
        assertEquals(listOf(10, 8, 8, 10, 12, 10, 10), exercises.map { it.repsMin })
        assertEquals(listOf(10, 8, 12, 10, 12, 10, 10), exercises.map { it.repsMax })
        assertNull(exercises.last().notes)
    }

    @Test
    fun `las repeticiones por lado dan su nota`() {
        val draft = ProgramImportParser.parseText(
            "Zancada 3 x 8/leg\nLunges 3 x 10 each side\nHip thrust 3 x 12 por pierna\nCurl 3 x 10 c/l\nPress 3 x 10 por lado",
            title = "Por lado",
        )

        val exercises = exercisesOf(draft)
        assertEquals(listOf(8, 10, 12, 10, 10), exercises.map { it.repsMin })
        assertEquals(List(5) { "Por lado." }, exercises.map { it.notes })
    }

    @Test
    fun `una fecha no es una piramide ni da ejercicios`() {
        val draft = ProgramImportParser.parseText("Deadlift 100 Updated: 06/03/15\nFecha 6/3/15", title = "Fecha")

        assertEquals(emptyList<String>(), exercisesOf(draft).map { it.rawName })
    }

    @Test
    fun `las piramides sin series van en un solo sentido y sin ceros delante`() {
        val draft = ProgramImportParser.parseText("Sentadilla 15-12-10-8\nCurl 8-10-12", title = "Sentido")

        val exercises = exercisesOf(draft)
        assertEquals(listOf("Sentadilla", "Curl"), exercises.map { it.rawName })
        assertEquals(listOf(4, 3), exercises.map { it.sets })
        assertEquals(listOf(8, 8), exercises.map { it.repsMin })
        assertEquals(listOf(15, 12), exercises.map { it.repsMax })
        assertEquals(listOf("Pirámide: 15-12-10-8.", "Pirámide: 8-10-12."), exercises.map { it.notes })
    }

    @Test
    fun `con series, una piramide en onda de seis series sigue siendo piramide`() {
        val draft = ProgramImportParser.parseText("Dominadas 6x 3-2-1-3-2-1", title = "Onda")

        val exercise = exercisesOf(draft).single()
        assertEquals(6, exercise.sets)
        assertEquals(1, exercise.repsMin)
        assertEquals(3, exercise.repsMax)
        assertEquals("Pirámide: 3-2-1-3-2-1.", exercise.notes)
    }

    @Test
    fun `en una hoja de OCR la prosa con cifras cuenta como linea de texto y no da avisos`() {
        val rows = listOf(
            listOf("Cómo leer esta tabla: RPE 8 significa que al final de la serie te quedan unas 2 repeticiones."),
            listOf("Regla de progresión: cuando el cliente llega al máximo del rango en todas las series, sube la carga."),
            listOf("• Cómo sumar las series de calentamiento: no cuentan para la progresión de cada ejercicio."),
            listOf("Sentadilla 4x8"),
        )

        val draft = ProgramImportParser.parseSheets(
            listOf(ImportSheet(name = "Hoja", rows = rows, pageStarts = listOf(0))),
            title = "Prosa OCR",
        )

        assertEquals(listOf("Sentadilla"), exercisesOf(draft).map { it.rawName })
        assertEquals(emptyList<String>(), skippedMessages(draft))
        assertTrue(draft.warnings.any { it.message.contains("3 líneas de texto") })
    }

    @Test
    fun `la misma prosa con cifras en una hoja normal sigue avisando como antes`() {
        val rows = listOf(
            listOf("Cómo leer esta tabla: RPE 8 significa que al final de la serie te quedan unas 2 repeticiones."),
            listOf("Regla de progresión: cuando el cliente llega al máximo del rango en todas las series, sube la carga."),
            listOf("Sentadilla 4x8"),
        )

        val draft = ProgramImportParser.parseSheets(listOf(ImportSheet(name = "Hoja", rows = rows)), title = "Prosa normal")

        assertTrue("avisos: ${draft.warnings.map { it.message }}", skippedMessages(draft).isNotEmpty())
    }
}

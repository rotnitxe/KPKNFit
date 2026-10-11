package com.example.kpkn.domain.training.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ruido del importador: hojas de instrucciones y de cálculo, prosa, días con etiqueta, calentamientos, etiquetas y
 * notación de repeticiones. Los ejemplos están escritos a mano, con nombres inventados.
 */
class ProgramImportParserNoiseTest {

    @Test
    fun `las hojas de instrucciones y de calculo se omiten por su nombre, y las demas no`() {
        val sheets = listOf(
            ImportSheet("Read Me", listOf(listOf("Plan de prueba con nombres inventados"))),
            ImportSheet("Setup", listOf(listOf("Paso 1: elige la unidad"))),
            ImportSheet("Calculators", listOf(listOf("Weight lifted", "100"))),
            ImportSheet("1RM Input", listOf(listOf("Peso", "100"))),
            ImportSheet("Summary", listOf(listOf("Total", "10"))),
            ImportSheet("Volume Calcs", listOf(listOf("Series", "4"))),
            ImportSheet("Notas del día", listOf(listOf("Sentadilla 4x6"))),
        )

        val draft = ProgramImportParser.parseSheets(sheets, title = "Omitidas")

        assertEquals(
            listOf(
                "Se omitió la hoja «Read Me»: parece de instrucciones o de cálculo.",
                "Se omitió la hoja «Setup»: parece de instrucciones o de cálculo.",
                "Se omitió la hoja «Calculators»: parece de instrucciones o de cálculo.",
                "Se omitió la hoja «1RM Input»: parece de instrucciones o de cálculo.",
                "Se omitió la hoja «Summary»: parece de instrucciones o de cálculo.",
                "Se omitió la hoja «Volume Calcs»: parece de instrucciones o de cálculo.",
            ),
            draft.warningMessages,
        )
        assertEquals(
            listOf("Hoja «Read Me»", "Hoja «Setup»", "Hoja «Calculators»", "Hoja «1RM Input»", "Hoja «Summary»", "Hoja «Volume Calcs»"),
            draft.warnings.map { it.location },
        )
        assertEquals(List(6) { ImportWarningKind.SHEET_NOTE }, draft.warnings.map { it.kind })
        assertEquals(listOf("Notas del día"), draft.weeks.single().days.map { it.name })
        assertEquals(listOf("Sentadilla"), draft.weeks.single().days.single().exercises.map { it.rawName })
    }

    @Test
    fun `la prosa no es un dia ni un aviso suelto, se cuenta por hoja`() {
        val sheet = ImportSheet(
            name = "Guía de uso",
            rows = listOf(
                listOf("Esta hoja reparte el trabajo en dos bloques y cada bloque incluye su propio calentamiento."),
                listOf("Consulta el enlace https://example.com/plan antes de empezar."),
                listOf("Descansa dos días antes de la prueba."),
                listOf("Día 1 — Pierna"),
                listOf("Sentadilla 4x6"),
            ),
        )

        val draft = ProgramImportParser.parseSheets(listOf(sheet), title = "Prosa")

        assertEquals(listOf("Día 1 — Pierna"), draft.weeks.single().days.map { it.name })
        assertEquals(listOf("Sentadilla"), draft.weeks.single().days.single().exercises.map { it.rawName })
        assertEquals(listOf("Hoja «Guía de uso»: se omitieron 3 líneas de texto."), draft.warningMessages)
        assertEquals(ImportWarningKind.SHEET_NOTE, draft.warnings.single().kind)
        assertEquals("Hoja «Guía de uso»", draft.warnings.single().location)
    }

    @Test
    fun `en texto pegado la prosa se cuenta como texto pegado, con singular para una sola linea`() {
        val draft = ProgramImportParser.parseText(
            "Día 1\nSentadilla 4x6\nEsta es una nota larga que no pertenece a ningún ejercicio concreto.",
            title = "Pegado",
        )

        assertEquals(listOf("Sentadilla"), draft.weeks.single().days.single().exercises.map { it.rawName })
        assertEquals(listOf("Texto pegado: se omitió 1 línea de texto."), draft.warningMessages)
        assertEquals("Texto pegado", draft.warnings.single().location)
        assertEquals(ImportWarningKind.SHEET_NOTE, draft.warnings.single().kind)
    }

    @Test
    fun `las frases de instrucciones y las palabras cortas sin ejercicios no son dias con aviso`() {
        val sheet = ImportSheet(
            name = "Pistas",
            rows = listOf(
                listOf("Setup"),
                listOf("Descent"),
                listOf("Set Priority (yellow) to Yes for at most one or two muscles."),
                listOf("Lift"),
            ),
        )

        val draft = ProgramImportParser.parseSheets(listOf(sheet), title = "Pistas")

        assertTrue(draft.weeks.isEmpty())
        // Tres palabras cortas sin ejercicios salen en silencio, y la frase cuenta como texto: 4 líneas.
        assertEquals(
            listOf(ImportWarningKind.SHEET_NOTE, ImportWarningKind.NOTHING_FOUND),
            draft.warnings.map { it.kind },
        )
        assertEquals("Hoja «Pistas»: se omitieron 4 líneas de texto.", draft.warnings[0].message)
    }

    @Test
    fun `las etiquetas de sesion, de dia de la semana y de foco son dias`() {
        val text = """
            Session 1
            Sentadilla 4x6
            Workout B
            Press banca 3x8
            Monday – Pull
            Remo 3x10
            Lunes: Pierna
            Prensa 3x12
            Entreno A/B
            Curl 3x10
            Pierna y glúteos
            Puente 3x12
            Push & Butt & Calves
            Fondos 3x10
        """.trimIndent()

        val draft = ProgramImportParser.parseText(text, title = "Etiquetas")

        assertEquals(
            listOf("Session 1", "Workout B", "Monday – Pull", "Lunes: Pierna", "Entreno A/B", "Pierna y glúteos", "Push & Butt & Calves"),
            draft.weeks.single().days.map { it.name },
        )
        assertEquals(emptyList<String>(), draft.warningMessages)
    }

    @Test
    fun `calentamientos y etiquetas no son ejercicios, se cuentan por hoja y los errores reales siguen avisando`() {
        val sheet = ImportSheet(
            name = "Tabla base",
            rows = listOf(
                listOf("Ejercicio", "Series", "Reps", "Peso"),
                listOf("Warm-up 12 reps @ 50%", "1", "12", "0"),
                listOf("Warm-up 10 reps @ 50%", "1", "10", "0"),
                listOf("Ramp up", "1", "5", ""),
                listOf("Set", "3", "10", ""),
                listOf("Biceps SxR", "3", "10", ""),
                listOf("11 2", "2", "8", ""),
                listOf("5 2RM", "2", "5", ""),
                listOf("Block 1 Meso A 1 10RM", "3", "8", ""),
                listOf("Abs Circuits", "3", "", ""),
                listOf("Sentadilla", "4", "6", "100"),
            ),
        )

        val draft = ProgramImportParser.parseSheets(listOf(sheet), title = "Tabla")

        assertEquals(listOf("Sentadilla"), draft.weeks.single().days.single().exercises.map { it.rawName })
        assertEquals(
            listOf(
                "Hoja «Tabla base», fila 10: «Abs Circuits» sin repeticiones; se omite",
                "Hoja «Tabla base»: se omitieron 3 series de calentamiento.",
                "Hoja «Tabla base»: se omitieron 5 filas de cálculo o de etiquetas.",
            ),
            draft.warningMessages,
        )
    }

    @Test
    fun `la notacion de repeticiones 1x5+, 3x8-12 y 3 x 10 sale de la celda de repeticiones`() {
        val sheet = ImportSheet(
            name = "Día demo",
            rows = listOf(
                listOf("Exercise", "Sets", "Reps / Scheme", "Weight"),
                listOf("Peso muerto demo", "1", "1x5+", "135"),
                listOf("Remo demo", "3", "3x8-12", ""),
                listOf("Press demo", "", "5x5", "60"),
                listOf("Curl demo", "", "3 x 10", ""),
                listOf("Dominadas demo", "4+1", "4x5, 1x5+", ""),
                listOf("Extensión demo", "3", "4-6 / 8-10", ""),
            ),
        )

        val exercises = ProgramImportParser.parseSheets(listOf(sheet), title = "Notación").weeks.single().days.single().exercises

        // La columna de series manda si tiene número («1», «3», «4+1»); si está vacía, la da la notación.
        assertEquals(listOf(1, 3, 5, 3, 4, 3), exercises.map { it.sets })
        assertEquals(listOf(5, 8, 5, 10, 5, 4), exercises.map { it.repsMin })
        assertEquals(listOf(5, 12, 5, 10, 5, 6), exercises.map { it.repsMax })
        assertEquals(
            listOf(
                "AMRAP: última serie, 5 o más repeticiones.",
                null,
                null,
                null,
                "AMRAP: última serie, 5 o más repeticiones.",
                "8-10",
            ),
            exercises.map { it.notes },
        )
    }

    @Test
    fun `AMRAP con + en el texto y en la celda, y el rango 10-15+ con la nota del 15`() {
        val draft = ProgramImportParser.parseText("Día 1\nPress banca 3x8+\nJalón 3x10-15+", title = "AMRAP")

        val exercises = draft.weeks.single().days.single().exercises
        assertEquals(listOf(8, 10), exercises.map { it.repsMin })
        assertEquals(listOf(8, 15), exercises.map { it.repsMax })
        assertEquals(
            listOf("AMRAP: última serie, 8 o más repeticiones.", "AMRAP: última serie, 15 o más repeticiones."),
            exercises.map { it.notes },
        )
        assertEquals(emptyList<String>(), draft.warningMessages)
    }

    @Test
    fun `en una tabla, las repeticiones AMRAP sin numero se omiten con su aviso`() {
        val sheet = ImportSheet(
            name = "Día 2",
            rows = listOf(
                listOf("Ejercicio", "Series", "Reps"),
                listOf("Remo", "3", "8"),
                listOf("Dominadas", "3", "AMRAP"),
            ),
        )

        val draft = ProgramImportParser.parseSheets(listOf(sheet), title = "Tabla AMRAP")

        assertEquals(listOf("Remo"), draft.weeks.single().days.single().exercises.map { it.rawName })
        assertEquals(
            listOf("Hoja «Día 2», fila 3: «Dominadas»: AMRAP sin número de repeticiones; se omite"),
            draft.warningMessages,
        )
        assertEquals(ImportWarningKind.SKIPPED_ROW, draft.warnings.single().kind)
        assertEquals("w1-d1", draft.warnings.single().dayId)
    }

    @Test
    fun `la notacion antes del nombre, 5x5 Squat da el nombre Squat, y lo de detras de la notacion tambien`() {
        val draft = ProgramImportParser.parseText(
            "Día 1\n5x5 Squat\n2x12-15 external cable rotations (light weight)",
            title = "Antes",
        )

        val exercises = draft.weeks.single().days.single().exercises
        assertEquals(listOf("Squat", "external cable rotations (light weight)"), exercises.map { it.rawName })
        assertEquals(listOf(5, 2), exercises.map { it.sets })
        assertEquals(listOf(5, 12), exercises.map { it.repsMin })
        assertEquals(listOf(5, 15), exercises.map { it.repsMax })
        assertNull(exercises[0].notes)
        assertEquals(emptyList<String>(), draft.warningMessages)
    }

    @Test
    fun `una hoja sin encabezado de dia no hereda el dia abierto de la hoja anterior`() {
        val sheets = listOf(
            ImportSheet("Día 1", listOf(listOf("Sentadilla 4x6"))),
            ImportSheet("Día 2", listOf(listOf("Press banca 3x8"))),
        )

        val draft = ProgramImportParser.parseSheets(sheets, title = "Dos")

        assertEquals(listOf("Día 1", "Día 2"), draft.weeks.single().days.map { it.name })
        assertEquals(listOf(1, 1), draft.weeks.single().days.map { it.exercises.size })
    }

    @Test
    fun `un programa tipo PPL con hojas de instrucciones y de dia da dos dias completos y solo notas de hoja`() {
        val sheets = listOf(
            ImportSheet(
                name = "Read Me",
                rows = listOf(
                    listOf("Plan de ejemplo con nombres inventados"),
                    listOf("Lee estas instrucciones antes de empezar: anota tus cargas en las celdas verdes."),
                    listOf("Fuente: https://example.com/plan"),
                ),
            ),
            ImportSheet(
                name = "Setup",
                rows = listOf(listOf("Paso 1: elige la unidad"), listOf("Unidad: kg")),
            ),
            ImportSheet(
                name = "Mon Pull",
                rows = listOf(
                    listOf("Monday – Pull"),
                    listOf("Exercise", "Sets", "Reps / Scheme", "Weight"),
                    listOf("Peso muerto demo", "1", "1x5+", "135"),
                    listOf("Remo demo", "3", "3x8-12", ""),
                    listOf("Jalón demo", "3", "3x8-12", ""),
                ),
            ),
            ImportSheet(
                name = "Tue Push",
                rows = listOf(
                    listOf("Tuesday – Push"),
                    listOf("Exercise", "Sets", "Reps / Scheme", "Weight"),
                    listOf("Press demo", "4+1", "4x5, 1x5+", "95"),
                    listOf("Fondos demo", "3", "3x8-12", ""),
                ),
            ),
        )

        val draft = ProgramImportParser.parseSheets(sheets, title = "PPL de ejemplo")

        val days = draft.weeks.single().days
        assertEquals(listOf("Monday – Pull", "Tuesday – Push"), days.map { it.name })
        assertEquals(listOf("w1-d1", "w1-d2"), days.map { it.id })
        assertEquals(listOf(3, 2), days.map { it.exercises.size })
        assertEquals(listOf("w1-d1-e1", "w1-d1-e2", "w1-d1-e3"), days[0].exercises.map { it.id })
        assertEquals(
            listOf("Peso muerto demo", "Remo demo", "Jalón demo", "Press demo", "Fondos demo"),
            days.flatMap { day -> day.exercises.map { it.rawName } },
        )
        assertEquals("AMRAP: última serie, 5 o más repeticiones.", days[0].exercises[0].notes)
        assertEquals("AMRAP: última serie, 5 o más repeticiones.", days[1].exercises[0].notes)
        assertEquals(listOf(ImportWarningKind.SHEET_NOTE, ImportWarningKind.SHEET_NOTE), draft.warnings.map { it.kind })
        assertEquals(
            listOf(
                "Se omitió la hoja «Read Me»: parece de instrucciones o de cálculo.",
                "Se omitió la hoja «Setup»: parece de instrucciones o de cálculo.",
            ),
            draft.warningMessages,
        )
        assertTrue(draft.warnings.all { it.dayId == null && it.exerciseId == null && it.weekIndex == null })
        assertUniqueIds(draft)
    }

    @Test
    fun `una columna SxR de notacion se lee como repeticiones, con series y AMRAP`() {
        val sheet = ImportSheet(
            name = "Week 1",
            rows = listOf(
                listOf("Day 1"),
                listOf("Exercise", "Lbs", "SxR", "Set 1", "Set 2"),
                listOf("Squat", "100", "5x3+", "", ""),
                listOf("Bench", "80", "3x10", "", ""),
            ),
        )

        val draft = ProgramImportParser.parseSheets(listOf(sheet), title = "SxR")

        val exercises = draft.weeks.single().days.single().exercises
        assertEquals(listOf("Squat", "Bench"), exercises.map { it.rawName })
        assertEquals(listOf(5, 3), exercises.map { it.sets })
        assertEquals(listOf(3, 10), exercises.map { it.repsMin })
        assertEquals("AMRAP: última serie, 3 o más repeticiones.", exercises[0].notes)
        assertEquals(emptyList<String>(), draft.warningMessages)
    }

    @Test
    fun `un dia de la semana seguido de cifras no es etiqueta de dia, es una fila que no se entiende`() {
        val draft = ProgramImportParser.parseText("Día 1\nSentadilla 4x6\nMiércoles 75 90 90 0 7 5 0.75", title = "Datos")

        assertEquals(listOf("Sentadilla"), draft.weeks.single().days.single().exercises.map { it.rawName })
        assertEquals(listOf("Línea 3: no se entiende «Miércoles 75 90 90 0 7 5 0.75»"), draft.warningMessages)
        assertEquals(ImportWarningKind.SKIPPED_ROW, draft.warnings.single().kind)
    }

    /** Invariante: ningún día ni ejercicio se queda sin id y todos los ids del borrador son distintos entre sí. */
    private fun assertUniqueIds(draft: ImportedProgramDraft) {
        val ids = draft.weeks.flatMap { week -> week.days.flatMap { day -> listOf(day.id) + day.exercises.map { it.id } } }
        assertTrue("ids vacíos en $ids", ids.none { it.isEmpty() })
        assertEquals("ids repetidos en $ids", ids.size, ids.toSet().size)
    }
}

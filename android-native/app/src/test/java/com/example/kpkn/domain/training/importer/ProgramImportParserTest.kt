package com.example.kpkn.domain.training.importer

import com.example.kpkn.data.importer.CsvReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramImportParserTest {

    @Test
    fun `una hoja por dia con cabecera en espanol y valores`() {
        val sheet = ImportSheet(
            name = "Día 1",
            rows = listOf(
                listOf("Ejercicio", "Series", "Reps", "Peso (kg)", "RPE", "Descanso (s)", "Notas"),
                listOf("Sentadilla", "4", "6-8", "120", "8", "180", "Profundidad completa"),
                listOf("Peso muerto rumano", "3", "10", "", "", "90", ""),
            ),
        )

        val draft = ProgramImportParser.parseSheets(listOf(sheet), title = "Mi plan")
        assertUniqueIds(draft)

        assertEquals("Mi plan", draft.title)
        assertEquals(emptyList<String>(), draft.warningMessages)
        val day = draft.weeks.single().days.single()
        assertEquals("w1-d1", day.id)
        assertEquals("Día 1", day.name)
        val squat = day.exercises[0]
        assertEquals("w1-d1-e1", squat.id)
        assertEquals("Sentadilla", squat.rawName)
        assertEquals("Sentadilla · 4 · 6-8 · 120 · 8 · 180 · Profundidad completa", squat.sourceText)
        assertEquals(4, squat.sets)
        assertEquals(6, squat.repsMin)
        assertEquals(8, squat.repsMax)
        assertNear(120.0, squat.loadKg)
        assertNear(8.0, squat.rpe)
        assertEquals(180, squat.restSeconds)
        assertEquals("Profundidad completa", squat.notes)
        val deadlift = day.exercises[1]
        assertEquals("w1-d1-e2", deadlift.id)
        assertEquals("Peso muerto rumano", deadlift.rawName)
        assertEquals("Peso muerto rumano · 3 · 10 · 90", deadlift.sourceText)
        assertEquals(3, deadlift.sets)
        assertEquals(10, deadlift.repsMin)
        assertEquals(10, deadlift.repsMax)
        assertNull(deadlift.loadKg)
        assertEquals(90, deadlift.restSeconds)
    }

    @Test
    fun `una hoja con semanas y dias en filas de encabezado`() {
        val sheet = ImportSheet(
            name = "Plan",
            rows = listOf(
                listOf("Semana 1"),
                listOf("Día 1 — Pierna"),
                listOf("Ejercicio", "Series", "Reps"),
                listOf("Sentadilla", "4", "6"),
                listOf(""),
                listOf("Día 2 — Empuje"),
                listOf("Press banca", "4", "8-10"),
                listOf("Semana 2"),
                listOf("Día 1 — Pierna"),
                listOf("Sentadilla", "3", "5"),
            ),
        )

        val draft = ProgramImportParser.parseSheets(listOf(sheet), title = "")
        assertUniqueIds(draft)

        assertEquals(listOf(1, 2), draft.weeks.map { it.index })
        assertEquals(listOf("Día 1 — Pierna", "Día 2 — Empuje"), draft.weeks[0].days.map { it.name })
        assertEquals(listOf("Sentadilla"), draft.weeks[0].days[0].exercises.map { it.rawName })
        assertEquals(listOf("Press banca"), draft.weeks[0].days[1].exercises.map { it.rawName })
        assertEquals(listOf(3), draft.weeks[1].days[0].exercises.map { it.sets })
        assertEquals(emptyList<String>(), draft.warningMessages)
        assertEquals(listOf("w1-d1", "w1-d2"), draft.weeks[0].days.map { it.id })
        assertEquals(listOf("w2-d1"), draft.weeks[1].days.map { it.id })
        assertEquals(listOf("w1-d1-e1"), draft.weeks[0].days[0].exercises.map { it.id })
        assertEquals(listOf("w2-d1-e1"), draft.weeks[1].days[0].exercises.map { it.id })
    }

    @Test
    fun `dos hojas que dicen Semana 1 comparten semana y los ids no chocan`() {
        val first = ImportSheet(
            name = "Semana 1",
            rows = listOf(listOf("Día 1"), listOf("Ejercicio", "Series", "Reps"), listOf("Sentadilla", "4", "6")),
        )
        val second = ImportSheet(
            name = "Semana 1",
            rows = listOf(listOf("Día 1"), listOf("Ejercicio", "Series", "Reps"), listOf("Press banca", "3", "8")),
        )

        val draft = ProgramImportParser.parseSheets(listOf(first, second), title = "Dos hojas")
        assertUniqueIds(draft)

        assertEquals(listOf(1), draft.weeks.map { it.index })
        assertEquals(listOf("Día 1", "Día 1"), draft.weeks.single().days.map { it.name })
        assertEquals(listOf("w1-d1", "w1-d2"), draft.weeks.single().days.map { it.id })
        assertEquals(listOf("w1-d1-e1"), draft.weeks.single().days[0].exercises.map { it.id })
        assertEquals(listOf("w1-d2-e1"), draft.weeks.single().days[1].exercises.map { it.id })
        assertEquals(emptyList<String>(), draft.warningMessages)
    }

    @Test
    fun `texto pegado con dos secciones Semana 1 da ids unicos`() {
        val draft = ProgramImportParser.parseText("Semana 1\nDía 1\nSentadilla 4x6\nSemana 1\nDía 1\nPress banca 3x8\n", title = "Secciones")
        assertUniqueIds(draft)

        assertEquals(listOf(1), draft.weeks.map { it.index })
        assertEquals(listOf("Día 1", "Día 1"), draft.weeks.single().days.map { it.name })
        assertEquals(listOf("w1-d1", "w1-d2"), draft.weeks.single().days.map { it.id })
        assertEquals(listOf("Sentadilla"), draft.weeks.single().days[0].exercises.map { it.rawName })
        assertEquals(listOf("Press banca"), draft.weeks.single().days[1].exercises.map { it.rawName })
        assertEquals(emptyList<String>(), draft.warningMessages)
    }

    @Test
    fun `texto pegado con dias, notacion compacta y encabezado Lunes`() {
        val draft = ProgramImportParser.parseText(readResource("import/texto_pegado.txt"), title = "Pegado")
        assertUniqueIds(draft)

        assertEquals(emptyList<String>(), draft.warningMessages)
        val days = draft.weeks.single().days
        assertEquals(listOf("Día 1 — Pierna", "Lunes"), days.map { it.name })
        assertEquals(listOf("w1-d1", "w1-d2"), days.map { it.id })
        val pierna = days[0].exercises
        assertEquals(listOf("Sentadilla", "Peso muerto rumano"), pierna.map { it.rawName })
        assertEquals(listOf("w1-d1-e1", "w1-d1-e2"), pierna.map { it.id })
        assertEquals("Sentadilla 4x6 @120kg", pierna[0].sourceText)
        assertEquals(4, pierna[0].sets)
        assertEquals(6, pierna[0].repsMin)
        assertNear(120.0, pierna[0].loadKg)
        assertNear(8.0, pierna[1].rpe)
        val lunes = days[1].exercises
        assertEquals(listOf("Press banca", "Dominadas"), lunes.map { it.rawName })
        assertEquals(listOf("w1-d2-e1", "w1-d2-e2"), lunes.map { it.id })
        assertEquals(10, lunes[0].repsMin)
        assertNear(75.0, lunes[0].percent1Rm)
        assertEquals(4, lunes[1].sets)
        assertEquals(6, lunes[1].repsMin)
        assertEquals(8, lunes[1].repsMax)
        assertEquals(120, lunes[1].restSeconds)
    }

    @Test
    fun `el CSV de ejemplo da ids estables y el texto original de cada fila`() {
        val sheet = CsvReader.parse(readResource("import/programa.csv"), name = "programa")

        val draft = ProgramImportParser.parseSheets(listOf(sheet), title = "CSV")
        assertUniqueIds(draft)

        assertEquals(emptyList<String>(), draft.warningMessages)
        val day = draft.weeks.single().days.single()
        assertEquals("programa", day.name)
        assertEquals("w1-d1", day.id)
        assertEquals(listOf("w1-d1-e1", "w1-d1-e2"), day.exercises.map { it.id })
        assertEquals("Press banca; agarre medio · 4 · 6-8 · 80,5 · 8", day.exercises[0].sourceText)
        assertEquals("Dominadas · 3 · 8", day.exercises[1].sourceText)
        assertNear(80.5, day.exercises[0].loadKg)
    }

    @Test
    fun `leer dos veces el mismo texto da el mismo borrador, con ids y avisos iguales`() {
        val text = readResource("import/texto_pegado.txt")

        val first = ProgramImportParser.parseText(text, "Pegado")
        assertEquals(first, ProgramImportParser.parseText(text, "Pegado"))
        assertUniqueIds(first)
    }

    @Test
    fun `notacion compacta de series, repeticiones, carga, RPE, porcentaje y descanso`() {
        val text = """
            Día 1
            Curl A 4x8
            Curl B 4×8
            Curl C 3x8-10
            Curl D 5x5 @100kg
            Curl E 3×10 RPE8
            Curl F 4 series de 6
            Curl G 3x12 @75%
            Curl H 3x10 2' descanso
        """.trimIndent()

        val draft = ProgramImportParser.parseText(text, title = "")
        assertUniqueIds(draft)
        val exercises = draft.weeks.single().days.single().exercises

        assertEquals(8, exercises.size)
        assertEquals(listOf(4, 4, 3, 5, 3, 4, 3, 3), exercises.map { it.sets })
        assertEquals(listOf(8, 8, 8, 5, 10, 6, 12, 10), exercises.map { it.repsMin })
        assertEquals(listOf(8, 8, 10, 5, 10, 6, 12, 10), exercises.map { it.repsMax })
        assertNear(100.0, exercises[3].loadKg)
        assertNear(8.0, exercises[4].rpe)
        assertNear(75.0, exercises[6].percent1Rm)
        assertEquals(120, exercises[7].restSeconds)
    }

    @Test
    fun `texto con tabuladores, copiado de una hoja, se lee como tabla`() {
        val text = "Ejercicio\tSeries\tReps\tPeso\nSentadilla\t4\t6\t120\nPeso muerto\t3\t8\t100\n"

        val draft = ProgramImportParser.parseText(text, title = "Tabla")
        assertUniqueIds(draft)
        val exercises = draft.weeks.single().days.single().exercises

        assertEquals(listOf("Sentadilla", "Peso muerto"), exercises.map { it.rawName })
        assertNear(120.0, exercises[0].loadKg)
        assertEquals(8, exercises[1].repsMax)
    }

    @Test
    fun `lo que no se entiende va a los avisos sin lanzar excepciones`() {
        val text = "Día 1\nSentadilla 4x6\nHacer 3 vueltas de cada una\n@@@ ### 0x0 ∞"

        val draft = ProgramImportParser.parseText(text, title = "Raro")
        assertUniqueIds(draft)

        assertEquals(listOf("Sentadilla"), draft.weeks.single().days.single().exercises.map { it.rawName })
        // «@@@ ###» no tiene letras: es una fila de etiquetas que se cuenta por hoja, no un ejercicio con cero series.
        assertEquals(
            listOf(
                "Línea 3: no se entiende «Hacer 3 vueltas de cada una»",
                "Texto pegado: se omitió 1 fila de cálculo o de etiquetas.",
            ),
            draft.warningMessages,
        )
        assertEquals(listOf(ImportWarningKind.SKIPPED_ROW, ImportWarningKind.SHEET_NOTE), draft.warnings.map { it.kind })
        assertEquals(listOf("w1-d1", null), draft.warnings.map { it.dayId })
    }

    @Test
    fun `cada tipo de aviso lleva su semana, su dia y su texto de origen`() {
        val text = "Semana 2\nDía 1\nSentadilla 3x5\nHacer 3 vueltas\nDía 2"

        val draft = ProgramImportParser.parseText(text, title = "Avisos")
        assertUniqueIds(draft)

        assertEquals(listOf("a1", "a2"), draft.warnings.map { it.id })
        val skipped = draft.warnings[0]
        assertEquals(ImportWarningKind.SKIPPED_ROW, skipped.kind)
        assertEquals("Línea 4", skipped.location)
        assertEquals("Línea 4: no se entiende «Hacer 3 vueltas»", skipped.message)
        assertEquals("Hacer 3 vueltas", skipped.sourceText)
        assertEquals(2, skipped.weekIndex)
        assertEquals("w2-d1", skipped.dayId)
        assertNull(skipped.exerciseId)
        val empty = draft.warnings[1]
        assertEquals(ImportWarningKind.EMPTY_DAY, empty.kind)
        assertEquals("Línea 5", empty.location)
        assertEquals("«Día 2» no tiene ejercicios; se omite", empty.message)
        assertEquals("Día 2", empty.sourceText)
        assertEquals(2, empty.weekIndex)
        assertNull(empty.dayId)
        assertNull(empty.exerciseId)
        assertEquals(listOf("w2-d1-e1"), draft.weeks.single().days.single().exercises.map { it.id })
    }

    @Test
    fun `un valor descartado de un ejercicio conservado apunta a ese ejercicio`() {
        val draft = ProgramImportParser.parseText("Día 1\nSentadilla 4x6 RPE 11", title = "")
        assertUniqueIds(draft)

        val warning = draft.warnings.single()
        assertEquals(ImportWarningKind.IGNORED_VALUE, warning.kind)
        assertEquals("Línea 2: el RPE de «Sentadilla» está fuera de rango; se ignora", warning.message)
        assertEquals("Sentadilla 4x6 RPE 11", warning.sourceText)
        assertEquals(1, warning.weekIndex)
        assertEquals("w1-d1", warning.dayId)
        assertEquals("w1-d1-e1", warning.exerciseId)
        assertNull(draft.weeks.single().days.single().exercises.single().rpe)
    }

    @Test
    fun `en una hoja, un valor ilegible apunta al ejercicio y una fila sin nombre sale como aviso de su dia`() {
        val sheet = ImportSheet(
            name = "Día 1",
            rows = listOf(
                listOf("Ejercicio", "Series", "Reps", "Peso"),
                listOf("Sentadilla", "4", "6", "abc"),
                listOf("", "3", "8", ""),
            ),
        )

        val draft = ProgramImportParser.parseSheets(listOf(sheet), title = "")
        assertUniqueIds(draft)

        assertEquals(listOf("w1-d1-e1"), draft.weeks.single().days.single().exercises.map { it.id })
        assertEquals(listOf(ImportWarningKind.IGNORED_VALUE, ImportWarningKind.SKIPPED_ROW), draft.warnings.map { it.kind })
        val ignored = draft.warnings[0]
        assertEquals("Hoja «Día 1», fila 2: «abc» no se entiende como carga; se ignora", ignored.message)
        assertEquals("Sentadilla · 4 · 6 · abc", ignored.sourceText)
        assertEquals("w1-d1-e1", ignored.exerciseId)
        assertEquals("w1-d1", ignored.dayId)
        assertEquals(1, ignored.weekIndex)
        val skipped = draft.warnings[1]
        assertEquals("Hoja «Día 1», fila 3: fila sin nombre de ejercicio; se omite", skipped.message)
        assertEquals("3 · 8", skipped.sourceText)
        assertNull(skipped.exerciseId)
        assertEquals("w1-d1", skipped.dayId)
        assertEquals(1, skipped.weekIndex)
    }

    @Test
    fun `un aviso sin dia abierto no lleva dia, pero si su semana`() {
        val draft = ProgramImportParser.parseText("Semana 3\nHacer 3 vueltas\nSentadilla 3x5", title = "")
        assertUniqueIds(draft)

        val warning = draft.warnings.single()
        assertEquals("Línea 2: no se entiende «Hacer 3 vueltas»", warning.message)
        assertEquals(3, warning.weekIndex)
        assertNull(warning.dayId)
        val week = draft.weeks.single()
        assertEquals(3, week.index)
        assertEquals("w3-d1", week.days.single().id)
        assertEquals("w3-d1-e1", week.days.single().exercises.single().id)
    }

    @Test
    fun `sin ejercicios el aviso es nada encontrado, sin semana ni dia`() {
        val draft = ProgramImportParser.parseText("", title = "Vacío")
        assertUniqueIds(draft)

        assertTrue(draft.weeks.isEmpty())
        val warning = draft.warnings.single()
        assertEquals(ImportWarningKind.NOTHING_FOUND, warning.kind)
        assertEquals("", warning.location)
        assertEquals("No se ha encontrado ningún ejercicio con series y repeticiones", warning.message)
        assertNull(warning.weekIndex)
        assertNull(warning.dayId)
        assertNull(warning.exerciseId)
    }

    /** Invariante: ningún día ni ejercicio se queda sin id y todos los ids del borrador son distintos entre sí. */
    private fun assertUniqueIds(draft: ImportedProgramDraft) {
        val ids = draft.weeks.flatMap { week -> week.days.flatMap { day -> listOf(day.id) + day.exercises.map { it.id } } }
        assertTrue("ids vacíos en $ids", ids.none { it.isEmpty() })
        assertEquals("ids repetidos en $ids", ids.size, ids.toSet().size)
    }

    private fun readResource(path: String): String =
        javaClass.classLoader!!.getResourceAsStream(path)!!.bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun assertNear(expected: Double?, actual: Double?) {
        if (expected == null) {
            assertNull(actual)
        } else {
            assertNotNull(actual)
            assertEquals(expected, actual!!, 1e-6)
        }
    }
}

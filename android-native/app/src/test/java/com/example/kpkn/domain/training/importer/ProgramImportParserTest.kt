package com.example.kpkn.domain.training.importer

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

        assertEquals("Mi plan", draft.title)
        assertEquals(emptyList<String>(), draft.warnings)
        val day = draft.weeks.single().days.single()
        assertEquals("Día 1", day.name)
        val squat = day.exercises[0]
        assertEquals("Sentadilla", squat.rawName)
        assertEquals(4, squat.sets)
        assertEquals(6, squat.repsMin)
        assertEquals(8, squat.repsMax)
        assertNear(120.0, squat.loadKg)
        assertNear(8.0, squat.rpe)
        assertEquals(180, squat.restSeconds)
        assertEquals("Profundidad completa", squat.notes)
        val deadlift = day.exercises[1]
        assertEquals("Peso muerto rumano", deadlift.rawName)
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

        assertEquals(listOf(1, 2), draft.weeks.map { it.index })
        assertEquals(listOf("Día 1 — Pierna", "Día 2 — Empuje"), draft.weeks[0].days.map { it.name })
        assertEquals(listOf("Sentadilla"), draft.weeks[0].days[0].exercises.map { it.rawName })
        assertEquals(listOf("Press banca"), draft.weeks[0].days[1].exercises.map { it.rawName })
        assertEquals(listOf(3), draft.weeks[1].days[0].exercises.map { it.sets })
        assertEquals(emptyList<String>(), draft.warnings)
    }

    @Test
    fun `texto pegado con dias, notacion compacta y encabezado Lunes`() {
        val draft = ProgramImportParser.parseText(readResource("import/texto_pegado.txt"), title = "Pegado")

        assertEquals(emptyList<String>(), draft.warnings)
        val days = draft.weeks.single().days
        assertEquals(listOf("Día 1 — Pierna", "Lunes"), days.map { it.name })
        val pierna = days[0].exercises
        assertEquals(listOf("Sentadilla", "Peso muerto rumano"), pierna.map { it.rawName })
        assertEquals(4, pierna[0].sets)
        assertEquals(6, pierna[0].repsMin)
        assertNear(120.0, pierna[0].loadKg)
        assertNear(8.0, pierna[1].rpe)
        val lunes = days[1].exercises
        assertEquals(listOf("Press banca", "Dominadas"), lunes.map { it.rawName })
        assertEquals(10, lunes[0].repsMin)
        assertNear(75.0, lunes[0].percent1Rm)
        assertEquals(4, lunes[1].sets)
        assertEquals(6, lunes[1].repsMin)
        assertEquals(8, lunes[1].repsMax)
        assertEquals(120, lunes[1].restSeconds)
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

        val exercises = ProgramImportParser.parseText(text, title = "").weeks.single().days.single().exercises

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

        val exercises = ProgramImportParser.parseText(text, title = "Tabla").weeks.single().days.single().exercises

        assertEquals(listOf("Sentadilla", "Peso muerto"), exercises.map { it.rawName })
        assertNear(120.0, exercises[0].loadKg)
        assertEquals(8, exercises[1].repsMax)
    }

    @Test
    fun `lo que no se entiende va a los avisos sin lanzar excepciones`() {
        val text = "Día 1\nSentadilla 4x6\nHacer 3 vueltas de cada una\n@@@ ### 0x0 ∞"

        val draft = ProgramImportParser.parseText(text, title = "Raro")

        assertEquals(listOf("Sentadilla"), draft.weeks.single().days.single().exercises.map { it.rawName })
        assertTrue(draft.warnings.size >= 2)
        assertTrue(draft.warnings.any { it.contains("Hacer 3 vueltas") })
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

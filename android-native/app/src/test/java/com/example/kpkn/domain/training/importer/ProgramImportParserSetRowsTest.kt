package com.example.kpkn.domain.training.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * H3h: hojas de una serie por fila (familia 5/3/1), tope de la carga según la unidad. Las hojas están escritas a mano,
 * con nombres inventados; solo imitan la disposición de las plantillas reales.
 */
class ProgramImportParserSetRowsTest {

    @Test
    fun `familia 5-3-1 con series Set N, sus repeticiones por semana y solo el primer ciclo`() {
        val sheet = ImportSheet(
            name = "Press",
            rows = listOf(
                listOf("Press – 5/3/1 BBB"),
                listOf("", "Week 1 (3x5)", "", "", "Week 2 (5/3/1)", "", ""),
                listOf("Set", "% of TM", "Load", "AMRAP Reps", "% of TM", "Load", "AMRAP Reps"),
                listOf("Cycle 1 (add 5 lb to TM)"),
                listOf("Set 1", "65%", "80", "", "65%", "85", ""),
                listOf("Set 2", "75%", "90", "", "75%", "95", ""),
                listOf("Set 3 (AMRAP)", "85%", "100", "", "85%", "105", ""),
                listOf("Boring But Big (5x10 @ BBB %)"),
                listOf("BBB Set 1 (10 reps)", "0.5", "60", "", "0.5", "65", ""),
                listOf("Cycle 2 (add 5 lb to TM)"),
                listOf("Set 1", "70%", "999", "", "70%", "999", ""),
            ),
        )

        val draft = ProgramImportParser.parseSheets(listOf(sheet), title = "5/3/1")

        assertEquals(listOf(1, 2), draft.weeks.map { it.index })
        draft.weeks.forEach { week -> assertEquals(listOf("Press"), week.days.map { it.name }) }
        val week1 = draft.weeks[0].days.single().exercises
        assertEquals(listOf("Press", "Press", "Press", "Press BBB"), week1.map { it.rawName })
        assertEquals(listOf(1, 1, 1, 1), week1.map { it.sets })
        assertEquals(listOf(5, 5, 5, 10), week1.map { it.repsMin })
        assertEquals(listOf(80.0, 90.0, 100.0, 60.0), week1.map { it.loadKg })
        assertEquals(listOf(65.0, 75.0, 85.0, 50.0), week1.map { it.percent1Rm })
        assertEquals("AMRAP: última serie, 5 o más repeticiones.", week1[2].notes)
        // 5/3/1: la semana 2 da 5, 3 y 1 repeticiones por orden de serie.
        val week2 = draft.weeks[1].days.single().exercises
        assertEquals(listOf(5, 3, 1, 10), week2.map { it.repsMin })
        assertEquals(listOf(85.0, 95.0, 105.0, 65.0), week2.map { it.loadKg })
        assertEquals("AMRAP: última serie, 1 o más repeticiones.", week2[2].notes)
        // El segundo ciclo no se lee y la hoja lo dice.
        assertTrue(draft.weeks.flatMap { it.days }.flatMap { it.exercises }.none { it.loadKg == 999.0 })
        assertEquals(
            listOf("Hoja «Press»: se leyó el primer ciclo."),
            draft.warnings.filter { it.kind == ImportWarningKind.SHEET_NOTE }.map { it.message },
        )
        assertUniqueIds(draft)
    }

    @Test
    fun `familia 5-3-1 con el nombre del levantamiento en cada bloque, Set % y Deload`() {
        val sheet = ImportSheet(
            name = "531 Vanilla",
            rows = listOf(
                listOf("Cycle 1 – 5/3/1 Vanilla"),
                listOf("Lift", "Week 1 – 3x5", "", "", "", "", "Week 2 – 3x3", "", "", "", ""),
                listOf("", "Set %", "Load", "Reps Done", "Est 1RM", "", "Set %", "Load", "Reps Done", "Est 1RM", ""),
                listOf("Military", "65%", "80", "", "", "", "70%", "85", "", "", ""),
                listOf("", "70%", "85", "", "", "", "80%", "95", "", "", ""),
                listOf("", "75%", "90", "", "90", "", "85%", "105", "", "", ""),
                listOf(""),
                listOf("Deadlift", "65%", "185", "", "", "", "70%", "200", "", "", ""),
                listOf("", "70%", "200", "", "", "", "80%", "225", "", "", ""),
                listOf("Cycle 2 – 5/3/1 Vanilla"),
                listOf("", "65%", "999", "", "", "", "65%", "999", "", "", ""),
            ),
        )

        val draft = ProgramImportParser.parseSheets(listOf(sheet), title = "Vanilla")

        assertEquals(listOf(1, 2), draft.weeks.map { it.index })
        draft.weeks.forEach { week -> assertEquals(listOf("Military", "Deadlift"), week.days.map { it.name }) }
        val military = draft.weeks[0].days[0].exercises
        assertEquals(listOf("Military", "Military", "Military"), military.map { it.rawName })
        assertEquals(listOf(5, 5, 5), military.map { it.repsMin })
        assertEquals(listOf(80.0, 85.0, 90.0), military.map { it.loadKg })
        assertEquals(listOf(65.0, 70.0, 75.0), military.map { it.percent1Rm })
        // Semana 2 («3x3»): tres repeticiones en cada serie. El Deadlift va en su propio día.
        assertEquals(listOf(3, 3, 3), draft.weeks[1].days[0].exercises.map { it.repsMin })
        assertEquals(listOf("Deadlift", "Deadlift"), draft.weeks[0].days[1].exercises.map { it.rawName })
        assertEquals(listOf(185.0, 200.0), draft.weeks[0].days[1].exercises.map { it.loadKg })
        assertEquals(
            listOf("Hoja «531 Vanilla»: se leyó el primer ciclo."),
            draft.warnings.filter { it.kind == ImportWarningKind.SHEET_NOTE }.map { it.message },
        )
        assertUniqueIds(draft)
    }

    @Test
    fun `semana con Deload y 5 repeticiones, sin etiqueta de repeticiones no sale la serie`() {
        val sheet = ImportSheet(
            name = "Squat",
            rows = listOf(
                listOf("", "Week 4 Deload", "", "", "Week 5 Sin esquema", "", ""),
                listOf("Set", "% of TM", "Load", "AMRAP Reps", "% of TM", "Load", "AMRAP Reps"),
                listOf("Set 1", "40%", "50", "", "60%", "70", ""),
            ),
        )

        val draft = ProgramImportParser.parseSheets(listOf(sheet), title = "Deload")

        assertEquals(listOf(4), draft.weeks.map { it.index })
        assertEquals(listOf(5), draft.weeks.single().days.single().exercises.map { it.repsMin })
        assertEquals(listOf("Squat"), draft.weeks.single().days.single().exercises.map { it.rawName })
    }

    @Test
    fun `libras, una carga de 1100 lb se conserva en un archivo en libras`() {
        val draft = ProgramImportParser.parseText("Día 1\nDeadlift 1x5 1100 lb", title = "Libras")

        assertEquals(ImportLoadUnit.LB, draft.loadUnit)
        assertEquals(listOf(1100.0), draft.weeks.single().days.single().exercises.map { it.loadKg })
        assertEquals(emptyList<String>(), draft.warningMessages)
    }

    @Test
    fun `libras, con cabecera Weight (lb) un valor de 1100 se conserva y el tope de 1000 no aplica`() {
        val sheet = ImportSheet(
            name = "Week 1",
            rows = listOf(
                listOf("Exercise", "Sets", "Reps", "Weight (lb)"),
                listOf("Deadlift", "1", "5", "1100"),
            ),
        )

        val draft = ProgramImportParser.parseSheets(listOf(sheet), title = "Libras")

        assertEquals(ImportLoadUnit.LB, draft.loadUnit)
        assertEquals(listOf(1100.0), draft.weeks.single().days.single().exercises.map { it.loadKg })
        assertEquals(emptyList<String>(), draft.warningMessages)
    }

    @Test
    fun `kilos, una carga de 1100 no cabe, avisa como antes y no conserva el valor`() {
        val draft = ProgramImportParser.parseText("Día 1\nDeadlift 1x5 @1100", title = "Kilos")

        assertEquals(ImportLoadUnit.KG, draft.loadUnit)
        assertEquals(listOf<Double?>(null), draft.weeks.single().days.single().exercises.map { it.loadKg })
        assertEquals(listOf("Línea 2: la carga de «Deadlift» está fuera de rango; se ignora"), draft.warningMessages)
        assertEquals("w1-d1-e1", draft.warnings.single().exerciseId)
    }

    @Test
    fun `libras, una carga de 0,7 lb queda fuera del rango de 1 a 2200 lb y se avisa`() {
        val draft = ProgramImportParser.parseText("Día 1\nCurl 3x10 0.7 lb\nPress 3x8 @100 lb", title = "Mínimo")

        assertEquals(ImportLoadUnit.LB, draft.loadUnit)
        assertEquals(listOf("Curl", "Press"), draft.weeks.single().days.single().exercises.map { it.rawName })
        assertEquals(listOf<Double?>(null, 100.0), draft.weeks.single().days.single().exercises.map { it.loadKg })
        assertEquals(listOf("Línea 2: la carga de «Curl» está fuera de rango; se ignora"), draft.warningMessages)
    }

    /** Invariante: ningún día ni ejercicio se queda sin id y todos los ids del borrador son distintos entre sí. */
    private fun assertUniqueIds(draft: ImportedProgramDraft) {
        val ids = draft.weeks.flatMap { week -> week.days.flatMap { day -> listOf(day.id) + day.exercises.map { it.id } } }
        assertTrue("ids vacíos en $ids", ids.none { it.isEmpty() })
        assertEquals("ids repetidos en $ids", ids.size, ids.toSet().size)
    }
}

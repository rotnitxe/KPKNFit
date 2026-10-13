package com.example.kpkn.domain.training.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Columnas de esquema y de series del importador (H6a): «Sets x Reps» y sus variantes, columnas de registro, «Set n»,
 * «S · X · R», día delante de la cabecera y listas de carga × repeticiones. Los ejemplos están escritos a mano.
 */
class ProgramImportParserSchemeColumnsTest {

    private fun draftOf(rows: List<List<String>>): ImportedProgramDraft =
        ProgramImportParser.parseSheets(listOf(ImportSheet("Hoja", rows)), title = "Prueba")

    private fun exercisesOf(rows: List<List<String>>): List<ImportedExercise> =
        draftOf(rows).weeks.flatMap { week -> week.days.flatMap { it.exercises } }

    @Test
    fun `Sets x Reps con 1 x 1 da una serie de 1 con la carga de su columna, y los registros no cuentan`() {
        val exercises = exercisesOf(
            listOf(
                listOf("Exercise", "Sets x Reps", "Intensity", "Load", "Weight Used", "Reps Done", "Notes"),
                listOf("Bench - Top Single", "1 x 1", "90%", "250", "", "", "Warm up, then hit the top single."),
            ),
        )

        val single = exercises.single()
        assertEquals("Bench - Top Single", single.rawName)
        assertEquals(1, single.sets)
        assertEquals(1, single.repsMin)
        assertEquals(1, single.repsMax)
        assertEquals(250.0, single.loadKg)
        assertEquals("Warm up, then hit the top single.", single.notes)
    }

    @Test
    fun `cada cabecera de la lista cerrada de esquema se lee como Sets x Reps`() {
        val headers = listOf(
            "Sets x Reps", "Sets×Reps", "Sets*Reps", "Set x Rep", "Sets x Rep", "Sets/Reps", "Sets / Reps", "Series x Reps",
            "Series x Repeticiones", "Set Scheme", "Rep Scheme", "Scheme", "Esquema", "Reps / Scheme", "Sets x Reps / How",
            "Sets x", "Rx", "Prescription", "Prescripción", "Target",
        )
        for (header in headers) {
            val exercises = exercisesOf(listOf(listOf("Exercise", header, "Load"), listOf("Squat", "3 x 5", "100")))
            assertEquals("cabecera «$header»", listOf(3 to 5), exercises.map { it.sets to it.repsMin })
            assertEquals("cabecera «$header»", listOf(5), exercises.map { it.repsMax })
            assertEquals("cabecera «$header»", listOf(100.0), exercises.map { it.loadKg })
        }
    }

    @Test
    fun `un rango de series 3-4 x 6-8 cuenta 3 series, 6-8 repeticiones y la nota Series 3-4`() {
        val squat = exercisesOf(listOf(listOf("Exercise", "Sets x Reps", "Load"), listOf("Squat", "3-4 x 6-8", "185"))).single()

        assertEquals(3, squat.sets)
        assertEquals(6, squat.repsMin)
        assertEquals(8, squat.repsMax)
        assertEquals(185.0, squat.loadKg)
        assertEquals("Series: 3-4.", squat.notes)
    }

    @Test
    fun `la notacion larga y el texto que sigue a la notacion van a notas`() {
        val exercises = exercisesOf(
            listOf(
                listOf("Exercise", "Set Scheme", "Load"),
                listOf("Bench Press", "3 sets of 3-5", "70"),
                listOf("Squat", "3 sets of 6", "80"),
                listOf("Deadlift", "5 x 5 (heavy, squeeze)", "100"),
                listOf("Back Squat", "3 x 10 (build to bodyweight)", "120"),
            ),
        )

        assertEquals(listOf("Bench Press", "Squat", "Deadlift", "Back Squat"), exercises.map { it.rawName })
        assertEquals(listOf(3, 3, 5, 3), exercises.map { it.sets })
        assertEquals(listOf(3, 6, 5, 10), exercises.map { it.repsMin })
        assertEquals(listOf(5, 6, 5, 10), exercises.map { it.repsMax })
        assertEquals(listOf(70.0, 80.0, 100.0, 120.0), exercises.map { it.loadKg })
        assertEquals(listOf(null, null, "(heavy, squeeze)", "(build to bodyweight)"), exercises.map { it.notes })
    }

    @Test
    fun `una celda de esquema sin notacion no es un ejercicio ni inventa series`() {
        val draft = draftOf(
            listOf(
                listOf("Exercise", "Sets x Reps"),
                listOf("Pull-Ups", "sets to 25 total reps"),
                listOf("Dips", "Work up to a 3-5 rep max"),
            ),
        )

        assertTrue(draft.weeks.isEmpty())
    }

    @Test
    fun `Last Set, Set 1 Reps y First Set no son columna de esquema ni de series`() {
        for (header in listOf("Last Set Rep Count", "Set 1 Reps", "First Set RPE")) {
            val exercises = exercisesOf(listOf(listOf("Exercise", header, "Load"), listOf("Squat", "3 x 5", "100")))
            assertTrue("cabecera «$header»", exercises.isEmpty())
        }
    }

    @Test
    fun `Series totales y Sets con Reps siguen siendo la columna de series`() {
        val totals = exercisesOf(listOf(listOf("Exercise", "Series totales", "Reps"), listOf("Squat", "4", "8")))
        val plain = exercisesOf(listOf(listOf("Exercise", "Sets", "Reps"), listOf("Squat", "3", "8")))

        assertEquals(listOf(4 to 8), totals.map { it.sets to it.repsMin })
        assertEquals(listOf(3 to 8), plain.map { it.sets to it.repsMin })
    }

    @Test
    fun `Reps Done es un registro y la columna de repeticiones es Reps`() {
        val exercises = exercisesOf(listOf(listOf("Exercise", "Sets", "Reps Done", "Reps"), listOf("Squat", "3", "", "8")))

        assertEquals(listOf(3 to 8), exercises.map { it.sets to it.repsMin })
    }

    @Test
    fun `Reps Scheme con repeticiones sueltas o con un rango sigue leyendo repeticiones`() {
        val exercises = exercisesOf(
            listOf(
                listOf("Exercise", "Sets", "Reps / Scheme"),
                listOf("Curl", "3", "8"),
                listOf("Remo", "3", "8-10"),
            ),
        )

        assertEquals(listOf(3 to 8, 3 to 8), exercises.map { it.sets to it.repsMin })
        assertEquals(listOf(8, 10), exercises.map { it.repsMax })
    }

    @Test
    fun `Set 1 y Set 2 con 8 y 6 repeticiones no se leen como cargas ni como series`() {
        val exercises = exercisesOf(listOf(listOf("Exercise", "Set 1", "Set 2"), listOf("Squat", "8", "6")))

        assertTrue(exercises.isEmpty())
    }

    @Test
    fun `las columnas Set n con cargas de 20 o mas son las series de la fila, con su carga mayor y su nota`() {
        val squat = exercisesOf(
            listOf(
                listOf("Exercise", "Reps", "RPE", "Fatigue %", "Top Set", "Set 1", "Set 2", "Set 3"),
                listOf("Squat", "4", "9", "0%", "300", "270", "285", "300"),
            ),
        ).single()

        assertEquals(3, squat.sets)
        assertEquals(4, squat.repsMin)
        assertEquals(4, squat.repsMax)
        assertEquals(300.0, squat.loadKg)
        assertEquals(9.0, squat.rpe)
        assertEquals("Cargas por serie: 270, 285, 300.", squat.notes)
    }

    @Test
    fun `con columna de carga, la carga es la de esa columna y las series son las celdas Set n`() {
        val squat = exercisesOf(
            listOf(
                listOf("Exercise", "Reps", "Load", "Set 1", "Set 2"),
                listOf("Squat", "5", "200", "180", "190"),
            ),
        ).single()

        assertEquals(2, squat.sets)
        assertEquals(200.0, squat.loadKg)
    }

    @Test
    fun `S, X y R son series, sin rol y repeticiones, y la etiqueta delante de la cabecera abre el dia`() {
        val draft = draftOf(
            listOf(
                listOf("MONDAY", "Exercise", "", "S", "X", "R", "LOAD", "%1RM", "FsRPE"),
                listOf("SQUAT", "Competition Squat", "", "3", "x", "5", "70", "68%", "6"),
            ),
        )

        assertEquals(listOf("MONDAY"), draft.weeks.single().days.map { it.name })
        val squat = draft.weeks.single().days.single().exercises.single()
        assertEquals("Competition Squat", squat.rawName)
        assertEquals(3, squat.sets)
        assertEquals(5, squat.repsMin)
        assertEquals(70.0, squat.loadKg)
        assertEquals(68.0, squat.percent1Rm)
    }

    @Test
    fun `Lift y Levantamiento son la cabecera del nombre, y Set con Sets al lado no es la columna de series`() {
        for (nameHeader in listOf("Lift", "Levantamiento")) {
            val lift = exercisesOf(
                listOf(
                    listOf(nameHeader, "Set", "Sets", "Reps", "Load"),
                    listOf("Front Squat", "1", "5", "5", "100"),
                ),
            ).single()

            assertEquals("cabecera «$nameHeader»", "Front Squat", lift.rawName)
            assertEquals("cabecera «$nameHeader»", 5, lift.sets)
            assertEquals("cabecera «$nameHeader»", 5, lift.repsMin)
            assertEquals("cabecera «$nameHeader»", 100.0, lift.loadKg)
        }
    }

    @Test
    fun `una lista de carga por repeticiones da un ejercicio por grupo de elementos iguales seguidos`() {
        val exercises = exercisesOf(
            listOf(
                listOf("Exercise", "Work-up (load × reps)", "Log"),
                listOf("Snatch", "95×3, 95×3, 100×3, 100×3", ""),
            ),
        )

        assertEquals(listOf("Snatch", "Snatch"), exercises.map { it.rawName })
        assertEquals(listOf(2 to 3, 2 to 3), exercises.map { it.sets to it.repsMin })
        assertEquals(listOf(95.0, 100.0), exercises.map { it.loadKg })
    }

    @Test
    fun `una lista de carga por repeticiones por series da un ejercicio por elemento`() {
        val exercises = exercisesOf(
            listOf(
                listOf("Exercise", "Prescription (load x reps x sets)"),
                listOf("Snatch", "105x1x5, 90x2x3, 90x3x2"),
            ),
        )

        assertEquals(listOf(5 to 1, 3 to 2, 2 to 3), exercises.map { it.sets to it.repsMin })
        assertEquals(listOf(105.0, 90.0, 90.0), exercises.map { it.loadKg })
    }

    @Test
    fun `una celda que no es lista bajo una cabecera de lista no inventa series`() {
        val exercises = exercisesOf(listOf(listOf("Exercise", "Work-up (load × reps)"), listOf("Squat", "reps: 10, 10, 10")))

        assertTrue(exercises.isEmpty())
    }

    @Test
    fun `filas por serie con cargas distintas forman un ejercicio con sus series y la nota de cargas`() {
        val squat = exercisesOf(
            listOf(
                listOf("Exercise", "Set #", "Reps", "Weight"),
                listOf("Squat", "1", "5", "60"),
                listOf("", "2", "5", "70"),
                listOf("", "3", "5", "80"),
            ),
        ).single()

        assertEquals("Squat", squat.rawName)
        assertEquals(3, squat.sets)
        assertEquals(5, squat.repsMin)
        assertEquals(5, squat.repsMax)
        assertEquals(80.0, squat.loadKg)
        assertEquals("Cargas por serie: 60, 70, 80.", squat.notes)
    }

    @Test
    fun `el calentamiento no cuenta como serie y se anota`() {
        val squat = exercisesOf(
            listOf(
                listOf("Exercise", "Set #", "Reps", "Weight"),
                listOf("Squat", "Warm-up", "8", "40"),
                listOf("", "1", "5", "80"),
                listOf("", "2", "5", "80"),
            ),
        ).single()

        assertEquals(2, squat.sets)
        assertEquals(80.0, squat.loadKg)
        assertEquals("Se omitió 1 serie de calentamiento.", squat.notes)
    }

    @Test
    fun `las repeticiones distintas dan un rango y el nombre en blanco sigue al anterior`() {
        val exercises = exercisesOf(
            listOf(
                listOf("Exercise", "Set #", "Goal Reps", "Actual Reps"),
                listOf("Bench", "1", "10", ""),
                listOf("", "2", "8", ""),
                listOf("", "3", "8", ""),
                listOf("", "4", "6", ""),
                listOf("Row", "1", "12", ""),
                listOf("", "2", "12", ""),
            ),
        )

        assertEquals(listOf("Bench", "Row"), exercises.map { it.rawName })
        assertEquals(listOf(4, 2), exercises.map { it.sets })
        assertEquals(listOf(6, 12), exercises.map { it.repsMin })
        assertEquals(listOf(10, 12), exercises.map { it.repsMax })
    }

    @Test
    fun `el porcentaje, el RPE y el RIR salen de la fila de trabajo mas pesada`() {
        val squat = exercisesOf(
            listOf(
                listOf("Exercise", "Set #", "Reps", "Weight", "%", "RPE", "RIR"),
                listOf("Squat", "1", "5", "60", "60%", "6", ""),
                listOf("", "2", "5", "80", "80%", "8", "2"),
                listOf("", "3", "5", "70", "70%", "7", ""),
            ),
        ).single()

        assertEquals(80.0, squat.loadKg)
        assertEquals(80.0, squat.percent1Rm)
        assertEquals(8.0, squat.rpe)
        assertEquals(2, squat.rir)
        assertEquals("Cargas por serie: 60, 80, 70.", squat.notes)
    }

    @Test
    fun `dias repetidos en una hoja sin semanas abren la semana siguiente`() {
        val draft = draftOf(
            listOf(
                listOf("DAY 1"),
                listOf("Squat 3x5"),
                listOf("DAY 2"),
                listOf("Bench 3x5"),
                listOf("DAY 1"),
                listOf("Squat 4x6"),
            ),
        )

        assertEquals(listOf(1, 2), draft.weeks.map { it.index })
        assertEquals(listOf("DAY 1", "DAY 2"), draft.weeks[0].days.map { it.name })
        assertEquals(listOf("DAY 1"), draft.weeks[1].days.map { it.name })
        assertEquals(listOf(4), draft.weeks[1].days.single().exercises.map { it.sets })
    }

    @Test
    fun `dias repetidos con semanas reconocidas no cambian de semana`() {
        val draft = draftOf(
            listOf(
                listOf("Week 1"),
                listOf("DAY 1"),
                listOf("Squat 3x5"),
                listOf("Week 2"),
                listOf("DAY 1"),
                listOf("Bench 3x5"),
            ),
        )

        assertEquals(listOf(1, 2), draft.weeks.map { it.index })
        assertEquals(listOf(listOf("DAY 1"), listOf("DAY 1")), draft.weeks.map { week -> week.days.map { it.name } })
    }

    @Test
    fun `una hoja con nombre de semana no abre semana nueva por un dia repetido`() {
        val draft = ProgramImportParser.parseSheets(
            listOf(
                ImportSheet("Week 1", listOf(listOf("DAY 1"), listOf("Squat 3x5"), listOf("DAY 1"), listOf("Bench 3x5"))),
            ),
            title = "Semana",
        )

        assertEquals(listOf(1), draft.weeks.map { it.index })
        assertEquals(listOf("DAY 1", "DAY 1"), draft.weeks.single().days.map { it.name })
    }

    @Test
    fun `una celda Set n con carga y repeticiones da las series, el rango de repeticiones y las cargas`() {
        val squat = exercisesOf(
            listOf(
                listOf("Exercise", "Set 1", "Log", "Set 2", "Log"),
                listOf("Squat", "100 x5", "", "110 x3", ""),
            ),
        ).single()

        assertEquals(2, squat.sets)
        assertEquals(3, squat.repsMin)
        assertEquals(5, squat.repsMax)
        assertEquals(110.0, squat.loadKg)
        assertEquals("Cargas por serie: 100, 110.", squat.notes)
    }

    @Test
    fun `una columna Set con cuentas, 1 y luego 3, no agrupa dos prescripciones distintas`() {
        val exercises = exercisesOf(
            listOf(
                listOf("Exercise", "Set", "Reps", "Percent"),
                listOf("High Bar Pause Squat", "1", "3", "100%"),
                listOf("High Bar Pause Squat", "3", "3", "75%"),
            ),
        )

        assertEquals(listOf(1, 3), exercises.map { it.sets })
        assertEquals(listOf(3, 3), exercises.map { it.repsMin })
    }

    @Test
    fun `un calentamiento con indice numerico no corta la serie y se anota`() {
        val squat = exercisesOf(
            listOf(
                listOf("Exercise", "Set #", "Reps", "Weight"),
                listOf("Squat Warm-up", "1", "8", "40"),
                listOf("Squat", "1", "5", "80"),
                listOf("", "2", "5", "80"),
            ),
        ).single()

        assertEquals(2, squat.sets)
        assertEquals(80.0, squat.loadKg)
        assertEquals("Se omitió 1 serie de calentamiento.", squat.notes)
    }

    @Test
    fun `una cabecera Movement repetida en varios bloques sigue siendo tabla`() {
        val exercises = exercisesOf(
            listOf(
                listOf("", "", "MOVEMENT", "SETS X", "REPS", "RIR", "", "MOVEMENT", "SETS X", "REPS", "RIR"),
                listOf("sq", "D1", "Pull Ups (Bodyweight)", "5", "5", "", "", "Row", "3", "8", "3"),
            ),
        )

        assertEquals(listOf("Pull Ups (Bodyweight)"), exercises.map { it.rawName })
        assertEquals(listOf(5), exercises.map { it.sets })
    }
}

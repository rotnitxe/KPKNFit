package com.example.kpkn.domain.training.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `filas OCR con DAY A repetido agrupan el movimiento y su prescripcion`() {
        val rows = listOf(
            listOf("DAY A", "Back Squat", "3 x 5 reps", "70 kg", "RIR 2", "rest 2 min"),
            listOf("DAY A", "Bench Press", "3 x 5 reps", "50 kg", "rest 2 min"),
            listOf("DAY B", "Deadlift", "2 x 5", "80 kg", "rest 2 min"),
        )

        val draft = ProgramImportParser.parseSheets(
            listOf(ImportSheet(name = "Rutina manuscrita", rows = rows, pageStarts = listOf(0))),
            title = "Rutina manuscrita",
        )

        val days = draft.weeks.single().days
        assertEquals(listOf("DAY A", "DAY B"), days.map { it.name })
        assertEquals(listOf("Back Squat", "Bench Press"), days[0].exercises.map { it.rawName })
        assertEquals(3, days[0].exercises[0].sets)
        assertEquals(5, days[0].exercises[0].repsMin)
        assertEquals(70.0, requireNotNull(days[0].exercises[0].loadKg), 0.001)
        assertEquals(listOf("Deadlift"), days[1].exercises.map { it.rawName })
        assertEquals(emptyList<String>(), skippedMessages(draft))
    }

    @Test
    fun `filas OCR conservan duracion aunque falte confirmar el numero de series`() {
        val rows = listOf(listOf("DAY A", "Plank", "40 s", "rest 1 min"))
        val draft = ProgramImportParser.parseSheets(
            listOf(ImportSheet(name = "Rutina manuscrita", rows = rows, pageStarts = listOf(0))),
            title = "Rutina manuscrita",
        )

        val plank = exercisesOf(draft).single()
        assertEquals("Plank", plank.rawName)
        assertEquals(0, plank.sets)
        assertEquals(40, plank.durationSeconds)
        assertEquals(60, plank.restSeconds)
        assertFalse(plank.notes.orEmpty().contains("rest", ignoreCase = true))
        val warning = draft.warnings.single { it.kind == ImportWarningKind.INCOMPLETE_ROW }
        assertEquals(plank.id, warning.exerciseId)
        assertTrue(warning.message.contains("no indica cuántas series"))
        assertTrue(ImportDraftValidation.checkOccurrences(draft) { true }.any {
            it.kind == ImportIssueKind.INCOMPLETE_EXERCISE && it.exerciseId == plank.id
        })
    }

    @Test
    fun `tiempo objetivo no se confunde con descanso y admite encabezado Duration`() {
        val compact = ProgramImportParser.parseText(
            "Plank 3x40 seconds rest 1 min\nSquat 3x5 2 min",
            title = "Tiempo",
        )
        val compactExercises = exercisesOf(compact)
        assertEquals(40, compactExercises[0].durationSeconds)
        assertEquals(3, compactExercises[0].sets)
        assertEquals(60, compactExercises[0].restSeconds)
        assertEquals(1, compactExercises[0].repsMin) // An internal serialization anchor; the builder omits rep targets for TIME.
        assertEquals(5, compactExercises[1].repsMin)
        assertEquals(120, compactExercises[1].restSeconds)

        val sheet = ImportSheet(
            name = "Circuito",
            rows = listOf(
                listOf("Exercise", "Sets", "Duration (s)", "Rest"),
                listOf("Plank", "3", "40", "60 sec"),
            ),
        )
        val timed = exercisesOf(ProgramImportParser.parseSheets(listOf(sheet), title = "Circuito")).single()
        assertEquals(3, timed.sets)
        assertEquals(40, timed.durationSeconds)
        assertEquals(60, timed.restSeconds)
    }

    @Test
    fun `filas OCR de reto conservan repeticiones sin inventar series`() {
        val rows = listOf(
            listOf("Day 1", "10 Push Ups", "10 Squats or Squat Jumps", "10 Sweeping Scissors"),
            listOf("Day 2", "15 Push Ups", "15 Squats or Squat Jumps", "15 Sweeping Scissors", "15 Walking Lunges"),
        )

        val draft = ProgramImportParser.parseSheets(
            listOf(ImportSheet(name = "Reto", rows = rows, pageStarts = listOf(0))),
            title = "Reto",
        )

        val exercises = exercisesOf(draft)
        assertEquals(7, exercises.size)
        assertTrue(exercises.all { it.sets == 0 })
        assertEquals(listOf(10, 10, 10, 15, 15, 15, 15), exercises.map { it.repsMin })
        assertTrue(skippedMessages(draft).isEmpty())
        assertEquals(7, draft.warnings.count { it.kind == ImportWarningKind.INCOMPLETE_ROW })
        assertEquals(7, ImportDraftValidation.checkOccurrences(draft) { true }
            .count { it.kind == ImportIssueKind.INCOMPLETE_EXERCISE })
    }

    @Test
    fun `OCR de reto en dias por columnas recupera semanas y une nombres partidos sin inventar series`() {
        val rows = listOf(
            listOf("DAY 1", "DAY 2", "DAY 3", "DAY 4", "DAY 5", "DAY", "DAY 7"),
            listOf("10 Push Ups", "10 Push Ups", "10 Push Ups", "10 Push Ups", "", "10 Push Ups", "10 Push Ups"),
            listOf("10 Squats or Squat", "10 Squats or Squat", "10 Squats or Squat", "10 Squats or Squat", "", "10 Squats or Squat", "10 Squats or Squat"),
            listOf("Jumps", "Jumps", "Jumps", "Jumps", "", "Jumps", "Jumps"),
            listOf("10Sweeping Scissors", "10 Sweeping Scissors", "10 Sweeping Scissors", "10 Sweeping Scissors", "", "10Sweeping Scissors", "10Sweeping Scissors"),
            listOf("DAY 8", "DAY 9", "DAY 10", "DAY 11", "DAY 12", "DAY 13", "DAY 14"),
            listOf("15 Push Ups", "15 Push Ups", "15 Push Ups", "15 Push Ups", "15 Push Ups", "15 Push Ups", "15 Push Ups"),
            listOf("15 Squats or Squat", "15 Squats or Squat", "15 Squats or Squat", "15 Squats or Squat", "15 Squats or Squat", "15 Squats or Squat", "15 Squats or Squat"),
            listOf("Jumps", "Jumps", "Jumps", "Jumps", "Jumps", "Jumps", "Jumps"),
            listOf("15 Sweeping Scissors", "15 Sweeping Scissors", "15 Sweeping Scissors", "15 Sweeping Scissors", "15 Sweeping Scissors", "15 Sweeping Scissors", "15 Sweeping Scissors"),
            listOf("15 Walking Lunges", "15 Walking Lunges", "15 Walking Lunges", "15 Walking Lunges", "15 Walking Lunges", "15 Walking Lunges", "15 Walking Lunges"),
            listOf("15 Army Crawl", "15 Army Crawl", "15 Army Crawl", "15 Army Crawl", "15 Army Crawl", "15 Army Crawl", "15 Army Crawl"),
        )

        val draft = ProgramImportParser.parseSheets(
            listOf(ImportSheet(name = "Reto impreso", rows = rows, pageStarts = listOf(0))),
            title = "Reto impreso",
        )

        assertEquals(listOf(1, 2), draft.weeks.map { it.index })
        assertEquals(6, draft.weeks[0].days.size) // El día 5 no tiene ejercicios visibles; se avisa en vez de inventar descanso.
        assertEquals(7, draft.weeks[1].days.size)
        assertEquals(listOf("Push Ups", "Squats or Squat Jumps", "Sweeping Scissors"), draft.weeks[0].days.first().exercises.map { it.rawName })
        assertEquals(listOf(10, 10, 10), draft.weeks[0].days.first().exercises.map { it.repsMin })
        assertEquals(listOf(15, 15, 15, 15, 15), draft.weeks[1].days.first().exercises.map { it.repsMin })
        assertTrue(exercisesOf(draft).all { it.sets == 0 })
        assertEquals(exercisesOf(draft).size, draft.warnings.count { it.kind == ImportWarningKind.INCOMPLETE_ROW })
        assertEquals(1, draft.warnings.count { it.kind == ImportWarningKind.EMPTY_DAY })
        assertTrue(skippedMessages(draft).isEmpty())
    }

    @Test
    fun `OCR de reto deja celdas truncadas como avisos y no completa ejercicios vecinos`() {
        val header = MutableList(20) { "" }.apply {
            this[0] = "DAY 9"
            this[13] = "DAY 22"
            this[18] = "DAY 27"
            this[19] = "DAY 28"
        }
        val truncated = MutableList(20) { "" }.apply {
            this[0] = "Sautps"
            this[13] = "25 Push"
            this[18] = "25 Push"
        }
        val neighboringExercises = MutableList(20) { "" }.apply {
            this[0] = "15 Sweeping Scissors"
            this[1] = "15 Push Ups"
            this[13] = "25 Sweeping Scissors"
            this[18] = "25 Sweeping Scissors"
            this[19] = "25 Push Ups"
        }
        val rows = listOf(
            header,
            truncated,
            neighboringExercises,
        )

        val draft = ProgramImportParser.parseSheets(
            listOf(ImportSheet(name = "Reto con OCR ambiguo", rows = rows, pageStarts = listOf(0))),
            title = "Reto con OCR ambiguo",
        )

        val exercises = exercisesOf(draft)
        val skipped = draft.warnings.filter { it.kind == ImportWarningKind.SKIPPED_ROW }
        assertEquals(3, skipped.size)
        assertTrue(skipped.any { it.message.contains("«Sautps»") })
        assertEquals(2, skipped.count { it.message.contains("«25 Push»") })
        assertEquals(listOf(9, 22, 27), skipped.map { warning ->
            val day = draft.weeks.flatMap { it.days }.single { it.id == warning.dayId }
            Regex("\\d+").find(day.name)?.value?.toInt()
        })
        val daysByNumber = draft.weeks.flatMap { it.days }.associateBy { day ->
            Regex("\\d+").find(day.name)?.value?.toInt() ?: error("día sin número: ${day.name}")
        }
        assertEquals(listOf("Sweeping Scissors"), daysByNumber.getValue(9).exercises.map { it.rawName })
        assertEquals(listOf("Push Ups"), daysByNumber.getValue(10).exercises.map { it.rawName })
        assertEquals(listOf("Sweeping Scissors"), daysByNumber.getValue(22).exercises.map { it.rawName })
        assertEquals(listOf("Sweeping Scissors"), daysByNumber.getValue(27).exercises.map { it.rawName })
        assertEquals(listOf("Push Ups"), daysByNumber.getValue(28).exercises.map { it.rawName })
        assertTrue(exercises.none { it.rawName == "Sautps" || it.rawName == "Push" })
        assertEquals(15, daysByNumber.getValue(10).exercises.single().repsMin)
        assertEquals(25, daysByNumber.getValue(28).exercises.single().repsMin)
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
    fun `en una hoja de OCR sin semanas, una etiqueta de dia repetida fuera de la cabecera de pagina abre la semana siguiente`() {
        val rows = listOf(listOf("Día 1"), listOf("Sentadilla 4x8"), listOf("Press 3x8"), listOf("DÍA 1"), listOf("Prensa 3x10"))

        val draft = ProgramImportParser.parseSheets(
            listOf(ImportSheet(name = "Hoja", rows = rows, pageStarts = listOf(0))),
            title = "OCR",
        )

        assertEquals(listOf(listOf("Día 1"), listOf("DÍA 1")), draft.weeks.map { week -> week.days.map { it.name } })
    }

    @Test
    fun `fuera de una hoja de OCR, una etiqueta de dia repetida sin semanas abre la semana siguiente`() {
        val draft = ProgramImportParser.parseText("Día 1\nSentadilla 4x8\nDÍA 1\nPrensa 3x10", title = "Texto")

        assertEquals(listOf(listOf("Día 1"), listOf("DÍA 1")), draft.weeks.map { week -> week.days.map { it.name } })
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
    fun `abreviaturas y una cabecera sabad0 con contexto conservan los dias ISO`() {
        val rows = listOf(
            listOf("LUN"), listOf("Press 3x8"),
            listOf("MAR"), listOf("Remo 3x10"),
            listOf("JUE"), listOf("Sentadilla 4x6"),
            listOf("SABAD0"), listOf("Press militar 3x8"), listOf("Sentadilla frontal 3x10"),
        )

        val draft = ProgramImportParser.parseSheets(
            listOf(ImportSheet(name = "Rutina", rows = rows, pageStarts = listOf(0))),
            title = "Cuatro días",
        )

        val days = draft.weeks.single().days
        assertEquals(listOf(1, 2, 4, 6), days.map { it.dayOfWeek })
        assertEquals(listOf(1, 1, 1, 2), days.map { it.exercises.size })
        assertTrue(draft.warnings.any { it.kind == ImportWarningKind.SHEET_NOTE && it.sourceText == "SABAD0" })
        assertTrue(draft.warnings.none { it.kind == ImportWarningKind.STRUCTURE_REVIEW })
    }

    @Test
    fun `cabecera sabad0 sin contexto suficiente aísla los ejercicios para revisión`() {
        val rows = listOf(listOf("JUEVES"), listOf("Curl 3x10"), listOf("SABAD0"), listOf("Press militar 3x8"))

        val draft = ProgramImportParser.parseSheets(
            listOf(ImportSheet(name = "Rutina", rows = rows, pageStarts = listOf(0))),
            title = "Día pendiente",
        )

        assertEquals(listOf("Día sin asignar"), draft.weeks.single().days.drop(1).map { it.name })
        val warning = draft.warnings.single { it.kind == ImportWarningKind.STRUCTURE_REVIEW }
        assertEquals("w1-d2", warning.dayId)
        assertEquals("SABAD0", warning.sourceText)
        assertEquals(listOf("Press militar"), draft.weeks.single().days.last().exercises.map { it.rawName })
    }

    @Test
    fun `corrige una cabecera OCR de cualquier dia con origen y conserva dudosas para asignacion`() {
        val corrected = ProgramImportParser.parseSheets(
            listOf(
                ImportSheet(
                    name = "Rutina PDF",
                    rows = listOf(
                        listOf("VIERNES"), listOf("Curl 3x10"),
                        listOf("M0NDAY"), listOf("Press militar 3x8"),
                        listOf("MIERCOLES"), listOf("Remo con barra 3x8"),
                    ),
                    documentType = ImportDocumentType.PDF,
                    pageStarts = listOf(0),
                ),
            ),
            title = "Rutina",
        )

        assertEquals(listOf(5, 1, 3), corrected.weeks.single().days.map { it.dayOfWeek })
        val correction = corrected.warnings.single { it.kind == ImportWarningKind.SHEET_NOTE }
        assertEquals(ImportDocumentType.PDF, correction.sourceDocumentType)
        assertEquals(1, correction.sourcePage)
        assertEquals(3, correction.sourceRow)
        assertEquals("Rutina PDF", correction.sourceSheet)
        assertEquals("M0NDAY", correction.sourceText)

        val uncertain = ProgramImportParser.parseSheets(
            listOf(
                ImportSheet(
                    name = "Rutina PDF",
                    rows = listOf(
                        listOf("VIERNES"), listOf("Curl 3x10"),
                        listOf("M0N0AY"), listOf("Press militar 3x8"),
                    ),
                    documentType = ImportDocumentType.PDF,
                    pageStarts = listOf(0),
                ),
            ),
            title = "Rutina",
        )
        val warning = uncertain.warnings.single { it.kind == ImportWarningKind.STRUCTURE_REVIEW }
        val unassigned = uncertain.weeks.single().days.last()
        assertEquals("Día sin asignar", unassigned.name)
        assertNull(unassigned.dayOfWeek)
        assertEquals(unassigned.id, warning.dayId)
        assertEquals(ImportDocumentType.PDF, warning.sourceDocumentType)
        assertEquals(3, warning.sourceRow)
        assertEquals("M0N0AY", warning.sourceText)
    }

    @Test
    fun `aviso agregado limita muestras y conserva ubicacion cantidad y truncamiento`() {
        val longLine = "Nota: " + "A".repeat(600)
        val draft = ProgramImportParser.parseSheets(
            listOf(
                ImportSheet(
                    name = "Hoja OCR",
                    rows = listOf(listOf(longLine), listOf("Otra nota")),
                    documentType = ImportDocumentType.PDF,
                    pageStarts = listOf(0),
                ),
            ),
            title = "Notas",
        )
        val note = draft.warnings.single { it.kind == ImportWarningKind.SHEET_NOTE }
        assertEquals(2, note.itemCount)
        assertTrue(note.sourceText.orEmpty().contains("pág. 1"))
        assertTrue(note.sourceText.orEmpty().contains("fila 1"))
        assertTrue(note.sourceText.orEmpty().length <= 500)
        assertTrue(note.sourceTextTruncated)
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

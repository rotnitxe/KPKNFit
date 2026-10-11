package com.example.kpkn.domain.training.importer

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Disposiciones de H3e: columna de día, días en columnas, grupos con porcentaje, libras, carga cero y los dos retoques
 * de H3c. Los ejemplos están escritos a mano, con nombres inventados.
 */
class ProgramImportParserLayoutTest {

    @Test
    fun `columna de dia, el dia abre en su fila y las filas sin dia siguen en el`() {
        val sheet = ImportSheet(
            name = "Semana",
            rows = listOf(
                listOf("Dia", "Grupo Muscular", "Ejercicio", "Series", "Reps", "Peso", "Descanso", "Notas"),
                listOf("Lunes", "Pecho", "Press plano", "4", "6-8", "80", "120", ""),
                listOf("", "Pecho", "Aperturas", "3", "10", "12", "60", ""),
                listOf("Miércoles", "Espalda", "Remo", "4", "8", "60", "90", ""),
                listOf("", "Espalda", "Jalón", "3", "10", "", "", ""),
                listOf("Viernes", "Piernas", "Sentadilla", "4", "6", "100", "180", "Profundidad"),
            ),
        )

        val draft = ProgramImportParser.parseSheets(listOf(sheet), title = "Columna de día")
        assertUniqueIds(draft)

        val days = draft.weeks.single().days
        assertEquals(listOf("Lunes", "Miércoles", "Viernes"), days.map { it.name })
        assertEquals(listOf("Press plano", "Aperturas"), days[0].exercises.map { it.rawName })
        assertEquals(listOf("Remo", "Jalón"), days[1].exercises.map { it.rawName })
        assertEquals(listOf("Sentadilla"), days[2].exercises.map { it.rawName })
        // Sin otra nota, la columna de grupo muscular va a notas; si la fila tiene nota propia, la nota gana.
        assertEquals("Pecho", days[0].exercises[0].notes)
        assertEquals("Profundidad", days[2].exercises[0].notes)
        assertEquals(180, days[2].exercises[0].restSeconds)
        assertEquals(12.0, days[0].exercises[1].loadKg)
        assertEquals(emptyList<String>(), draft.warningMessages)
    }

    @Test
    fun `el mismo dia repetido en cada fila sigue el mismo dia y no abre uno nuevo`() {
        val sheet = ImportSheet(
            name = "Plan",
            rows = listOf(
                listOf("Día", "Entrenamiento", "Ejercicio", "Series", "Repeticiones", "Descanso"),
                listOf("Lunes", "Torso A", "Press banca", "4", "6-10", "3 min"),
                listOf("Lunes", "Torso A", "Jalón al pecho", "4", "6-10", "3 min"),
                listOf("Martes", "Pierna A", "Sentadilla", "4", "6-10", "3 min"),
            ),
        )

        val draft = ProgramImportParser.parseSheets(listOf(sheet), title = "Repetido")
        assertUniqueIds(draft)

        val days = draft.weeks.single().days
        assertEquals(listOf("Lunes", "Martes"), days.map { it.name })
        assertEquals(listOf("Press banca", "Jalón al pecho"), days[0].exercises.map { it.rawName })
        assertEquals(180, days[0].exercises[0].restSeconds)
        assertEquals(emptyList<String>(), draft.warningMessages)
    }

    @Test
    fun `cuadricula, tres dias en columnas con bloques de ejercicio, series y peso`() {
        val sheet = ImportSheet(
            name = "Rutina",
            rows = listOf(
                listOf("", "Lunes", "Martes", "Miercoles"),
                listOf("Ejercicio 1", "Sentadilla", "Press banca", "Peso muerto"),
                listOf("Series x Reps", "4x8", "3x10", "5x5"),
                listOf("Peso (kg)", "80", "60", "100"),
                listOf(""),
                listOf("Ejercicio 2", "Prensa", "", "Remo"),
                listOf("Series x Reps", "3x12", "", "3x8"),
                listOf("Peso (kg)", "120", "", "70"),
            ),
        )

        val draft = ProgramImportParser.parseSheets(listOf(sheet), title = "Cuadrícula")
        assertUniqueIds(draft)

        val days = draft.weeks.single().days
        assertEquals(listOf("Lunes", "Martes", "Miercoles"), days.map { it.name })
        assertEquals(listOf("w1-d1", "w1-d2", "w1-d3"), days.map { it.id })
        assertEquals(
            listOf(listOf("Sentadilla", "Prensa"), listOf("Press banca"), listOf("Peso muerto", "Remo")),
            days.map { day -> day.exercises.map { it.rawName } },
        )
        val squat = days[0].exercises[0]
        assertEquals(4, squat.sets)
        assertEquals(8, squat.repsMin)
        assertEquals(80.0, squat.loadKg)
        assertEquals(12, days[0].exercises[1].repsMax)
        assertEquals(120.0, days[0].exercises[1].loadKg)
        assertEquals(5, days[2].exercises[0].sets)
        assertEquals(70.0, days[2].exercises[1].loadKg)
        assertEquals(emptyList<String>(), draft.warningMessages)
    }

    @Test
    fun `cuadricula, un bloque sin series ni repeticiones se omite con su aviso y su dia queda vacio`() {
        val sheet = ImportSheet(
            name = "Rutina",
            rows = listOf(
                listOf("", "Lunes", "Martes", "Miercoles"),
                listOf("Ejercicio 1", "Curl", "", ""),
                listOf("Peso (kg)", "15", "", ""),
                listOf("Ejercicio 2", "", "Press", ""),
                listOf("Series x Reps", "", "3x10", ""),
            ),
        )

        val draft = ProgramImportParser.parseSheets(listOf(sheet), title = "Sin series")
        assertUniqueIds(draft)

        assertEquals(listOf("Martes"), draft.weeks.single().days.map { it.name })
        assertEquals(listOf("Press"), draft.weeks.single().days.single().exercises.map { it.rawName })
        assertEquals(
            listOf(
                "Hoja «Rutina», fila 2: «Curl» sin series; se omite",
                "«Lunes» no tiene ejercicios; se omite",
                "«Miercoles» no tiene ejercicios; se omite",
            ),
            draft.warningMessages,
        )
        assertEquals(ImportWarningKind.SKIPPED_ROW, draft.warnings[0].kind)
    }

    @Test
    fun `una semana cierra la cuadricula y las filas siguientes se leen como filas normales`() {
        val sheet = ImportSheet(
            name = "Plan",
            rows = listOf(
                listOf("", "Lunes", "Martes", "Miércoles"),
                listOf("Ejercicio 1", "Sentadilla", "", ""),
                listOf("Series x Reps", "4x8", "", ""),
                listOf("Semana 2"),
                listOf("Press banca 3x8"),
            ),
        )

        val draft = ProgramImportParser.parseSheets(listOf(sheet), title = "Semanas")
        assertUniqueIds(draft)

        assertEquals(listOf(1, 2), draft.weeks.map { it.index })
        assertEquals(listOf("Sentadilla"), draft.weeks[0].days.single().exercises.map { it.rawName })
        assertEquals(listOf("Press banca"), draft.weeks[1].days.single().exercises.map { it.rawName })
    }

    @Test
    fun `grupos con porcentaje, 50%x5, 60%x4, 70%x3x2 y la forma con barras dan un ejercicio por grupo`() {
        val draft = ProgramImportParser.parseText(
            "Día 1\nSentadilla 50%x5, 60%x4, 70%x3x2\nSentadilla 50/5 60/4 70/3/2",
            title = "Grupos",
        )
        assertUniqueIds(draft)

        val exercises = draft.weeks.single().days.single().exercises
        assertEquals(listOf("Sentadilla"), exercises.map { it.rawName }.distinct())
        assertEquals(listOf(1, 1, 2, 1, 1, 2), exercises.map { it.sets })
        assertEquals(listOf(5, 4, 3, 5, 4, 3), exercises.map { it.repsMin })
        assertEquals(listOf(5, 4, 3, 5, 4, 3), exercises.map { it.repsMax })
        assertEquals(listOf(50.0, 60.0, 70.0, 50.0, 60.0, 70.0), exercises.map { it.percent1Rm })
        assertEquals(emptyList<String>(), draft.warningMessages)
    }

    @Test
    fun `grupos, Press 4 8 no es un grupo y una notacion normal gana a los grupos`() {
        val draft = ProgramImportParser.parseText("Día 1\nPress 4/8\nSentadilla 4x8 50%x5, 60%x4", title = "Prioridad")
        assertUniqueIds(draft)

        val exercises = draft.weeks.single().days.single().exercises
        assertEquals(listOf("Sentadilla"), exercises.map { it.rawName })
        assertEquals(4, exercises.single().sets)
        assertEquals(8, exercises.single().repsMin)
        assertEquals(listOf("Línea 2: no se entiende «Press 4/8»"), draft.warningMessages)
    }

    @Test
    fun `libras, la cabecera Weight (lb) marca el borrador en libras y guarda la carga tal como viene`() {
        val sheet = ImportSheet(
            name = "Week 1",
            rows = listOf(
                listOf("Exercise", "Sets", "Reps", "Weight (lb)"),
                listOf("Bench", "3", "5", "135"),
                listOf("Squat", "3", "5", "225 lb"),
            ),
        )

        val draft = ProgramImportParser.parseSheets(listOf(sheet), title = "Libras")

        assertEquals(ImportLoadUnit.LB, draft.loadUnit)
        assertEquals(listOf(135.0, 225.0), draft.weeks.single().days.single().exercises.map { it.loadKg })
        assertEquals(emptyList<String>(), draft.warningMessages)
    }

    @Test
    fun `libras, sin cabecera, la mitad de las cargas con lb ya marca el archivo en libras`() {
        val sheet = ImportSheet(
            name = "Sin cabecera",
            rows = listOf(
                listOf("Exercise", "Sets", "Reps", "Load"),
                listOf("Bench", "3", "5", "135 lb"),
                listOf("Row", "3", "8", "80 lbs"),
                listOf("Curl", "3", "10", "20"),
            ),
        )

        val draft = ProgramImportParser.parseSheets(listOf(sheet), title = "Mitad")

        assertEquals(ImportLoadUnit.LB, draft.loadUnit)
    }

    @Test
    fun `libras, un kg en la cabecera o en una celda manda sobre las libras`() {
        val mixed = ImportSheet(
            name = "Mixto",
            rows = listOf(
                listOf("Exercise", "Sets", "Reps", "Weight"),
                listOf("Bench", "3", "5", "135 lb"),
                listOf("Row", "3", "8", "80 kg"),
            ),
        )

        val draft = ProgramImportParser.parseSheets(listOf(mixed), title = "Mixto")

        assertEquals(ImportLoadUnit.KG, draft.loadUnit)
        assertEquals(listOf(135.0, 80.0), draft.weeks.single().days.single().exercises.map { it.loadKg })
    }

    @Test
    fun `libras, la carga cero es peso corporal, sin carga y sin aviso`() {
        val sheet = ImportSheet(
            name = "Peso corporal",
            rows = listOf(
                listOf("Exercise", "Sets", "Reps", "Weight (lb)"),
                listOf("Dips", "3", "10", "0"),
                listOf("Push-up", "3", "10", "0lb"),
                listOf("Plancha", "3", "30", "0kg"),
            ),
        )

        val draft = ProgramImportParser.parseSheets(listOf(sheet), title = "Corporal")

        assertEquals(listOf(null, null, null), draft.weeks.single().days.single().exercises.map { it.loadKg })
        assertEquals(emptyList<String>(), draft.warningMessages)
    }

    @Test
    fun `un borrador guardado sin unidad de carga se lee en kilos`() {
        val old = Json { ignoreUnknownKeys = true }
            .decodeFromString(ImportedProgramDraft.serializer(), "{\"title\":\"Viejo\",\"weeks\":[]}")

        assertEquals(ImportLoadUnit.KG, old.loadUnit)
    }

    @Test
    fun `una abreviatura conocida como DL o BP es un ejercicio, no una etiqueta`() {
        val draft = ProgramImportParser.parseText("Día 1\nDL 3x5\nBP 4x6", title = "Abreviaturas")

        val exercises = draft.weeks.single().days.single().exercises
        assertEquals(listOf("DL", "BP"), exercises.map { it.rawName })
        assertEquals(listOf(3, 4), exercises.map { it.sets })
        assertEquals(emptyList<String>(), draft.warningMessages)
    }

    @Test
    fun `una frase de mas de seis palabras antes de la notacion es prosa y no da ejercicio`() {
        val draft = ProgramImportParser.parseText(
            "Día 1\nSentadilla 4x6\nEmpieza con un calentamiento progresivo de tres series antes de seguir con 5x5",
            title = "Prosa",
        )

        assertEquals(listOf("Sentadilla"), draft.weeks.single().days.single().exercises.map { it.rawName })
        assertEquals(listOf("Texto pegado: se omitió 1 línea de texto."), draft.warningMessages)
        assertTrue(draft.warnings.single().exerciseId == null)
    }

    @Test
    fun `una cabecera de dias sin filas de series o peso debajo no abre cuadricula y la tabla siguiente se lee entera`() {
        val sheet = ImportSheet(
            name = "Rutina",
            rows = listOf(
                listOf("", "LUNES", "", "MIÉRCOLES", "", "VIERNES"),
                listOf("Ejercicio", "Series", "Reps", "Peso"),
                listOf("Sentadilla", "4", "6", "100"),
                listOf("Press banca", "3", "8", "60"),
            ),
        )

        val draft = ProgramImportParser.parseSheets(listOf(sheet), title = "Sin cuadrícula")
        assertUniqueIds(draft)

        val exercises = draft.weeks.single().days.flatMap { it.exercises }
        assertEquals(listOf("Sentadilla", "Press banca"), exercises.map { it.rawName })
        assertEquals(listOf(100.0, 60.0), exercises.map { it.loadKg })
    }

    @Test
    fun `una etiqueta que no es de la cuadricula la cierra y el ejercicio de la fila siguiente no se pierde`() {
        val sheet = ImportSheet(
            name = "Plan",
            rows = listOf(
                listOf("", "Lunes", "Martes", "Miércoles"),
                listOf("Ejercicio 1", "Curl", "", ""),
                listOf("Series x Reps", "3x10", "", ""),
                listOf("RPE", "8", "", ""),
                listOf("Press banca 3x8"),
            ),
        )

        val draft = ProgramImportParser.parseSheets(listOf(sheet), title = "Cierre")
        assertUniqueIds(draft)

        val names = draft.weeks.single().days.flatMap { day -> day.exercises.map { it.rawName } }
        assertEquals(listOf("Curl", "Press banca"), names)
    }

    /** Invariante: ningún día ni ejercicio se queda sin id y todos los ids del borrador son distintos entre sí. */
    private fun assertUniqueIds(draft: ImportedProgramDraft) {
        val ids = draft.weeks.flatMap { week -> week.days.flatMap { day -> listOf(day.id) + day.exercises.map { it.id } } }
        assertTrue("ids vacíos en $ids", ids.none { it.isEmpty() })
        assertEquals("ids repetidos en $ids", ids.size, ids.toSet().size)
    }
}

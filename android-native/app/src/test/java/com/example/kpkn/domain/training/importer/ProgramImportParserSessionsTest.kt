package com.example.kpkn.domain.training.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * H3g: sesiones en columnas (HST, PPL), nombres numerados y filas de día dentro de tablas (Scraler, Gymevo). Las hojas
 * están escritas a mano, con nombres inventados; solo imitan la disposición de las plantillas reales.
 */
class ProgramImportParserSessionsTest {

    @Test
    fun `sesiones en columnas tipo HST, dos bloques de semanas, dias y cargas por sesion`() {
        val sheet = ImportSheet(
            name = "Rutina HST",
            rows = hstBlock(firstWeek = 1, firstSession = 1, loadBase = 100) +
                listOf(emptyList<String>()) +
                hstBlock(firstWeek = 3, firstSession = 7, loadBase = 400),
        )

        val draft = ProgramImportParser.parseSheets(listOf(sheet), title = "HST")
        assertUniqueIds(draft)

        assertEquals(listOf(1, 2, 3, 4), draft.weeks.map { it.index })
        draft.weeks.forEach { week ->
            assertEquals(listOf("LUNES", "MIÉRCOLES", "VIERNES"), week.days.map { it.name })
            week.days.forEach { day ->
                assertEquals(listOf("Sentadilla libre", "Peso muerto", "Curl femoral"), day.exercises.map { it.rawName })
            }
        }
        // Cada grupo es un día de una semana: la carga sube 10 kg por sesión dentro de cada bloque.
        assertEquals(
            listOf(
                listOf(100.0, 110.0, 120.0),
                listOf(130.0, 140.0, 150.0),
                listOf(400.0, 410.0, 420.0),
                listOf(430.0, 440.0, 450.0),
            ),
            draft.weeks.map { week -> week.days.map { it.exercises.first().loadKg } },
        )
        val curl = draft.weeks.first().days.first().exercises.last()
        assertEquals(2, curl.sets)
        assertEquals(15, curl.repsMin)
        assertEquals(15, curl.repsMax)
        assertEquals("Bloque A1", curl.notes)
        assertEquals(emptyList<String>(), draft.warningMessages)
    }

    @Test
    fun `sesiones tipo PPL, la hoja es el dia y cada sesion es una semana con la carga de su grupo`() {
        val sessionStarts = listOf(3, 9, 15)
        val header = rowOf(
            listOf(0 to "Exercise", 1 to "Sets", 2 to "Reps / Scheme") +
                sessionStarts.flatMap { start ->
                    listOf(
                        start to "Weight",
                        start + 1 to "AMRAP Reps",
                        start + 2 to "Reps S1",
                        start + 3 to "Reps S2",
                        start + 4 to "Reps S3",
                        start + 5 to "Status",
                    )
                },
        )
        val sheet = ImportSheet(
            name = "Mon Pull",
            rows = listOf(
                rowOf(listOf(0 to "Mon Pull")),
                rowOf(sessionStarts.mapIndexed { index, start -> start to "Session ${index + 1}" }),
                header,
                rowOf(listOf(0 to "Deadlift", 1 to "1", 2 to "1x5+", 3 to "135", 9 to "135", 15 to "135")),
                rowOf(listOf(0 to "Pulldowns / Pullups / Chinups", 1 to "3", 2 to "3x8-12")),
            ),
        )

        val draft = ProgramImportParser.parseSheets(listOf(sheet), title = "PPL")
        assertUniqueIds(draft)

        assertEquals(listOf(1, 2, 3), draft.weeks.map { it.index })
        draft.weeks.forEach { week ->
            assertEquals(listOf("Mon Pull"), week.days.map { it.name })
            assertEquals(listOf("Deadlift", "Pulldowns / Pullups / Chinups"), week.days.single().exercises.map { it.rawName })
        }
        val deadlift = draft.weeks.map { it.days.single().exercises.first() }
        assertEquals(listOf(135.0, 135.0, 135.0), deadlift.map { it.loadKg })
        assertEquals(listOf(1, 1, 1), deadlift.map { it.sets })
        assertEquals(listOf(5, 5, 5), deadlift.map { it.repsMin })
        assertTrue(deadlift.all { it.notes == "AMRAP: última serie, 5 o más repeticiones." })
        val pulldowns = draft.weeks.map { it.days.single().exercises.last() }
        assertEquals(listOf(3, 3, 3), pulldowns.map { it.sets })
        assertEquals(listOf(8, 8, 8), pulldowns.map { it.repsMin })
        assertEquals(listOf(12, 12, 12), pulldowns.map { it.repsMax })
        assertEquals(listOf(null, null, null), pulldowns.map { it.loadKg })
        assertEquals(emptyList<String>(), draft.warningMessages)
    }

    @Test
    fun `filas de dia dentro de una tabla abren un dia con su foco y no son ejercicios ni avisos`() {
        val sheet = ImportSheet(
            name = "Plan",
            rows = listOf(
                listOf("Ejercicio", "Grupo", "Series", "Reps", "Descanso"),
                listOf("Día 1:", "Torso A", "", "", ""),
                listOf("Press banca", "", "3", "6-8", "2 min"),
                listOf("Remo", "", "3", "8-10", "90 s"),
                listOf("Día 2:", "Pierna A", "", "", ""),
                listOf("Sentadilla", "", "3", "6-8", "3 min"),
            ),
        )

        val draft = ProgramImportParser.parseSheets(listOf(sheet), title = "Scraler")

        val days = draft.weeks.single().days
        assertEquals(listOf("Día 1 — Torso A", "Día 2 — Pierna A"), days.map { it.name })
        assertEquals(listOf("Press banca", "Remo"), days[0].exercises.map { it.rawName })
        assertEquals(listOf("Sentadilla"), days[1].exercises.map { it.rawName })
        assertEquals(120, days[0].exercises[0].restSeconds)
        assertEquals(emptyList<String>(), draft.warningMessages)
    }

    @Test
    fun `columna de dia con fila de descanso no da aviso de fila sin nombre`() {
        val sheet = ImportSheet(
            name = "Ejemplo",
            rows = listOf(
                listOf("Día", "Entrenamiento", "Ejercicio", "Series", "Repeticiones", "Descanso"),
                listOf("Lunes", "Torso A", "Press banca", "4", "6-10", "3 min"),
                listOf("Miércoles", "Descanso", "", "", "", ""),
                listOf("Jueves", "Torso B", "Remo con barra", "4", "8-10", "2 min"),
            ),
        )

        val draft = ProgramImportParser.parseSheets(listOf(sheet), title = "Gymevo")

        assertEquals(listOf("Lunes", "Jueves"), draft.weeks.single().days.map { it.name })
        assertEquals(listOf(ImportWarningKind.EMPTY_DAY), draft.warnings.map { it.kind })
    }

    @Test
    fun `nombres numerados pierden el enumerador y la etiqueta de bloque pasa a nota`() {
        val sheet = ImportSheet(
            name = "Plan",
            rows = listOf(
                listOf("Ejercicio", "Series", "Reps", "Peso"),
                listOf("1. Sentadilla libre", "3", "5", "100"),
                listOf("A1. Remo con barra", "3", "8", "60"),
                listOf("12) Curl", "3", "10", "20"),
                listOf("1) Press 3x8"),
            ),
        )

        val draft = ProgramImportParser.parseSheets(listOf(sheet), title = "Numerados")

        val exercises = draft.weeks.single().days.single().exercises
        assertEquals(listOf("Sentadilla libre", "Remo con barra", "Curl", "Press"), exercises.map { it.rawName })
        assertEquals(listOf(null, "Bloque A1", null, null), exercises.map { it.notes })
        assertEquals(3, exercises[3].sets)
        assertEquals(8, exercises[3].repsMin)
    }

    @Test
    fun `una tabla normal sin repeticion de grupos no se trata como sesiones`() {
        val sheet = ImportSheet(
            name = "Plan",
            rows = listOf(
                listOf("Ejercicio", "Series", "Reps", "Peso", "Descanso"),
                listOf("Press banca", "4", "6-8", "80", "2 min"),
                listOf("Remo", "4", "8", "60", "90 s"),
            ),
        )

        val draft = ProgramImportParser.parseSheets(listOf(sheet), title = "Tabla")

        val day = draft.weeks.single().days.single()
        assertEquals("Plan", day.name)
        assertEquals(listOf("Press banca", "Remo"), day.exercises.map { it.rawName })
        assertEquals(listOf(80.0, 60.0), day.exercises.map { it.loadKg })
        assertEquals(emptyList<String>(), draft.warningMessages)
    }

    @Test
    fun `una fila con semana propia cambia la semana del bloque y la notacion puede venir en la columna de series`() {
        val sheet = ImportSheet(
            name = "Plan",
            rows = listOf(
                listOf("", "Mon", "", "", "Tue", "", ""),
                listOf("", "Lift", "Sets*Reps", "Weight", "Lift", "Sets*Reps", "Weight"),
                listOf("Week 1", "Bench", "1*2-5", "100", "Row", "1*2-5", "80"),
                listOf("Week 2", "Bench", "1*3-6", "105", "Row", "1*3-6", "85"),
            ),
        )

        val draft = ProgramImportParser.parseSheets(listOf(sheet), title = "Semanas")
        assertUniqueIds(draft)

        assertEquals(listOf(1, 2), draft.weeks.map { it.index })
        draft.weeks.forEach { week -> assertEquals(listOf("Mon", "Tue"), week.days.map { it.name }) }
        val bench = draft.weeks.map { it.days[0].exercises.single() }
        assertEquals(listOf("Bench", "Bench"), bench.map { it.rawName })
        assertEquals(listOf(100.0, 105.0), bench.map { it.loadKg })
        assertEquals(listOf(2, 3), bench.map { it.repsMin })
        assertEquals(listOf(5, 6), bench.map { it.repsMax })
        assertEquals(listOf(80.0, 85.0), draft.weeks.map { it.days[1].exercises.single().loadKg })
        assertEquals(emptyList<String>(), draft.warningMessages)
    }

    private val hstNames = listOf("1. Sentadilla libre", "2. Peso muerto", "A1. Curl femoral")

    /** Columna del primer papel del grupo [index]: tres columnas base y dos por grupo. */
    private fun group(index: Int): Int = 3 + 2 * index

    /** Fila con esas celdas en sus columnas; lo que no se nombra va vacío. */
    private fun rowOf(cells: List<Pair<Int, String>>): List<String> {
        val width = (cells.maxOfOrNull { it.first } ?: -1) + 1
        return List(width) { column -> cells.firstOrNull { it.first == column }?.second.orEmpty() }
    }

    /** Un bloque HST: dos semanas, tres días por semana, sesiones y seis grupos «Series x reps» + «Peso (kg)». */
    private fun hstBlock(firstWeek: Int, firstSession: Int, loadBase: Int): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        rows += rowOf(listOf(0 to "Rutina", group(0) to "Semana $firstWeek", group(3) to "Semana ${firstWeek + 1}"))
        rows += rowOf(
            listOf(
                group(0) to "LUNES", group(1) to "MIÉRCOLES", group(2) to "VIERNES",
                group(3) to "LUNES", group(4) to "MIÉRCOLES", group(5) to "VIERNES",
            ),
        )
        rows += rowOf(
            listOf(1 to "15RM actual", 2 to "Incremento deseado") +
                (0 until 6).map { group(it) to "Sesión ${firstSession + it}" },
        )
        rows += rowOf((0 until 6).flatMap { listOf(group(it) to "Series x reps", group(it) + 1 to "Peso (kg)") })
        hstNames.forEachIndexed { index, name ->
            rows += rowOf(
                listOf(0 to name, 1 to "260", 2 to "10") +
                    (0 until 6).flatMap { g ->
                        listOf(group(g) to "2 x 15", group(g) + 1 to "${loadBase + 100 * index + 10 * g}")
                    },
            )
        }
        return rows
    }

    /** Invariante: ningún día ni ejercicio se queda sin id y todos los ids del borrador son distintos entre sí. */
    private fun assertUniqueIds(draft: ImportedProgramDraft) {
        val ids = draft.weeks.flatMap { week -> week.days.flatMap { day -> listOf(day.id) + day.exercises.map { it.id } } }
        assertTrue("ids vacíos en $ids", ids.none { it.isEmpty() })
        assertEquals("ids repetidos en $ids", ids.size, ids.toSet().size)
    }
}

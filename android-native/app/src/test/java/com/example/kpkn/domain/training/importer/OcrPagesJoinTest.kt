package com.example.kpkn.domain.training.importer

import org.junit.Assert.assertEquals
import org.junit.Test

/** Muebles de página (H4d): lo que se repite en las páginas sale, pero el contenido del programa no se pierde. */
class OcrPagesJoinTest {

    /** Cuatro páginas de un PDF de ejercicios: marca arriba, «DÍA 1», una lista o un detalle y un pie numerado. */
    private fun templePages(): List<List<List<String>>> = listOf(
        listOf(
            listOf("TEMPLE OF FITNESS"), listOf("DÍA 1"), listOf("ADDUCTORES"), listOf("ABDUCTORES"),
            listOf("AGR Templo del fitness Página 1"),
        ),
        listOf(
            listOf("4 TEMPLE OF FITNESS"), listOf("ADDUCTORES"), listOf("DÍA 1"), listOf("4x 15-12-10-8"),
            listOf("AGR Templo del fitness Página 2"),
        ),
        listOf(
            listOf("TEMPLE OF FITNESS"), listOf("ABDUCTORES"), listOf("DÍA 1"), listOf("4 x 15-12-10-8"),
            listOf("AGR Templo del fitness Página 3"),
        ),
        listOf(
            listOf("TEMPLE OF FITNESS"), listOf("EXTENSIONES"), listOf("DÍA 1"), listOf("3x 12-10-8"),
            listOf("AGR Templo del fitness Página 4"),
        ),
    )

    @Test
    fun `la marca y el pie repetidos en las cuatro paginas desaparecen`() {
        val rows = OcrLayout.joinPages(templePages()).rows

        assertEquals(emptyList<List<String>>(), rows.filter { row -> row.any { it.contains("TEMPLE") || it.startsWith("AGR") } })
    }

    @Test
    fun `el dia repetido y la cabecera de tabla se quedan en cada pagina`() {
        val names = listOf("Remo", "Curl", "Prensa", "Zancada")
        val pages = List(4) { page ->
            listOf(
                listOf("Ejercicio", "Series", "Reps"),
                listOf("DÍA 1"),
                listOf(names[page], "3", "10"),
                listOf("AGR Página ${page + 1}"),
            )
        }

        val rows = OcrLayout.joinPages(pages).rows

        assertEquals(4, rows.count { it == listOf("Ejercicio", "Series", "Reps") })
        assertEquals(4, rows.count { it == listOf("DÍA 1") })
        assertEquals(names, rows.filter { it.size == 3 && it[1] == "3" }.map { it.first() })
    }

    @Test
    fun `un programa semanal con las mismas filas en cada pagina no pierde ninguna`() {
        val weekPage = listOf(
            listOf("Press banca 4x8"),
            listOf("Sentadilla", "4", "8", "100"),
            listOf("Curl"),
            listOf("4x8"),
            listOf("TREN SUPERIOR"),
            listOf("Remo", "3", "10"),
        )

        val joined = OcrLayout.joinPages(List(4) { weekPage })

        assertEquals(24, joined.rows.size)
        assertEquals(listOf(0, 6, 12, 18), joined.pageStarts)
    }

    @Test
    fun `con las paginas unidas, un solo Dia 1 con sus ejercicios, rango de piramide y sin la marca`() {
        val joined = OcrLayout.joinPages(templePages())

        val draft = ProgramImportParser.parseSheets(
            listOf(ImportSheet(name = "Tabla", rows = joined.rows, pageStarts = joined.pageStarts)),
            title = "Templo",
        )

        val week = draft.weeks.single()
        assertEquals(listOf("DÍA 1"), week.days.map { it.name })
        val exercises = week.days.single().exercises
        assertEquals(listOf("ADDUCTORES", "ABDUCTORES", "EXTENSIONES"), exercises.map { it.rawName })
        assertEquals(listOf(8, 8, 8), exercises.map { it.repsMin })
        assertEquals(listOf(15, 15, 12), exercises.map { it.repsMax })
        assertEquals(listOf("Pirámide: 15-12-10-8.", "Pirámide: 15-12-10-8.", "Pirámide: 12-10-8."), exercises.map { it.notes })
        assertEquals(listOf(4, 4, 3), exercises.map { it.sets })
    }
}

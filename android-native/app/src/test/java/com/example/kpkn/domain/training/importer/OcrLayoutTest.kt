package com.example.kpkn.domain.training.importer

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrLayoutTest {

    // Tabla de tres columnas: nombre, series y repeticiones (píxeles de la página de 1600 px).
    private val nameX = 100
    private val seriesX = 700
    private val repsX = 1100

    /** Palabra de 20 px por carácter y [height] px de alto. */
    private fun word(text: String, left: Int, top: Int, height: Int = 30) =
        OcrWord(text, left, top, left + text.length * 20, top + height)

    private fun page(vararg words: OcrWord, width: Int = 1600) = OcrPage(width, 2000, words.toList())

    @Test
    fun `una tabla de tres columnas con cabecera y cuatro filas sale alineada aunque tiemble en vertical`() {
        val rows = OcrLayout.reconstruct(
            listOf(
                page(
                    word("Ejercicio", nameX, 100), word("Series", seriesX, 103), word("Reps", repsX, 97),
                    word("Sentadilla", nameX, 183), word("4", seriesX, 180), word("8", repsX, 177),
                    word("Prensa", nameX, 257), word("3", seriesX, 260), word("10", repsX, 263),
                    word("Remo", nameX, 343), word("3", seriesX, 340), word("12", repsX, 337),
                    word("Curl", nameX, 418), word("3", seriesX, 420), word("10", repsX, 417),
                ),
            ),
        )
        assertEquals(
            listOf(
                listOf("Ejercicio", "Series", "Reps"),
                listOf("Sentadilla", "4", "8"),
                listOf("Prensa", "3", "10"),
                listOf("Remo", "3", "12"),
                listOf("Curl", "3", "10"),
            ),
            rows,
        )
    }

    @Test
    fun `una celda de varias palabras se une con espacio`() {
        val rows = OcrLayout.reconstruct(
            listOf(
                page(
                    word("Ejercicio", nameX, 100), word("Series", seriesX, 100), word("Reps", repsX, 100),
                    word("Press", nameX, 180), word("de", nameX + 120, 180), word("banca", nameX + 160, 180),
                    word("inclinado", nameX + 300, 180), word("4", seriesX, 180), word("8", repsX, 180),
                    word("Remo", nameX, 260), word("3", seriesX, 260), word("10", repsX, 260),
                ),
            ),
        )
        assertEquals(
            listOf(
                listOf("Ejercicio", "Series", "Reps"),
                listOf("Press de banca inclinado", "4", "8"),
                listOf("Remo", "3", "10"),
            ),
            rows,
        )
    }

    @Test
    fun `una fila con una celda vacía en medio conserva su columna`() {
        val rows = OcrLayout.reconstruct(
            listOf(
                page(
                    word("Ejercicio", nameX, 100), word("Series", seriesX, 100), word("Reps", repsX, 100),
                    word("Dominadas", nameX, 180), word("10", repsX, 180),
                    word("Remo", nameX, 260), word("3", seriesX, 260), word("10", repsX, 260),
                    word("Curl", nameX, 340), word("3", seriesX, 340), word("12", repsX, 340),
                ),
            ),
        )
        assertEquals(
            listOf(
                listOf("Ejercicio", "Series", "Reps"),
                listOf("Dominadas", "", "10"),
                listOf("Remo", "3", "10"),
                listOf("Curl", "3", "12"),
            ),
            rows,
        )
    }

    @Test
    fun `dos páginas salen seguidas, en orden y sin fila vacía entre ellas`() {
        val first = page(
            word("Ejercicio", nameX, 100), word("Series", seriesX, 100), word("Reps", repsX, 100),
            word("Remo", nameX, 180), word("3", seriesX, 180), word("10", repsX, 180),
            word("Curl", nameX, 260), word("3", seriesX, 260), word("12", repsX, 260),
        )
        val second = page(
            word("Ejercicio", nameX, 100), word("Series", seriesX, 100), word("Reps", repsX, 100),
            word("Prensa", nameX, 180), word("4", seriesX, 180), word("8", repsX, 180),
            word("Sentadilla", nameX, 260), word("4", seriesX, 260), word("6", repsX, 260),
        )
        assertEquals(
            listOf(
                listOf("Ejercicio", "Series", "Reps"),
                listOf("Remo", "3", "10"),
                listOf("Curl", "3", "12"),
                listOf("Ejercicio", "Series", "Reps"),
                listOf("Prensa", "4", "8"),
                listOf("Sentadilla", "4", "6"),
            ),
            OcrLayout.reconstruct(listOf(first, second)),
        )
    }

    @Test
    fun `una línea de día encima de la tabla va en su fila y los signos sueltos no entran`() {
        val rows = OcrLayout.reconstruct(
            listOf(
                page(
                    word("Día", nameX, 60), word("1", nameX + 80, 60), word("—", nameX + 120, 60),
                    word("Pierna", nameX + 150, 60),
                    word("Ejercicio", nameX, 100), word("Series", seriesX, 100), word("Reps", repsX, 100),
                    word("Sentadilla", nameX, 180), word("4", seriesX, 180), word("8", repsX, 180),
                    word("|", nameX - 30, 260), word("Remo", nameX, 260), word("3", seriesX, 260), word("12", repsX, 260),
                ),
            ),
        )
        assertEquals(
            listOf(
                listOf("Día 1 Pierna", "", ""),
                listOf("Ejercicio", "Series", "Reps"),
                listOf("Sentadilla", "4", "8"),
                listOf("Remo", "3", "12"),
            ),
            rows,
        )
    }

    @Test
    fun `el temblor de un 1 % del ancho en los bordes izquierdos no separa las columnas`() {
        val rows = OcrLayout.reconstruct(
            listOf(
                page(
                    word("Ejercicio", nameX + 12, 100), word("Series", seriesX - 9, 100), word("Reps", repsX + 16, 100),
                    word("Sentadilla", nameX - 16, 180), word("4", seriesX + 10, 180), word("8", repsX - 12, 180),
                    word("Prensa", nameX + 8, 260), word("3", seriesX - 16, 260), word("10", repsX + 4, 260),
                    word("Remo", nameX, 340), word("3", seriesX + 16, 340), word("12", repsX - 16, 340),
                ),
            ),
        )
        assertEquals(
            listOf(
                listOf("Ejercicio", "Series", "Reps"),
                listOf("Sentadilla", "4", "8"),
                listOf("Prensa", "3", "10"),
                listOf("Remo", "3", "12"),
            ),
            rows,
        )
    }
}

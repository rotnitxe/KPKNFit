package com.example.kpkn.debug

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ImportOcrHarnessTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `el arnes acepta formatos que lee el importador y rechaza libros`() {
        listOf("pdf", "jpg", "jpeg", "png", "webp", "heic", "JPG").forEach { extension ->
            val file = File(temporaryFolder.root, "sample.$extension").apply { writeBytes(byteArrayOf(1)) }
            assertTrue("debe aceptar .$extension", isOcrHarnessCorpusFile(file))
        }

        val workbook = File(temporaryFolder.root, "rutina.xlsx").apply { writeBytes(byteArrayOf(1)) }
        assertFalse("no debe tratar libros como imágenes OCR", isOcrHarnessCorpusFile(workbook))

        val folder = File(temporaryFolder.root, "carpeta.pdf").apply { mkdir() }
        assertFalse("no debe tratar directorios como documentos", isOcrHarnessCorpusFile(folder))
    }

    @Test
    fun `transcripcion igual compara filas celdas palabras y caracteres`() {
        val expected = """
            --- página 1 ---
            Día 1 | Sentadilla | 3 | 5 | 100 kg
            Press banca | 4 | 8 | 60 kg
        """.trimIndent()
        val recognized = """
            Dia 1 | Sentadilla | 3 | 5 | 100 kg
            --- página 1 ---
            Press banca | 4 | 8 | 60 kg
        """.trimIndent()

        val metrics = compareOcrTranscripts(expected, recognized)

        assertEquals(2, metrics.expectedRows)
        assertEquals(2, metrics.recognizedRows)
        assertEquals(2, metrics.exactRows)
        assertEquals(100.0, metrics.exactRowsPct!!, 0.001)
        assertEquals(100.0, metrics.exactCellsPct!!, 0.001)
        assertEquals(0.0, metrics.wordErrorRatePct!!, 0.001)
        assertEquals(0.0, metrics.characterErrorRatePct!!, 0.001)
    }

    @Test
    fun `errores de lectura y filas extra quedan reflejados`() {
        val metrics = compareOcrTranscripts(
            expected = "Sentadilla | 3 | 5\nPress banca | 4 | 8",
            recognized = "Sentadilla | 3 | 5\nPress banea | 4 | 8\nRemo | 2 | 10",
        )

        assertEquals(2, metrics.expectedRows)
        assertEquals(3, metrics.recognizedRows)
        assertEquals(1, metrics.exactRows)
        assertEquals(100.0 / 3.0, metrics.exactRowsPct!!, 0.001)
        assertTrue(metrics.wordErrorRatePct!! > 0.0)
        assertTrue(metrics.characterErrorRatePct!! > 0.0)
    }

    @Test
    fun `filas correctas posteriores a una fila extra se alinean para las metricas`() {
        val metrics = compareOcrTranscripts(
            expected = "Sentadilla | 3 | 5\nPress banca | 4 | 8",
            recognized = "Rutina | Semana 1\nSentadilla | 3 | 5\nPress banca | 4 | 8",
        )

        assertEquals(2, metrics.exactRows)
        assertEquals(2.0 / 3.0 * 100.0, metrics.exactRowsPct!!, 0.001)
        assertEquals(6, metrics.exactCells)
        assertEquals(75.0, metrics.exactCellsPct!!, 0.001)
    }

    @Test
    fun `celdas correctas posteriores a una celda extra quedan alineadas`() {
        val metrics = compareOcrTranscripts(
            expected = "Sentadilla | 3 | 5",
            recognized = "Día 1 | Sentadilla | 3 | 5",
        )

        assertEquals(0, metrics.exactRows)
        assertEquals(3, metrics.exactCells)
        assertEquals(75.0, metrics.exactCellsPct!!, 0.001)
    }

    @Test
    fun `la transcripcion esperada se guarda al lado con el nombre completo de entrada`() {
        val source = File(temporaryFolder.root, "routine.jpg")

        assertEquals("routine.jpg.expected.txt", expectedOcrTranscriptFile(temporaryFolder.root, source).name)
    }
}

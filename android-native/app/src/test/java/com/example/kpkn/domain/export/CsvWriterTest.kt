package com.example.kpkn.domain.export

import com.example.kpkn.data.importer.CsvReader
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class CsvWriterTest {
    @Test fun `BOM CRLF comas comillas acentos y notas hacen ida y vuelta`() {
        val text = "Tazón, \"especial\"\nsegunda línea"
        val bytes = CsvWriter.bytes(listOf("nombre", "valor", "nota"), listOf(listOf(CsvCell.Text("Piñón"), CsvCell.Number(1.23456789012345), CsvCell.Text(text))))
        assertArrayEquals(byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()), bytes.take(3).toByteArray())
        val csv = bytes.toString(Charsets.UTF_8)
        assertTrue(csv.endsWith("\r\n"))
        val rows = CsvReader.parse(csv, "CSV").rows
        assertEquals(listOf("nombre", "valor", "nota"), rows[0])
        assertEquals("Piñón", rows[1][0]); assertEquals(1.23456789012345, rows[1][1].toDouble(), 0.0)
        assertEquals(text, rows[1][2])
    }
    @Test fun `protege formulas tambien tras espacios sin alterar numeros negativos legitimos`() {
        val inputs = listOf("=1+1", "+SUM(A1)", "-CMD()", "@SUM(A1)", "\t=1+1", " -CMD()", "-1,5", "+1.5", "texto")
        val bytes = CsvWriter.bytes(listOf("dato"), inputs.map { listOf(CsvCell.Text(it)) })
        val rows = CsvReader.parse(bytes.toString(Charsets.UTF_8), "CSV").rows.drop(1).map { it.single() }
        inputs.indices.forEach { i -> assertEquals(if (i < 6) "'${inputs[i]}" else inputs[i], rows[i]) }
        val numeric = CsvWriter.bytes(listOf("dato"), listOf(listOf(CsvCell.Number(-1.5))))
        assertEquals("-1.5", CsvReader.parse(numeric.toString(Charsets.UTF_8), "CSV").rows[1][0])
    }
    @Test fun `datos vacios conservan cabecera y decimales nunca dependen del idioma`() {
        assertEquals(listOf(listOf("uno", "dos")), CsvReader.parse(CsvWriter.bytes(listOf("uno", "dos"), emptyList()).toString(Charsets.UTF_8), "CSV").rows)
        val prior = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("es-CL"))
            val bytes = CsvWriter.bytes(listOf("valor"), listOf(listOf(CsvCell.Number(1.5))))
            assertEquals("1.5", CsvReader.parse(bytes.toString(Charsets.UTF_8), "CSV").rows[1][0])
        } finally { Locale.setDefault(prior) }
    }
    @Test fun `rechaza numeros no finitos y filas que no encajan`() {
        assertTrue(runCatching { CsvWriter.bytes(listOf("v"), listOf(listOf(CsvCell.Number(Double.NaN)))) }.isFailure)
        assertTrue(runCatching { CsvWriter.bytes(listOf("v"), listOf(emptyList())) }.isFailure)
    }
}

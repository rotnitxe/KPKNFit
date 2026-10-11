package com.example.kpkn.data.importer

import org.junit.Assert.assertEquals
import org.junit.Test

class OdsReaderTest {

    @Test
    fun `lee dos hojas con filas y columnas repetidas, porcentaje, hora y decimales`() {
        val day = odsRow(
            odsCell(text = "Ejercicio"),
            odsCell(text = "Porcentaje"),
            odsCell(text = "Descanso"),
            odsCell(text = "Peso"),
        ) + odsRow(
            odsCell("office:value-type='string'", "Press banca"),
            odsCell("office:value-type='percentage' office:value='0.75'", "75%"),
            odsCell("office:value-type='time' office:time-value='PT0H2M0S'", "0:02:00"),
            odsCell("office:value-type='float' office:value='80.50'", "80.50"),
            attributes = "table:number-rows-repeated='2'",
        ) + odsRow(
            odsCell("office:value-type='string'", "Sentadilla"),
            odsCell("office:value-type='percentage' office:value='0.125'", "12.5%"),
            odsCell("office:value-type='time' office:time-value='PT1H5M30S'", "1:05:30"),
            odsCell("office:value-type='float' office:value='4'", "4"),
        ) + odsRow(
            odsCell("table:number-columns-repeated='3' office:value-type='string'", "Serie"),
        )
        val other = odsRow(odsCell(text = "Fila"), odsCell("table:number-columns-repeated='16384'")) +
            odsRow(odsCell("table:number-columns-repeated='1024'"), attributes = "table:number-rows-repeated='1048576'")

        val sheets = OdsReader.read(odsFile("Día 1" to day, "Hoja dos" to other))

        assertEquals(listOf("Día 1", "Hoja dos"), sheets.map { it.name })
        assertEquals(
            listOf(
                listOf("Ejercicio", "Porcentaje", "Descanso", "Peso"),
                listOf("Press banca", "75%", "2:00", "80.5"),
                listOf("Press banca", "75%", "2:00", "80.5"),
                listOf("Sentadilla", "12.5%", "1:05:30", "4"),
                listOf("Serie", "Serie", "Serie", ""),
            ),
            sheets[0].rows,
        )
        // Las 16 384 columnas vacías repetidas no se expanden, y las filas vacías del final no añaden nada.
        assertEquals(listOf(listOf("Fila")), sheets[1].rows)
    }

    @Test
    fun `mas de 40 columnas, un hueco de filas que pasa el tope y mas de 20 hojas se recortan con una nota`() {
        val wide = odsRow(odsCell("table:number-columns-repeated='100' office:value-type='string'", "x"))
        val gap = odsRow(odsCell(text = "A")) +
            odsRow(odsCell(), attributes = "table:number-rows-repeated='2000'") +
            odsRow(odsCell(text = "B"))
        val manySheets = Array(21) { "Hoja ${it + 1}" to odsRow(odsCell(text = "A")) }

        val wideSheet = OdsReader.read(odsFile("Hoja 1" to wide)).single()
        assertEquals(listOf(List(40) { "x" }), wideSheet.rows)
        assertEquals(listOf(TrimNotes.COLUMNS), wideSheet.notes)

        val gapSheet = OdsReader.read(odsFile("Hueco" to gap)).single()
        assertEquals(listOf(listOf("A")), gapSheet.rows)
        assertEquals(listOf(TrimNotes.ROWS), gapSheet.notes)

        val many = OdsReader.read(odsFile(*manySheets))
        assertEquals((1..20).map { "Hoja $it" }, many.map { it.name })
        assertEquals(listOf("El libro tiene 21 hojas; se leyeron las 20 primeras."), many.first().notes)
        assertEquals(
            "No es una hoja de cálculo OpenDocument (.ods) válida",
            rejectionOf { OdsReader.read(zipFile("notas.txt" to "hola")) },
        )
    }

    @Test
    fun `una hoja oculta de un ods no se devuelve`() {
        val content = """<?xml version="1.0" encoding="UTF-8"?>""" +
            "<office:document-content xmlns:office='urn:oasis:names:tc:opendocument:xmlns:office:1.0' " +
            "xmlns:table='urn:oasis:names:tc:opendocument:xmlns:table:1.0' " +
            "xmlns:style='urn:oasis:names:tc:opendocument:xmlns:style:1.0' " +
            "xmlns:text='urn:oasis:names:tc:opendocument:xmlns:text:1.0'><office:automatic-styles>" +
            "<style:style style:name='ta_oculta' style:family='table'><style:table-properties table:display='false'/></style:style>" +
            "<style:style style:name='ta_visible' style:family='table'><style:table-properties table:display='true'/></style:style>" +
            "</office:automatic-styles><office:body><office:spreadsheet>" +
            "<table:table table:name='Oculta' table:style-name='ta_oculta'>" + odsRow(odsCell(text = "secreto")) + "</table:table>" +
            "<table:table table:name='Visible' table:style-name='ta_visible'>" + odsRow(odsCell(text = "Press banca")) + "</table:table>" +
            "</office:spreadsheet></office:body></office:document-content>"

        val sheets = OdsReader.read(zipFile("mimetype" to "application/vnd.oasis.opendocument.spreadsheet", "content.xml" to content))

        assertEquals(listOf("Visible"), sheets.map { it.name })
        assertEquals(listOf(listOf("Press banca")), sheets.single().rows)
    }
}

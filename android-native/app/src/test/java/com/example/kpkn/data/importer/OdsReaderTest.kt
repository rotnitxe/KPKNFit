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
    fun `más de 40 columnas con datos, más de 20 hojas o un hueco de filas que pasa el tope se rechazan`() {
        val wide = odsRow(odsCell("table:number-columns-repeated='100' office:value-type='string'", "x"))
        val gap = odsRow(odsCell(text = "A")) +
            odsRow(odsCell(), attributes = "table:number-rows-repeated='2000'") +
            odsRow(odsCell(text = "B"))
        val manySheets = Array(21) { "Hoja ${it + 1}" to odsRow(odsCell(text = "A")) }

        assertEquals("La hoja «Hoja 1» tiene más de 40 columnas", rejectionOf { OdsReader.read(odsFile("Hoja 1" to wide)) })
        assertEquals("La hoja «Hueco» tiene más de 2000 filas", rejectionOf { OdsReader.read(odsFile("Hueco" to gap)) })
        assertEquals("El libro tiene más de 20 hojas", rejectionOf { OdsReader.read(odsFile(*manySheets)) })
        assertEquals(
            "No es una hoja de cálculo OpenDocument (.ods) válida",
            rejectionOf { OdsReader.read(zipFile("notas.txt" to "hola")) },
        )
    }
}

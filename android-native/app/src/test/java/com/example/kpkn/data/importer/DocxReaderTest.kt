package com.example.kpkn.data.importer

import org.junit.Assert.assertEquals
import org.junit.Test

class DocxReaderTest {

    @Test
    fun `párrafos y tablas en orden, con la combinación horizontal en una celda y la vertical que continúa vacía`() {
        val bytes = docxFile(
            docxParagraph("Día 1 — Pierna") +
                docxTable(
                    docxRow(docxCell("Ejercicio"), docxCell("Series"), docxCell("Reps")),
                    docxRow(docxCell("Sentadilla"), docxCell("4"), docxCell("8")),
                    docxRow(docxCell("Peso muerto", "<w:tcPr><w:gridSpan w:val='2'/></w:tcPr>"), docxCell("10")),
                    docxRow(docxCell("Remo", "<w:tcPr><w:vMerge w:val='restart'/></w:tcPr>"), docxCell("3"), docxCell("12")),
                    docxRow(docxCell("repetido", "<w:tcPr><w:vMerge/></w:tcPr>"), docxCell("3"), docxCell("12")),
                ) +
                docxParagraph("   "),
        )

        val sheet = DocxReader.read(bytes, "programa")

        assertEquals("programa", sheet.name)
        assertEquals(
            listOf(
                listOf("Día 1 — Pierna", "", ""),
                listOf("Ejercicio", "Series", "Reps"),
                listOf("Sentadilla", "4", "8"),
                listOf("Peso muerto", "10", ""),
                listOf("Remo", "3", "12"),
                listOf("", "3", "12"),
            ),
            sheet.rows,
        )
    }

    @Test
    fun `un ZIP sin documento de Word, un XML roto y más de 40 columnas se rechazan`() {
        assertEquals(
            "No es un documento de Word (.docx) válido",
            rejectionOf { DocxReader.read(zipFile("notas.txt" to "hola"), "x") },
        )
        assertEquals(
            "El documento contiene XML no válido",
            rejectionOf { DocxReader.read(zipFile("word/document.xml" to "<w:document><w:body>"), "x") },
        )
        val wide = docxFile(docxTable(docxRow(*Array(41) { docxCell("c$it") })))
        assertEquals("La hoja «ancha» tiene más de 40 columnas", rejectionOf { DocxReader.read(wide, "ancha") })
    }
}

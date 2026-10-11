package com.example.kpkn.data.importer

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// Constructores de ZIP, .ods y .docx para las pruebas del importador: se escriben a mano en cada prueba, sin nada de la
// colección real. Los atributos XML van con comillas simples para no escapar las dobles dentro de los textos.

/** Un ZIP con las entradas dadas (nombre → texto UTF-8). */
internal fun zipFile(vararg entries: Pair<String, String>): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        for ((name, content) in entries) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(content.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }
    return out.toByteArray()
}

/** Un .ods: cada par es el nombre de una hoja y sus filas en XML (ver [odsRow]). */
internal fun odsFile(vararg sheets: Pair<String, String>): ByteArray {
    val tables = sheets.joinToString("") { (name, rows) -> "<table:table table:name='$name'>$rows</table:table>" }
    val content = """<?xml version="1.0" encoding="UTF-8"?>""" +
        "<office:document-content xmlns:office='urn:oasis:names:tc:opendocument:xmlns:office:1.0' " +
        "xmlns:table='urn:oasis:names:tc:opendocument:xmlns:table:1.0' " +
        "xmlns:text='urn:oasis:names:tc:opendocument:xmlns:text:1.0'><office:body>" +
        "<office:spreadsheet>$tables</office:spreadsheet></office:body></office:document-content>"
    return zipFile("mimetype" to "application/vnd.oasis.opendocument.spreadsheet", "content.xml" to content)
}

/** Una fila de .ods con sus celdas y sus atributos (p. ej. `table:number-rows-repeated='2'`). */
internal fun odsRow(vararg cells: String, attributes: String = ""): String =
    "<table:table-row $attributes>${cells.joinToString("")}</table:table-row>"

/** Una celda de .ods con sus atributos tal cual; sin [text] es una celda vacía. */
internal fun odsCell(attributes: String = "", text: String? = null): String =
    if (text == null) {
        "<table:table-cell $attributes/>"
    } else {
        "<table:table-cell $attributes><text:p>$text</text:p></table:table-cell>"
    }

/** Un .docx con el cuerpo dado (párrafos y tablas, ver [docxParagraph]). */
internal fun docxFile(body: String): ByteArray {
    val document = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
        "<w:document xmlns:w='http://schemas.openxmlformats.org/wordprocessingml/2006/main'><w:body>$body</w:body></w:document>"
    return zipFile("word/document.xml" to document)
}

/** Un párrafo de texto del cuerpo de un .docx. */
internal fun docxParagraph(text: String): String = "<w:p><w:r><w:t xml:space='preserve'>$text</w:t></w:r></w:p>"

/** Una tabla de .docx con sus filas (ver [docxRow]). */
internal fun docxTable(vararg rows: String): String = "<w:tbl>${rows.joinToString("")}</w:tbl>"

/** Una fila de tabla de .docx con sus celdas. */
internal fun docxRow(vararg cells: String): String = "<w:tr>${cells.joinToString("")}</w:tr>"

/** Una celda de tabla de .docx con un párrafo; [properties] es su `w:tcPr` (combinaciones), si lo tiene. */
internal fun docxCell(text: String, properties: String = ""): String =
    "<w:tc>$properties<w:p><w:r><w:t xml:space='preserve'>$text</w:t></w:r></w:p></w:tc>"

/** El mensaje del [ImportFileException] que lanza [block], o null si no lanza. */
internal fun rejectionOf(block: () -> Unit): String? = try {
    block()
    null
} catch (error: ImportFileException) {
    error.message
}

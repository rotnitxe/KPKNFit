package com.example.kpkn.data.importer

import com.example.kpkn.domain.training.importer.ImportSheet
import java.io.ByteArrayInputStream

/**
 * Elige el lector de un archivo por su firma y, si no basta, por su extensión y su tipo MIME. Un ZIP es un libro .xlsx
 * (con `xl/workbook.xml`), una hoja .ods (con su `mimetype`) o un documento .docx (con `word/document.xml`). Para decidirlo
 * solo se miran los nombres de sus entradas y, si hace falta, su `mimetype` o su `content.xml`; otro ZIP no se lee. Un OLE
 * (.xls o .doc antiguos) y un PDF salen con un mensaje para la persona. El texto va a [TextProgramReader] (md, markdown y
 * txt), a [CsvReader] (csv, tsv y extensiones desconocidas) o a [XlsxReader] (xlsx sin firma ZIP). Cada lector aplica sus
 * propios topes; aquí no se guarda el libro entero.
 */
object ImportFileReader {
    /** Mensaje del tope de 5 MB, el mismo que da la lectura de la app. */
    internal const val TOO_LARGE_MESSAGE = "El archivo es demasiado grande (máx. 5 MB)."
    private const val UNKNOWN_MESSAGE = "No es un archivo que sepamos leer. Usa .xlsx, .ods, .csv, .docx o texto."
    private const val OLD_OFFICE_MESSAGE = "Es un formato antiguo de Office. Guárdalo como .xlsx o .docx y vuelve a intentarlo."
    private const val PDF_MESSAGE = "Para importar un PDF, copia su texto y usa «Pegar texto»."
    private const val TOO_LARGE_ZIP = "El archivo descomprimido es demasiado grande"
    private const val ODS_MIME = "application/vnd.oasis.opendocument.spreadsheet"
    private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    private const val MARKDOWN_MIME = "text/markdown"
    private const val WORKBOOK = "xl/workbook.xml"
    private const val WORD_DOCUMENT = "word/document.xml"
    private const val MIMETYPE = "mimetype"
    private const val CONTENT_XML = "content.xml"

    /** Cabecera local de un ZIP («PK», 3, 4). */
    private val ZIP_SIGNATURE = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    private val OLE_SIGNATURE = byteArrayOf(
        0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte(),
    )
    private val PDF_SIGNATURE = "%PDF".toByteArray(Charsets.US_ASCII)

    /**
     * Hojas de [bytes]. [fileName] aporta la extensión y el título (el nombre sin extensión, que es el nombre de la hoja
     * de texto); [mime] es el tipo que dio el proveedor, o null. Lanza [ImportFileException] con el mensaje para la persona.
     */
    fun read(bytes: ByteArray, fileName: String, mime: String?): List<ImportSheet> {
        if (bytes.size > ImportLimits.MAX_FILE_BYTES) throw ImportFileException(TOO_LARGE_MESSAGE)
        val title = titleOf(fileName)
        return when {
            bytes.hasSignature(ZIP_SIGNATURE) -> readZip(bytes, title)
            bytes.hasSignature(OLE_SIGNATURE) -> throw ImportFileException(OLD_OFFICE_MESSAGE)
            bytes.hasSignature(PDF_SIGNATURE) -> throw ImportFileException(PDF_MESSAGE)
            else -> readText(bytes, title, extensionOf(fileName), mime)
        }
    }

    /** Título de un archivo: su nombre sin la última extensión (vacío si el proveedor no da nombre). */
    internal fun titleOf(fileName: String): String =
        fileName.substringBeforeLast('.', missingDelimiterValue = fileName).trim()

    private fun extensionOf(fileName: String): String =
        fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()

    /**
     * Un ZIP es un .xlsx, un .ods o un .docx según sus entradas; cualquier otro ZIP no se lee. Para decidirlo se miran los
     * nombres y, si hace falta, el `mimetype` (pequeño). Las demás entradas se descartan: el lector elegido vuelve a leer el
     * libro con sus propios topes.
     */
    private fun readZip(bytes: ByteArray, title: String): List<ImportSheet> {
        val archive = OfficeXml.archive(bytes, TOO_LARGE_ZIP) { it == MIMETYPE }
            ?: throw ImportFileException(UNKNOWN_MESSAGE)
        val mimetype = archive.entries[MIMETYPE]?.let { String(it, Charsets.UTF_8).trim() }
        return when {
            WORKBOOK in archive.names -> XlsxReader.read(ByteArrayInputStream(bytes))
            mimetype == ODS_MIME -> OdsReader.read(bytes)
            WORD_DOCUMENT in archive.names -> listOf(DocxReader.read(bytes, title))
            isSpreadsheetContent(bytes, archive.names) -> OdsReader.read(bytes)
            else -> throw ImportFileException(UNKNOWN_MESSAGE)
        }
    }

    /** Un .ods sin `mimetype` se reconoce por un `office:spreadsheet` en su content.xml; ese XML solo se lee en este caso. */
    private fun isSpreadsheetContent(bytes: ByteArray, names: Set<String>): Boolean {
        if (CONTENT_XML !in names) return false
        val content = OfficeXml.entries(bytes, TOO_LARGE_ZIP) { it == CONTENT_XML }?.get(CONTENT_XML) ?: return false
        return String(content, Charsets.ISO_8859_1).contains("office:spreadsheet")
    }

    /** Texto sin firma: se decide por la extensión y, si no hay una conocida, por el tipo MIME. */
    private fun readText(bytes: ByteArray, title: String, extension: String, mime: String?): List<ImportSheet> {
        val text = String(bytes, Charsets.UTF_8)
        return when (extension) {
            "md", "markdown", "txt" -> listOf(TextProgramReader.read(text, title))
            "csv", "tsv" -> listOf(CsvReader.parse(text, title))
            "xlsx" -> XlsxReader.read(ByteArrayInputStream(bytes))
            // Estos formatos tienen firma propia: sin ella no hay nada que leer con seguridad.
            "ods", "docx", "doc", "xls", "pdf" -> throw ImportFileException(UNKNOWN_MESSAGE)
            else -> when (mime) {
                XLSX_MIME -> XlsxReader.read(ByteArrayInputStream(bytes))
                MARKDOWN_MIME -> listOf(TextProgramReader.read(text, title))
                else -> listOf(CsvReader.parse(text, title))
            }
        }
    }

    private fun ByteArray.hasSignature(signature: ByteArray): Boolean =
        size >= signature.size && signature.indices.all { this[it] == signature[it] }
}

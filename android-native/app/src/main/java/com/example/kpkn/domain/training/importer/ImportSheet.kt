package com.example.kpkn.domain.training.importer

import kotlinx.serialization.Serializable

/** Contenido reconocido en una hoja; se conserva para que cada aviso mantenga su origen. */
@Serializable
enum class ImportDocumentType {
    UNKNOWN,
    PASTED_TEXT,
    TEXT_FILE,
    CSV,
    XLS_BIFF8,
    XLSX,
    XLSM,
    ODS,
    DOCX,
    IMAGE,
    PDF,
    URL_HTML,
    URL_IMAGE,
    URL_PDF,
}

/** Ubicación de una fila antes de convertirla en un aviso o ejercicio durable. */
internal data class ImportRowOrigin(
    val documentType: ImportDocumentType = ImportDocumentType.UNKNOWN,
    val sheetName: String? = null,
    val pageNumber: Int? = null,
    val rowNumber: Int? = null,
)

/**
 * Una hoja ya leída (de un .xlsx, un .ods, un CSV, un texto, o de un PDF o una foto con OCR): nombre y filas de celdas en
 * texto. Las celdas vacías son "". [notes] recoge lo que el lector recortó u omitió de la hoja (columnas o filas de más,
 * el total de hojas del libro) y lo que la persona debe leer antes de guardar (p. ej. «Lo leímos de una imagen…»); el
 * analizador lo avisa como `SHEET_NOTE`. Vive en `domain/` para que el analizador no dependa de la capa de datos.
 */
data class ImportSheet(
    val name: String,
    val rows: List<List<String>>,
    val notes: List<String> = emptyList(),
    /** Solo en un PDF leído con OCR: las filas de cada página antes de quitar los muebles. Lo usa el arnés de depuración. */
    val ocrPages: List<List<List<String>>> = emptyList(),
    /**
     * Índice en [rows] donde empieza cada página, después de quitar los muebles. Lista no vacía = hoja de OCR: ahí la cabecera
     * de cada página repite el día y un texto corto no cambia el día abierto.
     */
    val pageStarts: List<Int> = emptyList(),
    /** Números de línea originales para lectores de texto que eliminan separadores y marcas Markdown. */
    val sourceLineNumbers: List<Int> = emptyList(),
    /** Solo para hojas leídas por OCR: resumen transitorio de confianza del modelo para diagnóstico y revisión. */
    val ocrConfidence: OcrConfidenceSummary? = null,
    val documentType: ImportDocumentType = ImportDocumentType.UNKNOWN,
)

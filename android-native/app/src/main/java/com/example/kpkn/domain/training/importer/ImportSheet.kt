package com.example.kpkn.domain.training.importer

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
)

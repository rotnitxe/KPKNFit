package com.example.kpkn.domain.training.importer

/**
 * Una hoja ya leída (de un .xlsx, de un CSV o de un PDF o una foto con OCR): nombre y filas de celdas en texto. Las celdas
 * vacías son "". [notes] son avisos que la persona debe leer antes de guardar (p. ej. «Lo leímos de una imagen…»): el
 * analizador los pasa a avisos `SHEET_NOTE`. Vive en `domain/` para que el analizador no dependa de la capa de datos.
 */
data class ImportSheet(
    val name: String,
    val rows: List<List<String>>,
    val notes: List<String> = emptyList(),
)

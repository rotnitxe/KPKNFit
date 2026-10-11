package com.example.kpkn.domain.training.importer

/**
 * Una hoja ya leída (de un .xlsx, un .ods, un CSV o un texto): nombre y filas de celdas en texto. Las celdas vacías son "".
 * [notes] recoge lo que el lector recortó u omitió de la hoja (columnas o filas de más, o el total de hojas del libro); el
 * analizador lo avisa como `SHEET_NOTE`. Vive en `domain/` para que el analizador no dependa de la capa de datos.
 */
data class ImportSheet(
    val name: String,
    val rows: List<List<String>>,
    val notes: List<String> = emptyList(),
)

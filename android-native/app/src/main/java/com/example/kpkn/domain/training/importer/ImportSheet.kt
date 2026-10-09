package com.example.kpkn.domain.training.importer

/**
 * Una hoja ya leída (de un .xlsx o de un CSV): nombre y filas de celdas en texto. Las celdas vacías son "".
 * Vive en `domain/` para que el analizador no dependa de la capa de datos.
 */
data class ImportSheet(
    val name: String,
    val rows: List<List<String>>,
)

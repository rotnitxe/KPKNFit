package com.example.kpkn.data.food

import java.io.File

/**
 * Lectura de los CSV de USDA embebidos para las pruebas JVM de WP-S9 (el working dir de las pruebas unitarias es el
 * módulo `app`, como en `DatasetKnowledgeIntegrationTest`).
 */
internal object UsdaTestAssets {
    val dir = File("src/main/assets/food_data")

    /** Texto íntegro de un CSV del catálogo. */
    fun text(name: String): String = File(dir, name).readText(Charsets.UTF_8)

    /** Líneas de datos de un CSV del catálogo (sin la cabecera). */
    fun dataLines(name: String): List<String> = File(dir, name).readLines(Charsets.UTF_8).drop(1)

    /** Campos de una línea CSV con comillas (las comas dentro de comillas no separan). */
    fun fields(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        for (c in line) {
            when {
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(c)
            }
        }
        result.add(current.toString())
        return result
    }

    class FoundationRow(val fdcId: Int, val description: String, val categoryId: String)

    /** Los `foundation_food` de food.csv, en el orden del archivo. */
    val foundation: List<FoundationRow> by lazy {
        dataLines("food.csv").mapNotNull { line ->
            val f = fields(line)
            if (f.size < 4 || f[1] != "foundation_food") null else FoundationRow(f[0].toInt(), f[2], f[3])
        }
    }
}

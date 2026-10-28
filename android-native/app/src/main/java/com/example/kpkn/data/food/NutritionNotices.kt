package com.example.kpkn.data.food

import android.content.res.AssetManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Aviso de fuentes o licencia de los datos de nutrición, empaquetado en `assets/nutrition_notices`. */
data class NutritionNotice(val fileName: String, val title: String) {
    val assetPath: String get() = "$NUTRITION_NOTICES_DIR/$fileName"
}

const val NUTRITION_NOTICES_DIR = "nutrition_notices"

/** Avisos que se muestran en Ajustes, en este orden. */
val nutritionNotices: List<NutritionNotice> = listOf(
    NutritionNotice("NOTICE.txt", "Fuentes de KPKN Fit"),
    NutritionNotice("ODbL-1.0.txt", "Open Food Facts · ODbL 1.0"),
    NutritionNotice("DbCL-1.0.txt", "Contenidos de Open Food Facts · DbCL 1.0"),
    NutritionNotice("CC0-1.0.txt", "USDA FoodData Central · CC0 1.0"),
    NutritionNotice("Etalab-2.0.md", "ANSES Ciqual 2025 · Licence Ouverte 2.0"),
)

private const val MAX_NOTICE_BYTES = 512 * 1024

/** Lee el texto de [notice] (UTF-8, sin BOM, con tope de 512 KB) fuera del hilo principal. */
suspend fun readNutritionNotice(assets: AssetManager, notice: NutritionNotice): String =
    withContext(Dispatchers.IO) {
        val bytes = assets.open(notice.assetPath).use { readAtMost(it, MAX_NOTICE_BYTES) }
        decodeNutritionNotice(bytes)
    }

private fun readAtMost(input: java.io.InputStream, limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    while (out.size() < limit) {
        val read = input.read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (read < 0) break
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}

/** UTF-8 sin BOM del contenido de un aviso. */
internal fun decodeNutritionNotice(bytes: ByteArray): String =
    String(bytes, Charsets.UTF_8).removePrefix("﻿")

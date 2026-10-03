package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.db.GlobalFoodEntity
import com.example.kpkn.data.db.toFoodItem
import com.example.kpkn.data.food.FoodImporter
import com.example.kpkn.data.models.FoodItem
import kotlinx.coroutines.runBlocking
import java.io.FileNotFoundException
import java.io.InputStream

/**
 * The committed USDA extract (`src/test/resources/food_data/usda_extract`, see its README.md) turned into the rows the importer
 * would store (WP-S9b). [entities] IS `FoodImporter.parseUsda` over the extract, so the search corpus holds exactly what the importer
 * produces (Spanish names and aliases, the Atwater energy and its flag, the clamped carbohydrate) and can never drift from it, the
 * way [OffSearchFixture] does for OFF.
 */
internal object UsdaSearchFixture {
    private const val DIR = "food_data/usda_extract/"

    private fun open(path: String): InputStream {
        val name = path.substringAfterLast('/')
        return checkNotNull(UsdaSearchFixture::class.java.classLoader) { "no class loader" }.getResourceAsStream(DIR + name)
            ?: throw FileNotFoundException("Missing test resource $DIR$name")
    }

    fun entities(): List<GlobalFoodEntity> = runBlocking { FoodImporter.parseUsda({ path -> open(path) }) }

    fun foods(): List<FoodItem> = entities().map { it.toFoodItem() }
}

package com.example.kpkn.data.food

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WP-S11 (B9): `assets/food_data` ships inside the APK, so a file in it is a file every install carries. This is the allow-list of
 * the files the app reads (the importer, the semantic dataset, the portion lexicon and the two branded catalogs); the USDA tables
 * nothing reads (samples, lab methods, conversion factors, update logs, the field-description workbook, `nutrient.csv`) live in
 * `android-native/datasets/usda_fdc_raw/`, outside the assets. A new asset is added here ON PURPOSE, with the code that reads it.
 *
 * The working directory of the unit tests is the `app` module, like `UsdaTestAssets` and `DatasetKnowledgeIntegrationTest`.
 */
class AssetInventoryTest {

    private val allowList: Set<String> = setOf(
        // USDA FoodData Central tables read by FoodImporter (IMPORT_ASSETS) and the Spanish name table built on food.csv.
        "food.csv", "food_nutrient.csv", "food_portion.csv", "food_category.csv", "measure_unit.csv", "usda_es_aliases.csv",
        // OpenFoodFacts Chile, read by FoodImporter.
        "off_chile.csv",
        // Semantic dataset (DatasetKnowledgeStore) and its report (scripts/process_dataset.py --check).
        "dataset_knowledge.bin", "dataset_knowledge_report.json",
        // Portion lexicon mirror and the JSON halves of the two branded catalogs.
        "subjective_portion_lexicon.json", "branded_snack_catalog.json", "branded_energy_kcal_catalog.json",
    )

    private val dir = File("src/main/assets/food_data")

    @Test
    fun `food_data holds exactly the allow-listed files`() {
        assertTrue("${dir.absolutePath} is not a directory", dir.isDirectory)
        val present = dir.list().orEmpty().toSortedSet()
        val unexpected = present - allowList
        val missing = allowList - present
        assertEquals(
            "files in assets/food_data that nothing reads (move them to android-native/datasets/usda_fdc_raw/): $unexpected; " +
                "allow-listed files that are missing: $missing",
            allowList.toSortedSet(),
            present,
        )
    }

    @Test
    fun `every asset the importer reads is allow-listed and present`() {
        FoodImporter.IMPORT_ASSETS.forEach { path ->
            val name = path.removePrefix("food_data/")
            assertTrue("$path is not in the allow-list", name in allowList)
            assertTrue("$path is missing from ${dir.path}", File(dir, name).isFile)
        }
    }

    @Test
    fun `allow-listed files are not empty`() {
        allowList.forEach { name -> assertTrue("$name is empty", File(dir, name).length() > 0L) }
    }
}

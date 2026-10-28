package com.example.kpkn.domain.nutrition.nutrients

import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.data.models.RecipeData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogRecordLinkTest {
    @Test
    fun usdaAndOffIdsAreKept() {
        assertEquals("usda_171265", CatalogRecordLink.recordIdFor(FoodItem(id = "usda_171265")))
        assertEquals("off_3017620422003", CatalogRecordLink.recordIdFor(FoodItem(id = "off_3017620422003")))
    }

    @Test
    fun staticFoodWithNumericUsdaSourceRecordLinksToUsda() {
        val food = FoodItem(id = "static_pollo", source = "USDA Foundation", sourceRecordId = "171077")
        assertEquals("usda_171077", CatalogRecordLink.recordIdFor(food))
        val lower = FoodItem(id = "x", source = "usda sr legacy", sourceRecordId = "171077")
        assertEquals("usda_171077", CatalogRecordLink.recordIdFor(lower))
    }

    @Test
    fun barcodeWithOffSourceLinksToOff() {
        val food = FoodItem(id = "x", source = "OFF", sourceRecordId = "7801234567890")
        assertEquals("off_7801234567890", CatalogRecordLink.recordIdFor(food))
        val short = FoodItem(id = "x", source = "OFF", sourceRecordId = "12345678")
        assertEquals("off_12345678", CatalogRecordLink.recordIdFor(short))
    }

    @Test
    fun offWithNonBarcodeLengthIsNull() {
        assertNull(CatalogRecordLink.recordIdFor(FoodItem(id = "x", source = "OFF", sourceRecordId = "1234567")))
        assertNull(CatalogRecordLink.recordIdFor(FoodItem(id = "x", source = "OFF", sourceRecordId = "123456789012345")))
    }

    @Test
    fun compoundEmptyAndSourcelessAreNull() {
        assertNull(CatalogRecordLink.recordIdFor(FoodItem(id = "x", source = "USDA", sourceRecordId = "171265+171890")))
        assertNull(CatalogRecordLink.recordIdFor(FoodItem(id = "x", source = "USDA", sourceRecordId = "")))
        assertNull(CatalogRecordLink.recordIdFor(FoodItem(id = "x", source = "USDA", sourceRecordId = null)))
        assertNull(CatalogRecordLink.recordIdFor(FoodItem(id = "x", source = null, sourceRecordId = "171077")))
        assertNull(CatalogRecordLink.recordIdFor(FoodItem(id = "x", source = "otra", sourceRecordId = "171077")))
        assertNull(CatalogRecordLink.recordIdFor(FoodItem()))
    }

    @Test
    fun v2SourceLinksCiqualAndKpknRecords() {
        assertEquals("ciqual_20047", CatalogRecordLink.recordIdFor(FoodItem(id = "ciqual_20047", source = CatalogRecordLink.V2_SOURCE)))
        assertEquals(
            "kpkn_pastel_de_choclo",
            CatalogRecordLink.recordIdFor(FoodItem(id = "kpkn_pastel_de_choclo", source = CatalogRecordLink.V2_SOURCE)),
        )
    }

    @Test
    fun packPrefixWithoutV2SourceIsNotLinked() {
        assertNull(CatalogRecordLink.recordIdFor(FoodItem(id = "ciqual_20047", source = "CIQUAL")))
        assertNull(CatalogRecordLink.recordIdFor(FoodItem(id = "kpkn_", source = CatalogRecordLink.V2_SOURCE)))
    }

    @Test
    fun recipeIsNull() {
        val recipe = FoodItem(
            id = "usda_1",
            recipe = RecipeData(ingredients = emptyList(), servings = 1),
        )
        assertNull(CatalogRecordLink.recordIdFor(recipe))
    }
}

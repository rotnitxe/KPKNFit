package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.food.CHILEAN_FOODS
import com.example.kpkn.data.food.GENERIC_FOODS
import com.example.kpkn.data.food.findStaticFoodById
import com.example.kpkn.data.models.FoodItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WP-S10 (B9): what makes a row a supermarket or laboratory record ([HouseholdPortions.isGlobalSku]). The prefix of its id says it
 * ("off_<barcode>", "usda_<fdcId>"); the text of its `source` does not, because a curated row cites its reference there ("USDA SR
 * Legacy (rounded)": water, tea, milk, tomato, lettuce, soda, beer, wine) and is still a row of the catalog.
 */
class GlobalSkuClassificationTest {

    private val all: List<FoodItem> = GENERIC_FOODS + CHILEAN_FOODS

    @Test
    fun `no static row reads as a supermarket SKU whatever its source cites`() {
        all.forEach { food -> assertFalse("${food.id} ${food.name} (${food.source})", HouseholdPortions.isGlobalSku(food)) }
        // They cite USDA SR Legacy in their source and were treated as imported records because of that text.
        listOf("gen015", "gen016", "gen026", "gen046", "gen066", "gen143", "gen146").forEach { id ->
            val food = checkNotNull(findStaticFoodById(id))
            assertTrue("$id cites USDA", food.source.orEmpty().contains("USDA"))
            assertFalse(id, HouseholdPortions.isGlobalSku(food))
        }
    }

    @Test
    fun `a row is a global SKU by the prefix of its id and by its source only when it has no id`() {
        assertTrue(HouseholdPortions.isGlobalSku(FoodItem(id = "off_7801234", name = "Yogurt", source = "OFF")))
        assertTrue(HouseholdPortions.isGlobalSku(FoodItem(id = "off_7801234", name = "Yogurt")))
        assertTrue(HouseholdPortions.isGlobalSku(FoodItem(id = "usda_171413", name = "Oil")))
        assertTrue(HouseholdPortions.isGlobalSku(FoodItem(id = "USDA_171413", name = "Oil")))
        // The text of source does not make a row that has an id one.
        assertFalse(HouseholdPortions.isGlobalSku(FoodItem(id = "gen015", name = "Aceite", source = "USDA SR Legacy (rounded)")))
        assertFalse(HouseholdPortions.isGlobalSku(FoodItem(id = "custom_1", name = "Mío", source = "OFF")))
        // With no id the text still tells.
        assertTrue(HouseholdPortions.isGlobalSku(FoodItem(name = "Sin id", source = "OFF")))
        assertTrue(HouseholdPortions.isGlobalSku(FoodItem(name = "Sin id", source = "USDA_FOUNDATION")))
        assertFalse(HouseholdPortions.isGlobalSku(FoodItem(name = "Sin id", source = "KPKN curated")))
        assertFalse(HouseholdPortions.isGlobalSku(FoodItem(name = "Sin id")))
    }
}

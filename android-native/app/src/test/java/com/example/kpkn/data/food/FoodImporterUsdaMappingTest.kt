package com.example.kpkn.data.food

import com.example.kpkn.data.db.GlobalFoodEntity
import com.example.kpkn.data.db.toFoodItem
import com.example.kpkn.domain.nutrition.HouseholdPortions
import com.example.kpkn.domain.nutrition.NutrientBasis
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WP-S9 (B6/B8): cómo el importador convierte filas USDA en alimentos globales, sin Room ni Android. Casos sintéticos
 * para cada regla (prioridad de ids de nutrientes, porción de UNA unidad, categoría legible, nombre y alias en español)
 * y una pasada sobre los CSV reales de `src/main/assets/food_data` para que el mapeo no se rompa en silencio.
 *
 * WP-S9b: energía Atwater (4/4/9, alcohol 7) para los alimentos sin energía publicada cuyos macros la sostienen, recorte a 0 del
 * carbohidrato por diferencia entre -2 g y 0, y sus banderas; los recuentos medidos sobre los CSV reales quedan fijados al final.
 */
class FoodImporterUsdaMappingTest {

    // ─── Fixtures sintéticas ─────────────────────────────────────────────────────────────────────

    /** Una línea CSV con todos los campos entre comillas, como los CSV de USDA. */
    private fun csvLine(vararg fields: Any): String = fields.joinToString(",") { "\"$it\"" }

    private fun nutrientLine(fdcId: Int, nutrientId: Int, amount: String) =
        csvLine(1, fdcId, nutrientId, amount, 1, 1, "", "", "", "", "")

    /** Nutrientes de un alimento a partir de filas (id de nutriente a cantidad) en el orden dado. */
    private fun nutrientsOf(vararg rows: Pair<Int, String>): FoodImporter.UsdaNutrients {
        val parsed = FoodImporter.parseUsdaNutrients(rows.map { nutrientLine(7, it.first, it.second) }.asSequence(), 16)
        return requireNotNull(parsed[7]) { "ninguna fila produjo nutrientes" }
    }

    private fun portionLine(
        fdcId: Int,
        seq: String = "1",
        amount: String = "1.0",
        unitId: Int = 1001,
        description: String = "",
        modifier: String = "",
        grams: String,
    ) = csvLine(1, fdcId, seq, amount, unitId, description, modifier, grams, 1, "", "")

    private val units = mapOf(1000 to "cup", 1001 to "tablespoon", 1004 to "milliliter", 1099 to "egg", 9999 to "undetermined")

    private fun portionsOf(vararg lines: String) = FoodImporter.parseUsdaPortions(lines.asSequence(), units)

    private fun aliasesOf(json: String): List<String> = Json.decodeFromString(json)

    private fun entity(
        description: String = "Milk, whole, 3.25% milkfat, with added vitamin D",
        category: String? = "Dairy and Egg Products",
        nutrients: FoodImporter.UsdaNutrients = nutrientsOf(2048 to "60", 1003 to "3.28", 1004 to "3.2", 1005 to "4.67", 1063 to "4.81"),
        portion: FoodImporter.UsdaPortion? = FoodImporter.UsdaPortion(249.0, "cup"),
        alias: FoodImporter.UsdaAlias? = FoodImporter.UsdaAlias("Leche entera", listOf("leche completa")),
    ): GlobalFoodEntity? = FoodImporter.usdaEntityOrNull(746782, description, category, nutrients, portion, alias)

    /** Una fila foundation sin energía publicada: solo los nutrientes dados. */
    private fun withoutEnergy(vararg rows: Pair<Int, String>): GlobalFoodEntity? =
        FoodImporter.usdaEntityOrNull(7, "Oil, test", "Fats and Oils", nutrientsOf(*rows), null, null)

    private fun flagsOf(food: GlobalFoodEntity): List<String> = Json.decodeFromString(food.qualityFlagsJson)

    // ─── Nutrientes: prioridad por id ─────────────────────────────────────────────────────────────

    @Test
    fun `sugar comes from 1063 when 2000 is absent`() {
        // Casi todo Foundation solo trae "Sugars, Total" (1063): antes el azúcar quedaba en 0.
        assertEquals(4.67f, nutrientsOf(1004 to "3.2", 1063 to "4.67").sugar, 0f)
    }

    @Test
    fun `sugar prefers 2000 over 1063 whichever row comes first`() {
        assertEquals(5.0f, nutrientsOf(2000 to "5.0", 1063 to "4.0").sugar, 0f)
        assertEquals(5.0f, nutrientsOf(1063 to "4.0", 2000 to "5.0").sugar, 0f)
    }

    @Test
    fun `fat comes from 1085 only when 1004 is absent`() {
        assertEquals(93.7f, nutrientsOf(1085 to "93.7").fat, 0f)
        assertEquals(94.0f, nutrientsOf(1004 to "94.0", 1085 to "93.7").fat, 0f)
        assertEquals(94.0f, nutrientsOf(1085 to "93.7", 1004 to "94.0").fat, 0f)
    }

    @Test
    fun `a higher priority id wins even when its value is zero`() {
        assertEquals(0f, nutrientsOf(2000 to "0.0", 1063 to "0.4").sugar, 0f)
        assertEquals(0f, nutrientsOf(1004 to "0.0", 1085 to "0.4").fat, 0f)
    }

    @Test
    fun `energy keeps the 2048 then 2047 then 1008 priority and ignores zero`() {
        assertEquals(120f, nutrientsOf(1008 to "100", 2047 to "110", 2048 to "120").energy, 0f)
        assertEquals(120f, nutrientsOf(2048 to "120", 2047 to "110", 1008 to "100").energy, 0f)
        assertEquals(110f, nutrientsOf(2047 to "110", 1008 to "100").energy, 0f)
        assertEquals(77f, nutrientsOf(2048 to "0", 1008 to "77").energy, 0f)
        assertEquals(0f, nutrientsOf(2048 to "0").energy, 0f)
    }

    @Test
    fun `carbohydrate comes from 1050 only when 1005 is absent`() {
        assertEquals(14.9f, nutrientsOf(1005 to "14.9", 1050 to "13.9").carbs, 0f)
        assertEquals(14.9f, nutrientsOf(1050 to "13.9", 1005 to "14.9").carbs, 0f)
        assertEquals(13.9f, nutrientsOf(1050 to "13.9").carbs, 0f)
    }

    @Test
    fun `alcohol comes from 1018`() {
        assertEquals(10.6f, nutrientsOf(1018 to "10.6").alcohol, 0f)
        assertEquals(0f, nutrientsOf(1003 to "3.2").alcohol, 0f)
    }

    @Test
    fun `the other nutrients keep their single ids`() {
        val n = nutrientsOf(
            1003 to "20", 1005 to "30", 1079 to "4", 1093 to "55", 1092 to "66", 1051 to "70", 1057 to "80",
        )
        assertEquals(20f, n.protein, 0f)
        assertEquals(30f, n.carbs, 0f)
        assertEquals(4f, n.fiber, 0f)
        assertEquals(55f, n.sodiumMg, 0f)
        assertEquals(66f, n.potassiumMg, 0f)
        assertEquals(70f, n.water, 0f)
        assertEquals(80f, n.caffeineMg, 0f)
    }

    @Test
    fun `unmapped nutrients and unreadable amounts do not create or claim anything`() {
        assertTrue(FoodImporter.parseUsdaNutrients(sequenceOf(nutrientLine(7, 1002, "5.0")), 16).isEmpty())
        // Una cantidad vacía en 2000 no debe tapar el valor real de 1063.
        assertEquals(4.0f, nutrientsOf(2000 to "", 1063 to "4.0").sugar, 0f)
        assertTrue(FoodImporter.parseUsdaNutrients(sequenceOf("", "short,row", nutrientLine(7, 1063, "x")), 16).isEmpty())
    }

    // ─── Porciones: UNA unidad, no `gram_weight` a secas ───────────────────────────────────────────

    @Test
    fun `one tablespoon of 13,6 g is the portion with its unit label`() {
        val portion = portionsOf(portionLine(10, grams = "13.6")).getValue(10)
        assertEquals(13.6, portion.grams, 1e-9)
        assertEquals("tablespoon", portion.unit)
    }

    @Test
    fun `a multi unit row is divided by its amount`() {
        // Hummus: "2 tablespoon = 33,9 g" es 16,95 g por cucharada; antes quedaba 33,9 g sin decir de qué.
        val portion = portionsOf(portionLine(10, amount = "2.0", grams = "33.9")).getValue(10)
        assertEquals(16.95, portion.grams, 1e-9)
        assertEquals("tablespoon", portion.unit)
    }

    @Test
    fun `oil per 100 ml is a density not a portion and the next row is used`() {
        assertNull(portionsOf(portionLine(10, amount = "100.0", unitId = 1004, grams = "90.7"))[10])
        val portion = portionsOf(
            portionLine(10, seq = "1", amount = "100.0", unitId = 1004, grams = "90.7"),
            portionLine(10, seq = "2", grams = "13.6"),
        ).getValue(10)
        assertEquals(13.6, portion.grams, 1e-9)
    }

    @Test
    fun `the first valid row by seq_num wins even when it appears later in the file`() {
        val portion = portionsOf(
            portionLine(10, seq = "3", unitId = 1000, grams = "240"),
            portionLine(10, seq = "2", grams = "15"),
            portionLine(10, seq = "5", unitId = 1099, grams = "50"),
        ).getValue(10)
        assertEquals(15.0, portion.grams, 1e-9)
        assertEquals("tablespoon", portion.unit)
    }

    @Test
    fun `at equal seq_num the earlier row wins and a missing seq_num goes last`() {
        val tie = portionsOf(portionLine(10, seq = "1", unitId = 1000, grams = "240"), portionLine(10, seq = "1", grams = "15"))
        assertEquals("cup", tie.getValue(10).unit)
        val blank = portionsOf(portionLine(10, seq = "", grams = "15"), portionLine(10, seq = "4", unitId = 1000, grams = "240"))
        assertEquals("cup", blank.getValue(10).unit)
    }

    @Test
    fun `portions outside 5 g and the pack limit are skipped`() {
        val pack = HouseholdPortions.PACK_GRAMS
        assertNull(portionsOf(portionLine(10, grams = "4.9"))[10])
        assertNull(portionsOf(portionLine(10, grams = "${pack + 0.1}"))[10])
        assertEquals(5.0, portionsOf(portionLine(10, grams = "5.0")).getValue(10).grams, 1e-9)
        assertEquals(pack, portionsOf(portionLine(10, grams = "$pack")).getValue(10).grams, 1e-9)
    }

    @Test
    fun `amounts below one half are ignored because the source rounds them to one decimal`() {
        // Ricota: "0.2 cup = 64,6 g" es en realidad 0,25 taza; dividir daría 323 g por taza en vez de 258 g.
        val portion = portionsOf(
            portionLine(10, seq = "1", amount = "0.2", unitId = 1000, grams = "64.6"),
            portionLine(10, seq = "3", amount = "0.5", unitId = 1000, grams = "129.0"),
        ).getValue(10)
        assertEquals(258.0, portion.grams, 1e-9)
    }

    @Test
    fun `an undetermined unit without detail is skipped and with detail it keeps the detail`() {
        assertNull(portionsOf(portionLine(10, unitId = 9999, grams = "30"))[10])
        assertNull(portionsOf(portionLine(10, unitId = 4242, grams = "30"))[10])
        val labelled = portionsOf(portionLine(10, unitId = 9999, modifier = "NLEA serving", grams = "30"))
        assertEquals("NLEA serving", labelled.getValue(10).unit)
    }

    @Test
    fun `the label joins the unit and its detail`() {
        assertEquals("cup, chopped", FoodImporter.usdaPortionLabel("cup", "chopped"))
        assertEquals("tablespoon", FoodImporter.usdaPortionLabel("tablespoon", "  "))
        assertNull(FoodImporter.usdaPortionLabel(null, " "))
        val egg = portionsOf(portionLine(10, unitId = 1099, modifier = "whole without shell", grams = "50.3"))
        assertEquals("egg, whole without shell", egg.getValue(10).unit)
        // portion_description manda sobre modifier.
        val cheese = portionsOf(portionLine(10, unitId = 1000, description = "shredded", modifier = "x", grams = "105"))
        assertEquals("cup, shredded", cheese.getValue(10).unit)
    }

    @Test
    fun `malformed portion rows are skipped`() {
        val portions = portionsOf("", "1,2,3", portionLine(10, amount = "n/a", grams = "13.6"), portionLine(10, grams = "-3"), portionLine(11, grams = "13.6"))
        assertEquals(setOf(11), portions.keys)
    }

    // ─── Categoría y unidades ───────────────────────────────────────────────────────────────────

    @Test
    fun `categories and measure units are read by id with quoted commas`() {
        val categories = FoodImporter.parseFoodCategories(
            sequenceOf(csvLine(6, "0600", "Soups, Sauces, and Gravies"), csvLine(14, "1400", "Beverages"), "bad,line", ""),
        )
        assertEquals(mapOf(6 to "Soups, Sauces, and Gravies", 14 to "Beverages"), categories)
        val measureUnits = FoodImporter.parseMeasureUnits(sequenceOf(csvLine(1001, "tablespoon"), csvLine(9999, "undetermined")))
        assertEquals(mapOf(1001 to "tablespoon", 9999 to "undetermined"), measureUnits)
    }

    @Test
    fun `the category is the description and an unknown id gives none`() {
        val categories = mapOf(1 to "Dairy and Egg Products", 14 to "Beverages")
        assertEquals("Beverages", FoodImporter.usdaCategory("14", categories))
        assertEquals("Dairy and Egg Products", FoodImporter.usdaCategory(" 1 ", categories))
        assertNull(FoodImporter.usdaCategory("99", categories))
        assertNull(FoodImporter.usdaCategory("", categories))
        assertNull(FoodImporter.usdaCategory(null, categories))
    }

    // ─── Alias en español ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `the alias table is read with its pipe separated aliases`() {
        val table = FoodImporter.parseUsdaAliases(
            sequenceOf(
                csvLine(746782, "Milk, whole", "Leche entera", "leche completa| leche normal ||"),
                csvLine(2, "Only aliases", "", "uno|dos"),
                csvLine(3, "Nothing curated", "", ""),
                "bad",
                "",
            ),
        )
        assertEquals(setOf(746782, 2), table.keys)
        assertEquals(FoodImporter.UsdaAlias("Leche entera", listOf("leche completa", "leche normal")), table[746782])
        assertEquals(FoodImporter.UsdaAlias(null, listOf("uno", "dos")), table[2])
    }

    @Test
    fun `search aliases are the normalized Spanish name then the English one then the extras without repeats`() {
        val aliases = FoodImporter.usdaSearchAliases(
            "Milk, whole, 3.25% milkfat",
            FoodImporter.UsdaAlias("Leche Entera", listOf("Leche completa", "leche  entera", "Plátano")),
        )
        assertEquals(listOf("leche entera", "milk whole 3 25 milkfat", "leche completa", "platano"), aliases)
        assertEquals(listOf("milk whole 3 25 milkfat"), FoodImporter.usdaSearchAliases("Milk, whole, 3.25% milkfat", null))
    }

    // ─── Entidad completa ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `the entity carries the Spanish name, its aliases, the category and the portion`() {
        val food = requireNotNull(entity())
        assertEquals("usda_746782", food.foodId)
        assertEquals("Leche entera", food.name)
        assertEquals("leche entera", food.normalizedName)
        assertEquals(
            listOf("leche entera", "milk whole 3 25 milkfat with added vitamin d", "leche completa"),
            aliasesOf(food.aliasesJson),
        )
        assertEquals("Dairy and Egg Products", food.category)
        assertEquals(249.0, food.portionGrams ?: 0.0, 1e-9)
        assertEquals("cup", food.portionUnit)
        assertEquals("USDA", food.source)
        assertEquals("746782", food.sourceRecordId)
        assertEquals(FoodImporter.DATA_VERSION.toString(), food.datasetVersion)
    }

    @Test
    fun `the food item keeps the category and the Spanish aliases of the catalog row`() {
        // B8: toFoodItem perdía la categoría de la fila global.
        val item = requireNotNull(entity()).toFoodItem()
        assertEquals("Dairy and Egg Products", item.category)
        assertEquals("Leche entera", item.name)
        assertEquals(listOf("leche entera", "milk whole 3 25 milkfat with added vitamin d", "leche completa"), item.searchAliases)
        assertNull(requireNotNull(entity(category = null)).toFoodItem().category)
    }

    @Test
    fun `the macros keep their values and the sugar comes from 1063`() {
        val food = requireNotNull(entity())
        assertEquals(60.0, food.calories, 1e-6)
        assertEquals(3.28f.toDouble(), food.protein, 1e-9)
        assertEquals(3.2f.toDouble(), food.fats, 1e-9)
        assertEquals(4.67f.toDouble(), food.carbs, 1e-9)
        assertEquals(4.81f.toDouble(), food.sugar, 1e-9)
    }

    @Test
    fun `without a Spanish name the English description stays as name and alias`() {
        val food = requireNotNull(entity(alias = null))
        assertEquals("Milk, whole, 3.25% milkfat, with added vitamin D", food.name)
        assertEquals("milk whole 3 25 milkfat with added vitamin d", food.normalizedName)
        assertEquals(listOf("milk whole 3 25 milkfat with added vitamin d"), aliasesOf(food.aliasesJson))
    }

    @Test
    fun `the state still comes from the English description`() {
        val raw = requireNotNull(entity(description = "Chicken, breast, boneless, skinless, raw", alias = FoodImporter.UsdaAlias("Pechuga de pollo cruda", emptyList())))
        assertEquals("RAW", raw.foodState)
        assertEquals("PER_100G_RAW", raw.nutritionBasis)
        assertEquals(FoodImporter.stateForDescription("Chicken, breast, boneless, skinless, raw"), raw.foodState)
    }

    @Test
    fun `hard spaces and spare blanks are cleaned from the English description`() {
        val food = requireNotNull(entity(description = "  Beef," + Char(0x00A0) + "tenderloin steak,  raw ", alias = null))
        assertEquals("Beef, tenderloin steak, raw", food.name)
    }

    @Test
    fun `an unknown category and a missing portion stay null`() {
        val food = requireNotNull(entity(category = null, portion = null))
        assertNull(food.category)
        assertNull(food.portionGrams)
        assertNull(food.portionUnit)
    }

    @Test
    fun `rows without trustworthy energy or with impossible macros do not enter the catalog`() {
        // Protein and fat but no carbohydrate and no energy: nothing to add up with confidence (WP-S9b).
        assertNull(entity(nutrients = nutrientsOf(1003 to "3.28", 1004 to "3.2")))
        // Beyond -2 g the carbohydrate is a broken value, not rounding noise.
        assertNull(entity(nutrients = nutrientsOf(2048 to "60", 1005 to "-2.5")))
        assertNull(entity(nutrients = nutrientsOf(2048 to "60", 1003 to "120")))
        assertNull(entity(description = "  "))
    }

    // ─── WP-S9b: energía Atwater y carbohidrato recortado ──────────────────────────────────────────

    @Test
    fun `a pure fat without energy gets 9 kcal per gram and the Atwater flag`() {
        val oil = requireNotNull(withoutEnergy(1004 to "100"))
        assertEquals(900.0, oil.calories, 1e-9)
        assertEquals(0.0, oil.protein, 0.0)
        assertEquals(0.0, oil.carbs, 0.0)
        assertEquals(listOf("ENERGY_ATWATER"), flagsOf(oil))
        // The oils of Foundation only carry "Total fat (NLEA)" (1085): 93,7 g of fat in 100 g of olive oil.
        assertEquals(843.3, requireNotNull(withoutEnergy(1085 to "93.7")).calories, 1e-9)
    }

    @Test
    fun `protein, carbohydrate and fat weigh 4, 4 and 9 kcal per gram`() {
        // A dry bean as sold: 11 g of water, 21,6 g of protein, 62,4 g of carbohydrate and 1,4 g of fat, with no energy row.
        val bean = requireNotNull(withoutEnergy(1051 to "11", 1003 to "21.6", 1005 to "62.4", 1004 to "1.4"))
        assertEquals(4 * 21.6 + 4 * 62.4 + 9 * 1.4, bean.calories, 0.1)
        assertEquals(348.6, bean.calories, 1e-9)
        assertEquals(62.4f.toDouble(), bean.carbs, 1e-9)
        assertEquals(listOf("ENERGY_ATWATER"), flagsOf(bean))
        // The carbohydrate by summation (1050) counts when the one by difference is absent.
        assertEquals(348.6, requireNotNull(withoutEnergy(1051 to "11", 1003 to "21.6", 1050 to "62.4", 1004 to "1.4")).calories, 1e-9)
    }

    @Test
    fun `alcohol weighs 7 kcal per gram and is not read as an energy mismatch`() {
        // A wine: 86,5 g of water, 10,6 g of alcohol, 2,6 g of carbohydrate and 0,07 g of protein.
        val wine = requireNotNull(withoutEnergy(1051 to "86.5", 1018 to "10.6", 1005 to "2.6", 1003 to "0.07"))
        assertEquals(84.9, wine.calories, 1e-9)
        assertEquals(listOf("ENERGY_ATWATER"), flagsOf(wine))
    }

    @Test
    fun `the published energy wins over Atwater and the row carries no flag`() {
        // Olive oil as SR Legacy publishes it: 884 kcal, not the 900 that 9 kcal times 100 g of fat would give.
        val oil = requireNotNull(withoutEnergy(2048 to "884", 1004 to "100"))
        assertEquals(884.0, oil.calories, 0.0)
        assertEquals("[]", oil.qualityFlagsJson)
        assertEquals("[]", requireNotNull(entity()).qualityFlagsJson)
    }

    @Test
    fun `Atwater needs the macros to explain 90 percent of the dry matter`() {
        assertEquals(900.0, requireNotNull(FoodImporter.atwaterEnergyOrNull(0.0, 0.0, 100.0, 0.0, 0.0)), 1e-9)
        assertNotNull(withoutEnergy(1004 to "90"))
        assertNull(withoutEnergy(1004 to "89.9"))
        // Water shrinks the dry matter: 5 g of protein are 91 % of the 5,5 g left by 94,5 g of water, 4,9 g are 89 %.
        assertNotNull(withoutEnergy(1051 to "94.5", 1003 to "5.0"))
        assertNull(withoutEnergy(1051 to "94.5", 1003 to "4.9"))
        assertNull(FoodImporter.atwaterEnergyOrNull(0.0, 0.0, 0.0, 0.0, 100.0))
        assertNull(FoodImporter.atwaterEnergyOrNull(0.0, 0.0, 0.0, 0.0, 0.0))
    }

    @Test
    fun `foods that do not declare their carbohydrate stay out instead of getting a fraction of their energy`() {
        // The dry beans of Foundation ("0 % moisture"): protein, fat, starch and fibre, no carbohydrate. 4P + 9F is 110 kcal; the bean has ~340.
        assertNull(withoutEnergy(1051 to "0.0", 1003 to "24.4", 1004 to "1.45", 1079 to "4.2"))
        // The watermelon: water, protein and sugars. Protein alone is 3,5 kcal; the fruit has 30.
        assertNull(withoutEnergy(1051 to "90.91", 1003 to "0.87125", 1063 to "7.197"))
        // Table salt: water and minerals, no macros.
        assertNull(withoutEnergy(1051 to "0.42", 1092 to "2.0"))
    }

    @Test
    fun `a slightly negative carbohydrate by difference is raised to zero and flagged`() {
        // Chicken breast with skin: 132,8 kcal published and -0,43 g of carbohydrate by difference.
        val chicken = requireNotNull(entity(nutrients = nutrientsOf(2048 to "132.8", 1003 to "21.4", 1004 to "4.78", 1005 to "-0.43")))
        assertEquals(0.0, chicken.carbs, 0.0)
        assertEquals(132.8f.toDouble(), chicken.calories, 1e-9)
        assertEquals(listOf("CARB_CLAMPED"), flagsOf(chicken))
    }

    @Test
    fun `the clamp stops at minus 2 g and a carbohydrate of zero or more is never flagged`() {
        fun row(carbs: String) = entity(nutrients = nutrientsOf(2048 to "132.8", 1003 to "21.4", 1004 to "4.78", 1005 to carbs))
        assertEquals(listOf("CARB_CLAMPED"), flagsOf(requireNotNull(row("-2.0"))))
        assertNull(row("-2.01"))
        assertNull(row("-3.0"))
        assertEquals(emptyList<String>(), flagsOf(requireNotNull(row("0.0"))))
        assertEquals(emptyList<String>(), flagsOf(requireNotNull(row("4.67"))))
    }

    @Test
    fun `both corrections can apply to the same row`() {
        val row = requireNotNull(withoutEnergy(1004 to "100", 1005 to "-0.3"))
        assertEquals(900.0, row.calories, 1e-9)
        assertEquals(0.0, row.carbs, 0.0)
        assertEquals(listOf("ENERGY_ATWATER", "CARB_CLAMPED"), flagsOf(row))
    }

    @Test
    fun `the two informational flags keep a row verified for the logger and a quality flag does not`() {
        val oil = requireNotNull(withoutEnergy(1085 to "93.7")).toFoodItem()
        assertEquals(listOf("ENERGY_ATWATER"), oil.qualityFlags)
        assertTrue(NutrientBasis.isVerified(oil))
        val chicken = requireNotNull(entity(nutrients = nutrientsOf(2048 to "132.8", 1003 to "21.4", 1004 to "4.78", 1005 to "-0.43"))).toFoodItem()
        assertEquals(listOf("CARB_CLAMPED"), chicken.qualityFlags)
        assertTrue(NutrientBasis.isVerified(chicken))
        assertTrue(NutrientBasis.isVerified(oil.copy(qualityFlags = listOf("ENERGY_ATWATER", "CARB_CLAMPED"))))
        assertFalse(NutrientBasis.isVerified(oil.copy(qualityFlags = listOf("ENERGY_ATWATER", "ENERGY_MISMATCH"))))
        assertFalse(NutrientBasis.isVerified(oil.copy(qualityFlags = listOf("INCOMPLETE"))))
        assertFalse(NutrientBasis.isVerified(oil.copy(qualityFlags = listOf("LOW_QUALITY"))))
    }

    @Test
    fun `every asset the import reads exists in the assets folder`() {
        // Los auxiliares se degradan en silencio si faltan: sin este test un nombre mal escrito dejaría el catálogo en inglés.
        assertTrue(FoodImporter.IMPORT_ASSETS.contains("food_data/usda_es_aliases.csv"))
        FoodImporter.IMPORT_ASSETS.forEach { path ->
            assertTrue("falta el asset $path", java.io.File(UsdaTestAssets.dir.parentFile, path).isFile)
        }
    }

    @Test
    fun `an install holding the previous data version imports the catalog once more`() {
        val fingerprint = FoodImporter.versionFingerprint()
        // WP-S9b changed the parser (Atwater energy, carbohydrate clamp): the installs that imported with v10 must import once more.
        val previous = FoodImporter.ImportMetadata(version = 10, checksum = "ab12".repeat(16), importedAt = "2026-10-01T00:00:00Z")
        assertTrue(FoodImporter.DATA_VERSION >= 11)
        assertTrue(FoodImporter.shouldImport(true, FoodImporter.adoptLegacyChecksum(previous, fingerprint), fingerprint))
    }

    // ─── CSV reales de USDA ───────────────────────────────────────────────────────────────────────

    @Test
    fun `real portions describe one unit and never carry the raw measure unit id`() {
        // Aceite de coco: "1 tablespoon (liquid oil) = 11,6 g". Hummus: "2 tablespoon = 33,9 g" -> 16,95 g por cucharada.
        assertEquals(FoodImporter.UsdaPortion(11.6, "tablespoon, liquid oil"), portions[330458])
        assertEquals(16.95, portions.getValue(321358).grams, 1e-9)
        assertEquals("tablespoon", portions.getValue(321358).unit)
        assertEquals(FoodImporter.UsdaPortion(249.0, "cup"), portions[746782])
        assertEquals("egg, whole without shell", portions.getValue(748967).unit)
        // El aceite de oliva solo declara "100 ml = 90,7 g" (una densidad): sin porción, no "90,7 g" como porción.
        assertNull(portions[748608])
        assertTrue(portions.size > 50)
        portions.forEach { (fdcId, portion) ->
            assertTrue("fdc $fdcId: ${portion.grams} g", portion.grams in 5.0..HouseholdPortions.PACK_GRAMS)
            assertTrue("fdc $fdcId: unit '${portion.unit}'", portion.unit.isNotBlank() && !portion.unit.all { it.isDigit() })
        }
    }

    @Test
    fun `real sugar is recovered from the 1063 id for most foundation foods`() {
        val withEnergy = foundation.map { it.fdcId }.filter { (nutrients[it]?.energy ?: 0f) > 0f }
        val withSugar = withEnergy.count { (nutrients.getValue(it).sugar) > 0f }
        // Antes solo el id 2000 (5 de 436 alimentos) alimentaba el azúcar; con el 1063 son más de 140.
        assertTrue("alimentos con energía: ${withEnergy.size}, con azúcar: $withSugar", withEnergy.size >= 350 && withSugar >= 140)
    }

    @Test
    fun `real foundation rows build catalog entities with readable category, unit and names`() {
        val ids = entities.map { it.foodId }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue("entidades: ${entities.size}", entities.size >= 350)
        val categoryNames = categories.values.toSet()
        entities.forEach { food ->
            assertTrue("${food.foodId}: categoría '${food.category}'", food.category in categoryNames)
            assertTrue("${food.foodId}: unidad '${food.portionUnit}'", food.portionUnit == null || !food.portionUnit.all { it.isDigit() })
            assertEquals("USDA", food.source)
            assertEquals(FoodImporter.DATA_VERSION.toString(), food.datasetVersion)
            assertEquals(FoodImporter.normalizeSearch(food.name), food.normalizedName)
            assertTrue(aliasesOf(food.aliasesJson).contains(food.normalizedName))
            assertTrue("${food.foodId}: sin macros finitos", listOf(food.calories, food.protein, food.fats, food.carbs, food.sugar).all { it.isFinite() })
        }
        // Todo alimento con nombre curado lo usa; el resto conserva la descripción en inglés.
        entities.forEach { food ->
            val esName = aliases[food.foodId.removePrefix("usda_").toInt()]?.esName
            if (esName != null) assertEquals(esName, food.name)
        }
        assertTrue(entities.count { it.sugar > 0.0 } >= 140)
        assertTrue(entities.count { it.portionGrams != null } >= 60)
    }

    @Test
    fun `real foundation foods without energy stay out unless Atwater can trust them and the ten negative carbohydrates are clamped`() {
        // WP-S9b, measured on the shipped CSVs: 436 foundation foods, 60 with no energy row (8 oils, 2 butters, 34 dry beans,
        // 13 fruits, vegetables and juices of lot 2727577-2727589, a watermelon and 2 salts) and 10 with a carbohydrate by difference below zero.
        val withoutEnergy = foundation.map { it.fdcId }.filter { nutrients.getValue(it).energy <= 0f }
        assertEquals(436, foundation.size)
        assertEquals(60, withoutEnergy.size)
        val rows = entities.associateBy { it.foodId.removePrefix("usda_").toInt() }

        // Atwater recovers the oils and the butters: their fat is 93-99 % of the dry matter.
        val atwater = rows.filterValues { NutrientBasis.FLAG_ENERGY_ATWATER in flagsOf(it) }
        assertEquals(setOf(748278, 748323, 748366, 748608, 789828, 790508, 1750348, 1750349, 1750350, 1750351), atwater.keys)
        atwater.values.forEach { food ->
            assertEquals(food.foodId, 4 * food.protein + 4 * food.carbs + 9 * food.fats, food.calories, 0.1)
            assertTrue("${food.foodId}: ${food.calories} kcal", food.calories in 700.0..900.0)
            assertTrue(food.foodId, NutrientBasis.isVerified(food.toFoodItem()))
        }
        assertEquals(843.3, rows.getValue(748608).calories, 1e-9)
        assertEquals(733.5, rows.getValue(789828).calories, 1e-9)

        // The other 50 declare no carbohydrate at all, so 4P + 9F would be a fraction of their energy: they stay out.
        val left = withoutEnergy - atwater.keys
        assertEquals(50, left.size)
        left.forEach { id ->
            assertEquals("fdc $id", 0f, nutrients.getValue(id).carbs, 0f)
            assertFalse("fdc $id", rows.containsKey(id))
        }

        // The ten carbohydrates by difference between -0,71 and -0,06 g are raised to zero and flagged.
        val clamped = rows.filterValues { NutrientBasis.FLAG_CARB_CLAMPED in flagsOf(it) }
        assertEquals(setOf(2727566, 2727567, 2727568, 2727569, 2727570, 2727571, 2727575, 2727576, 2747656, 2747673), clamped.keys)
        clamped.keys.forEach { id -> assertTrue("fdc $id", nutrients.getValue(id).carbs in -0.71f..-0.06f) }
        clamped.values.forEach { food ->
            assertEquals(food.foodId, 0.0, food.carbs, 0.0)
            assertTrue(food.foodId, food.calories > 0.0 && NutrientBasis.isVerified(food.toFoodItem()))
        }

        // 366 rows before WP-S9b, 20 more now; no other row changed its flags.
        assertEquals(366 + atwater.size + clamped.size, entities.size)
        assertTrue(entities.filter { it.foodId.removePrefix("usda_").toInt() !in atwater.keys + clamped.keys }.all { it.qualityFlagsJson == "[]" })
    }

    // ─── Lectura de los CSV reales ──────────────────────────────────────────────────────────────────

    private companion object {
        private fun dataLines(name: String) = UsdaTestAssets.dataLines(name)

        val categories: Map<Int, String> by lazy { FoodImporter.parseFoodCategories(dataLines("food_category.csv").asSequence()) }
        val measureUnits: Map<Int, String> by lazy { FoodImporter.parseMeasureUnits(dataLines("measure_unit.csv").asSequence()) }
        val portions: Map<Int, FoodImporter.UsdaPortion> by lazy {
            FoodImporter.parseUsdaPortions(dataLines("food_portion.csv").asSequence(), measureUnits)
        }
        val nutrients: Map<Int, FoodImporter.UsdaNutrients> by lazy { FoodImporter.parseUsdaNutrients(dataLines("food_nutrient.csv").asSequence()) }
        val aliases: Map<Int, FoodImporter.UsdaAlias> by lazy { FoodImporter.parseUsdaAliases(dataLines("usda_es_aliases.csv").asSequence()) }

        val foundation get() = UsdaTestAssets.foundation

        val entities: List<GlobalFoodEntity> by lazy {
            foundation.mapNotNull { row ->
                val food = nutrients[row.fdcId] ?: return@mapNotNull null
                FoodImporter.usdaEntityOrNull(
                    fdcId = row.fdcId,
                    description = row.description,
                    category = FoodImporter.usdaCategory(row.categoryId, categories),
                    nutrients = food,
                    portion = portions[row.fdcId],
                    alias = aliases[row.fdcId],
                )
            }
        }
    }
}

package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.db.GlobalFoodEntity
import com.example.kpkn.data.db.toFoodItem
import com.example.kpkn.data.food.findStaticFoodById
import com.example.kpkn.data.models.AmountIntent
import com.example.kpkn.data.models.FoodItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WP-S1 / B1: tapping a search result keeps the TAPPED row as the eaten identity. A pack-sized or
 * per-100 g row only changes the portion (household grams), never the food, and the tap is never a
 * silent no-op. Pure JVM: the drawer composes exactly these calls.
 *
 * WP-S10: the static rows whose macros are per 100 g are PER_100G_AS_SOLD, so their tap eats a household portion too (the last tests).
 */
class SearchPickContractTest {

    /** An OFF Chile row exactly as `GlobalFoodEntity.toFoodItem()` hands it to the search tab. */
    private fun off(
        id: String,
        name: String,
        brand: String?,
        calories: Double,
        protein: Double,
        carbs: Double,
        fats: Double,
        portionGrams: Double? = null,
    ): FoodItem = GlobalFoodEntity(
        foodId = id,
        name = name,
        brand = brand,
        calories = calories,
        protein = protein,
        carbs = carbs,
        fats = fats,
        source = "OFF Chile",
        sourcePriority = 80,
        nutritionBasis = "PER_100G_AS_SOLD",
        portionGrams = portionGrams,
    ).toFoodItem()

    private fun pick(row: FoodItem, query: String): Pair<FoodItem, Double> {
        val identity = HouseholdPortions.identityForSearchPick(row, query)
        return identity to HouseholdPortions.eatenGramsForSearchPick(identity, query)
    }

    @Test
    fun `leche plus Leche descremada Colun keeps the tapped row at a glass of milk`() {
        val colun = off("off_1", "Leche descremada", "Colun", 35.0, 3.4, 4.9, 0.1)
        assertTrue(FoodIdentity.matchesDeclaredIdentity("leche", colun))
        val (identity, grams) = pick(colun, "leche")
        assertSame(colun, identity)
        assertEquals("off_1", identity.id)
        // The 100 g of a per-100 g basis is a denominator, not what was drunk.
        assertEquals(200.0, grams, 0.0)
        assertEquals(70.0, scaleFoodByPortion(identity, amountGrams = grams).calories, 2.0)
    }

    @Test
    fun `huevos plus the curated egg keeps one egg`() {
        val egg = findStaticFoodById("gen007")!!
        val (identity, grams) = pick(egg, "huevos")
        assertEquals("gen007", identity.id)
        assertEquals(50.0, grams, 0.0)
    }

    @Test
    fun `hallulla plus a 1 kg pack keeps the pack row, eats one hallulla and is AUTO`() {
        val pack = off("off_hallulla_kg", "Hallulla Ideal 1kg", "Ideal", 400.0, 8.0, 70.0, 8.0, portionGrams = 1000.0)
        val (identity, grams) = pick(pack, "hallulla")
        assertSame(pack, identity)
        assertTrue("hallulla grams $grams", grams in 70.0..90.0)
        // The description path keeps refusing an unbranded pack-named SKU...
        assertEquals(
            FoodResolutionStatus.NO_RESOLVED,
            HouseholdPortions.operationalAutoStatus(identity, grams, null, false, AmountIntent.UNSPECIFIED),
        )
        // ...but a row the person tapped is a valid identity.
        assertEquals(
            FoodResolutionStatus.AUTO,
            HouseholdPortions.operationalAutoStatus(identity, grams, null, false, AmountIntent.UNSPECIFIED, explicitPick = true),
        )
    }

    @Test
    fun `pan plus a per 100 g bread is one piece, not the 100 g basis`() {
        val bread = off("off_pan_integral_bauducco", "Pan integral Bauducco", "Bauducco", 250.0, 9.5, 45.0, 3.6)
        assertTrue(FoodIdentity.matchesDeclaredIdentity("pan", bread))
        val (identity, grams) = pick(bread, "pan")
        assertSame(bread, identity)
        assertEquals(50.0, grams, 0.0)
        assertEquals(50.0, HouseholdPortions.unitGrams(bread, "pan"), 0.0)
    }

    @Test
    fun `nuggets keep the SKU nutrients and never borrow raw chicken`() {
        val nuggets = off("off_nuggets", "Nuggets de pollo", "Sadia", 204.0, 13.0, 16.0, 9.5)
        assertTrue(FoodIdentity.matchesDeclaredIdentity("nuggets", nuggets))
        val (identity, grams) = pick(nuggets, "nuggets")
        assertSame(nuggets, identity)
        assertNotEquals("gen003", identity.id)
        assertEquals(204.0, identity.calories, 0.0)
        assertTrue("nuggets grams $grams", grams.isFinite() && grams > 0.0)
        assertEquals(204.0 * grams / 100.0, scaleFoodByPortion(identity, amountGrams = grams).calories, 0.5)
    }

    @Test
    fun `an explicit kilogram query keeps the row and its declared pack mass`() {
        val pack = off("off_hallulla_kg", "Hallulla Ideal 1kg", "Ideal", 400.0, 8.0, 70.0, 8.0, portionGrams = 1000.0)
        val query = "Hallulla Ideal 1kg"
        val (identity, grams) = pick(pack, query)
        assertSame(pack, identity)
        assertEquals(1000.0, grams, 0.0)
        assertEquals(
            FoodResolutionStatus.AUTO,
            HouseholdPortions.operationalAutoStatus(
                identity, grams, null, HouseholdPortions.isExplicitKilogram(query), AmountIntent.UNSPECIFIED, explicitPick = true,
            ),
        )
    }

    @Test
    fun `the queries that used to be a silent no-op now keep their row with a usable portion`() {
        val rows = listOf(
            "red bull" to off("off_red_bull", "Red Bull Energy Drink", "Red Bull", 45.0, 0.0, 11.0, 0.0, portionGrams = 250.0),
            "coca cola" to off("off_coca_cola", "Coca-Cola Original 350 ml", "Coca-Cola", 42.0, 0.0, 10.6, 0.0, portionGrams = 350.0),
            "leche colun" to off("off_leche_colun", "Leche descremada Colun", "Colun", 35.0, 3.4, 4.9, 0.1),
        )
        rows.forEach { (query, row) ->
            val (identity, grams) = pick(row, query)
            assertSame("$query must keep ${row.id}", row, identity)
            assertTrue("$query grams $grams", grams.isFinite() && grams > 0.0)
        }
    }

    @Test
    fun `a household portion declared by the row is honoured and a pack-sized one never is`() {
        val yogurt = off("off_yogurt_125", "Yogurt natural", "Colun", 59.0, 3.5, 4.7, 2.9, portionGrams = 125.0)
        assertEquals(125.0, pick(yogurt, "yogurt").second, 0.0)
        val carton = off("off_leche_1l", "Leche entera 1 litro", "Colun", 61.0, 3.2, 4.8, 3.3, portionGrams = 1000.0)
        assertEquals(200.0, pick(carton, "leche").second, 0.0)
    }

    @Test
    fun `a per 100 g row that declares its own serving and a custom serving stay untouched`() {
        val bar = FoodItem(
            id = "bar", name = "Barra de cereal", servingSize = 30.0, nutritionBasis = "PER_100G",
            calories = 390.0, protein = 6.0, carbs = 70.0, fats = 9.0, source = "KPKN Curated",
        )
        assertEquals(30.0, pick(bar, "barra de cereal").second, 0.0)
        val custom = FoodItem(
            id = "my-bar", name = "Mi barrita", isCustom = true, servingSize = 40.0, nutritionBasis = "PER_100G_AS_SOLD",
            calories = 180.0, protein = 8.0, carbs = 16.0, fats = 9.0,
        )
        assertEquals(40.0, pick(custom, "mi barrita").second, 0.0)
    }
    // ─── WP-S10: the per-100 g rows of the static catalog tap to a household portion ──────────────────

    /** One representative row: its id, a word its name must hold, what the person typed, the unit it is measured in and the grams eaten. */
    private data class Tap(val id: String, val nameHas: String, val query: String, val unit: String, val grams: Double)

    private val perHundredTaps = listOf(
        Tap("gen004", "pechuga", "pechuga", "g", 150.0),
        Tap("gen005", "arroz", "arroz", "g", 120.0),
        Tap("gen085", "semidescremada", "leche", "ml", 200.0),
        Tap("gen049", "mantequilla", "mantequilla", "g", 10.0),
        Tap("gen099", "aceite", "aceite", "ml", 10.0),
        Tap("gen047", "queso", "queso", "g", 30.0),
        Tap("gen040", "pasta", "pasta", "g", 160.0),
        // WP-N8b: a plate of cooked legumes is one cup, 198 g (USDA household weight of cooked lentils, FDC 172421); it was the 120 g of a grain.
        Tap("gen012", "lentejas", "lentejas", "g", 198.0),
    )

    @Test
    fun `tapping a per 100 g catalog row eats a household portion and never the 100 g basis`() {
        // WP-S10 declares these rows PER_100G_AS_SOLD (their macros are per 100 g and 100 g is all the serving they had), so by the rule
        // of WP-S1 their 100 g is a denominator: the tap eats what a person eats of that food. Before, every one of them logged 100 g.
        perHundredTaps.forEach { tap ->
            val row = checkNotNull(findStaticFoodById(tap.id)) { tap.id }
            val label = "${tap.id} ${row.name} (typed \"${tap.query}\")"
            assertTrue("$label: not a ${tap.nameHas}", FoodIdentity.normalize(row.name).contains(tap.nameHas))
            assertEquals("$label: unit", tap.unit, row.unit)
            assertEquals("$label: basis", "PER_100G_AS_SOLD", row.nutritionBasis)

            val (identity, grams) = pick(row, tap.query)

            assertSame("$label keeps the tapped row", row, identity)
            assertEquals("$label: grams", tap.grams, grams, 0.0)
            assertNotEquals("$label: the 100 g basis is not a portion", 100.0, grams, 0.0)
        }
    }

    @Test
    fun `a catalog row measured in units taps to the weight of one unit`() {
        // The per-100 g rule only reaches a serving of exactly 100 g or 100 ml: a row that declares ONE unit keeps that weight, whether it
        // stays PER_SERVING (a slice of sliced bread) or is explicitly per 100 g (a mandarin).
        listOf(Triple("gen089", "pan de molde", 25.0), Triple("gen166", "mandarina", 90.0)).forEach { (id, nameHas, grams) ->
            val row = checkNotNull(findStaticFoodById(id)) { id }
            assertTrue("$id: not a $nameHas", FoodIdentity.normalize(row.name).contains(nameHas))
            assertEquals("$id: unit", "u", row.unit)
            assertEquals("$id: grams", grams, pick(row, nameHas).second, 0.0)
        }
    }

    @Test
    fun `an explicit kilogram or litre keeps the pack mass while the same food typed plainly eats the household portion`() {
        // The new defaults belong to the portion of a plain tap; a query that names a bulk unit still keeps the declared pack mass.
        val ricePack = off("off_arroz_1kg", "Arroz grado 1 Tucapel 1 kg", "Tucapel", 350.0, 7.0, 78.0, 0.6, portionGrams = 1000.0)
        val milkPack = off("off_leche_semi_1l", "Leche semidescremada Colun 1 litro", "Colun", 46.0, 3.2, 4.8, 1.5, portionGrams = 1000.0)
        val oilPack = off("off_aceite_1l", "Aceite vegetal Chef 1 litro", "Chef", 884.0, 0.0, 0.0, 100.0, portionGrams = 1000.0)
        val packs = listOf(
            Triple(ricePack, "arroz 1 kg", "gen005" to "arroz"),
            Triple(milkPack, "leche semidescremada 1 litro", "gen085" to "leche"),
            Triple(oilPack, "aceite 1 litro", "gen099" to "aceite"),
        )
        packs.forEach { (pack, bulkQuery, catalog) ->
            val (id, plainQuery) = catalog
            assertEquals("$bulkQuery keeps the pack", 1000.0, pick(pack, bulkQuery).second, 0.0)
            // Typed plainly, the pack and the catalog row of the same food eat the same household portion (never the 1000 g).
            val household = pick(checkNotNull(findStaticFoodById(id)) { id }, plainQuery).second
            assertEquals("$plainQuery on the pack", household, pick(pack, plainQuery).second, 0.0)
            assertTrue("$plainQuery household grams $household", household in 10.0..200.0)
        }
    }

    @Test
    fun `an ml row of the catalog is per 100 g so its denominator is 100 g and not the mass of 100 ml`() {
        val milk = checkNotNull(findStaticFoodById("gen085"))
        assertTrue(FoodIdentity.normalize(milk.name).contains("semidescremada"))
        assertEquals("ml", milk.unit)
        assertEquals("PER_100G_AS_SOLD", milk.nutritionBasis)
        // As PER_SERVING its 100 ml weighed 103 g and a glass came out 3 % short; its numbers are per 100 g.
        assertEquals(100.0, NutrientBasis.grams(milk), 0.0)
        val glass = HouseholdPortions.eatenGramsForSearchPick(milk, "leche")
        assertEquals(200.0, glass, 0.0)
        assertEquals(2.0 * milk.calories, scaleFoodByPortion(milk, amountGrams = glass).calories, 1.0)
        // Oil went the other way: 100 ml weighed 90 g, so every spoon was overstated by a tenth.
        val oil = checkNotNull(findStaticFoodById("gen099"))
        assertEquals("ml", oil.unit)
        assertEquals(100.0, NutrientBasis.grams(oil), 0.0)
        assertEquals(0.1 * oil.calories, scaleFoodByPortion(oil, amountGrams = 10.0).calories, 1.0)
    }
}

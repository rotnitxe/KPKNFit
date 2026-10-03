package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.db.GlobalFoodEntity
import com.example.kpkn.data.db.toFoodItem
import com.example.kpkn.data.food.findStaticFoodById
import com.example.kpkn.data.models.FoodItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WP-S2 / B2: the ranking of the Search tab as pure functions. Rows are hand-made OFF rows exactly as the importer
 * stores them (through [GlobalFoodEntity.toFoodItem]) next to the real curated catalog; the full-catalog behaviour is
 * pinned by [SearchGoldenCorpusTest].
 */
class FoodSearchRankerTest {

    private fun off(
        id: String,
        name: String,
        brand: String? = null,
        qualityFlags: List<String> = emptyList(),
    ): FoodItem {
        val normalizedName = TextKeys.normalize(name)
        val normalizedBrand = brand?.let(TextKeys::normalize)
        return GlobalFoodEntity(
            foodId = id,
            name = name,
            brand = brand,
            normalizedName = normalizedName,
            normalizedBrand = normalizedBrand,
            aliasesJson = Json.encodeToString(listOfNotNull(normalizedName, normalizedBrand)),
            calories = 100.0,
            protein = 5.0,
            carbs = 10.0,
            fats = 3.0,
            source = "OFF Chile",
            sourcePriority = 80,
            nutritionBasis = "PER_100G_AS_SOLD",
            qualityFlagsJson = if (qualityFlags.isEmpty()) "[]" else qualityFlags.joinToString(",", "[", "]") { "\"$it\"" },
        ).toFoodItem()
    }

    private fun curated(id: String): FoodItem = findStaticFoodById(id)!!

    private fun query(text: String, anchorId: String? = null) = FoodSearchRanker.query(text, anchorId)

    private fun score(food: FoodItem, q: FoodSearchRanker.Query, learned: String? = null): Double? =
        FoodSearchRanker.score(food, q, learned)?.score

    // 1. stem

    @Test
    fun `stem folds Spanish plurals and keeps the words that only look plural`() {
        val folded = mapOf(
            "huevos" to "huevo", "papas" to "papa", "tomates" to "tomate", "lentejas" to "lenteja",
            "panes" to "pan", "limones" to "limon", "cereales" to "cereal", "nueces" to "nuez", "peces" to "pez",
            "huevo" to "huevo", "tomate" to "tomate", "arroz" to "arroz", "pan" to "pan",
        )
        folded.forEach { (plural, singular) -> assertEquals(plural, singular, FoodSearchRanker.stem(plural)) }
        // Exceptions and short words are never touched.
        listOf("frances", "ingles", "hummus", "mas", "pais", "gris", "res", "gas", "pie").forEach {
            assertEquals(it, it, FoodSearchRanker.stem(it))
        }
        // "dulces" -> "dulz" is a known miss of the "ces" rule; a plural still hits its singular row.
        assertNotNull(FoodSearchRanker.score(curated("gen142"), query("dulces"), null))
    }

    // 2. whole-word hits, never substrings

    @Test
    fun `pan ranks the curated white bread above an OFF pan integral and never matches empanaditas`() {
        val gen019 = curated("gen019")
        val bauducco = off("off_pan_integral_bauducco", "Pan integral", "Bauducco")
        val empanaditas = off("off_empanaditas", "Empanaditas de queso", "Delicias")
        val biopan = off("off_biopan", "Biopan multigrano", "Castillo")
        val q = query("pan", anchorId = "gen019")

        assertNull("empanaditas hold the letters of pan, not the word", FoodSearchRanker.score(empanaditas, q, null))
        assertNull(FoodSearchRanker.score(biopan, q, null))
        assertTrue(score(gen019, q)!! > score(bauducco, q)!!)
        val ranked = FoodSearchRanker.rank(q, listOf(empanaditas, bauducco, gen019, biopan), null, 10, loggerFilter = false)
        assertEquals(listOf("gen019", "off_pan_integral_bauducco"), ranked.map { it.foodId })
    }

    // 3. brand and anchor

    @Test
    fun `leche colun ranks the Colun SKU above gen016 but leche ranks gen016 above it`() {
        val gen016 = curated("gen016")
        val colun = off("off_leche_descremada_colun", "Leche descremada", "Colun")

        val withBrand = query("leche colun")
        assertTrue(score(colun, withBrand)!! > score(gen016, withBrand)!!)
        assertTrue("brand" in FoodSearchRanker.score(colun, withBrand, null)!!.trace)

        val plain = query("leche", anchorId = "gen016")
        assertTrue(score(gen016, plain)!! > score(colun, plain)!!)
        assertTrue("anchor" in FoodSearchRanker.score(gen016, plain, null)!!.trace)
    }

    @Test
    fun `the anchor only counts when its row hits the head word the person typed`() {
        // "chocolate leche": gen016 hits "leche" but not the head "chocolate"; milk is not what was asked for.
        val q = query("chocolate leche", anchorId = "gen016")
        assertFalse("anchor" in FoodSearchRanker.score(curated("gen016"), q, null)!!.trace)
    }

    @Test
    fun `an OFF row named exactly like the query does not outrank the curated plain food`() {
        val gen087 = curated("gen087")
        val offYogurt = off("off_yogurt_colun", "yogurt", "Colun")
        val generic = query("yogurt")
        assertTrue(score(gen087, generic)!! > score(offYogurt, generic)!!)
        // Naming the brand makes that row the answer.
        val branded = query("yogurt colun")
        assertTrue(score(offYogurt, branded)!! > score(gen087, branded)!!)
    }

    // 4. order

    @Test
    fun `equal scores break by name length then id and the order never depends on the pool order`() {
        val rows = listOf(
            off("off_b", "Leche Alfa", "Marca"),
            off("off_a", "Leche Beta", "Marca"),
            off("off_c", "Leche Zu", "Marca"),
        )
        val q = query("leche")
        val ranked = FoodSearchRanker.rank(q, rows, null, 10, loggerFilter = false).map { it.foodId }
        assertEquals(listOf("off_c", "off_a", "off_b"), ranked)
        assertEquals(ranked, FoodSearchRanker.rank(q, rows.reversed(), null, 10, loggerFilter = false).map { it.foodId })
        assertEquals(ranked, FoodSearchRanker.rank(q, rows, null, 10, loggerFilter = false).map { it.foodId })
    }

    @Test
    fun `a state word qualifies the food but never identifies it`() {
        val q = query("pechuga de pollo cruda", anchorId = "gen003")
        // "Espinaca (cruda)" only shares the state word: not a candidate.
        assertNull(FoodSearchRanker.score(curated("gen023"), q, null))
        // The cooked breast shares the food but not the state: it ranks below the raw one.
        assertTrue(score(curated("gen003"), q)!! > score(curated("gen004"), q)!!)
        assertTrue("state" in FoodSearchRanker.score(curated("gen004"), q, null)!!.trace)
    }

    @Test
    fun `a pack-sized name is demoted unless the query asks for a pack`() {
        val plain = off("off_hallulla", "Hallulla Ideal", "Tottus")
        val pack = off("off_hallulla_1kg", "Hallulla 1kg", "Tottus")
        val q = query("hallulla")
        assertEquals(0.10, score(plain, q)!! - score(pack, q)!!, 1e-9)
        assertTrue("pack" in FoodSearchRanker.score(pack, q, null)!!.trace)
        assertFalse("pack" in FoodSearchRanker.score(pack, query("hallulla 1kg"), null)!!.trace)
    }

    @Test
    fun `a row whose state differs from the declared one loses 0 20 and an equal state loses nothing`() {
        fun quinoa(state: String) = FoodItem(id = "q_$state", name = "Quinoa", foodState = state, calories = 120.0, protein = 4.0, carbs = 21.0, fats = 2.0)
        val q = query("quinoa cocida")
        assertEquals(0.20, score(quinoa("COOKED"), q)!! - score(quinoa("RAW"), q)!!, 1e-9)
        assertFalse("state" in FoodSearchRanker.score(quinoa("COOKED"), q, null)!!.trace)
    }

    @Test
    fun `the score is the sum of its documented parts`() {
        // coverage 1.0*0.32 + precision 1.0*0.14 + all-tokens tier 0.15 + OFF prior 0.06 + brand named 0.15
        val colun = off("off_leche_descremada_colun", "Leche descremada", "Colun")
        assertEquals(0.82, score(colun, query("leche colun"))!!, 1e-9)
        // coverage 0.32 + precision 0.5*0.14 + name starts with the query 0.20 + curated prior 0.20 + anchor 0.30
        assertEquals(1.09, score(curated("gen016"), query("leche", anchorId = "gen016"))!!, 1e-9)
        // the same OFF row for a query that names no brand: prefix tier 0.20 and the unbranded-OFF penalty -0.05
        val soprole = off("off_leche_descremada_soprole", "Leche descremada", "Soprole")
        assertEquals(0.32 + 0.07 + 0.20 + 0.06 - 0.05, score(soprole, query("leche"))!!, 1e-9)
        // usage: ln(9 + 1)/ln(10) = 1.0 of 0.08
        val used = soprole.copy(usageCount = 9)
        assertEquals(0.08, score(used, query("leche"))!! - score(soprole, query("leche"))!!, 1e-9)
    }

    // 5. the filter runs before the limit

    @Test
    fun `loggerFilter drops LOW_QUALITY and mismatched identities BEFORE the limit`() {
        val lowQuality = (1..3).map { off("off_low_$it", "Leche", "Marca $it", qualityFlags = listOf("LOW_QUALITY")) }
        val dulce = off("off_dulce", "Dulce de leche", "Lapataia")
        val good = listOf(
            off("off_good_1", "Leche descremada", "Soprole"),
            off("off_good_2", "Leche entera", "Colun"),
        )
        val q = query("leche")
        val pool = lowQuality + dulce + good

        // Without the filter the best rows are taken as they come, junk included.
        val unfiltered = FoodSearchRanker.rank(q, pool, null, 2, loggerFilter = false)
        assertTrue(unfiltered.any { it.foodId.startsWith("off_low_") })

        // With it the screen still gets two usable rows: the rejects did not take their places.
        val filtered = FoodSearchRanker.rank(q, pool, null, 2, loggerFilter = true)
        assertEquals(listOf("off_good_1", "off_good_2"), filtered.map { it.foodId }.sorted())
        assertTrue(filtered.all { NutrientBasis.isVerified(it.food) && FoodIdentity.matchesDeclaredIdentity("leche", it.food) })
        assertFalse(filtered.any { it.foodId == "off_dulce" })
    }

    // 6. learning

    @Test
    fun `the learned selection adds exactly 0 22 and shows in the trace`() {
        val gen046 = curated("gen046")
        val q = query("leche")
        val plain = FoodSearchRanker.score(gen046, q, null)!!
        val learned = FoodSearchRanker.score(gen046, q, "gen046")!!
        assertEquals(0.22, learned.score - plain.score, 1e-9)
        assertTrue(learned.learned && "learned" in learned.trace)
        assertFalse(plain.learned || "learned" in plain.trace)
        // Learning another food adds nothing to this one.
        assertEquals(plain.score, FoodSearchRanker.score(gen046, q, "gen016")!!.score, 1e-9)
    }

    // Guards

    @Test
    fun `nothing hits, nothing is returned`() {
        assertNull(FoodSearchRanker.score(curated("gen016"), query("xyzq"), null))
        assertTrue(FoodSearchRanker.rank(query("   "), listOf(curated("gen016")), null, 5, loggerFilter = false).isEmpty())
        assertTrue(FoodSearchRanker.rank(query("de la"), listOf(curated("gen016")), null, 5, loggerFilter = false).isEmpty())
        assertTrue(FoodSearchRanker.rank(query("leche"), listOf(curated("gen016")), null, 0, loggerFilter = false).isEmpty())
    }

    @Test
    fun `collapseDuplicates keeps the anchor, then the curated lowest id, then the highest id of the rest`() {
        val twinA = curated("gen020")
        val twinB = curated("gen133")
        assertEquals(listOf("gen020"), FoodSearchRanker.collapseDuplicates(listOf(twinB, twinA), null).map { it.id })
        assertEquals(listOf("gen133"), FoodSearchRanker.collapseDuplicates(listOf(twinA, twinB), "gen133").map { it.id })

        val offLow = off("off_1", "Red Bull Energy Drink", "Red Bull")
        val offHigh = off("off_9", "Red Bull Energy Drink", "Red Bull")
        assertEquals(listOf("off_9"), FoodSearchRanker.collapseDuplicates(listOf(offLow, offHigh), null).map { it.id })
        // Different brand, same name: two products.
        val other = off("off_5", "Red Bull Energy Drink", "Otra")
        assertEquals(2, FoodSearchRanker.collapseDuplicates(listOf(offLow, offHigh, other), null).size)
    }
}

package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.food.buildFoodDatabase
import com.example.kpkn.data.food.findFoodExactByNormalized
import com.example.kpkn.data.models.FoodCandidate
import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.data.models.SearchSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

/**
 * WP-S2 golden corpus of the Search tab (pure JVM). The pool is what the app searches: the curated static catalog
 * (GENERIC_FOODS + CHILEAN_FOODS), the curated branded catalogs and a deterministic slice of OFF Chile
 * (`src/test/resources/food_data/off_chile_search_fixture.tsv`, built by `scripts/build_off_search_fixture.py`) that
 * includes the rows that used to bury the curated ones: OFF rows named exactly "Pan", "Arroz", "yogurt", "Aguacate",
 * "Hallulla", substring decoys (empanaditas, biopan, panchitos for "pan") and "DULCE DE LECHE & CO.".
 *
 * Each case is a query of the logger tab (`loggerFilter = true`, limit 15) and what the person must see. The pipeline
 * mirrors `NutritionRepository.searchFoodCandidates` without the DAO: anchor, duplicates collapsed, ranked, filter
 * before the limit.
 */
class SearchGoldenCorpusTest {

    private companion object {
        val pool: List<FoodItem> by lazy { buildFoodDatabase() + OffSearchFixture.foods() }
    }

    private fun search(query: String, limit: Int = 15, loggerFilter: Boolean = true): List<FoodCandidate> {
        val anchor = (HouseholdPortions.householdStaticFood(query) ?: findFoodExactByNormalized(query))?.id
        val q = FoodSearchRanker.query(query, anchor)
        return FoodSearchRanker.rank(q, FoodSearchRanker.collapseDuplicates(pool, anchor), null, limit, loggerFilter)
    }

    private fun ids(query: String, limit: Int = 15, loggerFilter: Boolean = true) =
        search(query, limit, loggerFilter).map { it.foodId }

    private fun assertTop(query: String, expected: String) {
        val found = ids(query)
        assertEquals("\"$query\" -> $found", expected, found.firstOrNull())
    }

    private fun isOff(candidate: FoodCandidate) = candidate.foodId.startsWith("off_")

    // Leche

    @Test
    fun `01 leche puts the curated whole milk first`() = assertTop("leche", "gen016")

    @Test
    fun `02 leche shows several curated milks before the first OFF row`() {
        val results = search("leche")
        val firstOff = results.indexOfFirst(::isOff)
        assertTrue("OFF row at rank ${firstOff + 1}: ${results.map { it.foodId }}", firstOff >= 3)
    }

    @Test
    fun `03 leche never offers dulce de leche as milk`() {
        val accepted = search("leche", 50)
        assertTrue(accepted.none { TextKeys.normalize(it.food.name).startsWith("dulce") })
        assertFalse("off_0721450761012" in accepted.map { it.foodId })
        // The row exists in the pool and matches the word; it is the head-noun gate that keeps it out.
        assertTrue("off_0721450761012" in ids("leche", 50, loggerFilter = false))
    }

    @Test
    fun `04 leche colun shows Colun products first`() {
        val results = search("leche colun")
        assertTrue(results.size >= 3)
        assertTrue(results.take(3).all { it.brandMatched && TextKeys.normalize(it.food.brand.orEmpty()).contains("colun") })
    }

    @Test
    fun `05 leche colun never ranks the generic milk above a Colun SKU`() {
        val found = ids("leche colun")
        val genericAt = found.indexOf("gen016")
        assertTrue(genericAt == -1 || genericAt > found.indexOfFirst { it.startsWith("off_") })
    }

    @Test
    fun `06 leche descremada finds the curated skim milk and the Colun SKU`() {
        val found = ids("leche descremada")
        assertEquals("gen046", found.first())
        assertTrue(found.take(3).any { it.startsWith("off_") })
    }

    @Test
    fun `07 dulce de leche is its own query and answers with the manjar and its rows`() {
        val found = ids("dulce de leche")
        assertEquals("gen109", found.first())
        assertTrue("off_0721450761012" in found)
    }

    // Huevo

    @Test
    fun `08 huevo puts the curated egg first`() = assertTop("huevo", "gen007")

    @Test
    fun `09 huevos folds the plural`() = assertTop("huevos", "gen007")

    @Test
    fun `10 huevos fritos finds the fried egg`() = assertTop("huevos fritos", "gen007f")

    @Test
    fun `11 huevo frito finds the fried egg`() = assertTop("huevo frito", "gen007f")

    // Pan

    @Test
    fun `12 pan puts the curated white bread first`() = assertTop("pan", "gen019")

    @Test
    fun `13 pan shows no empanaditas, panchitos, pancakes or biopan`() {
        val decoys = pool.filter { food -> TextKeys.normalize(food.name).let { n -> listOf("empanad", "panch", "pancake", "biopan").any { it in n } } }
        assertTrue("the fixture must hold substring decoys", decoys.size >= 3)
        val top = search("pan", 5)
        assertTrue(top.none { candidate -> decoys.any { it.id == candidate.foodId } })
        // Not even without the identity filter: the hit is a whole word, never a substring.
        assertTrue(search("pan", 50, loggerFilter = false).none { candidate -> decoys.any { it.id == candidate.foodId } })
    }

    @Test
    fun `14 pan ranks curated breads before every OFF row`() {
        val top = search("pan", 3)
        assertTrue("top3 ${top.map { it.foodId }}", top.size == 3 && top.none(::isOff))
    }

    @Test
    fun `15 marraqueta finds the Chilean bread`() = assertTop("marraqueta", "cl010")

    @Test
    fun `16 hallulla finds the Chilean bread and never a pack first`() {
        val results = search("hallulla")
        assertEquals("cl013", results.first().foodId)
        assertTrue(results.take(3).none { HouseholdPortions.looksLikePackName(it.food.name) })
    }

    @Test
    fun `17 pan integral finds the curated wholemeal bread and the Bauducco one`() {
        val found = ids("pan integral")
        assertTrue(found.first() in setOf("gen020", "gen133"))
        assertTrue(found.take(5).any { it == "off_7891962064055" })
    }

    // Pollo

    @Test
    fun `18 pollo puts the household chicken first`() = assertTop("pollo", "gen004")

    @Test
    fun `19 pollo ranks the cooked breast above the raw one`() {
        val found = ids("pollo")
        val rawAt = found.indexOf("gen003")
        assertTrue(rawAt == -1 || rawAt > found.indexOf("gen004"))
    }

    @Test
    fun `20 pechuga de pollo cruda finds the raw breast`() = assertTop("pechuga de pollo cruda", "gen003")

    @Test
    fun `21 pechuga de pollo finds the cooked breast`() = assertTop("pechuga de pollo", "gen004")

    @Test
    fun `22 nuggets finds supermarket SKUs and never raw chicken`() {
        val results = search("nuggets")
        assertTrue(results.isNotEmpty())
        assertTrue(results.all { it.source == SearchSource.OFF })
        assertTrue("gen003" !in results.take(3).map { it.foodId })
        assertTrue(results.first().food.name.contains("nugget", ignoreCase = true))
    }

    @Test
    fun `23 nuggets de pollo finds the chicken nuggets SKU`() {
        val results = search("nuggets de pollo")
        assertTrue(results.isNotEmpty())
        assertTrue(TextKeys.normalize(results.first().food.name).let { "nuggets" in it && "pollo" in it })
        assertTrue("gen003" !in results.take(3).map { it.foodId })
    }

    // Staples

    @Test
    fun `24 arroz puts the curated cooked rice first`() = assertTop("arroz", "gen005")

    @Test
    fun `25 papas fritas finds the fried potato`() = assertTop("papas fritas", "gen021f")

    @Test
    fun `26 papas folds the plural`() = assertTop("papas", "gen021")

    @Test
    fun `27 tomate puts the curated tomato first`() = assertTop("tomate", "gen026")

    @Test
    fun `28 tomates folds the plural`() = assertTop("tomates", "gen026")

    @Test
    fun `29 palta puts the curated avocado first`() = assertTop("palta", "gen014")

    @Test
    fun `30 aguacate finds the same avocado ahead of the OFF row named Aguacate`() {
        val found = ids("aguacate")
        assertEquals("gen014", found.first())
        assertTrue("off_2303858002676" in found.take(3))
    }

    @Test
    fun `31 platano finds the curated banana`() = assertTop("plátano", "gen002")

    @Ignore("WP-S6: banana is a declared alias of plátano; the ranker only matches words the row carries, and the pick path needs the alias-aware identity")
    @Test
    fun `32 banana finds the curated banana`() = assertTop("banana", "gen002")

    @Test
    fun `33 lentejas finds the curated lentils`() = assertTop("lentejas", "gen012")

    // Lácteos, snacks, marcas

    @Test
    fun `34 yogurt shows both curated yogurts before any OFF row`() {
        val found = ids("yogurt")
        assertEquals(setOf("gen017", "gen087"), found.take(2).toSet())
        assertTrue(found.drop(2).any { it.startsWith("off_") })
    }

    @Test
    fun `35 galletas shows curated cookies and never bread`() {
        val results = search("galletas")
        assertTrue(results.size >= 5)
        assertTrue(results.none(::isOff))
        assertTrue(results.none { it.foodId in setOf("gen019", "gen020", "gen133", "gen089", "cl010", "cl013") })
    }

    @Test
    fun `36 coca cola finds the Coca-Cola products`() {
        val results = search("coca cola")
        assertTrue(results.isNotEmpty())
        assertTrue(results.first().brandMatched)
        assertEquals("coca cola", TextKeys.normalize(results.first().food.brand.orEmpty()))
        assertTrue(results.all { candidate -> candidate.food.name.isNotBlank() && !candidate.food.brand.isNullOrBlank() })
    }

    @Test
    fun `37 red bull finds the OFF product because the curated energy drinks are not verified`() {
        val results = search("red bull")
        assertTrue(results.isNotEmpty())
        assertTrue(results.first().brandMatched && isOff(results.first()))
        // The unverified curated rows are the best scoring ones: the filter, applied before the limit, is what skips them.
        val unfiltered = search("red bull", 15, loggerFilter = false)
        assertTrue(unfiltered.first().foodId.startsWith("enr_rb_"))
        assertTrue(unfiltered.take(5).none(::isOff))
    }

    // Platos chilenos y suplementos

    @Test
    fun `38 completo finds the Chilean hot dog`() = assertTop("completo", "cl002")

    @Test
    fun `39 empanada finds the empanada de pino`() = assertTop("empanada", "cl001")

    @Test
    fun `40 empanadas folds the plural`() = assertTop("empanadas", "cl001")

    @Test
    fun `41 sopaipilla finds the sopaipillas`() = assertTop("sopaipilla", "cl006")

    @Test
    fun `42 cazuela finds the plain cazuela first and the variants right after`() {
        val found = ids("cazuela")
        assertEquals("cl004", found.first())
        assertTrue("cl030" in found.take(3))
    }

    @Test
    fun `43 whey finds the curated protein powder first`() = assertTop("whey", "gen105")

    @Test
    fun `44 xyzq finds nothing`() {
        assertTrue(search("xyzq").isEmpty())
        assertTrue(search("xyzq", 15, loggerFilter = false).isEmpty())
    }

    // Bebidas gaseosas (WP-S2b): una marca que la consulta nombra y el pool tiene va antes que la ficha genérica

    @Test
    fun `bebida, gaseosa and sprite answer with the generic soda, the pool holds no brand they name`() {
        listOf("bebida", "gaseosa", "sprite").forEach { assertTop(it, "gen146") }
    }

    @Test
    fun `coca cola zero finds the generic zero soda while the pool holds no Zero row`() {
        // The fixture only has the regular Coca-Cola: the generic zero soda is the one that answers "zero"; the day the
        // pool holds a Coca-Cola Zero row it goes first (FoodSearchRankerTest).
        assertTop("coca cola zero", "gen147")
    }

    // Todo el corpus

    private val corpusQueries = listOf(
        "leche", "leche colun", "leche descremada", "dulce de leche", "huevo", "huevos", "huevos fritos", "huevo frito",
        "pan", "marraqueta", "hallulla", "pan integral", "pollo", "pechuga de pollo cruda", "pechuga de pollo",
        "nuggets", "nuggets de pollo", "arroz", "papas fritas", "papas", "tomate", "tomates", "palta", "aguacate",
        "plátano", "lentejas", "yogurt", "galletas", "coca cola", "red bull", "completo", "empanada", "empanadas",
        "sopaipilla", "cazuela", "whey", "xyzq", "coca cola zero", "bebida", "gaseosa", "sprite",
    )

    @Test
    fun `every logger result has verified nutrients and the identity that was asked for`() {
        corpusQueries.forEach { query ->
            search(query).forEach { candidate ->
                assertTrue("$query -> ${candidate.foodId}", NutrientBasis.isVerified(candidate.food))
                assertTrue("$query -> ${candidate.foodId}", FoodIdentity.matchesDeclaredIdentity(query, candidate.food))
            }
        }
    }

    @Test
    fun `two runs return the same rows in the same order with the same scores`() {
        corpusQueries.forEach { query ->
            val first = search(query)
            val second = search(query)
            assertEquals(query, first.map { it.foodId }, second.map { it.foodId })
            assertEquals(query, first.map { it.score }, second.map { it.score })
        }
    }

    @Test
    fun `the verified and identity filter runs before the limit`() {
        // The old pipeline took the best 15 and filtered afterwards, so rows the logger rejects took places on the screen.
        listOf("pan", "leche", "pollo", "arroz", "yogurt", "galletas", "red bull", "huevos").forEach { query ->
            val usable = search(query, 500, loggerFilter = false)
                .filter { NutrientBasis.isVerified(it.food) && FoodIdentity.matchesDeclaredIdentity(query, it.food) }
            assertEquals(query, usable.take(15).map { it.foodId }, search(query, 15).map { it.foodId })
        }
    }

    @Test
    fun `skipping rows that lack a required word never changes what the full identity rules return`() {
        // rank() skips, without running the identity rules, the rows that lack a word those rules require: numbers, states,
        // units and "sin" are not required. The result must be exactly the one the full rules give.
        (corpusQueries + listOf("leche 1 litro", "leche 200 ml", "2 huevos", "pollo 200 g", "arroz cocido", "leche sin lactosa")).forEach { query ->
            val anchor = (HouseholdPortions.householdStaticFood(query) ?: findFoodExactByNormalized(query))?.id
            val q = FoodSearchRanker.query(query, anchor)
            val collapsed = FoodSearchRanker.collapseDuplicates(pool, anchor)
            val everyRule = FoodSearchRanker.rank(q, collapsed, null, Int.MAX_VALUE, loggerFilter = false)
                .filter { NutrientBasis.isVerified(it.food) && FoodIdentity.matchesDeclaredIdentity(query, it.food) }
            assertEquals(query, everyRule.take(15).map { it.foodId }, FoodSearchRanker.rank(q, collapsed, null, 15, loggerFilter = true).map { it.foodId })
        }
    }

    @Test
    fun `the fixture is the committed slice of OFF`() {
        val rows = OffSearchFixture.entities()
        assertTrue("rows ${rows.size}", rows.size in 250..450)
        assertEquals("every committed line is a row the importer would keep", OffSearchFixture.lines().size, rows.size)
        assertEquals(rows.size, rows.map { it.foodId }.toSet().size)
        assertNotEquals(0, rows.count { it.brand != null })
    }
}

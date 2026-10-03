package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.db.GlobalFoodEntity
import com.example.kpkn.data.models.FoodItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FoodIndexTest {

    @Test
    fun `normalizeSearch strips diacritics`() {
        assertEquals("arroz", FoodIndex.normalizeSearch("arroz"))
        assertEquals("papa", FoodIndex.normalizeSearch("papá"))
        assertEquals("nino", FoodIndex.normalizeSearch("niño"))
    }

    @Test
    fun `tokenize removes stopwords`() {
        val tokens = FoodIndex.tokenize("arroz de pollo con sal")
        assertTrue(tokens.contains("arroz"))
        assertTrue(tokens.contains("pollo"))
        assertTrue(tokens.contains("sal"))
        // Stopwords removed
        assertTrue(!tokens.contains("de"))
        assertTrue(!tokens.contains("con"))
    }

    @Test
    fun `tokenize filters short tokens`() {
        val tokens = FoodIndex.tokenize("a de la el arroz")
        assertTrue(!tokens.contains("a"))
        assertTrue(!tokens.contains("de"))
        assertTrue(!tokens.contains("la"))
        assertTrue(!tokens.contains("el"))
        assertTrue(tokens.contains("arroz"))
    }

    @Test
    fun `generateTrigrams creates correct trigrams`() {
        val trigrams = FoodIndex.generateTrigrams("hola")
        // "$hola$" → "$ho", "hol", "ola", "la$"
        assertTrue(trigrams.contains("\$ho"))
        assertTrue(trigrams.contains("hol"))
        assertTrue(trigrams.contains("ola"))
        assertTrue(trigrams.contains("la\$"))
    }

    @Test
    fun `generateTrigrams for short token`() {
        val trigrams = FoodIndex.generateTrigrams("ab")
        assertEquals(setOf("ab"), trigrams)
    }

    // ─── E16/IT2: custom foods indexados en runtime ─────────────────────────

    private fun customFood() = FoodItem(
        id = "custom1",
        name = "Almuerzo de Mamá",
        brand = null,
        servingSize = 150.0,
        unit = "g",
        calories = 300.0,
        protein = 15.0,
        carbs = 30.0,
        fats = 12.0,
        searchAliases = listOf("almuerzo de mama", "almuerzo de la mama"),
    )

    @Test
    fun `addStaticFood indexa un custom food y lo encuentra por alias`() {
        val index = FoodIndex()
        index.build(globalFoods = emptyList(), staticFoods = emptyList(), staticAliases = emptyMap())
        index.addStaticFood(customFood())

        assertEquals(1, index.size())
        val exact = index.exactMatches("almuerzo de la mama")
        assertEquals("el custom food se encuentra por alias normalizado", "custom1", exact.first().foodId)
        assertTrue("búsqueda por tokens también lo encuentra", index.search("almuerzo").contains("custom1"))
    }

    @Test
    fun `addStaticFood es idempotente y reemplaza por id`() {
        val index = FoodIndex()
        index.build(globalFoods = emptyList(), staticFoods = emptyList(), staticAliases = emptyMap())
        index.addStaticFood(customFood())
        index.addStaticFood(customFood())
        assertEquals("mismo id no duplica", 1, index.size())
    }

    @Test
    fun `addStaticFood no rompe alimentos ya indexados`() {
        val base = FoodItem(
            id = "gen001", name = "Manzana", brand = "Genérico", servingSize = 100.0, unit = "g",
            calories = 52.0, protein = 0.3, carbs = 14.0, fats = 0.2,
        )
        val index = FoodIndex()
        index.build(globalFoods = emptyList(), staticFoods = listOf(base), staticAliases = emptyMap())
        index.addStaticFood(customFood())
        assertTrue(index.search("manzana").contains("gen001"))
        assertTrue(index.search("almuerzo").contains("custom1"))
    }

    @Test
    fun `search retains an OFF pack for ranking alongside the household candidate`() {
        val rice = FoodItem(
            id = "gen005", name = "Arroz Blanco (cocido)", brand = "Genérico",
            servingSize = 100.0, unit = "g", calories = 130.0, protein = 2.7, carbs = 28.0, fats = 0.3,
        )
        val index = FoodIndex()
        index.build(
            globalFoods = listOf(
                com.example.kpkn.data.db.GlobalFoodEntity(
                    foodId = "off_arroz_kg",
                    name = "Arroz Grado 1kg",
                    normalizedName = "arroz grado 1kg",
                    calories = 360.0,
                    protein = 7.0,
                    carbs = 78.0,
                    fats = 1.0,
                    source = "OFF Chile",
                    portionGrams = 1000.0,
                ),
            ),
            staticFoods = listOf(rice),
            staticAliases = emptyMap(),
        )
        val hits = index.search("arroz")
        assertTrue(hits.contains("gen005"))
        assertTrue("retrieval must not erase products before identity and brand ranking", hits.contains("off_arroz_kg"))
    }

    @Test
    fun `retrieval keeps local exact and other candidates for compatibility filtering`() {
        val tomato = FoodItem(
            id = "gen026", name = "Tomate", brand = "Genérico", servingSize = 100.0, unit = "g",
            calories = 18.0, protein = 0.9, carbs = 3.9, fats = 0.2,
        )
        val pizza = FoodItem(
            id = "off1", name = "Pizza de Tomate", brand = "OFF", servingSize = 100.0, unit = "g",
            calories = 266.0, protein = 11.0, carbs = 33.0, fats = 10.0,
        )
        val index = FoodIndex()
        index.build(globalFoods = emptyList(), staticFoods = listOf(tomato, pizza), staticAliases = emptyMap())
        val hits = index.search("tomate")
        assertTrue(hits.contains("gen026"))
        assertTrue("retrieval is not the selection decision", hits.contains("off1"))
        assertFalse("the resolver must reject the compound as the plain identity",
            FoodIdentity.matchesDeclaredIdentity("tomate", pizza))
    }

    // ─── WP-S4: generaciones y reconstrucción atómica ───────────────────────

    private fun apple() = FoodItem(
        id = "gen001", name = "Manzana", brand = "Genérico", servingSize = 100.0, unit = "g",
        calories = 52.0, protein = 0.3, carbs = 14.0, fats = 0.2,
    )

    private fun pear() = GlobalFoodEntity(
        foodId = "off_pera", name = "Pera Hornitos", normalizedName = "pera hornitos",
        calories = 57.0, protein = 0.4, carbs = 15.0, fats = 0.1, source = "OFF Chile",
    )

    @Test
    fun `build can run again and the same instance serves the rebuilt data`() {
        val index = FoodIndex()
        assertEquals("never built", -1, index.generation)
        assertFalse(index.isBuilt())

        index.build(globalFoods = emptyList(), staticFoods = emptyList(), staticAliases = emptyMap(), generation = 1)
        assertEquals(1, index.generation)
        assertTrue("an empty build still counts as built", index.isBuilt())
        assertEquals(0, index.size())
        assertTrue(index.search("manzana").isEmpty())

        // B5: an index built before the catalog arrived must not stay frozen. Same instance, new data.
        index.build(globalFoods = listOf(pear()), staticFoods = listOf(apple()), staticAliases = emptyMap(), generation = 2)
        assertEquals(2, index.generation)
        assertEquals(2, index.size())
        assertTrue(index.search("manzana").contains("gen001"))
        assertTrue(index.search("pera").contains("off_pera"))
        assertEquals("Manzana", index.getFood("gen001")?.name)
        assertEquals("Pera Hornitos", index.getFood("off_pera")?.name)
        assertEquals(listOf("gen001"), index.exactMatches("manzana").map { it.foodId })
    }

    @Test
    fun `a rebuild replaces the previous contents instead of keeping the first build`() {
        val index = FoodIndex()
        index.build(globalFoods = listOf(pear()), staticFoods = listOf(apple()), generation = 1)
        assertEquals(2, index.size())

        // The old guard returned early once the index was non-empty; now the new generation wins.
        index.build(globalFoods = emptyList(), staticFoods = listOf(apple()), generation = 2)
        assertEquals(1, index.size())
        assertNull(index.getFood("off_pera"))
        assertFalse(index.search("pera").contains("off_pera"))
        assertEquals(2, index.generation)
    }

    @Test
    fun `addStaticFood after a rebuild lands on the new index and earlier additions do not survive it`() {
        val index = FoodIndex()
        index.build(globalFoods = emptyList(), staticFoods = listOf(apple()), generation = 1)
        index.addStaticFood(customFood())
        assertEquals(2, index.size())

        index.build(globalFoods = listOf(pear()), staticFoods = listOf(apple()), generation = 2)
        assertNull("the new input is the whole catalog: a food only the old index had is gone", index.getFood("custom1"))

        index.addStaticFood(customFood())
        assertEquals("custom1", index.exactMatches("almuerzo de la mama").first().foodId)
        assertTrue(index.search("almuerzo").contains("custom1"))
        assertTrue("rebuilt rows are still there", index.search("pera").contains("off_pera"))
        assertEquals(3, index.size())
    }

    @Test
    fun `a build never exposes a partial index and keeps foods added while it runs`() {
        val live = FoodIndex()
        live.build(globalFoods = emptyList(), staticFoods = listOf(apple()), generation = 1)

        val midBuild = mutableListOf<String>()
        // The global rows are read AFTER the static ones went into the private shard, i.e. in the middle of the build.
        val globals = object : AbstractList<GlobalFoodEntity>() {
            override val size: Int = 1

            override fun get(index: Int): GlobalFoodEntity {
                if (midBuild.isEmpty()) {
                    midBuild += "generation=${live.generation}"
                    midBuild += "size=${live.size()}"
                    midBuild += "pear=${live.getFood("off_pera") != null}"
                    midBuild += "apple=${live.search("manzana").contains("gen001")}"
                    live.addStaticFood(customFood()) // a hot addition lands on the published (old) index
                }
                return pear()
            }
        }
        live.build(globalFoods = globals, staticFoods = listOf(apple()), generation = 2)

        assertEquals(listOf("generation=1", "size=1", "pear=false", "apple=true"), midBuild)
        assertEquals(2, live.generation)
        assertEquals("apple + pear + the food added while building", 3, live.size())
        assertEquals("custom1", live.exactMatches("almuerzo de la mama").first().foodId)
        assertTrue(live.search("pera").contains("off_pera"))
    }

    @Test
    fun `a build without an explicit generation takes the next one and a negative generation is rejected`() {
        val index = FoodIndex()
        index.build(globalFoods = emptyList(), staticFoods = listOf(apple()))
        assertEquals(0, index.generation)
        index.build(globalFoods = emptyList(), staticFoods = listOf(apple()))
        assertEquals(1, index.generation)

        assertThrows(IllegalArgumentException::class.java) {
            index.build(globalFoods = emptyList(), staticFoods = emptyList(), generation = -1)
        }
        assertEquals("a rejected build leaves the published index alone", 1, index.generation)
        assertEquals(1, index.size())
    }

    @Test
    fun `the brand hint follows the rebuilt index`() {
        fun branded(id: String, brand: String) = FoodItem(
            id = id, name = "Leche $brand", brand = brand, servingSize = 100.0, unit = "g",
            calories = 60.0, protein = 3.0, carbs = 5.0, fats = 3.0,
        )
        val index = FoodIndex()
        index.build(globalFoods = emptyList(), staticFoods = listOf(branded("b1", "Colun")), generation = 1)
        assertEquals("Colun", index.brandHintFor("leche colun"))
        assertNull(index.brandHintFor("leche soprole"))

        index.build(globalFoods = emptyList(), staticFoods = listOf(branded("b2", "Soprole")), generation = 2)
        assertNull("a brand of the previous generation is gone", index.brandHintFor("leche colun"))
        assertEquals("Soprole", index.brandHintFor("leche soprole"))
    }
}

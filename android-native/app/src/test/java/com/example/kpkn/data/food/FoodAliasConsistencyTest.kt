package com.example.kpkn.data.food

import com.example.kpkn.domain.nutrition.FoodIndex
import com.example.kpkn.domain.nutrition.FoodSearchRanker
import com.example.kpkn.domain.nutrition.TextKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WP-S6 (B7): the alias table is one thing, whichever door a query comes in by. Every declared alias names ONE food by id
 * ([FOOD_ALIAS_IDS]); the exact lookups, the household anchor of the search ranker and [FoodIndex] all answer from it, and
 * plurals fold ("huevos", "papas", "tomates"). Before, a target was resolved by the first catalog name that CONTAINED it, so
 * "pechuga", "poyo" and "nuggets" were raw chicken breast, a dozen aliases named nothing, and the index only knew the
 * aliases whose target was a food's full name.
 */
class FoodAliasConsistencyTest {

    /** Aliases whose target names no food. Empty on purpose: a dead alias is deleted or backed by a row, never allowed. */
    private val knownDeadAliases: Set<String> = emptySet()

    private fun indexOf(aliases: Map<String, String>) = FoodIndex().apply {
        build(globalFoods = emptyList(), staticFoods = buildFoodDatabase(), staticAliases = aliases)
    }

    // 1. Every alias names exactly one food

    @Test
    fun `every declared alias names a food and the allow-list of dead aliases is empty`() {
        val dead = FOOD_ALIASES.filter { (_, target) -> resolveAliasTarget(target) == null }.keys
        assertEquals("aliases whose target names nothing: $dead", knownDeadAliases, dead)
        assertTrue(knownDeadAliases.isEmpty())
        FOOD_ALIASES.keys.forEach { alias -> assertNotNull("$alias has no id", FOOD_ALIAS_IDS[foodAliasKey(alias)]) }
    }

    @Test
    fun `aliases that share a key name the same food`() {
        FOOD_ALIASES.entries.groupBy { foodAliasKey(it.key) }.forEach { (key, entries) ->
            val ids = entries.map { resolveAliasTarget(it.value) }.toSet()
            assertEquals("\"$key\" (${entries.map { it.key }}) names $ids", 1, ids.size)
            assertEquals(key, ids.single(), FOOD_ALIAS_IDS[key])
        }
    }

    @Test
    fun `every alias id is a row of the catalog`() {
        val ids = buildFoodDatabase().map { it.id }.toSet()
        FOOD_ALIAS_IDS.forEach { (alias, id) -> assertTrue("$alias -> $id", id in ids) }
    }

    @Test
    fun `an approximation is a declared alias`() {
        val declared = FOOD_ALIASES.keys.map(::foodAliasKey).toSet()
        FOOD_ALIASES_APPROXIMATION.forEach { assertTrue("approximation \"$it\" is no declared alias", foodAliasKey(it) in declared) }
        // The plural of an approximation is the same approximation; a plain alias is none.
        listOf("torta", "tortas", "Torta de jamón", "ensaladas", "cereales", "galletas").forEach { assertTrue(it, isApproximationAlias(it)) }
        listOf("papas", "huevos", "pechuga", "tomate").forEach { assertFalse(it, isApproximationAlias(it)) }
    }

    // 2. pechuga, poyo and pechuga de pollo are the cooked breast; "pechuga cruda" the raw one

    @Test
    fun `pechuga poyo and pechuga de pollo are the cooked breast`() {
        listOf("pechuga", "poyo", "pechuga de pollo").forEach { alias ->
            assertEquals(alias, "gen004", FOOD_ALIAS_IDS[foodAliasKey(alias)])
            assertEquals(alias, "gen004", findFoodExactByNormalized(alias)?.id)
            assertEquals(alias, "gen004", findFoodByNormalized(alias)?.id)
            assertEquals(alias, "gen004", staticFoodForAlias(alias)?.id)
        }
        assertEquals("gen003", staticFoodForAlias("pechuga cruda")?.id)
        assertEquals("gen003", staticFoodForAlias("pechuga de pollo cruda")?.id)
    }

    // 3. huevo, huevos, huevo cocido

    @Test
    fun `huevo huevos and huevo cocido are the cooked egg`() {
        listOf("huevo", "huevos", "huevo cocido").forEach { alias ->
            assertEquals(alias, "gen007", FOOD_ALIAS_IDS[foodAliasKey(alias)])
            assertEquals(alias, "gen007", findFoodExactByNormalized(alias)?.id)
            assertEquals(alias, "gen007", staticFoodForAlias(alias)?.id)
        }
        assertEquals("gen007f", findFoodExactByNormalized("huevos fritos")?.id)
    }

    // 4. banana is the plátano: in the table, in the search ranker's pool and in FoodIndex

    @Test
    fun `banana is the platano in the table the ranker and the index`() {
        assertEquals("gen002", FOOD_ALIAS_IDS[foodAliasKey("banana")])
        assertEquals("gen002", findFoodExactByNormalized("banana")?.id)
        // The Search tab only matches the words a row carries: the synonyms of the table travel with the row.
        listOf("banana", "cambur", "plátano", "platanos").forEach { query ->
            val found = FoodSearchRanker.rank(FoodSearchRanker.query(query, "gen002"), buildFoodDatabase(), null, 15, loggerFilter = true)
            assertEquals(query, "gen002", found.firstOrNull()?.foodId)
        }
        // FoodIndex: the alias reaches the food by id, even when the row itself does not carry the word.
        val bare = checkNotNull(findStaticFoodById("gen002")).copy(searchAliases = emptyList())
        val index = FoodIndex().apply { build(emptyList(), listOf(bare), FOOD_ALIAS_IDS) }
        assertTrue("gen002" in index.search("banana"))
        assertEquals(listOf("gen002"), index.exactMatches("banana").map { it.foodId })
        assertTrue("gen002" in indexOf(FOOD_ALIAS_IDS).search("banana"))
        assertTrue("gen002" in indexOf(FOOD_ALIASES).search("banana"))
    }

    @Test
    fun `the index takes declared aliases in the form of the table and in the form of ids alike`() {
        val byTable = indexOf(FOOD_ALIASES)
        val byIds = indexOf(FOOD_ALIAS_IDS)
        FOOD_ALIASES.forEach { (alias, target) ->
            val id = checkNotNull(resolveAliasTarget(target))
            assertTrue("$alias -> $id by table", id in byTable.exactMatches(alias).map { it.foodId })
            assertTrue("$alias -> $id by ids", id in byIds.search(alias))
        }
        // The alias names ONE row: "poyo" is the cooked breast, never also the raw one.
        assertEquals(listOf("gen004"), byTable.exactMatches("poyo").map { it.foodId })
        assertEquals(listOf("gen004"), byIds.exactMatches("poyo").map { it.foodId })
    }

    @Test
    fun `an index built from a few rows only gets the aliases of those rows`() {
        val index = FoodIndex().apply { build(emptyList(), listOf(checkNotNull(findStaticFoodById("gen004"))), FOOD_ALIASES) }
        assertEquals(listOf("gen004"), index.exactMatches("poyo").map { it.foodId })
        assertTrue(index.exactMatches("banana").isEmpty())
        assertTrue(index.exactMatches("huevos").isEmpty())
        // A target that is no declared alias still names the food it equals exactly (the form before FOOD_ALIAS_IDS).
        val byName = FoodIndex().apply { build(emptyList(), buildFoodDatabase(), mapOf("zzfruta" to "Plátano")) }
        assertEquals(listOf("gen002"), byName.exactMatches("zzfruta").map { it.foodId })
    }

    @Test
    fun `an index holds every word together with its singular`() {
        val index = FoodIndex().apply { build(emptyList(), buildFoodDatabase()) }
        // Rows say "huevo", "lenteja", "tomate", "ala"; the person types the plural. Only the token index is asked here: the fuzzy
        // trigram expansion of search() would find these rows anyway and hide a missing singular.
        listOf("huevos" to "gen007", "lentejas" to "gen012", "tomates" to "gen026", "alitas" to "gen003a", "panes" to "gen019").forEach { (word, id) ->
            assertTrue("$word -> $id", id in index.foodsWithWord(word))
            assertTrue("$word -> $id by search", id in index.search(word))
        }
        // ... and the other way round: rows that only say the plural ("Frutillas", "Arándanos") answer the singular.
        listOf("frutilla" to "gen032", "arandano" to "gen082", "almendra" to "gen025").forEach { (word, id) ->
            assertTrue("$word -> $id", id in index.foodsWithWord(word))
        }
        assertTrue(index.foodsWithWord("xyzq").isEmpty())
        assertTrue(index.foodsWithWord("huevo cocido").isEmpty())
    }

    // 5. Plurals

    @Test
    fun `plurals fold in the exact lookup`() {
        assertEquals("gen026", findFoodExactByNormalized("tomates")?.id)
        assertEquals("gen021", findFoodExactByNormalized("papas")?.id)
        assertEquals("gen012", findFoodExactByNormalized("lentejas")?.id)
        assertEquals("gen039", findFoodExactByNormalized("nueces")?.id)
        assertEquals("gen039", findFoodExactByNormalized("nuez")?.id)
        assertEquals("gen021f", findFoodExactByNormalized("papa fritas")?.id)
        assertEquals("gen021f", findFoodExactByNormalized("papas fritas")?.id)
        assertEquals(foodAliasKey("papa"), foodAliasKey("PAPAS"))
        assertEquals(foodAliasKey("platano"), foodAliasKey("Plátanos"))
        assertEquals(foodAliasKey("pan"), foodAliasKey("panes"))
    }

    // 6. nuggets and galletas never name the wrong food

    @Test
    fun `nuggets are not raw chicken breast and galletas are not bread`() {
        listOf("nuggets", "nugget").forEach { alias ->
            assertNull(alias, FOOD_ALIAS_IDS[foodAliasKey(alias)])
            assertNotEquals(alias, "gen003", findFoodExactByNormalized(alias)?.id)
            assertNotEquals(alias, "gen003", findFoodByNormalized(alias)?.id)
            assertNotEquals(alias, "gen003", staticFoodForAlias(alias)?.id)
        }
        listOf("galletas", "galleta", "galletas del casino").forEach { alias ->
            assertNotEquals(alias, "gen019", FOOD_ALIAS_IDS[foodAliasKey(alias)])
            assertNotEquals(alias, "gen019", findFoodExactByNormalized(alias)?.id)
            assertNotEquals(alias, "gen019", findFoodByNormalized(alias)?.id)
            assertNotEquals(alias, "gen019", staticFoodForAlias(alias)?.id)
        }
        assertTrue(isApproximationAlias("galletas"))
    }

    // How a target is resolved

    @Test
    fun `a target is never resolved by containment`() {
        // "avena" names no row, it is only a word some names contain: the old fallback gave it the first of them.
        assertNull(resolveAliasTarget("avena"))
        assertNull(resolveAliasTarget("hojuelas"))
        assertNull(resolveAliasTarget("pollo cruda"))
        assertEquals("gen011", resolveAliasTarget("avena en hojuelas"))
        assertEquals("gen011", FOOD_ALIAS_IDS[foodAliasKey("avena")])
    }

    @Test
    fun `a target that names no state is the state a person eats`() {
        assertEquals("gen007", resolveAliasTarget("huevo entero"))
        assertEquals("gen021", resolveAliasTarget("papa"))
        assertEquals("gen012", resolveAliasTarget("lentejas"))
        assertEquals("gen013", resolveAliasTarget("garbanzos"))
        assertEquals("gen004", resolveAliasTarget("pechuga de pollo"))
        assertEquals("gen009", resolveAliasTarget("salmón"))
    }

    @Test
    fun `a target that declares a state is never answered by the other state`() {
        assertEquals("gen005c", resolveAliasTarget("arroz blanco (crudo)"))
        assertEquals("gen005", resolveAliasTarget("arroz blanco"))
        assertEquals("gen040c", resolveAliasTarget("pasta (cruda)"))
        assertEquals("gen044", resolveAliasTarget("merluza (cocida)"))
        assertEquals("gen012h", resolveAliasTarget("lentejas (hidratadas)"))
        assertEquals("gen005c", findFoodExactByNormalized("arroz seco")?.id)
    }

    @Test
    fun `branded aliases resolve to their curated row but are no static food`() {
        assertEquals("enr_rb_original", FOOD_ALIAS_IDS[foodAliasKey("red bull")])
        assertEquals("enr_monster_original", FOOD_ALIAS_IDS[foodAliasKey("monster")])
        assertEquals("enr_score_clasica", FOOD_ALIAS_IDS[foodAliasKey("score")])
        assertNull(staticFoodForAlias("red bull"))
        assertNull(findFoodExactByNormalized("monster"))
    }

    @Test
    fun `a declared alias wins over a search alias of another row`() {
        // Row gen144 carries the word "té" too; the table has always answered "té" with the green tea (a pinned contract).
        assertEquals("gen060", findFoodExactByNormalized("té")?.id)
        assertEquals("gen060", FOOD_ALIAS_IDS[foodAliasKey("te")])
    }

    @Test
    fun `the static anchor ignores what the source text of a row says`() {
        // These rows cite "USDA SR Legacy": HouseholdPortions.isGlobalSku reads that as a supermarket SKU and drops them.
        listOf("leche" to "gen016", "leche descremada" to "gen046", "tomate" to "gen026", "aceite de oliva" to "gen015").forEach { (query, id) ->
            assertEquals(query, id, staticFoodForAlias(query)?.id)
        }
        assertEquals("gen004", staticFoodForAlias("pollo")?.id)
        assertEquals("gen040", staticFoodForAlias("fideos")?.id)
        assertNull(staticFoodForAlias("xyzq"))
        assertNull(staticFoodForAlias("  "))
    }

    @Test
    fun `an alias key is accent free lower case and singular`() {
        FOOD_ALIAS_IDS.keys.forEach { key ->
            assertEquals(key, TextKeys.normalize(key), key)
            assertEquals(key, foodAliasKey(key))
        }
        assertFalse(FOOD_ALIAS_IDS.isEmpty())
    }

    @Test
    fun `tamal and queque are aliases backed by a row of their own`() {
        listOf("tamal", "tamales").forEach { alias ->
            assertEquals(alias, "gen153", FOOD_ALIAS_IDS[foodAliasKey(alias)])
            assertEquals(alias, "gen153", findFoodExactByNormalized(alias)?.id)
        }
        assertEquals("gen152", FOOD_ALIAS_IDS[foodAliasKey("queque del casino")])
        assertEquals("gen152", findFoodExactByNormalized("queques")?.id)
        listOf("gen152", "gen153").forEach { id ->
            val row = checkNotNull(findStaticFoodById(id))
            assertTrue(id, row.sourceRecordId?.isNotBlank() == true)
            assertTrue(id, row.nutritionBasis == "PER_100G_AS_SOLD" && row.calories in 100.0..500.0)
            // Not a supermarket SKU for HouseholdPortions.isGlobalSku (it reads the text of the source).
            assertFalse(id, row.source.orEmpty().uppercase().let { "USDA" in it || "OFF" in it })
        }
    }
}

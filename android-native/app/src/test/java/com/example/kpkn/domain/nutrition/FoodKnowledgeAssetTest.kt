package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.food.FoodKnowledgeStore
import com.example.kpkn.data.food.findStaticFoodById
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * WP-N13: the contract of `assets/food_data/food_knowledge_v1.json`, which is maintained by hand. The structure is checked by
 * [parseFoodKnowledge] (the loader would otherwise keep the Kotlin default); this test adds what only a person can get wrong and the
 * app could not notice at run time: a repeated key, a blank or duplicated entry, a value out of range, an unknown food id. The working
 * directory of the unit tests is the `app` module, like `DatasetTestHarness`.
 */
class FoodKnowledgeAssetTest {

    private val file = File("src/main/assets/food_data/food_knowledge_v1.json")
    private val text = file.readText(Charsets.UTF_8)
    private val root = Json.parseToJsonElement(text).jsonObject
    private val snapshot = parseFoodKnowledge(text)

    private fun section(name: String): JsonObject = root.getValue("sections").jsonObject.getValue(name).jsonObject

    @Test
    fun `the asset is version 1 with exactly the sections this build reads`() {
        assertEquals(FOOD_KNOWLEDGE_VERSION, root.getValue("version").jsonPrimitive.int)
        assertEquals(
            setOf("protectedPhrases", "typos", "synonyms", "householdUnits", "containers", "utensils", "densities"),
            root.getValue("sections").jsonObject.keys,
        )
        assertEquals(setOf("version", "sections"), root.keys)
    }

    @Test
    fun `no object of the asset repeats a key`() {
        assertEquals("a repeated key keeps only its last value in the parser", emptyList<String>(), duplicateKeys(text))
        // The scanner itself: the same key twice in one object is found, the same key in two objects is not.
        assertEquals(listOf("c", "a"), duplicateKeys("""{"a": 1, "b": {"c": 1, "c": 2}, "a": 3, "d": {"a": 1}}"""))
        assertEquals(emptyList<String>(), duplicateKeys("""{"a": {"x": 1}, "b": {"x": 2}, "c": ["x", "x"]}"""))
    }

    @Test
    fun `no list of the asset repeats an entry or holds a blank one`() {
        fun walk(path: String, element: JsonElement) {
            when (element) {
                is JsonObject -> element.forEach { (key, value) -> walk("$path.$key", value) }
                is JsonArray -> {
                    val strings = element.filterIsInstance<JsonPrimitive>().filter { it.isString }.map { it.content }
                    assertEquals("$path holds a duplicate entry", strings.distinct(), strings)
                    assertTrue("$path holds a blank entry", strings.none { it.isBlank() })
                    element.forEachIndexed { index, item -> walk("$path[$index]", item) }
                }
                else -> Unit
            }
        }
        walk("$", root)
    }

    @Test
    fun `typos and synonyms are lower-case words, one entry each, and no word is both`() {
        for ((table, entries) in listOf("typos" to snapshot.typos, "synonyms" to snapshot.synonyms)) {
            entries.forEach { (word, correction) ->
                assertEquals("$table key '$word'", word.trim().lowercase(), word)
                assertEquals("$table value of '$word'", correction.trim().lowercase(), correction)
            }
        }
        val both = snapshot.typos.keys intersect snapshot.synonyms.keys
        assertTrue("a word that is a typo and a synonym is rewritten by whichever pass comes first: $both", both.isEmpty())
    }

    @Test
    fun `the words the code compares with an accent-free key are accent-free lower case`() {
        fun assertKeys(label: String, words: Collection<String>) =
            words.forEach { assertEquals("$label '$it'", TextKeys.normalize(it), it) }
        assertKeys("unitGramsByToken", snapshot.householdUnits.unitGramsByToken.keys)
        assertKeys("notAWholePiece", snapshot.householdUnits.notAWholePiece)
        assertKeys("countableNameMarkers", snapshot.householdUnits.countableNameMarkers)
        snapshot.containers.foodClasses.forEach { assertKeys("foodClasses.${it.name}", it.words + it.name) }
        // The class words become a regex that is matched with a plain \b: a letter that is not ASCII would read differently on Android.
        snapshot.containers.foodClasses.flatMap { it.words }.forEach { word ->
            assertTrue("foodClasses word '$word' must be a-z", word.all { it in 'a'..'z' })
        }
    }

    @Test
    fun `protected phrases are lower case with single spaces`() {
        val lists = snapshot.protectedPhrases
        listOf(
            lists.entityLiterals, lists.fixedCompoundNames, lists.filledHeads, lists.maizeProducts, lists.flavors, lists.flavoredHeads,
            lists.catalogExtraPhrases,
        ).flatten().forEach { phrase ->
            assertEquals("'$phrase'", phrase.trim().lowercase().replace(Regex("""\s+"""), " "), phrase)
        }
    }

    @Test
    fun `densities stay between 0_2 and 1_2 grams per ml`() {
        snapshot.densities.gramsPerMl.forEach { (category, density) ->
            assertTrue("$category density $density is out of range", density in 0.2..1.2)
        }
        val categories = SubjectivePortionEngine.FoodDensityCategory.values().map { it.name }.toSet()
        assertEquals("one density per category of the enum", categories, snapshot.densities.gramsPerMl.keys)
    }

    @Test
    fun `every weight and volume is positive and plausible`() {
        fun assertWithin(label: String, values: Map<String, Double>, max: Double) =
            values.forEach { (key, value) -> assertTrue("$label '$key' = $value (0 < x <= $max)", value > 0.0 && value <= max) }
        assertWithin("unitGramsByToken", snapshot.householdUnits.unitGramsByToken, HouseholdPortions.MAX_ITEM_GRAMS_WITHOUT_KG)
        assertWithin("familyDefaultGrams", snapshot.householdUnits.familyDefaultGrams, HouseholdPortions.MAX_ITEM_GRAMS_WITHOUT_KG)
        snapshot.containers.contentByContainer.forEach { (container, row) ->
            assertWithin("contentByContainer.$container", row, HouseholdPortions.MAX_LIQUID_GRAMS_WITHOUT_KG)
        }
        assertWithin("utensils.defaultMl", snapshot.utensils.defaultMl, 1000.0)
    }

    @Test
    fun `container rows name the classes that exist, and a liquid container has a row`() {
        val classes = snapshot.containers.foodClasses.map { it.name }.toSet()
        snapshot.containers.contentByContainer.forEach { (container, row) ->
            assertTrue("$container has no default content", ContainersKnowledge.DEFAULT in row)
            val unknown = row.keys - classes - ContainersKnowledge.DEFAULT
            assertTrue("$container names a class that is not in foodClasses: $unknown", unknown.isEmpty())
        }
        assertTrue(snapshot.containers.liquidContainers.all { it in snapshot.containers.contentByContainer })
    }

    @Test
    fun `every container is named by a word, and a word names a share of one container`() {
        val named = snapshot.containers.words.map { it.id }.toSet()
        assertEquals("a row no word can reach is dead data", snapshot.containers.contentByContainer.keys, named)
        snapshot.containers.words.forEach { word ->
            assertTrue("${word.id} '${word.pattern}' fraction ${word.fraction}", word.fraction > 0.0 && word.fraction <= 1.0)
        }
        // The word is matched after the count has been turned into "una": the common ways of saying it are found, a different word is not.
        fun id(phrase: String) = snapshot.containers.words
            .firstOrNull { Regex("""\b(?:${it.pattern})\b""", RegexOption.IGNORE_CASE).containsMatchIn(phrase) }?.id
        assertEquals("lata", id("una latas de atun"))
        assertEquals("lata_chica", id("una lata chica de bebida"))
        assertEquals("botella", id("media botella de vino"))
        assertEquals("carton", id("un cartón de leche"))
        assertEquals(null, id("una cuchara de azucar"))
    }

    @Test
    fun `density rules name known categories and each one can match something`() {
        snapshot.densities.rules.forEachIndexed { index, rule ->
            assertTrue("rule $index: ${rule.category}", rule.category in snapshot.densities.gramsPerMl)
            assertTrue("rule $index matches nothing", rule.contains.isNotEmpty() || rule.words.isNotEmpty())
            rule.contains.forEach { assertEquals("rule $index contains '$it'", it.trim().lowercase(), it) }
        }
        assertTrue(snapshot.densities.fallbackCategory in snapshot.densities.gramsPerMl)
    }

    @Test
    fun `every food id the asset names is a row of the static catalog`() {
        // The sections of this version name no food: the rule is for the sections that will (dish compositions, aliases, profiles).
        val named = foodIdsIn(root)
        val unknown = named.filter { findStaticFoodById(it) == null }
        assertTrue("unknown food ids in the asset: $unknown", unknown.isEmpty())
        // The walker itself: a `foodId` and each entry of a `foodIds` are collected wherever they sit.
        val sample = Json.parseToJsonElement("""{"a": [{"foodId": "gen005"}, {"b": {"foodIds": ["gen001", "no_such_food"]}}]}""")
        assertEquals(listOf("gen005", "gen001", "no_such_food"), foodIdsIn(sample))
        assertNotNull(findStaticFoodById("gen005"))
        assertEquals(null, findStaticFoodById("no_such_food"))
    }

    @Test
    fun `the loader reads the file this test reads`() {
        assertEquals(file.canonicalFile, File("src/main/assets", FoodKnowledgeStore.ASSET_PATH).canonicalFile)
        assertTrue(file.isFile)
    }

    @Test
    fun `the asset is smaller than 300 KB`() {
        assertTrue("${file.length()} bytes", file.length() < 300 * 1024)
    }

    // ─── What the parser refuses ────────────────────────────────────────────────────────────────────────────────────

    private fun assertRejected(label: String, json: String, mentions: String = "") {
        try {
            parseFoodKnowledge(json)
            fail("$label was accepted")
        } catch (expected: IllegalArgumentException) {
            assertTrue("$label: ${expected.message}", expected.message.orEmpty().startsWith("food knowledge: "))
            assertTrue("$label should name '$mentions': ${expected.message}", mentions in expected.message.orEmpty())
        }
    }

    private fun JsonObject.with(key: String, value: JsonElement) = JsonObject(this + (key to value))

    private fun str(value: String) = JsonPrimitive(value)

    private fun num(value: Number) = JsonPrimitive(value)

    private fun obj(vararg fields: Pair<String, JsonElement>) = JsonObject(mapOf(*fields))

    private fun arr(vararg items: JsonElement) = JsonArray(items.toList())

    private fun withSection(name: String, value: JsonElement): String =
        root.with("sections", root.getValue("sections").jsonObject.with(name, value)).toString()

    private fun withoutSection(name: String): String =
        root.with("sections", JsonObject(root.getValue("sections").jsonObject - name)).toString()

    @Test
    fun `parse rejects another version`() {
        assertRejected("version 2", root.with("version", num(2)).toString(), "version 2")
        assertRejected("version 0", root.with("version", num(0)).toString(), "version 0")
        assertRejected("version as a string", root.with("version", str("1")).toString(), "version")
        assertRejected("no version", JsonObject(root - "version").toString(), "version")
        assertEquals(snapshot, parseFoodKnowledge(root.with("version", num(1)).toString()))
    }

    @Test
    fun `parse rejects malformed JSON`() {
        listOf("", "   ", "{", "[]", "null", "42", "not json", text.dropLast(40), text.replaceFirst("{", "[")).forEach {
            assertRejected("malformed '${it.take(20)}'", it)
        }
        // A byte order mark is a mistake of an editor, not of the data.
        assertEquals(snapshot, parseFoodKnowledge("\uFEFF" + text))
    }

    @Test
    fun `parse rejects a missing, an unknown or a misspelt key`() {
        assertRejected("missing section", withoutSection("densities"), "densities")
        assertRejected("unknown section", withSection("dishCompositions", obj()), "dishCompositions")
        assertRejected("misspelt key", withSection("utensils", section("utensils").with("defaultMls", obj())), "utensils")
        val units = section("householdUnits")
        assertRejected("missing field", withSection("householdUnits", JsonObject(units - "familyDefaultGrams")), "familyDefaultGrams")
        assertRejected("unknown field", withSection("householdUnits", units.with("extra", arr())), "extra")
        assertRejected("top level key", root.with("author", str("me")).toString(), "author")
    }

    @Test
    fun `parse rejects a value of the wrong type or out of its domain`() {
        val units = section("householdUnits")
        fun withUnits(field: String, value: JsonElement) = withSection("householdUnits", units.with(field, value))
        val weights = units.getValue("unitGramsByToken").jsonObject
        fun withWeight(value: JsonElement) = withUnits("unitGramsByToken", weights.with("tomate", value))
        assertRejected("negative weight", withWeight(num(-5)), "tomate")
        assertRejected("zero weight", withWeight(num(0)), "tomate")
        assertRejected("weight as a string", withWeight(str("120")), "tomate")
        assertRejected("weight as a list", withWeight(arr(num(1))), "tomate")
        assertRejected("blank word", withUnits("notAWholePiece", arr(str("  "))), "notAWholePiece[0]")
        assertRejected("duplicate entry", withUnits("countableFamilies", arr(str("pan"), str("pan"))), "countableFamilies[1]")
        assertRejected("a list where a map goes", withUnits("familyDefaultGrams", arr()), "familyDefaultGrams")
        assertRejected("blank typo", withSection("typos", section("typos").with("poyo", str(""))), "poyo")
        assertRejected("section of the wrong shape", withSection("synonyms", arr()), "synonyms")
    }

    @Test
    fun `parse rejects container rows, words and density rules that point nowhere`() {
        val containers = section("containers")
        val rows = containers.getValue("contentByContainer").jsonObject
        fun withContainers(field: String, value: JsonElement) = withSection("containers", containers.with(field, value))
        fun withRow(name: String, row: JsonObject) = withContainers("contentByContainer", rows.with(name, row))
        assertRejected("row without default", withRow("lata", obj("bebida" to num(350))), "default")
        assertRejected("row for a class that does not exist", withRow("lata", obj("default" to num(1), "sidra" to num(2))), "sidra")
        assertRejected("liquid container without a row", withContainers("liquidContainers", arr(str("barril"))), "barril")
        assertRejected("class without words", withContainers("foodClasses", arr(obj("class" to str("vino"), "words" to arr()))), "words")

        fun withWord(vararg fields: Pair<String, JsonElement>) = withContainers("words", arr(obj(*fields)))
        assertRejected("word of a container without a row", withWord("id" to str("barril"), "pattern" to str("barril")), "barril")
        assertRejected("pattern that is not a regex", withWord("id" to str("lata"), "pattern" to str("(lata")), "words[0].pattern")
        assertRejected("pattern missing", withWord("id" to str("lata")), "pattern")
        assertRejected("fraction of zero", withWord("id" to str("lata"), "pattern" to str("lata"), "fraction" to num(0)), "fraction")
        assertRejected("unknown field of a word", withWord("id" to str("lata"), "pattern" to str("lata"), "size" to num(1)), "size")

        val densities = section("densities")
        fun withDensities(field: String, value: JsonElement) = withSection("densities", densities.with(field, value))
        val gramsPerMl = densities.getValue("gramsPerMl").jsonObject
        val plasma = obj("category" to str("PLASMA"), "contains" to arr(str("x")))
        assertRejected("unknown category", withDensities("rules", arr(plasma)), "PLASMA")
        assertRejected("rule that matches nothing", withDensities("rules", arr(obj("category" to str("FAT")))), "rules[0]")
        assertRejected("unknown fallback", withDensities("fallbackCategory", str("PLASMA")), "fallbackCategory")
        assertRejected("a category is missing", withDensities("gramsPerMl", JsonObject(gramsPerMl - "FAT")), "gramsPerMl")
        assertRejected("a category too many", withDensities("gramsPerMl", gramsPerMl.with("PLASMA", num(1.0))), "gramsPerMl")
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────────────────────────────────────────

    /** The keys that appear twice in ONE object (the parser keeps the last value without a word); a minimal JSON walker. */
    private fun duplicateKeys(json: String): List<String> {
        val duplicates = ArrayList<String>()
        val scopes = ArrayList<MutableSet<String>?>() // an object: its keys so far; an array: null
        var i = 0
        while (i < json.length) {
            when (val c = json[i]) {
                '{' -> scopes.add(HashSet())
                '[' -> scopes.add(null)
                '}', ']' -> scopes.removeAt(scopes.lastIndex)
                '"' -> {
                    val value = StringBuilder()
                    i++
                    while (json[i] != '"') {
                        if (json[i] == '\\') { value.append(json[i]); i++ }
                        value.append(json[i])
                        i++
                    }
                    var next = i + 1
                    while (next < json.length && json[next].isWhitespace()) next++
                    val keys = scopes.lastOrNull()
                    val isKey = next < json.length && json[next] == ':' && keys != null
                    if (isKey && !keys.add(value.toString())) duplicates.add(value.toString())
                }
                else -> check(c.isWhitespace() || c in ",:+-.0123456789eEtrufalsn") { "unexpected '$c' at $i" }
            }
            i++
        }
        return duplicates
    }

    /** Every `foodId` and every entry of a `foodIds` list, wherever it sits in [element]. */
    private fun foodIdsIn(element: JsonElement): List<String> = when (element) {
        is JsonObject -> element.flatMap { (key, value) ->
            when {
                key == "foodId" && value is JsonPrimitive -> listOf(value.content)
                key == "foodIds" && value is JsonArray -> value.map { it.jsonPrimitive.content }
                else -> foodIdsIn(value)
            }
        }
        is JsonArray -> element.flatMap(::foodIdsIn)
        else -> emptyList()
    }
}
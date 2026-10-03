package com.example.kpkn.domain.nutrition

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import java.util.regex.PatternSyntaxException

/**
 * Version of the knowledge asset (`assets/food_data/food_knowledge_v1.json`) this build reads. A newer or older file is rejected whole
 * ([parseFoodKnowledge]); the pipeline then keeps the Kotlin default ([FoodKnowledge.defaultSnapshot]).
 */
const val FOOD_KNOWLEDGE_VERSION = 1

/**
 * Multi-word food phrases that no pass may rewrite or cut, and the words they are built from (section `protectedPhrases`).
 * [ProtectedPhrases] derives every spelling, set and regex from these lists.
 *
 * @property entityLiterals dishes and drinks named with a connector, written out ("café con leche", "pasteles de choclo").
 * @property fixedCompoundNames names that read as two foods and are one ("agua con gas", "ave palta").
 * @property filledHeads sandwiches and pastries named after a filling: each makes "<head> de jamón y queso" and its mirror.
 * @property maizeProducts foods made of maize: each makes "<product> de maíz".
 * @property flavors flavours that come in pairs: every ordered pair makes "vainilla y chocolate".
 * @property flavoredHeads foods that come in flavours: a pair is protected only after one of them and its "de".
 * @property catalogExtraPhrases catalog names that are not a row of the static catalog (a sauce), protected like one.
 */
data class ProtectedPhrasesKnowledge(
    val entityLiterals: List<String>,
    val fixedCompoundNames: List<String>,
    val filledHeads: List<String>,
    val maizeProducts: List<String>,
    val flavors: List<String>,
    val flavoredHeads: List<String>,
    val catalogExtraPhrases: List<String>,
)

/**
 * Weights of one piece and the lists that say what is counted by piece (section `householdUnits`); read by [HouseholdPortions].
 *
 * @property unitGramsByToken typical weight of ONE piece by the head noun of the food, accent-free, singular and plural.
 * @property notAWholePiece qualifiers that make a food something other than a whole piece ("cherry", "seco", "conserva").
 * @property countableFamilies food families counted by piece by default ("pan", "huevo").
 * @property countableNameMarkers accent-free name fragments of the foods counted by piece by default ("hallulla", "completo").
 * @property familyDefaultGrams default portion by food family when nothing more specific applies ("leche" 200 g).
 */
data class HouseholdUnitsKnowledge(
    val unitGramsByToken: Map<String, Double>,
    val notAWholePiece: Set<String>,
    val countableFamilies: Set<String>,
    val countableNameMarkers: Set<String>,
    val familyDefaultGrams: Map<String, Double>,
)

/**
 * One way to name a container in a phrase. [pattern] is the Java regex of the words, count included ("(?:un|una)\s+latas?"), matched
 * case-insensitively and as a whole word (the edges are added); the count of "2 latas" is read as "una latas" before it gets here.
 * [id] is the container it names (a key of [ContainersKnowledge.contentByContainer]) and [fraction] the share of one container the
 * phrase names: "media botella" is 0.5.
 */
data class ContainerWord(val id: String, val pattern: String, val fraction: Double)

/** The words of one food class of [ContainersKnowledge.foodClasses], accent-free and lower case ("vino": "vino", "tintos"). */
data class ContainerFoodClass(val name: String, val words: List<String>)

/**
 * What a container holds (section `containers`); read by [SubjectivePortionEngine].
 *
 * @property words the ways to name a container, in the order they are tried: the first that matches wins, so a "lata chica" goes
 *   before a "lata".
 * @property contentByContainer content of ONE container by container id, then by food class: ml for a drink, g for a solid. The
 *   row key `default` is the content of a container of any other food.
 * @property liquidContainers ids of the containers that only hold liquids: their content is a volume.
 * @property foodClasses the food classes, in the order they are tried: the first class that has one of its words in the food wins.
 */
data class ContainersKnowledge(
    val words: List<ContainerWord>,
    val contentByContainer: Map<String, Map<String, Double>>,
    val liquidContainers: Set<String>,
    val foodClasses: List<ContainerFoodClass>,
) {
    companion object {
        /** Key of the row entry that holds the content of a container of any food class. */
        const val DEFAULT = "default"
    }
}

/** Volume in ml of the utensils a person can edit in the logger (section `utensils`), by utensil id. */
data class UtensilsKnowledge(val defaultMl: Map<String, Double>)

/**
 * One step of the density detection: a food whose lower-case name holds one of [contains] (as a substring) or one of [words] (as a
 * whole word) is of [category]. The first rule that matches wins.
 */
data class DensityRule(val category: String, val contains: List<String>, val words: List<String>)

/**
 * Density by food category and the rules that tell the category of a food (section `densities`); read by [SubjectivePortionEngine].
 *
 * @property gramsPerMl grams per ml of each category, by the name of [SubjectivePortionEngine.FoodDensityCategory].
 * @property rules the detection rules, in order of precedence.
 * @property fallbackCategory the category of a food no rule matches.
 */
data class DensitiesKnowledge(
    val gramsPerMl: Map<String, Double>,
    val rules: List<DensityRule>,
    val fallbackCategory: String,
)

/**
 * The raw food knowledge of the description pipeline, immutable (WP-N13). The data lives in `assets/food_data/food_knowledge_v1.json`;
 * the Kotlin code keeps the logic and the structures derived from it (spellings, regexes, lookup sets). Every property after [version]
 * is a section of the asset, with the same name.
 */
data class FoodKnowledgeSnapshot(
    val version: Int,
    val protectedPhrases: ProtectedPhrasesKnowledge,
    val typos: Map<String, String>,
    val synonyms: Map<String, String>,
    val householdUnits: HouseholdUnitsKnowledge,
    val containers: ContainersKnowledge,
    val utensils: UtensilsKnowledge,
    val densities: DensitiesKnowledge,
)

/**
 * The knowledge snapshot the pipeline reads: the Kotlin default until the loader installs the asset (pattern of
 * [SemanticPortionRetriever.install]). Thread safe: [current] is a volatile read, [install] and [reset] are serialized.
 *
 * Consumers read [current] at the moment they use a table, and keep what they derive from it (compiled regexes, sets) in a
 * [KnowledgeCache]: an install that changes the content makes the next read rebuild it, so it takes effect at any time, also after the
 * pipeline has been used. Installing a snapshot equal to the current one changes nothing and rebuilds nothing, which is what the
 * loader does at every start while the asset and the Kotlin default are the same (FoodKnowledgeParityTest).
 *
 * Pure Kotlin / JVM: no Android dependency.
 */
object FoodKnowledge {
    private val default: FoodKnowledgeSnapshot = FoodKnowledgeDefaults.snapshot()

    @Volatile
    private var installed: FoodKnowledgeSnapshot? = null

    /** The snapshot in force: the installed one, else the Kotlin default. */
    fun current(): FoodKnowledgeSnapshot = installed ?: default

    /** The Kotlin default, whatever is installed (parity tests and the fallback of a failed load). */
    fun defaultSnapshot(): FoodKnowledgeSnapshot = default

    /**
     * Makes [snapshot] the one in force. A snapshot that equals the current one leaves it in place (and every cache derived from
     * it). Throws [IllegalArgumentException] for a version this build does not read.
     */
    @Synchronized
    fun install(snapshot: FoodKnowledgeSnapshot) {
        require(snapshot.version == FOOD_KNOWLEDGE_VERSION) {
            "Unsupported food knowledge version ${snapshot.version} (this build reads $FOOD_KNOWLEDGE_VERSION)"
        }
        if (snapshot == current()) return
        installed = snapshot
    }

    /** Back to the Kotlin default (tests). */
    @Synchronized
    fun reset() {
        installed = null
    }
}

/**
 * A value derived from [FoodKnowledge.current], built on first use and again after an install that changes the snapshot. It is
 * memoized by snapshot instance, like the vocabulary of [SemanticPortionRetriever]: a hit costs two volatile reads and a comparison,
 * so it is safe in the hot paths. Two threads may build the same value at once; the last one stays, and both are equal.
 */
internal class KnowledgeCache<T : Any>(private val build: (FoodKnowledgeSnapshot) -> T) {
    private class Entry<T : Any>(val snapshot: FoodKnowledgeSnapshot, val value: T)

    @Volatile
    private var entry: Entry<T>? = null

    fun get(): T {
        val snapshot = FoodKnowledge.current()
        entry?.let { if (it.snapshot === snapshot) return it.value }
        val value = build(snapshot)
        entry = Entry(snapshot, value)
        return value
    }
}

// ─── Parsing ─────────────────────────────────────────────────────────────────────────────────────────────────────

/**
 * Reads the knowledge asset. Strict: an unknown [FOOD_KNOWLEDGE_VERSION], malformed JSON, a missing or unknown key, a value of the
 * wrong type, a blank string, a duplicate list entry, or a number that is not finite and greater than 0, throws
 * [IllegalArgumentException] naming the JSON path. The loader catches it and keeps the Kotlin default, so a bad asset can never
 * install half a table. (Plausible ranges, such as a density between 0.2 and 1.2, are checked by FoodKnowledgeAssetTest.)
 */
fun parseFoodKnowledge(json: String): FoodKnowledgeSnapshot {
    val parsed = try {
        Json.parseToJsonElement(json.removePrefix("\uFEFF"))
    } catch (e: SerializationException) {
        throw IllegalArgumentException("food knowledge: malformed JSON (${e.message})", e)
    }
    val root = parsed.asObject("$")
    root.exactKeys("$", "version", "sections")
    val version = (root.getValue("version") as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
        ?: fail("$.version", "an integer")
    require(version == FOOD_KNOWLEDGE_VERSION) { "food knowledge: unsupported version $version (this build reads $FOOD_KNOWLEDGE_VERSION)" }
    val sections = root.getValue("sections").asObject("$.sections")
    sections.exactKeys("$.sections", "protectedPhrases", "typos", "synonyms", "householdUnits", "containers", "utensils", "densities")
    return FoodKnowledgeSnapshot(
        version = version,
        protectedPhrases = parseProtectedPhrases(sections.getValue("protectedPhrases"), "$.sections.protectedPhrases"),
        typos = sections.getValue("typos").stringMap("$.sections.typos"),
        synonyms = sections.getValue("synonyms").stringMap("$.sections.synonyms"),
        householdUnits = parseHouseholdUnits(sections.getValue("householdUnits"), "$.sections.householdUnits"),
        containers = parseContainers(sections.getValue("containers"), "$.sections.containers"),
        utensils = parseUtensils(sections.getValue("utensils"), "$.sections.utensils"),
        densities = parseDensities(sections.getValue("densities"), "$.sections.densities"),
    )
}

private fun parseProtectedPhrases(element: JsonElement, path: String): ProtectedPhrasesKnowledge {
    val obj = element.asObject(path)
    obj.exactKeys(
        path, "entityLiterals", "fixedCompoundNames", "filledHeads", "maizeProducts", "flavors", "flavoredHeads", "catalogExtraPhrases",
    )
    fun list(key: String) = obj.getValue(key).stringList("$path.$key")
    return ProtectedPhrasesKnowledge(
        entityLiterals = list("entityLiterals"),
        fixedCompoundNames = list("fixedCompoundNames"),
        filledHeads = list("filledHeads"),
        maizeProducts = list("maizeProducts"),
        flavors = list("flavors"),
        flavoredHeads = list("flavoredHeads"),
        catalogExtraPhrases = list("catalogExtraPhrases"),
    )
}

private fun parseHouseholdUnits(element: JsonElement, path: String): HouseholdUnitsKnowledge {
    val obj = element.asObject(path)
    obj.exactKeys(path, "unitGramsByToken", "notAWholePiece", "countableFamilies", "countableNameMarkers", "familyDefaultGrams")
    return HouseholdUnitsKnowledge(
        unitGramsByToken = obj.getValue("unitGramsByToken").numberMap("$path.unitGramsByToken"),
        notAWholePiece = obj.getValue("notAWholePiece").stringList("$path.notAWholePiece").toCollection(LinkedHashSet()),
        countableFamilies = obj.getValue("countableFamilies").stringList("$path.countableFamilies").toCollection(LinkedHashSet()),
        countableNameMarkers = obj.getValue("countableNameMarkers").stringList("$path.countableNameMarkers").toCollection(LinkedHashSet()),
        familyDefaultGrams = obj.getValue("familyDefaultGrams").numberMap("$path.familyDefaultGrams"),
    )
}

private fun parseContainers(element: JsonElement, path: String): ContainersKnowledge {
    val obj = element.asObject(path)
    obj.exactKeys(path, "words", "contentByContainer", "liquidContainers", "foodClasses")
    val classes = obj.getValue("foodClasses").asArray("$path.foodClasses").mapIndexed { index, item ->
        val itemPath = "$path.foodClasses[$index]"
        val entry = item.asObject(itemPath)
        entry.exactKeys(itemPath, "class", "words")
        val words = entry.getValue("words").stringList("$itemPath.words")
        if (words.isEmpty()) fail("$itemPath.words", "a non-empty list")
        ContainerFoodClass(entry.getValue("class").asString("$itemPath.class"), words)
    }
    val classNames = HashSet<String>()
    classes.forEachIndexed { index, foodClass ->
        if (!classNames.add(foodClass.name)) fail("$path.foodClasses[$index].class", "unique (duplicate \"${foodClass.name}\")")
    }
    val contentPath = "$path.contentByContainer"
    val content = LinkedHashMap<String, Map<String, Double>>()
    obj.getValue("contentByContainer").asObject(contentPath).forEach { (container, row) ->
        val rowPath = "$contentPath.$container"
        val values = row.numberMap(rowPath)
        if (ContainersKnowledge.DEFAULT !in values) fail(rowPath, "a row with a \"${ContainersKnowledge.DEFAULT}\" content")
        values.keys.forEach { key ->
            if (key != ContainersKnowledge.DEFAULT && key !in classNames) {
                fail("$rowPath.$key", "a food class of foodClasses or \"${ContainersKnowledge.DEFAULT}\"")
            }
        }
        content[container] = values
    }
    val liquid = obj.getValue("liquidContainers").stringList("$path.liquidContainers")
    liquid.forEachIndexed { index, container ->
        if (container !in content) fail("$path.liquidContainers[$index]", "a container of contentByContainer (got \"$container\")")
    }
    val words = obj.getValue("words").asArray("$path.words").mapIndexed { index, item ->
        val itemPath = "$path.words[$index]"
        val entry = item.asObject(itemPath)
        entry.onlyKeys(itemPath, setOf("id", "pattern", "fraction"), required = setOf("id", "pattern"))
        val id = entry.getValue("id").asString("$itemPath.id")
        if (id !in content) fail("$itemPath.id", "a container of contentByContainer (got \"$id\")")
        val pattern = entry.getValue("pattern").asString("$itemPath.pattern")
        try {
            Regex(pattern)
        } catch (e: PatternSyntaxException) {
            fail("$itemPath.pattern", "a valid regex (${e.description})")
        }
        ContainerWord(id, pattern, entry["fraction"]?.asPositive("$itemPath.fraction") ?: 1.0)
    }
    return ContainersKnowledge(words, content, liquid.toCollection(LinkedHashSet()), classes)
}

private fun parseUtensils(element: JsonElement, path: String): UtensilsKnowledge {
    val obj = element.asObject(path)
    obj.exactKeys(path, "defaultMl")
    return UtensilsKnowledge(obj.getValue("defaultMl").numberMap("$path.defaultMl"))
}

private fun parseDensities(element: JsonElement, path: String): DensitiesKnowledge {
    val obj = element.asObject(path)
    obj.exactKeys(path, "gramsPerMl", "rules", "fallbackCategory")
    val gramsPerMl = obj.getValue("gramsPerMl").numberMap("$path.gramsPerMl")
    val expected = FoodKnowledgeDefaults.DENSITY_CATEGORIES
    if (gramsPerMl.keys != expected.toSet()) fail("$path.gramsPerMl", "exactly the categories $expected")
    fun category(element: JsonElement, at: String): String =
        element.asString(at).also { if (it !in gramsPerMl) fail(at, "one of ${gramsPerMl.keys} (got \"$it\")") }
    val rules = obj.getValue("rules").asArray("$path.rules").mapIndexed { index, item ->
        val itemPath = "$path.rules[$index]"
        val entry = item.asObject(itemPath)
        entry.onlyKeys(itemPath, setOf("category", "contains", "words"), required = setOf("category"))
        val contains = entry["contains"]?.stringList("$itemPath.contains").orEmpty()
        val words = entry["words"]?.stringList("$itemPath.words").orEmpty()
        if (contains.isEmpty() && words.isEmpty()) fail(itemPath, "a rule with at least one of contains and words")
        DensityRule(category(entry.getValue("category"), "$itemPath.category"), contains, words)
    }
    return DensitiesKnowledge(gramsPerMl, rules, category(obj.getValue("fallbackCategory"), "$path.fallbackCategory"))
}

private fun fail(path: String, expected: String): Nothing = throw IllegalArgumentException("food knowledge: $path must be $expected")

private fun JsonElement.asObject(path: String): JsonObject = this as? JsonObject ?: fail(path, "an object")

private fun JsonElement.asArray(path: String): JsonArray = this as? JsonArray ?: fail(path, "an array")

private fun JsonElement.asString(path: String): String {
    val primitive = this as? JsonPrimitive
    if (primitive == null || !primitive.isString || primitive.content.isBlank()) fail(path, "a non-blank string")
    return primitive.content
}

private fun JsonElement.asPositive(path: String): Double {
    val primitive = this as? JsonPrimitive
    val value = primitive?.takeUnless { it.isString }?.doubleOrNull
    if (value == null || !value.isFinite() || value <= 0.0) fail(path, "a finite number greater than 0")
    return value
}

private fun JsonElement.stringList(path: String): List<String> {
    val seen = HashSet<String>()
    return asArray(path).mapIndexed { index, item ->
        item.asString("$path[$index]").also { if (!seen.add(it)) fail("$path[$index]", "unique (duplicate \"$it\")") }
    }
}

private fun JsonElement.stringMap(path: String): Map<String, String> {
    val result = LinkedHashMap<String, String>()
    asObject(path).forEach { (key, value) ->
        if (key.isBlank()) fail(path, "an object with non-blank keys")
        result[key] = value.asString("$path.$key")
    }
    return result
}

private fun JsonElement.numberMap(path: String): Map<String, Double> {
    val result = LinkedHashMap<String, Double>()
    asObject(path).forEach { (key, value) ->
        if (key.isBlank()) fail(path, "an object with non-blank keys")
        result[key] = value.asPositive("$path.$key")
    }
    return result
}

/** The object has exactly [keys]: a missing one and an unknown one are both errors (a misspelt key must not be ignored). */
private fun JsonObject.exactKeys(path: String, vararg keys: String) = onlyKeys(path, keys.toSet(), required = keys.toSet())

private fun JsonObject.onlyKeys(path: String, allowed: Set<String>, required: Set<String>) {
    val unknown = keys - allowed
    if (unknown.isNotEmpty()) fail(path, "an object with the keys $allowed only (unknown: $unknown)")
    val missing = required - keys
    if (missing.isNotEmpty()) fail(path, "an object with the keys $required (missing: $missing)")
}
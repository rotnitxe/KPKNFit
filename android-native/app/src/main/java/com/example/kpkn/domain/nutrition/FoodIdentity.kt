package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.FoodItem
import java.text.Normalizer

/**
 * Shared identity rules for the nutrition pipeline.
 *
 * Parsing, candidate ranking and macro calculation must agree on these rules.
 * This object deliberately contains no Android or database dependency so the
 * same contract can be exercised by unit tests and mirrored by the other
 * clients.
 */
enum class FoodState {
    UNKNOWN,
    RAW,
    COOKED,
    HYDRATED,
}

enum class FoodResolutionStatus {
    AUTO,
    /** Heuristic/fallback value is visible for editing but is not guardable yet. */
    NEEDS_REVIEW,
    NEEDS_STATE,
    NEEDS_CONFIRMATION,
    CONFIRMED_ESTIMATE,
    NO_RESOLVED,
}

enum class NutritionSourceKind {
    CURATED_LOCAL,
    VERIFIED_GLOBAL,
    EXTERNAL_ESTIMATE,
    DATASET_ESTIMATE,
    HEURISTIC_ESTIMATE,
}

data class FoodResolutionMetadata(
    val canonicalFamily: String? = null,
    val state: FoodState = FoodState.UNKNOWN,
    val status: FoodResolutionStatus = FoodResolutionStatus.NO_RESOLVED,
    val nutritionSource: NutritionSourceKind = NutritionSourceKind.HEURISTIC_ESTIMATE,
    val confidence: Double? = null,
)

/** Stable identity and state helpers shared by local and fuzzy resolution. */
object FoodIdentity {

    private val PASTA_WORDS = setOf(
        "pasta", "fideo", "fideos", "tallarin", "tallarines", "spaghetti",
        "espagueti", "espaguetis", "macarron", "macarrones", "penne", "lasana",
    )

    private val STATE_SENSITIVE_WORDS = setOf(
        "pollo", "pechuga", "pavo", "vacuno", "carne", "cerdo", "chancho",
        "pescado", "salmon", "salmon", "merluza", "atun", "huevo", "huevos",
        "arroz", "pasta", "fideo", "fideos", "tallarin", "tallarines",
        "lenteja", "lentejas", "garbanzo", "garbanzos", "poroto", "porotos",
        "frejol", "frijol", "papa", "papas", "patata", "quinoa", "avena",
    )

    private val STATE_SUFFIX = Regex(
        "\\s*\\((?:cruda?|cocida?|cocinada?|hidratada?|frita?|frito|plancha|horno|asada?|vapor|parrilla)\\)" +
            "|\\s+(?:cruda?|cocida?|cocinada?|hidratada?|frita?|frito|plancha|horno|asada?|vapor|parrilla)\\b",
        RegexOption.IGNORE_CASE,
    )

    // raw/cooked en inglés: las filas USDA/OFF traen el estado en la descripción
    // ("Chicken… cooked, braised") y antes quedaban UNKNOWN, ignorando la
    // penalización de estado en el ranking.
    private val RAW_PATTERN = Regex("\\b(?:crudo|cruda|crudos|crudas|seco|seca|secos|secas|deshidratado|deshidratada|deshidratados|deshidratadas|raw|dried)\\b")
    private val COOKED_PATTERN = Regex(
        "\\b(?:cocido|cocida|cocidos|cocidas|cocinado|cocinada|cocinados|cocinadas|hervido|hervida|hervidos|hervidas|frito|frita|fritos|fritas|plancha|horno|asado|asada|asados|asadas|vapor|parrilla|cooked|boiled|braised|baked|fried|grilled|roasted|steamed|smoked)\\b",
    )
    private val HYDRATED_PATTERN = Regex("\\b(?:hidratado|hidratada|remojado|remojada)\\b")

    fun normalize(value: String): String {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{Nd}]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    // FIX NUT-01: hallulla/marraqueta are regional names for same Chilean bread.
    // Grouping them collapses duplicate OFF rows before SAFE_GAP check and allows AUTO.
    private val BREAD_CHILENO_WORDS = setOf(
        "hallulla", "hallullas", "hallula", "hallulas", "halulla", "halullas",
        "allulla", "allullas",
        "marraqueta", "marraquetas", "pan batido", "pan frances",
    )

    private val SIMPLE_FAMILY_BY_TOKEN = mapOf(
        "tomate" to "tomate",
        "jitomate" to "tomate",
        "platano" to "platano",
        "banana" to "platano",
        "manzana" to "manzana",
        "zanahoria" to "zanahoria",
        "brocoli" to "brocoli",
        "lechuga" to "lechuga",
        "palta" to "palta",
        "aguacate" to "palta",
        "pollo" to "pollo",
        "pechuga" to "pollo",
        "arroz" to "arroz",
        "avena" to "avena",
        "lenteja" to "lenteja",
        "lentejas" to "lenteja",
        "garbanzo" to "garbanzo",
        "garbanzos" to "garbanzo",
        "huevo" to "huevo",
        "huevos" to "huevo",
        "leche" to "leche",
        "yogurt" to "yogurt",
        "yogur" to "yogurt",
        "pan" to "pan",
        "atun" to "atun",
        "tuna" to "atun",
        "salmon" to "salmon",
        "queso" to "queso",
        "cheddar" to "queso",
        "gouda" to "queso",
        "gauda" to "queso",
        "papa" to "papa",
        "papas" to "papa",
        "patata" to "papa",
    )

    private val COMPOUND_PRODUCT_MARKERS = listOf(
        " pizza", "pizza ",
        " ketchup", "catsup",
        " caldo ",
        " bread",
        "salsa de ",
        " pasta de ",
        " extracto",
        " mermelada",
        " helado de ",
        " sopa de ",
        " jugo de ",
        " pan de ",
        " con ",
    )

    private val PLAIN_FOOD_EXTRA_TOKENS = setOf(
        "pechuga", "blanco", "blanca", "entero", "entera", "hojuelas",
        "cocido", "cocida", "cocidos", "cocidas", "crudo", "cruda", "crudos", "crudas",
        "hidratado", "hidratada", "seco", "seca", "secos", "secas",
        "generico", "fresco", "fresca",
    )

    fun isCompoundProduct(value: String): Boolean {
        val padded = " ${normalize(value)} "
        return COMPOUND_PRODUCT_MARKERS.any { padded.contains(it) }
    }

    /**
     * True when [query] is a short head-noun and [foodName] is that same simple food
     * (possibly with state/cut qualifiers), not a mixed dish or branded compound.
     */
    fun isPlainSimpleFood(query: String, foodName: String): Boolean {
        val q = normalize(query)
        val n = normalize(foodName).replace(STATE_SUFFIX, "").trim()
        if (q.length < 2 || n.length < 2) return false
        if (isCompoundProduct(n) && !isCompoundProduct(q)) return false
        val queryFamily = familyFor(q)
        val foodFamily = familyFor(n)
        if (queryFamily != null && foodFamily != null && queryFamily != foodFamily) return false
        val stop = setOf("de", "la", "el", "los", "las", "un", "una")
        val queryTokens = q.split(" ").filter { it.length >= 2 && it !in stop }.toSet()
        if (queryTokens.isEmpty() || queryTokens.size > 3) return false
        val foodTokens = n.split(" ").filter { it.length >= 2 && it !in stop }.toSet()
        val extra = foodTokens - queryTokens - PLAIN_FOOD_EXTRA_TOKENS
        return extra.isEmpty() && foodTokens.any { it in queryTokens || queryFamily != null }
    }

    private val STOP_TOKENS = setOf(
        "de", "con", "y", "e", "la", "el", "los", "las", "un", "una", "a", "al", "del",
        "and", "with", "the",
    )

    fun contentTokens(value: String): List<String> =
        normalize(value).split(" ").filter { it.length > 1 && it !in STOP_TOKENS }

    fun headToken(value: String): String? = contentTokens(value).firstOrNull()

    fun familyFor(value: String): String? {
        val normalized = normalize(value)
        val tokens = normalized.split(" ").filter { it.isNotBlank() }
        return when {
            normalized.contains("salsa de tomate") || normalized == "salsa tomate" -> "salsa_de_tomate"
            normalized.contains("ketchup") || normalized.contains("catsup") -> "ketchup"
            BREAD_CHILENO_WORDS.any { normalized.contains(it) } -> "pan_chileno"
            tokens.any { it in PASTA_WORDS } -> "pasta"
            isCompoundProduct(normalized) -> when {
                normalized.contains("pizza") -> "pizza"
                normalized.contains("sopa") || normalized.contains("caldo") -> "sopa"
                normalized.contains("jugo") -> "jugo"
                normalized.contains("pan de") || normalized.contains("bread") -> "pan_compuesto"
                else -> null
            }
            else -> {
                val content = contentTokens(normalized)
                val headFamily = content.firstOrNull()?.let { SIMPLE_FAMILY_BY_TOKEN[it] }
                when {
                    headFamily != null -> headFamily
                    content.size >= 2 -> null
                    else -> content.firstNotNullOfOrNull { SIMPLE_FAMILY_BY_TOKEN[it] }
                }
            }
        }
    }

    fun stateFor(value: String): FoodState {
        val normalized = normalize(value)
        return when {
            // B12: RAW primero — "Asado de Tira (crudo)" contiene "asado" (que
            // también está en COOKED_PATTERN) y antes se marcaba COOKED, penalizando
            // con −0.35 la query correcta "asado de tira crudo".
            RAW_PATTERN.containsMatchIn(normalized) -> FoodState.RAW
            COOKED_PATTERN.containsMatchIn(normalized) -> FoodState.COOKED
            HYDRATED_PATTERN.containsMatchIn(normalized) -> FoodState.HYDRATED
            else -> FoodState.UNKNOWN
        }
    }

    fun stateFor(food: FoodItem): FoodState {
        val persisted = food.foodState.trim().uppercase()
            .let { value -> runCatching { FoodState.valueOf(value) }.getOrNull() }
        return persisted?.takeUnless { it == FoodState.UNKNOWN }
            ?: stateFor(food.name + " " + food.searchAliases.joinToString(" "))
    }

    fun familyFor(food: FoodItem): String? =
        familyFor(food.name + " " + food.searchAliases.joinToString(" "))

    fun isStateSensitive(value: String): Boolean {
        val normalized = normalize(value)
        val tokens = normalized.split(' ').toSet()
        return familyFor(value) == "pasta" ||
            tokens.any { it in STATE_SENSITIVE_WORDS }
    }

    fun isAmbiguousStateQuery(value: String): Boolean {
        return isStateSensitive(value) && stateFor(value) == FoodState.UNKNOWN
    }

    /** Search terms used when a family alias has no direct row of its own. */
    fun queryAliases(value: String): List<String> {
        val family = familyFor(value) ?: return emptyList()
        val state = stateFor(value)
        return when (family) {
            "pasta" -> when (state) {
                FoodState.RAW -> listOf("pasta cruda", "pasta (cruda)", "fideos crudos", "fideos secos")
                FoodState.COOKED, FoodState.HYDRATED -> listOf("pasta cocida", "pasta (hidratada/cocida)", "fideos cocidos")
                FoodState.UNKNOWN -> listOf("pasta", "tallarines")
            }
            "salsa_de_tomate" -> listOf("salsa de tomate")
            "pan_chileno" -> listOf("hallulla", "marraqueta", "pan batido")
            else -> emptyList()
        }
    }

    /** Extra phrases indexed for the curated rows, without assigning plain fideos a state. */
    fun aliasesForFood(food: FoodItem): List<String> {
        val family = familyFor(food)
        if (family == "pasta") return when (stateFor(food)) {
            FoodState.RAW -> listOf("fideos secos", "tallarines secos")
            FoodState.COOKED, FoodState.HYDRATED -> listOf("tallarines cocidos")
            FoodState.UNKNOWN -> emptyList()
        }
        if (family == "pan_chileno") return listOf("hallulla", "marraqueta", "pan batido", "pan frances")
        return emptyList()
    }

    /**
     * A plain pasta variant is a safe state-choice candidate for a bare
     * "fideos" query. Mixed dishes and flavored/instant products must not be
     * presented as the raw/cooked alternatives for that family alias.
     */
    fun isPlainPastaVariant(value: String): Boolean {
        if (familyFor(value) != "pasta" || stateFor(value) == FoodState.UNKNOWN) return false
        val normalized = normalize(value)
        val mixedOrProductMarkers = listOf(
            " con ", " sopa ", " sabor ", " instant", " mezcla ", " salchich",
            " vegetal", " pollo ", " carne ", " ternera ", " tomate ", " salsa ",
            " chipotle ", " rellena ", " preparado ",
        )
        return mixedOrProductMarkers.none { normalized.contains(it) }
    }
    fun isPlainPastaVariant(food: FoodItem): Boolean =
        isPlainPastaVariant(food.name + " " + food.searchAliases.joinToString(" "))

    /** Stable grouping key used to collapse duplicate catalog rows. */
    fun canonicalKey(food: FoodItem): String {
        val family = familyFor(food)
        val state = stateFor(food)
        if (family != null) {
            return "$family:${state.name.lowercase()}"
        }
        val name = normalize(food.name).replace(STATE_SUFFIX, "").trim()
        return name.ifBlank { food.id }
    }

    /** Reject obviously broken rows before they become a nutrition authority. */
    fun hasPlausibleMacros(food: FoodItem): Boolean {
        val values = listOf(food.calories, food.protein, food.carbs, food.fats)
        if (values.any { !it.isFinite() || it < 0.0 }) return false
        if (food.calories <= 0.0 && values.drop(1).all { it == 0.0 }) return false
        val macroEnergy = food.protein * 4.0 + food.carbs * 4.0 + food.fats * 9.0
        if (food.calories <= 0.0 || macroEnergy <= 0.0) return true
        return kotlin.math.abs(food.calories - macroEnergy) <= maxOf(80.0, food.calories * 0.70)
    }
}

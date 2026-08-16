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

    fun familyFor(value: String): String? {
        val normalized = normalize(value)
        return when {
            normalized.contains("salsa de tomate") || normalized == "salsa tomate" -> "salsa_de_tomate"
            normalized.split(" ").any { it in PASTA_WORDS } -> "pasta"
            else -> null
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
            else -> emptyList()
        }
    }

    /** Extra phrases indexed for the curated rows, without assigning plain fideos a state. */
    fun aliasesForFood(food: FoodItem): List<String> {
        if (familyFor(food) != "pasta") return emptyList()
        return when (stateFor(food)) {
            FoodState.RAW -> listOf("fideos secos", "tallarines secos")
            FoodState.COOKED, FoodState.HYDRATED -> listOf("tallarines cocidos")
            FoodState.UNKNOWN -> emptyList()
        }
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

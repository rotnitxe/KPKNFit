package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.CookingMethod

/**
 * CookingFactors — Multipliers for adjusting food macros by cooking method.
 *
 * Multipliers are per gram of the food's macros, but the pipeline does NOT apply the whole table (WP-N10): a row is
 * transformed by exactly one of a state conversion by yield, one of these factors, or nothing (see [CookingTransform]).
 * Only the [CONCENTRATING_METHODS] reach a row, and only when the catalog does not declare its state; boiling, steaming,
 * pot and stew are covered by the yield of a raw row, and frying and breading by oil in grams ([adjustLoggedFoodForOil]).
 * Those entries stay as the documented water and fat effect of each method; [cookingFactorFor] is the gate.
 * waterChange: positive = water gained (macros dilute), negative = water lost (macros concentrate).
 */
data class CookingFactor(
    val kcal: Double = 1.0,
    val fats: Double = 1.0,
    val carbs: Double = 1.0,
    val protein: Double = 1.0,
    val waterChange: Double = 0.0,
)

val COOKING_FACTORS: Map<CookingMethod, CookingFactor> = mapOf(
    CookingMethod.CRUDO to CookingFactor(kcal = 1.00, fats = 1.00, carbs = 1.00, protein = 1.00, waterChange = 0.00),
    CookingMethod.COCIDO to CookingFactor(kcal = 0.90, fats = 1.00, carbs = 1.00, protein = 0.95, waterChange = 0.15),
    CookingMethod.VAPOR to CookingFactor(kcal = 0.95, fats = 1.00, carbs = 1.00, protein = 1.00, waterChange = 0.08),
    CookingMethod.OLLA to CookingFactor(kcal = 0.90, fats = 1.00, carbs = 1.00, protein = 0.95, waterChange = 0.12),
    CookingMethod.PLANCHA to CookingFactor(kcal = 1.00, fats = 0.95, carbs = 1.00, protein = 1.05, waterChange = -0.15),
    CookingMethod.ASADO_PARRILLA to CookingFactor(kcal = 1.05, fats = 0.90, carbs = 1.00, protein = 1.10, waterChange = -0.20),
    CookingMethod.HORNO to CookingFactor(kcal = 1.15, fats = 0.95, carbs = 1.05, protein = 1.10, waterChange = -0.25),
    CookingMethod.GUISADO to CookingFactor(kcal = 1.30, fats = 1.20, carbs = 1.00, protein = 1.00, waterChange = 0.05),
    CookingMethod.AHUMADO to CookingFactor(kcal = 1.10, fats = 0.95, carbs = 1.00, protein = 1.05, waterChange = -0.15),
    CookingMethod.FRITO to CookingFactor(kcal = 1.10, fats = 1.00, carbs = 1.00, protein = 1.10, waterChange = -0.20),
    CookingMethod.EMPANIZADO_FRITO to CookingFactor(kcal = 1.20, fats = 1.00, carbs = 1.20, protein = 1.10, waterChange = -0.15),
)

// ─── Absorción de aceite por categoría culinaria (IT3) ───────────────────────

/**
 * Categoría culinaria para estimar cuánto aceite absorbe un alimento al freírse.
 * Antes se sumaban 8 g fijos a todo; ahora el medio depende del alimento:
 * las masas y tubérculos absorben mucho, las carnes rojas rinden su propia grasa.
 */
enum class OilAbsorptionCategory(val mediumGrams: Double) {
    /** Pollo, pavo, pescados magros, huevo, mariscos. */
    PROTEIN_LEAN(6.0),
    /** Carnes rojas y procesadas: rinden grasa propia al freírse. */
    PROTEIN_FATTY(4.0),
    /** Verduras y hortalizas. */
    VEGETABLE(8.0),
    /** Papas, masas, panes, empanadas, frituras rebozadas. */
    STARCH_BATTER(12.0),
    /** Legumbres. */
    LEGUME(7.0),
    /** Resto. */
    DEFAULT(8.0),
}

/**
 * Gramos de aceite añadido por nivel, según la categoría y la porción.
 * `mediumGrams` is the auditable midpoint per 100 g; scaling by the logged
 * amount avoids the old fixed-8-g error for 40 g and 400 g portions alike.
 */
fun oilGramsForLevelInCategory(
    oilLevel: String,
    category: OilAbsorptionCategory,
    portionGrams: Double = 100.0,
): Double {
    val base = category.mediumGrams
    val levelPer100g = when (oilLevel.lowercase()) {
        "sin aceite", "none", "zero", "cero" -> 0.0
        "poco" -> 3.0
        "abundante" -> base * 2.2
        else -> base
    }
    return (levelPer100g * (portionGrams / 100.0).coerceIn(0.1, 10.0))
}

private val STARCH_BATTER_KEYWORDS = listOf(
    "papa", "papas", "batata", "camote", "yuca", "churro", "donut", "croqueta",
    "empanada", "sopaipilla", "pan", "marraqueta", "hallulla", "completo",
    "milanesa", "rebozado", "tempura", "masa", "tortilla", "arepa", "choclo",
    "maiz", "maíz",
)

private val PROTEIN_LEAN_KEYWORDS = listOf(
    "pollo", "pechuga", "pavo", "pescado", "merluza", "congrio", "salmón", "salmon",
    "atún", "atun", "camarón", "camaron", "langostino", "pulpo", "calamar", "huevo",
    "clara", "tilapia", "trucha", "corvina", "reineta", "jurel",
)

private val PROTEIN_FATTY_KEYWORDS = listOf(
    "carne", "vacuno", "res", "cerdo", "puerco", "lomo", "bife", "asado", "costilla",
    "chuleta", "longaniza", "salchicha", "vienesa", "tocino", "panceta", "chorizo",
    "molida", "filete",
)

private val LEGUME_KEYWORDS = listOf(
    "lenteja", "garbanzo", "poroto", "frijol", "frejol", "haba", "soya", "soja", "pvt",
)

private val VEGETABLE_KEYWORDS = listOf(
    "verdura", "lechuga", "tomate", "cebolla", "zapallo", "brócoli", "brocoli",
    "champiñón", "champinon", "berenjena", "espinaca", "acelga", "zanahoria",
    "pepino", "pimentón", "pimenton", "coliflor", "repollo", "poroto verde",
)

/** Categoría de absorción de aceite según el nombre del alimento. */
fun oilAbsorptionCategory(foodName: String): OilAbsorptionCategory {
    val lower = foodName.lowercase()
    return when {
        STARCH_BATTER_KEYWORDS.any { lower.contains(it) } -> OilAbsorptionCategory.STARCH_BATTER
        PROTEIN_LEAN_KEYWORDS.any { lower.contains(it) } -> OilAbsorptionCategory.PROTEIN_LEAN
        PROTEIN_FATTY_KEYWORDS.any { lower.contains(it) } -> OilAbsorptionCategory.PROTEIN_FATTY
        LEGUME_KEYWORDS.any { lower.contains(it) } -> OilAbsorptionCategory.LEGUME
        VEGETABLE_KEYWORDS.any { lower.contains(it) } -> OilAbsorptionCategory.VEGETABLE
        else -> OilAbsorptionCategory.DEFAULT
    }
}

/**
 * Methods whose per-gram factor can apply (rule b of [cookingTransformFor]): they lose water or fat on a row whose state the
 * catalog does not declare.
 */
val CONCENTRATING_METHODS: Set<CookingMethod> = setOf(
    CookingMethod.HORNO,
    CookingMethod.PLANCHA,
    CookingMethod.ASADO_PARRILLA,
    CookingMethod.AHUMADO,
)

/** True when every macro multiplier is 1 (the water change is informative only). */
val CookingFactor.isIdentity: Boolean
    get() = kcal == 1.0 && fats == 1.0 && carbs == 1.0 && protein == 1.0

/**
 * The per-gram factor the pipeline applies for [method]: the table entry of a [CONCENTRATING_METHODS] method, identity for
 * every other one. Frying and breading never multiply kcal (their fat is added in grams by the oil path, 12 g per 100 g for
 * starches and batters), and a raw row never gets a factor on top of its yield conversion.
 */
fun cookingFactorFor(method: CookingMethod?): CookingFactor =
    if (method != null && method in CONCENTRATING_METHODS) COOKING_FACTORS[method] ?: CookingFactor() else CookingFactor()

/** Kept for its callers: the factor no longer depends on the food (WP-N10 removed the extra x1.20 of fried starches). */
@Suppress("UNUSED_PARAMETER")
fun cookingFactorFor(foodName: String, method: CookingMethod?): CookingFactor = cookingFactorFor(method)

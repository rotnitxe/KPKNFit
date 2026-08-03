package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.CookingMethod
import com.example.kpkn.data.models.FoodItem

/**
 * CookingFactors — Multipliers for adjusting food macros by cooking method.
 * Values sourced from CookingMethodParser.kt (440+ pattern system).
 *
 * Multipliers are applied per-gram to the DB food's macros.
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

/**
 * Apply cooking method factors to a FoodItem, returning adjusted per-100g macros.
 */
fun applyCooking(item: FoodItem, method: CookingMethod?): FoodItem {
    if (method == null) return item

    val factor = COOKING_FACTORS[method] ?: return item

    return item.copy(
        calories = round1(item.calories * factor.kcal),
        protein = round1(item.protein * factor.protein),
        carbs = round1(item.carbs * factor.carbs),
        fats = round1(item.fats * factor.fats),
    )
}

/**
 * Apply cooking factors to raw macro values, returning per-gram adjusted macros.
 */
fun applyCookingToMacros(
    calories: Double,
    protein: Double,
    carbs: Double,
    fats: Double,
    method: CookingMethod?,
): Quadruple {
    if (method == null) return Quadruple(calories, protein, carbs, fats)
    val factor = COOKING_FACTORS[method] ?: return Quadruple(calories, protein, carbs, fats)
    return Quadruple(
        round1(calories * factor.kcal),
        round1(protein * factor.protein),
        round1(carbs * factor.carbs),
        round1(fats * factor.fats),
    )
}

/**
 * Detect if a food name is a liquid (for unit display as ml instead of g).
 * B10: matching por palabra completa con límites de palabra; antes "te" hacía
 * match por substring y "tomate"/"lenteja"/"filete"/"mantequilla" mostraban ml.
 */
fun isLikelyLiquid(foodName: String, category: String? = null): Boolean {
    val lower = foodName.lowercase().trim()
    val liquidKeywords = listOf(
        "agua", "jugo", "zumo", "leche", "bebida", "refresco", "gaseosa",
        "café", "cafe", "té", "te", "cerveza", "vino", "licor", "ron", "whisky",
        "aceite", "vinagre", "salsa de soya", "salsa de soja", "caldo", "sopa",
        "batido", "smoothie", "malteada", "horchata", "ponche", "néctar", "nectar",
        "energética", "energetica", "isotónica", "isotonica", "cóctel", "coctel",
        "champán", "champagne", "sidra", "cava", "prosecco",
        "yogurt", "yogur", "kéfir", "kefir",
    )
    val liquidRegex = LIQUID_KEYWORD_REGEX
    if (liquidRegex.containsMatchIn(lower)) return true
    if (category != null) {
        val liquidCategories = listOf("beverage", "bebida", "drink", "dairy drink", "juice")
        if (liquidCategories.any { category.lowercase().contains(it) }) return true
    }
    return false
}

private val LIQUID_KEYWORD_REGEX: Regex by lazy {
    val keywords = listOf(
        "agua", "jugo", "zumo", "leche", "bebida", "refresco", "gaseosa",
        "café", "cafe", "té", "te", "cerveza", "vino", "licor", "ron", "whisky",
        "aceite", "vinagre", "salsa de soya", "salsa de soja", "caldo", "sopa",
        "batido", "smoothie", "malteada", "horchata", "ponche", "néctar", "nectar",
        "energética", "energetica", "isotónica", "isotonica", "cóctel", "coctel",
        "champán", "champagne", "sidra", "cava", "prosecco",
        "yogurt", "yogur", "kéfir", "kefir",
    )
    Regex(
        // (?U): sin UNICODE_CHARACTER_CLASS, \b trata "é"/"ñ" como no-palabra y
        // \bté\b jamás matchea (B10).
        "(?U)" + keywords.joinToString("|") { "\\b${Regex.escape(it)}\\b" },
        RegexOption.IGNORE_CASE,
    )
}

data class Quadruple(
    val calories: Double,
    val protein: Double,
    val carbs: Double,
    val fats: Double,
)

fun round1(v: Double): Double = kotlin.math.round(v * 10.0) / 10.0

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

/** Gramos de aceite añadido por nivel, según la categoría del alimento. */
fun oilGramsForLevelInCategory(oilLevel: String, category: OilAbsorptionCategory): Double {
    val base = category.mediumGrams
    return when (oilLevel.lowercase()) {
        "poco" -> 3.0
        "abundante" -> base * 2.2
        else -> base
    }
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
 * Factor de cocción por categoría (IT3): la fritura de masas/tubérculos concentra
 * más kcal por el aceite absorbido (×1.20 en vez de ×1.10). El resto usa la tabla base.
 */
fun cookingFactorFor(foodName: String, method: CookingMethod?): CookingFactor {
    if (method == null) return CookingFactor()
    val base = COOKING_FACTORS[method] ?: return CookingFactor()
    if (method == CookingMethod.FRITO &&
        oilAbsorptionCategory(foodName) == OilAbsorptionCategory.STARCH_BATTER
    ) {
        return base.copy(kcal = 1.20)
    }
    return base
}

package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.MealType

/**
 * Structural meal-shape inference: occasion and portion scale when the user
 * did not name a context or a quantity. Not a dish list and not “always lunch”.
 */
object InferredMealContext {

    enum class Shape {
        UNKNOWN,
        MAIN_PLATE,
        BREAKFAST_BOWL,
        SANDWICH,
        BEVERAGE,
        WRAP,
        SNACK_ITEM,
    }

    data class Decision(
        val shape: Shape,
        val context: ContextDetector.MealContext,
        val assumedLabel: String?,
    )

    private val STOP = setOf("de", "con", "y", "e", "and", "with", "la", "el", "los", "las", "un", "una", "a", "al", "del")

    private val STARCH = listOf(
        "arroz", "pasta", "fideo", "fideos", "papa", "papas", "patata", "pure", "puré",
        "quinoa", "couscous", "cuscus", "poroto", "porotos", "lenteja", "garbanzo",
        "frijol", "choclo", "ramen", "noodle", "udon", "pho", "pad thai", "padthai",
        "fideos salteados",
    )
    private val PROTEIN = listOf(
        "pollo", "huevo", "huevos", "carne", "vacuno", "cerdo", "pavo", "pescado",
        "salmon", "salmón", "atun", "atún", "merluza", "pechuga", "bistec", "lomo",
        "tofu", "jamon", "jamón",
    )
    private val BREAKFAST = listOf(
        "avena", "yogurt", "yogur", "granola", "cereal", "platano", "plátano",
        "banana", "fruta", "frutilla", "arandano",
    )
    private val BREAD = listOf(
        "pan", "hallulla", "hallula", "marraqueta", "tostada", "sandwich", "sándwich",
    )
    private val DRINK = listOf(
        "cafe", "café", "te", "té", "jugo", "batido", "smoothie", "agua", "leche",
        "capuchino", "cortado", "expresso", "espresso",
    )
    private val SNACK = listOf(
        "galleta", "galletas", "cookie", "cookies", "chips", "chip",
        "papas fritas", "patatas fritas", "chocolate", "dulce", "dulces",
        "caramelo", "caramelos", "gomita", "gomitas", "alfajor", "alfajores",
        "churro", "churros", "ramita", "ramitas", "snack", "snacks",
        "golosina", "golosinas", "chicle", "paleta", "helado",
    )
    private val CHOCOLATE_DRINK = listOf(
        "chocolate caliente", "cocoa", "cacao en polvo", "bebida de chocolate",
        "bebida chocolate", "chocolate a la taza",
    )
    private val WRAP = listOf(
        "taco", "tacos", "arepa", "burrito", "wrap", "sushi", "quesadilla",
    )
    private val NUT_SNACK = listOf("almendra", "nuez", "mani", "maní", "cacahuate", "pistacho")

    fun inferShape(description: String, foodTags: List<String> = emptyList()): Shape {
        val blob = FoodIdentity.normalize(
            (listOf(description) + foodTags).joinToString(" "),
        )
        val tokens = blob.split(" ").filter { it.isNotBlank() && it !in STOP }
        if (tokens.isEmpty()) return Shape.UNKNOWN

        val hasStarch = hasToken(blob, STARCH)
        val hasProtein = hasToken(blob, PROTEIN)
        val hasBreakfast = hasToken(blob, BREAKFAST)
        val hasBread = hasToken(blob, BREAD)
        val hasChocolateDrink = hasToken(blob, CHOCOLATE_DRINK)
        val hasSnack = hasToken(blob, SNACK) && !hasChocolateDrink
        val hasDrink = (hasToken(blob, DRINK) || hasChocolateDrink) && !hasSnack
        val hasWrap = hasToken(blob, WRAP)
        val hasNut = hasToken(blob, NUT_SNACK)
        val multi = foodTags.size >= 2 ||
            Regex("""\s+(?:con|y|e|and|with)\s+""").containsMatchIn(description.lowercase())

        if (hasWrap) return Shape.WRAP
        if (hasSnack) return Shape.SNACK_ITEM
        if (hasDrink && !hasStarch && !hasProtein) return Shape.BEVERAGE
        if (hasBread && multi) return Shape.SANDWICH
        if (hasBreakfast && !hasStarch && !hasProtein) return Shape.BREAKFAST_BOWL
        if (hasStarch && hasProtein) return Shape.MAIN_PLATE
        if (!multi) {
            if (hasNut || hasDrink || FoodIdentity.familyFor(blob) == "queso") return Shape.SNACK_ITEM
            if (tokens.size >= 2 && FoodIdentity.familyFor(blob) == null) return Shape.UNKNOWN
            return Shape.UNKNOWN
        }
        if (hasStarch || hasProtein) return Shape.MAIN_PLATE
        if (hasBreakfast) return Shape.BREAKFAST_BOWL
        return Shape.UNKNOWN
    }

    fun combine(
        lexical: List<ContextDetector.MealContext>,
        mealType: MealType?,
        shape: Shape,
    ): Decision {
        val lexicalPrimary = lexical.firstOrNull()
        if (lexicalPrimary != null) {
            return Decision(
                shape = shape,
                context = lexicalPrimary,
                assumedLabel = null,
            )
        }
        return when (shape) {
            Shape.BEVERAGE -> Decision(shape, ContextDetector.MealContext.GENERAL, "bebida")
            Shape.BREAKFAST_BOWL -> {
                val ctx = if (mealType == MealType.SNACK) {
                    ContextDetector.MealContext.SNACK
                } else {
                    ContextDetector.MealContext.DESAYUNO
                }
                Decision(shape, ctx, if (ctx == ContextDetector.MealContext.SNACK) "colación" else "desayuno")
            }
            Shape.SANDWICH -> {
                val ctx = when (mealType) {
                    MealType.SNACK -> ContextDetector.MealContext.SNACK
                    MealType.DINNER -> ContextDetector.MealContext.CENA
                    MealType.LUNCH -> ContextDetector.MealContext.ALMUERZO
                    else -> ContextDetector.MealContext.DESAYUNO
                }
                Decision(shape, ctx, labelFor(ctx, "once"))
            }
            Shape.MAIN_PLATE -> {
                val ctx = when (mealType) {
                    MealType.SNACK -> ContextDetector.MealContext.SNACK
                    MealType.DINNER -> ContextDetector.MealContext.CENA
                    MealType.BREAKFAST -> ContextDetector.MealContext.DESAYUNO
                    MealType.LUNCH -> ContextDetector.MealContext.ALMUERZO
                    null -> ContextDetector.MealContext.ALMUERZO
                }
                val label = when (ctx) {
                    ContextDetector.MealContext.SNACK -> "colación"
                    ContextDetector.MealContext.CENA -> "cena"
                    ContextDetector.MealContext.DESAYUNO -> "desayuno"
                    else -> "comida"
                }
                Decision(shape, ctx, label)
            }
            Shape.WRAP -> {
                val ctx = when (mealType) {
                    MealType.SNACK -> ContextDetector.MealContext.SNACK
                    MealType.DINNER -> ContextDetector.MealContext.CENA
                    MealType.BREAKFAST -> ContextDetector.MealContext.DESAYUNO
                    else -> ContextDetector.MealContext.GENERAL
                }
                Decision(shape, ctx, if (ctx == ContextDetector.MealContext.SNACK) "colación" else null)
            }
            Shape.SNACK_ITEM -> Decision(
                shape,
                if (mealType == MealType.SNACK) ContextDetector.MealContext.SNACK else ContextDetector.MealContext.GENERAL,
                if (mealType == MealType.SNACK) "colación" else null,
            )
            Shape.UNKNOWN -> Decision(shape, ContextDetector.MealContext.GENERAL, null)
        }
    }

    fun shouldInferPortions(shape: Shape, itemCount: Int, allUnspecified: Boolean, description: String = ""): Boolean {
        // A breakfast bowl with a measured base still needs topping grams
        // (fruta/granola/miel), not heuristicDishGrams of a full plate.
        // F2: [allUnspecified] is not a global kill-switch. A locked topping
        // (láminas, cucharada de aceite) must not starve unspecified plate
        // mates of MAIN_PLATE grams. TagResolver still skips EXPLICIT_MASS /
        // RESOLVED_SUBJECTIVE per item.
        if (shape == Shape.BREAKFAST_BOWL && itemCount >= 2) return true
        return when (shape) {
            Shape.MAIN_PLATE, Shape.BREAKFAST_BOWL, Shape.SANDWICH, Shape.BEVERAGE -> itemCount >= 1
            Shape.WRAP -> itemCount >= 1
            Shape.SNACK_ITEM -> itemCount >= 1
            Shape.UNKNOWN -> itemCount == 1 && FoodIdentity.contentTokens(description).size >= 2
        }
    }

    private fun labelFor(ctx: ContextDetector.MealContext, fallback: String): String = when (ctx) {
        ContextDetector.MealContext.SNACK -> "colación"
        ContextDetector.MealContext.DESAYUNO -> "desayuno"
        ContextDetector.MealContext.ALMUERZO -> "comida"
        ContextDetector.MealContext.CENA -> "cena"
        else -> fallback
    }

    private fun hasToken(blob: String, keywords: List<String>): Boolean {
        return keywords.any { keyword ->
            val n = FoodIdentity.normalize(keyword)
            if (n.isBlank()) return@any false
            Regex("""\b${Regex.escape(n)}\b""").containsMatchIn(blob)
        }
    }
}

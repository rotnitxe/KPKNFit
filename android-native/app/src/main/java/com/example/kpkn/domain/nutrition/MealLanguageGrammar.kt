package com.example.kpkn.domain.nutrition

/**
 * Mention roles and containers for the meal-language compiler: the vessel a clause names ("un plato de ...") and the dense
 * toppings. Does not replace [splitMealFragments].
 */
object MealLanguageGrammar {

    enum class MentionRole { FOOD, TOPPING, EXCLUDED }

    private val DENSE_TOPPING_MARKERS = listOf(
        "queso", "gouda", "gauda", "cheddar", "aceite", "mayo", "mayonesa",
        "mantequilla", "jamon", "cecina", "palta", "aguacate", "chocolate",
        "nuez", "almendra", "mani",
    )

    private val CONTAINER_HEADS = listOf(
        "plato", "platos", "bowl", "bol", "tazon", "tazón", "fuente", "fuentes",
    )

    private val CONTAINER_REGEX = Regex(
        """\b(?:un|una|unos|unas|1)\s+(${CONTAINER_HEADS.joinToString("|")})\s+de\b""",
        RegexOption.IGNORE_CASE,
    )

    fun detectContainer(description: String): String? {
        val match = CONTAINER_REGEX.find(description) ?: return null
        return FoodIdentity.normalize(match.groupValues[1])
    }

    fun isDenseTopping(text: String): Boolean {
        val blob = FoodIdentity.normalize(text)
        return DENSE_TOPPING_MARKERS.any { blob.contains(it) }
    }
}

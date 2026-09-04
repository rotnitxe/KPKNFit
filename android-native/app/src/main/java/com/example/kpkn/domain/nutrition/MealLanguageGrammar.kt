package com.example.kpkn.domain.nutrition

/**
 * Typed connectors and constructions for the meal-language compiler.
 * Longest / most-specific match wins. Does not replace [splitMealFragments].
 */
object MealLanguageGrammar {

    enum class DeKind { UNIT_OF, ATTRIBUTE, ORIGIN }

    enum class ConKind { PROTECTED_DISH, PLATE_LIST, DENSE_TOPPING }

    enum class YKind { LIST, PROTECTED_DISH }

    enum class MentionRole { FOOD, TOPPING, EXCLUDED }

    private val ATTRIBUTE_RIGHT = setOf(
        "chocolate", "vainilla", "limon", "naranja", "avena", "integral",
        "miel", "canela", "coco", "fresa", "frutilla", "manzana", "queso",
    )

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

    fun classifyDe(left: String, right: String): DeKind {
        val leftNorm = FoodIdentity.normalize(left)
        val rightNorm = FoodIdentity.normalize(right)
        val leftHead = leftNorm.split(" ").lastOrNull().orEmpty()
        if (leftHead.isNotBlank() && SubjectivePortionLexicon.isPortionHeadToken(leftHead)) {
            val probe = "$leftNorm de $rightNorm"
            if (SubjectivePortionLexicon.resolve(probe) != null) return DeKind.UNIT_OF
        }
        if (rightNorm in ATTRIBUTE_RIGHT || ATTRIBUTE_RIGHT.any { rightNorm.endsWith(" $it") || rightNorm == it }) {
            return DeKind.ATTRIBUTE
        }
        return DeKind.ORIGIN
    }

    fun classifyCon(fullClause: String, right: String): ConKind {
        val blob = FoodIdentity.normalize(fullClause)
        if (isWholeProtectedMeal(blob) || isWholeProtectedMeal(fullClause.trim())) {
            return ConKind.PROTECTED_DISH
        }
        val rightNorm = FoodIdentity.normalize(right)
        if (DENSE_TOPPING_MARKERS.any { rightNorm.contains(it) }) return ConKind.DENSE_TOPPING
        return ConKind.PLATE_LIST
    }

    fun classifyY(fullClause: String): YKind {
        return if (isWholeProtectedMeal(fullClause.trim())) YKind.PROTECTED_DISH else YKind.LIST
    }

    fun detectContainer(description: String): String? {
        val match = CONTAINER_REGEX.find(description) ?: return null
        return FoodIdentity.normalize(match.groupValues[1])
    }

    fun isDenseTopping(text: String): Boolean {
        val blob = FoodIdentity.normalize(text)
        return DENSE_TOPPING_MARKERS.any { blob.contains(it) }
    }
}

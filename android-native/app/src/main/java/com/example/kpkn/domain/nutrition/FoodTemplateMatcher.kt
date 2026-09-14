package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.MealTemplate
import com.example.kpkn.data.models.AmountIntent
import java.text.Normalizer

/**
 * E17: matching puro del atajo "¿Lo mismo que la última vez?".
 *
 * Extraído de NutritionRepository para ser testeable sin Room ni Android.
 * Reglas:
 * - Umbral de coincidencia 0.68 (token overlap 0.65 + overlap de alimentos 0.35).
 * - Si la consulta trae cantidades explícitas que contradicen las del template
 *   (≥2× o ≤0.5×), NO es la misma comida ("1 empanada" no hereda "3 empanadas").
 */
object FoodTemplateMatcher {

    const val THRESHOLD = 0.68

    fun normalizeSearchText(text: String): String {
        val stripped = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return stripped
            .lowercase()
            .replace(Regex("(?<=\\d),(?=\\d)"), ".")
            .replace(Regex("(?<!\\d)\\.|\\.(?!\\d)"), " ")
            .replace(Regex("[^\\p{L}\\p{Nd}.]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /** Devuelve el score [0,1] de coincidencia; 0.0 si no aplica. */
    fun score(template: MealTemplate, normalizedQuery: String): Double {
        // A meal memory may only match the same complete set of mentions.
        // Token overlap alone accepts omissions, negations and changed quantities.
        if (!sameMentions(template, normalizedQuery)) return 0.0
        val templateText = normalizeSearchText(
            buildString {
                append(template.name)
                if (template.description.isNotBlank()) {
                    append(" ")
                    append(template.description)
                }
                if (template.foods.isNotEmpty()) {
                    append(" ")
                    append(template.foods.joinToString(" ") { it.foodName })
                }
            }
        )

        if (templateText.isBlank()) return 0.0
        if (normalizedQuery == templateText) return 1.0

        // A3: cantidades explícitas contradictorias → no es la misma comida.
        if (quantitiesMismatch(template, normalizedQuery)) return 0.0

        val queryTokens = normalizedQuery.split(" ").filter { it.length >= 3 }.distinct()
        if (queryTokens.isEmpty()) return 0.0

        val templateTokens = templateText.split(" ").toSet()
        val tokenOverlap = queryTokens.count { it in templateTokens }.toDouble() / queryTokens.size.toDouble()
        val foodOverlap = if (template.foods.isNotEmpty()) {
            template.foods.count { food ->
                val foodName = normalizeSearchText(food.foodName)
                queryTokens.any { token -> foodName.contains(token) }
            }.toDouble() / template.foods.size.toDouble()
        } else 0.0

        return (tokenOverlap * 0.65) + (foodOverlap * 0.35)
    }

    private fun sameMentions(template: MealTemplate, query: String): Boolean {
        if (query.length > MAX_QUERY_CHARS) return false
        val parsed = parseMealDescription(query)
        if (parsed.items.any { it.isExcluded || it.excludedIngredients.isNotEmpty() }) return false
        if (parsed.items.size != template.foods.size || parsed.items.isEmpty()) return false
        val remaining = template.foods.toMutableList()
        for (item in parsed.items) {
            val exact = com.example.kpkn.data.food.findFoodExactByNormalized(item.foodQuery.ifBlank { item.tag })
            val index = remaining.indexOfFirst { stored ->
                FoodIdentity.normalize(stored.foodName) == FoodIdentity.normalize(item.tag) ||
                    (exact != null && com.example.kpkn.data.food.findFoodExactByNormalized(stored.foodName)?.id == exact.id)
            }
            if (index < 0) return false
            val stored = remaining.removeAt(index)
            if (item.cookingMethod != null && item.cookingMethod != stored.cookingMethod) return false
            // Counts may reuse the user's size for that same number of pieces.
            // A utensil or explicit mass still binds its measured grams.
            val countUnit = item.unitId?.removeSuffix("_n")?.replace('_', ' ')
            val countOnly = item.amountIntent == AmountIntent.RESOLVED_SUBJECTIVE &&
                HouseholdPortions.isCountable(exact, item.tag) &&
                (countUnit == null || FoodIdentity.normalize(countUnit) == FoodIdentity.normalize(item.tag))
            val storedGrams = if (stored.unit == "ml") SubjectivePortionEngine.massFromVolumeMl(stored.amount, stored.foodName) else stored.amount
            if (!countOnly && item.amountGrams != null && kotlin.math.abs(item.amountGrams - storedGrams) > 0.1) return false
            if ((countOnly || item.quantity != 1.0) && kotlin.math.abs(item.quantity - stored.quantity) > 0.001) return false
        }
        return remaining.isEmpty()
    }

    /**
     * Detecta cantidades explícitas en la consulta ("1", "3", "dos", "media"…).
     *
     * CRASH-FIX: el grupo anclado `(?:[\s-]+[a-z]+)*` con backtracking libre podía
     * degenerar exponencialmente en consultas largas (StackOverflowError en el hilo
     * que lo ejecutara — antes el main thread → crash al pulsar "Analizar").
     * Se usan cuantificadores POSESIVOS/ATÓMICOS (sin backtracking dentro del grupo:
     * no hay nada que recomponer porque el patrón termina ahí) y además se acota la
     * longitud de entrada en [quantitiesMismatch].
     */
    private val QUANTITY_ANCHOR_PATTERN =
        Regex("""(?:^|\s)(\d+(?:[.,]\d+)?|un|una|uno|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|media|medio|mitad|cuarto|tercio|doble|triple)(?:\s*[x×]\s*\d+)?\s+([a-záéíóúñü]++(?:[\s-]++[a-záéíóúñü]++)*+)""")

    /** Entrada acotada: defensa adicional contra descripciones patológicamente largas. */
    private const val MAX_QUERY_CHARS = 600

    private val QUANTITY_LITERALS = mapOf(
        "un" to 1.0, "una" to 1.0, "uno" to 1.0, "dos" to 2.0, "tres" to 3.0,
        "cuatro" to 4.0, "cinco" to 5.0, "seis" to 6.0, "siete" to 7.0,
        "ocho" to 8.0, "nueve" to 9.0, "diez" to 10.0, "media" to 0.5,
        "medio" to 0.5, "mitad" to 0.5, "cuarto" to 0.25, "tercio" to 0.33,
        "doble" to 2.0, "triple" to 3.0,
    )

    fun quantitiesMismatch(template: MealTemplate, normalizedQuery: String): Boolean {
        val templateFoods = template.foods.filter { it.amount > 0 }
        if (templateFoods.isEmpty()) return false

        QUANTITY_ANCHOR_PATTERN.findAll(normalizedQuery.take(MAX_QUERY_CHARS)).forEach { match ->
            val qty = QUANTITY_LITERALS[match.groupValues[1].lowercase()]
                ?: match.groupValues[1].replace(",", ".").toDoubleOrNull()
                ?: return@forEach
            val anchor = match.groupValues[2].split(" ", "-").first().lowercase()
            if (anchor.length < 3) return@forEach
            val templateFood = templateFoods.firstOrNull { food ->
                normalizeSearchText(food.foodName).split(" ").any { it == anchor || it.startsWith(anchor) }
            } ?: return@forEach
            val templateQty = templateFood.quantity.coerceAtLeast(1.0)
            val scale = qty / templateQty
            // Mismatch real (≥2× o ≤0.5×) → no es la misma comida
            if (scale >= 2.0 || scale <= 0.5) {
                return true
            }
        }
        return false
    }
}

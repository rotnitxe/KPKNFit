package com.example.kpkn.domain.nutrition

/**
 * Labels tokens and builds mentions. Segmentation of the user string still
 * belongs to the legacy parser; this compiler only describes bindings.
 */
object MealLanguageCompiler {

    data class Mention(
        val rawFragment: String,
        val foodSpan: String,
        val unitId: String? = null,
        val quantity: Double? = null,
        val grams: Double? = null,
        val containerScope: String? = null,
        val role: MealLanguageGrammar.MentionRole = MealLanguageGrammar.MentionRole.FOOD,
        val excluded: Boolean = false,
    )

    data class CompileResult(
        val source: String,
        val mentions: List<Mention>,
        val containerScope: String?,
        val protectedWhole: Boolean,
    )

    fun compile(description: String): CompileResult {
        val normalized = TextNormalizer.normalize(description).trim()
        if (normalized.isEmpty()) {
            return CompileResult(normalized, emptyList(), null, false)
        }
        val protectedWhole = isWholeProtectedMeal(normalized)
        val container = MealLanguageGrammar.detectContainer(normalized)
        val fragments = if (protectedWhole) {
            listOf(normalized)
        } else {
            splitMealFragments(normalized)
        }
        val mentions = fragments.map { fragment -> mentionFromFragment(fragment, container) }
        return CompileResult(
            source = normalized,
            mentions = mentions,
            containerScope = container,
            protectedWhole = protectedWhole,
        )
    }

    private fun mentionFromFragment(fragment: String, container: String?): Mention {
        val trimmed = fragment.trim()
        val excluded = trimmed.startsWith("sin ", ignoreCase = true)
        val lex = SubjectivePortionLexicon.resolve(trimmed)
        val foodSpan = SubjectivePortionLexicon.foodSpanAfterUnit(trimmed).ifBlank { trimmed }
        val unitId = SubjectivePortionLexicon.boundUnitId(trimmed)
        val role = when {
            excluded -> MealLanguageGrammar.MentionRole.EXCLUDED
            lex != null && MealLanguageGrammar.isDenseTopping(foodSpan) ->
                MealLanguageGrammar.MentionRole.TOPPING
            else -> MealLanguageGrammar.MentionRole.FOOD
        }
        return Mention(
            rawFragment = trimmed,
            foodSpan = foodSpan,
            unitId = unitId,
            quantity = lex?.relativeFactor,
            grams = lex?.grams,
            containerScope = container,
            role = role,
            excluded = excluded,
        )
    }
}

package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.AmountIntent
import com.example.kpkn.data.models.ParsedMealDescription
import com.example.kpkn.data.models.ParsedMealItem

/**
 * Additive merge: the compiler may only upgrade UNSPECIFIED → RESOLVED_SUBJECTIVE
 * when it bound a unit, fill [ParsedMealItem.foodQuery], and mark container scope.
 * Legacy segmentation always wins on item count.
 */
object MealLanguageMerge {

    fun apply(parsed: ParsedMealDescription): ParsedMealDescription {
        if (parsed.items.isEmpty()) return parsed
        val source = parsed.rawDescription.ifBlank { parsed.verbatimDescription }
        val compiled = MealLanguageCompiler.compile(source)
        val used = BooleanArray(compiled.mentions.size)
        val items = parsed.items.mapIndexed { index, item ->
            val mention = pickMention(item, index, parsed.items.size, compiled.mentions, used)
            enrich(item, mention, compiled)
        }
        return parsed.copy(items = items)
    }

    private fun pickMention(
        item: ParsedMealItem,
        index: Int,
        itemCount: Int,
        mentions: List<MealLanguageCompiler.Mention>,
        used: BooleanArray,
    ): MealLanguageCompiler.Mention? {
        if (mentions.isEmpty()) return null
        if (mentions.size == itemCount && index in mentions.indices && !used[index]) {
            used[index] = true
            return mentions[index]
        }
        val itemNorm = FoodIdentity.normalize(item.tag)
        var bestIdx = -1
        var bestScore = 0
        mentions.forEachIndexed { i, mention ->
            if (used[i]) return@forEachIndexed
            val score = overlapScore(itemNorm, mention)
            if (score > bestScore) {
                bestScore = score
                bestIdx = i
            }
        }
        if (bestIdx < 0 || bestScore <= 0) return null
        used[bestIdx] = true
        return mentions[bestIdx]
    }

    private fun overlapScore(itemNorm: String, mention: MealLanguageCompiler.Mention): Int {
        val food = FoodIdentity.normalize(mention.foodSpan)
        val raw = FoodIdentity.normalize(mention.rawFragment)
        if (itemNorm == food || itemNorm == raw) return 4
        if (itemNorm.contains(food) || food.contains(itemNorm)) return 3
        val itemTokens = itemNorm.split(" ").filter { it.length >= 3 }.toSet()
        val mentionTokens = (food.split(" ") + raw.split(" ")).filter { it.length >= 3 }.toSet()
        return itemTokens.intersect(mentionTokens).size
    }

    private fun enrich(
        item: ParsedMealItem,
        mention: MealLanguageCompiler.Mention?,
        compiled: MealLanguageCompiler.CompileResult,
    ): ParsedMealItem {
        val foodSpan = when {
            item.foodQuery.isNotBlank() -> item.foodQuery
            else -> SubjectivePortionLexicon.foodSpanAfterUnit(item.tag).ifBlank { item.tag }
        }
        val container = item.containerScope
            ?: mention?.containerScope
            ?: compiled.containerScope
        val unitId = item.unitId ?: mention?.unitId ?: SubjectivePortionLexicon.boundUnitId(item.tag)

        if (item.amountIntent == AmountIntent.EXPLICIT_MASS) {
            return item.copy(foodQuery = foodSpan, containerScope = container, unitId = unitId)
        }

        if (item.amountIntent == AmountIntent.RESOLVED_SUBJECTIVE) {
            return item.copy(foodQuery = foodSpan, containerScope = container, unitId = unitId)
        }

        val boundGrams = mention?.grams
            ?: SubjectivePortionLexicon.resolve(item.tag)?.grams
            ?: SubjectivePortionLexicon.resolve(mention?.rawFragment.orEmpty())?.grams
        if (item.amountIntent == AmountIntent.UNSPECIFIED && boundGrams != null && boundGrams > 0.0) {
            val qty = mention?.quantity ?: item.quantity
            return item.copy(
                foodQuery = foodSpan,
                containerScope = container,
                unitId = unitId,
                amountIntent = AmountIntent.RESOLVED_SUBJECTIVE,
                amountGrams = boundGrams,
                quantity = qty,
            )
        }

        return item.copy(foodQuery = foodSpan, containerScope = container, unitId = unitId)
    }
}

fun ParsedMealItem.effectiveFoodQuery(): String = foodQuery.ifBlank { tag }

package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.CookingMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WP-N4 · `\b` is ASCII-only on JDK 19+ and Unicode-aware under ICU (Android), so words that start
 * or end in an accented letter matched differently in these tests than on the phone. Every row
 * below must read the same on both engines; FoodParserAndroidTest runs the same table on a device.
 */
class RegexBoundaryPortabilityTest {

    private data class Row(
        val text: String,
        /** Items as `tag` or `tag|COOKING`. */
        val items: List<String>,
        val normalized: String? = null,
    )

    private val table = listOf(
        Row("huevo poché", listOf("huevo|COCIDO")),
        Row("ajá, un café", listOf("café"), normalized = "1 café"),
        Row("no sé, un té", listOf("té"), normalized = "1 té"),
        Row("brocolí al vapor", listOf("brocoli|VAPOR"), normalized = "brocoli al vapor"),
        Row("castañas de cajú con miel", listOf("castañas de cajú", "miel")),
        Row("pan con mantequilla de maní", listOf("pan", "mantequilla de maní")),
        Row("pollo al carbón", listOf("pollo|ASADO_PARRILLA")),
        Row("carne a la leña", listOf("carne|ASADO_PARRILLA")),
        Row("pollo al vacío", listOf("pollo|COCIDO")),
        Row("salmón ahumado", listOf("salmon|AHUMADO"), normalized = "salmon ahumado"),
    )

    private fun describe(text: String): List<String> = parseMealDescription(text).items.map { item ->
        item.cookingMethod?.let { "${item.tag}|${it.name}" } ?: item.tag
    }

    @Test fun everyRowReadsTheSameOnEitherEngine() {
        for (row in table) {
            assertEquals(row.text, row.items, describe(row.text))
            row.normalized?.let { assertEquals(row.text, it, TextNormalizer.normalize(row.text)) }
        }
        // The cooking method of the poached egg is the whole point of the first row.
        assertEquals(CookingMethod.COCIDO, parseMealDescription("huevo poché").items.single().cookingMethod)
    }

    @Test fun anAccentedKeywordIsFoundAtTheEndOfTheText() {
        assertTrue(ContextDetector.detect("me comí un tentempié").detectedContexts.contains(ContextDetector.MealContext.SNACK))
        assertEquals("tacita_cafe", parseMealDescription("una tacita de café").items.single().unitId)
        // The measure words that keep "un"/"una" include one that ends in an accented letter.
        assertEquals("un bote de aceitunas", TextNormalizer.normalize("un bote de aceitunas"))
        assertEquals("un boté de aceitunas", TextNormalizer.normalize("un boté de aceitunas"))
    }

    @Test fun boundedWrapsTheAlternationBetweenLetterAndDigitLookarounds() {
        assertEquals("""(?<![\p{L}\p{N}_])(?:a|b)(?![\p{L}\p{N}_])""", RegexEs.bounded("a|b"))
        assertEquals("""(?<![\p{L}\p{N}_])(?:\Qa.b\E)(?![\p{L}\p{N}_])""", RegexEs.boundedLiteral("a.b"))
        val poche = Regex(RegexEs.bounded("""poch[eé]"""), RegexOption.IGNORE_CASE)
        assertTrue(poche.containsMatchIn("huevo poché"))
        assertTrue(poche.containsMatchIn("poché"))
        assertTrue(!poche.containsMatchIn("pochéx"))
        assertTrue(!poche.containsMatchIn("xpoché"))
        assertTrue(!poche.containsMatchIn("poché1"))
    }

    @Test fun boundedMatchesTheSameWithAndWithoutUnicodeCharacterClasses() {
        // (?U) is JVM-only and only emulates how ICU reads word characters; the production
        // patterns never use it. The lookarounds name their classes, so the flag changes nothing.
        val alternations = listOf("poch[eé]", "aj[aá]", "brocol[ií]", "cuchar[oó]n", "huevo", """a\s+la\s+le[nñ]a""")
        val texts = listOf(
            "huevo poché", "pochéx", "xpoché", "ajá, un café", "ajáa", "brocolí", "brocolíes", "ñbrocolí", "jamón",
            "una cucharón", "a la leña", "á la leña", "huevo, huevos", "1huevo", "huevo_",
        )
        for (alternation in alternations) {
            val plain = Regex(RegexEs.bounded(alternation), RegexOption.IGNORE_CASE)
            val unicode = Regex("(?U)" + RegexEs.bounded(alternation), RegexOption.IGNORE_CASE)
            for (text in texts) {
                assertEquals("$alternation on '$text'", unicode.findAll(text).map { it.value }.toList(), plain.findAll(text).map { it.value }.toList())
            }
        }
    }

    @Test fun everyPatternHolderCompilesAndWarmsUp() {
        val holders = NutritionRegexRegistry.holderNames()
        assertTrue(holders.containsAll(listOf("TextNormalizer", "FoodParser", "ContextDetector", "FoodCombinationParser")))
        assertEquals(holders.size, NutritionRegexRegistry.warmUp())
    }
}

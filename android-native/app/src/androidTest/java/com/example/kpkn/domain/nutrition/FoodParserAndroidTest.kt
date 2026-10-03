package com.example.kpkn.domain.nutrition

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kpkn.data.models.CookingMethod
import java.util.regex.PatternSyntaxException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * WP-N4 · The device twin of RegexBoundaryPortabilityTest: Android reads `\b` with ICU (an accented
 * letter is a word character), the JVM tests do not, and `(?U)` is not accepted by ICU. The same
 * table must give the same result here, and every pattern holder must compile on this engine.
 */
@RunWith(AndroidJUnit4::class)
class FoodParserAndroidTest {

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

    @Test
    fun everyPatternHolderCompilesOnTheAndroidRegexEngine() {
        val touched = try {
            NutritionRegexRegistry.warmUp()
        } catch (error: PatternSyntaxException) {
            throw AssertionError("Android rejected a nutrition regex: ${error.description} in ${error.pattern}", error)
        }
        assertEquals(NutritionRegexRegistry.holderNames().size, touched)
    }

    @Test
    fun theBoundaryTableReadsOnAndroidTheSameAsOnTheJvm() {
        for (row in table) {
            val items = try {
                describe(row.text)
            } catch (error: PatternSyntaxException) {
                throw AssertionError("Android must parse '${row.text}' without a regex syntax error", error)
            }
            assertEquals(row.text, row.items, items)
            row.normalized?.let { assertEquals(row.text, it, TextNormalizer.normalize(row.text)) }
        }
        assertEquals(CookingMethod.COCIDO, parseMealDescription("huevo poché").items.single().cookingMethod)
    }

    @Test
    fun anAccentedKeywordIsFoundAtTheEndOfTheText() {
        assertTrue(ContextDetector.detect("me comí un tentempié").detectedContexts.contains(ContextDetector.MealContext.SNACK))
        assertEquals("tacita_cafe", parseMealDescription("una tacita de café").items.single().unitId)
        assertEquals("un boté de aceitunas", TextNormalizer.normalize("un boté de aceitunas"))
    }
}

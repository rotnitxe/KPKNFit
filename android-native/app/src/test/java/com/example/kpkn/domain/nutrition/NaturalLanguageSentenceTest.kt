package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.AmountIntent
import com.example.kpkn.data.models.CookingMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WP-N2 · A hedge is not a negation, "a" and "no" do not make a sentence English, and a
 * sentence or a meal label ends a mention. Expectations come from how people write, not from
 * the parser's own tables.
 */
class NaturalLanguageSentenceTest {
    private fun items(text: String) = parseMealDescription(text).items

    private fun tags(text: String) = items(text).map { it.tag }

    // ─── Hedges: "al menos 2 huevos" counts two eggs, it does not exclude them ──────────

    @Test fun aLowerBoundKeepsTheCountAndExcludesNothing() {
        for (text in listOf(
            "al menos 2 huevos", "Al menos dos huevos", "por lo menos 2 huevos", "a lo menos 2 huevos",
            "como mínimo 2 huevos", "más de 2 huevos", "menos de 2 huevos",
        )) {
            val egg = items(text).single()
            assertEquals(text, "huevo", egg.tag)
            assertEquals(text, 2.0, egg.quantity, 0.001)
            assertFalse(text, egg.isExcluded)
        }
        assertEquals("2 huevos", TextNormalizer.normalize("al menos 2 huevos"))
    }

    @Test fun aHedgedGlassStillResolvesAsAGlass() {
        val water = items("por lo menos un vaso de agua").single()
        assertEquals("agua", water.tag)
        assertEquals(AmountIntent.RESOLVED_SUBJECTIVE, water.amountIntent)
        assertEquals("vaso", water.unitId)
        assertFalse(water.isExcluded)
    }

    @Test fun aHedgeIsAWholeWordNotAPrefixOfAnotherWord() {
        // "casi" used to be cut out of "casino" ("no" is a negation) and "aprox" out of "aproximada".
        assertEquals("casino", TextNormalizer.normalize("casino"))
        assertEquals("casilla de pan", TextNormalizer.normalize("casilla de pan"))
        assertEquals("tipos de pan", TextNormalizer.normalize("tipos de pan"))
        assertEquals("coincidencia aproximada", TextNormalizer.normalize("coincidencia aproximada"))
        assertEquals("100 g de arroz", TextNormalizer.normalize("aproximadamente 100 g de arroz"))
        assertEquals("100 g de arroz", TextNormalizer.normalize("cerca de 100 g de arroz"))
        assertEquals(listOf("colación de casino"), tags("colación de casino"))
    }

    @Test fun menosAloneIsNotANegation() {
        assertTrue(items("arroz con menos sal").none { it.isExcluded })
        assertTrue(items("arroz, menos pan").none { it.isExcluded })
    }

    // ─── English guard: "a" and "no" are Spanish too ──────────────────────────────────────

    @Test fun aAndNoAloneDoNotMakeASentenceEnglish() {
        assertEquals("pasta a la bolognesa", TextNormalizer.normalize("pasta a la bolognesa"))
        assertEquals("pasta a la bolognesa", items("pasta a la bolognesa").single().tag)
        assertEquals("pollo a la plancha, no frito", TextNormalizer.normalize("pollo a la plancha, no frito"))
        // "banana" is an English key of the food map but also a Spanish word.
        assertEquals("1 platano a la plancha", TextNormalizer.normalize("una banana a la plancha"))
    }

    @Test fun chickenOnTheGriddleAndPoorMansPotatoesAreTwoMentionsWithoutAnInsertedCount() {
        val parsed = items("pollo a la plancha y papas a lo pobre")
        assertEquals(2, parsed.size)
        assertEquals("pollo", parsed[0].tag)
        assertEquals(CookingMethod.PLANCHA, parsed[0].cookingMethod)
        val insertedCount = Regex("""(^|\s)(1|un)(\s|$)""")
        assertTrue(parsed.toString(), parsed.none { insertedCount.containsMatchIn(it.tag) })
        assertEquals("papa a lo pobre", parsed[1].tag)
    }

    @Test fun realEnglishStillTranslates() {
        assertEquals("2 huevos y 1 taza de avena", TextNormalizer.normalize("two eggs and a cup of oats"))
        val parsed = items("two eggs and a cup of oats")
        assertEquals(listOf("huevo", "avena"), parsed.map { it.tag })
        assertEquals(2.0, parsed[0].quantity, 0.001)
        assertEquals("1 platano y un vaso de leche", TextNormalizer.normalize("one banana and a glass of milk"))
    }

    // ─── Sentences, labels and narration ──────────────────────────────────────────────────

    @Test fun labelsAndPeriodsSeparateMentions() {
        val parsed = items("Desayuno: 2 huevos. Almuerzo: arroz con pollo.")
        assertEquals(listOf("huevo", "arroz", "pollo"), parsed.map { it.tag })
        assertEquals(2.0, parsed[0].quantity, 0.001)
        assertEquals(listOf("huevo", "arroz", "pollo"), tags("Desayuné 2 huevos. Almorcé arroz con pollo."))
        assertEquals(listOf("té", "pan"), tags("Once: té con pan"))
        assertEquals(listOf("pan"), tags("Mañana: pan"))
        assertEquals(listOf("té"), tags("Tarde: té"))
        // "once" is still the number eleven when it counts something.
        assertEquals(11.0, items("once huevos").single().quantity, 0.001)
    }

    @Test fun narrationOpensEachSentenceWithoutBecomingAFood() {
        assertEquals(listOf("arroz", "pollo"), tags("hoy almorcé arroz con pollo"))
        assertEquals(listOf("arroz", "café"), tags("hoy almorcé arroz y después un café"))
        assertEquals(listOf("cazuela de vacuno"), tags("anoche cené una cazuela de vacuno"))
        val denied = items("no comí pan. Comí arroz.")
        assertEquals(listOf("pan", "arroz"), denied.map { it.tag })
        assertTrue(denied[0].isExcluded)
        assertFalse(denied[1].isExcluded)
    }

    @Test fun periodsAndColonsInsideNumbersAndAmountsDoNotSplit() {
        assertEquals(listOf("arroz", "huevo"), tags("1.5 tazas de arroz y 2 huevos"))
        assertEquals(1.5, items("1.5 tazas de arroz").single().quantity, 0.001)
        val clock = items("a las 13:30 comí 150 g de salmón").single()
        assertEquals(150.0, clock.amountGrams!!, 0.01)
        assertEquals("salmon", clock.tag)
        val amounts = items("pollo: 150 g, arroz: 100 g")
        assertEquals(listOf("pollo", "arroz"), amounts.map { it.tag })
        assertEquals(listOf(150.0, 100.0), amounts.map { it.amountGrams })
    }

    @Test fun theDotOfAUnitAbbreviationIsNotASentenceBoundary() {
        for (text in listOf("100 gr. de arroz", "100 g. de arroz")) {
            val rice = items(text).single()
            assertEquals(text, "arroz", rice.tag)
            assertEquals(text, 100.0, rice.amountGrams!!, 0.01)
        }
        assertEquals(1000.0, items("1 kg. de papas").single().amountGrams!!, 0.01)
        assertEquals(listOf(100.0, 50.0), items("arroz 100 g. pollo 50 g.").map { it.amountGrams })
    }
}

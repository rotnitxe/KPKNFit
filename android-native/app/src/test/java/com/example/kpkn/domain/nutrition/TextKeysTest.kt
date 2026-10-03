package com.example.kpkn.domain.nutrition

import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.Normalizer

/**
 * WP-N1 / WP-S7: [TextKeys.normalize] must give, for every input, exactly what the normalizers it
 * replaced gave. [legacyNormalize] is a verbatim copy of that chain (regexes built per call) and is
 * the oracle; `FoodIdentity.normalize` and `FoodIndex.normalizeSearch` now delegate to [TextKeys].
 */
class TextKeysTest {

    private fun legacyNormalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{Nd}]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private val samples = listOf(
        "",
        "   ",
        "Pechuga de Pollo (cruda)",
        "pasta (hidratada/cocida)",
        "1/2 taza de leche",
        "3 huevos",
        "0,5 kg de arroz",
        "100g de pollo",
        "2% grasa",
        "Piña colada",
        "ÑAME cocido",
        "ñoquis de papa",
        "café con leche",
        "JAMÓN SERRANO",
        "crème brûlée",
        "naïve Zoë",
        "Ca\u0301fe\u0301 decomposed",
        "İstanbul",
        "tabs\tand\nnewlines  and   spaces",
        "  leading and trailing  ",
        "!!!???...",
        "a_b-c+d,e;f:g",
        "α-β Ω",
        "m² ½ taza ¼",
        "１２３ fullwidth",
        "non\u00A0breaking\u2003space",
        "🍎 manzana 🍞 pan",
        "arroz con leche (150 g) / postre",
        "Coca-Cola Zero 350 ml",
        "Tomates cherry 1.5 kg",
    )

    @Test
    fun `thirty samples normalize exactly like the legacy inline chain`() {
        assertEquals(30, samples.size)
        for (sample in samples) {
            assertEquals("TextKeys vs legacy for [$sample]", legacyNormalize(sample), TextKeys.normalize(sample))
        }
    }

    @Test
    fun `FoodIdentity and FoodIndex delegate to TextKeys and agree on every sample`() {
        for (sample in samples) {
            val expected = TextKeys.normalize(sample)
            assertEquals("FoodIdentity.normalize for [$sample]", expected, FoodIdentity.normalize(sample))
            assertEquals("FoodIndex.normalizeSearch for [$sample]", expected, FoodIndex.normalizeSearch(sample))
        }
    }

    @Test
    fun `n-tilde and accents fold, digits and separators are kept as words`() {
        assertEquals("", TextKeys.normalize(""))
        assertEquals("", TextKeys.normalize("   "))
        assertEquals("pina colada", TextKeys.normalize("Piña colada"))
        assertEquals("name cocido", TextKeys.normalize("ÑAME cocido"))
        assertEquals("pechuga de pollo cruda", TextKeys.normalize("Pechuga de Pollo (cruda)"))
        assertEquals("pasta hidratada cocida", TextKeys.normalize("pasta (hidratada/cocida)"))
        assertEquals("1 2 taza de leche", TextKeys.normalize("1/2 taza de leche"))
        assertEquals("0 5 kg de arroz", TextKeys.normalize("0,5 kg de arroz"))
    }

    @Test
    fun `the shared patterns keep their exact legacy source`() {
        assertEquals("\\p{Mn}+", TextKeys.MARKS.pattern)
        assertEquals("[^\\p{L}\\p{Nd}]+", TextKeys.NON_ALNUM.pattern)
        assertEquals("\\s+", TextKeys.SPACES.pattern)
    }
}

package com.example.kpkn.domain.nutrition

import org.junit.Assert.assertEquals
import org.junit.Test

class TextNormalizerTest {

    @Test
    fun normalize_handlesEmojis() {
        assertEquals("palta", TextNormalizer.normalize("🥑"))
        assertEquals("pollo", TextNormalizer.normalize("🍗"))
        assertEquals("huevo", TextNormalizer.normalize("🥚"))
    }

    @Test
    fun normalize_handlesRepeatedVowels() {
        assertEquals("pollo", TextNormalizer.normalize("pollooo"))
        assertEquals("arroz", TextNormalizer.normalize("arrozzz"))
        assertEquals("leche", TextNormalizer.normalize("lecheeee"))
    }

    @Test
    fun normalize_handlesTypos() {
        assertEquals("pollo", TextNormalizer.normalize("poyo"))
        assertEquals("pollo", TextNormalizer.normalize("polllo"))
        assertEquals("arroz", TextNormalizer.normalize("arros"))
    }

    @Test
    fun normalize_handlesEnglishToSpanish() {
        assertEquals("pollo", TextNormalizer.normalize("chicken"))
        assertEquals("arroz", TextNormalizer.normalize("rice"))
        assertEquals("huevo", TextNormalizer.normalize("egg"))
        assertEquals("pan", TextNormalizer.normalize("bread"))
    }

    @Test
    fun normalize_handlesShorthand() {
        assertEquals("porque", TextNormalizer.normalize("xq"))
        assertEquals("porque", TextNormalizer.normalize("pq"))
        assertEquals("g", TextNormalizer.normalize("gr"))
        assertEquals("g", TextNormalizer.normalize("gramos"))
    }

    @Test
    fun normalize_handlesFractions() {
        assertEquals("500 g", TextNormalizer.normalize("medio kilo"))
        assertEquals("250 g", TextNormalizer.normalize("cuarto kilo"))
        assertEquals("1500 g", TextNormalizer.normalize("un kilo y medio"))
    }

    @Test
    fun normalize_handlesFillers() {
        assertEquals("pollo", TextNormalizer.normalize("eeeeehhh pollo"))
        assertEquals("arroz", TextNormalizer.normalize("tipo arroz"))
    }

    @Test
    fun normalizeFoodName_handlesDiminutives() {
        assertEquals("pollo", TextNormalizer.normalizeFoodName("pollo"))
        assertEquals("pollo", TextNormalizer.normalizeFoodName("pollito"))
        assertEquals("pan", TextNormalizer.normalizeFoodName("panecito"))
    }

    @Test
    fun normalizeFoodName_handlesAumentativos() {
        assertEquals("carne", TextNormalizer.normalizeFoodName("carnaza"))
        assertEquals("pollo", TextNormalizer.normalizeFoodName("pollote"))
    }

    @Test
    fun normalizeFoodName_preservesUnknownAugmentatives() {
        assertEquals("pelota", TextNormalizer.normalizeFoodName("pelota"))
    }

    @Test
    fun normalize_numberWordsToDigits() {
        assertEquals("1", TextNormalizer.normalize("uno"))
        assertEquals("2", TextNormalizer.normalize("dos"))
        assertEquals("100", TextNormalizer.normalize("cien"))
    }
}
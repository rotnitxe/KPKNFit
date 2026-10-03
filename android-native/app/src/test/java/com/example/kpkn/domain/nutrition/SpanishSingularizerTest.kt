package com.example.kpkn.domain.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WP-N3 · "-es" is ambiguous ("tomates" -> tomate, "limones" -> limon): the singular is the
 * candidate the vocabulary knows. Expectations are Spanish morphology, not the parser's tables.
 */
class SpanishSingularizerTest {
    private fun singular(text: String) = SpanishSingularizer.singularize(text)

    @Test fun pluralsThatAddOnlyAnSKeepTheirFinalE() {
        for ((plural, expected) in listOf(
            "tomates" to "tomate", "filetes" to "filete", "chocolates" to "chocolate",
            "aguacates" to "aguacate", "cafés" to "café", "cafes" to "cafe", "mates" to "mate",
        )) assertEquals(plural, expected, singular(plural))
    }

    @Test fun pluralsOfConsonantFinalWordsLoseTheWholeEs() {
        for ((plural, expected) in listOf(
            "limones" to "limon", "panes" to "pan", "yogures" to "yogur", "jamones" to "jamon",
            "salmones" to "salmon", "champiñones" to "champiñon", "camarones" to "camaron",
            "calamares" to "calamar", "atunes" to "atun", "pasteles" to "pastel", "ajíes" to "ají",
            "maníes" to "maní", "melones" to "melon", "alfajores" to "alfajor", "tamales" to "tamal",
            "cereales" to "cereal", "sándwiches" to "sándwich",
        )) assertEquals(plural, expected, singular(plural))
    }

    @Test fun cesBecomesZUnlessOnlyTheCeFormIsKnown() {
        for ((plural, expected) in listOf(
            "nueces" to "nuez", "peces" to "pez", "arroces" to "arroz", "maíces" to "maíz", "dulces" to "dulce",
        )) assertEquals(plural, expected, singular(plural))
    }

    @Test fun plainPluralsLoseTheirS() {
        for ((plural, expected) in listOf(
            "huevos" to "huevo", "lentejas" to "lenteja", "almendras" to "almendra", "papas" to "papa",
            "porotos" to "poroto", "kiwis" to "kiwi", "pimientos" to "pimiento", "Tomates" to "Tomate",
        )) assertEquals(plural, expected, singular(plural))
    }

    @Test fun shortWordsInvariantsAndForeignPluralsAreLeftAlone() {
        for (word in listOf(
            "res", "gas", "tés", "tres", "dos", "arroz", "lunes", "crisis", "chips", "nuggets", "cuscús",
            "anís", "tenis", "ñoquis", "inglés", "francés", "bagels", "menos", "hoy", "a lo",
        )) assertEquals(word, word, singular(word))
    }

    @Test fun aCatalogPhraseKeepsItsPluralAndOtherPhrasesChangeOnlyToACatalogPhrase() {
        assertEquals("frutos secos", singular("frutos secos"))
        assertEquals("papas fritas", singular("papas fritas"))
        assertEquals("pan francés", singular("pan francés"))
        assertEquals("pan integral", singular("panes integrales"))
        assertEquals("leche descremada", singular("leches descremadas"))
        assertEquals("jugo de naranja", singular("jugos de naranja"))
        // Neither the plural nor the singular phrase is in the catalog: nothing is guessed.
        for (phrase in listOf("manzanas verdes", "yogures griegos", "huevos duros", "tostadas francesas")) {
            assertEquals(phrase, phrase, singular(phrase))
        }
    }

    @Test fun aCompoundWhoseLastWordTookThePluralLosesOnlyThatS() {
        assertEquals("coca cola", singular("coca colas"))
        assertEquals("poroto verde", singular("poroto verdes"))
        assertEquals("pisco sour", singular("pisco sours"))
        assertEquals("coca cola", parseMealDescription("3 coca colas").items.single().tag)
        // A dish named with a number word is a plural name, not a count of a singular.
        assertEquals("tres leches", singular("tres leches"))
        assertEquals("cuatro quesos", singular("cuatro quesos"))
    }

    @Test fun anInjectedLexiconDecidesTheEsAmbiguityWithoutTheCatalog() {
        val chile = SpanishSingularizer.Lexicon(words = listOf("chile"))
        assertEquals("chile", SpanishSingularizer.singularize("chiles", chile))
        // A consonant-final suffix is enough when the lexicon knows neither candidate, and so is a lemma...
        assertEquals("sabor", SpanishSingularizer.singularize("sabores", SpanishSingularizer.Lexicon()))
        assertEquals("alfajor", SpanishSingularizer.singularize("alfajores", SpanishSingularizer.Lexicon()))
        // ...and the default is dropping only the S.
        assertEquals("bebe", SpanishSingularizer.singularize("bebes", SpanishSingularizer.Lexicon()))
        val phrases = SpanishSingularizer.Lexicon(words = listOf("sopa", "verdura"), phrases = listOf("sopa de verdura"))
        assertEquals("sopa de verdura", SpanishSingularizer.singularize("sopas de verdura", phrases))
        assertEquals("sopas de pollo", SpanishSingularizer.singularize("sopas de pollo", phrases))
    }

    @Test fun theLexiconIsBuiltFromTheStaticCatalog() {
        val lexicon = SpanishSingularizer.defaultLexicon
        assertTrue(lexicon.hasWord("tomate"))
        assertTrue(lexicon.hasWord("Plátano"))
        assertTrue(lexicon.hasPhrase("Pechuga de pollo"))
        assertTrue(lexicon.hasWord("nuez"))
    }

    // ─── Through the parser ───────────────────────────────────────────────────────────────────

    @Test fun aCountedPluralIsParsedAsItsSingular() {
        for ((text, tag, quantity) in listOf(
            Triple("tres tomates", "tomate", 3.0),
            Triple("cinco almendras", "almendra", 5.0),
            Triple("2 limones", "limon", 2.0),
            Triple("3 panes", "pan", 3.0),
            Triple("2 yogures", "yogur", 2.0),
            Triple("huevos x2", "huevo", 2.0),
            Triple("un par de huevos", "huevo", 2.0),
            Triple("4 nueces", "nuez", 4.0),
        )) {
            val item = parseMealDescription(text).items.single()
            assertEquals(text, tag, item.tag)
            assertEquals(text, quantity, item.quantity, 0.001)
        }
        // Without a count the name is kept as typed.
        assertEquals("almendras", parseMealDescription("almendras").items.single().tag)
        assertEquals("tres leches", parseMealDescription("tres leches").items.single().tag)
    }

    @Test fun theSingularAndThePluralOfOneFoodMergeWhenTheirMeasuresAreExplicit() {
        val merged = parseMealDescription("100 g de tomates y 50 g de tomate").items.single()
        assertEquals(150.0, merged.amountGrams!!, 0.01)
    }
}

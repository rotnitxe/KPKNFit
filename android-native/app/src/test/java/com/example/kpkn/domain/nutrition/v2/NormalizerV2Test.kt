package com.example.kpkn.domain.nutrition.v2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NormalizerV2Test {

    private fun norms(text: String): List<String> = NormalizerV2.tokenize(text).map { it.norm }

    /** Cada token cae sobre su tramo exacto del texto original. */
    private fun slices(text: String): List<String> = NormalizerV2.tokenize(text).map { text.substring(it.start, it.end) }

    @Test fun `tramos exactos sobre el original con tildes y mayusculas`() {
        val text = "Me comí 200g de Arróz"
        val tokens = NormalizerV2.tokenize(text)
        assertEquals(listOf("me", "comi", "200", "g", "de", "arroz"), tokens.map { it.norm })
        assertEquals(listOf("Me", "comí", "200", "g", "de", "Arróz"), slices(text))
        val arroz = tokens.last()
        assertEquals(16, arroz.start)
        assertEquals(21, arroz.end)
    }

    @Test fun `la enie pierde la tilde y mayusculas se bajan`() {
        assertEquals(listOf("pina", "ano", "cafe"), norms("PIÑA Año CAFÉ"))
    }

    @Test fun `tilde suelta de texto descompuesto se queda dentro del tramo`() {
        val text = "café con"
        val tokens = NormalizerV2.tokenize(text)
        assertEquals(listOf("cafe", "con"), tokens.map { it.norm })
        assertEquals(0, tokens[0].start)
        assertEquals(5, tokens[0].end)
    }

    @Test fun `coma o punto decimal dan una sola cifra`() {
        assertEquals(listOf("1.5", "tazas"), norms("1,5 tazas"))
        assertEquals(listOf("1.5", "tazas"), norms("1.5 tazas"))
        assertEquals("1,5", slices("1,5 tazas")[0])
        assertEquals(listOf("0.5", "taza"), norms(".5 taza"))
    }

    @Test fun `fracciones escritas y unicode`() {
        assertEquals(listOf("0.5", "taza"), norms("½ taza"))
        assertEquals(listOf("0.5"), norms("1/2"))
        assertEquals(listOf("0.75"), norms("3/4"))
        assertEquals(listOf("0.25"), norms("¼"))
        assertEquals(listOf("0.75"), norms("¾"))
        assertEquals(listOf("0.3333"), norms("⅓"))
        assertEquals(listOf("0.6667"), norms("⅔"))
        assertEquals(listOf("0.3333"), norms("1/3"))
    }

    @Test fun `uno y medio escrito como 1 1 barra 2 son dos tokens`() {
        val text = "1 1/2 tazas"
        val tokens = NormalizerV2.tokenize(text)
        assertEquals(listOf("1", "0.5", "tazas"), tokens.map { it.norm })
        assertEquals("1/2", text.substring(tokens[1].start, tokens[1].end))
        assertEquals(listOf("1", "0.5"), norms("1½"))
    }

    @Test fun `una fraccion con denominador cero no revienta`() {
        assertEquals(listOf("1", "0"), norms("1/0"))
    }

    @Test fun `numero pegado a unidad o a x da dos tokens con sus tramos`() {
        val text = "200g 1.5kg 250ml 2x"
        val tokens = NormalizerV2.tokenize(text)
        assertEquals(listOf("200", "g", "1.5", "kg", "250", "ml", "2", "x"), tokens.map { it.norm })
        assertEquals(listOf("200", "g", "1.5", "kg", "250", "ml", "2", "x"), slices(text))
    }

    @Test fun `numeros hablados en espanol sin ambiguedad`() {
        val text = "doscientos cincuenta gramos"
        val tokens = NormalizerV2.tokenize(text)
        assertEquals(listOf("250", "gramos"), tokens.map { it.norm })
        assertEquals("doscientos cincuenta", text.substring(tokens[0].start, tokens[0].end))
        assertEquals(listOf("35"), norms("treinta y cinco"))
        assertEquals(listOf("16"), norms("dieciséis"))
        assertEquals(listOf("22"), norms("veintidós"))
        assertEquals(listOf("2", "huevos"), norms("dos huevos"))
        assertEquals(listOf("1000"), norms("mil"))
        assertEquals(listOf("2500", "g"), norms("dos mil quinientos g"))
        assertEquals(listOf("101"), norms("ciento un"))
        assertEquals(listOf("31", "huevos"), norms("treinta y un huevos"))
        assertEquals(listOf("500", "g"), norms("quinientos g"))
    }

    @Test fun `el tramo de un numero hablado cubre toda la expresion`() {
        val text = "Treinta y cinco gramos"
        val tokens = NormalizerV2.tokenize(text)
        assertEquals("35", tokens[0].norm)
        assertEquals("Treinta y cinco", text.substring(tokens[0].start, tokens[0].end))
    }

    @Test fun `una lista de cantidades independientes no se suma`() {
        assertEquals(listOf("2", "y", "3", "huevos"), norms("dos y tres huevos"))
    }

    @Test fun `numeros hablados en ingles`() {
        val text = "two hundred fifty grams"
        val tokens = NormalizerV2.tokenize(text)
        assertEquals(listOf("250", "grams"), tokens.map { it.norm })
        assertEquals("two hundred fifty", text.substring(tokens[0].start, tokens[0].end))
        assertEquals(listOf("21"), norms("twenty-one"))
        assertEquals(listOf("100", "g"), norms("one hundred g"))
        assertEquals(listOf("150"), norms("one hundred and fifty"))
        assertEquals(listOf("2", "eggs"), norms("two eggs"))
        assertEquals(listOf("2500"), norms("two thousand five hundred"))
    }

    @Test fun `las palabras ambiguas quedan como palabra para el lexico`() {
        for (word in listOf("un", "una", "uno", "a", "an", "one", "medio", "media", "half", "cuarto", "quarter", "par",
            "couple", "docena", "dozen", "once")) {
            assertEquals(word, listOf(word), norms(word))
        }
        assertEquals(listOf("un", "huevo"), norms("un huevo"))
        assertEquals(listOf("a", "couple", "of", "eggs"), norms("a couple of eggs"))
    }

    @Test fun `el punto final de una abreviatura no es decimal`() {
        val text = "2 cda. de aceite"
        assertEquals(listOf("2", "cda", ".", "de", "aceite"), norms(text))
        assertEquals(listOf("cda", "."), norms("cda."))
        assertEquals(listOf("100", "gr", "."), norms("100 gr."))
        assertEquals(listOf("200", "."), norms("200."))
    }

    @Test fun `los signos se conservan y lo demas se descarta`() {
        assertEquals(
            listOf("pollo", ",", "arroz", ";", "(", "queso", ")", "+", "pan", ":", "3"),
            norms("pollo, arroz; (queso) + pan: 3"),
        )
        assertEquals(listOf("sugar", "free", "x"), norms("sugar-free 🍕 'x'"))
        assertEquals(listOf("don", "t"), norms("don't"))
        assertEquals(listOf("pollo", "arroz"), norms("pollo!! \"arroz\" ¿?"))
    }

    @Test fun `vacio y solo simbolos no dan tokens`() {
        assertTrue(NormalizerV2.tokenize("").isEmpty())
        assertTrue(NormalizerV2.tokenize("  🍕 -- % ").isEmpty())
    }

    @Test fun `los tramos van en orden y sin solaparse`() {
        val tokens = NormalizerV2.tokenize("Doscientos cincuenta g de Arróz, 1,5 tazas + ½ de leche (2 u.)")
        tokens.zipWithNext().forEach { (a, b) -> assertTrue("$a / $b", a.end <= b.start) }
        tokens.forEach { assertTrue(it.start < it.end) }
    }
}

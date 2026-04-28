package com.example.kpkn.domain.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FoodIndexTest {

    @Test
    fun `normalizeSearch strips diacritics`() {
        assertEquals("arroz", FoodIndex.normalizeSearch("arroz"))
        assertEquals("papa", FoodIndex.normalizeSearch("papá"))
        assertEquals("nino", FoodIndex.normalizeSearch("niño"))
    }

    @Test
    fun `tokenize removes stopwords`() {
        val tokens = FoodIndex.tokenize("arroz de pollo con sal")
        assertTrue(tokens.contains("arroz"))
        assertTrue(tokens.contains("pollo"))
        assertTrue(tokens.contains("sal"))
        // Stopwords removed
        assertTrue(!tokens.contains("de"))
        assertTrue(!tokens.contains("con"))
    }

    @Test
    fun `tokenize filters short tokens`() {
        val tokens = FoodIndex.tokenize("a de la el arroz")
        assertTrue(!tokens.contains("a"))
        assertTrue(!tokens.contains("de"))
        assertTrue(!tokens.contains("la"))
        assertTrue(!tokens.contains("el"))
        assertTrue(tokens.contains("arroz"))
    }

    @Test
    fun `generateTrigrams creates correct trigrams`() {
        val trigrams = FoodIndex.generateTrigrams("hola")
        // "$hola$" → "$ho", "hol", "ola", "la$"
        assertTrue(trigrams.contains("\$ho"))
        assertTrue(trigrams.contains("hol"))
        assertTrue(trigrams.contains("ola"))
        assertTrue(trigrams.contains("la\$"))
    }

    @Test
    fun `generateTrigrams for short token`() {
        val trigrams = FoodIndex.generateTrigrams("ab")
        assertEquals(setOf("ab"), trigrams)
    }
}

package com.example.kpkn.domain.nutrition

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WP-N11 (A-Q8): FoodIdentity accepts a candidate word for a query word of four letters or more when their phonetic codes are
 * equal. With every vowel folded into one letter, "pasta", "pesto" and "posta", "mote" and "mate", "lima" and "lomo" counted as
 * the same word. The code now keeps the vowels, so only a variant spelling of one sound is evidence of identity.
 */
class PhoneticIdentityEvidenceTest {

    @Test
    fun `a variant spelling of one sound is still evidence of identity`() {
        assertTrue(FoodIdentity.matchesDeclaredIdentity("keso fresco", "Queso fresco"))
        assertTrue(FoodIdentity.matchesDeclaredIdentity("poyo asado", "Pollo asado"))
        assertTrue(FoodIdentity.matchesDeclaredIdentity("uebo frito", "Huevo frito"))
    }

    @Test
    fun `a different vowel is not evidence of identity`() {
        assertFalse(FoodIdentity.matchesDeclaredIdentity("pesto verde", "Pasta verde"))
        assertFalse(FoodIdentity.matchesDeclaredIdentity("posta rosada", "Pasta rosada"))
        assertFalse(FoodIdentity.matchesDeclaredIdentity("mote cocido", "Mate cocido"))
        assertFalse(FoodIdentity.matchesDeclaredIdentity("lima fresca", "Lomo fresca"))
    }
}

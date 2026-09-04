package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.AmountIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MealLanguageCompilerTest {

    @Test
    fun laminasDeQuesoGouda_bindsUnitToFoodSpan() {
        val compiled = MealLanguageCompiler.compile("láminas de queso gouda")
        assertEquals(1, compiled.mentions.size)
        val mention = compiled.mentions.single()
        assertEquals("queso gouda", mention.foodSpan)
        assertEquals("thin_slice", mention.unitId)
        assertTrue(mention.grams != null && mention.grams in 35.0..50.0)
        assertEquals(
            MealLanguageGrammar.DeKind.UNIT_OF,
            MealLanguageGrammar.classifyDe("láminas", "queso gouda"),
        )
    }

    @Test
    fun galletasDeChocolate_isAttributeNotUnit() {
        val compiled = MealLanguageCompiler.compile("galletas de chocolate")
        assertEquals(1, compiled.mentions.size)
        assertEquals("galletas de chocolate", compiled.mentions.single().foodSpan)
        assertEquals(null, compiled.mentions.single().unitId)
        assertEquals(
            MealLanguageGrammar.DeKind.ATTRIBUTE,
            MealLanguageGrammar.classifyDe("galletas", "chocolate"),
        )
    }

    @Test
    fun twoTajadasDeJamonYQueso_doesNotCopyUnitToCheese() {
        val compiled = MealLanguageCompiler.compile("2 tajadas de jamón y queso")
        assertTrue("expected 2 mentions, was ${compiled.mentions.map { it.foodSpan }}", compiled.mentions.size >= 2)
        val ham = compiled.mentions.first { it.foodSpan.contains("jamon") || it.rawFragment.contains("jam") }
        val cheese = compiled.mentions.first { FoodIdentity.normalize(it.foodSpan).contains("queso") }
        assertTrue(ham.grams != null && ham.grams in 28.0..45.0)
        assertEquals(null, cheese.grams)
        assertEquals(null, cheese.unitId)
    }

    @Test
    fun platoDeArrozConPolloYChoclo_sharesContainer() {
        val compiled = MealLanguageCompiler.compile("un plato de arroz con pollo y choclo")
        assertEquals(3, compiled.mentions.size)
        assertEquals("plato", compiled.containerScope)
        assertTrue(compiled.mentions.all { it.containerScope == "plato" })
    }

    @Test
    fun cafeConLeche_andArrozConLeche_stayOneMention() {
        val coffee = MealLanguageCompiler.compile("café con leche")
        assertEquals(1, coffee.mentions.size)
        assertTrue(coffee.protectedWhole)
        val rice = MealLanguageCompiler.compile("arroz con leche")
        assertEquals(1, rice.mentions.size)
        assertTrue(rice.protectedWhole)
    }

    @Test
    fun mergeDoesNotTouchExplicitMass() {
        val parsed = parseMealDescription("100g de pollo")
        assertEquals(1, parsed.items.size)
        assertEquals(AmountIntent.EXPLICIT_MASS, parsed.items.single().amountIntent)
        val grams = parsed.items.single().amountGrams ?: 0.0
        assertEquals(100.0, grams, 0.01)
    }

    @Test
    fun mergeFillsFoodQueryForLaminas() {
        val parsed = parseMealDescription("láminas de queso gouda")
        val item = parsed.items.single()
        assertFalse(item.effectiveFoodQuery().contains("lamina"))
        assertTrue(item.effectiveFoodQuery().contains("queso"))
        assertEquals(AmountIntent.RESOLVED_SUBJECTIVE, item.amountIntent)
    }
}

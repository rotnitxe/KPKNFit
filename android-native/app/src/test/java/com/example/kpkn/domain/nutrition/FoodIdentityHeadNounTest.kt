package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.FoodItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WP-S2: a "leche" query must not accept "Dulce de leche" as milk. The head noun of the candidate (the first content
 * word of its name, brand words aside) has to be the noun the person asked for.
 */
class FoodIdentityHeadNounTest {

    private fun row(name: String, brand: String? = null) =
        FoodItem(id = "x", name = name, brand = brand, calories = 100.0, protein = 5.0, carbs = 10.0, fats = 3.0)

    @Test
    fun `the head noun is the first content word of the name`() {
        assertEquals("leche", FoodIdentity.headNoun("Leche descremada"))
        assertEquals("leche", FoodIdentity.headNoun("Leche condensada"))
        assertEquals("pan", FoodIdentity.headNoun("Pan integral"))
        assertEquals("pechuga", FoodIdentity.headNoun("Pechuga de pollo"))
        // The noun of a dessert or a dish is the dessert or the dish, not the milk inside it.
        assertEquals("dulce", FoodIdentity.headNoun("Dulce de leche"))
        assertEquals("arroz", FoodIdentity.headNoun("Arroz con leche"))
    }

    @Test
    fun `words that only name the brand are looked past`() {
        assertEquals("leche", FoodIdentity.headNoun("Colun leche entera", brand = "Colun"))
        assertEquals("leche", FoodIdentity.headNoun("Nestlé Leche condensada", brand = "Nestle", sought = "leche"))
    }

    @Test
    fun `a name made only of brand words is read by its own first word`() {
        // OFF: name "DULCE DE LECHE CO", brand "DULCE DE LECHE & CO.". Skipping the brand words used to make "leche" the head.
        assertEquals("dulce", FoodIdentity.headNoun("DULCE DE LECHE CO", brand = "DULCE DE LECHE & CO.", sought = "leche"))
        assertEquals("huevos", FoodIdentity.headNoun("Huevos de Talca", brand = "Huevos de Talca", sought = "huevo"))
    }

    @Test
    fun `a name that goes on past a brand that holds the noun is still read by the noun`() {
        // "Lonco Leche" and "Gran Cereal" are brands that contain the product noun: the name is milk / cereal.
        assertEquals("leche", FoodIdentity.headNoun("LONCO LECHE SIN LACTOSA", brand = "Lonco Leche", sought = "leche"))
        assertEquals("cereal", FoodIdentity.headNoun("Gran Cereal Clásica", brand = "Costa, Gran Cereal", sought = "cereal"))
        assertTrue(FoodIdentity.matchesDeclaredIdentity("leche sin lactosa", row("LONCO LECHE SIN LACTOSA", "Lonco Leche")))
        assertTrue(FoodIdentity.matchesDeclaredIdentity("cereal", row("Gran Cereal Clásica", "Costa, Gran Cereal")))
    }

    @Test
    fun `milk is milk and dulce de leche is not`() {
        val query = "leche"
        assertTrue(FoodIdentity.matchesDeclaredIdentity(query, row("Leche descremada", "Colun")))
        assertTrue(FoodIdentity.matchesDeclaredIdentity(query, row("Colun Leche entera", "Colun")))
        assertTrue(FoodIdentity.matchesDeclaredIdentity(query, row("Leche asada")))
        assertFalse(FoodIdentity.matchesDeclaredIdentity(query, row("Dulce de leche", "Havanna")))
        assertFalse(FoodIdentity.matchesDeclaredIdentity(query, row("DULCE DE LECHE CO", "DULCE DE LECHE & CO.")))
        assertFalse(FoodIdentity.matchesDeclaredIdentity(query, row("Arroz con leche", "Soprole")))
        assertFalse(FoodIdentity.matchesDeclaredIdentity(query, row("Crema de leche")))
    }

    @Test
    fun `the dulce de leche row answers its own query and leche condensada answers when asked for`() {
        assertTrue(FoodIdentity.matchesDeclaredIdentity("dulce de leche", row("DULCE DE LECHE CO", "DULCE DE LECHE & CO.")))
        // Same head ("leche") as plain milk, but a transformed product: only a query that names it accepts it.
        assertTrue(FoodIdentity.matchesDeclaredIdentity("leche condensada", row("Leche condensada", "Nestlé")))
        assertFalse(FoodIdentity.matchesDeclaredIdentity("leche", row("Leche condensada", "Nestlé")))
    }
}

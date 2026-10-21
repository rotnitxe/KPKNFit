package com.example.kpkn.screens.nutrition.scan

import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.domain.nutrition.HouseholdPortions
import com.example.kpkn.domain.nutrition.TagOrigin
import com.example.kpkn.domain.nutrition.canFinalize
import com.example.kpkn.domain.nutrition.toLoggedFood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** El producto escaneado entra al registro de comidas por el mismo camino que una fila del buscador. */
class ScannedFoodTagTest {
    private val yogur = FoodItem(
        id = "off_7801234567894", name = "Yogur natural", brand = "Soprole", calories = 61.0, protein = 3.5, carbs = 4.7, fats = 3.3,
        source = "OFF Chile", sourceRecordId = "7801234567894", nutritionBasis = "PER_100G_AS_SOLD", sourcePriority = 80, verifiedScore = 0.72,
    )

    @Test fun `sin gramos propone la misma racion que el buscador del cajon`() {
        val tag = scannedFoodTag(yogur)
        assertEquals(HouseholdPortions.eatenGramsForSearchPick(yogur, yogur.name), tag.amountGrams!!, 0.0)
        assertEquals(defaultGramsForScanned(yogur), tag.amountGrams!!, 0.0)
        assertEquals(TagOrigin.SEARCH, tag.origin)
    }

    @Test fun `los gramos elegidos escalan los nutrientes de la ficha`() {
        val tag = scannedFoodTag(yogur, grams = 200.0)
        assertEquals(200.0, tag.amountGrams!!, 0.0)
        assertEquals(200.0, tag.loggedFood!!.amount, 0.0)
        assertEquals(122.0, tag.loggedFood!!.calories, 0.5)
        assertEquals(7.0, tag.loggedFood!!.protein, 0.1)
    }

    @Test fun `la ficha es la identidad comida y queda lista para guardarse por el camino de siempre`() {
        val tag = scannedFoodTag(yogur, grams = 150.0)
        assertEquals(yogur.id, tag.foodItem!!.id)
        assertEquals(yogur.name, tag.foodQuery)
        assertTrue("identity" in tag.confirmedDimensions)
        assertNotNull(tag.interpretationV2)
        assertTrue("sin dudas pendientes, se puede guardar", tag.interpretationV2!!.canFinalize())
        val logged = tag.interpretationV2!!.toLoggedFood(tag.loggedFood!!)
        assertEquals(150.0, logged.amount, 0.5)
    }

    @Test fun `gramos no validos caen en la racion propuesta`() {
        val default = defaultGramsForScanned(yogur)
        assertEquals(default, scannedFoodTag(yogur, grams = 0.0).amountGrams!!, 0.0)
        assertEquals(default, scannedFoodTag(yogur, grams = Double.NaN).amountGrams!!, 0.0)
        assertEquals(default, scannedFoodTag(yogur, grams = -5.0).amountGrams!!, 0.0)
    }

    @Test fun `un alimento propio por porcion escala sus nutrientes por la racion`() {
        val galletas = FoodItem(
            id = "custom_g", name = "Galletas de avena", calories = 135.0, protein = 2.0, carbs = 21.0, fats = 4.5, servingSize = 30.0, unit = "g",
            isCustom = true, source = "custom", sourceRecordId = "4006381333931", nutritionBasis = "PER_SERVING",
        )
        val tag = scannedFoodTag(galletas)
        assertTrue(tag.amountGrams!! > 0.0)
        // Los nutrientes son los de 30 g: la ración elegida los escala en proporción.
        assertEquals(135.0 * tag.amountGrams!! / 30.0, tag.loggedFood!!.calories, 1.0)
    }
}

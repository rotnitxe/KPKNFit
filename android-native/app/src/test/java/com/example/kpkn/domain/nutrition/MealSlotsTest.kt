package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.MealType
import org.junit.Assert.assertEquals
import org.junit.Test

/** N0: una sola fuente de comidas. Orden del día, nombre visible y «Merienda» en lugar de «Snack». */
class MealSlotsTest {

    @Test
    fun `el orden del día es desayuno, almuerzo, merienda y cena`() {
        assertEquals(
            listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.SNACK, MealType.DINNER),
            MealSlots.mealOrder,
        )
    }

    @Test
    fun `cada comida tiene su etiqueta visible`() {
        assertEquals("Desayuno", MealSlots.mealLabel(MealType.BREAKFAST))
        assertEquals("Almuerzo", MealSlots.mealLabel(MealType.LUNCH))
        assertEquals("Merienda", MealSlots.mealLabel(MealType.SNACK))
        assertEquals("Cena", MealSlots.mealLabel(MealType.DINNER))
    }

    @Test
    fun `la etiqueta visible de SNACK en el detector es Merienda`() {
        assertEquals("Merienda", ContextDetector.MealContext.SNACK.label)
    }
}

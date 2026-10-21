package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.MealType

/**
 * Fuente única de las comidas del día: su orden y su nombre visible. Nutrición (raíz, historial y registro) y Home
 * leen de aquí, para que una comida se llame igual y vaya en el mismo sitio en toda la app.
 */
object MealSlots {

    /** Desayuno, Almuerzo, Merienda y Cena, en ese orden (no el de [MealType], que no es el del día). */
    val mealOrder: List<MealType> = listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.SNACK, MealType.DINNER)

    /** Nombre visible de la comida. `SNACK` se muestra como «Merienda». */
    fun mealLabel(type: MealType): String = when (type) {
        MealType.BREAKFAST -> "Desayuno"
        MealType.LUNCH -> "Almuerzo"
        MealType.SNACK -> "Merienda"
        MealType.DINNER -> "Cena"
    }
}

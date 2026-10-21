package com.example.kpkn.screens.nutrition.design

import androidx.compose.ui.graphics.vector.ImageVector
import com.example.kpkn.data.models.MealType
import com.example.kpkn.ui.components.icons.KpknLineIcons

/** Icono de línea de cada comida: amanecer (desayuno), sol (almuerzo), manzana (merienda) y luna (cena). */
fun mealIcon(type: MealType): ImageVector = when (type) {
    MealType.BREAKFAST -> KpknLineIcons.Sunrise
    MealType.LUNCH -> KpknLineIcons.Sun
    MealType.SNACK -> KpknLineIcons.Apple
    MealType.DINNER -> KpknLineIcons.Moon
}

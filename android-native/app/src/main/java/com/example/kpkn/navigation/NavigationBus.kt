package com.example.kpkn.navigation

object NavigationBus {
    private val nutritionShareListeners = mutableSetOf<(String) -> Unit>()

    @Synchronized
    fun registerNutritionShareListener(listener: (String) -> Unit) {
        nutritionShareListeners += listener
    }

    @Synchronized
    fun unregisterNutritionShareListener(listener: (String) -> Unit) {
        nutritionShareListeners -= listener
    }
}

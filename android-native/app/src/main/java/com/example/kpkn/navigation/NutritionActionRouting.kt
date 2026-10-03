package com.example.kpkn.navigation

/**
 * Destino de una acción rápida de Nutrición (widget, atajos y deep links
 * `nutrition/action/{action}`), ya resuelto y sin tocar el NavController.
 *
 * Separado de `MainActivity` para poder probar en JVM la tabla de alias y para
 * que la ruta de acción no haga nada durante la composición (WP-U3 / C3).
 */
sealed interface NutritionActionTarget {

    /** Ruta a la que se navega al resolver la acción. */
    val route: String

    /** Abre Nutrición con el logger de comidas visible: [tab] 0 = describir, 1 = buscar. */
    data class OpenLogger(val tab: Int) : NutritionActionTarget {
        override val route: String get() = KpknRoute.Nutrition.route
    }

    /** Abre la pantalla de cuerpo (editor de peso). */
    data object BodyProgress : NutritionActionTarget {
        override val route: String get() = KpknRoute.BodyProgress.route
    }

    /** Abre el dashboard (Home). */
    data object Home : NutritionActionTarget {
        override val route: String get() = KpknRoute.Home.route
    }

    /** Solo la pestaña Nutrición: acción vacía o desconocida. */
    data object Nutrition : NutritionActionTarget {
        override val route: String get() = KpknRoute.Nutrition.route
    }
}

object NutritionActionRouting {

    /**
     * Alias vigentes (sin distinguir mayúsculas): el widget emite `openFoodLog` y
     * `openSearch`; los demás se conservan por compatibilidad con atajos antiguos.
     */
    fun resolve(action: String?): NutritionActionTarget =
        when (action?.trim()?.lowercase()) {
            "openfoodlog", "foodlog", "log" -> NutritionActionTarget.OpenLogger(tab = 0)
            "opensearch", "search" -> NutritionActionTarget.OpenLogger(tab = 1)
            "openweighteditor", "weight" -> NutritionActionTarget.BodyProgress
            "opendashboard", "dashboard" -> NutritionActionTarget.Home
            else -> NutritionActionTarget.Nutrition
        }
}

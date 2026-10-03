package com.example.kpkn.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WP-U3 / C3: tabla de alias de `nutrition/action/{action}` (widget y atajos). Puro JVM:
 * `resolve` no toca el NavController; MainActivity solo navega al `route` resultante.
 */
class NutritionActionRoutingTest {

    private fun assertResolves(expected: NutritionActionTarget, vararg actions: String?) {
        actions.forEach { action ->
            assertEquals("acción «$action»", expected, NutritionActionRouting.resolve(action))
        }
    }

    @Test
    fun logAliasesOpenTheLoggerOnTheDescriptionTab() {
        // El widget emite «openFoodLog»; la ruta lo recibe tal cual y resolve ignora mayúsculas.
        assertResolves(
            NutritionActionTarget.OpenLogger(tab = 0),
            "openfoodlog", "openFoodLog", "foodlog", "FoodLog", "log", "LOG", " log ",
        )
    }

    @Test
    fun searchAliasesOpenTheLoggerOnTheSearchTab() {
        assertResolves(NutritionActionTarget.OpenLogger(tab = 1), "opensearch", "openSearch", "search", "SEARCH")
    }

    @Test
    fun weightAliasesOpenBodyProgress() {
        assertResolves(NutritionActionTarget.BodyProgress, "openweighteditor", "openWeightEditor", "weight", "Weight")
    }

    @Test
    fun dashboardAliasesOpenHome() {
        assertResolves(NutritionActionTarget.Home, "opendashboard", "openDashboard", "dashboard")
    }

    @Test
    fun unknownBlankOrMissingActionsOpenPlainNutrition() {
        assertResolves(NutritionActionTarget.Nutrition, "unknown", "openfoodlog2", "logs", "", "   ", null)
    }

    @Test
    fun targetsPointAtTheRightRoutes() {
        assertEquals(KpknRoute.Nutrition.route, NutritionActionRouting.resolve("log").route)
        assertEquals(KpknRoute.Nutrition.route, NutritionActionRouting.resolve("search").route)
        assertEquals(KpknRoute.BodyProgress.route, NutritionActionRouting.resolve("weight").route)
        assertEquals(KpknRoute.Home.route, NutritionActionRouting.resolve("dashboard").route)
        assertEquals(KpknRoute.Nutrition.route, NutritionActionRouting.resolve("otra").route)
    }

    @Test
    fun noTargetEverNavigatesBackToTheActionRoute() {
        // Navegar a la propia ruta de acción dejaría la pila en un bucle de entradas vacías.
        listOf("log", "search", "weight", "dashboard", "otra", "").forEach { action ->
            val route = NutritionActionRouting.resolve(action).route
            assertTrue("la acción «$action» no debe resolver a la ruta de acción", !route.startsWith("nutrition/action"))
        }
    }
}

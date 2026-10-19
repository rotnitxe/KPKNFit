package com.example.kpkn.screens.technique.hub

import androidx.navigation.NavArgument
import androidx.navigation.NavDestination
import androidx.navigation.NavType
import com.example.kpkn.navigation.KpknRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Comprueba la decodificación del argumento real que Navigation entrega al destino. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = android.app.Application::class)
class TechniqueNavigationArgumentsTest {
    private fun matchingArguments(pattern: String, route: String, vararg names: String): android.os.Bundle {
        val destination = NavDestination("technique-test")
        names.forEach { name -> destination.addArgument(name, NavArgument.Builder().setType(NavType.StringType).build()) }
        destination.route = pattern
        // Navigation 2.8 mantiene las rutas separadas de los enlaces externos registrados.
        val match = destination.matchRoute(route)
        assertNotNull(match)
        return requireNotNull(match?.matchingArgs)
    }
    @Test fun trendsPreservesLiteralPercentEncodedTextSlashesAndAccents() {
        val key = "name:remo %2F / ángulo 50%"
        val arguments = matchingArguments(KpknRoute.TechniqueTrends.route, KpknRoute.TechniqueTrends.create(key), KpknRoute.TechniqueTrends.ARG_EXERCISE_KEY)
        assertEquals(key, arguments.getString(KpknRoute.TechniqueTrends.ARG_EXERCISE_KEY))
    }
    @Test fun analysisAndComparisonPreserveTheirActualArguments() {
        val first = "vídeo / 50% %2F"
        val second = "sesión / carga %25"
        val analysis = matchingArguments(KpknRoute.TechniqueAnalysis.route, KpknRoute.TechniqueAnalysis.create(first), KpknRoute.TechniqueAnalysis.ARG_MEDIA_ID)
        assertEquals(first, analysis.getString(KpknRoute.TechniqueAnalysis.ARG_MEDIA_ID))
        val comparison = matchingArguments(KpknRoute.TechniqueCompare.route, KpknRoute.TechniqueCompare.create(first, second), KpknRoute.TechniqueCompare.ARG_MEDIA_A, KpknRoute.TechniqueCompare.ARG_MEDIA_B)
        assertEquals(first, comparison.getString(KpknRoute.TechniqueCompare.ARG_MEDIA_A))
        assertEquals(second, comparison.getString(KpknRoute.TechniqueCompare.ARG_MEDIA_B))
    }
}

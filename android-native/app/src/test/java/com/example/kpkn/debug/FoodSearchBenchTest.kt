package com.example.kpkn.debug

import android.app.Application
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FoodSearchBenchTest {
    @After fun cleanup() { FoodSearchBenchActivity.testRun = null }
    @Test fun doscientasConsultasUnicasConVeinteAusentesEnBaseFalsa() {
        val queries = FoodSearchBenchQueries.all
        assertEquals(200, queries.size); assertEquals(200, queries.map { it.text }.distinct().size)
        assertEquals(setOf("simple", "multi", "accent", "brand", "typo", "no_results"), queries.map { it.category }.toSet())
        val fake = listOf("pollo", "arroz integral", "yogur natural", "jamón", "danone")
        val missing = queries.filter { it.expectsNoResultsInFixture }
        assertEquals(20, missing.size)
        missing.forEach { q -> assertTrue(fake.filter { q.text in it }.isEmpty()) }
    }
    @Test fun percentilesDeRangoMasCercanoSinInterpolacion() {
        val input = listOf(8.0, 1.0, 4.0, 2.0, 10.0, 5.0, 9.0, 3.0, 7.0, 6.0)
        assertEquals(5.0, FoodSearchBenchQueries.percentile(input, .5), 0.0)
        assertEquals(10.0, FoodSearchBenchQueries.percentile(input, .95), 0.0)
        assertEquals(10.0, FoodSearchBenchQueries.percentile(input, .99), 0.0)
        assertEquals(7.0, FoodSearchBenchQueries.percentile(listOf(7.0), .95), 0.0)
    }
    @Test fun actividadPuedeArrancarSinRepositorioNiCatalogoReal() {
        FoodSearchBenchActivity.testRun = { "BENCH prueba aislada" }
        Robolectric.buildActivity(FoodSearchBenchActivity::class.java).setup().use { controller ->
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            assertNotNull(controller.get())
        }
    }
}
